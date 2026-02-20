/**
 * JNI bridge for whisper.cpp.
 *
 * Wraps whisper_init_from_file, whisper_full, and whisper_free
 * for use from Kotlin/JVM via WhisperCppBridge.
 */

#include <jni.h>
#include <string.h>
#include "whisper.h"

#define PACKAGE "io/github/kmpfacelink/voice/asr/internal"

JNIEXPORT jlong JNICALL
Java_io_github_kmpfacelink_voice_asr_internal_WhisperCppBridge_nativeInit(
    JNIEnv *env, jobject thiz, jstring model_path) {

    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    struct whisper_context *ctx = whisper_init_from_file(path);
    (*env)->ReleaseStringUTFChars(env, model_path, path);

    return (jlong)ctx;
}

JNIEXPORT jstring JNICALL
Java_io_github_kmpfacelink_voice_asr_internal_WhisperCppBridge_nativeTranscribe(
    JNIEnv *env, jobject thiz, jlong ctx_ptr, jfloatArray samples,
    jstring language, jint threads) {

    struct whisper_context *ctx = (struct whisper_context *)ctx_ptr;
    if (ctx == NULL) {
        return (*env)->NewStringUTF(env, "");
    }

    jfloat *data = (*env)->GetFloatArrayElements(env, samples, NULL);
    jsize n_samples = (*env)->GetArrayLength(env, samples);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = 0;
    params.print_timestamps = 0;
    params.n_threads = threads;
    params.single_segment = 1;

    if (language != NULL) {
        const char *lang = (*env)->GetStringUTFChars(env, language, NULL);
        params.language = lang;
        /* Note: lang pointer must remain valid during whisper_full */
    }

    whisper_full(ctx, params, data, n_samples);
    (*env)->ReleaseFloatArrayElements(env, samples, data, JNI_ABORT);

    /* Collect all segments into one string */
    int n_segments = whisper_full_n_segments(ctx);
    char result[4096] = "";
    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        strncat(result, text, sizeof(result) - strlen(result) - 1);
    }

    return (*env)->NewStringUTF(env, result);
}

JNIEXPORT void JNICALL
Java_io_github_kmpfacelink_voice_asr_internal_WhisperCppBridge_nativeFree(
    JNIEnv *env, jobject thiz, jlong ctx_ptr) {

    struct whisper_context *ctx = (struct whisper_context *)ctx_ptr;
    if (ctx != NULL) {
        whisper_free(ctx);
    }
}
