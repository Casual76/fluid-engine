package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedScrim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
 * F5B.4: le prove di movimento che il piano chiede per la chiusura di F5B («Come si verifica»), scritte dal codice di
 * oggi e accoppiate ai fotogrammi di Apple dove un riferimento c'e'.
 *
 *  - `popover-*`, `conferma-*`, `avviso-*`: i costruttori di F5B.1 ([F5b1ModalTraceTest]) con la testa che mancava: le
 *    riprese di Apple (WWDC25 284, il popover di formattazione sull'iPad e la conferma «Discard Changes» di UIKit; WWDC25
 *    323, la conferma «Delete» di SwiftUI) e le sonde della `curve`. L'avviso non ha un video suo: accanto ha le due
 *    conferme, che sono il riferimento di moto che F5B.1 aveva usato («affine»).
 *  - `foglio-sale`, `foglio-a-tutta-altezza`, `foglio-trascinato-interrotto`: i costruttori di F5B.2 ([F5b2SheetTraceTest]).
 *  - `foglio-dal-tasto`: il costruttore di F5B.3 ([F5b3ZoomTraceTest]).
 *  - `foglio-soglia`: la salita di `foglio-a-tutta-altezza` con `undimmedUpTo = Medium`, cioe' la soglia di oscuramento
 *    di F5B.1 (F5B-c): finche' il foglio sta al detent medio la pagina non si scurisce, sopra si'.
 *
 * **Lo scrim dei fogli e' quello di oggi.** Da F5B.1 un `FluidGlassSheet` (modo `Modal`) scurisce la pagina con un velo
 * nero al 12% senza sfocarla (`FluidPresentationScrim`): gli esportatori di F5B.2 e F5B.3 disegnano ancora lo scrim dei
 * portali, sfocato di 3,75 dp, che era quello del loro tempo. Qui ogni `scrim` diventa il velo semplice, con la stessa
 * presenza; nella soglia c'e' solo dove il foglio e' sopra il detent medio (`fluidSheetIsUndimmed`: mezzo pixel).
 *
 * Le sonde dei fogli leggono i numeri della traccia (`foglio.y`, `foglio.w`): con un velo su tutta la pagina un bordo
 * cercato dai pixel contro la pagina nuda cadrebbe in cima alla colonna. Quelle di popover, conferme e avvisi leggono i
 * pixel, ma solo cio' che schiarisce (`light`): il velo dell'avviso scurisce e resta fuori.
 *
 * Lo zoom fra pagine non si riscrive: le tracce di F5B.3 (`f5b-3/zoom-apre`, `zoom-torna`) sono gia' quelle della macchina
 * di oggi, con Apple (WWDC24 10145) e le sonde; F5B.4 le rende cosi' come sono.
 *
 * Si scrivono con `GLASS_F5B4_DIR` (di solito `tools/glassbench/traces/f5b-4`), fase `f5b-4`: con `GLASS_TRACE_DIR` si
 * riscriverebbero anche le tracce delle altre fasi.
 */
internal object F5b4PresentationTraces {
  const val Phase = "f5b-4"

  /** I fotogrammi di Apple che valgono t = 0: l'ultimo fermo prima del viaggio (letti a mano sui fogli densi). */
  const val ApplePopoverOpenT0 = 25168
  const val ApplePopoverCloseT0 = 25230
  const val AppleDeleteOpenT0 = 13958
  const val AppleDiscardOpenT0 = 26381
  const val AppleDiscardCloseT0 = 26450

  private const val Video284 = "artifacts/liquid-glass/ricerca/r7/video/wwdc2025-284_hd.mp4"
  private const val Video323 = "artifacts/liquid-glass/ricerca/r7/video/wwdc2025-323_hd.mp4"
  private const val Rif = "artifacts/liquid-glass/sessioni/f5b-4/rif"

