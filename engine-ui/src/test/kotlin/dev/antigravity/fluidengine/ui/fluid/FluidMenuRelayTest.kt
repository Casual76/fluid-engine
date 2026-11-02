package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il relè dell'hand-off senza Compose attorno: `FluidMenuRelayTracker` riceve i cambiamenti veri
 * (`PointerInputChange`) dei due passaggi e inoltra a `FluidMenuHandOff`. Quel che il nodo aggiunge
 * — `requireLayoutCoordinates` — qui e' un offset fisso. Il giro completo con un dito su uno schermo
 * lo copre la prova sul dispositivo; questi test inchiodano le regole che il dispositivo non mostra.
 */
class FluidMenuRelayTest {

  /** Un nodo che sta a (100, 200) nella radice: l'ancora non e' all'origine. */
  private val mapper = FluidMenuRootMapper { it + Offset(100f, 200f) }

  private fun root(local: Offset) = local + Offset(100f, 200f)

  private class FakeSink : FluidMenuPointerSink {
    val events = mutableListOf<String>()
    val positions = mutableListOf<Offset>()
    override fun handOffBegan(root: Offset) {
      events += "began"
      positions += root
    }

    override fun hover(root: Offset) {
      events += "hover"
      positions += root
    }

    override fun release(root: Offset) {
      events += "release"
      positions += root
    }

    override fun cancel() {
      events += "cancel"
    }
  }

  private fun change(
    id: Long,
    position: Offset,
    pressed: Boolean,
    previousPosition: Offset = position,
    previousPressed: Boolean = pressed,
    consumed: Boolean = false,
  ) = PointerInputChange(
    id = PointerId(id),
    uptimeMillis = 0L,
    position = position,
    pressed = pressed,
    previousUptimeMillis = 0L,
    previousPosition = previousPosition,
    previousPressed = previousPressed,
    isInitiallyConsumed = consumed,
  )

  private fun down(id: Long, at: Offset, consumed: Boolean = false) =
    change(id, at, pressed = true, previousPressed = false, consumed = consumed)

  private fun move(id: Long, from: Offset, to: Offset, consumed: Boolean = false) =
    change(id, to, pressed = true, previousPosition = from, previousPressed = true, consumed = consumed)

  private fun up(id: Long, at: Offset, consumed: Boolean = false) =
    change(id, at, pressed = false, previousPressed = true, consumed = consumed)

  private class Relay(handOff: FluidMenuHandOff) {
    val tracker = FluidMenuRelayTracker(handOff)
  }

  /** Un evento completo: il passaggio Initial e poi il Main, come li dispatcha Compose. */
  private fun Relay.dispatch(vararg changes: PointerInputChange, mapper: FluidMenuRootMapper) {
    val list = changes.toList()
    tracker.initial(list, mapper)
    tracker.main(list)
  }

  // --- Prima dell'hand-off il relè non tocca niente --------------------------------------------

  @Test
  fun aDownIsTrackedInRootCoordinatesAndNothingIsConsumed() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    val d = down(1, Offset(10f, 20f))

    relay.dispatch(d, mapper = mapper)

