# ── Nova ProGuard Rules ──────────────────────
# All data classes in Message.kt are top-level (not nested), so they compile to
# com.tdc.aichat.ChatMessage etc — NOT Message$ChatMessage.
# This was a critical bug in v5.2: release builds silently corrupted all API
# communication because Gson could not deserialize obfuscated model classes.

# ── Top-level data models (Message.kt) ──────
-keep class com.tdc.aichat.ChatMessage { *; }
-keep class com.tdc.aichat.ApiMessage { *; }
-keep class com.tdc.aichat.ChatRequest { *; }
-keep class com.tdc.aichat.ChatResponse { *; }
-keep class com.tdc.aichat.Choice { *; }
-keep class com.tdc.aichat.AppConfig { *; }
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

# ── Top-level data models (Conversation.kt) ─
-keep class com.tdc.aichat.Conversation { *; }

# ── Nested data classes (inside JsBridge.kt) ─
-keep class com.tdc.aichat.JsBridge$SendMsgData { *; }
-keep class com.tdc.aichat.JsBridge$JsMessage { *; }

# ── JsBridge @JavascriptInterface methods ───
-keepclassmembers class com.tdc.aichat.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Gson ───────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── OkHttp ─────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── Coroutines ────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
