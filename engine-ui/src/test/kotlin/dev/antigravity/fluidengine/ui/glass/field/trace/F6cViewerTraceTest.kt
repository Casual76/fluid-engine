package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidMediaPagesPresetTest
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerPresetTraceTest
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerTraceTest
import dev.antigravity.fluidengine.ui.fluid.viewer.ViewerChromeMotion
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Le tracce dei visori di F6C (F6C.5), riunite sotto la fase `f6c` per il banco del movimento
 * (`tools/glassbench/traces/f6c/`), con il collage di ogni fotogramma nella sessione.
 *
 * - **`visore-apre`, `visore-barre-tocco`, `visore-info-su`, `visore-chiude-giu`, `visore-chiude-pizzico`**: la macchina
 *   delle pagine vera (`FluidPageHostState` con un orologio finto), la curva di ritiro della barra, la molla del foglio.
 *   Sono quelle di F6C.1 dopo la correzione del fondo: le scrive [FluidViewerTraceTest], qui chiamato per non
 *   duplicarne la scena.
 * - **`video-comandi`**: il Play del preset video sul fermo delle dune, con il piano di vetro e la lente di R4 B3, e lo
 *   scrubber che segue le barre del visore. È la scena di [FluidMediaPagesPresetTest] (F6C.3) con i due tocchi su
 *   fotogrammi interi del video di Apple: il tocco che nasconde a 166,7 ms (Apple `f5368`, l'inizio della scomparsa di
 *   B3) e quello che mostra a 833,3 ms (`f5399`, l'inizio della comparsa). Così `glassmotion.py pair` mette i due
 *   accanto fotogramma per fotogramma, contro `B3_comparsa_play`.
 * - **`pdf-pagine`**: lo sfoglio di un documento di tre pagine miste col contatore del preset (F6C.3).
 * - **`testo-trova`**: «Trova» del preset testo sopra la tastiera finta, con la macchina di F6B.6 (F6C.2). La pagina
 *   in monospazio la aggiunge `strumenti/pagina_testo.py` della sessione F6C.5.
 *
 * Le immagini stanno negli artefatti delle sessioni (`artifacts/liquid-glass/sessioni/f6c-1` e `f6c-3`): il banco si fa
 * girare dalla radice del worktree principale, dove ci sono. Tema chiaro, come le riprese per Alessio.
 *
 * Si scrivono con `GLASS_F6C_DIR` (di solito `tools/glassbench/traces/f6c`).
 */
class F6cViewerTraceTest {

  private val phase = "f6c"
  private val fps = 120
  private val room = Rect(0f, 0f, 320f, 568f)
  private val f6c3Plates = "artifacts/liquid-glass/sessioni/f6c-3/tracce/"

  /** Le misure, la fonte e la fiducia di ogni traccia: la lista d'accettazione di F6C.0 le chiede accanto al collage. */
  private val sources = mapOf(
    "visore-apre" to "Molla critica a 0,355 s dalla miniatura (F5B.3 e corr-zoom, fiducia alta); il fondo entra " +
      "nell'ultimo 12% (scelta di F6C.1). L'apertura di Foto in iOS 26 non è misurata (F6C.0: non trovata).",
    "visore-barre-tocco" to "La barra che si ritira di R5 V5 per analogia (Podcast, iOS 27): via in 200 ms, di " +
      "nuovo nitida in 350 ms; fiducia media, trasferibilità bassa. Il contenuto non si sposta.",
    "visore-info-su" to "Il foglio di F5B.2 a Medium (52%), senza velo fino a Medium; Foto iOS 26 ≈54% da un fermo " +
      "(fiducia media). Gesto in su e tasto «i» aprono lo stesso foglio (scelta di Alessio).",
    "visore-chiude-giu" to "Il dito di F6B.5a, soglia di 110 dp di FluidImageViewer (fiducia alta sul codice), ritorno " +
      "nella miniatura con la molla di F5B.3. Gallery v1 usa 300 px: scelta di gusto.",
    "visore-chiude-pizzico" to "Il pizzico di Gallery-2 (ViewerGestures.kt): rilascio a scala ≤ 0,85, lancio ≥ 1,6 " +
      "al secondo (fiducia alta sul codice, analogia per il movimento).",
    "video-comandi" to "R4 B3 (WWDC25 219, 2:56–3:09): comparsa 430 ms, il contorno poi il simbolo dilatato ≈2× che " +
      "si contrae; scomparsa 470 ms (fiducia media). Lo scrubber segue le barre (R5 V5). Nessun ritiro a tempo.",
    "pdf-pagine" to "Anteprima di iOS 26 per analogia (guida Apple: scorrimento verticale, contatore che apre le " +
      "miniature); nessun tempo misurato, lo scorrimento del banco è a occhio.",
    "testo-trova" to "«Trova» di F6B.6 (findInPage, macchina vera); occorrenze e corrente di TextFind; lo " +
      "scorrimento verso la corrente usa la molla di serie di Compose (fiducia bassa).",
  )

  @Test fun `esporta le otto tracce dei visori e ne controlla i capi`() {
    val viewer = FluidViewerTraceTest()
    val presets = FluidViewerPresetTraceTest()
    val media = FluidMediaPagesPresetTest()
    val journeys = listOf("visore-apre", "visore-barre-tocco", "visore-info-su", "visore-chiude-giu", "visore-chiude-pizzico")
      .map { name -> call<GlassTrace>(viewer, "makeTrace", name, false) }
    val text = call<GlassTrace>(presets, "textTrace", false)
    val pages = call<GlassTrace>(media, "pagesTrace", false)
    val video = videoTrace()
    val traces = (journeys + text + pages).map(::adopt) + video
    assertEquals(sources.keys, traces.map { it.name }.toSet())

    for (trace in traces) {
      assertEquals(fps, trace.fps)
      assertTrue(trace.name, trace.frames.size > 100)
    }
    // Una sola foto per fotogramma nei viaggi: sorgente e arrivo non si leggono mai insieme (il viaggio d'oro, F1B).
    for (trace in traces.filter { it.name.startsWith("visore-") }) {
      assertTrue(trace.name, trace.frames.all { f -> f.elements.count { it["name"] == "foto" } == 1 })
    }
    // I tocchi del video cadono su fotogrammi interi del banco e del video di Apple.
    val events = video.head["events"] as List<*>
    assertEquals(listOf(HideMs, ShowMs), events.map { (it as Map<*, *>)["t"] })
    val apple = video.head["apple"] as List<*>
    for (block in apple) {
      block as Map<*, *>
      val start = if (block["name"] == "b3-comparsa") ShowMs else HideMs
      val first = if (block["name"] == "b3-comparsa") 5399 else 5368
      val t0 = block["t0Frame"] as Int
      assertEquals("Apple f$first sul tocco", start, (first - t0) * 1001.0 / 30.0, 1.0)
    }
    // Il Play compare per lente: il glifo non c'è al tocco, è dilatato a metà, nitido entro la comparsa di B3.
    val play = video.frames.map { f -> f.elements.firstOrNull { it["name"] == "play" } }
    fun glyph(ms: Double) = ((play[(ms * fps / 1000).toInt()]?.get("content") as? List<*>)?.single() as? Map<*, *>)
    assertNull("Nascosto fra i due tocchi", play[(700 * fps / 1000)])
    assertEquals("La lente prima del glifo", 0.0, (glyph(ShowMs + 75)?.get("alpha") as Number).toDouble(), 1e-6)
    assertTrue((glyph(ShowMs + 170)?.get("scale") as Number).toFloat() > 1.5f)
    assertEquals(1.0, (glyph(ShowMs + FluidMotion.PlaneAppearMillis + 10.0)?.get("scale") as Number).toDouble(), 1e-3)

    GlassTraceOutput.requestedDir(System.getenv("GLASS_F6C_DIR"))?.let { dir ->
      for (trace in traces) {
        val file = File(dir, "${trace.name}/trace.json")
        if (trace.name == "video-comandi" || trace.name == "pdf-pagine") writeWithPage(trace, file) else trace.write(file)
      }
    }
  }

  /** La traccia di un pacchetto, rinominata nella fase f6c, con la fonte e le immagini dove stanno davvero. */
  private fun adopt(trace: GlassTrace): GlassTrace {
    val head = LinkedHashMap(trace.head.mapValues { (_, v) -> plates(v) })
    head["_fonte"] = sources.getValue(trace.name)
    head["_origine"] = "Esportata da F6cViewerTraceTest con la macchina di ${trace.phase}."
    val frames = trace.frames.map { f -> GlassTraceFrame(f.tMs, f.elements.map { plates(it) as Map<String, Any?> }) }
    return GlassTrace(trace.name, phase, trace.density, trace.theme, trace.widthDp, trace.heightDp, frames, trace.fps, head)
  }

  private fun plates(value: Any?): Any? = when (value) {
    is String -> if (value.startsWith("build/f6c-3-tracce-assets/")) f6c3Plates + value.substringAfterLast('/') else value
    is Map<*, *> -> LinkedHashMap<String, Any?>().also { out -> value.forEach { (k, v) -> out[k as String] = plates(v) } }
    is List<*> -> value.map(::plates)
    else -> value
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> call(target: Any, name: String, vararg args: Any): T {
    val method = target.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
    method.isAccessible = true
    return method.invoke(target, *args) as T
  }

  // --- video-comandi, sui fotogrammi di B3 ------------------------------------------------------------------------

  private val exitGlyph = GlassPieceContent(GlassPieceContentRole.Exit, span = GlassPieceContentDefaults.LensExitSpan)
  private val lensGlyph = GlassPieceContent(GlassPieceContentRole.Lens)

  private fun videoTrace(): GlassTrace {
    val box = Rect(132f, 256f, 188f, 312f)
    val piece = GlassPlanePiece("play", box, FluidCornerRadii.all(28f), variant = GlassLiquidVariant.Clear,
      id = "play", transition = GlassPlaneTransition.Materialize)
    val shown = GlassPlaneState(listOf(piece), 0f, box, room = room)
    val hidden = GlassPlaneState(emptyList(), 0f, box, room = room)
    val hide = GlassPlaneJourney(shown, hidden)
    val show = GlassPlaneJourney(hidden, shown)
    val frames = (0..180).map { index ->
      val now = glassTraceTimeMs(index, fps)
      val video = Rect(-135f + index / 12f, 107f, 455f + index / 12f, 461f)
      val elements = mutableListOf(image("video", video, f6c3Plates + "dune.png"))
      val flight = when {
        now in HideMs..(HideMs + hide.durationMs) -> hide.frame(now - HideMs).pieces.singleOrNull()
        now >= ShowMs -> show.frame(now - ShowMs).pieces.singleOrNull()
        else -> null
      }?.takeIf { it.materialize > 0f }
      val resting = now < HideMs || now > ShowMs + show.durationMs
      if (flight != null || resting) {
        val bounds = flight?.bounds ?: box
        val look = when (flight?.change) {
          GlassPlaneChange.Materialize -> glassPieceContentLook(lensGlyph, flight.phase)
          GlassPlaneChange.Dematerialize -> glassPieceContentLook(exitGlyph, flight.phase)
          else -> GlassContentLook.Sharp
        }
        val content = listOf(mapOf(
          "name" to "glifo", "icon" to "fluid:play_arrow.fill", "iconSizeDp" to 29, "color" to "#FFFFFF",
          "x" to (bounds.width - box.width) / 2f, "y" to (bounds.height - box.height) / 2f,
          "w" to box.width, "h" to box.height, "alpha" to look.alpha, "scale" to look.scale,
          "blurSigmaDp" to glassContentSigmaPx(look.blur, box.height),
        ))
        elements += clearGlass("play", bounds, flight?.materialize ?: 1f, content)
      }
      // Lo scrubber è della chrome del visore: va e torna con la curva delle barre, non con la lente.
      val timeline = when {
        now < HideMs -> 1f
        now < ShowMs -> (1f - ((now - HideMs) / ViewerChromeMotion.HideMillis).toFloat()).coerceIn(0f, 1f)
        else -> ((now - ShowMs) / ViewerChromeMotion.ShowMillis).toFloat().coerceIn(0f, 1f)
      }
      if (timeline > 0f) {
        val footer = Rect(16f, 477f, 304f, 536f)
        elements += clearGlass("scrubber", footer, timeline, listOf(mapOf(
          "name" to "tempo", "w" to footer.width, "h" to footer.height, "alpha" to timeline,
          "items" to listOf(
            mapOf("kind" to "background", "x" to 14, "y" to 22, "w" to 260, "h" to 3,
              "background" to mapOf("kind" to "color", "color" to "#767676")),
            mapOf("kind" to "background", "x" to 14, "y" to 22, "w" to 82, "h" to 3,
              "background" to mapOf("kind" to "color", "color" to "#FFFFFF")),
            mapOf("kind" to "text", "x" to 14, "y" to 43, "text" to "0:18", "sizeDp" to 12, "color" to "#FFFFFF"),
            mapOf("kind" to "text", "x" to 242, "y" to 43, "text" to "0:56", "sizeDp" to 12, "color" to "#FFFFFF"),
          ),
        )))
      }
      GlassTraceFrame(now, elements)
    }
    val collage = "artifacts/liquid-glass/riferimenti/collage/"
    return GlassTrace("video-comandi", phase, 1f, "light", room.width, room.height, frames, fps, mapOf(
      "background" to mapOf("kind" to "color", "color" to "#000000"),
      "events" to listOf(
        mapOf("t" to HideMs, "kind" to "mark", "label" to "Tocco: via i comandi"),
        mapOf("t" to ShowMs, "kind" to "mark", "label" to "Tocco: tornano i comandi"),
      ),
      "apple" to listOf(
        mapOf("name" to "b3-comparsa", "video" to "wwdc2025-219_hd", "t0Frame" to 5374, "crop" to listOf(680, 240, 560, 560),
          "collage" to collage + "B3_comparsa_play_f5290-5400_ogni2.jpg",
          "note" to "f5399, l'inizio della comparsa di B3, cade sul tocco a 833 ms."),
        mapOf("name" to "b3-scomparsa", "video" to "wwdc2025-219_hd", "t0Frame" to 5363, "crop" to listOf(680, 240, 560, 560),
          "collage" to collage + "B3_comparsa_play_f5397-5420.jpg",
          "note" to "f5368, l'inizio della scomparsa di B3, cade sul tocco a 167 ms."),
      ),
      "_fonte" to sources.getValue("video-comandi"),
      "_" to "F6C.5. Piano di vetro e lente di produzione (R4 B3) a 120 Hz, sul fermo delle dune spostato nel banco.",
    ))
  }

  private fun image(name: String, rect: Rect, path: String): Map<String, Any?> = mapOf(
    "name" to name, "x" to rect.left, "y" to rect.top, "w" to rect.width, "h" to rect.height,
    "kind" to "background", "background" to mapOf("kind" to "image", "path" to path),
  )

  private fun clearGlass(name: String, rect: Rect, amount: Float, content: List<Map<String, Any?>>): Map<String, Any?> {
    val recipe = glassRecipe(GlassRole.Floating, rect.width, rect.height, true, GlassLiquidVariant.Clear)
    glassMaterializeRecipe(recipe, amount)
    val shape = mapOf<String, Any?>("name" to name, "x" to rect.left, "y" to rect.top, "w" to rect.width, "h" to rect.height)
    return liquidElement(shape, recipe, "dark", "Clear M2 / R4 B3").also(::completeRestBody) + ("content" to content)
  }

  /** Il video e la carta stanno nella pagina del banco, sotto il vetro, come li scrive F6C.3. */
  private fun writeWithPage(trace: GlassTrace, file: File) {
    val frames = trace.frames.map { frame ->
      val (page, elements) = frame.elements.partition { it["kind"] == "background" }
      mapOf("t" to frame.tMs, "page" to page, "elements" to elements)
    }
    file.parentFile?.mkdirs()
    file.writeText(GlassTraceJson.encode(trace.toJson() + ("frames" to frames)), Charsets.UTF_8)
  }

  private companion object {
    /** Venti fotogrammi del banco a 120 Hz, cinque del video di Apple a 29,97 fps (166,7 contro 166,8 ms). */
    const val HideMs = 20 * 1000.0 / 120
    /** Cento fotogrammi del banco, venticinque di Apple (833,3 contro 834,2 ms). */
    const val ShowMs = 100 * 1000.0 / 120
  }
}
