package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import dev.antigravity.fluidengine.ui.fluid.FluidBottomReplacement
import dev.antigravity.fluidengine.ui.fluid.FluidChromeController
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMode
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FilterList
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La barra delle azioni di selezione (piano «iOS in Android», F6B.1) in logica pura: la dichiarazione `selection { }`
 * nel modello della barra (conteggio, «Seleziona tutto» anche su una lista paginata, «Fine», posizioni, preset con
 * priorità e trabocco), il back che esce dalla selezione e non dalla pagina con la regola D1 provata sul dispatcher
 * vero di navigationevent (gesto predittivo annullato e confermato), e il canale `bottomReplacement` del
 * `FluidChromeController` durante un gesto indietro. La resa (vetri, semantica, fotogrammi) la provano la galleria e il
 * banco; questo file prova le regole.
 */
class FluidSelectionToolbarTest {

  private val metrics = fluidToolbarMetrics()
  private val bottomMetrics = fluidBottomBarMetrics()

  /** Una larghezza di scritta finta e dichiarata: 9 dp per carattere più i margini del tasto. */
  private val textWidth: (String) -> Float = { it.length * 9f + FluidToolbarDefaults.TextPadding.value * 2f }

  private var done = 0
  private var selectedAll = 0
  private val noop: () -> Unit = {}

  private fun state(vararg keys: Int) = FluidSelectionState<Int>().apply { keys.forEach(::select) }

  /** La pagina in selezione, come la scrive un'app: Mail con le sue azioni. */
  private fun selecting(
    state: FluidSelectionState<Int>,
    count: Int? = null,
    placement: FluidSelectionBarPlacement = FluidSelectionBarPlacement.ReplacingTabBar,
    onBack: (() -> Unit)? = noop,
    selectAll: (() -> Unit)? = { selectedAll++; state.selectAll("query@t0") },
    labels: FluidSelectionLabels = FluidSelectionLabels(),
    actions: FluidToolbarScope.() -> Unit = FluidSelectionActions.mail(onDelete = noop, onArchive = noop, onMove = noop,
      onMarkRead = noop),
  ): FluidToolbarDeclaration = buildFluidScreenToolbar(onBack) {
    selection(state, count = count, onSelectAll = selectAll, onDone = { done++ }, placement = placement, labels = labels,
      actions = actions)
  }

  /** La stessa pagina fuori selezione: «Seleziona» e il filtro. */
  private fun browsing(onBack: (() -> Unit)? = noop): FluidToolbarDeclaration = buildFluidScreenToolbar(onBack) {
    selectButton(onClick = noop)
    button("filter", FluidIcons.FilterList, "Filtra", onClick = noop)
  }

  private fun model(declaration: FluidToolbarDeclaration) = fluidToolbarModel(declaration, textWidth)
  private fun top(declaration: FluidToolbarDeclaration, width: Float = 600f) =
    resolveFluidToolbar(model(declaration), width, metrics = metrics, bar = FluidToolbarBar.Top)
  private fun bottom(declaration: FluidToolbarDeclaration, width: Float = 400f) =
    resolveFluidToolbar(model(declaration), width, metrics = bottomMetrics, bar = FluidToolbarBar.Bottom)

  private fun FluidToolbarDeclaration.text(id: Any): String? = buttons[id]?.text
  private fun FluidToolbarResolved.visibleIds(): List<Any> = elements.filter { !it.inOverflow }.map { it.item.id }

  // --- Il conteggio --------------------------------------------------------------------------------------------------

  @Test fun `il conteggio sta al centro al posto del titolo, senza vetro, e cambia con la selezione`() {
    val state = state(1, 2, 3)
    val bar = selecting(state)
    assertEquals("3 selezionati", bar.selection!!.title)
    assertEquals("3 selezionati", bar.text(FluidSelectionToolbarIds.Count))
    assertTrue("il titolo agganciato cede il posto al conteggio", bar.hasPrincipal)
    val count = top(bar).element(FluidSelectionToolbarIds.Count)!!
    assertEquals(FluidToolbarSide.Center, count.side)
    assertFalse("il conteggio è una scritta senza vetro", count.item.sharedBackground)
    // Il nome per TalkBack lo dà l'area viva: la faccia del conteggio non ne ha uno suo (niente doppia lettura).
    assertNull(bar.buttons.getValue(FluidSelectionToolbarIds.Count).label)
    assertNull(bar.buttons.getValue(FluidSelectionToolbarIds.Count).onClick)

    state.toggle(2)
    assertEquals("2 selezionati", selecting(state).selection!!.title)
    state.toggle(1)
    assertEquals("1 selezionato", selecting(state).selection!!.title)
    // La chiave del contenuto cambia con la scritta: la barra la scambia sfocandola (R7), il vetro resta.
    assertTrue(model(selecting(state(1, 2))).items.any { it.id == FluidSelectionToolbarIds.Count && it.label == "2 selezionati" })
  }

