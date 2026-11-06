/* Minimal host-only JNI surface for exercising the actual socket JNI code. */
#pragma once

typedef int jint;
typedef unsigned char jboolean;
typedef void* jclass;
typedef void* jstring;
#define JNIEXPORT
#define JNICALL

struct JNINativeInterface_;
typedef const struct JNINativeInterface_* JNIEnv;
struct JNINativeInterface_ {
    const char* (*GetStringUTFChars)(JNIEnv*, jstring, jboolean*);
    void (*ReleaseStringUTFChars)(JNIEnv*, jstring, const char*);
    jstring (*NewStringUTF)(JNIEnv*, const char*);
};
