package com.example.audio

import android.content.Context

/**
 * Saludo de bienvenida (res/raw/bienvenida.mp3). Suena UNA sola vez cada vez que se abre la plataforma;
 * girar la pantalla o volver a la lista de salas no lo repite.
 */
object WelcomeSound {
  @Volatile private var played = false

  fun playOnce(ctx: Context) {
    if (played) return
    played = true
    runCatching { GameSfx.play(ctx.applicationContext, "bienvenida") }
  }
}
