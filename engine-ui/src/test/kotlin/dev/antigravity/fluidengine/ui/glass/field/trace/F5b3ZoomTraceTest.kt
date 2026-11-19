package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidSheetOpaqueBand
import dev.antigravity.fluidengine.ui.fluid.FluidSheetZoomSpring
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidSheetDimColor
import dev.antigravity.fluidengine.ui.fluid.fluidMenuReturnFocusSpec
import dev.antigravity.fluidengine.ui.fluid.fluidMenuReturnInkSpec
import dev.antigravity.fluidengine.ui.fluid.fluidSheetLargeHeightPx
import dev.antigravity.fluidengine.ui.fluid.fluidSheetLargeness
import dev.antigravity.fluidengine.ui.fluid.fluidSheetStops
import dev.antigravity.fluidengine.ui.fluid.fluidSheetZoomBlurFraction
import dev.antigravity.fluidengine.ui.fluid.fluidSheetZoomBounds
import dev.antigravity.fluidengine.ui.fluid.fluidSheetZoomContentBlurDp
import dev.antigravity.fluidengine.ui.fluid.fluidSheetZoomVeil
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.page.FluidPageCorners
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.FluidZoom
import dev.antigravity.fluidengine.ui.page.FluidZoomShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.min

/**
 * Le tracce dello zoom dalla sorgente (F5B.3).
 *
 * - **`foglio-dal-tasto`** e **`foglio-dal-tasto-chiude`**: la scena di Apple (WWDC25 284, 14:07: Mail, il tasto
 *   «cartella» nel gruppo della barra in basso apre «Move to mailbox»), sul telefono di 402 × 874 dp. La sagoma è
 *   [fluidSheetZoomBounds] fra la capsula del gruppo e il foglio `Large`, sulla molla dello zoom ([FluidSheetZoomSpring])
 *   in apertura e su quella d'uscita dell'host in chiusura; il velo e la sfocatura del contenuto sono quelli del foglio
 *   ([fluidSheetLargeness], [fluidSheetZoomBlurFraction]); lo scrim è quello dei fogli di oggi, un nero al 23% senza
 *   sfocatura (`fluidSheetDimColor`; fino al giro di correzione di F5B era lo scrim sfocato dei portali).
 * - **`zoom-apre`**, **`zoom-torna`**, **`zoom-dito`**, **`zoom-dito-interrotto`**: una card della griglia che diventa
 *   pagina e torna, **dalla macchina vera** ([FluidPageHostState] con un orologio finto): la sagoma è
 *   [FluidPageHostState.zoomShape], la pagina dentro è in scala come la disegna l'host. La card sorgente non si disegna
 *   finché la macchina la tiene consumata.
 *
 * **Fuori dalla traccia**: l'ombra della carta, la barra in alto (ferma, si trasforma sul suo tempo: F4A), le icone della
 * barra in basso (la capsula sì).
 *
 * Si scrivono con `GLASS_F5B3_DIR` (di solito `tools/glassbench/traces/f5b-3`), fase `f5b-3`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class F5b3ZoomTraceTest {

  private val d = 3f
  private val fps = 60
  private val canvasW = 402f
  private val canvasH = 874f
  private val statusDp = 58f
  private val screenCornerDp = 55f
  private val phase = "f5b-3"

  private val surface = Color.White
  private val grouped = Color(0xFFF2F2F7)
  private val ink = Color(0xFF121214)
  private val secondary = Color(0xFF68686E)

  @Test
  fun `esporta le tracce dello zoom e ne controlla i capi`() {
    val sheetOpen = foglioDalTasto(closing = false)
    val sheetClose = foglioDalTasto(closing = true)
    val open = zoomPagine("zoom-apre")
    val back = zoomPagine("zoom-torna")
    val backFading = zoomPagine("zoom-torna-dissolvenza")
    val finger = zoomPagine("zoom-dito")
    val interrupted = zoomPagine("zoom-dito-interrotto")
    val traces = listOf(sheetOpen, sheetClose, open, back, backFading, finger, interrupted)
    GlassTraceOutput.requestedDir(System.getenv("GLASS_F5B3_DIR"))?.let { dir ->
      for (t in traces) t.write(File(dir, "${t.name}/trace.json"))
      foglioDalTasto(closing = true, returnInk = false).write(File(dir, "foglio-dal-tasto-chiude-prima/trace.json"))
    }

    // Il foglio nasce dal gruppo del tasto e arriva in cima, a filo dei lati.
    val first = sheetOpen.frames.first { it.tMs >= 0.0 }.elements.single { it["name"] == "foglio" }
    assertEquals(Group.left, (first["x"] as Number).toFloat(), 0.01f)
    assertEquals(Group.width, (first["w"] as Number).toFloat(), 0.01f)
    val last = sheetOpen.frames.last().elements.single { it["name"] == "foglio" }
    assertEquals(0f, (last["x"] as Number).toFloat(), 0.01f)
    assertEquals(canvasW, (last["w"] as Number).toFloat(), 0.01f)
    // La pagina nasce dalla card e ci torna; nel primo e nell'ultimo fotogramma della pagina la card non c'è.
    val born = open.frames.first { f -> f.elements.any { it["name"] == "pagina" } }.elements
    assertTrue(born.none { it["name"] == "card-sorgente" })
    val page = born.single { it["name"] == "pagina" }
    assertEquals(SourceCard.left, (page["x"] as Number).toFloat(), 0.5f)
    assertEquals(SourceCard.top, (page["y"] as Number).toFloat(), 0.5f)
    for (t in listOf(back, backFading, finger, interrupted)) {
      val end = t.frames.last().elements
      assertTrue("${t.name}: a riposo la card rinasce", end.any { it["name"] == "card-sorgente" })
      assertTrue("${t.name}: e la pagina non c'è più", end.none { it["name"] == "pagina" })
      val lastPage = t.frames.last { f -> f.elements.any { it["name"] == "pagina" } }.elements.single { it["name"] == "pagina" }
      assertEquals("${t.name}: l'ultima sagoma è sulla card", SourceCard.left, (lastPage["x"] as Number).toFloat(), 2f)
      assertEquals(SourceCard.top, (lastPage["y"] as Number).toFloat(), 2f)
    }
    // Senza la variante nessuna fotografia della card dentro la sagoma; con la variante c'è, e all'arrivo copre la sagoma.
    assertTrue(back.frames.none { f -> f.elements.any { it["name"] == "sorgente-in-sagoma" } })
    val fading = backFading.frames.filter { f -> f.elements.any { it["name"] == "sorgente-in-sagoma" } }
    assertTrue("la dissolvenza c'è", fading.isNotEmpty())
    val home = fading.last().elements.single { it["name"] == "sorgente-in-sagoma" }
    assertEquals(SourceCard.width, (home["w"] as Number).toFloat(), 2f)
    assertTrue("all'arrivo la card è piena: ${home["color"]}", (home["color"] as String).endsWith("FF"))
  }

  @Test
  fun `nel rientro lo strato del foglio perde fuoco subito e si spegne prima della lastra`() {
    val prima = foglioDalTasto(closing = true, returnInk = false)
    val dopo = foglioDalTasto(closing = true)
    fun at(trace: GlassTrace, ms: Double) = trace.frames.first { it.tMs >= ms }
    fun inkAt(trace: GlassTrace, ms: Double): Map<*, *> {
      val content = at(trace, ms).elements.single { it["name"] == "contenuto" }["content"] as List<*>
      return content.single() as Map<*, *>
    }
    assertEquals(1f, (inkAt(dopo, 0.0)["alpha"] as? Number)?.toFloat() ?: 1f, 0f)
    val early = inkAt(dopo, 16.0)
    assertTrue((early["blurDp"] as Number).toFloat() > (inkAt(prima, 16.0)["blurDp"] as Number).toFloat() + .7f)
    assertTrue((early["alpha"] as Number).toFloat() < 0.8f)
    assertEquals(0f, (inkAt(dopo, 90.0)["alpha"] as Number).toFloat(), 0f)
    assertTrue(at(dopo, 90.0).elements.any { it["name"] == "foglio" })
    assertEquals(prima.frames.map { f -> f.elements.filter { it["name"] != "contenuto" } },
      dopo.frames.map { f -> f.elements.filter { it["name"] != "contenuto" } })
  }

  // --- Il foglio dal tasto (Apple 284) ---------------------------------------------------------------------------

  private val windowPx get() = androidx.compose.ui.geometry.Size(canvasW * d, canvasH * d)
  private val largePx get() = fluidSheetLargeHeightPx(windowPx.height, statusDp * d, d)
  private val large get() = fluidSheetStops(listOf(FluidSheetDetent.Large), windowPx.height, statusDp * d, d)

  /**
   * Apertura: la molla dello zoom (min con quella dell'host, che la supera); chiusura: la molla d'uscita dell'host. Lo
   * scrim arriva e se ne va con le dissolvenze dell'host. La sorgente (l'icona della cartella) è consumata per tutto il
   * foglio: la capsula del gruppo resta, sotto lo scrim.
   */
  internal fun foglioDalTasto(closing: Boolean, returnInk: Boolean = true): GlassTrace {
    val open = TodaySpring(FluidSheetZoomSpring, 0f, 1f)
    val host = TodaySpring(FluidMotion.PopoverOpenY, 0f, 1f)
    val exit = TodaySpring(FluidMotion.PopoverExit, 1f, 0f)
    val fadeIn = TodaySpring(FluidMotion.fadeIn(FluidMotion.PopoverFadeInMillis), 0f, 1f)
    val fadeOut = TodaySpring(FluidMotion.fadeOut(FluidMotion.PopoverFadeOutMillis), 1f, 0f)
    val focus = TodaySpring(fluidMenuReturnFocusSpec(1f), 1f, 0f)
    val ink = TodaySpring(fluidMenuReturnInkSpec(1f), 1f, 0f)
    val sheetTop = canvasH - largePx / d
    val sheet = Rect(0f, sheetTop, canvasW, canvasH)
    val frames = (-3..48).map { k ->
      val ms = glassTraceTimeMs(k, fps)
      val nanos = glassTraceTimeNanos(k.coerceAtLeast(0), fps)
      val travel: Float
      val presence: Float
      if (!closing) {
        travel = if (k < 0) -1f else min(open.value(nanos), host.value(nanos))
        presence = if (k < 0) 0f else fadeIn.value(nanos)
      } else {
        // A molla ferma l'host toglie il foglio: nello stesso fotogramma la sorgente rinasce.
        travel = when {
          k < 0 -> 1f
          exit.finished(nanos) -> -1f
          else -> exit.value(nanos)
        }
        presence = if (k < 0) 1f else fadeOut.value(nanos)
      }
      val elapsed = if (closing) 10_000f else ms.toFloat()
      val returning = closing && returnInk && k >= 0
      GlassTraceFrame(ms, sheetFrame(travel, presence, elapsed, sheet,
        focus = if (returning) focus.value(nanos) else 1f,
        alpha = if (returning) ink.value(nanos) else 1f))
    }
    return GlassTrace(
      if (closing) "foglio-dal-tasto-chiude" else "foglio-dal-tasto", phase, d, "light", canvasW, canvasH, frames, fps,
      sheetHead(closing),
    )
  }

  /** Un fotogramma del foglio: la pagina di Mail, la barra, lo scrim, il vetro con la sagoma dello zoom, velo e contenuto. */
  private fun sheetFrame(travel: Float, presence: Float, elapsedMs: Float, sheet: Rect,
    focus: Float, alpha: Float,
  ): List<Map<String, Any?>> {
    val out = ArrayList<Map<String, Any?>>()
    // La barra in basso: il gruppo (la sorgente) e il tasto «scrivi», capsule di vetro schematiche.
    out += shapeOf("gruppo", Group, Group.height / 2f, Color.White.copy(alpha = 0.22f))
    out += shapeOf("scrivi", Compose, Compose.height / 2f, Color.White.copy(alpha = 0.22f))
    if (presence > 0.004f) out += scrim(presence)
    if (travel < 0f) return out
    val g = if (travel >= 1f) sheet else fluidSheetZoomBounds(Group, sheet, travel)
    val radii = sheetRadii(g)
    val recipe = glassRecipe(
      role = GlassRole.Modal, widthDp = g.width, heightDp = g.height, darkSide = false, optics = GlassDefaults.panelOptics(),
    )
    val shape = linkedMapOf<String, Any?>(
      "name" to "foglio", "x" to round3(g.left), "y" to round3(g.top), "w" to round3(g.width), "h" to round3(g.height),
      "radius" to radii,
    )
    out += liquidElement(shape, recipe, theme = null, note = "glassRecipe: ruolo panel, ${round3(g.width)} x ${round3(g.height)} dp")
      .also { completeRestBody(it) }
    // Il velo dello zoom: cresce col viaggio fino a quello del foglio al suo detent (pieno per `Large`).
    val veil = fluidSheetZoomVeil(travel.coerceAtMost(1f), fluidSheetLargeness(largePx, large, largePx, FluidSheetOpaqueBand))
    out += linkedMapOf(
      "kind" to "shape", "name" to "velo",
      "x" to round3(g.left), "y" to round3(g.top), "w" to round3(g.width), "h" to round3(g.height), "radius" to radii,
      "color" to glassColor(surface.copy(alpha = veil)),
    )
    // Il contenuto alla misura d'arrivo, in cima al vetro e al centro, ritagliato dal vetro e sfocato.
    val blur = fluidSheetZoomContentBlurDp(elapsedMs, travel.coerceAtMost(1f), focus)
    val x0 = (g.width - sheet.width) / 2f
    val items = ArrayList<Map<String, Any?>>()
    fun text(text: String, x: Float, y: Float, size: Float, color: Color, weight: Int? = null) {
      items += linkedMapOf<String, Any?>(
        "kind" to "text", "text" to text, "x" to round3(x0 + x), "y" to round3(y), "sizeDp" to size, "color" to glassColor(color),
      ).also { if (weight != null) it["weight"] = weight }
    }
    text("Sposta il messaggio in una cartella", 96f, 40f, 13f, secondary)
    text("iCloud", 178f, 72f, 17f, ink, 600)
    Mailboxes.forEachIndexed { i, box -> text(box, 56f, 150f + i * 44f, 17f, ink) }
    out += linkedMapOf(
      "kind" to "shape", "name" to "contenuto",
      "x" to round3(g.left), "y" to round3(g.top), "w" to round3(g.width), "h" to round3(g.height), "radius" to radii,
      "color" to "#00000000",
      "content" to listOf(
        linkedMapOf<String, Any?>(
          "name" to "righe", "items" to items, "blurDp" to round3(blur),
          "clip" to linkedMapOf("x" to 0, "y" to 0, "w" to round3(g.width), "h" to round3(g.height), "radius" to radii[0]),
        ).apply { if (alpha < 1f) put("alpha", round3(alpha)) },
      ),
      "contentClip" to "none",
    )
    return out
  }

  /**
   * I raggi del foglio sulla sagoma di adesso: in cima `FluidRadius.Sheet`, in fondo quelli dello schermo meno il rientro
   * (concentrici), ridotti in proporzione quando non ci stanno, come fa `CornerBasedShape` sulla capsula d'avvio.
   */
  private fun sheetRadii(g: Rect): List<Double> {
    var top = FluidRadius.Sheet.value
    var bottom = screenCornerDp - 5f
    val side = min(g.width, g.height)
    if (top + bottom > side) {
      val k = side / (top + bottom)
      top *= k
      bottom *= k
    }
    return listOf(round3(top), round3(top), round3(bottom), round3(bottom))
  }

  private fun sheetHead(closing: Boolean): Map<String, Any?> = linkedMapOf(
    "_" to "F5B.3: il foglio che ${if (closing) "rientra nel" else "nasce dal"} tasto della barra. Sagoma, velo e sfocatura " +
      "dalle funzioni del foglio (FluidGlassSheet.kt), molle dello zoom e dell'host; la scena di Apple ricostruita " +
      "(F5b3ZoomTraceTest.kt).",
    "palette" to "engine",
    "corner" to "engine",
    "background" to linkedMapOf("kind" to "color", "color" to "#FFFFFF"),
    "page" to mailPage(),
    "probes" to listOf(
      linkedMapOf("name" to "lato-sinistro", "kind" to "edge", "line" to "h:780:0:402", "which" to "first",
        "label" to "il lato sinistro del vetro all'altezza della barra"),
    ),
    "apple" to listOf(
      if (closing) linkedMapOf(
        "name" to "popover-ipad",
        "video" to "artifacts/liquid-glass/ricerca/r7/video/wwdc2025-284_hd.mp4",
        "t0Frame" to 25230,
        "crop" to listOf(600, 480, 560, 420),
        "note" to "Classe B, popover UIKit. Riferimento affine per il fuoco e l'inchiostro nel rientro al tasto. " +
          "La scena Mail del foglio mostra solo l'apertura.",
      ) else linkedMapOf(
        "name" to "telefono",
        "video" to "artifacts/liquid-glass/ricerca/r7/video/wwdc2025-284_hd.mp4",
        "t0Frame" to 25408,
        "crop" to listOf(1270, 60, 460, 960),
        "collage" to "artifacts/liquid-glass/sessioni/f5b-3/rif/apple_foglio_dal_tasto_284_f25406-25420.jpg",
        "note" to "classe B (interfaccia vera, UIKit): il vetro nasce dal gruppo della barra, contenuto sfocato dentro, " +
          "pagina scurita; misure in sessioni/f5b-3/strumenti/misura_284.py.",
      ),
    ),
  )

  /** La pagina di Mail della clip: il messaggio bianco in alto, la foto scura sotto. */
  private fun mailPage(): List<Map<String, Any?>> = listOf(
    linkedMapOf("kind" to "text", "text" to "Sanaa Squalli", "x" to 78, "y" to 140, "sizeDp" to 15, "color" to glassColor(ink)),
    linkedMapOf("kind" to "text", "text" to "San Francisco", "x" to 24, "y" to 210, "sizeDp" to 20, "color" to glassColor(ink)),
    linkedMapOf("kind" to "text", "text" to "Hey,", "x" to 24, "y" to 262, "sizeDp" to 15, "color" to glassColor(ink)),
    linkedMapOf("kind" to "text", "text" to "I hope you're doing great! I just visited", "x" to 24, "y" to 310, "sizeDp" to 15, "color" to glassColor(ink)),
    linkedMapOf("kind" to "text", "text" to "the conservatory of flowers in San Francisco", "x" to 24, "y" to 334, "sizeDp" to 15, "color" to glassColor(ink)),
    linkedMapOf(
      "kind" to "background", "x" to 0, "y" to 470, "w" to 402, "h" to 404, "radius" to 0,
      "background" to linkedMapOf(
        "kind" to "synthetic", "scaleDp" to 40, "detail" to 0.6,
        "palette" to listOf("#1E2A1A", "#2F4A2A", "#465E3A", "#8FA08C", "#C9D3C4", "#20301E"),
      ),
    ),
  )

  /** Lo scrim dei fogli di oggi (`FluidPresentationScrim`): un nero senza sfocatura, a [presence] del velo intero. */
  private fun scrim(presence: Float): Map<String, Any?> =
    todaySheetVeil(canvasW, canvasH, fluidSheetDimColor(false).alpha, presence)

  // --- Lo zoom fra pagine, dalla macchina vera -----------------------------------------------------------------------

  /** La griglia, la card sorgente (riga 2, colonna 1) e la pagina d'arrivo; [scenario] dice cosa fa il dito o la pila. */
  private fun zoomPagine(scenario: String): GlassTrace {
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
    val source = FluidTransitionSource("card", FluidGlassNamespace())
    val state = FluidPageHostState().also {
      it.scope = scope
      it.density = d
      it.width = canvasW * d
      it.height = canvasH * d
      it.cardRadii = FluidPageCorners(screenCornerDp * d, screenCornerDp * d, screenCornerDp * d, screenCornerDp * d)
      it.transitionOf = { page -> if (page == "D") FluidPageTransition.Expand else FluidPageTransition.Slide }
      it.sourceOf = { page -> if (page == "D") source else null }
      it.resolveSource = {
        FluidTransitionSourceGeometry(SourceCard.scaled(d), ContinuousCornerShape(FluidRadius.Card))
      }
      it.onBack = { it.onStack(listOf("G")) }
      it.zoomCrossfade = scenario == "zoom-torna-dissolvenza"
      it.onStack(listOf("G"))
    }
    var nanos = 0L
    fun tick() {
      clock.sendFrame(nanos)
      nanos += 1_000_000_000L / fps
    }
    // Le pagine che partono aperte arrivano a riposo prima della traccia.
    if (scenario != "zoom-apre") {
      state.onStack(listOf("G", "D"))
      state.pagePlaced("D")
      repeat(120) { tick() }
    }
    var token: Int? = null
    var fingerPx = 0f
    val downDp = Offset(14f, 600f)
    val frames = ArrayList<GlassTraceFrame>()
    // La molla dello zoom misurata su Apple a tempo vero (critica, 0,355 s): l'apertura si ferma verso 600 ms, il ritorno
    // col tasto arriva al dp verso 470 ms; dopo, qualche fotogramma a riposo.
    val lastFrame = when (scenario) {
      "zoom-apre" -> 48
      "zoom-torna", "zoom-torna-dissolvenza" -> 48
      "zoom-dito" -> 66
      "zoom-dito-interrotto" -> 90
      else -> 48
    }
    for (k in -3..lastFrame) {
      val ms = glassTraceTimeMs(k, fps)
      when (scenario) {
        "zoom-apre" -> if (k == 0) {
          state.onStack(listOf("G", "D"))
          state.pagePlaced("D")
        }
        "zoom-torna", "zoom-torna-dissolvenza" -> if (k == 0) state.onStack(listOf("G"))
        "zoom-dito" -> {
          // Il dito dal bordo: 220 dp in 250 ms (15 fotogrammi), poi lascia con la sua velocità.
          if (k == 0) {
            state.recordDown(downDp.scaled(d), IntSize((canvasW * d).toInt(), (canvasH * d).toInt()))
            token = state.beginDrag(1)
          }
          if (k in 1..15) {
            fingerPx = 220f * d * k / 15f
            state.dragTo(token!!, fingerPx)
          }
          if (k == 16) state.release(token!!, 220f * d / 0.25f)
        }
        "zoom-dito-interrotto" -> {
          // 120 dp in 200 ms e lascia piano (torna); a 350 ms il dito la riprende, torna indietro di 20 dp (si riapre) e
          // poi va oltre la soglia: 240 dp in 250 ms, e lascia (conferma).
          if (k == 0) {
            state.recordDown(downDp.scaled(d), IntSize((canvasW * d).toInt(), (canvasH * d).toInt()))
            token = state.beginDrag(1)
          }
          if (k in 1..12) state.dragTo(token!!, 120f * d * k / 12f)
          if (k == 13) state.release(token!!, 0f)
          if (k == 21) {
            val at = state.travelPx
            state.recordDown(Offset(downDp.x * d + at, downDp.y * d), IntSize((canvasW * d).toInt(), (canvasH * d).toInt()))
            assertTrue("il ritorno si riprende", state.canGrab())
            token = state.beginDrag(state.direction)
            fingerPx = at
          }
          if (k in 22..27) state.dragTo(token!!, fingerPx - 20f * d * (k - 21) / 6f)
          if (k in 28..42) state.dragTo(token!!, fingerPx - 20f * d + 240f * d * (k - 27) / 15f)
          if (k == 43) state.release(token!!, 200f * d / 0.25f)
        }
      }
      if (k >= 0) tick()
      frames += GlassTraceFrame(ms, pageFrame(state, source))
    }
    scope.cancel()
    source.release()
    return GlassTrace(scenario, phase, d, "light", canvasW, canvasH, frames, fps, pageHead(scenario))
  }

  /** Un fotogramma: la griglia (la card sorgente solo se non è consumata), e la pagina dentro la sua sagoma. */
  private fun pageFrame(state: FluidPageHostState, source: FluidTransitionSource): List<Map<String, Any?>> {
    val out = ArrayList<Map<String, Any?>>()
    val upperShown = state.isMoving || state.upper == "D"
    val zoom = state.zoomShape()
    // La griglia si vede mentre la pagina si muove o se la pagina non c'è.
    if (state.upper != "D" || state.isMoving) {
      for ((i, card) in Cards.withIndex()) {
        if (i == SourceIndex && source.isHidden) continue
        out += cardElements(if (i == SourceIndex) "card-sorgente" else "card-$i", card, Palette[i])
      }
    }
    if (!upperShown) return out
    val shape: FluidZoomShape = zoom?.let { FluidZoomShape(it.bounds.scaled(1f / d), it.corners.scaled(1f / d)) }
      ?: FluidZoomShape(Rect(0f, 0f, canvasW, canvasH), FluidPageCorners.Zero)
    val s = state.pageScale(FluidPageHostState.PageRole.Upper)
    val tx = state.pageOffsetX(FluidPageHostState.PageRole.Upper) / d
    val ty = state.pageOffsetY(FluidPageHostState.PageRole.Upper) / d
    fun map(p: Offset) = Offset(canvasW / 2f + s * (p.x - canvasW / 2f) + tx, canvasH / 2f + s * (p.y - canvasH / 2f) + ty)
    fun mapRect(r: Rect): Rect = Rect(map(r.topLeft), map(r.bottomRight))
    val g = shape.bounds
    val k = shape.corners
    // La variante con la dissolvenza (FluidPageHost.zoomCrossfade): il fondo resta pieno, il contenuto della pagina
    // sbiadisce e sopra compare la fotografia della card, larga quanto la sagoma e in alto.
    val fade = state.zoomCrossfadeAlpha()
    val pageAlpha = 1f - fade
    fun faded(color: Color) = color.copy(alpha = color.alpha * pageAlpha)
    out += linkedMapOf(
      "kind" to "shape", "name" to "pagina",
      "x" to round3(g.left), "y" to round3(g.top), "w" to round3(g.width), "h" to round3(g.height),
      "radius" to listOf(round3(k.topLeft), round3(k.topRight), round3(k.bottomRight), round3(k.bottomLeft)),
      "color" to glassColor(surface),
    )
    // La foto in cima e il gruppo di righe, in scala e ritagliati dalla sagoma (rettangoli: l'intersezione).
    for ((name, rect, color) in listOf(
      Triple("foto", DetailPhoto, Palette[SourceIndex]),
      Triple("righe", DetailRows, grouped),
    )) {
      val r = mapRect(rect).intersect(g)
      if (r.width > 0.05f && r.height > 0.05f && pageAlpha > 0.004f) {
        out += linkedMapOf(
          "kind" to "shape", "name" to name,
          "x" to round3(r.left), "y" to round3(r.top), "w" to round3(r.width), "h" to round3(r.height),
          "radius" to round3(FluidRadius.Card.value * s * 0.82f), "color" to glassColor(faded(color)),
        )
      }
    }
    // Titolo e righe di testo: uno strato grande quanto la pagina, in scala attorno al suo centro, ritagliato.
    val center = map(Offset(canvasW / 2f, canvasH / 2f))
    val layerX = center.x - canvasW / 2f - g.left
    val layerY = center.y - canvasH / 2f - g.top
    val items = listOf(
      text("Faro", 20f, 104f, 34f, faded(ink), 700),
      text("Capo Spartivento", 20f, 134f, 15f, faded(secondary)),
      text("Torna col dito", 32f, 448f, 17f, faded(ink)),
      text("Lascia a metà", 32f, 512f, 17f, faded(ink)),
      text("Tasto indietro", 32f, 576f, 17f, faded(ink)),
    )
    out += linkedMapOf(
      "kind" to "shape", "name" to "testo-pagina",
      "x" to round3(g.left), "y" to round3(g.top), "w" to round3(g.width), "h" to round3(g.height), "radius" to 0,
      "color" to "#00000000",
      "content" to listOf(
        linkedMapOf<String, Any?>(
          "name" to "testo", "x" to round3(layerX), "y" to round3(layerY), "w" to canvasW, "h" to canvasH,
          "items" to items, "scale" to round3(s),
          "clip" to linkedMapOf("x" to 0, "y" to 0, "w" to round3(g.width), "h" to round3(g.height), "radius" to round3(k.topLeft)),
        ),
      ),
      "contentClip" to "none",
    )
    if (fade > 0f) {
      val snap = FluidZoom.snapshotRect(g, SourceCard.size)
      out += cardElements("sorgente-in-sagoma", snap, Palette[SourceIndex], scale = snap.width / SourceCard.width, alpha = fade)
    }
    return out
  }

  /** Una card della griglia; [scale] e [alpha] per la sua fotografia dentro la sagoma (la variante con la dissolvenza). */
  private fun cardElements(
    name: String,
    card: Rect,
    color: Color,
    scale: Float = 1f,
    alpha: Float = 1f,
  ): List<Map<String, Any?>> {
    val radius = round3(FluidRadius.Card.value * 0.82f * scale)
    val photo = Rect(card.left, card.top, card.right, card.top + card.width / 1.25f)
    fun a(c: Color) = c.copy(alpha = c.alpha * alpha)
    return listOf(
      linkedMapOf(
        "kind" to "shape", "name" to name,
        "x" to round3(card.left), "y" to round3(card.top), "w" to round3(card.width), "h" to round3(card.height),
        "radius" to radius, "color" to glassColor(a(surface)),
        "content" to listOf(
          linkedMapOf<String, Any?>(
            "name" to "scritte",
            "items" to listOf(text("Luogo", 12f * scale, photo.height + 26f * scale, 15f * scale, a(ink), 600)),
          ),
        ),
      ),
      linkedMapOf(
        "kind" to "shape", "name" to "$name-foto",
        "x" to round3(photo.left), "y" to round3(photo.top), "w" to round3(photo.width), "h" to round3(photo.height),
        "radius" to listOf(radius, radius, 0.0, 0.0), "color" to glassColor(a(color)),
      ),
    )
  }

  private fun pageHead(scenario: String): Map<String, Any?> = linkedMapOf(
    "_" to "F5B.3: $scenario. La sagoma e la scala della pagina dalla macchina delle pagine (FluidPageHostState, molle e " +
      "dito veri), la scena ricostruita (F5b3ZoomTraceTest.kt). Senza ombra della carta e senza la barra.",
    "palette" to "engine",
    "corner" to "engine",
    "background" to linkedMapOf("kind" to "color", "color" to glassColor(grouped)),
    "page" to listOf(
      linkedMapOf("kind" to "text", "text" to "Zoom", "x" to 20, "y" to 120, "sizeDp" to 34, "color" to glassColor(ink)),
      linkedMapOf("kind" to "text", "text" to "Luoghi", "x" to 20, "y" to 158, "sizeDp" to 13, "color" to glassColor(secondary)),
    ),
    "probes" to listOf(
      linkedMapOf("name" to "bordo-sinistro", "kind" to "edge", "line" to "h:700:0:402", "which" to "first",
        "label" to "il lato sinistro della pagina, sotto la card sorgente"),
    ),
  ).also { head -> appleZoom(scenario)?.let { head["apple"] = listOf(it) } }

  /**
   * Lo zoom di Apple (WWDC24 10145, l'iPad della dimostrazione, iOS 18): l'apertura da una cella toccata (0:31,7) e il
   * ritorno col dito nella cella (1:45,9). Il ritorno col tasto non c'è: ha la stessa molla dell'apertura. **Il video è a
   * metà velocità** (lo dice Apple nella trascrizione): `speed` = 2 lo riporta a tempo vero, un fotogramma ogni 16,7 ms.
   */
  private fun appleZoom(scenario: String): Map<String, Any?>? {
    val (t0, note) = when (scenario) {
      "zoom-apre" -> 958 to "classe B (interfaccia vera, UIKit, iOS 18), video a metà velocità riportato a tempo vero: " +
        "la cella toccata diventa pagina; da ferma, molla critica di risposta 0,355 s partita a f957,6 " +
        "(sessioni/corr-zoom/strumenti/misura_10145_tempo_vero.py)."
      "zoom-dito" -> 3179 to "classe B, a tempo vero: la pagina presa dal bordo, trascinata e lasciata, rientra nella sua " +
        "cella; dopo il rilascio la stessa molla, partita con la velocità del dito. Il dito di Apple (≈120 ms) non è " +
        "quello della traccia (250 ms)."
      "zoom-torna", "zoom-torna-dissolvenza" -> 3180 to "classe B, a tempo vero: il ritorno col tasto non c'è nella " +
        "sessione; accanto, il ritorno col dito (1:46), allineato sulla strada dopo il rilascio (f3186-f3190: Apple 0,49 " +
        "-> 0,79, la nostra molla da f3180 entro 0,04). Prima del rilascio il dito di Apple porta la pagina a destra."
      else -> return null
    }
    return linkedMapOf(
      "name" to "ipad",
      "video" to "artifacts/liquid-glass/sessioni/f5b-3/rif/wwdc2024-10145/wwdc2024-10145_hd_0-8min.mp4",
      "t0Frame" to t0,
      "crop" to if (scenario == "zoom-apre") listOf(363, 123, 1193, 822) else listOf(493, 214, 934, 642),
      "speed" to 2,
      "note" to note,
    )
  }

  private fun text(text: String, x: Float, y: Float, size: Float, color: Color, weight: Int? = null): Map<String, Any?> =
    linkedMapOf<String, Any?>(
      "kind" to "text", "text" to text, "x" to round3(x), "y" to round3(y), "sizeDp" to size, "color" to glassColor(color),
    ).also { if (weight != null) it["weight"] = weight }

  private fun shapeOf(name: String, r: Rect, radius: Float, color: Color): Map<String, Any?> = linkedMapOf(
    "kind" to "shape", "name" to name,
    "x" to round3(r.left), "y" to round3(r.top), "w" to round3(r.width), "h" to round3(r.height),
    "radius" to round3(radius), "color" to glassColor(color),
  )

  private fun Rect.scaled(k: Float) = Rect(left * k, top * k, right * k, bottom * k)
  private fun Offset.scaled(k: Float) = Offset(x * k, y * k)
  private fun FluidPageCorners.scaled(k: Float) = FluidPageCorners(topLeft * k, topRight * k, bottomRight * k, bottomLeft * k)

  private companion object {
    /** Il gruppo della barra in basso di Mail (cestino, cartella, rispondi), in pt della clip: 35–210 × 796–841. */
    val Group = Rect(35f, 796f, 210f, 841f)
    val Compose = Rect(337f, 796f, 382f, 841f)
    val Mailboxes = listOf("Posta in arrivo", "Bozze", "Inviati", "Indesiderata", "Cestino", "Archivio")

    /** La griglia: due colonne di card da 179 × 195 dp a 12 dp l'una dall'altra; la sorgente è la prima della riga 2. */
    val Cards: List<Rect> = (0 until 6).map { i ->
      val col = i % 2
      val row = i / 2
      val left = 16f + col * (179f + 12f)
      val top = 180f + row * (195f + 12f)
      Rect(left, top, left + 179f, top + 195f)
    }
    const val SourceIndex = 2
    val SourceCard: Rect get() = Cards[SourceIndex]
    val Palette = listOf(
      Color(0xFF3A7BD5), Color(0xFF2F5D34), Color(0xFFE9A23B), Color(0xFF6DB3D6), Color(0xFF6B3E75), Color(0xFFF2994A),
    )

    /** La pagina d'arrivo: la foto sotto il titolo grande, poi il gruppo delle righe. */
    val DetailPhoto = Rect(16f, 160f, 386f, 400f)
    val DetailRows = Rect(16f, 420f, 386f, 612f)
  }
}
