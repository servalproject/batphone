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

#include "mphlr.h"

int process_packet(unsigned char *packet,int len,struct sockaddr *sender,int sender_len)
{
  int authenticatedP=0;
  char did[128];
  char sid[128];
  unsigned char *transaction_id=&packet[OFS_TRANSIDFIELD];
  
  did[0]=0; sid[0]=0;
  
  /* Get DID or SID */
  if (packetGetID(packet,len,did,sid)) return setReason("Could not parse DID or SID");
  
  /* Check for PIN */
  if (!isFieldZeroP(packet,OFS_PINFIELD,16))
    {
      /* Authentication has been attempted.
	 If it is incorrect, then we need to return with ACTION_DECLINED
      */
      if (debug>1) fprintf(stderr,"A PIN has been supplied.\n");
      
      /* Can only authenticate by SID, not DID (since DIDs are ambiguous) */
      if (packet[OFS_SIDDIDFIELD]!=1) return setReason("You can only authenticate against a SID");
   
      /* XXX check authentication */
      return setReason("Authentication not yet supported");
    }
  else 
    {
      /* No attempt at authentication was made */
      authenticatedP=0;
      if (debug>1) fprintf(stderr,"No PIN was supplied.\n");
    }

  if (serverMode) return processRequest(packet,len,sender,sender_len,transaction_id,did,sid);
   
  return 0;
}

int packetOk(unsigned char *packet,int len,unsigned char *transaction_id)
{
  /* Make sure that the packet is meant for us, and is not mal-formed */
  int version;
  int cipher;
  int length;
  int payloadRotation;

  if (len<HEADERFIELDS_LEN) return setReason("Packet is too short");
  if (packet[0]!=0x41||packet[1]!=0x10) return setReason("Packet has incorrect magic value");

  version=(packet[2]<<8)|packet[3];
  length=(packet[4]<<8)|packet[5];
  cipher=(packet[6]<<8)|packet[7];
  if (version!=1) return setReason("Unknown packet format version");
  if (cipher!=0) return setReason("Unknown packet cipher");
  if (length!=len) return setReason("Packet length incorrect");

  if (cipher) 
	  if (packetDecipher(packet,len,cipher)) 
		  return setReason("Could not decipher packet");

  /* Make sure the transaction ID matches */
  if (transaction_id)
    {
      int i;
	  for(i=0;i<TRANSID_SIZE;i++)
		if (packet[OFS_TRANSIDFIELD+i]!=transaction_id[i])
		  return setReason("transaction ID mismatch");
    }
  
  /* Unrotate the payload */
  payloadRotation=packet[OFS_ROTATIONFIELD];
  {
    unsigned char temp[256];
    bcopy(&packet[len-payloadRotation],&temp[0],payloadRotation);
    bcopy(&packet[HEADERFIELDS_LEN],&packet[HEADERFIELDS_LEN+payloadRotation],
	  len-(HEADERFIELDS_LEN)-payloadRotation);
    bcopy(&temp[0],&packet[HEADERFIELDS_LEN],payloadRotation);
  }

  if (debug>1) fprintf(stderr,"Packet passes sanity checks and is ready for decoding.\n");
  if (debug>2) dump("unrotated packet",packet,len);

  return 0;
}

