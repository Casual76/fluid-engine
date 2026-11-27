package dev.antigravity.fluidengine.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeThreshold
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Lo stesso tetto di `EngineWidgetGaugeTest`: la prima composizione di Glance col PC carico passa i 2 s di serie. */
private val GlanceUnitTimeout = 30.seconds

/**
 * R2-F5a (difetto 35) · il quadrante sulla Home con le soglie e i loro testi non allineati: l'app passa due liste
 * pubbliche separate, e una piu' corta faceva cadere la composizione del widget (e la Home). Ora il testo che manca
 * e' il numero della soglia.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EngineWidgetGaugeR2Test {

  @Test
  fun `soglie piu' dei loro testi non fanno cadere il widget`() = runGlanceAppWidgetUnitTest(timeout = GlanceUnitTimeout) {
    setContext(RuntimeEnvironment.getApplication())
    setAppWidgetSize(DpSize(180.dp, 180.dp))
    provideComposable {
      EngineWidgetGauge(
        value = 27f,
        range = 0f..100f,
        palette = engineWidgetPalette(RuntimeEnvironment.getApplication(), EngineSettings()),
        layout = resolveEngineWidgetLayout(DpSize(180.dp, 180.dp)),
        valueText = "27 %",
        style = FluidGaugeStyle.Ring,
        label = "Assenze",
        minText = "0",
        maxText = "100 %",
        thresholds = listOf(FluidGaugeThreshold(25f, "Limite"), FluidGaugeThreshold(50f, "Massimo")),
        thresholdTexts = listOf("25 %"),
      )
    }
    onNode(hasText("27 %")).assertExists()
    onNode(hasContentDescription("Massimo 50")).assertExists()
  }
}
