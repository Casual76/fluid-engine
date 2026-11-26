package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
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
import org.robolectric.annotation.GraphicsMode

/**
 * F8.15c · L'aspetto della ghiera a righello resta lo stesso, al bit. Le scene della galleria che la usano (F8.2,
 * «Raddrizza una foto» e «Velocita' e decibel») e alcune ghiere sole (spenta, da destra a sinistra, senza scritta, al
 * bordo), in chiaro e in scuro, catturate con PixelCopy in `sample/build/f8p15c-righello/`, con l'impronta SHA-256 dei
 * pixel in `impronte.txt` (tutta la finestra) e in `ghiere.txt` (il riquadro di ogni ghiera). Le impronte delle ghiere
 * si confrontano con quelle della ghiera di prima (F8.15b) e devono essere uguali; quelle di tutta la finestra no,
 * perche' il vetro delle scene cambia di qualche livello da una corsa all'altra. La prova da sola controlla solo che
 * ogni cattura riesca; il confronto si fa fra le due corse (vedi il rapporto di F8.15c).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p15cRulerPixelsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val dir = File("build/f8p15c-righello")

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() = FluidGlassDiagnostics.resetForTesting()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun show(dark: Boolean, rtl: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidGlassModalHostState provides host,
          LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(1_500)
  }

  private fun capture(name: String) {
    advance(1_600)
    val window = compose.activity.window
    val view = window.decorView
    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    dir.mkdirs()
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val pixels = IntArray(image.width * image.height)
    image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
    val digest = MessageDigest.getInstance("SHA-256")
    val row = java.nio.ByteBuffer.allocate(pixels.size * 4)
    row.asIntBuffer().put(pixels)
    val hex = digest.digest(row.array()).joinToString("") { "%02x".format(it) }
    File(dir, "impronte.txt").appendText("$name ${image.width}x${image.height} $hex\n")
    // Ogni ghiera (scritta e tacche) da sola: il vetro delle scene (il tasto indietro, l'ombra del selettore) in
    // Robolectric cambia di qualche livello da una corsa all'altra, la ghiera no.
    val dials = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true)
      .fetchSemanticsNodes()
    assertTrue("nessuna ghiera in $name", dials.isNotEmpty())
    dials.forEachIndexed { i, node ->
      val r = node.boundsInWindow
      val left = floor(r.left).toInt().coerceAtLeast(0)
      val top = floor(r.top).toInt().coerceAtLeast(0)
      val right = ceil(r.right).toInt().coerceAtMost(image.width)
      val bottom = ceil(r.bottom).toInt().coerceAtMost(image.height)
      val part = IntArray((right - left) * (bottom - top))
      image.getPixels(part, 0, right - left, left, top, right - left, bottom - top)
      val bytes = java.nio.ByteBuffer.allocate(part.size * 4)
      bytes.asIntBuffer().put(part)
      val h = MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
      File(dir, "ghiere.txt").appendText("$name#$i $left $top $right $bottom $h\n")
    }
  }

  private fun scene(id: String, dark: Boolean) {
    val s = F8p2Section.scenes.first { it.id == id }
    show(dark) { s.content({}) }
  }

  private fun setDial(value: Float) {
    compose.onNodeWithTag(F8p2Tags.Dial, useUnmergedTree = true).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    Snapshot.sendApplyNotifications()
    advance(1_500)
  }

  @Test fun raddrizzaInChiaro() {
    scene("ghiera-raddrizza", dark = false)
    capture("raddrizza-chiaro-zero")
    setDial(12f)
    capture("raddrizza-chiaro-12")
    setDial(-45f)
    capture("raddrizza-chiaro-bordo")
  }

  @Test fun raddrizzaInScuro() {
    scene("ghiera-raddrizza", dark = true)
    capture("raddrizza-scuro-zero")
    setDial(-7f)
    capture("raddrizza-scuro-meno7")
  }

  @Test fun velocitaEDecibel() {
    scene("ghiera-valori", dark = false)
    capture("valori-chiaro-neutro")
    setDial(1.5f)
    capture("valori-chiaro-1p5")
  }

  /** Ghiere sole: spenta, senza scritta, con l'intervallo stretto, e da destra a sinistra. */
  private fun alone(dark: Boolean, rtl: Boolean) = show(dark, rtl) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
      FluidRulerDial(value = -20f, onValueChange = {}, contentDescription = "Uno", modifier = Modifier.testTag("uno"))
      FluidRulerDial(value = 5f, onValueChange = {}, enabled = false, contentDescription = "Spenta")
      FluidRulerDial(value = 7f, onValueChange = {}, valueText = null, contentDescription = "Senza scritta")
      FluidRulerDial(
        value = 0.25f, onValueChange = {}, valueRange = 0f..1f, step = 0.05f, majorEvery = 5, neutralValue = null,
        valueText = { "%.2f".format(Locale.ITALY, it) }, contentDescription = "Stretta",
      )
      FluidRulerDial(value = 45f, onValueChange = {}, contentDescription = "Al bordo")
    }
  }

  @Test fun ghiereSoleInChiaro() {
    alone(dark = false, rtl = false)
    capture("sole-chiaro")
  }

  @Test fun ghiereSoleInScuro() {
    alone(dark = true, rtl = false)
    capture("sole-scuro")
  }

  @Test fun ghiereSoleDaDestra() {
    alone(dark = false, rtl = true)
    capture("sole-da-destra")
  }
}
