package dev.antigravity.fluidengine.ui.glass.field

import android.view.Surface
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceFrame
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.completeRestBody
import dev.antigravity.fluidengine.ui.glass.field.trace.liquidElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI

class GlassLightEnvironmentTest {
  @Test
  fun offAndEmptyScenesDoNotRegister() {
    val sensor = FakeSensor()
    val environment = GlassLightEnvironment(sensor)
    environment.addSurface()
    assertEquals(0, sensor.starts)
    environment.removeSurface()
    environment.setAvailable(true)
    assertEquals(0, sensor.starts)
    assertEquals(0f, environment.angle, 0f)
  }

  @Test
  fun theFirstDrawnSurfaceStartsOneListenerAndTheLastStopsIt() {
    val sensor = FakeSensor()
    val environment = GlassLightEnvironment(sensor)
    environment.setAvailable(true)
    environment.addSurface()
    environment.addSurface()
    assertEquals(1, sensor.starts)
    environment.removeSurface()
    assertEquals(0, sensor.stops)
    environment.removeSurface()
    assertEquals(1, sensor.stops)
  }

  @Test
  fun suspensionRestoresTheNeutralAngleAndIgnoresQueuedSamples() {
    val sensor = FakeSensor()
    val environment = GlassLightEnvironment(sensor)
    environment.addSurface()
    environment.setAvailable(true)
    sensor.emit(0f, 0L)
    sensor.emit(5f, 25_000_000L)
    assertTrue(environment.angle > 0f)

    environment.setAvailable(false)
    assertEquals(1, sensor.stops)
    sensor.emit(7f, 50_000_000L)
    assertEquals(0f, environment.angle, 0f)

    environment.setAvailable(true)
    assertEquals(2, sensor.starts)
    sensor.emit(7f, 75_000_000L)
    assertEquals(0f, environment.angle, 0f)
  }

  @Test
  fun stationaryNoiseAndInvalidEventsDoNotPublish() {
    val filter = GlassLightFilter()
    assertNull(filter.sample(0.3f, 9f, Surface.ROTATION_0, 0L))
    for (frame in 1..100) {
      val x = if (frame % 2 == 0) 0.25f else 0.35f
      assertNull(filter.sample(x, 9f, Surface.ROTATION_0, frame * 25_000_000L))
    }
    assertNull(filter.sample(Float.NaN, 0f, Surface.ROTATION_0, 3_000_000_000L))
    assertNull(filter.sample(5f, 9f, Surface.ROTATION_0, 2_000_000_000L))
  }

  @Test
  fun anInclinationApproachesTheLimitGraduallyAndSettles() {
    val filter = GlassLightFilter()
    filter.sample(0f, 0f, Surface.ROTATION_0, 0L)
    val first = checkNotNull(filter.sample(10f, 0f, Surface.ROTATION_0, 25_000_000L))
    assertTrue(first in 0.08f..0.10f)
    var shown = first
    for (frame in 2..30) {
      filter.sample(10f, 0f, Surface.ROTATION_0, frame * 25_000_000L)?.let { next ->
        assertTrue(next > shown && next <= 0.38f)
        shown = next
      }
    }
    assertTrue(shown > 0.37f)
    assertNull(filter.sample(10f, 0f, Surface.ROTATION_0, 775_000_000L))

    for (frame in 32..65) {
      filter.sample(0f, 0f, Surface.ROTATION_0, frame * 25_000_000L)?.let { shown = it }
    }
    assertEquals(0f, shown, 0f)
  }

  @Test
  fun theFilterUsesScreenAxesAndRebasesOnRotation() {
    val filter = GlassLightFilter()
    filter.sample(0f, 0f, Surface.ROTATION_0, 0L)
    val portrait = checkNotNull(filter.sample(5f, 0f, Surface.ROTATION_0, 25_000_000L))
    assertEquals(0f, checkNotNull(filter.sample(0f, 0f, Surface.ROTATION_90, 50_000_000L)), 0f)
    val landscape = checkNotNull(filter.sample(0f, 5f, Surface.ROTATION_90, 75_000_000L))
    assertEquals(portrait, landscape, 0.000001f)
  }

  @Test
  fun turningTheReflexKeepsTheAxisUnitLength() {
    val direction = glassReflexDirection(0f, -1f, (PI / 2).toFloat())
    assertEquals(1f, direction.x, 0.000001f)
    assertEquals(0f, direction.y, 0.000001f)
    assertEquals(1f, direction.getDistance(), 0.000001f)
  }

  @Test
  fun exportEdgeLightFromTheRecipeAndFilter() {
    val dir = GlassTraceOutput.requestedDir(System.getenv("GLASS_F65_DIR")) ?: return
    val fps = 40
    val filter = GlassLightFilter()
    var angle = 0f
    val tilt = (0..100).map { frame ->
      val x = when (frame) {
        in 12..35 -> 6.5f
        in 36..67 -> -6.5f
        else -> 0f
      }
      filter.sample(x, 0f, Surface.ROTATION_0, frame * 25_000_000L)?.let { angle = it }
      angle
    }
    val sweep = (0..34).map { (it * 1.5 * PI / 34 - PI / 4).toFloat() }
    trace("inclinazione", fps, tilt, "Accelerometro simulato e filtro dell'engine: neutro, destra, sinistra, neutro.")
      .write(File(dir, "inclinazione.json"))
    trace("corsa-sul-bordo", 4, sweep, "Sequenza angolare di confronto con R4 B8: 270 gradi in 8,5 s.")
      .write(File(dir, "corsa-sul-bordo.json"))
  }

  private fun trace(name: String, fps: Int, angles: List<Float>, note: String): GlassTrace {
    val frames = angles.mapIndexed { frame, angle ->
      val recipe = glassRecipe(role = GlassRole.Interactive, widthDp = 120f, heightDp = 72f, darkSide = true)
      val element = liquidElement(
        linkedMapOf("name" to "capsula", "x" to 20, "y" to 16, "w" to 120, "h" to 72, "radius" to "capsule"),
        recipe, theme = null, note = "Interactive 120 × 72 dp, tema scuro",
      )
      @Suppress("UNCHECKED_CAST")
      val light = element.getValue("light") as List<Map<String, Any?>>
      val (x, y) = glassReflexDirection(recipe.reflexDirX, recipe.reflexDirY, angle)
      for (layer in light) {
        @Suppress("UNCHECKED_CAST")
        val uniforms = layer.getValue("uniforms") as MutableMap<String, Any?>
        uniforms["reflexDir"] = listOf(x, y)
      }
      completeRestBody(element)
      GlassTraceFrame(frame * 1000.0 / fps, listOf(element))
    }
    return GlassTrace(
      name = name, phase = "f6-5", density = 3f, theme = "dark",
      widthDp = 160f, heightDp = 104f, frames = frames, fps = fps,
      head = linkedMapOf("_" to note, "background" to mapOf("kind" to "color", "color" to "#0D0D0F")),
    )
  }

  private class FakeSensor : GlassLightSensor {
    var starts = 0
    var stops = 0
    private var callback: ((Float, Float, Int, Long) -> Unit)? = null

    override fun start(onSample: (Float, Float, Int, Long) -> Unit): Boolean {
      starts++
      callback = onSample
      return true
    }

    override fun stop() {
      stops++
    }

    fun emit(x: Float, timestampNs: Long) {
      callback?.invoke(x, 0f, Surface.ROTATION_0, timestampNs)
    }
  }
}
