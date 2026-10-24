import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        // What the phone and the PC agree on: the port, the paths and the messages.
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // The phone's client is plain java.net; the PC's tests use it to talk to the real station.
        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                // Pairing QR codes: drawn on the PC, read by the phone's camera. Pure Java, offline.
                implementation(libs.zxing.core)
            }
        }
        // The Print Station itself runs only on the studio PC.
        val desktopMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(libs.zxing.core)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.network.tls.certificates)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.ktor.server.test.host)
            }
        }
    }
}

android {
    namespace = "com.tlw.photodokan.station"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
