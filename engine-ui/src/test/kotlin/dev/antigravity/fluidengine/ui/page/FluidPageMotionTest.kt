package dev.antigravity.fluidengine.ui.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole delle pagine, senza Compose: dove sta la pagina, quando un rilascio conferma, cosa e'
 * successo alla pila. Ognuna e' una promessa che l'host fa al dito, e qui si controlla da sola.
 */
class FluidPageMotionTest {

  private val width = 720f
  private val density = 2f

  // --- La corsa in-app -------------------------------------------------------------------------

  @Test
  fun `in-app la corsa e' dito meno contatto, verso l'uscita`() {
    assertEquals(48f, FluidPageMotion.inAppTravel(fingerX = 58f, downX = 10f, direction = 1), 0f)
    // In RTL la pagina esce a sinistra: il dito che va a sinistra e' corsa positiva.
    assertEquals(48f, FluidPageMotion.inAppTravel(fingerX = 652f, downX = 700f, direction = -1), 0f)
  }

  @Test
  fun `in-app la corsa non va mai sotto zero`() {
    assertEquals(0f, FluidPageMotion.inAppTravel(fingerX = 5f, downX = 30f, direction = 1), 0f)
  }

  @Test
  fun `in-app un annullo ripreso parte dalla corsa che aveva`() {
    assertEquals(130f, FluidPageMotion.inAppTravel(60f, 50f, 1, startTravel = 120f), 0f)
    assertEquals(100f, FluidPageMotion.inAppTravel(30f, 50f, 1, startTravel = 120f), 0f)
  }

  // --- Il gesto di sistema ---------------------------------------------------------------------

  @Test
  fun `di sistema l'ancora e' il bordo se l'host lo tocca`() {
    val anchor = FluidPageMotion.systemAnchor(
      firstTouchX = 24f, edge = FluidSwipeEdge.Left, hostLeft = 0f, hostRight = width, windowWidth = width,
    )
    assertEquals(0f, anchor, 0f)
    // La pagina salta sotto il dito al primo evento e da li' lo segue.
    assertEquals(24f, FluidPageMotion.systemTravel(24f, anchor, 1, width), 0f)
    assertEquals(200f, FluidPageMotion.systemTravel(200f, anchor, 1, width), 0f)
  }

  @Test
  fun `di sistema l'ancora e' il primo evento se l'host non tocca il bordo`() {
    // Un pannello di dettaglio da 400 a 1200 su una finestra larga 1200: il bordo sinistro e' dentro.
    val anchor = FluidPageMotion.systemAnchor(
      firstTouchX = 30f, edge = FluidSwipeEdge.Left, hostLeft = 400f, hostRight = 1200f, windowWidth = 1200f,
    )
    assertEquals(30f, anchor, 0f)
    assertEquals(0f, FluidPageMotion.systemTravel(30f, anchor, 1, 800f), 0f)
    assertEquals(70f, FluidPageMotion.systemTravel(100f, anchor, 1, 800f), 0f)
  }

  @Test
  fun `di sistema dal bordo destro la pagina esce a sinistra`() {
    assertEquals(-1, FluidPageMotion.systemDirection(FluidSwipeEdge.Right, layoutDirection = 1))
    assertEquals(1, FluidPageMotion.systemDirection(FluidSwipeEdge.Left, layoutDirection = -1))
    assertEquals(-1, FluidPageMotion.systemDirection(FluidSwipeEdge.None, layoutDirection = -1))
    val anchor = FluidPageMotion.systemAnchor(
      firstTouchX = 700f, edge = FluidSwipeEdge.Right, hostLeft = 0f, hostRight = width, windowWidth = width,
    )
    assertEquals(width, anchor, 0f)
    assertEquals(120f, FluidPageMotion.systemTravel(600f, anchor, -1, width), 0f)
  }

  @Test
  fun `di sistema la corsa resta fra zero e l'uscita`() {
    assertEquals(0f, FluidPageMotion.systemTravel(-20f, 0f, 1, width), 0f)
    assertEquals(width, FluidPageMotion.systemTravel(2000f, 0f, 1, width), 0f)
  }

  @Test
  fun `un annullo di sistema si deduce dal dito fermo e dal progresso in calo`() {
    assertTrue(FluidPageMotion.inferSystemCancel(300f, 300.2f, 0.4f, 0.35f))
    // Il dito si muove: e' ancora li', anche se il progresso scende (sta tornando verso il bordo).
    assertFalse(FluidPageMotion.inferSystemCancel(300f, 280f, 0.4f, 0.35f))
    // Il dito fermo con il progresso fermo o in salita non e' un rilascio.
    assertFalse(FluidPageMotion.inferSystemCancel(300f, 300f, 0.4f, 0.4f))
    assertFalse(FluidPageMotion.inferSystemCancel(300f, 300f, 0.4f, 0.5f))
  }

