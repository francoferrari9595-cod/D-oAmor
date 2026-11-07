package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokerHandEvaluatorTest {
  private fun card(suit: Int, rank: Int): Int = suit * 13 + rank

  @Test fun royalFlushBeatsLowerStraightFlush() {
    val royal = listOf(8, 9, 10, 11, 12).map { card(1, it) }
    val lower = (4..8).map { card(1, it) }
    assertTrue(rank5(royal) > rank5(lower))
    assertEquals("Escalera de color", handName(rank5(royal)))
  }

  @Test fun fourOfAKindBeatsFullHouse() {
    val quads = listOf(card(0, 12), card(1, 12), card(2, 12), card(3, 12), card(0, 8))
    val fullHouse = listOf(card(0, 11), card(1, 11), card(2, 11), card(0, 10), card(1, 10))
    assertTrue(rank5(quads) > rank5(fullHouse))
    assertEquals("Póker", handName(rank5(quads)))
    assertEquals("Full", handName(rank5(fullHouse)))
  }

  @Test fun sixHighStraightBeatsWheel() {
    val wheel = listOf(card(0, 12), card(1, 0), card(2, 1), card(3, 2), card(0, 3))
    val sixHigh = (0..4).map { card(it % 4, it) }
    assertEquals("Escalera", handName(rank5(wheel)))
    assertTrue(rank5(sixHigh) > rank5(wheel))
  }

  @Test fun bestOfSevenFindsBestFiveCards() {
    val cards = listOf(
      card(0, 12), card(0, 11), card(0, 10), card(0, 9), card(0, 8),
      card(1, 12), card(2, 12)
    )
    assertEquals("Escalera de color", handName(bestOf7(cards)))
  }
}
