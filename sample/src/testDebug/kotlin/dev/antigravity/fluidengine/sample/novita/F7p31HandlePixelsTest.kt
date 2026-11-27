package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidRangeSlider
import dev.antigravity.fluidengine.ui.fluid.FluidScrubber
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
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
 * F7.31 · L'aspetto delle maniglie di vetro non cambia di un bit: il cursore, il cursore a due maniglie, lo scrubber e
 * l'interruttore, sopra una pagina a righe colorate che la lente piega, fotografati (PixelCopy) a riposo, col dito giù a
 * metà corsa (la lente cresciuta, che campiona la pista e la pagina) e dopo il rilascio, da sinistra a destra e da destra
 * a sinistra.
 *
 * Non ha un'attesa scritta: stampa l'impronta di ogni foto (`F7p31PIX | nome | sha256`) e salva le foto in
 * `build/f7p31-pixels/`. Si fa girare sul codice di prima e su quello di adesso, la classe da sola (le impronte del vetro
 * dipendono da cosa ha già girato nella JVM), e si confrontano le righe: devono essere le stesse.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F7p31HandlePixelsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: android.view.View

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
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

  private fun capture(name: String) {
    val window = compose.activity.window
    // Robolectric disegna la finestra alla PixelCopy: due di fila, la seconda e' quella che conta (la prima puo' aspettare
    // la lettura della memoria del vetro).
    repeat(2) {
      val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
      var result = -1
      compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
      if (it == 1) {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = java.nio.ByteBuffer.allocate(pixels.size * 4)
        bytes.asIntBuffer().put(pixels)
        val hash = digest.digest(bytes.array()).joinToString("") { b -> "%02x".format(b) }
        println("F7p31PIX | $name | $hash")
        val dir = File("build/f7p31-pixels").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out -> image.compress(Bitmap.CompressFormat.PNG, 100, out) }
      }
    }
  }

  private var slider by mutableFloatStateOf(0.3f)
  private var range by mutableStateOf(0.2f..0.7f)
  private var position by mutableFloatStateOf(15_000f)
  private var checked by mutableStateOf(false)

  private fun show(rtl: Boolean) {
    compose.setContent {
      view = LocalView.current
      CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        FluidTheme(settings = remember { EngineSettings() }) {
          val page = rememberGlassBackdrop()
          Box(Modifier.fillMaxSize()) {
            // La pagina: righe colorate e del testo, che la lente piega e ingrandisce.
            Column(
              Modifier
                .fillMaxSize()
                .glassBackdropSource(page)
                .drawBehind {
                  val colors = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFFFDD835), Color(0xFF43A047))
                  var x = 0f
                  var i = 0
                  while (x < size.width) {
                    drawRect(colors[i % colors.size], topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(9f, size.height))
                    x += 9f
                    i++
                  }
                },
            ) {
              repeat(12) { Text("Maniglie di vetro $it", fontSize = 13.sp, modifier = Modifier.background(Color.White.copy(alpha = 0.5f))) }
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
              Spacer(Modifier.height(80.dp))
              FluidSlider(value = slider, onValueChange = { slider = it }, backdrop = page, modifier = Modifier.fillMaxWidth().testTag("cursore"))
              Spacer(Modifier.height(40.dp))
              FluidRangeSlider(value = range, onValueChange = { range = it }, backdrop = page, modifier = Modifier.fillMaxWidth().testTag("intervallo"))
              Spacer(Modifier.height(40.dp))
              FluidScrubber(position = { position }, onSeek = { position = it }, valueRange = 0f..60_000f, backdrop = page, modifier = Modifier.fillMaxWidth().testTag("scrubber"))
              Spacer(Modifier.height(40.dp))
              FluidSwitch(checked = checked, onCheckedChange = { checked = it }, backdrop = page, modifier = Modifier.testTag("interruttore"))
              Spacer(Modifier.size(1.dp))
            }
          }
        }
      }
    }
    advance(600)
  }

  /** Il dito giu' a [fromX] della larghezza di [tag], trascinato di [dx] in [steps] passi; foto a meta' e dopo. */
  private fun drag(tag: String, fromX: Float, dx: Float, steps: Int, name: String) {
    val b = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val finger = MaterialFinger(view)
    var at = Offset(b.left + b.width * fromX, b.center.y)
    finger.down(at)
    advance(16)
    repeat(steps) {
      at += Offset(dx / steps, 0f)
      finger.move(at)
      advance(16)
    }
    // Fermo, col dito giu': la lente cresciuta e posata.
    advance(400)
    capture("$name-dito")
    finger.up(at)
    advance(900)
    capture("$name-rilascio")
  }

  private fun scenes(prefix: String, rtl: Boolean) {
    show(rtl)
    capture("$prefix-riposo")
    val s = if (rtl) -1f else 1f
    drag("cursore", if (rtl) 0.7f else 0.3f, 90f * s, 20, "$prefix-cursore")
    drag("intervallo", if (rtl) 0.3f else 0.7f, -60f * s, 20, "$prefix-intervallo")
    drag("scrubber", if (rtl) 0.75f else 0.25f, 120f * s, 20, "$prefix-scrubber")
    drag("interruttore", if (rtl) 0.75f else 0.25f, 50f * s, 20, "$prefix-interruttore")
  }

  @Test
  fun `le maniglie da sinistra a destra`() = scenes("ltr", rtl = false)

  @Test
  fun `le maniglie da destra a sinistra`() = scenes("rtl", rtl = true)
}
