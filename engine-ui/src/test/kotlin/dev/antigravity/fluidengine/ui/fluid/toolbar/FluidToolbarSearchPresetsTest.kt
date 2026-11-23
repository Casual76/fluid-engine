package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.*
import dev.antigravity.fluidengine.ui.glass.field.trace.*
import java.util.Locale
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

/**
 * F6B.6: i preset d'uso del campo, «trova nella pagina», il campo in alto che inghiotte i tasti e [FluidFindState].
 * Le tracce del banco (`campo-messaggio`, `campo-alto-inghiotte`, `trova-sopra-tastiera`) sono in
 * [FluidToolbarSearchPresetTraces], che si attiva con `GLASS_F6B6_DIR`.
 */
internal class FluidToolbarSearchPresetsTest {
  private val log = mutableListOf<String>()

  private fun nav(current: Int, total: Int) = FluidSearchNavigation(current, total, { log += "su" }, { log += "giù" })

  // --- I cinque preset ---------------------------------------------------------------------------------------------

  /** Scopo, icona, tasto a destra, invio e righe di ogni preset: la tabella del brief di F6B.6. */
  private data class Expect(
    val purpose: FluidSearchPurpose, val icon: String, val trailing: FluidSearchTrailing, val ime: ImeAction,
    val submit: FluidSearchSubmit, val maxLines: Int, val prompt: String?,
  )

  @Test fun everyPresetDeclaresPurposeTrailingKeyAndSubmit() {
    val presets = mapOf(
      "ricerca" to FluidSearchSpec.search("", {}, onSearch = { log += "ricerca" }, onActiveChange = {}),
      "messaggio" to FluidSearchSpec.message("", {}, onSend = { log += "messaggio" }),
      "nota" to FluidSearchSpec.note("", {}, onDone = { log += "nota" }),
      "assistente" to FluidSearchSpec.assistant("", {}, onAsk = { log += "assistente" }),
      "trova" to FluidSearchSpec.findInPage("", {}, nav(0, 0), active = true, onActiveChange = {}),
    )
    val expected = mapOf(
      "ricerca" to Expect(FluidSearchPurpose.Search, FluidIcons.Search.name, FluidSearchTrailing.Clear, ImeAction.Search,
        FluidSearchSubmit.Close, 1, null),
      "messaggio" to Expect(FluidSearchPurpose.Input, FluidIcons.Forum.name, FluidSearchTrailing.Send, ImeAction.Send,
        FluidSearchSubmit.StayOpen, 5, "Messaggio"),
      "nota" to Expect(FluidSearchPurpose.Input, FluidIcons.Edit.name, FluidSearchTrailing.Clear, ImeAction.Done,
        FluidSearchSubmit.Close, 4, "Nota"),
      "assistente" to Expect(FluidSearchPurpose.Input, FluidIcons.AutoAwesome.name, FluidSearchTrailing.Send, ImeAction.Send,
        FluidSearchSubmit.StayOpen, 6, "Chiedi all'assistente"),
      "trova" to Expect(FluidSearchPurpose.Search, FluidIcons.Search.name, FluidSearchTrailing.Clear, ImeAction.Search,
        FluidSearchSubmit.StayOpen, 1, "Trova nella pagina"),
    )
    for ((name, spec) in presets) {
      val want = expected.getValue(name)
      assertEquals(name, want.purpose, spec.purpose)
      assertEquals(name, want.icon, fluidSearchIcon(spec).name)
      assertEquals(name, want.trailing, spec.trailing)
      assertEquals(name, want.ime, fluidSearchImeAction(spec))
      assertEquals(name, want.submit, spec.onSubmit)
      assertEquals(name, want.submit == FluidSearchSubmit.Close, fluidSearchClosesOnSubmit(spec))
      assertEquals(name, want.maxLines, spec.maxLines)
      assertEquals(name, want.prompt, spec.prompt)
    }
    // Solo «trova» non c'è a riposo; i campi di scrittura salgono sopra la tastiera.
    assertEquals(listOf("trova"), presets.filterValues { !it.showsAtRest }.keys.toList())
    for (name in listOf("messaggio", "nota", "assistente", "trova")) {
      assertEquals(name, FluidSearchExpansion.AboveKeyboard, presets.getValue(name).expansion)
    }
    assertEquals(FluidSearchPlacement.BottomBar, presets.getValue("messaggio").placement)
    assertEquals(FluidSearchBehavior.AlwaysVisible, presets.getValue("messaggio").behavior)
  }

  @Test fun searchConfirmsAndClosesKeepingTheWord() {
    var closed = 0
    val spec = FluidSearchSpec.search("lago", {}, onSearch = { log += "cerca" }, active = true, onActiveChange = {})
    fluidSearchSubmit(spec) { closed++ }
    assertEquals(listOf("cerca"), log)
    assertEquals(1, closed)
    // Senza onSearch l'invio chiude e basta.
    fluidSearchSubmit(spec.copy(onSearch = null)) { closed++ }
    assertEquals(2, closed)
  }

  @Test fun messageSendsAndStaysOpenAndNeverSendsAnEmptyText() {
    var closed = 0
    val empty = FluidSearchSpec.message("", {}, onSend = { log += "invia" }, active = true, onActiveChange = {})
    fluidSearchSubmit(empty) { closed++ }
    fluidSearchSubmit(empty.copy(query = "   ")) { closed++ }
    assertTrue(log.isEmpty())
    assertEquals(ToolbarSearchTrailingFace.Close, toolbarSearchTrailingFace(empty))
    assertEquals(ToolbarSearchTrailingFace.Close, toolbarSearchTrailingFace(empty.copy(query = "   ")))
    val written = empty.copy(query = "Ciao")
    assertEquals(ToolbarSearchTrailingFace.Send, toolbarSearchTrailingFace(written))
    fluidSearchSubmit(written) { closed++ }
    assertEquals(listOf("invia"), log)
    assertEquals(0, closed)
    // A campo chiuso il tasto a destra non c'è.
    assertNull(toolbarSearchTrailingFace(written.copy(active = false)))
    // Il cerchio che svuota non c'è col tasto che invia.
    assertFalse(toolbarSearchFieldParts(written, 360f).clear)
  }

  @Test fun noteConfirmsAndClosesAssistantAsksAndStays() {
    var closed = 0
    fluidSearchSubmit(FluidSearchSpec.note("Comprare il pane", {}, onDone = { log += "nota" }, active = true)) { closed++ }
    assertEquals(1, closed)
    fluidSearchSubmit(FluidSearchSpec.assistant("Che tempo fa?", {}, onAsk = { log += "chiedi" }, active = true)) { closed++ }
    assertEquals(1, closed)
    assertEquals(listOf("nota", "chiedi"), log)
  }

  @Test fun theSpecBuiltByHandKeepsTheBehaviourOfBeforeF6b6() {
    val spec = FluidSearchSpec("", {})
    assertNull(spec.navigation)
    assertNull(spec.onSubmit)
    assertEquals(1, spec.maxLines)
    assertNull(spec.icon)
    assertEquals(FluidSearchTrailing.Clear, spec.trailing)
    assertTrue(spec.showsAtRest)
    assertEquals(FluidIcons.Search.name, fluidSearchIcon(spec).name)
    assertEquals(FluidIcons.Edit.name, fluidSearchIcon(spec.copy(purpose = FluidSearchPurpose.Input)).name)
    assertEquals(ImeAction.Search, fluidSearchImeAction(spec))
    assertEquals(ImeAction.Done, fluidSearchImeAction(spec.copy(purpose = FluidSearchPurpose.Input)))
    // Come `spec.onSearch?.invoke() ?: close()`: senza onSearch chiude, con onSearch la chiama e resta aperto.
    var closed = 0
    fluidSearchSubmit(spec) { closed++ }
    assertEquals(1, closed)
    fluidSearchSubmit(spec.copy(onSearch = { log += "conferma" })) { closed++ }
    assertEquals(1, closed)
    assertEquals(listOf("conferma"), log)
    assertEquals(true, fluidToolbarSearchTouchesBar(spec.copy(active = true)))
    assertThrows(IllegalArgumentException::class.java) { spec.copy(maxLines = 0) }
  }

  // --- «Trova nella pagina» ----------------------------------------------------------------------------------------

