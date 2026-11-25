plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// GeckoView 145+ requires Android 8 (API 26). The newest release below 145 still supports Android 5.0+ (API 21).
val geckoVersion = providers.gradleProperty("geckoVersion").getOrElse("[142.0,145.0)")
val geckoArtifact = providers.gradleProperty("geckoArtifact").getOrElse("geckoview")
val abis = providers.gradleProperty("abis").getOrElse("armeabi-v7a,arm64-v8a").split(",").map { it.trim() }

android {
    namespace = "com.swift.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.swift.browser"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += abis }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    // Default pattern drops folders starting with "_" (that would delete "_locales" of the built-in extensions)
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("org.mozilla.geckoview:$geckoArtifact:$geckoVersion")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
