package dev.antigravity.fluidengine.ui.fluid.toolbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Risultati attesi della matrice R7, non prove di disegno: nessuna composizione né dispositivo. */
class FluidToolbarModelTest {
  private val metrics = FluidToolbarMetrics(44f, 12f, 16f, 44f)

  private fun resolve(model: FluidToolbarModel, width: Float, searchActive: Boolean = false, bar: FluidToolbarBar = FluidToolbarBar.Top) =
    resolveFluidToolbar(model, width, searchActive, metrics, bar)

  private fun icon(id: String, symbol: String = id, placement: FluidToolbarPlacement = FluidToolbarPlacement.Trailing) =
    FluidToolbarItem(id, symbol = symbol, placement = placement)
  private fun text(id: String, label: String = id, width: Float = 80f) =
    FluidToolbarItem(id, FluidToolbarRole.Text, label = label, widthDp = width)
  private fun primary(id: String, symbol: String = "check") = icon(id, symbol).copy(role = FluidToolbarRole.Primary, tintArgb = 0xff007affL)
  private fun back(id: String = "back") = icon(id, "chevron", FluidToolbarPlacement.Navigation).copy(role = FluidToolbarRole.Back)
  private fun close(id: String = "close") = icon(id, "x", FluidToolbarPlacement.Navigation).copy(role = FluidToolbarRole.Close)
  private fun fixed(id: String = "gap", width: Float? = null) =
    FluidToolbarItem(id, FluidToolbarRole.FixedSpacer, widthDp = width)
  private fun group(id: String, vararg items: FluidToolbarItem) =
    FluidToolbarItem(id, FluidToolbarRole.Group, children = items.toList())
  private fun state(vararg items: FluidToolbarItem, width: Float = 600f) = resolve(FluidToolbarModel(items.toList()), width)
  private fun change(old: List<FluidToolbarItem>, new: List<FluidToolbarItem>) = diffFluidToolbar(state(*old.toTypedArray()), state(*new.toTypedArray()))
  private fun FluidToolbarDiff.at(id: Any): FluidToolbarElementDiff = elements.single { it.to?.item?.id == id }
  private fun FluidToolbarDiff.leaving(id: Any): FluidToolbarElementDiff = elements.single { it.from?.item?.id == id && it.to == null }
  private fun assertContent(diff: FluidToolbarDiff, id: Any, expected: FluidToolbarContentChange) = assertEquals(expected, diff.at(id).content)
  private fun assertGroups(state: FluidToolbarResolved, vararg expected: List<Any>) =
    assertEquals(expected.toList(), state.groups.map { it.items.map { item -> item.id } })

  @Test fun `icone di serie insieme con passo 44 margine 16 e gruppi a 12`() {
    val resolved = state(icon("a"), icon("b"), fixed(), icon("c"))
    assertGroups(resolved, listOf("a", "b"), listOf("c"))
    assertEquals(44f, resolved.element("b")!!.leftDp - resolved.element("a")!!.leftDp, 0f)
    assertEquals(12f, resolved.groups[1].leftDp - resolved.groups[0].leftDp - resolved.groups[0].widthDp, 0f)
    assertEquals(16f, resolved.widthDp - resolved.groups.last().leftDp - resolved.groups.last().widthDp, 0f)
    assertEquals(88f, resolved.groups.first().widthDp, 0f)
  }

  @Test fun `testo uscita conferma e principale hanno vetro proprio`() {
    assertGroups(state(back(), icon("a"), text("edit")), listOf("back"), listOf("a"), listOf("edit"))
    assertGroups(state(close(), icon("a"), primary("done")), listOf("close"), listOf("a"), listOf("done"))
    assertGroups(state(icon("a"), icon("confirm").copy(role = FluidToolbarRole.Confirm)), listOf("a"), listOf("confirm"))
  }

  @Test fun `gruppo esplicito separa i vicini e non mescola testo e simboli`() {
    assertGroups(state(group("tools", icon("a"), text("edit"), icon("b"))), listOf("a"), listOf("edit"), listOf("b"))
    assertGroups(state(icon("a"), group("tools", icon("b"), icon("c")), icon("d")), listOf("a"), listOf("b", "c"), listOf("d"))
  }

  @Test fun `sfondo nascosto conserva elemento selezione tinta badge e misura`() {
    val item = icon("avatar").copy(sharedBackground = false, selected = true, tintArgb = 0xffaabbccL, badge = FluidToolbarBadge("3"))
    val resolved = state(icon("a"), item, icon("b"))
    assertGroups(resolved, listOf("a"), listOf("avatar"), listOf("b"))
    assertFalse(resolved.groups[1].sharedBackground)
    assertEquals(item, resolved.element("avatar")!!.item)
  }

  @Test fun `i dati visivi sono per elemento anche dentro lo stesso vetro`() {
    val a = icon("a").copy(selected = true, tintArgb = 0xff112233L)
    val b = icon("b").copy(selected = false, tintArgb = 0xff445566L, badge = FluidToolbarBadge())
    assertEquals(listOf(a, b), state(a, b).groups.single().items)
  }

