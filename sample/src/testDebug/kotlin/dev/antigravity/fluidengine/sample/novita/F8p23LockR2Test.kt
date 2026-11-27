package dev.antigravity.fluidengine.sample.novita

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.Lifecycle
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.security.FluidAppLock
import dev.antigravity.fluidengine.security.FluidAppLockHost
import dev.antigravity.fluidengine.security.FluidAppLockSettingsGroup
import dev.antigravity.fluidengine.security.FluidAppLockSettingsTags
import dev.antigravity.fluidengine.security.FluidAppLockState
import dev.antigravity.fluidengine.security.FluidAppLockTags
import dev.antigravity.fluidengine.security.FluidAuthCapability
import dev.antigravity.fluidengine.security.FluidLockAuthenticator
import dev.antigravity.fluidengine.security.FluidSecurityLabels
import dev.antigravity.fluidengine.security.FluidSystemAuthenticator
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.MemoryVaultKeystore
import dev.antigravity.fluidengine.vault.VaultAccess
import dev.antigravity.fluidengine.vault.VaultAuthMethod
import dev.antigravity.fluidengine.vault.VaultAuthRequest
import dev.antigravity.fluidengine.vault.VaultAuthResult
import dev.antigravity.fluidengine.vault.VaultAuthenticator
import dev.antigravity.fluidengine.vault.VaultUnlock
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager
import org.robolectric.shadows.ShadowBiometricPrompt

/** Il dialogo finto del blocco: risponde sempre [answer]. */
private class R2LockAuthenticator(var answer: VaultAuthResult = VaultAuthResult.Cancelled) : FluidLockAuthenticator {
  var calls = 0
  override suspend fun authenticateUser(title: String, subtitle: String?, fallback: String?): VaultAuthResult {
    calls++
    return answer
  }
}

