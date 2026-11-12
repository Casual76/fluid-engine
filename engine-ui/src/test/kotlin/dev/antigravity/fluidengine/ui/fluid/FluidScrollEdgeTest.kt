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
    // Il bordo morbido sotto tasti da 44 dp: 1,1 H = 48,4 dp oltre il bordo dei tasti (R4 B7: velo nullo a 1,1 H), a
    // qualunque risoluzione della cattura (la metà sposta la dissolvenza, non la portata).
    assertEquals(
      GlassScrollEdgeProfile.Soft.reach,
      GlassScrollEdgeProfile.Soft.atResolution(GlassScrollEdgeCaptureScale).reach,
      0f,
    )
    assertEquals(48.4f, FluidScrollEdgeDefaults.reach(FluidScrollEdgeSide.Top).value, 1e-3f)
    assertEquals(44.dp, FluidScrollEdgeDefaults.ControlHeight)
    // Una tab bar da 62 dp si porta dietro un bordo proporzionato.
    assertEquals(68.2f, FluidScrollEdgeDefaults.reach(FluidScrollEdgeSide.Bottom, controlHeight = 62.dp).value, 1e-3f)
    assertEquals(6.6f, FluidScrollEdgeDefaults.reach(FluidScrollEdgeSide.Top, FluidScrollEdgeStyle.Hard).value, 1e-3f)
    assertEquals(0f, FluidScrollEdgeDefaults.reach(FluidScrollEdgeSide.Top, FluidScrollEdgeStyle.Hidden).value, 0f)
  }
}
