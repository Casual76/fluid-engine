package dev.antigravity.fluidengine.sample.novita

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.diagnostics.FluidDiagnostics
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsConfig
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsDataPage
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsTags
import dev.antigravity.fluidengine.diagnostics.testing.FluidDiagnosticsTesting
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * R3 · La pagina «Dati di diagnostica» dopo la review R3 (difetto 22): mentre legge la coda non dice «Niente da mandare»,
 * e i rapporti sono una riga della lista l'uno, composti solo quando si vedono.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p24DataPageR3Test {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var app: Application

  @Before fun start() {
    app = ApplicationProvider.getApplicationContext()
    FluidDiagnosticsTesting.reset()
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() = FluidDiagnosticsTesting.reset()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun page(diagnostics: FluidDiagnostics) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            FluidDiagnosticsDataPage(diagnostics, onBack = {})
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
  }

  private fun diagnosticsWith(crashes: Int): FluidDiagnostics {
    val diagnostics = FluidDiagnosticsTesting.create(app, FluidDiagnosticsConfig("Fluid Glass"))
    repeat(crashes) { FluidDiagnosticsTesting.recordCrash(diagnostics, IllegalStateException("prova $it")) }
    assertEquals(crashes, diagnostics.queue.records().size)
    return diagnostics
  }

  private fun emptyShown() = compose.onAllNodesWithTag(FluidDiagnosticsTags.Empty).fetchSemanticsNodes().isNotEmpty()

  private val recordRow = SemanticsMatcher("una riga di rapporto") {
    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(FluidDiagnosticsTags.record("")) == true
  }

  private fun composedRows() = compose.onAllNodes(recordRow, useUnmergedTree = true).fetchSemanticsNodes().size

  @Test
  fun `mentre legge la coda la pagina non dice che e' vuota`() {
    page(diagnosticsWith(3))
    // Il primo fotogramma, prima che la lettura su IO torni: prima diceva «Niente da mandare».
    assertTrue("al primo fotogramma la pagina non deve dirsi vuota", !emptyShown())
    advance(600)
    assertTrue(!emptyShown())
    assertEquals(3, composedRows())
  }

  @Test
  fun `una coda vuota dice che e' vuota, quando lo sa`() {
    page(FluidDiagnosticsTesting.create(app, FluidDiagnosticsConfig("Fluid Glass")))
    advance(600)
    assertTrue(emptyShown())
  }

  @Test
  fun `i rapporti si compongono quando si vedono, non tutti in un colpo`() {
    page(diagnosticsWith(60))
    advance(800)
    val rows = composedRows()
    // Su uno schermo da 900 dp ci stanno una decina di righe: prima c'erano tutte e 60 in un elemento solo.
    assertTrue("righe composte: $rows", rows < 40)
  }
}
