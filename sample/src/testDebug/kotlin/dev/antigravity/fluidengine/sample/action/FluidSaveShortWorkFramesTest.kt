package dev.antigravity.fluidengine.sample.action

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionButton
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.roundToInt
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
 * «Salva» col lavoro breve, sul tasto vero (scelta di Alessio del 22 novembre 2026, sulle riprese di F6B.12): fra l'icona
 * che esce e la spunta che entra non c'è più un attimo di vetro vuoto. Un fotogramma ogni 16 ms dalla riuscita; in ogni
 * fotogramma si misura l'inchiostro dentro il tasto (quanto è più scuro del suo vetro, sommato sui pixel) e lo si confronta
 * con quello a riposo. Con lo scambio sfocato di R7 le due facce si sovrappongono e l'inchiostro non scende sotto metà;
 * prima, fra i 67 e gli 80 ms, scendeva quasi a zero. I PNG vanno in `build/reports/f6b-12/salva-breve/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w200dp-h120dp-xhdpi")
class FluidSaveShortWorkFramesTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var state by mutableStateOf<FluidActionState>(FluidActionState.Idle)
  private val out = File("build/reports/f6b-12/salva-breve")

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun frame(ms: Long = 16) {
    compose.mainClock.advanceTimeBy(ms)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    Snapshot.sendApplyNotifications()
  }

  private fun window(): Bitmap {
    val decor = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, decor.width, decor.height), bitmap, { result ->
        assertEquals(PixelCopy.SUCCESS, result)
      }, Handler(Looper.getMainLooper()))
    }
    return bitmap
  }

  /** L'inchiostro nel riquadro del tasto: la somma di quanto ogni pixel è più scuro del vetro (il 90° percentile). */
  private fun Bitmap.ink(left: Int, top: Int, right: Int, bottom: Int): Double {
    val lumas = ArrayList<Int>()
    for (y in top until bottom) for (x in left until right) {
      val c = getPixel(x, y)
      lumas += (android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 + android.graphics.Color.blue(c) * 114) / 1000
    }
    val glass = lumas.sorted()[(lumas.size * 9) / 10]
    return lumas.sumOf { (glass - it).coerceAtLeast(0).toDouble() }
  }

  @Test fun `col lavoro breve Salva passa alla spunta senza un fotogramma vuoto`() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val page = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color(0xFFE6ECF2)))
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FluidActionButton(FluidActionPreset.Save, state, onClick = {}, modifier = Modifier.testTag("salva"), backdrop = page)
          }
        }
      }
    }
    repeat(40) { frame() }
    val box = compose.onNodeWithTag("salva").fetchSemanticsNode().boundsInRoot
    // Il tondo, senza il bordo e l'ombra: il centro, dove stanno icona e spunta.
    val inset = box.width * .2f
    val area = intArrayOf((box.left + inset).roundToInt(), (box.top + inset).roundToInt(),
      (box.right - inset).roundToInt(), (box.bottom - inset).roundToInt())
    out.deleteRecursively()
    out.mkdirs()
    val rest = window().let { shot ->
      shot.compress(Bitmap.CompressFormat.PNG, 100, File(out, "riposo.png").outputStream())
      shot.ink(area[0], area[1], area[2], area[3])
    }
    assertTrue("a riposo l'icona c'è ($rest)", rest > 0.0)
    // Un lavoro di 180 ms: per l'esecutore è un lavoro sotto soglia (la faccia di riposo) e poi la riuscita.
    compose.runOnUiThread { state = FluidActionState.Succeeded }
    val log = StringBuilder("ms,inchiostro_sulla_faccia_piu_leggera\n")
    val inks = (1..40).map { index ->
      frame()
      val shot = window()
      shot.compress(Bitmap.CompressFormat.PNG, 100, File(out, "f%03d.png".format(index)).outputStream())
      shot.ink(area[0], area[1], area[2], area[3])
    }
    // La spunta posata ha meno inchiostro dell'icona di Salva: il riferimento è la faccia più leggera delle due.
    val check = inks.last()
    assertTrue("la spunta c'è ($check)", check > 0.0)
    val reference = minOf(rest, check)
    inks.forEachIndexed { i, ink -> log.append("${(i + 1) * 16},${"%.3f".format(ink / reference)}\n") }
    File(out, "inchiostro.csv").writeText(log.toString())
    val weakest = inks.withIndex().minBy { it.value }
    assertTrue("il fotogramma più vuoto, a ${(weakest.index + 1) * 16} ms, ha ${"%.2f".format(weakest.value / reference)} " +
      "dell'inchiostro della faccia più leggera", weakest.value / reference >= .4)
  }
}
