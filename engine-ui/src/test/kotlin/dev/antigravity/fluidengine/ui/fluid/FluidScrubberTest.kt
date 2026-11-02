package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I conti dello scrubber, senza una composizione: posizione e frazione, passi, testi, e soprattutto *quale
 * valore si mostra* — il lettore non combatte col dito, e dopo il rilascio non c'e' un ritorno.
 *
 * Quello che questi test non dicono e' il vetro e il moto: sono nel modulo `sample` (`MoreComponentsTest`)
 * per il gesto in un albero vero, e in mano per la goccia.
 */
class FluidScrubberTest {

  private val unit = 0f..1f
  private val track = 0f..200_000f // 3:20, in millisecondi

  // ===============================================================================================
  // Posizione e frazione
  // ===============================================================================================

  @Test
  fun `una posizione e la sua frazione si convertono una nell'altra`() {
    assertEquals(0.5f, FluidScrubbing.fraction(100_000f, track), 0f)
    assertEquals(100_000f, FluidScrubbing.value(0.5f, track), 0f)
    for (f in listOf(0f, 0.1f, 0.25f, 0.5f, 0.9f, 1f)) {
      assertEquals(f, FluidScrubbing.fraction(FluidScrubbing.value(f, track), track), 1e-6f)
    }
  }

  @Test
  fun `fuori dall'intervallo la frazione e' tenuta fra zero e uno`() {
    assertEquals(0f, FluidScrubbing.fraction(-5_000f, track), 0f)
    assertEquals(1f, FluidScrubbing.fraction(900_000f, track), 0f)
    assertEquals(0f, FluidScrubbing.value(-1f, track), 0f)
    assertEquals(200_000f, FluidScrubbing.value(7f, track), 0f)
  }

  @Test
  fun `l'estremita' e' l'estremita' esatta, senza un'ultima cifra di errore`() {
    // 0,1 + 0,2 non e' 0,3 in virgola mobile: una frazione di 1 deve comunque dare la fine.
    val awkward = 0.1f..0.3f
    assertEquals(0.3f, FluidScrubbing.value(1f, awkward), 0f)
    assertEquals(1f, FluidScrubbing.fraction(0.3f, awkward), 0f)
  }

  @Test
  fun `una durata ancora ignota non divide per zero`() {
    assertEquals(0f, FluidScrubbing.fraction(5f, 0f..0f), 0f)
    assertEquals(0f, FluidScrubbing.value(0.7f, 0f..0f), 0f)
    assertEquals(0f, FluidScrubbing.snap(5f, 0f..0f, 1f), 0f)
    assertEquals(0, FluidScrubbing.stepCount(0f..0f, 1f))
  }

  @Test
  fun `un intervallo al contrario o con un NaN e' un intervallo di un solo punto`() {
    // Una durata `TIME_UNSET` negativa e' un errore di chi chiama: non deve far cadere la schermata.
    val reversed = 5f..(-1f)
    assertEquals(5f..5f, FluidScrubbing.normalized(reversed))
    assertEquals(0f, FluidScrubbing.fraction(3f, reversed), 0f)
    assertEquals(5f, FluidScrubbing.clamp(99f, reversed), 0f)
    assertEquals(0f..0f, FluidScrubbing.normalized(Float.NaN..1f))
    assertEquals(0f, FluidScrubbing.fraction(Float.NaN, unit), 0f)
    assertEquals(0f, FluidScrubbing.clamp(Float.NaN, unit), 0f)
  }

  // ===============================================================================================
  // Passi
  // ===============================================================================================

  @Test
  fun `senza passo il valore e' il suo, dentro l'intervallo`() {
    assertEquals(0.42f, FluidScrubbing.snap(0.42f, unit, 0f), 0f)
    assertEquals(0f, FluidScrubbing.snap(-3f, unit, 0f), 0f)
    assertEquals(1f, FluidScrubbing.snap(3f, unit, 0f), 0f)
    assertEquals(-1, FluidScrubbing.stepIndex(0.4f, unit, 0f))
    assertEquals(0, FluidScrubbing.interiorSteps(unit, 0f))
  }

  @Test
  fun `a passi il valore cade sul multiplo piu' vicino, e a meta' vince quello avanti`() {
    val frames = 0f..12f
    assertEquals(3f, FluidScrubbing.snap(3.4f, frames, 1f), 0f)
    assertEquals(4f, FluidScrubbing.snap(3.6f, frames, 1f), 0f)
    assertEquals(4f, FluidScrubbing.snap(3.5f, frames, 1f), 0f)
    assertEquals(0f, FluidScrubbing.snap(-2f, frames, 1f), 0f)
    assertEquals(12f, FluidScrubbing.snap(40f, frames, 1f), 0f)
  }

