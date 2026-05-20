# KMP-FaceLink LLM module consumer ProGuard rules

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

# Coroutines
-dontwarn kotlinx.coroutines.**
