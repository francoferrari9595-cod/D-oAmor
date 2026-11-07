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

  @Test
  fun experimentalMedicineImmunizesOrganDirectly() {
    // 50..54 = medicinas experimentales (dos pastillitas)
    assertTrue(Slot(0, listOf(50)).immune)
    assertTrue(Slot(2, listOf(52)).immune)
    assertTrue(Slot(1, listOf(54)).immune)
  }

  @Test
  fun normalMedicineNeedsTwoToImmunize() {
    assertFalse(Slot(0, listOf(20)).immune)
    assertTrue(Slot(0, listOf(20, 20)).immune)
  }

  @Test
  fun organWithMulticolorMedicineCanBeAttackedByAnyColor() {
    // 24 = medicina multicolor sobre un corazón (0); un virus azul (12) normalmente no lo infectaría.
    assertFalse(slotVirusMatches(Slot(0, emptyList()), 12))
    assertTrue(slotVirusMatches(Slot(0, listOf(24)), 12))
    assertTrue(slotVirusMatches(Slot(1, listOf(24)), 13))
  }
}
