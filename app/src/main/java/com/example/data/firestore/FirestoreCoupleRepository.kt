package com.example.data.firestore

import android.content.Context
import android.util.Log
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.example.R
import com.example.model.CoupleMessage
import com.example.model.CoupleCalendarEvent
import com.example.model.EventType
import com.example.model.MessageType
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

class FirestoreCoupleRepository(private val context: Context) {

  // No inicializar servicios Firebase si no se incluyó la configuración real.
  private val firebaseConfigured = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
  private val firestore: FirebaseFirestore? = if (firebaseConfigured) runCatching {
    FirebaseFirestore.getInstance()
  }.getOrNull() else null
  private val auth: FirebaseAuth? = if (firebaseConfigured) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null
  private val storage = if (firebaseConfigured) runCatching { FirebaseStorage.getInstance() }.getOrNull() else null

  fun currentUserId(): String? = auth?.currentUser?.uid

  /**
   * Escucha mensajes en tiempo real para una pareja específica
   */
  fun observeMessages(coupleId: String): Flow<List<CoupleMessage>> {
    val firestoreInstance = firestore ?: return flowOf(emptyList())
    return callbackFlow {
    val collectionRef = firestoreInstance.collection("couples")
      .document(coupleId)
      .collection("messages")
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .limitToLast(120)

    val listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
      if (error != null) {
        Log.e("Firestore", "Error observando mensajes en tiempo real", error)
        close(error)
        return@addSnapshotListener
      }

      if (snapshot != null) {
        val messages = snapshot.documents.mapNotNull { doc ->
          try {
            val senderId = doc.getString("senderId") ?: return@mapNotNull null
            val content = doc.getString("content") ?: ""
            val typeStr = doc.getString("type") ?: MessageType.TEXT.name
            val type = try { MessageType.valueOf(typeStr) } catch (_: Exception) { MessageType.TEXT }
            val timestamp = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
            val audioDuration = (doc.getLong("audioDurationSec") ?: 0L).toInt()
            val locationName = doc.getString("locationName")
            val lat = doc.getDouble("latitude")
            val lng = doc.getDouble("longitude")
            val buzzDuration = doc.getLong("buzzDurationMs") ?: 0L
            val mediaUrl = doc.getString("mediaUrl")
            val mediaData = doc.getString("mediaData")

            CoupleMessage(
              id = doc.id,
              senderId = senderId,
              content = content,
              timestamp = timestamp,
              type = type,
              audioDurationSec = audioDuration,
              locationName = locationName,
              latitude = lat,
              longitude = lng,
              buzzDurationMs = buzzDuration,
              mediaUrl = mediaUrl,
              mediaData = mediaData
            )
          } catch (e: Exception) {
            Log.w("Firestore", "Error deserializando mensaje: ${doc.id}", e)
            null
          }
        }
        trySend(messages)
      }
    }

    awaitClose {
      listenerRegistration.remove()
    }
    }
  }

  /**
   * Envía un mensaje a Firestore con marca de tiempo del servidor
   */
  fun sendMessage(
    coupleId: String,
    content: String,
    type: MessageType = MessageType.TEXT,
    audioDurationSec: Int = 0,
    locName: String? = null,
    lat: Double? = null,
    lng: Double? = null,
    buzzDurationMs: Long = 0,
    mediaUrl: String? = null,
    mediaData: String? = null,
    onSuccess: () -> Unit = {},
    onError: (Exception) -> Unit = {}
  ) {
    val firestoreInstance = firestore
    if (firestoreInstance == null) {
      onError(IllegalStateException("Firebase no está configurado. Agrega google-services.json para sincronizar entre dispositivos."))
      return
    }
    val uid = auth?.currentUser?.uid
    if (uid.isNullOrBlank()) {
      onError(IllegalStateException("La sesión de Firebase no está iniciada. Vuelve a abrir la aplicación e inténtalo otra vez."))
      return
    }

    val data = hashMapOf(
      "senderId" to uid,
      "content" to content,
      "type" to type.name,
      "audioDurationSec" to audioDurationSec,
      "locationName" to locName,
      "latitude" to lat,
      "longitude" to lng,
      "buzzDurationMs" to buzzDurationMs,
      "mediaUrl" to mediaUrl,
      "mediaData" to mediaData,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )

    firestoreInstance.collection("couples")
      .document(coupleId)
      .collection("messages")
      .add(data)
      .addOnSuccessListener { onSuccess() }
      .addOnFailureListener { e ->
        Log.e("Firestore", "Error al enviar mensaje a Firestore", e)
        onError(e)
      }
  }

  /** Sube una foto o nota de voz a Firebase Storage y devuelve su URL descargable. */
  fun uploadMedia(coupleId: String, uri: Uri, mediaType: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
    val storageInstance = storage
    if (storageInstance == null) {
      onError(IllegalStateException("Firebase Storage no está disponible. Verifica la configuración del proyecto."))
      return
    }
    val safeType = if (mediaType == "audio") "audio" else "photos"
    val objectRef = storageInstance.reference.child("couples/$coupleId/$safeType/${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}")
    objectRef.putFile(uri)
      .addOnSuccessListener {
        objectRef.downloadUrl.addOnSuccessListener { downloadUri -> onSuccess(downloadUri.toString()) }
          .addOnFailureListener { error -> onError(error) }
      }
      .addOnFailureListener { error -> onError(error) }
  }


  /** Escucha el calendario compartido de la pareja en tiempo real. */
  fun observeCalendarEvents(coupleId: String): Flow<List<CoupleCalendarEvent>> {
    val db = firestore ?: return flowOf(emptyList())
    return callbackFlow {
      val registration = db.collection("couples").document(coupleId)
        .collection("calendarEvents").orderBy("dateMillis", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.e("Firestore", "Error observando calendario", error)
            close(error)
            return@addSnapshotListener
          }
          val events = snapshot?.documents.orEmpty().mapNotNull { doc ->
            val title = doc.getString("title") ?: return@mapNotNull null
            val eventType = runCatching { EventType.valueOf(doc.getString("eventType") ?: "SPECIAL") }
              .getOrDefault(EventType.SPECIAL)
            CoupleCalendarEvent(
              id = doc.id,
              title = title,
              description = doc.getString("description") ?: "",
              dateMillis = doc.getLong("dateMillis") ?: 0L,
              eventType = eventType,
              location = doc.getString("location") ?: ""
            )
          }
          trySend(events)
        }
      awaitClose { registration.remove() }
    }
  }

  fun addCalendarEvent(coupleId: String, event: CoupleCalendarEvent, onError: (Exception) -> Unit = {}) {
    val db = firestore
    if (db == null) {
      onError(IllegalStateException("Firebase no está configurado"))
      return
    }
    db.collection("couples").document(coupleId).collection("calendarEvents")
      .document(event.id).set(mapOf(
        "title" to event.title,
        "description" to event.description,
        "dateMillis" to event.dateMillis,
        "eventType" to event.eventType.name,
        "location" to event.location,
        "createdBy" to (auth?.currentUser?.uid ?: ""),
        "createdAt" to FieldValue.serverTimestamp()
      )).addOnFailureListener { error -> onError(error) }
  }


  data class SharedGameState(
    val board: List<String> = List(9) { "" },
    val turn: String = "❤️",
    val winner: String? = null,
    val scoreHearts: Int = 0,
    val scoreKisses: Int = 0,
    val updatedBy: String = ""
  )

  /** Escucha el estado compartido del tres en raya en tiempo real. */
  fun observeGameState(coupleId: String): Flow<SharedGameState?> {
    val db = firestore ?: return flowOf(null)
    return callbackFlow {
      val registration = db.collection("couples").document(coupleId)
        .collection("games").document("tictactoe")
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.e("Firestore", "Error observando el juego compartido", error)
            close(error)
            return@addSnapshotListener
          }
          if (snapshot == null || !snapshot.exists()) { trySend(null); return@addSnapshotListener }
          val board = (snapshot.get("board") as? List<*>)?.map { it as? String ?: "" }
          trySend(SharedGameState(
            board = (board ?: List(9) { "" }).take(9).let { it + List((9 - it.size).coerceAtLeast(0)) { "" } },
            turn = snapshot.getString("turn") ?: "❤️",
            winner = snapshot.getString("winner"),
            scoreHearts = (snapshot.getLong("scoreHearts") ?: 0L).toInt(),
            scoreKisses = (snapshot.getLong("scoreKisses") ?: 0L).toInt(),
            updatedBy = snapshot.getString("updatedBy") ?: ""
          ))
        }
      awaitClose { registration.remove() }
    }
  }

  /** Publica un movimiento. Firestore document es el estado canónico compartido. */
  fun publishGameState(coupleId: String, state: SharedGameState, onError: (Exception) -> Unit = {}) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase no está configurado")); return }
    db.collection("couples").document(coupleId).collection("games").document("tictactoe")
      .set(mapOf(
        "board" to state.board, "turn" to state.turn, "winner" to state.winner,
        "scoreHearts" to state.scoreHearts, "scoreKisses" to state.scoreKisses,
        "updatedBy" to (auth?.currentUser?.uid ?: ""), "updatedAt" to FieldValue.serverTimestamp()
      )).addOnFailureListener(onError)
  }

  /** Señalización remota para llamadas: registra invitaciones/estados; requiere WebRTC para audio real. */
  fun setCallState(coupleId: String, callId: String, fields: Map<String, Any?>, onError: (Exception) -> Unit = {}) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase no está configurado")); return }
    db.collection("couples").document(coupleId).collection("calls").document(callId)
      .set(fields + mapOf("updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to (auth?.currentUser?.uid ?: "")), SetOptions.merge())
      .addOnFailureListener(onError)
  }


  fun createCallInvitation(coupleId: String, callId: String, fields: Map<String, Any?>,
                           onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {}) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase no está configurado")); return }
    val uid = auth?.currentUser?.uid
    if (uid.isNullOrBlank()) { onError(IllegalStateException("La sesión de Firebase no está iniciada")); return }
    db.collection("couples").document(coupleId).collection("calls").document(callId)
      .set(fields + mapOf("updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid))
      .addOnSuccessListener { onSuccess() }
      .addOnFailureListener(onError)
  }

  /** Observa el estado de una llamada para recibir offer/answer y cambios de estado. */
  fun observeCallState(coupleId: String, callId: String): Flow<Map<String, Any>?> {
    val db = firestore ?: return flowOf(null)
    return callbackFlow {
      val registration = db.collection("couples").document(coupleId).collection("calls").document(callId)
        .addSnapshotListener { snapshot, error ->
          if (error != null) { close(error); return@addSnapshotListener }
          trySend(snapshot?.takeIf { it.exists() }?.data)
        }
      awaitClose { registration.remove() }
    }
  }

  /** Escucha llamadas entrantes activas para que la pantalla pueda ofrecer aceptar/rechazar. */
  fun observeIncomingCalls(coupleId: String, currentUid: String): Flow<List<Pair<String, Map<String, Any>>>> {
    val db = firestore ?: return flowOf(emptyList())
    return callbackFlow {
      val registration = db.collection("couples").document(coupleId).collection("calls")
        .whereEqualTo("status", "ringing")
        .addSnapshotListener { snapshot, error ->
          if (error != null) { Log.e("Firestore", "Error escuchando llamadas entrantes", error); close(error); return@addSnapshotListener }
          val incoming = snapshot?.documents.orEmpty().mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val caller = data["callerUid"] as? String ?: return@mapNotNull null
            if (caller == currentUid) null else doc.id to data
          }
          trySend(incoming)
        }
      awaitClose { registration.remove() }
    }
  }

  /** Escucha candidatos ICE remotos y evita bloquear el hilo de interfaz. */
  fun observeCallIceCandidates(coupleId: String, callId: String, remoteSide: String): Flow<List<Map<String, Any>>> {
    val db = firestore ?: return flowOf(emptyList())
    val collection = if (remoteSide == "answer") "answerCandidates" else "offerCandidates"
    return callbackFlow {
      val registration = db.collection("couples").document(coupleId).collection("calls").document(callId)
        .collection(collection).addSnapshotListener { snapshot, error ->
          if (error != null) { Log.e("Firestore", "Error escuchando ICE", error); close(error); return@addSnapshotListener }
          trySend(snapshot?.documents.orEmpty().mapNotNull { it.data })
        }
      awaitClose { registration.remove() }
    }
  }

  /** Publica una señal ICE individual; la app debe verificar que la llamada pertenece a la pareja. */
  fun addCallIceCandidate(coupleId: String, callId: String, side: String, candidate: Map<String, Any?>,
                          onError: (Exception) -> Unit = {}) {
    val db = firestore
    val uid = auth?.currentUser?.uid
    if (db == null || uid.isNullOrBlank()) {
      onError(IllegalStateException("Se requiere Firebase Auth para señalizar una llamada")); return
    }
    val safeSide = if (side == "answer") "answerCandidates" else "offerCandidates"
    db.collection("couples").document(coupleId).collection("calls").document(callId)
      .collection(safeSide).add(candidate + mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp()))
      .addOnFailureListener(onError)
  }

  /** Estado genérico para juegos de pareja (trivia y ruleta), en tiempo real. */
  fun observeNamedGameState(coupleId: String, gameId: String): Flow<Map<String, Any>?> {
    val db = firestore ?: return flowOf(null)
    return callbackFlow {
      val registration = db.collection("couples").document(coupleId)
        .collection("games").document(gameId)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.e("Firestore", "Error observando el juego $gameId", error)
            close(error)
            return@addSnapshotListener
          }
          trySend(snapshot?.takeIf { it.exists() }?.data)
        }
      awaitClose { registration.remove() }
    }
  }

  fun publishNamedGameState(coupleId: String, gameId: String, values: Map<String, Any?>, onError: (Exception) -> Unit = {}) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase no está configurado")); return }
    val uid = auth?.currentUser?.uid
    if (uid.isNullOrBlank()) { onError(IllegalStateException("Inicia sesión para sincronizar el juego")); return }
    db.collection("couples").document(coupleId).collection("games").document(gameId)
      .set(values + mapOf("updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
      .addOnFailureListener(onError)
  }

  /** Actualiza campos (rutas con puntos, p. ej. "wallet.uid") del documento de la pareja. */
  fun updateCoupleFields(
    coupleId: String,
    fields: Map<String, Any?>,
    onSuccess: () -> Unit = {},
    onError: (Exception) -> Unit = {}
  ) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase no está configurado")); return }
    if (auth?.currentUser?.uid.isNullOrBlank()) { onError(IllegalStateException("Inicia sesión para sincronizar")); return }
    db.collection("couples").document(coupleId).update(fields)
      .addOnSuccessListener { onSuccess() }
      .addOnFailureListener { e -> onError(e) }
  }

  /**
   * Guarda el estado de un juego dentro del propio documento de la pareja (campo games.<id>).
   * Así no depende de reglas adicionales de Firestore: usa los mismos permisos que el chat y el zumbador.
   */
  fun updateGameField(
    coupleId: String,
    gameId: String,
    values: Map<String, Any?>,
    onSuccess: () -> Unit = {},
    onError: (Exception) -> Unit = {}
  ) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase no está configurado")); return }
    val uid = auth?.currentUser?.uid
    if (uid.isNullOrBlank()) { onError(IllegalStateException("Inicia sesión para sincronizar el juego")); return }
    db.collection("couples").document(coupleId)
      .update(mapOf("games.$gameId" to (values + mapOf("updatedBy" to uid, "t" to System.currentTimeMillis()))))
      .addOnSuccessListener { onSuccess() }
      .addOnFailureListener { e -> onError(e) }
  }

  /** Nota compartida del salvapantallas; emite cambios remotos sin reiniciar la app. */
  fun observeScreensaver(coupleId: String): Flow<Pair<String, String>?> {
    val db = firestore ?: return flowOf(null)
    return callbackFlow {
      val registration = db.collection("couples").document(coupleId)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.e("Firestore", "Error observando la nota compartida", error)
            close(error)
            return@addSnapshotListener
          }
          if (snapshot == null || !snapshot.exists()) trySend(null)
          else trySend(Pair(snapshot.getString("screensaverNote") ?: "", snapshot.getString("screensaverEmoji") ?: "💖"))
        }
      awaitClose { registration.remove() }
    }
  }

  /**
   * Sincroniza el zumbido activo en tiempo real para que la pareja comience a sonar
   */
  fun updateActiveBuzzState(coupleId: String, isBuzzing: Boolean, durationMs: Long = 0) {
    val uid = auth?.currentUser?.uid
    if (uid.isNullOrBlank()) {
      Log.w("Firestore", "No se envía el zumbido porque no hay una sesión autenticada")
      return
    }
    val updates = hashMapOf<String, Any?>(
      "activeBuzzSenderUid" to if (isBuzzing) uid else null,
      "activeBuzzDurationMs" to durationMs,
      "updatedAt" to FieldValue.serverTimestamp()
    )

    val firestoreInstance = firestore ?: return
    firestoreInstance.collection("couples")
      .document(coupleId)
      .update(updates)
      .addOnFailureListener { error ->
        Log.w("Firestore", "No se pudo actualizar el zumbido; la pareja debe estar vinculada", error)
      }
  }

  /** Documento de la pareja completo (miembros, permisos de zumbido, zumbido activo). */
  fun observeCoupleMeta(coupleId: String): Flow<Map<String, Any>?> {
    val db = firestore ?: return flowOf(null)
    return callbackFlow {
      val registration = db.collection("couples").document(coupleId)
        .addSnapshotListener { snapshot, error ->
          if (error != null) { Log.w("Firestore", "Error observando la pareja", error); return@addSnapshotListener }
          trySend(snapshot?.takeIf { it.exists() }?.data)
        }
      awaitClose { registration.remove() }
    }
  }

  /** Publica si ESTE usuario permite que su pareja haga sonar/vibrar su teléfono. */
  fun setBuzzAllowed(coupleId: String, allowed: Boolean) {
    val uid = auth?.currentUser?.uid ?: return
    val db = firestore ?: return
    db.collection("couples").document(coupleId)
      .update(mapOf("buzzAllow.$uid" to allowed))
      .addOnFailureListener { Log.w("Firestore", "No se pudo guardar el permiso de zumbido", it) }
  }

  /**
   * Sincroniza la nota del salvapantallas en la nube
   */
  fun syncScreensaverNote(coupleId: String, note: String, emoji: String) {
    val firestoreInstance = firestore ?: return
    firestoreInstance.collection("couples")
      .document(coupleId)
      .update(
        hashMapOf(
          "screensaverNote" to note,
          "screensaverEmoji" to emoji,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
  }
}
