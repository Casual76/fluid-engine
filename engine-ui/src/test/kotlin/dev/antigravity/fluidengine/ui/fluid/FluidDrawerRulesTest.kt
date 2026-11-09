package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.page.FluidPageMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole pure del pannello laterale (pm-f1/ge): quanto e' largo, quando un rilascio lo chiude, cosa
 * si salva, e il segno delle zone della barra.
 */
class FluidDrawerRulesTest {

  // --- Larghezza ----------------------------------------------------------------------------------

  @Test
  fun `su un telefono stretto il pannello lascia il 15 per cento di pagina`() {
    assertEquals(306f, fluidDrawerWidth(FluidDrawerDefaults.Width, 360.dp).value, 0.01f)
  }

  @Test
  fun `con spazio il pannello e' largo quanto chiesto, non di piu'`() {
    assertEquals(320f, fluidDrawerWidth(FluidDrawerDefaults.Width, 800.dp).value, 0.01f)
    assertEquals(280f, fluidDrawerWidth(280.dp, 800.dp).value, 0.01f)
  }

  @Test
  fun `una finestra senza larghezza non da' un pannello negativo`() {
    assertEquals(0f, fluidDrawerWidth(FluidDrawerDefaults.Width, 0.dp).value, 0.0f)
    assertEquals(0f, fluidDrawerWidth((-10).dp, 400.dp).value, 0.0f)
  }

  // --- Rilascio: la regola del back -------------------------------------------------------------

  private val density = 2f
  private val travel = 636f

  @Test
  fun `un rilascio lento decide con la distanza, come il back`() {
    // 35% di 636 = 222,6 px (sotto il tetto di 180 dp = 360 px).
    assertFalse(fluidDrawerReleaseCloses(closedPx = 200f, velocityPx = 0f, travelPx = travel, density = density))
    assertTrue(fluidDrawerReleaseCloses(closedPx = 230f, velocityPx = 0f, travelPx = travel, density = density))
  }

  @Test
  fun `una spinta verso il bordo chiude anche se corta, una contraria lo tiene aperto anche se lunga`() {
    val fling = FluidPageMotion.FlingDpPerSecond * density
    assertTrue(fluidDrawerReleaseCloses(closedPx = 20f, velocityPx = fling, travelPx = travel, density = density))
    assertFalse(fluidDrawerReleaseCloses(closedPx = 500f, velocityPx = -fling, travelPx = travel, density = density))
  }

  @Test
  fun `la regola e' quella delle pagine, tale e quale`() {
    for (closed in listOf(0f, 100f, 222f, 223f, 400f, 636f)) {
      for (velocity in listOf(-2000f, -799f, 0f, 799f, 800f, 2000f)) {
        assertEquals(
          FluidPageMotion.backRelease(closed, velocity, travel, density),
          fluidDrawerReleaseCloses(closed, velocity, travel, density),
        )
      }
    }
  }

  // --- Stato ------------------------------------------------------------------------------------

  @Test
  fun `lo stato salva solo aperto o chiuso, e ritorna fermo`() {
    val open = FluidDrawerState(initiallyOpen = true)
    assertEquals(1f, open.progress, 0f)
    assertTrue(open.isOnScreen)
    val closed = FluidDrawerState()
    assertEquals(0f, closed.progress, 0f)
    assertFalse(closed.isOnScreen)

    open.progressState.floatValue = 0.37f
    val restored = FluidDrawerState(initiallyOpen = true)
    assertEquals("ritorna aperto del tutto, mai a meta'", 1f, restored.progress, 0f)
  }

  @Test
  fun `open e close cambiano l'obiettivo, snapTo anche la posizione`() {
    val state = FluidDrawerState()
    state.open()
    assertTrue(state.isOpen)
    assertEquals("la molla la muove il pannello, non lo stato", 0f, state.progress, 0f)
    assertTrue("aperto e' in scena anche prima che la molla parta", state.isOnScreen)
    state.snapTo(open = true)
    assertEquals(1f, state.progress, 0f)
    state.close()
    assertFalse(state.isOpen)
    assertTrue("chiudendo e' ancora in scena finche' non rientra", state.isOnScreen)
    state.snapTo(open = false)
    assertFalse(state.isOnScreen)
  }

  // --- Il segno delle zone ------------------------------------------------------------------------

  @Test
  fun `il pallino dice novita', il numero la quantita' vera`() {
    val labels = FluidLabels()
    assertEquals("Novità", fluidTabBadgePhrase(FluidTabBadge.Dot, labels))
    assertEquals("3 novità", fluidTabBadgePhrase(FluidTabBadge(count = 3), labels))
    assertEquals("150 novità", fluidTabBadgePhrase(FluidTabBadge(count = 150), labels))
    assertEquals("150 new", fluidTabBadgePhrase(FluidTabBadge(count = 150), FluidLabels.english()))
    assertEquals("New", fluidTabBadgePhrase(FluidTabBadge.Dot, FluidLabels.english()))
  }

  @Test
  fun `senza segno, o con un numero a zero, non c'e' niente da dire`() {
    val labels = FluidLabels()
    assertEquals(null, fluidTabBadgePhrase(null, labels))
    assertEquals(null, fluidTabBadgePhrase(FluidTabBadge(count = 0), labels))
    assertEquals(null, fluidTabBadgePhrase(FluidTabBadge(count = -2, contentDescription = "x"), labels))
  }

  @Test
  fun `la frase dell'app vince sulle parole dell'engine`() {
    assertEquals("2 messaggi", fluidTabBadgePhrase(FluidTabBadge(count = 2, contentDescription = "2 messaggi"), FluidLabels()))
  }

  @Test
  fun `una voce costruita con tre argomenti non ha segno`() {
    val glyph = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).build()
    val item = FluidTabItem("a", "A", glyph)
    assertEquals(null, item.badge)
    val (route, label) = item
    assertEquals("a", route)
    assertEquals("A", label)
  }
}
