package com.example.ui.screens

import android.content.Context

/**
 * Diccionarios de ¡Basta! para revisar que las palabras estén bien escritas. Se consultan tres bases:
 *  1) res/raw/basta_dict_categorias.txt  → palabras por categoría de la carta.
 *  2) res/raw/basta_dict_general.txt     → palabras comunes del español.
 *  3) TuttiFruttiLexicon                 → vocabulario integrado de Tutti Frutti + su archivo ampliado.
 * Para ampliarlas basta con agregar palabras a los archivos de texto (no hay que tocar el código).
 */
internal object BastaDict {
  /** Resultado de la revisión de una palabra. */
  const val OK_CATEGORY = 0   // está en la categoría de la carta
  const val OK_WORD = 1       // es una palabra real, pero no la encontramos en esta categoría
  const val UNKNOWN = 2       // no está en ningún diccionario

  private val byCategory = HashMap<String, MutableSet<String>>()
  private var general: Set<String> = emptySet()
  private var allCategoryWords: Set<String> = emptySet()
  @Volatile private var loaded = false

  /** Minúsculas, sin tildes (la ñ se conserva), sin signos y con espacios simples. */
  fun norm(value: String): String {
    val t = value.trim().lowercase().replace("ñ", "\u0001")
    val d = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace("\u0001", "ñ")
    return d.replace(Regex("[^a-zñ0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
  }

  /** Quita el emoji del nombre de la carta («🍎 Frutas» → «frutas»). */
  fun categoryKey(label: String): String = norm(label.dropWhile { !it.isLetter() })

  fun load(ctx: Context) {
    if (loaded) return
    synchronized(this) {
      if (loaded) return
      runCatching {
        val map = HashMap<String, MutableSet<String>>()
        ctx.resources.openRawResource(com.example.R.raw.basta_dict_categorias).bufferedReader(Charsets.UTF_8).useLines { lines ->
          for (line in lines) {
            if (line.isBlank() || line.startsWith("#") || !line.contains("|")) continue
            val set = map.getOrPut(categoryKey(line.substringBefore("|"))) { HashSet() }
            line.substringAfter("|").split(";").map(::norm).filter { it.isNotBlank() }.forEach { set.add(it) }
          }
        }
        byCategory.putAll(map)
        allCategoryWords = map.values.flatten().toSet()
      }
      runCatching {
        val g = HashSet<String>()
        ctx.resources.openRawResource(com.example.R.raw.basta_dict_general).bufferedReader(Charsets.UTF_8).useLines { lines ->
          for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            line.split(Regex("\\s+")).map(::norm).filter { it.isNotBlank() }.forEach { g.add(it) }
          }
        }
        general = g
      }
      TuttiFruttiLexicon.loadExtra(ctx)
      loaded = true
    }
  }

  /** Variantes simples para tolerar plural/singular y género: «gatos», «gato», «gata». */
  private fun variants(w: String): List<String> {
    val v = ArrayList<String>(6)
    v.add(w)
    if (w.endsWith("es") && w.length > 4) v.add(w.dropLast(2))
    if (w.endsWith("s") && w.length > 3) v.add(w.dropLast(1))
    if (w.endsWith("a") && w.length > 3) v.add(w.dropLast(1) + "o")
    if (w.endsWith("o") && w.length > 3) v.add(w.dropLast(1) + "a")
    return v
  }

  fun check(categoryLabel: String, word: String): Int {
    val w = norm(word)
    if (w.length < 2) return UNKNOWN
    val cat = byCategory[categoryKey(categoryLabel)]
    for (x in variants(w)) if (cat?.contains(x) == true) return OK_CATEGORY
    for (x in variants(w)) {
      if (x in allCategoryWords || x in general || TuttiFruttiLexicon.isRealWord(x)) return OK_WORD
    }
    return UNKNOWN
  }
}
