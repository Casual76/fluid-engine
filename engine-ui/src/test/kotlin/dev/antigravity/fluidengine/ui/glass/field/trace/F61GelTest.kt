@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScopeImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectsUpdates
import dev.antigravity.fluidengine.ui.glass.field.*
import dev.antigravity.fluidengine.ui.fluidphysics.growPhysicsField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class F61GelTest {
  @Test fun diagonalFlexPreservesAreaAndReducedMotionHasNoFlexOrDrift() {
    fun sample(reduced: Boolean) = fluidGelPose(FluidPressFeedback.ExpandRound, 1f, Offset(20f, 12f),
      Offset(2000f, -2000f), Offset(37f, 12f), 44f, 44f, 1f, reduced)
    val gel = sample(false)
    assertEquals(1.35f, gel.growth, 0f)
    assertTrue(gel.invXY > 0f && gel.tip.x > 0 && gel.tip.y < 0)
    assertEquals(1f, gel.invXX * gel.invYY - gel.invXY * gel.invXY, 0.00001f)
    assertTrue(gel.invXX + gel.invXY < FluidGel.MaxStretch)
    val reduced = sample(true)
    assertEquals(1.175f, reduced.growth, 0f)
    assertEquals(1f, reduced.invXX, 0f)
    assertEquals(0f, reduced.invXY, 0f)
    assertEquals(Offset.Zero, reduced.tip)
    assertEquals(Offset.Zero, reduced.drift)
  }

  @Test fun roundGrowthCapAndScreenSigmaMatchAtDifferentDensities() {
    for (density in listOf(1f, 2.625f, 4f)) {
      for ((diameter, expected) in listOf(44f to 59.4f, 68f to 91.8f, 76f to 100f)) {
        val side = diameter * density
        val increase = fluidGelGrowth(FluidPressFeedback.ExpandRound, side, side, density)
        assertEquals(expected, diameter * (1f + increase), 0.001f)
        val radius = FluidGel.blurRadiusPx(FluidGel.BlurDp * density / (1f + increase))
        assertEquals(2.2f, (0.57735f * radius + 0.5f) * (1f + increase) / density, 0.0001f)
      }
    }
    assertEquals(0f, FluidGel.blurRadiusPx(0f), 0f)
  }

  @Test fun physicsGrowthUsesTheFieldPivotAndKeepsOpticalLengths() {
    val inputs = GlassDrawInputs(8)
    inputs.body.setSlab(20f, 12f, GlassSlabGeometry())
    inputs.body.radii[1] = 3f
    inputs.body.radii[3] = 5f
    inputs.thickness = 2f
    val pivot = Offset(30f, 20f)
    growPhysicsField(inputs, pivot, 1.5f)
    assertArrayEquals(floatArrayOf(0f, -1f, 15f, 9f), inputs.body.rects.copyOf(4), 0f)
    assertEquals(4.5f, inputs.body.radii[1], 0f)
    assertEquals(7.5f, inputs.body.radii[3], 0f)
    assertEquals(2f, inputs.thickness, 0f)
    inputs.kernel = GlassBodyKernel.Ring
    val ring = inputs.ring
    ring.count = 1
    ring.verts[0] = 34f
    ring.verts[1] = 24f
    ring.bandOuter = 4f
    floatArrayOf(20f, 10f, 40f, 30f).copyInto(ring.bbox)
    growPhysicsField(inputs, pivot, 1.5f)
    assertArrayEquals(floatArrayOf(36f, 26f), ring.verts.copyOf(2), 0f)
    assertArrayEquals(floatArrayOf(17f, 7f, 43f, 33f), ring.bbox, 0f)
    assertEquals(4f, ring.bandOuter, 0f)
  }

  @Test fun reducedGestureReturnsMonotonicallyWithTheFingerStillOut() = runTest {
    val clock = BroadcastFrameClock()
    val press = FluidPressState(CoroutineScope(coroutineContext + clock), FluidPressFeedback.ExpandRound)
    press.bindGel(1)
    press.update(FluidPressFeedback.ExpandRound, press.haptics, reducedMotion = true)
    var previous = 1f
    try {
      for (k in 0..100) {
        if (k == 0) { press.touchDown(Offset(22f, 22f), 0); press.press() }
        if (k == 8) press.touchMove(Offset(-30f, 15f), 133, 1f, false)
        if (k == 40) { press.touchEnd(); press.release() }
        runCurrent()
        clock.sendFrame(1_000_000_000L + glassTraceTimeNanos(k))
        runCurrent()
        val pose = press.gelPose(44f, 44f, 1f)
        assertEquals(Offset.Zero, pose.drift)
        assertEquals(Offset.Zero, pose.tip)
        assertEquals(1f, pose.invXX, 0f)
        assertEquals(0f, pose.invXY, 0f)
        assertTrue(pose.growth in 1f..1.175f)
        if (k == 39) assertEquals(1.175f, pose.growth, 0.001f)
        if (k >= 40) assertTrue(pose.growth <= previous + 0.00001f)
        previous = pose.growth
      }
      assertEquals(1f, previous, 0f)
    } finally {
      press.bindGel(-1)
    }
  }

  @Test fun gestureUsesOneBodyPerFrameAndReturnsOnce() {
    runGesture("tasto", 44f, FluidPressFeedback.ExpandRound, 34)
    runGesture("capsula-trascinata", 158f, FluidPressFeedback.Expand, 30)
    runGesture("tasto-interrotto", 44f, FluidPressFeedback.ExpandRound, 4)
    runGesture("capsula-larga", 320f, FluidPressFeedback.Expand, 34)
  }

  @Test fun exportTasteVariants() {
    if (System.getenv("GLASS_F61_DIR") == null) return
    for (growth in listOf(0.2f, 0.35f, 0.5f)) {
      val suffix = ((1f + growth) * 100).roundToInt()
      // Confronto controllato: per la sola tavola liberiamo il tetto in dp della capsula di prova.
      val feedback = FluidPressFeedback.ExpandRound.copy(maxScale = growth, longSideCapDp = 200f)
      runGesture("tondo-$suffix", 44f, feedback, 34)
      runGesture("capsula-$suffix", 158f, feedback, 30, blur = true)
    }
    runGesture("icona-netta", 44f, FluidPressFeedback.ExpandRound, 34, blur = false)
    runGesture("capsula-netta", 158f, FluidPressFeedback.ExpandRound.copy(longSideCapDp = 200f), 30, blur = false)
  }

  private fun runGesture(name: String, width: Float, feedback: FluidPressFeedback, up: Int, blur: Boolean = width == 44f) = runTest {
    val clock = BroadcastFrameClock()
    val press = FluidPressState(CoroutineScope(coroutineContext + clock), feedback)
    press.bindGel(1)
    val blurAmount = Animatable(0f)
    val blurJob = if (blur) launch(clock) { press.animateIconBlur(blurAmount) } else null
    runCurrent()
    val geometry = FluidGlassPressGeometry(press)
    val optics = GlassDefaults.buttonOptics().sanitized()
    val surface = GlassLiquidSurface(GlassRole.Interactive, optics, FluidCapsuleShape, false, 1f,
      { 1f }, { 1f }, { press.progress.coerceIn(0f, 1f) }, { 1f }, { Color.Unspecified },
      { Color.Unspecified }, { null }, gel = geometry)
    val scope = object : BackdropEffectScopeImpl() { override val shape = FluidCapsuleShape }
    scope.size = Size(width, 44f)
    val observer = SnapshotStateObserver { it() }
    observer.start()
    var builds = 0
    var bodyHalfWidth = 0f
    var capturePadding = 0f
    val previousProbe = glassBodyProbe
    glassBodyProbe = { owner, inputs ->
      if (owner === scope) {
        builds++
        bodyHalfWidth = inputs.body.rects[2]
        capturePadding = inputs.paddingPx
      }
      previousProbe?.invoke(owner, inputs)
    }
    lateinit var updates: BackdropEffectsUpdates
    val invalidate: (Any) -> Unit = { updates.invalidate() }
    updates = BackdropEffectsUpdates({ observer.observeReads(scope, invalidate, it) }, {
      scope.apply(surface.effects)
    })
    val dir = System.getenv("GLASS_F61_DIR")?.let(::File)
    val frames = mutableListOf<GlassTraceFrame>()
    val csv = mutableListOf("ms,progress,growth,driftX,driftY,xx,xy,yy,tipX,tipY,bodyBuilds,bodyHalfWidth,capturePadding,clip,sigmaDp")
    val canvas = if (width == 44f) 108f else width * 1.65f
    val center = Offset(width / 2, 22f)
    var last = Offset.Zero
    var below = false
    var returned = false
    var minScale = 1f
    var maxScale = 1f
    var beyond = false
    var restingPadding = 0f
    var lastGrowth = 1f
    var lastSigma = 0f
    var activeFrames = 0
    try {
      for (k in -2..(up + 48)) {
        val ms = glassTraceTimeMs(k)
        if (k == 0) {
          press.touchDown(center, 0)
          press.press()
        }
        if (k in 1 until up && up > 4) {
          val t = ((k - 2f) / (up - 7f)).coerceIn(0f, 1f)
          val smooth = t * t * (3f - 2f * t)
          val finger = Offset(-30f * smooth, (if (width == 44f) 9f else 4f) * smooth)
          if (finger != last) press.touchMove(finger - last, ms.toLong(), 1f, false)
          last = finger
        }
        if (k == up) { press.touchEnd(); press.release() }
        Snapshot.sendApplyNotifications()
        runCurrent()
        clock.sendFrame(1_000_000_000L + glassTraceTimeNanos(k))
        runCurrent()
        Snapshot.sendApplyNotifications()
        val previous = builds
        updates.beforeFrame(true)
        updates.onDraw(false)
        val count = builds - previous
        assertTrue("due catene nello stesso fotogramma", count <= 1)
        if (k > up + 43) assertEquals("catena ferma dopo il ritorno", 0, count)
        val pose = geometry.pose(width, 44f, 1f)
        if (k == -2) restingPadding = capturePadding
        val clip = surface.clipShapeBlock() !== GlassUnclippedShape
        assertEquals("lastra del corpo", width * pose.growth / 2f, bodyHalfWidth, 0.001f)
        if (geometry.active) {
          activeFrames++
          assertFalse("il ritaglio taglierebbe il filo", clip)
          assertTrue("margine della cattura", capturePadding > restingPadding)
        } else {
          assertTrue(clip)
          assertEquals(restingPadding, capturePadding, 0f)
        }
        if (k == up && up == 4) assertTrue("continuità al distacco", abs(pose.growth - lastGrowth) < 0.10f)
        lastGrowth = pose.growth
        if (k >= up && pose.growth < 1f) {
          assertFalse("secondo sottopasso", returned)
          below = true
        }
        if (below && pose.growth >= 1f) returned = true
        if (k >= up && pose.drift.x > 0) beyond = true
        minScale = minOf(minScale, pose.growth)
        maxScale = maxOf(maxScale, pose.growth)
        val sigma = FluidGel.BlurDp * blurAmount.value
        if (name == "tasto") {
          when (k) {
            0 -> assertEquals(0f, sigma, 0.001f)
            6 -> assertEquals("ingresso Apple a +100 ms", 1.11f, sigma, 0.12f)
            30, up -> assertEquals("culmine del tenuto", 2.2f, sigma, 0.01f)
            up + 6 -> assertEquals("rilascio Apple a +100 ms", 1.07f, sigma, 0.12f)
            up + 12 -> assertEquals("rilascio Apple a +200 ms", 0.40f, sigma, 0.12f)
            up + 22 -> assertEquals(0f, sigma, 0.001f)
          }
        }
        if (name == "tasto-interrotto") {
          if (k == up) assertTrue("blur interrotto prima del culmine", sigma > 0f && sigma < FluidGel.BlurDp)
          if (k > up) assertTrue("rientro del blur dal valore raggiunto", sigma <= lastSigma)
        }
        lastSigma = sigma
        if (!blur) assertEquals(0f, sigma, 0f)
        csv += listOf(ms, press.progress, pose.growth, pose.drift.x, pose.drift.y, pose.invXX, pose.invXY,
          pose.invYY, pose.tip.x, pose.tip.y, count, bodyHalfWidth, capturePadding, clip, sigma).joinToString(",")
        if (dir != null) frames += GlassTraceFrame(ms, listOf(element(width, canvas, pose, press, sigma)))
      }
      assertTrue("la catena ha letto il gesto", builds > 1 && activeFrames > 0)
      assertTrue("$name: ritorno, min=$minScale", below && returned && minScale >= 0.9729f)
      assertTrue("$name: deriva oltre il riposo", beyond || up == 4)
      if (up == 4) assertTrue("il rilascio interrotto resta parziale", maxScale in 1.05f..1.48f)
      assertEquals(0f, press.progress, 0.0001f)
      assertEquals(Offset.Zero, press.lean)
      if (dir != null) {
        val folder = File(dir, name).apply { mkdirs() }
        GlassTrace(name, "f6-1", 3f, "light", canvas, 90f, frames, head = mapOf(
          "background" to mapOf("kind" to "gradient4", "tl" to "#C9C4C5", "tr" to "#787D82",
            "bl" to "#CECACD", "br" to "#606870"),
          "events" to listOf(mapOf("t" to glassTraceTimeMs(up), "kind" to "mark", "label" to "rilascio")),
          "probes" to listOf(mapOf("name" to "luce", "kind" to "luma", "rect" to listOf(canvas / 2 - 4, 38, 8, 4))),
        )).write(File(folder, "trace.json"))
        File(folder, "curve.csv").writeText(csv.joinToString("\n"))
      }
    } finally {
      blurJob?.cancel()
      glassBodyProbe = previousProbe
      observer.stop()
      observer.clear()
      press.bindGel(-1)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun element(width: Float, canvas: Float, pose: FluidGelPose, press: FluidPressState, sigma: Float): Map<String, Any?> {
    val g = press.progress.coerceIn(0f, 1f)
    val recipe = glassRecipe(role = GlassRole.Interactive, widthDp = width, heightDp = 44f, darkSide = false,
      optics = GlassDefaults.buttonOptics().sanitized(), pressed = g)
    // Il riquadro segue la crescita uniforme anche per l'ombra. Il campo applica la matrice ai pixel nel riquadro.
    val w = width * pose.growth
    val h = 44f * pose.growth
    val el = liquidElement(mapOf("name" to "vetro", "x" to (canvas - w) / 2 + pose.drift.x,
      "y" to (90f - h) / 2 + pose.drift.y, "w" to w, "h" to h, "radius" to "capsule"), recipe, null, "F6.1")
    val moving = pose.growth != 1f || pose.invXX != 1f || pose.tip != Offset.Zero
    el["clip"] = !moving
    el["marginDp"] = if (moving) 12f else 0f
    (el.getValue("capture") as MutableMap<String, Any?>)["scale"] = 1
    val body = (el["body"] as Map<String, Any?>)["uniforms"] as MutableMap<String, Any?>
    val lights = (el["light"] as List<Map<String, Any?>>).map { it["uniforms"] as MutableMap<String, Any?> }
    for (u in lights + listOf(body)) {
      u["flex"] = listOf(pose.invXX, pose.invXY, pose.invXY, pose.invYY)
      u["flexCenter"] = listOf(glassDp(w / 2), glassDp(h / 2))
      u["gelTip"] = listOf(glassDp(pose.tip.x), glassDp(pose.tip.y))
    }
    for (u in lights) {
      u["glow"] = recipe.glowPeak * g
      u["glowRadius"] = glassDp(width)
      u["glowCenter"] = listOf(glassDp(press.glow.x.coerceIn(0f, width) + (w - width) / 2),
        glassDp(press.glow.y.coerceIn(0f, 44f) + (h - 44f) / 2))
    }
    LiquidTasto.completeBody(el)
    body["nodeClip"] = listOf(0, 0)
    el["content"] = listOf(mapOf(
      "name" to "icone",
      "icons" to if (width == 44f) listOf("share") else listOf("back", "plus", "circle", "triangle"),
      "iconSizeDp" to 26f * pose.growth,
      "scale" to listOf(pose.scaleX / pose.growth, pose.scaleY / pose.growth),
      "blurSigmaDp" to sigma,
      "color" to "#202228",
    ))
    return el
  }
}
