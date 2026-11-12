package dev.antigravity.fluidengine.sample

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberCombinedGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
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
 * La sonda dello sfondo non gira quando niente cambia (piano «iOS in Android», F2-f; F2.3, rilievo 1 dell'orchestratore).
 *
 * Su una pagina ferma, dopo la prima misura, **nessuna** lettura, nessuna richiesta al lettore, nessuna cattura, nessun
 * export e nessuno stato scritto in più. Quattro scene: un vetro fuori dalla sorgente che campiona, uno **dentro** la
 * sorgente (la pagina che campiona lo contiene), i tasti di una barra che campionano l'export della barra, e la scheda
 * «Materiale» della galleria, dove sedici test non arrivavano mai a riposo. Ogni scena stampa i contatori, i motivi dei
 * risvegli della sonda ([FluidGlassProbeDiagnostics.wakes]) e le pile di chi scrive stato a pagina ferma: se una scena
 * gira, quello dice quale anello.
 *
 * L'anello che c'era (F2.3): `glassSurface` leggeva in composizione la risposta «annidata» della lastra, per decidere se
 * montare la sonda. Il vetro su vetro della scheda (pannello e lastra con una `ContinuousCornerShape`, che non è uguale
 * a se stessa) ricreava la lastra a ogni composizione; la lastra nuova ripartiva «non annidata», gli effetti la
 * correggevano scrivendo lo stato letto in composizione, e la scrittura chiedeva la composizione dopo. Un giro per
 * fotogramma. Robolectric non disegna, ma sul dispositivo ogni giro era un export e una cattura.
 *
 * In Robolectric il lettore non consegna mai l'immagine: le letture falliscono, e la sonda ne ritenta al più tre a tempo
 * (`GlassProbeMaxRetries`), poi aspetta un evento. Anche quello è un anello chiuso da F2.3: prima ritentava ogni due
 * secondi per sempre.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class GlassProbeQuietTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false

  /** Gli stati scritti nello snapshot globale nei 2 s fermi dell'ultima scena (animazioni, posizionamenti, effetti). */
  private var stateWritesAtRest = 0

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
  }

  /** Fa passare [ms] di tempo finto a fotogrammi da 16 ms, col filo principale (e i suoi messaggi) al passo. */
  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private class Counters(
    val readings: Long = FluidGlassProbeDiagnostics.totalReadings,
    val requests: Long = FluidGlassProbeDiagnostics.totalRequests,
    val failures: Long = FluidGlassProbeDiagnostics.totalFailures,
    val captures: Long = FluidGlassDiagnostics.totalCaptures,
    val exports: Long = FluidGlassDiagnostics.totalExports,
    val wakes: Map<String, Long> = FluidGlassProbeDiagnostics.wakes(),
  ) {
    fun minus(before: Counters): Counters = Counters(
      readings - before.readings, requests - before.requests, failures - before.failures,
      captures - before.captures, exports - before.exports,
      wakes.mapValues { (k, v) -> v - (before.wakes[k] ?: 0L) },
    )

    override fun toString(): String =
      "letture $readings, richieste $requests, fallite $failures, catture $captures, export $exports, risvegli $wakes"
  }

  /** Monta [content], lo lascia assestare 3 s, poi conta cosa succede in 2 s di pagina ferma. */
  private fun quiet(label: String, content: @Composable () -> Unit): Counters {
    compose.mainClock.autoAdvance = false
    compose.setContent(content)
    advance(3_000)
    val settled = Counters()
    // Chi scrive stato a pagina ferma: le pile delle scritture nello snapshot globale (animazioni, posizionamenti),
    // raggruppate per le prime righe utili, e gli oggetti cambiati a ogni applicazione.
    val traces = HashMap<String, Int>()
    val applied = HashMap<String, Int>()
    val nesting = ArrayList<String>()
    val writes = Snapshot.registerGlobalWriteObserver { written ->
      val stack = Throwable().stackTrace
      if (nesting.size < 12 && stack.any { it.methodName == "setNestedState" }) {
        nesting += "${compose.mainClock.currentTime} ms: $written"
      }
      val key = stack
        .drop(1)
        .filter { f ->
          val c = f.className
          !c.startsWith("androidx.compose.runtime.snapshots") && !c.startsWith("androidx.compose.runtime.Snapshot") &&
            !c.startsWith("java.") && !c.startsWith("kotlin.") && !c.startsWith("kotlinx.coroutines")
        }
        .take(14)
        .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
      traces[key] = (traces[key] ?: 0) + 1
    }
    val applies = Snapshot.registerApplyObserver { changed, _ ->
      for (o in changed) {
        val k = o.toString().take(90)
        applied[k] = (applied[k] ?: 0) + 1
      }
    }
    advance(2_000)
    writes.dispose()
    applies.dispose()
    val still = Counters().minus(settled)
    stateWritesAtRest = traces.values.sum()
    traces.entries.sortedByDescending { it.value }.take(8).forEach { (k, v) -> println("GlassProbeQuiet $label scrittura x$v: $k") }
    nesting.forEach { println("GlassProbeQuiet $label annidata: $it") }
    applied.entries.sortedByDescending { it.value }.take(40).forEach { (k, v) -> println("GlassProbeQuiet $label cambiato x$v: $k") }
    println(
      "GlassProbeQuiet $label: dopo 3 s [$settled]; nei 2 s fermi [$still]; caselle rifiutate " +
        "${FluidGlassProbeDiagnostics.totalSlotsRefused}, lettori vivi ${FluidGlassProbeDiagnostics.liveReaders}",
    )
    return still
  }

  private fun assertQuiet(label: String, still: Counters) {
    assertEquals("$label: letture a pagina ferma ($still)", 0L, still.readings)
    assertEquals("$label: richieste al lettore a pagina ferma ($still)", 0L, still.requests)
    assertEquals("$label: catture a pagina ferma ($still)", 0L, still.captures)
    assertEquals("$label: export a pagina ferma ($still)", 0L, still.exports)
    assertEquals("$label: stati scritti a pagina ferma (le pile sono nell'uscita del test)", 0, stateWritesAtRest)
  }

  @Composable
  private fun Icon() {
    Box(Modifier.size(22.dp).background(Color(0xFF121214)))
  }

  @Test
  fun aGlassBesideItsSourceReadsOnceAndThenRests() {
    val still = quiet("fuori dalla sorgente") {
      FluidTheme(settings = remember { EngineSettings() }) {
        val page = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color(0xFF7FA7D9)))
          Column(Modifier.padding(24.dp)) { FluidGlassIconButton(onClick = {}, backdrop = page) { Icon() } }
        }
      }
    }
    assertQuiet("fuori dalla sorgente", still)
  }

  @Composable
  private fun InsideTheSource() {
    FluidTheme(settings = remember { EngineSettings() }) {
      val page = rememberGlassBackdrop()
      Column(Modifier.fillMaxSize().glassBackdropSource(page).background(Color(0xFF7FA7D9)).padding(24.dp)) {
        FluidGlassIconButton(onClick = {}, backdrop = page) { Icon() }
      }
    }
  }

  @Test
  fun aGlassInsideTheSourceItSamplesReadsOnceAndThenRests() {
    val still = quiet("dentro la sorgente") { InsideTheSource() }
    assertQuiet("dentro la sorgente", still)
  }

  @Test
  fun theButtonsOfABarOnItsExportReadOnceAndThenRest() {
    val still = quiet("tasti sull'export di una barra") { ButtonsOnABar() }
    assertQuiet("tasti sull'export di una barra", still)
  }

  @Composable
  private fun ButtonsOnABar() {
    FluidTheme(settings = remember { EngineSettings() }) {
      val page = rememberGlassBackdrop()
      val bar = rememberGlassBackdrop()
      val onBar = rememberCombinedGlassBackdrop(page, bar)
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color(0xFFE8D8C0)))
        Row(
          Modifier
            .padding(24.dp)
            .size(300.dp, 64.dp)
            .glassSurface(
              state = page,
              tint = GlassDefaults.floatingTint(),
              shape = FluidCapsuleShape,
              role = GlassRole.Floating,
              exports = bar,
            ),
        ) {
          FluidGlassIconButton(onClick = {}, backdrop = onBar) { Icon() }
          FluidGlassIconButton(onClick = {}, backdrop = onBar) { Icon() }
        }
      }
    }
  }

  @Composable
  private fun MaterialTab() {
    FluidTheme(settings = remember { EngineSettings() }) {
      Gallery(
        settings = EngineSettings(),
        onSettingsChange = {},
        nav = GalleryNav(listOf(GalleryEntry.Tab(RouteMaterial))),
        tools = GalleryTools(),
      )
    }
  }

  /**
   * Chi scrive stato nello snapshot globale per [ms] di pagina ferma: quante scritture, e le pile più frequenti; più gli
   * stati cambiati da ogni applicazione (anche quelli scritti **in composizione**, che l'osservatore delle scritture
   * globali non vede).
   */
  private class Rest(
    /** Le scritture nello snapshot globale. */
    val writes: Int,
    /** Quante passano da un'animazione (una pagina viva: uno scheletro, un pallino, un grafico che respira). */
    val animated: Int,
    /** Quante scrivono la risposta «annidata» di una lastra: la firma dell'anello di F2.3. */
    val nesting: Int,
    val top: String?,
  )

  private fun watchWrites(ms: Long): Rest {
    val traces = HashMap<String, Int>()
    val applied = HashMap<String, Int>()
    val applies = Snapshot.registerApplyObserver { changed, _ ->
      for (o in changed) {
        val k = o.toString().take(110)
        applied[k] = (applied[k] ?: 0) + 1
      }
    }
    val writes = Snapshot.registerGlobalWriteObserver { _ ->
      val key = Throwable().stackTrace
        .drop(1)
        .filter { f ->
          val c = f.className
          !c.startsWith("androidx.compose.runtime.snapshots") && !c.startsWith("androidx.compose.runtime.Snapshot") &&
            !c.startsWith("java.") && !c.startsWith("kotlin.") && !c.startsWith("kotlinx.coroutines")
        }
        .take(10)
        .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
      traces[key] = (traces[key] ?: 0) + 1
    }
    try {
      advance(ms)
    } finally {
      writes.dispose()
      applies.dispose()
    }
    if (traces.isEmpty() && applied.isEmpty()) return Rest(0, 0, 0, null)
    val separator = "\n    "
    val top = traces.entries.sortedByDescending { it.value }.take(4)
      .joinToString(separator) { "scrittura x${it.value} ${it.key}" } + separator +
      applied.entries.sortedByDescending { it.value }.take(8)
        .joinToString(separator) { "cambiato x${it.value} ${it.key}" }
    val animation = Regex("InfiniteTransition|Animatable|SuspendAnimation|AnimationScope")
    return Rest(
      writes = traces.values.sum(),
      animated = traces.filterKeys { animation.containsMatchIn(it) }.values.sum(),
      nesting = traces.filterKeys { it.contains("setNestedState") }.values.sum(),
      top = top,
    )
  }

  /**
   * Il giro di tutta la galleria: ogni scheda e ogni pagina (aperta da «Chrome», come le apre la beta), lasciata
   * assestare 2 s e poi guardata 1 s ferma. Una pagina senza animazioni non scrive **nessuno** stato; una pagina viva
   * (un'animazione infinita: scheletri, pallino, grafici) scrive i suoi, e il test la lascia stare, come l'attesa di
   * Compose. In nessuna pagina una lastra riscrive la sua risposta «annidata» (la firma dell'anello di F2.3). Le
   * richieste al lettore non si contano qui: in Robolectric la lettura non torna mai, e la coda dei tre tentativi di
   * una pagina appena aperta dura più dell'assestamento; il riposo della sonda lo provano le scene qui sopra.
   */
  @Test
  fun everyPlaceOfTheGalleryRests() {
    val nav = GalleryNav(listOf(GalleryEntry.Tab(RouteChrome)))
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Gallery(settings = EngineSettings(), onSettingsChange = {}, nav = nav, tools = GalleryTools())
      }
    }
    advance(1_000)
    val places = listOf(RouteMaterial, RoutePlayground, RouteControls, RouteChrome, RouteSettings)
      .map { "scheda $it" to listOf<GalleryEntry>(GalleryEntry.Tab(it)) } +
      GalleryPage.entries.map {
        "pagina ${it.name}" to listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(it))
      }
    val noisy = ArrayList<String>()
    for ((name, entries) in places) {
      nav.reset(entries)
      FluidGlassProbeDiagnostics.resetPeakSlots()
      advance(2_000)
      val requests = FluidGlassProbeDiagnostics.totalRequests
      println("GlassProbeQuiet riposo inizio $name")
      val rest = watchWrites(1_000)
      println("GlassProbeQuiet riposo fine $name")
      val asked = FluidGlassProbeDiagnostics.totalRequests - requests
      val living = rest.animated > 0
      println(
        "GlassProbeQuiet giro $name: caselle ${FluidGlassProbeDiagnostics.peakSlots}, stati scritti ${rest.writes} " +
          "(animazioni ${rest.animated}, annidata ${rest.nesting}), richieste $asked${if (living) ", pagina viva" else ""}${rest.top?.let { "\n    $it" } ?: ""}",
      )
      if ((!living && rest.writes > 0) || rest.nesting > 0) {
        noisy += "$name (stati ${rest.writes}, animazioni ${rest.animated}, annidata ${rest.nesting})"
      }
    }
    assertTrue("posti della galleria che non stanno fermi: $noisy (le pile nell'uscita del test)", noisy.isEmpty())
    assertEquals("nessuna sonda senza casella in tutta la galleria", 0L, FluidGlassProbeDiagnostics.totalSlotsRefused)
  }

  @Test
  fun theMaterialTabOfTheGalleryRests() {
    val still = quiet("scheda Materiale della galleria") { MaterialTab() }
    assertTrue("nessuna casella rifiutata (${FluidGlassProbeDiagnostics.totalSlotsRefused})",
      FluidGlassProbeDiagnostics.totalSlotsRefused == 0L)
    assertQuiet("scheda Materiale", still)
  }
}
