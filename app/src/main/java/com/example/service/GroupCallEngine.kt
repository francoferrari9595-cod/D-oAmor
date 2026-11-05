package com.example.service

import android.content.Context
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Motor de llamada de voz GRUPAL (malla): una sola fuente de micrófono y una conexión WebRTC por cada
 * otro participante. La señalización (ofertas, respuestas y candidatos ICE) la hace quien lo usa.
 */
class GroupCallEngine(context: Context, private val listener: Listener) {
  interface Listener {
    fun onSignal(to: String, type: String, data: String)
    fun onPeerState(uid: String, state: PeerConnection.PeerConnectionState)
    fun onError(message: String)
  }

  private class Peer(val pc: PeerConnection) {
    var remoteReady = false
    val pending = ArrayList<IceCandidate>()
  }

  private val appContext = context.applicationContext
  private val factory: PeerConnectionFactory
  private var audioSource: AudioSource? = null
  private var audioTrack: AudioTrack? = null
  private val peers = HashMap<String, Peer>()

  init {
    PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
    factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
  }

  fun startLocalAudio() {
    if (audioTrack != null) return
    audioSource = factory.createAudioSource(MediaConstraints())
    audioTrack = factory.createAudioTrack("kombo-gaudio", audioSource)
  }

  private fun iceServers() = listOf(
    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
    PeerConnection.IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
    PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
    PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
    PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
  )

  private fun ensurePeer(uid: String): Peer? {
    peers[uid]?.let { return it }
    val config = PeerConnection.RTCConfiguration(iceServers()).apply {
      sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
      continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }
    val pc = factory.createPeerConnection(config, object : PeerConnection.Observer {
      override fun onSignalingChange(state: PeerConnection.SignalingState) {}
      override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
      override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) {}
      override fun onConnectionChange(state: PeerConnection.PeerConnectionState) { listener.onPeerState(uid, state) }
      override fun onIceConnectionReceivingChange(receiving: Boolean) {}
      override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
      override fun onIceCandidate(candidate: IceCandidate) {
        val json = JSONObject().put("m", candidate.sdpMid ?: "").put("i", candidate.sdpMLineIndex).put("s", candidate.sdp).toString()
        listener.onSignal(uid, "ice", json)
      }
      override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
      override fun onAddStream(stream: org.webrtc.MediaStream) {}
      override fun onRemoveStream(stream: org.webrtc.MediaStream) {}
      override fun onDataChannel(channel: DataChannel) {}
      override fun onRenegotiationNeeded() {}
      override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out org.webrtc.MediaStream>) {}
      override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {}
    }) ?: return null
    audioTrack?.let { pc.addTrack(it, listOf("kombo")) }
    return Peer(pc).also { peers[uid] = it }
  }

  fun hasPeer(uid: String): Boolean = peers.containsKey(uid)

  fun offerTo(uid: String) {
    val p = ensurePeer(uid) ?: return listener.onError("No se pudo crear la conexión con un participante")
    p.pc.createOffer(observer(onCreated = { d ->
      p.pc.setLocalDescription(observer(onSet = { listener.onSignal(uid, "offer", d.description) }), d)
    }), MediaConstraints())
  }

  fun handleOffer(uid: String, sdp: String) {
    val p = ensurePeer(uid) ?: return listener.onError("No se pudo crear la conexión con un participante")
    p.pc.setRemoteDescription(observer(onSet = {
      p.remoteReady = true
      flush(p)
      p.pc.createAnswer(observer(onCreated = { a ->
        p.pc.setLocalDescription(observer(onSet = { listener.onSignal(uid, "answer", a.description) }), a)
      }), MediaConstraints())
    }), SessionDescription(SessionDescription.Type.OFFER, sdp))
  }

  fun handleAnswer(uid: String, sdp: String) {
    val p = peers[uid] ?: return
    p.pc.setRemoteDescription(observer(onSet = { p.remoteReady = true; flush(p) }), SessionDescription(SessionDescription.Type.ANSWER, sdp))
  }

  fun handleIce(uid: String, json: String) {
    val p = ensurePeer(uid) ?: return
    val c = runCatching {
      val o = JSONObject(json)
      IceCandidate(o.optString("m"), o.optInt("i"), o.getString("s"))
    }.getOrNull() ?: return
    if (p.remoteReady) p.pc.addIceCandidate(c) else p.pending.add(c)
  }

  private fun flush(p: Peer) {
    p.pending.forEach { p.pc.addIceCandidate(it) }
    p.pending.clear()
  }

  fun removePeer(uid: String) {
    val p = peers.remove(uid) ?: return
    runCatching { p.pc.close() }
    runCatching { p.pc.dispose() }
  }

  fun setMuted(muted: Boolean) { audioTrack?.setEnabled(!muted) }

  private fun observer(onCreated: (SessionDescription) -> Unit = {}, onSet: () -> Unit = {}): SdpObserver = object : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription) { onCreated(description) }
    override fun onSetSuccess() { onSet() }
    override fun onCreateFailure(error: String) { listener.onError("SDP create: $error") }
    override fun onSetFailure(error: String) { listener.onError("SDP set: $error") }
  }

  fun close() {
    peers.keys.toList().forEach { removePeer(it) }
    runCatching { audioTrack?.dispose() }; audioTrack = null
    runCatching { audioSource?.dispose() }; audioSource = null
    runCatching { factory.dispose() }
  }
}
