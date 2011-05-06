OBJS=	dna.o server.o client.o peers.o ciphers.o responses.o packetformats.o dataformats.o \
	hlrdata.o srandomdev.o simulate.o batman.o overlay.o export.o
HDRS=	Makefile mphlr.h
LDFLAGS=	
DEFS=	-DPACKAGE_NAME=\"\" -DPACKAGE_TARNAME=\"\" -DPACKAGE_VERSION=\"\" -DPACKAGE_STRING=\"\" -DPACKAGE_BUGREPORT=\"\" -DPACKAGE_URL=\"\" -DHAVE_LIBC=1 -DSTDC_HEADERS=1 -DHAVE_SYS_TYPES_H=1 -DHAVE_SYS_STAT_H=1 -DHAVE_STDLIB_H=1 -DHAVE_STRING_H=1 -DHAVE_MEMORY_H=1 -DHAVE_STRINGS_H=1 -DHAVE_INTTYPES_H=1 -DHAVE_STDINT_H=1 -DHAVE_UNISTD_H=1 -DHAVE_STDIO_H=1 -DHAVE_ERRNO_H=1 -DHAVE_STDLIB_H=1 -DHAVE_STRINGS_H=1 -DHAVE_UNISTD_H=1 -DHAVE_STRING_H=1 -DHAVE_ARPA_INET_H=1 -DHAVE_SYS_SOCKET_H=1 -DHAVE_SYS_MMAN_H=1 -DHAVE_SYS_TIME_H=1 -DHAVE_POLL_H=1 -DHAVE_NETDB_H=1

all:	dna

%.o:	%.c $(HDRS)
	$(CC) $(DEFS) -Os -g -Wall -c $<

dna:	$(OBJS)
	$(CC) -Os -g -Wall -o dna $(OBJS) $(LDFLAGS)

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
