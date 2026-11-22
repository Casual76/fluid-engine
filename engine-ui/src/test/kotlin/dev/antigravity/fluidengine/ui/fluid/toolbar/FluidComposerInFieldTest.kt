package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.input.ImeAction
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.*
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

/**
 * F6B.7: il composer dentro il campo. Il campo che cresce di riga in riga col bordo dalla parte della tastiera fermo, lo
 * scorrimento dentro oltre `maxLines`, il vassoio degli allegati, il tasto a destra a stati senza doppio invio, la riga
 * della voce e lo stato dopo la ricreazione. Le tracce del banco sono in [ComposerTraces] ([ComposerTracesTest]).
 */
internal class FluidComposerInFieldTest {
  private val log = mutableListOf<String>()
  private val density = 2f
  private fun px(dp: Float) = (dp * density).roundToInt()

  /** Le misure del campo: la riga di 40 dp, i margini di 8 e la riga di testo di `bodyLarge` (17/23 sp, `FluidType`). */
  private val rowPx = px(FluidTextFieldDefaults.MinHeight.value)
  private val padPx = px(FluidTextFieldVerticalPadding.value)
  private val linePx = px(23f)

  private fun extra(lines: Int, maxLines: Int = 5, trayPx: Int = 0) =
    fluidSearchFieldExtraPx(fluidSearchFieldContentPx(lines, maxLines, linePx, rowPx, padPx, trayPx), rowPx, compact = false)

  private val message = FluidSearchSpec.message("", {}, onSend = { log += "invia" }, onActiveChange = {})

  private fun pose(spec: FluidSearchSpec) =
    ToolbarSearch(spec, FluidToolbarBar.Bottom, true).layout(FluidToolbarModel(emptyList()), 400f, fluidBottomBarMetrics())

  // --- Le righe -----------------------------------------------------------------------------------------------------

  @Test fun oneTwoAndManyLinesGrowUpwardWithTheBottomEdgeStill() {
    // Cresce chi ha più righe o il composer; la ricerca e «trova», di una riga, restano il campo di F6B.6.
    assertTrue(fluidSearchGrows(message))
    assertTrue(fluidSearchGrows(FluidSearchSpec.note("", {})))
    assertTrue(fluidSearchGrows(FluidSearchSpec("", {}, composer = FluidComposerSlots())))
    assertFalse(fluidSearchGrows(FluidSearchSpec.search("", {})))
    assertFalse(fluidSearchGrows(FluidSearchSpec("", {})))
    assertFalse(fluidSearchGrows(FluidSearchSpec.findInPage("", {}, FluidSearchNavigation(0, 0, {}, {}), true, {})))
    // Una riga: il campo di 40 dp, niente in più.
    assertEquals(rowPx, fluidSearchFieldContentPx(1, 5, linePx, rowPx, padPx))
    assertEquals(0, extra(1))
    // Due righe: i due margini e due righe di testo, meno la riga del campo. Ogni riga in più aggiunge una riga.
    assertEquals(2 * padPx + 2 * linePx - rowPx, extra(2))
    for (lines in 3..5) assertEquals(linePx, extra(lines) - extra(lines - 1))
    // Oltre `maxLines` il campo non cresce più: il testo scorre dentro.
    assertEquals(extra(5), extra(6))
    assertEquals(extra(5), extra(40))
    assertEquals(extra(6, maxLines = 6), extra(9, maxLines = 6))
    // Il bordo basso resta sopra la tastiera: sale solo quello alto. In alto (il campo che resta in cima) il contrario.
    for (lines in 1..8) {
      val up = toolbarSearchGrowthPlace(rowPx, extra(lines), upward = true)
      assertEquals(rowPx, up.bottom)
      assertEquals(-extra(lines), up.top)
      val down = toolbarSearchGrowthPlace(rowPx, extra(lines), upward = false)
      assertEquals(0, down.top)
      assertEquals(rowPx + extra(lines), down.bottom)
    }
    // Chiuso in lente non cresce, e un'altezza negativa non esiste.
    assertEquals(0, fluidSearchFieldExtraPx(fluidSearchFieldContentPx(3, 5, linePx, rowPx, padPx), rowPx, compact = true))
    assertEquals(toolbarSearchGrowthPlace(rowPx, 0, true), toolbarSearchGrowthPlace(rowPx, -12, true))
  }

