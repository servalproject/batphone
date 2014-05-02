/* micro_proxy - really small HTTP proxy
**
** Copyright © 1999 by Jef Poskanzer <jef@mail.acme.com>.
** All rights reserved.
**
** Redistribution and use in source and binary forms, with or without
** modification, are permitted provided that the following conditions
** are met:
** 1. Redistributions of source code must retain the above copyright
**    notice, this list of conditions and the following disclaimer.
** 2. Redistributions in binary form must reproduce the above copyright
**    notice, this list of conditions and the following disclaimer in the
**    documentation and/or other materials provided with the distribution.
**
** THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
** ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
** IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
** ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
** FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
** DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
** OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
** HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
** LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
** OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
** SUCH DAMAGE.
*/


#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <time.h>
#include <sys/time.h>
#include <signal.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <fcntl.h>
#include <syslog.h>


#define SERVER_NAME "micro_proxy"
#define SERVER_URL "http://www.acme.com/software/micro_proxy/"
#define PROTOCOL "HTTP/1.0"
#define RFC1123FMT "%a, %d %b %Y %H:%M:%S GMT"
#define TIMEOUT 300


/* Forwards. */
static int open_client_socket( char* hostname, unsigned short port );
static void proxy_http( char* method, char* path, char* protocol, FILE* sockrfp, FILE* sockwfp );
static void proxy_ssl( char* method, char* host, char* protocol, FILE* sockrfp, FILE* sockwfp );
static void sigcatch( int sig );
static void trim( char* line );
static void send_error( int status, char* title, char* extra_header, char* text );
static void send_headers( int status, char* title, char* extra_header, char* mime_type, int length, time_t mod );


int
main( int argc, char** argv )
    {
    char line[10000], method[10000], url[10000], protocol[10000], host[10000], path[10000];
    unsigned short port;
    int iport;
    int sockfd;
    int ssl;
    FILE* sockrfp;
    FILE* sockwfp;

    /* Read the first line of the request. */
    if ( fgets( line, sizeof(line), stdin ) == (char*) 0 )
	send_error( 400, "Bad Request", (char*) 0, "No request found." );

    /* Parse it. */
    trim( line );
    if ( sscanf( line, "%[^ ] %[^ ] %[^ ]", method, url, protocol ) != 3 )
	send_error( 400, "Bad Request", (char*) 0, "Can't parse request." );

    if ( url == (char*) 0 )
	send_error( 400, "Bad Request", (char*) 0, "Null URL." );

    openlog( "micro_proxy", 0, LOG_DAEMON );
    syslog( LOG_INFO, "proxying %s", url );

    if ( strncasecmp( url, "http://", 7 ) == 0 )
	{
	(void) strncpy( url, "http", 4 );	/* make sure it's lower case */
	if ( sscanf( url, "http://%[^:/]:%d%s", host, &iport, path ) == 3 )
	    port = (unsigned short) iport;
	else if ( sscanf( url, "http://%[^/]%s", host, path ) == 2 )
	    port = 80;
	else if ( sscanf( url, "http://%[^:/]:%d", host, &iport ) == 2 )
	    {
	    port = (unsigned short) iport;
	    *path = '\0';
	    }
	else if ( sscanf( url, "http://%[^/]", host ) == 1 )
	    {
	    port = 80;
	    *path = '\0';
	    }
	else
	    send_error( 400, "Bad Request", (char*) 0, "Can't parse URL." );
	ssl = 0;
	}
    else if ( strcmp( method, "CONNECT" ) == 0 )
	{
	if ( sscanf( url, "%[^:]:%d", host, &iport ) == 2 )
	    port = (unsigned short) iport;
	else if ( sscanf( url, "%s", host ) == 1 )
	    port = 443;
	else
	    send_error( 400, "Bad Request", (char*) 0, "Can't parse URL." );
	ssl = 1;
	}
    else
	send_error( 400, "Bad Request", (char*) 0, "Unknown URL type." );

    /* Get ready to catch timeouts.. */
    (void) signal( SIGALRM, sigcatch );

    /* Open the client socket to the real web server. */
    (void) alarm( TIMEOUT );
    sockfd = open_client_socket( host, port );

    /* Open separate streams for read and write, r+ doesn't always work. */
    sockrfp = fdopen( sockfd, "r" );
    sockwfp = fdopen( sockfd, "w" );

    if ( ssl )
	proxy_ssl( method, host, protocol, sockrfp, sockwfp );
    else
	proxy_http( method, path, protocol, sockrfp, sockwfp );

    /* Done. */
    (void) close( sockfd );
    exit( 0 );
    }


