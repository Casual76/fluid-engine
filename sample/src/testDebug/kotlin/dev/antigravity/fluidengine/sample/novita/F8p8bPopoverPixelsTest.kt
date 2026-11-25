package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidCompactAdaptation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidPresentationMode
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
 * F8.8b · il popover che nasce da una sorgente, coi pixel della finestra (PixelCopy): sotto il pannello a riposo il
 * testo della pagina, nero su bianco, **non si legge**; i numeri del calendario sopra restano a 4,5:1; la sagoma a
 * riposo e' un rettangolo arrotondato pulito (lati diritti).
 *
 * **Il vetro e' quello di ripiego (`Balanced`), coi verdetti degli shader rifiutati.** Il popover e' una superficie di
 * Fluid-physics, e col materiale `Liquid` ogni lastra di Fluid-physics passa da `GlassBody8`: il compilatore AGSL di
 * Robolectric lo rifiuta («program is too large», come in corr-s4-menu, corr-s4-zoom, f3-10 e sull'emulatore di
 * f4a-filo). Coi verdetti accettati la cattura della finestra si ferma su quell'errore. Il ramo `Liquid` della
 * correzione (la cattura nella regione del viaggio) lo provano `FluidPopoverWindowCaptureTest` sul JVM e la ripresa di
 * controllo sul tablet; qui si prova che il popover, a riposo, e' un pannello smerigliato e pulito, nella scena vera
 * della galleria e in un popover qualunque nato da una sorgente.
 *
 * La misura: l'escursione della luminanza (99° meno 1° percentile) in un ritaglio del pannello dove sopra non c'e'
 * contenuto e sotto c'e' testo della pagina. Il testo nitido ne ha piu' di 200 su 255; la soglia e' [SPREAD_LIMIT]. Le
 * immagini finiscono in `sample/build/f8p8b-pixels/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p8bPopoverPixelsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() = FluidGlassDiagnostics.resetForTesting()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private lateinit var view: android.view.View

  private fun tap(at: Offset) {
    val finger = MaterialFinger(view)
    finger.down(at)
    advance(48)
    finger.up(at)
  }

  private fun capture(name: String): Bitmap {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val dir = File("build/f8p8b-pixels").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return image
  }

  private fun luminance(image: Bitmap, x: Int, y: Int): Float {
    val pixel = image.getPixel(x, y)
    return 0.2126f * android.graphics.Color.red(pixel) + 0.7152f * android.graphics.Color.green(pixel) +
      0.0722f * android.graphics.Color.blue(pixel)
  }

  /** L'escursione della luminanza nel ritaglio: 99° meno 1° percentile, su 255 (l'1% ai due capi e' rumore). */
  private fun spread(image: Bitmap, area: Rect): Float {
    val values = ArrayList<Float>()
    val x0 = max(0, area.left.toInt()); val x1 = min(image.width - 1, area.right.toInt())
    val y0 = max(0, area.top.toInt()); val y1 = min(image.height - 1, area.bottom.toInt())
    for (y in y0..y1) for (x in x0..x1) values += luminance(image, x, y)
    values.sort()
    return values[(values.size * 0.99f).toInt().coerceAtMost(values.size - 1)] - values[(values.size * 0.01f).toInt()]
  }

  private fun relative(l: Float): Double {
    val c = l / 255.0
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
  }

  /** Il contrasto WCAG fra il pixel piu' scuro e la mediana di un riquadro: la cifra sul suo fondo. */
  private fun contrast(image: Bitmap, area: Rect): Double {
    val values = ArrayList<Float>()
    for (y in area.top.toInt()..area.bottom.toInt()) for (x in area.left.toInt()..area.right.toInt()) {
      values += luminance(image, x, y)
    }
    values.sort()
    val ink = relative(values.first())
    val ground = relative(values[values.size / 2])
    return (max(ink, ground) + 0.05) / (min(ink, ground) + 0.05)
  }

  /**
   * Dove il bordo del pannello taglia la riga [y] fra [from] e [to]: il pixel piu' scuro, cioe' la linea del bordo (due
   * pixel piu' scuri della pagina fuori e del vetro dentro). Il salto di luminanza piu' forte cadeva ora sul lato esterno
   * della linea ora su quello interno, secondo il testo sfocato dietro.
   */
  private fun edgeX(image: Bitmap, y: Int, from: Int, to: Int): Int =
    (max(0, from) until min(image.width, to)).minBy { luminance(image, it, y) }

  private fun edgeY(image: Bitmap, x: Int, from: Int, to: Int): Int =
    (max(0, from) until min(image.height, to)).minBy { luminance(image, x, it) }

  private fun cell(day: String): Rect =
    compose.onNode(hasContentDescription(day), useUnmergedTree = false).fetchSemanticsNode().boundsInRoot

  /** La galleria come `GalleryActivity`: la scena dentro la chrome, e l'host dei modali col fondale eletto da lei. */
  private fun showGallery() {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val chrome = rememberFluidChromeController()
        val host = rememberFluidGlassModalHostState()
        val fallback = rememberGlassBackdrop()
        val backdrop = chrome.activeBackdrop.value ?: fallback
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            ProvideFluidChrome(controller = chrome, bottomInset = 0.dp, scrollToTop = remember { FluidScrollToTopBus() }) {
              Box(Modifier.fillMaxSize().fluidChromePages()) {
                NewElementsPageAt(onBack = {}, initial = "capsula-data")
              }
            }
            FluidGlassModalHost(state = host, backdrop = backdrop)
          }
        }
      }
    }
    advance(800)
  }

  @Test fun `il popover della capsula a riposo smeriglia la pagina, tiene le cifre a 4,5 a 1 e ha i lati diritti`() {
    showGallery()
    val closed = capture("galleria-chiusa")
    val capsule = compose.onNodeWithTag(F8p8Tags.DateCapsule, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    tap(capsule.center)
    advance(1600)
    val open = capture("galleria-aperta")

    // La prima riga di novembre 2026 ha sei celle vuote (il primo e' domenica): li' sopra c'e' solo vetro, e sotto
    // la riga «Spegnimento» della pagina, nero su bianco.
    val one = cell("1 novembre")
    val two = cell("2 novembre")
    val empty = Rect(two.left + 6f, one.top + 6f, one.left - 6f, one.bottom - 6f)
    val bare = spread(closed, empty)
    val under = spread(open, empty)
    val digits = cell("15 novembre").let { contrast(open, Rect(it.left + 4f, it.top + 4f, it.right - 4f, it.bottom - 4f)) }
    println("F8p8b capsula: pagina nuda ${"%.1f".format(bare)}, sotto il pannello ${"%.1f".format(under)}, " +
      "cifre ${"%.2f".format(digits)}:1, ritaglio $empty")
    assertTrue("sotto il ritaglio la pagina non ha testo da coprire: $bare", bare > 150f)
    assertTrue("sotto il popover il testo della pagina si legge: escursione $under", under < SPREAD_LIMIT)
    assertTrue("le cifre del calendario scendono a $digits:1", digits >= 4.5)

    // La sagoma a riposo: i fianchi e il fondo diritti, al pixel, fra gli angoli (le righe si fermano alla cima
    // dell'ultima settimana, sopra l'angolo in basso).
    val thirty = cell("30 novembre")
    val rows = (two.top.toInt()..thirty.top.toInt() step 12)
    val left = rows.map { edgeX(open, it, two.left.toInt() - 60, two.left.toInt() - 4) }
    val right = rows.map { edgeX(open, it, one.right.toInt() + 4, one.right.toInt() + 60) }
    val columns = ((thirty.right.toInt() + 24)..(one.left.toInt() - 24) step 24)
    val bottom = columns.map { edgeY(open, it, thirty.bottom.toInt() + 4, thirty.bottom.toInt() + 80) }
    println("F8p8b sagoma: sinistra $left, destra $right, fondo $bottom")
    for ((side, edge) in listOf("sinistro" to left, "destro" to right, "di fondo" to bottom)) {
      assertTrue("il lato $side non e' diritto: $edge", edge.max() - edge.min() <= 1)
    }
  }

  private var open by mutableStateOf(false)

  @Test fun `sotto un popover qualunque nato da una sorgente il testo della pagina non si legge`() {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        val page = rememberGlassBackdrop()
        val namespace = rememberFluidGlassNamespace()
        val source = remember(namespace) { FluidTransitionSource("prova", namespace) }
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            // La pagina: righe di testo nero su bianco, a 17 sp come la riga dei valori della scena della capsula.
            Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color.White)) {
              Column(Modifier.padding(top = 180.dp, start = 12.dp)) {
                repeat(30) {
                  BasicText(
                    "Scadenza 2026-11-25, vacanze 21/12 – 6/1, spegnimento 45 min",
                    style = TextStyle(fontSize = 17.sp, color = Color.Black),
                    maxLines = 1,
                  )
                }
              }
            }
            Box(Modifier.padding(top = 100.dp, start = 120.dp)) {
              Box(
                Modifier
                  .fluidTransitionSource("prova", namespace, FluidCapsuleShape)
                  .size(140.dp, 34.dp)
                  .background(Color(0xFFE0E0E4), FluidCapsuleShape)
                  .fluidPressable(onClick = { open = true })
                  .testTag("sorgente"),
              )
              FluidGlassModalPortal(
                visible = open,
                onDismissRequest = { open = false },
                source = source,
                compactAdaptation = FluidCompactAdaptation.Popover,
                mode = FluidPresentationMode.Parallel,
              ) {
                // Un pannello senza contenuto: quello che si vede dentro e' il vetro sopra la pagina.
                Box(Modifier.size(320.dp, 300.dp).testTag("contenuto"))
              }
            }
            FluidGlassModalHost(state = host, backdrop = page)
          }
        }
      }
    }
    advance(600)
    val content = compose.onNodeWithTag("contenuto", useUnmergedTree = true)
    val bareArea = Rect(40f, 420f, 700f, 900f)
    val bare = spread(capture("sorgente-chiusa"), bareArea)
    tap(compose.onNodeWithTag("sorgente", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center)
    advance(1600)
    val area = content.fetchSemanticsNode().boundsInRoot.let { Rect(it.left + 24f, it.top + 24f, it.right - 24f, it.bottom - 24f) }
    val under = spread(capture("sorgente-aperta"), area)
    println("F8p8b popover dalla sorgente: pagina nuda ${"%.1f".format(bare)}, sotto il pannello ${"%.1f".format(under)}, ritaglio $area")
    assertTrue("la pagina non ha testo da coprire: $bare", bare > 150f)
    assertTrue("sotto il popover il testo della pagina si legge: escursione $under", under < SPREAD_LIMIT)
  }

  private companion object {
    /**
     * Quanta escursione di luminanza lascia passare il pannello sopra un testo nero su bianco: **40 su 255**. Il testo
     * nitido a 17 sp ne ha piu' di 200; a 40 le lettere sono una macchia (il contrasto locale massimo e' sotto 1,6:1,
     * sotto la soglia di lettura di un testo minuto). Il popover della capsula a riposo ne lascia 30 (la pagina nuda 231),
     * quello qualunque 32 (la pagina nuda 255). Scelta il
     * 24 novembre 2026, sulle immagini di Robolectric e sui fermi del tablet (dove il testo sotto il velo era nitido).
     */
    const val SPREAD_LIMIT = 40f
  }
}
