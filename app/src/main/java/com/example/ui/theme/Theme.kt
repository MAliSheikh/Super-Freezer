package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF8DBF9E),      // Peaceful light sage green
    secondary = Color(0xFFC68B45),    // Warm honey goldenrod
    tertiary = AlertWarningGold,
    background = DeepEspresso,        // Warm charcoal background
    surface = Color(0xFF1B221D),      // Gentle soft dark green-gray surface
    onPrimary = DeepEspresso,
    onSecondary = SolidWhiteBg,
    onBackground = Color(0xFFE4EDE6), // Soft off-white to eliminate high-contrast glare
    onSurface = Color(0xFFE4EDE6)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = TerracottaPrimary,      // Serene herbal green
    secondary = Color(0xFFC68B45),    // Warm honey goldenrod
    tertiary = AlertWarningGold,
    background = WarmLinenBg,         // Ultra-soft pale linen green background
    surface = SolidWhiteBg,
    onPrimary = SolidWhiteBg,
    onSecondary = DeepEspresso,
    onBackground = DeepEspresso,
    onSurface = DeepEspresso
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
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
