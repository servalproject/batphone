#include <dlfcn.h>
#include <stdio.h>

int main(int argc,char **argv)
{
 void *h = dlopen("/data/data/org.servalproject/lib/libiwstatus.so",RTLD_LAZY);
 int (*themain)(int,char **) = dlsym(h,"main_iwconfig");
 if (!themain) return fprintf(stderr,"Could not load libiwstatus.so\n");
 return (*themain)(argc,argv);
}
