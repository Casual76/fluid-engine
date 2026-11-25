package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaRecordCipher
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Voce 14 del banco (`b\chiavi.md` § 11, dopo F10.4 § 8): le porte dei dispositivi. Le stesse scene di
 * `Pampa-account\test\device-doors.test.ts`, dal lato del telefono: nessuna chiave copiata a mano, il secondo dispositivo
 * ha la chiave solo da una porta o dal QR.
 */
class DeviceDoorBenchTest {
  @get:Rule val tmp = TemporaryFolder()

  private val pkg = "dev.pampa.pampai"

  private class Device(val id: String, var custody: KeyCustody)

  /** Il telefono che fa partire la cifratura col codice, e la sua porta del dispositivo. */
  private fun start(server: FakeKeysServer): Pair<Device, RecoveryCode> {
    val code = RecoveryCode.generate()
    val s = KeyCustody.start(server.accountId, listOf(DoorSecret.recovery(code)))
    server.postRecovery(code.auth, s.upload.doors.single())
    server.postEpoch(s.upload.epoch)
    return Device("dev_telefono", s.custody).also { withDoor(server, it) } to code
  }

  /** Un dispositivo che entra col QR da [from], e crea la sua porta. */
  private fun join(server: FakeKeysServer, from: Device, id: String): Device {
    val offer = TransferOffer.create(server.openTransfer())
    server.sealTransfer(offer.transferId, from.custody.sealTransfer(TransferQr.parse(offer.qr)!!))
    val c = KeyCustody.receiveTransfer(server.accountId, offer, server.takeTransfer(offer.transferId), server.view())
    offer.forget()
    assertNull("il passaggio non porta la porta del dispositivo di chi lo manda", c.deviceDoorId)
    return Device(id, c).also { withDoor(server, it) }
  }

  private fun withDoor(server: FakeKeysServer, d: Device) {
    val p = d.custody.createDeviceDoor(d.id, server.view())
    server.putDoor(p.upload)
    d.custody = p.custody
  }

  private fun rotate(server: FakeKeysServer, d: Device, kind: EpochKind = EpochKind.NORMAL) {
    val p = d.custody.rotate(server.view(), kind)
    server.postEpoch(p.upload)
    d.custody = p.custody
  }

  private fun write(c: KeyCustody, id: String, text: String): String {
    val set = c.appKeys(pkg, AppKeyPurpose.APP)
    return PampaRecordCipher(c.accountId, pkg).seal(set.keys.getValue(set.current), set.current, "memories", id, 1, text.toByteArray())
  }

  private fun read(c: KeyCustody, id: String, field: String): String {
    val keys = c.appKeys(pkg, AppKeyPurpose.APP).keys
    return PampaRecordCipher(c.accountId, pkg).open({ keys[it] }, "memories", id, 1, field).decodeToString()
  }

  @Test
  fun `14 - il cuore - dopo un furto il tablet riceve l'epoca nuova in silenzio, il perso no`() {
    val server = FakeKeysServer()
    val (phone, _) = start(server)
    val tablet = join(server, phone, "dev_tablet")
    val lost = join(server, phone, "dev_vecchio")
    assertEquals(4, server.honestView().doors.size)

    // Il telefono vecchio è perso: la sua porta se ne va con la revoca, e la rotazione dev'essere «lost».
    server.dropDevice(lost.id)
    server.lostPending = true
    rotate(server, phone, EpochKind.LOST)
    assertTrue(server.view().doors.none { it.doorId == "device:${lost.id}" })

    // Il tablet è indietro, lo sa, e si rimette in pari dalla sua porta: senza codice, passkey o QR.
    assertTrue(runCatching { tablet.custody.verify(server.view()) }.exceptionOrNull() is CustodyBehindException)
    tablet.custody = tablet.custody.catchUp(server.view())
    assertEquals(2, tablet.custody.current)
    val afterTheft = write(phone.custody, "dopo", "scritto dopo il furto")
    assertEquals("scritto dopo il furto", read(tablet.custody, "dopo", afterTheft))

    // Il perso non ha nessun sigillo dell'epoca 2: per lui la porta non c'è più.
    assertTrue(runCatching { lost.custody.catchUp(server.view()) }.exceptionOrNull() is CustodyBehindException)
    assertTrue(runCatching { read(lost.custody, "dopo", afterTheft) }.isFailure)

    // Alla rotazione dopo, il tablet si rimette in pari di nuovo; per il perso non nasce niente.
    rotate(server, phone)
    tablet.custody = tablet.custody.catchUp(server.view())
    assertEquals(3, tablet.custody.current)
    assertTrue(server.honestView().doors.none { it.doorId == "device:${lost.id}" })
  }

