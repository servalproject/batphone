# - configuration --
DEVICE=781DBA27C40A
DEVICE_DEST=/data/data/org.servalproject/bin

# - paths --
SYS_ROOT=$(NDK_ROOT)/platforms/android-4/arch-arm
TOOLCHAIN=$(NDK_ROOT)/toolchains/arm-linux-androideabi-4.4.3/prebuilt/darwin-x86
ADB=$(SDK_ROOT)/platform-tools/adb -s $(DEVICE)

# - toolchain --
TCBASE=arm-linux-androideabi
CC=$(TOOLCHAIN)/bin/$(TCBASE)-gcc
LD=$(TOOLCHAIN)/bin/$(TCBASE)-gcc
STRIP=$(TOOLCHAIN)/bin/$(TCBASE)-strip
TARGET_CFLAGS=--sysroot=$(SYS_ROOT) -mandroid
TARGET_LDFLAGS=$(TARGET_CFLAGS)
CFLAGS=$(TARGET_CFLAGS) 
LDFLAGS=$(TARGET_LDFLAGS)

# - sources --
_SOURCES=edify/lex.yy.c edify/y.tab.c edify/expr.c adhoc.c install.c
#edify/lex.yy.c edify/y.tab.c edify/expr.c adhoc.c install.c
SOURCES=$(_SOURCES:%=adhoc-edify/%)

# - headers --
_HEADERS=adhoc.h install.h edify/yydefs.h edify/expr.h 
HEADERS=$(_HEADERS:%=adhoc-edify/%)

# - objects --
OBJECTS=$(SOURCES:.c=.o)

# - C Flags
CFLAGS+=-DANDROID -I./adhoc-edify/ -I./include
#CFLAGS+=-pedantic -Wall -W -O1 -g3 -std=gnu99
CFLAGS+=-std=gnu99
#CFLAGS+=-DDEBUG_MALLOC -DMEMORY_USAGE -DPROFILE_DATA -DREVISION_VERSION=\"\ rv1534\"
# force lex/yacc files to be compiled as c
#CFLAGS+=-x c

# - Linker Flags
#LDFLAGS+=-static
LDFLAGS+=-L./lib -lcutils

# - Rules --
adhoc : $(SOURCES) $(OBJECTS) $(HEADERS) 
	$(LD) $(LDFLAGS) -o $@ $(OBJECTS) 
	$(STRIP) adhoc

parser : edify/parser.y edify/lexer.l
	#yacc -d parser.y
	#lex lexer.l
  #mv y.tab.h parser.h

.c.o :
	$(CC) $(CFLAGS) -MD -c $< -o $@

push :
	$(ADB) shell rm $(DEVICE_DEST)/adhoc
	$(ADB) push ./adhoc $(DEVICE_DEST)


clean :
	find . -name *~  | xargs rm
	find . -name *.o | xargs rm
	find . -name *.d | xargs rm
	rm -rf *.o *~ 
