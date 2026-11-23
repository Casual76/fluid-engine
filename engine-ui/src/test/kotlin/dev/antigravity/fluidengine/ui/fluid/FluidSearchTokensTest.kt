package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarBar
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarModel
import dev.antigravity.fluidengine.ui.fluid.toolbar.ToolbarSearch
import dev.antigravity.fluidengine.ui.fluid.toolbar.ToolbarSearchKeyFace
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidBottomBarMetrics
import dev.antigravity.fluidengine.ui.fluid.toolbar.toolbarSearchCommandTargets
import dev.antigravity.fluidengine.ui.fluid.toolbar.toolbarSearchFreeEndDp
import dev.antigravity.fluidengine.ui.fluid.toolbar.toolbarSearchKeyFace
import dev.antigravity.fluidengine.ui.fluid.toolbar.toolbarSearchTokenRoomDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6B.8b · Il modello dei gettoni della ricerca e i bersagli da 48 dp, al JVM.
 *
 * - **I gettoni**: la pillola scelta diventa gettone in fondo (in `Single` prende il posto dell'altro della stessa fila);
 *   il gettone si toglie toccandolo o col secondo «cancella» a testo vuoto (il primo lo sceglie); la fila si richiude e si
 *   riapre al posto dichiarato; i primi gettoni si raccolgono in «+N» quando col testo non ci stanno; il disegno in volo è
 *   tutto vetro o tutto gettone fuori dalla sua finestra; il palco fa nascere al posto (R7) il gettone senza pillola.
 * - **I bersagli**: la regola dei bersagli disgiunti di una fila di comandi; i comandi del campo della barra a 360 dp.
 */
class FluidSearchTokensTest {
  private val anna = FluidToken("anna", "Anna")
  private val luca = FluidToken("luca", "Luca")
  private val marta = FluidToken("marta", "Marta")
  private val people = FluidFilterPresets.tokens(listOf(
    FluidFilterValue("anna", "Anna"), FluidFilterValue("luca", "Luca"), FluidFilterValue("marta", "Marta"),
    FluidFilterValue("giorgio", "Giorgio"),
  ))

  private fun filter(id: String) = people.filters.first { it.id == id }

  // --- Il modello -----------------------------------------------------------------------------------------------------

  @Test fun aChosenPillBecomesATokenAtTheEnd() {
    val one = FluidSearchTokenRules.choose(emptyList(), filter("luca"), people)
    assertEquals(listOf(luca), one)
    val two = FluidSearchTokenRules.choose(one, filter("anna"), people)
    assertEquals(listOf(luca, anna), two)
    // Una pillola che è già gettone non cambia niente.
    assertSame(two, FluidSearchTokenRules.choose(two, filter("luca"), people))
  }

  @Test fun inSingleModeTheTokenOfTheSameRowTakesThePlaceOfTheOther() {
    val single = people.copy(mode = FluidFilterMode.Single)
    val other = FluidToken("bozze", "Bozze")
    val start = listOf(other, luca)
    assertEquals(listOf(other, anna), FluidSearchTokenRules.choose(start, filter("anna"), single))
  }

  @Test fun aTappedTokenLeavesAndItsPillComesBackInTheRowAtItsDeclaredPlace() {
    val tokens = listOf(luca, anna)
    assertEquals(listOf("marta", "giorgio"), FluidSearchTokenRules.pills(people, tokens).map { it.id })
    val after = FluidSearchTokenRules.remove(tokens, "luca")
    assertEquals(listOf(anna), after)
    // La fila si riapre al posto dichiarato: Luca torna fra Anna (ancora gettone) e Marta, non in fondo.
    assertEquals(listOf("luca", "marta", "giorgio"), FluidSearchTokenRules.pills(people, after).map { it.id })
    assertEquals(people.filters.map { it.id }, FluidSearchTokenRules.pills(people, emptyList()).map { it.id })
    assertTrue(FluidSearchTokenRules.pills(null, tokens).isEmpty())
  }

  @Test fun backspaceAtTheStartFirstSelectsTheLastTokenThenRemovesIt() {
    val tokens = listOf(luca, anna)
    val first = FluidSearchTokenRules.backspace(tokens, null)
    assertEquals(tokens, first.tokens)
    assertEquals("anna", first.selected)
    assertFalse(first.changed)
    val second = FluidSearchTokenRules.backspace(first.tokens, first.selected)
    assertEquals(listOf(luca), second.tokens)
    assertNull(second.selected)
    assertTrue(second.changed)
    // Poi tocca a Luca, di nuovo in due colpi.
    val third = FluidSearchTokenRules.backspace(second.tokens, second.selected)
    assertEquals("luca", third.selected)
    val fourth = FluidSearchTokenRules.backspace(third.tokens, third.selected)
    assertTrue(fourth.tokens.isEmpty())
    // Senza gettoni non succede niente.
    val none = FluidSearchTokenRules.backspace(emptyList(), null)
    assertFalse(none.changed)
    assertNull(none.selected)
  }

  @Test fun theFirstTokensGatherInACountWhenTheyDoNotFitWithTheText() {
    val widths = listOf(53f, 51f, 60f)
    val count = { _: Int -> 36f }
    // Tre gettoni e quattro spazi: 164 + 8 = 172 dp.
    assertEquals(0, FluidSearchTokenRules.gathered(widths, 178f, 4f, count))
    assertEquals(0, FluidSearchTokenRules.gathered(widths, 172f, 4f, count))
    // Con meno posto i primi vanno in «+N», e l'ultimo (appena arrivato) resta sempre in vista.
    assertEquals(1, FluidSearchTokenRules.gathered(widths, 160f, 4f, count))
    assertEquals(2, FluidSearchTokenRules.gathered(widths, 120f, 4f, count))
    assertEquals(2, FluidSearchTokenRules.gathered(widths, 0f, 4f, count))
    assertEquals(0, FluidSearchTokenRules.gathered(emptyList(), 0f, 4f, count))
  }

  @Test fun changesSayWhoArrivedAndWhoLeft() {
    val c = FluidSearchTokenRules.changes(listOf("a", "b"), listOf("b", "c"))
    assertEquals(listOf("c"), c.added)
    assertEquals(listOf("a"), c.removed)
  }

  @Test fun theFlyingShapeIsAllGlassOrAllTokenOutsideItsWindow() {
    for (p in listOf(0f, 0.1f, 0.34f)) assertEquals(0f, FluidSearchTokenRules.mix(p, inward = true), 1e-6f)
    for (p in listOf(0.81f, 0.9f, 1f)) assertEquals(1f, FluidSearchTokenRules.mix(p, inward = true), 1e-6f)
    for (p in listOf(0f, 0.1f, 0.19f)) assertEquals(1f, FluidSearchTokenRules.mix(p, inward = false), 1e-6f)
    for (p in listOf(0.66f, 0.9f, 1f)) assertEquals(0f, FluidSearchTokenRules.mix(p, inward = false), 1e-6f)
    // Mai indietro: verso il campo sale, verso la fila scende.
    var last = 0f
    for (i in 0..100) {
      val m = FluidSearchTokenRules.mix(i / 100f, inward = true)
      assertTrue(m >= last - 1e-6f)
      last = m
    }
  }

  @Test fun theStageBringsATokenWithoutAPillInPlaceAndClosesTheSelectionOfALeftToken() {
    val stage = ToolbarSearchTokenStage()
    stage.sync(listOf(luca), people, animate = true, tokenHeightPx = 56f)
    assertTrue("il primo stato non anima", stage.flights.isEmpty() && stage.fades.isEmpty())
    // Nessuna pillola posata: Anna nasce al suo posto, sfocata (R7), il posto si apre.
    stage.sync(listOf(luca, anna), people, animate = true, tokenHeightPx = 56f)
    assertTrue(stage.flights.isEmpty())
    val fade = stage.fade("anna")!!
    assertTrue(fade.entering)
    assertEquals(0f, stage.tokenOpening("anna"), 1e-6f)
    assertEquals(1f, stage.tokenOpening("luca"), 1e-6f)
    stage.endFade(fade)
    assertEquals(1f, stage.tokenOpening("anna"), 1e-6f)
    // Il gettone scelto che se ne va non resta scelto.
    stage.selected = "anna"
    stage.sync(listOf(luca), people, animate = false, tokenHeightPx = 56f)
    assertNull(stage.selected)
    assertTrue(stage.leaving.isEmpty())
  }

  // --- Lo spec ---------------------------------------------------------------------------------------------------------

  @Test fun theSpecCarriesTokensAndSuggestedTokensWithDefaultsThatChangeNothing() {
    val plain = FluidSearchSpec("lago", {})
    assertTrue(plain.tokens.isEmpty())
    assertNull(plain.onTokensChange)
    assertNull(plain.filters)
    var got: List<FluidToken>? = null
    val spec = FluidSearchSpec.search("", {}, tokens = listOf(luca), onTokensChange = { got = it }, filters = people)
    assertEquals(listOf(luca), spec.tokens)
    assertSame(people, spec.filters)
    spec.onTokensChange!!.invoke(emptyList())
    assertEquals(emptyList<FluidToken>(), got)
    assertThrows(IllegalArgumentException::class.java) { FluidSearchSpec("", {}, tokens = listOf(luca, luca)) }
  }

  @Test fun theTokensPresetIsAPersonPerValue() {
    assertEquals(FluidFilterMode.Multiple, people.mode)
    assertEquals(listOf("Anna", "Luca", "Marta", "Giorgio"), people.filters.map { it.label })
    assertTrue(people.filters.all { it.icon == null && !it.hasValues })
    assertEquals(FluidToken("luca", "Luca"), FluidSearchTokenRules.tokenOf(filter("luca")))
  }

  // --- I bersagli -------------------------------------------------------------------------------------------------------

  private fun assertDisjointAndContaining(glyphs: List<ClosedFloatingPointRange<Float>>, targets: List<ClosedFloatingPointRange<Float>>) {
    for (i in targets.indices) {
      assertTrue("$i contiene il disegno: ${targets[i]} ⊇ ${glyphs[i]}",
        targets[i].start <= glyphs[i].start + 1e-3f && targets[i].endInclusive >= glyphs[i].endInclusive - 1e-3f)
      if (i > 0) assertTrue("$i non tocca il vicino: ${targets[i - 1]} ${targets[i]}", targets[i].start >= targets[i - 1].endInclusive - 1e-3f)
    }
  }

  @Test fun threeFortyDpCommandsGetFortyEightDpTargetsThatDoNotTouch() {
    // Le frecce e la X di «trova» alla fine di un campo da 328 dp: icone da 22 in posti da 40.
    val glyphs = listOf(217f..239f, 257f..279f, 297f..319f)
    val targets = fluidDisjointTargets(glyphs, 48f, 184f, 344f)
    assertDisjointAndContaining(glyphs, targets)
    for (t in targets) assertEquals(48f, t.endInclusive - t.start, 1e-3f)
    // Il primo prende dal contatore, l'ultimo esce dal vetro verso il margine.
    assertTrue(targets.first().start < 208f)
    assertTrue(targets.last().endInclusive > 328f && targets.last().endInclusive <= 344f)
  }

  @Test fun aLonelyCommandGetsItsTargetCentred() {
    val targets = fluidDisjointTargets(listOf(297f..319f), 48f, 0f, 400f)
    assertEquals(284f, targets.single().start, 1e-3f)
    assertEquals(332f, targets.single().endInclusive, 1e-3f)
  }

  @Test fun withoutRoomTheSpaceBetweenTwoDrawingsIsSplitInHalf() {
    val glyphs = listOf(10f..32f, 50f..72f)
    val targets = fluidDisjointTargets(glyphs, 48f, 0f, 80f)
    assertDisjointAndContaining(glyphs, targets)
    assertEquals(41f, targets[0].endInclusive, 1e-3f)
    assertEquals(41f, targets[1].start, 1e-3f)
  }

  @Test fun theOutsetOfATargetIsItsDistanceFromItsCell() {
    val o = fluidTargetOutsetOf(248f..288f, 244f..292f, 40f)
    assertEquals(4f, o.start.value, 1e-3f)
    assertEquals(4f, o.end.value, 1e-3f)
    assertEquals(4f, o.top.value, 1e-3f)
    assertEquals(4f, o.bottom.value, 1e-3f)
    val shrunk = fluidTargetOutsetOf(288f..328f, 292f..340f, 40f)
    assertEquals(-4f, shrunk.start.value, 1e-3f)
    assertEquals(12f, shrunk.end.value, 1e-3f)
  }

  /** I comandi del campo della barra in basso a [width] dp: i loro bersagli, nelle coordinate del campo. */
  private fun fieldTargets(spec: FluidSearchSpec, width: Float): List<Pair<String, ClosedFloatingPointRange<Float>>> {
    val search = ToolbarSearch(spec, FluidToolbarBar.Bottom, true)
    val pose = search.layout(FluidToolbarModel(emptyList()), width, fluidBottomBarMetrics())
    val face = toolbarSearchKeyFace(spec)
    val targets = toolbarSearchCommandTargets(spec, pose, face, toolbarSearchFreeEndDp(pose))
    val a = FluidSearchFieldDefaults.ActionSize.value
    val c = FluidSearchFieldDefaults.ClearSize.value
    val w = pose.width
    val out = ArrayList<Pair<String, ClosedFloatingPointRange<Float>>>()
    fun add(name: String, cellEnd: Float, cell: Float, outset: FluidTargetOutset?) {
      val o = outset ?: FluidTargetOutset.None
      out += name to (cellEnd - cell - o.start.value)..(cellEnd + o.end.value)
      assertEquals("$name: alto 48", 48f, cell + o.top.value + o.bottom.value, 1e-3f)
    }
    var end = w - dev.antigravity.fluidengine.ui.fluid.toolbar.ToolbarSearchTrailingInset.value
    if (face != null) { add("tasto", end, a, targets.key); end -= a }
    if (targets.next != null) { add("successivo", end, a, targets.next); end -= a }
    if (targets.previous != null) { add("precedente", end, a, targets.previous); end -= a }
    if (targets.clear != null && targets.previous == null) add("cancella", end, c, targets.clear)
    return out.reversed()
  }

  @Test fun theFieldCommandsAreFortyEightDpAndDisjointAtThreeSixtyAndFourTwelve() {
    for (width in listOf(360f, 412f)) {
      val find = FluidSearchSpec.findInPage("lago", {}, FluidSearchNavigation(0, 3, {}, {}), active = true, onActiveChange = {})
      val search = FluidSearchSpec.search("lago", {}, active = true, onActiveChange = {})
      val message = FluidSearchSpec.message("Ciao", {}, onSend = {}, active = true, onActiveChange = {})
      for ((name, spec) in listOf("trova" to find, "cerca" to search, "messaggio" to message)) {
        val targets = fieldTargets(spec, width)
        assertTrue("$name a $width: comandi ${targets.map { it.first }}", targets.isNotEmpty())
        for ((label, t) in targets) assertTrue("$name a $width: «$label» largo ${t.endInclusive - t.start}",
          t.endInclusive - t.start >= 47.9f)
        for (i in 1 until targets.size) assertTrue("$name a $width: si toccano ${targets[i - 1]} ${targets[i]}",
          targets[i].second.start >= targets[i - 1].second.endInclusive - 1e-3f)
        // L'ultimo non esce dallo schermo.
        val pose = ToolbarSearch(spec, FluidToolbarBar.Bottom, true).layout(FluidToolbarModel(emptyList()), width, fluidBottomBarMetrics())
        assertTrue("$name a $width: dentro lo schermo", pose.left + targets.last().second.endInclusive <= width + 1e-3f)
      }
    }
  }

  @Test fun atThreeSixtyThreeShortTokensAndTheTextFitInTheEmptyField() {
    val spec = FluidSearchSpec.search("", {}, active = true, onActiveChange = {}, tokens = listOf(anna, luca, marta), filters = people)
    val pose = ToolbarSearch(spec, FluidToolbarBar.Bottom, true).layout(FluidToolbarModel(emptyList()), 360f, fluidBottomBarMetrics())
    val targets = toolbarSearchCommandTargets(spec, pose, toolbarSearchKeyFace(spec), toolbarSearchFreeEndDp(pose))
    val room = toolbarSearchTokenRoomDp(spec, pose, targets)
    // Tre nomi corti («Anna», «Luca», «Marta»: ≈51, 50 e 58 dp con i margini) e i due spazi: ci stanno, col testo che resta.
    assertTrue("spazio dei gettoni a 360 dp: $room", room >= 51f + 50f + 58f + 2 * 4f - 2f)
    assertEquals(ToolbarSearchKeyFace.Close, toolbarSearchKeyFace(spec))
  }
}
