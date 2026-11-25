package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * T9, il primo giorno: l'HPKE «base» di Bouncy Castle passa i vettori dell'RFC 9180.
 *  - L'appendice A.1.1 (X25519, HKDF-SHA256, AES-128-GCM), riletta nel testo dell'RFC il 25/11.
 *  - La suite di Pampa (X25519, HKDF-SHA256, **AES-256-GCM**), che nell'appendice non c'è: viene dai vettori ufficiali
 *    del gruppo CFRG (`test-vectors.json`, gli stessi da cui nasce l'appendice; la voce aead 1 è identica all'A.1.1).
 * Per ogni suite: la pubblica dalla privata, il sigillo deterministico con l'effimera fissa (`enc`), le 257 (o 6)
 * cifrature in fila col numero di sequenza, e l'apertura dal lato di chi riceve.
 */
class HpkeVectorsTest {

  private val suites = Json.parseToJsonElement(resource("vettori/hpke-rfc9180.json")).jsonObject["suites"]!!.jsonArray.map { it.jsonObject }

  @Test
  fun `le due suite ci sono, con la modalita' base e X25519`() {
    assertEquals(listOf(1, 2), suites.map { it.int("aead_id") })
    for (s in suites) {
      assertEquals(0, s.int("mode"))
      assertEquals(0x20, s.int("kem_id"))
      assertEquals(1, s.int("kdf_id"))
    }
  }

  @Test
  fun `RFC 9180 A_1_1 e la suite AES-256-GCM, dal lato di chi manda`() {
    for (s in suites) {
      val hpke = PampaHpke.suiteFor(s.int("aead_id"))
      val skE = X25519KeyPair.fromSeed(hex(s.str("skEm")))
      val skR = X25519KeyPair.fromSeed(hex(s.str("skRm")))
      assertEquals("pkEm", s.str("pkEm"), skE.publicKey.hex())
      assertEquals("pkRm", s.str("pkRm"), skR.publicKey.hex())
      val info = hex(s.str("info"))
      val pkR = hpke.deserializePublicKey(skR.publicKey)
      val ctx = hpke.setupBaseS(pkR, info, skE.asHpke(hpke))
      assertEquals("enc", s.str("enc"), ctx.encapsulation.hex())
      var expectedSeq = 0
      for (e in s["encryptions"]!!.jsonArray.map { it.jsonObject }) {
        val seq = e.int("seq")
        // Il contesto conta da sé: per saltare alle sequenze 255 e 256 si cifra quello che sta in mezzo.
        while (expectedSeq < seq) {
          ctx.seal(ByteArray(0), ByteArray(0)); expectedSeq++
        }
        assertEquals("aead ${s.int("aead_id")}, sequenza $seq", e.str("ct"), ctx.seal(hex(e.str("aad")), hex(e.str("pt"))).hex())
        expectedSeq++
      }
      // Gli export dell'RFC: provano il key schedule anche dove l'AEAD non arriva.
      for (x in s["exports"]!!.jsonArray.map { it.jsonObject }) {
        assertEquals(x.str("exported_value"), ctx.export(hex(x.str("exporter_context")), x.int("L")).hex())
      }
    }
  }

  @Test
  fun `RFC 9180 A_1_1 e la suite AES-256-GCM, dal lato di chi riceve`() {
    for (s in suites) {
      val hpke = PampaHpke.suiteFor(s.int("aead_id"))
      val skR = X25519KeyPair.fromSeed(hex(s.str("skRm")))
      val first = s["encryptions"]!!.jsonArray.first().jsonObject
      val sealed = hex(s.str("enc")) + hex(first.str("ct"))
      val plain = PampaHpke.openWith(hpke, skR, hex(s.str("info")), hex(first.str("aad")), sealed)
      assertArrayEquals(hex(first.str("pt")), plain)
    }
  }

  @Test
  fun `il sigillo della suite di Pampa con l'effimera fissa e' quello dei vettori`() {
    val s = suites.single { it.int("aead_id") == 2 }
    val first = s["encryptions"]!!.jsonArray.first().jsonObject
    val sealed = PampaHpke.sealWith(
      PampaHpke.suiteFor(2),
      recipient = hex(s.str("pkRm")),
      info = hex(s.str("info")),
      aad = hex(first.str("aad")),
      plaintext = hex(first.str("pt")),
      ephemeral = X25519KeyPair.fromSeed(hex(s.str("skEm"))),
    )
    assertEquals(s.str("enc") + first.str("ct"), sealed.hex())
  }

  @Test
  fun `un sigillo toccato, per un'altra chiave o con altri dati non si apre`() {
    val me = X25519KeyPair.random()
    val sealed = PampaHpke.seal(me.publicKey, "info".toByteArray(), ByteArray(0), "chiave".toByteArray())
    assertEquals("chiave", PampaHpke.open(me, "info".toByteArray(), ByteArray(0), sealed).decodeToString())
    refuse { PampaHpke.open(X25519KeyPair.random(), "info".toByteArray(), ByteArray(0), sealed) }
    refuse { PampaHpke.open(me, "altra".toByteArray(), ByteArray(0), sealed) }
    refuse { PampaHpke.open(me, "info".toByteArray(), "aad".toByteArray(), sealed) }
    refuse { PampaHpke.open(me, "info".toByteArray(), ByteArray(0), sealed.copyOf().also { it[40] = (it[40] + 1).toByte() }) }
    refuse { PampaHpke.open(me, "info".toByteArray(), ByteArray(0), sealed.copyOf(20)) }
  }

  private fun refuse(block: () -> Unit) {
    try {
      block()
      fail("doveva rifiutare")
    } catch (_: PampaCryptoException) {
    }
  }

  private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
  private fun JsonObject.int(k: String) = this[k]!!.jsonPrimitive.int
}

internal fun resource(path: String): String =
  requireNotNull(HpkeVectorsTest::class.java.classLoader!!.getResourceAsStream(path)) { "manca $path" }.use { it.readBytes().decodeToString() }

internal fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
