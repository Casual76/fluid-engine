package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeCaptureScale
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole pure di [FluidScrollEdge] (F2.4): quando può disegnare, quale profilo dà ogni stile, quanto sporge.
 * L'innesto in `FluidScreen` (F2.5) sceglie la fascia di oggi dove [fluidScrollEdgeAvailable] è falso: questo test
 * tiene ferma la risposta che quella scelta legge.
 */
class FluidScrollEdgeTest {

  @Test
  fun itDrawsOnlyWithAgslTheLiquidModelAndHealthyShaders() {
    assertTrue(glassScrollEdgeAvailable(33, FluidGlassModel.Liquid) { true })
    assertTrue(glassScrollEdgeAvailable(36, FluidGlassModel.Liquid) { true })
    // Sotto Android 13 e sotto `Classic` resta la fascia di oggi (piano 2, 3.10, 3.11).
    assertFalse(glassScrollEdgeAvailable(32, FluidGlassModel.Liquid) { true })
    assertFalse(glassScrollEdgeAvailable(26, FluidGlassModel.Liquid) { true })
    assertFalse(glassScrollEdgeAvailable(36, FluidGlassModel.Classic) { true })
    // Un compilatore che rifiuta il programma della striscia o della lastra: niente striscia.
    assertFalse(glassScrollEdgeAvailable(36, FluidGlassModel.Liquid) { false })
  }

  @Test
  fun theCompilerIsNotAskedWhenTheAnswerIsAlreadyNo() {
    // La sonda compila la prima volta che le si chiede un verdetto: sotto Android 13 o sotto `Classic` non si chiede.
    var asked = 0
    glassScrollEdgeAvailable(32, FluidGlassModel.Liquid) { asked++; true }
    glassScrollEdgeAvailable(36, FluidGlassModel.Classic) { asked++; true }
    assertEquals(0, asked)
    glassScrollEdgeAvailable(36, FluidGlassModel.Liquid) { asked++; true }
    assertEquals(1, asked)
  }

  @Test
  fun eachStyleHasItsProfile() {
    val top = FluidScrollEdgeSide.Top
    val bottom = FluidScrollEdgeSide.Bottom
    // `Automatic` è il morbido di iOS 26 in alto (decisione di Alessio), in basso il velo senza sfocatura.
    assertSame(GlassScrollEdgeProfile.Soft, fluidScrollEdgeProfile(top, FluidScrollEdgeStyle.Automatic))
    assertSame(FluidScrollEdgeBottomAutomatic, fluidScrollEdgeProfile(bottom, FluidScrollEdgeStyle.Automatic))
    assertSame(GlassScrollEdgeProfile.FadeOnly, FluidScrollEdgeBottomAutomatic)
    for (edge in FluidScrollEdgeSide.entries) {
      assertSame(GlassScrollEdgeProfile.Soft, fluidScrollEdgeProfile(edge, FluidScrollEdgeStyle.Soft))
      assertSame(GlassScrollEdgeProfile.Hard, fluidScrollEdgeProfile(edge, FluidScrollEdgeStyle.Hard))
      assertNull(fluidScrollEdgeProfile(edge, FluidScrollEdgeStyle.Hidden))
    }
  }

  @Test
  fun theStripReachesBeyondTheControlsAsFarAsItsProfile() {
    // Il bordo morbido sotto tasti da 44 dp: 1,4 H = 61,6 dp oltre il bordo dei tasti (il velo dolce finisce a 1,35 H,
    // la dissolvenza a 1,4), a
    // qualunque risoluzione della cattura (la metà sposta la dissolvenza, non la portata).
    assertEquals(
      GlassScrollEdgeProfile.Soft.reach,
      GlassScrollEdgeProfile.Soft.atResolution(GlassScrollEdgeCaptureScale).reach,
      0f,
    )
    assertEquals(64.4f, FluidScrollEdgeDefaults.reach(FluidScrollEdgeSide.Top).value, 1e-3f)
    assertEquals(46.dp, FluidScrollEdgeDefaults.ControlHeight)
    // Una tab bar da 62 dp si porta dietro un bordo proporzionato.
    assertEquals(86.8f, FluidScrollEdgeDefaults.reach(FluidScrollEdgeSide.Bottom, controlHeight = 62.dp).value, 1e-3f)
    assertEquals(6.9f, FluidScrollEdgeDefaults.reach(FluidScrollEdgeSide.Top, FluidScrollEdgeStyle.Hard).value, 1e-3f)
    assertEquals(0f, FluidScrollEdgeDefaults.reach(FluidScrollEdgeSide.Top, FluidScrollEdgeStyle.Hidden).value, 0f)
  }

  @Test
  fun whereItCannotDrawItKeepsTheSamePlace() {
    // D-R10: il posto che il componente pubblico tiene vuoto dove la striscia non si disegna è l'altezza del `Box`
    // della striscia (`FluidScrollEdgeStrip`: `height + controlHeight * profile.reach`, col profilo alla risoluzione
    // della cattura), al bit, per ogni lato, stile e altezza dei tasti; ed è `height + reach` della scheda.
    val heights = listOf(0.dp, 68.dp, 112.5.dp)
    val controls = listOf(FluidScrollEdgeDefaults.ControlHeight, 52.dp, 62.dp)
    for (edge in FluidScrollEdgeSide.entries) {
      for (style in FluidScrollEdgeStyle.entries) {
        for (height in heights) {
          for (control in controls) {
            val extent = fluidScrollEdgeExtent(edge, style, height, control)
            if (style == FluidScrollEdgeStyle.Hidden) {
              // `Hidden` non ha striscia: non occupa niente, né dove si disegna né altrove.
              assertNull(extent)
            } else {
              val profile = fluidScrollEdgeProfile(edge, style)!!.atResolution(GlassScrollEdgeCaptureScale)
              assertEquals(height + control * profile.reach, extent)
              assertEquals(height + FluidScrollEdgeDefaults.reach(edge, style, control), extent)
            }
          }
        }
      }
    }
    // In alto, morbido, sotto barra di stato e tasti da 44 dp: 68 + 61,6 dp.
    assertEquals(
      129.6f,
      fluidScrollEdgeExtent(FluidScrollEdgeSide.Top, FluidScrollEdgeStyle.Automatic, 68.dp, 44.dp)!!.value,
      1e-3f,
    )
  }
}
