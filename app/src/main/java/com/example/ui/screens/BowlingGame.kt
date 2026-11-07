package com.example.ui.screens

import android.media.AudioAttributes
import com.example.model.BOWL_BALLS
import com.example.model.BowlBall
import com.example.model.Cosmetics
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.OptionChip
import com.example.ui.components.SoftButton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════════════════════
// BOWLING 3D — hecho desde cero.
//  · Física 3D de cuerpos rígidos: cada bolo es un sólido individual (peso, centro de masa, inercia, rozamiento y
//    rebote propios) que vuelca, rueda, vuela, choca y cae al foso. Idéntica en todos los dispositivos: quien tira
//    publica los parámetros y cada pantalla repite el mismo lanzamiento.
//  · Brazo humano que se balancea y suelta la bola al apretar TIRAR.
//  · Puntuación oficial de 10 frames (chuza, spare, décimo frame con hasta 3 tiros).
//  · Escena en perspectiva real con cámara que sigue a la bola, luces de neón, chispas y sonidos sintetizados.
// ═════════════════════════════════════════════════════════════════════════════

private const val BY0 = 18.29          // distancia de la línea de falta al bolo cabeza (m)
private const val BROW = 0.264         // separación entre filas de bolos (m)
private const val BSP = 0.3048         // separación lateral entre bolos (m)
private const val BHALF = 0.53         // mitad del ancho de la pista (m)
private const val BRB = 0.108          // radio de la bola (m)
private const val BMB = 6.0            // masa de la bola (kg)
private const val BFULL = 0x3FF        // los diez bolos en pie
private const val BYD = BY0 + 0.95     // fin de la tarima de bolos (m); después empieza el foso
private const val BYB = BY0 + 1.75     // cojín trasero del foso (m)
private const val BPIT = -0.28         // profundidad del foso (m)
private const val BGUT = -0.045        // profundidad de la canaleta (m)
private const val BWALL = 0.66         // paredes laterales (m)
private const val BGRAV = 9.81
private const val BDT = 1.0 / 480.0    // paso de la física
private const val BNS = 7              // esferas de colisión por bolo
private const val BNB = 8              // puntos del borde de la base
private const val BNP = BNS + BNB
private const val BRING = 0.0285       // radio de la base del bolo (m)
private const val BPINH = 0.381        // altura del bolo (m)

// Perfil de colisión del bolo: cadena de esferas sobre su eje (altura desde la base, radio).
private val BSPZ = doubleArrayOf(0.040, 0.100, 0.150, 0.205, 0.255, 0.310, 0.347)
private val BSPR = doubleArrayOf(0.036, 0.057, 0.057, 0.040, 0.0245, 0.0315, 0.030)

private val BPIN_POS: List<Pair<Double, Double>> = buildList {
  for (k in 0 until 4) for (i in 0..k) add(Pair((i - k / 2.0) * BSP, BY0 + k * BROW))
}

private fun bowlBits(mask: Int): Int = Integer.bitCount(mask and BFULL)

/** Propiedades individuales de cada bolo: peso, desgaste (centro de masa), rozamiento y rebote propios. */
private class BowlProps {
  val mass = DoubleArray(10)
  val zc = DoubleArray(10)       // altura del centro de masa sobre la base
  val invIax = DoubleArray(10)   // inercia inversa alrededor del eje del bolo
  val invItr = DoubleArray(10)   // inercia inversa alrededor de un eje transversal
  val mu = DoubleArray(10)
  val rest = DoubleArray(10)
  val lp = DoubleArray(10 * BNP * 4)   // puntos locales (x, y, z respecto al centro de masa, radio)

  init {
    val r = java.util.Random(20241104L)
    for (i in 0 until 10) {
      val m = 1.53 + 0.11 * r.nextDouble()            // 3 lb 6 oz a 3 lb 10 oz
      val dz = (r.nextDouble() - 0.5) * 0.008
      mass[i] = m
      mu[i] = 0.28 + 0.10 * r.nextDouble()
      rest[i] = 0.42 + 0.16 * r.nextDouble()
      var sw = 0.0
      var zs = 0.0
      for (s in 0 until BNS) { val w = BSPR[s] * BSPR[s] * BSPR[s]; sw += w; zs += w * BSPZ[s] }
      val z = zs / sw + dz
      zc[i] = z
      var iax = 0.0
      var itr = 0.0
      for (s in 0 until BNS) {
        val ms = m * (BSPR[s] * BSPR[s] * BSPR[s]) / sw
        iax += ms * 0.4 * BSPR[s] * BSPR[s]
        itr += ms * (0.4 * BSPR[s] * BSPR[s] + (BSPZ[s] - z) * (BSPZ[s] - z))
      }
      invIax[i] = 1.0 / iax
      invItr[i] = 1.0 / itr
      for (s in 0 until BNS) {
        val o = (i * BNP + s) * 4
        lp[o] = 0.0; lp[o + 1] = 0.0; lp[o + 2] = BSPZ[s] - z; lp[o + 3] = BSPR[s]
      }
      for (k in 0 until BNB) {
        val a = 2.0 * PI * k / BNB
        val o = (i * BNP + BNS + k) * 4
        lp[o] = BRING * StrictMath.cos(a); lp[o + 1] = BRING * StrictMath.sin(a); lp[o + 2] = -z; lp[o + 3] = 0.0
      }
    }
  }
}

private val BPROPS: BowlProps by lazy { BowlProps() }

private fun bowlFloorH(x: Double, y: Double): Double =
  if (y > BYD) BPIT else if (abs(x) > BHALF + 0.01) BGUT else 0.0

/**
 * Resultado completo de un lanzamiento: cuadros de animación a 60 fps.
 *  ball: x, y, z + cuaternión (7 por cuadro) · pins: x, y, z (centro de masa) + cuaternión por bolo (70 por cuadro)
 *  events: t, x, y, impulso, tipo (0 suelo · 1 pared · 2 bolo-bolo · 3 bola-bolo)
 */
private class BowlSim(
  val frames: Int,
  val ball: FloatArray,
  val pins: FloatArray,
  val events: List<FloatArray>,
  val startMask: Int, val endMask: Int, val gutter: Boolean, val duration: Float
)

/**
 * Motor de cuerpos rígidos 3D. Cada bolo es un sólido propio (masa, centro de masa, inercia, rozamiento y rebote
 * individuales) que cae por gravedad, vuelca cuando su centro de masa pasa el borde de la base, rueda por su
 * forma cónica, choca contra la bola, otros bolos, paredes y cojín, y se cae por el borde hacia el foso.
 * Todo con aritmética determinista (StrictMath) para que cada teléfono repita exactamente el mismo lanzamiento.
 */
