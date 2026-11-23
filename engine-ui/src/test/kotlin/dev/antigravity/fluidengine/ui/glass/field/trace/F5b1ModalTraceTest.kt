package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidCompactAdaptation
import dev.antigravity.fluidengine.ui.fluid.FluidModalSourceMotion
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidConfirmationFrame
import dev.antigravity.fluidengine.ui.fluid.fluidMenuInflation
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLensBlurDp
import dev.antigravity.fluidengine.ui.fluid.fluidModalAdaptsPopover
import dev.antigravity.fluidengine.ui.fluid.fluidModalContentAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidMenuReturnInkSpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalContentMap
import dev.antigravity.fluidengine.ui.fluid.fluidModalFaceAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidModalFocusSpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalGeometrySpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalReturnFaceAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidModalInkExitSpec
import dev.antigravity.fluidengine.ui.fluid.fluidModalSourceSlab
import dev.antigravity.fluidengine.ui.fluid.fluidModalTravelClip
import dev.antigravity.fluidengine.ui.fluid.fluidSheetIsUndimmed
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedScrim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F5b1ModalTraceTest {
  @Test fun compactPopoverAdaptsOnlyWhenRequested() {
    assertTrue(fluidModalAdaptsPopover(true, FluidCompactAdaptation.Sheet))
    assertFalse(fluidModalAdaptsPopover(true, FluidCompactAdaptation.Popover))
    assertFalse(fluidModalAdaptsPopover(false, FluidCompactAdaptation.Sheet))
  }

  @Test fun inlineConfirmationIsAboveTheSourceAndCenteredWithoutOne() {
    val window = Size(400f, 600f)
    val size = Size(280f, 120f)
    val anchor = Rect(200f, 280f, 244f, 324f)
    val inline = fluidConfirmationFrame(anchor, size, window, 16f, 12f)
    assertEquals(anchor.center.x, inline.center.x, 0.01f)
    assertEquals(anchor.top - 12f, inline.bottom, 0.01f)
    assertEquals(Offset(200f, 300f), fluidConfirmationFrame(null, size, window, 16f, 12f).center)
  }

  @Test fun dimFollowsPositionDuringBothDirectionsOfDrag() {
    val heights = listOf(299f, 300f, 301f, 560f, 301f, 300f, 290f)
    assertEquals(listOf(true, true, false, false, false, true, true), heights.map { fluidSheetIsUndimmed(it, 300f) })
    assertFalse(fluidSheetIsUndimmed(300f, null))
  }

  @Test fun sourceAndArrivalTradeInkAndReturnToTheSameContour() {
    val start = FluidForm.Slab(Rect(280f, 40f, 324f, 84f), FluidCornerRadii.all(22f))
    val end = FluidForm.Slab(Rect(88f, 100f, 344f, 278f), FluidCornerRadii.all(24f))
    assertEquals(start, fluidModalSourceSlab(start, end, 0f))
    assertEquals(end, fluidModalSourceSlab(start, end, 1f))
    val gather = fluidModalSourceSlab(start, end, FluidMotion.MenuButtonGatherEnd)
    assertEquals(start.frame.center.x, gather.frame.center.x, 0.001f)
    assertEquals(start.frame.center.y, gather.frame.center.y, 0.001f)
    assertTrue(gather.frame.width < start.frame.width)
    for (motion in FluidModalSourceMotion.entries) for (i in 0..1000) {
      val p = i / 1000f
      assertEquals(0f, fluidModalFaceAlpha(motion, p) * fluidModalContentAlpha(motion, p, 1f, 1f), 0f)
    }
  }

  @Test fun dialogsHoldTheSeedAndTheirLensFocusesWithTheMovingContour() {
    val popover = FluidModalSourceMotion.Popover
    val focus = TodaySpring(fluidModalFocusSpec(popover, opening = true, from = 0f, fresh = true), 0f, 1f)
    for (time in 0..FluidMotion.ContextMenuFocusMillis) {
      val expected = FluidMotion.ContextMenuFocusEasing.transform(time.toFloat() / FluidMotion.ContextMenuFocusMillis)
      assertEquals("messa a fuoco di Altro a ${time} ms", expected, focus.value(time * 1_000_000L), 0.0001f)
    }
    val contour = TodaySpring(fluidModalGeometrySpec(popover, opening = true, from = 0f), 0f, 1f)
    assertTrue("il pannello è ancora in viaggio a 300 ms", contour.value(300_000_000) < 1f)
    assertTrue("il bordo vicino è ancora in viaggio a 400 ms", contour.value(400_000_000) < 0.99f)
    assertEquals(1f, contour.value(467_000_000), 0f)
    for (motion in listOf(FluidModalSourceMotion.Confirmation, FluidModalSourceMotion.Alert)) {
      val contour = TodaySpring(fluidModalGeometrySpec(motion, opening = true, from = 0f), 0f, 1f)
      val held = contour.value(67_000_000)
      assertEquals(FluidMotion.MenuButtonGatherEnd, held, 0.0001f)
      assertEquals(held, contour.value(133_000_000), 0.0001f)
      assertEquals(0f, fluidModalFaceAlpha(motion, held), 0.0001f)
      assertTrue(contour.value(200_000_000) > FluidMotion.MenuButtonFirstRise)
      assertTrue(contour.value(350_000_000) < contour.value(367_000_000))
      assertTrue(contour.value(367_000_000) < contour.value(400_000_000))
      val late = TodaySpring(fluidModalFocusSpec(motion, opening = true, from = 0f, fresh = true), 0f, 1f)
      assertTrue(fluidMenuLensBlurDp(late.value(267_000_000)) > 3f)
      assertTrue(fluidMenuLensBlurDp(late.value(350_000_000)) > 2.4f)
      assertTrue(fluidMenuLensBlurDp(late.value(400_000_000)) < 0.01f)
    }
  }

  @Test fun anEarlyReturnProgressCannotShowTheFaceWhileTheDropIsInFlight() {
    val anchor = Rect(42f, 376f, 86f, 420f)
    assertEquals(0f, fluidModalReturnFaceAlpha(0.01f, anchor.translate(Offset(12f, -20f)), anchor), 0f)
    assertEquals(0f, fluidModalReturnFaceAlpha(0.01f, anchor.inflate(5f), anchor), 0f)
    assertTrue(fluidModalReturnFaceAlpha(0.01f, anchor.inflate(1f), anchor) in 0.01f..0.99f)
    assertEquals(1f, fluidModalReturnFaceAlpha(0f, anchor, anchor), 0f)
  }

  /** Le tre scene sulla tela 360 x 520 dp, riusate da F5b4PresentationTraceTest. */
  internal val scenes: List<Scene> = run {
    val anchor = Rect(264f, 280f, 308f, 324f)
    listOf(
      Scene("popover", FluidModalSourceMotion.Popover,
        Rect(276f, 38f, 320f, 82f), Rect(112f, 94f, 344f, 253f), listOf("Ordina per", "Per data", "Per nome")),
      Scene("conferma", FluidModalSourceMotion.Confirmation,
        anchor, fluidConfirmationFrame(anchor, Size(240f, 94f), Size(360f, 520f), 16f, 12f),
        listOf("Eliminare la voce?", "Elimina")),
      Scene("avviso", FluidModalSourceMotion.Alert, Rect(42f, 376f, 86f, 420f), Rect(40f, 182f, 320f, 338f),
        listOf("Modifiche non salvate", "Continuare?"), modal = true),
    )
  }

  @Test fun exportOpeningAndReturnAtNativeTime() {
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F5B1_TRACE_DIR"))
    for (scene in scenes) for (closing in listOf(false, true)) {
      val trace = trace(scene, closing)
      assertTrue(trace.encode().isNotEmpty())
      if (output != null) trace.write(GlassTraceOutput.fileFor(output, trace))
      else GlassTraceOutput.writeIfRequested(trace)
    }
  }

  internal data class Scene(
    val name: String,
    val motion: FluidModalSourceMotion,
    val anchor: Rect,
    val target: Rect,
    val labels: List<String>,
    val modal: Boolean = false,
  )

  internal fun trace(scene: Scene, closing: Boolean): GlassTrace {
    val start = FluidForm.Slab(scene.anchor, FluidCornerRadii.all(22f))
    val end = FluidForm.Slab(scene.target, FluidCornerRadii.all(24f))
    val physics = FluidPhysicsState(start)
    val driver = TodayLiquidDriver(physics, 1f)
    val optics = GlassDefaults.panelOptics().sanitized()
    val dress = LiquidPhysicsDress(GlassRole.Modal, PhysicsMaterialSource(optics, optics), 1f, shadow = true)
    val from = if (closing) 1f else 0f
    val to = 1f - from
    val travel = TodaySpring(fluidModalGeometrySpec(scene.motion, !closing, from), from, to)
    val focusClock = TodaySpring(fluidModalFocusSpec(scene.motion, !closing, from, fresh = !closing), from, to)
    val visibility = TodaySpring(if (closing) fluidMenuReturnInkSpec(1f) else fluidModalInkExitSpec(1f), 1f, to)
    val inflation = fluidMenuInflation(scene.target.width, scene.target.height, 360f, 6f, 240f, 0.6f, 0.15f)
    var p = from
    physics.driveExternally(
      from = start, to = end, progress = { p }, startFrame = null,
      overshootInflationX = inflation.x, overshootInflationY = inflation.y,
      travelSlab = { if (p < 1f) fluidModalSourceSlab(start, end, p, returning = closing) else null },
    )
    val frames = (0..36).map { index ->
      val time = glassTraceTimeMs(index)
      val nanos = glassTraceTimeNanos(index)
      p = travel.value(nanos)
      driver.frame(nanos)
      val plan = physics.ensurePlan()
      val bounds = physics.contentBoundsNow()
      val focus = focusClock.value(nanos)
      val face = if (closing) fluidModalReturnFaceAlpha(p, bounds, scene.anchor) else fluidModalFaceAlpha(scene.motion, p)
      val alpha = fluidModalContentAlpha(scene.motion, p, focus, visibility.value(nanos))
      assertEquals("glifo e inchiostro a ${scene.name} / $time", 0f, face * alpha, 0f)
      var contentClip: Map<String, Any?>? = null
      val content = if (face > 0f) {
        val icon = when (scene.name) {
          "conferma" -> "fluid:delete|circle"
          "avviso" -> "fluid:info|circle"
          else -> "fluid:more_horiz|dots"
        }
        val faceBounds = if (closing) scene.anchor else bounds
        listOf(todayIconLayer("sorgente", faceBounds, icon, 22f, Color.Black, Offset.Zero, 1f,
          alpha = face, scaleX = minOf(faceBounds.width, faceBounds.height) / scene.anchor.width))
      } else if (alpha > 0f) {
        val map = fluidModalContentMap(physics, scene.target, p, focus)
        val center = scene.target.center + map.translation
        val items = scene.labels.mapIndexed { row, text ->
          mapOf(
            "kind" to "text", "text" to text, "color" to "#101015",
            "x" to round3(center.x - scene.target.width * map.scale / 2f + 16f * map.scale),
            "y" to round3(center.y + (36f + row * 34f - scene.target.height / 2f) * map.scale),
            "sizeDp" to round3(16f * map.scale),
          )
        }
        val layer = linkedMapOf<String, Any?>(
          "name" to "arrivo", "x" to 0, "y" to 0, "w" to 360, "h" to 520,
          "items" to items, "alpha" to alpha, "blurDp" to fluidMenuLensBlurDp(focus) * map.scale,
        )
        menuTraceContentLens(plan, Offset.Zero, 1f, focus)?.let { layer["effect"] = it }
        val clip = fluidModalTravelClip(physics, scene.target, p, focus)?.translate(scene.target.topLeft)
        if (clip != null) contentClip = mapOf(
          "x" to round3(clip.left), "y" to round3(clip.top), "w" to round3(clip.width), "h" to round3(clip.height),
          "radius" to listOf(clip.topLeftCornerRadius.x, clip.topRightCornerRadius.x,
            clip.bottomRightCornerRadius.x, clip.bottomLeftCornerRadius.x).map(::round3),
        )
        listOf(layer)
      } else emptyList()
      val elements = buildList {
        if (scene.modal) {
          val fade = if (closing) (1f - time.toFloat() / 160f) else time.toFloat() / 140f
          add(mapOf("kind" to "shape", "name" to "velo", "x" to 0, "y" to 0, "w" to 360, "h" to 520,
            "radius" to 0, "color" to glassColor(GlassPaintedScrim.color(false).copy(
              alpha = GlassPaintedScrim.LightAlpha * fade.coerceIn(0f, 1f),
            ))))
        }
        val panel = dress.element("pannello", physics, plan, 1f, Offset.Zero, 360f, 520f, content, timeMs = time)
        add(contentClip?.let { panel + ("contentClip" to listOf(it)) } ?: panel)
      }
      GlassTraceFrame(time, elements)
    }
    val name = scene.name + if (closing) "-torna" else "-apre"
    return GlassTrace(name, "f5b-1", 1f, "light", 360f, 520f, frames,
      head = mapOf("background" to mapOf("kind" to "color", "color" to "#E7EBF4")))
  }
}
