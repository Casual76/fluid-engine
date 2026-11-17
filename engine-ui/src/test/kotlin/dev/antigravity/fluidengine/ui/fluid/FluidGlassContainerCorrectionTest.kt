package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneNeck
import dev.antigravity.fluidengine.ui.fluidphysics.physicsNeckAt
import dev.antigravity.fluidengine.ui.fluidphysics.physicsPlaneNeckMs
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidNeck
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Il giro di correzione di F3 sul conducente e sulle funzioni pure del contenitore (`sessioni/f3-corr-nucleo/brief.md`):
 *  - l'**immediatezza a viaggio avviato** (R1 di C, bloccante): la scala delle animazioni che va a zero, `None` o il
 *    movimento ridotto mentre un vetro viaggia, con l'arrivo invariato, finiscono il viaggio in quel fotogramma, senza
 *    eccezioni (la sequenza di C: riposo, partenza, scala zero);
 *  - il **contenitore che cambia misura** (D R5): gli stessi pezzi spostati dal layout non viaggiano;
 *  - la **pressione al primo fotogramma del palco** (R2 di C, rilievo 1 di A): la lastra del tasto premuto parte premuta,
 *    oltre il suo riquadro nominale, e si rilascia con la pressione;
 *  - il **ponte che nasce da zero** e il contenuto che aspetta il suo vetro.
 */
class FluidGlassContainerCorrectionTest {

  private val canvas = Rect(0f, 0f, 600f, 200f)
  private val frameNanos = 16_666_667L

  private fun piece(key: String, x: Float, width: Float = 132f) =
    GlassPlanePiece(key, Rect(x, 60f, x + width, 192f), FluidCornerRadii.all(66f), id = key)

  private fun state(vararg pieces: GlassPlanePiece, bounds: Rect = canvas) =
    GlassPlaneState(pieces.toList(), 48f, bounds)

  private val a = piece("a", 40f)
  private val b = piece("b", 196f)

  /** Un conducente con un viaggio avviato da qualche fotogramma: «b» nasce da «a». */
  private fun running(motion: GlassPlaneMotion = GlassPlaneMotion()): Pair<FluidGlassPlaneDriver, Long> {
    val driver = FluidGlassPlaneDriver()
    assertEquals(FluidGlassPlaneDriver.Change.Rest, driver.step(0L, state(a), motion, 1f))
    assertEquals(FluidGlassPlaneDriver.Change.Started, driver.step(frameNanos, state(a, b), motion, 1f))
    var now = frameNanos
    repeat(3) {
      now += frameNanos
      assertEquals(FluidGlassPlaneDriver.Change.Moved, driver.step(now, state(a, b), motion, 1f))
    }
    return driver to now
  }

  @Test
  fun `la scala a zero a viaggio avviato, con l'arrivo invariato, arriva subito senza eccezioni`() {
    // La sequenza di C (R1): step(0, uno, motion, 1) → Rest; step(16 ms, due, motion, 1) → Started; step(33 ms, due,
    // motion, 0) → prima un IllegalArgumentException da `GlassPlaneJourney.frame(Infinity)`.
    val driver = FluidGlassPlaneDriver()
    val motion = GlassPlaneMotion()
    assertEquals(FluidGlassPlaneDriver.Change.Rest, driver.step(0L, state(a), motion, 1f))
    assertEquals(FluidGlassPlaneDriver.Change.Started, driver.step(16_666_667L, state(a, b), motion, 1f))
    assertEquals(FluidGlassPlaneDriver.Change.Finished, driver.step(33_333_334L, state(a, b), motion, 0f))
    assertNull(driver.journey)
    assertEquals(1f, driver.progress, 0f)
    assertEquals(setOf("a", "b"), driver.rest!!.pieces.map { it.key }.toSet())
    // E dopo, a scala zero, i cambi non viaggiano.
    assertEquals(FluidGlassPlaneDriver.Change.Rest, driver.step(50_000_001L, state(a), motion, 0f))
    // Una scala non finita (NaN) vale zero.
    val nan = running().first
    assertEquals(FluidGlassPlaneDriver.Change.Finished, nan.step(200_000_000L, state(a, b), GlassPlaneMotion(), Float.NaN))
  }

  @Test
  fun `None e il movimento ridotto a viaggio avviato finiscono il viaggio al posto misurato`() {
    // `FluidGlassAnimation.None` e il movimento ridotto arrivano al conducente come `motion` null (vedi `configure`).
    val (driver, now) = running()
    assertEquals(FluidGlassPlaneDriver.Change.Finished, driver.step(now + frameNanos, state(a, b), null, 1f))
    assertNull(driver.journey)
    assertNull(driver.frame)
    assertEquals(0f, driver.journeyLight, 0f)
    // E con l'arrivo cambiato nello stesso fotogramma: al posto nuovo, senza ripartire.
    val (other, later) = running()
    assertEquals(FluidGlassPlaneDriver.Change.Finished, other.step(later + frameNanos, state(a), null, 1f))
    assertEquals(listOf("a"), other.rest!!.pieces.map { it.key })
    assertNull(other.journey)
  }

