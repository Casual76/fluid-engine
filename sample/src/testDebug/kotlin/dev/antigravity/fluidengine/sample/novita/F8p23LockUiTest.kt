package dev.antigravity.fluidengine.sample.novita

import android.app.KeyguardManager
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.bilanci.BackgroundWork
import dev.antigravity.fluidengine.security.FluidAppLock
import dev.antigravity.fluidengine.security.FluidAppLockHost
import dev.antigravity.fluidengine.security.FluidAppLockState
import dev.antigravity.fluidengine.security.FluidAppLockTags
import dev.antigravity.fluidengine.security.FluidAuthCapability
import dev.antigravity.fluidengine.security.FluidLockAuthenticator
import dev.antigravity.fluidengine.security.FluidLockedContentTags
import dev.antigravity.fluidengine.security.FluidVaultContent
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.MemoryVaultKeystore
import dev.antigravity.fluidengine.vault.VaultAccess
import dev.antigravity.fluidengine.vault.VaultAuthResult
import dev.antigravity.fluidengine.vault.VaultAuthenticator
import java.io.File
import java.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

/** Il dialogo di sistema finto per il blocco dell'app: conta le volte e risponde quello che si sceglie. */
private class CountingLockAuthenticator(
  var answer: VaultAuthResult = VaultAuthResult.Success,
  /** Se c'è, il dialogo resta aperto finché il test non lo completa (come la persona che non ha ancora toccato). */
  val held: CompletableDeferred<Unit>? = null,
) : FluidLockAuthenticator {
  var calls = 0
  var lastFallback: String? = null
  override suspend fun authenticateUser(title: String, subtitle: String?, fallback: String?): VaultAuthResult {
    calls++
    lastFallback = fallback
    held?.await()
    return answer
  }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi")
class F8p23LockUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private lateinit var vault: FluidVault
  private val locks = mutableListOf<FluidAppLock>()
  private val everything = FluidAuthCapability(strongBiometrics = true, anyBiometrics = true, deviceCredential = true)

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FakeAndroidKeyStore.install()
    shadowOf(compose.activity.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
    vault = FluidVault.create(
      dir = File(compose.activity.noBackupFilesDir, "fluid-vault/ui-${System.nanoTime()}"),
      keystore = MemoryVaultKeystore(),
      scope = scope,
    )
  }

  @After fun stop() {
    locks.forEach { it.close() }
    scope.cancel()
    FakeAndroidKeyStore.uninstall()
    FluidGlassDiagnostics.resetForTesting()
  }

  /**
   * Aspetta [condition] facendo girare il looper principale: le risposte della cassaforte (che lavora su IO) tornano lì,
   * e `waitUntil` di Compose da solo non lo fa girare.
   *
   * F7.32 · L'orologio di Compose avanza solo quando il lavoro in sottofondo ha finito ([BackgroundWork]: la cassaforte
   * su IO, i 210 000 giri del PBKDF2 su Default). Prima avanzava di un fotogramma ogni 5 ms veri anche mentre il PIN si
   * controllava, e il tempo della prova dipendeva da quanto era carico il PC.
   */
  private fun awaitMain(what: String = "", timeoutMs: Long = 10_000, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
      BackgroundWork.await()
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      // L'orologio di Compose va avanti a mano: i `delay` degli effetti (il dialogo che parte dopo 250 ms) ne dipendono.
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

  /** Un blocco acceso (e, se [locked], già bloccato), con le impostazioni lette. */
  private fun lock(locked: Boolean = true, pin: String? = null): FluidAppLock {
    val lock = FluidAppLock(compose.activity, vault = vault, name = "ui")
    locks += lock
    awaitMain("impostazioni lette") { lock.state.value != FluidAppLockState.Loading }
    runBlocking {
      lock.setEnabled(true)
      if (pin != null) lock.setPin(pin.toCharArray())
    }
    if (locked) lock.lock()
    return lock
  }

  private fun show(
    lock: FluidAppLock,
    authenticator: FluidLockAuthenticator?,
    reduced: Boolean = false,
    capability: FluidAuthCapability = everything,
    backdrop: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    width: Dp? = null,
    fontScale: Float = 1f,
  ) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val policy = if (reduced) FluidMotionPolicy(durationScale = 0f) else LocalFluidMotionPolicy.current
        val density = Density(LocalDensity.current.density, fontScale)
        CompositionLocalProvider(LocalFluidMotionPolicy provides policy, LocalDensity provides density) {
          Box(if (width != null) Modifier.width(width).fillMaxHeight() else Modifier.fillMaxSize()) {
            FluidAppLockHost(lock = lock, appName = "Posta", authenticator = authenticator, capability = capability, backdrop = backdrop) {
              Column { Text("Messaggio segreto da Marco") }
            }
          }
        }
      }
    }
    compose.waitForIdle()
  }

  /** Il campo del PIN: le caselle in una riga sola sono alte una casella (52 dp), due righe il doppio più lo spazio. */
  private fun assertPinOnOneRow(what: String) {
    val node = compose.onNodeWithTag(FluidAppLockTags.PinField, useUnmergedTree = true).fetchSemanticsNode()
    val heightDp = node.boundsInRoot.height / compose.activity.resources.displayMetrics.density
    assertTrue("$what: il PIN è alto $heightDp dp, cioè su due righe", heightDp < 80f)
    val panelRight = compose.onNodeWithTag(FluidAppLockTags.Screen, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right
    assertTrue("$what: le caselle escono dallo schermo", node.boundsInRoot.right <= panelRight + 0.5f)
  }

  private fun hasNode(matcher: SemanticsMatcher): Boolean =
    compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  /**
   * Il nodo è nell'albero che legge TalkBack: quello unito, dove `clearAndSetSemantics` toglie i figli (nell'albero non
   * unito Compose li tiene, ma l'accessibilità usa i figli «sostituiti»).
   */
  private fun readByTalkBack(matcher: SemanticsMatcher): Boolean =
    compose.onAllNodes(matcher, useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty()

  @Test fun `bloccata, il vetro copre l'app e TalkBack non legge il contenuto, poi il dialogo parte da solo`() {
    val lock = lock()
    val auth = CountingLockAuthenticator(held = CompletableDeferred())
    show(lock, auth)
    idle(400)
    assertTrue(hasNode(hasText("Posta è bloccata")))
    assertFalse("il contenuto bloccato non è nell'albero di TalkBack", readByTalkBack(hasText("Messaggio segreto da Marco")))
    // Il dialogo è partito da solo, una volta, con la schermata già lì; la persona mette il dito.
    assertEquals(1, auth.calls)
    auth.held!!.complete(Unit)
    awaitMain("aperta") { lock.state.value == FluidAppLockState.Unlocked }
    assertEquals(1, auth.calls)
    idle(600)
    assertTrue(hasNode(hasText("Messaggio segreto da Marco")))
    assertFalse(hasNode(hasTestTag(FluidAppLockTags.Screen)))
  }

  @Test fun `TalkBack, la schermata ha il nome del pannello, il titolo è un'intestazione e il tasto ha un nome`() {
    val lock = lock()
    show(lock, CountingLockAuthenticator(VaultAuthResult.Cancelled))
    idle(400)
    val screen = compose.onNodeWithTag(FluidAppLockTags.Screen, useUnmergedTree = true).fetchSemanticsNode()
    assertEquals("Schermata di blocco", screen.config[SemanticsProperties.PaneTitle])
    assertTrue(hasNode(hasText("Posta è bloccata") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)))
    assertTrue(hasNode(hasText("Sblocca")))
    assertTrue(hasNode(hasText("Usa l'impronta, il volto o il codice del telefono per aprirla.")))
  }

  @Test fun `annullato il dialogo, il tasto Sblocca lo riapre`() {
    val lock = lock()
    val auth = CountingLockAuthenticator(VaultAuthResult.Cancelled)
    show(lock, auth)
    idle(400)
    awaitMain("primo dialogo") { auth.calls == 1 }
    assertEquals(FluidAppLockState.Locked, lock.state.value)
    auth.answer = VaultAuthResult.Success
    compose.onNodeWithTag(FluidAppLockTags.Unlock, useUnmergedTree = true).performClick()
    awaitMain("aperta") { lock.state.value == FluidAppLockState.Unlocked }
    assertEquals(2, auth.calls)
  }

  @Test fun `al rientro si blocca, dopo una condivisione no, dopo una rotazione no`() {
    val lock = lock(locked = false)
    show(lock, CountingLockAuthenticator(VaultAuthResult.Cancelled))
    assertEquals(FluidAppLockState.Unlocked, lock.state.value)
    val scenario = compose.activityRule.scenario
    // Una condivisione: il permesso prima di aprire il foglio, poi fuori e dentro.
    lock.allowLeaving()
    scenario.moveToState(Lifecycle.State.CREATED)
    scenario.moveToState(Lifecycle.State.RESUMED)
    assertEquals(FluidAppLockState.Unlocked, lock.state.value)
    // Una rotazione: l'attività rinasce, l'app non esce.
    scenario.recreate()
    assertEquals(FluidAppLockState.Unlocked, lock.state.value)
    // Fuori davvero (Home) e dentro: bloccata.
    scenario.moveToState(Lifecycle.State.CREATED)
    scenario.moveToState(Lifecycle.State.RESUMED)
    assertEquals(FluidAppLockState.Locked, lock.state.value)
  }

  @Test @Config(sdk = [32])
  fun `col blocco acceso l'anteprima nelle recenti è oscurata, e spento torna normale`() {
    val lock = lock(locked = false)
    show(lock, CountingLockAuthenticator())
    val flags = { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE }
    assertTrue("sotto Android 13 l'equivalente è FLAG_SECURE", flags() != 0)
    runBlocking { lock.setEnabled(false) }
    idle(100)
    assertEquals(0, flags())
  }

  @Test fun `il PIN dell'app, il tasto della riserva, l'errore e l'apertura`() {
    val lock = lock(pin = "4821")
    val auth = CountingLockAuthenticator(VaultAuthResult.Fallback)
    show(lock, auth)
    idle(400)
    awaitMain("primo dialogo") { auth.calls == 1 }
    assertEquals("Usa il PIN dell'app", auth.lastFallback)
    idle(300)
    val field = hasSetTextAction() and hasAnyAncestor(hasTestTag(FluidAppLockTags.PinField))
    compose.onNode(field, useUnmergedTree = true).performTextInput("1111")
    awaitMain("PIN sbagliato") { hasNode(hasText("PIN sbagliato. Riprova.")) }
    assertEquals(FluidAppLockState.Locked, lock.state.value)
    idle(700)
    compose.onNode(field, useUnmergedTree = true).performTextInput("4821")
    awaitMain("aperta") { lock.state.value == FluidAppLockState.Unlocked }
  }

  @Test fun `il PIN giusto chiude la tastiera e toglie il fuoco nell'istante dell'apertura`() {
    val lock = lock(pin = "482193")
    show(lock, CountingLockAuthenticator(), capability = FluidAuthCapability(strongBiometrics = false, anyBiometrics = false, deviceCredential = false))
    idle(500)
    val focusedField = SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)
    assertTrue("il campo del PIN prende il fuoco da solo", hasNode(focusedField))
    val field = hasSetTextAction() and hasAnyAncestor(hasTestTag(FluidAppLockTags.PinField))
    compose.onNode(field, useUnmergedTree = true).performTextInput("482193")
    // Un fotogramma alla volta fino all'apertura: così si guarda nell'istante in cui avviene, a dissolvenza appena partita.
    compose.mainClock.autoAdvance = false
    val end = System.currentTimeMillis() + 10_000
    while (lock.state.value != FluidAppLockState.Unlocked) {
      assertTrue("tempo scaduto: aperta", System.currentTimeMillis() < end)
      // F7.32 · Il PIN si controlla in sottofondo: l'orologio aspetta che abbia finito (vedi [awaitMain]).
      BackgroundWork.await()
      shadowOf(Looper.getMainLooper()).idle()
      compose.mainClock.advanceTimeByFrame()
      Thread.sleep(5)
    }
    // Il looper principale gira (lì si chiudono fuoco e tastiera) e un fotogramma ricompone.
    shadowOf(Looper.getMainLooper()).idle()
    compose.mainClock.advanceTimeByFrame()
    // Nell'istante dell'apertura, mentre la schermata si dissolve ancora: niente fuoco, niente tastiera (sul Tab S9 la
    // tastiera passava alla QWERTY prima di chiudersi, perché il fuoco restava al campo che se ne andava).
    assertTrue("la schermata c'è ancora: si sta dissolvendo", hasNode(hasTestTag(FluidAppLockTags.Screen)))
    assertFalse("niente più fuoco sul campo del PIN", hasNode(focusedField))
    val imm = compose.activity.getSystemService(InputMethodManager::class.java)
    assertFalse("la tastiera è chiusa", shadowOf(imm).isSoftInputVisible)
    compose.mainClock.autoAdvance = true
  }

  @Test fun `le sei caselle del PIN stanno in una riga nel formato telefono`() {
    // Il formato telefono del Tab S9 (412 dp): lì andavano su due righe, 3 + 3.
    show(lock(pin = "482193"), CountingLockAuthenticator(), capability = FluidAuthCapability(false, false, false))
    idle(500)
    assertPinOnOneRow("412 dp")
  }

  @Test fun `nel telefono stretto e col testo al doppio le caselle si stringono ma non vanno a capo`() {
    show(
      lock(pin = "482193"), CountingLockAuthenticator(), capability = FluidAuthCapability(false, false, false),
      width = 320.dp, fontScale = 2f,
    )
    idle(500)
    assertPinOnOneRow("320 dp, testo 2×")
  }

  @Test fun `senza impronta né codice del telefono si apre il PIN subito`() {
    val lock = lock(pin = "4821")
    val auth = CountingLockAuthenticator()
    show(lock, auth, capability = FluidAuthCapability(strongBiometrics = false, anyBiometrics = false, deviceCredential = false))
    idle(500)
    assertEquals("nessun dialogo di sistema", 0, auth.calls)
    assertTrue(hasNode(hasText("Scrivi il PIN dell'app per aprirla.")))
    assertTrue(hasNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(FluidAppLockTags.PinField))))
  }

  @Test fun `con Riduci movimento l'app si apre di colpo, senza la dissolvenza`() {
    val reduced = lock()
    show(reduced, CountingLockAuthenticator(VaultAuthResult.Cancelled), reduced = true)
    idle(400)
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { reduced.unlock() }
    Snapshot.sendApplyNotifications()
    repeat(3) { compose.mainClock.advanceTimeByFrame() }
    assertFalse("Riduci movimento: niente schermata dopo tre fotogrammi (50 ms)", hasNode(hasTestTag(FluidAppLockTags.Screen)))
    compose.mainClock.autoAdvance = true
  }

  @Test fun `col movimento normale la schermata si dissolve in un terzo di secondo`() {
    val lock = lock()
    show(lock, CountingLockAuthenticator(VaultAuthResult.Cancelled))
    idle(400)
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { lock.unlock() }
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()
    compose.mainClock.advanceTimeByFrame()
    assertTrue("a metà dissolvenza la schermata c'è ancora", hasNode(hasTestTag(FluidAppLockTags.Screen)))
    compose.mainClock.advanceTimeBy(500)
    assertFalse(hasNode(hasTestTag(FluidAppLockTags.Screen)))
    compose.mainClock.autoAdvance = true
  }

  @Test fun `ferma, la schermata di blocco non ridisegna`() {
    val lock = lock()
    var draws = 0
    show(lock, CountingLockAuthenticator(VaultAuthResult.Cancelled), backdrop = { Box(Modifier.fillMaxSize().drawBehind { draws++ }) })
    idle(600)
    compose.mainClock.autoAdvance = false
    val settled = draws
    repeat(60) { compose.mainClock.advanceTimeByFrame() }
    assertEquals("sessanta fotogrammi fermi: nessun ridisegno dello sfondo", settled, draws)
    compose.mainClock.autoAdvance = true
  }

  @Test fun `il contenuto protetto si apre col dialogo e si richiude da solo`() {
    val keystore = MemoryVaultKeystore()
    val notesVault = FluidVault.create(
      dir = File(compose.activity.noBackupFilesDir, "fluid-vault/note-${System.nanoTime()}"),
      keystore = keystore,
      scope = scope,
    )
    val section = notesVault.section("note", VaultAccess.Biometric(relockAfter = 1.minutes))
    val auth = VaultAuthenticator { request ->
      request.cipher?.let { keystore.approve(it) } ?: keystore.authenticate()
      VaultAuthResult.Success
    }
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidVaultContent(section = section, title = "Privata", authenticator = auth) { Text("Il codice del portone è 4821") }
      }
    }
    compose.waitForIdle()
    assertTrue(hasNode(hasText("Privata è bloccato")))
    assertFalse(hasNode(hasText("Il codice del portone è 4821")))
    compose.onNodeWithTag(FluidLockedContentTags.View, useUnmergedTree = true).performClick()
    awaitMain { hasNode(hasText("Il codice del portone è 4821")) }
    idle(61_000)
    awaitMain { hasNode(hasText("Privata è bloccato")) }
    assertFalse(section.isOpen)
  }

  @Test fun `le cinque scene si aprono`() {
    var open by mutableStateOf<String?>(null)
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        key(open) { NewElementsPage(onBack = {}, initial = open) }
      }
    }
    compose.waitForIdle()
    val titles = mapOf(
      "blocco-app" to "Posta",
      "note-bloccate" to "Note bloccate",
      "album-nascosto" to "Album",
      "chiave-cambiata" to "La chiave cambiata",
      "chiavi-pampai" to "Chiavi",
    )
    assertEquals(titles.keys, F8p23Section.scenes.map { it.id }.toSet())
    for (scene in F8p23Section.scenes) {
      open = scene.id
      Snapshot.sendApplyNotifications()
      idle(900)
      val title = titles.getValue(scene.id)
      assertTrue("${scene.id}: manca il titolo", hasNode(hasText(title)) || hasNode(hasText("Pampa Mail è bloccata")))
    }
    assertNull(newElementScene("blocco-inesistente"))
  }
}
