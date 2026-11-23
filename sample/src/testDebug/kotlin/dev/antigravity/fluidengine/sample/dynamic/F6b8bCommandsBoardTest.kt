package dev.antigravity.fluidengine.sample.dynamic

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTextReplacement
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F6B.8b · la tavola «prima e dopo» dei comandi del campo per Alessio (scelta del 23 novembre 2026, «tasti più grandi»):
 * le scene `trova`, `messaggio` e `assistente` della galleria sul telefono (360 dp a 3×, cioè 1080 px), in chiaro e in
 * scuro, col campo aperto e i suoi comandi in vista. La stessa prova gira sul codice di prima (319b7ce) e su quello di
 * F6B.8b; le schermate vanno in `build/reports/f6b-8b/tavola/`. Il vetro è quello di ripiego di Robolectric (niente
 * shader): la tavola confronta le misure, non il materiale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class F6b8bCommandsBoardTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val out = File("build/reports/f6b-8b/tavola")

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

  private fun open(scene: DynamicScene, dark: Boolean) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          Box(Modifier.fillMaxSize()) {
            DynamicElementsPage(onBack = {}, initial = scene)
            FluidGlassModalHost(modals, backdrop = null)
          }
        }
      }
    }
    advance(900)
  }

  private fun clickable(name: String): SemanticsNode = compose.onAllNodes(hasText(name) or hasContentDescription(name))
    .fetchSemanticsNodes().first { SemanticsActions.OnClick in it.config && SemanticsActions.SetText !in it.config }

  private fun tap(name: String) {
    val action = clickable(name).config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(900)
  }

  private fun type(text: String) {
    compose.onNode(hasSetTextAction()).performTextReplacement(text)
    Snapshot.sendApplyNotifications()
    advance(900)
  }

  private fun shot(name: String) {
    advance(600)
    val view = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, view.width, view.height),
        bitmap, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    out.mkdirs()
    File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun find(dark: Boolean) {
    open(DynamicScene.FindInPage, dark)
    tap("Trova")
    type("progetto")
    shot("trova-${if (dark) "scuro" else "chiaro"}")
  }

  private fun message(dark: Boolean) {
    open(DynamicScene.MessageComposer, dark)
    tap("Messaggio")
    tap("Aggiungi un allegato")
    type("Ci vediamo alle otto")
    shot("messaggio-${if (dark) "scuro" else "chiaro"}")
  }

  private fun assistant(dark: Boolean) {
    open(DynamicScene.AssistantComposer, dark)
    tap("Scrivi un messaggio…")
    type("Che cosa manca per il trasloco?")
    shot("assistente-${if (dark) "scuro" else "chiaro"}")
  }

  @Test fun trovaChiaro() = find(false)
  @Test fun trovaScuro() = find(true)
  @Test fun messaggioChiaro() = message(false)
  @Test fun messaggioScuro() = message(true)
  @Test fun assistenteChiaro() = assistant(false)
  @Test fun assistenteScuro() = assistant(true)
}
