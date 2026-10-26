package com.tlw.photodokan.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.theme.PhotoDokanTheme
import com.tlw.photodokan.ui.theme.StudioTheme
import com.tlw.photodokan.ui.theme.ThemeMode

/** Every shared component on one screen, for checking them by eye in [mode]. */
@Composable
fun ComponentGallery(mode: ThemeMode) = PhotoDokanTheme(mode) {
    val c = StudioTheme.colors
    StudioBackground(Modifier.fillMaxSize()) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(onClick = {}) { Text("Print 15 photos") }
                PrimaryButton(onClick = {}, tone = ButtonTone.Ok) { Text("On the sheet") }
                QuietButton(onClick = {}) { Text("New sheet") }
                PrimaryButton(onClick = {}, enabled = false) { Text("Print") }
                GlassIconButton(onClick = {}, label = "Back") { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                var chosen by remember { mutableStateOf("Fair skin") }
                for ((label, value) in listOf("Light" to "55", "Fair skin" to "40", "Smooth" to "40")) {
                    ValueChip(label, value, selected = label == chosen, onClick = { chosen = label }, modifier = Modifier.width(96.dp))
                }
                var copies by remember { mutableIntStateOf(5) }
                CopyStepper(copies, { copies = it })
                CopyChoices(copies, listOf(4, 5, 10), { copies = it }, Modifier.width(220.dp))
                CustomerNumber("0012")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((colour, label) in listOf(c.textMuted to "to edit", c.ok to "printed", c.warn to "problem", c.accent to "ready")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(colour, glow = colour != c.textMuted)
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text)
                    }
                }
            }
            ToolDock(Modifier.width(420.dp)) {
                var tab by remember { mutableStateOf("Crop") }
                for (name in listOf("Crop", "Look", "Retouch", "Backdrop")) {
                    DockTab(name, selected = tab == name, onClick = { tab = name }) { Box(Modifier.size(22.dp)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Card(Modifier.size(260.dp, 150.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Card", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text("White on warm grey", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                }
                GreyStage(Modifier.size(220.dp, 150.dp)) {
                    Box(Modifier.size(70.dp, 90.dp).background(Color(0xFF3F6FB5)))
                }
                Box(Modifier.size(90.dp, 120.dp).selectionGlow(true).background(Color(0xFF3F6FB5)))
            }
        }
    }
}