int packetMakeHeader(unsigned char *packet,int packet_maxlen,int *packet_len,
		     unsigned char *transaction_id)
{
  int i;

  CHECK_PACKET_LEN(OFS_PAYLOAD);

  /* 0x4110 magic value */
  packet[0]=0x41;
  packet[1]=0x10;
  
  /* encoding version */
  packet[2]=0x00;
  packet[3]=0x01;
  
  /* Payload length (to be filled in later) */
  packet[4]=0x00;
  packet[5]=0x00;
  
  /* Payload cipher (0x0000 = plain text) */
  packet[6]=0x00;
  packet[7]=0x00;

  /* Add 64bit transaction id */
  if (transaction_id)
    /* Use supplied transaction ID */
    for(i=0;i<TRANSID_SIZE;i++) packet[OFS_TRANSIDFIELD+i]=transaction_id[i];
  else
    /* No transaction ID supplied, so create random transaction ID */
    for(i=0;i<TRANSID_SIZE;i++) packet[OFS_TRANSIDFIELD+i]=random()&0xff;

  /* payload rotation (not yet applied) */
  packet[OFS_ROTATIONFIELD]=0x00;

  *packet_len=HEADERFIELDS_LEN;

  /* Clear did/subscriber ID, salt and hashed pin fields.
     However, we cannot zero them, because that would provide significant knowable plain-text
     for a known plain text attack.
     Thus, instead we fill it with random date, but make the modulo sum of each field == 0x00
     to indicate that no PIN has been provided. */
  safeZeroField(packet,*packet_len,SIDDIDFIELD_LEN); *packet_len+=SIDDIDFIELD_LEN;
  safeZeroField(packet,*packet_len,16); *packet_len+=16;
  safeZeroField(packet,*packet_len,16); *packet_len+=16;

  return 0;
}

int packetSetDid(unsigned char *packet,int packet_maxlen,int *packet_len,char *did)
{
  /* Set the subject field to the supplied DID.
     DIDs get encoded 4bits per digit (0-9,#,*,+,SPARE1,ESCAPE,END)
  */
  int ofs=OFS_SIDDIDFIELD; /* where the DID/subscriber ID gets written */

  /* Put DID (ie not SID) marker into packet */
  packet[ofs++]=0x00;

  return stowDid(packet,&ofs,did);
}

int packetSetSid(unsigned char *packet,int packet_maxlen,int *packet_len,char *sid)
{
  /* Convert and store hex formatted sid */
  int ofs=OFS_SIDDIDFIELD; /* where the DID/subscriber ID gets written */

  if (strlen(sid)!=64) {
    if (debug) fprintf(stderr,"Invalid SID: [%s] - should be 64 hex digits\n",sid);
    return setReason("SID must consist of 64 hex digits");
  }

  packet[ofs++]=0x01; /* SID */
  return stowSid(packet,ofs,sid);
}

int packetFinalise(unsigned char *packet,int packet_maxlen,int *packet_len)
{
  /* Add any padding bytes and EOT to packet */
  int paddingBytes=rand()&0xf;
  int payloadRotation;

  if (paddingBytes)
    {
      CHECK_PACKET_LEN(2+paddingBytes);
      packet[(*packet_len)++]=ACTION_PAD;
      packet[(*packet_len)++]=paddingBytes;
      while(paddingBytes--) packet[(*packet_len)++]=random()&0xff;
    }

  packet[(*packet_len)++]=ACTION_EOT;

  /* Set payload length */
  packet[4]=((*packet_len)>>8)&0xff;
  packet[5]=((*packet_len)&0xff);

  /* Work out by how much to rotate the packet payload.
     The purpose of the rotation is to make it more difficult to
     conduct a known-plaintext attack against any ciphers that we 
     may later support.
  */
  payloadRotation=(*packet_len)-HEADERFIELDS_LEN;
  if (payloadRotation>0xff) payloadRotation=0xff;
  payloadRotation=random()%payloadRotation;
  if (debug>2) 
    fprintf(stderr,"Known Plaintext counter-measure: rotating packet payload by 0x%02x bytes.\n",
	    payloadRotation);
  if (debug>2) dump("unrotated packet",packet,*packet_len);

  /* Now rotate the payload */
  {
    unsigned char temp[256];

    /*Copy first part of payload to a temporary buffer */
    bcopy(&packet[HEADERFIELDS_LEN],&temp[0],payloadRotation);
    /* Copy the main part of the payload left by the rotation factor */
    bcopy(&packet[HEADERFIELDS_LEN+payloadRotation],&packet[HEADERFIELDS_LEN],
	  (*packet_len)-(HEADERFIELDS_LEN)-payloadRotation);
    /* Copy the temporary buffer to the end of the packet to complete the rotation */
    bcopy(&temp[0],&packet[(*packet_len)-payloadRotation],payloadRotation);
  }
  packet[OFS_ROTATIONFIELD]=payloadRotation;
  if (debug>3) dump("rotated packet",packet,*packet_len);

  return 0;
}

