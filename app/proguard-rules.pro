# Keep JNI methods
-keepclasseswithmembernames class com.aicleaner.engine.ShellEngine {
    native <methods>;
}

# OkHttp platform dependencies (optional TLS providers)
-dontwarn okhttp3.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
