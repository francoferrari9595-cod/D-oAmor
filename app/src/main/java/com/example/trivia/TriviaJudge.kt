package com.example.trivia

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

internal enum class TVerdict { CORRECT, INCORRECT, AMBIGUOUS }

/** Resultado del juez local. [score] 0..1 es la mejor similitud con alguna forma aceptada. */
internal data class LocalResult(val verdict: TVerdict, val score: Float, val note: String)

private val ACCENTS = Regex("\\p{InCombiningDiacriticalMarks}+")
private val ARTICLES = setOf("el", "la", "los", "las", "un", "una", "de", "del", "al", "y", "es", "fue", "era", "se", "llama", "en", "por", "que", "lo", "su")
private val NEGATIONS = setOf("no", "ni", "nunca", "tampoco", "jamas")

/** Minúsculas, sin tildes ni signos; «300.000» → 300000 y «3,14» → 3.14. */
internal fun tNorm(s: String): String {
  var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(ACCENTS, "")
  t = t.replace(Regex("(\\d)[.,](?=\\d{3}(?!\\d))"), "$1")
  t = t.replace(Regex("(\\d),(\\d{1,2})(?!\\d)"), "$1.$2")
  t = t.replace(Regex("[^a-z0-9.ñ ]"), " ").replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
  return t.replace(Regex("\\s+"), " ").trim()
}

private val UNITS = mapOf(
  "cero" to 0, "uno" to 1, "un" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
  "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
  "veinte" to 20, "veintiuno" to 21, "veintidos" to 22, "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29,
  "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90,
  "cien" to 100, "ciento" to 100, "doscientos" to 200, "trescientos" to 300, "cuatrocientos" to 400, "quinientos" to 500, "seiscientos" to 600, "setecientos" to 700, "ochocientos" to 800, "novecientos" to 900
)

/** Convierte números escritos en español («mil novecientos ochenta y nueve») a cifras; si no es un número devuelve null. */
internal fun tSpanishNumber(norm: String): Double? {
  norm.toDoubleOrNull()?.let { return it }
  val toks = norm.split(" ").filter { it.isNotEmpty() && it != "y" }
  if (toks.isEmpty() || toks.any { it != "mil" && it !in UNITS }) return null
  var total = 0; var cur = 0
  for (t in toks) {
    if (t == "mil") { total += (if (cur == 0) 1 else cur) * 1000; cur = 0 } else cur += UNITS.getValue(t)
  }
  return (total + cur).toDouble()
}

private fun lev(a: String, b: String): Int {
  if (a == b) return 0
  if (a.isEmpty()) return b.length
  if (b.isEmpty()) return a.length
  var prev = IntArray(b.length + 1) { it }
  var cur = IntArray(b.length + 1)
  for (i in 1..a.length) {
    cur[0] = i
    for (j in 1..b.length) cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
    val tmp = prev; prev = cur; cur = tmp
  }
  return prev[b.length]
}

private fun sim(a: String, b: String): Float {
  val m = max(a.length, b.length)
  return if (m == 0) 1f else 1f - lev(a, b).toFloat() / m
}

private fun contentTokens(n: String) = n.split(" ").filter { it.isNotEmpty() && it !in ARTICLES }

/**
 * Juez local: acepta exactos, cifras/letras equivalentes, faltas de ortografía leves y la respuesta dentro de una frase.
 * Lo que no pueda decidir con seguridad lo marca AMBIGUO (o INCORRECTO si no se parece en nada) para que lo revise la IA o el administrador.
 */
internal fun tJudgeLocal(q: TQ, given: String): LocalResult {
  val g = tNorm(given)
  if (g.isEmpty()) return LocalResult(TVerdict.INCORRECT, 0f, "Respuesta vacía.")
  val gTokens = g.split(" ")
  val negated = gTokens.any { it in NEGATIONS }
  val gNum = tSpanishNumber(g)
  var best = 0f
  for (cand in listOf(q.a) + q.alt) {
    val c = tNorm(cand)
    if (c.isEmpty()) continue
    // 1) idéntica (con números en letras o cifras)
    val cNum = tSpanishNumber(c)
    if (g == c || (gNum != null && cNum != null && gNum == cNum)) return LocalResult(if (negated) TVerdict.AMBIGUOUS else TVerdict.CORRECT, 1f, if (negated) "La respuesta incluye una negación." else "Coincide con la respuesta esperada.")
    // 2) la respuesta esperada aparece completa dentro de la frase del jugador
    val cTok = contentTokens(c); val gTok = contentTokens(g)
    if (cTok.isNotEmpty() && gTok.size >= cTok.size && gTok.size <= cTok.size + 4) {
      val joined = " " + gTok.joinToString(" ") + " "
      if (joined.contains(" " + cTok.joinToString(" ") + " ")) return LocalResult(if (negated) TVerdict.AMBIGUOUS else TVerdict.CORRECT, 0.97f, if (negated) "La respuesta incluye una negación." else "Contiene la respuesta esperada.")
    }
    // 3) errores de ortografía leves (solo textos de 5 letras o más para no aceptar números cercanos)
    val s = sim(c, g)
    if (s > best) best = s
    if (cNum == null && c.length >= 5 && s >= 0.85f) return LocalResult(if (negated) TVerdict.AMBIGUOUS else TVerdict.CORRECT, s, "Coincide salvo por la ortografía.")
  }
  // 4) respuesta parcial de un nombre largo (apellido, una palabra): puede ser válida o no → ambigua
  val anyShared = (listOf(q.a) + q.alt).any { c -> contentTokens(tNorm(c)).any { t -> t.length >= 4 && contentTokens(g).any { gt -> sim(t, gt) >= 0.8f } } }
  return when {
    anyShared || best >= 0.6f -> LocalResult(TVerdict.AMBIGUOUS, best, "Se parece, pero no coincide del todo.")
    else -> LocalResult(TVerdict.INCORRECT, best, "No coincide con la respuesta esperada.")
  }
}
