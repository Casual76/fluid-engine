package dev.antigravity.fluidengine.ui.page

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsLiquidSurface
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScopeImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectsUpdates
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.glassBodyProbe
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
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
 *  3. gli effetti e il posizionamento: il `SideEffect` dell'host (`onStack`), poi quello della barra, che scrive le
 *     `backKeys` viste dalla sua composizione e chiama `journey.recompute()`; la superficie è posizionata solo se è
 *     composta;
 *  4. il disegno: a cancello aperto (`carries` della composizione) il nodo della superficie disegna, e il suo vetro si
 *     vede se la catena che i suoi effetti hanno **installato** ha l'intensità ([TravelNode]: la regola vera del nodo,
 *     `BackdropEffectsUpdates`, e l'azzeramento di `apply` a ogni giro); i tasti hanno il vetro se la composizione non
 *     l'ha spento, e il tasto indietro se il suo collettore non l'ha spento.
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

  /**
   * Le pagine della demo «Pagine»: A (la radice, tre azioni), B (indietro, condividi e stella), C (indietro, cerca,
   * condividi ed elimina). Le `backKeys` non sono fisse: le scrive ogni fotogramma il `SideEffect` della barra (vedi
   * [frame]).
   */
  private val sinkA = sink(Rect(1230f, 25f, 1324f, 119f), Rect(1336f, 25f, 1430f, 119f), Rect(1442f, 25f, 1536f, 119f))
  private val sinkB = sink(Rect(1336f, 25f, 1430f, 119f), Rect(1442f, 25f, 1536f, 119f))
  private val sinkC = sink(Rect(1230f, 25f, 1324f, 119f), Rect(1336f, 25f, 1430f, 119f), Rect(1442f, 25f, 1536f, 119f))
  private val back = FluidBarPieceSink(suppressed = { journey.active && journey.backInJourney }, onChanged = {}).apply {
    pieces += FluidBarPiece(capsule = true).apply { coordinates = coordinates(Rect(64f, 25f, 158f, 119f)) }
  }

  init {
    journey.sinks["A"] = sinkA
    journey.sinks["B"] = sinkB
    journey.sinks["C"] = sinkC
    journey.backSink = back
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
    /** Il tasto indietro ha il proprio vetro (composto e non spento dal suo collettore). */
    val backGlass: Boolean = false,
    /** Il vetro del tasto indietro lo porta la superficie che viaggia. */
    val backTravels: Boolean = false,
  )

  private var nanos = 0L
  private val frameNanos = 8_333_333L

  /**
   * La superficie che viaggia **come la tratta `drawBackdrop`** (giro di correzione di F1B.2, riga 17 del tablet; giro di
   * correzione unico di F1B, rilievo R3 del critico C). Disegnare non basta: la superficie mostra la catena che i suoi
   * effetti hanno **installato** l'ultima volta che sono girati, e il nodo li fa girare con la regola vera
   * ([BackdropEffectsUpdates]: all'attacco, quando cambia uno stato letto, e nel disegno quando arriva la taglia, sempre
   * sotto osservazione). Tre cose come nel prodotto:
   *  - il **cancello** della barra (`drawWithContent { if (carries) drawContent() }`, prima del nodo del vetro): a cancello
   *    chiuso il nodo non disegna, quindi non prende nemmeno la taglia;
   *  - ogni giro passa da `BackdropEffectScopeImpl.apply`, che azzera margine ed effetto: un giro che non costruisce il
   *    corpo (intensità zero, taglia ignota) installa **niente**, non «la catena di prima»;
   *  - il verdetto «il vetro si vede» legge la catena installata (il corpo che `glassBody` riceve nel giro, da
   *    [glassBodyProbe]), non un numero scritto dalla superficie.
   *
   * Gli oggetti sono quelli veri: [PhysicsLiquidSurface] e i suoi effetti, un osservatore di snapshot di Compose.
   */
  private inner class TravelNode {
    /** Lo `rememberUpdatedState(intensity)` di `fluidPhysicsSurface`: lo scrive la composizione della barra. */
    val intensity = mutableStateOf(TravelOff)
    val surface = PhysicsLiquidSurface(
      state = physics,
      role = GlassRole.Interactive,
      material = PhysicsMaterialSource(null, GlassDefaults.buttonOptics().sanitized()),
      darkSide = false,
      screenDensity = 2f,
      tint = NeutralTint,
      tintFrom = null,
      pressState = null,
      glowWeight = 1f,
      profile = GlassLiquidProfile.Default,
      intensity = { intensity.value() },
      tintBlend = { 1f },
      opticsBlend = { 1f },
      quality = { 1f },
      layoutWidth = { width },
      onPanel = { false },
    )
    private val scope = object : BackdropEffectScopeImpl() {
      override val shape: Shape = RectangleShape
    }

    /** L'intensità del corpo installato dall'ultimo giro; zero se quel giro non ne ha costruito uno. */
    var installed = 0f
      private set

    /** `updateEffects`: `effectScope.apply(effects)`, e ciò che il giro ha costruito diventa l'effetto del layer. */
    private val rebuild: () -> Unit = {
      var built = 0f
      glassBodyProbe = { s, inputs -> if (s === scope) built = inputs.materialize }
      try {
        scope.apply(surface.effects)
      } finally {
        glassBodyProbe = null
      }
      installed = built
    }
    val updates = BackdropEffectsUpdates(
      observe = { block -> observer.observeReads(this, onObservedChanged, block) },
      rebuild = rebuild,
    )

    /** `onAttach` → `observeEffects`. */
    fun attach() = updates.refresh()

    /** `DrawBackdropNode.draw`, raggiunto solo a cancello aperto: la taglia arriva qui. */
    fun draw() {
      val size = Size(width, 380f)
      val changed = scope.size != size
      if (changed) scope.size = size
      updates.onDraw(changed)
    }

    fun detach() = observer.clear(this)
  }

  private val stale = LinkedHashSet<TravelNode>()
  private val onObservedChanged: (TravelNode) -> Unit = { stale += it }
  private val observer = SnapshotStateObserver { it() }.also { it.start() }
  private var travel: TravelNode? = null

  @After
  fun stopObserving() {
    observer.stop()
    observer.clear()
    glassBodyProbe = null
  }

  /** Compose applica la composizione: gli osservatori di ciò che è cambiato rifanno i loro giri prima del disegno. */
  private fun applyChanges() {
    Snapshot.sendApplyNotifications()
    val nodes = stale.toList()
    stale.clear()
    nodes.forEach { it.updates.refresh() }
  }

  /** Le pagine col tasto indietro, come `onBack != null` delle loro registrazioni. */
  private val pagesWithBack = setOf<Any>("B", "C")

  /**
   * Un fotogramma a 120 Hz, nell'ordine di Compose (vedi la testa). [afterComposition] è ciò che l'host fa nel suo
   * `SideEffect`: `onStack` con la pila nuova. Il `SideEffect` della barra viene dopo, e scrive le `backKeys` **che la
   * sua composizione ha visto** (le pagine composte prima di `onStack`), come `FluidAnchoredBar`.
   */
  private fun frame(longMs: Float = 0f, afterComposition: () -> Unit = {}): Seen {
    // 1. Le animazioni (un fotogramma lungo: il filo principale è stato occupato, per esempio a comporre la pagina nuova).
    nanos += frameNanos + (longMs * 1e6f).toLong()
    clock.sendFrame(nanos)
    // 2. La composizione: le letture della barra e dei tasti, nella stessa passata. La superficie, se è composta, riceve
    // l'intensità di questa composizione; il nodo si attacca quando la superficie entra. Le pagine composte (e quindi le
    // registrazioni che la barra legge) sono quelle dello stato di adesso, prima di `onStack`.
    val composedMoving = state.isMoving
    val composedActive = journey.active
    val composedBackInJourney = journey.backInJourney
    val composedPages = listOfNotNull(state.upper, state.lower.takeIf { composedMoving })
    val composedBackKeys = composedPages.filter { it in pagesWithBack }.toSet()
    // Il tasto indietro: composto se una delle due pagine lo ha; il suo vetro lo spegne il suo collettore.
    val backComposed = composedBackKeys.isNotEmpty()
    val backSuppressed = composedActive && composedBackInJourney
    val m = if (state.isMoving) FluidPageMotion.barMorph(state.frontFraction()) else 0f
    if (composedMoving) {
      val node = travel ?: TravelNode().also { travel = it }
      node.intensity.value = if (composedActive) TravelFull else TravelOff
    }
    // 3. Effetti e posizionamento: la pila nuova (host), poi il `SideEffect` della barra (le chiavi viste, il viaggio).
    // La superficie è posizionata, e il suo nodo attaccato, solo se questa composizione l'ha messa.
    afterComposition()
    journey.backKeys = composedBackKeys
    journey.knownPages = composedPages.toSet()
    stageComposed = composedMoving
    journey.node = stage
    journey.recompute()
    val backInDrive = journey.backInJourney
    val node = travel
    if (composedMoving && node != null && node !in attached) {
      attached += node
      node.attach()
    }
    if (!composedMoving && node != null) {
      node.detach()
      attached -= node
      travel = null
    }
    applyChanges()
    // 4. Il disegno, con ciò che la composizione ha deciso. A cancello aperto il nodo della superficie disegna (e prende
    // la sua taglia); il suo vetro si vede se la catena che ha installato ha l'intensità.
    val drawn = if (composedMoving && composedActive) travel else null
    drawn?.draw()
    val travelDraws = drawn != null && drawn.installed > 0.001f
    return Seen(
      tMs = nanos / 1e6f,
      moving = composedMoving,
      m = m,
      travelDraws = travelDraws,
      buttonsGlass = !composedActive,
      backGlass = backComposed && !backSuppressed,
      backTravels = travelDraws && backInDrive,
    )
  }

  private val attached = HashSet<TravelNode>()

  /** Un viaggio intero: il fotogramma in cui arriva la pila nuova, poi fino a riposo e qualche fotogramma oltre. */
  private fun journeyTo(stack: List<Any>, longSecondFrameMs: Float = 0f): List<Seen> {
    val seen = ArrayList<Seen>()
    seen += frame { state.onStack(stack) }
    if (longSecondFrameMs > 0f) {
      // Il caso ripreso sul tablet (log della barra, push B→C): dopo la pila nuova la superficie che viaggia è composta e
      // posizionata (il suo `onGloballyPositioned` chiama `recompute`) prima della composizione del fotogramma lungo, che
      // trova il viaggio pronto (`act=true`) con le `backKeys` e le pagine note di una composizione prima.
      // E la molla dell'host è già partita: sul dispositivo il fotogramma lungo è la composizione della pagina nuova, che
      // viene dopo i richiami dei fotogrammi; la forma vi arriva in viaggio (nel log m = 0,05).
      clock.sendFrame(nanos)
      stageComposed = true
      journey.node = stage
      journey.recompute()
      seen += frame(longMs = longSecondFrameMs)
    }
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

  /**
   * B ↔ C: due pagine che hanno tutte e due il tasto indietro (rilievo 1 del critico D: sul tablet, in scuro, il disco
   * del tasto indietro sparisce ~60–85 ms a metà viaggio). Il tasto indietro non viaggia: tiene il suo vetro in ogni
   * fotogramma, e la superficie non lo porta mai (sarebbero due dischi). Le `backKeys` le scrive il `SideEffect` della
   * barra con le pagine che la sua composizione ha visto, un fotogramma dopo `onStack`, come in produzione.
   */
  @Test
  fun betweenTwoPagesWithBackTheBackButtonKeepsItsGlassEveryFrame() {
    frame { state.onStack(listOf("A")) }
    frame()
    journeyTo(listOf("A", "B"))
    repeat(10) { frame() }
    // Come sul tablet: il fotogramma dopo la pila nuova può essere lungo (la pagina C si compone: ~160 ms nel push B→C
    // ripreso), e la forma vi arriva già in viaggio. Con le `backKeys` di una composizione prima, il tasto indietro
    // spegneva lì il suo vetro, e rimontandolo restava senza disco 84–108 ms. Più lunghezze, perché la molla dell'host qui
    // non è quella del dispositivo: almeno una deve far cadere il fotogramma lungo dentro il viaggio.
    var stalledInsideTheJourney = 0
    val stalls = StringBuilder()
    for (stall in listOf(0f, 40f, 60f, 80f, 100f, 120f, 150f, 175f)) {
      for ((what, stack) in listOf("push B→C" to listOf("A", "B", "C"), "pop C→B" to listOf("A", "B"))) {
        val label = "$what, fotogramma lungo di $stall ms"
        val seen = journeyTo(stack, longSecondFrameMs = stall)
        if (stall > 0f && seen[1].travelDraws) stalledInsideTheJourney++
        if (stall > 0f) stalls.append("\n  $label: ${seen[1]}")
        assertOneGlassEveryFrame(label, seen)
        for ((i, s) in seen.withIndex()) {
          assertTrue("$label, fotogramma $i (+${s.tMs} ms, m=${s.m}): il tasto indietro è senza vetro", s.backGlass || s.backTravels)
          assertFalse("$label, fotogramma $i (+${s.tMs} ms, m=${s.m}): due dischi del tasto indietro", s.backGlass && s.backTravels)
        }
        repeat(10) { frame() }
      }
    }
    assertTrue("nessun fotogramma lungo è caduto dentro il viaggio: la prova non prova niente$stalls", stalledInsideTheJourney > 0)
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

  private companion object {
    /** Le due intensità che la barra passa alla superficie (`BarTravelFull`, `BarTravelOff` di `FluidAnchoredBar`). */
    val TravelFull: () -> Float = { 1f }
    val TravelOff: () -> Float = { 0f }

    /** La tinta conta poco qui: la superficie deve solo costruire la sua catena. */
    val NeutralTint = GlassTint(
      overlay = Color.Transparent,
      fallback = Color.Transparent,
      hairline = Color.Transparent,
      mode = GlassTintMode.Neutral,
    )
  }
}
