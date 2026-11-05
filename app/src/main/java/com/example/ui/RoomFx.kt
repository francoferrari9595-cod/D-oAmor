package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FX_LIST
import com.example.model.GIFTS
import com.example.model.RoomMsg
import com.example.model.entryFx
import com.example.model.shopItem
import com.example.ui.screens.GiftArt
import com.example.ui.theme.GoldYellow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ═══════════════ Estado social de la sala (chat + efectos) ═══════════════

class FxEvent(val kind: String, val title: String, val sub: String, val giftId: String, val key: Long, val entry: Boolean, val mine: Boolean, val forMe: Boolean = false)

/** Mantiene los mensajes del chat de la sala, el contador de no leídos y la cola de efectos de pantalla. */
class RoomSocial(private val vm: CoupleViewModel) {
  val messages = mutableStateListOf<RoomMsg>()
  var unread by mutableStateOf(0)
  var chatOpen by mutableStateOf(false)
  var current by mutableStateOf<FxEvent?>(null)
  var toast by mutableStateOf("")
  private val queue = ArrayDeque<FxEvent>()
  private val seen = HashSet<String>()
  private var first = true
  private val openedAt = System.currentTimeMillis()
  private var keySeq = 0L
  private var lastFxSent = 0L

  fun toMsg(m: Map<String, Any?>): RoomMsg = RoomMsg(
    id = m["id"] as? String ?: "", uid = m["uid"] as? String ?: "", name = m["name"] as? String ?: "Jugador",
    kind = m["kind"] as? String ?: "text", text = m["text"] as? String ?: "", gift = m["gift"] as? String ?: "",
    to = m["to"] as? String ?: "", toName = m["toName"] as? String ?: "", fx = m["fx"] as? String ?: "",
    bubble = m["bub"] as? String ?: "", nameCol = m["nmc"] as? String ?: "", t = (m["tms"] as? Number)?.toLong() ?: 0L
  )

  fun onSnapshot(list: List<Map<String, Any?>>, me: String?) {
    val parsed = list.map { toMsg(it) }.sortedBy { it.t }
    val fresh = parsed.filter { seen.add(it.id) }
    messages.clear(); messages.addAll(parsed)
    if (first) { first = false; return } // el historial no dispara efectos
    for (m in fresh) {
      if (m.t < openedAt - 10_000L) continue // mensaje viejo que llegó tarde desde la caché
      val mine = m.uid == me
      if (!mine && !chatOpen && (m.kind == "text" || m.kind == "gift")) unread++
      when (m.kind) {
        "gift" -> {
          val g = GIFTS.firstOrNull { it.id == m.gift }
          if (g != null) {
            val dest = if (m.to.isBlank() || m.to == "all") "para todos" else "para ${m.toName}"
            val forMe = !mine && (m.to.isBlank() || m.to == "all" || m.to == me)
            if (forMe && !com.example.model.RoomPrefs.giftBanners(vm.getApplication<android.app.Application>())) com.example.audio.SoftChime.play(vm.getApplication<android.app.Application>())
            enqueue("gift", "${m.name} envió ${g.name}", dest, g.id, false, mine, forMe)
            if (m.to == me && !mine) vm.addCredits(max(5L, g.price / 10))
          }
        }
        "fx" -> {
          val fx = FX_LIST.firstOrNull { it.id == m.fx }
          if (fx != null) enqueue(fx.id, "${fx.emoji} ${m.name} lanzó ${fx.name}", "", "", false, mine)
        }
        "join" -> if (m.fx.isNotBlank()) enqueue(m.fx, "${m.name} entró a la sala", "", "", true, mine)
      }
    }
  }

  private fun enqueue(kind: String, title: String, sub: String, gift: String, entry: Boolean, mine: Boolean, forMe: Boolean = false) {
    val ctx = vm.getApplication<android.app.Application>()
    if (!com.example.model.RoomPrefs.fxEnabled(ctx)) return
    if (kind == "gift" && !com.example.model.RoomPrefs.giftBanners(ctx)) return
    if (entry && !mine && !com.example.model.RoomPrefs.joinFx(ctx)) return
    if (queue.size >= 6) return
    val ev = FxEvent(kind, title, sub, gift, ++keySeq, entry, mine, forMe)
    if (current == null) current = ev else queue.addLast(ev)
  }

  fun next() { current = if (queue.isEmpty()) null else queue.removeFirst() }

  /** Reproduce un efecto solo en este teléfono (vista previa de la tienda). */
  fun preview(kind: String, title: String) {
    queue.clear()
    current = FxEvent(kind, title, "", "", ++keySeq, true, true)
  }

  fun say(text: String) { toast = text }

  fun sendText(text: String) {
    val t = text.trim().take(200)
    if (t.isBlank()) return
    val ctx = vm.getApplication<android.app.Application>()
    vm.sendRoomChat(
      mapOf(
        "kind" to "text", "text" to t,
        "bub" to com.example.model.LocalWallet.equipped(ctx, com.example.model.Cat.BUBBLE),
        "nmc" to com.example.model.LocalWallet.equipped(ctx, com.example.model.Cat.NAME)
      )
    )
  }

