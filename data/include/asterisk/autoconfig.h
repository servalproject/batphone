/* include/asterisk/autoconfig.h.  Generated from autoconfig.h.in by configure.  */
/* include/asterisk/autoconfig.h.in.  Generated from configure.ac by autoheader.  */

#ifndef ASTERISK_AUTOCONFIG_H
#define ASTERISK_AUTOCONFIG_H

#include "asterisk/buildopts.h"



/* Define to 1 if internal poll should be used. */
/* #undef AST_POLL_COMPAT */

/* Define to 1 if the `closedir' function returns void instead of `int'. */
/* #undef CLOSEDIR_VOID */

/* Define to one of `_getb67', `GETB67', `getb67' for Cray-2 and Cray-YMP
   systems. This function is required for `alloca.c' support on those systems.
   */
/* #undef CRAY_STACKSEG_END */

/* Define to 1 if using `alloca.c'. */
/* #undef C_ALLOCA */

/* Define to 1 if you have `alloca', as a function or macro. */
#define HAVE_ALLOCA 1

/* Define to 1 if you have <alloca.h> and it should be used (not on Ultrix).
   */
#define HAVE_ALLOCA_H 1

/* Define to 1 if you have the Advanced Linux Sound Architecture library. */
/* #undef HAVE_ALSA */

/* Define to 1 if you have the <arpa/inet.h> header file. */
#define HAVE_ARPA_INET_H 1

/* Define to 1 if you have the <arpa/nameser.h> header file. */
#define HAVE_ARPA_NAMESER_H 1

/* Define to 1 if you have the `asprintf' function. */
#define HAVE_ASPRINTF 1

/* Define to 1 if you have the `atexit' function. */
#define HAVE_ATEXIT 1

/* Define to 1 if your GCC C compiler supports the 'always_inline' attribute.
   */
/* #undef HAVE_ATTRIBUTE_always_inline */

/* Define to 1 if your GCC C compiler supports the 'const' attribute. */
/* #undef HAVE_ATTRIBUTE_const */

/* Define to 1 if your GCC C compiler supports the 'deprecated' attribute. */
/* #undef HAVE_ATTRIBUTE_deprecated */

/* Define to 1 if your GCC C compiler supports the 'malloc' attribute. */
/* #undef HAVE_ATTRIBUTE_malloc */

/* Define to 1 if your GCC C compiler supports the 'pure' attribute. */
/* #undef HAVE_ATTRIBUTE_pure */

/* Define to 1 if your GCC C compiler supports the 'unused' attribute. */
/* #undef HAVE_ATTRIBUTE_unused */

/* Define to 1 if you have the `bzero' function. */
#define HAVE_BZERO 1

/* Define to 1 if you have the POSIX 1.e capabilities library. */
/* #undef HAVE_CAP */

/* Define to 1 if your system has a working `chown' function. */
#define HAVE_CHOWN 1

/* Define to 1 if you have a functional curl library. */
/* #undef HAVE_CURL */

/* Define to 1 if you have the curses library. */
#define HAVE_CURSES 1

/* Define if your system has the DAHDI headers. */
/* #undef HAVE_DAHDI */

/* Define DAHDI headers version */
/* #undef HAVE_DAHDI_VERSION */

/* Define to 1 if you have the <dirent.h> header file, and it defines `DIR'.
   */
#define HAVE_DIRENT_H 1

/* Define to 1 if you don't have `vprintf' but do have `_doprnt.' */
/* #undef HAVE_DOPRNT */

/* Define to 1 if you have the `dup2' function. */
#define HAVE_DUP2 1

/* Define to 1 if you have the `endpwent' function. */
#define HAVE_ENDPWENT 1

/* Define to 1 if you have the <fcntl.h> header file. */
#define HAVE_FCNTL_H 1

/* Define to 1 if you have the `floor' function. */
/* #undef HAVE_FLOOR */

