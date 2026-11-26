@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.bilanci

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.GlassBenchDevice
import dev.antigravity.fluidengine.sample.GlassBenchHeap
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberCanvasBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawPlainBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.theme.FluidTheme
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
 * F7.28 · Rosso → verde per ogni guardia: per ciascuna, la scena di base è verde e una **mutazione** (un difetto messo
 * apposta nella scena, non una soglia cambiata) la fa diventare rossa. I tetti delle prove si prendono dalla misura della
 * base, quindi la mutazione cade senza alzare né abbassare niente.
 *
 * Le guardie sono quelle che `GlassNodeCountTest`, `GlassBalanceScenesTest` e `F8BalanceTest` applicano ai componenti
 * veri: la quiete (due campioni separati), la doppia registrazione nello stesso fotogramma, il tetto, il filtro oltre il
 * rettangolo più il margine ottico, il figlio aggiornato rimasto sulla cattura vecchia, le fabbriche in moto stabile.
 * Quella delle allocazioni sta in `GlassHotPathAllocationTest`, quella di completezza in `GlassBalanceRegistryTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [35],
  qualifiers = "w800dp-h1280dp-mdpi",
  application = GlassBenchDevice::class,
  shadows = [
    GlassBenchHeap::class, CountingRenderNode::class, CountingBitmap::class, CountingRenderEffect::class,
    CountingRuntimeShader::class,
  ],
)
class GlassBalanceGuardsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val harness = BalanceHarness(compose)
  private lateinit var restore: AutoCloseable
  private val tick = mutableIntStateOf(0)

  @Before
  fun start() {
    compose.mainClock.autoAdvance = false
    restore = harness.install()
  }

  @After
  fun stop() = restore.close()

  /** Il fondo con la sua sorgente e, sopra, [content]. */
  @Composable
  private fun Stage(source: @Composable () -> Unit = {}, content: @Composable (GlassBackdropState) -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val backdrop = rememberGlassBackdrop()
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFF7FA7D9))) { source() }
        content(backdrop)
      }
    }
  }

  @Composable
  private fun Slab(backdrop: GlassBackdropState, x: Int = 0, modifier: Modifier = Modifier) {
    Box(
      modifier
        .offset { IntOffset(x, 0) }
        .padding(start = 40.dp, top = 80.dp)
        .size(156.dp, 56.dp)
        .glassSurface(
          state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape,
          role = GlassRole.Floating, optics = GlassDefaults.optics(GlassRole.Floating),
        ),
    )
  }

  /** La riga col tetto uguale alla misura: chi la passa costa esattamente quanto la base. */
  private fun rowOf(id: String, phase: String, measured: Map<BalanceColumn, Long>): BalanceFile {
    val cells = BalanceColumn.entries.joinToString(" ") { measured[it]?.toString() ?: "-" }
    return GlassBalanceRegistry.parse("prova.bilancio", "$id $phase $cells F7.28 GlassBalanceGuardsTest | il tetto è la misura della base")
  }

  private fun mountAndRest(label: String, content: @Composable () -> Unit): GlassBalanceMeter {
    val meter = harness.meter(label)
    meter.begin("entrata")
    harness.show(content)
    repeat(30) { harness.frame(meter) }
    meter.end()
    harness.quiet(meter)
    return meter
  }

  // ---- 1. La quiete: due campioni separati -----------------------------------------------------------------------

  @Test
  fun quieteUnaLastraFermaNonRidisegnaEUnaCheRespiraCade() {
    val base = mountAndRest("lastra ferma") { Stage { Slab(it) } }
    val quiet = checkNotNull(base.results["posa"])
    assertEquals("base: niente catture fra i due campioni", 0L, quiet.getValue(BalanceColumn.Captures))
    assertEquals("base: nessuna registrazione fra i due campioni", 0L, quiet.getValue(BalanceColumn.Redraws))
    val zero = GlassBalanceRegistry.parse(
      "prova.bilancio",
      "lastra posa 9 9 9 0 999999 9999 0 0 0 - F7.28 GlassBalanceGuardsTest | la posa di una lastra: niente di nuovo in 2 s",
    )
    assertEquals("base verde", emptyList<String>(), base.compare(zero, "lastra").filter { "posa" in it })

    // Mutazione: il contenuto della lastra respira (un'animazione infinita letta nel disegno).
    val breathing = mountAndRest("lastra che respira") {
      Stage { backdrop ->
        val alpha by rememberInfiniteTransition(label = "respiro").animateFloat(
          0.2f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "alpha",
        )
        Box(Modifier.padding(start = 40.dp, top = 80.dp)) {
          Slab(backdrop, modifier = Modifier.drawBehind { drawRect(Color.White.copy(alpha = alpha)) })
        }
      }
    }
    val problems = breathing.compare(zero, "lastra").filter { "posa" in it }
    assertTrue("la mutazione deve cadere: $problems", problems.any { "rid" in it })
  }

  // ---- 2. Stesso campo, stessa revisione --------------------------------------------------------------------------

  private fun recordingScene(times: Int): GlassBalanceMeter {
    val meter = harness.meter("registra $times")
    harness.show {
      val layer = rememberGraphicsLayer()
      Box(Modifier.size(120.dp).drawBehind {
        tick.intValue
        repeat(times) { layer.record { drawRect(Color.Red) } }
        drawLayer(layer)
      })
    }
    repeat(4) { harness.frame() }
    meter.begin("moto", stableFrames = 2..9)
    repeat(12) {
      compose.runOnUiThread { tick.intValue++ }
      harness.frame(meter)
    }
    meter.end()
    return meter
  }

  @Test
  fun unLayerRegistratoDueVolteNelloStessoFotogrammaCade() {
    val base = recordingScene(times = 1)
    assertEquals("base verde", emptyList<String>(), base.violations)
    val mutant = recordingScene(times = 2)
    assertTrue("la mutazione deve cadere: ${mutant.violations}", mutant.violations.any { "registrato 2 volte" in it })
  }

  // ---- 3. Il tetto documentato -----------------------------------------------------------------------------------

  @Test
  fun unaLastraInPiuSuperaIlTettoDellaBase() {
    val first = mountAndRest("una lastra") { Stage { Slab(it) } }
    val row = rowOf("lastra", "entrata", checkNotNull(first.results["entrata"]))
    harness.clear()
    repeat(10) { harness.frame() }
    val again = mountAndRest("una lastra, di nuovo") { Stage { Slab(it) } }
    assertEquals("la stessa scena due volte: stessa misura", emptyList<String>(),
      again.compare(row, "lastra").filter { "entrata" in it })
    harness.clear()
    repeat(10) { harness.frame() }
    val two = mountAndRest("due lastre") {
      Stage { backdrop ->
        Column {
          Slab(backdrop)
          Slab(backdrop)
        }
      }
    }
    val problems = two.compare(row, "lastra").filter { "entrata" in it }
    assertTrue("la lastra in più deve cadere: $problems", problems.any { "nodi" in it && "layer" in it })
  }

  // ---- 4. Il filtro dentro il rettangolo più il margine -------------------------------------------------------------

  private fun movingPlainGlass(label: String, blurPx: Float): GlassBalanceMeter {
    val meter = harness.meter(label)
    val x = mutableIntStateOf(0)
    harness.show {
      Stage { backdrop ->
        Box(
          Modifier.offset { IntOffset(x.intValue, 0) }.padding(start = 40.dp, top = 200.dp).size(200.dp, 100.dp)
            .drawPlainBackdrop(backdrop.backdrop, { FluidCapsuleShape }, effects = {
              if (blurPx > 0f) blur(blurPx, TileMode.Decal)
            }),
        )
      }
    }
    repeat(6) { harness.frame() }
    meter.begin("moto", stableFrames = 2..11)
    repeat(14) {
      compose.runOnUiThread { x.intValue += 6 }
      harness.frame(meter)
    }
    meter.end()
    return meter
  }

  @Test
  fun unFiltroCheEsceDalRettangoloPiuIlMargineCade() {
    val base = movingPlainGlass("vetro senza sfocatura", blurPx = 0f)
    assertTrue("la base deve catturare e controllare le catture", base.checkedCaptures > 0)
    assertEquals("base verde", emptyList<String>(), base.violations)
    val mutant = movingPlainGlass("vetro con 24 px di sfocatura che allarga la cattura", blurPx = 24f)
    assertTrue("la mutazione deve cadere: ${mutant.violations}", mutant.violations.any { "oltre il margine ottico" in it })
  }

  // ---- 5. Un figlio aggiornato non resta sulla cattura vecchia --------------------------------------------------------

  private fun pixelAt(offset: Offset): Int {
    val window = compose.activity.window
    val view = window.decorView
    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    return image.getPixel(offset.x.toInt(), offset.y.toInt())
  }

  /** Vero se il vetro, dopo il cambio del figlio, mostra il colore nuovo (il canale verde sale di almeno 60 livelli). */
  private fun childReachesTheGlass(snapshot: Boolean, liquid: Boolean): Boolean {
    val child = mutableStateOf(Color.Red)
    val x = mutableIntStateOf(0)
    harness.show {
      Stage(source = {
        // Il figlio della sorgente ha il suo layer: si registra da solo, la sorgente lo tiene per riferimento.
        Box(Modifier.fillMaxSize().graphicsLayer().drawBehind { drawRect(child.value) })
      }) { backdrop ->
        val frozen = remember {
          // La mutazione: il vetro campiona una copia del fondo presa una volta, non il fondo vivo.
          Bitmap.createBitmap(800, 1280, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.Red.toArgb()) }.asImageBitmap()
        }
        val stale = rememberCanvasBackdrop { drawImage(frozen) }
        val modifier = Modifier.offset { IntOffset(x.intValue, 0) }.padding(start = 40.dp, top = 200.dp).size(200.dp, 100.dp)
          .testTag("vetro")
        if (liquid && !snapshot) {
          Box(modifier.glassSurface(
            state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape,
            role = GlassRole.Floating, optics = GlassDefaults.optics(GlassRole.Floating),
          ))
        } else {
          Box(modifier.drawPlainBackdrop(if (snapshot) stale else backdrop.backdrop, { FluidCapsuleShape }, effects = {}))
        }
      }
    }
    repeat(6) { harness.frame() }
    repeat(6) {
      compose.runOnUiThread { x.intValue += 4 }
      harness.frame()
    }
    val center = compose.onNodeWithTag("vetro").fetchSemanticsNode().boundsInRoot.center
    val before = pixelAt(center)
    val logStart = org.robolectric.shadows.ShadowLog.getLogsForTag(BalanceHarness.FrameTag).size
    compose.runOnUiThread {
      child.value = Color.Green
      x.intValue += 4
    }
    harness.frame()
    val after = pixelAt(center)
    if (liquid) {
      // Diagnosi: che cosa ha fatto il vetro nel fotogramma del cambio.
      val kinds = Regex(""" kind=(capture|decide|glassSolid|glassDeniedFill|glassBudgetDenied|glassAwaitReading|draw|bodyEffect) """)
      org.robolectric.shadows.ShadowLog.getLogsForTag(BalanceHarness.FrameTag).drop(logStart)
        .filter { kinds.containsMatchIn(it.msg) }.take(30).forEach { println("Figlio Liquid | ${it.msg.take(400)}") }
    }
    val rise = android.graphics.Color.green(after) - android.graphics.Color.green(before)
    println("Figlio aggiornato (copia=$snapshot, liquid=$liquid): prima #${Integer.toHexString(before)}, dopo #${Integer.toHexString(after)}, verde +$rise")
    return rise >= 60
  }

  @Test
  fun unFiglioAggiornatoArrivaAlVetroInMotoEUnaCopiaVecchiaCade() {
    assertTrue("base: il vetro semplice in moto vede subito il figlio nuovo", childReachesTheGlass(snapshot = false, liquid = false))
    assertTrue("mutazione: il vetro che campiona una copia resta rosso", !childReachesTheGlass(snapshot = true, liquid = false))
  }

  @Test
  fun unFiglioAggiornatoArrivaAllaLastraLiquidInMoto() {
    assertTrue("la lastra Liquid in moto vede subito il figlio nuovo", childReachesTheGlass(snapshot = false, liquid = true))
  }

  // ---- 6. Le fabbriche in moto stabile ----------------------------------------------------------------------------------

  private enum class Factory { None, Bitmap, Layer, Program, Filter }

  private fun factoryScene(make: Factory): GlassBalanceMeter {
    val meter = harness.meter("fabbrica $make")
    harness.show {
      val graphics = LocalGraphicsContext.current
      Box(Modifier.size(120.dp).drawBehind {
        tick.intValue
        when (make) {
          Factory.None -> Unit
          Factory.Bitmap -> Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
          Factory.Layer -> graphics.releaseGraphicsLayer(graphics.createGraphicsLayer())
          Factory.Program -> android.graphics.RuntimeShader("half4 main(float2 p) { return half4(1); }")
          Factory.Filter -> RenderEffect.createOffsetEffect(tick.intValue.toFloat(), 0f)
        }
        drawRect(Color.Red)
      })
    }
    repeat(4) { harness.frame() }
    meter.begin("moto", stableFrames = 2..9)
    repeat(12) {
      compose.runOnUiThread { tick.intValue++ }
      harness.frame(meter)
    }
    meter.end()
    return meter
  }

  @Test
  fun unaBitmapUnLayerUnProgrammaOUnFiltroPerFotogrammaCadono() {
    val zero = GlassBalanceRegistry.parse(
      "prova.bilancio",
      "scena moto - - - - - - 0 0 - - F7.28 GlassBalanceGuardsTest | in moto stabile niente fabbriche nuove",
    )
    val base = factoryScene(Factory.None)
    assertEquals("base verde", emptyList<String>(), base.compare(zero, "scena"))
    for (make in listOf(Factory.Bitmap, Factory.Layer, Factory.Program, Factory.Filter)) {
      val problems = factoryScene(make).compare(zero, "scena")
      val column = if (make == Factory.Filter) "fx" else "fab"
      assertTrue("la mutazione $make deve cadere su $column: $problems", problems.any { "$column 1" in it })
    }
  }
}
