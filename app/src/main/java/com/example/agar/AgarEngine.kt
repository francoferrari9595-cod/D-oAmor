package com.example.agar

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Motor de «Células Arena»: juego de células estilo Agar.io, escrito desde cero por nosotros.
 * Las reglas y constantes siguen lo documentado públicamente sobre Agar.io y sus servidores comunitarios (Ogar/MultiOgar):
 *  - masa = tamaño² / 100 (el tamaño es el radio); se empieza con masa 10; una célula solo se come a otra con ≥ 1,25× su masa
 *    y cuando el centro de la presa queda dentro del círculo del cazador.
 *  - velocidad ∝ tamaño^-0,439 (las grandes son más lentas); la masa se pierde un 0,2 % por segundo.
 *  - dividir: mínimo 36 de masa, máximo 16 células, se reúnen tras 30 s + 0,02 × masa.
 *  - expulsar masa: mínimo 35 de masa; se pierden 16 y la bolita expulsada vale 12.
 *  - virus: masa 100 (máx. 200); una célula con ≥ 1,25× su masa lo toca y explota en pedazos; alimentarlo 7 veces crea otro virus.
 * Modos: solo, contra bots o arena compartida (un anfitrión simula y los demás reciben «instantáneas» y envían su dedo).
 */

internal const val A_MAX_CELLS = 16
internal const val A_START_MASS = 10f
internal const val A_MIN_SPLIT_MASS = 36f
internal const val A_MIN_EJECT_MASS = 35f
internal const val A_EJECT_LOSS = 16f
internal const val A_EJECT_MASS = 12f
internal const val A_VIRUS_MASS = 100f
internal const val A_VIRUS_MAX = 200f
internal const val A_VIRUS_FEED = 14.3f
internal const val A_EAT_RATIO = 1.25f
internal const val A_MAX_CELL_MASS = 22500f
internal const val A_SPLIT_DIST = 650f
internal const val A_EJECT_DIST = 480f
internal const val A_DECAY = 0.002f
internal const val A_PELLET_SIZE = 10f
internal const val A_ARENA = 7000f
internal fun aPellets(size: Float) = (size * size / 90_000f).toInt()
private const val A_GEN = 35

internal fun aSize(mass: Float) = sqrt(mass * 100f)
/** Velocidad en unidades por segundo (fórmula de los servidores comunitarios: 2,2·tamaño^-0,439 por tick de 40 ms, ×40). */
internal fun aSpeed(size: Float) = 2200f * size.pow(-0.439f)

internal class PCell(var x: Float, var y: Float, var mass: Float, val id: Int = 0) {
  var bx = 0f; var by = 0f; var boost = 0f
  var mergeAt = 0f
  var dead = false
  var nx = x; var ny = y; var nm = mass      // objetivo recibido por red (modo espejo)
  var born = 0f
  val size: Float get() = aSize(mass)
}

internal class AgarPlayer(val id: Int, var name: String, var color: Int, val bot: Boolean) {
  val cells = ArrayList<PCell>()
  var uid = ""
  var alive = false
  var tx = 0f; var ty = 0f
  var wantSplit = false
  var ejectHeld = false
  var nextEject = 0f
  var respawnAt = 0f
  var think = 0f
  var bornAt = 0f; var maxMass = 0f; var pellets = 0; var kills = 0
  var killedBy: String? = null
  var diedAt = 0f
  var lastKill = ""
  val totalMass: Float get() { var s = 0f; for (c in cells) s += c.mass; return s }
  val totalSize: Float get() { var s = 0f; for (c in cells) s += c.size; return s }
  fun cx(): Float { if (cells.isEmpty()) return 0f; var s = 0f; for (c in cells) s += c.x; return s / cells.size }
  fun cy(): Float { if (cells.isEmpty()) return 0f; var s = 0f; for (c in cells) s += c.y; return s / cells.size }
}

internal class Virus(var x: Float, var y: Float, var mass: Float = A_VIRUS_MASS, val id: Int = 0) {
  var bx = 0f; var by = 0f; var boost = 0f
  var nx = x; var ny = y; var nm = mass
  val size: Float get() = aSize(mass)
}
internal class Ejected(var x: Float, var y: Float, val color: Int, val owner: Int, var bx: Float, var by: Float, val id: Int = 0) {
  var boost = A_EJECT_DIST; var age = 0f; val mass = A_EJECT_MASS
  var nx = x; var ny = y
}

