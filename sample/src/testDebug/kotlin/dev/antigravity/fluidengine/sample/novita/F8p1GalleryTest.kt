package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F8.1 · La pagina «Elementi nuovi»: l'indice ha le scene del pacchetto, e ogni scena si apre e torna all'indice. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class F8p1GalleryTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun advance(millis: Long) {
    repeat(((millis + 15) / 16).toInt()) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  @Test
  fun `l'indice ha una voce per scena, coi nomi brevi delle riprese`() {
    val ids = F8p1Section.scenes.map { it.id }
    assertEquals(listOf("numeri", "testo-arriva", "espandibile", "conto", "tempi", "immagine", "stati"), ids)
    assertTrue("una sezione per pacchetto", NewElementSections.map { it.pack }.distinct().size == NewElementSections.size)
    compose.mainClock.autoAdvance = false
    var closed = 0
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { NewElementsPage(onBack = { closed++ }, initial = null) }
    }
    advance(400)
    compose.onNodeWithTag(NewElementsTags.Index).assertExists()
    // L'intestazione di sezione è in maiuscolo (`FluidSectionHeader`).
    compose.onNodeWithText("F8.1 · Testo e numeri in movimento", ignoreCase = true).assertExists()
  }

  @Test
  fun `ogni scena si apre, sta in piedi e torna all'indice`() {
    compose.mainClock.autoAdvance = false
    var wanted by mutableStateOf<String?>(null)
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        androidx.compose.runtime.key(wanted) { NewElementsPage(onBack = {}, initial = wanted) }
      }
    }
    for (scene in F8p1Section.scenes) {
      wanted = scene.id
      advance(1_200)
      assertTrue("${scene.id} non si vede", compose.onAllNodesWithText(scene.title).fetchSemanticsNodes().isNotEmpty())
    }
    // Il back di una scena torna all'indice.
    wanted = "numeri"
    advance(400)
    compose.onNodeWithTag(F8p1Tags.BalanceUp).performClick()
    advance(700)
    compose.activity.onBackPressedDispatcher.onBackPressed()
    advance(600)
    compose.onNodeWithTag(NewElementsTags.Index).assertExists()
  }
}