#if defined(AF_INET6) && defined(IN6_IS_ADDR_V4MAPPED)
#define USE_IPV6
#endif

static int
open_client_socket( char* hostname, unsigned short port )
    {
#ifdef USE_IPV6
    struct addrinfo hints;
    char portstr[10];
    int gaierr;
    struct addrinfo* ai;
    struct addrinfo* ai2;
    struct addrinfo* aiv4;
    struct addrinfo* aiv6;
    struct sockaddr_in6 sa;
#else /* USE_IPV6 */
    struct hostent *he;
    struct sockaddr_in sa;
#endif /* USE_IPV6 */
    int sa_len, sock_family, sock_type, sock_protocol;
    int sockfd;

    (void) memset( (void*) &sa, 0, sizeof(sa) );

#ifdef USE_IPV6

    (void) memset( &hints, 0, sizeof(hints) );
    hints.ai_family = PF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    (void) snprintf( portstr, sizeof(portstr), "%d", (int) port );
    if ( (gaierr = getaddrinfo( hostname, portstr, &hints, &ai )) != 0 )
	send_error( 404, "Not Found", (char*) 0, "Unknown host." );

    /* Find the first IPv4 and IPv6 entries. */
    aiv4 = (struct addrinfo*) 0;
    aiv6 = (struct addrinfo*) 0;
    for ( ai2 = ai; ai2 != (struct addrinfo*) 0; ai2 = ai2->ai_next )
	{
	switch ( ai2->ai_family )
	    {
	    case AF_INET: 
	    if ( aiv4 == (struct addrinfo*) 0 )
		aiv4 = ai2;
	    break;
	    case AF_INET6:
	    if ( aiv6 == (struct addrinfo*) 0 )
		aiv6 = ai2;
	    break;
	    }
	}

    /* If there's an IPv4 address, use that, otherwise try IPv6. */
    if ( aiv4 != (struct addrinfo*) 0 )
	{
	if ( sizeof(sa) < aiv4->ai_addrlen )
	    {
	    (void) fprintf(
		stderr, "%s - sockaddr too small (%lu < %lu)\n",
		hostname, (unsigned long) sizeof(sa),
		(unsigned long) aiv4->ai_addrlen );
	    exit( 1 );
	    }
	sock_family = aiv4->ai_family;
	sock_type = aiv4->ai_socktype;
	sock_protocol = aiv4->ai_protocol;
	sa_len = aiv4->ai_addrlen;
	(void) memmove( &sa, aiv4->ai_addr, sa_len );
	goto ok;
	}
    if ( aiv6 != (struct addrinfo*) 0 )
	{
	if ( sizeof(sa) < aiv6->ai_addrlen )
	    {
	    (void) fprintf(
		stderr, "%s - sockaddr too small (%lu < %lu)\n",
		hostname, (unsigned long) sizeof(sa),
		(unsigned long) aiv6->ai_addrlen );
	    exit( 1 );
	    }
	sock_family = aiv6->ai_family;
	sock_type = aiv6->ai_socktype;
	sock_protocol = aiv6->ai_protocol;
	sa_len = aiv6->ai_addrlen;
	(void) memmove( &sa, aiv6->ai_addr, sa_len );
	goto ok;
	}

    send_error( 404, "Not Found", (char*) 0, "Unknown host." );

    ok:
    freeaddrinfo( ai );

#else /* USE_IPV6 */

    he = gethostbyname( hostname );
    if ( he == (struct hostent*) 0 )
	send_error( 404, "Not Found", (char*) 0, "Unknown host." );
    sock_family = sa.sin_family = he->h_addrtype;
    sock_type = SOCK_STREAM;
    sock_protocol = 0;
    sa_len = sizeof(sa);
    (void) memmove( &sa.sin_addr, he->h_addr, he->h_length );
    sa.sin_port = htons( port );

#endif /* USE_IPV6 */

    sockfd = socket( sock_family, sock_type, sock_protocol );
    if ( sockfd < 0 )
	send_error( 500, "Internal Error", (char*) 0, "Couldn't create socket." );

    if ( connect( sockfd, (struct sockaddr*) &sa, sa_len ) < 0 )
	send_error( 503, "Service Unavailable", (char*) 0, "Connection refused." );

    return sockfd;
    }


