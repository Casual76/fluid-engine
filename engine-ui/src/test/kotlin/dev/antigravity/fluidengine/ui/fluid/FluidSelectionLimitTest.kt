package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.14 · Il tetto della selezione (`selectionLimit` del selettore di foto di iOS) e l'ordine delle scelte, che il
 * selettore usa per i numeri: chi entra per primo e' 1, togliendo una scelta le altre scalano, a tetto pieno niente entra.
 */
class FluidSelectionLimitTest {

  private fun keys(state: FluidSelectionState<String>): List<String> =
    (state.mode as FluidSelectionMode.Explicit).keys.toList()

  @Test fun lOrdineEQuelloDeiTocchi() {
    val state = FluidSelectionState<String>()
    listOf("c", "a", "e", "b").forEach { state.toggle(it) }
    assertEquals(listOf("c", "a", "e", "b"), keys(state))
    state.toggle("a")
    assertEquals(listOf("c", "e", "b"), keys(state))
    state.toggle("a")
    assertEquals("chi rientra va in fondo", listOf("c", "e", "b", "a"), keys(state))
    assertFalse("senza tetto non e' mai pieno", state.atLimit)
  }

  @Test fun aTettoPienoNienteEntra() {
    val state = FluidSelectionState<String>(limit = 2)
    state.select("a")
    assertFalse(state.atLimit)
    state.select("b")
    assertTrue(state.atLimit)
    state.select("c")
    state.setSelected("d", true)
    assertEquals(listOf("a", "b"), keys(state))
    // Togliere si puo' sempre, e poi c'e' posto.
    state.deselect("a")
    assertFalse(state.atLimit)
    state.select("c")
    assertEquals(listOf("b", "c"), keys(state))
    // «Seleziona tutto» con un tetto non fa niente.
    state.selectAll()
    assertFalse(state.isAllExcept)
  }

  @Test fun ilTettoSopravviveAlSalvataggio() {
    val scope = SaverScope { true }
    val saver = FluidSelectionState.saver<String>(limit = 3)
    val state = FluidSelectionState<String>(limit = 3).also { s -> listOf("x", "y", "z").forEach { s.select(it) } }
    val restored = saver.restore(with(saver) { scope.save(state) }!!)!!
    assertEquals(3, restored.limit)
    assertEquals(listOf("x", "y", "z"), keys(restored))
    assertTrue(restored.atLimit)
    // Un salvataggio con piu' chiavi del tetto (un tetto che si e' stretto) tiene le prime.
    val wide = FluidSelectionState<String>().also { s -> listOf("1", "2", "3", "4").forEach { s.select(it) } }
    val tight = FluidSelectionState.saver<String>(limit = 2)
    val clipped = tight.restore(with(FluidSelectionState.saver<String>()) { scope.save(wide) }!!)!!
    assertEquals(listOf("1", "2"), keys(clipped))
  }

  @Test fun ilSegnoDiceIlNumero() {
    assertEquals("Selezionato, numero 2", FluidSelectionMarkDefaults.description(true, 2))
    assertEquals("Selezionato", FluidSelectionMarkDefaults.description(true, null))
    assertEquals("Non selezionato", FluidSelectionMarkDefaults.description(false, 3))
  }

  @Test(expected = IllegalArgumentException::class)
  fun unTettoDiZeroNonEsiste() {
    FluidSelectionState<String>(limit = 0)
  }
}
