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
import com.example.data.firestore.GameOps

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
      c.vm.patchGame("riddle", mapOf("solvedBy" to c.me, "idx" to idx, "points.${c.me}" to GameOps.increment(1)))
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
/** Palabras fáciles de dibujar y de reconocer a simple vista (una sola palabra, cosas de todos los días). */
private val DRAW_WORDS = listOf(
  // animales
  "gato", "perro", "vaca", "caballo", "cerdo", "oveja", "gallina", "pato", "pez", "pájaro", "tortuga", "conejo", "ratón", "mono",
  "elefante", "jirafa", "león", "tigre", "oso", "cebra", "serpiente", "rana", "mariposa", "abeja", "araña", "caracol", "hormiga",
  "delfín", "ballena", "tiburón", "pulpo", "cangrejo", "pingüino", "búho", "loro", "cocodrilo", "canguro", "panda", "lobo", "zorro",
  // comida
  "pizza", "helado", "manzana", "banana", "uvas", "sandía", "naranja", "fresa", "zanahoria", "pan", "queso", "huevo", "hamburguesa",
  "torta", "galleta", "café", "leche", "tomate", "papa", "maíz", "pollo", "chocolate", "dona", "sopa", "taco", "piña", "limón", "cereza", "pera",
  // objetos de casa
  "casa", "silla", "mesa", "cama", "puerta", "ventana", "lámpara", "reloj", "llave", "libro", "lápiz", "tijeras", "paraguas", "teléfono",
  "cámara", "guitarra", "piano", "tambor", "pelota", "globo", "regalo", "vela", "espejo", "escoba", "taza", "vaso", "botella", "cuchara",
  "tenedor", "plato", "martillo", "escalera", "maleta", "mochila", "anillo", "corona", "gafas", "sombrero", "zapato", "camisa", "calcetín",
  "bufanda", "bolso", "cepillo", "peine", "televisor", "computadora", "bombilla", "cohete", "bandera", "tren", "campana", "balanza",
  // vehículos
  "auto", "bicicleta", "moto", "avión", "barco", "autobús", "helicóptero", "camión", "submarino", "patineta",
  // naturaleza
  "sol", "luna", "estrella", "nube", "lluvia", "arcoíris", "montaña", "volcán", "playa", "isla", "árbol", "flor", "rosa", "cactus", "hoja",
  "fuego", "nieve", "rayo", "río", "cascada", "mar", "desierto", "hongo", "palmera",
  // lugares y cuerpo
  "castillo", "puente", "faro", "iglesia", "granja", "escuela", "ojo", "nariz", "boca", "mano", "pie", "oreja", "corazón", "diente",
  // personajes
  "payaso", "fantasma", "pirata", "dragón", "robot", "sirena", "bruja", "rey", "reina", "bebé", "monstruo", "extraterrestre",
  // acciones sencillas
  "bailar", "dormir", "correr", "nadar", "cantar", "volar", "cocinar", "llorar", "reír", "saltar", "comer"
).distinct()


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

  // Modos de partida listos para elegir (un toque): meta de puntos + tiempo por dibujo.
  val modes = listOf(
    Triple("⚡ Rápida", "20 pts · 60 s por dibujo", 20 to 60),
    Triple("🎯 Normal", "50 pts · 90 s por dibujo", 50 to 90),
    Triple("🏆 Larga", "100 pts · 2:30 por dibujo", 100 to 150),
    Triple("🧘 Sin prisa", "30 pts · sin límite de tiempo", 30 to 0)
  )
  var selMode by remember { mutableStateOf(1) }
  if (drawer.isEmpty()) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🎨 Adivina el dibujo", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
      Text("Uno dibuja una palabra fácil y el otro la adivina en vivo. Cambian de dibujante en cada ronda y ganan puntos los dos.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(10.dp))
    Text("¿Cómo quieren jugar?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    modes.chunked(2).forEachIndexed { row, pair ->
      Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pair.forEachIndexed { col, (title, desc, _) ->
          val idx = row * 2 + col
          val sel = selMode == idx
          Column(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
              .background(if (sel) Color(0x33FFD60A) else Color(0x22FFFFFF))
              .border(if (sel) 2.dp else 1.dp, if (sel) GoldYellow else Color(0x33FFFFFF), RoundedCornerShape(14.dp))
              .clickable { selMode = idx }.padding(horizontal = 12.dp, vertical = 10.dp)
          ) {
            Text(title, color = if (sel) GoldYellow else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
          }
        }
      }
    }
    Text("Cada jugador empieza con 5 puntos para comprar pistas (letras reveladas).", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Spacer(Modifier.height(10.dp))
    GradientButton("▶ Empezar (yo dibujo primero)", modifier = Modifier.fillMaxWidth(), onClick = {
      val (goalPts, timeSec) = modes[selMode].third
      val firstWord = drawFreshWord(DRAW_WORDS, emptyList())
      c.vm.publishGame("draw", mapOf(
        "drawer" to c.me, "word" to firstWord, "used" to listOf(firstWord), "limit" to timeSec,
        "startedAt" to System.currentTimeMillis(), "strokes" to emptyList<Any>(), "solved" to false, "timeUp" to false, "round" to 1,
        "goal" to goalPts, "hints" to 0, "rated" to false, "lastAward" to 0,
        // Cada jugador arranca con 5 puntos para poder comprar pistas.
        "points" to mapOf(c.me to 5, c.otherUid to 5)
      ))
    })
    return
  }
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
      (if (iDraw) "✏️ Tú dibujas: \"$word\"" else "🔎 ${c.partnerName} dibuja… ¡adivina! (${word.length} letras)") + (if (limit > 0 && !solved) "  ⏱ ${remaining.coerceAtLeast(0)} s" else ""),
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
        c.vm.patchGame("draw", mapOf("hints" to hints + 1, "points.${c.me}" to GameOps.increment(-HINT_PRICE.toLong()), "points.$drawer" to GameOps.increment(HINT_PRICE.toLong())))
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
        c.vm.patchGame("draw", mapOf("solved" to true, "lastAward" to award, "points.${c.me}" to GameOps.increment(award.toLong()), "points.$drawer" to GameOps.increment(6L)))
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
          SoftButton("⭐".repeat(n), onClick = { c.vm.patchGame("draw", mapOf("rated" to true, "points.$drawer" to GameOps.increment(n.toLong()))) })
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
      val nextWord = drawFreshWord(DRAW_WORDS, usedWords)
      c.vm.patchGame("draw", mapOf("drawer" to if (iDraw) c.otherUid else c.me, "word" to nextWord, "used" to (usedWords + nextWord).takeLast(300), "limit" to limit, "startedAt" to System.currentTimeMillis(), "strokes" to emptyList<Any>(), "guesses" to emptyList<Any>(), "solved" to false, "timeUp" to false, "round" to round + 1, "hints" to 0, "rated" to false, "lastAward" to 0))
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
