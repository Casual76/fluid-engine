package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La politica di colonne dell'intestazione editoriale.
 *
 * Vale la pena testarla perche' si rompe dove nessuno guarda: non sul telefono di chi la scrive, ma
 * su uno stretto o con la scala del carattere alzata, dove tre celle affiancate diventano tre
 * colonne di testo tagliato.
 */
class FluidHeroLayoutTest {

  @Test
  fun roomyPhone_keepsThreeMetricsOnOneRow() {
    assertEquals(
      3,
      fluidHeroMetricColumnCount(
        availableWidthDp = 331f,
        fontScale = 1f,
        metricCount = 3,
      ),
    )
  }

  @Test
  fun widthNearOldBreakpoint_usesTwoColumnsInsteadOfFullStack() {
    assertEquals(
      2,
      fluidHeroMetricColumnCount(
        availableWidthDp = 285f,
        fontScale = 1f,
        metricCount = 3,
      ),
    )
  }

  @Test
  fun compactWidth_fallsBackToOneColumn() {
    assertEquals(
      1,
      fluidHeroMetricColumnCount(
        availableWidthDp = 215f,
        fontScale = 1f,
        metricCount = 3,
      ),
    )
  }

  @Test
  fun fontScale_reducesDensityProgressively() {
    assertEquals(2, fluidHeroMetricColumnCount(331f, 1.3f, 3))
    assertEquals(1, fluidHeroMetricColumnCount(331f, 1.6f, 3))
  }

  @Test
  fun columnCount_neverExceedsAvailableMetrics() {
    assertEquals(2, fluidHeroMetricColumnCount(700f, 1f, 2))
    assertEquals(0, fluidHeroMetricColumnCount(700f, 1f, 0))
  }
}