  @Test fun `a zero la selezione resta aperta, il titolo lo dice e le azioni sono spente`() {
    val bar = selecting(state())
    assertEquals("0 selezionati", bar.selection!!.title)
    assertFalse(bar.selection!!.hasItems)
    for (id in listOf("mark", "move", "archive", "delete")) assertFalse("$id spento", bar.buttons.getValue(id).enabled)
    // «Fine» c'è sempre: è l'uscita.
    assertTrue(bar.buttons.getValue(FluidSelectionToolbarIds.Toggle).enabled)
    val some = selecting(state(4))
    for (id in listOf("mark", "move", "archive", "delete")) assertTrue("$id acceso", some.buttons.getValue(id).enabled)
  }

  @Test fun `il conteggio dell'app vince, e senza il totale la forma tutto-tranne lo dice a parole`() {
    val state = state(1)
    state.selectAll("q")
    assertEquals("con il totale della query", "480 selezionati", selecting(state, count = state.countIn(480)).selection!!.title)
    assertEquals("senza totale", "Tutti selezionati", selecting(state).selection!!.title)
    state.toggle(9)
    state.toggle(10)
    assertEquals("Tutti tranne 2", selecting(state).selection!!.title)
    assertEquals("478 selezionati", selecting(state, count = state.countIn(480)).selection!!.title)
    // Le parole di un'app in un'altra lingua.
    val english = buildFluidScreenToolbar(null) {
      selection(state(1, 2), count = null, onSelectAll = null, onDone = noop,
        labels = FluidSelectionLabels(count = { "$it Selected" }, done = "Done")) {}
    }
    assertEquals("2 Selected", english.selection!!.title)
    assertEquals("Done", english.text(FluidSelectionToolbarIds.Toggle))
  }

  // --- «Seleziona tutto» e «Deseleziona» ---------------------------------------------------------------------------

  @Test fun `Seleziona tutto passa alla forma tutto-tranne e diventa Deseleziona, anche dopo le esclusioni`() {
    val state = state(3, 5)
    val before = selecting(state)
    val selectAll = before.buttons.getValue(FluidSelectionToolbarIds.SelectAll)
    assertEquals("Seleziona tutto", selectAll.label)
    assertFalse(selectAll.selected)
    assertEquals(FluidToolbarSide.Leading, top(before).element(FluidSelectionToolbarIds.SelectAll)!!.side)

    selectAll.onClick!!.invoke()
    assertEquals(1, selectedAll)
    assertEquals("nessuna chiave in memoria: la forma tutto-tranne", FluidSelectionMode.AllExcept(emptySet(), "query@t0"), state.mode)

    // Una lista paginata da 50.000 righe: il numero lo dà l'app con lo stesso testimone.
    val all = selecting(state, count = state.countIn(50_000))
    val deselect = all.buttons.getValue(FluidSelectionToolbarIds.SelectAll)
    assertEquals("Deseleziona", deselect.label)
    assertTrue("lo stesso tondo, acceso", deselect.selected)
    assertEquals("50000 selezionati", all.selection!!.title)
    assertTrue(all.selection!!.allSelected)
    // Lo stesso tasto: il simbolo resta, cambia la pellicola della selezione (nessun vetro nuovo).
    assertEquals(selectAll.icon, deselect.icon)

    // Una riga tolta a mano: non è più tutto, e il tasto torna a offrire «Seleziona tutto».
    state.toggle(42)
    val allBut = selecting(state, count = state.countIn(50_000))
    assertEquals("Seleziona tutto", allBut.buttons.getValue(FluidSelectionToolbarIds.SelectAll).label)
    assertEquals("49999 selezionati", allBut.selection!!.title)
    allBut.buttons.getValue(FluidSelectionToolbarIds.SelectAll).onClick!!.invoke()
    assertEquals(FluidSelectionMode.AllExcept(emptySet(), "query@t0"), state.mode)

    // «Deseleziona» svuota e lascia aperta la selezione: non è «Fine».
    selecting(state).buttons.getValue(FluidSelectionToolbarIds.SelectAll).onClick!!.invoke()
    assertFalse(state.isActive)
    assertEquals(0, done)
    assertEquals("0 selezionati", selecting(state).selection!!.title)
  }

