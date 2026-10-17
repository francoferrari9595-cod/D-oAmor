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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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

/** Fondo degradado con destellos neón que se mueven despacio. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  val p = LocalDuoPalette.current
  val t = rememberInfiniteTransition(label = "aurora")
  val phase by t.animateFloat(
    initialValue = 0f,
    targetValue = 6.2831855f,
    animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
    label = "phase"
  )
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(p.backgroundBrush)
      .drawBehind {
        val w = size.width
        val h = size.height
        val c1 = Offset(w * (0.2f + 0.12f * cos(phase)), h * (0.15f + 0.06f * sin(phase)))
        val c2 = Offset(w * (0.85f + 0.08f * sin(phase)), h * (0.8f + 0.06f * cos(phase)))
        drawCircle(
          brush = Brush.radialGradient(listOf(p.accentA.copy(alpha = 0.30f), Color.Transparent), center = c1, radius = w * 0.75f),
          radius = w * 0.75f, center = c1
        )
        drawCircle(
          brush = Brush.radialGradient(listOf(p.glow.copy(alpha = 0.26f), Color.Transparent), center = c2, radius = w * 0.8f),
          radius = w * 0.8f, center = c2
        )
      },
    content = content
  )
}

/** Tarjeta de cristal: translúcida, con borde suave. */
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
  val base = modifier
    .clip(shape)
    .background(if (highlight) Brush.linearGradient(listOf(p.accentA.copy(alpha = 0.30f), p.accentB.copy(alpha = 0.18f))) else Brush.linearGradient(listOf(GlassWhite, Color(0x0DFFFFFF))))
    .border(BorderStroke(1.dp, if (highlight) p.accentA.copy(alpha = 0.6f) else GlassBorder), shape)
  val clickable = if (onClick != null) base.clickable { onClick() } else base
  Column(modifier = clickable.padding(contentPadding), content = content)
}

/** Botón principal con degradado. */
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
  Box(
    modifier = modifier
      .height(height)
      .clip(shape)
      .background(if (enabled) p.accentBrush else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))))
      .clickable(enabled = enabled) { onClick() }
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

/** Botón secundario translúcido. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  val shape = RoundedCornerShape(16.dp)
  Box(
    modifier = modifier
      .height(46.dp)
      .clip(shape)
      .background(GlassWhite)
      .border(BorderStroke(1.dp, GlassBorder), shape)
      .clickable(enabled = enabled) { onClick() }
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
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
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


/** Título de marca SINI&JOSS con degradado y brillo. */
@Composable
fun SiniJossTitle(size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
  val p = LocalDuoPalette.current
  Text(
    text = "SINI&JOSS",
    modifier = modifier,
    style = androidx.compose.ui.text.TextStyle(
      brush = Brush.horizontalGradient(listOf(Color.White, p.accentA, p.accentB)),
      fontSize = size,
      fontWeight = FontWeight.Black,
      letterSpacing = (size.value * 0.12f).sp,
      shadow = androidx.compose.ui.graphics.Shadow(color = p.accentA.copy(alpha = 0.85f), offset = Offset(0f, 4f), blurRadius = size.value * 0.6f)
    )
  )
}
