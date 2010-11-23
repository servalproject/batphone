#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/system_properties.h>

#include <stdio.h>
#include <errno.h>
#include <stdlib.h>
#include <unistd.h>
#ifdef HAVE_STRINGS_H
#include <strings.h>
#endif
#include <string.h>
#ifdef HAVE_ARPA_INET_H
#include <arpa/inet.h>
#else
typedef unsigned int in_addr_t;
struct in_addr {
	in_addr_t s_addr;
};
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
#include <poll.h>
#ifdef HAVE_NETDB_H
#include <netdb.h>
#endif
#ifdef HAVE_CTYPE_H
#include <ctype.h>
#endif
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <net/if.h>
#include <fcntl.h>
#include <getopt.h>
#include <ctype.h>


#include "android_tether_system_BatmanPeerCount.h"

JNIEXPORT jlong JNICALL Java_android_tether_system_BatmanPeerCount_BatmanPeerCount

(JNIEnv* env, jobject thiz, jstring filename)

{
	const char *filenameString;
	filenameString= (*env)->GetStringUTFChars(env,filename,0);
	
	int peerCount=0;
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
	if (strlen(filenameString)>256) return 0;
	strcpy(socket_address.sun_path,filenameString);
	
	/* Connect the socket */
	if (connect(sock,(struct sockaddr*)&socket_address,sizeof(socket_address))<0)
		return 0;
	
	memset(&cmd[0],0,30);
	snprintf(cmd,30,"d:%c",1);  
	if (write(sock,cmd,30)!=30)
    { close(sock); return 0; }
	
	fds.fd=sock;
	fds.events=POLLIN;
	
getmore:
	
	while(notDone)
    {      
		switch (poll(&fds,1,1500)) {
			case 1: /* Excellent - we have a response */ break;
			case 0: 
				close(sock);
				if (tries++<=3) goto askagain;
				return peerCount;
			default: /* some sort of error, e.g., lost connection */
				close(sock);
				return peerCount;
		}
		
		res=read(sock,&buf[bytes],16383-bytes); close(sock);
		ofs=0;
		if (res<1) {
			if (bytes)
			{
				/* Got a partial response, then a dead line.
				 Should probably ask again unless we have tried too many times.
				 */
				close(sock);
				bytes=0;
				if (tries++<=3) goto askagain;
				else return peerCount;
			}
			return peerCount;
		}
		if (!res) return peerCount;
		
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
				bytes+=res;
				goto getmore;
			}
			close(sock);
			goto askagain;
		}
		bytes+=res;
		
		while(ofs<bytes)
		{	  
			/* Check for IP address of peers */
			if (isdigit(buf[ofs]))
			{
				int i;
				for(i=0;ofs+i<bytes;i++)
					if (buf[i+ofs]==' ') { 
						peerCount++;
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
	return peerCount;
}