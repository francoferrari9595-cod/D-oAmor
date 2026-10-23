package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirusRulesTest {
  // Carta = tipo*10 + color; órganos 0..4 (4 = comodín), virus 10..14 (14 = arcoíris).
  @Test
  fun rainbowVirusInfectsEveryOrganColor() {
    for (organ in 0..4) assertTrue("El virus arcoíris debe infectar el órgano $organ", virusMatches(organ, 14))
  }

  @Test
  fun coloredVirusOnlyInfectsSameColorOrWildcardOrgan() {
    assertTrue(virusMatches(0, 10))
    assertFalse(virusMatches(1, 10))
    assertFalse(virusMatches(2, 13))
    assertTrue(virusMatches(4, 12))
  }
}
