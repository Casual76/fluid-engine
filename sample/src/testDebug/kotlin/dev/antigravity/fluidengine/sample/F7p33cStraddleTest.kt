package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassAppearance
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.ocr.FluidLiveTextControls
import dev.antigravity.fluidengine.viewer.ocr.FluidLiveTextState
import dev.antigravity.fluidengine.viewer.ocr.FluidLiveTextTags
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedBlock
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedLine
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedText
import dev.antigravity.fluidengine.viewer.ocr.FluidRecognizedWord
import dev.antigravity.fluidengine.viewer.ocr.FluidTextQuad
import java.io.File
import java.time.Duration
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
 * F7.33c · **Un gruppo a cavallo di un confine** (D7, scelta di Alessio del 27/11/2026: «ci sta una priorità per il tema
 * impostato, sennò il gruppo»). La riga di «Leggi testo» sopra la foto di una lettera: l'azione rapida sulla carta bianca
 * a sinistra, «Testo» e il tasto di Testo attivo sul tavolo nero a destra.
 *
 * - In tema scuro tutta la riga resta scura: sopra il bianco l'azione rapida è una pillola scura con la scritta chiara
 *   (il soffitto del velo dei grandi). In tema chiaro, specularmente, resta chiara, e sopra il nero il vetro chiaro ha il
 *   suo pavimento. Ogni comando a 4,5:1 (le scritte) o 3:1 (il tasto) contro il fondo vero, nei pixel. Il tasto di Testo
 *   attivo è acceso: ha il suo riempimento (il verde dell'accento) e il simbolo scuro nei due temi, e di lui conta solo il
 *   contrasto. In F7.33b decideva l'azione rapida, la più grande: in tema chiaro «Testo» stava a 3,8:1 e il tasto a 3,0:1
 *   sul tavolo, e in tema scuro la riga girava al chiaro.
 * - Un confine che passa piano sotto un gruppo fa girare il gruppo **una volta sola**: mentre il gruppo sta a cavallo vince
 *   il tema, e gira quando tutti i grappoli sono passati dall'altra parte (le stesse soglie e gli stessi tempi del filtro:
 *   l'ultimo tasto passa dall'altra parte quando la luma sotto di lui supera 0,62, cioè coperto per più di metà).
 *
 * Il lettore finto della sonda legge cella per cella ([FluidGlassProbeDiagnostics.readerInWindowForTesting]). I PNG e le
 * misure vanno in `build/reports/f7-33c/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xhdpi")
class F7p33cStraddleTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val out = File("build/reports/f7-33c")
  /** Dove finisce la carta e comincia il tavolo, in frazione della larghezza della finestra. */
  private val split = 0.45f
  private var warmUpWas = true

  @Before fun start() {
    warmUpWas = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidGlassDiagnostics.warmUpEnabled = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
    out.mkdirs()
  }

  @After fun stop() {
    FluidGlassDiagnostics.warmUpEnabled = warmUpWas
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun capture(name: String): Bitmap {
    val window = compose.activity.window
    val view = window.decorView
    var image: Bitmap? = null
    repeat(2) {
      image?.recycle()
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      var result = -1
      PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
      image = bitmap
      advance(48)
    }
    val shot = image!!
    File(out, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return shot
  }

  /** Il lato dell'inchiostro e il contrasto nel riquadro [bounds] (pixel della finestra), ristretto di [inset]. */
  private fun ink(shot: Bitmap, bounds: Rect, inset: Float): Pair<Boolean, Float> {
    val dx = bounds.width * inset
    val dy = bounds.height * inset
    val left = ceil(bounds.left + dx).toInt().coerceAtLeast(0)
    val right = floor(bounds.right - dx).toInt().coerceAtMost(shot.width)
    val top = ceil(bounds.top + dy).toInt().coerceAtLeast(0)
    val bottom = floor(bounds.bottom - dy).toInt().coerceAtMost(shot.height)
    val pixels = IntArray((right - left) * (bottom - top))
    shot.getPixels(pixels, 0, right - left, left, top, right - left, bottom - top)
    val values = FloatArray(pixels.size) { Color(pixels[it]).luminance() }.apply { sort() }
    val ground = values[values.size / 2]
    val low = values[values.size / 100]
    val high = values[values.lastIndex - values.size / 100]
    val light = (high + .05f) / (ground + .05f)
    val dark = (ground + .05f) / (low + .05f)
    return if (light > dark) true to light else false to dark
  }

  private fun letter(): FluidLiveTextState {
    fun line(words: List<String>, top: Float): FluidRecognizedLine {
      var x = 0.08f
      val recognized = words.map { w ->
        val width = 0.012f * w.length
        FluidRecognizedWord(w, FluidTextQuad.of(Rect(x, top, x + width, top + 0.02f)), 0.95f).also { x += width + 0.01f }
      }
      return FluidRecognizedLine(recognized, FluidTextQuad.of(Rect(0.08f, top, x, top + 0.02f)))
    }
    val lines = listOf(
      line("Gentile signora Ferri".split(' '), 0.1f),
      line("Per spostare l'appuntamento scriva a info@rb.it".split(' '), 0.14f),
    )
    val text = FluidRecognizedText(listOf(FluidRecognizedBlock(lines, FluidTextQuad.of(Rect(0f, 0f, 1f, 1f)))), 720, 1560)
    return FluidLiveTextState(1f).also {
      it.show(text)
      it.active = true
    }
  }

  /** La riga sopra la carta e il tavolo nel tema [dark]: il lato e il contrasto di ogni comando. */
  private fun mixedRow(dark: Boolean): Map<String, Pair<Boolean, Float>> {
    val state = letter()
    val width = compose.activity.resources.displayMetrics.widthPixels.toFloat()
    FluidGlassProbeDiagnostics.readerInWindowForTesting = { r -> if (r.center.x < width * split) Color.White else Color.Black }
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).drawBehind {
            drawRect(Color.White, size = Size(size.width * split, size.height))
            drawRect(Color.Black, topLeft = Offset(size.width * split, 0f), size = Size(size.width * (1 - split), size.height))
          })
          CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            FluidLiveTextControls(state, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp))
          }
        }
      }
    }
    advance(2_500)
    val name = "riga-mista-${if (dark) "scuro" else "chiaro"}"
    val shot = capture(name)
    val sides = linkedMapOf<String, Pair<Boolean, Float>>()
    for (tag in listOf(FluidLiveTextTags.QuickAction, FluidLiveTextTags.ShowText, FluidLiveTextTags.CopyAll, FluidLiveTextTags.Button)) {
      val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
      if (nodes.isEmpty()) continue
      sides[tag] = ink(shot, nodes.first().boundsInWindow, if (tag == FluidLiveTextTags.Button) 0.22f else 0.1f)
    }
    File(out, "$name.txt").writeText(sides.entries.joinToString("\n") { (k, v) -> "$k chiaro=${v.first} ${"%.2f".format(v.second)}:1" })
    val quick = compose.onAllNodesWithTag(FluidLiveTextTags.QuickAction, useUnmergedTree = true).fetchSemanticsNodes().first()
    val toggle = compose.onAllNodesWithTag(FluidLiveTextTags.Button, useUnmergedTree = true).fetchSemanticsNodes().first()
    assertTrue("l'azione rapida sta sulla carta", quick.boundsInWindow.center.x < width * split)
    assertTrue("il tasto sta sul tavolo", toggle.boundsInWindow.center.x > width * split)
    return sides
  }

  /** Il lato dell'inchiostro dei comandi di vetro (vero = chiaro): il tasto acceso ha il suo riempimento e non conta. */
  private fun glassSides(sides: Map<String, Pair<Boolean, Float>>): List<Boolean> =
    sides.filterKeys { it != FluidLiveTextTags.Button }.values.map { it.first }

  private fun assertReadable(sides: Map<String, Pair<Boolean, Float>>) {
    for ((tag, side) in sides) {
      val needed = if (tag == FluidLiveTextTags.Button) 3f else 4.5f
      assertTrue("$tag a ${side.second}:1, serve $needed:1: $sides", side.second >= needed)
    }
  }

  @Test fun inTemaScuroLaRigaMistaRestaScura() {
    val sides = mixedRow(dark = true)
    assertTrue("tutta la riga col vetro scuro e l'inchiostro chiaro: $sides", glassSides(sides).all { it })
    assertReadable(sides)
  }

  @Test fun inTemaChiaroLaRigaMistaRestaChiara() {
    val sides = mixedRow(dark = false)
    assertTrue("tutta la riga col vetro chiaro e l'inchiostro scuro: $sides", glassSides(sides).none { it })
    assertReadable(sides)
  }

  /** Dove sta il confine (pixel della finestra) mentre la prova lo muove. Campo semplice: lo legge [Mark]. */
  private var boundaryNow = 0f

  /** Il lato che ogni tasto riceve a ogni composizione (vero = scuro), col confine di quel momento. */
  private val seen = mutableListOf<Triple<String, Boolean?, Float>>()

  @Composable private fun Mark(id: String) {
    val dark = LocalFluidGlassAppearance.current?.isDark
    seen += Triple(id, dark, boundaryNow)
    Text(id)
  }

  @Test fun unConfineCheScorreFaGirareIlGruppoUnaVoltaSola() {
    // Tema scuro: il bianco entra da sinistra e copre i tre tasti uno alla volta, in 6 s. A cavallo vince il tema (scuro);
    // quando sono tutti sul bianco il gruppo gira al chiaro, una volta. In F7.33b decideva il primo tasto, e il gruppo
    // girava quando il bianco copriva lui solo.
    val boundary = mutableFloatStateOf(0f)
    FluidGlassProbeDiagnostics.readerInWindowForTesting = { r -> if (r.center.x < boundary.floatValue) Color.White else Color.Black }
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.DARK, dynamicColorEnabled = false) }) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).drawBehind {
            val x = boundary.floatValue
            drawRect(Color.Black)
            if (x > 0f) drawRect(Color.White, size = Size(minOf(x, size.width), size.height))
          })
          // Gli shader del campo non si eseguono qui, e la distanza di fusione zero tiene i tasti grappoli a sé.
          Box(Modifier.padding(24.dp).drawWithContent { }) {
            FluidGlassContainer(spacing = 0.dp) {
              Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                FluidGlassIconButton(onClick = {}, modifier = Modifier.testTag("a"), backdrop = backdrop) { Mark("a") }
                FluidGlassIconButton(onClick = {}, modifier = Modifier.testTag("b"), backdrop = backdrop) { Mark("b") }
                FluidGlassIconButton(onClick = {}, modifier = Modifier.testTag("c"), backdrop = backdrop) { Mark("c") }
              }
            }
          }
        }
      }
    }
    fun drawWindow() = compose.runOnUiThread {
      val view = compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      try { view.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
    }
    repeat(3) { advance(600); drawWindow() }
    val width = compose.activity.resources.displayMetrics.widthPixels.toFloat()
    val last = compose.onAllNodesWithTag("c", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInWindow
    val firstRight = compose.onAllNodesWithTag("a", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInWindow.right
    // 6 s di confine che scorre, a passi di un fotogramma; un disegno ogni 64 ms.
    val steps = 6_000 / 16
    for (i in 1..steps) {
      boundaryNow = width * i / steps
      compose.runOnUiThread { boundary.floatValue = boundaryNow }
      advance(16)
      if (i % 4 == 0) drawWindow()
    }
    repeat(3) { advance(600); drawWindow() }
    val marks = seen.filter { it.first == "a" && it.second != null }
    val sides = marks.map { it.second!! }
    val changes = sides.zipWithNext().count { (x, y) -> x != y }
    val flipAt = marks.firstOrNull { it.second == false }?.third
    File(out, "confine.txt").writeText(
      "lati del tasto a: $sides\ncambi $changes\ngiro col confine a $flipAt px; il primo tasto finisce a $firstRight px, " +
        "l'ultimo va da ${last.left} a ${last.right} px",
    )
    assertTrue("alla fine il gruppo è sul chiaro: $sides", sides.lastOrNull() == false)
    assertEquals("il gruppo gira una volta sola: $sides", 1, changes)
    assertTrue("il gruppo gira solo quando il bianco copre anche l'ultimo tasto: a $flipAt px, l'ultimo a metà a ${last.center.x} px",
      flipAt != null && flipAt >= last.center.x)
  }
}
