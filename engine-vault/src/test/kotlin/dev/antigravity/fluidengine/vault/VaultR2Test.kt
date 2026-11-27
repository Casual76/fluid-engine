package dev.antigravity.fluidengine.vault

import java.io.File
import java.io.OutputStream
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * R2 (muse-f8-prime, maggiori 5 e 6 e minori della cassaforte): la scheda che ricorda la riserva e «solo a telefono
 * sbloccato», i DataStore vietati dove la chiave esce allo schermo spento, due scritture insieme, la scheda illeggibile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultR2Test {
  @get:Rule val tmp = TemporaryFolder()

  private lateinit var keystore: MemoryVaultKeystore
  private lateinit var dir: File
  private val realScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @After fun stop() {
    realScope.cancel()
  }

  /** Una cassaforte nuova sulla stessa cartella e lo stesso Keystore: un processo nuovo (un'app aggiornata). */
  private fun TestScope.vault(): FluidVault {
    if (!::keystore.isInitialized) keystore = MemoryVaultKeystore { testScheduler.currentTime }
    if (!::dir.isInitialized) dir = tmp.newFolder("vault")
    return FluidVault.create(
      dir = dir,
      keystore = keystore,
      clock = { testScheduler.currentTime },
      scope = backgroundScope,
      io = StandardTestDispatcher(testScheduler),
      secretIterations = 1_000,
    )
  }

  private fun biometric(reserve: VaultReserve) = VaultAccess.Biometric(relockAfter = 5.minutes, reserve = reserve)

  // --- maggiore 5: la scheda ricorda come è nata la sezione -----------------------------------------------------------

  @Test fun `una sezione nata col codice del telefono come riserva non si apre chiesta col PIN dell'app`() = runTest {
    vault().section("note", biometric(VaultReserve.DeviceCredential)).apply {
      unlock(FakeAuthenticator(keystore))
      values.putString("nota", "Il codice del portone è 4821")
      lock()
    }
    // L'app si aggiorna e chiede la stessa sezione con un'altra riserva.
    val changed = vault().section("note", biometric(VaultReserve.AppSecret))
    val result = changed.unlock(FakeAuthenticator(keystore))
    assertTrue("la riserva è cambiata: $result", result is VaultUnlock.Failed)
    assertFalse(changed.isOpen)

    // Con la riserva con cui è nata si apre ancora, e i dati ci sono.
    val same = vault().section("note", biometric(VaultReserve.DeviceCredential))
    assertEquals(VaultUnlock.Opened(), same.unlock(FakeAuthenticator(keystore)))
    assertEquals("Il codice del portone è 4821", same.values.getString("nota"))
  }

  @Test fun `una sezione nata senza «solo a telefono sbloccato» non si legge chiesta con, e viceversa`() = runTest {
    vault().section("conti").values.putString("iban", "IT60X0542811101000000123456")
    try {
      vault().section("conti", VaultAccess.Device(whileUnlocked = true)).values.getString("iban")
      fail("la chiave è nata senza setUnlockedDeviceRequired: a schermo spento si aprirebbe")
    } catch (e: VaultException.Corrupt) {
      assertTrue(e.message!!.contains("conti"))
    }
    vault().section("bancomat", VaultAccess.Device(whileUnlocked = true)).values.putString("pin", "1234")
    try {
      vault().section("bancomat", VaultAccess.Device()).values.getString("pin")
      fail("la sezione è nata «solo a telefono sbloccato»")
    } catch (e: VaultException.Corrupt) {
      assertTrue(e.message!!.contains("bancomat"))
    }
    // Chiesta com'è nata, si legge.
    assertEquals("IT60X0542811101000000123456", vault().section("conti").values.getString("iban"))
    assertEquals("1234", vault().section("bancomat", VaultAccess.Device(whileUnlocked = true)).values.getString("pin"))
  }

  @Test fun `una scheda scritta prima di R2, senza il segno, si accetta una volta e poi lo ricorda`() = runTest {
    vault().section("vecchia", VaultAccess.Device(whileUnlocked = true)).values.putString("k", "v")
    // La scheda come la scriveva la versione di prima: senza l'ultimo campo (4 byte).
    val record = File(dir, "vecchia.key")
    record.writeBytes(record.readBytes().let { it.copyOf(it.size - 4) })
    assertEquals("v", vault().section("vecchia", VaultAccess.Device(whileUnlocked = true)).values.getString("k"))
    try {
      vault().section("vecchia", VaultAccess.Device()).values.getString("k")
      fail("la scheda ora ricorda «solo a telefono sbloccato»")
    } catch (e: VaultException.Corrupt) {
      // giusto
    }
  }

  // --- maggiore 6: niente DataStore dove la chiave esce allo schermo spento -------------------------------------------

  @Test fun `un DataStore in una sezione «solo a telefono sbloccato» è un errore`() = runTest {
    val section = vault().section("conti", VaultAccess.Device(whileUnlocked = true))
    try {
      section.preferencesDataStore("conti")
      fail("un DataStore terrebbe i dati in chiaro in memoria anche a schermo spento")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message!!.contains("values o file"))
    }
  }

  // --- minori: i file e la scheda -------------------------------------------------------------------------------------

  @Test fun `due scritture insieme dello stesso file non si pestano`() = runBlocking {
    val vault = FluidVault.create(dir = tmp.newFolder("file"), keystore = MemoryVaultKeystore(), clock = { System.nanoTime() / 1_000_000 }, scope = realScope, io = Dispatchers.IO)
    val file = vault.section().file("referto.pdf")
    val a = ByteArray(200_000) { 1 }
    val b = ByteArray(200_000) { 2 }
    val slow: (ByteArray) -> (OutputStream) -> Unit = { bytes ->
      { out ->
        var at = 0
        while (at < bytes.size) {
          out.write(bytes, at, 20_000)
          at += 20_000
          Thread.sleep(2)
        }
      }
    }
    repeat(5) { round ->
      val failures = listOf(a, b).map { bytes ->
        async(Dispatchers.IO) { runCatching { file.write(slow(bytes)) }.exceptionOrNull() }
      }.awaitAll().filterNotNull()
      assertEquals("giro $round: $failures", emptyList<Throwable>(), failures)
      val read = file.read()
      assertTrue("giro $round: il file è mescolato", read.contentEquals(a) || read.contentEquals(b))
    }
  }

  @Test fun `una scrittura del file che si interrompe lascia quello di prima`() = runBlocking {
    val vault = FluidVault.create(dir = tmp.newFolder("file2"), keystore = MemoryVaultKeystore(), clock = { System.nanoTime() / 1_000_000 }, scope = realScope, io = Dispatchers.IO)
    val file = vault.section().file("diario")
    file.write("prima".toByteArray())
    try {
      file.write { out -> out.write("dopo, a me".toByteArray()); error("l'app si ferma a metà") }
      fail("il blocco si è fermato")
    } catch (e: IllegalStateException) {
      // giusto
    }
    assertArrayEquals("prima".toByteArray(), file.read())
    assertEquals("nessun file a metà accanto", listOf("diario.fvs"), file.file.parentFile!!.list()!!.sorted())
  }

  @Test fun `una scheda della sezione che non si legge dà un esito, non un'eccezione`() = runTest {
    vault().section("note", biometric(VaultReserve.AppSecret)).apply {
      unlock(FakeAuthenticator(keystore))
      setReserveSecret("482913".toCharArray())
      lock()
    }
    // La scheda diventa illeggibile (un errore del disco): una cartella al suo posto.
    val record = File(dir, "note.key")
    assertTrue(record.delete())
    assertTrue(record.mkdirs())
    val section = vault().section("note", biometric(VaultReserve.AppSecret))
    val result = section.unlock(FakeAuthenticator(keystore))
    assertTrue("$result", result is VaultUnlock.Unavailable)
    val secret = section.unlockWithSecret("482913".toCharArray())
    assertTrue("$secret", secret is VaultUnlock.Unavailable)
    assertFalse(section.isOpen)
  }
}
