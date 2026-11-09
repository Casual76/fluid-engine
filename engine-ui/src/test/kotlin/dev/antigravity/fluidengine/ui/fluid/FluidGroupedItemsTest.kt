package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.theme.FluidGroupSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La logica pura di [fluidGroupedItems]: il pezzo di ogni riga, chi richiude il vuoto, quanto, e come i pezzi si
 * ricalcolano quando una riga entra in cima o esce. La parte che si vede (un item per riga, il tocco nella fascia
 * richiusa, l'ancoraggio alla chiave) e' in `FluidLongListsTest` della galleria.
 */
class FluidGroupedItemsTest {

  private fun segments(count: Int) = List(count) { fluidGroupedSegment(it, count) }

  @Test
  fun `una riga sola e' un pezzo intero`() {
    assertEquals(listOf(FluidGroupSegment.Whole), segments(1))
    assertFalse(fluidGroupedJoinsNext(0, 1))
  }

  @Test
  fun `due righe sono primo e ultimo, senza mezzo`() {
    assertEquals(listOf(FluidGroupSegment.First, FluidGroupSegment.Last), segments(2))
  }

  @Test
  fun `in mezzo nessun angolo tondo, agli estremi primo e ultimo`() {
    val pieces = segments(6)
    assertEquals(FluidGroupSegment.First, pieces.first())
    assertEquals(FluidGroupSegment.Last, pieces.last())
    assertTrue(pieces.subList(1, 5).all { it == FluidGroupSegment.Middle })
  }

  @Test
  fun `tutte tranne l'ultima richiudono il vuoto, quindi i separatori sono righe meno uno`() {
    for (count in 1..9) {
      val joins = (0 until count).count { fluidGroupedJoinsNext(it, count) }
      assertEquals("separatori per $count righe", count - 1, joins)
      assertFalse("l'ultima tiene l'aria dopo la sezione", fluidGroupedJoinsNext(count - 1, count))
    }
  }

  @Test
  fun `una riga che entra in cima - la vecchia prima diventa di mezzo, l'ultima resta ultima`() {
    // Il pezzo dipende solo da posizione e conteggio, quindi la riga che era la prima (indice 0 su 3) e ora e'
    // all'indice 1 su 4 perde gli angoli in alto senza che nessuno lo chieda.
    assertEquals(FluidGroupSegment.First, fluidGroupedSegment(0, 3))
    assertEquals(FluidGroupSegment.Middle, fluidGroupedSegment(1, 4))
    assertEquals(FluidGroupSegment.Last, fluidGroupedSegment(3, 4))
  }

  @Test
  fun `l'ultima riga che esce - la penultima diventa l'ultima e smette di richiudere`() {
    assertEquals(FluidGroupSegment.Middle, fluidGroupedSegment(1, 3))
    assertTrue(fluidGroupedJoinsNext(1, 3))
    assertEquals(FluidGroupSegment.Last, fluidGroupedSegment(1, 2))
    assertFalse(fluidGroupedJoinsNext(1, 2))
  }

  @Test
  fun `una sezione che resta con una riga torna intera`() {
    assertEquals(FluidGroupSegment.First, fluidGroupedSegment(0, 2))
    assertEquals(FluidGroupSegment.Whole, fluidGroupedSegment(0, 1))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `una sezione vuota non ha pezzi`() {
    fluidGroupedSegment(0, 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `un indice fuori dalla sezione e' un errore, non un pezzo inventato`() {
    fluidGroupedSegment(3, 3)
  }

  @Test
  fun `il vuoto riassorbito e' la spaziatura, piu' due ritagli solo sul vetro`() {
    assertEquals(28, fluidGroupedGapPx(spacingPx = 28, openEdgePx = 0))
    assertEquals(28 + 8, fluidGroupedGapPx(spacingPx = 28, openEdgePx = 4))
  }

  @Test
  fun `l'altezza dichiarata e' quella disegnata meno il vuoto, mai negativa`() {
    assertEquals(144 - 28, fluidGroupedJoinedHeight(144, 28))
    assertEquals(0, fluidGroupedJoinedHeight(10, 28))
  }

  @Test
  fun `la sagoma e' una istanza stabile per pezzo, diversa fra pezzi`() {
    for (segment in FluidGroupSegment.entries) {
      assertSame(fluidGroupedSegmentShape(segment), fluidGroupedSegmentShape(segment))
    }
    assertNotSame(fluidGroupedSegmentShape(FluidGroupSegment.First), fluidGroupedSegmentShape(FluidGroupSegment.Last))
  }

  @Test
  fun `il materiale di serie e' l'opaco, e il vetro e' solo il suo`() {
    assertFalse(FluidGroupedMaterial.Opaque.isGlass)
    assertFalse(FluidGroupedMaterial.Opaque.isTinted)
    assertTrue(FluidGroupedMaterial.Tinted.isTinted)
    @OptIn(FluidExperimentalRowGlass::class)
    assertTrue(FluidGroupedMaterial.GlassSegments.isGlass)
    assertEquals("Opaque", FluidGroupedMaterial.Opaque.toString())
  }

  @Test
  fun `50 000 righe - il pezzo di ogni riga si calcola senza materializzare niente`() {
    val count = 50_000
    var middles = 0
    for (index in 0 until count) if (fluidGroupedSegment(index, count) == FluidGroupSegment.Middle) middles++
    assertEquals(count - 2, middles)
  }
}
