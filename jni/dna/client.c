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

int packetSendFollowup(struct in_addr destination,
		       unsigned char *packet,int packet_len)
{
  struct sockaddr_in peer_addr;
  int r;
  
  bzero(&peer_addr, sizeof(peer_addr));
  peer_addr.sin_family=AF_INET;
  peer_addr.sin_port = htons( PORT_DNA );
  peer_addr.sin_addr.s_addr=destination.s_addr;

  if (!serverMode) {
    sock=socket(PF_INET,SOCK_DGRAM,0);
    if (sock<0) {
      fprintf(stderr,"Could not create UDP socket.\n");
      exit(-3);
    }
  }
  
  r=sendto(sock,packet,packet_len,0,(struct sockaddr *)&peer_addr,sizeof(peer_addr));
  if (r<packet_len)	{
    if (debug) fprintf(stderr,"Could not send to %s (r=%d, packet_len=%d)\n",inet_ntoa(destination),r,packet_len);
    perror("sendto");
  } else {
    if (debug>1) fprintf(stderr,"Sent request to client %s\n",inet_ntoa(destination));
  }
  return 0;
}

int packetSendRequest(int method,unsigned char *packet,int packet_len,int batchP,
		      unsigned char *transaction_id,struct response_set *responses)
{
  int i;
  int cumulative_timeout=0; /* ms */
  int this_timeout=125; /* ms */
  int peer_low,peer_high;
  int timeout_remaining;

  struct timeval time_in,now;
 
  /* Prepare ephemeral UDP socket (hence no binding)
     If in server mode, then we already have a socket available to us and appropriately bound */
  if (!serverMode) {
    sock=socket(PF_INET,SOCK_DGRAM,0);
    if (sock<0) {
      fprintf(stderr,"Could not create UDP socket.\n");
      exit(-3);
    }
  }
  
  /* Deal with special case */
  if (method==REQ_REPLY)
    {
      int r=sendto(sock,packet,packet_len,0,(struct sockaddr *)&recvaddr,sizeof(recvaddr));
      if (r<packet_len)	{
	if (debug) fprintf(stderr,"Could not send to client %s\n",inet_ntoa(client_addr));
      } else {
	if (debug>1) fprintf(stderr,"Sent request to client %s\n",inet_ntoa(client_addr));
      }
      return 0;
    }

  if (!peer_count) getPeerList();

  gettimeofday(&time_in,NULL);

  /* REQ_SERIAL & REQ_PARALLEL work in fundamentally different ways, 
     but it turns out the retry/timeout code is the dominant part.
     So we do a bit of fiddling around to make one loop that can handle both */
  if (method==REQ_SERIAL) {
    peer_low=0; peer_high=peer_count-1;
    /* If there are too many peers to allow sending to each three times, then we should 
       adjust our incremental timeout accordingly, so far as is practicable */
    if (this_timeout*peer_count*3>timeout)
      {
	this_timeout=timeout/(3*peer_count);
	if (this_timeout<10) this_timeout=10; /* 10ms minimum sending interval */
      }
  } else 
    { peer_low=-1; peer_high=-1;}

  while(cumulative_timeout<=timeout)
    {
      /* If not in serial mode, then send request to everyone immediately.
         Make sure we only ask once in parallel mode, since it will always ask everyone */
      if (method==REQ_PARALLEL) sendToPeers(packet,packet_len,method,0,responses);
      else if (method!=REQ_SERIAL)
	for(i=0;i<peer_count;i++) sendToPeers(packet,packet_len,method,i,responses);

      /* If in serial mode, send request to peers in turn until one responds positively, 
	 otherwise just deal with the reply fetching loop to listen to as many or few reply. */
      for(i=peer_low;i<=peer_high;i++) {
	struct response *rr;
	if (i>-1) sendToPeers(packet,packet_len,REQ_SERIAL,i,responses);
	
	/* Placing the timeout calculation here means that the total timeout is shared among
	   all peers in a serial request, but round-robining after each time-step.
	   We adjust this_timeout if there are many peers to allow 3 sends to each peer where possible.
	*/
	cumulative_timeout+=this_timeout;
	timeout_remaining=this_timeout;
	
	while(1)
	  {
	    /* Wait for response */
	    int r=getReplyPackets(method,i,batchP,responses,transaction_id,timeout_remaining);
	    if (r&&debug>1) fprintf(stderr,"getReplyPackets(): Returned on timeout\n");
	    
	    switch(method)
	      {
	      case REQ_PARALLEL:
		/* XXX We could stop once all peers have replied.
		   (need to update the test script if we do that, so that it tests with multiple
		    peers and so tests that we wait if not all peers have responded) */
		break;
	      case REQ_FIRSTREPLY:
		if (debug>1) fprintf(stderr,"Returning with first reply (REQ_FIRSTREPLY)\n");
		if (!r) return 0;
		break;
	      case REQ_SERIAL:
		if (!r) {
		  /* Stop if we have an affirmative response.
		     XXX - doesn't allow for out of order replies. */
		  if (debug>1) dumpResponses(responses);
		  rr=responses->last_response;
		  while (rr)
		    {
		      if (rr->checked) break;
		      if (debug>1) 
			fprintf(stderr,"Got a response code 0x%02x, checking if that is what we need.\n",rr->code);
		      switch (rr->code)
			{
			case ACTION_OKAY: case ACTION_DATA:
			  /* bingo */
			  if (!batchP) return 0;
			  break;
			}
		      rr->checked=1;
		      rr=rr->prev;
		    }
		  
		  /* not what we are after, so clear response and try with next peer */
		  clearResponses(responses);
		}
		break;
	      }
	    
	    /* Wait for the previous timeout to really expire,
	       (this is for the case where all peers have replied) */
	    {
	      int elapsed_usecs=0;
	      int cumulative_usecs=cumulative_timeout*1000;
	      int remaining_usecs;
	      
	      gettimeofday(&now,NULL);
	      elapsed_usecs=(now.tv_sec-time_in.tv_sec)*1000000;
	      elapsed_usecs+=(now.tv_usec-time_in.tv_usec);
	      
	      remaining_usecs=cumulative_usecs-elapsed_usecs;
	      
	      if (remaining_usecs<=0) break;
	      else timeout_remaining=remaining_usecs/1000;		    
	    }
	    
	  }
      }
      cumulative_timeout+=this_timeout;
    }
  if (debug>1) if (cumulative_timeout>=timeout) 
		 fprintf(stderr,"Request timed out after retries (timeout=%d, elapsed=%d).\n",
			 timeout,cumulative_timeout);

  return 0;
}

