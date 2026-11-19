package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScrimLook
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetentSpring
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetentSpringDown
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDismiss
import dev.antigravity.fluidengine.ui.fluid.FluidSheetOpaqueBand
import dev.antigravity.fluidengine.ui.fluid.FluidSheetStops
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidSheetBottomRadiusPx
import dev.antigravity.fluidengine.ui.fluid.fluidSheetInsetPx
import dev.antigravity.fluidengine.ui.fluid.fluidSheetLargeHeightPx
import dev.antigravity.fluidengine.ui.fluid.fluidSheetLargeness
import dev.antigravity.fluidengine.ui.fluid.fluidSheetSettleTarget
import dev.antigravity.fluidengine.ui.fluid.fluidSheetSinkPx
import dev.antigravity.fluidengine.ui.fluid.fluidSheetStops
import dev.antigravity.fluidengine.ui.fluid.fluidSheetVeilStep
import dev.antigravity.fluidengine.ui.fluid.fluidSheetVisibleHeight
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.min

/**
 * Le tracce del foglio di vetro (F5B.2), scritte dalle funzioni pure del foglio (`FluidGlassSheet.kt`): altezze dei
 * detent, altezza visibile ed elastico, discesa intera sotto il detent più basso, rilascio, «grandezza» (velo e
 * rientro), angoli concentrici; la molla dei detent ([FluidSheetDetentSpring]) e quelle dell'host per entrare e uscire
 * (`FluidMotion.PopoverOpenY`, `PopoverExit`, le dissolvenze dello scrim). Il vetro è la ricetta del pannello
 * (`glassRecipe`, ruolo `Modal`, `panelOptics`) alla taglia di ogni fotogramma; il velo opaco è il `surface` del tema.
 *
 * **La scena è quella di Apple** (WWDC25 323, 6:47): un telefono di 402 × 874 dp con l'area sicura in alto a 58 dp (il
 * foglio a tutta altezza arriva a 64, come lì), angoli dello schermo da 55, una pagina-modulo con una griglia di foto
 * sotto il foglio, il foglio con un titolo e una lista di luoghi in schede bianche. **Il dito è quello di Apple**: in
 * `foglio-a-tutta-altezza` il bordo alto del foglio segue, fino al rilascio, il bordo alto misurato sul video
 * (`sessioni/f5b-2/strumenti/misura_323.py`, f12203–f12213), poi decide l'engine.
 *
 * **Fuori dalla traccia**: il chiarore e la pressione del dito (il banco non rende un gradiente), l'ultima frazione di
 * dissolvenza del foglio in uscita, il contenuto che lo scrim sfoca sotto il vetro (la pagina sì).
 *
 * Si scrivono con `GLASS_F5B2_DIR` (di solito `tools/glassbench/traces/f5b-2`), fase `f5b-2`.
 */
class F5b2SheetTraceTest {

  private val d = 3f
  private val fps = 60
  private val canvasW = 402f
  private val canvasH = 874f
  private val statusDp = 58f
  private val screenCornerDp = 55f
  private val phase = "f5b-2"

  private val windowPx = Size(canvasW * d, canvasH * d)
  private val largePx = fluidSheetLargeHeightPx(windowPx.height, statusDp * d, d)
  private val medium = fluidSheetStops(listOf(FluidSheetDetent.Medium, FluidSheetDetent.Large), windowPx.height, statusDp * d, d)
  private val small = fluidSheetStops(listOf(FluidSheetDetent.Height(150f.dpValue()), FluidSheetDetent.Medium), windowPx.height, statusDp * d, d)

  private val surface = Color.White
  private val ink = Color(0xFF121214)
  private val secondary = Color(0xFF68686E)

