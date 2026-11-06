#ifndef TEST_AHB_H
#define TEST_AHB_H
#include <stdint.h>
typedef struct AHardwareBuffer { int index; } AHardwareBuffer;
typedef struct { uint32_t width, height, layers, format; uint64_t usage; uint32_t stride, rfu0; uint64_t rfu1; } AHardwareBuffer_Desc;
void AHardwareBuffer_describe(const AHardwareBuffer*, AHardwareBuffer_Desc*);
#endif
