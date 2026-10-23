package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.ui.theme.DuoPalette
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.LocalDuoPalette
import kotlin.math.cos
import kotlin.math.sin

/** Estilos del fondo animado de toda la app (se eligen en Configuración). */
enum class BgStyle(val label: String, val emoji: String) {
  AURORA("Aurora", "🌌"), BOKEH("Burbujas de luz", "🫧"), WAVES("Olas", "🌊"),
  STARS("Cielo de estrellas", "✨"), LAVA("Lámpara de lava", "🔮"), OFF("Sin movimiento", "⏸️")
}

object BackgroundPrefs {
  var style by mutableStateOf(BgStyle.AURORA)
  private var loaded = false
  fun ensureLoaded(ctx: android.content.Context) {
    if (loaded) return
    loaded = true
    val name = ctx.getSharedPreferences("sini_joss_ui", android.content.Context.MODE_PRIVATE).getString("bg_style", null)
    style = BgStyle.values().firstOrNull { it.name == name } ?: BgStyle.AURORA
  }
  fun set(ctx: android.content.Context, s: BgStyle) {
    style = s
    ctx.getSharedPreferences("sini_joss_ui", android.content.Context.MODE_PRIVATE).edit().putString("bg_style", s.name).apply()
  }
}

private const val TAU = 6.2831855f

private fun DrawScope.drawAuroraOrbs(ph: Float, p: DuoPalette) {
  val w = size.width; val h = size.height
  val c1 = Offset(w * (0.18f + 0.14f * cos(ph)), h * (0.12f + 0.07f * sin(ph)))
  val c2 = Offset(w * (0.88f + 0.08f * sin(ph)), h * (0.78f + 0.07f * cos(ph)))
  val c3 = Offset(w * (0.55f + 0.18f * sin(ph * 0.5f)), h * (0.45f + 0.10f * cos(ph * 0.5f)))
  val soft = if (p.isLight) 0.16f else 0.30f
  drawCircle(Brush.radialGradient(listOf(p.accentA.copy(alpha = soft), Color.Transparent), center = c1, radius = w * 0.80f), w * 0.80f, c1)
  drawCircle(Brush.radialGradient(listOf(p.glow.copy(alpha = soft * 0.9f), Color.Transparent), center = c2, radius = w * 0.85f), w * 0.85f, c2)
  drawCircle(Brush.radialGradient(listOf(p.accentB.copy(alpha = soft * 0.45f), Color.Transparent), center = c3, radius = w * 0.55f), w * 0.55f, c3)
  for (i in 0 until 26) {
    val seed = (i * 53 % 97) / 97f
    val prog = ((ph / TAU) * (1 + i % 3) + seed) % 1f
    val x = w * (((i * 37) % 100) / 100f) + sin(ph + i) * 6f
    val y = h * (1f - prog)
    val a = (if (p.isLight) 0.18f else 0.38f) * sin(prog * 3.1415927f)
    drawCircle(p.glow.copy(alpha = a), 1.2f + (i % 3) * 0.9f, Offset(x, y))
  }
}

private fun DrawScope.drawBokeh(ph: Float, p: DuoPalette) {
  val w = size.width; val h = size.height
  val cols = listOf(p.accentA, p.accentB, p.glow)
  for (i in 0 until 18) {
    val seed = (i * 0.61803f) % 1f
    val prog = ((ph / TAU) * (1 + i % 3) + seed) % 1f
    val x = w * (((i * 37) % 100) / 100f) + sin(ph * (1 + i % 2) + i) * w * 0.05f
    val y = h * (1.15f - prog * 1.3f)
    val r = w * (0.05f + 0.10f * ((i * 17) % 10) / 10f)
    val a = (if (p.isLight) 0.20f else 0.32f) * sin(prog * 3.1415927f).coerceAtLeast(0f)
    drawCircle(Brush.radialGradient(listOf(cols[i % 3].copy(alpha = a), cols[i % 3].copy(alpha = a * 0.25f), Color.Transparent), center = Offset(x, y), radius = r), r, Offset(x, y))
    drawCircle(cols[i % 3].copy(alpha = a * 0.7f), r * 0.98f, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f))
  }
}

