package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton

private val HANG_CATS: Map<String, List<String>> = mapOf(
  "🐾 Animales" to listOf("ELEFANTE", "JIRAFA", "MARIPOSA", "DELFIN", "PINGUINO", "COCODRILO", "TORTUGA", "CABALLO", "MURCIELAGO", "ZORRO"),
  "🍕 Comida" to listOf("HAMBURGUESA", "CHOCOLATE", "EMPANADA", "ESPAGUETI", "HELADO", "TORTILLA", "SANDWICH", "PANQUEQUE", "ENSALADA", "AGUACATE"),
  "🌎 Países" to listOf("ARGENTINA", "COLOMBIA", "ESPAÑA", "MEXICO", "JAPON", "ITALIA", "PORTUGAL", "URUGUAY", "ECUADOR", "CANADA"),
  "🎬 Películas" to listOf("TITANIC", "AVATAR", "FROZEN", "SHREK", "COCO", "MATRIX", "GLADIADOR", "ENCANTO", "INCEPCION", "ALADDIN"),
  "💼 Profesiones" to listOf("DOCTOR", "ARQUITECTO", "PROFESORA", "BOMBERO", "COCINERO", "ABOGADA", "PILOTO", "DENTISTA", "PINTOR", "MUSICO"),
  "⚽ Deportes" to listOf("FUTBOL", "BALONCESTO", "NATACION", "TENIS", "CICLISMO", "VOLEIBOL", "BEISBOL", "ATLETISMO", "BOXEO", "SURF"),
  "💖 Amor" to listOf("ABRAZO", "BESO", "CARIÑO", "ROMANCE", "CORAZON", "PROMESA", "DISTANCIA", "RECUERDO", "COMPLICE", "ETERNIDAD")
)
private val LETTER_ROWS = listOf("ABCDEFGHI", "JKLMNÑOPQ", "RSTUVWXYZ")
private const val MAX_WRONG = 6