  /** Il popover di formattazione sull'iPad, col tasto «Aa» e il pannello intero (pixel del video HD). */
  private val PopoverCrop = listOf(600, 480, 560, 420)

  /** La parte alta del telefono di 323 (il cestino e la conferma) e di 284 (la X e «Discard Changes»). */
  private val DeleteCrop = listOf(1270, 40, 460, 440)
  private val DiscardCrop = listOf(730, 20, 460, 400)

  private fun apple(name: String, video: String, t0: Int, crop: List<Int>, collage: String, note: String) =
    linkedMapOf<String, Any?>(
      "name" to name,
      "video" to video,
      "t0Frame" to t0,
      "crop" to crop,
      "collage" to "$Rif/$collage",
      "note" to note,
    )

  private val PopoverOpen = apple(
    "ipad", Video284, ApplePopoverOpenT0, PopoverCrop, "apple_popover_apre_284_f25166-25183.jpg",
    "classe B (interfaccia vera, UIKit, WWDC25 284 14:00): il tasto «Aa» della barra diventa il popover di formattazione. " +
      "f25169 la barra si spegne, f25170 una goccia sfocata sul tasto, f25173 il pannello quasi intero, voci leggibili a " +
      "f25174-25175, posa a ~f25176.",
  )

  private val PopoverClose = apple(
    "ipad", Video284, ApplePopoverCloseT0, PopoverCrop, "apple_popover_torna_284_f25228-25245.jpg",
    "classe B: il popover rientra nel tasto. f25231 le voci si sfocano, f25234-25236 una goccia sfocata che sale al tasto, " +
      "f25237-25238 la barra di nuovo nitida (233-267 ms).",
  )

  private val DeleteOpen = apple(
    "delete", Video323, AppleDeleteOpenT0, DeleteCrop, "apple_conferma_delete_323_f13956-13972.jpg",
    "classe B (SwiftUI, WWDC25 323 7:46): il cestino diventa la conferma. f13959-13960 il glifo si spegne, f13961-13962 la " +
      "goccia chiara sul tasto, f13963-13967 il corpo cresce con le righe sfocate, leggibile a f13968, fermo a ~f13970.",
  )

  private val DiscardOpen = apple(
    "uikit", Video284, AppleDiscardOpenT0, DiscardCrop, "apple_conferma_uikit_284_f26379-26392.jpg",
    "classe B (UIKit, WWDC25 284 14:40): la X diventa «Discard Changes». La X premuta f26376-26380 (prima di t = 0), " +
      "f26382 la goccia, f26383-26386 il corpo sfocato, leggibile a f26387, fermo a ~f26389.",
  )

  private val DiscardClose = apple(
    "uikit", Video284, AppleDiscardCloseT0, DiscardCrop, "apple_conferma_torna_284_f26448-26460.jpg",
    "classe B (UIKit): toccato fuori, «Discard Changes» rientra nella X. f26451 sfocato, f26452-26454 la goccia sulla X, " +
      "la X nitida a f26456, ferma a f26457 (233 ms).",
  )

  /** Le sonde dei pannelli di F5B.1: un bordo lungo la colonna della sorgente e la larghezza lungo una riga dell'arrivo. */
  private fun panelProbes(
    column: Double,
    which: String,
    row: Double,
    heightDp: Double,
    ref: Map<String, Any?>,
  ): List<Map<String, Any?>> = listOf(
    linkedMapOf(
      "name" to if (which == "first") "bordo-alto" else "bordo-basso",
      "kind" to "edge",
      "line" to "v:$column:0:520",
      "which" to which,
      "mode" to "light",
      "percentOf" to heightDp,
      "label" to "il bordo ${if (which == "first") "alto" else "basso"} del vetro lungo la colonna che passa per la " +
        "sorgente e per l'arrivo, dai pixel (solo cio' che schiarisce); in % dell'altezza del pannello",
      "ref" to ref,
    ),
    linkedMapOf(
      "name" to "larghezza",
      "kind" to "width",
      "line" to "h:$row:0:360",
      "mode" to "light",
      "label" to "la larghezza del vetro lungo una riga dell'arrivo, dai pixel",
      "ref" to ref,
    ),
  )

