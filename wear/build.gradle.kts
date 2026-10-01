plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 incluye Kotlin; esta línea solo fija la versión de Kotlin usada.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
