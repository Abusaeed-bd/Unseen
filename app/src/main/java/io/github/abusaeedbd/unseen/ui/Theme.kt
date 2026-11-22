package io.github.abusaeedbd.unseen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF12152A)
val Accent = Color(0xFF8B6CFF)
val CardBg = Color(0xFF1C2140)
val Muted = Color(0xFFA3A7C2)
val Warning = Color(0xFFFFB25C)
val Good = Color(0xFF4CD08A)
val Bad = Color(0xFFFF6B6B)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Ink,
    onBackground = Color.White,
    surface = CardBg,
    onSurface = Color.White,
    surfaceVariant = CardBg,
    onSurfaceVariant = Muted,
)

@Composable
fun UnseenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
