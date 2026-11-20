package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.model.RoomPrefs

/**
 * Música de fondo de la app: una lista de reproducción que arranca al terminar la presentación
 * (cuando se ven las salas) y sigue sonando mientras se juega. Volumen fijo y bajo; se apaga desde Ajustes.
 *
 * Para agregar canciones: copiar el .mp3 a res/raw (nombre en minúsculas, con guiones bajos, p. ej.
 * musica_nueva_cancion.mp3) y sumar su referencia a [PLAYLIST]. Con una sola canción se repite en bucle;
 * con varias, suenan al azar (nunca la misma dos veces seguidas).
 */
object BackgroundMusic {
  /** 10 % del volumen del dispositivo, para no tapar los sonidos de los juegos ni las voces. */
  const val VOLUME = 0.1f

  /** Lista de reproducción, en orden. Se agregan nuevas canciones debajo de las anteriores. */
  val PLAYLIST: List<Int> = listOf(
    com.example.R.raw.musica_elevator_jazz_loop,
    // Gorillaz «Demon Days» (mix de 49 min comprimido a Opus y cortado en 8 partes para que se intercale con las demás).
    com.example.R.raw.musica_gorillaz_demon_days_1,
    com.example.R.raw.musica_gorillaz_demon_days_2,
    com.example.R.raw.musica_gorillaz_demon_days_3,
    com.example.R.raw.musica_gorillaz_demon_days_4,
    com.example.R.raw.musica_gorillaz_demon_days_5,
    com.example.R.raw.musica_gorillaz_demon_days_6,
    com.example.R.raw.musica_gorillaz_demon_days_7,
    com.example.R.raw.musica_gorillaz_demon_days_8
  )

  // ---- Pausa automática mientras alguien habla (walkie, nota de voz, llamada) ----
  // Cada fuente de voz «retiene» la música con su propia clave; suena otra vez cuando se sueltan todas.
  // Una retención vence sola a los 2 min por si algo falla y no se libera (la música nunca queda muda).
  private val voiceHolds = HashMap<String, Long>()
  private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  private const val HOLD_MAX_MS = 120_000L

  private fun activeHolds(): Boolean {
    val now = android.os.SystemClock.elapsedRealtime()
    synchronized(voiceHolds) {
      voiceHolds.entries.removeAll { now - it.value > HOLD_MAX_MS }
      return voiceHolds.isNotEmpty()
    }
  }

  /** Pausa la música mientras dure la voz [key] (se puede llamar muchas veces con la misma clave). */
  fun holdForVoice(key: String) {
    synchronized(voiceHolds) { voiceHolds[key] = android.os.SystemClock.elapsedRealtime() }
    handler.post { sync() }
    handler.postDelayed({ sync() }, HOLD_MAX_MS + 500)
  }

  /** Suelta la retención de [key]; si ya no queda ninguna, la música continúa donde estaba. */
  fun releaseVoice(key: String) {
    synchronized(voiceHolds) { voiceHolds.remove(key) }
    handler.post { sync() }
  }

  private var player: MediaPlayer? = null
  private var appCtx: Context? = null
  private var index = 0
  private var firstPlay = true
  private var started = false      // ya pasó la presentación
  private var inForeground = true  // la app está a la vista

  /** Llamar cuando termina la presentación: desde ahí suena la música. */
  fun start(ctx: Context) { appCtx = ctx.applicationContext; started = true; sync() }

  fun onForeground(ctx: Context) { appCtx = ctx.applicationContext; inForeground = true; sync() }

  fun onBackground() { inForeground = false; runCatching { player?.pause() } }

  fun isEnabled(ctx: Context): Boolean = RoomPrefs.musicEnabled(ctx.applicationContext)

  /** Activa o desactiva la música (se recuerda en el teléfono). */
  fun setEnabled(ctx: Context, on: Boolean) {
    appCtx = ctx.applicationContext
    RoomPrefs.setMusicEnabled(ctx.applicationContext, on)
    sync()
  }

  private fun shouldPlay(): Boolean {
    val c = appCtx ?: return false
    return started && inForeground && RoomPrefs.musicEnabled(c) && !activeHolds()
  }

  private fun sync() {
    if (!shouldPlay()) { runCatching { player?.pause() }; return }
    val p = player
    if (p == null) playTrack(if (firstPlay) { firstPlay = false; kotlin.random.Random.nextInt(PLAYLIST.size.coerceAtLeast(1)) } else index) else runCatching { if (!p.isPlaying) p.start() }
  }

  /** Pista siguiente al azar, distinta de la que acaba de sonar. */
  private fun nextRandom(current: Int, size: Int): Int {
    if (size <= 1) return 0
    var n = kotlin.random.Random.nextInt(size - 1)
    if (n >= current) n++
    return n
  }

  private fun release() {
    player?.let { runCatching { it.stop() }; runCatching { it.release() } }
    player = null
  }

  private fun playTrack(i: Int) {
    val app = appCtx ?: return
    val list = PLAYLIST
    if (list.isEmpty()) return
    val idx = ((i % list.size) + list.size) % list.size
    index = idx
    release()
    val mp = MediaPlayer()
    try {
      mp.setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
      )
      app.resources.openRawResourceFd(list[idx]).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
      mp.setVolume(VOLUME, VOLUME)
      mp.isLooping = list.size == 1
      mp.setOnCompletionListener { if (list.size > 1 && player === it) playTrack(nextRandom(idx, list.size)) }
      mp.setOnErrorListener { m, _, _ -> if (player === m) release(); true }
      mp.setOnPreparedListener { m -> if (player === m && shouldPlay()) runCatching { m.start() } }
      player = mp
      mp.prepareAsync()
    } catch (_: Exception) {
      runCatching { mp.release() }
      if (player === mp) player = null
    }
  }
}
