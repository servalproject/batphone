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

FILE *i_f=NULL;

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
  bind_addr.sin_port = htons( PORT_DNA );
  bind_addr.sin_addr.s_addr = htonl( INADDR_ANY );
  if(bind(sock,(struct sockaddr *)&bind_addr,sizeof(bind_addr))) {
    fprintf(stderr,"MP HLR server could not bind to UDP port %d\n", PORT_DNA);
    perror("bind");
    exit(-3);
  }

  /* Detach from the console */
  if (!foregroundMode) daemon(0,0);

  while(1) {
    unsigned char buffer[16384];
    socklen_t recvaddrlen=sizeof(recvaddr);
    struct pollfd fds;
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
	  if (!did[0]) return respondSimple(NULL,ACTION_DECLINED,NULL,0,transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);
	  if (sid[0])  return respondSimple(sid,ACTION_DECLINED,NULL,0,transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);
	  if (debug>1) fprintf(stderr,"Verified that create request supplies DID but not SID\n");
	  
	  {
	    char sid[128];
	    /* make HLR with new random SID and initial DID */
	    if (!createHlr(did,sid))
	      return respondSimple(sid,ACTION_OKAY,NULL,0,transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);
	    else
	      return respondSimple(NULL,ACTION_DECLINED,NULL,0,transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);
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
	    case ACTION_STATS:
	      /* short16 variable id,
		 int32 value */
	      {
		pofs++;
		short field=packet[pofs+1]+(packet[pofs]<<8);
		int value=packet[pofs+5]+(packet[pofs+4]<<8)+(packet[pofs+3]<<16)+(packet[pofs+2]<<24);
		pofs+=6;
		if (instrumentation_file)
		  {
		    if (!i_f) { if (strcmp(instrumentation_file,"-")) i_f=fopen(instrumentation_file,"a"); else i_f=stdout; }
		    if (i_f) fprintf(i_f,"%ld:%08x:%d:%d\n",time(0),*(unsigned int *)&sender->sa_data[0],field,value);
		    if (i_f) fflush(i_f);
		  }
	      }
	      break;
	    case ACTION_DIGITALTELEGRAM:
	      // Unpack SMS message.
	      if (debug>1) fprintf(stderr,"In ACTION_DIGITALTELEGRAM\n");
	      {
		char emitterPhoneNumber[256];
		char message[256];
		pofs++;
		char messageType = packet[pofs];
		pofs++;
		char emitterPhoneNumberLen = packet[pofs];
		pofs++;
		char messageLen = packet[pofs];
		pofs++;
		strncpy(emitterPhoneNumber, (const char*)packet+pofs, emitterPhoneNumberLen);
		pofs+=emitterPhoneNumberLen;
		strncpy(message, (const char*)packet+pofs, messageLen); 
		pofs+=messageLen;
	      
		// Check if I'm the recipient
		ofs=0;
		if (findHlr(hlr, &ofs, sid, did)){
		  // Send SMS to android
		  char amCommand[576]; // 64 char + 2*256(max) char = 576
		  sprintf(amCommand, "am broadcast -a org.servalproject.DT -e number \"%s\"  -e content \"%s\"", emitterPhoneNumber, message);
		  if (debug>1) fprintf(stderr,"Delivering DT message via intent: %s\n",amCommand);
		  int exitcode = runCommand(amCommand);
		  respondSimple(sid,ACTION_OKAY,NULL,0,transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);
		}
	      }
	      break;
	    case ACTION_SET:
	      ofs=0;
	      if (debug>1) fprintf(stderr,"Looking for hlr entries with sid='%s' / did='%s'\n",sid,did);

	      if ((!sid)||(!sid[0])) {
		setReason("You can only set variables by SID");
		return respondSimple(NULL,ACTION_ERROR,(unsigned char *)"SET requires authentication by SID",0,transaction_id,
				     CRYPT_CIPHERED|CRYPT_SIGNED);
	      }

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
		    
		  pofs++; rofs=pofs;
		  if (extractRequest(packet,&pofs,len,
				     &itemId,&instance,value,
				     &start_offset,&bytes,&flags))
		    {
		      setReason("Could not extract ACTION_SET request");
		      return 
			respondSimple(NULL,ACTION_ERROR,(unsigned char *)"Mal-formed SET request",0,transaction_id,
				      CRYPT_CIPHERED|CRYPT_SIGNED);
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
					0,transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);
		    }
		  } else {
		    if (flags==SET_NOREPLACE) {
		      setReason("Tried to SET_NOREPLACE an existing value");
		      if (debug>1) dump("Existing value",oldvalue,oldl);
		      return 
			respondSimple(NULL,ACTION_ERROR,
				      (unsigned char *)"Cannot SET NOREPLACE; a value exists",
				      0,transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);
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
			respondSimple(NULL,ACTION_ERROR,(unsigned char *)"Failed to SET variable",0,transaction_id,
				      CRYPT_CIPHERED|CRYPT_SIGNED);
		    }
		  if (debug>2) { fprintf(stderr,"HLR after writing:\n"); hlrDump(hlr,ofs); }
		  
		  /* Reply that we wrote the fragment */
		  respondSimple(sid,ACTION_WROTE,&packet[rofs],6,
				transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);
		  /* Advance to next record and keep searching */
		  if (nextHlr(hlr,&ofs)) break;
		}
	      break;
	    case ACTION_GET:
	      {
		/* Limit transfer size to MAX_DATA_BYTES, plus an allowance for variable packing. */
		unsigned char data[MAX_DATA_BYTES+16];
		int dlen=0;
		int sendDone=0;
		int var_id=packet[pofs+1];
		int instance=packet[pofs+2];
		int offset=(packet[pofs+3]<<8)+packet[pofs+4];
		char *hlr_sid=NULL;

		pofs+=7;
		if (debug>2) dump("Request bytes",&packet[pofs],8);
		if (debug>1) fprintf(stderr,"Processing ACTION_GET (var_id=%02x, instance=%02x, pofs=0x%x, len=%d)\n",var_id,instance,pofs,len);

		ofs=0;
		if (debug>1) fprintf(stderr,"Looking for hlr entries with sid='%s' / did='%s'\n",sid?sid:"null",did?did:"null");

		while(1)
		  {
		    struct hlrentry_handle *h;

		    // if an empty did was passed in, get results from all hlr records
		    if (*sid || *did){
 		      if (!findHlr(hlr,&ofs,sid,did)) break;
		      if (debug>1) fprintf(stderr,"findHlr found a match @ 0x%x\n",ofs);
		    }
		    if (debug>2) hlrDump(hlr,ofs);
  		  
		    /* XXX consider taking action on this HLR
		       (check PIN first depending on the action requested) */

		    /* Form a reply packet containing the requested data */
  		  
		    if (instance==0xff) instance=-1;
  		  
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
				if (debug>1) fprintf(stderr,"Sending matching variable value instance (instance #%d), value offset %d.\n",
						     h->var_instance,offset);
  			      
				// only send each value when the *next* record is found, that way we can easily stamp the last response with DONE
				if (sendDone>0)
				  respondSimple(hlr_sid,ACTION_DATA,data,dlen,transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);

				dlen=0;
	    
				if (packageVariableSegment(data,&dlen,h,offset,MAX_DATA_BYTES+16))
				  return setReason("packageVariableSegment() failed.");
				hlr_sid=hlrSid(hlr,ofs);

				sendDone++;
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
  		  
		    /* Advance to next record and keep searching */
		    if (nextHlr(hlr,&ofs)) break;
		  }
		  if (sendDone)
		    {
		      data[dlen++]=ACTION_DONE;
		      data[dlen++]=sendDone&0xff;
		      respondSimple(hlr_sid,ACTION_DATA,data,dlen,transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);
		    }
		  if (gatewayuri&&(var_id==VAR_LOCATIONS)&&did&&strlen(did))
		    {
		      /* We are a gateway, so offer connection via the gateway as well */
		      unsigned char data[MAX_DATA_BYTES+16];
		      int dlen=0;
		      struct hlrentry_handle fake;
		      unsigned char uri[1024];

		      /* We use asterisk to provide the gateway service,
			 so we need to create a temporary extension in extensions.conf,
			 ask asterisk to re-read extensions.conf, and then make sure it has
			 a functional SIP gateway.
		      */
		      if (!asteriskObtainGateway(sid,did,(char *)uri))
			{
			  
			  fake.value_len=strlen((char *)uri);
			  fake.var_id=var_id;
			  fake.value=uri;
			  
			  if (packageVariableSegment(data,&dlen,&fake,offset,MAX_DATA_BYTES+16))
			    return setReason("packageVariableSegment() of gateway URI failed.");
			  
			  respondSimple(hlrSid(hlr,0),ACTION_DATA,data,dlen,transaction_id,CRYPT_CIPHERED|CRYPT_SIGNED);
			}
		      else
			{
			  /* Should we indicate the gateway is not available? */
			}
		    }
	      
	      }
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
		  unsigned char *transaction_id,int cryptoFlags)
{
  unsigned char packet[8000];
  int pl=0;
  int *packet_len=&pl;
  int packet_maxlen=8000;
  int i;

  /* XXX Complain about invalid crypto flags.
     XXX We don't do anything with the crypto flags right now
     XXX Other packet sending routines need this as well. */
  if (!cryptoFlags) return -1;

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
