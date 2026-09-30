# Правила по умолчанию. Дополнить при появлении конкретных проблем обфускации
# (Room/Hilt/kotlinx.serialization обычно приносят свои consumer-rules автоматически).
-keepattributes *Annotation*

# androidx.security:security-crypto тянет com.google.crypto.tink (Tink), который, в свою
# очередь, ссылается на несколько compile-only библиотек аннотаций (errorprone, j2objc,
# checker-framework, javax.annotation) — они нужны только статическому анализу самого
# Tink, в рантайме не участвуют и физически отсутствуют на classpath приложения. Без
# этих строк R8 (начиная с включённого по умолчанию android.r8.failOnMissingClasses)
# падает с "Missing classes detected while running R8" на KeysetManager и других
# классах Tink. Правила ниже — стандартный, задокументированный самим R8-проектом
# набор именно для этого случая (см. src/main/dontwarn.txt в исходниках R8), НЕ общее
# "выключить проверку на всё": каждый пакет — конкретная compile-only аннотация.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
