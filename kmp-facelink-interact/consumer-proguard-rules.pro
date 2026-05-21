# KMP-FaceLink Interact module consumer ProGuard rules

# Coroutines
-dontwarn kotlinx.coroutines.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.kmpfacelink.interact.**$$serializer { *; }
-keepclassmembers class io.github.kmpfacelink.interact.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.kmpfacelink.interact.** {
    kotlinx.serialization.KSerializer serializer(...);
}