  /** Lanza un efecto de pantalla para toda la sala (con enfriamiento de 8 s para no saturar). */
  fun sendFx(fxId: String): Boolean {
    val fx = FX_LIST.firstOrNull { it.id == fxId } ?: return false
    val now = System.currentTimeMillis()
    if (now - lastFxSent < 8_000L) { say("Esperá unos segundos para lanzar otro efecto ⏳"); return false }
    if (fx.price > 0 && !vm.trySpend(fx.price)) { say("No te alcanzan las fichas 😢"); return false }
    lastFxSent = now
    vm.sendRoomChat(mapOf("kind" to "fx", "fx" to fx.id, "text" to ""))
    return true
  }

  fun sendGift(giftId: String, toUid: String, toName: String): Boolean {
    val g = GIFTS.firstOrNull { it.id == giftId } ?: return false
    if (!vm.trySpend(g.price)) { say("No te alcanzan las fichas 😢"); return false }
    vm.sendRoomChat(mapOf("kind" to "gift", "gift" to g.id, "to" to toUid, "toName" to toName, "text" to ""))
    say("¡${g.name} enviado! 🎁")
    return true
  }

  fun announceJoin() {
    val ctx = vm.getApplication<android.app.Application>()
    if (!com.example.model.RoomPrefs.announceJoin(ctx)) return
    val entry = com.example.model.LocalWallet.equipped(ctx, com.example.model.Cat.ENTRY)
    vm.sendRoomChat(mapOf("kind" to "join", "text" to "", "fx" to entryFx(entry)))
  }
}

// ═══════════════ Efectos de pantalla ═══════════════

private fun frand(i: Int, k: Int): Float {
  val x = sin(i * 12.9898 + k * 78.233) * 43758.5453
  return (x - floor(x)).toFloat()
}
private fun easeOutF(x: Float): Float { val k = 1f - x.coerceIn(0f, 1f); return 1f - k * k * k }

private fun DrawScope.fxEmoji(paint: android.graphics.Paint, e: String, x: Float, y: Float, size: Float, alpha: Float) {
  paint.textSize = size
  paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
  drawIntoCanvas { it.nativeCanvas.drawText(e, x, y, paint) }
}

private val FX_PAL = listOf(Color(0xFFFF4D8D), Color(0xFFFFD60A), Color(0xFF37E6FF), Color(0xFF7CFF6B), Color(0xFFB388FF), Color(0xFFFF9500))

private fun DrawScope.fxSparkle(c: Offset, r: Float, col: Color) {
  drawLine(col, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 2.5f)
  drawLine(col, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 2.5f)
  drawLine(col.copy(alpha = col.alpha * 0.7f), Offset(c.x - r * 0.6f, c.y - r * 0.6f), Offset(c.x + r * 0.6f, c.y + r * 0.6f), 1.8f)
  drawLine(col.copy(alpha = col.alpha * 0.7f), Offset(c.x - r * 0.6f, c.y + r * 0.6f), Offset(c.x + r * 0.6f, c.y - r * 0.6f), 1.8f)
}

