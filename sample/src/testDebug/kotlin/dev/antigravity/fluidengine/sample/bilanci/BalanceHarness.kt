@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.bilanci

import android.graphics.RenderNode
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.fluid.FluidTimeSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidFirstUseMemory
import dev.antigravity.fluidengine.ui.fluid.LocalFluidTimeSource
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassCaptureClock
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.SharedRuntimeShaders
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.GlassCostLevers
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import dev.antigravity.fluidengine.ui.theme.LocalRouteMotionSignals
import dev.antigravity.fluidengine.ui.theme.rememberRouteMotionSignals
import java.time.Duration
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers

/**
 * F7.28 · L'imbracatura delle prove dei bilanci: l'orologio a passi di 16 ms, il disegno di ogni fotogramma su un canvas
 * hardware (come `GlassNodeCountTest`), il log del vetro, le fabbriche e le finiture dei viaggi.
 *
 * [install] accende la diagnostica del vetro e il log `FluidGlassFrame` (salvando e ripristinando i valori di prima),
 * i contatori delle fabbriche, e sostituisce i soli programmi a otto pezzi che lo Skia di Robolectric rifiuta («program
 * is too large»), come `GlassNodeCountTest`: la ricetta, gli uniform e le misure restano quelle di produzione. Poi
 * (F7.29) compila tutti i programmi condivisi dell'engine, perché la misura non dipenda dalle prove di prima.
 */
internal class BalanceHarness(private val compose: AndroidComposeTestRule<*, ComponentActivity>) {

  private var display: RenderNode? = null
  private var logIndex = 0
  private val finishes = ArrayList<IntSize>()

  /** Accende tutto; il valore restituito rimette com'era. */
  fun install(frameLog: Boolean = true): AutoCloseable {
    val lazy = ReflectionHelpers.getStaticField<Lazy<Boolean>>(GlassFrameDiagnostics::class.java, "enabled\$delegate")
    val previousLog = ReflectionHelpers.getField<Any?>(lazy, "_value")
    ReflectionHelpers.setField(lazy, "_value", frameLog)
    val diagnosticsWere = FluidGlassDiagnostics.enabled
    val warmUpWas = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.warmUpEnabled = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    val previousObserver = GlassCostLevers.onCapture
    GlassCostLevers.onCapture = { _, finish -> finishes.add(IntSize(finish.width.toInt(), finish.height.toInt())) }
    val substitutes = substituteLargePrograms()
    compileSharedPrograms()
    // La sonda dello sfondo legge come sul dispositivo (una lettura, poi riposa): il lettore vero di Robolectric non
    // consegna mai l'immagine, e la sonda ritenterebbe a vuoto, un lavoro che sul tablet non c'è.
    val previousReader = FluidGlassProbeDiagnostics.readerForTesting
    FluidGlassProbeDiagnostics.readerForTesting = { ProbeColor }
    // F7.32c · Le decisioni di cattura del vetro (la cattura tenuta, 110 ms; l'intervallo fra due ricampionamenti) sull'orologio
    // della prova, come i fotogrammi: con l'orologio di sistema le catture dipendevano da quanto il PC ci metteva a disegnare
    // un fotogramma (`sync-due-dispositivi/entrata`, 11 catture su 9 a volte). Un'origine lontana da zero: per il vetro
    // zero vuol dire «mai registrata».
    val previousCaptureClock = GlassCaptureClock.clockForTesting
    if (System.getProperty(SystemCaptureClockProperty) != "1") {
      GlassCaptureClock.clockForTesting = { CaptureClockOrigin + compose.mainClock.currentTime }
    }
    GlassFactoryCounters.reset()
    val node = RenderNode("bilanci • finestra")
    display = node
    GlassFactoryCounters.harnessNode = node
    GlassFactoryCounters.harnessView = compose.activity.window.decorView
    GlassFactoryCounters.active = true
    ShadowLog.clear()
    logIndex = 0
    return AutoCloseable {
      GlassFactoryCounters.active = false
      GlassFactoryCounters.harnessNode = null
      GlassFactoryCounters.harnessView = null
      GlassFactoryCounters.reset()
      node.discardDisplayList()
      display = null
      GlassCostLevers.onCapture = previousObserver
      FluidGlassProbeDiagnostics.readerForTesting = previousReader
      GlassCaptureClock.clockForTesting = previousCaptureClock
      val cache = ReflectionHelpers.getStaticField<MutableMap<String, RuntimeShader>>(SharedRuntimeShaders::class.java, "shaders")
      substitutes.forEach(cache::remove)
      ReflectionHelpers.setField(lazy, "_value", previousLog)
      FluidGlassDiagnostics.enabled = diagnosticsWere
      FluidGlassDiagnostics.resetForTesting()
      FluidGlassDiagnostics.warmUpEnabled = warmUpWas
      ShadowLog.clear()
    }
  }

