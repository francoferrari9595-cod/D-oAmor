package com.example.service

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/** WebRTC media engine; callers must exchange SDP and ICE through authenticated signaling. */
class WebRtcCallEngine(context: Context, private val listener: Listener) {
  interface Listener {
    fun onLocalDescription(type: String, sdp: String)
    fun onIceCandidate(candidate: IceCandidate)
    fun onConnectionState(state: PeerConnection.PeerConnectionState)
    fun onLocalVideoTrack(track: VideoTrack?)
    fun onRemoteVideoTrack(track: VideoTrack?)
    fun onError(message: String)
  }

  private val appContext = context.applicationContext
  private val egl = EglBase.create()
  private val factory: PeerConnectionFactory
  val eglContext: EglBase.Context get() = egl.eglBaseContext
  private var peer: PeerConnection? = null
  private var audioSource: AudioSource? = null
  private var audioTrack: AudioTrack? = null
  private var videoSource: VideoSource? = null
  private var videoTrack: VideoTrack? = null
  private var capturer: CameraVideoCapturer? = null
  private var textureHelper: SurfaceTextureHelper? = null
  private var remoteDescriptionReady = false
  private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

  init {
    PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
    factory = PeerConnectionFactory.builder()
      .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
      .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext)).createPeerConnectionFactory()
  }

  fun create(isVideo: Boolean): Boolean {
    closePeer()
    // STUN + TURN: el TURN permite conectar cuando los dos teléfonos están en redes distintas
    // (datos móviles / Wi-Fi distinto). Para uso comercial conviene un TURN propio (ver LEEME).
    val iceServers = listOf(
      PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
      PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
      PeerConnection.IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
      PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
      PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
      PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
    )
    val config = PeerConnection.RTCConfiguration(iceServers).apply {
      sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
      continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }
    peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
      override fun onSignalingChange(state: PeerConnection.SignalingState) {}
      override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
      override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) {}
      override fun onConnectionChange(state: PeerConnection.PeerConnectionState) { listener.onConnectionState(state) }
      override fun onIceConnectionReceivingChange(receiving: Boolean) {}
      override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
      override fun onIceCandidate(candidate: IceCandidate) { listener.onIceCandidate(candidate) }
      override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
      override fun onAddStream(stream: org.webrtc.MediaStream) {}
      override fun onRemoveStream(stream: org.webrtc.MediaStream) {}
      override fun onDataChannel(channel: DataChannel) {}
      override fun onRenegotiationNeeded() {}
      override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out org.webrtc.MediaStream>) {
        (receiver.track() as? VideoTrack)?.let { listener.onRemoteVideoTrack(it) }
      }
      override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
        (transceiver.receiver.track() as? VideoTrack)?.let { listener.onRemoteVideoTrack(it) }
      }
    })
    val connection = peer ?: run { listener.onError("No se pudo crear la conexión WebRTC"); return false }
    audioSource = factory.createAudioSource(MediaConstraints())
    audioTrack = factory.createAudioTrack("duoamor-audio", audioSource).also { connection.addTrack(it) }
    if (isVideo && !createCameraTrack()) listener.onError("No se pudo iniciar la cámara; se necesita permiso CAMERA y una cámara disponible.")
    return true
  }

  private fun createCameraTrack(): Boolean {
    val enumerator = Camera2Enumerator(appContext)
    val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull() ?: return false
    capturer = enumerator.createCapturer(cameraName, null) as? CameraVideoCapturer ?: return false
    textureHelper = SurfaceTextureHelper.create("DuoAmorCamera", egl.eglBaseContext)
    videoSource = factory.createVideoSource(false)
    capturer?.initialize(textureHelper, appContext, videoSource?.capturerObserver)
    return try {
      capturer?.startCapture(640, 480, 24)
      videoTrack = factory.createVideoTrack("duoamor-video", videoSource)
      videoTrack?.let { track -> peer?.addTrack(track); listener.onLocalVideoTrack(track) }
      true
    } catch (e: Exception) { listener.onError("Error iniciando cámara: ${e.message}"); false }
  }

  fun createOffer() {
    val pc = peer ?: return listener.onError("WebRTC no está inicializado")
    pc.createOffer(observer(onCreated = { description ->
      pc.setLocalDescription(observer(onSet = { listener.onLocalDescription("offer", description.description) }), description)
    }), MediaConstraints())
  }

  fun acceptOffer(sdp: String) {
    val pc = peer ?: return listener.onError("WebRTC no está inicializado")
    pc.setRemoteDescription(observer(onSet = {
      remoteDescriptionReady = true
      flushRemoteCandidates()
      pc.createAnswer(observer(onCreated = { answer ->
        pc.setLocalDescription(observer(onSet = { listener.onLocalDescription("answer", answer.description) }), answer)
      }), MediaConstraints())
    }), SessionDescription(SessionDescription.Type.OFFER, sdp))
  }

  fun acceptAnswer(sdp: String) {
    peer?.setRemoteDescription(observer(onSet = {
      remoteDescriptionReady = true
      flushRemoteCandidates()
    }), SessionDescription(SessionDescription.Type.ANSWER, sdp))
  }

  fun addRemoteIceCandidate(candidate: IceCandidate) {
    if (remoteDescriptionReady) peer?.addIceCandidate(candidate) else pendingRemoteCandidates.add(candidate)
  }

  private fun flushRemoteCandidates() {
    val pc = peer ?: return
    pendingRemoteCandidates.forEach { pc.addIceCandidate(it) }
    pendingRemoteCandidates.clear()
  }
  fun setMuted(muted: Boolean) { audioTrack?.setEnabled(!muted) }
  fun setVideoEnabled(enabled: Boolean) { videoTrack?.setEnabled(enabled) }
  fun switchCamera() {
    val camera = capturer ?: return listener.onError("La cámara no está disponible")
    runCatching { camera.switchCamera(null) }
      .onFailure { listener.onError("No se pudo cambiar la cámara: ${it.message}") }
  }

  private fun observer(onCreated: (SessionDescription) -> Unit = {}, onSet: () -> Unit = {}): SdpObserver = object : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription) { onCreated(description) }
    override fun onSetSuccess() { onSet() }
    override fun onCreateFailure(error: String) { listener.onError("SDP create: $error") }
    override fun onSetFailure(error: String) { listener.onError("SDP set: $error") }
  }

  fun close() { closePeer(); factory.dispose(); egl.release() }
  private fun closePeer() {
    remoteDescriptionReady = false
    pendingRemoteCandidates.clear()
    runCatching { capturer?.stopCapture() }; runCatching { capturer?.dispose() }; capturer = null
    textureHelper?.dispose(); textureHelper = null
    videoTrack?.let { listener.onLocalVideoTrack(null); it.dispose() }; videoTrack = null; videoSource?.dispose(); videoSource = null
    audioTrack?.dispose(); audioTrack = null; audioSource?.dispose(); audioSource = null
    listener.onRemoteVideoTrack(null)
    peer?.close(); peer?.dispose(); peer = null
  }
}
