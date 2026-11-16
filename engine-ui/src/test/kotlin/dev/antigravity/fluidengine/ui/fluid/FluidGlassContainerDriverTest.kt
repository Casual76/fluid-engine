package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsPlaneNeck
import dev.antigravity.fluidengine.ui.fluidphysics.physicsPlaneNeckMs
import dev.antigravity.fluidengine.ui.glass.field.GlassJourneySwellPeak
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidJourneys
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneChange
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneJourney
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneTransition
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneUnionChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Il conducente del contenitore (4.0, piano «iOS in Android» F3.2): chi porta il vetro, fotogramma per fotogramma.
 *
 * È la prova senza dispositivo del **passaggio di consegne** (F3-b) per la parte che si può provare: la geometria. Il
 * palco parte dalla geometria che il riposo mostrava (nessun pezzo salta al primo fotogramma), il riposo d'arrivo
 * riprende la geometria che il palco aveva all'ultimo fotogramma (entro mezzo pixel), e un cambio a metà viaggio riparte
 * da dove i vetri si trovano (F3-f). Che riposo e palco disegnino quella geometria allo stesso modo è la parità di F1B
 * (stessa ricetta, stesso corpo): resta da guardare in S3.
 */
class FluidGlassContainerDriverTest {

  private val canvas = Rect(0f, 0f, 600f, 200f)
  private val frameNanos = 16_666_667L

  private fun piece(key: String, x: Float, width: Float = 132f, id: Any? = key, transition: GlassPlaneTransition =
    GlassPlaneTransition.MatchedGeometry) =
    GlassPlanePiece(key, Rect(x, 60f, x + width, 192f), FluidCornerRadii.all(66f), id = id, transition = transition)

  private fun state(vararg pieces: GlassPlanePiece, spacing: Float = 48f) = GlassPlaneState(pieces.toList(), spacing, canvas)

  private fun assertRect(expected: Rect, actual: Rect, epsilon: Float = 0.001f, message: String = "") {
    assertEquals("$message left", expected.left, actual.left, epsilon)
    assertEquals("$message top", expected.top, actual.top, epsilon)
    assertEquals("$message right", expected.right, actual.right, epsilon)
    assertEquals("$message bottom", expected.bottom, actual.bottom, epsilon)
  }

  private val a = piece("a", 40f)
  private val b = piece("b", 196f)
  private val far = piece("far", 440f, transition = GlassPlaneTransition.Materialize)

  @Test
  fun `la prima misura e' il riposo, senza viaggio`() {
    val driver = FluidGlassPlaneDriver()
    assertEquals(FluidGlassPlaneDriver.Change.Rest, driver.step(0L, state(a), GlassPlaneMotion()))
    assertNull(driver.journey)
    assertEquals(1, driver.rest!!.pieces.size)
  }

