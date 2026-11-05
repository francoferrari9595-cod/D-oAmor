package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class GameInvitesTest {
  private fun lobby(vararg invited: Pair<String, Long>) = mapOf<String, Any>("game" to "basta", "host" to "h", "invited" to invited.toMap())

  @Test
  fun notInvitedHasNoStatus() {
    assertEquals(GameInvites.NONE, GameInvites.status(lobby(), emptyMap(), "a"))
  }

  @Test
  fun invitedWithoutReplyIsPending() {
    assertEquals(GameInvites.PENDING, GameInvites.status(lobby("a" to 10L), emptyMap(), "a"))
  }

  @Test
  fun replyMustMatchGameAndStamp() {
    val l = lobby("a" to 10L)
    val ok = mapOf<String, Any>("gReply_a" to mapOf("game" to "basta", "stamp" to 10L, "ok" to true))
    val stale = mapOf<String, Any>("gReply_a" to mapOf("game" to "basta", "stamp" to 5L, "ok" to true))
    val other = mapOf<String, Any>("gReply_a" to mapOf("game" to "ludo", "stamp" to 10L, "ok" to true))
    assertEquals(GameInvites.ACCEPTED, GameInvites.status(l, ok, "a"))
    assertEquals(GameInvites.PENDING, GameInvites.status(l, stale, "a"))
    assertEquals(GameInvites.PENDING, GameInvites.status(l, other, "a"))
  }

  @Test
  fun acceptedKeepsInvitationOrderAndSkipsDeclined() {
    val l = lobby("b" to 20L, "a" to 10L, "c" to 30L)
    val all = mapOf<String, Any>(
      "gReply_a" to mapOf("game" to "basta", "stamp" to 10L, "ok" to true),
      "gReply_b" to mapOf("game" to "basta", "stamp" to 20L, "ok" to true),
      "gReply_c" to mapOf("game" to "basta", "stamp" to 30L, "ok" to false)
    )
    assertEquals(listOf("a", "b"), GameInvites.accepted(l, all))
  }
}
