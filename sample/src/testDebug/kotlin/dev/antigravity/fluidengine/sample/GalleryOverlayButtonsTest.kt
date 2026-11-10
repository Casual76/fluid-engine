package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidLabels
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Nella pagina «Avvisi con azione» i tasti «Indietro» e «Matrice» stanno nella riga in alto, e l'avviso della demo
 * cade proprio li': disegnati dopo la demo, i tasti ne coprivano l'intestazione. L'avviso scende sotto la riga: si
 * legge per intero e i due tasti restano raggiungibili.
 *
 * Si guardano le posizioni vere (i riquadri dei nodi), non un'immagine: Robolectric non disegna il vetro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class GalleryOverlayButtonsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var settings by mutableStateOf(EngineSettings(themeMode = ThemeMode.LIGHT))
  private val matrix = GalleryMatrix()

  private fun showPage(page: GalleryPage) {
    val nav = GalleryNav(listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(page)))
    compose.setContent {
      FluidTheme(settings = settings) {
        Gallery(settings = settings, onSettingsChange = { settings = it }, nav = nav, tools = GalleryTools(), matrix = matrix)
      }
    }
    compose.waitForIdle()
  }

  private fun backButton() = compose.onNode(hasContentDescription(FluidLabels().back), useUnmergedTree = true)

  private fun matrixButton() = compose.onNode(hasTestTag(MatrixButtonTag), useUnmergedTree = true)

  private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot

  private fun tap(node: SemanticsNodeInteraction) {
    node.performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitForIdle()
  }

  /** La scheda dell'avviso: il nodo con il titolo del pannello che contiene il testo dell'avviso. */
  private fun noticeCard(): SemanticsNodeInteraction = compose.onNode(
    SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle) and hasAnyDescendant(hasText(NoticeTitle)),
    useUnmergedTree = true,
  )

  private fun noticeShown(): Boolean =
    compose.onAllNodes(hasText(NoticeTitle), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun checkNoticeAndButtons(where: String) {
    compose.waitUntil(10_000) { noticeShown() }
    compose.waitForIdle()
    val card = bounds(noticeCard())
    val back = bounds(backButton())
    val matrixBounds = bounds(matrixButton())
    assertFalse("$where: l'avviso $card copre «Indietro» $back", card.overlaps(back))
    assertFalse("$where: l'avviso $card copre il tasto della matrice $matrixBounds", card.overlaps(matrixBounds))
    backButton().assertIsDisplayed()
    matrixButton().assertIsDisplayed()
  }

  @Test fun `l'avviso non copre ne' Indietro ne' il tasto della matrice, e si legge per intero`() {
    showPage(GalleryPage.NotificationUndo)
    compose.runOnIdle { matrix.reducedMotion = true }
    compose.waitForIdle()
    tap(compose.onNodeWithText("Modifica ordinaria"))
    checkNoticeAndButtons("testo 1,0×")
    // L'avviso e' tutto dentro lo schermo, non tagliato dall'alto.
    val card = bounds(noticeCard())
    assertTrue("l'avviso e' tagliato in cima: $card", card.top >= 0f)
  }

  @Test fun `con il testo al 200 per cento l'avviso non copre i tasti`() {
    showPage(GalleryPage.NotificationUndo)
    compose.runOnIdle {
      matrix.reducedMotion = true
      matrix.textScale = MatrixTextScale.Largest
    }
    compose.waitForIdle()
    tap(compose.onNodeWithText("Modifica ordinaria"))
    checkNoticeAndButtons("testo 2,0×")
  }

  @Test fun `con l'avviso in vista il tasto della matrice apre ancora il pannello`() {
    showPage(GalleryPage.NotificationUndo)
    compose.runOnIdle { matrix.reducedMotion = true }
    compose.waitForIdle()
    tap(compose.onNodeWithText("Modifica ordinaria"))
    compose.waitUntil(10_000) { noticeShown() }
    tap(matrixButton())
    compose.waitUntil(5_000) {
      compose.onAllNodes(hasTestTag(MatrixPanelTag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test fun `la pagina del contenuto web tiene raggiungibili Indietro e la matrice`() {
    showPage(GalleryPage.WebContent)
    matrixButton().assertIsDisplayed()
    backButton().assertIsDisplayed()
  }

  @Test fun `la pagina delle liste lunghe tiene raggiungibile la matrice`() {
    showPage(GalleryPage.LongLists)
    matrixButton().assertIsDisplayed()
  }

  private companion object {
    const val NoticeTitle = "Operazione aggiornata"
  }
}
