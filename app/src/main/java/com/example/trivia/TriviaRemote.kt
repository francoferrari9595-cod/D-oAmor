package com.example.trivia

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal data class RemoteVerdict(val verdict: TVerdict, val explanation: String, val usedWeb: Boolean, val sources: List<String>)

/**
 * Pide al servidor (Cloud Function «verifyTriviaAnswer») que juzgue la respuesta con IA y, si hace falta, búsqueda web.
 * La clave de la API NO está en la app: vive como secreto en Firebase. «usedWeb» lo informa el servidor a partir de las
 * búsquedas realmente ejecutadas; la app nunca lo supone. Si falla la conexión devuelve Result.failure.
 */
internal suspend fun tVerifyRemote(q: TQ, given: String, second: Boolean): Result<RemoteVerdict> {
  val fn = runCatching { com.google.firebase.functions.FirebaseFunctions.getInstance() }.getOrNull()
    ?: return Result.failure(IllegalStateException("Firebase Functions no está disponible"))
  val data = hashMapOf<String, Any>(
    "question" to q.q, "expected" to q.a, "aliases" to q.alt, "given" to given, "category" to tCat(q.cat).name, "second" to second
  )
  val res = withTimeoutOrNull(45_000) {
    suspendCancellableCoroutine<Result<RemoteVerdict>> { cont ->
      try {
        fn.getHttpsCallable("verifyTriviaAnswer").call(data)
          .addOnSuccessListener { r ->
            val m = r.getData() as? Map<*, *>
            if (m == null) { if (cont.isActive) cont.resume(Result.failure(IllegalStateException("Respuesta vacía"))); return@addOnSuccessListener }
            val v = when (m["verdict"]) { "correct" -> TVerdict.CORRECT; "incorrect" -> TVerdict.INCORRECT; else -> TVerdict.AMBIGUOUS }
            val src = (m["sources"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val rv = RemoteVerdict(v, (m["explanation"] as? String).orEmpty(), (m["usedWeb"] as? Boolean) == true, src)
            if (cont.isActive) cont.resume(Result.success(rv))
          }
          .addOnFailureListener { e -> if (cont.isActive) cont.resume(Result.failure(e)) }
      } catch (e: Exception) { if (cont.isActive) cont.resume(Result.failure(e)) }
    }
  }
  return res ?: Result.failure(IllegalStateException("El verificador tardó demasiado"))
}