  @Test fun findCountsZeroOneAndManyResultsInALiveRegion() {
    val labels = FluidSearchLabels()
    assertNull(fluidSearchCounterSpeech("", nav(0, 0), labels))
    assertNull(fluidSearchCounterSpeech("  ", nav(0, 4), labels))
    val none = fluidSearchCounterSpeech("zzz", nav(0, 0), labels)!!
    assertEquals("Nessun risultato", none.text)
    assertEquals("Nessun risultato", none.spoken)
    val one = fluidSearchCounterSpeech("lago", nav(0, 1), labels)!!
    assertEquals("1/1", one.text)
    assertEquals("1 di 1", one.spoken)
    val many = fluidSearchCounterSpeech("lago", nav(2, 12), labels)!!
    assertEquals("3/12", many.text)
    assertEquals("3 di 12", many.spoken)
    // L'area viva fa leggere ogni cambio senza spostare il fuoco.
    for (speech in listOf(none, one, many)) assertEquals(LiveRegionMode.Polite, speech.liveRegion)
  }

  @Test fun findArrowsAndEnterWalkThroughTheResultsAndStayOpen() {
    val find = FluidFindState.of(AnnotatedString(Transcript))
    var active = true
    fun spec() = FluidSearchSpec.findInPage(find.query, find::onQueryChange, find.navigation, active, { active = it })
    spec().onQueryChange("lago")
    assertEquals(3, spec().navigation!!.total)
    assertEquals(0, spec().navigation!!.current)
    // L'invio passa alla successiva e il campo resta aperto.
    fluidSearchSubmit(spec()) { active = false }
    assertTrue(active)
    assertEquals(1, find.current)
    // Le frecce: su torna indietro e dalla prima va all'ultima; giù dall'ultima torna alla prima.
    spec().navigation!!.onPrevious()
    assertEquals(0, find.current)
    spec().navigation!!.onPrevious()
    assertEquals(2, find.current)
    spec().navigation!!.onNext()
    assertEquals(0, find.current)
    // Uno solo.
    spec().onQueryChange("Tahoe")
    assertEquals(1, find.total)
    assertEquals("1/1", fluidSearchCounterSpeech(find.query, spec().navigation!!, FluidSearchLabels())!!.text)
    spec().navigation!!.onNext()
    assertEquals(0, find.current)
    // Nessuno: frecce e invio non fanno niente, il contatore dice «Nessun risultato».
    spec().onQueryChange("zzz")
    assertEquals(0, find.total)
    val requests = find.scrollRequest
    fluidSearchSubmit(spec()) { active = false }
    spec().navigation!!.onPrevious()
    assertSame(requests, find.scrollRequest)
    assertTrue(active)
    assertEquals("Nessun risultato", fluidSearchCounterSpeech(find.query, spec().navigation!!, FluidSearchLabels())!!.text)
    // Le frecce stanno nel campo quando c'è posto: dopo la lente, il testo e la X.
    val open = spec().copy(query = "lago")
    assertTrue(toolbarSearchFieldParts(open, 368f).navigation)
    assertTrue(toolbarSearchFieldParts(open, 368f).clear)
    assertFalse(toolbarSearchFieldParts(open, 200f).navigation)
    assertFalse(toolbarSearchFieldParts(open.copy(active = false), 368f).navigation)
  }

  @Test fun findLeavesTheTopBarWhereItIs() {
    val spec = FluidSearchSpec.findInPage("lago", {}, nav(0, 3), active = true, onActiveChange = {})
    val search = ToolbarSearch(spec, searchBar(spec.placement, 400.dp)!!, true)
    assertEquals(FluidToolbarBar.Bottom, search.bar)
    assertFalse(search.takesTop)
    assertFalse(search.owns(FluidToolbarBar.Top))
    assertTrue(search.occupiesBottom)
    // La barra in alto, che legge questo valore, resta quella di riposo: il fissato non va nel trabocco.
    assertEquals(false, fluidToolbarSearchTouchesBar(spec))
    val top = FluidToolbarModel(listOf(
      FluidToolbarItem("back", placement = FluidToolbarPlacement.Leading),
      FluidToolbarItem("share"),
      FluidToolbarItem("avatar", placement = FluidToolbarPlacement.PinnedTrailing),
    ))
    val metrics = fluidToolbarMetrics()
    val rest = resolveFluidToolbar(top, 400f, false, metrics)
    assertEquals(rest, resolveFluidToolbar(top, 400f, fluidToolbarSearchTouchesBar(spec)!!, metrics))
    assertTrue(rest.overflow.isEmpty())
    // Con la ricerca di sempre attiva il fissato passa nel trabocco, come prima.
    val search2 = FluidSearchSpec("lago", {}, placement = FluidSearchPlacement.BottomBar, active = true)
    assertEquals(listOf("avatar"), resolveFluidToolbar(top, 400f, fluidToolbarSearchTouchesBar(search2)!!, metrics).overflow.map { it.id })
  }

  @Test fun findIsNotThereAtRestAndTheBottomRowIsThePagesOwn() {
    val metrics = fluidBottomBarMetrics()
    val bottom = FluidToolbarModel(listOf(
      FluidToolbarItem("a", placement = FluidToolbarPlacement.BottomBar),
      FluidToolbarItem("b", placement = FluidToolbarPlacement.BottomBar),
    ))
    val spec = FluidSearchSpec.findInPage("", {}, nav(0, 0), active = false, onActiveChange = {})
    val idle = ToolbarSearch(spec, FluidToolbarBar.Bottom, true).layout(bottom, 400f, metrics)
    assertTrue(idle.hidden)
    assertEquals(resolveFluidToolbar(bottom, 400f, false, metrics, FluidToolbarBar.Bottom), idle.neighbours)
    assertEquals(fluidToolbarExtent(idle.neighbours, 3f), idle.extent(3f))
    val open = ToolbarSearch(spec.copy(active = true), FluidToolbarBar.Bottom, true).layout(bottom, 400f, metrics)
    assertFalse(open.hidden)
    assertFalse(open.compact)
    // In basso i vicini si raccolgono in «Altro», come per la ricerca (F4B.2).
    assertEquals(listOf<Any>("a", "b"), open.neighbours.overflow.map { it.id })
    assertTrue(open.left >= 0f && open.left + open.width <= 400f)
  }

  @Test fun aFieldThatIsNotThereAtRestLeavesThroughAGhost() {
    val spec = FluidSearchSpec.findInPage("lago", {}, nav(0, 3), active = true, onActiveChange = {})
    val metrics = fluidBottomBarMetrics()
    val model = FluidToolbarModel(emptyList())
    val open = ToolbarSearch(spec, FluidToolbarBar.Bottom, true).layout(model, 400f, metrics)
    val hidden = ToolbarSearch(spec.copy(active = false), FluidToolbarBar.Bottom, true).layout(model, 400f, metrics)
    val leaving = ToolbarSearchLeaving()
    leaving.track(hidden, spec.copy(active = false), animate = true)
    assertNull(leaving.ghost)
    leaving.track(open, spec, animate = true)
    assertNull(leaving.ghost)
    leaving.track(hidden, spec.copy(active = false), animate = true)
    val ghost = leaving.ghost!!
    // Il fantasma ha l'ultima posa e la dichiarazione aperta: le stesse parole e gli stessi comandi.
    assertSame(open, ghost.pose)
    assertTrue(ghost.spec.active)
    leaving.track(hidden, spec.copy(active = false), animate = true)
    assertSame(ghost, leaving.ghost)
    leaving.finish(ghost)
    assertNull(leaving.ghost)
    // Riaperto durante l'uscita: il fantasma va via e il campo vero riprende.
    leaving.track(open, spec, animate = true)
    leaving.track(hidden, spec.copy(active = false), animate = true)
    assertNotNull(leaving.ghost)
    leaving.track(open, spec, animate = true)
    assertNull(leaving.ghost)
    // Con il movimento ridotto non c'è fantasma.
    leaving.track(hidden, spec.copy(active = false), animate = false)
    assertNull(leaving.ghost)
  }

  // --- Il campo in alto che inghiotte i tasti ---------------------------------------------------------------------

  private val topModel = FluidToolbarModel(listOf(
    FluidToolbarItem("menu", placement = FluidToolbarPlacement.Leading, symbol = "menu"),
    FluidToolbarItem("compose", symbol = "edit"),
    FluidToolbarItem("avatar", placement = FluidToolbarPlacement.PinnedTrailing, symbol = "person"),
  ))
  private val topSpec = FluidSearchSpec.search("", {}, placement = FluidSearchPlacement.TopBar,
    behavior = FluidSearchBehavior.CollapseToButton, expansion = FluidSearchExpansion.StayAtTop, onActiveChange = {})

  private fun topPose(active: Boolean, width: Float = 400f) =
    ToolbarSearch(topSpec.copy(active = active), FluidToolbarBar.Top, true).layout(topModel, width, fluidToolbarMetrics())