/** Dibuja el efecto `kind` en el instante `p` (0..1). */
private fun DrawScope.drawFx(kind: String, p: Float, paint: android.graphics.Paint) {
  val w = size.width; val h = size.height
  val env = if (p < 0.08f) p / 0.08f else if (p > 0.86f) (1f - p) / 0.14f else 1f
  when (kind) {
    "confetti" -> {
      for (i in 0 until 110) {
        val x0 = frand(i, 1) * w
        val sp = 0.7f + frand(i, 2) * 0.9f
        val y = -40f + (p * sp * 1.25f) * (h + 80f) - frand(i, 3) * 200f
        val sway = sin(p * 9f + i) * 26f
        val rot = p * 540f * (0.5f + frand(i, 4)) + i * 17f
        val cw = 9f + frand(i, 5) * 10f
        rotate(rot, Offset(x0 + sway, y)) { drawRect(FX_PAL[i % FX_PAL.size].copy(alpha = env), Offset(x0 + sway - cw / 2, y - cw / 4), Size(cw, cw / 2)) }
      }
    }
    "hearts" -> {
      val es = listOf("❤️", "💖", "💗", "💕")
      for (i in 0 until 36) {
        val sp = 0.6f + frand(i, 2) * 0.7f
        val y = h * 1.1f - p * sp * h * 1.35f - frand(i, 3) * h * 0.25f
        val x = frand(i, 1) * w + sin(p * 6f + i) * 22f
        fxEmoji(paint, es[i % es.size], x, y, 30f + frand(i, 4) * 56f, env)
      }
    }
    "snow" -> {
      drawRect(Color(0xFF0B1E3D).copy(alpha = 0.35f * env))
      for (i in 0 until 70) {
        val sp = 0.5f + frand(i, 2) * 0.8f
        val y = -30f + p * sp * (h + 60f) * 1.2f - frand(i, 3) * 220f
        val x = frand(i, 1) * w + sin(p * 5f + i) * 24f
        if (i % 3 == 0) fxEmoji(paint, "❄️", x, y, 26f + frand(i, 4) * 30f, env)
        else drawCircle(Color.White.copy(alpha = 0.85f * env), 3f + frand(i, 4) * 5f, Offset(x, y))
      }
    }
    "stars" -> {
      val es = listOf("⭐", "✨", "🌟")
      for (i in 0 until 40) {
        val tw = 0.5f + 0.5f * sin(p * 14f + i * 1.7f)
        val x = frand(i, 1) * w; val y = frand(i, 2) * h
        fxEmoji(paint, es[i % es.size], x, y, (22f + frand(i, 3) * 48f) * (0.6f + 0.6f * tw), env * (0.4f + 0.6f * tw))
      }
      for (i in 0 until 6) {
        val q = ((p - i * 0.1f) / 0.3f)
        if (q in 0f..1f) {
          val sx = w * frand(i, 8); val sy = h * 0.1f * frand(i, 9)
          drawLine(Color.White.copy(alpha = (1f - q) * env), Offset(sx + q * w * 0.5f, sy + q * h * 0.4f), Offset(sx + q * w * 0.5f - 90f, sy + q * h * 0.4f - 60f), 4f)
        }
      }
    }
    "fire" -> {
      drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAAFF4A00).copy(alpha = 0.7f * env), Color(0xFFFF9500).copy(alpha = 0.9f * env)), h * 0.45f, h), Offset(0f, h * 0.45f), Size(w, h * 0.55f))
      for (i in 0 until 30) {
        val sp = 0.5f + frand(i, 2) * 0.8f
        val prog = (p * sp * 1.6f + frand(i, 3)) % 1f
        val x = frand(i, 1) * w + sin(p * 12f + i) * 14f
        val y = h * (1.02f - prog * 0.55f)
        fxEmoji(paint, "🔥", x, y, 50f + frand(i, 4) * 70f * (1f - prog * 0.5f), env * (1f - prog * 0.6f))
      }
      for (i in 0 until 40) {
        val prog = (p * 2f + frand(i, 5)) % 1f
        drawCircle(Color(0xFFFFD166).copy(alpha = (1f - prog) * env), 2.5f, Offset(frand(i, 6) * w + sin(prog * 8f + i) * 18f, h * (1f - prog * 0.8f)))
      }
    }
    "fireworks" -> {
      drawRect(Color(0xFF05020F).copy(alpha = 0.45f * env))
      for (b in 0 until 6) {
        val q = (p - b * 0.12f) / 0.5f
        if (q <= 0f || q >= 1f) continue
        val cx = w * (0.15f + 0.7f * frand(b, 7)); val cy = h * (0.12f + 0.38f * frand(b, 8))
        val col = FX_PAL[(b + 1) % FX_PAL.size]
        for (k in 0 until 38) {
          val ang = k * (2f * PI.toFloat() / 38f)
          val spd = (0.6f + 0.4f * frand(k + b * 50, 9)) * w * 0.34f
          val r = spd * easeOutF(q)
          val gy = q * q * h * 0.12f
          drawCircle(col.copy(alpha = (1f - q)), 4.5f * (1f - q) + 1.4f, Offset(cx + cos(ang) * r, cy + sin(ang) * r + gy))
          drawCircle(col.copy(alpha = (1f - q) * 0.35f), 2.4f, Offset(cx + cos(ang) * r * 0.86f, cy + sin(ang) * r * 0.86f + gy * 0.8f))
        }
        drawCircle(Color.White.copy(alpha = (1f - q) * 0.55f), 34f * (1f - q), Offset(cx, cy))
      }
    }
    "lightning" -> {
      drawRect(Color(0xFF0B0830).copy(alpha = 0.55f * env))
      val flashes = listOf(0.06f, 0.22f, 0.5f)
      var fl = 0f; var idx = 0
      for ((i, t) in flashes.withIndex()) { val f = (1f - abs(p - t) / 0.05f).coerceAtLeast(0f); if (f > fl) { fl = f; idx = i } }
      if (fl > 0f) {
        drawRect(Color.White.copy(alpha = fl * 0.75f))
        var x = w * (0.25f + 0.5f * frand(idx, 3)); var y = 0f
        val path = Path(); path.moveTo(x, y)
        for (s in 0 until 10) { x += (frand(s, idx + 11) - 0.5f) * 120f; y += h / 9f; path.lineTo(x, y) }
        drawPath(path, Color(0xFF7DF9FF).copy(alpha = fl), style = Stroke(width = 22f))
        drawPath(path, Color.White.copy(alpha = fl), style = Stroke(width = 8f))
      }
    }
    "galaxy" -> {
      val cx = w / 2f; val cy = h * 0.42f
      drawCircle(Brush.radialGradient(listOf(Color(0xFF7C4DFF).copy(alpha = 0.55f * env), Color.Transparent), Offset(cx, cy), w * 0.7f), w * 0.7f, Offset(cx, cy))
      val maxR = hypot(w, h) * 0.5f
      for (i in 0 until 150) {
        val tt = i / 150f
        val ang = tt * 14f + p * 5f
        val rr = tt * maxR * 0.7f * easeOutF(min(1f, p * 2f))
        val col = when (i % 3) { 0 -> Color(0xFF7DF9FF); 1 -> Color(0xFFB388FF); else -> Color.White }
        drawCircle(col.copy(alpha = env * (0.35f + 0.65f * frand(i, 3))), 2f + 4f * frand(i, 4), Offset(cx + cos(ang) * rr, cy + sin(ang) * rr * 0.75f))
      }
    }
    "sakura" -> {
      drawRect(Color(0xFF3A0B2A).copy(alpha = 0.28f * env))
      for (i in 0 until 60) {
        val sp = 0.45f + frand(i, 2) * 0.6f
        val y = -40f + p * sp * (h + 80f) * 1.15f - frand(i, 3) * 260f
        val x = frand(i, 1) * w + sin(p * 4f + i) * 60f
        fxEmoji(paint, "🌸", x, y, 22f + frand(i, 4) * 30f, env)
      }
    }
    "rain" -> {
      drawRect(Color(0xFF020818).copy(alpha = 0.5f * env))
      for (i in 0 until 90) {
        val sp = 1.3f + frand(i, 2) * 1.2f
        val prog = (p * sp + frand(i, 3)) % 1f
        val x = frand(i, 1) * w; val y = prog * (h + 80f) - 40f
        val col = if (i % 2 == 0) Color(0xFF37E6FF) else Color(0xFFB388FF)
        drawLine(col.copy(alpha = env * 0.85f), Offset(x, y), Offset(x - 8f, y + 40f + 26f * frand(i, 5)), 3f)
      }
    }
    "bubbles" -> {
      for (i in 0 until 40) {
        val sp = 0.5f + frand(i, 2) * 0.7f
        val y = h * 1.1f - p * sp * h * 1.3f - frand(i, 3) * h * 0.3f
        val x = frand(i, 1) * w + sin(p * 6f + i) * 26f
        val r = 14f + frand(i, 4) * 40f
        drawCircle(Color(0xFF7DF9FF).copy(alpha = 0.22f * env), r, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.7f * env), r, Offset(x, y), style = Stroke(width = 2.5f))
        drawCircle(Color.White.copy(alpha = 0.8f * env), r * 0.18f, Offset(x - r * 0.35f, y - r * 0.35f))
      }
    }
    "meteor" -> {
      drawRect(Color(0xFF05020F).copy(alpha = 0.5f * env))
      for (i in 0 until 12) {
        val q = ((p * 1.6f - i * 0.07f) / 0.45f)
        if (q !in 0f..1f) continue
        val sx = w * (0.1f + 0.9f * frand(i, 1)); val sy = -40f
        val ex = sx - w * 0.5f; val ey = h * (0.7f + 0.3f * frand(i, 2))
        val hx = sx + (ex - sx) * q; val hy = sy + (ey - sy) * q
        drawLine(Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFFD166)), Offset(hx + (sx - ex) * 0.25f, hy + (sy - ey) * 0.25f), Offset(hx, hy)), Offset(hx + (sx - ex) * 0.25f, hy + (sy - ey) * 0.25f), Offset(hx, hy), 7f)
        drawCircle(Color.White.copy(alpha = env), 7f, Offset(hx, hy))
      }
    }
    "portal" -> {
      // Portal dimensional vectorial: anillos luminosos y partículas, sin emojis.
      drawRect(Color(0xFF020312).copy(alpha = 0.58f * env))
      val cx = w * (0.5f + 0.08f * sin(p * 5f)); val cy = h * 0.48f
      val maxRadius = min(w, h) * 0.48f
      for (ring in 0 until 12) {
        val phase = p * 360f + ring * 30f
        val r = maxRadius * (0.35f + ring * 0.055f) * (0.92f + 0.08f * sin(p * 8f + ring))
        val col = listOf(Color(0xFF37E6FF), Color(0xFF7C4DFF), Color(0xFFFF4D8D))[ring % 3]
        drawCircle(col.copy(alpha = env * (0.32f + ring * 0.045f)), r, Offset(cx, cy), style = Stroke(width = 5f + (ring % 3) * 2f))
        for (spark in 0 until 8) {
          val a = (phase + spark * 45f) * PI.toFloat() / 180f
          drawCircle(Color.White.copy(alpha = env * 0.9f), 2.5f + (ring % 3), Offset(cx + cos(a) * r, cy + sin(a) * r))
        }
      }
      drawCircle(Brush.radialGradient(listOf(Color(0xFF8D7CFF).copy(alpha = env * 0.75f), Color(0xFF050515).copy(alpha = env), Color.Transparent), Offset(cx, cy), maxRadius * 0.62f), maxRadius * 0.62f, Offset(cx, cy))
    }
    "aurora" -> {
      for (b in 0 until 4) {
        val col = listOf(Color(0xFF00E5A0), Color(0xFF37E6FF), Color(0xFFB388FF), Color(0xFFFF4D8D))[b]
        val path = Path()
        path.moveTo(0f, h * 0.55f)
        var x = 0f
        while (x <= w) { path.lineTo(x, h * (0.22f + b * 0.06f) + sin(x / w * 6f + p * 5f + b) * h * 0.05f); x += 18f }
        path.lineTo(w, h * 0.7f); path.close()
        drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.55f * env), Color.Transparent), h * 0.15f, h * 0.7f))
      }
    }
    "coins" -> {
      for (i in 0 until 50) {
        val sp = 0.7f + frand(i, 2) * 0.9f
        val y = -50f + p * sp * (h + 100f) * 1.2f - frand(i, 3) * 240f
        val x = frand(i, 1) * w
        fxEmoji(paint, if (i % 5 == 0) "💰" else "🪙", x + sin(p * 8f + i) * 14f, y, 34f + frand(i, 4) * 26f, env)
      }
    }
    "disco" -> {
      drawRect(Color(0xFF07020F).copy(alpha = 0.55f * env))
      for (i in 0 until 8) {
        val a = (i * 45f + p * 220f) * (PI.toFloat() / 180f)
        val col = FX_PAL[i % FX_PAL.size]
        drawLine(col.copy(alpha = 0.35f * env), Offset(w / 2f, 0f), Offset(w / 2f + cos(a) * w * 1.2f, h * 0.9f + sin(a) * h * 0.4f), 46f)
      }
      fxEmoji(paint, "🪩", w / 2f - 60f, h * 0.18f, 130f, env)
    }
    "dragon" -> {
      drawRect(Color(0xFF200504).copy(alpha = 0.4f * env))
      val x = -200f + (w + 400f) * easeOutF(min(1f, p * 1.2f))
      val y = h * 0.28f + sin(p * 9f) * 40f
      fxEmoji(paint, "🐉", x, y, 170f, env)
      for (i in 0 until 14) fxEmoji(paint, "🔥", x + 130f + i * 46f, y - 20f + sin(p * 14f + i) * 20f, 54f - i * 2.5f, env * (1f - i / 16f))
    }
    "royal" -> {
      val cx = w / 2f; val cy = h * 0.34f
      for (i in 0 until 12) {
        val a = (i * 30f + p * 40f) * (PI.toFloat() / 180f)
        drawLine(Color(0xFFFFE08A).copy(alpha = 0.3f * env), Offset(cx, cy), Offset(cx + cos(a) * w, cy + sin(a) * w), 6f)
      }
      drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE08A).copy(alpha = 0.55f * env), Color.Transparent), Offset(cx, cy), w * 0.5f), w * 0.5f, Offset(cx, cy))
      for (r in 0 until 3) {
        val q = ((p - r * 0.12f) / 0.5f)
        if (q in 0f..1f) drawCircle(Color(0xFFFFC857).copy(alpha = (1f - q) * env), w * 0.5f * easeOutF(q), Offset(cx, cy), style = Stroke(width = 5f))
      }
      for (i in 0 until 50) {
        val tw = 0.5f + 0.5f * sin(p * 16f + i * 2.1f)
        fxSparkle(Offset(frand(i, 1) * w, frand(i, 2) * h), 8f + 14f * tw, Color(0xFFFFE08A).copy(alpha = env * tw))
      }
      val drop = easeOutF(min(1f, p / 0.3f))
      val bounce = if (p > 0.3f) 0.04f * sin((p - 0.3f) * 30f) * (1f - p) else 0f
      fxEmoji(paint, "👑", cx - 80f, (-150f + (cy + 40f) * drop) + bounce * h, 160f, env)
    }
    else -> drawNewFx(kind, p, paint)
  }
}