  /**
   * Il bordo alto del foglio di Apple sotto il dito, f12203–f12214 (px del ritaglio 460 × 1000 del telefono; il bordo
   * alto dello schermo a 20 px, il fondo a 966: 946 px per 874 dp).
   */
  private val appleTopPx = floatArrayOf(470f, 457f, 438f, 417f, 398f, 359f, 319f, 287f, 245f, 190f, 147f)
  private val applePxPerDp = 946f / 874f
  private val appleFrameMs = 1001.0 / 30.0

  @Test
  fun `esporta le tracce del foglio e ne controlla i capi`() {
    val traces = listOf(
      sale(),
      cresce(),
      aTuttaAltezza(),
      scende(),
      interrotto(),
      chiude(),
      dissolvenza(),
    )
    // Una variabile sua, come F6.6: con `GLASS_TRACE_DIR` si riscriverebbero anche le tracce delle altre fasi.
    GlassTraceOutput.requestedDir(System.getenv("GLASS_F5B2_DIR"))?.let { dir ->
      for (t in traces) t.write(File(dir, "${t.name}/trace.json"))
    }

    // Il foglio che va in cima ci arriva: velo pieno, a filo dei lati.
    val top = traces[2].frames.last().elements.single { it["name"] == "foglio" }
    assertEquals(0f, (top["x"] as Number).toFloat(), 0.01f)
    assertEquals(canvasW, (top["w"] as Number).toFloat(), 0.01f)
    val veil = traces[2].frames.last().elements.single { it["name"] == "velo" }
    assertTrue("velo pieno: ${veil["color"]}", (veil["color"] as String).endsWith("FF") || (veil["color"] as String).endsWith("FE"))
    // Il foglio interrotto torna a meta' altezza.
    val back = traces[4].frames.last().elements.single { it["name"] == "foglio" }
    assertEquals(canvasH - 5f - medium.heights[0] / d, (back["y"] as Number).toFloat(), 0.05f)
  }

  // --- Gli scenari -----------------------------------------------------------------------------------------------

  /** L'apertura a meta' altezza: la molla dell'host (`PopoverOpenY`) e lo scrim che arriva (`fadeIn`). */
  internal fun sale(): GlassTrace {
    val slide = TodaySpring(FluidMotion.PopoverOpenY, 0f, 1f)
    val fade = TodaySpring(FluidMotion.fadeIn(FluidMotion.PopoverFadeInMillis), 0f, 1f)
    val frames = timeline(-3, 48) { k, nanos ->
      val s = if (k < 0) 0f else slide.value(nanos)
      val p = if (k < 0) 0f else fade.value(nanos)
      frame(medium, medium.heights[0], slideOffsetPx = sheetHeightPx(medium, medium.heights[0]) * (1f - s), presence = p)
    }
    return trace("foglio-sale", frames, appleBlocks219Sale(), "apertura del foglio a meta' altezza (molla dell'host)")
  }

  /** Il foglio piccolo che la maniglia porta a meta' altezza: la molla dei detent. */
  private fun cresce(): GlassTrace {
    val from = small.heights[0]
    val to = small.heights[1]
    val spring = TodaySpring(FluidSheetDetentSpring, from, to)
    val frames = timeline(-3, 36) { k, nanos -> frame(small, if (k < 0) from else spring.value(nanos)) }
    return trace("foglio-cresce", frames, appleBlocks219Sale(), "il foglio piccolo che cresce a meta' (maniglia toccata)")
  }