private class BowlEngine(
  x0In: Double, angIn: Double, private val hook: Double, power: Double, seed: Int,
  private val mask: Int, private val bump: Boolean
) {
  private val pr = BPROPS
  private var bx = 0.0
  private var by = 0.0
  private var bz = 0.0
  private var bvx = 0.0
  private var bvy = 0.0
  private var bvz = 0.0
  private var gut = false
  private val bq = doubleArrayOf(1.0, 0.0, 0.0, 0.0)
  private val here = BooleanArray(10)
  private val asleep = BooleanArray(10)
  private val timer = DoubleArray(10)
  private val floorC = IntArray(10)
  private val imass = DoubleArray(11)
  private val pp = DoubleArray(30)
  private val qq = DoubleArray(40)
  private val vv = DoubleArray(33)
  private val ww = DoubleArray(33)
  private val rm = DoubleArray(90)
  private val wpt = DoubleArray(10 * BNP * 4)
  private var t = 0.0
  private val maxC = 700
  private var nc = 0
  private val cA = IntArray(maxC)
  private val cB = IntArray(maxC)
  private val cKind = IntArray(maxC)
  private val cN = DoubleArray(maxC * 3)
  private val cRA = DoubleArray(maxC * 3)
  private val cRB = DoubleArray(maxC * 3)
  private val cT1 = DoubleArray(maxC * 3)
  private val cT2 = DoubleArray(maxC * 3)
  private val cDepth = DoubleArray(maxC)
  private val cMu = DoubleArray(maxC)
  private val cE = DoubleArray(maxC)
  private val cKn = DoubleArray(maxC)
  private val cK1 = DoubleArray(maxC)
  private val cK2 = DoubleArray(maxC)
  private val cTv = DoubleArray(maxC)
  private val cVn0 = DoubleArray(maxC)
  private val cJn = DoubleArray(maxC)
  private val cJ1 = DoubleArray(maxC)
  private val cJ2 = DoubleArray(maxC)
  private val tmp = DoubleArray(3)
  private val rv = DoubleArray(3)
  private val events = ArrayList<FloatArray>()
  private val maxFrames = 560
  private val fBall = FloatArray(maxFrames * 7)
  private val fPins = FloatArray(maxFrames * 70)
  private var frames = 0

  init {
    val rnd = java.util.Random(seed.toLong())
    val x0 = x0In + (rnd.nextDouble() - 0.5) * 0.016
    val ang = angIn + (rnd.nextDouble() - 0.5) * 0.004
    val v = 5.6 + 3.0 * power
    bx = x0; by = 0.0; bz = BRB + 0.035
    bvx = v * StrictMath.sin(ang); bvy = v * StrictMath.cos(ang)
    for (i in 0 until 10) {
      here[i] = ((mask shr i) and 1) == 1
      asleep[i] = true
      pp[i * 3] = BPIN_POS[i].first; pp[i * 3 + 1] = BPIN_POS[i].second; pp[i * 3 + 2] = pr.zc[i]
      qq[i * 4] = 1.0
      imass[i] = 1.0 / pr.mass[i]
    }
    imass[10] = 1.0 / BMB
    for (i in 0 until 10) { updateRot(i); updateWorld(i) }
  }

  private fun updateRot(i: Int) {
    val w = qq[i * 4]; val x = qq[i * 4 + 1]; val y = qq[i * 4 + 2]; val z = qq[i * 4 + 3]
    val o = i * 9
    rm[o] = 1.0 - 2.0 * (y * y + z * z); rm[o + 1] = 2.0 * (x * y - w * z); rm[o + 2] = 2.0 * (x * z + w * y)
    rm[o + 3] = 2.0 * (x * y + w * z); rm[o + 4] = 1.0 - 2.0 * (x * x + z * z); rm[o + 5] = 2.0 * (y * z - w * x)
    rm[o + 6] = 2.0 * (x * z - w * y); rm[o + 7] = 2.0 * (y * z + w * x); rm[o + 8] = 1.0 - 2.0 * (x * x + y * y)
  }

  private fun updateWorld(i: Int) {
    val o = i * 9
    val px = pp[i * 3]; val py = pp[i * 3 + 1]; val pz = pp[i * 3 + 2]
    for (k in 0 until BNP) {
      val li = (i * BNP + k) * 4
      val lx = pr.lp[li]; val ly = pr.lp[li + 1]; val lz = pr.lp[li + 2]
      wpt[li] = px + rm[o] * lx + rm[o + 1] * ly + rm[o + 2] * lz
      wpt[li + 1] = py + rm[o + 3] * lx + rm[o + 4] * ly + rm[o + 5] * lz
      wpt[li + 2] = pz + rm[o + 6] * lx + rm[o + 7] * ly + rm[o + 8] * lz
      wpt[li + 3] = pr.lp[li + 3]
    }
  }

  /** Inercia inversa en el mundo aplicada a un vector; deja el resultado en tmp. */
  private fun iinv(i: Int, ux: Double, uy: Double, uz: Double) {
    val o = i * 9
    var lx = rm[o] * ux + rm[o + 3] * uy + rm[o + 6] * uz
    var ly = rm[o + 1] * ux + rm[o + 4] * uy + rm[o + 7] * uz
    var lz = rm[o + 2] * ux + rm[o + 5] * uy + rm[o + 8] * uz
    val a = pr.invItr[i]; val b = pr.invIax[i]
    lx *= a; ly *= a; lz *= b
    tmp[0] = rm[o] * lx + rm[o + 1] * ly + rm[o + 2] * lz
    tmp[1] = rm[o + 3] * lx + rm[o + 4] * ly + rm[o + 5] * lz
    tmp[2] = rm[o + 6] * lx + rm[o + 7] * ly + rm[o + 8] * lz
  }

  private fun invMass(b: Int): Double = if (b < 0) 0.0 else imass[b]

  private fun kdir(c: Int, dx: Double, dy: Double, dz: Double): Double {
    val a = cA[c]; val b = cB[c]
    var k = invMass(a) + invMass(b)
    if (a in 0..9) {
      val rx = cRA[c * 3]; val ry = cRA[c * 3 + 1]; val rz = cRA[c * 3 + 2]
      iinv(a, ry * dz - rz * dy, rz * dx - rx * dz, rx * dy - ry * dx)
      val wx = tmp[1] * rz - tmp[2] * ry; val wy = tmp[2] * rx - tmp[0] * rz; val wz = tmp[0] * ry - tmp[1] * rx
      k += wx * dx + wy * dy + wz * dz
    }
    if (b in 0..9) {
      val rx = cRB[c * 3]; val ry = cRB[c * 3 + 1]; val rz = cRB[c * 3 + 2]
      iinv(b, ry * dz - rz * dy, rz * dx - rx * dz, rx * dy - ry * dx)
      val wx = tmp[1] * rz - tmp[2] * ry; val wy = tmp[2] * rx - tmp[0] * rz; val wz = tmp[0] * ry - tmp[1] * rx
      k += wx * dx + wy * dy + wz * dz
    }
    return k
  }

  private fun relVel(c: Int) {
    val a = cA[c]; val b = cB[c]
    val rbx = cRB[c * 3]; val rby = cRB[c * 3 + 1]; val rbz = cRB[c * 3 + 2]
    var ux = vv[b * 3] + ww[b * 3 + 1] * rbz - ww[b * 3 + 2] * rby
    var uy = vv[b * 3 + 1] + ww[b * 3 + 2] * rbx - ww[b * 3] * rbz
    var uz = vv[b * 3 + 2] + ww[b * 3] * rby - ww[b * 3 + 1] * rbx
    if (a >= 0) {
      val rax = cRA[c * 3]; val ray = cRA[c * 3 + 1]; val raz = cRA[c * 3 + 2]
      ux -= vv[a * 3] + ww[a * 3 + 1] * raz - ww[a * 3 + 2] * ray
      uy -= vv[a * 3 + 1] + ww[a * 3 + 2] * rax - ww[a * 3] * raz
      uz -= vv[a * 3 + 2] + ww[a * 3] * ray - ww[a * 3 + 1] * rax
    }
    rv[0] = ux; rv[1] = uy; rv[2] = uz
  }

  private fun impulse(c: Int, jx: Double, jy: Double, jz: Double) {
    val a = cA[c]; val b = cB[c]
    if (a >= 0) {
      val m = imass[a]
      vv[a * 3] -= m * jx; vv[a * 3 + 1] -= m * jy; vv[a * 3 + 2] -= m * jz
      if (a < 10) {
        val rx = cRA[c * 3]; val ry = cRA[c * 3 + 1]; val rz = cRA[c * 3 + 2]
        iinv(a, ry * jz - rz * jy, rz * jx - rx * jz, rx * jy - ry * jx)
        ww[a * 3] -= tmp[0]; ww[a * 3 + 1] -= tmp[1]; ww[a * 3 + 2] -= tmp[2]
      }
    }
    if (b >= 0) {
      val m = imass[b]
      vv[b * 3] += m * jx; vv[b * 3 + 1] += m * jy; vv[b * 3 + 2] += m * jz
      if (b < 10) {
        val rx = cRB[c * 3]; val ry = cRB[c * 3 + 1]; val rz = cRB[c * 3 + 2]
        iinv(b, ry * jz - rz * jy, rz * jx - rx * jz, rx * jy - ry * jx)
        ww[b * 3] += tmp[0]; ww[b * 3 + 1] += tmp[1]; ww[b * 3 + 2] += tmp[2]
      }
    }
  }

  private fun addC(
    a: Int, b: Int, nx: Double, ny: Double, nz: Double,
    rax: Double, ray: Double, raz: Double, rbx: Double, rby: Double, rbz: Double,
    depth: Double, mu: Double, e: Double, kind: Int
  ) {
    if (nc >= maxC) return
    val c = nc++
    cA[c] = a; cB[c] = b; cKind[c] = kind
    cN[c * 3] = nx; cN[c * 3 + 1] = ny; cN[c * 3 + 2] = nz
    cRA[c * 3] = rax; cRA[c * 3 + 1] = ray; cRA[c * 3 + 2] = raz
    cRB[c * 3] = rbx; cRB[c * 3 + 1] = rby; cRB[c * 3 + 2] = rbz
    cDepth[c] = depth; cMu[c] = mu; cE[c] = e
    cJn[c] = 0.0; cJ1[c] = 0.0; cJ2[c] = 0.0
  }

  private fun wake(i: Int) { if (asleep[i]) { asleep[i] = false; timer[i] = 0.0 } }

  private fun buildContacts() {
    nc = 0
    // suelo, paredes y cojín contra los bolos despiertos
    for (i in 0 until 10) {
      if (!here[i] || asleep[i]) continue
      val px = pp[i * 3]; val py = pp[i * 3 + 1]; val pz = pp[i * 3 + 2]
      for (k in 0 until BNP) {
        val li = (i * BNP + k) * 4
        val wx = wpt[li]; val wy = wpt[li + 1]; val wz = wpt[li + 2]; val r = wpt[li + 3]
        val rx = wx - px; val ry = wy - py; val rz = wz - pz
        var d = bowlFloorH(wx, wy) - (wz - r)
        if (d > -0.0015) addC(-1, i, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, rx, ry, rz - r, d, pr.mu[i], 0.25, 0)
        d = -BWALL - (wx - r)
        if (d > -0.0015) addC(-1, i, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, rx - r, ry, rz, d, 0.2, 0.35, 1)
        d = (wx + r) - BWALL
        if (d > -0.0015) addC(-1, i, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, rx + r, ry, rz, d, 0.2, 0.35, 1)
        d = (wy + r) - BYB
        if (d > -0.0015) addC(-1, i, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, rx, ry + r, rz, d, 0.5, 0.10, 1)
      }
    }
    // bolo contra bolo
    for (i in 0 until 10) {
      if (!here[i]) continue
      for (k in i + 1 until 10) {
        if (!here[k]) continue
        if (asleep[i] && asleep[k]) continue
        val dx = pp[k * 3] - pp[i * 3]; val dy = pp[k * 3 + 1] - pp[i * 3 + 1]; val dz = pp[k * 3 + 2] - pp[i * 3 + 2]
        if (dx * dx + dy * dy + dz * dz > 0.46 * 0.46) continue
        for (s in 0 until BNS) {
          val ia = (i * BNP + s) * 4
          val ax = wpt[ia]; val ay = wpt[ia + 1]; val az = wpt[ia + 2]; val ar = wpt[ia + 3]
          for (u in 0 until BNS) {
            val ib = (k * BNP + u) * 4
            val ex = wpt[ib] - ax; val ey = wpt[ib + 1] - ay; val ez = wpt[ib + 2] - az
            val dd = ex * ex + ey * ey + ez * ez
            val br = wpt[ib + 3]
            val mn = ar + br
            if (dd < mn * mn && dd > 1e-12) {
              val dist = sqrt(dd)
              val nx = ex / dist; val ny = ey / dist; val nz = ez / dist
              val depth = mn - dist
              if (depth > 0.0003) { wake(i); wake(k) }
              val fa = ar - depth / 2.0; val fb = br - depth / 2.0
              addC(
                i, k, nx, ny, nz,
                ax - pp[i * 3] + nx * fa, ay - pp[i * 3 + 1] + ny * fa, az - pp[i * 3 + 2] + nz * fa,
                wpt[ib] - pp[k * 3] - nx * fb, wpt[ib + 1] - pp[k * 3 + 1] - ny * fb, wpt[ib + 2] - pp[k * 3 + 2] - nz * fb,
                depth, 0.3, (pr.rest[i] + pr.rest[k]) * 0.5, 2
              )
            }
          }
        }
      }
    }
    // bola contra bolos
    if (!gut && by > BY0 - 0.7 && by < BYD + 0.25) {
      for (k in 0 until 10) {
        if (!here[k]) continue
        val dx = pp[k * 3] - bx; val dy = pp[k * 3 + 1] - by; val dz = pp[k * 3 + 2] - bz
        val lim = 0.42 + BRB
        if (dx * dx + dy * dy + dz * dz > lim * lim) continue
        for (u in 0 until BNS) {
          val ib = (k * BNP + u) * 4
          val ex = wpt[ib] - bx; val ey = wpt[ib + 1] - by; val ez = wpt[ib + 2] - bz
          val dd = ex * ex + ey * ey + ez * ez
          val br = wpt[ib + 3]
          val mn = BRB + br
          if (dd < mn * mn && dd > 1e-12) {
            val dist = sqrt(dd)
            val nx = ex / dist; val ny = ey / dist; val nz = ez / dist
            val depth = mn - dist
            if (depth > 0.0003) wake(k)
            val fa = BRB - depth / 2.0; val fb = br - depth / 2.0
            addC(
              10, k, nx, ny, nz,
              nx * fa, ny * fa, nz * fa,
              wpt[ib] - pp[k * 3] - nx * fb, wpt[ib + 1] - pp[k * 3 + 1] - ny * fb, wpt[ib + 2] - pp[k * 3 + 2] - nz * fb,
              depth, 0.25, 0.62, 3
            )
          }
        }
      }
    }
  }

  private fun step() {
    val dt = BDT
    // ── bola: fuerzas ──
    if (by > BYD + 0.2) bvx = 0.0
    if (!gut && by < BYD) {
      if (by > 11.0) bvx += hook * 0.9 * minOf(1.0, (by - 11.0) / 7.0) * dt
      if (abs(bx) > BHALF && by < BY0 - 0.5) {
        if (bump) { bx = if (bx > 0) BHALF else -BHALF; bvx = -bvx * 0.5 }
        else { gut = true; bvx = 0.0; bx = if (bx > 0) 0.61 else -0.61 }
      }
    }
    val onFloor = bz <= BRB + bowlFloorH(bx, by) + 0.002
    if (onFloor) bvy -= 0.12 * dt
    bvz -= BGRAV * dt
    // ── bolos: gravedad y giro libre ──
    for (i in 0 until 10) {
      if (!here[i] || asleep[i]) continue
      vv[i * 3 + 2] -= BGRAV * dt
      val o = i * 9
      val wx = ww[i * 3]; val wy = ww[i * 3 + 1]; val wz = ww[i * 3 + 2]
      var lx = rm[o] * wx + rm[o + 3] * wy + rm[o + 6] * wz
      var ly = rm[o + 1] * wx + rm[o + 4] * wy + rm[o + 7] * wz
      var lz = rm[o + 2] * wx + rm[o + 5] * wy + rm[o + 8] * wz
      val itr = 1.0 / pr.invItr[i]; val iax = 1.0 / pr.invIax[i]
      val lax = itr * lx; val lay = itr * ly; val laz = iax * lz
      val tx = -(ly * laz - lz * lay); val ty = -(lz * lax - lx * laz); val tz = -(lx * lay - ly * lax)
      lx += tx * pr.invItr[i] * dt; ly += ty * pr.invItr[i] * dt; lz += tz * pr.invIax[i] * dt
      ww[i * 3] = rm[o] * lx + rm[o + 1] * ly + rm[o + 2] * lz
      ww[i * 3 + 1] = rm[o + 3] * lx + rm[o + 4] * ly + rm[o + 5] * lz
      ww[i * 3 + 2] = rm[o + 6] * lx + rm[o + 7] * ly + rm[o + 8] * lz
    }
    // la bola rueda sin deslizar: su giro solo sirve para el rozamiento contra los bolos
    ww[30] = -bvy / BRB; ww[31] = bvx / BRB; ww[32] = 0.0
    vv[30] = bvx; vv[31] = bvy; vv[32] = bvz

    buildContacts()
    // ── preparar contactos ──
    for (c in 0 until nc) {
      val nx = cN[c * 3]; val ny = cN[c * 3 + 1]; val nz = cN[c * 3 + 2]
      val ax: Double; val ay: Double; val az: Double
      if (abs(nx) < 0.9) { ax = 1.0; ay = 0.0; az = 0.0 } else { ax = 0.0; ay = 1.0; az = 0.0 }
      var t1x = ny * az - nz * ay; var t1y = nz * ax - nx * az; var t1z = nx * ay - ny * ax
      val l = sqrt(t1x * t1x + t1y * t1y + t1z * t1z)
      t1x /= l; t1y /= l; t1z /= l
      val t2x = ny * t1z - nz * t1y; val t2y = nz * t1x - nx * t1z; val t2z = nx * t1y - ny * t1x
      cT1[c * 3] = t1x; cT1[c * 3 + 1] = t1y; cT1[c * 3 + 2] = t1z
      cT2[c * 3] = t2x; cT2[c * 3 + 1] = t2y; cT2[c * 3 + 2] = t2z
      cKn[c] = 1.0 / kdir(c, nx, ny, nz)
      cK1[c] = 1.0 / kdir(c, t1x, t1y, t1z)
      cK2[c] = 1.0 / kdir(c, t2x, t2y, t2z)
      relVel(c)
      val vn0 = rv[0] * nx + rv[1] * ny + rv[2] * nz
      val bounce = if (vn0 < -1.0) -cE[c] * vn0 else 0.0
      val beta = if (cKind[c] < 3) 0.2 else 0.1
      val bias = minOf(beta / dt * maxOf(cDepth[c] - 0.0006, 0.0), 1.5)
      cTv[c] = maxOf(bounce, bias)
      cVn0[c] = vn0
    }
    // ── resolver impulsos ──
    for (iter in 0 until 8) {
      for (c in 0 until nc) {
        val nx = cN[c * 3]; val ny = cN[c * 3 + 1]; val nz = cN[c * 3 + 2]
        relVel(c)
        val vn = rv[0] * nx + rv[1] * ny + rv[2] * nz
        var j = (cTv[c] - vn) * cKn[c]
        val nj = maxOf(cJn[c] + j, 0.0)
        j = nj - cJn[c]; cJn[c] = nj
        impulse(c, j * nx, j * ny, j * nz)
        val lim = cMu[c] * cJn[c]
        for (w in 0 until 2) {
          val tx = if (w == 0) cT1[c * 3] else cT2[c * 3]
          val ty = if (w == 0) cT1[c * 3 + 1] else cT2[c * 3 + 1]
          val tz = if (w == 0) cT1[c * 3 + 2] else cT2[c * 3 + 2]
          relVel(c)
          val vt = rv[0] * tx + rv[1] * ty + rv[2] * tz
          val acc = if (w == 0) cJ1[c] else cJ2[c]
          val jt = -vt * (if (w == 0) cK1[c] else cK2[c])
          val nt = maxOf(-lim, minOf(lim, acc + jt))
          val dj = nt - acc
          if (w == 0) cJ1[c] = nt else cJ2[c] = nt
          impulse(c, dj * tx, dj * ty, dj * tz)
        }
      }
    }
    // ── marcar apoyo en el suelo y registrar choques ──
    for (i in 0 until 10) floorC[i] = 0
    for (c in 0 until nc) {
      val kind = cKind[c]
      if (kind == 0) {
        val b = cB[c]
        floorC[b] = if (pp[b * 3 + 1] > BYD || abs(pp[b * 3]) > BHALF + 0.01) 2 else 1
      }
      val vn0 = cVn0[c]
      if (vn0 < -0.35) {
        val sp = -vn0
        if (kind == 0 && sp < 0.6) continue
        val imp = cJn[c]
        val b = cB[c]
        val ex = if (b < 10) pp[b * 3] else bx
        val ey = if (b < 10) pp[b * 3 + 1] else by
        if (events.size < 500) events.add(floatArrayOf(t.toFloat(), ex.toFloat(), ey.toFloat(), imp.toFloat(), kind.toFloat()))
      }
    }
    // ── integrar bolos ──
    for (i in 0 until 10) {
      if (!here[i] || asleep[i]) continue
      val fc = floorC[i]
      val dl = 1.0 - (if (fc == 0) 0.04 else if (fc == 1) 0.7 else 3.0) * dt
      val da = 1.0 - (if (fc == 0) 0.15 else if (fc == 1) 0.9 else 4.0) * dt
      for (k in 0 until 3) { vv[i * 3 + k] *= dl; ww[i * 3 + k] *= da }
      pp[i * 3] += vv[i * 3] * dt; pp[i * 3 + 1] += vv[i * 3 + 1] * dt; pp[i * 3 + 2] += vv[i * 3 + 2] * dt
      val wx = ww[i * 3]; val wy = ww[i * 3 + 1]; val wz = ww[i * 3 + 2]
      val qw = qq[i * 4]; val qx = qq[i * 4 + 1]; val qy = qq[i * 4 + 2]; val qz = qq[i * 4 + 3]
      val nqw = qw + 0.5 * dt * (-(wx * qx + wy * qy + wz * qz))
      val nqx = qx + 0.5 * dt * (wx * qw + wy * qz - wz * qy)
      val nqy = qy + 0.5 * dt * (wy * qw + wz * qx - wx * qz)
      val nqz = qz + 0.5 * dt * (wz * qw + wx * qy - wy * qx)
      val l = sqrt(nqw * nqw + nqx * nqx + nqy * nqy + nqz * nqz)
      qq[i * 4] = nqw / l; qq[i * 4 + 1] = nqx / l; qq[i * 4 + 2] = nqy / l; qq[i * 4 + 3] = nqz / l
      updateRot(i)
      updateWorld(i)
      val vx = vv[i * 3]; val vy = vv[i * 3 + 1]; val vz = vv[i * 3 + 2]
      val sp = sqrt(vx * vx + vy * vy + vz * vz)
      if (sp < 0.04 && sqrt(wx * wx + wy * wy + wz * wz) < 0.25) {
        timer[i] += dt
        if (timer[i] > 0.45) {
          asleep[i] = true
          vv[i * 3] = 0.0; vv[i * 3 + 1] = 0.0; vv[i * 3 + 2] = 0.0
          ww[i * 3] = 0.0; ww[i * 3 + 1] = 0.0; ww[i * 3 + 2] = 0.0
        }
      } else timer[i] = 0.0
    }
    // ── integrar bola ──
    bvx = vv[30]; bvy = vv[31]; bvz = vv[32]
    if (gut) bvx = 0.0
    bx += bvx * dt; by += bvy * dt; bz += bvz * dt
    val fh = bowlFloorH(bx, by)
    if (bz < BRB + fh) {
      bz = BRB + fh
      bvz = if (bvz < -0.5) -bvz * 0.12 else 0.0
    }
    if (bx > BWALL - BRB) { bx = BWALL - BRB; bvx = -abs(bvx) * 0.3 }
    if (bx < -BWALL + BRB) { bx = -BWALL + BRB; bvx = abs(bvx) * 0.3 }
    if (by > BYB - BRB) { by = BYB - BRB; bvy = 0.0; bvx *= 0.5 }
    // orientación de la bola (rodadura)
    val sx = -bvy / BRB; val sy = bvx / BRB
    val qw = bq[0]; val qx = bq[1]; val qy = bq[2]; val qz = bq[3]
    val nw = qw + 0.5 * dt * (-(sx * qx + sy * qy))
    val nx = qx + 0.5 * dt * (sx * qw - sy * qz)
    val ny = qy + 0.5 * dt * (sy * qw + sx * qz)
    val nz = qz + 0.5 * dt * (sx * qy - sy * qx)
    val ln = sqrt(nw * nw + nx * nx + ny * ny + nz * nz)
    bq[0] = nw / ln; bq[1] = nx / ln; bq[2] = ny / ln; bq[3] = nz / ln
    t += dt
  }

  private fun standing(i: Int): Boolean {
    val x = qq[i * 4 + 1]; val y = qq[i * 4 + 2]
    val az = 1.0 - 2.0 * (x * x + y * y)
    return az > 0.93 && pp[i * 3 + 2] > 0.10 && pp[i * 3 + 1] < BYD - 0.02 && abs(pp[i * 3]) < 0.60
  }

  private fun record() {
    if (frames >= maxFrames) return
    val bo = frames * 7
    fBall[bo] = bx.toFloat(); fBall[bo + 1] = by.toFloat(); fBall[bo + 2] = bz.toFloat()
    fBall[bo + 3] = bq[0].toFloat(); fBall[bo + 4] = bq[1].toFloat(); fBall[bo + 5] = bq[2].toFloat(); fBall[bo + 6] = bq[3].toFloat()
    for (i in 0 until 10) {
      val po = frames * 70 + i * 7
      fPins[po] = pp[i * 3].toFloat(); fPins[po + 1] = pp[i * 3 + 1].toFloat(); fPins[po + 2] = pp[i * 3 + 2].toFloat()
      fPins[po + 3] = qq[i * 4].toFloat(); fPins[po + 4] = qq[i * 4 + 1].toFloat()
      fPins[po + 5] = qq[i * 4 + 2].toFloat(); fPins[po + 6] = qq[i * 4 + 3].toFloat()
    }
    frames++
  }

  fun run(): BowlSim {
    var n = 0
    var rest = 0
    val maxT = 9.0
    while (t < maxT && frames < maxFrames) {
      step()
      n++
      if (n % 8 == 0) record()
      var allAsleep = true
      for (i in 0 until 10) if (here[i] && !asleep[i]) { allAsleep = false; break }
      if (allAsleep && by > BYD - 0.05) rest++ else rest = 0
      if (rest > 120) break
    }
    if (frames < 2) { record(); record() }
    var end = 0
    for (i in 0 until 10) if (here[i] && standing(i)) end = end or (1 shl i)
    return BowlSim(frames, fBall.copyOf(frames * 7), fPins.copyOf(frames * 70), events, mask and BFULL, end, gut, (frames - 1) / 60f)
  }
}