  @Test fun `spaziatore flessibile spinge i due vetri agli estremi`() {
    val flexible = FluidToolbarItem("flex", FluidToolbarRole.FlexibleSpacer)
    val resolved = state(icon("filter"), flexible, icon("search"), icon("write"), width = 360f)
    assertGroups(resolved, listOf("filter"), listOf("search", "write"))
    assertEquals(FluidToolbarSide.Leading, resolved.groups.first().side)
    assertEquals(FluidToolbarSide.Trailing, resolved.groups.last().side)
    assertEquals(16f, resolved.groups.first().leftDp, 0f)
    assertEquals(344f, resolved.groups.last().leftDp + resolved.groups.last().widthDp, 0f)
  }

  @Test fun `spaziatore fisso personalizzato conta una volta e non lascia vetri vuoti`() {
    val resolved = state(fixed("start"), icon("a"), fixed(width = 24f), icon("b"), fixed("end"))
    assertEquals(24f, resolved.groups[1].leftDp - resolved.groups[0].leftDp - 44f, 0f)
    assertEquals(2, resolved.groups.size)
    assertTrue(state(fixed()).groups.isEmpty())
  }

  @Test fun `priorita escono Low poi Automatic poi High contando il tasto Altro`() {
    val items = listOf(icon("low").copy(priority = FluidToolbarPriority.Low), icon("auto"), icon("high").copy(priority = FluidToolbarPriority.High))
    val wide = resolve(FluidToolbarModel(items), 164f)
    assertTrue(wide.overflow.isEmpty())
    val narrow = resolve(FluidToolbarModel(items), 132f)
    assertEquals(listOf("low", "auto"), narrow.overflow.map { it.id })
    assertNotNull(narrow.element("high")!!.groupId)
    assertNotNull(narrow.overflowElement)
    assertEquals(132f, narrow.requiredWidthDp, 0f)
    assertTrue(narrow.fits)
    val smallest = resolve(FluidToolbarModel(items), 76f)
    assertEquals(listOf("low", "auto", "high"), smallest.overflow.map { it.id })
    assertGroups(smallest, listOf(FluidToolbarOverflowId))
  }

  @Test fun `overflow esplicito e sempre Altro anche con larghezza abbondante`() {
    val resolved = state(icon("hidden").copy(overflow = true), icon("shown"), width = 1000f)
    assertEquals(listOf("hidden"), resolved.overflow.map { it.id })
    assertGroups(resolved, listOf("shown"), listOf(FluidToolbarOverflowId))
    assertTrue(resolved.element("hidden")!!.inOverflow)
  }

  @Test fun `a parita di priorita resta prima il comando piu esterno`() {
    val trailing = state(icon("inner"), icon("outer"), width = 132f)
    assertTrue(trailing.overflow.isEmpty())
    val withMenu = state(icon("inner"), icon("middle"), icon("outer"), width = 132f)
    assertEquals(listOf("inner", "middle"), withMenu.overflow.map { it.id })
    val leading = state(icon("outer", placement = FluidToolbarPlacement.Leading), icon("middle", placement = FluidToolbarPlacement.Leading),
      icon("inner", placement = FluidToolbarPlacement.Leading), width = 144f)
    assertEquals(listOf("middle", "inner"), leading.overflow.map { it.id })
  }

  @Test fun `massimo tre gruppi comprende Altro ma non indietro come iPhone R5 B11`() {
    // F4A.1 contava anche l'uscita: R5 B11 mostra invece indietro + tondo + gruppo + Altro.
    val resolved = state(back(), text("a"), text("b"), text("c"), primary("done"))
    assertTrue(resolved.fits)
    assertEquals(4, resolved.groups.size)
    assertEquals(listOf("a", "b"), resolved.overflow.map { it.id })
    assertGroups(resolved, listOf("back"), listOf("c"), listOf(FluidToolbarOverflowId), listOf("done"))
  }

  @Test fun `indietro e Chiudi sono fuori conto ma occupano larghezza e restano protetti`() {
    for (exit in listOf(back(), close(), close().copy(placement = FluidToolbarPlacement.Trailing))) {
      val resolved = state(exit, icon("a"), fixed("one"), icon("b"), fixed("two"), icon("c"))
      assertTrue(resolved.fits)
      assertEquals(4, resolved.groups.size)
      assertTrue(resolved.overflow.isEmpty())
      assertFalse(resolved.element(exit.id)!!.inOverflow)
      val narrow = state(exit, primary("done"), width = 120f)
      assertFalse(narrow.fits)
      assertEquals(132f, narrow.requiredWidthDp, 0f)
      assertTrue(narrow.overflow.isEmpty())
    }
    val withoutExit = state(text("a"), text("b"), text("c"), primary("done"))
    assertEquals(3, withoutExit.groups.size)
    assertEquals(listOf("a", "b"), withoutExit.overflow.map { it.id })
  }

  @Test fun `PinnedTrailing e esterno protetto e va nel menu con ricerca attiva`() {
    val pinned = icon("pin").copy(placement = FluidToolbarPlacement.PinnedTrailing, priority = FluidToolbarPriority.Low)
    val model = FluidToolbarModel(listOf(pinned, icon("ordinary").copy(priority = FluidToolbarPriority.High)))
    val resolved = resolve(model, 132f)
    assertFalse(resolved.element("pin")!!.inOverflow)
    assertEquals("pin", resolved.groups.last().items.last().id)
    val search = resolve(model, 600f, searchActive = true)
    assertEquals(listOf("pin"), search.overflow.map { it.id })
  }

