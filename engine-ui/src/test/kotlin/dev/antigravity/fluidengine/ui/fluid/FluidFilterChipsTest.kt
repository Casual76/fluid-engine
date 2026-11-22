package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6B.8, la fila: la regola del trabocco della fila di pillole, la selezione identica fra fila e menu, la pillola a piu'
 * valori, i recenti e lo stato vuoto. La resa vera (testo misurato a 1,0, 1,3 e 2,0, menu dal vetro, semantica e
 * bersagli) e' in `sample/src/testDebug/.../FluidFilterChipsUiTest.kt`, che ha Robolectric.
 *
 * **I numeri di Transit** (`ChipLayoutTest.kt`): le etichette «Tutti», «Urbani», «Extraurbani» misurano circa 43, 57 e
 * 100 dp a 1,3, cioe' 33, 44 e 77 a 1,0 e 66, 88 e 154 a 2,0. Una pillola e' l'etichetta piu' 2 × 14 dp di margine;
 * il tasto «Filtri · 3 ▾» e' l'etichetta (~65 dp a 1,0) piu' 5 + 15 dp di freccia e i margini. La fila sta in uno
 * schermo meno 2 × 16 dp di margine.
 */
class FluidFilterChipsTest {

  private val spacing = FluidFilterChipsDefaults.Spacing.value

  /** Le larghezze delle tre pillole di Transit e del tasto alla scala [scale]. */
  private fun transit(scale: Float): Pair<List<Float>, Float> {
    val labels = listOf(33f, 44f, 77f).map { it * scale }
    val pill = FluidChipHorizontalPadding.value * 2
    val key = 65f * scale + FluidChipContentGap.value + FluidChipGlyphSize.value + pill
    return labels.map { it + pill } to key
  }

  private fun row(scale: Float, screenDp: Float, priorities: List<FluidToolbarPriority> = List(3) { FluidToolbarPriority.Automatic }):
    FluidFilterRowLayout {
    val (widths, key) = transit(scale)
    return resolveFluidFilterRow(widths, priorities, screenDp - 32f, key, spacing)
  }

  // --- La fila che diventa menu ---------------------------------------------------------------------------------

  @Test
  fun `a 1,0 la fila resta intera, sul telefono e su uno schermo stretto`() {
    for (screen in listOf(360f, 320f)) {
      val layout = row(1f, screen)
      assertEquals("$screen dp", listOf(0, 1, 2), layout.visible)
      assertFalse("$screen dp: nessun tasto", layout.collapses)
    }
  }

  @Test
  fun `a 1,3 la fila intera ci sta a 360 dp, dove Transit leggeva Tutt e Urba, e si raccoglie a 320`() {
    // A quote uguali Transit tagliava le etichette; con le larghezze naturali la fila intera fa 300 dp in 328.
    assertFalse(row(1.3f, 360f).collapses)
    val narrow = row(1.3f, 320f)
    assertEquals(listOf(0), narrow.visible)
    assertEquals(listOf(1, 2), narrow.collapsed)
  }

  @Test
  fun `a 2,0 sul telefono resta la prima pillola e il tasto, su un tablet la fila intera`() {
    val phone = row(2f, 360f)
    assertEquals(listOf(0), phone.visible)
    assertEquals(listOf(1, 2), phone.collapsed)
    assertFalse(row(2f, 700f).collapses)
  }

  @Test
  fun `la fila raccolta sta sempre nella larghezza e non tronca nessuna pillola`() {
    for (scale in listOf(1f, 1.3f, 2f)) for (screen in listOf(320f, 360f, 411f, 700f)) {
      val (widths, key) = transit(scale)
      val available = screen - 32f
      val layout = resolveFluidFilterRow(widths, List(3) { FluidToolbarPriority.Automatic }, available, key, spacing)
      val used = layout.visible.sumOf { widths[it].toDouble() }.toFloat() + (if (layout.collapses) key else 0f) +
        spacing * (layout.visible.size + (if (layout.collapses) 1 else 0) - 1).coerceAtLeast(0)
      assertTrue("$scale a $screen dp: $used in $available", used <= available + 0.5f)
      // Ogni pillola e' o nella fila, alla sua larghezza intera, o nel menu: mai tutte e due, mai nessuna.
      assertEquals((layout.visible + layout.collapsed).sorted(), listOf(0, 1, 2))
    }
  }

