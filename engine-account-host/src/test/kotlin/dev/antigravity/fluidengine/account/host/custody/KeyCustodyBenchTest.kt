package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.account.crypto.PampaLabels
import dev.antigravity.fluidengine.account.crypto.PampaRecordCipher
import dev.antigravity.fluidengine.account.host.server.AccountHttp
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.ServerResponse
import dev.antigravity.fluidengine.auth.FluidPrfInput
import dev.antigravity.fluidengine.auth.FluidWebAuthnJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Il banco di `b\chiavi.md` § 11 sulla custodia, voce per voce, contro il server finto con le regole del vero
 * ([FakeKeysServer]). Le voci sono numerate come nel documento. Quelle che hanno bisogno della rete vera (la 9 su
 * HTTP, la 5 con le app) stanno anche nel banco del pacchetto 3 e 4.
 */
class KeyCustodyBenchTest {

  private val pkg = "dev.pampa.pampai"

  /** Un account con la cifratura partita col codice di recupero: il server, la custodia, il codice. */
  private fun account(): Triple<FakeKeysServer, KeyCustody, RecoveryCode> {
    val server = FakeKeysServer()
    val code = RecoveryCode.generate()
    val start = KeyCustody.start(server.accountId, listOf(DoorSecret.recovery(code)))
    server.postRecovery(code.auth, start.upload.doors.single())
    server.postEpoch(start.upload.epoch)
    return Triple(server, start.custody, code)
  }

  private fun KeyCustody.rotated(server: FakeKeysServer, kind: EpochKind = EpochKind.NORMAL): KeyCustody {
    val p = rotate(server.view(), kind)
    server.postEpoch(p.upload)
    return p.custody
  }

  private fun KeyCustody.withDoor(server: FakeKeysServer, door: DoorSecret): KeyCustody {
    server.putDoor(buildDoor(door, server.view()))
    return this
  }

  private fun write(c: KeyCustody, recordId: String, version: Long, text: String): String {
    val set = c.appKeys(pkg, AppKeyPurpose.APP)
    return PampaRecordCipher(c.accountId, pkg).seal(set.keys.getValue(set.current), set.current, "memories", recordId, version, text.toByteArray())
  }

  private fun read(c: KeyCustody, recordId: String, version: Long, field: String): String {
    val keys = c.appKeys(pkg, AppKeyPurpose.APP).keys
    return PampaRecordCipher(c.accountId, pkg).open({ keys[it] }, "memories", recordId, version, field).decodeToString()
  }

  // --- 1. Telefono perso, in ogni riga di § 5 -------------------------------------------------------------------