  @Test fun `vincolo impossibile e dichiarato senza perdere un fissato`() {
    val resolved = state(icon("pin").copy(placement = FluidToolbarPlacement.PinnedTrailing), width = 20f)
    assertFalse(resolved.fits)
    assertTrue(resolved.overflow.isEmpty())
    assertEquals(76f, resolved.requiredWidthDp, 0f)
  }

  @Test fun `principale unica e sempre a destra anche se dichiarata Leading`() {
    val main = primary("save").copy(placement = FluidToolbarPlacement.Leading)
    assertEquals(FluidToolbarSide.Trailing, state(main).groups.single().side)
    assertThrows(IllegalArgumentException::class.java) { FluidToolbarModel(listOf(main, primary("done"))) }
    assertThrows(IllegalArgumentException::class.java) {
      state(group("main", FluidToolbarItem("a"), FluidToolbarItem("b")).copy(placement = FluidToolbarPlacement.PrimaryAction))
    }
  }

  @Test fun `centro resta centrato e la risoluzione conta la collisione asimmetrica`() {
    val resolved = state(icon("left", placement = FluidToolbarPlacement.Leading),
      icon("title", placement = FluidToolbarPlacement.Principal), width = 200f)
    assertTrue(resolved.fits)
    assertEquals(78f, resolved.element("title")!!.leftDp, 0f)
    val narrow = state(icon("left", placement = FluidToolbarPlacement.Leading),
      icon("title", placement = FluidToolbarPlacement.Principal), width = 120f)
    assertTrue(narrow.overflow.isNotEmpty())
    assertTrue(narrow.fits)
  }

  @Test fun `elementi BottomBar non vengono assorbiti dalla barra superiore`() {
    val model = FluidToolbarModel(listOf(icon("top"), icon("bottom", placement = FluidToolbarPlacement.BottomBar)))
    assertNull(resolve(model, 600f).element("bottom"))
    assertGroups(resolve(model, 600f, bar = FluidToolbarBar.Bottom), listOf("bottom"))
  }

  @Test fun `id gruppo strutturale stabile fra ricreazioni riordino tinta e larghezza`() {
    val a = icon("a")
    val b = icon("b")
    val id = state(a, b).groups.single().id
    assertEquals(id, state(a.copy(selected = true), b.copy(tintArgb = 0xff123456L), width = 360f).groups.single().id)
    assertEquals(id, state(b, a).groups.single().id)
    assertNotEquals(id, state(a).groups.single().id)
    assertNotEquals(state(icon("a|b")).groups.single().id, id)
  }

  @Test fun `snapshot copia le liste esterne ed annidate ed e strutturale`() {
    val children = mutableListOf(icon("a"))
    val source = mutableListOf(group("tools").copy(children = children))
    val model = FluidToolbarModel(source)
    children += icon("b")
    source.clear()
    assertGroups(resolve(model, 600f), listOf("a"))
    assertEquals(FluidToolbarModel(listOf(group("tools", icon("a")))), model)
  }

  @Test fun `input ambiguo o non finito viene rifiutato`() {
    assertThrows(IllegalArgumentException::class.java) { FluidToolbarModel(listOf(icon("a"), icon("a"))) }
    assertThrows(IllegalArgumentException::class.java) { FluidToolbarModel(listOf(group("a", icon("a")))) }
    assertThrows(IllegalArgumentException::class.java) { state(width = Float.NaN) }
    assertThrows(IllegalArgumentException::class.java) { icon("a").copy(widthDp = -1f) }
    assertThrows(IllegalArgumentException::class.java) { FluidToolbarMetrics(0f, 12f, 16f, 44f) }
  }

  @Test fun `R7 01 I verso I stesso simbolo resta nitido`() {
    val diff = change(listOf(icon("more")), listOf(icon("more")))
    assertEquals(FluidToolbarElementChange.Matched, diff.at("more").change)
    assertContent(diff, "more", FluidToolbarContentChange.None)
    assertFalse(diff.at("more").moved)
  }

  @Test fun `R7 02 I verso I simbolo diverso stesso vetro incrocia sfocato`() {
    val diff = change(listOf(icon("slot", "filter")), listOf(icon("slot", "more")))
    assertContent(diff, "slot", FluidToolbarContentChange.BlurReplace)
    assertFalse(diff.at("slot").moved)
    assertEquals(FluidToolbarGroupChange.Matched, diff.groups.single().change)
  }

  @Test fun `R7 03 gruppo stesso numero cambia solo il contenuto diverso`() {
    val diff = change(listOf(icon("a"), icon("b")), listOf(icon("a", "new"), icon("b")))
    assertContent(diff, "a", FluidToolbarContentChange.BlurReplace)
    assertContent(diff, "b", FluidToolbarContentChange.None)
    assertEquals(diff.groups.single().from.single().widthDp, diff.groups.single().to.single().widthDp, 0f)
  }

