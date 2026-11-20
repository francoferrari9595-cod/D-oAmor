package com.example.soccer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Motor de «Fútbol Pro»: cancha reglamentaria (105 × 68 m), jugadores con carrera y resistencia, balón con altura, efecto, rebotes y
 * postes, regate, pases, tiros con potencia, entradas, porteros que atajan, saques de banda/esquina/arco y goles.
 * Equipos de 1 a 4 jugadores de campo + portero (siempre IA). Cualquier jugador puede ser humano o bot.
 * El equipo 0 ataca hacia y positivo; el equipo 1, hacia y negativo.
 */

internal const val S_HW = 34f          // medio ancho
internal const val S_HL = 52.5f        // medio largo
internal const val S_GW = 3.66f        // medio ancho de la portería
internal const val S_GH = 2.44f        // alto de la portería

internal fun sDir(team: Int) = if (team == 0) 1f else -1f

internal class SPlayer(val id: Int, val team: Int, val slot: Int) {
  var uid = ""; var name = ""; var number = 0
  var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var face = 0f; var run = 0f
  var nx = 0f; var ny = 0f
  var homeU = 0f; var homeV = 0f
  var ix = 0f; var iy = 0f; var sprint = false; var stamina = 1f
  var kickT = 0f; var stunT = 0f; var tackleT = 0f; var touchT = 0f; var holdT = 0f; var diveT = 0f
  var human = false
  var hold = false; var stealCd = 0f            // ROBAR mantenido: persigue al que tiene la pelota y se la quita
  var think = 0f; var decision = 0
  val isGK: Boolean get() = slot == 0
}

internal class SBall { var x = 0f; var y = 0f; var z = 0f; var vx = 0f; var vy = 0f; var vz = 0f; var curl = 0f; var owner = -1; var spin = 0f }
internal class SEvent(val kind: Int, val x: Float, val y: Float, val power: Float, val team: Int)   // 0 patada, 1 entrada, 2 gol, 3 atajada, 4 poste, 5 pitido, 6 rebote

internal const val PH_KICKOFF = 0
internal const val PH_PLAY = 1
internal const val PH_GOAL = 2
internal const val PH_RESTART = 3
internal const val PH_HALF = 4
internal const val PH_OVER = 5

private val FORM = listOf(
  emptyList(),
  listOf(0f to 26f),
  listOf(-9f to 20f, 9f to 32f),
  listOf(-14f to 18f, 14f to 18f, 0f to 34f),
  listOf(-16f to 16f, 16f to 16f, -8f to 34f, 8f to 34f)
)

internal class SoccerWorld(val teamSize: Int, val level: Int = 1, val halfSec: Float = 90f, val rnd: Random = Random.Default, val mirror: Boolean = false) {
  val players = ArrayList<SPlayer>()
  val ball = SBall()
  val score = IntArray(2)
  var phase = PH_KICKOFF; var phaseT = 0f
  var half = 1; var time = 0f
  var msg = ""
  var goalSeq = 0; var lastScorer = -1; var scorerTeam = -1
  var lastTouchTeam = 0; var lastTouch = -1
  var kickingTeam = 0
  val events = ArrayList<SEvent>()
  var passTo = -1; var passT = 0f            // receptor previsto del último pase (para que corra a buscarla y la controle)
  var kitIdx = intArrayOf(0, 1)
  var kits: Pair<SKit, SKit> = resolveKits(0, 1)
  fun setKits(a: Int, b: Int) { kitIdx[0] = a; kitIdx[1] = b; kits = resolveKits(a, b) }
  private val ctrlOf = HashMap<String, Int>()
  private val switchT = HashMap<String, Float>()
  private val pendingRestart = ArrayList<Float>()
  private var rsTeam = 0; private var rsX = 0f; private var rsY = 0f; private var rsGK = false
  val totalTime: Float get() = halfSec * 2f
  private var nbx = 0f; private var nby = 0f; private var gotFirst = false; private var since = 0f
  val winner: Int get() = if (phase == PH_OVER) (if (score[0] > score[1]) 0 else if (score[1] > score[0]) 1 else -1) else -2

  init {
    for (team in 0..1) {
      val gk = SPlayer(players.size, team, 0); gk.number = 1; gk.homeU = 0f; gk.homeV = 2.5f; players.add(gk)
      for (i in 1..teamSize) {
        val p = SPlayer(players.size, team, i); p.number = 1 + i + (if (i > 1) 1 else 0)
        val f = FORM[teamSize.coerceIn(1, 4)][i - 1]; p.homeU = f.first; p.homeV = f.second; players.add(p)
      }
    }
    kickoff(0)
  }

  // ───────────── humanos ─────────────
  /** Asigna un humano a un jugador de campo libre de su equipo. */
  fun assignHuman(uid: String, name: String, team: Int): SPlayer? {
    val free = players.firstOrNull { it.team == team && !it.isGK && !it.human } ?: return null
    free.human = true; free.uid = uid; free.name = name; ctrlOf[uid] = free.id
    return free
  }
  fun controlled(uid: String): SPlayer? = ctrlOf[uid]?.let { players.getOrNull(it) }
  private fun humansIn(team: Int) = players.count { it.team == team && it.human }

  fun setInput(uid: String, jx: Float, jy: Float, sprint: Boolean) {
    val p = controlled(uid) ?: return
    val d = sDir(p.team)
    // cámara lateral: derecha = hacia el arco rival, arriba = hacia el lado lejano de la cancha
    p.ix = -jy * d; p.iy = jx * d; p.sprint = sprint || hypot(jx, jy) > 0.92f   // joystick a fondo = correr
  }

