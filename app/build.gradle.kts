plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.user14923929.pocketfoss"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.user14923929.pocketfoss"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing comes from the environment (set by the release workflow); unsigned otherwise.
    val keystorePath: String? = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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

// Intentionally no dependencies: framework APIs only (no AndroidX, no Compose, no Material).