  @Test fun `R7 04 gruppo cresce e si restringe mantenendo nitida icona esterna`() {
    val small = listOf(icon("outer"))
    val large = listOf(icon("inner"), icon("middle"), icon("outer"))
    val grow = change(small, large)
    assertContent(grow, "outer", FluidToolbarContentChange.None)
    assertEquals(FluidToolbarElementChange.BornFromNeighbour, grow.at("inner").change)
    assertEquals("outer", grow.at("inner").neighbourId)
    assertTrue(grow.at("inner").insideGroup)
    val shrink = change(large, small)
    assertContent(shrink, "outer", FluidToolbarContentChange.None)
    assertEquals(FluidToolbarContentChange.BlurDisappear, shrink.leaving("inner").content)
  }

  @Test fun `R7 05 tondo diventa gruppo cresce verso interno senza sfocare superstite`() {
    val diff = change(listOf(icon("more")), listOf(icon("share"), icon("more")))
    assertContent(diff, "more", FluidToolbarContentChange.None)
    assertEquals(FluidToolbarGroupChange.Matched, diff.groups.single().change)
    assertEquals(44f, diff.groups.single().from.single().widthDp, 0f)
    assertEquals(88f, diff.groups.single().to.single().widthDp, 0f)
  }

  @Test fun `R7 06 gruppo diventa tondo chiudendosi sullaccoppiato`() {
    val diff = change(listOf(icon("a"), icon("b")), listOf(icon("b", "check")))
    assertContent(diff, "b", FluidToolbarContentChange.BlurReplace)
    assertEquals(FluidToolbarContentChange.BlurDisappear, diff.leaving("a").content)
    assertEquals(44f, diff.groups.single().to.single().widthDp, 0f)
  }

  @Test fun `R7 07 un tondo diventa due e il nuovo nasce dal fianco del vicino`() {
    val diff = change(listOf(icon("more")), listOf(icon("share"), fixed(), icon("more")))
    assertEquals(FluidToolbarElementChange.BornFromNeighbour, diff.at("share").change)
    assertEquals("more", diff.at("share").neighbourId)
    assertContent(diff, "more", FluidToolbarContentChange.None)
    assertTrue(diff.groups.any { it.change == FluidToolbarGroupChange.BornFromNeighbour })
  }

  @Test fun `R7 08 due tondi diventano uno assorbendo il vetro svuotato`() {
    val diff = change(listOf(icon("share"), fixed(), icon("more")), listOf(icon("more")))
    assertEquals(FluidToolbarElementChange.MergeIntoNeighbour, diff.leaving("share").change)
    assertEquals("more", diff.leaving("share").neighbourId)
    assertEquals(FluidToolbarContentChange.BlurDisappear, diff.leaving("share").content)
  }

  @Test fun `R7 09 separati verso gruppo e ritorno unisce e divide con icone nitide`() {
    val separate = listOf(icon("a"), fixed(), icon("b"))
    val united = listOf(icon("a"), icon("b"))
    val merge = change(separate, united)
    val split = change(united, separate)
    assertEquals(FluidToolbarGroupChange.Merge, merge.groups.single().change)
    assertEquals(FluidToolbarGroupChange.Split, split.groups.single().change)
    for (diff in listOf(merge, split)) for (id in listOf("a", "b")) assertContent(diff, id, FluidToolbarContentChange.None)
  }

  @Test fun `R7 10 spaziatore divide due gruppi espliciti e rimuoverlo riunisce`() {
    val whole = listOf(group("g", icon("a"), icon("b")))
    val divided = listOf(group("g", icon("a"), fixed(), icon("b")))
    assertEquals(FluidToolbarGroupChange.Split, change(whole, divided).groups.single().change)
    assertEquals(FluidToolbarGroupChange.Merge, change(divided, whole).groups.single().change)
  }

  @Test fun `R7 11 gruppo nuovo su lato gia occupato nasce dal vicino`() {
    val diff = change(listOf(text("done")), listOf(group("tools", icon("a"), icon("b")), text("done")))
    assertEquals("done", diff.at("a").neighbourId)
    assertEquals(FluidToolbarElementChange.BornFromNeighbour, diff.at("b").change)
  }

  @Test fun `R7 12 lato vuoto materializza tondo o gruppo sul posto`() {
    for (items in listOf(listOf(icon("a")), listOf(icon("a"), icon("b")))) {
      val diff = change(emptyList(), items)
      assertTrue(diff.elements.all { it.change == FluidToolbarElementChange.Materialize && it.neighbourId == null })
      assertEquals(FluidToolbarGroupChange.Materialize, diff.groups.single().change)
    }
  }

  @Test fun `R7 13 ultimo vetro dematerializza sul posto`() {
    val diff = change(listOf(icon("a"), icon("b")), emptyList())
    assertTrue(diff.elements.all { it.change == FluidToolbarElementChange.Dematerialize && it.content == FluidToolbarContentChange.BlurDisappear })
    assertEquals(FluidToolbarGroupChange.Dematerialize, diff.groups.single().change)
  }

  @Test fun `R7 14 testo verso testo scambia sfocato e cambia larghezza`() {
    val diff = change(listOf(text("edit", "Modifica", 90f)), listOf(text("edit", "Annulla", 80f)))
    assertContent(diff, "edit", FluidToolbarContentChange.BlurReplace)
    assertEquals(90f, diff.groups.single().from.single().widthDp, 0f)
    assertEquals(80f, diff.groups.single().to.single().widthDp, 0f)
  }