/* Create a new HLR entry on a peer.
   Should always try localhost as first peer, which is why
   sendToPeers() does it that way.  We just have to remember to
   ask for serialised attempts, rather than all at once.
*/
int requestNewHLR(char *did,char *pin,char *sid)
{
  unsigned char packet[8000];
  int packet_len=0;
  struct response_set responses;
  unsigned char transaction_id[TRANSID_SIZE];

  bzero(&responses,sizeof(responses));

  /* Prepare the request packet */
  if (packetMakeHeader(packet,8000,&packet_len,NULL)) return -1;
  bcopy(&packet[OFS_TRANSIDFIELD],transaction_id,TRANSID_SIZE);
  if (packetSetDid(packet,8000,&packet_len,did)) return -1;
  if (packetAddHLRCreateRequest(packet,8000,&packet_len)) return -1;
  if (packetFinalise(packet,8000,&packet_len)) return -1;

  /* Send it to peers, starting with ourselves, one at a time until one succeeds.
     XXX - This could take a while if we have long timeouts for each. */
  if (packetSendRequest(REQ_SERIAL,packet,packet_len,NONBATCH,transaction_id,&responses)) return -1;

  /* Extract response */
  if (debug>2) dumpResponses(&responses);
  if (!responses.response_count) {
    printf("NOREPLY\n");
    return -1;
  }
  switch(responses.responses->code)
    {
    case ACTION_DECLINED:
      printf("DECLINED\n");
      return -1;
      break;
    case ACTION_OKAY:
      {
	char sid[128];
	int ofs=0;
	extractSid(&responses.responses->sid[0],&ofs,&sid[0]);
	printf("OK:%s\n",sid);
      }
      return 0;
      break;
    default:
      printf("ERROR:Unknown response 0x%02x\n",responses.responses->code);
      return -1;
    }
 
  return setReason("Request creation of new HLR not implemented");
}

