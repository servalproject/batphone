#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <cutils/properties.h>
#include <sys/system_properties.h>

#include "android_tether_system_NativeTask.h"

JNIEXPORT jstring JNICALL Java_android_tether_system_NativeTask_getProp
  (JNIEnv *env, jclass class, jstring name)
{
  const char *nameString;
  nameString = (*env)->GetStringUTFChars(env, name, 0);

  char value[PROPERTY_VALUE_MAX];
  char *default_value;
  jstring jstrOutput;
  
  default_value = "undefined";
  property_get(nameString, value, default_value);

  jstrOutput = (*env)->NewStringUTF(env, value);

  (*env)->ReleaseStringUTFChars(env, name, nameString);  

  return jstrOutput;
}

JNIEXPORT jint JNICALL Java_android_tether_system_NativeTask_runCommand
  (JNIEnv *env, jclass class, jstring command)
{
  const char *commandString;
  commandString = (*env)->GetStringUTFChars(env, command, 0);
  int exitcode = system(commandString); 
  (*env)->ReleaseStringUTFChars(env, command, commandString);  
  return (jint)exitcode;
}
