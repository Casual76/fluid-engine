package dev.antigravity.fluidengine.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La riga del cursore sopra la barra di formattazione: i passi di [CaretBarAvoidance] su pagine finte.
 *
 * Una pagina finta e' una fila di contenitori che scorrono, dal piu' interno al piu' esterno, ognuno col suo
 * bordo basso e quanto contenuto ha ancora sotto quel bordo; portare in vista un rettangolo li fa scorrere
 * come fa Compose: prima il piu' interno, quanto serve e quanto puo', poi chi lo contiene, **a cui il
 * rettangolo arriva come se chi sta dentro avesse scorso tutto il necessario**. E' il motivo della coda: un
 * rettangolo che sporge sotto la fine del contenuto farebbe scorrere la pagina di una misura sbagliata.
 *
 * Quello che si prova: la riga finisce sopra la barra, **appena** sopra, in una richiesta; non si muove
 * niente quando la riga non e' coperta; la coda si tiene solo quando la riga coperta e' vicina alla fine.
 */
class FluidRichTextCaretAvoidanceTest {

  /**
   * Un contenitore che scorre: il bordo basso, quanto contenuto ha sotto il bordo ([range]: quanto puo'
   * ancora scorrere) e quanto vuoto ha fra la fine del contenuto e il bordo ([slack]: un contenuto piu'
   * corto del contenitore, che non scorre finche' non lo riempie).
   */
  private class Scroller(var bottom: Float, var range: Float, var slack: Float = 0f)

  private class Page(
    var fieldBottom: Float,
    var caretBottom: Float,
    val barTop: Float,
    /** Dal piu' interno al piu' esterno. */
    val scrollers: List<Scroller>,
    var fieldTop: Float = 300f,
    val caretHeight: Float = 50f,
    val windowHeight: Float = 2400f,
    val gap: Float = 10f,
  ) {
    val line: Float get() = barTop - gap
    var requests = 0
    var tail = 0f

    fun bringIntoView(allowance: Float) {
      requests++
      var rectBottom = caretBottom + allowance
      scrollers.forEachIndexed { index, scroller ->
        val wanted = (rectBottom - scroller.bottom).coerceAtLeast(0f)
        val delta = wanted.coerceAtMost(scroller.range)
        scroller.range -= delta
        // A chi sta fuori il rettangolo arriva dove sarebbe se questo avesse scorso tutto.
        rectBottom -= wanted
        caretBottom -= delta
        fieldTop -= delta
        fieldBottom -= delta
        // Chi sta dentro sale insieme al contenuto di chi scorre.
        for (inner in 0 until index) scrollers[inner].bottom -= delta
      }
    }

    /** Il campo si allunga di una coda: il contenuto di chi lo contiene per primo cresce di altrettanto. */
    fun reserve(space: Float) {
      tail += space
      fieldBottom += space
      val first = scrollers.first()
      if (space < 0f) {
        // La coda restituita era contenuto sotto il bordo: chi scorre perde quella corsa.
        check(first.range + space >= -0.5f) { "si restituisce piu' coda di quella sotto il bordo" }
        first.range = (first.range + space).coerceAtLeast(0f)
        return
      }
      val fill = space.coerceAtMost(first.slack)
      first.slack -= fill
      first.range += space - fill
    }

    fun measure(): CaretBarMeasure {
      // Il primo contenitore con del contenuto sotto il bordo lo taglia, li' o dove lo taglia chi sta fuori.
      val cutter = scrollers.indexOfFirst { it.range > 0f }
      return CaretBarMeasure(
        caretBottom = caretBottom,
        caretHeight = caretHeight,
        fieldBottom = fieldBottom,
        barTop = barTop,
        visibleFieldTop = fieldTop.coerceAtLeast(0f),
        scrollBottom = if (cutter < 0) fieldBottom else scrollers.drop(cutter).minOf { it.bottom },
        cut = cutter >= 0,
        windowHeight = windowHeight,
        gap = gap,
      )
    }

    /** Il campo porta il cursore in vista da solo; poi l'editor fa i suoi passi. Ritorna le richieste fatte. */
    fun settle(): Int {
      bringIntoView(0f)
      requests = 0
      val avoidance = CaretBarAvoidance()
      var steps = 0
      while (true) {
        check(++steps < 50) { "i passi non finiscono" }
        when (val step = avoidance.next(measure())) {
          CaretBarStep.Done -> break
          is CaretBarStep.Bring -> bringIntoView(step.allowance)
          is CaretBarStep.Reserve -> reserve(step.space)
        }
      }
      return requests
    }
  }

  private fun assertJustAboveBar(page: Page) {
    assertTrue("la riga (fondo ${page.caretBottom}) sta sotto la linea della barra (${page.line})", page.caretBottom <= page.line + 0.5f)
    assertTrue("la riga (fondo ${page.caretBottom}) e' salita piu' del necessario (${page.line})", page.caretBottom >= page.line - 0.5f)
  }