  /** Il piano della riga come lo registra la barra: i gruppi con l'identità della memoria e il campo. */
  private fun plane(pose: ToolbarSearchPose, ids: Map<FluidToolbarGroupId, FluidToolbarGlassId>): GlassPlaneState {
    val row = fluidToolbarMetrics().buttonWidthDp
    fun piece(key: String, x: Float, w: Float, h: Float) = GlassPlanePiece(key, Rect(x, 48f - h / 2, x + w, 48f + h / 2),
      FluidCornerRadii.all(h / 2), id = key)
    val field = piece("campo", pose.left, pose.width,
      if (pose.compact) FluidMetrics.GlassButtonLens.value else FluidSearchFieldDefaults.BarFieldHeight.value)
    val groups = pose.neighbours.groups.map { group -> piece(ids.getValue(group.id).toString(), group.leftDp, group.widthDp, row) }
    return GlassPlaneState(groups + field, FluidGlassContainerDefaults.Spacing.value, Rect(0f, 0f, 400f, 96f))
  }

  @Test fun atTheTopTheActiveFieldTakesTheWholeRowWithoutAnOverflowButton() {
    val rest = topPose(false)
    val open = topPose(true)
    assertTrue(rest.compact)
    assertEquals(3, rest.neighbours.groups.flatMap { it.items }.size)
    assertTrue(open.neighbours.groups.isEmpty())
    assertNull(open.neighbours.overflowElement)
    assertEquals(setOf<Any>("menu", "compose", "avatar"), open.neighbours.overflow.map { it.id }.toSet())
    assertEquals(400f - open.left, open.left + open.width, 0.001f)
    // Il campo aperto copre il posto di ogni vicino: ci rientrano dentro.
    for (group in rest.neighbours.groups) {
      assertTrue(group.leftDp >= open.left - 0.001f && group.leftDp + group.widthDp <= open.left + open.width + 0.001f)
    }
    // Il campo che scende sopra la tastiera porta i comandi in «Altro», come in basso (F4B.2).
    val travelling = ToolbarSearch(topSpec.copy(active = true, expansion = FluidSearchExpansion.AboveKeyboard), FluidToolbarBar.Top, true)
      .layout(topModel, 400f, fluidToolbarMetrics())
    assertNotNull(travelling.neighbours.overflowElement)
    // In basso resta «Altro»: il gruppo si raccoglie nel tondo (F4B.2).
    val bottom =ToolbarSearch(topSpec.copy(active = true), FluidToolbarBar.Bottom, true)
      .layout(FluidToolbarModel(topModel.items.map { it.copy(placement = FluidToolbarPlacement.BottomBar) }), 400f, fluidBottomBarMetrics())
    assertNotNull(bottom.neighbours.overflowElement)
  }

  @Test fun swallowedCommandsMergeIntoTheFieldAndAreBornFromItOnTheWayBack() {
    val rest = topPose(false)
    val open = topPose(true)
    val memory = FluidToolbarMemory()
    memory.update(rest.neighbours, FluidToolbarMotion.Full, searchActive = false)
    val before = plane(rest, memory.glassIds)
    val restIds = memory.glassIds.values.map { it.toString() }
    memory.update(open.neighbours, FluidToolbarMotion.Full, searchActive = true)
    // Le icone escono sull'orologio comune della ricerca; nessun «Altro» entra.
    assertEquals(3, memory.ghosts.count { it.searchSwap })
    assertTrue(memory.searchArrivals.isEmpty())
    assertTrue(memory.arrivals.isEmpty())
    val after = plane(open, memory.glassIds)
    assertEquals(listOf("campo"), after.pieces.map { it.key })
    val motion = GlassPlaneMotion(spec = ToolbarSearchTiming)
    val going = GlassPlaneJourney(before, after, motion).frame(1000.0 / 120)
    for (id in restIds) {
      val flight = going.pieces.single { it.flightId.sourceKey == id }
      assertEquals(id, GlassPlaneChange.MergeIntoNeighbour, flight.change)
      assertEquals(id, "campo", flight.mergesInto)
    }
    // Alla chiusura il viaggio inverso: le icone rinascono sull'orologio comune, i vetri dal campo.
    memory.update(rest.neighbours, FluidToolbarMotion.Full, searchActive = false)
    assertEquals(setOf<Any>("menu", "compose", "avatar"), memory.searchArrivals)
    val back = GlassPlaneJourney(after, plane(rest, memory.glassIds), motion).frame(1000.0 / 120)
    for (id in memory.glassIds.values.map { it.toString() }) {
      val flight = back.pieces.single { it.flightId.targetKey == id }
      assertEquals(id, GlassPlaneChange.BornFromNeighbour, flight.change)
      assertEquals(id, "campo", flight.bornFrom)
    }
  }

  @Test fun swallowedIconsBlurAtOnceAndAreGoneWithinAHundredMilliseconds() {
    // R4 B1 (b), per analogia: sfocati e spariti entro 67–100 ms. Lo stesso orologio di F4B.2 in basso.
    fun at(ms: Float) = ms / ToolbarSearchInkMillis
    assertTrue(toolbarSearchInkBlur(at(1000f / 120), false) > 0.2f)
    assertEquals(1f, toolbarSearchInkBlur(at(67f), false), 1e-4f)
    assertTrue(toolbarSearchInkAlpha(at(67f), false) < 0.5f)
    assertTrue(toolbarSearchInkAlpha(at(100f), false) < 0.06f)
    assertEquals(0f, toolbarSearchInkAlpha(at(117f), false), 1e-4f)
    // Al ritorno l'orologio va all'indietro: le icone sono di nuovo nitide alla fine.
    assertEquals(1f, toolbarSearchInkAlpha(0f, false), 0f)
    assertEquals(0f, toolbarSearchInkBlur(0f, false), 0f)
  }

  // --- rememberFluidFindState --------------------------------------------------------------------------------------

  @Test fun findStateOnPlainTextIgnoresCaseAndAccents() {
    // L0 a1 g2 o3 ,4 _5 l6 a7 g8 o9 _10 e11 _12 L13 À14 G15 O16 .17 _18 l19 a20 g21 h22 i23
    val state = FluidFindState.of(AnnotatedString("Lago, lago e LÀGO. laghi"))
    assertTrue(state.matches.isEmpty())
    assertNull(state.currentRange)
    state.onQueryChange("lago")
    assertEquals(listOf(TextRange(0, 4), TextRange(6, 10), TextRange(13, 17)), state.matches)
    assertEquals(0, state.current)
    assertEquals(TextRange(0, 4), state.scrollRequest!!.range)
    val first = state.scrollRequest!!.serial
    state.next()
    assertEquals(1, state.scrollRequest!!.index)
    state.next()
    state.next()
    assertEquals(0, state.current)
    assertEquals(first + 3, state.scrollRequest!!.serial)
    state.previous()
    assertEquals(2, state.current)
    assertEquals(TextRange(13, 17), state.currentRange)
    // Una lettera in meno: la corrente resta dov'era.
    state.onQueryChange("lag")
    assertEquals(4, state.total)
    assertEquals(2, state.current)
    // Nessuna: niente richiesta nuova.
    val last = state.scrollRequest
    state.onQueryChange("lagx")
    assertEquals(0, state.total)
    assertSame(last, state.scrollRequest)
    // Con le maiuscole o con gli accenti.
    assertEquals(1, FluidFindState.of(AnnotatedString("Lago, lago e LÀGO."), "lago", ignoreCase = false).total)
    assertEquals(2, FluidFindState.of(AnnotatedString("Lago, lago e LÀGO."), "lago", ignoreDiacritics = false).total)
    assertEquals("perche e", fluidFindFold("PERCHÉ È"))
    // Le occorrenze non si sovrappongono.
    assertEquals(listOf(TextRange(0, 2), TextRange(2, 4)), fluidFindMatches("aaaa", "aa"))
  }

