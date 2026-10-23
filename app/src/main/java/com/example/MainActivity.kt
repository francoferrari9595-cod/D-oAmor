package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CoupleViewModel
import com.example.ui.MainCoupleScaffold
import com.example.ui.PairingGate
import com.example.ui.theme.DuoAmorTheme

class MainActivity : ComponentActivity() {
  override fun onStart() { super.onStart(); com.example.data.AppVisibility.foreground = true }
  override fun onStop() { super.onStop(); com.example.data.AppVisibility.foreground = false }


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val viewModel: CoupleViewModel = viewModel()
      val appTheme by viewModel.appTheme.collectAsState()

      // Runtime permission launcher for notifications and audio
      val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
      ) { /* Result handled gracefully */ }

      LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
          }
        }
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
          permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
          permissions.add(Manifest.permission.CAMERA)
        }
        if (permissions.isNotEmpty()) {
          permissionLauncher.launch(permissions.toTypedArray())
        }
      }

      DuoAmorTheme(appTheme = appTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
          var welcomed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
          var guestJoin by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
          val guestRoom by viewModel.guestRoomId.collectAsState()
          if (guestRoom.isNotBlank()) {
            com.example.ui.GuestScaffold(viewModel)
          } else if (!welcomed && !guestJoin) {
            com.example.ui.WelcomeScreen(onContinue = { welcomed = true }, onGuest = { guestJoin = true })
          } else if (guestJoin) {
            com.example.ui.GuestJoinScreen(viewModel, onBack = { guestJoin = false })
          } else {
            PairingGate(onPaired = { coupleId -> viewModel.repository.setPairingCode(coupleId) }) { MainCoupleScaffold(viewModel = viewModel) }
          }
        }
      }
    }
  }
}
