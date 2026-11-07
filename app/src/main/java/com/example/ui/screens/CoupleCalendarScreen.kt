package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.components.GlassCard
import java.util.Calendar
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CoupleCalendarEvent
import androidx.compose.ui.draw.alpha
import com.example.model.EventType
import com.example.ui.CoupleViewModel
import com.example.ui.theme.HeartRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────── Historia: calendario compartido, notas y buzón (diseño de vidrio) ─────────────
private val CalPink = Color(0xFFFF5C93)
private val CalCyan = Color(0xFF38BDF8)
private val CalViolet = Color(0xFF8B5CF6)
private val CalWeekDays = listOf("L", "M", "X", "J", "V", "S", "D")

private fun calKey(y: Int, m0: Int, d: Int): String = String.format(Locale.US, "%04d-%02d-%02d", y, m0 + 1, d)
private fun calDayKey(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
private fun calKeyToCalendar(key: String, hour: Int): Calendar {
  val c = Calendar.getInstance()
  val parts = key.split("-").mapNotNull { it.toIntOrNull() }
  if (parts.size == 3) c.set(parts[0], parts[1] - 1, parts[2], hour, 0, 0)
  c.set(Calendar.MILLISECOND, 0)
  return c
}

@Composable
private fun CalField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true) {
  OutlinedTextField(
    value = value, onValueChange = onChange, label = { Text(label, fontSize = 12.sp) }, singleLine = singleLine,
    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = Color.White, unfocusedTextColor = Color.White,
      focusedBorderColor = CalCyan, unfocusedBorderColor = Color(0x55FFFFFF),
      focusedLabelColor = CalCyan, unfocusedLabelColor = Color(0xCCFFFFFF), cursorColor = Color.White
    ),
    modifier = modifier
  )
}

