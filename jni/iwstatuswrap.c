#include <dlfcn.h>
#include <stdio.h>

int main(int argc,char **argv)
{
 void *h = dlopen("/data/data/org.servalproject/lib/libiwstatus.so",RTLD_LAZY);
 int (*themain)(int,char **) = dlsym(h,"main_iwconfig");
 char *msg = dlsym(h,"msg_out");
 if (!themain) return fprintf(stderr,"Could not load libiwstatus.so\n");
 (*themain)(argc,argv);

 printf("completed:\n[%s]\n",msg);
 return 0;
}
