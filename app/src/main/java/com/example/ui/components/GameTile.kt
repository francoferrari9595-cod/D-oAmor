package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/** Colores de acento que se reparten entre los juegos del menú. */
val GameAccents = listOf(
  Color(0xFFFF4D8D), Color(0xFF37E6FF), Color(0xFFFFC857), Color(0xFF9D6BFF),
  Color(0xFF3DDC84), Color(0xFFFF8A5B), Color(0xFF3D8BFF), Color(0xFFE879F9)
)

/**
 * Tarjeta de juego del menú: orbe luminoso con el emoji flotando muy despacio,
 * entrada escalonada (una sola vez) y rebote al tocar.
 */
@Composable
fun GameTile(
  emoji: String,
  title: String,
  desc: String,
  accent: Color,
  index: Int,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val shape = RoundedCornerShape(24.dp)
  val source = remember { MutableInteractionSource() }
  val enter = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    kotlinx.coroutines.delay((index * 45L).coerceAtMost(500L))
    enter.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 240f))
  }
  val t = rememberInfiniteTransition(label = "tileFloat")
  val bob by t.animateFloat(
    initialValue = -3f, targetValue = 3f,
    animationSpec = infiniteRepeatable(tween(3600 + (index % 5) * 450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "bob"
  )
  BoxWithConstraints(
    // Todas las tarjetas son cuadradas y del mismo tamaño, aunque la descripción sea corta.
    modifier = modifier
      .aspectRatio(1f)
      .pressScale(source, 0.95f)
      .graphicsLayer {
        alpha = enter.value.coerceIn(0f, 1f)
        translationY = (1f - enter.value) * 40f
        scaleX = 0.92f + 0.08f * enter.value
        scaleY = 0.92f + 0.08f * enter.value
      }
      .clip(shape)
      .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.26f), Color(0x14FFFFFF), Color(0x0A000000))))
      .border(BorderStroke(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.75f), Color(0x14FFFFFF), accent.copy(alpha = 0.30f)))), shape)
      .clickable(interactionSource = source, indication = null) { onClick() }
      .drawBehind {
        drawCircle(
          Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent), center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.8f),
          radius = size.width * 0.8f, center = Offset(size.width * 0.85f, 0f)
        )
      }
  ) {
    val k = (maxWidth.value / 160f).coerceIn(0.78f, 1.35f)
    Column(modifier = Modifier.fillMaxSize().padding((12 * k).dp), verticalArrangement = Arrangement.SpaceBetween) {
      Box(
        modifier = Modifier
          .size((48 * k).dp)
          .clip(CircleShape)
          .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.12f))))
          .border(BorderStroke(1.dp, accent.copy(alpha = 0.6f)), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Text(emoji, fontSize = (25 * k).sp, modifier = Modifier.graphicsLayer { translationY = bob })
      }
      Column {
        Text(title, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = (15 * k).sp, maxLines = 2, lineHeight = (17 * k).sp, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(desc, color = Color(0xFFC9BEDD), fontSize = (10.5f * k).sp, lineHeight = (13.5f * k).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}
