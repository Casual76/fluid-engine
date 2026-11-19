package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La DSL della barra (piano «iOS in Android», F4A.2) in logica pura: che cosa diventa nel modello di F4A.1, come la barra
 * dà un'identità ai suoi vetri fra due stati (il gruppo che cresce è lo stesso vetro) e quanto spazio lascia al titolo.
 * La resa nell'albero (pezzi del piano, semantica, bersagli) la prova `FluidToolbarBarTest` della galleria.
 */
class FluidToolbarDslTest {

  private val metrics = fluidToolbarMetrics()

  /** Una larghezza di scritta finta e dichiarata: 10 dp per carattere più i margini del tasto. */
  private val textWidth: (String) -> Float = { it.length * 10f + FluidToolbarDefaults.TextPadding.value * 2f }

  private fun model(onBack: (() -> Unit)? = null, block: FluidToolbarScope.() -> Unit): FluidToolbarModel =
    fluidToolbarModel(buildFluidToolbar(onBack, block), textWidth)

  private fun resolve(model: FluidToolbarModel, width: Float = 400f) = resolveFluidToolbar(model, width, metrics = metrics)

  /** «Sfoglia»: indietro, «Seleziona» e i tre puntini. */
  private val browse: FluidToolbarScope.() -> Unit = {
    text("select", "Seleziona", onClick = {})
    button("more", FluidIcons.MoreHoriz, "Altro", onClick = {})
  }

  /** «Seleziona»: indietro, condividi ed elimina in un gruppo, la conferma tinta da sola a destra. */
  private val select: FluidToolbarScope.() -> Unit = {
    button("share", FluidIcons.IosShare, "Condividi", onClick = {})
    button("delete", FluidIcons.Delete, "Elimina", onClick = {}, role = FluidButtonRole.Destructive)
    confirm(onClick = {})
  }

  @Test fun `entrambi i lati possono avere spaziatori fissi e flessibili annidati`() {
    val bar = model {
      group("sx", placement = FluidToolbarPlacement.Leading) {
        button("a", FluidIcons.Check, "A", onClick = {})
        spacer()
        flexibleSpacer()
      }
      group("dx", placement = FluidToolbarPlacement.Trailing) {
        spacer()
        flexibleSpacer()
        button("b", FluidIcons.Search, "B", onClick = {})
      }
    }
    val resolved = resolve(bar)
    assertEquals(FluidToolbarSide.Leading, resolved.element("a")!!.side)
    assertEquals(FluidToolbarSide.Trailing, resolved.element("b")!!.side)
    assertEquals(4, bar.items.flatMap { it.children }.filter { it.isSpacer }.map { it.id }.distinct().size)
  }

  @Test fun `il fantasma resta nella barra e il rientro lo cancella anche durante la coda`() {
    fun row(vararg ids: String) = resolve(model {
      for (id in ids) button(id, FluidIcons.Search, id, onClick = {})
    })
    val both = row("a", "b")
    val memory = FluidToolbarMemory()
    memory.update(both, FluidToolbarMotion.Full)
    memory.update(row("a"), FluidToolbarMotion.Full)
    assertEquals(both.element("b"), memory.ghosts.single().element)
    memory.update(both, FluidToolbarMotion.Full)
    assertTrue(memory.ghosts.isEmpty())
    memory.update(row("b", "a"), FluidToolbarMotion.Full)
    assertTrue("il diff separa gli incroci", memory.transfers.isEmpty())
    assertEquals(setOf("a", "b"), memory.arrivals.keys)
  }

  @Test fun `chi esce da un vetro smontato si incrocia con la scritta in arrivo`() {
    val memory = FluidToolbarMemory()
    val before = resolve(model {
      button("share", FluidIcons.IosShare, "Condividi", onClick = {})
    })
    memory.update(before, FluidToolbarMotion.Full)
    memory.update(resolve(model { text("draft", "Bozza", onClick = {}) }), FluidToolbarMotion.Full)
    assertEquals(before.element("share"), memory.ghosts.single().element)
    assertFalse(memory.ghosts.single().glassStays)
    assertEquals("Bozza", memory.arrivals.getValue("draft").item.label)
  }

  @Test fun `alla posa i trasferimenti si svuotano e una variazione di badge non ne apre altri`() {
    fun row(split: Boolean, badge: Int) = resolve(model {
      button("a", FluidIcons.Check, "A", onClick = {}, badge = badge)
      if (split) spacer()
      button("b", FluidIcons.Search, "B", onClick = {})
    })
    val memory = FluidToolbarMemory()
    memory.update(row(false, 0), FluidToolbarMotion.Full)
    memory.update(row(true, 0), FluidToolbarMotion.Full)
    assertTrue(memory.transfers.isNotEmpty())
    memory.finishTransfers(memory.transfers)
    memory.update(row(true, 2), FluidToolbarMotion.Full)
    assertTrue(memory.transfers.isEmpty())
  }

