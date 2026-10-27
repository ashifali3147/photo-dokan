package com.tlw.photodokan.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.util.lerp
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.ui.theme.StudioColors
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The intro's pictures, one per page: shoot, edit, fill the sheet, print, and the shop opening. */
internal enum class IntroScene(val millis: Int) {
    Shoot(3_600),
    Edit(3_600),
    Arrange(3_800),
    Print(4_200),
    Welcome(4_800),
}

/**
 * The picture for [scene]. Shoot runs round and round, as a camera does; the others play once
 * each time their page settles ([playing]), start again from the beginning when it comes back,
 * and then keep breathing a little so the page never looks frozen. [dslr] shoots with the
 * studio's camera rather than a phone.
 */
@Composable
internal fun IntroArt(scene: IntroScene, playing: Boolean, dslr: Boolean, modifier: Modifier = Modifier) {
    val c = StudioTheme.colors
    val measurer = rememberTextMeasurer()
    val loops = rememberInfiniteTransition(label = "intro")
    val idle = loops.animateFloat(0f, 1f, infiniteRepeatable(tween(IDLE_MILLIS, easing = LinearEasing)), label = "idle")
    if (scene == IntroScene.Shoot) {
        val loop = loops.animateFloat(0f, 1f, infiniteRepeatable(tween(scene.millis, easing = LinearEasing)), label = "shoot")
        Canvas(modifier) { drawIntroScene(scene, c, loop.value, idle.value, measurer, dslr) }
    } else {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(playing) {
            // A page swiped away is off screen by the time it settles, so it can quietly rewind.
            if (playing) progress.animateTo(1f, tween(scene.millis, easing = LinearEasing)) else progress.snapTo(0f)
        }
        Canvas(modifier) { drawIntroScene(scene, c, progress.value, idle.value, measurer, dslr) }
    }
}

private const val IDLE_MILLIS = 4_000

/** Draws [scene] at [t] (0..1 through its story) and [idle] (0..1, looping, for breathing). */
internal fun DrawScope.drawIntroScene(scene: IntroScene, c: StudioColors, t: Float, idle: Float, measurer: TextMeasurer, dslr: Boolean = false) {
    val g = Units(size)
    when (scene) {
        IntroScene.Shoot -> if (dslr) drawShootDslr(g, t, idle) else drawShootPhone(g, t, idle)
        IntroScene.Edit -> drawEdit(g, c, t, idle)
        IntroScene.Arrange -> drawArrange(g, c, t, idle, measurer)
        IntroScene.Print -> drawPrint(g, c, t, idle, measurer)
        IntroScene.Welcome -> drawWelcome(g, c, t, idle, measurer)
    }
}

/** A phone's live camera, with its own bars and round shutter button on the screen. */
private fun DrawScope.drawShootPhone(g: Units, t: Float, idle: Float) {
    val u = g.u
    val phone = g.rect(30f, 3f, 40f, 94f)
    val screen = phone.deflate(1.8f * u)
    val portrait = Rect(Offset(screen.left - 3f * u, screen.top + 14f * u), Size(screen.width + 6f * u, (screen.width + 6f * u) / PASSPORT))
    val sway = wave(idle) * 0.012f

    drawFlashSpill(t, phone.center, 60f * u)
    drawSoftShadow(phone, u, 6f * u)
    drawRoundRect(Device, phone.topLeft, phone.size, CornerRadius(6f * u))
    drawRoundRect(DeviceRim, phone.topLeft, phone.size, CornerRadius(6f * u), style = Stroke(0.5f * u))
    drawLiveView(screen, 4.6f * u, portrait, t, sway, u) {
        drawRect(Color.Black.copy(alpha = 0.45f), screen.topLeft, Size(screen.width, 8f * u))
        for (i in 0..2) drawCircle(Color.White.copy(alpha = 0.7f), 0.7f * u, Offset(screen.left + (5f + i * 4f) * u, screen.top + 4f * u))
        val bar = Rect(screen.left, screen.bottom - 15f * u, screen.right, screen.bottom)
        drawRect(Color.Black.copy(alpha = 0.45f), bar.topLeft, bar.size)
        drawCircle(Color.White, 4.6f * u, bar.center, style = Stroke(0.7f * u))
        drawCircle(Color.White, (3.6f - 0.8f * shutterPress(t)) * u, bar.center)
    }
    drawTakenPhoto(portrait, sway, g.rect(4f, 60f, 22f, 22f / PASSPORT), t, u)
}

/**
 * The studio's camera from behind: live view on its screen, the shutter button on top pressed,
 * and the flash on the prism firing.
 */
