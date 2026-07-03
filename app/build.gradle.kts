plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hermes.studio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hermes.studio"
        minSdk = 24
        targetSdk = 34
        versionCode = 100
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.webkit:webkit:1.9.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // WorkManager for periodic notification checks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Kotlin coroutines for WorkManager
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
