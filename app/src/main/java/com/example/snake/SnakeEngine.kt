package com.example.snake

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Motor de «Viboritas Neón» v2: víboras de verdad.
 * Movimiento continuo (no por casillas): cada víbora avanza en la dirección en que apunta su cabeza, gira con una velocidad limitada
 * (por eso hace curvas amplias y suaves) y su cuerpo sigue exactamente el camino que recorrió la cabeza, como una serpiente.
 * Simulación a 30 pasos por segundo; el dibujo interpola entre pasos.
 * Unidades: 1 unidad = el grosor de una víbora.
 */

internal const val SN_DT = 1f / 30f
private const val STEP = 0.15f            // separación entre los puntos del rastro que deja la cabeza
internal const val SN_SP = 0.45f          // separación entre segmentos del cuerpo
private const val TWO_PI = (2.0 * PI).toFloat()

internal enum class SMode(val id: String, val title: String, val emoji: String, val desc: String, val multi: Boolean, val w: Float, val h: Float, val walls: Boolean) {
  CLASSIC("classic", "Clásico", "🐍", "Come, crece y no choques. Cada manzana te hace más rápida", false, 22f, 32f, true),
  PORTAL("portal", "Sin paredes", "🌀", "Las paredes son portales: sales por un lado y apareces por el otro", true, 40f, 40f, false),
  ARENA("arena", "Todos contra todos", "⚔️", "Quien muere deja comida. Gana la más larga cuando se acaban los 3 minutos", true, 56f, 56f, true),
  SURVIVAL("survival", "Supervivencia", "☠️", "La zona se achica y el que queda fuera muere. Gana la última viva", true, 56f, 56f, true),
  TEAMS("teams", "Equipos", "🤝", "Rojos contra azules: las manzanas suman para tu equipo (a tus compañeras no las chocas)", true, 56f, 56f, true),
  SHOOTER("shooter", "Shooter", "🔫", "Recoge munición y dispara. Un tiro al cuerpo corta la víbora; a la cabeza, la elimina", true, 56f, 56f, true),
  RUSH("rush", "Carrera de manzanas", "🍎", "Quien coma 15 manzanas primero gana. Hay 4 manzanas a la vez", true, 40f, 40f, true);
  companion object { fun byId(id: String) = values().firstOrNull { it.id == id } ?: CLASSIC }
}

internal val SN_COLORS = listOf(
  0xFF00E676.toInt(), 0xFF00B0FF.toInt(), 0xFFFF4081.toInt(), 0xFFFFEA00.toInt(),
  0xFFB388FF.toInt(), 0xFFFF9100.toInt(), 0xFF1DE9B6.toInt(), 0xFFFF5252.toInt()
)
internal val SN_NAMES = listOf("Venenita", "Cobrita", "Mamba", "Anaconda", "Pitón", "Víbora", "Rayo", "Sombra", "Escama", "Nagini", "Colmillo", "Jade")

/** Rastro de la cabeza: puntos (x, y) a distancia fija; el índice 0 es el más reciente. */
internal class Trail {
  var a = FloatArray(256)
  var n = 0
  fun x(i: Int) = a[i * 2]
  fun y(i: Int) = a[i * 2 + 1]
  fun clear() { n = 0 }
  fun addFront(x: Float, y: Float) {
    if ((n + 1) * 2 > a.size) a = a.copyOf(a.size * 2)
    System.arraycopy(a, 0, a, 2, n * 2)
    a[0] = x; a[1] = y; n++
  }
  fun addEnd(x: Float, y: Float) {
    if ((n + 1) * 2 > a.size) a = a.copyOf(a.size * 2)
    a[n * 2] = x; a[n * 2 + 1] = y; n++
  }
  fun trim(maxPts: Int) { if (n > maxPts) n = maxPts }
}

internal class SSnake(val id: Int, var name: String, var color: Int, val bot: Boolean) {
  var uid = ""
  var x = 0f; var y = 0f                 // cabeza (continua; en «Sin paredes» puede salirse del tablero: se «envuelve» al dibujar)
  var ang = 0f; var target = 0f          // rumbo actual y rumbo al que quiere girar
  val trail = Trail()
  var cur = FloatArray(0)                // cuerpo ahora: x0, y0, x1, y1… (el 0 es la cabeza)
  var prev = FloatArray(0)               // cuerpo en el paso anterior (para interpolar el dibujo)
  var segs = 10
  var alive = false
  var score = 0; var ammo = 0; var kills = 0; var apples = 0; var deaths = 0
  var team = 0
  var respawnAt = 0
  var wantFire = false
  var boosting = false                   // el jugador mantiene pulsado el turbo
  var fast = false                       // va realmente a turbo
  var boostAcc = 0f
  var thinkSkip = 0
}

