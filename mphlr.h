/* 
Serval Distributed Numbering Architecture (DNA)
Copyright (C) 2010 Paul Gardner-Stephen 

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

#include <stdio.h>
#include <errno.h>
#include <stdlib.h>
#ifdef HAVE_STRINGS_H
#include <strings.h>
#endif
#include <string.h>

#ifdef WIN32
#include "win32/win32.h"
#else
#include <unistd.h>
#endif

#if !defined(FORASTERISK) && !defined(s_addr)
#ifdef HAVE_ARPA_INET_H
#include <arpa/inet.h>
#else
typedef unsigned int in_addr_t;
struct in_addr {
   in_addr_t s_addr;
};
#endif
#endif

#ifdef HAVE_SYS_SOCKET_H
#include <sys/socket.h>
#endif
#ifdef HAVE_SYS_MMAN_H
#include <sys/mman.h>
#endif
#ifdef HAVE_SYS_TIME_H
#include <sys/time.h>
#endif
#ifdef HAVE_POLL_H
#include <poll.h>
#endif
#ifdef HAVE_NETDB_H
#include <netdb.h>
#endif
#ifdef HAVE_CTYPE_H
#include <ctype.h>
#endif

#ifndef WIN32
#include <sys/ioctl.h>
#include <sys/un.h>
#include <net/if.h>
#endif

#include <fcntl.h>
//FIXME #include <getopt.h>
#include <ctype.h>

/* UDP Port numbers for various Serval services */
#define PORT_DNA 4110
#define PORT_OVERLAY 4119

/* OpenWRT libc doesn't have bcopy, but has memmove */
#define bcopy(A,B,C) memmove(B,A,C)

#define BATCH 1
#define NONBATCH 0

#define REQ_SERIAL 0
#define REQ_PARALLEL -1
#define REQ_FIRSTREPLY -2
#define REQ_REPLY -101


#define SET_NOREPLACE 1
#define SET_REPLACE 2
#define SET_NOCREATE 3
#define SET_FRAGMENT 0x80

#define WITHDATA 1
#define WITHOUTDATA 0

/* Limit packet payloads to minimise packet loss of big packets in mesh networks */
#define MAX_DATA_BYTES 256

extern int debug;
extern int timeout;
extern int hlr_size;
extern unsigned char *hlr;

double simulatedBER;

extern int serverMode;

extern char *gatewayuri;

extern struct sockaddr recvaddr;
extern struct in_addr client_addr;
extern int client_port;

#define MAX_PEERS 1024
extern int peer_count;
extern struct in_addr peers[MAX_PEERS];

struct mphlr_variable {
  unsigned char id;
  char *name;
  char *desc;
};

extern char *outputtemplate;
extern char *instrumentation_file;
extern char *batman_socket;
extern char *batman_peerfile;

/* HLR records can be upto 4GB, so 4x8bits are needed to encode the size */
#define HLR_RECORD_LEN_SIZE 4

/* Packet format:

   16 bit - Magic value 0x4110
   16 bit - Version number (0001 initially)
   16 bit - Payload length
   16 bit - Cipher method (0000 = clear text)
   
   Ciphered payload follows:
   (needs to have no predictable data to protect against known plain-text attacks)
   
   64bit transaction id (random)
   8bit - payload rotation (random, to help protect encryption from cribs)

   Remainder of payload, after correcting for rotation:
   
   33byte did|subscriber id
   16byte salt
   16byte hash of PIN+salt
   
   Remainder of packet is interpretted as a series of operations

   8 bit operation: 
   00 = get, 01 = set, 02 = delete, 03 = update,
   80 = decline, 81 = okay (+optional result),
   f0 = xfer HLR record
   fe = random padding follows (to help protect cryptography from cribs)
   ff = end of transaction
   
   get - 8 bit variable value

*/
#define SID_SIZE 32 
#define DID_MAXSIZE 32
#define SIDDIDFIELD_LEN (SID_SIZE+1)
#define PINFIELD_LEN 32
#define HEADERFIELDS_LEN (2+2+2+2+8+1)
#define OFS_TRANSIDFIELD (2+2+2+2)
#define TRANSID_SIZE 8
#define OFS_ROTATIONFIELD (OFS_TRANSIDFIELD+TRANSID_SIZE)
#define OFS_SIDDIDFIELD HEADERFIELDS_LEN
#define OFS_PINFIELD (OFS_SIDDIDFIELD+SIDDIDFIELD_LEN)
#define OFS_PAYLOAD (OFS_PINFIELD+16+16)

