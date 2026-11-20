package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrucoRulesTest {
  @Test fun spanishDeckHasFortyUniqueCards() {
    val deck = tDeck()
    assertEquals(40, deck.size)
    assertEquals(40, deck.toSet().size)
  }

  @Test fun hierarchyMatchesArgentineTruco() {
    assertTrue(tPower(41) > tPower(61))    // 1 de espada > 1 de basto
    assertTrue(tPower(47) > tPower(7))     // 7 de espada > 7 de oro
    assertTrue(tPower(7) > tPower(3))      // 7 de oro > 3
    assertTrue(tPower(3) > tPower(2))      // 3 > 2
    assertTrue(tPower(61) > tPower(2))     // 1 de basto > 2
  }

  @Test fun envidoUsesBestSameSuitPairAndFiguresAreZero() {
    assertEquals(33, tEnvido(listOf(7, 6, 21)))
    assertEquals(20, tEnvido(listOf(10, 11, 12)))
    assertEquals(7, tEnvido(listOf(7, 21, 42)))
  }

  @Test fun handEndsWhenATeamWinsTwoTricks() {
    assertEquals(0, trucoHandWinner(listOf(0, 0), 1))
    assertEquals(-2, trucoHandWinner(listOf(0, 1), 1))
    assertEquals(0, trucoHandWinner(listOf(0, -1), 1))   // gana la primera, la segunda es parda
    assertEquals(1, trucoHandWinner(listOf(-1, 1), 0))   // parda y luego gana el segundo
    assertEquals(-2, trucoHandWinner(listOf(-1, -1), 0))
    assertEquals(1, trucoHandWinner(listOf(-1, -1, -1), 1)) // todo parda: gana la mano
    assertEquals(0, trucoHandWinner(listOf(0, 1, -1), 1))   // 1-1 y tercera parda: la primera
  }
}
