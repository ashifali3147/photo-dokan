plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.tlw.photodokan.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.tlw.photodokan"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        // ONNX Runtime's native library is large: only phones, 64- and 32-bit ARM.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Compressed in the APK, so it stays small enough to send; Android unpacks it on install.
    packaging {
        jniLibs.useLegacyPackaging = true
    }

    buildTypes {
        // And the emulator, while developing.
        debug {
            ndk { abiFilters += "x86_64" }
        }
        release {
            isMinifyEnabled = false
        }
        // Release speed (not debuggable, so Compose runs at full speed) but signed with the debug key,
        // for measuring on a phone: ./gradlew :app-android:installProfile
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(project(":shared:library"))
    implementation(project(":shared:station"))
    implementation(project(":shared:vision"))
    implementation(libs.androidx.activity.compose)
}
