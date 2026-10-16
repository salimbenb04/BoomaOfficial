package com.booma.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Booma snake progress bar, ported from the PC app: while music plays, the played part is a snake
 * with a thin tail, a thick body and a wave sliding toward the head, which flicks its tongue every few
 * seconds. Paused, it settles into a plain bar. Tap or drag anywhere to seek.
 *
 * Everything that moves is read while drawing (not while building the screen), so the 60 fps wiggle
 * only repaints this bar.
 */
@Composable
fun SnakeSeekBar(
    positionMs: () -> Long,
    durationMs: () -> Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onScrub: (Float?) -> Unit = {},
) {
    val showHead = LocalSnakeHead.current
    val core = OwlCore
    val glow = OwlGlow
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)
    val position by rememberUpdatedState(positionMs)
    val duration by rememberUpdatedState(durationMs)
    val density = LocalDensity.current.density

    var drag by remember { mutableStateOf<Float?>(null) }
    val amp = animateFloatAsState(if (playing && drag == null) 1f else 0f, tween(700), label = "snakeAmp")
    val hover = animateFloatAsState(if (drag != null) 1f else 0f, tween(150), label = "snakeHover")

    // Glide smoothly between the player's position updates; the frame clock only runs while playing.
    val phase = remember { mutableFloatStateOf(0f) }
    val now = remember { mutableLongStateOf(System.nanoTime()) }
    val base = remember { mutableLongStateOf(0L) }
    val baseTime = remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(playing) {
        var last = 0L
        while (playing) {
            withFrameNanos { t ->
                if (last != 0L) phase.floatValue += ((t - last) / 1e9f).coerceAtMost(0.1f) * 9f
                last = t
                now.longValue = System.nanoTime()
                val p = position()
                if (p != base.longValue) {
                    base.longValue = p
                    baseTime.longValue = now.longValue
                }
            }
        }
        now.longValue = System.nanoTime()
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                val margin = 9f * density
                fun fractionAt(x: Float): Float =
                    ((x - margin) / (size.width - 2 * margin).coerceAtLeast(1f)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    drag = fractionAt(down.position.x)
                    scrub(drag)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        drag = fractionAt(change.position.x)
                        scrub(drag)
                        change.consume()
                    }
                    drag?.let { seek((it * duration()).toLong()) }
                    drag = null
                    scrub(null)
                }
            },
    ) {
        val d = density
        val a = amp.value
        val hv = hover.value
        val dur = duration()
        val shownMs = if (playing && drag == null) {
            (base.longValue + (now.longValue - baseTime.longValue) / 1_000_000).coerceIn(0L, max(dur, 0L))
        } else {
            position()
        }
        val fraction = drag ?: if (dur > 0) (shownMs.toFloat() / dur).coerceIn(0f, 1f) else 0f

        val m = 9f * d
        val span = (size.width - 2 * m).coerceAtLeast(1f)
        val cy = size.height / 2
        val gh = (5f + 3f * hv) * d
        val body = Brush.horizontalGradient(listOf(glow, core), startX = m, endX = m + span)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.24f),
            topLeft = Offset(m, cy - gh / 2),
            size = Size(span, gh),
            cornerRadius = CornerRadius(gh / 2),
        )

        val x = m + span * fraction
        if (a < 0.01f) {
            drawRoundRect(
                brush = body,
                topLeft = Offset(m, cy - gh / 2),
                size = Size(max(span * fraction, gh), gh),
                cornerRadius = CornerRadius(gh / 2),
            )
        } else {
            // the snake: thin tail fading in, thick body, wave sliding toward the head
            val length = x - m
            if (length > 0.5f) {
                val n = max((length / (3f * d)).toInt(), 1)
                val ph = phase.floatValue
                var prev: Offset? = null
                for (i in 0..n) {
                    val px = m + length * i / n
                    val tail = (px - m) / d
                    val head = (x - px) / d
                    var env = min(1f, tail / 46f)
                    env = env * env * (3 - 2 * env)
                    env *= min(1f, head / 14f)
                    val y = cy + 4.2f * d * a * env * sin(0.16f * (px / d) - ph)
                    val thick = (1.4f + (5f + 3f * hv + 1.5f - 1.4f) * min(1f, tail / 110f)) * d
                    val point = Offset(px, y)
                    val p0 = prev
                    if (p0 != null) {
                        drawLine(brush = body, start = p0, end = point, strokeWidth = gh + (thick - gh) * a, cap = StrokeCap.Round)
                    }
                    prev = point
                }
            }
        }

        val r = (4.5f + 3f * hv + if (drag != null) 1f else 0f) * d
        if (hv > 0.02f) {
            drawCircle(
                brush = Brush.radialGradient(listOf(glow.copy(alpha = 0.47f * hv), Color.Transparent), center = Offset(x, cy), radius = r * 2.6f),
                radius = r * 2.6f,
                center = Offset(x, cy),
            )
        }
        if (showHead) {
            val rx = r + 2.2f * d * a           // round when paused, oval when slithering
            val ry = r + 0.2f * d * a
            if (a > 0.6f && drag == null) {     // tongue flick every few seconds
                val tph = ((now.longValue / 1e9) % 3.4).toFloat()
                if (tph < 0.4f) {
                    val ext = sin(PI.toFloat() * tph / 0.4f) * 6f * d
                    val x0 = x + rx - d
                    val tongue = Color(0xFFFF8FB1)
                    val sw = 1.2f * d
                    drawLine(color = tongue, start = Offset(x0, cy), end = Offset(x0 + ext, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(color = tongue, start = Offset(x0 + ext, cy), end = Offset(x0 + ext + 2.2f * d, cy - 1.8f * d), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(color = tongue, start = Offset(x0 + ext, cy), end = Offset(x0 + ext + 2.2f * d, cy + 1.8f * d), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }
            drawOval(color = Color.White, topLeft = Offset(x - rx, cy - ry), size = Size(rx * 2, ry * 2))
            if (a > 0.05f) {
                val eye = Color(0xFF241540).copy(alpha = a)
                val er = (1.1f + 0.3f * hv) * d
                drawCircle(color = eye, radius = er, center = Offset(x + rx * 0.35f, cy - ry * 0.45f))
                drawCircle(color = eye, radius = er, center = Offset(x + rx * 0.35f, cy + ry * 0.45f))
            }
        }
    }
}