  @Test
  fun `i passi partono dall'inizio dell'intervallo, non da zero`() {
    // Una cronologia da -120 a +30 minuti, un fotogramma ogni dieci.
    val timeline = -120f..30f
    assertEquals(-120f, FluidScrubbing.snap(-119f, timeline, 10f), 0f)
    assertEquals(-110f, FluidScrubbing.snap(-113f, timeline, 10f), 0f)
    assertEquals(0f, FluidScrubbing.snap(2f, timeline, 10f), 0f)
    assertEquals(15, FluidScrubbing.stepCount(timeline, 10f))
    assertEquals(12, FluidScrubbing.stepIndex(0f, timeline, 10f))
  }

  @Test
  fun `se l'intervallo non e' un multiplo del passo anche la fine e' un valore`() {
    // 25 minuti a passi di 10: 0, 10, 20 e 25. La fine di un brano si raggiunge.
    val odd = 0f..25f
    assertEquals(3, FluidScrubbing.stepCount(odd, 10f))
    assertEquals(25f, FluidScrubbing.snap(24f, odd, 10f), 0f)
    assertEquals(25f, FluidScrubbing.snap(23f, odd, 10f), 0f)
    assertEquals(20f, FluidScrubbing.snap(21f, odd, 10f), 0f)
    assertEquals(3, FluidScrubbing.stepIndex(25f, odd, 10f))
    assertEquals(2, FluidScrubbing.stepIndex(20f, odd, 10f))
  }

  @Test
  fun `un passo piu' largo dell'intervallo lascia i due estremi`() {
    assertEquals(1, FluidScrubbing.stepCount(0f..5f, 100f))
    assertEquals(0f, FluidScrubbing.snap(1f, 0f..5f, 100f), 0f)
    assertEquals(5f, FluidScrubbing.snap(4f, 0f..5f, 100f), 0f)
  }

  @Test
  fun `i passi interni sono quelli che dice TalkBack, i valori meno le due estremita'`() {
    // Tredici fotogrammi: 0..12 a passi di uno sono dodici intervalli e undici passi interni.
    assertEquals(12, FluidScrubbing.stepCount(0f..12f, 1f))
    assertEquals(11, FluidScrubbing.interiorSteps(0f..12f, 1f))
    assertEquals(0, FluidScrubbing.interiorSteps(0f..1f, 1f))
  }

  // ===============================================================================================
  // Tastiera e azioni di TalkBack
  // ===============================================================================================

  @Test
  fun `una freccia senza passo ne' keyStep si muove del cinque per cento`() {
    assertEquals(110_000f, FluidScrubbing.nudge(100_000f, track, 0f, 0f, +1, page = false), 0f)
    assertEquals(90_000f, FluidScrubbing.nudge(100_000f, track, 0f, 0f, -1, page = false), 0f)
  }

  @Test
  fun `un keyStep dato vale piu' del cinque per cento`() {
    assertEquals(110_000f, FluidScrubbing.nudge(100_000f, track, 0f, 10_000f, +1, page = false), 0f)
    // Una registrazione di diciannove ore: il cinque per cento sarebbe quasi un'ora, dieci secondi no.
    val long = 0f..68_400_000f
    assertEquals(10_000f, FluidScrubbing.nudge(0f, long, 0f, 10_000f, +1, page = false), 0f)
  }

  @Test
  fun `a passi una freccia e' un passo, e parte dal passo e non da mezzo passo`() {
    val frames = 0f..12f
    assertEquals(5f, FluidScrubbing.nudge(4f, frames, 1f, 0f, +1, page = false), 0f)
    assertEquals(3f, FluidScrubbing.nudge(4f, frames, 1f, 0f, -1, page = false), 0f)
    // Da 4,4 (la posizione di fuori non e' sul passo) si parte da 4, non da 4,4.
    assertEquals(5f, FluidScrubbing.nudge(4.4f, frames, 1f, 0f, +1, page = false), 0f)
  }

  @Test
  fun `una pagina e' un decimo dell'intervallo, o la freccia se e' piu' grande`() {
    assertEquals(120_000f, FluidScrubbing.nudge(100_000f, track, 0f, 0f, +1, page = true), 0f)
    // Una freccia da 30 s su 200 s e' piu' di un decimo: la pagina non e' piu' piccola della freccia.
    assertEquals(130_000f, FluidScrubbing.nudge(100_000f, track, 0f, 30_000f, +1, page = true), 0f)
  }