  @Test
  fun `escono prima le Low, poi a parita' dall'interno verso l'esterno, e le High per ultime`() {
    val low = FluidToolbarPriority.Low
    val auto = FluidToolbarPriority.Automatic
    val high = FluidToolbarPriority.High
    // «Tutti» Low: esce lei per prima e restano le altre due, finche' ci stanno.
    val first = row(1.3f, 320f, listOf(low, auto, auto))
    assertTrue(0 in first.collapsed)
    // «Extraurbani» High a 2,0: escono Urbani e Tutti, e alla fine anche lei, perche' con il tasto non ci sta.
    val pinned = row(2f, 360f, listOf(auto, auto, high))
    assertEquals(emptyList<Int>(), pinned.visible)
    // Con piu' spazio la High resta e le altre escono.
    val roomy = row(2f, 400f, listOf(auto, auto, high))
    assertEquals(listOf(2), roomy.visible)
  }

  @Test
  fun `senza un limite di larghezza la fila resta intera, e un tasto che non ci sta resta solo`() {
    val (widths, key) = transit(2f)
    assertFalse(resolveFluidFilterRow(widths, List(3) { FluidToolbarPriority.Automatic }, Float.POSITIVE_INFINITY, key, spacing).collapses)
    val tiny = resolveFluidFilterRow(widths, List(3) { FluidToolbarPriority.Automatic }, 40f, key, spacing)
    assertEquals(emptyList<Int>(), tiny.visible)
    assertEquals(listOf(0, 1, 2), tiny.collapsed)
    assertFalse(resolveFluidFilterRow(emptyList(), emptyList(), 100f, key, spacing).collapses)
  }

  // --- Il tasto «Filtri · 2» --------------------------------------------------------------------------------------

  private val labels = FluidFilterLabels()
  private val period = FluidFilterPresets.lastDays()
  private val filters = listOf(
    FluidFilter("aperti", "Aperti ora"),
    FluidFilter("accessibile", "Accessibile"),
    FluidFilter("parcheggio", "Con parcheggio"),
    period,
  )

  @Test
  fun `il tasto dice quante pillole accese contiene, e in Single la scelta`() {
    val two = FluidFilterSelection.of("accessibile", "parcheggio")
    assertEquals("Filtri · 2", fluidFilterKeyLabel(filters, two, FluidFilterMode.Multiple, null, labels))
    assertEquals("Filtri", fluidFilterKeyLabel(filters, FluidFilterSelection.None, FluidFilterMode.Multiple, null, labels))
    // Conta solo quelle che contiene: «Aperti ora» resta nella fila e si vede da se'.
    assertEquals("Filtri · 1", fluidFilterKeyLabel(filters.drop(1), FluidFilterSelection.of("aperti", "parcheggio"),
      FluidFilterMode.Multiple, null, labels))
    val scopes = FluidFilterPresets.scopes(listOf(FluidFilterValue("tutti", "Tutti"), FluidFilterValue("urbani", "Urbani")))
    assertEquals("Urbani", fluidFilterKeyLabel(scopes.filters, FluidFilterSelection.of("urbani"), FluidFilterMode.Single, null, labels))
    assertEquals("Altro", fluidFilterKeyLabel(scopes.filters.drop(1), FluidFilterSelection.of("tutti"), FluidFilterMode.Single, null, labels))
  }

  @Test
  fun `il tasto si misura con l'etichetta piu' larga, cosi' una scelta nel menu non sposta la fila`() {
    assertEquals(listOf("Filtri · 4"), fluidFilterKeyProbeLabel(filters, FluidFilterMode.Multiple, null, labels))
    assertEquals(listOf("Periodo", "7 giorni", "30 giorni", "Altro"), fluidFilterPillProbeLabels(period))
  }