  /** Da meta' altezza in cima col dito di Apple; al rilascio la molla dei detent, con lo slancio del dito. */
  internal fun aTuttaAltezza(): GlassTrace {
    val start = medium.heights[0]
    val releaseIndex = appleTopPx.size - 1
    val releaseMs = releaseIndex * appleFrameMs
    fun fingerAt(ms: Double): Float {
      val i = (ms / appleFrameMs).toInt().coerceIn(0, releaseIndex - 1)
      val f = ((ms - i * appleFrameMs) / appleFrameMs).toFloat().coerceIn(0f, 1f)
      val px = appleTopPx[i] + (appleTopPx[i + 1] - appleTopPx[i]) * f
      return start + (appleTopPx[0] - px) / applePxPerDp * d
    }
    val releaseAt = fingerAt(releaseMs)
    // La velocita' del dito al rilascio: gli ultimi due fotogrammi di Apple.
    val velocity = (appleTopPx[releaseIndex - 1] - appleTopPx[releaseIndex]) / applePxPerDp * d / (appleFrameMs / 1000.0).toFloat()
    val target = fluidSheetSettleTarget(medium, releaseAt, velocity, d)
    assertEquals("il rilascio va in cima", 1, target)
    val settle = TodaySpring(FluidSheetDetentSpring, releaseAt, medium.heights[target], velocity)
    val frames = timeline(-3, 60) { k, _ ->
      val ms = glassTraceTimeMs(k, fps)
      val position = when {
        ms <= 0.0 -> start
        ms <= releaseMs -> fingerAt(ms)
        else -> settle.value(((ms - releaseMs) * 1_000_000.0).toLong())
      }
      frame(medium, position)
    }
    return trace(
      "foglio-a-tutta-altezza", frames, appleBlocks323(),
      "da meta' altezza in cima: il dito di Apple fino al rilascio (433 ms), poi la molla dei detent",
      events = listOf(linkedMapOf("t" to round3(releaseMs.toFloat()), "kind" to "mark", "label" to "rilascio")),
    )
  }

  /** Da meta' altezza al foglio piccolo, con la maniglia. */
  private fun scende(): GlassTrace {
    val from = small.heights[1]
    val to = small.heights[0]
    val spring = TodaySpring(FluidSheetDetentSpringDown, from, to)
    val frames = timeline(-3, 36) { k, nanos -> frame(small, if (k < 0) from else spring.value(nanos)) }
    return trace("foglio-scende", frames, appleBlocks219Scende(), "da meta' altezza al foglio piccolo (maniglia toccata)")
  }

  /**
   * F5B-e: la maniglia manda il foglio in cima; a 150 ms il dito lo riprende (la molla si ferma dov'e'), lo tira giu' di
   * 120 dp in 250 ms e lo lascia piano: torna a meta' altezza.
   */
  internal fun interrotto(): GlassTrace {
    val start = medium.heights[0]
    val up = TodaySpring(FluidSheetDetentSpring, start, medium.heights[1])
    val grabMs = 150.0
    val grabbed = up.value((grabMs * 1_000_000.0).toLong())
    val dragMs = 250.0
    val dragPx = 120f * d
    val released = grabbed - dragPx
    val target = fluidSheetSettleTarget(medium, released, -200f * d, d)
    assertEquals("lasciato piano a meta' strada torna a meta' altezza", 0, target)
    val back = TodaySpring(FluidSheetDetentSpringDown, released, medium.heights[target], -200f * d)
    val frames = timeline(-3, 72) { k, nanos ->
      val ms = glassTraceTimeMs(k, fps)
      val position = when {
        ms <= 0.0 -> start
        ms <= grabMs -> up.value(nanos)
        ms <= grabMs + dragMs -> grabbed - dragPx * ((ms - grabMs) / dragMs).toFloat()
        else -> back.value(((ms - grabMs - dragMs) * 1_000_000.0).toLong())
      }
      frame(medium, position)
    }
    return trace(
      "foglio-trascinato-interrotto", frames, emptyList(),
      "la molla verso la cima interrotta dal dito a 150 ms, trascinata giu' e lasciata",
      events = listOf(
        linkedMapOf("t" to grabMs, "kind" to "interrupt", "label" to "il dito riprende il foglio"),
        linkedMapOf("t" to grabMs + dragMs, "kind" to "mark", "label" to "rilascio"),
      ),
    )
  }