int packetAddHLRCreateRequest(unsigned char *packet,int packet_maxlen,int *packet_len)
{
  int packet_len_in=*packet_len;

  CHECK_PACKET_LEN(1);
  packet[(*packet_len)++]=ACTION_CREATEHLR;

  if (debug>2) dump("Variable request octets (HLR create)",&packet[packet_len_in],(*packet_len)-packet_len_in);

  return 0;
}

int packetAddVariableRequest(unsigned char *packet,int packet_maxlen,int *packet_len,
			     char *item,int instance,int start_offset,int bytes)
{
  /* Work out which item type we are asking for */
  int itemId;
  int packet_len_in=*packet_len;
  for(itemId=0;vars[itemId].name;itemId++)
    if (!strcmp(item,vars[itemId].name)) {
      break;
    }

  /* Sanity check the request */
  if (!vars[itemId].name) {
    if (debug) fprintf(stderr,"`%s' is not a known HLR variable.\n",item);
    return setReason("Requested unknown HLR variable");
  }
  itemId=vars[itemId].id;
  if (instance<-1) return setReason("Asked for illegal variable value instance");
  if (instance>0xfe) return setReason("Asked for illegal variable value instance");
  if ((itemId<0x80)&&instance) return setReason("Asked for secondary value of single-value variable");
  if (start_offset<0||start_offset>0xffff) return setReason("Asked for illegal variable value starting offset");
  if (bytes<0||(start_offset+bytes)>0xffff) {
    if (debug) fprintf(stderr,"Asked for %d bytes at offset %d\n",bytes,start_offset);
    return setReason("Asked for illegal variable value ending offset");
  }
  
  /* Add request to the packet */
  CHECK_PACKET_LEN(1+1+((itemId&0x80)?1:0)+2+2);
  packet[(*packet_len)++]=ACTION_GET;
  packet[(*packet_len)++]=itemId;
  if (instance==-1) instance=0xff;
  if (itemId&0x80) packet[(*packet_len)++]=instance;
  packet[(*packet_len)++]=start_offset>>8;
  packet[(*packet_len)++]=start_offset&0xff;
  packet[(*packet_len)++]=bytes>>8;
  packet[(*packet_len)++]=bytes&0xff;
  
  if (debug>2) dump("Variable request octets (var)",&packet[packet_len_in],(*packet_len)-packet_len_in);

  return 0;
}

int packetAddVariableWrite(unsigned char *packet,int packet_maxlen,
			   int *packet_len,
			   int itemId,int instance,unsigned char *value,
			   int start_offset,int value_len,int flags)
{
  /* Work out which item type we are asking for */
  int packet_len_in=*packet_len;

  int max_offset=start_offset+value_len-1;

  if (debug>1) printf("packetAddVariableWrite(start=%d,len=%d,flags=%d)\n",start_offset,value_len,flags);

  /* Sanity check */
  if (instance<0) return setReason("Asked for illegal variable value instance");
  if (instance>0xfe) return setReason("Asked for illegal variable value instance");
  if ((itemId<0x80)&&instance) return setReason("Asked for secondary value of single-value variable");
  if (start_offset<0||start_offset>0xffff) return setReason("Asked for illegal variable value starting offset");
  if (max_offset<0||max_offset>0xffff) return setReason("Asked for illegal variable value ending offset");
  
  /* Add request to the packet */
  CHECK_PACKET_LEN(1+1+((itemId&0x80)?1:0)+2+2+1);
  packet[(*packet_len)++]=ACTION_SET;
  packet[(*packet_len)++]=itemId;
  if (itemId&0x80) packet[(*packet_len)++]=instance;
  packet[(*packet_len)++]=start_offset>>8;
  packet[(*packet_len)++]=start_offset&0xff;
  packet[(*packet_len)++]=value_len>>8;
  packet[(*packet_len)++]=value_len&0xff;
  packet[(*packet_len)++]=flags;
  
  if (debug>2) dump("Packet with var write header",&packet[0],*packet_len);

  CHECK_PACKET_LEN(value_len);
  bcopy(&value[0],&packet[*packet_len],value_len);
  (*packet_len)+=value_len;

  if (debug>2) dump("Variable request octets (write)",&packet[packet_len_in],(*packet_len)-packet_len_in);

  return 0;
}

