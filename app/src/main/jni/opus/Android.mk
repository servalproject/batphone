LOCAL_PATH_OLD := $(LOCAL_PATH)
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

include $(LOCAL_PATH)/celt_sources.mk
include $(LOCAL_PATH)/silk_sources.mk
include $(LOCAL_PATH)/opus_sources.mk

LOCAL_MODULE        := servalopus

LOCAL_SRC_FILES     := $(CELT_SOURCES) $(CELT_SOURCES_ARM) \
		$(SILK_SOURCES) $(SILK_SOURCES_FIXED) $(SILK_SOURCES_ARM) \
		$(OPUS_SOURCES) $(LOCAL_PATH)/opus_jni.c

LOCAL_LDLIBS        := -lm -llog

LOCAL_C_INCLUDES    := \
		$(LOCAL_PATH)/include \
		$(LOCAL_PATH)/silk \
		$(LOCAL_PATH)/silk/fixed \
		$(LOCAL_PATH)/celt

LOCAL_CFLAGS        := -DNULL=0 -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED -D_LARGEFILE_SOURCE=1 -D_FILE_OFFSET_BITS=64
LOCAL_CFLAGS        += -Drestrict='' -D__EMX__ -DOPUS_BUILD -DFIXED_POINT=1 -DDISABLE_FLOAT_API -DUSE_ALLOCA -DHAVE_LRINT -DHAVE_LRINTF  -DAVOID_TABLES
LOCAL_CFLAGS        +=  -w -std=gnu99 -O3 -fno-strict-aliasing -fprefetch-loop-arrays  -fno-math-errno
LOCAL_CPPFLAGS      := -DBSD=1
LOCAL_CPPFLAGS      += -ffast-math -O3 -funroll-loops

include $(BUILD_SHARED_LIBRARY)

LOCAL_PATH := $(LOCAL_PATH_OLD)
