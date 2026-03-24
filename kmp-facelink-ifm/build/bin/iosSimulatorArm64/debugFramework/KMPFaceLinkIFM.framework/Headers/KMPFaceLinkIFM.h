#import <Foundation/NSArray.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSError.h>
#import <Foundation/NSObject.h>
#import <Foundation/NSSet.h>
#import <Foundation/NSString.h>
#import <Foundation/NSValue.h>

@class KMPFLIFMDataVersion, KMPFLIFMIfmConfig, KMPFLIFMIfmConfigCompanion, KMPFLIFMKmp_facelinkBlendShape, KMPFLIFMKmp_facelinkBlendShapeCompanion, KMPFLIFMKmp_facelinkGazeData, KMPFLIFMKmp_facelinkHeadTransform, KMPFLIFMKotlinArray<T>, KMPFLIFMKotlinEnum<E>, KMPFLIFMKotlinEnumCompanion, KMPFLIFMKotlinException, KMPFLIFMKotlinFloatArray, KMPFLIFMKotlinFloatIterator, KMPFLIFMKotlinIllegalStateException, KMPFLIFMKotlinRuntimeException, KMPFLIFMKotlinThrowable;

@protocol KMPFLIFMKotlinComparable, KMPFLIFMKotlinCoroutineContext, KMPFLIFMKotlinCoroutineContextElement, KMPFLIFMKotlinCoroutineContextKey, KMPFLIFMKotlinIterator, KMPFLIFMKotlinx_coroutines_coreCoroutineScope, KMPFLIFMKotlinx_coroutines_coreFlow, KMPFLIFMKotlinx_coroutines_coreFlowCollector;

NS_ASSUME_NONNULL_BEGIN
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunknown-warning-option"
#pragma clang diagnostic ignored "-Wincompatible-property-type"
#pragma clang diagnostic ignored "-Wnullability"

#pragma push_macro("_Nullable_result")
#if !__has_feature(nullability_nullable_result)
#undef _Nullable_result
#define _Nullable_result _Nullable
#endif

__attribute__((swift_name("KotlinBase")))
@interface KMPFLIFMBase : NSObject
- (instancetype)init __attribute__((unavailable));
+ (instancetype)new __attribute__((unavailable));
+ (void)initialize __attribute__((objc_requires_super));
@end

@interface KMPFLIFMBase (KMPFLIFMBaseCopying) <NSCopying>
@end

__attribute__((swift_name("KotlinMutableSet")))
@interface KMPFLIFMMutableSet<ObjectType> : NSMutableSet<ObjectType>
@end

__attribute__((swift_name("KotlinMutableDictionary")))
@interface KMPFLIFMMutableDictionary<KeyType, ObjectType> : NSMutableDictionary<KeyType, ObjectType>
@end

@interface NSError (NSErrorKMPFLIFMKotlinException)
@property (readonly) id _Nullable kotlinException;
@end

__attribute__((swift_name("KotlinNumber")))
@interface KMPFLIFMNumber : NSNumber
- (instancetype)initWithChar:(char)value __attribute__((unavailable));
- (instancetype)initWithUnsignedChar:(unsigned char)value __attribute__((unavailable));
- (instancetype)initWithShort:(short)value __attribute__((unavailable));
- (instancetype)initWithUnsignedShort:(unsigned short)value __attribute__((unavailable));
- (instancetype)initWithInt:(int)value __attribute__((unavailable));
- (instancetype)initWithUnsignedInt:(unsigned int)value __attribute__((unavailable));
- (instancetype)initWithLong:(long)value __attribute__((unavailable));
- (instancetype)initWithUnsignedLong:(unsigned long)value __attribute__((unavailable));
- (instancetype)initWithLongLong:(long long)value __attribute__((unavailable));
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value __attribute__((unavailable));
- (instancetype)initWithFloat:(float)value __attribute__((unavailable));
- (instancetype)initWithDouble:(double)value __attribute__((unavailable));
- (instancetype)initWithBool:(BOOL)value __attribute__((unavailable));
- (instancetype)initWithInteger:(NSInteger)value __attribute__((unavailable));
- (instancetype)initWithUnsignedInteger:(NSUInteger)value __attribute__((unavailable));
+ (instancetype)numberWithChar:(char)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedChar:(unsigned char)value __attribute__((unavailable));
+ (instancetype)numberWithShort:(short)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedShort:(unsigned short)value __attribute__((unavailable));
+ (instancetype)numberWithInt:(int)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedInt:(unsigned int)value __attribute__((unavailable));
+ (instancetype)numberWithLong:(long)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedLong:(unsigned long)value __attribute__((unavailable));
+ (instancetype)numberWithLongLong:(long long)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value __attribute__((unavailable));
+ (instancetype)numberWithFloat:(float)value __attribute__((unavailable));
+ (instancetype)numberWithDouble:(double)value __attribute__((unavailable));
+ (instancetype)numberWithBool:(BOOL)value __attribute__((unavailable));
+ (instancetype)numberWithInteger:(NSInteger)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedInteger:(NSUInteger)value __attribute__((unavailable));
@end