  @Test fun findStateHighlightsTheCurrentAndTheOthersOnAnnotatedText() {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val text = buildAnnotatedString {
      withStyle(bold) { append("Lago") }
      append(", lago e LÀGO.")
    }
    val state = FluidFindState.of(text)
    state.onQueryChange("lago")
    state.next()
    val marks = FluidFindHighlights(SpanStyle(background = Color.Yellow), SpanStyle(background = Color.Gray))
    val out = state.highlight(marks)
    assertEquals(text.text, out.text)
    // Lo stile del testo resta, e sopra ci sono le tre occorrenze: la seconda è la corrente.
    assertTrue(out.spanStyles.any { it.item == bold && it.start == 0 && it.end == 4 })
    val found = out.spanStyles.filter { it.item.background != Color.Unspecified }.sortedBy { it.start }
    assertEquals(listOf(0, 6, 13), found.map { it.start })
    assertEquals(listOf(Color.Gray, Color.Yellow, Color.Gray), found.map { it.item.background })
    // Il testo che si accorcia: la corrente si ferma all'ultima e gli evidenziatori fuori dal testo si saltano.
    state.next()
    state.update(AnnotatedString("Lago."), ignoreCase = true, ignoreDiacritics = true)
    assertEquals(1, state.total)
    assertEquals(0, state.current)
    assertEquals(1, state.highlight(marks).spanStyles.size)
    assertEquals(1, fluidFindHighlight(AnnotatedString("Lago."), listOf(TextRange(0, 4), TextRange(13, 17)), 1, marks).spanStyles.size)
  }


  // --- Le tracce del banco -------------------------------------------------------------------------------------------

  @Test fun theSwallowTraceShowsTheCommandsGoingIntoTheField() {
    val scene = SearchPresetTraces.scenes().first { it.name == "campo-alto-inghiotte" }
    val trace = SearchPresetTraces.build(scene)
    fun at(ms: Double) = trace.frames.minBy { kotlin.math.abs(it.tMs - ms) }
    fun layers(ms: Double) = at(ms).elements.first { it["name"] == "contenuto" }.let { element ->
      @Suppress("UNCHECKED_CAST")
      (element["content"] as List<Map<String, Any?>>)
    }
    fun measure(ms: Double, key: String) = (at(ms).elements.first { it["name"] == "misure" }[key] as Number).toFloat()
    // Le icone dei tre vicini escono sfocate: a 100 ms sono sotto il 6%, e nessun «Altro» entra.
    assertEquals(3, layers(0.0).count { (it["name"] as String).startsWith("esce-") })
    assertTrue(layers(100.0).filter { (it["name"] as String).startsWith("esce-") }.all { (it["alpha"] as Number).toFloat() < 0.06f })
    assertTrue(layers(200.0).none { (it["name"] as String).startsWith("entra-") })
    // Il campo prende la riga e ci resta; alla chiusura torna lente e i tre tasti rinascono nei loro vetri.
    val open = topPose(true)
    assertEquals(open.width, measure(650.0, "campoLarghezza"), 0.5f)
    assertEquals(FluidMetrics.GlassButtonLens.value, measure(1490.0, "campoLarghezza"), 0.5f)
    assertEquals(3, layers(900.0).count { (it["name"] as String).startsWith("entra-") })
    assertEquals(3, layers(1490.0).count { (it["name"] as String).startsWith("tasto-") })
    // Alla chiusura la lente resta nel campo che si stringe; parola e X escono sfocate dal fantasma.
    val closing = layers(717.0)
    assertEquals(1, (closing.first { it["name"] == "campo" }["items"] as List<*>).size)
    assertTrue(closing.any { it["name"] == "fantasma" })
    assertTrue(layers(1000.0).none { it["name"] == "fantasma" })
    // La traccia «prima» (d475cbf): «Altro» entra a sinistra, e alla chiusura parola e X non hanno fantasma.
    val before = SearchPresetTraces.build(SearchPresetTraces.scenes().first { it.name == "campo-alto-inghiotte-prima" })
    fun beforeLayers(ms: Double) = before.frames.minBy { kotlin.math.abs(it.tMs - ms) }.elements.first { it["name"] == "contenuto" }.let {
      @Suppress("UNCHECKED_CAST")
      (it["content"] as List<Map<String, Any?>>)
    }
    assertTrue(beforeLayers(250.0).any { (it["name"] as String).startsWith("entra-") })
    assertTrue(beforeLayers(717.0).none { it["name"] == "fantasma" })
  }

  @Test fun aFieldThatClosesIntoALensLeavesItsWordsThroughAGhost() {
    val leaving = ToolbarSearchLeaving()
    val rest = topPose(false)
    val open = topPose(true)
    leaving.track(rest, topSpec, animate = true)
    leaving.track(open, topSpec.copy(active = true), animate = true)
    assertNull(leaving.ghost)
    leaving.track(rest, topSpec, animate = true)
    val ghost = leaving.ghost!!
    // La lente resta nel campo: il fantasma porta solo parola e tasti, dalla posa aperta.
    assertFalse(ghost.withLens)
    assertSame(open, ghost.pose)
    assertTrue(ghost.spec.active)
    leaving.track(rest, topSpec, animate = true)
    assertSame(ghost, leaving.ghost)
    // Riaperto durante l'uscita: il campo vero riprende.
    leaving.track(open, topSpec.copy(active = true), animate = true)
    assertNull(leaving.ghost)
    // Un campo che a riposo resta largo (il messaggio) chiude con lo scambio sfocato nel suo layout, senza fantasma.
    val message = FluidSearchSpec.message("", {}, onSend = {}, onActiveChange = {})
    val metrics = fluidBottomBarMetrics()
    val wide = ToolbarSearch(message, FluidToolbarBar.Bottom, true).layout(FluidToolbarModel(emptyList()), 400f, metrics)
    val typing = ToolbarSearch(message.copy(active = true), FluidToolbarBar.Bottom, true).layout(FluidToolbarModel(emptyList()), 400f, metrics)
    val other = ToolbarSearchLeaving()
    other.track(typing, message.copy(active = true), animate = true)
    other.track(wide, message, animate = true)
    assertFalse(wide.compact)
    assertNull(other.ghost)
  }

  @Test fun theMessageTraceNeverShowsTheHintUnderTheText() {
    val trace = SearchPresetTraces.build(SearchPresetTraces.scenes().first { it.name == "campo-messaggio" })
    fun layers(ms: Double) = trace.frames.minBy { kotlin.math.abs(it.tMs - ms) }.elements.first { it["name"] == "contenuto" }.let {
      @Suppress("UNCHECKED_CAST")
      (it["content"] as List<Map<String, Any?>>)
    }
    // Scritto il testo, il segnaposto non c'è più dal primo fotogramma; il tasto a destra passa da X a «Invia» sfocato.
    for (ms in listOf(608.0, 650.0, 700.0)) assertTrue(layers(ms).none { (it["name"] as String).startsWith("segnaposto") })
    assertEquals(2, layers(750.0).count { (it["name"] as String).startsWith("destra") })
    // Inviato, il testo si svuota: il segnaposto torna fermo e nitido, il campo resta aperto.
    val hint = layers(1008.0).single { (it["name"] as String).startsWith("segnaposto") }
    assertEquals(1f, (hint["alpha"] as Number).toFloat(), 0.001f)
  }

  /** Scrive le tracce del banco con `GLASS_F6B6_DIR` (piano 4.3): `<cartella>/f6b-6/<nome>/trace.json`. */
  @Test fun exportTheBenchTraces() {
    val dir = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B6_DIR")) ?: return
    for (scene in SearchPresetTraces.scenes()) {
      val trace = SearchPresetTraces.build(scene)
      trace.write(GlassTraceOutput.fileFor(dir, trace))
    }
  }

  private companion object {
    /** La trascrizione della scena di prova (WWDC25 231 cerca «beaut» in un testo sul lago Tahoe). */
    const val Transcript = "Il lago Tahoe è un lago d'acqua dolce fra la California e il Nevada. " +
      "D'inverno il lago è freddo e d'estate è tiepido."
  }
}

/**
 * Le tracce di F6B.6 per il banco del movimento (piano F6B, «campo e composer»): `campo-messaggio`, `campo-alto-inghiotte`
 * (con `campo-alto-inghiotte-prima`, la posa di d475cbf, con «Altro») e `trova-sopra-tastiera`, chiare e scure, a 120 Hz.
 *
 * La geometria è quella di produzione: dichiarazione → [ToolbarSearch.layout] → [FluidToolbarMemory] → piano
 * ([F38aTraces] con la curva della ricerca, [ToolbarSearchTiming]) e un [FluidGlassPlaneDriver] gemello per posare il
 * contenuto. Il contenuto usa le curve di produzione: [GlassContentJourney] per il segnaposto e il tasto a destra,
 * [toolbarSearchInkAlpha] per i vicini sull'orologio della ricerca, [GlassContentCurves] per la nascita e l'uscita di
 * «trova». La tastiera è **finta**: sale e scende in 285 ms con la curva (0,2, 0, 0, 1) di `InsetsController` (a occhio),
 * e la riga la segue a 4 dp come `searchImeViewport`. Le misure delle icone e del testo nel campo sono quelle del layout
 * (lente 46, tasti 40, testo 16 sp), posate a mano: il banco non impagina Compose.
 */
