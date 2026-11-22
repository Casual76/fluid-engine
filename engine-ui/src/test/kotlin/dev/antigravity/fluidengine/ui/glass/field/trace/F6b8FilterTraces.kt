package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.antigravity.fluidengine.ui.fluid.FluidContextFocusEasing
import dev.antigravity.fluidengine.ui.fluid.FluidContextFocusMillis
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOrder
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.fluidGroupBirthSlab
import dev.antigravity.fluidengine.ui.fluid.FluidPressTransform
import dev.antigravity.fluidengine.ui.fluid.fluidChipMenuTrigger
import dev.antigravity.fluidengine.ui.fluid.fluidContextBirthInk
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLensBlurDp
import dev.antigravity.fluidengine.ui.fluid.fluidMenuReverses
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6B.8, le tracce del banco a 120 Hz: il menu che nasce dal vetro di un chip della fila dei filtri **come il menu dal
 * gruppo della barra** (correzione di Alessio del 22 novembre 2026; F5A.2, R4 B1 b), col costruttore di F5A
 * ([buildMenuTrace] con `grouped`: le funzioni dell'engine, l'orologio della nascita del gruppo, la goccia, le voci nella
 * lente). La capsula e' il chip ([MenuScene.groupDp]), il tasto premuto la goccia al suo centro ([fluidChipMenuTrigger]),
 * come fa `rememberFluidChipMenu`.
 *
 * - `pillole-menu-200`: la fila a testo «200%» su 360 dp, come la compone l'engine (`F6b8FilterFramesTest`): «Aperti ora»
 *   resta nella fila, «Accessibile» e «Wi-Fi» (accese) stanno nel tasto «Filtri · 2 ▾», che si apre al tocco nel menu con
 *   le due pillole e la spunta. Righe e testo alla scala vera di Android a 200% ([Effective200]).
 * - `pillola-periodo-menu`: la pillola «Periodo ▾» a testo normale che si apre nei suoi valori («Qualsiasi», «7 giorni»,
 *   «30 giorni», «Altro»).
 * - `…-congedo`: le stesse, il congedo a menu fermo (t = 0 alla richiesta), il vetro che rientra nel chip.
 *
 * I bersagli (piano, «Bersagli», pillole → menu e il menu dal gruppo): si stringe al 60% in ~33 ms; aperto in 330–370 ms
 * con un solo superamento (+5,7%, scelta di Alessio); voci nella lente, leggibili a 170–200 ms; chiusura in ~233 ms. La
 * tabella delle misure accanto ai bersagli la stampa il test (`confronto-f5a.txt`) e la riporta il rapporto.
 *
 * Le tracce si scrivono sempre in `build/reports/f6b-8/tracce/<nome>/trace.json` (si rendono con `glassmotion.py`), e
 * anche sotto `GLASS_TRACE_DIR` se la variabile lo chiede.
 */
internal object F6b8FilterTraces {
  const val Phase = "f6b-8"

  /** Il telefono di prova: 360 × 780 dp a densita' 3, come un telefono a 1080 px; 120 Hz. */
  private val Host = Size(360f, 780f)
  const val Density = 3f
  private val CanvasOrigin = Offset(0f, 60f)
  private const val CanvasW = 360f
  private const val CanvasH = 460f

  /**
   * La fila vera a 200% su 360 dp (`F6b8FilterFramesTest`, `FluidFilterChips` composta in Robolectric e misurata sui
   * fotogrammi a densita' 2): «Aperti ora» resta, da 16,5 a 169 dp, e «Accessibile» e «Wi-Fi» (accese) vanno nel tasto
   * «Filtri · 2 ▾», da 178,5 a 331 dp; alti 49 dp.
   */
  val KeyAt200 = Rect(178.5f, 116f, 331.5f, 165f)

  /**
   * La scala vera del testo a «200%»: dal 14 di Android la scala e' non lineare, e i corpi di 15–17 sp crescono di ~1,75×
   * (sui fotogrammi: l'etichetta del tasto ~1,85×, la riga del menu 64–65 dp cioe' 23 sp × 1,75 + 24). La traccia usa
   * questa, non 2.
   */
  const val Effective200 = 1.75f
  val NeighbourAt200 = Rect(16.5f, 116f, 169f, 165f)

