package com.example.data.firestore

/**
 * Operaciones atómicas sobre el estado de los juegos que funcionan igual en Realtime Database
 * (donde viven ahora los juegos) y en Firestore (respaldo si Realtime no estuviera disponible).
 * Reemplazan a FieldValue.increment / arrayUnion / arrayRemove / delete en los juegos.
 */
sealed class GameOp {
  data class Increment(val by: Long) : GameOp()
  data class ArrayUnion(val items: List<Any>) : GameOp()
  data class ArrayRemove(val items: List<Any>) : GameOp()
  object Delete : GameOp()
}

object GameOps {
  fun increment(by: Long): GameOp = GameOp.Increment(by)
  fun arrayUnion(vararg items: Any): GameOp = GameOp.ArrayUnion(items.toList())
  fun arrayRemove(vararg items: Any): GameOp = GameOp.ArrayRemove(items.toList())
  fun delete(): GameOp = GameOp.Delete
}
