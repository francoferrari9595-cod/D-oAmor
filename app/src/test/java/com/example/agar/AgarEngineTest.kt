package com.example.agar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AgarEngineTest {
  /** Arena vacía (sin bots, comida ni virus) para probar una regla a la vez. */
  private fun emptyWorld() = AgarWorld(size = 7000f, botCount = 0, pelletTarget = 0, virusTarget = 0, rnd = Random(7))

  private fun place(w: AgarWorld, p: AgarPlayer, x: Float, y: Float, mass: Float) {
    p.cells.clear(); p.cells.add(PCell(x, y, mass)); p.alive = true; p.tx = x; p.ty = y
  }

  @Test fun sizeIsRootOfMassTimesHundred() {
    assertEquals(100f, aSize(100f), 0.001f)
    assertEquals(sqrt10(), aSize(10f), 0.01f)
  }
  private fun sqrt10() = kotlin.math.sqrt(1000f)

  @Test fun bigCellsAreSlower() {
    assertTrue(aSpeed(aSize(10f)) > aSpeed(aSize(1000f)))
  }

  @Test fun splitHalvesMassAndCreatesTwoCells() {
    val w = emptyWorld(); val p = w.human
    place(w, p, 3500f, 3500f, 100f)
    p.tx = 4000f; p.ty = 3500f
    w.split(p)
    assertEquals(2, p.cells.size)
    assertEquals(50f, p.cells[0].mass, 0.001f); assertEquals(50f, p.cells[1].mass, 0.001f)
  }

  @Test fun cannotSplitBelowMinimumMass() {
    val w = emptyWorld(); val p = w.human
    place(w, p, 3500f, 3500f, 30f)
    w.split(p)
    assertEquals(1, p.cells.size)
  }

  @Test fun maximumSixteenCells() {
    val w = emptyWorld(); val p = w.human
    place(w, p, 3500f, 3500f, 20000f)
    p.tx = 4000f; p.ty = 3500f
    repeat(6) { w.split(p) }
    assertEquals(A_MAX_CELLS, p.cells.size)
  }

  @Test fun ejectCostsSixteenAndCreatesTwelve() {
    val w = emptyWorld(); val p = w.human
    place(w, p, 3500f, 3500f, 100f)
    p.tx = 4000f; p.ty = 3500f
    w.eject(p)
    assertEquals(84f, p.cells[0].mass, 0.001f)
    assertEquals(1, w.ejects.size)
    assertEquals(12f, w.ejects[0].mass, 0.001f)
  }

  @Test fun cannotEjectBelowMinimumMass() {
    val w = emptyWorld(); val p = w.human
    place(w, p, 3500f, 3500f, 30f)
    w.eject(p)
    assertEquals(0, w.ejects.size)
  }

  @Test fun eatsCellWithAtLeast125TimesTheMass() {
    val w = AgarWorld(size = 7000f, botCount = 1, pelletTarget = 0, virusTarget = 0, rnd = Random(3))
    val a = w.human; val b = w.players[1]
    place(w, a, 3500f, 3500f, 130f); place(w, b, 3500f, 3500f, 100f)
    w.update(0.01f)
    assertFalse(b.alive)
    assertTrue(a.totalMass > 220f)
  }

  @Test fun doesNotEatCellWithLessThanTheRatio() {
    val w = AgarWorld(size = 7000f, botCount = 1, pelletTarget = 0, virusTarget = 0, rnd = Random(3))
    val a = w.human; val b = w.players[1]
    place(w, a, 3500f, 3500f, 120f); place(w, b, 3500f, 3500f, 100f)
    b.think = 999f   // el bot no se mueve en este instante
    w.update(0.01f)
    assertTrue(b.alive)
  }

  @Test fun eatingFoodAddsOneMassEach() {
    val w = AgarWorld(size = 7000f, botCount = 0, pelletTarget = 400, virusTarget = 0, rnd = Random(11))
    val p = w.human
    place(w, p, 3500f, 3500f, 20000f)   // célula enorme: se come lo que toque
    val before = p.totalMass
    w.update(0.02f)
    assertTrue(p.pellets > 0)
    assertEquals(before + p.pellets, p.totalMass, before * 0.01f + 1f)
  }

  @Test fun splitCellsRecombineAfterThirtySecondsPlusMass() {
    val w = emptyWorld(); val p = w.human
    place(w, p, 3500f, 3500f, 100f)
    p.tx = 3500f; p.ty = 3500f
    w.split(p)
    assertEquals(2, p.cells.size)
    assertEquals(32f, p.cells[0].mergeAt - w.time, 0.01f)   // 30 s + 0,02 × 100
    repeat(400) { w.update(0.05f) }                          // 20 s: todavía separadas
    assertEquals(2, p.cells.size)
    repeat(400) { w.update(0.05f) }                          // 40 s en total: ya se reunieron
    assertEquals(1, p.cells.size)
  }

  @Test fun massDecaysSlowly() {
    val w = emptyWorld(); val p = w.human
    place(w, p, 3500f, 3500f, 1000f)
    repeat(200) { w.update(0.05f) }   // 10 s
    assertTrue(p.totalMass < 1000f && p.totalMass > 975f)
  }

  @Test fun snapshotRoundTripKeepsPlayersFoodAndViruses() {
    val host = AgarWorld(7000f, 3, 300, 4, Random(5))
    val mirror = AgarWorld(7000f, 0, 300, 0, Random(1), 12, true)
    mirror.applySnapshot(host.encode())
    assertEquals(host.players.size, mirror.players.size)
    assertEquals(host.players.sumOf { if (it.alive) it.cells.size else 0 }, mirror.players.sumOf { it.cells.size })
    assertEquals(host.viruses.size, mirror.viruses.size)
    for (s in 0 until 300) {
      assertEquals(host.palive[s], mirror.palive[s])
      if (host.palive[s]) assertEquals(host.px[s], mirror.px[s], 0.01f)
    }
  }

  @Test fun anotherPlayerCanTakeOverTheWorld() {
    val host = AgarWorld(7000f, 3, 300, 4, Random(5), 12, false, true)
    val ana = host.newPlayer("Ana", 2, "u1"); host.spawn(ana)
    val mirror = AgarWorld(7000f, 0, 300, 0, Random(1), 12, true)
    mirror.applySnapshot(host.encode())
    val sim = AgarWorld(7000f, 0, 300, 0, Random(2), 12, false, true)
    sim.loadFrom(mirror)
    assertEquals(host.players.size, sim.players.size)
    assertTrue(sim.players.any { it.uid == "u1" && it.alive })
    repeat(20) { sim.update(0.05f) }
    assertEquals(host.viruses.size, sim.viruses.size)
  }
}
