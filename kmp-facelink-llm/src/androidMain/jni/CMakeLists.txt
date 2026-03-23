cmake_minimum_required(VERSION 3.22)
project(llama_jni)

# llama.cpp source path — set via Gradle or environment
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp"
    CACHE PATH "Path to llama.cpp source directory")

if(NOT EXISTS "${LLAMA_DIR}/include/llama.h")
    message(FATAL_ERROR
        "llama.cpp not found at ${LLAMA_DIR}. "
        "Clone https://github.com/ggml-org/llama.cpp and set LLAMA_DIR.")
endif()

# Disable unnecessary build targets
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Build llama.cpp + ggml via add_subdirectory
add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

# JNI glue library
add_library(llama_jni SHARED llama_jni.c)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
)

target_link_libraries(llama_jni
    llama
    log
)
