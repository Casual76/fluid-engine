package dev.antigravity.fluidengine.vault

import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Il dialogo finto: dice di sì (o quello che si sceglie) e «sblocca» il Keystore finto come farebbe il sistema. */
internal class FakeAuthenticator(private val keystore: MemoryVaultKeystore) : VaultAuthenticator {
  val requests = mutableListOf<VaultAuthRequest>()
  var answer: (VaultAuthRequest) -> VaultAuthResult = { VaultAuthResult.Success }

  override suspend fun authenticate(request: VaultAuthRequest): VaultAuthResult {
    requests += request
    val result = answer(request)
    if (result == VaultAuthResult.Success) {
      val cipher = request.cipher
      if (cipher != null) keystore.approve(cipher) else keystore.authenticate()
    }
    return result
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FluidVaultTest {
  @get:Rule val tmp = TemporaryFolder()

  private lateinit var keystore: MemoryVaultKeystore
  private lateinit var dir: File

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

  private fun biometric(relock: Int = 5, reserve: VaultReserve = VaultReserve.DeviceCredential) =
    VaultAccess.Biometric(relockAfter = relock.minutes, reserve = reserve)

  // --- il solo telefono -------------------------------------------------------------------------------------------

  @Test fun `una sezione «solo telefono» si apre da sola e tiene i valori fra un avvio e l'altro`() = runTest {
    val first = vault().section()
    first.values.putString("imap.password", "hunter2")
    first.values.putBytes("token", byteArrayOf(1, 2, 3))
    assertEquals("hunter2", first.values.getString("imap.password"))
    // Un processo nuovo: la stessa cartella e lo stesso Keystore.
    val again = vault().section()
    assertEquals("hunter2", again.values.getString("imap.password"))
    assertArrayEquals(byteArrayOf(1, 2, 3), again.values.getBytes("token"))
    assertEquals(setOf("imap.password", "token"), again.values.keys())
    again.values.remove("token")
    assertNull(vault().section().values.getBytes("token"))
    // Sul disco non c'è niente in chiaro.
    val onDisk = File(dir, "default/values.fv").readBytes()
    assertFalse(String(onDisk, Charsets.ISO_8859_1).contains("hunter2"))
    assertFalse(String(onDisk, Charsets.ISO_8859_1).contains("imap.password"))
  }

  @Test fun `i byte sigillati si aprono solo con gli stessi dati associati e nella stessa sezione`() = runTest {
    val vault = vault()
    val account = vault.section("account")
    val root = VaultCrypto.randomBytes(32)
    val box = account.seal(root, associated = "pampa/v1/cd/e=3".toByteArray())
    assertArrayEquals(root, account.open(box, associated = "pampa/v1/cd/e=3".toByteArray()))
    expectCorrupt { account.open(box, associated = "pampa/v1/cd/e=4".toByteArray()) }
    expectCorrupt { vault.section("altra").open(box, associated = "pampa/v1/cd/e=3".toByteArray()) }
    expectCorrupt { account.open(box.copyOf().also { it[20] = (it[20].toInt() xor 4).toByte() }, "pampa/v1/cd/e=3".toByteArray()) }
    expectCorrupt { account.open(byteArrayOf(1, 2, 3, 4, 5), ByteArray(0)) }
  }

  @Test fun `un Keystore azzerato rende la sezione persa, con la frase pronta`() = runTest {
    vault().section().values.putString("k", "v")
    keystore.wipe()
    try {
      vault().section().values.getString("k")
      fail("la chiave non c'è più")
    } catch (e: VaultException.Invalidated) {
      assertEquals(VaultInvalidation.KeyLost, e.reason)
    }
    val labels = VaultLabels()
    assertTrue(labels.message(VaultUnlock.Lost(VaultInvalidation.KeyLost), VaultReserve.None)!!.contains("non c'è più"))
  }

  @Test fun `una sezione chiesta con un altro accesso è un errore`() = runTest {
    val vault = vault()
    vault.section("note", biometric())
    try {
      vault.section("note", VaultAccess.Device())
      fail("accesso diverso")
    } catch (e: IllegalStateException) {
      assertTrue(e.message!!.contains("note"))
    }
  }

  // --- l'impronta ---------------------------------------------------------------------------------------------------

  @Test fun `una sezione all'impronta nasce aperta, chiusa non si legge, e si apre col dialogo e il CryptoObject`() = runTest {
    val notes = vault().section("note", biometric())
    val auth = FakeAuthenticator(keystore)
    assertEquals(VaultUnlock.Opened(), notes.unlock(auth, VaultPrompt("Sblocca le note")))
    assertTrue(auth.requests.isEmpty()) // la prima volta nasce, senza dialogo
    notes.values.putString("nota", "Il codice del portone è 4821")
    notes.lock()
    assertFalse(notes.isOpen)
    assertEquals(VaultSectionState.Closed, notes.state.value)
    expectLocked { notes.values.getString("nota") }

    val reopened = vault().section("note", biometric())
    assertEquals(VaultUnlock.Opened(), reopened.unlock(auth, VaultPrompt("Sblocca le note")))
    val request = auth.requests.single()
    assertEquals(VaultAuthMethod.Biometric, request.method)
    assertNotNull("il dialogo dell'impronta riceve il CryptoObject", request.cipher)
    assertEquals("Sblocca le note", request.title)
    assertEquals(VaultLabels().useDeviceCredential, request.fallback)
    assertEquals("Il codice del portone è 4821", reopened.values.getString("nota"))
  }

  @Test fun `senza l'impronta il Keystore non apre la chiave, anche se l'app prova da sola`() = runTest {
    vault().section("note", biometric()).apply {
      unlock(FakeAuthenticator(keystore))
      values.putString("nota", "x")
    }
    val section = vault().section("note", biometric())
    // Un dialogo che dice «sì» senza passare dal sistema: il Keystore finto non ha visto l'impronta.
    val liar = VaultAuthenticator { VaultAuthResult.Success }
    val result = section.unlock(liar)
    assertTrue("$result", result is VaultUnlock.Failed)
    assertFalse(section.isOpen)
  }

  @Test fun `la sezione si richiude da sola dopo il tempo senza usarla, e ogni uso sposta la richiusura`() = runTest {
    val notes = vault().section("note", biometric(relock = 5))
    notes.unlock(FakeAuthenticator(keystore))
    notes.values.putString("nota", "x")
    advanceTimeBy(4.minutes.inWholeMilliseconds)
    runCurrent()
    assertTrue(notes.isOpen)
    assertEquals("x", notes.values.getString("nota")) // un uso: altri 5 minuti
    advanceTimeBy(4.minutes.inWholeMilliseconds)
    runCurrent()
    assertTrue("usata 4 minuti fa: ancora aperta", notes.isOpen)
    advanceTimeBy(1.minutes.inWholeMilliseconds + 1)
    runCurrent()
    assertFalse(notes.isOpen)
    assertEquals(VaultSectionState.Closed, notes.state.value)
    expectLocked { notes.values.getString("nota") }
  }

  @Test fun `lo schermo spento chiude le sezioni all'impronta e quelle a telefono sbloccato, non le altre`() = runTest {
    val vault = vault()
    val notes = vault.section("note", biometric())
    notes.unlock(FakeAuthenticator(keystore))
    val unlockedOnly = vault.section("bancomat", VaultAccess.Device(whileUnlocked = true))
    unlockedOnly.values.putString("pin", "1234")
    val plain = vault.section()
    plain.values.putString("tema", "scuro")
    vault.onScreenOff()
    assertFalse(notes.isOpen)
    assertFalse(unlockedOnly.isOpen)
    assertTrue(plain.isOpen)
  }

  @Test fun `annullare il dialogo lascia la sezione chiusa`() = runTest {
    vault().section("note", biometric()).unlock(FakeAuthenticator(keystore))
    val section = vault().section("note", biometric())
    val auth = FakeAuthenticator(keystore).apply { answer = { VaultAuthResult.Cancelled } }
    assertEquals(VaultUnlock.Cancelled, section.unlock(auth))
    assertFalse(section.isOpen)
  }

  @Test fun `il tasto della riserva passa al codice del telefono senza toccare la chiave dell'impronta`() = runTest {
    vault().section("note", biometric()).apply {
      unlock(FakeAuthenticator(keystore))
      values.putString("nota", "x")
    }
    val section = vault().section("note", biometric())
    val auth = FakeAuthenticator(keystore).apply {
      answer = { if (it.method == VaultAuthMethod.Biometric) VaultAuthResult.Fallback else VaultAuthResult.Success }
    }
    assertEquals(VaultUnlock.Opened(), section.unlock(auth))
    assertEquals(listOf(VaultAuthMethod.Biometric, VaultAuthMethod.DeviceCredential), auth.requests.map { it.method })
    assertEquals("x", section.values.getString("nota"))
  }

  @Test fun `un'impronta aggiunta chiude la chiave, la frase pronta, il codice del telefono, e la chiave si rifà`() = runTest {
    vault().section("note", biometric()).apply {
      unlock(FakeAuthenticator(keystore))
      values.putString("nota", "Il codice del portone è 4821")
    }
    val before = keystore.aliases
    keystore.enrollBiometric()

    val section = vault().section("note", biometric())
    val auth = FakeAuthenticator(keystore)
    val result = section.unlock(auth, VaultPrompt("Sblocca le note"))
    assertEquals(VaultUnlock.Opened(recovered = VaultInvalidation.BiometricsChanged), result)
    // Nessun dialogo dell'impronta (la chiave non vale più): subito il codice, con la frase pronta.
    val request = auth.requests.single()
    assertEquals(VaultAuthMethod.DeviceCredential, request.method)
    assertEquals(VaultLabels().biometricsChanged, request.subtitle)
    assertEquals("Il codice del portone è 4821", section.values.getString("nota"))
    // La chiave dell'impronta è nuova; la prossima volta basta lei.
    assertFalse(keystore.aliases == before)
    section.lock()
    val next = FakeAuthenticator(keystore)
    assertEquals(VaultUnlock.Opened(), vault().section("note", biometric()).unlock(next))
    assertEquals(listOf(VaultAuthMethod.Biometric), next.requests.map { it.method })
  }

  @Test fun `senza riserva un'impronta aggiunta perde i dati, e reset riparte vuota`() = runTest {
    vault().section("password", biometric(reserve = VaultReserve.None)).apply {
      unlock(FakeAuthenticator(keystore))
      values.putString("imap", "hunter2")
    }
    keystore.enrollBiometric()
    val section = vault().section("password", biometric(reserve = VaultReserve.None))
    val result = section.unlock(FakeAuthenticator(keystore))
    assertEquals(VaultUnlock.Lost(VaultInvalidation.BiometricsChanged), result)
    assertEquals(VaultSectionState.Lost(VaultInvalidation.BiometricsChanged), section.state.value)
    assertEquals(VaultLabels().biometricsChangedLost, VaultLabels().message(result, VaultReserve.None))
    section.reset()
    assertEquals(VaultUnlock.Opened(), section.unlock(FakeAuthenticator(keystore)))
    assertNull(section.values.getString("imap"))
  }

  @Test fun `il blocco schermo tolto perde la sezione anche con il codice del telefono come riserva`() = runTest {
    vault().section("note", biometric()).apply {
      unlock(FakeAuthenticator(keystore))
      values.putString("nota", "x")
    }
    keystore.removeScreenLock()
    val result = vault().section("note", biometric()).unlock(FakeAuthenticator(keystore))
    assertEquals(VaultUnlock.Lost(VaultInvalidation.ScreenLockRemoved), result)
    assertTrue(VaultLabels().message(result, VaultReserve.DeviceCredential)!!.contains("blocco schermo"))
  }

  @Test fun `con il PIN dell'app come riserva la sezione sopravvive anche al blocco schermo tolto`() = runTest {
    val access = biometric(reserve = VaultReserve.AppSecret)
    vault().section("note", access).apply {
      unlock(FakeAuthenticator(keystore))
      values.putString("nota", "x")
      setReserveSecret("482913".toCharArray())
    }
    keystore.enrollBiometric()
    val section = vault().section("note", access)
    assertEquals(VaultUnlock.NeedsSecret(VaultInvalidation.BiometricsChanged), section.unlock(FakeAuthenticator(keystore)))
    assertEquals(VaultUnlock.WrongSecret, section.unlockWithSecret("000000".toCharArray()))
    assertFalse(section.isOpen)
    assertEquals(VaultUnlock.Opened(recovered = VaultInvalidation.BiometricsChanged), section.unlockWithSecret("482913".toCharArray()))
    assertEquals("x", section.values.getString("nota"))

    // E dopo, senza blocco schermo: il PIN dell'app apre ancora.
    section.lock()
    keystore.removeScreenLock()
    val after = vault().section("note", access)
    val result = after.unlock(FakeAuthenticator(keystore))
    assertEquals(VaultUnlock.NeedsSecret(VaultInvalidation.ScreenLockRemoved), result)
    assertEquals(VaultUnlock.Opened(recovered = VaultInvalidation.ScreenLockRemoved), after.unlockWithSecret("482913".toCharArray()))
    assertEquals("x", after.values.getString("nota"))
  }

  @Test fun `senza impronte registrate la sezione nasce col solo codice del telefono, e l'impronta arriva dopo`() = runTest {
    keystore = MemoryVaultKeystore { testScheduler.currentTime }.apply { biometricsEnrolled = false }
    vault().section("note", biometric()).apply {
      assertEquals(VaultUnlock.Opened(), unlock(FakeAuthenticator(keystore)))
      values.putString("nota", "x")
      lock()
    }
    val first = FakeAuthenticator(keystore)
    vault().section("note", biometric()).apply {
      assertEquals(VaultUnlock.Opened(), unlock(first))
      lock()
    }
    assertEquals(listOf(VaultAuthMethod.DeviceCredential), first.requests.map { it.method })
    // Registra un'impronta: al prossimo codice la chiave dell'impronta nasce, e poi basta lei.
    keystore.biometricsEnrolled = true
    vault().section("note", biometric()).apply { unlock(FakeAuthenticator(keystore)); lock() }
    val later = FakeAuthenticator(keystore)
    vault().section("note", biometric()).unlock(later)
    assertEquals(listOf(VaultAuthMethod.Biometric), later.requests.map { it.method })
  }

  @Test fun `senza blocco schermo una sezione all'impronta non nasce, e lo dice`() = runTest {
    keystore = MemoryVaultKeystore { testScheduler.currentTime }.apply { deviceSecure = false }
    val result = vault().section("note", biometric()).unlock(FakeAuthenticator(keystore))
    assertEquals(VaultUnlock.Unavailable(VaultUnavailability.NoScreenLock), result)
    assertTrue(VaultLabels().message(result, VaultReserve.DeviceCredential)!!.contains("blocco schermo"))
  }

  // --- la chiave diretta (engine-ai) --------------------------------------------------------------------------------

  @Test fun `la chiave diretta scrive nonce, testo cifrato e tag, e si riapre`() = runTest {
    val memory = MemoryVaultKeystore()
    val key = FluidKeystoreKey("fluidengine.ai", memory)
    assertFalse(key.exists)
    val sealed = key.seal("sk-or-v1-segreto".toByteArray())
    assertTrue(key.exists)
    assertEquals(setOf("fluidengine.ai"), memory.aliases)
    assertEquals(12 + "sk-or-v1-segreto".length + 16, sealed.size)
    assertEquals("sk-or-v1-segreto", String(key.open(sealed)))
    expectCorrupt { key.open(sealed.copyOf().also { it[13] = (it[13].toInt() xor 1).toByte() }) }
    key.delete()
    try {
      key.open(sealed)
      fail("chiave tolta")
    } catch (e: VaultException.Invalidated) {
      assertEquals(VaultInvalidation.KeyLost, e.reason)
    }
  }

  private suspend fun expectLocked(block: suspend () -> Unit) {
    try {
      block()
      fail("la sezione doveva essere chiusa")
    } catch (e: VaultException.Locked) {
      // giusto
    }
  }

  private suspend fun expectCorrupt(block: suspend () -> Unit) {
    try {
      block()
      fail("doveva essere un dato non valido")
    } catch (e: VaultException.Corrupt) {
      // giusto
    }
  }
}