// ═══════════════ 30 efectos de entrada nuevos ═══════════════

/** Modos de movimiento de las partículas: 0 caer, 1 subir, 2 estallar, 3 espiral, 4 cruzar, 5 titilar, 6 acercarse, 7 rebotar. */
private class FxSpec(val emojis: List<String>, val mode: Int, val tint: Color?, val count: Int, val minS: Float, val maxS: Float, val glow: Color? = null)

private val FX_SPECS: Map<String, FxSpec> = mapOf(
  "tornado" to FxSpec(listOf("🌪️", "💨", "🍃"), 3, Color(0xFF263238), 42, 28f, 60f),
  "butterflies" to FxSpec(listOf("🦋"), 4, null, 24, 30f, 62f, Color(0xFF4FC3F7)),
  "fireflies" to FxSpec(emptyList(), 5, Color(0xFF031A12), 46, 10f, 22f, Color(0xFFD4FF5A)),
  "leaves" to FxSpec(listOf("🍂", "🍁", "🍃"), 0, Color(0xFF3A1A05), 46, 26f, 52f),
  "petals" to FxSpec(listOf("🌹", "🌸", "🌺"), 0, Color(0xFF3A0B1A), 44, 24f, 50f),
  "pizza" to FxSpec(listOf("🍕", "🧀", "🍅"), 0, null, 36, 34f, 66f),
  "ghosts" to FxSpec(listOf("👻", "🎃", "🦇"), 1, Color(0xFF14082E), 30, 36f, 80f),
  "skulls" to FxSpec(listOf("☠️", "🏴‍☠️", "💰", "⚓"), 7, Color(0xFF0B1620), 26, 34f, 66f),
  "rockets" to FxSpec(listOf("🚀", "🔥", "⭐"), 1, Color(0xFF05020F), 26, 36f, 84f),
  "ufo" to FxSpec(listOf("🛸", "👽", "✨"), 4, Color(0xFF02120E), 18, 40f, 90f, Color(0xFF69F0AE)),
  "money" to FxSpec(listOf("💸", "💵", "🤑"), 0, null, 46, 30f, 60f),
  "diamonds" to FxSpec(listOf("💎", "✨"), 5, Color(0xFF031A33), 34, 28f, 70f, Color(0xFF7DF9FF)),
  "crowns" to FxSpec(listOf("👑", "✨"), 6, Color(0xFF2A1A00), 20, 40f, 130f, Color(0xFFFFD166)),
  "music" to FxSpec(listOf("🎶", "🎵", "🎼", "🎸"), 1, Color(0xFF14082E), 34, 30f, 64f),
  "soccer" to FxSpec(listOf("⚽", "🥅", "🏆"), 7, Color(0xFF052A14), 22, 34f, 70f),
  "emoji" to FxSpec(listOf("😎", "😂", "🥳", "😍", "🤩", "🔥"), 2, null, 36, 34f, 70f),
  "balloons" to FxSpec(listOf("🎈", "🎉"), 1, null, 30, 40f, 86f),
  "cherry" to FxSpec(listOf("🍒", "🍓"), 0, null, 40, 28f, 56f),
  "snowfall" to FxSpec(listOf("❄️", "🌨️"), 0, Color(0xFF0B1E3D), 50, 22f, 46f, Color(0xFFB3E5FF))
)

