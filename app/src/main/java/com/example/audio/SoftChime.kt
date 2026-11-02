package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sonidito muy suave que avisa «te llegó un regalo»: dos notas de campanilla con ataque lento y volumen bajo.
 * Se genera por código (no necesita archivos de audio) y se reproduce sin interrumpir lo que esté sonando.
 */
object SoftChime {
  private const val SR = 22050
  private var data: ShortArray? = null
  private var last: AudioTrack? = null

  private fun build(): ShortArray {
    val total = (SR * 1.25f).toInt()
    val buf = FloatArray(total)
    // (inicio en segundos, frecuencia, volumen)
    val notes = listOf(Triple(0.00f, 1318.5f, 1.0f), Triple(0.16f, 1760.0f, 0.85f), Triple(0.34f, 2093.0f, 0.6f))
    for ((start, f, vol) in notes) {
      val s0 = (start * SR).toInt()
      var i = 0
      while (s0 + i < total) {
        val t = i / SR.toFloat()
        val attack = 1f - exp(-t * 220f)
        val decay = exp(-t * 4.6f)
        val tone = sin(2.0 * PI * f * t).toFloat() + 0.30f * sin(2.0 * PI * f * 2.0 * t).toFloat() * exp(-t * 8f) + 0.10f * sin(2.0 * PI * f * 3.0 * t).toFloat() * exp(-t * 12f)
        buf[s0 + i] += tone * attack * decay * vol
        i++
      }
    }
    val out = ShortArray(total)
    for (k in 0 until total) out[k] = (buf[k] * 0.16f * 32767f).coerceIn(-32767f, 32767f).toInt().toShort()
    return out
  }

  fun play(@Suppress("UNUSED_PARAMETER") ctx: Context) {
    runCatching {
      val pcm = data ?: build().also { data = it }
      last?.let { runCatching { it.stop() }; runCatching { it.release() } }
      val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(pcm.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
      track.write(pcm, 0, pcm.size)
      track.play()
      last = track
    }
  }
}