private fun DrawScope.drawShootDslr(g: Units, t: Float, idle: Float) {
    val u = g.u
    val body = g.rect(6f, 30f, 88f, 50f)
    val flashHead = g.rect(38f, 15f, 20f, 5f)
    val sway = wave(idle) * 0.012f

    drawFlashSpill(t, flashHead.center, 70f * u)
    drawSoftShadow(body, u, 7f * u)
    // The prism hump, with the flash on top and the eyepiece on its back.
    val hump = Path().apply {
        val a = g.at(31f, 31f)
        val b = g.at(36f, 19f)
        val c = g.at(60f, 19f)
        val d = g.at(65f, 31f)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
    drawRoundRect(DeviceRim, flashHead.topLeft, flashHead.size, CornerRadius(1.2f * u))
    val lamp = g.rect(40f, 16f, 16f, 2.4f)
    drawRoundRect(lerp(Color(0xFF8C93A3), Color.White, flashAt(t)), lamp.topLeft, lamp.size, CornerRadius(0.6f * u))
    drawPath(hump, Device)
    val eyepiece = g.rect(41f, 21f, 14f, 8f)
    drawRoundRect(Rubber, eyepiece.topLeft, eyepiece.size, CornerRadius(2f * u))
    drawCircle(Color(0xFF2A3140), 2.2f * u, eyepiece.center)

    // The mode dial on the left shoulder and the shutter button on the right one.
    val dial = g.rect(12f, 25f, 14f, 6f)
    drawRoundRect(DeviceRim, dial.topLeft, dial.size, CornerRadius(1f * u))
    for (i in 1..6) drawLine(Device, Offset(dial.left + i * dial.width / 7f, dial.top + 0.8f * u), Offset(dial.left + i * dial.width / 7f, dial.bottom - 0.8f * u), 0.4f * u)
    val press = shutterPress(t)
    drawRoundRect(DeviceRim, g.at(74f, 27f), Size(14f * u, 4f * u), CornerRadius(1f * u))
    drawOval(Silver, Offset(g.at(76f, 0f).x, g.at(0f, 24.6f + 1.4f * press).y), Size(10f * u, 3.4f * u))

    drawRoundRect(Brush.verticalGradient(listOf(DeviceRim, Device), body.top, body.bottom), body.topLeft, body.size, CornerRadius(7f * u))
    drawRoundRect(Color.White.copy(alpha = 0.08f), body.topLeft, body.size, CornerRadius(7f * u), style = Stroke(0.4f * u))
    // Strap lugs.
    drawRect(DeviceRim, g.at(4f, 34f), Size(2f * u, 5f * u))
    drawRect(DeviceRim, g.at(94f, 34f), Size(2f * u, 5f * u))

    // The buttons and the four-way pad beside the screen.
    val pad = g.at(81f, 58f)
    drawCircle(Rubber, 6.5f * u, pad)
    drawCircle(DeviceRim, 6.5f * u, pad, style = Stroke(0.8f * u))
    drawCircle(DeviceRim, 2.6f * u, pad)
    for (at in listOf(g.at(76f, 41f), g.at(86f, 41f))) drawCircle(DeviceRim, 1.9f * u, at)
    for (at in listOf(g.at(75f, 72f), g.at(87f, 72f))) drawCircle(DeviceRim, 1.5f * u, at)
    drawCircle(Color(0xFFE5484D), 0.7f * u, g.at(88f, 34f))

    val frame = g.rect(12f, 36f, 56f, 38f)
    drawRoundRect(Rubber, frame.topLeft, frame.size, CornerRadius(2.5f * u))
    val screen = frame.deflate(1.4f * u)
    val portraitHeight = screen.height * 1.2f
    val portrait = Rect(Offset(screen.center.x - portraitHeight * PASSPORT / 2f, screen.top + 2f * u), Size(portraitHeight * PASSPORT, portraitHeight))
    drawLiveView(screen, 1.4f * u, portrait, t, sway, u) {
        // What the camera shows round the picture: battery, focus point, exposure.
        drawRect(Color.White.copy(alpha = 0.75f), Offset(screen.left + 2f * u, screen.top + 1.6f * u), Size(4f * u, 1.6f * u), style = Stroke(0.3f * u))
        drawRect(Color.White.copy(alpha = 0.75f), Offset(screen.left + 2.4f * u, screen.top + 2f * u), Size(2.4f * u, 0.8f * u))
        drawRect(Color.Black.copy(alpha = 0.45f), Offset(screen.left, screen.bottom - 4f * u), Size(screen.width, 4f * u))
        for (i in 0..4) {
            drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(screen.left + (3f + i * 10f) * u, screen.bottom - 2.6f * u), Size(6f * u, 1.2f * u), CornerRadius(0.6f * u))
        }
    }
    drawTakenPhoto(portrait, sway, g.rect(3f, 62f, 20f, 20f / PASSPORT), t, u)
}

/**
 * A camera's live view in [screen]: the face is found and boxed, [overlay] is the camera's own
 * display, and the shutter closes with a flash.
 */
private inline fun DrawScope.drawLiveView(screen: Rect, corner: Float, portrait: Rect, t: Float, sway: Float, u: Float, overlay: DrawScope.() -> Unit) {
    clipPath(Path().apply { addRoundRect(RoundRect(screen, CornerRadius(corner))) }) {
        drawRoom(screen)
        drawPerson(portrait, sway = sway)

        // The face box: drifts in large, settles on the face and turns green once it is sure.
        val face = faceRect(portrait, sway)
        val found = eased(t, 0.05f, 0.3f)
        val sure = span(t, 0.3f, 0.36f)
        if (found > 0f && t < 0.62f) {
            val box = scaleAbout(face.inflate(1.2f * u), lerp(1.7f, 1f, found))
            drawCorners(box, lerp(Color.White, FaceFound, sure).copy(alpha = found), 3f * u, 0.7f * u)
            val ring = span(t, 0.3f, 0.48f)
            if (ring in 0.001f..0.999f) {
                val pulse = scaleAbout(box, 1f + 0.35f * ring)
                drawRoundRect(FaceFound.copy(alpha = 0.7f * (1f - ring)), pulse.topLeft, pulse.size, CornerRadius(2f * u), style = Stroke(0.5f * u))
            }
        }
        overlay()

        // The shutter: blades close to a point and open again, as a real one would.
        val closed = eased(t, 0.5f, 0.56f) * (1f - eased(t, 0.56f, 0.64f))
        if (closed > 0f) drawIris(screen, closed, u)
        val flash = flashAt(t)
        if (flash > 0f) drawRect(Color.White.copy(alpha = flash), screen.topLeft, screen.size)
    }
}

/** The light from the flash, spilling round [centre]. */
private fun DrawScope.drawFlashSpill(t: Float, centre: Offset, radius: Float) {
    val flash = flashAt(t)
    if (flash > 0f) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * flash), Color.Transparent), centre, radius), radius, centre)
}

/** The picture just taken, cropped to the face, flies out and lands at [to] with a tick. */
private fun DrawScope.drawTakenPhoto(portrait: Rect, sway: Float, to: Rect, t: Float, u: Float) {
    val fly = eased(t, 0.62f, 0.84f)
    if (fly <= 0f) return
    val fade = 1f - span(t, 0.95f, 1f)
    val from = scaleAbout(faceRect(portrait, sway), 1.9f).let { Rect(it.left, it.top, it.right, it.top + it.width / PASSPORT) }
    val rect = lerpRect(from, to, fly)
    // An arc rather than a straight line: thrown, not slid.
    val lift = sin(fly * PI.toFloat()) * 10f * u
    rotate(-9f * fly, rect.center) {
        translate(0f, -lift) {
            faded(fade) {
                drawPrintedPhoto(rect, u)
                val tick = back(span(t, 0.82f, 0.9f))
                if (tick > 0f) drawTick(Offset(rect.right - 0.5f * u, rect.top + 0.5f * u), 3.6f * u * tick, FaceFound)
            }
        }
    }
}

