package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedScrim
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Il velo della `Dialog` di `FluidAlert` fuori dall'host (F1B, giro di correzione; C, R12): col materiale nuovo lo
 * scrim dei pannelli dipinti, col vetro di prima il velo di piattaforma che la finestra aveva. Si applica in tutti e
 * due i rami, quindi un cambio di modello con la stessa `Dialog` aperta lo rimette.
 */
class FluidSheetPaintedDimTest {

  @Test
  fun liquidTakesThePaintedScrimAndClassicGivesThePlatformDimBack() {
    for (dark in listOf(false, true)) {
      assertEquals(GlassPaintedScrim.alpha(dark), paintedDialogDim(liquid = true, darkSide = dark, platformDim = 0.6f), 0f)
      assertEquals(0.6f, paintedDialogDim(liquid = false, darkSide = dark, platformDim = 0.6f), 0f)
      assertEquals(0.32f, paintedDialogDim(liquid = false, darkSide = dark, platformDim = 0.32f), 0f)
    }
    // Avanti e indietro con la stessa finestra: il velo di partenza torna identico.
    val platform = 0.6f
    var dim = platform
    for (liquid in listOf(true, false, true, false)) dim = paintedDialogDim(liquid, darkSide = false, platformDim = platform)
    assertEquals(platform, dim, 0f)
  }
}
