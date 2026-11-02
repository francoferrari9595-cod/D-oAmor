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
    FirestoreInstance.get(context)
  }.getOrNull() else null
  private val auth: FirebaseAuth? = if (firebaseConfigured) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null
  private val storage = if (firebaseConfigured) runCatching { FirebaseStorage.getInstance() }.getOrNull() else null

  fun currentUserId(): String? = auth?.currentUser?.uid

  /** Garantiza una sesión de Firebase (anónima si hace falta). Los invitados no pasan por la pantalla de vinculación. */
  fun ensureSignedIn(onDone: (String) -> Unit, onError: (Exception) -> Unit) {
    val a = auth
    if (a == null) { onError(IllegalStateException("Firebase no está configurado en esta app.")); return }
    val current = a.currentUser
    if (current != null) { onDone(current.uid); return }
    a.signInAnonymously()
      .addOnSuccessListener { r -> val u = r.user?.uid; if (u != null) onDone(u) else onError(IllegalStateException("No se pudo iniciar sesión.")) }
      .addOnFailureListener { onError(it) }
  }

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

  // ---------- Diagnóstico de conexión ----------
  private fun codeOf(e: Exception): String =
    ((e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name) ?: e.javaClass.simpleName

  fun diagCouple(coupleId: String, uid: String, cb: (String) -> Unit) {
    val db = firestore ?: run { cb("Firebase no configurado"); return }
    db.collection("couples").document(coupleId).get()
      .addOnSuccessListener { d ->
        @Suppress("UNCHECKED_CAST")
        val members = (d.get("memberUids") as? List<String>) ?: emptyList()
        cb("OK · miembros=${members.size} · ¿tu usuario está en la pareja? ${if (members.contains(uid)) "SÍ" else "NO (hay que re-vincular)"}")
      }
      .addOnFailureListener { cb("ERROR ${codeOf(it)}") }
  }

  fun diagGet(path: String, cb: (String) -> Unit) {
    val db = firestore ?: run { cb("Firebase no configurado"); return }
    db.document(path).get()
      .addOnSuccessListener { cb("OK (existe=${it.exists()})") }
      .addOnFailureListener { cb("ERROR ${codeOf(it)}") }
  }

  fun diagList(path: String, cb: (String) -> Unit) {
    val db = firestore ?: run { cb("Firebase no configurado"); return }
    db.collection(path).limit(1).get()
      .addOnSuccessListener { cb("OK") }
      .addOnFailureListener { cb("ERROR ${codeOf(it)}") }
  }

  fun diagSet(path: String, cb: (String) -> Unit) {
    val db = firestore ?: run { cb("Firebase no configurado"); return }
    db.document(path).set(mapOf("diag" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
      .addOnSuccessListener { cb("OK") }
      .addOnFailureListener { cb("ERROR ${codeOf(it)}") }
  }

  // ---------- Sala de invitados ----------
  fun observeRoom(roomId: String): Flow<Map<String, Any>?> {
    val db = firestore ?: return flowOf(null)
    return callbackFlow {
      val registration = roomRef(db, roomId)
        .addSnapshotListener { snapshot, error ->
          if (error != null) { Log.w("Firestore", "Error observando la sala", error); close(error); return@addSnapshotListener }
          trySend(snapshot?.takeIf { it.exists() }?.data)
        }
      awaitClose { registration.remove() }
    }
  }

  /** Walkie-talkie de la pareja: usa los mismos permisos que el chat (couples/{id}/walkie/state). */
  fun observeWalkie(coupleId: String): Flow<Map<String, Any>?> {
    val db = firestore ?: return flowOf(null)
    return callbackFlow {
      val registration = db.collection("couples").document(coupleId).collection("walkie").document("state")
        .addSnapshotListener { snapshot, error ->
          if (error != null) { Log.w("Firestore", "Error observando el walkie", error); close(error); return@addSnapshotListener }
          trySend(snapshot?.takeIf { it.exists() }?.data)
        }
      awaitClose { registration.remove() }
    }
  }

  fun setWalkie(coupleId: String, fields: Map<String, Any?>, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {}) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase no está configurado")); return }
    if (auth?.currentUser?.uid.isNullOrBlank()) { onError(IllegalStateException("Inicia sesión para sincronizar")); return }
    db.collection("couples").document(coupleId).collection("walkie").document("state")
      .set(fields, com.google.firebase.firestore.SetOptions.merge())
      .addOnSuccessListener { onSuccess() }
      .addOnFailureListener { onError(it) }
  }

  fun ensureRoom(roomId: String, members: List<String>, myUid: String, myName: String, onError: (Exception) -> Unit = {}, onSuccess: () -> Unit = {}) {
    val db = firestore ?: return
    db.collection("rooms").document(roomId)
      .set(mapOf("memberUids" to members, "players" to mapOf(myUid to mapOf("name" to myName))), com.google.firebase.firestore.SetOptions.merge())
      .addOnSuccessListener { onSuccess() }
      .addOnFailureListener { onError(it) }
  }

  fun updateRoomFields(roomId: String, fields: Map<String, Any?>, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {}) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase no está configurado")); return }
    if (auth?.currentUser?.uid.isNullOrBlank()) { onError(IllegalStateException("Inicia sesión para sincronizar")); return }
    roomRef(db, roomId).update(fields)
      .addOnSuccessListener { onSuccess() }
      .addOnFailureListener { e -> onError(e) }
  }

  fun createGuestInvite(roomId: String, code: String, createdBy: String, onDone: () -> Unit, onError: (Exception) -> Unit) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase/Firestore no está inicializado. No se pudo guardar el código.")); return }
    if (auth?.currentUser?.uid.isNullOrBlank()) { onError(IllegalStateException("No hay sesión de Firebase activa. Volvé a iniciar sesión e intentá otra vez.")); return }
    // Transacción para no sobrescribir accidentalmente una invitación existente si hay colisión.
    val ref = db.collection("guestInvites").document(code)
    db.runTransaction { transaction ->
      val existing = transaction.get(ref)
      if (existing.exists()) throw IllegalStateException("Ese código ya está ocupado. Volvé a generar otro.")
      transaction.set(ref, mapOf(
        "roomId" to roomId,
        "createdBy" to createdBy,
        "active" to true,
        "createdAt" to System.currentTimeMillis()
      ))
      true
    }.addOnSuccessListener { onDone() }
      .addOnFailureListener { onError(it) }
  }

  fun deactivateInvite(code: String) {
    val db = firestore ?: return
    db.collection("guestInvites").document(code).update(mapOf("active" to false))
  }

  fun joinRoomAsGuest(code: String, uid: String, name: String, onDone: (String) -> Unit, onError: (Exception) -> Unit) {
    val db = firestore ?: return
    db.collection("guestInvites").document(code).get()
      .addOnSuccessListener { inv ->
        val roomId = inv.getString("roomId")
        if (!inv.exists() || roomId.isNullOrBlank() || inv.getBoolean("active") != true) {
          onError(IllegalStateException("Ese código no existe o ya no está activo."))
          return@addOnSuccessListener
        }
        db.collection("rooms").document(roomId).update(
          mapOf(
            "guestUids" to com.google.firebase.firestore.FieldValue.arrayUnion(uid),
            "joinCode" to code,
            "players.$uid.name" to name
          )
        ).addOnSuccessListener { onDone(roomId) }.addOnFailureListener { onError(it) }
      }
      .addOnFailureListener { onError(it) }
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

  // ---------- Salas públicas (Sala 1-4 y salas creadas con nombre) ----------
  /** Las salas públicas viven en "lobbies"; las de pareja/invitados siguen en "rooms". */
  private fun roomRef(db: FirebaseFirestore, roomId: String) =
    if (roomId.startsWith(com.example.model.Lobbies.PREFIX)) db.collection("lobbies").document(roomId)
    else db.collection("rooms").document(roomId)

  fun observeLobbies(): Flow<List<com.example.model.LobbyInfo>> {
    val db = firestore ?: return flowOf(emptyList())
    return callbackFlow {
      val registration = db.collection("lobbies").addSnapshotListener { snap, error ->
        if (error != null) { Log.w("Firestore", "Error observando las salas", error); close(error); return@addSnapshotListener }
        val now = System.currentTimeMillis()
        val list = snap?.documents.orEmpty().mapNotNull { d ->
          val name = d.getString("name") ?: return@mapNotNull null
          @Suppress("UNCHECKED_CAST")
          val players = d.get("players") as? Map<String, Any?> ?: emptyMap()
          val online = players.values.count { v -> ((v as? Map<*, *>)?.get("t") as? Number)?.toLong()?.let { now - it < com.example.model.Lobbies.PRESENCE_MS } == true }
          com.example.model.LobbyInfo(
            id = d.id, name = name, hasPassword = d.getBoolean("hasPw") == true,
            online = online, order = (d.getLong("order") ?: 1000L), fixed = d.getBoolean("fixed") == true
          )
        }.sortedWith(compareBy({ it.order }, { it.name }))
        trySend(list)
      }
      awaitClose { registration.remove() }
    }
  }

  /**
   * Crea Sala 1-4 la primera vez (Sala 4 con contraseña). No pisa las que ya existen.
   * Es tolerante: si el documento de contraseña ya existe (otro intento lo creó) o la sala ya fue creada
   * por otro teléfono, los "permiso denegado" de esta preparación se ignoran; no son un error real para el usuario.
   */
  fun ensureDefaultLobbies(onError: (Exception) -> Unit = {}) {
    val db = firestore ?: return
    if (auth?.currentUser == null) return
    com.example.model.Lobbies.DEFAULTS.forEach { d ->
      val ref = db.collection("lobbies").document(d.id)
      // Si un administrador eliminó esta sala fija, no se vuelve a crear.
      db.collection("tombstones").document(d.id).get().addOnCompleteListener { tomb ->
      if (tomb.isSuccessful && tomb.result?.exists() == true) return@addOnCompleteListener
      ref.get().addOnSuccessListener { snap ->
        if (snap.exists()) return@addOnSuccessListener
        val fields = mapOf("name" to d.name, "hasPw" to d.hasPassword, "order" to d.order, "fixed" to true,
          "createdAt" to System.currentTimeMillis(), "guestUids" to emptyList<String>(), "players" to emptyMap<String, Any>())
        val createLobbyDoc = {
          ref.set(fields).addOnFailureListener { e -> Log.w("Firestore", "No se pudo crear ${d.id} (puede que ya exista)", e) }
        }
        if (d.hasPassword) {
          // La contraseña se guarda en un documento privado que ningún cliente puede leer; las reglas lo comparan al entrar.
          // Se continúa aunque falle: si ya existía, igual hay que crear el documento de la sala.
          ref.collection("private").document("pw").set(mapOf("hash" to com.example.model.Lobbies.hash(com.example.model.Lobbies.DEFAULT_SALA4_PASSWORD)))
            .addOnCompleteListener { createLobbyDoc() }
        } else createLobbyDoc()
      }.addOnFailureListener { e -> onError(e) }
      }
    }
  }

  /** Elimina una sala (requiere el código de administración; las reglas lo verifican por su hash). */
  fun adminDeleteLobby(id: String, fixed: Boolean, codeHash: String, uid: String, onDone: () -> Unit, onError: (Exception) -> Unit) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase no está configurado")); return }
    db.collection("admins").document(uid).set(mapOf("hash" to codeHash, "t" to System.currentTimeMillis())).addOnSuccessListener {
      val del = { db.collection("lobbies").document(id).delete().addOnSuccessListener { onDone() }.addOnFailureListener { onError(it) } }
      if (fixed) db.collection("tombstones").document(id).set(mapOf("t" to System.currentTimeMillis())).addOnSuccessListener { del() }.addOnFailureListener { onError(it) }
      else del()
    }.addOnFailureListener { onError(it) }
  }

  fun createLobby(name: String, password: String, onDone: (String) -> Unit, onError: (Exception) -> Unit) {
    val db = firestore
    val uid = auth?.currentUser?.uid
    if (db == null || uid == null) { onError(IllegalStateException("Inicia sesión para crear una sala.")); return }
    val id = com.example.model.Lobbies.PREFIX + "u" + java.util.UUID.randomUUID().toString().take(8)
    val ref = db.collection("lobbies").document(id)
    val fields = mapOf("name" to name, "hasPw" to password.isNotEmpty(), "order" to 1000L + (System.currentTimeMillis() % 100000L),
      "fixed" to false, "createdBy" to uid, "createdAt" to System.currentTimeMillis(),
      "guestUids" to emptyList<String>(), "players" to emptyMap<String, Any>())
    val create = { ref.set(fields).addOnSuccessListener { onDone(id) }.addOnFailureListener { onError(it) } }
    if (password.isNotEmpty()) {
      ref.collection("private").document("pw").set(mapOf("hash" to com.example.model.Lobbies.hash(password)))
        .addOnSuccessListener { create() }.addOnFailureListener { onError(it) }
    } else create()
  }

  /** Entra a una sala pública. Si tiene contraseña, primero registra la prueba de acceso (las reglas la validan). */
  fun joinLobby(id: String, hasPassword: Boolean, password: String, uid: String, name: String, onDone: () -> Unit, onError: (Exception) -> Unit) {
    val db = firestore
    if (db == null) { onError(IllegalStateException("Firebase no está configurado")); return }
    val ref = db.collection("lobbies").document(id)
    val enter = {
      ref.update(mapOf(
        "guestUids" to com.google.firebase.firestore.FieldValue.arrayUnion(uid),
        "players.$uid" to mapOf("name" to name, "t" to System.currentTimeMillis())
      )).addOnSuccessListener { onDone() }.addOnFailureListener { onError(it) }
    }
    if (hasPassword) {
      ref.collection("joins").document(uid).set(mapOf("hash" to com.example.model.Lobbies.hash(password), "t" to System.currentTimeMillis()))
        .addOnSuccessListener { enter() }
        .addOnFailureListener { onError(it) }
    } else enter()
  }

  /** Reinicia la conexión de Firestore (destraba una cola de escrituras atascada). */
  fun resetConnection() {
    val db = firestore ?: return
    db.disableNetwork().addOnCompleteListener { db.enableNetwork() }
  }

  /** Se asegura de que la red de Firestore esté activa (idempotente); se usa cuando vuelve la señal. */
  fun nudgeNetwork() {
    val db = firestore ?: return
    runCatching { db.enableNetwork() }
  }

  fun leaveLobby(id: String, uid: String) {
    val db = firestore ?: return
    db.collection("lobbies").document(id).update(mapOf(
      "guestUids" to com.google.firebase.firestore.FieldValue.arrayRemove(uid),
      "players.$uid" to com.google.firebase.firestore.FieldValue.delete()
    ))
  }

  // ---------- Carreras: un documento por piloto en la subcolección "race" de la sala ----------
  fun observeRaceCars(roomId: String): Flow<Map<String, Map<String, Any?>>> {
    val db = firestore ?: return flowOf(emptyMap())
    return callbackFlow {
      val registration = roomRef(db, roomId).collection("race").addSnapshotListener { snap, error ->
        if (error != null) { Log.w("Firestore", "No se pudo escuchar a los pilotos", error); return@addSnapshotListener }
        val out = HashMap<String, Map<String, Any?>>()
        snap?.documents?.forEach { d -> d.data?.let { out[d.id] = it } }
        trySend(out)
      }
      awaitClose { registration.remove() }
    }
  }

  fun publishRaceCar(roomId: String, uid: String, data: Map<String, Any?>) {
    val db = firestore ?: return
    roomRef(db, roomId).collection("race").document(uid).set(data)
      .addOnFailureListener { Log.w("Firestore", "No se pudo publicar mi posición de carrera", it) }
  }

  // ---------- Acciones en vivo (puntería, selección, etc.): un documento por jugador en "live" ----------
  fun observeLive(roomId: String): Flow<Map<String, Map<String, Any?>>> {
    val db = firestore ?: return flowOf(emptyMap())
    return callbackFlow {
      val registration = roomRef(db, roomId).collection("live").addSnapshotListener { snap, error ->
        if (error != null) { Log.w("Firestore", "No se pudo escuchar lo que hacen los demás", error); return@addSnapshotListener }
        val out = HashMap<String, Map<String, Any?>>()
        snap?.documents?.forEach { d -> d.data?.let { out[d.id] = it } }
        trySend(out)
      }
      awaitClose { registration.remove() }
    }
  }

  fun publishLive(roomId: String, uid: String, data: Map<String, Any?>) {
    val db = firestore ?: return
    roomRef(db, roomId).collection("live").document(uid).set(data)
  }

  // ---------- Chat de sala ----------
  fun observeRoomChat(roomId: String): Flow<List<Map<String, Any?>>> {
    val db = firestore ?: return flowOf(emptyList())
    return callbackFlow {
      val q = roomRef(db, roomId).collection("chat").orderBy("ts", Query.Direction.ASCENDING).limitToLast(80)
      val registration = q.addSnapshotListener { snap, error ->
        if (error != null) { Log.w("Firestore", "No se pudo escuchar el chat de la sala", error); return@addSnapshotListener }
        val out = snap?.documents?.map { d ->
          val ms = d.getDate("ts", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.time ?: (d.getLong("t") ?: 0L)
          (d.data ?: emptyMap<String, Any?>()) + mapOf("id" to d.id, "tms" to ms)
        } ?: emptyList()
        trySend(out)
      }
      awaitClose { registration.remove() }
    }
  }

  fun sendRoomChat(roomId: String, data: Map<String, Any?>, onError: (Exception) -> Unit = {}) {
    val db = firestore ?: return
    roomRef(db, roomId).collection("chat").add(data + mapOf("ts" to FieldValue.serverTimestamp(), "t" to System.currentTimeMillis()))
      .addOnFailureListener { Log.w("Firestore", "No se pudo enviar al chat", it); onError(it) }
  }

  // ---------- Llamada de voz grupal ----------
  fun observeCallMembers(roomId: String): Flow<List<Map<String, Any?>>> {
    val db = firestore ?: return flowOf(emptyList())
    return callbackFlow {
      val registration = roomRef(db, roomId).collection("gcall").addSnapshotListener { snap, error ->
        if (error != null) { Log.w("Firestore", "No se pudo escuchar la llamada", error); return@addSnapshotListener }
        val now = System.currentTimeMillis()
        val out = snap?.documents?.map { d ->
          val ms = d.getDate("ts", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.time ?: now
          (d.data ?: emptyMap<String, Any?>()) + mapOf("ageMs" to (now - ms).coerceAtLeast(0L))
        } ?: emptyList()
        trySend(out)
      }
      awaitClose { registration.remove() }
    }
  }

  fun setCallMember(roomId: String, uid: String, data: Map<String, Any?>) {
    val db = firestore ?: return
    roomRef(db, roomId).collection("gcall").document(uid).set(data + mapOf("ts" to FieldValue.serverTimestamp()))
      .addOnFailureListener { Log.w("Firestore", "No se pudo publicar mi presencia en la llamada", it) }
  }

  fun removeCallMember(roomId: String, uid: String) {
    val db = firestore ?: return
    roomRef(db, roomId).collection("gcall").document(uid).delete()
  }

  /** Mensajes de señalización dirigidos a mí (se entregan una sola vez cuando aparecen). */
  fun observeCallSignals(roomId: String, uid: String): Flow<List<Map<String, Any?>>> {
    val db = firestore ?: return flowOf(emptyList())
    return callbackFlow {
      val registration = roomRef(db, roomId).collection("gsig").whereEqualTo("to", uid).addSnapshotListener { snap, error ->
        if (error != null) { Log.w("Firestore", "No se pudo escuchar la señalización", error); return@addSnapshotListener }
        val added = snap?.documentChanges?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }?.map { ch ->
          (ch.document.data) + mapOf("id" to ch.document.id)
        } ?: emptyList()
        if (added.isNotEmpty()) trySend(added)
      }
      awaitClose { registration.remove() }
    }
  }

  fun sendCallSignal(roomId: String, data: Map<String, Any?>) {
    val db = firestore ?: return
    roomRef(db, roomId).collection("gsig").add(data + mapOf("t" to System.currentTimeMillis()))
      .addOnFailureListener { Log.w("Firestore", "No se pudo enviar señal de llamada", it) }
  }

  fun deleteCallSignal(roomId: String, id: String) {
    val db = firestore ?: return
    roomRef(db, roomId).collection("gsig").document(id).delete()
  }

  fun signOutFirebase() { auth?.signOut() }

}
