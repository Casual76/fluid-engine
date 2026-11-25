@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.RenderNode
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.LocalGlassFrontLayer
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassYieldScale
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberCanvasBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawPlainBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.glassFrameMarker
import dev.antigravity.fluidengine.ui.glass.field.GlassCostLevers
import dev.antigravity.fluidengine.ui.glass.field.GlassTravelCapture
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
 * F7.18 · Il tetto del vetro sotto pressione: il pannello davanti ha la precedenza sulla pagina che copre, e una cattura
 * col campione separato (il viaggio, la finitura di Fluid-physics) scende anche nell'uscita, non solo nel campione.
 *
 * Il banco è quello di [GlassNodeCountTest] (8 GiB, heap 1024 MiB). Il registro si stringe a mano con una prenotazione
 * che lascia libero solo quanto serve alla prova; i fondali sono `drawPlainBackdrop` senza filtro, quindi i byte sono
 * quelli della texture e si contano qui sotto. Le catture si leggono dal log del vetro (`kind=capture … owner=…`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-mdpi", application = GlassBenchDevice::class, shadows = [GlassBenchHeap::class])
class F7p18GlassCeilingTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false
  private lateinit var frameLog: Lazy<Boolean>
  private var previousFrameLog: Any? = null

  @Before fun start() {
    frameLog = ReflectionHelpers.getStaticField(GlassFrameDiagnostics::class.java, "enabled\$delegate")
    previousFrameLog = ReflectionHelpers.getField(frameLog, "_value")
    ReflectionHelpers.setField(frameLog, "_value", true)
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
  }

  @After fun stop() {
    ReflectionHelpers.setField(frameLog, "_value", previousFrameLog)
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun drawHardwareFrame() {
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      val display = RenderNode("F7.18")
      display.setPosition(0, 0, view.width, view.height)
      val canvas = display.beginRecording()
      try { view.draw(canvas) } finally {
        display.endRecording()
        display.discardDisplayList()
      }
    }
  }

  /**
   * Un fotogramma: il looper avanza (le riprove del nodo), le scritture di stato arrivano agli osservatori e la finestra si
   * disegna su un `RenderNode`. Senza `waitForIdle`: in Robolectric consumerebbe l'invalidazione del nodo fuori dal disegno
   * hardware, e la riprova del pannello davanti non si vedrebbe mai.
   */
  private fun settle(ms: Long = 16) {
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    // Nessun ridisegno forzato: la riprova del credito deve rimettere da sola il nodo nel fotogramma (F7.18, `budgetWake`).
    Snapshot.sendApplyNotifications()
    drawHardwareFrame()
  }

  private val owner = Regex("""\bowner=(\S+)""")
  private val scale = Regex("""\bscale=([0-9.]+)""")

  /** Le scale delle catture di [label], in ordine, dal log a partire da [from]. */
  private fun captures(label: String, from: Int): List<Float> = ShadowLog.getLogsForTag(FrameTag).drop(from)
    .filter { " kind=capture " in it.msg && owner.find(it.msg)?.groupValues?.get(1) == label }
    .map { scale.find(it.msg)!!.groupValues[1].toFloat() }

  /** RGBA8 senza filtro, righe allineate a 64 byte: il conto di `reserveCapture`. */
  private fun plainBytes(width: Int, height: Int): Long = (width * 4L + 63L) / 64L * 64L * height

  @Test fun ilPannelloDavantiHaIlTettoPrimaDellaPaginaSotto() {
    // Protetto fin dall'avvio (termica severa simulata): la scala di ogni fondale grande è già 0,5, così il rifiuto del
    // pannello davanti non cambia livello e si vede solo la precedenza.
    ShadowLog.setLoggable("FluidF7Hot", Log.VERBOSE)
    val resources = FluidGlassResources.get(compose.activity)
    resources.refresh()
    val budget = resources.budget
    val glass = FluidMemoryCategory.GLASS
    val page = plainBytes(320, 256)
    val frontFull = plainBytes(300, 240)
    // Libero: la pagina a 0,5 e metà del pannello davanti a 0,5. Senza precedenza il pannello resta a 0,25 per sempre.
    val room = page + frontFull / 2
    val before = budget.usage(glass)
    val held = checkNotNull(budget.tryReserve(glass, before.limitBytes - before.usedBytes - room))
    val front = mutableStateOf(false)
    try {
      compose.setContent {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color.Blue))
          Box(Modifier.glassFrameMarker("pagina")) {
            Box(Modifier.size(640.dp, 512.dp).drawPlainBackdrop(backdrop.backdrop, { FluidCapsuleShape }, {}))
          }
          if (front.value) CompositionLocalProvider(LocalGlassFrontLayer provides true) {
            Box(Modifier.glassFrameMarker("davanti")) {
              Box(Modifier.size(600.dp, 480.dp).drawPlainBackdrop(backdrop.backdrop, { FluidCapsuleShape }, {}))
            }
          }
        }
      }
      compose.waitForIdle()
      drawHardwareFrame()
      val first = captures("pagina", 0)
      assertEquals("la pagina parte alla scala del livello", 0.5f, first.last())
      val cursor = ShadowLog.getLogsForTag(FrameTag).size

      compose.runOnIdle { front.value = true }
      compose.waitForIdle()
      repeat(4) { settle() }
      repeat(4) { settle(150) }
      ShadowLog.getLogsForTag(FrameTag).drop(cursor).filter {
        listOf(" kind=capture ", " kind=glassBudget", " kind=glassFront", " kind=glassRelease").any { k -> k in it.msg }
      }.forEach { println("F718LOG | " + it.msg.substringBefore(" kind=").takeLast(10) + " " + it.msg.substringAfter("kind=").take(260)) }
      val frontScales = captures("davanti", cursor)
      val pageScales = captures("pagina", cursor)
      println("F718PREC | davanti $frontScales | pagina $pageScales | domanda ${resources.frontDemand.intValue} | " +
        resources.usageLine())
      assertEquals("il pannello davanti ha chiesto spazio", 1, resources.frontDemand.intValue)
      assertTrue("la pagina cede, a metà lato: $pageScales", pageScales.isNotEmpty() && pageScales.all { it == 0.5f * GlassYieldScale })
      assertEquals("il pannello davanti arriva alla sua scala piena", 0.5f, frontScales.last())

      val closing = ShadowLog.getLogsForTag(FrameTag).size
      compose.runOnIdle { front.value = false }
      compose.waitForIdle()
      repeat(3) { settle() }
      assertEquals("il pannello se ne va e la pagina torna alla sua scala", 0, resources.frontDemand.intValue)
      assertEquals(0.5f, captures("pagina", closing).last())
    } finally {
      held.close()
      ShadowLog.setLoggable("FluidF7Hot", Log.INFO)
      resources.refresh()
    }
  }

  @Test fun conIlCampioneSeparatoIlTettoScendeAncheSullUscita() {
    val resources = FluidGlassResources.get(compose.activity)
    resources.refresh()
    val budget = resources.budget
    val glass = FluidMemoryCategory.GLASS
    val extents = mutableListOf<Pair<IntSize, IntSize>>()
    val previous = GlassCostLevers.onCapture
    GlassCostLevers.onCapture = { sample, finish -> extents.add(sample to IntSize(finish.width.toInt(), finish.height.toInt())) }
    // Un'uscita da 512 × 512 (1 MiB) non entra, nemmeno col campione al minimo: ne entra una da 256 × 256.
    val room = 600_000L
    val before = budget.usage(glass)
    val held = checkNotNull(budget.tryReserve(glass, before.limitBytes - before.usedBytes - room))
    val cursor = ShadowLog.getLogsForTag(FrameTag).size
    try {
      compose.setContent {
        val source = rememberCanvasBackdrop { drawRect(Color.Blue) }
        val layer = rememberGraphicsLayer()
        val density = LocalDensity.current.density
        val travel = remember(layer, density) { GlassTravelCapture(layer, density, {}) }
        Box(Modifier.size(512.dp).drawBackdrop(
          backdrop = source, shape = { FluidCapsuleShape }, effects = travel.effects,
          onDrawBackdrop = travel.capture, highlight = null, shadow = null, innerShadow = null,
        ))
      }
      compose.waitForIdle()
      repeat(3) { settle() }
      val logs = ShadowLog.getLogsForTag(FrameTag).drop(cursor)
      val denied = logs.count { " kind=glassBudgetDenied " in it.msg }
      println("F718OUT | rifiuti $denied | catture ${extents.distinct()} | ${resources.usageLine()}")
      assertTrue("il tetto ha davvero negato l'uscita piena", denied > 0)
      assertTrue("la cattura entra", logs.any { " kind=capture " in it.msg })
      val (sample, finish) = extents.last()
      assertEquals("l'uscita scende a metà lato", IntSize(256, 256), finish)
      // Senza sfocatura nota il minimo è un quarto di lato dell'uscita (vedi `FrostedSampleFloor`).
      assertEquals("il campione resta al suo minimo uguale a vista", IntSize(64, 64), sample)
    } finally {
      held.close()
      GlassCostLevers.onCapture = previous
      resources.refresh()
    }
  }

  private companion object {
    const val FrameTag = "FluidGlassFrame"
  }
}
