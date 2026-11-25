package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidConditionReason
import dev.antigravity.fluidengine.foundation.FluidConditions
import dev.antigravity.fluidengine.foundation.FluidDeviceConditions
import dev.antigravity.fluidengine.foundation.SyncState
import dev.antigravity.fluidengine.foundation.SyncStatus
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferControls
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferFailure
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferItem
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferOverview
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import dev.antigravity.fluidengine.net.testing.FakeNetworks
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidConnectionCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidConnectionCapsuleTags
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidUpdatePrompt
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateRelease
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateUiState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityKind
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.transfer.FluidTransferActivity
import dev.antigravity.fluidengine.ui.fluid.transfer.FluidTransferTags
import dev.antigravity.fluidengine.ui.fluid.transfer.FluidTransfersPage
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
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

/**
 * F8.20 · La capsula della rete col dito vero (eventi del sistema con l'orologio che avanza) e col vetro vero (verdetti
 * degli shader accettati): le fasi, il pannello che nasce dalla capsula, «Sincronizza ora», le micro-cadute che non
 * lampeggiano (le reti finte con la tenuta vera), TalkBack, «Riduci movimento», niente scritture a riposo. Poi la pagina
 * «Trasferimenti» e la pillola. Formato telefono (400 dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p20NetworkUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private var online by mutableStateOf(true)
  private var connected by mutableStateOf(false)
  private var status by mutableStateOf(SyncStatus())
  private var syncCalls = 0

  @Before fun start() {
    Locale.setDefault(Locale.ITALY)
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
    // Il posto solo delle condizioni e' del processo: una prova non lascia la sua simulazione alle altre.
    FluidDeviceConditions.reset()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun host(reduced: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val modal = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
          LocalFluidGlassModalHostState provides modal,
        ) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = modal, backdrop = null)
          }
        }
      }
    }
    advance(300)
    finger = MaterialFinger(view)
  }

  private fun showCapsule(reduced: Boolean = false) = host(reduced) {
    Box(Modifier.fillMaxSize()) {
      FluidConnectionCapsule(
        online = online,
        status = status,
        connected = connected,
        onSyncNow = { syncCalls++ },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }

  private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = false).fetchSemanticsNode()
  private fun bounds(tag: String): Rect = node(tag).boundsInRoot
  private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
  private fun spoken(tag: String): String = node(tag).config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
  private fun text(tag: String): String {
    val texts = ArrayList<String>()
    fun walk(n: SemanticsNode) {
      n.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
      n.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { texts += it }
      n.children.forEach(::walk)
    }
    walk(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())
    return texts.joinToString(" ")
  }

  private fun tap(tag: String) {
    val at = bounds(tag).center
    finger.down(at)
    advance(80)
    finger.up(at)
    advance(500)
  }

  private fun writesDuring(ms: Long): Int {
    var writes = 0
    val observer = Snapshot.registerGlobalWriteObserver { writes++ }
    advance(ms)
    observer.dispose()
    return writes
  }

  @Test fun `in linea non c'e', offline dice le modifiche in attesa`() {
    showCapsule()
    assertFalse(exists(FluidConnectionCapsuleTags.Capsule))
    status = SyncStatus(pending = 3)
    online = false
    advance(600)
    assertTrue(exists(FluidConnectionCapsuleTags.Capsule))
    assertEquals("Offline · 3 modifiche in attesa", spoken(FluidConnectionCapsuleTags.Capsule))
    // Il bersaglio: almeno 44 dp (88 px a xhdpi).
    assertTrue(bounds(FluidConnectionCapsuleTags.Capsule).height >= 88f)
    connected = true
    advance(600)
    assertEquals("Nessuna connessione a internet · 3 modifiche in attesa", spoken(FluidConnectionCapsuleTags.Capsule))
  }

  @Test fun `al ritorno sincronizza, poi tutto aggiornato, poi sparisce`() {
    showCapsule()
    status = SyncStatus(pending = 2)
    online = false
    advance(600)
    online = true
    status = SyncStatus(state = SyncState.SYNCING, pending = 2)
    advance(400)
    assertEquals("Di nuovo in linea · sincronizzo", spoken(FluidConnectionCapsuleTags.Capsule))
    status = SyncStatus(state = SyncState.IDLE, lastSuccessfulSyncEpochMillis = System.currentTimeMillis(), pending = 0)
    advance(400)
    assertEquals("Tutto aggiornato", spoken(FluidConnectionCapsuleTags.Capsule))
    advance(2_400)
    assertFalse(exists(FluidConnectionCapsuleTags.Capsule))
  }

  @Test fun `senza niente da mandare, di nuovo in linea e poi via`() {
    showCapsule()
    online = false
    advance(600)
    assertEquals("Offline", spoken(FluidConnectionCapsuleTags.Capsule))
    online = true
    advance(400)
    assertEquals("Di nuovo in linea", spoken(FluidConnectionCapsuleTags.Capsule))
    advance(2_600)
    assertFalse(exists(FluidConnectionCapsuleTags.Capsule))
  }

  @Test fun `col dito si apre il pannello, e Sincronizza ora parte quando c'e' la rete`() {
    showCapsule()
    status = SyncStatus(pending = 3, lastSuccessfulSyncEpochMillis = System.currentTimeMillis() - 5 * 60_000L)
    online = false
    advance(600)
    tap(FluidConnectionCapsuleTags.Capsule)
    advance(600)
    assertTrue(exists(FluidConnectionCapsuleTags.Panel))
    val panel = text(FluidConnectionCapsuleTags.Panel)
    assertTrue(panel, "Sei offline" in panel)
    assertTrue(panel, "3 modifiche" in panel)
    assertTrue(panel, "5 min fa" in panel)
    assertTrue(panel, "Sincronizza ora" in panel)
    // Senza rete il tasto e' spento.
    tap(FluidConnectionCapsuleTags.SyncNow)
    assertEquals(0, syncCalls)
    online = true
    status = SyncStatus(state = SyncState.SYNCING, pending = 3)
    advance(400)
    tap(FluidConnectionCapsuleTags.SyncNow)
    advance(400)
    assertEquals(1, syncCalls)
  }

  @Test fun `una micro-caduta e il cambio di rete non fanno lampeggiare la capsula`() {
    val nets = FakeNetworks(now = { compose.mainClock.currentTime })
    var appeared = false
    host {
      val state by remember { nets.states }.collectAsState(initial = nets.raw.value)
      if (!state.online) appeared = true
      Box(Modifier.fillMaxSize()) {
        FluidConnectionCapsule(online = state.online, status = SyncStatus(pending = 1), connected = state.connected, modifier = Modifier.align(Alignment.BottomCenter))
      }
    }
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
    scope.launch { nets.microCut(600) }
    advance(3_500)
    assertFalse("una micro-caduta di 0,6 s non si vede", appeared || exists(FluidConnectionCapsuleTags.Capsule))
    scope.launch { nets.handover(gapMillis = 400, validationMillis = 900) }
    advance(3_500)
    assertFalse("il passaggio ai dati mobili non si vede", appeared || exists(FluidConnectionCapsuleTags.Capsule))
    nets.disconnect()
    advance(3_000)
    assertTrue("una caduta vera si vede dopo la tenuta", exists(FluidConnectionCapsuleTags.Capsule))
    scope.cancel()
  }

  @Test fun `TalkBack - un tasto con la frase, che si annuncia e apre il pannello`() {
    showCapsule()
    status = SyncStatus(pending = 1)
    online = false
    advance(600)
    val capsule = node(FluidConnectionCapsuleTags.Capsule)
    assertEquals(Role.Button, capsule.config.getOrNull(SemanticsProperties.Role))
    assertEquals(LiveRegionMode.Polite, capsule.config.getOrNull(SemanticsProperties.LiveRegion))
    assertEquals("Mostra i dettagli", capsule.config.getOrNull(SemanticsActions.OnClick)?.label)
    assertEquals("Offline · una modifica in attesa", spoken(FluidConnectionCapsuleTags.Capsule))
    compose.onNodeWithTag(FluidConnectionCapsuleTags.Capsule).performSemanticsAction(SemanticsActions.OnClick)
    advance(600)
    assertTrue(exists(FluidConnectionCapsuleTags.Panel))
  }

  @Test fun `riduci movimento - stessa frase, e ferma non scrive niente`() {
    showCapsule(reduced = true)
    status = SyncStatus(pending = 3)
    online = false
    advance(600)
    assertEquals("Offline · 3 modifiche in attesa", spoken(FluidConnectionCapsuleTags.Capsule))
    advance(1_000)
    assertEquals("a riposo nessuna scrittura di stato (niente ridisegni per fotogramma)", 0, writesDuring(1_000))
  }

  @Test fun `ferma non scrive niente anche col movimento`() {
    showCapsule()
    status = SyncStatus(pending = 3)
    online = false
    advance(1_500)
    assertEquals(0, writesDuring(1_000))
  }

  private class Controls : FluidTransferControls {
    val calls = ArrayList<String>()
    override fun pause(id: String) { calls += "pause:$id" }
    override fun resume(id: String) { calls += "resume:$id" }
    override fun retry(id: String) { calls += "retry:$id" }
    override fun force(id: String) { calls += "force:$id" }
    override fun cancel(id: String) { calls += "cancel:$id" }
    override fun clearFinished() { calls += "clear" }
  }

  private val transferItems = listOf(
    FluidTransferItem("apk", "Pampa Notes 2.4", FluidTransferDirection.Download, FluidTransferState.Running, 12_400_000L, 48_000_000L, 2_100_000L, 17_000L),
    FluidTransferItem(
      "mappa", "Mappa della Toscana", FluidTransferDirection.Download, FluidTransferState.Waiting, 0L, 120_000_000L,
      waitingFor = listOf(FluidConditionReason.Metered), canForce = true,
    ),
    FluidTransferItem(
      "video", "Video della gita", FluidTransferDirection.Upload, FluidTransferState.Failed, 3_000_000L, 24_000_000L,
      failure = FluidTransferFailure.server(503),
    ),
    FluidTransferItem("vecchio", "Brano offline", FluidTransferDirection.Download, FluidTransferState.Completed, 9_000_000L, 9_000_000L, finishedAtMillis = 1L),
  )

  @Test fun `la pagina dei trasferimenti - il tasto che serve, il perche' dell'attesa e TalkBack`() {
    val controls = Controls()
    host { FluidTransfersPage(transferItems, controls) }
    advance(600)
    assertTrue(text(FluidTransferTags.row("apk")), "12,4 MB di 48 MB" in text(FluidTransferTags.row("apk")))
    assertTrue(text(FluidTransferTags.row("apk")), "2,1 MB/s" in text(FluidTransferTags.row("apk")))
    assertTrue(text(FluidTransferTags.row("mappa")), "Aspetto il Wi-Fi" in text(FluidTransferTags.row("mappa")))
    assertTrue(text(FluidTransferTags.row("video")), "Il server non risponde (503)" in text(FluidTransferTags.row("video")))
    tap(FluidTransferTags.action("apk"))
    tap(FluidTransferTags.action("mappa"))
    tap(FluidTransferTags.action("video"))
    assertEquals(listOf("pause:apk", "force:mappa", "retry:video"), controls.calls)
    assertFalse("un completato non ha un tasto", exists(FluidTransferTags.action("vecchio")))
    tap(FluidTransferTags.ClearFinished)
    assertEquals("clear", controls.calls.last())
  }

  @Test fun `la pillola dei trasferimenti si pubblica e si chiude con l'esito`() {
    val activities = FluidActivityHostState()
    var overview by mutableStateOf(FluidTransferOverview(transferItems.take(1)))
    val controls = Controls()
    host { FluidTransferActivity(activities, overview, controls) }
    advance(300)
    val shown = activities.current
    assertEquals(FluidActivityKind.Transfer, shown?.kind)
    assertEquals("Pampa Notes 2.4", shown?.title)
    shown!!.actions.first().onClick()
    assertEquals("pause:apk", controls.calls.single())
    overview = FluidTransferOverview(listOf(transferItems[0].copy(state = FluidTransferState.Completed, bytesDone = 48_000_000L, finishedAtMillis = 5L)))
    advance(300)
    assertTrue("finita, la pillola esce con la spunta", activities.activities.isEmpty())
  }

  @Test fun `l'aggiornamento in-app dice perche' aspettare e offre Aggiorna comunque`() {
    var installs = 0
    host {
      FluidUpdatePrompt(
        state = FluidUpdateUiState.Available(FluidUpdateRelease("2.4.0", sizeBytes = 32_000_000L)),
        onInstall = { installs++ },
        onLater = {},
        modifier = Modifier.testTag("aggiornamento"),
      )
    }
    advance(300)
    assertFalse(text("aggiornamento"), "Aggiorna comunque" in text("aggiornamento"))
    FluidDeviceConditions.publish(FluidConditions(online = true, connected = true))
    FluidDeviceConditions.simulate { it.copy(metered = true) }
    advance(300)
    val shown = text("aggiornamento")
    assertTrue(shown, "Rete a consumo: conviene aspettare il Wi-Fi" in shown)
    assertTrue(shown, "Aggiorna comunque" in shown)
    // Senza rete non si forza: l'avviso torna quello di sempre (sara' l'installazione a dire che non va).
    FluidDeviceConditions.simulate { FluidConditions.Offline }
    advance(300)
    assertFalse(text("aggiornamento"), "Aggiorna comunque" in text("aggiornamento"))
  }

  @Test fun `le tre scene si aprono e stanno in piedi`() {
    var wanted by mutableStateOf<String?>("rete-capsula")
    host { androidx.compose.runtime.key(wanted) { NewElementsPageAt(onBack = {}, initial = wanted) } }
    advance(1_200)
    assertTrue(exists(F8p20Tags.Network))
    tap(F8p20Tags.Write)
    advance(1_500)
    wanted = "rete-condizioni"
    advance(1_200)
    assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Pochi dati, poca batteria", substring = true)).fetchSemanticsNodes().isNotEmpty())
    wanted = null
    advance(600)
    assertFalse("uscendo la simulazione si spegne", FluidDeviceConditions.simulated)
  }

  @Test fun `la riga ferma di un trasferimento e' una frase sola, con In pausa e il perche' dell'attesa`() {
    val labels = dev.antigravity.fluidengine.ui.fluid.transfer.FluidTransferLabels()
    fun line(item: FluidTransferItem) = dev.antigravity.fluidengine.ui.fluid.transfer.fluidTransferPhrases(item, labels).line
    val paused = FluidTransferItem(
      id = "a", title = "Pampa Notes 2.4", direction = FluidTransferDirection.Download, state = FluidTransferState.Paused,
      bytesDone = 12_700_000L, bytesTotal = 48_000_000L,
    )
    assertEquals("In pausa · 12,7 MB di 48 MB", line(paused))
    val waiting = paused.copy(state = FluidTransferState.Waiting, waitingFor = listOf(FluidConditionReason.Metered), canForce = true)
    assertEquals("Aspetto il Wi-Fi · Rete a consumo", line(waiting))
    assertEquals("Completato · 48 MB", line(paused.copy(state = FluidTransferState.Completed, bytesDone = 48_000_000L)))
    val failed = paused.copy(state = FluidTransferState.Failed, failure = FluidTransferFailure.server(503, null))
    assertTrue(line(failed), line(failed).startsWith("Non riuscito · "))
  }

  @Test fun `il pannello dice quanto tempo fa, mai aggiornato adesso`() {
    val now = 1_800_000_000_000L
    fun last(status: SyncStatus) = dev.antigravity.fluidengine.ui.fluid.fluidConnectionLastSync(status, nowEpochMillis = now)
    assertEquals("meno di un minuto fa", last(SyncStatus(lastSuccessfulSyncEpochMillis = now - 30_000L, pending = 6)))
    assertEquals("12 min fa", last(SyncStatus(lastSuccessfulSyncEpochMillis = now - 12 * 60_000L, pending = 2)))
    assertEquals("3 ore fa", last(SyncStatus(lastSuccessfulSyncEpochMillis = now - 3 * 3_600_000L)))
    assertEquals("mai", last(SyncStatus(pending = 1)))
    assertEquals("Sincronizzo…", last(SyncStatus(state = SyncState.SYNCING, lastSuccessfulSyncEpochMillis = now)))
  }

  @Test fun `nella scena tre tocchi su Scrivi una modifica fanno tre modifiche in attesa`() {
    // Tab S9, 25/11: tre tocchi davano «Offline · 6 modifiche in attesa». Qui col dito vero, senza rete.
    host { NewElementsPageAt(onBack = {}, initial = "rete-capsula") }
    advance(1_200)
    val offline = compose.onAllNodes(androidx.compose.ui.test.hasText("Senza rete"), useUnmergedTree = true)
      .fetchSemanticsNodes().first().boundsInRoot.center
    finger.down(offline)
    advance(80)
    finger.up(offline)
    advance(600)
    repeat(3) { tap(F8p20Tags.Write) }
    // Le scritture e il tentativo di sincronizzare girano su Dispatchers.Default: si aspetta il tempo vero.
    var said = ""
    val deadline = System.currentTimeMillis() + 10_000
    while (System.currentTimeMillis() < deadline && !said.contains("3 modifiche")) {
      Thread.sleep(100)
      advance(100)
      if (exists(FluidConnectionCapsuleTags.Capsule)) said = spoken(FluidConnectionCapsuleTags.Capsule)
    }
    // Ancora un po': il conto non deve salire dopo il tentativo di sincronizzare a vuoto.
    repeat(20) {
      Thread.sleep(100)
      advance(100)
    }
    said = spoken(FluidConnectionCapsuleTags.Capsule)
    println("F8p20b tre tocchi: «$said»")
    assertEquals("Offline · 3 modifiche in attesa", said)
  }
}
