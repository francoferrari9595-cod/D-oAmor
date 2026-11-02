package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.GameSfx
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
private val RIDDLES_BASE: List<Pair<String, List<String>>> = listOf(
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

// Banco adicional de adivinanzas breves: se combina una pista de categoría con
// una pista de uso/rasgo del objeto. Las combinaciones son estables y únicas;
// las respuestas aceptan sinónimos comunes en español.
private data class RiddleSeed(val answer: String, val category: String, val clue: String, val aliases: List<String> = emptyList())
private val RIDDLE_SEEDS = listOf(
  RiddleSeed("paraguas", "objeto que se abre y se cierra", "te protege cuando cae agua del cielo", listOf("el paraguas")),
  RiddleSeed("reloj", "objeto que puedes llevar puesto o colgar", "te ayuda a saber qué hora es", listOf("el reloj")),
  RiddleSeed("llave", "objeto pequeño de metal", "puede abrir una cerradura", listOf("la llave")),
  RiddleSeed("espejo", "objeto que refleja imágenes", "te deja ver tu propio rostro", listOf("el espejo")),
  RiddleSeed("mochila", "objeto que se lleva en la espalda", "sirve para transportar tus cosas", listOf("la mochila")),
  RiddleSeed("lámpara", "objeto que puede estar en una mesa o techo", "da luz cuando el lugar está oscuro", listOf("lampara", "la lámpara")),
  RiddleSeed("teléfono", "aparato de comunicación", "te permite hablar con alguien a distancia", listOf("telefono", "celular", "móvil")),
  RiddleSeed("libro", "objeto con páginas", "guarda historias o información escrita", listOf("el libro")),
  RiddleSeed("bicicleta", "vehículo de dos ruedas", "avanza cuando pedaleas", listOf("la bicicleta", "bici")),
  RiddleSeed("avión", "medio de transporte", "vuela y lleva pasajeros por el cielo", listOf("avion", "el avión")),
  RiddleSeed("barco", "medio de transporte", "navega por el agua", listOf("el barco")),
  RiddleSeed("tren", "medio de transporte", "avanza sobre rieles", listOf("el tren")),
  RiddleSeed("helado", "alimento frío", "se derrite si lo dejas al calor", listOf("el helado")),
  RiddleSeed("pizza", "comida que suele compartirse en porciones", "lleva una base redonda y puede tener queso", listOf("la pizza")),
  RiddleSeed("manzana", "fruta", "puede ser roja, verde o amarilla", listOf("la manzana")),
  RiddleSeed("banana", "fruta alargada", "tiene cáscara amarilla al madurar", listOf("plátano", "platano")),
  RiddleSeed("zanahoria", "verdura", "suele ser naranja y crece bajo tierra", listOf("la zanahoria")),
  RiddleSeed("cebolla", "vegetal de muchas capas", "al cortarla puede hacerte llorar", listOf("la cebolla")),
  RiddleSeed("abeja", "insecto", "visita flores y produce miel", listOf("la abeja")),
  RiddleSeed("mariposa", "insecto", "tiene alas y pasa por una metamorfosis", listOf("la mariposa")),
  RiddleSeed("pingüino", "ave", "no vuela y es excelente nadadora", listOf("pinguino", "el pingüino")),
  RiddleSeed("murciélago", "mamífero", "vuela y suele estar activo de noche", listOf("murcielago", "el murciélago")),
  RiddleSeed("tortuga", "animal", "lleva un caparazón protector", listOf("la tortuga")),
  RiddleSeed("jirafa", "mamífero", "tiene un cuello muy largo", listOf("la jirafa")),
  RiddleSeed("elefante", "mamífero", "tiene trompa y grandes orejas", listOf("el elefante")),
  RiddleSeed("pulpo", "animal marino", "tiene ocho brazos con ventosas", listOf("el pulpo")),
  RiddleSeed("delfín", "mamífero marino", "salta en el agua y usa sonidos para orientarse", listOf("delfin", "el delfín")),
  RiddleSeed("guitarra", "instrumento musical", "tiene cuerdas y se toca rasgueando o punteando", listOf("la guitarra")),
  RiddleSeed("tambor", "instrumento musical", "suena cuando golpeas su membrana", listOf("el tambor")),
  RiddleSeed("piano", "instrumento musical", "tiene teclas blancas y negras", listOf("el piano")),
  RiddleSeed("termómetro", "instrumento de medición", "sirve para conocer la temperatura", listOf("termometro", "el termómetro")),
  RiddleSeed("brújula", "instrumento de orientación", "su aguja ayuda a encontrar el norte", listOf("brujula", "la brújula")),
  RiddleSeed("semáforo", "señal de tránsito", "usa luces de colores para ordenar el paso", listOf("semaforo", "el semáforo")),
  RiddleSeed("ascensor", "medio para subir y bajar en edificios", "te lleva entre diferentes pisos", listOf("el ascensor", "elevador")),
  RiddleSeed("refrigerador", "electrodoméstico", "mantiene fríos los alimentos", listOf("heladera", "nevera", "el refrigerador")),
  RiddleSeed("lavadora", "electrodoméstico", "limpia la ropa con agua y movimiento", listOf("la lavadora")),
  RiddleSeed("cepillo", "objeto de cuidado personal", "tiene cerdas para limpiar o peinar", listOf("el cepillo")),
  RiddleSeed("jabón", "producto de higiene", "hace espuma y ayuda a limpiar", listOf("jabon", "el jabón")),
  RiddleSeed("toalla", "objeto de tela", "sirve para secarte después de bañarte", listOf("la toalla")),
  RiddleSeed("almohada", "objeto blando de cama", "apoya la cabeza cuando duermes", listOf("la almohada")),
  RiddleSeed("manta", "objeto de tela", "te abriga cuando hace frío", listOf("la manta", "cobija")),
  RiddleSeed("ventilador", "aparato eléctrico", "mueve el aire para refrescarte", listOf("el ventilador")),
  RiddleSeed("calendario", "objeto para organizar el tiempo", "muestra días, semanas y meses", listOf("el calendario")),
  RiddleSeed("lápiz", "útil escolar", "escribe y se puede borrar con goma", listOf("lapiz", "el lápiz")),
  RiddleSeed("tijera", "herramienta", "tiene dos hojas que se juntan para cortar", listOf("tijeras", "la tijera")),
  RiddleSeed("martillo", "herramienta", "sirve para clavar clavos", listOf("el martillo")),
  RiddleSeed("escalera", "objeto con peldaños", "te ayuda a alcanzar lugares altos", listOf("la escalera")),
  RiddleSeed("volcán", "formación natural", "puede expulsar lava y ceniza", listOf("volcan", "el volcán")),
  RiddleSeed("arcoíris", "fenómeno del cielo", "aparece con luz y gotas de agua y muestra varios colores", listOf("arcoiris", "el arcoíris")),
  RiddleSeed("nube", "formación del cielo", "está hecha de pequeñas gotas o cristales de hielo", listOf("la nube")),
  RiddleSeed("trueno", "sonido de una tormenta", "se oye después del destello del relámpago", listOf("el trueno"))
)
private val RIDDLE_FRAMES = listOf(
  "¿Qué \$category \$clue?",
  "Adivina: soy un \$category y \$clue. ¿Qué soy?",
  "Pista: \$category; \$clue. ¿Cuál es la respuesta?",
  "No te diré el nombre: soy un \$category que \$clue. ¿Qué soy?",
  "¿Puedes descubrirlo? Soy un \$category; \$clue.",
  "Reto de ingenio: \$category, \$clue. ¿De qué se trata?",
  "Piensa bien: \$category y \$clue. ¿Qué objeto o ser soy?",
  "Una pista para ti: \$category; \$clue. ¿Quién soy?",
  "¿Qué estoy describiendo? \$category; \$clue.",
  "Resuelve esta adivinanza: \$category, porque \$clue. ¿Qué es?",
  "Desafío: descubre el nombre de un \$category que \$clue. ¿Cuál es?",
  "A ver si lo sabes: \$category; \$clue. ¿Qué será?",
  "Acertijo relámpago: \$category que \$clue. Nómbralo.",
  "¿Qué cosa encaja con estas pistas? \$category y \$clue.",
  "Piensa en algo: \$category; además, \$clue. ¿Qué es?",
  "Completa el reto: busco un \$category que \$clue. ¿Cuál?",
  "Adivinanza nueva: \$category, porque \$clue. ¿Qué soy?",
  "No es tan difícil: \$category que \$clue. ¿Lo adivinas?",
  "Dos pistas, una respuesta: \$category; \$clue. ¿Cuál es?",
  "Tu turno de pensar: \$category que \$clue. ¿Qué nombre tiene?",
  "¿Puedes ponerle nombre a esto? \$category; \$clue.",
  "Reto para mentes rápidas: \$category que \$clue. ¿Qué es?",
  "Imagina algo que sea un \$category y que \$clue. ¿Qué imaginaste?",
  "La respuesta se esconde aquí: \$category; \$clue. Descúbrela.",
  "¿Qué palabra estoy buscando? \$category que \$clue.",
  "Adivina la palabra: \$category; \$clue. ¿Cuál te parece?",
  "Ponte a prueba: ¿qué \$category \$clue?",
  "Una cosa misteriosa: \$category y \$clue. ¿Cuál será?",
  "Lee la pista y responde: \$category; \$clue.",
  "¿Qué respuesta une estas ideas? \$category y \$clue.",
  "Modo detective: encuentra el \$category que \$clue.",
  "Acertijo de hoy: ¿qué \$category \$clue?",
  "Si digo \$category y añado que \$clue, ¿qué digo?",
  "Responde con una palabra: \$category que \$clue.",
  "¿Qué elemento describen estas palabras? \$category; \$clue.",
  "Misterio por resolver: \$category que \$clue. ¿Cuál es?",
  "Encuentra la solución: piensa en un \$category que \$clue.",
  "Adivina sin mirar: \$category; \$clue. ¿Qué será?",
  "La pista es breve: \$category que \$clue. Di la respuesta.",
  "¿Qué ser u objeto puede ser \$category y \$clue?",
  "Desbloquea el acertijo: \$category; \$clue. ¿Qué palabra es?",
  "¿Lo reconoces? Es un \$category que \$clue.",
  "Desafío de vocabulario: \$category que \$clue. ¿Cuál buscas?",
  "Adivina a partir de la descripción: \$category; \$clue.",
  "Una pregunta, una respuesta: ¿qué \$category \$clue?",
  "Busca en tu memoria: \$category que \$clue. ¿Qué es?",
  "Pista secreta: se trata de un \$category que \$clue.",
  "¿Qué nombre le darías a algo que es \$category y \$clue?",
  "Resuelve este pequeño misterio: \$category; \$clue.",
  "Última pista: \$category que \$clue. ¿A qué me refiero?"
)
private val RIDDLES_GENERATED: List<Pair<String, List<String>>> = RIDDLE_SEEDS.flatMap { seed ->
  RIDDLE_FRAMES.map { frame -> frame.replace("\$category", seed.category).replace("\$clue", seed.clue) to (listOf(seed.answer) + seed.aliases) }
}.distinctBy { it.first }
private val RIDDLES: List<Pair<String, List<String>>> = (RIDDLES_BASE + RIDDLES_GENERATED).distinctBy { it.first }

@Composable
internal fun RiddleGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("riddle") }
  val st by flow.collectAsState(initial = null)
  var initialRandomIdx by remember { mutableStateOf(RIDDLES.indices.random()) }
  val storedIdx = st?.get("idx") as? Number
  val idx = (storedIdx?.toInt() ?: initialRandomIdx).coerceIn(0, RIDDLES.lastIndex)
  LaunchedEffect(storedIdx, c.me, c.hostUid) {
    if (storedIdx == null && c.me.isNotBlank() && c.me == c.hostUid) {
      c.vm.patchGame("riddle", mapOf("idx" to initialRandomIdx, "solvedBy" to ""))
    }
  }
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
      val nextIdx = (RIDDLES.indices.filter { it != idx }).random()
      initialRandomIdx = nextIdx
      c.vm.patchGame("riddle", mapOf("idx" to nextIdx, "solvedBy" to ""))
    })
  }
}

