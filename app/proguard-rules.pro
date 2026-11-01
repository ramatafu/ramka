# ==========================================
# ПРАВИЛА ОБФУСКАЦИИ (proguard-rules.pro)
# ==========================================

# 1. Базовые настройки для аннотаций (страховка для Room/Hilt/kotlinx.serialization)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# 2. Правила для Tink (androidx.security:security-crypto):
# Tink ссылается на compile-only библиотеки аннотаций, которые отсутствуют в рантайме.
# Без этих строк R8 падает с "Missing classes detected while running R8".
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# 3. КРИТИЧЕСКИ ВАЖНО для SQLCipher:
# Предотвращает обфускацию нативных полей, которая вызывает падение:
# "NoSuchFieldError: no "J" field "mNativeHandle" in class Lnet/sqlcipher/database/SQLiteDatabase;"
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# 4. WebRTC (stream-webrtc-android):
# Нативный код вызывает Java-классы org.webrtc через JNI по именам.
# R8 без этого правила переименует/удалит их в release-сборке.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# 5. Yggmail (JavaMail, com.sun.mail:android-mail):
# провайдеры протоколов и обработчики MIME создаются по именам из META-INF/javamail.* и mailcap, R8 их не видит.
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.activation.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn javax.naming.**
-dontwarn org.apache.harmony.**
