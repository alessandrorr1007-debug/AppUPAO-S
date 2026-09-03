plugins {
    alias(libs.plugins.android.application) apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}