  /**
   * A 100%: «Aperti ora» da 16,5 a 114,5 dp (fotogrammi), e la pillola «Periodo ▾» dopo di lei a 8 dp: etichetta (~52 dp)
   * + 5 + 15 dp di freccia + 2 × 14 dp. Nella traccia la faccia e' l'etichetta con la freccia; il simbolo in testa del
   * preset (l'orologio) non c'e'.
   */
  val NeighbourAt100 = Rect(16.5f, 116f, 114.5f, 164f)
  val PeriodPill = Rect(122.5f, 116f, 122.5f + 100f, 164f)

  /** Il chip vicino, fermo: vetro dei tasti a riposo con la sua etichetta, sulla tela. */
  private fun neighbour(rect: Rect, label: String, fontScale: Float): (Offset, TodayPalette) -> List<Map<String, Any?>> =
    { origin, palette ->
      val px = rect.scaled(Density)
      val size = 15f * fontScale
      val text = linkedMapOf<String, Any?>(
        "name" to "etichetta",
        "x" to round3((px.left - origin.x) / Density),
        "y" to round3((px.top - origin.y) / Density),
        "w" to round3(rect.width),
        "h" to round3(rect.height),
        "items" to listOf(
          linkedMapOf(
            "kind" to "text",
            "text" to label,
            "x" to 14.0,
            "y" to round3(rect.height / 2f + size * 0.35f),
            "sizeDp" to round3(size),
            "color" to glassColor(palette.onSurface),
          ),
        ),
      )
      listOf(
        liquidButtonElement(
          name = "vicino",
          rest = px,
          transform = FluidPressTransform.Identity,
          pressed = 0f,
          origin = origin,
          density = Density,
          content = listOf(text),
          shadow = true,
        ),
      )
    }

  private fun head(note: String): (Rect) -> Map<String, Any?> = { panel ->
    val midX = round3(panel.center.x)
    val midY = round3(panel.center.y)
    linkedMapOf(
      "_" to note,
      "palette" to "engine",
      "corner" to "engine",
      "background" to linkedMapOf("kind" to "color", "color" to "#F2F2F7"),
      "page" to listOf(
        // Il campo di ricerca sopra la fila, e la lista sotto: qualcosa da rifrangere.
        linkedMapOf("kind" to "background", "x" to 16, "y" to 2, "w" to 328, "h" to 44,
          "background" to linkedMapOf("kind" to "synthetic", "scaleDp" to 40, "detail" to 0.3,
            "palette" to listOf("#E5E5EA", "#E1E1E6", "#E8E8ED"))),
        linkedMapOf("kind" to "text", "text" to "Cerca", "x" to 52, "y" to 30, "sizeDp" to 17),
        linkedMapOf("kind" to "background", "x" to 16, "y" to 130, "w" to 328, "h" to 300,
          "background" to linkedMapOf("kind" to "synthetic", "scaleDp" to 46, "detail" to 0.5,
            "palette" to listOf("#DCE7F5", "#F5E6D3", "#2F5D34", "#E9E9E9", "#7C6BD0", "#D2D2D2"))),
      ),
      "events" to listOf(
        linkedMapOf("t" to 33.4, "kind" to "mark", "label" to "bersaglio: al 60% a 33 ms"),
        linkedMapOf("t" to 333.7, "kind" to "mark", "label" to "bersaglio: aperto a 330–370 ms"),
      ),
      "probes" to listOf(
        linkedMapOf(
          "name" to "bordo-basso",
          "kind" to "edge",
          "line" to "v:$midX:0:$CanvasH",
          "which" to "last",
          "percentOf" to round3(panel.height),
          "label" to "il bordo lontano del vetro (in basso), dai pixel; in % dell'altezza del pannello",
          "ref" to linkedMapOf("restMs" to listOf(330, 370), "source" to "F5A.2 e R4 B1 (b), il menu dal gruppo"),
        ),
        linkedMapOf(
          "name" to "larghezza",
          "kind" to "width",
          "line" to "h:$midY:0:$CanvasW",
          "label" to "la larghezza del vetro a meta' pannello, dai pixel",
          "ref" to linkedMapOf("overshootPct" to 5.7, "restMs" to listOf(330, 370),
            "source" to "F5A.2 (+5,7%, scelta di Alessio) e R4 B1 (b)"),
        ),
      ),
    )
  }

