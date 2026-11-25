package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.people.TestResources
import dev.antigravity.fluidengine.people.toHex
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Una persona di prova: radice, chiavi di lavoro, scheda. */
internal class TestPerson(name: String, val root: Ed25519Key = Ed25519Key.random(), epoch: Int = 1) {
  var work: WorkKeys = WorkKeys.create(root, epoch)
  val works = mutableMapOf(epoch to work)
  var card: PersonCard = PersonCard.sign(root, name, ByteArray(16) { 7 }, work.public, 1_764_000_000_000)
  val mailbox: String get() = card.mailbox

  fun rotate() {
    work = WorkKeys.create(root, work.epoch + 1)
    works[work.epoch] = work
    card = PersonCard.sign(root, card.name, card.mineral, work.public, card.at + 1)
  }
}

/** Il comportamento della crittografia delle persone, e i casi in cui qualcuno bara. */
class PeopleCryptoTest {
  private fun detected(block: () -> Unit): String = try {
    block()
    fail("doveva scoprirlo")
    ""
  } catch (e: PeopleDetected) {
    e.what
  }

  @Test
  fun `l'elenco delle parole e quello di BIP-39, 2048 parole diverse nelle prime quattro lettere`() {
    val all = ItalianWords.all
    assertEquals(2048, all.size)
    assertEquals(2048, all.map { it.take(4) }.toSet().size)
    assertEquals(all.sorted(), all)
    assertTrue(all.all { w -> w.all { it in 'a'..'z' } })
    assertEquals("d392c49fdb700a24cd1fceb237c1f65dcc128f6b34a8aacb58b59384b5c648c2", PampaCrypto.sha256(all.joinToString("") { "$it\n" }.toByteArray()).toHex())
    assertEquals(0, ItalianWords.indexOf("abaco"))
    assertEquals(2047, ItalianWords.indexOf("ZUPPA"))
    assertEquals(ItalianWords.indexOf("abbaglio"), ItalianWords.indexOf("abba"))
    assertEquals(-1, ItalianWords.indexOf("xyz"))
  }

  @Test
  fun `una scheda toccata, di un altro o con chiavi non firmate dalla radice viene scoperta`() {
    val a = TestPerson("Alessio")
    val b = TestPerson("Marco")
    assertEquals(a.card, PersonCard.verify(a.card.raw, a.card.sig, expectCode = a.card.code, expectMailbox = a.mailbox, expectRoot = a.root.publicKey))
    assertEquals("scheda con un altro codice", detected { PersonCard.verify(b.card.raw, b.card.sig, expectCode = a.card.code) })
    assertEquals("radice cambiata", detected { PersonCard.verify(b.card.raw, b.card.sig, expectRoot = a.root.publicKey) })
    assertEquals("firma della scheda", detected { PersonCard.verify(a.card.raw, b.card.sig) })
    // Il server ostile cambia il nome nella scheda: la firma non torna più.
    val json = PampaBase64.decode(a.card.raw).decodeToString().replace("Alessio", "Alessia")
    assertEquals("firma della scheda", detected { PersonCard.verify(PampaBase64.encode(json.toByteArray()), a.card.sig) })
    // Chiavi di lavoro firmate da un'altra radice, messe in una scheda firmata da lui: scoperte lo stesso.
    val stranger = Ed25519Key.random()
    val fakeWork = WorkKeys.create(stranger, 1)
    val forged = buildForged(a.root, fakeWork.public)
    assertEquals("chiavi di lavoro non firmate dalla radice", detected { PersonCard.verify(forged.first, forged.second) })
    // Il codice d'un altro in una scheda vera: il codice nasce dalla radice.
    val wrongCode = PampaBase64.decode(a.card.raw).decodeToString().replace(a.card.code, b.card.code)
    val resigned = PampaBase64.encode(a.root.sign(PeopleLabels.CARD.toByteArray() + wrongCode.toByteArray()))
    assertEquals("codice che non viene dalla radice", detected { PersonCard.verify(PampaBase64.encode(wrongCode.toByteArray()), resigned) })
  }

  private fun buildForged(root: Ed25519Key, work: WorkPublic): Pair<String, String> {
    val json = buildJsonObject {
      put("v", 1); put("code", PersonCode.of(root.publicKey)); put("name", "X"); put("mineral", PampaBase64.encode(ByteArray(16)))
      put("root", PampaBase64.encode(root.publicKey)); put("mailbox", PersonCode.mailboxOf(root.publicKey))
      put("work", buildJsonObject { put("epoch", 1); put("enc", PampaBase64.encode(work.enc)); put("sign", PampaBase64.encode(work.sign)); put("sig", PampaBase64.encode(work.sig)) })
      put("at", 1)
    }.toString().toByteArray()
    return PampaBase64.encode(json) to PampaBase64.encode(root.sign(PeopleLabels.CARD.toByteArray() + json))
  }

