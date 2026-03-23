/**
 * JNI bridge for llama.cpp.
 *
 * Wraps llama_model_load_from_file, llama_init_from_model, llama_decode,
 * and llama_model_free / llama_free for use from Kotlin/JVM via LlamaCppBridge.
 *
 * Streaming is implemented via a JNI callback: the C code calls
 * LlamaCallback.onToken(String) for each generated token.
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "llama.h"

#define TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* Max buffer size for token-to-piece conversion */
#define TOKEN_BUF_SIZE 256

/**
 * Opaque handle passed across JNI as a jlong.
 * Bundles the model and context together so that a single pointer
 * can be stored on the Kotlin side.
 */
typedef struct {
    struct llama_model   *model;
    struct llama_context *ctx;
    struct llama_sampler *sampler;
} llama_jni_context;

/* ── Init ─────────────────────────────────────────────────────────── */

JNIEXPORT jlong JNICALL
Java_io_github_kmpfacelink_llm_internal_LlamaCppBridge_nativeInit(
    JNIEnv *env, jobject thiz, jstring model_path,
    jint n_ctx, jint n_gpu_layers) {

    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    if (path == NULL) return 0;

    llama_backend_init();

    struct llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers;

    struct llama_model *model = llama_model_load_from_file(path, model_params);
    (*env)->ReleaseStringUTFChars(env, model_path, path);

    if (model == NULL) {
        LOGE("Failed to load model");
        return 0;
    }

    struct llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;

    struct llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == NULL) {
        LOGE("Failed to create context");
        llama_model_free(model);
        return 0;
    }

    llama_jni_context *jni_ctx = malloc(sizeof(llama_jni_context));
    if (jni_ctx == NULL) {
        llama_free(ctx);
        llama_model_free(model);
        return 0;
    }
    jni_ctx->model   = model;
    jni_ctx->ctx     = ctx;
    jni_ctx->sampler = NULL;

    LOGI("Model loaded successfully (n_ctx=%d, n_gpu_layers=%d)", n_ctx, n_gpu_layers);
    return (jlong)(intptr_t)jni_ctx;
}

/* ── helpers ──────────────────────────────────────────────────────── */

static struct llama_sampler *create_sampler(float temperature) {
    struct llama_sampler_chain_params chain_params = llama_sampler_chain_default_params();
    struct llama_sampler *chain = llama_sampler_chain_init(chain_params);