private fun bowlSimulate(x0: Double, ang: Double, hook: Double, power: Double, seed: Int, mask: Int, bump: Boolean): BowlSim =
  BowlEngine(x0, ang, hook, power, seed, mask, bump).run()

/** Última simulación calculada: evita repetirla en el teléfono de quien tiró. */
private var bowlSimCache: Pair<String, BowlSim>? = null
private fun bowlSimKey(x0: Double, ang: Double, hook: Double, power: Double, seed: Int, mask: Int, bump: Boolean): String =
  "$x0|$ang|$hook|$power|$seed|$mask|$bump"

/** Trayectoria prevista (sin ruido) para la guía de puntería: pares x, y cada 0,2 m aprox. */
private fun bowlPredict(x0: Double, ang: Double, hook: Double, power: Double, bump: Boolean): List<Pair<Float, Float>> {
  val out = ArrayList<Pair<Float, Float>>()
  val v = 5.6 + 3.0 * power
  var bx = x0; var by = 0.0; var bvx = v * sin(ang); var bvy = v * cos(ang)
  val dt = 1.0 / 60.0
  var last = -1.0
  var n = 0
  while (by < BY0 - 0.3 && n < 900) {
    if (by > 11.0) bvx += hook * 0.9 * minOf(1.0, (by - 11.0) / 7.0) * dt
    if (abs(bx) > BHALF) { if (bump) { bx = if (bx > 0) BHALF else -BHALF; bvx = -bvx * 0.5 } else break }
    bvy -= 0.12 * dt
    bx += bvx * dt; by += bvy * dt
    if (by - last >= 0.2) { out.add(Pair(bx.toFloat(), by.toFloat())); last = by }
    n++
  }
  return out
}

// ───────────────────────────── Puntuación oficial ─────────────────────────────

private class BowlFrame(val marks: List<String>, val total: Int?)
private class BowlProg(val frame: Int, val ball: Int, val finished: Boolean)

private fun bowlMark(n: Int): String = if (n == 0) "-" else "$n"

/** Marcas y puntaje acumulado de los 10 frames a partir de la lista de pines derribados por tiro. */
private fun bowlFrames(r: List<Int>): List<BowlFrame> {
  val frames = ArrayList<BowlFrame>()
  var i = 0
  var cum = 0
  for (f in 0 until 10) {
    if (i >= r.size) { frames.add(BowlFrame(emptyList(), null)); continue }
    if (f < 9) {
      if (r[i] == 10) {
        val n1 = r.getOrNull(i + 1); val n2 = r.getOrNull(i + 2)
        val tot = if (n1 != null && n2 != null) { cum += 10 + n1 + n2; cum } else null
        frames.add(BowlFrame(listOf("X"), tot)); i += 1
      } else {
        val a = r[i]; val b = r.getOrNull(i + 1)
        if (b == null) { frames.add(BowlFrame(listOf(bowlMark(a)), null)); i += 1 }
        else if (a + b == 10) {
          val n = r.getOrNull(i + 2)
          val tot = if (n != null) { cum += 10 + n; cum } else null
          frames.add(BowlFrame(listOf(bowlMark(a), "/"), tot)); i += 2
        } else {
          cum += a + b
          frames.add(BowlFrame(listOf(bowlMark(a), bowlMark(b)), cum)); i += 2
        }
      }
    } else {
      val rest = r.drop(i)
      val a = rest[0]; val b = rest.getOrNull(1); val c3 = rest.getOrNull(2)
      val marks = ArrayList<String>()
      marks.add(if (a == 10) "X" else bowlMark(a))
      if (b != null) marks.add(if (a == 10) (if (b == 10) "X" else bowlMark(b)) else if (a + b == 10) "/" else bowlMark(b))
      if (c3 != null && b != null) {
        marks.add(
          when {
            a == 10 && b == 10 -> if (c3 == 10) "X" else bowlMark(c3)
            a == 10 -> if (b + c3 == 10) "/" else bowlMark(c3)
            else -> if (c3 == 10) "X" else bowlMark(c3)
          }
        )
      }
      val complete = rest.size >= 3 || (rest.size == 2 && a < 10 && a + (b ?: 0) < 10)
      frames.add(BowlFrame(marks, if (complete) cum + rest.sum() else null))
    }
  }
  return frames
}

private fun bowlTotal(r: List<Int>): Int = bowlFrames(r).lastOrNull { it.total != null }?.total ?: 0

/** En qué frame está el jugador, qué número de tiro sigue (0 = primero) y si ya terminó sus 10 frames. */
private fun bowlProg(r: List<Int>): BowlProg {
  var i = 0
  for (f in 0 until 9) {
    if (i >= r.size) return BowlProg(f, 0, false)
    if (r[i] == 10) { i += 1; continue }
    if (i + 1 >= r.size) return BowlProg(f, 1, false)
    i += 2
  }
  val rest = r.drop(i)
  return when {
    rest.isEmpty() -> BowlProg(9, 0, false)
    rest.size == 1 -> BowlProg(9, 1, false)
    rest.size == 2 -> if (rest[0] == 10 || rest[0] + rest[1] == 10) BowlProg(9, 2, false) else BowlProg(9, 2, true)
    else -> BowlProg(9, 3, true)
  }
}

private class BowlLane(val name: String, val hookK: Double, val hint: String)
private val BOWL_LANES = listOf(
  BowlLane("Normal", 1.0, "Efecto estándar"),
  BowlLane("Aceitada", 0.45, "Pista resbalosa: la bola curva poco"),
  BowlLane("Efecto extremo", 1.8, "La bola curva muchísimo")
)

/** Pantalla LED sobre la pista: baja desde arriba al cambiar de turno y muestra jugador, frame y puntos. */
@Composable
private fun BowlLedBoard(name: String, frame: Int, ball: Int, total: Int, text: String, key: Int, modifier: Modifier = Modifier) {
  val slide = remember { Animatable(0f) }
  LaunchedEffect(key, name) { slide.snapTo(0f); slide.animateTo(1f, spring(0.55f, 180f)) }
  val blink by rememberInfiniteTransition(label = "led").animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "ledB")
  val led = Color(0xFF39FF88)
  Column(
    modifier.graphicsLayer { translationY = (slide.value - 1f) * 190f }
      .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).background(Color(0xE6050A07))
      .border(1.5.dp, led.copy(alpha = 0.55f), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
      .padding(horizontal = 14.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(name.uppercase(), color = led.copy(alpha = blink), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
    Text("FRAME ${frame.coerceIn(1, 10)} · TIRO ${ball.coerceAtLeast(1)}   |   PTS $total", color = Color(0xFFFFC857), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    if (text.isNotBlank()) Text(text, color = Color(0xFFFF6B8A), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
  }
}

// ───────────────────────────── Sonidos sintetizados ─────────────────────────────

/** Sonidos del bowling generados por código (sin archivos): bola rodando y bolos cayendo. */
private object BowlSfx {
  private const val SR = 22050

  private fun noiseBuffer(seconds: Float, shape: (Float) -> Float, lowpass: Float, tone: Float, toneHz: Float): ShortArray {
    val n = (SR * seconds).toInt()
    val out = ShortArray(n)
    val rnd = java.util.Random(7)
    var lp = 0f
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      lp += (rnd.nextFloat() * 2f - 1f - lp) * lowpass
      val s = (lp * (1f - tone) + sin(2.0 * PI * toneHz * t).toFloat() * tone) * shape(t / seconds)
      out[i] = (s.coerceIn(-1f, 1f) * 30000f).toInt().toShort()
    }
    return out
  }

  /** Bola rodando por la madera: ruido grave que se hace más agudo al acercarse a los bolos. */
  val rumble: ShortArray by lazy { noiseBuffer(2.6f, { x -> (if (x < 0.1f) x * 10f else 1f) * (0.55f + 0.45f * x) * (if (x > 0.92f) (1f - x) * 12f else 1f) }, 0.06f, 0.18f, 52f) }

  /** Estruendo de bolos: golpe seco, madera chocando y cola que se apaga. */
  val crash: ShortArray by lazy {
    val n = (SR * 1.6f).toInt()
    val out = ShortArray(n)
    val rnd = java.util.Random(11)
    var lp = 0f
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      lp += (rnd.nextFloat() * 2f - 1f - lp) * 0.55f
      val body = lp * kotlin.math.exp(-t * 3.2f)
      val clack = (sin(2.0 * PI * (900 + 400 * sin(t * 30.0)) * t).toFloat() + sin(2.0 * PI * 2300 * t).toFloat() * 0.5f) *
        kotlin.math.exp(-t * 9f) * (0.5f + 0.5f * sin(t * 70f))
      out[i] = ((body * 0.75f + clack * 0.35f).coerceIn(-1f, 1f) * 30000f).toInt().toShort()
    }
    out
  }

  /** Golpe corto de un bolo contra otro. */
  val knock: ShortArray by lazy { noiseBuffer(0.12f, { x -> (1f - x) * (1f - x) }, 0.7f, 0.55f, 1500f) }

  /** Fanfarria corta para chuza o spare. */
  val fanfare: ShortArray by lazy {
    val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f)
    val per = (SR * 0.14f).toInt()
    val out = ShortArray(per * notes.size + SR / 4)
    for (k in notes.indices) for (i in 0 until per) {
      val t = i.toFloat() / SR
      val env = (1f - i.toFloat() / per).coerceAtLeast(0f)
      out[k * per + i] = (sin(2.0 * PI * notes[k] * t).toFloat() * env * 18000f).toInt().toShort()
    }
    for (i in 0 until SR / 4) {
      val t = i.toFloat() / SR
      out[per * notes.size + i] = (sin(2.0 * PI * 1318.5 * t).toFloat() * (1f - i.toFloat() / (SR / 4)) * 16000f).toInt().toShort()
    }
    out
  }

  /** Golpe seco de la bola al caer sobre la madera al soltarla. */
  val release: ShortArray by lazy { noiseBuffer(0.35f, { x -> kotlin.math.exp(-x * 7f) * (if (x < 0.02f) x * 50f else 1f) }, 0.12f, 0.6f, 70f) }

  /** Canaleta: la bola cae y rueda con un eco metálico hueco. */
  val gutter: ShortArray by lazy { noiseBuffer(1.4f, { x -> (1f - x) * (0.6f + 0.4f * sin(x * 38f)) }, 0.10f, 0.35f, 180f) }

  /** Bocina de chuza: dos notas graves y brillantes. */
  val horn: ShortArray by lazy {
    val n = (SR * 1.0f).toInt(); val out = ShortArray(n)
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      val f = if (t < 0.4f) 311.1f else 415.3f
      val env = (if (t < 0.02f) t * 50f else 1f) * (1f - t / 1.0f).coerceAtLeast(0f)
      val s = (sin(2.0 * PI * f * t) + 0.5 * sin(2.0 * PI * f * 2 * t) + 0.25 * sin(2.0 * PI * f * 3 * t)).toFloat() * env
      out[i] = (s.coerceIn(-1f, 1f) * 17000f).toInt().toShort()
    }
    out
  }

  /** Aplausos: ruido filtrado con palmas rápidas. */
  val applause: ShortArray by lazy {
    val n = (SR * 1.8f).toInt(); val out = ShortArray(n); val rnd = java.util.Random(21); var lp = 0f
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      lp += (rnd.nextFloat() * 2f - 1f - lp) * 0.5f
      val clap = (0.45f + 0.55f * kotlin.math.abs(sin(t * 47f))) * (if (t < 0.15f) t / 0.15f else 1f) * (1f - t / 1.8f).coerceAtLeast(0f)
      out[i] = ((lp * clap).coerceIn(-1f, 1f) * 20000f).toInt().toShort()
    }
    out
  }

  /** Campanita de spare / bonus. */
  val chime: ShortArray by lazy {
    val n = (SR * 0.9f).toInt(); val out = ShortArray(n)
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      val s = (sin(2.0 * PI * 1568.0 * t) + 0.6 * sin(2.0 * PI * 2093.0 * t) + 0.3 * sin(2.0 * PI * 3136.0 * t)).toFloat() * kotlin.math.exp(-t * 4.5f)
      out[i] = (s.coerceIn(-1f, 1f) * 15000f).toInt().toShort()
    }
    out
  }

  /** Bip del marcador LED al cambiar de jugador. */
  val ledBeep: ShortArray by lazy {
    val n = (SR * 0.16f).toInt(); val out = ShortArray(n)
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      val f = if (t < 0.08f) 880.0 else 1320.0
      out[i] = (sin(2.0 * PI * f * t).toFloat() * (1f - i.toFloat() / n) * 12000f).toInt().toShort()
    }
    out
  }

  /** Golpes de madera más agudos y más graves para variar los choques entre bolos. */
  val knockHi: ShortArray by lazy { noiseBuffer(0.09f, { x -> (1f - x) * (1f - x) }, 0.85f, 0.65f, 2100f) }
  val knockLo: ShortArray by lazy { noiseBuffer(0.16f, { x -> (1f - x) * (1f - x) }, 0.55f, 0.55f, 900f) }

  /** Bolo contra suelo, pared o cojín del foso. */
  val thud: ShortArray by lazy { noiseBuffer(0.14f, { x -> kotlin.math.exp(-x * 6f) }, 0.14f, 0.5f, 120f) }

  /** Paso del jugador en la aproximación. */
  val step: ShortArray by lazy { noiseBuffer(0.12f, { x -> (1f - x) * (1f - x) * (if (x < 0.03f) x * 33f else 1f) }, 0.08f, 0.45f, 85f) }

  /** Barra de la máquina de bolos empujando la madera caída. */
  val rake: ShortArray by lazy { noiseBuffer(1.1f, { x -> (if (x < 0.1f) x * 10f else 1f) * (1f - x) * (0.6f + 0.4f * sin(x * 60f)) }, 0.07f, 0.25f, 110f) }

  /** Silbido del brazo cortando el aire en el balanceo. */
  val swoosh: ShortArray by lazy {
    val n = (SR * 0.5f).toInt(); val out = ShortArray(n); val rnd = java.util.Random(5); var lp = 0f
    for (i in 0 until n) {
      val x = i.toFloat() / n
      lp += (rnd.nextFloat() * 2f - 1f - lp) * (0.06f + 0.5f * x)
      val env = sin(PI.toFloat() * x) * (0.4f + 0.6f * x)
      out[i] = ((lp * env * 1.6f).coerceIn(-1f, 1f) * 20000f).toInt().toShort()
    }
    out
  }

  /** Reproduce un buffer una vez; es seguro lanzarlo desde varias corrutinas a la vez. */
  suspend fun play(data: ShortArray, volume: Float) {
    var track: AudioTrack? = null
    try {
      track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(data.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
      track.write(data, 0, data.size)
      track.setVolume(volume.coerceIn(0f, 1f))
      track.play()
      delay(data.size * 1000L / SR + 120L)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      // sin audio disponible: el juego sigue funcionando en silencio
    } finally {
      runCatching { track?.stop() }
      runCatching { track?.release() }
    }
  }
}