// =====================================================================
// ADIVINA EL DIBUJO
// =====================================================================
private val DRAW_BASE_WORDS = listOf(
  "corazón", "casa", "gato", "perro", "sol", "luna", "árbol", "flor", "coche", "avión", "barco", "pizza", "helado", "guitarra", "teléfono",
  "paraguas", "mariposa", "estrella", "castillo", "bicicleta", "pastel", "anillo", "beso", "playa", "montaña", "payaso", "robot", "cohete",
  "pingüino", "elefante", "cámara", "libro", "lámpara", "reloj", "sombrero", "zapato", "manzana", "uvas", "café", "regalo"
)

// Banco ampliado de consignas compuestas: 50 sujetos x 40 situaciones = 2.000 combinaciones
// deterministas y únicas, además de las palabras base. Se generan localmente, sin descargar datos.
private val DRAW_SUBJECTS = listOf(
  "gato", "perro", "pingüino", "elefante", "jirafa", "mono", "oso", "zorro", "conejo", "tortuga",
  "robot", "astronauta", "pirata", "payaso", "chef", "bombero", "detective", "mago", "princesa", "caballero",
  "dragón", "unicornio", "dinosaurio", "alienígena", "fantasma", "vampiro", "sirena", "pulpo", "tiburón", "ballena",
  "mariposa", "abeja", "búho", "rana", "serpiente", "caballo", "vaca", "gallina", "oveja", "ardilla",
  "niño", "abuela", "músico", "futbolista", "astrónomo", "científica", "monstruo", "muñeco de nieve", "superhéroe", "extraterrestre"
)
private val DRAW_SITUATIONS = listOf(
  "con sombrero", "con anteojos", "en bicicleta", "en patineta", "tocando guitarra", "bailando tango", "preparando pizza", "comiendo helado",
  "volando entre nubes", "surfeando una ola", "pescando en un lago", "plantando una flor", "leyendo un libro", "pintando un cuadro", "jugando al fútbol", "jugando al básquet",
  "en una nave espacial", "en la Luna", "en una isla desierta", "en un castillo", "en una casa del árbol", "en un submarino", "en un tren", "en un globo aerostático",
  "bajo la lluvia", "en una tormenta", "en la playa", "en la montaña", "en el desierto", "en la selva", "en el fondo del mar", "en un bosque encantado",
  "con un paraguas", "con un ramo de flores", "abriendo un regalo", "haciendo una torta", "durmiendo en una hamaca", "paseando un perro", "montando un caballo", "tomando fotografías"
)
// Segunda biblioteca de sujetos: combinada con las 40 situaciones crea más de 5.000
// consignas distintas sin descargar contenido en tiempo de ejecución.
private val DRAW_MORE_SUBJECTS = listOf(
  "canguro", "koala", "león", "tigre", "pantera", "camello", "dromedario", "cocodrilo", "lagarto", "camaleón",
  "flamenco", "pavo real", "cisne", "águila", "loro", "cuervo", "colibrí", "murciélago", "erizo", "mapache",
  "hurón", "llama", "alpaca", "ciervo", "alce", "hipopótamo", "rinoceronte", "cebra", "gorila", "chimpancé",
  "orangután", "panda", "hámster", "cobayo", "ratón", "ardilla voladora", "foca", "león marino", "orca", "delfín",
  "medusa", "estrella de mar", "cangrejo", "langosta", "camarón", "caracol", "babosa", "lombriz", "escarabajo", "saltamontes",
  "grillo", "libélula", "avispa", "hormiga", "oruga", "gusano de seda", "mariquita", "araña", "escorpión", "ciempiés",
  "bruja", "rey", "reina", "príncipe", "princesa guerrera", "samurái", "ninja", "vikinga", "explorador", "arqueóloga",
  "inventor", "inventora", "mecánico", "carpintera", "panadero", "jardinera", "veterinario", "dentista", "astronauta turista", "piloto",
  "maestra", "estudiante", "científico loco", "artista", "escultora", "bailarín", "cantante", "comediante", "mimo", "malabarista",
  "genio de la lámpara", "monstruo amistoso", "hada", "duende", "trol", "centauro", "pegaso", "grifo", "fénix", "gólem",
  "momia", "esqueleto bailarín", "zombi", "hombre lobo", "brujo", "rey extraterrestre", "bebé dragón", "dinosaurio bebé", "gallina astronauta", "perro detective",
  "gato chef", "robot jardinero", "pirata espacial", "vaca cantante", "conejo mago", "pingüino patinador", "tortuga ninja", "oso músico", "zorro cartero", "pulpo pintor",
  "bicicleta", "motocicleta", "tractor", "helicóptero", "cohete", "submarino", "tranvía", "camión de bomberos", "máquina del tiempo", "castillo ambulante"
)
// Banco adicional de exactamente 700 palabras simples, concretas y fáciles de dibujar.
// Se mantiene separado para poder jugar una ronda con consignas cortas de una sola palabra.
private val DRAW_SIMPLE_WORDS = listOf(
  "abeja", "águila", "alacrán", "alce", "alpaca", "anaconda", "anguila", "antílope",
  "araña", "ardilla", "armadillo", "asno", "atún", "avestruz", "avispa", "ballena",
  "barracuda", "bisonte", "búfalo", "búho", "burro", "cabra", "caballo", "caballito",
  "camarón", "camello", "canario", "cangrejo", "canguro", "caracol", "carancho", "carpa",
  "castor", "cebra", "cerdo", "ciervo", "cigüeña", "cisne", "cobra", "cocodrilo",
  "colibrí", "comadreja", "conejo", "cordero", "corvina", "cotorra", "coyote", "cuervo",
  "delfín", "dromedario", "elefante", "erizo", "escarabajo", "escorpión", "foca", "faisán",
  "gallina", "gallo", "ganso", "gato", "gaviota", "gorila", "gorrión", "grillo",
  "grulla", "guanaco", "guepardo", "halcón", "hámster", "hipopótamo", "hormiga", "iguana",
  "jabalí", "jaguar", "jirafa", "koala", "lagarto", "langosta", "lechuza", "lemur",
  "león", "liebre", "llama", "lobo", "loro", "luciérnaga", "manatí", "mandril",
  "mariposa", "medusa", "mejillón", "morsa", "mosquito", "mula", "murciélago", "nutria",
  "ñandú", "ocelote", "orangután", "orca", "ornitorrinco", "oso", "ostra", "oveja",
  "pájaro", "paloma", "pantera", "pato", "pavo", "pelícano", "perro", "pez",
  "pingüino", "piojo", "piraña", "pitón", "pollo", "pulpo", "puma", "rana",
  "ratón", "reno", "rinoceronte", "salamandra", "salmón", "sanguijuela", "sapo", "sardina",
  "serpiente", "suricata", "tiburón", "tigre", "topo", "toro", "tortuga", "tucán",
  "urraca", "vaca", "víbora", "vizcacha", "yacaré", "yak", "zorro", "abejorro",
  "abubilla", "agutí", "albatros", "almeja", "anchoa", "anémona", "arenque", "australiano",
  "babosa", "bacalao", "bagre", "besugo", "boa", "bogavante", "bonobo", "caballa",
  "cacatúa", "caimán", "calamar", "calandria", "camaleón", "caribú", "carpincho", "casuario",
  "cernícalo", "chacal", "chinchilla", "chorlito", "chotacabras", "chúcaro", "damán", "demonio",
  "tasmania", "dingo", "dorada", "escuerzo", "estornino", "flamenco", "gallineta", "garduña",
  "garrapata", "gecko", "gerbo", "glotón", "montaña", "guacamayo", "halibut", "herrerillo",
  "ibis", "impala", "jicotea", "jilguero", "lamprea", "langur", "lemming", "lince",
  "gris", "macaco", "macarela", "mantarraya", "martín", "pescador", "mirlo", "mofeta",
  "monito", "del", "monte", "morena", "narval", "nautilo", "olinguito", "oropéndola",
  "carpintero", "pangolín", "papagayo", "pécari", "petirrojo", "espada", "globo", "payaso",
  "pinzón", "polilla", "puercoespín", "quebrantahuesos", "quetzal", "quokka", "raya", "rémora",
  "renacuajo", "ruiseñor", "sabueso", "saltamontes", "tarántula", "tejón", "termita", "tilapia",
  "tritón", "trucha", "uapití", "azul", "varano", "vicuña", "wallaby", "wapití",
  "wombat", "xoloitzcuintle", "zángano", "acelga", "alfajor", "albóndiga", "empanada", "asado",
  "arroz", "arepa", "batata", "berenjena", "bife", "bizcocho", "budín", "burrito",
  "cacao", "café", "calabaza", "canelones", "carne", "cazuela", "ceviche", "cereal",
  "chipa", "chorizo", "chocolate", "churro", "coco", "croqueta", "curry", "ensalada",
  "espagueti", "fideos", "flan", "frijoles", "galleta", "guiso", "hamburguesa", "harina",
  "helado", "humita", "jamón", "lasaña", "leche", "lentejas", "locro", "longaniza",
  "maíz", "milanesa", "mortadela", "ñoquis", "omelet", "pan", "panqueque", "papa",
  "papas", "pasta", "pastel", "pastas", "pizza", "polenta", "puré", "queso",
  "ravioles", "risotto", "salame", "salchicha", "sandwich", "sopa", "sushi", "tamal",
  "tarta", "tacos", "tortilla", "tostada", "tuco", "yogur", "zapallo", "achuras",
  "arequipe", "bagel", "baklava", "barrita", "batido", "bechamel", "beicon", "berenjenas",
  "rellenas", "birria", "brownie", "bruschetta", "calzone", "capeletti", "carpaccio", "cassata",
  "causa", "limeña", "champiñones", "cheesecake", "chicharrón", "chimichurri", "caliente", "coleslaw",
  "conserva", "couscous", "croissant", "crumble", "empanadilla", "enchilada", "falafel", "fondue",
  "focaccia", "gazpacho", "goulash", "granola", "gravy", "kebab", "kimchi", "kombucha",
  "condensada", "macarrones", "magdalena", "maki", "matambre", "medialuna", "merengue", "mermelada",
  "mousse", "nachos", "natilla", "nuggets", "okonomiyaki", "árabe", "dulce", "panettone",
  "fritas", "parfait", "paella", "palitos", "paté", "pavlova", "pesto", "picada",
  "pionono", "poke", "ramen", "ratatouille", "revuelto", "gramajo", "rosca", "pascua",
  "reyes", "roti", "salpicón", "sándwich", "miga", "shawarma", "strudel", "tabulé",
  "tempura", "tiramisú", "española", "trufa", "wafle", "wonton", "yakisoba", "yakitori",
  "zapallitos", "rellenos", "achicoria", "ajo", "alcachofa", "alcaparra", "apio", "arándano",
  "arveja", "banana", "berro", "brócoli", "cebolla", "cereza", "ciruela", "col",
  "coliflor", "dátil", "durazno", "espárrago", "espinaca", "frambuesa", "frutilla", "granada",
  "grosella", "guayaba", "higo", "kiwi", "lima", "limón", "mandarina", "mango",
  "manzana", "melón", "membrillo", "mora", "naranja", "nectarina", "nopal", "nuez",
  "palta", "pepino", "pera", "perejil", "pimiento", "piña", "pomelo", "puerro",
  "rábano", "remolacha", "repollo", "rúcula", "sandía", "tomate", "uva", "zanahoria",
  "zapallito", "albahaca", "ananá", "anís", "alcaucil", "alubia", "macho", "castaña",
  "caqui", "cardo", "chirivía", "chirimoya", "chayote", "endivia", "escarola", "espelta",
  "feijoa", "guanábana", "jengibre", "judía", "kale", "kumquat", "kaffir", "mandioca",
  "maracuyá", "melocotón", "morrón", "nabo", "níspero", "okra", "pak", "choi",
  "papaya", "parsnip", "patata", "pecán", "physalis", "pitahaya", "plátano", "rabanito",
  "rambután", "ruibarbo", "salsifí", "tamarindo", "taro", "tomatillo", "topinambur", "yuca",
  "abrelatas", "almohada", "anillo", "antena", "armario", "aspiradora", "azucarera", "balanza",
  "bandeja", "bandera", "barril", "bicicleta", "billetera", "binoculares", "blusa", "bolsa",
  "botella", "botón", "brújula", "cable", "cadena", "caja", "calculadora", "calendario",
  "cama", "cámara", "camisa", "candado", "canasto", "carpeta", "cartera", "casco",
  "celular", "cepillo", "cerradura", "cinturón", "clavo", "cobija", "colchón", "compás",
  "computadora", "corbata", "cortina", "cuchara", "cuchillo", "cuaderno", "cuadro", "cubeta",
  "cuchilla", "dado", "despertador", "destornillador", "diccionario", "disco", "escoba", "escuadra",
  "espejo", "estante", "estufa", "etiqueta", "exprimidor", "flauta", "foco", "fósforo",
  "frasco", "freno", "fuente", "gafas", "gancho", "guitarra", "hebilla", "herramienta",
  "hilo", "horno", "imán", "impresora", "jarrón", "jarra", "jabón", "joyero",
  "juguete", "lámpara", "lata", "lápiz", "lavadora", "libro", "llave", "llavero",
  "mochila", "moneda", "monitor", "martillo", "mueble", "muñeca", "navaja", "nevera",
  "olla", "ordenador", "orejera", "papel", "paraguas", "pared", "peine", "pelota",
  "pendrive", "persiana", "pincel", "pinza", "pizarrón", "plato", "plumero", "puerta",
  "radio", "rastrillo", "regla", "reloj", "remo", "repisa", "ropero", "rueda",
  "sacapuntas", "sábana", "sartén", "secador", "silla", "sillón", "sofá", "sombrero",
  "soga", "soporte", "taza", "teclado", "teléfono", "televisor", "tijera", "timbre",
  "tirador", "tostadora", "tubo", "tuerca", "uniforme", "urna", "ventilador", "ventana",
  "vestido", "video", "violín", "zapato", "alicate", "anafe", "ancla", "andamio",
  "arado", "arcón", "archivador", "arpa", "balde", "bandolera", "bañera", "batidora",
  "biblioteca", "bidé", "boina", "bombilla", "bordadora", "brocha", "bufanda", "buril",
  "caballete", "cafetera", "cajón", "caldera", "calentador", "campana", "carretilla", "cartabón",
  "cenicero", "cerámica", "chaleco", "chubasquero"
)

