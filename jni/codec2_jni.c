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

JNIEXPORT jint JNICALL Java_org_servalproject_audio_Codec2_encode(JNIEnv *env, jobject this, jlong ptr, jbyteArray in, jbyteArray out)
{
  struct CODEC2 *c2=(struct CODEC2*)ptr;
  if ((*env)->GetArrayLength(env, in) != codec2_samples_per_frame(c2)*2)
    return -1;
  if ((*env)->GetArrayLength(env, out) < (codec2_bits_per_frame(c2)+7)/8)
    return -2;
  jbyte * inBytes = (*env)->GetByteArrayElements(env, in, (void*)0);
  jbyte * outBytes = (*env)->GetByteArrayElements(env, out, (void*)0);
  codec2_encode(c2, outBytes, (short *)inBytes);
  (*env)->ReleaseByteArrayElements(env, in, inBytes, 0);
  (*env)->ReleaseByteArrayElements(env, out, outBytes, 0);
  return (codec2_bits_per_frame(c2)+7)/8;
}

JNIEXPORT jint JNICALL Java_org_servalproject_audio_Codec2_decode(JNIEnv *env, jobject this, jlong ptr, jbyteArray in, jbyteArray out)
{
  struct CODEC2 *c2=(struct CODEC2*)ptr;
  if ((*env)->GetArrayLength(env, out) < codec2_samples_per_frame(c2)*2)
    return -1;
  if ((*env)->GetArrayLength(env, in) < (codec2_bits_per_frame(c2)+7)/8)
    return -2;
  jbyte * inBytes = (*env)->GetByteArrayElements(env, in, (void*)0);
  jbyte * outBytes = (*env)->GetByteArrayElements(env, out, (void*)0);
  codec2_decode(c2, (short *)outBytes, inBytes);
  (*env)->ReleaseByteArrayElements(env, in, inBytes, 0);
  (*env)->ReleaseByteArrayElements(env, out, outBytes, 0);
  return codec2_samples_per_frame(c2)*2;
}