internal object SearchPresetTraces {
  const val Phase = "f6b-6"
  private const val Density = 2f
  private const val Width = 400f
  private const val TopBarTop = 32f
  private const val ImeGap = 4f
  private const val ImeMillis = 285.0
  private const val FieldKey = "campo"
  private const val FirstFrame = -3
  private val ImeCurve = CubicBezierEasing(0.2f, 0f, 0f, 1f)
  private val Motion = GlassPlaneMotion(spec = ToolbarSearchTiming)

  class Step(val at: Double, val spec: FluidSearchSpec, val label: String)

  /** La tastiera finta: sale a [showAt] e scende a [hideAt]. */
  class Ime(val showAt: Double, val hideAt: Double?, val heightDp: Float = 260f) {
    fun height(t: Double): Float {
      fun rise(from: Double) = ImeCurve.transform(((t - from) / ImeMillis).toFloat().coerceIn(0f, 1f))
      val up = if (t < showAt) 0f else rise(showAt)
      val down = if (hideAt == null || t < hideAt) 0f else rise(hideAt)
      return heightDp * up * (1f - down)
    }
  }

  class TopBar(val model: FluidToolbarModel, val title: String)

  class Scene(
    val name: String,
    val note: String,
    val dark: Boolean,
    val bar: FluidToolbarBar,
    val model: FluidToolbarModel,
    val steps: List<Step>,
    val lastFrame: Int,
    val heightDp: Float,
    val ime: Ime? = null,
    val top: TopBar? = null,
    val before: Boolean = false,
    val text: Boolean = false,
  )

  private class Prepared(
    val step: Step,
    val pose: ToolbarSearchPose,
    val ids: Map<FluidToolbarGroupId, FluidToolbarGlassId>,
    val ghosts: List<FluidToolbarGhost>,
    val arrivals: Map<Any, FluidToolbarElement>,
    val searchArrivals: Set<Any>,
    val plane: GlassPlaneState,
  ) {
    val spec get() = step.spec
  }

  private fun metrics(bar: FluidToolbarBar) = if (bar == FluidToolbarBar.Bottom) fluidBottomBarMetrics() else fluidToolbarMetrics()

  // --- Le scene -----------------------------------------------------------------------------------------------------

  private fun item(id: String, icon: String, placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic) =
    FluidToolbarItem(id, placement = placement, symbol = icon, label = id)

  private val Mail = FluidToolbarModel(listOf(
    item("menu", "menu", FluidToolbarPlacement.Leading),
    item("scrivi", "edit"),
    item("account", "person", FluidToolbarPlacement.PinnedTrailing),
  ))

  private val Reader = FluidToolbarModel(listOf(
    item("indietro", "arrow_back_ios_new", FluidToolbarPlacement.Leading),
    item("condividi", "ios_share"),
    item("altro", "more_horiz"),
  ))

  private fun nav(current: Int, total: Int) = FluidSearchNavigation(current, total, {}, {})

  fun scenes(): List<Scene> = listOf(false, true).flatMap { dark ->
    val suffix = if (dark) "-scuro" else ""
    val message = FluidSearchSpec.message("", {}, onSend = {}, onActiveChange = {})
    val search = FluidSearchSpec.search("", {}, prompt = "Cerca nella posta", placement = FluidSearchPlacement.TopBar,
      behavior = FluidSearchBehavior.CollapseToButton, expansion = FluidSearchExpansion.StayAtTop, onActiveChange = {})
    fun find(query: String, current: Int, total: Int, active: Boolean) =
      FluidSearchSpec.findInPage(query, {}, nav(current, total), active, {})
    val swallow = listOf(Step(-100.0, search, "riposo"), Step(0.0, search.copy(active = true), "apre"),
      Step(700.0, search, "chiude"))
    listOf(
      Scene("campo-messaggio$suffix", "F6B.6: il preset messaggio in basso, sopra la tastiera finta", dark,
        FluidToolbarBar.Bottom, FluidToolbarModel(emptyList()), listOf(
          Step(-100.0, message, "riposo"),
          Step(0.0, message.copy(active = true), "tocco"),
          Step(600.0, message.copy(active = true, query = "Arrivo tra dieci minuti"), "scrive"),
          Step(1000.0, message.copy(active = true), "invia, resta aperto"),
          Step(1500.0, message, "chiude"),
        ), lastFrame = 252, heightDp = 460f, ime = Ime(0.0, 1500.0)),
      Scene("campo-alto-inghiotte$suffix", "F6B.6: il campo in alto inghiotte ☰, scrivi e account e li restituisce", dark,
        FluidToolbarBar.Top, Mail, swallow, lastFrame = 180, heightDp = 108f),
      Scene("campo-alto-inghiotte-prima$suffix", "d475cbf: il campo in alto manda i tasti in «Altro»", dark,
        FluidToolbarBar.Top, Mail, swallow, lastFrame = 180, heightDp = 108f, before = true),
      Scene("trova-sopra-tastiera$suffix", "F6B.6: «trova» nasce in fondo, sale sopra la tastiera finta, la barra in alto resta", dark,
        FluidToolbarBar.Bottom, FluidToolbarModel(emptyList()), listOf(
          Step(-100.0, find("", 0, 0, false), "riposo"),
          Step(0.0, find("", 0, 0, true), "apre"),
          Step(450.0, find("lago", 0, 3, true), "«lago»: 1/3"),
          Step(800.0, find("lago", 1, 3, true), "invio: 2/3"),
          Step(1100.0, find("lago", 0, 3, true), "freccia su: 1/3"),
          Step(1400.0, find("lagx", 0, 0, true), "nessun risultato"),
          Step(1700.0, find("lagx", 0, 0, false), "chiude"),
        ), lastFrame = 288, heightDp = 460f, ime = Ime(0.0, 1700.0), top = TopBar(Reader, "Trascrizione"), text = true),
    )
  }

  // --- La geometria -------------------------------------------------------------------------------------------------

  /** La posa in alto a campo attivo com'era a d475cbf (F4B.2): i vicini in «Altro», accanto al campo. Solo per «prima». */
  private fun poseBeforeF6b6(search: ToolbarSearch, model: FluidToolbarModel, width: Float, metrics: FluidToolbarMetrics): ToolbarSearchPose {
    val lens = FluidMetrics.GlassButtonLens.value
    val margin = metrics.edgeMarginDp.coerceAtMost((width - lens).coerceAtLeast(0f) / 2)
    val inside = (width - 2 * margin).coerceAtLeast(0f)
    val others = FluidToolbarModel(model.items.map { it.copy(overflow = true) })
    val preferred = resolveFluidToolbar(others, width, true, metrics, search.bar)
    val hasOthers = preferred.groups.isNotEmpty()
    val gap = if (hasOthers) metrics.groupSpacingDp else 0f
    val room = if (hasOthers) (preferred.requiredWidthDp - 2 * metrics.edgeMarginDp).coerceAtLeast(metrics.buttonWidthDp) else 0f
    val ideal = (inside - room - gap).coerceAtLeast(lens)
    val fieldWidth = ideal.coerceAtMost(inside).coerceAtMost((inside - if (hasOthers) metrics.buttonWidthDp + gap else 0f).coerceAtLeast(0f))
    val reserved = fieldWidth + gap
    val row = resolveFluidToolbar(others, (width - reserved).coerceAtLeast(0f), true, metrics, search.bar)
    val after = row.groups.filter { group -> group.items.any { it.placement == FluidToolbarPlacement.PinnedTrailing } }
    val afterIds = after.mapTo(mutableSetOf()) { it.id }
    val shifted = if (afterIds.isEmpty()) row else row.copy(
      groups = row.groups.map { if (it.id in afterIds) it.copy(leftDp = it.leftDp + reserved) else it },
      elements = row.elements.map { if (it.groupId in afterIds) it.copy(leftDp = it.leftDp + reserved) else it },
    )
    val x = after.minOfOrNull { it.leftDp } ?: (width - margin - fieldWidth)
    return ToolbarSearchPose(shifted.copy(widthDp = width), x, fieldWidth, false)
  }

