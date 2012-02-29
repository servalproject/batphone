LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
SPANDSP     := spandsp
LOCAL_MODULE    := gsm_jni
LOCAL_SRC_FILES := gsm_jni.cpp \
	$(SPANDSP)/gsm0610_decode.c \
	$(SPANDSP)/gsm0610_encode.c \
	$(SPANDSP)/gsm0610_lpc.c \
	$(SPANDSP)/gsm0610_preprocess.c \
	$(SPANDSP)/gsm0610_rpe.c \
	$(SPANDSP)/gsm0610_short_term.c \
	$(SPANDSP)/gsm0610_long_term.c
LOCAL_ARM_MODE := arm
LOCAL_LDLIBS := -llog
LOCAL_C_INCLUDES += $(LOCAL_PATH)/$(SPANDSP)/spandsp $(LOCAL_PATH)/$(SPANDSP)
LOCAL_CFLAGS = -O3
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
IW:=wireless-tools
LOCAL_MODULE := iwstatus
LOCAL_SRC_FILES := iwstatus.c wireless-tools/iwlib.c
LOCAL_ARM_MODE:= arm
LOCAL_C_INCLUDES += $(LOCAL_PATH)/$(WT)
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE:= iwlist
LOCAL_SRC_FILES:= iwstatuswrap.c

include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_CFLAGS	:= -Wall -W -Os -g3 -std=gnu99 -DDEBUG_MALLOC -DMEMORY_USAGE -DPROFILE_DATA -DNO_POLICY_ROUTING -DREVISION_VERSION=\"3.2\" -Dfdatasync=fsync
LOCAL_MODULE	:= batmand
LOCAL_SRC_FILES := batman/allocate.c \
		   batman/batman.c \
		   batman/bitarray.c \
		   batman/hash.c \
		   batman/hna.c \
		   batman/linux/kernel.c \
		   batman/linux/route.c \
		   batman/linux/tun.c \
		   batman/list-batman.c \
		   batman/originator.c \
		   batman/posix/init.c \
		   batman/posix/posix.c \
		   batman/posix/tunnel.c \
		   batman/posix/unix_socket.c \
		   batman/profile.c \
		   batman/ring_buffer.c \
		   batman/schedule.c \
		   batman/dprintf.c

LOCAL_ARM_MODE:= arm
LOCAL_C_INCLUDES += $(LOCAL_PATH)/batman
include $(BUILD_EXECUTABLE) 

# Build sqlite3 library
include $(LOCAL_PATH)/sqlite3/Android.mk

# Build serval-dna library & dna binary
include $(LOCAL_PATH)/serval-dna/Android.mk

# Build adhoc-edify
include $(LOCAL_PATH)/adhoc-edify/Android.mk

# Build olsrd
include $(CLEAR_VARS)
LOCAL_CFLAGS	:=	-Wall -Wextra -Wold-style-definition -Wdeclaration-after-statement -Wmissing-prototypes -Wstrict-prototypes \
			-Wmissing-declarations -Wsign-compare -Waggregate-return -Wmissing-noreturn -Wmissing-format-attribute \
			-Wno-multichar -Wno-deprecated-declarations -Wendif-labels -Wwrite-strings -Wbad-function-cast -Wpointer-arith \
			-Wcast-qual -Wshadow -Wformat -Wsequence-point -Wcast-align -Wnested-externs -Winline -Wdisabled-optimization \
			-finline-functions-called-once -funit-at-a-time -fearly-inlining -fomit-frame-pointer -finline-limit=350 \
			-I$(LOCAL_PATH)/olsrd/android -I$(LOCAL_PATH)/olsrd/src -DUSE_FPM -Dlinux -DLINUX_NETLINK_ROUTING -Dandroid \
			-DINET_ADDRSTRLEN=16 -D'IPTOS_PREC(tos)=((tos)&0xe0)' -D'IPTOS_TOS(tos)=((tos)&0x1e)' \
			-DOLSRD_GLOBAL_CONF_FILE=\"/data/local/etc/olsrd.conf\" -D__ARM_ARCH_5__ -D__ARM_ARCH_5T__ -D__ARM_ARCH_5E__ \
			-D__ARM_ARCH_5TE__ -fomit-frame-pointer -fstrict-aliasing -funswitch-loops -finline-limit=300 -DNDEBUG