  /** Spinto giu' di 140 dp in 200 ms dalla meta' altezza e lasciato: si chiude con la molla d'uscita dell'host. */
  private fun chiude(): GlassTrace {
    val start = medium.heights[0]
    val dragMs = 200.0
    val pushed = start - 140f * d
    val velocity = -140f * d / (dragMs / 1000.0).toFloat()
    assertEquals(FluidSheetDismiss, fluidSheetSettleTarget(medium, pushed, velocity, d))
    val slide = TodaySpring(FluidMotion.PopoverExit, 1f, 0f)
    val fade = TodaySpring(FluidMotion.fadeOut(FluidMotion.PopoverFadeOutMillis), 1f, 0f)
    val frames = timeline(-3, 40) { k, _ ->
      val ms = glassTraceTimeMs(k, fps)
      if (ms <= dragMs) {
        frame(medium, if (ms <= 0.0) start else start - 140f * d * (ms / dragMs).toFloat())
      } else {
        val nanos = ((ms - dragMs) * 1_000_000.0).toLong()
        val s = slide.value(nanos)
        frame(medium, pushed, slideOffsetPx = sheetHeightPx(medium, pushed) * (1f - s), presence = fade.value(nanos))
      }
    }
    return trace(
      "foglio-chiude", frames, emptyList(), "spinto giu' e lasciato: si chiude",
      events = listOf(linkedMapOf("t" to dragMs, "kind" to "mark", "label" to "rilascio")),
    )
  }

  /** iOS 27 `.crossFade`: la molla del foglio letta come opacita', senza viaggio. A occhio, senza Apple. */
  private fun dissolvenza(): GlassTrace {
    val slide = TodaySpring(FluidMotion.PopoverOpenY, 0f, 1f)
    val fade = TodaySpring(FluidMotion.fadeIn(FluidMotion.PopoverFadeInMillis), 0f, 1f)
    val frames = timeline(-3, 36) { k, nanos ->
      val s = if (k < 0) 0f else slide.value(nanos).coerceIn(0f, 1f)
      val p = if (k < 0) 0f else fade.value(nanos)
      frame(medium, medium.heights[0], presence = p, sheetAlpha = s)
    }
    return trace("foglio-dissolvenza", frames, emptyList(), "il foglio che compare in dissolvenza (iOS 27 .crossFade, a occhio)")
  }

  // --- Il fotogramma -----------------------------------------------------------------------------------------------

  /** Il velo opaco del fotogramma prima: insegue la «grandezza» come nel foglio (`fluidSheetVeilStep`). -1 a inizio scena. */
  private var veil = -1f

  private fun timeline(first: Int, last: Int, build: (Int, Long) -> List<Map<String, Any?>>): List<GlassTraceFrame> {
    veil = -1f
    return (first..last).map { k -> GlassTraceFrame(glassTraceTimeMs(k, fps), build(k, glassTraceTimeNanos(k.coerceAtLeast(0), fps))) }
  }

  private fun sheetHeightPx(stops: FluidSheetStops, position: Float): Float =
    fluidSheetVisibleHeight(position, stops, windowPx.height)

