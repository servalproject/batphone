To build olsrd, edit jni/olsrd/make/Makefile.android and modify the path of NDK and NDK_ARCH to match your environment. 
Run the following commands;
jni/olsrd> make OS=android DEBUG=0 build_all
jni/olsrd> cp olsrd ../../data/bin