    if (temperature <= 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(chain, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(chain, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    }
    return chain;
}

static int tokenize_prompt(
    const struct llama_vocab *vocab,
    const char *text,
    llama_token **out_tokens) {

    /* First call: measure required size */
    int n = llama_tokenize(vocab, text, (int)strlen(text), NULL, 0, true, true);
    if (n < 0) n = -n; /* llama_tokenize returns negative when buffer is too small */

    llama_token *tokens = malloc(sizeof(llama_token) * (n + 1));
    if (tokens == NULL) return 0;

    int actual = llama_tokenize(vocab, text, (int)strlen(text), tokens, n + 1, true, true);
    if (actual < 0) {
        free(tokens);
        return 0;
    }
    *out_tokens = tokens;
    return actual;
}

/* ── Generate ─────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_io_github_kmpfacelink_llm_internal_LlamaCppBridge_nativeGenerate(
    JNIEnv *env, jobject thiz, jlong ctx_ptr, jstring prompt,
    jint max_tokens, jfloat temperature, jobject callback) {

    llama_jni_context *jni_ctx = (llama_jni_context *)(intptr_t)ctx_ptr;
    if (jni_ctx == NULL || jni_ctx->ctx == NULL) {
        return (*env)->NewStringUTF(env, "");
    }

    /* Resolve callback method */
    jclass cb_class = (*env)->GetObjectClass(env, callback);
    jmethodID on_token = (*env)->GetMethodID(env, cb_class, "onToken", "(Ljava/lang/String;)V");

    const char *prompt_str = (*env)->GetStringUTFChars(env, prompt, NULL);
    if (prompt_str == NULL) return (*env)->NewStringUTF(env, "");

    const struct llama_vocab *vocab = llama_model_get_vocab(jni_ctx->model);

    /* Tokenize */
    llama_token *tokens = NULL;
    int n_tokens = tokenize_prompt(vocab, prompt_str, &tokens);
    (*env)->ReleaseStringUTFChars(env, prompt, prompt_str);

    if (n_tokens <= 0) {
        LOGE("Tokenization failed");
        return (*env)->NewStringUTF(env, "");
    }

    /* Create sampler */
    struct llama_sampler *sampler = create_sampler(temperature);

    /* Prepare batch for prompt evaluation */
    struct llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i]    = tokens[i];
        batch.pos[i]      = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]   = (i == n_tokens - 1) ? 1 : 0;
    }
    batch.n_tokens = n_tokens;

    /* Evaluate prompt */
    if (llama_decode(jni_ctx->ctx, batch) != 0) {
        LOGE("llama_decode failed for prompt");
        llama_batch_free(batch);
        llama_sampler_free(sampler);
        free(tokens);
        return (*env)->NewStringUTF(env, "");
    }
    llama_batch_free(batch);
    free(tokens);

    llama_token eos = llama_vocab_eos(vocab);

    /* Result buffer */
    size_t result_cap = 4096;
    size_t result_len = 0;
    char *result = malloc(result_cap);
    if (result == NULL) {
        llama_sampler_free(sampler);
        return (*env)->NewStringUTF(env, "");
    }
    result[0] = '\0';

    int cur_pos = n_tokens;

    /* Generate tokens one by one */
    for (int i = 0; i < max_tokens; i++) {
        llama_token new_token = llama_sampler_sample(sampler, jni_ctx->ctx, -1);

        if (new_token == eos) break;

        /* Convert token to text */
        char piece[TOKEN_BUF_SIZE];
        int piece_len = llama_token_to_piece(vocab, new_token, piece, TOKEN_BUF_SIZE, 0, false);
        if (piece_len < 0) piece_len = 0;
        if (piece_len >= TOKEN_BUF_SIZE) piece_len = TOKEN_BUF_SIZE - 1;
        piece[piece_len] = '\0';

        /* Append to result */
        if (result_len + piece_len + 1 > result_cap) {
            result_cap *= 2;
            char *new_buf = realloc(result, result_cap);
            if (new_buf == NULL) break;
            result = new_buf;
        }
        memcpy(result + result_len, piece, piece_len);
        result_len += piece_len;
        result[result_len] = '\0';

        /* Stream token via callback */
        if (on_token != NULL) {
            jstring token_str = (*env)->NewStringUTF(env, piece);
            (*env)->CallVoidMethod(env, callback, on_token, token_str);
            (*env)->DeleteLocalRef(env, token_str);

            /* Check for exceptions from callback */
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionClear(env);
                break;
            }
        }

        /* Prepare single-token batch for next decode */
        struct llama_batch next = llama_batch_init(1, 0, 1);
        next.token[0]    = new_token;
        next.pos[0]      = cur_pos;
        next.n_seq_id[0] = 1;
        next.seq_id[0][0] = 0;
        next.logits[0]   = 1;
        next.n_tokens     = 1;

        if (llama_decode(jni_ctx->ctx, next) != 0) {
            LOGE("llama_decode failed during generation");
            llama_batch_free(next);
            break;
        }
        llama_batch_free(next);
        cur_pos++;
    }

    llama_sampler_free(sampler);
    jstring jresult = (*env)->NewStringUTF(env, result);
    free(result);
    return jresult;
}

/* ── Free ─────────────────────────────────────────────────────────── */

JNIEXPORT void JNICALL
Java_io_github_kmpfacelink_llm_internal_LlamaCppBridge_nativeFree(
    JNIEnv *env, jobject thiz, jlong ctx_ptr) {

    llama_jni_context *jni_ctx = (llama_jni_context *)(intptr_t)ctx_ptr;
    if (jni_ctx == NULL) return;

    if (jni_ctx->sampler != NULL) {
        llama_sampler_free(jni_ctx->sampler);
    }
    if (jni_ctx->ctx != NULL) {
        llama_free(jni_ctx->ctx);
    }
    if (jni_ctx->model != NULL) {
        llama_model_free(jni_ctx->model);
    }
    free(jni_ctx);
    LOGI("Model freed");
}

/* ── IsLoaded ─────────────────────────────────────────────────────── */

JNIEXPORT jboolean JNICALL
Java_io_github_kmpfacelink_llm_internal_LlamaCppBridge_nativeIsLoaded(
    JNIEnv *env, jobject thiz, jlong ctx_ptr) {

    llama_jni_context *jni_ctx = (llama_jni_context *)(intptr_t)ctx_ptr;
    return (jni_ctx != NULL && jni_ctx->ctx != NULL) ? JNI_TRUE : JNI_FALSE;
}
