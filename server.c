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

unsigned char *hlr=NULL;
int hlr_size=0;

struct sockaddr recvaddr;
struct in_addr client_addr;
int client_port;

int server(char *backing_file,int size,int foregroundMode)
{
  
  struct sockaddr_in bind_addr;
  
  /* Get backing store */
  if (!backing_file)
    {
      /* transitory storage of HLR data, so just malloc() the memory */
      hlr=calloc(size,1);
      if (!hlr) exit(setReason("Failed to calloc() HLR database."));
      if (debug) fprintf(stderr,"Allocated %d byte temporary HLR store\n",size);
    }
  else
    {
      unsigned char zero[8192];
      FILE *f=fopen(backing_file,"r+");
      if (!f) f=fopen(backing_file,"w+");
      if (!f) exit(setReason("Could not open backing file."));
      bzero(&zero[0],8192);
      fseek(f,0,SEEK_END);
      errno=0;
      while(ftell(f)<size)
	{
	  int r;
	  fseek(f,0,SEEK_END);
	  if ((r=fwrite(zero,8192,1,f))!=1)
	    {
	      perror("fwrite");
	      exit(setReason("Could not enlarge backing file to requested size (short write)"));
	    }
	  fseek(f,0,SEEK_END);
	}
      
      if (errno) perror("fseek");
      if (fwrite("",1,1,f)!=1)
	{
	  fprintf(stderr,"Failed to set backing file size.\n");
	  perror("fwrite");
	}
      hlr=(unsigned char *)mmap(NULL,size,PROT_READ|PROT_WRITE,MAP_SHARED|MAP_NORESERVE,fileno(f),0);
      if (hlr==MAP_FAILED) {
	perror("mmap");
	exit(setReason("Memory mapping of HLR backing file failed."));
      }
      if (debug) fprintf(stderr,"Allocated %d byte HLR store backed by file `%s'\n",
			 size,backing_file);
    }
  hlr_size=size;


  sock=socket(PF_INET,SOCK_DGRAM,0);
  if (sock<0) {
    fprintf(stderr,"Could not create UDP socket.\n");
    perror("socket");
    exit(-3);
  }

  bind_addr.sin_family = AF_INET;
  bind_addr.sin_port = htons( 4110 );
  bind_addr.sin_addr.s_addr = htonl( INADDR_ANY );
  if(bind(sock,(struct sockaddr *)&bind_addr,sizeof(bind_addr))) {
    fprintf(stderr,"MP HLR server could not bind to UDP port 4110\n");
    perror("bind");
    exit(-3);
  }

  /* Detach from the console */
  if (!foregroundMode) daemon(0,0);

  while(1) {
    unsigned char buffer[16384];
    socklen_t recvaddrlen=sizeof(recvaddr);
    pollfd fds;
	int len;

    bzero((void *)&recvaddr,sizeof(recvaddr));
    fds.fd=sock; fds.events=POLLIN;
    
    /* Wait patiently for packets to arrive */
    while (poll(&fds,1,1000)<1)	sleep(0);

    len=recvfrom(sock,buffer,sizeof(buffer),0,&recvaddr,&recvaddrlen);

    client_port=((struct sockaddr_in*)&recvaddr)->sin_port;
    client_addr=((struct sockaddr_in*)&recvaddr)->sin_addr;

    if (debug) fprintf(stderr,"Received packet from %s (len=%d).\n",inet_ntoa(client_addr),len);
    if (debug>1) dump("recvaddr",(unsigned char *)&recvaddr,recvaddrlen);
    if (debug>3) dump("packet",(unsigned char *)buffer,len);
    if (dropPacketP(len)) {
      if (debug) fprintf(stderr,"Simulation mode: Dropped packet due to simulated link parameters.\n");
      continue;
    }
    if (!packetOk(buffer,len,NULL)) process_packet(buffer,len,&recvaddr,recvaddrlen);
    else {
      if (debug) setReason("Ignoring invalid packet");
    }
    if (debug>1) fprintf(stderr,"Finished processing packet, waiting for next one.\n");
  }  
}

