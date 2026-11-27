package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroller
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.security.MessageDigest
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
 * F7.25 · La bolla dello scorrimento rapido fotogramma per fotogramma, al bit: l'entrata quando si prende la maniglia,
 * il trascinamento, l'uscita. La bolla ora è composta prima e spostata nell'`AnimatedVisibility` di sempre: i fotogrammi
 * devono essere gli stessi del codice di prima. La prova stampa le impronte (SHA-256 della striscia a sinistra della
 * maniglia, dalla finestra intera in PixelCopy) e salva le immagini in `build/f7-25-bolla/`. Le impronte dipendono anche
 * da che cosa ha girato prima nella stessa JVM (lo stato del vetro di processo cambia l'ombra della goccia di qualche
 * livello): il confronto al bit con la base si fa con la prova da sola, «in chiaro» per prima (rapporto di F7.25).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class F7p25BubbleFramesTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun window(): Bitmap {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, view.width, view.height), bitmap,
      { result = it }, Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(PixelCopy.SUCCESS, result)
    return bitmap
  }

  private fun sha(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): String {
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, left, top, width, height)
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = java.nio.ByteBuffer.allocate(pixels.size * 4)
    bytes.asIntBuffer().put(pixels)
    return digest.digest(bytes.array()).joinToString("") { "%02x".format(it) }.take(16)
  }

  private fun scene(dark: Boolean) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
        val list = rememberLazyListState()
        val page = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          LazyColumn(state = list, modifier = Modifier.fillMaxSize().glassBackdropSource(page)) {
            items(400) { index ->
              val shade = if (index % 3 == 0) Color(0xFF1E3A5F) else if (index % 3 == 1) Color(0xFFF4E9D8) else Color(0xFF7A9E7E)
              Box(Modifier.fillMaxWidth().height(56.dp).background(shade)) { Text("Riga $index") }
            }
          }
          FluidFastScroller(state = list, label = { "Gruppo ${it / 10}" }, backdrop = page)
        }
      }
    }
    advance(600)
    finger = MaterialFinger(view)
  }

  private fun frames(dark: Boolean): List<String> {
    scene(dark)
    val density = view.resources.displayMetrics.density
    // La lista si muove col dito: la maniglia compare.
    val middle = Offset(view.width / 2f, view.height / 2f)
    finger.down(middle)
    var at = middle
    repeat(8) {
      advance(16)
      at = Offset(at.x, at.y - 14f * density)
      finger.move(at)
    }
    finger.up(at)
    advance(400)
    val strip = compose.onNode(hasContentDescription("Scorrimento rapido"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    var thumb = Offset(strip.right - 18f * density, strip.top + 26f * density + 2f)
    val out = mutableListOf<String>()
    val dir = File("build/f7-25-bolla").apply { mkdirs() }
    fun shot(label: String) {
      val bitmap = window()
      val top = (thumb.y - 40f * density).toInt().coerceIn(0, view.height - 1)
      val height = (80f * density).toInt().coerceAtMost(view.height - top)
      val width = strip.left.toInt()
      val name = "${if (dark) "scuro" else "chiaro"} $label"
      out += "$name ${sha(bitmap, 0, top, width, height)}"
      java.io.FileOutputStream(File(dir, name.replace(' ', '-') + ".png")).use {
        Bitmap.createBitmap(bitmap, 0, top, width, height).compress(Bitmap.CompressFormat.PNG, 100, it)
      }
    }
    finger.down(thumb)
    repeat(10) { advance(16); shot("entrata ${it + 1}") }
    repeat(4) { step ->
      thumb = Offset(thumb.x, thumb.y + 24f * density)
      finger.move(thumb)
      advance(16)
      shot("trascinamento ${step + 1}")
    }
    finger.up(thumb)
    repeat(10) { advance(16); shot("uscita ${it + 1}") }
    return out
  }

  @Test
  fun `la bolla, fotogramma per fotogramma, in chiaro`() = check(frames(dark = false), "chiaro")

  @Test
  fun `la bolla, fotogramma per fotogramma, in scuro`() = check(frames(dark = true), "scuro")

  private fun check(got: List<String>, theme: String) {
    println("F7.25 · bolla $theme:\n" + got.joinToString("\n"))
    assertEquals("24 fotogrammi della bolla in $theme", 24, got.size)
  }
}
