#include <pthread.h>
#include "shader_inspector.h"
#include "spirv.h"

#define OP(n,op) (((uint32_t)(n)<<16)|(op))
/* Vertex shader: location 0 float4 copied to built-in Position. */
static const uint32_t vertex_code[]={
    SpvMagicNumber,0x00010000,0,12,0,
    OP(2,SpvOpCapability),SpvCapabilityShader,
    OP(3,SpvOpMemoryModel),SpvAddressingModelLogical,SpvMemoryModelGLSL450,
    OP(7,SpvOpEntryPoint),SpvExecutionModelVertex,9,0x6e69616d,0,7,8,
    OP(4,SpvOpDecorate),7,SpvDecorationLocation,0,
    OP(4,SpvOpDecorate),8,SpvDecorationBuiltIn,SpvBuiltInPosition,
    OP(2,SpvOpTypeVoid),1,
    OP(3,SpvOpTypeFunction),2,1,
    OP(3,SpvOpTypeFloat),3,32,
    OP(4,SpvOpTypeVector),4,3,4,
    OP(4,SpvOpTypePointer),5,SpvStorageClassInput,4,
    OP(4,SpvOpTypePointer),6,SpvStorageClassOutput,4,
    OP(4,SpvOpVariable),5,7,SpvStorageClassInput,
    OP(4,SpvOpVariable),6,8,SpvStorageClassOutput,
    OP(5,SpvOpFunction),1,9,0,2,
    OP(2,SpvOpLabel),10,
    OP(4,SpvOpLoad),4,11,7,
    OP(3,SpvOpStore),8,11,
    OP(1,SpvOpReturn),OP(1,SpvOpFunctionEnd)
};
static VkFormat unsupported=VK_FORMAT_UNDEFINED;
static VkResult creation_result=VK_SUCCESS;
static unsigned create_calls;
static uint32_t driver_code[256];
static size_t driver_code_size;
static void properties(VkPhysicalDevice d,VkPhysicalDeviceProperties* p) { (void)d;memset(p,0,sizeof(*p));strcpy(p->deviceName,"Turnip Adreno (TM) 750"); }
static void format_properties(VkPhysicalDevice d,VkFormat f,VkFormatProperties* p) { (void)d;memset(p,0,sizeof(*p));if(f!=unsupported)p->bufferFeatures=VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT; }
static VkResult create_module(VkDevice d,const VkShaderModuleCreateInfo* ci,const VkAllocationCallbacks* a,VkShaderModule* m) { (void)d;(void)a;++create_calls;assert(ci->codeSize<=sizeof(driver_code));driver_code_size=ci->codeSize;memcpy(driver_code,ci->pCode,ci->codeSize);*m=creation_result==VK_SUCCESS?(VkShaderModule)(uintptr_t)0x1234:VK_NULL_HANDLE;return creation_result; }
ShaderTestVulkanWrapper vulkanWrapper={properties,format_properties,create_module};

static ShaderInspector* make_inspector(const char* engine,bool clip) { VkContext c={.engineName=(char*)engine};VkPhysicalDeviceFeatures f={.shaderClipDistance=clip};return ShaderInspector_create(&c,VK_NULL_HANDLE,&f); }
static VkFormat pipeline(ShaderInspector* i,ShaderModule* m,VkFormat format) {
    VkVertexInputAttributeDescription attr={.format=format};
    VkPipelineVertexInputStateCreateInfo input={.sType=VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,.vertexAttributeDescriptionCount=1,.pVertexAttributeDescriptions=&attr};
    VkPipelineShaderStageCreateInfo stage={.sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,.stage=VK_SHADER_STAGE_VERTEX_BIT,.module=(VkShaderModule)m,.pName="main"};
    assert(ShaderInspector_inspectShaderStages(i,VK_NULL_HANDLE,&stage,1,&input)==VK_SUCCESS);
    assert(stage.module==(VkShaderModule)(uintptr_t)0x1234);
    return attr.format;
}
typedef struct { ShaderInspector* i;ShaderModule* m; } Worker;
static void* reuse(void* arg) { Worker* w=arg;for(int j=0;j<1000;j++)assert(pipeline(w->i,w->m,VK_FORMAT_R8G8B8A8_USCALED)==VK_FORMAT_R8G8B8A8_USCALED);return NULL; }
int main(int argc,char** argv) {
    bool baseline=argc>1 && strcmp(argv[1],"--baseline")==0;
    ShaderInspector* i=make_inspector("shadPS4 Vulkan",true);
    ShaderModule* m=NULL;
    assert(ShaderInspector_createModule(i,VK_NULL_HANDLE,vertex_code,sizeof(vertex_code),&m)==VK_SUCCESS);
    VkFormat first=pipeline(i,m,VK_FORMAT_R8G8B8A8_USCALED);
    VkFormat second=pipeline(i,m,VK_FORMAT_R8G8B8A8_USCALED);
    printf("first format=%d repeated format=%d requested=%d native shader bytes unchanged=%d\n",first,second,VK_FORMAT_R8G8B8A8_USCALED,driver_code_size==sizeof(vertex_code)&&memcmp(driver_code,vertex_code,sizeof(vertex_code))==0);
    fflush(stdout);
    if(baseline) { assert(first==second);return 0; }
    assert(first==VK_FORMAT_R8G8B8A8_USCALED && second==first);
    assert(driver_code_size==sizeof(vertex_code) && memcmp(driver_code,vertex_code,sizeof(vertex_code))==0);
    assert(create_calls==1);
    Worker w={i,m};pthread_t threads[8];for(int j=0;j<8;j++)assert(pthread_create(&threads[j],NULL,reuse,&w)==0);for(int j=0;j<8;j++)pthread_join(threads[j],NULL);
    assert(create_calls==1 && m->code==NULL);free(m);free(i);
    for(int case_id=0;case_id<3;case_id++) {
        unsupported=case_id==0?VK_FORMAT_R16G16B16A16_SSCALED:VK_FORMAT_UNDEFINED;
        i=make_inspector(case_id==1?"DXVK":"shadPS4 Vulkan",case_id!=2);m=NULL;
        unsigned before=create_calls;
        assert(ShaderInspector_createModule(i,VK_NULL_HANDLE,vertex_code,sizeof(vertex_code),&m)==VK_SUCCESS);
        assert(create_calls==before && m->code!=NULL);free(m->code);free(m);free(i);
    }
    unsupported=VK_FORMAT_UNDEFINED;creation_result=VK_ERROR_OUT_OF_HOST_MEMORY;
    i=make_inspector("shadPS4 Vulkan",true);m=NULL;
    assert(ShaderInspector_createModule(i,VK_NULL_HANDLE,vertex_code,sizeof(vertex_code),&m)==VK_ERROR_OUT_OF_HOST_MEMORY);
    assert(m==NULL);free(i);
    puts("PASS: native shader bytes/formats, 8,000 concurrent reuses, capability fallback and creation failure");
}
