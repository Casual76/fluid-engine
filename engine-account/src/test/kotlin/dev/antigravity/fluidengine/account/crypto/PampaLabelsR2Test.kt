package dev.antigravity.fluidengine.account.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 · I dati associati delle righe usano «|» senza lunghezze (`review\R2\muse-account.md`, varie): con un «|» nella
 * raccolta o nell'id, due righe diverse davano gli stessi dati associati, e il server poteva scambiarle.
 */
class PampaLabelsR2Test {
  @Test
  fun `una raccolta o un id con il separatore sono rifiutati dove nascono`() {
    // `a|b` + `c` e `a` + `b|c` darebbero gli stessi byte.
    assertTrue(runCatching { PampaLabels.recordAad("acc_1", "dev.pampa.notes", "a|b", "c", 1) }.exceptionOrNull() is PampaCryptoException)
    assertTrue(runCatching { PampaLabels.recordAad("acc_1", "dev.pampa.notes", "a", "b|c", 1) }.exceptionOrNull() is PampaCryptoException)
    val cipher = PampaRecordCipher("acc_1", "dev.pampa.notes")
    assertTrue(runCatching { cipher.seal(ByteArray(32), 1, "a", "b|c", 1, "x".toByteArray()) }.exceptionOrNull() is PampaCryptoException)
    // Senza separatore, i byte del contratto restano quelli.
    assertArrayEquals("acc_1|dev.pampa.notes|a|c|1".toByteArray(), PampaLabels.recordAad("acc_1", "dev.pampa.notes", "a", "c", 1))
  }
}
