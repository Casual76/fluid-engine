package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.Constraints
import dev.antigravity.fluidengine.ui.theme.FluidTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** La pila di un foglio: testata, corpo che scorre, tasti fermi. Pura, senza comporre niente. */
class FluidSheetScaffoldLayoutTest {

  @Test
  fun `il corpo prende quello che resta dopo la testata, i tasti e i due vuoti`() {
    // 640 - 60 (testata) - 12 (vuoto) - 100 (tasti) - 16 (vuoto) = 452
    val budget = fluidSheetBodyBudget(maxHeight = 640, header = 60, actions = 100, headerGap = 12, actionsGap = 16)
    assertEquals(452, budget)
  }

  @Test
  fun `senza tasti non si riserva il vuoto sopra di loro`() {
    val budget = fluidSheetBodyBudget(maxHeight = 640, header = 60, actions = 0, headerGap = 12, actionsGap = 16)
    assertEquals(568, budget)
  }

  @Test
  fun `senza un'altezza massima il corpo prende un tetto enorme ma finito, perche' un verticalScroll non si lascia misurare all'infinito`() {
    val budget = fluidSheetBodyBudget(
      maxHeight = Constraints.Infinity, header = 60, actions = 100, headerGap = 12, actionsGap = 16,
    )
    assertEquals(FluidSheetUnboundedBodyCap, budget)
    assertNotEquals(Constraints.Infinity, budget)
  }

  @Test
  fun `se testata e tasti riempiono tutto il corpo non scende sotto zero`() {
    val budget = fluidSheetBodyBudget(maxHeight = 100, header = 60, actions = 100, headerGap = 12, actionsGap = 16)
    assertEquals(0, budget)
  }

  @Test
  fun `un vuoto sta solo fra due cose che ci sono`() {
    val full = fluidSheetStack(header = 60, body = 200, actions = 100, headerGap = 12, actionsGap = 16)
    assertEquals(FluidSheetStack(bodyTop = 72, actionsTop = 288, height = 388), full)

    val noBody = fluidSheetStack(header = 60, body = 0, actions = 100, headerGap = 12, actionsGap = 16)
    assertEquals(FluidSheetStack(bodyTop = 60, actionsTop = 76, height = 176), noBody)

    val noActions = fluidSheetStack(header = 60, body = 200, actions = 0, headerGap = 12, actionsGap = 16)
    assertEquals(FluidSheetStack(bodyTop = 72, actionsTop = 272, height = 272), noActions)

    val onlyTitle = fluidSheetStack(header = 60, body = 0, actions = 0, headerGap = 12, actionsGap = 16)
    assertEquals(FluidSheetStack(bodyTop = 60, actionsTop = 60, height = 60), onlyTitle)
  }

  @Test
  fun `un corpo che riempie il suo budget fa un foglio alto quanto il tetto`() {
    val max = 400
    val header = 60
    val actions = 120
    val budget = fluidSheetBodyBudget(max, header, actions, headerGap = 12, actionsGap = 16)
    // Un corpo piu' alto del budget viene tagliato dal budget stesso (e scorre): il totale e' il tetto.
    val body = minOf(budget, 900)
    val stack = fluidSheetStack(header, body, actions, headerGap = 12, actionsGap = 16)
    assertEquals(max, stack.height)
  }

  @Test
  fun `una conferma distruttiva prende il rosso, a meno che non si chieda un altro tono`() {
    assertEquals(FluidTone.Danger, fluidConfirmTone(destructive = true, requested = null))
    assertEquals(FluidTone.Primary, fluidConfirmTone(destructive = false, requested = null))
    assertEquals(FluidTone.Warning, fluidConfirmTone(destructive = true, requested = FluidTone.Warning))
  }
}