  private fun ref(restMs: List<Int>, source: String) = linkedMapOf<String, Any?>("restMs" to restMs, "source" to source)

  private val OpenRef = ref(listOf(233, 300), "WWDC25 284 f25168: corpo a 33-67 ms, voci leggibili a ~170-200, posa a ~267 (F5B.1)")
  private val CloseRef = ref(listOf(200, 267), "WWDC25 284 f25230 e f26450: il tasto di nuovo nitido a 200-267 ms")
  private val ConfirmRef = ref(
    listOf(267, 400),
    "UIKit (284 f26381) fermo a ~267 ms, SwiftUI (323 f13958) a ~400; il testo nitido a ~400 nei due (F5B.1)",
  )

  /** Una traccia di un altro pacchetto con la testa di F5B.4: nome, fase, nota, Apple, sonde ed eventi. */
  private fun retitled(
    built: GlassTrace,
    name: String,
    note: String,
    apple: List<Map<String, Any?>>?,
    probes: List<Map<String, Any?>>?,
    events: List<Map<String, Any?>>? = null,
    frames: List<GlassTraceFrame> = built.frames,
  ): GlassTrace {
    val head = LinkedHashMap(built.head)
    head["_"] = note
    if (apple != null) head["apple"] = apple else head.remove("apple")
    if (probes != null) head["probes"] = probes
    if (events != null) head["events"] = events
    return GlassTrace(name, Phase, built.density, built.theme, built.widthDp, built.heightDp, frames, built.fps, head)
  }

  /**
   * Lo scrim di un foglio di oggi (`FluidPresentationScrim`): un velo nero al 12% (`GlassPaintedScrim`, lato chiaro)
   * per la presenza del fotogramma, senza sfocatura, al posto dello scrim sfocato dei portali che gli esportatori di F5B.2
   * e F5B.3 disegnano ancora. [dimmed] dice se in quel fotogramma la pagina e' scurita (la soglia di `undimmedUpTo`).
   */
  internal fun todaySheetScrim(
    frames: List<GlassTraceFrame>,
    widthDp: Float,
    heightDp: Float,
    dimmed: (List<Map<String, Any?>>) -> Boolean = { true },
  ): List<GlassTraceFrame> = frames.map { frame ->
    val dim = dimmed(frame.elements)
    val elements = frame.elements.mapNotNull { e ->
      if (e["name"] != "scrim") return@mapNotNull e
      if (!dim) return@mapNotNull null
      val presence = (e["alpha"] as? Number)?.toFloat() ?: 1f
      linkedMapOf<String, Any?>(
        "kind" to "shape", "name" to "scrim",
        "x" to 0, "y" to 0, "w" to widthDp, "h" to heightDp, "radius" to 0,
        "color" to glassColor(GlassPaintedScrim.color(false).copy(alpha = GlassPaintedScrim.LightAlpha * presence.coerceIn(0f, 1f))),
      )
    }
    GlassTraceFrame(frame.tMs, elements)
  }

  /** Le sonde dei fogli, dai numeri della traccia: il bordo alto e la larghezza del vetro, e la luce di un tratto. */
  private fun sheetProbes(ref: Map<String, Any?>?, withLight: Boolean = true): List<Map<String, Any?>> = buildList {
    add(linkedMapOf<String, Any?>(
      "name" to "bordo-alto", "kind" to "value", "path" to "foglio.y", "unit" to "dp",
      "label" to "il bordo alto del foglio (dp dalla cima): la molla che lo porta",
    ).also { if (ref != null) it["ref"] = ref })
    add(linkedMapOf<String, Any?>(
      "name" to "larghezza", "kind" to "value", "path" to "foglio.w", "unit" to "dp",
      "label" to "la larghezza del vetro: 392 dp staccato dai lati, 402 a filo",
    ))
    if (withLight) add(linkedMapOf<String, Any?>(
      "name" to "luce-foglio", "kind" to "luma", "rect" to listOf(20, 760, 60, 40),
      "label" to "un tratto del foglio sopra la griglia di foto: vetro (scuro, colorato) o pagina (bianco)",
    ))
  }

