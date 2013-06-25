#include "opus.h"
#include <jni.h>

JNIEXPORT jlong JNICALL Java_org_servalproject_audio_Opus_encodercreate(JNIEnv *env, jobject this, jint sample_rate)
{
  int error;
  OpusEncoder *enc = opus_encoder_create(sample_rate, 1, OPUS_APPLICATION_VOIP, &error);

  return (jlong)enc;
}

JNIEXPORT void JNICALL Java_org_servalproject_audio_Opus_encoderbitrate(JNIEnv *env, jobject this, jlong ptr, jint bitrate)
{
  OpusEncoder *enc = (OpusEncoder *)ptr;
  opus_encoder_ctl(enc, OPUS_SET_BITRATE(bitrate));
}

JNIEXPORT void JNICALL Java_org_servalproject_audio_Opus_encodercomplexity(JNIEnv *env, jobject this, jlong ptr, jint complexity)
{
  OpusEncoder *enc = (OpusEncoder *)ptr;
  opus_encoder_ctl(enc, OPUS_SET_COMPLEXITY(complexity));
}

JNIEXPORT void JNICALL Java_org_servalproject_audio_Opus_encoderdestroy(JNIEnv *env, jobject this, jlong ptr)
{
  OpusEncoder *enc = (OpusEncoder *)ptr;
  opus_encoder_destroy(enc);
}

JNIEXPORT jint JNICALL Java_org_servalproject_audio_Opus_encode(JNIEnv *env, jobject this, jlong ptr, jint data_size, jbyteArray in, jbyteArray out)
{
  OpusEncoder *enc = (OpusEncoder *)ptr;

  int input_buffer_size = (*env)->GetArrayLength(env, in);
  int output_buffer_size = (*env)->GetArrayLength(env, out);
  if (data_size>input_buffer_size)
    return -100;

  jbyte * inBytes = (*env)->GetByteArrayElements(env, in, (void*)0);
  jbyte * outBytes = (*env)->GetByteArrayElements(env, out, (void*)0);

  int ret = opus_encode(enc, (opus_int16 *)inBytes, data_size / 2, outBytes, output_buffer_size);

  (*env)->ReleaseByteArrayElements(env, in, inBytes, 0);
  (*env)->ReleaseByteArrayElements(env, out, outBytes, 0);

  return ret;
}

JNIEXPORT jlong JNICALL Java_org_servalproject_audio_Opus_decodercreate(JNIEnv *env, jobject this, jint sample_rate)
{
  int error;
  OpusDecoder *dec = opus_decoder_create(sample_rate, 1, &error);

  return (jlong)dec;
}

JNIEXPORT jlong JNICALL Java_org_servalproject_audio_Opus_decoderdestroy(JNIEnv *env, jobject this, jlong ptr)
{
  OpusDecoder *dec = (OpusDecoder *)ptr;
  opus_decoder_destroy(dec);
}

JNIEXPORT jint JNICALL Java_org_servalproject_audio_Opus_decode(JNIEnv *env, jobject this, jlong ptr, jint data_size, jbyteArray in, jint output_size, jbyteArray out)
{
  OpusDecoder *dec = (OpusDecoder *)ptr;

  int input_buffer_size = 0;
  jbyte *inBytes = 0;

  int output_buffer_size = (*env)->GetArrayLength(env, out);

  if (data_size){
    input_buffer_size = (*env)->GetArrayLength(env, in);
    if (data_size > input_buffer_size)
      return -100;
    inBytes = (*env)->GetByteArrayElements(env, in, (void*)0);
  }else{
    if (output_size > output_buffer_size)
      return -101;
    output_buffer_size = output_size;
  }
  jbyte * outBytes = (*env)->GetByteArrayElements(env, out, (void*)0);

  int ret = opus_decode(dec, inBytes, data_size, (opus_int16 *)outBytes, output_buffer_size / 2, 0);

  if (inBytes)
    (*env)->ReleaseByteArrayElements(env, in, inBytes, 0);
  (*env)->ReleaseByteArrayElements(env, out, outBytes, 0);

  return ret * 2;
}