internal class SPick(var kind: Int, var x: Float, var y: Float)        // 0 manzana, 1 munición, 2 manzana dorada
internal class SBullet(var x: Float, var y: Float, val ang: Float, val owner: Int, var life: Float)
internal class SEvent(val kind: Int, val x: Float, val y: Float, val color: Int)   // 0 comer, 1 muerte, 2 disparo, 3 impacto, 4 corte

internal fun angDiff(to: Float, from: Float): Float {
  var d = (to - from) % TWO_PI
  if (d > PI.toFloat()) d -= TWO_PI
  if (d < -PI.toFloat()) d += TWO_PI
  return d
}

internal class SnakeWorld(val mode: SMode, val bots: Int = 0, val rnd: Random = Random.Default, val mirror: Boolean = false) {
  val W = mode.w; val H = mode.h
  val snakes = ArrayList<SSnake>()
  val pickups = ArrayList<SPick>()
  val bullets = ArrayList<SBullet>()
  val events = ArrayList<SEvent>()
  var tick = 0
  var duration = 5400                    // 3 minutos a 30 pasos por segundo
  var zone = floatArrayOf(0f, 0f, W, H)
  var over = false
  var winner = ""
  var winnerId = -1
  var apples = 0                         // total de manzanas de la partida (para la velocidad del clásico)
  private val tmp = FloatArray(2)
  val timed: Boolean get() = mode != SMode.CLASSIC && mode != SMode.RUSH && mode != SMode.SURVIVAL

  fun tickMs(): Long = 33L

  init {
    if (!mirror) for (i in 0 until bots) { val s = addSnake(SN_NAMES[i % SN_NAMES.size], SN_COLORS[(i + 1) % SN_COLORS.size], true, ""); spawn(s) }
  }

  fun addSnake(name: String, color: Int, bot: Boolean, uid: String): SSnake {
    val s = SSnake(snakes.size, name, color, bot); s.uid = uid; s.team = snakes.size % 2; snakes.add(s)
    if (mode == SMode.TEAMS) s.color = if (s.team == 0) 0xFFFF5252.toInt() else 0xFF40C4FF.toInt()
    return s
  }

  // ───────────── geometría (toroidal si no hay paredes) ─────────────
  fun wv(v: Float, m: Float): Float { var r = v % m; if (r < 0f) r += m; return r }
  private fun td(a: Float, b: Float, m: Float): Float {
    var d = a - b
    if (!mode.walls) d = wv(d + m / 2f, m) - m / 2f
    return d
  }
  private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float { val dx = td(ax, bx, W); val dy = td(ay, by, H); return dx * dx + dy * dy }
  private fun inZone(x: Float, y: Float) = mode != SMode.SURVIVAL || (x >= zone[0] && x <= zone[2] && y >= zone[1] && y <= zone[3])

  fun thick(s: SSnake) = 1f + min(0.3f, s.segs / 300f)
  private fun rad(s: SSnake) = 0.40f * thick(s)
  private fun baseSpeed(@Suppress("UNUSED_PARAMETER") s: SSnake): Float = if (mode == SMode.CLASSIC) 4.6f + min(2.6f, apples * 0.09f) else 5.2f

  // ───────────── cuerpo a partir del rastro ─────────────
  private fun pointAt(s: SSnake, dist: Float, out: FloatArray) {
    val tr = s.trail
    if (tr.n == 0) { out[0] = s.x; out[1] = s.y; return }
    val dx = s.x - tr.x(0); val dy = s.y - tr.y(0)
    val d0 = sqrt(dx * dx + dy * dy)
    if (dist <= d0 && d0 > 1e-5f) { val f = dist / d0; out[0] = s.x - dx * f; out[1] = s.y - dy * f; return }
    val d = dist - d0
    val idx = (d / STEP).toInt()
    if (idx >= tr.n - 1) { out[0] = tr.x(tr.n - 1); out[1] = tr.y(tr.n - 1); return }
    val f = (d - idx * STEP) / STEP
    out[0] = tr.x(idx) + (tr.x(idx + 1) - tr.x(idx)) * f
    out[1] = tr.y(idx) + (tr.y(idx + 1) - tr.y(idx)) * f
  }