  fun shootRelease(uid: String, power: Float) { val p = controlled(uid) ?: return; if (phase == PH_PLAY || phase == PH_KICKOFF) act(p, 1, power) }
  /** Pase raso hacia donde apunta el joystick; [power] (0..1) es cuánto se mantuvo apretado el botón. */
  fun passPress(uid: String, power: Float = -1f) { val p = controlled(uid) ?: return; if (phase == PH_PLAY || phase == PH_KICKOFF) act(p, if (power > 0.92f) 3 else 2, power) }
  /** Pase alto (por arriba, para saltar a los rivales). */
  fun lobPress(uid: String) { val p = controlled(uid) ?: return; if (phase == PH_PLAY || phase == PH_KICKOFF) act(p, 3, -1f) }
  /** Pase filtrado: al espacio, por delante del compañero que corre. */
  fun throughPress(uid: String) { val p = controlled(uid) ?: return; if (phase == PH_PLAY || phase == PH_KICKOFF) act(p, 4, -1f) }
  fun setHold(uid: String, on: Boolean) { val p = controlled(uid) ?: return; p.hold = on && phase == PH_PLAY }
  fun tacklePress(uid: String) { val p = controlled(uid) ?: return; if (phase == PH_PLAY) tackle(p) }
  fun switchPress(uid: String) { switchTo(uid, true) }

  private fun switchTo(uid: String, force: Boolean) {
    val cur = controlled(uid) ?: return
    if (!force && (switchT[uid] ?: 0f) > 0f) return
    var best: SPlayer? = null; var bd = 999f
    for (q in players) {
      if (q.team != cur.team || q.isGK || q === cur || q.human) continue
      val d = hypot(q.x - ball.x, q.y - ball.y)
      if (d < bd) { bd = d; best = q }
    }
    if (best == null) return
    if (!force && hypot(cur.x - ball.x, cur.y - ball.y) <= bd + 1.5f) return
    cur.human = false; cur.uid = ""; cur.ix = 0f; cur.iy = 0f
    best.human = true; best.uid = uid; best.name = cur.name; ctrlOf[uid] = best.id; switchT[uid] = 0.6f
  }

  // ───────────── saques y posiciones ─────────────
  private fun homeOf(p: SPlayer, shift: Boolean): Pair<Float, Float> {
    val d = sDir(p.team)
    var v = p.homeV
    if (shift && !p.isGK) { val prog = ball.y * d + S_HL; v += ((prog - S_HL) * 0.30f).coerceIn(-12f, 16f) }
    val u = if (shift && !p.isGK) p.homeU + ball.x * 0.18f else p.homeU
    return (u * d).coerceIn(-S_HW + 3f, S_HW - 3f) to (-d * S_HL + d * v)
  }

  fun kickoff(team: Int) {
    kickingTeam = team
    ball.x = 0f; ball.y = 0f; ball.z = 0f; ball.vx = 0f; ball.vy = 0f; ball.vz = 0f; ball.curl = 0f; ball.owner = -1
    for (p in players) { val h = homeOf(p, false); p.x = h.first; p.y = h.second; p.vx = 0f; p.vy = 0f; p.stunT = 0f; p.holdT = 0f; p.face = if (p.team == 0) (PI / 2f).toFloat() else (-PI / 2f).toFloat() }
    var best: SPlayer? = null; var bd = 999f
    for (p in players) if (p.team == team && !p.isGK) { val d = abs(p.homeV - 26f); if (d < bd) { bd = d; best = p } }
    best?.let { it.x = -0.7f * sDir(team); it.y = -0.3f * sDir(team); ball.owner = it.id }
    phase = PH_KICKOFF; phaseT = 0f; msg = ""
  }

  private fun restartAt(team: Int, x: Float, y: Float, gk: Boolean, text: String) {
    rsTeam = team; rsX = x; rsY = y; rsGK = gk
    ball.x = x; ball.y = y; ball.z = 0f; ball.vx = 0f; ball.vy = 0f; ball.vz = 0f; ball.owner = -1; ball.curl = 0f
    var best: SPlayer? = null; var bd = 999f
    for (p in players) { if (p.team != team || (p.isGK != gk)) continue; val d = hypot(p.x - x, p.y - y); if (d < bd) { bd = d; best = p } }
    best?.let { it.x = x - sDir(team) * 0.3f * (if (abs(x) > 30f) 0f else 1f); it.y = y; ball.owner = it.id; it.vx = 0f; it.vy = 0f }
    phase = PH_RESTART; phaseT = 0f; msg = text
    events.add(SEvent(5, x, y, 0f, team))
  }

  // ───────────── acciones ─────────────
  private fun act(p: SPlayer, kind: Int, power: Float) {
    if (p.kickT > 0f || p.stunT > 0f) return
    val hasBall = ball.owner == p.id
    val near = hypot(p.x - ball.x, p.y - ball.y)
    if (!hasBall && !(near < 1.3f && ball.z < 1.4f)) return
    val d = sDir(p.team)
    var dx = p.ix; var dy = p.iy
    var m = hypot(dx, dy)
    val aimed = m >= 0.25f                                  // el joystick apunta a una dirección concreta
    if (m < 0.15f) { dx = cos(p.face); dy = sin(p.face); m = 1f }
    dx /= m; dy /= m
    when (kind) { 2 -> pass(p, dx, dy, 0, power, aimed); 3 -> pass(p, dx, dy, 1, power, aimed); 4 -> pass(p, dx, dy, 2, power, aimed); else -> shoot(p, dx, dy, power) }
  }

