package com.example.pong

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Motor de «Ping Pong 3D Pro»: física de la pelota con gravedad, rozamiento del aire, efecto Magnus (topspin, backspin y efecto lateral),
 * rebotes con fricción en la mesa, red, reglas oficiales de saque y puntuación, y una IA con cuatro niveles.
 * Medidas reales de la mesa (2,74 × 1,525 m, red de 15,25 cm). Eje x = ancho, y = alto, z = largo (jugador A en z negativo, B en z positivo).
 */

internal const val P_HL = 1.37f          // medio largo de la mesa
internal const val P_HW = 0.7625f        // medio ancho
internal const val P_TY = 0.76f          // altura de la mesa
internal const val P_NET = 0.1525f       // alto de la red
internal const val P_R = 0.02f           // radio de la pelota
internal const val P_PZ = 1.62f          // distancia del plano de las raquetas al centro
internal const val P_PR = 0.115f         // radio de la pala (algo generoso para jugar con el dedo)
internal const val P_REACH = 0.30f       // radio 3D de alcance de la pala de una persona (se puede avanzar hasta casi la red)
internal const val P_HIT0 = 1.30f        // la pala puede golpear la pelota entre |z| = P_HIT0 y P_HIT1 (ventana de golpeo, más justa que un plano)
internal const val P_HIT1 = 1.85f
private const val G = 9.81f
private const val MAGNUS = 0.0042f

internal class PBall { var x = 0f; var y = 1f; var z = 0f; var vx = 0f; var vy = 0f; var vz = 0f; var wx = 0f; var wy = 0f; var wz = 0f }
internal class PEvent(val kind: Int, val x: Float, val y: Float, val z: Float, val power: Float)   // 0 bote, 1 golpe, 2 red, 3 punto

private const val SERVE_H = 0.50f       // altura de la pelota al sacar (sobre la mesa)

internal const val ST_SERVE = 0
internal const val ST_RALLY = 1
internal const val ST_POINT = 2
internal const val ST_OVER = 3

private fun physStep(b: PBall, h: Float) {
  val speed = sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz)
  val drag = exp(-0.10f * speed * h * 2.2f)
  val ax_ = MAGNUS * (b.wy * b.vz - b.wz * b.vy)
  val ay_ = -G + MAGNUS * (b.wz * b.vx - b.wx * b.vz)
  val az_ = MAGNUS * (b.wx * b.vy - b.wy * b.vx)
  b.vx = (b.vx + ax_ * h) * drag; b.vy = (b.vy + ay_ * h) * drag; b.vz = (b.vz + az_ * h) * drag
  val dw = exp(-0.25f * h); b.wx *= dw; b.wy *= dw; b.wz *= dw
  b.x += b.vx * h; b.y += b.vy * h; b.z += b.vz * h
}

/** Rebote en la mesa con fricción: transfiere parte de la rotación a la velocidad horizontal. */
private fun tableBounce(b: PBall) {
  b.y = P_TY + P_R
  b.vy = -b.vy * 0.93f
  val cx = b.vx + P_R * b.wz; val cz = b.vz - P_R * b.wx
  val k = 0.28f
  b.vx -= k * cx; b.vz -= k * cz
  b.wz -= k * cx / P_R * 0.6f; b.wx += k * cz / P_R * 0.6f
}