  private fun rebuildBody(s: SSnake) {
    val n = max(2, s.segs)
    val old = s.cur
    val nc = FloatArray(n * 2)
    nc[0] = s.x; nc[1] = s.y
    for (i in 1 until n) { pointAt(s, i * SN_SP, tmp); nc[i * 2] = tmp[0]; nc[i * 2 + 1] = tmp[1] }
    s.cur = nc
    s.prev = if (old.isEmpty()) nc else old
  }

  fun spawn(s: SSnake) {
    var bx = W / 2f; var by = H / 2f; var best = -1f
    for (t in 0 until 30) {
      val x = 6f + rnd.nextFloat() * (W - 12f); val y = 6f + rnd.nextFloat() * (H - 12f)
      if (!inZone(x, y)) continue
      var near = 999f
      for (o in snakes) if (o.alive) { var i = 0; while (i < o.cur.size / 2) { near = min(near, dist2(o.cur[i * 2], o.cur[i * 2 + 1], x, y)); i += 3 } }
      if (near > best) { best = near; bx = x; by = y }
    }
    s.x = bx; s.y = by
    s.ang = atan2(H / 2f - by, W / 2f - bx) + (rnd.nextFloat() - 0.5f) * 0.8f
    s.target = s.ang
    s.segs = 10; s.alive = true; s.score = 0; s.ammo = if (mode == SMode.SHOOTER) 3 else 0
    s.wantFire = false; s.boosting = false; s.fast = false; s.boostAcc = 0f
    s.trail.clear()
    val n = ((s.segs * SN_SP) / STEP).toInt() + 6
    for (k in 1..n) s.trail.addEnd(bx - cos(s.ang) * k * STEP, by - sin(s.ang) * k * STEP)
    s.cur = FloatArray(0)
    rebuildBody(s)
  }

  /** Orden de rumbo: la cabeza gira hacia [angle] con su velocidad de giro limitada. */
  fun turnTo(s: SSnake, angle: Float) { if (s.alive) s.target = angle }

  // ───────────── comida ─────────────
  private fun foodTarget() = when (mode) {
    SMode.CLASSIC -> 1
    SMode.RUSH -> 4
    SMode.PORTAL -> 8 + snakes.count { it.alive }
    else -> 16 + snakes.count { it.alive }
  }

  private fun addPick(kind: Int) {
    for (t in 0 until 12) {
      val x = 1.5f + rnd.nextFloat() * (W - 3f); val y = 1.5f + rnd.nextFloat() * (H - 3f)
      if (!inZone(x, y)) continue
      var free = true
      for (s in snakes) if (s.alive && dist2(s.x, s.y, x, y) < 4f) { free = false; break }
      if (free) { pickups.add(SPick(kind, x, y)); return }
    }
  }

  private fun kill(s: SSnake, killer: SSnake?) {
    if (!s.alive) return
    s.alive = false; s.deaths++; s.fast = false
    events.add(SEvent(1, wv(s.x, W), wv(s.y, H), s.color))
    if (killer != null && killer !== s) { killer.kills++; killer.score += 5 }
    if (mode != SMode.CLASSIC && mode != SMode.RUSH) {
      var i = 0
      while (i < s.cur.size / 2) {
        if (i % 2 == 0 && pickups.size < 160) pickups.add(SPick(0, wv(s.cur[i * 2], W), wv(s.cur[i * 2 + 1], H)))
        i++
      }
    }
    s.respawnAt = if (mode == SMode.SURVIVAL || mode == SMode.CLASSIC || mode == SMode.RUSH) Int.MAX_VALUE else tick + 60
  }

