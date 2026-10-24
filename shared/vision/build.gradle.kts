import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

/**
 * The vision models, downloaded once when the app is built and checked against their SHA-256,
 * then shipped inside it: the app never downloads anything. Kept in Gradle's cache, not in git.
 */
val models = mapOf(
    // YuNet face detector, OpenCV Zoo, MIT licence.
    "face_detection_yunet_2023mar.onnx" to ModelDownload(
        "https://media.githubusercontent.com/media/opencv/opencv_zoo/f12e12798e8314f7c074a6656816c048dcc95b7a/models/face_detection_yunet/face_detection_yunet_2023mar.onnx",
        "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
    ),
    // MODNet portrait matting (ZHKKKe/MODNet), ONNX export by Xenova, Apache-2.0 licence.
    "modnet.onnx" to ModelDownload(
        "https://huggingface.co/Xenova/modnet/resolve/fa2fa546052fba4c08921230a26cc69a333fca12/onnx/model.onnx",
        "07c308cf0fc7e6e8b2065a12ed7fc07e1de8febb7dc7839d7b7f15dd66584df9",
    ),
)

data class ModelDownload(val url: String, val sha256: String) : java.io.Serializable

/** Puts each model in [outputDir]/models, downloading it into [cacheDir] the first time. */
abstract class DownloadModels : DefaultTask() {
    @get:Input
    abstract val models: MapProperty<String, ModelDownload>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val target = outputDir.get().asFile.resolve("models").apply { deleteRecursively(); mkdirs() }
        val cache = cacheDir.get().asFile.apply { mkdirs() }
        models.get().forEach { (fileName, model) ->
            val cached = cache.resolve("${model.sha256}-$fileName")
            if (!cached.exists() || sha256(cached) != model.sha256) {
                logger.lifecycle("Downloading $fileName")
                val partial = cache.resolve("$fileName.part")
                try {
                    URI(model.url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
                } catch (e: Exception) {
                    throw GradleException("Could not download $fileName from ${model.url}. Connect to the internet once, or put the file at $cached.", e)
                }
                val actual = sha256(partial)
                if (actual != model.sha256) {
                    partial.delete()
                    throw GradleException("$fileName downloaded from ${model.url} is not the expected file (SHA-256 $actual).")
                }
                partial.renameTo(cached)
            }
            cached.copyTo(target.resolve(fileName))
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val downloadModels = tasks.register<DownloadModels>("downloadModels") {
    models.set(this@Build_gradle.models)
    cacheDir.set(File(gradle.gradleUserHomeDir, "caches/photo-dokan/models"))
    outputDir.set(layout.buildDirectory.dir("generated/visionModels"))
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // The same ONNX Runtime Java API on the phone and the PC.
        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(libs.onnxruntime.android)
            }
        }
        val desktopMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
            // The models ship inside the app's jar.
            resources.srcDir(downloadModels)
            dependencies {
                implementation(libs.onnxruntime)
            }
        }
        // What every platform's models must do: load and run on this machine.
        val desktopTest by getting {
            kotlin.srcDir("src/modelsTest/kotlin")
        }
        // Runs on a phone or emulator: ./gradlew :shared:vision:connectedDebugAndroidTest
        val androidInstrumentedTest by getting {
            kotlin.srcDir("src/modelsTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.junit)
            }
        }
    }
}

android {
    namespace = "com.tlw.photodokan.vision"
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

// The models ship inside the app as assets.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadModels, DownloadModels::outputDir)
    }
}