struct response {
  int code;
  unsigned char sid[32];
  struct in_addr sender;
  unsigned char *response;
  int response_len;
  int var_id;
  int var_instance;
  int value_len;
  int value_offset;
  int value_bytes;
  struct response *next,*prev;

  /* who sent it? */
  unsigned short peer_id;
  /* have we checked it to see if it allows us to stop requesting? */
  unsigned char checked;
};

struct response_set {
  struct response *responses;
  struct response *last_response;
  int response_count;

  /* Bit mask of peers who have replied */
  unsigned char *reply_bitmask;
};

struct hlrentry_handle {
  int record_length;
  unsigned char *hlr;
  int hlr_offset;
  
  int var_id;
  int var_instance;
  unsigned char *value;
  int value_len;

  int entry_offset;
};

/* Array of variables that can be placed in an MPHLR */
#define VAR_EOR 0x00
#define VAR_CREATETIME 0x01
#define VAR_CREATOR 0x02
#define VAR_REVISION 0x03
#define VAR_REVISOR 0x04
#define VAR_PIN 0x05
#define VAR_VOICESIG 0x08
#define VAR_HLRMASTER 0x0f
#define VAR_DIDS 0x80
#define VAR_LOCATIONS 0x81
#define VAR_IEMIS 0x82
#define VAR_TEMIS 0x83
#define VAR_CALLS_IN 0x90
#define VAR_CALLS_MISSED 0x91
#define VAR_CALLS_OUT 0x92
#define VAR_SMESSAGES 0xa0
#define VAR_DID2SUBSCRIBER 0xb0
#define VAR_HLRBACKUPS 0xf0
#define VAR_NOTE 0xff
extern struct mphlr_variable vars[];

#define ACTION_GET 0x00
#define ACTION_SET 0x01
#define ACTION_DEL 0x02
#define ACTION_INSERT 0x03
#define ACTION_SENDSMS 0x04
#define ACTION_CREATEHLR 0x0f

#define ACTION_STATS 0x40

#define ACTION_DONE 0x7e
#define ACTION_ERROR 0x7f

#define ACTION_DECLINED 0x80
#define ACTION_OKAY 0x81
#define ACTION_DATA 0x82
#define ACTION_WROTE 0x83
#define ACTION_SMSRECEIVED 0x84

#define ACTION_XFER 0xf0
#define ACTION_PAD 0xfe
#define ACTION_EOT 0xff

extern int hexdigit[16];

/* Make sure we have space to put bytes of the packet as we go along */
#define CHECK_PACKET_LEN(B) {if (((*packet_len)+(B))>=packet_maxlen) { setReason("Packet composition ran out of space."); return -1; } }

extern int sock;

int stowSid(unsigned char *packet,int ofs,char *sid);
int stowDid(unsigned char *packet,int *ofs,char *did);
int isFieldZeroP(unsigned char *packet,int start,int count);
void srandomdev();
int respondSimple(char *sid,int action,unsigned char *action_text,int action_len,
		  unsigned char *transaction_id,int cryptoFlags);
int requestItem(char *did,char *sid,char *item,int instance,unsigned char *buffer,int buffer_length,int *len,
		unsigned char *transaction_id);
int requestNewHLR(char *did,char *pin,char *sid);
int server(char *backing_file,int size,int foregroundMode);

