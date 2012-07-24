# Included by top-level Android.mk

OSLEC_SRC_FILES = \
	oslec-jni/jni.c \
        oslec-jni/echo.c

OSLEC_LOCAL_CFLAGS = \
	-DHAVE_CONFIG_H=1 \
	-g \
	-I$(LOCAL_PATH)/oslec-jni/spandsp
OSLEC_LOCAL_LDLIBS = -llog

# Build liboslec.so
include $(CLEAR_VARS)
LOCAL_SRC_FILES:= $(OSLEC_SRC_FILES)
LOCAL_CFLAGS := $(OSLEC_LOCAL_CFLAGS)
LOCAL_LDLIBS := $(OSLEC_LOCAL_LDLIBS)
LOCAL_MODULE:= oslec
include $(BUILD_SHARED_LIBRARY)

