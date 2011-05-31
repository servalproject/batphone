#include "../mphlr.h"

char	*optarg;		// global argument pointer
int		optind = 0; 	// global argv index

int strncasecmp(char *a,char *b,int len){
	return CompareStringA(LOCALE_INVARIANT, NORM_IGNORECASE, a, -1, b, len) -2;
}

int getopt(int argc, char *argv[], char *optstring)
{
	char c;
	char *cp;
	static char *next = NULL;


	if (optind == 0)
		next = NULL;

	optarg = NULL;

	if (next == NULL || *next == '\0')
	{
		if (optind == 0)
			optind++;

		if (optind >= argc || argv[optind][0] != '-' || argv[optind][1] == '\0')
		{
			optarg = NULL;
			if (optind < argc)
				optarg = argv[optind];
			return EOF;
		}

		if (strcmp(argv[optind], "--") == 0)
		{
			optind++;
			optarg = NULL;
			if (optind < argc)
				optarg = argv[optind];
			return EOF;
		}

		next = argv[optind];
		next++;		// skip past -
		optind++;
	}

	c = *next++;
	cp = strchr(optstring, c);

	if (cp == NULL || c == ':')
		return '?';

	cp++;
	if (*cp == ':')
	{
		if (*next != '\0')
		{
			optarg = next;
			next = NULL;
		}
		else if (optind < argc)
		{
			optarg = argv[optind];
			optind++;
		}
		else
		{
			return '?';
		}
	}

	return c;
}

int gettimeofday(struct timeval *tv, void *tz)
{
  FILETIME ft;
  unsigned __int64 tmpres = 0;
  static int tzflag;
 
  if (NULL != tv)
  {
    GetSystemTimeAsFileTime(&ft);
 
    tmpres |= ft.dwHighDateTime;
    tmpres <<= 32;
    tmpres |= ft.dwLowDateTime;
 
    /*converting file time to unix epoch*/
    tmpres -= DELTA_EPOCH_IN_MICROSECS; 
    tmpres /= 10;  /*convert into microseconds*/
    tv->tv_sec = (long)(tmpres / 1000000UL);
    tv->tv_usec = (long)(tmpres % 1000000UL);
  }
 
  return 0;
}

void * mmap(void *i1, size_t len, int i2, int i3, int fd, int offset){
  HANDLE fm;
  HANDLE h = (HANDLE) _get_osfhandle (fd);

  fm = CreateFileMapping(h, NULL, PAGE_READWRITE, 0, len+offset, NULL);
  return MapViewOfFile(fm, FILE_MAP_WRITE, 0, offset, len);
}