  private fun release(p: SPlayer) { ball.owner = -1; p.kickT = 0.32f; p.touchT = 0.4f; lastTouchTeam = p.team; lastTouch = p.id }

  private fun shoot(p: SPlayer, dxIn: Float, dyIn: Float, power: Float) {
    var dx = dxIn; var dy = dyIn
    val d = sDir(p.team)
    // ayuda de puntería hacia la portería rival si se apunta más o menos hacia ella
    val gx = 0f; val gy = d * S_HL
    val tx = gx - p.x; val ty = gy - p.y; val td = hypot(tx, ty).coerceAtLeast(0.1f)
    val cosA = (dx * tx + dy * ty) / td
    if (cosA > 0.86f && td < 38f) { dx = dx * 0.55f + tx / td * 0.45f; dy = dy * 0.55f + ty / td * 0.45f; val mm = hypot(dx, dy); dx /= mm; dy /= mm }
    val pw = power.coerceIn(0.15f, 1f)
    val sp = 9f + 21f * pw
    ball.vx = dx * sp + p.vx * 0.25f; ball.vy = dy * sp + p.vy * 0.25f
    ball.vz = if (pw > 0.82f) 2.2f + 5f * (pw - 0.82f) else 0.6f * pw
    ball.curl = ((cos(p.face) * dy - sin(p.face) * dx) * 0.10f).coerceIn(-0.12f, 0.12f)   // si apuntas distinto a hacia donde corres, la pelota sale con efecto
    ball.spin = 1f
    release(p)
    events.add(SEvent(0, ball.x, ball.y, pw, p.team))
  }

  /** [style]: 0 pase raso al pie, 1 pase alto, 2 pase filtrado al espacio. */
  private fun pass(p: SPlayer, dx: Float, dy: Float, style: Int = 0, power: Float = -1f, aimed: Boolean = false) {
    var best: SPlayer? = null
    // con el joystick apuntando: primero se busca a alguien casi en esa línea; si no hay, se acepta un ángulo más abierto
    val tiers = if (aimed) floatArrayOf(0.80f, 0.55f) else floatArrayOf(0.35f)
    for (minC in tiers) {
      var bs = -9f
      for (q in players) {
        if (q === p || q.team != p.team || q.isGK && p.isGK) continue
        val rx = q.x - p.x; val ry = q.y - p.y; val dist = hypot(rx, ry)
        if (dist < 2f || dist > 42f) continue
        val c = (rx * dx + ry * dy) / dist
        if (c < minC) continue
        val ahead = (q.y - p.y) * sDir(p.team)
        val score = c * 2.4f - dist * 0.012f + (if (q.human) 0.2f else 0f) + (if (style == 2) ahead * 0.03f else 0f) + (if (style == 1 && dist > 12f) 0.3f else 0f)
        if (score > bs) { bs = score; best = q }
      }
      if (best != null) break
    }
    val tx: Float; val ty: Float; val sp: Float
    if (best != null) {
      val dist = hypot(best.x - p.x, best.y - p.y)
      val base = if (style == 1) (dist * 0.8f + 6f).coerceIn(8f, 19f) else if (style == 2) (dist * 1.2f + 8f).coerceIn(12f, 26f) else (dist * 0.85f + 6.5f).coerceIn(9f, 20f)
      sp = if (power >= 0f) base * (0.8f + 0.4f * power) else base
      // adelanta al receptor según lo que tarda la pelota en llegar
      val tt = (dist / sp).coerceIn(0.15f, 1.3f) * (if (style == 2) 1.0f else 0.75f)
      tx = best.x + best.vx * tt + (if (style == 2) dx * 4f else 0f); ty = best.y + best.vy * tt + (if (style == 2) dy * 4f else 0f)
    } else {
      // sin compañero en esa dirección: el pase sale recto, con la potencia que se eligió
      val pw = if (power >= 0f) power else 0.5f
      tx = p.x + dx * 30f; ty = p.y + dy * 30f
      sp = if (style == 1) 9f + 13f * pw else 8f + 20f * pw
    }
    val ddx = tx - p.x; val ddy = ty - p.y; val dd = hypot(ddx, ddy).coerceAtLeast(0.1f)
    ball.vx = ddx / dd * sp; ball.vy = ddy / dd * sp
    ball.vz = if (style == 1) (5.5f + (if (best != null) dd * 0.12f else sp * 0.25f)).coerceAtMost(8.5f) else if (sp > 19f) 3.0f else 0.35f
    ball.curl = 0f
    release(p)
    if (best != null) {
      passTo = best.id; passT = (dd / sp).coerceIn(0.4f, 2.5f) + 0.8f
      // el control pasa al compañero que va a recibir (si el que pasa lo manejaba una persona)
      if (p.human && !best.human && p.uid.isNotEmpty()) {
        val uid = p.uid
        p.human = false; p.uid = ""; p.ix = 0f; p.iy = 0f
        best.human = true; best.uid = uid; best.name = p.name; ctrlOf[uid] = best.id; switchT[uid] = 0.8f
      }
    }
    events.add(SEvent(0, ball.x, ball.y, 0.5f, p.team))
  }

