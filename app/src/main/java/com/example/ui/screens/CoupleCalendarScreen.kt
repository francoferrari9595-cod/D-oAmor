package com.example.ui.screens

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleCalendarScreen(viewModel: CoupleViewModel) {
  val partner by viewModel.partnerUser.collectAsState()
  val activeUser by viewModel.activeUser.collectAsState()
  val timeTogether by viewModel.timeTogether.collectAsState()
  val events by viewModel.calendarEvents.collectAsState()
  val coupleStartDate by viewModel.coupleStartDate.collectAsState()

  var showDatePickerDialog by remember { mutableStateOf(false) }
  var showAddEventDialog by remember { mutableStateOf(false) }

  val dateFormat = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("es", "ES"))
  val startDateFormatted = dateFormat.format(Date(coupleStartDate))

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(8.dp))
        // Title
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Nuestra Historia",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground
            )
            Text(
              text = "Juntos desde el $startDateFormatted",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Button(
            onClick = { showDatePickerDialog = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.testTag("edit_anniversary_date_btn")
          ) {
            Icon(
              imageVector = Icons.Default.EditCalendar,
              contentDescription = "Cambiar fecha",
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cambiar Fecha", fontSize = 12.sp)
          }
        }
      }

      // Hero Card: Live Anniversary Counter
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(
              width = 1.dp,
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
              shape = RoundedCornerShape(24.dp)
            )
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Text(text = activeUser.avatar, fontSize = 28.sp)
              Spacer(modifier = Modifier.width(8.dp))
              Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = HeartRed,
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = partner.avatar, fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "TIEMPO JUNTOS",
              style = MaterialTheme.typography.labelSmall,
              letterSpacing = 1.5.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Stat Counter Grid
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly
            ) {
              CounterTile(value = "${timeTogether.days}", label = "DÍAS")
              CounterTile(value = String.format("%02d", timeTogether.hours), label = "HORAS")
              CounterTile(value = String.format("%02d", timeTogether.minutes), label = "MINUTOS")
              CounterTile(value = String.format("%02d", timeTogether.seconds), label = "SEGUNDOS")
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "💍 Faltan ${timeTogether.daysUntilNextAnniversary} días para nuestro próximo gran aniversario",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        }
      }

      // Shared Calendar Section Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.CalendarMonth,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Calendario en Común",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground
            )
          }

          Text(
            text = "${events.size} eventos",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Event Cards
      items(events, key = { it.id }) { event ->
        EventCard(
          event = event,
          onToggleDone = { viewModel.toggleEventDone(event.id) },
          onDelete = { viewModel.deleteCalendarEvent(event.id) }
        )
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }

    // FAB to add Event
    FloatingActionButton(
      onClick = { showAddEventDialog = true },
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = Color.White,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(24.dp)
        .testTag("add_calendar_event_fab")
    ) {
      Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar evento")
    }
  }

  // DatePickerDialog for Anniversary
  if (showDatePickerDialog) {
    val datePickerState = rememberDatePickerState(
      initialSelectedDateMillis = coupleStartDate
    )
    DatePickerDialog(
      onDismissRequest = { showDatePickerDialog = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let {
              viewModel.setCoupleStartDate(it)
            }
            showDatePickerDialog = false
          }
        ) {
          Text("Guardar Fecha")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePickerDialog = false }) {
          Text("Cancelar")
        }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }

  // Add Event Dialog
  if (showAddEventDialog) {
    AddEventDialog(
      onDismiss = { showAddEventDialog = false },
      onConfirm = { title, desc, dateMillis, type, location ->
        viewModel.addCalendarEvent(title, desc, dateMillis, type, location)
        showAddEventDialog = false
      }
    )
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