// ───────────────────────────── Escena 3D ─────────────────────────────

/** Cámara de perspectiva: mira hacia +y desde (x, y, h) inclinada hacia abajo `pitch` radianes. */
private class BowlCam(val x: Float, val y: Float, val h: Float, val pitch: Float, val f: Float, val cx: Float, val cy: Float) {
  private val cp = cos(pitch)
  private val sp = sin(pitch)
  fun depth(py: Float, pz: Float): Float = (py - y) * cp - (pz - h) * sp
  fun p(px: Float, py: Float, pz: Float): Offset? {
    val rx = px - x; val ry = py - y; val rz = pz - h
    val z = ry * cp - rz * sp
    if (z < 0.3f) return null
    val up = ry * sp + rz * cp
    return Offset(cx + f * rx / z, cy - f * up / z)
  }
  /** Píxeles por metro a esa profundidad. */
  fun scale(py: Float, pz: Float): Float = f / maxOf(0.3f, depth(py, pz))
  /** Cuánto mira hacia la cámara una dirección del mundo (positivo = de frente). */
  fun toward(dy: Float, dz: Float): Float = -(dy * cp - dz * sp)
  /** Componente vertical en pantalla de una dirección del mundo. */
  fun upOf(dy: Float, dz: Float): Float = dy * sp + dz * cp
}

private fun blerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private fun bsmooth(a: Float, b: Float, x: Float): Float {
  val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

// Silueta real del bolo (15 pulgadas): altura relativa → semiancho relativo al vientre.
private val PIN_PV = floatArrayOf(0.00f, 0.03f, 0.10f, 0.20f, 0.30f, 0.40f, 0.50f, 0.60f, 0.667f, 0.733f, 0.80f, 0.867f, 0.933f, 0.98f, 1.00f)
private val PIN_PW = floatArrayOf(0.47f, 0.54f, 0.69f, 0.93f, 1.00f, 0.94f, 0.73f, 0.50f, 0.377f, 0.42f, 0.50f, 0.525f, 0.48f, 0.33f, 0.00f)
private const val PIN_RMAX = 0.0605f

private fun pinRel(v: Float): Float {
  for (k in 1 until PIN_PV.size) {
    if (v <= PIN_PV[k]) return blerp(PIN_PW[k - 1], PIN_PW[k], (v - PIN_PV[k - 1]) / (PIN_PV[k] - PIN_PV[k - 1]))
  }
  return 0f
}

/** Un bolo a dibujar: centro de masa, orientación (cuaternión), altura del centro de masa y transparencia. */
private class BowlPinDraw(
  val x: Float, val y: Float, val z: Float,
  val qw: Float, val qx: Float, val qy: Float, val qz: Float,
  val zc: Float, val alpha: Float
)

private val PIN_SAMPLES = floatArrayOf(0f, 0.03f, 0.10f, 0.20f, 0.30f, 0.40f, 0.50f, 0.60f, 0.667f, 0.733f, 0.80f, 0.867f, 0.933f, 0.98f, 1f)
private val PIN_LIT = Offset(-0.55f, -0.83f)

/** Dibuja un bolo con su orientación 3D real: eje, silueta de revolución con perspectiva, franjas, brillo y sombra. */
private fun DrawScope.drawPin3D(cam: BowlCam, pd: BowlPinDraw) {
  if (pd.alpha <= 0.01f) return
  val ax = 2f * (pd.qx * pd.qz + pd.qw * pd.qy)
  val ay = 2f * (pd.qy * pd.qz - pd.qw * pd.qx)
  val az = 1f - 2f * (pd.qx * pd.qx + pd.qy * pd.qy)
  val bx0 = pd.x - ax * pd.zc; val by0 = pd.y - ay * pd.zc; val bz0 = pd.z - az * pd.zc
  val n = PIN_SAMPLES.size
  val sx = FloatArray(n); val sy = FloatArray(n); val rho = FloatArray(n)
  var rhoMax = 0f
  for (k in 0 until n) {
    val v = PIN_SAMPLES[k]
    val hh = v * BPINH.toFloat()
    val wx = bx0 + ax * hh; val wy = by0 + ay * hh; val wz = bz0 + az * hh
    val p = cam.p(wx, wy, wz) ?: return
    sx[k] = p.x; sy[k] = p.y
    rho[k] = pinRel(v) * PIN_RMAX * cam.scale(wy, wz)
    if (rho[k] > rhoMax) rhoMax = rho[k]
  }
  if (rhoMax < 0.6f) return
  val ddx = sx[n - 1] - sx[0]; val ddy = sy[n - 1] - sy[0]
  val len = hypot(ddx, ddy)
  val nx = if (len < 1e-3f) 1f else -ddy / len
  val ny = if (len < 1e-3f) 0f else ddx / len
  val lit = if (nx * PIN_LIT.x + ny * PIN_LIT.y >= 0f) 1f else -1f

  // sombra sobre la pista
  if (bz0 > -0.06f) {
    val tx = bx0 + ax * BPINH.toFloat(); val ty = by0 + ay * BPINH.toFloat()
    val g0 = cam.p(bx0, by0, 0f); val g1 = cam.p(tx, ty, 0f)
    if (g0 != null && g1 != null) {
      val fade = (1f - (bz0 / 0.9f)).coerceIn(0f, 1f)
      drawLine(Color.Black.copy(alpha = 0.30f * pd.alpha * fade), g0, g1, 0.115f * cam.scale(by0, 0f), StrokeCap.Round)
    }
  }

  val body = Path()
  for (k in 0 until n) { val px = sx[k] + nx * rho[k]; val py = sy[k] + ny * rho[k]; if (k == 0) body.moveTo(px, py) else body.lineTo(px, py) }
  for (k in n - 1 downTo 0) body.lineTo(sx[k] - nx * rho[k], sy[k] - ny * rho[k])
  body.close()
  val mc = 4
  val st = Offset(sx[mc] + lit * nx * rhoMax, sy[mc] + lit * ny * rhoMax)
  val en = Offset(sx[mc] - lit * nx * rhoMax, sy[mc] - lit * ny * rhoMax)
  val brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE4E7EF), Color(0xFFD5D9E3), Color(0xFF7E859A)), start = st, end = en)
  drawPath(body, brush, alpha = pd.alpha)
  // visto desde arriba o desde abajo: la silueta es un disco
  if (len < rhoMax * 1.4f) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFC9CEDA), Color(0xFF7E859A)), center = Offset(sx[mc] - rhoMax * 0.25f, sy[mc] - rhoMax * 0.25f), radius = rhoMax * 1.3f), rho[mc], Offset(sx[mc], sy[mc]), alpha = pd.alpha)
  }
  fun band(v0: Float, v1: Float, col: Color) {
    val steps = 4
    val path = Path()
    val pts = ArrayList<FloatArray>()
    for (s in 0..steps) {
      val v = blerp(v0, v1, s / steps.toFloat())
      val hh = v * BPINH.toFloat()
      val wx = bx0 + ax * hh; val wy = by0 + ay * hh; val wz = bz0 + az * hh
      val p = cam.p(wx, wy, wz) ?: return
      pts.add(floatArrayOf(p.x, p.y, pinRel(v) * PIN_RMAX * cam.scale(wy, wz)))
    }
    for (s in 0..steps) { val q = pts[s]; val px = q[0] + nx * q[2]; val py = q[1] + ny * q[2]; if (s == 0) path.moveTo(px, py) else path.lineTo(px, py) }
    for (s in steps downTo 0) { val q = pts[s]; path.lineTo(q[0] - nx * q[2], q[1] - ny * q[2]) }
    path.close()
    drawPath(path, col, alpha = pd.alpha)
  }
  band(0.64f, 0.69f, Color(0xFFD7263D))
  band(0.73f, 0.78f, Color(0xFFD7263D))
  // brillo largo del lado de la luz y sombra del lado opuesto
  val hl = Path()
  for (k in 2..9) { val px = sx[k] + lit * nx * rho[k] * 0.50f; val py = sy[k] + lit * ny * rho[k] * 0.50f; if (k == 2) hl.moveTo(px, py) else hl.lineTo(px, py) }
  drawPath(hl, Color(0x88FFFFFF), alpha = pd.alpha, style = Stroke(width = maxOf(1.2f, rhoMax * 0.16f), cap = StrokeCap.Round))
  val sh = Path()
  for (k in 2..9) { val px = sx[k] - lit * nx * rho[k] * 0.78f; val py = sy[k] - lit * ny * rho[k] * 0.78f; if (k == 2) sh.moveTo(px, py) else sh.lineTo(px, py) }
  drawPath(sh, Color(0x44101030), alpha = pd.alpha, style = Stroke(width = maxOf(1f, rhoMax * 0.22f), cap = StrokeCap.Round))
  drawPath(body, Color(0x55000000), alpha = pd.alpha, style = Stroke(width = 1f))
}

// ───────────────────────────── Brazo del jugador ─────────────────────────────

private const val ARM_L1 = 0.31f     // hombro → codo
private const val ARM_L2 = 0.27f     // codo → muñeca
private const val ARM_LB = 0.14f     // muñeca → centro de la bola
private const val ARM_SA = 0.22f     // inclinación lateral del brazo (el brazo cuelga hacia adentro)

/** Duración de la aproximación y el balanceo antes de soltar la bola; con más fuerza el brazo va más rápido. */
private fun bowlPre(power: Float): Float = 0.86f + (0.46f - 0.14f * power)

/**
 * Brazo humano en el instante ta (segundos desde que se aprieta TIRAR): preparación, empuje, vaivén hacia atrás,
 * balanceo acelerado como un péndulo, soltada a ras de pista y seguimiento.
 * Rellena o: hombro, codo, muñeca, centro de la bola (3 cada uno) y dirección del antebrazo.
 */
private fun bowlArm(ta: Float, x0: Float, power: Float, o: FloatArray) {
  val pre = bowlPre(power)
  val thb = Math.toRadians((62.0 + 30.0 * power)).toFloat()
  val rel = Math.toRadians(8.0).toFloat()
  val th: Float
  val ph: Float
  if (ta <= pre) {
    if (ta < 0.30f) {
      val u = bsmooth(0f, 0.30f, ta)
      th = blerp(Math.toRadians(28.0).toFloat(), Math.toRadians(18.0).toFloat(), u)
      ph = blerp(Math.toRadians(80.0).toFloat(), Math.toRadians(35.0).toFloat(), u)
    } else if (ta < 0.80f) {
      val u = (1f - cos(PI.toFloat() * (ta - 0.30f) / 0.50f)) / 2f
      th = blerp(Math.toRadians(18.0).toFloat(), -thb, u)
      ph = blerp(Math.toRadians(35.0).toFloat(), Math.toRadians(8.0).toFloat(), u)
    } else if (ta < 0.86f) {
      th = -thb; ph = Math.toRadians(8.0).toFloat()
    } else {
      val u = ((ta - 0.86f) / (pre - 0.86f)).coerceIn(0f, 1f)
      val e = 1f - cos(PI.toFloat() / 2f * u)
      th = blerp(-thb, rel, e); ph = blerp(Math.toRadians(8.0).toFloat(), 0f, e)
    }
  } else {
    val u = ((ta - pre) / 0.5f).coerceIn(0f, 1f)
    val e = 1f - (1f - u) * (1f - u)
    th = blerp(rel, Math.toRadians(150.0).toFloat(), e); ph = blerp(0f, Math.toRadians(75.0).toFloat(), e)
  }
  val ca = sqrt(1f - ARM_SA * ARM_SA)
  val rTot = ARM_L1 + ARM_L2 + ARM_LB
  // hombro: se calcula para que la bola salga justo en (x0, 0, altura de suelta)
  val relY = ca * sin(rel); val relZ = -ca * cos(rel)
  val srx = x0 + ARM_SA * rTot
  val sry = 0.02f - rTot * relY
  val srz = (BRB.toFloat() + 0.035f) - rTot * relZ
  val sy: Float
  val sz: Float
  if (ta <= pre) {
    val u = (ta / pre).coerceIn(0f, 1f)
    sy = sry - 1.10f * Math.pow((1f - u).toDouble(), 1.6).toFloat()
    sz = srz + 0.46f * (1f - bsmooth(0f, 1f, u)) + 0.012f * sin(ta * 16f) * (1f - u)
  } else {
    val u = ((ta - pre) / 0.5f).coerceIn(0f, 1f)
    val e = 1f - (1f - u) * (1f - u)
    sy = sry + 0.22f * e; sz = srz + 0.12f * e
  }
  o[0] = srx; o[1] = sy; o[2] = sz
  val d1y = ca * sin(th); val d1z = -ca * cos(th)
  val d2y = ca * sin(th + ph); val d2z = -ca * cos(th + ph)
  o[3] = srx - ARM_SA * ARM_L1; o[4] = sy + ARM_L1 * d1y; o[5] = sz + ARM_L1 * d1z
  o[6] = o[3] - ARM_SA * ARM_L2; o[7] = o[4] + ARM_L2 * d2y; o[8] = o[5] + ARM_L2 * d2z
  o[9] = o[6] - ARM_SA * ARM_LB; o[10] = o[7] + ARM_LB * d2y; o[11] = o[8] + ARM_LB * d2z
  o[12] = -ARM_SA; o[13] = d2y; o[14] = d2z
}