int extractRequest(unsigned char *packet,int *packet_ofs,int packet_len,
		   int *itemId,int *instance,unsigned char *value,
		   int *start_offset,int *bytes,int *flags)
{
  if (*packet_ofs<0||(*packet_ofs)+6>=packet_len) 
    return setReason("mal-formed request packet (packet too short/bad offset)");

  *itemId=packet[(*packet_ofs)++];

  if ((*itemId)&0x80) *instance=packet[(*packet_ofs)++];
  if (*instance==0xff) *instance=-1;

  *start_offset=packet[(*packet_ofs)++]<<8;
  *start_offset|=packet[(*packet_ofs)++];

  *bytes=packet[(*packet_ofs)++]<<8;
  *bytes|=packet[(*packet_ofs)++];

  *flags=packet[(*packet_ofs)++];
  if (debug>2) printf("Write flags = 0x%02x\n",*flags);

  if (*packet_ofs<0||(*packet_ofs)+(*bytes)>=packet_len)
    {
      if (debug) fprintf(stderr,"Packet offset is %d, length is %d, and asked for %d bytes.\n",*packet_ofs,packet_len,*bytes);
      return setReason("mal-formed request packet (too short for claimed data)");
    }

  bcopy(&packet[*packet_ofs],value,*bytes);
  (*packet_ofs)+=*bytes;

  return 0;
}


int extractResponses(struct in_addr sender,unsigned char *buffer,int len,struct response_set *responses)
{
  int ofs=OFS_PAYLOAD;
  
  while(ofs<len)
    {
      /* XXX should allocate responses from a temporary and bounded slab of memory */
      struct response *r=calloc(sizeof(struct response),1);
      if (!r) exit(setReason("calloc() failed."));
      
      r->code=buffer[ofs];
      r->sender=sender;
      /* XXX doesn't make sure it is SID instead of DID */
      bcopy(&buffer[HEADERFIELDS_LEN+1],r->sid,SID_SIZE);

      switch(buffer[ofs])
	{
	case ACTION_EOT:
	  if (debug>1) fprintf(stderr,"Reached response packet EOT.\n");
	case ACTION_DECLINED: case ACTION_OKAY:
	case ACTION_CREATEHLR:
	  r->response_len=0; break;
	case ACTION_GET: 
	  /* Followed by variable # to fetch.
	     XXX If variable number >=0x80 then get instance information */
	  r->response_len=1; break;
	case ACTION_ERROR:
	  r->response_len=buffer[++ofs];
	  break;
	case ACTION_DATA:
	  /* Extract variable value */
	  unpackageVariableSegment(&buffer[ofs+1],len-ofs,WITHDATA,r);
	  break;
	case ACTION_DONE:
	  r->value_offset=buffer[ofs+1];
	  r->response_len=1;
	  break;
	case ACTION_PAD:
	  /* Skip padding bytes */
	  r->response_len=1+buffer[ofs+1];	 
	  break;
	case ACTION_WROTE:
	  /* Extract info about the variable segment that was written.
	     This uses the same format as the request to write it, but without the data */
	  unpackageVariableSegment(&buffer[ofs+1],len-ofs,WITHOUTDATA,r);
	  r->response=NULL;
	  break;
	case ACTION_SET:
	case ACTION_DEL:
	case ACTION_XFER:
	default:
	  free(r);
	  if (debug>1) fprintf(stderr,"Encountered unimplemented response code 0x%02x @ 0x%x\n",buffer[ofs],ofs);
	  fixResponses(responses);
	  return setReason("Encountered unimplemented response type");
	}
      ofs++;
      if (r->response_len) {
	/* extract bytes of response */
	unsigned char *rr;
	if (r->response) rr=r->response; else rr=&buffer[ofs];
	r->response=malloc(r->response_len+1);
	if (!r->response) exit(setReason("malloc() failed."));
	bcopy(&rr[0],r->response,r->response_len);
	r->response[r->response_len]=0;
	ofs+=r->response_len;
      }

      /* Work out peer ID */
      r->sender=sender;
      for(r->peer_id=0;r->peer_id<peer_count;r->peer_id++)
	{
	  if (sender.s_addr==peers[r->peer_id].s_addr) break;
	}
      if (r->peer_id>peer_count) r->peer_id=-1;

      /* Link new response into chain */
      if (debug>2) printf("Linking response into response set.\n");
      r->prev=responses->last_response;
      if (responses->last_response)
	responses->last_response->next=r;
      else
	responses->responses=r;
      responses->last_response=r;
      responses->response_count++;

      responseFromPeer(responses,r->peer_id);

      if (debug>2) dumpResponses(responses);
    }
  
  fixResponses(responses);
  return 0;
}