__attribute__((swift_name("KotlinByte")))
@interface KMPFLIFMByte : KMPFLIFMNumber
- (instancetype)initWithChar:(char)value;
+ (instancetype)numberWithChar:(char)value;
@end

__attribute__((swift_name("KotlinUByte")))
@interface KMPFLIFMUByte : KMPFLIFMNumber
- (instancetype)initWithUnsignedChar:(unsigned char)value;
+ (instancetype)numberWithUnsignedChar:(unsigned char)value;
@end

__attribute__((swift_name("KotlinShort")))
@interface KMPFLIFMShort : KMPFLIFMNumber
- (instancetype)initWithShort:(short)value;
+ (instancetype)numberWithShort:(short)value;
@end

__attribute__((swift_name("KotlinUShort")))
@interface KMPFLIFMUShort : KMPFLIFMNumber
- (instancetype)initWithUnsignedShort:(unsigned short)value;
+ (instancetype)numberWithUnsignedShort:(unsigned short)value;
@end

__attribute__((swift_name("KotlinInt")))
@interface KMPFLIFMInt : KMPFLIFMNumber
- (instancetype)initWithInt:(int)value;
+ (instancetype)numberWithInt:(int)value;
@end

__attribute__((swift_name("KotlinUInt")))
@interface KMPFLIFMUInt : KMPFLIFMNumber
- (instancetype)initWithUnsignedInt:(unsigned int)value;
+ (instancetype)numberWithUnsignedInt:(unsigned int)value;
@end

__attribute__((swift_name("KotlinLong")))
@interface KMPFLIFMLong : KMPFLIFMNumber
- (instancetype)initWithLongLong:(long long)value;
+ (instancetype)numberWithLongLong:(long long)value;
@end

__attribute__((swift_name("KotlinULong")))
@interface KMPFLIFMULong : KMPFLIFMNumber
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value;
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value;
@end

__attribute__((swift_name("KotlinFloat")))
@interface KMPFLIFMFloat : KMPFLIFMNumber
- (instancetype)initWithFloat:(float)value;
+ (instancetype)numberWithFloat:(float)value;
@end

__attribute__((swift_name("KotlinDouble")))
@interface KMPFLIFMDouble : KMPFLIFMNumber
- (instancetype)initWithDouble:(double)value;
+ (instancetype)numberWithDouble:(double)value;
@end

__attribute__((swift_name("KotlinBoolean")))
@interface KMPFLIFMBoolean : KMPFLIFMNumber
- (instancetype)initWithBool:(BOOL)value;
+ (instancetype)numberWithBool:(BOOL)value;
@end

__attribute__((swift_name("KotlinComparable")))
@protocol KMPFLIFMKotlinComparable
@required
- (int32_t)compareToOther:(id _Nullable)other __attribute__((swift_name("compareTo(other:)")));
@end

