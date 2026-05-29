package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Emerald80,
    secondary = CharcoalGrey80,
    tertiary = CoolGreen80,
    background = DarkSoftBg,
    surface = DarkCardBg,
    surfaceVariant = Color(0xFF232D2B),
    onPrimary = Color(0xFF00371E),
    onSecondary = Color(0xFF1C2825),
    onBackground = Color(0xFFECEFF1),
    onSurface = Color(0xFFECEFF1)
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald40,
    secondary = SlateGrey40,
    tertiary = CoolGreen40,
    background = LightSoftBg,
    surface = Color.White,
    surfaceVariant = Color(0xFFE0EAE6),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A2120),
    onSurface = Color(0xFF1A2120)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
