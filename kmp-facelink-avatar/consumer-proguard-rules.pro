# KMP-FaceLink avatar module consumer ProGuard rules

# Live2D Cubism SDK — JNI bindings
-keep class com.live2d.sdk.cubism.** { *; }
-dontwarn com.live2d.sdk.cubism.**

# Coroutines
-dontwarn kotlinx.coroutines.**
