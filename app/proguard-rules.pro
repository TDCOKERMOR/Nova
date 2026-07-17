# ── Nova AI Chat ProGuard Rules ─────────────────────────

# Keep data classes used by Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile, LineNumberTable

# Gson specific
-keep class com.tdc.aichat.Message$ChatMessage { *; }
-keep class com.tdc.aichat.Message$ChatMessage$Companion { *; }
-keep class com.tdc.aichat.ChatMessage { *; }
-keep class com.tdc.aichat.Conversation { *; }
-keep class com.tdc.aichat.AppConfig { *; }
-keep class com.tdc.aichat.ApiMessage { *; }
-keep class com.tdc.aichat.ChatRequest { *; }
-keep class com.tdc.aichat.ChatResponse { *; }
-keep class com.tdc.aichat.Choice { *; }
-keep class com.tdc.aichat.ChatStreamChunk { *; }
-keep class com.tdc.aichat.StreamChoice { *; }
-keep class com.tdc.aichat.StreamDelta { *; }
-keep class com.tdc.aichat.ImageGenRequest { *; }
-keep class com.tdc.aichat.ImageGenResponse { *; }
-keep class com.tdc.aichat.ImageData { *; }
-keep class com.tdc.aichat.AltImageResponse { *; }
-keep class com.tdc.aichat.ImageJobResponse { *; }
-keep class com.tdc.aichat.ImageJobError { *; }
-keep class com.tdc.aichat.MultimodalContent { *; }
-keep class com.tdc.aichat.ImageUrl { *; }
-keep class com.tdc.aichat.MultimodalRequest { *; }
-keep class com.tdc.aichat.MultimodalMessage { *; }

# JsBridge — keep @JavascriptInterface methods
-keepclassmembers class com.tdc.aichat.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.tdc.aichat.JsBridge$SendMsgData { *; }
-keep class com.tdc.aichat.JsBridge$JsMessage { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Encryption
-keep class androidx.security.crypto.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