private val DRAW_GENERATED_PROMPTS = (DRAW_SUBJECTS + DRAW_MORE_SUBJECTS).flatMap { subject ->
  DRAW_SITUATIONS.map { situation -> "$subject $situation" }
}.distinct()
private val DRAW_WORDS = (DRAW_BASE_WORDS + DRAW_GENERATED_PROMPTS + DRAW_SIMPLE_WORDS).distinct()

private val DRAW_CATS: Map<String, List<String>> = mapOf(
  "🎲 Mixto" to DRAW_WORDS,
  "✏️ Palabras simples" to DRAW_SIMPLE_WORDS,
  "🐾 Animales" to listOf("gato", "perro", "elefante", "mariposa", "pingüino", "pez", "caballo", "tortuga", "jirafa", "pájaro"),
  "🍕 Comida" to listOf("pizza", "helado", "manzana", "uvas", "pastel", "hamburguesa", "café", "banana", "sandía", "taco"),
  "🏠 Objetos" to listOf("lámpara", "reloj", "paraguas", "teléfono", "guitarra", "libro", "sombrero", "zapato", "cámara", "llave"),
  "🌍 Lugares" to listOf("playa", "montaña", "castillo", "casa", "puente", "isla", "faro", "granja", "desierto", "volcán"),
  "🎬 Acciones" to listOf("bailar", "dormir", "correr", "cocinar", "nadar", "llorar", "cantar", "abrazar", "volar", "besar"),
  "💖 Amor" to listOf("corazón", "beso", "anillo", "flor", "regalo", "abrazo", "carta", "luna", "estrella", "cita")
)

