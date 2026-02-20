# MediaPipe — keep model task classes loaded at runtime
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Ktor — keep engine and serialization classes
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# kotlinx-serialization — keep @Serializable classes and generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.kmpfacelink.**$$serializer { *; }
-keepclassmembers class io.github.kmpfacelink.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.kmpfacelink.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Live2D Cubism SDK — JNI bindings
-keep class com.live2d.sdk.cubism.** { *; }
-dontwarn com.live2d.sdk.cubism.**

# Coroutines
-dontwarn kotlinx.coroutines.**