  private fun prepare(scene: Scene): List<Prepared> {
    val metrics = metrics(scene.bar)
    val memory = FluidToolbarMemory()
    var previousAt = Double.NEGATIVE_INFINITY
    return scene.steps.map { step ->
      // Fra due stati lontani i fantasmi e gli arrivi di prima sono finiti, come nella barra.
      if (step.at - previousAt >= 400.0) {
        memory.finishTransfers(memory.transfers)
        memory.ghosts.toList().forEach(memory::forget)
        memory.arrivals.values.toList().forEach(memory::finishArrival)
      }
      previousAt = step.at
      val search = ToolbarSearch(step.spec, scene.bar, true)
      val pose = if (scene.before && step.spec.active && scene.bar == FluidToolbarBar.Top)
        poseBeforeF6b6(search, scene.model, Width, metrics) else search.layout(scene.model, Width, metrics)
      memory.update(pose.neighbours, FluidToolbarMotion.Full, searchActive = step.spec.active)
      Prepared(step, pose, memory.glassIds.toMap(), memory.ghosts.toList(), memory.arrivals.toMap(),
        memory.searchArrivals.toSet(), plane(pose, memory.glassIds, metrics))
    }
  }

  private fun groupPiece(group: FluidToolbarGroup, id: FluidToolbarGlassId, row: Float, top: Float = 0f): GlassPlanePiece {
    val d = Density
    val bounds = Rect(group.leftDp * d, top * d, (group.leftDp + group.widthDp) * d, (top + row) * d)
    return GlassPlanePiece(id.toString(), bounds, FluidCornerRadii.all(bounds.height / 2), id = id,
      transition = if (fluidToolbarGlassTransition(group) == FluidGlassTransition.MaterializeWhenAlone)
        GlassPlaneTransition.MaterializeWhenAlone else GlassPlaneTransition.MatchedGeometry,
      contentKey = fluidToolbarGlassContentKey(group))
  }

  private fun plane(pose: ToolbarSearchPose, ids: Map<FluidToolbarGroupId, FluidToolbarGlassId>, metrics: FluidToolbarMetrics): GlassPlaneState {
    val d = Density
    val row = metrics.buttonWidthDp
    val pieces = pose.neighbours.groups.map { groupPiece(it, ids.getValue(it.id), row) }.toMutableList()
    if (!pose.hidden) {
      val h = if (pose.compact) FluidMetrics.GlassButtonLens.value else FluidSearchFieldDefaults.BarFieldHeight.value
      val bounds = Rect(pose.left * d, (row - h) / 2 * d, (pose.left + pose.width) * d, (row + h) / 2 * d)
      pieces += GlassPlanePiece(FieldKey, bounds, FluidCornerRadii.all(bounds.height / 2), id = FieldKey)
    }
    return GlassPlaneState(pieces, FluidGlassContainerDefaults.Spacing.value * d, Rect(0f, 0f, Width * d, row * d))
  }

  private fun rowTop(scene: Scene, t: Double, row: Float): Float {
    if (scene.bar == FluidToolbarBar.Top) return TopBarTop
    val rest = scene.heightDp - FluidFloatingTabBarDefaults.BottomMargin.value - row
    return minOf(rest, scene.heightDp - (scene.ime?.height(t) ?: 0f) - ImeGap - row)
  }

  // --- Il contenuto -------------------------------------------------------------------------------------------------

  private fun ink(dark: Boolean) = if (dark) "#F2F2F7" else "#17171A"
  private fun accent(dark: Boolean) = if (dark) "#0A84FF" else "#007AFF"

  private fun blurDp(fraction: Float, sizeDp: Float) =
    glassContentBlurRadiusPx(glassContentSigmaPx(fraction, sizeDp * Density)) / Density

  private fun icon(name: String, x: Float, y: Float, color: String, size: Float = FluidSearchFieldDefaults.IconSize.value) =
    linkedMapOf<String, Any?>("kind" to "icon", "icon" to "fluid:$name", "x" to x, "y" to y, "sizeDp" to size, "color" to color)

  private fun text(value: String, x: Float, y: Float, color: String, size: Float = 16f) =
    linkedMapOf<String, Any?>("kind" to "text", "text" to value, "x" to x, "y" to y, "sizeDp" to size, "color" to color)

  private fun layer(
    name: String, x: Float, y: Float, w: Float, h: Float, items: List<Map<String, Any?>>,
    alpha: Float = 1f, scale: Float = 1f, blur: Float = 0f, clip: Rect? = null,
  ): Map<String, Any?>? {
    if (alpha <= 0.001f || items.isEmpty()) return null
    val out = linkedMapOf<String, Any?>("name" to name, "x" to x, "y" to y, "w" to w, "h" to h, "items" to items,
      "alpha" to alpha.coerceIn(0f, 1f), "scale" to scale, "blurDp" to blur, "marginDp" to 12)
    if (clip != null) out["clip"] = linkedMapOf("x" to clip.left, "y" to clip.top, "w" to clip.width, "h" to clip.height,
      "radius" to minOf(clip.width, clip.height) / 2f)
    return out
  }

  private fun itemIcon(item: FluidToolbarItem): String =
    if (item.role == FluidToolbarRole.Overflow) "more_horiz" else item.symbol as? String ?: "more_horiz"

  /** La fase dell'orologio comune della ricerca a [t]: 0 a riposo, 1 a campo attivo, in 250 ms lineari. */
  private fun inkPhase(prepared: List<Prepared>, t: Double): Float {
    var phase = if (prepared.first().spec.active) 1f else 0f
    for (i in 1 until prepared.size) {
      val step = prepared[i]
      if (step.step.at > t) break
      if (step.spec.active != prepared[i - 1].spec.active) {
        val run = ((t - step.step.at) / ToolbarSearchInkMillis).toFloat().coerceIn(0f, 1f)
        phase = if (step.spec.active) run else 1f - run
      }
    }
    return phase
  }

  /** Il riquadro di un vetro in volo, in dp della riga; null se non vola. */
  private fun flight(frame: GlassPlaneFrame?, key: String, target: Boolean = true): Rect? {
    val piece = frame?.pieces?.firstOrNull { (if (target) it.flightId.targetKey else it.flightId.sourceKey) == key } ?: return null
    val b = piece.bounds
    return Rect(b.left / Density, b.top / Density, b.right / Density, b.bottom / Density)
  }

