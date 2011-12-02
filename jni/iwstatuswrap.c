#include <dlfcn.h>
#include <stdio.h>

int main(int argc,char **argv)
{
 void *h = dlopen("/data/data/org.servalproject/lib/libiwstatus.so",RTLD_LAZY);
 int (*dnamain)(int,char **) = dlsym(h,"main_iwstatus");
 if (!dnamain) return fprintf(stderr,"Could not load libiwstatus.so\n");
 return (*dnamain)(argc,argv);

}
