package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationCountdown
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.FluidSplitButtonDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidToolbarSplitType
import dev.antigravity.fluidengine.ui.fluid.FluidUndoCountdown
import dev.antigravity.fluidengine.ui.fluid.FluidUndoCountdownState
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityDisplay
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityIds
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityLabels
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityPlacement
import dev.antigravity.fluidengine.ui.fluid.activity.activity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityReadout
import dev.antigravity.fluidengine.ui.fluid.activity.fluidToolbarWithActivityOf
import dev.antigravity.fluidengine.ui.fluid.fluidSplitTargets
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.FilterList
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneChange
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMatch
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneTransition
import dev.antigravity.fluidengine.ui.glass.field.planeJourneyFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6B.11 in logica pura: **un tasto che cambia nello stesso posto resta un vetro solo** (scelta di Alessio del 22 novembre
 * 2026), il **tasto diviso** e il **titolo che diventa selettore**. La resa (semantica, bersagli nell'albero vero, nessuna
 * intersezione sul telefono e sul tablet) la provano i test Robolectric della galleria (`FluidSplitTitleUiTest`); i
 * fotogrammi il banco (`FluidToolbarSplitTitleTraceTest`).
 */
class FluidToolbarSplitTitleTest {

  private val metrics = fluidToolbarMetrics()
  private val textWidth: (String) -> Float = { it.length * 9f + FluidToolbarDefaults.TextPadding.value * 2f }
  private val noop: () -> Unit = {}

  private fun resolve(declaration: FluidToolbarDeclaration, width: Float = 412f): FluidToolbarResolved =
    resolveFluidToolbar(fluidToolbarModel(declaration, textWidth, title = "Posta"), width, metrics = metrics)

  private fun selection() = FluidSelectionState<Int>().apply { select(1) }

  /** La posta che sfoglia: l'indietro della schermata, «Seleziona», il filtro. */
  private fun browsing(): FluidToolbarDeclaration = buildFluidScreenToolbar(noop) {
    selectButton(onClick = noop)
    button("filtri", FluidIcons.FilterList, "Filtri", onClick = noop)
  }

  /** La stessa pagina in selezione: «Seleziona tutto» al posto dell'indietro, il conteggio, «Fine». */
  private fun selecting(): FluidToolbarDeclaration = buildFluidScreenToolbar(noop) {
    selection(selection(), count = null, onSelectAll = noop, onDone = noop,
      actions = FluidSelectionActions.mail(onDelete = noop, onArchive = noop, onMove = noop, onMarkRead = noop))
  }

  private fun FluidToolbarResolved.groupOf(id: Any): FluidToolbarGroup = groups.single { group -> group.items.any { it.id == id } }

  // ---------------------------------------------------------------------------------------------------------------
  // 1. Un tasto che cambia nello stesso posto resta un vetro solo
  // ---------------------------------------------------------------------------------------------------------------

  @Test fun `l'indietro che diventa Seleziona tutto e' lo stesso vetro, col contenuto che si scambia dentro`() {
    val before = resolve(browsing())
    val after = resolve(selecting())
    val back = before.groupOf(FluidToolbarIds.Back)
    val all = after.groupOf(FluidSelectionToolbarIds.SelectAll)
    val diff = diffFluidToolbar(before, after)
    val inPlace = diff.groups.single { it.inPlace }
    assertEquals(listOf(back), inPlace.from)
    assertEquals(listOf(all), inPlace.to)
    assertEquals(FluidToolbarGroupChange.Matched, inPlace.change)
    // Il vetro: lo stesso id per il piano.
    val ids0 = fluidToolbarGlassIds(before)
    val ids1 = fluidToolbarGlassIds(after, ids0, before, diff)
    assertEquals("lo stesso vetro", ids0.getValue(back.id), ids1.getValue(all.id))
    assertEquals("tutti diversi", ids1.size, ids1.values.toSet().size)
    // Il contenuto: l'indietro esce e «Seleziona tutto» entra, dentro il vetro che resta. Nessuna lente.
    val leaving = diff.elements.single { it.from?.item?.id == FluidToolbarIds.Back }
    val entering = diff.elements.single { it.to?.item?.id == FluidSelectionToolbarIds.SelectAll }
    assertEquals(FluidToolbarElementChange.MergeIntoNeighbour, leaving.change)
    assertEquals(FluidSelectionToolbarIds.SelectAll, leaving.neighbourId)
    assertTrue(leaving.insideGroup)
    assertEquals(FluidToolbarContentChange.BlurDisappear, leaving.content)
    assertEquals(FluidToolbarElementChange.BornFromNeighbour, entering.change)
    assertEquals(FluidToolbarIds.Back, entering.neighbourId)
    assertTrue(entering.insideGroup)
    assertEquals(FluidToolbarContentChange.BlurAppear, entering.content)
    // E al ritorno, lo stesso vetro di nuovo.
    val back2 = fluidToolbarGlassIds(before, ids1, after, diffFluidToolbar(after, before))
    assertEquals(ids0.getValue(back.id), back2.getValue(back.id))
  }

