package com.example.ui.screens

import android.Manifest

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.example.ui.components.AvatarView
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CoupleMessage
import com.example.model.MessageType
import com.example.ui.CoupleViewModel
import com.example.ui.theme.BlueCheck
import com.example.ui.theme.BuzzAmber
import com.example.ui.theme.HeartRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
  viewModel: CoupleViewModel,
  onNavigateToBuzzer: () -> Unit,
  onNavigateToScreensaver: () -> Unit
) {
  val messages by viewModel.messages.collectAsState()
  val partner by viewModel.partnerUser.collectAsState()
  val activeUser by viewModel.activeUser.collectAsState()
  val currentUserId by viewModel.currentUserId.collectAsState()
  val isRecording by viewModel.isRecordingVoice.collectAsState()
  val recordingDuration by viewModel.voiceRecordingDurationSec.collectAsState()
  val playingAudioId by viewModel.playingAudioMessageId.collectAsState()
  val partnerPhoto by viewModel.partnerPhoto.collectAsState()
  val partnerBattery by viewModel.partnerBattery.collectAsState()
  val partnerDisplayName by viewModel.partnerDisplayName.collectAsState()
  val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
    if (r.values.any { it }) viewModel.sendLocation()
  }

  var inputText by remember { mutableStateOf("") }
  var partnerPhotoExpanded by remember { mutableStateOf(false) }
  var giftSheetOpen by remember { mutableStateOf(false) }
  var pendingVideoCall by remember { mutableStateOf(false) }
  val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
    val audioGranted = result[Manifest.permission.RECORD_AUDIO] == true
    val cameraGranted = !pendingVideoCall || result[Manifest.permission.CAMERA] == true
    if (audioGranted && cameraGranted) viewModel.startCall(isVideo = pendingVideoCall)
  }
  fun requestCallPermissions(video: Boolean) {
    pendingVideoCall = video
    callPermissionLauncher.launch(if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO))
  }
  val micContext = androidx.compose.ui.platform.LocalContext.current
  val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
  val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
    if (ok) pendingCameraUri?.let { viewModel.sendPhoto(it) }
  }
  fun launchCamera() {
    val dir = java.io.File(micContext.cacheDir, "camera").apply { mkdirs() }
    val file = java.io.File(dir, "foto_" + System.currentTimeMillis() + ".jpg")
    val uri = androidx.core.content.FileProvider.getUriForFile(micContext, micContext.packageName + ".fileprovider", file)
    pendingCameraUri = uri
    takePicture.launch(uri)
  }
  val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) launchCamera()
  }
  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) viewModel.sendPhoto(uri)
  }
  var showAttachmentSheet by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()

  // Auto-scroll on new message
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    // WhatsApp-like Header
    TopAppBar(
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
        ) {
          Box(modifier = Modifier.clickable { giftSheetOpen = true }) {
            AvatarView(partnerPhoto, partner.avatar, 44.dp)
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = partnerDisplayName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(if (partner.isOnline) Color(0xFF4CAF50) else Color.Gray)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "🔋 " + (if (partnerBattery >= 0) "$partnerBattery%" else "—") + " (batería real de " + partnerDisplayName + ")",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      },
      actions = {
        IconButton(
          onClick = { requestCallPermissions(false) },
          modifier = Modifier.testTag("voice_call_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Llamada de voz",
            tint = MaterialTheme.colorScheme.primary
          )
        }
        IconButton(
          onClick = { requestCallPermissions(true) },
          modifier = Modifier.testTag("video_call_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = "Videollamada",
            tint = MaterialTheme.colorScheme.primary
          )
        }
      },
      colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface
      )
    )

    // Message List
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 12.dp),
      contentPadding = PaddingValues(vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(messages, key = { it.id }) { message ->
        val isMe = message.senderId == currentUserId || message.senderId == viewModel.firestoreRepo.currentUserId()
        MessageBubble(
          message = message,
          isMe = isMe,
          isPlaying = playingAudioId == message.id,
          onToggleAudio = {
            viewModel.toggleAudioPlayback(message.id, message.audioDurationSec, message.mediaUrl, message.mediaData)
          },
          onOpenScreensaver = onNavigateToScreensaver
        )
      }
    }

    // Attachment Row Menu if opened
    AnimatedVisibility(
      visible = showAttachmentSheet,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      AttachmentQuickBar(
        onSendLocation = {
          val hasLoc = androidx.core.content.ContextCompat.checkSelfPermission(micContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(micContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
          if (hasLoc) viewModel.sendLocation()
          else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
          showAttachmentSheet = false
        },
        onSendPhoto = {
          photoPicker.launch("image/*")
          showAttachmentSheet = false
        },
        onTakePhoto = {
          val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(micContext, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
          if (hasCam) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
          showAttachmentSheet = false
        },
        onSendSticker = { emoji, text ->
          viewModel.sendSticker(emoji, text)
          showAttachmentSheet = false
        },
        onSendScreensaverNote = {
          onNavigateToScreensaver()
          showAttachmentSheet = false
        },
        onGoToBuzzer = {
          onNavigateToBuzzer()
          showAttachmentSheet = false
        }
      )
    }

    // Recording Banner
    AnimatedVisibility(visible = isRecording) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Mic,
              contentDescription = "Grabando",
              tint = HeartRed
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Grabando audio: ${String.format("%02d:%02d", recordingDuration / 60, recordingDuration % 60)}",
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }

          Text(
            text = "Soltar para enviar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
    }

    // Bottom Input Bar
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 3.dp,
      modifier = Modifier
        .fillMaxWidth()
        .imePadding()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Toggle Attachments
        IconButton(
          onClick = { showAttachmentSheet = !showAttachmentSheet },
          modifier = Modifier.testTag("attachment_btn")
        ) {
          Icon(
            imageVector = if (showAttachmentSheet) Icons.Default.Close else Icons.Outlined.EmojiEmotions,
            contentDescription = "Adjuntar opciones románticas",
            tint = MaterialTheme.colorScheme.primary
          )
        }

        // Text Field
        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = { Text("Escribe un mensaje de amor...", fontSize = 14.sp) },
          modifier = Modifier
            .weight(1f)
            .testTag("chat_input_field"),
          shape = RoundedCornerShape(24.dp),
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            focusedBorderColor = MaterialTheme.colorScheme.primary
          ),
          maxLines = 4
        )

        Spacer(modifier = Modifier.width(6.dp))

        if (inputText.isNotBlank()) {
          FilledIconButton(
            onClick = {
              viewModel.sendTextMessage(inputText)
              inputText = ""
            },
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
              .size(46.dp)
              .testTag("send_message_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Send,
              contentDescription = "Enviar",
              tint = Color.White
            )
          }
        } else {
          // Voice Note Hold Button (Press and hold to record)
          FilledIconButton(
            onClick = {},
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = if (isRecording) HeartRed else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
              .size(46.dp)
              .testTag("voice_record_btn")
              .pointerInput(Unit) {
                detectTapGestures(
                  onPress = {
                    val micOk = androidx.core.content.ContextCompat.checkSelfPermission(
                      micContext, Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!micOk) {
                      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                      viewModel.startVoiceRecording()
                      tryAwaitRelease()
                      viewModel.finishVoiceRecording()
                    }
                  }
                )
              }
          ) {
            Icon(
              imageVector = Icons.Default.Mic,
              contentDescription = "Mantén presionado para nota de voz",
              tint = Color.White
            )
          }
        }
      }
    }
  }
  if (giftSheetOpen) {
    val hasPhoto = !partnerPhoto.isNullOrBlank() || partner.avatar.startsWith("http")
    GiftToPersonDialog(viewModel, partnerDisplayName, if (hasPhoto) ({ partnerPhotoExpanded = true }) else null) { giftSheetOpen = false }
  }
  if (partnerPhotoExpanded) {
    Dialog(onDismissRequest = { partnerPhotoExpanded = false }) {
      Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).clickable { partnerPhotoExpanded = false },
        contentAlignment = Alignment.Center
      ) {
        AsyncImage(
          model = partnerPhoto?.takeIf { it.isNotBlank() } ?: partner.avatar.takeIf { it.startsWith("http") },
          contentDescription = "Foto de perfil ampliada. Toca para cerrar",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize()
        )
        IconButton(
          onClick = { partnerPhotoExpanded = false },
          modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape)
        ) { Icon(Icons.Default.Close, contentDescription = "Cerrar foto de perfil", tint = Color.White) }
      }
    }
  }
}

