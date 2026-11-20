package com.example.model

import android.content.Context
import androidx.compose.ui.graphics.Color

/** Modelo de bola de boliche: tres colores (brillo, cuerpo, sombra) y precio en fichas (0 = gratis). */
data class BowlBall(val id: Int, val name: String, val emoji: String, val hi: Color, val mid: Color, val lo: Color, val price: Long)

val BOWL_BALLS = listOf(
  BowlBall(0, "Clásica azul", "🔵", Color(0xFF7886FF), Color(0xFF1D2A8C), Color(0xFF080C33), 0),
  BowlBall(1, "Rubí", "🔴", Color(0xFFFF8A80), Color(0xFFC62828), Color(0xFF3B0A0A), 0),
  BowlBall(2, "Esmeralda", "🟢", Color(0xFF8CFFB0), Color(0xFF1B8A4B), Color(0xFF05260F), 0),
  BowlBall(3, "Galaxia", "🌌", Color(0xFFD1A3FF), Color(0xFF6A1B9A), Color(0xFF12002B), 150),
  BowlBall(4, "Lava", "🌋", Color(0xFFFFE082), Color(0xFFE65100), Color(0xFF2B0A00), 200),
  BowlBall(5, "Hielo", "🧊", Color(0xFFE0F7FF), Color(0xFF29B6F6), Color(0xFF01324F), 200),
  BowlBall(6, "Oro", "🥇", Color(0xFFFFF3A0), Color(0xFFFFB300), Color(0xFF4A3000), 350),
  BowlBall(7, "Neón rosa", "💗", Color(0xFFFFB3E6), Color(0xFFFF2E93), Color(0xFF3A0020), 300)
)

/** Compras y selección cosmética guardadas en este teléfono (las fichas salen de la billetera compartida). */
object Cosmetics {
  private const val PREFS = "sinijoss_cosmetics"
  private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun ownedBalls(ctx: Context): Set<Int> {
    val saved = p(ctx).getStringSet("owned_balls", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
    return saved + BOWL_BALLS.filter { it.price == 0L }.map { it.id }
  }
  fun markBallOwned(ctx: Context, id: Int) {
    val now = p(ctx).getStringSet("owned_balls", emptySet()).orEmpty().toMutableSet(); now.add(id.toString())
    p(ctx).edit().putStringSet("owned_balls", now).apply()
  }
  fun selectedBall(ctx: Context): Int = p(ctx).getInt("bowl_ball", 0).takeIf { it in ownedBalls(ctx) } ?: 0
  fun selectBall(ctx: Context, id: Int) { if (id in ownedBalls(ctx)) p(ctx).edit().putInt("bowl_ball", id).apply() }
}
