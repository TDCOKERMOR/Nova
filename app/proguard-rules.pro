# ── Nova ProGuard Rules ──────────────────────
# Keep all data model classes used by Gson serialization/deserialization

# Keep Gson models
-keep class com.tdc.aichat.Message$ChatMessage { *; }
-keep class com.tdc.aichat.Message$ApiMessage { *; }
-keep class com.tdc.aichat.Message$ChatRequest { *; }
-keep class com.tdc.aichat.Message$ChatResponse { *; }
-keep class com.tdc.aichat.Message$Choice { *; }
-keep class com.tdc.aichat.Message$AppConfig { *; }
-keep class com.tdc.aichat.Message$ChatStreamChunk { *; }
-keep class com.tdc.aichat.Message$StreamChoice { *; }
-keep class com.tdc.aichat.Message$StreamDelta { *; }
-keep class com.tdc.aichat.Message$ImageGenRequest { *; }
-keep class com.tdc.aichat.Message$ImageGenResponse { *; }
-keep class com.tdc.aichat.Message$ImageData { *; }
-keep class com.tdc.aichat.Message$AltImageResponse { *; }
-keep class com.tdc.aichat.Message$ImageJobResponse { *; }
-keep class com.tdc.aichat.Message$ImageJobError { *; }
-keep class com.tdc.aichat.Message$MultimodalContent { *; }
-keep class com.tdc.aichat.Message$ImageUrl { *; }
-keep class com.tdc.aichat.Message$MultimodalRequest { *; }
-keep class com.tdc.aichat.Message$MultimodalMessage { *; }

# Keep Conversation data model
-keep class com.tdc.aichat.Conversation { *; }

# Keep JsBridge data classes (used by Gson internally)
-keep class com.tdc.aichat.JsBridge$SendMsgData { *; }
-keep class com.tdc.aichat.JsBridge$JsMessage { *; }

# Keep JsBridge public methods (JavascriptInterface)
-keepclassmembers class com.tdc.aichat.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