/* Some data types can end in @ if they require the address of the sender to be appended for correct local interpretation */
int fixResponses(struct response_set *responses)
{
  struct response *rr;

  if (debug>1) fprintf(stderr,"Fixing response set\n");

  if (!responses) return -1;

  rr=responses->responses;
  while(rr)
    {
      if (debug>1) fprintf(stderr,"  len=%d, rr->code=%02x, rr->var_id=%02x\n",
			   rr->value_bytes,rr->code,rr->var_id);
      if (rr->value_bytes>0&&rr->code==ACTION_DATA&&rr->var_id==VAR_LOCATIONS)
	{
	  if (debug>1) fprintf(stderr,"  response='%s'\n",rr->response);
	  if (rr->response[rr->value_bytes-1]=='@')
	    {
	      /* Append response with IP address of sender */
	      char *addr=inet_ntoa(rr->sender);
	      int alen=strlen(addr);
	      char *new = malloc(rr->value_bytes+alen+1);
	      if (debug>1) fprintf(stderr,"Fixing LOCATIONS response '%s' received from '%s'\n",
				   rr->response,addr);
	      if (!new) return -1;
	      bcopy(rr->response,new,rr->value_bytes);
	      bcopy(addr,&new[rr->value_bytes],alen+1);
	      free(rr->response); rr->response=NULL;
	      rr->response=new;
	      rr->value_len+=alen;
	      rr->value_bytes+=alen;
	      if (debug>1) fprintf(stderr,"Response string now '%s'\n",rr->response);
	    }
	}
      rr=rr->next;
    }
  return 0;
}

int getReplyPackets(int method,int peer,int batchP,
		    struct response_set *responses,
		    unsigned char *transaction_id,int timeout)
{
  /* set timeout alarm */
  
  /* get packets until timeout, or until we get a packet from the specified peer
     if method==REQ_SERIAL.  If REQ_SERIAL we also reject packets from other 
     senders as they must be spoofs.
  */
  struct timeval t;
  int timeout_secs;
  int timeout_usecs;
  int to=timeout;
  int len;

  if (debug>1) printf("getReplyPackets(policy=%d)\n",method);

  /* Work out when the timeout will expire */
  gettimeofday(&t,NULL); 
  timeout_secs=t.tv_sec; timeout_usecs=t.tv_usec;
  if (to>1000) { timeout_secs+=(to/1000); to=to%1000; }
  timeout_usecs+=to*1000; if (timeout_usecs>1000000) { timeout_secs++; timeout_usecs-=1000000; }
  
  while(1) {
    unsigned char buffer[16384];
    socklen_t recvaddrlen=sizeof(recvaddr);
    struct pollfd fds;

    bzero((void *)&recvaddr,sizeof(recvaddr));
    fds.fd=sock; fds.events=POLLIN; fds.revents=0;

    while (poll(&fds,1,10 /* wait for 10ms at a time */)==0)
      {
	gettimeofday(&t,NULL);
	if (t.tv_sec>timeout_secs) return 1;
	if (t.tv_sec==timeout_secs&&t.tv_usec>=timeout_usecs) return 1;
      }
    len=recvfrom(sock,buffer,sizeof(buffer),0,&recvaddr,&recvaddrlen);
	if (len<=0) return setReason("Unable to receive packet.");

    client_port=((struct sockaddr_in*)&recvaddr)->sin_port;
    client_addr=((struct sockaddr_in*)&recvaddr)->sin_addr;

    if (debug) fprintf(stderr,"Received reply from %s (len=%d).\n",inet_ntoa(client_addr),len);
    if (debug>1) dump("recvaddr",(unsigned char *)&recvaddr,recvaddrlen);
    if (debug>2) dump("packet",(unsigned char *)buffer,len);

    if (dropPacketP(len)) {
      if (debug) fprintf(stderr,"Simulation mode: Dropped packet due to simulated link parameters.\n");
      continue;
    }
    if (!packetOk(buffer,len,transaction_id)) {
      /* Packet passes tests - extract responses and append them to the end of the response list */
      if (extractResponses(client_addr,buffer,len,responses)) 
	return setReason("Problem extracting response fields from reply packets");
      if (method==REQ_SERIAL||method==REQ_FIRSTREPLY) {
	if (!batchP) return 0;
	/* In batch mode we need ACTION_DONE to mark end of transmission. 
	   While it gets sent last, out-of-order delivery means we can't rely on
	   such a nice arrangement. */
	{
	  /* XXX inefficient for long lists.
	     XXX can be made better by working backwards from end using double-linked list and 
	     remembering the previous length of the list */
	  struct response *r=responses->responses;
	  while(r)
	    {
	      if (r->code==ACTION_DONE) return 0;
	      r=r->next;
	    }
	}
      }
      else {
	if (debug>1) printf("Waiting for more packets, since called with policy %d\n",method);
      }
    } else {
      if (debug) setReason("Ignoring invalid packet");
    }      
  }
}