  // ───────────── un paso de la simulación (anfitrión / solo) ─────────────
  fun step() {
    if (over) return
    tick++
    if (mode == SMode.SURVIVAL) {
      val inset = ((tick - 600) / 30f * 0.35f).coerceIn(0f, (W - 14f) / 2f)
      zone = floatArrayOf(inset, inset, W - inset, H - inset)
    }
    for (s in snakes) {
      if (!s.alive && tick >= s.respawnAt && (s.bot || s.uid.isNotEmpty() || mode == SMode.CLASSIC)) spawn(s)
      if (s.alive && s.bot) aiThink(s)
    }
    // disparos
    for (s in snakes) if (s.alive && s.wantFire) {
      s.wantFire = false
      if (mode == SMode.SHOOTER && s.ammo > 0) {
        s.ammo--
        bullets.add(SBullet(s.x + cos(s.ang) * 0.9f, s.y + sin(s.ang) * 0.9f, s.ang, s.id, 1.1f))
        events.add(SEvent(2, wv(s.x, W), wv(s.y, H), s.color))
      }
    }
    for (s in snakes) if (s.alive) moveSnake(s)
    stepBullets()
    // choques
    val dead = ArrayList<Pair<SSnake, SSnake?>>()
    for (s in snakes) {
      if (!s.alive) continue
      if (!inZone(wv(s.x, W), wv(s.y, H))) { dead.add(s to null); continue }
      val rs = rad(s)
      var hit: SSnake? = null
      loop@ for (o in snakes) {
        if (!o.alive) continue
        if (mode == SMode.TEAMS && o !== s && o.team == s.team) continue
        val r = rs + rad(o); val lim = r * r
        val c = o.cur; val n = c.size / 2
        var i = if (o === s) 7 else 0
        while (i < n) {
          if (dist2(s.x, s.y, c[i * 2], c[i * 2 + 1]) < lim) { hit = o; break@loop }
          i++
        }
      }
      val h = hit
      if (h != null) dead.add(s to (if (h === s) null else h))
    }
    for ((s, k) in dead) kill(s, k)
    // comida (con un pequeño imán para que se sienta bien)
    for (s in snakes) if (s.alive) {
      val reach = 0.8f + 0.4f * thick(s)
      val pit = pickups.iterator()
      while (pit.hasNext()) {
        val p = pit.next()
        val d2 = dist2(s.x, s.y, p.x, p.y)
        if (d2 < reach * reach) {
          when (p.kind) {
            0 -> { s.segs += 2; s.score += 1; s.apples++; apples++ }
            1 -> s.ammo = min(20, s.ammo + 5)
            else -> { s.segs += 6; s.score += 5; s.apples += 3; apples += 3 }
          }
          events.add(SEvent(0, p.x, p.y, if (p.kind == 1) 0xFFFFD740.toInt() else s.color))
          pit.remove()
        } else if (d2 < 3.4f * 3.4f) {
          val d = sqrt(d2); val pull = 6.5f * SN_DT
          p.x = wv(p.x - td(p.x, s.x, W) / d * pull, W); p.y = wv(p.y - td(p.y, s.y, H) / d * pull, H)
        }
      }
    }
    // reposición de comida y munición
    var guard = 0
    while (pickups.count { it.kind != 1 } < foodTarget() && guard++ < 3) addPick(if (mode != SMode.CLASSIC && rnd.nextInt(14) == 0) 2 else 0)
    if (mode == SMode.SHOOTER && pickups.count { it.kind == 1 } < 6 && rnd.nextInt(24) == 0) addPick(1)
    // reglas por modo
    when (mode) {
      SMode.SURVIVAL -> {
        val alive = snakes.filter { it.alive }
        if (snakes.size > 1 && alive.size <= 1) finish(alive.firstOrNull())
        else if (snakes.size == 1 && alive.isEmpty()) finish(null)
      }
      SMode.RUSH -> snakes.firstOrNull { it.apples >= 15 }?.let { finish(it) }
      SMode.CLASSIC -> if (snakes.none { it.alive }) finish(null)
      else -> if (tick >= duration) finishByScore()
    }
  }

