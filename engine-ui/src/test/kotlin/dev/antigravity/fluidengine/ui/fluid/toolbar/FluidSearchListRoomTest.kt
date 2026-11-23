package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsuleDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F6B.13: la lista sotto il campo cresciuto. Il campo in fondo cresce di riga in riga (F6B.7) e sale con la tastiera
 * (F6B.9); la lista aggiunge in fondo quanto il vetro del campo supera la riga a riposo, così a fine scorrimento l'ultima
 * riga si legge sopra il campo, con e senza tastiera. Qui la regola pura, in px a densità 1 (px = dp); la prova nella
 * schermata vera, a 360 e 412 dp, è `FluidScreenGrownFieldListTest` nella galleria.
 */
internal class FluidSearchListRoomTest {
  private val navigation = 24
  private val rest = fluidBottomBarOffset(0.dp).value.toInt()            // 16 dp senza tab bar
  private val row = FluidFloatingCapsuleDefaults.ActionSize.value          // 48 dp, la riga della barra in basso
  private val gap = ToolbarSearchImeGap.value.toInt()                      // 4 dp

  /** Lo spazio che la lista lascia già alla riga del campo: la barra di sistema, il margine e la riga (bottomExtra). */
  private val assumed = navigation + fluidBottomBarExtraInset(0.dp).value

  private fun fieldTop(keyboard: Int = 0, growth: Float = 0f, dock: Int? = null) =
    toolbarSearchRowBottomPx(navigation, rest, keyboard, gap, dock) + row + growth

  @Test fun atRestTheListKeepsTodaysMargin() {
    // Una riga, senza tastiera: lo spazio è quello di sempre, la lista non cambia.
    assertEquals(0f, toolbarSearchListExtraPx(fieldTop(), assumed), 0f)
  }

  @Test fun aGrownFieldAddsItsExtraHeight() {
    // Cinque righe del «Messaggio»: il vetro supera la riga di quattro righe di testo (4 × 23 dp, `bodyLarge`).
    assertEquals(92f, toolbarSearchListExtraPx(fieldTop(growth = 92f), assumed), 0f)
  }

  @Test fun theKeyboardLiftsTheFieldAndTheListWithIt() {
    // Una tastiera di 300 dp (con la barra di sistema dentro): il campo sta 4 dp sopra di lei.
    val lift = 300 + gap - (navigation + rest)
    assertEquals(lift.toFloat(), toolbarSearchListExtraPx(fieldTop(keyboard = 300), assumed), 0f)
    // Con cinque righe le due cose si sommano.
    assertEquals(lift + 92f, toolbarSearchListExtraPx(fieldTop(keyboard = 300, growth = 92f), assumed), 0f)
    // Una tastiera più bassa della posa a riposo (la sola barra dei suggerimenti di una tastiera fisica) non toglie niente.
    assertEquals(0f, toolbarSearchListExtraPx(fieldTop(keyboard = 30), assumed), 0f)
  }

  @Test fun aboveTheKeyboardBarTheFieldClearsTheBarToo() {
    // Con la barra sopra la tastiera in vista (F6B.9) il campo le sta sopra; la lista le lascia già il suo posto.
    val clearance = FluidKeyboardSearchClearance.value.toInt()
    val barRoom = FluidKeyboardBarHeight.value
    val top = fieldTop(keyboard = 300, dock = clearance)
    assertEquals(300 + clearance + row, top, 0f)
    assertEquals(top - (assumed + barRoom), toolbarSearchListExtraPx(top, assumed + barRoom), 0f)
  }

  @Test fun aRestingFieldThatRetractsTakesItsRoomAway() {
    assertEquals(46f, toolbarSearchListExtraPx(fieldTop(growth = 92f), assumed, hidden = 0.5f), 0f)
    assertEquals(0f, toolbarSearchListExtraPx(fieldTop(growth = 92f), assumed, hidden = 1f), 0f)
  }

  @Test fun aPannedWindowAlreadyCoversPartOfTheKeyboard() {
    // `adjustPan` (il modo di serie di un'Activity che non lo dichiara, come la galleria): Android sposta in su la radice
    // per mostrare il cursore. Sul Tab S9, misurato il 23 novembre 2026: tastiera di 799 px, finestra spostata di ~560 px.
    // La lista conta solo la parte di tastiera che la finestra spostata non ha già scavalcato.
    assertEquals(0, toolbarSearchWindowPanPx(windowTopPx = 0, rootTopPx = 0))
    assertEquals(560, toolbarSearchWindowPanPx(windowTopPx = 0, rootTopPx = -560))
    // Una radice più in basso della finestra (una finestra non a tutto schermo, una barra sopra) non è uno spostamento.
    assertEquals(0, toolbarSearchWindowPanPx(windowTopPx = 0, rootTopPx = 48))
    assertEquals(0, toolbarSearchWindowPanPx(windowTopPx = 300, rootTopPx = 300))
    val keyboard = 799 - toolbarSearchWindowPanPx(0, -560)
    assertEquals(navigation + rest, toolbarSearchRowBottomPx(navigation, rest, 0, gap))
    assertEquals(keyboard + gap, toolbarSearchRowBottomPx(navigation, rest, keyboard, gap))
  }

  @Test fun theRowBottomIsWhereTheOverlayPutsIt() {
    // A riposo sopra la barra di sistema col margine della barra in basso; la tastiera vince quando è più alta.
    assertEquals(navigation + rest, toolbarSearchRowBottomPx(navigation, rest, 0, gap))
    assertEquals(300 + gap, toolbarSearchRowBottomPx(navigation, rest, 300, gap))
    // Sopra la barra della tastiera: senza tastiera (fisica) sopra la barra di sistema.
    assertEquals(navigation + 64, toolbarSearchRowBottomPx(navigation, rest, 0, gap, dockClearancePx = 64))
  }
}
