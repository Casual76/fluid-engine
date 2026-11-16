package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidClearDim
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import org.junit.Assert.assertEquals
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
  fun `il limpido scurisce del 35 per cento fisso, non gira e non e' smerigliato`() {
    val tint = fluidGlassTint(FluidGlass.Clear, base)
    assertEquals(GlassTintMode.Fixed, glassTintLiquidMode(tint))
    assertEquals(0f, tint.overlay.red, 0f)
    assertEquals(GlassLiquidClearDim, tint.overlay.alpha, 0.002f)
    assertEquals(0f, fluidGlassOptics(FluidGlass.Clear).blurScale, 0f)
    assertNotEquals(0f, fluidGlassOptics(FluidGlass.Regular).blurScale)
    assertEquals(GlassLiquidVariant.Clear, FluidGlass.Clear.liquidVariant)
  }

  @Test
  fun `un vetro che reagisce al dito ha l'ottica dei tasti, uno fermo quella flottante`() {
    assertEquals(GlassDefaults.buttonOptics(), fluidGlassOptics(FluidGlass.Regular.interactive()))
    assertEquals(GlassDefaults.optics(GlassRole.Floating), fluidGlassOptics(FluidGlass.Regular))
  }

  @Test
  fun `i bilanci del piano sono i token di FluidMotion`() {
    val motion = GlassPlaneMotion()
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
