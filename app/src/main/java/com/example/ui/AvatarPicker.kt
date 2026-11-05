package com.example.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Decodifica una foto de perfil en base64 (JPEG chico) una sola vez; null si no hay o está dañada. */
@Composable
fun rememberAvatarBitmap(b64: String?): ImageBitmap? = remember(b64) {
  if (b64.isNullOrBlank()) null
  else runCatching {
    val bytes = Base64.decode(b64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
  }.getOrNull()
}

/** Círculo con la foto de perfil o, si no hay, la inicial del nombre. */
@Composable
fun AvatarCircle(photo: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
  val bmp = rememberAvatarBitmap(photo)
  Box(
    modifier.size(size).clip(CircleShape)
      .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF8B5CF6))))
      .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
    contentAlignment = Alignment.Center
  ) {
    if (bmp != null) Image(bitmap = bmp, contentDescription = "Foto de perfil", modifier = Modifier.size(size), contentScale = ContentScale.Crop)
    else Text(name.trim().take(1).uppercase().ifEmpty { "📷" }, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp)
  }
}

/** Selector de foto de perfil: tocá el círculo para elegir una imagen de la galería. */
@Composable
fun AvatarPicker(vm: CoupleViewModel, name: String, size: Dp = 96.dp) {
  val photo by vm.myAvatar.collectAsState()
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.setMyAvatar(uri) }
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    AvatarCircle(photo, name, size, Modifier.clickable { picker.launch("image/*") })
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(if (photo == null) "📷 Elegir foto" else "📷 Cambiar foto", color = Color(0xFF7DD3FC), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { picker.launch("image/*") })
      if (photo != null) Text("Quitar", color = Color(0xCCFFFFFF), fontSize = 12.sp, modifier = Modifier.clickable { vm.setMyAvatar(null) })
    }
  }
}
