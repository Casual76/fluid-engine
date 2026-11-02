package dev.antigravity.fluidengine.sample

import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainConfig
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHapticTraceOutcome
import dev.antigravity.fluidengine.ui.haptics.FluidHapticTraceRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole pure della beta, senza Android attorno: la pila della galleria, i numeri della grana che
 * i cursori muovono, e i dati di "Da provare".
 */
class GalleryBetaPureTest {

  // --- La pila ------------------------------------------------------------------------------------

  @Test
  fun `la beta parte da Materiale con Da provare sopra`() {
    val nav = GalleryNav(GalleryNav.Launch)

    assertEquals(GalleryPage.ToTry, nav.page)
    assertEquals(RouteMaterial, nav.route)
    assertTrue(nav.canGoBack)
  }

  @Test
  fun `una scheda aperta da Da provare si impila, e Indietro torna alla lista e poi alla scheda`() {
    val nav = GalleryNav(GalleryNav.Launch)

    nav.openTab(RouteControls)
    assertNull(nav.page)
    assertEquals("la barra evidenzia la scheda aperta", RouteControls, nav.route)

    assertTrue(nav.back())
    assertEquals(GalleryPage.ToTry, nav.page)
    assertEquals(RouteMaterial, nav.route)

    assertTrue(nav.back())
    assertNull(nav.page)
    assertEquals(RouteMaterial, nav.route)

    assertFalse("in fondo non c'e' piu' niente da chiudere: tocca al sistema", nav.back())
    assertFalse(nav.canGoBack)
  }

  @Test
  fun `toccare una scheda della barra riparte da lei, senza pagine sotto`() {
    val nav = GalleryNav(GalleryNav.Launch)
    nav.open(GalleryPage.RowsAndSheets)

    nav.selectTab(RouteChrome)

    assertEquals(listOf<GalleryEntry>(GalleryEntry.Tab(RouteChrome)), nav.stack)
    assertFalse(nav.canGoBack)
  }

  @Test
  fun `il secondo tocco sulla scheda gia' scelta chiude le pagine e resta sulla scheda`() {
    val nav = GalleryNav(listOf(GalleryEntry.Tab(RouteChrome)))
    nav.open(GalleryPage.Gaps)
    nav.open(GalleryPage.CapsulesAndParts)

    nav.popToRoot()

    assertEquals(listOf<GalleryEntry>(GalleryEntry.Tab(RouteChrome)), nav.stack)
  }

  @Test
  fun `una pagina gia' in cima non se ne impila una seconda, e nemmeno una scheda`() {
    val nav = GalleryNav(listOf(GalleryEntry.Tab(RouteChrome)))

    nav.open(GalleryPage.Gaps)
    nav.open(GalleryPage.Gaps)
    assertEquals(2, nav.stack.size)

    nav.selectTab(RouteControls)
    nav.openTab(RouteControls)
    assertEquals(1, nav.stack.size)
  }

  @Test
  fun `una pila senza scheda in fondo ne ha comunque una`() {
    val nav = GalleryNav(listOf(GalleryEntry.Page(GalleryPage.Gaps)))

    assertEquals(RouteMaterial, nav.route)
    assertEquals(GalleryPage.Gaps, nav.page)
  }

  // --- La taratura della grana --------------------------------------------------------------------

  private val defaults = FluidHapticGrainConfig.Default

  @Test
  fun `abbassare la partenza sotto lo spegnimento trascina con se' lo spegnimento`() {
    val tuned = GrainTuning.withOnset(defaults, 300f)

    assertEquals(300f, tuned.onsetDpPerSec, 0f)
    assertEquals(300f - GrainTuning.Hysteresis, tuned.releaseDpPerSec, 0f)
  }

  @Test
  fun `la partenza resta nel suo intervallo, e lo spegnimento non scende sotto il minimo`() {
    assertEquals(GrainTuning.OnsetRange.endInclusive, GrainTuning.withOnset(defaults, 99_999f).onsetDpPerSec, 0f)
    val low = GrainTuning.withOnset(defaults, 0f)
    assertEquals(GrainTuning.OnsetRange.start, low.onsetDpPerSec, 0f)
    assertTrue(low.releaseDpPerSec >= GrainTuning.ReleaseMin)
  }

  @Test
  fun `lo spegnimento sta sempre sotto la partenza, di almeno l'isteresi`() {
    val tuned = GrainTuning.withRelease(defaults, 5_000f)

    assertEquals(defaults.onsetDpPerSec - GrainTuning.Hysteresis, tuned.releaseDpPerSec, 0f)
    assertEquals(GrainTuning.ReleaseMin, GrainTuning.withRelease(defaults, 0f).releaseDpPerSec, 0f)
  }

  @Test
  fun `la forza e' il massimo, e il minimo non lo supera`() {
    val tuned = GrainTuning.withIntensity(defaults, 0.15f)
    assertEquals(0.15f, tuned.maxIntensity, 0f)
    assertTrue(tuned.minIntensity <= tuned.maxIntensity)

    assertEquals(1f, GrainTuning.withIntensity(defaults, 7f).maxIntensity, 0f)
    // Alzare il massimo non tocca il minimo.
    assertEquals(defaults.minIntensity, GrainTuning.withIntensity(defaults, 0.9f).minIntensity, 0f)
  }

  @Test
  fun `il passo resta nel suo intervallo`() {
    assertEquals(GrainTuning.SpacingRange.start, GrainTuning.withSpacing(defaults, 0f).spacingDp, 0f)
    assertEquals(GrainTuning.SpacingRange.endInclusive, GrainTuning.withSpacing(defaults, 500f).spacingDp, 0f)
  }

