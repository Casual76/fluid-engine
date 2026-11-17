package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentFlight
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPieceContent
import dev.antigravity.fluidengine.ui.fluid.FluidGlassStageShape
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.GlassContentLook
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPendingResize
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPressedSlab
import dev.antigravity.fluidengine.ui.fluid.fluidPressTransform
import dev.antigravity.fluidengine.ui.fluid.glassLabelLook
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneShape
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneFusionDp
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Riquadri RIPOSO/FINE di S5bis in pixel a densità 2,125. I voli e gli uniform sono quelli del palco. */
internal class F3Rif3TraceTest {
  private val density = 2.125f
  private fun piece(key: String, left: Int, top: Int, width: Int, height: Int): GlassPlanePiece {
    val rect = Rect(40f + left, 48f + top, 40f + left + width, 48f + top + height)
    return GlassPlanePiece(key, rect, FluidCornerRadii.all(rect.height / 2f), id = key)
  }
  private fun state(vararg pieces: GlassPlanePiece) = GlassPlaneState(pieces.toList(),
    PhysicsPlaneFusionDp * density, Rect(40f, 48f, pieces.maxOf { it.bounds.right }, 150f),
    room = Rect(0f, 0f, 1600f, 2560f))

  private val back = piece("indietro", 4, 4, 94, 94)
  private val short = state(back, piece("salva", 111, 0, 180, 102))
  private val long = state(back, piece("salva", 111, 0, 504, 102))
  private val one = state(piece("seleziona", 0, 0, 251, 102))
  private val three = state(piece("seleziona", 222, 0, 251, 102), piece("condividi", 4, 4, 94, 94),
    piece("elimina", 115, 4, 94, 94))
  private fun textScene(sameText: Boolean = false) = F38aTraces.Scene("testo", "Salva cambia misura nei due versi, con inversione in corsa",
    332f, 96f, false, short, listOf(F38aTraces.Step(0.0, long), F38aTraces.Step(1000.0, short),
      F38aTraces.Step(1150.0, long), F38aTraces.Step(2100.0, short)),
    mapOf("indietro" to F38aTraces.Label(icon = "fluid:arrow_back_ios_new")), lastFrame = 360, density = density, phase = "f3-testo",
    labelAt = { key, bounds ->
      if (key != "salva") null
      // Gli anticipi misurati da Skia a 17 dp, arrotondati in eccesso. Il line box della label è alto 24 dp.
      else if (sameText || bounds.width == 180f) F38aTraces.Label(text = "Salva", lineBoxDp = Size(41f, 24f))
      else F38aTraces.Label(text = "Salva e chiudi la raccolta", lineBoxDp = Size(190f, 24f))
    }, layoutLeadFrames = 2)

  private fun siblings() = F38aTraces.Scene("due-nati", "Due tondi rilasciati dallo stesso donatore",
    280f, 96f, false, one, listOf(F38aTraces.Step(0.0, three)), mapOf(
      "seleziona" to F38aTraces.Label(text = "Seleziona"), "condividi" to F38aTraces.Label(icon = "fluid:share"),
      "elimina" to F38aTraces.Label(icon = "fluid:delete")), lastFrame = 120, density = density, phase = "f3-rif3")

  @Test fun export() {
    GlassTraceOutput.requestedDir(System.getenv("GLASS_RIF3_DIR"))?.let {
      F38aTraces.build(siblings()).write(File(it, "due-nati/trace.json"))
    }
    GlassTraceOutput.requestedDir(System.getenv("GLASS_F3_TESTO_DIR"))?.let {
      F38aTraces.build(textScene()).write(File(it, "testo/trace.json"))
    }
  }

  private fun GlassTraceFrame.labels() = elements.flatMap { (it["content"] as? List<*>).orEmpty() }
    .filterIsInstance<Map<*, *>>().filter { (it["name"] as? String)?.startsWith("c-salva-") == true }

