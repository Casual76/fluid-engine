package dev.antigravity.fluidengine.ui.page

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il passaggio di consegne della barra fra due pagine (F1B.2, partenza.md difetti 1 e 2): in ogni fotogramma il
 * vetro dei tasti lo disegna **o** la superficie che viaggia **o** il tasto, mai nessuno dei due e mai tutti e due,
 * e il passaggio cade quando la **forma** si ferma, non quando la molla della pagina scende sotto mezzo pixel.
 *
 * Gira sul JVM con gli oggetti veri: lo stato dell'host ([FluidPageHostState], la sua molla e il suo `settle`,
 * guidati da un orologio dei fotogrammi), il viaggio della barra ([FluidBarJourney]) con i collettori dei tasti, e
 * Fluid-physics guidata da fuori. Di Compose c'è solo **l'ordine delle fasi di un fotogramma**, scritto qui sotto
 * in [frame], ed è ciò che il test prova:
 *
 *  1. le animazioni (l'orologio manda il fotogramma: la molla scrive la corsa e, all'ultimo, chiama `settle`);
 *  2. la **composizione**: legge `state.isMoving` (la superficie che viaggia esiste?) e `journey.active` (chi porta
 *     il vetro). La leggono, in questa stessa passata, la barra e i tasti (`glassControlSurface` monta il proprio
 *     vetro se il suo collettore non lo spegne);
 *  3. gli effetti e il posizionamento: il `SideEffect` della barra e `onGloballyPositioned` della superficie
 *     chiamano `journey.recompute()`; la superficie è posizionata solo se è composta;
 *  4. il disegno: la superficie disegna se è composta e porta il vetro **secondo la composizione**; i tasti hanno il
 *     vetro se la composizione non l'ha spento.
 *
 * Col codice di F1B.1 (`active` scritto da `recompute` e falso solo a pagine ferme, la superficie tolta con
 * `isMoving`) questo test fallisce in due punti: al fotogramma del `settle` nessuno disegna il vetro (il vuoto di
 * partenza.md), e il vetro torna ai tasti ~360 ms dopo che la forma si è fermata.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidBarHandoffTest {

  private val width = 1600f
  private val clock = BroadcastFrameClock()
  private val state = FluidPageHostState().apply {
    scope = CoroutineScope(UnconfinedTestDispatcher() + clock)
    this.width = this@FluidBarHandoffTest.width
    height = 2560f
  }
  private val physics = FluidPhysicsState(FluidForm.Slab(Rect(0f, 0f, 1f, 1f)))
  private val journey = FluidBarJourney(state, physics).apply { blendPx = 8.5f }

  /** La riga della barra, larga quanto l'host; è attaccata solo se la superficie è composta. */
  private var stageComposed = false
  private val rects = HashMap<LayoutCoordinates, Rect>()
  private val stage: LayoutCoordinates = coordinates(Rect(0f, 0f, width, 119f), attached = { stageComposed })

  /** Le pagine: A (la radice, tre azioni) e B (la figlia, indietro e un'azione), come nella demo «Pagine». */
  private val sinkA = sink(Rect(1230f, 25f, 1324f, 119f), Rect(1336f, 25f, 1430f, 119f), Rect(1442f, 25f, 1536f, 119f))
  private val sinkB = sink(Rect(1442f, 25f, 1536f, 119f))
  private val back = sink(Rect(64f, 25f, 158f, 119f))

  init {
    journey.sinks["A"] = sinkA
    journey.sinks["B"] = sinkB
    journey.backSink = back
    journey.backKeys = setOf("B")
  }

  private fun coordinates(rect: Rect, attached: () -> Boolean = { true }): LayoutCoordinates {
    val proxy = Proxy.newProxyInstance(LayoutCoordinates::class.java.classLoader, arrayOf(LayoutCoordinates::class.java)) { self, method, args ->
      when {
        method.name == "isAttached" -> attached()
        // `size` è un IntSize, una classe valore: sul JVM il getter ha il nome storpiato e restituisce il Long impacchettato.
        method.name.startsWith("getSize") -> (rect.width.toLong() shl 32) or (rect.height.toLong() and 0xFFFFFFFFL)
        method.name == "localBoundingBoxOf" -> rects.getValue(args[0] as LayoutCoordinates)
        method.name == "hashCode" -> System.identityHashCode(self)
        method.name == "equals" -> self === args[0]
        method.name == "toString" -> "coordinate $rect"
        else -> throw UnsupportedOperationException(method.name)
      }
    } as LayoutCoordinates
    rects[proxy] = rect
    return proxy
  }

  private fun sink(vararg lenses: Rect): FluidBarPieceSink {
    val sink = FluidBarPieceSink(suppressed = { journey.active }, onChanged = {})
    for (lens in lenses) sink.pieces += FluidBarPiece(capsule = true).apply { coordinates = coordinates(lens) }
    return sink
  }

  /** Un fotogramma, con ciò che si vede alla fine. */
  private data class Seen(
    val tMs: Float,
    val moving: Boolean,
    val m: Float,
    val travelDraws: Boolean,
    val buttonsGlass: Boolean,
  )

  private var nanos = 0L
  private val frameNanos = 8_333_333L

  /**
   * Un fotogramma a 120 Hz, nell'ordine di Compose (vedi la testa). [afterComposition] è ciò che l'host fa nel suo
   * `SideEffect`: `onStack` con la pila nuova.
   */
  private fun frame(afterComposition: () -> Unit = {}): Seen {
    // 1. Le animazioni.
    nanos += frameNanos
    clock.sendFrame(nanos)
    // 2. La composizione: le letture della barra e dei tasti, nella stessa passata.
    val composedMoving = state.isMoving
    val composedActive = journey.active
    val m = if (state.isMoving) FluidPageMotion.barMorph(state.frontFraction()) else 0f
    // 3. Effetti e posizionamento: la pila nuova (host), poi il viaggio (barra). La superficie è posizionata, e
    // il suo nodo attaccato, solo se questa composizione l'ha messa.
    afterComposition()
    stageComposed = composedMoving
    journey.node = stage
    journey.recompute()
    // 4. Il disegno, con ciò che la composizione ha deciso.
    return Seen(
      tMs = nanos / 1e6f,
      moving = composedMoving,
      m = m,
      travelDraws = composedMoving && composedActive,
      buttonsGlass = !composedActive,
    )
  }

  /** Un viaggio intero: il fotogramma in cui arriva la pila nuova, poi fino a riposo e qualche fotogramma oltre. */
  private fun journeyTo(stack: List<Any>): List<Seen> {
    val seen = ArrayList<Seen>()
    seen += frame { state.onStack(stack) }
    var still = 0
    while (still < 6 && seen.size < 400) {
      val s = frame()
      seen += s
      if (!s.moving) still++
    }
    return seen
  }

  private fun assertOneGlassEveryFrame(what: String, seen: List<Seen>) {
    for ((i, s) in seen.withIndex()) {
      assertFalse("$what, fotogramma $i (+${s.tMs} ms, m=${s.m}): nessuno disegna il vetro", !s.travelDraws && !s.buttonsGlass)
      assertFalse("$what, fotogramma $i (+${s.tMs} ms, m=${s.m}): due vetri sovrapposti", s.travelDraws && s.buttonsGlass)
    }
  }

  /** Il vetro passa ai tasti nel fotogramma in cui la forma si ferma, e la superficie lo prende quando parte. */
  private fun assertHandsOverWhenTheShapeStops(what: String, seen: List<Seen>, endM: Float) {
    val carried = seen.indices.filter { seen[it].travelDraws }
    assertTrue("$what: la superficie non ha mai portato il vetro", carried.isNotEmpty())
    val first = carried.first()
    val last = carried.last()
    // In mezzo sempre lei: nessun buco.
    assertEquals("$what: la superficie porta il vetro in un tratto solo", last - first + 1, carried.size)
    // Prende il vetro al primo fotogramma in cui la forma ha lasciato il capo di partenza.
    assertTrue("$what: la superficie prende il vetro a m=${seen[first].m}", seen[first].m > 0f && seen[first].m < 1f)
    assertTrue("$what: prima del fotogramma $first la forma era ferma", seen[first - 1].m == 1f - endM || !seen[first - 1].moving)
    // Lo lascia nel fotogramma in cui la forma arriva al capo d'arrivo, non quando la pagina si posa.
    val handback = last + 1
    assertEquals("$what: al ritorno del vetro ai tasti la forma è al capo d'arrivo", endM, seen[handback].m)
    assertTrue("$what: il ritorno arriva mentre la pagina si muove ancora (la forma si ferma prima della molla)", seen[handback].moving)
    val settle = seen.indexOfFirst { it.tMs > seen[handback].tMs && !it.moving }
    assertTrue(
      "$what: il vetro torna ai tasti ${seen[settle].tMs - seen[handback].tMs} ms prima che la pagina si posi: atteso ben più di 100",
      seen[settle].tMs - seen[handback].tMs > 100f,
    )
  }

  @Test
  fun aPushHandsTheGlassOverWithoutAnEmptyFrameWhenTheShapeStops() {
    frame { state.onStack(listOf("A")) }
    frame()
    val seen = journeyTo(listOf("A", "B"))
    assertOneGlassEveryFrame("push A→B", seen)
    // Nel push la forma va dai tasti di A (m = 1) a quelli di B (m = 0).
    assertHandsOverWhenTheShapeStops("push A→B", seen, endM = 0f)
  }

  @Test
  fun aPopHandsTheGlassOverWithoutAnEmptyFrameWhenTheShapeStops() {
    frame { state.onStack(listOf("A")) }
    frame { state.onStack(listOf("A", "B")) }
    repeat(120) { frame() }
    assertFalse(state.isMoving)
    val seen = journeyTo(listOf("A"))
    assertOneGlassEveryFrame("pop B→A", seen)
    // Nel pop la forma va dai tasti di B (m = 0) a quelli di A (m = 1).
    assertHandsOverWhenTheShapeStops("pop B→A", seen, endM = 1f)
  }

  @Test
  fun aPushInterruptedByAPopNeverLeavesTheBarWithoutGlass() {
    frame { state.onStack(listOf("A")) }
    frame()
    val seen = ArrayList<Seen>()
    seen += frame { state.onStack(listOf("A", "B")) }
    // A un quarto del viaggio il tasto indietro di sistema: la pila torna ad A mentre la forma è in mezzo.
    repeat(12) { seen += frame() }
    assertTrue("a metà push la superficie porta il vetro", seen.last().travelDraws)
    seen += journeyTo(listOf("A"))
    assertOneGlassEveryFrame("push interrotto", seen)
  }
}