  private val scene = mutableStateOf<(@Composable () -> Unit)?>(null)
  private val generation = mutableIntStateOf(0)
  private var contentSet = false

  /**
   * F7.32 · L'ora dei testi vivi ([LocalFluidTimeSource]) sull'orologio della prova: un secondo pieno di partenza, più il
   * tempo dell'orologio di Compose, quello che fa scadere i `delay` degli effetti. Con l'ora del sistema (di serie) il
   * conto alla rovescia di F8.1 aspettava il secondo dopo sull'orologio della prova e poi leggeva l'ora vera: i secondi
   * che scendevano nella posa dipendevano da quanto era carico il PC (`conto/posa`, 4 corse su 6 in F7.29). Così ogni
   * corsa vede gli stessi secondi, come sul telefono, dove i due orologi sono uno.
   */
  private val timeAnchor = System.currentTimeMillis() / 1000L * 1000L
  val time = FluidTimeSource { timeAnchor + compose.mainClock.currentTime }

  /**
   * Mostra [content] al posto della scena di prima (la regola di prova permette un solo `setContent` per prova): una
   * chiave per scena, così lo stato di una non passa alla dopo.
   *
   * F7.32 · Ogni scena ha i suoi segnali del movimento fra pagine ([LocalRouteMotionSignals]). Quelli di serie sono uno
   * solo per tutto il processo, e il loro timbro (`glassRelaxAt`, «il vetro resta al minimo fino a…») è un istante di
   * `SystemClock.uptimeMillis()`. Sul telefono l'orologio non torna indietro e il timbro scade dopo 40 ms; Robolectric
   * invece lo rimette a 100 ms a ogni prova, e un timbro lasciato da `FluidPageHost` in una scena di «Misure» (per
   * esempio a 4,8 s) teneva il vetro della prova dopo al minimo (qualità 0,25) per i suoi primi 4,8 s: la sonda, sospesa
   * sotto 0,9, leggeva per la prima volta nella posa, e luce e tinta si ridisegnavano lì (le pose di dieci pacchetti di
   * F8 quando `GlassBalanceScenesTest` girava prima di `F8BalanceTest`, F7.29).
   */
  fun show(content: @Composable () -> Unit) {
    if (awaitsBackground) System.getProperty(BackgroundDelayProperty)?.toLongOrNull()?.let(BackgroundWork::occupy)
    if (!contentSet) {
      contentSet = true
      scene.value = content
      compose.setContent {
        CompositionLocalProvider(LocalFluidTimeSource provides time) {
          key(generation.intValue) {
            // F7.32d · Ogni scena la sua memoria della prima volta: quella di serie (LocalFluidFirstUseMemory) dura quanto
            // il processo, e il benvenuto di F8.11 si segna come visto quando compare. Una prova di prima che l'aveva
            // aperto (BalanceOrderTest con F8.11) toglieva il foglio alla scena dopo: 3 nodi o 4, secondo l'ordine delle classi.
            CompositionLocalProvider(LocalFluidFirstUseMemory provides remember { FluidFirstUseMemory.inMemory() }) {
              if (System.getProperty(SharedRouteSignalsProperty) == "1") {
                scene.value?.invoke()
              } else {
                CompositionLocalProvider(LocalRouteMotionSignals provides rememberRouteMotionSignals()) { scene.value?.invoke() }
              }
            }
          }
        }
      }
    } else {
      compose.runOnUiThread {
        scene.value = content
        generation.intValue++
      }
    }
  }

  /** Toglie la scena (l'uscita dell'elemento). */
  fun clear() = show {}

  fun view(): android.view.View = compose.activity.window.decorView

  fun runOnUi(block: () -> Unit) = compose.runOnUiThread(block)

  /** I byte del vetro usati adesso (residenti e in volo). */
  fun usage(): Long = FluidGlassResources.get(compose.activity).budget.usage(FluidMemoryCategory.GLASS).usedBytes

  /** La radice di Compose, o null se non c'è ancora. */
  fun root(): LayoutNode? = GlassBalanceMeter.composeRoot(compose.activity.window.decorView)

  fun meter(label: String, margin: Int = 0) = GlassBalanceMeter(label, ::usage, ::root, margin)

  /** Svuota il log del vetro (fra una scena e l'altra: il log di Robolectric non si svuota da solo). */
  fun clearLog() {
    ShadowLog.clear()
    logIndex = 0
  }

  /**
   * F7.32 · Prima di far avanzare l'orologio della prova, il lavoro in sottofondo delle scene finisce ([BackgroundWork]):
   * per la prova dura meno di un fotogramma, come sul telefono, e arriva al fotogramma dopo a ogni corsa.
   */
  private fun awaitBackground() {
    if (awaitsBackground && System.getProperty(NoBackgroundBarrierProperty) != "1") BackgroundWork.await()
  }

