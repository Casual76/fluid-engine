package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceFrame
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.glassTraceTimeMs
import dev.antigravity.fluidengine.ui.glass.field.trace.completeRestBody
import dev.antigravity.fluidengine.ui.glass.field.trace.SceneAsk
import dev.antigravity.fluidengine.ui.glass.field.trace.liquidFromGlass
import java.io.File
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FluidSharedElementTest {
  private val sourceBox = Rect(38f, 389f, 282f, 437f)
  private val smallArt = Rect(48f, 397f, 80f, 429f)
  private val largeArt = Rect(30f, 64f, 290f, 324f)
  private val full = FluidZoomShape(Rect(0f, 0f, 320f, 480f))
  private val source = FluidZoomShape(sourceBox, FluidPageCorners(24f, 24f, 24f, 24f))

  @Test fun unaTraiettoriaPerDueSediConLoStessoNome() {
    val ns = FluidGlassNamespace()
    assertEquals(SharedKey(ns, "foto"), SharedKey(ns, "foto"))
    assertNotEquals(SharedKey(ns, "foto"), SharedKey(FluidGlassNamespace(), "foto"))
    for (i in 0..120) {
      val p = i / 120f
      val outline = FluidZoom.lerp(source, full, p)
      assertEquals(androidx.compose.ui.geometry.lerp(smallArt, largeArt, p),
        FluidZoom.sharedBounds(smallArt, largeArt, p, outline, source, full))
    }
    val displaced = FluidZoom.lerp(source, full, 0.5f)
    val delta = androidx.compose.ui.geometry.Offset(21f, 14f)
    assertEquals(androidx.compose.ui.geometry.lerp(smallArt, largeArt, 0.5f).translate(delta),
      FluidZoom.sharedBounds(smallArt, largeArt, 0.5f, displaced.translate(delta), source, full))
  }

  @Test fun treTrattiDisgiuntiInEntrambiIVersi() {
    for (i in 0..1200) {
      val p = i / 1200f
      val frame = FluidZoom.faces(p)
      assertTrue(frame.sourceAlpha * frame.pageAlpha == 0f)
      if (p >= FluidPageMotion.SourceFaceEnd) assertEquals(0f, frame.sourceAlpha, 0f)
      if (p <= FluidPageMotion.PageChromeStart) assertEquals(0f, frame.pageAlpha, 0f)
      if (frame.sourceAlpha in 0.01f..0.8f) assertTrue(frame.sourceBlur > 0f)
      if (frame.pageAlpha in 0.01f..0.8f) assertTrue(frame.pageBlur > 0f)
    }
    assertEquals(1f, FluidZoom.faces(1f).pageAlpha, 0f)
    assertEquals(0f, FluidZoom.faces(1f).riseDp, 0f)
  }

  private inner class Run(val grow: Boolean) : AutoCloseable {
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob() + clock)
    val handle = FluidTransitionSource("sorgente", FluidGlassNamespace()).let { if (grow) it.growingGlass() else it }
    val state = FluidPageHostState().also {
      it.scope = scope
      it.width = full.bounds.width
      it.height = full.bounds.height
      it.transitionOf = { FluidPageTransition.Expand }
      it.dismissOf = { FluidPageDismiss.Down }
      it.sourceOf = { handle }
      it.resolveSource = { FluidTransitionSourceGeometry(sourceBox, RoundedCornerShape(24.dp)) }
      it.onBack = { it.onStack(listOf("lista")) }
      it.onStack(listOf("lista"))
    }
    var time = 0L
    val frames = mutableListOf<Sample>()

    fun sample() {
      val p = state.fractionToward { it == "pagina" }
      frames += Sample(p, state.zoomShape() ?: if (p == 1f) full else source,
        if (!state.isMoving && p == 1f) 1f else state.solidOpacity,
        if (state.isMoving) state.zoomFaces() else FluidZoom.faces(p))
    }

    fun tick() {
      clock.sendFrame(time)
      time += 8_333_333L
      sample()
    }

    fun push() {
      state.onStack(listOf("lista", "pagina"))
      state.pagePlaced("pagina")
    }
    fun finger() = requireNotNull(state.beginVertical { push() })
    fun move(token: Int, distance: Float, steps: Int, fling: Boolean = false): Float {
      var previous = 0f
      var speed = 0f
      for (i in 1..steps) {
        val t = Math.PI * i / steps
        val part = if (fling) 1 - cos(t / 2) else (1 - cos(t)) / 2
        val next = distance * part.toFloat()
        speed = (next - previous) * 120
        state.moveVertical(token, next - previous, speed)
        tick()
        previous = next
      }
      return speed
    }
    fun settle() {
      var i = 0
      while (state.isMoving && i++ < 200) tick()
      assertFalse(state.isMoving)
    }
    override fun close() { scope.cancel(); state.releaseSources() }
  }

  private data class Sample(val p: Float, val shape: FluidZoomShape, val solid: Float, val plainFaces: ZoomFaces)

  @Test fun cresceSiSolidificaSoloAllaPosaEPuoRiprendereDuranteLaSolidificazione() {
    Run(true).use { r ->
      val token = r.finger()
      r.move(token, -481f, 60) // Il dito oltrepassa davvero il bordo, anche dopo gli arrotondamenti dei delta.
      repeat(20) { r.tick() }
      assertEquals(1f, r.frames.last().p, 0.000001f)
      assertEquals(0f, r.state.solidOpacity, 0f)
      r.state.releaseVertical(token, 0f)
      repeat(10) { r.tick() }
      val opacity = r.state.solidOpacity
      assertTrue("solidificazione in corso: " + opacity, opacity > 0f && opacity < 1f)
      val retake = requireNotNull(r.state.beginVertical(null))
      assertEquals(opacity, r.state.solidOpacity, 0f)
      r.move(retake, 160f, 18)
      assertEquals(0f, r.state.solidOpacity, 0f)
      r.state.releaseVertical(retake, -1200f)
      r.settle()
      assertEquals("pagina", r.state.upper)
    }
  }

  @Test fun toccoPiuRapidoDelloZoomELimiteDelPannello() {
    fun firstHalf(grow: Boolean) = Run(grow).use { r ->
      r.state.resizePage(276f, 460f)
      r.push()
      r.settle()
      assertEquals(Rect(0f, 0f, 276f, 460f), r.state.fullZoomShape().bounds)
      if (grow) {
        val landing = r.frames.filter { it.solid > 0f && it.solid < 1f }
        assertTrue(landing.size >= 8)
        assertTrue(landing.all { it.shape.bounds == r.state.fullZoomShape().bounds && it.p == 1f })
      }
      r.frames.indexOfFirst { it.p >= 0.5f }
    }
    assertTrue(firstHalf(true) < firstHalf(false))
  }

  @Test fun iQuattroAngoliSeguonoIlFoglioEIlDisplayDelPannello() {
    val panel = full.copy(corners = FluidPageCorners(0f, 64f, 52f, 0f))
    val asym = source.copy(corners = FluidPageCorners(24f, 10f, 7f, 19f))
    val sheet = ContinuousCornerShape.roundedEquivalent(FluidRadius.Sheet.value, full.bounds.size).x
    assertEquals(asym.corners, FluidZoom.pageCorners(sourceBox, asym, panel, 1f, 0f))
    assertEquals(FluidPageCorners(sheet, 58f, 46f, sheet),
      FluidZoom.pageCorners(Rect(6f, 4f, 314f, 476f), asym, panel, 1f, 0f))
    assertEquals(FluidPageCorners(sheet, 64f, 52f, sheet), FluidZoom.pageCorners(full.bounds, asym, panel, 1f, 0f))
    assertEquals(panel.corners, FluidZoom.pageCorners(full.bounds, asym, panel, 1f, 1f))
  }

  @Test fun gliAngoliSiChiudonoPerUltimiESiRiapronoAlPrimoDistacco() {
    val sheet = ContinuousCornerShape.roundedEquivalent(FluidRadius.Sheet.value, full.bounds.size).x
    for (grow in listOf(true, false)) Run(grow).use { r ->
      val token = r.finger()
      r.move(token, -481f, 60)
      repeat(8) { r.tick() }
      fun radius() = requireNotNull(r.state.zoomShape()).corners.topLeft
      assertEquals(sheet, radius(), 0.001f)
      assertTrue(r.frames.filter { it.p > 0.2f }.all { kotlin.math.abs(it.shape.corners.topLeft - sheet) < 0.001f })
      r.move(token, 72f, 8)
      assertEquals(sheet, radius(), 0.001f)
      r.move(token, -73f, 8)
      r.state.releaseVertical(token, 0f)
      r.settle()
      val flattening = r.frames.filter { it.p > 0.2f && it.shape.corners.topLeft in 0.001f..(sheet - 0.001f) }
      assertTrue("più fotogrammi per il solo bordo", flattening.size >= 5)
      assertTrue(flattening.all { it.shape.bounds == full.bounds && (!grow || it.solid > 0.7f) })
      assertTrue(flattening.zipWithNext().all { (a, b) -> a.shape.corners.topLeft > b.shape.corners.topLeft })
      val back = requireNotNull(r.state.beginVertical(null))
      assertEquals(0f, radius(), 0f)
      r.move(back, 4f, 1)
      assertTrue(radius() > 0f && radius() < sheet)
      r.move(back, 40f, 7)
      assertEquals(sheet, radius(), 0.001f)
      r.move(back, -45f, 7)
      assertEquals("il dito non riposa il bordo", sheet, radius(), 0.001f)
      r.state.releaseVertical(back, 0f, cancelled = true)
      r.settle()
    }
  }

  @Test fun tracceDelVetroDellaCopertinaEDellaRipresaA120Hz() {
    val output = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B5B_DIR"))
    fun scene(name: String, grow: Boolean, finger: Boolean, stop: Int? = null) {
      Run(grow).use { r ->
        r.sample()
        if (!finger) r.push() else {
          val token = r.finger()
          val distance = -480f * (stop ?: 84) / 100f
          val speed = r.move(token, distance, if (stop == null) 64 else 36)
          r.state.releaseVertical(token, speed)
          if (stop != null) {
            repeat(5) { r.tick() }
            val pose = r.state.zoomShape()
            val again = requireNotNull(r.state.beginVertical(null))
            assertEquals(pose, r.state.zoomShape())
            val reverse = r.move(again, -distance * 0.76f, 25, true)
            r.state.releaseVertical(again, reverse)
          }
        }
        r.settle()
        assertEquals(if (stop == null) "pagina" else "lista", r.state.upper)
        if (grow) assertTrue(r.frames.filter { it.p < 1f }.all { it.solid == 0f })
        if (output != null) {
          export(output, name, "light", grow, r.frames)
          if (stop == 25) export(output, "elemento-senza-condiviso-25", "light", false, r.frames, matched = false)
          if (grow) export(output, name + "-scuro", "dark", true, r.frames)
        }
      }
    }
    scene("elemento-zoom", false, false)
    scene("elemento-cresce", true, false)
    scene("elemento-cresce-col-dito", true, true)
    scene("elemento-sale-col-dito", false, true)
    for (stop in listOf(25, 50, 75)) scene("elemento-interrotto-" + stop, false, true, stop)
  }

  private fun export(output: File, name: String, theme: String, grow: Boolean, samples: List<Sample>, matched: Boolean = true) {
    val dark = theme == "dark"
    val paper = if (dark) "#1D2634" else "#F3F3F8"
    val ink = if (dark) "#F6F5FC" else "#182A3E"
    fun rect(name: String, box: Rect, radius: Float) = mapOf<String, Any?>(
      "name" to name, "x" to box.left, "y" to box.top, "w" to box.width, "h" to box.height, "radius" to radius)
    fun label(text: String, x: Float, y: Float, size: Int, color: String = ink) =
      mapOf("kind" to "text", "text" to text, "x" to x, "y" to y, "sizeDp" to size, "color" to color)
    fun face(name: String, box: Rect, alpha: Float, blur: Float, y: Float, items: List<Map<String, Any?>>) =
      mapOf("name" to name, "x" to 0, "y" to y, "w" to box.width, "h" to box.height,
        "alpha" to alpha, "blurSigmaDp" to glassContentSigmaPx(blur, GlassContentDefaults.BlurReference.value), "items" to items)
    val frames = samples.mapIndexed { i, s ->
      val p = s.p
      val b = s.shape.bounds
      val f = if (matched) FluidZoom.faces(p) else s.plainFaces
      val dims = rect("sagoma", b, s.shape.corners.topLeft)
      val elements = mutableListOf<Map<String, Any?>>()
      val material = liquidFromGlass(dims + ("role" to "button"), theme, SceneAsk()).also(::completeRestBody)
      if (grow || p == 0f) elements += material
      val solid = if (grow) s.solid else if (p == 0f) 0f else 1f
      val base = dims + mapOf("kind" to "shape", "corner" to "round", "color" to paper + "%02X".format((solid * 255).roundToInt()),
        "name" to "pagina", "_p" to p, "_solid" to s.solid,
        "_sigmaSfondo" to if (grow) FluidPageMotion.GrowBlurDp * FluidZoom.atmosphere(p) else 0f,
        "_veloSfondo" to if (grow) FluidPageMotion.GrowDim * FluidZoom.atmosphere(p) else 0f)
      val text = if (grow) "Località" else "Il brano"
      val sourceScale = b.width / sourceBox.width
      val origin = face("origine", b, f.sourceAlpha, f.sourceBlur, 0f,
        listOf(label(text, (if (grow) 22f else 53f) * sourceScale, 30f * sourceScale, (16f * sourceScale).roundToInt())))
      val title = if (grow) "Le mie località" else "In riproduzione"
      val commands = if (grow) listOf(label(title, 23f, 64f, 30), label("Bologna", 24f, 155f, 25),
        label("18°", 229f, 157f, 32), label("Parzialmente nuvoloso", 24f, 185f, 15)) else
        listOf(label(title, 30f, 359f, 25), label("In ascolto", 31f, 386f, 15),
          mapOf("kind" to "rect", "x" to 30, "y" to 405, "w" to 260, "h" to 3, "color" to ink))
      val placement = FluidZoom.content(s.shape, full.bounds)
      val shift = full.bounds.topLeft + placement.translation - b.topLeft
      val rise = f.riseDp * if (grow) 1f else placement.scale
      val chrome = face("comandi", full.bounds, f.pageAlpha, f.pageBlur * placement.scale, shift.y + rise, commands) +
        mapOf("x" to shift.x, "scale" to placement.scale)
      elements += base + ("content" to listOf(origin, chrome))
      if (!grow && matched) {
        val art = FluidZoom.sharedBounds(smallArt, largeArt, p, s.shape, source, full)
        elements += rect("copertina", art, art.width / 12f) + mapOf("kind" to "shape", "color" to "#E6A379",
          "content" to listOf(mapOf("name" to "motivo", "w" to art.width, "h" to art.height, "items" to listOf(
            mapOf("kind" to "circle", "x" to art.width * 0.72f, "y" to art.height * 0.30f, "r" to art.width * 0.18f, "color" to "#F9D5A0"),
            mapOf("kind" to "circle", "x" to art.width * 0.25f, "y" to art.height * 1.12f, "r" to art.width * 0.82f, "color" to "#466C76"),
            mapOf("kind" to "circle", "x" to art.width, "y" to art.height * 1.29f, "r" to art.width * 0.78f, "color" to "#264653")))))
      }
      GlassTraceFrame(glassTraceTimeMs(i, 120), elements)
    }
    val background = if (grow) mapOf("kind" to "gradient4",
      "tl" to if (dark) "#141E38" else "#B5CFDF", "tr" to if (dark) "#263451" else "#F6DFC5",
      "bl" to if (dark) "#334B61" else "#6D9EA9", "br" to if (dark) "#493652" else "#E5BCA9")
      else mapOf("kind" to "color", "color" to paper)
    val lists = if (grow) listOf("Previsioni", "Bologna", "Oggi", "Domani", "Sabato")
      else listOf("Libreria", "Ascolti recenti", "Album", "Artisti", "Brani")
    val page = lists.mapIndexed { index, title ->
      label(title, 24f, listOf(54f, 125f, 206f, 271f, 338f)[index], if (index == 0) 31 else 20)
    } + (1..4).map { i ->
      mapOf("kind" to "rect", "x" to 23, "y" to (62 + 66 * i), "w" to 274, "h" to 1, "color" to ink + "28")
    }
    val head = mapOf("background" to background, "page" to page,
      "probes" to listOf("_p", "_solid", "_sigmaSfondo", "_veloSfondo").map {
        mapOf("name" to it.drop(1), "kind" to "value", "path" to "pagina." + it) })
    GlassTrace(name, "f6b-5b", 1f, theme, 320f, 480f, frames, 120, head).write(File(output, name + "/trace.json"))
  }
}
