plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Unit-тесты (pure JVM, JUnit4) — RetryBackoff, AppMessage, HandleIncomingTextUseCase.
    // runBlocking из kotlinx-coroutines-core достаточно для этих тестов, отдельный
    // kotlinx-coroutines-test не подключаем (новых зависимостей без одобрения — нет).
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}