  @Test
  fun `il contenitore che cambia misura con gli stessi pezzi non viaggia, un pezzo nuovo si`() {
    val driver = FluidGlassPlaneDriver()
    val motion = GlassPlaneMotion()
    driver.step(0L, state(a, b), motion)
    // Una rotazione: la riga è centrata in un contenitore più largo, i pezzi si spostano di 120 px.
    val wider = Rect(0f, 0f, 840f, 200f)
    val moved = state(a.copy(bounds = a.bounds.translate(120f, 0f)), b.copy(bounds = b.bounds.translate(120f, 0f)), bounds = wider)
    assertEquals(FluidGlassPlaneDriver.Change.Rest, driver.step(frameNanos, moved, motion, resized = true))
    assertNull("niente viaggio, niente luce", driver.journey)
    assertEquals(0f, driver.journeyLight, 0f)
    assertEquals(a.bounds.left + 120f, driver.rest!!.pieces.single { it.key == "a" }.bounds.left, 0f)
    // Lo stesso spostamento **senza** vincoli nuovi è un cambio dell'app: viaggia.
    val again = FluidGlassPlaneDriver()
    again.step(0L, state(a, b), motion)
    assertEquals(FluidGlassPlaneDriver.Change.Started, again.step(frameNanos, moved, motion, resized = false))
    // Vincoli nuovi e un pezzo nuovo nello stesso fotogramma: il pezzo nuovo viaggia (nasce dal vicino).
    val born = FluidGlassPlaneDriver()
    born.step(0L, state(a), motion)
    assertEquals(FluidGlassPlaneDriver.Change.Started, born.step(frameNanos, state(a, b, bounds = wider), motion, resized = true))
    // Un ridimensionamento continuo a viaggio avviato: il viaggio arriva lì, e da lì i vetri seguono il riquadro.
    val (live, now) = running()
    val shifted = state(a.copy(bounds = a.bounds.translate(30f, 0f)), b.copy(bounds = b.bounds.translate(30f, 0f)))
    assertEquals(FluidGlassPlaneDriver.Change.Finished, live.step(now + frameNanos, shifted, motion, resized = true))
    val further = state(a.copy(bounds = a.bounds.translate(60f, 0f)), b.copy(bounds = b.bounds.translate(60f, 0f)))
    assertEquals(FluidGlassPlaneDriver.Change.Rest, live.step(now + 2 * frameNanos, further, motion, resized = true))
    assertTrue(samePieces(state(a, b), further))
    assertFalse(samePieces(state(a, b), state(a)))
    assertFalse(samePieces(state(a, b), state(a, b.copy(id = "altro"))))
  }

