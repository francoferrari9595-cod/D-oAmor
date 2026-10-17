package com.example.ui.screens

import android.Manifest
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.EglBase
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CallStatus
import com.example.ui.CoupleViewModel
import com.example.ui.theme.HeartRed

@Composable
fun CallScreen(viewModel: CoupleViewModel) {
  val partner by viewModel.partnerUser.collectAsState()
  val activeUser by viewModel.activeUser.collectAsState()
  val callStatus by viewModel.callStatus.collectAsState()
  val isVideoCall by viewModel.isVideoCall.collectAsState()
  val durationSec by viewModel.callDurationSec.collectAsState()
  val isMuted by viewModel.isMuted.collectAsState()
  val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
  val floatingHearts by viewModel.floatingHearts.collectAsState()
  val localVideoTrack by viewModel.localVideoTrack.collectAsState()
  val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
  val eglContext = viewModel.videoEglContext()
  val acceptCallPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
    if (result[Manifest.permission.RECORD_AUDIO] == true && (!isVideoCall || result[Manifest.permission.CAMERA] == true)) {
      viewModel.acceptIncomingCall()
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "call_pulse")
  val ringScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.2f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "ring_scale"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF1E1B19),
            Color(0xFF2C2420),
            Color(0xFF141211)
          )
        )
      )
      .testTag("call_screen_overlay")
  ) {
    // Vídeo remoto a pantalla completa y vista previa local en una ventana flotante.
    if (isVideoCall && callStatus != CallStatus.IDLE && eglContext != null) {
      val currentRemoteVideoTrack = remoteVideoTrack
      if (currentRemoteVideoTrack != null) {
        WebRtcVideoSurface(track = currentRemoteVideoTrack, eglContext = eglContext, mirror = false,
          modifier = Modifier.fillMaxSize())
      }
      Box(
        modifier = Modifier
          .padding(top = 48.dp, end = 20.dp)
          .size(width = 110.dp, height = 150.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color(0xFF38322E))
          .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
          .align(Alignment.TopEnd),
        contentAlignment = Alignment.Center
      ) {
        val currentLocalVideoTrack = localVideoTrack
        if (currentLocalVideoTrack != null) {
          WebRtcVideoSurface(track = currentLocalVideoTrack, eglContext = eglContext, mirror = true, modifier = Modifier.fillMaxSize())
        } else {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = activeUser.avatar, fontSize = 38.sp)
            Text(text = "Tú", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 40.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top info
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 20.dp)
      ) {
        Text(
          text = if (isVideoCall) "Videollamada de Amor" else "Llamada de Amor",
          color = MaterialTheme.colorScheme.primary,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = partner.nickname.ifEmpty { partner.name },
          color = Color.White,
          fontSize = 28.sp,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = when (callStatus) {
            CallStatus.OUTGOING -> "Llamando… 💖"
            CallStatus.INCOMING -> "Te está llamando 💖"
            CallStatus.CONNECTING -> "Conectando audio y vídeo…"
            CallStatus.CONNECTED -> {
              val mins = durationSec / 60
              val secs = durationSec % 60
              String.format("%02d:%02d", mins, secs)
            }
            CallStatus.IDLE -> ""
          },
          color = Color.White.copy(alpha = 0.8f),
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold
        )
      }

      // Center Avatar
      if (!isVideoCall || remoteVideoTrack == null || callStatus == CallStatus.INCOMING || callStatus == CallStatus.OUTGOING) Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
      ) {
        if (callStatus == CallStatus.OUTGOING || callStatus == CallStatus.CONNECTING || callStatus == CallStatus.INCOMING) {
          Box(
            modifier = Modifier
              .size(190.dp)
              .scale(ringScale)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
          )
        }

        Box(
          modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Text(text = partner.avatar, fontSize = 64.sp)
        }
      }

      // Bottom Call Controls
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        // Floating Heart Reaction Trigger
        FilledIconButton(
          onClick = { viewModel.sendFloatingHeart() },
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = HeartRed.copy(alpha = 0.9f)
          ),
          modifier = Modifier
            .size(52.dp)
            .padding(bottom = 8.dp)
            .testTag("send_heart_during_call_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Enviar amor",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (callStatus == CallStatus.INCOMING) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            FilledIconButton(
              onClick = { viewModel.rejectIncomingCall() },
              colors = IconButtonDefaults.filledIconButtonColors(containerColor = HeartRed),
              modifier = Modifier.size(68.dp)
            ) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Rechazar llamada", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            FilledIconButton(
              onClick = { acceptCallPermissionLauncher.launch(if (isVideoCall) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)) },
              colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF2EAD62)),
              modifier = Modifier.size(68.dp)
            ) {
              Icon(imageVector = Icons.Default.Call, contentDescription = "Aceptar llamada", tint = Color.White, modifier = Modifier.size(30.dp))
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (isVideoCall) {
            FilledIconButton(
              onClick = { viewModel.switchCamera() },
              colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.2f)),
              modifier = Modifier.size(52.dp)
            ) {
              Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = "Cambiar cámara", tint = Color.White)
            }
          }
          // Mute Toggle
          FilledIconButton(
            onClick = { viewModel.toggleMute() },
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.size(56.dp)
          ) {
            Icon(
              imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
              contentDescription = "Silenciar",
              tint = if (isMuted) Color.Black else Color.White
            )
          }

          // End Call
          FilledIconButton(
            onClick = { viewModel.endCall() },
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = HeartRed
            ),
            modifier = Modifier
              .size(72.dp)
              .testTag("end_call_btn")
          ) {
            Icon(
              imageVector = Icons.Default.CallEnd,
              contentDescription = "Cortar llamada",
              tint = Color.White,
              modifier = Modifier.size(32.dp)
            )
          }

          // Speaker Toggle
          FilledIconButton(
            onClick = { viewModel.toggleSpeaker() },
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = if (isSpeakerOn) Color.White else Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.size(56.dp)
          ) {
            Icon(
              imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
              contentDescription = "Altavoz",
              tint = if (isSpeakerOn) Color.Black else Color.White
            )
          }
        }
      }
    }
  }
}


@Composable
private fun WebRtcVideoSurface(
  track: VideoTrack,
  eglContext: EglBase.Context,
  mirror: Boolean,
  modifier: Modifier = Modifier
) {
  var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
  AndroidView(
    modifier = modifier,
    factory = { context ->
      SurfaceViewRenderer(context).also { view ->
        view.init(eglContext, null)
        view.setEnableHardwareScaler(true)
        view.setMirror(mirror)
        renderer = view
      }
    },
    update = { view ->
      if (renderer !== view) renderer = view
    }
  )
  DisposableEffect(renderer, track) {
    val target = renderer
    if (target != null) track.addSink(target)
    onDispose { if (target != null) runCatching { track.removeSink(target) } }
  }
  DisposableEffect(renderer) {
    val target = renderer
    onDispose { if (target != null) { runCatching { target.release() }; if (renderer === target) renderer = null } }
  }
}