  /** Una scena: il chip [capsule] e' la capsula, la goccia al suo centro il tasto premuto. */
  private fun scene(
    name: String,
    capsule: Rect,
    fontScale: Float,
    faceText: String,
    items: List<MenuItemSpec>,
    note: String,
    still: (Offset, TodayPalette) -> List<Map<String, Any?>>,
  ): MenuScene = MenuScene(
    name = name,
    fps = 120,
    density = Density,
    hostDp = Host,
    anchorDp = fluidChipMenuTrigger(capsule),
    canvasOriginDp = CanvasOrigin,
    canvasWidthDp = CanvasW,
    canvasHeightDp = CanvasH,
    // Il tocco: nessun click lungo.
    longPressMs = 0.0,
    preambleMs = 0.0,
    // Si apre verso il basso dal chip in alto: `Automatic` dal tasto tiene l'ordine scritto.
    items = items.let { if (fluidMenuReverses(FluidMenuOrder.Automatic, opensUpward = false, fromButton = true)) it.asReversed() else it },
    faceIcon = "fluid:expand_more|circle",
    faceIconSizeDp = 15f,
    labelDpPerChar = 9f * fontScale,
    maxMs = 1800.0,
    tailFrames = 6,
    head = head(note),
    still = still,
    fontScale = fontScale,
    faceText = faceText,
    faceTextSizeDp = 15f * fontScale,
    faceTrailingIcon = "fluid:expand_more|circle",
    groupDp = capsule,
  )

  private const val Check = "fluid:check|circle"

  /** Le due scene, con la loro capsula in dp. */
  fun scenes(): List<Pair<MenuScene, Rect>> {
    val source = "F6B.8: il menu Liquid dal vetro di un chip come dal gruppo della barra (FluidChipMenuSource, B1 b), " +
      "numeri dalle funzioni dell'engine, orologio e assemblaggio di F5A (OggiMenuApertura.kt). Chiaro, 120 Hz, densita' 3."
    val menu200 = scene(
      name = "pillole-menu-200",
      capsule = KeyAt200,
      fontScale = Effective200,
      faceText = "Filtri · 2",
      items = listOf(
        MenuItemSpec("Accessibile", Check),
        MenuItemSpec("Wi-Fi", Check),
      ),
      note = "$source A testo 200% «Accessibile» e «Wi-Fi» stanno nel tasto «Filtri · 2»: il tocco lo apre nel menu con " +
        "le stesse pillole (nel menu ci sono solo quelle che contiene).",
      still = neighbour(NeighbourAt200, "Aperti ora", Effective200),
    )
    val period = scene(
      name = "pillola-periodo-menu",
      capsule = PeriodPill,
      fontScale = 1f,
      faceText = "Periodo",
      items = listOf(
        MenuItemSpec("Qualsiasi", Check),
        MenuItemSpec("7 giorni", "", startsSection = true),
        MenuItemSpec("30 giorni", ""),
        MenuItemSpec("Altro", ""),
      ),
      note = "$source La pillola del periodo si apre nei suoi valori dal suo vetro.",
      still = neighbour(NeighbourAt100, "Aperti ora", 1f),
    )
    return listOf(menu200 to KeyAt200, period to PeriodPill)
  }

  /** L'apertura (t = 0 al tocco) e il congedo a menu fermo (t = 0 alla richiesta), per ogni scena. */
  fun build(): List<Pair<MenuBuilt, Rect>> = scenes().flatMap { (scene, capsule) ->
    listOf(
      buildMenuTrace(scene, dress = TraceDress.Liquid, phase = Phase, unified = true,
        variant = MenuTraceVariant(grouped = true, pointOfLight = true, corrected = true)) to capsule,
      buildMenuTrace(scene, dress = TraceDress.Liquid, name = "${scene.name}-congedo", phase = Phase, unified = true,
        variant = MenuTraceVariant(grouped = true, corrected = true, closingAtMs = 900, alignToClose = true)).withoutEvents() to capsule,
    )
  }

