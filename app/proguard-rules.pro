# Базовые настройки
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Tink (androidx.security:security-crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# SQLCipher (защита от mNativeHandle)
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# WebRTC (защита нативных вызовов)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**