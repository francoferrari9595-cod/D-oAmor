package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.BuzzerSoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class BuzzerSoundEngine(private val context: Context) {

  private var audioTrack: AudioTrack? = null
  private var ringtonePlayer: MediaPlayer? = null
  private var isPlaying = false
  private var playbackJob: Job? = null
  // Cualquier error de audio se captura aquí: antes un fallo al crear el AudioTrack cerraba toda la app.
  private val scope = CoroutineScope(Dispatchers.Default + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> android.util.Log.w("KomboArena", "Error de audio del zumbador", e) })

  private val vibrator: Vibrator? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vibratorManager?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  fun startBuzzer(soundType: BuzzerSoundType = BuzzerSoundType.CLASSIC_BUZZ) {
    if (isPlaying) return
    isPlaying = true

    // Start vibration
    startVibration(soundType)

    // Tono de llamada real del teléfono (en bucle, volumen de alarma).
    if (soundType == BuzzerSoundType.PHONE_RING && startRingtone()) return

    // Start Audio synthesis loop
    playbackJob = scope.launch {
      val sampleRate = 44100
      val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      ).coerceAtLeast(sampleRate / 4)

      val track = AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

      audioTrack = track
      track.play()

      val buffer = ShortArray(bufferSize)
      var sampleIndex = 0L

      try {
        while (isActive && isPlaying) {
          for (i in buffer.indices) {
            val t = sampleIndex.toDouble() / sampleRate
            val sampleValue = when (soundType) {
              BuzzerSoundType.CLASSIC_BUZZ, BuzzerSoundType.PHONE_RING -> {
                // Harsh square buzz modulated with 30Hz flutter
                val baseFreq = 440.0 + 40.0 * sin(2.0 * PI * 30.0 * t)
                val wave = sin(2.0 * PI * baseFreq * t)
                if (wave >= 0) 24000.0 else -24000.0
              }
              BuzzerSoundType.LOVE_SIREN -> {
                // Smooth siren sweep 600Hz <-> 1200Hz
                val freq = 800.0 + 400.0 * sin(2.0 * PI * 2.5 * t)
                26000.0 * sin(2.0 * PI * freq * t)
              }
              BuzzerSoundType.HEARTBEAT -> {
                // Heartbeat pulse: thump-thump
                val cycleTime = t % 0.85
                val amp = if (cycleTime < 0.12) {
                  sin(2.0 * PI * (cycleTime / 0.12) * PI)
                } else if (cycleTime in 0.20..0.32) {
                  0.75 * sin(2.0 * PI * ((cycleTime - 0.20) / 0.12) * PI)
                } else 0.0
                amp * 30000.0 * sin(2.0 * PI * 75.0 * t)
              }
              BuzzerSoundType.CHIMES -> {
                // Dual chime tone
                val tone1 = sin(2.0 * PI * 659.25 * t) // E5
                val tone2 = sin(2.0 * PI * 880.00 * t) // A5
                14000.0 * (tone1 + tone2) * (0.8 + 0.2 * sin(2.0 * PI * 6.0 * t))
              }
              BuzzerSoundType.COSMIC -> {
                // FM synthesizer tone
                val mod = 150.0 * sin(2.0 * PI * 12.0 * t)
                val carrier = 520.0 + mod
                24000.0 * sin(2.0 * PI * carrier * t)
              }
            }
            buffer[i] = sampleValue.toInt().coerceIn(-32767, 32767).toShort()
            sampleIndex++
          }
          track.write(buffer, 0, buffer.size)
        }
      } catch (_: Exception) {
        // Safe exit
      } finally {
        try {
          track.stop()
          track.release()
        } catch (_: Exception) {}
      }
    }
  }

  private fun startRingtone(): Boolean {
    return try {
      val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
      val mp = MediaPlayer()
      mp.setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      )
      mp.setDataSource(context, uri)
      mp.isLooping = true
      mp.prepare()
      mp.start()
      ringtonePlayer = mp
      true
    } catch (e: Exception) {
      android.util.Log.w("KomboArena", "No se pudo reproducir el tono de llamada", e)
      false
    }
  }

  fun stopBuzzer() {
    runCatching { ringtonePlayer?.stop() }
    runCatching { ringtonePlayer?.release() }
    ringtonePlayer = null
    isPlaying = false
    playbackJob?.cancel()
    playbackJob = null
    try {
      audioTrack?.stop()
      audioTrack?.release()
    } catch (_: Exception) {}
    audioTrack = null
    stopVibration()
  }

  private fun startVibration(soundType: BuzzerSoundType) {
    try {
      val v = vibrator ?: return
      if (!v.hasVibrator()) return
      // Patrón tipo llamada: vibra fuerte, pausa corta, y se repite hasta que se detiene.
      val timings = when (soundType) {
        BuzzerSoundType.HEARTBEAT -> longArrayOf(0, 120, 80, 140, 500)
        else -> longArrayOf(0, 900, 350, 900, 350)
      }
      // Con usage ALARM Android permite vibrar desde segundo plano (si no, lo ignora).
      val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = if (v.hasAmplitudeControl()) {
          val amps = IntArray(timings.size) { i -> if (i % 2 == 1) 255 else 0 }
          VibrationEffect.createWaveform(timings, amps, 0)
        } else {
          VibrationEffect.createWaveform(timings, 0)
        }
        @Suppress("DEPRECATION")
        v.vibrate(effect, attrs)
      } else {
        @Suppress("DEPRECATION")
        v.vibrate(timings, 0, attrs)
      }
    } catch (e: Exception) {
      android.util.Log.w("KomboArena", "No se pudo vibrar", e)
    }
  }

  /** Vibración muy corta para confirmar el toque en el teléfono de quien envía. */
  fun softHaptic(ms: Long = 40L) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(ms)
      }
    } catch (_: Exception) {}
  }

  private fun stopVibration() {
    try {
      vibrator?.cancel()
    } catch (_: Exception) {}
  }

  // Play a short cute sound effect (e.g., Kiss, Sent message, Call chime)
  fun playShortSfx(type: ShortSfxType) {
    scope.launch {
      val sampleRate = 44100
      val durationMs = when (type) {
        ShortSfxType.KISS -> 300
        ShortSfxType.MESSAGE_SENT -> 150
        ShortSfxType.CALL_RING -> 800
        ShortSfxType.SUCCESS -> 400
      }
      val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
      val buffer = ShortArray(totalSamples)

      for (i in 0 until totalSamples) {
        val t = i.toDouble() / sampleRate
        val progress = i.toDouble() / totalSamples
        val sampleValue = when (type) {
          ShortSfxType.KISS -> {
            // Cute pop sound with rapid pitch dive
            val freq = 900.0 - (progress * 500.0)
            val envelope = (1.0 - progress)
            26000.0 * envelope * sin(2.0 * PI * freq * t)
          }
          ShortSfxType.MESSAGE_SENT -> {
            // Gentle high chime
            val freq = 1046.5 // C6
            val envelope = (1.0 - progress)
            20000.0 * envelope * sin(2.0 * PI * freq * t)
          }
          ShortSfxType.CALL_RING -> {
            val freq = 480.0
            val envelope = if (progress < 0.8) 1.0 else (1.0 - (progress - 0.8) / 0.2)
            22000.0 * envelope * sin(2.0 * PI * freq * t)
          }
          ShortSfxType.SUCCESS -> {
            val freq = 523.25 + (progress * 300.0)
            val envelope = (1.0 - progress)
            22000.0 * envelope * sin(2.0 * PI * freq * t)
          }
        }
        buffer[i] = sampleValue.toInt().coerceIn(-32767, 32767).toShort()
      }

      val track = AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(buffer.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

      track.write(buffer, 0, buffer.size)
      track.play()
    }
  }

  enum class ShortSfxType {
    KISS,
    MESSAGE_SENT,
    CALL_RING,
    SUCCESS
  }
}