  @Test fun `su un telefono stretto la barra in alto tiene Seleziona tutto, il conteggio e Fine`() {
    // 360 dp, le scritte vere del conteggio a zero e con molti elementi.
    for (count in listOf(0, 3, 128)) {
      val shown = top(selecting(state(1), count = count), width = 360f)
      assertTrue("$count: ci sta", shown.fits)
      assertEquals("$count: niente in «Altro»", emptyList<Any>(), shown.overflow.map { it.id })
      assertTrue(shown.visibleIds().containsAll(listOf(FluidSelectionToolbarIds.SelectAll, FluidSelectionToolbarIds.Count,
        FluidSelectionToolbarIds.Toggle)))
    }
  }

  @Test fun `quando il posto non basta escono le azioni, poi Seleziona tutto, poi il conteggio, e Fine per ultimo`() {
    val bar = selecting(state(1), labels = FluidSelectionLabels(count = { "$it elementi selezionati in questa cartella" }))
    val narrow = top(bar, width = 360f)
    assertTrue("«Fine» resta", FluidSelectionToolbarIds.Toggle in narrow.visibleIds())
    val hidden = narrow.overflow.map { it.id }
    if (FluidSelectionToolbarIds.Count in hidden) assertTrue("prima esce «Seleziona tutto»", FluidSelectionToolbarIds.SelectAll in hidden)
    assertTrue(FluidSelectionToolbarIds.SelectAll in hidden)
  }

  @Test fun `senza onSelectAll il tasto non c'e'`() {
    val bar = selecting(state(1), selectAll = null)
    assertNull(bar.buttons[FluidSelectionToolbarIds.SelectAll])
  }

  // --- «Seleziona» → «Fine» e il back -----------------------------------------------------------------------------

  @Test fun `Seleziona e Fine sono lo stesso tasto, nello stesso vetro`() {
    val browse = top(browsing())
    val select = top(selecting(state(1)))
    assertEquals("Seleziona", browsing().text(FluidSelectionToolbarIds.Toggle))
    assertEquals("Fine", selecting(state(1)).text(FluidSelectionToolbarIds.Toggle))
    val memory = FluidToolbarMemory()
    memory.update(browse, FluidToolbarMotion.Full)
    val glass = memory.glassIds.getValue(browse.element(FluidSelectionToolbarIds.Toggle)!!.groupId!!)
    memory.update(select, FluidToolbarMotion.Full)
    assertEquals("lo stesso vetro, più stretto, che scambia la scritta", glass,
      memory.glassIds.getValue(select.element(FluidSelectionToolbarIds.Toggle)!!.groupId!!))
    assertEquals(FluidToolbarSide.Trailing, select.element(FluidSelectionToolbarIds.Toggle)!!.side)
    assertTrue("«Fine» è più stretto di «Seleziona»",
      select.element(FluidSelectionToolbarIds.Toggle)!!.widthDp < browse.element(FluidSelectionToolbarIds.Toggle)!!.widthDp)
  }

  @Test fun `in selezione la barra non mette l'indietro della pagina, fuori si'`() {
    val browse = browsing(onBack = noop)
    assertNotNull("fuori selezione l'indietro c'è, come con buildFluidToolbar", browse.back)
    assertEquals(buildFluidToolbar(noop) { selectButton(onClick = noop); button("filter", FluidIcons.FilterList, "Filtra", onClick = noop) }
      .entries.map { it.id }, browse.entries.map { it.id })
    val select = selecting(state(1), onBack = noop)
    assertNull("in selezione il back esce dalla selezione: niente chevron che tolga la pagina", select.back)
    assertFalse(select.buttons.values.any { it.role == FluidToolbarRole.Back })
  }

  @Test fun `Fine svuota la selezione e chiama onDone, e il back fa lo stesso`() {
    val state = state(1, 2)
    val bar = selecting(state)
    bar.buttons.getValue(FluidSelectionToolbarIds.Toggle).onClick!!.invoke()
    assertFalse(state.isActive)
    assertEquals(1, done)
    state.select(7)
    selecting(state).selection!!.exit()
    assertFalse(state.isActive)
    assertEquals(2, done)
  }

  // --- D1: il gesto indietro predittivo ----------------------------------------------------------------------------

  /** La pagina sotto la selezione: risponde al back se nessuno sopra di lei lo prende. */
  private class PageBack : NavigationEventHandler<PageInfo>(PageInfo, true) {
    var started = 0
    var completed = 0
    override fun onBackStarted(event: NavigationEvent) { started++ }
    override fun onBackCompleted() { completed++ }
  }

  private object PageInfo : NavigationEventInfo()

  private fun event(x: Float, progress: Float) = NavigationEvent(
    touchX = x, touchY = 400f, progress = progress, swipeEdge = NavigationEvent.EDGE_LEFT, frameTimeMillis = 16L,
  )

