package dev.antigravity.fluidengine.ui.glass.field.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tracce «di oggi» (F0.5): tre viaggi dell'engine com'e' adesso, per provare la catena traccia → video del
 * banco del movimento e per avere il «prima» da mettere accanto al «dopo».
 *
 * Ogni test costruisce la sua traccia e controlla i fatti che la rendono credibile (parte dove deve partire,
 * arriva dove deve arrivare, la goccia ha girato davvero); poi, **solo se** `GLASS_TRACE_DIR` c'e', la scrive
 * (vedi [GlassTraceOutput]). Nei giri normali dei test non resta nessun file.
 */
class GlassTraceExportTest {

  @Test
  fun menuOpeningStartsOnThePressedButtonAndRestsOnThePanel() {
    val built = OggiMenuApertura.build()
    val trace = built.trace
    val d = trace.density

    // Il preambolo e' il tasto premuto (glassSurface); da t = 0 la superficie del menu.
    val preamble = trace.frames.filter { it.tMs < 0 }
    assertTrue(preamble.isNotEmpty())
    assertTrue(preamble.all { it.elements.single()["name"] == "tasto" })

    // A t = 0 la sagoma e' la capsula sul tasto premuto, non quella a riposo.
    val first = piece(trace.frames.first { it.tMs == 0.0 }, "menu")
    assertEquals((built.pressedPx.width / d).toDouble(), first.getValue("w") as Double, 0.002)
    assertEquals((built.pressedPx.height / d).toDouble(), first.getValue("h") as Double, 0.002)

    // La goccia ha girato: fotogrammi ad anello e passi integrati.
    assertTrue("nessun fotogramma ad anello", built.ringFrames > 0)
    assertTrue("la goccia non ha integrato", built.integratedFrames > 0)

    // L'ultimo fotogramma e' il pannello fermo, coi raggi del gruppo (22 dp).
    val last = piece(trace.frames.last(), "menu")
    assertEquals((built.panelPx.width / d).toDouble(), last.getValue("w") as Double, 0.002)
    assertEquals((built.panelPx.height / d).toDouble(), last.getValue("h") as Double, 0.002)
    @Suppress("UNCHECKED_CAST")
    val radii = last.getValue("radius") as List<Double>
    radii.forEach { assertEquals(22.0, it, 0.002) }

    // Le voci sono ritagliate dal `clip` dello strato (TRACE.md), non da un effetto a maschera.
    assertVociClipped(trace)

    // Due riprese di Apple, ognuna col suo nome.
    @Suppress("UNCHECKED_CAST")
    val apple = trace.head.getValue("apple") as List<Map<String, Any?>>
    assertEquals(listOf("telefono", "foto"), apple.map { it["name"] })

    finish(trace)
  }

  @Test
  fun galleryMenuIsTheTabletOneWithTheStarAndFourItems() {
    val built = OggiMenuAperturaGalleria.build()
    val trace = built.trace
    val d = trace.density
    assertEquals(120, trace.fps)
    assertEquals(2.125f, d, 0f)

    // Il pannello: 252 dp di serie, alto 212 (tre righe, la sezione, una riga), sopra l'ancora (la stella).
    assertEquals(252.0, (built.panelPx.width / d).toDouble(), 0.01)
    assertEquals(212.0, (built.panelPx.height / d).toDouble(), 0.01)
    assertEquals(OggiMenuAperturaGalleria.StarDp.top.toDouble(), (built.panelPx.top / d).toDouble(), 0.01)

    // Il preambolo comincia prima del contatto (due fotogrammi fermi) e copre i 500 ms del click lungo.
    assertTrue(trace.frames.first().tMs <= -500.0 - 2 * 1000.0 / 120 + 1e-6)
    val beforeTouch = trace.frames.first()
    assertEquals(listOf("puntini", "tasto"), beforeTouch.elements.map { it["name"] })
    assertEquals(44.0, buttonPiece(beforeTouch, "tasto").getValue("w") as Double, 0.002)

    assertTrue(built.ringFrames > 0)
    assertVociClipped(trace)
    val last = piece(trace.frames.last(), "menu")
    assertEquals(252.0, last.getValue("w") as Double, 0.002)

    finish(trace)
  }

  @Test
  fun barPiecesTravelFromTheRootActionsToTheChildOnes() {
    val built = OggiBarraPagine.build()
    val trace = built.trace

    // Tre azioni contro una piu' il tasto indietro: quattro coppie, due delle quali con un seme.
    assertEquals(4, built.pairs.size)
    assertEquals(2, built.pairs.count { it.first.width < 2f })
    assertEquals(1, built.pairs.count { it.second.width < 2f })

    // In un push il progresso dei pezzi va da 1 (i tasti della radice) a 0 (quelli della figlia), senza
    // tornare indietro: la molla delle pagine e' critica e `barMorph` e' monotona.
    assertEquals(1f, built.morphs.first(), 0f)
    for (i in 1 until built.morphs.size) assertTrue(built.morphs[i] <= built.morphs[i - 1] + 1e-6f)
    assertTrue(built.morphs.last() < 0.01f)

    // Prima i tasti della radice, alla fine quelli della figlia, ognuno col suo vetro.
    assertEquals(listOf("a-preferito", "a-condividi", "a-aggiungi"), trace.frames.first().elements.map { it["name"] })
    assertEquals(listOf("b-aggiungi", "b-indietro"), trace.frames.last().elements.map { it["name"] })
    assertTrue(built.settledFrame > 0)

    finish(trace)
  }