  /** Il congedo parte a t = 0 dalla richiesta: i segni dell'apertura (33 e 333,7 ms) non sono suoi. */
  private fun MenuBuilt.withoutEvents(): MenuBuilt {
    val t = trace
    val head = LinkedHashMap(t.head)
    head.remove("events")
    val stripped = GlassTrace(t.name, t.phase, t.density, t.theme, t.widthDp, t.heightDp, t.frames, t.fps, head)
    return MenuBuilt(stripped, panelPx, pressedPx, ringFrames, integratedFrames, openBounds, closeBounds, born, rowShift)
  }

  /** Bordo vicino (in alto), lontano (in basso), sinistro e destro del corpo in frazioni del pannello che si apre in giu'. */
  fun fractions(panel: Rect, body: Rect): DoubleArray = doubleArrayOf(
    ((body.top - panel.top) / panel.height).toDouble(),
    ((body.bottom - panel.top) / panel.height).toDouble(),
    ((body.left - panel.left) / panel.width).toDouble(),
    ((body.right - panel.left) / panel.width).toDouble(),
  )

  /** La sfocatura delle voci a [ms] dalla nascita del gruppo (come il contestuale: a fuoco a 334 ms), in dp. */
  fun itemBlurDp(ms: Double): Float =
    fluidMenuLensBlurDp(FluidContextFocusEasing.transform((ms / FluidContextFocusMillis).toFloat().coerceIn(0f, 1f)))
}

/** Le misure di un'apertura (e del suo congedo) accanto ai bersagli. */
internal class F6b8Measures(
  val shrinkDrawn33: Float,
  val shrinkAsked33: Float,
  val widthFull: Double,
  val full: Double,
  val widthOvershoot: Float,
  val widthPeakMs: Double,
  val farOvershoot: Double,
  val overshoots: Int,
  val settled: Double,
  val readable: Double,
  val back: Double,
)

/**
 * Le misure di [open] (e del congedo [close]) per la capsula [capsule], in pixel dell'host. La sagoma chiesta a 33 ms e'
 * quella del profilo del gruppo ([fluidGroupBirthSlab], senza l'anticipo della goccia); il vetro disegnato e' l'anello
 * della goccia, che la insegue in ritardo.
 */
internal fun f6b8Measures(open: MenuBuilt, close: MenuBuilt?, capsule: Rect): F6b8Measures {
  val panel = open.panelPx
  val bounds = open.openBounds
  val at33 = bounds.last { it.first <= 33.4 + 1e-6 }.second
  val radius = FluidRadius.Group.value * open.trace.density
  val asked = fluidGroupBirthSlab(capsule, open.pressedPx, panel, radius, 33.4f, leadForDrop = false)?.frame ?: at33
  val widths = bounds.map { it.second.width / panel.width }
  val widthFull = bounds.firstOrNull { it.second.width >= panel.width * 0.99f }?.first ?: -1.0
  val full = bounds.firstOrNull { (_, r) -> r.width >= panel.width * 0.99f && r.height >= panel.height * 0.99f }?.first ?: -1.0
  val peak = widths.withIndex().maxBy { it.value }
  // Gli episodi sopra il pannello: quante volte la larghezza ci sale sopra (oltre lo 0,2%).
  val overshoots = widths.zipWithNext().count { (a, b) -> a <= 1.002f && b > 1.002f }
  val settled = bounds.indexOfLast { (_, r) ->
    abs(r.width / panel.width - 1f) > 0.01f || abs(r.height / panel.height - 1f) > 0.01f
  }.let { i -> bounds.getOrNull(i + 1)?.first ?: 0.0 }
  var readable = -1.0
  var ms = 0.0
  while (ms <= 600.0) {
    if (F6b8FilterTraces.itemBlurDp(ms) <= 1f) {
      readable = ms
      break
    }
    ms += 1.0
  }
  val home = close?.closeBounds?.filter { it.first >= 0.0 }.orEmpty()
  val back = home.indexOfLast { (_, r) ->
    abs(r.width - capsule.width) > capsule.width * 0.03f || abs(r.height - capsule.height) > capsule.height * 0.03f
  }.let { i -> home.getOrNull(i + 1)?.first ?: -1.0 }
  return F6b8Measures(
    shrinkDrawn33 = at33.width / capsule.width,
    shrinkAsked33 = asked.width / capsule.width,
    widthFull = widthFull,
    full = full,
    widthOvershoot = peak.value - 1f,
    widthPeakMs = bounds[peak.index].first,
    // Il bordo lontano: in basso per la fila (il menu scende), in alto per la barra in basso (il menu sale).
    farOvershoot = bounds.maxOf { (_, r) ->
      if (panel.center.y < open.pressedPx.center.y) ((panel.bottom - r.top) / panel.height).toDouble()
      else ((r.bottom - panel.top) / panel.height).toDouble()
    } - 1.0,
    overshoots = overshoots,
    settled = settled,
    readable = readable,
    back = back,
  )
}

