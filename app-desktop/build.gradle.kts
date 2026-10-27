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
    implementation(project(":shared:station"))
    implementation(project(":shared:vision"))
    implementation(compose.desktop.currentOs)
    // The title bar follows the app's dark or light theme on Windows.
    implementation(libs.jna)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.tlw.photodokan.desktop.MainKt"
        jvmArgs += listOf("-Xmx1536m")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Photo Dokan"
            packageVersion = providers.gradleProperty("photodokan.version").get()
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

// Checks a folder of photos the way the editor opens them, and writes a quality report:
// ./gradlew :app-desktop:qualityReport -Pphotos=/path/to/photos [-Preport=/path/to/report]
tasks.register<JavaExec>("qualityReport") {
    group = "verification"
    description = "Writes a quality report for a folder of photos (-Pphotos=..., optional -Preport=...)."
    mainClass.set("com.tlw.photodokan.desktop.quality.QualityMainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs("-Xmx1536m", "-Djava.awt.headless=true")
    args = listOfNotNull(findProperty("photos")?.toString(), findProperty("report")?.toString())
}
