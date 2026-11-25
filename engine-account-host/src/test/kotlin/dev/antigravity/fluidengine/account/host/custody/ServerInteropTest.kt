package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaRecordCipher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La custodia dell'engine e il telefono finto del server parlano la stessa lingua, al byte.
 *
 * `vettori/telefono-finto.json` esce dal codice del server Pampa-account (l'HPKE di `test/fake/hpke.ts`, l'HKDF di
 * `src/crypto.ts`, le etichette di `src/e2e-contract.ts`, le formule di `test/fake/phone.ts`), con lo script
 * `vettori/genera-vettori.ts.txt`. Le formule deterministiche (auth, seme, pubblica, tag, check, link, chiavi delle app)
 * devono dare gli stessi byte; i sigilli e le buste, che hanno un'effimera o un nonce casuale, si devono aprire.
 */
class ServerInteropTest {

  private val v = Json.parseToJsonElement(resource("vettori/telefono-finto.json")).jsonObject
  private val accountId = v.str("accountId")
  private val cds = v["epochs"]!!.jsonArray.associate { it.jsonObject.int("epoch") to b(it.jsonObject.str("cd")) }

  @Test
  fun `auth del codice di recupero, come il server`() {
    val rec = v.obj("recovery")
    assertEquals(rec.str("auth"), PampaBase64.encode(CustodyMath.recoveryAuth(b(rec.str("code")))))
  }

  @Test
  fun `seme, pubblica, tag e check delle due porte, come il server`() {
    for ((secret, door) in listOf(b(v.obj("recovery").str("code")) to v.obj("recovery").obj("door"), b(v.obj("prf").str("output")) to v.obj("prf").obj("door"))) {
      val salt = b(door.str("salt"))
      assertEquals(door.str("seed"), PampaBase64.encode(CustodyMath.doorSeed(secret, salt)))
      assertEquals(door.str("pub"), PampaBase64.encode(CustodyMath.doorKeys(secret, salt).publicKey))
      assertEquals(door.str("tag1"), PampaBase64.encode(CustodyMath.tag(cds.getValue(1), door.str("doorId"), door.str("kind"), door.str("pub"), door.str("salt"), 1)))
      assertEquals(door.str("tag3"), PampaBase64.encode(CustodyMath.tag(cds.getValue(3), door.str("doorId"), door.str("kind"), door.str("pub"), door.str("salt"), 3)))
      assertEquals(door.str("check"), PampaBase64.encode(CustodyMath.check(secret, cds.getValue(1))))
    }
  }

  @Test
  fun `i sigilli delle porte fatti dal server si aprono con la custodia`() {
    for ((secret, door) in listOf(b(v.obj("recovery").str("code")) to v.obj("recovery").obj("door"), b(v.obj("prf").str("output")) to v.obj("prf").obj("door"))) {
      val keys = CustodyMath.doorKeys(secret, b(door.str("salt")))
      for (seal in door["seals"]!!.jsonArray.map { it.jsonObject }) {
        val epoch = seal.int("epoch")
        assertArrayEquals("epoca $epoch della porta ${door.str("doorId")}", cds.getValue(epoch), CustodyMath.openDoorSeal(keys, door.str("doorId"), epoch, b(seal.str("sealed"))))
      }
    }
  }

  @Test
  fun `la catena delle epoche, col tipo dentro, come il server`() {
    val links = v.obj("links")
    assertEquals(links.str("2"), PampaBase64.encode(CustodyMath.link(cds.getValue(1), 2, "normal", cds.getValue(2))))
    assertEquals(links.str("3"), PampaBase64.encode(CustodyMath.link(cds.getValue(2), 3, "lost", cds.getValue(3))))
  }

  @Test
  fun `chiavi dell'app, del backup e dei luoghi, come il server`() {
    val k = v.obj("keys")
    val cd = cds.getValue(k.int("epoch"))
    assertEquals(k.str("app"), PampaBase64.encode(CustodyMath.appKey(cd, accountId, k.str("package"))))
    assertEquals(k.str("backup"), PampaBase64.encode(CustodyMath.backupKey(cd, accountId, k.str("package"))))
    assertEquals(k.str("places"), PampaBase64.encode(CustodyMath.sharedKey(cd, accountId, "places")))
  }

  @Test
  fun `una riga cifrata dal telefono finto si apre con la busta dell'engine`() {
    val r = v.obj("record")
    val k = v.obj("keys")
    val appKey = b(k.str("app"))
    val plain = PampaRecordCipher(accountId, k.str("package")).open({ e -> if (e == 2) appKey else null }, r.str("collection"), r.str("recordId"), r.int("version").toLong(), r.str("ciphertext"))
    assertEquals(r.str("plain"), plain.decodeToString())
  }

  @Test
  fun `il passaggio col QR fatto dal server si verifica e si apre`() {
    val t = v.obj("transfer")
    val n = X25519KeyPair.fromSeed(b(t.str("nSeed")))
    assertEquals(t.str("N"), PampaBase64.encode(n.publicKey))
    val sealed = b(t.str("sealed"))
    assertEquals(t.str("mac"), PampaBase64.encode(CustodyMath.transferMac(b(t.str("s")), t.str("transferId"), n.publicKey, sealed)))
    val payload = PampaHpke.open(n, CustodyMath.transferInfo(t.str("transferId")), ByteArray(0), sealed)
    val epochs = TransferPayload.decode(payload)
    assertEquals(cds.keys, epochs.keys)
    for ((e, cd) in cds) assertArrayEquals(cd, epochs.getValue(e))
  }

  private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
  private fun JsonObject.int(k: String) = this[k]!!.jsonPrimitive.int
  private fun JsonObject.obj(k: String) = this[k]!!.jsonObject
  private fun b(s: String) = PampaBase64.decode(s)
}
