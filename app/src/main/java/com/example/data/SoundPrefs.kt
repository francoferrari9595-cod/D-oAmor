package com.example.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** Estado de visibilidad de la app (para decidir si mostrar notificaciones de fondo). */
object AppVisibility {
  @Volatile var foreground: Boolean = false
}

/** Sonidos y avisos configurables por tipo: mensajes, notas de voz y llamadas. */
object SoundPrefs {
  const val MESSAGE = "msg"
  const val AUDIO = "audio"
  const val CALL = "call"
  private const val PREFS = "doamor_sounds"

  private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** "" = sonido por defecto del teléfono, "silent" = silencio, otro = uri elegida. */
  fun raw(ctx: Context, kind: String): String = prefs(ctx).getString("uri_$kind", "") ?: ""
  fun setRaw(ctx: Context, kind: String, value: String) { prefs(ctx).edit().putString("uri_$kind", value).apply() }
  fun enabled(ctx: Context, kind: String): Boolean = prefs(ctx).getBoolean("on_$kind", true)
  fun setEnabled(ctx: Context, kind: String, on: Boolean) { prefs(ctx).edit().putBoolean("on_$kind", on).apply() }

  fun resolve(ctx: Context, kind: String): Uri? {
    val s = raw(ctx, kind)
    return when {
      s == "silent" -> null
      s.isEmpty() -> RingtoneManager.getDefaultUri(if (kind == CALL) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION)
      else -> Uri.parse(s)
    }
  }

  fun label(ctx: Context, kind: String): String {
    val s = raw(ctx, kind)
    return when {
      s == "silent" -> "Silencio"
      s.isEmpty() -> "Predeterminado del teléfono"
      else -> runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(s))?.getTitle(ctx) }.getOrNull() ?: "Personalizado"
    }
  }

  /** Reproduce una vez el sonido elegido (aviso dentro de la app). */
  fun play(ctx: Context, kind: String) {
    if (!enabled(ctx, kind)) return
    val u = resolve(ctx, kind) ?: return
    runCatching { RingtoneManager.getRingtone(ctx, u)?.play() }
  }
}