  // --- Il rilascio -----------------------------------------------------------------------------

  @Test
  fun `un rilascio lento conferma oltre il 35 percento`() {
    assertFalse(FluidPageMotion.backRelease(0.30f * width, 0f, width, density))
    assertTrue(FluidPageMotion.backRelease(0.35f * width, 0f, width, density))
    assertTrue(FluidPageMotion.backRelease(0.40f * width, 0f, width, density))
  }

  @Test
  fun `sui tablet la distanza ha un tetto in dp`() {
    val tablet = 1600f
    // 0,35 W sarebbero 560 px; il tetto e' 180 dp = 360 px.
    assertFalse(FluidPageMotion.backRelease(350f, 0f, tablet, density))
    assertTrue(FluidPageMotion.backRelease(360f, 0f, tablet, density))
  }

  @Test
  fun `una spinta decide da sola in entrambi i versi`() {
    val fling = FluidPageMotion.FlingDpPerSecond * density
    // Corta ma lanciata: esce.
    assertTrue(FluidPageMotion.backRelease(0.10f * width, fling, width, density))
    assertFalse(FluidPageMotion.backRelease(0.10f * width, fling - 1f, width, density))
    // Oltre meta' ma spinta indietro: resta.
    assertFalse(FluidPageMotion.backRelease(0.5f * width, -fling, width, density))
    assertTrue(FluidPageMotion.backRelease(0.5f * width, -fling + 1f, width, density))
  }

  @Test
  fun `a larghezza zero il rilascio conferma senza dividere per zero`() {
    assertTrue(FluidPageMotion.backRelease(0f, 0f, 0f, density))
    assertEquals(1f, FluidPageMotion.exitFraction(FluidPageTransition.Slide, 0f, 0f), 0f)
    assertEquals(0f, FluidPageMotion.exitTravel(FluidPageTransition.ShrinkSlide, 0f), 0f)
  }

  // --- La pagina sopra -------------------------------------------------------------------------

  @Test
  fun `Slide e' il dito e basta`() {
    val frame = FluidPageMotion.pageFrame(FluidPageTransition.Slide, 123f, width)
    assertEquals(123f, frame.translation, 0f)
    assertEquals(1f, frame.scale, 0f)
    assertEquals(width, FluidPageMotion.exitTravel(FluidPageTransition.Slide, width), 0f)
  }

  @Test
  fun `ShrinkSlide e' monotona e continua a 0,30 W`() {
    var lastX = -1f
    var lastScale = 2f
    val exit = FluidPageMotion.exitTravel(FluidPageTransition.ShrinkSlide, width)
    var travel = 0f
    while (travel <= exit) {
      val frame = FluidPageMotion.pageFrame(FluidPageTransition.ShrinkSlide, travel, width)
      assertTrue("x monotona a $travel", frame.translation >= lastX)
      assertTrue("scala monotona a $travel", frame.scale <= lastScale)
      lastX = frame.translation
      lastScale = frame.scale
      travel += 1f
    }
    val shrinkEnd = 0.30f * width
    val before = FluidPageMotion.pageFrame(FluidPageTransition.ShrinkSlide, shrinkEnd - 0.01f, width)
    val after = FluidPageMotion.pageFrame(FluidPageTransition.ShrinkSlide, shrinkEnd + 0.01f, width)
    assertEquals(before.translation, after.translation, 0.05f)
    assertEquals(before.scale, after.scale, 0.001f)
    assertEquals(0.9f, after.scale, 0.001f)
    // Fino a 0,30 W rimpicciolisce e basta.
    assertEquals(0f, before.translation, 0f)
  }

  @Test
  fun `ShrinkSlide a 1,25 W ha il bordo sul bordo dello schermo`() {
    val exit = FluidPageMotion.exitTravel(FluidPageTransition.ShrinkSlide, width)
    assertEquals(1.25f * width, exit, 0.001f)
    val frame = FluidPageMotion.pageFrame(FluidPageTransition.ShrinkSlide, exit, width)
    // Il perno e' al centro: il bordo sinistro della pagina scalata e' x + W·(1 − s)/2.
    val left = frame.translation + width * (1f - frame.scale) / 2f
    assertEquals(width, left, 0.5f)
  }