  @Test
  fun `una riga a meta del testo sale sopra la barra con una richiesta e senza coda`() {
    // La pagina finisce a 1600 (sopra la tastiera), la barra comincia a 1400; il campo continua sotto.
    val page = Page(fieldBottom = 3000f, caretBottom = 2000f, barTop = 1400f, scrollers = listOf(Scroller(1600f, 5000f)))
    assertEquals(1, page.settle())
    assertJustAboveBar(page)
    assertEquals(0f, page.tail, 0f)
  }

  @Test
  fun `ultima riga di una pagina che continua sotto il campo - la coda da al rettangolo il suo posto`() {
    // Sotto il campo c'e' altro (la pagina ha corsa), ma il rettangolo deve stare nel campo: una coda.
    val page = Page(fieldBottom = 1500f, caretBottom = 1500f, barTop = 1400f, scrollers = listOf(Scroller(1600f, 5000f)))
    assertEquals(1, page.settle())
    assertJustAboveBar(page)
    assertEquals("la coda e' lo spazio della barra sopra il fondo della pagina", 210f, page.tail, 0.5f)
  }

  @Test
  fun `ultima riga di una pagina senza spazio in fondo`() {
    // Il compositore di una mail: il corpo e' l'ultima cosa della pagina, gia' scorsa fino in fondo. Senza
    // coda nessuno potrebbe portare l'ultima riga sopra la barra.
    val page = Page(fieldBottom = 1600f, caretBottom = 1600f, barTop = 1400f, scrollers = listOf(Scroller(1600f, 0f)))
    assertEquals(1, page.settle())
    assertJustAboveBar(page)
    assertEquals(210f, page.tail, 0.5f)
  }

  @Test
  fun `una pagina corta che non scorre ancora`() {
    // Una mail di tre righe col testo grande: il contenuto non riempie la pagina (200 px di vuoto in fondo),
    // ma l'ultima riga e' gia' dietro la barra.
    val page = Page(fieldBottom = 1400f, caretBottom = 1400f, barTop = 1300f, scrollers = listOf(Scroller(1600f, 0f, slack = 200f)))
    assertEquals(1, page.settle())
    assertJustAboveBar(page)
    assertEquals("resta la coda che serve, non quella presa per misurare", 310f, page.tail, 0.5f)
  }

  @Test
  fun `la coda presa per misurare e segnata, e quella che serve no`() {
    // Niente taglia niente: prima la coda fino al fondo della finestra, segnata come presa per misurare.
    val short = CaretBarMeasure(
      caretBottom = 1400f, caretHeight = 50f, fieldBottom = 1400f, barTop = 1300f,
      visibleFieldTop = 300f, scrollBottom = 1400f, cut = false, windowHeight = 2400f, gap = 10f,
    )
    val probing = CaretBarAvoidance().next(short) as CaretBarStep.Reserve
    assertTrue(probing.probe)
    assertEquals(1000f, probing.space, 0f)
    // La pagina taglia: la coda e' quella che serve al rettangolo, e resta.
    val needed = CaretBarAvoidance().next(
      CaretBarMeasure(
        caretBottom = 1500f, caretHeight = 50f, fieldBottom = 1500f, barTop = 1400f,
        visibleFieldTop = 300f, scrollBottom = 1600f, cut = true, windowHeight = 2400f, gap = 10f,
      ),
    ) as CaretBarStep.Reserve
    assertTrue(!needed.probe)
    assertEquals(210f, needed.space, 0f)
  }

  @Test
  fun `in un riquadro che scorre dentro la pagina scorre il riquadro e la pagina resta ferma`() {
    val inner = Scroller(bottom = 1500f, range = 800f)
    val outer = Scroller(bottom = 1600f, range = 5000f)
    val page = Page(fieldBottom = 2200f, caretBottom = 1900f, barTop = 1400f, scrollers = listOf(inner, outer))
    assertEquals(1, page.settle())
    assertJustAboveBar(page)
    assertEquals("la pagina non doveva scorrere", 5000f, outer.range, 0f)
    assertEquals(0f, page.tail, 0f)
  }

  @Test
  fun `ultima riga di un riquadro che finisce sotto il bordo della pagina`() {
    // La demo della galleria: il campo e' l'ultima cosa di un riquadro alto, che scorre dentro la pagina e
    // il cui fondo (1900) sta sotto il bordo della pagina (1600). Senza coda il rettangolo sporge dal
    // riquadro, e alla pagina arriva appoggiato al fondo del riquadro: la pagina scorrerebbe di 300 px
    // invece dei 210 che servono, e la riga finirebbe troppo in alto.
    val inner = Scroller(bottom = 1900f, range = 0f, slack = 100f)
    val outer = Scroller(bottom = 1600f, range = 5000f)
    val page = Page(fieldBottom = 1800f, caretBottom = 1800f, barTop = 1400f, scrollers = listOf(inner, outer))
    assertEquals(1, page.settle())
    assertJustAboveBar(page)
    assertTrue("la coda (${page.tail}) non supera lo spazio della barra", page.tail <= 1600f - page.line + 0.5f)
  }

