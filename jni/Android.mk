LOCAL_PATH:= $(call my-dir)

# Build iwconfig binary
include $(CLEAR_VARS)
LOCAL_MODULE:= iwconfig
LOCAL_SRC_FILES:= wireless_tools.29/iwlib.c wireless_tools.29/iwconfig.c
LOCAL_C_INCLUDES += wireless_tools.29/
include $(BUILD_EXECUTABLE)

# Build ifconfig binary
include $(CLEAR_VARS)
LOCAL_MODULE:= ifconfig
LOCAL_SRC_FILES:= ifconfig/ifconfig.c
include $(BUILD_EXECUTABLE)

# Build adhoc-edify
include $(CLEAR_VARS)
include $(LOCAL_PATH)/adhoc-edify/Android.mk

# Codec 2
include $(CLEAR_VARS)
LOCAL_MODULE := libcodec2
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
include $(CLEAR_VARS)
LOCAL_MODULE            := opus.a
LOCAL_SRC_FILES         := opus/.libs/libopus.a
LOCAL_EXPORT_C_INCLUDES := opus/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE	:= libopus
LOCAL_STATIC_LIBRARIES := opus.a
LOCAL_SRC_FILES	:= opus_jni.c
LOCAL_CFLAGS	:= -I$(LOCAL_PATH)/opus/include -O3
include $(BUILD_SHARED_LIBRARY)

# netlink library for iw
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
LOCAL_SRC_FILES := \
        iw/bitrate.c \
        iw/connect.c \
        iw/cqm.c \
        iw/event.c \
        iw/genl.c \
        iw/ibss.c \
        iw/info.c \
        iw/interface.c \
        iw/iw.c \
        iw/link.c \
        iw/mesh.c \
        iw/mpath.c \
        iw/offch.c \
        iw/phy.c \
        iw/ps.c \
        iw/reason.c \
        iw/reg.c \
        iw/scan.c \
        iw/sections.c \
        iw/station.c \
        iw/status.c \
        iw/survey.c \
        iw/util.c \
        iw/version.c
LOCAL_CFLAGS := -I$(LOCAL_PATH)/iw/ -I$(LOCAL_PATH)/libnl/ $(TARGET_GLOBAL_CFLAGS) $(PRIVATE_ARM_CFLAGS)
LOCAL_MODULE := iw
LOCAL_LDFLAGS := -Wl,--no-gc-sections
LOCAL_STATIC_LIBRARIES += libnl
include $(BUILD_EXECUTABLE)

# Build serval-dna library & binary
include $(CLEAR_VARS)
include $(LOCAL_PATH)/serval-dna/Android.mk

