package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.RenderNode
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidFilter
import dev.antigravity.fluidengine.ui.fluid.FluidFilterChips
import dev.antigravity.fluidengine.ui.fluid.FluidFilterSelection
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
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

/**
 * F6B.8, le riprese per Alessio: la fila di filtri vera (l'entry point di produzione, PixelCopy della finestra come in
 * F6B.3b), in chiaro, a 8 ms per fotogramma (~120 Hz). Prima a testo 100% (la fila intera), poi a 200% (la fila si
 * raccoglie in «Filtri · 2»), il tocco che apre il menu dal vetro del tasto, una voce che si accende senza chiudere il
 * menu, e il congedo. I fotogrammi vanno in `build/reports/f6b-8/fotogrammi/`, con la cronologia; il video lo monta
 * `ffmpeg` (rapporto della sessione).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h560dp-xhdpi")
class F6b8FilterFramesTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val filters = listOf(
    FluidFilter("aperti", "Aperti ora"),
    FluidFilter("accessibile", "Accessibile"),
    FluidFilter("wifi", "Wi-Fi"),
  )
  private var selection by mutableStateOf(FluidFilterSelection.of("accessibile", "wifi"))
  private var fontScale by mutableFloatStateOf(1f)
  private val out = File("build/reports/f6b-8/fotogrammi")
  private val view get() = compose.activity.window.decorView

  private fun frame() {
    compose.mainClock.advanceTimeBy(8)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(8))
    Snapshot.sendApplyNotifications()
  }

  private fun window(): Bitmap {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, view.width, view.height),
        bitmap, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    return bitmap
  }

  @Test fun `la fila che a testo grande diventa Filtri e apre il menu`() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
        val density = LocalDensity.current
        val backdrop = rememberGlassBackdrop()
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalDensity provides Density(density.density, fontScale),
          LocalFluidGlassModalHostState provides host,
          LocalGlassBackdrop provides backdrop,
          LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) {
          val colors = MaterialTheme.colorScheme
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(backdrop)
              .background(Brush.verticalGradient(listOf(colors.surface, colors.primaryContainer, colors.tertiaryContainer))))
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Box(Modifier.fillMaxWidth().height(44.dp).background(colors.onSurface.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                Text("Cerca un luogo", color = colors.onSurfaceVariant)
              }
              FluidFilterChips(filters = filters, selection = selection, onChange = { selection = it })
              repeat(5) { i ->
                Row(Modifier.fillMaxWidth().height(56.dp).background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                  .padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                  Text(listOf("Caffè Dante", "Biblioteca delle Oblate", "Mercato Centrale", "Giardino dei Semplici", "Teatro della Pergola")[i])
                }
              }
            }
            FluidGlassModalHost(state = host, backdrop = backdrop)
          }
        }
      }
    }
    repeat(20) { frame() }
    out.mkdirs()
    val node = RenderNode("F6B.8")
    node.setPosition(0, 0, view.width, view.height)
    fun draw() = compose.runOnUiThread {
      val canvas = node.beginRecording()
      try { view.draw(canvas) } finally { node.endRecording() }
    }
    var index = 0
    val log = StringBuilder("frame,ms,fase,scala,catture\n")
    val start = FluidGlassDiagnostics.totalCaptures
    fun shoot(phase: String, count: Int) {
      repeat(count) {
        frame()
        draw()
        val shot = window()
        File(out, "f%04d.png".format(index)).outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shot.recycle()
        log.append("$index,${index * 8},$phase,$fontScale,${FluidGlassDiagnostics.totalCaptures - start}\n")
        index++
      }
    }
    try {
      shoot("fila-100", 50)
      assertTrue("a 100% la fila e' intera", compose.onAllNodesWithText("Filtri", substring = true).fetchSemanticsNodes().isEmpty())
      compose.runOnUiThread { fontScale = 2f }
      shoot("fila-200", 60)
      val key = compose.onAllNodesWithText("Filtri", substring = true).fetchSemanticsNodes()
      assertTrue("a 200% la fila e' il tasto", key.isNotEmpty())
      // Una pillola che la fila non mostra, spenta se ce n'e' una: e' nel menu.
      val hidden = filters.filter { compose.onAllNodesWithText(it.label).fetchSemanticsNodes().isEmpty() }
      val pick = hidden.firstOrNull { !selection.isSelected(it.id) } ?: hidden.first()
      val before = selection.isSelected(pick.id)
      compose.onNodeWithText(key.first().config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text).performClick()
      shoot("menu-apre", 90)
      compose.onNodeWithText(pick.label).performClick()
      shoot("voce-accesa", 50)
      assertTrue("la voce ha cambiato il filtro, a menu aperto", selection.isSelected(pick.id) != before)
      log.append("# voce scelta nel menu: ${pick.label}").append('\n')
      compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
      shoot("congedo", 70)
    } finally {
      node.discardDisplayList()
      File(out, "cronologia.csv").writeText(log.toString())
    }
    println("F6B.8: $index fotogrammi in ${out.absolutePath}")
  }
}
