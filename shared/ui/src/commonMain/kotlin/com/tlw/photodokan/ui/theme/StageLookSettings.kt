package com.tlw.photodokan.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.components.ChoiceChip
import com.tlw.photodokan.ui.components.GreyStage
import com.tlw.photodokan.ui.editor.ColourChoices
import com.tlw.photodokan.ui.editor.ColourPicker
import org.koin.compose.koinInject

/**
 * The colours offered for the stage, greys first: grey shows a photo's colours truly. Few enough
 * that, with the style's own colour and Custom, they fit across a phone; Custom gives any other.
 */
private val STAGE_COLOURS = listOf(
    "Mid grey" to STAGE_GREY,
    "Dark grey" to 0xFF3C3C3C,
    "Light grey" to 0xFFD6D6D4,
    "Warm" to 0xFF8C8172,
)

/** Where a Custom stage colour starts, before one has been picked. */
private const val FIRST_CUSTOM_STAGE = 0xFF5E6B7A

/**
 * What photos lie on in the editor: a style and a colour, with a small preview, saved on this
 * device. Set here only, so the editor itself stays as simple as it is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageLookSettings(settings: StageSettings = koinInject()) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    val c = StudioTheme.colors
    val look by settings.look.collectAsState()
    Text("Photo background", style = MaterialTheme.typography.titleLarge, color = c.text)
    Text(
        "What the photo lies on while you edit it. It never changes the photo or the print.",
        style = MaterialTheme.typography.bodyLarge,
        color = c.textMuted,
    )
    StagePreview(look)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (style in StageStyle.entries) {
            // A new style starts in its own colour.
            ChoiceChip(style.label, selected = style == look.style, onClick = { if (style != look.style) settings.choose(StageLook(style)) })
        }
    }
    Text(look.style.description, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)

    Text("Colour", style = MaterialTheme.typography.titleMedium, color = c.text)
    // The style's own colour first, then the greys and tints; each only once.
    val choices = (listOf("${look.style.label} colour" to look.style.defaultColour) + STAGE_COLOURS).distinctBy { it.second }
    var customColour by remember { mutableStateOf(FIRST_CUSTOM_STAGE) }
    val chosen = look.shownColour
    val custom = choices.none { it.second == chosen }
    if (custom) customColour = chosen
    ColourChoices(
        choices,
        chosen,
        customColour,
        onPick = { settings.choose(look.copy(colour = it.takeIf { argb -> argb != look.style.defaultColour })) },
        onCustom = { settings.choose(look.copy(colour = customColour)) },
    )
    if (custom) ColourPicker(chosen, onPreview = { settings.preview(look.copy(colour = it)) }, onCommit = settings::commit)
    if (!look.neutral) {
        Text(
            "This colour has a tint, which can change how skin looks. Choose a grey when judging skin tone.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.warn,
        )
    }
}

/** What each style is, and what it does to how a photo looks. */
private val StageStyle.description: String
    get() = when (this) {
        StageStyle.Solid -> "Plain neutral grey, as before. Shows a photo's colours truly."
        StageStyle.Glass -> "Grey glass with a soft sheen. Shows a photo's colours truly."
        StageStyle.Frost -> "The photo carries on past its edges, blurred in black and white, behind grey glass. Shows a photo's colours truly."
        StageStyle.ColourFrost -> "The photo carries on past its edges, blurred in colour. The most vivid, but colours around a photo can change how skin looks."
        StageStyle.Smoked -> "Frost behind dark glass, in keeping with the dark theme. Photos look a little brighter than they print."
        StageStyle.LightTable -> "Frost behind milky light glass, like a light box. Photos look a little darker than they print."
    }

/**
 * A small stage in [look]: a print on it, and for the frosted styles a blurred, larger shape of
 * the print showing through the glass round it.
 */
@Composable
private fun StagePreview(look: StageLook) {
    val colour = Color(look.shownColour.toInt())
    GreyStage(Modifier.fillMaxWidth().height(150.dp), colour = colour) {
        Canvas(Modifier.matchParentSize()) {
            if (look.style.frost) {
                // The photo carrying on behind the glass, softened and veiled in the stage's colour.
                val tone = if (look.style.colourFrost) Color(0xFF5C7FBF) else Color(0xFF6A6A6A)
                drawRect(
                    Brush.radialGradient(listOf(tone.copy(alpha = 1f - look.style.veil), Color.Transparent), center = center, radius = size.height),
                )
            }
            if (look.style.glass) {
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), start = Offset.Zero, end = Offset(size.width * 0.6f, size.height * 0.6f)))
            }
        }
        Box(Modifier.fillMaxHeight(0.78f).aspectRatio(0.8f).shadow(10.dp, RectangleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                // A plain sample print: a sky backdrop and the outline of a head and shoulders.
                drawRect(Color(0xFF9CC3E8))
                drawOval(Color(0xFF3A3A44), topLeft = Offset(size.width * 0.12f, size.height * 0.62f), size = Size(size.width * 0.76f, size.height * 0.7f))
                drawOval(Color(0xFFC99A7C), topLeft = Offset(size.width * 0.3f, size.height * 0.16f), size = Size(size.width * 0.4f, size.height * 0.5f))
            }
        }
    }
}