private fun DrawScope.drawWaves(ph: Float, p: DuoPalette) {
  val w = size.width; val h = size.height
  val cols = listOf(p.accentA, p.glow, p.accentB, p.accentA)
  for (layer in 0 until 4) {
    val path = Path()
    val baseY = h * (0.58f + layer * 0.11f)
    path.moveTo(0f, h)
    var x = 0f
    val dir = if (layer % 2 == 0) 1f else -1f
    while (x <= w + 8f) {
      val y = baseY + sin(x / w * TAU * (1.2f + layer * 0.35f) + ph * (1 + layer) * dir) * h * 0.022f * (1f + layer * 0.35f)
      path.lineTo(x, y)
      x += 8f
    }
    path.lineTo(w, h); path.close()
    drawPath(path, cols[layer].copy(alpha = if (p.isLight) 0.10f else 0.17f))
  }
  val glowC = Offset(w * 0.5f, h * 0.15f)
  drawCircle(Brush.radialGradient(listOf(p.accentB.copy(alpha = if (p.isLight) 0.10f else 0.22f), Color.Transparent), center = glowC, radius = w * 0.7f), w * 0.7f, glowC)
}

private fun DrawScope.drawStarSky(ph: Float, p: DuoPalette) {
  val w = size.width; val h = size.height
  for (i in 0 until 70) {
    val layer = i % 3
    val bx = ((i * 47) % 100) / 100f
    val by = ((i * 29 + 13) % 100) / 100f
    val x = bx * w + sin(ph + i) * w * 0.012f * (layer + 1)
    val y = by * h + cos(ph * (1 + i % 2) + i) * h * 0.006f * (layer + 1)
    val tw = 0.25f + 0.75f * kotlin.math.abs(sin(ph * (1 + i % 3) + i))
    val rr = (0.8f + layer * 0.7f)
    drawCircle((if (i % 5 == 0) p.accentB else Color.White).copy(alpha = (if (p.isLight) 0.30f else 0.75f) * tw * (0.5f + layer * 0.25f)), rr, Offset(x, y))
  }
  // estrellas fugaces: dos por vuelta
  val t = ((ph / TAU) * 2f) % 1f
  if (t < 0.18f) {
    val k = t / 0.18f
    val sx = w * (0.15f + 0.5f * ((ph / TAU * 2f).toInt() % 2)) + k * w * 0.35f
    val sy = h * 0.08f + k * h * 0.22f
    drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * (1f - k))), start = Offset(sx - 60f, sy - 34f), end = Offset(sx, sy)), Offset(sx - 60f, sy - 34f), Offset(sx, sy), strokeWidth = 2.5f)
  }
  val c = Offset(w * 0.8f, h * 0.2f)
  drawCircle(Brush.radialGradient(listOf(p.accentA.copy(alpha = if (p.isLight) 0.08f else 0.20f), Color.Transparent), center = c, radius = w * 0.6f), w * 0.6f, c)
}