  private fun tackle(p: SPlayer) {
    if (p.tackleT > 0f || p.stunT > 0f) return
    p.tackleT = 0.9f
    val dx = cos(p.face); val dy = sin(p.face)
    p.vx += dx * 5f; p.vy += dy * 5f
    val o = if (ball.owner >= 0) players[ball.owner] else null
    if (o != null && o.team != p.team) {
      val rx = o.x - p.x; val ry = o.y - p.y; val dist = hypot(rx, ry)
      if (dist < 1.9f && (rx * dx + ry * dy) > -0.3f) {
        val chance = floatArrayOf(0.40f, 0.52f, 0.62f, 0.72f)[level.coerceIn(0, 3)].let { if (p.human) 0.62f else it }
        if (rnd.nextFloat() < chance) {
          ball.owner = -1; ball.vx = dx * 5f + (rnd.nextFloat() - 0.5f) * 3f; ball.vy = dy * 5f + (rnd.nextFloat() - 0.5f) * 3f; ball.vz = 0.8f
          o.stunT = 0.55f; o.touchT = 0.5f; lastTouchTeam = p.team; lastTouch = p.id
          events.add(SEvent(1, ball.x, ball.y, 1f, p.team))
        } else { p.stunT = 0.6f; events.add(SEvent(1, ball.x, ball.y, 0f, p.team)) }
      }
    } else if (o == null && hypot(p.x - ball.x, p.y - ball.y) < 1.6f) {
      ball.vx = dx * 7f; ball.vy = dy * 7f; ball.vz = 0.4f; lastTouchTeam = p.team; lastTouch = p.id; p.touchT = 0.3f
      events.add(SEvent(1, ball.x, ball.y, 1f, p.team))
    }
  }

  // ───────────── actualización ─────────────
  fun update(dtIn: Float) {
    val dt = dtIn.coerceIn(0f, 0.05f)
    phaseT += dt
    for ((k, v) in switchT.entries.toList()) switchT[k] = max(0f, v - dt)
    passT = max(0f, passT - dt); if (passT <= 0f) passTo = -1
    when (phase) {
      PH_KICKOFF -> { if (phaseT > 1.2f) { phase = PH_PLAY; events.add(SEvent(5, 0f, 0f, 1f, 0)) } else { for (p in players) if (p.isGK || p.team != kickingTeam || ball.owner != p.id) { /* quietos */ } ; stepPlayers(dt, true); return } }
      PH_GOAL -> { stepPlayers(dt, true); ballPhysics(dt); if (phaseT > 3.4f) { if (!checkEnd()) kickoff(1 - scorerTeam) }; return }
      PH_RESTART -> { stepPlayers(dt, true); if (phaseT > 1.0f) phase = PH_PLAY else return }
      PH_HALF -> { if (phaseT > 3f) { half = 2; kickoff(1) }; return }
      PH_OVER -> return
    }
    time += dt
    if (half == 1 && time >= halfSec) { phase = PH_HALF; phaseT = 0f; msg = "Medio tiempo"; events.add(SEvent(5, 0f, 0f, 2f, 0)); return }
    if (half == 2 && time >= totalTime) { phase = PH_OVER; msg = "Fin del partido"; events.add(SEvent(5, 0f, 0f, 3f, 0)); return }
    for (p in players) {
      p.kickT = max(0f, p.kickT - dt); p.stunT = max(0f, p.stunT - dt); p.tackleT = max(0f, p.tackleT - dt); p.touchT = max(0f, p.touchT - dt); p.diveT = max(0f, p.diveT - dt)
      if (p.isGK) botKeeper(p, dt) else if (!p.human) botField(p, dt)
    }
    for ((uid, _) in ctrlOf.entries.toList()) if (humansIn(controlled(uid)?.team ?: 0) == 1) autoSwitch(uid)
    for (p in players) { p.stealCd = max(0f, p.stealCd - dt); if (!p.human) p.hold = false; if (p.hold) holdSteal(p) }
    stepPlayers(dt, false)
    ballPhysics(dt)
  }

  /** ROBAR mantenido: corre solo hacia el rival con la pelota y, al alcanzarlo, se la quita directamente. */
  private fun holdSteal(p: SPlayer) {
    val o = if (ball.owner >= 0) players[ball.owner] else null
    if (o == null || o.team == p.team || p.stunT > 0f) return
    val rx = o.x - p.x; val ry = o.y - p.y; val d = hypot(rx, ry)
    if (d < 11f) { p.ix = rx / max(d, 0.01f); p.iy = ry / max(d, 0.01f); p.sprint = true }
    if (d < 1.7f && p.stealCd <= 0f) {
      p.stealCd = 1.1f
      ball.owner = p.id; ball.vx = 0f; ball.vy = 0f; ball.vz = 0f
      o.stunT = 0.5f; o.touchT = 0.6f; p.touchT = 0f; lastTouchTeam = p.team; lastTouch = p.id
      events.add(SEvent(1, ball.x, ball.y, 1f, p.team))
    }
  }

  private fun autoSwitch(uid: String) {
    val cur = controlled(uid) ?: return
    if (ball.owner == cur.id) return
    switchTo(uid, false)
  }

