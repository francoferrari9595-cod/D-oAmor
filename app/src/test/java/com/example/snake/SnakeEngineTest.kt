package com.example.snake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class SnakeEngineTest {
  @Test fun snakeMovesContinuouslyAndFollowsItsHeadPath() {
    val w = SnakeWorld(SMode.CLASSIC, 0, Random(1))
    val s = w.addSnake("t", SN_COLORS[0], false, "me")
    w.step()
    assertTrue(s.alive)
    assertEquals(10, s.segs)
    assertEquals(10, s.cur.size / 2)
    val hx = s.x; val hy = s.y
    w.step()
    // avanza ~ velocidad × dt en un solo paso (no salta de casilla en casilla)
    val moved = hypot(s.x - hx, s.y - hy)
    assertTrue("movió $moved", moved > 0.1f && moved < 0.4f)
    // el cuerpo mantiene su separación
    for (i in 1 until s.cur.size / 2) {
      val d = hypot(s.cur[i * 2] - s.cur[i * 2 - 2], s.cur[i * 2 + 1] - s.cur[i * 2 - 1])
      assertTrue("segmento $i separado $d", d in 0.2f..0.6f)
    }
  }

  @Test fun turningIsLimitedSoTheSnakeMakesWideCurves() {
    val w = SnakeWorld(SMode.PORTAL, 0, Random(2))
    val s = w.addSnake("t", SN_COLORS[0], false, "me")
    w.step()
    val a0 = s.ang
    w.turnTo(s, a0 + 3.0f)               // casi media vuelta
    w.step()
    assertTrue("giró de golpe ${abs(angDiff(s.ang, a0))}", abs(angDiff(s.ang, a0)) < 0.2f)
    repeat(40) { w.step() }
    assertTrue(abs(angDiff(s.ang, a0 + 3.0f)) < 0.05f)
  }

  @Test fun classicSnakeEventuallyHitsTheWall() {
    val w = SnakeWorld(SMode.CLASSIC, 0, Random(1))
    val s = w.addSnake("t", SN_COLORS[0], false, "me")
    repeat(900) { w.step() }
    assertTrue(w.over)
    assertFalse(s.alive)
  }

  @Test fun eatingAnAppleGrowsTheSnake() {
    val w = SnakeWorld(SMode.CLASSIC, 0, Random(4))
    val s = w.addSnake("t", SN_COLORS[0], false, "me")
    w.step()
    w.pickups.clear()
    w.pickups.add(SPick(0, s.x + 0.6f * kotlin.math.cos(s.ang), s.y + 0.6f * kotlin.math.sin(s.ang)))
    w.step()
    assertEquals(1, s.apples)
    assertEquals(12, s.segs)
  }

  @Test fun portalModeWrapsAroundInsteadOfDying() {
    val w = SnakeWorld(SMode.PORTAL, 0, Random(3))
    val s = w.addSnake("t", SN_COLORS[0], false, "me")
    repeat(600) { w.step() }
    assertTrue(s.alive)
  }

  @Test fun botsPlayWithoutCrashingAndStayFinite() {
    val w = SnakeWorld(SMode.ARENA, 5, Random(9))
    repeat(900) { w.step() }
    for (s in w.snakes) if (s.alive) { assertFalse(s.x.isNaN() || s.y.isNaN()); assertTrue(s.cur.size >= 20) }
  }

  @Test fun snapshotRoundTripKeepsBodies() {
    val w = SnakeWorld(SMode.ARENA, 3, Random(9))
    repeat(60) { w.step() }
    val mirror = SnakeWorld(SMode.ARENA, 0, Random(1), true)
    mirror.applySnapshot(w.encode())
    for (s in w.snakes) if (s.alive) {
      val m = mirror.snakes[s.id]
      assertEquals(s.cur.size, m.cur.size)
      for (i in s.cur.indices) assertEquals("punto $i", s.cur[i], m.cur[i], 0.08f)
    }
    assertEquals(w.pickups.size, mirror.pickups.size)
  }
}
