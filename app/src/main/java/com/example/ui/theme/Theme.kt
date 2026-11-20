package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

enum class AppThemeMode(val displayName: String, val primaryColorHex: Long) {
  AURORA("KOMBO Arena (negro + verde neón)", 0xFF39FF14),
  PASION("Rosa Pasión", 0xFFFF2E63),
  DORADA("Noche Dorada", 0xFFFFC857),
  ULTRAVIOLETA("Ultravioleta", 0xFF8B5CF6),
  OCEANO("Océano Neón", 0xFF22E3C5),
  NEGRO_ROSA("Negro + Rosa", 0xFFFF2E93),
  NEGRO_CIAN("Negro + Cian", 0xFF00E5FF),
  NEGRO_LIMA("Negro + Lima", 0xFFB6FF3B),
  BLANCO_ROSA("Blanco + Rosa", 0xFFFF2E93),
  BLANCO_AZUL("Blanco + Azul", 0xFF2979FF),
  BLANCO_VERDE("Blanco + Menta", 0xFF00BFA5)
}

/** Colores extra del diseño (degradados y brillos) disponibles en toda la app. */
data class DuoPalette(
  val bgTop: Color,
  val bgBottom: Color,
  val accentA: Color,
  val accentB: Color,
  val glow: Color,
  val isLight: Boolean = false,
) {
  val accentBrush: Brush get() = Brush.horizontalGradient(listOf(accentA, accentB))
  val accentDiagonal: Brush get() = Brush.linearGradient(listOf(accentA, accentB))
  val backgroundBrush: Brush get() = Brush.verticalGradient(listOf(bgTop, bgBottom))
}

val LocalDuoPalette = staticCompositionLocalOf {
  DuoPalette(AuroraBgTop, AuroraBgBottom, AuroraPink, AuroraCoral, AuroraViolet)
}

private fun scheme(bgTop: Color, bgBottom: Color, surface: Color, a: Color, b: Color): ColorScheme = darkColorScheme(
  primary = a,
  onPrimary = if (a.luminance() > 0.45f) Color(0xFF04120A) else Color.White,
  primaryContainer = a.copy(alpha = 0.22f).compositeOver(surface),
  onPrimaryContainer = Color.White,
  secondary = b,
  onSecondary = if (b.luminance() > 0.45f) Color(0xFF04120A) else Color.White,
  secondaryContainer = b.copy(alpha = 0.22f).compositeOver(surface),
  onSecondaryContainer = Color.White,
  tertiary = AuroraCyan,
  background = Color.Transparent,
  onBackground = Color(0xFFF7F2FF),
  surface = surface,
  onSurface = Color(0xFFF7F2FF),
  surfaceVariant = Color.White.copy(alpha = 0.08f).compositeOver(surface),
  onSurfaceVariant = Color(0xFFC9BEDD),
  outline = Color(0x33FFFFFF),
  error = Color(0xFFFF6B8B)
)

private fun Color.compositeOver(background: Color): Color {
  val a = this.alpha
  return Color(
    red = this.red * a + background.red * (1 - a),
    green = this.green * a + background.green * (1 - a),
    blue = this.blue * a + background.blue * (1 - a),
    alpha = 1f
  )
}

@Composable
fun KomboArenaTheme(
  appTheme: AppThemeMode = AppThemeMode.AURORA,
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val palette = when (appTheme) {
    AppThemeMode.AURORA -> DuoPalette(AuroraBgTop, AuroraBgBottom, AuroraPink, AuroraCoral, AuroraViolet)
    AppThemeMode.PASION -> DuoPalette(PasionBgTop, PasionBgBottom, PasionPink, PasionCoral, PasionPink)
    AppThemeMode.DORADA -> DuoPalette(GoldBgTop, GoldBgBottom, GoldMain, GoldSoft, GoldMain)
    AppThemeMode.ULTRAVIOLETA -> DuoPalette(UvBgTop, UvBgBottom, UvMain, UvSoft, UvMain)
    AppThemeMode.OCEANO -> DuoPalette(OceanBgTop, OceanBgBottom, OceanMain, OceanSoft, OceanMain)
    AppThemeMode.NEGRO_ROSA -> DuoPalette(Color(0xFF000000), Color(0xFF000000), Color(0xFFFF2E93), Color(0xFFFF6BCB), Color(0xFFFF2E93))
    AppThemeMode.NEGRO_CIAN -> DuoPalette(Color(0xFF000000), Color(0xFF000000), Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF00E5FF))
    AppThemeMode.NEGRO_LIMA -> DuoPalette(Color(0xFF000000), Color(0xFF000000), Color(0xFFB6FF3B), Color(0xFF00F5A0), Color(0xFFB6FF3B))
    AppThemeMode.BLANCO_ROSA -> DuoPalette(Color(0xFFFFFFFF), Color(0xFFFFEAF4), Color(0xFFFF2E93), Color(0xFFFF8A5B), Color(0xFFFF2E93), true)
    AppThemeMode.BLANCO_AZUL -> DuoPalette(Color(0xFFFFFFFF), Color(0xFFE8F0FF), Color(0xFF2979FF), Color(0xFF00B8D4), Color(0xFF2979FF), true)
    AppThemeMode.BLANCO_VERDE -> DuoPalette(Color(0xFFFFFFFF), Color(0xFFE6FAF5), Color(0xFF00BFA5), Color(0xFF64DD17), Color(0xFF00BFA5), true)
  }
  val surface = when (appTheme) {
    AppThemeMode.AURORA -> AuroraSurface
    AppThemeMode.PASION -> PasionSurface
    AppThemeMode.DORADA -> GoldSurface
    AppThemeMode.ULTRAVIOLETA -> UvSurface
    AppThemeMode.OCEANO -> OceanSurface
    AppThemeMode.NEGRO_ROSA -> Color(0xFF141014)
    AppThemeMode.NEGRO_CIAN -> Color(0xFF0D1418)
    AppThemeMode.NEGRO_LIMA -> Color(0xFF11140A)
    AppThemeMode.BLANCO_ROSA, AppThemeMode.BLANCO_AZUL, AppThemeMode.BLANCO_VERDE -> Color(0xFFFFFFFF)
  }
  val colorScheme = if (palette.isLight) {
    androidx.compose.material3.lightColorScheme(
      primary = palette.accentA, onPrimary = Color.White, secondary = palette.accentB, onSecondary = Color.White,
      background = Color.Transparent, onBackground = Color(0xFF1A1030), surface = Color.White, onSurface = Color(0xFF1A1030),
      surfaceVariant = Color(0xFFEDE7F6), onSurfaceVariant = Color(0xFF5A4F73), outline = Color(0x33000000)
    )
  } else scheme(palette.bgTop, palette.bgBottom, surface, palette.accentA, palette.accentB)
  val view = androidx.compose.ui.platform.LocalView.current
  androidx.compose.runtime.SideEffect {
    (view.context as? android.app.Activity)?.window?.let { w ->
      val ctrl = androidx.core.view.WindowCompat.getInsetsController(w, view)
      ctrl.isAppearanceLightStatusBars = palette.isLight
      ctrl.isAppearanceLightNavigationBars = palette.isLight
    }
  }
  CompositionLocalProvider(LocalDuoPalette provides palette) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
