package dev.antigravity.fluidengine.sample.novita

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreVert
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.security.MessageDigest
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
 * F7.31b · La goccia dei menù (la luce che scorre fra le voci sotto il dito): il posto e la crescita nel disegno, l'alfa in
 * uno strato suo dentro la trasformazione.
 *
 * Prima (F7.31) posto, crescita e alfa stavano nello stesso strato: una traslazione e una scala che cambiano sono per
 * Compose un cambio di layout, e con l'accessibilità accesa la pagina si ricontrollava ogni 100 ms mentre il dito scorreva
 * fra le voci. Qui:
 * - **il dito che scorre fra le voci**, con un servizio di accessibilità acceso: gli eventi `TYPE_WINDOW_CONTENT_CHANGED`
 *   col dito giù devono essere zero;
 * - **le pose del viaggio**: il menù fotografato (PixelCopy) mentre nasce dal tasto, aperto, col dito giù fra due voci,
 *   mentre si chiude e chiuso. Non ha un'attesa scritta: stampa le impronte (`F7p31bPIX | nome | sha256`) e salva le foto
 *   in `build/f7p31b-pixels/`; la classe da sola sul codice di prima e su quello di adesso, e le righe devono essere uguali.
 *   Col vetro di ripiego (verdetti degli shader rifiutati): la lastra del menù col materiale nuovo usa uno shader che il
 *   compilatore di Robolectric rifiuta («program is too large»), e la goccia passa dalla sua ricetta di Kyant, che ha lo
 *   stesso strato della presenza dentro la trasformazione.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F7p31bMenuDropTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private val picks = ArrayList<String>()

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    val shadow = shadowOf(manager())
    shadow.setEnabled(true)
    shadow.setTouchExplorationEnabled(true)
    shadow.setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun manager(): AccessibilityManager =
    ApplicationProvider.getApplicationContext<Context>().getSystemService(AccessibilityManager::class.java)

  private fun events(): Int =
    shadowOf(manager()).sentAccessibilityEvents.count { it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun actions(): List<FluidContextAction> = listOf(
    FluidContextAction(label = "Condividi", icon = FluidIcons.Share) { picks += "Condividi" },
    FluidContextAction(label = "Aggiungi ai preferiti", icon = FluidIcons.Filled.Star) { picks += "Preferiti" },
    FluidContextAction(label = "Rinomina", icon = FluidIcons.Edit) { picks += "Rinomina" },
    FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true) { picks += "Elimina" },
  )

  private fun scene() {
    compose.setContent {
      view = LocalView.current
      val chrome = rememberFluidChromeController()
      val scrollToTop = remember { FluidScrollToTopBus() }
      val host = rememberFluidGlassModalHostState()
      val page = rememberGlassBackdrop()
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
           ProvideFluidChrome(controller = chrome, bottomInset = 0.dp, scrollToTop = scrollToTop) {
           Box(Modifier.fillMaxSize().fluidChromePages().fluidGlassModalObscured()) {
            // La pagina: righe colorate e testo, che il vetro del menù e la goccia piegano.
            Column(
              Modifier
                .fillMaxSize()
                .glassBackdropSource(page)
                .drawBehind {
                  val colors = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFFFDD835), Color(0xFF43A047))
                  var y = 0f
                  var i = 0
                  while (y < size.height) {
                    drawRect(colors[i % colors.size], topLeft = Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, 11f))
                    y += 11f
                    i++
                  }
                }
                .padding(16.dp),
            ) {
              repeat(20) { Text("Riga della pagina $it", fontSize = 14.sp) }
            }
            FluidBarAction(
              icon = FluidIcons.MoreVert,
              contentDescription = "Altro",
              onClick = {},
              actions = { actions() },
              opensOn = FluidMenuOpensOn.Tap,
              modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 16.dp).testTag("altro"),
            )
           }
           }
            FluidGlassModalHost(state = host, backdrop = chrome.activeBackdrop.value ?: page)
          }
        }
      }
    }
    advance(600)
  }

  private fun bounds(text: String): Rect =
    compose.onAllNodesWithText(text).fetchSemanticsNodes().first().boundsInRoot

  private fun tapAltro() {
    val b = compose.onNodeWithTag("altro", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val finger = MaterialFinger(view)
    finger.down(b.center)
    advance(48)
    finger.up(b.center)
  }

  private fun capture(name: String) {
    val window = compose.activity.window
    // Robolectric disegna la finestra alla PixelCopy: due di fila, conta la seconda.
    repeat(2) {
      val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
      var result = -1
      compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
      if (it == 1) {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val bytes = java.nio.ByteBuffer.allocate(pixels.size * 4)
        bytes.asIntBuffer().put(pixels)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { b -> "%02x".format(b) }
        println("F7p31bPIX | $name | $hash")
        val dir = File("build/f7p31b-pixels").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out -> image.compress(Bitmap.CompressFormat.PNG, 100, out) }
      }
    }
  }

  /**
   * Il dito giù sulla prima voce e poi, a passi di 16 ms, fino alla terza: quanti eventi di accessibilità col dito giù (dopo
   * il primo tratto, in cui la goccia nasce sotto il dito), in quanti fotogrammi. [during] guarda col dito fermo a metà.
   */
  private fun slide(during: () -> Unit = {}): Pair<Int, Int> {
    val first = bounds("Condividi").center
    val third = bounds("Rinomina").center
    val finger = MaterialFinger(view)
    finger.down(first)
    advance(400)
    val before = events()
    var last = before
    var frames = 0
    val steps = 30
    for (i in 1..steps) {
      finger.move(Offset(first.x, first.y + (third.y - first.y) * i / steps))
      advance(16)
      val now = events()
      if (now != last) frames++
      last = now
      if (i == steps / 2) during()
    }
    val during = events() - before
    println("F7p31b menù: $during eventi in $frames fotogrammi col dito che scorre fra le voci (${steps * 16} ms)")
    finger.up(Offset(first.x, third.y))
    return during to frames
  }

  @Test
  fun `il dito che scorre fra le voci non fa ricontrollare la pagina`() {
    scene()
    tapAltro()
    advance(1_600)
    assertTrue("il menù non si è aperto", compose.onAllNodesWithText("Condividi").fetchSemanticsNodes().isNotEmpty())
    val (during, _) = slide()
    advance(1_200)
    assertEquals("il rilascio sceglie la voce sotto il dito", listOf("Rinomina"), picks)
    assertEquals("nessun evento di accessibilità col dito che scorre fra le voci", 0, during)
  }

  @Test
  fun `le pose del viaggio del menù`() {
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    scene()
    capture("pagina")
    tapAltro()
    // La nascita dal tasto: tre pose del viaggio.
    advance(48)
    capture("apre-48")
    advance(64)
    capture("apre-112")
    advance(96)
    capture("apre-208")
    advance(1_200)
    capture("aperto")
    // Il dito che scorre: a metà strada fra due voci, con la goccia cresciuta.
    slide { capture("dito") }
    // Il rilascio sceglie e chiude: due pose del ritorno.
    advance(48)
    capture("chiude-48")
    advance(64)
    capture("chiude-112")
    advance(1_200)
    capture("chiuso")
  }
}
