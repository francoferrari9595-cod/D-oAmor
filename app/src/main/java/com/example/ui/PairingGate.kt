package com.example.ui

import android.util.Log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.ui.components.AuroraBackground
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.example.data.firestore.FirestoreInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

private class CodeTakenException : Exception("CODE_TAKEN")

@Composable
fun PairingGate(onPaired: (String) -> Unit, content: @Composable () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var ready by remember { mutableStateOf(false) }
  var paired by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(true) }
  var uiError by remember { mutableStateOf<String?>(null) }
  var code by remember { mutableStateOf("") }
  var myCode by remember { mutableStateOf<String?>(null) }
  var pendingCoupleId by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val auth = remember { runCatching { FirebaseAuth.getInstance() }.getOrNull() }
  val db = remember(context) { runCatching { FirestoreInstance.get(context) }.getOrNull() }

  LaunchedEffect(pendingCoupleId, db, auth) {
    val pendingId = pendingCoupleId
    if (pendingId != null && db != null && auth?.currentUser != null) {
      val registration = db.collection("couples").document(pendingId).addSnapshotListener { snapshot, _ ->
        val members = snapshot?.get("memberUids") as? List<*>
        if (members != null && members.filterIsInstance<String>().size == 2) {
          context.getSharedPreferences("doamor_pairing", android.content.Context.MODE_PRIVATE).edit().putString("coupleId", pendingId).apply()
          onPaired(pendingId)
          paired = true
        }
      }
      try { awaitCancellation() } finally { registration.remove() }
    }
  }

  LaunchedEffect(Unit) {
    try {
      if (FirebaseApp.getApps(context).isEmpty() || auth == null || db == null) {
        uiError = "No se pudo inicializar Firebase. Comprueba la configuración de la aplicación."
      } else {
        if (auth.currentUser == null) auth.signInAnonymously().await()
        val uid = auth.currentUser!!.uid
        val userDoc = db.collection("users").document(uid)
        val snap = userDoc.get().await()
        val coupleId = snap.getString("coupleId")
        if (!coupleId.isNullOrBlank()) {
          val couple = db.collection("couples").document(coupleId).get().await()
          val members = (couple.get("memberUids") as? List<*>).orEmpty().filterIsInstance<String>()
          // Solo habilitar la app cuando ambos teléfonos están vinculados. Antes se
          // permitía entrar al creador con un único miembro y parecía una pareja completa.
          if (uid in members && members.size == 2) {
            context.getSharedPreferences("doamor_pairing", android.content.Context.MODE_PRIVATE).edit().putString("coupleId", coupleId).apply()
            onPaired(coupleId)
            paired = true
          } else if (uid in members && members.size == 1) {
            // Recuperar el código pendiente después de cerrar/reabrir la app para que
            // el primer integrante no pierda la invitación que ya había creado.
            val invitation = db.collection("invitations")
              .whereEqualTo("coupleId", coupleId)
              .whereEqualTo("ownerUid", uid)
              .whereEqualTo("status", "pending")
              .limit(1).get().await().documents.firstOrNull()
            val expiresAt = invitation?.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
            if (invitation != null && expiresAt > System.currentTimeMillis()) {
              myCode = invitation.getString("code")
              pendingCoupleId = coupleId
            } else {
              uiError = "Tu invitación venció. Crea un código nuevo para vincular a tu pareja."
            }
          }
        }
      }
    } catch (e: Exception) { uiError = e.localizedMessage ?: "No se pudo conectar con Firebase." }
    loading = false
  }

  if (paired) { content(); return }
  AuroraBackground {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
      Text("💞", fontSize = 56.sp)
      com.example.ui.components.SiniJossTitle(34.sp)
      Spacer(Modifier.height(8.dp))
      Text("Un espacio solo para ustedes dos: chat, llamadas, zumbador y juegos en vivo.", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE2D8F5), textAlign = TextAlign.Center)
      Spacer(Modifier.height(24.dp))
      if (loading) CircularProgressIndicator()
      else {
        Text("Vincular dos teléfonos", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text("Una persona crea una invitación y comparte el código con su pareja. La otra lo introduce una sola vez.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        Button(onClick = {
          scope.launch {
            loading = true; uiError = null
            try {
              val a = auth ?: error("Firebase Auth no disponible")
              val d = db ?: error("Firestore no disponible")
              if (a.currentUser == null) a.signInAnonymously().await()
              val uid = a.currentUser!!.uid
              val userRef = d.collection("users").document(uid)
              val existing = userRef.get().await().getString("coupleId")
              if (!existing.isNullOrBlank()) { paired = true }
              else {
                // Código de invitación: 4 dígitos. El identificador REAL de la pareja es un valor largo y
                // aleatorio (así nadie puede adivinar tu chat probando códigos de 4 dígitos).
                val random = SecureRandom()
                val idChars = "abcdefghijklmnopqrstuvwxyz0123456789"
                var createdCode: String? = null
                var createdCouple: String? = null
                var attempts = 0
                while (createdCode == null && attempts < 30) {
                  attempts++
                  val invitation = random.nextInt(10000).toString().padStart(4, '0')
                  val newCoupleId = (1..24).map { idChars[random.nextInt(idChars.length)] }.joinToString("")
                  val ref = d.collection("invitations").document(invitation)
                  val coupleRef = d.collection("couples").document(newCoupleId)
                  try {
                    d.runTransaction { tx ->
                      val ex = tx.get(ref)
                      val taken = ex.exists() && ex.getString("status") == "pending" &&
                        (ex.getTimestamp("expiresAt")?.toDate()?.time ?: 0L) > System.currentTimeMillis()
                      if (taken) throw CodeTakenException()
                      tx.set(coupleRef, mapOf("memberUids" to listOf(uid), "createdAt" to FieldValue.serverTimestamp(), "relationshipStartedAt" to null))
                      tx.set(userRef, mapOf("coupleId" to coupleRef.id, "displayName" to "", "updatedAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
                      tx.set(ref, mapOf("code" to invitation, "ownerUid" to uid, "coupleId" to coupleRef.id, "status" to "pending", "createdAt" to FieldValue.serverTimestamp(), "expiresAt" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + 24*60*60*1000))))
                      null
                    }.await()
                    createdCode = invitation
                    createdCouple = coupleRef.id
                  } catch (e: Exception) {
                    if (generateSequence<Throwable>(e) { it.cause }.none { it is CodeTakenException }) throw e
                  }
                }
                val invitation = createdCode ?: error("No se pudo generar un código libre. Inténtalo otra vez.")
                myCode = invitation
                pendingCoupleId = createdCouple
              }
            } catch (e: Exception) { uiError = e.localizedMessage ?: "No se pudo crear la invitación." }
            loading = false
          }
        }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Crear código de invitación", fontWeight = FontWeight.ExtraBold) }
        myCode?.let { invite ->
          Spacer(Modifier.height(10.dp))
          Text("Tu código", style = MaterialTheme.typography.titleMedium)
          Text(invite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
          Text("Compártelo únicamente con tu pareja. Caduca en 24 horas.")
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = code, onValueChange = { code = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Código de tu pareja (4 dígitos)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
          scope.launch {
            loading = true; uiError = null
            try {
              val a = auth ?: error("Firebase Auth no disponible")
              val d = db ?: error("Firestore no disponible")
              if (a.currentUser == null) a.signInAnonymously().await()
              val uid = a.currentUser!!.uid
              val inviteRef = d.collection("invitations").document(code.trim())
              val userRef = d.collection("users").document(uid)
              var joinedCoupleId = ""
              d.runTransaction { tx ->
                val invite = tx.get(inviteRef)
                check(invite.exists()) { "El código no existe o ya venció." }
                check(invite.getString("status") == "pending") { "Este código ya fue utilizado." }
                check(invite.getTimestamp("expiresAt")?.toDate()?.time?.let { it > System.currentTimeMillis() } == true) { "El código venció. Pide uno nuevo." }
                val owner = invite.getString("ownerUid") ?: error("Invitación inválida")
                check(owner != uid) { "No puedes vincularte contigo mismo." }
                val coupleId = invite.getString("coupleId") ?: error("Invitación inválida")
                joinedCoupleId = coupleId
                val coupleRef = d.collection("couples").document(coupleId)
                val couple = tx.get(coupleRef)
                val members = (couple.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                check(members.size == 1 && members[0] == owner) { "Esta invitación ya no está disponible." }
                val current = tx.get(userRef)
                check(current.getString("coupleId").isNullOrBlank()) { "Esta cuenta ya tiene una pareja vinculada." }
                tx.update(coupleRef, mapOf("memberUids" to listOf(owner, uid), "relationshipStartedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
                tx.set(userRef, mapOf("coupleId" to coupleId, "displayName" to "", "updatedAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
                tx.update(inviteRef, mapOf("status" to "used", "usedBy" to uid, "usedAt" to FieldValue.serverTimestamp()))
                null
              }.await()
              check(joinedCoupleId.isNotBlank()) { "No se pudo completar la vinculación." }
              context.getSharedPreferences("doamor_pairing", android.content.Context.MODE_PRIVATE).edit().putString("coupleId", joinedCoupleId).apply()
              onPaired(joinedCoupleId)
              paired = true
            } catch (e: Exception) {
              Log.e("D-oAmor-Pairing", "Falló la vinculación del código introducido", e)
              uiError = e.localizedMessage ?: "No se pudo completar la vinculación. Comprueba la conexión e inténtalo de nuevo."
            }
            loading = false
          }
        }, enabled = code.length == 4, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Vincular con este código", fontWeight = FontWeight.ExtraBold) }
        uiError?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
      }
    }
  }
}