  @Test
  fun `Expand si apre in scala sul posto e si chiude come ShrinkSlide`() {
    val exit = FluidPageMotion.exitTravel(FluidPageTransition.Expand, width)
    val start = FluidPageMotion.pageFrame(FluidPageTransition.Expand, exit, width, opening = true)
    assertEquals(0f, start.translation, 0f)
    assertEquals(FluidPageMotion.ExpandInitialScale, start.scale, 0.0001f)
    val end = FluidPageMotion.pageFrame(FluidPageTransition.Expand, 0f, width, opening = true)
    assertEquals(1f, end.scale, 0f)
    val closing = FluidPageMotion.pageFrame(FluidPageTransition.Expand, 400f, width)
    assertEquals(FluidPageMotion.pageFrame(FluidPageTransition.ShrinkSlide, 400f, width), closing)
  }

  // --- La pagina sotto -------------------------------------------------------------------------

  @Test
  fun `la pagina sotto arretra di un terzo e torna a zero all'uscita`() {
    assertEquals(-width / 3f, FluidPageMotion.belowParallax(0f, width), 0.001f)
    assertEquals(0f, FluidPageMotion.belowParallax(1f, width), 0.001f)
  }

  @Test
  fun `fra la pagina sotto e quella sopra non c'e' mai un buco`() {
    for (transition in listOf(FluidPageTransition.Slide, FluidPageTransition.ShrinkSlide)) {
      val exit = FluidPageMotion.exitTravel(transition, width)
      var travel = 0f
      while (travel <= exit) {
        val e = FluidPageMotion.exitFraction(transition, travel, width)
        val belowRight = FluidPageMotion.belowParallax(e, width) + width
        val frame = FluidPageMotion.pageFrame(transition, travel, width)
        val upperLeft = frame.translation + width * (1f - frame.scale) / 2f
        assertTrue("$transition a $travel: sotto finisce a $belowRight, sopra comincia a $upperLeft", belowRight >= upperLeft - 0.01f)
        travel += 2f
      }
    }
  }

  // --- Chi e' davanti --------------------------------------------------------------------------

  @Test
  fun `il davanti passa di mano a meta' uscita`() {
    assertTrue(FluidPageMotion.pageIsFront(0f, isUpper = true))
    assertTrue(FluidPageMotion.pageIsFront(0.49f, isUpper = true))
    assertFalse(FluidPageMotion.pageIsFront(0.5f, isUpper = true))
    assertFalse(FluidPageMotion.pageIsFront(0.49f, isUpper = false))
    assertTrue(FluidPageMotion.pageIsFront(0.5f, isUpper = false))
  }

  // --- Gli angoli ------------------------------------------------------------------------------

  @Test
  fun `gli angoli del telefono solo dove l'host tocca un angolo dello schermo`() {
    val display = FluidPageCorners(40f, 41f, 42f, 43f)
    val full = FluidPageMotion.pageCardRadii(0f, 0f, 1080f, 2400f, 1080f, 2400f, display)
    assertEquals(display, full)
    // Pannello di dettaglio a destra: angoli tondi a destra, retti a sinistra.
    val detail = FluidPageMotion.pageCardRadii(400f, 0f, 1080f, 2400f, 1080f, 2400f, display)
    assertEquals(FluidPageCorners(0f, 41f, 42f, 0f), detail)
    // Sotto una barra opaca: niente angoli in alto.
    val belowBar = FluidPageMotion.pageCardRadii(0f, 120f, 1080f, 2400f, 1080f, 2400f, display)
    assertEquals(FluidPageCorners(0f, 0f, 42f, 43f), belowBar)
    // Senza raggi del display (API < 31): retti.
    assertTrue(FluidPageMotion.pageCardRadii(0f, 0f, 1080f, 2400f, 1080f, 2400f, FluidPageCorners.Zero).isZero)
  }

  // --- La pila ---------------------------------------------------------------------------------

