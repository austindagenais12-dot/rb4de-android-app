#ifndef RB4DX_SHADER_HOST_H
#define RB4DX_SHADER_HOST_H
#define VORTEK_H
#define VULKAN_HELPER_H
#define VK_NO_PROTOTYPES
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdarg.h>
#include <vulkan/vulkan.h>
#define MEMFREE(x) do { free(x); (x)=NULL; } while (0)
#define ARRAY_SIZE(x) (sizeof(x)/sizeof((x)[0]))
#define MAX(a,b) ((a)>(b)?(a):(b))
#define ENSURE_ARRAY_CAPACITY(n,c,p,s) do { if ((n)>(c)) { (c)=(n)+16; (p)=realloc((p),(size_t)(c)*(s)); assert(p); } } while(0)
typedef struct { void** elements; int size,capacity; } ArrayList;
typedef struct { uint32_t* values; int size,capacity; } IntArray;
typedef struct { char* engineName; } VkContext;
typedef struct {
    PFN_vkGetPhysicalDeviceProperties vkGetPhysicalDeviceProperties;
    PFN_vkGetPhysicalDeviceFormatProperties vkGetPhysicalDeviceFormatProperties;
    PFN_vkCreateShaderModule vkCreateShaderModule;
} ShaderTestVulkanWrapper;
extern ShaderTestVulkanWrapper vulkanWrapper;
static inline void* memdup(const void* p,size_t n) { void* r=malloc(n); assert(r); memcpy(r,p,n); return r; }
static inline void* memmerge(const void* add,size_t an,const void* src,size_t sn,size_t at,size_t* n) { assert(at<=sn); *n=an+sn; char* r=malloc(*n); assert(r); memcpy(r,src,at); memcpy(r+at,add,an); memcpy(r+at+an,(const char*)src+at,sn-at); return r; }
static inline void* memdel(const void* src,size_t sn,size_t at,size_t count,size_t* n) { assert(at+count<=sn); *n=sn-count; char* r=malloc(*n); assert(r); memcpy(r,src,at); memcpy(r+at,(const char*)src+at+count,sn-at-count); return r; }
static inline void ArrayList_add(ArrayList* a,void* p) { ENSURE_ARRAY_CAPACITY(a->size+1,a->capacity,a->elements,sizeof(void*)); a->elements[a->size++]=p; }
static inline void ArrayList_removeAt(ArrayList* a,int i) { memmove(a->elements+i,a->elements+i+1,(size_t)(--a->size-i)*sizeof(void*)); }
static inline void ArrayList_free(ArrayList* a,bool items) { if(items)for(int i=0;i<a->size;i++)free(a->elements[i]); free(a->elements); memset(a,0,sizeof(*a)); }
static inline void IntArray_add(IntArray* a,uint32_t v) { ENSURE_ARRAY_CAPACITY(a->size+1,a->capacity,a->values,sizeof(uint32_t)); a->values[a->size++]=v; }
static inline void IntArray_addAll(IntArray* a,int n,...) { va_list v;va_start(v,n);for(int i=0;i<n;i++)IntArray_add(a,va_arg(v,uint32_t));va_end(v); }
static inline void IntArray_clear(IntArray* a) { free(a->values); memset(a,0,sizeof(*a)); }
static inline int compare_int(const void* a,const void* b) { uint32_t x=*(const uint32_t*)a,y=*(const uint32_t*)b;return (x>y)-(x<y); }
static inline void IntArray_sort(IntArray* a) { qsort(a->values,a->size,sizeof(uint32_t),compare_int); }
#endif
