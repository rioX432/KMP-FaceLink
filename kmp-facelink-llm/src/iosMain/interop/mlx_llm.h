/**
 * Objective-C wrapper interface for MLX Swift LLM inference.
 *
 * This header defines the API that the actual MLX Swift wrapper must implement.
 * K/N cinterop bindings are generated from this header via mlx_llm.def.
 *
 * The implementation wraps MLX Swift's LLMModel/generate APIs in an
 * Objective-C class so Kotlin/Native can call it via cinterop.
 *
 * Build the MLX framework with: scripts/setup-mlx-ios.sh
 */

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Objective-C bridge to MLX Swift LLM inference engine.
 *
 * Wraps mlx-swift-examples' LLM loading and generation APIs.
 * Thread safety: callers must serialize access externally (IosInferenceEngine uses NSRecursiveLock).
 */
@interface MLXLlmEngine : NSObject

/**
 * Loads a model from the given directory path.
 *
 * The path should point to an MLX model directory containing
 * weights, tokenizer, and configuration files.
 *
 * @param path Absolute path to the MLX model directory
 * @param nCtx Context window size
 * @return YES if the model was loaded successfully
 */
- (BOOL)loadModelFromPath:(NSString *)path contextSize:(int)nCtx;

/**
 * Generates text from a prompt, streaming tokens via callback.
 *
 * @param prompt Input prompt string (ChatML formatted)
 * @param maxTokens Maximum number of tokens to generate
 * @param temperature Sampling temperature (0.0 = greedy, higher = more random)
 * @param callback Block called for each generated token; receives the token string
 * @return The complete generated text
 */
- (NSString *)generateWithPrompt:(NSString *)prompt
                       maxTokens:(int)maxTokens
                     temperature:(float)temperature
                   tokenCallback:(void (^)(NSString *token))callback;

/**
 * Unloads the model and releases all associated memory.
 */
- (void)unloadModel;

/**
 * Whether a model is currently loaded and ready for inference.
 */
- (BOOL)isModelLoaded;

@end

NS_ASSUME_NONNULL_END
