package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.richtext.FluidRichTextDocument
import dev.antigravity.fluidengine.richtext.FluidRichTextEditor
import dev.antigravity.fluidengine.richtext.FluidRichTextState
import dev.antigravity.fluidengine.richtext.FluidRichTextToolbar
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La riga che si sta scrivendo non finisce dietro la barra di formattazione.
 *
 * La barra galleggia nell'overlay sopra la tastiera, e la pagina non le lascia uno spazio suo: il campo
 * porta il cursore sopra il fondo della pagina (cioe' sopra la tastiera), ma li' sopra c'e' la barra.
 * Con la barra in scena l'editor deve portare il cursore **sopra la barra**.
 *
 * **La tastiera e' simulata**, perche' quella vera su un emulatore arriva quando vuole: la pagina e
 * l'overlay stanno in un riquadro che finisce [keyboard] sopra il fondo dello schermo (come una pagina con
 * `imePadding`), e il campo non apre la tastiera di sistema (`InterceptPlatformTextInput`). Le misure sono
 * quelle vere del layout sul dispositivo: il rettangolo del cursore dal `TextLayoutResult` del campo e il
 * bordo alto della barra.
 */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class FluidRichTextCaretAboveToolbarTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var state: FluidRichTextState
  private lateinit var scroll: ScrollState

  /** @param below quanta pagina c'e' sotto il campo: zero se il campo e' l'ultima cosa della pagina. */
  private fun mount(fontScale: Float, keyboard: Dp, below: Dp = 900.dp) {
    state = FluidRichTextState(FluidRichTextDocument.Empty)
    scroll = ScrollState(0)
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        val density = LocalDensity.current
        CompositionLocalProvider(
          LocalDensity provides Density(density.density, fontScale),
          LocalFluidMotionPolicy provides FluidMotionPolicy(0f),
        ) {
          // Nessuna sessione con la tastiera di sistema: quella vera non si apre (il testo si scrive
          // dalle azioni del campo), e Android non sposta la finestra per farle posto.
          InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
          // Il riquadro sopra la tastiera finta: la pagina che scorre e, nell'overlay, la barra.
          Box(Modifier.fillMaxSize().padding(bottom = keyboard).testTag(PageTag)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp)) {
              Text("Oggetto", Modifier.padding(vertical = 24.dp))
              FluidRichTextEditor(state, contentDescription = "Corpo", minLines = 3)
              // Di serie la pagina continua sotto il campo (la citazione, gli allegati).
              Spacer(Modifier.height(below))
            }
            FluidRichTextToolbar(
              state,
              Modifier.align(Alignment.BottomCenter).testTag(BarTag),
              avoidKeyboard = false,
            )
          }
          }
        }
      }
    }
    compose.runOnIdle { state.requestFocus() }
    compose.waitForIdle()
  }

  private fun editor() = compose.onNode(hasSetTextAction() and hasContentDescription("Corpo"))

  /** Il rettangolo del cursore (alto, basso) nelle coordinate della radice, senza ritagli. */
  private fun caret(offset: Int = state.selection.end): Pair<Float, Float> {
    val node = editor().fetchSemanticsNode()
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
    val layout = layouts.first()
    val rect = layout.getCursorRect(offset.coerceIn(0, layout.layoutInput.text.length))
    val top = node.positionInRoot.y
    return (top + rect.top) to (top + rect.bottom)
  }

  private fun barTop(): Float = compose.onNodeWithTag(BarTag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

  private fun pageBottom(): Float = compose.onNodeWithTag(PageTag, useUnmergedTree = true).fetchSemanticsNode().let {
    it.positionInRoot.y + it.size.height
  }

  private fun showBar() {
    compose.runOnIdle { state.isFormattingRequested = true }
    compose.waitForIdle()
    assertTrue("la barra deve essere in scena", state.isFormattingBarVisible)
  }

  private fun assertCaretAboveBar(what: String, offset: Int = state.selection.end) {
    compose.waitForIdle()
    val (top, bottom) = caret(offset)
    val bar = barTop()
    assertTrue("$what: il cursore (fondo $bottom) sta sotto il bordo alto della barra ($bar)", bottom <= bar + Tolerance)
    assertTrue("$what: il cursore (cima $top) e' uscito dalla pagina in alto", top >= -Tolerance)
  }

  /** Scrive righe finche' il campo non arriva ben oltre il fondo della pagina, controllando a ogni a capo. */
  private fun typeToTheBottom(check: Boolean) {
    repeat(Lines) { index ->
      editor().performTextInput("riga ${index + 1}\n")
      if (check) assertCaretAboveBar("dopo l'a capo ${index + 1}")
    }
    compose.waitForIdle()
  }

  private fun writesToTheBottom(fontScale: Float, keyboard: Dp, below: Dp = 900.dp) {
    mount(fontScale, keyboard, below)
    showBar()
    typeToTheBottom(check = true)
    // Non piu' in alto del necessario: la riga sta appena sopra la barra, non a meta' pagina.
    val (_, bottom) = caret()
    val slack = with(compose.density) { 24.dp.toPx() }
    assertTrue("la riga e' stata portata troppo in alto: fondo $bottom, barra ${barTop()}", bottom >= barTop() - slack)
    assertTrue("la pagina deve aver scorso", scroll.value > 0)
  }

  @Test fun scrivendoInFondoLaRigaRestaSopraLaBarra_testo100() = writesToTheBottom(1f, Keyboard)

  @Test fun scrivendoInFondoLaRigaRestaSopraLaBarra_testo200() = writesToTheBottom(2f, Keyboard)

  @Test fun scrivendoInFondoLaRigaRestaSopraLaBarra_senzaTastiera() = writesToTheBottom(1f, 0.dp)

  @Test fun scrivendoInFondoLaRigaRestaSopraLaBarra_senzaTastiera_testo200() = writesToTheBottom(2f, 0.dp)

  /**
   * Il campo e' l'ultima cosa della pagina (il corpo di una mail): sotto l'ultima riga non c'e' niente da
   * far salire, e la pagina non ha lasciato spazio alla barra. Il campo si tiene la coda che serve.
   */
  @Test fun ultimaRigaDiUnaPaginaSenzaSpazioInFondo_testo100() = writesToTheBottom(1f, Keyboard, below = 0.dp)

  @Test fun ultimaRigaDiUnaPaginaSenzaSpazioInFondo_testo200() = writesToTheBottom(2f, Keyboard, below = 0.dp)

  /** La coda c'e' solo con la barra: quando la barra esce, la pagina torna lunga quanto il suo testo. */
  @Test fun quandoLaBarraEsceLaCodaTornaIndietro() {
    mount(1f, Keyboard, below = 0.dp)
    typeToTheBottom(check = false)
    val withoutBar = scroll.maxValue
    showBar()
    assertCaretAboveBar("con la barra")
    assertTrue("con la barra il campo si tiene una coda sotto l'ultima riga", scroll.maxValue > withoutBar)
    compose.runOnIdle { state.isFormattingRequested = false }
    compose.waitForIdle()
    assertTrue("la barra deve essere uscita", !state.isFormattingBarVisible)
    assertTrue(
      "uscita la barra la coda torna indietro (corsa ${scroll.maxValue}, prima $withoutBar)",
      scroll.maxValue == withoutBar,
    )
  }

  /** Una riga gia' sopra la barra non si muove e il campo non cresce: la coda e' solo per le righe coperte. */
  @Test fun unaRigaSopraLaBarraNonMuoveNiente() {
    mount(1f, Keyboard, below = 0.dp)
    showBar()
    repeat(3) { editor().performTextInput("riga ${it + 1}\n") }
    compose.waitForIdle()
    assertTrue("tre righe stanno sopra la barra: la pagina non scorre", scroll.value == 0 && scroll.maxValue == 0)
    assertCaretAboveBar("tre righe")
  }

  /** La barra compare dopo, con la riga gia' in fondo alla pagina: la riga sale sopra di lei. */
  private fun barAppearing(fontScale: Float) {
    mount(fontScale, Keyboard)
    typeToTheBottom(check = false)
    showBar()
    assertCaretAboveBar("quando la barra compare")
  }

  @Test fun quandoLaBarraCompareLaRigaSaleSopra_testo100() = barAppearing(1f)

  @Test fun quandoLaBarraCompareLaRigaSaleSopra_testo200() = barAppearing(2f)

  /** Spostare il cursore: in cima (la pagina torna su), poi di nuovo in fondo. */
  private fun movingTheCaret(fontScale: Float) {
    mount(fontScale, Keyboard)
    showBar()
    typeToTheBottom(check = false)
    compose.runOnIdle { state.select(TextRange(0)) }
    assertCaretAboveBar("col cursore in cima")
    val end = state.document.text.length
    compose.runOnIdle { state.select(TextRange(end)) }
    assertCaretAboveBar("col cursore di nuovo in fondo")
    // A meta': una riga che dopo lo scorrimento sta sotto la barra o fuori vista.
    val middle = state.document.text.indexOf("riga ${Lines / 2}")
    compose.runOnIdle { state.select(TextRange(0)) }
    compose.waitForIdle()
    compose.runOnIdle { state.select(TextRange(middle)) }
    assertCaretAboveBar("col cursore a meta'")
  }

  @Test fun spostandoIlCursoreLaRigaRestaSopraLaBarra_testo100() = movingTheCaret(1f)

  @Test fun spostandoIlCursoreLaRigaRestaSopraLaBarra_testo200() = movingTheCaret(2f)

  /** Selezionare (la barra compare da sola): la fine della selezione sta sopra la barra. */
  private fun selecting(fontScale: Float) {
    mount(fontScale, Keyboard)
    typeToTheBottom(check = false)
    val end = state.document.text.length
    compose.runOnIdle { state.select(TextRange(0)) }
    compose.waitForIdle()
    compose.runOnIdle { state.select(TextRange(0, end)) }
    compose.waitForIdle()
    assertTrue("con una selezione la barra compare da sola", state.isFormattingBarVisible)
    assertCaretAboveBar("la fine della selezione", offset = end)
  }

  @Test fun selezionandoLaFineDellaSelezioneRestaSopraLaBarra_testo100() = selecting(1f)

  @Test fun selezionandoLaFineDellaSelezioneRestaSopraLaBarra_testo200() = selecting(2f)

  /** Senza barra niente cambia: la riga sta in fondo alla pagina, dove la porta il campo da solo. */
  @Test fun senzaBarraLaRigaStaInFondoAllaPagina() {
    mount(1f, Keyboard)
    typeToTheBottom(check = false)
    assertTrue("la barra non deve essere in scena", !state.isFormattingBarVisible)
    val (top, bottom) = caret()
    val page = pageBottom()
    assertTrue("senza barra la riga resta dentro la pagina (fondo $bottom, pagina $page)", bottom <= page + Tolerance)
    // Non e' stata alzata per una barra che non c'e': fra la riga e il fondo non ci sta una barra.
    val barSpace = with(compose.density) { 60.dp.toPx() }
    assertTrue("senza barra la riga non si alza (fondo $bottom, pagina $page)", bottom > page - barSpace)
    assertTrue(top >= -Tolerance)
  }

  private companion object {
    const val PageTag = "pagina"
    const val BarTag = "barra"
    const val Lines = 40
    const val Tolerance = 1.5f
    val Keyboard = 300.dp
  }
}
