package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
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
import org.robolectric.annotation.GraphicsMode

/**
 * F8.10 · la «x» di un foglio nato da un tasto, coi pixel della finestra: a riposo e' nitida anche quando la testata
 * cambia a meta' zoom (il tablet, 25 novembre: la «x» del foglio Condividi restava il disegno di meta' zoom, al 40% e
 * sfocata, quando la miniatura o la riga sotto il nome arrivavano mentre il foglio nasceva dal tasto Condividi).
 *
 * Il foglio e' un [FluidGlassSheet] qualunque, non quello di condivisione: la correzione e' nello zoom dei fogli. La
 * misura e' il pixel piu' scuro al centro della lente della «x»: il glifo nitido e' nero, il disegno di meta' zoom e' una
 * macchia grigia. Coi verdetti degli shader rifiutati (il vetro di ripiego), come le altre prove coi pixel; le immagini
 * finiscono in `sample/build/f8-10-zoom-x/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p10SheetZoomHeaderTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var open by mutableStateOf(false)
  private var header by mutableStateOf("1 foto")

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() = FluidGlassDiagnostics.resetForTesting()

  /**
   * Un passo di 16 ms alla volta, **col disegno della finestra a ogni passo**, come sul dispositivo: Robolectric da solo
   * non disegna fra due catture, e il vetro deciderebbe tutto al primo disegno a riposo.
   */
  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      drawFrame()
      left -= 16
    }
  }

  private var frame: Bitmap? = null

  private fun drawFrame() {
    val window = compose.activity.window
    val view = window.decorView
    if (view.width <= 0 || view.height <= 0) return
    val image = frame ?: Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { frame = it }
    PixelCopy.request(window, image, {}, Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun capture(name: String): Bitmap {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val dir = File("build/f8-10-zoom-x").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return image
  }

  private fun luminance(image: Bitmap, x: Int, y: Int): Float {
    val pixel = image.getPixel(x, y)
    return 0.2126f * android.graphics.Color.red(pixel) + 0.7152f * android.graphics.Color.green(pixel) +
      0.0722f * android.graphics.Color.blue(pixel)
  }

  /** Il pixel piu' scuro nel quadrato centrale della «x» (un terzo del riquadro del tocco): il glifo. */
  private fun glyphInk(image: Bitmap): Float {
    val x = compose.onNodeWithTag(CloseTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val side = x.width / 3f
    val area = Rect(x.center.x - side / 2f, x.center.y - side / 2f, x.center.x + side / 2f, x.center.y + side / 2f)
    var darkest = 255f
    for (y in area.top.toInt()..area.bottom.toInt()) for (px in area.left.toInt()..area.right.toInt()) {
      darkest = minOf(darkest, luminance(image, px, y))
    }
    return darkest
  }

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        val page = rememberGlassBackdrop()
        val namespace = rememberFluidGlassNamespace()
        val source = remember(namespace) { FluidTransitionSource("condividi", namespace) }
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            // La pagina: una foto scura in alto (dove passa la «x» mentre il foglio cresce dal tasto) e righe di testo.
            Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color.White)) {
              Column {
                Box(Modifier.fillMaxWidth().height(360.dp).background(Color(0xFF3A2440)))
                repeat(20) {
                  BasicText("Posizione dell'originale, 43° 46′ N", style = TextStyle(fontSize = 17.sp, color = Color.Black))
                }
              }
            }
            FluidGlassIconButton(
              onClick = { open = true },
              size = 44.dp,
              modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 12.dp)
                .fluidTransitionSource("condividi", namespace, CircleShape),
            ) { FluidIcon(FluidIcons.IosShare, contentDescription = null) }
            FluidGlassSheet(
              visible = open,
              onDismissRequest = { open = false },
              detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
              source = source,
              undimmedUpTo = null,
            ) {
              Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(if (header == "1 foto") Color(0xFFD0D0D4) else Color(0xFFB03040)))
                BasicText(header, Modifier.weight(1f).padding(start = 12.dp), style = TextStyle(fontSize = 17.sp))
                FluidGlassIconButton(onClick = { open = false }, size = 36.dp, modifier = Modifier.testTag(CloseTag)) {
                  FluidIcon(FluidIcons.Close, contentDescription = null, size = 18.dp)
                }
              }
              Spacer(Modifier.height(320.dp))
            }
            FluidGlassModalHost(state = host, backdrop = page)
          }
        }
      }
    }
    advance(400)
  }

  /** Il foglio nasce dal tasto; [changeAtMillis] dopo, se c'e', la testata cambia. Poi la posa, lunga. */
  private fun openAndRest(name: String, changeAtMillis: Long?): Float {
    open = true
    if (changeAtMillis != null) {
      advance(changeAtMillis)
      capture("$name-meta-zoom")
      header = "1 foto · Immagine JPG · 49,9 KB · Posizione inclusa"
    }
    advance(2_000)
    return glyphInk(capture("$name-a-riposo"))
  }

  @Test fun theCloseGlyphIsSharpAtRestWhenNothingChanges() {
    show()
    val ink = openAndRest("ferma", changeAtMillis = null)
    println("F8.10 zoom: la «x» a riposo, testata ferma: pixel piu' scuro $ink")
    assertTrue("la «x» a riposo non e' nitida (pixel piu' scuro $ink)", ink < InkLimit)
  }

  @Test fun theCloseGlyphIsSharpAtRestWhenTheHeaderChangesMidZoom() {
    show()
    for (at in listOf(48L, 112L, 176L, 240L)) {
      val ink = openAndRest("cambia-a-$at", changeAtMillis = at)
      println("F8.10 zoom: la «x» a riposo, testata cambiata a $at ms: pixel piu' scuro $ink")
      assertTrue("la testata cambiata a $at ms lascia la «x» com'era a meta' zoom (pixel piu' scuro $ink)", ink < InkLimit)
      open = false
      header = "1 foto"
      advance(1_200)
    }
  }

  private companion object {
    const val CloseTag = "f8-10-x"

    /** Il glifo nitido e' nero (sotto 60 su 255); il disegno di meta' zoom sul tablet era una macchia a 205. */
    const val InkLimit = 110f
  }
}
