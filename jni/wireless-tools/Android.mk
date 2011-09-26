include $(CLEAR_VARS)
LOCAL_PATH:= $(call my-dir)

WT_INCS  := -I$(LOCAL_PATH)
WT_DEFS := -W -Wall -Wstrict-prototypes -Wmissing-prototypes -Wshadow -Wpointer-arith -Wcast-qual -Winline
WT_DEFS += -DWT_NOLIMB=y

#####################
LOCAL_SRC_FILES := iwlib.c
LOCAL_MODULE := iwlib 
LOCAL_CFLAGS := $(WT_INCS) $(WT_DEFS)
include $(BUILD_STATIC_LIBRARY)
#####################
include $(CLEAR_VARS)
LOCAL_SRC_FILES := iwspy.c
LOCAL_MODULE := iwspy 
LOCAL_STATIC_LIBRARIES := iwlib
LOCAL_CFLAGS := $(WT_INCS) $(WT_DEFS)
include $(BUILD_EXECUTABLE)
#####################
include $(CLEAR_VARS)
LOCAL_SRC_FILES := iwconfig.c
LOCAL_MODULE := iwconfig 
LOCAL_STATIC_LIBRARIES := iwlib
LOCAL_CFLAGS := $(WT_INCS) $(WT_DEFS)
include $(BUILD_EXECUTABLE)
#####################
include $(CLEAR_VARS)
LOCAL_SRC_FILES := iwlist.c
LOCAL_MODULE := iwlist 
LOCAL_STATIC_LIBRARIES := iwlib
LOCAL_CFLAGS := $(WT_INCS) $(WT_DEFS)
include $(BUILD_EXECUTABLE)
#####################
include $(CLEAR_VARS)
LOCAL_SRC_FILES := iwpriv.c
LOCAL_MODULE := iwpriv 
LOCAL_STATIC_LIBRARIES := iwlib
LOCAL_CFLAGS := $(WT_INCS) $(WT_DEFS)
include $(BUILD_EXECUTABLE)
#####################
include $(CLEAR_VARS)
LOCAL_SRC_FILES := iwevent.c
LOCAL_MODULE := iwevent 
LOCAL_STATIC_LIBRARIES := iwlib
LOCAL_CFLAGS := $(WT_INCS) $(WT_DEFS)
include $(BUILD_EXECUTABLE)
#####################
include $(CLEAR_VARS)
LOCAL_SRC_FILES := iwgetid.c
LOCAL_MODULE := iwgetid 
LOCAL_STATIC_LIBRARIES := iwlib
LOCAL_CFLAGS := $(WT_INCS) $(WT_DEFS)
include $(BUILD_EXECUTABLE)
#####################
include $(CLEAR_VARS)
LOCAL_SRC_FILES := ifrename.c \
		   getdelim.c
LOCAL_MODULE := ifrename
LOCAL_STATIC_LIBRARIES := iwlib
LOCAL_CFLAGS := $(WT_INCS) $(WT_DEFS)
include $(BUILD_EXECUTABLE)
#####################
include $(CLEAR_VARS)
LOCAL_SRC_FILES := iwstatus.c
LOCAL_MODULE := iwstatus
LOCAL_STATIC_LIBRARIES := iwlib
LOCAL_CFLAGS := $(WT_INCS) $(WT_DEFS)
include $(BUILD_SHARED_LIBRARY))
#####################