/* Define to 1 if you have the `fork' function. */
#define HAVE_FORK 1

/* Define to 1 if you have the FreeTDS library. */
/* #undef HAVE_FREETDS */

/* Define to 1 if fseeko (and presumably ftello) exists and is declared. */
#define HAVE_FSEEKO 1

/* Define to 1 if you have the `ftruncate' function. */
#define HAVE_FTRUNCATE 1

/* Define to 1 if your GCC C compiler provides atomic operations. */
/* #undef HAVE_GCC_ATOMICS */

/* Define to 1 if you have the `getcwd' function. */
#define HAVE_GETCWD 1

/* Define to 1 if you have the `gethostbyname' function. */
#define HAVE_GETHOSTBYNAME 1

/* Define to 1 if your system has gethostbyname_r with 5 arguments. */
/* #undef HAVE_GETHOSTBYNAME_R_5 */

/* Define to 1 if your system has gethostbyname_r with 6 arguments. */
#define HAVE_GETHOSTBYNAME_R_6 1

/* Define to 1 if you have the `gethostname' function. */
#define HAVE_GETHOSTNAME 1

/* Define if your system has the GETIFADDRS headers. */
#define HAVE_GETIFADDRS 1

/* Define GETIFADDRS headers version */
#define HAVE_GETIFADDRS_VERSION /**/

/* Define to 1 if you have the `getloadavg' function. */
#define HAVE_GETLOADAVG 1

/* Define to 1 if you have the `getpagesize' function. */
#define HAVE_GETPAGESIZE 1

/* Define to 1 if you have the `gettimeofday' function. */
#define HAVE_GETTIMEOFDAY 1

/* Define to 1 if your glob function supports GLOB_BRACE option. */
#define HAVE_GLOB_BRACE 1

/* Define to 1 if your glob function supports GLOB_NOMAGIC option. */
#define HAVE_GLOB_NOMAGIC 1

/* Define to 1 if you have the GNU TLS support (used for iksemel only)
   library. */
/* #undef HAVE_GNUTLS */

/* Define to indicate the GSM library */
/* #undef HAVE_GSM */

/* Define to indicate that gsm.h is in gsm/gsm.h */
/* #undef HAVE_GSM_GSM_HEADER */

/* Define to indicate that gsm.h has no prefix for its location */
#define HAVE_GSM_HEADER 1

/* Define if your system has the GTK libraries. */
/* #undef HAVE_GTK */

/* Define if your system has the GTK2 libraries. */
#define HAVE_GTK2 1

/* Define to 1 if you have the Iksemel Jabber Library library. */
/* #undef HAVE_IKSEMEL */

/* Define if your system has the UW IMAP Toolkit c-client library. */
/* #undef HAVE_IMAP_TK */

/* Define if your system has the UW IMAP Toolkit c-client library version 2006
   or greater. */
/* #undef HAVE_IMAP_TK2006 */

/* Define to 1 if you have the `inet_ntoa' function. */
#define HAVE_INET_NTOA 1

/* Define to 1 if you have the <inttypes.h> header file. */
#define HAVE_INTTYPES_H 1

/* Define to 1 if you have the `ioperm' function. */
#define HAVE_IOPERM 1

/* Define to 1 if your system has PMTU discovery on UDP sockets. */
#define HAVE_IP_MTU_DISCOVER 1

/* Define to 1 if you have the `isascii' function. */
#define HAVE_ISASCII 1

/* Define to 1 if you have the ISDN4Linux Library library. */
/* #undef HAVE_ISDNNET */

/* Define to 1 if you have the KDE library. */
/* #undef HAVE_KDE */

/* Define to 1 if you have the <libintl.h> header file. */
#define HAVE_LIBINTL_H 1

/* Define if your system has the KDE libraries. */
/* #undef HAVE_LIBKDE */

/* Define to 1 if you have the <limits.h> header file. */
#define HAVE_LIMITS_H 1

