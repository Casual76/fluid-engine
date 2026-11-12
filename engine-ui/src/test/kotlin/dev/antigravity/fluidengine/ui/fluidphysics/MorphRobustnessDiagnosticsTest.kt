package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.field.glassLiquidLargeVeilGain
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/** Diagnosi ripetibile dei noti, non un nuovo criterio di accettazione e non una modifica al motore. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MorphRobustnessDiagnosticsTest {
  private class Clock : MonotonicFrameClock {
    val frames = Channel<Long>(Channel.UNLIMITED)
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.receive())
  }

  private data class Frame(
    val index: Int, val ms: Double, val progress: Float, val rest: FluidForm.Slab,
    val ringWidth: Float, val ringHeight: Float, val crossings: Int, val liquidDisplacement: Float,
    val values: FloatArray,
  )

  /**
   * `floor` cambia SOLO la destinazione sintetica della prova, poi la posa alla destinazione vera.
   * `contourFloor` lascia intatto morphTo: limita soltanto il contorno inseguito e ricostruito nel banco.
   * Sono due ipotesi diverse: non si attribuisce al motore un rimedio che non è stato implementato.
   */
  private fun trace(case: MorphRobustnessCases.Case, hz: Int, floor: Float = 0f, contourFloor: Float = 0f): List<Frame> = collect(case, hz, floor, contourFloor)

  private fun collect(case: MorphRobustnessCases.Case, hz: Int, floor: Float, contourFloor: Float): List<Frame> {
    val result = mutableListOf<Frame>()
    runTest {
      val state = FluidPhysicsState(case.from).apply { liquidAllowed = true }
      val original = case.to as FluidForm.Slab
      val target = original.floorSide(floor)
      val clock = Clock()
      val journey = launch(clock) { state.morphTo(target) }
      runCurrent()
      val material = PhysicsMaterialSource(GlassDefaults.buttonOptics().sanitized(), GlassDefaults.panelOptics(compact = true).sanitized())
      val veil = PhysicsLargeVeil()
      val oracle = MorphRobustnessTest(case, "diagnosi", hz, false)
      val restPoints = FloatArray(state.liquid.points * 2)
      val ring = FloatArray(DropRenderPoints * 2)
      val perimeter = RoundedRectPerimeter()
      val parameters = FloatArray(DropRenderPoints)
      var time = 0L
      for (index in 0 until hz * 8) {
        time += 1_000_000_000L / hz
        clock.frames.send(time)
        runCurrent()
        val rest = requireNotNull(state.dropRestSlabNow()).floorSide(contourFloor)
        sampleRestContour(rest, state.liquid.points, restPoints)
        val anchor = state.dropAnchorNow()
        state.liquid.step(time, restPoints, anchor.x, anchor.y, FluidMotion.DropAnchorReachDp, 1f)
        val plan = state.ensurePlan()
        val count: Int
        val vertices: FloatArray
        if (contourFloor > 0f && !state.liquid.quiet) {
          buildDropRing(rest, state.liquid, DropRenderPoints, ring, perimeter, parameters)
          vertices = ring
          count = DropRenderPoints
        } else {
          vertices = plan.verts
          count = plan.vertCount
        }
        val crossings = if (count > 2) crossings(vertices, count) else 0
        val largeNow = glassLiquidLargeVeilGain(physicsRecipeShortPx(plan))
        val meta = state.journeyTargetNow()
        val large = if (meta == null) veil.settle(largeNow, time) else veil.next(
          state.journeyOriginNow()?.let { glassLiquidLargeVeilGain(physicsFormShortPx(it)) } ?: largeNow,
          glassLiquidLargeVeilGain(physicsFormShortPx(meta)), time)
        val recipe = physicsLiquidRecipe(GlassRole.Modal, material.at(state.progress), physicsRecipeShortPx(plan),
          false, 1f, 1f, 0f, GlassTint(Color.Transparent, Color.Transparent, Color.Transparent, dev.antigravity.fluidengine.ui.fluid.GlassTintMode.Neutral),
          null, state.progress, false, state.liquid.lens, largeVeil = large)
        val metrics = oracle.metrics(plan, validateContour = false)
        val displacement = state.liquid.displacementX.indices.maxOf {
          hypot(state.liquid.displacementX[it], state.liquid.displacementY[it])
        }
        result += Frame(index, time / 1e6, state.progress, rest,
          if (count > 2) (0 until count).maxOf { vertices[it*2] } - (0 until count).minOf { vertices[it*2] } else plan.bounds.width,
          if (count > 2) (0 until count).maxOf { vertices[it*2+1] } - (0 until count).minOf { vertices[it*2+1] } else plan.bounds.height,
          crossings, displacement,
          metrics + floatArrayOf(recipe.veilColor.alpha, recipe.blurDp, recipe.refractionDp))
        if (journey.isCompleted && state.liquid.quiet) break
      }
      assertTrue("la diagnosi deve terminare", journey.isCompleted && state.liquid.quiet)
      // Posa sintetica dopo il pavimento: si misura separatamente dal viaggio, non si nasconde il salto.
      val beforePose = oracle.metrics(state.ensurePlan(), validateContour = false)
      state.snapTo(original)
      val afterPose = oracle.metrics(state.ensurePlan(), validateContour = false)
      if (floor > 0f) println("POSA ${case.name} ${hz}Hz minimo=$floor deltaArea=${abs(beforePose[0]-afterPose[0]).f()} deltaBaricentro=${hypot(beforePose[1]-afterPose[1],beforePose[2]-afterPose[2]).f()}")
      assertTrue(state.restFormNow() == original)
    }
    return result
  }

  @Test fun separaLeGrandezzeDeiQuattroSuperamenti() {
    for (name in listOf("taglia-24-600", "taglia-24-600-inverso")) for (hz in listOf(60, 120)) {
      val case = MorphRobustnessCases.all.single { it.name == name }
      val frames = trace(case, hz)
      val scale = maxOf(case.from.frame.maxDimension, case.to.frame.maxDimension,
        (case.from.frame.center - case.to.frame.center).getDistance(), 1f)
      val limit = 20f * (1_000_000_000L / hz) / 1e9f + .001f
      val labels = listOf("area", "baricentro", "velo", "sfocatura", "rifrazione")
      val maxima = FloatArray(5)
      val first = arrayOfNulls<String>(5)
      var badFrames = 0
      for ((a, b) in frames.zipWithNext()) {
        val raw = floatArrayOf(abs(b.values[0]-a.values[0]), hypot(b.values[1]-a.values[1], b.values[2]-a.values[2]),
          abs(b.values[3]-a.values[3]), abs(b.values[4]-a.values[4]), abs(b.values[5]-a.values[5]))
        val normalized = floatArrayOf(raw[0]/(scale*scale), raw[1]/scale, raw[2], raw[3]/50f, raw[4]/50f)
        if (normalized.any { it > limit }) badFrames++
        for (i in normalized.indices) {
          maxima[i] = maxOf(maxima[i], normalized[i])
          if (normalized[i] > limit) println("SUPERAMENTO $name ${hz}Hz ${labels[i]} frame=${b.index} ms=${b.ms.f()} delta=${raw[i].f()} normalizzato=${normalized[i].f()} limite=${limit.f()} eccesso=${(normalized[i]-limit).f()}")
          if (normalized[i] > limit && first[i] == null) first[i] =
            "frame=${b.index} ms=${b.ms.f()} da=${a.values.joinToString { it.f() }} a=${b.values.joinToString { it.f() }} delta=${raw[i].f()} normalizzato=${normalized[i].f()} eccesso=${(normalized[i]-limit).f()}"
        }
      }
      println("CONTINUITA $name ${hz}Hz limite=${limit.f()} fotogrammiFuori=$badFrames")
      for (i in labels.indices) println("  ${labels[i]} massimo=${maxima[i].f()} primo=${first[i] ?: "mai"}")
    }
  }

  @Test fun misuraIncrociEPavimentiSoloNelBanco() {
    for (name in listOf("larghezza-zero-inverso", "altezza-zero-inverso", "un-pixel-inverso")) for (hz in listOf(60, 120)) {
      val case = MorphRobustnessCases.all.single { it.name == name }
      for (floor in listOf(0f, 2f, 4f, 8f, 16f, 32f, 64f)) {
        val frames = trace(case, hz, floor = floor)
        describeCrossings(name, hz, "destinazioneMin=${floor.f()}", frames)
      }
      describeCrossings(name, hz, "soloContornoMin=2", trace(case, hz, contourFloor = 2f))
    }
  }

  private fun describeCrossings(name: String, hz: Int, mode: String, frames: List<Frame>) {
    val bad = frames.filter { it.crossings > 0 }
    println("INCROCI $name ${hz}Hz $mode fotogrammi=${bad.size}/${frames.size} " +
      if (bad.isEmpty()) "nessuno" else {
        val first = bad.first(); val last = bad.last()
        "primo=${first.index} ms=${first.ms.f()} progress=${first.progress.f()} " +
          "rest=${first.rest.frame.width.f()}x${first.rest.frame.height.f()} ring=${first.ringWidth.f()}x${first.ringHeight.f()} scartoMax=${first.liquidDisplacement.f()} " +
          "ultimo=${last.index} restMinIntervallo=${bad.minOf { it.rest.frame.minDimension }.f()}..${bad.maxOf { it.rest.frame.minDimension }.f()} " +
          "tagliaMinPrima=${frames.take(first.index).minOfOrNull { it.rest.frame.minDimension }?.f()} " +
          "posa=${frames.last().rest.frame.width.f()}x${frames.last().rest.frame.height.f()}"
      })
  }

  private fun FluidForm.Slab.floorSide(min: Float): FluidForm.Slab {
    if (min <= 0f || (frame.width >= min && frame.height >= min)) return this
    val w = maxOf(min, frame.width); val h = maxOf(min, frame.height); val c = frame.center
    val half = minOf(w,h)/2
    return copy(frame = Rect(c.x-w/2,c.y-h/2,c.x+w/2,c.y+h/2), cornerRadii = FluidCornerRadii(
      cornerRadii.topLeft.coerceIn(0f,half),cornerRadii.topRight.coerceIn(0f,half),
      cornerRadii.bottomRight.coerceIn(0f,half),cornerRadii.bottomLeft.coerceIn(0f,half)))
  }

  /** Stessa tolleranza dell'oracolo di accettazione, senza fermarsi al primo incrocio. */
  private fun crossings(p: FloatArray, n: Int): Int {
    fun cross(a: Int, b: Int, c: Int) =
      (p[b*2]-p[a*2])*(p[c*2+1]-p[a*2+1])-(p[b*2+1]-p[a*2+1])*(p[c*2]-p[a*2])
    var count = 0
    for (i in 0 until n) for (j in i+2 until n) {
      if (i == 0 && j == n-1) continue
      val b=(i+1)%n; val d=(j+1)%n
      if (cross(i,b,j)*cross(i,b,d) < -.001f && cross(j,d,i)*cross(j,d,b) < -.001f) count++
    }
    return count
  }

  private fun Float.f() = String.format(Locale.ROOT, "%.6f", this)
  private fun Double.f() = String.format(Locale.ROOT, "%.3f", this)
}
