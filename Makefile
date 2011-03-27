OBJS=	dna.o server.o client.o peers.o ciphers.o responses.o packetformats.o dataformats.o hlrdata.o srandomdev.o simulate.o batman.o
CFILES=	dna.c server.c client.c peers.c ciphers.c responses.c packetformats.c dataformats.c hlrdata.c srandomdev.c simulate.c batman.c
HDRS=	Makefile mphlr.h
LDFLAGS=	-Xlinker -I/data/data/org.servalproject/lib/ld-linux.so.3 -R/data/data/org.servalproject/lib -Xlinker -rpath=/data/data/org.servalproject/lib
DEFS=	-DPACKAGE_NAME=\"\" -DPACKAGE_TARNAME=\"\" -DPACKAGE_VERSION=\"\" -DPACKAGE_STRING=\"\" -DPACKAGE_BUGREPORT=\"\" -DPACKAGE_URL=\"\" -DHAVE_LIBC=1 -DSTDC_HEADERS=1 -DHAVE_SYS_TYPES_H=1 -DHAVE_SYS_STAT_H=1 -DHAVE_STDLIB_H=1 -DHAVE_STRING_H=1 -DHAVE_MEMORY_H=1 -DHAVE_STRINGS_H=1 -DHAVE_INTTYPES_H=1 -DHAVE_STDINT_H=1 -DHAVE_UNISTD_H=1 -DHAVE_STDIO_H=1 -DHAVE_ERRNO_H=1 -DHAVE_STDLIB_H=1 -DHAVE_STRINGS_H=1 -DHAVE_UNISTD_H=1 -DHAVE_STRING_H=1 -DHAVE_ARPA_INET_H=1 -DHAVE_SYS_SOCKET_H=1 -DHAVE_SYS_MMAN_H=1 -DHAVE_SYS_TIME_H=1 -DHAVE_POLL_H=1 -DHAVE_NETDB_H=1

all:	dna app_serval.so

%.o:	%.c $(HDRS)
	$(CC) $(DEFS) -Os -g -Wall -c $<

dna:	$(OBJS)
	$(CC) -Os -g -Wall -o dna $(OBJS) $(LDFLAGS)

app_serval.so: app_serval.c
	gcc -DFORASTERISK -c -o app_serval.o -Iasterisk_include app_serval.c
	gcc -shared -Wl,-soname,app_serval.so.1 -o app_serval.so app_serval.o

app_serval.c: asterisk_app.c $(CFILES) $(HDRS)
	cat asterisk_app.c > app_serval.c
	cat $(CFILES) | grep -v "#include" >> app_serval.c

testserver: dna
	clear
	rm hlr.dat
	./dna -vvv -S 1 -f hlr.dat

testcreate: dna
	clear
	./dna -vvv -d 0427679796 -C
	@touch testcreate

testget:	dna testcreate
	clear
	./dna -vvv -d 0427679796 -R dids | tee testget

testset:	dna testget
	clear
	# Try writing a value to a variable
	echo "short value" >shortvalue.txt
	./dna -vvv -s `cat testget | cut -f2 -d: | tail -1` -i 0 -W note=@shortvalue.txt

testbigset: testget
	clear
	./dna -vvv -s `cat testget | cut -f2 -d: | tail -1` -i 0 -W note=@411.txt
