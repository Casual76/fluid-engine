package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareFiles
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareItem
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareSheet
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareSheetTags
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
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
import java.io.File

/**
 * F8.10 · La miniatura in cima al foglio di condivisione, coi pixel veri (il tablet il 25 novembre: vuota in ogni scena).
 *
 * Senza immagine la miniatura mostra l'icona del tipo (un link: la catena nell'accento); con un'immagine pronta
 * ([FluidShareItem.preview]) mostra l'immagine. La causa del vuoto era in `FluidGlassContentTransition`, che saltava uno
 * stato null anche quando la sua chiave («tipo») c'era. La finestra si cattura con `PixelCopy`, come le prove di F8.1;
 * i verdetti degli shader sono rifiutati, perche' cosi' la scena col vetro si cattura intera.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p10ThumbnailPixelsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
    FluidShareFiles.overrideUrisForTesting(null)
    File(compose.activity.filesDir, "f8-10-prove").deleteRecursively()
  }

  @Composable
  private fun Hosted(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
        Box(Modifier.fillMaxSize()) {
          content()
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  /** I pixel della miniatura, dalla finestra intera. */
  private fun thumbnail(): IntArray {
    compose.waitUntil(5_000) {
      compose.onAllNodes(androidx.compose.ui.test.hasTestTag(FluidShareSheetTags.Thumbnail)).fetchSemanticsNodes().isNotEmpty()
    }
    compose.waitForIdle()
    val bounds = compose.onNodeWithTag(FluidShareSheetTags.Thumbnail).fetchSemanticsNode().boundsInWindow
    val window = compose.activity.window
    val view = window.decorView
    val node = android.graphics.RenderNode("F8.10")
    node.setPosition(0, 0, view.width, view.height)
    val canvas = node.beginRecording()
    try { view.draw(canvas) } finally { node.endRecording() }
    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    // Il bordo arrotondato fuori: il quadrato al centro, un ottavo per lato.
    val inset = (bounds.width / 8f).toInt()
    val left = bounds.left.toInt() + inset
    val top = bounds.top.toInt() + inset
    val width = (bounds.width.toInt() - 2 * inset).coerceAtMost(image.width - left)
    val height = (bounds.height.toInt() - 2 * inset).coerceAtMost(image.height - top)
    return IntArray(width * height).also { image.getPixels(it, 0, width, left, top, width, height) }
  }

  /** Quanti pixel hanno colore vero (non grigi): l'icona nell'accento o l'immagine. */
  private fun colorful(pixels: IntArray): Int = pixels.count { c ->
    val r = android.graphics.Color.red(c)
    val g = android.graphics.Color.green(c)
    val b = android.graphics.Color.blue(c)
    maxOf(r, g, b) - minOf(r, g, b) > 60
  }

  @Test fun aLinkShowsTheLinkIconInTheAccent() {
    compose.setContent { Hosted { FluidShareSheet(FluidShareItem.link("https://example.org/app", "Fluid Glass"), onDismissRequest = {}) } }
    val pixels = thumbnail()
    val ink = colorful(pixels)
    assertTrue("l'icona del link nella miniatura: $ink pixel colorati su ${pixels.size}", ink > pixels.size / 40)
  }

  private fun reds(pixels: IntArray): Int = pixels.count { c ->
    android.graphics.Color.red(c) > 170 && android.graphics.Color.green(c) < 90 && android.graphics.Color.blue(c) < 90
  }

  @Test fun aPhotoIsReadFromItsFileIntoTheThumbnail() {
    // La seconda causa del vuoto: la lettura delle misure (`inJustDecodeBounds`) torna sempre null, e un `?: return null`
    // su di lei faceva uscire la miniatura di ogni Uri. La foto sta fuori dalla cartella dell'engine: si copia (Uri
    // finto, il FileProvider vero sul JVM di Windows non va), e la miniatura si legge dal file al suo posto.
    FluidShareFiles.overrideUrisForTesting { file -> Uri.parse("content://prova.fluid.share/${file.parentFile!!.name}/${file.name}") }
    val file = File(compose.activity.filesDir, "f8-10-prove/Rosso.png").apply { parentFile!!.mkdirs() }
    val red = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(220, 30, 30)) }
    file.outputStream().use { red.compress(Bitmap.CompressFormat.PNG, 100, it) }
    compose.setContent { Hosted { FluidShareSheet(FluidShareItem.file(compose.activity, file, "image/png"), onDismissRequest = {}) } }
    // La lettura e' su un thread di I/O, fuori dall'attesa di Compose: si riguarda finche' arriva.
    var last = 0
    var size = 1
    for (attempt in 0 until 40) {
      val pixels = thumbnail()
      size = pixels.size
      last = reds(pixels)
      if (last > size / 2) break
      Thread.sleep(100)
    }
    assertTrue("la foto nella miniatura: $last pixel rossi su $size", last > size / 2)
  }

  @Test fun aReadyPreviewIsShown() {
    val red = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(220, 30, 30)) }
    val item = FluidShareItem.text("Il voto di matematica", title = "Voto").copy(preview = red.asImageBitmap())
    compose.setContent { Hosted { FluidShareSheet(item, onDismissRequest = {}) } }
    val pixels = thumbnail()
    val count = reds(pixels)
    assertTrue("l'immagine nella miniatura: $count pixel rossi su ${pixels.size}", count > pixels.size / 2)
  }
}
