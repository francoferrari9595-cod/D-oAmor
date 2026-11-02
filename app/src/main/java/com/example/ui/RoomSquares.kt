package com.example.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LobbyInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Salas en cuadrados: 2 por fila. Cada sala es solo texto («Sala 1», «Sala 2»… o el nombre que le pusiste)
 * con efecto de letrero de neón que titila. Sin ilustraciones de juegos.
 */
@Composable
fun RoomSquaresGrid(lobbies: List<LobbyInfo>, onLongPress: (LobbyInfo) -> Unit = {}, onPick: (LobbyInfo) -> Unit) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    lobbies.withIndex().toList().chunked(2).forEach { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { (i, l) -> RoomSquare(l, i, Modifier.weight(1f), { onLongPress(l) }) { onPick(l) } }
        if (row.size == 1) Spacer(Modifier.weight(1f))
      }
    }
  }
}

/** Colores de neón: cada sala toma uno según su posición (la lista se repite si hay más salas). */
private val NEON_COLORS = listOf(
  Color(0xFF39FF14), // verde
  Color(0xFF00E5FF), // cian
  Color(0xFFFF2BD6), // magenta
  Color(0xFFFFB300), // ámbar
  Color(0xFFB388FF), // violeta
  Color(0xFFFF5252), // rojo
  Color(0xFF00FFB3), // menta
  Color(0xFFFFEB3B)  // amarillo
)

@Composable
private fun RoomSquare(l: LobbyInfo, index: Int, modifier: Modifier, onLongPress: () -> Unit, onClick: () -> Unit) {
  val shape = RoundedCornerShape(22.dp)
  val neon = NEON_COLORS[index % NEON_COLORS.size]
  var hold by remember { mutableStateOf(0f) }
  var fired by remember { mutableStateOf(false) }

  // Titileo de tubo de neón: casi siempre encendido, con parpadeos bruscos. Cada sala tiene su propio ritmo.
  val tr = rememberInfiniteTransition(label = "neon$index")
  val dur = 3600 + (index % 5) * 430
  val flick by tr.animateFloat(
    initialValue = 1f, targetValue = 1f,
    animationSpec = infiniteRepeatable(
      keyframes {
        durationMillis = dur
        1f at 0
        1f at (dur * 0.40f).toInt()
        0.20f at (dur * 0.42f).toInt()
        1f at (dur * 0.45f).toInt()
        0.45f at (dur * 0.47f).toInt()
        1f at (dur * 0.50f).toInt()
        1f at (dur * 0.80f).toInt()
        0.30f at (dur * 0.82f).toInt()
        1f at (dur * 0.86f).toInt()
      },
      RepeatMode.Restart
    ),
    label = "flick$index"
  )
  // Brillo que respira suavemente.
  val breathe by tr.animateFloat(0.72f, 1f, infiniteRepeatable(tween(1700 + (index % 4) * 250), RepeatMode.Reverse), label = "breathe$index")
  val glow = flick * breathe

  Box(
    modifier.aspectRatio(1f).clip(shape)
      .background(Color(0xFF05060B))
      .background(Brush.radialGradient(listOf(neon.copy(alpha = 0.20f * glow), Color.Transparent)))
      .border(2.dp, neon.copy(alpha = 0.45f + 0.55f * flick), shape)
      .pointerInput(l.id) {
        detectTapGestures(
          onPress = {
            fired = false
            kotlinx.coroutines.coroutineScope {
              val job = launch {
                val t0 = System.currentTimeMillis()
                while (true) {
                  val e = (System.currentTimeMillis() - t0) / 5000f
                  hold = e.coerceAtMost(1f)
                  if (e >= 1f) { fired = true; hold = 0f; onLongPress(); break }
                  delay(50)
                }
              }
              tryAwaitRelease()
              job.cancel(); hold = 0f
            }
          },
          onTap = { if (!fired) onClick() }
        )
      }
  ) {
    // Resplandor interior del borde (anillos concéntricos que se desvanecen hacia adentro).
    Canvas(Modifier.fillMaxSize()) {
      val r = 22.dp.toPx()
      for (k in 0 until 5) {
        val inset = 1.dp.toPx() + k * 3.dp.toPx()
        drawRoundRect(
          color = neon.copy(alpha = ((0.30f - k * 0.055f) * flick).coerceAtLeast(0f)),
          topLeft = Offset(inset, inset),
          size = Size(size.width - 2 * inset, size.height - 2 * inset),
          cornerRadius = CornerRadius((r - inset * 0.5f).coerceAtLeast(2f)),
          style = Stroke(3.dp.toPx())
        )
      }
    }

    // Texto neón: halo ancho + halo cercano + núcleo casi blanco.
    val label = l.name.trim()
    val fs = when {
      label.length <= 7 -> 32.sp
      label.length <= 10 -> 26.sp
      label.length <= 14 -> 21.sp
      else -> 17.sp
    }
    val core = lerp(neon, Color.White, 0.78f)
    Box(Modifier.align(Alignment.Center).padding(horizontal = 10.dp)) {
      Text(
        label, color = neon.copy(alpha = flick), fontWeight = FontWeight.Black, fontSize = fs, lineHeight = fs * 1.1f,
        textAlign = TextAlign.Center, maxLines = 3, letterSpacing = 1.sp,
        style = TextStyle(shadow = Shadow(neon.copy(alpha = 0.95f * flick), Offset.Zero, 64f))
      )
      Text(
        label, color = core.copy(alpha = flick), fontWeight = FontWeight.Black, fontSize = fs, lineHeight = fs * 1.1f,
        textAlign = TextAlign.Center, maxLines = 3, letterSpacing = 1.sp,
        style = TextStyle(shadow = Shadow(neon.copy(alpha = flick), Offset.Zero, 22f))
      )
    }

    // Conectados
    Row(
      Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(if (l.online > 0) Color(0xFF39FF88) else Color(0xFF9E9E9E)))
      Spacer(Modifier.size(5.dp))
      Text("${l.online}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
    Text(
      if (l.hasPassword) "🔒" else "🚪", fontSize = 18.sp,
      modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(Color(0x99000000)).padding(6.dp)
    )
    if (hold > 0.12f) {
      androidx.compose.material3.LinearProgressIndicator(progress = { hold }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), color = Color(0xFFFF5252))
    }
    Text(
      if (l.hasPassword) "Con contraseña" else "Libre",
      color = neon.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
    )
  }
}