int processRequest(unsigned char *packet,int len,
		   struct sockaddr *sender,int sender_len,
		   unsigned char *transaction_id,char *did,char *sid)
{
  /* Find HLR entry by DID or SID, unless creating */
  int ofs,rofs=0;
  int records_searched=0;
  
  int prev_pofs=0;
  int pofs=OFS_PAYLOAD;

  while(pofs<len)
    {
      if (debug>1) fprintf(stderr,"  processRequest: len=%d, pofs=%d, pofs_prev=%d\n",len,pofs,prev_pofs);
      /* Avoid infinite loops */
      if (pofs<=prev_pofs) break;
      prev_pofs=pofs;

      if (packet[pofs]==ACTION_CREATEHLR)
	{
	  /* Creating an HLR requires an initial DID number and definately no SID -
	     you can't choose a SID. */
	  if (debug>1) fprintf(stderr,"Creating a new HLR record. did='%s', sid='%s'\n",did,sid);
	  if (!did[0]) return respondSimple(NULL,ACTION_DECLINED,NULL,0,transaction_id);
	  if (sid[0])  return respondSimple(sid,ACTION_DECLINED,NULL,0,transaction_id);
	  if (debug>1) fprintf(stderr,"Verified that create request supplies DID but not SID\n");
	  
	  {
	    char sid[128];
	    /* make HLR with new random SID and initial DID */
	    if (!createHlr(did,sid))
	      return respondSimple(sid,ACTION_OKAY,NULL,0,transaction_id);
	    else
	      return respondSimple(NULL,ACTION_DECLINED,NULL,0,transaction_id);
	  }
	  pofs+=1;
	  pofs+=1+SID_SIZE;
	}
      else
	{
	  switch(packet[pofs])
	    {
	    case ACTION_PAD: /* Skip padding */
	      pofs++;
	      pofs+=1+packet[pofs];
	      break;
	    case ACTION_EOT:  /* EOT */
	      pofs=len;
	      break;
	    case ACTION_SENDSMS: /* Send an SMS to the specified SID. */ 
	      /*  You cannot use a DID */
	      if (did[0]) return respondSimple(NULL,ACTION_DECLINED,NULL,0,transaction_id);
	      
	      /* XXX Thomas to complete and make sure it works:
		 1. Unpack SMS message.
		 2. Make sure it hasn't already been delivered.
		 3. Make sure there is space to deliver it .
		 4. Deliver it to the next free message slot
	      */
		  {
	      // extractSMS(...); -- don't forget to check maximum message length
	      int instance=-1; /* use first free slot */
	      unsigned char oldvalue[65536];
	      int oldl=65536; 
		  int oldr=hlrGetVariable(hlr,ofs,VAR_SMESSAGES,instance,oldvalue,&oldl);

	      if (oldr) { 
		/* Already exists, so no need to deliver it again */
		respondSimple(sid,ACTION_SMSRECEIVED,NULL,0,transaction_id);
	      } 
	      else
		{
		  /* Write new value back */
		  if (hlrSetVariable(hlr,ofs,VAR_SMESSAGES,instance,oldvalue,oldl))
		    {
		      setReason("Failed to write variable");
		      return 
			respondSimple(NULL,ACTION_ERROR,(unsigned char *)"No space for message",0,transaction_id);
		    }
		  if (debug>2) { fprintf(stderr,"HLR after writing:\n"); hlrDump(hlr,ofs); }
		  
		  /* Reply that we wrote the fragment */
		  respondSimple(sid,ACTION_WROTE,&packet[rofs],6,
				transaction_id);
		}
	      }
	      break;
	    case ACTION_SET:
	      ofs=0;
	      if (debug>1) fprintf(stderr,"Looking for hlr entries with sid='%s' / did='%s'\n",sid,did);
	      while(findHlr(hlr,&ofs,sid,did))
		{
		  int itemId,instance,start_offset,bytes,flags;
		  unsigned char value[9000],oldvalue[65536];
		  int oldr,oldl;
		  
		  if (debug>1) fprintf(stderr,"findHlr found a match for writing at 0x%x\n",ofs);
		  if (debug>2) hlrDump(hlr,ofs);
		  
		  /* XXX consider taking action on this HLR
		     (check PIN first depending on the action requested) */
	      
		  /* XXX Doesn't verify PIN authentication */
		  
		  /* Get write request */
		  if ((!sid)||(!sid[0])) {
		    setReason("You can only set variables by SID");
		    return
		      respondSimple(NULL,ACTION_ERROR,(unsigned char *)"SET requires authentication by SID",0,transaction_id);
		  }
		    
		  pofs++; rofs=pofs;
		  if (extractRequest(packet,&pofs,len,
				     &itemId,&instance,value,
				     &start_offset,&bytes,&flags))
		    {
		      setReason("Could not extract ACTION_SET request");
		      return 
			respondSimple(NULL,ACTION_ERROR,(unsigned char *)"Mal-formed SET request",0,transaction_id);
		    }
		  
		  /* Get the stored value */
		  oldl=65536;
		  oldr=hlrGetVariable(hlr,ofs,itemId,instance,oldvalue,&oldl);
		  if (oldr) {
		    if (flags==SET_NOREPLACE) {
		      oldl=0;
		    } else {
		      setReason("Tried to SET_NOCREATE/SET_REPLACE a non-existing value");
		      return 
			  respondSimple(NULL,ACTION_ERROR,
					(unsigned char *)"Cannot SET NOCREATE/REPLACE a value that does not exist",
					0,transaction_id);
		    }
		  } else {
		    if (flags==SET_NOREPLACE) {
		      setReason("Tried to SET_NOREPLACE an existing value");
		      if (debug>1) dump("Existing value",oldvalue,oldl);
		      return 
			respondSimple(NULL,ACTION_ERROR,
				      (unsigned char *)"Cannot SET NOREPLACE; a value exists",
				      0,transaction_id);
		      }
		  }
		  /* Replace the changed portion of the stored value */
		  if ((start_offset+bytes)>oldl) {
		    bzero(&oldvalue[oldl],start_offset+bytes-oldl);
		    oldl=start_offset+bytes;
		  }
		  bcopy(&value[0],&oldvalue[start_offset],bytes);
		    
		  /* Write new value back */
		  if (hlrSetVariable(hlr,ofs,itemId,instance,oldvalue,oldl))
		    {
		      setReason("Failed to write variable");
		      return 
			respondSimple(NULL,ACTION_ERROR,(unsigned char *)"Failed to SET variable",0,transaction_id);
		    }
		  if (debug>2) { fprintf(stderr,"HLR after writing:\n"); hlrDump(hlr,ofs); }
		  
		  /* Reply that we wrote the fragment */
		  respondSimple(sid,ACTION_WROTE,&packet[rofs],6,
				transaction_id);
		  /* Advance to next record and keep searching */
		  if (nextHlr(hlr,&ofs)) break;
		}
	      break;
	    case ACTION_GET:
	      ofs=0;
	      if (debug>1) fprintf(stderr,"Looking for hlr entries with sid='%s' / did='%s'\n",sid,did);
	      while(findHlr(hlr,&ofs,sid,did))
		{
		  int var_id=packet[pofs+1];
		  int instance=packet[pofs+2];
		  int offset=(packet[pofs+3]<<8)+packet[pofs+4];
		  int sendDone=0;
		  struct hlrentry_handle *h;

		  if (debug>1) fprintf(stderr,"findHlr found a match at 0x%x\n",ofs);
		  if (debug>2) hlrDump(hlr,ofs);
		  
		  /* XXX consider taking action on this HLR
		     (check PIN first depending on the action requested) */

		  /* Form a reply packet containing the requested data */
		  
		  if (instance==0xff) instance=-1;
		  
		  if (debug>1) fprintf(stderr,"Responding to ACTION_GET (var_id=%02x, instance=%02x, pofs=0x%x, len=%d)\n",var_id,instance,pofs,len);
		  if (debug>2) dump("Request bytes",&packet[pofs],8);
		  /* Step through HLR to find any matching instances of the requested variable */
		  h=openhlrentry(hlr,ofs);
		  if (debug>1) fprintf(stderr,"openhlrentry(hlr,%d) returned %p\n",ofs,h);
		  while(h)
		    {
		      /* Is this the variable? */
		      if (debug>2) fprintf(stderr,"  considering var_id=%02x, instance=%02x\n",
					   h->var_id,h->var_instance);
		      if (h->var_id==var_id)
			{
			  if (h->var_instance==instance||instance==-1)
			    {
			      /* Limit transfer size to MAX_DATA_BYTES, plus an allowance for variable packing. */
			      unsigned char data[MAX_DATA_BYTES+16];
			      int dlen=0;
			      
			      if (debug>1) fprintf(stderr,"Sending matching variable value instance (instance #%d), value offset %d.\n",
						   h->var_instance,offset);
			      
			      if (packageVariableSegment(data,&dlen,h,offset,MAX_DATA_BYTES+16))
				return setReason("packageVariableSegment() failed.");
			      
			      respondSimple(hlrSid(hlr,ofs),ACTION_DATA,data,dlen,transaction_id);
			      if (instance==-1) sendDone++;
			    }
			  else
			    if (debug>2) fprintf(stderr,"Ignoring variable instance %d (not %d)\n",
						 h->var_instance,instance);
			}
		      else
			if (debug>2) fprintf(stderr,"Ignoring variable ID %d (not %d)\n",
					     h->var_id,var_id);
		      h=hlrentrygetent(h);
		    }
		  if (sendDone)
		    {
		      unsigned char data[1];
		      data[0]=sendDone&0xff;
		      respondSimple(hlrSid(hlr,ofs),ACTION_DONE,data,1,transaction_id);
		    }
		  
		  /* Advance to next record and keep searching */
		  if (nextHlr(hlr,&ofs)) break;
		}
	      
	      pofs+=7;
	      break;
	    default:
	      setReason("Asked to perform unsupported action");
	      if (debug) fprintf(stderr,"Packet offset = 0x%x\n",pofs);
	      if (debug) dump("Packet",packet,len);
	      return -1;
	    }	   
	}
    }
  
  if (debug>1) fprintf(stderr,"Searched %d HLR entries.\n",records_searched);

  return 0;
}

