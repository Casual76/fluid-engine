package dev.antigravity.fluidengine.ui.page

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidTopBarControls
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarExtent
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarIds
import dev.antigravity.fluidengine.ui.fluid.toolbar.buildFluidToolbar
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La barra dell'host disegnata dal modello (piano «iOS in Android», F4A.3): **chi** dà i tasti alla barra in ogni
 * fotogramma di un push, di un pop, del gesto indietro (tenuto, annullato, confermato), di un push interrotto e di un
 * cambio di scheda, sullo stato vero dell'host; che cosa disegna per una pagina dichiarata, per una di sempre e per una
 * non ancora composta; lo scambio del titolo agganciato sul tempo suo; gli inset del titolo.
 *
 * Il «come si vede» (il vetro che resta, nasce, confluisce) è del piano flottante, che ha le sue prove; qui si prova che
 * la barra **gli chiede il cambio giusto al momento giusto**: al push, non durante il gesto, alla conferma, mai
 * all'annullo, e mai in funzione della corsa della pagina. L'albero vero è in `FluidToolbarPagesTest` (galleria).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidPageBarModelTest {
  @Test fun ilMaterialeMistoSostieneIlTondoDuranteLoScambio() {
    val lens = Rect(10f, 0f, 54f, 44f)
    val face = mixedActionFace(lens, 26f)
    assertEquals(lens.center, face.center)
    assertTrue(face.width > 26f && face.width < lens.width)
    for (step in 0..100) {
      val model = step / 100f
      assertTrue(model + (1f - model) * legacyMaterialAlpha(model) >= 0.85f)
    }
    assertEquals(1f, legacyMaterialAlpha(0f), 0f)
    assertEquals(0f, legacyMaterialAlpha(1f), 0f)
  }

  private val declared = buildFluidToolbar(onBack = {}) {
    button("trail", FluidIcons.MoreHoriz, "Opzioni", onClick = {})
  }

  // --- Chi dà i tasti alla barra -------------------------------------------------------------------------------------

  @Test
  fun `la barra prende i tasti dalla cima della pila richiesta, e prima della prima pila dalla pagina sopra`() {
    assertEquals("figlia", fluidPageBarPage(interactionPage = "figlia", upper = "radice"))
    assertEquals("radice", fluidPageBarPage(interactionPage = null, upper = "radice"))
    assertEquals(null, fluidPageBarPage(interactionPage = null, upper = null))
  }

  @Test
  fun `fra due pagine con le actions di sempre la barra e' quella di prima`() {
    assertFalse(fluidPageBarUsesModelOf(listOf(null, null)))
    assertFalse(fluidPageBarUsesModelOf(emptyList()))
    assertTrue(fluidPageBarUsesModelOf(listOf(null, declared)))
    assertTrue(fluidPageBarUsesModelOf(listOf(declared, declared)))
  }

  @Test
  fun `una pagina di sempre accanto a una dichiarata porta nel modello solo il suo indietro`() {
    // Lo stesso indietro delle pagine dichiarate: fermo al pixel anche fra una pagina vecchia e una nuova.
    val legacy = fluidPageBarDeclarationOf(toolbar = null, onBack = {})
    assertEquals(listOf<Any>(FluidToolbarIds.Back), legacy.buttons.keys.toList())
    assertTrue(fluidPageBarDeclarationOf(toolbar = null, onBack = null).entries.isEmpty())
    assertSame(declared, fluidPageBarDeclarationOf(declared, onBack = {}))
  }

  @Test
  fun `una pagina non ancora composta non cambia i tasti, una senza barra li toglie solo a riposo`() {
    // Non ancora composta (la cima nuova arriva allo stato dopo la composizione): resta l'ultima, in movimento e no.
    assertSame(declared, fluidPageBarTargetOf(false, false, null, null, moving = true, last = declared))
    assertSame(declared, fluidPageBarTargetOf(false, false, null, null, moving = false, last = declared))
    assertSame(FluidPageBarEmpty, fluidPageBarTargetOf(false, false, null, null, moving = true, last = null))
    // Composta senza barra: durante il movimento i tasti non muoiono per rinascere; a riposo niente.
    assertSame(declared, fluidPageBarTargetOf(true, false, null, null, moving = true, last = declared))
    assertSame(FluidPageBarEmpty, fluidPageBarTargetOf(true, false, null, null, moving = false, last = declared))
    // Con la sua barra: la sua.
    val other = buildFluidToolbar(null) { button("lead", FluidIcons.IosShare, "Condividi", onClick = {}) }
    assertSame(other, fluidPageBarTargetOf(true, true, other, null, moving = true, last = declared))
  }

  @Test
  fun `un host passa al modello alla prima barra dichiarata e non torna indietro`() {
    val memory = FluidPageBarTargetMemory()
    assertFalse("un host di sole pagine di sempre resta quello di prima", memory.noteModel(false))
    assertFalse(memory.noteModel(false))
    assertTrue(memory.noteModel(true))
    // Poi resta: la barra del modello non si monta e smonta a ogni passaggio fra una pagina di sempre e una dichiarata.
    assertTrue(memory.noteModel(false))
    assertTrue(memory.hostModel)
  }

  // --- Il viaggio fra pagine sullo stato vero dell'host ---------------------------------------------------------------

  @Test
  fun `al push i tasti passano subito alla pagina nuova e non seguono la corsa`() = Rig().use { rig ->
    rig.stack(listOf("radice", "figlia"))
    assertEquals("la barra parte col tocco", "figlia", rig.barPage)
    val travels = HashSet<Int>()
    rig.untilRest {
      assertEquals("la barra non cambia mentre la pagina corre", "figlia", rig.barPage)
      travels += rig.state.travelPx.toInt() / 100
    }
    assertTrue("la corsa e' passata per molti punti: ${travels.size}", travels.size >= 5)
    assertEquals("figlia", rig.barPage)
  }

  @Test
  fun `al pop col tasto i tasti passano subito alla pagina sotto`() = Rig().use { rig ->
    rig.atRestOn(listOf("radice", "figlia"))
    rig.stack(listOf("radice"))
    assertEquals(FluidPageMove.Pop, rig.state.kind)
    assertEquals("radice", rig.barPage)
    rig.untilRest { assertEquals("radice", rig.barPage) }
  }

  @Test
  fun `il gesto non cambia i tasti prima della conferma, e all'annullo non succede niente`() = Rig().use { rig ->
    rig.atRestOn(listOf("radice", "figlia"))
    val token = rig.state.beginDrag(direction = 1)!!
    for (fraction in listOf(0.25f, 0.5f, 0.75f, 0.95f)) {
      rig.state.dragTo(token, rig.state.exitTravel() * fraction)
      repeat(30) { rig.tick() }
      assertEquals("tenuto al ${(fraction * 100).toInt()}%: la pagina sotto si vede, i tasti no", "figlia", rig.barPage)
    }
    rig.state.release(token, velocity = -2_000f, decided = false)
    assertEquals(FluidPageMove.Back, rig.state.kind)
    rig.untilRest { assertEquals("l'annullo non cambia i tasti", "figlia", rig.barPage) }
    assertEquals(listOf<Any>("radice", "figlia"), rig.requested)

    // Ora il gesto che conferma: i tasti cambiano al rilascio, una volta, e non tornano.
    val again = rig.state.beginDrag(direction = 1)!!
    rig.state.dragTo(again, rig.state.exitTravel() * 0.4f)
    assertEquals("figlia", rig.barPage)
    rig.state.release(again, velocity = 0f, decided = true)
    assertEquals("alla conferma la pila si accorcia e la barra va alla pagina sotto", "radice", rig.barPage)
    rig.untilRest { assertEquals("radice", rig.barPage) }
  }

  @Test
  fun `un push interrotto dal gesto finisce verso la pagina in cima e riparte alla conferma`() = Rig().use { rig ->
    rig.stack(listOf("radice", "figlia"))
    repeat(8) { rig.tick() }
    assertTrue(rig.state.isMoving)
    val token = rig.state.beginDrag(direction = 1)!!
    repeat(20) { rig.tick() }
    assertEquals("durante il gesto i tasti restano quelli della pagina in cima", "figlia", rig.barPage)
    rig.state.release(token, velocity = 3_000f, decided = true)
    assertEquals("radice", rig.barPage)
    rig.untilRest { assertEquals("radice", rig.barPage) }
  }

  @Test
  fun `un cambio di scheda e' un cambio di cima come un altro, e il titolo non fa passi`() = Rig().use { rig ->
    rig.state.peerOf = { from, to -> if (from == "note" && to == "cartelle") 1 else if (from == "cartelle" && to == "note") -1 else 0 }
    rig.atRestOn(listOf("note"))
    rig.stack(listOf("cartelle"))
    assertEquals(FluidPageMove.Peer, rig.state.kind)
    assertEquals("cartelle", rig.barPage)
    assertEquals(0, FluidPageMotion.barTitleStepSign(rig.state.kind, rig.state.direction))
  }

  // --- Il titolo agganciato ------------------------------------------------------------------------------------------

  @Test
  fun `il titolo agganciato si scambia sfocandosi sul tempo suo, con un passo nel verso della navigazione`() {
    val swap = FluidBarTitleSwap(blurAvailable = true)
    swap.retarget("radice", step = 0, snap = false)
    assertEquals("il primo titolo c'e' gia'", 1f, swap.alpha("radice"), 0f)
    assertEquals(0f, swap.alpha("figlia"), 0f)

    swap.retarget("figlia", step = 1, snap = false)
    assertEquals(1f, swap.alpha("radice"), 0.001f)
    assertEquals(0f, swap.alpha("figlia"), 0.001f)
    assertEquals("il nuovo arriva dal lato della sua pagina", 1f, swap.step("figlia"), 0.001f)
    assertEquals(0f, swap.step("radice"), 0.001f)

    swap.advance(150.0)
    assertTrue("a meta' il vecchio e' sfocato: ${swap.blur("radice")}", swap.blur("radice") > 0.08f)
    assertTrue("e va dall'altra parte: ${swap.step("radice")}", swap.step("radice") < -0.5f)
    assertTrue(swap.blur("figlia") > 0.05f)
    assertTrue(swap.step("figlia") > 0f && swap.step("figlia") <= 1f)

    swap.advance(500.0)
    assertTrue(swap.finished)
    swap.settle()
    assertEquals(1f, swap.alpha("figlia"), 0f)
    assertEquals(0f, swap.blur("figlia"), 0f)
    assertEquals(0f, swap.step("figlia"), 0f)
    assertEquals(0f, swap.alpha("radice"), 0f)
  }

  @Test
  fun `lo scambio del titolo riparte da dove si trova`() {
    val swap = FluidBarTitleSwap(blurAvailable = true)
    swap.retarget("radice", step = 0, snap = false)
    swap.retarget("figlia", step = 1, snap = false)
    swap.advance(200.0)
    val alpha = swap.alpha("figlia")
    val blur = swap.blur("figlia")
    val step = swap.step("figlia")
    // Un pop a meta' del push: il titolo che stava arrivando se ne va da dove e', senza salti.
    swap.retarget("radice", step = -1, snap = false)
    assertEquals(alpha, swap.alpha("figlia"), 0.001f)
    assertEquals(blur, swap.blur("figlia"), 0.001f)
    assertEquals(step, swap.step("figlia"), 0.001f)
    swap.advance(700.0)
    assertTrue(swap.finished)
    swap.settle()
    assertEquals(1f, swap.alpha("radice"), 0f)
    assertEquals(0f, swap.alpha("figlia"), 0f)
  }

  @Test
  fun `col movimento ridotto, o nella variante che segue il dito, il titolo cambia subito`() {
    val swap = FluidBarTitleSwap(blurAvailable = true)
    swap.retarget("radice", step = 0, snap = false)
    swap.retarget("figlia", step = 1, snap = true)
    assertEquals(1f, swap.alpha("figlia"), 0f)
    assertEquals(0f, swap.blur("figlia"), 0f)
    assertEquals(0f, swap.step("figlia"), 0f)
    assertEquals(0f, swap.alpha("radice"), 0f)
    assertTrue(swap.finished)
  }

  // --- Gli inset del titolo ------------------------------------------------------------------------------------------

  @Test
  fun `gli inset del titolo sono la riga di sempre, la misura d'arrivo del modello, o il piu' largo dei due`() {
    val controls = FluidTopBarControls()
    val insets = FluidBarInsets(controls)
    insets.rowStart(120)
    insets.rowEnd(200)
    assertEquals("le pagine di sempre: la misura della riga, com'era", 120, controls.startPx)
    assertEquals(200, controls.endPx)
    insets.model = true
    insets.modelExtent(FluidToolbarExtent(startPx = 60, endPx = 150))
    assertEquals("col modello: la misura d'arrivo dei gruppi, non il vetro in volo", 60, controls.startPx)
    assertEquals(150, controls.endPx)
    insets.legacyRows = true
    insets.push()
    assertEquals("con una pagina di sempre accanto: il lato piu' largo", 200, controls.endPx)
  }

  // --- Il banco dello stato dell'host --------------------------------------------------------------------------------

  /** Lo stato vero dell'host con la sua molla, guidato a fotogrammi da 8 ms; la pila la cambia [onBack] come un'app. */
  private class Rig : Closeable {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
    private var nanos = 0L
    var requested: List<Any> = emptyList()
      private set
    val state = FluidPageHostState().apply {
      this.scope = this@Rig.scope
      width = 1000f
      layoutDirectionSign = 1
      onBack = { stack(requested.dropLast(1)) }
    }

    init {
      stack(listOf("radice"))
    }

    val barPage: Any? get() = fluidPageBarPage(state.interactionPage, state.upper)

    fun stack(pages: List<Any>) {
      requested = pages
      state.onStack(pages)
      pages.lastOrNull()?.let(state::pagePlaced)
    }

    fun tick(step: Long = 8_333_333L) {
      nanos += step
      clock.sendFrame(nanos)
    }

    fun untilRest(each: () -> Unit) {
      repeat(600) {
        if (!state.isMoving) return
        each()
        tick()
      }
      throw AssertionError("la molla non si e' fermata")
    }

    fun atRestOn(pages: List<Any>) {
      stack(pages)
      untilRest { }
    }

    override fun close() {
      scope.cancel()
    }
  }
}
