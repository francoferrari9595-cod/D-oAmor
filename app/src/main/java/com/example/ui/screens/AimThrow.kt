package com.example.ui.screens

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════
// Piezas comunes de los juegos de puntería (Dardos y Tejo):
//  · mira que tiembla y se aquieta mientras mantenés apretado (y se cansa si te pasás),
//  · reloj de "dedo apretado" que se actualiza cada cuadro sin recomponer,
//  · confeti y sonidos sintetizados (impacto, campana metálica, explosión).
// ═══════════════════════════════════════════════════════════════════════════

/** Amplitud del temblor de la mira: grande al tocar, se aquieta en ~1,5 s y se cansa pasados 2,4 s. */
internal fun aimWobbleAmp(holdSec: Float, maxAmp: Float, minAmp: Float): Float {
  val settle = maxAmp * exp(-holdSec / 0.9f)
  val tire = if (holdSec > 2.4f) (holdSec - 2.4f) * maxAmp * 0.6f else 0f
  return minAmp + settle + tire
}

/** Desplazamiento de la mira en el instante `holdSec`; `phase` distingue un lanzamiento de otro. */
internal fun aimWobbleOffset(holdSec: Float, amp: Float, phase: Float): Offset =
  Offset(sin(holdSec * 2.7f + phase) * amp, cos(holdSec * 2.1f + phase * 1.7f) * amp)

/** Segundos que lleva apretado el dedo (0 si no hay dedo). Se lee dentro de dibujo o gestos, no al componer. */
@Composable
internal fun rememberHoldClock(active: Boolean): State<Float> {
  val t = remember { mutableStateOf(0f) }
  LaunchedEffect(active) {
    if (active) {
      t.value = 0f
      var last = withFrameNanos { it }
      while (true) {
        val now = withFrameNanos { it }
        t.value += (now - last) / 1_000_000_000f
        last = now
      }
    } else t.value = 0f
  }
  return t
}

@Composable
internal fun GameConfetti(modifier: Modifier = Modifier) {
  val t by rememberInfiniteTransition(label = "gameConfetti").animateFloat(
    initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "gameConfettiT"
  )
  val cols = listOf(Color(0xFFFF5C93), Color(0xFFFFC857), Color(0xFF38BDF8), Color(0xFF7DE3A6), Color.White)
  Canvas(modifier) {
    for (i in 0 until 46) {
      val seed = ((i * 7919) % 1000) / 1000f
      val seed2 = ((i * 104729) % 997) / 997f
      val x = size.width * seed
      val y = ((t * (0.6f + seed2) + seed2) % 1f) * (size.height + 40f) - 20f
      drawRect(cols[i % cols.size], Offset(x, y), Size(8.dp.toPx(), 12.dp.toPx()))
    }
  }
}

/** Sonidos generados por código (sin archivos), seguros de lanzar desde varias corrutinas a la vez. */
internal object SynthSfx {
  private const val SR = 22050

  private fun synth(seconds: Float, sample: (t: Float, rnd: java.util.Random, state: FloatArray) -> Float): ShortArray {
    val n = (SR * seconds).toInt()
    val out = ShortArray(n)
    val rnd = java.util.Random(5)
    val state = FloatArray(2)
    for (i in 0 until n) {
      val s = sample(i.toFloat() / SR, rnd, state)
      out[i] = (s.coerceIn(-1f, 1f) * 30000f).toInt().toShort()
    }
    return out
  }

  /** Golpe seco de un objeto que se clava o cae. */
  val thud: ShortArray by lazy {
    synth(0.28f) { t, rnd, st ->
      st[0] += (rnd.nextFloat() * 2f - 1f - st[0]) * 0.25f
      (st[0] * 0.8f + sin(2.0 * PI * 130.0 * t).toFloat() * 0.6f) * exp(-t * 22f)
    }
  }

  /** Metal contra metal: tres parciales que se apagan despacio. */
  val clang: ShortArray by lazy {
    synth(1.0f) { t, _, _ ->
      val tone = sin(2.0 * PI * 1180.0 * t).toFloat() + 0.7f * sin(2.0 * PI * 1870.0 * t).toFloat() + 0.5f * sin(2.0 * PI * 2930.0 * t).toFloat()
      tone * 0.33f * exp(-t * 5f)
    }
  }

  /** Explosión: chasquido inicial, retumbo grave y cola de humo. */
  val boom: ShortArray by lazy {
    synth(1.5f) { t, rnd, st ->
      st[0] += (rnd.nextFloat() * 2f - 1f - st[0]) * 0.045f
      val crack = (rnd.nextFloat() * 2f - 1f) * exp(-t * 40f)
      val rumble = st[0] * 2.2f * exp(-t * 2.6f)
      val sub = sin(2.0 * PI * 55.0 * t).toFloat() * exp(-t * 3.2f)
      crack * 0.9f + rumble * 0.7f + sub * 0.7f
    }
  }

  /** Choque seco entre dos bolas de billar. */
  val click: ShortArray by lazy {
    synth(0.11f) { t, rnd, _ ->
      (sin(2.0 * PI * 2100.0 * t).toFloat() * 0.7f + sin(2.0 * PI * 3300.0 * t).toFloat() * 0.3f + (rnd.nextFloat() * 2f - 1f) * 0.25f) * exp(-t * 55f)
    }
  }

  /** Bola que cae en la tronera: golpe grave y rodado corto. */
  val pocketDrop: ShortArray by lazy {
    synth(0.6f) { t, rnd, st ->
      st[0] += (rnd.nextFloat() * 2f - 1f - st[0]) * 0.12f
      (sin(2.0 * PI * 140.0 * t).toFloat() * 0.7f * exp(-t * 14f)) + st[0] * 0.5f * exp(-t * 6f) * (if (t > 0.08f) 1f else 0.3f)
    }
  }

  /** Aire cortado por algo que vuela. */
  val whoosh: ShortArray by lazy {
    synth(0.4f) { t, rnd, st ->
      val k = 0.05f + 0.5f * (t / 0.4f)
      st[0] += (rnd.nextFloat() * 2f - 1f - st[0]) * k
      st[0] * 1.6f * sin((t / 0.4f) * PI.toFloat()) * 0.7f
    }
  }

  suspend fun play(data: ShortArray, volume: Float) {
    var track: AudioTrack? = null
    try {
      track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(data.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
      track.write(data, 0, data.size)
      track.setVolume(volume.coerceIn(0f, 1f))
      track.play()
      delay(data.size * 1000L / SR + 120L)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // sin audio disponible: el juego sigue en silencio
    } finally {
      runCatching { track?.stop() }
      runCatching { track?.release() }
    }
  }
}
