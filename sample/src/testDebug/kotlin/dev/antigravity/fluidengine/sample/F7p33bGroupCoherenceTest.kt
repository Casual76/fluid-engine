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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassAppearance
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
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
import org.junit.Assert.assertFalse
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
 * F7.33b · **La coerenza del gruppo** (D1, scelta di Alessio del 27/11/2026): «se in una barra tutti i tasti piccoli sono
 * chiari, anche uno più grande deve esserlo». I vetri di uno stesso gruppo prendono una decisione sola.
 *
 * - La riga di «Leggi testo» sopra la foto di una lettera (l'azione rapida, «Testo», il tasto di Testo attivo): a
 *   sinistra la carta bianca, a destra il tavolo scuro. Fino a F7.33 ogni comando era un vetro a sé con la sua sonda,
 *   e l'azione rapida restava chiara mentre «Testo» e il tasto giravano allo scuro. Si legge nei pixel il lato
 *   dell'inchiostro di ogni comando: deve essere uno solo. Il prezzo (il caso dubbio del rapporto di F7.33b): i comandi
 *   sopra lo sfondo opposto a quello che decide perdono contrasto.
 * - Un gruppo con due tasti piccoli e un pannello grande, in tema scuro sopra il bianco: fino a F7.33 decideva il pannello
 *   (scurisce in continuo, non gira) e i tasti restavano scuri, diversi da ogni altro tasto sopra il bianco. Ora gira
 *   tutto il gruppo, il pannello compreso.
 *
 * Il lettore finto della sonda sa dove legge ([FluidGlassProbeDiagnostics.readerInWindowForTesting]): ogni sonda vede il
 * colore dello sfondo sotto la parte che legge. I PNG vanno in `build/reports/f7-33b/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xhdpi")
class F7p33bGroupCoherenceTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val out = File("build/reports/f7-33b")
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

  /** Il lato dell'inchiostro nel riquadro [bounds] (pixel della finestra), ristretto di [inset]: vero se chiaro. */
  private fun lightInk(shot: Bitmap, bounds: Rect, inset: Float): Pair<Boolean, Float> {
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

  /** Una lettera con una mail: la riga ha l'azione rapida a sinistra e «Testo» col tasto a destra. */
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

  @Test fun laRigaDiLeggiTestoGiraTuttaInsieme() {
    val state = letter()
    val width = compose.activity.resources.displayMetrics.widthPixels.toFloat()
    // La carta a sinistra, il tavolo scuro a destra: ogni sonda vede quello che ha sotto il centro della parte che legge.
    FluidGlassProbeDiagnostics.readerInWindowForTesting = { r -> if (r.center.x < width * split) Color.White else Color.Black }
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
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
    val shot = capture("riga-leggi-testo")
    val sides = linkedMapOf<String, Pair<Boolean, Float>>()
    for (tag in listOf(FluidLiveTextTags.QuickAction, FluidLiveTextTags.ShowText, FluidLiveTextTags.CopyAll, FluidLiveTextTags.Button)) {
      val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
      if (nodes.isEmpty()) continue
      sides[tag] = lightInk(shot, nodes.first().boundsInWindow, if (tag == FluidLiveTextTags.Button) 0.22f else 0.1f)
    }
    File(out, "riga-leggi-testo.txt").writeText(sides.entries.joinToString("\n") { (k, v) -> "$k chiaro=${v.first} ${"%.2f".format(v.second)}:1" })
    assertTrue("servono l'azione rapida a sinistra e il tasto a destra: $sides",
      FluidLiveTextTags.QuickAction in sides && FluidLiveTextTags.Button in sides)
    val quick = compose.onAllNodesWithTag(FluidLiveTextTags.QuickAction, useUnmergedTree = true).fetchSemanticsNodes().first()
    val toggle = compose.onAllNodesWithTag(FluidLiveTextTags.Button, useUnmergedTree = true).fetchSemanticsNodes().first()
    assertTrue("l'azione rapida sta sulla carta", quick.boundsInWindow.center.x < width * split)
    assertTrue("il tasto sta sul tavolo", toggle.boundsInWindow.center.x > width * split)
    assertEquals("un lato solo per tutta la riga: $sides", 1, sides.values.map { it.first }.distinct().size)
    // In F7.33b decideva il grappolo più grande dei piccoli (qui l'azione rapida, sulla carta), e i comandi sopra il tavolo
    // stavano a 3,8:1 («Testo») e 3,0:1 (il tasto): il caso dubbio del rapporto. Dalla F7.33c (D7) la riga è a cavallo e
    // vince il tema (qui chiaro): il contrasto di ogni comando lo misura F7p33cStraddleTest; qui si controlla che restino
    // sopra 2,5:1.
    assertTrue("l'azione rapida, che decide, è leggibile: $sides", sides.getValue(FluidLiveTextTags.QuickAction).second >= 4.5f)
    for ((tag, side) in sides) assertTrue("$tag: $sides", side.second >= 2.5f)
  }

  /** I due tasti e il pannello del gruppo, col lato che il loro contenuto riceve (vero = scuro). */
  private val seen = linkedMapOf<String, Boolean?>()

  @Composable private fun Mark(id: String) {
    seen[id] = LocalFluidGlassAppearance.current?.isDark
    Text(id)
  }

  @Test fun unGruppoConUnPannelloGrandeGiraComeITasti() {
    FluidGlassProbeDiagnostics.readerForTesting = { Color.White }
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.DARK, dynamicColorEnabled = false) }) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).drawBehind { drawRect(Color.White) })
          // Gli shader del campo non si eseguono qui (come in GlassFlipRecompositionTest): albero, sonda e lato sono veri.
          // Distanza di fusione zero: tre vetri a sé, senza il campo fuso che il compilatore di Robolectric non accetta
          // («program is too large»); la decisione resta del contenitore.
          Box(Modifier.padding(24.dp).drawWithContent { }) {
            FluidGlassContainer(spacing = 0.dp) {
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FluidGlassIconButton(onClick = {}, backdrop = backdrop) { Mark("tasto-1") }
                FluidGlassIconButton(onClick = {}, backdrop = backdrop) { Mark("tasto-2") }
                Box(Modifier.size(240.dp, 200.dp).fluidGlassPanel(backdrop, GlassDefaults.modalTint(),
                  RoundedCornerShape(28.dp), GlassRole.Modal, large = true)) { Mark("pannello") }
              }
            }
          }
        }
      }
    }
    repeat(3) {
      advance(800)
      compose.runOnUiThread {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try { view.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
      }
    }
    advance(2_000)
    File(out, "gruppo-pannello.txt").writeText("$seen grande=${FluidGlassContainerDiagnostics.lastRestLarge}")
    assertFalse("un gruppo con dei piccoli non scurisce in continuo", FluidGlassContainerDiagnostics.lastRestLarge)
    assertEquals("tasti e pannello sul lato chiaro sopra il bianco, insieme: $seen", listOf(false, false, false),
      listOf(seen["tasto-1"], seen["tasto-2"], seen["pannello"]))
  }
}
