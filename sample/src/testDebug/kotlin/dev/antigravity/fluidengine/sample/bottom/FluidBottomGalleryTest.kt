package dev.antigravity.fluidengine.sample.bottom

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.material.LocalGalleryPageOpener
import dev.antigravity.fluidengine.ui.fluid.FluidSearchBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidSearchExpansion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPurpose
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * La galleria di F4B (F4B.5): ogni sede della ricerca chiede alla barra ciò che dice il suo titolo, e la pagina mostra i
 * quattro capitoli con le frasi che le riprese toccano. La finestra è alta perché la lista componga tutti i capitoli.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h3000dp-xhdpi")
class FluidBottomGalleryTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  @Test fun everySearchModeAsksTheBarForWhatItsRowSays() {
    val specs = BottomSearchMode.entries.associateWith { it.spec("", {}, scopes = {}, suggestions = {}) }
    with(specs.getValue(BottomSearchMode.Bottom)) {
      assertEquals(FluidSearchPlacement.BottomBar, placement)
      assertEquals(FluidSearchBehavior.Automatic, behavior)
      assertNotNull("gli ambiti stanno in linea", scopes)
    }
    assertEquals(FluidSearchPlacement.Automatic, specs.getValue(BottomSearchMode.Automatic).placement)
    with(specs.getValue(BottomSearchMode.Minimized)) {
      assertEquals(FluidSearchPlacement.Automatic, placement)
      assertEquals(FluidSearchBehavior.CollapseToButton, behavior)
    }
    with(specs.getValue(BottomSearchMode.Top)) {
      assertEquals(FluidSearchPlacement.TopBar, placement)
      assertEquals(FluidSearchExpansion.StayAtTop, expansion)
    }
    assertEquals(FluidSearchExpansion.AboveKeyboard, specs.getValue(BottomSearchMode.TopToKeyboard).expansion)
    with(specs.getValue(BottomSearchMode.Input)) {
      assertEquals(FluidSearchPurpose.Input, purpose)
      assertNull("l'inserimento scrive e non sceglie: niente ambiti", scopes)
      assertNotNull("«Fine» conferma", onSearch)
    }
    // La riga della ricerca in basso conserva le frasi delle riprese di F4B.2.
    assertEquals("Mostra la ricerca in basso", BottomSearchMode.Bottom.title)
    assertEquals("Nascondi la ricerca", BottomSearchMode.Bottom.activeTitle)
  }

  @Test fun theGalleryReadsInFourChaptersAndOpensTheTabletPage() {
    val opened = mutableListOf<GalleryPage>()
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalGalleryPageOpener provides { opened += it }) { BottomBarPage {} }
      }
    }
    advance(1_200)
    for (chapter in listOf("Barra in basso e capsula", "Ricerca", "Tab bar", "Tab bar in alto e barra laterale")) {
      compose.onNodeWithText(chapter).assertExists()
    }
    compose.onNodeWithText("Ricerca in alto").performClick()
    advance(800)
    compose.onNodeWithText("In uso", substring = true).assertExists()
    compose.onNodeWithText("Mostra la ricerca in basso").performClick()
    advance(800)
    compose.onNodeWithText("Nascondi la ricerca").assertExists()
    compose.onNodeWithText("In uso", substring = true).assertDoesNotExist()
    compose.onNodeWithText("Piega barra").performClick()
    advance(800)
    compose.onNodeWithText("Apri barra").assertExists()
    compose.onNodeWithText("Apri la navigazione adattiva").performClick()
    advance(100)
    assertEquals(listOf(GalleryPage.TabletNavigation), opened)
  }
}
