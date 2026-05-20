# KMP-FaceLink networking module consumer ProGuard rules

# Ktor — keep engine and serialization classes
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Coroutines
-dontwarn kotlinx.coroutines.**
