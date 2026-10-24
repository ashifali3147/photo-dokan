package com.tlw.photodokan.ui.station

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tlw.photodokan.station.PairingInvite
import com.tlw.photodokan.station.QrCodes
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scans the PC's pairing QR with the back camera and returns its link in [EXTRA_TEXT]. Other QR
 * codes in view (a Wi-Fi card, a menu) are ignored. Nothing is saved: frames are read and dropped.
 */
class QrScanActivity : ComponentActivity() {

    private var allowed by mutableStateOf(false)
    private val analysis = Executors.newSingleThreadExecutor()
    private val found = AtomicBoolean(false)

    override fun onResume() {
        super.onResume()
        allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        analysis.shutdown()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    var refused by remember { mutableStateOf(false) }
                    val ask = rememberLauncherForActivityResult(RequestPermission()) { granted ->
                        allowed = granted
                        refused = !granted
                    }
                    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) { if (allowed) Viewfinder() }
                        Text(
                            if (refused) "Photo Dokan needs the camera to scan the code. Allow it in the app's settings." else "Point the camera at the code on the PC's screen.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            TextButton(onClick = ::finish) { Text("Close", color = Color.White) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Viewfinder() {
        val preview = remember { Preview.Builder().build() }
        val reader = remember {
            ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
                setAnalyzer(analysis) { frame -> frame.use { read(it) } }
            }
        }
        DisposableEffect(Unit) {
            val future = ProcessCameraProvider.getInstance(this@QrScanActivity)
            future.addListener({
                try {
                    future.get().run {
                        unbindAll()
                        bindToLifecycle(this@QrScanActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, reader)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Camera could not be bound", e)
                }
            }, ContextCompat.getMainExecutor(this@QrScanActivity))
            onDispose { if (future.isDone) future.get().unbindAll() }
        }
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    preview.surfaceProvider = surfaceProvider
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    /** Reads the grey (Y) plane of [frame]; a pairing link ends the scan. */
    private fun read(frame: ImageProxy) {
        if (found.get()) return
        val plane = frame.planes[0]
        val width = frame.width
        val height = frame.height
        val buffer = plane.buffer
        // Rows may be padded; copy them side by side for the reader.
        val pixels = ByteArray(width * height)
        for (y in 0 until height) {
            buffer.position(y * plane.rowStride)
            buffer.get(pixels, y * width, width)
        }
        val text = QrCodes.decode(pixels, width, height) ?: return
        if (PairingInvite.parse(text) == null || !found.compareAndSet(false, true)) return
        runOnUiThread {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
            finish()
        }
    }

    companion object {
        const val EXTRA_TEXT = "com.tlw.photodokan.station.TEXT"
        private const val TAG = "PhotoDokanQr"
    }
}

@Composable
actual fun rememberQrScanner(onScanned: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onScanned)
    val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        result.data?.getStringExtra(QrScanActivity.EXTRA_TEXT)?.takeIf { result.resultCode == Activity.RESULT_OK }?.let(callback)
    }
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return null
    return { launcher.launch(Intent(context, QrScanActivity::class.java)) }
}
