package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * R2 (review `muse-account`, bloccante e minori della custodia): il server ostile che **aggiunge** un sigillo per
 * un'epoca fuori dalla catena, una chiave dei dati di un byte, una porta senza sigilli, e una pubblica X25519 debole.
 * Sul codice di prima la prima prova finiva con `current = 99` e le chiavi delle app nate da una chiave del server.
 */
class KeyCustodyR2Test {

  private val pkg = "dev.pampa.pampai"

  private fun account(): Triple<FakeKeysServer, KeyCustody, RecoveryCode> {
    val server = FakeKeysServer()
    val code = RecoveryCode.generate()
    val start = KeyCustody.start(server.accountId, listOf(DoorSecret.recovery(code)))
    server.postRecovery(code.auth, start.upload.doors.single())
    server.postEpoch(start.upload.epoch)
    return Triple(server, start.custody, code)
  }

  /** Il server sigilla una chiave sua per l'epoca [epoch] verso la pubblica della porta: non serve nessun segreto. */
  private fun extraSeal(d: DoorInfo, epoch: Int, key: ByteArray) =
    SealInfo(epoch, PampaBase64.encode(CustodyMath.sealForDoor(PampaBase64.decode(d.pub), d.doorId, epoch, key)))

  private fun tampered(block: () -> Any?): String = try {
    val got = block()
    fail("doveva essere rifiutato, invece: $got")
    ""
  } catch (e: CustodyTamperedException) {
    e.message!!
  }

  @Test
  fun `il server aggiunge alla porta del codice un sigillo per un'epoca fuori dalla catena - rifiutato`() {
    val (server, _, code) = account()
    val evil = PampaCrypto.random(32)
    server.corruptDoor("recovery") { d -> d.copy(seals = d.seals + extraSeal(d, 99, evil)) }
    val message = tampered {
      val opened = KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code))
      // Sul codice di prima: l'epoca corrente diventava la 99, e le chiavi delle app quelle del server.
      "current=${opened.current}, epochs=${opened.epochs}, appKey del server=${
        PampaCrypto.equal(opened.appKeys(pkg, AppKeyPurpose.APP).keys.getValue(opened.current), CustodyMath.appKey(evil, server.accountId, pkg))
      }"
    }
    assertEquals("sigillo dell'epoca 99, fuori dalla catena", message)
  }

  @Test
  fun `una chiave dei dati di un byte fuori dalla catena - rifiutata`() {
    val (server, _, code) = account()
    server.corruptDoor("recovery") { d -> d.copy(seals = d.seals + extraSeal(d, 50, byteArrayOf(7))) }
    assertEquals("sigillo dell'epoca 50, fuori dalla catena", tampered { KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code)).current })
  }

  @Test
  fun `una chiave dei dati di un byte dentro la catena - rifiutata prima di usarla`() {
    val (server, _, code) = account()
    server.corruptDoor("recovery") { d -> d.copy(seals = listOf(extraSeal(d, 1, byteArrayOf(7)))) }
    assertEquals("la chiave dei dati dell'epoca 1 non è di 32 byte", tampered { KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code)) })
  }

  @Test
  fun `due sigilli per la stessa epoca - rifiutati`() {
    val (server, _, code) = account()
    server.corruptDoor("recovery") { d -> d.copy(seals = d.seals + extraSeal(d, 1, PampaCrypto.random(32))) }
    assertEquals("due sigilli per l'epoca 1", tampered { KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code)) })
  }

  @Test
  fun `il rimettersi in pari in silenzio con la porta del dispositivo - il sigillo in piu' e' rifiutato`() {
    val (server, phone, code) = account()
    val tablet0 = KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code))
    val withDoor = tablet0.createDeviceDoor("dev_tablet", server.view())
    server.putDoor(withDoor.upload)
    val tablet = withDoor.custody
    val p = phone.rotate(server.view())
    server.postEpoch(p.upload)
    // Il server ostile aggiunge alla porta del tablet un'epoca 99 sua: il tablet ruota «in silenzio» (catchUp).
    server.corruptDoor(withDoor.upload.doorId) { d -> d.copy(seals = d.seals + extraSeal(d, 99, PampaCrypto.random(32))) }
    assertEquals("sigillo dell'epoca 99, fuori dalla catena", tampered { tablet.catchUp(server.view()).current })
    // Senza il sigillo in più il tablet si rimette in pari, all'epoca vera.
    server.corruptDoor(withDoor.upload.doorId) { d -> d.copy(seals = d.seals.filter { it.epoch != 99 }) }
    assertEquals(2, tablet.catchUp(server.view()).current)
  }

  @Test
  fun `una porta senza sigilli - rifiutata, non NoSuchElementException`() {
    val (server, _, code) = account()
    server.corruptDoor("recovery") { d -> d.copy(seals = emptyList()) }
    val r = runCatching { KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code)) }
    assertTrue("era ${r.exceptionOrNull()}", r.exceptionOrNull() is CustodyTamperedException)
    assertEquals("manca il sigillo dell'epoca 1", r.exceptionOrNull()!!.message)
  }

  @Test
  fun `il passaggio con un'epoca in piu' fuori dalla catena - rifiutato`() {
    // Il sigillo del passaggio lo firma il telefono vecchio col segreto del QR: qui un telefono vecchio guasto (o
    // chiunque abbia s) ci mette un'epoca in più. Il nuovo non la prende.
    val (server, old, _) = account()
    val offer = TransferOffer.create(server.openTransfer())
    val qr = TransferQr.parse(offer.qr)!!
    val payload = TransferPayload.encode(mapOf(1 to old.cd(1)!!, 99 to PampaCrypto.random(32)))
    val sealed = PampaHpke.seal(qr.n, CustodyMath.transferInfo(qr.transferId), PampaCrypto.EMPTY, payload)
    val seal = TransferSeal(PampaBase64.encode(sealed), PampaBase64.encode(CustodyMath.transferMac(qr.s, qr.transferId, qr.n, sealed)))
    assertEquals("al passaggio c'è l'epoca 99, fuori dalla catena", tampered { KeyCustody.receiveTransfer(server.accountId, offer, seal, server.view()).current })
  }

  @Test
  fun `HPKE verso una pubblica X25519 debole - un rifiuto, non un'eccezione grezza`() {
    for (weak in listOf(ByteArray(32), ByteArray(32).also { it[0] = 1 })) {
      val r = runCatching { PampaHpke.seal(weak, "info".toByteArray(), PampaCrypto.EMPTY, PampaCrypto.random(32)) }
      assertTrue("era ${r.exceptionOrNull()}", r.exceptionOrNull() is PampaCryptoException)
    }
    // Il QR del passaggio con una N debole: il telefono vecchio rifiuta di sigillare.
    val (_, old, _) = account()
    val qr = TransferQr("xfr_1", ByteArray(32), PampaCrypto.random(16))
    assertTrue(runCatching { old.sealTransfer(qr) }.exceptionOrNull() is PampaCryptoException)
  }
}