  @Test fun aLineMoreWhileTypingIsFollowedWithoutAJourneyWhileOpeningStillTravels() {
    // Una riga in più o in meno mentre si scrive: la molla, e il piano segue senza viaggio.
    assertEquals(ToolbarSearchGrowthMove.Follow, toolbarSearchGrowthMove(0f, 44f, poseChanged = false, settled = true))
    assertEquals(ToolbarSearchGrowthMove.Follow, toolbarSearchGrowthMove(90f, 44f, poseChanged = false, settled = true))
    assertEquals(ToolbarSearchGrowthMove.Stay, toolbarSearchGrowthMove(44f, 44f, poseChanged = true, settled = true))
    // Aprire, chiudere, cambiare larghezza: l'altezza nuova viaggia col piano sulla curva della ricerca.
    assertEquals(ToolbarSearchGrowthMove.Jump, toolbarSearchGrowthMove(44f, 0f, poseChanged = true, settled = true))
    // Il piano: con `FluidGlassAnimation.None` (il moto nullo) il vetro più alto è al posto nuovo nel fotogramma dopo, e
    // nessun viaggio parte; con la curva della ricerca lo stesso cambio sarebbe un viaggio.
    fun plane(extraPx: Float) = GlassPlaneState(
      listOf(GlassPlanePiece("campo", Rect(32f, 4f - extraPx, 768f, 84f), FluidCornerRadii.all(40f), id = "campo")),
      FluidGlassContainerDefaults.Spacing.value * density, Rect(0f, 0f, 800f, 96f),
    )
    val follows = FluidGlassPlaneDriver()
    follows.step(0L, plane(0f), null)
    for ((frame, grown) in listOf(10f, 26f, 40f, 44f).withIndex()) {
      assertNotEquals(FluidGlassPlaneDriver.Change.Started, follows.step((frame + 1) * 8_333_333L, plane(grown), null))
      assertNull(follows.journey)
      assertEquals(4f - grown, follows.rest!!.pieces.single().bounds.top, 0f)
    }
    val travels = FluidGlassPlaneDriver()
    val curve = GlassPlaneMotion(spec = ToolbarSearchTiming)
    travels.step(0L, plane(0f), curve)
    assertEquals(FluidGlassPlaneDriver.Change.Started, travels.step(8_333_333L, plane(44f), curve))
    // La curva della ricerca è quella di F4B.2, la stessa di prima a riposo.
    assertTrue(ToolbarSearchCurve is FluidGlassAnimation.Custom)
    assertSame(ToolbarSearchTiming, (ToolbarSearchCurve as FluidGlassAnimation.Custom).spec)
  }

  // --- Gli allegati -------------------------------------------------------------------------------------------------

  @Test fun attachmentsOpenTheTrayUpwardAndRemovingItClosesWithoutMovingTheAnchor() {
    // Il vassoio sta sopra il testo: il vetro cresce verso l'alto della sua altezza.
    val trayPx = px(6f + 32f)
    assertEquals(trayPx, extra(1, trayPx = trayPx))
    assertEquals(extra(3) + trayPx, extra(3, trayPx = trayPx))
    val open = toolbarSearchGrowthPlace(rowPx, extra(2, trayPx = trayPx), upward = true)
    assertEquals(rowPx, open.bottom)
    // Tolto l'allegato il vetro si richiude con la molla, senza viaggio, e il bordo basso non si sposta.
    assertEquals(ToolbarSearchGrowthMove.Follow,
      toolbarSearchGrowthMove(extra(2, trayPx = trayPx).toFloat(), extra(2).toFloat(), poseChanged = false, settled = true))
    assertEquals(open.bottom, toolbarSearchGrowthPlace(rowPx, extra(2), upward = true).bottom)
    // Chiuso in lente il vassoio non conta.
    assertEquals(0, fluidSearchFieldExtraPx(fluidSearchFieldContentPx(1, 5, linePx, rowPx, padPx, trayPx), rowPx, compact = true))
  }

  // --- Il tasto a destra a stati ------------------------------------------------------------------------------------