  @Test
  fun `una CDXB spostata, col mittente cambiato o firmata da un altro non si apre`() {
    val a = TestPerson("Alessio")
    val b = TestPerson("Marco")
    val c = TestPerson("Chiara")
    val payload = buildJsonObject { put("t", "richiesta"); put("greeting", "ciao") }
    val sealed = Cdxb.seal(a.work, a.mailbox, a.card, CdxbRecipient.of(b.card), payload)
    assertEquals("ciao", Cdxb.open(b.mailbox, b.works, a.mailbox, sealed).payload["greeting"]!!.jsonPrimitive.content)
    // Il server la consegna a Chiara: non è per la sua chiave.
    assertEquals("busta per una chiave che non ho", detected { Cdxb.open(c.mailbox, mapOf(9 to c.work), a.mailbox, sealed) })
    assertTrue(detected { Cdxb.open(c.mailbox, c.works, a.mailbox, sealed) }.startsWith("busta che non si apre"))
    // Il server dice che viene da Chiara.
    assertTrue(detected { Cdxb.open(b.mailbox, b.works, c.mailbox, sealed) }.startsWith("busta che non si apre"))
    // Un bit della firma cambiato.
    val bad = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
    assertEquals("firma della busta", detected { Cdxb.open(b.mailbox, b.works, a.mailbox, bad) })
    // La radice che conosco già per quella casella deve restare quella.
    assertEquals("radice cambiata", detected { Cdxb.open(b.mailbox, b.works, a.mailbox, sealed, knownRoot = c.root.publicKey) })
  }

  @Test
  fun `dopo il rinnovo le buste nuove sono per la chiave nuova, e il telefono rubato non le apre`() {
    val a = TestPerson("Alessio")
    val b = TestPerson("Marco")
    val stolen = b.works.toMap()
    b.rotate()
    val sealed = Cdxb.seal(a.work, a.mailbox, a.card, CdxbRecipient.of(b.card), buildJsonObject { put("t", "x") })
    assertEquals(2, Cdxb.open(b.mailbox, b.works, a.mailbox, sealed).recipientEpoch)
    assertEquals("busta per una chiave che non ho", detected { Cdxb.open(b.mailbox, stolen, a.mailbox, sealed) })
    // La scheda nuova è firmata dalla stessa radice: chi la conosce non deve rifare niente.
    assertEquals(b.card.code, PersonCard.verify(b.card.raw, b.card.sig, expectRoot = b.root.publicKey).code)
  }

  @Test
  fun `una PMX1 spostata in un'altra conversazione o attribuita a un altro non si apre`() {
    val key = PampaCrypto.random(32)
    val data = Pmx1.seal(key, 3, "da", "a", "filo", "ciao".toByteArray())
    assertArrayEquals("ciao".toByteArray(), Pmx1.open(mapOf(3 to key), "da", "a", "filo", data))
    assertNull(Pmx1.open(mapOf(3 to key), "da", "a", "altro-filo", data))
    assertNull(Pmx1.open(mapOf(3 to key), "terzo", "a", "filo", data))
    assertNull(Pmx1.open(mapOf(2 to key), "da", "a", "filo", data))
    assertEquals(3, Pmx1.epochOf(data))
    assertNotEquals(Pmx1.messageIdOf(data), Pmx1.messageIdOf(Pmx1.seal(key, 3, "da", "a", "filo", "ciao".toByteArray())))
  }

  @Test
  fun `un record spostato, rinominato o rinumerato non si apre, e la firma dice chi l'ha scritto`() {
    val a = TestPerson("Alessio")
    val key = PampaCrypto.random(32)
    val ct = SpaceRecordCipher.seal(key, "spazio", 1, "voce", 2, "latte", a.card.code, a.work)
    val opened = SpaceRecordCipher.open(key, "spazio", 1, "voce", 2, ct)
    assertTrue(SpaceRecordCipher.signedBy(opened, a.work.sign.publicKey, "spazio", 1, "voce", 2))
    assertFalse(SpaceRecordCipher.signedBy(opened, TestPerson("Marco").work.sign.publicKey, "spazio", 1, "voce", 2))
    for ((s, r, ver) in listOf(Triple("altro", "voce", 2L), Triple("spazio", "altra", 2L), Triple("spazio", "voce", 1L))) {
      assertTrue(detected { SpaceRecordCipher.open(key, s, 1, r, ver, ct) }.startsWith("record che non si apre"))
    }
  }

