package com.example.ui.buzzer

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado de la comunicación de la pareja
 */
enum class ConnectionState {
  DISCONNECTED,
  CONNECTING,
  CONNECTED
}

/**
 * Patrones de vibración disponibles para el zumbador
 */
enum class VibrationPattern(val label: String, val patternArray: LongArray) {
  CONTINUOUS("Vibración Continua", longArrayOf(0, 1000, 50, 1000)),
  INTENSE_PULSE("Pulso Rápido Intenso", longArrayOf(0, 150, 50, 150, 50)),
  HEARTBEAT("Latido de Pareja", longArrayOf(0, 120, 80, 140, 500)),
  ROMANTIC_CHIME("Alerta Rítmica", longArrayOf(0, 250, 100, 250, 100))
}

/**
 * Registro de un evento de zumbido completado
 */
data class BuzzRecord(
  val id: String,
  val senderName: String,
  val durationMs: Long,
  val timestamp: Long = System.currentTimeMillis()
)

/**
 * Estado inmutable de la UI para la pantalla de zumbido y comunicación
 */
data class BuzzerUiState(
  val connectionState: ConnectionState = ConnectionState.CONNECTED,
  val partnerName: String = "Mi Amor",
  val isPartnerOnline: Boolean = true,
  // Estado cuando el usuario local mantiene presionado el botón
  val isSendingBuzz: Boolean = false,
  val sendBuzzDurationMs: Long = 0L,
  // Estado cuando la pareja está haciendo sonar este dispositivo
  val isReceivingBuzz: Boolean = false,
  val receiveBuzzDurationMs: Long = 0L,
  // Configuraciones
  val isVibrationEnabled: Boolean = true,
  val selectedPattern: VibrationPattern = VibrationPattern.CONTINUOUS,
  // Historial reciente
  val lastBuzzSent: BuzzRecord? = null,
  val lastBuzzReceived: BuzzRecord? = null
)

/**
 * Eventos puntuales (One-shot side-effects)
 */
sealed interface BuzzerEvent {
  data class BuzzSentSuccessfully(val durationMs: Long) : BuzzerEvent
  data class IncomingBuzzAlert(val senderName: String) : BuzzerEvent
  data class Error(val message: String) : BuzzerEvent
}

/**
 * ViewModel base y profesional para gestionar la comunicación en tiempo real
 * y el ciclo de vida de la vibración continua al presionar/soltar un botón.
 */
class BuzzerCommunicationViewModel(application: Application) : AndroidViewModel(application) {

  // Estado reactivo observable por la UI (Compose o Views)
  private val _uiState = MutableStateFlow(BuzzerUiState())
  val uiState: StateFlow<BuzzerUiState> = _uiState.asStateFlow()

  // Canal para eventos de un solo disparo (toasts, navegación, sonidos)
  private val _events = MutableSharedFlow<BuzzerEvent>()
  val events: SharedFlow<BuzzerEvent> = _events.asSharedFlow()

  // Hardware: Motor háptico de vibración del dispositivo
  private val vibrator: Vibrator? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vibratorManager?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  // Coroutine Jobs para control de tiempo y latidos mientras el botón está presionado
  private var sendBuzzJob: Job? = null
  private var receiveBuzzJob: Job? = null

  // =========================================================================
  // GESTIÓN DEL BOTÓN PRESIONADO (EMISOR: YO HAGO VIBRAR A MI PAREJA)
  // =========================================================================

  /**
   * Se ejecuta en el evento "Pointer Down" / "onPress" cuando el usuario apoya el dedo.
   * Inicia el estado activo, el contador en tiempo real y el pulso vibratorio.
   */
  fun onBuzzButtonPressed() {
    if (_uiState.value.isSendingBuzz) return

    val startTime = System.currentTimeMillis()

    // 1. Actualizar estado UI a "enviando zumbido"
    _uiState.update {
      it.copy(
        isSendingBuzz = true,
        sendBuzzDurationMs = 0L
      )
    }

    // 2. Feedback háptico local para confirmar que se está transmitiendo
    if (_uiState.value.isVibrationEnabled) {
      startDeviceVibration(_uiState.value.selectedPattern)
    }

    // 3. Notificar al socket/servicio en segundo plano: START_BUZZ_STREAM
    notifyBackendBuzzStarted()

    // 4. Iniciar Coroutine con ticker para medir la duración continua
    sendBuzzJob?.cancel()
    sendBuzzJob = viewModelScope.launch {
      while (isActive && _uiState.value.isSendingBuzz) {
        val elapsed = System.currentTimeMillis() - startTime
        _uiState.update { it.copy(sendBuzzDurationMs = elapsed) }
        delay(50) // Refresco suave para la animación
      }
    }
  }

  /**
   * Se ejecuta en el evento "Pointer Up" / "Release" / "Cancel" cuando el usuario levanta el dedo.
   * Detiene inmediatamente la vibración, cancela el ticker y finaliza la transmisión.
   */
  fun onBuzzButtonReleased() {
    if (!_uiState.value.isSendingBuzz) return

    val duration = _uiState.value.sendBuzzDurationMs

    // 1. Detener vibración inmediatamente
    stopDeviceVibration()

    // 2. Cancelar la corrutina del contador
    sendBuzzJob?.cancel()
    sendBuzzJob = null

    // 3. Notificar al backend: STOP_BUZZ_STREAM
    notifyBackendBuzzStopped(duration)

    // 4. Actualizar estado y registrar evento si duró más del umbral mínimo (e.g. 250ms)
    val record = if (duration >= 250) {
      BuzzRecord(
        id = System.currentTimeMillis().toString(),
        senderName = "Tú",
        durationMs = duration
      )
    } else null

    _uiState.update {
      it.copy(
        isSendingBuzz = false,
        sendBuzzDurationMs = 0L,
        lastBuzzSent = record ?: it.lastBuzzSent
      )
    }

    if (record != null) {
      viewModelScope.launch {
        _events.emit(BuzzerEvent.BuzzSentSuccessfully(duration))
      }
    }
  }

