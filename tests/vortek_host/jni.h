#ifndef HOST_JNI_H
#define HOST_JNI_H
#include <stdint.h>
typedef int jint;
typedef int64_t jlong;
typedef unsigned char jboolean;
typedef void* jclass;
typedef void* jobject;
typedef void* jmethodID;
struct JNIInvokeInterface_;
typedef const struct JNIInvokeInterface_* JavaVM;
struct JNIInvokeInterface_ { jint (*DetachCurrentThread)(JavaVM*); };
struct JNINativeInterface_;
typedef const struct JNINativeInterface_* JNIEnv;
struct JNINativeInterface_ {
 jclass (*GetObjectClass)(JNIEnv*,jobject);
 jmethodID (*GetMethodID)(JNIEnv*,jclass,const char*,const char*);
 void (*DeleteLocalRef)(JNIEnv*,jobject);
 jint (*CallIntMethod)(JNIEnv*,jobject,jmethodID,...);
 jlong (*CallLongMethod)(JNIEnv*,jobject,jmethodID,...);
 void (*CallVoidMethod)(JNIEnv*,jobject,jmethodID,...);
};
#endif
