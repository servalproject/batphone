LOCAL_PATH:= $(call my-dir)

SODIUM_ARCH_FOLDER := $(TARGET_ARCH)
ifeq ($(SODIUM_ARCH_FOLDER),arm)
    SODIUM_ARCH_FOLDER = armv6
endif
ifeq ($(SODIUM_ARCH_FOLDER),arm-64)
    SODIUM_ARCH_FOLDER = armv8-a
endif
ifeq ($(SODIUM_ARCH_FOLDER),x86)
        SODIUM_ARCH_FOLDER = i686
endif
ifeq ($(SODIUM_ARCH_FOLDER),mips)
        SODIUM_ARCH_FOLDER = mips32
endif
SODIUM_BASE := libsodium/libsodium-android-$(SODIUM_ARCH_FOLDER)
SODIUM_INCLUDE := $(LOCAL_PATH)/$(SODIUM_BASE)/include

# Build iwconfig binary
include $(CLEAR_VARS)
LOCAL_MODULE:= iwconfig-NOPIE
LOCAL_SRC_FILES:= wireless_tools.29/iwlib.c wireless_tools.29/iwconfig.c
LOCAL_C_INCLUDES += wireless_tools.29/
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE:= iwconfig-PIE
LOCAL_SRC_FILES:= wireless_tools.29/iwlib.c wireless_tools.29/iwconfig.c
LOCAL_C_INCLUDES += wireless_tools.29/
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_EXECUTABLE)

# Build ifconfig binary
include $(CLEAR_VARS)
LOCAL_MODULE:= ifconfig-NOPIE
LOCAL_SRC_FILES:= ifconfig/ifconfig.c
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE:= ifconfig-PIE
LOCAL_SRC_FILES:= ifconfig/ifconfig.c
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_EXECUTABLE)

# Build adhoc-edify
include $(LOCAL_PATH)/adhoc-edify/Android.mk

# Codec 2
include $(CLEAR_VARS)
LOCAL_MODULE := libservalcodec2
LOCAL_CFLAGS := -O3 -ffast-math -DNDEBUG
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/codec2
LOCAL_SRC_FILES := codec2/dump.c codec2/lpc.c \
        codec2/nlp.c codec2/postfilter.c \
        codec2/sine.c codec2/codec2.c \
        codec2/fifo.c codec2/fdmdv.c \
        codec2/kiss_fft.c codec2/interp.c \
        codec2/lsp.c codec2/phase.c \
        codec2/quantise.c codec2/pack.c \
        codec2/codebook.c codec2/codebookd.c \
        codec2/codebookvq.c codec2/codebookjnd.c \
        codec2/codebookjvm.c codec2/codebookvqanssi.c \
        codec2/codebookdt.c codec2/codebookge.c \
	codec2_jni.c
include $(BUILD_SHARED_LIBRARY)


# Build libopus
include $(LOCAL_PATH)/opus/Android.mk

include $(CLEAR_VARS)
LOCAL_SRC_FILES :=  libnl/cache.c \
        libnl/data.c \
        libnl/nl.c \
        libnl/doc.c \
        libnl/cache_mngr.c \
        libnl/addr.c \
        libnl/socket.c \
        libnl/fib_lookup/lookup.c \
        libnl/fib_lookup/request.c \
        libnl/msg.c \
        libnl/object.c \
        libnl/attr.c \
        libnl/utils.c \
        libnl/cache_mngt.c \
        libnl/handlers.c \
        libnl/genl/ctrl.c \
        libnl/genl/mngt.c \
        libnl/genl/family.c \
        libnl/genl/genl.c \
        libnl/route/rtnl.c \
        libnl/route/route_utils.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/libnl
LOCAL_MODULE := libnl
include $(BUILD_STATIC_LIBRARY)


include $(CLEAR_VARS)
FILE_LIST := $(wildcard $(LOCAL_PATH)/iw/*.c)
LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%)
LOCAL_CFLAGS := -I$(LOCAL_PATH)/iw/ -I$(LOCAL_PATH)/libnl/ $(TARGET_GLOBAL_CFLAGS) $(PRIVATE_ARM_CFLAGS)
LOCAL_MODULE := iw-NOPIE
LOCAL_LDFLAGS := -Wl,--no-gc-sections
LOCAL_STATIC_LIBRARIES += libnl
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
FILE_LIST := $(wildcard $(LOCAL_PATH)/iw/*.c)
LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%)
LOCAL_CFLAGS := -I$(LOCAL_PATH)/iw/ -I$(LOCAL_PATH)/libnl/ $(TARGET_GLOBAL_CFLAGS) $(PRIVATE_ARM_CFLAGS) -fPIE
LOCAL_MODULE := iw-PIE
LOCAL_LDFLAGS := -Wl,--no-gc-sections -fPIE -pie
LOCAL_STATIC_LIBRARIES += libnl
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE:= sodium
LOCAL_SRC_FILES:= $(SODIUM_BASE)/lib/libsodium.a
include $(PREBUILT_STATIC_LIBRARY)

# Build serval-dna library & binary
include $(LOCAL_PATH)/serval-dna/Android.mk
