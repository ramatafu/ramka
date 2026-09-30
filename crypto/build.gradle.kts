plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ramka.crypto"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":domain"))
    // Примитивы X25519 / Ed25519 / ChaCha20-Poly1305 / HKDF / Argon2 —
    // никакой собственной криптографии, только вызовы проверенной библиотеки.
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Unit-тесты (src/test) — pure JVM, JUnit4. Тесты используют только
    // MessageCipher/SessionCipher/IkHandshake/KeyFingerprint и in-memory
    // KeyValueStore (см. src/test/.../keys/InMemoryKeyValueStore.kt) — ни один
    // тест не обращается к SecureKeyStore/Context/EncryptedSharedPreferences,
    // поэтому Android SDK/Robolectric для их запуска не нужен.
    testImplementation("junit:junit:4.13.2")
}
