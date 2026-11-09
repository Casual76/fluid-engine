package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.catalog.NotificationUndoDemo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La pagina «Avvisi con azione» e' una lista di pulsanti, uno per riga, e con il testo al 200% e' piu' alta dello
 * schermo: un dito che parte da un pulsante deve far scorrere la pagina (`FluidPressGrip.YieldsToScroll`, come chiede
 * `ADOZIONE.md` per i pulsanti ripetuti in una lista che scorre), e un tocco deve cliccare ancora.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class NotificationUndoScrollTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private fun show() {
    compose.setContent {
      Box(Modifier.height(360.dp)) {
        // Il testo al 200%: la pagina non sta nello spazio e scorre.
        CompositionLocalProvider(LocalDensity provides Density(2f, 2f)) { NotificationUndoDemo() }
      }
    }
    compose.waitForIdle()
  }

  private fun top(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

  @Test fun `un dito che parte da un pulsante fa scorrere la pagina e non clicca`() {
    show()
    val before = top("Errore")
    compose.onNodeWithText("Errore").performTouchInput {
      down(center)
      repeat(6) { moveBy(Offset(0f, -30f)) }
      up()
    }
    compose.waitForIdle()
    val after = top("Errore")
    assertTrue("la pagina non e' scorsa ($before -> $after)", after < before - 60f)
    assertEquals(
      "il pulsante da cui e' partito il trascinamento non deve cliccare",
      0, compose.onAllNodes(hasText("Operazione non riuscita")).fetchSemanticsNodes().size,
    )
  }

  @Test fun `un tocco sul pulsante clicca ancora`() {
    show()
    compose.onNodeWithText("Errore").performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitUntil(5_000) {
      compose.onAllNodes(hasText("Operazione non riuscita")).fetchSemanticsNodes().isNotEmpty()
    }
  }
}