    assertTrue(handOff.pointerDown)
    assertEquals(root(Offset(10f, 20f)), handOff.lastRoot)
    assertFalse(handOff.isActive)
    assertFalse("il relè non consuma finche' nessuno ha preso il dito", d.isConsumed)
  }

  @Test
  fun movesBeforeAttachingUpdateTheLastPointButConsumeNothing() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val m = move(1, Offset(10f, 20f), Offset(14f, 26f))

    relay.dispatch(m, mapper = mapper)

    assertEquals(root(Offset(14f, 26f)), handOff.lastRoot)
    assertFalse(m.isConsumed)
  }

  @Test
  fun attachingHandsOverTheCurrentPositionInRootCoordinates() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val sink = FakeSink()

    val attached = handOff.attach(sink)

    assertTrue(attached)
    assertTrue(handOff.isActive)
    assertEquals(listOf("began"), sink.events)
    assertEquals(listOf(root(Offset(10f, 20f))), sink.positions)
  }

  @Test
  fun attachingWithoutAFingerDownFailsSoTheMenuOpensForTaps() {
    val handOff = FluidMenuHandOff()
    val sink = FakeSink()

    assertFalse(handOff.attach(sink))
    assertFalse(handOff.isActive)
    assertEquals(emptyList<String>(), sink.events)

    // E dopo un dito sollevato.
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(0f, 0f)), mapper = mapper)
    relay.dispatch(up(1, Offset(0f, 0f)), mapper = mapper)
    assertFalse(handOff.attach(sink))
  }

  // --- Durante l'hand-off ---------------------------------------------------------------------------

  @Test
  fun anAttachedFingerForwardsMovesAndConsumesThem() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val sink = FakeSink()
    handOff.attach(sink)
    val m = move(1, Offset(10f, 20f), Offset(30f, 90f))

    relay.dispatch(m, mapper = mapper)

    assertEquals(listOf("began", "hover"), sink.events)
    assertEquals(root(Offset(30f, 90f)), sink.positions.last())
    // La LazyColumn attorno alla riga non deve partire mentre il dito trascina sul menu.
    assertTrue(m.isConsumed)
  }

  @Test
  fun anAttachedFingerIsConsumedAlreadyInTheInitialPass() {
    // Il tasto sotto (3.0) non prende mai il dito e legge nel Main: deve trovare il cambio consumato
    // GIA' li', e finire il suo gesto — senza, il tasto nascosto continuava a seguire il dito sul
    // menu e a suonare la sua grana sotto i tick delle voci.
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    handOff.attach(FakeSink())
    val m = move(1, Offset(10f, 20f), Offset(30f, 90f))

    relay.tracker.initial(listOf(m), mapper)

    assertTrue("consumato nell'Initial, prima di qualunque gesto del tasto", m.isConsumed)
  }

  @Test
  fun aLongPressFiringDuringAnEventConsumesThatSameEvent() {
    // Il click lungo scatta nel Main del tasto, un discendente che passa prima del relè. Un evento a
    // scatti che lo fa scattare e porta il dito oltre lo slop (beta 2 sull'emulatore: la pagina
    // scivolava di ~17 px sotto il menu) non deve arrivare alla lista attorno.
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val m = move(1, Offset(10f, 20f), Offset(10f, -20f))

    relay.tracker.initial(listOf(m), mapper)
    assertFalse(m.isConsumed)
    handOff.attach(FakeSink())
    relay.tracker.main(listOf(m))

    assertTrue("preso a meta' evento, consumato prima dei genitori", m.isConsumed)
  }

  @Test
  fun beforeTheHandOffTheInitialPassConsumesNothing() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val m = move(1, Offset(10f, 20f), Offset(30f, 90f))

    relay.tracker.initial(listOf(m), mapper)

    assertFalse("senza menu il tasto e la pagina hanno il dito come sempre", m.isConsumed)
  }

  @Test
  fun aStationaryEventWithoutMovementIsNotForwardedButStillConsumedWhileActive() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val sink = FakeSink()
    handOff.attach(sink)
    val same = change(1, Offset(10f, 20f), pressed = true, previousPosition = Offset(10f, 20f), previousPressed = true)

    relay.dispatch(same, mapper = mapper)

    assertEquals("nessun movimento, nessun hover", listOf("began"), sink.events)
    assertTrue(same.isConsumed)
  }

  @Test
  fun theLiftReleasesAtTheLiftPointDetachesAndIsConsumedToo() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val sink = FakeSink()
    handOff.attach(sink)
    val u = up(1, Offset(30f, 90f))

    relay.dispatch(u, mapper = mapper)

    assertEquals(listOf("began", "release"), sink.events)
    assertEquals(root(Offset(30f, 90f)), sink.positions.last())
    assertFalse(handOff.pointerDown)
    assertFalse(handOff.isActive)
    // Il sollevamento che chiude un hand-off e' ancora dell'hand-off: se `clickable` lo vedesse,
    // lo leggerebbe come un click sull'ancora.
    assertTrue(u.isConsumed)
  }

  @Test
  fun afterTheLiftTheNextFingerIsAFreshGestureAndNothingIsConsumed() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    handOff.attach(FakeSink())
    relay.dispatch(up(1, Offset(10f, 20f)), mapper = mapper)

    val next = down(2, Offset(50f, 60f))
    relay.dispatch(next, mapper = mapper)

    assertTrue(handOff.pointerDown)
    assertEquals(root(Offset(50f, 60f)), handOff.lastRoot)
    assertFalse(handOff.isActive)
    assertFalse(next.isConsumed)
  }

  @Test
  fun theRelayReadsEventsADescendantHasAlreadyConsumed() {
    // Nel passaggio Initial un genitore vede il dito prima del figlio, ma un figlio che ha gia' preso
    // il gesto (il pulsante) non deve privare il menu del dito: si legge "ignorando il consumato".
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)

    relay.dispatch(down(1, Offset(10f, 20f), consumed = true), mapper = mapper)
    assertTrue(handOff.pointerDown)

    val sink = FakeSink()
    handOff.attach(sink)
    relay.dispatch(move(1, Offset(10f, 20f), Offset(10f, 80f), consumed = true), mapper = mapper)
    assertEquals(listOf("began", "hover"), sink.events)

    relay.dispatch(up(1, Offset(10f, 80f), consumed = true), mapper = mapper)
    assertEquals(listOf("began", "hover", "release"), sink.events)
  }

  @Test
  fun onlyTheFirstFingerCounts() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val sink = FakeSink()
    handOff.attach(sink)

    // Un secondo dito scende e si muove: non e' il dito del menu. Un evento vero porta TUTTI i
    // puntatori giu', quindi il primo c'e' sempre, fermo.
    val firstStill = change(1, Offset(10f, 20f), pressed = true, previousPressed = true)
    val second = down(2, Offset(200f, 200f))
    relay.dispatch(firstStill, second, mapper = mapper)
    val firstStillAgain = change(1, Offset(10f, 20f), pressed = true, previousPressed = true)
    val secondMove = move(2, Offset(200f, 200f), Offset(220f, 220f))
    relay.dispatch(firstStillAgain, secondMove, mapper = mapper)

    assertEquals(listOf("began"), sink.events)
    assertEquals(root(Offset(10f, 20f)), handOff.lastRoot)
    assertFalse(second.isConsumed)
    assertFalse(secondMove.isConsumed)
  }

  @Test
  fun aFingerThatVanishesFromTheEventWithoutALiftIsACancel() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val sink = FakeSink()
    handOff.attach(sink)

    // L'evento seguente parla di un altro puntatore soltanto: il nostro non c'e' piu'.
    relay.dispatch(move(7, Offset(0f, 0f), Offset(5f, 5f)), mapper = mapper)

    assertEquals(listOf("began", "cancel"), sink.events)
    assertFalse(handOff.pointerDown)
    assertFalse(handOff.isActive)
  }

  @Test
  fun cancelPointerInputDismissesTheSinkAndIsIdempotent() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val sink = FakeSink()
    handOff.attach(sink)

    relay.tracker.cancel()
    relay.tracker.cancel()

    // Una sola scollegata: la seconda trova il relè gia' vuoto.
    assertEquals(listOf("began", "cancel"), sink.events)
    assertFalse(handOff.pointerDown)
    assertFalse(handOff.isActive)

    // Il dito che arriva dopo l'annullo, senza un nuovo down, non e' di nessuno.
    relay.dispatch(move(1, Offset(10f, 20f), Offset(30f, 30f)), mapper = mapper)
    assertEquals(listOf("began", "cancel"), sink.events)
  }

  @Test
  fun aNewDownDismissesAStaleSink() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val stale = FakeSink()
    handOff.attach(stale)

    // Un dito nuovo arriva mentre l'hand-off e' ancora agganciato al vecchio (un up perso): il vecchio
    // menu non si porta dietro il dito nuovo, lo si congeda.
    handOff.down(Offset(0f, 0f))

    assertEquals(listOf("began", "cancel"), stale.events)
    assertEquals(null, handOff.sink)
  }

  @Test
  fun aSinkCanBeDetachedByAssigningNullAndThenStopsReceiving() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val sink = FakeSink()
    handOff.attach(sink)

    handOff.sink = null // il congedo a dito giu'
    relay.dispatch(move(1, Offset(10f, 20f), Offset(40f, 40f)), mapper = mapper)

    assertEquals(listOf("began"), sink.events)
    assertFalse(handOff.isActive)
  }

  @Test
  fun attachingTwiceIsAMatterOfWhoAsks() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    relay.dispatch(down(1, Offset(10f, 20f)), mapper = mapper)
    val first = FakeSink()
    val second = FakeSink()

    assertTrue(handOff.attach(first))
    assertTrue("lo stesso e' gia' agganciato", handOff.attach(first))
    assertFalse("un altro non scalza il primo", handOff.attach(second))
    assertEquals(listOf("began"), first.events)
    assertEquals(emptyList<String>(), second.events)
  }

  // --- Il giro intero con una selezione vera --------------------------------------------------------

  private fun rows() = FluidMenuGeometry(
    cells = (0..2).map { Rect(0f, 300f + it * 48f, 400f, 300f + it * 48f + 48f) },
    enabled = listOf(true, true, true),
    panel = Rect(0f, 300f, 400f, 444f),
  )

  @Test
  fun holdDragOntoARowAndReleasePicksItOnce() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    val picks = mutableListOf<Int>()
    var ticks = 0
    val selection = FluidMenuSelection(touchSlop = 10f, onTick = { ticks++ }, onPick = { picks += it })
    selection.geometry = rows()

    // L'ancora sta a (100, 200) nella radice: il dito scende a (110, 215) in coordinate dell'ancora.
    relay.dispatch(down(1, Offset(10f, 15f)), mapper = mapper)
    assertTrue(handOff.attach(selection))

    // Il dito scende sulla riga 1 (y radice 348..396): da (10, 15) locale a (10, 160) locale = y 360.
    relay.dispatch(move(1, Offset(10f, 15f), Offset(10f, 160f)), mapper = mapper)
    assertEquals(1, selection.hovered)
    assertEquals(1, ticks)

    relay.dispatch(up(1, Offset(10f, 160f)), mapper = mapper)

    assertEquals(listOf(1), picks)
    assertEquals(FluidMenuOwner.None, selection.owner)
    assertFalse(handOff.isActive)
  }

  @Test
  fun holdAndReleaseWithoutMovingLeavesTheMenuOpen() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    val picks = mutableListOf<Int>()
    val selection = FluidMenuSelection(touchSlop = 10f, onPick = { picks += it })
    selection.geometry = rows()

    // Il pannello e' comparso proprio sotto il dito (che e' sulla riga 0, y radice 300..348).
    relay.dispatch(down(1, Offset(10f, 110f)), mapper = mapper)
    handOff.attach(selection)
    relay.dispatch(up(1, Offset(10f, 110f)), mapper = mapper)

    assertEquals(emptyList<Int>(), picks)
    assertEquals(FluidMenuOwner.None, selection.owner)
  }

  @Test
  fun theAnchorLeavingTheCompositionMidDragCancelsAndLeavesTheMenuOpen() {
    val handOff = FluidMenuHandOff()
    val relay = Relay(handOff)
    val picks = mutableListOf<Int>()
    val selection = FluidMenuSelection(touchSlop = 10f, onPick = { picks += it })
    selection.geometry = rows()
    relay.dispatch(down(1, Offset(10f, 15f)), mapper = mapper)
    handOff.attach(selection)
    relay.dispatch(move(1, Offset(10f, 15f), Offset(10f, 160f)), mapper = mapper)
    assertEquals(1, selection.hovered)

    // onCancelPointerInput: una riga di LazyColumn rimossa a meta' gesto.
    relay.tracker.cancel()

    assertEquals(emptyList<Int>(), picks)
    assertEquals(FluidMenuOwner.None, selection.owner)
    assertEquals(-1, selection.hovered)
  }
}