private fun normWord(w: String): String {
  val up = w.uppercase().replace('Ñ', '#')
  val nfd = java.text.Normalizer.normalize(up, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
  return nfd.replace('#', 'Ñ')
}

private const val HANG_TARGET = 3   // el primero en ganar 3 rondas gana la partida

/**
 * Ahorcado en DUELO, como el clásico de internet: uno piensa la palabra secreta (con una pista) y el otro
 * intenta adivinarla letra por letra antes de 6 errores. Cada ronda se intercambian los papeles.
 */
@Composable
internal fun HangmanGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("hang") }
  val st by flow.collectAsState(initial = null)
  val word = (st?.get("word") as? String) ?: ""
  val hint = (st?.get("hint") as? String) ?: ""
  val started = word.isNotEmpty()
  val guessed = (st?.get("guessed") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val wrong = (st?.get("wrong") as? Number)?.toInt() ?: 0
  val setter = (st?.get("setter") as? String) ?: ""
  val nextSetter = (st?.get("next") as? String) ?: ""
  val myScore = (st?.get("sc_${c.me}") as? Number)?.toInt() ?: 0
  val theirScore = (st?.get("sc_${c.otherUid}") as? Number)?.toInt() ?: 0
  val matchOver = myScore >= HANG_TARGET || theirScore >= HANG_TARGET
  val nw = normWord(word)
  val letters = nw.filter { it != ' ' }.toSet()
  val won = started && letters.all { guessed.contains(it.toString()) }
  val lost = started && wrong >= MAX_WRONG
  val over = won || lost
  val iSet = started && setter == c.me
  val iGuess = started && setter.isNotEmpty() && !iSet
  val guesserUid = if (iSet) c.otherUid else c.me
  var selCat by remember { mutableStateOf(HANG_CATS.keys.first()) }
  var customWord by remember { mutableStateOf("") }
  var customHint by remember { mutableStateOf("") }
  var riskWord by remember { mutableStateOf("") }

  fun scores(mine: Int = myScore, theirs: Int = theirScore): Map<String, Any?> = mapOf("sc_${c.me}" to mine, "sc_${c.otherUid}" to theirs)

  fun startRound(newWord: String, newHint: String) {
    c.vm.publishGame("hang", mapOf(
      "word" to newWord, "hint" to newHint, "guessed" to emptyList<String>(), "wrong" to 0, "setter" to c.me, "next" to ""
    ) + scores())
    c.vm.gameSfx(1)
  }

  // El que adivina juega una letra; si acierta o falla se actualiza la ronda y, al terminar, se suma el punto.
  fun applyGuess(ng: List<String>, nWrong: Int, good: Boolean) {
    val nowWon = letters.all { ng.contains(it.toString()) }
    val nowLost = nWrong >= MAX_WRONG
    // Yo soy quien adivina: gano si completo la palabra; el que la puso gana si me cuelgan.
    val mine = if (nowWon) myScore + 1 else myScore
    val theirs = if (nowLost && !nowWon) theirScore + 1 else theirScore
    c.vm.publishGame("hang", mapOf(
      "word" to word, "hint" to hint, "guessed" to ng, "wrong" to nWrong, "setter" to setter, "next" to ""
    ) + scores(mine, theirs))
    c.vm.gameSfx(if (good) 2 else 0)
  }
  fun guess(letter: String) {
    if (!iGuess || over || guessed.contains(letter)) return
    val ok = letters.contains(letter[0])
    applyGuess(guessed + letter, if (ok) wrong else wrong + 1, ok)
  }
  fun risk() {
    if (!iGuess || over) return
    val attempt = normWord(riskWord.trim())
    if (attempt.length < 2) return
    riskWord = ""
    if (attempt == nw) applyGuess(guessed + letters.map { it.toString() }, wrong, true)
    else applyGuess(guessed, wrong + 1, false)
  }
  fun nextRound() {
    c.vm.publishGame("hang", mapOf("word" to "", "setter" to "", "next" to guesserUid, "guessed" to emptyList<String>(), "wrong" to 0) + scores())
  }

  // Marcador siempre visible
  Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf(c.me to myScore, c.otherUid to theirScore).forEach { (uid, pts) ->
      Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0FFFFFFF)))).border(BorderStroke(1.dp, if (started && setter == uid) Color(0xFFFFD166) else Color(0x33FFFFFF)), RoundedCornerShape(14.dp)).padding(10.dp)) {
        Column {
          Text(if (uid == c.me) "Tú" else c.partnerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          Text("$pts / $HANG_TARGET", color = Color(0xFFFFD166), fontWeight = FontWeight.Black, fontSize = 22.sp)
          if (started) Text(if (setter == uid) "✍️ puso la palabra" else "🔎 adivina", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
      }
    }
  }

  // ---------------- Antes de la ronda ----------------
  if (!started) {
    if (matchOver) {
      val iWon = myScore > theirScore
      ResultBanner(if (iWon) "🏆 ¡Ganaste el duelo $myScore a $theirScore!" else "😅 ${c.partnerName} ganó el duelo $theirScore a $myScore")
      Spacer(Modifier.height(8.dp))
      GradientButton("Nueva partida (marcador en 0)", modifier = Modifier.fillMaxWidth(), onClick = { c.vm.publishGame("hang", emptyMap()) })
      return
    }
    val myTurnToSet = nextSetter.isEmpty() || nextSetter == c.me
    if (!myTurnToSet) {
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Text("✍️ ${c.partnerName} está eligiendo su palabra secreta…", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text("Cuando la ponga, empiezas a adivinar. ¡Tienes 6 errores!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      }
      return
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🪢 Ahorcado: duelo", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
      Text("Piensa una palabra secreta y dale una pista a ${c.partnerName}. Tiene que adivinarla antes de 6 errores. Después cambian los papeles. Gana quien llegue primero a $HANG_TARGET rondas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value = customWord, onValueChange = { customWord = it.filter { ch -> ch.isLetter() || ch == ' ' }.take(18) }, label = { Text("Tu palabra secreta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(value = customHint, onValueChange = { customHint = it.take(40) }, label = { Text("Pista (opcional): ej. “un animal”") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    GradientButton("Poner palabra y empezar", modifier = Modifier.fillMaxWidth(), enabled = customWord.trim().length >= 3,
      onClick = { startRound(customWord.trim().uppercase(), customHint.trim().ifEmpty { "${customWord.trim().length} letras" }) })
    Spacer(Modifier.height(10.dp))
    Text("¿Sin ideas? Elige una categoría y se sortea una palabra para ${c.partnerName}:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      HANG_CATS.keys.forEach { k -> if (selCat == k) GradientButton(k, onClick = { selCat = k }, height = 40.dp) else SoftButton(k, onClick = { selCat = k }) }
    }
    Spacer(Modifier.height(6.dp))
    SoftButton("🎲 Palabra al azar", modifier = Modifier.fillMaxWidth(), onClick = { startRound(HANG_CATS[selCat]!!.random(), selCat) })
    return
  }

  // ---------------- Ronda en juego ----------------
  GlassCard(modifier = Modifier.fillMaxWidth(), corner = 16.dp) {
    Text("Pista: $hint · errores $wrong/$MAX_WRONG", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Text(
      when {
        won && iGuess -> "🏆 ¡Adivinaste la palabra!"
        won -> "😮 ${c.partnerName} adivinó tu palabra"
        lost && iGuess -> "💀 Te colgaron… la palabra era $word"
        lost -> "😈 ¡Ganaste la ronda! ${c.partnerName} no adivinó"
        iGuess -> "Tu turno: adivina la palabra de ${c.partnerName}"
        else -> "Tu palabra: $word · ${c.partnerName} está adivinando…"
      }, color = Color.White, fontWeight = FontWeight.ExtraBold
    )
  }
  Spacer(Modifier.height(8.dp))
  // Horca 3D
  Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.15f).graphicsLayer { rotationY = -14f; cameraDistance = 14f * density }) {
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.15f)) {
      val w = size.width
      val h = size.height
      val wood = Brush.horizontalGradient(listOf(Color(0xFF8B5A2B), Color(0xFFD2A06A), Color(0xFF6B3F18)))
      drawOval(Color(0x66000000), Offset(w * 0.08f, h * 0.88f), androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.08f))
      drawLine(wood, Offset(w * 0.12f, h * 0.92f), Offset(w * 0.60f, h * 0.92f), strokeWidth = h * 0.05f, cap = StrokeCap.Round)
      drawLine(wood, Offset(w * 0.2f, h * 0.92f), Offset(w * 0.2f, h * 0.06f), strokeWidth = h * 0.045f, cap = StrokeCap.Round)
      drawLine(wood, Offset(w * 0.2f, h * 0.06f), Offset(w * 0.55f, h * 0.06f), strokeWidth = h * 0.045f, cap = StrokeCap.Round)
      drawLine(wood, Offset(w * 0.2f, h * 0.26f), Offset(w * 0.34f, h * 0.06f), strokeWidth = h * 0.03f, cap = StrokeCap.Round)
      drawLine(Color(0xFFE8D9B5), Offset(w * 0.55f, h * 0.06f), Offset(w * 0.55f, h * 0.2f), strokeWidth = h * 0.015f, cap = StrokeCap.Round)
      val skin = Brush.radialGradient(listOf(Color(0xFFFFE0BD), Color(0xFFE0A370), Color(0xFF9A6238)), center = Offset(w * 0.53f, h * 0.24f), radius = h * 0.1f)
      val limb = Brush.horizontalGradient(listOf(Color(0xFFFF8AB8), Color(0xFFD6336C)))
      if (wrong >= 1) drawCircle(skin, h * 0.075f, Offset(w * 0.55f, h * 0.27f))
      if (wrong >= 2) drawLine(limb, Offset(w * 0.55f, h * 0.345f), Offset(w * 0.55f, h * 0.60f), strokeWidth = h * 0.045f, cap = StrokeCap.Round)
      if (wrong >= 3) drawLine(limb, Offset(w * 0.55f, h * 0.40f), Offset(w * 0.45f, h * 0.52f), strokeWidth = h * 0.035f, cap = StrokeCap.Round)
      if (wrong >= 4) drawLine(limb, Offset(w * 0.55f, h * 0.40f), Offset(w * 0.65f, h * 0.52f), strokeWidth = h * 0.035f, cap = StrokeCap.Round)
      if (wrong >= 5) drawLine(limb, Offset(w * 0.55f, h * 0.60f), Offset(w * 0.47f, h * 0.78f), strokeWidth = h * 0.035f, cap = StrokeCap.Round)
      if (wrong >= 6) drawLine(limb, Offset(w * 0.55f, h * 0.60f), Offset(w * 0.63f, h * 0.78f), strokeWidth = h * 0.035f, cap = StrokeCap.Round)
    }
  }
  // Palabra
  Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
    nw.forEach { ch ->
      val shown = ch == ' ' || guessed.contains(ch.toString()) || lost || iSet
      Box(modifier = Modifier.padding(horizontal = 3.dp).height(44.dp).aspectRatio(0.8f).clip(RoundedCornerShape(8.dp))
        .background(if (ch == ' ') Color.Transparent else Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
        if (shown && ch != ' ') PopText(ch.toString(), 22.sp, Modifier, if (!guessed.contains(ch.toString())) Color(0xFFFFE08A) else Color.Unspecified)
      }
    }
  }
  if (over) {
    if (won && iGuess) ResultBanner("🏆 ¡Era \"$word\"!")
    Spacer(Modifier.height(8.dp))
    if (matchOver) GradientButton("Ver resultado del duelo", modifier = Modifier.fillMaxWidth(), onClick = { c.vm.publishGame("hang", mapOf("word" to "", "setter" to "", "next" to "", "guessed" to emptyList<String>(), "wrong" to 0) + scores()) })
    else GradientButton("Siguiente ronda (cambian los papeles)", modifier = Modifier.fillMaxWidth(), onClick = { nextRound() })
  } else if (iGuess) {
    LETTER_ROWS.forEach { row ->
      Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        row.forEach { ch ->
          val used = guessed.contains(ch.toString())
          val good = used && letters.contains(ch)
          Box(
            modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp))
              .background(if (good) Color(0xAA3DDC84) else if (used) Color(0x88FF4D4D) else Color(0x33FFFFFF))
              .border(BorderStroke(1.dp, Color(0x44FFFFFF)), RoundedCornerShape(10.dp))
              .clickable(enabled = !used) { guess(ch.toString()) },
            contentAlignment = Alignment.Center
          ) { Text(ch.toString(), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        }
      }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(value = riskWord, onValueChange = { riskWord = it.filter { ch -> ch.isLetter() || ch == ' ' }.take(18) }, label = { Text("¿Ya sabes cuál es? Arriesga (error si fallas)") }, singleLine = true, modifier = Modifier.weight(1f))
      GradientButton("Arriesgar", onClick = { risk() }, enabled = riskWord.trim().length >= 2, height = 52.dp)
    }
  } else {
    Text("👀 Mira cómo ${c.partnerName} intenta adivinar tu palabra…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      guessed.forEach { l -> Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (letters.contains(l[0])) Color(0xAA3DDC84) else Color(0x88FF4D4D)).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(l, color = Color.White, fontWeight = FontWeight.Bold) } }
    }
  }
}
