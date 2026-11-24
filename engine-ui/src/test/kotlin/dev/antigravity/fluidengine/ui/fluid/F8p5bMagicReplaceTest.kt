package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.vector.addPathNodes
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.MicOff
import dev.antigravity.fluidengine.ui.fluid.icons.Notifications
import dev.antigravity.fluidengine.ui.fluid.icons.NotificationsActive
import dev.antigravity.fluidengine.ui.fluid.icons.NotificationsOff
import dev.antigravity.fluidengine.ui.fluid.icons.Pause
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.5b · La Magic Replace, il piano: quali icone sono parenti, dove passa la penna, cosa compare da solo e come
 * rimbalza la base, contro i quattro casi di Apple (WWDC24 10188, la campana). Le riprese nell'albero Compose vero, coi
 * fermi per la tavola accanto ad Apple, sono nel `sample` (`F8p5bMagicFramesTest`).
 */
class F8p5bMagicReplaceTest {
  private fun g(icon: androidx.compose.ui.graphics.vector.ImageVector) = SymbolGeometryBuilder.of(icon)

  @Test
  fun `la campana che diventa barrata disegna la barra con la penna, dall'alto a sinistra, senza rimbalzo`() {
    val plan = SymbolMagicPlanner.build(g(FluidIcons.Notifications), g(FluidIcons.NotificationsOff))
    assertNotNull("campana e campana barrata sono parenti", plan)
    plan!!
    assertEquals(1, plan.bands.size)
    val band = plan.bands[0]
    assertTrue("la penna disegna", band.drawsOn)
    assertTrue("parte in alto a sinistra: ${band.startX}, ${band.startY}", band.startX < 7f && band.startY < 7f)
    assertTrue("va verso il basso a destra", band.ux > 0.5f && band.uy > 0.5f)
    assertTrue("e' lunga quasi tutto il riquadro: ${band.length}", band.length > 18f)
    assertTrue("la punta e' larga quanto il tratto: ${band.capRadius}", band.capRadius in 0.6f..2f)
    assertTrue(plan.badges.isEmpty())
    assertEquals("Apple non fa rimbalzare la campana quando la barra si disegna", 0, plan.bounce)
  }

  @Test
  fun `la barra che se ne va si ritira verso il suo inizio e la campana rimbalza`() {
    val plan = SymbolMagicPlanner.build(g(FluidIcons.NotificationsOff), g(FluidIcons.Notifications))!!
    assertEquals(1, plan.bands.size)
    assertTrue("si ritira", !plan.bands[0].drawsOn)
    assertTrue(plan.bands[0].startX < 7f && plan.bands[0].startY < 7f)
    assertEquals(1, plan.bounce)
  }

  @Test
  fun `la campana che suona disegna le due onde, e il microfono muto la sua barra`() {
    val active = SymbolMagicPlanner.build(g(FluidIcons.Notifications), g(FluidIcons.NotificationsActive))!!
    assertEquals(2, active.bands.size)
    assertTrue(active.bands.all { it.drawsOn })
    val mute = SymbolMagicPlanner.build(g(FluidIcons.Mic), g(FluidIcons.MicOff))!!
    assertEquals(1, mute.bands.size)
    assertTrue(mute.bands[0].drawsOn)
  }

  @Test
  fun `fra icone che non sono parenti resta la sostituzione sfocata`() {
    assertNull(SymbolMagicPlanner.build(g(FluidIcons.PlayArrow), g(FluidIcons.Pause)))
    assertNull(SymbolMagicPlanner.build(g(FluidIcons.Check), g(FluidIcons.Close)))
    assertNull("la stessa icona non cambia", SymbolMagicPlanner.build(g(FluidIcons.Mic), g(FluidIcons.Mic)))
  }

  @Test
  fun `il pallino compare da solo e la base rimbalza su, e quando se ne va rimbalza giu'`() {
    val square = addPathNodes("M4 6H16V18H4Z M6 8V16H14V8Z")
    val badge = addPathNodes("M19 2L22 5L19 8L16 5Z")
    val plain = SymbolGeometryBuilder.build(listOf(square), 24f, 24f)
    val badged = SymbolGeometryBuilder.build(listOf(square, badge), 24f, 24f)
    val on = SymbolMagicPlanner.build(plain, badged)!!
    assertTrue(on.bands.isEmpty())
    assertEquals(1, on.badges.size)
    assertTrue(on.badges[0].appears)
    assertEquals(19f, on.badges[0].centerX, 0.5f)
    assertEquals(5f, on.badges[0].centerY, 0.5f)
    assertEquals(1, on.bounce)
    val off = SymbolMagicPlanner.build(badged, plain)!!
    assertTrue(!off.badges[0].appears)
    assertEquals(-1, off.bounce)
  }

  @Test
  fun `il piano si fa una volta per coppia`() {
    val a = g(FluidIcons.Notifications)
    val b = g(FluidIcons.NotificationsOff)
    assertSame(SymbolMagicPlanner.plan(a, b), SymbolMagicPlanner.plan(a, b))
    assertNull(SymbolMagicPlanner.plan(g(FluidIcons.PlayArrow), g(FluidIcons.Pause)))
  }

  @Test
  fun `i tempi sono quelli di Apple`() {
    assertEquals(267f, FluidSymbolMagic.PenMs, 1f)
    assertEquals(300f, FluidSymbolMagic.RetractMs, 1f)
    assertEquals(100f, FluidSymbolMagic.BadgeDelayMs, 1f)
    assertEquals(167f, FluidSymbolMagic.BadgeOutMs, 1f)
    assertEquals(700f, FluidSymbolMagic.Ms, 1f)
    // Il pallino: +21 % al massimo, a 100 + 167 ms dal taglio; fermo prima della fine.
    val peak = 1f + (FluidSymbolProfile.BounceScale.at(200f) - 1f) * FluidSymbolMagic.BadgeAmplitude
    assertEquals(1.217f, peak, 0.01f)
    assertTrue(FluidSymbolMagic.BadgeDelayMs + FluidSymbolProfile.BounceMs / FluidSymbolMagic.BadgeSpeed <= FluidSymbolMagic.Ms)
  }
}

/** Le onde della campana che suona scendono tutte e due dall'alto, come una coppia. */
class F8p5bMagicWavesTest {
  @Test
  fun `le due onde partono dall'alto`() {
    val plan = SymbolMagicPlanner.build(SymbolGeometryBuilder.of(FluidIcons.Notifications),
      SymbolGeometryBuilder.of(FluidIcons.NotificationsActive))!!
    assertTrue(plan.bands.all { it.uy > 0f })
  }
}
