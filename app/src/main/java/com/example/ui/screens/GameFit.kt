package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Alto disponible para el juego en pantalla (lo fija MinigamesScreen); sin él se usa el alto del teléfono. */
internal val LocalGameAvail = compositionLocalOf<Dp?> { null }

@Composable
internal fun gameAvail(): Dp = LocalGameAvail.current ?: LocalConfiguration.current.screenHeightDp.dp

/**
 * Hace que todo el contenido entre en el alto disponible SIN scroll: si es más alto, se mide más ancho y se
 * reduce de forma uniforme (los toques siguen funcionando porque la escala se aplica como capa gráfica).
 */
internal fun Modifier.fitToHeight(minScale: Float = 0.42f): Modifier = layout { measurable, constraints ->
  val maxH = constraints.maxHeight
  val w = constraints.maxWidth
  if (maxH == Constraints.Infinity || w == Constraints.Infinity) {
    val p = measurable.measure(constraints)
    layout(p.width, p.height) { p.place(0, 0) }
  } else {
    var s = 1f
    var p = measurable.measure(Constraints(minWidth = w, maxWidth = w, maxHeight = Constraints.Infinity))
    for (i in 0 until 3) {
      if (p.height <= maxH) break
      s = (maxH.toFloat() / p.height).coerceIn(minScale, 1f)
      val ww = (w / s).toInt()
      p = measurable.measure(Constraints(minWidth = ww, maxWidth = ww, maxHeight = Constraints.Infinity))
    }
    if (p.height * s > maxH) s = (maxH.toFloat() / p.height).coerceIn(minScale, 1f)
    val shownW = (p.width * s).toInt()
    layout(w, maxH) {
      p.placeWithLayer((w - shownW) / 2, 0) {
        scaleX = s; scaleY = s
        transformOrigin = TransformOrigin(0f, 0f)
      }
    }
  }
}