int respondSimple(char *sid,int action,unsigned char *action_text,int action_len,
		  unsigned char *transaction_id)
{
  unsigned char packet[8000];
  int pl=0;
  int *packet_len=&pl;
  int packet_maxlen=8000;
  int i;

  /* ACTION_ERROR is associated with an error message.
     For syntactic simplicity, we do not require the respondSimple() call to provide
     the length of the error message. */
  if (action==ACTION_ERROR) {
    action_len=strlen((char *)action_text);
    /* Make sure the error text isn't too long.
       IF it is, trim it, as we still need to communicate the error */
    if (action_len>255) action_len=255;
  }

  /* Prepare the request packet */
  if (packetMakeHeader(packet,8000,packet_len,transaction_id)) return -1;
  if (sid&&sid[0]) 
    { if (packetSetSid(packet,8000,packet_len,sid)) 
	return setReason("invalid SID in reply"); }
  else 
    { if (packetSetDid(packet,8000,packet_len,"")) 
	return setReason("Could not set empty DID in reply"); }  

  CHECK_PACKET_LEN(1+1+action_len);
  packet[(*packet_len)++]=action;
  if (action==ACTION_ERROR) packet[(*packet_len)++]=action_len;
  for(i=0;i<action_len;i++) packet[(*packet_len)++]=action_text[i];

  if (debug>2) dump("Simple response octets",action_text,action_len);

  if (packetFinalise(packet,8000,packet_len)) return -1;

  if (debug) fprintf(stderr,"Sending response of %d bytes.\n",*packet_len);

  if (packetSendRequest(REQ_REPLY,packet,*packet_len,NONBATCH,transaction_id,NULL)) return -1;
  
  return 0;
}