  private fun moveSnake(s: SSnake) {
    val diff = angDiff(s.target, s.ang)
    val canBoost = mode != SMode.CLASSIC && s.segs > 9
    s.fast = s.boosting && canBoost
    val maxTurn = (4.4f - min(1.3f, s.segs * 0.012f)) * (if (s.fast) 0.8f else 1f) * SN_DT
    s.ang += diff.coerceIn(-maxTurn, maxTurn)
    var spd = baseSpeed(s)
    if (s.fast) {
      spd *= 1.75f
      s.boostAcc += SN_DT
      if (s.boostAcc >= 0.3f) {
        s.boostAcc = 0f; s.segs--
        val c = s.cur
        if (c.size >= 4 && pickups.size < 160) pickups.add(SPick(0, wv(c[c.size - 2], W), wv(c[c.size - 1], H)))
      }
    } else s.boostAcc = 0f
    s.x += cos(s.ang) * spd * SN_DT
    s.y += sin(s.ang) * spd * SN_DT
    if (mode.walls && (s.x < 0.35f || s.x > W - 0.35f || s.y < 0.35f || s.y > H - 0.35f)) { kill(s, null); return }
    // rastro: un punto cada STEP de recorrido
    val tr = s.trail
    while (true) {
      val lx = if (tr.n > 0) tr.x(0) else s.x - cos(s.ang) * STEP
      val ly = if (tr.n > 0) tr.y(0) else s.y - sin(s.ang) * STEP
      val dx = s.x - lx; val dy = s.y - ly
      val d = sqrt(dx * dx + dy * dy)
      if (d < STEP) break
      tr.addFront(lx + dx / d * STEP, ly + dy / d * STEP)
    }
    tr.trim(((s.segs * SN_SP) / STEP).toInt() + 6)
    rebuildBody(s)
  }

  private fun stepBullets() {
    val bi = bullets.iterator()
    while (bi.hasNext()) {
      val b = bi.next()
      var gone = false
      for (k in 0 until 2) {
        if (gone) break
        b.x += cos(b.ang) * 16f * SN_DT / 2f; b.y += sin(b.ang) * 16f * SN_DT / 2f
        if (!mode.walls) { b.x = wv(b.x, W); b.y = wv(b.y, H) }
        if (b.x < 0f || b.y < 0f || b.x > W || b.y > H) { gone = true; break }
        val owner = snakes.getOrNull(b.owner)
        for (t in snakes) {
          if (!t.alive) continue
          if (mode == SMode.TEAMS && owner != null && t.team == owner.team && t !== owner) continue
          val c = t.cur; val n = c.size / 2
          var idx = -1
          var i = if (t === owner) 8 else 0
          while (i < n) { if (dist2(b.x, b.y, c[i * 2], c[i * 2 + 1]) < 0.5f * 0.5f) { idx = i; break }; i++ }
          if (idx < 0) continue
          events.add(SEvent(3, b.x, b.y, t.color))
          if (idx <= 1) kill(t, owner)
          else {
            // el disparo corta el cuerpo: lo que queda atrás se vuelve comida
            var j = idx
            while (j < n) { if (j % 2 == 0 && pickups.size < 160) pickups.add(SPick(0, wv(c[j * 2], W), wv(c[j * 2 + 1], H))); j++ }
            t.segs = max(4, idx)
            t.trail.trim(((t.segs * SN_SP) / STEP).toInt() + 6)
            rebuildBody(t)
            t.score = max(0, t.score - 2); owner?.let { it.score += 2 }
            events.add(SEvent(4, b.x, b.y, t.color))
          }
          gone = true; break
        }
      }
      b.life -= SN_DT
      if (gone || b.life <= 0f) bi.remove()
    }
  }

  private fun finish(w: SSnake?) { over = true; winner = w?.name.orEmpty(); winnerId = w?.id ?: -1 }
  private fun finishByScore() {
    over = true
    if (mode == SMode.TEAMS) {
      val a = snakes.filter { it.team == 0 }.sumOf { it.score }; val b = snakes.filter { it.team == 1 }.sumOf { it.score }
      winner = if (a == b) "Empate" else if (a > b) "Equipo rojo" else "Equipo azul"; winnerId = if (a >= b) 0 else 1
    } else {
      val top = snakes.maxByOrNull { if (it.alive) it.segs + it.score else it.score }
      winner = top?.name.orEmpty(); winnerId = top?.id ?: -1
    }
  }