  private val SheetRiseRef = linkedMapOf<String, Any?>(
    "overshootPct" to listOf(0, 4),
    "restMs" to listOf(300, 400),
    "source" to "R4 B8 (WWDC25 219 4:05,6, render): 95% a 200 ms, appena oltre a 267-300, fermo a ~400",
  )

  /** Le tracce di F5B.4, nell'ordine della galleria. */
  fun build(): List<GlassTrace> {
    val modals = F5b1ModalTraceTest()
    val sheets = F5b2SheetTraceTest()
    val zoom = F5b3ZoomTraceTest()
    val source = "F5B.4, chiusura di F5B, dal codice di oggi:"
    fun modal(name: String, closing: Boolean) = modals.trace(modals.scenes.single { it.name == name }, closing)

    val popoverNote = "$source il costruttore di F5B.1 (F5b1ModalTraceTest.kt): sagoma, lente, inchiostro e faccia " +
      "dalle funzioni dell'engine; tela di prova senza pagina."
    val sheetNote = "$source il costruttore di F5B.2 (F5b2SheetTraceTest.kt) con lo scrim dei fogli di oggi (velo al " +
      "12%, nessuna sfocatura: F5B.1)."

    val rise = sheets.sale()
    val top = sheets.aTuttaAltezza()
    val interrupted = sheets.interrotto()
    val fromButton = zoom.foglioDalTasto(closing = false)
    val mediumDp = (top.frames.first().elements.single { it["name"] == "foglio" }["h"] as Number).toFloat()
    val threshold = todaySheetScrim(top.frames, top.widthDp, top.heightDp) { elements ->
      val sheet = elements.firstOrNull { it["name"] == "foglio" }
      // `fluidSheetIsUndimmed`: scurita solo sopra il detent medio, oltre mezzo pixel (a 3 px per dp).
      sheet == null || (sheet["h"] as Number).toFloat() > mediumDp + 0.5f / top.density
    }
    val firstDimmed = threshold.first { f -> f.elements.any { it["name"] == "scrim" } }.tMs

    @Suppress("UNCHECKED_CAST")
    val topEvents = top.head["events"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val interruptedEvents = interrupted.head["events"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val topApple = top.head["apple"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val fromButtonApple = fromButton.head["apple"] as List<Map<String, Any?>>

    return listOf(
      retitled(
        modal("popover", closing = false), "popover-apre", "$popoverNote Il tasto in alto a destra diventa il popover.",
        listOf(PopoverOpen), panelProbes(300.0, "last", 170.0, 159.0, OpenRef),
      ),
      retitled(
        modal("popover", closing = true), "popover-torna", "$popoverNote Il popover rientra nel suo tasto, t = 0 alla richiesta.",
        listOf(PopoverClose), panelProbes(300.0, "last", 170.0, 159.0, CloseRef),
      ),
      retitled(
        modal("conferma", closing = false), "conferma-apre", "$popoverNote «Elimina» diventa la conferma, sopra di se'.",
        listOf(DeleteOpen, DiscardOpen), panelProbes(286.0, "first", 221.0, 94.0, ConfirmRef),
      ),
      retitled(
        modal("conferma", closing = true), "conferma-torna", "$popoverNote La conferma rientra in «Elimina», t = 0 alla richiesta.",
        listOf(DiscardClose), panelProbes(286.0, "first", 221.0, 94.0, CloseRef),
      ),
      retitled(
        modal("avviso", closing = false), "avviso-apre",
        "$popoverNote L'avviso esce dal tasto in basso a sinistra e si posa al centro; il velo della pagina e' quello dell'avviso.",
        listOf(DeleteOpen + ("note" to "riferimento affine (F5B.1): la conferma Delete per il ritmo del testo; l'avviso di Apple non e' nel materiale."),
          DiscardOpen + ("note" to "riferimento affine: la conferma UIKit dalla X.")),
        panelProbes(64.0, "first", 260.0, 156.0, ConfirmRef),
      ),
      retitled(
        modal("avviso", closing = true), "avviso-torna", "$popoverNote L'avviso rientra nel suo tasto, t = 0 alla richiesta.",
        listOf(DiscardClose + ("note" to "riferimento affine (F5B.1): il rientro della conferma UIKit nella X.")),
        panelProbes(64.0, "first", 260.0, 156.0, CloseRef),
      ),
      retitled(
        rise, "foglio-sale", "$sheetNote L'apertura a meta' altezza con la molla dell'host.",
        listOf(
          linkedMapOf(
            "name" to "render", "video" to "wwdc2025-219_hd", "t0Frame" to 7361, "crop" to listOf(520, 0, 880, 1080),
            "collage" to "$Rif/B8_foglio_sale_219_4m05.6-4m06.1.jpg",
            "note" to "R4 B8, classe C (render), il tratto 4:05,6-4:06,1 che il piano indica: il foglio piccolo toccato sale a " +
              "meta' altezza. Collage fatto con riferimenti/tools/sheet.py.",
          ),
        ),
        sheetProbes(SheetRiseRef),
        frames = todaySheetScrim(rise.frames, rise.widthDp, rise.heightDp),
      ),
      retitled(
        top, "foglio-a-tutta-altezza", "$sheetNote Da meta' altezza in cima col dito di Apple (323), poi la molla dei detent.",
        topApple, sheetProbes(null), topEvents,
        frames = todaySheetScrim(top.frames, top.widthDp, top.heightDp),
      ),
      retitled(
        top, "foglio-soglia",
        "$sheetNote La stessa salita con undimmedUpTo = Medium (la soglia di F5B.1): a meta' altezza la pagina resta " +
          "attiva e chiara, sopra si scurisce. Apple non ha questa scena nel materiale.",
        null,
        sheetProbes(null, withLight = false) + linkedMapOf<String, Any?>(
          "name" to "luce-pagina", "kind" to "luma", "rect" to listOf(20, 8, 362, 40),
          "label" to "un tratto di pagina che il foglio non copre mai: scende del 12% quando la pagina si scurisce",
        ),
        topEvents + linkedMapOf<String, Any?>("t" to firstDimmed, "kind" to "mark", "label" to "la pagina si scurisce"),
        frames = threshold,
      ),
      retitled(
        interrupted, "foglio-trascinato-interrotto",
        "$sheetNote F5B-e: la molla verso la cima interrotta dal dito a 150 ms, trascinata giu' e lasciata.",
        null, sheetProbes(null), interruptedEvents,
        frames = todaySheetScrim(interrupted.frames, interrupted.widthDp, interrupted.heightDp),
      ),
      retitled(
        fromButton, "foglio-dal-tasto",
        "$source il costruttore di F5B.3 (F5b3ZoomTraceTest.kt) con lo scrim dei fogli di oggi: il foglio nasce dal gruppo " +
          "della barra (la scena di Mail, WWDC25 284 14:07).",
        fromButtonApple,
        listOf(
          linkedMapOf<String, Any?>("name" to "lato-sinistro", "kind" to "value", "path" to "foglio.x", "unit" to "dp",
            "label" to "il lato sinistro del vetro, dal gruppo (Apple 35 pt) al bordo"),
          linkedMapOf<String, Any?>("name" to "bordo-alto", "kind" to "value", "path" to "foglio.y", "unit" to "dp",
            "label" to "il bordo alto del vetro, dal gruppo alla cima",
            "ref" to linkedMapOf<String, Any?>("overshootPct" to 0, "restMs" to listOf(300, 400),
              "source" to "WWDC25 284 f25408: 50% a 100 ms, 92% a 234, 97% a 300, fermo a ~400, senza superare (F5B.3)")),
        ),
        frames = todaySheetScrim(fromButton.frames, fromButton.widthDp, fromButton.heightDp),
      ),
    )
  }
}

class F5b4PresentationTraceTest {
  @Test
  fun `le prove di chiusura di F5B hanno Apple dove c'e', sonde e lo scrim dei fogli di oggi`() {
    val traces = F5b4PresentationTraces.build()
    assertEquals(
      listOf(
        "popover-apre", "popover-torna", "conferma-apre", "conferma-torna", "avviso-apre", "avviso-torna",
        "foglio-sale", "foglio-a-tutta-altezza", "foglio-soglia", "foglio-trascinato-interrotto", "foglio-dal-tasto",
      ),
      traces.map { it.name },
    )
    val withoutApple = setOf("foglio-soglia", "foglio-trascinato-interrotto")
    for (trace in traces) {
      val name = trace.name
      assertEquals("$name: fase", F5b4PresentationTraces.Phase, trace.phase)
      assertTrue("$name: le sonde della curve", (trace.head["probes"] as List<*>).isNotEmpty())
      val apple = (trace.head["apple"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
      if (name in withoutApple) {
        assertTrue("$name: nessun Apple da accostare", apple.isEmpty())
      } else {
        assertTrue("$name: almeno un confronto con Apple", apple.isNotEmpty())
        assertEquals("$name: nomi delle riprese unici", apple.size, apple.map { it["name"] }.toSet().size)
      }
      // Lo scrim dei fogli di oggi non sfoca: nessuno scrim a shader con una catena sfocata.
      for (frame in trace.frames) for (e in frame.elements.filter { it["name"] == "scrim" }) {
        assertEquals("$name a ${frame.tMs}: scrim senza sfocatura", "shape", e["kind"])
      }
      assertTrue("$name: serializzabile", trace.encode().isNotEmpty())
    }
    val byName = traces.associateBy { it.name }

    // La soglia: a meta' altezza la pagina e' chiara, sopra e' scurita, e il passaggio cade quando il foglio lascia il
    // detent medio (il primo fotogramma del dito che sale).
    val threshold = requireNotNull(byName["foglio-soglia"])
    val atRest = threshold.frames.filter { it.tMs <= 0.0 }
    assertTrue("a meta' altezza nessuno scrim", atRest.all { f -> f.elements.none { it["name"] == "scrim" } })
    val end = threshold.frames.last().elements
    assertNotNull("in cima la pagina e' scurita", end.firstOrNull { it["name"] == "scrim" })
    val dimmed = threshold.frames.filter { f -> f.elements.any { it["name"] == "scrim" } }
    assertTrue("la pagina si scurisce dopo la partenza", dimmed.first().tMs > 0.0)

    // Il popover parte dal tasto e torna nel tasto: al primo fotogramma della salita e all'ultimo del ritorno c'e' la faccia
    // della sorgente e non le voci.
    fun layers(frame: GlassTraceFrame) = (frame.elements.single { it["name"] == "pannello" }["content"] as List<*>)
      .filterIsInstance<Map<*, *>>().map { it["name"] }
    assertEquals(listOf("sorgente"), layers(requireNotNull(byName["popover-apre"]).frames.first()))
    assertEquals(listOf("sorgente"), layers(requireNotNull(byName["popover-torna"]).frames.last()))

    GlassTraceOutput.requestedDir(System.getenv("GLASS_F5B4_DIR"))?.let { dir ->
      for (t in traces) t.write(File(dir, "${t.name}/trace.json"))
    }
  }
}
