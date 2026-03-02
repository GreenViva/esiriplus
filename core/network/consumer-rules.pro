# Retrofit service interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Moshi codegen
-keep @com.squareup.moshi.JsonClass class * { *; }

# kotlinx.serialization DTOs
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Supabase / Ktor
-dontwarn io.ktor.**
-keep class io.github.jan.supabase.** { *; }
