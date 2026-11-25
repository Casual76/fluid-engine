@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.RenderNode
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberCanvasBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
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
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers

/**
 * F7.19 · Un ridisegno che `DrawBackdropNode` si chiede **da fuori di un fotogramma** (un `Handler`, un ricevitore, un
 * osservatore degli stati) deve arrivare al suo disegno.
 *
 * Sul Tab S9 (F8.10, 25/11) l'azione di debug «ridisegna» (un `invalidateDraw()` per ogni vetro, dal ricevitore) faceva
 * rendere due fotogrammi all'app, ma nessuno dei sette vetri si disegnava. Il banco e' quello di [F7p18GlassCeilingTest]:
 * la finestra si disegna su un `RenderNode` come in un fotogramma vero, e un layer si registra di nuovo solo se e' sporco.
 * Il disegno del nodo si conta dal suo `onDrawBehind`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-mdpi", application = GlassBenchDevice::class, shadows = [GlassBenchHeap::class])
class F7p19RedrawFromOutsideTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false
  private lateinit var frameLog: Lazy<Boolean>
  private var previousFrameLog: Any? = null
  private val main = Handler(Looper.getMainLooper())

  @Before fun start() {
    frameLog = ReflectionHelpers.getStaticField(GlassFrameDiagnostics::class.java, "enabled\$delegate")
    previousFrameLog = ReflectionHelpers.getField(frameLog, "_value")
    ReflectionHelpers.setField(frameLog, "_value", true)
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    ReflectionHelpers.setField(frameLog, "_value", previousFrameLog)
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun drawHardwareFrame() {
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      val display = RenderNode("F7.19")
      display.setPosition(0, 0, view.width, view.height)
      val canvas = display.beginRecording()
      try { view.draw(canvas) } finally {
        display.endRecording()
        display.discardDisplayList()
      }
    }
  }

  /** Un fotogramma: il looper avanza, le scritture arrivano agli osservatori, la finestra si disegna. Mai `waitForIdle`. */
  private fun frame(ms: Long = 16) {
    compose.mainClock.advanceTimeBy(ms)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    Snapshot.sendApplyNotifications()
    drawHardwareFrame()
  }

  private fun frames(n: Int) = repeat(n) { frame() }

  private fun events(kind: String, from: Int): Int =
    ShadowLog.getLogsForTag(FrameTag).drop(from).count { " kind=$kind " in it.msg }

  private fun report(label: String, before: Int, after: Int, from: Int) {
    println(
      "F719OUT | $label | disegni $before -> $after | draw ${events("draw", from)} | catture ${events("capture", from)}" +
        " | attesa ${events("awaitReading", from)} | lettura ${events("glassReadingRefreshed", from)}" +
        " | export ${FluidGlassDiagnostics.totalExports}",
    )
    val kinds = ShadowLog.getLogsForTag(FrameTag).drop(from).mapNotNull { Regex(""" kind=(\S+)""").find(it.msg)?.groupValues?.get(1) }
    println("F719KINDS | $label | ${kinds.groupingBy { it }.eachCount()}")
  }

  /** Dopo il ridisegno chiesto il vetro torna fermo: nessun anello di richieste. */
  private fun assertRestsAfter(label: String, draws: () -> Int) {
    val settled = draws()
    frames(6)
    assertEquals("$label: a riposo il vetro non si ridisegna piu'", settled, draws())
  }

  /** Il controllo: uno stato letto **nel** disegno del nodo, scritto da un `Handler`. Compose ridisegna il layer giusto. */
  @Test fun unoStatoLettoNelDisegnoScrittoDaUnHandlerRidisegna() {
    val tint = mutableStateOf(Color.Red)
    var draws = 0
    compose.setContent {
      val source = rememberCanvasBackdrop { drawRect(Color.Blue) }
      Box(Modifier.size(200.dp).drawBackdrop(
        backdrop = source, shape = { RectangleShape }, effects = {},
        highlight = null, shadow = null, innerShadow = null,
        onDrawBehind = { draws++; drawRect(tint.value) },
      ))
    }
    frames(6)
    val before = draws
    val from = ShadowLog.getLogsForTag(FrameTag).size
    main.post { tint.value = Color.Green }
    frames(4)
    report("stato nel disegno", before, draws, from)
    assertTrue("uno stato letto nel disegno, scritto da un Handler, ridisegna il vetro ($before -> $draws)", draws > before)
  }

  /**
   * Il primo lettore del materiale arriva da fuori del fotogramma (`addReader` da un `Handler`): il pannello deve tornare
   * a disegnare per registrare il materiale. Il nodo lo chiede con `wakeExport`, cioe' con un `invalidateDraw()`.
   */
  @Test fun ilPrimoLettoreDelMaterialeDaUnHandlerRidisegnaIlPannello() {
    var draws = 0
    lateinit var material: dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
    compose.setContent {
      val source = rememberCanvasBackdrop { drawRect(Color.Blue) }
      material = rememberLayerBackdrop().also { it.onDemand = true }
      Box(Modifier.size(200.dp).drawBackdrop(
        backdrop = source, shape = { RectangleShape }, effects = {},
        highlight = null, shadow = null, innerShadow = null,
        exportedBackdrop = material,
        onDrawBehind = { draws++ },
      ))
    }
    frames(6)
    val before = draws
    val exportsBefore = FluidGlassDiagnostics.totalExports
    val from = ShadowLog.getLogsForTag(FrameTag).size
    main.post { material.addReader() }
    frames(4)
    report("primo lettore", before, draws, from)
    try {
      assertTrue("il pannello si ridisegna per il primo lettore ($before -> $draws)", draws > before)
      assertTrue("e registra il materiale", FluidGlassDiagnostics.totalExports > exportsBefore)
      assertRestsAfter("primo lettore") { draws }
    } finally {
      material.removeReader()
    }
  }

  /**
   * Un ingresso degli effetti (il raggio della sfocatura) cambiato da un `Handler`, a cornice ferma: arriva dal pre-draw
   * (F3), in un fotogramma chiesto dal nodo, e **non** ridisegna il vetro: la catena e' una proprieta' del layer della
   * cattura. Prima di F7.19 era lo stesso, ma per caso: il disegno chiesto si perdeva e restava solo il fotogramma.
   */
  @Test fun unIngressoDegliEffettiDaUnHandlerArrivaDalPreDraw() {
    val radius = mutableFloatStateOf(4f)
    var draws = 0
    compose.setContent {
      val source = rememberCanvasBackdrop { drawRect(Color.Blue) }
      Box(Modifier.size(200.dp).drawBackdrop(
        backdrop = source, shape = { RectangleShape }, effects = { blur(radius.floatValue) },
        highlight = null, shadow = null, innerShadow = null,
        onDrawBehind = { draws++ },
      ))
    }
    frames(6)
    val before = draws
    val from = ShadowLog.getLogsForTag(FrameTag).size
    main.post { radius.floatValue = 24f }
    frames(4)
    report("ingresso degli effetti", before, draws, from)
    assertTrue("la catena nuova e' installata nel pre-draw", events("beforeFrame", from) > 0 && events("installed", from) > 0)
    assertEquals("senza ridisegnare il vetro", before, draws)
    assertEquals("e senza ricatturare", 0, events("capture", from))
  }

  /** La sfocatura `Decal` cambiata da un `Handler` allarga la cornice: il vetro si ridisegna e ricattura, una volta. */
  @Test fun unaCorniceNuovaDaUnHandlerRidisegnaERicattura() {
    val radius = mutableFloatStateOf(4f)
    var draws = 0
    compose.setContent {
      val source = rememberCanvasBackdrop { drawRect(Color.Blue) }
      Box(Modifier.size(200.dp).drawBackdrop(
        backdrop = source, shape = { RectangleShape }, effects = { blur(radius.floatValue, TileMode.Decal) },
        highlight = null, shadow = null, innerShadow = null,
        onDrawBehind = { draws++ },
      ))
    }
    frames(6)
    val before = draws
    val from = ShadowLog.getLogsForTag(FrameTag).size
    main.post { radius.floatValue = 24f }
    frames(4)
    report("cornice nuova", before, draws, from)
    assertTrue("il vetro si ridisegna quando la cornice cambia ($before -> $draws)", draws > before)
    assertTrue("e ricattura", events("capture", from) > 0)
    assertRestsAfter("cornice nuova") { draws }
  }

  /** Un parametro semplice (la lambda del velo) cambiato da una ricomposizione: il vetro disegna quella nuova. */
  @Test fun unaLambdaNuovaDallaRicomposizioneSiDisegna() {
    val green = mutableStateOf(false)
    var drawnGreen = 0
    var draws = 0
    compose.setContent {
      val source = rememberCanvasBackdrop { drawRect(Color.Blue) }
      val isGreen by green
      val behind: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = remember(isGreen) {
        { draws++; if (isGreen) drawnGreen++ }
      }
      Box(Modifier.size(200.dp).drawBackdrop(
        backdrop = source, shape = { RectangleShape }, effects = {},
        highlight = null, shadow = null, innerShadow = null,
        onDrawBehind = behind,
      ))
    }
    frames(6)
    val before = draws
    val from = ShadowLog.getLogsForTag(FrameTag).size
    main.post { green.value = true }
    frames(6)
    report("lambda nuova", before, draws, from)
    assertTrue("il vetro disegna la lambda nuova (verdi $drawnGreen, disegni $before -> $draws)", drawnGreen > 0)
    assertRestsAfter("lambda nuova") { draws }
  }

  /**
   * Il vetro grande che trova la lettura della memoria vecchia aspetta (`AwaitReading`), chiede la lettura al looper e
   * si risveglia con `wake()` (F7.18). Il risveglio deve portare la cattura. La taglia resta ferma (un cambio di taglia
   * rinfresca la lettura gia' nella misura): la cattura piu' grande la chiede la sfocatura `Decal`, che allarga la cornice.
   */
  @Test fun dopoLAttesaDellaLetturaIlRisveglioPortaLaCattura() {
    val radius = mutableFloatStateOf(8f)
    var draws = 0
    compose.setContent {
      val source = rememberCanvasBackdrop { drawRect(Color.Blue) }
      Box(Modifier.size(600.dp).drawBackdrop(
        backdrop = source, shape = { RectangleShape }, effects = { blur(radius.floatValue, TileMode.Decal) },
        highlight = null, shadow = null, innerShadow = null,
        onDrawBehind = { draws++ },
      ))
    }
    frames(6)
    // La lettura invecchia (piu' di un secondo): la prossima cattura grande la deve aspettare.
    val resources = FluidGlassResources.get(compose.activity)
    ReflectionHelpers.setField(resources, "allocationReadingAtNs", android.os.SystemClock.elapsedRealtimeNanos() - 5_000_000_000L)
    val before = draws
    val from = ShadowLog.getLogsForTag(FrameTag).size
    main.post { radius.floatValue = 24f }
    frames(6)
    report("attesa della lettura", before, draws, from)
    assertTrue("il vetro ha aspettato la lettura", events("awaitReading", from) > 0)
    assertTrue("la lettura e' arrivata dal looper", events("glassReadingRefreshed", from) > 0)
    assertTrue("e dopo la lettura ha catturato", events("capture", from) > 0)
    assertRestsAfter("attesa della lettura") { draws }
  }

  /**
   * Dopo un calo di risoluzione (la qualita' che scende mentre la pagina si muove) il vetro fermo torna a scala piena:
   * il ritorno lo chiede `recoverResolution`, un `Runnable`, quando nessun altro ridisegna il vetro. Prima di F7.19 il
   * suo `invalidateDraw()` si perdeva e il vetro restava alla cattura ridotta (il foglio di `GlassNodeCountTest`
   * fermo a 0,365 invece di 0,73). La qualita' qui non e' uno stato: cambiarla non ridisegna niente.
   */
  @Test fun dopoUnCaloDiRisoluzioneIlVetroFermoTornaPieno() {
    var quality = 0.5f
    val tick = mutableIntStateOf(0)
    compose.setContent {
      val source = rememberCanvasBackdrop { drawRect(Color.Blue) }
      Box(Modifier.size(600.dp).drawBackdrop(
        backdrop = source, shape = { RectangleShape }, effects = {},
        highlight = null, shadow = null, innerShadow = null,
        onDrawBehind = { tick.intValue },
        backdropScaleFactor = { quality },
      ))
    }
    frames(2)
    val from = ShadowLog.getLogsForTag(FrameTag).size
    // Tre fotogrammi «in movimento», ognuno col suo disegno.
    repeat(3) { tick.intValue++; frame() }
    quality = 1f
    // Poi fermo: nessuno stato cambia, nessun disegno chiesto da fuori.
    frames(12)
    val scales = ShadowLog.getLogsForTag(FrameTag).drop(from)
      .filter { " kind=capture " in it.msg }
      .map { Regex("""\bscale=([0-9.]+)""").find(it.msg)!!.groupValues[1].toFloat() }
    report("risoluzione", 0, scales.size, from)
    val reasons = ShadowLog.getLogsForTag(FrameTag).drop(from).filter { " kind=redrawRequest " in it.msg }
      .map { it.msg.substringAfter("reason=").substringBefore(' ') }
    println("F719SCALE | $scales | richieste $reasons")
    assertTrue("in movimento la cattura e' ridotta, fermo e' piena: $scales",
      scales.size >= 2 && scales.dropLast(1).all { it < scales.last() } && scales.last() == scales.max())
    assertTrue("il ritorno l'ha chiesto recoverResolution: $reasons", "resolution" in reasons)
  }

  private companion object {
    const val FrameTag = "FluidGlassFrame"
  }
}
