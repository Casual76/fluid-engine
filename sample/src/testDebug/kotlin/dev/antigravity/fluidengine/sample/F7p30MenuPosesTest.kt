@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.measure.MeasureScenario
import dev.antigravity.fluidengine.sample.measure.ScenarioActions
import dev.antigravity.fluidengine.sample.measure.ScenarioScene
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMenuReadiness
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.SharedRuntimeShaders
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/**
 * F7.30, il menu tenuto pronto (`FluidMenuReadiness`): **l'aspetto non cambia**. Lo scenario della bench
 * (`menu-gruppo-barra`: il gruppo in basso del palco della galleria che diventa menu e torna), nel formato telefono, con
 * **due aperture** per corsa: la prima (che col menu tenuto pronto trova il corpo preparato alla pressione) e quella a
 * caldo (che lo trova parcheggiato dalla prima). Le finestre lette con PixelCopy agli stessi istanti devono essere uguali
 * al bit col menu tenuto pronto spento (il codice di F7.29) e acceso, in chiaro e in scuro.
 *
 * Il braccio acceso corre fra due corse del braccio spento, e ogni sua foto deve essere uguale al bit ad almeno una delle
 * due: due corse uguali dello stesso processo possono differire di 1/255 in qualche centinaio di pixel (la deriva del
 * banco di F7.27, che cresce dopo altre prove nella stessa JVM). Un cambio vero sarebbe diverso da tutte e due; una foto
 * che dista da tutte e due al più quanto le basi distano fra loro (mai oltre 1/255) è la stessa deriva. Densità
 * `mdpi`: gli stessi percorsi di codice del telefono, meno pixel da rendere. Il vetro è quello vero (verdetti degli shader
 * accettati); i corpi a otto pezzi, che il compilatore di Robolectric rifiuta, girano coi cicli accorciati come in
 * `GlassCostScenarioTest`, uguali nei due bracci.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [35],
  qualifiers = "w411dp-h891dp-mdpi",
  application = GlassBenchDevice::class,
  shadows = [GlassBenchHeap::class],
)
class F7p30MenuPosesTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** I fotogrammi (da 16 ms) dell'apertura e della chiusura in cui si legge la finestra. */
  private val openShots = listOf(1, 2, 3, 4, 6, 9, 12, 20, 28, 38, 50, 62)
  private val closeShots = listOf(4, 12, 24, 44)

  @Test fun chiaro() = compare(ThemeMode.LIGHT)

  @Test fun scuro() = compare(ThemeMode.DARK)

  private fun compare(theme: ThemeMode) = withLargeBodies {
    val diagnostics = FluidGlassDiagnostics
    val enabled = diagnostics.enabled
    val warm = diagnostics.warmUpEnabled
    val readiness = FluidMenuReadiness.enabled
    diagnostics.resetForTesting()
    diagnostics.enabled = true
    diagnostics.warmUpEnabled = false
    diagnostics.forceShaderVerdictsForTesting(true)
    val run = mutableIntStateOf(0)
    var actions = ScenarioActions()
    lateinit var ui: CoroutineScope
    try {
      compose.mainClock.autoAdvance = false
      compose.setContent {
        key(run.intValue) {
          val current = remember { ScenarioActions().also { actions = it } }
          FluidTheme(settings = remember { EngineSettings(themeMode = theme, dynamicColorEnabled = false) }) {
            ui = rememberCoroutineScope()
            val chrome = rememberFluidChromeController()
            val host = rememberFluidGlassModalHostState()
            val fallback = rememberGlassBackdrop()
            CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
              ProvideFluidChrome(chrome, 0.dp, remember { FluidScrollToTopBus() }) {
                Box(Modifier.fillMaxSize()) {
                  Box(Modifier.fillMaxSize().fluidChromePages()) { ScenarioScene(MeasureScenario.MenuGroupBar, current, 0.dp) }
                  FluidGlassModalHost(host, chrome.activeBackdrop.value ?: fallback)
                }
              }
            }
          }
        }
      }
      fun frame() {
        compose.mainClock.advanceTimeByFrame()
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(8))
        Snapshot.sendApplyNotifications()
      }
      fun cycle(shots: MutableList<IntArray>) {
        compose.runOnUiThread { ui.launch { checkNotNull(actions.action("apre")).invoke() } }
        for (index in 0..openShots.last()) {
          frame()
          if (index in openShots) shots += window()
        }
        compose.runOnUiThread { ui.launch { checkNotNull(actions.action("chiude")).invoke() } }
        for (index in 0..closeShots.last()) {
          frame()
          if (index in closeShots) shots += window()
        }
        // L'uscita finisce del tutto (col menu tenuto pronto: si parcheggia) prima dell'apertura dopo.
        repeat(40) { frame() }
        shots += window()
      }
      // Un braccio: pagina nuova, quiete, due aperture e chiusure fotografate agli stessi istanti.
      fun arm(ready: Boolean): List<IntArray> {
        FluidMenuReadiness.enabled = ready
        compose.runOnIdle { run.intValue++ }
        repeat(45) { frame() }
        assertTrue("scena pronta", actions.ready)
        val shots = ArrayList<IntArray>()
        shots += window()
        cycle(shots)
        cycle(shots)
        assertNull("gesto", actions.failure)
        return shots
      }
      fun differences(a: List<IntArray>, b: List<IntArray>): List<String> = a.indices.mapNotNull { shot ->
        val x = a[shot]
        val y = b[shot]
        var count = 0
        var maxDelta = 0
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        for (i in x.indices) {
          if (x[i] == y[i]) continue
          count++
          val px = i % width; val py = i / width
          minX = minOf(minX, px); maxX = maxOf(maxX, px); minY = minOf(minY, py); maxY = maxOf(maxY, py)
          for (shift in intArrayOf(0, 8, 16, 24)) {
            maxDelta = maxOf(maxDelta, kotlin.math.abs(((x[i] ushr shift) and 0xff) - ((y[i] ushr shift) and 0xff)))
          }
        }
        if (count == 0) null else "foto $shot: $count pixel, scarto massimo $maxDelta/255, riquadro ($minX,$minY)-($maxX,$maxY)"
      }
      val base1 = arm(ready = false)
      val ready1 = arm(ready = true)
      val base2 = arm(ready = false)
      val ready2 = arm(ready = true)
      val base3 = arm(ready = false)
      // Il controllo positivo: l'apertura si vede (la finestra cambia).
      assertFalse("l'apertura deve cambiare la finestra", base1.first().contentEquals(base1[8]))
      println("F730 PIXEL $theme base1 contro base2: ${differences(base1, base2).ifEmpty { listOf("uguali") }.joinToString(" | ")}")
      println("F730 PIXEL $theme base2 contro base3: ${differences(base2, base3).ifEmpty { listOf("uguali") }.joinToString(" | ")}")
      // La deriva del banco: lo scarto più grande fra due corse della base di questo tema (in scuro 1/255, F7.27 e F7.30).
      val drift = maxOf(maxDelta(base1, base2), maxDelta(base2, base3))
      println("F730 PIXEL $theme deriva fra le basi: $drift/255")
      fun check(label: String, shots: List<IntArray>, before: List<IntArray>, after: List<IntArray>) {
        // Una foto è spiegata se è uguale al bit a una delle due basi vicine, o se ne dista al più quanto le basi distano
        // fra loro (la deriva, mai oltre 1/255): un cambio vero sarebbe più lontano.
        val unexplained = shots.indices.filter { shot ->
          !shots[shot].contentEquals(before[shot]) && !shots[shot].contentEquals(after[shot]) &&
            minOf(maxDelta(shots[shot], before[shot]), maxDelta(shots[shot], after[shot])) > minOf(drift, 1)
        }
        val vsBefore = differences(before, shots)
        println("F730 PIXEL $theme $label: ${if (vsBefore.isEmpty()) "uguali al bit alla base di prima (${shots.size} foto)" else "contro la base di prima " + vsBefore.joinToString(" | ")}" +
          (if (unexplained.isEmpty()) "; ogni foto uguale al bit a una delle due basi" else "; foto diverse da tutte e due le basi: $unexplained"))
        assertTrue("$theme, $label: foto diverse da tutte e due le basi vicine: $unexplained " +
          "(${differences(before, shots)} / ${differences(after, shots)})", unexplained.isEmpty())
      }
      check("pronto 1", ready1, base1, base2)
      check("pronto 2", ready2, base2, base3)
    } finally {
      FluidMenuReadiness.enabled = readiness
      diagnostics.enabled = enabled
      diagnostics.warmUpEnabled = warm
      diagnostics.resetForTesting()
    }
  }

  private var width = 0

  /** Lo scarto più grande, in livelli di un canale, fra due foto. */
  private fun maxDelta(x: IntArray, y: IntArray): Int {
    var delta = 0
    for (i in x.indices) {
      if (x[i] == y[i]) continue
      for (shift in intArrayOf(0, 8, 16, 24)) {
        delta = maxOf(delta, kotlin.math.abs(((x[i] ushr shift) and 0xff) - ((y[i] ushr shift) and 0xff)))
      }
    }
    return delta
  }

  /** Lo scarto più grande fra due corse, foto per foto. */
  private fun maxDelta(a: List<IntArray>, b: List<IntArray>): Int = a.indices.maxOf { maxDelta(a[it], b[it]) }

  private fun window(): IntArray {
    val view = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, view.width, view.height),
        bitmap, { result = it }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy della finestra", PixelCopy.SUCCESS, result)
    width = bitmap.width
    return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
  }

  /** Il compilatore Skia di Robolectric rifiuta i corpi a otto pezzi: cicli accorciati, uguali nei due bracci. */
  private fun withLargeBodies(block: () -> Unit) {
    val cache = ReflectionHelpers.getStaticField<MutableMap<String, RuntimeShader>>(SharedRuntimeShaders::class.java, "shaders")
    val installed = mutableListOf<String>()
    try {
      for (source in listOf(GlassShaderSources.Body8, GlassShaderSources.BodyFilms8)) {
        for (capacity in 8 downTo 1) {
          val program = source.text.replace("i < 8; ++i", "i < $capacity; ++i")
          try {
            val shader = RuntimeShader(program)
            if (capacity < 8 && cache.putIfAbsent(source.text, shader) == null) installed += source.text
            break
          } catch (error: IllegalArgumentException) {
            if (capacity == 1 || error.message?.contains("program is too large") != true) throw error
          }
        }
      }
      block()
    } finally {
      installed.forEach(cache::remove)
    }
  }
}