  @Test
  fun `ultima riga di un riquadro arrivato in fondo sopra il bordo della pagina`() {
    val inner = Scroller(bottom = 1500f, range = 30f)
    val outer = Scroller(bottom = 1600f, range = 5000f)
    val page = Page(fieldBottom = 1530f, caretBottom = 1530f, barTop = 1400f, scrollers = listOf(inner, outer))
    val requests = page.settle()
    assertTrue("troppe richieste: $requests", requests <= 2)
    assertJustAboveBar(page)
    assertEquals("la pagina non doveva scorrere", 5000f, outer.range, 0f)
  }

  @Test
  fun `una pagina che arriva fin sotto la tastiera - la riga sale comunque sopra la barra`() {
    // La pagina non si ferma sopra la tastiera (bordo a 2400, il fondo della finestra): conta la barra.
    val page = Page(fieldBottom = 4000f, caretBottom = 2300f, barTop = 1400f, scrollers = listOf(Scroller(2400f, 5000f)))
    assertEquals(1, page.settle())
    assertJustAboveBar(page)
  }

  @Test
  fun `una riga gia sopra la barra non muove niente e non chiede coda`() {
    val scroller = Scroller(1600f, 5000f)
    // Anche se e' l'ultima riga del campo.
    val page = Page(fieldBottom = 900f, caretBottom = 900f, barTop = 1400f, scrollers = listOf(scroller))
    assertEquals(0, page.settle())
    assertEquals(900f, page.caretBottom, 0f)
    assertEquals(5000f, scroller.range, 0f)
    assertEquals(0f, page.tail, 0f)
  }

  @Test
  fun `senza una barra misurata non si chiede niente`() {
    val measure = CaretBarMeasure(
      caretBottom = 1600f, caretHeight = 50f, fieldBottom = 1600f, barTop = Float.NaN,
      visibleFieldTop = 300f, scrollBottom = 1600f, cut = true, windowHeight = 2400f, gap = 10f,
    )
    assertSame(CaretBarStep.Done, CaretBarAvoidance().next(measure))
  }

  @Test
  fun `una barra sopra tutto il campo visibile non sposta niente`() {
    // Una barra posata in alto: sopra di lei non c'e' campo dove portare la riga.
    val measure = CaretBarMeasure(
      caretBottom = 900f, caretHeight = 50f, fieldBottom = 3000f, barTop = 100f,
      visibleFieldTop = 300f, scrollBottom = 1600f, cut = true, windowHeight = 2400f, gap = 10f,
    )
    assertSame(CaretBarStep.Done, CaretBarAvoidance().next(measure))
  }

  @Test
  fun `se niente si muove i passi finiscono`() {
    // Un campo fermo sotto la barra che non puo' ne' scorrere ne' crescere: non si insiste all'infinito.
    val measure = CaretBarMeasure(
      caretBottom = 1600f, caretHeight = 50f, fieldBottom = 1600f, barTop = 1400f,
      visibleFieldTop = 300f, scrollBottom = 1600f, cut = true, windowHeight = 2400f, gap = 10f,
    )
    val avoidance = CaretBarAvoidance()
    var reserves = 0
    var brings = 0
    var steps = 0
    while (true) {
      assertTrue("i passi non finiscono", ++steps < 50)
      when (avoidance.next(measure)) {
        CaretBarStep.Done -> break
        is CaretBarStep.Reserve -> reserves++
        is CaretBarStep.Bring -> brings++
      }
    }
    assertEquals(CaretBarAvoidance.MaxReserves, reserves)
    assertEquals(CaretBarAvoidance.MaxPasses, brings)
  }

  @Test
  fun `lo spazio chiesto non supera la finestra`() {
    val measure = CaretBarMeasure(
      caretBottom = 2300f, caretHeight = 100f, fieldBottom = 9000f, barTop = 200f,
      visibleFieldTop = 0f, scrollBottom = 2400f, cut = true, windowHeight = 1000f, gap = 10f,
    )
    assertEquals(900f, (CaretBarAvoidance().next(measure) as CaretBarStep.Bring).allowance, 0f)
  }

  @Test
  fun `si porta in vista il capo della selezione che si e mosso`() {
    // Il cursore, o la fine che si allunga: la fine.
    assertEquals(7, movedSelectionOffset(previousStart = 5, previousEnd = 5, start = 7, end = 7))
    assertEquals(9, movedSelectionOffset(previousStart = 2, previousEnd = 7, start = 2, end = 9))
    // La maniglia d'inizio: l'inizio.
    assertEquals(1, movedSelectionOffset(previousStart = 2, previousEnd = 7, start = 1, end = 7))
    // La barra appena comparsa (nessuna selezione di prima): la fine.
    assertEquals(7, movedSelectionOffset(previousStart = null, previousEnd = null, start = 2, end = 7))
  }
}
