package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.screens.BuzzerScreen
import com.example.ui.screens.WatchPartyScreen
import com.example.ui.screens.GiftOverlay
import com.example.ui.components.AvatarView
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CoupleCalendarScreen
import com.example.ui.screens.MinigamesScreen
import com.example.ui.screens.ScreensaverScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.HeartRed
import com.example.ui.theme.LocalDuoPalette

enum class NavigationTab(val label: String, val activeIcon: androidx.compose.ui.graphics.vector.ImageVector, val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector) {
  CHAT("Chat", Icons.Filled.Chat, Icons.Outlined.Chat),
  BUZZER("Zumbador", Icons.Filled.NotificationsActive, Icons.Outlined.NotificationsActive),
  CINE("Cine", Icons.Filled.Movie, Icons.Outlined.Movie),
  CALENDAR("Historia", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
  GAMES("Juegos", Icons.Filled.Casino, Icons.Outlined.Casino)
}

@Composable
fun MainCoupleScaffold(viewModel: CoupleViewModel) {
  var currentTab by remember { mutableStateOf(NavigationTab.CHAT) }
  var showSettings by remember { mutableStateOf(false) }
  var callMinimized by remember { mutableStateOf(false) }

  val activeUser by viewModel.activeUser.collectAsState()
  val partnerUser by viewModel.partnerUser.collectAsState()
  val isIncomingBuzz by viewModel.isIncomingBuzzActive.collectAsState()
  val incomingBuzzDurationMs by viewModel.incomingBuzzDurationMs.collectAsState()
  val callStatus by viewModel.callStatus.collectAsState()
  val buzzAsked by viewModel.buzzAsked.collectAsState()
  val myPhoto by viewModel.myPhoto.collectAsState()
  val partnerPhoto by viewModel.partnerPhoto.collectAsState()
  val partnerShown by viewModel.partnerDisplayName.collectAsState()
  val syncIssue by viewModel.syncIssue.collectAsState()
  val swapPhotos by viewModel.swapAvatars.collectAsState()
  val palette = LocalDuoPalette.current
  val context = LocalContext.current

  val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
    viewModel.setBuzzAllowed(true)
    // Pide excluir la app del ahorro de batería para que el zumbido llegue con la app cerrada.
    runCatching {
      context.startActivity(
        Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }
  }

  AuroraBackground {
    Box(modifier = Modifier.fillMaxSize()) {
      Scaffold(
        containerColor = Color.Transparent,
        topBar = {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .statusBarsPadding()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AvatarView(if (swapPhotos) partnerPhoto else myPhoto, activeUser.avatar, 42.dp, Modifier.testTag("profile_chip"))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              com.example.ui.components.SiniJossTitle(20.sp)
              Text(
                "con $partnerShown 💞",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            AvatarView(if (swapPhotos) myPhoto else partnerPhoto, partnerUser.avatar, 36.dp)
            Spacer(Modifier.width(8.dp))
            com.example.ui.screens.WalkieButton(viewModel, 40.dp)
            Spacer(Modifier.width(8.dp))
            IconButton(
              onClick = { showSettings = !showSettings },
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x0FFFFFFF))))
                .border(BorderStroke(1.dp, GlassBorder), CircleShape)
                .testTag("open_settings_btn")
            ) {
              Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color.White)
            }
          }
        },
        bottomBar = {
          FloatingNavBar(
            current = currentTab,
            onSelect = { currentTab = it; showSettings = false }
          )
        }
      ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          if (showSettings) {
            SettingsScreen(viewModel)
          } else {
            if (currentTab == NavigationTab.CINE) {
              WatchPartyScreen(viewModel)
            } else {
              val enter = remember(currentTab) { androidx.compose.animation.core.Animatable(0f) }
              val tabOrder = NavigationTab.values().indexOf(currentTab)
              val prevOrder = remember { mutableStateOf(tabOrder) }
              val dir = if (tabOrder >= prevOrder.value) 1f else -1f
              androidx.compose.runtime.LaunchedEffect(currentTab, showSettings) {
                enter.snapTo(0f)
                enter.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 260f))
                prevOrder.value = tabOrder
              }
              Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = enter.value.coerceIn(0f, 1f)
                translationX = (1f - enter.value) * 90f * dir
                scaleX = 0.96f + 0.04f * enter.value
                scaleY = 0.96f + 0.04f * enter.value
              }) {
              val tab = currentTab
            when (tab) {
              NavigationTab.CHAT -> ChatScreen(
                viewModel = viewModel,
                onNavigateToBuzzer = { currentTab = NavigationTab.BUZZER },
                onNavigateToScreensaver = { }
              )
              NavigationTab.BUZZER -> BuzzerScreen(viewModel)
              NavigationTab.CINE -> WatchPartyScreen(viewModel)
              NavigationTab.CALENDAR -> CoupleCalendarScreen(viewModel)
              NavigationTab.GAMES -> MinigamesScreen(viewModel)
            }
              }
            }
          }
        }
      }

      // Permiso del zumbador (se pregunta una sola vez; se puede cambiar en Ajustes y en la pestaña Zumbador).
      if (!buzzAsked) {
        BuzzPermissionDialog(
          partnerName = partnerShown,
          onAllow = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
              viewModel.setBuzzAllowed(true)
            }
          },
          onLater = { viewModel.dismissBuzzPrompt() }
        )
      }

      AnimatedVisibility(visible = isIncomingBuzz, enter = fadeIn(), exit = fadeOut()) {
        IncomingBuzzFullscreenAlert(
          senderName = partnerShown,
          durationMs = incomingBuzzDurationMs,
          onStop = { viewModel.stopIncomingBuzz() },
          onOpenChat = {
            viewModel.stopIncomingBuzz()
            currentTab = NavigationTab.CHAT
          }
        )
      }

      if (callStatus != CallStatus.IDLE && !callMinimized) {
        CallScreen(viewModel = viewModel, onMinimize = { callMinimized = true })
      }
      LaunchedEffect(callStatus) { if (callStatus == CallStatus.IDLE) callMinimized = false }
      if (callStatus != CallStatus.IDLE && callMinimized) {
        Row(
          modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xEE21191B))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF65E6A3))
          Column(modifier = Modifier.weight(1f).clickable { callMinimized = false }) {
            Text(if (callStatus == CallStatus.CONNECTED) "Llamada activa" else "Llamada en curso", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Toca para volver a la llamada", color = Color(0xFFD7C9CC), style = MaterialTheme.typography.labelSmall)
          }
          IconButton(onClick = { callMinimized = false }) {
            Icon(Icons.Default.StayCurrentPortrait, contentDescription = "Volver a la llamada", tint = Color.White)
          }
          IconButton(onClick = { viewModel.endCall(); callMinimized = false }) {
            Icon(Icons.Default.CallEnd, contentDescription = "Finalizar llamada", tint = HeartRed)
          }
        }
      }

      // Tono de llamada elegido mientras suena una llamada entrante dentro de la app.
      DisposableEffect(callStatus) {
        var ringtone: android.media.Ringtone? = null
        if (callStatus == CallStatus.INCOMING && com.example.data.SoundPrefs.enabled(context, com.example.data.SoundPrefs.CALL)) {
          val uri = com.example.data.SoundPrefs.resolve(context, com.example.data.SoundPrefs.CALL)
          if (uri != null) {
            ringtone = runCatching { android.media.RingtoneManager.getRingtone(context, uri) }.getOrNull()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.isLooping = true
            runCatching { ringtone?.play() }
          }
        }
        onDispose { runCatching { ringtone?.stop() } }
      }

      GiftOverlay(viewModel)

      if (currentTab != NavigationTab.CINE) {
        com.example.ui.screens.WatchMiniPlayer(
          vm = viewModel,
          onOpen = { currentTab = NavigationTab.CINE; showSettings = false },
          modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 92.dp, end = 10.dp)
        )
      }

      if (syncIssue != null) {
        Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 64.dp, start = 12.dp, end = 12.dp)) {
          GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true, corner = 16.dp, onClick = { viewModel.clearSyncIssue() }) {
            Text("⚠️ ${syncIssue}  (toca para cerrar)", color = Color(0xFFFFD0D8), fontSize = 12.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun FloatingNavBar(current: NavigationTab, onSelect: (NavigationTab) -> Unit) {
  val palette = LocalDuoPalette.current
  val shape = RoundedCornerShape(32.dp)
  val tabs = NavigationTab.values()
  val index = tabs.indexOf(current)
  Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .height(66.dp)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xE61C1030), Color(0xF00E0719))))
        .border(BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0x55FFFFFF), Color(0x14FFFFFF), Color(0x33FFFFFF)))), shape)
        .padding(5.dp)
    ) {
      val slot: Dp = maxWidth / tabs.size
      // La píldora de selección se desliza con un resorte entre pestañas.
      val x by animateDpAsState(slot * index, spring(dampingRatio = 0.72f, stiffness = 380f), label = "navPill")
      Box(
        modifier = Modifier
          .offset(x = x)
          .width(slot)
          .fillMaxHeight()
          .clip(RoundedCornerShape(26.dp))
          .background(palette.accentDiagonal)
          .border(BorderStroke(1.dp, Color(0x55FFFFFF)), RoundedCornerShape(26.dp))
      )
      Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { tab ->
          val selected = tab == current
          val lift by animateFloatAsState(if (selected) -2f else 0f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "navLift")
          val grow by animateFloatAsState(if (selected) 1.14f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "navGrow")
          val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(26.dp))
              .clickable(interactionSource = src, indication = null) { onSelect(tab) }
              .testTag("nav_tab_${tab.name.lowercase()}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = if (selected) tab.activeIcon else tab.inactiveIcon,
              contentDescription = tab.label,
              tint = if (selected) Color.White else Color(0xFFB8A9D4),
              modifier = Modifier.size(22.dp).graphicsLayer { translationY = lift; scaleX = grow; scaleY = grow }
            )
            Text(
              tab.label,
              fontSize = 10.sp,
              fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
              color = if (selected) Color.White else Color(0xFFB8A9D4)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BuzzPermissionDialog(partnerName: String, onAllow: () -> Unit, onLater: () -> Unit) {
  Box(
    modifier = Modifier.fillMaxSize().background(Color(0xCC05020C)).clickable(enabled = false) {},
    contentAlignment = Alignment.Center
  ) {
    GlassCard(modifier = Modifier.padding(24.dp), highlight = true, corner = 28.dp) {
      Text("🔔", fontSize = 44.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
      Spacer(Modifier.height(8.dp))
      Text(
        "¿Permitir que $partnerName te haga sonar?",
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
        color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
      )
      Spacer(Modifier.height(10.dp))
      Text(
        "Con el zumbador, tu pareja podrá hacer sonar y vibrar tu teléfono mientras mantenga presionado el botón, aunque la app esté en segundo plano. Solo funciona si tú lo permites y puedes quitarlo cuando quieras en la pestaña Zumbador.",
        style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE2D8F5), textAlign = TextAlign.Center
      )
      Spacer(Modifier.height(18.dp))
      GradientButton("Sí, permitir", onClick = onAllow, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(10.dp))
      SoftButton("Ahora no", onClick = onLater, modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
fun IncomingBuzzFullscreenAlert(
  senderName: String,
  durationMs: Long,
  onStop: () -> Unit,
  onOpenChat: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "buzz_alert")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.92f,
    targetValue = 1.22f,
    animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "alert_scale"
  )
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(listOf(Color(0xFF7A0B3A), Color(0xFFB0124F), Color(0xFF2B0518))))
      .padding(24.dp)
      .testTag("incoming_buzz_alert_overlay"),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Box(
        modifier = Modifier.size(170.dp).scale(pulseScale).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
      ) {
        Box(modifier = Modifier.size(116.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
          Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = HeartRed, modifier = Modifier.size(58.dp))
        }
      }
      Spacer(Modifier.height(28.dp))
      Text("¡$senderName te está haciendo sonar!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
      Spacer(Modifier.height(10.dp))
      Text(
        "Tu teléfono seguirá sonando hasta que suelte el botón.",
        color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp
      )
      Spacer(Modifier.height(14.dp))
      Text("${String.format("%.1f", durationMs / 1000.0)} s", color = Color(0xFFFFD54F), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
      Spacer(Modifier.height(32.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SoftButton("Silenciar", onClick = onStop, modifier = Modifier.weight(1f))
        GradientButton("Responder 💬", onClick = onOpenChat, modifier = Modifier.weight(1f))
      }
    }
  }
}
