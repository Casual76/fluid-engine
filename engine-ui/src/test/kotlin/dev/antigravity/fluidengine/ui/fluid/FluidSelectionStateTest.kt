package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La logica della selezione multipla: chiavi esplicite, «tutto tranne» per le liste paginate, pulizia
 * dopo gli aggiornamenti dei dati, salvataggio.
 */
class FluidSelectionStateTest {

  private fun explicit(vararg keys: String) =
    FluidSelectionState<String>().also { state -> keys.forEach { state.select(it) } }

  private val scope = SaverScope { true }

  private fun <K : Any> roundTrip(
    state: FluidSelectionState<K>,
    keySaver: Saver<K, out Any>? = null,
    max: Int = FluidSelectionState.DefaultMaxSavedKeys,
  ): FluidSelectionState<K> {
    val saver = FluidSelectionState.saver(keySaver, max)
    val saved = with(saver) { scope.save(state) }
    assertTrue("deve essere una lista salvabile", saved is java.io.Serializable)
    return saver.restore(saved!!)!!
  }

  // --- Esplicita -----------------------------------------------------------------------------------

  @Test
  fun `a new state is not active and has nothing selected`() {
    val state = FluidSelectionState<String>()
    assertFalse(state.isActive)
    assertFalse(state.isAllExcept)
    assertEquals(0, state.countIn(100))
    assertFalse(state.isSelected("a"))
  }

  @Test
  fun `toggle selects then deselects and the first selection makes it active`() {
    val state = FluidSelectionState<String>()
    state.toggle("a")
    assertTrue(state.isActive)
    assertTrue(state.isSelected("a"))
    state.toggle("a")
    assertFalse(state.isSelected("a"))
    assertFalse("togliere l'ultima chiave esce dalla selezione", state.isActive)
  }

  @Test
  fun `explicit count ignores the total and keeps selection order`() {
    val state = explicit("c", "a", "b")
    assertEquals(3, state.countIn(0))
    assertEquals(3, state.countIn(1_000_000))
    assertEquals(listOf("c", "a", "b"), (state.mode as FluidSelectionMode.Explicit).keys.toList())
  }

  @Test
  fun `selecting twice is one key and deselecting a key that is not there changes nothing`() {
    val state = explicit("a", "a")
    assertEquals(1, state.countIn(10))
    val before = state.mode
    state.deselect("zzz")
    assertTrue("nessun nuovo valore se non e' cambiato niente", before === state.mode)
  }

  @Test
  fun `clear leaves the selection and is idempotent`() {
    val state = explicit("a", "b")
    state.clear()
    assertFalse(state.isActive)
    assertEquals(0, state.countIn(5))
    state.clear()
    assertFalse(state.isActive)
  }

  // --- «Tutto tranne» ------------------------------------------------------------------------------

  @Test
  fun `select all represents the whole result without materialising keys`() {
    val state = FluidSelectionState<String>()
    state.selectAll()
    assertTrue(state.isActive)
    assertTrue(state.isAllExcept)
    // Qualunque chiave, caricata o no, e' scelta.
    assertTrue(state.isSelected("riga-mai-caricata-49999"))
    assertEquals(50_000, state.countIn(50_000))
    assertTrue((state.mode as FluidSelectionMode.AllExcept).excluded.isEmpty())
  }

  @Test
  fun `toggling a row in all-except mode excludes it and toggling again puts it back`() {
    val state = FluidSelectionState<String>()
    state.selectAll()
    state.toggle("x")
    assertFalse(state.isSelected("x"))
    assertTrue(state.isSelected("y"))
    assertEquals(49_999, state.countIn(50_000))
    state.toggle("x")
    assertTrue(state.isSelected("x"))
    assertEquals(50_000, state.countIn(50_000))
  }

  @Test
  fun `the count never goes under zero`() {
    val state = FluidSelectionState<String>()
    state.selectAll()
    state.deselect("a")
    state.deselect("b")
    assertEquals(0, state.countIn(1))
    assertEquals(0, state.countIn(0))
  }

  @Test
  fun `select all replaces an explicit selection`() {
    val state = explicit("a", "b")
    state.selectAll()
    assertTrue(state.isSelected("zzz"))
    assertTrue((state.mode as FluidSelectionMode.AllExcept).excluded.isEmpty())
  }

  @Test
  fun `rows that arrived after the tap are not selected when the app says they do not belong`() {
    val state = FluidSelectionState<String>()
    state.selectAll(snapshot = "inbox@t=1000")
    assertEquals("inbox@t=1000", state.snapshot)
    assertTrue(state.isSelected("vecchia", belongsToSnapshot = true))
    assertFalse(state.isSelected("nuova", belongsToSnapshot = false))
    // In forma esplicita il parametro non conta: la persona ha toccato quella riga.
    val explicit = explicit("nuova")
    assertTrue(explicit.isSelected("nuova", belongsToSnapshot = false))
  }

  @Test
  fun `a changed snapshot leaves an all-except selection but never an explicit one`() {
    val all = FluidSelectionState<String>()
    all.selectAll(snapshot = "inbox")
    all.clearIfSnapshotDiffers("inbox")
    assertTrue("stesso testimone: resta", all.isActive)
    all.clearIfSnapshotDiffers("archive")
    assertFalse("filtro cambiato: esce", all.isActive)

    val explicit = explicit("a")
    explicit.clearIfSnapshotDiffers("archive")
    assertTrue("le chiavi esplicite valgono in ogni vista", explicit.isActive)
  }

