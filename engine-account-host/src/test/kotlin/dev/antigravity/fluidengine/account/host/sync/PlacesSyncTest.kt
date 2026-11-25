package dev.antigravity.fluidengine.account.host.sync

import dev.antigravity.fluidengine.account.Place
import dev.antigravity.fluidengine.account.host.custody.DoorSecret
import dev.antigravity.fluidengine.account.host.custody.KeyCustody
import dev.antigravity.fluidengine.account.host.custody.RecoveryCode
import dev.antigravity.fluidengine.sync.SyncFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * F10.8 · I luoghi comuni fra i dispositivi (`b\chiavi.md` § 8): lo spazio `places` di `engine-sync` sopra le righe
 * cifrate del server, con `K_com(places)`. Il server finto vede solo byte sigillati.
 */
class PlacesSyncTest {
  @get:Rule val tmp = TemporaryFolder()

  private val custody: KeyCustody = KeyCustody.start("acc_1", listOf(DoorSecret.recovery(RecoveryCode.generate()))).custody

  private fun pair(server: FakeSharedServer) =
    server.Device("a", "acc_1", custody, tmp.newFolder()) to server.Device("b", "acc_1", custody, tmp.newFolder())

  private val casa = Place("casa", "Casa", Place.HOME, 43.7696, 11.2558, "Via de' Neri 4, Firenze")
  private val scuola = Place("scuola", "Scuola", Place.SCHOOL, 43.7731, 11.2560)

  /** Qualche millisecondo: due scritture una dopo l'altra, su due dispositivi, hanno orologi in quell'ordine. */
  private fun tick() = Thread.sleep(20)

  @Test
  fun `un luogo nato su un dispositivo arriva sull'altro, e il server vede solo byte cifrati`() = runBlocking {
    val server = FakeSharedServer()
    val (a, b) = pair(server)
    a.places.put(null, casa)
    a.places.put(null, scuola)
    val r = a.placesSync.sync() as StoreSyncResult.Ok
    assertEquals(2, r.pushed)
    b.notified.clear()
    assertTrue((b.placesSync.sync() as StoreSyncResult.Ok).changed)
    assertEquals(listOf(casa, scuola), b.places.all())
    assertEquals(listOf(listOf("places")), b.notified)
    // Niente in chiaro sul server: né i nomi, né l'indirizzo, né le coordinate, né l'id del luogo.
    val everything = server.bodies.joinToString("\n") + server.rows.values.joinToString("\n") { it.ciphertext?.decodeToString().orEmpty() }
    for (secret in listOf("Casa", "Neri", "43.7696", "11.2558", "\"casa\"", "Scuola")) assertFalse(secret, everything.contains(secret))
    // Gli identificativi delle righe sono casuali, non «casa».
    assertTrue(server.rows.keys.none { it.contains("casa") || it.contains("scuola") })
  }

  @Test
  fun `conflitto - lo stesso campo cambiato su due dispositivi, vince la scrittura piu recente su tutti e due`() = runBlocking {
    val server = FakeSharedServer()
    val (a, b) = pair(server)
    a.places.put(null, casa)
    a.placesSync.sync()
    b.placesSync.sync()
    server.offline = true
    a.places.put(null, casa.copy(name = "Casa al mare"))
    tick()
    b.places.put(null, casa.copy(name = "Casa nuova"))
    tick()
    // Un altro campo dello stesso luogo, cambiato dopo su A: si tengono tutti e due i cambi, ognuno col suo orologio.
    a.places.put(null, casa.copy(name = "Casa al mare", address = "Viale dei Mille 1"))
    server.offline = false
    a.placesSync.sync()
    b.placesSync.sync()
    a.placesSync.sync()
    for (d in listOf(a, b)) {
      val p = d.places.all().single()
      assertEquals(d.name, "Casa nuova", p.name)
      assertEquals(d.name, "Viale dei Mille 1", p.address)
    }
  }