/* Define to 1 if your system has linux/compiler.h. */
/* #undef HAVE_LINUX_COMPILER_H */

/* Define to 1 if you have the <locale.h> header file. */
#define HAVE_LOCALE_H 1

/* Define to 1 if you have the `localtime_r' function. */
#define HAVE_LOCALTIME_R 1

/* Define to 1 if you have the libtool library. */
/* #undef HAVE_LTDL */

/* Define to 1 if you have the <malloc.h> header file. */
#define HAVE_MALLOC_H 1

/* Define to 1 if you have the `memchr' function. */
#define HAVE_MEMCHR 1

/* Define to 1 if you have the `memmove' function. */
#define HAVE_MEMMOVE 1

/* Define to 1 if you have the <memory.h> header file. */
#define HAVE_MEMORY_H 1

/* Define to 1 if you have the `memset' function. */
#define HAVE_MEMSET 1

/* Define to 1 if you have the mISDN User Library library. */
/* #undef HAVE_MISDN */

/* Define to 1 if you have the `mkdir' function. */
#define HAVE_MKDIR 1

/* Define to 1 if you have a working `mmap' system call. */
#define HAVE_MMAP 1

/* Define to 1 if you have the `munmap' function. */
#define HAVE_MUNMAP 1

/* Define to 1 if you have the Network Broadcast Sound library. */
/* #undef HAVE_NBS */

/* Define to 1 if you have the ncurses library. */
#define HAVE_NCURSES 1

/* Define to 1 if you have the <ndir.h> header file, and it defines `DIR'. */
/* #undef HAVE_NDIR_H */

/* Define to 1 if you have the <netdb.h> header file. */
#define HAVE_NETDB_H 1

/* Define to 1 if you have the <netinet/in.h> header file. */
#define HAVE_NETINET_IN_H 1

/* Define to indicate the Net-SNMP library */
/* #undef HAVE_NETSNMP */

/* Define to 1 if you have the newt library. */
/* #undef HAVE_NEWT */

/* Define to 1 if you have the OGG library. */
/* #undef HAVE_OGG */

/* Define if your system has the OpenH323 libraries. */
/* #undef HAVE_OPENH323 */

/* Define to 1 if you have the OpenSSL library. */
/* #undef HAVE_OPENSSL */

/* Define to 1 if you have the OSP Toolkit library. */
/* #undef HAVE_OSPTK */

/* Define to indicate the Open Sound System library */
#define HAVE_OSS 1

/* Define to 1 if OSX atomic operations are supported. */
/* #undef HAVE_OSX_ATOMICS */

/* Define to indicate the PostgreSQL library */
/* #undef HAVE_PGSQL */

/* Define to 1 if you have the popt library. */
/* #undef HAVE_POPT */

/* Define to 1 if you have the `pow' function. */
/* #undef HAVE_POW */

/* Define to 1 if you have the ISDN PRI library. */
/* #undef HAVE_PRI */

/* Define to 1 if you have the ISDN PRI set_inbanddisconnect library. */
/* #undef HAVE_PRI_INBANDDISCONNECT */

/* Define to 1 if you have the ISDN PRI get_version library. */
/* #undef HAVE_PRI_VERSION */

/* Define if you have POSIX threads libraries and header files. */
#define HAVE_PTHREAD 1

/* Define to 1 if your system has PTHREAD_RWLOCK_INITIALIZER. */
#define HAVE_PTHREAD_RWLOCK_INITIALIZER 1

/* Define to 1 if your system has PTHREAD_RWLOCK_PREFER_WRITER_NP. */
#define HAVE_PTHREAD_RWLOCK_PREFER_WRITER_NP 1

/* Define if your system has pthread_rwlock_timedwrlock() */
/* #undef HAVE_PTHREAD_RWLOCK_TIMEDWRLOCK */

/* Define to 1 if the system has the type `ptrdiff_t'. */
#define HAVE_PTRDIFF_T 1

