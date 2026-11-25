package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.people.TestResources
import dev.antigravity.fluidengine.people.b64
import dev.antigravity.fluidengine.people.obj
import dev.antigravity.fluidengine.people.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'interoperabilità col server delle persone (F10.5): i vettori di `vettori/persone-server.json`, usciti dal codice vero
 * di `Pampa-account` (`genera-vettori-persone.ts.txt`). Dove il risultato è deterministico l'engine rifà **gli stessi
 * byte** (codice, casella, firme, scheda, PMX1, record, chiave di coppia); dove c'è un'effimera (CDXB) la apre.
 */
class ServerVectorsTest {
  private val v = TestResources.json("vettori/persone-server.json")
  private val people = v["people"]!!.jsonArray.map { it.jsonObject }
  private val alessio = people[0]
  private val marco = people[1]

  private fun root(p: JsonObject) = Ed25519Key.fromPrivate(b64(p.str("rootSeed")))
  private fun work(p: JsonObject): WorkKeys {
    val enc = X25519Key.fromPrivate(b64(p.str("encSeed")))
    val sign = Ed25519Key.fromPrivate(b64(p.str("signSeed")))
    return WorkKeys(1, enc, sign, root(p).sign(WorkPublic.signatureInput(1, enc.publicKey, sign.publicKey)))
  }
  private fun card(p: JsonObject) = PersonCard.verify(p.str("cardRaw"), p.str("cardSig"))

  @Test
  fun `radice, codice, casella e chiavi di lavoro sono quelli del server`() {
    for (p in people) {
      val r = root(p)
      assertEquals(p.str("rootPub"), PampaBase64.encode(r.publicKey))
      assertEquals(p.str("code"), PersonCode.of(r.publicKey))
      assertEquals(p.str("mailbox"), PersonCode.mailboxOf(r.publicKey))
      val w = work(p)
      assertEquals(p.str("encPub"), PampaBase64.encode(w.enc.publicKey))
      assertEquals(p.str("signPub"), PampaBase64.encode(w.sign.publicKey))
      assertEquals("la firma della radice sulle chiavi di lavoro, al byte", p.str("workSig"), PampaBase64.encode(w.sig))
    }
  }

  @Test
  fun `la scheda firmata sul telefono e quella del server sono gli stessi byte`() {
    for (p in people) {
      val mine = PersonCard.sign(root(p), p.str("name"), b64(p.str("mineral")), work(p).public, p["at"]!!.jsonPrimitive.long)
      assertEquals(p.str("cardRaw"), mine.raw)
      assertEquals(p.str("cardSig"), mine.sig)
      val theirs = card(p)
      assertEquals(p.str("code"), theirs.code)
      assertEquals(p.str("name"), theirs.name)
      assertEquals(1, theirs.work.epoch)
    }
  }

  @Test
  fun `il codice scritto a mano si legge come sul server`() {
    for ((input, expected) in v.obj("normalize")) {
      val server = (expected as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
      val ours = PersonCode.normalize(input)
      if (server != null) assertEquals(input, server, ours)
      else if (input.startsWith("CDX") || input.contains('U')) assertNull(input, ours)
    }
  }

  @Test
  fun `le impronte dei gettoni e dei bloccati sono quelle della casella`() {
    val p = v.obj("prints")
    val token = b64(p.str("token"))
    assertEquals(p.str("delivery"), PeoplePrints.delivery(token))
    assertEquals(p.str("invite"), PeoplePrints.invite(token))
    assertEquals(p.str("write"), PeoplePrints.write(token))
    assertEquals(p.str("blocked"), PeoplePrints.blocked(marco.str("mailbox")))
  }

  @Test
  fun `la CDXB del telefono finto si apre, e con lei la scheda di chi manda`() {
    val c = v.obj("cdxb")
    val opened = Cdxb.open(c.str("to"), mapOf(1 to work(marco)), c.str("from"), b64(c.str("data")))
    assertEquals("richiesta", opened.payload.str("t"))
    assertEquals(c.str("greeting"), opened.payload.str("greeting"))
    assertEquals(c.str("token"), opened.payload.str("token"))
    assertEquals(alessio.str("code"), opened.card.code)
    assertEquals(1, opened.senderEpoch)
  }

  @Test
  fun `la CDXB dell'engine la apre il destinatario, e la firma e del mittente`() {
    val sealed = Cdxb.seal(work(alessio), alessio.str("mailbox"), card(alessio), CdxbRecipient.of(card(marco)), Json.parseToJsonElement("""{"t":"accetta","token":"x"}""").jsonObject)
    val opened = Cdxb.open(marco.str("mailbox"), mapOf(1 to work(marco)), alessio.str("mailbox"), sealed)
    assertEquals("accetta", opened.payload.str("t"))
    assertEquals(alessio.str("code"), opened.card.code)
  }

  @Test
  fun `la PMX1 con id e nonce fissi e gli stessi byte, e si apre`() {
    val p = v.obj("pmx1")
    val plain = """{"text":"${p.str("text")}"}""".toByteArray()
    val data = Pmx1.sealWith(b64(p.str("chatKey")), p["epoch"]!!.jsonPrimitive.int, alessio.str("mailbox"), marco.str("mailbox"), p.str("thread"), plain, b64(p.str("messageId")), b64(p.str("nonce")))
    assertEquals(p.str("data"), PampaBase64.encode(data))
    val opened = Pmx1.open(mapOf(2 to b64(p.str("chatKey"))), alessio.str("mailbox"), marco.str("mailbox"), p.str("thread"), b64(p.str("data")))
    assertArrayEquals(plain, opened)
    assertEquals(p.str("messageId"), Pmx1.messageIdOf(data))
  }

  @Test
  fun `il record dello spazio col nonce fisso e gli stessi byte, e la firma e di chi scrive`() {
    val r = v.obj("record")
    val version = r["version"]!!.jsonPrimitive.long
    val epoch = r["epoch"]!!.jsonPrimitive.int
    val mine = SpaceRecordCipher.sealWith(b64(r.str("key")), r.str("spaceId"), epoch, r.str("recordId"), version, r.str("data"), alessio.str("code"), work(alessio), b64(r.str("nonce")))
    assertEquals(r.str("ciphertext"), PampaBase64.encode(mine))
    val opened = SpaceRecordCipher.open(b64(r.str("key")), r.str("spaceId"), epoch, r.str("recordId"), version, b64(r.str("ciphertext")))
    assertEquals(r.str("data"), opened.data)
    assertEquals(alessio.str("code"), opened.writer)
    assertTrue(SpaceRecordCipher.signedBy(opened, work(alessio).sign.publicKey, r.str("spaceId"), epoch, r.str("recordId"), version))
  }

  @Test
  fun `chiave di coppia, gemma e parole del contratto sono quelle del telefono finto, dai due lati`() {
    val p = v.obj("pair")
    val key = PairCode.pairKey(work(alessio).enc, b64(marco.str("encPub")), b64(alessio.str("rootPub")), b64(marco.str("rootPub")))
    val back = PairCode.pairKey(work(marco).enc, b64(alessio.str("encPub")), b64(marco.str("rootPub")), b64(alessio.str("rootPub")))
    assertEquals(p.str("key"), PampaBase64.encode(key))
    assertEquals(p.str("keyBack"), PampaBase64.encode(back))
    assertEquals(p.str("gem"), PampaBase64.encode(PairCode.gem(key)))
    assertEquals(p["words"]!!.jsonArray.map { it.jsonPrimitive.int }, PairCode.words(key))
  }
}