  @Test fun theRightKeyIsOneShapeWithDictationSendAndStop() {
    var voice = 0
    var stop = 0
    val mic = FluidSearchTrailing.voice({ voice++ })
    val composer = FluidComposerSlots(trailing = mic)
    val empty = FluidSearchSpec.message("", {}, onSend = { log += "invia" }, onActiveChange = {}, composer = composer)
    assertSame(mic, empty.trailing)
    assertSame(composer, empty.composer)
    assertEquals(ImeAction.Send, fluidSearchImeAction(empty))
    // Il microfono a campo vuoto, anche a riposo (come in Messaggi); «Invia» appena c'è del testo, sulla stessa sagoma.
    assertEquals(ToolbarSearchKeyFace.Voice, toolbarSearchKeyFace(empty))
    assertEquals(ToolbarSearchKeyFace.Voice, toolbarSearchKeyFace(empty.copy(active = true)))
    assertEquals(ToolbarSearchKeyFace.Voice, toolbarSearchKeyFace(empty.copy(query = "   ")))
    assertEquals(ToolbarSearchKeyFace.Send, toolbarSearchKeyFace(empty.copy(query = "Ciao")))
    assertEquals(ToolbarSearchKeyFace.Send, toolbarSearchKeyFace(empty.copy(query = "Ciao", active = true)))
    // La faccia di F6B.6 resta com'era: del microfono non dice niente, e «Invia» resta «Invia».
    assertNull(toolbarSearchTrailingFace(empty))
    assertEquals(ToolbarSearchTrailingFace.Send, toolbarSearchTrailingFace(empty.copy(query = "Ciao")))
    mic.onVoice!!.invoke()
    assertEquals(1, voice)
    // Lo stop: lo stop anche con del testo; la tastiera manda il testo (in coda), a campo vuoto niente.
    val busy = empty.copy(trailing = FluidSearchTrailing.stop({ stop++ }, "Ferma"))
    assertEquals(ToolbarSearchKeyFace.Stop, toolbarSearchKeyFace(busy))
    assertEquals(ToolbarSearchKeyFace.Stop, toolbarSearchKeyFace(busy.copy(query = "La prossima", active = true)))
    assertEquals(ImeAction.Send, fluidSearchImeAction(busy))
    fluidSearchSubmit(busy) { }
    assertTrue(log.isEmpty())
    fluidSearchSubmit(busy.copy(query = "La prossima")) { }
    assertEquals(listOf("invia"), log)
    busy.trailing.onStop!!.invoke()
    assertEquals(1, stop)
    assertEquals("Ferma", busy.trailing.stopLabel)
    // Il cerchio che svuota non c'è col tasto del composer; la X e «Invia» di F6B.6 restano com'erano.
    assertFalse(toolbarSearchFieldParts(empty.copy(active = true, query = "Ciao"), 360f).clear)
    assertNull(toolbarSearchTrailingFace(message))
    assertEquals(ToolbarSearchTrailingFace.Close, toolbarSearchTrailingFace(message.copy(active = true)))
    assertEquals(ToolbarSearchTrailingFace.Send, toolbarSearchTrailingFace(message.copy(active = true, query = "Ciao")))
    // I valori nel companion (3.11): uguali per valore, con un nome leggibile.
    assertEquals(FluidSearchTrailing.Send, FluidSearchTrailing.send(FluidActionState.Idle))
    assertNotEquals(FluidSearchTrailing.Send, FluidSearchTrailing.send(FluidActionState.Working()))
    assertNotEquals(FluidSearchTrailing.Clear, FluidSearchTrailing.Send)
    assertEquals("FluidSearchTrailing.Voice", mic.toString())
    assertEquals("FluidSearchTrailing.Clear", FluidSearchTrailing.Clear.toString())
  }

  @Test fun aDoubleTapOnSendSendsOnceAndDoesNotStartDictation() {
    var now = 0L
    val gate = FluidSearchSubmitGate({ now })
    // Il primo invio parte; il secondo tocco sullo stesso testo, prima che l'app svuoti il campo, no.
    assertTrue(gate.trySubmit("Ciao"))
    now = 40L
    assertFalse(gate.trySubmit("Ciao"))
    // Il tasto è diventato il microfono sotto il dito: non risponde finché la faccia nuova non si legge.
    assertFalse(gate.tapAllowed())
    // Un testo diverso passa subito.
    assertTrue(gate.trySubmit("Arrivo"))
    now = 40L + FluidSearchSubmitGateMs
    assertTrue(gate.tapAllowed())
    assertTrue(gate.trySubmit("Arrivo"))
    // La finestra è lo scambio sfocato fino alla faccia nuova piena: 300 ms × 1,15.
    assertEquals(345L, FluidSearchSubmitGateMs)
    // Con l'invio che lavora (F6B.3a) il campo non manda niente; dopo il fallimento si può riprovare.
    val sending = FluidSearchSpec.message("Ciao", {}, onSend = { log += "invia" },
      composer = FluidComposerSlots(trailing = FluidSearchTrailing.voice({}, FluidActionState.Working())))
    assertFalse(fluidSearchSubmittable(sending))
    fluidSearchSubmit(sending) { }
    assertTrue(log.isEmpty())
    assertTrue(fluidSearchSubmittable(sending.copy(trailing = FluidSearchTrailing.voice({}, FluidActionState.Failed))))
    assertFalse(fluidSearchSubmittable(message.copy(trailing = FluidSearchTrailing.send(FluidActionState.Working()), query = "x")))
    // La ricerca e «trova» non inviano testo: niente cancello, l'invio ripetuto passa al risultato dopo.
    assertFalse(fluidSearchSendsText(FluidSearchSpec.findInPage("lago", {}, FluidSearchNavigation(0, 3, {}, {}), true, {})))
    assertFalse(fluidSearchSendsText(FluidSearchSpec.search("lago", {})))
    assertTrue(fluidSearchSendsText(message))
  }

