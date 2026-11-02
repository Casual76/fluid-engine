package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidGlassSizeTest {

  @Test
  fun lensIs40And44And54Dp() {
    assertEquals(40.dp, FluidGlassSize.Compact.lens)
    assertEquals(44.dp, FluidGlassSize.Regular.lens)
    assertEquals(54.dp, FluidGlassSize.Large.lens)
  }

  @Test
  fun theDefaultSizeIsTheOneEveryCallerAlreadyHas() {
    // Aggiungere una taglia non cambia quella di sempre: 44 dp di vetro nel riquadro da 48 per il
    // tasto tondo, 48 dp di altezza per quello con il testo.
    assertEquals(FluidGlassIconLensSize, FluidGlassSize.Regular.lens)
    assertEquals(FluidGlassIconTouchTargetSize, FluidGlassSize.Regular.touchTarget)
    assertEquals(FluidGlassButtonHeight, FluidGlassSize.Regular.buttonHeight)
  }

  @Test
  fun theTouchTargetIsNeverBelow48DpWhateverTheSize() {
    FluidGlassSize.entries.forEach { size ->
      assertTrue("$size: ${size.touchTarget}", size.touchTarget >= 48.dp)
      assertTrue("$size: il riquadro non puo' essere piu' piccolo del vetro", size.touchTarget >= size.lens)
    }
    assertEquals(48.dp, FluidGlassSize.Compact.touchTarget)
    // Oltre i 48 dp il vetro e' il proprio bersaglio: nessun riquadro in piu' attorno a una lente da 54.
    assertEquals(54.dp, FluidGlassSize.Large.touchTarget)
  }

  @Test
  fun aTextButtonIsAtLeastAsTallAsTheIconLensOfTheSameSize() {
    FluidGlassSize.entries.forEach { size ->
      assertTrue("$size", size.buttonHeight >= size.lens)
    }
  }

  @Test
  fun theLabelPaddingAndTheLeadingIconGrowWithTheHeight() {
    val sizes = FluidGlassSize.entries.sortedBy { it.buttonHeight.value }
    sizes.zipWithNext().forEach { (smaller, larger) ->
      assertTrue("$smaller -> $larger", smaller.buttonPadding <= larger.buttonPadding)
      assertTrue("$smaller -> $larger", smaller.buttonLeading <= larger.buttonLeading)
    }
  }

  @Test
  fun theIconSitsAtHalfTheLens() {
    assertEquals(20.dp, FluidGlassSize.Compact.iconSize)
    assertEquals(22.dp, FluidGlassSize.Regular.iconSize)
    assertEquals(27.dp, FluidGlassSize.Large.iconSize)
  }

  @Test
  fun theRoundButtonGrowsLibrePodsOnTheSizesUpToTheTouchTarget() {
    // La crescita di un tasto tondo e' una frazione (LibrePods: x1,5) finche' il tetto non entra in
    // gioco, e il tetto (24 dp in tutto) sta al bersaglio minimo del tocco: le taglie che l'engine ha
    // sempre avuto, 40 e 44 dp, sono intere come prima.
    listOf(FluidGlassSize.Compact, FluidGlassSize.Regular).forEach { size ->
      val growth = fluidPressGrowth(FluidPressFeedback.ExpandRound, size.lens.value, size.lens.value, dp = 1f)
      assertEquals("$size", RoundMaxScale, growth, 0.0001f)
      val peak = size.lens.value * (1f + growth)
      assertEquals("$size", size.lens.value * 1.5f, peak, 0.001f)
    }
  }

  @Test
  fun theRoundButtonAboveTheTouchTargetGrowsByTheCapAndNoMore() {
    // Large e' da 54 dp: oltre i 48 il 50% (27 dp) e' piu' del tetto (24), e il tasto arriva a 78 dp.
    val large = FluidGlassSize.Large.lens.value
    val growth = fluidPressGrowth(FluidPressFeedback.ExpandRound, large, large, dp = 1f)
    assertEquals(RoundLongSideCapDp / large, growth, 0.0001f)
    assertEquals(large + RoundLongSideCapDp, large * (1f + growth), 0.001f)
    // E nessuna taglia fa invadere piu' di 12 dp per lato.
    FluidGlassSize.entries.forEach { size ->
      val g = fluidPressGrowth(FluidPressFeedback.ExpandRound, size.lens.value, size.lens.value, dp = 1f)
      assertTrue("$size", size.lens.value * g / 2f <= RoundLongSideCapDp / 2f + 0.0001f)
    }
  }

  @Test
  fun theCapSitsAtTheMinimumTouchTarget() {
    // Il numero non e' libero: e' il piu' piccolo che lascia intere le taglie che c'erano (50% di 48).
    assertEquals(FluidGlassIconTouchTargetSize.value * RoundMaxScale, RoundLongSideCapDp, 0.0001f)
  }

  @Test
  fun buttonSizeLargeIs50DpForASheet() {
    // MyCloud: "l'engine Medium e' 42 dp". Per un foglio c'e' Large, e questo lo tiene fermo.
    assertEquals(50.dp, FluidButtonDefaults.height(FluidButtonSize.Large))
    assertEquals(42.dp, FluidButtonDefaults.height(FluidButtonSize.Medium))
    assertEquals(32.dp, FluidButtonDefaults.height(FluidButtonSize.Small))
    assertTrue(
      FluidButtonDefaults.horizontalPadding(FluidButtonSize.Large) >
        FluidButtonDefaults.horizontalPadding(FluidButtonSize.Medium),
    )
  }
}