  // ───────────── bots ─────────────
  /** Cuánto peligro hay en el punto (px, py) para la víbora [s]: paredes, zona y cuerpos. */
  private fun hazard(s: SSnake, px: Float, py: Float): Float {
    var h = 0f
    if (mode.walls && (px < 1.2f || px > W - 1.2f || py < 1.2f || py > H - 1.2f)) h += 4f
    if (!inZone(wv(px, W), wv(py, H))) h += 4f
    for (o in snakes) {
      if (!o.alive) continue
      if (mode == SMode.TEAMS && o !== s && o.team == s.team) continue
      val c = o.cur; val n = c.size / 2
      var i = if (o === s) 4 else 0
      while (i < n) {
        if (dist2(px, py, c[i * 2], c[i * 2 + 1]) < 1.15f) { h += if (i == 0) 3f else 1.5f; if (h > 8f) return h }
        i++
      }
    }
    return h
  }

  private fun aiThink(s: SSnake) {
    if (s.thinkSkip-- > 0) return
    s.thinkSkip = 2
    var target: SPick? = null; var td0 = 1e9f
    for (p in pickups) {
      if (p.kind == 1 && (mode != SMode.SHOOTER || s.ammo >= 6)) continue
      val d = dist2(s.x, s.y, p.x, p.y) - (if (p.kind == 2) 20f else 0f)
      if (d < td0) { td0 = d; target = p }
    }
    val tg = target
    val tAng = if (tg != null) atan2(td(tg.y, s.y, H), td(tg.x, s.x, W)) else s.ang
    var bestA = s.ang; var bestScore = Float.MAX_VALUE
    for (k in -3..3) {
      val a = s.ang + k * 0.45f
      var danger = 0f
      for (dd in floatArrayOf(1.4f, 2.8f, 4.6f)) danger += hazard(s, s.x + cos(a) * dd, s.y + sin(a) * dd) * (1.6f - dd * 0.12f)
      val score = danger * 10f + abs(angDiff(a, tAng)) * 2f + abs(k) * 0.12f + rnd.nextFloat() * 0.4f
      if (score < bestScore) { bestScore = score; bestA = a }
    }
    s.target = bestA
    // shooter: dispara si hay una víbora rival delante
    if (mode == SMode.SHOOTER && s.ammo > 0 && rnd.nextInt(4) == 0) {
      var d = 1.5f
      while (d < 12f) {
        val px = s.x + cos(s.ang) * d; val py = s.y + sin(s.ang) * d
        var found = false
        for (o in snakes) {
          if (o === s || !o.alive || (mode == SMode.TEAMS && o.team == s.team)) continue
          val c = o.cur
          var i = 0
          while (i < c.size / 2) { if (dist2(px, py, c[i * 2], c[i * 2 + 1]) < 0.6f) { found = true; break }; i += 2 }
          if (found) break
        }
        if (found) { s.wantFire = true; break }
        d += 0.8f
      }
    }
  }

  // ───────────── red ─────────────
  private fun clean(s: String) = s.replace('|', ' ').replace(',', ' ').replace(';', ' ').take(14)

  /** Instantánea: cabeza en cuadrícula de 1/40 y el cuerpo como desplazamientos relativos (2 caracteres por segmento). */
  fun encode(): Map<String, Any?> {
    val sn = snakes.map { s ->
      val sb = StringBuilder()
      var hx = 0; var hy = 0
      if (s.alive && s.cur.size >= 4) {
        val c = s.cur
        hx = (wv(c[0], W) * 40f).roundToInt(); hy = (wv(c[1], H) * 40f).roundToInt()
        var px = 0; var py = 0
        for (i in 1 until c.size / 2) {
          val gx = ((c[i * 2] - c[0]) * 40f).roundToInt(); val gy = ((c[i * 2 + 1] - c[1]) * 40f).roundToInt()
          val dx = (gx - px).coerceIn(-20, 20); val dy = (gy - py).coerceIn(-20, 20)
          sb.append((85 + dx).toChar()).append((85 + dy).toChar())
          px += dx; py += dy
        }
      }
      "${s.id}|${clean(s.name)}|${s.color}|${s.team}|${if (s.alive) 1 else 0}|${s.score}|${s.ammo}|${s.kills}|${s.apples}|${(s.ang * 100f).roundToInt()}|${s.uid}|${s.segs}|${if (s.fast) 1 else 0}|${if (s.bot) 1 else 0}|$hx|$hy|$sb"
    }
    val pk = ArrayList<Long>(); for (p in pickups) { pk.add(p.kind.toLong()); pk.add((p.x * 100f).roundToInt().toLong()); pk.add((p.y * 100f).roundToInt().toLong()) }
    val bu = ArrayList<Long>(); for (b in bullets) { bu.add((b.x * 100f).roundToInt().toLong()); bu.add((b.y * 100f).roundToInt().toLong()); bu.add((b.ang * 100f).roundToInt().toLong()); bu.add(b.owner.toLong()) }
    return mapOf("mode" to mode.id, "tk" to tick.toLong(), "dur" to duration.toLong(), "z" to zone.map { (it * 100f).roundToInt().toLong() }, "ov" to over, "wn" to winner, "wi" to winnerId.toLong(), "ap" to apples.toLong(), "s" to sn, "p" to pk, "b" to bu)
  }

