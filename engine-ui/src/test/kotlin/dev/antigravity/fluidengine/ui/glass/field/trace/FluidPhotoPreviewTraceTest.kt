package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.fluidCustomPreviewFrame
import dev.antigravity.fluidengine.ui.fluid.fluidLiftProgress
import dev.antigravity.fluidengine.ui.fluid.fluidLiftStartFraction
import dev.antigravity.fluidengine.ui.fluid.fluidMenuReturnSpec
import dev.antigravity.fluidengine.ui.fluid.fluidPreviewFit
import dev.antigravity.fluidengine.ui.fluid.fluidPreviewMargins
import dev.antigravity.fluidengine.ui.fluid.fluidPreviewMix
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidPhotoPreviewTraceTest {
  /** S4d a 340 dpi. Riproduce il drive precedente: progresso, passo della goccia, contentBounds, poi disegno. */
  @Test fun theCardFollowsItsMeasuredDestinationFromTheFirstFrame() {
    val density = 2.125f
    val source = Rect(42f, 1391f, 246f, 1595f)
    val viewport = Rect(0f, 0f, 1600f, 2560f)
    val margins = fluidPreviewMargins(viewport, 16 * density)
    val target = fluidPreviewFit(source.center, Size(595f, 383f), margins)
    val radii = FluidCornerRadii.all(22 * density)
    val legacy = FluidPhysicsState(FluidForm.Slab(source, radii)).apply {
      this.density = density
      liquidAllowed = true
      liquidBody = true
    }
    val driver = TodayLiquidDriver(legacy, density)
    val opening = TodaySpring(FluidMotion.dropOpen(), 0f, 1f)
    val closing = TodaySpring(fluidMenuReturnSpec(), 1f, 0f)
    var lift = 0f
    legacy.driveExternally(FluidForm.Slab(source, radii), FluidForm.Slab(target, radii), { lift },
      FluidMotion.LiftedSwellX, FluidMotion.LiftedSwellY)
    var legacyLeft = source.left
    val data = mutableListOf("direction,k,ms,p,oldLift,oldL,oldT,oldR,oldB,newL,newT,newR,newB,mix")
    for (k in 0..204) {
      val returning = k >= 120
      val step = if (returning) k - 120 else k
      val ns = step * 1_000_000_000L / 120
      val p = if (returning) closing.value(ns) else opening.value(ns)
      val press = if (returning) 1f else 1.02f
      lift = fluidLiftProgress(p.coerceAtLeast(0f), fluidLiftStartFraction(source, target, press))
      driver.frame(k * 1_000_000_000L / 120)
      val old = legacy.contentBoundsNow()
      if (!returning) legacyLeft = minOf(legacyLeft, old.left)
      val frame = fluidCustomPreviewFrame(source, press, target, viewport, margins, p)
      assertTrue("fotogramma $k: $frame", frame.left >= margins.left - 0.001f && frame.right <= margins.right)
      val dir = if (returning) "ritorno" else "andata"
      data += listOf(dir, step, ns / 1e6, p, lift, old.left, old.top, old.right, old.bottom,
        frame.left, frame.top, frame.right, frame.bottom, fluidPreviewMix(p)).joinToString(",")
    }
    assertTrue("controllo positivo S4d: minimo $legacyLeft", legacyLeft < 0f)
    System.getenv("FLUID_PHOTO_TRACE")?.let { File(it).writeText(data.joinToString("\n", postfix = "\n")) }
  }
}