private val SKIN = Color(0xFFE3AE8A)
private val SKIN_SH = Color(0xFFB27A57)

/** Miembro cónico (cápsula con perspectiva) entre dos puntos 3D, con su lado en sombra. */
private fun DrawScope.drawLimb(
  cam: BowlCam, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
  r1: Float, r2: Float, col: Color, shade: Color, alpha: Float
) {
  val a = cam.p(ax, ay, az) ?: return
  val b = cam.p(bx, by, bz) ?: return
  val ra = r1 * cam.scale(ay, az); val rb = r2 * cam.scale(by, bz)
  val dx = b.x - a.x; val dy = b.y - a.y
  val l = hypot(dx, dy)
  val nx = if (l < 1e-3f) 1f else -dy / l
  val ny = if (l < 1e-3f) 0f else dx / l
  val body = Path().apply {
    moveTo(a.x + nx * ra, a.y + ny * ra); lineTo(b.x + nx * rb, b.y + ny * rb)
    lineTo(b.x - nx * rb, b.y - ny * rb); lineTo(a.x - nx * ra, a.y - ny * ra); close()
  }
  drawCircle(col, ra, a, alpha = alpha)
  drawCircle(col, rb, b, alpha = alpha)
  drawPath(body, col, alpha = alpha)
  val side = Path().apply {
    moveTo(a.x + nx * ra, a.y + ny * ra); lineTo(b.x + nx * rb, b.y + ny * rb)
    lineTo(b.x + nx * rb * 0.15f, b.y + ny * rb * 0.15f); lineTo(a.x + nx * ra * 0.15f, a.y + ny * ra * 0.15f); close()
  }
  drawPath(side, shade, alpha = alpha * 0.85f)
  drawLine(Color.White.copy(alpha = 0.18f * alpha), Offset(a.x - nx * ra * 0.45f, a.y - ny * ra * 0.45f), Offset(b.x - nx * rb * 0.45f, b.y - ny * rb * 0.45f), maxOf(1f, ra * 0.28f), StrokeCap.Round)
}

/** Hombro con manga, brazo, antebrazo y mano (palma y dedos sobre la bola). */
private fun DrawScope.drawArm(cam: BowlCam, o: FloatArray, sleeve: Color, alpha: Float, handsOnly: Boolean) {
  val sleeveSh = Color(sleeve.red * 0.62f, sleeve.green * 0.62f, sleeve.blue * 0.62f, 1f)
  if (!handsOnly) {
    val mx = o[0] + (o[3] - o[0]) * 0.62f; val my = o[1] + (o[4] - o[1]) * 0.62f; val mz = o[2] + (o[5] - o[2]) * 0.62f
    drawLimb(cam, o[0], o[1], o[2], mx, my, mz, 0.066f, 0.060f, sleeve, sleeveSh, alpha)
    drawLimb(cam, mx, my, mz, o[3], o[4], o[5], 0.050f, 0.046f, SKIN, SKIN_SH, alpha)
    drawLimb(cam, o[3], o[4], o[5], o[6], o[7], o[8], 0.046f, 0.032f, SKIN, SKIN_SH, alpha)
    val s = cam.p(o[0], o[1], o[2])
    if (s != null) drawCircle(sleeve, 0.078f * cam.scale(o[1], o[2]), s, alpha = alpha)
  } else {
    val dx = o[12]; val dy = o[13]; val dz = o[14]
    val px = o[6] + dx * 0.075f; val py = o[7] + dy * 0.075f; val pz = o[8] + dz * 0.075f
    drawLimb(cam, o[6], o[7], o[8], px, py, pz, 0.036f, 0.038f, SKIN, SKIN_SH, alpha)
    // dedos que abrazan la bola (centro en o[9..11])
    for (k in 0 until 4) {
      val lat = (k - 1.5f) * 0.024f
      val fx = px + lat; val fy = py; val fz = pz
      var tx = o[9] - fx; var ty = o[10] - fy; var tz = o[11] - fz
      val tl = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-4f)
      tx /= tl; ty /= tl; tz /= tl
      drawLimb(cam, fx, fy, fz, fx + tx * 0.07f, fy + ty * 0.07f, fz + tz * 0.07f, 0.0135f, 0.0115f, SKIN, SKIN_SH, alpha)
    }
    drawLimb(cam, px - 0.03f, py, pz, px - 0.03f + (o[9] - px) * 0.5f, py + (o[10] - py) * 0.55f, pz + (o[11] - pz) * 0.55f, 0.016f, 0.013f, SKIN, SKIN_SH, alpha)
  }
}