static void
proxy_http( char* method, char* path, char* protocol, FILE* sockrfp, FILE* sockwfp )
    {
    char line[10000], protocol2[10000], comment[10000];
    int first_line, status, ich;
    long content_length, i;

    /* Send request. */
    (void) alarm( TIMEOUT );
	(void) fprintf( sockwfp, "%s %s %s\r\n", method, path, protocol );
    /* Forward the remainder of the request from the client. */
    content_length = -1;
    while ( fgets( line, sizeof(line), stdin ) != (char*) 0 )
	{
	if ( strcmp( line, "\n" ) == 0 || strcmp( line, "\r\n" ) == 0 )
	    break;
	(void) fputs( line, sockwfp );
	(void) alarm( TIMEOUT );
	trim( line );
	if ( strncasecmp( line, "Content-Length:", 15 ) == 0 )
	    content_length = atol( &(line[15]) );
	}
    (void) fputs( line, sockwfp );
    (void) fflush( sockwfp );
    /* If there's content, forward that too. */
    if ( content_length != -1 )
	for ( i = 0; i < content_length && ( ich = getchar() ) != EOF; ++i )
	    putc( ich, sockwfp );
    (void) fflush( sockwfp );

    /* Forward the response back to the client. */
    (void) alarm( TIMEOUT );
    content_length = -1;
    first_line = 1;
    status = -1;
    while ( fgets( line, sizeof(line), sockrfp ) != (char*) 0 )
	{
	if ( strcmp( line, "\n" ) == 0 || strcmp( line, "\r\n" ) == 0 )
	    break;
	(void) fputs( line, stdout );
	(void) alarm( TIMEOUT );
	trim( line );
	if ( first_line )
	    {
	    (void) sscanf( line, "%[^ ] %d %s", protocol2, &status, comment );
	    first_line = 0;
	    }
	if ( strncasecmp( line, "Content-Length:", 15 ) == 0 )
	    content_length = atol( &(line[15]) );
	}
    /* Add a response header. */
    (void) fputs( "Connection: close\r\n", stdout );
    (void) fputs( line, stdout );
    (void) fflush( stdout );
    /* Under certain circumstances we don't look for the contents, even
    ** if there was a Content-Length.
    */
    if ( strcasecmp( method, "HEAD" ) != 0 && status != 304 )
	{
	/* Forward the content too, either counted or until EOF. */
	for ( i = 0;
	      ( content_length == -1 || i < content_length ) && ( ich = getc( sockrfp ) ) != EOF;
	      ++i )
	    {
	    putchar( ich );
	    if ( i % 10000 == 0 )
		(void) alarm( TIMEOUT );
	    }
	}
    (void) fflush( stdout );
    }


