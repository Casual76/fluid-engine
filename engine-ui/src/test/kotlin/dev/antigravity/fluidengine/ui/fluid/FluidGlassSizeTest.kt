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
  fun theRoundButtonGrowsByTheSameFractionOnEverySize() {
    // La crescita di un tasto tondo e' una frazione (LibrePods: x1,5), non una misura in dp: un
    // tasto piu' grande cresce di piu' in assoluto e della stessa quantita' in proporzione. Il tetto
    // sul lato lungo e' infinito per `ExpandRound`, quindi nessuna taglia lo accorcia.
    FluidGlassSize.entries.forEach { size ->
      val growth = fluidPressGrowth(FluidPressFeedback.ExpandRound, size.lens.value, size.lens.value, dp = 1f)
      assertEquals("$size", RoundMaxScale, growth, 0.0001f)
      val peak = size.lens.value * (1f + growth)
      assertEquals("$size", size.lens.value * 1.5f, peak, 0.001f)
    }
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
