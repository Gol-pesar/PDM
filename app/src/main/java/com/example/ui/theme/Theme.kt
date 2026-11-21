package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemePreference

private val PdmLightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = OnTealPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = OnTealPrimaryContainer,
    secondary = CoralSecondary,
    onSecondary = OnCoralSecondary,
    secondaryContainer = CoralSecondaryContainer,
    onSecondaryContainer = OnCoralSecondaryContainer,
    tertiary = SkyTertiary,
    onTertiary = OnSkyTertiary,
    tertiaryContainer = SkyTertiaryContainer,
    onTertiaryContainer = OnSkyTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = StatusCrimson,
    onError = OnTealPrimary
)

private val PdmDarkColorScheme = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = DarkOnTealPrimary,
    primaryContainer = DarkTealPrimaryContainer,
    onPrimaryContainer = DarkOnTealPrimaryContainer,
    secondary = DarkCoralSecondary,
    onSecondary = DarkOnCoralSecondary,
    secondaryContainer = DarkCoralSecondaryContainer,
    onSecondaryContainer = DarkOnCoralSecondaryContainer,
    tertiary = DarkSkyTertiary,
    onTertiary = DarkOnSkyTertiary,
    tertiaryContainer = DarkSkyTertiaryContainer,
    onTertiaryContainer = DarkOnSkyTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = StatusCrimson,
    onError = DarkOnTealPrimary
)

val PdmShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PdmTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (darkTheme) PdmDarkColorScheme else PdmLightColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = PdmShapes,
            content = content
        )
    }
}