  @Test
  fun `la schermata con onBack ha l'indietro in testa, se la pagina non ne dichiara uno`() {
    val auto = buildFluidToolbar(onBack = {}, browse)
    assertEquals(FluidToolbarIds.Back, auto.entries.first().id)
    assertEquals(FluidToolbarRole.Back, auto.back?.role)
    val own = buildFluidToolbar(onBack = {}) { close(onClick = {}); browse() }
    assertNull("con un Chiudi dichiarato la barra non aggiunge l'indietro", own.back)
    assertNull("senza onBack niente indietro", buildFluidToolbar(onBack = null, browse).back)
  }

  @Test
  fun `i tasti diventano il modello di F4A1, con le chiavi pure`() {
    val red = Color(0xFFFF3B30)
    val model = model(onBack = {}) {
      text("select", "Seleziona", onClick = {})
      button("fav", FluidIcons.Check, "Preferito", onClick = {}, tint = red, selected = true, badge = 3)
      spacer()
      flexibleSpacer()
      item("avatar", label = "Account", width = 36.dp) { }
      overflow { button("hidden", FluidIcons.Search, "Cerca", onClick = {}) }
    }
    val items = model.items.associateBy { it.id }
    val select = items.getValue("select")
    assertEquals(FluidToolbarRole.Text, select.role)
    assertEquals("la scritta misurata dal chiamante", textWidth("Seleziona"), select.widthDp!!, 0f)
    val fav = items.getValue("fav")
    assertSame("il simbolo è l'icona", FluidIcons.Check, fav.symbol)
    assertEquals(fluidToolbarArgb(red), fav.tintArgb)
    assertTrue(fav.selected)
    assertEquals(FluidToolbarBadge("3"), fav.badge)
    val spacers = model.items.filter { it.isSpacer }
    assertEquals(2, spacers.size)
    assertTrue("gli spaziatori non hanno un'identità da accoppiare", spacers.all { it.matchId == null })
    assertEquals(FluidToolbarRole.FlexibleSpacer, spacers.last().role)
    val avatar = items.getValue("avatar")
    assertEquals(36f, avatar.widthDp!!, 0f)
    assertNotEquals("un elemento libero ha la sua chiave di contenuto", null, avatar.contentKey)
    assertTrue("ciò che sta in overflow è sempre nel menu", items.getValue("hidden").overflow)
    assertEquals(FluidToolbarIds.Back, model.items.first().id)
  }

  @Test
  fun `conferma e principale sono l'azione principale, tinta, sola a destra`() {
    val accent = Color(0xFF0A84FF)
    val error = Color(0xFFFF3B30)
    val declaration = buildFluidToolbar(null, select)
    val confirm = declaration.buttons.getValue(FluidToolbarIds.Confirm)
    assertSame(FluidIcons.Check, confirm.icon)
    assertEquals(accent, fluidToolbarTint(confirm, accent, error))
    assertEquals("la distruttiva prende il colore d'errore", error, fluidToolbarTint(declaration.buttons.getValue("delete"), accent, error))
    assertEquals(Color.Unspecified, fluidToolbarTint(declaration.buttons.getValue("share"), accent, error))
    val resolved = resolve(fluidToolbarModel(declaration, textWidth, { fluidToolbarTint(it, accent, error) }))
    val confirmGroup = resolved.groups.single { group -> group.items.any { it.id == FluidToolbarIds.Confirm } }
    assertEquals("la conferma ha il suo vetro", 1, confirmGroup.items.size)
    assertEquals("ed è la più esterna a destra", confirmGroup, resolved.groups.maxBy { it.leftDp })
    assertEquals(setOf("share", "delete"), resolved.groups.first { it.items.size == 2 }.items.map { it.id }.toSet())
  }

  @Test
  fun `il gruppo esplicito e la priorita' passano ai figli, il trabocco si apre col tasto Altro`() {
    val model = model {
      group("fmt", priority = FluidToolbarPriority.Low) {
        button("b", FluidIcons.Check, "Grassetto", onClick = {})
        button("i", FluidIcons.Search, "Corsivo", onClick = {})
      }
      button("share", FluidIcons.IosShare, "Condividi", onClick = {}, priority = FluidToolbarPriority.High)
    }
    // Stretta: il gruppo a priorità bassa esce per primo e finisce nel menu «Altro».
    val narrow = resolve(model, width = 150f)
    assertEquals(setOf("b", "i"), narrow.overflow.map { it.id }.toSet())
    assertNotEquals(null, narrow.overflowElement)
    assertTrue(narrow.element("share")!!.inOverflow.not())
  }

  @Test
  fun `un gruppo che guadagna un'icona e' lo stesso vetro, che si allarga`() {
    val one = resolve(model { button("more", FluidIcons.MoreHoriz, "Altro", onClick = {}) })
    val two = resolve(model {
      button("share", FluidIcons.IosShare, "Condividi", onClick = {})
      button("more", FluidIcons.MoreHoriz, "Altro", onClick = {})
    })
    val first = fluidToolbarGlassIds(one)
    val diff = diffFluidToolbar(one, two)
    val second = fluidToolbarGlassIds(two, first, one, diff)
    assertNotEquals("l'id del gruppo cambia con i membri (F4A.1)", one.groups.single().id, two.groups.single().id)
    assertEquals("il vetro no", first.getValue(one.groups.single().id), second.getValue(two.groups.single().id))
    // E torna: il gruppo che cala resta lo stesso vetro.
    val back = fluidToolbarGlassIds(one, second, two, diffFluidToolbar(two, one))
    assertEquals(first.values.single(), back.values.single())
  }

