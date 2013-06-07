LOCAL_PATH:= $(call my-dir)

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

# Build serval-dna library & dna binary
include $(LOCAL_PATH)/serval-dna/Android.mk

# Build adhoc-edify
include $(LOCAL_PATH)/adhoc-edify/Android.mk