  @Test
  fun `14 - anche il tablet ruota, e il telefono si rimette in pari da lui`() {
    val server = FakeKeysServer()
    val (phone, _) = start(server)
    val tablet = join(server, phone, "dev_tablet")
    rotate(server, tablet)
    phone.custody = phone.custody.catchUp(server.view())
    assertEquals(2, phone.custody.current)
    phone.custody.verify(server.view())
  }

  @Test
  fun `14 - K2 - la porta del dispositivo non basta, e il codice non si toglie se resta solo lei`() {
    val server = FakeKeysServer()
    val onlyDevice = runCatching { KeyCustody.start(server.accountId, listOf(DoorSecret.device("dev_x", PampaCrypto.random(32)))) }
    assertTrue(onlyDevice.exceptionOrNull() is NeedsDoorException)
    val (_, _) = start(server)
    assertEquals("last_door", runCatching { server.deleteDoor("recovery") }.exceptionOrNull()?.message)
  }

  @Test
  fun `14 - server ostile - inventa la porta di un dispositivo con la sua pubblica, e il telefono non ruota`() {
    val server = FakeKeysServer()
    val (phone, _) = start(server)
    val evil = X25519KeyPair.random()
    val salt = PampaBase64.encode(PampaCrypto.random(16))
    server.tamper = { v ->
      v.copy(doors = v.doors + DoorInfo("device:dev_finto", DoorKind.DEVICE, PampaBase64.encode(evil.publicKey), salt, PampaBase64.encode(PampaCrypto.random(32)), 1, PampaBase64.encode(PampaCrypto.random(32)), emptyList()))
    }
    val r = runCatching { phone.custody.rotate(server.view()) }
    assertEquals("porta device:dev_finto: sigillo d'autenticità falso (K8)", r.exceptionOrNull()?.message)
    assertEquals(1, server.honestView().epoch)
  }

  @Test
  fun `14 - server ostile - cambia la pubblica della porta del tablet, poi la toglie`() {
    val server = FakeKeysServer()
    val (phone, _) = start(server)
    val tablet = join(server, phone, "dev_tablet")
    val evil = PampaBase64.encode(X25519KeyPair.random().publicKey)
    server.tamper = { v -> v.copy(doors = v.doors.map { if (it.doorId == "device:dev_tablet") it.copy(pub = evil) else it }) }
    assertEquals("porta device:dev_tablet: sigillo d'autenticità falso (K8)", runCatching { phone.custody.rotate(server.view()) }.exceptionOrNull()?.message)
    // Tolta: il tablet se ne accorge al giro dopo; il telefono no, perché le porte degli altri dispositivi se ne vanno
    // da sole quando escono.
    server.tamper = { v -> v.copy(doors = v.doors.filter { it.doorId != "device:dev_tablet" }) }
    assertEquals("la porta device:dev_tablet è sparita", runCatching { tablet.custody.verify(server.view()) }.exceptionOrNull()?.message)
    phone.custody.verify(server.view())
  }

  @Test
  fun `14 - il segreto della porta del dispositivo non parte mai, e resta nella custodia chiusa in classe madre`() {
    val server = FakeKeysServer()
    val (phone, code) = start(server)
    val tablet = join(server, phone, "dev_tablet")
    rotate(server, phone, EpochKind.NORMAL)
    tablet.custody = tablet.custody.catchUp(server.view())
    val everything = server.received.joinToString("\n")
    for (d in listOf(phone, tablet)) assertFalse(everything.contains(PampaBase64.encode(d.custody.deviceSecret()!!)))
    assertTrue(everything.contains(code.auth))
    // Il portachiavi la ricorda, chiusa con la chiave dei dati (madre): a telefono bloccato non si apre.
    val vault = MemoryCustodyVault()
    val ring = CustodyKeyring(vault, tmp.root)
    ring.saveCustody(tablet.custody, emptyList())
    val back = ring.loadCustody()!!
    assertEquals("device:dev_tablet", back.deviceDoorId)
    rotate(server, phone)
    assertEquals(3, back.catchUp(server.view()).current)
    vault.locked = true
    assertTrue(runCatching { ring.loadCustody() }.exceptionOrNull() is CustodyLockedException)
  }
}
