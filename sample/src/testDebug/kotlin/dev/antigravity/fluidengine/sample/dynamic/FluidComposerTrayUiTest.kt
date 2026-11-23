package dev.antigravity.fluidengine.sample.dynamic

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il vassoio dell'assistente sul telefono (F6B.16, dopo le riprese del 23 novembre 2026): il vassoio scorre di lato sotto
 * il chip del modello, e il PDF appena aggiunto usciva a destra con la sua X finché non lo si scorreva a mano. Ora
 * l'allegato che compare si porta in vista: a 360 e a 412 dp, dopo «Aggiungi», il PDF (l'ultimo aggiunto) sta tutto dentro
 * il vassoio, X compresa. La prova controlla anche che il vassoio sia davvero più lungo di quanto si vede, altrimenti non
 * proverebbe niente.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], shadows = [ShadowFirstPagePdfRenderer::class, ShadowFirstPagePdfPage::class])
class FluidComposerTrayUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  /** La scena dell'assistente col tema e l'host dei modali alla radice, come nella galleria. */
  private fun openAssistant() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          Box(Modifier.fillMaxSize()) {
            DynamicElementsPage(onBack = {}, initial = DynamicScene.AssistantComposer)
            FluidGlassModalHost(modals, backdrop = null)
          }
        }
      }
    }
    advance(900)
  }

  private val pdf = hasContentDescription("Quaderno di campo", substring = true) and hasClickAction()

  private fun checkTray(width: String) {
    openAssistant()
    compose.onNode(hasContentDescription("Aggiungi") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    // Lo scritto fatto dal tocco, fuori dalla composizione, arriva alla ricomposizione solo con la notifica: in coda alla
    // suite, con l'orologio fermo, senza di lei il vassoio restava col solo chip del modello.
    Snapshot.sendApplyNotifications()
    advance(1_500)
    // Il vassoio vero nell'albero non unito: in quello unito il raggio di scorrimento sale al campo (un tasto che unisce i
    // figli), che è alto quanto la riga del testo, mentre il vassoio cresce sopra di lui.
    val tray = compose.onNode(
      SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyDescendant(pdf),
      useUnmergedTree = true,
    )
    val range = tray.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
    assertTrue("$width: il vassoio è più lungo di quanto si vede (${range.maxValue()})", range.maxValue() > 0f)
    val inside = tray.getUnclippedBoundsInRoot()
    val tile = compose.onNode(pdf, useUnmergedTree = true).getUnclippedBoundsInRoot()
    assertTrue("$width: il PDF con la sua X sta dentro il vassoio: $tile in $inside",
      tile.left >= inside.left - Tolerance && tile.right <= inside.right + Tolerance &&
        tile.top >= inside.top - Tolerance && tile.bottom <= inside.bottom + Tolerance)
    assertTrue("$width: il vassoio è scorso fino al PDF (${range.value()})", range.value() > 0f)
  }

  @Test @Config(qualifiers = "w360dp-h780dp-xhdpi") fun `a 360 dp il PDF appena aggiunto si vede con la sua X`() = checkTray("360")

  @Test @Config(qualifiers = "w412dp-h915dp-xhdpi") fun `a 412 dp il PDF appena aggiunto si vede con la sua X`() = checkTray("412")

  private companion object {
    /** Mezzo dp: gli arrotondamenti dei pixel. */
    val Tolerance = androidx.compose.ui.unit.Dp(0.5f)
  }
}