@Composable
private fun CalPill(text: String, selected: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(50)
  Box(
    Modifier.clip(shape)
      .background(if (selected) Brush.linearGradient(listOf(CalCyan, CalViolet)) else Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x14FFFFFF))))
      .border(1.dp, if (selected) Color.White.copy(alpha = 0.6f) else Color(0x33FFFFFF), shape)
      .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp)
  ) { Text(text, color = Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleCalendarScreen(viewModel: CoupleViewModel) {
  val partner by viewModel.partnerUser.collectAsState()
  val activeUser by viewModel.activeUser.collectAsState()
  val timeTogether by viewModel.timeTogether.collectAsState()
  val events by viewModel.calendarEvents.collectAsState()
  val notes by viewModel.coupleNotes.collectAsState()
  val coupleStartDate by viewModel.coupleStartDate.collectAsState()
  val partnerName by viewModel.partnerDisplayName.collectAsState()
  val myName by viewModel.myDisplayName.collectAsState()
  val me = viewModel.myUid() ?: ""

  val now = remember { Calendar.getInstance() }
  val todayKey = remember { calKey(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)) }
  var year by remember { mutableStateOf(now.get(Calendar.YEAR)) }
  var month by remember { mutableStateOf(now.get(Calendar.MONTH)) }
  var selected by remember { mutableStateOf(todayKey) }
  var showDatePickerDialog by remember { mutableStateOf(false) }
  var adding by remember { mutableStateOf(false) }
  var addingNote by remember { mutableStateOf(false) }
  var planTitle by remember { mutableStateOf("") }
  var planPlace by remember { mutableStateOf("") }
  var planType by remember { mutableStateOf(EventType.DATE_NIGHT) }
  var planHour by remember { mutableStateOf(20) }
  var dayNote by remember { mutableStateOf("") }
  var mailbox by remember { mutableStateOf("") }

  val eventsByDay = remember(events) { events.groupBy { calDayKey(it.dateMillis) } }
  val notesByDay = remember(notes) { notes.filter { it.dayKey.isNotEmpty() }.groupBy { it.dayKey } }
  val mailboxNotes = remember(notes) { notes.filter { it.dayKey.isEmpty() }.sortedByDescending { it.createdAt } }

  // Notas nuevas de tu pareja: se marcan con "NUEVA" durante esta visita y se avisa que ya las leíste.
  val unread = notes.count { it.from != me && me !in it.readBy }
  var newIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  LaunchedEffect(notes) {
    val fresh = notes.filter { it.from != me && me !in it.readBy }.map { it.id }
    if (fresh.isNotEmpty()) newIds = newIds + fresh
  }
  LaunchedEffect(unread) {
    if (unread > 0) { delay(2500); viewModel.markNotesRead() }
  }

  val dayFormat = remember { SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES")) }
  val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("es", "ES")) }
  val startDateFormatted = remember(coupleStartDate) { SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("es", "ES")).format(Date(coupleStartDate)) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Encabezado compacto
    item {
      Spacer(Modifier.height(8.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          Text("Nuestra historia", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
          Text("Juntos desde el $startDateFormatted", color = Color(0xB3FFFFFF), fontSize = 11.sp)
        }
        CalPill("📅 Cambiar fecha", false) { showDatePickerDialog = true }
      }
    }

    // Resumen del tiempo juntos, en una sola fila
    item {
      GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text("${activeUser.avatar}❤️${partner.avatar}", fontSize = 24.sp)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text("${timeTogether.days} días juntos", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(
              String.format(Locale.US, "%02d:%02d:%02d", timeTogether.hours, timeTogether.minutes, timeTogether.seconds),
              color = Color(0xCCFFFFFF), fontSize = 12.sp
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text("💍", fontSize = 18.sp)
            Text("en ${timeTogether.daysUntilNextAnniversary} días", color = Color(0xFFFFC857), fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Aviso de notas nuevas de la pareja
    if (newIds.isNotEmpty()) {
      item {
        GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp, highlight = true) {
          Text(
            "💌 $partnerName te dejó ${newIds.size} ${if (newIds.size == 1) "nota nueva" else "notas nuevas"}",
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
          )
        }
      }
    }

    // Calendario mensual en vidrio
    item {
      GlassCard(modifier = Modifier.fillMaxWidth(), corner = 24.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color(0x22FFFFFF)).clickable {
              if (month == 0) { month = 11; year -= 1 } else month -= 1
            }, contentAlignment = Alignment.Center
          ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
          val monthTitle = monthFormat.format(calKeyToCalendar(calKey(year, month, 1), 12).time).replaceFirstChar { it.uppercase() }
          Text(monthTitle, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
          Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color(0x22FFFFFF)).clickable {
              if (month == 11) { month = 0; year += 1 } else month += 1
            }, contentAlignment = Alignment.Center
          ) { Text("›", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
          CalWeekDays.forEach { Text(it, color = Color(0x99FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        val first = calKeyToCalendar(calKey(year, month, 1), 12)
        val firstOffset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7 // lunes = 0
        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val rows = (firstOffset + daysInMonth + 6) / 7
        for (r in 0 until rows) {
          Row(Modifier.fillMaxWidth()) {
            for (col in 0 until 7) {
              val d = r * 7 + col - firstOffset + 1
              if (d < 1 || d > daysInMonth) { Spacer(Modifier.weight(1f).height(44.dp)); continue }
              val key = calKey(year, month, d)
              val isSel = key == selected
              val isToday = key == todayKey
              Box(
                Modifier.weight(1f).height(44.dp).padding(2.dp).clip(RoundedCornerShape(14.dp))
                  .background(if (isSel) Brush.linearGradient(listOf(CalCyan, CalViolet)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                  .border(if (isToday && !isSel) 1.5.dp else 0.dp, if (isToday && !isSel) Color(0xFFFFC857) else Color.Transparent, RoundedCornerShape(14.dp))
                  .clickable { selected = key },
                contentAlignment = Alignment.Center
              ) {
                Text("$d", color = Color.White, fontSize = 14.sp, fontWeight = if (isSel || isToday) FontWeight.ExtraBold else FontWeight.Medium)
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                  if (eventsByDay.containsKey(key)) Box(Modifier.size(5.dp).clip(CircleShape).background(CalPink))
                  if (notesByDay.containsKey(key)) Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFFFE066)))
                }
              }
            }
          }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(CalPink)); Spacer(Modifier.width(4.dp)); Text("Planes", color = Color(0xB3FFFFFF), fontSize = 10.sp) }
          Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFFE066))); Spacer(Modifier.width(4.dp)); Text("Notas", color = Color(0xB3FFFFFF), fontSize = 10.sp) }
        }
      }
    }

    // Día elegido: planes y notas pegadas en ese día
    item {
      val dayEvents = (eventsByDay[selected] ?: emptyList()).sortedBy { it.dateMillis }
      val dayNotes = (notesByDay[selected] ?: emptyList()).sortedBy { it.createdAt }
      val title = dayFormat.format(calKeyToCalendar(selected, 12).time).replaceFirstChar { it.uppercase() }
      GlassCard(modifier = Modifier.fillMaxWidth(), corner = 24.dp, highlight = true) {
        Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        if (dayEvents.isEmpty() && dayNotes.isEmpty()) {
          Text("Nada anotado todavía. Agregá un plan o dejá una nota para este día.", color = Color(0xB3FFFFFF), fontSize = 12.sp)
        }
        dayEvents.forEach { ev ->
          Spacer(Modifier.height(8.dp))
          Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x1AFFFFFF)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(CalPink.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) { Text(ev.eventType.emoji, fontSize = 17.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text(
                ev.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                textDecoration = if (ev.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
              )
              val hour = SimpleDateFormat("HH:mm", Locale.US).format(Date(ev.dateMillis))
              Text(hour + if (ev.location.isNotBlank()) " · 📍 ${ev.location}" else "", color = Color(0xB3FFFFFF), fontSize = 11.sp, maxLines = 1)
            }
            Text(if (ev.done) "✅" else "⬜", fontSize = 18.sp, modifier = Modifier.clickable { viewModel.toggleEventDone(ev.id) }.padding(6.dp))
            Text("🗑", fontSize = 16.sp, modifier = Modifier.clickable { viewModel.deleteCalendarEvent(ev.id) }.padding(6.dp))
          }
        }
        dayNotes.forEach { n ->
          Spacer(Modifier.height(8.dp))
          val mine = n.from == me
          Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (mine) Color(0xFFFFF3B0) else Color(0xFFFFC8DD)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(Modifier.weight(1f)) {
              Text(n.text, color = Color(0xFF2B2B33), fontSize = 13.sp)
              Text(if (mine) "Vos" else partnerName, color = Color(0xAA2B2B33), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (mine) Text("✕", color = Color(0xAA2B2B33), fontSize = 14.sp, modifier = Modifier.clickable { viewModel.deleteNote(n.id) }.padding(6.dp))
          }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CalPill("＋ Plan", adding) { adding = !adding; if (adding) addingNote = false }
          CalPill("📝 Nota en este día", addingNote) { addingNote = !addingNote; if (addingNote) adding = false }
        }
        if (adding) {
          Spacer(Modifier.height(8.dp))
          CalField(planTitle, { planTitle = it.take(60) }, "¿Qué van a hacer?", Modifier.fillMaxWidth())
          Spacer(Modifier.height(6.dp))
          CalField(planPlace, { planPlace = it.take(60) }, "Lugar (opcional)", Modifier.fillMaxWidth())
          Spacer(Modifier.height(8.dp))
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EventType.values().forEach { t -> CalPill("${t.emoji} ${t.label}", planType == t) { planType = t } }
          }
          Spacer(Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10 to "Mañana 10:00", 16 to "Tarde 16:00", 20 to "Noche 20:00").forEach { (h, label) -> CalPill(label, planHour == h) { planHour = h } }
          }
          Spacer(Modifier.height(8.dp))
          CalPill("Guardar plan", true) {
            if (planTitle.isNotBlank()) {
              viewModel.addCalendarEvent(planTitle.trim(), "", calKeyToCalendar(selected, planHour).timeInMillis, planType, planPlace.trim())
              planTitle = ""; planPlace = ""; adding = false
            }
          }
        }
        if (addingNote) {
          Spacer(Modifier.height(8.dp))
          CalField(dayNote, { dayNote = it.take(300) }, "Escribí algo para este día", Modifier.fillMaxWidth(), singleLine = false)
          Spacer(Modifier.height(8.dp))
          CalPill("Pegar nota", true) {
            if (dayNote.isNotBlank()) { viewModel.addNote(dayNote, selected); dayNote = ""; addingNote = false }
          }
        }
      }
    }

    // Buzón: notas sueltas para que tu pareja las lea cuando entre
    item {
      GlassCard(modifier = Modifier.fillMaxWidth(), corner = 24.dp) {
        Text("💌 Buzón de notas", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text("Dejale una nota a $partnerName: la va a ver apenas entre.", color = Color(0xB3FFFFFF), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        if (mailboxNotes.isEmpty()) {
          Text("Todavía no hay notas. ¡Escribí la primera!", color = Color(0x99FFFFFF), fontSize = 12.sp)
        } else {
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            mailboxNotes.forEachIndexed { i, n ->
              val mine = n.from == me
              val isNew = n.id in newIds
              Box(
                Modifier.width(150.dp).graphicsLayer { rotationZ = if (i % 2 == 0) -1.8f else 1.8f }
                  .shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                  .background(if (mine) Color(0xFFFFF3B0) else Color(0xFFFFC8DD)).padding(12.dp)
              ) {
                Column {
                  Text(n.text, color = Color(0xFF2B2B33), fontSize = 13.sp, maxLines = 6)
                  Spacer(Modifier.height(6.dp))
                  Text(
                    (if (mine) "Vos" else partnerName) + " · " + SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(n.createdAt)),
                    color = Color(0xAA2B2B33), fontSize = 10.sp, fontWeight = FontWeight.Bold
                  )
                  if (mine && n.readBy.any { it != me }) Text("✓✓ leída", color = Color(0xFF2E7D32), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (isNew) {
                  Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(CalPink).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text("NUEVA", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                  }
                } else if (mine) {
                  Text("✕", color = Color(0xAA2B2B33), fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).clickable { viewModel.deleteNote(n.id) })
                }
              }
            }
          }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          CalField(mailbox, { mailbox = it.take(300) }, "Escribí una nota para $partnerName", Modifier.weight(1f), singleLine = false)
          Spacer(Modifier.width(8.dp))
          Box(
            Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(CalPink, CalViolet)))
              .clickable { if (mailbox.isNotBlank()) { viewModel.addNote(mailbox, ""); mailbox = "" } },
            contentAlignment = Alignment.Center
          ) { Text("➤", color = Color.White, fontSize = 18.sp) }
        }
      }
    }

    // Próximos planes, en filas compactas
    item {
      val startOfToday = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis }
      val upcoming = events.filter { it.dateMillis >= startOfToday && !it.done }.sortedBy { it.dateMillis }.take(5)
      GlassCard(modifier = Modifier.fillMaxWidth(), corner = 24.dp) {
        Text("Próximos planes", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        if (upcoming.isEmpty()) Text("No hay planes por delante. ¡Armen uno!", color = Color(0xB3FFFFFF), fontSize = 12.sp)
        upcoming.forEach { ev ->
          val c = Calendar.getInstance().apply { timeInMillis = ev.dateMillis }
          Spacer(Modifier.height(8.dp))
          Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF))
              .clickable { year = c.get(Calendar.YEAR); month = c.get(Calendar.MONTH); selected = calDayKey(ev.dateMillis) }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(
              Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(CalPink, CalViolet))),
              horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
              Text("${c.get(Calendar.DAY_OF_MONTH)}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
              Text(SimpleDateFormat("MMM", Locale("es", "ES")).format(Date(ev.dateMillis)).uppercase(), color = Color(0xE6FFFFFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text("${ev.eventType.emoji} ${ev.title}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
              Text(SimpleDateFormat("HH:mm", Locale.US).format(Date(ev.dateMillis)) + if (ev.location.isNotBlank()) " · 📍 ${ev.location}" else "", color = Color(0xB3FFFFFF), fontSize = 11.sp, maxLines = 1)
            }
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }

  // Cambiar la fecha de aniversario (diálogo de fecha)
  if (showDatePickerDialog) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = coupleStartDate)
    DatePickerDialog(
      onDismissRequest = { showDatePickerDialog = false },
      confirmButton = {
        TextButton(onClick = {
          datePickerState.selectedDateMillis?.let { viewModel.setCoupleStartDate(it) }
          showDatePickerDialog = false
        }) { Text("Guardar fecha") }
      },
      dismissButton = { TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancelar") } }
    ) { DatePicker(state = datePickerState) }
  }
}

