package com.booma.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

// Booma the owl, drawn in code from the logo outline (original artwork, laid out on a 1024 grid).

private fun headPath(): Path = Path().apply {
    moveTo(512f, 335f)
    cubicTo(470f, 270f, 390f, 255f, 330f, 268f)
    cubicTo(240f, 288f, 195f, 380f, 200f, 480f)
    cubicTo(210f, 640f, 380f, 760f, 512f, 865f)
    cubicTo(644f, 760f, 814f, 640f, 824f, 480f)
    cubicTo(829f, 380f, 784f, 288f, 694f, 268f)
    cubicTo(634f, 255f, 554f, 270f, 512f, 335f)
    close()
}

private fun headbandPath(outer: Boolean): Path = Path().apply {
    // Half-circle over the top, from the right side to the left side.
    val rect = if (outer) Rect(132f, 100f, 892f, 860f) else Rect(182f, 150f, 842f, 810f)
    arcTo(rect, 0f, -180f, true)
}

private fun earpadPath(left: Boolean): Path = Path().apply {
    val x = if (left) 102f else 826f
    val cushionX = if (left) 160f else 824f
    addRoundRect(RoundRect(x, 432f, x + 96f, 658f, 40f, 40f))
    addRoundRect(RoundRect(cushionX, 440f, cushionX + 40f, 650f, 20f, 20f))
}

private fun beakPath(): Path = Path().apply {
    moveTo(430f, 500f)
    cubicTo(440f, 600f, 480f, 690f, 512f, 775f)
    cubicTo(544f, 690f, 584f, 600f, 594f, 500f)
}

private fun nosePath(): Path = Path().apply {
    moveTo(512f, 408f)
    lineTo(512f, 578f)
}

private fun tipPath(): Path = Path().apply {
    moveTo(512f, 625f)
    lineTo(550f, 662f)
    lineTo(512f, 698f)
    lineTo(474f, 662f)
    close()
}

private val staticStrokes: List<Path> by lazy {
    listOf(
        headPath(),
        headbandPath(outer = true),
        headbandPath(outer = false),
        earpadPath(left = true),
        earpadPath(left = false),
        beakPath(),
        nosePath(),
        tipPath(),
    )
}

@Composable
fun BoomaOwl(
    modifier: Modifier = Modifier.size(56.dp),
    playing: Boolean = false,
    // -1..1 each axis: where the pupils look
    lookX: Float = 0f,
    lookY: Float = 0f,
) {
    // Animation values are read only while drawing, so the owl repaints without rebuilding the screen.
    // The beat (625 ms, about 96 a minute) only runs while music plays.
    val beat = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                beat.animateTo(1f, tween(625, easing = LinearEasing))
                beat.snapTo(0f)
            }
        }
    }
    val groove = animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(400),
        label = "groove",
    )
    val blink = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2500, 5500))
            blink.animateTo(1f, tween(80))
            blink.animateTo(0f, tween(120))
        }
    }

    val core = OwlCore   // the owl takes the theme's colours
    val glow = OwlGlow
    Canvas(modifier = modifier) {
        val g = groove.value
        val b = beat.value * 2f * PI.toFloat()
        val bob = sin(b) * 8f * g
        val tilt = sin(b / 2f) * 2.5f * g
        val pulse = 1f + 0.02f * g * (1f + cos(b)) / 2f
        val squash = (1f - 0.92f * blink.value).coerceAtLeast(0.04f)
        val s = min(size.width, size.height) / 1024f

        scale(s, Offset.Zero) {
            withTransform({
                translate(512f, 560f)
                rotate(tilt, Offset.Zero)
                scale(pulse, pulse, Offset.Zero)
                translate(-512f, -560f + bob)
            }) {
                drawOwlLines(squash, lookX, lookY, core, glow)
            }
        }
    }
}

private val GlowWide = Stroke(width = 96f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val GlowNarrow = Stroke(width = 56f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val CoreLine = Stroke(width = 34f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawOwlLines(squash: Float, lookX: Float, lookY: Float, OwlCore: Color, OwlGlow: Color) {
    val eyes = listOf(358f, 666f).map { ex ->
        Path().apply {
            addOval(Rect(ex - 62f, 468f - 62f * squash, ex + 62f, 468f + 62f * squash))
        }
    }
    val lines = staticStrokes + eyes

    // neon: two soft glow passes, then the crisp core line
    for ((width, alpha) in listOf(96f to 0.10f, 56f to 0.24f)) {
        for (path in lines) {
            drawPath(
                path = path,
                color = OwlGlow.copy(alpha = alpha),
                style = if (width > 90f) GlowWide else GlowNarrow,
            )
        }
    }
    for (path in lines) {
        drawPath(
            path = path,
            color = OwlCore,
            style = CoreLine,
        )
    }

    // pupils: glowing dots that lean toward lookX / lookY
    for (ex in listOf(358f, 666f)) {
        val px = ex + lookX * 34f
        val py = 468f + lookY * 34f
        val pr = 14f
        drawOval(
            color = OwlGlow.copy(alpha = 0.3f),
            topLeft = Offset(px - pr * 1.9f, py - pr * 1.9f * squash.coerceAtLeast(0.08f)),
            size = Size(pr * 3.8f, pr * 3.8f * squash.coerceAtLeast(0.08f)),
        )
        drawOval(
            color = OwlCore,
            topLeft = Offset(px - pr, py - pr * squash.coerceAtLeast(0.08f)),
            size = Size(pr * 2f, pr * 2f * squash.coerceAtLeast(0.08f)),
        )
    }
}
