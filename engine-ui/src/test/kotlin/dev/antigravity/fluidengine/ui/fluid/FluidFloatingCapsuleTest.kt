package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dove sta una capsula flottante rispetto alla barra di navigazione. Sono numeri, e un numero
 * sbagliato di dieci dp e' una capsula che copre la barra o che le fluttua sopra con un buco: si
 * prova qui, non guardando uno schermo.
 */
class FluidFloatingCapsuleTest {

  private val free = FluidFloatingCapsulePlacement.Free
  private val replacing = FluidFloatingCapsulePlacement.ReplacingTabBar
  private val above = FluidFloatingCapsulePlacement.AboveTabBar

  @Test
  fun `a free capsule has no offset and asks nothing of the page`() {
    assertEquals(0.dp, FluidFloatingCapsuleDefaults.bottomOffset(free))
    assertEquals(0.dp, FluidFloatingCapsuleDefaults.contentInset(free))
  }

  @Test
  fun `replacing the tab bar uses the bar's own bottom margin`() {
    assertEquals(
      FluidFloatingTabBarDefaults.BottomMargin,
      FluidFloatingCapsuleDefaults.bottomOffset(replacing),
    )
  }

  @Test
  fun `replacing the tab bar leaves the page the same inset the bar did`() {
    // Il punto di sostituirla: la pagina sotto non cambia lo spazio che si e' lasciata.
    assertEquals(
      FluidFloatingTabBarDefaults.ContentInset,
      FluidFloatingCapsuleDefaults.contentInset(replacing),
    )
  }

  @Test
  fun `a taller capsule in place of the bar raises the inset by the difference`() {
    val taller = FluidFloatingTabBarDefaults.OpenHeight + 12.dp
    assertEquals(
      FluidFloatingTabBarDefaults.ContentInset + 12.dp,
      FluidFloatingCapsuleDefaults.contentInset(replacing, capsuleHeight = taller),
    )
  }

  @Test
  fun `a shorter capsule never lowers the inset under the bar's own`() {
    // Una capsula bassa che prende il posto della barra non libera spazio: la barra tornera'.
    assertEquals(
      FluidFloatingTabBarDefaults.ContentInset,
      FluidFloatingCapsuleDefaults.contentInset(replacing, capsuleHeight = 40.dp),
    )
  }

  @Test
  fun `above the tab bar the capsule sits one spacing over the bar's top`() {
    assertEquals(
      FluidFloatingTabBarDefaults.ContentInset + FluidFloatingTabBarDefaults.Spacing,
      FluidFloatingCapsuleDefaults.bottomOffset(above),
    )
  }

  @Test
  fun `above the tab bar the page inset is exactly the capsule's top edge`() {
    // Nessun buco e nessuna sovrapposizione: lo spazio che la pagina si lascia finisce dove comincia
    // il bordo alto della capsula.
    val height = FluidFloatingCapsuleDefaults.Height
    assertEquals(
      FluidFloatingCapsuleDefaults.bottomOffset(above) + height,
      FluidFloatingCapsuleDefaults.contentInset(above, height),
    )
    val tall = 72.dp
    assertEquals(
      FluidFloatingCapsuleDefaults.bottomOffset(above) + tall,
      FluidFloatingCapsuleDefaults.contentInset(above, tall),
    )
  }

  @Test
  fun `the page inset above the bar matches the bar's own accessory rule`() {
    assertEquals(
      FluidFloatingTabBarDefaults.contentInsetWithAccessory(FluidFloatingCapsuleDefaults.Height),
      FluidFloatingCapsuleDefaults.contentInset(above),
    )
  }

  @Test
  fun `the default height is the open tab bar and holds a full-size action with its padding`() {
    assertEquals(FluidFloatingTabBarDefaults.OpenHeight, FluidFloatingCapsuleDefaults.Height)
    // 48 dp di azione piu' il margine sopra e sotto fanno l'altezza della barra: la capsula non deve crescere per
    // contenerla. Cambio voluto della 4.0: la barra e' alta 62 (R6, era 60), e il margine e' passato da 6 a 7 dp
    // perche' la regola resti vera; prima il test diceva `ActionSize + 12.dp`, cioe' 60.
    val vertical = FluidFloatingCapsuleDefaults.ContentPadding.calculateTopPadding() +
      FluidFloatingCapsuleDefaults.ContentPadding.calculateBottomPadding()
    assertEquals(FluidFloatingCapsuleDefaults.Height, FluidFloatingCapsuleDefaults.ActionSize + vertical)
    assertEquals(62.dp, FluidFloatingCapsuleDefaults.Height)
    assertEquals(7.dp, FluidFloatingCapsuleDefaults.ContentPadding.calculateTopPadding())
  }

  @Test
  fun `the touch target of an action never drops under 48 dp by default`() {
    assertTrue(FluidFloatingCapsuleDefaults.ActionSize >= 48.dp)
  }
}