@Composable
fun CounterTile(value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      fontSize = 24.sp,
      fontWeight = FontWeight.Black,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = label,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      letterSpacing = 1.sp
    )
  }
}

@Composable
fun EventCard(event: CoupleCalendarEvent, onToggleDone: () -> Unit = {}, onDelete: () -> Unit = {}) {
  val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale("es", "ES"))
  val dateFormatted = dateFormat.format(Date(event.dateMillis))

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth().alpha(if (event.done) 0.55f else 1f)
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Text(text = event.eventType.emoji, fontSize = 22.sp)
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = event.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
          ) {
            Text(
              text = event.eventType.label,
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = dateFormatted,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.primary
        )

        if (event.description.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = event.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        if (event.location.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = event.location,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.TextButton(onClick = onToggleDone) { Text(if (event.done) "↩︎ Reabrir" else "✅ Concluida", fontSize = 11.sp) }
        androidx.compose.material3.TextButton(onClick = onDelete) { Text("🗑️ Borrar", fontSize = 11.sp) }
      }
    }
  }
}

@Composable
fun AddEventDialog(
  onDismiss: () -> Unit,
  onConfirm: (String, String, Long, EventType, String) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var desc by remember { mutableStateOf("") }
  var location by remember { mutableStateOf("") }
  var selectedType by remember { mutableStateOf(EventType.DATE_NIGHT) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text("Nuevo Evento de Pareja", fontWeight = FontWeight.Bold)
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Título (ej: Cena aniversario)") },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
          value = desc,
          onValueChange = { desc = it },
          label = { Text("Detalles o sorpresa") },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
          value = location,
          onValueChange = { location = it },
          label = { Text("Lugar (opcional)") },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp)
        )

        Text("Tipo de Evento:", style = MaterialTheme.typography.labelSmall)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          EventType.values().take(3).forEach { type ->
            FilterChip(
              selected = selectedType == type,
              onClick = { selectedType = type },
              label = { Text("${type.emoji} ${type.label}", fontSize = 11.sp) }
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (title.isNotBlank()) {
            val eventDate = System.currentTimeMillis() + 86400000L * 7 // default in 7 days
            onConfirm(title, desc, eventDate, selectedType, location)
          }
        },
        enabled = title.isNotBlank()
      ) {
        Text("Agregar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancelar")
      }
    }
  )
}
