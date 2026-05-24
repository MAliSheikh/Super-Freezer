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
    primary = Color(0xFF81D2D2),      // Frosted cool aqua mint (Zero blue-glare primary)
    secondary = Color(0xFF5BA2A4),    // Quiet polar forest mint-teal
    tertiary = AlertIcyCyan,
    background = NordicIceDark,        // Deep frosted dark-slate background
    surface = Color(0xFF151F22),      // Cozy cool dark forest card surface
    onPrimary = NordicIceDark,
    onSecondary = SolidWhiteBg,
    onBackground = Color(0xFFDFECEE), // Soft low-glare frosted silver text
    onSurface = Color(0xFFDFECEE)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = GlacialAquaPrimary,     // Deep comforting forest-teal
    secondary = SoftMintContainer,    // Peaceful silver-mint secondary
    tertiary = AlertIcyCyan,
    background = CoolGlacialBg,       // Soft glacial ice cream background (eliminates white glare)
    surface = SolidWhiteBg,           // Pure snow-white card background
    onPrimary = SolidWhiteBg,
    onSecondary = NordicIceDark,
    onBackground = Color(0xFF162528), // Sleep-friendly deep graphite slate text
    onSurface = Color(0xFF162528)
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