  /** Il contenuto della riga: i tasti, i fantasmi, gli arrivi e il campo. Coordinate della riga. */
  private fun rowContent(
    scene: Scene, prepared: List<Prepared>, current: Prepared, time: Double, frame: GlassPlaneFrame?, metrics: FluidToolbarMetrics,
    prompt: GlassContentJourney, trailing: GlassContentJourney,
  ): List<Map<String, Any?>> {
    val row = metrics.buttonWidthDp
    val color = ink(scene.dark)
    val layers = ArrayList<Map<String, Any?>?>()
    val phase = inkPhase(prepared, time)
    val inkSettled = phase == 0f || phase == 1f
    // I tasti al loro posto, nel loro vetro (l'ancora del lato), salvo chi sta arrivando.
    for (group in current.pose.neighbours.groups) {
      val key = current.ids.getValue(group.id).toString()
      val flying = flight(frame, key)
      val shift = if (flying == null) 0f else when (group.side) {
        FluidToolbarSide.Leading -> flying.left - group.leftDp
        FluidToolbarSide.Trailing -> flying.right - group.leftDp - group.widthDp
        FluidToolbarSide.Center -> flying.center.x - group.leftDp - group.widthDp / 2f
      }
      for (item in group.items) {
        val arriving = item.id in current.arrivals && (!inkSettled || frame != null)
        if (arriving) continue
        val element = current.pose.neighbours.element(item.id) ?: continue
        val size = fluidToolbarIconSize(item.role).value
        layers += layer("tasto-${item.id}", element.leftDp + shift, 0f, element.widthDp, row,
          listOf(icon(itemIcon(item), element.widthDp / 2f, row / 2f, color, size)), clip = flying)
      }
    }
    // Chi esce sull'orologio della ricerca, dove stava; chi arriva, nel suo vetro che nasce.
    for (ghost in current.ghosts) {
      val element = ghost.element
      val overflow = element.item.id == FluidToolbarOverflowId
      val size = fluidToolbarIconSize(element.item.role).value
      val (alpha, blur) = if (ghost.searchSwap) toolbarSearchInkAlpha(phase, overflow) to toolbarSearchInkBlur(phase, overflow)
        else {
          val u = ((time - current.step.at) / GlassContentDefaults.DurationMs).toFloat()
          GlassContentCurves.alpha(GlassContentRole.Exit, u, true) to GlassContentCurves.blur(GlassContentRole.Exit, u)
        }
      val clip = if (ghost.glassStays) flight(frame, ghost.glass.toString()) else null
      layers += layer("esce-${element.item.id}", element.leftDp, 0f, element.widthDp, row,
        listOf(icon(itemIcon(element.item), element.widthDp / 2f, row / 2f, color, size)), alpha = alpha,
        blur = blurDp(blur * GlassContentDefaults.PeakBlur, size), clip = clip)
    }
    for ((id, element) in current.arrivals) {
      if (inkSettled && frame == null) continue
      val group = current.pose.neighbours.groups.first { it.id == element.groupId }
      val key = current.ids.getValue(group.id).toString()
      val flying = flight(frame, key)
      val offset = if (flying == null) 0f else flying.center.x - group.leftDp - group.widthDp / 2f
      val overflow = id == FluidToolbarOverflowId
      val size = fluidToolbarIconSize(element.item.role).value
      val (alpha, blur) = if (id in current.searchArrivals) toolbarSearchInkAlpha(phase, overflow) to toolbarSearchInkBlur(phase, overflow)
        else {
          val u = ((time - current.step.at) / GlassContentDefaults.DurationMs).toFloat()
          GlassContentCurves.alpha(GlassContentRole.Enter, u, true) to GlassContentCurves.blur(GlassContentRole.Enter, u)
        }
      layers += layer("entra-$id", element.leftDp + offset, 0f, element.widthDp, row,
        listOf(icon(itemIcon(element.item), element.widthDp / 2f, row / 2f, color, size)), alpha = alpha,
        blur = blurDp(blur * GlassContentDefaults.PeakBlur, size), clip = flying)
    }
    // Il campo vero, ancorato al bordo sinistro del suo vetro e ritagliato sul vetro in volo, come FluidGlassPieceContentNode.
    val cy = row / 2f
    val noWhole = Triple(1f, 1f, 0f)
    fun curve(role: GlassContentRole, u: Float) = Triple(GlassContentCurves.alpha(role, u, true),
      GlassContentCurves.scale(role, u), GlassContentCurves.blur(role, u) * GlassContentDefaults.PeakBlur)
    /** Parola, contatore, frecce, cerchio e tasto a destra di un campo aperto largo [w]; la lente a parte. */
    fun openItems(spec: FluidSearchSpec, w: Float, word: String?, trailingFace: ToolbarSearchTrailingFace?): List<Map<String, Any?>> {
      val items = ArrayList<Map<String, Any?>>()
      if (!word.isNullOrEmpty()) items += text(word, 46f, cy + 5.5f, color)
      val parts = toolbarSearchFieldParts(spec, w)
      val navigation = spec.navigation
      // F6B.8b: i comandi da 44 dp con le icone da 26, «Cancella» da 36 col cerchio da 22; F6B.15: «Invia» un disco da 44.
      val a = FluidSearchFieldDefaults.ActionSize.value
      val glyph = FluidSearchFieldDefaults.CommandIconSize.value
      val inset = ToolbarSearchTrailingInset.value
      var right = w - inset - a
      if (parts.navigation && navigation != null) {
        val arrows = if (navigation.total > 0) color else color + "61"
        items += icon("keyboard_arrow_down", right - a / 2f, cy, arrows, glyph)
        items += icon("keyboard_arrow_up", right - 1.5f * a, cy, arrows, glyph)
        right -= 2f * a
        val counter = fluidSearchCounter(spec.query, navigation.current, navigation.total, FluidSearchLabels().noResults)
        if (counter != null) {
          val counterWidth = counter.length * 6.6f + 8f
          items += text(counter, right - counterWidth + 4f, cy + 4.5f, color, 12f)
          right -= counterWidth
        }
      }
      val clear = FluidSearchFieldDefaults.ClearSize.value
      val circle = FluidSearchFieldDefaults.ClearGlyph.value
      if (parts.clear) items += linkedMapOf("kind" to "rect", "x" to right - clear / 2f - circle / 2f, "y" to cy - circle / 2f,
        "w" to circle, "h" to circle, "radius" to "capsule", "color" to color + "47")
      when (trailingFace) {
        ToolbarSearchTrailingFace.Send -> {
          // F6B.15: il disco da 44, largo quanto il posto, con la freccia da 24.
          items += linkedMapOf<String, Any?>("kind" to "rect", "x" to w - inset - a, "y" to cy - a / 2f, "w" to a,
            "h" to a, "radius" to "capsule", "color" to accent(scene.dark))
          items += icon("arrow_upward", w - inset - a / 2f, cy, "#FFFFFF", 24f)
        }
        ToolbarSearchTrailingFace.Close -> items += icon("close", w - inset - a / 2f, cy, color, glyph)
        null -> Unit
      }
      return items
    }
    fun hintOf(spec: FluidSearchSpec) = spec.prompt ?: if (spec.purpose == FluidSearchPurpose.Search) FluidSearchLabels().search else "Messaggio"
    if (!current.pose.hidden) {
      val pose = current.pose
      val spec = current.spec
      val flying = flight(frame, FieldKey)
      val h = if (pose.compact) FluidMetrics.GlassButtonLens.value else FluidSearchFieldDefaults.BarFieldHeight.value
      val clip = flying ?: Rect(pose.left, (row - h) / 2f, pose.left + pose.width, (row + h) / 2f)
      val x = flying?.left ?: pose.left
      // «Trova» nasce senza un tasto da cui allungarsi: tutto il contenuto entra come chi nasce (R7 A6).
      val (wa, ws, wb) = if (spec.showsAtRest) noWhole else
        curve(GlassContentRole.Born, ((time - prepared.first { it.spec.active }.step.at) / GlassContentDefaults.DurationMs).toFloat())
      val base = ArrayList<Map<String, Any?>>()
      base += icon(benchIcon(fluidSearchIcon(spec).name.substringAfterLast('.')), 23f, cy, color)
      // La lente è tutto il contenuto del campo chiuso: il layout di 46 dp schiaccia il resto, che esce dal fantasma.
      if (!pose.compact) base += openItems(spec, pose.width, spec.query, null)
      layers += layer("campo", x, 0f, pose.width, row, base, wa, ws, blurDp(wb, FluidSearchFieldDefaults.IconSize.value), clip)
      if (!pose.compact) {
        // Il segnaposto e il tasto a destra cambiano con lo scambio sfocato (FluidGlassContentTransition).
        for (ink in prompt.frame(time).layers) {
          val hint = ink.key as? String ?: continue
          val look = ink.look
          layers += layer("segnaposto-${ink.id}", x, 0f, pose.width, row, listOf(text(hint, 46f, cy + 5.5f, color)),
            wa * look.alpha, ws * look.scale,
            hypot(blurDp(look.blur, GlassContentDefaults.BlurReference.value), blurDp(wb, FluidSearchFieldDefaults.IconSize.value)), clip)
        }
        for (ink in trailing.frame(time).layers) {
          val face = ink.key as? ToolbarSearchTrailingFace ?: continue
          val look = ink.look
          layers += layer("destra-${ink.id}", x, 0f, pose.width, row, openItems(spec.copy(navigation = null, query = ""), pose.width, null, face),
            wa * look.alpha, ws * look.scale,
            hypot(blurDp(look.blur, GlassContentDefaults.BlurReference.value), blurDp(wb, FluidSearchFieldDefaults.IconSize.value)), clip)
        }
      }
    }
    // Il fantasma (ToolbarSearchGhost): «trova» che se ne va, o il campo aperto che torna lente.
    val before = prepared.lastOrNull { it.step.at < current.step.at }
    val gone = current.pose.hidden
    val toLens = before != null && before.spec.active && !before.pose.compact && !current.spec.active && current.pose.compact
    // La scena «prima» rifà d475cbf, che il fantasma non l'aveva: parola e X sparivano col layout della lente.
    val shown = when {
      scene.before -> null
      gone -> prepared.lastOrNull { it.step.at < current.step.at && !it.pose.hidden }
      toLens -> before
      else -> null
    }
    if (shown != null) {
      val flying = flight(frame, FieldKey, target = !gone)
      if (flying != null || !gone) {
        val (ga, gs, gb) = curve(GlassContentRole.Exit, ((time - current.step.at) / GlassContentDefaults.DurationMs).toFloat())
        val spec = shown.spec
        val items = ArrayList<Map<String, Any?>>()
        if (gone) items += icon(benchIcon(fluidSearchIcon(spec).name.substringAfterLast('.')), 23f, cy, color)
        items += openItems(spec, shown.pose.width, spec.query.ifEmpty { hintOf(spec) }, toolbarSearchTrailingFace(spec))
        layers += layer("fantasma", shown.pose.left, 0f, shown.pose.width, row, items, ga, gs,
          blurDp(gb, FluidSearchFieldDefaults.IconSize.value), flying?.let { Rect(it.left, 0f, it.right, row) })
      }
    }
    return layers.filterNotNull()
  }

