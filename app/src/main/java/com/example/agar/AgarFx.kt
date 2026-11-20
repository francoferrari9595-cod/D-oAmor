package com.example.agar

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val size: Float, val color: Color)
internal class Ring(val x: Float, val y: Float, val maxR: Float, var life: Float, val max: Float, val color: Color, val width: Float)
internal class FloatText(val x: Float, var y: Float, val text: String, var life: Float, val color: Color)

/** Efectos especiales: partículas, ondas expansivas, textos flotantes, sacudida de pantalla y destellos. */
internal class FxState {
  val parts = ArrayList<Particle>()
  val rings = ArrayList<Ring>()
  val texts = ArrayList<FloatText>()
  var shake = 0f
  var danger = 0f
  var flash = 0f
  var flashColor = Color.White

  fun burst(x: Float, y: Float, color: Color, n: Int, speed: Float, size: Float, rnd: Random) {
    for (i in 0 until n) {
      if (parts.size >= 420) return
      val a = rnd.nextFloat() * 2f * PI.toFloat(); val v = speed * (0.35f + rnd.nextFloat() * 0.65f)
      val life = 0.45f + rnd.nextFloat() * 0.5f
      parts.add(Particle(x, y, cos(a) * v, sin(a) * v, life, life, size * (0.6f + rnd.nextFloat() * 0.8f), color))
    }
  }

  fun ring(x: Float, y: Float, maxR: Float, color: Color, width: Float, life: Float = 0.5f) {
    if (rings.size < 60) rings.add(Ring(x, y, maxR, life, life, color, width))
  }

  /** Convierte los eventos del motor en efectos visibles (solo los que están a la vista). */
  fun consume(world: AgarWorld, rnd: Random, inView: (Float, Float) -> Boolean) {
    for (ev in world.fx) {
      if (!inView(ev.x, ev.y)) continue
      val col = A_COLORS[ev.color % A_COLORS.size]
      when (ev.kind) {
        FX_PELLET -> burst(ev.x, ev.y, col, 3, 90f, 4f, rnd)
        FX_EAT -> {
          burst(ev.x, ev.y, col, 16, 260f, 7f, rnd)
          ring(ev.x, ev.y, aSize(ev.mass) * 1.5f + 30f, col, 10f)
          if (ev.mass >= 15f) texts.add(FloatText(ev.x, ev.y, "+${ev.mass.toInt()}", 1.1f, Color.White))
          shake = maxOf(shake, 0.12f)
        }
        FX_POP -> {
          burst(ev.x, ev.y, Color(0xFF33FF33), 28, 340f, 9f, rnd)
          ring(ev.x, ev.y, 280f, Color(0xFF66FF66), 16f, 0.6f)
          shake = maxOf(shake, 0.45f)
        }
        FX_SPLIT -> { burst(ev.x, ev.y, col, 8, 200f, 6f, rnd); ring(ev.x, ev.y, aSize(ev.mass) * 1.8f + 20f, col, 8f, 0.4f) }
        FX_EJECT -> burst(ev.x, ev.y, col, 4, 120f, 4f, rnd)
      }
    }
    world.fx.clear()
  }

  fun step(dt: Float) {
    var i = parts.size - 1
    val drag = Math.pow(0.04, dt.toDouble()).toFloat()
    while (i >= 0) {
      val p = parts[i]; p.life -= dt
      if (p.life <= 0f) parts.removeAt(i) else { p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= drag; p.vy *= drag }
      i--
    }
    i = rings.size - 1; while (i >= 0) { rings[i].life -= dt; if (rings[i].life <= 0f) rings.removeAt(i); i-- }
    i = texts.size - 1; while (i >= 0) { val t = texts[i]; t.life -= dt; t.y -= 70f * dt; if (t.life <= 0f) texts.removeAt(i); i-- }
    shake = maxOf(0f, shake - dt * 1.4f); flash = maxOf(0f, flash - dt * 1.6f)
  }
}