  @Test
  fun `1 - telefono perso con la passkey sincronizzata col PRF, i dati tornano alla prima impronta`() {
    val (server, phone, _) = account()
    val prf = PampaCrypto.random(32)
    phone.withDoor(server, DoorSecret.prf("cred1", prf, synced = true))
    val field = write(phone, "r1", 1, "ricordo")
    val newPhone = KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.prf("cred1", prf, synced = true))
    assertEquals("ricordo", read(newPhone, "r1", 1, field))
  }

  @Test
  fun `1 - telefono perso con un altro dispositivo dentro, i dati tornano col passaggio`() {
    val (server, tablet, _) = account()
    val field = write(tablet, "r1", 1, "ricordo")
    val offer = TransferOffer.create(server.openTransfer())
    server.sealTransfer(offer.transferId, tablet.sealTransfer(TransferQr.parse(offer.qr)!!))
    val newPhone = KeyCustody.receiveTransfer(server.accountId, offer, server.takeTransfer(offer.transferId), server.view())
    offer.forget()
    assertEquals("ricordo", read(newPhone, "r1", 1, field))
  }

  @Test
  fun `1 - telefono perso col codice di recupero, i dati tornano`() {
    val (server, phone, code) = account()
    val field = write(phone, "r1", 1, "ricordo")
    val typed = RecoveryCode.parse(code.display.lowercase().replace("-", " "))!!
    val newPhone = KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(typed))
    assertEquals("ricordo", read(newPhone, "r1", 1, field))
  }

  @Test
  fun `1 - solo Google, o una passkey senza PRF - l'account torna ma i dati no, e Ricomincia riparte`() {
    val (server, phone, _) = account()
    val field = write(phone, "r1", 1, "ricordo")
    // Senza un segreto di porta non c'è niente da aprire: un codice sbagliato non apre la porta del codice.
    val wrong = runCatching { KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(RecoveryCode.generate())) }
    assertTrue(wrong.exceptionOrNull() is CustodyTamperedException)
    // «Ricomincia»: chiave nuova, epoca nuova di tipo reset, porta nuova; i dati vecchi sono persi davvero.
    val newCode = RecoveryCode.generate()
    val fresh = KeyCustody.start(server.accountId, listOf(DoorSecret.recovery(newCode))).custody
    val reset = fresh.reset(listOf(DoorSecret.recovery(newCode)))
    server.reset(reset.upload)
    val after = reset.custody
    assertEquals(EpochKind.RESET, server.view().epochs.single().kind)
    assertEquals(2, after.rootOfChain())
    assertTrue(runCatching { read(after, "r1", 1, field) }.exceptionOrNull() is PampaCryptoException)
    // La catena nuova si apre col codice nuovo, e quello vecchio non apre più niente.
    assertNotNull(KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(newCode)))
  }

  // --- 2. Server ostile -------------------------------------------------------------------------------------------

  @Test
  fun `2 - il server scambia due righe o rimette una versione vecchia - il telefono se ne accorge`() {
    val (_, phone, _) = account()
    val r1 = write(phone, "r1", 1, "uno")
    val r2 = write(phone, "r2", 1, "due")
    // Scambiate: la busta di r2 al posto di r1.
    assertTrue(runCatching { read(phone, "r1", 1, r2) }.exceptionOrNull() is PampaCryptoException)
    // Versione vecchia: chi ha visto la 2 non accetta più la 1.
    val v2 = write(phone, "r1", 2, "uno bis")
    val reader = PampaRecordCipher(phone.accountId, pkg)
    val keys = phone.appKeys(pkg, AppKeyPurpose.APP).keys
    assertEquals("uno bis", reader.open({ keys[it] }, "memories", "r1", 2, v2).decodeToString())
    assertTrue(runCatching { reader.open({ keys[it] }, "memories", "r1", 1, r1) }.exceptionOrNull() is PampaCryptoException)
  }

  @Test
  fun `2 - il server mette una sua pubblica al posto di quella di una porta (K8) - niente si sigilla per lui`() {
    val (server, phone, _) = account()
    val evil = X25519KeyPair.random()
    server.tamper = { v -> v.copy(doors = v.doors.map { it.copy(pub = PampaBase64.encode(evil.publicKey)) }) }
    val rotation = runCatching { phone.rotate(server.view()) }
    assertTrue(rotation.exceptionOrNull()?.message, rotation.exceptionOrNull() is CustodyTamperedException)
    val door = runCatching { phone.buildDoor(DoorSecret.prf("c", PampaCrypto.random(32), true), server.view()) }
    assertTrue(door.exceptionOrNull() is CustodyTamperedException)
  }

  @Test
  fun `2 - il server sigilla una chiave dei dati sua per la porta vera (K9) - il telefono nuovo la rifiuta`() {
    val (server, _, code) = account()
    val mine = PampaCrypto.random(32)
    server.corruptDoor("recovery") { d ->
      d.copy(seals = listOf(SealInfo(1, PampaBase64.encode(CustodyMath.sealForDoor(PampaBase64.decode(d.pub), "recovery", 1, mine)))))
    }
    val r = runCatching { KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code)) }
    assertEquals("la radice non è quella della porta (K9)", r.exceptionOrNull()?.message)
  }

  @Test
  fun `2 - il server aggiunge un'epoca falsa alla catena (K9) - rifiutata`() {
    val (server, phone, code) = account()
    val mine = PampaCrypto.random(32)
    server.tamper = { v ->
      val d = v.doors.single()
      val fakeSeal = SealInfo(2, PampaBase64.encode(CustodyMath.sealForDoor(PampaBase64.decode(d.pub), d.doorId, 2, mine)))
      v.copy(epoch = 2, epochs = v.epochs + EpochInfo(2, EpochKind.NORMAL, PampaBase64.encode(PampaCrypto.random(32))), doors = listOf(d.copy(seals = d.seals + fakeSeal)))
    }
    val r = runCatching { KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code)) }
    assertEquals("epoca 2 non legata alla catena (K9)", r.exceptionOrNull()?.message)
    // E il telefono che c'era non la prende per buona: è un'epoca che non ha, e non la può verificare senza una porta.
    assertTrue(runCatching { phone.verify(server.view()) }.exceptionOrNull() is CustodyBehindException)
    assertTrue(runCatching { phone.catchUp(server.view(), DoorSecret.recovery(code)) }.exceptionOrNull() is CustodyTamperedException)
  }

  @Test
  fun `2 - il server toglie una porta - il telefono che la conosceva se ne accorge`() {
    val (server, phone, _) = account()
    phone.withDoor(server, DoorSecret.prf("c1", PampaCrypto.random(32), synced = true))
    server.tamper = { v -> v.copy(doors = v.doors.filter { it.doorId != "recovery" }) }
    assertEquals("la porta recovery è sparita", runCatching { phone.rotate(server.view()) }.exceptionOrNull()?.message)
  }

  @Test
  fun `2 - il server rimette indietro l'epoca - scoperto`() {
    val (server, phone0, _) = account()
    val phone = phone0.rotated(server)
    server.tamper = { v -> v.copy(epoch = 1, epochs = v.epochs.filter { it.epoch == 1 }, doors = v.doors.map { d -> d.copy(seals = d.seals.filter { it.epoch == 1 }) }) }
    assertTrue(runCatching { phone.verify(server.view()) }.exceptionOrNull()?.message!!.contains("più vecchia"))
  }

  // --- 3. Epoche --------------------------------------------------------------------------------------------------

  @Test
  fun `3 - la chiave cambia tre volte e un dispositivo nuovo legge tutto`() {
    val (server, start, code) = account()
    var phone = start
    val fields = mutableListOf(write(phone, "r1", 1, "epoca 1"))
    repeat(3) { i ->
      phone = phone.rotated(server)
      fields += write(phone, "r${i + 2}", 1, "epoca ${i + 2}")
    }
    assertEquals(4, server.view().epoch)
    val newPhone = KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code))
    assertEquals(setOf(1, 2, 3, 4), newPhone.epochs)
    fields.forEachIndexed { i, f -> assertEquals("epoca ${i + 1}", read(newPhone, "r${i + 1}", 1, f)) }
    // E anche col passaggio.
    val offer = TransferOffer.create(server.openTransfer())
    server.sealTransfer(offer.transferId, newPhone.sealTransfer(TransferQr.parse(offer.qr)!!))
    val third = KeyCustody.receiveTransfer(server.accountId, offer, server.takeTransfer(offer.transferId), server.view())
    fields.forEachIndexed { i, f -> assertEquals("epoca ${i + 1}", read(third, "r${i + 1}", 1, f)) }
  }

  @Test
  fun `3 - un dispositivo rimasto indietro lo sa, e si rimette in pari aprendo una porta`() {
    val (server, phone, code) = account()
    val tablet = KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code))
    val phone2 = phone.rotated(server)
    assertTrue(runCatching { tablet.verify(server.view()) }.exceptionOrNull() is CustodyBehindException)
    val caught = tablet.catchUp(server.view(), DoorSecret.recovery(code))
    assertEquals(phone2.current, caught.current)
    assertArrayEquals(phone2.cd(2), caught.cd(2))
  }

  // --- 4. Due porte -----------------------------------------------------------------------------------------------

  @Test
  fun `4 - niente parte senza una porta fuori dal telefono`() {
    val server = FakeKeysServer()
    // Una passkey col PRF ma non sincronizzata (flag BS spento) si perde col telefono: non basta.
    val r = runCatching { KeyCustody.start(server.accountId, listOf(DoorSecret.prf("c", PampaCrypto.random(32), synced = false))) }
    assertTrue(r.exceptionOrNull() is NeedsDoorException)
    assertTrue("non è partito niente", server.received.isEmpty())
    // Il server rifiuta anche da sé la prima epoca senza porte.
    assertEquals("no_door", runCatching { server.postEpoch(EpochUpload(1, EpochKind.FIRST, null, emptyList())) }.exceptionOrNull()?.message)
    // Con la passkey sincronizzata basta, anche senza codice.
    val ok = KeyCustody.start(server.accountId, listOf(DoorSecret.prf("c", PampaCrypto.random(32), synced = true)))
    server.putDoor(ok.upload.doors.single())
    server.postEpoch(ok.upload.epoch)
    assertEquals(1, server.view().epoch)
  }

  @Test
  fun `4 - l'ultima porta non si toglie a cifratura partita`() {
    val (server, _, _) = account()
    assertEquals("last_door", runCatching { server.deleteDoor("recovery") }.exceptionOrNull()?.message)
  }

  // --- 6. PRF assente o diverso -----------------------------------------------------------------------------------

  @Test
  fun `6 - PRF diverso fra creazione e accesso, come Samsung Pass nel test di terzi - la porta non si apre e si passa al codice`() {
    val (server, phone, code) = account()
    phone.withDoor(server, DoorSecret.prf("cred", PampaCrypto.random(32), synced = true))
    val atLogin = PampaCrypto.random(32)
    val r = runCatching { KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.prf("cred", atLogin, synced = true)) }
    assertEquals("la pubblica della porta non è quella del mio segreto (K8)", r.exceptionOrNull()?.message)
    assertNotNull(KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code)))
  }

  @Test
  fun `6 - PRF assente - la passkey resta un accesso ma non fa da porta`() {
    // Senza uscita del PRF non c'è un DoorSecret da costruire: l'unica strada è un'altra porta.
    val (server, _, _) = account()
    assertNull(server.view().door("prf:cred"))
    assertTrue(runCatching { DoorSecret.prf("cred", ByteArray(0), synced = true) }.exceptionOrNull() is IllegalArgumentException)
  }

  // --- 9. Niente segreti verso il server (anche su HTTP nel pacchetto 3) -------------------------------------------

  @Test
  fun `9 - in un giro completo nessun corpo contiene il codice, il PRF, un seme, N, s o una chiave dei dati`() {
    val server = FakeKeysServer()
    val code = RecoveryCode.generate()
    val start = KeyCustody.start(server.accountId, listOf(DoorSecret.recovery(code)))
    server.postRecovery(code.auth, start.upload.doors.single())
    server.postEpoch(start.upload.epoch)
    val prf = PampaCrypto.random(32)
    var phone = start.custody.withDoor(server, DoorSecret.prf("cred", prf, synced = true))
    phone = phone.rotated(server).rotated(server, EpochKind.LOST)
    val offer = TransferOffer.create(server.openTransfer())
    val qr = TransferQr.parse(offer.qr)!!
    server.sealTransfer(offer.transferId, phone.sealTransfer(qr))
    KeyCustody.receiveTransfer(server.accountId, offer, server.takeTransfer(offer.transferId), server.view())
    val everything = server.received.joinToString("\n")
    val secrets = recoveryForms(code) + listOf(
      PampaBase64.encode(prf),
      PampaBase64.encode(qr.s),
      PampaBase64.encode(offer.keyPair().privateBytes()),
    ) + phone.copyEpochs().values.map(PampaBase64::encode) +
      server.view().doors.map { d -> PampaBase64.encode(CustodyMath.doorSeed(if (d.kind == DoorKind.RECOVERY) code.secret() else prf, PampaBase64.decode(d.salt))) }
    for (s in secrets) assertFalse("è partito un segreto («${s.take(6)}…»)", everything.contains(s))
    assertTrue("parte solo auth", everything.contains(code.auth))
  }

  // --- 10. Il passaggio col server ostile --------------------------------------------------------------------------

  @Test
  fun `10 - il server sostituisce il sigillo, ne rimanda uno vecchio, cambia il transferId - il nuovo rifiuta ogni volta`() {
    val (server, old, _) = account()
    val offer = TransferOffer.create(server.openTransfer())
    val good = old.sealTransfer(TransferQr.parse(offer.qr)!!)
    // Sigillo sostituito: il server sigilla una chiave sua verso N (N lo può anche indovinare, ma non s).
    val evilSealed = PampaHpke.seal(offer.publicKey, CustodyMath.transferInfo(offer.transferId), ByteArray(0), TransferPayload.encode(mapOf(1 to PampaCrypto.random(32))))
    val evil = TransferSeal(PampaBase64.encode(evilSealed), PampaBase64.encode(CustodyMath.transferMac(PampaCrypto.random(16), offer.transferId, offer.publicKey, evilSealed)))
    assertTampered("sigillo del passaggio non fatto col segreto del QR") { KeyCustody.receiveTransfer(server.accountId, offer, evil, server.view()) }
    // Il mac buono messo su un sigillo cambiato.
    assertTampered("sigillo del passaggio non fatto col segreto del QR") { KeyCustody.receiveTransfer(server.accountId, offer, good.copy(sealed = evil.sealed), server.view()) }
    // Un sigillo vecchio, di un altro passaggio dello stesso account.
    val offer2 = TransferOffer.create(server.openTransfer())
    assertTampered("sigillo del passaggio non fatto col segreto del QR") { KeyCustody.receiveTransfer(server.accountId, offer2, good, server.view()) }
    // Lo stesso sigillo sotto un altro transferId: il mac lega l'id.
    val renamed = TransferOffer.create("xfr_altro")
    assertTampered("sigillo del passaggio non fatto col segreto del QR") { KeyCustody.receiveTransfer(server.accountId, renamed, good, server.view()) }
    // Il sigillo buono passa.
    assertNotNull(KeyCustody.receiveTransfer(server.accountId, offer, good, server.view()))
  }

  @Test
  fun `10 - promemoria storico - le tre parole a 12 bit senza impegno si fanno falsificare dal server`() {
    // La versione scartata (critica S2): i due schermi mostravano parole ricavate dalle due pubbliche, e il server, in
    // mezzo, sceglieva la sua dopo aver visto le altre. Con 12 bit gli bastano qualche migliaio di tentativi.
    fun words(a: ByteArray, b: ByteArray): Int {
      val h = PampaCrypto.sha256(a, b)
      return ((h[0].toInt() and 0xff) shl 4) or ((h[1].toInt() and 0xf0) shr 4)
    }
    val oldPhone = X25519KeyPair.random().publicKey
    val newPhone = X25519KeyPair.random().publicKey
    val target = words(oldPhone, newPhone)
    var tries = 0
    var forged: ByteArray
    do {
      forged = X25519KeyPair.random().publicKey
      tries++
    } while (words(oldPhone, forged) != target && tries < 200_000)
    assertEquals("il server trova le stesse parole con la sua chiave", target, words(oldPhone, forged))
    assertTrue("in pochi tentativi ($tries)", tries < 200_000)
  }

  // --- 11. Il PRF scelto dallo Store (sopra engine-auth, F10.2) ----------------------------------------------------

  private fun b64url(bytes: ByteArray) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

  /** Una risposta d'accesso di Credential Manager, con l'uscita del PRF [prfOut] e il flag BS [synced]. */
  private fun assertionJson(prfOut: ByteArray?, synced: Boolean): String {
    val flags = 0x01 or 0x04 or 0x08 or (if (synced) 0x10 else 0)
    val authData = ByteArray(32) + byteArrayOf(flags.toByte(), 0, 0, 0, 7)
    val clientData = """{"type":"webauthn.get","challenge":"abc","origin":"android:apk-key-hash:prova"}""".toByteArray()
    val prf = if (prfOut != null) ""","prf":{"results":{"first":"${b64url(prfOut)}"}}""" else ""
    return """{"id":"${b64url("cred1".toByteArray())}","rawId":"${b64url("cred1".toByteArray())}","type":"public-key",""" +
      """"response":{"authenticatorData":"${b64url(authData)}","clientDataJSON":"${b64url(clientData)}","signature":"c2ln"},""" +
      """"clientExtensionResults":{"credProps":{"rk":true}$prf}}"""
  }

  @Test
  fun `11 - le opzioni del server con un altro ingresso o evalByCredential - lo Store le ignora`() {
    val serverOptions = buildJsonObject {
      put("challenge", "abc")
      putJsonObject("extensions") {
        put("credProps", JsonPrimitive(true))
        putJsonObject("prf") {
          putJsonObject("eval") { put("first", "aW5ncmVzc28tZGVsLXNlcnZlcg") }
          putJsonObject("evalByCredential") { putJsonObject("cred") { put("first", "YWx0cm8") } }
        }
      }
    }
    for (request in listOf(StorePrf.assertionRequest(serverOptions.toString()))) {
      val mine = Json.parseToJsonElement(request).jsonObject
      val prf = mine["extensions"]!!.jsonObject["prf"]!!.jsonObject
      assertEquals(setOf("eval"), prf.keys)
      assertArrayEquals(PampaLabels.prfInput(), PampaBase64.decode(prf["eval"]!!.jsonObject["first"]!!.jsonPrimitive.content))
      assertEquals("le altre estensioni restano", "true", mine["extensions"]!!.jsonObject["credProps"]!!.jsonPrimitive.content)
    }
  }

  @Test
  fun `11 - l'ingresso di engine-auth e' quello del protocollo`() {
    assertArrayEquals(PampaLabels.prfInput(), FluidPrfInput.PampaV1.bytes())
    assertArrayEquals(PampaCrypto.sha256("pampa/v1/prf".toByteArray()), StorePrf.input.bytes())
  }

  @Test
  fun `11 - la risposta della passkey - la porta resta sul telefono, al server va senza risultati`() {
    val prfOut = PampaCrypto.random(32)
    val result = FluidWebAuthnJson.readAssertion(assertionJson(prfOut, synced = true))
    assertFalse("la risposta da spedire non porta l'uscita", StorePrf.leaksPrfOutput(result.responseJson))
    assertFalse(result.responseJson.contains(b64url(prfOut)))
    assertTrue(StorePrf.leaksPrfOutput(assertionJson(prfOut, synced = true)))
    val door = DoorSecret.fromPasskey(result)
    assertEquals("prf:${b64url("cred1".toByteArray())}", door.doorId)
    assertEquals(DoorKind.PRF, door.kind)
    assertTrue("BS acceso: la porta conta come esterna (K2)", door.external)
    assertArrayEquals(prfOut, door.secret)
    // Senza BS la passkey sta su un telefono solo: porta sì, esterna no.
    assertFalse(DoorSecret.fromPasskey(FluidWebAuthnJson.readAssertion(assertionJson(prfOut, synced = false))).external)
    // Senza uscita del PRF non c'è porta.
    val none = runCatching { DoorSecret.fromPasskey(FluidWebAuthnJson.readAssertion(assertionJson(null, synced = true))) }
    assertTrue(none.exceptionOrNull() is IllegalArgumentException)
  }

  @Test
  fun `11 - un corpo con l'uscita del PRF non parte verso il server`() = kotlinx.coroutines.test.runTest {
    var sent = 0
    val client = AccountServerClient("https://account.invalid", "1.0", AccountHttp { _, _ -> sent++; ServerResponse(200, "{}".toByteArray(), null) })
    val leaking = Json.parseToJsonElement(assertionJson(PampaCrypto.random(32), synced = true)).jsonObject
    val r = runCatching { client.call("POST", "/v1/keys/doors", JsonObject(mapOf("credential" to leaking))) }
    assertTrue(r.exceptionOrNull() is IllegalStateException)
    assertEquals(0, sent)
    // La stessa risposta letta da engine-auth parte.
    val clean = Json.parseToJsonElement(FluidWebAuthnJson.readAssertion(assertionJson(PampaCrypto.random(32), synced = true)).responseJson).jsonObject
    client.call("POST", "/v1/keys/doors", JsonObject(mapOf("credential" to clean)))
    assertEquals(1, sent)
  }

  @Test
  fun `11 - la porta della passkey apre la custodia come quella fatta a mano`() {
    val (server, phone, _) = account()
    val prfOut = PampaCrypto.random(32)
    phone.withDoor(server, DoorSecret.fromPasskey(FluidWebAuthnJson.readAssertion(assertionJson(prfOut, synced = true))))
    val again = KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.fromPasskey(FluidWebAuthnJson.readAssertion(assertionJson(prfOut, synced = true))))
    assertArrayEquals(phone.cd(1), again.cd(1))
  }

  // --- 12. I sigilli d'autenticità dopo un furto ------------------------------------------------------------------

  @Test
  fun `12 - dopo una rotazione perso o rubato, un tag fatto con la prima epoca non vale piu'`() {
    val (server, phone0, _) = account()
    val oldCd1 = phone0.cd(1)!!
    server.lostPending = true
    assertEquals("lost_rotation_required", runCatching { phone0.rotated(server) }.exceptionOrNull()?.message)
    val phone = phone0.rotated(server, EpochKind.LOST)
    assertEquals(2, server.view().reference)
    assertTrue(server.view().doors.all { it.tagEpoch == 2 })
    // Il ladro (ha CD_1) col server: rimette una porta sua col tag fatto con la prima epoca.
    val evil = X25519KeyPair.random()
    val salt = PampaBase64.encode(PampaCrypto.random(16))
    val evilPub = PampaBase64.encode(evil.publicKey)
    server.tamper = { v ->
      v.copy(doors = v.doors.map { d -> d.copy(pub = evilPub, salt = salt, tagEpoch = 1, tag = PampaBase64.encode(CustodyMath.tag(oldCd1, d.doorId, d.kind.wire, evilPub, salt, 1))) })
    }
    assertTampered("porta recovery: sigillo d'autenticità di un'epoca superata (S11)") { phone.rotate(server.view()) }
  }

  // --- utilità -----------------------------------------------------------------------------------------------------

  private fun assertTampered(message: String, block: () -> Unit) {
    try {
      block()
      fail("doveva essere scoperto: $message")
    } catch (e: CustodyTamperedException) {
      assertEquals(message, e.message)
    }
  }
}

/** Il codice di recupero in tutte le forme in cui potrebbe scappare (come `secrets.test.ts` del server). */
internal fun recoveryForms(code: RecoveryCode): List<String> {
  val plain = code.display.replace("-", "")
  val bytes = code.secret()
  return listOf(
    code.display, code.display.lowercase(), plain, plain.lowercase(),
    PampaBase64.encode(bytes), java.util.Base64.getEncoder().encodeToString(bytes),
    bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) },
  )
}
