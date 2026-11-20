package com.example.trivia

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.BackgroundMusic
import com.example.data.firestore.GameOp
import com.example.ui.components.GradientButton
import com.example.ui.components.OptionChip
import com.example.ui.components.SoftButton
import com.example.ui.screens.GameCtx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** Configuración elegida por el anfitrión antes de empezar. */
private data class TCfg(
  val cats: Set<String> = emptySet(), val diff: Int = 0, val rounds: Int = 10, val qSec: Int = 30, val ansSec: Int = 10,
  val ok: Int = 100, val pen: Int = 0, val speed: Boolean = true, val reb: Int = 2
)

private fun tHost(u: String): String = runCatching { java.net.URI(u).host.removePrefix("www.") }.getOrDefault(u)

/**
 * Módulo TRIVIAS: concurso con pulsadores. Todas las decisiones que importan pasan por transacciones del servidor
 * (quién pulsó primero) y por el anfitrión (cronómetro, veredictos y puntos); los demás dispositivos solo muestran el estado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TriviaGame(c: GameCtx) {
  val ctx = LocalContext.current.applicationContext
  val scope = rememberCoroutineScope()
  val doc by remember { c.vm.gameState("trivia") }.collectAsState(initial = null)
  val offset by remember { c.vm.serverOffsetFlow() }.collectAsState(initial = 0L)
  val offsetNow by rememberUpdatedState(offset)
  val s = remember(doc) { TS(doc ?: emptyMap()) }
  val sNow by rememberUpdatedState(s)
  var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis(); delay(100) } }
  val srv = tick + offset
  val isHost = c.me == c.hostUid.ifBlank { c.firstUid }
  val mySeat = s.seats.indexOf(c.me)
  var screen by remember { mutableStateOf("play") }
  var toast by remember { mutableStateOf("") }
  var cfg by remember { mutableStateOf(TCfg()) }
  var answerText by remember { mutableStateOf("") }
  var overlay by remember { mutableStateOf<Triple<Boolean, String, String>?>(null) }
  var confirmEnd by remember { mutableStateOf(false) }
  val hostQs = remember(s.gid) { if (isHost && s.gid.isNotEmpty()) TriviaStore.loadActive(ctx, s.gid) else emptyList() }
  fun hostQ(st: TS): TQ? = hostQs.getOrNull(st.round)
  fun nameOfUid(u: String) = if (u.isEmpty()) "" else c.nameOf(u)
  fun nowSrv() = System.currentTimeMillis() + offsetNow

  /** Escribe solo si el estado sigue siendo el que el anfitrión vio (comparar y cambiar en el servidor). */
  fun cas(expect: Long, upd: Map<String, Any?>) {
    c.vm.triviaTransact({ it["ev"].tLong() == expect }, { upd + ("ev" to expect + 1L) }, { _, _ -> })
  }

  // ───────── construcción de transiciones (solo las usa el anfitrión) ─────────
  fun revealUpd(st: TS, vOk: String, how: String, web: Boolean, msg: String, src: List<String>, sc: List<Long>, gain: Int, who: String, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
    mapOf(
      "phase" to "reveal", "revealAt" to GameOp.ServerTime, "revealAns" to (hostQ(st)?.a ?: ""), "vOk" to vOk, "vHow" to how, "vWeb" to web,
      "vMsg" to msg, "vSrc" to src, "sc" to sc, "lastGain" to gain.toLong(), "lastWho" to who, "rv" to "", "rvBy" to ""
    ) + extra

  fun correctUpd(st: TS, how: String, web: Boolean, msg: String, src: List<String>): Map<String, Any?> {
    val idx = st.seats.indexOf(st.buzzer)
    val used = st.baseUsed + (st.buzzAt - st.resumeAt).coerceAtLeast(0L)
    val total = st.qSec * 1000L
    val frac = ((total - used).toFloat() / total).coerceIn(0f, 1f)
    val bonus = if (st.speed) ((st.ptsOk * 0.5f * frac) / 5f).toInt() * 5 else 0
    val gain = st.ptsOk + bonus
    val sc = st.sc.toMutableList()
    if (idx in sc.indices) sc[idx] = sc[idx] + gain
    return revealUpd(st, "1", how, web, msg, src, sc, gain, st.buzzer)
  }

  fun wrongUpd(st: TS, how: String, web: Boolean, msg: String, src: List<String>): Map<String, Any?> {
    val idx = st.seats.indexOf(st.buzzer)
    val sc = st.sc.toMutableList()
    if (idx in sc.indices) sc[idx] = sc[idx] - st.pen
    val solo = st.seats.size <= 1
    val locked = if (solo) st.locked else (st.locked + st.buzzer).distinct()
    val attempts = st.attempts + 1
    val usedAtBuzz = st.baseUsed + (st.buzzAt - st.resumeAt).coerceAtLeast(0L)
    val remaining = st.qSec * 1000L - usedAtBuzz
    val end = attempts >= 1 + st.reb || (!solo && locked.size >= st.seats.size) || remaining <= 500L
    return if (end) revealUpd(st, "0", how, web, msg, src, sc, 0, st.buzzer, mapOf("attempts" to attempts.toLong(), "locked" to locked))
    else mapOf(
      "phase" to "question", "baseUsed" to usedAtBuzz, "resumeAt" to GameOp.ServerTime, "buzzer" to "", "buzzAt" to 0L, "answer" to "",
      "locked" to locked, "attempts" to attempts.toLong(), "sc" to sc, "wrongUid" to st.buzzer, "wrongAns" to st.answer, "wrongEv" to st.ev + 1L,
      "vOk" to "", "vMsg" to msg, "vHow" to how, "vWeb" to web, "vSrc" to src, "rv" to "", "rvBy" to ""
    )
  }

  fun reviewUpd(how: String, web: Boolean, msg: String, src: List<String>): Map<String, Any?> =
    mapOf("phase" to "review", "vOk" to "?", "vHow" to how, "vWeb" to web, "vMsg" to msg, "vSrc" to src, "rv" to "", "rvBy" to "")

  fun nextRoundUpd(st: TS): Map<String, Any?> {
    val next = st.round + 1
    return if (next >= st.total) mapOf("phase" to "final", "finishedAt" to GameOp.ServerTime)
    else mapOf(
      "phase" to "ready", "r" to next.toLong(), "rid" to "${st.gid}-$next", "readyAt" to GameOp.ServerTime, "baseUsed" to 0L, "resumeAt" to 0L, "buzzAt" to 0L,
      "buzzer" to "", "locked" to emptyList<String>(), "attempts" to 0L, "answer" to "", "vOk" to "", "vMsg" to "", "vSrc" to emptyList<String>(), "revealAns" to "",
      "wrongUid" to "", "wrongAns" to "", "wrongEv" to 0L, "lastGain" to 0L, "lastWho" to "", "rv" to "", "rvBy" to ""
    )
  }

  fun startGame() {
    val off = TriviaStore.disabled(ctx)
    val pool = TriviaStore.allQs(ctx).filter { it.id !in off && (cfg.cats.isEmpty() || it.cat in cfg.cats) && (cfg.diff == 0 || it.diff == cfg.diff) }
    if (pool.isEmpty()) { toast = "No hay preguntas activas con esos filtros."; return }
    var picked = pool.shuffled().take(cfg.rounds)
    if (cfg.diff == 0) picked = picked.sortedBy { it.diff }   // la dificultad sube poco a poco
    val gid = System.currentTimeMillis().toString(36)
    TriviaStore.saveActive(ctx, gid, picked)
    val seats = c.players
    c.vm.publishGame("trivia", mapOf(
      "phase" to "ready", "ev" to 1L, "gid" to gid, "rid" to "$gid-0", "seats" to seats, "sc" to List(seats.size) { 0L }, "r" to 0L, "total" to picked.size.toLong(),
      "qs" to picked.map { mapOf("c" to it.cat, "d" to it.diff.toLong(), "q" to it.q) },
      "cQ" to cfg.qSec.toLong(), "cA" to cfg.ansSec.toLong(), "cOk" to cfg.ok.toLong(), "cPen" to cfg.pen.toLong(), "cSp" to cfg.speed, "cReb" to cfg.reb.toLong(),
      "readyAt" to nowSrv(), "baseUsed" to 0L, "resumeAt" to 0L, "buzzAt" to 0L, "buzzer" to "", "attempts" to 0L
    ))
  }

  // ───────── automatización del anfitrión ─────────
  LaunchedEffect(s.ev, s.phase, isHost) {
    if (!isHost || !s.exists) return@LaunchedEffect
    val ev = s.ev
    when (s.phase) {
      "ready" -> {
        while (nowSrv() < s.readyAt + 3300L) delay(100)
        cas(ev, mapOf("phase" to "question", "resumeAt" to GameOp.ServerTime, "baseUsed" to 0L, "buzzer" to "", "buzzAt" to 0L))
      }
      "question" -> {
        while (sNow.remainingMs(nowSrv()) > 0L) delay(120)
        cas(ev, revealUpd(s, "t", "local", false, "Nadie respondió a tiempo.", emptyList(), s.sc, 0, ""))
      }
      "answering" -> {
        while (nowSrv() < s.buzzAt + s.ansSec * 1000L) delay(150)
        cas(ev, wrongUpd(s, "local", false, "Se acabó el tiempo para responder.", emptyList()))
      }
      "judging" -> {
        val q = hostQ(s)
        if (q == null) { cas(ev, reviewUpd("manual", false, "Este dispositivo no tiene las respuestas de la partida. Decide manualmente.", emptyList())); return@LaunchedEffect }
        val local = tJudgeLocal(q, s.answer)
        if (local.verdict == TVerdict.CORRECT) { cas(ev, correctUpd(s, "local", false, local.note, emptyList())); return@LaunchedEffect }
        val rv = tVerifyRemote(q, s.answer, false).getOrNull()
        if (rv != null) {
          val how = if (rv.usedWeb) "web" else "ia"
          when (rv.verdict) {
            TVerdict.CORRECT -> cas(ev, correctUpd(s, how, rv.usedWeb, rv.explanation, rv.sources))
            TVerdict.INCORRECT -> cas(ev, wrongUpd(s, how, rv.usedWeb, rv.explanation, rv.sources))
            TVerdict.AMBIGUOUS -> cas(ev, reviewUpd(how, rv.usedWeb, rv.explanation.ifBlank { "La IA no pudo decidir con seguridad." }, rv.sources))
          }
        } else {
          val msg = "No se pudo consultar Internet; evaluación local. ${local.note}"
          if (local.verdict == TVerdict.INCORRECT) cas(ev, wrongUpd(s, "local", false, msg, emptyList())) else cas(ev, reviewUpd("local", false, msg, emptyList()))
        }
      }
      "review" -> {
        // el voto lo emite un jugador distinto de quien respondió; el anfitrión (que tiene las respuestas) lo aplica
        if (s.rv == "1") cas(ev, correctUpd(s, "manual", false, "Aprobada por ${nameOfUid(s.rvBy).ifBlank { "el rival" }}.", emptyList()))
        else if (s.rv == "0") cas(ev, wrongUpd(s, "manual", false, "Rechazada por ${nameOfUid(s.rvBy).ifBlank { "el rival" }}.", emptyList()))
      }
      "reveal" -> {
        val start = if (s.revealAt > 0L) s.revealAt else nowSrv()
        while (nowSrv() < start + 6500L) delay(150)
        cas(ev, nextRoundUpd(s))
      }
    }
  }

  // ───────── acciones del jugador ─────────
  fun buzz() {
    val st = sNow
    if (mySeat < 0 || st.phase != "question" || st.remainingMs(nowSrv()) <= 0L) return
    val rid = st.rid
    val solo = st.seats.size <= 1
    c.vm.triviaTransact(
      guard = { cur -> cur["phase"] == "question" && cur["rid"] == rid && (cur["buzzer"] as? String).isNullOrEmpty() && (solo || (cur["locked"] as? List<*>)?.contains(c.me) != true) },
      build = { cur -> mapOf("phase" to "answering", "buzzer" to c.me, "buzzAt" to GameOp.ServerTime, "ev" to cur["ev"].tLong() + 1L) },
      onResult = { won, err ->
        toast = when { won -> ""; err == null -> "¡Otro jugador pulsó primero!"; else -> "Sin conexión con el servidor. Inténtalo otra vez." }
      }
    )
  }

  /** Revisión manual: nunca la decide quien respondió. El host aplica directamente; los demás dejan su voto para que el host lo aplique. */
  fun vote(ok: Boolean) {
    val st = sNow
    if (st.phase != "review" || (st.seats.size > 1 && st.buzzer == c.me)) return
    if (isHost) {
      val who = nameOfUid(c.me)
      cas(st.ev, if (ok) correctUpd(st, "manual", false, "Aprobada por $who.", emptyList()) else wrongUpd(st, "manual", false, "Rechazada por $who.", emptyList()))
    } else {
      val ev = st.ev
      c.vm.triviaTransact({ it["ev"].tLong() == ev && it["phase"] == "review" }, { mapOf("rv" to (if (ok) "1" else "0"), "rvBy" to c.me, "ev" to ev + 1L) }, { _, _ -> })
    }
  }

  fun submit(text: String) {
    val t = text.trim().take(120)
    if (t.isEmpty()) return
    c.vm.triviaTransact(
      guard = { cur -> cur["phase"] == "answering" && cur["buzzer"] == c.me },
      build = { cur -> mapOf("phase" to "judging", "answer" to t, "ev" to cur["ev"].tLong() + 1L) },
      onResult = { ok, err -> if (!ok && err != null) toast = "No se pudo enviar la respuesta: $err" }
    )
    answerText = ""
  }

  val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
    BackgroundMusic.releaseVoice("trivia_voice")
    if (r.resultCode == Activity.RESULT_OK) {
      val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
      if (said.isNotBlank()) { answerText = said; if (sNow.phase == "answering" && sNow.buzzer == c.me) submit(said) }
    }
  }
  fun listen() {
    val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES"); putExtra(RecognizerIntent.EXTRA_PROMPT, "Di tu respuesta")
    }
    BackgroundMusic.holdForVoice("trivia_voice")
    try { voiceLauncher.launch(it) } catch (e: Exception) { BackgroundMusic.releaseVoice("trivia_voice"); toast = "Este teléfono no tiene reconocimiento de voz. Escribe tu respuesta." }
  }

  // ───────── efectos de sonido y animaciones de resultado ─────────
  LaunchedEffect(s.buzzer) { if (s.phase == "answering" && s.buzzer.isNotEmpty()) TriviaSfx.buzz() }
  val secLeft = ceil(s.remainingMs(srv) / 1000f).toInt()
  LaunchedEffect(secLeft, s.phase) { if (s.phase == "question" && secLeft in 1..5) TriviaSfx.tick() }
  val countNum = if (s.phase == "ready") ceil(((s.readyAt + 3300L) - srv) / 1000f).toInt().coerceIn(0, 3) else -1
  LaunchedEffect(countNum) { if (countNum == 0) TriviaSfx.go() else if (countNum in 1..3) TriviaSfx.tick() }
  LaunchedEffect(s.ev, s.phase) {
    overlay = when {
      s.phase == "reveal" && s.vOk == "1" -> Triple(true, "¡CORRECTO!", "${nameOfUid(s.lastWho)} +${s.lastGain} pts")
      s.phase == "reveal" && s.vOk == "t" -> Triple(false, "¡SE ACABÓ EL TIEMPO!", "La respuesta era: ${s.revealAns}")
      s.phase == "reveal" -> Triple(false, "INCORRECTO", "La respuesta era: ${s.revealAns}")
      s.phase == "question" && s.wrongEv == s.ev && s.wrongEv > 0L -> Triple(false, "¡FALLÓ!", "${nameOfUid(s.wrongUid)}: «${s.wrongAns}». ¡Pulsadores habilitados!")
      else -> null
    }
    if (overlay != null) { if (overlay!!.first) TriviaSfx.ok() else TriviaSfx.bad(); delay(2300); overlay = null }
  }
  LaunchedEffect(toast) { if (toast.isNotEmpty()) { delay(3000); toast = "" } }
  LaunchedEffect(s.phase, s.gid) {
    if (s.phase == "final" && TriviaStore.lastRecordedGid(ctx) != s.gid) {
      val order = s.seats.indices.sortedByDescending { s.sc.getOrElse(it) { 0L } }
      val top = order.firstOrNull()
      TriviaStore.addHistory(ctx, THist(System.currentTimeMillis(), s.total, if (top != null) nameOfUid(s.seats[top]) else "-", order.map { nameOfUid(s.seats[it]) to s.sc.getOrElse(it) { 0L }.toInt() }))
      TriviaStore.recordGame(ctx, top != null && s.seats[top] == c.me && s.sc.getOrElse(top) { 0L } > 0L)
      TriviaStore.markRecorded(ctx, s.gid)
    }
  }
  val seenStat = remember { mutableSetOf<String>() }
  LaunchedEffect(s.ev) {
    val cat = (s.curQ?.get("c") as? String) ?: return@LaunchedEffect
    if (s.phase == "reveal" && s.buzzer == c.me && (s.vOk == "1" || s.vOk == "0") && seenStat.add("${s.gid}-${s.round}-r")) TriviaStore.recordAnswer(ctx, cat, s.vOk == "1")
    if (s.phase == "question" && s.wrongEv == s.ev && s.wrongUid == c.me && seenStat.add("${s.gid}-${s.round}-${s.ev}")) TriviaStore.recordAnswer(ctx, cat, false)
  }

  androidx.compose.runtime.DisposableEffect(Unit) { onDispose { TriviaSfx.release() } }

  // ───────── interfaz ─────────
  // El contenedor de juegos mide con altura infinita y luego encoge: hay que dar una altura propia (igual que Monopoly, Pool o Carreras).
  val avail = (com.example.ui.screens.gameAvail() - 60.dp).coerceAtLeast(380.dp)
  if (screen == "admin") { Box(Modifier.fillMaxWidth().height(avail)) { TriviaAdmin(ctx) { screen = "play" } }; return }
  if (screen == "stats") { Box(Modifier.fillMaxWidth().height(avail)) { TriviaStats(ctx) { screen = "play" } }; return }

  Box(Modifier.fillMaxWidth().height(avail).clip(RoundedCornerShape(18.dp))) {
    TriviaBackdrop(Modifier.fillMaxSize())
    Column(Modifier.fillMaxSize().padding(8.dp)) {
      // barra superior
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("🎙️ TRIVIA", color = T_GOLD, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 2.sp)
        if (s.exists && s.phase != "final") Text("   Pregunta ${s.round + 1}/${s.total}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { screen = "stats" }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) { Text("📊", fontSize = 18.sp) }
        if (isHost && s.exists) TextButton(onClick = { confirmEnd = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) { Text("⏹", fontSize = 18.sp) }
      }
      Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
          !s.exists -> TriviaSetup(c, cfg, isHost, { cfg = it }, { startGame() }, { screen = "admin" }, ctx)
          s.phase == "ready" -> {
            val q = s.curQ
            TriviaCountdown(countNum, "PREGUNTA ${s.round + 1} DE ${s.total}", "${tCat((q?.get("c") as? String) ?: "cul").emoji} ${tCat((q?.get("c") as? String) ?: "cul").name} · ${"⭐".repeat(((q?.get("d") as? Number)?.toInt() ?: 1).coerceIn(1, 3))}", Modifier.fillMaxSize())
          }
          s.phase == "final" -> {
            val order = s.seats.indices.sortedByDescending { s.sc.getOrElse(it) { 0L } }
            Column(Modifier.fillMaxSize()) {
              TriviaWinnerBoard(order.map { Triple(it, nameOfUid(s.seats[it]), s.sc.getOrElse(it) { 0L }.toInt()) }, mySeat, Modifier.weight(1f))
              if (isHost) GradientButton("Nueva partida", modifier = Modifier.fillMaxWidth(), onClick = { c.vm.publishGame("trivia", emptyMap()) })
              else Text("Esperando que el anfitrión inicie otra partida…", color = Color(0xFFC9BEDD), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
          }
          else -> TriviaPlay(s, srv, mySeat, c, ::nameOfUid, answerText, { answerText = it }, ::buzz, ::submit, ::listen, isHost,
            onAccept = { vote(true) },
            onReject = { vote(false) },
            onSecond = {
              val q = hostQ(sNow); val st = sNow
              if (q == null) toast = "Faltan las respuestas en este dispositivo."
              else scope.launch {
                toast = "Segunda evaluación en curso…"
                val rv = tVerifyRemote(q, st.answer, true).getOrNull()
                toast = ""
                if (rv == null) { toast = "No se pudo consultar. Decide manualmente."; return@launch }
                val how = if (rv.usedWeb) "web" else "ia"
                when (rv.verdict) {
                  TVerdict.CORRECT -> cas(st.ev, correctUpd(st, how, rv.usedWeb, rv.explanation, rv.sources))
                  TVerdict.INCORRECT -> cas(st.ev, wrongUpd(st, how, rv.usedWeb, rv.explanation, rv.sources))
                  TVerdict.AMBIGUOUS -> cas(st.ev, mapOf("vHow" to how, "vWeb" to rv.usedWeb, "vMsg" to "Segunda evaluación: ${rv.explanation.ifBlank { "sigue sin ser concluyente." }}", "vSrc" to rv.sources))
                }
              }
            },
            onSkip = { cas(s.ev, nextRoundUpd(sNow)) }
          )
        }
      }
      if (toast.isNotEmpty()) Text(toast, color = Color(0xFFFFE082), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
    }
    overlay?.let { TriviaVerdictOverlay(it.first, it.second, it.third) }
  }
  if (confirmEnd) AlertDialog(
    onDismissRequest = { confirmEnd = false }, title = { Text("¿Terminar la partida?") }, text = { Text("Se cerrará la partida actual para todos.") },
    confirmButton = { TextButton(onClick = { confirmEnd = false; c.vm.publishGame("trivia", emptyMap()) }) { Text("Terminar") } },
    dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancelar") } }
  )
}

@Composable
private fun TriviaPlay(
  s: TS, srv: Long, mySeat: Int, c: GameCtx, nameOf: (String) -> String, answerText: String, onAnswerText: (String) -> Unit,
  onBuzz: () -> Unit, onSubmit: (String) -> Unit, onListen: () -> Unit, isHost: Boolean,
  onAccept: () -> Unit, onReject: () -> Unit, onSecond: () -> Unit, onSkip: () -> Unit
) {
  val q = s.curQ
  val cat = tCat((q?.get("c") as? String) ?: "cul")
  val diff = ((q?.get("d") as? Number)?.toInt() ?: 1).coerceIn(1, 3)
  val rem = s.remainingMs(srv)
  val secs = ceil(rem / 1000f).toInt()
  val answering = s.phase == "answering"
  val iAmBuzzer = s.buzzer == c.me && answering
  val locked = c.me in s.locked && s.seats.size > 1
  // la revisión manual la hace un jugador que NO respondió (en práctica de un solo jugador sí puede decidir él)
  val canReview = s.phase == "review" && mySeat >= 0 && (s.seats.size <= 1 || s.buzzer != c.me)
  val ansLeft = if (answering) ((s.buzzAt + s.ansSec * 1000L - srv).coerceAtLeast(0L)) else 0L

  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    // cabecera compacta: relojes pequeños + categoría, para dejar el mayor espacio posible a la pregunta
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TriviaTimerRing(rem.toFloat() / (s.qSec * 1000f), secs, Modifier.size(50.dp), "", numSize = 19.sp)
      if (answering) TriviaTimerRing(ansLeft.toFloat() / (s.ansSec * 1000f), ceil(ansLeft / 1000f).toInt(), Modifier.size(44.dp), "", warnColor = true, numSize = 16.sp)
      Text("${cat.emoji} ${cat.name.uppercase()}  ${"⭐".repeat(diff)}", color = T_NEON_CYAN, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f), maxLines = 1)
    }
    Spacer(Modifier.height(4.dp))
    TriviaScreenPanel(Modifier.weight(1f).fillMaxWidth(), pad = 10.dp) {
      val qText = (q?.get("q") as? String).orEmpty()
      val qSize = when { qText.length > 170 -> 13f; qText.length > 120 -> 14.5f; qText.length > 70 -> 16f; else -> 17.5f }
      Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(qText, color = Color.White, fontSize = qSize.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = (qSize * 1.28f).sp)
      }
      Spacer(Modifier.height(4.dp))
      when (s.phase) {
        "question" -> Text("¡Pulsa tu botón para responder!", color = T_GOLD, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        "answering" -> Text("🔔 ${if (s.buzzer == c.me) "¡Tienes la palabra!" else nameOf(s.buzzer) + " tiene la palabra"}", color = T_GOLD, fontSize = 14.sp, fontWeight = FontWeight.Black)
        "judging" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("«${s.answer}»", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
          Text("🤖 Verificando la respuesta…", color = T_NEON_CYAN, fontSize = 12.sp)
        }
        "review" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("«${s.answer}»", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
          Text("⚖️ Respuesta ambigua — la decide tu rival", color = T_GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          if (s.vMsg.isNotBlank()) Text(s.vMsg, color = Color(0xFFC9BEDD), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        "reveal" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(if (s.vOk == "1") "✅ ${s.revealAns}" else "Respuesta: ${s.revealAns}", color = if (s.vOk == "1") T_OK else T_GOLD, fontSize = 17.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
          val how = when (s.vHow) {
            "local" -> "Comparación local, sin consulta a Internet"
            "ia" -> "Evaluada por IA, sin consulta web"
            "web" -> "Verificada con consulta web (${s.vSrc.size} fuente${if (s.vSrc.size == 1) "" else "s"})"
            "manual" -> "Decidida por un rival"
            else -> ""
          }
          if (s.vOk == "1" || s.vOk == "0") Text("«${s.answer}» · $how", color = Color(0xFFC9BEDD), fontSize = 11.sp, textAlign = TextAlign.Center)
          if (s.vMsg.isNotBlank() && s.vOk != "t") Text(s.vMsg, color = Color(0xFFC9BEDD), fontSize = 11.sp, textAlign = TextAlign.Center)
          s.vSrc.take(2).forEach { Text("🔗 ${tHost(it)}", color = T_NEON_CYAN, fontSize = 10.sp) }
        }
      }
    }
    Spacer(Modifier.height(4.dp))
    // zona de control: pulsador, respuesta o revisión
    Box(Modifier.fillMaxWidth().height(if (iAmBuzzer) 108.dp else 84.dp), contentAlignment = Alignment.Center) {
      when {
        s.phase == "question" && mySeat >= 0 -> TriviaBuzzer(!locked && rem > 0L, if (locked) "BLOQUEADO" else "¡PULSA!", Modifier.size(84.dp), onBuzz)
        iAmBuzzer -> Column(Modifier.fillMaxWidth()) {
          OutlinedTextField(
            value = answerText, onValueChange = onAnswerText, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Escribe tu respuesta", color = Color(0xFF9E9E9E)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { onSubmit(answerText) }),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = T_NEON_CYAN, unfocusedBorderColor = Color(0x88FFFFFF), cursorColor = T_NEON_CYAN)
          )
          Spacer(Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("🎤 Voz", onClick = onListen, modifier = Modifier.weight(1f))
            GradientButton("Responder", modifier = Modifier.weight(1.4f), enabled = answerText.isNotBlank(), onClick = { onSubmit(answerText) })
          }
        }
        s.phase == "review" && (canReview || isHost) -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          if (canReview) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton("✅ Aceptar", modifier = Modifier.weight(1f), onClick = onAccept)
            SoftButton("❌ Rechazar", onClick = onReject, modifier = Modifier.weight(1f))
          } else Text("Tu respuesta no la puedes aprobar tú: la decide tu rival", color = Color(0xFFC9BEDD), fontSize = 12.sp, textAlign = TextAlign.Center)
          if (isHost) SoftButton("🔎 Segunda evaluación con IA y web", onClick = onSecond, modifier = Modifier.fillMaxWidth())
        }
        s.phase == "reveal" && isHost -> SoftButton("Siguiente ▶", onClick = onSkip, modifier = Modifier.fillMaxWidth())
        else -> Text(
          when {
            s.phase == "answering" -> "Pulsadores bloqueados"
            s.phase == "judging" -> "El juez está verificando…"
            s.phase == "review" -> "Un rival está revisando la respuesta…"
            mySeat < 0 -> "Estás mirando la partida"
            else -> ""
          }, color = Color(0xFFC9BEDD), fontSize = 13.sp
        )
      }
    }
    Spacer(Modifier.height(4.dp))
    // marcadores y luces de pulsador
    val leader = s.sc.withIndex().maxByOrNull { it.value }?.index ?: -1
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
      s.seats.forEachIndexed { i, uid ->
        val st = when { s.buzzer == uid && (answering || s.phase == "judging" || s.phase == "review") -> PodState.BUZZED; uid in s.locked && s.seats.size > 1 -> PodState.LOCKED; else -> PodState.IDLE }
        TriviaPod(i, nameOf(uid), s.sc.getOrElse(i) { 0L }.toInt(), st, uid == c.me, i == leader, Modifier.weight(1f))
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriviaSetup(c: GameCtx, cfg: TCfg, isHost: Boolean, onCfg: (TCfg) -> Unit, onStart: () -> Unit, onAdmin: () -> Unit, ctx: android.content.Context) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    TriviaScreenPanel(Modifier.fillMaxWidth()) {
      Text("🎙️ CONCURSO DE TRIVIAS", color = T_GOLD, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
      Text("Pulsadores en vivo: el servidor decide quién llegó primero.", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
      Spacer(Modifier.height(6.dp))
      Text("En la mesa: " + c.players.joinToString(", ") { c.nameOf(it) }, color = T_NEON_CYAN, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    if (!isHost) {
      Text("⏳ El anfitrión está preparando la partida…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
      return@Column
    }
    @Composable fun lbl(t: String) = Text(t, color = T_GOLD, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    lbl("CATEGORÍAS (${if (cfg.cats.isEmpty()) "todas" else cfg.cats.size.toString()})")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      OptionChip("🎲 Todas", cfg.cats.isEmpty(), { onCfg(cfg.copy(cats = emptySet())) })
      T_CATS.forEach { k -> OptionChip("${k.emoji} ${k.name}", k.id in cfg.cats, { onCfg(cfg.copy(cats = if (k.id in cfg.cats) cfg.cats - k.id else cfg.cats + k.id)) }) }
    }
    lbl("DIFICULTAD")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(0 to "Mixta", 1 to "Fácil", 2 to "Media", 3 to "Difícil").forEach { (d, n) -> OptionChip(n, cfg.diff == d, { onCfg(cfg.copy(diff = d)) }) }
    }
    lbl("PREGUNTAS")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5, 10, 15, 20).forEach { n -> OptionChip("$n", cfg.rounds == n, { onCfg(cfg.copy(rounds = n)) }) } }
    lbl("TIEMPO POR PREGUNTA (segundos)")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(15, 20, 30, 45, 60).forEach { n -> OptionChip("$n", cfg.qSec == n, { onCfg(cfg.copy(qSec = n)) }) } }
    lbl("TIEMPO PARA RESPONDER (segundos)")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5, 10, 15, 20).forEach { n -> OptionChip("$n", cfg.ansSec == n, { onCfg(cfg.copy(ansSec = n)) }) } }
    lbl("PUNTOS POR ACIERTO")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(50, 100, 200).forEach { n -> OptionChip("+$n", cfg.ok == n, { onCfg(cfg.copy(ok = n)) }) } }
    lbl("PENALIZACIÓN POR ERROR")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0, 25, 50).forEach { n -> OptionChip(if (n == 0) "Sin penal." else "-$n", cfg.pen == n, { onCfg(cfg.copy(pen = n)) }) } }
    lbl("BONIFICACIÓN POR VELOCIDAD")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      OptionChip("Activada", cfg.speed, { onCfg(cfg.copy(speed = true)) }); OptionChip("Desactivada", !cfg.speed, { onCfg(cfg.copy(speed = false)) })
    }
    lbl("REBOTES (oportunidades extra tras un error)")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0, 1, 2, 3, 5).forEach { n -> OptionChip("$n", cfg.reb == n, { onCfg(cfg.copy(reb = n)) }) } }
    Spacer(Modifier.height(4.dp))
    GradientButton("🎬 ¡Comenzar concurso!", modifier = Modifier.fillMaxWidth(), onClick = onStart)
    SoftButton("📝 Gestionar preguntas", onClick = onAdmin, modifier = Modifier.fillMaxWidth())
  }
}