  @Test
  fun `ripristinare rimette i numeri di serie e tiene lo stile scelto`() {
    val tuned = GrainTuning.withSpacing(GrainTuning.withStyle(defaults, FluidHapticGrainStyle.Fluid), 20f)

    val reset = GrainTuning.reset(tuned)

    assertEquals(FluidHapticGrainStyle.Fluid, reset.style)
    assertEquals(defaults.copy(style = FluidHapticGrainStyle.Fluid), reset)
  }

  @Test
  fun `i numeri di serie sono dentro gli intervalli dei cursori`() {
    assertTrue(defaults.onsetDpPerSec in GrainTuning.OnsetRange)
    assertTrue(defaults.spacingDp in GrainTuning.SpacingRange)
    assertTrue(defaults.maxIntensity in GrainTuning.IntensityRange)
    assertTrue(defaults.releaseDpPerSec in GrainTuning.ReleaseMin..(defaults.onsetDpPerSec - GrainTuning.Hysteresis))
  }

  // --- Le righe del registro ----------------------------------------------------------------------

  private fun row(delta: Long?, outcome: FluidHapticTraceOutcome, label: String = "Tap", count: Int = 1) =
    FluidHapticTraceRow(label = label, detail = null, count = count, deltaMillis = delta, outcome = outcome, atMillis = 0L)

  @Test
  fun `il ritardo si scrive con i millisecondi, e la prima riga ha un trattino`() {
    assertEquals("+412 ms", hapticDeltaText(row(412, FluidHapticTraceOutcome.Played)))
    assertEquals("—", hapticDeltaText(row(null, FluidHapticTraceOutcome.Played)))
  }

  @Test
  fun `la riga piccola dice se il colpo e' fuso o scartato`() {
    assertEquals("+3 Tap (fuso)", hapticOverlayLine(row(3, FluidHapticTraceOutcome.Merged)))
    assertEquals("+3 Tap (scartato)", hapticOverlayLine(row(3, FluidHapticTraceOutcome.Dropped)))
    assertEquals("— Tap", hapticOverlayLine(row(null, FluidHapticTraceOutcome.Played)))
    assertEquals("+9 grana Fluid ×14", hapticOverlayLine(row(9, FluidHapticTraceOutcome.Played, "grana Fluid", 14)))
  }

  // --- I dati di Da provare -----------------------------------------------------------------------

  private val items = TryRounds.flatMap { it.items }

  @Test
  fun `le voci hanno un id unico, un titolo e una frase sola`() {
    assertEquals("id doppi", items.size, items.map { it.id }.toSet().size)
    items.forEach { item ->
      assertTrue("${item.id}: id non in minuscolo con trattini", item.id.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*")))
      assertTrue("${item.id}: titolo vuoto", item.title.isNotBlank())
      assertTrue("${item.id}: la frase non e' una frase", item.text.trim().endsWith("."))
      // Una frase sola: il punto fermo e' alla fine e basta (i due punti e i punti e virgola dividono
      // "cosa provare" da "cosa giudicare").
      assertEquals("${item.id}: piu' di una frase", 1, item.text.count { it == '.' })
      assertTrue("${item.id}: dove vuoto", item.target.where.isNotBlank())
    }
  }

  @Test
  fun `ogni voce porta a una scheda che esiste`() {
    val routes = setOf(RouteMaterial, RoutePlayground, RouteControls, RouteChrome, RouteSettings)
    items.map { it.target }.filterIsInstance<TryTarget.Tab>().forEach {
      assertTrue("scheda sconosciuta: ${it.route}", it.route in routes)
    }
  }

  @Test
  fun `il raggruppamento di ogni giro tiene l'ordine, e nessuna intestazione e' vuota`() {
    TryRounds.forEach { round ->
      val grouped = round.byArea()

      assertEquals("${round.id}: voci perse", round.items.size, grouped.sumOf { it.second.size })
      assertEquals(round.items.map { it.area }.distinct(), grouped.map { it.first })
      grouped.forEach { (area, inArea) ->
        assertTrue("${round.id}: area vuota", inArea.isNotEmpty())
        assertTrue(inArea.all { it.area == area })
      }
    }
  }

  @Test
  fun `la beta 1 tocca tutte le aree, e le beta dopo ne tolgono nessuna alla pagina`() {
    // Le aree sono quelle della beta 1: un giro nuovo ne usa alcune, e la pagina non ne ha mai di vuote.
    assertEquals(TryArea.entries.toSet(), TryRounds.first().items.map { it.area }.toSet())
    assertTrue(TryRounds.flatMap { it.items }.map { it.area }.toSet().containsAll(TryArea.entries.toSet()))
  }

  @Test
  fun `i giri hanno un id unico e il giro piu' nuovo e' l'ultimo`() {
    assertEquals(TryRounds.size, TryRounds.map { it.id }.toSet().size)
    assertEquals(listOf("Beta 1", "Beta 2"), TryRounds.map { it.title })
    assertEquals(TryRounds.last().items.size, latestRoundSize())
  }

  @Test
  fun `la beta 2 e' dei menu e dell'avviso, e ogni sezione della demo ha una voce`() {
    val beta2 = TryRounds.last()

    assertEquals(setOf(TryArea.Menus, TryArea.Panels), beta2.items.map { it.area }.toSet())
    val where = beta2.items.map { it.target.where }
    listOf(
      "Il tasto che si trasforma",
      "Le azioni della barra",
      "Righe con il menu contestuale",
      "L'avviso",
    ).forEach { section ->
      assertTrue("nessuna voce della beta 2 porta a «$section»", where.any { it.contains(section) })
    }
    // Il tocco che apre il menu, e l'avviso con la tastiera, hanno una voce a parte.
    assertTrue(beta2.items.any { it.id == "menu-apre-al-tocco" })
    assertTrue(beta2.items.any { it.id == "avviso-tastiera" })
  }
}
