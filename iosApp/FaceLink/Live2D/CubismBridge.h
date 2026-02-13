#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#import <MetalKit/MetalKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Obj-C bridge wrapping CubismNativeFramework C++ API for Swift access.
/// Handles model loading, parameter updates, and Metal rendering.
@interface CubismBridge : NSObject

- (instancetype)initWithDevice:(id<MTLDevice>)device;

/// Loads a Live2D model from a bundle directory.
/// @param directory Full path to the model directory
/// @param fileName Model settings file name (e.g. "Hiyori.model3.json")
/// @return YES if model loaded successfully
- (BOOL)loadModelFromDirectory:(NSString *)directory
                 modelFileName:(NSString *)fileName;

/// Sets a single parameter value on the loaded model.
/// @param paramId Live2D parameter ID (e.g. "ParamAngleX")
/// @param value Parameter value
- (void)setParameterValue:(NSString *)paramId value:(float)value;

/// Updates the model state (physics, pose, etc.) with the given delta time.
/// @param deltaTime Time since last update in seconds
- (void)updateWithDeltaTime:(float)deltaTime;

/// Draws the model to the given render pass.
/// @param commandBuffer Metal command buffer for this frame
/// @param rpd Render pass descriptor for the output texture
/// @param size Drawable size for projection calculation
- (void)drawWithCommandBuffer:(id<MTLCommandBuffer>)commandBuffer
         renderPassDescriptor:(MTLRenderPassDescriptor *)rpd
                 drawableSize:(CGSize)size;

/// Whether a model is currently loaded and ready to render.
@property (nonatomic, readonly) BOOL isModelLoaded;

/// Whether the Live2D Cubism SDK is available at compile time.
@property (class, nonatomic, readonly) BOOL sdkAvailable;

/// Releases all Cubism resources.
- (void)releaseResources;

@end

NS_ASSUME_NONNULL_END