  @Test fun `D1, il gesto predittivo annullato non cambia niente, e confermato esce dalla selezione e non dalla pagina`() {
    val dispatcher = NavigationEventDispatcher()
    val system = DirectNavigationEventInput()
    dispatcher.addInput(system)
    val page = PageBack()
    // L'host e la pagina si registrano prima: la selezione entra nella pila quando si apre, sopra di loro.
    dispatcher.addHandler(page)
    val state = state(1, 2, 3)
    var title = selecting(state).selection!!.title
    val gesture = FluidSelectionBackGesture { selecting(state).selection!!.exit() }
    dispatcher.addHandler(gesture)

    // Il dito parte, avanza fino a metà schermo e torna: annullato.
    system.backStarted(event(4f, 0f))
    for (step in 1..30) {
      system.backProgressed(event(4f + step * 12f, step / 30f * 0.6f))
      assertEquals("durante il gesto la selezione non cambia", setOf(1, 2, 3), (state.mode as FluidSelectionMode.Explicit).keys)
      assertEquals("e nemmeno il titolo", title, selecting(state).selection!!.title)
    }
    system.backCancelled()
    assertEquals("annullato: la selezione è com'era", setOf(1, 2, 3), (state.mode as FluidSelectionMode.Explicit).keys)
    assertEquals(0, done)
    assertEquals("la pagina non ha visto il gesto: lo prende la selezione", 0, page.started + page.completed)

    // Lo stesso gesto, confermato.
    system.backStarted(event(4f, 0f))
    for (step in 1..30) {
      system.backProgressed(event(4f + step * 12f, step / 30f * 0.6f))
      assertTrue("fino al rilascio la selezione c'è", state.isActive)
    }
    assertEquals(0, done)
    system.backCompleted()
    assertFalse("confermato: la selezione si chiude", state.isActive)
    assertEquals("una volta sola", 1, done)
    assertEquals("e la pagina resta", 0, page.completed)

    // Fuori selezione la pagina non compone più il gestore: il back torna della pagina.
    gesture.remove()
    title = "-"
    system.backStarted(event(4f, 0f))
    system.backCompleted()
    assertEquals(1, page.completed)
    assertEquals(1, done)
    assertEquals("-", title)
    page.remove()
  }

  @Test fun `il back senza gesto, coi tre tasti, esce dalla selezione, e spento lascia passare la pagina`() {
    val dispatcher = NavigationEventDispatcher()
    val system = DirectNavigationEventInput()
    dispatcher.addInput(system)
    val page = PageBack()
    dispatcher.addHandler(page)
    val state = state(5)
    val gesture = FluidSelectionBackGesture { selecting(state).selection!!.exit() }
    dispatcher.addHandler(gesture)
    system.backCompleted()
    assertFalse(state.isActive)
    assertEquals(1, done)
    assertEquals(0, page.completed)

    gesture.isBackEnabled = false
    system.backCompleted()
    assertEquals("spento: risponde la pagina", 1, page.completed)
    assertEquals(1, done)
    gesture.remove()
    page.remove()
  }

  @Test fun `un menu aperto dopo la selezione si chiude prima di lei`() {
    val dispatcher = NavigationEventDispatcher()
    val system = DirectNavigationEventInput()
    dispatcher.addInput(system)
    val page = PageBack()
    dispatcher.addHandler(page)
    val state = state(5)
    val gesture = FluidSelectionBackGesture { selecting(state).selection!!.exit() }
    dispatcher.addHandler(gesture)
    val menu = PageBack()
    dispatcher.addHandler(menu)
    system.backCompleted()
    assertEquals("il primo back chiude il menu", 1, menu.completed)
    assertTrue("la selezione resta", state.isActive)
    // Il menu chiuso non compone più il suo gestore.
    menu.remove()
    system.backCompleted()
    assertFalse("il secondo esce dalla selezione", state.isActive)
    // Fuori selezione la schermata non compone più il back della selezione (`FluidScreen`: solo con `selection`).
    gesture.remove()
    system.backCompleted()
    assertEquals("il terzo toglie la pagina", 1, page.completed)
    page.remove()
  }

  // --- Il canale bottomReplacement ----------------------------------------------------------------------------------

  private fun backdrop() = GlassBackdropState(emptyBackdrop(), null, 2.5.dp)
  private fun controller() = FluidChromeController(bottomBarVelocityThresholdPx = 0f, densityPx = 1f)
  private fun replacement(declaration: FluidToolbarDeclaration?) = FluidBottomReplacement().apply {
    this.declaration = declaration
    replacesTabBar = declaration?.bottomReplacesTabBar == true
  }

