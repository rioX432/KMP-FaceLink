# KMP-FaceLink core module consumer ProGuard rules

# MediaPipe — keep model task classes loaded at runtime
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Coroutines
-dontwarn kotlinx.coroutines.**