int writeItem(char *sid,int var_id,int instance,unsigned char *value,
	      int value_start,int value_length,int flags)
{
  unsigned char packet[8000];
  int packet_len=0;
  struct response_set responses;
  struct response *r;
  unsigned char transaction_id[TRANSID_SIZE];

  bzero(&responses,sizeof(responses));

  if (debug>1) fprintf(stderr,"Writing %d bytes of var %02x/%02x @ 0x%d flags=%d\n",
		       value_length,var_id,instance,value_start,flags);

  if (!sid) {
    printf("ERROR:Must use SID when writing values.\n");
    return -1;
  }

  /* Split long writes into many short writes.
     (since each write is acknowledged, we don't have to worry about batch mode) */
  if (value_length-value_start>MAX_DATA_BYTES)
    { 
      int o;      
      if (debug) fprintf(stderr,"Writing large value (%d bytes)\n",value_length-value_start);
      for(o=value_start;o<value_length;o+=MAX_DATA_BYTES)
	{
	  int bytes=MAX_DATA_BYTES;
	  if (o+bytes>value_length) bytes=value_length-o;
	  if (debug>1) fprintf(stderr,"  writing [%d,%d)\n",o,o+bytes-1);
	  if (writeItem(sid,var_id,instance,&value[o-value_start],o,bytes,
			flags|((o>value_start)?SET_FRAGMENT:0)))
	    {
	      if (debug) fprintf(stderr,"   - writing installment failed\n");
	      return setReason("Failure during multi-packet write of long-value");
	    }
	}
      printf("OK:%s\n",sid);
      return 0;
    }

  /* Prepare the request packet */
  if (packetMakeHeader(packet,8000,&packet_len,NULL)) return -1;
  bcopy(&packet[OFS_TRANSIDFIELD],transaction_id,TRANSID_SIZE);
  if (packetSetSid(packet,8000,&packet_len,sid)) return -1;
  if (packetAddVariableWrite(packet,8000,&packet_len,var_id,instance,
			     value,value_start,value_length,flags)) return -1;
  if (packetFinalise(packet,8000,&packet_len)) return -1;

  /* XXX should be able to target to the peer holding the SID, if we have it.
     In any case, we */
  if (packetSendRequest(REQ_FIRSTREPLY,packet,packet_len,NONBATCH,transaction_id,&responses)) return -1;

  r=responses.responses;
  while(r)
    {
      int slen=0;
      char sid[SID_SIZE*2+1];
      extractSid(r->sid,&slen,sid);
      switch(r->code)
	{
	case ACTION_ERROR:
	  /* We allocate an extra byte to allow us to do this */
	  r->response[r->response_len]=0;
	  printf("ERROR:%s\n",(char *)r->response);
	  break;
	case ACTION_OKAY: printf("ERROR:Unexpected OK response\n"); break;
	case ACTION_DECLINED: printf("DECLINED:%s\n",sid); break;
	case ACTION_WROTE: 
	  /* Supress success messages when writing fragments */
	  if (!(flags&SET_FRAGMENT)) printf("WROTE:%s\n",sid); break;
	case ACTION_DATA: printf("ERROR:DATA reponse not implemented\n"); break;
	case ACTION_GET: printf("ERROR:You cant respond with GET\n"); break;
	case ACTION_SET: printf("ERROR:You cant respond with SET\n"); break;
	case ACTION_DEL: printf("ERROR:You cant respond with DEL\n"); break;
	case ACTION_CREATEHLR: printf("ERROR:You cant respond with CREATEHLR\n"); break;
	case ACTION_PAD: /* ignore it */ break;
	case ACTION_EOT: /* ignore it */ break;
	default: printf("ERROR:Unexpected response code 0x%02x\n",r->code);
	}
      fflush(stdout);
      r=r->next;
    }

  return 0;
}

