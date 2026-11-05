package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CoupleViewModel
import com.example.ui.theme.GoldYellow

/** Botón walkie-talkie: mantén presionado, habla y al soltar tu pareja te escucha. */
@Composable
fun WalkieButton(vm: CoupleViewModel, size: Dp = 44.dp) {
  val ctx = LocalContext.current
  val talking by vm.walkieTalking.collectAsState()
  val incomingPlay by vm.walkieIncoming.collectAsState()
  val partnerBusy by vm.partnerTalking.collectAsState()
  val incoming = incomingPlay || partnerBusy
  val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  Box(
    modifier = Modifier
      .size(size)
      .clip(CircleShape)
      .background(
        when {
          talking -> Brush.radialGradient(listOf(Color(0xFFFF6B6B), Color(0xFF9A0F0F)))
          incoming -> Brush.radialGradient(listOf(Color(0xFF3DDC84), Color(0xFF0B6B3A)))
          else -> Brush.radialGradient(listOf(Color(0xFF4A4A58), Color(0xFF1A1A22)))
        }
      )
      .border(BorderStroke(2.dp, if (talking) Color(0xFFFF9A9A) else GoldYellow), CircleShape)
      .pointerInput(Unit) {
        detectTapGestures(onPress = {
          val ok = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
          if (!ok) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
          } else {
            vm.startWalkie()
            tryAwaitRelease()
            vm.stopWalkie()
          }
        })
      },
    contentAlignment = Alignment.Center
  ) {
    Text(if (incoming) "🔊" else "🎙️", fontSize = (size.value * 0.46f).sp)
  }
}