__attribute__((swift_name("KotlinEnum")))
@interface KMPFLIFMKotlinEnum<E> : KMPFLIFMBase <KMPFLIFMKotlinComparable>
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) KMPFLIFMKotlinEnumCompanion *companion __attribute__((swift_name("companion")));
- (int32_t)compareToOther:(E)other __attribute__((swift_name("compareTo(other:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *name __attribute__((swift_name("name")));
@property (readonly) int32_t ordinal __attribute__((swift_name("ordinal")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("DataVersion")))
@interface KMPFLIFMDataVersion : KMPFLIFMKotlinEnum<KMPFLIFMDataVersion *>
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
@property (class, readonly) KMPFLIFMDataVersion *v1 __attribute__((swift_name("v1")));
@property (class, readonly) KMPFLIFMDataVersion *v2 __attribute__((swift_name("v2")));
+ (KMPFLIFMKotlinArray<KMPFLIFMDataVersion *> *)values __attribute__((swift_name("values()")));
@property (class, readonly) NSArray<KMPFLIFMDataVersion *> *entries __attribute__((swift_name("entries")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("IfmConfig")))
@interface KMPFLIFMIfmConfig : KMPFLIFMBase
- (instancetype)initWithHost:(NSString *)host port:(int32_t)port dataVersion:(KMPFLIFMDataVersion *)dataVersion __attribute__((swift_name("init(host:port:dataVersion:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) KMPFLIFMIfmConfigCompanion *companion __attribute__((swift_name("companion")));
- (KMPFLIFMIfmConfig *)doCopyHost:(NSString *)host port:(int32_t)port dataVersion:(KMPFLIFMDataVersion *)dataVersion __attribute__((swift_name("doCopy(host:port:dataVersion:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) KMPFLIFMDataVersion *dataVersion __attribute__((swift_name("dataVersion")));
@property (readonly) NSString *host __attribute__((swift_name("host")));
@property (readonly) int32_t port __attribute__((swift_name("port")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("IfmConfig.Companion")))
@interface KMPFLIFMIfmConfigCompanion : KMPFLIFMBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) KMPFLIFMIfmConfigCompanion *shared __attribute__((swift_name("shared")));
@property (readonly) NSString *DEFAULT_HOST __attribute__((swift_name("DEFAULT_HOST")));
@property (readonly) int32_t DEFAULT_PORT __attribute__((swift_name("DEFAULT_PORT")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("IfmReceiver")))
@interface KMPFLIFMIfmReceiver : KMPFLIFMBase
- (instancetype)initWithPort:(int32_t)port __attribute__((swift_name("init(port:)"))) __attribute__((objc_designated_initializer));
- (void)sendHandshakeDeviceHost:(NSString *)deviceHost devicePort:(int32_t)devicePort __attribute__((swift_name("sendHandshake(deviceHost:devicePort:)")));
- (void)startScope:(id<KMPFLIFMKotlinx_coroutines_coreCoroutineScope>)scope __attribute__((swift_name("start(scope:)")));
- (void)stop __attribute__((swift_name("stop()")));
@property (readonly) id<KMPFLIFMKotlinx_coroutines_coreFlow> trackingData __attribute__((swift_name("trackingData")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("IfmSender")))
@interface KMPFLIFMIfmSender : KMPFLIFMBase
- (instancetype)initWithConfig:(KMPFLIFMIfmConfig *)config __attribute__((swift_name("init(config:)"))) __attribute__((objc_designated_initializer));
- (void)sendFrameBlendShapes:(NSDictionary<KMPFLIFMKmp_facelinkBlendShape *, KMPFLIFMFloat *> *)blendShapes headTransform:(KMPFLIFMKmp_facelinkHeadTransform *)headTransform gazeData:(KMPFLIFMKmp_facelinkGazeData * _Nullable)gazeData __attribute__((swift_name("sendFrame(blendShapes:headTransform:gazeData:)")));
- (void)startTrackingFlow:(id<KMPFLIFMKotlinx_coroutines_coreFlow>)trackingFlow scope:(id<KMPFLIFMKotlinx_coroutines_coreCoroutineScope>)scope __attribute__((swift_name("start(trackingFlow:scope:)")));
- (void)stop __attribute__((swift_name("stop()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("IfmReceiverKt")))
@interface KMPFLIFMIfmReceiverKt : KMPFLIFMBase
@property (class, readonly) NSString *HANDSHAKE_STRING __attribute__((swift_name("HANDSHAKE_STRING")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinEnumCompanion")))
@interface KMPFLIFMKotlinEnumCompanion : KMPFLIFMBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) KMPFLIFMKotlinEnumCompanion *shared __attribute__((swift_name("shared")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinArray")))
@interface KMPFLIFMKotlinArray<T> : KMPFLIFMBase
+ (instancetype)arrayWithSize:(int32_t)size init:(T _Nullable (^)(KMPFLIFMInt *))init __attribute__((swift_name("init(size:init:)")));
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (T _Nullable)getIndex:(int32_t)index __attribute__((swift_name("get(index:)")));
- (id<KMPFLIFMKotlinIterator>)iterator __attribute__((swift_name("iterator()")));
- (void)setIndex:(int32_t)index value:(T _Nullable)value __attribute__((swift_name("set(index:value:)")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end

__attribute__((swift_name("Kotlinx_coroutines_coreCoroutineScope")))
@protocol KMPFLIFMKotlinx_coroutines_coreCoroutineScope
@required
@property (readonly) id<KMPFLIFMKotlinCoroutineContext> coroutineContext __attribute__((swift_name("coroutineContext")));
@end

__attribute__((swift_name("Kotlinx_coroutines_coreFlow")))
@protocol KMPFLIFMKotlinx_coroutines_coreFlow
@required

/**
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)collectCollector:(id<KMPFLIFMKotlinx_coroutines_coreFlowCollector>)collector completionHandler:(void (^)(NSError * _Nullable))completionHandler __attribute__((swift_name("collect(collector:completionHandler:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kmp_facelinkBlendShape")))
@interface KMPFLIFMKmp_facelinkBlendShape : KMPFLIFMKotlinEnum<KMPFLIFMKmp_facelinkBlendShape *>
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
@property (class, readonly, getter=companion) KMPFLIFMKmp_facelinkBlendShapeCompanion *companion __attribute__((swift_name("companion")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeBlinkLeft __attribute__((swift_name("eyeBlinkLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeBlinkRight __attribute__((swift_name("eyeBlinkRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeLookDownLeft __attribute__((swift_name("eyeLookDownLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeLookDownRight __attribute__((swift_name("eyeLookDownRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeLookInLeft __attribute__((swift_name("eyeLookInLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeLookInRight __attribute__((swift_name("eyeLookInRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeLookOutLeft __attribute__((swift_name("eyeLookOutLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeLookOutRight __attribute__((swift_name("eyeLookOutRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeLookUpLeft __attribute__((swift_name("eyeLookUpLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeLookUpRight __attribute__((swift_name("eyeLookUpRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeSquintLeft __attribute__((swift_name("eyeSquintLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeSquintRight __attribute__((swift_name("eyeSquintRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeWideLeft __attribute__((swift_name("eyeWideLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *eyeWideRight __attribute__((swift_name("eyeWideRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *jawForward __attribute__((swift_name("jawForward")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *jawLeft __attribute__((swift_name("jawLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *jawOpen __attribute__((swift_name("jawOpen")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *jawRight __attribute__((swift_name("jawRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthClose __attribute__((swift_name("mouthClose")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthDimpleLeft __attribute__((swift_name("mouthDimpleLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthDimpleRight __attribute__((swift_name("mouthDimpleRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthFrownLeft __attribute__((swift_name("mouthFrownLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthFrownRight __attribute__((swift_name("mouthFrownRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthFunnel __attribute__((swift_name("mouthFunnel")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthLeft __attribute__((swift_name("mouthLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthLowerDownLeft __attribute__((swift_name("mouthLowerDownLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthLowerDownRight __attribute__((swift_name("mouthLowerDownRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthPressLeft __attribute__((swift_name("mouthPressLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthPressRight __attribute__((swift_name("mouthPressRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthPucker __attribute__((swift_name("mouthPucker")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthRight __attribute__((swift_name("mouthRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthRollLower __attribute__((swift_name("mouthRollLower")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthRollUpper __attribute__((swift_name("mouthRollUpper")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthShrugLower __attribute__((swift_name("mouthShrugLower")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthShrugUpper __attribute__((swift_name("mouthShrugUpper")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthSmileLeft __attribute__((swift_name("mouthSmileLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthSmileRight __attribute__((swift_name("mouthSmileRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthStretchLeft __attribute__((swift_name("mouthStretchLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthStretchRight __attribute__((swift_name("mouthStretchRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthUpperUpLeft __attribute__((swift_name("mouthUpperUpLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *mouthUpperUpRight __attribute__((swift_name("mouthUpperUpRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *browDownLeft __attribute__((swift_name("browDownLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *browDownRight __attribute__((swift_name("browDownRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *browInnerUp __attribute__((swift_name("browInnerUp")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *browOuterUpLeft __attribute__((swift_name("browOuterUpLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *browOuterUpRight __attribute__((swift_name("browOuterUpRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *cheekPuff __attribute__((swift_name("cheekPuff")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *cheekSquintLeft __attribute__((swift_name("cheekSquintLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *cheekSquintRight __attribute__((swift_name("cheekSquintRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *noseSneerLeft __attribute__((swift_name("noseSneerLeft")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *noseSneerRight __attribute__((swift_name("noseSneerRight")));
@property (class, readonly) KMPFLIFMKmp_facelinkBlendShape *tongueOut __attribute__((swift_name("tongueOut")));
+ (KMPFLIFMKotlinArray<KMPFLIFMKmp_facelinkBlendShape *> *)values __attribute__((swift_name("values()")));
@property (class, readonly) NSArray<KMPFLIFMKmp_facelinkBlendShape *> *entries __attribute__((swift_name("entries")));
@property (readonly) NSString *arKitName __attribute__((swift_name("arKitName")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kmp_facelinkHeadTransform")))
@interface KMPFLIFMKmp_facelinkHeadTransform : KMPFLIFMBase
- (instancetype)initWithPitch:(float)pitch yaw:(float)yaw roll:(float)roll positionX:(float)positionX positionY:(float)positionY positionZ:(float)positionZ transformMatrix:(KMPFLIFMKotlinFloatArray * _Nullable)transformMatrix __attribute__((swift_name("init(pitch:yaw:roll:positionX:positionY:positionZ:transformMatrix:)"))) __attribute__((objc_designated_initializer));
- (KMPFLIFMKmp_facelinkHeadTransform *)doCopyPitch:(float)pitch yaw:(float)yaw roll:(float)roll positionX:(float)positionX positionY:(float)positionY positionZ:(float)positionZ transformMatrix:(KMPFLIFMKotlinFloatArray * _Nullable)transformMatrix __attribute__((swift_name("doCopy(pitch:yaw:roll:positionX:positionY:positionZ:transformMatrix:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) float pitch __attribute__((swift_name("pitch")));
@property (readonly) float positionX __attribute__((swift_name("positionX")));
@property (readonly) float positionY __attribute__((swift_name("positionY")));
@property (readonly) float positionZ __attribute__((swift_name("positionZ")));
@property (readonly) float roll __attribute__((swift_name("roll")));
@property (readonly) KMPFLIFMKotlinFloatArray * _Nullable transformMatrix __attribute__((swift_name("transformMatrix")));
@property (readonly) float yaw __attribute__((swift_name("yaw")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kmp_facelinkGazeData")))
@interface KMPFLIFMKmp_facelinkGazeData : KMPFLIFMBase
- (instancetype)initWithLeftEyeYaw:(float)leftEyeYaw leftEyePitch:(float)leftEyePitch rightEyeYaw:(float)rightEyeYaw rightEyePitch:(float)rightEyePitch __attribute__((swift_name("init(leftEyeYaw:leftEyePitch:rightEyeYaw:rightEyePitch:)"))) __attribute__((objc_designated_initializer));
- (KMPFLIFMKmp_facelinkGazeData *)doCopyLeftEyeYaw:(float)leftEyeYaw leftEyePitch:(float)leftEyePitch rightEyeYaw:(float)rightEyeYaw rightEyePitch:(float)rightEyePitch __attribute__((swift_name("doCopy(leftEyeYaw:leftEyePitch:rightEyeYaw:rightEyePitch:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) float combinedPitch __attribute__((swift_name("combinedPitch")));
@property (readonly) float combinedYaw __attribute__((swift_name("combinedYaw")));
@property (readonly) float leftEyePitch __attribute__((swift_name("leftEyePitch")));
@property (readonly) float leftEyeYaw __attribute__((swift_name("leftEyeYaw")));
@property (readonly) float rightEyePitch __attribute__((swift_name("rightEyePitch")));
@property (readonly) float rightEyeYaw __attribute__((swift_name("rightEyeYaw")));
@end

__attribute__((swift_name("KotlinIterator")))
@protocol KMPFLIFMKotlinIterator
@required
- (BOOL)hasNext __attribute__((swift_name("hasNext()")));
- (id _Nullable)next __attribute__((swift_name("next()")));
@end


/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.3")
*/
__attribute__((swift_name("KotlinCoroutineContext")))
@protocol KMPFLIFMKotlinCoroutineContext
@required
- (id _Nullable)foldInitial:(id _Nullable)initial operation:(id _Nullable (^)(id _Nullable, id<KMPFLIFMKotlinCoroutineContextElement>))operation __attribute__((swift_name("fold(initial:operation:)")));
- (id<KMPFLIFMKotlinCoroutineContextElement> _Nullable)getKey:(id<KMPFLIFMKotlinCoroutineContextKey>)key __attribute__((swift_name("get(key:)")));
- (id<KMPFLIFMKotlinCoroutineContext>)minusKeyKey:(id<KMPFLIFMKotlinCoroutineContextKey>)key __attribute__((swift_name("minusKey(key:)")));
- (id<KMPFLIFMKotlinCoroutineContext>)plusContext:(id<KMPFLIFMKotlinCoroutineContext>)context __attribute__((swift_name("plus(context:)")));
@end

__attribute__((swift_name("KotlinThrowable")))
@interface KMPFLIFMKotlinThrowable : KMPFLIFMBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(KMPFLIFMKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(KMPFLIFMKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));

/**
 * @note annotations
 *   kotlin.experimental.ExperimentalNativeApi
*/
- (KMPFLIFMKotlinArray<NSString *> *)getStackTrace __attribute__((swift_name("getStackTrace()")));
- (void)printStackTrace __attribute__((swift_name("printStackTrace()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) KMPFLIFMKotlinThrowable * _Nullable cause __attribute__((swift_name("cause")));
@property (readonly) NSString * _Nullable message __attribute__((swift_name("message")));
- (NSError *)asError __attribute__((swift_name("asError()")));
@end

__attribute__((swift_name("KotlinException")))
@interface KMPFLIFMKotlinException : KMPFLIFMKotlinThrowable
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(KMPFLIFMKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(KMPFLIFMKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("KotlinRuntimeException")))
@interface KMPFLIFMKotlinRuntimeException : KMPFLIFMKotlinException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(KMPFLIFMKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(KMPFLIFMKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("KotlinIllegalStateException")))
@interface KMPFLIFMKotlinIllegalStateException : KMPFLIFMKotlinRuntimeException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(KMPFLIFMKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(KMPFLIFMKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end


/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.4")
*/
__attribute__((swift_name("KotlinCancellationException")))
@interface KMPFLIFMKotlinCancellationException : KMPFLIFMKotlinIllegalStateException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(KMPFLIFMKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(KMPFLIFMKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("Kotlinx_coroutines_coreFlowCollector")))
@protocol KMPFLIFMKotlinx_coroutines_coreFlowCollector
@required

/**
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)emitValue:(id _Nullable)value completionHandler:(void (^)(NSError * _Nullable))completionHandler __attribute__((swift_name("emit(value:completionHandler:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kmp_facelinkBlendShape.Companion")))
@interface KMPFLIFMKmp_facelinkBlendShapeCompanion : KMPFLIFMBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) KMPFLIFMKmp_facelinkBlendShapeCompanion *shared __attribute__((swift_name("shared")));
- (KMPFLIFMKmp_facelinkBlendShape * _Nullable)fromArKitNameName:(NSString *)name __attribute__((swift_name("fromArKitName(name:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinFloatArray")))
@interface KMPFLIFMKotlinFloatArray : KMPFLIFMBase
+ (instancetype)arrayWithSize:(int32_t)size __attribute__((swift_name("init(size:)")));
+ (instancetype)arrayWithSize:(int32_t)size init:(KMPFLIFMFloat *(^)(KMPFLIFMInt *))init __attribute__((swift_name("init(size:init:)")));
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (float)getIndex:(int32_t)index __attribute__((swift_name("get(index:)")));
- (KMPFLIFMKotlinFloatIterator *)iterator __attribute__((swift_name("iterator()")));
- (void)setIndex:(int32_t)index value:(float)value __attribute__((swift_name("set(index:value:)")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end

__attribute__((swift_name("KotlinCoroutineContextElement")))
@protocol KMPFLIFMKotlinCoroutineContextElement <KMPFLIFMKotlinCoroutineContext>
@required
@property (readonly) id<KMPFLIFMKotlinCoroutineContextKey> key __attribute__((swift_name("key")));
@end

__attribute__((swift_name("KotlinCoroutineContextKey")))
@protocol KMPFLIFMKotlinCoroutineContextKey
@required
@end

__attribute__((swift_name("KotlinFloatIterator")))
@interface KMPFLIFMKotlinFloatIterator : KMPFLIFMBase <KMPFLIFMKotlinIterator>
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (KMPFLIFMFloat *)next __attribute__((swift_name("next()")));
- (float)nextFloat __attribute__((swift_name("nextFloat()")));
@end

#pragma pop_macro("_Nullable_result")
#pragma clang diagnostic pop
NS_ASSUME_NONNULL_END
