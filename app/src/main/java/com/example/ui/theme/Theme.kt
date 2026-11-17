package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AboAlAtaDarkColorScheme = darkColorScheme(
    primary = RoyalGoldPrimary,
    onPrimary = OnRoyalGold,
    primaryContainer = RoyalGoldContainer,
    onPrimaryContainer = OnRoyalGoldContainer,
    secondary = RoyalGoldBright,
    onSecondary = OnRoyalGold,
    secondaryContainer = ObsidianSurfaceHigh,
    onSecondaryContainer = WarmIvoryText,
    tertiary = EmeraldSuccess,
    onTertiary = OnRoyalGold,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = ObsidianBackground,
    onBackground = WarmIvoryText,
    surface = ObsidianSurface,
    onSurface = WarmIvoryText,
    surfaceVariant = ObsidianSurfaceContainer,
    onSurfaceVariant = MutedChampagneText,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    error = CrimsonError,
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = OnCrimsonErrorContainer,
)

private val AboAlAtaLightColorScheme = lightColorScheme(
    primary = EspressoPrimary,
    onPrimary = OnEspressoPrimary,
    primaryContainer = RoyalGoldBright,
    onPrimaryContainer = OnRoyalGold,
    secondary = RoyalGoldDeep,
    onSecondary = OnEspressoPrimary,
    secondaryContainer = CreamSurfaceVariant,
    onSecondaryContainer = EspressoText,
    tertiary = EmeraldSuccess,
    onTertiary = OnRoyalGold,
    tertiaryContainer = OnEmeraldContainer,
    onTertiaryContainer = EmeraldContainer,
    background = CreamBackground,
    onBackground = EspressoText,
    surface = CreamSurface,
    onSurface = EspressoText,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = MutedEspressoText,
    outline = RoyalGoldDeep,
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AboAlAtaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AboAlAtaDarkColorScheme else AboAlAtaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