  @Test fun `il canale porta la barra in basso della pagina davanti con il segno della sostituzione`() {
    val chrome = controller()
    val list = replacement(browsing())
    chrome.registerBackdrop("elenco", backdrop())
    chrome.setBottomReplacement("elenco", list)
    chrome.setBackdropFront("elenco", true)
    assertSame(list, chrome.bottomReplacement.value)
    assertFalse("«sfoglia» non chiede il posto della tab bar", list.replacesTabBar)

    // La pagina entra in selezione: lo stesso oggetto, che adesso chiede il posto della tab bar.
    list.declaration = selecting(state(1))
    list.replacesTabBar = list.declaration!!.bottomReplacesTabBar
    assertSame(list, chrome.bottomReplacement.value)
    assertTrue(list.replacesTabBar)
    assertTrue("le azioni sono le voci in basso della dichiarazione", bottom(list.declaration!!).visibleIds().contains("delete"))

    // Le altre posizioni non chiedono il posto: sopra la tab bar, in alto, flottante.
    for (placement in listOf(FluidSelectionBarPlacement.AboveTabBar, FluidSelectionBarPlacement.Top,
      FluidSelectionBarPlacement.Floating)) {
      assertFalse("$placement", selecting(state(1), placement = placement).bottomReplacesTabBar)
    }

    // Chi la disegna lo dice (F6B.2): da lì la schermata non disegna la sua barra in basso.
    assertFalse(chrome.bottomReplacementClaimed)
    chrome.claimBottomReplacement("tab bar", true)
    assertTrue(chrome.bottomReplacementClaimed)
    chrome.claimBottomReplacement("tab bar", false)
    assertFalse(chrome.bottomReplacementClaimed)

    chrome.unregisterBackdrop("elenco")
    assertNull("se ne va con la sua schermata", chrome.bottomReplacement.value)
  }

  @Test fun `durante un gesto indietro il canale resta della pagina in cima, D1, annullato e confermato`() {
    val chrome = controller()
    val under = replacement(browsing())
    val over = replacement(selecting(state(1, 2)))
    chrome.registerBackdrop("sotto", backdrop())
    chrome.setBottomReplacement("sotto", under)
    chrome.registerBackdrop("sopra", backdrop())
    chrome.setBottomReplacement("sopra", over)
    chrome.setBackdropFront("sopra", true)
    assertSame(over, chrome.bottomReplacement.value)
    assertTrue(over.replacesTabBar)

    // Il gesto comincia: l'host tiene l'elezione. A metà uscita il «davanti» del vetro passa alla pagina sotto...
    chrome.holdBottomReplacement("host", true)
    chrome.setBackdropFront("sopra", false)
    chrome.setBackdropFront("sotto", true)
    assertSame("...ma la barra delle azioni resta quella della pagina in cima", over, chrome.bottomReplacement.value)
    assertNotNull("il vetro invece rifrange già la pagina sotto", chrome.activeBackdrop.value)

    // Annullato: il davanti torna su, la tenuta finisce quando la pagina si è posata. Non è cambiato niente.
    chrome.setBackdropFront("sotto", false)
    chrome.setBackdropFront("sopra", true)
    assertSame(over, chrome.bottomReplacement.value)
    chrome.holdBottomReplacement("host", false)
    assertSame(over, chrome.bottomReplacement.value)

    // Confermato: durante il gesto niente; alla conferma la pila cambia, la tenuta finisce, e la pagina sotto è davanti.
    chrome.holdBottomReplacement("host", true)
    chrome.setBackdropFront("sopra", false)
    chrome.setBackdropFront("sotto", true)
    assertSame(over, chrome.bottomReplacement.value)
    chrome.holdBottomReplacement("host", false)
    assertSame(under, chrome.bottomReplacement.value)
    assertFalse("la tab bar torna com'era", chrome.bottomReplacement.value!!.replacesTabBar)
    chrome.unregisterBackdrop("sopra")
    assertSame(under, chrome.bottomReplacement.value)
  }

  @Test fun `in selezione il gesto confermato chiude la selezione mentre la pagina resta, e il canale lo segue subito`() {
    // Il dito dentro l'app sulla pagina in selezione: l'host la porta sotto il dito (tenuta), il rilascio oltre la soglia
    // arriva al gestore della selezione, che la chiude; la pagina torna al suo posto con la molla dell'annullo.
    val chrome = controller()
    val state = state(1, 2)
    val page = replacement(selecting(state))
    chrome.registerBackdrop("posta", backdrop())
    chrome.setBottomReplacement("posta", page)
    chrome.setBackdropFront("posta", true)
    chrome.holdBottomReplacement("host", true)
    assertTrue(page.replacesTabBar)
    page.declaration!!.selection!!.exit()
    // La pagina ricompone fuori selezione: lo stesso oggetto eletto, che non chiede più il posto della tab bar.
    page.declaration = browsing()
    page.replacesTabBar = page.declaration!!.bottomReplacesTabBar
    assertSame(page, chrome.bottomReplacement.value)
    assertFalse("al rilascio confermato la barra torna tab bar", chrome.bottomReplacement.value!!.replacesTabBar)
    chrome.holdBottomReplacement("host", false)
    assertSame(page, chrome.bottomReplacement.value)
  }

