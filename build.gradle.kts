// Versions choisies pour rester compatibles avec Gradle 8.4, qui est la version
// installee par la CI (gradle/actions/setup-gradle@v3). AGP 8.2.2 exige Gradle
// 8.2 minimum ; Kotlin 1.9.22 va de pair avec le compilateur Compose 1.5.10.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
