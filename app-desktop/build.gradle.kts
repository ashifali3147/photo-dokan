import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(project(":shared:library"))
    implementation(project(":shared:print"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.tlw.photodokan.desktop.MainKt"
        jvmArgs += listOf("-Xmx1536m")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Photo Dokan"
            packageVersion = "1.0.0"
            description = "Passport and stamp photo studio software"
            vendor = "Photo Dokan"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                // Keep this fixed forever so new versions upgrade the installed app.
                upgradeUuid = "6f3a7f0e-3c1b-4d2e-9a51-8b7c2d4e1f90"
            }
        }
    }
}
