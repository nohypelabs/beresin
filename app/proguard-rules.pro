# Keep JNI methods
-keepclasseswithmembernames class com.aicleaner.engine.ShellEngine {
    native <methods>;
}

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep JSON parsing
-keep class org.json.** { *; }
