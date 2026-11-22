package dev.antigravity.fluidengine.sample.fluid.viewer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class FluidMediaPagesPresetUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun videoUsesOneSetOfCommandsAndOnlyTheFileTapHidesThem() {
    val player = Session()
    compose.setContent {
      Viewer(FluidViewerContent.Media(player, videoSurface = {
        Box(Modifier.fillMaxSize().background(Color.Gray).testTag("video"))
      }))
    }
    val picture = compose.onNodeWithTag("video")
    val bounds = picture.fetchSemanticsNode().boundsInRoot
    compose.onNodeWithContentDescription("Riproduci").assertHasClickAction().performClick()
    compose.onNodeWithContentDescription("Pausa").assertHasClickAction()
    compose.mainClock.advanceTimeBy(18_000)
    compose.onNodeWithContentDescription("Pausa").assertIsDisplayed()
    val timeline = compose.onNodeWithContentDescription("Posizione di riproduzione")
    timeline.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0:05 di 1:00"))
    timeline.performSemanticsAction(SemanticsActions.SetProgress) { set -> assertTrue(set(30_000f)) }
    assertEquals(listOf(30_000L), player.seeks)
    picture.performTouchInput { click(Offset(width * .2f, height * .35f)) }
    compose.onNodeWithContentDescription("Pausa").assertDoesNotExist()
    timeline.assertDoesNotExist()
    assertEquals(bounds, picture.fetchSemanticsNode().boundsInRoot)
    compose.mainClock.advanceTimeBy(18_000)
    compose.onNodeWithContentDescription("Pausa").assertDoesNotExist()
    picture.performTouchInput { click(Offset(width * .2f, height * .35f)) }
    compose.onNodeWithContentDescription("Pausa").performClick()
    assertEquals(listOf("play", "pause"), player.commands)
  }

  @Test fun audioRecordsOneSeekPerActionAndExplainsUnknownDuration() {
    val player = Session()
    compose.setContent { Viewer(FluidViewerContent.Media(player, artwork = { Box(Modifier.size(80.dp).testTag("onda")) })) }
    compose.onNodeWithTag("onda").assertIsDisplayed()
    compose.onNodeWithContentDescription("Indietro di 10 secondi").performClick()
    compose.onNodeWithContentDescription("Avanti di 10 secondi").performClick()
    assertEquals(listOf(0L, 10_000L), player.seeks)
    compose.runOnIdle { player.durationMillis = null }
    val timeline = compose.onNodeWithContentDescription("Posizione di riproduzione")
    timeline.assertIsNotEnabled().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
      "0:10 di Durata sconosciuta"))
    compose.onNodeWithContentDescription("Avanti di 10 secondi").assertDoesNotExist()
    compose.runOnIdle { player.error = "File illeggibile" }
    compose.onNodeWithText("File illeggibile").assertIsDisplayed()
    compose.onNodeWithContentDescription("Riproduci").assertIsNotEnabled()
  }

  @Test fun changingOrLeavingMediaDoesNotCommandTheOwnersPlayer() {
    val first = Session()
    val second = Session()
    var firstFrame by mutableStateOf(false)
    var file by mutableStateOf<FluidViewerContent?>(FluidViewerContent.Media(first, videoSurface = {
      if (firstFrame) Box(Modifier.size(120.dp, 68.dp).testTag("primo-fotogramma"))
    }))
    compose.setContent { file?.let { Viewer(it) } }
    compose.onNodeWithContentDescription("Riproduci").performClick()
    compose.runOnIdle { firstFrame = true }
    val frame = compose.onNodeWithTag("primo-fotogramma").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val media = compose.onNodeWithTag("preset-media").fetchSemanticsNode().boundsInRoot
    assertEquals(media.center, frame.center)
    compose.runOnIdle { file = FluidViewerContent.Media(second) }
    compose.onNodeWithTag("primo-fotogramma").assertDoesNotExist()
    compose.onNodeWithContentDescription("Riproduci").performClick()
    compose.runOnIdle { file = null }
    compose.onNodeWithContentDescription("Pausa").assertDoesNotExist()
    assertEquals(listOf("play"), first.commands)
    assertEquals(listOf("play"), second.commands)
  }

  @Test fun pageThumbnailJumpsAndZoomRemainsAccessible() {
    compose.setContent { Viewer(FluidViewerContent.Pages(Document())) }
    val opener = compose.onNodeWithContentDescription("Miniature delle pagine")
    opener.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pagina 1 di 5"))
    opener.performClick()
    compose.onNode(hasContentDescription("Pagina 3 di 5") and hasClickAction()).performClick()
    opener.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pagina 3 di 5"))
    val reader = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
    val actions = reader.fetchSemanticsNode().config[SemanticsActions.CustomActions]
    compose.runOnIdle {
      assertTrue(actions.first { it.label == "Ingrandisci" }.action())
    }
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 250%")).assertExists()
    opener.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pagina 3 di 5"))
  }

  @Test fun badPageOffersRetryAndKeepsItsSlot() {
    val doc = Document(failOnce = true)
    compose.setContent { Viewer(FluidViewerContent.Pages(doc)) }
    compose.onNodeWithText("Impossibile mostrare la pagina").assertIsDisplayed()
    compose.onNodeWithText("Riprova").performClick()
    compose.onNodeWithText("Impossibile mostrare la pagina").assertDoesNotExist()
    compose.onNodeWithContentDescription("Pagina 1 di 5").assertIsDisplayed()
  }

  @Composable private fun Viewer(file: FluidViewerContent) {
    FluidTheme(settings = EngineSettings()) {
      FluidFileViewer(1, content = { file }, onBack = {}, renderContent = { content, visible ->
        when (content) {
          is FluidViewerContent.Media -> FluidMediaPreset(content, visible, Modifier.testTag("preset-media"), sampleVideo = true)
          is FluidViewerContent.Pages -> FluidPagesPreset(content, visible)
        }
      })
    }
  }

  private class Session : FluidMediaSession {
    override var playing: Boolean by mutableStateOf(false)
    override var durationMillis: Long? by mutableStateOf(60_000L)
    override var positionMillis: Long by mutableStateOf(5_000L)
    override var error: String? by mutableStateOf(null)
    override val canSeek = true
    val seeks = mutableListOf<Long>()
    val commands = mutableListOf<String>()
    override fun play() { commands += "play"; playing = true }
    override fun pause() { commands += "pause"; playing = false }
    override fun seekTo(positionMillis: Long) { seeks += positionMillis; this.positionMillis = positionMillis }
  }

  private class Document(private var failOnce: Boolean = false) : FluidPageSource {
    override val count = 5
    override suspend fun size(index: Int) = if (index % 2 == 0) IntSize(612, 792) else IntSize(792, 612)
    override suspend fun render(index: Int, target: IntSize): ImageBitmap {
      if (index == 0 && failOnce) {
        failOnce = false
        error("Test source could not render its first page")
      }
      return ImageBitmap(target.width, target.height)
    }
  }
}
