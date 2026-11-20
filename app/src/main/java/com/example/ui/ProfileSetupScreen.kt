package com.example.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton

/**
 * Primer ingreso: pide el nombre de usuario (obligatorio) y una foto de perfil (opcional).
 * `onDone` recibe el nombre ya recortado y la foto elegida, o null si se omitió.
 */
@Composable
fun ProfileSetupScreen(initialName: String, onDone: (name: String, photo: Uri?) -> Unit) {
  val ctx = LocalContext.current
  var name by remember { mutableStateOf(initialName.trim().take(24)) }
  var photo by remember { mutableStateOf<Uri?>(null) }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) photo = uri }
  val appear = remember { Animatable(0f) }
  LaunchedEffect(Unit) { appear.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
  // Vista previa liviana de la foto elegida (se reduce al decodificar).
  val preview = remember(photo) {
    photo?.let { uri ->
      runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
      }.getOrNull()
    }
  }
  val canContinue = name.trim().isNotEmpty()

  AuroraBackground {
    Column(
      Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp)
        .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 40f },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Spacer(Modifier.height(24.dp))
      Text("¡Bienvenido!", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, textAlign = TextAlign.Center)
      Spacer(Modifier.height(6.dp))
      Text("Contanos cómo querés que te vea tu pareja.", color = Color(0xCCFFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center)
      Spacer(Modifier.height(22.dp))
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          Box(
            Modifier.size(112.dp).clip(CircleShape)
              .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF8B5CF6))))
              .border(3.dp, Color.White.copy(alpha = 0.8f), CircleShape)
              .clickable { picker.launch("image/*") },
            contentAlignment = Alignment.Center
          ) {
            if (preview != null) {
              Image(bitmap = preview, contentDescription = "Tu foto", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
              Text(name.trim().take(1).uppercase().ifEmpty { "📷" }, color = Color.White, fontWeight = FontWeight.Black, fontSize = 40.sp)
            }
          }
          Spacer(Modifier.height(10.dp))
          SoftButton(if (photo == null) "📷 Agregar foto (opcional)" else "Cambiar foto", onClick = { picker.launch("image/*") })
          if (photo != null) {
            Spacer(Modifier.height(6.dp))
            Text("Quitar foto", color = Color(0xCCFFFFFF), fontSize = 12.sp, modifier = Modifier.clickable { photo = null }.padding(6.dp))
          }
          Spacer(Modifier.height(14.dp))
          OutlinedTextField(
            value = name, onValueChange = { name = it.take(24) },
            label = { Text("Tu nombre de usuario") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White, unfocusedTextColor = Color.White,
              focusedBorderColor = Color(0xFF7DD3FC), unfocusedBorderColor = Color(0x66FFFFFF),
              focusedLabelColor = Color(0xFF7DD3FC), unfocusedLabelColor = Color(0xCCFFFFFF), cursorColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
      Spacer(Modifier.height(18.dp))
      GradientButton("Continuar", onClick = { onDone(name.trim().take(24), photo) }, enabled = canContinue, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(10.dp))
      Text("Después podés cambiarlos cuando quieras desde Ajustes.", color = Color(0x99FFFFFF), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
  }
}