internal class PongWorld(val mirror: Boolean = false, var level: Int = 1, var target: Int = 11, val rnd: Random = Random.Default) {
  val b = PBall()
  var ax = 0f; var ay = 1.05f; var bx = 0f; var by = 1.05f              // centro de las palas (A abajo/cerca, B lejos)
  var avx = 0f; var avy = 0f; var bvx = 0f; var bvy = 0f                // velocidad de las palas (m/s)
  var az = -P_PZ; var bz = P_PZ                                           // profundidad de cada pala (se puede avanzar hacia la red)
  var avf = 0f; var bvf = 0f                                              // velocidad de avance hacia la red (da potencia al golpe)
  var scoreA = 0; var scoreB = 0
  var state = ST_SERVE
  var server = 0
  private var firstServer = 0
  var lastHitter = 0
  var stateT = 0f
  var msg = ""
  var serveBounceOk = false
  private val sideBounces = IntArray(2)
  private var oppBounces = 0
  private var ownBounces = 0
  private var serving = false
  var rallyHits = 0
  var rallyT = 0f
  val events = ArrayList<PEvent>()
  val winner: Int get() = if (state == ST_OVER) (if (scoreA > scoreB) 0 else 1) else -1
  val matchPoint: Boolean get() = state != ST_OVER && (max(scoreA, scoreB) >= target - 1) && abs(scoreA - scoreB) >= 1 && max(scoreA, scoreB) >= target - 1
  // IA
  private var aiTx = 0f; private var aiTy = 1.05f; private var aiTz = P_PZ; private var aiT = 0f
  private var aiServeAt = 0f

  init { reset() }

  fun reset() {
    scoreA = 0; scoreB = 0; firstServer = rnd.nextInt(2); server = firstServer; startServe()
    state = ST_SERVE
  }

  private fun startServe() {
    state = ST_SERVE; stateT = 0f; msg = ""; serving = true; serveBounceOk = false
    sideBounces[0] = 0; sideBounces[1] = 0; oppBounces = 0; ownBounces = 0; lastHitter = server; rallyHits = 0; rallyT = 0f
    val sz = if (server == 0) -1f else 1f
    b.x = if (server == 0) ax else bx; b.y = P_TY + SERVE_H; b.z = sz * 1.2f
    b.vx = 0f; b.vy = 0f; b.vz = 0f; b.wx = 0f; b.wy = 0f; b.wz = 0f
    aiServeAt = 0.9f + rnd.nextFloat() * 0.8f
  }

  /**
   * Saca el jugador [side] (0 = A, 1 = B). La pelota cae en su campo, pasa la red y bota en el rival.
   * La velocidad inicial se CALCULA con la misma física del juego (bisección sobre la velocidad vertical), de modo que el saque siempre
   * rebota en su mitad, supera la red con margen y cae en la mitad contraria.
   */
  fun serve(side: Int, aimX: Float = 0f) {
    if (state != ST_SERVE || server != side) return
    val sz = if (side == 0) -1f else 1f
    val x0 = (if (side == 0) ax else bx).coerceIn(-P_HW + 0.1f, P_HW - 0.1f)
    b.x = x0; b.y = P_TY + SERVE_H; b.z = sz * 1.2f
    val tx = (-x0 * 0.5f + aimX).coerceIn(-0.55f, 0.55f)
    var sol: FloatArray? = null
    for (d in floatArrayOf(1.12f, 1.02f, 1.20f, 0.95f)) { sol = solveServe(side, x0, tx, d); if (sol != null) break }
    if (sol == null) sol = solveServe(side, x0, 0f, 1.08f)
    if (sol != null) { b.vx = sol[0]; b.vy = sol[1]; b.vz = sol[2] }
    else { b.vx = 0f; b.vy = 0.4f; b.vz = if (side == 0) 3.8f else -3.8f }   // respaldo (no debería ocurrir)
    b.wx = 0f; b.wy = 0f; b.wz = 0f
    state = ST_RALLY; stateT = 0f; lastHitter = side; serving = true; serveBounceOk = false
    sideBounces[0] = 0; sideBounces[1] = 0; oppBounces = 0; ownBounces = 0
    events.add(PEvent(1, b.x, b.y, b.z, 3f))
  }