/* Define to 1 if you have the `putenv' function. */
#define HAVE_PUTENV 1

/* Define if your system has the PWLib libraries. */
/* #undef HAVE_PWLIB */

/* Define to 1 if you have the Radius Client library. */
/* #undef HAVE_RADIUS */

/* Define to 1 if you have the `regcomp' function. */
#define HAVE_REGCOMP 1

/* Define to 1 if your system has the ndestroy resolver function. */
/* #undef HAVE_RES_NDESTROY */

/* Define to 1 if your system has the re-entrant resolver functions. */
#define HAVE_RES_NINIT 1

/* Define to 1 if you have the `re_comp' function. */
#define HAVE_RE_COMP 1

/* Define to 1 if you have the `rint' function. */
/* #undef HAVE_RINT */

/* Define to 1 if your system has /sbin/launchd. */
/* #undef HAVE_SBIN_LAUNCHD */

/* Define to 1 if you have the `select' function. */
#define HAVE_SELECT 1

/* Define to 1 if you have the `setenv' function. */
#define HAVE_SETENV 1

/* Define to 1 if you have the `socket' function. */
#define HAVE_SOCKET 1

/* Define to 1 if your system has soxmix application. */
/* #undef HAVE_SOXMIX */

/* Define to 1 if you have the Speex library. */
/* #undef HAVE_SPEEX */

/* Define to 1 if you have the Speexdsp library. */
/* #undef HAVE_SPEEXDSP */

/* Define to 1 if you have the speex_preprocess_ctl library. */
/* #undef HAVE_SPEEX_PREPROCESS */

/* Define to 1 if you have the SQLite library. */
/* #undef HAVE_SQLITE */

/* Define to 1 if you have the `sqrt' function. */
/* #undef HAVE_SQRT */

/* Define to 1 if `stat' has the bug that it succeeds when given the
   zero-length file name argument. */
/* #undef HAVE_STAT_EMPTY_STRING_BUG */

/* Define to 1 if stdbool.h conforms to C99. */
#define HAVE_STDBOOL_H 1

/* Define to 1 if you have the <stddef.h> header file. */
#define HAVE_STDDEF_H 1

/* Define to 1 if you have the <stdint.h> header file. */
#define HAVE_STDINT_H 1

/* Define to 1 if you have the <stdlib.h> header file. */
#define HAVE_STDLIB_H 1

/* Define to 1 if you have the `strcasecmp' function. */
#define HAVE_STRCASECMP 1

/* Define to 1 if you have the `strcasestr' function. */
#define HAVE_STRCASESTR 1

/* Define to 1 if you have the `strchr' function. */
#define HAVE_STRCHR 1

/* Define to 1 if you have the `strcoll' function and it is properly defined.
   */
#define HAVE_STRCOLL 1

/* Define to 1 if you have the `strcspn' function. */
#define HAVE_STRCSPN 1

/* Define to 1 if you have the `strdup' function. */
#define HAVE_STRDUP 1

/* Define to 1 if you have the `strerror' function. */
#define HAVE_STRERROR 1

/* Define to 1 if you have the `strftime' function. */
#define HAVE_STRFTIME 1

/* Define to 1 if you have the <strings.h> header file. */
#define HAVE_STRINGS_H 1

/* Define to 1 if you have the <string.h> header file. */
#define HAVE_STRING_H 1

/* Define to 1 if you have the `strlcat' function. */
/* #undef HAVE_STRLCAT */

/* Define to 1 if you have the `strlcpy' function. */
/* #undef HAVE_STRLCPY */

/* Define to 1 if you have the `strncasecmp' function. */
#define HAVE_STRNCASECMP 1

/* Define to 1 if you have the `strndup' function. */
#define HAVE_STRNDUP 1

/* Define to 1 if you have the `strnlen' function. */
#define HAVE_STRNLEN 1

/* Define to 1 if you have the `strrchr' function. */
#define HAVE_STRRCHR 1

