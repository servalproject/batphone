# Copyright 2009 The Android Open Source Project

# Included by top-level Android.mk

# Build edify library
include $(CLEAR_VARS)

LOCAL_MODULE:= edify

LOCAL_SRC_FILES:=	adhoc-edify/edify/expr.c \
			adhoc-edify/edify/lex.yy.c \
			adhoc-edify/edify/main.c \
			adhoc-edify/edify/y.tab.c

include $(BUILD_STATIC_LIBRARY)

# Prebuilt cutils (blech)
include $(CLEAR_VARS)
LOCAL_MODULE := cutils
LOCAL_SRC_FILES := adhoc-edify/lib/libcutils.so
include $(PREBUILT_SHARED_LIBRARY)

# Build adhoc binary
include $(CLEAR_VARS)

LOCAL_SRC_FILES :=	adhoc-edify/install.c \
			adhoc-edify/adhoc.c 

LOCAL_C_INCLUDES += $(LOCAL_PATH)/adhoc-edify/include

LOCAL_STATIC_LIBRARIES := libedify

PREBUILT_SHARED_LIBRARIES := adhoc-edify/lib/libcutils.so

LOCAL_SHARED_LIBRARIES :=	libcutils \
				libhardware_legacy

LOCAL_MODULE := adhoc

include $(BUILD_EXECUTABLE)