  private fun award(w: Int, why: String) {
    if (state != ST_RALLY) return
    if (w == 0) scoreA++ else scoreB++
    msg = why; state = ST_POINT; stateT = 0f
    events.add(PEvent(3, b.x, b.y, b.z, w.toFloat()))
    val total = scoreA + scoreB
    if (max(scoreA, scoreB) >= target && abs(scoreA - scoreB) >= 2) { state = ST_OVER; return }
    server = if (scoreA >= target - 1 && scoreB >= target - 1) (firstServer + total) % 2 else (firstServer + total / 2) % 2
  }

  /** Avanza la simulación [dt] segundos. En modo espejo solo mueve la pelota (las reglas las decide el anfitrión). */
  fun step(dtIn: Float) {
    val dt = dtIn.coerceIn(0f, 0.05f)
    stateT += dt
    if (!mirror) {
      when (state) {
        ST_POINT -> { if (stateT > 1.6f) startServe() }
        ST_SERVE -> {
          // la pelota flota sobre el campo del que saca
          b.x = if (server == 0) ax else bx
          if (server == 1 && stateT > aiServeAt && !humanB) serve(1, (rnd.nextFloat() - 0.5f) * 0.5f)
        }
        ST_RALLY -> rallyT += dt
      }
      ay = followY(ay, 0, dt)
      if (humanB) by = followY(by, 1, dt) else aiMove(dt)
    }
    if (state == ST_RALLY || (mirror && (b.vx != 0f || b.vz != 0f || b.vy != 0f))) {
      val n = max(1, (dt / 0.004f).toInt() + 1); val h = dt / n
      for (i in 0 until n) { integrate(h); if (!mirror && state != ST_RALLY) break }
    }
    if (!mirror && state == ST_RALLY && rallyT > 40f) award(1 - lastHitter, "Tiempo")
  }

  var humanB = false                      // true cuando B es una persona (duelo en línea)

  /** La altura de la pala de una persona sigue sola a la pelota cuando se acerca (no hace falta controlarla). */
  fun followY(cur: Float, side: Int, dt: Float): Float {
    val coming = state == ST_RALLY && (if (side == 0) b.vz < 0f && b.z < 0.2f else b.vz > 0f && b.z > -0.2f)
    val target = if (coming) b.y.coerceIn(P_TY + 0.03f, P_TY + 0.9f) else P_TY + 0.22f
    return cur + (target - cur) * min(1f, dt * 30f)
  }

  private fun integrate(h: Float) {
    val pz = b.z
    physStep(b, h)
    // mesa
    if (b.vy < 0f && b.y - P_R <= P_TY && b.y - P_R > P_TY - 0.09f && abs(b.x) <= P_HW + P_R && abs(b.z) <= P_HL + P_R) {
      tableBounce(b)
      events.add(PEvent(0, b.x, P_TY, b.z, abs(b.vy)))
      if (!mirror) onBounce(if (b.z < 0f) 0 else 1)
    }
    // red
    if (pz * b.z < 0f && b.y - P_R < P_TY + P_NET && abs(b.x) <= P_HW + 0.15f) {
      b.vz = -b.vz * 0.15f; b.vx *= 0.3f; b.z = if (pz < 0f) -0.02f else 0.02f
      events.add(PEvent(2, b.x, b.y, b.z, 1f))
      if (!mirror) award(1 - lastHitter, "¡Red!")
      return
    }
    if (!mirror) {
      // palas: la pelota se golpea si pasa cerca de la pala dentro de la ventana de golpeo
      if (b.vz < 0f) tryHit(0)
      if (b.vz > 0f) tryHit(1)
      // fuera / suelo
      if (state == ST_RALLY && (b.y < P_R || abs(b.z) > P_PZ + 0.9f || abs(b.x) > 3.2f)) {
        if (oppBounces == 0) award(1 - lastHitter, if (serving) "Saque fallido" else "Fuera") else award(lastHitter, "¡Punto!")
      }
    } else if (b.y < P_R) { b.y = P_R; b.vy = abs(b.vy) * 0.4f; b.vx *= 0.7f; b.vz *= 0.7f }
  }