  @Test fun `R7 15 testo e icona condividono il vetro con cambio forma`() {
    for ((old, new) in listOf(text("slot") to icon("slot"), icon("slot") to text("slot"))) {
      val diff = change(listOf(old), listOf(new))
      assertContent(diff, "slot", FluidToolbarContentChange.BlurReplace)
      assertEquals(FluidToolbarGroupChange.Matched, diff.groups.single().change)
    }
  }

  @Test fun `R7 16 Seleziona diventa Fine ed un gruppo nato dal testo`() {
    val diff = change(listOf(text("mode", "Seleziona")), listOf(icon("circle"), icon("triangle"), text("mode", "Fine")))
    assertContent(diff, "mode", FluidToolbarContentChange.BlurReplace)
    assertEquals("mode", diff.at("circle").neighbourId)
    assertEquals(2, diff.groups.sumOf { it.to.size })
  }

  @Test fun `R7 17 azione normale diventa principale con tinta nello scambio`() {
    val diff = change(listOf(icon("edit", "pencil")), listOf(primary("edit")))
    assertContent(diff, "edit", FluidToolbarContentChange.BlurReplace)
    assertTrue(diff.at("edit").appearanceChanged)
    assertEquals(FluidToolbarGroupChange.Matched, diff.groups.single().change)
  }

  @Test fun `R7 18 principale esce dal gruppo che si divide o si chiude sul tondo`() {
    val old = listOf(icon("a"), icon("done"))
    val split = change(old, listOf(icon("a"), primary("done")))
    assertEquals(FluidToolbarGroupChange.Split, split.groups.single().change)
    assertContent(split, "a", FluidToolbarContentChange.None)
    assertContent(split, "done", FluidToolbarContentChange.BlurReplace)
    val shrink = change(old, listOf(primary("done")))
    assertEquals(FluidToolbarGroupChange.Matched, shrink.groups.single().change)
    assertEquals(44f, shrink.groups.single().to.single().widthDp, 0f)
  }

  @Test fun `R7 19 principale torna azione o testo oppure dematerializza`() {
    for (target in listOf(icon("done"), text("done"))) assertContent(change(listOf(primary("done")), listOf(target)), "done", FluidToolbarContentChange.BlurReplace)
    assertEquals(FluidToolbarElementChange.Dematerialize, change(listOf(primary("done")), emptyList()).leaving("done").change)
  }

  @Test fun `R7 20 indietro comune e fermo al pixel e nitido`() {
    val diff = change(listOf(back(), icon("a")), listOf(back(), icon("a", "b")))
    assertFalse(diff.at("back").moved)
    assertContent(diff, "back", FluidToolbarContentChange.None)
  }

  @Test fun `R7 21 radice verso figlia e ritorno indietro per lente anche con altri comandi`() {
    val root = listOf(icon("left", placement = FluidToolbarPlacement.Leading))
    val child = listOf(back(), icon("left", placement = FluidToolbarPlacement.Leading))
    assertEquals(FluidToolbarElementChange.Materialize, change(root, child).at("back").change)
    assertEquals(FluidToolbarElementChange.Dematerialize, change(child, root).leaving("back").change)
  }

  @Test fun `R7 22 chiudi verso indietro anonimi stesso ruolo uscita stesso vetro`() {
    val diff = change(listOf(close().copy(matchId = null)), listOf(back().copy(matchId = null)))
    assertEquals(FluidToolbarMatch.Role, diff.at("back").matchedBy)
    assertContent(diff, "back", FluidToolbarContentChange.BlurReplace)
    assertEquals(FluidToolbarGroupChange.Matched, diff.groups.single().change)
  }

  @Test fun `R7 23 chiudi compare e scompare per lente`() {
    assertEquals(FluidToolbarElementChange.Materialize, change(emptyList(), listOf(close())).at("close").change)
    assertEquals(FluidToolbarElementChange.Dematerialize, change(listOf(close()), emptyList()).leaving("close").change)
  }

  @Test fun `R7 24 entra nel trabocco dentro Altro e il gruppo si divide`() {
    val old = listOf(icon("favorite"), icon("share"), icon("delete"))
    val new = listOf(icon("favorite"), icon("share").copy(overflow = true), icon("delete").copy(overflow = true))
    val diff = change(old, new)
    for (id in listOf("share", "delete")) {
      assertEquals(FluidToolbarElementChange.IntoOverflow, diff.at(id).change)
      assertEquals(FluidToolbarOverflowId, diff.at(id).neighbourId)
    }
    assertEquals(FluidToolbarGroupChange.Split, diff.groups.single().change)
    assertContent(diff, "favorite", FluidToolbarContentChange.None)
  }

