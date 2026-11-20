package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GIFTS
import com.example.ui.CoupleViewModel
import com.example.ui.theme.GoldYellow

/**
 * Ventana que se abre al tocar la foto de una persona: permite enviarle un regalo a ella
 * (y, si tiene foto, verla ampliada).
 */
@Composable
fun GiftToPersonDialog(vm: CoupleViewModel, personName: String, onViewPhoto: (() -> Unit)?, onDismiss: () -> Unit) {
  val coins by vm.myCredits.collectAsState()
  var info by remember { mutableStateOf("") }
  val viewBtn: (@Composable () -> Unit)? = if (onViewPhoto != null) {
    @Composable { TextButton(onClick = { onDismiss(); onViewPhoto() }) { Text("Ver foto") } }
  } else null
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("🎁 Regalo para $personName") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Tus fichas: 🪙 $coins", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (info.isNotEmpty()) Text(info, color = Color(0xFF9CFF7A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        GIFTS.chunked(3).forEach { row ->
          Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { g ->
              Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(g.c2.copy(alpha = 0.7f), g.c1.copy(alpha = 0.25f))))
                  .clickable {
                    info = if (vm.sendGift(g.id)) "¡${g.name} enviado a $personName! 🎁" else "No te alcanzan las fichas 😢"
                  }.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(g.emoji, fontSize = 28.sp)
                Text(g.name, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
                Text("${g.price} 🪙", color = GoldYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    dismissButton = viewBtn
  )
}
