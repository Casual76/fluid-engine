package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Home
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Home
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Quale icona disegnano la tab bar e il rail per una scheda ([FluidTabItem.barIcon], 4.0): la gemella piena se l'app
 * l'ha data, **su ogni scheda**, altrimenti l'icona di sempre.
 *
 * Perche' esiste. Nella tab bar di Apple le icone sono tutte piene, anche quelle delle schede non scelte (R6, piano
 * 3.14): la scelta si distingue per la pillola e il colore. `FluidTabItem` aveva una sola icona; la gemella e' un
 * parametro in coda e non una ricerca nel set dell'engine: qui si prova che vale per il set, per una coppia Material e
 * per una voce scritta come nella 3.x, che non cambia. Fino al giro di correzione di F1A la gemella andava sulla sola
 * scheda scelta (`selectedIcon`, mai pubblicato): e' la lettura sbagliata che questo test esclude.
 */
class FluidTabItemIconTest {

  @Test
  fun `una voce della 3_x disegna la sua icona`() {
    val item = FluidTabItem("home", "Home", Icons.Rounded.Home)
    assertNull(item.filledIcon)
    assertSame(Icons.Rounded.Home, item.barIcon)
  }

  @Test
  fun `la gemella piena del set va su ogni scheda, non solo sulla scelta`() {
    val item = FluidTabItem("home", "Home", icon = FluidIcons.Home, filledIcon = FluidIcons.Filled.Home)
    // L'icona della barra non dipende dalla scelta: non c'e' un parametro che la chieda.
    assertSame(FluidIcons.Filled.Home, item.barIcon)
    // L'icona al tratto resta quella della zona, per chi la vuole fuori dalla barra.
    assertSame(FluidIcons.Home, item.icon)
  }

  @Test
  fun `vale per qualunque coppia, anche Material`() {
    val item = FluidTabItem("home", "Home", Icons.Outlined.Home, filledIcon = Icons.Rounded.Home)
    assertSame(Icons.Rounded.Home, item.barIcon)
  }

  @Test
  fun `un'icona senza gemella resta la stessa`() {
    // `search` ha la piena nel set, ma la voce non la chiede: l'engine non la va a cercare.
    val item = FluidTabItem("search", "Cerca", FluidIcons.Search)
    assertSame(FluidIcons.Search, item.barIcon)
  }

  @Test
  fun `la gemella entra nell'uguaglianza della voce, in coda`() {
    val plain = FluidTabItem("home", "Home", FluidIcons.Home)
    val twin = FluidTabItem("home", "Home", FluidIcons.Home, null, FluidIcons.Filled.Home)
    assertEquals(plain, FluidTabItem(route = "home", label = "Home", icon = FluidIcons.Home))
    assertEquals(twin.copy(filledIcon = null), plain)
    assertEquals(FluidIcons.Filled.Home, twin.component5())
  }
}