  // --- Le posizioni ------------------------------------------------------------------------------------------------

  @Test fun `in basso le azioni stanno nella barra in basso e in alto restano Seleziona tutto, conteggio e Fine`() {
    val bar = selecting(state(1, 2))
    val up = top(bar).visibleIds()
    assertTrue(up.containsAll(listOf(FluidSelectionToolbarIds.SelectAll, FluidSelectionToolbarIds.Count, FluidSelectionToolbarIds.Toggle)))
    for (id in listOf("mark", "move", "archive", "delete")) assertFalse("$id non in alto", id in up)
    val down = bottom(bar)
    assertEquals(setOf("mark", "move", "archive", "delete"), down.visibleIds().toSet())
    assertTrue("tre gruppi al più, come vuole la barra", down.groups.size <= 3)
    assertTrue(bar.hasBottomBar)
    assertTrue(bar.bottomReservesSpace)
    // L'eliminazione è distruttiva e sta lo stesso in basso, nel suo vetro (uno spazio la stacca da sposta e archivia).
    assertTrue(bar.buttons.getValue("delete").destructive)
    val delete = down.element("delete")!!
    val archive = down.element("archive")!!
    assertTrue("vetri diversi", delete.groupId != archive.groupId)
    assertEquals("sposta e archivia in un vetro", down.element("move")!!.groupId, archive.groupId)
    assertEquals("«Segna» all'altro estremo", FluidToolbarSide.Leading, down.element("mark")!!.side)
  }

  @Test fun `in alto le azioni stanno nella barra in alto, in Altro quando i tre gruppi sono pieni, e in basso niente`() {
    val bar = selecting(state(1), placement = FluidSelectionBarPlacement.Top)
    assertFalse(bar.hasBottomBar)
    // Una sola azione e niente «Seleziona tutto»: ci sta accanto a «Fine».
    val one = top(selecting(state(1), placement = FluidSelectionBarPlacement.Top, selectAll = null,
      actions = FluidSelectionActions.deleteOnly(onDelete = noop)), width = 600f)
    assertTrue(one.visibleIds().containsAll(listOf("delete", FluidSelectionToolbarIds.Count, FluidSelectionToolbarIds.Toggle)))
    assertEquals("«Fine» è il più esterno a destra", FluidSelectionToolbarIds.Toggle,
      one.elements.filter { it.side == FluidToolbarSide.Trailing && !it.inOverflow }.maxBy { it.leftDp }.item.id)
    // Mail: conteggio, «Fine» e «Altro» sono già tre gruppi anche su un tablet (R5 B11). Le azioni stanno in «Altro»,
    // e con loro «Seleziona tutto»; il conteggio e «Fine» restano.
    val up = top(bar, width = 900f)
    assertTrue(up.fits)
    assertTrue(up.visibleIds().containsAll(listOf(FluidSelectionToolbarIds.Count, FluidSelectionToolbarIds.Toggle)))
    assertTrue(up.overflow.map { it.id }.containsAll(listOf("mark", "move", "archive", "delete", FluidSelectionToolbarIds.SelectAll)))
  }

  @Test fun `la capsula flottante non chiede spazio alla lista, le altre in basso si'`() {
    assertFalse(selecting(state(1), placement = FluidSelectionBarPlacement.Floating).bottomReservesSpace)
    assertTrue(selecting(state(1), placement = FluidSelectionBarPlacement.Floating).hasBottomBar)
    assertTrue(selecting(state(1), placement = FluidSelectionBarPlacement.AboveTabBar).bottomReservesSpace)
    assertTrue(selecting(state(1), placement = FluidSelectionBarPlacement.ReplacingTabBar).bottomReservesSpace)
    assertTrue("fuori selezione come prima", buildFluidScreenToolbar(null) {
      button("a", FluidIcons.MoreHoriz, "A", onClick = noop, placement = FluidToolbarPlacement.BottomBar)
    }.bottomReservesSpace)
  }

  @Test fun `una selezione sola per barra`() {
    val failure = runCatching {
      buildFluidScreenToolbar(null) {
        selection(state(1), count = null, onSelectAll = null, onDone = noop) {}
        selection(state(2), count = null, onSelectAll = null, onDone = noop) {}
      }
    }.exceptionOrNull()
    assertTrue(failure is IllegalArgumentException)
  }

