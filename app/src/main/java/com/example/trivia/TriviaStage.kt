package com.example.trivia

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AmbientSparkles
import com.example.ui.screens.ConfettiBurst
import com.example.ui.screens.ImpactBurst
import com.example.ui.screens.rememberNeonBrush
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val T_NEON_PINK = Color(0xFFFF2BD6)
internal val T_NEON_CYAN = Color(0xFF00E5FF)
internal val T_GOLD = Color(0xFFFFD54F)
internal val T_OK = Color(0xFF39FF88)
internal val T_BAD = Color(0xFFFF3D5A)
internal val T_AVATARS = listOf("🦊", "🐼", "🦁", "🐯", "🐸", "🦄", "🐙", "🦉")

/** Sonidos del concurso con ToneGenerator (sin archivos): no interfieren con la música ni con otros módulos. */
internal object TriviaSfx {
  private var tg: ToneGenerator? = null
  private fun gen(): ToneGenerator? {
    if (tg == null) tg = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }.getOrNull()
    return tg
  }
  fun buzz() { gen()?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 260) }
  fun ok() { gen()?.startTone(ToneGenerator.TONE_PROP_ACK, 350) }
  fun bad() { gen()?.startTone(ToneGenerator.TONE_SUP_ERROR, 450) }
  fun tick() { gen()?.startTone(ToneGenerator.TONE_PROP_BEEP, 60) }
  fun go() { gen()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300) }
  fun release() { runCatching { tg?.release() }; tg = null }
}

/** Escenario futurista: haces de luz que barren, suelo de rejilla LED en perspectiva y destellos. */
@Composable
internal fun TriviaBackdrop(modifier: Modifier = Modifier) {
  val t = rememberInfiniteTransition(label = "tbg")
  val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "tbgPh")
  Box(modifier) {
    Canvas(Modifier.fillMaxSize()) {
      val w = size.width; val h = size.height
      drawRect(Brush.verticalGradient(listOf(Color(0xFF04000F), Color(0xFF12073A), Color(0xFF2A0F5C))))
      val beams = listOf(Triple(0.16f, T_NEON_PINK, 0f), Triple(0.5f, T_NEON_CYAN, 0.33f), Triple(0.84f, T_GOLD, 0.66f))
      for ((bx, col, off) in beams) {
        val sway = sin((ph + off) * 2f * PI.toFloat()) * 0.22f
        val endX = w * (bx + sway)
        val spread = w * 0.14f
        val path = Path().apply {
          moveTo(w * bx - 6.dp.toPx(), -4.dp.toPx()); lineTo(w * bx + 6.dp.toPx(), -4.dp.toPx())
          lineTo(endX + spread, h * 0.74f); lineTo(endX - spread, h * 0.74f); close()
        }
        drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.34f), Color.Transparent), 0f, h * 0.74f))
        drawCircle(col.copy(alpha = 0.9f), 4.dp.toPx(), Offset(w * bx, 2.dp.toPx()))
      }
      val horizon = h * 0.74f
      drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0B45), Color(0xFF060112)), horizon, h), Offset(0f, horizon), Size(w, h - horizon))
      for (i in -9..9) drawLine(T_NEON_CYAN.copy(alpha = 0.22f), Offset(w / 2f + i * w * 0.02f, horizon), Offset(w / 2f + i * w * 0.17f, h), 1.2.dp.toPx())
      for (k in 0 until 8) {
        val f = ((k + ph * 8f) % 8f) / 8f
        val y = horizon + (h - horizon) * f * f
        drawLine(T_NEON_PINK.copy(alpha = 0.10f + 0.32f * f), Offset(0f, y), Offset(w, y), 1.dp.toPx())
      }
      drawLine(Brush.horizontalGradient(listOf(Color.Transparent, T_NEON_CYAN, Color.Transparent)), Offset(0f, horizon), Offset(w, horizon), 2.5.dp.toPx())
    }
    AmbientSparkles(Modifier.fillMaxSize(), listOf(Color.White, T_NEON_CYAN, T_NEON_PINK, T_GOLD), 22)
  }
}