private fun DrawScope.drawNewFx(kind: String, p: Float, paint: android.graphics.Paint) {
  val w = size.width; val h = size.height
  val env = if (p < 0.08f) p / 0.08f else if (p > 0.86f) (1f - p) / 0.14f else 1f
  when (kind) {
    "rainbow" -> {
      val cols = listOf(Color(0xFFFF3B3B), Color(0xFFFF9500), Color(0xFFFFD60A), Color(0xFF34C759), Color(0xFF00B0FF), Color(0xFF5B2BD6), Color(0xFFB388FF))
      val sw = 180f * easeOutF(min(1f, p / 0.45f))
      for (b in cols.indices) {
        val r = w * 0.56f - b * 20f
        drawArc(cols[b].copy(alpha = 0.85f * env), 180f, sw, false, Offset(w / 2f - r, h * 0.55f - r), Size(2f * r, 2f * r), style = Stroke(width = 20f))
      }
      fxEmoji(paint, "☁️", w * 0.02f, h * 0.6f, 110f, env); fxEmoji(paint, "☁️", w * 0.78f, h * 0.6f, 110f, env)
      for (i in 0 until 26) {
        val tw = 0.5f + 0.5f * sin(p * 14f + i * 1.9f)
        fxSparkle(Offset(frand(i, 1) * w, frand(i, 2) * h * 0.6f), 6f + 12f * tw, Color.White.copy(alpha = env * tw))
      }
    }
    "tsunami" -> {
      val lvl = sin(min(1f, p / 0.92f) * PI.toFloat()).coerceIn(0f, 1f)
      val top = h * (1.02f - 0.82f * lvl)
      val path = Path(); path.moveTo(0f, h)
      var x = 0f
      while (x <= w + 16f) { path.lineTo(x, top + sin(x / w * 9f + p * 18f) * 16f + sin(x / w * 4f - p * 9f) * 10f); x += 16f }
      path.lineTo(w, h); path.close()
      drawPath(path, Brush.verticalGradient(listOf(Color(0xCC4FC3F7).copy(alpha = 0.8f * env), Color(0xE60B3A8A).copy(alpha = env)), top, h))
      for (i in 0 until 16) {
        val xx = frand(i, 1) * w
        fxEmoji(paint, if (i % 4 == 0) "🐬" else "🌊", xx, top + sin(xx / w * 9f + p * 18f) * 16f + 20f, 44f + frand(i, 2) * 34f, env)
      }
      for (i in 0 until 30) drawCircle(Color.White.copy(alpha = 0.7f * env), 2f + 4f * frand(i, 3), Offset(frand(i, 4) * w, top - frand(i, 5) * 80f * lvl))
    }
    "sunrise" -> {
      drawRect(Brush.verticalGradient(listOf(Color(0xFF3A1C71).copy(alpha = 0.55f * env), Color(0xFFFF6B9D).copy(alpha = 0.55f * env), Color(0xFFFFB347).copy(alpha = 0.6f * env)), 0f, h))
      val sc = Offset(w / 2f, h * (0.98f - 0.55f * easeOutF(min(1f, p / 0.75f))))
      for (i in 0 until 14) {
        val a = (i * 25.7f + p * 50f) * (PI.toFloat() / 180f)
        drawLine(Color(0xFFFFE08A).copy(alpha = 0.35f * env), sc, Offset(sc.x + cos(a) * w, sc.y + sin(a) * w), 10f)
      }
      drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF59D).copy(alpha = env), Color(0xFFFF7043).copy(alpha = env), Color.Transparent), sc, w * 0.32f), w * 0.32f, sc)
      drawCircle(Color(0xFFFFF3C4).copy(alpha = env), w * 0.13f, sc)
      for (i in 0 until 6) fxEmoji(paint, "🕊️", frand(i, 1) * w, h * (0.15f + 0.3f * frand(i, 2)) + sin(p * 8f + i) * 12f, 40f, env)
    }
    "glitch" -> {
      val step = floor(p * 36f).toInt()
      drawRect(Color(0xFF05020F).copy(alpha = 0.45f * env))
      for (k in 0 until 16) {
        val y = frand(step + k, 1) * h; val bh = 6f + frand(step + k, 2) * 40f
        val off = (frand(step + k, 3) - 0.5f) * w * 0.4f
        drawRect(Color(0xFF00E5FF).copy(alpha = 0.45f * env), Offset(off, y), Size(w * (0.3f + 0.7f * frand(step + k, 4)), bh))
        drawRect(Color(0xFFFF2E93).copy(alpha = 0.40f * env), Offset(-off * 0.6f + 8f, y + 4f), Size(w * (0.2f + 0.6f * frand(step + k, 5)), bh * 0.8f))
      }
      var yy = 0f
      while (yy < h) { drawLine(Color(0x1500FF99), Offset(0f, yy), Offset(w, yy), 1f); yy += 6f }
      for (i in 0 until 8) fxEmoji(paint, "👾", frand(step + i, 6) * w, frand(step + i, 7) * h, 44f + frand(i, 8) * 40f, env * (if (frand(step, i) > 0.4f) 1f else 0.2f))
    }
    "neon" -> {
      drawRect(Color(0xFF07020F).copy(alpha = 0.6f * env))
      val cols = listOf(Color(0xFF00E5FF), Color(0xFFFF2E93), Color(0xFFB388FF), Color(0xFF7CFF6B))
      for (r in 0 until 9) {
        val q = ((p * 1.6f + r * 0.11f) % 1f)
        drawCircle(cols[r % 4].copy(alpha = (1f - q) * env), w * 0.7f * q, Offset(w / 2f, h * 0.42f), style = Stroke(width = 4f + 7f * (1f - q)))
      }
      for (i in 0 until 10) {
        val x = w * (i + 0.5f) / 10f
        val len = h * (0.2f + 0.5f * abs(sin(p * 9f + i)))
        drawLine(cols[i % 4].copy(alpha = 0.55f * env), Offset(x, h), Offset(x, h - len), 7f)
      }
    }
    "storm" -> {
      drawRect(Color(0xFF0B1020).copy(alpha = 0.6f * env))
      for (i in 0 until 90) {
        val prog = (p * (1.6f + frand(i, 2)) + frand(i, 3)) % 1f
        val x = frand(i, 1) * w; val y = prog * (h + 60f) - 30f
        drawLine(Color(0xFF9FB8FF).copy(alpha = 0.7f * env), Offset(x, y), Offset(x - 10f, y + 38f), 2.5f)
      }
      val flashes = listOf(0.16f, 0.42f, 0.7f)
      for ((idx, t) in flashes.withIndex()) {
        val f = (1f - abs(p - t) / 0.04f).coerceAtLeast(0f)
        if (f > 0f) {
          drawRect(Color.White.copy(alpha = f * 0.6f))
          var x = w * (0.2f + 0.6f * frand(idx, 3)); var y = 0f
          val path = Path(); path.moveTo(x, y)
          for (s in 0 until 9) { x += (frand(s, idx + 21) - 0.5f) * 110f; y += h / 8f; path.lineTo(x, y) }
          drawPath(path, Color(0xFFBFD4FF).copy(alpha = f), style = Stroke(width = 18f)); drawPath(path, Color.White.copy(alpha = f), style = Stroke(width = 6f))
        }
      }
      fxEmoji(paint, "⛈️", w * 0.5f - 90f, h * 0.18f, 180f, env)
    }
    "volcano" -> {
      drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF4A00).copy(alpha = 0.75f * env), Color(0xFFB3120A).copy(alpha = env)), h * 0.4f, h), Offset(0f, h * 0.4f), Size(w, h * 0.6f))
      fxEmoji(paint, "🌋", w / 2f - 120f, h * 0.98f, 240f, env)
      for (i in 0 until 50) {
        val prog = (p * (1.2f + frand(i, 2)) + frand(i, 3)) % 1f
        val x = w / 2f + (frand(i, 1) - 0.5f) * w * (0.25f + 0.7f * prog)
        val y = h * 0.82f - sin(prog * PI.toFloat()) * h * (0.35f + 0.4f * frand(i, 4)) + prog * prog * h * 0.2f
        drawCircle(Color(0xFFFFB347).copy(alpha = (1f - prog * 0.6f) * env), 3f + 5f * frand(i, 5), Offset(x, y))
        if (i % 6 == 0) fxEmoji(paint, "🔥", x, y, 40f, env * (1f - prog * 0.5f))
      }
    }
    "magic" -> {
      drawRect(Color(0xFF14082E).copy(alpha = 0.5f * env))
      val cx = w / 2f; val cy = h * 0.42f
      for (i in 0 until 60) {
        val a = i * 0.45f + p * 7f
        val r = (i / 60f) * w * 0.55f * easeOutF(min(1f, p * 2f))
        val tw = 0.5f + 0.5f * sin(p * 18f + i)
        fxSparkle(Offset(cx + cos(a) * r, cy + sin(a) * r * 0.8f), 5f + 13f * tw, (if (i % 2 == 0) Color(0xFFFFE066) else Color(0xFFB388FF)).copy(alpha = env * tw))
      }
      val sw = sin(p * 10f) * 0.5f
      fxEmoji(paint, "🪄", cx - 90f + sw * 120f, cy + 60f + abs(sw) * 40f, 170f, env)
      fxEmoji(paint, "🧚", w * 0.12f + p * w * 0.5f, h * 0.2f + sin(p * 9f) * 40f, 80f, env)
    }
    "legend" -> {
      drawFx("royal", p, paint)
      drawFx("coins", p, paint)
      drawFx("fire", p, paint)
      val f = (1f - abs(p - 0.3f) / 0.04f).coerceAtLeast(0f)
      if (f > 0f) drawRect(Color.White.copy(alpha = f * 0.5f))
      fxEmoji(paint, "🏆", w / 2f - 95f, h * 0.72f, 190f, env)
    }
    else -> {
      val sp = FX_SPECS[kind] ?: return
      if (sp.tint != null) drawRect(sp.tint.copy(alpha = 0.42f * env))
      if (sp.glow != null) drawCircle(Brush.radialGradient(listOf(sp.glow.copy(alpha = 0.32f * env), Color.Transparent), Offset(w / 2f, h * 0.45f), w * 0.8f), w * 0.8f, Offset(w / 2f, h * 0.45f))
      for (i in 0 until sp.count) {
        val spd = 0.6f + frand(i, 2) * 0.8f
        val s0 = sp.minS + (sp.maxS - sp.minS) * frand(i, 4)
        var x: Float; var y: Float; var sz = s0; var a = env
        when (sp.mode) {
          0 -> { x = frand(i, 1) * w + sin(p * 8f + i) * 26f; y = -40f + p * spd * 1.25f * (h + 80f) - frand(i, 3) * 220f }
          1 -> { x = frand(i, 1) * w + sin(p * 6f + i) * 22f; y = h * 1.1f - p * spd * h * 1.4f - frand(i, 3) * h * 0.25f }
          2 -> { val ang = frand(i, 1) * 2f * PI.toFloat(); val d = easeOutF(min(1f, p * 1.4f)) * w * (0.25f + 0.6f * frand(i, 2)); x = w / 2f + cos(ang) * d; y = h * 0.42f + sin(ang) * d; a = env * (1f - p * 0.5f) }
          3 -> { val t = i / sp.count.toFloat(); val ang = t * 6f * PI.toFloat() + p * 11f; val r = t * w * 0.6f * easeOutF(min(1f, p * 1.6f)); x = w / 2f + cos(ang) * r; y = h * 0.5f + sin(ang) * r * 0.7f; sz = s0 * (0.5f + t) }
          4 -> { x = -70f + ((p * spd + frand(i, 3)) % 1f) * (w + 140f); y = h * (0.08f + 0.8f * frand(i, 1)) + sin(p * 10f + i) * h * 0.04f }
          5 -> { x = frand(i, 1) * w + sin(p * 3f + i) * 14f; y = frand(i, 2) * h + cos(p * 3f + i) * 14f; val life = (p * 2.2f + frand(i, 3)) % 1f; val pop = sin(life * PI.toFloat()); sz = s0 * (0.4f + 0.8f * pop); a = env * pop }
          6 -> { val z = (p * 1.3f + i / sp.count.toFloat()) % 1f; val ang = frand(i, 1) * 2f * PI.toFloat(); x = w / 2f + cos(ang) * z * w * 0.55f; y = h * 0.45f + sin(ang) * z * h * 0.4f; sz = s0 * (0.3f + z * 1.4f); a = env * sin(z * PI.toFloat()) }
          else -> { x = frand(i, 1) * w; y = h * 0.92f - abs(sin((p * spd * 2.6f + frand(i, 3)) * PI.toFloat())) * h * 0.55f * (1f - p * 0.35f) }
        }
        if (sp.emojis.isEmpty()) {
          val col = sp.glow ?: Color.White
          drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.7f * a), Color.Transparent), Offset(x, y), sz * 1.6f), sz * 1.6f, Offset(x, y))
          drawCircle(Color.White.copy(alpha = a), sz * 0.16f, Offset(x, y))
        } else fxEmoji(paint, sp.emojis[i % sp.emojis.size], x, y, sz, a)
      }
    }
  }
}