  private fun onBounce(side: Int) {
    if (state != ST_RALLY) return
    sideBounces[side]++
    if (side == lastHitter) {
      ownBounces++
      if (serving && ownBounces == 1 && !serveBounceOk) { serveBounceOk = true; return }
      award(1 - lastHitter, "Rebote en su campo")
    } else {
      oppBounces++
      serving = false
      if (oppBounces >= 2) award(lastHitter, "¡Punto!")
    }
  }

  private fun tryHit(side: Int) {
    if (state != ST_RALLY || lastHitter == side) return
    if (sideBounces[side] == 0) return                    // la pelota todavía no botó en su campo: pasa de largo (no es falta)
    val px = if (side == 0) ax else bx; val py = if (side == 0) ay else by; val pz = if (side == 0) az else bz
    val ai = side == 1 && !humanB
    val reach = if (ai) P_PR + P_R + 0.05f + 0.015f * level else P_REACH
    val dx = b.x - px; val dy = (b.y - py) * 0.6f; val dz = b.z - pz
    if (sqrt(dx * dx + dy * dy + dz * dz) > reach) return
    hit(side, px, py, if (side == 0) avx else bvx, if (side == 0) avf else bvf)
  }

  /** Golpe de la pala: dirige la pelota al campo contrario; la velocidad del movimiento de la pala da fuerza, profundidad y efecto. */
  private fun hit(side: Int, px: Float, py: Float, pvx: Float, pvf: Float) {
    val dirZ = if (side == 0) 1f else -1f
    val swing = hypot(pvx, pvf)
    val ai = side == 1 && !humanB
    var power = (4.2f + max(0f, pvf) * 0.75f + abs(pvx) * 0.45f).coerceIn(4.2f, 10.2f)
    if (ai) power = (power * (0.80f + 0.07f * level)).coerceIn(3.8f, 9.6f)
    var depth = (0.80f + 0.34f * (swing / 8f).coerceIn(0f, 1f) + (rnd.nextFloat() - 0.5f) * 0.12f).coerceIn(0.75f, 1.2f)
    var tx = if (ai) (-ax * 0.45f + (rnd.nextFloat() - 0.5f) * 0.7f) else (pvx * 0.20f + (b.x - px) * 1.2f)
    tx = tx.coerceIn(-0.68f, 0.68f)
    var allowOut = false
    if (ai) {
      val pErr = floatArrayOf(0.26f, 0.15f, 0.07f, 0.02f)[level.coerceIn(0, 3)]
      if (rnd.nextFloat() < pErr) { allowOut = true; if (rnd.nextBoolean()) depth = P_HL + 0.40f else tx = (if (tx >= 0f) 1f else -1f) * (P_HW + 0.32f) }
    }
    // efecto: topspin con el movimiento hacia arriba, lateral con el movimiento horizontal (se reduce si el cálculo no encuentra solución)
    var sol: FloatArray? = null
    for (sp in floatArrayOf(1f, 0.5f, 0f)) {
      val wx = (dirZ * pvf * 16f * sp).coerceIn(-170f, 170f); val wy = (-pvx * 16f * sp).coerceIn(-150f, 150f)
      sol = solveHit(dirZ, b.x, b.y, b.z, tx, depth, power, wx, wy, allowOut)
      if (sol != null) { b.wx = wx; b.wy = wy; break }
    }
    if (sol == null) { sol = solveHit(dirZ, b.x, b.y, b.z, 0f, 0.85f, 5f, 0f, 0f, false); b.wx = 0f; b.wy = 0f }
    if (sol != null) { b.vx = sol[0]; b.vy = sol[1]; b.vz = sol[2] }
    else { b.vx = 0f; b.vz = dirZ * 4.5f; b.vy = 2.6f }
    b.wz = 0f
    lastHitter = side; serving = false; serveBounceOk = false
    sideBounces[0] = 0; sideBounces[1] = 0; oppBounces = 0; ownBounces = 0
    rallyHits++
    events.add(PEvent(1, b.x, b.y, b.z, power + (if (swing > 5f) 2f else 0f)))
  }

