package com.tlw.photodokan.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackingEngine
import com.tlw.photodokan.ui.SheetPreview
import com.tlw.photodokan.ui.photo.PhotoPreview
import com.tlw.photodokan.ui.photo.PhotoSource
import com.tlw.photodokan.ui.photo.canDropPhotos
import com.tlw.photodokan.ui.photo.decodePreview
import com.tlw.photodokan.ui.photo.photoDropTarget
import com.tlw.photodokan.ui.photo.rememberPhotoPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
private fun Screen(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
    }
}

/** For now: import one photo and show it upright. Filing it under a customer comes with the library task. */
@Composable
fun CustomersScreen(modifier: Modifier = Modifier) = Screen("Customers", modifier) {
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Pair<String, PhotoPreview>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hovering by remember { mutableStateOf(false) }

    fun import(source: PhotoSource) {
        scope.launch {
            loading = true
            error = null
            val preview = withContext(Dispatchers.Default) { runCatching { decodePreview(source.read()) }.getOrNull() }
            loading = false
            if (preview == null) {
                error = "\"${source.name}\" is not a photo Photo Dokan can open. Choose a JPEG or PNG photo."
            } else {
                photo = source.name to preview
            }
        }
    }
    val pickPhoto = rememberPhotoPicker(::import)

    val borderColor = if (hovering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .border(if (hovering) 3.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .photoDropTarget(onHoverChange = { hovering = it }, onDrop = ::import)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val current = photo
        when {
            loading -> CircularProgressIndicator()
            current != null -> Image(
                bitmap = current.second.image,
                contentDescription = current.first,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Text(
                if (canDropPhotos) "Drop a photo here, or choose one below." else "Choose a photo to start.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    photo?.let { (name, preview) ->
        Text("$name · ${preview.originalWidth} × ${preview.originalHeight} px", style = MaterialTheme.typography.bodyMedium)
    }
    Button(onClick = pickPhoto, enabled = !loading) {
        Text(if (photo == null) "Choose photo" else "Choose another photo")
    }
}

/** For now shows an example packed sheet; the order queue comes with the sheet screen task. */
@Composable
fun PrintScreen(modifier: Modifier = Modifier) = Screen("Print", modifier) {
    val template = koinInject<SheetTemplate>()
    val example = remember(template) {
        PackingEngine.pack(
            template,
            SheetState.FRESH,
            listOf(
                Order("0001", "Customer 0001", 5),
                Order("0002", "Customer 0002", 10),
                Order("0003", "Customer 0003", 4),
                Order("0004", "Customer 0004", 5),
            ),
        )
    }
    Text(
        "Example: ${example.copiesPlaced} photos for 4 customers on one ${template.name} sheet",
        style = MaterialTheme.typography.bodyMedium,
    )
    SheetPreview(template = template, sheet = example.sheets.first(), modifier = Modifier.weight(1f))
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) = Screen("Settings", modifier) {
    Text("Printer, photo sizes and the photo library folder will be set here.", style = MaterialTheme.typography.bodyLarge)
}
