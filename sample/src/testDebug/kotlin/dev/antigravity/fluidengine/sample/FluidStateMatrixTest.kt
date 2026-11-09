package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.richtext.LocalFluidRichTextLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La matrice degli stati e le dieci pagine dei componenti nuovi, dentro la galleria vera (F1.6).
 *
 * Tre cose da provare, e un test non basta a nessuna:
 *
 *  1. **ogni pagina si apre** — dal catalogo di Chrome con un tocco, e con la sua voce di «Da provare» — e **regge ogni
 *     combinazione**: tema (chiaro, scuro, nero) per scala del testo (1,0, 1,3, 2,0) per movimento (normale, ridotto),
 *     e poi la lingua, senza un'eccezione. Il pixel qui non si prova (Robolectric non disegna il vetro): si prova
 *     che la pagina resti in piedi, e il giro su emulatore (`tools/qa/gallery-tour.sh`) guarda il resto;
 *  2. **la matrice arriva davvero alle demo**: una demo con interruttori suoi li ha accesi da lei, una riga di testo
 *     al 2,0× e' piu' alta di una al 1,0×, l'"Indietro" cambia lingua, e uno stato dei dati cambia cosa mostra la
 *     lista. Una matrice che si sceglie e non si vede e' peggio di nessuna matrice;
 *  3. **il pannello e' la sola strada**: lo si apre dal tasto, si sceglie con le dita, si ripristina.
 *
 * Lo schermo e' alto (800 dp) come in `FluidBetaGalleryTest`: la barra flottante in basso ha bisogno di posto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class FluidStateMatrixTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: GalleryNav
  private val matrix = GalleryMatrix()
  private val tools = GalleryTools()

  /** Le impostazioni della galleria, come le tiene l'attivita': il tema della matrice passa di qui. */
  private var settings by mutableStateOf(EngineSettings(themeMode = ThemeMode.LIGHT))

  private fun show(initial: List<GalleryEntry>) {
    nav = GalleryNav(initial)
    compose.setContent {
      FluidTheme(settings = settings) {
        Gallery(
          settings = settings,
          onSettingsChange = { settings = it },
          nav = nav,
          tools = tools,
          matrix = matrix,
        )
      }
    }
    compose.waitForIdle()
  }

  private fun showPage(page: GalleryPage) =
    show(listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(page)))

  private fun pageShown(): Boolean =
    compose.onAllNodes(hasTestTag(NewComponentPageTag)).fetchSemanticsNodes().isNotEmpty()

  private fun shown(text: String): Boolean =
    compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

  /**
   * Il tasto della matrice sta nella barra delle pagine a sezioni, e la riga della barra fonde i suoi figli in un
   * nodo solo (il tocco sul titolo che riporta in cima): li' il tag esiste solo nell'albero non fuso.
   */
  private fun tagShown(tag: String): Boolean =
    compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  /** Il tasto, per il nome che dice a TalkBack (comincia sempre cosi'): e' quello che una persona cerca. */
  private fun matrixButton(): SemanticsNodeInteraction =
    compose.onNode(hasContentDescription("Matrice degli stati", substring = true), useUnmergedTree = true)

  /** Un tocco fermo con un dito vero: i tasti di vetro prendono il tocco dal dito, non da un click di semantica. */
  private fun tap(node: SemanticsNodeInteraction) {
    node.performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitForIdle()
  }

  /** Un tocco sulla frazione [x] della larghezza di un segmentato: i suoi segmenti sono uguali. */
  private fun tapSegment(tag: String, x: Float) {
    compose.onNodeWithTag(tag).performTouchInput { click(Offset(width * x, height / 2f)) }
    compose.waitForIdle()
  }

  private fun setMatrix(theme: MatrixTheme, scale: MatrixTextScale, reduced: Boolean) {
    compose.runOnIdle {
      settings = theme.applyTo(settings)
      matrix.textScale = scale
      matrix.reducedMotion = reduced
    }
    compose.waitForIdle()
  }

  // --- 1. Ogni pagina regge ogni combinazione ----------------------------------------------------------

  /**
   * La pagina aperta, poi le diciotto combinazioni (tema × scala × movimento) una dopo l'altra sullo stesso albero —
   * una ricomposizione per combinazione, che e' quello che fa una persona che gira i cursori — e infine l'inglese.
   */
  private fun sweep(page: GalleryPage) {
    showPage(page)
    assertTrue("${page.name}: la pagina non si vede", pageShown())
    assertTrue("${page.name}: manca il tasto della matrice", tagShown(MatrixButtonTag))

    for (theme in MatrixTheme.entries) {
      for (scale in MatrixTextScale.entries) {
        for (reduced in listOf(false, true)) {
          setMatrix(theme, scale, reduced)
          val where = "${page.name} · ${theme.label} · ${scale.label} · ridotto=$reduced"
          assertTrue("$where: la pagina non si vede piu'", pageShown())
          assertTrue("$where: il tasto della matrice e' sparito", tagShown(MatrixButtonTag))
        }
      }
    }

    // L'ultima combinazione e' scuro e ridotto al 2,0×: ora l'inglese, e poi il ritorno all'italiano.
    compose.runOnIdle { matrix.language = MatrixLanguage.English }
    compose.waitForIdle()
    assertTrue("${page.name} in inglese: la pagina non si vede", pageShown())
    compose.runOnIdle { matrix.language = MatrixLanguage.Italian }
    compose.waitForIdle()
    assertTrue("${page.name} di nuovo in italiano: la pagina non si vede", pageShown())
  }

  @Test fun `Liste lunghe e righe regge ogni combinazione`() = sweep(GalleryPage.LongLists)

  @Test fun `Identita e selezione regge ogni combinazione`() = sweep(GalleryPage.Identity)

  @Test fun `Pannello laterale e schede regge ogni combinazione`() = sweep(GalleryPage.DrawerAndTabs)

  @Test fun `Avvisi con azione regge ogni combinazione`() = sweep(GalleryPage.NotificationUndo)

  @Test fun `Campo a gettoni regge ogni combinazione`() = sweep(GalleryPage.TokenField)

  @Test fun `Data e ora regge ogni combinazione`() = sweep(GalleryPage.DateTime)

  @Test fun `Testo ricco regge ogni combinazione`() = sweep(GalleryPage.RichText)

  @Test fun `Contenuto web regge ogni combinazione`() = sweep(GalleryPage.WebContent)

  @Test fun `Grafici regge ogni combinazione`() = sweep(GalleryPage.Charts)

  @Test fun `Piastrelle di file regge ogni combinazione`() = sweep(GalleryPage.FileTile)

  // --- Ogni pagina si raggiunge -------------------------------------------------------------------------

  @Test
  fun `ogni pagina nuova si apre dal catalogo di Chrome con un tocco, e Indietro torna a Chrome`() {
    show(listOf(GalleryEntry.Tab(RouteChrome)))

    GalleryPage.newComponents.forEach { page ->
      compose.runOnIdle { nav.reset(listOf(GalleryEntry.Tab(RouteChrome))) }
      compose.waitForIdle()
      val tag = "chrome-open-${page.name}"
      compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasTestTag(tag))
      compose.waitForIdle()
      compose.onNodeWithTag(tag).performClick()
      compose.waitForIdle()

      assertEquals("${page.name}: la pagina", page, nav.page)
      assertTrue("${page.name}: non si vede", pageShown())

      compose.runOnIdle { assertTrue(nav.back()) }
      compose.waitForIdle()
      assertNull("${page.name}: dopo Indietro", nav.page)
      assertEquals(RouteChrome, nav.route)
    }
  }

  @Test
  fun `le pagine di prima non hanno la matrice, ne' il suo tasto`() {
    show(listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(GalleryPage.RowsAndSheets)))

    assertFalse(tagShown(MatrixButtonTag))
    assertFalse(pageShown())
  }

  // --- 2. La matrice arriva davvero alle demo ----------------------------------------------------------

  @Test
  fun `le liste lunghe partono dalla matrice, e gli interruttori della pagina restano liberi`() {
    showPage(GalleryPage.LongLists)
    compose.onNode(hasText("Tema scuro")).assertToggled(false)

    setMatrix(MatrixTheme.Dark, MatrixTextScale.Largest, reduced = true)

    // Gli interruttori propri della demo sono accesi da lei, senza che nessuno li tocchi.
    compose.onNode(hasText("Tema scuro")).assertToggled(true)
    compose.onNode(hasText("Testo al 200%")).assertToggled(true)
    compose.onNode(hasText("Movimento ridotto")).assertToggled(true)

    // E restano suoi: un tocco sulla riga la spegne, la matrice non la riaccende da sola.
    compose.onNode(hasText("Tema scuro")).performClick()
    compose.waitForIdle()
    compose.onNode(hasText("Tema scuro")).assertToggled(false)
    assertEquals(MatrixTextScale.Largest, matrix.textScale)
  }

  private fun SemanticsNodeInteraction.assertToggled(on: Boolean): SemanticsNodeInteraction =
    if (on) assertIsOn() else assertIsOff()

  @Test
  fun `una riga di testo occupa piu' spazio al 2,0x che al 1,0x, dentro la pagina vera`() {
    showPage(GalleryPage.LongLists)
    setMatrix(MatrixTheme.Light, MatrixTextScale.Normal, reduced = false)
    val normal = titleTextArea()

    setMatrix(MatrixTheme.Light, MatrixTextScale.Medium, reduced = false)
    val medium = titleTextArea()

    setMatrix(MatrixTheme.Light, MatrixTextScale.Largest, reduced = false)
    val largest = titleTextArea()

    assertTrue("1,3× ($medium) non e' piu' grande di 1,0× ($normal)", medium > normal)
    assertTrue("2,0× ($largest) non e' piu' grande di 1,3× ($medium)", largest > medium)
  }

  /** L'area della piu' piccola fra le scritte «Tema scuro» (quella del testo, non della riga che lo contiene). */
  private fun titleTextArea(): Float = compose.onAllNodesWithText("Tema scuro", useUnmergedTree = true)
    .fetchSemanticsNodes()
    .minOf { it.boundsInRoot.width * it.boundsInRoot.height }

  private fun SemanticsNodeInteraction.getBoundsHeight(): Float {
    val bounds = fetchSemanticsNode().boundsInRoot
    return bounds.height
  }

  @Test
  fun `l'Indietro di una pagina a sezioni cambia lingua con la matrice`() {
    showPage(GalleryPage.Identity)
    compose.onNodeWithContentDescription("Indietro").assertExists()

    compose.runOnIdle { matrix.language = MatrixLanguage.English }
    compose.waitForIdle()

    compose.onNodeWithContentDescription("Back").assertExists()
    assertTrue(compose.onAllNodes(hasContentDescription("Indietro")).fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun `l'Indietro delle pagine con la barra sopra la demo cambia lingua anch'esso`() {
    showPage(GalleryPage.NotificationUndo)
    compose.onNodeWithContentDescription("Indietro").assertExists()

    compose.runOnIdle { matrix.language = MatrixLanguage.English }
    compose.waitForIdle()

    compose.onNodeWithContentDescription("Back").assertExists()
  }

  /** Una riga dell'elenco: la demo le marca `demo-row-<id>`. */
  private val aRow = SemanticsMatcher("una riga dell'elenco") { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("demo-row-") == true
  }

  private fun rowsShown(): Boolean = compose.onAllNodes(aRow).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun `lo stato dei dati della matrice cambia cosa mostra l'elenco`() {
    showPage(GalleryPage.LongLists)
    // Con dati: la prima riga (la piu' nuova, id 11 su dodici) si raggiunge scorrendo.
    compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasTestTag("demo-row-11"))
    compose.waitForIdle()
    assertTrue("con dati manca la prima riga", rowsShown())
    assertFalse(tagShown("demo-empty"))

    compose.runOnIdle { matrix.data = MatrixData.Empty }
    compose.waitForIdle()
    assertTrue("vuoto: manca lo stato vuoto", tagShown("demo-empty"))
    assertFalse("vuoto: restano delle righe", rowsShown())

    compose.runOnIdle { matrix.data = MatrixData.Error }
    compose.waitForIdle()
    assertTrue("errore: manca il messaggio", shown("Non è stato possibile caricare"))
    assertFalse("errore: restano delle righe", rowsShown())

    compose.runOnIdle { matrix.data = MatrixData.Loading }
    compose.waitForIdle()
    assertFalse("caricamento: restano lo stato vuoto e l'errore", tagShown("demo-empty") || shown("Non è stato possibile"))
    assertFalse("caricamento: restano delle righe", rowsShown())

    compose.runOnIdle { matrix.data = MatrixData.Content }
    compose.waitForIdle()
    compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasTestTag("demo-row-11"))
    compose.waitForIdle()
    assertTrue("di nuovo con dati manca la prima riga", rowsShown())
  }

  @Test
  fun `la matrice resta fra una pagina e l'altra`() {
    showPage(GalleryPage.Identity)
    setMatrix(MatrixTheme.Black, MatrixTextScale.Largest, reduced = true)

    compose.runOnIdle { nav.reset(listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(GalleryPage.TokenField))) }
    compose.waitForIdle()

    assertTrue(pageShown())
    assertEquals(MatrixTextScale.Largest, matrix.textScale)
    assertTrue(matrix.reducedMotion)
    assertEquals(MatrixTheme.Black, MatrixTheme.of(settings, systemDark = false))
  }

  // --- Cosa fa MatrixScope, senza una pagina intorno ---------------------------------------------------

  /** Cosa vede un figlio di [MatrixScope]. */
  private class Seen {
    var fontScale = 0f
    var reduced = false
    var back = ""
    var richTextBold = ""
    var snapshot: MatrixSnapshot? = null
  }

  @Composable
  private fun Probe(seen: Seen) {
    seen.fontScale = LocalDensity.current.fontScale
    seen.reduced = LocalFluidMotionPolicy.current.reducedMotion
    seen.back = LocalFluidLabels.current.back
    seen.richTextBold = LocalFluidRichTextLabels.current.bold
    seen.snapshot = LocalGalleryMatrix.current
    Box(Modifier.testTag("probe")) { Text("probe") }
  }

  @Test
  fun `la matrice attiva cambia densita, movimento e lingua di quel che sta dentro`() {
    val seen = Seen()
    val snapshot = MatrixSnapshot(
      theme = MatrixTheme.Dark,
      textScale = MatrixTextScale.Largest,
      reducedMotion = true,
      language = MatrixLanguage.English,
    )

    compose.setContent {
      FluidTheme(settings = EngineSettings(themeMode = ThemeMode.DARK)) {
        MatrixScope(snapshot, active = true) { Probe(seen) }
      }
    }
    compose.waitForIdle()

    assertEquals(2f, seen.fontScale, 0f)
    assertTrue(seen.reduced)
    assertEquals("Back", seen.back)
    assertEquals(snapshot, seen.snapshot)
    // Le etichette dell'editor passano in inglese con quelle dell'engine.
    assertTrue("etichette dell'editor ancora in italiano: ${seen.richTextBold}", seen.richTextBold != "Grassetto")
  }

  @Test
  fun `la matrice non attiva non tocca niente`() {
    val seen = Seen()
    var outside = 0f
    compose.setContent {
      FluidTheme(settings = EngineSettings(themeMode = ThemeMode.LIGHT)) {
        outside = LocalDensity.current.fontScale
        MatrixScope(
          MatrixSnapshot(textScale = MatrixTextScale.Largest, reducedMotion = true, language = MatrixLanguage.English),
          active = false,
        ) { Probe(seen) }
      }
    }
    compose.waitForIdle()

    assertEquals("la scala del testo non e' quella di fuori", outside, seen.fontScale, 0f)
    assertFalse(seen.reduced)
    assertEquals("Indietro", seen.back)
    assertNull("fuori dalla matrice le demo non la vedono", seen.snapshot)
  }

  @Test
  fun `senza matrice una demo con interruttori propri parte da spenta`() {
    // Il caso delle anteprime e dei test di un pezzo: LocalGalleryMatrix e' nulla, e il tema e' quello di sempre.
    var dark = true
    var settingsSeen: EngineSettings? = null
    compose.setContent {
      FluidTheme(settings = EngineSettings(themeMode = ThemeMode.LIGHT)) {
        dark = rememberMatrixFlag { it.theme.isDark }.value
        settingsSeen = matrixDemoSettings(dark = false)
      }
    }
    compose.waitForIdle()

    assertFalse(dark)
    assertEquals(EngineSettings(themeMode = ThemeMode.LIGHT), settingsSeen)
  }

  // --- 3. Il pannello -----------------------------------------------------------------------------------

  @Test
  fun `il tasto della matrice apre il pannello su ogni pagina, e il pannello mostra le sue scelte`() {
    showPage(GalleryPage.NotificationUndo)

    GalleryPage.newComponents.forEach { page ->
      compose.runOnIdle { nav.reset(listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(page))) }
      compose.waitForIdle()
      assertTrue("${page.name}: la pagina non si vede", pageShown())

      tap(matrixButton())
      compose.mainClock.advanceTimeBy(900)
      compose.waitForIdle()

      assertTrue("${page.name}: il pannello non si e' aperto", tagShown(MatrixPanelTag))
      listOf("matrix-theme", "matrix-text", "matrix-motion", "matrix-language").forEach {
        assertTrue("${page.name}: manca $it", tagShown(it))
      }
      // I dati compaiono solo dove la demo li ha: le liste.
      assertEquals(
        "${page.name}: i dati nel pannello",
        page == GalleryPage.LongLists,
        tagShown("matrix-data"),
      )
    }
  }

  @Test
  fun `il pannello cambia tema, testo, movimento, lingua e dati, e Ripristina rimette tutto`() {
    showPage(GalleryPage.LongLists)
    tap(matrixButton())
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    assertTrue(tagShown(MatrixPanelTag))

    // Tre segmenti: il terzo sta nell'ultimo terzo. Due: il secondo nella meta' destra.
    tapSegment("matrix-theme", 0.83f)
    assertEquals(MatrixTheme.Black, MatrixTheme.of(settings, systemDark = false))
    tapSegment("matrix-text", 0.83f)
    assertEquals(MatrixTextScale.Largest, matrix.textScale)
    tapSegment("matrix-motion", 0.75f)
    assertTrue(matrix.reducedMotion)
    tapSegment("matrix-language", 0.75f)
    assertEquals(MatrixLanguage.English, matrix.language)
    compose.onNode(hasText("Vuoto") and hasAnyAncestor(hasTestTag("matrix-data"))).performClick()
    compose.waitForIdle()
    assertEquals(MatrixData.Empty, matrix.data)

    // La pagina ha seguito: la lista e' vuota e il tasto della matrice dice che qualcosa non e' di serie.
    compose.onNodeWithTag("matrix-reset").performClick()
    compose.waitForIdle()

    assertTrue("la matrice non e' tornata di serie", matrix.snapshot(settings, systemDark = false).isDefault)
    assertEquals(ThemeMode.SYSTEM, settings.themeMode)
    assertFalse(settings.amoledEnabled)
  }

  @Test
  fun `il pannello resta leggibile al 2,0x, le sue scelte non crescono con la matrice`() {
    showPage(GalleryPage.LongLists)
    tap(matrixButton())
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    val before = compose.onNodeWithTag("matrix-theme").getBoundsHeight()

    setMatrix(MatrixTheme.Light, MatrixTextScale.Largest, reduced = false)
    val after = compose.onNodeWithTag("matrix-theme").getBoundsHeight()

    assertEquals("il segmentato del pannello e' cresciuto col testo della demo", before, after, 0.5f)
  }
}
