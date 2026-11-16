package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneFusion
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneFusionDefault
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneFusionDp
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidClearDim
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Come si traduce un `FluidGlass` nel materiale (4.0, piano «iOS in Android» F3.2), e i token del piano consolidati in
 * [FluidMotion].
 */
class FluidGlassContainerLookTest {

  private val base = GlassTint(
    overlay = Color(0x61FFFFFF),
    fallback = Color(0xE6F2F2F7),
    hairline = Color.Transparent,
    mode = GlassTintMode.Neutral,
  )

  @Test
  fun `il vetro di serie ha la pellicola di serie dei controlli`() {
    assertSame(base, fluidGlassTint(FluidGlass.Regular, base))
    assertSame(base, fluidGlassTint(FluidGlass.Regular.interactive(), base))
  }

  @Test
  fun `il tinto e' la tinta di Apple, la tonalita' resta e la luminosita' segue lo sfondo`() {
    val accent = Color(0xFF0A84FF)
    val tint = fluidGlassTint(FluidGlass.Regular.tint(accent), base)
    assertEquals(GlassTintMode.Colored, tint.mode)
    assertEquals(accent, tint.overlay)
    assertEquals(GlassTintMode.Colored, glassTintLiquidMode(tint))
  }

  @Test
  fun `il limpido e' la variante della ricetta, non piu' un ponte`() {
    // F3.2c: pellicola e ottica sono quelle di serie; lo scurimento e la smerigliatura a nitido li fa la ricetta.
    assertSame(base, fluidGlassTint(FluidGlass.Clear, base))
    assertEquals(fluidGlassOptics(FluidGlass.Regular), fluidGlassOptics(FluidGlass.Clear))
    assertNotEquals(0f, fluidGlassOptics(FluidGlass.Clear).blurScale)
    assertEquals(GlassLiquidVariant.Clear, FluidGlass.Clear.liquidVariant)
    val optics = fluidGlassOptics(FluidGlass.Clear).sanitized()
    val clear = glassRecipe(GlassRole.Floating, 44f, 44f, darkSide = false, variant = GlassLiquidVariant.Clear, optics = optics)
    val regular = glassRecipe(GlassRole.Floating, 44f, 44f, darkSide = false, optics = optics)
    assertEquals("a nitido", 1f, clear.mix, 0f)
    assertEquals("il raggio resta: la cattura non si rifà fra le due varianti", regular.blurDp, clear.blurDp, 0f)
    assertEquals(regular.paddingDp, clear.paddingDp, 0f)
    assertTrue("scurisce", clear.dim > 0f && clear.dim <= GlassLiquidClearDim)
    assertEquals("niente velo", 0f, clear.veilColor.alpha, 0f)
    // Tinto e limpido: la tinta di Apple sopra il limpido.
    assertEquals(GlassTintMode.Colored, fluidGlassTint(FluidGlass.Clear.tint(Color(0xFF0A84FF)), base).mode)
  }

  @Test
  fun `la distanza di fusione e' una costante sola, la media scelta da Alessio`() {
    assertEquals(PhysicsPlaneFusion.Medium, PhysicsPlaneFusionDefault)
    assertEquals(PhysicsPlaneFusionDefault.dp, PhysicsPlaneFusionDp, 0f)
    assertEquals(PhysicsPlaneFusionDp.dp, FluidGlassContainerDefaults.Spacing)
    assertFalse("a riposo i vetri distinti sono separati", PhysicsPlaneFusionDefault.restFused)
    assertEquals(
      listOf(8f, 16f, 24f),
      FluidGlassContainerDiagnostics.FusionPreview.entries.map { it.fusion.dp },
    )
    assertTrue("solo la larga fonde a riposo", FluidGlassContainerDiagnostics.FusionPreview.Wide.fusion.restFused)
  }

  @Test
  fun `un vetro che reagisce al dito ha l'ottica dei tasti, uno fermo quella flottante`() {
    assertEquals(GlassDefaults.buttonOptics(), fluidGlassOptics(FluidGlass.Regular.interactive()))
    assertEquals(GlassDefaults.optics(GlassRole.Floating), fluidGlassOptics(FluidGlass.Regular))
  }

  @Test
  fun `i bilanci del piano sono i token di FluidMotion`() {
    val motion = GlassPlaneMotion()
    assertEquals(1.09f, FluidMotion.PlaneAppearScale, 0f)
    assertEquals(300.0, motion.contextMs, 0.0)
    assertEquals(FluidMotion.DurationExpand.toDouble(), motion.contextMs, 0.0)
    assertEquals(430.0, motion.appearMs, 0.0)
    assertEquals(470.0, motion.disappearMs, 0.0)
    assertEquals(1.09f, motion.initialContourScale, 0f)
    assertNull(motion.spec)
  }

  @Test
  fun `le tre animazioni del contenitore`() {
    assertEquals(FluidGlassAnimation.Custom(tween(100)), FluidGlassAnimation.Custom(tween(100)))
    assertNotEquals(FluidGlassAnimation.Default, FluidGlassAnimation.None)
  }
}
