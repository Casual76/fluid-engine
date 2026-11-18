package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Lo zoom fra pagine (F5B.3) sulla macchina vera, senza Compose: la sorgente la dà [FluidPageHostState.resolveSource].
 * Schermo 1080 × 2400 px a densità 1 (la soglia del rilascio è 180 px), card della griglia a (100, 900)–(500, 1400).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidPageZoomTest {
  private val clock = BroadcastFrameClock()
  private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
  private val card = Rect(100f, 900f, 500f, 1400f)
  private val source = FluidTransitionSource("card", FluidGlassNamespace())
  private var sourceThere = true
  private var time = 0L

  @After fun close() {
    scope.cancel()
    source.release()
  }

  private fun host(
    transition: FluidPageTransition = FluidPageTransition.Expand,
    declared: Boolean = true,
  ): FluidPageHostState = FluidPageHostState().also { state ->
    state.scope = scope
    state.width = 1080f
    state.height = 2400f
    state.transitionOf = { page -> if (page == "D") transition else FluidPageTransition.Slide }
    state.sourceOf = { page -> if (declared && page == "D") source else null }
    state.resolveSource = { if (sourceThere) FluidTransitionSourceGeometry(card, RoundedCornerShape(CornerSize(40f))) else null }
    state.onBack = { state.onStack(listOf("G")) }
    state.onStack(listOf("G"))
  }

  private fun frame() {
    clock.sendFrame(time)
    time += 16_666_667L
  }

  private fun FluidPageHostState.toRest(maxFrames: Int = 240) {
    repeat(maxFrames) {
      if (!isMoving) return
      frame()
    }
  }

  private fun FluidPageHostState.open() {
    onStack(listOf("G", "D"))
    pagePlaced("D")
    toRest()
  }

  /** Dove la pagina sopra, scalata e spostata come la disegna l'host, mette un suo punto. */
  private fun FluidPageHostState.mapUpper(p: Offset): Offset {
    val s = pageScale(PageRole.Upper)
    val c = Offset(width / 2f, height / 2f)
    return Offset(c.x + s * (p.x - c.x) + pageOffsetX(PageRole.Upper), c.y + s * (p.y - c.y) + pageOffsetY(PageRole.Upper))
  }

  private fun near(a: Rect, b: Rect, tolerance: Float): Boolean =
    abs(a.left - b.left) <= tolerance && abs(a.top - b.top) <= tolerance &&
      abs(a.right - b.right) <= tolerance && abs(a.bottom - b.bottom) <= tolerance

  @Test fun `il push parte dal riquadro e dalla forma della sorgente e arriva allo schermo`() {
    val state = host()
    state.onStack(listOf("G", "D"))
    val start = requireNotNull(state.zoomShape())
    assertEquals(card, start.bounds)
    assertEquals(FluidPageCorners(40f, 40f, 40f, 40f), start.corners)
    // La pagina sta intera nella sagoma: la sua cima sulla cima della card, il suo centro sul centro della card.
    val top = state.mapUpper(Offset(540f, 0f))
    assertEquals(card.top, top.y, 0.01f)
    assertEquals(card.center.x, top.x, 0.01f)
    assertEquals(maxOf(400f / 1080f, 500f / 2400f), state.pageScale(PageRole.Upper), 1e-5f)
    // Cresce senza tornare indietro, e a riposo la pagina e' intera e senza sagoma.
    state.pagePlaced("D")
    var width = start.bounds.width
    while (state.isMoving) {
      frame()
      val shape = state.zoomShape() ?: break
      assertTrue("la sagoma cresce", shape.bounds.width >= width - 0.01f)
      width = shape.bounds.width
    }
    assertFalse(state.isMoving)
    assertNull(state.zoomShape())
    assertEquals(1f, state.pageScale(PageRole.Upper), 0f)
    assertEquals(0f, state.pageOffsetY(PageRole.Upper), 0f)
  }

  @Test fun `la sorgente si nasconde quando la pagina compare e rinasce quando ci rientra`() {
    val state = host()
    val twin = FluidTransitionSource("card", FluidGlassNamespace())
    state.onStack(listOf("G", "D"))
    assertFalse("nel fotogramma del cambio di pila la pagina non c'e' ancora: la sorgente resta", source.isHidden)
    state.pagePlaced("D")
    assertTrue("la pagina compare sulla sagoma e la sorgente sparisce", source.isHidden)
    assertFalse("un'altra identita' non c'entra", twin.isHidden)
    state.toRest()
    assertTrue("aperta, la sorgente resta consumata", source.isHidden)

    state.onStack(listOf("G"))
    assertEquals(FluidPageMove.Pop, state.kind)
    var last: FluidZoomShape? = null
    while (state.isMoving) {
      last = state.zoomShape() ?: last
      assertTrue("finche' la pagina si vede, la sorgente e' nascosta", source.isHidden)
      frame()
    }
    assertTrue("l'ultimo fotogramma della pagina e' sulla sorgente", near(requireNotNull(last).bounds, card, 3f))
    assertFalse("a riposo la sorgente rinasce, nello stesso fotogramma in cui la pagina non si disegna piu'", source.isHidden)
    assertEquals(PageRole.Gone, state.roleOf("D"))
  }

  @Test fun `senza sorgente vale l Expand di sempre`() {
    sourceThere = false
    val zoomed = host()
    val today = host(declared = false)
    for (state in listOf(zoomed, today)) state.onStack(listOf("G", "D"))
    repeat(40) {
      assertNull(zoomed.zoomShape())
      assertEquals(today.pageScale(PageRole.Upper), zoomed.pageScale(PageRole.Upper), 0f)
      assertEquals(today.pageOffsetX(PageRole.Upper), zoomed.pageOffsetX(PageRole.Upper), 0f)
      assertEquals(0f, zoomed.pageOffsetY(PageRole.Upper), 0f)
      frame()
    }
    assertFalse(source.isHidden)
  }

  @Test fun `la sorgente sparita al ritorno ripiega sulla chiusura di Expand e rinasce`() {
    val state = host()
    state.open()
    assertTrue(source.isHidden)
    sourceThere = false
    state.onStack(listOf("G"))
    assertNull("nessuna sagoma: la chiusura di sempre", state.zoomShape())
    repeat(3) { frame() }
    assertTrue("rimpicciolisce come ShrinkSlide", state.pageScale(PageRole.Upper) < 1f)
    state.toRest()
    assertFalse(source.isHidden)
  }

  @Test fun `il gesto indietro segue il dito si interrompe e riprende senza salti`() {
    val state = host()
    state.open()
    state.recordDown(Offset(10f, 1200f), IntSize(1080, 2400))
    val token = requireNotNull(state.beginDrag(1))
    state.dragTo(token, 120f)
    val held = requireNotNull(state.zoomShape())
    val k = FluidZoom.followScale(120f, 1080f)
    // Il punto toccato e' sotto il dito.
    assertEquals(10f + 120f, held.bounds.left + 10f * k, 0.01f)
    assertTrue("si rimpicciolisce", held.bounds.width < 1080f)
    // Sotto la soglia: torna verso la pagina intera.
    state.release(token, 0f)
    repeat(3) { frame() }
    val returning = requireNotNull(state.zoomShape())
    assertTrue(returning.bounds.width > held.bounds.width)
    // Un dito la riprende a meta': nessun salto.
    state.recordDown(Offset(400f, 800f), IntSize(1080, 2400))
    assertTrue(state.canGrab())
    val again = requireNotNull(state.beginDrag(state.direction))
    val grabbed = requireNotNull(state.zoomShape())
    assertTrue("ripresa al volo dalla sagoma che si vede", near(returning.bounds, grabbed.bounds, 0.01f))
    val grabTravel = state.travelPx
    // Il dito torna indietro: la pagina si riapre.
    state.dragTo(again, grabTravel * 0.5f)
    assertTrue(requireNotNull(state.zoomShape()).bounds.width > grabbed.bounds.width)
    // Poi va oltre la soglia e lascia: torna nella sorgente, che rinasce a riposo.
    state.dragTo(again, 400f)
    val released = requireNotNull(state.zoomShape())
    state.release(again, 0f)
    assertEquals("conferma: la pila si accorcia", FluidPageMove.Back, state.kind)
    assertTrue("al rilascio nessun salto", near(released.bounds, requireNotNull(state.zoomShape()).bounds, 0.01f))
    var last: FluidZoomShape? = null
    while (state.isMoving) {
      last = state.zoomShape() ?: last
      frame()
    }
    assertTrue(near(requireNotNull(last).bounds, card, 3f))
    assertFalse(source.isHidden)
  }

  @Test fun `il ritorno con lo zoom ha la molla misurata su Apple e finisce con la sagoma sulla card`() {
    // La frazione di una molla critica da ferma dopo [seconds], con la risposta data.
    fun critical(seconds: Double, response: Double): Float {
      val x = 2 * Math.PI / response * seconds
      return (1 - (1 + x) * Math.exp(-x)).toFloat()
    }
    val frameSeconds = 16_666_667L / 1e9
    val state = host()
    state.open()
    state.onStack(listOf("G"))
    val full = requireNotNull(state.zoomShape()).bounds
    var frames = 0
    var last: FluidZoomShape? = null
    while (state.isMoving) {
      last = state.zoomShape() ?: last
      // Il primo fotogramma fa partire la molla: dopo n fotogrammi ne sono passati n - 1.
      if (frames == 10 || frames == 25) {
        val shape = requireNotNull(state.zoomShape()).bounds
        val fraction = (full.width - shape.width) / (full.width - card.width)
        val expected = critical((frames - 1) * frameSeconds, FluidPageMotion.ResponseZoomSeconds)
        assertEquals("la sagoma segue la molla di Apple al fotogramma $frames", expected, fraction, 0.02f)
      }
      frame()
      frames++
    }
    // La molla critica di 0,71 s su questa distanza arriva al dp verso 1,1 s: piu' lunga della molla delle pagine, ma la
    // sagoma si muove fino all'ultimo fotogramma.
    assertTrue("il ritorno dura come la molla di Apple ($frames fotogrammi)", frames in 50..80)
    assertTrue("e quando finisce la sagoma e' sulla card", near(requireNotNull(last).bounds, card, 3f))
    assertFalse(source.isHidden)

    // L'Expand senza sorgente resta sulla molla delle pagine.
    val plain = host(declared = false)
    plain.open()
    plain.onStack(listOf("G"))
    val exit = FluidPageMotion.exitTravel(FluidPageTransition.Expand, plain.width)
    repeat(10) { frame() }
    assertEquals(
      "senza sagoma la corsa e' quella delle pagine",
      critical(9 * frameSeconds, FluidPageMotion.ResponsePageSeconds),
      plain.travelPx / exit,
      0.02f,
    )
    plain.toRest()
  }

  @Test fun `un pop che interrompe il push riparte dalla sagoma che si vede`() {
    val state = host()
    state.onStack(listOf("G", "D"))
    state.pagePlaced("D")
    repeat(6) { frame() }
    val mid = requireNotNull(state.zoomShape())
    assertTrue(mid.bounds.width > card.width && mid.bounds.width < 1080f)
    state.onStack(listOf("G"))
    assertEquals(FluidPageMove.Pop, state.kind)
    assertTrue("nessun salto all'inversione", near(mid.bounds, requireNotNull(state.zoomShape()).bounds, 0.01f))
    var last: FluidZoomShape? = null
    while (state.isMoving) {
      last = state.zoomShape() ?: last
      frame()
    }
    assertTrue(near(requireNotNull(last).bounds, card, 3f))
    assertFalse(source.isHidden)
  }

  @Test fun `Slide e ShrinkSlide non cambiano anche con una sorgente dichiarata`() {
    for (transition in listOf(FluidPageTransition.Slide, FluidPageTransition.ShrinkSlide)) {
      val declared = host(transition)
      val plain = host(transition, declared = false)
      for (state in listOf(declared, plain)) {
        state.onStack(listOf("G", "D"))
        state.pagePlaced("D")
      }
      repeat(60) {
        assertNull(declared.zoomShape())
        assertEquals(plain.pageOffsetX(PageRole.Upper), declared.pageOffsetX(PageRole.Upper), 0f)
        assertEquals(plain.pageScale(PageRole.Upper), declared.pageScale(PageRole.Upper), 0f)
        assertEquals(plain.pageOffsetX(PageRole.Lower), declared.pageOffsetX(PageRole.Lower), 0f)
        frame()
      }
      // Anche il gesto indietro.
      for (state in listOf(declared, plain)) {
        state.toRest()
        state.recordDown(Offset(10f, 1200f), IntSize(1080, 2400))
      }
      val a = requireNotNull(declared.beginDrag(1))
      val b = requireNotNull(plain.beginDrag(1))
      declared.dragTo(a, 200f)
      plain.dragTo(b, 200f)
      assertEquals(plain.pageOffsetX(PageRole.Upper), declared.pageOffsetX(PageRole.Upper), 0f)
      assertEquals(plain.pageScale(PageRole.Upper), declared.pageScale(PageRole.Upper), 0f)
      assertFalse("nessuna sorgente consumata", source.isHidden)
      declared.release(a, 0f)
      plain.release(b, 0f)
      declared.toRest()
      plain.toRest()
    }
  }
}
