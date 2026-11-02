package dev.antigravity.fluidengine.wear.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole di un elenco che l'orologio riempie da lontano. Pure: cosa va in quale riga, e in che
 * ordine, e soprattutto cosa succede quando "e' fallito" incontra "ho gia' qualcosa da mostrare".
 */
class FluidWearListPlanTest {

  private val retry = {}

  @Test
  fun `a ready list with items is just the items`() {
    assertEquals(listOf(FluidWearListPart.Items), fluidWearListPlan(FluidWearListStatus.Ready, 3))
  }

  @Test
  fun `a ready list with nothing in it draws nothing`() {
    assertTrue(fluidWearListPlan(FluidWearListStatus.Ready, 0).isEmpty())
  }

  @Test
  fun `loading with nothing in hand is the loading line alone`() {
    assertEquals(
      listOf(FluidWearListPart.Loading("Un attimo")),
      fluidWearListPlan(FluidWearListStatus.Loading("Un attimo"), 0),
    )
  }

  @Test
  fun `loading with items in hand never covers them and goes after them`() {
    assertEquals(
      listOf(FluidWearListPart.Items, FluidWearListPart.Loading("Un attimo")),
      fluidWearListPlan(FluidWearListStatus.Loading("Un attimo"), 5),
    )
  }

  @Test
  fun `a notice sits above the items, and alone when there are none`() {
    assertEquals(
      listOf(FluidWearListPart.Notice("Solo i primi")),
      fluidWearListPlan(FluidWearListStatus.Notice("Solo i primi"), 0),
    )
    assertEquals(
      listOf(FluidWearListPart.Notice("Solo i primi"), FluidWearListPart.Items),
      fluidWearListPlan(FluidWearListStatus.Notice("Solo i primi"), 2),
    )
  }

  @Test
  fun `a failure with nothing to show is the sentence and a way to retry`() {
    val plan = fluidWearListPlan(FluidWearListStatus.Failed("Il telefono non risponde", "Riprova", retry), 0)
    assertEquals(1, plan.size)
    val part = plan.single() as FluidWearListPart.Failed
    assertEquals("Il telefono non risponde", part.text)
    assertEquals("Riprova", part.retryText)
    assertSame(retry, part.onRetry)
  }

  @Test
  fun `a failure over items keeps the items and warns above them`() {
    // L'errore non nasconde i dati vecchi: valgono piu' di una schermata di scuse.
    val plan = fluidWearListPlan(FluidWearListStatus.Failed("Dati di ieri", "Aggiorna", retry), 4)
    assertEquals(2, plan.size)
    val stale = plan[0] as FluidWearListPart.Stale
    assertEquals("Dati di ieri", stale.text)
    assertEquals("Aggiorna", stale.retryText)
    assertSame(retry, stale.onRetry)
    assertEquals(FluidWearListPart.Items, plan[1])
  }

  @Test
  fun `the plan changes with the item count and nothing else`() {
    val status = FluidWearListStatus.Failed("x", "y", retry)
    val empty = fluidWearListPlan(status, 0).single()
    val full = fluidWearListPlan(status, 1).first()
    assertTrue(empty is FluidWearListPart.Failed)
    assertTrue(full is FluidWearListPart.Stale)
  }
}