private fun flashAt(t: Float): Float = span(t, 0.55f, 0.57f) * (1f - span(t, 0.57f, 0.8f))

private fun shutterPress(t: Float): Float = span(t, 0.46f, 0.49f) * (1f - span(t, 0.49f, 0.54f))

/**
 * The photo on the editor's glass stage: dropped on, cropped to passport size, the room behind
 * wiped away for the studio blue, the light raised a touch, and a sparkle when it's right.
 */
private fun DrawScope.drawEdit(g: Units, c: StudioColors, t: Float, idle: Float) {
    val u = g.u
    val stage = g.rect(3f, 4f, 94f, 76f)
    drawSoftShadow(stage, u, 5f * u)
    drawRoundRect(c.stage, stage.topLeft, stage.size, CornerRadius(5f * u))
    clipPath(Path().apply { addRoundRect(RoundRect(stage, CornerRadius(5f * u))) }) {
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), stage.topLeft, stage.center), stage.topLeft, stage.size)
        // A soft band of light crossing the glass now and then.
        val x = stage.left + (idle * 1.8f - 0.4f) * stage.width
        drawRect(
            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent), Offset(x - 12f * u, stage.top), Offset(x + 12f * u, stage.bottom)),
            stage.topLeft,
            stage.size,
        )
    }
    drawRoundRect(Color.White.copy(alpha = 0.3f), stage.topLeft, stage.size, CornerRadius(5f * u), style = Stroke(0.4f * u))

    val crop = g.rect(32f, 18.5f, 36f, 36f / PASSPORT)
    val loose = g.rect(18f, 10f, 64f, 64f)
    val drop = eased(t, 0f, 0.14f)
    val light = eased(t, 0.76f, 0.94f)
    val studio = eased(t, 0.46f, 0.74f)
    scale(lerp(1.1f, 1f, drop), loose.center) {
        clipRect(loose.left, loose.top, loose.right, loose.bottom) {
            drawRoom(loose)
            // The new backdrop is laid only inside the crop: outside it is cut away anyway.
            clipRect(crop.left, crop.top, crop.left + crop.width * studio, crop.bottom) { drawStudio(crop) }
            drawPerson(crop, light = light)
        }
        if (drop < 1f) drawRect(c.stage.copy(alpha = 1f - drop), loose.topLeft, loose.size)

        // Cropping: the frame closes in and the rest of the picture sinks under the glass.
        val closing = eased(t, 0.16f, 0.4f)
        val frame = lerpRect(loose, crop, closing)
        val veil = c.stage.copy(alpha = 0.92f * eased(t, 0.18f, 0.5f))
        drawRect(veil, loose.topLeft, Size(loose.width, frame.top - loose.top))
        drawRect(veil, Offset(loose.left, frame.bottom), Size(loose.width, loose.bottom - frame.bottom))
        drawRect(veil, Offset(loose.left, frame.top), Size(frame.left - loose.left, frame.height))
        drawRect(veil, Offset(frame.right, frame.top), Size(loose.right - frame.right, frame.height))
        if (t > 0.12f) drawCorners(frame, lerp(c.accent, Color.White, span(t, 0.5f, 0.7f)), 4f * u, 0.9f * u)

        // The wipe that swaps the room for the studio's blue.
        if (studio in 0.001f..0.999f) {
            val wx = crop.left + crop.width * studio
            drawLine(Color.White.copy(alpha = 0.35f), Offset(wx, crop.top), Offset(wx, crop.bottom), 2.4f * u)
            drawLine(Color.White, Offset(wx, crop.top), Offset(wx, crop.bottom), 0.6f * u)
        }
    }

    // The light slider under the stage, nudged up.
    val track = g.at(26f, 90f) to g.at(74f, 90f)
    val knob = lerp(0.38f, 0.68f, light)
    val knobAt = Offset(lerp(track.first.x, track.second.x, knob), track.first.y)
    drawLine(c.line, track.first, track.second, 1.2f * u, StrokeCap.Round)
    drawLine(c.accent, track.first, knobAt, 1.2f * u, StrokeCap.Round)
    drawSun(Offset(track.first.x - 6f * u, track.first.y), 1.4f * u, c.textMuted)
    drawSun(Offset(track.second.x + 6f * u, track.second.y), 2f * u, c.textMuted)
    drawCircle(c.accentGlow, 4f * u * (0.6f + 0.4f * span(t, 0.74f, 0.8f) * (1f - span(t, 0.94f, 1f))), knobAt)
    drawCircle(c.surface, 2.6f * u, knobAt)
    drawCircle(c.accent, 2.6f * u, knobAt, style = Stroke(0.6f * u))

    // Done: a few sparkles round the finished photo.
    val shine = span(t, 0.92f, 1f)
    if (shine > 0f) {
        val spots = listOf(Offset(crop.right + 3f * u, crop.top + 2f * u), Offset(crop.left - 4f * u, crop.top + 14f * u), Offset(crop.right + 1f * u, crop.bottom - 5f * u))
        spots.forEachIndexed { i, at -> drawSparkle(at, (1.4f + 1.1f * wave(idle, i / 3f)) * u * shine, Color.White) }
    }
}

/**
 * The copies go onto the sheet: the finished photo, as a little stack of copies, sends them one
 * by one to the lowest free places of a part-used 5 × 8 sheet, and the next free place waits, lit.
 */