  @Test fun `la memoria della barra fa uscire l'indietro e entrare Seleziona tutto nello stesso vetro`() {
    val before = resolve(browsing())
    val after = resolve(selecting())
    val memory = FluidToolbarMemory()
    memory.update(before, FluidToolbarMotion.Full)
    val glass = memory.glassIds.getValue(before.groupOf(FluidToolbarIds.Back).id)
    memory.update(after, FluidToolbarMotion.Full)
    assertEquals(glass, memory.glassIds.getValue(after.groupOf(FluidSelectionToolbarIds.SelectAll).id))
    val ghost = memory.ghosts.single { it.element.item.id == FluidToolbarIds.Back }
    assertTrue("il contenuto che esce sta nel vetro che resta", ghost.glassStays)
    assertEquals(glass, ghost.glass)
    assertTrue("il contenuto nuovo entra sullo stesso orologio", FluidSelectionToolbarIds.SelectAll in memory.arrivals)
  }

  /** I vetri del piano di uno stato della barra, come li posa la barra (il pezzo, l'id del vetro, l'ingresso). */
  private fun plane(resolved: FluidToolbarResolved, ids: Map<FluidToolbarGroupId, FluidToolbarGlassId>): GlassPlaneState {
    val pieces = resolved.groups.filter { it.sharedBackground }.map { group ->
      val bounds = Rect(group.leftDp, 0f, group.leftDp + group.widthDp, metrics.buttonWidthDp)
      GlassPlanePiece(ids.getValue(group.id).toString(), bounds, FluidCornerRadii.all(bounds.height / 2f), id = ids.getValue(group.id),
        transition = if (fluidToolbarGlassTransition(group) == dev.antigravity.fluidengine.ui.fluid.FluidGlassTransition.MaterializeWhenAlone)
          GlassPlaneTransition.MaterializeWhenAlone else GlassPlaneTransition.MatchedGeometry,
        contentKey = fluidToolbarGlassContentKey(group))
    }
    return GlassPlaneState(pieces, metrics.groupSpacingDp, Rect(0f, 0f, resolved.widthDp, metrics.buttonWidthDp))
  }

  /** In ogni fotogramma a 120 Hz, quanti vetri del piano coprono il posto a sinistra (l'indietro, «Seleziona tutto»). */
  private fun glassesOverTheLeadingSlot(from: GlassPlaneState, to: GlassPlaneState, slot: Rect): List<Int> =
    (0..96).map { index ->
      planeJourneyFrame(index * 1000.0 / 120.0, from, to).pieces.count { it.bounds.overlaps(slot) }
    }

  @Test fun `nel piano non c'e' mai un fotogramma con due vetri nel posto dell'indietro`() {
    val before = resolve(browsing())
    val after = resolve(selecting())
    val ids0 = fluidToolbarGlassIds(before)
    val ids1 = fluidToolbarGlassIds(after, ids0, before, diffFluidToolbar(before, after))
    val slot = before.groupOf(FluidToolbarIds.Back).let { Rect(it.leftDp + 4f, 4f, it.leftDp + it.widthDp - 4f, metrics.buttonWidthDp - 4f) }
    val counts = glassesOverTheLeadingSlot(plane(before, ids0), plane(after, ids1), slot)
    assertTrue("un vetro solo in ogni fotogramma: $counts", counts.all { it == 1 })
    val first = planeJourneyFrame(8.3, plane(before, ids0), plane(after, ids1)).pieces.single { it.bounds.overlaps(slot) }
    assertEquals(GlassPlaneChange.Matched, first.change)
    assertEquals(GlassPlaneMatch.Id, first.matchedBy)
    // Il controllo vede il difetto: con due vetri diversi (com'era fino a F6B.10) i fotogrammi con due contorni ci sono.
    val fresh = ids1.mapValues { (group, glass) ->
      if (group == after.groupOf(FluidSelectionToolbarIds.SelectAll).id) FluidToolbarGlassId(group, 7) else glass
    }
    val old = glassesOverTheLeadingSlot(plane(before, ids0), plane(after, fresh), slot)
    assertTrue("com'era: due vetri sovrapposti in qualche fotogramma ($old)", old.any { it >= 2 })
  }

