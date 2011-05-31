#include <io.h>
#include <time.h>
#include <winsock2.h>
#include <Shlwapi.h>
#include <malloc.h>

typedef struct in_addr in_addr_t;
typedef int socklen_t;
#define snprintf _snprintf
#define strdup StrDupA
#define bzero ZeroMemory
#define HAVE_BZERO
#define pollfd WSAPOLLFD
#define poll WSAPoll
#define random rand
#define srandom srand
#define sleep Sleep
#define getpid GetCurrentProcessId
#define daemon(a,b) FreeConsole()

#if defined(_MSC_VER) || defined(_MSC_EXTENSIONS)
  #define DELTA_EPOCH_IN_MICROSECS  11644473600000000Ui64
#else
  #define DELTA_EPOCH_IN_MICROSECS  11644473600000000ULL
#endif

extern int optind, opterr;
extern char *optarg;

int getopt(int argc, char *argv[], char *optstring);
int gettimeofday(struct timeval *tv, void *tz);
void * mmap(void *i1, size_t len, int i2, int i3, int fd, int offset);
int strncasecmp(char *a,char *b,int len);

#define MAP_FAILED NULL
#define PROT_READ 0
#define PROT_WRITE 0
#define MAP_SHARED 0
#define MAP_NORESERVE 0