  // --- La selezione identica fra fila e menu --------------------------------------------------------------------

  @Test
  fun `una voce del menu cambia la selezione come il tocco sulla pillola, e lascia il menu aperto`() {
    var selection = FluidFilterSelection.of("aperti")
    val items = fluidFilterMenuItems(filters, { selection }, FluidFilterMode.Multiple, labels) { selection = it }
    // Le stesse pillole, nello stesso ordine, con la stessa selezione.
    assertEquals(filters.map { it.label }, items.map { it.label })
    assertEquals(listOf(true, false, false, false), items.map { it.selected })
    assertTrue(items.take(3).all { it.keepsMenuOpen })

    val fromRow = selection.toggle("parcheggio", FluidFilterMode.Multiple)
    items[2].onClick()
    assertEquals("la voce del menu = il tocco nella fila", fromRow, selection)
    // Il menu si rilegge a ogni ridisegno (FluidMenuActions): la voce mostra lo stato nuovo.
    val again = fluidFilterMenuItems(filters, { selection }, FluidFilterMode.Multiple, labels) { selection = it }
    assertEquals(listOf(true, false, true, false), again.map { it.selected })
    // Spegnere dal menu e' spegnere dalla fila.
    again[0].onClick()
    assertEquals(FluidFilterSelection.of("parcheggio"), selection)
  }

  @Test
  fun `in Single la voce del menu sceglie al posto delle altre e chiude`() {
    val scopes = FluidFilterPresets.scopes(listOf("tutti" to "Tutti", "urbani" to "Urbani", "extra" to "Extraurbani")
      .map { FluidFilterValue(it.first, it.second) })
    var selection = FluidFilterSelection.of("tutti")
    val items = fluidFilterMenuItems(scopes.filters, { selection }, scopes.mode, labels) { selection = it }
    assertTrue(items.none { it.keepsMenuOpen })
    items[2].onClick()
    assertEquals(FluidFilterSelection.of("extra"), selection)
    // Toccata quando e' gia' la scelta resta: gli ambiti ne hanno sempre una.
    assertEquals(selection, selection.toggle("extra", FluidFilterMode.Single))
  }

  @Test
  fun `la voce del menu legge la selezione del momento, non quella dell'apertura`() {
    var selection = FluidFilterSelection.None
    val items = fluidFilterMenuItems(filters, { selection }, FluidFilterMode.Multiple, labels) { selection = it }
    selection = FluidFilterSelection.of("accessibile") // cambiata da fuori, a menu aperto
    items[0].onClick()
    assertEquals(FluidFilterSelection.of("accessibile", "aperti"), selection)
  }

  // --- La pillola a piu' valori ---------------------------------------------------------------------------------

  @Test
  fun `la pillola del periodo si apre nei suoi valori, mostra quello scelto e si spegne con Qualsiasi`() {
    assertTrue(period.hasValues)
    assertEquals(listOf("7", "30", "altro"), period.values.map { it.id })
    var selection = FluidFilterSelection.None
    val items = fluidFilterValueItems(period, { selection }, FluidFilterMode.Multiple, labels) { selection = it }
    assertEquals(listOf("Qualsiasi", "7 giorni", "30 giorni", "Altro"), items.map { it.label })
    assertEquals(listOf(true, false, false, false), items.map { it.selected })
    assertTrue("una scelta fra alternative chiude il menu", items.none { it.keepsMenuOpen })
    assertEquals("Periodo", fluidFilterPillLabel(period, selection))

    items[2].onClick()
    assertEquals("30", selection.valueOf("periodo"))
    assertTrue(selection.isSelected("periodo"))
    assertEquals("30 giorni", fluidFilterPillLabel(period, selection))
    val reopened = fluidFilterValueItems(period, { selection }, FluidFilterMode.Multiple, labels) { selection = it }
    assertEquals(listOf(false, false, true, false), reopened.map { it.selected })

    reopened[0].onClick()
    assertFalse(selection.isSelected("periodo"))
    assertEquals(null, selection.valueOf("periodo"))
  }

