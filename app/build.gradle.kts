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
        // Единственный источник правды для версии — gradle.properties
        // (RAMKA_VERSION_CODE/RAMKA_VERSION_NAME), чтобы не редактировать build-скрипт
        // на каждый релиз.
        versionCode = (project.findProperty("RAMKA_VERSION_CODE") as String).toInt()
        versionName = project.findProperty("RAMKA_VERSION_NAME") as String
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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

    // Bouncy Castle (bcprov-jdk18on) — multi-release jar, публикует OSGi-манифест под
    // META-INF/versions/9/; при наличии более чем одного bc*-jdk18on артефакта на
    // classpath (сейчас только bcprov, но это защита на будущее) Gradle находит два
    // одинаковых пути и падает на merge — сам файл на работу приложения не влияет,
    // это метаданные OSGi-бандла, безопасно исключить один из дублей.
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

    // ЭТАП B.2 — периодический sweep очереди недоставленных сообщений, не чаще
    // раза в 15 минут (ровно минимальный интервал, который платформа разрешает
    // для PeriodicWorkRequest — совпадение неслучайное, тут и рассчитано).
    // Единственная новая зависимость в этом этапе, названа в тексте задачи явно.
    // ВЕРСИЯ ЗАФИКСИРОВАНА НА 2.9.1, А НЕ ПОСЛЕДНЕЙ: начиная с 2.10.0 сам WorkManager
    // собирается под compileSdk 35 и по правилам AAR-метаданных требует того же от
    // потребителя — а весь проект сидит на compileSdk 34/AGP 8.5.2. 2.9.1 — последний
    // стабильный патч, всё ещё совместимый с текущим compileSdk, без необходимости
    // поднимать AGP и рисковать совместимостью остальных модулей ради одной задачи.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Unit-тесты (pure JVM, JUnit4) чистой логики: OemDetector, BackgroundDeliveryController.
    // Android SDK/Robolectric не нужны — тесты не трогают android.* классы.
    testImplementation("junit:junit:4.13.2")
}
