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

# Codec 2
include $(CLEAR_VARS)
LOCAL_MODULE := libcodec2
LOCAL_LDLIBS := -llog
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


