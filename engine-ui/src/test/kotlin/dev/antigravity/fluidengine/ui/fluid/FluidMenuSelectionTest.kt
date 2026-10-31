package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La regola di Alessio, in test: il menu si sceglie col dito che l'ha aperto, e un rilascio altrove
 * lo LASCIA APERTO. Ogni caso e' una cosa che in mano si sente subito e in una revisione mai.
 */
class FluidMenuSelectionTest {

  private val slop = 10f

  /** Tre righe da 48 px una sopra l'altra: 0 = 0..48, 1 = 48..96, 2 = 96..144 (x da 0 a 200). */
  private fun rows(enabled: List<Boolean> = listOf(true, true, true)): FluidMenuGeometry =
    FluidMenuGeometry(
      cells = enabled.indices.map { Rect(0f, it * 48f, 200f, it * 48f + 48f) },
      enabled = enabled,
      panel = Rect(0f, 0f, 200f, enabled.size * 48f),
    )

  private fun center(row: Int) = Offset(100f, row * 48f + 24f)

  /** Un banco: una selezione con tick e scelte registrati. */
  private class Bench(touchSlop: Float, geometry: FluidMenuGeometry?) {
    var ticks = 0
    val picks = mutableListOf<Int>()
    val selection = FluidMenuSelection(
      touchSlop = touchSlop,
      onTick = { ticks++ },
      onPick = { picks += it },
    ).also { it.geometry = geometry }
  }

  private fun bench(geometry: FluidMenuGeometry? = rows()) = Bench(slop, geometry)

  // --- Il dito che ha aperto il menu (hand-off) ---------------------------------------------

  @Test
  fun aHandedOffFingerWithinTheSlopDoesNothing() {
    val b = bench()
    val s = b.selection
    s.handOffBegan(center(0))
    assertEquals(FluidMenuOwner.HandOff, s.owner)
    assertTrue(s.held)

    // Un tremolio dentro lo slop, anche sopra un'altra riga: il dito sta ancora "tenendo premuto".
    s.hover(center(0) + Offset(3f, 4f))
    s.hover(center(0) + Offset(0f, 9f))

    assertEquals(-1, s.hovered)
    assertEquals(0, b.ticks)
  }

  @Test
  fun aHandedOffFingerBeyondTheSlopHoversAndTicks() {
    val b = bench()
    val s = b.selection
    s.handOffBegan(center(0))

    s.hover(center(1)) // 48 px piu' in basso: oltre lo slop, sulla riga 1

    assertEquals(1, s.hovered)
    assertEquals(1, b.ticks)

    s.hover(center(1) + Offset(5f, 2f)) // ancora la riga 1: niente tick
    assertEquals(1, b.ticks)

    s.hover(center(2)) // un'altra riga: tick
    assertEquals(2, s.hovered)
    assertEquals(2, b.ticks)
  }

  @Test
  fun releasingARestingHandedOffFingerLeavesTheMenuOpen() {
    // "Tieni e rilascia": il pannello e' comparso sotto il dito, il dito non si e' mosso.
    val b = bench()
    val s = b.selection
    s.handOffBegan(center(0))

    s.release(center(0))

    assertEquals(emptyList<Int>(), b.picks)
    assertEquals(FluidMenuOwner.None, s.owner)
    assertFalse(s.held)
    assertEquals(-1, s.hovered)
  }

  @Test
  fun releasingAMovedHandedOffFingerOnAnEnabledItemPicksItOnce() {
    val b = bench()
    val s = b.selection
    s.handOffBegan(center(0))
    s.hover(center(1))
    s.hover(center(2))

    s.release(center(2))

    assertEquals(listOf(2), b.picks)
    assertEquals(FluidMenuOwner.None, s.owner)
    assertEquals(-1, s.hovered)

    // Un secondo rilascio (un evento doppio) non sceglie di nuovo.
    s.release(center(2))
    assertEquals(listOf(2), b.picks)
  }

  @Test
  fun releaseOffTheItemsOrOnADisabledOneLeavesTheMenuOpen() {
    // Fuori dal pannello.
    run {
      val b = bench()
      b.selection.handOffBegan(center(0))
      b.selection.hover(center(1))
      b.selection.release(Offset(500f, 500f))
      assertEquals(emptyList<Int>(), b.picks)
    }
    // Su una voce disabilitata.
    run {
      val b = bench(rows(listOf(true, false, true)))
      b.selection.handOffBegan(center(0))
      b.selection.hover(center(2))
      b.selection.hover(center(1))
      assertEquals("la disabilitata non sta mai sotto la goccia", -1, b.selection.hovered)
      b.selection.release(center(1))
      assertEquals(emptyList<Int>(), b.picks)
    }
    // Nel padding fra le righe (qui un separatore da mezzo pixel).
    run {
      val g = FluidMenuGeometry(
        cells = listOf(Rect(0f, 0f, 200f, 47.5f), Rect(0f, 48f, 200f, 95.5f)),
        enabled = listOf(true, true),
        panel = Rect(0f, 0f, 200f, 96f),
      )
      val b = bench(g)
      b.selection.handOffBegan(center(0))
      b.selection.hover(center(1))
      b.selection.release(Offset(100f, 47.75f))
      assertEquals(emptyList<Int>(), b.picks)
    }
  }