private fun DrawScope.drawArrange(g: Units, c: StudioColors, t: Float, idle: Float, measurer: TextMeasurer) {
    val u = g.u
    val paper = g.rect(35f, 4f, 92f / PAPER_RATIO, 92f)
    drawSoftShadow(paper, u, 0.8f * u)
    drawRect(c.paper, paper.topLeft, paper.size)
    for (i in 0 until SHEET.capacity) {
        val s = slot(paper, i)
        when {
            i < USED -> drawRect(c.slotPrinted, s.topLeft, s.size)
            else -> {
                drawRect(c.slotFree, s.topLeft, s.size)
                drawRect(c.line, s.topLeft, s.size, style = Stroke(0.25f * u))
            }
        }
    }

    // The stack of copies to the left, with how many are still to place.
    val source = g.rect(5f, 30f, 22f, 22f / PASSPORT)
    val left = COPIES - (0 until COPIES).count { copyProgress(t, it) > 0f }
    for (layer in 2 downTo 1) {
        if (left > layer) rotate(layer * 5f - 2f, source.center) { drawPrintedPhoto(source.translate(layer * 0.6f * u, layer * 0.6f * u), u) }
    }
    if (left > 0) {
        drawPrintedPhoto(source, u)
        val count = measurer.measure("× $left", TextStyle(color = c.text, fontSize = (4.4f * u).toSp(), fontWeight = FontWeight.SemiBold))
        drawText(count, topLeft = Offset(source.center.x - count.size.width / 2f, source.bottom + 3f * u))
    }

    // Each copy flies on an arc to its place and lands with a ring of light.
    for (i in 0 until COPIES) {
        val f = copyProgress(t, i)
        if (f <= 0f) continue
        val target = slot(paper, USED + i)
        val rect = lerpRect(source, target, f)
        val arc = sin(f * PI.toFloat()) * 14f * u
        rotate(-6f * (1f - f), rect.center) { drawPortrait(rect.translate(0f, -arc), studio = 1f) }
        val ring = span(t - (0.06f + i * STEP + FLIGHT), 0f, 0.1f)
        if (ring in 0.001f..0.999f) {
            val r = target.inflate(ring * 2f * u)
            drawRect(c.accent.copy(alpha = 1f - ring), r.topLeft, r.size, style = Stroke(0.6f * u))
        }
    }

    // The lowest free place is always the next to fill.
    val placed = (0 until COPIES).count { copyProgress(t, it) >= 1f }
    val next = slot(paper, USED + placed).inflate(0.5f * u)
    drawRect(c.accent.copy(alpha = 0.45f + 0.35f * wave(idle)), next.topLeft, next.size, style = Stroke(0.6f * u))
}

/**
 * Printing: the sheet runs out of the printer with the new photos on it, one is cut round and
 * lifted out, and handed over with the customer's number.
 */
private fun DrawScope.drawPrint(g: Units, c: StudioColors, t: Float, idle: Float, measurer: TextMeasurer) {
    val u = g.u
    val paper = (62f / PAPER_RATIO).let { w -> g.rect(50f - w / 2f, 27f, w, 62f) }
    val out = eased(t, 0.04f, 0.58f)
    val printing = t < 0.6f

    // Paper waiting at the back of the printer.
    val tray = g.rect(24f, 1f, 52f, 8f)
    drawRect(c.paper, tray.topLeft, tray.size)
    drawRect(c.line, tray.topLeft, tray.size, style = Stroke(0.3f * u))

    // The sheet comes out moving down, so it is drawn first and the printer hides its top.
    val travel = paper.height * (1f - out)
    clipRect(paper.left - 2f * u, paper.top, paper.right + 2f * u, g.at(0f, 100f).y) {
        translate(0f, -travel) {
            drawSoftShadow(paper, u, 0.6f * u)
            drawRect(c.paper, paper.topLeft, paper.size)
            for (i in 0 until SHEET.capacity) {
                val s = slot(paper, i)
                when {
                    i == HANDED && t >= LIFT -> drawRect(c.base, s.topLeft, s.size)
                    i < USED -> drawRect(c.slotPrinted, s.topLeft, s.size)
                    i < USED + COPIES -> drawPortrait(s, studio = 1f)
                    else -> drawRect(c.slotFree, s.topLeft, s.size)
                }
            }
        }
    }

    val body = g.rect(12f, 6f, 76f, 23f)
    drawSoftShadow(body, u, 4f * u)
    drawRoundRect(Brush.verticalGradient(listOf(DeviceRim, Device), body.top, body.bottom), body.topLeft, body.size, CornerRadius(4f * u))
    drawLine(Color.Black.copy(alpha = 0.7f), g.at(24f, 27f), g.at(76f, 27f), 1.6f * u, StrokeCap.Round)
    // A small screen showing how far it has got, and a light that blinks while it prints.
    val panel = g.rect(17f, 11f, 18f, 6f)
    drawRoundRect(Color(0xFF0E1622), panel.topLeft, panel.size, CornerRadius(1f * u))
    val bar = Rect(panel.left + 1.4f * u, panel.center.y - 0.7f * u, panel.right - 1.4f * u, panel.center.y + 0.7f * u)
    drawRoundRect(Color.White.copy(alpha = 0.15f), bar.topLeft, bar.size, CornerRadius(0.7f * u))
    drawRoundRect(c.accent, bar.topLeft, Size(bar.width * out, bar.height), CornerRadius(0.7f * u))
    val blink = if (printing) 0.35f + 0.65f * (0.5f + 0.5f * wave(idle * 6f)) else 1f
    drawCircle(Ready.copy(alpha = 0.35f * blink), 2.4f * u, g.at(80f, 14f))
    drawCircle(Ready.copy(alpha = blink), 1.1f * u, g.at(80f, 14f))
    for (i in 0..2) drawCircle(Color.White.copy(alpha = 0.18f), 1f * u, g.at(64f + i * 4.5f, 14f))

    // One photo is cut round...
    val handed = slot(paper, HANDED)
    val cut = span(t, 0.62f, 0.8f)
    if (cut > 0f && t < LIFT + 0.04f) {
        val outline = Path().apply { addRect(handed.inflate(0.7f * u)) }
        val measure = PathMeasure().apply { setPath(outline, false) }
        val traced = Path()
        measure.getSegment(0f, measure.length * cut, traced, true)
        drawPath(traced, c.accent, style = Stroke(0.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f * u, 0.8f * u))))
        if (cut < 1f) drawCircle(c.accent, 0.9f * u, measure.getPosition(measure.length * cut))
    }

    // ...lifted out, and handed over with the customer's number.
    val lift = eased(t, LIFT, 1f)
    if (lift > 0f) {
        val bob = wave(idle) * 0.8f * u * span(t, 0.98f, 1f)
        val to = g.rect(58f, 50f, 30f, 30f * handed.height / handed.width)
        val rect = lerpRect(handed, to, lift).translate(0f, bob)
        rotate(-7f * lift, rect.center) {
            drawSoftShadow(rect, u * lift * 1.6f, 0.4f * u)
            drawRect(Color.White, rect.inflate(0.8f * u * lift).topLeft, rect.inflate(0.8f * u * lift).size)
            drawPortrait(rect, studio = 1f)
            val tag = back(span(t, 0.9f, 1f))
            if (tag > 0f) {
                val text = measurer.measure("#14", TextStyle(color = c.onAccent, fontSize = (4.2f * u).toSp(), fontWeight = FontWeight.Bold))
                val pill = Rect(Offset(rect.left - 4f * u, rect.bottom - 6f * u), Size(text.size.width + 4f * u, text.size.height + 1.6f * u))
                scale(tag, pill.center) {
                    drawRoundRect(c.accentGlow, pill.inflate(1f * u).topLeft, pill.inflate(1f * u).size, CornerRadius(pill.height))
                    drawRoundRect(c.accent, pill.topLeft, pill.size, CornerRadius(pill.height / 2))
                    drawText(text, topLeft = Offset(pill.left + 2f * u, pill.top + 0.8f * u))
                }
            }
        }
        if (lift >= 1f) {
            listOf(Offset(to.right + 3f * u, to.top - 2f * u), Offset(to.left - 3f * u, to.top + 5f * u)).forEachIndexed { i, at ->
                drawSparkle(at, (1.2f + 1f * wave(idle, i / 2f)) * u, c.accent)
            }
        }
    }
}

