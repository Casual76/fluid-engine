package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cosa finisce dentro una finestra ancorata.
 *
 * Il caso che conta è il terzo: `Expand` con un contenuto e nessuna azione. Prima disegnava la
 * lista di azioni comunque, cioè niente, e il pannello si apriva vuoto — sagoma in viaggio, scrim,
 * ancora nascosta, e dentro il nulla. Un `content` ignorato senza dirlo non si scopre leggendo il
 * codice del chiamante, e questo test è il posto dove se ne accorgerebbe qualcuno.
 */
class FluidGlassModalContentTest {

  @Test
  fun `il menu contestuale disegna le proprie azioni`() {
    assertTrue(
      fluidModalShowsActions(FluidGlassModalPresentation.ContextMenu, hasActions = true),
    )
  }

  @Test
  fun `il tasto che si espande disegna le proprie azioni`() {
    assertTrue(fluidModalShowsActions(FluidGlassModalPresentation.Expand, hasActions = true))
  }

  @Test
  fun `senza azioni da elencare vince il contenuto del chiamante`() {
    for (presentation in FluidGlassModalPresentation.entries) {
      assertFalse(
        "$presentation senza azioni deve disegnare il contenuto",
        fluidModalShowsActions(presentation, hasActions = false),
      )
    }
  }

  @Test
  fun `un pop-up e un foglio disegnano il contenuto anche con delle azioni in giro`() {
    // Le azioni appartengono all'ancora che le ha registrate, non alla finestra: una presentazione
    // che non le ha mai chieste non deve ereditarle da un'apertura precedente.
    assertFalse(fluidModalShowsActions(FluidGlassModalPresentation.Popover, hasActions = true))
    assertFalse(fluidModalShowsActions(FluidGlassModalPresentation.Sheet, hasActions = true))
  }
}
