plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 has built-in Kotlin; the org.jetbrains.kotlin.android plugin must NOT be applied.
    alias(libs.plugins.kotlin.compose) apply false
}
