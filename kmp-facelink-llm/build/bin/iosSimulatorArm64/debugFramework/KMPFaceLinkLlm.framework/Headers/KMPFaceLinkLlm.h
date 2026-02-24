#import <Foundation/NSArray.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSError.h>
#import <Foundation/NSObject.h>
#import <Foundation/NSSet.h>
#import <Foundation/NSString.h>
#import <Foundation/NSValue.h>

@class KMPFLLChatMessage, KMPFLLChatMessageCompanion, KMPFLLChatMessageRole, KMPFLLConversationHistory, KMPFLLKotlinArray<T>, KMPFLLKotlinEnum<E>, KMPFLLKotlinEnumCompanion, KMPFLLKotlinException, KMPFLLKotlinIllegalStateException, KMPFLLKotlinNothing, KMPFLLKotlinRuntimeException, KMPFLLKotlinThrowable, KMPFLLKotlinx_serialization_coreSerialKind, KMPFLLKotlinx_serialization_coreSerializersModule, KMPFLLLlmClientCompanion, KMPFLLLlmConfig, KMPFLLLlmConfigAnthropic, KMPFLLLlmConfigGemini, KMPFLLLlmConfigOpenAi, KMPFLLLlmState, KMPFLLLlmStateError, KMPFLLLlmStateIdle, KMPFLLLlmStateStreaming, KMPFLLPersonaConfig, KMPFLLTokenCounter;

@protocol KMPFLLKmp_facelinkReleasable, KMPFLLKotlinAnnotation, KMPFLLKotlinComparable, KMPFLLKotlinIterator, KMPFLLKotlinKAnnotatedElement, KMPFLLKotlinKClass, KMPFLLKotlinKClassifier, KMPFLLKotlinKDeclarationContainer, KMPFLLKotlinx_coroutines_coreFlow, KMPFLLKotlinx_coroutines_coreFlowCollector, KMPFLLKotlinx_coroutines_coreSharedFlow, KMPFLLKotlinx_coroutines_coreStateFlow, KMPFLLKotlinx_serialization_coreCompositeDecoder, KMPFLLKotlinx_serialization_coreCompositeEncoder, KMPFLLKotlinx_serialization_coreDecoder, KMPFLLKotlinx_serialization_coreDeserializationStrategy, KMPFLLKotlinx_serialization_coreEncoder, KMPFLLKotlinx_serialization_coreKSerializer, KMPFLLKotlinx_serialization_coreSerialDescriptor, KMPFLLKotlinx_serialization_coreSerializationStrategy, KMPFLLKotlinx_serialization_coreSerializersModuleCollector, KMPFLLLlmClient;

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
@interface KMPFLLBase : NSObject
- (instancetype)init __attribute__((unavailable));
+ (instancetype)new __attribute__((unavailable));
+ (void)initialize __attribute__((objc_requires_super));
@end

@interface KMPFLLBase (KMPFLLBaseCopying) <NSCopying>
@end

__attribute__((swift_name("KotlinMutableSet")))
@interface KMPFLLMutableSet<ObjectType> : NSMutableSet<ObjectType>
@end

__attribute__((swift_name("KotlinMutableDictionary")))
@interface KMPFLLMutableDictionary<KeyType, ObjectType> : NSMutableDictionary<KeyType, ObjectType>
@end

@interface NSError (NSErrorKMPFLLKotlinException)
@property (readonly) id _Nullable kotlinException;
@end

__attribute__((swift_name("KotlinNumber")))
@interface KMPFLLNumber : NSNumber
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
@interface KMPFLLByte : KMPFLLNumber
- (instancetype)initWithChar:(char)value;
+ (instancetype)numberWithChar:(char)value;
@end

__attribute__((swift_name("KotlinUByte")))
@interface KMPFLLUByte : KMPFLLNumber
- (instancetype)initWithUnsignedChar:(unsigned char)value;
+ (instancetype)numberWithUnsignedChar:(unsigned char)value;
@end

__attribute__((swift_name("KotlinShort")))
@interface KMPFLLShort : KMPFLLNumber
- (instancetype)initWithShort:(short)value;
+ (instancetype)numberWithShort:(short)value;
@end

__attribute__((swift_name("KotlinUShort")))
@interface KMPFLLUShort : KMPFLLNumber
- (instancetype)initWithUnsignedShort:(unsigned short)value;
+ (instancetype)numberWithUnsignedShort:(unsigned short)value;
@end