  @Test
  fun `il palco parte dal tasto premuto, oltre il suo riquadro, e lo rilascia con la pressione`() {
    // R2 di C, rilievo 1 di A: il tasto premuto (×1,5 a pressione piena per un tondo, `ExpandRound`) tornava 1× al primo
    // fotogramma del palco. La lastra del palco ora è quella che il blocco del layer mostrava sul vetro fermo.
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    driver.step(frameNanos, state(a, b), GlassPlaneMotion())
    val frame = driver.frame!!
    val flights = frame.pieces.map { it.flightId }
    val w = a.bounds.width
    val h = a.bounds.height
    fun pressAt(progress: Float) =
      fluidPressTransform(FluidPressFeedback.ExpandRound, progress, Offset.Zero, w, h, 3f, reducedMotion = false)
    val pressed = pressAt(0.97f)
    assertTrue("il tasto premuto cresce: ${pressed.scaleX}", pressed.scaleX > 1.4f)
    fun first(press: FluidPressTransform?) = fluidGlassStagePlane(
      frame, flights, Offset.Zero, { 0f }, 48f, driver.neckNow(), FluidGlassSwell.None, 3f, 0f,
      pressed = { member -> press?.takeIf { member.flightId.sourceKey == "a" } },
    )
    val plain = first(null)
    val held = first(pressed)
    // Il riposo premuto: la lastra di «a» scalata attorno al suo centro, come la scala il layer della pressione.
    val rest = fluidGlassPressedSlab(FluidForm.Slab(a.bounds, a.radii), pressed)
    val donor = held.slabs.first { abs(it.frame.center.x - a.bounds.center.x) < 1f && it.frame.width > a.bounds.width }
    assertEquals(rest.frame, donor.frame)
    assertEquals(rest.cornerRadii, donor.cornerRadii)
    assertTrue("oltre il rettangolo nominale: ${donor.frame} contro ${a.bounds}", donor.frame.left < a.bounds.left - 20f &&
      donor.frame.right > a.bounds.right + 20f && donor.frame.top < a.bounds.top - 20f)
    assertTrue("senza pressione resta nominale", plain.slabs.none { it.frame.width > a.bounds.width + 0.5f })
    // E si rilascia con continuità: la molla della pressione (0,5, 300) da 0,97 non scende sotto 0,6 in un fotogramma.
    val spring = androidx.compose.animation.core.FloatSpringSpec(0.5f, 300f, 0.001f)
    var previous = donor.frame.width
    for (step in 1..30) {
      val p = spring.getValueFromNanos(step * 8_333_333L, 0.97f, 0f, 0f)
      val now = fluidGlassPressedSlab(FluidForm.Slab(a.bounds, a.radii), pressAt(p)).frame.width
      assertTrue("passo $step: ${previous - now} px", abs(previous - now) < 0.12f * w)
      previous = now
    }
    // Il contenuto si ritaglia sulla lastra premuta, e non si sposta due volte (la scala ce l'ha nel suo layer).
    val flying = frame.pieces.single { it.flightId.targetKey == "a" }
    val clip = fluidGlassContentFlightOf(flying, GlassPieceContent.Rest, GlassContentLook.Sharp.copy(alpha = 0.999f), a.bounds,
      FluidGlassSwell.None, pressed)!!
    assertEquals(rest.frame.translate(-a.bounds.left, -a.bounds.top), clip.clip)
    assertEquals(Offset.Zero, clip.offset)
  }

  @Test
  fun `una capsula premuta resta una capsula, e la pressione identita' non cambia niente`() {
    val capsule = FluidForm.Slab(Rect(0f, 0f, 200f, 80f), FluidCornerRadii.all(40f))
    val stretched = fluidGlassPressedSlab(capsule, FluidPressTransform(1.1f, 1.3f, 4f, -2f))
    assertEquals(Rect(-6f, -14f, 214f, 90f), stretched.frame)
    assertEquals(stretched.frame.minDimension / 2f, stretched.cornerRadii.topLeft, 1e-3f)
    assertTrue(capsule === fluidGlassPressedSlab(capsule, FluidPressTransform.Identity))
  }

  @Test
  fun `il contenuto aspetta il suo vetro un fotogramma, e solo se si e' spostato davvero`() {
    val place = Rect(10f, 20f, 210f, 120f)
    assertNull("fermo", fluidGlassContentHold(place, place))
    assertNull("entro mezzo pixel", fluidGlassContentHold(place, place.translate(0.3f, -0.4f)))
    // Il dock che cresce in alto (è ancorato in fondo): il contenuto di prima resta dov'era.
    assertEquals(Offset(0f, 40f), fluidGlassContentHold(place, Rect(10f, -20f, 210f, 120f)))
    // Cresce in basso: lo stesso angolo, si mostra il contenuto di prima senza spostarlo.
    assertEquals(Offset.Zero, fluidGlassContentHold(place, Rect(10f, 20f, 210f, 180f)))
    assertNull("nessun posto ancora", fluidGlassContentHold(Rect.Zero, place))
    // Il pezzo che non si vede ancora: nascosto, o nato e trasparente; non a riposo, non quando compare.
    assertFalse(fluidGlassContentHidden(null))
    assertTrue(fluidGlassContentHidden(FluidGlassContentFlight.Hidden))
    assertTrue(fluidGlassContentHidden(FluidGlassContentFlight(GlassContentLook.Sharp.copy(alpha = 0f))))
    assertFalse(fluidGlassContentHidden(FluidGlassContentFlight(GlassContentLook.Sharp.copy(alpha = 0.2f))))
  }

