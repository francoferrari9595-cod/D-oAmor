package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {

  // Firebase es opcional para que la aplicación no se cierre si todavía falta google-services.json.
  private val auth: FirebaseAuth? = runCatching {
    if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAuth.getInstance()
  }.getOrNull()
  private val credentialManager = CredentialManager.create(context)

  private val _currentUser = MutableStateFlow<FirebaseUser?>(auth?.currentUser)
  val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

  init {
    auth?.addAuthStateListener { firebaseAuth ->
      _currentUser.value = firebaseAuth.currentUser
    }
  }

  fun attemptAutoSignIn(
    scope: CoroutineScope,
    onSuccess: () -> Unit = {},
    onFailed: () -> Unit = {}
  ) {
    val firebaseAuth = auth
    if (firebaseAuth == null) { onFailed(); return }
    if (firebaseAuth.currentUser != null) {
      onSuccess()
      return
    }

    val serverClientId = getServerClientId()
    if (serverClientId == null) {
      onFailed()
      return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(serverClientId)
      .setAutoSelectEnabled(true)
      .build()

    val request = GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()

    scope.launch {
      try {
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          firebaseAuth.signInWithCredential(authCredential).await()
          onSuccess()
        } else {
          onFailed()
        }
      } catch (_: Exception) {
        onFailed()
      }
    }
  }

  fun signInWithGoogle(
    activity: Activity,
    scope: CoroutineScope,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onCancelled: () -> Unit = {}
  ) {
    val firebaseAuth = auth
    if (firebaseAuth == null) {
      onError("Firebase no está configurado todavía. Agrega google-services.json al proyecto.")
      return
    }
    val serverClientId = getServerClientId()
    if (serverClientId == null) {
      onError("No se encontró el ID de cliente de Google. Configura Firebase en este proyecto.")
      return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
      try {
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          firebaseAuth.signInWithCredential(authCredential).await()
          onSuccess()
        } else {
          onError("Credencial no compatible")
        }
      } catch (e: GetCredentialCancellationException) {
        Log.w("Auth", "Inicio de sesión con Google cancelado: ${e.message}", e)
        onCancelled()
      } catch (e: Exception) {
        Log.e("Auth", "Error de inicio de sesión", e)
        onError(e.localizedMessage ?: "Error de inicio de sesión")
      }
    }
  }

  // Firebase Google Services genera este recurso cuando existe un google-services.json válido.
  // La búsqueda dinámica permite compilar también en entornos donde todavía no se configuró Firebase.
  private fun getServerClientId(): String? {
    val resourceId = context.resources.getIdentifier(
      "default_web_client_id", "string", context.packageName
    )
    if (resourceId == 0) return null
    return runCatching { context.getString(resourceId) }
      .getOrNull()
      ?.takeIf { it.isNotBlank() }
  }

  fun signOut(scope: CoroutineScope, onComplete: () -> Unit = {}) {
    auth?.signOut()
    scope.launch {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (_: Exception) {}
      onComplete()
    }
  }
}