  /** Il nome del banco di un'icona dell'engine (`Rounded.Search` → `search`). */
  private fun benchIcon(name: String): String = name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase(Locale.ROOT)

  private fun promptKey(prepared: Prepared): String? {
    val spec = prepared.spec
    if (prepared.pose.hidden || prepared.pose.compact || spec.query.isNotEmpty()) return null
    return spec.prompt ?: if (spec.purpose == FluidSearchPurpose.Search) FluidSearchLabels().search else "Messaggio"
  }

  @Suppress("UNCHECKED_CAST")
  private fun shifted(elements: List<Map<String, Any?>>, dy: Float): List<Map<String, Any?>> = elements.map { element ->
    LinkedHashMap(element).also { it["y"] = (it.getValue("y") as Number).toDouble() + dy }
  }

  fun build(scene: Scene): GlassTrace {
    val prepared = prepare(scene)
    val metrics = metrics(scene.bar)
    val row = metrics.buttonWidthDp
    // «Trova»: un fondo pieno; il testo della pagina con gli evidenziatori lo aggiunge `pagina_trova.py` della sessione,
    // che misura le parole col carattere del banco e legge parola e corrente da `misure`.
    val background: Map<String, Any?> = if (scene.text) linkedMapOf("kind" to "color",
      "color" to if (scene.dark) "#1C1C1E" else "#F2F2F7")
      else linkedMapOf("kind" to "synthetic", "scaleDp" to 64, "detail" to .45,
        "palette" to if (scene.dark) listOf("#16283C", "#5A3145", "#1F423E") else listOf("#E6D0A0", "#AFC9D7", "#F7EEDD"))
    val base = F38aTraces.build(F38aTraces.Scene(scene.name, scene.note, Width, scene.heightDp, scene.dark,
      prepared.first().plane, prepared.drop(1).map { F38aTraces.Step(it.step.at, it.plane) }, emptyMap(), scene.lastFrame,
      density = Density, firstFrame = FirstFrame, phase = Phase, background = background, motion = Motion))
    // La barra in alto di «trova»: un piano fermo, letto con lo stesso valore della barra vera.
    val topMetrics = fluidToolbarMetrics()
    val topResolved = scene.top?.let { top ->
      resolveFluidToolbar(top.model, Width, fluidToolbarSearchTouchesBar(scene.steps.last { it.spec.active }.spec) ?: false, topMetrics)
    }
    val topBase = scene.top?.let {
      val memory = FluidToolbarMemory()
      memory.update(topResolved!!, FluidToolbarMotion.Full)
      val pieces = topResolved.groups.map { groupPiece(it, memory.glassIds.getValue(it.id), topMetrics.buttonWidthDp) }
      F38aTraces.build(F38aTraces.Scene("${scene.name}-alto", "barra in alto ferma", Width, scene.heightDp, scene.dark,
        GlassPlaneState(pieces, FluidGlassContainerDefaults.Spacing.value * Density, Rect(0f, 0f, Width * Density, topMetrics.buttonWidthDp * Density)),
        emptyList(), emptyMap(), scene.lastFrame, density = Density, firstFrame = FirstFrame, phase = Phase,
        background = background, motion = Motion))
    }
    val driver = FluidGlassPlaneDriver()
    var prompt = GlassContentJourney.rest(promptKey(prepared.first()))
    var trailing = GlassContentJourney.rest(toolbarSearchTrailingFace(prepared.first().spec))
    var previous: Prepared? = null
    val color = ink(scene.dark)
    val frames = base.frames.mapIndexed { index, frame ->
      val time = frame.tMs
      val current = prepared.last { it.step.at <= time + 1e-6 }
      if (previous != null && previous !== current) {
        // Il segnaposto lascia il posto al testo subito e torna fermo quando il testo si svuota (ToolbarSearchField).
        prompt = if (previous!!.spec.query.isEmpty() != current.spec.query.isEmpty()) GlassContentJourney.rest(promptKey(current))
          else prompt.retarget(time, promptKey(current))
        trailing = trailing.retarget(time, toolbarSearchTrailingFace(current.spec))
      }
      previous = current
      driver.step(10_000_000_000L + glassTraceTimeNanos(index + FirstFrame, F38aTraces.Fps), current.plane, Motion)
      val top = rowTop(scene, time, row)
      val elements = ArrayList<Map<String, Any?>>()
      if (topBase != null && topResolved != null) {
        elements += shifted(topBase.frames[index].elements, TopBarTop)
        val icons = topResolved.elements.filter { !it.inOverflow }.mapNotNull { element ->
          layer("alto-${element.item.id}", element.leftDp, 0f, element.widthDp, topMetrics.buttonWidthDp,
            listOf(icon(itemIcon(element.item), element.widthDp / 2f, topMetrics.buttonWidthDp / 2f, color,
              fluidToolbarIconSize(element.item.role).value)))
        } + listOfNotNull(layer("titolo", 0f, 0f, Width, topMetrics.buttonWidthDp,
          listOf(text(scene.top!!.title, Width / 2f - scene.top.title.length * 4.6f, topMetrics.buttonWidthDp / 2f + 6f, color, 17f))))
        elements += linkedMapOf("kind" to "shape", "name" to "barra-alta", "x" to 0, "y" to TopBarTop, "w" to Width,
          "h" to topMetrics.buttonWidthDp, "radius" to 0, "color" to "#00000000", "contentClip" to "none", "content" to icons)
      }
      elements += shifted(frame.elements, top)
      elements += linkedMapOf("kind" to "shape", "name" to "contenuto", "x" to 0, "y" to top, "w" to Width, "h" to row,
        "radius" to 0, "color" to "#00000000", "contentClip" to "none",
        "content" to rowContent(scene, prepared, current, time, driver.frame, metrics, prompt, trailing))
      val ime = scene.ime?.height(time) ?: 0f
      if (scene.ime != null && ime > 0.01f) {
        elements += linkedMapOf("kind" to "shape", "name" to "tastiera", "x" to 0, "y" to scene.heightDp - ime, "w" to Width,
          "h" to scene.ime.heightDp, "radius" to 0, "color" to if (scene.dark) "#2C2C2E" else "#D1D4DA", "contentClip" to "none",
          "content" to listOf(linkedMapOf("name" to "scritta", "x" to 0, "y" to 0, "w" to Width, "h" to 40,
            "items" to listOf(text("tastiera finta", Width / 2f - 44f, 26f, if (scene.dark) "#8E8E93" else "#6E6E73", 14f)))))
      }
      val field = flight(driver.frame, FieldKey) ?: current.plane.pieces.firstOrNull { it.key == FieldKey }?.bounds?.let {
        Rect(it.left / Density, it.top / Density, it.right / Density, it.bottom / Density)
      }
      elements += linkedMapOf("kind" to "shape", "name" to "misure", "x" to 0, "y" to 0, "w" to 1, "h" to 1,
        "color" to "#00000000", "radius" to 0,
        "campoSinistra" to (field?.left ?: 0f), "campoLarghezza" to (field?.width ?: 0f),
        "campoAlto" to top + (field?.top ?: 0f), "rigaAlto" to top, "tastieraAlto" to scene.heightDp - ime,
        "inchiostro" to inkPhase(prepared, time)).also { misure ->
          current.spec.navigation?.let { navigation ->
            misure["trovaParola"] = if (current.spec.active) current.spec.query else ""
            misure["trovaCorrente"] = navigation.current
            misure["trovaTotale"] = navigation.total
          }
        }
      GlassTraceFrame(time, elements)
    }
    val head = LinkedHashMap(base.head)
    head["_provenienza"] = "FluidSearchSpec -> ToolbarSearch.layout -> FluidToolbarMemory -> F38aTraces (piano di " +
      "produzione, ToolbarSearchTiming) + FluidGlassPlaneDriver gemello per il contenuto; tastiera finta. Nessun Compose/GPU."
    head["events"] = scene.steps.drop(1).map { linkedMapOf("t" to it.at, "kind" to "mark", "label" to it.label) }
    head["probes"] = listOf("campoSinistra", "campoLarghezza", "campoAlto", "rigaAlto", "tastieraAlto", "inchiostro").map {
      linkedMapOf("name" to it, "kind" to "value", "path" to "misure.$it")
    }
    return GlassTrace(scene.name, Phase, Density, base.theme, Width, scene.heightDp, frames, F38aTraces.Fps, head)
  }
}