  @Test fun `l'indietro che diventa Chiudi con un altro id resta lo stesso vetro`() {
    val before = resolve(buildFluidToolbar(noop) { button("share", FluidIcons.IosShare, "Condividi", onClick = noop) })
    val after = resolve(buildFluidToolbar(null) {
      close(onClick = noop)
      button("share", FluidIcons.IosShare, "Condividi", onClick = noop)
    })
    val diff = diffFluidToolbar(before, after)
    val ids0 = fluidToolbarGlassIds(before)
    val ids1 = fluidToolbarGlassIds(after, ids0, before, diff)
    assertEquals(ids0.getValue(before.groupOf(FluidToolbarIds.Back).id), ids1.getValue(after.groupOf(FluidToolbarIds.Close).id))
  }

  @Test fun `un tasto che nasce o se ne va davvero tiene la lente`() {
    // L'indietro che se ne va senza che nessuno prenda il suo posto.
    val withBack = resolve(buildFluidToolbar(noop) { button("share", FluidIcons.IosShare, "Condividi", onClick = noop) })
    val without = resolve(buildFluidToolbar(null) { button("share", FluidIcons.IosShare, "Condividi", onClick = noop) })
    val gone = diffFluidToolbar(withBack, without)
    assertTrue(gone.groups.none { it.inPlace })
    assertEquals(FluidToolbarElementChange.Dematerialize, gone.elements.single { it.from?.item?.id == FluidToolbarIds.Back }.change)
    val born = diffFluidToolbar(without, withBack)
    assertEquals(FluidToolbarElementChange.Materialize, born.elements.single { it.to?.item?.id == FluidToolbarIds.Back }.change)
  }

  @Test fun `fra due vetri ordinari nello stesso posto decide il piano, come prima`() {
    // Nessuna lente in gioco: il piano fa la staffetta (F3.11), e la barra non cambia niente.
    val a = resolve(buildFluidToolbar(null) { button("a", FluidIcons.Add, "A", onClick = noop, placement = FluidToolbarPlacement.Leading) })
    val b = resolve(buildFluidToolbar(null) { button("b", FluidIcons.MoreHoriz, "B", onClick = noop, placement = FluidToolbarPlacement.Leading) })
    assertTrue(diffFluidToolbar(a, b).groups.none { it.inPlace })
  }

  @Test fun `un contatore temporaneo che chiede la lente la tiene, anche nello stesso posto`() {
    val countdown = FluidUndoCountdown(FluidUndoCountdownState(FluidNotificationCountdown(5_000, 0L), { 0L }), onUndo = noop)
    val withBack = resolve(buildFluidToolbar(noop) {})
    val withUndo = resolve(buildFluidToolbar(null) {
      undoCountdown("annulla", countdown, placement = FluidToolbarPlacement.Navigation)
    })
    assertTrue(diffFluidToolbar(withBack, withUndo).groups.none { it.inPlace })
  }

  @Test fun `un tasto che c'e' nei due stati non e' mai una sostituzione`() {
    val before = resolve(browsing())
    val after = resolve(buildFluidScreenToolbar(noop) {
      selectButton(onClick = noop)
      button("filtri", FluidIcons.FilterList, "Filtri", onClick = noop)
      button("nuovo", FluidIcons.Add, "Nuovo", onClick = noop)
    })
    assertTrue(diffFluidToolbar(before, after).groups.none { it.inPlace })
  }

  /** Un tipo che chiede la lente (come la pillola di attività), per la regola sul centro. */
  private data class LensKind(override val identity: Any) : FluidToolbarKind {
    override val type: String get() = "prova-lente"
    override val state: Any? get() = null
    override val materializes: Boolean get() = true
  }