private fun DrawScope.drawLava(ph: Float, p: DuoPalette) {
  val w = size.width; val h = size.height
  val cols = listOf(p.accentA, p.accentB, p.glow)
  for (i in 0 until 6) {
    val cx = w * (0.5f + 0.40f * sin(ph * (1 + i % 2) + i * 1.3f))
    val cy = h * (0.5f + 0.42f * cos(ph * (1 + (i + 1) % 2) + i * 0.9f))
    val r = w * (0.42f + 0.10f * sin(ph * 2f + i))
    drawCircle(Brush.radialGradient(listOf(cols[i % 3].copy(alpha = if (p.isLight) 0.18f else 0.34f), Color.Transparent), center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
  }
}

/** Fondo degradado con movimiento lento y sutil; el estilo se elige en Configuración. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  val p = LocalDuoPalette.current
  val ctx = LocalContext.current
  remember { BackgroundPrefs.ensureLoaded(ctx) }
  val style = BackgroundPrefs.style
  val t = rememberInfiniteTransition(label = "aurora")
  val phase by t.animateFloat(
    initialValue = 0f,
    targetValue = TAU,
    animationSpec = infiniteRepeatable(tween(if (style == BgStyle.STARS || style == BgStyle.LAVA) 40000 else 26000, easing = LinearEasing), RepeatMode.Restart),
    label = "phase"
  )
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(p.backgroundBrush)
      .drawBehind {
        when (style) {
          BgStyle.AURORA -> drawAuroraOrbs(phase, p)
          BgStyle.BOKEH -> drawBokeh(phase, p)
          BgStyle.WAVES -> drawWaves(phase, p)
          BgStyle.STARS -> drawStarSky(phase, p)
          BgStyle.LAVA -> drawLava(phase, p)
          BgStyle.OFF -> Unit
        }
      },
    content = content
  )
}

/** Selector de fondo animado para Configuración. */
@Composable
fun BackgroundStylePicker() {
  val ctx = LocalContext.current
  remember { BackgroundPrefs.ensureLoaded(ctx) }
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
    Text("🎞️ Fondo animado", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    Text("Un movimiento lento y elegante detrás de todos los menús y juegos.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      BgStyle.values().forEach { b ->
        val label = "${b.emoji} ${b.label}"
        if (BackgroundPrefs.style == b) GradientButton(label, onClick = { BackgroundPrefs.set(ctx, b) }, height = 42.dp)
        else SoftButton(label, onClick = { BackgroundPrefs.set(ctx, b) })
      }
    }
  }
}

/** Efecto de rebote al presionar: la vista se hunde un poco y vuelve con resorte. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, amount: Float = 0.95f): Modifier {
  val pressed by source.collectIsPressedAsState()
  val s by animateFloatAsState(if (pressed) amount else 1f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "pressScale")
  return this.graphicsLayer { scaleX = s; scaleY = s }
}

/** Tarjeta de cristal: capas translúcidas, borde de luz y resplandor suave. */
@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  corner: Dp = 24.dp,
  highlight: Boolean = false,
  onClick: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(16.dp),
  content: @Composable ColumnScope.() -> Unit
) {
  val p = LocalDuoPalette.current
  val shape = RoundedCornerShape(corner)
  val source = remember { MutableInteractionSource() }
  val fill = if (p.isLight) {
    Brush.linearGradient(listOf(Color(0xF21A1030), if (highlight) p.accentA.copy(alpha = 0.92f) else Color(0xF22A1646)))
  } else if (highlight) {
    Brush.linearGradient(listOf(p.accentA.copy(alpha = 0.34f), p.accentB.copy(alpha = 0.16f), Color(0x14000000)))
  } else {
    Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF)))
  }
  val rim = Brush.linearGradient(
    if (highlight) listOf(p.accentA.copy(alpha = 0.85f), p.accentB.copy(alpha = 0.25f), p.accentA.copy(alpha = 0.55f))
    else listOf(Color(0x55FFFFFF), Color(0x14FFFFFF), Color(0x33FFFFFF))
  )
  val base = modifier
    .then(if (onClick != null) Modifier.pressScale(source, 0.97f) else Modifier)
    .clip(shape)
    .background(fill)
    .border(BorderStroke(1.dp, rim), shape)
  val clickable = if (onClick != null) base.clickable(interactionSource = source, indication = null) { onClick() } else base
  Column(modifier = clickable.padding(contentPadding), content = content)
}

