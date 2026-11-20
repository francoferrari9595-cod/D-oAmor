package com.example.trivia

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GradientButton
import com.example.ui.components.OptionChip
import com.example.ui.components.SoftButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tFieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = T_NEON_CYAN, unfocusedBorderColor = Color(0x88FFFFFF),
  cursorColor = T_NEON_CYAN, focusedLabelColor = T_NEON_CYAN, unfocusedLabelColor = Color(0xFFC9BEDD)
)

@Composable
private fun TPanel(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  val shape = RoundedCornerShape(16.dp)
  Column(modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xE6180E44), Color(0xE60A0522)))).border(1.5.dp, Color(0x6600E5FF), shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** Gestión de preguntas: activar/desactivar, añadir preguntas propias y borrarlas. Todo local al anfitrión. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TriviaAdmin(ctx: Context, onBack: () -> Unit) {
  var version by remember { mutableIntStateOf(0) }
  var catFilter by remember { mutableStateOf("hist") }
  var adding by remember { mutableStateOf(false) }
  val all = remember(version) { TriviaStore.allQs(ctx) }
  val off = remember(version) { TriviaStore.disabled(ctx) }
  Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
    TriviaBackdrop(Modifier.fillMaxSize())
    Column(Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← Volver", color = T_GOLD, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.weight(1f))
        Text("📝 Preguntas", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
      }
      Text("${all.size} preguntas · ${all.count { it.id !in off }} activas · ${all.count { it.custom }} propias", color = Color(0xFFC9BEDD), fontSize = 12.sp)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T_CATS.forEach { k -> OptionChip("${k.emoji} ${k.name} (${all.count { it.cat == k.id }})", catFilter == k.id, { catFilter = k.id; adding = false }) }
      }
      if (adding) {
        TAddForm(ctx, catFilter, { adding = false; version++ })
      } else {
        GradientButton("➕ Añadir pregunta en ${tCat(catFilter).name}", modifier = Modifier.fillMaxWidth(), onClick = { adding = true })
        all.filter { it.cat == catFilter }.forEach { q ->
          TPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(q.q, color = if (q.id in off) Color(0xFF8E8E9E) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("R: ${q.a}  ·  ${"⭐".repeat(q.diff)} ${tDiffName(q.diff)}${if (q.custom) "  ·  propia" else ""}", color = T_NEON_CYAN, fontSize = 11.sp)
              }
              Switch(checked = q.id !in off, onCheckedChange = { TriviaStore.setDisabled(ctx, q.id, !it); version++ })
            }
            if (q.custom) TextButton(onClick = { TriviaStore.deleteCustom(ctx, q.id); version++ }) { Text("🗑 Eliminar", color = T_BAD, fontSize = 12.sp) }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TAddForm(ctx: Context, cat: String, onDone: () -> Unit) {
  var diff by remember { mutableIntStateOf(1) }
  var q by remember { mutableStateOf("") }
  var a by remember { mutableStateOf("") }
  var alt by remember { mutableStateOf("") }
  var err by remember { mutableStateOf("") }
  TPanel(Modifier.fillMaxWidth()) {
    Text("Nueva pregunta de ${tCat(cat).emoji} ${tCat(cat).name}", color = T_GOLD, fontWeight = FontWeight.Black, fontSize = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..3).forEach { d -> OptionChip(tDiffName(d), diff == d, { diff = d }) } }
    OutlinedTextField(q, { q = it }, label = { Text("Pregunta") }, modifier = Modifier.fillMaxWidth(), colors = tFieldColors)
    OutlinedTextField(a, { a = it }, label = { Text("Respuesta verificable") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tFieldColors)
    OutlinedTextField(alt, { alt = it }, label = { Text("Otras formas válidas (separadas por ;)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tFieldColors)
    if (err.isNotEmpty()) Text(err, color = T_BAD, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SoftButton("Cancelar", onClick = onDone, modifier = Modifier.weight(1f))
      GradientButton("Guardar", modifier = Modifier.weight(1f), onClick = {
        if (q.trim().length < 8 || a.trim().isEmpty()) err = "Escribe la pregunta completa y su respuesta."
        else { TriviaStore.addCustom(ctx, cat, diff, q, a, alt.split(";").map { it.trim() }.filter { it.isNotEmpty() }); onDone() }
      })
    }
  }
}

/** Estadísticas personales e historial de las últimas partidas en este dispositivo. */
@Composable
internal fun TriviaStats(ctx: Context, onBack: () -> Unit) {
  val st = remember { TriviaStore.stats(ctx) }
  val hist = remember { TriviaStore.history(ctx) }
  val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale("es")) }
  Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
    TriviaBackdrop(Modifier.fillMaxSize())
    Column(Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← Volver", color = T_GOLD, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.weight(1f))
        Text("📊 Estadísticas", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
      }
      TPanel(Modifier.fillMaxWidth()) {
        val tot = st.ok + st.bad
        Text("Partidas: ${st.played} · Ganadas: ${st.won}", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Aciertos: ${st.ok} · Errores: ${st.bad}${if (tot > 0) " · ${st.ok * 100 / tot}% de acierto" else ""}", color = T_NEON_CYAN, fontSize = 13.sp)
      }
      TPanel(Modifier.fillMaxWidth()) {
        Text("Por categoría", color = T_GOLD, fontWeight = FontWeight.Black, fontSize = 13.sp)
        T_CATS.forEach { k ->
          val (o, b) = st.byCat[k.id] ?: (0 to 0)
          if (o + b > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("${k.emoji} ${k.name}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
              Text("✅ $o  ❌ $b", color = Color(0xFFC9BEDD), fontSize = 12.sp)
            }
          }
        }
        if (st.byCat.isEmpty()) Text("Aún no hay respuestas registradas.", color = Color(0xFFC9BEDD), fontSize = 12.sp)
      }
      Text("Historial de resultados", color = T_GOLD, fontWeight = FontWeight.Black, fontSize = 13.sp)
      if (hist.isEmpty()) Text("Todavía no has terminado ninguna partida.", color = Color(0xFFC9BEDD), fontSize = 12.sp)
      hist.forEach { h ->
        TPanel(Modifier.fillMaxWidth()) {
          Text("${fmt.format(Date(h.t))} · ${h.rounds} preguntas · 🏆 ${h.winner}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          Text(h.scores.joinToString("  ·  ") { "${it.first} ${it.second}" }, color = T_NEON_CYAN, fontSize = 11.sp)
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}
