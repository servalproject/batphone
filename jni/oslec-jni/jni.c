/*
Serval Distributed Numbering Architecture (DNA)
Copyright (C) 2010 Paul Gardner-Stephen
 
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
 
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
 
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

#include <jni.h>
#include <telephony.h>
#include <echo.h>
#include <android/log.h> 

// TODO if building for a 64bit jvm...
#define JVM_PTR jint

/*
 * Class:     org_servalproject_audio_Oslec
 * Method:    echoCanInit
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_org_servalproject_audio_Oslec_echoCanInit
(JNIEnv *, jclass, jint, jint);

/*
 * Class:     org_servalproject_audio_Oslec
 * Method:    echoCanUpdate
 * Signature: (I[BI[BI[BII)I
 */
JNIEXPORT jint JNICALL Java_org_servalproject_audio_Oslec_echoCanUpdate
(JNIEnv *, jclass, jint, jbyteArray, jint, jbyteArray, jint, jbyteArray, jint, jint);

/*
 * Class:     org_servalproject_audio_Oslec
 * Method:    echoCanFree
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_org_servalproject_audio_Oslec_echoCanFree
(JNIEnv *, jclass, jint);



int dump(char *name, unsigned char *addr, size_t len)
{
	char buf[100], *p;
	size_t i;
	__android_log_print(ANDROID_LOG_DEBUG, "oslec", "Dump of %s", name);
	for(i = 0; i < len; i += 16) {
		p=buf;
		p+=sprintf(p, "  %04x :", i);
		int j;
		for (j = 0; j < 16 && i + j < len; j++)
			p+=sprintf(p, " %02x", addr[i + j]);
		for (; j < 16; j++){
			strcat(p,"   ");p+=strlen(p);
		}
		strcat(p,"   ");p+=strlen(p);
		for (j = 0; j < 16 && i + j < len; j++)
			p+=sprintf(p, "%c", addr[i+j] >= ' ' && addr[i+j] < 0x7f ? addr[i+j] : '.');
		__android_log_print(ANDROID_LOG_DEBUG, "oslec", "%s", buf);
	}
	
	return 0;
}


JNIEXPORT JVM_PTR JNICALL
Java_org_servalproject_audio_Oslec_echoCanInit(JNIEnv *env, jclass class, jint len, jint adaption_mode) {
	return (JVM_PTR)echo_can_init(len, adaption_mode);
}

JNIEXPORT jint JNICALL
Java_org_servalproject_audio_Oslec_echoCanUpdate(JNIEnv *env, jclass class, JVM_PTR echo_can_state, 
																	jbyteArray playArray, jint playOffset,
																	jbyteArray recordArray, jint recordOffset,
																	jbyteArray destArray, jint destOffset,
																	jint len) {
	
	echo_can_state_t *state = (echo_can_state_t*)echo_can_state;
	int16_t *splay = NULL;
	int16_t *srecord = NULL;
	int16_t *sdest = NULL;
	jbyte *play = NULL;
	jbyte *record = NULL;
	jbyte *dest = NULL;
	int i;
	if (len<0)
		return -1;
	
	// allow either the record or playback buffers to be NULL pointers
	// then just pass silence into the echo canceller
	
	if (playArray){
		jsize playLen = (*env)->GetArrayLength(env, playArray);
		if (playOffset + len > playLen || playOffset<0)
			return -1;
	}
	
	if (recordArray){
		jsize recordLen = (*env)->GetArrayLength(env, recordArray);
		if (recordOffset<0 || recordOffset + len > recordLen)
			return -1;
	}
	
	if (destArray){
		jsize arrayLen = (*env)->GetArrayLength(env, destArray);
		if (destOffset < 0 || destOffset + len > arrayLen)
			return -1;
	}
	
	
	if (playArray){
		play = (*env)->GetByteArrayElements(env, playArray, (jboolean *)NULL);
		splay = (int16_t *)(play+playOffset);
	}
	
	if (recordArray){
		record = (*env)->GetByteArrayElements(env, recordArray, (jboolean *)NULL);
		srecord = (int16_t *)(record+recordOffset);
	}
	
	if (destArray){
		dest = (*env)->GetByteArrayElements(env, destArray, (jboolean *)NULL);
		sdest = (int16_t *)(dest+destOffset);
	}
	
	int dumpBuffs=0;
	
	for (i=0;i<(len/2);i++){
		int16_t x = echo_can_update(state, recordArray?srecord[i]:0, playArray?splay[i]:0);
		if (destArray)
			sdest[i] =x;
		
		if (playArray && splay[i]!=x)
			dumpBuffs=1;
	}
	
#if 0
	if (dumpBuffs){
		if (playArray)
			dump("Play", (unsigned char *)splay, len);
		if (destArray)
			dump("Destination", (unsigned char *)sdest, len);
	}else{
		__android_log_print(ANDROID_LOG_DEBUG, "oslec", "Nothing changed");
	}
#endif
	
	if (playArray)
		(*env)->ReleaseByteArrayElements(env, playArray, play, 0);
	if (recordArray)
		(*env)->ReleaseByteArrayElements(env, recordArray, record, 0);
	if (destArray)
		(*env)->ReleaseByteArrayElements(env, destArray, dest, 0);
	
	return 0;
}

JNIEXPORT void JNICALL
Java_org_servalproject_audio_Oslec_echoCanFree(JNIEnv *env, jclass class, JVM_PTR echo_can_state) {
	echo_can_state_t *state = (echo_can_state_t*)echo_can_state;
	echo_can_free(state);
}

