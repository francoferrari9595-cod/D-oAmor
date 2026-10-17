package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

@Composable
internal fun HangmanGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("hang") }
  val st by flow.collectAsState(initial = null)
  val started = ((st?.get("word") as? String) ?: "").isNotEmpty()
  val word = (st?.get("word") as? String) ?: ""
  val cat = (st?.get("cat") as? String) ?: ""
  val guessed = (st?.get("guessed") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val wrong = (st?.get("wrong") as? Number)?.toInt() ?: 0
  val turn = (st?.get("turn") as? String) ?: c.firstUid
  val setter = (st?.get("setter") as? String) ?: ""
  val score = (st?.get("score") as? Number)?.toInt() ?: 0
  val nw = normWord(word)
  val letters = nw.filter { it != ' ' }.toSet()
  val won = started && letters.all { guessed.contains(it.toString()) }
  val lost = started && wrong >= MAX_WRONG
  val over = won || lost
  val duel = setter.isNotEmpty()
  val iGuess = if (duel) setter != c.me else turn == c.me
  var selCat by remember { mutableStateOf(HANG_CATS.keys.first()) }
  var customWord by remember { mutableStateOf("") }

  fun start(newWord: String, category: String, setterUid: String) {
    c.vm.publishGame("hang", mapOf(
      "word" to newWord, "cat" to category, "guessed" to emptyList<String>(), "wrong" to 0,
      "turn" to c.firstUid, "setter" to setterUid, "score" to score
    ))
    c.vm.gameSfx(1)
  }

  fun guess(letter: String) {
    if (!started || over || !iGuess || guessed.contains(letter)) return
    val ok = letters.contains(letter[0])
    val ng = guessed + letter
    val nWrong = if (ok) wrong else wrong + 1
    val nowWon = letters.all { ng.contains(it.toString()) }
    val nextTurn = if (duel) turn else c.otherUid
    c.vm.publishGame("hang", mapOf(
      "word" to word, "cat" to cat, "guessed" to ng, "wrong" to nWrong, "turn" to nextTurn, "setter" to setter,
      "score" to if (nowWon) score + 1 else score
    ))
    c.vm.gameSfx(if (ok) 2 else 0)
  }

  if (!started) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🪢 Ahorcado", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
      Text("Elige una categoría: juegan los dos por turnos adivinando la misma palabra. O escribe tu propia palabra secreta para que tu pareja la adivine.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      HANG_CATS.keys.forEach { k ->
        if (selCat == k) GradientButton(k, onClick = { selCat = k }, height = 40.dp) else SoftButton(k, onClick = { selCat = k })
      }
    }
    Spacer(Modifier.height(8.dp))
    GradientButton("Palabra al azar (jugamos juntos)", modifier = Modifier.fillMaxWidth(), onClick = { start(HANG_CATS[selCat]!!.random(), selCat, "") })
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(value = customWord, onValueChange = { customWord = it.filter { ch -> ch.isLetter() || ch == ' ' }.take(18) }, label = { Text("O escribe tu palabra secreta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    SoftButton("Retar a ${c.partnerName} con mi palabra", enabled = customWord.trim().length >= 3, modifier = Modifier.fillMaxWidth(), onClick = { start(customWord.trim().uppercase(), "✍️ Palabra de ${c.nameOf(c.me)}", c.me) })
    return
  }

  GlassCard(modifier = Modifier.fillMaxWidth(), corner = 16.dp) {
    Text("$cat · aciertos juntos: $score", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Text(
      when {
        won -> "🏆 ¡Adivinaron!"
        lost -> "💀 Perdieron… la palabra era $word"
        iGuess && !duel -> "Tu turno: elige una letra"
        iGuess -> "Adivina la palabra de ${c.partnerName}"
        duel -> "${c.partnerName} está adivinando tu palabra…"
        else -> "Turno de ${c.partnerName}…"
      }, color = Color.White, fontWeight = FontWeight.ExtraBold
    )
  }
  Spacer(Modifier.height(8.dp))
  // Horca 3D
  Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).graphicsLayer { rotationY = -14f; cameraDistance = 14f * density }) {
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f)) {
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
      val shown = ch == ' ' || guessed.contains(ch.toString()) || lost
      Box(modifier = Modifier.padding(horizontal = 3.dp).height(44.dp).aspectRatio(0.8f).clip(RoundedCornerShape(8.dp))
        .background(if (ch == ' ') Color.Transparent else Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
        if (shown && ch != ' ') PopText(ch.toString(), 22.sp, Modifier, if (!guessed.contains(ch.toString()) && lost) Color(0xFFFF8A8A) else Color.Unspecified)
      }
    }
  }
  if (over) {
    if (won) ResultBanner("🏆 ¡Adivinaron \"$word\"!")
    Spacer(Modifier.height(8.dp))
    GradientButton("Nueva palabra", modifier = Modifier.fillMaxWidth(), onClick = {
      c.vm.publishGame("hang", mapOf("word" to "", "score" to score))
    })
  } else {
    LETTER_ROWS.forEach { row ->
      Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        row.forEach { ch ->
          val used = guessed.contains(ch.toString())
          val good = used && letters.contains(ch)
          Box(
            modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp))
              .background(if (good) Color(0xAA3DDC84) else if (used) Color(0x88FF4D4D) else Color(0x33FFFFFF))
              .border(BorderStroke(1.dp, Color(0x44FFFFFF)), RoundedCornerShape(10.dp))
              .clickable(enabled = iGuess && !used) { guess(ch.toString()) },
            contentAlignment = Alignment.Center
          ) { Text(ch.toString(), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        }
      }
    }
  }
}