  /**
   * Gli elementi di un fotogramma: lo scrim, il foglio di vetro alla posizione [positionPx] (l'altezza che il dito o la
   * molla chiedono), il velo opaco, la maniglia, il contenuto.
   */
  private fun frame(
    stops: FluidSheetStops,
    positionPx: Float,
    slideOffsetPx: Float = 0f,
    presence: Float = 1f,
    sheetAlpha: Float = 1f,
  ): List<Map<String, Any?>> {
    val visiblePx = fluidSheetVisibleHeight(positionPx, stops, windowPx.height)
    val sinkPx = fluidSheetSinkPx(positionPx, stops)
    val largeness = fluidSheetLargeness(visiblePx, stops, largePx)
    val veilTarget = fluidSheetLargeness(visiblePx, stops, largePx, FluidSheetOpaqueBand)
    veil = if (veil < 0f) veilTarget else fluidSheetVeilStep(veil, veilTarget, 1000f / fps)
    val insetPx = fluidSheetInsetPx(largeness, d)
    val widthPx = min(windowPx.width - 2f * insetPx, 620f * d)
    val restInset = fluidSheetInsetPx(0f, d)
    val bottomRadius = fluidSheetBottomRadiusPx(
      windowSize = windowPx,
      sheetWidthPx = min(windowPx.width - 2f * restInset, 620f * d),
      insetPx = restInset,
      displayCornerPx = screenCornerDp * d,
      topRadiusPx = FluidRadius.Sheet.value * d,
      minimumPx = FluidRadius.Card.value * d,
    ) / d
    val w = widthPx / d
    val h = visiblePx / d
    val x = (canvasW - w) / 2f
    val y = canvasH - insetPx / d - h + (sinkPx + slideOffsetPx) / d

    val out = ArrayList<Map<String, Any?>>()
    if (presence > 0.004f) out += scrim(presence)
    if (sheetAlpha <= 0.004f || y >= canvasH) return out
    val radius = listOf(FluidRadius.Sheet.value, FluidRadius.Sheet.value, bottomRadius, bottomRadius)
    val recipe = glassRecipe(
      role = GlassRole.Modal,
      widthDp = w,
      heightDp = h,
      darkSide = false,
      optics = GlassDefaults.panelOptics(),
      intensity = sheetAlpha,
    )
    val shape = linkedMapOf<String, Any?>("name" to "foglio", "x" to round3(x), "y" to round3(y), "w" to round3(w), "h" to round3(h), "radius" to radius)
    // Una lastra ferma a ogni fotogramma (la taglia cambia, la forma no): il corpo dichiara anche luce in corpo, ombra in
    // viaggio e ritaglio emulato, spenti, come chiede il validatore del movimento.
    out += liquidElement(shape, recipe, theme = null, note = "glassRecipe: ruolo panel, ${round3(w)} x ${round3(h)} dp, intensita' ${round3(sheetAlpha)}")
      .also { completeRestBody(it) }
    out += linkedMapOf(
      "kind" to "shape", "name" to "velo",
      "x" to round3(x), "y" to round3(y), "w" to round3(w), "h" to round3(h), "radius" to radius,
      "color" to glassColor(surface.copy(alpha = veil * sheetAlpha)),
    )
    out += linkedMapOf(
      "kind" to "shape", "name" to "maniglia",
      "x" to round3(x + w / 2f - 18f), "y" to round3(y + 8f), "w" to 36, "h" to 5, "radius" to 2.5,
      "color" to glassColor(ink.copy(alpha = 0.18f * sheetAlpha)),
    )
    out += content(x, y, w, sheetAlpha)
    return out
  }

  /** Lo scrim dei modali (`FluidScrimLook.Modal`) con lo shader di servizio di F5A.4. */
  private fun scrim(presence: Float): Map<String, Any?> = linkedMapOf(
    "kind" to "shader",
    "name" to "scrim",
    "source" to "file:tools/glassbench/traces/f5a-4/velo-scrim.agsl",
    "x" to 0, "y" to 0, "w" to canvasW, "h" to canvasH,
    "radius" to 0,
    "inputs" to linkedMapOf("content" to linkedMapOf("saturation" to 1, "blurDp" to round3(FluidScrimLook.Modal.blurDp), "edge" to "clamp")),
    "uniforms" to linkedMapOf("dim" to FluidScrimLook.Modal.alpha(false)),
    "alpha" to round3(presence.coerceIn(0f, 1f)),
  )

