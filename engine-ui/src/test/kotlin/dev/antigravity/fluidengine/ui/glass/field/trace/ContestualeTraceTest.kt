package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidContextScrimLevel
import dev.antigravity.fluidengine.ui.fluid.FluidContextScrimTuning
import dev.antigravity.fluidengine.ui.fluid.fluidLiftedFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tracce del menu contestuale dopo il grande passo (sessione «contestuale», fase `contestuale`): la riga che si solleva e
 * il menu che nasce come B1 (b) di Apple, in chiaro e in scuro, e il congedo con un tocco fuori. Dal costruttore di F5A.4
 * ([F5a4ContextTraces], ramo `corrected`), che chiama le funzioni dell'engine come le chiama `FluidMenuLayer`.
 *
 * Il riferimento di Apple dell'apertura e' **B1 (b)**, il menu sulla foto (WWDC25 219, 7:15, f13046): e' la nascita che
 * Alessio ha scelto come modello del contestuale. La chiusura resta confrontata con B1 (a), il ritorno del menu dal tasto
 * del telefono (f9187), perche' un contestuale di Apple che si chiude non c'e' nel materiale: e da li' viene anche
 * l'inchiostro del congedo (`fluidContextExitInkSpec`), scelto da Alessio il 2026-11-19.
 */
internal object ContestualeTraces {
  const val Phase = "contestuale"

  /** B1 (b): il ritaglio del menu sulla foto nel video HD, t = 0 al primo fotogramma che si muove. */
  val Photo = linkedMapOf<String, Any?>(
    "name" to "foto",
    "video" to "wwdc2025-219_hd",
    "t0Frame" to 13046,
    "crop" to listOf(500, 150, 910, 767),
    "collage" to "artifacts/liquid-glass/riferimenti/collage/B1_menu_foto_f13036-13075.jpg",
    "note" to "R4 B1 (b), classe B (nessun rallentamento nella trascrizione di 219): la graffetta che diventa menu sulla " +
      "foto, il modello della nascita del contestuale. Il nostro t = 0 e' il click lungo.",
  )

  /** B1 (a): la chiusura del menu dal tasto del telefono. */
  val PhoneClose = linkedMapOf<String, Any?>(
    "name" to "telefono",
    "video" to "wwdc2025-219_hd",
    "t0Frame" to 9187,
    "crop" to listOf(559, 280, 570, 481),
    "collage" to "artifacts/liquid-glass/riferimenti/collage/B1_menu_telefono_chiusura_f9185-9200.jpg",
    "note" to "R4 B1 (a), classe A: il ritorno del menu dal tasto, riferimento affine per il congedo del contestuale.",
  )

  private fun retitled(built: GlassTrace, name: String, note: String, apple: Map<String, Any?>): GlassTrace {
    val head = LinkedHashMap(built.head)
    head["_"] = note
    head["apple"] = listOf(apple)
    return GlassTrace(name, Phase, built.density, built.theme, built.widthDp, built.heightDp, built.frames, built.fps, head)
  }

  fun build(): List<Pair<GlassTrace, ContextBuilt>> {
    val level = FluidContextScrimTuning.Default
    val source = "Sessione contestuale: la riga sollevata e il menu che nasce come B1 (b) di Apple (FluidContextBirth), " +
      "livello di sfondo ${level.name}; numeri dalle funzioni dell'engine, orologio e pagina ricostruiti (F5a4ContextTraces.kt). " +
      "Senza luce del dito ne' banda fra le sezioni."
    val light = F5a4ContextTraces.build(level, dark = false, corrected = true)
    val dark = F5a4ContextTraces.build(level, dark = true, corrected = true)
    return listOf(
      retitled(light.opens, "apre-riga", "$source Apertura dalla riga, chiaro.", Photo) to light,
      retitled(dark.opens, "apre-riga-scuro", "$source Apertura dalla riga, scuro.", Photo) to dark,
      retitled(light.closes, "chiude-tocco", "$source Congedo con un tocco fuori, t = 0 al tocco.", PhoneClose) to light,
      retitled(dark.closes, "chiude-tocco-scuro", "$source Congedo con un tocco fuori, scuro.", PhoneClose) to dark,
    )
  }

