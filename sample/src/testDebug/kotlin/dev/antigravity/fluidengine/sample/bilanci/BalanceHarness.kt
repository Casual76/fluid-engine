@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.bilanci

import android.graphics.RenderNode
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.SharedRuntimeShaders
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.GlassCostLevers
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
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
   * Mostra [content] al posto della scena di prima (la regola di prova permette un solo `setContent` per prova): una
   * chiave per scena, così lo stato di una non passa alla dopo.
   */
  fun show(content: @Composable () -> Unit) {
    if (!contentSet) {
      contentSet = true
      scene.value = content
      compose.setContent { key(generation.intValue) { scene.value?.invoke() } }
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

  /** Un fotogramma: l'orologio avanza di 16 ms, gli stati scritti si consegnano, la finestra si disegna. */
  fun frame(meter: GlassBalanceMeter? = null) {
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

  /** [millis] di tempo a passi di un fotogramma, disegnando un fotogramma ogni [drawEvery]. */
  fun idle(millis: Long, drawEvery: Int, meter: GlassBalanceMeter? = null) {
    val steps = ((millis + FrameMillis - 1) / FrameMillis).toInt()
    for (step in 1..steps) {
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
   * La posa: due campioni separati da 2 s (un disegno ogni 25 fotogrammi). Ogni lavoro autonomo nel mezzo (una cattura,
   * un layer, una registrazione, un filtro) si vede al disegno dopo, anche se il fotogramma in cui è nato non si disegna.
   */
  fun quiet(meter: GlassBalanceMeter, name: String = "posa") {
    draw()
    meter.beginQuiet(name)
    idle(QuietMillis, drawEvery = 25, meter = meter)
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
    const val FrameTag = "FluidGlassFrame"
    const val FrameMillis = 16L
    const val QuietMillis = 2_000L
    val ProbeColor = androidx.compose.ui.graphics.Color(0xFF7FA7D9)
  }
}
