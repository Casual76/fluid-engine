package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.novita.F8p4Tags
import dev.antigravity.fluidengine.sample.novita.F8p8Tags
import dev.antigravity.fluidengine.sample.novita.NewElementsPageAt
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAsyncImage
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidImagePhase
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidThumbHash
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.security.MessageDigest
import java.time.Duration
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
 * F7.29 · Le impronte dei pixel delle celle che hanno perso i layer di ritaglio: la griglia delle foto di F8.4 (ferma e
 * con due foto scelte), il selettore di F8.14 e `FluidAsyncImage` nelle sue fasi (arrivata, con la sagoma, a metà
 * dell'arrivo, l'impronta ThumbHash, l'errore). La prova stampa un'impronta SHA-256 per caso e salva le immagini in
 * `build/f7-29-pixels/`: la stessa classe lanciata da sola sulla base e sul ramo deve dare le stesse impronte (rapporto
 * F7.29). Qui controlla solo che ogni caso si fotografi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi", application = GlassBenchDevice::class)
class F7p29CellPixelsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun start() {
    compose.mainClock.autoAdvance = false
  }

  private fun advance(millis: Long) {
    var left = millis
    while (left > 0) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun print(name: String): String {
    val window = compose.activity.window
    val view = window.decorView
    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy $name", PixelCopy.SUCCESS, result)
    val pixels = IntArray(image.width * image.height)
    image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = java.nio.ByteBuffer.allocate(pixels.size * 4)
    bytes.asIntBuffer().put(pixels)
    val hash = digest.digest(bytes.array()).joinToString("") { "%02x".format(it) }
    val dir = File("build/f7-29-pixels").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    println("IMPRONTA $name $hash")
    return hash
  }

  @Composable
  private fun Page(initial: String) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val host = rememberFluidGlassModalHostState()
      androidx.compose.runtime.CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
        Box(Modifier.fillMaxSize()) {
          NewElementsPageAt(onBack = {}, initial = initial)
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  /** Una foto finta: quattro bande di colore, così il ritaglio e la scala si vedono nei pixel. */
  private fun photo(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(64, 40, Bitmap.Config.ARGB_8888)
    val colors = intArrayOf(0xFFD9534F.toInt(), 0xFF5CB85C.toInt(), 0xFF428BCA.toInt(), 0xFFF0AD4E.toInt())
    for (x in 0 until 64) for (y in 0 until 40) bitmap.setPixel(x, y, colors[(x / 16 + y / 10) % 4])
    return bitmap.asImageBitmap()
  }

  /** L'impronta ThumbHash della stessa foto. */
  private fun thumbHash(): ByteArray {
    val rgba = ByteArray(16 * 10 * 4)
    val colors = intArrayOf(0xFFD9534F.toInt(), 0xFF5CB85C.toInt(), 0xFF428BCA.toInt(), 0xFFF0AD4E.toInt())
    for (x in 0 until 16) for (y in 0 until 10) {
      val c = colors[(x / 4 + y / 3) % 4]
      val i = (y * 16 + x) * 4
      rgba[i] = (c shr 16 and 0xFF).toByte(); rgba[i + 1] = (c shr 8 and 0xFF).toByte()
      rgba[i + 2] = (c and 0xFF).toByte(); rgba[i + 3] = 0xFF.toByte()
    }
    return FluidThumbHash.encode(16, 10, rgba)
  }

  @Test
  fun impronteDelleCelle() {
    val scene = mutableStateOf<(@Composable () -> Unit)?>(null)
    compose.setContent { scene.value?.invoke() }
    fun show(content: @Composable () -> Unit) {
      compose.runOnUiThread { scene.value = null }
      advance(64)
      compose.runOnUiThread { scene.value = content }
      advance(64)
    }

    // FluidAsyncImage, fermo: arrivata alla prima composizione (rettangolo e sagoma), l'impronta mentre carica, l'errore.
    val image = photo()
    val hash = thumbHash()
    show {
      FluidTheme(settings = remember { EngineSettings() }) {
        Column(Modifier.fillMaxSize().background(Color(0xFFEEEEEE)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluidAsyncImage(FluidImagePhase.Success(image), "foto", Modifier.size(97.dp, 61.dp))
            FluidAsyncImage(FluidImagePhase.Success(image), "foto", Modifier.size(97.dp, 61.dp), shape = ContinuousCornerShape(FluidRadius.Card))
            FluidAsyncImage(FluidImagePhase.Success(image), null, Modifier.size(61.dp, 97.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluidAsyncImage(FluidImagePhase.Loading, "foto", Modifier.size(97.dp, 61.dp), thumbHash = hash)
            FluidAsyncImage(FluidImagePhase.Loading, "foto", Modifier.size(97.dp, 61.dp))
            FluidAsyncImage(FluidImagePhase.Failure(), "foto", Modifier.size(120.dp, 120.dp), onRetry = {})
          }
        }
      }
    }
    advance(500)
    print("immagini-ferme")

    // L'arrivo: da Loading a Success, a metà dell'animazione (il livello della sfocatura c'è) e alla fine.
    val phase = mutableStateOf<FluidImagePhase>(FluidImagePhase.Loading)
    show {
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize().background(Color(0xFF223344)).padding(24.dp)) {
          FluidAsyncImage(phase.value, "foto", Modifier.size(200.dp, 125.dp), thumbHash = hash)
        }
      }
    }
    advance(200)
    compose.runOnUiThread { phase.value = FluidImagePhase.Success(image) }
    advance(16 * 9)
    print("immagine-a-meta-arrivo")
    advance(1_500)
    print("immagine-arrivata")

    // La griglia delle foto di F8.4, ferma; poi «Seleziona» e due foto scelte.
    show { Page("griglia-foto") }
    advance(1_500)
    print("griglia-foto")
    compose.onNodeWithTag(F8p4Tags.Select).performClick()
    advance(400)
    compose.onNodeWithTag(F8p4Tags.Grid).performTouchInput { click(Offset(60f, 330f)) }
    advance(300)
    compose.onNodeWithTag(F8p4Tags.Grid).performTouchInput { click(Offset(220f, 330f)) }
    advance(1_000)
    print("griglia-foto-scelte")

    // Il selettore di F8.14: le miniature arrivano dalla sorgente finta, poi si posano.
    show { Page("selettore-foto") }
    advance(2_500)
    print("selettore-foto")
    show { Page("foto-profilo") }
    advance(2_500)
    print("foto-profilo")

    // Il calendario dell'agenda di F8.8: il mese coi segni (arrivano un mese alla volta) e la striscia della settimana.
    show { Page("calendario-agenda") }
    advance(2_500)
    print("calendario-agenda")
    compose.onNodeWithTag(F8p8Tags.Agenda).performTouchInput {
      swipeUp(startY = centerY, endY = centerY - 420f, durationMillis = 400)
    }
    advance(2_000)
    print("calendario-agenda-striscia")
    show {}
  }
}
