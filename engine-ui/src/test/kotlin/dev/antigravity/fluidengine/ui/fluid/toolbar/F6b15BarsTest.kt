package dev.antigravity.fluidengine.ui.fluid.toolbar

import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsuleDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSearchFieldDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSearchTokenDefaults
import dev.antigravity.fluidengine.ui.fluid.fluidSearchFieldTokenRoomDp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F6B.15 · le regole pure delle barre e del campo dopo le riprese del 23 novembre 2026:
 * - la finestra non si sposta mai sopra la barra in alto (`adjustPan` → `adjustNothing`, prima di Android 11
 *   `adjustResize`; gli altri modi restano);
 * - i vetri della barra si compongono nell'ordine in cui sono nati, così un vetro nuovo non fa rifare gli altri;
 * - la barra in basso sta a 28 dp dal bordo dello schermo, come iOS 26, una misura sola;
 * - «Invia» da 44 nel campo aperto da 52;
 * - lo spazio della fila delle pillole e quello dei gettoni nei campi fuori dalla barra.
 */
class F6b15BarsTest {

  // --- La finestra ---------------------------------------------------------------------------------------------------

  @Test fun `adjustPan e il modo non dichiarato diventano un modo che non sposta la finestra, gli altri restano`() {
    // Da Android 11: adjustNothing (sul Tab S9, Android 16, adjustResize restava trattato da adjustPan).
    assertEquals(SOFT_INPUT_ADJUST_NOTHING, fluidFieldSoftInputMode(SOFT_INPUT_ADJUST_PAN, sdk = 36))
    assertEquals(SOFT_INPUT_ADJUST_NOTHING, fluidFieldSoftInputMode(SOFT_INPUT_ADJUST_UNSPECIFIED, sdk = 30))
    // Prima di Android 11: adjustResize.
    assertEquals(SOFT_INPUT_ADJUST_RESIZE, fluidFieldSoftInputMode(SOFT_INPUT_ADJUST_PAN, sdk = 29))
    // Chi ha già dichiarato un modo che non sposta non cambia.
    assertEquals(SOFT_INPUT_ADJUST_RESIZE, fluidFieldSoftInputMode(SOFT_INPUT_ADJUST_RESIZE, sdk = 36))
    assertEquals(SOFT_INPUT_ADJUST_NOTHING, fluidFieldSoftInputMode(SOFT_INPUT_ADJUST_NOTHING, sdk = 36))
    // I bit dello stato della tastiera e della navigazione in avanti restano com'erano.
    assertEquals(SOFT_INPUT_STATE_ALWAYS_HIDDEN or SOFT_INPUT_ADJUST_NOTHING,
      fluidFieldSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN or SOFT_INPUT_ADJUST_PAN, sdk = 36))
    assertEquals(0x100 or SOFT_INPUT_ADJUST_NOTHING, fluidFieldSoftInputMode(0x100, sdk = 36))
  }

  // --- L'ordine dei vetri della barra --------------------------------------------------------------------------------

  private fun glass(vararg members: Any) = FluidToolbarGlassId(FluidToolbarGroupId(members.toSet()))

  @Test fun `un vetro nuovo va in fondo all'ordine, e chi resta non si sposta`() {
    val back = glass("indietro")
    val filters = glass("filtri")
    val more = glass(FluidToolbarOverflowId)
    val pill = glass("fluid:activity")
    val before = fluidToolbarComposeOrder(emptyList(), listOf(back, filters, more))
    assertEquals(listOf(back, filters, more), before)
    // La pillola di attività nasce fra l'indietro e «Filtri» (al posto del titolo): nell'ordine va in fondo.
    val during = fluidToolbarComposeOrder(before, listOf(back, pill, filters, more))
    assertEquals(listOf(back, filters, more, pill), during)
    // Se ne va: chi resta non si sposta.
    assertEquals(listOf(back, filters, more), fluidToolbarComposeOrder(during, listOf(back, filters, more)))
  }

  // --- La barra in basso ---------------------------------------------------------------------------------------------

  @Test fun `la barra in basso ha il fondo del vetro a 28 dp dal bordo dello schermo, come iOS 26`() {
    assertEquals(28.dp, FluidBottomBarPlacement.ScreenEdgeDistance)
    // Coi gesti (24 dp di barra di sistema) restano 4 dp sopra la barra di sistema: il fondo a 28, il centro a 52.
    val gesture = 24.dp
    val lift = fluidBottomBarLift(0.dp, gesture)
    assertEquals(28.dp, gesture + lift)
    assertEquals(52.dp, gesture + lift + FluidFloatingCapsuleDefaults.ActionSize / 2)
    // Senza barra di sistema (un tablet coi gesti nascosti): 28 dal bordo.
    assertEquals(28.dp, fluidBottomBarLift(0.dp, 0.dp))
    // Coi tre tasti (48 dp) il vetro non ci scende mai sopra.
    assertEquals(FluidBottomBarPlacement.NavigationClearance, fluidBottomBarLift(0.dp, 48.dp))
    // Con la tab bar sta sopra di lei come prima, e la tab bar resta com'è.
    assertEquals(100.dp + FluidFloatingTabBarDefaults.Spacing, fluidBottomBarLift(100.dp, gesture))
    assertEquals(16.dp, FluidFloatingTabBarDefaults.BottomMargin)
    // La lista le lascia il posto: la barra e il suo margine.
    assertEquals(4.dp + 48.dp, fluidBottomBarExtraInset(0.dp, gesture))
  }

  // --- «Invia» da 44 nel campo da 52 ---------------------------------------------------------------------------------

  @Test fun `il disco di Invia e' largo quanto il posto, con 4 dp di vetro attorno nel campo da 52`() {
    assertEquals(52.dp, FluidSearchFieldDefaults.BarFieldHeight)
    assertEquals(44.dp, FluidSearchFieldDefaults.ActionSize)
    val air = (FluidSearchFieldDefaults.BarFieldHeight - FluidSearchFieldDefaults.ActionSize) / 2
    assertEquals(4.dp, air)
    assertEquals("lo stesso margine a destra", air, ToolbarSearchTrailingInset)
  }

  // --- La fila delle pillole e i gettoni fuori dalla barra -----------------------------------------------------------

  @Test fun `lo spazio della fila cresce con la sua entrata e i gettoni lasciano il testo`() {
    assertEquals(0f, toolbarSearchPillsRoomPx(96f, 16f, 0f), 0f)
    assertEquals(56f, toolbarSearchPillsRoomPx(96f, 16f, 0.5f), 0.001f)
    assertEquals(112f, toolbarSearchPillsRoomPx(96f, 16f, 1f), 0f)
    assertEquals(112f, toolbarSearchPillsRoomPx(96f, 16f, 2f), 0f)
    // Un campo da 360 dp con la lente (42) e due comandi (88): ai gettoni restano 360 − 42 − 88 − 64.
    assertEquals(360f - 42f - 88f - FluidSearchTokenDefaults.MinTextRoom.value, fluidSearchFieldTokenRoomDp(360f, 42f, 88f), 0.001f)
    assertEquals(0f, fluidSearchFieldTokenRoomDp(100f, 42f, 88f), 0f)
  }
}