/* Define to 1 if you have the `strsep' function. */
#define HAVE_STRSEP 1

/* Define to 1 if you have the `strspn' function. */
#define HAVE_STRSPN 1

/* Define to 1 if you have the `strstr' function. */
#define HAVE_STRSTR 1

/* Define to 1 if you have the `strtol' function. */
#define HAVE_STRTOL 1

/* Define to 1 if you have the `strtoq' function. */
#define HAVE_STRTOQ 1

/* Define to 1 if `st_blksize' is member of `struct stat'. */
#define HAVE_STRUCT_STAT_ST_BLKSIZE 1

/* Define to 1 if you have the mISDN Supplemental Services library. */
/* #undef HAVE_SUPPSERV */

/* Define to 1 if you have the <syslog.h> header file. */
#define HAVE_SYSLOG_H 1

/* Define to 1 if you have the <sys/dir.h> header file, and it defines `DIR'.
   */
/* #undef HAVE_SYS_DIR_H */

/* Define to 1 if you have the <sys/file.h> header file. */
#define HAVE_SYS_FILE_H 1

/* Define to 1 if you have the <sys/ioctl.h> header file. */
#define HAVE_SYS_IOCTL_H 1

/* Define to 1 if you have the <sys/io.h> header file. */
#define HAVE_SYS_IO_H 1

/* Define to 1 if you have the <sys/ndir.h> header file, and it defines `DIR'.
   */
/* #undef HAVE_SYS_NDIR_H */

/* Define to 1 if you have the <sys/param.h> header file. */
#define HAVE_SYS_PARAM_H 1

/* Define to 1 if you have the <sys/select.h> header file. */
#define HAVE_SYS_SELECT_H 1

/* Define to 1 if you have the <sys/socket.h> header file. */
#define HAVE_SYS_SOCKET_H 1

/* Define to 1 if you have the <sys/stat.h> header file. */
#define HAVE_SYS_STAT_H 1

/* Define to 1 if you have the <sys/time.h> header file. */
#define HAVE_SYS_TIME_H 1

/* Define to 1 if you have the <sys/types.h> header file. */
#define HAVE_SYS_TYPES_H 1

/* Define to 1 if you have <sys/wait.h> that is POSIX.1 compatible. */
#define HAVE_SYS_WAIT_H 1

/* Define to 1 if you have the Termcap library. */
#define HAVE_TERMCAP 1

/* Define to 1 if you have the <termios.h> header file. */
#define HAVE_TERMIOS_H 1

/* Define to 1 if your system has timersub in time.h */
#define HAVE_TIMERSUB 1

/* Define to 1 if you have the Term Info library. */
/* #undef HAVE_TINFO */

/* Define to 1 if you have the tonezone library. */
/* #undef HAVE_TONEZONE */

/* Define to 1 if you have the <unistd.h> header file. */
#define HAVE_UNISTD_H 1

/* Define to 1 if you have the unixODBC library. */
/* #undef HAVE_UNIXODBC */

/* Define to 1 if you have the `unsetenv' function. */
#define HAVE_UNSETENV 1

/* Define to 1 if you have the usb library. */
/* #undef HAVE_USB */

/* Define to 1 if you have the `utime' function. */
#define HAVE_UTIME 1

/* Define to 1 if you have the <utime.h> header file. */
#define HAVE_UTIME_H 1

/* Define to 1 if `utime(file, NULL)' sets file's timestamp to the present. */
#define HAVE_UTIME_NULL 1

/* Define to 1 if you have the `vasprintf' function. */
#define HAVE_VASPRINTF 1

/* Define to 1 if you have the `vfork' function. */
#define HAVE_VFORK 1

/* Define to 1 if you have the <vfork.h> header file. */
/* #undef HAVE_VFORK_H */

/* Define to 1 if you have the Vorbis library. */
/* #undef HAVE_VORBIS */