  /** Il contenuto del foglio: il titolo e le schede bianche coi luoghi, che salgono col bordo alto. */
  private fun content(x: Float, y: Float, w: Float, alpha: Float): List<Map<String, Any?>> {
    val out = ArrayList<Map<String, Any?>>()
    val items = ArrayList<Map<String, Any?>>()
    fun text(text: String, tx: Float, ty: Float, size: Float, color: Color, weight: Int? = null) {
      items += linkedMapOf<String, Any?>(
        "kind" to "text", "text" to text, "x" to round3(tx), "y" to round3(ty), "sizeDp" to size,
        "color" to glassColor(color.copy(alpha = color.alpha * alpha)),
      ).also { if (weight != null) it["weight"] = weight }
    }
    text("Scegli i luoghi", x + w / 2f - 58f, y + 38f, 17f, ink, 600)
    var top = y + 62f
    for ((region, places) in Places) {
      text(region, x + 32f, top + 14f, 13f, secondary)
      top += 22f
      val cardH = places.size * 52f
      out += linkedMapOf(
        "kind" to "shape", "name" to "scheda-${region.lowercase()}",
        "x" to round3(x + 16f), "y" to round3(top), "w" to round3(w - 32f), "h" to round3(cardH),
        "radius" to FluidRadius.Group.value, "color" to glassColor(surface.copy(alpha = alpha)),
      )
      places.forEachIndexed { i, place -> text(place, x + 32f, top + i * 52f + 32f, 17f, ink) }
      top += cardH + 12f
      if (top > canvasH) break
    }
    out += linkedMapOf(
      "kind" to "shape", "name" to "testo",
      "x" to 0, "y" to 0, "w" to canvasW, "h" to canvasH, "radius" to 0, "color" to "#00000000",
      "content" to listOf(linkedMapOf("name" to "testo", "x" to 0, "y" to 0, "w" to canvasW, "h" to canvasH, "items" to items)),
      "contentClip" to "none",
    )
    return out
  }

  // --- La testa ----------------------------------------------------------------------------------------------------

  private fun trace(
    name: String,
    frames: List<GlassTraceFrame>,
    apple: List<Map<String, Any?>>,
    what: String,
    events: List<Map<String, Any?>> = emptyList(),
  ): GlassTrace {
    val head = linkedMapOf<String, Any?>(
      "_" to "F5B.2: $what. Numeri dalle funzioni del foglio (FluidGlassSheet.kt) e dalle molle dell'engine; scena e " +
        "dito di Apple ricostruiti (F5b2SheetTraceTest.kt). Senza chiarore del dito.",
      "palette" to "engine",
      "corner" to "engine",
      "background" to linkedMapOf("kind" to "color", "color" to "#F2F2F7"),
      "page" to page(),
      "probes" to listOf(
        linkedMapOf(
          "name" to "bordo-alto",
          "kind" to "edge",
          "line" to "v:120:40:$canvasH",
          "which" to "first",
          "label" to "il bordo alto del foglio, a 120 dp dal lato (lontano dagli angoli)",
        ),
        linkedMapOf(
          "name" to "luce-foglio",
          "kind" to "luma",
          "rect" to listOf(20, 760, 60, 40),
          "label" to "un tratto del foglio sopra la griglia di foto: vetro (scuro, colorato) o pagina (bianco)",
        ),
      ),
    )
    if (events.isNotEmpty()) head["events"] = events
    if (apple.isNotEmpty()) head["apple"] = apple
    return GlassTrace(name, phase, d, "light", canvasW, canvasH, frames, fps, head)
  }