  /**
   * I contorni di B1 (b) letti a mano (ritaglio a mezza risoluzione del 4K; menu a riposo 117-612 x 30-750 px): ms, bordo
   * vicino, bordo lontano, larghezza, in frazioni del menu a riposo. Gli stessi di `riferimento-apple.md`.
   */
  val Apple = listOf(
    doubleArrayOf(100.1, 0.039, 0.385, 0.322),
    doubleArrayOf(133.5, 0.069, 0.542, 0.469),
    doubleArrayOf(166.8, 0.100, 0.683, 0.610),
    doubleArrayOf(200.2, 0.128, 0.794, 0.737),
    doubleArrayOf(233.6, 0.153, 0.878, 0.838),
    doubleArrayOf(266.9, 0.139, 0.938, 0.909),
    doubleArrayOf(300.3, 0.121, 0.975, 0.964),
    doubleArrayOf(333.7, 0.093, 1.003, 1.004),
    doubleArrayOf(367.0, 0.072, 1.028, 1.026),
    doubleArrayOf(400.4, 0.053, 1.028, 1.030),
    doubleArrayOf(467.1, 0.024, 1.031, 1.032),
    doubleArrayOf(533.9, 0.006, 1.019, 1.030),
    doubleArrayOf(600.6, 0.000, 1.010, 1.024),
    doubleArrayOf(767.4, 0.000, 1.000, 1.004),
  )
}

class ContestualeTraceTest {

  private fun assertRect(where: String, expected: Rect, actual: Rect, tolerance: Float) {
    assertEquals("$where: left", expected.left, actual.left, tolerance)
    assertEquals("$where: top", expected.top, actual.top, tolerance)
    assertEquals("$where: right", expected.right, actual.right, tolerance)
    assertEquals("$where: bottom", expected.bottom, actual.bottom, tolerance)
  }

  @Suppress("UNCHECKED_CAST")
  private fun scrimAlpha(frame: GlassTraceFrame): Float =
    (frame.elements.firstOrNull { it["name"] == "scrim" }?.get("alpha") as Number?)?.toFloat() ?: 0f