  @Test
  fun `exit if empty leaves when the exclusions caught up with the total`() {
    val state = FluidSelectionState<String>()
    state.selectAll()
    state.deselect("a")
    state.deselect("b")
    state.exitIfEmpty(total = 3)
    assertTrue("restano da scegliere: ancora attiva", state.isActive)
    state.deselect("c")
    state.exitIfEmpty(total = 3)
    assertFalse(state.isActive)
    assertFalse(state.isAllExcept)
  }

  // --- Chiavi sparite dai dati ----------------------------------------------------------------------

  @Test
  fun `retain only drops keys that are gone from an explicit selection`() {
    val state = explicit("a", "b", "c")
    state.retainOnly(setOf("a", "c", "z"))
    assertEquals(listOf("a", "c"), (state.mode as FluidSelectionMode.Explicit).keys.toList())
    state.retainOnly(emptySet())
    assertFalse(state.isActive)
  }

  @Test
  fun `retain only also drops exclusions of rows that no longer exist so the count stays right`() {
    val state = FluidSelectionState<String>()
    state.selectAll()
    state.deselect("a")
    state.deselect("gone")
    // 10 righe, «a» e' ancora li', «gone» non c'e' piu': il totale e' 10 e le esclusioni vere sono 1.
    state.retainOnly(setOf("a", "b"))
    assertEquals(9, state.countIn(10))
  }

  @Test
  fun `forget removes keys the app knows are gone without looking at the others - paginated lists`() {
    val state = explicit("loaded", "not-loaded")
    state.forget(listOf("loaded"))
    assertTrue("la riga non caricata resta scelta", state.isSelected("not-loaded"))
    assertFalse(state.isSelected("loaded"))

    val all = FluidSelectionState<String>()
    all.selectAll()
    all.deselect("archived-row")
    all.forget(listOf("archived-row"))
    assertEquals(100, all.countIn(100))
  }

  @Test
  fun `forget with nothing to forget keeps the very same value`() {
    val state = explicit("a")
    val before = state.mode
    state.forget(emptyList())
    state.forget(listOf("zzz"))
    assertTrue(before === state.mode)
  }

  // --- Salvataggio -----------------------------------------------------------------------------------

  @Test
  fun `an explicit selection survives a save and restore with keys saved as they are`() {
    val restored = roundTrip(explicit("a", "b", "c"))
    assertEquals(listOf("a", "b", "c"), (restored.mode as FluidSelectionMode.Explicit).keys.toList())
  }

  @Test
  fun `an all-except selection survives with its exclusions and its snapshot`() {
    val state = FluidSelectionState<String>()
    state.selectAll(snapshot = "q=inbox;t=42")
    state.deselect("x")
    val restored = roundTrip(state)
    assertTrue(restored.isAllExcept)
    assertEquals("q=inbox;t=42", restored.snapshot)
    assertFalse(restored.isSelected("x"))
    assertTrue(restored.isSelected("y"))
    assertEquals(9, restored.countIn(10))
  }

  @Test
  fun `a selection without snapshot restores without one`() {
    val state = FluidSelectionState<String>()
    state.selectAll()
    assertNull(roundTrip(state).snapshot)
  }

  @Test
  fun `long keys and integer keys round trip`() {
    val state = FluidSelectionState<Long>()
    state.select(1L)
    state.select(Long.MAX_VALUE)
    val restored = roundTrip(state)
    assertTrue(restored.isSelected(Long.MAX_VALUE))
    assertEquals(2, restored.countIn(0))
  }

  @Test
  fun `a key saver turns domain keys into ids and back`() {
    data class Row(val id: Int)
    val rowSaver = Saver<Row, Any>(save = { it.id }, restore = { Row(it as Int) })
    val state = FluidSelectionState<Row>()
    state.select(Row(7))
    state.select(Row(9))
    val restored = roundTrip(state, rowSaver)
    assertTrue(restored.isSelected(Row(7)))
    assertTrue(restored.isSelected(Row(9)))
    assertEquals(2, restored.countIn(0))
  }

  @Test
  fun `a key the saver cannot save is left out and never crashes the save`() {
    val picky = Saver<String, Any>(save = { if (it.startsWith("ok")) it else null }, restore = { it as String })
    val state = explicit("ok-1", "bad", "ok-2")
    val restored = roundTrip(state, picky)
    assertEquals(listOf("ok-1", "ok-2"), (restored.mode as FluidSelectionMode.Explicit).keys.toList())
  }

  @Test
  fun `too many keys are saved as an empty selection instead of overflowing the bundle`() {
    val state = FluidSelectionState<String>()
    repeat(50) { state.select("k$it") }
    val restored = roundTrip(state, max = 10)
    assertFalse(restored.isActive)

    val all = FluidSelectionState<String>()
    all.selectAll(snapshot = "s")
    repeat(50) { all.deselect("k$it") }
    val restoredAll = roundTrip(all, max = 10)
    assertFalse("meglio nessuna selezione che una selezione sbagliata", restoredAll.isActive)
    assertFalse(restoredAll.isAllExcept)
  }

  @Test
  fun `the saved form is a plain list of primitives`() {
    val saver = FluidSelectionState.saver<String>()
    val state = explicit("a")
    state.let {
      val saved = with(saver) { scope.save(it) }
      assertTrue(saved is ArrayList<*>)
      @Suppress("UNCHECKED_CAST")
      val list = saved as List<Any>
      assertTrue(list.all { it is Int || it is String })
    }
  }
}
