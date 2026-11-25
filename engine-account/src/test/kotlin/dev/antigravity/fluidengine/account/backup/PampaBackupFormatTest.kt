package dev.antigravity.fluidengine.account.backup

import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Il formato del backup per app (`b\chiavi.md` § 7): CBOR deterministico, chiusura legata al suo posto, tetto di 5 MB. */
class PampaBackupFormatTest {

  private fun pkg(text: String = "ciao", createdAt: Long = 1_795_000_000_000) = BackupPackage(
    app = "dev.pampa.fluidweather",
    appVersion = "2.3.0",
    schema = 3,
    createdAt = createdAt,
    files = listOf(BackupFile("localita.json", """["Firenze","Prato"]""".toByteArray()), BackupFile("note.txt", text.toByteArray())),
  )

  @Test
  fun `il pacchetto va e torna, e gli stessi dati danno gli stessi byte`() {
    val p = pkg()
    assertEquals(p, PampaBackupFormat.decode(PampaBackupFormat.encode(p)))
    assertArrayEquals(PampaBackupFormat.encode(p), PampaBackupFormat.encode(p.copy()))
    // L'impronta non guarda la data: un backup uguale di ieri non riparte.
    assertArrayEquals(PampaBackupFormat.fingerprint(p), PampaBackupFormat.fingerprint(pkg(createdAt = 5)))
    assertFalse(PampaBackupFormat.fingerprint(p).contentEquals(PampaBackupFormat.fingerprint(pkg("altro"))))
  }

  @Test
  fun `CBOR deterministico - chiavi in ordine, lunghezze corte, vettore a mano`() {
    // {"a": 1, "bb": h'00', "c": [true, null]} → a3 61 61 01 61 63 82 f5 f6 62 62 62 41 00
    val bytes = PampaCbor.encode(mapOf("bb" to byteArrayOf(0), "c" to listOf(true, null), "a" to 1L))
    assertEquals("a36161016163 82f5f6 62626241 00".replace(" ", ""), bytes.joinToString("") { "%02x".format(it) })
    assertEquals(listOf(1L, -1L, 300L, -301L, 70000L, 5_000_000_000L), PampaCbor.decode(PampaCbor.encode(listOf(1L, -1L, 300L, -301L, 70000L, 5_000_000_000L))))
  }

  @Test
  fun `chiuso col suo posto - un altro numero, un'altra app, un altro account non lo aprono`() {
    val key = PampaCrypto.random(32)
    val blob = PampaBackupFormat.seal(pkg(), key, epoch = 2, accountId = "acc_1", n = 4)
    assertEquals(2, PampaBackupFormat.epochOf(blob))
    assertFalse("il contenuto non si legge sul server", blob.decodeToString().contains("Firenze"))
    val keys = mapOf(2 to key)
    assertEquals(pkg(), PampaBackupFormat.open(blob, keys::get, "acc_1", "dev.pampa.fluidweather", 4))
    refuse { PampaBackupFormat.open(blob, keys::get, "acc_1", "dev.pampa.fluidweather", 3) }
    refuse { PampaBackupFormat.open(blob, keys::get, "acc_1", "dev.pampa.pampai", 4) }
    refuse { PampaBackupFormat.open(blob, keys::get, "acc_2", "dev.pampa.fluidweather", 4) }
    refuse { PampaBackupFormat.open(blob, mapOf(1 to key)::get, "acc_1", "dev.pampa.fluidweather", 4) }
    // L'epoca nella testa cambiata dal server: la chiave sbagliata, o i dati associati sbagliati.
    val touched = blob.copyOf().also { it[7] = 1 }
    refuse { PampaBackupFormat.open(touched, mapOf(1 to key, 2 to key)::get, "acc_1", "dev.pampa.fluidweather", 4) }
    refuse { PampaBackupFormat.epochOf("non un backup".toByteArray()) }
  }

  @Test
  fun `oltre i 5 MB non si chiude`() {
    val big = pkg().copy(files = listOf(BackupFile("grande", ByteArray(5 * 1024 * 1024))))
    try {
      PampaBackupFormat.seal(big, PampaCrypto.random(32), 1, "acc", 1)
      fail("doveva rifiutare")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message!!.contains("oltre"))
    }
  }

  @Test
  fun `un pacchetto di un'altra versione si rifiuta, uno schema vecchio dell'app si legge`() {
    val other = PampaCbor.encode(mapOf("v" to 2L, "app" to "x", "schema" to 1L, "files" to emptyList<Any>()))
    refuse { PampaBackupFormat.decode(other) }
    val old = PampaBackupFormat.decode(PampaBackupFormat.encode(pkg().copy(schema = 1)))
    assertEquals(1, old.schema)
  }

  private fun refuse(block: () -> Unit) {
    try {
      block()
      fail("doveva rifiutare")
    } catch (_: PampaCryptoException) {
    }
  }
}