  // --- La riga della voce --------------------------------------------------------------------------------------------

  @Test fun theVoiceLineTakesThePlaceOfTheTextWhileListening() {
    val listening = FluidSearchSpec.message("", {}, onSend = {}, onActiveChange = {},
      composer = FluidComposerSlots(voiceLine = {}, trailing = FluidSearchTrailing.stop({}, "Smetti di ascoltare")))
    val open = pose(listening.copy(active = true))
    assertTrue(toolbarSearchShowsVoiceLine(listening.copy(active = true), open))
    // A riposo anche: la dettatura può partire dal microfono a riposo.
    assertTrue(toolbarSearchShowsVoiceLine(listening, pose(listening)))
    // Senza la riga, o chiuso in lente, il testo.
    assertFalse(toolbarSearchShowsVoiceLine(message.copy(active = true), open))
    val lens = FluidSearchSpec.assistant("", {}, onAsk = {}, behavior = FluidSearchBehavior.CollapseToButton, onActiveChange = {},
      composer = FluidComposerSlots(voiceLine = {}))
    val compact = pose(lens)
    assertTrue(compact.compact)
    assertFalse(toolbarSearchShowsVoiceLine(lens, compact))
    // Mentre ascolta il tasto a destra è lo stop, sulla stessa sagoma del microfono.
    assertEquals(ToolbarSearchKeyFace.Stop, toolbarSearchKeyFace(listening))
  }

  // --- La rotazione ---------------------------------------------------------------------------------------------------

  @Test fun afterARotationTheDraftIsThereAtItsHeightWithoutGrowing() {
    // Il testo è dell'app (salvato con la pagina); il campo ricreato lo misura e si posa all'altezza giusta in un colpo.
    val growth = ToolbarSearchGrowth()
    assertEquals(0, growth.measures)
    assertFalse(growth.following)
    val draft = fluidSearchFieldContentPx(3, 5, linePx, rowPx, padPx)
    growth.report(draft, rowPx)
    assertEquals(1, growth.measures)
    val target = fluidSearchFieldExtraPx(growth.contentPx, growth.rowPx, compact = false).toFloat()
    assertEquals(extra(3).toFloat(), target)
    assertEquals(ToolbarSearchGrowthMove.Jump,
      toolbarSearchGrowthMove(growth.extra.targetValue, target, poseChanged = false, settled = growth.measures > 1))
    // La stessa misura non conta due volte; la riga dopo, scritta a mano, cresce con la molla.
    growth.report(draft, rowPx)
    assertEquals(1, growth.measures)
    growth.report(fluidSearchFieldContentPx(4, 5, linePx, rowPx, padPx), rowPx)
    assertEquals(2, growth.measures)
    assertEquals(ToolbarSearchGrowthMove.Follow, toolbarSearchGrowthMove(target, extra(4).toFloat(), false, growth.measures > 1))
    // La posa ruotata (più larga) è un cambio di posa: viaggia, non segue.
    val portrait = ToolbarSearchGrowthKey(false, false, true, 16f, 368f)
    val landscape = portrait.copy(width = 700f)
    assertNotEquals(portrait, landscape)
    assertEquals(ToolbarSearchGrowthMove.Jump, toolbarSearchGrowthMove(extra(4).toFloat(), extra(2).toFloat(), poseChanged = true, settled = true))
  }
}
