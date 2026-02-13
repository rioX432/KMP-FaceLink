#import "CubismBridge.h"

#ifdef LIVE2D_AVAILABLE

#include <string>
#include <vector>

// CubismNativeFramework headers
#import <CubismFramework.hpp>
#import <Model/CubismUserModel.hpp>
#import <Model/CubismMoc.hpp>
#import <CubismModelSettingJson.hpp>
#import <ICubismModelSetting.hpp>
#import <Id/CubismIdManager.hpp>
#import <Physics/CubismPhysics.hpp>
#import <Rendering/Metal/CubismRenderer_Metal.hpp>
#import <Math/CubismMatrix44.hpp>
#import <CubismDefaultParameterId.hpp>
#import <Rendering/Metal/CubismRenderingInstanceSingleton_Metal.h>

using namespace Live2D::Cubism::Framework;
using namespace Live2D::Cubism::Framework::Rendering;

// Simple allocator for Cubism Framework
class BridgeAllocator : public Csm::ICubismAllocator {
public:
    void* Allocate(const Csm::csmSizeType size) override {
        return malloc(size);
    }
    void Deallocate(void* addr) override {
        free(addr);
    }
    void* AllocateAligned(const Csm::csmSizeType size, const Csm::csmUint32 alignment) override {
        void* ptr = nullptr;
        posix_memalign(&ptr, alignment, size);
        return ptr;
    }
    void DeallocateAligned(void* addr) override {
        free(addr);
    }
};

// Internal C++ model class
class BridgeModel : public CubismUserModel {
public:
    void Setup(const std::string& directory, const std::string& fileName,
               id<MTLDevice> device, id<MTLCommandQueue> commandQueue) {
        _directory = directory;

        // Load model3.json
        std::string settingsPath = directory + "/" + fileName;
        auto settingsData = LoadFile(settingsPath);
        if (settingsData.empty()) return;

        auto setting = new CubismModelSettingJson(
            reinterpret_cast<const Csm::csmByte*>(settingsData.data()),
            static_cast<Csm::csmSizeInt>(settingsData.size())
        );

        // Load .moc3
        const char* mocFileName = setting->GetModelFileName();
        if (mocFileName) {
            auto mocData = LoadFile(directory + "/" + mocFileName);
            if (!mocData.empty()) {
                LoadModel(
                    reinterpret_cast<const Csm::csmByte*>(mocData.data()),
                    static_cast<Csm::csmSizeInt>(mocData.size())
                );
            }
        }

        // Load physics
        const char* physicsFileName = setting->GetPhysicsFileName();
        if (physicsFileName && strlen(physicsFileName) > 0) {
            auto physicsData = LoadFile(directory + "/" + physicsFileName);
            if (!physicsData.empty()) {
                LoadPhysics(
                    reinterpret_cast<const Csm::csmByte*>(physicsData.data()),
                    static_cast<Csm::csmSizeInt>(physicsData.size())
                );
            }
        }

        // Load pose
        const char* poseFileName = setting->GetPoseFileName();
        if (poseFileName && strlen(poseFileName) > 0) {
            auto poseData = LoadFile(directory + "/" + poseFileName);
            if (!poseData.empty()) {
                LoadPose(
                    reinterpret_cast<const Csm::csmByte*>(poseData.data()),
                    static_cast<Csm::csmSizeInt>(poseData.size())
                );
            }
        }

        // Create renderer
        CreateRenderer();
        auto* renderer = GetRenderer<CubismRenderer_Metal>();
        renderer->Initialize(_model);
        renderer->IsPremultipliedAlpha(true);

        // Load textures
        Csm::csmInt32 textureCount = setting->GetTextureCount();
        for (Csm::csmInt32 i = 0; i < textureCount; i++) {
            const char* textureFile = setting->GetTextureFileName(i);
            if (!textureFile) continue;

            // GetTextureFileName returns the full relative path (e.g. "Hiyori.2048/texture_00.png")
            std::string texturePath = directory + "/" + textureFile;

            id<MTLTexture> texture = LoadTexture(texturePath, device, commandQueue);
            if (texture) {
                renderer->BindTexture(i, texture);
            }
        }

        delete setting;
        _isLoaded = true;
    }

    void SetParameterById(const std::string& paramId, float value) {
        if (!_model) return;
        auto handle = CubismFramework::GetIdManager()->GetId(paramId.c_str());
        _model->SetParameterValue(handle, value);
    }

    void Update(float deltaTime) {
        if (!_model) return;
        if (_physics) {
            _physics->Evaluate(_model, deltaTime);
        }
        if (_pose) {
            _pose->UpdateParameters(_model, deltaTime);
        }
        _model->Update();
    }

    void Draw(id<MTLCommandBuffer> commandBuffer,
              MTLRenderPassDescriptor* rpd,
              CGSize size,
              id<MTLDevice> device) {
        if (!_model || !_isLoaded) return;

        auto* renderer = GetRenderer<CubismRenderer_Metal>();
        CubismRenderer_Metal::StartFrame(device, commandBuffer, rpd);

        CubismMatrix44 projection;
        float aspect = (float)size.width / (float)size.height;
        if (aspect > 1.0f) {
            projection.Scale(1.0f / aspect, 1.0f);
        } else {
            projection.Scale(1.0f, aspect);
        }

        CubismMatrix44 modelMat;
        CubismMatrix44::Multiply(
            _modelMatrix->GetArray(),
            projection.GetArray(),
            projection.GetArray()
        );

        renderer->SetMvpMatrix(&projection);
        renderer->DrawModel();
    }

    bool IsLoaded() const { return _isLoaded; }

private:
    std::string _directory;
    bool _isLoaded = false;