  @Test fun `il portatore della selezione e' inerte per il modello`() {
    val bar = selecting(state(1), actions = {})
    // Il gruppo vuoto non dà vetri né spazio: in alto solo i tre posti, in basso niente.
    assertEquals(listOf(FluidSelectionToolbarIds.SelectAll, FluidSelectionToolbarIds.Count, FluidSelectionToolbarIds.Toggle).toSet(),
      top(bar).elements.map { it.item.id }.toSet())
    assertFalse(bar.hasBottomBar)
    // Due composizioni con callback nuove danno lo stesso modello.
    assertEquals(model(selecting(state(1), actions = {})), model(selecting(state(1), actions = {})))
  }

  // --- I preset ----------------------------------------------------------------------------------------------------

  @Test fun `i preset scrivono azioni, priorita' e trabocco`() {
    val mail = selecting(state(1))
    assertEquals(FluidToolbarPriority.Low, mail.buttons.getValue("mark").priority)
    assertTrue("nessuna azione dei preset ha priorità alta", mail.buttons.values
      .filter { it.id in listOf("mark", "move", "archive", "delete") }.none { it.priority == FluidToolbarPriority.High })
    // Stretta, la barra in basso fa uscire prima il meno importante, e «Elimina» (l'ultima) resta.
    val narrow = bottom(mail, width = 210f)
    assertTrue(narrow.fits)
    assertTrue("«Segna» va in «Altro» per primo", narrow.overflow.any { it.id == "mark" })
    assertTrue("«Elimina» resta", "delete" in narrow.visibleIds())

    val gallery = selecting(state(1), actions = FluidSelectionActions.gallery(onShare = noop, onDelete = noop,
      onFavorite = noop, onAddToAlbum = noop, onHide = noop, onDuplicate = noop))
    val g = bottom(gallery)
    assertTrue(g.fits)
    assertEquals(setOf("share", "favorite", "album", "delete"), g.visibleIds().filter { it != FluidToolbarOverflowId }.toSet())
    assertTrue("«Nascondi» e «Duplica» sempre in «Altro»", g.overflow.map { it.id }.containsAll(listOf("hide", "duplicate")))
    assertEquals(FluidToolbarSide.Leading, g.element("share")!!.side)
    assertTrue("tre gruppi con «Altro»", g.groups.size <= 3)

    val files = selecting(state(1), actions = FluidSelectionActions.files(onShare = noop, onMove = noop, onDelete = noop,
      onDuplicate = noop, onCompress = noop))
    val f = bottom(files)
    assertTrue(f.fits)
    assertTrue(f.visibleIds().containsAll(listOf("share", "duplicate", "move", "delete")))
    assertTrue(f.overflow.any { it.id == "compress" })
    assertTrue(f.groups.size <= 3)

    val only = selecting(state(1), actions = FluidSelectionActions.deleteOnly(onDelete = noop))
    assertEquals(listOf("delete"), bottom(only).visibleIds())
    assertTrue(only.buttons.getValue("delete").destructive)
    assertEquals("anche da sola sta in basso", FluidToolbarPlacement.BottomBar, only.buttons.getValue("delete").placement)
  }

  @Test fun `le azioni dei preset in alto non usano spaziatori e non chiedono la barra in basso`() {
    for (preset in listOf(
      FluidSelectionActions.mail(onDelete = noop, onArchive = noop, onMove = noop, onMarkRead = noop),
      FluidSelectionActions.gallery(onShare = noop, onDelete = noop, onFavorite = noop),
      FluidSelectionActions.files(onShare = noop, onMove = noop, onDelete = noop, onDuplicate = noop),
    )) {
      val bar = selecting(state(1), placement = FluidSelectionBarPlacement.Top, actions = preset)
      val group = bar.entries.filterIsInstance<FluidToolbarGroupEntry>().single { it.id == FluidSelectionToolbarIds.Actions }
      assertTrue(group.children.none { it is FluidToolbarSpacerEntry })
      assertFalse(bar.hasBottomBar)
    }
  }

  @Test fun `le azioni su misura leggono selectionHasItems`() {
    var seen: Boolean? = null
    buildFluidScreenToolbar(null) {
      selection(state(), count = null, onSelectAll = null, onDone = noop) { seen = selectionHasItems }
    }
    assertEquals(false, seen)
    buildFluidScreenToolbar(null) { seen = selectionHasItems }
    assertEquals("fuori da una selezione", true, seen)
  }

  // --- La scena del banco: la barra in basso che cambia azioni, senza tab bar ---------------------------------------

