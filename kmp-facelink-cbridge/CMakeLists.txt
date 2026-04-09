# Shared C bridge for KMP-FaceLink.
#
# This CMakeLists.txt is used by consumers that compile the C bridge as part of
# their build (e.g., Flutter build hooks). Unity and React Native on iOS compile
# the .m file directly via Xcode / CocoaPods instead.
#
# Usage (iOS):
#   The .m file is compiled as part of the host Xcode project (not via CMake).
#   This CMakeLists.txt is provided for reference and for Flutter's build hooks.

cmake_minimum_required(VERSION 3.20)
project(kmpfacelink_bridge C)

set(CMAKE_C_STANDARD 11)

# iOS-only: compile the ObjC implementation
if(APPLE)
    add_library(kmpfacelink_bridge STATIC
        src/kmpfacelink_bridge_ios.m
    )
    target_include_directories(kmpfacelink_bridge PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
    )
    target_link_libraries(kmpfacelink_bridge PRIVATE
        "-framework Foundation"
    )
endif()