  @Test
  fun galleryBarGoesFromSearchToBackShareAndStar() {
    val built = OggiBarraPagineGalleria.build()
    val trace = built.trace
    assertEquals(120, trace.fps)

    // Una azione contro due piu' il tasto indietro: tre coppie; la stella sulla lente di «Cerca», gli altri due
    // dal seme.
    assertEquals(3, built.pairs.size)
    assertEquals(2, built.pairs.count { it.second.width < 2f })
    assertEquals(1f, built.morphs.first(), 0f)
    assertTrue(built.morphs.last() < 0.01f)

    assertEquals(listOf("a-cerca"), trace.frames.first().elements.map { it["name"] })
    assertEquals(listOf("b-condividi", "b-preferito", "b-indietro"), trace.frames.last().elements.map { it["name"] })
    // La lente di «Cerca»: a W - 52 dp, alta 44 sotto la barra di stato (64 px).
    val search = buttonPiece(trace.frames.first(), "a-cerca")
    assertEquals((2560 / 2.125 - 52), search.getValue("x") as Double, 0.002)
    assertEquals(64 / 2.125, search.getValue("y") as Double, 0.002)

    finish(trace)
  }

  @Test
  fun galleryTapFollowsTheSwipeAndReleasesAt567() {
    val built = OggiTastoGalleria.build()
    val trace = built.trace
    assertEquals(120, trace.fps)
    assertEquals(69, OggiTastoGalleria.ReleaseFrame)

    val widths = built.widthsDp
    assertEquals(44f, widths.first(), 0.001f)
    assertTrue("picco ${widths.max()}", widths.max() > 66f)
    assertEquals(44f, widths.last(), 0.05f)

    // A riposo la lente e' quella di «Cerca»: 2560 px - 52 dp, nella tela che comincia a 2322 px.
    val rest = buttonPiece(trace.frames.first(), "tasto")
    assertEquals(2560 / 2.125 - 52 - 2322 / 2.125, rest.getValue("x") as Double, 0.002)

    finish(trace)
  }

  @Test
  fun buttonSwellsPastOneAndAHalfAndPopsBelowRestOnRelease() {
    val built = OggiTasto.build()
    val widths = built.widthsDp

    assertEquals(44f, widths.first(), 0.001f)
    // ExpandRound: x1,5 con la molla di LibrePods, che sorpassa (0,5 di smorzamento).
    assertTrue("picco ${widths.max()}", widths.max() > 66f)
    // Il «pop» del ritorno: sotto il riposo, poi di nuovo a 44.
    assertTrue("nessun rimbalzo sotto il riposo", widths.min() < 44f)
    assertEquals(44f, widths.last(), 0.05f)

    finish(built.trace)
  }

  /** Il pezzo di un tasto a riposo o premuto (`todayButtonElement`): uno solo. */
  private fun buttonPiece(frame: GlassTraceFrame, element: String): Map<String, Any?> = piece(frame, element)

  /** In ogni fotogramma con le voci, lo strato porta `clip` e nessun `effect`; e in qualcuno il ritaglio c'e'. */
  private fun assertVociClipped(trace: GlassTrace) {
    var clipped = 0
    for (frame in trace.frames) {
      for (el in frame.elements) {
        @Suppress("UNCHECKED_CAST")
        val layers = (el["content"] as? List<Map<String, Any?>>).orEmpty()
        val voci = layers.singleOrNull { it["name"] == "voci" } ?: continue
        assertTrue("le voci hanno ancora un effetto", "effect" !in voci)
        if ("clip" in voci) clipped++
      }
    }
    assertTrue("nessun fotogramma con le voci ritagliate", clipped > 0)
  }

  private fun piece(frame: GlassTraceFrame, element: String): Map<String, Any?> {
    val el = frame.elements.single { it["name"] == element }
    @Suppress("UNCHECKED_CAST")
    val pieces = el.getValue("pieces") as List<Map<String, Any?>>
    return pieces.single()
  }

  /** Lo scrittore deve accettarla (numeri finiti, nomi validi), e la scrive se glielo chiedono. */
  private fun finish(trace: GlassTrace) {
    assertTrue(trace.encode().isNotEmpty())
    GlassTraceOutput.writeIfRequested(trace)?.let { println("traccia scritta: $it (${trace.frames.size} fotogrammi)") }
  }
}