  @Test
  fun onceArmedAFingerThatComesBackNearTheOriginStaysArmed() {
    val b = bench()
    val s = b.selection
    s.handOffBegan(center(0))
    s.hover(center(1)) // armato
    s.hover(center(0)) // di nuovo vicino all'origine

    s.release(center(0))

    // Si e' mosso e ha deciso: la voce da cui e' partito e' una scelta come un'altra.
    assertEquals(listOf(0), b.picks)
  }

  @Test
  fun aFastReleaseFarFromTheLastMoveStillCountsAsMoved() {
    // Nessun hover fra il down e l'up (un dito veloce o un evento saltato): il sollevamento stesso e'
    // lontano dall'origine, quindi il dito si e' mosso.
    val b = bench()
    val s = b.selection
    s.handOffBegan(center(0))

    s.release(center(2))

    assertEquals(listOf(2), b.picks)
  }

  @Test
  fun hoveringADisabledRowDropsTheDropWithoutATick() {
    val b = bench(rows(listOf(true, false, true)))
    val s = b.selection
    s.handOffBegan(center(0))
    s.hover(center(0) + Offset(0f, 20f)) // armato, ma ancora sulla riga 0: tick d'ingresso
    assertEquals(0, s.hovered)
    val afterEntry = b.ticks

    s.hover(center(1)) // disabilitata
    assertEquals(-1, s.hovered)
    assertEquals(afterEntry, b.ticks)

    s.hover(center(2)) // abilitata: un'altra voce, tick
    assertEquals(2, s.hovered)
    assertEquals(afterEntry + 1, b.ticks)
  }

  // --- Il dito sceso sul pannello ----------------------------------------------------------

  @Test
  fun aPanelDownTakesTheItemWithoutATickAndACrossingTicks() {
    val b = bench()
    val s = b.selection

    s.panelDown(center(0))
    assertEquals(FluidMenuOwner.Panel, s.owner)
    assertEquals(0, s.hovered)
    assertEquals("il down e' una presa, non un cambio di voce", 0, b.ticks)

    s.panelMove(center(0) + Offset(2f, 3f))
    assertEquals(0, b.ticks)

    s.panelMove(center(1))
    assertEquals(1, s.hovered)
    assertEquals(1, b.ticks)

    s.panelUp(center(1))
    assertEquals(listOf(1), b.picks)
    assertEquals(FluidMenuOwner.None, s.owner)
    assertEquals(-1, s.hovered)
  }

  @Test
  fun aTapOnAnEnabledItemPicksItWithoutMoving() {
    val b = bench()
    val s = b.selection

    s.panelDown(center(1))
    s.panelUp(center(1))

    assertEquals(listOf(1), b.picks)
    assertEquals(0, b.ticks)
  }

  @Test
  fun aTapOnADisabledItemOrOutsideDoesNotPick() {
    val b = bench(rows(listOf(true, false, true)))
    val s = b.selection

    s.panelDown(center(1))
    assertEquals(-1, s.hovered)
    s.panelUp(center(1))
    assertEquals(emptyList<Int>(), b.picks)

    s.panelDown(Offset(300f, 40f))
    s.panelUp(Offset(300f, 40f))
    assertEquals(emptyList<Int>(), b.picks)
    assertEquals(0, b.ticks)
  }

  @Test
  fun aPanelFingerThatLiftsOffTheItemsLeavesTheMenuOpen() {
    val b = bench()
    val s = b.selection
    s.panelDown(center(0))
    s.panelMove(center(1))

    s.panelUp(Offset(100f, 400f)) // sollevato sotto il pannello

    assertEquals(emptyList<Int>(), b.picks)
    assertEquals(FluidMenuOwner.None, s.owner)
  }

  @Test
  fun aCancelledPanelGestureNeverPicks() {
    val b = bench()
    val s = b.selection
    s.panelDown(center(2))

    s.panelCancel()

    assertEquals(emptyList<Int>(), b.picks)
    assertEquals(FluidMenuOwner.None, s.owner)
    assertEquals(-1, s.hovered)
    // E il sollevamento che arriva dopo non e' di nessuno.
    s.panelUp(center(2))
    assertEquals(emptyList<Int>(), b.picks)
  }

  // --- Un proprietario alla volta ---------------------------------------------------------

  @Test
  fun aPanelGestureIsIgnoredWhileTheHandedOffFingerHoldsTheMenu() {
    val b = bench()
    val s = b.selection
    s.handOffBegan(center(0))
    s.hover(center(1))
    assertEquals(1, s.hovered)
    val ticks = b.ticks

    s.panelDown(center(2))
    s.panelMove(center(2))
    s.panelUp(center(2))

    assertEquals(FluidMenuOwner.HandOff, s.owner)
    assertEquals("il secondo proprietario non sposta la goccia", 1, s.hovered)
    assertEquals(ticks, b.ticks)
    assertEquals("e non sceglie", emptyList<Int>(), b.picks)

    // Il dito passato, intanto, decide lui.
    s.release(center(1))
    assertEquals(listOf(1), b.picks)
  }