/** Cronómetro circular con marcas LED; cambia de verde a rojo y late en los últimos 5 segundos. */
@Composable
internal fun TriviaTimerRing(fraction: Float, seconds: Int, modifier: Modifier = Modifier, caption: String = "", warnColor: Boolean = true, numSize: androidx.compose.ui.unit.TextUnit = 30.sp) {
  val f = fraction.coerceIn(0f, 1f)
  val urgent = seconds <= 5 && f > 0f
  val t = rememberInfiniteTransition(label = "tring")
  val beat by t.animateFloat(1f, if (urgent) 1.09f else 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "tbeat")
  val col = if (warnColor) (if (f > 0.5f) lerp(T_GOLD, T_OK, (f - 0.5f) * 2f) else lerp(T_BAD, T_GOLD, f * 2f)) else T_NEON_CYAN
  Box(modifier.graphicsLayer { scaleX = beat; scaleY = beat }, contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val stroke = size.minDimension * 0.09f
      val r = size.minDimension / 2f - stroke
      val c = Offset(size.width / 2f, size.height / 2f)
      drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
      drawCircle(Color(0xFF0B0620), r + stroke / 2f, c)
      drawCircle(Color(0x33FFFFFF), r, c, style = Stroke(stroke))
      drawArc(col, -90f, 360f * f, false, Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f), style = Stroke(stroke, cap = StrokeCap.Round))
      for (i in 0 until 60) {
        val a = (i / 60f) * 2f * PI.toFloat() - PI.toFloat() / 2f
        val lit = i / 60f <= f
        val r1 = r - stroke * 0.9f; val r2 = r - stroke * (if (i % 5 == 0) 1.7f else 1.35f)
        drawLine(if (lit) col.copy(alpha = 0.9f) else Color(0x22FFFFFF), Offset(c.x + cos(a) * r1, c.y + sin(a) * r1), Offset(c.x + cos(a) * r2, c.y + sin(a) * r2), 1.4.dp.toPx())
      }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text("$seconds", color = Color.White, fontSize = numSize, fontWeight = FontWeight.Black)
      if (caption.isNotEmpty()) Text(caption, color = col, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
  }
}

/** Pantalla central: marco de neón, líneas de barrido y contenido del concurso. */
@Composable
internal fun TriviaScreenPanel(modifier: Modifier = Modifier, pad: androidx.compose.ui.unit.Dp = 14.dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  val shape = RoundedCornerShape(22.dp)
  val t = rememberInfiniteTransition(label = "tscan")
  val scan by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "tscanY")
  Box(modifier.shadow(18.dp, shape, ambientColor = T_NEON_CYAN, spotColor = T_NEON_PINK).clip(shape)
    .background(Brush.verticalGradient(listOf(Color(0xE6120A33), Color(0xF2060213)))).border(3.dp, rememberNeonBrush(5000, 0.85f), shape)) {
    Canvas(Modifier.fillMaxSize()) {
      var y = 0f
      while (y < size.height) { drawLine(Color(0x0DFFFFFF), Offset(0f, y), Offset(size.width, y), 1f); y += 4.dp.toPx() }
      val sy = size.height * scan
      drawRect(Brush.verticalGradient(listOf(Color.Transparent, T_NEON_CYAN.copy(alpha = 0.14f), Color.Transparent), sy - 40.dp.toPx(), sy + 40.dp.toPx()), Offset(0f, sy - 40.dp.toPx()), Size(size.width, 80.dp.toPx()))
    }
    Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
  }
}

internal enum class PodState { IDLE, BUZZED, LOCKED }

/** Marcador de un jugador: avatar, nombre, puntos animados y luz LED según su pulsador. */
@Composable
internal fun TriviaPod(seat: Int, name: String, score: Int, state: PodState, isMe: Boolean, leader: Boolean, modifier: Modifier = Modifier) {
  val shown by animateIntAsState(score, tween(700, easing = FastOutSlowInEasing), label = "podScore")
  val t = rememberInfiniteTransition(label = "pod")
  val glow by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "podGlow")
  val led = when (state) { PodState.BUZZED -> T_GOLD; PodState.LOCKED -> T_BAD; else -> T_NEON_CYAN }
  val a = if (state == PodState.BUZZED) glow else if (state == PodState.LOCKED) 0.5f else 0.35f
  val shape = RoundedCornerShape(14.dp)
  Column(
    modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xCC1A0F44), Color(0xCC0A0522))))
      .border(if (state == PodState.BUZZED) 2.5.dp else 1.5.dp, led.copy(alpha = a), shape).padding(horizontal = 4.dp, vertical = 5.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(led.copy(alpha = a)))
    Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
      Text(if (leader && score > 0) "👑" else T_AVATARS[seat % T_AVATARS.size], fontSize = 20.sp)
      Spacer(Modifier.width(6.dp))
      Column {
        Text(if (isMe) "Tú" else name, color = if (isMe) T_GOLD else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("$shown", color = if (shown < 0) T_BAD else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
          if (state == PodState.LOCKED) Text(" 🔒", fontSize = 10.sp) else if (state == PodState.BUZZED) Text(" 🔔", fontSize = 10.sp)
        }
      }
    }
  }
}

