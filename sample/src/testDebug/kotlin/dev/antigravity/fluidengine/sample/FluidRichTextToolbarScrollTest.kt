package dev.antigravity.fluidengine.sample

import android.os.Parcel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextRange
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.richtext.FluidRichTextDocument
import dev.antigravity.fluidengine.richtext.FluidRichTextEditor
import dev.antigravity.fluidengine.richtext.FluidRichTextListKind
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
 * La barra dell'editor con formattazione scorre di lato su un telefono stretto, e il gesto vero (il dito
 * che parte sopra un tasto e si trascina) deve far scorrere la fila: i tasti cedono al gesto
 * (`FluidPressGrip.YieldsToScroll`). Un tocco semplice li preme ancora.
 *
 * I test di scorrimento semantico (`performScrollTo`) non passano dal gesto e non vedono il difetto:
 * qui si usa `performTouchInput` (giu', spostamenti, su) sullo stesso tasto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidRichTextToolbarScrollTest {
  @get:Rule val compose = createComposeRule()

  private lateinit var state: FluidRichTextState

  private fun setEditor(initial: FluidRichTextDocument = FluidRichTextDocument.fromPlainText("Testo")) {
    state = FluidRichTextState(initial)
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(0f)) {
          Box(Modifier.fillMaxSize()) {
            FluidRichTextEditor(state, contentDescription = "Corpo")
            FluidRichTextToolbar(state, Modifier.align(Alignment.BottomCenter), avoidKeyboard = false)
          }
        }
      }
    }
    compose.onNode(hasSetTextAction() and hasContentDescription("Corpo")).performClick()
    // Il testo selezionato: la barra compare da sola, e uno stile cambia il documento in modo visibile.
    compose.runOnIdle { state.select(TextRange(0, 5)) }
    compose.waitForIdle()
  }

  private fun left(description: String): Float =
    compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot().left.value

  private fun dragLeftFrom(description: String) {
    compose.onNodeWithContentDescription(description).performTouchInput {
      down(center)
      repeat(10) { moveBy(Offset(-12f, 0f)) }
      up()
    }
    compose.waitForIdle()
  }

  @Test fun aDragThatStartsOnBoldScrollsTheRowAndDoesNotPressIt() {
    setEditor()
    val before = left("Grassetto")
    dragLeftFrom("Grassetto")
    val after = left("Grassetto")
    assertTrue("la fila deve scorrere: $before -> $after", after < before - 10f)
    assertEquals("il grassetto non deve scattare", FluidRichTextDocument.fromPlainText("Testo"), state.document)
    assertFalse(state.format.bold)
  }

  private fun assertDragOnActionScrollsAndDoesNotPress(description: String) {
    setEditor()
    val before = left(description)
    dragLeftFrom(description)
    val after = left(description)
    assertTrue("$description: la fila deve scorrere: $before -> $after", after < before - 10f)
    assertEquals("$description non deve scattare", FluidRichTextDocument.fromPlainText("Testo"), state.document)
  }

  @Test fun aDragThatStartsOnItalicScrollsTheRow() = assertDragOnActionScrollsAndDoesNotPress("Corsivo")

  @Test fun aDragThatStartsOnTheBulletListScrollsTheRow() = assertDragOnActionScrollsAndDoesNotPress("Elenco puntato")

  @Test fun aDragThatStartsOnTheQuoteScrollsTheRow() = assertDragOnActionScrollsAndDoesNotPress("Citazione")

  @Test fun aDragThatStartsOnUnderlineScrollsTheRow() = assertDragOnActionScrollsAndDoesNotPress("Sottolineato")

  @Test fun aDragThatStartsOnTheNumberedListScrollsTheRow() = assertDragOnActionScrollsAndDoesNotPress("Elenco numerato")

  @Test fun aSimpleTapStillAppliesTheCommand() {
    setEditor()
    compose.onNodeWithContentDescription("Grassetto").performTouchInput { click() }
    compose.waitForIdle()
    assertTrue(state.format.bold)
    assertEquals(1, state.document.spans.size)
    compose.onNodeWithContentDescription("Elenco puntato").performTouchInput { click() }
    compose.waitForIdle()
    assertEquals(FluidRichTextListKind.Bullet, state.document.paragraphStyles.single().list)
  }

  @Test fun afterScrollingTheRowATapStillWorks() {
    setEditor()
    dragLeftFrom("Grassetto")
    compose.onNodeWithContentDescription("Corsivo").performTouchInput { click() }
    compose.waitForIdle()
    assertTrue(state.format.italic)
  }

  /**
   * La stima della dimensione dello stato salvato e' per eccesso: un documento appena sotto il tetto,
   * scritto in un `Parcel` vero, pesa meno del tetto e ben meno del limite del Binder (circa 1 MB).
   */
  @Test fun theSavedSizeEstimateIsAnUpperBoundOfTheRealParcel() {
    val doc = buildFluidRichText {
      repeat(60) { index ->
        paragraph { link("l$index", "https://example.org/" + "a".repeat(1900) + index) }
      }
    }
    val saved = with(FluidRichTextState.Saver) {
      androidx.compose.runtime.saveable.SaverScope { true }.save(FluidRichTextState(doc))
    }
    assertNotNull("sotto il tetto si salva", saved)
    val parcel = Parcel.obtain()
    try {
      parcel.writeValue(saved)
      assertTrue("pesa ${parcel.dataSize()} byte", parcel.dataSize() < 256 * 1024)
    } finally {
      parcel.recycle()
    }
  }
}