  @Test
  fun `le parole con l'impegno sono uguali dai due lati, e il nonce sbagliato non apre l'impegno`() {
    val a = TestPerson("Alessio")
    val b = TestPerson("Marco")
    val ka = PairCode.pairKey(a.work.enc, b.work.public.enc, a.root.publicKey, b.root.publicKey)
    val kb = PairCode.pairKey(b.work.enc, a.work.public.enc, b.root.publicKey, a.root.publicKey)
    assertArrayEquals(ka, kb)
    val na = PampaCrypto.random(16)
    val nb = PampaCrypto.random(16)
    val commitment = PairCode.commit(na, a.root.publicKey, a.work.public.enc, b.work.public.enc)
    assertTrue(PairCode.opens(commitment, na, a.root.publicKey, a.work.public.enc, b.work.public.enc))
    assertFalse(PairCode.opens(commitment, PampaCrypto.random(16), a.root.publicKey, a.work.public.enc, b.work.public.enc))
    // L'impegno lega anche le chiavi: con un'altra chiave dell'altro non torna.
    assertFalse(PairCode.opens(commitment, na, a.root.publicKey, a.work.public.enc, TestPerson("X").work.public.enc))
    val salt = PairCode.salt(na, nb)
    assertEquals(PairCode.wordsText(ka, salt), PairCode.wordsText(kb, salt))
    assertEquals(4, PairCode.wordsText(ka, salt).size)
    assertArrayEquals(PairCode.gem(ka, salt), PairCode.gem(kb, salt))
    assertNotEquals("i nonce cambiano le parole", PairCode.words(ka, salt), PairCode.words(ka, PairCode.salt(na, PampaCrypto.random(16))))
  }

  @Test
  fun `le parole cambiano col rinnovo delle chiavi`() {
    val a = TestPerson("Alessio")
    val b = TestPerson("Marco")
    val before = PairCode.words(PairCode.pairKey(a.work.enc, b.work.public.enc, a.root.publicKey, b.root.publicKey))
    b.rotate()
    val after = PairCode.words(PairCode.pairKey(a.work.enc, b.work.public.enc, a.root.publicKey, b.root.publicKey))
    assertNotEquals(before, after)
  }

  @Test
  fun `vicinanza - saluto, risposta e runa uguale dai due lati, e chi sta in mezzo vede un'altra runa`() {
    val a = TestPerson("Alessio")
    val b = TestPerson("Marco")
    val ea = X25519Key.random()
    val eb = X25519Key.random()
    val ca = NearbyHandshake.newChallenge()
    val cb = NearbyHandshake.newChallenge()
    val helloA = NearbyHandshake.readHello(NearbyHandshake.hello(a.card, ca, ea.publicKey))
    val helloB = NearbyHandshake.readHello(NearbyHandshake.hello(b.card, cb, eb.publicKey))
    assertEquals(a.card, helloA.card)
    // B risponde alla sfida di A, A controlla.
    val answerB = NearbyHandshake.answer(helloA.challenge, b.card, eb.publicKey, helloA.ephemeral, b.work)
    assertTrue(NearbyHandshake.verify(ca, helloB.card, helloB.ephemeral, ea.publicKey, answerB))
    // Rigirata in un altro canale (un'altra effimera) la firma non vale.
    assertFalse(NearbyHandshake.verify(ca, helloB.card, helloB.ephemeral, X25519Key.random().publicKey, answerB))
    val runeA = NearbyHandshake.rune(ea, helloB.ephemeral, a.card, helloB.card)
    val runeB = NearbyHandshake.rune(eb, helloA.ephemeral, b.card, helloA.card)
    assertEquals(runeA, runeB)
    assertEquals(4, runeA.length)
    assertTrue(runeA.all { it in PampaRune.POOL })
    // Uno nella stanza fa due canali con due effimere sue: le due rune non tornano.
    val ma = X25519Key.random()
    val mb = X25519Key.random()
    assertNotEquals(NearbyHandshake.rune(ea, ma.publicKey, a.card, b.card), NearbyHandshake.rune(eb, mb.publicKey, b.card, a.card))
    assertTrue(runCatching { NearbyHandshake.readHello("ciao".toByteArray()) }.exceptionOrNull() is NearbyHandshake.Refused)
    assertNotEquals(NearbyHandshake.advertisedName(), NearbyHandshake.advertisedName())
  }