  @Test
  fun `meno di mezzo pixel non e' un cambio, e un contenitore che si sposta non cambia niente`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    val nudged = a.copy(bounds = a.bounds.translate(0.3f, -0.4f))
    assertEquals(FluidGlassPlaneDriver.Change.Nothing, driver.step(frameNanos, state(nudged), GlassPlaneMotion()))
    assertTrue(planesAlike(state(a), state(nudged)))
    assertFalse(planesAlike(state(a), state(a.copy(bounds = a.bounds.translate(1f, 0f)))))
    assertFalse(planesAlike(state(a), state(a.copy(id = "altro"))))
    // L'ordine del registro non è un cambio: chi è stato tolto e rimesso torna in fondo.
    assertTrue(planesAlike(state(a, b), state(b, a)))
    assertFalse(planesAlike(state(a, b), state(a, b.copy(key = "c"))))
  }

  @Test
  fun `il palco parte dalla geometria del riposo, e chi nasce parte sovrapposto al vicino`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    assertEquals(FluidGlassPlaneDriver.Change.Started, driver.step(frameNanos, state(a, b), GlassPlaneMotion()))
    val frame = driver.frame!!
    val matched = frame.pieces.single { it.flightId.targetKey == "a" }
    val born = frame.pieces.single { it.flightId.targetKey == "b" }
    assertEquals(GlassPlaneChange.Matched, matched.change)
    assertEquals(GlassPlaneChange.BornFromNeighbour, born.change)
    assertEquals("a", born.bornFrom)
    // Il primo fotogramma del palco è il riposo di prima: «a» è dov'era, «b» coincide con «a».
    assertRect(a.bounds, matched.bounds, message = "a al primo fotogramma")
    assertRect(a.bounds, born.bounds, message = "b al primo fotogramma")
    assertEquals(0f, driver.progress, 0f)
    // Il riposo è già l'arrivo, montato e spento finché il palco porta il vetro.
    assertEquals(setOf("a", "b"), driver.rest!!.pieces.map { it.key }.toSet())
    assertEquals(listOf("a"), driver.origin!!.pieces.map { it.key })
  }

  @Test
  fun `il riposo d'arrivo riprende la geometria dell'ultimo fotogramma del palco`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    driver.step(frameNanos, state(a, b), GlassPlaneMotion())
    var now = frameNanos
    var last = driver.frame!!
    var frames = 0
    while (true) {
      now += frameNanos
      val change = driver.step(now, state(a, b), GlassPlaneMotion())
      if (change == FluidGlassPlaneDriver.Change.Finished) break
      assertEquals(FluidGlassPlaneDriver.Change.Moved, change)
      assertTrue("il progresso dichiarato non arriva all'uno in viaggio", driver.progress < 1f)
      last = driver.frame!!
      frames++
      assertTrue("il viaggio deve finire", frames < 200)
    }
    // Contesto di 300 ms a 60 Hz: una ventina di fotogrammi.
    assertTrue("fotogrammi $frames", frames in 12..24)
    assertNull(driver.journey)
    assertEquals(1f, driver.progress, 0f)
    for (target in listOf(a, b)) {
      val flight = last.pieces.single { it.flightId.targetKey == target.key }
      assertRect(target.bounds, flight.bounds, FluidGlassPlaneTolerancePx, "${target.key} all'ultimo fotogramma")
    }
  }

  @Test
  fun `un cambio a meta' viaggio riparte da dove i vetri si trovano`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    driver.step(frameNanos, state(a, b), GlassPlaneMotion())
    var now = frameNanos
    repeat(6) {
      now += frameNanos
      driver.step(now, state(a, b), GlassPlaneMotion())
    }
    val before = driver.frame!!.pieces.single { it.flightId.targetKey == "b" }.bounds
    val revision = driver.journeyRevision
    now += frameNanos
    // «b» se ne va prima di essere arrivato: il viaggio riparte dalla geometria di adesso.
    assertNotNull(driver.journey)
    assertEquals(FluidGlassPlaneDriver.Change.Retargeted, driver.step(now, state(a), GlassPlaneMotion()))
    assertEquals(revision + 1, driver.journeyRevision)
    val restart = driver.frame!!.pieces.single { it.flightId.sourceKey == "b" }
    // Fra l'ultimo fotogramma mostrato e la ripartenza passa un fotogramma di moto, non un salto.
    val step = maxOf(
      abs(restart.bounds.left - before.left), abs(restart.bounds.right - before.right),
      abs(restart.bounds.top - before.top), abs(restart.bounds.bottom - before.bottom),
    )
    assertTrue("salto alla ripartenza di $step px", step < 40f)
    assertTrue(restart.change == GlassPlaneChange.MergeIntoNeighbour || restart.change == GlassPlaneChange.Dematerialize)
  }

  @Test
  fun `senza animazione, o con la scala di sistema a zero, il vetro va al posto nuovo`() {
    val none = FluidGlassPlaneDriver()
    none.step(0L, state(a), null)
    assertEquals(FluidGlassPlaneDriver.Change.Rest, none.step(frameNanos, state(a, b), null))
    assertNull(none.journey)
    val zero = FluidGlassPlaneDriver()
    zero.step(0L, state(a), GlassPlaneMotion())
    assertEquals(FluidGlassPlaneDriver.Change.Rest, zero.step(frameNanos, state(a, b), GlassPlaneMotion(), 0f))
  }

  @Test
  fun `la scala delle animazioni di sistema allunga il viaggio`() {
    fun framesAt(scale: Float): Int {
      val driver = FluidGlassPlaneDriver()
      driver.step(0L, state(a), GlassPlaneMotion())
      driver.step(frameNanos, state(a, b), GlassPlaneMotion(), scale)
      var now = frameNanos
      var frames = 0
      while (driver.step(now + frameNanos, state(a, b), GlassPlaneMotion(), scale) != FluidGlassPlaneDriver.Change.Finished) {
        now += frameNanos
        frames++
      }
      return frames
    }
    val normal = framesAt(1f)
    val slow = framesAt(2f)
    assertTrue("$normal contro $slow", slow in normal * 2 - 2..normal * 2 + 2)
  }

  @Test
  fun `una spec dell'app porta il suo tempo`() {
    val driver = FluidGlassPlaneDriver()
    val motion = GlassPlaneMotion(spec = tween(100))
    driver.step(0L, state(a), motion)
    driver.step(frameNanos, state(a, b), motion)
    assertEquals(100.0, driver.journey!!.durationMs, 1.0)
  }

  @Test
  fun `chi compare per lente e' un campo a se', con la sua comparsa esatta`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    driver.step(frameNanos, state(a, far), GlassPlaneMotion())
    driver.step(frameNanos * 8, state(a, far), GlassPlaneMotion())
    val frame = driver.frame!!
    val appearing = frame.pieces.single { it.flightId.targetKey == "far" }
    assertEquals(GlassPlaneChange.Materialize, appearing.change)
    // F3.2c: nessun campo mescola chi compare con chi viaggia.
    assertTrue(driver.journey!!.fields.all { field -> field.flights.size == 1 || field.flights.none { it.targetKey == "far" } })
    val alone = fluidGlassFieldSlabs(frame, listOf(appearing.flightId), Offset(400f, 20f)) { 0.6f }
    assertEquals(appearing.materialize, alone.materialize, 0f)
    assertTrue(alone.materialize > 0f && alone.materialize < 1f)
    assertEquals(1, alone.slabs.size)
    assertRect(appearing.bounds.translate(-400f, -20f), alone.slabs.single().frame, message = "nelle coordinate del nodo")
    assertEquals(0.6f, alone.slabs.single().smoothing, 0f)
    // Al primo fotogramma chi compare c'è già, a `materialize` zero: è la ricetta della comparsa a non mostrarlo.
    val start = FluidGlassPlaneDriver()
    start.step(0L, state(a), GlassPlaneMotion())
    start.step(frameNanos, state(a, far), GlassPlaneMotion())
    val t0 = start.frame!!
    val first = fluidGlassFieldSlabs(t0, listOf(t0.pieces.single { it.flightId.targetKey == "far" }.flightId), Offset.Zero) { 0f }
    assertEquals(0f, first.materialize, 0f)
    assertEquals(1, first.slabs.size)
    // Un campo di vetri che viaggiano è pieno.
    val moving = fluidGlassFieldSlabs(t0, listOf(t0.pieces.single { it.flightId.targetKey == "a" }.flightId), Offset.Zero) { 0f }
    assertEquals(1f, moving.materialize, 0f)
  }

  @Test
  fun `il collo parte da fermo, riparte da dove si trova e a riposo non c'e'`() {
    val motion = GlassPlaneMotion()
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), motion)
    assertNull(driver.neck)
    assertEquals(-1f, driver.neckNow(), 0f)
    driver.step(frameNanos, state(a, b), motion)
    assertEquals("alla partenza il collo è quello da fermi", -1f, driver.neckNow(), 0f)
    assertEquals("l'orologio dura il doppio del riposo a vista", 2.0 * driver.journey!!.settleMs, driver.neck!!.durationMs, 1e-9)
    var now = frameNanos
    repeat(5) {
      now += frameNanos
      driver.step(now, state(a, b), motion)
    }
    assertTrue("in viaggio il collo c'è", driver.neckNow() > -1f)
    val old = driver.neck!!
    val moving = driver.movingMs
    now += frameNanos
    assertEquals(FluidGlassPlaneDriver.Change.Retargeted, driver.step(now, state(a), motion))
    val at = (now - frameNanos) / 1_000_000.0
    assertEquals("riparte dal collo di adesso", old.at(at), driver.neckNow(), 1e-5f)
    assertTrue("il vetro in viaggio non si riaccende alla ripartenza", driver.movingMs > moving)
    while (driver.step(now + frameNanos, state(a), motion) != FluidGlassPlaneDriver.Change.Finished) now += frameNanos
    assertNull(driver.neck)
    assertEquals(-1f, driver.neckNow(), 0f)
  }

  @Test
  fun `le unioni stanno in testa, nell'ordine di prima, con i loro indici`() {
    val ns = Any()
    val p1 = piece("p1", 0f)
    val u1 = piece("u1", 140f).copy(unionId = ns)
    val p2 = piece("p2", 280f)
    val u2 = piece("u2", 420f).copy(unionId = ns)
    val ordered = listOf(p1, u1, p2, u2).unionsFirst { it }
    assertEquals(listOf("u1", "u2", "p1", "p2"), ordered.map { it.key })
    assertEquals(listOf(0, 0, -1, -1), fluidGlassUnionIndices(ordered)!!.toList())
    assertNull(fluidGlassUnionIndices(listOf(p1, p2)))
    val plain = listOf(p1, p2)
    assertSame("senza unioni la stessa lista", plain, plain.unionsFirst { it })
  }

  // ---- F3.8a ---------------------------------------------------------------------------------------------------

  @Test
  fun `i primi passi di un viaggio hanno un tetto, un buco di fotogrammi alla partenza e' tempo perso e non moto saltato`() {
    val motion = GlassPlaneMotion()
    val capMs = FluidMotion.PlaneFirstStepMillis.toDouble()
    val driver = FluidGlassPlaneDriver(firstStepCapNanos = FluidMotion.PlaneFirstStepMillis * 1_000_000L)
    driver.step(0L, state(a), motion)
    driver.step(frameNanos, state(a, b), motion)
    assertEquals(0.0, driver.elapsedMs, 0.0)
    // Il pannello sale da 30 a 120 Hz: 58 ms senza fotogrammi fra il primo e il secondo del viaggio (S3, framestats).
    var now = frameNanos + 58_300_000L
    driver.step(now, state(a, b), motion)
    assertEquals(capMs, driver.elapsedMs, 1e-6)
    val early = driver.frame!!.pieces.single { it.flightId.targetKey == "b" }.bounds.left - a.bounds.left
    assertTrue("al secondo fotogramma il vetro ha appena cominciato: $early px", early < 0.1f * (b.bounds.left - a.bounds.left))
    repeat(2) {
      now += 8_333_333L
      driver.step(now, state(a, b), motion)
    }
    assertEquals(capMs + 2 * 8.333333, driver.elapsedMs, 1e-3)
    // Dopo i primi passi l'orologio è quello a muro: un fotogramma lungo a metà viaggio non rallenta il viaggio.
    now += 50_000_000L
    driver.step(now, state(a, b), motion)
    assertEquals(capMs + 2 * 8.333333 + 50.0, driver.elapsedMs, 1e-3)
    // Una ripartenza a metà non ha tetto: il pannello è già a regime.
    now += 40_000_000L
    assertEquals(FluidGlassPlaneDriver.Change.Retargeted, driver.step(now, state(a), motion))
    now += 40_000_000L
    driver.step(now, state(a), motion)
    assertEquals(40.0, driver.elapsedMs, 1e-3)
    // Senza tetto (chi conta il tempo a mano) il buco è moto saltato, com'era.
    val plain = FluidGlassPlaneDriver()
    plain.step(0L, state(a), motion)
    plain.step(frameNanos, state(a, b), motion)
    plain.step(frameNanos + 58_300_000L, state(a, b), motion)
    assertEquals(58.3, plain.elapsedMs, 1e-6)
  }

  @Test
  fun `la fase del viaggio va da zero a uno sul riposo a vista, e una ripartenza la riporta a zero`() {
    val motion = GlassPlaneMotion()
    val driver = FluidGlassPlaneDriver()
    assertEquals(1f, driver.phase, 0f)
    driver.step(0L, state(a), motion)
    driver.step(frameNanos, state(a, b), motion)
    assertEquals(0f, driver.phase, 0f)
    val settle = driver.journey!!.settleMs
    driver.step(frameNanos + (settle / 2.0 * 1_000_000.0).toLong(), state(a, b), motion)
    assertEquals(0.5f, driver.phase, 0.001f)
    driver.step(frameNanos + (settle / 2.0 * 1_000_000.0).toLong() + frameNanos, state(a), motion)
    assertEquals(0f, driver.phase, 0f)
  }

  @Test
  fun `il carattere del viaggio segue la fase, e una ripartenza riparte dalla luce e dal rigonfiamento che ha`() {
    // L'aggancio di F3.9: la luce e il rigonfiamento del vetro lungo il viaggio, col livello d'entrata nel driver.
    val motion = GlassPlaneMotion()
    val character = GlassLiquidJourneys.Apple
    val driver = FluidGlassPlaneDriver(character = { character })
    assertEquals("a riposo niente carattere", 0f, driver.journeyLight, 0f)
    assertEquals(0f, driver.journeySwell, 0f)
    driver.step(0L, state(a), motion)
    driver.step(frameNanos, state(a, b), motion)
    assertEquals("alla partenza la ricetta è quella di riposo", 0f, driver.journeyLight, 0f)
    assertEquals(0f, driver.journeySwell, 0f)
    val settle = driver.journey!!.settleMs
    // Al colmo del rigonfiamento la luce è piena.
    val peak = frameNanos + (settle * GlassJourneySwellPeak * 1_000_000.0).toLong()
    driver.step(peak, state(a, b), motion)
    assertEquals(character.swell(driver.phase), driver.journeySwell, 0f)
    assertEquals(1f, driver.journeySwell, 0.01f)
    assertEquals(1f, driver.journeyLight, 0.01f)
    // Un cambio di stato un fotogramma dopo: il viaggio riparte dalla fase zero, la luce e il rigonfiamento no.
    driver.step(peak + frameNanos, state(a), motion)
    assertEquals(0f, driver.phase, 0f)
    assertTrue("rigonfiamento d'entrata ${driver.journeySwellEntry}", driver.journeySwellEntry > 0.9f)
    assertTrue("luce d'entrata ${driver.journeyLightEntry}", driver.journeyLightEntry > 0.9f)
    assertEquals(driver.journeySwellEntry, driver.journeySwell, 1e-6f)
    assertEquals(driver.journeyLightEntry, driver.journeyLight, 1e-6f)
    // Da lì scendono senza scalini (a 60 Hz la coda della luce scende al più di un quarto per fotogramma), e
    // all'arrivo non resta niente: geometria e ricetta di riposo.
    var t = peak + frameNanos
    var light = driver.journeyLight
    var swell = driver.journeySwell
    while (driver.journey != null) {
      t += frameNanos
      driver.step(t, state(a), motion)
      assertTrue("la luce non fa scalini", abs(driver.journeyLight - light) < 0.3f)
      assertTrue("il rigonfiamento non fa scalini", abs(driver.journeySwell - swell) < 0.3f)
      light = driver.journeyLight
      swell = driver.journeySwell
      assertTrue(t < 5_000_000_000L)
    }
    assertEquals(0f, driver.journeyLight, 0f)
    assertEquals(0f, driver.journeySwell, 0f)
    assertEquals(0f, driver.journeyLightEntry, 0f)
    // Senza carattere (la manopola `journey=off`) niente si accende e niente si gonfia.
    val plain = FluidGlassPlaneDriver(character = { GlassLiquidJourneys.Off })
    plain.step(0L, state(a), motion)
    plain.step(frameNanos, state(a, b), motion)
    plain.step(peak, state(a, b), motion)
    assertEquals(0f, plain.journeySwell * plain.journeyCharacter.swell, 0f)
    assertTrue(plain.journeyCharacter.isOff)
  }

  @Test
  fun `un'unione che nasce e' un collo che cresce e resta, una che si scioglie si strozza e si rompe`() {
    val union = Any()
    val motion = GlassPlaneMotion()
    val apart = state(a, b)
    val united = state(a.copy(unionId = union), b.copy(unionId = union))
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, apart, motion)
    // «Unisci»: nessun pezzo si muove, ma è un viaggio.
    assertEquals(FluidGlassPlaneDriver.Change.Started, driver.step(frameNanos, united, motion))
    assertEquals(GlassPlaneUnionChange.Forming, driver.journey!!.fields.single().union)
    assertEquals("parte dai pezzi separati", -1f, driver.neckNow(GlassPlaneUnionChange.Forming), 0f)
    // Durante la nascita i due pezzi non sono ancora un'unione piena: il loro collo è quello che cresce.
    assertNull(fluidGlassStableUnionIndices(driver.frame!!.pieces))
    var now = frameNanos
    var last = -1f
    var frames = 0
    var bornAt = -1
    while (driver.step(now + frameNanos, united, motion) != FluidGlassPlaneDriver.Change.Finished) {
      now += frameNanos
      frames++
      val neck = driver.neckNow(GlassPlaneUnionChange.Forming)
      assertTrue("il collo cresce e non torna indietro: $last -> $neck", neck >= last - 1e-6f)
      if (bornAt < 0 && neck > 0f) bornAt = frames
      last = neck
    }
    assertTrue("«Unisci» dura un viaggio, non un fotogramma: $frames", frames >= 15)
    assertTrue("il ponte nasce dopo i primi fotogrammi, non pieno al primo: $bornAt", bornAt in 3..8)
    assertEquals("alla fine il collo è pieno, com'è l'unione a riposo", 1f, last, 0f)
    // «Separa»: dal collo pieno, si strozza e si rompe una volta sola.
    now += frameNanos
    assertEquals(FluidGlassPlaneDriver.Change.Started, driver.step(now + frameNanos, apart, motion))
    now += frameNanos
    assertEquals(GlassPlaneUnionChange.Dissolving, driver.journey!!.fields.single().union)
    assertEquals("parte dal collo pieno", 1f, driver.neckNow(GlassPlaneUnionChange.Dissolving), 0f)
    last = 1f
    var breaks = 0
    frames = 0
    while (driver.step(now + frameNanos, apart, motion) != FluidGlassPlaneDriver.Change.Finished) {
      now += frameNanos
      frames++
      val neck = driver.neckNow(GlassPlaneUnionChange.Dissolving)
      assertTrue("il collo non rinasce: $last -> $neck", neck <= last + 1e-6f)
      if (last > 0f && neck <= 0f) breaks++
      last = neck
    }
    assertTrue(frames >= 15)
    assertEquals("una rottura sola", 1, breaks)
    assertTrue("all'ultimo fotogramma i lobi si sono ritirati: $last", last < -0.95f)
    assertEquals("a riposo i pezzi sono separati del tutto", -1f, driver.neckNow(GlassPlaneUnionChange.Dissolving), 0f)
  }

  @Test
  fun `un'unione ripensata a meta' riparte dal collo che c'e'`() {
    val union = Any()
    val motion = GlassPlaneMotion()
    val apart = state(a, b)
    val united = state(a.copy(unionId = union), b.copy(unionId = union))
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, united, motion)
    driver.step(frameNanos, apart, motion)
    val law = PhysicsPlaneNeck(1f, physicsPlaneNeckMs(driver.journey!!.settleMs))
    var now = frameNanos
    // «Separa» fino a poco prima della rottura, poi «Unisci» di nuovo.
    while (driver.neckNow(GlassPlaneUnionChange.Dissolving) > 0.6f) {
      now += frameNanos
      driver.step(now, apart, motion)
    }
    now += frameNanos
    assertEquals(FluidGlassPlaneDriver.Change.Retargeted, driver.step(now, united, motion))
    // Il collo all'istante della ripartenza: quello della legge della divisione, un fotogramma dopo l'ultimo mostrato.
    val before = law.at((now - frameNanos) / 1_000_000.0)
    val after = driver.neckNow(GlassPlaneUnionChange.Forming)
    assertTrue("a metà strada: $before", before > -1f && before < 1f)
    assertEquals("riparte da dove il collo si trova", before, after, 0.01f)
    now += frameNanos
    driver.step(now, united, motion)
    assertTrue(driver.neckNow(GlassPlaneUnionChange.Forming) >= after)
    // La salita letta al contrario: -1 da fermi, pieno a metà del riposo a vista, e pieno resta.
    assertEquals(-1f, fluidGlassUnionFormingNeck(0.0, 300.0), 0f)
    assertEquals(1f, fluidGlassUnionFormingNeck(150.0, 300.0), 0f)
    assertEquals(1f, fluidGlassUnionFormingNeck(900.0, 300.0), 0f)
    assertEquals(0.0, fluidGlassUnionFormingOffsetMs(-1f, 300.0), 0.0)
    val offset = fluidGlassUnionFormingOffsetMs(0.4f, 300.0)
    assertEquals(0.4f, fluidGlassUnionFormingNeck(offset, 300.0), 0.01f)
  }

  @Test
  fun `i grappoli che non cambiano restano al vetro di riposo, e il palco prende solo chi viaggia`() {
    // «cerca» e «preferiti» fermi mentre «play» compare (S3): al primo fotogramma del palco si univano con un ponte pieno.
    val from = state(a, b)
    val to = state(a, b, far)
    val journey = GlassPlaneJourney(from, to)
    val resting = fluidGlassRestingClusters(from, to, journey.stillKeys)
    assertEquals(setOf<Any>("a", "b"), resting.single().pieces.map { it.key }.toSet())
    assertEquals("un grappolo di più pezzi si chiama con la sua identità", resting.single().id, fluidGlassRestKey(resting.single()))
    assertTrue("nessun campo unisce due pezzi fermi", journey.fields.none { field ->
      field.flights.map { it.targetKey }.containsAll(listOf("a", "b"))
    })
    // Un pezzo solo, fermo: la chiave della sua superficie è il pezzo.
    val alone = fluidGlassRestingClusters(state(a), state(a, far), GlassPlaneJourney(state(a), state(a, far)).stillKeys)
    assertEquals("a", fluidGlassRestKey(alone.single()))
    // Se un pezzo del grappolo si muove, tutto il grappolo passa dal palco.
    val moved = state(a, b.copy(bounds = b.bounds.translate(12f, 0f)), far)
    assertTrue(fluidGlassRestingClusters(from, moved, GlassPlaneJourney(from, moved).stillKeys).isEmpty())
    // Un grappolo che prima non c'era (un pezzo nuovo accanto) passa dal palco anche se i vecchi sono fermi.
    val grown = state(a, b, piece("c", 352f))
    assertTrue(fluidGlassRestingClusters(from, grown, GlassPlaneJourney(from, grown).stillKeys).isEmpty())
  }

  @Test
  fun `il rigonfiamento gonfia ogni lato della quota del carattere, col tetto per i pannelli grandi`() {
    val character = GlassLiquidJourneys.Apple
    val slab = FluidForm.Slab(Rect(10f, 10f, 110f, 54f), FluidCornerRadii.all(22f))
    assertSame("senza rigonfiamento è la stessa lastra", slab, FluidGlassSwell.None.of(slab))
    assertSame("a livello zero è la stessa lastra", slab, FluidGlassSwell(character, 0f, 100f, 1f).of(slab))
    // Il 15% del lato corto (44): 3,3 per lato; una capsula resta una capsula.
    val swollen = FluidGlassSwell(character, 1f, 100f, 1f).of(slab)
    assertRect(Rect(6.7f, 6.7f, 113.3f, 57.3f), swollen.frame, 1e-4f)
    assertEquals(25.3f, swollen.cornerRadii.topLeft, 1e-4f)
    assertEquals(swollen.frame.height / 2f, swollen.cornerRadii.bottomRight, 1e-4f)
    // La quota è una frazione del lato corto: la densità non la cambia.
    assertEquals(3.3f, FluidGlassSwell(character, 1f, 100f, 3f).growPx(44f), 1e-4f)
    // A metà livello, metà della quota.
    assertEquals(1.65f, FluidGlassSwell(character, 0.5f, 100f, 1f).growPx(44f), 1e-4f)
    // Su un pannello grande il tetto tiene il rigonfiamento: 30 px senza, 6 col tetto.
    val panel = FluidForm.Slab(Rect(0f, 0f, 600f, 400f), FluidCornerRadii.all(40f))
    assertEquals(-30f, FluidGlassSwell(character, 1f, 100f, 1f).of(panel).frame.left, 1e-4f)
    assertEquals(-6f, FluidGlassSwell(character, 1f, 6f, 1f).of(panel).frame.left, 1e-4f)
    // Un carattere senza rigonfiamento non muove niente.
    assertSame(slab, FluidGlassSwell(GlassLiquidJourneys.Off, 1f, 100f, 1f).of(slab))
  }

  @Test
  fun `le unioni stabili di un campo, e quelle che cambiano no`() {
    val union = Any()
    val ua = a.copy(unionId = union)
    val ub = b.copy(unionId = union)
    // Un'unione che c'era e c'è: piena, col suo indice. Chi nasce dentro vi appartiene da subito.
    val kept = GlassPlaneJourney(state(ua, ub), state(ua, ub, piece("c", 352f).copy(unionId = union))).frame(50.0)
    assertEquals(listOf(0, 0, 0), fluidGlassStableUnionIndices(kept.pieces)!!.toList())
    // Un'unione che si scioglie non è più piena dal primo fotogramma del suo viaggio.
    val dissolving = GlassPlaneJourney(state(ua, ub), state(a, b)).frame(50.0)
    assertNull(fluidGlassStableUnionIndices(dissolving.pieces))
  }

  @Test
  fun `la forma di Fluid-physics, una lastra da sola, un gruppo fino a otto`() {
    val slab = FluidForm.Slab(Rect(0f, 0f, 10f, 10f))
    assertNull(fluidGlassFormOf(emptyList(), 4f))
    assertSame(slab, fluidGlassFormOf(listOf(slab), 4f))
    val group = fluidGlassFormOf(List(10) { slab }, 4f) as FluidForm.Group
    assertEquals(FluidForm.Group.MaxPieces, group.pieces.size)
    assertEquals(4f, group.blendRadius, 0f)
  }
}