  @Test
  fun `nel menu della fila raccolta la pillola a piu' valori e' una voce col sottomenu`() {
    var selection = FluidFilterSelection.None.choose("periodo", "7", FluidFilterMode.Multiple)
    val items = fluidFilterMenuItems(filters, { selection }, FluidFilterMode.Multiple, labels) { selection = it }
    val item = items.last()
    assertEquals("Periodo", item.label)
    assertEquals("7 giorni", item.subtitle)
    assertTrue(item.selected)
    assertEquals(listOf("Qualsiasi", "7 giorni", "30 giorni", "Altro"), item.children.map { it.label })
    item.children[3].onClick()
    assertEquals("altro", selection.valueOf("periodo"))
  }

  @Test
  fun `in Single scegliere un valore spegne le altre pillole`() {
    val sel = FluidFilterSelection.of("aperti").choose("periodo", "30", FluidFilterMode.Single)
    assertEquals(FluidFilterSelection(setOf("periodo"), mapOf("periodo" to "30")), sel)
    val multi = FluidFilterSelection.of("aperti").choose("periodo", "30", FluidFilterMode.Multiple)
    assertEquals(setOf("aperti", "periodo"), multi.selected)
  }

  @Test
  fun `senza menu il tocco sulla pillola a piu' valori passa al valore dopo, e dall'ultimo a nessuno`() {
    var selection = FluidFilterSelection.None
    val seen = ArrayList<String?>()
    repeat(4) {
      selection = selection.choose("periodo", fluidFilterNextValue(period, selection), FluidFilterMode.Multiple)
      seen += selection.valueOf("periodo")
    }
    assertEquals(listOf("7", "30", "altro", null), seen)
  }

  @Test
  fun `la selezione sopravvive alla morte del processo`() {
    val scope = object : SaverScope { override fun canBeSaved(value: Any): Boolean = true }
    val original = FluidFilterSelection.of("aperti", "accessibile").choose("periodo", "30", FluidFilterMode.Multiple)
    val saved = with(FluidFilterSelection.Saver) { scope.save(original) }!!
    assertEquals(original, FluidFilterSelection.Saver.restore(saved))
  }

  // --- Il menu dal chip come dal gruppo (correzione del 22 novembre) -----------------------------------------------

  @Test
  fun `il chip e' un'ancora di gruppo, la capsula e' il chip e il tasto premuto la goccia al centro`() {
    val chip = Rect(100f, 50f, 300f, 98f)
    val trigger = fluidChipMenuTrigger(chip)
    assertEquals(Rect(176f, 50f, 224f, 98f), trigger)
    val source = FluidChipMenuSource(FluidMenuAnchor())
    // A riposo il chip scrive solo dove sta: nessun gruppo finche' il menu non si apre.
    source.place(chip)
    assertEquals(chip, source.bounds)
    assertEquals(null, source.anchor.groupBounds)
    source.aim(chip)
    assertEquals(chip, source.anchor.groupBounds)
    assertEquals(trigger, source.anchor.restBounds)
    assertEquals(trigger, source.anchor.pressedBounds)
    // A menu in scena il chip che cambia larghezza (il conteggio del tasto) porta con se' capsula e goccia: il vetro torna
    // dove il chip e' adesso.
    val wider = Rect(100f, 50f, 320f, 98f)
    source.place(wider)
    assertEquals(wider, source.anchor.groupBounds)
    assertEquals(fluidChipMenuTrigger(wider), source.anchor.restBounds)
    // Al congedo finito l'ancora torna libera, e una posa dopo non riaccende il gruppo.
    source.anchor.consume(true)
    source.anchor.consume(false)
    assertEquals(null, source.anchor.groupBounds)
    source.place(chip)
    assertEquals(null, source.anchor.groupBounds)
    // Un chip quadrato (48 x 48) e' la sua goccia.
    val square = Rect(0f, 0f, 48f, 48f)
    assertEquals(square, fluidChipMenuTrigger(square))
  }