  @Test
  fun `il QR porta la scheda intera, e la prova lega chi l'ha inquadrato`() {
    val a = TestPerson("Alessio")
    val b = TestPerson("Marco")
    val qr = PersonQr.create(a.card, PeoplePrints.newToken())
    val read = PersonQr.decode(qr.encode())!!
    assertEquals(a.card, read.card)
    assertEquals(qr.invite, read.invite)
    val proof = read.proof(b.card.raw)
    assertTrue(qr.checkProof(b.card.raw, proof))
    assertFalse("la prova non vale per un'altra scheda", qr.checkProof(TestPerson("X").card.raw, proof))
    assertNull(PersonQr.decode("PMQ1:abc"))
    assertNull(PersonQr.decode(qr.encode().replace(a.card.sig, b.card.sig)))
    assertTrue(PersonQr.looksLikePersonQr(" pmq1:x"))
  }

  @Test
  fun `il codice scritto a mano, con le lettere che si confondono`() {
    val a = TestPerson("Alessio")
    val code = a.card.code
    assertEquals(code, PersonCode.normalize(code.lowercase()))
    assertEquals(code, PersonCode.normalize(code.replace("-", " ")))
    assertEquals(code, PersonCode.normalize(code.removePrefix("PMP-")))
    assertNull(PersonCode.normalize("PMP-1234"))
    assertEquals("PMP-0K3P-111Q", PersonCode.normalize("pmp-ok3p-ilLq"))
  }

  @Test
  fun `le epoche - chi esce tiene il passato e perde il futuro`() {
    val ring = EpochKeyring.create()
    val first = ring.currentKey
    val gone = EpochKeyring.of(ring.snapshot())
    assertEquals(2, ring.rotate())
    assertFalse(ring.currentKey.contentEquals(first))
    assertArrayEquals(first, ring.key(1))
    assertNull(gone.key(2))
    gone.merge(mapOf(1 to ByteArray(32)))
    assertArrayEquals("una busta non sostituisce una chiave che si ha già", first, gone.key(1))
  }

  @Test
  fun `le etichette sono quelle del contratto del server`() {
    val contract = TestResources.json("contract/e2e.json")["people"]!!.jsonObject
    val labels = contract["labels"]!!.jsonObject
    val ours = mapOf(
      "code" to PeopleLabels.CODE, "mailbox" to PeopleLabels.MAILBOX, "card" to PeopleLabels.CARD, "workKeys" to PeopleLabels.WORK_KEYS,
      "deliveryPrint" to PeopleLabels.DELIVERY_PRINT, "invitePrint" to PeopleLabels.INVITE_PRINT, "writePrint" to PeopleLabels.WRITE_PRINT,
      "blockedPrint" to PeopleLabels.BLOCKED_PRINT, "cdxbInfo" to PeopleLabels.CDXB_INFO, "cdxbSig" to PeopleLabels.CDXB_SIG,
      "pmx1Key" to PeopleLabels.PMX1_KEY, "pmx1Aad" to PeopleLabels.PMX1_AAD, "pair" to PeopleLabels.PAIR, "gem" to PeopleLabels.GEM,
      "words" to PeopleLabels.WORDS, "spaceAad" to PeopleLabels.SPACE_AAD, "spaceSig" to PeopleLabels.SPACE_SIG,
    )
    assertEquals("ogni etichetta del contratto ha la sua copia", labels.keys, ours.keys)
    for ((k, v) in ours) assertEquals(k, (labels[k] as JsonPrimitive).content, v)
    assertEquals(32, contract["tokens"]!!.jsonObject["bytes"]!!.jsonPrimitive.content.toInt())
    assertEquals(listOf("delivery", "blocked", "invite.qr", "invite.link", "invite.group"), contract["tokens"]!!.jsonObject["kinds"]!!.jsonArray.map { it.jsonPrimitive.content })
    val hpke = TestResources.json("contract/e2e.json")["hpke"] as JsonObject
    assertEquals("base", hpke["mode"]!!.jsonPrimitive.content)
    assertEquals(listOf(32, 1, 2), listOf("kemId", "kdfId", "aeadId").map { hpke[it]!!.jsonPrimitive.content.toInt() })
  }
}
