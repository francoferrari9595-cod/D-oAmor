package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import com.google.firebase.firestore.FieldValue

private fun norm(s: String): String =
  java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), "").trim()

private fun Map<String, Any>?.num(k: String): Int = (this?.get(k) as? Number)?.toInt() ?: 0
private fun Map<String, Any>?.str(k: String): String = (this?.get(k) as? String) ?: ""
private fun Any?.intList(): List<Int> = (this as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>?.sub(k: String): Map<String, Any?> = (this?.get(k) as? Map<String, Any?>) ?: emptyMap()

// =====================================================================
// HUNDIR LA FLOTA
// =====================================================================
private fun randomFleet(): List<Int> {
  val board = MutableList(100) { 0 }
  val sizes = listOf(5, 4, 3, 3, 2)
  for ((idx, len) in sizes.withIndex()) {
    var placed = false
    var tries = 0
    while (!placed && tries < 500) {
      tries++
      val horiz = (0..1).random() == 0
      val r = (0 until (if (horiz) 10 else 10 - len + 1)).random()
      val col = (0 until (if (horiz) 10 - len + 1 else 10)).random()
      val cells = (0 until len).map { k -> if (horiz) r * 10 + col + k else (r + k) * 10 + col }
      if (cells.all { board[it] == 0 }) {
        cells.forEach { board[it] = idx + 1 }
        placed = true
      }
    }
  }
  return board
}

@Composable
private fun BsGrid(board: List<Int>, shots: List<Int>, showShips: Boolean, onTap: ((Int) -> Unit)?, small: Boolean) {
  for (r in 0 until 10) {
    Row(modifier = Modifier.fillMaxWidth()) {
      for (col in 0 until 10) {
        val i = r * 10 + col
        val ship = board.getOrElse(i) { 0 } != 0
        val shot = shots.getOrElse(i) { 0 }
        Box(
          modifier = Modifier.weight(1f).aspectRatio(1f).padding(1.dp).clip(RoundedCornerShape(3.dp))
            .background(
              when {
                shot == 2 -> Color(0xCCFF5A1F)
                showShips && ship -> Color(0xFF7C8A99)
                shot == 1 -> Color(0x6637B6FF)
                else -> Color(0x880E4A78)
              }
            )
            .clickable(enabled = onTap != null) { onTap?.invoke(i) },
          contentAlignment = Alignment.Center
        ) {
          if (shot == 2) PopText("💥", if (small) 9.sp else 14.sp)
          else if (shot == 1) PopText("💧", if (small) 8.sp else 12.sp)
        }
      }
    }
  }
}

@Composable
internal fun BattleshipGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("bs") }
  val st by flow.collectAsState(initial = null)
  val me = c.me
  val enemy = c.otherUid
  val myBoard = st.sub("board")[me].intList()
  val enemyBoard = st.sub("board")[enemy].intList()
  val myShots = st.sub("shots")[me].intList().let { if (it.size == 100) it else List(100) { 0 } }
  val enemyShots = st.sub("shots")[enemy].intList().let { if (it.size == 100) it else List(100) { 0 } }
  val ready = st.sub("ready")
  val myReady = ready[me] == true
  val enemyReady = ready[enemy] == true
  val turn = st.str("turn")
  val winner = st.str("winner")
  var draft by remember { mutableStateOf<List<Int>>(emptyList()) }
  val playing = myReady && enemyReady && turn.isNotEmpty()

  fun myHitsOnEnemy(shots: List<Int>) = shots.count { it == 2 }
  val enemyShipCells = enemyBoard.count { it != 0 }

  fun shoot(i: Int) {
    if (!playing || winner.isNotEmpty() || turn != me || myShots[i] != 0 || enemyBoard.size != 100) return
    val hit = enemyBoard[i] != 0
    val ns = myShots.toMutableList()
    ns[i] = if (hit) 2 else 1
    val won = hit && ns.count { it == 2 } >= enemyShipCells
    val fields = mutableMapOf<String, Any?>("shots.$me" to ns)
    if (won) fields["winner"] = me else fields["turn"] = if (hit) me else enemy
    c.vm.patchGame("bs", fields)
    c.vm.gameSfx(if (hit) 2 else 0)
  }

  if (winner.isNotEmpty()) {
    ResultBanner("🏆 ¡Ganó ${c.nameOf(winner)}!")
    Spacer(Modifier.height(8.dp))
    GradientButton("Nueva batalla", modifier = Modifier.fillMaxWidth(), onClick = {
      c.vm.publishGame("bs", mapOf("round" to System.currentTimeMillis()))
    })
    return
  }
  if (!myReady) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("Coloca tu flota", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
      Text("5 barcos: de 5, 4, 3, 3 y 2 casillas. Pulsa Reordenar hasta que te guste.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    val shown = if (draft.size == 100) draft else List(100) { 0 }
    BsGrid(shown, List(100) { 0 }, true, null, false)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      SoftButton("🔀 Reordenar", onClick = { draft = randomFleet() }, modifier = Modifier.weight(1f))
      GradientButton("¡Listo! ⚓", enabled = draft.size == 100, onClick = {
        c.vm.patchGame("bs", mapOf("board.$me" to draft, "shots.$me" to List(100) { 0 }, "ready.$me" to true))
        if (enemyReady && turn.isEmpty()) c.vm.patchGame("bs", mapOf("turn" to c.firstUid))
      }, modifier = Modifier.weight(1f))
    }
    return
  }
  if (!enemyReady) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("⚓ Flota lista. Esperando a ${c.partnerName}…", color = Color.White, fontWeight = FontWeight.Bold)
    }
    return
  }
  if (turn.isEmpty()) c.vm.patchGame("bs", mapOf("turn" to c.firstUid))

  TurnBanner(
    if (turn == me) "🎯 Tu turno: dispara al mar enemigo" else "Esperando el disparo de ${c.partnerName}…",
    turn == me
  )
  Spacer(Modifier.height(8.dp))
  Text("Mar enemigo (impactos: ${myHitsOnEnemy(myShots)}/$enemyShipCells)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
  BsGrid(List(100) { 0 }, myShots, false, { i -> shoot(i) }, false)
  Spacer(Modifier.height(8.dp))
  Text("Tu flota", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
  Box(modifier = Modifier.fillMaxWidth(0.62f)) { BsGrid(myBoard, enemyShots, true, null, true) }
}

// =====================================================================
// ADIVINANZAS
// =====================================================================
private val RIDDLES: List<Pair<String, List<String>>> = listOf(
  "Blanca por dentro, verde por fuera. Si quieres que te lo diga, espera." to listOf("pera"),
  "Oro parece, plata no es. ¿Qué es?" to listOf("platano", "banana"),
  "Tengo agujas y no sé coser, tengo números y no sé leer." to listOf("reloj"),
  "Vuelo sin alas, silbo sin boca, azoto sin manos y no se me ve." to listOf("viento", "el viento"),
  "Cuanto más lavo, más sucia está. ¿Qué es?" to listOf("agua", "el agua"),
  "Tiene dientes y no come, tiene cabeza y no es hombre." to listOf("ajo", "el ajo"),
  "Si me nombras desaparezco. ¿Quién soy?" to listOf("silencio", "el silencio"),
  "Tiene ciudades sin casas, ríos sin agua y montañas sin árboles." to listOf("mapa", "el mapa"),
  "Siempre va delante de ti y nunca lo puedes ver." to listOf("futuro", "el futuro"),
  "Lo tienes tú, lo tengo yo, y si lo compartes se hace más grande." to listOf("amor", "el amor", "cariño", "una sonrisa", "sonrisa"),
  "Tengo ojos y no veo, tengo agua y no bebo, tengo piel y no soy fruta." to listOf("papa", "patata"),
  "Redondo como un queso pero no es queso, brilla de noche y no es una lámpara." to listOf("luna", "la luna"),
  "Pica pero no es mosquito, es rojo y da lágrimas." to listOf("cebolla", "la cebolla", "chile", "aji"),
  "Todos pasan por mí y yo no me muevo; siempre estoy abierta o cerrada." to listOf("puerta", "la puerta"),
  "Dos hermanos que corren juntos y nunca se alcanzan." to listOf("ruedas", "las ruedas", "piernas"),
  "Camina sin pies, llora sin ojos y cuando me tocan me deshago." to listOf("nube", "la nube"),
  "Cuanto más grande es, menos se ve." to listOf("oscuridad", "la oscuridad"),
  "Se rompe sin tocarlo. ¿Qué es?" to listOf("promesa", "una promesa", "silencio", "el silencio"),
  "Tiene corazón pero no late, tiene hojas pero no es árbol." to listOf("libro", "el libro", "alcachofa"),
  "Tengo cuatro patas pero no camino; me sientas y no me quejo." to listOf("silla", "la silla", "mesa"),
  "Blanco por fuera, amarillo por dentro, y de él nace un pollito." to listOf("huevo", "el huevo"),
  "Un señor muy aseñorado, que siempre va de botas y nunca está en el prado." to listOf("gallo", "el gallo"),
  "Lo que más se parece a un pájaro sin serlo, vuela de noche y duerme boca abajo." to listOf("murcielago", "el murcielago"),
  "Me gusta que me abraces y no soy tu pareja; te cubro del frío." to listOf("manta", "la manta", "abrigo", "cobija")
)

@Composable
internal fun RiddleGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("riddle") }
  val st by flow.collectAsState(initial = null)
  val idx = st.num("idx").coerceIn(0, RIDDLES.lastIndex)
  val solvedBy = st.str("solvedBy")
  val points = st.sub("points")
  val myPts = (points[c.me] as? Number)?.toInt() ?: 0
  val theirPts = (points[c.otherUid] as? Number)?.toInt() ?: 0
  var guess by remember(idx) { mutableStateOf("") }
  var wrong by remember(idx) { mutableStateOf(0) }
  var hint by remember(idx) { mutableStateOf(false) }
  val (q, answers) = RIDDLES[idx]

  fun check() {
    if (solvedBy.isNotEmpty()) return
    val g = norm(guess)
    if (g.isEmpty()) return
    if (answers.any { norm(it) == g || (g.length >= 4 && norm(it).contains(g)) }) {
      c.vm.patchGame("riddle", mapOf("solvedBy" to c.me, "idx" to idx, "points.${c.me}" to FieldValue.increment(1)))
      c.vm.gameSfx(2)
    } else {
      wrong++
      c.vm.gameSfx(0)
    }
  }

  ScoreBoard(c, if (c.myIdx == 0) myPts else theirPts, if (c.myIdx == 0) theirPts else myPts, null, "🧩", "🧩")
  Spacer(Modifier.height(10.dp))
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
    Text("Adivinanza ${idx + 1} de ${RIDDLES.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Text(q, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 25.sp)
  }
  Spacer(Modifier.height(10.dp))
  if (solvedBy.isEmpty()) {
    OutlinedTextField(value = guess, onValueChange = { guess = it }, label = { Text("Tu respuesta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (wrong > 0) PopText("❌ Sigue intentando ($wrong)", 13.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      GradientButton("Responder", onClick = { check() }, modifier = Modifier.weight(1f), enabled = guess.isNotBlank())
      SoftButton("💡 Pista", onClick = { hint = true }, modifier = Modifier.weight(0.7f))
      SoftButton("Rendirse", onClick = { c.vm.patchGame("riddle", mapOf("solvedBy" to "reveal", "idx" to idx)) }, modifier = Modifier.weight(0.7f))
    }
    if (hint) PopText("💡 Empieza con “${answers[0].first().uppercase()}” y tiene ${answers[0].length} letras", 13.sp)
  } else {
    ResultBanner(if (solvedBy == "reveal") "💡 Era: ${answers[0]}" else "🏆 ¡${c.nameOf(solvedBy)} acertó! Era: ${answers[0]}")
    Spacer(Modifier.height(8.dp))
    GradientButton("Siguiente adivinanza", modifier = Modifier.fillMaxWidth(), onClick = {
      c.vm.patchGame("riddle", mapOf("idx" to (idx + 1) % RIDDLES.size, "solvedBy" to ""))
    })
  }
}

// =====================================================================
// ADIVINA EL DIBUJO
// =====================================================================
private val DRAW_WORDS = listOf(
  "corazón", "casa", "gato", "perro", "sol", "luna", "árbol", "flor", "coche", "avión", "barco", "pizza", "helado", "guitarra", "teléfono",
  "paraguas", "mariposa", "estrella", "castillo", "bicicleta", "pastel", "anillo", "beso", "playa", "montaña", "payaso", "robot", "cohete",
  "pingüino", "elefante", "cámara", "libro", "lámpara", "reloj", "sombrero", "zapato", "manzana", "uvas", "café", "regalo"
)
private val DRAW_CATS: Map<String, List<String>> = mapOf(
  "🎲 Mixto" to DRAW_WORDS,
  "🐾 Animales" to listOf("gato", "perro", "elefante", "mariposa", "pingüino", "pez", "caballo", "tortuga", "jirafa", "pájaro"),
  "🍕 Comida" to listOf("pizza", "helado", "manzana", "uvas", "pastel", "hamburguesa", "café", "banana", "sandía", "taco"),
  "🏠 Objetos" to listOf("lámpara", "reloj", "paraguas", "teléfono", "guitarra", "libro", "sombrero", "zapato", "cámara", "llave"),
  "🌍 Lugares" to listOf("playa", "montaña", "castillo", "casa", "puente", "isla", "faro", "granja", "desierto", "volcán"),
  "🎬 Acciones" to listOf("bailar", "dormir", "correr", "cocinar", "nadar", "llorar", "cantar", "abrazar", "volar", "besar"),
  "💖 Amor" to listOf("corazón", "beso", "anillo", "flor", "regalo", "abrazo", "carta", "luna", "estrella", "cita")
)

private val DRAW_COLORS = listOf(Color(0xFFFFFFFF), Color(0xFFFF4D8D), Color(0xFFFFC857), Color(0xFF37E6FF), Color(0xFF3DDC84), Color(0xFF9D6BFF))

private fun fmt3(v: Float): String = String.format(java.util.Locale.US, "%.3f", v)

private fun encodeStroke(pts: List<Offset>): String = pts.joinToString(";") { fmt3(it.x) + ":" + fmt3(it.y) }

private fun decodeStroke(s: String): List<Offset> = s.split(";").mapNotNull { part ->
  val xy = part.split(":")
  if (xy.size == 2) {
    val x = xy[0].toFloatOrNull()
    val y = xy[1].toFloatOrNull()
    if (x != null && y != null) Offset(x, y) else null
  } else null
}

@Suppress("UNCHECKED_CAST")
@Composable
internal fun DrawGuessGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("draw") }
  val st by flow.collectAsState(initial = null)
  val drawer = st.str("drawer")
  val word = st.str("word")
  val solved = (st?.get("solved") as? Boolean) ?: false
  val round = st.num("round")
  val strokesRaw = (st?.get("strokes") as? List<Map<String, Any?>>) ?: emptyList()
  val guessesRaw = (st?.get("guesses") as? List<Map<String, Any?>>) ?: emptyList()
  val points = st.sub("points")
  val iDraw = drawer == c.me
  val local = remember(round) { mutableStateListOf<Pair<Int, List<Offset>>>() }
  var color by remember { mutableStateOf(0) }
  val cur = remember(round) { mutableStateListOf<Offset>() }
  var guess by remember(round) { mutableStateOf("") }
  var wrong by remember(round) { mutableStateOf(0) }

  fun publishStrokes() {
    c.vm.patchGame("draw", mapOf("strokes" to local.map { mapOf("c" to it.first, "p" to encodeStroke(it.second)) }))
  }

  var selCat by remember { mutableStateOf("🎲 Mixto") }
  var selTime by remember { mutableStateOf(0) }
  if (drawer.isEmpty()) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🎨 Adivina el dibujo", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
      Text("Uno dibuja la palabra secreta y el otro intenta adivinarla en vivo. Cambian de dibujante cada ronda.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    Text("Categoría", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      DRAW_CATS.keys.forEach { k -> if (selCat == k) GradientButton(k, onClick = { selCat = k }, height = 38.dp) else SoftButton(k, onClick = { selCat = k }) }
    }
    Spacer(Modifier.height(6.dp))
    Text("Tiempo por dibujo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(0 to "Sin límite", 45 to "45 s", 90 to "90 s", 150 to "2:30").forEach { (sec, label) ->
        if (selTime == sec) GradientButton(label, onClick = { selTime = sec }, height = 38.dp) else SoftButton(label, onClick = { selTime = sec })
      }
    }
    Spacer(Modifier.height(10.dp))
    GradientButton("Empezar (yo dibujo)", modifier = Modifier.fillMaxWidth(), onClick = {
      c.vm.publishGame("draw", mapOf(
        "drawer" to c.me, "word" to DRAW_CATS[selCat]!!.random(), "cat" to selCat, "limit" to selTime,
        "startedAt" to System.currentTimeMillis(), "strokes" to emptyList<Any>(), "solved" to false, "timeUp" to false, "round" to 1
      ))
    })
    return
  }
  val cat = st.str("cat").ifEmpty { "🎲 Mixto" }
  val limit = st.num("limit")
  val startedAt = (st?.get("startedAt") as? Number)?.toLong() ?: 0L
  val timeUp = (st?.get("timeUp") as? Boolean) ?: false
  var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
  val remaining = if (limit > 0) (limit - ((nowMs - startedAt) / 1000)).toInt() else -1
  LaunchedEffect(round, solved) {
    while (!solved) {
      nowMs = System.currentTimeMillis()
      delay(500)
    }
  }
  LaunchedEffect(remaining, solved) {
    if (limit > 0 && remaining <= 0 && !solved && iDraw) {
      c.vm.patchGame("draw", mapOf("solved" to true, "timeUp" to true))
    }
  }

  val shown: List<Pair<Int, List<Offset>>> =
    if (iDraw) local.toList() else strokesRaw.map { m -> ((m["c"] as? Number)?.toInt() ?: 0) to decodeStroke((m["p"] as? String) ?: "") }

  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true, corner = 16.dp, contentPadding = PaddingValues(10.dp)) {
    Text(
      (if (iDraw) "✏️ Tú dibujas: \"$word\"" else "🔎 ${c.partnerName} dibuja… ¡adivina! (${word.length} letras)") + (if (limit > 0 && !solved) "  ⏱ ${remaining.coerceAtLeast(0)} s" else "") + "  · $cat",
      color = Color.White, fontWeight = FontWeight.ExtraBold
    )
  }
  Spacer(Modifier.height(8.dp))
  Box(
    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))
      .background(Color(0xFF120A22)).border(BorderStroke(2.dp, GoldYellow), RoundedCornerShape(18.dp))
  ) {
    Canvas(
      modifier = Modifier.fillMaxSize().pointerInput(iDraw, round, solved) {
        if (iDraw && !solved) {
          detectDragGestures(
            onDragStart = { o -> cur.clear(); cur.add(Offset(o.x / size.width, o.y / size.height)) },
            onDrag = { change, _ -> cur.add(Offset(change.position.x / size.width, change.position.y / size.height)) },
            onDragEnd = {
              if (cur.size > 1) { local.add(color to cur.toList()); publishStrokes() }
              cur.clear()
            }
          )
        }
      }
    ) {
      val all = shown + (if (cur.isNotEmpty()) listOf(color to cur.toList()) else emptyList())
      for ((ci, pts) in all) {
        if (pts.size < 2) continue
        val path = Path()
        path.moveTo(pts[0].x * size.width, pts[0].y * size.height)
        for (k in 1 until pts.size) path.lineTo(pts[k].x * size.width, pts[k].y * size.height)
        drawPath(path, DRAW_COLORS.getOrElse(ci) { Color.White }, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
      }
    }
  }
  if (guessesRaw.isNotEmpty() && !solved) {
    Spacer(Modifier.height(6.dp))
    guessesRaw.takeLast(5).forEach { g ->
      Text("${c.nameOf((g["u"] as? String) ?: "")} intentó: ${(g["t"] as? String) ?: ""} ❌", color = Color(0xFFFFC2CC), fontSize = 13.sp)
    }
  }
  Spacer(Modifier.height(8.dp))
  if (iDraw && !solved) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      DRAW_COLORS.forEachIndexed { i, col ->
        Box(
          modifier = Modifier.size(30.dp).clip(CircleShape).background(col)
            .border(BorderStroke(if (color == i) 3.dp else 1.dp, if (color == i) GoldYellow else Color(0x66FFFFFF)), CircleShape)
            .clickable { color = i }
        )
      }
      Spacer(Modifier.weight(1f))
      SoftButton("↩︎", onClick = { if (local.isNotEmpty()) { local.removeAt(local.lastIndex); publishStrokes() } })
      SoftButton("🗑️", onClick = { local.clear(); publishStrokes() })
    }
  }
  if (!iDraw && !solved) {
    OutlinedTextField(value = guess, onValueChange = { guess = it }, label = { Text("¿Qué es?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (wrong > 0) PopText("❌ Intenta otra vez ($wrong)", 13.sp)
    Spacer(Modifier.height(6.dp))
    GradientButton("Adivinar", modifier = Modifier.fillMaxWidth(), enabled = guess.isNotBlank(), onClick = {
      if (norm(guess) == norm(word)) {
        c.vm.patchGame("draw", mapOf("solved" to true, "points.${c.me}" to FieldValue.increment(1), "points.$drawer" to FieldValue.increment(1)))
        c.vm.gameSfx(2)
      } else {
        wrong++
        c.vm.gameSfx(0)
        c.vm.patchGame("draw", mapOf("guesses" to (guessesRaw + mapOf<String, Any?>("u" to c.me, "t" to guess.trim())).takeLast(8)))
        guess = ""
      }
    })
  }
  if (solved) {
    ResultBanner(if (timeUp) "⏰ ¡Se acabó el tiempo! Era \"$word\"" else "🏆 ¡Era \"$word\"!")
    Spacer(Modifier.height(8.dp))
    GradientButton("Siguiente ronda (cambian de dibujante)", modifier = Modifier.fillMaxWidth(), onClick = {
      c.vm.patchGame("draw", mapOf("drawer" to if (iDraw) c.otherUid else c.me, "word" to (DRAW_CATS[cat] ?: DRAW_WORDS).random(), "cat" to cat, "limit" to limit, "startedAt" to System.currentTimeMillis(), "strokes" to emptyList<Any>(), "guesses" to emptyList<Any>(), "solved" to false, "timeUp" to false, "round" to round + 1))
    })
  }
  val my = (points[c.me] as? Number)?.toInt() ?: 0
  val their = (points[c.otherUid] as? Number)?.toInt() ?: 0
  Text("Puntos juntos: tú $my · ${c.partnerName} $their", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

// =====================================================================
// ROBA 2 (acercarse a 21 robando de dos en dos)
// =====================================================================
@Composable
internal fun PlayingCardView(label: String, red: Boolean, hidden: Boolean = false) {
  Box(
    modifier = Modifier.width(44.dp).aspectRatio(0.7f).clip(RoundedCornerShape(8.dp))
      .background(if (hidden) Color(0xFF3A1F5E) else Color.White)
      .border(BorderStroke(1.dp, Color(0x55000000)), RoundedCornerShape(8.dp)),
    contentAlignment = Alignment.Center
  ) {
    if (hidden) Text("🂠", fontSize = 22.sp)
    else PopText(label, 17.sp, Modifier, if (red) Color(0xFFD32F2F) else Color(0xFF111111))
  }
}

@Composable
internal fun RobaDosGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("r2") }
  val st by flow.collectAsState(initial = null)
  val started = (st?.get("deck") as? List<*>)?.isNotEmpty() == true
  val deck = st?.get("deck").intList()
  val di = st.num("di")
  val hands = listOf(st?.get("h0").intList(), st?.get("h1").intList())
  val stand = listOf((st?.get("s0") as? Boolean) ?: false, (st?.get("s1") as? Boolean) ?: false)
  val turn = st.num("turn")
  val result = st.str("result")
  val total = hands.map { it.sum() }
  val myIdx = c.myIdx
  val myTurn = started && result.isEmpty() && turn == myIdx

  fun finish(res: String, h0: List<Int>, h1: List<Int>, s0: Boolean, s1: Boolean, d: Int, t: Int) {
    c.vm.publishGame("r2", mapOf("deck" to deck, "di" to d, "h0" to h0, "h1" to h1, "s0" to s0, "s1" to s1, "turn" to t, "result" to res))
    if (res.isNotEmpty()) c.vm.gameSfx(2)
  }

  fun evaluate(h0: List<Int>, h1: List<Int>, s0: Boolean, s1: Boolean, d: Int, nextTurn: Int) {
    val t0 = h0.sum()
    val t1 = h1.sum()
    val res = when {
      t0 > 21 -> "W1"
      t1 > 21 -> "W0"
      s0 && s1 -> if (t0 > t1) "W0" else if (t1 > t0) "W1" else "T"
      else -> ""
    }
    val nt = if (nextTurn == 0 && s0 && !s1) 1 else if (nextTurn == 1 && s1 && !s0) 0 else nextTurn
    finish(res, h0, h1, s0, s1, d, nt)
  }

  fun draw2() {
    if (!myTurn || di + 2 > deck.size) return
    val mine = hands[myIdx] + deck[di] + deck[di + 1]
    val h0 = if (myIdx == 0) mine else hands[0]
    val h1 = if (myIdx == 1) mine else hands[1]
    evaluate(h0, h1, stand[0], stand[1], di + 2, 1 - myIdx)
  }

  fun doStand() {
    if (!myTurn) return
    val s0 = if (myIdx == 0) true else stand[0]
    val s1 = if (myIdx == 1) true else stand[1]
    evaluate(hands[0], hands[1], s0, s1, di, 1 - myIdx)
  }

  if (!started) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🃏 Roba 2", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
      Text("En tu turno robas 2 cartas (valen 1 a 10) o te plantas. Quien se acerque más a 21 sin pasarse gana. Si te pasas, pierdes.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(10.dp))
    GradientButton("Repartir", modifier = Modifier.fillMaxWidth(), onClick = {
      val d = (List(4) { (1..10).toList() }.flatten()).shuffled()
      c.vm.publishGame("r2", mapOf("deck" to d, "di" to 0, "h0" to emptyList<Int>(), "h1" to emptyList<Int>(), "s0" to false, "s1" to false, "turn" to 0, "result" to ""))
    })
    return
  }
  for (i in 0..1) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), highlight = turn == i && result.isEmpty(), corner = 18.dp) {
      Text("${c.nameOfIdx(i)} · total ${total[i]}${if (stand[i]) " · se plantó" else ""}${if (total[i] > 21) " · ¡se pasó!" else ""}", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
        hands[i].forEach { PlayingCardView("$it", false) }
        if (hands[i].isEmpty()) Text("— sin cartas —", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      }
    }
  }
  if (result.isNotEmpty()) {
    ResultBanner(if (result == "T") "🤝 ¡Empate!" else "🏆 ¡Ganó ${c.nameOfIdx(if (result == "W0") 0 else 1)}!")
    Spacer(Modifier.height(8.dp))
    GradientButton("Otra mano", modifier = Modifier.fillMaxWidth(), onClick = {
      val d = (List(4) { (1..10).toList() }.flatten()).shuffled()
      c.vm.publishGame("r2", mapOf("deck" to d, "di" to 0, "h0" to emptyList<Int>(), "h1" to emptyList<Int>(), "s0" to false, "s1" to false, "turn" to 0, "result" to ""))
    })
  } else {
    TurnBanner(if (myTurn) "Tu turno: roba 2 cartas o plántate" else "Turno de ${c.nameOfIdx(turn)}…", myTurn)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      GradientButton("🃏🃏 Robar 2", onClick = { draw2() }, enabled = myTurn, modifier = Modifier.weight(1f))
      SoftButton("✋ Plantarme", onClick = { doStand() }, enabled = myTurn, modifier = Modifier.weight(1f))
    }
  }
}
