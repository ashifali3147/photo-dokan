import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
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
            api(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest {
            // What every LibraryFiles must do; the phone's version runs it on a device too.
            kotlin.srcDir("src/filesContractTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        // Runs on a phone or emulator: ./gradlew :shared:library:connectedDebugAndroidTest
        val androidInstrumentedTest by getting {
            kotlin.srcDir("src/filesContractTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.junit)
            }
        }
    }
}

android {
    namespace = "com.tlw.photodokan.library"
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
