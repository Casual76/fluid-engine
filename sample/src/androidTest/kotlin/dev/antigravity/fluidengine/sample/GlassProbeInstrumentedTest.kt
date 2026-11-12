package dev.antigravity.fluidengine.sample

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * La sonda dello sfondo sul dispositivo (F2.1, piano 3.9; si lancia in S3): il lettore della finestra
 * (`HardwareRenderer` + `ImageReader`), la richiesta dopo il disegno, i conti sul filo di lavoro, e la catena intera
 * dalla revisione della sorgente al lato filtrato.
 *
 * La scena è la più piccola che serve: una sorgente (`layerBackdrop`) a tutto schermo, un fondo che cambia colore
 * **senza muoversi**, e una sonda di taglia e coordinate fisse (156×56 dp, la capsula del banco) sopra di essa. Nessun
 * vetro: la sonda sta da sola, e nessuno ricattura niente. È la prova «bianco → nero» del piano: sotto un vetro fermo
 * lo sfondo cambia, e l'unica cosa che lo dice alla sonda è la revisione della sorgente.
 *
 * Cosa guardare nel log (`adb logcat -s FluidGlassProbeTest`): i tempi dal cambio alla lettura e al giro. Attesi:
 * lettura entro 120 ms più un fotogramma e 3–5 ms di lettura (S1-bis); giro ≈ 160 ms dopo la lettura (la media del
 * filtro che attraversa 0,45 con τ = 200 ms).
 *
 * Comando (dal worktree, con il tablet collegato):
 * `gradlew :sample:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.antigravity.fluidengine.sample.GlassProbeInstrumentedTest`
 */
