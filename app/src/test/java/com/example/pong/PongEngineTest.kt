package com.example.pong

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PongEngineTest {
  @Test fun ballHittingTheNetGivesThePointToTheOtherPlayer() {
    val w = PongWorld(false, 1, 11, Random(2))
    w.state = ST_RALLY; w.lastHitter = 0
    w.b.x = 0f; w.b.y = P_TY + 0.06f; w.b.z = -0.01f; w.b.vx = 0f; w.b.vy = 0f; w.b.vz = 3f
    w.step(0.016f)
    assertEquals(1, w.scoreB)
    assertEquals(0, w.scoreA)
  }

  @Test fun rallyAgainstTheAiProducesPointsAndStaysFinite() {
    val w = PongWorld(false, 2, 11, Random(5))
    repeat(2500) {
      w.step(0.016f)
      if (w.state == ST_SERVE && w.server == 0) w.serve(0, 0f)
    }
    assertTrue(w.scoreA + w.scoreB > 0)
    assertFalse(w.b.x.isNaN() || w.b.y.isNaN() || w.b.z.isNaN())
  }

  @Test fun gameEndsAtTargetWithTwoPointLead() {
    val w = PongWorld(false, 1, 3, Random(1))
    w.scoreA = 2; w.scoreB = 0
    w.state = ST_RALLY; w.lastHitter = 1
    w.b.x = 0f; w.b.y = P_TY + 0.06f; w.b.z = 1.2f; w.b.vx = 0f; w.b.vy = 0f; w.b.vz = 4f   // la pelota cae fuera sin botar en la mesa: falta de quien la golpeó (B), punto para A
    repeat(60) { w.step(0.016f) }
    assertEquals(ST_OVER, w.state)
  }

  @Test fun everyServeClearsTheNetAndBouncesOnBothHalves() {
    for (seed in 1..12) for (side in 0..1) {
      val w = PongWorld(false, 1, 11, Random(seed))
      w.humanB = true            // la IA no interfiere: se prueba solo el saque
      w.server = side; w.state = ST_SERVE
      w.ax = (seed - 6) * 0.1f; w.bx = (6 - seed) * 0.1f
      w.serve(side, (seed % 3 - 1) * 0.2f)
      var guard = 0
      val limit = 1.3f
      while (kotlin.math.abs(w.b.z) < limit && w.state == ST_RALLY && guard++ < 1500) w.step(0.004f)
      assertEquals("saque seed=$seed lado=$side debe seguir en juego", ST_RALLY, w.state)
      assertEquals(0, w.scoreA + w.scoreB)
    }
  }

  @Test fun reachableReturnsKeepTheRallyAlive() {
    // dos «jugadores» perfectos (las palas siguen a la pelota): los intercambios deben prolongarse sin faltas absurdas
    val w = PongWorld(false, 3, 11, Random(11))
    w.humanB = true
    var longest = 0
    repeat(6000) {
      if (w.state == ST_SERVE) w.serve(w.server, 0f)
      if (w.state == ST_RALLY) {
        val trackY = w.b.y.coerceIn(P_TY + 0.05f, 1.45f)
        if (w.b.vz > 0f) { w.bx = w.b.x; w.by = trackY; w.bz = w.b.z.coerceIn(0.3f, 1.9f) } else { w.ax = w.b.x; w.ay = trackY; w.az = w.b.z.coerceIn(-1.9f, -0.3f) }
        longest = maxOf(longest, w.rallyHits)
      }
      w.step(0.004f)
    }
    assertTrue("intercambio máximo = $longest", longest >= 6)
  }
}