private fun DrawScope.drawBowlScene(
  sim: BowlSim?, t: Float, standing: Int, aim: FloatArray, aimPath: List<Pair<Float, Float>>, showAim: Boolean,
  camBlend: Float, sweep: Float, drop: Float, flash: Float, hue: Float, ball: BowlBall = BOWL_BALLS[0],
  armTa: Float = -1f, armX: Float = 0f, armPower: Float = 0.7f, stay: Boolean = false
) {
  val w = size.width
  val h = size.height
  drawRect(Brush.verticalGradient(listOf(Color(0xFF05010F), Color(0xFF1B0A3C), Color(0xFF0A0420))))

  // ── brazo y fase del lanzamiento ──
  val preMax = bowlPre(armPower)
  val armO = FloatArray(15)
  val throwCamOn = sim != null && armTa >= 0f && armTa < preMax + 0.95f
  val inPre = sim != null && armTa >= 0f && armTa < preMax
  if (throwCamOn) bowlArm(armTa, armX, armPower, armO)

  // ── estado de la bola y los bolos en el instante t ──
  var bxNow = aim[0]; var byNow = 0f; var bzNow = BRB.toFloat()
  var bqw = 0.92f; var bqx = 0.12f; var bqy = 0.30f; var bqz = 0.20f
  var ballVisible = showAim
  var f0 = 0; var f1 = 0; var fr = 0f
  if (sim != null && sim.frames > 0) {
    val idx = (t * 60f).coerceIn(0f, (sim.frames - 1).toFloat())
    f0 = idx.toInt(); f1 = minOf(f0 + 1, sim.frames - 1); fr = idx - f0
    bxNow = blerp(sim.ball[f0 * 7], sim.ball[f1 * 7], fr)
    byNow = blerp(sim.ball[f0 * 7 + 1], sim.ball[f1 * 7 + 1], fr)
    bzNow = blerp(sim.ball[f0 * 7 + 2], sim.ball[f1 * 7 + 2], fr)
    bqw = blerp(sim.ball[f0 * 7 + 3], sim.ball[f1 * 7 + 3], fr); bqx = blerp(sim.ball[f0 * 7 + 4], sim.ball[f1 * 7 + 4], fr)
    bqy = blerp(sim.ball[f0 * 7 + 5], sim.ball[f1 * 7 + 5], fr); bqz = blerp(sim.ball[f0 * 7 + 6], sim.ball[f1 * 7 + 6], fr)
    ballVisible = byNow < BYD.toFloat() + 0.12f && bzNow > -0.14f
    if (inPre) { bxNow = armO[9]; byNow = armO[10]; bzNow = armO[11]; ballVisible = true; bqw = 0.92f; bqx = 0.12f; bqy = 0.30f; bqz = 0.20f }
  }
  val bl = sqrt(bqw * bqw + bqx * bqx + bqy * bqy + bqz * bqz).coerceAtLeast(1e-6f)
  bqw /= bl; bqx /= bl; bqy /= bl; bqz /= bl

  // ── cámara ──
  var camX: Float; var camY: Float; var camH: Float; var camLook: Float; var camF = 1.9f * w
  if (sim == null) {
    camY = blerp(-2.6f, 13.6f, camBlend)
    camX = aim[0] * 0.35f
    val prog = ((camY + 2.6f) / 16.2f).coerceIn(0f, 1f)
    camH = 1.45f - 0.55f * prog
    camLook = 8f - 3f * prog
  } else {
    val followY = (byNow - 3f).coerceIn(-2.6f, 13.6f)
    val followX = bxNow * 0.35f
    val prog = ((followY + 2.6f) / 16.2f).coerceIn(0f, 1f)
    val followH = 1.45f - 0.55f * prog
    val followLook = 8f - 3f * prog
    if (throwCamOn) {
      // cámara detrás del hombro del jugador; tras soltar se abre y pasa a seguir la bola
      val u = bsmooth(0f, 0.35f, armTa)
      val tcX = blerp(armX * 0.35f, armO[0] - 0.50f, u)
      val tcY = blerp(-2.6f, armO[1] - 2.35f, u)
      val tcH = blerp(1.45f, 1.50f, u)
      val tcLook = blerp(8f, 7f, u)
      val tcF = blerp(1.9f, 1.45f, u) * w
      if (inPre) {
        camX = tcX; camY = tcY; camH = tcH; camLook = tcLook; camF = tcF
      } else {
        val s = bsmooth(0f, 0.9f, t)
        camX = blerp(tcX, followX, s)
        camY = maxOf(followY, blerp(tcY, -2.6f, s))
        camH = blerp(tcH, followH, s)
        camLook = blerp(tcLook, followLook, s)
        camF = blerp(tcF, 1.9f * w, s)
      }
    } else {
      camX = followX; camY = followY; camH = followH; camLook = followLook
    }
  }
  val cam = BowlCam(camX, camY, camH, atan2(camH, camLook), camF, w / 2f, h * 0.52f)

  // temblor de cámara con los impactos fuertes
  var shake = 0f
  if (sim != null) for (ev in sim.events) {
    val dtE = abs(t - ev[0])
    if (dtE < 0.25f && ev[3] > 1.5f && ev[4] >= 2f) shake += ev[3] * (if (ev[4] >= 3f) 0.35f else 0.12f) * (1f - dtE / 0.25f)
  }
  shake = shake.coerceAtMost(8f)
  val sx = sin(t * 95f) * shake
  val sy = cos(t * 83f) * shake * 0.6f

  translate(sx, sy) {
    fun quad(x0: Float, x1: Float, y0: Float, y1: Float, z: Float, c: Color) {
      val a = cam.p(x0, y0, z); val b = cam.p(x1, y0, z); val c2 = cam.p(x1, y1, z); val d = cam.p(x0, y1, z)
      if (a == null || b == null || c2 == null || d == null) return
      val path = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c2.x, c2.y); lineTo(d.x, d.y); close() }
      drawPath(path, c)
    }
    val neon = Color.hsv((hue * 360f) % 360f, 0.75f, 1f)
    val yStart = maxOf(-4.5f, cam.y + 0.45f)
    val yEnd = BYB.toFloat()
    val half = BHALF.toFloat()

    // ── sala de bolos: paredes con paneles, franja de neón, luces de techo y mueble de retorno de bolas ──
    run {
      val wallX = 1.95f
      var wy = maxOf(-4.5f, cam.y + 0.45f)
      var idx = 0
      while (wy < 21f) {
        val wy2 = wy + 1.6f
        val sh = ((wy - cam.y) / 26f).coerceIn(0f, 1f)
        for (side in listOf(-1f, 1f)) {
          val xx = side * wallX
          val a = cam.p(xx, wy, 0f); val b = cam.p(xx, wy2, 0f); val c3 = cam.p(xx, wy2, 3.2f); val d3 = cam.p(xx, wy, 3.2f)
          if (a != null && b != null && c3 != null && d3 != null) {
            val panel = if (idx % 2 == 0) lerp(Color(0xFF2A1A4E), Color(0xFF0C0620), sh) else lerp(Color(0xFF221440), Color(0xFF0A051A), sh)
            drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c3.x, c3.y); lineTo(d3.x, d3.y); close() }, panel)
          }
          val n1 = cam.p(xx, wy, 1.25f); val n2 = cam.p(xx, wy2, 1.25f)
          if (n1 != null && n2 != null) {
            drawLine(neon.copy(alpha = 0.18f), n1, n2, 9f); drawLine(neon.copy(alpha = 0.9f), n1, n2, 2.2f)
          }
          // lámpara de pared
          if (idx % 2 == 0) {
            val l0 = cam.p(xx, wy + 0.8f, 2.0f)
            if (l0 != null) drawCircle(Brush.radialGradient(listOf(Color(0x55FFE9B0), Color.Transparent), l0, 34f * cam.scale(wy + 0.8f, 2.0f) / 30f + 6f), 34f * cam.scale(wy + 0.8f, 2.0f) / 30f + 6f, l0)
          }
        }
        // luces del techo
        val t0 = cam.p(0f, wy + 0.8f, 3.2f)
        if (t0 != null) {
          val rr = 60f * cam.scale(wy + 0.8f, 3.2f) / 40f + 8f
          drawCircle(Brush.radialGradient(listOf(Color(0x66FFF1CC), Color.Transparent), t0, rr), rr, t0)
        }
        wy = wy2; idx++
      }
      // mueble de retorno de bolas junto a la línea de falta
      val ra = cam.p(-0.95f, 0.2f, 0.65f); val rb = cam.p(0.95f, 0.2f, 0.65f)
      if (ra != null && rb != null) drawLine(Color(0xFF3A2A66), ra, rb, 6f, StrokeCap.Round)
    }
    // pista por tramos (cada tramo con su sombreado según la distancia)
    var y0 = yStart
    while (y0 < yEnd) {
      val y1 = minOf(yEnd, y0 + 0.5f + maxOf(0f, y0 - cam.y) * 0.04f)
      val shade = ((y0 - cam.y) / 24f).coerceIn(0f, 1f)
      val lane = if (y0 < 0f) lerp(Color(0xFFE2B572), Color(0xFFB98445), shade) else lerp(Color(0xFFD9A15C), Color(0xFF8A5527), shade)
      quad(-half, half, y0, y1 + 0.02f, 0f, lane)
      quad(-0.66f, -half, y0, y1 + 0.02f, -0.02f, lerp(Color(0xFF2A2A33), Color(0xFF0B0B10), shade))
      quad(half, 0.66f, y0, y1 + 0.02f, -0.02f, lerp(Color(0xFF2A2A33), Color(0xFF0B0B10), shade))
      quad(-4f, -0.66f, y0, y1 + 0.02f, -0.03f, lerp(Color(0xFF16102B), Color(0xFF07040F), shade))
      quad(0.66f, 4f, y0, y1 + 0.02f, -0.03f, lerp(Color(0xFF16102B), Color(0xFF07040F), shade))
      y0 = y1
    }
    // tablas de madera
    val laneStart = maxOf(0f, yStart)
    for (k in -16..16) {
      val x = k * (2f * half / 32f)
      val a = cam.p(x, laneStart, 0f); val b = cam.p(x, yEnd, 0f)
      if (a != null && b != null) drawLine(Color(0x24000000), a, b, 1f)
    }
    // tablas alternadas y brillo barnizado de la pista
    for (k in -16 until 16 step 2) {
      val xa = k * (2f * half / 32f); val xb = (k + 1) * (2f * half / 32f)
      var yy = laneStart
      while (yy < yEnd) {
        val y2 = minOf(yEnd, yy + 1.2f + maxOf(0f, yy - cam.y) * 0.1f)
        quad(xa, xb, yy, y2, 0.001f, Color(0x0F000000))
        yy = y2
      }
    }
    run {
      val gl = cam.p(-0.12f, laneStart, 0f); val gr = cam.p(0.12f, laneStart, 0f)
      val tl = cam.p(-0.12f, yEnd, 0f); val tr = cam.p(0.12f, yEnd, 0f)
      if (gl != null && gr != null && tl != null && tr != null) {
        val path = Path().apply { moveTo(gl.x, gl.y); lineTo(gr.x, gr.y); lineTo(tr.x, tr.y); lineTo(tl.x, tl.y); close() }
        drawPath(path, Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color(0x33FFFFFF), Color(0x1AFFFFFF)), startY = tl.y, endY = gl.y))
      }
    }
    // línea de falta, flechas y puntos de mira
    val foulA = cam.p(-half, 0f, 0f); val foulB = cam.p(half, 0f, 0f)
    if (foulA != null && foulB != null) drawLine(Color(0xFFE53935), foulA, foulB, 3f)
    for (i in 0..6) {
      val ax = (i - 3) * 0.14f
      val ay = 3.9f + (3 - abs(i - 3)) * 0.38f
      val a = cam.p(ax, ay, 0f); val b = cam.p(ax - 0.035f, ay + 0.55f, 0f); val c2 = cam.p(ax + 0.035f, ay + 0.55f, 0f)
      if (a != null && b != null && c2 != null) {
        val tri = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c2.x, c2.y); close() }
        drawPath(tri, Color(0x66402200))
      }
    }
    for (i in 0..6) {
      val c0 = cam.p((i - 3) * 0.14f, 2.2f, 0f)
      if (c0 != null) drawCircle(Color(0x66402200), 2.5f * cam.scale(2.2f, 0f) / 40f + 1.2f, c0)
    }
    // zona de los bolos, borde de la tarima y foso con cojín trasero
    val yd = BYD.toFloat(); val yb = BYB.toFloat()
    quad(-half, half, (BY0 - 0.35).toFloat(), yd, 0.001f, Color(0x16FFFFFF))
    quad(-0.7f, 0.7f, yd, yb, -0.01f, Color(0xFF040308))
    run {
      val ea = cam.p(-0.7f, yd, 0f); val eb = cam.p(0.7f, yd, 0f)
      if (ea != null && eb != null) { drawLine(Color(0xFF6B4A24), ea, eb, 4f); drawLine(Color(0x55FFFFFF), ea, eb, 1.2f) }
      val a = cam.p(-0.7f, yb, 0f); val b = cam.p(0.7f, yb, 0f); val c2 = cam.p(0.7f, yb, 1.6f); val d = cam.p(-0.7f, yb, 1.6f)
      if (a != null && b != null && c2 != null && d != null) {
        val path = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c2.x, c2.y); lineTo(d.x, d.y); close() }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFF1A1030), Color(0xFF05020A)), startY = d.y, endY = a.y))
        drawLine(neon.copy(alpha = 0.9f), d, c2, 3f)
      }
    }
    // tiras de neón a los lados de la pista
    for (side in listOf(-1f, 1f)) {
      val a = cam.p(side * 0.82f, yStart, 0.03f); val b = cam.p(side * 0.82f, yEnd, 0.03f)
      if (a != null && b != null) {
        drawLine(neon.copy(alpha = 0.10f), a, b, 16f, StrokeCap.Round)
        drawLine(neon.copy(alpha = 0.25f), a, b, 8f, StrokeCap.Round)
        drawLine(neon.copy(alpha = 0.95f), a, b, 2.5f, StrokeCap.Round)
      }
    }
    // foco sobre los bolos
    val spot = cam.p(0f, (BY0 + 0.35).toFloat(), 0.2f)
    if (spot != null) {
      val rs = cam.scale((BY0 + 0.35).toFloat(), 0.2f) * 1.5f
      drawCircle(Brush.radialGradient(listOf(Color(0x66FFFFFF), Color.Transparent), center = spot, radius = rs), rs, spot)
    }

    // guía de puntería (solo cuando te toca)
    if (showAim && sim == null) {
      aimPath.forEachIndexed { i, pt ->
        val p = cam.p(pt.first, pt.second, 0.01f)
        if (p != null) drawCircle(neon.copy(alpha = (0.85f - i * 0.012f).coerceAtLeast(0.2f)), 3.2f * cam.scale(pt.second, 0f) / 30f + 1.4f, p)
      }
    }

    // ── bolos: pose 3D de cada uno (partida, caída libre o máquina de bolos) ──
    val pins = ArrayList<BowlPinDraw>()
    val zcs = BPROPS.zc
    for (i in 0 until 10) {
      val present = if (sim != null) ((sim.startMask shr i) and 1) == 1 else ((standing shr i) and 1) == 1
      if (!present) continue
      val zc = zcs[i].toFloat()
      if (sim != null && sim.frames > 0) {
        val o0 = f0 * 70 + i * 7; val o1 = f1 * 70 + i * 7
        var x = blerp(sim.pins[o0], sim.pins[o1], fr)
        var y = blerp(sim.pins[o0 + 1], sim.pins[o1 + 1], fr)
        var z = blerp(sim.pins[o0 + 2], sim.pins[o1 + 2], fr)
        var qw = blerp(sim.pins[o0 + 3], sim.pins[o1 + 3], fr); var qx = blerp(sim.pins[o0 + 4], sim.pins[o1 + 4], fr)
        var qy = blerp(sim.pins[o0 + 5], sim.pins[o1 + 5], fr); var qz = blerp(sim.pins[o0 + 6], sim.pins[o1 + 6], fr)
        var alpha = 1f
        if (sweep > 0f) {
          val up = ((sim.endMask shr i) and 1) == 1
          if (up && stay) {
            // la máquina levanta los bolos que quedaron, los endereza y los apoya otra vez en su marca
            val lift = 0.45f * bsmooth(0.04f, 0.22f, sweep) * (1f - bsmooth(0.70f, 0.95f, sweep))
            val u = bsmooth(0.12f, 0.32f, sweep)
            val v2 = bsmooth(0.30f, 0.72f, sweep)
            x = blerp(x, BPIN_POS[i].first.toFloat(), v2); y = blerp(y, BPIN_POS[i].second.toFloat(), v2)
            qw = blerp(qw, 1f, u); qx = blerp(qx, 0f, u); qy = blerp(qy, 0f, u); qz = blerp(qz, 0f, u)
            z = blerp(z, zc, u) + lift
          } else if (up) {
            z += 1.7f * bsmooth(0.05f, 0.35f, sweep)
          } else {
            // madera caída: la barra la empuja hasta el foso
            val barY = blerp((BY0 - 0.55).toFloat(), yd + 0.2f, bsmooth(0.22f, 0.72f, sweep))
            if (y < barY + 0.12f && z > -0.1f) y = barY + 0.12f
            z = blerp(z, -0.20f, bsmooth(yd, yd + 0.25f, y))
            alpha = 1f - bsmooth(0.80f, 0.97f, sweep)
          }
        }
        val dark = if (z < -0.05f) 0.6f else 1f
        pins.add(BowlPinDraw(x, y, z, qw, qx, qy, qz, zc, alpha * dark))
      } else {
        val dr = drop.coerceIn(0f, 1f)
        pins.add(BowlPinDraw(BPIN_POS[i].first.toFloat(), BPIN_POS[i].second.toFloat(), zc + (1f - dr) * 1.3f, 1f, 0f, 0f, 0f, zc, minOf(1f, dr * 3f)))
      }
    }
    pins.sortByDescending { it.y }

    fun drawBall() {
      val c0 = cam.p(bxNow, byNow, bzNow) ?: return
      val r = BRB.toFloat() * cam.scale(byNow, bzNow)
      val sh = cam.p(bxNow, byNow, 0f)
      if (sh != null && bzNow > -0.05f) {
        val lift = ((bzNow - BRB.toFloat()) / 0.6f).coerceIn(0f, 1f)
        drawOval(Color(0x66000000).copy(alpha = 0.40f * (1f - lift)), Offset(sh.x - r * 1.1f, sh.y - r * 0.18f), Size(r * 2.2f, r * 0.36f))
      }
      if (sim != null && !inPre) for (k in 1..7) {
        val fi = f0 - k * 2
        if (fi < 0) break
        val tp = cam.p(sim.ball[fi * 7], sim.ball[fi * 7 + 1], sim.ball[fi * 7 + 2])
        if (tp != null) drawCircle(neon.copy(alpha = 0.10f * (8 - k) / 7f), r * (1f - k * 0.05f), tp)
      }
      drawCircle(
        Brush.radialGradient(listOf(ball.hi, ball.mid, ball.lo), center = Offset(c0.x - r * 0.35f, c0.y - r * 0.4f), radius = r * 1.6f),
        r, c0
      )
      // agujeros de dedos y pulgar girando con la bola (orientación 3D real)
      val hx = floatArrayOf(0.36f, 0.10f, -0.20f, 0.02f)
      val hy = floatArrayOf(0.05f, 0.32f, 0.22f, -0.28f)
      val hz = floatArrayOf(0.93f, 0.94f, 0.95f, 0.96f)
      val r00 = 1f - 2f * (bqy * bqy + bqz * bqz); val r01 = 2f * (bqx * bqy - bqw * bqz); val r02 = 2f * (bqx * bqz + bqw * bqy)
      val r10 = 2f * (bqx * bqy + bqw * bqz); val r11 = 1f - 2f * (bqx * bqx + bqz * bqz); val r12 = 2f * (bqy * bqz - bqw * bqx)
      val r20 = 2f * (bqx * bqz - bqw * bqy); val r21 = 2f * (bqy * bqz + bqw * bqx); val r22 = 1f - 2f * (bqx * bqx + bqy * bqy)
      for (k in 0 until 4) {
        val lx = hx[k]; val ly = hy[k]; val lz = hz[k]
        val dx = r00 * lx + r01 * ly + r02 * lz
        val dy = r10 * lx + r11 * ly + r12 * lz
        val dz = r20 * lx + r21 * ly + r22 * lz
        val face = cam.toward(dy, dz)
        if (face > 0.08f) {
          val hp = Offset(c0.x + dx * r, c0.y - cam.upOf(dy, dz) * r)
          val rad = r * (if (k == 3) 0.13f else 0.095f)
          drawOval(Color(0xFF05061A), Offset(hp.x - rad, hp.y - rad * face), Size(rad * 2f, rad * 2f * face))
        }
      }
      drawRoundRect(Color(0x55FFFFFF), Offset(c0.x - r * 0.62f, c0.y - r * 0.72f), Size(r * 0.62f, r * 0.34f), androidx.compose.ui.geometry.CornerRadius(r * 0.1f))
      drawArc(Color(0x33FFFFFF), 200f, 120f, false, Offset(c0.x - r * 0.9f, c0.y - r * 0.9f), Size(r * 1.8f, r * 1.8f), style = Stroke(r * 0.12f))
      drawCircle(Color(0x66FFFFFF), r * 0.17f, Offset(c0.x - r * 0.38f, c0.y - r * 0.44f))
      drawCircle(Color(0xCCFFFFFF), r * 0.06f, Offset(c0.x - r * 0.42f, c0.y - r * 0.5f))
      drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color(0x55000000)), center = c0, radius = r), r, c0)
      drawCircle(neon.copy(alpha = 0.35f), r * 1.04f, c0, style = Stroke(width = 1.6f))
    }

    // orden de dibujo: bolos lejanos → bola → bolos cercanos; el brazo según qué tan cerca esté de la cámara
    val armAlpha = if (sim != null && armTa >= 0f) 1f - bsmooth(preMax + 0.30f, preMax + 0.65f, armTa) else 0f
    val armOn = armAlpha > 0.01f
    val sleeve = Color(ball.mid.red * 0.8f + 0.1f, ball.mid.green * 0.8f + 0.1f, ball.mid.blue * 0.8f + 0.1f, 1f)
    val armFirst = armOn && cam.depth(byNow, bzNow) < cam.depth(armO[1], armO[2])
    for (pd in pins) if (pd.y > byNow || !ballVisible) drawPin3D(cam, pd)
    if (armOn && armFirst) drawArm(cam, armO, sleeve, armAlpha, false)
    if (ballVisible) drawBall()
    if (armOn && !armFirst) drawArm(cam, armO, sleeve, armAlpha, false)
    if (armOn && inPre) drawArm(cam, armO, sleeve, armAlpha, true)
    for (pd in pins) if (ballVisible && pd.y <= byNow) drawPin3D(cam, pd)

    // barra barredora de la máquina de bolos
    if (sim != null && sweep > 0.02f && sweep < 0.97f) {
      val barY = blerp((BY0 - 0.55).toFloat(), yd + 0.2f, bsmooth(0.22f, 0.72f, sweep))
      val rz = blerp(0.55f, 0.10f, bsmooth(0.10f, 0.22f, sweep)) + 0.45f * bsmooth(0.74f, 0.90f, sweep)
      val ra = cam.p(-0.64f, barY, rz); val rb = cam.p(0.64f, barY, rz)
      if (ra != null && rb != null) {
        val wpx = 0.08f * cam.scale(barY, rz)
        drawLine(Color(0xFF2B2740), ra, rb, wpx, StrokeCap.Round)
        drawLine(Color(0xFF5E5890), Offset(ra.x, ra.y - wpx * 0.28f), Offset(rb.x, rb.y - wpx * 0.28f), wpx * 0.22f, StrokeCap.Round)
        drawLine(neon.copy(alpha = 0.9f), ra, rb, 2.2f)
      }
    }

    // chispas en cada impacto fuerte de la bola o entre bolos
    if (sim != null) for (ev in sim.events) {
      val age = (t - ev[0]) / 0.45f
      if (age in 0f..1f && ev[3] > 1.5f && ev[4] >= 2f) {
        val c0 = cam.p(ev[1], ev[2], 0.12f) ?: continue
        for (s in 0 until 7) {
          val a = s * 0.9f + ev[0] * 13f
          val d = age * 44f * (0.5f + (s % 3) * 0.3f)
          drawCircle(Color(0xFFFFE9A8).copy(alpha = 1f - age), 2.6f * (1f - age) + 0.8f, Offset(c0.x + cos(a) * d, c0.y + sin(a) * d - age * 10f))
        }
      }
    }
  }

  // viñeta y destello de chuza
  drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x99000000)), center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.78f))
  if (flash > 0f) drawRect(Color.White.copy(alpha = flash * 0.5f))
}

