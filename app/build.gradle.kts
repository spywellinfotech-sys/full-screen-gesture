plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Build tag: comes from the GitHub Actions run number, so every APK is unique.
val buildNo = System.getenv("GITHUB_RUN_NUMBER") ?: "0"

android {
    namespace = "com.spywell.fullscreengesture"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.spywell.fullscreengesture"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNo.toInt().coerceAtLeast(1)
        versionName = "1.0.0-b$buildNo"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