  private fun stepPlayers(dt: Float, frozen: Boolean) {
    for (p in players) {
      val lv = level.coerceIn(0, 3)
      var maxV = (if (p.sprint && p.stamina > 0.05f) 9.8f else 6.4f) * (if (p.human) 1f else floatArrayOf(0.88f, 0.96f, 1.03f, 1.1f)[lv])
      if (p.isGK) maxV *= 0.95f
      if (p.stunT > 0f) maxV *= 0.25f
      var ix = p.ix; var iy = p.iy
      if (frozen) { ix = 0f; iy = 0f }
      val m = hypot(ix, iy); if (m > 1f) { ix /= m; iy /= m }
      val tvx = ix * maxV; val tvy = iy * maxV
      val k = min(1f, dt * 8f)
      p.vx += (tvx - p.vx) * k; p.vy += (tvy - p.vy) * k
      val moving = hypot(p.vx, p.vy) > 1f
      p.stamina = (p.stamina + (if (p.sprint && moving) -dt * 0.11f else dt * 0.14f)).coerceIn(0f, 1f)
      p.x = (p.x + p.vx * dt).coerceIn(-S_HW - 2.5f, S_HW + 2.5f); p.y = (p.y + p.vy * dt).coerceIn(-S_HL - 3.5f, S_HL + 3.5f)
      val sp = hypot(p.vx, p.vy)
      if (sp > 0.6f) { val tgt = atan2(p.vy, p.vx); var df = tgt - p.face; while (df > PI) df -= 2f * PI.toFloat(); while (df < -PI) df += 2f * PI.toFloat(); p.face += df * min(1f, dt * 12f) }
      p.run += sp * dt * 1.5f
    }
    // separación entre jugadores
    for (i in 0 until players.size) for (j in i + 1 until players.size) {
      val a = players[i]; val b = players[j]
      val dx = b.x - a.x; val dy = b.y - a.y; val d = hypot(dx, dy)
      if (d < 0.8f && d > 0.001f) { val push = (0.8f - d) * 0.5f; a.x -= dx / d * push; a.y -= dy / d * push; b.x += dx / d * push; b.y += dy / d * push }
    }
  }

  private fun ballPhysics(dt: Float) {
    val o = if (ball.owner >= 0) players[ball.owner] else null
    if (o != null) {
      val tx = o.x + cos(o.face) * 0.62f; val ty = o.y + sin(o.face) * 0.62f
      val k = min(1f, dt * 14f)
      ball.x += (tx - ball.x) * k; ball.y += (ty - ball.y) * k; ball.vx = o.vx; ball.vy = o.vy; ball.z = 0f; ball.vz = 0f
      ball.spin = hypot(o.vx, o.vy)
      lastTouchTeam = o.team; lastTouch = o.id
      if (o.stunT > 0.3f) ball.owner = -1
      if (o.isGK) { o.holdT += dt; if (phase == PH_PLAY && o.holdT > 1.3f) { releaseKeeper(o) } } else o.holdT = 0f
      return
    }
    // física libre
    val sub = 3; val h = dt / sub
    for (s in 0 until sub) {
      ball.vz -= 9.81f * h
      ball.x += ball.vx * h; ball.y += ball.vy * h; ball.z += ball.vz * h
      if (ball.z <= 0f) {
        ball.z = 0f
        if (ball.vz < -1.2f) { events.add(SEvent(6, ball.x, ball.y, min(1f, -ball.vz / 8f), 0)); ball.vz = -ball.vz * 0.55f } else ball.vz = 0f
      }
      val sp = hypot(ball.vx, ball.vy)
      if (sp > 0.01f) {
        val fr = (if (ball.z <= 0.02f) 1.7f else 0.25f) * h
        val ns = max(0f, sp - fr - sp * sp * 0.0016f * h)
        ball.vx *= ns / sp; ball.vy *= ns / sp
        if (ball.curl != 0f) { val ax = -ball.vy / sp * ball.curl * sp * h; val ay = ball.vx / sp * ball.curl * sp * h; ball.vx += ax; ball.vy += ay; ball.curl *= exp(-0.8f * h) }
      }
      ball.spin = hypot(ball.vx, ball.vy)
    }
    if (phase == PH_GOAL && abs(ball.y) > S_HL + 2.2f) { ball.y = (if (ball.y > 0) 1f else -1f) * (S_HL + 2.2f); ball.vy *= -0.15f; ball.vx *= 0.3f }
    if (phase != PH_PLAY) return
    // quién la toma
    var cand: SPlayer? = null; var cd = 99f
    for (p in players) {
      if (p.touchT > 0f || p.stunT > 0.3f) continue
      val d = hypot(p.x - ball.x, p.y - ball.y)
      val reach = if (p.isGK) 1.35f else if (p.id == passTo) 1.15f else 0.8f
      if (d < reach && ball.z < (if (p.isGK) 2.3f else 1.0f) && d < cd) { cand = p; cd = d }
    }
    val speed = hypot(ball.vx, ball.vy)
    if (cand != null) {
      if (cand.isGK && speed > 16f && abs(ball.y) > S_HL - 18f) { parry(cand) }
      else if (speed < (if (cand.isGK) 20f else 17f) || cand.human || cand.id == passTo) { passTo = -1; ball.owner = cand.id; cand.touchT = 0f; lastTouchTeam = cand.team; lastTouch = cand.id; if (cand.isGK) { cand.holdT = 0f; events.add(SEvent(3, ball.x, ball.y, 0.5f, cand.team)) } }
    }
    // fuera de juego
    if (abs(ball.x) > S_HW) {
      val team = 1 - lastTouchTeam
      restartAt(team, if (ball.x > 0) S_HW - 0.5f else -S_HW + 0.5f, ball.y.coerceIn(-S_HL + 3f, S_HL - 3f), false, "Saque de banda")
      return
    }
    if (abs(ball.y) > S_HL) {
      val sgn = if (ball.y > 0) 1f else -1f
      val attackerTeam = if (sgn > 0) 0 else 1
      if (abs(ball.x) < S_GW && ball.z < S_GH) { goal(attackerTeam); return }
      if (abs(abs(ball.x) - S_GW) < 0.18f && ball.z < S_GH + 0.1f) { ball.vy = -ball.vy * 0.55f; ball.y = sgn * (S_HL - 0.25f); events.add(SEvent(4, ball.x, ball.y, 1f, 0)); return }
      if (abs(ball.x) < S_GW + 0.1f && ball.z in S_GH..(S_GH + 0.25f)) { ball.vy = -ball.vy * 0.5f; ball.vz = -ball.vz * 0.4f; ball.y = sgn * (S_HL - 0.25f); events.add(SEvent(4, ball.x, ball.y, 1f, 0)); return }
      if (lastTouchTeam != attackerTeam) restartAt(attackerTeam, if (ball.x > 0) S_HW - 0.8f else -S_HW + 0.8f, sgn * (S_HL - 0.8f), false, "Saque de esquina")
      else restartAt(1 - attackerTeam, 0f, sgn * (S_HL - 6f), true, "Saque de meta")
    }
  }

