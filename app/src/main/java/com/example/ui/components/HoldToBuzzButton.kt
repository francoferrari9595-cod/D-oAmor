package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BuzzAmber
import com.example.ui.theme.HeartRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Botón personalizado de alta fidelidad para Jetpack Compose que gestiona eventos de
 * pulsación sostenida (Hold-to-Buzz / Long Press continuo).
 *
 * Características:
 * - Detecta de forma instantánea el contacto inicial (Pointer Down) y la liberación del dedo (Pointer Up/Cancel).
 * - Notifica a Firestore el inicio (`isBuzzing = true`) y el fin (`isBuzzing = false`) con la duración exacta en ms.
 * - Renderiza ondas concéntricas de pulso radial mediante Canvas animado.
 * - Incluye compresión elástica física al presionar (Spring animation) y escala de pulso rítmico.
 * - Contador de tiempo en vivo durante la pulsación activa.
 */
@Composable
fun HoldToBuzzButton(
  isBuzzing: Boolean,
  partnerName: String,
  onBuzzStart: () -> Unit,
  onBuzzRelease: (durationMs: Long) -> Unit,
  modifier: Modifier = Modifier,
  buttonSize: Dp = 190.dp,
  outerContainerSize: Dp = 290.dp
) {
  val scope = rememberCoroutineScope()
  var localActiveDurationMs by remember { mutableLongStateOf(0L) }
  var isPointerDown by remember { mutableStateOf(false) }

  // Animación física de compresión al presionar el botón (efecto botón elástico)
  val buttonScale by animateFloatAsState(
    targetValue = if (isPointerDown) 0.92f else 1.0f,
    animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
    label = "button_press_scale"
  )

  // Ondas de expansión radial continua
  val infiniteTransition = rememberInfiniteTransition(label = "pulse_waves")
  val waveProgress1 by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (isPointerDown) 800 else 1800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "wave_progress_1"
  )

  val waveProgress2 by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (isPointerDown) 800 else 1800, delayMillis = if (isPointerDown) 400 else 900, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "wave_progress_2"
  )

  // Cronómetro local en tiempo real durante la pulsación
  LaunchedEffect(isPointerDown) {
    if (isPointerDown) {
      val startTime = System.currentTimeMillis()
      while (isActive && isPointerDown) {
        localActiveDurationMs = System.currentTimeMillis() - startTime
        delay(35)
      }
    } else {
      localActiveDurationMs = 0L
    }
  }

  Box(
    modifier = modifier
      .size(outerContainerSize)
      .testTag("hold_to_buzz_custom_button")
      .semantics {
        contentDescription = "Botón de zumbido continuo para $partnerName. Mantén presionado para hacer sonar."
      },
    contentAlignment = Alignment.Center
  ) {
    // 1. Capa de Canvas con Ondas Expansivas (Ripples)
    val rippleColor = if (isPointerDown) HeartRed else MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val maxRadius = size.width / 2f
      val baseButtonRadius = buttonSize.toPx() / 2f

      // Onda 1
      val r1 = baseButtonRadius + (maxRadius - baseButtonRadius) * waveProgress1
      val alpha1 = (1f - waveProgress1).coerceIn(0f, 1f) * if (isPointerDown) 0.6f else 0.25f
      drawCircle(
        color = rippleColor.copy(alpha = alpha1),
        radius = r1,
        center = center,
        style = Stroke(width = if (isPointerDown) 6.dp.toPx() else 3.dp.toPx())
      )

      // Onda 2
      val r2 = baseButtonRadius + (maxRadius - baseButtonRadius) * waveProgress2
      val alpha2 = (1f - waveProgress2).coerceIn(0f, 1f) * if (isPointerDown) 0.5f else 0.2f
      drawCircle(
        color = rippleColor.copy(alpha = alpha2),
        radius = r2,
        center = center,
        style = Stroke(width = if (isPointerDown) 4.dp.toPx() else 2.dp.toPx())
      )
    }

    // 2. Halo difuminado de fondo
    Box(
      modifier = Modifier
        .size(buttonSize + 30.dp)
        .clip(CircleShape)
        .background(
          if (isPointerDown) HeartRed.copy(alpha = 0.22f)
          else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
    )

    // 3. Botón Central Interactivo con detección de Pointer Input
    val buttonBrush = if (isPointerDown) {
      Brush.radialGradient(
        colors = listOf(
          Color(0xFFE53935),
          Color(0xFFC62828),
          Color(0xFF8E0000)
        )
      )
    } else {
      Brush.radialGradient(
        colors = listOf(
          MaterialTheme.colorScheme.primary,
          MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
          MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
        )
      )
    }

    Surface(
      shape = CircleShape,
      color = Color.Transparent,
      shadowElevation = if (isPointerDown) 20.dp else 8.dp,
      modifier = Modifier
        .size(buttonSize)
        .scale(buttonScale)
        .clip(CircleShape)
        .border(
          width = if (isPointerDown) 5.dp else 3.5.dp,
          brush = Brush.linearGradient(
            listOf(
              Color.White.copy(alpha = 0.8f),
              Color.White.copy(alpha = 0.25f)
            )
          ),
          shape = CircleShape
        )
        .pointerInput(Unit) {
          awaitEachGesture {
            // Await finger touch down
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            isPointerDown = true
            val startTime = System.currentTimeMillis()

            // Disparar vibración y notificar inicio a Firestore
            onBuzzStart()

            // Wait until finger is lifted or gesture is cancelled
            val upOrCancel = waitForUpOrCancellation()
            isPointerDown = false

            val finalDuration = System.currentTimeMillis() - startTime

            // Detener vibración y notificar fin a Firestore
            onBuzzRelease(finalDuration)
          }
        }
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(buttonBrush),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          // Ícono dinámico
          if (isPointerDown) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(46.dp)
            )
          } else {
            Text(
              text = "⚡",
              fontSize = 42.sp
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Texto de estado
          Text(
            text = if (isPointerDown) "¡HACIENDO SONAR!" else "MANTÉN\nPRESIONADO",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = if (isPointerDown) 13.sp else 14.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp,
            lineHeight = 18.sp
          )

          // Cronómetro en vivo mientras el usuario mantiene el botón presionado
          if (isPointerDown) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
              shape = CircleShape,
              color = Color.Black.copy(alpha = 0.35f)
            ) {
              Text(
                text = String.format("%.1f s", localActiveDurationMs / 1000.0),
                color = Color(0xFFFFD54F),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.semantics {
                  contentDescription = "Duración activa de ${localActiveDurationMs / 1000} segundos"
                }
              )
            }
          }
        }
      }
    }
  }
}