@Composable
fun MessageBubble(
  message: CoupleMessage,
  isMe: Boolean,
  isPlaying: Boolean,
  onToggleAudio: () -> Unit,
  onOpenScreensaver: () -> Unit
) {
  val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
  val timeString = timeFormat.format(Date(message.timestamp))

  val bubbleColor = if (isMe) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
  } else {
    if (com.example.ui.theme.LocalDuoPalette.current.isLight) Color(0xFF2A1646) else Color.White.copy(alpha = 0.10f)
  }

  val textColor = Color.White
  var photoExpanded by remember(message.id) { mutableStateOf(false) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
  ) {
    Card(
      shape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomStart = if (isMe) 22.dp else 6.dp,
        bottomEnd = if (isMe) 6.dp else 22.dp
      ),
      colors = CardDefaults.cardColors(containerColor = bubbleColor),
      modifier = Modifier.widthIn(max = 300.dp)
    ) {
      Column(modifier = Modifier.padding(10.dp)) {
        when (message.type) {
          MessageType.TEXT -> {
            Text(
              text = message.content,
              style = MaterialTheme.typography.bodyMedium,
              color = textColor
            )
          }
          MessageType.AUDIO -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(vertical = 4.dp)
            ) {
              FilledIconButton(
                onClick = onToggleAudio,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.primary
                )
              ) {
                Icon(
                  imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                  contentDescription = "Reproducir audio",
                  tint = Color.White
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                  progress = { if (isPlaying) 0.6f else 0.0f },
                  modifier = Modifier.fillMaxWidth().height(4.dp),
                  color = MaterialTheme.colorScheme.primary,
                  trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Nota de voz (${message.audioDurationSec}s)",
                  fontSize = 12.sp,
                  color = textColor
                )
              }
            }
          }
          MessageType.BUZZ -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(BuzzAmber),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.NotificationsActive,
                  contentDescription = "Zumbido",
                  tint = Color.White,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = message.content,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = textColor
              )
            }
          }
          MessageType.LOCATION -> {
            val locCtx = androidx.compose.ui.platform.LocalContext.current
            Column(
              modifier = Modifier.clickable {
                val la = message.latitude
                val lo = message.longitude
                if (la != null && lo != null) {
                  runCatching {
                    locCtx.startActivity(
                      android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:$la,$lo?q=$la,$lo"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                  }
                }
              }
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.LocationOn,
                  contentDescription = "Ubicación",
                  tint = HeartRed
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = message.locationName ?: "Ubicación compartida",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = textColor
                )
              }
              Text(
                text = "📍 ${String.format("%.5f", message.latitude ?: 0.0)}, ${String.format("%.5f", message.longitude ?: 0.0)}",
                fontSize = 11.sp,
                color = textColor.copy(alpha = 0.8f)
              )
            }
          }
          MessageType.PHOTO -> {
            Column {
              if (!message.mediaUrl.isNullOrBlank() || !message.mediaData.isNullOrBlank()) {
                val photoModel: Any? = remember(message.id, message.mediaUrl, message.mediaData) {
                  if (!message.mediaData.isNullOrBlank()) android.util.Base64.decode(message.mediaData, android.util.Base64.DEFAULT) else message.mediaUrl
                }
                AsyncImage(
                  model = photoModel,
                  contentDescription = "Foto recibida",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
                    .clickable { photoExpanded = true }
                )
              } else {
                Box(
                  modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                  contentAlignment = Alignment.Center
                ) { Text("La foto todavía no está disponible", color = textColor, fontSize = 12.sp) }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(text = message.content, fontSize = 12.sp, color = textColor)
            }
          }
          MessageType.SCREENSAVER_NOTE -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable { onOpenScreensaver() }
            ) {
              Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = "Salvapantallas",
                tint = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = textColor
              )
            }
          }
          MessageType.STICKER -> {
            Text(text = message.content, fontSize = 28.sp)
          }
          MessageType.CALL_LOG -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Llamada",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = message.content,
                fontSize = 12.sp,
                color = textColor
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Time and Double Checks
        Row(
          modifier = Modifier.align(Alignment.End),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = timeString,
            fontSize = 10.sp,
            color = textColor.copy(alpha = 0.7f)
          )
          if (isMe) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Outlined.DoneAll,
              contentDescription = "Leído",
              tint = BlueCheck,
              modifier = Modifier.size(14.dp)
            )
          }
        }
      }
    }
  }

  if (photoExpanded && message.type == MessageType.PHOTO) {
    val expandedPhotoModel: Any? = remember(message.id, message.mediaUrl, message.mediaData) {
      if (!message.mediaData.isNullOrBlank()) android.util.Base64.decode(message.mediaData, android.util.Base64.DEFAULT) else message.mediaUrl
    }
    Dialog(onDismissRequest = { photoExpanded = false }) {
      Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).clickable { photoExpanded = false },
        contentAlignment = Alignment.Center
      ) {
        AsyncImage(
          model = expandedPhotoModel,
          contentDescription = "Fotografía ampliada. Toca para cerrar",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize()
        )
        IconButton(
          onClick = { photoExpanded = false },
          modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape)
        ) { Icon(Icons.Default.Close, contentDescription = "Cerrar fotografía", tint = Color.White) }
      }
    }
  }
}