  private fun parry(gk: SPlayer) {
    ball.vx = -ball.vx * 0.4f + (rnd.nextFloat() - 0.5f) * 6f; ball.vy = -ball.vy * 0.45f; ball.vz = 2f; gk.touchT = 0.5f; gk.diveT = 0.5f
    events.add(SEvent(3, ball.x, ball.y, 1f, gk.team)); lastTouchTeam = gk.team; lastTouch = gk.id
  }

  private fun releaseKeeper(gk: SPlayer) {
    var best: SPlayer? = null; var bd = -1f
    for (q in players) if (q.team == gk.team && q !== gk) { val d = hypot(q.x - gk.x, q.y - gk.y); if (d > bd && d < 40f) { bd = d; best = q } }
    best?.let {
      val dx = it.x - gk.x; val dy = it.y - gk.y; val dd = hypot(dx, dy).coerceAtLeast(0.1f); val sp = (dd * 1.0f + 7f).coerceIn(10f, 24f)
      ball.vx = dx / dd * sp; ball.vy = dy / dd * sp; ball.vz = 3f
    }
    release(gk)
    gk.holdT = 0f
  }

  private fun goal(team: Int) {
    score[team]++
    phase = PH_GOAL; phaseT = 0f; scorerTeam = team; lastScorer = if (lastTouchTeam == team) lastTouch else -1
    goalSeq++; msg = "¡GOOOL!"
    ball.owner = -1
    ball.vx *= 0.15f; ball.vy *= 0.15f
    events.add(SEvent(2, ball.x, ball.y, 1f, team))
  }

  private fun checkEnd(): Boolean = false

  // ───────────── bots ─────────────
  private fun botKeeper(g: SPlayer, dt: Float) {
    val d = sDir(g.team); val goalY = -d * S_HL
    g.ix = 0f; g.iy = 0f
    if (ball.owner == g.id) return
    var tx = (ball.x * 0.16f).coerceIn(-3f, 3f); var ty = goalY + d * 2.2f
    val towards = ball.vy * d < -5f && abs(ball.y - goalY) < 32f
    if (towards) {
      val tt = (goalY - ball.y) / ball.vy
      if (tt in 0f..1.8f) {
        val px = ball.x + ball.vx * tt
        if (abs(px) < 7f) { tx = px.coerceIn(-4.5f, 4.5f); ty = goalY + d * 0.9f; if (tt < 0.45f) g.diveT = 0.5f }
      }
    } else if (ball.owner == -1 && hypot(ball.x - g.x, ball.y - g.y) < 9f && abs(ball.y - goalY) < 14f && hypot(ball.vx, ball.vy) < 6f) { tx = ball.x; ty = ball.y }
    val dx = tx - g.x; val dy = ty - g.y; val dist = hypot(dx, dy)
    val sp = if (towards) 1f else min(1f, dist / 2f)
    if (dist > 0.1f) { g.ix = dx / dist * sp; g.iy = dy / dist * sp }
    g.sprint = towards
  }

  private fun nearestToBall(team: Int, excludeGk: Boolean, excludeHuman: Boolean): SPlayer? {
    var best: SPlayer? = null; var bd = 999f
    val px = ball.x + ball.vx * 0.35f; val py = ball.y + ball.vy * 0.35f
    for (p in players) {
      if (p.team != team || (excludeGk && p.isGK) || (excludeHuman && p.human)) continue
      val d = hypot(p.x - px, p.y - py)
      if (d < bd) { bd = d; best = p }
    }
    return best
  }

