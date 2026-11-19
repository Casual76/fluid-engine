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

  /** La frazione di una molla critica da ferma dopo [seconds], con la risposta data. */
  private fun critical(seconds: Double, response: Double): Float {
    val x = 2 * Math.PI / response * seconds
    return (1 - (1 + x) * Math.exp(-x)).toFloat()
  }

  private val frameSeconds = 16_666_667L / 1e9

  @Test fun `andata e ritorno con lo zoom hanno la molla misurata su Apple`() {
    // L'apertura: dalla card verso lo schermo, sulla stessa molla del ritorno (F5B.3, scelta dell'orchestratore).
    val opening = host()
    opening.onStack(listOf("G", "D"))
    opening.pagePlaced("D")
    val fullWidth = opening.width
    repeat(10) { frame() }
    val grown = requireNotNull(opening.zoomShape()).bounds
    assertEquals(
      "il push segue la molla di Apple",
      critical(9 * frameSeconds, FluidPageMotion.ResponseZoomSeconds),
      (grown.width - card.width) / (fullWidth - card.width),
      0.02f,
    )
    opening.toRest()
    // L'Expand senza sorgente apre sulla molla delle pagine.
    val plainOpening = host(declared = false)
    plainOpening.onStack(listOf("G", "D"))
    plainOpening.pagePlaced("D")
    val exitOpen = FluidPageMotion.exitTravel(FluidPageTransition.Expand, plainOpening.width)
    repeat(10) { frame() }
    assertEquals(
      "senza sagoma il push e' quello delle pagine",
      critical(9 * frameSeconds, FluidPageMotion.ResponsePageSeconds),
      1f - plainOpening.travelPx / exitOpen,
      0.02f,
    )
    plainOpening.toRest()
  }

  @Test fun `il ritorno con lo zoom ha la molla misurata su Apple e finisce con la sagoma sulla card`() {
    val state = host()
    state.open()
    state.onStack(listOf("G"))
    val full = requireNotNull(state.zoomShape()).bounds
    var frames = 0
    var last: FluidZoomShape? = null
    while (state.isMoving) {
      last = state.zoomShape() ?: last
      // Il primo fotogramma fa partire la molla: dopo n fotogrammi ne sono passati n - 1.
      if (frames == 6 || frames == 15) {
        val shape = requireNotNull(state.zoomShape()).bounds
        val fraction = (full.width - shape.width) / (full.width - card.width)
        val expected = critical((frames - 1) * frameSeconds, FluidPageMotion.ResponseZoomSeconds)
        assertEquals("la sagoma segue la molla di Apple al fotogramma $frames", expected, fraction, 0.02f)
      }
      frame()
      frames++
    }
    // La molla critica di 0,355 s (Apple a tempo vero) su questa distanza (1000 px) arriva al dp verso 520 ms, ~32
    // fotogrammi; la sagoma si muove fino all'ultimo. Con la molla letta sul video a meta' velocita' (0,71 s) erano ~64.
    assertTrue("il ritorno dura come la molla di Apple ($frames fotogrammi)", frames in 26..40)
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

  @Test fun `la dissolvenza e di serie e segue la strada della sagoma al ritorno`() {
    // Spenta (solo le tracce di confronto): niente dissolvenza.
    val plain = host().also { it.zoomCrossfade = false }
    plain.open()
    plain.onStack(listOf("G"))
    while (plain.isMoving) {
      assertEquals(0f, plain.zoomCrossfadeAlpha(), 0f)
      frame()
    }
    // Di serie (scelte-b, 19 novembre 2026): zero finché la sagoma non ha fatto un terzo della strada, poi cresce fino a
    // 1, senza salti.
    val state = host()
    assertTrue("accesa di serie", state.zoomCrossfade)
    state.open()
    state.onStack(listOf("G"))
    val full = state.fullZoomShape()
    var last = 0f
    var sawZero = false
    var sawPartial = false
    while (state.isMoving) {
      val shape = requireNotNull(state.zoomShape())
      val alpha = state.zoomCrossfadeAlpha()
      assertEquals(FluidZoom.crossfade(shape, full, FluidZoomShape(card, FluidPageCorners(40f, 40f, 40f, 40f))), alpha, 0.02f)
      assertTrue("cresce: $alpha dopo $last", alpha >= last - 1e-4f)
      if (alpha == 0f) sawZero = true
      if (alpha > 0.05f && alpha < 0.95f) sawPartial = true
      last = alpha
      frame()
    }
    assertTrue("prima del terzo della strada la pagina e' intera", sawZero)
    assertTrue("e poi si dissolve", sawPartial)
    assertTrue("all'arrivo e' la sorgente: $last", last > 0.99f)
  }

  @Test fun `all'apertura la card sbiadisce dentro la sagoma come Apple`() {
    // Apple (10145 a tempo vero, corr-zoom): la cella sbiadisce da ≈23 a ≈115 ms, fra 0,07 e 0,60 della strada.
    val state = host()
    state.onStack(listOf("G", "D"))
    state.pagePlaced("D")
    val full = state.fullZoomShape()
    val cardShape = FluidZoomShape(card, FluidPageCorners(40f, 40f, 40f, 40f))
    var last = 1f
    var ms = 0f
    val seen = ArrayList<Pair<Float, Float>>()
    while (state.isMoving) {
      val alpha = state.zoomCrossfadeAlpha()
      assertEquals(FluidZoom.crossfade(requireNotNull(state.zoomShape()), full, cardShape), alpha, 0.02f)
      assertTrue("cala: $alpha dopo $last", alpha <= last + 1e-4f)
      seen += ms to alpha
      last = alpha
      frame()
      ms += 1000f / 60f
    }
    assertEquals("al primo fotogramma la sagoma e' la card", 1f, seen.first().second, 0f)
    assertEquals("a riposo la pagina e' intera", 0f, state.zoomCrossfadeAlpha(), 0f)
    val start = seen.last { it.second >= 0.999f }.first
    val end = seen.first { it.second <= 0.001f }.first
    assertTrue("comincia entro 70 ms come Apple (≈23; l'orologio del test parte un fotogramma dopo): $start", start <= 70f)
    assertTrue("finisce fra 100 e 170 ms (Apple ≈115): $end", end in 100f..170f)
  }

  /**
   * Il titolo grande della pagina sopra lo disegna la barra dell'host, sopra le pagine (corr-s4-zoom, S4 NO 4): sul Tab S9
   * restava nitido sulla card fino all'arrivo (≈480 ms), mentre il resto della pagina se n'era andato verso 200 ms. La barra
   * lo disegna con [FluidPageHostState.zoomContentAlpha], la stessa curva con cui la pagina sbiadisce dentro la sagoma
   * (`1 − zoomCrossfadeAlpha`, il `saveLayer` di `FluidPageHost.zoomCrossfade`): uguale al bit in ogni fotogramma, e
   * finita la dissolvenza la sagoma è la sola card, come in Apple (la cella sola verso 190 ms).
   */
  @Test fun `il titolo della pagina se ne va con la pagina nel ritorno col tasto`() {
    val state = host()
    state.open()
    assertEquals("a riposo il titolo si vede intero", 1f, state.zoomContentAlpha(PageRole.Upper), 0f)
    state.onStack(listOf("G"))
    // Il tempo della molla: il primo fotogramma la fa partire (dopo n fotogrammi ne sono passati n − 1).
    var frames = 0
    fun springMs() = (frames - 1).coerceAtLeast(0) * 1000f / 60f
    var at200: Float? = null
    var goneAt: Float? = null
    var last = 1f
    while (state.isMoving) {
      val title = state.zoomContentAlpha(PageRole.Upper)
      val ms = springMs()
      assertEquals("la curva della pagina, al bit, a $ms ms", 1f - state.zoomCrossfadeAlpha(), title, 0f)
      assertEquals("la pagina sotto non sbiadisce", 1f, state.zoomContentAlpha(PageRole.Lower), 0f)
      assertTrue("cala senza tornare: $title dopo $last a $ms ms", title <= last + 1e-4f)
      if (ms >= 200f && at200 == null) at200 = title
      if (title == 0f && goneAt == null) goneAt = ms
      last = title
      frame()
      frames++
    }
    // A 200 ms la sagoma ha fatto 0,87 della strada: il titolo è al 6% (in Apple la cella è sola verso 185–190 ms), e
    // sparisce del tutto quando la sagoma passa 0,9, a 220 ms. Prima della correzione restava pieno fino all'arrivo.
    assertTrue("verso 200 ms del titolo non resta quasi niente: ${at200}", requireNotNull(at200) <= 0.1f)
    val gone = requireNotNull(goneAt) { "il titolo non se ne va prima dell'arrivo" }
    assertTrue("il titolo sparisce del tutto entro 250 ms: $gone ms", gone in 200f..250f)
    assertTrue("e la card resta sola fino all'arrivo, che viene dopo: ${springMs()} ms", springMs() > gone + 150f)
  }

  @Test fun `il titolo della pagina segue la dissolvenza anche col dito e all'apertura`() {
    // Col dito: confermato, la sagoma vola nella card e il titolo se ne va con la pagina prima che arrivi.
    val finger = host()
    finger.open()
    finger.recordDown(Offset(10f, 1200f), IntSize(1080, 2400))
    val token = requireNotNull(finger.beginDrag(1))
    for (step in 1..10) {
      finger.dragTo(token, 40f * step)
      assertEquals(1f - finger.zoomCrossfadeAlpha(), finger.zoomContentAlpha(PageRole.Upper), 0f)
    }
    finger.release(token, 2000f)
    assertEquals(FluidPageMove.Back, finger.kind)
    var fingerGone = false
    while (finger.isMoving) {
      val title = finger.zoomContentAlpha(PageRole.Upper)
      assertEquals(1f - finger.zoomCrossfadeAlpha(), title, 0f)
      if (title == 0f) fingerGone = true
      frame()
    }
    assertTrue("col dito il titolo se ne va prima dell'arrivo", fingerGone)

    // All'apertura: la card nasce piena nella sagoma, e il titolo compare con la pagina, non sopra la card.
    val opening = host()
    opening.onStack(listOf("G", "D"))
    opening.pagePlaced("D")
    assertEquals("al primo fotogramma il titolo non c'è: la sagoma è la card", 0f, opening.zoomContentAlpha(PageRole.Upper), 0f)
    var last = 0f
    while (opening.isMoving) {
      val title = opening.zoomContentAlpha(PageRole.Upper)
      assertEquals(1f - opening.zoomCrossfadeAlpha(), title, 0f)
      assertTrue("cresce senza tornare: $title dopo $last", title >= last - 1e-4f)
      last = title
      frame()
    }
    assertEquals("a riposo intero", 1f, opening.zoomContentAlpha(PageRole.Upper), 0f)

    // Senza zoom (le animazioni ridotte, o l'Expand senza sorgente) il titolo non sbiadisce mai.
    for (state in listOf(host().also { it.reducedMotion = true }, host(declared = false))) {
      state.onStack(listOf("G", "D"))
      state.pagePlaced("D")
      state.toRest()
      state.onStack(listOf("G"))
      repeat(40) {
        assertEquals(1f, state.zoomContentAlpha(PageRole.Upper), 0f)
        frame()
      }
      state.toRest()
    }
  }

  /**
   * La sagoma dello zoom è nelle coordinate dell'host; il titolo sta nella riga dei controlli della barra, sotto la barra
   * di stato (corr-s4-zoom). Senza riportarla lì il ritaglio scendeva dell'altezza della barra di stato: sul Tab S9 il
   * titolo «Zoom» della griglia restava sopra la pagina che si apriva (S4, apertura, fotogrammi 6–8).
   */
  @Test fun `la sagoma si riporta nelle coordinate del titolo`() {
    val zoom = FluidZoomShape(card, FluidPageCorners(40f, 40f, 40f, 40f))
    val inRow = barTitleZoomClip(zoom, Offset(0f, 58f))
    assertEquals(Rect(100f, 842f, 500f, 1342f), inRow.bounds)
    assertEquals(zoom.corners, inRow.corners)
    assertEquals(Rect(70f, 900f, 470f, 1400f), barTitleZoomClip(zoom, Offset(30f, 0f)).bounds)
    assertTrue("con il titolo nell'angolo dell'host è la stessa sagoma", barTitleZoomClip(zoom, Offset.Zero) === zoom)
  }

  @Test fun `con le animazioni ridotte non c'e' zoom e quindi nemmeno dissolvenza`() {
    val state = host().also { it.reducedMotion = true }
    assertTrue("di serie accesa anche qui: la spegne il movimento ridotto", state.zoomCrossfade)
    state.onStack(listOf("G", "D"))
    state.pagePlaced("D")
    repeat(60) {
      assertNull(state.zoomShape())
      assertEquals(0f, state.zoomCrossfadeAlpha(), 0f)
      frame()
    }
    state.toRest()
    state.onStack(listOf("G"))
    repeat(60) {
      assertNull(state.zoomShape())
      assertEquals(0f, state.zoomCrossfadeAlpha(), 0f)
      assertFalse("nessuna sorgente consumata", source.isHidden)
      frame()
    }
  }
}
