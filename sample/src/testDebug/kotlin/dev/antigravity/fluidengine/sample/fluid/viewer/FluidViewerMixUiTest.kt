package dev.antigravity.fluidengine.sample.fluid.viewer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class FluidViewerMixUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun durationIsRequestedBeforePlayWithNoTagsOrArtwork() {
    var requests = 0
    var plays = 0
    val session = object : FluidMediaSession, FluidMediaDurationLoader {
      override val playing = false
      override val positionMillis = 0L
      override var durationMillis by mutableStateOf<Long?>(null)
      override val canSeek get() = durationMillis != null
      override fun requestDuration() { requests++; durationMillis = 12_000 }
      override fun play() { plays++ }
      override fun pause() {}
      override fun seekTo(positionMillis: Long) {}
    }
    compose.setContent { FluidTheme(EngineSettings()) { FluidFileViewer(1, content = { FluidViewerContent.Media(session) }) } }
    compose.onNodeWithContentDescription("Posizione di riproduzione").assertIsDisplayed().assertIsEnabled()
      .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
        "0:00 di 0:12"))
    assertEquals(1, requests)
    assertEquals(0, plays)
  }

  @Test fun findKeepsTheSelectedLineBetweenTopBarAndEditorAfterResize() {
    val document = object : FluidTextSource {
      override val lineCount = 300
      override suspend fun readLines(start: Int, count: Int) = (start until minOf(start + count, lineCount)).map {
        if (it == 160) "occorrenza centrale" else "riga $it"
      }
    }
    var height by mutableStateOf(760.dp)
    compose.setContent {
      FluidTheme(EngineSettings()) {
        Box(Modifier.fillMaxWidth().height(height)) {
          FluidFileViewer(1, content = { FluidViewerContent.Text(document) })
        }
      }
    }
    compose.onNodeWithContentDescription("Trova").performClick()
    val editor = compose.onNode(hasSetTextAction())
    editor.performTextInput("occorrenza")
    compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("1 di 1").fetchSemanticsNodes().isNotEmpty() }
    compose.runOnIdle { height = 360.dp }
    compose.waitForIdle()
    val hit = compose.onNodeWithText("occorrenza centrale").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val chrome = compose.onNodeWithContentDescription("Informazioni").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    assertTrue("Barra $chrome, occorrenza $hit", hit.top >= chrome.bottom)
    assertTrue("Occorrenza $hit sopra il campo", hit.bottom <= editor.fetchSemanticsNode().boundsInRoot.top)
  }

  @Test fun fileFactsAndExecutableWarningFitPhoneAtBothTextScales() = checkUnknownVariants()

  @Config(sdk = [35], qualifiers = "w1000dp-h800dp-mdpi")
  @Test fun fileFactsAndExecutableWarningFitWideTabletAtBothTextScales() = checkUnknownVariants()

  private fun checkUnknownVariants() {
    var font by mutableFloatStateOf(1f)
    var name by mutableStateOf("programma.exe")
    var icon by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    compose.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, font)) {
        FluidTheme(EngineSettings()) {
          val file = FluidFileInfo(name)
          val info = FluidViewerInfo(file, thumbnail = icon, facts = listOf("File di prova"))
          FluidViewerInfoProvider({ info }) {
            FluidFileViewer(1, content = { FluidViewerContent.Unknown(file, openWith = {}) })
          }
        }
      }
    }
    for (scale in listOf(1f, 2f)) {
      for ((fileName, hasIcon) in listOf("programma.exe" to false, "senza.apk" to false,
        "con.apk" to true, "dati.bin" to false)) {
        compose.runOnIdle {
          font = scale
          name = fileName
          icon = if (hasIcon) androidx.compose.ui.graphics.ImageBitmap(24, 24) else null
        }
        compose.waitForIdle()
        compose.onNodeWithText("File di prova").performScrollTo().assertIsDisplayed()
        val warning = compose.onNodeWithText(
          if (fileName.endsWith(".apk")) "È un'app da installare." else "È un'app o un programma", substring = true)
        if (fileName.endsWith(".bin")) warning.assertDoesNotExist()
        else warning.performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Apri con…").performScrollTo().assertIsDisplayed()
      }
    }
  }

  @Test fun mediumKeepsPhotoAboveTheInspectorAndExposesCopyAndClose() {
    val photo = FluidViewerContent.Image(1.5f) { Box(Modifier.fillMaxSize().testTag("photo")) }
    val info = FluidViewerInfo(FluidFileInfo("Foto.jpg"),
      facts = listOf("JPEG"), sections = listOf(FluidViewerInfoSection("Luogo",
        listOf(FluidViewerInfoRow("Coordinate", "43, 11")), content = { Text("Mappa dell'app") })))
    compose.setContent { FluidTheme(EngineSettings()) {
      FluidViewerInfoProvider({ info }) { FluidFileViewer(1, content = { photo }) }
    } }
    val before = compose.onNodeWithTag("photo").fetchSemanticsNode().boundsInRoot
    compose.onNodeWithContentDescription("Informazioni").performClick()
    compose.waitForIdle()
    val after = compose.onNodeWithTag("photo").fetchSemanticsNode().boundsInRoot
    assertTrue("Foto prima $before, dopo $after", after.center.y < before.center.y)
    val header = compose.onAllNodesWithText("Foto.jpg", useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue(header.any { it.boundsInRoot.top >= after.bottom })
    compose.onNodeWithText("JPEG").assertIsDisplayed()
    compose.onNodeWithText("Coordinate").performSemanticsAction(SemanticsActions.OnLongClick)
    compose.onNodeWithText("Mappa dell'app").assertExists()
    compose.onNodeWithContentDescription("Chiudi").performClick()
    compose.onNodeWithText("JPEG").assertDoesNotExist()
  }
}