  @Test
  fun aHandOffIsIgnoredWhileThePanelFingerHoldsTheMenu() {
    val b = bench()
    val s = b.selection
    s.panelDown(center(0))

    s.handOffBegan(center(2))
    s.hover(center(2))
    s.release(center(2))

    assertEquals(FluidMenuOwner.Panel, s.owner)
    assertEquals(0, s.hovered)
    assertEquals(emptyList<Int>(), b.picks)

    s.panelUp(center(0))
    assertEquals(listOf(0), b.picks)
  }

  @Test
  fun afterARelaxedHandOffThePanelTakesTaps() {
    // "Tieni e rilascia fermo, poi tocco sulla 1": il menu e' rimasto aperto e il tocco sceglie.
    val b = bench()
    val s = b.selection
    s.handOffBegan(center(0))
    s.release(center(0))
    assertEquals(emptyList<Int>(), b.picks)

    s.panelDown(center(1))
    s.panelUp(center(1))

    assertEquals(listOf(1), b.picks)
  }

  @Test
  fun cancelDismissesWhoeverHoldsTheFingerWithoutPicking() {
    for (viaPanel in listOf(false, true)) {
      val b = bench()
      val s = b.selection
      if (viaPanel) {
        s.panelDown(center(1))
      } else {
        s.handOffBegan(center(0))
        s.hover(center(1))
      }
      assertEquals(1, s.hovered)

      s.cancel()

      assertEquals(FluidMenuOwner.None, s.owner)
      assertEquals(-1, s.hovered)
      assertEquals(emptyList<Int>(), b.picks)
      // Quel che arriva dopo l'annullo non ha un proprietario.
      s.release(center(1))
      s.panelUp(center(1))
      assertEquals(emptyList<Int>(), b.picks)
    }
  }

  // --- La geometria che arriva tardi ---------------------------------------------------------

  @Test
  fun aHoverThatArrivesBeforeTheGeometryIsReplayedWhenItArrives() {
    val b = bench(geometry = null)
    val s = b.selection
    s.handOffBegan(center(0))

    s.hover(center(1)) // armato, ma il pannello non ha ancora scritto le celle
    assertEquals(-1, s.hovered)
    assertEquals(0, b.ticks)

    s.geometry = rows()

    assertEquals(1, s.hovered)
    assertEquals(1, b.ticks)
  }

  @Test
  fun onlyTheLatestEarlyHoverIsReplayed() {
    val b = bench(geometry = null)
    val s = b.selection
    s.handOffBegan(center(0))
    s.hover(center(1))
    s.hover(center(2))

    s.geometry = rows()

    assertEquals(2, s.hovered)
    assertEquals("una sola rigiocata, non una per movimento perso", 1, b.ticks)
  }

  @Test
  fun aPanelDownBeforeTheGeometryReplaysWithoutATick() {
    val b = bench(geometry = null)
    val s = b.selection
    s.panelDown(center(1))
    assertEquals(-1, s.hovered)

    s.geometry = rows()

    assertEquals(1, s.hovered)
    assertEquals("e' sempre un down: niente tick", 0, b.ticks)
  }

  @Test
  fun aReleaseWithoutGeometryLeavesTheMenuOpen() {
    val b = bench(geometry = null)
    val s = b.selection
    s.handOffBegan(center(0))
    s.hover(center(2))

    s.release(center(2))

    assertEquals(emptyList<Int>(), b.picks)
    assertEquals(FluidMenuOwner.None, s.owner)
    // Una geometria che arriva dopo un rilascio non rigioca niente: il dito non c'e' piu'.
    s.geometry = rows()
    assertEquals(-1, s.hovered)
    assertEquals(0, b.ticks)
  }

  @Test
  fun anUnarmedHoverBeforeTheGeometryIsNotWorthReplaying() {
    val b = bench(geometry = null)
    val s = b.selection
    s.handOffBegan(center(0))
    s.hover(center(0) + Offset(2f, 2f)) // dentro lo slop

    s.geometry = rows()

    assertEquals(-1, s.hovered)
    assertEquals(0, b.ticks)
  }

  // --- La scelta chiude lo stato prima della callback --------------------------------------

  @Test
  fun thePickCallbackSeesAMenuThatNoLongerHoldsTheFinger() {
    var ownerInCallback: FluidMenuOwner? = null
    var heldInCallback: Boolean? = null
    lateinit var selection: FluidMenuSelection
    selection = FluidMenuSelection(
      touchSlop = slop,
      onPick = {
        ownerInCallback = selection.owner
        heldInCallback = selection.held
      },
    )
    selection.geometry = rows()

    selection.panelDown(center(1))
    selection.panelUp(center(1))

    assertEquals(FluidMenuOwner.None, ownerInCallback)
    assertEquals(false, heldInCallback)
  }
}