  @Test
  fun `una cancellazione vince su una modifica arrivata insieme`() = runBlocking {
    val server = FakeSharedServer()
    val (a, b) = pair(server)
    a.places.put(null, casa)
    a.placesSync.sync()
    b.placesSync.sync()
    a.places.delete(null, "casa")
    b.places.put(null, casa.copy(name = "Casa (nuova)"))
    b.placesSync.sync()
    a.placesSync.sync()
    b.placesSync.sync()
    assertTrue(a.places.all().isEmpty())
    assertTrue(b.places.all().isEmpty())
  }

  @Test
  fun `lo stesso luogo creato su due dispositivi senza vedersi - ne resta uno, quello scritto per ultimo`() = runBlocking {
    val server = FakeSharedServer()
    val (a, b) = pair(server)
    a.places.put(null, casa)
    tick()
    b.places.put(null, casa.copy(latitude = 43.8, longitude = 11.3))
    a.placesSync.sync()
    b.placesSync.sync()
    a.placesSync.sync()
    for (d in listOf(a, b)) assertEquals(d.name, listOf(casa.copy(latitude = 43.8, longitude = 11.3)), d.places.all())
    assertEquals(1, server.rows.values.count { !it.deleted })
  }

  @Test
  fun `il server cattivo scambia due righe - l'altro dispositivo se ne accorge e non le applica`() = runBlocking {
    val server = FakeSharedServer()
    val (a, b) = pair(server)
    a.places.put(null, casa)
    a.places.put(null, scuola)
    a.placesSync.sync()
    val (r1, r2) = server.rows.values.toList()
    val c = r1.ciphertext
    r1.ciphertext = r2.ciphertext
    r2.ciphertext = c
    val r = b.placesSync.sync()
    assertTrue(r.toString(), r is StoreSyncResult.Failed)
    assertTrue(b.places.all().isEmpty())
  }

  @Test
  fun `senza rete, senza account o senza chiavi i luoghi restano sul telefono`() = runBlocking {
    val server = FakeSharedServer()
    val (a, _) = pair(server)
    server.offline = true
    a.places.put(null, casa)
    assertEquals(StoreSyncResult.Offline, a.placesSync.sync())
    assertEquals(listOf(casa), a.places.all())
    server.offline = false
    assertTrue(a.placesSync.sync() is StoreSyncResult.Ok)
    assertEquals(1, server.rows.size)
    val noKeys = server.Device("c", "acc_1", null, tmp.newFolder())
    noKeys.places.put(null, scuola)
    assertEquals(StoreSyncResult.NoE2e, noKeys.placesSync.sync())
    noKeys.session.signOut()
    assertEquals(StoreSyncResult.SignedOut, noKeys.placesSync.sync())
    assertEquals(listOf(scuola), noKeys.places.all())
  }

  @Test
  fun `uscire dall'account non toglie i luoghi, l'uscita da remoto si`() = runBlocking {
    val server = FakeSharedServer()
    val (a, _) = pair(server)
    a.places.put(null, casa)
    a.placesSync.sync()
    a.session.signOut()
    // Una cassaforte nuova per i luoghi, come dopo un riavvio dello Store: il file si riapre.
    val reopened = dev.antigravity.fluidengine.account.host.PlacesStore(a.placesVault, java.io.File(a.dir, "luoghi"), dev.antigravity.fluidengine.account.host.AppPolicy())
    assertEquals(listOf(casa), reopened.all())
    reopened.wipe()
    assertTrue(reopened.all().isEmpty())
    // Anche un deposito nuovo sulla stessa cartella non trova più niente.
    assertTrue(dev.antigravity.fluidengine.account.host.PlacesStore(a.placesVault, java.io.File(a.dir, "luoghi"), dev.antigravity.fluidengine.account.host.AppPolicy()).all().isEmpty())
  }

  @Test
  fun `un'epoca che il server non conosce e un rifiuto, non un'eccezione`() = runBlocking {
    val server = FakeSharedServer()
    val (a, _) = pair(server)
    server.keyEpoch = 0
    a.places.put(null, casa)
    val r = a.placesSync.sync() as StoreSyncResult.Failed
    assertTrue(r.failure is SyncFailure.Rejected)
  }
}