__attribute__((swift_name("KotlinInt")))
@interface KMPFLLInt : KMPFLLNumber
- (instancetype)initWithInt:(int)value;
+ (instancetype)numberWithInt:(int)value;
@end

__attribute__((swift_name("KotlinUInt")))
@interface KMPFLLUInt : KMPFLLNumber
- (instancetype)initWithUnsignedInt:(unsigned int)value;
+ (instancetype)numberWithUnsignedInt:(unsigned int)value;
@end

__attribute__((swift_name("KotlinLong")))
@interface KMPFLLLong : KMPFLLNumber
- (instancetype)initWithLongLong:(long long)value;
+ (instancetype)numberWithLongLong:(long long)value;
@end

__attribute__((swift_name("KotlinULong")))
@interface KMPFLLULong : KMPFLLNumber
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value;
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value;
@end

__attribute__((swift_name("KotlinFloat")))
@interface KMPFLLFloat : KMPFLLNumber
- (instancetype)initWithFloat:(float)value;
+ (instancetype)numberWithFloat:(float)value;
@end

__attribute__((swift_name("KotlinDouble")))
@interface KMPFLLDouble : KMPFLLNumber
- (instancetype)initWithDouble:(double)value;
+ (instancetype)numberWithDouble:(double)value;
@end

__attribute__((swift_name("KotlinBoolean")))
@interface KMPFLLBoolean : KMPFLLNumber
- (instancetype)initWithBool:(BOOL)value;
+ (instancetype)numberWithBool:(BOOL)value;
@end


