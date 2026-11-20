package com.example.trivia

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class THist(val t: Long, val rounds: Int, val winner: String, val scores: List<Pair<String, Int>>)
internal data class TStats(val played: Int, val won: Int, val ok: Int, val bad: Int, val byCat: Map<String, Pair<Int, Int>>)

/** Almacenamiento local del módulo (SharedPreferences propio: no toca la base de datos ni las preferencias del resto de la app). */
internal object TriviaStore {
  private fun sp(c: Context) = c.applicationContext.getSharedPreferences("trivia_store", Context.MODE_PRIVATE)

  private fun qToJson(q: TQ) = JSONObject().put("id", q.id).put("cat", q.cat).put("d", q.diff).put("q", q.q).put("a", q.a).put("alt", JSONArray(q.alt)).put("custom", q.custom)
  private fun qFromJson(o: JSONObject, custom: Boolean) = TQ(
    o.getInt("id"), o.getString("cat"), o.getInt("d"), o.getString("q"), o.getString("a"),
    o.optJSONArray("alt")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(), custom || o.optBoolean("custom")
  )

  // ── preguntas propias ──
  fun customQs(c: Context): List<TQ> = runCatching {
    val a = JSONArray(sp(c).getString("custom", "[]")); (0 until a.length()).map { qFromJson(a.getJSONObject(it), true) }
  }.getOrDefault(emptyList())

  fun addCustom(c: Context, cat: String, diff: Int, q: String, a: String, alt: List<String>) {
    val list = customQs(c)
    val id = (list.maxOfOrNull { it.id } ?: 99_999) + 1
    val arr = JSONArray(); (list + TQ(id, cat, diff, q.trim(), a.trim(), alt, true)).forEach { arr.put(qToJson(it)) }
    sp(c).edit().putString("custom", arr.toString()).apply()
  }

  fun deleteCustom(c: Context, id: Int) {
    val arr = JSONArray(); customQs(c).filter { it.id != id }.forEach { arr.put(qToJson(it)) }
    sp(c).edit().putString("custom", arr.toString()).apply()
  }

  fun allQs(c: Context): List<TQ> = T_BANK + customQs(c)

  // ── preguntas desactivadas ──
  fun disabled(c: Context): Set<Int> = sp(c).getStringSet("off", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
  fun setDisabled(c: Context, id: Int, off: Boolean) {
    val cur = (sp(c).getStringSet("off", emptySet()) ?: emptySet()).toMutableSet()
    if (off) cur.add(id.toString()) else cur.remove(id.toString())
    sp(c).edit().putStringSet("off", cur).apply()
  }

  // ── respuestas de la partida en curso (solo las conoce el anfitrión; así no viajan en la base de datos compartida) ──
  fun saveActive(c: Context, gid: String, qs: List<TQ>) {
    val arr = JSONArray(); qs.forEach { arr.put(qToJson(it)) }
    sp(c).edit().putString("active_gid", gid).putString("active_qs", arr.toString()).apply()
  }
  fun loadActive(c: Context, gid: String): List<TQ> {
    if (sp(c).getString("active_gid", "") != gid) return emptyList()
    return runCatching { val a = JSONArray(sp(c).getString("active_qs", "[]")); (0 until a.length()).map { qFromJson(a.getJSONObject(it), false) } }.getOrDefault(emptyList())
  }

  // ── historial y estadísticas ──
  fun history(c: Context): List<THist> = runCatching {
    val a = JSONArray(sp(c).getString("hist", "[]"))
    (0 until a.length()).map { i ->
      val o = a.getJSONObject(i); val sc = o.getJSONArray("s")
      THist(o.getLong("t"), o.getInt("r"), o.getString("w"), (0 until sc.length()).map { j -> sc.getJSONObject(j).let { it.getString("n") to it.getInt("p") } })
    }
  }.getOrDefault(emptyList())

  fun addHistory(c: Context, h: THist) {
    val list = (listOf(h) + history(c)).take(40)
    val arr = JSONArray()
    list.forEach { e -> arr.put(JSONObject().put("t", e.t).put("r", e.rounds).put("w", e.winner).put("s", JSONArray().also { sa -> e.scores.forEach { (n, p) -> sa.put(JSONObject().put("n", n).put("p", p)) } })) }
    sp(c).edit().putString("hist", arr.toString()).apply()
  }

  fun lastRecordedGid(c: Context) = sp(c).getString("rec_gid", "") ?: ""
  fun markRecorded(c: Context, gid: String) { sp(c).edit().putString("rec_gid", gid).apply() }

  fun stats(c: Context): TStats {
    val o = runCatching { JSONObject(sp(c).getString("stats", "{}")) }.getOrDefault(JSONObject())
    val bc = o.optJSONObject("c") ?: JSONObject()
    val map = HashMap<String, Pair<Int, Int>>()
    bc.keys().forEach { k -> bc.getJSONObject(k).let { map[k] = it.optInt("o") to it.optInt("b") } }
    return TStats(o.optInt("p"), o.optInt("w"), o.optInt("o"), o.optInt("b"), map)
  }

  private fun saveStats(c: Context, s: TStats) {
    val bc = JSONObject(); s.byCat.forEach { (k, v) -> bc.put(k, JSONObject().put("o", v.first).put("b", v.second)) }
    sp(c).edit().putString("stats", JSONObject().put("p", s.played).put("w", s.won).put("o", s.ok).put("b", s.bad).put("c", bc).toString()).apply()
  }

  fun recordAnswer(c: Context, cat: String, correct: Boolean) {
    val s = stats(c); val cur = s.byCat[cat] ?: (0 to 0)
    saveStats(c, s.copy(ok = s.ok + if (correct) 1 else 0, bad = s.bad + if (correct) 0 else 1, byCat = s.byCat + (cat to (if (correct) cur.first + 1 to cur.second else cur.first to cur.second + 1))))
  }

  fun recordGame(c: Context, won: Boolean) {
    val s = stats(c); saveStats(c, s.copy(played = s.played + 1, won = s.won + if (won) 1 else 0))
  }
}