int packageVariableSegment(unsigned char *data,int *dlen,struct hlrentry_handle *h,
			   int offset,int buffer_size)
{
  int bytes;
  int dlen_in=*dlen;

  if ((buffer_size-(*dlen))<8) return setReason("Insufficient buffer space for packageVariableSegment()");

  /* Figure out how many bytes we need to package */
  bytes=buffer_size-(*dlen)-8;
  if ((h->value_len-offset)<bytes) bytes=h->value_len-offset;
  if (bytes<0) bytes=0;
  if (debug>1) fprintf(stderr,"Packaging %d bytes of variable\n",bytes);

  /* Describe variable */

  /* Variable id and instance # (if required) */
  data[(*dlen)++]=h->var_id;
  if (h->var_id&0x80) data[(*dlen)++]=h->var_instance;

  /* Variable length */
  data[(*dlen)++]=h->value_len>>8;
  data[(*dlen)++]=h->value_len&0xff;

  /* Start offset in this segment */
  data[(*dlen)++]=(offset>>8)&0xff;
  data[(*dlen)++]=offset&0xff;

  /* Number of bytes in this segment */
  data[(*dlen)++]=(bytes>>8)&0xff;
  data[(*dlen)++]=bytes&0xff;
  if (debug>1) fprintf(stderr,"Packaging %d bytes\n",bytes);

  /* Package the variable value itself (or part thereof) */
  bcopy(&h->value[offset],&data[*dlen],bytes);
  (*dlen)+=bytes;

  if (debug>2) dump("Variable segment octets",&data[dlen_in],(*dlen)-dlen_in);

  return 0;
}

int unpackageVariableSegment(unsigned char *data,int dlen,int flags,struct response *r)
{
  r->response_len=0;
  if (dlen<7) return setReason("unpackageVariableSegment() fed insufficient data");
  
  r->var_id=data[r->response_len++];
  if (r->var_id&0x80) r->var_instance=data[r->response_len++]; else r->var_instance=0;
  if (r->var_instance==0xff) r->var_instance=-1;

  r->value_len=data[r->response_len++]<<8;
  r->value_len|=data[r->response_len++];

  r->value_offset=data[r->response_len++]<<8;
  r->value_offset|=data[r->response_len++];

  r->value_bytes=data[r->response_len++]<<8;
  r->value_bytes|=data[r->response_len++];

  r->response=&data[r->response_len];

  r->response_len+=r->value_bytes;

  if (flags!=WITHOUTDATA)
    if (r->response_len>dlen) 
      return setReason("unpackageVariableSegment() fed insufficient or corrupt data");
  
  return 0;
}
