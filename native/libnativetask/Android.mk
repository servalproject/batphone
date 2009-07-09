LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := android_tether_system_NativeTask.c 

LOCAL_SHARED_LIBRARIES := libcutils

LOCAL_C_INCLUDES += $(JNI_H_INCLUDE)

LOCAL_MODULE := libNativeTask

include $(BUILD_SHARED_LIBRARY)