  @Test fun `R7 25 esce dal trabocco da Altro che diventa lultimo uscito`() {
    val hidden = listOf(icon("a").copy(overflow = true))
    val shown = listOf(icon("a"))
    val diff = change(hidden, shown)
    assertEquals(FluidToolbarElementChange.OutOfOverflow, diff.at("a").change)
    assertEquals(FluidToolbarOverflowId, diff.at("a").neighbourId)
    assertEquals(FluidToolbarGroupChange.Matched, diff.groups.single().change)
    assertEquals(FluidToolbarOverflowId, diff.groups.single().from.single().items.single().id)
    assertEquals("a", diff.groups.single().to.single().items.single().id)
  }

  @Test fun `R7 26 titolo in linea dichiara incrocio sfocato senza animare pagine`() {
    val old = resolve(FluidToolbarModel(titles = FluidToolbarTitles(inline = "Prima")), 360f)
    val new = resolve(FluidToolbarModel(titles = FluidToolbarTitles(inline = "Dopo")), 360f)
    assertEquals(FluidToolbarTitleChange.BlurReplace, diffFluidToolbar(old, new).titles.single().change)
    assertEquals(FluidToolbarTitleKind.Inline, diffFluidToolbar(old, new).titles.single().kind)
  }

  @Test fun `R7 27 titolo grande resta contenuto della pagina`() {
    val old = resolve(FluidToolbarModel(titles = FluidToolbarTitles(large = "Prima")), 360f)
    val new = resolve(FluidToolbarModel(titles = FluidToolbarTitles(large = "Dopo")), 360f)
    assertEquals(FluidToolbarTitleChange.PageContent, diffFluidToolbar(old, new).titles.single().change)
    assertTrue(diffFluidToolbar(old, new).groups.isEmpty())
  }

  @Test fun `R7 28 sottotitolo compare e scompare senza sfocare titolo invariato`() {
    val old = resolve(FluidToolbarModel(titles = FluidToolbarTitles(inline = "Note")), 360f)
    val new = resolve(FluidToolbarModel(titles = FluidToolbarTitles(inline = "Note", subtitle = "3 note")), 360f)
    assertEquals(FluidToolbarTitleKind.Subtitle, diffFluidToolbar(old, new).titles.single().kind)
    assertEquals(FluidToolbarTitleChange.BlurAppear, diffFluidToolbar(old, new).titles.single().change)
    assertEquals(FluidToolbarTitleChange.BlurDisappear, diffFluidToolbar(new, old).titles.single().change)
  }

  @Test fun `R7 29 ricerca tasto verso campo mantiene lente nitida dichiarando la misura`() {
    val button = icon("search", "search").copy(role = FluidToolbarRole.Search)
    // Questo test riguarda l'identità della lente, la traiettoria è di F4B.2.
    val field = button.copy(widthDp = 280f)
    val diff = change(listOf(button), listOf(field))
    assertContent(diff, "search", FluidToolbarContentChange.None)
    assertEquals(FluidToolbarGroupChange.Matched, diff.groups.single().change)
  }

  @Test fun `R7 30 ricerca compare e scompare dichiara lente delegando lo scroll`() {
    val search = icon("search").copy(role = FluidToolbarRole.Search)
    assertEquals(FluidToolbarElementChange.Materialize, change(emptyList(), listOf(search)).at("search").change)
    assertEquals(FluidToolbarElementChange.Dematerialize, change(listOf(search), emptyList()).leaving("search").change)
  }

  @Test fun `R7 31 menu riceve intero gruppo sorgente senza duplicare resa F5A`() {
    assertPresentation(FluidToolbarPresentationKind.Menu)
  }

  @Test fun `R7 32 foglio riceve gruppo sorgente consumato e chiusura inversa F5B`() {
    assertPresentation(FluidToolbarPresentationKind.Sheet)
  }

  private fun assertPresentation(kind: FluidToolbarPresentationKind) {
    val items = listOf(icon("a"), icon("b"))
    val old = resolve(FluidToolbarModel(items), 600f)
    val new = resolve(FluidToolbarModel(items, presentation = FluidToolbarPresentation("b", kind)), 600f)
    val opening = diffFluidToolbar(old, new)
    assertEquals(old.groups.single().id, opening.presentations.single().sourceGroupId)
    assertTrue(opening.presentations.single().opening)
    assertFalse(diffFluidToolbar(new, old).presentations.single().opening)
    assertTrue(opening.elements.all { it.content == FluidToolbarContentChange.None })
  }

  @Test fun `Alessio 1 andata e ritorno da more preservano vetro ed identita`() {
    val a = listOf(icon("more"))
    val b = listOf(icon("share"), fixed(), icon("more"))
    assertEquals("more", change(a, b).at("share").neighbourId)
    assertEquals("more", change(b, a).leaving("share").neighbourId)
    assertContent(change(a, b), "more", FluidToolbarContentChange.None)
  }

  @Test fun `Alessio 2 due vetri fermi scambiano icone simultaneamente`() {
    val old = listOf(icon("one", "folder"), fixed(), icon("two", "send"))
    val new = listOf(icon("one", "clip"), fixed(), icon("two", "check"))
    val diff = change(old, new)
    assertTrue(diff.elements.all { it.content == FluidToolbarContentChange.BlurReplace && !it.moved })
    assertTrue(diff.startsNow)
  }

