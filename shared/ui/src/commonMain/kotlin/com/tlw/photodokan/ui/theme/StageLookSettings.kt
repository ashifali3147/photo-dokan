package com.tlw.photodokan.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.ui.components.GreyStage
import com.tlw.photodokan.ui.components.glow
import com.tlw.photodokan.ui.components.pressScale
import com.tlw.photodokan.ui.components.rememberFrost
import com.tlw.photodokan.ui.editor.ColourChoices
import com.tlw.photodokan.ui.editor.ColourPicker
import com.tlw.photodokan.ui.editor.CropEditor
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
 * What photos lie on in the editor: a style and a colour, each style shown as it looks, saved on this
 * device. Set here only, so the editor itself stays as simple as it is.
 */
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
    StyleTiles(look, onChoose = { if (it != look.style) settings.choose(StageLook(it)) })
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

/** Room for one tile; fewer fit across and the tiles go in two rows of three. */
private val TILE_MIN = 104.dp

/**
 * The styles as small stages to tap, each with a sample photo lying on it as in the editor, in the
 * style's own colour; the chosen one in the colour chosen. A new style starts in its own colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleTiles(look: StageLook, onChoose: (StageStyle) -> Unit) = BoxWithConstraints {
    val styles = StageStyle.entries
    val across = if (maxWidth >= TILE_MIN * styles.size + 12.dp * (styles.size - 1)) styles.size else 3
    val sample = remember { samplePortrait() }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = across,
    ) {
        for (style in styles) {
            val chosen = style == look.style
            StyleTile(style, if (chosen) look.shownColour else style.defaultColour, chosen, sample, { onChoose(style) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StyleTile(style: StageStyle, argb: Long, selected: Boolean, sample: ImageBitmap, onClick: () -> Unit, modifier: Modifier) {
    val c = StudioTheme.colors
    val shape = RoundedCornerShape(17.dp)
    val source = remember { MutableInteractionSource() }
    val on by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "chosen")
    val colour = Color(argb.toInt())
    Column(
        modifier
            .pressScale(source)
            .clickable(interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .glow(c.accentGlow, shape, radius = 14.dp, amount = on * 0.7f)
                // A ring with a gap round the chosen one, as round a chosen photo.
                .border(2.dp, c.accent.copy(alpha = on), shape)
                .padding(5.dp),
        ) {
            GreyStage(Modifier.matchParentSize(), shape = RoundedCornerShape(12.dp), colour = colour) {
                CropEditor(
                    image = sample,
                    preset = PhotoSizePreset.STUDIO_SLOT,
                    crop = SAMPLE_CROP,
                    onCropChange = {},
                    onCommit = {},
                    modifier = Modifier.fillMaxSize().padding(vertical = 14.dp),
                    showGuide = false,
                    frost = rememberFrost(sample, Color.White, style.colourFrost).takeIf { style.frost },
                    frostVeil = colour,
                    frostVeilAlpha = style.veil,
                    glass = style.glass,
                    interactive = false,
                    frostBlur = 6.dp,
                )
            }
        }
        Text(
            style.label,
            style = MaterialTheme.typography.labelLarge,
            color = lerp(c.text, c.accentText, on),
            textAlign = TextAlign.Center,
        )
    }
}

/** The sample photo framed as a print would be: head and shoulders. */
private val SAMPLE_CROP = CropConfig(centerY = 0.4, scale = 1.6)
