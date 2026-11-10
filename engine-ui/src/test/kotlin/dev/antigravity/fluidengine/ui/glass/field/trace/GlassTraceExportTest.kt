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
