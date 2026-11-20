package com.example.trivia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriviaRulesTest {
  private val paris = TQ(900, "geo", 1, "¿Capital de Francia?", "París", listOf("Paris"))
  private val year = TQ(901, "hist", 2, "¿Año de la caída del Muro de Berlín?", "1989", listOf("mil novecientos ochenta y nueve"))
  private val washington = TQ(902, "hist", 1, "¿Primer presidente de EE. UU.?", "George Washington", listOf("Washington"))
  private val shakira = TQ(903, "mus", 2, "¿Cantante de «Hips Don't Lie»?", "Shakira")

  @Test fun acceptsAccentAndCaseVariations() {
    assertEquals(TVerdict.CORRECT, tJudgeLocal(paris, "PARIS").verdict)
  }

  @Test fun acceptsAnswerInsideASentence() {
    assertEquals(TVerdict.CORRECT, tJudgeLocal(paris, "La capital es París").verdict)
  }

  @Test fun acceptsNumbersWrittenInWords() {
    assertEquals(TVerdict.CORRECT, tJudgeLocal(year, "mil novecientos ochenta y nueve").verdict)
  }

  @Test fun rejectsAWrongNumber() {
    assertEquals(TVerdict.INCORRECT, tJudgeLocal(year, "1500").verdict)
  }

  @Test fun acceptsSmallSpellingMistakes() {
    assertEquals(TVerdict.CORRECT, tJudgeLocal(shakira, "Shakyra").verdict)
  }

  @Test fun partialNameIsAmbiguousUnlessListedAsAlias() {
    assertEquals(TVerdict.AMBIGUOUS, tJudgeLocal(washington, "George").verdict)
    assertEquals(TVerdict.CORRECT, tJudgeLocal(washington, "Washington").verdict)
  }

  @Test fun negationIsNeverAcceptedAutomatically() {
    assertEquals(TVerdict.AMBIGUOUS, tJudgeLocal(paris, "no es París").verdict)
  }

  @Test fun emptyAnswerIsIncorrect() {
    assertEquals(TVerdict.INCORRECT, tJudgeLocal(paris, "   ").verdict)
  }

  @Test fun parsesSpanishNumbers() {
    assertEquals(29.0, tSpanishNumber("veintinueve")!!, 0.0)
    assertEquals(180.0, tSpanishNumber("ciento ochenta")!!, 0.0)
    assertEquals(1989.0, tSpanishNumber("mil novecientos ochenta y nueve")!!, 0.0)
    assertEquals(null, tSpanishNumber("hola"))
  }

  @Test fun normalizesThousandsAndDecimals() {
    assertEquals("300000", tNorm("300.000"))
    assertEquals("3.14", tNorm("3,14"))
  }

  @Test fun bankCoversAllElevenCategoriesAndThreeLevels() {
    assertEquals(11, T_CATS.size)
    for (cat in T_CATS) {
      val qs = T_BANK.filter { it.cat == cat.id }
      assertTrue("${cat.name} necesita preguntas", qs.size >= 8)
      for (d in 1..3) assertTrue("${cat.name} sin dificultad $d", qs.any { it.diff == d })
    }
    assertEquals(T_BANK.size, T_BANK.map { it.id }.toSet().size)
  }

  @Test fun questionClockPausesWhileSomeoneAnswers() {
    // 30 s por pregunta; la pregunta empezó en t=1000 y alguien pulsó en t=6000 (5 s consumidos)
    val answering = TS(mapOf("phase" to "answering", "cQ" to 30L, "baseUsed" to 0L, "resumeAt" to 1000L, "buzzAt" to 6000L))
    assertEquals(25_000L, answering.remainingMs(999_999L))
    // tras un error el reloj se reanuda con el tiempo que quedaba (rebote): empieza en t=9000 con 5 s ya usados
    val rebound = TS(mapOf("phase" to "question", "cQ" to 30L, "baseUsed" to 5000L, "resumeAt" to 9000L))
    assertEquals(20_000L, rebound.remainingMs(14_000L))
    assertEquals(0L, rebound.remainingMs(99_000L))
  }
}
