package com.tlw.photodokan.ui.photo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tlw.photodokan.ui.components.GlassIconButton
import com.tlw.photodokan.ui.components.PrimaryButton
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.theme.PhotoDokanTheme
import com.tlw.photodokan.ui.theme.StudioTheme
import com.tlw.photodokan.ui.theme.ThemeMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The in-app camera, always dark: the back camera's preview with a face guide, a Close button over
 * it, and one big shutter button.
 * The photo is saved as a JPEG in the app's cache and its path returned in [EXTRA_PATH]; it is
 * filed in the library only when the operator saves the customer, never in the phone's gallery.
 */
class CameraActivity : ComponentActivity() {

    /** Checked again on every return, e.g. from the app's settings after allowing the camera. */
    private var allowed by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        allowed = hasCameraPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // The camera is always dark, whatever the app's theme: nothing bright beside the subject.
            PhotoDokanTheme(ThemeMode.Dark) {
                Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    CameraScreen(onTaken = ::finishWith, onClose = ::finish)
                }
            }
        }
    }

    private fun finishWith(photo: File) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, photo.path))
        finish()
    }

    @Composable
    private fun CameraScreen(onTaken: (File) -> Unit, onClose: () -> Unit) {
        var refused by remember { mutableStateOf(false) }
        val ask = rememberLauncherForActivityResult(RequestPermission()) { granted ->
            allowed = granted
            refused = !granted
        }
        LaunchedEffect(Unit) { if (!hasCameraPermission()) ask.launch(Manifest.permission.CAMERA) }

        when {
            allowed -> Viewfinder(onTaken, onClose)
            refused -> NoPermission(onClose)
        }
    }

    @Composable
    private fun Viewfinder(onTaken: (File) -> Unit, onClose: () -> Unit) {
        // 4:3 for both, and the preview fitted rather than cropped, so the guide frames what is taken.
        val ratio = remember { ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build() }
        val preview = remember { Preview.Builder().setResolutionSelector(ratio).build() }
        val capture = remember {
            ImageCapture.Builder()
                .setResolutionSelector(ratio)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF) // the studio has its own lights
                .build()
        }
        var taking by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        DisposableEffect(Unit) {
            val future = ProcessCameraProvider.getInstance(this@CameraActivity)
            future.addListener({
                val provider = future.get()
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(this@CameraActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                } catch (e: Exception) {
                    Log.w(TAG, "Camera could not be bound", e)
                    error = "The camera could not be started. Close other camera apps and try again."
                }
            }, ContextCompat.getMainExecutor(this@CameraActivity))
            onDispose { if (future.isDone) future.get().unbindAll() }
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { context ->
                        PreviewView(context).apply {
                            // A TextureView sits inside Compose layouts (and screenshots) like any other view.
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                            preview.surfaceProvider = surfaceProvider
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                FaceGuide(Modifier.fillMaxSize())
                GlassIconButton(onClose, "Close camera", Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                }
                Hint(
                    error ?: "Fit the face inside the oval",
                    isError = error != null,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp, start = 24.dp, end = 24.dp),
                )
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                Shutter(taking) {
                    taking = true
                    error = null
                    takePhoto(capture, onSaved = onTaken, onFailed = {
                        taking = false
                        error = "The photo could not be taken. Try again."
                    })
                }
            }
        }
    }

    /** A short line on a dark pill over the preview, so it reads on any scene. */
    @Composable
    private fun Hint(text: String, isError: Boolean, modifier: Modifier) {
        Text(
            text,
            modifier.clip(CircleShape).background(Color(0xCC0F1013)).padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isError) StudioTheme.colors.warn else Color.White,
            style = MaterialTheme.typography.titleSmall,
        )
    }

    /** The shutter: a white ring around a white disc, with a soft light around it. */
    @Composable
    private fun Shutter(taking: Boolean, onClick: () -> Unit) {
        Box(
            Modifier
                .size(84.dp)
                .shadow(if (taking) 0.dp else 18.dp, CircleShape, ambientColor = Color.White, spotColor = Color.White)
                .clip(CircleShape)
                .background(Color.Black)
                .border(4.dp, Color.White, CircleShape)
                .padding(8.dp)
                .background(if (taking) Color.Gray else Color.White, CircleShape)
                .clickable(enabled = !taking, onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription = "Take photo"
                },
            contentAlignment = Alignment.Center,
        ) {
            if (taking) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
        }
    }

    /** An oval where the face goes, about where the editor's face guide expects it. */
    @Composable
    private fun FaceGuide(modifier: Modifier) = Canvas(modifier) {
        val width = minOf(size.width * 0.5f, size.height * 0.38f)
        val height = width * 1.3f
        drawOval(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset((size.width - width) / 2, size.height * 0.42f - height / 2),
            size = Size(width, height),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
        )
    }

    @Composable
    private fun NoPermission(onClose: () -> Unit) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("Photo Dokan needs the camera to take photos.", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("Allow Camera in the app's settings, or close this and choose a photo instead.", color = Color.White)
            PrimaryButton(onClick = {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }) { Text("Open settings") }
            QuietButton(onClick = onClose) { Text("Close") }
        }
    }

    private fun takePhoto(capture: ImageCapture, onSaved: (File) -> Unit, onFailed: () -> Unit) {
        val folder = File(cacheDir, "camera").apply { mkdirs() }
        // Only the latest photo is ever needed: the editor reads it straight away.
        folder.listFiles()?.forEach { it.delete() }
        val file = File(folder, "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved(file)
                override fun onError(exception: ImageCaptureException) {
                    Log.w(TAG, "Photo could not be taken", exception)
                    onFailed()
                }
            },
        )
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_PATH = "com.tlw.photodokan.photo.PATH"
        private const val TAG = "PhotoDokanCamera"
    }
}
