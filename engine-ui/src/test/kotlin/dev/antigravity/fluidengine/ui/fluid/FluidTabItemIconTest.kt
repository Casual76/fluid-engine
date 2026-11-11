package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Home
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Home
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Quale icona disegna una scheda ([FluidTabItem.iconFor], 4.0): la gemella piena se la scheda e' quella scelta e
 * l'app l'ha data, altrimenti l'icona di sempre.
 *
 * Perche' esiste. In Apple la scheda scelta della tab bar e' piena e le altre al tratto (R6); `FluidTabItem` aveva
 * una sola icona. La scelta e' un parametro in coda e non una ricerca nel set dell'engine: qui si prova che
 * vale per il set, per una coppia Material e per una voce scritta come nella 3.x, che non cambia.
 */
class FluidTabItemIconTest {

  @Test
  fun `una voce della 3_x disegna la sua icona, scelta o no`() {
    val item = FluidTabItem("home", "Home", Icons.Rounded.Home)
    assertNull(item.selectedIcon)
    assertSame(Icons.Rounded.Home, item.iconFor(selected = false))
    assertSame(Icons.Rounded.Home, item.iconFor(selected = true))
  }

  @Test
  fun `la scheda scelta prende la gemella piena del set, le altre no`() {
    val item = FluidTabItem("home", "Home", icon = FluidIcons.Home, selectedIcon = FluidIcons.Filled.Home)
    assertSame(FluidIcons.Home, item.iconFor(selected = false))
    assertSame(FluidIcons.Filled.Home, item.iconFor(selected = true))
    assertNotSame(item.iconFor(selected = false), item.iconFor(selected = true))
  }

  @Test
  fun `vale per qualunque coppia, anche Material`() {
    val item = FluidTabItem("home", "Home", Icons.Outlined.Home, selectedIcon = Icons.Rounded.Home)
    assertSame(Icons.Outlined.Home, item.iconFor(selected = false))
    assertSame(Icons.Rounded.Home, item.iconFor(selected = true))
  }

  @Test
  fun `un'icona senza gemella resta la stessa da scelta`() {
    // `search` ha la piena nel set, ma la voce non la chiede: l'engine non la va a cercare.
    val item = FluidTabItem("search", "Cerca", FluidIcons.Search)
    assertSame(FluidIcons.Search, item.iconFor(selected = true))
  }

  @Test
  fun `la gemella entra nell'uguaglianza della voce, in coda`() {
    val plain = FluidTabItem("home", "Home", FluidIcons.Home)
    val twin = FluidTabItem("home", "Home", FluidIcons.Home, null, FluidIcons.Filled.Home)
    assertEquals(plain, FluidTabItem(route = "home", label = "Home", icon = FluidIcons.Home))
    assertEquals(twin.copy(selectedIcon = null), plain)
    assertEquals(FluidIcons.Filled.Home, twin.component5())
  }
}
