package com.tlw.photodokan.ui.intro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.components.PrimaryButton
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.StudioBackground
import com.tlw.photodokan.ui.platform.SystemBackHandler
import com.tlw.photodokan.ui.platform.deviceIsCamera
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** What each page says beside its picture: a few words, as the operator will read it once. */
private class IntroPage(val scene: IntroScene, val title: String, val body: String)

private val PAGES = listOf(
    IntroPage(IntroScene.Shoot, "Take the photo", "Shoot on the phone, or bring a photo in on the PC. The app finds the face for you."),
    IntroPage(IntroScene.Edit, "Make it right", "Crop to size, put the studio blue behind, and fix the light. The original photo is never changed."),
    IntroPage(IntroScene.Arrange, "Fill the sheet", "Choose the size and how many copies. They go into the next free places on the A4 sheet."),
    IntroPage(IntroScene.Print, "Print and hand over", "Prints at the exact size. A part-used sheet goes back in, and only the free places print."),
    IntroPage(IntroScene.Welcome, "Welcome to Photo Dokan", "Everything the studio needs, from the first click to the last print. Let's open the shop."),
)

/**
 * The tour shown the first time the app opens: from taking a photo to handing over the print,
 * ending on the shop opening for the day. The pictures move a little faster than the words as a
 * page is swiped, so they seem to sit in front. Skip leaves at any time. On a phone the words sit
 * under the picture; on a wide window, beside it, filling the window. [dslr] shoots the first
 * picture with the studio's camera, as the PC does, rather than a phone.
 */
@Composable
fun IntroScreen(onDone: () -> Unit, modifier: Modifier = Modifier, dslr: Boolean = !deviceIsCamera) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    fun go(page: Int) {
        scope.launch { pager.animateScrollToPage(page) }
    }
    SystemBackHandler(enabled = pager.currentPage > 0) { go(pager.currentPage - 1) }

    StudioBackground(modifier.fillMaxSize()) {
        IntroLight(pager, Modifier.matchParentSize())
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth >= 600.dp
            IntroContent(
                pager,
                wide,
                dslr,
                onNext = { if (pager.currentPage == PAGES.lastIndex) onDone() else go(pager.currentPage + 1) },
                onBack = { go(pager.currentPage - 1) },
                onSkip = onDone,
                modifier = if (wide) Modifier.padding(horizontal = 48.dp, vertical = 28.dp) else Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun IntroContent(pager: PagerState, wide: Boolean, dslr: Boolean, onNext: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit, modifier: Modifier) {
    val c = StudioTheme.colors
    val last = pager.currentPage == PAGES.lastIndex
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Photo Dokan", style = MaterialTheme.typography.titleMedium, color = c.textMuted)
            Spacer(Modifier.weight(1f))
            if (!last) TextButton(onSkip) { Text("Skip", style = MaterialTheme.typography.labelLarge, color = c.textMuted) }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = PAGES[index]
            // How far this page is from the middle: 0 on screen, -1 to the right, 1 to the left.
            val away = (pager.currentPage - index) + pager.currentPageOffsetFraction
            val art = @Composable { artModifier: Modifier ->
                IntroArt(
                    page.scene,
                    playing = pager.settledPage == index,
                    dslr = dslr,
                    modifier = artModifier.graphicsLayer {
                        translationX = -away * size.width * 0.45f
                        alpha = 1f - abs(away).coerceAtMost(1f) * 0.8f
                        val shrink = 1f - abs(away).coerceAtMost(1f) * 0.12f
                        scaleX = shrink
                        scaleY = shrink
                    },
                )
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    art(Modifier.weight(1.2f).fillMaxHeight().padding(vertical = 16.dp))
                    Spacer(Modifier.width(48.dp))
                    Column(Modifier.weight(0.8f).widthIn(max = 460.dp)) {
                        Text(page.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = c.text)
                        Spacer(Modifier.height(16.dp))
                        Text(page.body, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, color = c.textMuted)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    art(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(page.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = c.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        page.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 420.dp).heightIn(min = 72.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PageDots(pager)
            Spacer(Modifier.weight(1f))
            // A mouse can't swipe, so a wide window gets a way back too.
            if (wide && pager.currentPage > 0) {
                QuietButton(onBack, tall = true) { Text("Back") }
                Spacer(Modifier.width(12.dp))
            }
            PrimaryButton(onNext, shineKey = last) { Text(if (last) "Open the shop" else "Next") }
        }
    }
}

/** A dot per page; the one shown stretches into a bar, following the finger as it swipes. */
@Composable
private fun PageDots(pager: PagerState) {
    val c = StudioTheme.colors
    val at = pager.currentPage + pager.currentPageOffsetFraction
    Row(
        Modifier.semantics { contentDescription = "Page ${pager.currentPage + 1} of ${PAGES.size}" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in PAGES.indices) {
            val near = 1f - abs(i - at).coerceAtMost(1f)
            Box(Modifier.size(width = (8 + 18 * near).dp, height = 8.dp).clip(CircleShape).background(lerp(c.line, c.accent, near)))
        }
    }
}

/**
 * A soft light behind the whole tour that drifts across as the pages turn, and warms from the
 * accent's blue to the green of done by the last page.
 */
@Composable
private fun IntroLight(pager: PagerState, modifier: Modifier) {
    val c = StudioTheme.colors
    Canvas(modifier) {
        val at = (pager.currentPage + pager.currentPageOffsetFraction) / PAGES.lastIndex
        val colour = lerp(c.accentGlow, c.okGlow, at.coerceIn(0f, 1f))
        val centre = Offset(size.width * (0.2f + 0.6f * at), size.height * 0.3f)
        val radius = max(size.width, size.height) * 0.6f
        drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = colour.alpha * 0.5f * c.glowStrength), Color.Transparent), centre, radius), radius, centre)
    }
}