/* Define if your system has the VoiceTronix API libraries. */
/* #undef HAVE_VPB */

/* Define to 1 if you have the `vprintf' function. */
#define HAVE_VPRINTF 1

/* Define to 1 if `fork' works. */
#define HAVE_WORKING_FORK 1

/* Define to 1 if `vfork' works. */
#define HAVE_WORKING_VFORK 1

/* Define if your system has the Zaptel headers. */
/* #undef HAVE_ZAPTEL */

/* Define if your Zaptel drivers have chan_alarms. */
/* #undef HAVE_ZAPTEL_CHANALARMS */

/* Define to 1 if you have the zlib library. */
/* #undef HAVE_ZLIB */

/* Define to 1 if the system has the type `_Bool'. */
#define HAVE__BOOL 1

/* Defined if libcurl supports AsynchDNS */
/* #undef LIBCURL_FEATURE_ASYNCHDNS */

/* Defined if libcurl supports IDN */
/* #undef LIBCURL_FEATURE_IDN */

/* Defined if libcurl supports IPv6 */
/* #undef LIBCURL_FEATURE_IPV6 */

/* Defined if libcurl supports KRB4 */
/* #undef LIBCURL_FEATURE_KRB4 */

/* Defined if libcurl supports libz */
/* #undef LIBCURL_FEATURE_LIBZ */

/* Defined if libcurl supports NTLM */
/* #undef LIBCURL_FEATURE_NTLM */

/* Defined if libcurl supports SSL */
/* #undef LIBCURL_FEATURE_SSL */

/* Defined if libcurl supports SSPI */
/* #undef LIBCURL_FEATURE_SSPI */

/* Defined if libcurl supports DICT */
/* #undef LIBCURL_PROTOCOL_DICT */

/* Defined if libcurl supports FILE */
/* #undef LIBCURL_PROTOCOL_FILE */

/* Defined if libcurl supports FTP */
/* #undef LIBCURL_PROTOCOL_FTP */

/* Defined if libcurl supports FTPS */
/* #undef LIBCURL_PROTOCOL_FTPS */

/* Defined if libcurl supports HTTP */
/* #undef LIBCURL_PROTOCOL_HTTP */

/* Defined if libcurl supports HTTPS */
/* #undef LIBCURL_PROTOCOL_HTTPS */

/* Defined if libcurl supports LDAP */
/* #undef LIBCURL_PROTOCOL_LDAP */

/* Defined if libcurl supports TELNET */
/* #undef LIBCURL_PROTOCOL_TELNET */

/* Defined if libcurl supports TFTP */
/* #undef LIBCURL_PROTOCOL_TFTP */

/* Define to 1 if `lstat' dereferences a symlink specified with a trailing
   slash. */
#define LSTAT_FOLLOWS_SLASHED_SYMLINK 1

/* Build chan_misdn for mISDN 1.2 or later. */
/* #undef MISDN_1_2 */

/* Define to the address where bug reports for this package should be sent. */
#define PACKAGE_BUGREPORT "www.asterisk.org"

/* Define to the full name of this package. */
#define PACKAGE_NAME "asterisk"

/* Define to the full name and version of this package. */
#define PACKAGE_STRING "asterisk 1.4"

/* Define to the one symbol short name of this package. */
#define PACKAGE_TARNAME "asterisk"

/* Define to the version of this package. */
#define PACKAGE_VERSION "1.4"

/* Define to 1 if the C compiler supports function prototypes. */
/* #undef PROTOTYPES */

/* Define to necessary symbol if this constant uses a non-standard name on
   your system. */
/* #undef PTHREAD_CREATE_JOINABLE */

/* Define if your system needs braces around PTHREAD_ONCE_INIT */
#define PTHREAD_ONCE_INIT_NEEDS_BRACES 1

/* Define as the return type of signal handlers (`int' or `void'). */
#define RETSIGTYPE void

/* Define to the type of arg 1 for `select'. */
#define SELECT_TYPE_ARG1 int