  @Test fun textIsReplacedInsideTheCapsuleInsteadOfBeingShrunk() {
    val frames = F38aTraces.build(textScene()).frames
    fun labelsAt(time: Double) = frames.minBy { abs(it.tMs - time) }.labels()
    for (time in listOf(-16.667, -8.333, 0.0)) {
      assertEquals("il layout anticipa il palco a $time ms", "Salva", labelsAt(time).single()["label"])
    }
    val entering = frames.first { frame -> frame.labels().any { it["label"] == "Salva e chiudi la raccolta" } }
    assertTrue(entering.tMs in 175.0..275.0)
    assertTrue(entering.labels().any { it["blurDp"] != null && it["alpha"] != null })
    assertEquals("Salva e chiudi la raccolta", labelsAt(650.0).single()["label"])
    assertEquals("Salva e chiudi la raccolta", labelsAt(1000.0).single()["label"])
    assertEquals("Salva e chiudi la raccolta", labelsAt(1750.0).single()["label"])
    assertEquals("Salva", labelsAt(2800.0).single()["label"])
    assertTrue(frames.any { it.labels().size == 2 })
    for (frame in frames) {
      val names = frame.labels().map { it["name"] }
      assertEquals(names.size, names.distinct().size)
    }
    for (frame in frames) for (ink in frame.labels()) {
      val clip = ink["clip"] as Map<*, *>
      val center = (ink["x"] as Number).toDouble() + (ink["w"] as Number).toDouble() / 2
      val glassCenter = (clip["x"] as Number).toDouble() + (clip["w"] as Number).toDouble() / 2
      assertEquals("testo decentrato a ${frame.tMs}", glassCenter, center, 0.003)
      assertTrue("scala a ${frame.tMs} ms: $ink", ((ink["scale"] as? Number)?.toDouble() ?: 1.0) in 0.9..1.0)
    }
  }

  @Test fun sameTextStaysSharpAndFullSizeThroughWidthChangesAndReversal() {
    val frames = F38aTraces.build(textScene(sameText = true)).frames
    val widths = HashSet<Any?>()
    for (frame in frames) {
      val ink = frame.labels().single()
      assertEquals("Salva", ink["label"])
      assertEquals(null, ink["blurDp"])
      assertEquals(null, ink["scale"])
      assertEquals(null, ink["alpha"])
      widths += (ink["clip"] as Map<*, *>)["w"]
    }
    assertTrue(widths.size > 100)
  }

  @Test fun theRoundedEdgeAndBlurFootprintGateOpacityWithoutChangingScale() {
    val glass = Rect(0f, 0f, 200f, 48f)
    val corners = FluidCornerRadii.all(24f)
    val near = Rect(8f, 16f, 192f, 32f)
    val look = GlassContentLook(alpha = 0.8f, scale = 0.93f, blur = 0.15f)
    assertEquals(0f, glassLabelLook(look, near.translate(9f, 0f), glass, corners, 26f).alpha, 0f)
    val ramp = glassLabelLook(look, near.translate(2f, 0f), glass, corners, 26f)
    assertTrue(ramp.alpha > 0f && ramp.alpha < look.alpha)
    assertEquals(look.scale, ramp.scale, 0f)
    assertEquals(look, glassLabelLook(look, Rect(20f, 16f, 180f, 32f), glass, corners, 26f))
    assertEquals(0.9f, glassLabelLook(look.copy(scale = 0.82f), near, glass, corners, 26f).scale, 0f)
  }

  @Test fun theClipAndAnchorSurvivePendingDrawsAndAnInterruptedFlight() {
    val a = short.pieces.last()
    val b = long.pieces.last().bounds.translate(19f, -4f)
    val held = fluidGlassPendingResize(a.bounds, b, a.radii, null)!!
    repeat(3) {
      val again = fluidGlassPendingResize(a.bounds, b, a.radii, null)!!
      assertEquals(a.bounds.center, b.center + again.offset)
      assertEquals(GlassContentLook.Sharp, again.look)
      assertEquals(a.bounds, again.clip!!.translate(b.topLeft))
    }
    // Il layout torna corto mentre l'ultimo volo pubblicato è ancora espresso nel nodo lungo.
    val inverse = fluidGlassPendingResize(b, a.bounds, a.radii, held)!!
    assertEquals(a.bounds, inverse.clip!!.translate(a.bounds.topLeft))
    assertEquals(a.bounds.center, inverse.offset + a.bounds.center)
    assertEquals(1f, inverse.look.scale, 0.0001f)
  }