  @Test
  fun `una freccia non esce dall'intervallo`() {
    assertEquals(200_000f, FluidScrubbing.nudge(199_000f, track, 0f, 0f, +1, page = false), 0f)
    assertEquals(0f, FluidScrubbing.nudge(2_000f, track, 0f, 0f, -1, page = false), 0f)
    assertEquals(12f, FluidScrubbing.nudge(12f, 0f..12f, 1f, 0f, +1, page = false), 0f)
    assertEquals(0f, FluidScrubbing.nudge(3f, 0f..0f, 1f, 0f, +1, page = false), 0f)
  }

  // ===============================================================================================
  // Testi
  // ===============================================================================================

  @Test
  fun `l'orologio scrive minuti e secondi, e le ore solo quando ci sono`() {
    assertEquals("0:00", FluidScrubbing.clock(0L))
    assertEquals("0:05", FluidScrubbing.clock(5_000L))
    assertEquals("1:05", FluidScrubbing.clock(65_000L))
    assertEquals("3:20", FluidScrubbing.clock(200_000L))
    assertEquals("59:59", FluidScrubbing.clock(3_599_000L))
    assertEquals("1:00:00", FluidScrubbing.clock(3_600_000L))
    assertEquals("1:02:03", FluidScrubbing.clock(3_723_000L))
    assertEquals("19:00:00", FluidScrubbing.clock(68_400_000L))
  }

  @Test
  fun `l'orologio arrotonda per difetto al secondo e non scende sotto zero`() {
    // Un lettore a 3:19,9 e' ancora a 3:19.
    assertEquals("3:19", FluidScrubbing.clock(199_900L))
    assertEquals("0:00", FluidScrubbing.clock(-4_000L))
    assertEquals("0:00", FluidScrubbing.clock(999L))
  }

  @Test
  fun `il testo di serie e' quello che TalkBack dice, trascorso di totale`() {
    assertEquals("1:05 di 3:20", FluidScrubbing.Spoken(65_000f, track))
    assertEquals("0:00 di 3:20", FluidScrubbing.Spoken(0f, track))
    assertEquals("3:20 di 3:20", FluidScrubbing.Spoken(200_000f, track))
  }

  @Test
  fun `trascorso, mancante e totale dicono tre cose diverse della stessa posizione`() {
    assertEquals("1:05", FluidScrubbing.Elapsed(65_000f, track))
    assertEquals("−2:15", FluidScrubbing.Remaining(65_000f, track))
    assertEquals("3:20", FluidScrubbing.Total(65_000f, track))
    // Il meno e' quello tipografico, come nel resto dell'app.
    assertTrue(FluidScrubbing.Remaining(0f, track).startsWith("−"))
  }

  @Test
  fun `i testi contano dall'inizio dell'intervallo, non da zero`() {
    val window = 60_000f..260_000f
    assertEquals("0:05", FluidScrubbing.Elapsed(65_000f, window))
    assertEquals("3:15", FluidScrubbing.Remaining(65_000f, window).removePrefix("−"))
    assertEquals("3:20", FluidScrubbing.Total(65_000f, window))
  }

  // ===============================================================================================
  // Quale valore si mostra
  // ===============================================================================================

  @Test
  fun `a riposo si mostra la posizione di fuori, sotto il dito e dopo il rilascio quella tenuta`() {
    assertEquals(10f, FluidScrubbing.shownValue(FluidScrubberPhase.Idle, held = 99f, external = 10f), 0f)
    assertEquals(99f, FluidScrubbing.shownValue(FluidScrubberPhase.Dragging, held = 99f, external = 10f), 0f)
    assertEquals(99f, FluidScrubbing.shownValue(FluidScrubberPhase.Settling, held = 99f, external = 10f), 0f)
  }

  @Test
  fun `mentre si trascina il lettore che avanza non sposta niente`() {
    val hold = FluidScrubberHold()
    var external = 10_000f
    hold.grab(10_000f, direct = true)
    hold.move(120_000f)
    // Il lettore va avanti di dieci secondi, a battiti: la riga non lo guarda.
    repeat(10) {
      external += 1_000f
      assertEquals(120_000f, FluidScrubbing.shownValue(hold.phase, hold.value, external), 0f)
    }
  }

