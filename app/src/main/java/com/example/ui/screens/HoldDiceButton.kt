package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Botón de dado "mantené para girar, soltá para tirar".
 * Mientras el dedo está apretado avisa con [onHoldChange] (para que el dado o el vaso sigan girando y sacudiéndose)
 * y suena un tictac; al soltar se llama a [onRelease] una sola vez. Un toque corto también tira.
 */
@Composable
internal fun HoldDiceButton(
  label: String,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  holdLabel: String = "Girando… soltá para tirar",
  height: Dp = 54.dp,
  onHoldChange: (Boolean) -> Unit = {},
  onRelease: () -> Unit
) {
  val ctx = LocalContext.current
  var pressed by remember { mutableStateOf(false) }
  val currentRelease by rememberUpdatedState(onRelease)
  val currentHold by rememberUpdatedState(onHoldChange)
  val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "holdScale")
  val glow by rememberInfiniteTransition(label = "holdGlow").animateFloat(
    initialValue = 0.35f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "holdGlowV"
  )
  LaunchedEffect(enabled) {
    if (!enabled && pressed) { pressed = false; currentHold(false) }
  }
  // Mientras se mantiene apretado suena un tictac constante.
  LaunchedEffect(pressed) {
    while (pressed) {
      com.example.audio.GameSfx.play(ctx, "sfx_countdown_tick", 0.22f)
      delay(130)
    }
  }
  val shape = RoundedCornerShape(height / 2)
  val top = if (pressed) Color(0xFF7DD3FC) else Color(0xFF38BDF8)
  Box(
    modifier = modifier.fillMaxWidth().height(height)
      .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
      .shadow(if (pressed) 14.dp else 6.dp, shape, ambientColor = Color(0xFF38BDF8), spotColor = Color(0xFF38BDF8))
      .clip(shape)
      .background(Brush.linearGradient(listOf(top, Color(0xFF3B82F6))))
      .border(2.dp, Color.White.copy(alpha = if (pressed) 0.35f + 0.5f * glow else 0.35f), shape)
      .pointerInput(enabled) {
        if (enabled) {
          detectTapGestures(onPress = {
            pressed = true
            currentHold(true)
            tryAwaitRelease()
            pressed = false
            currentHold(false)
            currentRelease()
          })
        }
      },
    contentAlignment = Alignment.Center
  ) {
    Text(if (pressed) holdLabel else label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
  }
}
