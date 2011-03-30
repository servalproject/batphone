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

char *batman_socket=NULL;
char *batman_peerfile="/data/data/org.servalproject/var/batmand.peers";

int peer_count=0;
in_addr_t peers[MAX_PEERS];
unsigned char peer_replied[MAX_PEERS];

in_addr_t nominated_peers[256];
int nom_peer_count=0;

int additionalPeer(char *peer)
{
  in_addr_t pa;

  if (nom_peer_count>255) return setReason("Too many peers.  You can only nominate 255 peers in this version.");

  pa.s_addr=inet_addr(peer);
  if (pa.s_addr==INADDR_NONE) return setReason("Invalid peer address specified.");
  nominated_peers[nom_peer_count++]=pa;

  return 0;
}

int getPeerList()
{
  /* Generate the list of known peers.
     If using BATMAN layer 3, this needs to be the list of exact IP addresses of the peers,
     as we cannot reliably broadcast.
     Once BATMAN Advanced is available, we will be able to do that.
     In the mean time, we need to query BATMANd to find the known list of peers.  This is not
     quite as easy as we might wish.

     Also, while using layer 3 routing we should keep note of which nodes have repied so that
     we can not waste bandwidth by resending to them.  For this purpose we maintain a set of
     flags, peer_replied[], which is set to zero by us, and then set non-zero if that peer 
     solicits a reply, letting us know that we can suppress resends to that address.

     Broadcasting to interfaces is a special problem for managing replies, as we should never mark those
     peers as replied.  We will do this by setting their peer_replied[] flag to 2 instead of zero.
  */
  int i;

  peer_count=0;
  
  /* Add user specified peers */
  for(i=0;i<nom_peer_count;i++) peers[peer_count++]=nominated_peers[i];

  /* Add ourselves as a peer */
  peers[peer_count].s_addr=inet_addr("127.0.0.1");
  peer_replied[peer_count++]=0; 

  /* XXX Add broadcast address of every running interface */

  /* XXX Query BATMANd for other peers */
  if (batman_peerfile) readBatmanPeerFile(batman_peerfile,peers,&peer_count,MAX_PEERS);
  if (batman_socket) getBatmanPeerList(batman_socket,peers,&peer_count,MAX_PEERS);

  return 0;
}

int sendToPeers(unsigned char *packet,int packet_len,int method,int peerId,struct response_set *r)
{
  /* The normal version of BATMAN works at layer 3, so we cannot simply use an ethernet broadcast
     to get the message out.  BATMAN Advanced might solve this, though.

     So, in the mean time, we need to explicitly send the request to each peer.
     If this is a re-send, we don't want to bother the peers who have already responded,
     so check the peer_replied[] flags.
  */
  int i;
  int maxPeer=peer_count-1;
  int n=0;
  int ret;
  struct sockaddr_in peer_addr;

  bzero(&peer_addr, sizeof(peer_addr));
  peer_addr.sin_family=AF_INET;
  peer_addr.sin_port = htons(4110);

  if (method==REQ_PARALLEL) i=0; else { i=peerId; maxPeer=i; }
  for(;i<=maxPeer;i++)
    if (!responseFromPeerP(r,i))
      {
	peer_addr.sin_addr=peers[i];

	if (debug>1) fprintf(stderr,"Sending packet to peer #%d\n",i);
	
	ret=sendto(sock,packet,packet_len,0,(struct sockaddr *)&peer_addr,sizeof(peer_addr));
	if (ret<packet_len)
	  {
	    /* XXX something bad happened */
	    if (debug) fprintf(stderr,"Could not send to peer %s\n",inet_ntoa(peer_addr.sin_addr));
	  }
	else
	  {
	    if (debug>1) fprintf(stderr,"Sent request to peer %s\n",inet_ntoa(peer_addr.sin_addr));
	    n++;
	    /* If sending to only one peer, return now */ 
	    if (method==i) break;
	  }
      }
    else
      if (debug>1) fprintf(stderr,"Peer %s has already replied, so not sending again.\n",
			   inet_ntoa(peer_addr.sin_addr));

  if (debug) fprintf(stderr,"Sent request to %d peers.\n",n);

  return 0;

}
