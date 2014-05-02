#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <errno.h>
#include <signal.h>

#include <stdio.h>
#include <stdlib.h>

volatile sig_atomic_t interrupted;
volatile sig_atomic_t child_done;

void sighandler(int sig)
{
	switch(sig)
	{
	case SIGINT:
write(2,"Caught SIGINT\n",14);
		interrupted=1;
		break;
	case SIGCHLD:
write(2,"Caught SIGCHLD\n",15);
		child_done=1;
		break;
	}
	signal(sig,sighandler);
}

const char *myname;

int main(int argc,char **argv)
{
	int lis;	/*Listener socket*/
	unsigned long temp_ul;
	unsigned short port;
	char *endptr;
	int ret;
	struct sockaddr_in addr={0};

	myname=argv[0];
	argc--; argv++;

	if(argc < 2)
	{
		fprintf(stderr,"Usage: %s port cmd args...\n",myname);
		exit(0);
	}

	temp_ul=strtoul(argv[0],&endptr,10);
	if(temp_ul==0 || temp_ul > (unsigned short)-1 || *endptr!='\0')
	{
		fprintf(stderr,"Usage: %s port cmd args...\n",myname);
		exit(EXIT_FAILURE);
	}
	port=temp_ul;
	argc--; argv++;

	/*Set up socket*/
	lis=socket(PF_INET,SOCK_STREAM,IPPROTO_TCP);
	if(lis==-1)
	{
		perror("socket");
		exit(EXIT_FAILURE);
	}
	addr.sin_family=AF_INET;
	addr.sin_port=htons(port);
	addr.sin_addr.s_addr=INADDR_ANY;
	if(bind(lis,(struct sockaddr *)&addr,sizeof addr) == -1)
	{
		perror("bind");
		close(lis);
		exit(EXIT_FAILURE);
	}
	if(listen(lis,128) == -1)
	{
		perror("listen");
		close(lis);
		exit(EXIT_FAILURE);
	}
	fprintf(stderr,"Listening on port %hu\n",port);

	signal(SIGINT,sighandler);
	signal(SIGCHLD,sighandler);

	while(1)
	{
		fd_set rd;

		/*MacOS 10.4 has a buggy accept that stays blocked
		    even if a signal comes in.
		  select works properly, so block in select and then
		    accept if there's a connection ready.
		*/
		FD_ZERO(&rd);
		FD_SET(lis,&rd);
		if(select(lis+1,&rd,NULL,NULL,NULL) > 0)
		{
			/*connection ready*/
			struct sockaddr_in remote;
			socklen_t len=sizeof remote;
			int sock=accept(lis,(struct sockaddr *)&remote,&len);
			if(sock >= 0)
			{
				fprintf(stderr,"Got connection from %s\n",inet_ntoa(remote.sin_addr));
				ret=fork();
				if(ret < 0)
				{
					/*fork() failed*/
					perror("fork");
					exit(EXIT_FAILURE);
				}
				else if(ret==0)
				{
					/*child*/

					if(setsid() == -1)
					{
						fprintf(stderr,"setsid() failed!  Unable to detach from terminal\n");
					}
					if(dup2(sock,0)==-1 || dup2(sock,1)==-1)
					{
						perror("dup2");
						exit(EXIT_FAILURE);
					}
					execvp(argv[0],argv);
					/*If we get here, exec failed*/
					perror("exec");
					exit(EXIT_FAILURE);
					/*If we get here, the world is about to end*/
				}
				else
				{
					/*parent*/
					close(sock);
				}
			}/*if accept succeeded*/
			else if(errno!=EINTR)
			{
				perror("accept");
				exit(EXIT_FAILURE);
			}
		}/*if select returned >0*/
		else if(errno!=EINTR)
		{
			perror("select");
			exit(EXIT_FAILURE);
		}

		/*Check signal flags even if we didn't get EINTR - we
		    may have caught a signal while select wasn't running
		*/
		if(interrupted)
		{
			fprintf(stderr,"Interrupted, cleaning up\n");
			break;	/*out of loop*/
		}
		if(child_done)
		{
			/*Clean up children*/
			child_done=0;
			while(waitpid(-1,&ret,WNOHANG) > 0)
			{
				fprintf(stderr,"Child process finished\n");
			}
		}
	}

	/*We only get here on normal exit - if a syscall fails, we bail out
	    immediately.
	*/
	close(lis);
	fprintf(stderr,"Waiting for children (interrupt again to stop immediately)\n");
	while(wait(&ret) > 0)	/*EINTR or ECHILD means stop*/
		;
	exit(0);
}
