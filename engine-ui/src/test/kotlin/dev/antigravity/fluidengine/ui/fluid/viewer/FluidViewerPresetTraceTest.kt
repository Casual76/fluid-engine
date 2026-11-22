package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarBar
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarItem
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarModel
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.SearchPresetTraces
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarMinimizeLook
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.trace.*
import dev.antigravity.fluidengine.ui.page.FluidPageDismiss
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.FluidZoomShape
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Test
import dev.antigravity.fluidengine.ui.page.FluidZoom as PageZoom

/**
 * F6C.2, il banco del movimento a 120 Hz.
 *
 * - `immagine-zoom`: la miniatura si apre (la macchina delle pagine vera, zoom di F5B.3 con la molla critica a 0,355 s),
 *   un doppio tocco porta a 2,5× attorno al dito, un secondo torna a 1×, il dito trascina giù e la foto torna nella
 *   miniatura. Lo zoom è quello di [FluidZoomState.zoomTo]: la stessa molla ([FluidMotion.snappy]) e gli stessi limiti
 *   ([dev.antigravity.fluidengine.ui.fluid.FluidZoom.clampPan], [dev.antigravity.fluidengine.ui.fluid.FluidZoom.zoomAbout]).
 * - `testo-trova`: il file di testo, «Trova» che nasce in fondo e sale sopra la tastiera finta (la macchina di F6B.6,
 *   [SearchPresetTraces]), «lago» con 1/3, l'invio, la freccia su e la chiusura. Le occorrenze e la corrente sono quelle
 *   di [TextFind]; lo scorrimento verso la corrente porta la sua riga a un terzo dell'altezza, con la molla di serie di
 *   Compose (fiducia bassa: `animateScrollToItem` non dice la sua). Il testo in monospazio lo impagina
 *   `strumenti/pagina_testo.py` della sessione, dai numeri scritti in `testo`.
 *
 * `GLASS_F6C2_DIR` esporta le tracce; `GLASS_F6C2_PLATE` è la foto (di serie quella di F6C.1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidViewerPresetTraceTest {
  private val width = 320f
  private val height = 568f
  private val container = Size(width, height)
  private val fit = FluidZoom.fitSize(container, 1.5f)
  private val thumb = Rect(20f, 121f, 149f, 207f)
  private val destination = Rect(0f, (height - fit.height) / 2f, width, (height + fit.height) / 2f)
  private val full = FluidZoomShape(Rect(0f, 0f, width, height))
  private val fps = 120
  private val plate = System.getenv("GLASS_F6C2_PLATE") ?: "artifacts/liquid-glass/sessioni/f6c-1/tracce/dune.png"

  @Test fun exportImageZoomAndTextFindInBothAppearances() {
    val out = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6C2_DIR"))
    for (dark in listOf(false, true)) {
      for (trace in listOf(imageTrace(dark), textTrace(dark))) {
        assertEquals(fps, trace.fps)
        out?.let { trace.write(File(it, "${trace.name}/trace.json")) }
      }
    }
  }

  // --- immagine-zoom -----------------------------------------------------------------------------------------------

  /** Un doppio tocco: da [from] e [fromPan] a [to] attorno a [focal], partito a [startMs]. */
  private class ZoomLeg(val from: Float, val fromPan: Offset, val to: Float, val focal: Offset, val startMs: Double)

  private val zoomSpring = TargetBasedAnimation(FluidMotion.snappy<Float>(), Float.VectorConverter, 0f, 1f)

  /** La scala e lo spostamento di [leg] a [ms]: i conti di FluidZoomState.zoomTo, fotogramma per fotogramma. */
  private fun zoomAt(leg: ZoomLeg, ms: Double): Pair<Float, Offset> {
    val p = if (ms <= leg.startMs) 0f else zoomSpring.getValueFromNanos(((ms - leg.startMs) * 1e6).toLong())
    val s = leg.from + (leg.to - leg.from) * p
    val pan = FluidZoom.clampPan(FluidZoom.zoomAbout(leg.from, leg.fromPan, s, leg.focal), container, fit, s)
    return s to pan
  }

  /** Il riquadro della foto adattata, a scala [scale] e spostata di [pan] dal centro. */
  private fun zoomed(scale: Float, pan: Offset): Rect {
    val w = fit.width * scale
    val h = fit.height * scale
    val cx = width / 2f + pan.x
    val cy = height / 2f + pan.y
    return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
  }

  @Test fun theImageZoomsAboutTheFingerStaysInsideItsLimitsAndClosesIntoTheThumbnail() {
    val trace = imageTrace(false)
    val photos = trace.frames.map { frame -> frame.elements.single { it["name"] == "foto" } }
    val scales = trace.frames.map { frame -> (frame.elements.single { it["name"] == "misure" }["scala"] as Number).toFloat() }
    assertEquals(2.5f, scales.max(), 0.01f)
    // Nessun rimbalzo oltre 2,5× (la molla dello zoom è critica) e di nuovo 1× prima del trascinamento.
    assertTrue(scales.all { it <= 2.5f + 1e-3f })
    assertEquals(1f, scales[DragFrame - 1], 1e-3f)
    // Ingrandita, la foto non lascia un vuoto di lato: copre la larghezza; in verticale resta centrata.
    trace.frames.forEachIndexed { i, frame ->
      val s = scales[i]
      if (s > 1.4f) {
        val box = photos[i]
        val left = (box["x"] as Number).toFloat()
        val w = (box["w"] as Number).toFloat()
        assertTrue("fotogramma $i: vuoto a sinistra", left <= 1e-3f)
        assertTrue("fotogramma $i: vuoto a destra", left + w >= width - 1e-3f)
      }
    }
    // L'ultimo fotogramma è la miniatura.
    val last = photos.last()
    assertEquals(thumb.left, (last["x"] as Number).toFloat(), .5f)
    assertEquals(thumb.width, (last["w"] as Number).toFloat(), .5f)
  }

  private fun imageTrace(dark: Boolean): GlassTrace {
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
    val source = FluidTransitionSource("dune", FluidGlassNamespace())
    val state = FluidPageHostState().also {
      it.scope = scope
      it.width = width
      it.height = height
      it.density = 1f
      it.transitionOf = { FluidPageTransition.Expand }
      it.dismissOf = { FluidPageDismiss.Down }
      it.verticalDismissRelease["file"] = { travel, speed -> viewerDismissRelease(travel, speed, 1f) }
      it.sourceOf = { source }
      it.resolveSource = { FluidTransitionSourceGeometry(thumb, ContinuousCornerShape(FluidRadius.Card)) }
      it.onBack = { it.onStack(listOf("album")) }
      it.onStack(listOf("album"))
    }
    var ticks = 0
    fun tick() = clock.sendFrame(glassTraceTimeNanos(ticks++, fps))
    val tapIn = Offset(230f, 250f)
    val tapOut = Offset(200f, 300f)
    val center = Offset(width / 2f, height / 2f)
    val zoomIn = ZoomLeg(1f, Offset.Zero, FluidZoom.DoubleTapScale, tapIn - center, glassTraceTimeMs(ZoomInFrame, fps))
    var zoomOut: ZoomLeg? = null
    val base = mutableMapOf<String, Any?>()
    val frames = mutableListOf<GlassTraceFrame>()
    var token: Int? = null
    try {
      for (n in 0..LastImageFrame) {
        if (n == 9) {
          state.onStack(listOf("album", "file"))
          state.pagePlaced("file")
        }
        if (n == DragFrame) {
          state.recordDown(Offset(151f, 300f), IntSize(width.toInt(), height.toInt()))
          token = requireNotNull(state.beginVertical(null))
        }
        if (n in DragFrame + 1 until ReleaseFrame) state.moveVertical(token!!, 8f, 960f)
        if (n == ReleaseFrame) state.releaseVertical(token!!, 960f)
        tick()
        val ms = glassTraceTimeMs(n, fps)
        if (n == ZoomOutFrame) {
          val (s, pan) = zoomAt(zoomIn, ms)
          zoomOut = ZoomLeg(s, pan, FluidZoom.MinScale, tapOut - center, ms)
        }
        val (scale, pan) = zoomOut?.let { zoomAt(it, ms) } ?: zoomAt(zoomIn, ms)
        val p = state.fractionToward { it == "file" }
        val shape = state.zoomShape() ?: if (p > .5f) full else FluidZoomShape(thumb)
        val target = zoomed(scale, pan)
        val photo = PageZoom.sharedBounds(thumb, target, p, shape, FluidZoomShape(thumb), full)
        val elements = mutableListOf<Map<String, Any?>>()
        val page = if (dark) "#161619" else "#F6F5F2"
        val ink = if (dark) "#FFFFFF" else "#242328"
        elements += rectangle("album", full.bounds, page)
        elements += label("titolo-album", "Foto", 20f, 84f, 32f, ink)
        elements += label("nome-file", "Dune", 20f, 230f, 16f, ink)
        if (p > 0f) elements += rectangle("visore", shape.bounds, "#000000", shape.corners.topLeft)
        elements += rectangle("foto", photo, "#00000000").plus("content" to listOf(mapOf(
          "name" to "foto", "w" to photo.width, "h" to photo.height, "items" to listOf(mapOf(
            "kind" to "background", "x" to 0, "y" to 0, "w" to photo.width, "h" to photo.height,
            "radius" to (1f - p) * FluidRadius.Card.value,
            "background" to mapOf("kind" to "image", "path" to plate),
          )),
        )))
        val look = fluidToolbarMinimizeLook(0f, 48f, false, true)
        val faces = PageZoom.faces(p)
        val alpha = look.alpha * faces.pageAlpha
        if (alpha > .001f) {
          val y = 22f + look.offsetPx + faces.riseDp
          val bottom = height - 66f - look.offsetPx
          elements += glass(base, "indietro", Rect(12f, y, 60f, y + 48f), true, listOf("arrow_back_ios_new"), alpha, look.blur)
          elements += label("conteggio", "1 di 3", 135f, y + 30f, 17f, "#FFFFFF", alpha, look.blur)
          elements += glass(base, "strumenti", Rect(112f, bottom, 208f, bottom + 48f), true, listOf("info", "edit"), alpha, look.blur)
        }
        elements += mapOf("kind" to "shape", "name" to "misure", "x" to 0, "y" to 0, "w" to 1, "h" to 1,
          "color" to "#00000000", "radius" to 0, "scala" to scale, "panX" to pan.x, "panY" to pan.y, "apertura" to p)
        frames += GlassTraceFrame(ms, elements)
      }
      assertFalse(state.upper == "file")
      val name = "immagine-zoom" + if (dark) "-scuro" else ""
      val events = listOf(9 to "apre dalla miniatura", ZoomInFrame to "doppio tocco: 2,5×", ZoomOutFrame to "doppio tocco: 1×",
        DragFrame to "il dito tira giù", ReleaseFrame to "rilascio: torna nella miniatura")
        .map { (n, label) -> mapOf("t" to glassTraceTimeMs(n, fps), "kind" to "mark", "label" to label) }
      val probes = listOf("scala", "panX", "panY", "apertura").map { mapOf("name" to it, "kind" to "value", "path" to "misure.$it") }
      return GlassTrace(name, "f6c-2", 1f, if (dark) "dark" else "light", width, height, frames, fps,
        mapOf("background" to mapOf("kind" to "color", "color" to "#000000"), "base" to base, "events" to events, "probes" to probes,
          "_" to "Scena di banco. Pagine, zoom di F5B.3, dito di F6B.5a, molla e limiti di FluidZoomState dal codice Fluid."))
    } finally {
      scope.cancel()
      state.releaseSources()
    }
  }

  // --- testo-trova -------------------------------------------------------------------------------------------------

  private val lake: List<String> = buildList {
    val prose = listOf(
      "Appunti di viaggio, agosto.", "", "Siamo arrivati al lago di sera, col vento.", "Le barche erano ferme ai pontili.",
      "La strada scende tra i pini fino alla riva.", "Il paese ha una piazza e due alberghi.",
    )
    addAll(prose)
    for (i in prose.size until 60) add(
      when (i) {
        31 -> "Al mattino il LAGO era liscio come vetro."
        52 -> "Ultimo giorno: il làgo è grigio e calmo."
        else -> "Nota ${i + 1}: sentieri, rifugi, orari."
      },
    )
  }

  /** Le misure del testo nel banco: quelle del preset (bodySmall in monospazio), a occhio per il carattere del banco. */
  private object TextBench {
    const val SizeDp = 13f
    const val LineDp = 18f
    const val TopDp = 86f
    const val MarginDp = 20f
    const val HeightDp = 700f

    /** La tastiera finta di F6B.6 e la riga del campo sopra di lei: il margine in basso del preset con «trova» aperto. */
    const val ImeDp = 260f
    const val FieldRowDp = 48f
    const val GapDp = 10f
  }

  @Test fun findWalksTheResultsAndTheTextFollowsTheCurrent() {
    val trace = textTrace(false)
    val text = trace.frames.map { frame -> frame.elements.single { it["name"] == "testo" } }
    val currents = text.map { it["corrente"] as Int }
    // L'ordine delle correnti nel tempo, senza ripetizioni: 1/3, invio 2/3, invio 3/3, freccia su 2/3.
    val walk = currents.filter { it >= 0 }.fold(mutableListOf<Int>()) { acc, c -> if (acc.lastOrNull() != c) acc += c; acc }
    assertEquals(listOf(0, 1, 2, 1), walk)
    // Chiuso «trova», niente evidenziatori.
    assertTrue((text.last()["occorrenze"] as List<*>).isEmpty())
    // La riga corrente finisce a un terzo dell'altezza, salvo dove il testo non scorre oltre.
    val settledOnThird = text.last { it["corrente"] == 2 }
    val line = 52
    val scroll = (settledOnThird["scorrimentoDp"] as Number).toFloat()
    val y = TextBench.TopDp + line * TextBench.LineDp - scroll
    assertTrue("la riga 53 sta a $y dp, sotto la barra o sotto il campo",
      y >= TextBench.TopDp - 1f && y + TextBench.LineDp <= TextBench.HeightDp - TextBench.ImeDp - TextBench.FieldRowDp)
    // Lo scorrimento non torna mai sotto zero.
    assertTrue(text.all { (it["scorrimentoDp"] as Number).toFloat() >= 0f })
  }

  private fun textTrace(dark: Boolean): GlassTrace {
    val find = TextFind.of()
    fun spec(active: Boolean) = FluidSearchSpec.findInPage(find.query, {}, FluidSearchNavigation(find.current, find.total, {}, {}),
      active, {})
    val steps = mutableListOf(SearchPresetTraces.Step(-100.0, spec(false), "riposo"), SearchPresetTraces.Step(0.0, spec(true), "tocco su «Trova»"))
    find.onQueryChange("lago")
    find.replace(fluidTextMatches(lake, find.query))
    steps += SearchPresetTraces.Step(500.0, spec(true), "«lago»: 1/3")
    find.next()
    steps += SearchPresetTraces.Step(1_000.0, spec(true), "invio: 2/3")
    find.next()
    steps += SearchPresetTraces.Step(1_500.0, spec(true), "invio: 3/3")
    find.previous()
    steps += SearchPresetTraces.Step(2_000.0, spec(true), "freccia su: 2/3")
    steps += SearchPresetTraces.Step(2_500.0, spec(false), "chiude")
    val matches = find.matches
    val model = FluidToolbarModel(listOf(
      FluidToolbarItem("trova", placement = FluidToolbarPlacement.BottomBar, symbol = "search", label = "Trova"),
      FluidToolbarItem("acapo", placement = FluidToolbarPlacement.BottomBar, symbol = "text_format", label = "A capo"),
    ))
    val top = SearchPresetTraces.TopBar(FluidToolbarModel(listOf(
      FluidToolbarItem("indietro", placement = FluidToolbarPlacement.Leading, symbol = "arrow_back_ios_new", label = "Indietro"),
    )), "1 di 1")
    val name = "testo-trova" + if (dark) "-scuro" else ""
    val scene = SearchPresetTraces.Scene(name, "F6C.2: «Trova» nel preset testo, sopra la tastiera finta; il testo segue la corrente",
      dark, FluidToolbarBar.Bottom, model, steps, lastFrame = 336, heightDp = TextBench.HeightDp,
      ime = SearchPresetTraces.Ime(0.0, 2_500.0), top = top, text = true)
    val built = SearchPresetTraces.build(scene)
    // Lo scorrimento: a ogni cambio di corrente una molla verso la riga a un terzo dell'altezza.
    // Come contentPadding nel preset: con la tastiera su, la fine del testo si ferma sopra il campo.
    val bottom = TextBench.ImeDp + TextBench.FieldRowDp + TextBench.GapDp
    val maxScroll = (TextBench.TopDp + lake.size * TextBench.LineDp + bottom - TextBench.HeightDp).coerceAtLeast(0f)
    fun targetFor(current: Int?): Float = if (current == null) 0f else
      (matches[current].line * TextBench.LineDp - TextBench.HeightDp / 3f).coerceIn(0f, maxScroll)
    val timeline = steps.filter { it.spec.active && it.spec.query.isNotEmpty() }.map { it.at to it.spec.navigation!!.current }
    var from = 0f
    var legStart = Double.NEGATIVE_INFINITY
    var legTarget = 0f
    var leg: TargetBasedAnimation<Float, AnimationVector1D>? = null
    val frames = built.frames.map { frame ->
      val t = frame.tMs
      val currentStep = timeline.lastOrNull { it.first <= t + 1e-6 }
      val target = targetFor(currentStep?.second)
      if (currentStep != null && currentStep.first != legStart) {
        // Un cambio di corrente: la molla parte da dove è lo scorrimento ora.
        from = leg?.getValueFromNanos(((t - legStart) * 1e6).toLong()) ?: from
        legStart = currentStep.first
        legTarget = target
        leg = TargetBasedAnimation(spring<Float>(), Float.VectorConverter, from, legTarget)
      }
      val scroll = leg?.getValueFromNanos(((t - legStart) * 1e6).toLong().coerceAtLeast(0)) ?: 0f
      val active = steps.last { it.at <= t + 1e-6 }.spec.active
      val current = if (active && currentStep != null) currentStep.second else -1
      val shown = if (active && currentStep != null) matches.map { listOf(it.line, it.start, it.end) } else emptyList()
      val testo = linkedMapOf<String, Any?>("kind" to "shape", "name" to "testo", "x" to 0, "y" to 0, "w" to 1, "h" to 1,
        "color" to "#00000000", "radius" to 0, "scorrimentoDp" to scroll, "corrente" to current, "occorrenze" to shown)
      GlassTraceFrame(t, frame.elements + testo)
    }
    val head = LinkedHashMap(built.head)
    head["_testo"] = linkedMapOf("righe" to lake, "sizeDp" to TextBench.SizeDp, "rigaDp" to TextBench.LineDp,
      "altoDp" to TextBench.TopDp, "margineDp" to TextBench.MarginDp, "cifre" to 3)
    head["_"] = "F6C.2. Campo, tastiera e barra dalla macchina di F6B.6; occorrenze e corrente da TextFind (preset testo); " +
      "scorrimento con la molla di serie di Compose verso un terzo dell'altezza (fiducia bassa). Pagina: pagina_testo.py."
    @Suppress("UNCHECKED_CAST")
    val probes = (head["probes"] as? List<Map<String, Any?>>).orEmpty() +
      mapOf("name" to "scorrimentoDp", "kind" to "value", "path" to "testo.scorrimentoDp")
    head["probes"] = probes
    return GlassTrace(built.name, "f6c-2", built.density, built.theme, built.widthDp, built.heightDp, frames, built.fps, head)
  }

  // --- Gli elementi della scena (gli stessi di FluidViewerTraceTest, F6C.1) -------------------------------------------

  private fun rectangle(name: String, box: Rect, color: String, radius: Float = 0f) = mapOf<String, Any?>(
    "kind" to "shape", "name" to name, "x" to box.left, "y" to box.top, "w" to box.width, "h" to box.height,
    "radius" to radius, "color" to color,
  )

  private fun label(name: String, value: String, x: Float, baseline: Float, size: Float, color: String,
    alpha: Float = 1f, blur: Float = 0f): Map<String, Any?> {
    val box = Rect(x, baseline - size * 1.5f, width, baseline + size)
    return rectangle(name, box, "#00000000").plus("content" to listOf(mapOf("name" to name, "w" to box.width, "h" to box.height,
      "alpha" to alpha, "blurSigmaDp" to glassContentSigmaPx(blur, FluidMetrics.BarIconSize.value),
      "items" to listOf(mapOf("kind" to "text", "text" to value, "x" to 0, "y" to size * 1.5f,
        "sizeDp" to size, "color" to color, "weight" to 600)))))
  }

  private fun glass(base: MutableMap<String, Any?>, name: String, box: Rect, dark: Boolean, icons: List<String>,
    alpha: Float, blur: Float): Map<String, Any?> {
    base.getOrPut(name) {
      val recipe = glassRecipe(GlassRole.Interactive, box.width, box.height, dark, optics = GlassDefaults.buttonOptics())
      liquidElement(mapOf("name" to name, "x" to 0, "y" to 0, "w" to box.width, "h" to box.height, "radius" to box.height / 2),
        recipe, null, "Ricetta Fluid").also { completeRestBody(it) }
    }
    val content = if (icons.isEmpty()) emptyList() else listOf(mapOf(
      "name" to name, "w" to box.width, "h" to box.height, "alpha" to alpha,
      "blurSigmaDp" to glassContentSigmaPx(blur, FluidMetrics.BarIconSize.value),
      "icons" to icons.map { "fluid:$it" }, "iconSizeDp" to FluidMetrics.BarIconSize.value, "color" to "#FFFFFF"))
    return mapOf("name" to name, "x" to box.left, "y" to box.top, "content" to content)
  }

  private companion object {
    const val ZoomInFrame = 84
    const val ZoomOutFrame = 168
    const val DragFrame = 240
    const val ReleaseFrame = 270
    const val LastImageFrame = 400
  }
}