    std::vector<char> LoadFile(const std::string& path) {
        FILE* fp = fopen(path.c_str(), "rb");
        if (!fp) return {};
        fseek(fp, 0, SEEK_END);
        long size = ftell(fp);
        fseek(fp, 0, SEEK_SET);
        std::vector<char> data(size);
        fread(data.data(), 1, size, fp);
        fclose(fp);
        return data;
    }

    id<MTLTexture> LoadTexture(const std::string& path,
                                id<MTLDevice> device,
                                id<MTLCommandQueue> commandQueue) {
        NSString* nsPath = [NSString stringWithUTF8String:path.c_str()];
        NSData* imageData = [NSData dataWithContentsOfFile:nsPath];
        if (!imageData) return nil;

        CGDataProviderRef provider = CGDataProviderCreateWithCFData((__bridge CFDataRef)imageData);
        CGImageRef cgImage = CGImageCreateWithPNGDataProvider(provider, nullptr, true, kCGRenderingIntentDefault);
        CGDataProviderRelease(provider);
        if (!cgImage) return nil;

        size_t width = CGImageGetWidth(cgImage);
        size_t height = CGImageGetHeight(cgImage);

        // Decode to RGBA
        std::vector<uint8_t> rgba(width * height * 4);
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        CGContextRef ctx = CGBitmapContextCreate(
            rgba.data(), width, height, 8, width * 4,
            colorSpace,
            kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big
        );
        CGContextDrawImage(ctx, CGRectMake(0, 0, width, height), cgImage);
        CGContextRelease(ctx);
        CGColorSpaceRelease(colorSpace);
        CGImageRelease(cgImage);

        // Create Metal texture
        MTLTextureDescriptor* desc =
            [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm
                                                              width:width
                                                             height:height
                                                          mipmapped:YES];
        id<MTLTexture> texture = [device newTextureWithDescriptor:desc];
        [texture replaceRegion:MTLRegionMake2D(0, 0, width, height)
                   mipmapLevel:0
                     withBytes:rgba.data()
                   bytesPerRow:width * 4];

        // Generate mipmaps
        id<MTLCommandBuffer> cmdBuf = [commandQueue commandBuffer];
        id<MTLBlitCommandEncoder> blitEncoder = [cmdBuf blitCommandEncoder];
        [blitEncoder generateMipmapsForTexture:texture];
        [blitEncoder endEncoding];
        [cmdBuf commit];
        [cmdBuf waitUntilCompleted];

        return texture;
    }
};

// MARK: - CubismBridge Implementation (SDK available)

@implementation CubismBridge {
    BridgeModel* _model;
    id<MTLDevice> _device;
    id<MTLCommandQueue> _commandQueue;
}

static BridgeAllocator s_allocator;
static bool s_frameworkInitialized = false;

+ (BOOL)sdkAvailable {
    return YES;
}

- (instancetype)initWithDevice:(id<MTLDevice>)device {
    self = [super init];
    if (self) {
        _device = device;
        _commandQueue = [device newCommandQueue];
        _isModelLoaded = NO;

        if (!s_frameworkInitialized) {
            CubismFramework::Option option;
            option.LoggingLevel = CubismFramework::Option::LogLevel_Warning;
            CubismFramework::StartUp(&s_allocator, &option);
            CubismFramework::Initialize();
            s_frameworkInitialized = true;
        }

        // Register Metal device with the rendering singleton (required for shader compilation)
        CubismRenderingInstanceSingleton_Metal *single =
            [CubismRenderingInstanceSingleton_Metal sharedManager];
        [single setMTLDevice:_device];
    }
    return self;
}

- (BOOL)loadModelFromDirectory:(NSString *)directory
                 modelFileName:(NSString *)fileName {
    _model = new BridgeModel();
    _model->Setup(
        [directory UTF8String],
        [fileName UTF8String],
        _device,
        _commandQueue
    );
    _isModelLoaded = _model->IsLoaded();
    return _isModelLoaded;
}

- (void)setParameterValue:(NSString *)paramId value:(float)value {
    if (_model) {
        _model->SetParameterById([paramId UTF8String], value);
    }
}

- (void)updateWithDeltaTime:(float)deltaTime {
    if (_model) {
        _model->Update(deltaTime);
    }
}

- (void)drawWithCommandBuffer:(id<MTLCommandBuffer>)commandBuffer
         renderPassDescriptor:(MTLRenderPassDescriptor *)rpd
                 drawableSize:(CGSize)size {
    if (_model) {
        _model->Draw(commandBuffer, rpd, size, _device);
    }
}

- (void)releaseResources {
    if (_model) {
        delete _model;
        _model = nullptr;
    }
    _isModelLoaded = NO;
}

- (void)dealloc {
    [self releaseResources];
}

@end

#else // !LIVE2D_AVAILABLE

// MARK: - CubismBridge Stub Implementation (SDK not available)

@implementation CubismBridge

+ (BOOL)sdkAvailable {
    return NO;
}

- (instancetype)initWithDevice:(id<MTLDevice>)device {
    self = [super init];
    if (self) {
        _isModelLoaded = NO;
    }
    return self;
}

- (BOOL)loadModelFromDirectory:(NSString *)directory
                 modelFileName:(NSString *)fileName {
    return NO;
}

- (void)setParameterValue:(NSString *)paramId value:(float)value {}
- (void)updateWithDeltaTime:(float)deltaTime {}

- (void)drawWithCommandBuffer:(id<MTLCommandBuffer>)commandBuffer
         renderPassDescriptor:(MTLRenderPassDescriptor *)rpd
                 drawableSize:(CGSize)size {}

- (void)releaseResources {}

@end

#endif // LIVE2D_AVAILABLE