int peerAddress(char *did,char *sid,int flags)
{
  unsigned char transaction_id[TRANSID_SIZE];
  unsigned char packet[8000];
  int packet_len=0;
  struct response *r;
  struct response_set responses;

  int i;
  int pc=0;
  struct in_addr mypeers[256];
  int method;

  bzero(&responses,sizeof(responses));

  for(i=0;i<TRANSID_SIZE;i++) transaction_id[i]=random()&0xff;

  /* Prepare the request packet */
  if (packetMakeHeader(packet,8000,&packet_len,transaction_id)) 
    {
      if (debug) fprintf(stderr,"%s() failed at line %d\n",__FUNCTION__,__LINE__);
      return -1;
    }
  if (did&&(!sid))
    { if (packetSetDid(packet,8000,&packet_len,did)) {
	if (debug) fprintf(stderr,"%s() failed at line %d\n",__FUNCTION__,__LINE__);
	return -1; }
    }
  else if (sid&&(!did))
    { if (packetSetSid(packet,8000,&packet_len,sid)) {
	if (debug) fprintf(stderr,"%s() failed at line %d\n",__FUNCTION__,__LINE__);
	return -1;
      }
    }
  else {
    if (debug) fprintf(stderr,"%s() failed at line %d\n",__FUNCTION__,__LINE__);
    return setReason("You must request items by DID or SID, not neither, nor both");
  }

      
  if (packetAddVariableRequest(packet,8000,&packet_len,
			       "dids",0,0,128 /* only small things please */)) {
    if (debug) fprintf(stderr,"%s() failed at line %d\n",__FUNCTION__,__LINE__);
    return -1;
  }
  if (packetFinalise(packet,8000,&packet_len)) {
    if (debug) fprintf(stderr,"%s() failed at line %d\n",__FUNCTION__,__LINE__);
    return -1;
  }

  method=REQ_PARALLEL;
  if (sid) method=REQ_FIRSTREPLY;
  if (packetSendRequest(method,packet,packet_len,NONBATCH,transaction_id,&responses)) {
    if (debug) fprintf(stderr,"peerAddress() failed because packetSendRequest() failed.\n");
    return -1;
  }

  r=responses.responses;
  if (!r)
    {
      if (debug) fprintf(stderr,"peerAddress() failed because noone answered.\n");
      return -1;
    }
  while(r)
    {
      if (flags&1) printf("%s\n",inet_ntoa(r->sender));
      if (flags&2) {
	if (pc<256) mypeers[pc++]=r->sender;
      }
      break;
      r=r->next;
    }

  /* Set the peer list to exactly the list of nodes that we have identified */
  if (flags&2)
    {
      for(i=0;i<pc;i++)
	peers[i]=mypeers[i];
      peer_count=pc;
    }

  return 0;
}