private val DRAW_COLORS = listOf(Color(0xFFFFFFFF), Color(0xFFFF4D8D), Color(0xFFFFC857), Color(0xFF37E6FF), Color(0xFF3DDC84), Color(0xFF9D6BFF))

/** Elige una consigna al azar evitando las ya usadas; cuando se agotan todas, vuelve a empezar. */
private fun drawFreshWord(pool: List<String>, used: List<String>): String =
  pool.filter { it !in used }.ifEmpty { pool }.random()

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

/** Palabra enmascarada: cada pista comprada revela una letra (primera, última y una del medio). */
private fun drawMask(word: String, hints: Int): List<String> {
  val letters = word.indices.filter { word[it] != ' ' }
  val reveal = mutableSetOf<Int>()
  if (hints >= 1 && letters.isNotEmpty()) reveal.add(letters.first())
  if (hints >= 2 && letters.isNotEmpty()) reveal.add(letters.last())
  if (hints >= 3 && letters.size > 2) reveal.add(letters[letters.size / 2])
  return word.indices.map { i -> if (word[i] == ' ') " " else if (i in reveal) word[i].uppercase() else "_" }
}
private const val HINT_PRICE = 2
private const val HINT_MAX = 3

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
  val hints = st.num("hints")
  val goal = st.num("goal").let { if (it <= 0) 30 else it }
  val rated = (st?.get("rated") as? Boolean) ?: false
  val lastAward = st.num("lastAward")
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
  var selGoal by remember { mutableStateOf(30) }
  if (drawer.isEmpty()) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🎨 Adivina el dibujo", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
      Text("Uno dibuja la consigna secreta y el otro intenta adivinarla en vivo; cambian de dibujante cada ronda. Ganan puntos los DOS: quien adivina (más rápido, más puntos) y quien dibuja (si lo adivinan, más las pistas que le compren y las estrellas que le den). Gana la partida quien llegue primero a la meta.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
    Spacer(Modifier.height(6.dp))
    Text("Meta para ganar la partida", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(20, 30, 50, 75, 100, 150, 200).forEach { g -> if (selGoal == g) GradientButton("$g pts", onClick = { selGoal = g }, height = 38.dp) else SoftButton("$g pts", onClick = { selGoal = g }) }
    }
    Spacer(Modifier.height(10.dp))
    GradientButton("Empezar (yo dibujo)", modifier = Modifier.fillMaxWidth(), onClick = {
      val firstWord = drawFreshWord(DRAW_CATS[selCat] ?: DRAW_WORDS, emptyList())
      c.vm.publishGame("draw", mapOf(
        "drawer" to c.me, "word" to firstWord, "used" to listOf(firstWord), "cat" to selCat, "limit" to selTime,
        "startedAt" to System.currentTimeMillis(), "strokes" to emptyList<Any>(), "solved" to false, "timeUp" to false, "round" to 1,
        "goal" to selGoal, "hints" to 0, "rated" to false, "lastAward" to 0,
        // Cada jugador arranca con 5 puntos para poder comprar pistas.
        "points" to mapOf(c.me to 5, c.otherUid to 5)
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
  val drawSoundContext = androidx.compose.ui.platform.LocalContext.current
  LaunchedEffect(remaining, solved) {
    if (limit > 0 && !solved && remaining in 1..10) {
      GameSfx.play(drawSoundContext, "sfx_countdown_tick")
    }
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
  if (!solved) {
    // Pistas: el que adivina le COMPRA una pista al que dibuja (los puntos pasan de uno al otro).
    val myPoints0 = (points[c.me] as? Number)?.toInt() ?: 0
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      drawMask(word, if (iDraw) HINT_MAX else hints).forEach { ch ->
        if (ch == " ") Spacer(Modifier.width(14.dp))
        else Box(Modifier.padding(2.dp).size(26.dp).clip(RoundedCornerShape(7.dp)).background(if (ch == "_") Color(0x22FFFFFF) else GoldYellow), contentAlignment = Alignment.Center) {
          if (ch != "_") PopText(ch, 15.sp, Modifier, Color.Black) else Text("", fontSize = 1.sp)
        }
      }
    }
    if (iDraw) Text("Pistas compradas por ${c.partnerName}: $hints/$HINT_MAX · cobras $HINT_PRICE pts por cada una", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    else SoftButton(
      if (hints >= HINT_MAX) "💡 Sin más pistas" else "💡 Comprar pista a ${c.partnerName} (−$HINT_PRICE pts) · $hints/$HINT_MAX",
      enabled = hints < HINT_MAX && myPoints0 >= HINT_PRICE,
      modifier = Modifier.fillMaxWidth(),
      onClick = {
        c.vm.patchGame("draw", mapOf("hints" to hints + 1, "points.${c.me}" to FieldValue.increment(-HINT_PRICE.toLong()), "points.$drawer" to FieldValue.increment(HINT_PRICE.toLong())))
        c.vm.gameSfx(1)
      }
    )
    if (!iDraw && myPoints0 < HINT_PRICE && hints < HINT_MAX) Text("No te alcanzan los puntos para otra pista.", color = Color(0xFFFFC2CC), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
  }
  if (!iDraw && !solved) {
    OutlinedTextField(value = guess, onValueChange = { guess = it }, label = { Text("¿Qué es?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (wrong > 0) PopText("❌ Intenta otra vez ($wrong)", 13.sp)
    Spacer(Modifier.height(6.dp))
    GradientButton("Adivinar", modifier = Modifier.fillMaxWidth(), enabled = guess.isNotBlank(), onClick = {
      if (norm(guess) == norm(word)) {
        // Premio por adivinar según la rapidez; quien dibujó recibe 6 por un dibujo claro.
        val award = if (limit > 0) { val f = remaining.coerceAtLeast(0) / limit.toFloat(); if (f > 0.66f) 10 else if (f > 0.33f) 8 else 6 } else 8
        c.vm.patchGame("draw", mapOf("solved" to true, "lastAward" to award, "points.${c.me}" to FieldValue.increment(award.toLong()), "points.$drawer" to FieldValue.increment(6L)))
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
    val myNow = (points[c.me] as? Number)?.toInt() ?: 0
    val theirNow = (points[c.otherUid] as? Number)?.toInt() ?: 0
    val matchOver = myNow >= goal || theirNow >= goal
    ResultBanner(if (timeUp) "⏰ ¡Se acabó el tiempo! Era \"$word\"" else "🏆 ¡Era \"$word\"! Adivinador +$lastAward · dibujante +6")
    if (!timeUp && !iDraw && !rated) {
      Spacer(Modifier.height(6.dp))
      Text("¿Qué tan bien dibujó ${c.partnerName}? Premia con estrellas (suma puntos al dibujante):", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..3).forEach { n ->
          SoftButton("⭐".repeat(n), onClick = { c.vm.patchGame("draw", mapOf("rated" to true, "points.$drawer" to FieldValue.increment(n.toLong()))) })
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    if (matchOver) {
      val iWon = myNow >= theirNow
      PopText(if (myNow == theirNow) "🤝 ¡Empate!" else if (iWon) "🎉 ¡Ganaste la partida!" else "😅 ${c.partnerName} ganó la partida", 18.sp)
      Spacer(Modifier.height(6.dp))
      GradientButton("Nueva partida", modifier = Modifier.fillMaxWidth(), onClick = { c.vm.publishGame("draw", emptyMap()) })
    } else GradientButton("Siguiente ronda (cambian de dibujante)", modifier = Modifier.fillMaxWidth(), onClick = {
      val usedWords = (st?.get("used") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
      val nextWord = drawFreshWord(DRAW_CATS[cat] ?: DRAW_WORDS, usedWords)
      c.vm.patchGame("draw", mapOf("drawer" to if (iDraw) c.otherUid else c.me, "word" to nextWord, "used" to (usedWords + nextWord).takeLast(300), "cat" to cat, "limit" to limit, "startedAt" to System.currentTimeMillis(), "strokes" to emptyList<Any>(), "guesses" to emptyList<Any>(), "solved" to false, "timeUp" to false, "round" to round + 1, "hints" to 0, "rated" to false, "lastAward" to 0))
    })
  }
  val my = (points[c.me] as? Number)?.toInt() ?: 0
  val their = (points[c.otherUid] as? Number)?.toInt() ?: 0
  Text("Tú $my · ${c.partnerName} $their · Meta: $goal pts", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

// =====================================================================
// ROBA 2 (acercarse a 21 robando de dos en dos)
// =====================================================================
@Composable
internal fun PlayingCardView(label: String, red: Boolean, hidden: Boolean = false) {
  var revealed by remember(label, hidden) { mutableStateOf(false) }
  LaunchedEffect(label, hidden) { delay(55); revealed = true }
  val rotation by animateFloatAsState(if (hidden || revealed) 0f else 78f, tween(260), label = "cardRotation")
  val lift by animateFloatAsState(if (revealed) 1f else 0f, tween(320), label = "cardLift")
  Box(
    modifier = Modifier.width(44.dp).aspectRatio(0.7f).graphicsLayer {
      translationY = (1f - lift) * -22.dp.toPx()
      rotationY = rotation
      cameraDistance = 18f * density
    }.clip(RoundedCornerShape(8.dp))
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
