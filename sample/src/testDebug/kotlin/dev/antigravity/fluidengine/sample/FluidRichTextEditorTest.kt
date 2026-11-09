package dev.antigravity.fluidengine.sample

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.richtext.FluidRichTextChangeSource
import dev.antigravity.fluidengine.richtext.FluidRichTextDocument
import dev.antigravity.fluidengine.richtext.FluidRichTextEditor
import dev.antigravity.fluidengine.richtext.FluidRichTextInlineStyle
import dev.antigravity.fluidengine.richtext.FluidRichTextListKind
import dev.antigravity.fluidengine.richtext.FluidRichTextParagraphStyle
import dev.antigravity.fluidengine.richtext.FluidRichTextState
import dev.antigravity.fluidengine.richtext.FluidRichTextToolbar
import dev.antigravity.fluidengine.richtext.buildFluidRichText
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * L'editor con formattazione con una tastiera finta: inserimento e composizione dall'`InputConnection`
 * (come Gboard), a capo negli elenchi, cancella, selezione, la barra che riflette gli stili, incolla e
 * copia con gli appunti, semantica per TalkBack, testo al 200% e da destra a sinistra.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidRichTextEditorTest {
  @get:Rule val compose = createComposeRule()

  private lateinit var host: View
  private lateinit var state: FluidRichTextState

  private val bullet = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)

  private fun setEditor(
    initial: FluidRichTextDocument = FluidRichTextDocument.Empty,
    scale: Float = 1f,
    rtl: Boolean = false,
  ) {
    state = FluidRichTextState(initial)
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.DARK) }) {
        host = LocalView.current
        val density = LocalDensity.current
        CompositionLocalProvider(
          LocalDensity provides Density(density.density, scale),
          LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
          LocalFluidMotionPolicy provides FluidMotionPolicy(0f),
        ) {
          Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
              FluidRichTextEditor(state = state, contentDescription = "Corpo", placeholder = "Scrivi")
            }
            FluidRichTextToolbar(state = state, modifier = Modifier.align(Alignment.BottomCenter))
          }
        }
      }
    }
  }

  private fun editor() = compose.onNode(hasSetTextAction() and hasContentDescription("Corpo"))

  /** Fuoco all'editor e la connessione che una tastiera userebbe. */
  private fun connect(): InputConnection {
    editor().performClick()
    lateinit var connection: InputConnection
    compose.runOnIdle { connection = host.onCreateInputConnection(EditorInfo())!! }
    return connection
  }

  /** Il testo che il campo ha (senza il carattere a larghezza zero del rientro finale) e' quello del documento. */
  private fun fieldText(): String =
    editor().fetchSemanticsNode().config[SemanticsProperties.EditableText].text.replace("​", "")

  private fun assertInSync() {
    compose.waitForIdle()
    assertEquals(state.document.text, fieldText())
  }

  @Test fun committedTextTakesTheChosenFormat() {
    setEditor()
    val ic = connect()
    compose.runOnIdle { state.toggle(FluidRichTextInlineStyle.Bold) }
    compose.runOnIdle { ic.commitText("Ciao", 1) }
    compose.runOnIdle {
      assertEquals("Ciao", state.document.text)
      assertTrue(state.document.text.indices.all { state.document.formatAt(it).bold })
      assertEquals(FluidRichTextChangeSource.Typing, state.lastChangeSource)
    }
    compose.runOnIdle { ic.commitText(" mondo", 1) }
    compose.runOnIdle { assertTrue(state.document.formatAt(6).bold) }
    assertInSync()
  }

  @Test fun compositionLikeGboardKeepsFormatAndText() {
    setEditor(buildFluidRichText { paragraph { append("Un ") } })
    val ic = connect()
    compose.runOnIdle { state.select(TextRange(3)) }
    compose.runOnIdle { state.toggle(FluidRichTextInlineStyle.Italic) }
    compose.runOnIdle { ic.setComposingText("p", 1) }
    compose.runOnIdle { ic.setComposingText("pa", 1) }
    compose.runOnIdle { ic.setComposingText("parola", 1) }
    compose.runOnIdle {
      assertEquals("Un parola", state.document.text)
      assertNotNull(state.composition)
    }
    // La correzione automatica sostituisce la parola composta e aggiunge lo spazio.
    compose.runOnIdle { ic.commitText("parole ", 1) }
    compose.runOnIdle {
      assertEquals("Un parole ", state.document.text)
      assertTrue((3 until 9).all { state.document.formatAt(it).italic })
      assertFalse(state.document.formatAt(0).italic)
    }
    // Un suggerimento che rimpiazza una parola gia' scritta (composing region + commit).
    compose.runOnIdle { ic.setComposingRegion(3, 9) }
    compose.runOnIdle { ic.commitText("frase", 1) }
    compose.runOnIdle {
      assertEquals("Un frase ", state.document.text)
      assertTrue((3 until 8).all { state.document.formatAt(it).italic })
    }
    assertInSync()
  }

  @Test fun enterContinuesTheListAndDoubleEnterLeavesIt() {
    setEditor(buildFluidRichText { paragraph(bullet) { append("uno") } })
    val ic = connect()
    compose.runOnIdle { ic.commitText("\n", 1) }
    compose.runOnIdle {
      assertEquals("uno\n", state.document.text)
      assertEquals(listOf(bullet, bullet), state.document.paragraphStyles)
    }
    compose.runOnIdle { ic.commitText("\n", 1) }
    compose.runOnIdle {
      assertEquals("uno\n", state.document.text)
      assertEquals(listOf(bullet, FluidRichTextParagraphStyle.Plain), state.document.paragraphStyles)
      assertEquals(FluidRichTextChangeSource.Formatting, state.lastChangeSource)
    }
    assertInSync()
  }

  @Test fun backspaceAtStartOfItemRemovesTheItemThenMerges() {
    setEditor(buildFluidRichText { paragraph { append("prima") }; paragraph(bullet) { append("voce") } })
    val ic = connect()
    compose.runOnIdle { state.select(TextRange(6)) }
    compose.runOnIdle { ic.deleteSurroundingText(1, 0) }
    compose.runOnIdle {
      assertEquals("prima\nvoce", state.document.text)
      assertEquals(FluidRichTextParagraphStyle.Plain, state.document.paragraphStyles[1])
    }
    compose.runOnIdle { ic.deleteSurroundingText(1, 0) }
    compose.runOnIdle { assertEquals("primavoce", state.document.text) }
    assertInSync()
  }

  @Test fun hardwareBackspaceAtDocumentStartRemovesTheItem() {
    setEditor(buildFluidRichText { paragraph(bullet) { append("voce") } })
    connect()
    compose.runOnIdle { state.select(TextRange(0)) }
    editor().performKeyInput { pressKey(Key.Backspace) }
    compose.runOnIdle {
      assertEquals("voce", state.document.text)
      assertEquals(FluidRichTextParagraphStyle.Plain, state.document.paragraphStyles.single())
    }
  }

  @Test fun selectionShowsTheBarAndItReflectsStyles() {
    setEditor(buildFluidRichText { paragraph { bold("grassetto"); append(" normale") } })
    connect()
    compose.onNodeWithContentDescription("Grassetto").assertDoesNotExist()
    compose.runOnIdle { state.select(TextRange(0, 4)) }
    val boldButton = compose.onNodeWithContentDescription("Grassetto")
    boldButton.assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .assertWidthIsAtLeast(48.dp)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    compose.onNodeWithContentDescription("Corsivo")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    // Toccare il tasto toglie il grassetto alla selezione e non toglie il fuoco all'editor.
    boldButton.performClick()
    compose.runOnIdle {
      assertFalse(state.document.formatAt(0).bold)
      assertTrue(state.isFocused)
    }
    boldButton.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    compose.onNodeWithContentDescription("Elenco puntato").performClick()
    compose.runOnIdle { assertEquals(bullet, state.document.paragraphStyles.single()) }
    // Annulla sta in fondo alla barra, che scorre di lato su un telefono stretto.
    compose.onNodeWithContentDescription("Annulla").performScrollTo().performClick()
    compose.runOnIdle { assertEquals(FluidRichTextParagraphStyle.Plain, state.document.paragraphStyles.single()) }
  }

  @Test fun formatButtonRequestsTheBarWithoutSelection() {
    setEditor(FluidRichTextDocument.fromPlainText("testo"))
    connect()
    compose.runOnIdle { assertFalse(state.isFormattingBarVisible) }
    compose.runOnIdle { state.isFormattingRequested = true }
    compose.onNodeWithContentDescription("Formattazione")
      .assertIsDisplayed()
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
      .performClick()
    compose.runOnIdle { assertFalse(state.isFormattingBarVisible) }
  }

  @Test fun talkBackHearsTheStylesAtTheCursor() {
    setEditor(buildFluidRichText {
      paragraph(bullet) { bold("uno"); append(" "); link("sito", "https://esempio.it") }
    })
    connect()
    compose.runOnIdle { state.select(TextRange(1)) }
    editor().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Grassetto, Elenco puntato"))
    compose.runOnIdle { state.select(TextRange(6)) }
    editor().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Elenco puntato, link a https://esempio.it"))
    val text = editor().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text
    assertEquals("uno sito", text)
  }

  @Test fun pasteHtmlKeepsSupportedFormattingOnly() {
    setEditor(FluidRichTextDocument.fromPlainText("prima "))
    connect()
    val clipboard = host.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
      ClipData.newHtmlText(
        "web",
        "Titolo e link",
        "<script>alert(1)</script><b>Titolo</b> e <a href=\"javascript:x\">link</a><img src=\"https://t.example/p.gif\">",
      ),
    )
    compose.runOnIdle { state.select(TextRange(6)) }
    editor().performSemanticsAction(SemanticsActions.PasteText)
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals("prima Titolo e link", state.document.text)
      assertTrue(state.document.formatAt(6).bold)
      assertTrue(state.document.spans.none { it.format.link != null })
      assertEquals(FluidRichTextChangeSource.Paste, state.lastChangeSource)
    }
    assertInSync()
    // Un incolla si annulla in un passo.
    compose.runOnIdle { state.undo() }
    compose.runOnIdle { assertEquals("prima ", state.document.text) }
  }

  @Test fun copyPutsHtmlOnTheClipboard() {
    setEditor(buildFluidRichText { paragraph { bold("forte"); append(" piano") } })
    connect()
    compose.runOnIdle { state.select(TextRange(0, 5)) }
    editor().performSemanticsAction(SemanticsActions.CopyText)
    compose.waitForIdle()
    val clipboard = host.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val item = clipboard.primaryClip!!.getItemAt(0)
    assertEquals("forte", item.text.toString())
    assertEquals("<div><b>forte</b></div>", item.htmlText)
    // Tagliare: il testo esce dal documento e l'HTML va comunque negli appunti.
    compose.runOnIdle { state.select(TextRange(6, 11)) }
    compose.runOnIdle { state.toggle(FluidRichTextInlineStyle.Italic) }
    editor().performSemanticsAction(SemanticsActions.CutText)
    compose.waitForIdle()
    compose.runOnIdle { assertEquals("forte ", state.document.text) }
    assertEquals("<div><i>piano</i></div>", clipboard.primaryClip!!.getItemAt(0).htmlText)
    // E reincollato torna in corsivo.
    editor().performSemanticsAction(SemanticsActions.PasteText)
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals("forte piano", state.document.text)
      assertTrue(state.document.formatAt(8).italic)
    }
  }

  @Test fun ctrlZUndoesTheEditorHistory() {
    setEditor(FluidRichTextDocument.fromPlainText("abc"))
    connect()
    compose.runOnIdle { state.select(TextRange(0, 3)) }
    editor().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.B) } }
    compose.runOnIdle { assertTrue(state.document.formatAt(0).bold) }
    editor().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) } }
    compose.runOnIdle { assertFalse(state.document.formatAt(0).bold) }
    editor().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Y) } }
    compose.runOnIdle { assertTrue(state.document.formatAt(0).bold) }
  }

  @Test fun emptyLastListItemPutsTheCursorAfterTheMarker() {
    setEditor(buildFluidRichText { paragraph(bullet) { append("uno") }; paragraph(bullet) { } })
    connect()
    val layout = textLayout()
    val plainStart = layout.getCursorRect(0).left
    val emptyItem = layout.getCursorRect(state.document.text.length).left
    // La voce vuota in fondo ha lo stesso rientro della prima: il cursore sta dopo il pallino.
    assertTrue("rientro $emptyItem", emptyItem > 10f)
    assertEquals(plainStart, emptyItem, 1f)
  }

  @Test fun largeTextAndRightToLeftStayUsable() {
    setEditor(
      buildFluidRichText {
        paragraph(bullet) { append("مرحبا بالعالم، هذا نص طويل يلتف على عدة أسطر في المحرر") }
        paragraph(FluidRichTextParagraphStyle(quote = true)) { append("اقتباس") }
      },
      scale = 2f,
      rtl = true,
    )
    val ic = connect()
    compose.runOnIdle { state.select(TextRange(0, 5)) }
    compose.onNodeWithContentDescription("Grassetto").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    compose.runOnIdle { ic.commitText("أهلا", 1) }
    compose.runOnIdle { assertTrue(state.document.text.startsWith("أهلا")) }
    // In arabo il rientro sta a destra: il cursore all'inizio della voce e' lontano dal bordo destro.
    val layout = textLayout()
    val width = layout.size.width
    assertTrue(layout.getCursorRect(state.document.paragraphStart(1)).left < width - 10f)
    assertInSync()
  }

  @Test fun programmaticChangesReachTheField() {
    setEditor(FluidRichTextDocument.fromPlainText("Testo"))
    connect()
    compose.runOnIdle {
      state.insert(5, buildFluidRichText { paragraph { }; paragraph { bold("Firma") } })
    }
    compose.runOnIdle { assertEquals(TextRange(11), state.selection) }
    assertEquals("Testo\nFirma", fieldText())
  }

  private fun textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    compose.runOnIdle {
      editor().fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
    }
    return results.first()
  }
}
