plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ru.mobilegateway.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.mobilegateway.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
}
