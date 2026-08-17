// Real JNI bridge into llama.cpp's public C API (include/llama.h, fetched
// for real at build time - see CMakeLists.txt). No mocked/stubbed model
// behaviour anywhere in this file: every function below either calls into
// the real llama.cpp API or returns an honest error/false. There is no
// fallback path that fabricates a fake generated answer.
//
// Lifecycle mirrors llama.cpp's own examples/simple/simple.cpp:
//   llama_backend_init() -> llama_model_load_from_file() ->
//   llama_init_from_model() -> [per message: tokenize -> llama_decode loop
//   -> llama_sampler_sample per step] -> llama_free() / llama_model_free()
//   on unload.

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstring>

#include "llama.h"

#define LOG_TAG "BrainLlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_engine_mutex;
llama_model *g_model = nullptr;
llama_context *g_ctx = nullptr;
const llama_vocab *g_vocab = nullptr;
bool g_backend_initialized = false;
int g_n_ctx = 0;

// Frees whatever is currently loaded. Caller must hold g_engine_mutex.
void unload_locked() {
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
    g_n_ctx = 0;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_brain_offlineai_engine_BrainNative_nativeBackendInit(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_backend_initialized) {
        ggml_backend_load_all();
        llama_backend_init();
        g_backend_initialized = true;
        LOGI("llama backend initialized");
    }
    return JNI_TRUE;
}

// Loads a real GGUF model file from disk. Returns true only if
// llama_model_load_from_file and llama_init_from_model both succeed -
// any failure is surfaced honestly to Kotlin as false, never papered over.
JNIEXPORT jboolean JNICALL
Java_com_brain_offlineai_engine_BrainNative_nativeLoadModel(
        JNIEnv *env, jobject /* thiz */, jstring modelPath, jint nCtx, jint nThreads) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    std::string pathStr(path);
    env->ReleaseStringUTFChars(modelPath, path);

    unload_locked();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU-only: correct default for phones
                                    // without a verified GPU backend build.

    LOGI("Loading model from %s", pathStr.c_str());
    g_model = llama_model_load_from_file(pathStr.c_str(), model_params);
    if (g_model == nullptr) {
        LOGE("llama_model_load_from_file failed for %s", pathStr.c_str());
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(nCtx > 0 ? nCtx : 2048);
    ctx_params.n_batch = ctx_params.n_ctx;
    ctx_params.n_threads = nThreads > 0 ? nThreads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        LOGE("llama_init_from_model failed");
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    g_n_ctx = static_cast<int>(ctx_params.n_ctx);
    LOGI("Model loaded. n_ctx=%d n_threads=%d", g_n_ctx, ctx_params.n_threads);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_brain_offlineai_engine_BrainNative_nativeUnloadModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    unload_locked();
    LOGI("Model unloaded");
}

JNIEXPORT jboolean JNICALL
Java_com_brain_offlineai_engine_BrainNative_nativeIsModelLoaded(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_brain_offlineai_engine_BrainNative_nativeGetContextSize(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    return g_n_ctx;
}

// Streams real generated tokens one at a time into a Kotlin callback object
// (interface BrainNative.TokenCallback, method `onToken(String): Boolean`).
// Generation stops when: the model emits a real end-of-generation token,
// maxTokens is reached, the context fills up, or the Kotlin side returns
// false from onToken (user cancelled). Returns the real stop reason as a
// short string so the Kotlin layer never has to guess.
JNIEXPORT jstring JNICALL
Java_com_brain_offlineai_engine_BrainNative_nativeGenerate(
        JNIEnv *env, jobject /* thiz */, jstring prompt, jint maxTokens,
        jfloat temperature, jfloat topP, jobject callback) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);

    if (g_model == nullptr || g_ctx == nullptr || g_vocab == nullptr) {
        return env->NewStringUTF("error: no model loaded");
    }

    const char *promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptChars);
    env->ReleaseStringUTFChars(prompt, promptChars);

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");
    if (onTokenMethod == nullptr) {
        return env->NewStringUTF("error: callback missing onToken(String):Boolean");
    }

    // --- Tokenize the prompt (real llama_tokenize call) ---
    const bool add_bos = true;
    int n_prompt_tokens = -llama_tokenize(
            g_vocab, promptStr.c_str(), static_cast<int32_t>(promptStr.size()),
            nullptr, 0, add_bos, true);
    if (n_prompt_tokens <= 0) {
        return env->NewStringUTF("error: tokenization failed");
    }
    std::vector<llama_token> prompt_tokens(n_prompt_tokens);
    if (llama_tokenize(g_vocab, promptStr.c_str(), static_cast<int32_t>(promptStr.size()),
                        prompt_tokens.data(), n_prompt_tokens, add_bos, true) < 0) {
        return env->NewStringUTF("error: tokenization failed");
    }

    if (n_prompt_tokens >= g_n_ctx) {
        return env->NewStringUTF("error: prompt longer than context window");
    }

    // --- Build a real sampler chain (temperature -> top-p -> distribution) ---
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP > 0 ? topP : 0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature > 0 ? temperature : 0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // --- Feed the prompt through decode ---
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()));
    std::string stopReason = "max_tokens";

    if (llama_decode(g_ctx, batch) != 0) {
        llama_sampler_free(sampler);
        return env->NewStringUTF("error: llama_decode failed on prompt");
    }

    int n_generated = 0;
    int n_cur = n_prompt_tokens;
    const int limit = maxTokens > 0 ? maxTokens : 512;
    char piece_buf[256];

    while (n_generated < limit) {
        llama_token new_token = llama_sampler_sample(sampler, g_ctx, -1);

        if (llama_vocab_is_eog(g_vocab, new_token)) {
            stopReason = "end_of_generation";
            break;
        }

        int piece_len = llama_token_to_piece(g_vocab, new_token, piece_buf, sizeof(piece_buf), 0, true);
        if (piece_len < 0) {
            stopReason = "error: token_to_piece failed";
            break;
        }
        std::string piece(piece_buf, piece_len);

        jstring jpiece = env->NewStringUTF(piece.c_str());
        jboolean keepGoing = env->CallBooleanMethod(callback, onTokenMethod, jpiece);
        env->DeleteLocalRef(jpiece);
        n_generated++;

        if (!keepGoing) {
            stopReason = "cancelled";
            break;
        }

        if (n_cur >= g_n_ctx - 1) {
            stopReason = "context_full";
            break;
        }

        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, next_batch) != 0) {
            stopReason = "error: llama_decode failed mid-generation";
            break;
        }
        n_cur++;
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(stopReason.c_str());
}

} // extern "C"
