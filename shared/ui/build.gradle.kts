import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            implementation(project(":shared:print"))
            implementation(project(":shared:station"))
            implementation(project(":shared:vision"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.material.icons.core)
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            // The in-app camera; no gallery copy and the same screen on every phone.
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
        }
        val desktopMain by getting {
            dependencies {
                // Dispatchers.Main on desktop, needed by viewModelScope.
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.koin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Runs on a phone or emulator: ./gradlew :shared:ui:connectedDebugAndroidTest
        val androidInstrumentedTest by getting {
            kotlin.srcDir("src/renderParityTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.junit)
            }
        }
        val desktopTest by getting {
            // Shared with the Android instrumented tests: the same render checks on both platforms.
            kotlin.srcDir("src/renderParityTest/kotlin")
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.tlw.photodokan.ui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
