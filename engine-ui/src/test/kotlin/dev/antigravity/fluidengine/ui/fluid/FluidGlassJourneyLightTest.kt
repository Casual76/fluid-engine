package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.GlassJourneyLightHold
import dev.antigravity.fluidengine.ui.glass.field.GlassJourneyLightSettle
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidJourney
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidJourneys
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La luce del viaggio del piano agganciata al palco (F3.10): il carattere di F3.9 nella forma di ogni campo, e la
 * **luce del dito** (la scelta di Alessio del 16 novembre: «a metà, più la luce del dito»).
 *
 * Che cosa prova: che il conducente dà al palco il livello della luce del carattere; che `journey=off` spegne luce **e**
 * rigonfiamento; che un tocco su un pezzo che apre un viaggio fa cominciare il suo campo dal bagliore della pressione
 * (il livello del vetro fermo, esatto: il passaggio riposo → palco non salta), che quel bagliore riempie il vetro, lo
 * segue in volo e si spegne col carattere; e che un cambio senza tocco parte dal livello del carattere. Non prova come
 * si vede: è la tavola dal banco (`sessioni/f3-10/per-alessio/`) e il tablet.
 */
class FluidGlassJourneyLightTest {

  private val canvas = Rect(0f, 0f, 600f, 200f)
  private val frameNanos = 16_666_667L

  private fun piece(key: String, x: Float, width: Float = 132f) =
    GlassPlanePiece(key, Rect(x, 60f, x + width, 192f), FluidCornerRadii.all(66f), id = key)

  private fun state(vararg pieces: GlassPlanePiece) = GlassPlaneState(pieces.toList(), 48f, canvas)

  private val a = piece("a", 40f)
  private val b = piece("b", 196f)

  /** Un conducente col carattere [character], a riposo su «a», con il viaggio verso «a» e «b» appena partito. */
  private fun started(character: GlassLiquidJourney = GlassLiquidJourneys.Shipped): FluidGlassPlaneDriver {
    val driver = FluidGlassPlaneDriver(character = { character })
    driver.step(0L, state(a), GlassPlaneMotion())
    assertEquals(FluidGlassPlaneDriver.Change.Started, driver.step(frameNanos, state(a, b), GlassPlaneMotion()))
    return driver
  }

  /** La forma del campo di adesso, come la costruisce `driveStage` ([swelling] falso: senza rigonfiamento). */
  private fun plane(driver: FluidGlassPlaneDriver, swelling: Boolean = true) = driver.journey!!.fields.single().let { field ->
    fluidGlassStagePlane(
      frame = driver.frame!!,
      flights = field.flights,
      origin = Offset.Zero,
      smoothing = { 0f },
      spacingPx = 48f,
      neck = driver.neckNow(field.union),
      swell = if (swelling) FluidGlassSwell(driver.journeyCharacter, driver.journeySwell, Float.MAX_VALUE, 3f)
        else FluidGlassSwell.None,
      density = 3f,
      movingMs = driver.movingMs.toFloat(),
      journeyLight = driver.journeyLight,
      touch = fluidGlassFingerTouch(driver.frame!!, field.flights, Offset.Zero, driver.finger, driver.fingerGlow, driver.fingerFill),
    )
  }

  /** Avanza il viaggio fino alla fase [phase] (o alla fine), un fotogramma alla volta. */
  private fun advanceTo(driver: FluidGlassPlaneDriver, phase: Float, now: Long = frameNanos): Long {
    var t = now
    var guard = 0
    while (driver.journey != null && driver.phase < phase && guard++ < 400) {
      t += frameNanos / 4
      driver.step(t, state(a, b), GlassPlaneMotion())
    }
    return t
  }

  @Test
  fun `il palco porta la luce del carattere, che parte da zero e si spegne prima del riposo`() {
    val driver = started()
    // Al primo fotogramma il carattere è a zero: il palco è il vetro di riposo.
    assertEquals(0f, plane(driver).journeyLight, 0f)
    advanceTo(driver, 0.3f)
    val lit = plane(driver).journeyLight
    assertTrue("a un terzo del viaggio la luce è piena: $lit", lit > 0.95f)
    assertEquals(driver.journeyCharacter.light(driver.phase, 0f, driver.journey!!.settleMs.toFloat()), lit, 1e-6f)
    advanceTo(driver, GlassJourneyLightSettle + 0.02f)
    if (driver.journey != null) assertEquals(0f, plane(driver).journeyLight, 1e-6f)
  }

  @Test
  fun `journey=off spegne la luce e anche il rigonfiamento`() {
    val driver = started(GlassLiquidJourneys.Off)
    var t = frameNanos
    var maxLight = 0f
    var maxSwell = 0f
    while (driver.journey != null) {
      val shape = plane(driver)
      maxLight = maxOf(maxLight, shape.journeyLight)
      maxSwell = maxOf(maxSwell, driver.journeySwell)
      // Le lastre sono quelle del piano, senza un pixel di rigonfiamento.
      val plain = plane(driver, swelling = false)
      assertEquals(plain.slabs.map { it.frame }, shape.slabs.map { it.frame })
      t += frameNanos
      driver.step(t, state(a, b), GlassPlaneMotion())
    }
    assertEquals(0f, maxLight, 0f)
    assertEquals(0f, maxSwell, 0f)
    // Il rigonfiamento spento restituisce la stessa lastra, la stessa istanza.
    val swell = FluidGlassSwell(GlassLiquidJourneys.Off, 1f, Float.MAX_VALUE, 3f)
    val slab = dev.antigravity.fluidengine.ui.fluidphysics.FluidForm.Slab(a.bounds, a.radii)
    assertSame(slab, swell.of(slab))
    // E il dito non c'è: il tocco non accende niente.
    driver.step(t + frameNanos, state(a), GlassPlaneMotion())
    driver.touched("a", 0.9f, 0.5f, 0.5f, shown = true)
    assertNull(driver.finger)
  }

