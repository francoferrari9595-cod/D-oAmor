package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Sonidos reales de los juegos. Si en res/raw existen archivos con estos nombres
 * (sfx_move, sfx_start, sfx_win, sfx_dice, sfx_card, sfx_boom, sfx_ball...), se reproducen;
 * si no, la app usa los tonos sintéticos de siempre.
 */
object GameSfx {
  private var pool: SoundPool? = null
  private val ids = HashMap<String, Int>()
  private val pending = HashSet<Int>()

  /** Carga los sonidos en memoria sin reproducirlos, para que suenen al instante la primera vez. */
  fun preload(ctx: Context, vararg names: String) {
    val p = pool ?: SoundPool.Builder()
      .setMaxStreams(6)
      .setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
      ).build().also { sp ->
        pool = sp
        sp.setOnLoadCompleteListener { s, sid, status -> if (status == 0 && pending.remove(sid)) s.play(sid, 1f, 1f, 1, 0, 1f) }
      }
    for (n in names) {
      if (ids.containsKey(n)) continue
      val resId = ctx.resources.getIdentifier(n, "raw", ctx.packageName)
      if (resId != 0) ids[n] = p.load(ctx, resId, 1)
    }
  }

  fun play(ctx: Context, name: String): Boolean = play(ctx, name, 1f)

  fun play(ctx: Context, name: String, volume: Float): Boolean {
    val safeVolume = volume.coerceIn(0f, 1f)
    val resId = ctx.resources.getIdentifier(name, "raw", ctx.packageName)
    if (resId == 0) return false
    val p = pool ?: SoundPool.Builder()
      .setMaxStreams(6)
      .setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
      ).build().also { sp ->
        pool = sp
        sp.setOnLoadCompleteListener { s, sid, status -> if (status == 0 && pending.remove(sid)) s.play(sid, 1f, 1f, 1, 0, 1f) }
      }
    val sid = ids[name]
    if (sid != null) {
      p.play(sid, safeVolume, safeVolume, 1, 0, 1f)
    } else {
      val loaded = p.load(ctx, resId, 1)
      ids[name] = loaded
      pending.add(loaded)
    }
    return true
  }
}