/** Botón principal: degradado con brillo diagonal que recorre el botón de vez en cuando. */
@Composable
fun GradientButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  height: Dp = 54.dp
) {
  val p = LocalDuoPalette.current
  val shape = RoundedCornerShape(18.dp)
  val source = remember { MutableInteractionSource() }
  val t = rememberInfiniteTransition(label = "btnShine")
  val shine by t.animateFloat(
    initialValue = -0.4f, targetValue = 1.6f,
    animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
    label = "shine"
  )
  Box(
    modifier = modifier
      .height(height)
      .pressScale(source, 0.95f)
      .clip(shape)
      .background(if (enabled) p.accentBrush else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))))
      .then(
        if (enabled) Modifier.drawBehind {
          val cx = size.width * shine
          drawRect(
            Brush.linearGradient(
              listOf(Color.Transparent, Color(0x40FFFFFF), Color.Transparent),
              start = Offset(cx - size.height, 0f), end = Offset(cx + size.height, size.height)
            )
          )
          drawRect(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color.Transparent), endY = size.height * 0.5f))
        } else Modifier
      )
      .border(BorderStroke(1.dp, if (enabled) Color(0x40FFFFFF) else Color(0x18FFFFFF)), shape)
      .clickable(interactionSource = source, indication = null, enabled = enabled) { onClick() }
      .padding(horizontal = 22.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = if (enabled) Color.White else Color(0x88FFFFFF),
      fontWeight = FontWeight.ExtraBold,
      fontSize = 16.sp,
      textAlign = TextAlign.Center
    )
  }
}

/** Botón secundario translúcido con rebote al presionar. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  val shape = RoundedCornerShape(16.dp)
  val lightMode = LocalDuoPalette.current.isLight
  val source = remember { MutableInteractionSource() }
  Box(
    modifier = modifier
      .height(46.dp)
      .pressScale(source, 0.95f)
      .clip(shape)
      .background(if (lightMode) Color(0xF22A1646) else Color(0x1FFFFFFF))
      .border(BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0x55FFFFFF), Color(0x14FFFFFF)))), shape)
      .clickable(interactionSource = source, indication = null, enabled = enabled) { onClick() }
      .padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(text, color = if (enabled) Color.White else Color(0x77FFFFFF), fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
  }
}

/** Texto con título de sección grande. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
  Column(modifier = modifier.padding(horizontal = 4.dp)) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
    if (subtitle != null) {
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** Pastilla pequeña de estado. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(50))
      .background(color.copy(alpha = 0.18f))
      .border(BorderStroke(1.dp, color.copy(alpha = 0.5f)), RoundedCornerShape(50))
      .padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
  }
}


/** Foto de perfil circular (base64) o emoji si todavía no hay foto. */
@Composable
fun AvatarView(photoB64: String?, emoji: String, size: Dp, modifier: Modifier = Modifier) {
  val bmp = remember(photoB64) {
    photoB64?.let {
      runCatching {
        val bytes = android.util.Base64.decode(it, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
      }.getOrNull()
    }
  }
  Box(
    modifier = modifier.size(size).clip(CircleShape).background(LocalDuoPalette.current.accentDiagonal),
    contentAlignment = Alignment.Center
  ) {
    if (bmp != null) {
      Image(bitmap = bmp, contentDescription = "Foto de perfil", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
      Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
  }
}


/** Título de marca Sini⚡ Joss con degradado y brillo. */
@Composable
fun SiniJossTitle(size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
  val p = LocalDuoPalette.current
  Text(
    text = "Sini⚡ Joss",
    modifier = modifier,
    style = androidx.compose.ui.text.TextStyle(
      brush = Brush.horizontalGradient(if (p.isLight) listOf(p.accentA, p.accentB) else listOf(Color.White, p.accentA, p.accentB)),
      fontSize = size,
      fontWeight = FontWeight.Black,
      letterSpacing = (size.value * 0.04f).sp,
      shadow = androidx.compose.ui.graphics.Shadow(color = p.accentA.copy(alpha = 0.85f), offset = Offset(0f, 4f), blurRadius = size.value * 0.6f)
    )
  )
}
