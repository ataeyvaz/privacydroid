package io.privacydroid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryGreenDim,
    onPrimaryContainer = PrimaryGreen,

    secondary = SecondaryTeal,
    onSecondary = OnSecondary,

    background = DarkBackground,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    outline = DarkOutline,

    error = AlertRed,
    onError = TextOnDark,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = OnLightPrimary,
    background = LightBackground,
    surface = LightSurface,
)

/**
 * PrivacyDroid tema wrapper. Koyu tema varsayılan ve önerilen moddur.
 * Dynamic Color yalnızca Android 12+ cihazlarda etkinleştirilir, ancak
 * uygulama kendi izin renk paletini korur.
 */
@Composable
fun PrivacyDroidTheme(
    darkTheme: Boolean = true, // Her zaman koyu tema varsayılan
    dynamicColor: Boolean = false, // Dynamic color devre dışı — marka tutarlılığı için
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PrivacyDroidTypography,
        content = content
    )
}