/**
 * Photo Dokan opening for the day: the shutter rolls up, the striped awning drops, the sign
 * lights letter by letter beside the PD mark, photos go up in the window, the door's sign turns
 * from Closed to Open and the string of lights starts to twinkle.
 */
private fun DrawScope.drawWelcome(g: Units, c: StudioColors, t: Float, idle: Float, measurer: TextMeasurer) {
    val u = g.u
    drawLine(c.line, g.at(1f, 90f), g.at(99f, 90f), 0.8f * u, StrokeCap.Round)

    val shop = g.rect(10f, 14f, 80f, 76f)
    drawSoftShadow(shop, u, 1f * u)
    drawRect(ShopWall, shop.topLeft, shop.size)
    drawRect(Wood, g.at(8f, 11f), Size(84f * u, 3.5f * u))
    drawRect(Wood, g.at(10f, 86f), Size(80f * u, 4f * u))

    // The sign: the PD mark, then the name, each letter lighting in turn.
    val sign = g.rect(14f, 17f, 72f, 13f)
    drawRoundRect(SignBoard, sign.topLeft, sign.size, CornerRadius(2f * u))
    val mark = g.rect(16.5f, 19f, 9f, 9f)
    val markOn = span(t, 0.36f, 0.42f)
    drawRoundRect(lerp(SignOff, Awning, markOn), mark.topLeft, mark.size, CornerRadius(2f * u))
    val pd = measurer.measure("PD", TextStyle(color = Color.White, fontSize = (4.2f * u).toSp(), fontWeight = FontWeight.Bold))
    drawText(pd, topLeft = mark.center - Offset(pd.size.width / 2f, pd.size.height / 2f))
    val name = "PHOTO DOKAN"
    val lit = span(t, 0.4f, 0.7f) * name.length
    val letters = name.map { measurer.measure(it.toString(), TextStyle(fontSize = (5.6f * u).toSp(), fontWeight = FontWeight.Bold)) }
    var x = g.at(29f, 0f).x + (54f * u - letters.sumOf { it.size.width }) / 2f
    letters.forEachIndexed { i, letter ->
        val on = (lit - i).coerceIn(0f, 1f)
        val colour = lerp(SignOff, SignOn, on)
        val glowing = TextStyle(
            color = colour,
            fontSize = (5.6f * u).toSp(),
            fontWeight = FontWeight.Bold,
            shadow = if (on > 0f) Shadow(SignOn.copy(alpha = 0.8f * on), blurRadius = 3f * u * on) else null,
        )
        drawText(measurer.measure(name[i].toString(), glowing), topLeft = Offset(x, sign.center.y - letter.size.height / 2f))
        x += letter.size.width
    }

    // The front: a window of photos and the door, behind the rolling shutter.
    val window = g.rect(15f, 45f, 42f, 33f)
    drawRect(Wood, window.inflate(1f * u).topLeft, window.inflate(1f * u).size)
    drawRect(Brush.verticalGradient(listOf(Glass, Color.White), window.top, window.bottom), window.topLeft, window.size)
    drawLine(Wood, Offset(window.left, window.top + 4f * u), Offset(window.right, window.top + 4f * u), 0.4f * u)
    for (i in 0 until 3) {
        val pop = back(span(t, 0.6f + i * 0.06f, 0.72f + i * 0.06f))
        if (pop <= 0f) continue
        val pin = Offset(window.left + (8f + i * 13f) * u, window.top + 4f * u)
        val photo = Rect(Offset(pin.x - 4.5f * u, pin.y + 3f * u), Size(9f * u, 9f * u / PASSPORT))
        rotate(wave(idle, i / 3f) * 3f, pin) {
            scale(pop, pin) {
                drawLine(Wood, pin, photo.topLeft, 0.3f * u)
                drawLine(Wood, pin, Offset(photo.right, photo.top), 0.3f * u)
                drawPrintedPhoto(photo, u * 0.6f)
            }
        }
    }
    // A little camera on the counter, which fires once the shop is open.
    val counter = window.bottom - 4f * u
    drawLine(Wood, Offset(window.left, counter), Offset(window.right, counter), 0.6f * u)
    val cam = Rect(Offset(window.right - 12f * u, counter - 5f * u), Size(9f * u, 5f * u))
    drawRoundRect(Device, cam.topLeft, cam.size, CornerRadius(1f * u))
    drawRect(Device, Offset(cam.left + 2.6f * u, cam.top - 1.2f * u), Size(3.6f * u, 1.4f * u))
    drawCircle(DeviceRim, 1.8f * u, cam.center)
    drawCircle(Glass, 0.9f * u, cam.center)
    val pop = span(t, 0.9f, 0.92f) * (1f - span(t, 0.92f, 1f))
    if (pop > 0f) {
        val at = Offset(cam.left + 4.4f * u, cam.top - 1f * u)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = pop), Color.Transparent), at, 14f * u), 14f * u, at)
    }

    val door = g.rect(62f, 44f, 22f, 42f)
    drawRect(Wood, door.inflate(1f * u).topLeft, door.inflate(1f * u).size)
    drawRect(Door, door.topLeft, door.size)
    val pane = g.rect(65f, 47f, 16f, 15f)
    drawRect(Glass, pane.topLeft, pane.size)
    drawCircle(Silver, 1.1f * u, g.at(80.5f, 68f))
    // The sign on the door turns over from Closed to Open, then swings a little on its string.
    val turn = eased(t, 0.78f, 0.9f)
    val open = turn >= 0.5f
    val hook = g.at(73f, 48.5f)
    val card = g.rect(66.5f, 53f, 13f, 6f)
    rotate(if (turn >= 1f) wave(idle) * 4f else 0f, hook) {
        drawLine(Wood, hook, Offset(card.left + 1f * u, card.top), 0.3f * u)
        drawLine(Wood, hook, Offset(card.right - 1f * u, card.top), 0.3f * u)
        scale(scaleX = abs(cos(turn * PI.toFloat())), scaleY = 1f, pivot = card.center) {
            drawRoundRect(Color.White, card.topLeft, card.size, CornerRadius(0.8f * u))
            val words = measurer.measure(
                if (open) "OPEN" else "CLOSED",
                TextStyle(color = if (open) OpenGreen else ClosedRed, fontSize = (if (open) 3.4f else 2.6f).let { (it * u).toSp() }, fontWeight = FontWeight.Bold),
            )
            drawText(words, topLeft = card.center - Offset(words.size.width / 2f, words.size.height / 2f))
        }
    }

    // The rolling shutter goes up first, into its box under the awning.
    val up = eased(t, 0.02f, 0.28f)
    val front = g.rect(12f, 40f, 76f, 46f)
    val bottom = front.bottom - front.height * up
    if (bottom > front.top) {
        drawRect(ShutterGrey, front.topLeft, Size(front.width, bottom - front.top))
        var rib = bottom - 2.2f * u
        while (rib > front.top) {
            drawLine(ShutterRib, Offset(front.left, rib), Offset(front.right, rib), 0.35f * u)
            rib -= 2.2f * u
        }
        drawRect(ShutterRib, Offset(front.left, bottom - 1.4f * u), Size(front.width, 1.4f * u))
    }

    // The awning drops with a little bounce: stripes with a scalloped edge.
    val drop = back(span(t, 0.22f, 0.4f))
    val top = g.at(0f, 31f).y
    val awning = g.rect(11f, 31f, 78f, 0f)
    val stripes = 8
    val stripe = awning.width / stripes
    val depth = 9f * u * drop
    if (drop > 0f) {
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), top + depth, top + depth + 5f * u), Offset(awning.left, top + depth), Size(awning.width, 5f * u))
        for (i in 0 until stripes) {
            val colour = if (i % 2 == 0) Awning else AwningLight
            val left = awning.left + i * stripe
            drawRect(colour, Offset(left, top), Size(stripe, depth))
            drawArc(colour, 0f, 180f, useCenter = true, topLeft = Offset(left, top + depth - stripe / 2f * drop.coerceAtMost(1f)), size = Size(stripe, stripe * drop.coerceAtMost(1f)))
        }
    }

    // A string of lights along the roof, twinkling once the sign is lit.
    val bulbs = 13
    val lights = span(t, 0.7f, 0.76f)
    for (i in 0 until bulbs) {
        val f = i / (bulbs - 1f)
        val at = g.at(9f + 82f * f, 12.5f + 2.2f * sin(f * PI.toFloat() * 4f).let { it * it })
        val on = lights * (0.55f + 0.45f * wave(idle * 2f, i * 0.27f))
        drawCircle(Bulb.copy(alpha = 0.35f * on), 1.8f * u, at)
        drawCircle(lerp(SignOff, Bulb, on), 0.7f * u, at)
    }

    // Pots either side, for a welcome.
    for (left in listOf(2f, 91f)) {
        val pot = Path().apply {
            val a = g.at(left, 82f)
            val b = g.at(left + 7f, 82f)
            val c2 = g.at(left + 6f, 90f)
            val d = g.at(left + 1f, 90f)
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(c2.x, c2.y)
            lineTo(d.x, d.y)
            close()
        }
        val sway = wave(idle, left / 100f) * 0.4f * u
        for ((dx, dy, r) in listOf(Triple(1.6f, 79f, 2.4f), Triple(5.4f, 78.6f, 2.6f), Triple(3.5f, 75.6f, 2.8f))) {
            drawCircle(Leaf, r * u, g.at(left + dx, dy) + Offset(sway, 0f))
        }
        drawPath(pot, Terracotta)
    }

    // And a few sparkles once everything is open.
    val shine = span(t, 0.9f, 1f)
    if (shine > 0f) {
        listOf(g.at(88f, 7f), g.at(12f, 8f), g.at(60f, 40f)).forEachIndexed { i, at ->
            drawSparkle(at, (1.3f + 1f * wave(idle, i / 3f)) * u * shine, SignOn)
        }
    }
}