  // --- I suggerimenti -------------------------------------------------------------------------------------------

  @Test
  fun `mai Nessun risultato prima del primo carattere, ne' mentre i risultati arrivano`() {
    assertEquals(FluidSuggestionsShow.Nothing, fluidSuggestionsShow("", 0, loading = false))
    assertEquals(FluidSuggestionsShow.Nothing, fluidSuggestionsShow("   ", 0, loading = false))
    assertEquals(FluidSuggestionsShow.Nothing, fluidSuggestionsShow("sta", 0, loading = true))
    assertEquals(FluidSuggestionsShow.NoResults, fluidSuggestionsShow("sta", 0, loading = false))
    // Prima del primo carattere i recenti e i vicini si vedono.
    assertEquals(FluidSuggestionsShow.Rows, fluidSuggestionsShow("", 3, loading = false))
  }

  @Test
  fun `una lista sola per pertinenza, decisa dall'app, e a parita' l'ordine dell'app`() {
    val list = listOf(
      FluidFilterPresets.recent("r1", "Stazione", relevance = 0.2f),
      FluidSearchSuggestion("a", "Stadio", relevance = 0.9f),
      FluidFilterPresets.recent("r2", "Stadera", relevance = 0.2f),
      FluidFilterPresets.popular("p", "Statuto", relevance = 0.5f),
    )
    assertEquals(listOf("a", "p", "r1", "r2"), fluidSuggestionsOrdered(list).map { it.id })
  }

  @Test
  fun `i preset dei recenti e dei vicini e popolari`() {
    assertEquals(FluidSuggestionKind.Recent, FluidFilterPresets.recent("r", "Stazione").kind)
    val merged = FluidFilterPresets.nearbyAndPopular(
      nearby = listOf(FluidSearchSuggestion("n1", "Bar Roma"), FluidSearchSuggestion("n2", "Farmacia", relevance = 0.8f)),
      popular = listOf(FluidSearchSuggestion("p1", "Musei", relevance = 0.8f), FluidSearchSuggestion("p2", "Parchi")),
    )
    // Una lista sola; a parita' di pertinenza prima i vicini.
    assertEquals(listOf("n2", "p1", "n1", "p2"), merged.map { it.id })
    assertEquals(
      listOf(FluidSuggestionKind.Nearby, FluidSuggestionKind.Popular, FluidSuggestionKind.Nearby, FluidSuggestionKind.Popular),
      merged.map { it.kind },
    )
  }

  @Test
  fun `i preset delle file`() {
    val scopes = FluidFilterPresets.scopes(listOf(FluidFilterValue("tutte", "Tutte le caselle"), FluidFilterValue("questa", "Casella attuale")))
    assertEquals(FluidFilterMode.Single, scopes.mode)
    assertEquals(listOf("tutte", "questa"), scopes.filters.map { it.id })
    assertEquals(FluidFilterMode.Multiple, FluidFilterPresets.choices(filters).mode)
    val range = FluidFilterPresets.range("prezzo", "Prezzo", listOf(FluidFilterValue("1", "€"), FluidFilterValue("2", "€€")))
    assertTrue(range.hasValues)
  }

  // --- Semantica e bersagli -------------------------------------------------------------------------------------

  @Test
  fun `ogni bersaglio della fila e della lista e' di almeno 48 dp`() {
    assertEquals(48.dp, FluidChipMinTarget)
    // Una pillola di misura ha lo stesso minimo della pillola vera (FluidChipProbe e FluidChipCore lo leggono da qui).
    val (widths, key) = transit(1f)
    assertTrue(widths.all { it >= 48f } && key >= 48f)
  }

  @Test
  fun `le parole di serie sono italiane e il conteggio per TalkBack ha il numero`() {
    assertEquals("attivi: 1", labels.activeSpoken.format(1))
    assertEquals("Rimuovi dai recenti", labels.removeRecent)
    assertEquals("Nessun risultato", labels.noResults)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `una pillola senza id non esiste`() {
    FluidFilter("", "Vuota")
  }
}