  /** La pagina sotto il foglio: un modulo come quello di Apple, con una griglia di foto dove il foglio si posa. */
  private fun page(): List<Map<String, Any?>> = listOf(
    linkedMapOf("kind" to "text", "text" to "Titolo", "x" to 20, "y" to 128, "sizeDp" to 15, "color" to glassColor(secondary)),
    linkedMapOf("kind" to "rect", "x" to 16, "y" to 138, "w" to 370, "h" to 44, "color" to "#FFFFFF", "radius" to 10),
    linkedMapOf("kind" to "text", "text" to "Nuova raccolta", "x" to 30, "y" to 166, "sizeDp" to 17, "color" to glassColor(ink)),
    linkedMapOf("kind" to "text", "text" to "Descrizione", "x" to 20, "y" to 214, "sizeDp" to 15, "color" to glassColor(secondary)),
    linkedMapOf("kind" to "rect", "x" to 16, "y" to 224, "w" to 370, "h" to 96, "color" to "#FFFFFF", "radius" to 10),
    linkedMapOf("kind" to "text", "text" to "Luoghi", "x" to 20, "y" to 352, "sizeDp" to 20, "color" to glassColor(ink)),
    linkedMapOf(
      "kind" to "background", "x" to 16, "y" to 366, "w" to 370, "h" to 480, "radius" to 14,
      "background" to linkedMapOf(
        "kind" to "synthetic", "scaleDp" to 60, "detail" to 0.5,
        "palette" to listOf("#E9A23B", "#3A7BD5", "#2F5D34", "#F2994A", "#C0392B", "#8FB8DE"),
      ),
    ),
  )

  /**
   * Apple, R4 B8: il foglio che sale (WWDC25 219, 4:05,6). Un render: il foglio piccolo toccato cresce a meta'
   * altezza. `t = 0` e' l'ultimo fotogramma fermo prima del viaggio (f7361; il chiarore del dito comincia prima, a
   * f7357).
   */
  private fun appleBlocks219Sale(): List<Map<String, Any?>> = listOf(
    linkedMapOf(
      "name" to "render",
      "video" to "wwdc2025-219_hd",
      "t0Frame" to 7361,
      "crop" to listOf(520, 0, 880, 1080),
      "collage" to "artifacts/liquid-glass/sessioni/f5b-2/rif/foglio_sale_219_denso.jpg",
      "note" to "R4 B8, classe C (render): il foglio piccolo toccato sale a meta' altezza, 95% a 200 ms, appena oltre a 267.",
    ),
  )

  private fun appleBlocks219Scende(): List<Map<String, Any?>> = listOf(
    linkedMapOf(
      "name" to "render",
      "video" to "wwdc2025-219_hd",
      "t0Frame" to 7422,
      "crop" to listOf(520, 0, 880, 1080),
      "collage" to "artifacts/liquid-glass/sessioni/f5b-2/rif/foglio_scende_219_denso.jpg",
      "note" to "R4 B8, classe C (render): il foglio a meta' altezza toccato torna piccolo in ~250 ms, senza rimbalzo.",
    ),
  )

  /**
   * Apple, WWDC25 323 (6:47): interfaccia vera, il foglio «Select landmarks» trascinato da meta' altezza in cima. `t = 0`
   * e' f12203 (l'ultimo fermo); il video sta in `artifacts/liquid-glass/ricerca/r7/video/`.
   */
  private fun appleBlocks323(): List<Map<String, Any?>> = listOf(
    linkedMapOf(
      "name" to "telefono",
      "video" to "artifacts/liquid-glass/ricerca/r7/video/wwdc2025-323_hd.mp4",
      "t0Frame" to 12203,
      "crop" to listOf(1270, 40, 460, 1000),
      "collage" to "artifacts/liquid-glass/sessioni/f5b-2/rif/323_media-a-grande_denso.jpg",
      "note" to "classe B: vetro a meta', i lati si aprono dalla meta' del tratto, bianco e a filo in cima (f12214-f12218).",
    ),
  )

  private companion object {
    val Places: List<Pair<String, List<String>>> = listOf(
      "Asia" to listOf("Everest", "Fuji", "Wulingyuan"),
      "Africa" to listOf("Deadvlei", "Sahara", "Serengeti"),
      "Antartide" to listOf("Isole Shetland Meridionali"),
      "Oceania" to listOf("Grande barriera corallina", "Uluru"),
      "Europa" to listOf("Dolomiti", "Cinque Terre", "Etna"),
    )
  }
}

/** Un numero di dp come `Dp` per i detent della scena. */
private fun Float.dpValue() = androidx.compose.ui.unit.Dp(this)