  /**
   * F7.32 · Se la scena di adesso ha il lavoro in sottofondo sull'orologio della prova: le scene che il loro frammento
   * dichiara con `@sottofondo`. Le altre vanno come prima di F7.32 (la barriera su tutte cambierebbe le righe di sei
   * pacchetti: vedi il rapporto di F7.32).
   */
  var awaitsBackground: Boolean = false

  /** Un fotogramma: l'orologio avanza di 16 ms, gli stati scritti si consegnano, la finestra si disegna. */
  fun frame(meter: GlassBalanceMeter? = null) {
    awaitBackground()
    compose.mainClock.advanceTimeByFrame()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FrameMillis))
    Snapshot.sendApplyNotifications()
    draw(meter)
  }

  /** Il disegno della finestra su un canvas hardware, e gli eventi del fotogramma al misuratore. */
  fun draw(meter: GlassBalanceMeter? = null) {
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      val node = checkNotNull(display) { "BalanceHarness.install() prima di disegnare" }
      node.setPosition(0, 0, view.width, view.height)
      val canvas = node.beginRecording()
      try {
        view.draw(canvas)
      } finally {
        node.endRecording()
      }
      val logs = ShadowLog.getLogsForTag(FrameTag)
      val events = if (logIndex <= logs.size) logs.subList(logIndex, logs.size).map { it.msg } else logs.map { it.msg }
      logIndex = logs.size
      meter?.frame(events, ArrayList(finishes))
      finishes.clear()
    }
  }

  /**
   * F7.29 · Aspetta che la scena sia pronta da misurare ([ready] sull'albero della semantica): per le scene che arrivano
   * dopo un lavoro su fili veri, come la memoria di F9.3 (`Dispatchers.IO` e il filo dell'indice di ricerca), che
   * altrimenti compariva a caso nella posa o nel moto secondo il carico del PC. L'orologio della prova avanza di un
   * fotogramma solo quando Compose ha lavoro in sospeso (una ricomposizione, un fotogramma chiesto); se non ne ha, si
   * lasciano lavorare i fili veri e si consegna sul principale quello che hanno finito **all'istante di adesso** (il
   * looper e lo scheduler della prova, 0 ms). Quanti millisecondi veri ci mettano i fili non cambia i fotogrammi né la
   * misura. Oltre [ReadyTimeoutMillis] veri la prova cade col nome della scena. Restituisce i fotogrammi disegnati.
   */
  fun awaitReady(meter: GlassBalanceMeter, what: String, ready: (List<SemanticsNode>) -> Boolean): Int {
    val deadline = System.nanoTime() + ReadyTimeoutMillis * 1_000_000
    var frames = 0
    while (!ready(semantics())) {
      check(System.nanoTime() < deadline) { "$what: la scena non è pronta dopo ${ReadyTimeoutMillis / 1000} s veri ($frames fotogrammi)" }
      if (pendingWork()) {
        frame(meter)
        frames++
      } else {
        Thread.sleep(1)
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeBy(0, ignoreFrameDuration = true)
        Snapshot.sendApplyNotifications()
      }
    }
    return frames
  }

  @OptIn(ExperimentalComposeRuntimeApi::class)
  private fun pendingWork(): Boolean = Recomposer.runningRecomposers.value.any { it.hasPendingWork }

  /** I nodi della semantica (non uniti) di tutte le radici di Compose della finestra, letti senza aspettare l'inattività. */
  fun semantics(): List<SemanticsNode> {
    val roots = ArrayList<RootForTest>()
    fun walk(view: View) {
      if (view is RootForTest) roots += view
      if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
    }
    walk(compose.activity.window.decorView)
    return roots.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false, skipDeactivatedNodes = true) }
  }

  /** [millis] di tempo a passi di un fotogramma, disegnando un fotogramma ogni [drawEvery]. */
  fun idle(millis: Long, drawEvery: Int, meter: GlassBalanceMeter? = null) {
    val steps = ((millis + FrameMillis - 1) / FrameMillis).toInt()
    for (step in 1..steps) {
      awaitBackground()
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FrameMillis))
      Snapshot.sendApplyNotifications()
      // Con un misuratore gli eventi dei fotogrammi non disegnati arrivano al disegno dopo (le richieste di ridisegno).
      if (step % drawEvery == 0 || step == steps) draw(meter) else if (meter == null) discardEvents()
    }
  }

  private fun discardEvents() {
    logIndex = ShadowLog.getLogsForTag(FrameTag).size
    finishes.clear()
  }

  /**
   * La posa: due campioni separati da 2 s, con un disegno a ogni fotogramma. F7.32d: fino a F7.32c un disegno ogni 25
   * fotogrammi (5 in 2 s): il lavoro dei fotogrammi non disegnati si raccoglieva nel disegno dopo, e un ridisegno a ogni
   * fotogramma ne contava 5 invece di 125, un'animazione di 200 ms uno invece di 12. Ora la posa conta il lavoro di ogni
   * fotogramma come sul tablet, e il suo tetto è la misura (zero resta zero).
   */
  fun quiet(meter: GlassBalanceMeter, name: String = "posa") {
    draw()
    meter.beginQuiet(name)
    idle(QuietMillis, drawEvery = 1, meter = meter)
    meter.end()
  }

  private fun substituteLargePrograms(): List<String> {
    val cache = ReflectionHelpers.getStaticField<MutableMap<String, RuntimeShader>>(SharedRuntimeShaders::class.java, "shaders")
    val substitutes = ArrayList<String>()
    for (source in listOf(GlassShaderSources.Body8, GlassShaderSources.BodyFilms8)) {
      if (cache.containsKey(source.text)) continue
      for (capacity in 8 downTo 1) {
        val program = source.text.replace("i < 8; ++i", "i < $capacity; ++i")
        try {
          val shader = RuntimeShader(program)
          if (capacity < 8 && cache.putIfAbsent(source.text, shader) == null) substitutes.add(source.text)
          break
        } catch (error: IllegalArgumentException) {
          if (capacity == 1 || error.message?.contains("program is too large") != true) throw error
        }
      }
    }
    return substitutes
  }

  /**
   * F7.29 · I programmi AGSL dell'engine stanno in una cache del processo (`SharedRuntimeShaders`): il primo vetro che
   * ne usa uno lo compila, chi viene dopo lo ritrova. Senza questo passo l'entrata di una scena contava i programmi che
   * nessuna prova prima di lei, nella stessa JVM, aveva già compilato, e la misura dipendeva dall'ordine delle classi
   * (Gradle fa girare prima le classi cadute l'ultima volta, e apre una JVM nuova ogni 80 classi): `menu-tasto` 17 o 18
   * (il bagliore del tasto, `Glow`), `scorrimento-testo` 45 o 47 (il corpo e la luce del materiale `Liquid`), 26–27/11.
   * Qui si compilano tutti prima della prima misura, fuori dai contatori: ogni scena si misura a processo caldo, come
   * sul dispositivo dopo il riscaldamento (`GlassWarmUp`, spento in queste prove), e un programma che un componente crea
   * per conto suo, fuori dalla cache, conta ancora. Restano nella cache come li lascerebbe una scena: sono i programmi
   * veri, senza sostituti.
   */
  private fun compileSharedPrograms() {
    val cache = ReflectionHelpers.getStaticField<MutableMap<String, RuntimeShader>>(SharedRuntimeShaders::class.java, "shaders")
    for (source in GlassShaderSources.all) {
      if (cache.containsKey(source.text)) continue
      // Un programma che lo Skia di Robolectric rifiuta non entra nella cache: la scena che lo usa fa come prima.
      runCatching { SharedRuntimeShaders.obtain(source.text) }
    }
  }

  companion object {
    /**
     * F7.32 · Con questa proprietà di sistema a `1` le scene usano i segnali del movimento fra pagine di serie, uno per
     * processo, come prima di F7.32: solo per `BalanceOrderTest`, che mostra il guasto e poi la correzione. Una
     * proprietà di sistema e non un campo, perché la prova che la scrive gira fuori dalla sandbox di Robolectric.
     */
    const val SharedRouteSignalsProperty = "fluid.bilanci.segnaliCondivisi"

    /** F7.32 · Per `BalanceOrderTest`: a `1` il banco non aspetta il lavoro in sottofondo (com'era prima di F7.32). */
    const val NoBackgroundBarrierProperty = "fluid.bilanci.senzaBarriera"

    /** F7.32 · Per `BalanceOrderTest`: i millisecondi per cui i fili in sottofondo restano occupati quando entra una scena. */
    const val BackgroundDelayProperty = "fluid.bilanci.ritardoSottofondo"
    const val FrameTag = "FluidGlassFrame"

    /** F7.32c · Dove comincia l'orologio delle catture del vetro nelle prove ([GlassCaptureClock]): un milione di ms. */
    const val CaptureClockOrigin = 1_000_000L

    /** F7.32c · Per `BalanceOrderTest`: a `1` le decisioni di cattura del vetro usano l'orologio di sistema, com'era prima. */
    const val SystemCaptureClockProperty = "fluid.bilanci.orologioDiSistemaCatture"
    const val FrameMillis = 16L
    const val QuietMillis = 2_000L

    /** F7.29 · Il tetto dell'attesa di [awaitReady], in millisecondi veri: oltre, la scena non arriva e la prova cade. */
    const val ReadyTimeoutMillis = 60_000L
    val ProbeColor = androidx.compose.ui.graphics.Color(0xFF7FA7D9)
  }
}
