#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define TAG "WHISPER_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_whereikept_app_utils_LibWhisper_initContext(JNIEnv *env, jobject thiz, jstring model_path_str) {
    const char *model_path = env->GetStringUTFChars(model_path_str, nullptr);

    LOGI("=== Initializing Whisper Context ===");
    LOGI("Model path: %s", model_path);

    // Initialize the model
    struct whisper_context_params cparams = whisper_context_default_params();
    LOGI("Creating whisper context with default params");

    whisper_context *ctx = whisper_init_from_file_with_params(model_path, cparams);

    env->ReleaseStringUTFChars(model_path_str, model_path);

    if (ctx == nullptr) {
        LOGE("FAILED to load Whisper model!");
        LOGE("Check if model file exists at path");
        return 0;
    }

    LOGI("Whisper context initialized successfully");
    LOGI("Context pointer: %p", ctx);
    LOGI("===================================");

    return (jlong) ctx;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_whereikept_app_utils_LibWhisper_transcribe(JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray audio_buffer) {
    LOGI("=== Starting Transcription ===");

    if (context_ptr == 0) {
        LOGE("ERROR: Context pointer is null!");
        return env->NewStringUTF("");
    }

    whisper_context *w_ctx = (whisper_context *) context_ptr;
    LOGI("Using context pointer: %p", w_ctx);

    // Get audio data from Java
    jsize len = env->GetArrayLength(audio_buffer);
    LOGI("Audio buffer length: %d samples", len);
    LOGI("Audio duration: %.2f seconds", (float)len / 16000.0f);

    jfloat *audio_data = env->GetFloatArrayElements(audio_buffer, nullptr);
    if (audio_data == nullptr) {
        LOGE("ERROR: Failed to get audio data from Java array");
        return env->NewStringUTF("Error: Failed to access audio data");
    }

    // Calculate audio statistics
    float max_val = 0.0f;
    float min_val = 0.0f;
    for (int i = 0; i < len; i++) {
        if (audio_data[i] > max_val) max_val = audio_data[i];
        if (audio_data[i] < min_val) min_val = audio_data[i];
    }
    LOGI("Audio range: [%.4f, %.4f]", min_val, max_val);

    // SETUP PARAMS (The "Fast" Configuration)
    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    LOGI("Configuring Whisper parameters:");
    // Threads: Use 4 threads for a balance of speed vs heat.
    // Using all cores (e.g. 8) often throttles the CPU due to heat.
    wparams.strategy = WHISPER_SAMPLING_GREEDY;
    wparams.n_threads = 4;
    LOGI("  - Threads: %d", wparams.n_threads);
    LOGI("  - Strategy: GREEDY");

    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.print_realtime = false;
    wparams.translate = false;
    wparams.language = "en";
    LOGI("  - Language: %s", wparams.language);
    LOGI("  - Translate: %s", wparams.translate ? "yes" : "no");

    // Performance optimizations
    wparams.no_context = true;
    wparams.single_segment = false;
    LOGI("  - No context: %s", wparams.no_context ? "yes" : "no");
    LOGI("  - Single segment: %s", wparams.single_segment ? "yes" : "no");

    LOGI("Running Whisper inference...");
    long long start_time = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();

    // Run the inference
    int result = whisper_full(w_ctx, wparams, audio_data, len);

    long long end_time = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();
    long long inference_time = end_time - start_time;

    if (result != 0) {
        LOGE("ERROR: Whisper inference failed with code: %d", result);
        env->ReleaseFloatArrayElements(audio_buffer, audio_data, 0);
        return env->NewStringUTF("Error: Inference failed");
    }

    LOGI("Inference completed in %lld ms", inference_time);

    // Collect the results
    std::string transcription = "";
    int n_segments = whisper_full_n_segments(w_ctx);
    LOGI("Number of segments detected: %d", n_segments);

    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(w_ctx, i);
        int64_t t0 = whisper_full_get_segment_t0(w_ctx, i);
        int64_t t1 = whisper_full_get_segment_t1(w_ctx, i);

        LOGI("Segment %d [%lld -> %lld]: %s", i, t0, t1, text);
        transcription += text;
    }

    LOGI("=== Transcription Complete ===");
    LOGI("Total length: %zu characters", transcription.length());
    LOGI("Full text: %s", transcription.c_str());
    LOGI("==============================");

    env->ReleaseFloatArrayElements(audio_buffer, audio_data, 0);
    return env->NewStringUTF(transcription.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_whereikept_app_utils_LibWhisper_freeContext(JNIEnv *env, jobject thiz, jlong context_ptr) {
    LOGI("=== Freeing Whisper Context ===");
    if (context_ptr != 0) {
        whisper_context *ctx = (whisper_context *) context_ptr;
        LOGI("Freeing context pointer: %p", ctx);
        whisper_free(ctx);
        LOGI("Context freed successfully");
    } else {
        LOGW("Context pointer was null, nothing to free");
    }
    LOGI("================================");
}