// ═══════════════ Capa de efectos sobre toda la sala ═══════════════

@Composable
fun RoomFxLayer(social: RoomSocial, vm: CoupleViewModel) {
  val ev = social.current ?: return
  val prog = remember(ev.key) { Animatable(0f) }
  val dur = if (ev.kind == "gift") 4200 else 3400
  val paint = remember {
    android.graphics.Paint().apply { isAntiAlias = true }
  }
  val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
  LaunchedEffect(ev.key) {
    // Regalos: solo suena una campanilla muy suave y únicamente si el regalo es para mí (o para todos).
    if (ev.kind == "gift") { if (ev.forMe) com.example.audio.SoftChime.play(appCtx) } else vm.gameSfx(1)
    prog.snapTo(0f)
    prog.animateTo(1f, tween(dur, easing = LinearEasing))
    social.next()
  }
  val p = prog.value
  val env = if (p < 0.1f) p / 0.1f else if (p > 0.88f) (1f - p) / 0.12f else 1f
  val gift = GIFTS.firstOrNull { it.id == ev.giftId }
  Box(Modifier.fillMaxSize()) {
    if (gift != null) Box(Modifier.fillMaxSize().background(Color(0x99070311).copy(alpha = 0.6f * env)))
    Canvas(Modifier.fillMaxSize()) { drawFx(if (gift != null) "confetti" else ev.kind, p, paint) }
    if (gift != null) {
      val sc = remember(ev.key) { Animatable(0.1f) }
      LaunchedEffect(ev.key) { sc.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
      Column(Modifier.align(Alignment.Center).graphicsLayer { alpha = env }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ev.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        Text(ev.sub, color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value }) { GiftArt(gift, 210.dp) }
        Spacer(Modifier.height(8.dp))
        Text(gift.note, color = Color(0xFFE2D8F5), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
      }
    } else {
      val slide = easeOutF(min(1f, p / 0.2f))
      Box(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp)
          .graphicsLayer { alpha = env; translationY = (1f - slide) * -120f; scaleX = 0.8f + 0.2f * slide; scaleY = 0.8f + 0.2f * slide }
          .clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFF5B2BD6), Color(0xFFFF4D8D), Color(0xFFFFB347))))
          .padding(horizontal = 22.dp, vertical = 10.dp)
      ) { Text(ev.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, textAlign = TextAlign.Center) }
    }
  }
}
