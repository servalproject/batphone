LOCAL_PATH:= $(call my-dir)

# Build iwconfig binary
include $(CLEAR_VARS)
LOCAL_MODULE:= iwconfig
LOCAL_SRC_FILES:= wireless_tools.29/iwlib.c wireless_tools.29/iwconfig.c
LOCAL_C_INCLUDES += wireless_tools.29/
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_EXECUTABLE)

# Build ifconfig binary
include $(CLEAR_VARS)
LOCAL_MODULE:= ifconfig
LOCAL_SRC_FILES:= ifconfig/ifconfig.c
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_EXECUTABLE)

# Build adhoc-edify
include $(CLEAR_VARS)
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
include $(CLEAR_VARS)
LOCAL_MODULE	:= libservalopus
LOCAL_SRC_FILES	:= opus/celt/bands.c opus/celt/celt.c \
	opus/celt/cwrs.c opus/celt/entcode.c \
	opus/celt/entdec.c opus/celt/entenc.c \
	opus/celt/kiss_fft.c opus/celt/laplace.c \
	opus/celt/mathops.c opus/celt/mdct.c \
	opus/celt/modes.c opus/celt/pitch.c \
	opus/celt/celt_lpc.c opus/celt/quant_bands.c \
	opus/celt/rate.c opus/celt/vq.c \
	opus/celt/celt_decoder.c opus/celt/celt_encoder.c \
	opus/silk/CNG.c opus/silk/code_signs.c \
	opus/silk/init_decoder.c opus/silk/decode_core.c \
	opus/silk/decode_frame.c opus/silk/decode_parameters.c \
	opus/silk/decode_indices.c opus/silk/decode_pulses.c \
	opus/silk/decoder_set_fs.c opus/silk/dec_API.c \
	opus/silk/enc_API.c opus/silk/encode_indices.c \
	opus/silk/encode_pulses.c opus/silk/gain_quant.c \
	opus/silk/interpolate.c opus/silk/LP_variable_cutoff.c \
	opus/silk/NLSF_decode.c opus/silk/NSQ.c \
	opus/silk/NSQ_del_dec.c opus/silk/PLC.c \
	opus/silk/shell_coder.c opus/silk/tables_gain.c \
	opus/silk/tables_LTP.c opus/silk/tables_NLSF_CB_NB_MB.c \
	opus/silk/tables_NLSF_CB_WB.c opus/silk/tables_other.c \
	opus/silk/tables_pitch_lag.c opus/silk/tables_pulses_per_block.c \
	opus/silk/VAD.c opus/silk/control_audio_bandwidth.c \
	opus/silk/quant_LTP_gains.c opus/silk/VQ_WMat_EC.c \
	opus/silk/HP_variable_cutoff.c opus/silk/NLSF_encode.c \
	opus/silk/NLSF_VQ.c opus/silk/NLSF_unpack.c \
	opus/silk/NLSF_del_dec_quant.c opus/silk/process_NLSFs.c \
	opus/silk/stereo_LR_to_MS.c opus/silk/stereo_MS_to_LR.c \
	opus/silk/check_control_input.c opus/silk/control_SNR.c \
	opus/silk/init_encoder.c opus/silk/control_codec.c \
	opus/silk/A2NLSF.c opus/silk/ana_filt_bank_1.c \
	opus/silk/biquad_alt.c opus/silk/bwexpander_32.c \
	opus/silk/bwexpander.c opus/silk/debug.c \
	opus/silk/decode_pitch.c opus/silk/inner_prod_aligned.c \
	opus/silk/lin2log.c opus/silk/log2lin.c \
	opus/silk/LPC_analysis_filter.c opus/silk/LPC_inv_pred_gain.c \
	opus/silk/table_LSF_cos.c opus/silk/NLSF2A.c \
	opus/silk/NLSF_stabilize.c opus/silk/NLSF_VQ_weights_laroia.c \
	opus/silk/pitch_est_tables.c opus/silk/resampler.c \
	opus/silk/resampler_down2_3.c opus/silk/resampler_down2.c \
	opus/silk/resampler_private_AR2.c opus/silk/resampler_private_down_FIR.c \
	opus/silk/resampler_private_IIR_FIR.c opus/silk/resampler_private_up2_HQ.c \
	opus/silk/resampler_rom.c opus/silk/sigm_Q15.c \
	opus/silk/sort.c opus/silk/sum_sqr_shift.c \
	opus/silk/stereo_decode_pred.c opus/silk/stereo_encode_pred.c \
	opus/silk/stereo_find_predictor.c opus/silk/stereo_quant_pred.c \
	opus/silk/fixed/LTP_analysis_filter_FIX.c opus/silk/fixed/LTP_scale_ctrl_FIX.c \
	opus/silk/fixed/corrMatrix_FIX.c opus/silk/fixed/encode_frame_FIX.c \
	opus/silk/fixed/find_LPC_FIX.c opus/silk/fixed/find_LTP_FIX.c \
	opus/silk/fixed/find_pitch_lags_FIX.c opus/silk/fixed/find_pred_coefs_FIX.c \
	opus/silk/fixed/noise_shape_analysis_FIX.c opus/silk/fixed/prefilter_FIX.c \
	opus/silk/fixed/process_gains_FIX.c opus/silk/fixed/regularize_correlations_FIX.c \
	opus/silk/fixed/residual_energy16_FIX.c opus/silk/fixed/residual_energy_FIX.c \
	opus/silk/fixed/solve_LS_FIX.c opus/silk/fixed/warped_autocorrelation_FIX.c \
	opus/silk/fixed/apply_sine_window_FIX.c opus/silk/fixed/autocorr_FIX.c \
	opus/silk/fixed/burg_modified_FIX.c opus/silk/fixed/k2a_FIX.c \
	opus/silk/fixed/k2a_Q16_FIX.c opus/silk/fixed/pitch_analysis_core_FIX.c \
	opus/silk/fixed/vector_ops_FIX.c opus/silk/fixed/schur64_FIX.c \
	opus/silk/fixed/schur_FIX.c opus/src/opus.c \
	opus/src/opus_decoder.c opus/src/opus_encoder.c \
	opus/src/opus_multistream.c opus/src/repacketizer.c \
	opus_jni.c
LOCAL_CFLAGS	:= -I$(LOCAL_PATH)/opus/include -I$(LOCAL_PATH)/opus/celt \
	-I$(LOCAL_PATH)/opus/silk -I$(LOCAL_PATH)/opus/silk/fixed \
	-I$(LOCAL_PATH)/opus/src \
	-Drestrict='' -D__EMX__ -DOPUS_BUILD -DFIXED_POINT -DUSE_ALLOCA -DHAVE_LRINT -DHAVE_LRINTF -O3 -fno-math-errno
include $(BUILD_SHARED_LIBRARY)

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
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_EXECUTABLE)

# Build serval-dna library & binary
include $(CLEAR_VARS)
include $(LOCAL_PATH)/serval-dna/Android.mk