/** Eventos para los efectos visuales (partículas, ondas, sacudidas). Solo se llenan si fxOn está activo. */
internal class FxEvent(val kind: Int, val x: Float, val y: Float, val color: Int, val mass: Float)
internal const val FX_PELLET = 0
internal const val FX_EAT = 1
internal const val FX_POP = 2
internal const val FX_SPLIT = 3
internal const val FX_EJECT = 4

internal val A_BOT_NAMES = listOf(
  "Pelusa", "Tiburón", "Nebulosa", "Gordito", "Rayo", "Cometa", "Ñoño", "Bicho", "Sombra", "Turbo", "Mancha", "Burbuja", "Zorro", "Atlas", "Piraña",
  "Cacao", "Neón", "Volcán", "Lince", "Mochi", "Trueno", "Gelatina", "Fénix", "Pixel", "Cobra", "Lobo", "Mini", "Kraken", "Ámbar", "Sputnik",
  "Medusa", "Galaxia", "Tormenta", "Chispa", "Gusano", "Meteoro", "Coral", "Bruma", "Tigre", "Nácar"
)

private const val DIG = "0123456789abcdefghijklmnopqrstuvwxyz"

internal class AgarWorld(
  val size: Float = 7000f, botCount: Int = 24, val pelletTarget: Int = (size * size / 90_000f).toInt(),
  val virusTarget: Int = 16, val rnd: Random = Random.Default, val colorCount: Int = 12, val mirror: Boolean = false, val noHuman: Boolean = false
) {
  var time = 0f
  val players = ArrayList<AgarPlayer>()
  val viruses = ArrayList<Virus>()
  val ejects = ArrayList<Ejected>()
  val fx = ArrayList<FxEvent>()
  var fxOn = false
  private var nextId = 1
  private var nextObj = 1

  // comida en «casillas» deterministas: la posición depende de (casilla, generación), así se sincroniza con una cadena corta
  val px = FloatArray(pelletTarget); val py = FloatArray(pelletTarget)
  val pcol = IntArray(pelletTarget); val pgen = IntArray(pelletTarget); val palive = BooleanArray(pelletTarget)
  private val pwait = FloatArray(pelletTarget)
  private val pending = ArrayList<Int>()
  private val gridCell = 250f
  private val gw = (size / gridCell).toInt() + 1
  val grid: Array<ArrayList<Int>> = Array(gw * gw) { ArrayList<Int>() }
  val cellById = HashMap<Int, PCell>()
  private val virusById = HashMap<Int, Virus>()
  private val ejectById = HashMap<Int, Ejected>()
  val human = AgarPlayer(0, "Tú", 0, false)

  init {
    if (!mirror) {
      if (!noHuman) players.add(human)
      nextId = botCount + 1
      for (i in 0 until botCount) players.add(AgarPlayer(i + 1, A_BOT_NAMES[i % A_BOT_NAMES.size], rnd.nextInt(colorCount), true))
      for (s in 0 until pelletTarget) { pgen[s] = rnd.nextInt(A_GEN); placePellet(s); palive[s] = true; gridAdd(s) }
      for (i in 0 until virusTarget) viruses.add(Virus(rnd.nextFloat() * size, rnd.nextFloat() * size, A_VIRUS_MASS, nextObj++))
      for (p in players) if (p.bot) spawn(p)
    }
  }

  private fun gi(v: Float) = (v / gridCell).toInt().coerceIn(0, gw - 1)
  fun gridIndex(v: Float) = gi(v)
  fun gridWidth() = gw
  private fun gridAdd(s: Int) { grid[gi(py[s]) * gw + gi(px[s])].add(s) }
  private fun gridRemove(s: Int) { grid[gi(py[s]) * gw + gi(px[s])].remove(s) }

  private fun hash(a: Int, b: Int): Long {
    var z = a.toLong() * 6364136223846793005L + b.toLong() * 1442695040888963407L + 0x9E3779B9L
    z = (z xor (z ushr 30)) * -4658895280553007687L
    z = (z xor (z ushr 27)) * -7723592293110705685L
    return z xor (z ushr 31)
  }
  private fun placePellet(s: Int) {
    val z = hash(s, pgen[s])
    px[s] = ((z and 0xFFFFFF).toFloat() / 16777215f) * size
    py[s] = (((z ushr 24) and 0xFFFFFF).toFloat() / 16777215f) * size
    pcol[s] = ((z ushr 48) % colorCount).toInt()
  }

  /** Recorre las casillas de comida (vivas) dentro de un rectángulo. */
  inline fun forPellets(x0: Float, y0: Float, x1: Float, y1: Float, f: (Int) -> Unit) {
    val gx0 = gridIndex(x0); val gx1 = gridIndex(x1); val gy0 = gridIndex(y0); val gy1 = gridIndex(y1)
    val w = gridWidth()
    for (gy in gy0..gy1) for (gx in gx0..gx1) { val l = grid[gy * w + gx]; for (i in 0 until l.size) f(l[i]) }
  }

  fun newPlayer(name: String, color: Int, uid: String): AgarPlayer {
    val p = AgarPlayer(nextId++, name, color, false); p.uid = uid; players.add(p); return p
  }
  fun removePlayer(p: AgarPlayer) { p.cells.clear(); p.alive = false; players.remove(p) }

  /** Convierte un mundo «espejo» (lo recibido por red) en un mundo que se simula: así otro jugador toma el relevo de la arena. */
  fun loadFrom(src: AgarWorld) {
    time = src.time
    players.clear(); viruses.clear(); ejects.clear(); cellById.clear()
    var maxId = 0; var maxObj = 0
    for (sp in src.players) {
      val p = AgarPlayer(sp.id, sp.name, sp.color, sp.bot); p.uid = sp.uid; maxId = max(maxId, sp.id)
      for (c in sp.cells) { val nc = PCell(c.nx, c.ny, c.nm, c.id); nc.born = time; p.cells.add(nc); maxObj = max(maxObj, c.id) }
      p.alive = p.cells.isNotEmpty(); p.tx = p.cx(); p.ty = p.cy(); p.bornAt = time; p.maxMass = p.totalMass; p.kills = sp.kills; p.pellets = sp.pellets
      if (!p.alive && p.bot) p.respawnAt = time + 2f
      players.add(p)
    }
    for (v in src.viruses) { viruses.add(Virus(v.nx, v.ny, v.nm, v.id)); maxObj = max(maxObj, v.id) }
    for (e in src.ejects) { val ne = Ejected(e.nx, e.ny, e.color, -1, 0f, 0f, e.id); ne.boost = 0f; ne.age = 1f; ejects.add(ne); maxObj = max(maxObj, e.id) }
    for (g in grid) g.clear()
    pending.clear()
    for (s in 0 until min(pelletTarget, src.pelletTarget)) {
      pgen[s] = src.pgen[s]
      if (src.palive[s]) { placePellet(s); palive[s] = true; gridAdd(s) } else { palive[s] = false; pwait[s] = time + 1f + rnd.nextFloat() * 2f; pending.add(s) }
    }
    nextId = maxId + 1; nextObj = maxObj + 1
  }

  fun playerById(id: Int): AgarPlayer? { for (p in players) if (p.id == id) return p; return null }

  fun spawn(p: AgarPlayer, mass: Float = A_START_MASS) {
    var bx = rnd.nextFloat() * size; var by = rnd.nextFloat() * size
    var best = -1f
    for (t in 0 until 6) {   // elige el sitio más lejos de células grandes
      val x = size * 0.04f + rnd.nextFloat() * size * 0.92f; val y = size * 0.04f + rnd.nextFloat() * size * 0.92f
      var near = Float.MAX_VALUE
      for (q in players) for (c in q.cells) near = min(near, hypot(c.x - x, c.y - y) - c.size)
      if (near > best) { best = near; bx = x; by = y }
    }
    p.cells.clear(); p.cells.add(PCell(bx, by, mass, nextObj++).also { it.born = time })
    p.alive = true; p.tx = bx; p.ty = by; p.bornAt = time; p.maxMass = mass; p.pellets = 0; p.kills = 0; p.killedBy = null
  }

  fun leaderboard(n: Int = 10): List<AgarPlayer> = players.filter { it.alive && it.cells.isNotEmpty() }.sortedByDescending { it.totalMass }.take(n)

  private fun emit(kind: Int, x: Float, y: Float, color: Int, mass: Float) { if (fxOn && fx.size < 400) fx.add(FxEvent(kind, x, y, color, mass)) }

  // ───────────── acciones ─────────────
  fun split(p: AgarPlayer) {
    if (!p.alive) return
    for (c in ArrayList(p.cells)) {
      if (p.cells.size >= A_MAX_CELLS) break
      if (c.mass < A_MIN_SPLIT_MASS) continue
      var dx = p.tx - c.x; var dy = p.ty - c.y
      val d = hypot(dx, dy)
      if (d < 1f) { dx = 1f; dy = 0f } else { dx /= d; dy /= d }
      val wait = 30f + 0.02f * c.mass
      c.mass /= 2f
      val n = PCell(c.x + dx * c.size * 0.5f, c.y + dy * c.size * 0.5f, c.mass, nextObj++)
      n.bx = dx; n.by = dy; n.boost = A_SPLIT_DIST; n.born = time
      n.mergeAt = time + wait; c.mergeAt = time + wait
      p.cells.add(n)
      emit(FX_SPLIT, c.x, c.y, p.color, c.mass)
    }
  }

  fun eject(p: AgarPlayer) {
    if (!p.alive || time < p.nextEject) return
    p.nextEject = time + 0.12f
    for (c in p.cells) {
      if (c.mass < A_MIN_EJECT_MASS) continue
      var dx = p.tx - c.x; var dy = p.ty - c.y
      val d = hypot(dx, dy)
      if (d < 1f) { dx = 1f; dy = 0f } else { dx /= d; dy /= d }
      val a = (rnd.nextFloat() - 0.5f) * 0.12f
      val ex = dx * cos(a) - dy * sin(a); val ey = dx * sin(a) + dy * cos(a)
      c.mass -= A_EJECT_LOSS
      ejects.add(Ejected(c.x + ex * c.size, c.y + ey * c.size, p.color, p.id, ex, ey, nextObj++))
      emit(FX_EJECT, c.x + ex * c.size, c.y + ey * c.size, p.color, A_EJECT_MASS)
    }
  }

  private fun explode(c: PCell, p: AgarPlayer) {
    val slots = A_MAX_CELLS - p.cells.size
    val n = min(slots, 15)
    emit(FX_POP, c.x, c.y, p.color, c.mass)
    if (n <= 0) return
    val piece = c.mass / (n + 2)
    c.mass -= piece * n
    val wait = 15f + 0.01f * c.mass
    c.mergeAt = time + wait
    for (i in 0 until n) {
      val a = rnd.nextFloat() * 2f * PI.toFloat()
      val nc = PCell(c.x, c.y, piece, nextObj++)
      nc.bx = cos(a); nc.by = sin(a); nc.boost = A_SPLIT_DIST * (0.55f + 0.45f * rnd.nextFloat()); nc.born = time
      nc.mergeAt = time + wait
      p.cells.add(nc)
    }
  }

  // ───────────── simulación (anfitrión / solo / bots) ─────────────
  fun update(dtIn: Float) {
    val dt = dtIn.coerceIn(0f, 0.06f)
    time += dt
    for (p in players) {
      if (!p.alive) {
        if (p.bot && time >= p.respawnAt) { p.color = rnd.nextInt(colorCount); spawn(p) }
        continue
      }
      if (p.bot) aiThink(p, dt)
      if (p.wantSplit) { split(p); p.wantSplit = false }
      if (p.ejectHeld) eject(p)
      moveCells(p, dt)
    }
    val decay = 1f - 0.8889f.pow(25f * dt)
    var ei = ejects.size - 1
    while (ei >= 0) {
      val e = ejects[ei]; e.age += dt
      if (e.boost > 0.5f) { val st = e.boost * decay; e.x = (e.x + e.bx * st).coerceIn(0f, size); e.y = (e.y + e.by * st).coerceIn(0f, size); e.boost -= st }
      ei--
    }
    var vi = viruses.size - 1
    while (vi >= 0) {
      val v = viruses[vi]
      if (v.boost > 0.5f) { val st = v.boost * decay; v.x = (v.x + v.bx * st).coerceIn(0f, size); v.y = (v.y + v.by * st).coerceIn(0f, size); v.boost -= st }
      var j = ejects.size - 1
      while (j >= 0) {
        val e = ejects[j]
        if (hypot(e.x - v.x, e.y - v.y) < v.size) {
          ejects.removeAt(j); v.mass += A_VIRUS_FEED
          if (v.mass > A_VIRUS_MAX) {
            v.mass = A_VIRUS_MASS
            if (viruses.size < virusTarget * 2) { val nv = Virus(v.x, v.y, A_VIRUS_MASS, nextObj++); nv.bx = e.bx; nv.by = e.by; nv.boost = 520f; viruses.add(nv) }
          }
        }
        j--
      }
      vi--
    }
    eatPass()
    // la comida reaparece en la misma casilla (con otra posición)
    var k = pending.size - 1
    while (k >= 0) {
      val s = pending[k]
      if (time >= pwait[s]) { pgen[s] = (pgen[s] + 1) % A_GEN; placePellet(s); palive[s] = true; gridAdd(s); pending.removeAt(k) }
      k--
    }
    if (viruses.size < virusTarget && rnd.nextFloat() < dt * 0.5f) viruses.add(Virus(rnd.nextFloat() * size, rnd.nextFloat() * size, A_VIRUS_MASS, nextObj++))
    for (p in players) if (p.alive) p.maxMass = max(p.maxMass, p.totalMass)
  }

  private fun moveCells(p: AgarPlayer, dt: Float) {
    val decay = 1f - 0.8889f.pow(25f * dt)
    for (c in p.cells) {
      val dx = p.tx - c.x; val dy = p.ty - c.y
      val d = hypot(dx, dy)
      if (d > 1f) {
        val step = min(d, aSpeed(c.size) * dt)
        c.x += dx / d * step; c.y += dy / d * step
      }
      if (c.boost > 0.5f) {
        val st = c.boost * decay
        c.x += c.bx * st; c.y += c.by * st; c.boost -= st
      }
      c.mass = min(A_MAX_CELL_MASS, if (c.mass > A_START_MASS) c.mass * (1f - A_DECAY * dt) else c.mass)
    }
    var i = 0
    while (i < p.cells.size) {
      var j = i + 1
      while (j < p.cells.size) {
        val a = p.cells[i]; val b = p.cells[j]
        val d = hypot(b.x - a.x, b.y - a.y)
        val r = a.size + b.size
        if (d < r) {
          if (time >= a.mergeAt && time >= b.mergeAt) {
            val big = if (a.mass >= b.mass) a else b; val small = if (big === a) b else a
            if (d < big.size) { big.mass = min(A_MAX_CELL_MASS, big.mass + small.mass); small.dead = true }
          } else if (a.boost < 40f || b.boost < 40f) {
            val push = (r - d) * 0.5f
            val nx = if (d > 0.01f) (b.x - a.x) / d else 1f; val ny = if (d > 0.01f) (b.y - a.y) / d else 0f
            val wa = b.mass / (a.mass + b.mass); val wb = 1f - wa
            a.x -= nx * push * wa; a.y -= ny * push * wa
            b.x += nx * push * wb; b.y += ny * push * wb
          }
        }
        j++
      }
      i++
    }
    p.cells.removeAll { it.dead }
    for (c in p.cells) { val h = c.size * 0.5f; c.x = c.x.coerceIn(h, size - h); c.y = c.y.coerceIn(h, size - h) }
  }

  private fun eatPass() {
    for (p in players) {
      if (!p.alive) continue
      for (a in ArrayList(p.cells)) {   // copia: al explotar contra un virus se añaden células
        if (a.dead) continue
        val r = a.size
        val gx0 = gi(a.x - r); val gx1 = gi(a.x + r); val gy0 = gi(a.y - r); val gy1 = gi(a.y + r)
        for (gy in gy0..gy1) for (gx in gx0..gx1) {
          val l = grid[gy * gw + gx]
          var k = l.size - 1
          while (k >= 0) {
            val s = l[k]
            if (hypot(px[s] - a.x, py[s] - a.y) < r) {
              a.mass += 1f; p.pellets++
              if (!p.bot) emit(FX_PELLET, px[s], py[s], pcol[s], 1f)
              l.removeAt(k); palive[s] = false; pwait[s] = time + 1f + rnd.nextFloat() * 2.5f; pending.add(s)
            }
            k--
          }
        }
        var j = ejects.size - 1
        while (j >= 0) {
          val e = ejects[j]
          if (a.mass >= 18f && !(e.owner == p.id && e.age < 0.3f) && hypot(e.x - a.x, e.y - a.y) < r) { a.mass += e.mass; ejects.removeAt(j) }
          j--
        }
        var v = viruses.size - 1
        while (v >= 0) {
          val vi = viruses[v]
          if (a.mass >= vi.mass * A_EAT_RATIO && hypot(vi.x - a.x, vi.y - a.y) < r) {
            a.mass += vi.mass; viruses.removeAt(v); explode(a, p)
          }
          v--
        }
        for (q in players) {
          if (q === p || !q.alive) continue
          for (b in q.cells) {
            if (b.dead) continue
            if (a.mass >= b.mass * A_EAT_RATIO && hypot(b.x - a.x, b.y - a.y) < a.size) {
              a.mass = min(A_MAX_CELL_MASS, a.mass + b.mass); b.dead = true
              emit(FX_EAT, b.x, b.y, q.color, b.mass)
              if (q.cells.count { !it.dead } == 0) { q.killedBy = p.name; p.kills++; p.lastKill = q.name }
            }
          }
        }
        a.mass = min(A_MAX_CELL_MASS, a.mass)
      }
    }
    for (q in players) {
      if (!q.alive) continue
      q.cells.removeAll { it.dead }
      if (q.cells.isEmpty()) { q.alive = false; q.diedAt = time; q.respawnAt = time + 3f + rnd.nextFloat() * 4f }
    }
  }

  // ───────────── inteligencia de los bots ─────────────
  private fun aiThink(p: AgarPlayer, dt: Float) {
    p.think -= dt
    if (p.think > 0f || p.cells.isEmpty()) return
    p.think = 0.2f + rnd.nextFloat() * 0.15f
    val cx = p.cx(); val cy = p.cy(); val mass = p.totalMass
    val big = p.cells.maxByOrNull { it.mass }!!
    val sz = big.size
    var threat: PCell? = null; var threatD = Float.MAX_VALUE
    var prey: PCell? = null; var preyD = Float.MAX_VALUE
    for (q in players) {
      if (q === p || !q.alive) continue
      for (c in q.cells) {
        val d = hypot(c.x - cx, c.y - cy) - c.size
        if (c.mass >= big.mass * A_EAT_RATIO && d < 700f + c.size && d < threatD) { threat = c; threatD = d }
        else if (big.mass >= c.mass * A_EAT_RATIO && d < 900f && d < preyD) { prey = c; preyD = d }
      }
    }
    p.wantSplit = false; p.ejectHeld = false
    if (threat != null) {
      var ax = cx - threat.x; var ay = cy - threat.y
      val l = hypot(ax, ay).coerceAtLeast(1f); ax /= l; ay /= l
      p.tx = (cx + ax * 800f).coerceIn(0f, size); p.ty = (cy + ay * 800f).coerceIn(0f, size)
      if (p.tx <= 5f || p.tx >= size - 5f || p.ty <= 5f || p.ty >= size - 5f) { p.tx = size / 2f; p.ty = size / 2f }
      return
    }
    var vx = 0f; var vy = 0f
    if (mass >= 130f) for (v in viruses) {
      val d = hypot(v.x - cx, v.y - cy)
      if (d < sz + v.size + 120f) { vx += (cx - v.x) / d.coerceAtLeast(1f); vy += (cy - v.y) / d.coerceAtLeast(1f) }
    }
    if (prey != null) {
      p.tx = prey.x; p.ty = prey.y
      if (vx != 0f || vy != 0f) { p.tx += vx * 400f; p.ty += vy * 400f }
      if (preyD < sz * 2.2f && p.cells.size == 1 && big.mass / 2f >= prey.mass * A_EAT_RATIO && big.mass >= 80f && rnd.nextFloat() < 0.35f) {
        val oldTx = p.tx; val oldTy = p.ty; split(p); p.tx = oldTx; p.ty = oldTy
      }
      return
    }
    var bestD = Float.MAX_VALUE; var bx = 0f; var by = 0f; var found = false
    val range = 520f + sz
    forPellets(cx - range, cy - range, cx + range, cy + range) { s ->
      val d = hypot(px[s] - cx, py[s] - cy)
      if (d < bestD) { bestD = d; bx = px[s]; by = py[s]; found = true }
    }
    if (found) { p.tx = bx + vx * 300f; p.ty = by + vy * 300f; return }
    if (hypot(p.tx - cx, p.ty - cy) < 150f || rnd.nextFloat() < 0.15f) {
      p.tx = (cx + (rnd.nextFloat() - 0.5f) * 2400f).coerceIn(0f, size); p.ty = (cy + (rnd.nextFloat() - 0.5f) * 2400f).coerceIn(0f, size)
    }
  }

  // ───────────── sincronización en línea ─────────────
  private fun clean(s: String) = s.replace('|', ' ').replace(',', ' ').take(15)

  /** Instantánea compacta del estado para enviarla a los demás jugadores (solo la usa el anfitrión). */
  fun encode(): Map<String, Any?> {
    val meta = players.map { "${it.id}|${clean(it.name)}|${it.color}|${it.uid}" }
    val hum = players.filter { !it.bot }.map { "${it.id}|${if (it.alive) 1 else 0}|${it.kills}|${it.pellets}|${(it.maxMass * 10f).toInt()}|${(it.bornAt * 10f).toInt()}|${(it.diedAt * 10f).toInt()}|${clean(it.killedBy ?: "")}" }
    val cells = ArrayList<Long>()
    for (p in players) if (p.alive) for (c in p.cells) { cells.add(c.id.toLong()); cells.add(p.id.toLong()); cells.add(c.x.toLong()); cells.add(c.y.toLong()); cells.add((c.mass * 10f).toLong()) }
    val vir = ArrayList<Long>(); for (v in viruses) { vir.add(v.id.toLong()); vir.add(v.x.toLong()); vir.add(v.y.toLong()); vir.add(v.mass.toLong()) }
    val ej = ArrayList<Long>(); for (e in ejects) { ej.add(e.id.toLong()); ej.add(e.x.toLong()); ej.add(e.y.toLong()); ej.add(e.color.toLong()) }
    val sb = StringBuilder(pelletTarget)
    for (s in 0 until pelletTarget) sb.append(if (palive[s]) DIG[pgen[s] + 1] else '0')
    return mapOf("w" to size.toLong(), "T" to (time * 1000f).toLong(), "m" to meta, "h" to hum, "c" to cells, "v" to vir, "e" to ej, "p" to sb.toString(), "n" to pelletTarget.toLong())
  }

  private fun num(a: Any?): Float = (a as? Number)?.toFloat() ?: 0f
  private fun list(a: Any?): List<Float> = (a as? List<*>)?.map { num(it) } ?: emptyList()

  /** Aplica una instantánea recibida (modo espejo). Detecta lo que desapareció para lanzar efectos. */
  fun applySnapshot(m: Map<String, Any?>) {
    time = num(m["T"]) / 1000f
    (m["m"] as? List<*>)?.forEach { line ->
      val f = (line as? String)?.split("|") ?: return@forEach
      if (f.size < 4) return@forEach
      val id = f[0].toIntOrNull() ?: return@forEach
      val p = playerById(id) ?: AgarPlayer(id, f[1], f[2].toIntOrNull() ?: 0, f[3].isEmpty()).also { players.add(it) }
      p.name = f[1]; p.color = f[2].toIntOrNull() ?: 0; p.uid = f[3]
    }
    val seen = HashSet<Int>()
    val owners = HashSet<Int>()
    val c = list(m["c"])
    var i = 0
    while (i + 4 < c.size) {
      val id = c[i].toInt(); val owner = c[i + 1].toInt()
      val p = playerById(owner)
      if (p != null) {
        var cell = cellById[id]
        if (cell == null) { cell = PCell(c[i + 2], c[i + 3], c[i + 4] / 10f, id); cell.born = time; cellById[id] = cell; p.cells.add(cell) }
        cell.nx = c[i + 2]; cell.ny = c[i + 3]; cell.nm = c[i + 4] / 10f
        seen.add(id); owners.add(owner)
      }
      i += 5
    }
    for (p in players) {
      val had = p.cells.isNotEmpty()
      var lx = 0f; var ly = 0f; var lm = 0f
      val it = p.cells.iterator()
      while (it.hasNext()) {
        val cell = it.next()
        if (cell.id !in seen) { lx = cell.x; ly = cell.y; lm = cell.mass; it.remove() }
      }
      if (had && p.cells.isEmpty()) emit(FX_EAT, lx, ly, p.color, max(lm, 20f))
      p.alive = p.cells.isNotEmpty()
    }
    cellById.keys.retainAll(seen)
    (m["h"] as? List<*>)?.forEach { line ->
      val f = (line as? String)?.split("|") ?: return@forEach
      if (f.size < 8) return@forEach
      val p = playerById(f[0].toIntOrNull() ?: return@forEach) ?: return@forEach
      p.kills = f[2].toIntOrNull() ?: 0; p.pellets = f[3].toIntOrNull() ?: 0; p.maxMass = (f[4].toFloatOrNull() ?: 0f) / 10f
      p.bornAt = (f[5].toFloatOrNull() ?: 0f) / 10f; p.diedAt = (f[6].toFloatOrNull() ?: 0f) / 10f; p.killedBy = f[7].ifBlank { null }
    }
    val v = list(m["v"]); val vSeen = HashSet<Int>()
    i = 0
    while (i + 3 < v.size) {
      val id = v[i].toInt()
      val vir = virusById[id] ?: Virus(v[i + 1], v[i + 2], v[i + 3], id).also { virusById[id] = it; viruses.add(it) }
      vir.nx = v[i + 1]; vir.ny = v[i + 2]; vir.nm = v[i + 3]; vSeen.add(id); i += 4
    }
    val vit = viruses.iterator()
    while (vit.hasNext()) { val x = vit.next(); if (x.id !in vSeen) { emit(FX_POP, x.x, x.y, 3, x.mass); vit.remove() } }
    virusById.keys.retainAll(vSeen)
    val e = list(m["e"]); val eSeen = HashSet<Int>()
    i = 0
    while (i + 3 < e.size) {
      val id = e[i].toInt()
      val ej = ejectById[id] ?: Ejected(e[i + 1], e[i + 2], e[i + 3].toInt(), -1, 0f, 0f, id).also { ejectById[id] = it; ejects.add(it) }
      ej.nx = e[i + 1]; ej.ny = e[i + 2]; eSeen.add(id); i += 4
    }
    ejects.removeAll { it.id !in eSeen }
    ejectById.keys.retainAll(eSeen)
    // comida
    val ps = m["p"] as? String
    if (ps != null) {
      for (s in 0 until min(pelletTarget, ps.length)) {
        val ch = ps[s]
        if (ch == '0') {
          if (palive[s]) { gridRemove(s); palive[s] = false }
        } else {
          val g = DIG.indexOf(ch) - 1
          if (g >= 0 && (!palive[s] || pgen[s] != g)) {
            if (palive[s]) gridRemove(s)
            pgen[s] = g; placePellet(s); palive[s] = true; gridAdd(s)
          }
        }
      }
    }
  }

  /** Suaviza lo recibido por red hacia el último objetivo (se llama cada cuadro en el modo espejo). */
  fun smooth(dt: Float) {
    val k = 1f - exp(-dt * 13f)
    for (p in players) for (c in p.cells) { c.x += (c.nx - c.x) * k; c.y += (c.ny - c.y) * k; c.mass += (c.nm - c.mass) * k }
    for (v in viruses) { v.x += (v.nx - v.x) * k; v.y += (v.ny - v.y) * k; v.mass += (v.nm - v.mass) * k }
    for (e in ejects) { e.x += (e.nx - e.x) * k; e.y += (e.ny - e.y) * k }
  }
}