  @Test
  fun `la pila dice push, pop, sostituzione, salto e niente`() {
    val push = FluidPageMotion.classifyStackChange(listOf("a"), listOf("a", "b"))
    assertEquals(FluidStackChangeKind.Push, push.kind)
    assertEquals("a", push.from)
    assertEquals("b", push.to)

    val pop = FluidPageMotion.classifyStackChange(listOf("a", "b"), listOf("a"))
    assertEquals(FluidStackChangeKind.Pop, pop.kind)
    assertEquals(0, pop.skipped)

    val jump = FluidPageMotion.classifyStackChange(listOf("a", "b", "c", "d"), listOf("a"))
    assertEquals(FluidStackChangeKind.Pop, jump.kind)
    assertEquals("d", jump.from)
    assertEquals("a", jump.to)
    assertEquals(2, jump.skipped)

    val replace = FluidPageMotion.classifyStackChange(listOf("a", "b"), listOf("a", "c"))
    assertEquals(FluidStackChangeKind.Replace, replace.kind)

    assertEquals(FluidStackChangeKind.None, FluidPageMotion.classifyStackChange(listOf("a", "b"), listOf("x", "b")).kind)
    assertEquals(FluidStackChangeKind.None, FluidPageMotion.classifyStackChange(emptyList(), listOf("a")).kind)
  }

  @Test
  fun `due schede fanno il passo laterale anche quando la pila dice push`() {
    val order = listOf("home", "cerca", "profilo")
    val peer: (String, String) -> Int = { from, to ->
      val a = order.indexOf(from)
      val b = order.indexOf(to)
      if (a < 0 || b < 0) 0 else b - a
    }
    val forward = FluidPageMotion.classifyStackChange(listOf("home"), listOf("home", "profilo"), peer)
    assertEquals(FluidStackChangeKind.Peer, forward.kind)
    assertEquals(1, forward.direction)
    val backward = FluidPageMotion.classifyStackChange(listOf("home", "profilo"), listOf("home", "cerca"), peer)
    assertEquals(FluidStackChangeKind.Peer, backward.kind)
    assertEquals(-1, backward.direction)
    // Una pagina che non e' una scheda: regole delle pagine.
    assertEquals(FluidStackChangeKind.Push, FluidPageMotion.classifyStackChange(listOf("home"), listOf("home", "dettaglio"), peer).kind)
  }

  // --- Il passo fra schede ---------------------------------------------------------------------

  @Test
  fun `il passo fra schede e' il 13 percento e la vecchia sfuma in 150 ms`() {
    val (arriving0, departing0, alpha0) = FluidPageMotion.peerFrame(0f, 0f, width, 1)
    assertEquals(0.13f * width, arriving0, 0.01f)
    assertEquals(0f, departing0, 0f)
    assertEquals(1f, alpha0, 0f)
    val (arriving1, departing1, alpha1) = FluidPageMotion.peerFrame(1f, 1f, width, 1)
    assertEquals(0f, arriving1, 0f)
    assertEquals(-0.13f * width, departing1, 0.01f)
    assertEquals(0f, alpha1, 0f)
    // A 150 ms su 280 la vecchia e' gia' sparita.
    val (_, _, alphaAtFade) = FluidPageMotion.peerFrame(150f / 280f, 0.8f, width, 1)
    assertEquals(0f, alphaAtFade, 0.001f)
  }

  // --- La barra ancorata ------------------------------------------------------------------------

  @Test
  fun `il morph dei tasti sta fermo ai due capi e cresce in mezzo`() {
    assertEquals(0f, FluidPageMotion.barMorph(0f), 0f)
    assertEquals(0f, FluidPageMotion.barMorph(0.10f), 0f)
    assertEquals(0.5f, FluidPageMotion.barMorph(0.5f), 0.0001f)
    assertEquals(1f, FluidPageMotion.barMorph(0.90f), 0f)
    assertEquals(1f, FluidPageMotion.barMorph(1f), 0f)
    var last = 0f
    var e = 0f
    while (e <= 1f) {
      val m = FluidPageMotion.barMorph(e)
      assertTrue(m >= last)
      last = m
      e += 0.01f
    }
  }

  @Test
  fun `le icone delle due pagine non si leggono mai insieme`() {
    var m = 0f
    while (m <= 1f) {
      val upper = FluidPageMotion.barIconAlpha(m, ofLower = false)
      val lower = FluidPageMotion.barIconAlpha(m, ofLower = true)
      // Al massimo una delle due oltre meta': chi arriva aspetta che chi esce sia quasi sparito.
      assertTrue("a m = $m: $upper e $lower", upper < 0.5f || lower < 0.5f)
      m += 0.01f
    }
    assertEquals(1f, FluidPageMotion.barIconAlpha(0f, ofLower = false), 0f)
    assertEquals(0f, FluidPageMotion.barIconAlpha(0f, ofLower = true), 0f)
    assertEquals(0f, FluidPageMotion.barIconAlpha(1f, ofLower = false), 0f)
    assertEquals(1f, FluidPageMotion.barIconAlpha(1f, ofLower = true), 0f)
  }