  // ───────────── cálculo de tiros (usa la misma física que la partida) ─────────────
  private class Land(val x: Float, val z: Float, val t: Float, val netOk: Boolean, val fb: Float, val hasFb: Boolean)
  private val scratch = PBall()

  /** Simula la trayectoria hasta el [nb]-ésimo bote en la mesa (o hasta el suelo) y dice dónde cae y si pasa la red con margen. */
  private fun land(x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, wx: Float, wy: Float, wz: Float, nb: Int): Land {
    val s = scratch
    s.x = x; s.y = y; s.z = z; s.vx = vx; s.vy = vy; s.vz = vz; s.wx = wx; s.wy = wy; s.wz = wz
    var t = 0f; var bn = 0; var netOk = true; var fb = 0f; var hasFb = false
    val h = 0.005f
    while (t < 3.5f) {
      val pz = s.z
      physStep(s, h); t += h
      if (pz * s.z < 0f && abs(s.x) <= P_HW + 0.15f && s.y - P_R - (P_TY + P_NET) < 0.02f) netOk = false
      if (s.vy < 0f && s.y - P_R <= P_TY && s.y - P_R > P_TY - 0.09f && abs(s.x) <= P_HW + P_R && abs(s.z) <= P_HL + P_R) {
        bn++
        if (!hasFb) { fb = s.z; hasFb = true }
        tableBounce(s)
        if (bn >= nb) return Land(s.x, s.z, t, netOk, fb, hasFb)
      }
      if (s.y < P_R) return Land(s.x, s.z, t, netOk, fb, hasFb)
    }
    return Land(s.x, s.z, t, false, fb, hasFb)
  }

  /** Velocidad inicial (vx, vy, vz) con la que un golpe desde (x, y, z) cae en (tx, [depth] metros pasada la red), o null si no hay solución. */
  private fun solveHit(dirZ: Float, x: Float, y: Float, z: Float, tx: Float, depth: Float, power: Float, wx: Float, wy: Float, allowOut: Boolean): FloatArray? {
    for (f in floatArrayOf(1f, 0.86f, 0.74f, 0.63f, 0.54f, 0.46f, 0.4f)) {
      val vz = dirZ * power * f
      var vx = ((tx - x) / 0.5f).coerceIn(-4f, 4f); var vy = 0f
      for (outer in 0 until 3) {
        var lo = -1.5f; var hi = 8f
        for (k in 0 until 20) {
          vy = (lo + hi) / 2f
          val r = land(x, y, z, vx, vy, vz, wx, wy, 0f, 1)
          val lz = if (r.netOk) r.z * dirZ else -99f
          if (lz < depth) lo = vy else hi = vy
        }
        vy = (lo + hi) / 2f
        val r = land(x, y, z, vx, vy, vz, wx, wy, 0f, 1)
        if (r.t > 0.05f) vx = (vx + (tx - r.x) / r.t).coerceIn(-4f, 4f)
      }
      val r = land(x, y, z, vx, vy, vz, wx, wy, 0f, 1)
      if (!r.netOk) continue
      if (allowOut) { if (abs(r.z * dirZ - depth) < 0.3f || !r.hasFb) return floatArrayOf(vx, vy, vz); continue }
      if (r.hasFb && r.fb * dirZ > 0f && abs(r.z * dirZ - depth) < 0.12f && abs(r.x - tx) < 0.12f) return floatArrayOf(vx, vy, vz)
    }
    return null
  }