class F6b8FilterTracesTest {
  @Test
  fun `il menu nasce dal vetro del chip come il menu dal gruppo, e si misura accanto ai bersagli`() {
    val built = F6b8FilterTraces.build()
    assertEquals(
      listOf("pillole-menu-200", "pillole-menu-200-congedo", "pillola-periodo-menu", "pillola-periodo-menu-congedo"),
      built.map { it.first.trace.name },
    )
    val out = File("build/reports/f6b-8/tracce")
    val report = StringBuilder()
    val d = F6b8FilterTraces.Density
    val measured = ArrayList<Pair<String, F6b8Measures>>()
    for ((b, _) in built) {
      val trace = b.trace
      // Si scrivono prima delle verifiche: una traccia che non passa si deve poter guardare sul banco.
      trace.write(File(File(out, trace.name), "trace.json"))
      GlassTraceOutput.writeIfRequested(trace)
      assertTrue("${trace.name}: la nascita di Apple (il gruppo)", b.born)
      assertTrue("${trace.name}: la goccia", b.ringFrames > 0)
      assertEquals(120, trace.fps)
      // Il costo: il menu e il chip vicino fermo per fotogramma, nessuna superficie in piu'.
      for (frame in trace.frames) assertTrue("${trace.name} a ${frame.tMs} ms", frame.elements.size <= 2)
    }
    for ((b, capsuleDp) in built.filter { !it.first.trace.name.endsWith("-congedo") }) {
      val trace = b.trace
      val panel = b.panelPx
      val capsule = capsuleDp.scaled(d)
      val first = b.openBounds.first().second
      assertEquals("${trace.name}: parte dalla capsula del chip", capsule.width, first.width, capsule.width * 0.05f)

      val table = StringBuilder("ms;alto;basso;sinistro;destro;larghezza/capsula;larghezza/pannello;altezza/pannello;sfocatura voci dp;voci dentro\n")
      for ((t, body) in b.openBounds) {
        if (t > 900.0) break
        val f = F6b8FilterTraces.fractions(panel, body)
        table.append("%.1f;%.3f;%.3f;%.3f;%.3f;%.3f;%.3f;%.3f;%.2f;%.2f\n".format(t, f[0], f[1], f[2], f[3],
          body.width / capsule.width, body.width / panel.width, body.height / panel.height,
          F6b8FilterTraces.itemBlurDp(t), fluidContextBirthInk(t.toFloat())))
      }
      File(File(out, trace.name), "sagoma.csv").apply { parentFile.mkdirs() }.writeText(table.toString())

      // Contro Apple, la graffetta (B1 b): bordo vicino, lontano, larghezza, come il gruppo della barra (TastoTraceTest).
      val apple = StringBuilder("ms;vicino banco;vicino Apple;lontano banco;lontano Apple;larghezza banco;larghezza Apple\n")
      for (a in ContestualeTraces.Apple) {
        val (t, body) = b.openBounds.lastOrNull { it.first <= a[0] + 1e-6 } ?: continue
        val f = F6b8FilterTraces.fractions(panel, body)
        apple.append("%.1f (%.1f);%.3f;%.3f;%.3f;%.3f;%.3f;%.3f\n".format(a[0], t, f[0], a[1], f[1], a[2], f[3] - f[2], a[3]))
      }
      File(File(out, trace.name), "contro-b1b.csv").writeText(apple.toString())

      val close = requireNotNull(built.firstOrNull { it.first.trace.name == "${trace.name}-congedo" }).first
      measured += trace.name to f6b8Measures(b, close, capsule)
    }

    // Il riferimento: il menu dal gruppo della barra di F5A (tre tasti, quello di destra premuto), stesso costruttore.
    val automatic = fluidMenuReverses(FluidMenuOrder.Automatic, opensUpward = true, fromButton = true)
    val barScene = OggiMenuApertura.scene(
      density = 1.5f, lensDp = FluidMetrics.GlassButtonLens.value, iconDp = FluidMetrics.BarIconSize.value, reversed = automatic,
    )
    val barOpen = buildMenuTrace(barScene, dress = TraceDress.Liquid, name = "riferimento-gruppo", phase = F6b8FilterTraces.Phase,
      unified = true, variant = MenuTraceVariant(grouped = true, pointOfLight = true, corrected = true))
    val barClose = buildMenuTrace(barScene, dress = TraceDress.Liquid, name = "riferimento-gruppo-congedo",
      phase = F6b8FilterTraces.Phase, unified = true,
      variant = MenuTraceVariant(grouped = true, corrected = true, closingAtMs = 900, alignToClose = true))
    val shift = FluidMetrics.GroupItemPitch.value * 2f * 1.5f
    val barCapsule = barOpen.pressedPx.let { Rect(it.left - shift, it.top, it.right, it.bottom) }
    measured += "menu dal gruppo della barra (F5A)" to f6b8Measures(barOpen, barClose, barCapsule)

    val names = measured.map { it.first }
    val m = measured.map { it.second }
    fun row(label: String, target: String, value: (F6b8Measures) -> String) {
      report.append("| ").append(label).append(" | ").append(m.joinToString(" | ") { value(it) }).append(" | ").append(target).append(" |\n")
    }
    report.append("| grandezza | ").append(names.joinToString(" | ")).append(" | bersaglio |\n")
    report.append("|---|").append(names.joinToString("") { "---|" }).append("---|\n")
    row("larghezza a 33 ms sulla capsula: sagoma chiesta", "si stringe al 60% in ~33 ms (B1 b: 0,63-0,67)") { "%.2f".format(it.shrinkAsked33) }
    row("larghezza a 33 ms sulla capsula: vetro disegnato (la goccia insegue)", "") { "%.2f".format(it.shrinkDrawn33) }
    row("larghezza del pannello raggiunta", "aperto in 330-370 ms") { "%.0f ms".format(it.widthFull) }
    row("pieno (larghezza e altezza al 99%)", "") { "%.0f ms".format(it.full) }
    row("superamento della larghezza", "uno solo, +5,7%") { "+%.1f%% a %.0f ms".format(it.widthOvershoot * 100f, it.widthPeakMs) }
    row("superamenti (volte sopra il pannello)", "uno") { "${it.overshoots}" }
    row("superamento del bordo lontano", "(B1 b: +3% a 367-467 ms)") { "+%.1f%%".format(it.farOvershoot * 100.0) }
    row("entro l'1% del pannello, per sempre", "(B1 b: fermo a 770-868 ms)") { "%.0f ms".format(it.settled) }
    row("voci sotto 1 dp di sfocatura", "leggibili a 170-200 ms (B1 b: 234-267)") { "%.0f ms".format(it.readable) }
    row("congedo: rientrato nella capsula (entro il 3%)", "~233 ms") { "%.0f ms".format(it.back) }
    println("F6B.8 misure accanto ai bersagli \n$report")

    for ((name, mm) in measured) {
      // Il gruppo: la capsula si stringe e il pannello si apre una volta sola, come il menu dal gruppo della barra.
      assertTrue("$name: a 33 ms la sagoma chiesta si stringe (${mm.shrinkAsked33})", mm.shrinkAsked33 in 0.5f..0.8f)
      assertTrue("$name: un superamento solo (${mm.overshoots})", mm.overshoots <= 1)
      assertTrue("$name: rientrato entro 400 ms (${mm.back})", mm.back in 0.0..400.0)
    }
    // La fila e la barra fanno lo stesso viaggio: la stessa messa a fuoco delle voci e lo stesso congedo, a un fotogramma.
    val bar = measured.last().second
    for ((name, mm) in measured.dropLast(1)) {
      assertEquals("$name: la messa a fuoco delle voci e' quella del gruppo", bar.readable, mm.readable, 1.0)
      assertEquals("$name: il congedo e' quello del gruppo", bar.back, mm.back, 34.0)
    }
    File(out, "confronto-f5a.txt").writeText(report.toString())
  }
}
