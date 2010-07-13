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


int getBatmanPeerList(char *socket_path,in_addr_t peers[],int *peer_count,int peer_max)
{
  int sock;
  struct sockaddr_un socket_address;
  unsigned char buf[16384]; /* big enough for a gigabit jumbo frame or loopback godzilla-gram */
  int ofs=0;
  int bytes=0;
  struct pollfd fds;
  char cmd[30];
  int notDone=1;
  int res;
  int tries=0;

 askagain:

  /* Make socket */
  sock=socket(AF_LOCAL,SOCK_STREAM,0);
  memset(&socket_address,0,sizeof(struct sockaddr_un));
  socket_address.sun_family=AF_LOCAL;
  if (strlen(socket_path)>256) return setReason("BATMAN socket path too long");
  strcpy(socket_address.sun_path,socket_path);

  /* Connect the socket */
  if (connect(sock,(struct sockaddr*)&socket_address,sizeof(socket_address))<0)
    return setReason("connect() to BATMAN socket failed.");

  memset(&cmd[0],0,30);
  snprintf(cmd,30,"d:%c",1);  
  if (write(sock,cmd,30)!=30)
    { close(sock); return setReason("write() command failed to BATMAN socket."); }

  fds.fd=sock;
  fds.events=POLLIN;

 getmore:

  while(notDone)
    {      
      switch (poll(&fds,1,1500)) {
      case 1: /* Excellent - we have a response */ break;
      case 0: if (debug>1) fprintf(stderr,"BATMAN did not respond to peer enquiry.\n");
	close(sock);
	if (tries++<=3) goto askagain;
	return setReason("No response from BATMAN.");
      default: /* some sort of error, e.g., lost connection */
	close(sock);
	return setReason("poll() of BATMAN socket failed.");
	}
      
      res=read(sock,&buf[bytes],16383-bytes); close(sock);
      ofs=0;
      if (res<1) {
	if (bytes)
	  {
	    /* Got a partial response, then a dead line.
	       Should probably ask again unless we have tried too many times.
	    */
	    if (debug>2) fprintf(stderr,"Trying again after cold drop.\n");
	    close(sock);
	    bytes=0;
	    if (tries++<=3) goto askagain;
	    else return setReason("failed to read() from BATMAN socket (too many tries).");
	  }
	return setReason("failed to read() from BATMAN socket.");
      }
      if (!res) return 0;
      if (debug>1) fprintf(stderr,"BATMAN has responded with %d bytes.\n",res);
      
      if (debug>2) dump("BATMAN says",&buf[bytes],res);
      
      if (res<80 /*||buf[bytes]!='B' -- turns out we can't rely on this, either */
	  ||buf[bytes+res-1]!=0x0a||buf[bytes+res-4]!='E')
	{
	  /* Jolly BATMAN on Android sometimes sends us bung packets from time to
	     time.  Sometimes it is fragmenting, other times it is just plain
	     odd.
	     If this happens, we should just ask again.
	     We should also try to reassemble fragments.
	     
	     Sometimes we get cold-drops and resumes, too.  Yay.
	  */
	  if (buf[bytes+res-4]!='E') {
	    /* no end marker, so try adding record to the end. */
	    if (debug>2) fprintf(stderr,"Data has no end marker, accumulating.\n");
	    bytes+=res;
	    goto getmore;
	  }
	  close(sock);
	  goto askagain;
	}
      bytes+=res;
      
      while(ofs<bytes)
	{	  
	  if(debug>1) fprintf(stderr,"New line @ %d\n",ofs);
	  /* Check for IP address of peers */
	  if (isdigit(buf[ofs]))
	    {
	      int i;
	      for(i=0;ofs+i<bytes;i++)
		if (buf[i+ofs]==' ') { 
		  buf[i+ofs]=0;
		  if (*peer_count<peer_max) peers[(*peer_count)++]=inet_addr((char *)&buf[ofs]);
		  if (debug>1) fprintf(stderr,"Found BATMAN peer '%s'\n",&buf[ofs]);
		  buf[ofs+i]=' ';
		  break; 
		}	    
	    }
	  /* Check for end of transmission */
	  if (buf[ofs]=='E') { notDone=0; }
	  
	  /* Skip to next line */
	  while(buf[ofs]>=' '&&(ofs<bytes)) ofs++;
	  while(buf[ofs]<' '&&(ofs<bytes)) ofs++;
	}
      bytes=0;
    }
  return 0;
}