# Copied from jni/olsrd/Makefile
VERS =          pre-0.6.2

# Compute MD5 hash of filenames
FNHASH := $(shell cd $(LOCAL_PATH)/olsrd ; find . -name '*.[ch]' | openssl md5)

.PHONY:	$(LOCAL_PATH)/olsrd/src/builddata_android.c
$(LOCAL_PATH)/olsrd/src/builddata_android.c:
	@$(RM) "$@"
	@echo "#include \"defs.h\"" >> "$@" 
	@echo "const char olsrd_version[] = \"olsr.org -  $(VERS)-git_`git log -1 --pretty=%h`-hash_$(FNHASH)\";"  >> "$@"
	@date +"const char build_date[] = \"%Y-%m-%d %H:%M:%S\";" >> "$@" 
	@echo "const char build_host[] = \"$(shell hostname)\";" >> "$@" 

$(LOCAL_PATH)/olsrd/src/cfgparser/oparse.c: $(LOCAL_PATH)/olsrd/src/cfgparser/oparse.y
	bison -d -o "$@" "$<"

$(LOCAL_PATH)/olsrd/src/cfgparser/oscan.c: $(LOCAL_PATH)/olsrd/src/cfgparser/oscan.lex
	flex -Cem -o"$@" "$<"

LOCAL_MODULE	:=	olsrd
LOCAL_SRC_FILES :=	\
			olsrd/src/build_msg.c \
			olsrd/src/builddata_android.c \
			olsrd/src/cfgparser/cfgfile_gen.c \
			olsrd/src/cfgparser/olsrd_conf.c \
			olsrd/src/cfgparser/oparse.c \
			olsrd/src/cfgparser/oscan.c \
			olsrd/src/common/autobuf.c \
			olsrd/src/common/avl.c \
			olsrd/src/common/list.c \
			olsrd/src/duplicate_handler.c \
			olsrd/src/duplicate_set.c \
			olsrd/src/fpm.c \
			olsrd/src/gateway.c \
			olsrd/src/gateway_default_handler.c \
			olsrd/src/generate_msg.c \
			olsrd/src/hashing.c \
			olsrd/src/hna_set.c \
			olsrd/src/hysteresis.c \
			olsrd/src/interfaces.c \
			olsrd/src/ipc_frontend.c \
			olsrd/src/ipcalc.c \
			olsrd/src/link_set.c \
			olsrd/src/linux/apm.c \
			olsrd/src/linux/kernel_routes_ioctl.c \
			olsrd/src/linux/kernel_routes_nl.c \
			olsrd/src/linux/kernel_tunnel.c \
			olsrd/src/linux/link_layer.c \
			olsrd/src/linux/net.c \
			olsrd/src/lq_mpr.c \
			olsrd/src/lq_packet.c \
			olsrd/src/lq_plugin.c \
			olsrd/src/lq_plugin_default_ff.c \
			olsrd/src/lq_plugin_default_ffeth.c \
			olsrd/src/lq_plugin_default_float.c \
			olsrd/src/lq_plugin_default_fpm.c \
			olsrd/src/main.c \
			olsrd/src/mantissa.c \
			olsrd/src/mid_set.c \
			olsrd/src/mpr.c \
			olsrd/src/mpr_selector_set.c \
			olsrd/src/neighbor_table.c \
			olsrd/src/net_olsr.c \
			olsrd/src/olsr.c \
			olsrd/src/olsr_cookie.c \
			olsrd/src/olsr_niit.c \
			olsrd/src/olsr_spf.c \
			olsrd/src/packet.c \
			olsrd/src/parser.c \
			olsrd/src/plugin_loader.c \
			olsrd/src/plugin_util.c \
			olsrd/src/print_packet.c \
			olsrd/src/process_package.c \
			olsrd/src/process_routes.c \
			olsrd/src/rebuild_packet.c \
			olsrd/src/routing_table.c \
			olsrd/src/scheduler.c \
			olsrd/src/tc_set.c \
			olsrd/src/two_hop_neighbor_table.c \
			olsrd/src/unix/ifnet.c \
			olsrd/src/unix/log.c \
			olsrd/src/unix/misc.c

LOCAL_LDLIBS := -llog

include $(BUILD_EXECUTABLE) 