  @Test
  fun `un tocco su un pezzo che apre un viaggio fa partire il suo campo dal bagliore della pressione`() {
    val driver = started()
    // Il vetro fermo mostrava la pressione a 0,8 (il dito si sta alzando): il campo comincia da lì, esatto.
    driver.touched("a", 0.8f, 0.25f, 0.5f, shown = true)
    val first = plane(driver)
    assertNotNull("il campo del vetro toccato ha la luce del dito", first.touch)
    val touch = first.touch!!
    assertEquals(0.8f, touch.amount, 1e-6f)
    assertEquals("al primo fotogramma il bagliore è la campana del vetro fermo", 0f, touch.fill, 1e-6f)
    // Il dito sta dove stava sul pezzo toccato, nelle coordinate del campo.
    assertEquals(a.bounds.left + 0.25f * a.bounds.width, touch.x, 0.01f)
    assertEquals(a.bounds.top + 0.5f * a.bounds.height, touch.y, 0.01f)
    // Poi riempie il vetro (a metà viaggio), accompagna il volo, e si spegne con la coda della luce.
    advanceTo(driver, 0.3f)
    val full = plane(driver).touch!!
    assertTrue("cresce verso il bagliore pieno: ${full.amount}", full.amount > 0.95f)
    advanceTo(driver, GlassJourneyLightHold + 0.01f)
    assertTrue("a metà viaggio ha riempito il vetro: ${plane(driver).touch!!.fill}", plane(driver).touch!!.fill > 0.99f)
    advanceTo(driver, GlassJourneyLightSettle + 0.01f)
    if (driver.journey != null) assertNull("spento con la luce del carattere", plane(driver).touch)
  }

  @Test
  fun `un cambio senza tocco parte dal livello del carattere`() {
    val driver = started()
    val first = plane(driver)
    assertNull("nessun dito, nessun bagliore", first.touch)
    assertEquals(driver.journeyCharacter.light(0f), first.journeyLight, 0f)
    assertEquals(0f, first.journeyLight, 0f)
    // Una pressione sotto la soglia non è un tocco (il contenitore non la passa): un pezzo non toccato non ha dito.
    assertNull(fluidGlassFingerTouch(driver.frame!!, driver.journey!!.fields.single().flights, Offset.Zero, null, 1f, 1f))
  }

  @Test
  fun `un pezzo di un grappolo, che a riposo non mostra il bagliore, lo fa salire senza scalino`() {
    val driver = started()
    driver.touched("a", 0.8f, 0.5f, 0.5f, shown = false)
    assertEquals(0f, plane(driver).let { it.touch?.amount ?: 0f }, 0f)
    advanceTo(driver, 0.3f)
    assertTrue(plane(driver).touch!!.amount > 0.95f)
  }

  @Test
  fun `un viaggio che riparte si porta dietro il bagliore e il riempimento`() {
    val driver = started()
    driver.touched("a", 0.6f, 0.5f, 0.5f, shown = true)
    var t = advanceTo(driver, 0.25f)
    val before = plane(driver).touch!!
    // Lo stato torna indietro a metà: il viaggio riparte da dove si trova, e il bagliore anche.
    t += frameNanos / 4
    assertEquals(FluidGlassPlaneDriver.Change.Retargeted, driver.step(t, state(a), GlassPlaneMotion()))
    val after = fluidGlassFingerTouch(
      driver.frame!!, driver.journey!!.fields.flatMap { it.flights }, Offset.Zero, driver.finger, driver.fingerGlow, driver.fingerFill,
    )!!
    assertTrue("bagliore ${before.amount} -> ${after.amount}", abs(after.amount - before.amount) < 0.08f)
    assertTrue("riempimento ${before.fill} -> ${after.fill}", abs(after.fill - before.fill) < 0.08f)
  }

  @Test
  fun `le curve del dito partono dal bagliore del vetro fermo e si spengono con la luce`() {
    val j = GlassLiquidJourneys.Shipped
    assertEquals(1f, j.finger, 0f)
    assertEquals(0.7f, j.fingerGlow(0f, 0.7f, 300f), 1e-6f)
    assertEquals(1f, j.fingerGlow(0.4f, 0.7f, 300f), 1e-6f)
    assertEquals(0f, j.fingerGlow(GlassJourneyLightSettle, 0.7f, 300f), 1e-6f)
    assertEquals(0f, j.fingerGlow(1f, 0.7f, 300f), 0f)
    // Nessun salto fra un fotogramma e l'altro a 120 Hz.
    var previous = j.fingerGlow(0f, 0.7f, 300f)
    for (i in 1..36) {
      val now = j.fingerGlow(i / 36f, 0.7f, 300f)
      assertTrue("passo $i: $previous -> $now", abs(now - previous) < 0.15f)
      previous = now
    }
    assertEquals(0f, j.fingerFill(0f), 0f)
    assertEquals(1f, j.fingerFill(GlassJourneyLightHold), 1e-6f)
    assertEquals(0.4f, j.fingerFill(0f, 0.4f), 1e-6f)
    // Spento, senza un'entrata, non c'è.
    assertEquals(0f, GlassLiquidJourneys.Off.fingerGlow(0.3f, 0f, 300f), 0f)
    assertTrue(GlassLiquidJourneys.Off.isOff)
  }
}
