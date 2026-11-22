package io.github.abusaeedbd.unseen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// iOS 18 dark system colours
val IosBlack = Color(0xFF000000)
val Grouped = Color(0xFF1C1C1E)
val Elevated = Color(0xFF2C2C2E)
val Tertiary = Color(0xFF3A3A3C)
val SwitchOff = Color(0xFF39393D)
val Label2 = Color(0xFF8E8E93)
val Label3 = Color(0xFF636366)
val IosGreen = Color(0xFF30D158)
val IosOrange = Color(0xFFFF9F0A)
val IosRed = Color(0xFFFF453A)
val IosBlue = Color(0xFF0A84FF)
val IosCyan = Color(0xFF64D2FF)
val IosYellow = Color(0xFFFFD60A)
val Hairline = Color(0xA6545458)
val CardBorder = Color(0x14FFFFFF)

// Unseen brand
val Accent = Color(0xFF8B6CFF)
val AccentSoft = Color(0xFFA78BFA)
val BrandInk = Color(0xFF12152A)

// Older names still used in a few places
val Ink = IosBlack
val CardBg = Grouped
val Muted = Label2
val Warning = IosOrange
val Good = IosGreen
val Bad = IosRed

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = IosBlack,
    onBackground = Color.White,
    surface = Grouped,
    onSurface = Color.White,
    surfaceVariant = Elevated,
    onSurfaceVariant = Label2,
    surfaceContainerLow = Grouped,
    surfaceContainer = Grouped,
)

@Composable
fun UnseenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