// --- The person, the room and the studio backdrop ---------------------------------------------

/** A person from the chest up filling [r], cropped to it as a print would be. */
private fun DrawScope.drawPortrait(r: Rect, studio: Float = 0f, light: Float = 0f) {
    // Cover [r] with a passport-shaped picture, centred, as the crop does.
    val wide = r.width / r.height > PASSPORT
    val w = if (wide) r.width else r.height * PASSPORT
    val h = w / PASSPORT
    val person = Rect(Offset(r.center.x - w / 2, r.center.y - h / 2), Size(w, h))
    clipRect(r.left, r.top, r.right, r.bottom) {
        if (studio < 1f) drawRoom(r)
        if (studio > 0f) clipRect(r.left, r.top, r.left + r.width * studio, r.bottom) { drawStudio(r) }
        drawPerson(person, light = light)
    }
}

/** A printed photo: the portrait with a thin white border, lying on something. */
private fun DrawScope.drawPrintedPhoto(r: Rect, u: Float) {
    drawSoftShadow(r, u, 0.5f * u)
    val border = r.inflate(0.8f * u)
    drawRect(Color.White, border.topLeft, border.size)
    drawPortrait(r, studio = 1f)
}

/** Draws [block] see-through by [alpha], as one picture, so overlaps don't show through each other. */
private inline fun DrawScope.faded(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 1f) return block()
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha }) }
    block()
    drawIntoCanvas { it.restore() }
}