  @Test fun `Alessio 3 gesto aspetta conferma annullo non produce alcuna differenza`() {
    val old = state(icon("a"))
    val new = state(icon("a", "b"))
    for (trigger in listOf(FluidToolbarTrigger.PredictiveBackPreview, FluidToolbarTrigger.PredictiveBackCancelled)) {
      val diff = diffFluidToolbar(old, new, trigger)
      assertFalse(diff.startsNow)
      assertTrue(diff.elements.isEmpty() && diff.groups.isEmpty() && diff.titles.isEmpty())
    }
    assertTrue(diffFluidToolbar(old, new, FluidToolbarTrigger.PredictiveBackCommitted).startsNow)
    assertContent(diffFluidToolbar(old, new, FluidToolbarTrigger.PredictiveBackCommitted), "a", FluidToolbarContentChange.BlurReplace)
  }

  @Test fun `Alessio 4 quarantanove incroci hanno il risultato atteso per ciascuna coppia`() {
    val names = listOf("solo", "gruppo", "testo", "principale", "indietro", "chiudi", "trabocco")
    fun variant(index: Int): List<FluidToolbarItem> = when (index) {
      0 -> listOf(icon("subject", "same"))
      1 -> listOf(icon("peer"), icon("subject", "same"))
      2 -> listOf(text("subject", "Testo"))
      3 -> listOf(primary("subject"))
      4 -> listOf(back("subject"))
      5 -> listOf(close("subject"))
      else -> listOf(icon("subject", "same").copy(overflow = true))
    }
    for (old in names.indices) for (new in names.indices) {
      val diff = change(variant(old), variant(new))
      val label = "${names[old]} -> ${names[new]}"
      val sideChanges = (old in 4..5) != (new in 4..5)
      if (sideChanges) {
        assertEquals(label, if (old == 6) FluidToolbarElementChange.InOverflow else FluidToolbarElementChange.Dematerialize,
          diff.leaving("subject").change)
        assertEquals(label, if (new == 6) FluidToolbarElementChange.InOverflow else FluidToolbarElementChange.Materialize,
          diff.at("subject").change)
      } else {
        val expected = when {
          old == 6 && new == 6 -> FluidToolbarElementChange.InOverflow
          old == 6 -> FluidToolbarElementChange.OutOfOverflow
          new == 6 -> FluidToolbarElementChange.IntoOverflow
          else -> FluidToolbarElementChange.Matched
        }
        assertEquals(label, expected, diff.at("subject").change)
        val expectedContent = when {
          old == 6 && new == 6 -> FluidToolbarContentChange.None
          old == 6 -> FluidToolbarContentChange.BlurAppear
          new == 6 -> FluidToolbarContentChange.BlurDisappear
          old == new || old in 0..1 && new in 0..1 -> FluidToolbarContentChange.None
          else -> FluidToolbarContentChange.BlurReplace
        }
        assertEquals(label, expectedContent, diff.at("subject").content)
      }
    }
  }

  @Test fun `Alessio 5 raggruppamento e il comportamento di serie`() {
    assertEquals(1, state(icon("a"), icon("b"), icon("c")).groups.size)
    assertEquals(132f, state(icon("a"), icon("b"), icon("c")).groups.single().widthDp, 0f)
  }

  @Test fun `id prima del ruolo ed id espliciti diversi impediscono euristiche`() {
    val old = listOf(icon("same", "a").copy(role = FluidToolbarRole.Search))
    val new = listOf(primary("same", "b"))
    assertEquals(FluidToolbarMatch.Id, change(old, new).at("same").matchedBy)
    val different = change(listOf(icon("old", "same")), listOf(icon("new", "same")))
    assertNull(different.at("new").matchedBy)
    assertNull(different.leaving("old").matchedBy)
  }

  @Test fun `anonimi ruolo poi simbolo unico poi ordine esterno`() {
    val role = change(listOf(primary("old").copy(matchId = null)), listOf(primary("new").copy(matchId = null)))
    assertEquals(FluidToolbarMatch.Role, role.at("new").matchedBy)
    val symbol = change(listOf(icon("old", "star").copy(matchId = null)), listOf(icon("new", "star").copy(matchId = null)))
    assertEquals(FluidToolbarMatch.Symbol, symbol.at("new").matchedBy)
    val order = change(listOf(icon("old", "a").copy(matchId = null)), listOf(text("new").copy(matchId = null)))
    assertEquals(FluidToolbarMatch.OutsideOrder, order.at("new").matchedBy)
  }

  @Test fun `simboli duplicati non inventano un accoppiamento unico`() {
    val old = listOf(icon("a", "star").copy(matchId = null), icon("b", "star").copy(matchId = null))
    val new = listOf(icon("c", "star").copy(matchId = null), icon("d", "star").copy(matchId = null))
    val diff = change(old, new)
    assertEquals(FluidToolbarMatch.OutsideOrder, diff.at("d").matchedBy)
    assertEquals("b", diff.at("d").from!!.item.id)
  }

  @Test fun `nessun accoppiamento attraversa il centro neanche per id`() {
    val diff = change(listOf(icon("a", placement = FluidToolbarPlacement.Leading)), listOf(icon("a")))
    assertEquals(FluidToolbarElementChange.Dematerialize, diff.leaving("a").change)
    assertEquals(FluidToolbarElementChange.Materialize, diff.at("a").change)
  }

