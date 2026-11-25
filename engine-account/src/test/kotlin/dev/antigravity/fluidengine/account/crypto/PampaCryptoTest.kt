package dev.antigravity.fluidengine.account.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Le primitive con Bouncy Castle: i vettori di RFC 5869 (HKDF-SHA256, casi 1–3, letti dal testo dell'RFC il 25/11),
 * AES-256-GCM coi dati associati, la busta del contratto, e le etichette confrontate col contratto del server.
 */
class PampaCryptoTest {

  @Test
  fun `HKDF-SHA256, RFC 5869 caso 1`() {
    val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
    val salt = hex("000102030405060708090a0b0c")
    val info = hex("f0f1f2f3f4f5f6f7f8f9")
    val okm = PampaCrypto.hkdfRaw(ikm, salt, info, 42)
    assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.hex())
    // Il PRK dell'RFC è l'HMAC del sale sull'IKM: la stessa primitiva che usano tag, check e link.
    assertEquals("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5", PampaCrypto.hmacSha256(salt, ikm).hex())
  }

  @Test
  fun `HKDF-SHA256, RFC 5869 caso 2, ingressi e uscita lunghi`() {
    val ikm = ByteArray(80) { it.toByte() }
    val salt = ByteArray(80) { (0x60 + it).toByte() }
    val info = ByteArray(80) { (0xb0 + it).toByte() }
    assertEquals(
      "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09" +
        "da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87",
      PampaCrypto.hkdfRaw(ikm, salt, info, 82).hex(),
    )
    assertEquals("06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244", PampaCrypto.hmacSha256(salt, ikm).hex())
  }

  @Test
  fun `HKDF-SHA256, RFC 5869 caso 3, sale e info vuoti come nel server`() {
    val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
    val okm = PampaCrypto.hkdfRaw(ikm, PampaCrypto.EMPTY, PampaCrypto.EMPTY, 42)
    assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8", okm.hex())
    // Il sale vuoto vale come 32 zeri (RFC 5869 § 2.2): è così che lo tratta WebCrypto nel server.
    assertArrayEquals(okm, PampaCrypto.hkdfRaw(ikm, ByteArray(32), PampaCrypto.EMPTY, 42))
  }

  @Test
  fun `due info diversi danno uscite indipendenti, come vuole S1`() {
    val code = PampaCrypto.random(16)
    val auth = PampaCrypto.hkdf(code, info = PampaLabels.RECOVERY_AUTH)
    val seed = PampaCrypto.hkdf(code, PampaCrypto.random(16), PampaLabels.DOOR_SEED)
    assertEquals(32, auth.size)
    assertFalse(auth.contentEquals(seed))
  }

  @Test
  fun `AES-256-GCM apre solo con la stessa chiave e gli stessi dati associati`() {
    val key = PampaCrypto.random(32)
    val nonce = PampaCrypto.random(12)
    val sealed = PampaCrypto.aesGcmSeal(key, nonce, "ciao".toByteArray(), "a|b".toByteArray())
    assertEquals(4 + 16, sealed.size)
    assertEquals("ciao", PampaCrypto.aesGcmOpen(key, nonce, sealed, "a|b".toByteArray()).decodeToString())
    expectFailure { PampaCrypto.aesGcmOpen(key, nonce, sealed, "a|c".toByteArray()) }
    expectFailure { PampaCrypto.aesGcmOpen(PampaCrypto.random(32), nonce, sealed, "a|b".toByteArray()) }
    val touched = sealed.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
    expectFailure { PampaCrypto.aesGcmOpen(key, nonce, touched, "a|b".toByteArray()) }
  }

  @Test
  fun `AES-256-GCM, vettore NIST col tag in coda`() {
    // GCM spec (McGrew e Viega), caso 16: AES-256, nonce da 96 bit, dati associati (Codex prova il 13 e il 14).
    // Ricontrollato il 25/11 con la libreria `cryptography` di Python.
    val key = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
    val iv = hex("cafebabefacedbaddecaf888")
    val pt = hex(
      "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525" +
        "b16aedf5aa0de657ba637b39",
    )
    val aad = hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")
    val ct = PampaCrypto.aesGcmSeal(key, iv, pt, aad)
    assertEquals(
      "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838" +
        "c5f61e6393ba7a0abcc9f662" + "76fc6ece0f4e1768cddf8853bb2d551b",
      ct.hex(),
    )
  }

  @Test
  fun `la busta del contratto va e torna, e non si sposta`() {
    val key = PampaCrypto.random(32)
    val cipher = PampaRecordCipher("acc_1", "dev.pampa.pampai")
    val field = cipher.seal(key, 2, "memories", "r1", 3, "ricordo".toByteArray())
    val json = PampaBase64.decode(field).decodeToString()
    val obj = Json.parseToJsonElement(json).jsonObject
    assertEquals(listOf("v", "e", "alg", "n", "c"), obj.keys.toList())
    assertEquals("A256GCM", obj["alg"]!!.jsonPrimitive.content)
    assertEquals("2", obj["e"]!!.jsonPrimitive.content)
    val keys = mapOf(2 to key)
    assertEquals("ricordo", cipher.open(keys::get, "memories", "r1", 3, field).decodeToString())
    // Spostata in un'altra riga, in un'altra raccolta o a un'altra versione: non si apre (K4).
    expectFailure { PampaRecordCipher("acc_1", "dev.pampa.pampai").open(keys::get, "memories", "r2", 3, field) }
    expectFailure { PampaRecordCipher("acc_1", "dev.pampa.pampai").open(keys::get, "luoghi", "r1", 3, field) }
    expectFailure { PampaRecordCipher("acc_1", "dev.pampa.pampai").open(keys::get, "memories", "r1", 4, field) }
    expectFailure { PampaRecordCipher("acc_1", "dev.antigravity.fluidtransit").open(keys::get, "memories", "r1", 3, field) }
    expectFailure { PampaRecordCipher("acc_2", "dev.pampa.pampai").open(keys::get, "memories", "r1", 3, field) }
  }

  @Test
  fun `una versione vecchia rimessa dal server viene scoperta (M12)`() {
    val key = PampaCrypto.random(32)
    val writer = PampaRecordCipher("acc_1", "dev.pampa.pampai")
    val v3 = writer.seal(key, 1, "c", "r", 3, "tre".toByteArray())
    val v4 = writer.seal(key, 1, "c", "r", 4, "quattro".toByteArray())
    val reader = PampaRecordCipher("acc_1", "dev.pampa.pampai")
    assertEquals("quattro", reader.open({ key }, "c", "r", 4, v4).decodeToString())
    expectFailure { reader.open({ key }, "c", "r", 3, v3) }
    // Le versioni viste si salvano e si rimettono.
    val again = PampaRecordCipher("acc_1", "dev.pampa.pampai").apply { remember(reader.lastSeen()) }
    expectFailure { again.open({ key }, "c", "r", 3, v3) }
  }

  @Test
  fun `una busta di un formato che non conosco si rifiuta`() {
    val bad = listOf(
      """{"v":2,"e":1,"alg":"A256GCM","n":"AAAAAAAAAAAAAAAA","c":"AA"}""",
      """{"v":1,"e":0,"alg":"A256GCM","n":"AAAAAAAAAAAAAAAA","c":"AA"}""",
      """{"v":1,"e":1,"alg":"A128GCM","n":"AAAAAAAAAAAAAAAA","c":"AA"}""",
      """{"v":1,"e":1,"alg":"A256GCM","n":"AAAA","c":"AA"}""",
      """[]""",
    )
    for (json in bad) expectFailure { PampaEnvelope.parseJson(json) }
  }

  @Test
  fun `le etichette sono quelle del contratto del server`() {
    val contract = Json.parseToJsonElement(resource("contract/e2e.json")).jsonObject
    val labels = contract["labels"]!!.jsonObject
    val mine = mapOf(
      "recoveryAuth" to PampaLabels.RECOVERY_AUTH,
      "doorSeed" to PampaLabels.DOOR_SEED,
      "doorSeal" to PampaLabels.DOOR_SEAL,
      "doorTag" to PampaLabels.DOOR_TAG,
      "doorCheck" to PampaLabels.DOOR_CHECK,
      "epochLink" to PampaLabels.EPOCH_LINK,
      "appKey" to PampaLabels.APP_KEY,
      "backupKey" to PampaLabels.BACKUP_KEY,
      "sharedKey" to PampaLabels.SHARED_KEY,
      "transferSeal" to PampaLabels.TRANSFER_SEAL,
      "transferMac" to PampaLabels.TRANSFER_MAC,
      "prfInput" to "SHA-256(\"${PampaLabels.PRF_INPUT_TEXT}\")",
    )
    assertEquals("ogni etichetta del contratto ha la sua costante", labels.keys, mine.keys)
    for ((k, v) in mine) assertEquals(k, labels[k]!!.jsonPrimitive.content, v)
    val inputs = contract["inputs"]!!.jsonObject
    assertEquals("utf8(\"<accountId>|<package>|<collection>|<recordId>|<version>\")", inputs["recordAad"]!!.jsonPrimitive.content)
    assertEquals("a|p|c|r|7", PampaLabels.recordAad("a", "p", "c", "r", 7).decodeToString())
    assertEquals("utf8(\"<accountId>|<package>|backup|<n>|<epoch>\")", inputs["backupAad"]!!.jsonPrimitive.content)
    assertEquals("a|p|backup|2|5", PampaLabels.backupAad("a", "p", 2, 5).decodeToString())
    val hpke = contract["hpke"] as JsonObject
    assertEquals("base", hpke["mode"]!!.jsonPrimitive.content)
    assertEquals("32", hpke["kemId"]!!.jsonPrimitive.content)
    assertEquals("1", hpke["kdfId"]!!.jsonPrimitive.content)
    assertEquals("2", hpke["aeadId"]!!.jsonPrimitive.content)
  }

  @Test
  fun `i segnaposto si riempiono tutti, e uno mancante è un errore`() {
    assertEquals("pampa/v1/door-seal|recovery|3", PampaLabels.fill(PampaLabels.DOOR_SEAL, "doorId" to "recovery", "epoch" to 3))
    assertEquals("pampa/v1/app/dev.pampa.pampai", PampaLabels.fill(PampaLabels.APP_KEY, "package" to "dev.pampa.pampai"))
    try {
      PampaLabels.fill(PampaLabels.DOOR_SEAL, "doorId" to "recovery")
      fail("doveva mancare <epoch>")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun `base64url senza riempimento, e niente altro`() {
    val bytes = byteArrayOf(-5, -1, 0, 1)
    val text = PampaBase64.encode(bytes)
    assertFalse(text.contains('=') || text.contains('+') || text.contains('/'))
    assertArrayEquals(bytes, PampaBase64.decode(text))
    assertArrayEquals(bytes, PampaBase64.decode("$text=="))
    try {
      PampaBase64.decode("a+b/")
      fail("non è base64url")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun `il confronto a tempo costante`() {
    val a = PampaCrypto.random(32)
    assertTrue(PampaCrypto.equal(a, a.copyOf()))
    assertNotEquals(true, PampaCrypto.equal(a, PampaCrypto.random(32)))
  }

  private fun expectFailure(block: () -> Unit) {
    try {
      block()
      fail("doveva rifiutare")
    } catch (_: PampaCryptoException) {
    }
  }

  private fun resource(path: String): String =
    requireNotNull(javaClass.classLoader!!.getResourceAsStream(path)) { "manca $path" }.use { it.readBytes().decodeToString() }
}

internal fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