  @Test
  fun `il ponte fra due pezzi staccati nasce da zero e cresce con continuita'`() {
    // S4 di F3: nella confluenza il ponte era pieno in due fotogrammi, e le capsule accostate si univano al primo. La vita
    // del collo è la radice della coordinata: col quadrato nella salita il ponte cresce come la coordinata. Lo zero (il
    // ponte che nasce), l'uno, la strozzatura e la rottura restano dove sono; i viaggi di `morphTo` non cambiano.
    val neck = PhysicsPlaneNeck(-1f, physicsPlaneNeckMs(FluidMotion.PlaneReleaseMillis.toDouble()))
    var previousWaist = 0f
    var bornAt = Double.NaN
    var sixtyAt = Double.NaN
    var t = 0.0
    while (t <= 120.0) {
      val n = neck.at(t)
      val waist = if (n > 0f) sqrt(n) else 0f
      if (n > 0f && bornAt.isNaN()) bornAt = t
      if (waist >= 0.6f && sixtyAt.isNaN()) sixtyAt = t
      assertTrue("t=$t: la vita cresce di ${waist - previousWaist} in un fotogramma", waist - previousWaist < 0.26f)
      previousWaist = waist
      t += 1000.0 / 120.0
    }
    assertTrue("il ponte nasce a $bornAt ms", bornAt in 15.0..30.0)
    // Prima arrivava a sei decimi della vita piena un fotogramma dopo la nascita (0 → 0,42 → 0,66); ora in due o più.
    assertTrue("sei decimi a $sixtyAt ms, nato a $bornAt", sixtyAt - bornAt > 1.5 * 1000.0 / 120.0)
    // Lo zero della salita è lo stesso di prima; la strozzatura e la rottura anche.
    for (x in listOf(0.3f, 0.35f, GlassLiquidNeck, 0.45f, 0.6f)) {
      assertEquals(physicsNeckAt(x, -1f, GlassLiquidNeck), physicsNeckAt(x, -1f, GlassLiquidNeck, bridgeFromZero = true), 0f)
    }
    assertEquals(0f, physicsNeckAt(GlassLiquidNeck, -1f, GlassLiquidNeck, bridgeFromZero = true), 1e-4f)
    // Un viaggio interrotto riparte dal collo che c'era, anche nella salita.
    for (at in listOf(5.0, 12.0, 20.0, 40.0, 300.0, 420.0)) {
      val next = neck.retarget(at, 600.0)
      assertEquals("a $at ms", neck.at(at), next.at(0.0), 1e-6f)
    }
    // Una salita che parte da un collo positivo (un viaggio ripartito) non va al quadrato: niente salto.
    assertEquals(0.5f, physicsNeckAt(0f, 0.5f, GlassLiquidNeck, bridgeFromZero = true), 0f)
  }

  @Test
  fun `la vita della goccia stringe la sola parte positiva del collo`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    driver.step(frameNanos, state(a, b), GlassPlaneMotion())
    val journey = driver.journey!!
    val flights = journey.fields.flatMap { it.flights }
    fun shapeAt(ms: Double, neck: Float) = fluidGlassStagePlane(
      journey.frame(ms), flights, Offset.Zero, { 0f }, 48f, neck, FluidGlassSwell.None, 2f, 0f,
    )
    val waist = journey.frame(380.0).pieces.minOf { it.birthWaist }
    assertEquals(FluidMotion.PlaneReleaseWaist, waist, 1e-6f)
    assertEquals(0.6f * waist * waist, shapeAt(380.0, 0.6f).neck, 1e-6f)
    assertEquals("la rottura non si sposta", 0f, shapeAt(380.0, 0f).neck, 0f)
    assertEquals("i lobi staccati non cambiano", -0.4f, shapeAt(380.0, -0.4f).neck, 0f)
    assertEquals("prima della vita, il collo dell'orologio", 1f, shapeAt(100.0, 1f).neck, 0f)
    assertNotNull(journey.releaseDelays["b"])
  }

  @Test
  fun `quanto costa sul PC il lavoro puro del primo passo del palco`() {
    // C R5 (b): il viaggio si costruisce nel passo del fotogramma della partenza (`GlassPlaneJourney`, il palco dal
    // campionamento di `flownBounds` ogni 4 ms) e la prima forma del palco. Qui solo una misura, stampata: la JVM del PC
    // non è il thread UI del tablet, ma dice l'ordine di grandezza della parte pura, da confrontare con i 22–43 ms del
    // primo fotogramma di S4 (che comprendono composizione e disegno).
    val motion = GlassPlaneMotion()
    fun once(): Long {
      val began = System.nanoTime()
      val journey = dev.antigravity.fluidengine.ui.glass.field.GlassPlaneJourney(state(a), state(a, b), motion)
      val frame = journey.frame(0.0)
      for (field in journey.fields) {
        fluidGlassStagePlane(frame, field.flights, Offset.Zero, { 0f }, 48f, -1f, FluidGlassSwell.None, 3f, 0f)
      }
      return System.nanoTime() - began
    }
    repeat(200) { once() }
    val runs = List(200) { once() }.sorted()
    val median = runs[runs.size / 2] / 1_000_000.0
    val p90 = runs[runs.size * 9 / 10] / 1_000_000.0
    println("F3 giro di correzione, partenza: viaggio e prima forma del palco sul PC, mediana ${"%.3f".format(median)} ms, " +
      "p90 ${"%.3f".format(p90)} ms")
    assertTrue(median > 0.0)
  }
}
