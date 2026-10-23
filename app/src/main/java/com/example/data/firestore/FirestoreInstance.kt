package com.example.data.firestore

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

/** Usa la base predeterminada del proyecto configurado en google-services.json. */
object FirestoreInstance {
  @Suppress("UNUSED_PARAMETER")
  fun get(context: Context): FirebaseFirestore = FirebaseFirestore.getInstance()
}
