package com.booma.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.min

// Default background (the app's original dark look) and the logo's neon colours.
val BoomaDefaultBg = Color(0xFF0B0716)

/** The colours every screen draws with, worked out from the theme's background colour. */
data class BoomaColors(
    val core: Color,      // bright line colour: owl, play button, selected items
    val glow: Color,      // stronger accent: highlights, progress, the snake's body
    val muted: Color,     // secondary text
    val onCore: Color,    // text and icons on top of the bright colour
)

/** The logo's own colours, used by the Default theme. */
private val LogoColors = BoomaColors(
    core = Color(0xFFEAFCFF),
    glow = Color(0xFF6FE3FF),
    muted = Color(0xFFD9D2F4),
    onCore = BoomaDefaultBg,
)

private fun hsv(h: Float, s: Float, v: Float) = Color.hsv(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

private fun hsvOf(c: Color): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }

/**
 * Very bright picks (or album covers) are darkened so white text stays readable, as on PC.
 * Then, if needed, darkened further until white text has a contrast of at least 5:1 on it
 * (light greys and yellows can pass the first step and still be too bright for white text).
 */
fun effectiveBackground(c: Color): Color {
    val (h, s, v0) = hsvOf(c).let { Triple(it[0], it[1], it[2]) }
    var v = min(v0, 150f / 255f)
    var out = hsv(h, s, v)
    while (contrast(Color.White, lerp(out, Color.White, 0.12f)) < 5f && v > 0.05f) {
        v -= 0.03f
        out = hsv(h, s, v)
    }
    return out
}

/** WCAG contrast ratio between two colours (1 = none, 21 = black on white). */
fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** [c], lightened step by step until it reaches [min] contrast on [bg] (the theme backgrounds are always dark). */
fun readableOn(c: Color, bg: Color, min: Float): Color {
    var out = c
    var t = 0f
    while (contrast(out, bg) < min && t < 1f) {
        t += 0.1f
        out = lerp(c, Color.White, t)
    }
    return out
}

/**
 * Same formula as Booma on PC (make_theme): accents and text are light tints of the background's hue,
 * so every theme reads as one family. Grey and black themes stay neutral.
 */
fun boomaColorsFor(background: Color): BoomaColors {
    if (background == BoomaDefaultBg) return LogoColors
    val (h, s) = hsvOf(background).let { it[0] to it[1] * 255f }
    val k = min(1f, s / 90f)
    // checked against the lightest part of the screen (top of the gradient, cards), so text stays readable
    val lightest = lerp(background, Color.White, 0.14f)
    val core = readableOn(hsv(h, 32f * k / 255f, 1f), lightest, 7f)
    return BoomaColors(
        core = core,
        glow = readableOn(hsv(h, 132f * k / 255f, 1f), lightest, 4.5f),
        muted = readableOn(hsv(h, 30f * k / 255f, 244f / 255f), lightest, 4.5f),
        onCore = hsv(h, min(s, 171f) / 255f, 64f / 255f).let { if (contrast(it, core) >= 4.5f) it else Color(0xFF101010) },
    )
}

val LocalBoomaColors = staticCompositionLocalOf { LogoColors }

/** Bright line colour of the current theme. */
val OwlCore: Color
    @Composable @ReadOnlyComposable get() = LocalBoomaColors.current.core

/** Accent colour of the current theme. */
val OwlGlow: Color
    @Composable @ReadOnlyComposable get() = LocalBoomaColors.current.glow

