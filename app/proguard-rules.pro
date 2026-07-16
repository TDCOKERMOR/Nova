# Default ProGuard rules for AI-Chat
-keepattributes SourceFile,LineNumberTable,*Annotation*
-keep class com.tdc.aichat.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
