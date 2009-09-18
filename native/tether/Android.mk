ifneq ($(TARGET_SIMULATOR),true)

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES:= tetherStartStop.cpp
LOCAL_MODULE := tether

include $(BUILD_EXECUTABLE)

endif  # TARGET_SIMULATOR != true