/** Corneta del pulsador: dos notas con armónicos generadas por código (no necesita archivos de audio). */
internal object TriviaHorn {
  private const val RATE = 22050
  private val pcm: ShortArray by lazy {
    val n = (RATE * 0.55f).toInt(); val out = ShortArray(n)
    for (i in 0 until n) {
      val t = i / RATE.toFloat()
      val env = minOf(1f, t / 0.012f) * (if (t > 0.42f) maxOf(0f, 1f - (t - 0.42f) / 0.13f) else 1f)
      var v = 0f
      for (h in 1..6) { val k = 1f / h; v += k * kotlin.math.sin(2.0 * Math.PI * 392.0 * h * t).toFloat(); v += k * 0.8f * kotlin.math.sin(2.0 * Math.PI * 494.0 * h * t).toFloat() }
      v += 0.25f * kotlin.math.sin(2.0 * Math.PI * (392.0 + 6.0 * kotlin.math.sin(2.0 * Math.PI * 7.0 * t)) * t).toFloat()   // leve vibrato de bocina
      out[i] = (v * env * 5200f).toInt().coerceIn(-30000, 30000).toShort()
    }
    out
  }
  fun honk() {
    try {
      val t = android.media.AudioTrack.Builder()
        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_GAME).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(pcm.size * 2).setTransferMode(android.media.AudioTrack.MODE_STATIC).build()
      t.write(pcm, 0, pcm.size); t.play()
      android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ try { t.release() } catch (e: Throwable) { } }, 900)
    } catch (e: Throwable) { }
  }
}

/** Pulsador grande con brillo 3D, borde de neón que gira y pulsa, explosión de luz al apretar y sonido de corneta. */
@Composable
internal fun TriviaBuzzer(enabled: Boolean, label: String, modifier: Modifier = Modifier, onBuzz: () -> Unit) {
  var pressed by remember { mutableStateOf(false) }
  val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "buzzSc")
  val t = rememberInfiniteTransition(label = "buzzFx")
  val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "buzzRing")
  val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "buzzSpin")
  val beat by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "buzzBeat")
  val burst = remember { Animatable(1f) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val base = if (enabled) listOf(Color(0xFFFF8A80), Color(0xFFFF1744), Color(0xFF7F0000)) else listOf(Color(0xFF9E9E9E), Color(0xFF424242), Color(0xFF1B1B1B))
  val neon = listOf(Color(0xFFFF2BD6), Color(0xFF00E5FF), Color(0xFFFFEA00), Color(0xFFFF2BD6))
  Box(modifier.graphicsLayer { scaleX = sc; scaleY = sc }.pointerInput(enabled) {
    detectTapGestures(onPress = {
      if (enabled) {
        pressed = true; TriviaHorn.honk(); onBuzz()
        scope.launch { burst.snapTo(0f); burst.animateTo(1f, tween(650, easing = LinearEasing)) }
      }
      tryAwaitRelease(); pressed = false
    })
  }, contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension / 2f * 0.74f
      if (enabled) {
        // halo de neón que late
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF2BD6).copy(alpha = 0.40f * beat), Color(0xFF00E5FF).copy(alpha = 0.16f * beat), Color.Transparent), c, r * 1.7f), r * 1.7f, c)
        for (k in 0..1) {
          val f = (ring + k * 0.5f) % 1f
          drawCircle(neon[k].copy(alpha = 0.6f * (1f - f)), r * (1.1f + 0.5f * f), c, style = Stroke(3.dp.toPx() * (1f - f) + 1f))
        }
      }
      drawCircle(Color(0xFF200000), r * 1.12f, c)
      drawCircle(Brush.radialGradient(base, Offset(c.x - r * 0.25f, c.y - r * 0.3f), r * 1.2f), r, c)
      drawCircle(Color.White.copy(alpha = if (enabled) 0.35f else 0.12f), r * 0.55f, Offset(c.x - r * 0.18f, c.y - r * 0.42f))
      drawCircle(Color.White.copy(alpha = 0.25f), r, c, style = Stroke(2.dp.toPx()))
      if (enabled) {
        // borde de neón giratorio con doble trazo para el efecto de brillo
        rotate(spin, c) {
          drawCircle(Brush.sweepGradient(neon, c), r * 1.08f, c, style = Stroke(7.dp.toPx() * (0.7f + 0.3f * beat)), alpha = 0.35f)
          drawCircle(Brush.sweepGradient(neon, c), r * 1.08f, c, style = Stroke(2.5.dp.toPx()))
        }
      }
      // explosión de neón al pulsar
      val bf = burst.value
      if (bf < 1f) {
        for (k in 0..2) {
          val f = (bf * (1f + k * 0.25f)).coerceIn(0f, 1f)
          drawCircle(neon[k].copy(alpha = 0.9f * (1f - f)), r * (1f + 1.4f * f), c, style = Stroke(6.dp.toPx() * (1f - f) + 1f))
        }
        for (i in 0 until 14) {
          val a = i * 2f * Math.PI.toFloat() / 14f
          val r1 = r * (1.05f + 0.5f * bf); val r2 = r * (1.2f + 1.1f * bf)
          drawLine(neon[i % 3].copy(alpha = 1f - bf), Offset(c.x + kotlin.math.cos(a) * r1, c.y + kotlin.math.sin(a) * r1), Offset(c.x + kotlin.math.cos(a) * r2, c.y + kotlin.math.sin(a) * r2), 3.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(Color.White.copy(alpha = 0.5f * (1f - bf)), r, c)
      }
    }
    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp))
  }
}

