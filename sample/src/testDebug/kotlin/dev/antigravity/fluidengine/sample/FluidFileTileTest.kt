package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidFileTileTest {
  @get:Rule val compose = createComposeRule()

  private fun show(scale: Float = 1f, dark: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalFluidMotionPolicy provides FluidMotionPolicy(0f)) { content() }
      }
    }
  }

  @Test fun `semantica completa italiana e azioni annulla riprova rimuovi`() {
    var cancel = 0; var retry = 0; var remove = 0; var open = 0
    show {
      Column {
        FluidFileTile("Fattura.pdf", { open++ }, sizeBytes = 1200000, locale = Locale.ITALIAN)
        FluidFileTile("download.zip", { open++ }, status = remember { FluidFileStatus.Transferring { .5f } }, onCancel = { cancel++ })
        FluidFileTile("errore.pdf", { open++ }, status = FluidFileStatus.Failed, onRetry = { retry++ }, onRemove = { remove++ })
      }
    }
    compose.onNodeWithContentDescription("Fattura.pdf, PDF, 1,2 MB, Scaricato").assertExists().performClick()
    compose.onNodeWithContentDescription("Annulla trasferimento").assertHeightIsAtLeast(48.dp).performClick()
    compose.onNodeWithContentDescription("Riprova").performClick()
    compose.onNodeWithContentDescription("Rimuovi file").performClick()
    assertEquals(listOf(1, 1, 1, 1), listOf(cancel, retry, remove, open))
  }

  @Test fun `inglese e stato indeterminato con moto ridotto`() {
    show {
      CompositionLocalProvider(LocalFluidLabels provides FluidLabels.english()) {
        FluidFileTile("a.apk", {}, mimeType = "application/pdf", status = remember { FluidFileStatus.Transferring() }, onCancel = {})
      }
    }
    compose.onNodeWithContentDescription("a.apk, Executable or package, warning, Transferring").assertExists()
    compose.onNodeWithContentDescription("Cancel transfer").assertExists()
    compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
  }

  @Test fun `nome lungo doppia estensione resta visibile a 200 scuro`() {
    val name = "fattura molto lunga con informazioni ulteriori ripetute.pdf.apk"
    show(scale = 2f, dark = true) { FluidFileTile(name, {}, modifier = Modifier.width(300.dp)) }
    compose.onNodeWithText(".apk", substring = true, useUnmergedTree = true).assertExists()
    compose.onNodeWithContentDescription("$name, Eseguibile o pacchetto, attenzione, Scaricato").assertExists()
    val text = compose.onNodeWithText(".apk", substring = true, useUnmergedTree = true).fetchSemanticsNode()
    val layouts = mutableListOf<TextLayoutResult>()
    text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    assertFalse(layouts.single().hasVisualOverflow)
    assertTrue(layouts.single().lineCount <= 2)
    val tile = compose.onNodeWithContentDescription("$name, Eseguibile o pacchetto, attenzione, Scaricato").fetchSemanticsNode()
    assertTrue(tile.boundsInRoot.contains(text.boundsInRoot.topLeft))
    assertTrue(tile.boundsInRoot.contains(text.boundsInRoot.bottomRight - Offset(.1f, .1f)))
  }

  @Test fun `chip ha bersaglio espanso e margine cliccabile senza alzare la riga`() {
    var clicks = 0
    show { Box(Modifier.padding(32.dp)) { FluidFileChip("a.pdf", { clicks++ }, Modifier.testTag("chip")) } }
    val chip = compose.onNodeWithTag("chip").fetchSemanticsNode()
    val touch = chip.touchBoundsInRoot
    assertTrue(touch.height >= 96f) // xhdpi: 48 dp
    assertTrue(touch.height >= chip.boundsInRoot.height)
    compose.onRoot().performTouchInput { click(Offset(touch.center.x, touch.top + 1f)) }
    assertEquals(1, clicks)
  }

  @Test fun `chip cede al trascinamento della lista ma il tap clicca`() {
    val state = LazyListState()
    var clicks = 0
    show {
      LazyColumn(state = state) {
        items(40) { i -> Box(Modifier.height(72.dp)) { FluidFileChip("file$i.pdf", { clicks++ }, Modifier.testTag("chip$i")) } }
      }
    }
    compose.onNodeWithTag("chip3").performTouchInput { click() }
    assertEquals(1, clicks)
    compose.onNodeWithTag("chip3").performTouchInput {
      down(center); advanceEventTime(40)
      repeat(10) { moveBy(Offset(0f, -25f), delayMillis = 8) }
      up()
    }
    assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
    assertEquals(1, clicks)
  }

  @Test fun `riuso della tile aggiorna nome tipo dimensione e stato senza dati precedenti`() {
    val ready = mutableStateOf(false)
    var downloads = 0
    show {
      FluidFileTile(if (ready.value) "nuovo.apk" else "vecchio.pdf", { downloads++ },
        status = if (ready.value) FluidFileStatus.Ready else FluidFileStatus.Remote,
        sizeBytes = if (ready.value) 0 else null, locale = Locale.ITALIAN)
    }
    compose.onNodeWithContentDescription("vecchio.pdf, PDF, Da scaricare").performClick()
    assertEquals(1, downloads)
    compose.runOnIdle { ready.value = true }
    compose.onNodeWithContentDescription("nuovo.apk, Eseguibile o pacchetto, attenzione, 0 B, Scaricato").assertExists()
    compose.onNodeWithContentDescription("vecchio.pdf, PDF, Da scaricare").assertDoesNotExist()
  }

  @Test fun `progresso non ricompone contenuto e miniatura`() {
    val progress = mutableStateOf<Float?>(.1f)
    var thumbnails = 0
    show {
      FluidFileTile("a.jpg", {}, status = remember { FluidFileStatus.Transferring { progress.value } }, thumbnail = { SideEffect { thumbnails++ } })
    }
    val before = thumbnails
    compose.runOnIdle { progress.value = .9f }
    compose.waitForIdle()
    assertEquals(before, thumbnails)
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, androidx.compose.ui.semantics.ProgressBarRangeInfo(.9f, 0f..1f))).assertExists()
  }
}