  @Test
  fun `il titolo che esce sfuma nella prima meta' e quello che arriva dal 40 percento`() {
    val shift = 32f
    // Back (la pagina sopra se ne va): il suo titolo e' quello che esce.
    val (outAtStart, outShiftAtStart) = FluidPageMotion.barTitleFrame(0f, isUpper = true, forward = false, shiftPx = shift, direction = 1)
    assertEquals(1f, outAtStart, 0f)
    assertEquals(0f, outShiftAtStart, 0f)
    assertEquals(0f, FluidPageMotion.barTitleFrame(0.5f, true, false, shift, 1).first, 0f)
    assertEquals(0f, FluidPageMotion.barTitleFrame(0.4f, false, false, shift, 1).first, 0f)
    assertEquals(1f, FluidPageMotion.barTitleFrame(0.9f, false, false, shift, 1).first, 0f)
    // Quello che arriva viene dal lato da cui la pagina sopra se ne va... al contrario: da sinistra.
    assertEquals(-shift, FluidPageMotion.barTitleFrame(0f, false, false, shift, 1).second, 0f)
    assertEquals(0f, FluidPageMotion.barTitleFrame(1f, false, false, shift, 1).second, 0f)
    // Push (la pagina sopra arriva): all'inizio (e = 1) si vede il titolo di sotto, alla fine quello di sopra.
    assertEquals(1f, FluidPageMotion.barTitleFrame(1f, false, true, shift, 1).first, 0f)
    assertEquals(0f, FluidPageMotion.barTitleFrame(1f, true, true, shift, 1).first, 0f)
    assertEquals(1f, FluidPageMotion.barTitleFrame(0f, true, true, shift, 1).first, 0f)
    // In RTL i passi si specchiano.
    assertEquals(shift * 0.5f, FluidPageMotion.barTitleFrame(0.5f, true, false, shift, 1).second, 0.0001f)
    assertEquals(-shift * 0.5f, FluidPageMotion.barTitleFrame(0.5f, true, false, shift, -1).second, 0.0001f)
  }

  private fun lens(left: Float) = androidx.compose.ui.geometry.Rect(left, 0f, left + 44f, 44f)

  @Test
  fun `i pezzi della barra si accoppiano dall'esterno e chi resta solo nasce da un seme`() {
    val bar = 720f
    val back = lens(12f)
    // Una pagina con indietro e due azioni, l'altra con indietro e tre.
    val from = listOf(back, lens(600f), lens(650f))
    val to = listOf(back, lens(550f), lens(600f), lens(650f))
    val pairs = FluidPageMotion.barPieceJourney(from, to, bar)!!
    assertEquals(4, pairs.size)
    // Il tasto indietro resta se stesso.
    assertTrue(pairs.contains(back to back))
    // La prima azione da destra con la prima da destra.
    assertTrue(pairs.contains(lens(650f) to lens(650f)))
    assertTrue(pairs.contains(lens(600f) to lens(600f)))
    // Quella in piu' nasce da un pixel sul proprio centro, sul posto.
    val born = pairs.single { it.second == lens(550f) }.first
    assertEquals(lens(550f).center, born.center)
    assertEquals(1f, born.width, 0.0001f)
  }

  @Test
  fun `il tasto indietro di una pagina sola si spegne sul posto`() {
    val back = lens(12f)
    val pairs = FluidPageMotion.barPieceJourney(listOf(back), listOf(lens(650f)), 720f)!!
    assertEquals(2, pairs.size)
    val gone = pairs.single { it.first == back }.second
    assertEquals(back.center, gone.center)
    assertEquals(1f, gone.width, 0.0001f)
  }

  @Test
  fun `oltre sei pezzi o senza pezzi niente viaggio`() {
    val many = (0 until 7).map { lens(380f + it * 46f) }
    assertEquals(null, FluidPageMotion.barPieceJourney(many, many, 720f))
    assertEquals(null, FluidPageMotion.barPieceJourney(emptyList(), emptyList(), 720f))
  }

  @Test
  fun `le molle delle pagine sono criticamente smorzate`() {
    assertEquals(1f, FluidPageMotion.page().dampingRatio, 0f)
    assertEquals(1f, FluidPageMotion.pageReturn().dampingRatio, 0f)
    assertTrue(FluidPageMotion.page().stiffness < FluidPageMotion.pageReturn().stiffness)
  }
}
