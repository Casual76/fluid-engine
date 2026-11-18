package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.page.FluidPageBarDiagnostics
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La barra che si ritira scorrendo **dentro un host di pagine** (giro di correzione di F4A: B1, C8, F4A-e), con le pagine
 * vere della prova «Barre fra pagine»: la barra dichiarata con la ricerca principale, lo slot `actions` di sempre con e
 * senza campo, in un host misto e in uno di sole pagine di prima, a testo normale e al 200%.
 *
 * Prova che a barra ritirata **l'indietro, i tasti e l'area che riporta in cima** escono dall'albero insieme al titolo
 * (il campo, salito sotto la barra di stato, non ha più niente sopra che gli prenda il dito), che tornano risalendo, e
 * che al 200% titolo, campo e bersagli non si sovrappongono e i bersagli restano di 48 dp. Non prova come si vede: il
 * disegno si guarda sull'emulatore e sul tablet (`riprese.md` della sessione).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidToolbarHostMinimizeTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false
  private lateinit var host: FluidPageHostState

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FluidPageBarDiagnostics.gestureFollowsFinger = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidPageBarDiagnostics.gestureFollowsFinger = false
  }

  /** Fa passare [ms] di tempo finto a fotogrammi da 16 ms, col filo principale al passo. */
  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun pages(start: ToolbarHostPage, legacyOnly: Boolean = false, fontScale: Float = 1f) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
          val state = rememberFluidPageHostState()
          host = state
          ToolbarPagesContent(
            ToolbarPagesLaunch.Request(start, 0L, follow = false, legacyOnly = legacyOnly),
            bottomInset = 0.dp,
            onBack = {},
            hostState = state,
          )
        }
      }
    }
    compose.waitForIdle()
    advance(1_200)
  }

  /**
   * Apre dalla pagina in cima la riga [tag] e lascia arrivare il push. Un host è **misto** solo se ha mostrato una barra
   * dichiarata: partire direttamente da una pagina di prima dà un host di sempre (indietro e azioni di prima).
   */
  private fun open(tag: String) {
    compose.onNodeWithTag(tag).performClick()
    Snapshot.sendApplyNotifications()
    advance(1_500)
  }

  private fun present(label: String): Boolean =
    compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()

  /**
   * Ogni tasto ha il suo nome, è un pulsante, e il dito ha almeno [target] per lato: 48 dp per i tasti della barra
   * dichiarata (F4A.2), 44 (il vetro) per quelli di sempre (`FluidBarAction`, l'indietro di un host senza barre
   * dichiarate), com'erano prima di F4A.
   */
  private fun assertButton(label: String, target: Dp = 48.dp) {
    val node = compose.onNodeWithContentDescription(label)
    node.assertWidthIsAtLeast(target).assertHeightIsAtLeast(target)
    assertEquals(label, Role.Button, node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
    assertTrue(label, node.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
  }

  private fun scrollDown() {
    compose.onNodeWithTag("toolbar-pages").performTouchInput { swipeUp(startY = bottom * 0.75f, endY = bottom * 0.3f) }
    advance(1_200)
  }

  private fun scrollUp() {
    compose.onNodeWithTag("toolbar-pages").performTouchInput { swipeDown(startY = bottom * 0.3f, endY = bottom * 0.75f) }
    advance(1_200)
  }

  @Test fun ilPopDaRicercaRitirataNonRiattivaIndietroSopraNote() {
    pages(ToolbarHostPage.Search)
    scrollDown()
    assertFalse(present("Indietro"))
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    advance(64)
    assertFalse(present("Indietro"))
    advance(1_000)
    assertTrue(present("Opzioni"))
  }

  /**
   * La pagina [start] ha la ricerca principale: scorrendo, l'indietro, [action] e l'area «Torna all'inizio» se ne vanno
   * dall'albero e il campo sale; tornando su, tutto torna.
   */
  private fun assertHostMinimize(
    start: ToolbarHostPage,
    action: String,
    prompt: String,
    legacyOnly: Boolean = false,
    actionTarget: Dp = 48.dp,
    via: List<String> = emptyList(),
  ) {
    val backTarget = if (legacyOnly) LegacyTarget else 48.dp
    pages(start, legacyOnly)
    via.forEach(::open)
    assertFalse(host.isMoving)
    assertButton("Indietro", backTarget)
    assertButton(action, actionTarget)
    assertTrue("a riposo la riga riporta in cima", present("Torna all'inizio"))
    val field = compose.onNodeWithText(prompt)
    val before = field.getBoundsInRoot().top

    scrollDown()
    assertTrue("il campo sale sotto la barra di stato", field.getBoundsInRoot().top < before)
    assertFalse("l'indietro si ritira con la barra", present("Indietro"))
    assertFalse("$action si ritira con la barra", present(action))
    assertFalse("la riga ritirata non prende più il dito sopra il campo", present("Torna all'inizio"))

    scrollUp()
    assertEquals(before, field.getBoundsInRoot().top)
    assertButton("Indietro", backTarget)
    assertButton(action, actionTarget)
    assertTrue(present("Torna all'inizio"))
  }

  @Test
  fun `barra dichiarata nell'host - indietro, tasti e area toccabile si ritirano col titolo, il campo resta`() =
    assertHostMinimize(ToolbarHostPage.Search, action = "Opzioni", prompt = "Cerca in Ricerca")

  @Test
  fun `actions di sempre in un host misto, con il campo - l'indietro si ritira con le azioni`() =
    assertHostMinimize(
      ToolbarHostPage.Folders,
      action = "Nuova (di prima)",
      prompt = "Cerca in Ricerca di prima",
      actionTarget = LegacyTarget,
      via = listOf("toolbar-pages-search"),
    )

  @Test
  fun `actions di sempre in un host di sole pagine di prima, con il campo - l'indietro si ritira con le azioni`() =
    assertHostMinimize(
      ToolbarHostPage.LegacySearch,
      action = "Nuova (di prima)",
      prompt = "Cerca in Ricerca di prima",
      legacyOnly = true,
      actionTarget = LegacyTarget,
    )

  @Test
  fun `actions di sempre senza campo, verso il fondo - l'indietro si ritira con le azioni e torna risalendo`() {
    // Host misto: «Cartelle» (dichiarata), poi «Pagina di prima» e «Lettura di prima».
    pages(ToolbarHostPage.Folders)
    open("toolbar-pages-open")
    open("toolbar-pages-open")
    assertButton("Indietro")
    assertButton("Condividi (di prima)", LegacyTarget)
    scrollDown()
    assertFalse(present("Indietro"))
    assertFalse(present("Condividi (di prima)"))
    assertFalse(present("Torna all'inizio"))
    scrollUp()
    assertButton("Indietro")
    assertButton("Condividi (di prima)", LegacyTarget)
  }

  @Test
  fun `due cambi di scheda di fila arrivano a riposo sulla scheda giusta`() {
    pages(ToolbarHostPage.Notes)
    compose.onNodeWithTag("toolbar-pages-tab").performClick()
    Snapshot.sendApplyNotifications()
    advance(48)
    assertTrue("il passo è partito", host.isMoving)
    // Durante il passo le due schede hanno la riga: quella che arriva sta più a destra (arriva dal lato di fine).
    val rows = compose.onAllNodesWithTag("toolbar-pages-tab")
    val arriving = (0 until rows.fetchSemanticsNodes().size).maxBy { rows[it].fetchSemanticsNode().boundsInRoot.left }
    rows[arriving].performClick()
    Snapshot.sendApplyNotifications()
    advance(1_200)
    assertFalse("nessun passo resta ad aspettare un posizionamento", host.isMoving)
    assertTrue("di nuovo «Note»: i suoi tre puntini", present("Opzioni"))
    assertFalse(present("Nuova cartella"))
  }

  // --- Il testo al 200% (F4A-e) ----------------------------------------------------------------------------------------

  /** Il titolo grande di [title], in px: fra le copie col suo testo (quella agganciata nella barra è più bassa) la più alta. */
  private fun largeTitle(title: String): Rect =
    compose.onAllNodesWithText(title).fetchSemanticsNodes().maxBy { it.boundsInRoot.height }.boundsInRoot

  private fun boundsOf(text: String): Rect = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

  /** La prima riga di testo della pagina in cima sotto il campo [field]: la voce della pagina. */
  private fun boundsOfVoiceUnder(field: Rect): Rect =
    ToolbarHostPage.entries.mapNotNull { page ->
      compose.onAllNodesWithText(page.voice).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
    }.first { it.top >= field.top }

  private fun buttonBounds(label: String): Rect = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot

  /** Al 200%: il titolo grande sta sotto i tasti, il campo sotto il titolo, la prima riga sotto il campo; bersagli da 48 dp. */
  private fun assertLargeText(
    start: ToolbarHostPage,
    action: String,
    prompt: String,
    title: String,
    actionTarget: Dp = 48.dp,
    via: List<String> = emptyList(),
  ) {
    pages(start, fontScale = 2f)
    via.forEach(::open)
    assertButton("Indietro")
    assertButton(action, actionTarget)
    val back = buttonBounds("Indietro")
    val heading = largeTitle(title)
    val field = boundsOf(prompt)
    val voice = compose.onNodeWithText(prompt).fetchSemanticsNode().let { boundsOfVoiceUnder(it.boundsInRoot) }
    assertTrue("titolo sotto la riga dei tasti: $heading / $back", heading.top >= back.bottom - 1f)
    assertTrue("campo sotto il titolo: $field / $heading", field.top >= heading.bottom - 1f)
    assertTrue("prima riga sotto il campo: $voice / $field", voice.top >= field.bottom - 1f)

    scrollDown()
    assertFalse(present("Indietro"))
    assertFalse(present(action))
    assertFalse(present("Torna all'inizio"))
    val pinned = boundsOf(prompt)
    assertTrue("il campo resta tutto sullo schermo: $pinned", pinned.top >= 0f)
    assertTrue("il campo è salito: $pinned / $field", pinned.top < field.top)

    scrollUp()
    assertButton("Indietro")
    assertButton(action, actionTarget)
    assertEquals(field, boundsOf(prompt))
  }

  @Test
  fun `al 200 percento con la barra dichiarata titolo, campo e bersagli non si sovrappongono, a riposo e ritirata`() =
    assertLargeText(ToolbarHostPage.Search, action = "Opzioni", prompt = "Cerca in Ricerca", title = "Ricerca")

  @Test
  fun `al 200 percento con le actions di sempre titolo, campo e bersagli non si sovrappongono, a riposo e ritirata`() =
    assertLargeText(
      ToolbarHostPage.Folders,
      action = "Nuova (di prima)",
      prompt = "Cerca in Ricerca di prima",
      title = "Ricerca di prima",
      actionTarget = LegacyTarget,
      via = listOf("toolbar-pages-search"),
    )

  @Test
  fun `al 200 percento i tasti di una barra dichiarata senza campo restano di 48 dp e il titolo grande non li copre`() {
    pages(ToolbarHostPage.Note, fontScale = 2f)
    listOf("Indietro", "Condividi", "Opzioni").forEach(::assertButton)
    val share = buttonBounds("Condividi")
    val title = largeTitle("Nota")
    assertTrue("il titolo grande sta sotto la riga dei tasti: $title / $share", title.top >= share.bottom - 1f)
  }
}

/** Il bersaglio dei tasti di sempre (`FluidBarAction`, `FluidBackButton`): il vetro, 44 dp, com'era prima di F4A. */
private val LegacyTarget: Dp = 44.dp