  /** Velocidad del saque: primer bote en su mitad, supera la red y segundo bote a [depth] metros del otro lado de la red. */
  private fun solveServe(side: Int, x: Float, tx: Float, depth: Float): FloatArray? {
    val dirZ = if (side == 0) 1f else -1f
    val z0 = -dirZ * 1.2f; val y0 = P_TY + SERVE_H
    for (vzm in floatArrayOf(3.4f, 3.0f, 3.8f, 2.6f, 4.2f)) {
      val vz = dirZ * vzm
      var vx = ((tx - x) / 0.9f).coerceIn(-2f, 2f)
      var bestVy = 0f; var found = false
      for (outer in 0 until 3) {
        var bestE = 9f; found = false
        var vy = -3f
        while (vy <= 3f) {
          val r = land(x, y0, z0, vx, vy, vz, 0f, 0f, 0f, 2)
          if (r.netOk && r.hasFb && r.fb * dirZ < -0.15f && abs(r.x) < P_HW && abs(r.z) < P_HL && r.z * dirZ > 0.2f) {
            val e = abs(r.z * dirZ - depth)
            if (e < bestE) { bestE = e; bestVy = vy; found = true }
          }
          vy += 0.1f
        }
        if (!found) break
        val r = land(x, y0, z0, vx, bestVy, vz, 0f, 0f, 0f, 2)
        if (r.t > 0.05f) vx = (vx + (tx - r.x) / r.t * 0.7f).coerceIn(-2f, 2f)
      }
      if (!found) continue
      val r = land(x, y0, z0, vx, bestVy, vz, 0f, 0f, 0f, 2)
      if (r.netOk && r.hasFb && r.fb * dirZ < -0.15f && abs(r.z * dirZ - depth) < 0.2f) return floatArrayOf(vx, bestVy, vz)
    }
    return null
  }

  // ───────────── inteligencia artificial de la pala B ─────────────
  private fun aiMove(dt: Float) {
    val maxSpeed = floatArrayOf(1.7f, 2.6f, 3.6f, 4.8f)[level.coerceIn(0, 3)]
    val err = floatArrayOf(0.20f, 0.11f, 0.05f, 0.015f)[level.coerceIn(0, 3)]
    aiT -= dt
    if (aiT <= 0f) {
      aiT = floatArrayOf(0.20f, 0.13f, 0.08f, 0.05f)[level.coerceIn(0, 3)]
      if (state == ST_RALLY && b.vz > 0f && lastHitter == 0) {
        val fb = predictBounceZ()
        aiTz = if (fb != null && fb < 1.0f) (fb + 0.30f).coerceIn(0.45f, P_PZ) else P_PZ
        val p = predict(aiTz)
        if (p != null) { aiTx = p.first + (rnd.nextFloat() - 0.5f) * 2f * err; aiTy = (p.second + (rnd.nextFloat() - 0.5f) * 2f * err).coerceIn(P_TY + 0.04f, 1.45f) }
      } else { aiTx = b.x.coerceIn(-0.6f, 0.6f) * 0.5f; aiTy = 1.0f; aiTz = P_PZ }
    }
    val dx = aiTx.coerceIn(-1.1f, 1.1f) - bx; val dy = aiTy - by
    val d = hypot(dx, dy)
    val st = min(d, maxSpeed * dt)
    val nvx = if (d > 0.001f) dx / d * st / dt else 0f; val nvy = if (d > 0.001f) dy / d * st / dt else 0f
    bvx += (nvx - bvx) * min(1f, dt * 14f); bvy += (nvy - bvy) * min(1f, dt * 14f)
    bx += bvx * dt; by += bvy * dt
    bz += (aiTz - bz) * min(1f, dt * 6f)
    bvf = if (state == ST_RALLY && b.vz > 0f) 1.0f + 0.7f * level else 0f   // un golpe de la IA lleva algo de avance para dar fuerza
  }