/** The head and shoulders in a passport-shaped [p]: head where a passport photo wants it. */
private fun DrawScope.drawPerson(p: Rect, light: Float = 0f, sway: Float = 0f) {
    val w = p.width
    val h = p.height
    val x = p.center.x + sway * w
    val skin = lerp(Skin, SkinLit, light)
    drawOval(Shirt, Offset(x - w * 0.48f, p.top + h * 0.72f), Size(w * 0.96f, h * 0.6f))
    drawPath(Path().apply {
        moveTo(x - w * 0.12f, p.top + h * 0.74f)
        lineTo(x, p.top + h * 0.86f)
        lineTo(x + w * 0.12f, p.top + h * 0.74f)
        close()
    }, Collar)
    drawRect(skin, Offset(x - w * 0.085f, p.top + h * 0.56f), Size(w * 0.17f, h * 0.19f))
    drawOval(Hair, Offset(x - w * 0.255f, p.top + h * 0.15f), Size(w * 0.51f, h * 0.44f))
    drawOval(skin, Offset(x - w * 0.21f, p.top + h * 0.23f), Size(w * 0.42f, h * 0.43f))
    drawArc(Hair, 180f, 180f, useCenter = true, topLeft = Offset(x - w * 0.235f, p.top + h * 0.17f), size = Size(w * 0.47f, h * 0.24f))
    val eyes = p.top + h * 0.44f
    drawCircle(Hair, w * 0.024f, Offset(x - w * 0.085f, eyes))
    drawCircle(Hair, w * 0.024f, Offset(x + w * 0.085f, eyes))
    drawArc(Mouth, 25f, 130f, useCenter = false, topLeft = Offset(x - w * 0.07f, p.top + h * 0.5f), size = Size(w * 0.14f, h * 0.07f), style = Stroke(w * 0.02f, cap = StrokeCap.Round))
}

/** The face inside a person drawn in [p], for the camera's face box. */
private fun faceRect(p: Rect, sway: Float): Rect {
    val x = p.center.x + sway * p.width
    return Rect(Offset(x - p.width * 0.21f, p.top + p.height * 0.23f), Size(p.width * 0.42f, p.height * 0.43f))
}

/** A room as shot at the counter: a wall with a window and a picture, which a passport photo can't have. */
private fun DrawScope.drawRoom(r: Rect) {
    val w = r.width
    val h = r.height
    drawRect(Brush.verticalGradient(listOf(Wall, WallShade), r.top, r.bottom), r.topLeft, r.size)
    val window = Rect(Offset(r.left + w * 0.04f, r.top + h * 0.08f), Size(w * 0.32f, h * 0.3f))
    drawRect(Sky, window.topLeft, window.size)
    drawRect(Frame, window.topLeft, window.size, style = Stroke(w * 0.022f))
    drawLine(Frame, Offset(window.center.x, window.top), Offset(window.center.x, window.bottom), w * 0.016f)
    drawLine(Frame, Offset(window.left, window.center.y), Offset(window.right, window.center.y), w * 0.016f)
    drawRect(Frame, Offset(r.left + w * 0.72f, r.top + h * 0.12f), Size(w * 0.2f, h * 0.15f))
    drawRect(Picture, Offset(r.left + w * 0.735f, r.top + h * 0.135f), Size(w * 0.17f, h * 0.12f))
}

/** The studio's blue backdrop, lighter at the top. */
private fun DrawScope.drawStudio(r: Rect) {
    drawRect(Brush.verticalGradient(listOf(StudioTop, StudioBottom), r.top, r.bottom), r.topLeft, r.size)
}

// --- Small pieces ------------------------------------------------------------------------------

/** The shutter's blades over [r], [closed] from 0 (open) to 1 (shut). */
private fun DrawScope.drawIris(r: Rect, closed: Float, u: Float) {
    val full = r.maxDimension * 0.75f
    val radius = full * (1f - closed)
    val hole = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(r)
        addOval(Rect(r.center, radius))
    }
    drawPath(hole, Color(0xFF0B0B0D))
    // The blade edges, swinging round as they close.
    for (i in 0 until 6) {
        val a = (i * 60f + closed * 50f) * PI.toFloat() / 180f
        val from = r.center + Offset(cos(a), sin(a)) * radius
        val along = Offset(-sin(a), cos(a))
        drawLine(Color(0xFF34343A), from, from + along * full, 0.4f * u)
    }
}

