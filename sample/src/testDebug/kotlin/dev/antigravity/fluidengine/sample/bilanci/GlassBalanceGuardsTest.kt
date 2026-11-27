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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
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

  private enum class Factory { None, Bitmap, Layer, Program, Filter, LayerEverySixth }

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
          // Una riga che entra in vista ogni sei fotogrammi, coi suoi layer nuovi (F7.29).
          Factory.LayerEverySixth -> if (tick.intValue % 6 == 0) graphics.releaseGraphicsLayer(graphics.createGraphicsLayer())
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

  /**
   * F7.29 · La riga che entra e la fabbrica a ogni fotogramma. Il tetto `fab` del moto è il massimo per fotogramma: una
   * lista che scorre lo raggiunge quando entra una riga, ogni tanto, e la stessa colonna non distingue un layer che nasce
   * a ogni fotogramma. La scena col layer ogni sei fotogrammi passa sotto il tetto 1; quella col layer a ogni fotogramma
   * misura anche lei 1, quindi il tetto non la ferma: cade sulla guardia dei fotogrammi stabili (fabbriche in almeno metà).
   */
  @Test
  fun unaRigaCheEntraPassaEUnLayerAOgniFotogrammaCadeAncheSottoIlTetto() {
    val one = GlassBalanceRegistry.parse(
      "prova.bilancio",
      "scena moto - - - - - - 1 0 - - F7.29 GlassBalanceGuardsTest | una riga che entra: un layer nuovo, ogni tanto",
    )
    val rows = factoryScene(Factory.LayerEverySixth)
    assertEquals("un layer ogni sei fotogrammi: verde", emptyList<String>(), rows.compare(one, "scena"))
    val every = factoryScene(Factory.Layer).compare(one, "scena")
    assertTrue("un layer a ogni fotogramma deve cadere anche sotto il tetto: $every", every.any { "fotogrammi stabili: layer, bitmap o programmi nuovi" in it })
    assertTrue("e non sul tetto, che lo lascerebbe passare: $every", every.none { "fab 1 (tetto" in it })
  }

  /**
   * F7.29b · D4 sempre, non solo quando il tempo vero lo vuole. Un pannello di vetro con un vetro dentro entra in scena
   * mentre il moto è misurato: il vetro dentro campiona il materiale proprio del pannello (il layer che nasce quando il
   * vetro dentro si attacca) nel fotogramma in cui lo trova la prima volta. Prima di F7.29b quel layer non aveva ancora
   * una display list: `drawLayer` del vetro dentro lo registrava vuoto, e il pannello, svegliato dal suo primo lettore,
   * lo registrava di nuovo nello stesso fotogramma (in `GlassBalanceScenesTest` la barra di selezione lo faceva in
   * alcune corse sì e in altre no). Rosso sulla base R3, verde con il materiale che nasce già registrato.
   */
  @Test
  fun unPannelloCheEntraColVetroDentroRegistraIlSuoMaterialeUnaVolta() {
    val shown = mutableStateOf(false)
    val meter = harness.meter("pannello col vetro dentro")
    harness.show {
      Stage { backdrop ->
        if (shown.value) {
          Box(
            Modifier
              .padding(start = 40.dp, top = 80.dp)
              .size(260.dp, 96.dp)
              .glassSurface(
                state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape,
                role = GlassRole.Floating, optics = GlassDefaults.optics(GlassRole.Floating),
              ),
          ) {
            Box(
              Modifier
                .padding(16.dp)
                .size(64.dp)
                .glassSurface(
                  state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape,
                  role = GlassRole.Floating, optics = GlassDefaults.optics(GlassRole.Floating),
                ),
            )
          }
        }
      }
    }
    repeat(8) { harness.frame() }
    meter.begin("moto")
    compose.runOnUiThread { shown.value = true }
    repeat(12) { harness.frame(meter) }
    meter.end()
    // La seconda registrazione del materiale viene dall'export del pannello (`DrawBackdropNode.drawGlass`). Nello stesso
    // fotogramma un altro layer del pannello si registra due volte per un'altra causa, la stessa di D5 (uno stato scritto
    // nel disegno e applicato a metà disegno quando il pannello avvia l'osservatore del suo export): resta scritto, non
    // corretto qui (rapporto F7.29, cause-d2-d5.md), e la prova lo stampa.
    val doubles = meter.violations.filter { "registrato" in it }
    doubles.filter { "drawGlass" !in it }.forEach { println("Bilancio pannello col vetro dentro, nota (causa di D5): $it") }
    assertEquals("all'entrata il pannello registra il suo materiale una volta per fotogramma", emptyList<String>(),
      doubles.filter { "drawGlass" in it })
  }

  // ---- F7.32d · La posa dopo il gesto ---------------------------------------------------------------------------

  /** Una lastra su cui si trascina il dito; con [keepsDrawing] dopo il rilascio comincia a respirare e non smette più. */
  @Composable
  private fun AfterTheGesture(keepsDrawing: Boolean) {
    Stage { backdrop ->
      var released by remember { mutableStateOf(false) }
      Box(Modifier.fillMaxSize().pointerInput(Unit) { detectDragGestures(onDragEnd = { released = true }) { _, _ -> } }) {
        if (keepsDrawing && released) {
          val alpha by rememberInfiniteTransition(label = "dopo il gesto").animateFloat(
            0.2f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "alpha",
          )
          Slab(backdrop, modifier = Modifier.drawBehind { drawRect(Color.White.copy(alpha = alpha)) })
        } else {
          Slab(backdrop)
        }
      }
    }
  }

  /**
   * F7.32d (D3 della review R3) · Un componente che dopo il rilascio continua a disegnare, come il righello di F7.24b
   * prima della correzione (120 fps per 2,4 s), o un'animazione che dopo il tocco gira per sempre. Il copione degli
   * elementi misurava la posa solo prima del gesto, e lo lasciava passare; la posa dopo il gesto lo fa cadere.
   */
  @Test
  fun unComponenteCheDopoIlRilascioContinuaADisegnareCadeNellaPosaDopo() {
    val zero = GlassBalanceRegistry.parse(
      "prova.bilancio",
      "gesto posa 9 9 9 0 999999 9999 0 0 0 - F7.32d GlassBalanceGuardsTest | la lastra ferma prima del gesto\n" +
        "gesto posa:dopo 9 9 9 0 999999 9999 0 0 0 - F7.32d GlassBalanceGuardsTest | la lastra ferma dopo il gesto",
    )
    fun poses(meter: GlassBalanceMeter) = meter.compare(zero, "gesto").filter { it.startsWith("gesto/posa") && "senza riga" !in it }

    val base = BalanceRuns.measureElement(harness, "gesto", 0) { AfterTheGesture(keepsDrawing = false) }
    assertEquals("base: niente registrazioni dopo il gesto", 0L, checkNotNull(base.results["posa:dopo"]).getValue(BalanceColumn.Redraws))
    assertEquals("base verde", emptyList<String>(), poses(base))

    // Mutazione: dopo il rilascio la lastra respira per sempre.
    val spinning = BalanceRuns.measureElement(harness, "gesto", 0) { AfterTheGesture(keepsDrawing = true) }
    val problems = poses(spinning)
    assertTrue("la mutazione deve cadere nella posa dopo il gesto: $problems", problems.any { it.startsWith("gesto/posa:dopo") && "rid" in it })
    assertTrue("e nella posa prima del gesto non si vede (il copione di prima la lasciava passare): $problems",
      problems.none { it.startsWith("gesto/posa (") })
  }

}