  /** Predice (x, y) de la pelota al llegar al plano z, simulando bote en mesa y gravedad con la misma física del juego. */
  private fun predict(planeZ: Float): Pair<Float, Float>? {
    val s = PBall()
    s.x = b.x; s.y = b.y; s.z = b.z; s.vx = b.vx; s.vy = b.vy; s.vz = b.vz; s.wx = b.wx; s.wy = b.wy; s.wz = b.wz
    val h = 0.01f
    var t = 0f
    while (t < 3f) {
      physStep(s, h)
      if (s.vy < 0f && s.y - P_R <= P_TY && s.y - P_R > P_TY - 0.09f && abs(s.x) <= P_HW + P_R && abs(s.z) <= P_HL + P_R) tableBounce(s)
      if (s.z >= planeZ) return s.x to s.y
      t += h
    }
    return null
  }

  /** z del primer bote de la pelota en la mitad B (o null si no botará ahí). */
  private fun predictBounceZ(): Float? {
    val s = PBall()
    s.x = b.x; s.y = b.y; s.z = b.z; s.vx = b.vx; s.vy = b.vy; s.vz = b.vz; s.wx = b.wx; s.wy = b.wy; s.wz = b.wz
    var t = 0f
    while (t < 3f) {
      physStep(s, 0.01f)
      if (s.vy < 0f && s.y - P_R <= P_TY && s.y - P_R > P_TY - 0.09f && abs(s.x) <= P_HW + P_R && abs(s.z) <= P_HL + P_R) return if (s.z > 0f) s.z else null
      t += 0.01f
    }
    return null
  }

  // ───────────── red ─────────────
  fun encode(): Map<String, Any?> = mapOf(
    "b" to listOf(b.x, b.y, b.z, b.vx, b.vy, b.vz, b.wx, b.wy, b.wz).map { (it * 1000f).toLong() },
    "p" to listOf(ax, ay, bx, by, avx, avy, bvx, bvy).map { (it * 1000f).toLong() },
    "pz" to listOf(az, bz, avf, bvf).map { (it * 1000f).toLong() },
    "sc" to listOf(scoreA.toLong(), scoreB.toLong(), state.toLong(), server.toLong(), lastHitter.toLong(), target.toLong()),
    "msg" to msg, "t" to (stateT * 1000f).toLong()
  )

  private fun nf(a: Any?): Float = ((a as? Number)?.toFloat() ?: 0f) / 1000f

  fun applySnapshot(m: Map<String, Any?>) {
    (m["b"] as? List<*>)?.let { l -> if (l.size >= 9) { b.x = nf(l[0]); b.y = nf(l[1]); b.z = nf(l[2]); b.vx = nf(l[3]); b.vy = nf(l[4]); b.vz = nf(l[5]); b.wx = nf(l[6]); b.wy = nf(l[7]); b.wz = nf(l[8]) } }
    (m["p"] as? List<*>)?.let { l -> if (l.size >= 8) { ax = nf(l[0]); ay = nf(l[1]); bx = nf(l[2]); by = nf(l[3]); avx = nf(l[4]); avy = nf(l[5]); bvx = nf(l[6]); bvy = nf(l[7]) } }
    (m["pz"] as? List<*>)?.let { l -> if (l.size >= 4) { az = nf(l[0]); bz = nf(l[1]); avf = nf(l[2]); bvf = nf(l[3]) } }
    (m["sc"] as? List<*>)?.let { l ->
      if (l.size >= 6) {
        val na = (l[0] as? Number)?.toInt() ?: 0; val nb = (l[1] as? Number)?.toInt() ?: 0
        if (na + nb > scoreA + scoreB) events.add(PEvent(3, b.x, b.y, b.z, if (na > scoreA) 0f else 1f))
        scoreA = na; scoreB = nb; state = (l[2] as? Number)?.toInt() ?: 0; server = (l[3] as? Number)?.toInt() ?: 0
        lastHitter = (l[4] as? Number)?.toInt() ?: 0; target = (l[5] as? Number)?.toInt() ?: 11
      }
    }
    msg = (m["msg"] as? String).orEmpty()
  }
}