static void
proxy_ssl( char* method, char* host, char* protocol, FILE* sockrfp, FILE* sockwfp )
    {
    int client_read_fd, server_read_fd, client_write_fd, server_write_fd;
    struct timeval timeout;
    fd_set fdset;
    int maxp1, r;
    char buf[10000];

    /* Return SSL-proxy greeting header. */
    (void) fputs( "HTTP/1.0 200 Connection established\r\n\r\n", stdout );
    (void) fflush( stdout );
    /* Now forward SSL packets in both directions until done. */
    client_read_fd = fileno( stdin );
    server_read_fd = fileno( sockrfp );
    client_write_fd = fileno( stdout );
    server_write_fd = fileno( sockwfp );
    timeout.tv_sec = TIMEOUT;
    timeout.tv_usec = 0;
    if ( client_read_fd >= server_read_fd )
	maxp1 = client_read_fd + 1;
    else
	maxp1 = server_read_fd + 1;
    (void) alarm( 0 );
    for (;;)
	{
	FD_ZERO( &fdset );
	FD_SET( client_read_fd, &fdset );
	FD_SET( server_read_fd, &fdset );
	r = select( maxp1, &fdset, (fd_set*) 0, (fd_set*) 0, &timeout );
	if ( r == 0 )
	    send_error( 408, "Request Timeout", (char*) 0, "Request timed out." );
	else if ( FD_ISSET( client_read_fd, &fdset ) )
	    {
	    r = read( client_read_fd, buf, sizeof( buf ) );
	    if ( r <= 0 )
		break;
	    r = write( server_write_fd, buf, r );
	    if ( r <= 0 )
		break;
	    }
	else if ( FD_ISSET( server_read_fd, &fdset ) )
	    {
	    r = read( server_read_fd, buf, sizeof( buf ) );
	    if ( r <= 0 )
		break;
	    r = write( client_write_fd, buf, r );
	    if ( r <= 0 )
		break;
	    }
	}
    }


static void
sigcatch( int sig )
    {
    send_error( 408, "Request Timeout", (char*) 0, "Request timed out." );
    }


static void
trim( char* line )
    {
    int l;

    l = strlen( line );
    while ( line[l-1] == '\n' || line[l-1] == '\r' )
	line[--l] = '\0';
    }


static void
send_error( int status, char* title, char* extra_header, char* text )
    {
    send_headers( status, title, extra_header, "text/html", -1, -1 );
    (void) printf( "\
<HTML>\n\
<HEAD><TITLE>%d %s</TITLE></HEAD>\n\
<BODY BGCOLOR=\"#cc9999\" TEXT=\"#000000\" LINK=\"#2020ff\" VLINK=\"#4040cc\">\n\
<H4>%d %s</H4>\n",
	status, title, status, title );
    (void) printf( "%s\n", text );
    (void) printf( "\
<HR>\n\
<ADDRESS><A HREF=\"%s\">%s</A></ADDRESS>\n\
</BODY>\n\
</HTML>\n",
	SERVER_URL, SERVER_NAME );
    (void) fflush( stdout );
    exit( 1 );
    }


static void
send_headers( int status, char* title, char* extra_header, char* mime_type, int length, time_t mod )
    {
    time_t now;
    char timebuf[100];

    (void) printf( "%s %d %s\r\n", PROTOCOL, status, title );
    (void) printf( "Server: %s\r\n", SERVER_NAME );
    now = time( (time_t*) 0 );
    (void) strftime( timebuf, sizeof(timebuf), RFC1123FMT, gmtime( &now ) );
    (void) printf( "Date: %s\r\n", timebuf );
    if ( extra_header != (char*) 0 )
	(void) printf( "%s\r\n", extra_header );
    if ( mime_type != (char*) 0 )
	(void) printf( "Content-Type: %s\r\n", mime_type );
    if ( length >= 0 )
	(void) printf( "Content-Length: %d\r\n", length );
    if ( mod != (time_t) -1 )
	{
	(void) strftime( timebuf, sizeof(timebuf), RFC1123FMT, gmtime( &mod ) );
	(void) printf( "Last-Modified: %s\r\n", timebuf );
	}
    (void) printf( "Connection: close\r\n" );
    (void) printf( "\r\n" );
    }