  // =========================================================================
  // GESTIÓN DE ZUMBIDO ENTRANTE (RECEPTOR: MI PAREJA ME HACE VIBRAR A MÍ)
  // =========================================================================

  /**
   * Invocado cuando llega el paquete de red/servicio indicando que la pareja
   * comenzó a presionar su pantalla.
   */
  fun onIncomingBuzzStarted(partnerName: String = _uiState.value.partnerName) {
    if (_uiState.value.isReceivingBuzz) return

    val startTime = System.currentTimeMillis()

    _uiState.update {
      it.copy(
        isReceivingBuzz = true,
        receiveBuzzDurationMs = 0L
      )
    }

    // Vibración continua en bucle en el dispositivo
    if (_uiState.value.isVibrationEnabled) {
      startDeviceVibration(_uiState.value.selectedPattern)
    }

    // Emitir evento para abrir overlay o alertar en pantalla
    viewModelScope.launch {
      _events.emit(BuzzerEvent.IncomingBuzzAlert(partnerName))
    }

    receiveBuzzJob?.cancel()
    receiveBuzzJob = viewModelScope.launch {
      while (isActive && _uiState.value.isReceivingBuzz) {
        val elapsed = System.currentTimeMillis() - startTime
        _uiState.update { it.copy(receiveBuzzDurationMs = elapsed) }
        delay(50)
      }
    }
  }

  /**
   * Invocado cuando llega el paquete indicando que la pareja soltó el dedo.
   */
  fun onIncomingBuzzStopped() {
    if (!_uiState.value.isReceivingBuzz) return

    val duration = _uiState.value.receiveBuzzDurationMs
    stopDeviceVibration()

    receiveBuzzJob?.cancel()
    receiveBuzzJob = null

    val record = BuzzRecord(
      id = System.currentTimeMillis().toString(),
      senderName = _uiState.value.partnerName,
      durationMs = duration
    )

    _uiState.update {
      it.copy(
        isReceivingBuzz = false,
        receiveBuzzDurationMs = 0L,
        lastBuzzReceived = record
      )
    }
  }

  // =========================================================================
  // HARDWARE DE VIBRACIÓN CONTINUA
  // =========================================================================

  /**
   * Activa la vibración en bucle infinito repetitivo hasta llamar a [stopDeviceVibration]
   */
  private fun startDeviceVibration(pattern: VibrationPattern) {
    try {
      if (vibrator?.hasVibrator() != true) return

      val timings = pattern.patternArray
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // repeat index = 0 significa repetir indefinidamente desde el inicio del patrón
        val effect = VibrationEffect.createWaveform(timings, 0)
        vibrator?.vibrate(effect)
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(timings, 0)
      }
    } catch (_: Exception) {
      // Manejo seguro ante restricciones del fabricante
    }
  }

  /**
   * Cancela cualquier vibración activa en el dispositivo
   */
  private fun stopDeviceVibration() {
    try {
      vibrator?.cancel()
    } catch (_: Exception) {
      // Manejo seguro
    }
  }

  // =========================================================================
  // CONFIGURACIONES Y PREFERENCIAS
  // =========================================================================

  fun setVibrationEnabled(enabled: Boolean) {
    _uiState.update { it.copy(isVibrationEnabled = enabled) }
    if (!enabled) {
      stopDeviceVibration()
    }
  }

  fun setVibrationPattern(pattern: VibrationPattern) {
    _uiState.update { it.copy(selectedPattern = pattern) }
    // Muestra una prueba breve de 200ms
    if (_uiState.value.isVibrationEnabled) {
      startDeviceVibration(pattern)
      viewModelScope.launch {
        delay(250)
        stopDeviceVibration()
      }
    }
  }

  fun updatePartnerInfo(name: String, isOnline: Boolean) {
    _uiState.update {
      it.copy(partnerName = name, isPartnerOnline = isOnline)
    }
  }

  // =========================================================================
  // COMUNICACIÓN CON CAPA DE RED / SOCKET / WEBRTC / REPOSITORY
  // =========================================================================

  private fun notifyBackendBuzzStarted() {
    // Aquí se conecta con WebSocket, Firestore Snapshot o WebRTC DataChannel
    // Ej: webSocketClient.send(BuzzPacket(type = "START", senderId = myId))
  }

  private fun notifyBackendBuzzStopped(totalDurationMs: Long) {
    // Ej: webSocketClient.send(BuzzPacket(type = "STOP", senderId = myId, duration = totalDurationMs))
  }

  /**
   * Limpieza de recursos al destruir el ViewModel para garantizar que el motor
   * de vibración NUNCA quede encendido consumiendo batería.
   */
  override fun onCleared() {
    stopDeviceVibration()
    sendBuzzJob?.cancel()
    receiveBuzzJob?.cancel()
    super.onCleared()
  }
}
