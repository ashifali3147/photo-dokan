package com.tlw.photodokan.ui.intro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.theme.PhotoDokanTheme
import com.tlw.photodokan.ui.theme.StudioTheme
import com.tlw.photodokan.ui.theme.ThemeMode
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Draws each intro scene at several moments of its story, in a strip each, and the whole first page on
 * a phone and a wide window, into build/reports/screens for checking by eye. It fails only if
 * something can't draw.
 */
@OptIn(ExperimentalTestApi::class)
class IntroScreenshotTest {

    private val moments = listOf(0.15f, 0.35f, 0.55f, 0.75f, 1f)

    private fun scenes(mode: ThemeMode) {
        for ((scene, dslr) in IntroScene.entries.map { it to false } + (IntroScene.Shoot to true)) {
            runComposeUiTest {
                setContent {
                    PhotoDokanTheme(mode) {
                        val c = StudioTheme.colors
                        val measurer = rememberTextMeasurer()
                        Row(Modifier.background(c.base)) {
                            for (t in moments) {
                                Canvas(Modifier.size(200.dp)) { drawIntroScene(scene, c, t, idle = 0.2f, measurer, dslr) }
                            }
                        }
                    }
                }
                save("intro-${scene.name.lowercase()}${if (dslr) "-dslr" else ""}-${mode.name.lowercase()}")
            }
        }
    }

    private fun screen(name: String, width: Dp, height: Dp, mode: ThemeMode) = runComposeUiTest {
        setContent { PhotoDokanTheme(mode) { Box(Modifier.size(width, height)) { IntroScreen(onDone = {}, dslr = width >= 600.dp) } } }
        mainClock.advanceTimeBy(2_500)
        save(name)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.save(name: String) {
        val folder = File("build/reports/screens").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(folder, "$name.png"))
    }

    @Test fun scenesLight() = scenes(ThemeMode.Light)
    @Test fun scenesDark() = scenes(ThemeMode.Dark)
    @Test fun phone() = screen("intro-phone-light", 390.dp, 844.dp, ThemeMode.Light)
    @Test fun wideDark() = screen("intro-wide-dark", 1280.dp, 800.dp, ThemeMode.Dark)
}