  @Test
  fun theContextMenuIsBornLikeApplesMenuOnThePhotoAndWritesWhenAsked() {
    val traces = ContestualeTraces.build()
    assertEquals(listOf("apre-riga", "apre-riga-scuro", "chiude-tocco", "chiude-tocco-scuro"), traces.map { it.first.name })
    // Si scrivono prima delle verifiche: una traccia che non passa si deve poter guardare sul banco.
    for ((trace, _) in traces) GlassTraceOutput.writeIfRequested(trace)
    // La sagoma vera del menu (con la goccia, come la disegna lo shader) contro i contorni di Apple, fotogramma per
    // fotogramma: la tabella va nel rapporto, le tolleranze dicono «vicino ad Apple», non «uguale».
    val built = traces.first().second
    val panel = built.panel
    val rows = StringBuilder("ms;vicino banco;vicino Apple;lontano banco;lontano Apple;larghezza banco;larghezza Apple\n")
    val measured = ContestualeTraces.Apple.map { a ->
      val ms = a[0]
      val (t, body) = built.menuOpenBounds.last { it.first <= ms + 1e-6 }
      val near = (body.top - panel.top) / panel.height
      val far = (body.bottom - panel.top) / panel.height
      val width = body.width / panel.width
      rows.append("%.1f (%.1f);%.3f;%.3f;%.3f;%.3f;%.3f;%.3f\n".format(ms, t, near, a[1], far, a[2], width, a[3]))
      floatArrayOf(near, far, width)
    }
    println("CONTESTUALE sagoma contro Apple B1 (b)\n$rows")
    for ((trace, built) in traces) {
      val where = trace.name
      assertEquals(ContestualeTraces.Phase, trace.phase)
      assertTrue("$where: serializzabile", trace.encode().isNotEmpty())
      // La card resta quella di F5A.4: parte dalla riga gonfiata dal dito, finisce sulla riga a riposo.
      assertRect("$where: card al click lungo", fluidLiftedFrame(built.row, built.anchorScaleAtLongPress), built.cardAtLongPress, 0.5f)
      assertRect("$where: card alla fine", built.row, built.cardAtEnd, 1f)
      // Le voci dentro il vetro in ogni fotogramma (S9), anche nella nascita lenta col bordo vicino che si stacca.
      println("CONTESTUALE $where: voci fuori dal vetro in apertura ${built.inkOutsideOpen} px a ${built.inkWorstOpenMs} ms (${built.inkWorstOpenWhere}), al ritorno ${built.inkOutsideClose} px")
      assertTrue("$where: voci fuori dal vetro in apertura ${built.inkOutsideOpen} px a ${built.inkWorstOpenMs} ms: ${built.inkWorstOpenWhere}", built.inkOutsideOpen <= 0.6f)
      assertTrue("$where: voci fuori dal vetro al ritorno ${built.inkOutsideClose} px", built.inkOutsideClose <= 0.6f)
      // Lo scrim: sale e scende senza tornare indietro.
      val open = built.opens.frames.filter { it.tMs >= 0.0 }.map(::scrimAlpha)
      for (i in 1 until open.size) assertTrue("$where: scrim in apertura al fotogramma $i", open[i] >= open[i - 1] - 1e-4f)
      val close = built.closes.frames.filter { it.tMs >= 0.0 }.map(::scrimAlpha)
      for (i in 1 until close.size) assertTrue("$where: scrim al ritorno al fotogramma $i", close[i] <= close[i - 1] + 1e-4f)
      // Il costo (debito di F1B): la nascita nuova non aggiunge superfici ne' catture. Per fotogramma al piu' tre elementi
      // (scrim, card, menu), tre catture dello sfondo, una sola a tutto schermo (lo scrim): come prima (sessione
      // contestuale, `costo_tracce.py` sulla traccia di Scelte A: 3, 3, 1).
      for (frame in trace.frames) {
        val captures = frame.elements.count { it.containsKey("capture") || ((it["inputs"] as? Map<*, *>)?.containsKey("content") == true) }
        val fullScreen = frame.elements.count {
          it["kind"] == "shader" && it["w"] == trace.widthDp && it["h"] == trace.heightDp && (it["inputs"] as? Map<*, *>)?.containsKey("content") == true
        }
        assertTrue("$where a ${frame.tMs} ms: ${frame.elements.size} elementi", frame.elements.size <= 3)
        assertTrue("$where a ${frame.tMs} ms: $captures catture", captures <= 3)
        assertTrue("$where a ${frame.tMs} ms: $fullScreen catture a tutto schermo", fullScreen <= 1)
      }
    }

    for ((i, a) in ContestualeTraces.Apple.withIndex()) {
      val (near, far, width) = measured[i].toList()
      assertEquals("bordo lontano a ${a[0]} ms", a[2], far.toDouble(), 0.05)
      // La larghezza: fino a 300 ms la goccia ha le proporzioni di quella di Apple, piu' stretta della frazione del menu.
      if (a[0] >= 300.0) assertEquals("larghezza a ${a[0]} ms", a[3], width.toDouble(), 0.05)
      else assertTrue("larghezza a ${a[0]} ms: $width", width <= a[3] + 0.05)
      assertEquals("bordo vicino a ${a[0]} ms", a[1], near.toDouble(), 0.04)
    }
    // Prima della nascita il menu era pieno a ~150 ms: ora a 100 ms e' meno di meta' altezza, come Apple (0,35).
    val at100 = built.menuOpenBounds.last { it.first <= 100.0 }.second
    assertTrue("a 100 ms il menu e' alto ${at100.height / panel.height}", at100.height / panel.height < 0.5f)
  }
}
