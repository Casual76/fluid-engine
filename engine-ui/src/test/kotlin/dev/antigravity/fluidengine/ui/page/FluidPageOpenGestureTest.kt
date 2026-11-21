package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceFrame
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.glassTraceTimeMs
import java.io.File
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FluidPageOpenGestureTest {
  private val clock = BroadcastFrameClock()
  private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
  private val source = FluidTransitionSource("accessorio", FluidGlassNamespace())
  private var card = Rect(100f, 1800f, 900f, 2000f)
  private val frameNanos = 8_333_333L
  private var time = 0L
  private var opens = 0
  private var backs = 0
  private val state = FluidPageHostState().also {
    it.scope = scope
    it.width = 1000f
    it.height = 2000f
    it.transitionOf = { FluidPageTransition.Expand }
    it.sourceOf = { source }
    it.resolveSource = { FluidTransitionSourceGeometry(card, RoundedCornerShape(16.dp)) }
    it.onBack = { backs++; it.onStack(listOf("lista")) }
    it.onStack(listOf("lista"))
  }

  @After fun close() {
    scope.cancel()
    state.releaseSources()
  }

  private fun frame() {
    clock.sendFrame(time)
    time += frameNanos
  }

  private fun open(): Int = requireNotNull(state.beginVertical {
    opens++
    state.onStack(listOf("lista", "pagina"))
    state.pagePlaced("pagina")
  })

  private fun presence() = state.fractionToward { it == "pagina" }

  @Test fun `il dito e la molla percorrono la stessa sagoma nei due versi`() {
    for (fraction in listOf(0.25f, 0.50f, 0.75f)) {
      val token = open()
      assertEquals(card, requireNotNull(state.zoomShape()).bounds)
      state.moveVertical(token, -state.height * fraction, -450f)
      assertEquals(fraction, presence(), 0.00001f)
      val atTurn = state.zoomShape()
      val speed = state.velocity
      state.releaseVertical(token, -450f)
      assertEquals(atTurn, state.zoomShape())
      assertEquals(speed, state.velocity, 0f)
      repeat(4) { frame() }
      val atGrab = state.zoomShape()
      val moving = state.velocity
      val again = requireNotNull(state.beginVertical(null))
      assertEquals(atGrab, state.zoomShape())
      assertEquals(moving, state.velocity, 0f)
      val grabTravel = state.travelPx
      frame()
      // FloatSpringSpec integra il tempo del vsync in millisecondi interi.
      val dt = (frameNanos / 1_000_000L) / 1000f
      val expectedDelta = moving * dt * kotlin.math.exp((-2 * Math.PI * dt / FluidPageMotion.ResponseZoomSeconds)).toFloat()
      assertEquals("anche la sagoma avanza al primo vsync della ripresa", expectedDelta, state.travelPx - grabTravel, 0.001f)
      val before = presence()
      state.moveVertical(again, 40f, 300f)
      assertTrue(presence() < before)
      state.moveVertical(again, -40f, -300f)
      assertEquals(before, presence(), 0.00001f)
      state.releaseVertical(again, 2000f)
      repeat(200) { frame() }
      assertEquals("lista", state.upper)
      assertFalse(source.isHidden)
    }
    assertEquals(3, opens)
    assertEquals(3, backs)
  }

  @Test fun `il dito breve torna e il lancio breve apre una volta anche se la pila arriva dopo`() {
    val token = open()
    state.moveVertical(token, -25f, -50f)
    state.releaseVertical(token, 0f)
    repeat(200) { frame() }
    assertEquals("lista", state.upper)
    val next = requireNotNull(state.beginVertical { opens++ })
    state.moveVertical(next, -25f, -2000f)
    state.releaseVertical(next, -2000f)
    state.onStack(listOf("lista", "pagina"))
    state.pagePlaced("pagina")
    repeat(200) { frame() }
    assertEquals("pagina", state.upper)
    assertTrue(source.isHidden)
    assertEquals(2, opens)
    assertEquals(1, backs)
  }

  @Test fun `la lista cede solo il residuo verticale e il dito chiude sulla sorgente scomparsa`() {
    state.dismissOf = { FluidPageDismiss.Down }
    state.onStack(listOf("lista", "pagina"))
    state.pagePlaced("pagina")
    repeat(200) { frame() }
    val handoff = VerticalPageHandoff(1)
    val wheel = NestedScrollSource.UserInput
    assertEquals(Offset.Zero, handoff.onPostScroll(Offset(0f, 100f), Offset.Zero, wheel))
    assertEquals(Offset.Zero, handoff.onPostScroll(Offset.Zero, Offset(80f, 2f), wheel))
    assertEquals(Offset.Zero, handoff.onPostScroll(Offset.Zero, Offset(0f, 20f), NestedScrollSource.SideEffect))
    assertEquals(Offset(0f, 20f), handoff.onPostScroll(Offset(0f, 28f), Offset(0f, 20f), wheel))
    val token = requireNotNull(state.beginVertical(null))
    state.moveVertical(token, handoff.take(), 0f)
    assertEquals(0.99f, presence(), 0.00001f)
    assertEquals(0f, handoff.take(), 0f)
    state.resolveSource = { null }
    state.releaseVertical(token, 2200f)
    repeat(200) { frame() }
    assertEquals("lista", state.upper)
    assertEquals(1, backs)
    assertFalse(source.isHidden)
  }

  @Test fun `la rotazione rimisura la sorgente conservando progresso e velocita normalizzata`() {
    val token = open()
    state.moveVertical(token, -1000f, -200f)
    card = Rect(250f, 900f, 1750f, 1000f)
    state.resizePage(2000f, 1000f)
    assertEquals(0.5f, presence(), 0f)
    assertEquals(Rect(125f, 450f, 1875f, 1000f), requireNotNull(state.zoomShape()).bounds)
    state.moveVertical(token, -50f, -200f)
    assertEquals(0.55f, presence(), 0.00001f)
    state.releaseVertical(token, -200f)
    repeat(5) { frame() }
    val before = presence()
    val speed = state.velocity / state.exitTravel()
    state.resizePage(1000f, 2000f)
    assertEquals(before, presence(), 0.00001f)
    assertEquals(speed, state.velocity / state.exitTravel(), 0.00001f)
    frame()
    assertTrue(presence() >= before)
    repeat(200) { frame() }
    assertEquals("pagina", state.upper)
  }

  @Test fun `tracce a 120 Hz di presa inversione e ritorno nella sorgente`() {
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B5A_DIR"))
    for (percent in listOf(25, 50, 75)) {
      val frames = mutableListOf<GlassTraceFrame>()
      fun sample() {
        val rect = state.zoomShape()?.bounds ?: if (state.upper == "pagina") state.fullZoomShape().bounds else card
        val sourceAlpha = if (!source.isHidden) 1f else state.zoomCrossfadeAlpha()
        val pageAlpha = if (state.isMoving || state.upper == "pagina") 1f - sourceAlpha else 0f
        val unit = 2.5f
        fun ink(alpha: Float) = "#17223C%02X".format((alpha * 255).roundToInt())
        val x = rect.left / unit
        val y = rect.top / unit
        val w = rect.width / unit
        val h = rect.height / unit
        val outline = mapOf("kind" to "shape", "name" to "sagoma", "x" to x, "y" to y, "w" to w, "h" to h,
          "radius" to ((state.zoomShape()?.corners?.topLeft ?: 16f) / unit), "color" to "#EEF2FC",
          "_presenza" to presence(), "_velocita" to state.velocity, "_sorgenteConsumata" to source.isHidden)
        val face = mapOf("kind" to "text", "text" to "Il brano", "x" to 16, "y" to 36,
          "sizeDp" to 15, "color" to ink(sourceAlpha))
        val page = mapOf("kind" to "text", "text" to "In riproduzione", "x" to 16, "y" to h / 2,
          "sizeDp" to 24, "color" to ink(pageAlpha))
        val layer = mapOf("name" to "facce", "x" to 0, "y" to 0, "w" to w, "h" to h,
          "items" to listOf(face, page))
        frames += GlassTraceFrame(glassTraceTimeMs(frames.size, 120),
          listOf(outline + ("content" to listOf(layer))))
      }
      sample()
      val held = open()
      // Una mezza cosinusoide rappresenta un dito che accelera e si ferma alla quota richiesta.
      fun drag(token: Int, distance: Float, count: Int, fling: Boolean = false): Float {
        var previous = 0f
        var speed = 0f
        for (step in 1..count) {
          val angle = Math.PI * step / count
          val fraction = if (fling) 1 - cos(angle / 2) else (1 - cos(angle)) / 2
          val next = distance * fraction.toFloat()
          val delta = next - previous
          speed = delta * 120
          state.moveVertical(token, delta, speed)
          frame()
          sample()
          previous = next
        }
        return speed
      }
      val release = drag(held, -state.height * percent / 100, 36)
      assertEquals(percent / 100f, presence(), 0.00001f)
      state.releaseVertical(held, release)
      repeat(5) { frame(); sample() }
      val before = state.velocity
      val again = requireNotNull(state.beginVertical(null))
      assertEquals(before, state.velocity, 0f)
      val fling = drag(again, state.height * percent / 160, 28, fling = true)
      state.releaseVertical(again, fling)
      var tail = 0
      while (state.isMoving && tail++ < 180) { frame(); sample() }
      assertFalse(state.isMoving)
      assertFalse(source.isHidden)
      val name = "elemento-interrotto-" + percent
      val probes = listOf("w", "h", "_presenza", "_velocita").map { field ->
        mapOf("name" to field.trimStart('_'), "kind" to "value", "path" to "sagoma." + field)
      }
      val trace = GlassTrace(name, "f6b-5a", 1f, "light", 400f, 800f, frames, 120, mapOf(
        "background" to mapOf("kind" to "color", "color" to "#A4B7CD"), "probes" to probes,
        "events" to listOf(mapOf("kind" to "interrupt", "t" to glassTraceTimeMs(41, 120), "label" to "ripresa"))))
      if (output != null) trace.write(File(output, name + "/trace.json"))
    }
  }

  @Test fun `riprendere lo zoom durante il ritorno conserva anche la velocita`() {
    state.onStack(listOf("lista", "pagina"))
    state.pagePlaced("pagina")
    repeat(200) { frame() }
    val token = requireNotNull(state.beginDrag(1))
    state.dragTo(token, state.exitTravel() / 2)
    state.release(token, -350f, decided = false)
    repeat(3) { frame() }
    val shape = requireNotNull(state.zoomShape())
    val velocity = state.velocity
    assertTrue(velocity < 0f)
    requireNotNull(state.beginDrag(1))
    val grabbed = requireNotNull(state.zoomShape()).bounds
    assertEquals(shape.bounds.left, grabbed.left, 0.001f)
    assertEquals(shape.bounds.bottom, grabbed.bottom, 0.001f)
    assertEquals("la presa non azzera la velocità della molla", velocity, state.velocity, 0f)
  }
}