  @Test
  fun `dopo il rilascio la riga resta dove l'hai lasciata finche' il lettore non ci arriva`() {
    val hold = FluidScrubberHold()
    val range = track
    var external = 30_000f
    hold.grab(30_000f, direct = true)
    hold.move(150_000f)
    hold.land(150_000f, from = external)

    // Il seek non e' ancora arrivato: il lettore e' dov'era (un po' piu' avanti, perche' suona ancora).
    external = 30_400f
    assertEquals("la riga non torna indietro", 150_000f, FluidScrubbing.shownValue(hold.phase, hold.value, external), 0f)
    assertFalse(FluidScrubbing.caughtUp(external, hold.value, hold.origin, range, 0f))

    // Il seek e' arrivato: il lettore e' al punto, qualche decimo piu' in la per la durata del seek.
    external = 150_300f
    assertTrue(FluidScrubbing.caughtUp(external, hold.value, hold.origin, range, 0f))
    hold.clear()
    assertEquals(150_300f, FluidScrubbing.shownValue(hold.phase, hold.value, external), 0f)
  }

  // ===============================================================================================
  // Quando il lettore ha raggiunto il punto
  // ===============================================================================================

  @Test
  fun `un colpetto su una registrazione lunga non e' raggiunto prima che il lettore si muova`() {
    // Diciannove ore, un minuto avanti: lo 0,09% dell'intervallo. Una tolleranza in frazioni dell'intervallo lo
    // darebbe per raggiunto subito, e la maniglia tornerebbe indietro di un minuto per poi ripartire.
    val long = 0f..68_400_000f
    val origin = 30_000_000f
    val target = origin + 60_000f
    assertFalse("il lettore e' ancora dov'era", FluidScrubbing.caughtUp(origin, target, origin, long, 0f))
    assertFalse("e un battito dopo, ancora", FluidScrubbing.caughtUp(origin + 250f, target, origin, long, 0f))
    assertTrue("il seek e' atterrato", FluidScrubbing.caughtUp(target + 120f, target, origin, long, 0f))
  }

  @Test
  fun `un seek che si appoggia vicino al punto, ma non sul punto, e' raggiunto`() {
    // L'app ha chiesto 2:30 e il lettore e' atterrato a 2:28 (un fotogramma chiave): la maniglia deve
    // scivolare li', non aspettare per sempre 2:30.
    assertTrue(FluidScrubbing.caughtUp(148_000f, 150_000f, 30_000f, track, 0f))
  }

  @Test
  fun `un salto di lunghezza zero e' raggiunto subito, e un intervallo di un punto sempre`() {
    assertTrue(FluidScrubbing.caughtUp(80_000f, 80_000f, 80_000f, track, 0f))
    assertTrue(FluidScrubbing.caughtUp(1f, 9f, 0f, 0f..0f, 0f))
  }

  @Test
  fun `un salto all'indietro funziona come uno in avanti`() {
    assertFalse(FluidScrubbing.caughtUp(120_000f, 20_000f, 120_000f, track, 0f))
    assertFalse(FluidScrubbing.caughtUp(120_400f, 20_000f, 120_000f, track, 0f))
    assertTrue(FluidScrubbing.caughtUp(20_300f, 20_000f, 120_000f, track, 0f))
  }

  @Test
  fun `a passi e' raggiunto entro mezzo passo, la dove la posizione si poserebbe comunque`() {
    val frames = 0f..12f
    assertFalse(FluidScrubbing.caughtUp(3f, 8f, 3f, frames, 1f))
    assertFalse(FluidScrubbing.caughtUp(7f, 8f, 3f, frames, 1f))
    assertTrue(FluidScrubbing.caughtUp(8f, 8f, 3f, frames, 1f))
    assertTrue(FluidScrubbing.caughtUp(8.4f, 8f, 3f, frames, 1f))
  }

  // ===============================================================================================
  // Lo stato tenuto
  // ===============================================================================================

  @Test
  fun `afferrare, spostare, lasciare e finire passano per i tre tempi, una volta ciascuno`() {
    val hold = FluidScrubberHold()
    assertEquals(FluidScrubberPhase.Idle, hold.phase)
    assertFalse(hold.active)

    hold.grab(40f, direct = true)
    assertEquals(FluidScrubberPhase.Dragging, hold.phase)
    assertEquals(40f, hold.value, 0f)
    assertTrue(hold.direct)

    hold.move(70f)
    assertEquals(70f, hold.value, 0f)
    assertEquals(FluidScrubberPhase.Dragging, hold.phase)

    hold.land(70f, from = 41f)
    assertEquals(FluidScrubberPhase.Settling, hold.phase)
    assertEquals(41f, hold.origin, 0f)
    assertTrue("il dito resta la verita' finche' la molla non lo raggiunge", hold.direct)

    hold.clear()
    assertEquals(FluidScrubberPhase.Idle, hold.phase)
    assertFalse(hold.direct)
  }