// The same background themes as the PC app (names and colours match booma.py's PALETTE).
val BoomaPalette: List<Pair<String, Color>> = listOf(
    "Violet" to Color(0xFF3A1D68), "Indigo" to Color(0xFF283593), "Ocean" to Color(0xFF0D4A7A),
    "Teal" to Color(0xFF0B5D5B), "Emerald" to Color(0xFF14663C), "Forest" to Color(0xFF3C5A1E),
    "Sunset" to Color(0xFF8F3D10), "Crimson" to Color(0xFF7D1830), "Rose" to Color(0xFF8C2A5E),
    "Plum" to Color(0xFF5E1F70), "Slate" to Color(0xFF2E3A4E), "Graphite" to Color(0xFF2A2A2E),
    "Midnight" to Color(0xFF0C1233), "Black" to Color(0xFF000000),
    "Pine" to Color(0xFF173F2E), "Moss" to Color(0xFF4A5A22), "Mangrove" to Color(0xFF2F4F3A),
    "Autumn" to Color(0xFF6B3A12), "Cocoa" to Color(0xFF3E2A22), "Desert" to Color(0xFF7A5A2C),
    "Lagoon" to Color(0xFF0E5C6E), "Glacier" to Color(0xFF23485E), "Lavender" to Color(0xFF4F4478),
    "Dusk" to Color(0xFF3B2452),
)

/** Gradient (like the PC app's default) or a flat colour. */
val LocalBoomaGradient = staticCompositionLocalOf { true }

/** Whether the progress snake shows its head (You > Snake head), like the PC setting. */
val LocalSnakeHead = staticCompositionLocalOf { true }

/** Firmer type scale: bold, tightly set titles; calm body text. */
private val BoomaType = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun BoomaTheme(
    background: Color,
    gradient: Boolean = true,
    snakeHead: Boolean = true,
    content: @Composable () -> Unit,
) {
    val bg = remember(background) { if (background == BoomaDefaultBg) background else effectiveBackground(background) }
    val colors = remember(bg) { boomaColorsFor(bg) }
    val scheme = remember(bg, colors) { darkColorScheme(
        primary = colors.core,
        onPrimary = colors.onCore,
        secondary = colors.glow,
        onSecondary = colors.onCore,
        secondaryContainer = colors.glow.copy(alpha = 0.25f),
        onSecondaryContainer = Color.White,
        background = bg,
        onBackground = Color.White,
        surface = lerp(bg, Color.White, 0.06f),
        onSurface = Color.White,
        surfaceVariant = lerp(bg, Color.White, 0.12f),
        onSurfaceVariant = colors.muted,
        outline = colors.muted.copy(alpha = 0.5f),
        // menus, dialogs and sheets: a little lighter than the background, always with white text
        surfaceContainerLowest = lerp(bg, Color.Black, 0.3f),
        surfaceContainerLow = lerp(bg, Color.White, 0.05f),
        surfaceContainer = lerp(bg, Color.White, 0.08f),
        surfaceContainerHigh = lerp(bg, Color.White, 0.11f),
        surfaceContainerHighest = lerp(bg, Color.White, 0.15f),
        primaryContainer = colors.glow.copy(alpha = 0.3f).compositeOver(bg),
        onPrimaryContainer = Color.White,
        tertiary = colors.glow,
        onTertiary = colors.onCore,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        inverseSurface = Color.White,
        inverseOnSurface = bg,
    ) }
    CompositionLocalProvider(
        LocalBoomaGradient provides gradient,
        LocalSnakeHead provides snakeHead,
        LocalBoomaColors provides colors,
    ) {
        MaterialTheme(colorScheme = scheme, typography = BoomaType) {
            // Text and icons that don't name a colour are white, on every screen (also the ones drawn
            // outside a Surface, like Now Playing; there the default would be black).
            CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
        }
    }
}

/**
 * The app background: a soft top-to-bottom gradient from the theme colour, or the flat colour.
 * Theme changes fade here, in the drawing step only, so switching themes or songs stays smooth
 * (text and accents switch instantly; nothing else is rebuilt during the fade).
 */
@Composable
fun Modifier.boomaBackground(): Modifier {
    val target = MaterialTheme.colorScheme.background
    val bg = animateColorAsState(target, tween(700), label = "background")
    val gradient = LocalBoomaGradient.current
    // The gradient is built once per colour and size (not every frame) and drawn from that cache.
    return this.drawWithCache {
        val c = bg.value
        val brush = if (gradient) Brush.verticalGradient(listOf(lerp(c, Color.White, 0.10f), c, lerp(c, Color.Black, 0.6f))) else null
        onDrawBehind {
            if (brush != null) drawRect(brush) else drawRect(c)
        }
    }
}