/** L-shaped corners of [r], as a camera's face box or the crop frame. */
private fun DrawScope.drawCorners(r: Rect, colour: Color, length: Float, width: Float) {
    for ((x, dx) in listOf(r.left to 1f, r.right to -1f)) {
        for ((y, dy) in listOf(r.top to 1f, r.bottom to -1f)) {
            val corner = Path().apply {
                moveTo(x, y + dy * length)
                lineTo(x, y)
                lineTo(x + dx * length, y)
            }
            drawPath(corner, colour, style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}

/** A round green badge with a tick. */
private fun DrawScope.drawTick(centre: Offset, radius: Float, colour: Color) {
    drawCircle(colour, radius, centre)
    val tick = Path().apply {
        moveTo(centre.x - radius * 0.45f, centre.y + radius * 0.02f)
        lineTo(centre.x - radius * 0.1f, centre.y + radius * 0.35f)
        lineTo(centre.x + radius * 0.48f, centre.y - radius * 0.3f)
    }
    drawPath(tick, Color(0xFF0B3B24).copy(alpha = colour.alpha), style = Stroke(radius * 0.22f, cap = StrokeCap.Round))
}

/** A four-pointed twinkle. */
private fun DrawScope.drawSparkle(centre: Offset, radius: Float, colour: Color) {
    if (radius <= 0f) return
    val star = Path().apply {
        moveTo(centre.x, centre.y - radius)
        quadraticTo(centre.x, centre.y, centre.x + radius, centre.y)
        quadraticTo(centre.x, centre.y, centre.x, centre.y + radius)
        quadraticTo(centre.x, centre.y, centre.x - radius, centre.y)
        quadraticTo(centre.x, centre.y, centre.x, centre.y - radius)
        close()
    }
    drawPath(star, colour)
}

/** A little sun beside the light slider: small for less light, big for more. */
private fun DrawScope.drawSun(centre: Offset, radius: Float, colour: Color) {
    drawCircle(colour, radius, centre)
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f
        val dir = Offset(cos(a), sin(a))
        drawLine(colour, centre + dir * radius * 1.5f, centre + dir * radius * 2.1f, radius * 0.35f, StrokeCap.Round)
    }
}

/** A soft shadow under [r], as if lit from above. */
private fun DrawScope.drawSoftShadow(r: Rect, u: Float, corner: Float) {
    for (i in 1..3) {
        drawRoundRect(
            Color.Black.copy(alpha = 0.07f),
            Offset(r.left - i * 0.3f * u, r.top + i * 0.6f * u),
            Size(r.width + i * 0.6f * u, r.height + i * 0.3f * u),
            CornerRadius(corner + i * 0.5f * u),
        )
    }
}

// --- Geometry and timing -----------------------------------------------------------------------

/** A 100 × 100 square centred in the canvas, so each scene is drawn once, in its units, at any size. */
private class Units(size: Size) {
    val u = min(size.width, size.height) / 100f
    val left = (size.width - 100f * u) / 2f
    val top = (size.height - 100f * u) / 2f

    fun at(x: Float, y: Float) = Offset(left + x * u, top + y * u)

    fun rect(x: Float, y: Float, w: Float, h: Float) = Rect(at(x, y), Size(w * u, h * u))
}

/** The studio's sheet, so the picture shows the real 5 × 8 layout. */
private val SHEET = SheetTemplate.STUDIO_A4_5X8
private val PAPER_RATIO = (SHEET.paper.height / SHEET.paper.width).toFloat()
private const val PASSPORT = 35f / 45f

/** Slots already printed on the part-used sheet, the copies in this order, and the one handed over. */
private const val USED = 12
private const val COPIES = 10
private const val HANDED = USED + 7
private const val STEP = 0.075f
private const val FLIGHT = 0.2f
private const val LIFT = 0.8f

/** [index] slot of the sheet laid on [paper], from its millimetres. */
private fun slot(paper: Rect, index: Int): Rect {
    val k = paper.width / SHEET.paper.width.toFloat()
    val s = SHEET.slotRect(index)
    return Rect(Offset(paper.left + s.x.toFloat() * k, paper.top + s.y.toFloat() * k), Size(s.width.toFloat() * k, s.height.toFloat() * k))
}

private fun copyProgress(t: Float, i: Int): Float = eased(t, 0.06f + i * STEP, 0.06f + i * STEP + FLIGHT)

/** How far [t] is from [from] to [to], kept within 0..1. */
private fun span(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun eased(t: Float, from: Float, to: Float): Float = FastOutSlowInEasing.transform(span(t, from, to))

/** Overshoots a little and settles back, for things that pop in. */
private fun back(x: Float): Float {
    if (x <= 0f) return 0f
    val s = 1.7f
    val y = x - 1f
    return 1f + (s + 1f) * y * y * y + s * y * y
}

/** -1..1, once round per loop of [idle], started [phase] of a loop along. */
private fun wave(idle: Float, phase: Float = 0f): Float = sin((idle + phase) * 2f * PI.toFloat())

private fun lerpRect(a: Rect, b: Rect, f: Float) = Rect(lerp(a.left, b.left, f), lerp(a.top, b.top, f), lerp(a.right, b.right, f), lerp(a.bottom, b.bottom, f))

private fun scaleAbout(r: Rect, by: Float) =
    Rect(r.center.x - r.width * by / 2f, r.center.y - r.height * by / 2f, r.center.x + r.width * by / 2f, r.center.y + r.height * by / 2f)

// --- Colours of the made-up customer and room; the same in both themes, as a photo is --------

private val Skin = Color(0xFFD9A27C)
private val SkinLit = Color(0xFFEDC3A3)
private val Hair = Color(0xFF241D1A)
private val Mouth = Color(0xFF8E4F3E)
private val Shirt = Color(0xFF2F3A4F)
private val Collar = Color(0xFFF2F2F0)
private val Wall = Color(0xFFDCCBAA)
private val WallShade = Color(0xFFC2AD8A)
private val Sky = Color(0xFFBFDCF0)
private val Frame = Color(0xFF8E6A47)
private val Picture = Color(0xFF6E8F5A)
private val StudioTop = Color(0xFFB4D2F2)
private val StudioBottom = Color(0xFF6F9BD8)
private val Device = Color(0xFF1C1F27)
private val DeviceRim = Color(0xFF3A3F4C)
private val Rubber = Color(0xFF101217)
private val Silver = Color(0xFFB9BEC9)

// The shop front: warm and the same in both themes, as a real shop is.
private val ShopWall = Color(0xFFF4E9D4)
private val Wood = Color(0xFF8A5A3C)
private val SignBoard = Color(0xFF1E2433)
private val SignOff = Color(0xFF3A4152)
private val SignOn = Color(0xFFFFD27A)
private val Awning = Color(0xFF3E62E6)
private val AwningLight = Color(0xFFF7F4EE)
private val Glass = Color(0xFFCFE3F3)
private val Door = Color(0xFF2E3B5E)
private val ShutterGrey = Color(0xFFA9AEB8)
private val ShutterRib = Color(0xFF8D939E)
private val Bulb = Color(0xFFFFD98A)
private val Leaf = Color(0xFF4F8F5B)
private val Terracotta = Color(0xFFC2693E)
private val OpenGreen = Color(0xFF1F8553)
private val ClosedRed = Color(0xFFB3261E)
/** The camera is always dark, so its face box keeps one bright green in both themes. */
private val FaceFound = Color(0xFF7EE2AE)
private val Ready = Color(0xFF5BE39A)