  @Test fun `al centro un elemento che chiede la lente al posto di un altro vetro resta lo stesso vetro`() {
    fun bar(lens: Boolean) = FluidToolbarModel(listOf(
      FluidToolbarItem(FluidToolbarIds.Back, FluidToolbarRole.Back, FluidToolbarPlacement.Navigation, symbol = "<"),
      if (lens) FluidToolbarItem("pillola", FluidToolbarRole.Text, FluidToolbarPlacement.Principal, label = "Sincronizzo",
        widthDp = 140f, kind = LensKind("sync"))
      else FluidToolbarItem("cartella", FluidToolbarRole.Text, FluidToolbarPlacement.Principal, label = "Cartella", widthDp = 120f),
    ))
    val a = resolveFluidToolbar(bar(false), 412f, metrics = metrics)
    val b = resolveFluidToolbar(bar(true), 412f, metrics = metrics)
    val diff = diffFluidToolbar(a, b)
    val inPlace = diff.groups.single { it.inPlace }
    assertEquals("cartella", inPlace.from.single().items.single().id)
    assertEquals("pillola", inPlace.to.single().items.single().id)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // 2. Il tasto diviso
  // ---------------------------------------------------------------------------------------------------------------

  private val menu: () -> List<FluidContextAction> = { listOf(FluidContextAction("Programma invio", onClick = noop)) }

  @Test fun `il tasto diviso e' un vetro solo, largo quanto la scritta piu' il filo e la freccia, e non si unisce alle icone`() {
    val declaration = buildFluidToolbar(null) {
      button("allega", FluidIcons.Add, "Allega", onClick = noop)
      split("invia", "Invia", onClick = noop, menu = menu)
      button("condividi", FluidIcons.IosShare, "Condividi", onClick = noop)
    }
    val resolved = resolve(declaration)
    val split = resolved.element("invia")!!
    assertEquals(textWidth("Invia") + FluidSplitButtonDefaults.Hairline.value + FluidSplitButtonDefaults.ArrowZone.value,
      split.widthDp, 0.001f)
    assertEquals("un gruppo a sé", listOf("invia"), resolved.groupOf("invia").items.map { it.id })
    assertEquals(FluidToolbarKinds.Split, split.item.kind?.type)
    val entry = declaration.buttons.getValue("invia")
    assertNotNull(entry.onClick)
    assertSame(menu, entry.menu)
    assertTrue(entry.type is FluidToolbarSplitType)
    assertEquals(FluidSplitButtonDefaults.ArrowZone, entry.type!!.menuZone)
  }

  @Test fun `il tasto diviso con l'icona ha la zona dell'azione da 48 dp e quella della freccia`() {
    val resolved = resolve(buildFluidToolbar(null) {
      split("invia", FluidIcons.Send, "Invia", onClick = noop, menuLabel = "Opzioni di invio", menu = menu)
    })
    val split = resolved.element("invia")!!
    assertEquals(48f + FluidSplitButtonDefaults.Hairline.value + FluidSplitButtonDefaults.ArrowZone.value, split.widthDp, 0.001f)
  }

  @Test fun `i due bersagli sono disgiunti, di almeno 48 dp, e non arrivano al vetro vicino`() {
    for (width in listOf(87f, 109f, 140f, 220f)) {
      val t = fluidSplitTargets(width, FluidSplitButtonDefaults.ArrowZone.value, 48f)
      assertTrue("$width: azione ${t.actionWidth}", t.actionWidth >= 48f)
      assertTrue("$width: freccia ${t.menuWidth}", t.menuWidth >= 48f)
      assertEquals("$width: disgiunti e contigui al filo", t.actionRight, t.menuLeft, 0.001f)
      assertTrue("$width: l'azione parte dal vetro o prima", t.actionLeft <= 0f)
      assertTrue("$width: la freccia arriva alla fine del vetro", t.menuRight >= width)
      // Fuori dal vetro al più quanto i 12 dp fra due gruppi: il bersaglio non entra nel vicino.
      assertTrue("$width: ${t.menuRight - width} dp fuori", t.menuRight - width < metrics.groupSpacingDp)
      assertTrue("$width: ${-t.actionLeft} dp fuori", -t.actionLeft < metrics.groupSpacingDp)
    }
  }

  @Test fun `action con onClick ha l'azione e il menu, e quella di sempre solo il menu`() {
    val declaration = buildFluidToolbar(null) {
      action("rispondi", FluidIcons.Send, "Rispondi", onClick = noop, menu = menu)
      action("ordina", FluidIcons.FilterList, "Ordina", menu = menu)
    }
    assertNotNull(declaration.buttons.getValue("rispondi").onClick)
    assertNotNull(declaration.buttons.getValue("rispondi").menu)
    assertNull(declaration.buttons.getValue("ordina").onClick)
    // Nel modello sono due icone dello stesso gruppo, come prima.
    assertEquals(2, resolve(declaration).groupOf("rispondi").items.size)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // 3. Il titolo che diventa selettore
  // ---------------------------------------------------------------------------------------------------------------

  @Test fun `il titolo con il menu o col pannello e' un selettore della dichiarazione, e il titolo resta`() {
    val accounts: () -> List<FluidContextAction> = { listOf(FluidContextAction("Lavoro", onClick = noop)) }
    val withMenu = buildFluidToolbar(noop) { title(FluidToolbarTitleMode.Large, menu = accounts, selectorHint = "Cambia account") }
    assertSame(accounts, withMenu.titleSelector!!.menu)
    assertNull(withMenu.titleSelector!!.panel)
    assertEquals("Cambia account", withMenu.titleSelector!!.hint)
    assertFalse("il titolo non cede il posto: è lui il selettore", withMenu.hasPrincipal)
    val withPanel = buildFluidToolbar(null) { title(FluidToolbarTitleMode.Inline, panel = { }) }
    assertNotNull(withPanel.titleSelector!!.panel)
    assertNull(buildFluidToolbar(null) { title(FluidToolbarTitleMode.Inline) }.titleSelector)
    assertThrows(IllegalArgumentException::class.java) {
      buildFluidToolbar(null) { title(menu = accounts, panel = { }) }
    }
    // L'indietro aggiunto dalla schermata non lo perde.
    assertNotNull(buildFluidScreenToolbar(noop) { title(menu = accounts) }.titleSelector)
  }

  @Test fun `il menu del titolo nasce da un quadrato attorno al disco, e mai da un riquadro degenere`() {
    val center = androidx.compose.ui.geometry.Offset(300f, 50f)
    val anchor = fluidTitleSelectorAnchor(center, 18f, 44f)
    assertEquals("il lato di un tasto della barra", 44f, anchor.width, 0f)
    assertEquals(44f, anchor.height, 0f)
    assertEquals(center, anchor.center)
    assertEquals("un disco più grande (il titolo grande) tiene il suo diametro", 60f,
      fluidTitleSelectorAnchor(center, 60f, 44f).width, 0f)
    assertTrue(fluidTitleSelectorAnchorUsable(anchor))
    assertFalse("alto zero (il titolo fuori vista)", fluidTitleSelectorAnchorUsable(Rect(0f, 0f, 100f, 0f)))
    assertFalse("rovesciato", fluidTitleSelectorAnchorUsable(Rect(10f, 10f, 0f, 20f)))
    assertFalse("non finito", fluidTitleSelectorAnchorUsable(Rect(0f, 0f, Float.NaN, 10f)))
  }

  @Test fun `con il titolo selettore la pillola di attivita' gli sta accanto, minima, e il titolo resta`() {
    val accounts: () -> List<FluidContextAction> = { listOf(FluidContextAction("Lavoro", onClick = noop)) }
    val declaration = buildFluidToolbar(noop) {
      title(FluidToolbarTitleMode.Inline, menu = accounts)
      button("cerca", FluidIcons.FilterList, "Cerca", onClick = noop)
    }
    val bar = fluidToolbarWithActivityOf(declaration, FluidActivityDisplay(FluidActivity.sync(progress = { 0.4f })),
      FluidActivityLabels(), 160f, FluidActivityReadout.Percent, false) {}
    assertNotNull("il selettore resta", bar.titleSelector)
    val pill = bar.buttons.getValue(FluidActivityIds.Pill)
    assertNotEquals("non al posto del titolo", FluidToolbarPlacement.Principal, pill.placement)
    assertFalse("il titolo non cede il posto", bar.hasPrincipal)
    val resolved = resolve(bar)
    val element = resolved.element(FluidActivityIds.Pill)!!
    assertEquals(FluidToolbarSide.Trailing, element.side)
    assertTrue("minima, il solo anello: ${element.widthDp} dp contro i 160 della compatta", element.widthDp < 80f)
    // Con `activity(placement = Title)` dichiarato dalla pagina vince la pagina: la pillola prende il posto del titolo.
    val explicit = buildFluidToolbar(noop) {
      title(FluidToolbarTitleMode.Inline, menu = accounts)
      activity(placement = FluidActivityPlacement.Title)
    }
    val taken = fluidToolbarWithActivityOf(explicit, FluidActivityDisplay(FluidActivity.sync(progress = { 0.4f })),
      FluidActivityLabels(), 160f, FluidActivityReadout.Percent, false) {}
    assertEquals(FluidToolbarPlacement.Principal, taken.buttons.getValue(FluidActivityIds.Pill).placement)
  }
}
