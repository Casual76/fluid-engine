package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il pannello flottante e' un solo materiale: `GlassDefaults.panelOptics`.
 *
 * Prima ogni pannello aveva i suoi numeri (la finestra che si espande 2,2 con un bordo da 1,1 dp, la
 * notifica e il callout il ruolo `Modal` intero, il menu che si trasforma lo stesso con la lente di
 * un modale), e cinque vetri diversi per la stessa famiglia di cose si leggevano uno accanto
 * all'altro. Qui si inchiodano i numeri di partenza — quelli che il popover aveva gia' — e cio' che
 * cambia con i due interruttori, e niente piu'.
 */
class GlassPanelMaterialTest {

  private val base = GlassDefaults.panelOptics()

  @Test
  fun theBase_isWhatThePopoverAlreadyHad() {
    assertEquals(2.5f, base.blurScale, 0f)
    assertEquals(14f, base.refractionHeight.value, 0f)
    assertEquals(18f, base.refractionAmount.value, 0f)
    assertFalse(base.depthEffect)
    assertFalse(base.dispersion)
    assertEquals(1.6f, base.vibrancy, 0f)
    assertEquals(0.8f, base.highlightWidth.value, 0f)
    assertEquals(0.45f, base.highlightAlpha, 0f)
    assertEquals(90f, base.highlightAngle, 0f)
    // Nessuna ombra interna: con un bordo speculare si leggerebbero due bordi.
    assertEquals(0f, base.innerShadowAlpha, 0f)
    assertEquals(0f, base.innerShadowRadius.value, 0f)
    assertEquals(18f, base.shadowRadius.value, 0f)
    assertEquals(0.36f, base.shadowAlpha, 0f)
    assertEquals(0f, base.pressedDepthBoost, 0f)
  }

  @Test
  fun compact_changesOnlyTheLens() {
    val compact = GlassDefaults.panelOptics(compact = true)
    assertEquals(7f, compact.refractionHeight.value, 0f)
    assertEquals(9f, compact.refractionAmount.value, 0f)
    assertEquals(base.copy(refractionHeight = 7.dp, refractionAmount = 9.dp), compact)
  }

  @Test
  fun overSharpPage_raisesOnlyTheBlur_toWhatTheModalRoleHadForThem() {
    val sharp = GlassDefaults.panelOptics(overSharpPage = true)
    assertEquals(3.5f, sharp.blurScale, 0f)
    assertEquals(base.copy(blurScale = 3.5f), sharp)
    // 3,5 e' il blurScale che il ruolo Modal aveva per la notifica e il callout.
    assertEquals(GlassDefaults.optics(GlassRole.Modal).blurScale, sharp.blurScale, 0f)
  }

  @Test
  fun compactOverSharpPage_isFrostedMoreButNotPastWhatTheCompactLensCovers() {
    val both = GlassDefaults.panelOptics(compact = true, overSharpPage = true)
    val compact = GlassDefaults.panelOptics(compact = true)
    assertTrue(both.blurScale > compact.blurScale)
    // Il padding della cattura vale max(0, sfocatura - altezza): resta zero solo se la lente copre.
    val blurDp = DefaultGlassBlurRadius.value * both.blurScale
    assertTrue("sfocatura $blurDp dp contro lente ${both.refractionHeight.value}", blurDp <= both.refractionHeight.value + 1e-4f)
    assertEquals(compact.refractionHeight, both.refractionHeight)
    assertEquals(compact.refractionAmount, both.refractionAmount)
  }

  @Test
  fun everyCombination_isOneStableInstance() {
    for (compact in listOf(false, true)) for (sharp in listOf(false, true)) {
      assertSame(
        GlassDefaults.panelOptics(compact, sharp),
        GlassDefaults.panelOptics(compact, sharp),
      )
    }
  }

  @Test
  fun everyVariant_survivesSanitizingUntouched() {
    for (compact in listOf(false, true)) for (sharp in listOf(false, true)) {
      val optics = GlassDefaults.panelOptics(compact, sharp)
      assertEquals(optics, optics.sanitized())
    }
  }

  @Test
  fun thePanel_isFrostedMoreThanAControl_andLessThanTheModalItWasBuiltFrom() {
    val modal = GlassDefaults.optics(GlassRole.Modal)
    val interactive = GlassDefaults.optics(GlassRole.Interactive)
    assertTrue(base.blurScale > interactive.blurScale)
    assertTrue(base.blurScale < modal.blurScale)
    // E il suo bordo e' piu' tenue del modale: a 0,7 il pop-up sembrava un adesivo.
    assertTrue(base.highlightAlpha < modal.highlightAlpha)
  }
}