/**
 * @note annotations
 *   kotlinx.serialization.Serializable
*/
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("ChatMessage")))
@interface KMPFLLChatMessage : KMPFLLBase
- (instancetype)initWithRole:(KMPFLLChatMessageRole *)role content:(NSString *)content __attribute__((swift_name("init(role:content:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) KMPFLLChatMessageCompanion *companion __attribute__((swift_name("companion")));
- (KMPFLLChatMessage *)doCopyRole:(KMPFLLChatMessageRole *)role content:(NSString *)content __attribute__((swift_name("doCopy(role:content:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *content __attribute__((swift_name("content")));
@property (readonly) KMPFLLChatMessageRole *role __attribute__((swift_name("role")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("ChatMessage.Companion")))
@interface KMPFLLChatMessageCompanion : KMPFLLBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) KMPFLLChatMessageCompanion *shared __attribute__((swift_name("shared")));
- (id<KMPFLLKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("serializer()")));
@end

__attribute__((swift_name("KotlinComparable")))
@protocol KMPFLLKotlinComparable
@required
- (int32_t)compareToOther:(id _Nullable)other __attribute__((swift_name("compareTo(other:)")));
@end

__attribute__((swift_name("KotlinEnum")))
@interface KMPFLLKotlinEnum<E> : KMPFLLBase <KMPFLLKotlinComparable>
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) KMPFLLKotlinEnumCompanion *companion __attribute__((swift_name("companion")));
- (int32_t)compareToOther:(E)other __attribute__((swift_name("compareTo(other:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *name __attribute__((swift_name("name")));
@property (readonly) int32_t ordinal __attribute__((swift_name("ordinal")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("ChatMessage.Role")))
@interface KMPFLLChatMessageRole : KMPFLLKotlinEnum<KMPFLLChatMessageRole *>
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
@property (class, readonly) KMPFLLChatMessageRole *system __attribute__((swift_name("system")));
@property (class, readonly) KMPFLLChatMessageRole *user __attribute__((swift_name("user")));
@property (class, readonly) KMPFLLChatMessageRole *assistant __attribute__((swift_name("assistant")));
+ (KMPFLLKotlinArray<KMPFLLChatMessageRole *> *)values __attribute__((swift_name("values()")));
@property (class, readonly) NSArray<KMPFLLChatMessageRole *> *entries __attribute__((swift_name("entries")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("ConversationHistory")))
@interface KMPFLLConversationHistory : KMPFLLBase
- (instancetype)initWithPersona:(KMPFLLPersonaConfig *)persona maxContextTokens:(int32_t)maxContextTokens __attribute__((swift_name("init(persona:maxContextTokens:)"))) __attribute__((objc_designated_initializer));
- (void)addAssistantContent:(NSString *)content __attribute__((swift_name("addAssistant(content:)")));
- (void)addUserContent:(NSString *)content __attribute__((swift_name("addUser(content:)")));
- (void)clear __attribute__((swift_name("clear()")));
- (NSArray<KMPFLLChatMessage *> *)toMessageList __attribute__((swift_name("toMessageList()")));
@property (readonly) int32_t maxContextTokens __attribute__((swift_name("maxContextTokens")));
@property (readonly) NSArray<KMPFLLChatMessage *> *messages __attribute__((swift_name("messages")));
@property (readonly) KMPFLLPersonaConfig *persona __attribute__((swift_name("persona")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end

__attribute__((swift_name("Kmp_facelinkReleasable")))
@protocol KMPFLLKmp_facelinkReleasable
@required
- (void)release_ __attribute__((swift_name("release()")));
@end

__attribute__((swift_name("LlmClient")))
@protocol KMPFLLLlmClient <KMPFLLKmp_facelinkReleasable>
@required
- (id<KMPFLLKotlinx_coroutines_coreFlow>)chatHistory:(KMPFLLConversationHistory *)history __attribute__((swift_name("chat(history:)")));

/**
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)chatOnceHistory:(KMPFLLConversationHistory *)history completionHandler:(void (^)(NSString * _Nullable, NSError * _Nullable))completionHandler __attribute__((swift_name("chatOnce(history:completionHandler:)")));
@property (readonly) id<KMPFLLKotlinx_coroutines_coreStateFlow> state __attribute__((swift_name("state")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LlmClientCompanion")))
@interface KMPFLLLlmClientCompanion : KMPFLLBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) KMPFLLLlmClientCompanion *shared __attribute__((swift_name("shared")));
- (id<KMPFLLLlmClient>)createConfig:(KMPFLLLlmConfig *)config __attribute__((swift_name("create(config:)")));
@end

__attribute__((swift_name("LlmConfig")))
@interface KMPFLLLlmConfig : KMPFLLBase
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LlmConfig.Anthropic")))
@interface KMPFLLLlmConfigAnthropic : KMPFLLLlmConfig
- (instancetype)initWithApiKey:(NSString *)apiKey model:(NSString *)model maxTokens:(int32_t)maxTokens temperature:(double)temperature __attribute__((swift_name("init(apiKey:model:maxTokens:temperature:)"))) __attribute__((objc_designated_initializer));
- (KMPFLLLlmConfigAnthropic *)doCopyApiKey:(NSString *)apiKey model:(NSString *)model maxTokens:(int32_t)maxTokens temperature:(double)temperature __attribute__((swift_name("doCopy(apiKey:model:maxTokens:temperature:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *apiKey __attribute__((swift_name("apiKey")));
@property (readonly) int32_t maxTokens __attribute__((swift_name("maxTokens")));
@property (readonly) NSString *model __attribute__((swift_name("model")));
@property (readonly) double temperature __attribute__((swift_name("temperature")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LlmConfig.Gemini")))
@interface KMPFLLLlmConfigGemini : KMPFLLLlmConfig
- (instancetype)initWithApiKey:(NSString *)apiKey model:(NSString *)model maxTokens:(int32_t)maxTokens temperature:(double)temperature __attribute__((swift_name("init(apiKey:model:maxTokens:temperature:)"))) __attribute__((objc_designated_initializer));
- (KMPFLLLlmConfigGemini *)doCopyApiKey:(NSString *)apiKey model:(NSString *)model maxTokens:(int32_t)maxTokens temperature:(double)temperature __attribute__((swift_name("doCopy(apiKey:model:maxTokens:temperature:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *apiKey __attribute__((swift_name("apiKey")));
@property (readonly) int32_t maxTokens __attribute__((swift_name("maxTokens")));
@property (readonly) NSString *model __attribute__((swift_name("model")));
@property (readonly) double temperature __attribute__((swift_name("temperature")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LlmConfig.OpenAi")))
@interface KMPFLLLlmConfigOpenAi : KMPFLLLlmConfig
- (instancetype)initWithApiKey:(NSString *)apiKey model:(NSString *)model baseUrl:(NSString *)baseUrl maxTokens:(int32_t)maxTokens temperature:(double)temperature __attribute__((swift_name("init(apiKey:model:baseUrl:maxTokens:temperature:)"))) __attribute__((objc_designated_initializer));
- (KMPFLLLlmConfigOpenAi *)doCopyApiKey:(NSString *)apiKey model:(NSString *)model baseUrl:(NSString *)baseUrl maxTokens:(int32_t)maxTokens temperature:(double)temperature __attribute__((swift_name("doCopy(apiKey:model:baseUrl:maxTokens:temperature:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *apiKey __attribute__((swift_name("apiKey")));
@property (readonly) NSString *baseUrl __attribute__((swift_name("baseUrl")));
@property (readonly) int32_t maxTokens __attribute__((swift_name("maxTokens")));
@property (readonly) NSString *model __attribute__((swift_name("model")));
@property (readonly) double temperature __attribute__((swift_name("temperature")));
@end

__attribute__((swift_name("LlmState")))
@interface KMPFLLLlmState : KMPFLLBase
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LlmState.Error")))
@interface KMPFLLLlmStateError : KMPFLLLlmState
- (instancetype)initWithMessage:(NSString *)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (KMPFLLLlmStateError *)doCopyMessage:(NSString *)message __attribute__((swift_name("doCopy(message:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *message __attribute__((swift_name("message")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LlmState.Idle")))
@interface KMPFLLLlmStateIdle : KMPFLLLlmState
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)idle __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) KMPFLLLlmStateIdle *shared __attribute__((swift_name("shared")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LlmState.Streaming")))
@interface KMPFLLLlmStateStreaming : KMPFLLLlmState
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)streaming __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) KMPFLLLlmStateStreaming *shared __attribute__((swift_name("shared")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PersonaConfig")))
@interface KMPFLLPersonaConfig : KMPFLLBase
- (instancetype)initWithSystemPrompt:(NSString *)systemPrompt name:(NSString *)name language:(NSString * _Nullable)language __attribute__((swift_name("init(systemPrompt:name:language:)"))) __attribute__((objc_designated_initializer));
- (KMPFLLPersonaConfig *)doCopySystemPrompt:(NSString *)systemPrompt name:(NSString *)name language:(NSString * _Nullable)language __attribute__((swift_name("doCopy(systemPrompt:name:language:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString * _Nullable language __attribute__((swift_name("language")));
@property (readonly) NSString *name __attribute__((swift_name("name")));
@property (readonly) NSString *systemPrompt __attribute__((swift_name("systemPrompt")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("TokenCounter")))
@interface KMPFLLTokenCounter : KMPFLLBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)tokenCounter __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) KMPFLLTokenCounter *shared __attribute__((swift_name("shared")));
- (int32_t)estimateText:(NSString *)text __attribute__((swift_name("estimate(text:)")));
- (int32_t)estimateMessages:(NSArray<KMPFLLChatMessage *> *)messages __attribute__((swift_name("estimate(messages:)")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreSerializationStrategy")))
@protocol KMPFLLKotlinx_serialization_coreSerializationStrategy
@required
- (void)serializeEncoder:(id<KMPFLLKotlinx_serialization_coreEncoder>)encoder value:(id _Nullable)value __attribute__((swift_name("serialize(encoder:value:)")));
@property (readonly) id<KMPFLLKotlinx_serialization_coreSerialDescriptor> descriptor __attribute__((swift_name("descriptor")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreDeserializationStrategy")))
@protocol KMPFLLKotlinx_serialization_coreDeserializationStrategy
@required
- (id _Nullable)deserializeDecoder:(id<KMPFLLKotlinx_serialization_coreDecoder>)decoder __attribute__((swift_name("deserialize(decoder:)")));
@property (readonly) id<KMPFLLKotlinx_serialization_coreSerialDescriptor> descriptor __attribute__((swift_name("descriptor")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreKSerializer")))
@protocol KMPFLLKotlinx_serialization_coreKSerializer <KMPFLLKotlinx_serialization_coreSerializationStrategy, KMPFLLKotlinx_serialization_coreDeserializationStrategy>
@required
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinEnumCompanion")))
@interface KMPFLLKotlinEnumCompanion : KMPFLLBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) KMPFLLKotlinEnumCompanion *shared __attribute__((swift_name("shared")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinArray")))
@interface KMPFLLKotlinArray<T> : KMPFLLBase
+ (instancetype)arrayWithSize:(int32_t)size init:(T _Nullable (^)(KMPFLLInt *))init __attribute__((swift_name("init(size:init:)")));
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (T _Nullable)getIndex:(int32_t)index __attribute__((swift_name("get(index:)")));
- (id<KMPFLLKotlinIterator>)iterator __attribute__((swift_name("iterator()")));
- (void)setIndex:(int32_t)index value:(T _Nullable)value __attribute__((swift_name("set(index:value:)")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end

__attribute__((swift_name("KotlinThrowable")))
@interface KMPFLLKotlinThrowable : KMPFLLBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(KMPFLLKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(KMPFLLKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));

/**
 * @note annotations
 *   kotlin.experimental.ExperimentalNativeApi
*/
- (KMPFLLKotlinArray<NSString *> *)getStackTrace __attribute__((swift_name("getStackTrace()")));
- (void)printStackTrace __attribute__((swift_name("printStackTrace()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) KMPFLLKotlinThrowable * _Nullable cause __attribute__((swift_name("cause")));
@property (readonly) NSString * _Nullable message __attribute__((swift_name("message")));
- (NSError *)asError __attribute__((swift_name("asError()")));
@end

__attribute__((swift_name("KotlinException")))
@interface KMPFLLKotlinException : KMPFLLKotlinThrowable
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(KMPFLLKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(KMPFLLKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("KotlinRuntimeException")))
@interface KMPFLLKotlinRuntimeException : KMPFLLKotlinException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(KMPFLLKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(KMPFLLKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("KotlinIllegalStateException")))
@interface KMPFLLKotlinIllegalStateException : KMPFLLKotlinRuntimeException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(KMPFLLKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(KMPFLLKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end


/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.4")
*/
__attribute__((swift_name("KotlinCancellationException")))
@interface KMPFLLKotlinCancellationException : KMPFLLKotlinIllegalStateException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(KMPFLLKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(KMPFLLKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("Kotlinx_coroutines_coreFlow")))
@protocol KMPFLLKotlinx_coroutines_coreFlow
@required

/**
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)collectCollector:(id<KMPFLLKotlinx_coroutines_coreFlowCollector>)collector completionHandler:(void (^)(NSError * _Nullable))completionHandler __attribute__((swift_name("collect(collector:completionHandler:)")));
@end

__attribute__((swift_name("Kotlinx_coroutines_coreSharedFlow")))
@protocol KMPFLLKotlinx_coroutines_coreSharedFlow <KMPFLLKotlinx_coroutines_coreFlow>
@required
@property (readonly) NSArray<id> *replayCache __attribute__((swift_name("replayCache")));
@end

__attribute__((swift_name("Kotlinx_coroutines_coreStateFlow")))
@protocol KMPFLLKotlinx_coroutines_coreStateFlow <KMPFLLKotlinx_coroutines_coreSharedFlow>
@required
@property (readonly) id _Nullable value __attribute__((swift_name("value")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreEncoder")))
@protocol KMPFLLKotlinx_serialization_coreEncoder
@required
- (id<KMPFLLKotlinx_serialization_coreCompositeEncoder>)beginCollectionDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor collectionSize:(int32_t)collectionSize __attribute__((swift_name("beginCollection(descriptor:collectionSize:)")));
- (id<KMPFLLKotlinx_serialization_coreCompositeEncoder>)beginStructureDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("beginStructure(descriptor:)")));
- (void)encodeBooleanValue:(BOOL)value __attribute__((swift_name("encodeBoolean(value:)")));
- (void)encodeByteValue:(int8_t)value __attribute__((swift_name("encodeByte(value:)")));
- (void)encodeCharValue:(unichar)value __attribute__((swift_name("encodeChar(value:)")));
- (void)encodeDoubleValue:(double)value __attribute__((swift_name("encodeDouble(value:)")));
- (void)encodeEnumEnumDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)enumDescriptor index:(int32_t)index __attribute__((swift_name("encodeEnum(enumDescriptor:index:)")));
- (void)encodeFloatValue:(float)value __attribute__((swift_name("encodeFloat(value:)")));
- (id<KMPFLLKotlinx_serialization_coreEncoder>)encodeInlineDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("encodeInline(descriptor:)")));
- (void)encodeIntValue:(int32_t)value __attribute__((swift_name("encodeInt(value:)")));
- (void)encodeLongValue:(int64_t)value __attribute__((swift_name("encodeLong(value:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (void)encodeNotNullMark __attribute__((swift_name("encodeNotNullMark()")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (void)encodeNull __attribute__((swift_name("encodeNull()")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (void)encodeNullableSerializableValueSerializer:(id<KMPFLLKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeNullableSerializableValue(serializer:value:)")));
- (void)encodeSerializableValueSerializer:(id<KMPFLLKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeSerializableValue(serializer:value:)")));
- (void)encodeShortValue:(int16_t)value __attribute__((swift_name("encodeShort(value:)")));
- (void)encodeStringValue:(NSString *)value __attribute__((swift_name("encodeString(value:)")));
@property (readonly) KMPFLLKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreSerialDescriptor")))
@protocol KMPFLLKotlinx_serialization_coreSerialDescriptor
@required
- (NSArray<id<KMPFLLKotlinAnnotation>> *)getElementAnnotationsIndex:(int32_t)index __attribute__((swift_name("getElementAnnotations(index:)")));
- (id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)getElementDescriptorIndex:(int32_t)index __attribute__((swift_name("getElementDescriptor(index:)")));
- (int32_t)getElementIndexName:(NSString *)name __attribute__((swift_name("getElementIndex(name:)")));
- (NSString *)getElementNameIndex:(int32_t)index __attribute__((swift_name("getElementName(index:)")));
- (BOOL)isElementOptionalIndex:(int32_t)index __attribute__((swift_name("isElementOptional(index:)")));
@property (readonly) NSArray<id<KMPFLLKotlinAnnotation>> *annotations __attribute__((swift_name("annotations")));
@property (readonly) int32_t elementsCount __attribute__((swift_name("elementsCount")));
@property (readonly) BOOL isInline __attribute__((swift_name("isInline")));
@property (readonly) BOOL isNullable __attribute__((swift_name("isNullable")));
@property (readonly) KMPFLLKotlinx_serialization_coreSerialKind *kind __attribute__((swift_name("kind")));
@property (readonly) NSString *serialName __attribute__((swift_name("serialName")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreDecoder")))
@protocol KMPFLLKotlinx_serialization_coreDecoder
@required
- (id<KMPFLLKotlinx_serialization_coreCompositeDecoder>)beginStructureDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("beginStructure(descriptor:)")));
- (BOOL)decodeBoolean __attribute__((swift_name("decodeBoolean()")));
- (int8_t)decodeByte __attribute__((swift_name("decodeByte()")));
- (unichar)decodeChar __attribute__((swift_name("decodeChar()")));
- (double)decodeDouble __attribute__((swift_name("decodeDouble()")));
- (int32_t)decodeEnumEnumDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)enumDescriptor __attribute__((swift_name("decodeEnum(enumDescriptor:)")));
- (float)decodeFloat __attribute__((swift_name("decodeFloat()")));
- (id<KMPFLLKotlinx_serialization_coreDecoder>)decodeInlineDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("decodeInline(descriptor:)")));
- (int32_t)decodeInt __attribute__((swift_name("decodeInt()")));
- (int64_t)decodeLong __attribute__((swift_name("decodeLong()")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (BOOL)decodeNotNullMark __attribute__((swift_name("decodeNotNullMark()")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (KMPFLLKotlinNothing * _Nullable)decodeNull __attribute__((swift_name("decodeNull()")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id _Nullable)decodeNullableSerializableValueDeserializer:(id<KMPFLLKotlinx_serialization_coreDeserializationStrategy>)deserializer __attribute__((swift_name("decodeNullableSerializableValue(deserializer:)")));
- (id _Nullable)decodeSerializableValueDeserializer:(id<KMPFLLKotlinx_serialization_coreDeserializationStrategy>)deserializer __attribute__((swift_name("decodeSerializableValue(deserializer:)")));
- (int16_t)decodeShort __attribute__((swift_name("decodeShort()")));
- (NSString *)decodeString __attribute__((swift_name("decodeString()")));
@property (readonly) KMPFLLKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end

__attribute__((swift_name("KotlinIterator")))
@protocol KMPFLLKotlinIterator
@required
- (BOOL)hasNext __attribute__((swift_name("hasNext()")));
- (id _Nullable)next __attribute__((swift_name("next()")));
@end

__attribute__((swift_name("Kotlinx_coroutines_coreFlowCollector")))
@protocol KMPFLLKotlinx_coroutines_coreFlowCollector
@required

/**
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)emitValue:(id _Nullable)value completionHandler:(void (^)(NSError * _Nullable))completionHandler __attribute__((swift_name("emit(value:completionHandler:)")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreCompositeEncoder")))
@protocol KMPFLLKotlinx_serialization_coreCompositeEncoder
@required
- (void)encodeBooleanElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(BOOL)value __attribute__((swift_name("encodeBooleanElement(descriptor:index:value:)")));
- (void)encodeByteElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int8_t)value __attribute__((swift_name("encodeByteElement(descriptor:index:value:)")));
- (void)encodeCharElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(unichar)value __attribute__((swift_name("encodeCharElement(descriptor:index:value:)")));
- (void)encodeDoubleElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(double)value __attribute__((swift_name("encodeDoubleElement(descriptor:index:value:)")));
- (void)encodeFloatElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(float)value __attribute__((swift_name("encodeFloatElement(descriptor:index:value:)")));
- (id<KMPFLLKotlinx_serialization_coreEncoder>)encodeInlineElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("encodeInlineElement(descriptor:index:)")));
- (void)encodeIntElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int32_t)value __attribute__((swift_name("encodeIntElement(descriptor:index:value:)")));
- (void)encodeLongElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int64_t)value __attribute__((swift_name("encodeLongElement(descriptor:index:value:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (void)encodeNullableSerializableElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index serializer:(id<KMPFLLKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeNullableSerializableElement(descriptor:index:serializer:value:)")));
- (void)encodeSerializableElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index serializer:(id<KMPFLLKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeSerializableElement(descriptor:index:serializer:value:)")));
- (void)encodeShortElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int16_t)value __attribute__((swift_name("encodeShortElement(descriptor:index:value:)")));
- (void)encodeStringElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(NSString *)value __attribute__((swift_name("encodeStringElement(descriptor:index:value:)")));
- (void)endStructureDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("endStructure(descriptor:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (BOOL)shouldEncodeElementDefaultDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("shouldEncodeElementDefault(descriptor:index:)")));
@property (readonly) KMPFLLKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreSerializersModule")))
@interface KMPFLLKotlinx_serialization_coreSerializersModule : KMPFLLBase

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (void)dumpToCollector:(id<KMPFLLKotlinx_serialization_coreSerializersModuleCollector>)collector __attribute__((swift_name("dumpTo(collector:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id<KMPFLLKotlinx_serialization_coreKSerializer> _Nullable)getContextualKClass:(id<KMPFLLKotlinKClass>)kClass typeArgumentsSerializers:(NSArray<id<KMPFLLKotlinx_serialization_coreKSerializer>> *)typeArgumentsSerializers __attribute__((swift_name("getContextual(kClass:typeArgumentsSerializers:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id<KMPFLLKotlinx_serialization_coreSerializationStrategy> _Nullable)getPolymorphicBaseClass:(id<KMPFLLKotlinKClass>)baseClass value:(id)value __attribute__((swift_name("getPolymorphic(baseClass:value:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id<KMPFLLKotlinx_serialization_coreDeserializationStrategy> _Nullable)getPolymorphicBaseClass:(id<KMPFLLKotlinKClass>)baseClass serializedClassName:(NSString * _Nullable)serializedClassName __attribute__((swift_name("getPolymorphic(baseClass:serializedClassName:)")));
@end

__attribute__((swift_name("KotlinAnnotation")))
@protocol KMPFLLKotlinAnnotation
@required
@end

__attribute__((swift_name("Kotlinx_serialization_coreSerialKind")))
@interface KMPFLLKotlinx_serialization_coreSerialKind : KMPFLLBase
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreCompositeDecoder")))
@protocol KMPFLLKotlinx_serialization_coreCompositeDecoder
@required
- (BOOL)decodeBooleanElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeBooleanElement(descriptor:index:)")));
- (int8_t)decodeByteElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeByteElement(descriptor:index:)")));
- (unichar)decodeCharElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeCharElement(descriptor:index:)")));
- (int32_t)decodeCollectionSizeDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("decodeCollectionSize(descriptor:)")));
- (double)decodeDoubleElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeDoubleElement(descriptor:index:)")));
- (int32_t)decodeElementIndexDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("decodeElementIndex(descriptor:)")));
- (float)decodeFloatElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeFloatElement(descriptor:index:)")));
- (id<KMPFLLKotlinx_serialization_coreDecoder>)decodeInlineElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeInlineElement(descriptor:index:)")));
- (int32_t)decodeIntElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeIntElement(descriptor:index:)")));
- (int64_t)decodeLongElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeLongElement(descriptor:index:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id _Nullable)decodeNullableSerializableElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index deserializer:(id<KMPFLLKotlinx_serialization_coreDeserializationStrategy>)deserializer previousValue:(id _Nullable)previousValue __attribute__((swift_name("decodeNullableSerializableElement(descriptor:index:deserializer:previousValue:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (BOOL)decodeSequentially __attribute__((swift_name("decodeSequentially()")));
- (id _Nullable)decodeSerializableElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index deserializer:(id<KMPFLLKotlinx_serialization_coreDeserializationStrategy>)deserializer previousValue:(id _Nullable)previousValue __attribute__((swift_name("decodeSerializableElement(descriptor:index:deserializer:previousValue:)")));
- (int16_t)decodeShortElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeShortElement(descriptor:index:)")));
- (NSString *)decodeStringElementDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeStringElement(descriptor:index:)")));
- (void)endStructureDescriptor:(id<KMPFLLKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("endStructure(descriptor:)")));
@property (readonly) KMPFLLKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinNothing")))
@interface KMPFLLKotlinNothing : KMPFLLBase
@end


/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
__attribute__((swift_name("Kotlinx_serialization_coreSerializersModuleCollector")))
@protocol KMPFLLKotlinx_serialization_coreSerializersModuleCollector
@required
- (void)contextualKClass:(id<KMPFLLKotlinKClass>)kClass provider:(id<KMPFLLKotlinx_serialization_coreKSerializer> (^)(NSArray<id<KMPFLLKotlinx_serialization_coreKSerializer>> *))provider __attribute__((swift_name("contextual(kClass:provider:)")));
- (void)contextualKClass:(id<KMPFLLKotlinKClass>)kClass serializer:(id<KMPFLLKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("contextual(kClass:serializer:)")));
- (void)polymorphicBaseClass:(id<KMPFLLKotlinKClass>)baseClass actualClass:(id<KMPFLLKotlinKClass>)actualClass actualSerializer:(id<KMPFLLKotlinx_serialization_coreKSerializer>)actualSerializer __attribute__((swift_name("polymorphic(baseClass:actualClass:actualSerializer:)")));
- (void)polymorphicDefaultBaseClass:(id<KMPFLLKotlinKClass>)baseClass defaultDeserializerProvider:(id<KMPFLLKotlinx_serialization_coreDeserializationStrategy> _Nullable (^)(NSString * _Nullable))defaultDeserializerProvider __attribute__((swift_name("polymorphicDefault(baseClass:defaultDeserializerProvider:)"))) __attribute__((deprecated("Deprecated in favor of function with more precise name: polymorphicDefaultDeserializer")));
- (void)polymorphicDefaultDeserializerBaseClass:(id<KMPFLLKotlinKClass>)baseClass defaultDeserializerProvider:(id<KMPFLLKotlinx_serialization_coreDeserializationStrategy> _Nullable (^)(NSString * _Nullable))defaultDeserializerProvider __attribute__((swift_name("polymorphicDefaultDeserializer(baseClass:defaultDeserializerProvider:)")));
- (void)polymorphicDefaultSerializerBaseClass:(id<KMPFLLKotlinKClass>)baseClass defaultSerializerProvider:(id<KMPFLLKotlinx_serialization_coreSerializationStrategy> _Nullable (^)(id))defaultSerializerProvider __attribute__((swift_name("polymorphicDefaultSerializer(baseClass:defaultSerializerProvider:)")));
@end

__attribute__((swift_name("KotlinKDeclarationContainer")))
@protocol KMPFLLKotlinKDeclarationContainer
@required
@end

__attribute__((swift_name("KotlinKAnnotatedElement")))
@protocol KMPFLLKotlinKAnnotatedElement
@required
@end


/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.1")
*/
__attribute__((swift_name("KotlinKClassifier")))
@protocol KMPFLLKotlinKClassifier
@required
@end

__attribute__((swift_name("KotlinKClass")))
@protocol KMPFLLKotlinKClass <KMPFLLKotlinKDeclarationContainer, KMPFLLKotlinKAnnotatedElement, KMPFLLKotlinKClassifier>
@required

/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.1")
*/
- (BOOL)isInstanceValue:(id _Nullable)value __attribute__((swift_name("isInstance(value:)")));
@property (readonly) NSString * _Nullable qualifiedName __attribute__((swift_name("qualifiedName")));
@property (readonly) NSString * _Nullable simpleName __attribute__((swift_name("simpleName")));
@end

#pragma pop_macro("_Nullable_result")
#pragma clang diagnostic pop
NS_ASSUME_NONNULL_END