  @Test fun `id che si incrociano rinascono senza viaggio attraverso il vicino`() {
    val diff = change(listOf(icon("a"), icon("b")), listOf(icon("b"), icon("a")))
    for (id in listOf("a", "b")) {
      assertEquals(FluidToolbarElementChange.Dematerialize, diff.leaving(id).change)
      assertEquals(FluidToolbarElementChange.Materialize, diff.at(id).change)
    }
  }

  @Test fun `trasferimento fra due gruppi rinasce mentre i superstiti restano`() {
    val old = listOf(group("first", icon("a"), icon("b")), group("second", icon("c"), icon("d")))
    val new = listOf(group("first", icon("a"), icon("c")), group("second", icon("b"), icon("d")))
    val diff = change(old, new)
    assertEquals(FluidToolbarGroupChange.Reconfigured, diff.groups.single().change)
    assertEquals(FluidToolbarElementChange.Matched, diff.at("a").change)
    assertEquals(FluidToolbarElementChange.Matched, diff.at("d").change)
    for (id in listOf("b", "c")) assertEquals(FluidToolbarElementChange.Materialize, diff.at(id).change)
  }

  @Test fun `bornFrom sceglie vicino sullo stesso lato e ignora un id sullaltro lato`() {
    val old = listOf(text("donor"), icon("outer"))
    val new = listOf(icon("born").copy(bornFrom = "donor"), text("donor"), icon("outer"))
    assertEquals("donor", change(old, new).at("born").neighbourId)
    val invalid = new.map { if (it.id == "born") it.copy(bornFrom = "left") else it }
    assertNotEquals("left", change(listOf(icon("left", placement = FluidToolbarPlacement.Leading)) + old, invalid).at("born").neighbourId)
  }

  @Test fun `al piu una nascita o fusione di vetri per lato gli altri usano lente`() {
    val diff = change(listOf(text("anchor")), listOf(text("a"), text("b"), text("anchor")))
    assertEquals(1, diff.groups.count { it.change == FluidToolbarGroupChange.BornFromNeighbour && it.animateShape })
    assertEquals(1, diff.elements.count { it.change == FluidToolbarElementChange.BornFromNeighbour })
    assertEquals(1, diff.elements.count { it.change == FluidToolbarElementChange.Materialize })
  }

  @Test fun `Identity e Materialize sono dichiarazioni distinte`() {
    val old = listOf(icon("a"))
    val identity = change(old, listOf(icon("a", "b").copy(transition = FluidToolbarTransition.Identity)))
    assertEquals(FluidToolbarElementChange.Identity, identity.at("a").change)
    assertContent(identity, "a", FluidToolbarContentChange.Identity)
    assertFalse(identity.groups.single().animateShape)
    val lens = change(old, listOf(icon("a", "b").copy(transition = FluidToolbarTransition.Materialize)))
    assertEquals(FluidToolbarElementChange.Materialize, lens.at("a").change)
    assertEquals(FluidToolbarElementChange.Dematerialize, lens.leaving("a").change)
  }

  @Test fun `movimento ridotto spegne forme e scambia contenuto senza sfocatura`() {
    val diff = diffFluidToolbar(state(icon("a")), state(icon("a", "b")), motion = FluidToolbarMotion.Reduced)
    assertContent(diff, "a", FluidToolbarContentChange.Crossfade)
    assertTrue(diff.groups.none { it.animateShape })
    val same = diffFluidToolbar(state(icon("a")), state(icon("a")), motion = FluidToolbarMotion.Reduced)
    assertContent(same, "a", FluidToolbarContentChange.None)
  }

  @Test fun `cambio scheda usa le stesse corrispondenze di un cambio stato`() {
    val old = state(icon("a"))
    val new = state(icon("b"), fixed(), icon("a"))
    assertEquals(diffFluidToolbar(old, new), diffFluidToolbar(old, new, FluidToolbarTrigger.TabChange))
  }

  @Test fun `badge cambia separatamente senza sfocare simbolo invariato`() {
    val diff = change(listOf(icon("a")), listOf(icon("a").copy(badge = FluidToolbarBadge("1"))))
    assertContent(diff, "a", FluidToolbarContentChange.None)
    assertTrue(diff.at("a").appearanceChanged)
  }

  @Test fun `nessun elemento viene perso o duplicato nelle risoluzioni delle larghezze`() {
    val model = FluidToolbarModel(listOf(back(), icon("low").copy(priority = FluidToolbarPriority.Low), icon("auto"),
      icon("high").copy(priority = FluidToolbarPriority.High), primary("done")))
    for (width in listOf(0f, 76f, 132f, 200f, 360f, 600f)) {
      val resolved = resolve(model, width)
      val real = resolved.elements.filter { it.item.id != FluidToolbarOverflowId }
      assertEquals(5, real.size)
      assertEquals(5, real.map { it.item.id }.distinct().size)
      assertEquals(resolved.overflow.map { it.id }.toSet(), real.filter { it.inOverflow }.map { it.item.id }.toSet())
      if (resolved.fits) assertTrue(resolved.groups.count { group -> group.items.none { it.isExit } } <= 3 && resolved.requiredWidthDp <= width)
    }
  }
}