@RunWith(AndroidJUnit4::class)
class GlassProbeInstrumentedTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val dark = mutableStateOf(false)
  private val textured = mutableStateOf(false)
  private val shown = mutableStateOf(true)
  private val readings = CopyOnWriteArrayList<FluidGlassProbeReading>()

  @Before
  fun start() {
    assumeTrue("Il materiale nuovo, e con lui la sonda, esiste da Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    assumeTrue("La sonda non è disponibile (livello di API simulato?)", FluidGlassProbeDiagnostics.isAvailable)
  }

  @Composable
  private fun Scene() {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
      // La sorgente: cambia colore (e testo) senza muoversi. Ogni cambio la fa registrare di nuovo, e basta.
      Box(
        Modifier
          .fillMaxSize()
          .layerBackdrop(backdrop)
          .background(if (dark.value) Color.Black else Color.White),
      ) {
        if (textured.value) {
          Column(Modifier.padding(start = 24.dp, top = 100.dp)) {
            repeat(8) {
              BasicText(
                text = "Signs are made using neon lights. Light is used to define color, tone, and shape.",
                style = TextStyle(color = if (dark.value) Color.White else Color.Black, fontSize = 17.sp),
              )
            }
          }
        }
      }
      if (shown.value) {
        Box(
          Modifier
            .offset(x = 40.dp, y = 120.dp)
            .size(156.dp, 56.dp)
            .then(FluidGlassProbeDiagnostics.probe(backdrop) { readings += it }),
        )
      }
    }
  }

  /** Aspetta la prima consegna dopo [since] che soddisfa [condition]; la restituisce. */
  private fun awaitReading(
    since: Long,
    timeoutMillis: Long = 3_000L,
    what: String,
    condition: (FluidGlassProbeReading) -> Boolean,
  ): FluidGlassProbeReading {
    var found: FluidGlassProbeReading? = null
    try {
      compose.waitUntil(timeoutMillis) {
        found = readings.firstOrNull { it.uptimeMillis >= since && condition(it) }
        found != null
      }
    } catch (error: Throwable) {
      throw AssertionError("$what: nessuna consegna in $timeoutMillis ms. Consegne: ${readings.takeLast(6)}", error)
    }
    return found!!
  }

  private fun settleOnWhite(): FluidGlassProbeReading {
    compose.setContent { Scene() }
    compose.waitForIdle()
    return awaitReading(since = 0L, what = "la prima lettura su bianco") { it.fresh && it.luminance > 0.95f }
  }

  @Test
  fun aWhiteBackdropReadsWhiteFlatAndCovered() {
    val white = settleOnWhite()
    Log.i(Tag, "bianco: $white")
    assertTrue("copertura ${white.coverage}", white.coverage > 0.99f)
    assertTrue("un fondo pieno non ha varianza: ${white.deviation}", white.deviation < 0.01f)
    assertTrue("né gradienti: ${white.gradient}", white.gradient < 0.01f)
    assertFalse("su bianco il lato è chiaro", white.dark)
    assertTrue("il lettore della finestra è aperto", FluidGlassProbeDiagnostics.liveReaders >= 1)
  }

  @Test
  fun theBackdropTurnsBlackUnderAStillProbeAndBack() {
    settleOnWhite()
    val failures = FluidGlassProbeDiagnostics.totalFailures

    // Bianco → nero, senza che niente si muova.
    val toBlack = SystemClock.uptimeMillis()
    compose.runOnIdle { dark.value = true }
    val black = awaitReading(toBlack, what = "la lettura del nero") { it.fresh && it.luminance < 0.05f }
    val flipped = awaitReading(toBlack, what = "il giro allo scuro") { it.dark }
    Log.i(Tag, "bianco → nero: lettura dopo ${black.uptimeMillis - toBlack} ms, giro dopo ${flipped.uptimeMillis - toBlack} ms")
    assertTrue("la lettura del nero dopo ${black.uptimeMillis - toBlack} ms", black.uptimeMillis - toBlack < 1_000L)
    assertTrue("il giro dopo ${flipped.uptimeMillis - toBlack} ms", flipped.uptimeMillis - toBlack < 1_500L)

    // E ritorno: il giro al chiaro aspetta anche la permanenza (400 ms dal giro di prima).
    val toWhite = SystemClock.uptimeMillis()
    compose.runOnIdle { dark.value = false }
    val white = awaitReading(toWhite, what = "la lettura del bianco") { it.fresh && it.luminance > 0.95f }
    val back = awaitReading(toWhite, what = "il giro al chiaro") { !it.dark }
    Log.i(Tag, "nero → bianco: lettura dopo ${white.uptimeMillis - toWhite} ms, giro dopo ${back.uptimeMillis - toWhite} ms")
    assertTrue("il giro al chiaro dopo ${back.uptimeMillis - toWhite} ms", back.uptimeMillis - toWhite < 1_500L)
    assertEquals("nessuna lettura fallita", failures, FluidGlassProbeDiagnostics.totalFailures)
  }

  @Test
  fun aStillPageIsNotRead() {
    settleOnWhite()
    // Lascia finire ciò che è in coda (la lettura della nascita può averne chiesta una seconda).
    SystemClock.sleep(500L)
    compose.waitForIdle()
    val before = readings.count { it.fresh }
    SystemClock.sleep(1_500L)
    compose.waitForIdle()
    val after = readings.count { it.fresh }
    Log.i(Tag, "pagina ferma: ${after - before} letture in 1,5 s")
    assertTrue("niente eventi, niente letture: ${after - before} in 1,5 s", after - before <= 1)
  }

  @Test
  fun textUnderTheProbeRaisesVarianceAndGradient() {
    val flat = settleOnWhite()
    val since = SystemClock.uptimeMillis()
    compose.runOnIdle { textured.value = true }
    val text = awaitReading(since, what = "la lettura del testo") { it.fresh && it.gradient > 0.02f }
    Log.i(Tag, "piatto: $flat; testo: $text")
    // Il banco (probe_stats, capsula su testo, riduzione dell'engine a 16×16): sd 0,14, gradiente 0,13; piatto 0.
    assertTrue("la deviazione sul testo ${text.deviation} contro ${flat.deviation} sul piatto", text.deviation > 0.03f)
    assertTrue("il gradiente sul testo ${text.gradient}", text.gradient > 0.03f)
    assertFalse("testo nero su bianco: il lato resta chiaro", text.dark)
  }

  @Test
  fun theReaderClosesWithTheLastProbe() {
    val before = FluidGlassProbeDiagnostics.liveReaders
    settleOnWhite()
    assertTrue(FluidGlassProbeDiagnostics.liveReaders >= 1)
    compose.runOnIdle { shown.value = false }
    compose.waitUntil(2_000L) { FluidGlassProbeDiagnostics.liveReaders == before }
    assertEquals("l'ultima sonda chiude il lettore della finestra", before, FluidGlassProbeDiagnostics.liveReaders)
  }

  private companion object {
    const val Tag = "FluidGlassProbeTest"
  }
}
