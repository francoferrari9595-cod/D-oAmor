package com.example.data.firestore

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.FirebaseFirestoreSettings

/** Usa la base predeterminada del proyecto configurado en google-services.json. */
object FirestoreInstance {
  private var configured = false

  @Synchronized
  @Suppress("UNUSED_PARAMETER")
  fun get(context: Context): FirebaseFirestore {
    val db = FirebaseFirestore.getInstance()
    if (!configured) {
      configured = true
      // Caché solo en memoria: una cola de escrituras vieja guardada en disco (de versiones anteriores) no puede
      // bloquear los envíos nuevos. Si Firestore ya había arrancado, se ignora sin romper nada.
      runCatching {
        db.firestoreSettings = FirebaseFirestoreSettings.Builder(db.firestoreSettings)
          .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
      }
      runCatching { db.enableNetwork() }
    }
    return db
  }
}