  private fun botField(p: SPlayer, dt: Float) {
    val lv = level.coerceIn(0, 3)
    val d = sDir(p.team)
    p.ix = 0f; p.iy = 0f; p.sprint = false
    p.think -= dt
    if (passTo == p.id && ball.owner < 0) {
      val tt = 0.35f
      moveToward(p, ball.x + ball.vx * tt, ball.y + ball.vy * tt, 1f); p.sprint = hypot(p.x - ball.x, p.y - ball.y) > 3f
      return
    }
    val owner = if (ball.owner >= 0) players[ball.owner] else null
    if (owner === p) {
      val gx = 0f; val gy = d * S_HL
      val toG = hypot(gx - p.x, gy - p.y)
      // oponente más cercano (presión)
      var press = 99f; var avx = 0f; var avy = 0f
      for (q in players) if (q.team != p.team) {
        val dd = hypot(q.x - p.x, q.y - p.y)
        press = min(press, dd)
        if (dd < 7f && dd > 0.1f) { avx -= (q.x - p.x) / dd * (7f - dd); avy -= (q.y - p.y) / dd * (7f - dd) }
      }
      var dxg = (gx - p.x) / toG.coerceAtLeast(0.1f); var dyg = (gy - p.y) / toG.coerceAtLeast(0.1f)
      dxg += avx * 0.12f; dyg += avy * 0.12f
      val mm = hypot(dxg, dyg).coerceAtLeast(0.1f)
      p.ix = dxg / mm; p.iy = dyg / mm; p.sprint = press > 5f
      if (p.think <= 0f && p.kickT <= 0f) {
        p.think = 0.3f + rnd.nextFloat() * 0.35f + (3 - lv) * 0.08f
        val facingGoal = (cos(p.face) * (gx - p.x) + sin(p.face) * (gy - p.y)) / toG.coerceAtLeast(0.1f)
        if (toG < 26f && facingGoal > 0.6f && rnd.nextFloat() < 0.5f + 0.1f * lv) {
          val aimX = (if (rnd.nextBoolean()) 1f else -1f) * (1.4f + rnd.nextFloat() * 1.8f) + (rnd.nextFloat() - 0.5f) * (3 - lv) * 1.2f
          val tx = aimX - p.x; val ty = gy - p.y; val td = hypot(tx, ty)
          p.ix = tx / td * 0.01f; p.iy = ty / td * 0.01f
          shootAim(p, tx / td, ty / td, 0.78f + rnd.nextFloat() * 0.22f)
        } else if (press < 4.2f && rnd.nextFloat() < 0.55f) {
          passAim(p)
        }
      }
      return
    }
    val h = homeOf(p, true)
    if (owner != null && owner.team == p.team) {
      // apoyo: avanzar un poco desde la posición base
      moveToward(p, h.first, h.second + d * 4f, 0.9f)
      return
    }
    if (owner != null && owner.team != p.team) {
      val chaser = nearestToBall(p.team, true, true)
      if (chaser === p) {
        val dd = hypot(owner.x - p.x, owner.y - p.y)
        moveToward(p, owner.x + owner.vx * 0.3f, owner.y + owner.vy * 0.3f, 1f); p.sprint = dd > 3f
        if (dd < 1.5f && p.tackleT <= 0f && p.think <= 0f) { p.think = 0.5f; if (rnd.nextFloat() < floatArrayOf(0.3f, 0.45f, 0.6f, 0.75f)[lv]) tackle(p) }
      } else {
        // marcación: se ubica entre el rival más peligroso de su zona y su propio arco
        var mark: SPlayer? = null; var md = 14f
        for (q in players) if (q.team != p.team && !q.isGK && q !== owner) { val dd = hypot(q.x - h.first, q.y - h.second); if (dd < md) { md = dd; mark = q } }
        val mk = mark
        if (mk != null) moveToward(p, mk.x + (0f - mk.x) * 0.2f, mk.y + (-d * S_HL - mk.y) * 0.18f, 0.9f) else moveToward(p, h.first, h.second, 0.85f)
      }
      return
    }
    val chaser = nearestToBall(p.team, true, true)
    if (chaser === p) { moveToward(p, ball.x + ball.vx * 0.4f, ball.y + ball.vy * 0.4f, 1f); p.sprint = true
      if (hypot(p.x - ball.x, p.y - ball.y) < 1.0f && ball.z < 1.2f && p.kickT <= 0f && p.touchT <= 0f) { passAim(p) }
    } else moveToward(p, h.first, h.second, 0.85f)
  }

  private fun moveToward(p: SPlayer, tx: Float, ty: Float, mag: Float) {
    val dx = tx - p.x; val dy = ty - p.y; val d = hypot(dx, dy)
    if (d > 0.6f) { val s = min(mag, d / 3f + 0.2f); p.ix = dx / d * s; p.iy = dy / d * s }
  }

  private fun shootAim(p: SPlayer, dx: Float, dy: Float, power: Float) {
    if (ball.owner != p.id) return
    shoot(p, dx, dy, power)
  }

  private fun passAim(p: SPlayer) {
    val d = sDir(p.team)
    if (ball.owner != p.id && !(hypot(p.x - ball.x, p.y - ball.y) < 1.3f)) return
    // prefiere compañeros más adelantados
    var best: SPlayer? = null; var bs = -99f
    for (q in players) {
      if (q === p || q.team != p.team || q.isGK) continue
      val dist = hypot(q.x - p.x, q.y - p.y)
      if (dist < 4f || dist > 32f) continue
      var danger = 0f
      for (o in players) if (o.team != p.team) { val dd = hypot(o.x - q.x, o.y - q.y); if (dd < 4f) danger += (4f - dd) }
      val score = (q.y - p.y) * d * 0.08f - dist * 0.02f - danger * 0.5f
      if (score > bs) { bs = score; best = q }
    }
    if (best != null) { val bx = best.x - p.x; val by = best.y - p.y; val bm = hypot(bx, by).coerceAtLeast(0.1f); pass(p, bx / bm, by / bm) } else shoot(p, 0f, d, 0.5f)
  }

