package com.example.soccer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class SoccerEngineTest {
  private fun playing(size: Int = 2): SoccerWorld {
    val w = SoccerWorld(size, 1, 90f, Random(3))
    w.phase = PH_PLAY; w.ball.owner = -1
    return w
  }

  @Test fun simulationStaysFiniteAndInsideTheField() {
    val w = SoccerWorld(3, 2, 90f, Random(7))
    repeat(60 * 12) { w.update(0.016f) }
    assertFalse(w.ball.x.isNaN() || w.ball.y.isNaN())
    for (p in w.players) { assertTrue(kotlin.math.abs(p.x) <= S_HW + 3f); assertTrue(kotlin.math.abs(p.y) <= S_HL + 4f) }
  }

  @Test fun ballCrossingTheGoalLineBetweenThePostsIsAGoal() {
    val w = playing()
    for (p in w.players) { p.x = 30f; p.y = 0f; p.vx = 0f; p.vy = 0f }
    w.ball.x = 0f; w.ball.y = S_HL - 0.1f; w.ball.z = 0.5f; w.ball.vx = 0f; w.ball.vy = 12f; w.ball.vz = 0f
    w.update(0.05f)
    assertEquals(1, w.score[0])
    assertEquals(PH_GOAL, w.phase)
  }

  @Test fun ballOverTheSidelineStopsPlayForARestart() {
    val w = playing()
    for (p in w.players) { p.x = -30f; p.y = 40f; p.vx = 0f; p.vy = 0f }
    w.ball.x = S_HW - 0.1f; w.ball.y = 0f; w.ball.z = 0f; w.ball.vx = 10f; w.ball.vy = 0f
    w.update(0.05f)
    assertEquals(PH_RESTART, w.phase)
  }

  @Test fun shootingGivesTheBallSpeedAndReleasesPossession() {
    val w = SoccerWorld(2, 1, 90f, Random(1))
    val me = w.assignHuman("u", "Yo", 0)!!
    me.x = 0f; me.y = 0f
    w.ball.owner = me.id; w.ball.x = me.x; w.ball.y = me.y
    w.phase = PH_PLAY
    w.setInput("u", 0f, -1f, false)
    w.shootRelease("u", 1f)
    assertEquals(-1, w.ball.owner)
    assertTrue(hypot(w.ball.vx, w.ball.vy) > 12f)
  }

  @Test fun fullMatchEndsAfterTwoHalves() {
    val w = SoccerWorld(1, 1, 10f, Random(5))
    repeat(60 * 40) { w.update(0.016f) }
    assertEquals(PH_OVER, w.phase)
  }

  @Test fun snapshotRoundTripKeepsPositionsAndScore() {
    val w = SoccerWorld(2, 1, 90f, Random(9))
    repeat(120) { w.update(0.016f) }
    w.score[0] = 2; w.score[1] = 1
    val mirror = SoccerWorld(2, 1, 90f, Random(1), true)
    mirror.applySnapshot(w.encode())
    assertEquals(2, mirror.score[0]); assertEquals(1, mirror.score[1])
    for (p in w.players) assertTrue(hypot(p.x - mirror.players[p.id].x, p.y - mirror.players[p.id].y) < 0.02f)
  }
}
