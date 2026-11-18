package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.theme.FluidListTextInsets
import dev.antigravity.fluidengine.ui.theme.FluidToneTileSize
import dev.antigravity.fluidengine.ui.theme.fluidListRowTextStartDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le misure di liste e campi di F6.6: dove comincia il separatore (sotto il testo, come in Apple), e il campo pieno
 * (la capsula da 40 pt del campo di iOS 27, il grigio di riempimento).
 */
class FluidListMetricsTest {

  @Test
  fun `il separatore comincia dove comincia il testo della riga`() {
    assertEquals(16f, fluidListRowTextStartDp(hasLeading = false, leadingWidthDp = Float.NaN)!!, 0f)
    // La piastrella da 32 dp, piu' i 16 dp di ListItem prima e dopo.
    assertEquals(64f, fluidListRowTextStartDp(hasLeading = true, leadingWidthDp = FluidToneTileSize.value)!!, 0f)
    assertEquals(16f + 40f + 16f, fluidListRowTextStartDp(hasLeading = true, leadingWidthDp = 40f)!!, 0f)
    assertNull("un leading non ancora misurato non dice niente", fluidListRowTextStartDp(true, Float.NaN))
  }

  @Test
  fun `in un gruppo misto vince il rientro piu' piccolo, e una riga che esce non conta piu'`() {
    val insets = FluidListTextInsets()
    assertNull(insets.startDp)
    val withIcon = Any()
    val plain = Any()
    insets.report(withIcon, 64f)
    assertEquals(64f, insets.startDp!!, 0f)
    insets.report(plain, 16f)
    assertEquals(16f, insets.startDp!!, 0f)
    insets.remove(plain)
    assertEquals(64f, insets.startDp!!, 0f)
  }

  @Test
  fun `il campo e' la capsula da 40 dp di Apple, e su piu' righe ha il suo raggio`() {
    assertEquals(40f, FluidTextFieldDefaults.MinHeight.value, 0f)
    assertEquals(FluidTextFieldDefaults.MinHeight.value / 2f, FluidTextFieldDefaults.MultilineRadius.value, 0f)
    // Il contenuto (40 meno gli 8 dp sopra e sotto) e' alto quanto lo slot a destra: il campo non cresce per lui.
    assertEquals(
      FluidFieldTrailingSize.value,
      FluidTextFieldDefaults.MinHeight.value - 2f * FluidTextFieldVerticalPadding.value,
      0f,
    )
  }

  @Test
  fun `il grigio di riempimento toglie 18 livelli sul bianco`() {
    // `onSurface` chiaro dell'engine ~ 27 di luma; Apple: pagina 253, campo 235 (sessione 278, 11:15).
    val onSurface = 27f
    val page = 253f
    val field = page - fluidQuietFillAlpha(dark = false) * (page - onSurface)
    assertEquals(235f, field, 1.5f)
    assertEquals(0.12f, fluidQuietFillAlpha(dark = true), 0f)
  }
}
