package dev.antigravity.fluidengine.security

import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.MemoryVaultKeystore
import dev.antigravity.fluidengine.vault.VaultAccess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class FluidAppLockTest {
  @get:Rule val tmp = TemporaryFolder()

  private lateinit var vault: FluidVault
  private val dir by lazy { tmp.newFolder("vault") }

  private fun TestScope.lock(): FluidAppLock {
    vault = FluidVault.create(
      dir = dir,
      keystore = keystore,
      clock = { testScheduler.currentTime },
      scope = backgroundScope,
      io = StandardTestDispatcher(testScheduler),
    )
    return FluidAppLock.forTests(
      section = vault.section("fluid-lock-app", VaultAccess.Device()),
      clock = { testScheduler.currentTime },
      scope = backgroundScope,
      vault = vault,
    ).also { runCurrent() }
  }

  private val keystore = MemoryVaultKeystore()

  // --- le regole --------------------------------------------------------------------------------------------------

  @Test fun `al rientro si blocca dopo il tempo scelto, mai spento, mai durante un'uscita permessa`() {
    assertTrue(FluidAppLockRules.shouldLockOnReturn(enabled = true, awayMillis = 0, timeout = Duration.ZERO, allowedLeave = false))
    assertFalse(FluidAppLockRules.shouldLockOnReturn(enabled = false, awayMillis = 99_999_999, timeout = Duration.ZERO, allowedLeave = false))
    assertFalse(FluidAppLockRules.shouldLockOnReturn(enabled = true, awayMillis = 59_999, timeout = 1.minutes, allowedLeave = false))
    assertTrue(FluidAppLockRules.shouldLockOnReturn(enabled = true, awayMillis = 60_000, timeout = 1.minutes, allowedLeave = false))
    assertFalse(FluidAppLockRules.shouldLockOnReturn(enabled = true, awayMillis = 3_600_000, timeout = Duration.ZERO, allowedLeave = true))
  }

  @Test fun `i ritardi dei PIN sbagliati sono quelli di iPhone`() {
    assertEquals(listOf(0, 0, 0, 0, 1, 5, 15, 15, 60, 60), (1..10).map { FluidAppLockDefaults.pinDelay(it).inWholeMinutes.toInt() })
    assertNull(FluidAppLockRules.pinWaitUntil(failures = 4, lastFailureAt = 1_000, now = 1_000))
    assertEquals(61_000L, FluidAppLockRules.pinWaitUntil(failures = 5, lastFailureAt = 1_000, now = 1_000))
    assertNull(FluidAppLockRules.pinWaitUntil(failures = 5, lastFailureAt = 1_000, now = 61_000))
  }

  @Test fun `le parole dei tempi e delle attese`() {
    val it = FluidSecurityLabels()
    assertEquals(listOf("Subito", "Dopo 1 min", "Dopo 5 min", "Dopo 15 min", "Dopo 1 h"), FluidAppLockDefaults.Timeouts.map(it::timeout))
    assertEquals("Troppi tentativi. Riprova fra 1 minuto.", it.pinWaitText(30.seconds.inWholeMilliseconds))
    assertEquals("Troppi tentativi. Riprova fra 5 minuti.", it.pinWaitText(5.minutes.inWholeMilliseconds))
    assertEquals("Troppi tentativi. Riprova fra 1 ora.", it.pinWaitText(1.hours.inWholeMilliseconds))
    assertEquals("Too many attempts. Try again in 15 minutes.", FluidSecurityLabels.English.pinWaitText(15.minutes.inWholeMilliseconds))
    assertEquals("Fluid Glass è bloccata", it.lockedTitle.format("Fluid Glass"))
  }

  // --- il blocco --------------------------------------------------------------------------------------------------

  @Test fun `spento resta aperta, acceso, si blocca al rientro e all'avvio`() = runTest {
    val lock = lock()
    assertEquals(FluidAppLockState.Unlocked, lock.state.value)
    lock.onAppHidden()
    advanceTimeBy(10.minutes.inWholeMilliseconds)
    lock.onAppShown()
    assertEquals(FluidAppLockState.Unlocked, lock.state.value)

    lock.setEnabled(true)
    assertEquals("acceso adesso, si blocca al prossimo rientro", FluidAppLockState.Unlocked, lock.state.value)
    lock.onAppHidden()
    lock.onAppShown()
    assertEquals("«Subito»: anche un'uscita di un istante", FluidAppLockState.Locked, lock.state.value)
    lock.unlock()
    assertEquals(FluidAppLockState.Unlocked, lock.state.value)

    // Un avvio nuovo (il processo chiuso in sottofondo): parte bloccata.
    val cold = lock()
    assertEquals(FluidAppLockState.Locked, cold.state.value)
    assertTrue(cold.settings.value.enabled)
  }

  @Test fun `con «dopo 5 minuti» un'uscita breve non blocca e una lunga sì`() = runTest {
    val lock = lock()
    lock.setEnabled(true)
    lock.setTimeout(5.minutes)
    lock.onAppHidden()
    advanceTimeBy(4.minutes.inWholeMilliseconds)
    lock.onAppShown()
    assertEquals(FluidAppLockState.Unlocked, lock.state.value)
    lock.onAppHidden()
    advanceTimeBy(5.minutes.inWholeMilliseconds)
    lock.onAppShown()
    assertEquals(FluidAppLockState.Locked, lock.state.value)
    assertEquals(5.minutes, lock().settings.value.timeout)
  }

  @Test fun `una condivisione o una scelta di file non bloccano, ma solo per quel rientro`() = runTest {
    val lock = lock()
    lock.setEnabled(true)
    lock.allowLeaving()
    lock.onAppHidden()
    advanceTimeBy(3.minutes.inWholeMilliseconds)
    lock.onAppShown()
    assertEquals(FluidAppLockState.Unlocked, lock.state.value)
    // Il permesso è consumato: l'uscita dopo blocca.
    lock.onAppHidden()
    lock.onAppShown()
    assertEquals(FluidAppLockState.Locked, lock.state.value)
  }

  @Test fun `un permesso di uscire non usato non vale per un'uscita vera più tardi`() = runTest {
    val lock = lock()
    lock.setEnabled(true)
    lock.allowLeaving()
    lock.onAppResumed() // il pannello si è chiuso senza far uscire l'app
    lock.onAppHidden()
    lock.onAppShown()
    assertEquals(FluidAppLockState.Locked, lock.state.value)
  }

  @Test fun `un permesso scaduto non vale`() = runTest {
    val lock = lock()
    lock.setEnabled(true)
    lock.allowLeaving(within = 1.minutes)
    lock.onAppHidden()
    advanceTimeBy(2.minutes.inWholeMilliseconds)
    lock.onAppShown()
    assertEquals(FluidAppLockState.Locked, lock.state.value)
  }

  @Test fun `bloccarsi richiude anche le sezioni della cassaforte`() = runTest {
    val lock = lock()
    lock.setEnabled(true)
    val notes = vault.section("note")
    notes.values.putString("nota", "x")
    assertTrue(notes.isOpen)
    lock.lock()
    assertFalse(notes.isOpen)
  }

  // --- il PIN dell'app ----------------------------------------------------------------------------------------------

  @Test fun `il PIN dell'app si salva come impronta, apre, e dopo cinque errori fa aspettare`() = runTest {
    val lock = lock()
    lock.setEnabled(true)
    lock.setPin("482913".toCharArray())
    assertTrue(lock.settings.value.hasPin)
    assertEquals(6, lock.settings.value.pinLength)
    // Mai il PIN in chiaro nella cassaforte.
    val stored = vault.section("fluid-lock-app").values.getBytes(FluidAppLock.KeyPin)!!
    assertFalse(String(stored, Charsets.ISO_8859_1).contains("482913"))

    lock.lock()
    repeat(4) { i ->
      val result = lock.checkPin("000000".toCharArray())
      assertEquals(FluidPinCheck.Wrong(failures = i + 1, waitUntil = null), result)
    }
    val fifth = lock.checkPin("000000".toCharArray()) as FluidPinCheck.Wrong
    assertEquals(5, fifth.failures)
    assertEquals(testScheduler.currentTime + 60_000, fifth.waitUntil)
    // Anche il PIN giusto aspetta.
    assertTrue(lock.checkPin("482913".toCharArray()) is FluidPinCheck.Wait)
    assertEquals(FluidAppLockState.Locked, lock.state.value)
    advanceTimeBy(61_000)
    assertEquals(FluidPinCheck.Correct, lock.checkPin("482913".toCharArray()))
    assertEquals(FluidAppLockState.Unlocked, lock.state.value)
    // Il conto riparte.
    lock.lock()
    assertEquals(FluidPinCheck.Wrong(failures = 1, waitUntil = null), lock.checkPin("111111".toCharArray()))
  }

  @Test fun `senza PIN il controllo lo dice, e togliere il PIN lo spegne`() = runTest {
    val lock = lock()
    assertEquals(FluidPinCheck.NoPin, lock.checkPin("1234".toCharArray()))
    lock.setPin("1234".toCharArray())
    assertEquals(4, lock.settings.value.pinLength)
    lock.setPin(null)
    assertFalse(lock.settings.value.hasPin)
    assertEquals(FluidPinCheck.NoPin, lock.checkPin("1234".toCharArray()))
  }
}
