package com.spotter.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Spotter's palette.
 *
 * **Dark because of where this screen physically is, not because dark looks modern.** The phone is
 * lying on the floor two metres away, angled up at someone who is moving. A near-white screen down
 * there is a glare source in exactly the wrong place — under gym lighting it washes out, and it is
 * the brightest thing in your lower field of view while you are trying to balance.
 *
 * Warm charcoal rather than pure black: black clips to nothing on an OLED and the text edges
 * shimmer as the phone vibrates on the floor with each rep.
 *
 * Colour here carries meaning and nothing else. [danger] is only ever knee cave, because that is
 * the fault that injures people; [caution] is form worth fixing but not urgent; [good] means the
 * rep was clean. No gradients, no glows, no accent colour applied for decoration — at two metres
 * and mid-movement the only thing a colour can usefully do is tell you how worried to be.
 */
data class SpotterColors(
    val floor: Color,
    val ink: Color,
    val inkMuted: Color,
    val good: Color,
    val caution: Color,
    val danger: Color,
)

private val Colors = SpotterColors(
    floor = Color(0xFF14120F),
    ink = Color(0xFFF4EFE6),
    inkMuted = Color(0xFF8A8175),
    good = Color(0xFF7FB069),
    caution = Color(0xFFE8A44A),
    danger = Color(0xFFE0603F),
)

val LocalSpotterColors = staticCompositionLocalOf { Colors }

/**
 * Type sized for the distance it is read from.
 *
 * These are much larger than a phone app normally uses, and that is the whole point: every other
 * app on this device is read at arm's length by someone standing still. This one is read from the
 * floor, at two metres, by someone halfway through a squat with a bar on their back. [count] and
 * [callout] are sized so they are legible in peripheral vision — you should not have to look
 * directly at the phone to know you got the rep.
 */
private val SpotterType = Typography(
    // The rep count. Enormous on purpose — it is the one thing worth seeing without looking.
    displayLarge = TextStyle(
        fontSize = 148.sp,
        lineHeight = 148.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-6).sp,
    ),
    // The single correction, when there is one.
    headlineLarge = TextStyle(
        fontSize = 56.sp,
        lineHeight = 60.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1).sp,
    ),
    // Setup instructions, read while standing still and close — normal sizes are right here.
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SpotterTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpotterColors provides Colors) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = Colors.floor,
                surface = Colors.floor,
                onBackground = Colors.ink,
                onSurface = Colors.ink,
                primary = Colors.good,
                onPrimary = Colors.floor,
            ),
            typography = SpotterType,
            content = content,
        )
    }
}
