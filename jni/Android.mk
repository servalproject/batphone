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
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog
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
BATMAND	:= batmand
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

include $(LOCAL_PATH)/serval-dna/Android.mk