/**
 * R2 (muse-f8-prime, maggiore 4 e minori della sicurezza): la tastiera sotto il blocco, le capacità rilette al rientro,
 * il sensore bloccato, la riga delle impostazioni che dice gli errori, rilegge il blocco schermo e chiede chi sei prima
 * di spegnere, e il dialogo della cassaforte sul thread principale.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi")
class F8p23LockR2Test {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val keystore = MemoryVaultKeystore()
  private lateinit var vault: FluidVault
  private val locks = mutableListOf<FluidAppLock>()
  private val everything = FluidAuthCapability(strongBiometrics = true, anyBiometrics = true, deviceCredential = true)
  private val labels = FluidSecurityLabels()

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FakeAndroidKeyStore.install()
    secure(true)
    biometrics(false)
    vault = FluidVault.create(
      dir = File(compose.activity.noBackupFilesDir, "fluid-vault/r2-${System.nanoTime()}"),
      keystore = keystore,
      scope = scope,
    )
  }

  @After fun stop() {
    locks.forEach { it.close() }
    scope.cancel()
    FakeAndroidKeyStore.uninstall()
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun secure(on: Boolean) {
    shadowOf(compose.activity.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(on)
  }

  private fun biometrics(on: Boolean) {
    val manager = Shadow.extract<ShadowBiometricManager>(compose.activity.getSystemService(BiometricManager::class.java))
    manager.setCanAuthenticate(on)
    if (on) manager.setAuthenticatorType(BiometricManager.Authenticators.BIOMETRIC_STRONG)
  }

  private fun awaitMain(what: String = "", timeoutMs: Long = 10_000, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      if (compose.mainClock.autoAdvance) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
      }
      Thread.sleep(5)
    }
    throw AssertionError("tempo scaduto: $what")
  }

  private fun idle(ms: Long = 0) {
    if (ms > 0) compose.mainClock.advanceTimeBy(ms)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    compose.waitForIdle()
  }

  /** Un blocco con le impostazioni lette; [enabled] lo accende, [locked] lo blocca subito. */
  private fun lock(enabled: Boolean = true, locked: Boolean = enabled, pin: String? = null): FluidAppLock {
    val lock = FluidAppLock(compose.activity, vault = vault, name = "r2")
    locks += lock
    awaitMain("impostazioni lette") { lock.state.value != FluidAppLockState.Loading }
    runBlocking {
      if (enabled) lock.setEnabled(true)
      if (pin != null) lock.setPin(pin.toCharArray())
    }
    if (locked) lock.lock()
    return lock
  }

  private fun hasNode(matcher: SemanticsMatcher): Boolean =
    compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  /** La frase che TalkBack legge sull'interruttore («Richiedi l'impronta, …»). */
  private fun toggleSays(text: String): Boolean =
    compose.onAllNodes(hasTestTag(FluidAppLockSettingsTags.Toggle), useUnmergedTree = true).fetchSemanticsNodes().any { node ->
      node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.any { it.contains(text) }
    }

  private fun showSettings(lock: FluidAppLock) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Column(Modifier.fillMaxSize()) { FluidAppLockSettingsGroup(lock = lock, appName = "Posta", pinLength = 4) }
      }
    }
    compose.waitForIdle()
  }

  private fun backFromSettings() {
    val scenario = compose.activityRule.scenario
    scenario.moveToState(Lifecycle.State.CREATED)
    scenario.moveToState(Lifecycle.State.RESUMED)
    idle(400)
  }

  private fun typeCode(code: String) {
    compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextInput(code)
    idle(700)
  }

  /** Il dialogo di sistema di adesso, se ce n'è uno nuovo dopo [before]. */
  private fun currentPrompt(): BiometricPrompt? = runCatching { ShadowBiometricPrompt.getCurrentPrompt() }.getOrNull()

  // --- maggiore 4: la tastiera sotto il blocco -----------------------------------------------------------------------

  @Test fun `bloccata, con la tastiera il fuoco non va sui tasti del contenuto e Invio non li preme`() {
    val lock = lock()
    var pressed = 0
    var focusedInContent = 0
    lateinit var inputMode: InputModeManager
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        inputMode = LocalInputModeManager.current
        FluidAppLockHost(lock = lock, appName = "Posta", authenticator = R2LockAuthenticator(), capability = everything) {
          Column {
            FluidButton(
              text = "Cancella tutta la posta",
              onClick = { pressed++ },
              modifier = Modifier.onFocusChanged { if (it.isFocused) focusedInContent++ },
            )
            // Un gruppo del fuoco in mezzo, come una lista o una barra dell'app.
            Column(Modifier.focusGroup()) {
              FluidButton(
                text = "Svuota il cestino",
                onClick = { pressed++ },
                modifier = Modifier.onFocusChanged { if (it.isFocused) focusedInContent++ },
              )
            }
          }
        }
      }
    }
    idle(400)
    compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
    val onUnlock = SemanticsMatcher.expectValue(SemanticsProperties.Focused, true) and
      (hasTestTag(FluidAppLockTags.Unlock) or hasAnyAncestor(hasTestTag(FluidAppLockTags.Unlock)))
    var reachedUnlock = false
    val keys = listOf(Key.Tab, Key.Tab, Key.DirectionDown, Key.Tab, Key.DirectionUp, Key.Tab, Key.DirectionDown, Key.Tab)
    keys.forEachIndexed { i, key ->
      compose.onRoot().performKeyInput { pressKey(key) }
      idle(50)
      assertEquals("tasto $i ($key): il fuoco è arrivato su un tasto invisibile", 0, focusedInContent)
      if (hasNode(onUnlock)) reachedUnlock = true
      compose.onRoot().performKeyInput { pressKey(Key.Enter) }
      idle(50)
      assertEquals("tasto $i ($key), poi Invio: premuto un tasto sotto il blocco", 0, pressed)
    }
    assertTrue("con la tastiera si arriva sempre a «Sblocca»", reachedUnlock)
    assertEquals(FluidAppLockState.Locked, lock.state.value)
  }

  // --- minore: le capacità rilette al rientro ------------------------------------------------------------------------

  @Test fun `bloccata, il blocco schermo messo nelle impostazioni del telefono si vede al rientro`() {
    secure(false)
    val lock = lock()
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidAppLockHost(lock = lock, appName = "Posta", authenticator = R2LockAuthenticator()) { Text("Messaggio segreto") }
      }
    }
    idle(400)
    assertFalse(hasNode(hasTestTag(FluidAppLockTags.Unlock)))
    assertTrue(hasNode(hasText(labels.nothingToUnlockWith)))
    // La persona va nelle impostazioni del telefono, mette un PIN dello schermo e torna.
    secure(true)
    backFromSettings()
    assertTrue("al rientro c'è il tasto «Sblocca»", hasNode(hasTestTag(FluidAppLockTags.Unlock)))
  }

  // --- minore: il sensore bloccato -----------------------------------------------------------------------------------

  @Test fun `il sensore bloccato da troppi tentativi passa alla riserva della cassaforte, se c'è`() {
    biometrics(true)
    val authenticator = FluidSystemAuthenticator(compose.activity)
    val cases = listOf(
      Triple(BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT, "Usa il codice del telefono", VaultAuthResult.Fallback),
      Triple(BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT, "Usa il codice del telefono", VaultAuthResult.Fallback),
      Triple(BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT, null, VaultAuthResult.Failed("Troppi tentativi.")),
    )
    for ((code, fallback, expected) in cases) {
      val before = currentPrompt()
      val result = scope.async {
        authenticator.authenticate(VaultAuthRequest(VaultAuthMethod.Biometric, cipher = null, title = "Sblocca le note", fallback = fallback))
      }
      awaitMain("dialogo per $code") { currentPrompt().let { it != null && it !== before } }
      ShadowBiometricPrompt.authenticateCurrentSessionWithError(code, "Troppi tentativi.")
      awaitMain("esito per $code") { result.isCompleted }
      assertEquals("errore $code, riserva «$fallback»", expected, runBlocking { result.await() })
    }
  }

  // --- minori: la riga delle impostazioni ----------------------------------------------------------------------------

  @Test fun `la cassaforte che rifiuta il PIN lo dice e tiene aperta la scelta`() {
    val lock = lock(enabled = false)
    showSettings(lock)
    compose.onNodeWithTag(FluidAppLockSettingsTags.Pin, useUnmergedTree = true).performClick()
    idle(300)
    // La cassaforte smette di rispondere (qui: la chiave sparita).
    vault.lockAll()
    keystore.wipe()
    typeCode("4821")
    typeCode("4821")
    awaitMain("la scelta resta aperta") { hasNode(hasTestTag(FluidAppLockSettingsTags.PinSetup)) && hasNode(hasText("Non è stato possibile salvare. Riprova.")) }
    assertFalse(lock.settings.value.hasPin)
  }

  @Test fun `la cassaforte che rifiuta l'interruttore lo dice`() {
    val lock = lock(enabled = false)
    showSettings(lock)
    vault.lockAll()
    keystore.wipe()
    compose.onNodeWithTag(FluidAppLockSettingsTags.Toggle, useUnmergedTree = true).performClick()
    awaitMain("il messaggio") { hasNode(hasText("Non è stato possibile salvare. Riprova.")) }
    assertFalse(lock.settings.value.enabled)
  }

  @Test fun `il blocco schermo messo nelle impostazioni del telefono accende l'interruttore al rientro`() {
    secure(false)
    val lock = lock(enabled = false)
    showSettings(lock)
    assertTrue(toggleSays(labels.settingUnavailable))
    secure(true)
    backFromSettings()
    assertTrue("al rientro l'interruttore si può accendere", toggleSays(labels.settingSubtitle.format("Posta")))
  }

  @Test fun `spegnere il blocco chiede prima il dialogo di sistema`() {
    val lock = lock(locked = false)
    showSettings(lock)
    // Annullato il dialogo, il blocco resta acceso.
    var before = currentPrompt()
    compose.onNodeWithTag(FluidAppLockSettingsTags.Toggle, useUnmergedTree = true).performClick()
    // Prima del rimedio la cassaforte spegneva il blocco senza chiedere niente: si aspetta l'uno o l'altro.
    awaitMain("dialogo o blocco spento") { currentPrompt().let { it != null && it !== before } || !lock.settings.value.enabled }
    assertTrue("acceso finché non si dice chi si è", lock.settings.value.enabled)
    ShadowBiometricPrompt.cancelCurrentSession()
    idle(200)
    assertTrue(lock.settings.value.enabled)
    // Riuscito, si spegne.
    before = currentPrompt()
    compose.onNodeWithTag(FluidAppLockSettingsTags.Toggle, useUnmergedTree = true).performClick()
    awaitMain("secondo dialogo") { currentPrompt().let { it != null && it !== before } }
    ShadowBiometricPrompt.authenticateCurrentSessionSuccessfully()
    awaitMain("spento") { !lock.settings.value.enabled }
  }

  @Test fun `cambiare o togliere il PIN chiede prima il PIN di adesso, senza impronta né codice`() {
    secure(false)
    val lock = lock(locked = false, pin = "4821")
    showSettings(lock)
    compose.onNodeWithTag(FluidAppLockSettingsTags.Pin, useUnmergedTree = true).performClick()
    idle(300)
    assertTrue("prima il PIN di adesso", hasNode(hasText("Scrivi il PIN attuale")))
    assertFalse(hasNode(hasText(labels.pinRemove)))
    typeCode("1111")
    awaitMain("sbagliato") { hasNode(hasText(labels.pinWrong)) }
    assertFalse(hasNode(hasText(labels.pinRemove)))
    // Le caselle tremano e si svuotano (600 ms), poi si riscrive: come nella prova della schermata di blocco.
    idle(700)
    typeCode("4821")
    awaitMain("la scelta del PIN") { hasNode(hasText(labels.pinRemove)) }
    assertTrue(lock.settings.value.hasPin)
  }

  // --- minore: il dialogo della cassaforte sul thread principale -----------------------------------------------------

  @Test fun `la cassaforte chiede il dialogo sul thread principale anche da un lavoro in sottofondo`() {
    val memory = MemoryVaultKeystore()
    val notes = FluidVault.create(dir = File(compose.activity.noBackupFilesDir, "fluid-vault/r2-note-${System.nanoTime()}"), keystore = memory, scope = scope)
    val section = notes.section("note", VaultAccess.Biometric())
    var onMain: Boolean? = null
    val auth = VaultAuthenticator { request ->
      onMain = Looper.myLooper() == Looper.getMainLooper()
      request.cipher?.let { memory.approve(it) } ?: memory.authenticate()
      VaultAuthResult.Success
    }
    val created = scope.async(Dispatchers.IO) { section.unlock(auth) }
    awaitMain("nata") { created.isCompleted }
    section.lock()
    val opened = scope.async(Dispatchers.IO) { section.unlock(auth) }
    awaitMain("aperta") { opened.isCompleted }
    assertEquals(VaultUnlock.Opened(), runBlocking { opened.await() })
    assertEquals("il dialogo è stato chiesto sul thread principale", true, onMain)
  }
}
