plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.ramka.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ramka.app"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.findProperty("RAMKA_VERSION_CODE") as String).toInt()
        versionName = project.findProperty("RAMKA_VERSION_NAME") as String
    }

    buildTypes {
        // КРИТИЧЕСКИ ВАЖНО: отключаем обфускацию для debug-сборки!
        // Без этого R8 ломает нативные привязки SQLCipher (ошибка mNativeHandle).
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":crypto"))
    implementation(project(":network"))
    implementation(project(":storage"))
    implementation(project(":data"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WebRTC для звонков. Версия 1.3.10 стабильна и совместима с compileSdk 34.
    implementation("io.getstream:stream-webrtc-android:1.3.10")

    testImplementation("junit:junit:4.13.2")
    // org.json в юнит-тестах: в android.jar он заглушка, а TurnServerCodec пользуется JSONArray/JSONObject.
    testImplementation("org.json:json:20240303")
}