@Composable
fun AttachmentQuickBar(
  onSendLocation: () -> Unit,
  onSendPhoto: () -> Unit,
  onTakePhoto: () -> Unit,
  onSendSticker: (String, String) -> Unit,
  onSendScreensaverNote: () -> Unit,
  onGoToBuzzer: () -> Unit
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = "Acciones rápidas para parejas 💕",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        QuickActionItem(
          icon = Icons.Default.LocationOn,
          label = "Ubicación",
          color = Color(0xFFE53935),
          onClick = onSendLocation
        )
        QuickActionItem(
          icon = Icons.Default.PhotoCamera,
          label = "Galería",
          color = Color(0xFF1E88E5),
          onClick = onSendPhoto
        )
        QuickActionItem(
          icon = Icons.Default.PhotoCamera,
          label = "Cámara",
          color = Color(0xFF00ACC1),
          onClick = onTakePhoto
        )
        QuickActionItem(
          icon = Icons.Default.NotificationsActive,
          label = "Zumbador",
          color = BuzzAmber,
          onClick = onGoToBuzzer
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Quick love stickers
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        listOf(
          "💋" to "Beso apasionado",
          "🫂" to "Abrazo fuerte",
          "❤️" to "Te amo",
          "🥺" to "Te extraño",
          "☕" to "Pensando en ti"
        ).forEach { (emoji, text) ->
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surface)
              .clickable { onSendSticker(emoji, text) },
            contentAlignment = Alignment.Center
          ) {
            Text(text = emoji, fontSize = 20.sp)
          }
        }
      }
    }
  }
}

@Composable
fun QuickActionItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  color: Color,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clickable(onClick = onClick)
      .padding(4.dp)
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(color.copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = icon, contentDescription = label, tint = color)
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}