/** Animación de acierto o error a pantalla completa: destello de color, estallido y texto que rebota. */
@Composable
internal fun TriviaVerdictOverlay(ok: Boolean, title: String, subtitle: String, modifier: Modifier = Modifier) {
  val col = if (ok) T_OK else T_BAD
  val wash = remember { Animatable(0.6f) }
  val pop = remember { Animatable(0.3f) }
  val shake = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    wash.animateTo(0.18f, tween(900))
  }
  LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
  LaunchedEffect(Unit) {
    if (!ok) for (i in 0 until 6) { shake.animateTo(if (i % 2 == 0) 14f else -14f, tween(55)) }
    shake.animateTo(0f, tween(60))
  }
  Box(modifier.fillMaxSize().background(col.copy(alpha = wash.value)), contentAlignment = Alignment.Center) {
    if (ok) ConfettiBurst(Modifier.fillMaxSize())
    ImpactBurst(Modifier.size(280.dp), if (ok) listOf(T_OK, T_GOLD, Color.White) else listOf(T_BAD, Color(0xFFFF8A3D), Color.White), 18)
    Column(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; translationX = shake.value }, horizontalAlignment = Alignment.CenterHorizontally) {
      Text(if (ok) "✅" else "❌", fontSize = 54.sp)
      Text(title, color = col, fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
      if (subtitle.isNotEmpty()) Text(subtitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
    }
  }
}

/** Cuenta atrás cinematográfica 3-2-1 antes de cada pregunta. */
@Composable
internal fun TriviaCountdown(number: Int, title: String, subtitle: String, modifier: Modifier = Modifier) {
  val pop = remember(number) { Animatable(2.2f) }
  LaunchedEffect(number) { pop.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text(title, color = T_GOLD, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
    Text(subtitle, color = Color.White, fontSize = 13.sp)
    Text(if (number > 0) "$number" else "¡YA!", color = T_NEON_CYAN, fontSize = 84.sp, fontWeight = FontWeight.Black, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = (2.2f - pop.value).coerceIn(0f, 1f) })
  }
}

/** Podio final: tres columnas LED con trofeo, corona, confeti y el resto de la clasificación. */
@Composable
internal fun TriviaWinnerBoard(ranking: List<Triple<Int, String, Int>>, meSeat: Int, modifier: Modifier = Modifier) {
  val bounce = rememberInfiniteTransition(label = "trophy")
  val dy by bounce.animateFloat(-6f, 6f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "trophyDy")
  Box(modifier) {
    ConfettiBurst(Modifier.fillMaxSize())
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
      Text("🏆", fontSize = 64.sp, modifier = Modifier.graphicsLayer { translationY = dy })
      val winner = ranking.firstOrNull()
      Text(if (winner != null && winner.third > 0) "¡GANADOR!" else "¡FIN DEL JUEGO!", color = T_GOLD, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
      if (winner != null) Text("${if (winner.first == meSeat) "¡Tú!" else winner.second} · ${winner.third} pts", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(14.dp))
      val order = listOf(1, 0, 2).filter { it < ranking.size }
      Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        for (pos in order) {
          val (seat, name, pts) = ranking[pos]
          val hgt = when (pos) { 0 -> 120.dp; 1 -> 90.dp; else -> 66.dp }
          val medal = when (pos) { 0 -> "🥇"; 1 -> "🥈"; else -> "🥉" }
          val colr = when (pos) { 0 -> T_GOLD; 1 -> Color(0xFFCFD8DC); else -> Color(0xFFFFAB76) }
          Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(T_AVATARS[seat % T_AVATARS.size], fontSize = 22.sp)
            Text(if (seat == meSeat) "Tú" else name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Box(Modifier.fillMaxWidth().height(hgt).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
              .background(Brush.verticalGradient(listOf(colr.copy(alpha = 0.9f), colr.copy(alpha = 0.25f)))).border(1.5.dp, colr, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)), contentAlignment = Alignment.TopCenter) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(medal, fontSize = 20.sp); Text("$pts", color = Color(0xFF1A1A1A), fontSize = 16.sp, fontWeight = FontWeight.Black) }
            }
          }
        }
      }
      ranking.drop(3).forEachIndexed { i, (seat, name, pts) ->
        Text("${i + 4}. ${if (seat == meSeat) "Tú" else name} — $pts pts", color = Color(0xFFC9BEDD), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
      }
    }
  }
}