@Composable
private fun BowlScene(
  sim: BowlSim?, timeProvider: () -> Float, standing: Int, aim: FloatArray, aimPath: List<Pair<Float, Float>>, showAim: Boolean,
  camBlend: () -> Float, sweep: () -> Float, drop: () -> Float, flash: () -> Float,
  armTa: () -> Float, armX: Float, armPower: Float, stay: Boolean,
  ball: BowlBall = BOWL_BALLS[0],
  modifier: Modifier = Modifier, onDrag: (Float, Float) -> Unit
) {
  val hue by rememberInfiniteTransition(label = "bowlHue").animateFloat(
    initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "bowlHueV"
  )
  Canvas(
    modifier.pointerInput(showAim) {
      if (showAim) detectHorizontalDragGestures { change, dx -> change.consume(); onDrag(dx, size.width.toFloat()) }
    }
  ) {
    drawBowlScene(sim, timeProvider(), standing, aim, aimPath, showAim, camBlend(), sweep(), drop(), flash(), hue, ball, armTa(), armX, armPower, stay)
  }
}

// ───────────────────────────── Marcador ─────────────────────────────


@Composable
private fun BowlRoundBtn(label: String, onClick: () -> Unit) {
  Box(
    Modifier.size(42.dp).clip(CircleShape).background(Color(0xAA1A0F2B)).border(2.dp, Color(0xFFB388FF), CircleShape).clickable { onClick() },
    contentAlignment = Alignment.Center
  ) { Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
}

/** Medidor de fuerza vertical: barra de segmentos que van de verde a rojo, con icono de bola; se arrastra con el dedo. */
@Composable
private fun BowlPowerMeter(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text("💥", fontSize = 16.sp)
    Box(
      Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x99000000)).border(2.dp, Color(0xFFB388FF), RoundedCornerShape(14.dp))
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown()
            fun set(y: Float) { onChange((1f - y / size.height.toFloat()).coerceIn(0f, 1f).let { 0.3f + it * 0.7f }) }
            set(down.position.y)
            while (true) {
              val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
              if (!ch.pressed) break
              set(ch.position.y); ch.consume()
            }
          }
        }
    ) {
      Canvas(Modifier.fillMaxSize()) {
        val segs = 12; val gap = 3.dp.toPx(); val h = (size.height - gap * (segs + 1)) / segs
        val lit = (((value - 0.3f) / 0.7f).coerceIn(0f, 1f) * segs).let { kotlin.math.ceil(it.toDouble()).toInt().coerceAtLeast(1) }
        for (i in 0 until segs) {
          val y = size.height - gap - (i + 1) * (h + gap) + gap
          val t = i / (segs - 1f)
          val col = androidx.compose.ui.graphics.lerp(Color(0xFF39FF88), Color(0xFFFF3D57), t)
          drawRoundRect(if (i < lit) col else col.copy(alpha = 0.15f), Offset(gap * 1.5f, y), androidx.compose.ui.geometry.Size(size.width - gap * 3f, h), androidx.compose.ui.geometry.CornerRadius(5f, 5f))
        }
      }
    }
    Text("${(value * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
  }
}