  // ───────────── red ─────────────
  private fun clean(s: String) = s.replace('|', ' ').replace(',', ' ').take(10)

  fun encode(): Map<String, Any?> {
    val pl = players.map { p -> "${(p.x * 100f).toInt()}|${(p.y * 100f).toInt()}|${(p.vx * 100f).toInt()}|${(p.vy * 100f).toInt()}|${(p.face * 100f).toInt()}|${if (p.human) 1 else 0}|${clean(p.uid)}|${clean(p.name)}|${if (p.stunT > 0f) 1 else 0}|${if (p.kickT > 0f) 1 else 0}|${if (p.tackleT > 0.5f) 1 else 0}|${if (p.sprint) 1 else 0}|${(p.stamina * 100f).toInt()}|${if (p.diveT > 0f) 1 else 0}" }
    val b = listOf(ball.x, ball.y, ball.z, ball.vx, ball.vy, ball.vz, ball.spin).map { (it * 100f).toLong() } + listOf(ball.owner.toLong())
    return mapOf("pl" to pl, "ba" to b, "sc" to listOf(score[0].toLong(), score[1].toLong(), phase.toLong(), half.toLong(), goalSeq.toLong(), scorerTeam.toLong()), "tm" to (time * 10f).toLong(), "ms" to msg, "ts" to teamSize.toLong(), "hs" to halfSec.toLong(), "pt" to (phaseT * 100f).toLong(), "kt" to listOf(kitIdx[0].toLong(), kitIdx[1].toLong()))
  }

  private fun ni(a: Any?): Int = (a as? Number)?.toInt() ?: 0

  fun applySnapshot(m: Map<String, Any?>) {
    (m["pl"] as? List<*>)?.forEachIndexed { i, line ->
      val p = players.getOrNull(i) ?: return@forEachIndexed
      val f = (line as? String)?.split("|") ?: return@forEachIndexed
      if (f.size < 14) return@forEachIndexed
      p.nx = f[0].toIntOrNull()?.div(100f) ?: p.x; p.ny = f[1].toIntOrNull()?.div(100f) ?: p.y
      if (!gotFirst) { p.x = p.nx; p.y = p.ny }
      p.vx = f[2].toIntOrNull()?.div(100f) ?: 0f; p.vy = f[3].toIntOrNull()?.div(100f) ?: 0f; p.face = f[4].toIntOrNull()?.div(100f) ?: p.face
      p.human = f[5] == "1"; p.uid = f[6]; p.name = f[7]; p.stunT = if (f[8] == "1") 1f else 0f; p.kickT = if (f[9] == "1") 0.2f else 0f; p.tackleT = if (f[10] == "1") 1f else 0f
      p.sprint = f[11] == "1"; p.stamina = (f[12].toIntOrNull() ?: 100) / 100f; p.diveT = if (f[13] == "1") 0.3f else 0f
      p.run += hypot(p.vx, p.vy) * 0.02f
    }
    (m["ba"] as? List<*>)?.let { l -> if (l.size >= 8) { nbx = ni(l[0]) / 100f; nby = ni(l[1]) / 100f; if (!gotFirst) { ball.x = nbx; ball.y = nby }; ball.z = ni(l[2]) / 100f; ball.vx = ni(l[3]) / 100f; ball.vy = ni(l[4]) / 100f; ball.vz = ni(l[5]) / 100f; ball.spin = ni(l[6]) / 100f; ball.owner = ni(l[7]) } }
    (m["sc"] as? List<*>)?.let { l ->
      if (l.size >= 6) {
        val gs = ni(l[4]); if (gs > goalSeq) events.add(SEvent(2, ball.x, ball.y, 1f, ni(l[5])))
        score[0] = ni(l[0]); score[1] = ni(l[1]); phase = ni(l[2]); half = ni(l[3]); goalSeq = gs; scorerTeam = ni(l[5])
      }
    }
    (m["kt"] as? List<*>)?.let { l -> if (l.size >= 2 && (ni(l[0]) != kitIdx[0] || ni(l[1]) != kitIdx[1])) setKits(ni(l[0]), ni(l[1])) }
    time = ni(m["tm"]) / 10f; msg = (m["ms"] as? String).orEmpty(); phaseT = ni(m["pt"]) / 100f
    gotFirst = true; since = 0f
  }

  /** En modo espejo, mueve el balón y los jugadores según su velocidad para que el dibujo sea fluido entre instantáneas. */
  fun extrapolate(dt: Float) {
    since += dt
    val k = min(1f, dt * 14f)
    for (p in players) { p.x += (p.nx + p.vx * since - p.x) * k; p.y += (p.ny + p.vy * since - p.y) * k; p.run += hypot(p.vx, p.vy) * dt * 1.5f }
    if (ball.owner < 0) {
      ball.x += (nbx + ball.vx * since - ball.x) * k; ball.y += (nby + ball.vy * since - ball.y) * k
      ball.vz -= 9.81f * dt; ball.z = max(0f, ball.z + ball.vz * dt); if (ball.z == 0f && ball.vz < 0f) ball.vz = 0f
    } else { val o = players.getOrNull(ball.owner); if (o != null) { ball.x += (o.x + cos(o.face) * 0.62f - ball.x) * k; ball.y += (o.y + sin(o.face) * 0.62f - ball.y) * k; ball.z = 0f } }
  }
}