/* Define to the type of args 2, 3 and 4 for `select'. */
#define SELECT_TYPE_ARG234 (fd_set *)

/* Define to the type of arg 5 for `select'. */
#define SELECT_TYPE_ARG5 (struct timeval *)

/* Define to 1 if the `setvbuf' function takes the buffering type as its
   second argument and the buffer pointer as the third, as on System V before
   release 3. */
/* #undef SETVBUF_REVERSED */

/* The size of `int', as computed by sizeof. */
#define SIZEOF_INT 4

/* If using the C implementation of alloca, define if you know the
   direction of stack growth for your system; otherwise it will be
   automatically deduced at runtime.
	STACK_DIRECTION > 0 => grows toward higher addresses
	STACK_DIRECTION < 0 => grows toward lower addresses
	STACK_DIRECTION = 0 => direction of growth unknown */
/* #undef STACK_DIRECTION */

/* Define to 1 if you have the ANSI C header files. */
#define STDC_HEADERS 1

/* Define to 1 if you can safely include both <sys/time.h> and <time.h>. */
#define TIME_WITH_SYS_TIME 1

/* Define to 1 if your <sys/time.h> declares `struct tm'. */
/* #undef TM_IN_SYS_TIME */

/* Define to 1 if on AIX 3.
   System headers sometimes define this.
   We just want to avoid a redefinition error message.  */
#ifndef _ALL_SOURCE
# define _ALL_SOURCE 1
#endif

/* Number of bits in a file offset, on hosts where this is settable. */
#define _FILE_OFFSET_BITS 64

/* Enable GNU extensions on systems that have them.  */
#ifndef _GNU_SOURCE
# define _GNU_SOURCE 1
#endif

/* Define to 1 to make fseeko visible on some hosts (e.g. glibc 2.2). */
/* #undef _LARGEFILE_SOURCE */

/* Define for large files, on AIX-style hosts. */
/* #undef _LARGE_FILES */

/* Define to 1 if on MINIX. */
/* #undef _MINIX */

/* Define to 2 if the system does not provide POSIX.1 features except with
   this defined. */
/* #undef _POSIX_1_SOURCE */

/* Define to 1 if you need to in order for `stat' and other things to work. */
/* #undef _POSIX_SOURCE */

/* Enable extensions on Solaris.  */
#ifndef __EXTENSIONS__
# define __EXTENSIONS__ 1
#endif
#ifndef _POSIX_PTHREAD_SEMANTICS
# define _POSIX_PTHREAD_SEMANTICS 1
#endif
#ifndef _TANDEM_SOURCE
# define _TANDEM_SOURCE 1
#endif

/* Define like PROTOTYPES; this can be used by system headers. */
/* #undef __PROTOTYPES */

/* Define to empty if `const' does not conform to ANSI C. */
/* #undef const */

/* Define curl_free() as free() if our version of curl lacks curl_free. */
/* #undef curl_free */

/* Define to `int' if <sys/types.h> doesn't define. */
/* #undef gid_t */

/* Define to `__inline__' or `__inline' if that's what the C compiler
   calls it, or to nothing if 'inline' is not supported under any name.  */
#ifndef __cplusplus
/* #undef inline */
#endif

/* Define to `int' if <sys/types.h> does not define. */
/* #undef mode_t */

/* Define to `long int' if <sys/types.h> does not define. */
/* #undef off_t */

/* Define to `int' if <sys/types.h> does not define. */
/* #undef pid_t */

/* Define to `unsigned int' if <sys/types.h> does not define. */
/* #undef size_t */

/* Define to `int' if <sys/types.h> doesn't define. */
/* #undef uid_t */

/* Define as `fork' if `vfork' does not work. */
/* #undef vfork */

/* Define to empty if the keyword `volatile' does not work. Warning: valid
   code using `volatile' can become incorrect without. Disable with care. */
/* #undef volatile */

#endif

