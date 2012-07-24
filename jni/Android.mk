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

# Build sqlite3 library
include $(LOCAL_PATH)/sqlite3/Android.mk

# Build serval-dna library & dna binary
include $(LOCAL_PATH)/serval-dna/Android.mk

# Build adhoc-edify
include $(LOCAL_PATH)/adhoc-edify/Android.mk

# Build oslec echo canceller
include $(LOCAL_PATH)/oslec-jni/Android.mk
