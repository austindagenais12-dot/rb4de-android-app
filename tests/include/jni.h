/* Minimal host-only JNI surface for exercising the actual socket JNI code. */
#pragma once

typedef int jint;
typedef unsigned char jboolean;
typedef void* jclass;
typedef void* jstring;
typedef void* jbyteArray;
typedef signed char jbyte;
typedef int jsize;
#define JNIEXPORT
#define JNICALL

struct JNINativeInterface_;
typedef const struct JNINativeInterface_* JNIEnv;
struct JNINativeInterface_ {
    const char* (*GetStringUTFChars)(JNIEnv*, jstring, jboolean*);
    void (*ReleaseStringUTFChars)(JNIEnv*, jstring, const char*);
    jstring (*NewStringUTF)(JNIEnv*, const char*);
    jsize (*GetArrayLength)(JNIEnv*, jbyteArray);
    void (*SetByteArrayRegion)(JNIEnv*, jbyteArray, jsize, jsize, const jbyte*);
};
