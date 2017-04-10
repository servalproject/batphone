#include "codec2/codec2.h"
#include <jni.h>

JNIEXPORT jlong JNICALL Java_org_servalproject_audio_Codec2_init(JNIEnv *env, jobject this, jint mode)
{
  switch(mode){
  case 3200:
    mode = CODEC2_MODE_3200;
    break;
  case 2400:
    mode = CODEC2_MODE_2400;
    break;
  case 1600:
    mode = CODEC2_MODE_1600;
    break;
  case 1400:
    mode = CODEC2_MODE_1400;
    break;
  case 1300:
    mode = CODEC2_MODE_1300;
    break;
  case 1200:
    mode = CODEC2_MODE_1200;
    break;
  }
  struct CODEC2 *c2=codec2_create(mode);
  int bits = codec2_bits_per_frame(c2);
  int samples = codec2_samples_per_frame(c2);
  return (jlong)c2;
}

JNIEXPORT void JNICALL Java_org_servalproject_audio_Codec2_release(JNIEnv *env, jobject this, jlong ptr)
{
  struct CODEC2 *c2=(struct CODEC2*)ptr;
  codec2_destroy(c2);
}

JNIEXPORT jint JNICALL Java_org_servalproject_audio_Codec2_encode(JNIEnv *env, jobject this, jlong ptr, jint data_size, jbyteArray in, jbyteArray out)
{
  struct CODEC2 *c2=(struct CODEC2*)ptr;
  int input_block_size = codec2_samples_per_frame(c2)*2;
  int output_block_size = (codec2_bits_per_frame(c2)+7)/8;

  int input_buffer_size = (*env)->GetArrayLength(env, in);
  int output_buffer_size = (*env)->GetArrayLength(env, out);
  if (data_size>input_buffer_size)
    return -1;
  if (data_size % input_block_size != 0)
    return -2;
  int block_count = data_size / input_block_size;
  if (output_buffer_size < output_block_size * block_count)
    return -3;

  jbyte * inBytes = (*env)->GetByteArrayElements(env, in, (void*)0);
  jbyte * outBytes = (*env)->GetByteArrayElements(env, out, (void*)0);
  int i;
  for (i=0;i<block_count;i++)
    codec2_encode(c2, outBytes + (output_block_size * i), (short *)(inBytes + (input_block_size * i)));
  (*env)->ReleaseByteArrayElements(env, in, inBytes, 0);
  (*env)->ReleaseByteArrayElements(env, out, outBytes, 0);

  return output_block_size * block_count;
}

JNIEXPORT jint JNICALL Java_org_servalproject_audio_Codec2_decode(JNIEnv *env, jobject this, jlong ptr, jint data_size, jbyteArray in, jbyteArray out)
{
  struct CODEC2 *c2=(struct CODEC2*)ptr;
  int output_block_size = codec2_samples_per_frame(c2)*2;
  int input_block_size = (codec2_bits_per_frame(c2)+7)/8;

  int input_buffer_size = (*env)->GetArrayLength(env, in);
  int output_buffer_size = (*env)->GetArrayLength(env, out);
  if (data_size > input_buffer_size)
    return -1;
  if (data_size % input_block_size != 0)
    return -2;
  int block_count = data_size / input_block_size;
  if (output_buffer_size < block_count * output_block_size)
    return -3;

  jbyte * inBytes = (*env)->GetByteArrayElements(env, in, (void*)0);
  jbyte * outBytes = (*env)->GetByteArrayElements(env, out, (void*)0);

  int i;
  for (i=0;i<block_count;i++)
    codec2_decode(c2, (short *)(outBytes + (output_block_size * i)), inBytes + (input_block_size * i));

  (*env)->ReleaseByteArrayElements(env, in, inBytes, 0);
  (*env)->ReleaseByteArrayElements(env, out, outBytes, 0);

  return output_block_size * block_count;
}