  private fun n(a: Any?): Int = (a as? Number)?.toInt() ?: 0

  /** Aplica una instantánea (modo espejo). */
  fun applySnapshot(m: Map<String, Any?>) {
    tick = n(m["tk"]); duration = n(m["dur"]); over = m["ov"] == true; winner = (m["wn"] as? String).orEmpty(); winnerId = n(m["wi"]); apples = n(m["ap"])
    (m["z"] as? List<*>)?.let { z -> if (z.size == 4) zone = FloatArray(4) { n(z[it]) / 100f } }
    val list = (m["s"] as? List<*>) ?: emptyList<Any?>()
    for (line in list) {
      val f = (line as? String)?.split("|") ?: continue
      if (f.size < 17) continue
      val id = f[0].toIntOrNull() ?: continue
      while (snakes.size <= id) snakes.add(SSnake(snakes.size, "?", 0, true))
      val s = snakes[id]
      val wasAlive = s.alive
      val ohx = s.x; val ohy = s.y
      s.name = f[1]; s.color = f[2].toIntOrNull() ?: 0; s.team = f[3].toIntOrNull() ?: 0; s.alive = f[4] == "1"; s.score = f[5].toIntOrNull() ?: 0
      s.ammo = f[6].toIntOrNull() ?: 0; s.kills = f[7].toIntOrNull() ?: 0; s.apples = f[8].toIntOrNull() ?: 0; s.ang = (f[9].toIntOrNull() ?: 0) / 100f; s.uid = f[10]
      s.segs = f[11].toIntOrNull() ?: 10; s.fast = f[12] == "1"
      if (wasAlive && !s.alive) events.add(SEvent(1, ohx, ohy, s.color))
      val hx = (f[14].toIntOrNull() ?: 0) / 40f; val hy = (f[15].toIntOrNull() ?: 0) / 40f
      val chars = f[16]
      val old = s.cur
      if (s.alive) {
        val cnt = chars.length / 2 + 1
        val nc = FloatArray(cnt * 2)
        nc[0] = hx; nc[1] = hy
        var gx = 0; var gy = 0
        for (i in 1 until cnt) {
          gx += chars[(i - 1) * 2].code - 85; gy += chars[(i - 1) * 2 + 1].code - 85
          nc[i * 2] = hx + gx / 40f; nc[i * 2 + 1] = hy + gy / 40f
        }
        s.cur = nc; s.x = hx; s.y = hy
        s.prev = if (wasAlive && old.isNotEmpty()) old else nc
      } else { s.cur = FloatArray(0); s.prev = FloatArray(0) }
    }
    val pl = (m["p"] as? List<*>)?.map { n(it) } ?: emptyList()
    val oldPicks = pickups.map { (it.x * 2f).roundToInt() * 4096 + (it.y * 2f).roundToInt() }.toSet()
    pickups.clear()
    var i = 0
    while (i + 2 < pl.size) { pickups.add(SPick(pl[i], pl[i + 1] / 100f, pl[i + 2] / 100f)); i += 3 }
    val bl = (m["b"] as? List<*>)?.map { n(it) } ?: emptyList()
    bullets.clear()
    i = 0
    while (i + 3 < bl.size) { bullets.add(SBullet(bl[i] / 100f, bl[i + 1] / 100f, bl[i + 2] / 100f, bl[i + 3], 1f)); i += 4 }
    val nowPicks = pickups.map { (it.x * 2f).roundToInt() * 4096 + (it.y * 2f).roundToInt() }.toSet()
    for (c in oldPicks) if (c !in nowPicks) events.add(SEvent(0, (c / 4096) / 2f, (c % 4096) / 2f, 0xFFFFFFFF.toInt()))
    if (events.size > 120) events.clear()
  }
}