  @Test
  fun `una divisione lascia il vetro alla parte piu' grande, e i vetri nuovi non riusano un id vivo`() {
    val grouped = resolve(model {
      button("a", FluidIcons.Check, "A", onClick = {})
      button("b", FluidIcons.Search, "B", onClick = {})
      button("c", FluidIcons.IosShare, "C", onClick = {})
    })
    val split = resolve(model {
      button("a", FluidIcons.Check, "A", onClick = {})
      spacer()
      button("b", FluidIcons.Search, "B", onClick = {})
      button("c", FluidIcons.IosShare, "C", onClick = {})
    })
    val before = fluidToolbarGlassIds(grouped)
    val after = fluidToolbarGlassIds(split, before, grouped, diffFluidToolbar(grouped, split))
    val big = split.groups.single { it.items.size == 2 }
    val small = split.groups.single { it.items.size == 1 }
    assertEquals("la parte grande continua il vetro", before.values.single(), after.getValue(big.id))
    assertNotEquals(after.getValue(big.id), after.getValue(small.id))
    assertFalse("il vetro nuovo non è uno di prima", after.getValue(small.id) in before.values)
  }

  @Test
  fun `da sfoglia a seleziona e ritorno, l'indietro resta lo stesso vetro`() {
    val browsing = resolve(model(onBack = {}, browse))
    val selecting = resolve(model(onBack = {}, select))
    val ids0 = fluidToolbarGlassIds(browsing)
    val ids1 = fluidToolbarGlassIds(selecting, ids0, browsing, diffFluidToolbar(browsing, selecting))
    fun backGlass(resolved: FluidToolbarResolved, ids: Map<FluidToolbarGroupId, FluidToolbarGlassId>) =
      ids.getValue(resolved.element(FluidToolbarIds.Back)!!.groupId!!)
    assertEquals(backGlass(browsing, ids0), backGlass(selecting, ids1))
    assertEquals("ogni gruppo ha un vetro", selecting.groups.size, ids1.size)
    assertEquals("tutti diversi", ids1.size, ids1.values.toSet().size)
    val ids2 = fluidToolbarGlassIds(browsing, ids1, selecting, diffFluidToolbar(selecting, browsing))
    assertEquals(backGlass(browsing, ids0), backGlass(browsing, ids2))
  }

  @Test
  fun `il titolo agganciato si ferma ai bordi interni dei gruppi d'arrivo`() {
    val resolved = resolve(model(onBack = {}, select), width = 400f)
    val extent = fluidToolbarExtent(resolved, density = 2f)
    // L'indietro: 16 dp dal bordo, 46 di vetro.
    assertEquals(((16f + 46f) * 2f).toInt(), extent.startPx)
    val trailing = resolved.groups.filter { it.side == FluidToolbarSide.Trailing }.minOf { it.leftDp }
    assertEquals(((400f - trailing) * 2f).toInt(), extent.endPx)
  }

  @Test
  fun `il modo del titolo, il sottotitolo e la ricerca sono della dichiarazione`() {
    val spec = FluidSearchSpec(query = "", onQueryChange = {})
    val declaration = buildFluidToolbar(null) {
      title(FluidToolbarTitleMode.Inline, subtitle = "3 note")
      search(spec)
      button("p", FluidIcons.Check, "Centro", onClick = {}, placement = FluidToolbarPlacement.Principal)
    }
    assertEquals(FluidToolbarTitleMode.Inline, declaration.titleMode)
    assertEquals("3 note", declaration.subtitle)
    assertSame(spec, declaration.search)
    assertTrue("un elemento al centro prende il posto del titolo", declaration.hasPrincipal)
    val center = fluidToolbarModel(declaration, textWidth, title = "Note")
    declaration.measured(resolve(center, 400f))
    assertTrue(declaration.hasPrincipal)
    declaration.measured(resolve(center, 20f))
    assertFalse("il titolo torna quando il centro passa nel menu", declaration.hasPrincipal)
    val titles = center.titles
    assertEquals("Note", titles.inline)
    assertNull("in linea non c'è il titolo grande", titles.large)
    assertEquals("3 note", titles.subtitle)
  }

  @Test
  fun `solo la ricerca principale usa lo slot sotto il titolo`() {
    val spec = FluidSearchSpec(query = "", onQueryChange = {})
    assertFalse(fluidToolbarSearchSits(spec, FluidToolbarSearchSite.UnderTitle))
    assertTrue(fluidToolbarSearchSits(spec.copy(placement = dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement.Principal), FluidToolbarSearchSite.UnderTitle))
    assertFalse(fluidToolbarSearchSits(spec, FluidToolbarSearchSite.TopBar))
    assertFalse(fluidToolbarSearchSits(spec, FluidToolbarSearchSite.Bottom))
  }
}
