package com.tlw.photodokan.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
                SafelightButton(onClick = {}) { Text("Print"); Text("×15", style = StudioTheme.type.number) }
                GoButton(onClick = {}) { Text("Add to sheet") }
                QuietButton(onClick = {}) { Text("Save as PDF") }
                SafelightButton(onClick = {}, enabled = false) { Text("Print") }
                GlassIconButton(onClick = {}, label = "Back") { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                var chosen by remember { mutableStateOf("Fair skin") }
                for ((label, value) in listOf("Light" to "55", "Fair skin" to "40", "Smooth" to "40")) {
                    ValueChip(label, value, selected = label == chosen, onClick = { chosen = label }, modifier = Modifier.width(96.dp))
                }
                var copies by remember { mutableIntStateOf(5) }
                CopyStepper(copies, { copies = it })
                FrameNumber("0012")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((colour, label) in listOf(c.textMuted to "to edit", c.cyanotype to "ready", c.fixer to "printed", c.caution to "problem")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(colour, glow = colour != c.textMuted)
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Panel(Modifier.size(260.dp, 150.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Panel", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text("Lit from above", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                }
                GreyCardStage(Modifier.size(220.dp, 150.dp)) {
                    Box(Modifier.size(70.dp, 90.dp).background(Color(0xFF5B86C4)))
                }
                FilmStrip(Modifier.width(320.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (n in listOf("0012", "0011", "0010")) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.fillMaxWidth().aspectRatio(35f / 42f).background(Color(0xFFEDEFF2)))
                                FrameNumber(n, color = c.filmText)
                            }
                        }
                    }
                }
            }
            Box(Modifier.height(1.dp))
        }
    }
}