  @Test
  fun `un salto e' un punto tenuto da subito, e la maniglia lo raggiunge con la molla`() {
    val hold = FluidScrubberHold()
    hold.grab(10f, direct = true)
    hold.jump(60f, from = 10f)
    assertEquals(FluidScrubberPhase.Settling, hold.phase)
    assertEquals(60f, hold.value, 0f)
    assertFalse("un salto viaggia, non e' il dito", hold.direct)
  }

  @Test
  fun `ogni gesto cambia la chiave, cosi' un fermo vecchio non spegne quello nuovo`() {
    val hold = FluidScrubberHold()
    val first = hold.generation
    hold.grab(1f, direct = false)
    val grabbed = hold.generation
    hold.land(2f, from = 1f)
    val landed = hold.generation
    hold.jump(3f, from = 2f)
    val jumped = hold.generation
    assertEquals(4, setOf(first, grabbed, landed, jumped).size)
    // Muoversi non e' un gesto nuovo: non rimette in moto il fermo.
    hold.move(9f)
    assertEquals(jumped, hold.generation)
  }

  // ===============================================================================================
  // Aptica
  // ===============================================================================================

  @Test
  fun `arrivare a un'estremita' suona una volta, starci o afferrarci no`() {
    assertTrue(FluidScrubbing.edgeEntered(0.4f, 1f))
    assertTrue(FluidScrubbing.edgeEntered(0.4f, 0f))
    assertFalse("un dito fermo in fondo", FluidScrubbing.edgeEntered(1f, 1f))
    assertFalse("afferrare la maniglia in fondo", FluidScrubbing.edgeEntered(0f, 0f))
    assertFalse(FluidScrubbing.edgeEntered(0.4f, 0.6f))
    assertTrue("uscire e rientrare e' arrivare di nuovo", FluidScrubbing.edgeEntered(0.97f, 1f))
  }

  @Test
  fun `un trascinamento continuo non suona altro che le estremita'`() {
    assertNull(FluidScrubbing.dragHaptic(edge = false, stepped = false, landed = false))
    // `landed` e' dei passi: un valore continuo non ne atterra mai uno, ma anche se lo dicesse non suonerebbe.
    assertNull(FluidScrubbing.dragHaptic(edge = false, stepped = false, landed = true))
    assertEquals(FluidHapticEvent.Threshold, FluidScrubbing.dragHaptic(edge = true, stepped = false, landed = false))
  }

  @Test
  fun `a passi un passo atterrato e' un Tick, e l'estremita' che e' anche un passo suona una volta sola`() {
    assertEquals(FluidHapticEvent.Tick, FluidScrubbing.dragHaptic(edge = false, stepped = true, landed = true))
    assertNull(FluidScrubbing.dragHaptic(edge = false, stepped = true, landed = false))
    assertEquals(FluidHapticEvent.Threshold, FluidScrubbing.dragHaptic(edge = true, stepped = true, landed = true))
  }

  @Test
  fun `un tocco sulla pista suona un colpo solo, mai due`() {
    assertEquals(FluidHapticEvent.Tap, FluidScrubbing.tapHaptic(edge = false, stepped = false, stepChanged = false))
    assertEquals(FluidHapticEvent.Threshold, FluidScrubbing.tapHaptic(edge = true, stepped = false, stepChanged = false))
    assertEquals(FluidHapticEvent.Tick, FluidScrubbing.tapHaptic(edge = false, stepped = true, stepChanged = true))
    assertNull("a passi, un tocco sul passo in cui si e' gia' non e' un momento", FluidScrubbing.tapHaptic(edge = false, stepped = true, stepChanged = false))
    assertEquals(FluidHapticEvent.Threshold, FluidScrubbing.tapHaptic(edge = true, stepped = true, stepChanged = true))
  }

  @Test
  fun `passando veloci su molti passi se ne sentono alcuni, non un ronzio`() {
    // Cinque passi in cento millisecondi: il pavimento di 55 ms ne fa passare due.
    val gate = FluidSliderNotchGate()
    gate.restart(0)
    val ticks = (1..5).count { i ->
      val landed = gate.land(i, 1_000L + i * 20L)
      FluidScrubbing.dragHaptic(edge = false, stepped = true, landed = landed) == FluidHapticEvent.Tick
    }
    assertTrue("$ticks tick su cinque passi in cento millisecondi", ticks in 1..3)
  }

  // ===============================================================================================
  // I token
  // ===============================================================================================

  @Test
  fun `i due tempi di attesa sono token con il nome e il valore che avevano`() {
    // La sparizione dello scorrimento rapido era `HideDelayMillis = 1_200L` con un TODO: stesso valore.
    assertEquals(1_200L, FluidMotion.FastScrollerHideMillis)
    assertEquals(3_000L, FluidMotion.ScrubberHoldMillis)
  }
}