int requestItem(char *did,char *sid,char *item,int instance,unsigned char *buffer,int buffer_length,int *len,
		unsigned char *transaction_id)
{
  unsigned char packet[8000];
  int packet_len=0;
  struct response *r;
  struct response_set responses;

  int successes=0;
  int errors=0;
  int method;

  bzero(&responses,sizeof(responses));

  /* Prepare the request packet */
  if (packetMakeHeader(packet,8000,&packet_len,transaction_id)) 
    {
      if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
      return -1;
    }
  if (did&&(!sid))
    { if (packetSetDid(packet,8000,&packet_len,did)) {
	if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
	return -1; }
    }
  else if (sid&&(!did))
    { if (packetSetSid(packet,8000,&packet_len,sid)) {
	if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
	return -1;
      }
    }
  else {
    if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
    return setReason("You must request items by DID or SID, not neither, nor both");
  }

      
  if (packetAddVariableRequest(packet,8000,&packet_len,
			       item,instance,0,buffer_length)) {
    if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
    return -1;
  }
  if (packetFinalise(packet,8000,&packet_len)) {
    if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
    return -1;
  }

  method=REQ_PARALLEL;
  if (sid) method=REQ_FIRSTREPLY;
  if (packetSendRequest(method,packet,packet_len,(instance==-1)?BATCH:NONBATCH,transaction_id,&responses)) {
    if (debug) fprintf(stderr,"requestItem() failed because packetSendRequest() failed.\n");
    return -1;
  }

  r=responses.responses;
  while(r)
    {
      char sid[SID_SIZE*2+1];
      int slen=0;
      extractSid(r->sid,&slen,sid);
      switch(r->code)
	{
	case ACTION_OKAY: printf("OK:%s\n",sid); if (buffer) {strcpy(buffer,sid); *len=strlen(sid); } successes++; break;
	case ACTION_DECLINED: printf("DECLINED:%s\n",sid); errors++; break;
	case ACTION_DATA: 
	  /* Display data.
	     The trick is knowing the format of the data.
	     Fortunately we get the variable id etc.
	     XXX Need to deal with fragmented values.
	     (perhaps the fragments should get auto-assembled when accepting the responses)
	  */
	  switch(r->var_id)
	    {
	    case VAR_DIDS:
	      {
		char did[DID_MAXSIZE+1];
		int dlen=0;
		did[0]=0;
		extractDid(r->response,&dlen,did);
		printf("DIDS:%s:%d:%s\n",sid,r->var_instance,did);
		if (buffer) {strcpy(buffer,did); *len=strlen(did); }
		successes++;
	      }
	      break;
	    case VAR_NOTE:
	    default:
	      /* ASCII formatted variable types */
	      {
		int v=0;
	        int i=0;
		FILE *outputfile=stdout;
		while(vars[v].name&&vars[v].id!=r->var_id) v++;
		if (!vars[v].id) printf("0x%02x",r->var_id);
		while(vars[v].name[i]) fputc(toupper(vars[v].name[i++]),stdout);
		printf(":%s:%d:",sid,r->var_instance);
		*len=r->value_len;
		
		if (outputtemplate)
		  {
		    char outputname[8192];
		    snprintf(outputname,8192,outputtemplate,sid,r->var_id,r->var_instance);
		    outputfile=fopen(outputname,"w");
		    if (!outputfile) printf("ERROR:Could not open output file '%s'",outputname);
		    if (debug) fprintf(stderr,"Writing output to '%s'\n",outputname);
		  }

		if (outputfile) fwrite(r->response,r->value_bytes,1,outputfile);

		if (r->value_bytes<r->value_len)
		  {
		    /* Partial response, so ask for the rest of it */
		    unsigned char packet[8000];
		    int packet_len=0;
		    struct response *rr;
		    struct response_set responses;
		    int offset,max_bytes;
		    int *recv_map;
		    int recv_map_size=1+(r->value_len/MAX_DATA_BYTES);
		    int needMoreData;
		    int tries=0;
			
			recv_map=alloca(recv_map_size);

		    /* work out EXACTLY how many installments we need */
		    while (((recv_map_size-1)*MAX_DATA_BYTES)>=r->value_len) recv_map_size--;

		    recv_map[0]=0; /* we received the first installment, so mark it off ... */
		    /* ... but we haven't received the rest */
		    for(i=1;i<recv_map_size;i++) recv_map[i]=0;		    

		    /* Ask for all remaining pieces in parallel, then keep track of what has arrived
		       XXX - Not yet implemented.  Currently uses a slow serial method, worse than TFTP */  
		    needMoreData=recv_map_size-1;

		    while(needMoreData&&(tries++<15))
		      {
			if (debug>1) fprintf(stderr,"Multi-packet request: try %d, %d fragments remaining.\n",tries,needMoreData);
			needMoreData=0;
			for(i=0;i<recv_map_size;i++)
			  if (!recv_map[i])
			    {
			      needMoreData++;
			      offset=i*MAX_DATA_BYTES;
			      
			      if (debug>1) fprintf(stderr,"Asking for variable segment @ offset %d\n",offset);
			      
			      /* Send accumulated request direct to the responder */
			      if (packet_len>=MAX_DATA_BYTES)
				{
				  if (packetFinalise(packet,8000,&packet_len)) {
				    if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
				    return -1;
				  }
				  packetSendFollowup(r->sender,packet,packet_len);
				  packet_len=0;
				}
			      /* Prepare a new request packet if one is not currently being built */
			      if (!packet_len)
				{
				  if (packetMakeHeader(packet,8000,&packet_len,transaction_id)) {
				    if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
				    return -1;
				  }
				  if (packetSetSid(packet,8000,&packet_len,sid)) {
				    if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
				    return setReason("SID went mouldy during multi-packet get");
				  }
				}
			      
			      max_bytes=65535-offset;
			      if (max_bytes>buffer_length) max_bytes=buffer_length;
			      if (packetAddVariableRequest(packet,8000,&packet_len,
							   item,r->var_instance,offset,max_bytes)) {
				if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
				return -1;
			      }
			    }
			/* Send accumulated request direct to the responder */
			if (packet_len)
			  {
			    if (packetFinalise(packet,8000,&packet_len)) {
			      if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
			      return -1;
			    }
			    packetSendFollowup(r->sender,packet,packet_len);
			    packet_len=0;
			  }
			
			/* Collect responses to our multiple requests */
			bzero(&responses,sizeof(responses));

			/* XXX should target specific peer that sent first piece */
			getReplyPackets(REQ_PARALLEL,i,0,&responses,transaction_id,250);
			rr=responses.responses;
			while(rr)
			  {
			    if (rr->code==ACTION_DATA&&rr->var_id==r->var_id&&rr->var_instance==r->var_instance)
			      {
				int piece=rr->value_offset/MAX_DATA_BYTES;
				if (!recv_map[piece])
				  {
				    if (debug>1) fprintf(stderr,"Extracted value fragment @ offset %d, with %d bytes\n",rr->value_offset,rr->value_bytes);
				    if (debug>2) dump("Fragment",rr->response,rr->value_bytes);
				    fseek(outputfile,rr->value_offset,SEEK_SET);
				    fwrite(rr->response,rr->value_bytes,1,outputfile);
				    if (buffer) bcopy(rr->response,&buffer[rr->value_offset],rr->value_bytes);
				    recv_map[piece]=1;
				  }
				else
				  {
				    if (debug>1) fprintf(stderr,"DUPLICATE value fragment @ offset %d, with %d bytes\n",rr->value_offset,rr->value_bytes);
				   
				  }
			      }
			    rr=rr->next;
			  }
			clearResponses(&responses);
		      }
		  }
		else
		  {
		    if (buffer) bcopy(r->response,&buffer[r->value_offset],r->value_bytes);
		  }
		if (outputtemplate) fclose(outputfile); else fflush(outputfile);		    
		printf("\n");
		if (debug) fprintf(stderr,"requestItem() returned DATA\n");
		return 0;
		break;
	      }
	    } 
	  break;
	case ACTION_DONE: 
	  printf("DONE:%s:%d\n",sid,r->response[0]);
	  break;
	case ACTION_GET: printf("ERROR:You cant respond with GET\n"); break;
	case ACTION_SET: printf("ERROR:You cant respond with SET\n"); break;
	case ACTION_WROTE: printf("ERROR:You cant respond with WROTE\n"); break;
	case ACTION_DEL: printf("ERROR:You cant respond with DEL\n"); break;
	case ACTION_CREATEHLR: printf("ERROR:You cant respond with CREATEHLR\n"); break;
	case ACTION_PAD: /* ignore it */ break;
	case ACTION_EOT: /* ignore it */ break;
	default: printf("ERROR:Unexpected response code 0x%02x\n",r->code);
	}
      fflush(stdout);
      r=r->next;
    }

  if (debug) fprintf(stderr,"requestItem() failed at line %d\n",__LINE__);
  return -1;
}