int setReason(char *fmt, ...);
int hexvalue(unsigned char c);
int dump(char *name,unsigned char *addr,int len);
int packetOk(unsigned char *packet,int len,unsigned char *transaction_id);
int process_packet(unsigned char *packet,int len,struct sockaddr *sender,int sender_len);
int packetMakeHeader(unsigned char *packet,int packet_maxlen,int *packet_len,unsigned char *transaction_id);
int packetSetDid(unsigned char *packet,int packet_maxlen,int *packet_len,char *did);
int packetSetSid(unsigned char *packet,int packet_maxlen,int *packet_len,char *sid);
int packetFinalise(unsigned char *packet,int packet_maxlen,int *packet_len);
int packetAddHLRCreateRequest(unsigned char *packet,int packet_maxlen,int *packet_len);
int extractResponses(struct in_addr sender,unsigned char *buffer,int len,struct response_set *responses);
int packetAddVariableRequest(unsigned char *packet,int packet_maxlen,int *packet_len,
                             char *item,int instance,int start_offset,int max_offset);
int packetGetID(unsigned char *packet,int len,char *did,char *sid);
int getPeerList();
int sendToPeers(unsigned char *packet,int packet_len,int method,int peerId,struct response_set *responses);
int getReplyPackets(int method,int peer,int batchP,
		    struct response_set *responses,
		    unsigned char *transaction_id,int timeout);
int clearResponse(struct response **response);
int nextHlr(unsigned char *hlr,int *ofs);
int findHlr(unsigned char *hlr,int *ofs,char *sid,char *did);
int createHlr(char *did,char *sid);
struct hlrentry_handle *openhlrentry(unsigned char *hlr,int hofs);
struct hlrentry_handle *hlrentrygetent(struct hlrentry_handle *h);
int hlrStowValue(unsigned char *hlr,int hofs,int hlr_offset,
		 int varid,int varinstance,unsigned char *value,int len);
int hlrMakeSpace(unsigned char *hlr,int hofs,int hlr_offset,int bytes);
int packageVariableSegment(unsigned char *data,int *dlen,struct hlrentry_handle *h,
			   int offset,int buffer_size);
int packetDecipher(unsigned char *packet,int len,int cipher);
int safeZeroField(unsigned char *packet,int start,int count);
int unpackageVariableSegment(unsigned char *data,int dlen,int flags,struct response *r);
int extractSid(unsigned char *packet,int *ofs,char *sid);
int hlrSetVariable(unsigned char *hlr,int hofs,int varid,int varinstance,
		   unsigned char *value,int len);
int extractDid(unsigned char *packet,int *ofs,char *did);
char *hlrSid(unsigned char *hlr,int ofs);
int parseAssignment(unsigned char *text,int *var_id,unsigned char *value,int *value_len);
int writeItem(char *id,int var_id,int instance,unsigned char *value,int value_start,int value_len,int policy);
int packetAddVariableWrite(unsigned char *packet,int packet_maxlen,int *packet_len,
			   int itemId,int instance,unsigned char *value,int start_offset,int value_len,int flags);
int processRequest(unsigned char *packet,int len,struct sockaddr *sender,int sender_len,
		   unsigned char *transaction_id,char *did,char *sid);

int extractRequest(unsigned char *packet,int *packet_ofs,int packet_len,
		   int *itemId,int *instance,unsigned char *value,
		   int *start_offset,int *max_offset,int *flags);
int hlrGetVariable(unsigned char *hlr,int hofs,int varid,int varinstance,
		   unsigned char *value,int *len);
int packetSendRequest(int method,unsigned char *packet,int packet_len,int batchP,
		      unsigned char *transaction_id,struct response_set *responses);
int dumpResponses(struct response_set *responses);
int eraseLastResponse(struct response_set *responses);
int dropPacketP(int packet_len);
int clearResponses(struct response_set *responses);
int responseFromPeerP(struct response_set *responses,int peerId);
int responseFromPeer(struct response_set *responses,int peerId);
int additionalPeer(char *peer);
int readBatmanPeerFile(char *file_path,struct in_addr peers[],int *peer_count,int peer_max);
int getBatmanPeerList(char *socket_path,struct in_addr peers[],int *peer_count,int peer_max);
int hlrDump(unsigned char *hlr,int hofs);
int peerAddress(char *did,char *sid,int flags);
int fixResponses(struct response_set *responses);
int importHlr(char *textfile);
int exportHlr(unsigned char *hlr,char *text);
int openHlrFile(char *backing_file,int size);

#define CRYPT_CIPHERED 1
#define CRYPT_SIGNED 2
#define CRYPT_PUBLIC 4
