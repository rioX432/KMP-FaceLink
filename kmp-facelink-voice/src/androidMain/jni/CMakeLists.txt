cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

# whisper.cpp source path — set via Gradle or environment
set(WHISPER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper.cpp"
    CACHE PATH "Path to whisper.cpp source directory")

if(NOT EXISTS "${WHISPER_DIR}/whisper.h")
    message(FATAL_ERROR
        "whisper.cpp not found at ${WHISPER_DIR}. "
        "Clone https://github.com/ggerganov/whisper.cpp and set WHISPER_DIR.")
endif()

# Build whisper.cpp as static library
add_library(whisper STATIC
    ${WHISPER_DIR}/whisper.cpp
    ${WHISPER_DIR}/ggml/src/ggml.c
    ${WHISPER_DIR}/ggml/src/ggml-alloc.c
    ${WHISPER_DIR}/ggml/src/ggml-backend.c
    ${WHISPER_DIR}/ggml/src/ggml-quants.c
)

target_include_directories(whisper PUBLIC
    ${WHISPER_DIR}
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
)

target_compile_options(whisper PRIVATE
    -O3
    -DNDEBUG
    -fPIC
)

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
    target_compile_options(whisper PRIVATE -march=armv8.2-a+fp16)
endif()

# JNI glue library
add_library(whisper_jni SHARED whisper_jni.c)

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_DIR}
)

target_link_libraries(whisper_jni
    whisper
    log
)
