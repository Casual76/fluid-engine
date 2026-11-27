package dev.antigravity.fluidengine.security

import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.MemoryVaultKeystore
import dev.antigravity.fluidengine.vault.VaultAccess
import java.io.File
import java.security.Provider
import java.security.Security
import java.security.spec.KeySpec
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.SecretKeyFactorySpi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Il PBKDF2 del sistema, che si segna su quale filo lavora. */
class SpyPbkdf2 : SecretKeyFactorySpi() {
  private val real = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", "SunJCE")

  override fun engineGenerateSecret(keySpec: KeySpec): SecretKey {
    threads += Thread.currentThread().name
    return real.generateSecret(keySpec)
  }

  override fun engineGetKeySpec(key: SecretKey, keySpec: Class<*>): KeySpec = real.getKeySpec(key, keySpec)

  override fun engineTranslateKey(key: SecretKey): SecretKey = real.translateKey(key)

  companion object {
    val threads = CopyOnWriteArrayList<String>()
  }
}

@Suppress("DEPRECATION")
private object SpyProvider : Provider("FluidPinSpy", 1.0, "il PBKDF2 del PIN, che dice su quale filo gira") {
  init {
    putService(
      object : Provider.Service(this, "SecretKeyFactory", "PBKDF2WithHmacSHA256", SpyPbkdf2::class.java.name, null, null) {
        override fun newInstance(constructorParameter: Any?): Any = SpyPbkdf2()
      },
    )
  }
}

/** R2 (muse-f8-prime, maggiori 2 e 3, minore «fail-open»): il PIN dopo un riavvio, il PBKDF2 fuori dal filo di chi chiama. */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidAppLockR2Test {
  @get:Rule val tmp = TemporaryFolder()

  private val keystore = MemoryVaultKeystore()
  private val dir by lazy { tmp.newFolder("vault") }

  /** L'orologio dall'accensione (`elapsedRealtime`) e l'ora vera: un riavvio rimette a zero il primo. */
  private var elapsed = 1_000_000L
  private var wall = 1_800_000_000_000L

  @After fun stop() {
    Security.removeProvider(SpyProvider.name)
    SpyPbkdf2.threads.clear()
  }

  /** Un blocco nuovo sulla stessa cartella e lo stesso Keystore: un processo nuovo. */
  private fun TestScope.lock(): FluidAppLock {
    val vault = FluidVault.create(
      dir = dir,
      keystore = keystore,
      clock = { testScheduler.currentTime },
      scope = backgroundScope,
      io = StandardTestDispatcher(testScheduler),
    )
    return FluidAppLock(
      section = vault.section("fluid-lock-app", VaultAccess.Device()),
      vault = null,
      clock = { elapsed },
      scope = backgroundScope,
      pinIterations = 1_000,
      lockVaultOnLock = false,
      wallClock = { wall },
    ).also { runCurrent() }
  }

  @Test fun `dopo un riavvio con cinque errori si aspetta una volta, poi il PIN giusto apre`() = runTest {
    val before = lock()
    before.setEnabled(true)
    before.setPin("482913".toCharArray())
    repeat(5) { before.checkPin("000000".toCharArray()) }

    // Il telefono si riavvia dieci minuti dopo: l'orologio dall'accensione riparte da pochi secondi.
    elapsed = 5_000
    wall += 10 * 60_000
    val after = lock()
    assertEquals(FluidAppLockState.Locked, after.state.value)
    val first = after.checkPin("482913".toCharArray())
    assertTrue("subito dopo il riavvio si aspetta da capo: $first", first is FluidPinCheck.Wait)

    // Passato il minuto, il PIN giusto apre (prima l'attesa ripartiva a ogni tentativo, per sempre).
    elapsed += 61_000
    wall += 61_000
    assertEquals(FluidPinCheck.Correct, after.checkPin("482913".toCharArray()))
    assertEquals(FluidAppLockState.Unlocked, after.state.value)
  }

  @Test fun `l'orologio spostato in avanti fa aspettare una volta, non per sempre`() = runTest {
    val lock = lock()
    lock.setEnabled(true)
    lock.setPin("4821".toCharArray())
    repeat(5) { lock.checkPin("0000".toCharArray()) }
    elapsed += 61_000
    wall += 61_000 + 3_600_000 // la persona sposta l'ora di un'ora
    assertTrue(lock.checkPin("4821".toCharArray()) is FluidPinCheck.Wait)
    elapsed += 61_000
    wall += 61_000
    assertEquals(FluidPinCheck.Correct, lock.checkPin("4821".toCharArray()))
  }

  @Test fun `il PIN si sceglie e si controlla fuori dal filo di chi chiama`() = runTest {
    Security.insertProviderAt(SpyProvider, 1)
    val lock = lock()
    val caller = Thread.currentThread().name
    lock.setPin("482913".toCharArray())
    assertEquals(FluidPinCheck.Correct, lock.checkPin("482913".toCharArray()))
    assertEquals("scelta e controllo: due PBKDF2", 2, SpyPbkdf2.threads.size)
    assertTrue("il PBKDF2 ha girato su «$caller» (il thread principale, nell'app): ${SpyPbkdf2.threads}", SpyPbkdf2.threads.none { it == caller })
  }

  @Test fun `le impostazioni che non si leggono per un errore passeggero non spengono il blocco, e si rileggono`() = runTest {
    lock().setEnabled(true)
    // Il file dei valori diventa illeggibile (un errore del disco): una cartella al suo posto.
    val values = File(dir, "fluid-lock-app/values.fv")
    val saved = File(dir, "da-parte.fv")
    assertTrue(values.renameTo(saved))
    assertTrue(values.mkdirs())

    val cold = lock()
    assertEquals("l'app resta coperta mentre non si sa se il blocco è acceso", FluidAppLockState.Loading, cold.state.value)
    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(FluidAppLockState.Loading, cold.state.value)

    // Il disco torna a posto: alla prossima prova si legge, e il blocco è acceso.
    assertTrue(values.delete())
    assertTrue(saved.renameTo(values))
    advanceTimeBy(60_000)
    runCurrent()
    assertEquals(FluidAppLockState.Locked, cold.state.value)
    assertTrue(cold.settings.value.enabled)
  }

  @Test fun `col Keystore azzerato le impostazioni sono perse e il blocco riparte spento`() = runTest {
    lock().setEnabled(true)
    keystore.wipe()
    val cold = lock()
    assertEquals(FluidAppLockState.Unlocked, cold.state.value)
    assertFalse(cold.settings.value.enabled)
  }
}