  @Test fun thePressureLayerStageAndClipReadTheSameSampleEvenAfterTheFrameCallback() {
    val pressure = mutableFloatStateOf(1f)
    val rect = Rect(0f, 0f, 44f, 44f)
    val slab = FluidForm.Slab(rect, FluidCornerRadii.all(22f))
    fun transform() = fluidPressTransform(FluidPressFeedback.ExpandRound, pressure.floatValue, Offset.Zero,
      rect.width, rect.height, 1f, false)
    val stage = FluidGlassStageShape { PhysicsPlaneShape(listOf(fluidGlassPressedSlab(slab, transform())), 0f) }
    val ink = FluidGlassPieceContent()
    ink.show(FluidGlassContentFlight(GlassContentLook.Sharp, clip = rect, radii = slab.cornerRadii), ::transform)
    val touch = F38aTraces.Touch("modifica", 1f, press = true, releaseMs = 0.0)
    for (ms in listOf(0.0, 8.33, 32.0, 58.77, 82.878, 92.278, 116.71, 167.0, 250.0)) {
      pressure.floatValue = touch.progress(ms)
      val diameter = 44f * transform().scaleX
      assertEquals("palco a $ms ms", diameter, stage.value.slabs.single().frame.width, 0.0001f)
      assertEquals("ritaglio a $ms ms", diameter, ink.drawnFlight()!!.clip!!.width, 0.0001f)
      assertTrue(stage.value === stage.value)
    }
  }

  @Test fun siblingBridgeBreaksOnlyOnceInTheRenderedField() {
    assertSingleBreak(siblings())
  }

  @Test fun twoReleasesBesideARelayAndThreeReleasesFromAWideDonor() {
    // Geometria di GlassPlaneTest.aGlassThatLeavesIntoTheGlassBornFromItIsOneGlassChangingIdentity.
    val relay = state(piece("select", 150, 40, 96, 44))
    val done = state(piece("circle", 97, 40, 44, 44), piece("triangle", 154, 40, 44, 44), piece("done", 211, 40, 88, 44))
    fun test(initial: GlassPlaneState, target: GlassPlaneState, pairs: Int) {
      val scene = F38aTraces.Scene("rilasci", "Raccordi tra rilasci", 490f, 130f, false,
        initial, listOf(F38aTraces.Step(0.0, target)), emptyMap(), 132, density = 1f)
      for (i in 0 until pairs) assertSingleBreak(scene, i)
    }
    test(relay, done, 1)
    val donor = piece("donatore", 0, 0, 295, 94)
    val releases = (0..2).map { piece("nato-$it", it * 110, 0, 94, 94) }
    test(state(donor), state(piece("donatore", 330, 0, 295, 94), *releases.toTypedArray()), 2)
  }

  private fun assertSingleBreak(scene: F38aTraces.Scene, pair: Int = 0) {
    var connected = true
    var breaks = 0
    var checked = 0
    for (frame in F38aTraces.build(scene).frames) {
      val stage = frame.elements.firstOrNull { it["name"] == "palco-0" } ?: continue
      val uniforms = (stage["body"] as Map<*, *>)["uniforms"] as Map<*, *>
      val count = (uniforms["pieceCount"] as Number).toInt()
      val rects = (uniforms["pieceRect"] as List<*>).take(count).map { raw ->
        (raw as List<*>).map { it.toString().removeSuffix("dp").toDouble() }
      }
      val blends = (uniforms["pieceBlend"] as List<*>).map { it.toString().removeSuffix("dp").toDouble() }
      // Sulla retta dei centri la distanza di una capsula è la distanza dal suo intervallo.
      val sorted = rects.sortedBy { it[0] }
      if (frame.tMs < 180) continue
      val left = sorted[pair][0]
      val right = sorted[pair + 1][0]
      val joined = (0..200).all { tick ->
        val x = left + (right - left) * tick / 200
        var field = Double.POSITIVE_INFINITY
        for ((i, rect) in rects.withIndex()) {
          val distance = abs(x - rect[0]) - rect[2]
          val k = blends[i].coerceAtLeast(0.0001)
          val h = ((k - abs(field - distance)) / k).coerceAtLeast(0.0)
          field = minOf(field, distance) - h * h * k / 4
        }
        field <= 0
      }
      if (connected && !joined) breaks++
      assertFalse("${scene.note} coppia $pair: collo riformato a ${frame.tMs} ms", !connected && joined)
      connected = joined
      checked++
    }
    assertEquals(1, breaks)
    assertTrue(checked > 50)
  }
}