  /**
   * Le tracce del banco (con `GLASS_F6B1_DIR`): la barra in basso di una pagina senza tab bar che entra in selezione e
   * ne esce, dalle dichiarazioni vere di `selection { }` e dei preset, risolte, ricordate e guidate dal Kotlin di
   * produzione. L'esportatore è quello delle barre di F4A e F4B.1 (`F4aToolbarExportTest.trace`, chiamato per
   * riflessione per non copiarlo): stesse curve, stesso piano, stesso contenuto. La barra in basso vede solo le sue voci,
   * quindi la dichiarazione passata al banco è il gruppo delle azioni (in alto stanno «Fine» e il conteggio).
   */
  @Test fun `esporta la barra in basso che cambia azioni senza tab bar`() {
    val output = dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B1_DIR"))
    fun onlyBottom(declaration: FluidToolbarDeclaration) = FluidToolbarDeclaration(
      declaration.entries.filter { it.declaresBottomAction(false) }, declaration.titleMode, null, null, null,
    )
    fun gallery(selecting: Boolean) = onlyBottom(buildFluidScreenToolbar(noop) {
      if (selecting) {
        selection(state(1, 2, 3), count = null, onSelectAll = noop, onDone = noop,
          actions = FluidSelectionActions.gallery(onShare = noop, onDelete = noop, onFavorite = noop,
            onAddToAlbum = noop, onHide = noop))
      } else {
        selectButton(onClick = noop)
        group("foto", placement = FluidToolbarPlacement.BottomBar) {
          button("share", FluidIcons.IosShare, "Condividi", onClick = noop)
          flexibleSpacer()
          button("favorite", FluidIcons.Favorite, "Preferiti", onClick = noop)
          button("add", FluidIcons.Add, "Aggiungi", onClick = noop)
        }
      }
    })
    fun files(selecting: Boolean) = onlyBottom(buildFluidScreenToolbar(noop) {
      if (selecting) {
        selection(state(1, 2), count = null, onSelectAll = noop, onDone = noop,
          actions = FluidSelectionActions.files(onShare = noop, onMove = noop, onDelete = noop, onDuplicate = noop,
            onCompress = noop))
      } else {
        selectButton(onClick = noop)
        group("file", placement = FluidToolbarPlacement.BottomBar) {
          button("filter", FluidIcons.FilterList, "Filtra", onClick = noop)
          flexibleSpacer()
          button("add", FluidIcons.Add, "Nuova cartella", onClick = noop)
        }
      }
    })
    val exporter = Class.forName("dev.antigravity.fluidengine.ui.glass.field.trace.F4aToolbarExportTest")
    val instance = exporter.getDeclaredConstructor().newInstance()
    val input = Class.forName("dev.antigravity.fluidengine.ui.glass.field.trace.F4aToolbarExportTest\$Input")
      .getDeclaredConstructor(Double::class.javaPrimitiveType, String::class.java, FluidToolbarDeclaration::class.java)
      .apply { isAccessible = true }
    val trace = exporter.declaredMethods.single { it.name == "trace" && it.parameterCount == 9 }.apply { isAccessible = true }
    for ((name, scene) in listOf("selezione-senza-tabbar" to ::gallery, "selezione-senza-tabbar-file" to ::files)) {
      for (dark in listOf(false, true)) {
        val inputs = listOf(
          input.newInstance(-100.0, "sfoglia", scene(false)),
          input.newInstance(0.0, "seleziona", scene(true)),
          input.newInstance(1100.0, "sfoglia", scene(false)),
        )
        val traceName = if (dark) "$name-scuro" else name
        val result = trace.invoke(instance, traceName, inputs, 300, dark, false, FluidToolbarKeptContent.Soft, false,
          "f6b-1", FluidToolbarBar.Bottom) as dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
        assertTrue(result.frames.all { it.elements.isNotEmpty() })
        output?.let { result.write(java.io.File(it, "$traceName/trace.json")) }
      }
    }
    // Le stesse dichiarazioni, risolte: la condivisione resta lo stesso vetro e il gruppo a destra cambia larghezza.
    val before = bottom(gallery(false))
    val after = bottom(gallery(true))
    assertEquals(FluidToolbarSide.Leading, before.element("share")!!.side)
    assertEquals(FluidToolbarSide.Leading, after.element("share")!!.side)
    assertEquals(before.element("share")!!.leftDp, after.element("share")!!.leftDp, 0f)
    assertTrue(after.groups.size <= 3)
  }

  // --- Il segno nelle righe ----------------------------------------------------------------------------------------

  @Test fun `lo spazio di una riga senza leading si apre con lo scambio e non salta`() {
    assertEquals(0f, leadingOpening(0f), 0f)
    assertEquals(1f, leadingOpening(1f), 0f)
    var last = 0f
    for (i in 1..100) {
      val now = leadingOpening(i / 100f)
      assertTrue("monotona", now >= last)
      assertTrue("nessun passo oltre il 4% per centesimo", now - last <= 0.04f)
      last = now
    }
  }
}