/** Dial de efecto: una bola con flecha curva; arrastra a izquierda/derecha para curvar la bola. */
@Composable
private fun BowlHookDial(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.pointerInput(Unit) {
    awaitEachGesture {
      val down = awaitFirstDown()
      fun set(x: Float) { onChange(((x / size.width.toFloat()) * 2f - 1f).coerceIn(-1f, 1f)) }
      set(down.position.x)
      while (true) {
        val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
        if (!ch.pressed) break
        set(ch.position.x); ch.consume()
      }
    }
  }, horizontalAlignment = Alignment.CenterHorizontally) {
    Canvas(Modifier.weight(1f).fillMaxWidth()) {
      val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension / 2f - 4f
      drawCircle(Color(0xAA1A0F2B), r, c); drawCircle(Color(0xFFB388FF), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
      drawArc(Color(0x33FFFFFF), 200f, 140f, false, c - Offset(r * 0.72f, r * 0.72f), androidx.compose.ui.geometry.Size(r * 1.44f, r * 1.44f), style = androidx.compose.ui.graphics.drawscope.Stroke(6f))
      val sweep = value * 70f
      drawArc(Color(0xFFFFC857), 270f, sweep, false, c - Offset(r * 0.72f, r * 0.72f), androidx.compose.ui.geometry.Size(r * 1.44f, r * 1.44f), style = androidx.compose.ui.graphics.drawscope.Stroke(8f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
      val a = Math.toRadians((270f + sweep).toDouble())
      drawCircle(Color.White, 9f, Offset(c.x + (r * 0.72f * kotlin.math.cos(a)).toFloat(), c.y + (r * 0.72f * kotlin.math.sin(a)).toFloat()))
      drawCircle(Brush.radialGradient(listOf(Color(0xFF7886FF), Color(0xFF1D2A8C)), c, r * 0.4f), r * 0.32f, c)
    }
    Text(if (value < -0.15f) "↶ Curva izq." else if (value > 0.15f) "Curva der. ↷" else "Recta", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
  }
}

@Composable
private fun BowlBallPicker(selected: Int, tick: Int, onPick: (Int) -> Unit) {
  val ctx = LocalContext.current
  val owned = remember(tick) { Cosmetics.ownedBalls(ctx) }
  Text("Tu bola (las bloqueadas se compran en la Tienda de juegos)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    BOWL_BALLS.forEach { b ->
      val ok = b.id in owned
      val label = (if (ok) b.emoji else "🔒") + " " + b.name
      OptionChip(label, selected = (selected == b.id), onClick = { if (ok) onPick(b.id) }, enabled = ok)
    }
  }
}

@Composable
private fun BowlScoreboard(names: List<String>, rolls: List<List<Int>>, active: Int) {
  Column(Modifier.horizontalScroll(rememberScrollState())) {
    Row {
      Spacer(Modifier.width(78.dp))
      for (f in 1..10) {
        Box(Modifier.width(if (f == 10) 50.dp else 36.dp), contentAlignment = Alignment.Center) {
          Text("$f", color = Color(0x99FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
      Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) { Text("TOTAL", color = Color(0xFFFFC857), fontSize = 10.sp, fontWeight = FontWeight.Black) }
    }
    names.forEachIndexed { i, name ->
      val frames = bowlFrames(rolls.getOrElse(i) { emptyList() })
      val isActive = i == active
      Row(
        Modifier.padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp))
          .background(if (isActive) Color(0x33FFC857) else Color(0x14FFFFFF)),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(Modifier.width(78.dp).padding(horizontal = 6.dp)) {
          Text(name, color = if (isActive) Color(0xFFFFC857) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        frames.forEachIndexed { fi, fr ->
          Column(
            Modifier.width(if (fi == 9) 50.dp else 36.dp).height(42.dp).border(0.5.dp, Color(0x33FFFFFF)),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(fr.marks.joinToString(" "), color = if (fr.marks.contains("X") || fr.marks.contains("/")) Color(0xFFFF6B8A) else Color(0xCCFFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(fr.total?.toString() ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
          }
        }
        Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) {
          Text("${bowlTotal(rolls.getOrElse(i) { emptyList() })}", color = Color(0xFFFFC857), fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
      }
    }
  }
}

@Composable
private fun BowlBanner(id: Int, text: String, color: Color, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val s = remember { Animatable(0.2f) }
  val a = remember { Animatable(0f) }
  LaunchedEffect(id) {
    launch { a.animateTo(1f, tween(140)) }
    s.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 330f))
    delay(1300)
    a.animateTo(0f, tween(320))
    onDone()
  }
  val shape = RoundedCornerShape(50)
  Box(
    modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value }
      .clip(shape).background(color).border(2.dp, Color.White, shape).padding(horizontal = 22.dp, vertical = 10.dp)
  ) { Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp) }
}

// ───────────────────────────── Juego ─────────────────────────────

@Composable
internal fun BowlingGame(c: GameCtx) {
  val ctx = LocalContext.current
  val flow = remember { c.vm.gameState("bowl") }
  val st by flow.collectAsState(initial = null)
  fun n(k: String, d: Int = 0) = (st?.get(k) as? Number)?.toInt() ?: d
  fun dbl(k: String, def: Double = 0.0) = (st?.get(k) as? Number)?.toDouble() ?: def
  val seats = (st?.get("seats") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val started = seats.isNotEmpty()
  val rolls = (0 until 4).map { i -> (st?.get("r$i") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList() }
  val turn = n("turn")
  val mask = n("mask", BFULL)
  val tid = n("tid")
  val bump = (st?.get("bump") as? Boolean) ?: false
  val laneIdx = n("lane").coerceIn(0, BOWL_LANES.size - 1)
  val laneK = BOWL_LANES[laneIdx].hookK
  val ballIdx = n("tball").coerceIn(0, BOWL_BALLS.size - 1)
  val gameOver = (st?.get("done") as? Boolean) ?: false
  val msg = (st?.get("msg") as? String) ?: ""

  var botsSel by remember { mutableStateOf(1) }
  var bumpSel by remember { mutableStateOf(false) }
  var laneSel by remember { mutableStateOf(0) }
  var myBall by remember { mutableStateOf(Cosmetics.selectedBall(ctx)) }
  var ownedTick by remember { mutableStateOf(0) }
  var doneTid by remember { mutableStateOf(-1) }
  var primed by remember { mutableStateOf(false) }
  var shownRolls by remember { mutableStateOf<List<List<Int>>>(emptyList()) }
  var shownMask by remember { mutableStateOf(BFULL) }
  var animSim by remember { mutableStateOf<BowlSim?>(null) }
  val playT = remember { Animatable(0f) }
  val preT = remember { Animatable(-1f) }
  val scope = rememberCoroutineScope()
  var curPre by remember { mutableStateOf(1.2f) }
  var armPowerShown by remember { mutableStateOf(0.7f) }
  var armXShown by remember { mutableStateOf(0f) }
  var stayShown by remember { mutableStateOf(false) }
  var throwing by remember { mutableStateOf(false) }
  val camBlend = remember { Animatable(0f) }
  val sweep = remember { Animatable(0f) }
  val drop = remember { Animatable(1f) }
  val flash = remember { Animatable(0f) }
  val enter = remember { Animatable(0f) }
  var bannerId by remember { mutableStateOf(0) }
  var bannerText by remember { mutableStateOf<String?>(null) }
  var bannerColor by remember { mutableStateOf(Color(0xFFE53935)) }
  var aimX by remember { mutableStateOf(-0.10f) }
  var aimAng by remember { mutableStateOf(0f) }
  var aimHook by remember { mutableStateOf(0.4f) }
  var aimPower by remember { mutableStateOf(0.7f) }
  var pending by remember { mutableStateOf(-1) }
  val animating = started && doneTid != tid

  fun nameAt(i: Int): String = if (seats.getOrNull(i) == "bot") "Bot ${seats.take(i + 1).count { it == "bot" }}" else c.nameOf(seats.getOrElse(i) { "" })
  val names = seats.indices.map { nameAt(it) }

  LaunchedEffect(started) {
    enter.snapTo(0f)
    enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
  }

  /** Publica un lanzamiento: calcula la física (fuera del hilo principal), la puntuación, el próximo turno y deja todo listo para animarlo. */
  fun doThrow(seat: Int, x0: Double, ang: Double, hook: Double, power: Double) {
    if (seat !in seats.indices || gameOver || animating || throwing) return
    throwing = true
    val allRolls = (0 until 4).map { rolls[it].toMutableList() }
    val before = bowlProg(allRolls[seat])
    val seed = (System.nanoTime() and 0x7FFFFFFFL).toInt()
    val hk = hook * laneK
    val maskNow = mask
    val bumpNow = bump
    val tidNow = tid
    scope.launch {
      val sim = withContext(Dispatchers.Default) { bowlSimulate(x0, ang, hk, power, seed, maskNow, bumpNow) }
      bowlSimCache = Pair(bowlSimKey(x0, ang, hk, power, seed, maskNow, bumpNow), sim)
      val end = sim.endMask
      val knocked = bowlBits(maskNow) - bowlBits(end)
      allRolls[seat].add(knocked)
      val after = bowlProg(allRolls[seat])
      val stay = !after.finished && after.frame == before.frame
      val nextMask = if (end == 0 || !stay) BFULL else end
      var nextSeat = seat
      var over = false
      if (!stay) {
        var found = -1
        for (k in 1..seats.size) {
          val s = (seat + k) % seats.size
          if (!bowlProg(allRolls[s]).finished) { found = s; break }
        }
        if (found == -1) over = true else nextSeat = found
      }
      val label = when {
        end == 0 && maskNow == BFULL -> "¡CHUZA!"
        end == 0 -> "¡SPARE!"
        sim.gutter && knocked == 0 -> "canaleta"
        knocked == 0 -> "sin bolos"
        else -> "$knocked ${if (knocked == 1) "bolo" else "bolos"}"
      }
      var text = "${nameAt(seat)}: $label"
      if (over) {
        val best = seats.indices.maxByOrNull { bowlTotal(allRolls[it]) } ?: 0
        text = "🏆 ¡Ganó ${nameAt(best)} con ${bowlTotal(allRolls[best])} puntos!"
      }
      val data = mutableMapOf<String, Any?>(
        "seats" to seats, "turn" to nextSeat, "mask" to nextMask, "tid" to (tidNow + 1), "bump" to bumpNow, "done" to over, "msg" to text,
        "tseat" to seat, "tx" to x0, "ta" to ang, "th" to hook, "tp" to power, "tseed" to seed, "tmask" to maskNow, "tres" to end, "tstay" to stay,
        "lane" to laneIdx, "tball" to (if (seats.getOrNull(seat) == c.me) myBall else (seats.getOrNull(seat)?.let { (seat + 1) % BOWL_BALLS.size } ?: 0))
      )
      for (i in 0 until 4) data["r$i"] = allRolls[i]
      c.vm.publishGame("bowl", data)
      throwing = false
    }
  }

  // Cada lanzamiento se anima igual en todos los dispositivos a partir del estado compartido.
  LaunchedEffect(tid, started) {
    if (!started) { primed = false; return@LaunchedEffect }
    val first = !primed
    primed = true
    if (first || tid <= 0 || tid == doneTid || n("tseat", -1) < 0) {
      shownRolls = rolls; shownMask = mask; animSim = null; doneTid = tid
      return@LaunchedEffect
    }
    val ax0 = dbl("tx"); val aang = dbl("ta"); val ahook = dbl("th") * laneK; val apow = dbl("tp"); val aseed = n("tseed"); val amask = n("tmask", BFULL)
    val key = bowlSimKey(ax0, aang, ahook, apow, aseed, amask, bump)
    val sim = bowlSimCache?.takeIf { it.first == key }?.second
      ?: withContext(Dispatchers.Default) { bowlSimulate(ax0, aang, ahook, apow, aseed, amask, bump) }
    val stayAfter = (st?.get("tstay") as? Boolean) ?: false
    stayShown = stayAfter
    armPowerShown = apow.toFloat(); armXShown = ax0.toFloat()
    val pre = bowlPre(apow.toFloat())
    curPre = pre
    sweep.snapTo(0f); flash.snapTo(0f); playT.snapTo(0f); preT.snapTo(0f)
    animSim = sim
    // aproximación: pasos, balanceo del brazo y soltada
    launch { delay(150); BowlSfx.play(BowlSfx.step, 0.35f) }
    launch { delay(430); BowlSfx.play(BowlSfx.step, 0.4f) }
    launch { delay(((pre - 0.52f) * 1000).toLong().coerceAtLeast(0L)); BowlSfx.play(BowlSfx.swoosh, 0.55f) }
    launch { BowlSfx.play(BowlSfx.ledBeep, 0.35f) }
    preT.animateTo(pre, tween((pre * 1000).toInt().coerceAtLeast(300), easing = LinearEasing))
    launch { BowlSfx.play(BowlSfx.release, 0.75f) }
    launch { delay(250); BowlSfx.play(BowlSfx.rumble, 0.5f) }
    if (sim.gutter) launch { delay(600); BowlSfx.play(BowlSfx.gutter, 0.6f) }
    val hits = sim.events.filter { it[4] >= 2f && it[3] > 0.6f }
    if (hits.isNotEmpty()) {
      val firstHit = hits.firstOrNull { it[4] >= 3f } ?: hits[0]
      launch { delay((firstHit[0] * 1000).toLong()); BowlSfx.play(BowlSfx.crash, 0.85f) }
    }
    launch {
      var last = -1f
      var played = 0
      for (ev in sim.events) {
        if (played >= 26) break
        val strong = ev[3] > 0.6f
        if (!strong || ev[0] - last < 0.035f) continue
        last = ev[0]
        played++
        val buf = when {
          ev[4] >= 3f -> BowlSfx.knockLo
          ev[4] >= 2f -> if (played % 2 == 0) BowlSfx.knock else BowlSfx.knockHi
          else -> BowlSfx.thud
        }
        val vol = (0.18f + ev[3] * 0.06f).coerceIn(0.18f, 0.75f)
        val wait = ((ev[0] * 1000).toLong() - (playT.value * 1000).toLong()).coerceAtLeast(0L)
        launch { delay(wait); BowlSfx.play(buf, vol) }
      }
    }
    playT.animateTo(sim.duration, tween((sim.duration * 1000).toInt().coerceAtLeast(300), easing = LinearEasing))
    val resultEnd = n("tres", sim.endMask)
    val knocked = bowlBits(sim.startMask) - bowlBits(resultEnd)
    val strike = resultEnd == 0 && sim.startMask == BFULL
    val spare = resultEnd == 0 && sim.startMask != BFULL
    bannerId += 1
    when {
      strike -> { bannerText = "🎳 ¡CHUZA!"; bannerColor = Color(0xFFE53935) }
      spare -> { bannerText = "✨ ¡SPARE!"; bannerColor = Color(0xFF8E24AA) }
      sim.gutter && knocked == 0 -> { bannerText = "Canaleta 😬"; bannerColor = Color(0xFF546E7A) }
      knocked == 0 -> { bannerText = "Sin bolos"; bannerColor = Color(0xFF546E7A) }
      else -> { bannerText = "$knocked ${if (knocked == 1) "bolo" else "bolos"}"; bannerColor = Color(0xFF1E88E5) }
    }
    if (strike || spare) {
      launch { BowlSfx.play(BowlSfx.fanfare, 0.7f) }
      if (strike) { launch { BowlSfx.play(BowlSfx.horn, 0.8f) }; launch { delay(300); BowlSfx.play(BowlSfx.applause, 0.8f) } }
      else launch { BowlSfx.play(BowlSfx.chime, 0.8f); delay(200); BowlSfx.play(BowlSfx.applause, 0.5f) }
      launch { flash.snapTo(1f); flash.animateTo(0f, tween(800)) }
    }
    delay(500)
    launch { delay(250); BowlSfx.play(BowlSfx.rake, 0.5f) }
    sweep.animateTo(1f, tween(1700, easing = LinearEasing))
    val reset = !stayAfter
    shownRolls = rolls; shownMask = mask
    animSim = null
    if (reset) { drop.snapTo(0f); launch { drop.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) } } else drop.snapTo(1f)
    preT.snapTo(-1f)
    camBlend.snapTo(1f)
    launch { camBlend.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
    doneTid = tid
  }

  // Los bots tiran desde el primer dispositivo, apuntando al bolsillo (o al bolo restante) con algo de error.
  LaunchedEffect(tid, turn, animating, gameOver, started) {
    if (!started || gameOver || animating || seats.getOrNull(turn) != "bot" || c.me != c.firstUid) return@LaunchedEffect
    delay(1200)
    val rnd = java.util.Random()
    if (mask == BFULL) {
      val side = if (rnd.nextBoolean()) 1.0 else -1.0
      doThrow(turn, -0.10 * side + rnd.nextGaussian() * 0.045, rnd.nextGaussian() * 0.004, 0.45 * side, 0.65 + rnd.nextDouble() * 0.3)
    } else {
      var target = -1
      for (i in 0 until 10) if (((mask shr i) and 1) == 1 && (target == -1 || BPIN_POS[i].second < BPIN_POS[target].second - 1e-6 ||
          (abs(BPIN_POS[i].second - BPIN_POS[target].second) < 1e-6 && abs(BPIN_POS[i].first) < abs(BPIN_POS[target].first)))) target = i
      val tx = BPIN_POS[target].first
      val sx = (tx * 0.5).coerceIn(-0.4, 0.4)
      doThrow(turn, sx + rnd.nextGaussian() * 0.03, atan2(tx - sx, BY0) + rnd.nextGaussian() * 0.003, 0.0, 0.7 + rnd.nextDouble() * 0.2)
    }
  }

  if (!started) {
    Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Text("🎳 Bowling 3D", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(
          "Mové la bola arrastrando sobre la pista, elegí ángulo, efecto y fuerza, y lanzá: tu brazo se balancea y suelta la bola. Diez frames: chuza = 10 más los dos tiros siguientes, spare = 10 más el siguiente. Cada bolo tiene su propio peso y física real: vuelca, rueda, vuela y choca con los demás.",
          color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
      }
      Spacer(Modifier.height(10.dp))
      Text("Bots", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..3).forEach { k -> OptionChip("$k bots", selected = (botsSel == k), onClick = { botsSel = k }) }
      }
      Spacer(Modifier.height(8.dp))
      Text("Pista", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OptionChip("Con canaletas", selected = (!bumpSel), onClick = { bumpSel = false })
        OptionChip("Con barandas", selected = (bumpSel), onClick = { bumpSel = true })
      }
      Spacer(Modifier.height(8.dp))
      Text("Efecto de la pista", color = Color.White, fontWeight = FontWeight.Bold)
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BOWL_LANES.forEachIndexed { i, l -> OptionChip(l.name, selected = (laneSel == i), onClick = { laneSel = i }) }
      }
      Text(BOWL_LANES[laneSel].hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
      Spacer(Modifier.height(8.dp))
      BowlBallPicker(myBall, ownedTick) { myBall = it; Cosmetics.selectBall(ctx, it); ownedTick++ }
      Spacer(Modifier.height(10.dp))
      GradientButton("🎳 Empezar a jugar", modifier = Modifier.fillMaxWidth(), onClick = {
        val list = c.players.take(4).toMutableList()
        repeat(botsSel.coerceAtMost(4 - list.size)) { list.add("bot") }
        if (list.isEmpty()) list.add("bot")
        val data = mutableMapOf<String, Any?>(
          "seats" to list, "turn" to 0, "mask" to BFULL, "tid" to (tid + 1), "bump" to bumpSel, "lane" to laneSel, "done" to false, "msg" to "¡Primer tiro!",
          "tseat" to -1, "tx" to 0.0, "ta" to 0.0, "th" to 0.0, "tp" to 0.7, "tseed" to 0, "tmask" to BFULL, "tres" to BFULL
        )
        for (i in 0 until 4) data["r$i"] = emptyList<Int>()
        c.vm.publishGame("bowl", data)
      })
    }
    return
  }

  val myTurn = !gameOver && !animating && seats.getOrNull(turn) == c.me
  // Lo que apunta el jugador que tiene el turno se ve en vivo en los demás teléfonos, antes de lanzar.
  val liveAll by remember { c.vm.liveStates() }.collectAsState(initial = emptyMap())
  val throwerUid = seats.getOrNull(turn) ?: ""
  val remote = liveAll[throwerUid]?.takeIf { throwerUid != c.me && throwerUid != "bot" && (it["g"] as? String) == "bowl" && (it["tid"] as? Number)?.toInt() == tid }
  val remoteOn = !myTurn && !animating && !gameOver && remote != null
  fun rf(k: String, d: Float) = (remote?.get(k) as? Number)?.toFloat() ?: d
  val ex by androidx.compose.animation.core.animateFloatAsState(if (remoteOn) rf("x", 0f) else aimX, androidx.compose.animation.core.tween(260), label = "rx")
  val eAng by androidx.compose.animation.core.animateFloatAsState(if (remoteOn) rf("ang", 0f) else aimAng, androidx.compose.animation.core.tween(260), label = "ra")
  val eHook by androidx.compose.animation.core.animateFloatAsState(if (remoteOn) rf("hook", 0.4f) else aimHook, androidx.compose.animation.core.tween(260), label = "rh")
  val ePow by androidx.compose.animation.core.animateFloatAsState(if (remoteOn) rf("pow", 0.7f) else aimPower, androidx.compose.animation.core.tween(260), label = "rp")
  LaunchedEffect(myTurn, tid) {
    while (myTurn) {
      c.vm.publishLive(mapOf("g" to "bowl", "tid" to tid, "x" to aimX.toDouble(), "ang" to aimAng.toDouble(), "hook" to aimHook.toDouble(), "pow" to aimPower.toDouble()))
      delay(250)
    }
  }
  val aim = remember(ex) { floatArrayOf(ex) }
  val aimPath = remember(ex, eAng, eHook, ePow, bump, laneK) { bowlPredict(ex.toDouble(), eAng.toDouble(), eHook.toDouble() * laneK, ePow.toDouble(), bump) }
  val shownR = if (shownRolls.isEmpty()) rolls else shownRolls
  val sliderColors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8), inactiveTrackColor = Color(0x33FFFFFF))

  Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
    Box(
      Modifier.fillMaxWidth().height((gameAvail() * 0.56f).coerceIn(260.dp, 720.dp)).clip(RoundedCornerShape(22.dp)).border(2.dp, Color(0x66B388FF), RoundedCornerShape(22.dp))
    ) {
      BowlScene(
        sim = animSim, timeProvider = { playT.value }, standing = if (animSim == null) shownMask else mask, aim = aim, aimPath = aimPath,
        showAim = myTurn || remoteOn, camBlend = { camBlend.value }, sweep = { sweep.value }, drop = { drop.value }, flash = { flash.value },
        armTa = { if (preT.value < 0f) -1f else if (preT.value < curPre) preT.value else curPre + playT.value },
        armX = armXShown, armPower = armPowerShown, stay = stayShown,
        ball = BOWL_BALLS[(if (animating) ballIdx else myBall).coerceIn(0, BOWL_BALLS.size - 1)],
        modifier = Modifier.fillMaxSize(),
        onDrag = { dx, wpx -> aimX = (aimX + dx / wpx * 1.3f).coerceIn(-0.45f, 0.45f) }
      )
      val ledSeat = turn.coerceIn(0, (seats.size - 1).coerceAtLeast(0))
      val ledProg = bowlProg(shownR.getOrElse(ledSeat) { emptyList() })
      BowlLedBoard(
        name = names.getOrElse(ledSeat) { "" }, frame = ledProg.frame + 1, ball = ledProg.ball + 1,
        total = bowlTotal(shownR.getOrElse(ledSeat) { emptyList() }), text = if (animating) "" else msg, key = tid,
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.92f)
      )
      Text(
        if (animating) "🎳 ¡Lanzamiento!" else if (gameOver) "Fin de la partida" else if (myTurn) "Tu turno · arrastrá para mover la bola" else if (remoteOn) "👀 ${nameAt(turn)} está apuntando…" else "Turno de ${nameAt(turn)}",
        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 14.dp, vertical = 5.dp)
      )
      if (myTurn && !animating && !gameOver) {
        BowlPowerMeter(aimPower, { aimPower = it }, Modifier.align(Alignment.CenterStart).padding(start = 8.dp, top = 70.dp, bottom = 50.dp).width(46.dp).fillMaxHeight())
        BowlHookDial(aimHook, { aimHook = it }, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(92.dp))
        Row(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          BowlRoundBtn("◀") { aimAng = (aimAng - 0.008f).coerceIn(-0.06f, 0.06f) }
          BowlRoundBtn("▶") { aimAng = (aimAng + 0.008f).coerceIn(-0.06f, 0.06f) }
        }
        Box(
          Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(84.dp, 56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (pending != tid) Brush.horizontalGradient(listOf(Color(0xFFFF4D8D), Color(0xFFFF9A3C))) else Brush.horizontalGradient(listOf(Color(0xFF555555), Color(0xFF777777))))
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(28.dp))
            .clickable(enabled = pending != tid) {
              pending = tid
              doThrow(turn, aimX.toDouble(), aimAng.toDouble(), aimHook.toDouble(), aimPower.toDouble())
            },
          contentAlignment = Alignment.Center
        ) { Text("🎳 TIRAR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp) }
        val owned = remember(ownedTick) { Cosmetics.ownedBalls(ctx).sorted() }
        val bb = BOWL_BALLS[myBall.coerceIn(0, BOWL_BALLS.size - 1)]
        Box(
          Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp).size(46.dp).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(bb.hi, bb.mid, bb.lo))).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            .clickable { val nx = owned[(owned.indexOf(myBall).coerceAtLeast(0) + 1) % owned.size]; myBall = nx; Cosmetics.selectBall(ctx, nx); ownedTick++ },
          contentAlignment = Alignment.Center
        ) { Text("🔄", fontSize = 14.sp) }
      }
      val bt = bannerText
      if (bt != null) androidx.compose.runtime.key(bannerId) {
        BowlBanner(bannerId, bt, bannerColor, { bannerText = null }, Modifier.align(Alignment.Center))
      }
    }
    Spacer(Modifier.height(8.dp))
    if (gameOver && !animating) {
      ResultBanner(msg)
      GradientButton("Revancha", modifier = Modifier.fillMaxWidth(), onClick = {
        val data = mutableMapOf<String, Any?>(
          "seats" to seats, "turn" to 0, "mask" to BFULL, "tid" to (tid + 1), "bump" to bump, "lane" to laneIdx, "done" to false, "msg" to "¡Revancha!",
          "tseat" to -1, "tx" to 0.0, "ta" to 0.0, "th" to 0.0, "tp" to 0.7, "tseed" to 0, "tmask" to BFULL, "tres" to BFULL
        )
        for (i in 0 until 4) data["r$i"] = emptyList<Int>()
        c.vm.publishGame("bowl", data)
      })
    } else if (myTurn) {
      Text("Arrastra sobre la pista para mover la bola · fuerza y efecto a los lados", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    } else {
      Text(if (animating) "…" else msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(8.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp) {
      BowlScoreboard(names, shownR, if (animating || gameOver) -1 else turn)
    }
  }
}
