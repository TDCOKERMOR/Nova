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
-keep class com.tdc.aichat.ImageJobResponse { *; }
-keep class com.tdc.aichat.ImageJobError { *; }
-keep class com.tdc.aichat.MultimodalContent { *; }
-keep class com.tdc.aichat.ImageUrl { *; }
-keep class com.tdc.aichat.MultimodalRequest { *; }
-keep class com.tdc.aichat.MultimodalMessage { *; }

# ── Top-level data models (Conversation.kt) ─
-keep class com.tdc.aichat.Conversation { *; }

# ── Nested data classes (inside ChatBridgeHandler.kt, relocated in v5.4) ─
-keep class com.tdc.aichat.ChatBridgeHandler$SendMsgData { *; }
-keep class com.tdc.aichat.ChatBridgeHandler$JsMessage { *; }

# ── ImageBridgeHandler nested classes ───────
-keep class com.tdc.aichat.ImageBridgeHandler$ImageGenData { *; }
-keep class com.tdc.aichat.ImageBridgeHandler$ImageGenResult { *; }

# ── ConfigManager nested classes ────────────
-keep class com.tdc.aichat.ConfigManager$Config { *; }

# ── ConversationManager nested classes ──────
-keep class com.tdc.aichat.ConversationManager$ConversationItem { *; }

# ── JsBridge @JavascriptInterface methods ───
-keepclassmembers class com.tdc.aichat.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Gson ───────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
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

# ── AndroidX Security Crypto ───────────────
-dontwarn androidx.security.crypto.**
-keep class androidx.security.crypto.** { *; }

# ── Kotlinx Coroutines ─────────────────────
-keepnames class kotlinx.coroutines.** { *; }

# ── Material Components ────────────────────
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ── AndroidX Lifecycle ─────────────────────
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ── AndroidX Activity ──────────────────────
-keep class androidx.activity.** { *; }
-dontwarn androidx.activity.**

# ── Preserve generic type information for Gson ───
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations

# ── Keep Parcelable implementations ─────────
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Keep Serializable classes ───────────────
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── Keep Enum values ───────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Keep annotation classes ────────────────
-keep @interface *

# ── Keep WebView JavaScript interface ──────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
