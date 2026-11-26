package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.editor.FluidImageEditorTags
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
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
 * F8.15 · Le scene della galleria dal vero: l'album apre l'editor dalla foto e «Fine» salva una copia nella cache (il file
 * di partenza, una risorsa della galleria, resta); il visore dei file ha «Modifica» nella capsula e il suo «Fine» salva;
 * la tendina si apre col suo cursore per TalkBack. La foto e il salvataggio girano su fili veri: si aspetta in tempo vero.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class F8p15ScenesUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val saved get() = File(compose.activity.cacheDir, "f8p15/modifica-0.jpg")

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
    File(compose.activity.cacheDir, "f8p15").deleteRecursively()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  /** Aspetta [condition] in tempo vero (fino a 20 s), facendo girare l'orologio e il looper. */
  private fun await(what: String, condition: () -> Boolean) {
    val until = System.currentTimeMillis() + 20_000
    while (!condition() && System.currentTimeMillis() < until) {
      advance(32)
      Thread.sleep(5)
    }
    assertTrue("non e' arrivato: $what", condition())
  }

  private fun exists(tag: String) =
    compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun enabled(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
    .any { !it.config.contains(SemanticsProperties.Disabled) }

  private fun click(tag: String) {
    compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
    advance(300)
  }

  private fun show(scene: String) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { NewElementsPageAt(onBack = {}, initial = scene) }
    }
    advance(600)
  }

  @Test
  fun lAlbumApreLEditorEFineSalvaUnaCopia() {
    show("modifica-foto")
    await("la foto dell'album") { exists(F8p15Tags.photo(0)) }
    click(F8p15Tags.photo(0))
    await("l'editor pronto") { enabled(FluidImageEditorTags.Done) }
    click(FluidImageEditorTags.item("auto"))
    click(FluidImageEditorTags.Done)
    await("la copia salvata e l'album") { saved.exists() && exists(F8p15Tags.Result) }
    assertTrue(saved.length() > 1000)
    // «Riapri la modifica» rimette la ricetta: «Auto» e' ancora acceso.
    click(F8p15Tags.Reopen)
    await("l'editor riaperto") { enabled(FluidImageEditorTags.Done) }
    val auto = compose.onAllNodes(hasTestTag(FluidImageEditorTags.item("auto")), useUnmergedTree = true)
      .fetchSemanticsNodes().single()
    assertTrue(auto.config.getOrNull(SemanticsProperties.StateDescription) == "Auto acceso")
  }

  @Test
  fun ilVisoreHaModificaEIlSuoFineSalva() {
    show("visore-modifica")
    await("lo strumento nella capsula") {
      compose.onAllNodes(hasContentDescription("Modifica") and hasClickAction(), useUnmergedTree = false)
        .fetchSemanticsNodes().isNotEmpty()
    }
    compose.onAllNodes(hasContentDescription("Modifica") and hasClickAction())[0]
      .performSemanticsAction(SemanticsActions.OnClick)
    advance(400)
    await("l'editor nel visore") { exists(FluidImageEditorTags.Strip) && exists(FluidImageEditorTags.item("auto")) }
    click(FluidImageEditorTags.item("auto"))
    compose.onAllNodes(hasContentDescription("Fine") and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    await("la copia salvata dal visore") { saved.exists() }
  }

  @Test
  fun laTendinaSiApreColSuoCursore() {
    show("confronto-tendina")
    await("la tendina") {
      compose.onAllNodes(hasContentDescription("Confronto prima e dopo"), useUnmergedTree = true).fetchSemanticsNodes()
        .any { it.config.contains(SemanticsActions.SetProgress) }
    }
  }
}
