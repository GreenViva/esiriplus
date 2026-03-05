# Retrofit service interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Moshi codegen
-keep @com.squareup.moshi.JsonClass class * { *; }

# kotlinx.serialization DTOs — keep Companion, serializer(), and generated $$serializer
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclassmembers class <2>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keep class <1>$$serializer {
    static <1>$$serializer INSTANCE;
    *** childSerializers(...);
    *** serialize(...);
    *** deserialize(...);
}
# Keep @Serializable class fields for reflection-based serialization
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Supabase / Ktor
-dontwarn io.ktor.**
-keep class io.github.jan.supabase.** { *; }
