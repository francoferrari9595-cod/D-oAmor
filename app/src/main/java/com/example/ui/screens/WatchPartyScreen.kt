package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.CoupleViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow


private fun extractVideoId(input: String): String? {
  val t = input.trim()
  if (Regex("^[A-Za-z0-9_-]{11}$").matches(t)) return t
  val m = Regex("""(?:v=|/embed/|/shorts/|/live/|youtu\.be/)([A-Za-z0-9_-]{11})""").find(t)
  return m?.groupValues?.get(1)
}

@Composable
fun WatchPartyScreen(vm: CoupleViewModel) {
  val chatFlow = remember { vm.gameState("watchChat") }
  val chatDoc by chatFlow.collectAsState(initial = null)
  val me = vm.myUid() ?: ""
  val w = vm.watch
  val vid by w.vid.collectAsState()
  val playing by w.playing.collectAsState()
  val curTime by w.time.collectAsState()
  val duration by w.duration.collectAsState()
  val errorCode by w.error.collectAsState()
  val by by w.controller.collectAsState()
  val partnerName by vm.partnerDisplayName.collectAsState()
  val myName by vm.myDisplayName.collectAsState()
  val ctx = LocalContext.current

  var urlText by remember { mutableStateOf("") }
  var showLink by remember { mutableStateOf(false) }
  var linkError by remember { mutableStateOf(false) }
  var dragging by remember { mutableStateOf(false) }
  var dragValue by remember { mutableStateOf(0f) }
  var chatText by remember { mutableStateOf("") }

  val lights = rememberInfiniteTransition(label = "marquee")
  val chase by lights.animateFloat(0f, 12f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "chase")

  @Suppress("UNCHECKED_CAST")
  val msgs = (chatDoc?.get("msgs") as? List<Map<String, Any?>>) ?: emptyList()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(listOf(Color(0xFF2A0008), Color(0xFF0B0003), Color(0xFF000000))))
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 12.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(modifier = Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
      for (i in 0 until 14) {
        val on = ((chase.toInt() + i) % 3) == 0
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) GoldYellow else Color(0xFF5A4A1A)))
      }
    }
    Spacer(Modifier.height(6.dp))

    // Pantalla (sin recortes ni capas: así el video no se ve negro)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .border(BorderStroke(3.dp, Brush.linearGradient(listOf(GoldYellow, Color(0xFF8A5A00), GoldYellow))))
        .background(Color.Black),
      contentAlignment = Alignment.Center
    ) {
      if (vid.isEmpty()) {
        Text("🍿 Toca “🔗 Cargar video” abajo", color = Color(0xFFBBA878), textAlign = TextAlign.Center, fontSize = 14.sp)
      } else {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { _ -> w.webView() })
      }
    }
    if (errorCode != 0 && vid.isNotEmpty()) {
      Text("⚠️ YouTube no deja reproducir este video aquí (error $errorCode).", color = Color(0xFFFF9AA8), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
      SoftButton("▶ Abrir en YouTube", onClick = {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$vid")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
      })
    }

    if (vid.isNotEmpty()) {
      val shown = if (dragging) dragValue else curTime.toFloat()
      val maxV = if (duration > 0) duration.toFloat() else 1f
      Slider(
        value = shown.coerceIn(0f, maxV),
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = { dragging = false; w.seek(dragValue.toDouble()) },
        valueRange = 0f..maxV
      )
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        SoftButton("⏪ 10", onClick = { w.skip(-10.0) })
        GradientButton(if (playing) "⏸ Pausar" else "▶ Reproducir", onClick = { if (playing) w.pause() else w.play() }, modifier = Modifier.width(140.dp), height = 46.dp)
        SoftButton("10 ⏩", onClick = { w.skip(10.0) })
      }
      Text("${fmtTime(curTime)} / ${fmtTime(duration)}  ·  ${if (by == me) "controlas tú" else "controla $partnerName"} · sincronizado con $partnerName", color = Color(0xFFBBA878), fontSize = 11.sp, textAlign = TextAlign.Center)
    }

    Spacer(Modifier.height(8.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 16.dp, contentPadding = PaddingValues(10.dp)) {
      Text("💬 Chat del cine", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
      msgs.takeLast(6).forEach { m ->
        val mine = (m["by"] as? String) == me
        Text("${if (mine) "Tú" else (m["n"] as? String ?: partnerName)}: ${m["text"] as? String ?: ""}", color = if (mine) Color(0xFFFFE9A8) else Color.White, fontSize = 13.sp)
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        OutlinedTextField(value = chatText, onValueChange = { chatText = it.take(140) }, singleLine = true, placeholder = { Text("Escribe…") }, modifier = Modifier.weight(1f))
        GradientButton("Enviar", onClick = {
          val txt = chatText.trim()
          if (txt.isNotEmpty()) {
            val list = msgs.takeLast(29) + mapOf<String, Any?>("by" to me, "n" to myName, "text" to txt, "t" to System.currentTimeMillis())
            vm.patchGame("watchChat", mapOf("msgs" to list))
            chatText = ""
          }
        }, enabled = chatText.isNotBlank(), height = 48.dp)
      }
    }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      WalkieButton(vm, 64.dp)
      Text("📻 Mantén para hablar", color = Color(0xFFD9C7A0), fontSize = 12.sp)
    }

    Spacer(Modifier.height(10.dp))
    SoftButton(if (showLink) "Ocultar" else "🔗 Cargar video", onClick = { showLink = !showLink })
    if (showLink) {
      Spacer(Modifier.height(6.dp))
      OutlinedTextField(value = urlText, onValueChange = { urlText = it; linkError = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Link de YouTube") }, singleLine = true)
      Spacer(Modifier.height(6.dp))
      GradientButton("Cargar para los dos 🎞️", onClick = {
        val id = extractVideoId(urlText)
        if (id != null) { w.load(id); urlText = ""; showLink = false } else linkError = true
      }, modifier = Modifier.fillMaxWidth(), enabled = urlText.isNotBlank())
      if (linkError) Text("No reconozco ese link.", color = Color(0xFFFF9AA8), fontSize = 12.sp)
    }
    Spacer(Modifier.height(24.dp))
  }
}

/** Mini reproductor flotante: el video sigue sonando mientras juegas o chateas. */
@Composable
fun WatchMiniPlayer(vm: CoupleViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
  val vid by vm.watch.vid.collectAsState()
  val playing by vm.watch.playing.collectAsState()
  if (vid.isEmpty()) return
  Box(
    modifier = modifier
      .size(width = 150.dp, height = 96.dp)
      .border(BorderStroke(2.dp, GoldYellow))
      .background(Color.Black)
  ) {
    AndroidView(modifier = Modifier.fillMaxWidth().height(68.dp), factory = { _ -> vm.watch.webView() })
    Row(
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp).background(Color(0xCC000000)),
      horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
      Text(if (playing) "⏸" else "▶", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable { if (playing) vm.watch.pause() else vm.watch.play() })
      Text("⛶", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable { onOpen() })
    }
  }
}

private fun fmtTime(sec: Double): String {
  val s = sec.toInt().coerceAtLeast(0)
  return String.format("%d:%02d", s / 60, s % 60)
}
