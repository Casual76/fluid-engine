@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.action

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.LocalFluidIconSize
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionButton
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.action.label
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * I fotogrammi densi del tasto Salva (F6B.3a) dal banco: un fotogramma ogni 16 ms, il tondo col solo simbolo e la capsula
 * con la scritta, sopra un fondale a righe che il vetro rifrange. Quattro scene: il lavoro che riesce, quello che
 * fallisce (la scossa del solo contenuto, la tinta d'errore), il lavoro breve (sotto i 300 ms: solo l'esito) e Scarica con
 * la percentuale. A destra, come controllo, un tasto di vetro con lo scambio d'icona di sempre. I PNG e la cronologia vanno
 * in `build/reports/f6b-3a/`; il collage lo fa lo strumento della sessione.
 *
 * Nei test le animazioni infinite sono ferme: l'anello indeterminato qui non gira (sul dispositivo sì).
 *
 * Accanto, la misura: i limiti dei due tasti nell'albero sono gli stessi in ogni fotogramma (il vetro non cambia misura
 * con lo stato), e la cronologia lo scrive.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w300dp-h96dp-xhdpi")
class FluidActionFramesTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val out = File("build/reports/f6b-3a")

  private var state by mutableStateOf<FluidActionState>(FluidActionState.Idle)
  private var preset by mutableStateOf(FluidActionPreset.Save)
  // Uno stato di Compose, come l'avanzamento dell'esecutore: il disegno lo legge e si ridisegna da solo.
  private var progress by mutableFloatStateOf(0f)
  private val reader = { progress }

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

  private fun frame(ms: Long = 16) {
    compose.mainClock.advanceTimeBy(ms)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    Snapshot.sendApplyNotifications()
  }

  private fun show() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val page = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(
            Modifier.fillMaxSize().glassBackdropSource(page)
              .background(Brush.linearGradient(listOf(Color(0xFF7FB8E8), Color(0xFFF2C38B))))
              .drawBehind {
                val step = 18.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                  drawRect(Color.White.copy(alpha = 0.35f), Offset(x, 0f), Size(step / 3f, size.height))
                  x += step
                }
              },
          )
          Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FluidActionButton(preset, state, onClick = {}, modifier = Modifier.testTag("tondo"), backdrop = page)
            FluidActionButton(preset, state, onClick = {}, modifier = Modifier.testTag("capsula"), backdrop = page,
              label = if (preset == FluidActionPreset.Download) "Scarica" else "Salva")
            // Il controllo: lo scambio d'icona di sempre (F3.4) su un tasto di vetro, senza il tasto con esito. Dice se un
            // difetto dei fotogrammi è dello scambio o del tasto.
            FluidGlassIconButton(onClick = {}, backdrop = page) {
              val icon = when (state) {
                FluidActionState.Succeeded -> FluidIcons.Check
                FluidActionState.Failed -> FluidIcons.Close
                else -> preset.idle
              }
              FluidGlassContentTransition(icon, blurReference = LocalFluidIconSize.current) { FluidIcon(it, contentDescription = null) }
            }
          }
        }
      }
    }
    repeat(40) { frame() }
  }

  /** Un cambio di stato a [atMs] dall'inizio della scena. */
  private class Cue(val atMs: Long, val state: () -> FluidActionState)

  /**
   * Registra [name]: un fotogramma ogni 16 ms fino a [untilMs], con i cambi [cues]; nei tratti [skip] (da, a) il tempo
   * passa senza fotogrammi salvati. [ramp] dà l'avanzamento al tempo t.
   */
  private fun record(
    name: String,
    cues: List<Cue>,
    untilMs: Long,
    skip: List<Pair<Long, Long>> = emptyList(),
    ramp: (Long) -> Float = { 0f },
  ): List<String> {
    fun bounds(): String = listOf("tondo", "capsula").joinToString(";") { tag ->
      compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.let { "${it.left},${it.top},${it.width},${it.height}" }
    }
    state = FluidActionState.Idle
    progress = 0f
    repeat(20) { frame() }
    val dir = File(out, name).apply { deleteRecursively(); mkdirs() }
    val log = StringBuilder("fotogramma,ms,stato\n")
    var t = 0L
    var index = 0
    var next = 0
    val boxes = mutableListOf<String>()
    while (t <= untilMs) {
      val gap = skip.firstOrNull { t >= it.first && t < it.second }
      if (gap != null) {
        while (next < cues.size && cues[next].atMs < gap.second) { compose.runOnUiThread { state = cues[next].state() }; next++ }
        frame(gap.second - t)
        t = gap.second
        continue
      }
      while (next < cues.size && cues[next].atMs <= t) {
        val cue = cues[next]
        compose.runOnUiThread { state = cue.state() }
        next++
      }
      progress = ramp(t)
      frame()
      t += 16
      val shot = window()
      shot.compress(File(dir, "f%03d.png".format(index)))
      log.append("$index,$t,${state.label}\n")
      boxes += bounds()
      index++
    }
    File(dir, "cronologia.csv").writeText(log.toString())
    return boxes
  }

  private fun Bitmap.compress(file: File) = file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }

  @Test fun `Salva che lavora, riesce, fallisce, il lavoro breve e la percentuale`() {
    val enabled = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(true)
    try {
      show()
      val pending = { FluidActionState.Working(null, pending = true) }
      val working = { FluidActionState.Working() }
      // Il tocco a 80 ms; l'anello a 300 ms dal tocco; l'esito a 800 ms di lavoro.
      val ok = record("salva-riesce", listOf(Cue(80, pending), Cue(380, working), Cue(880) { FluidActionState.Succeeded },
        Cue(2_380) { FluidActionState.Idle }), untilMs = 2_800, skip = listOf(1_700L to 2_300L))
      val ko = record("salva-fallisce", listOf(Cue(80, pending), Cue(380, working), Cue(880) { FluidActionState.Failed },
        Cue(3_380) { FluidActionState.Idle }), untilMs = 3_800, skip = listOf(1_800L to 3_300L))
      val brief = record("salva-breve", listOf(Cue(80, pending), Cue(260) { FluidActionState.Succeeded },
        Cue(1_760) { FluidActionState.Idle }), untilMs = 2_100, skip = listOf(900L to 1_700L))
      preset = FluidActionPreset.Download
      val download = record("scarica-percentuale", listOf(Cue(80, pending), Cue(380) { FluidActionState.Working(reader) },
        Cue(1_620) { FluidActionState.Succeeded }), untilMs = 2_300, ramp = { t -> ((t - 380f) / 1_200f).coerceIn(0f, 1f) })
      for ((name, boxes) in listOf("riesce" to ok, "fallisce" to ko, "breve" to brief, "scarica" to download)) {
        // I due tasti hanno gli stessi limiti in ogni fotogramma: la misura non segue lo stato.
        val distinct = boxes.distinct()
        println("F6B.3a fotogrammi $name: ${boxes.size}, limiti dei tasti ${distinct.joinToString(" | ")}")
        assertEquals("$name: i tasti non cambiano misura (${distinct.joinToString(" | ")})", 1, distinct.size)
      }
    } finally {
      FluidGlassDiagnostics.resetForTesting()
      FluidGlassDiagnostics.enabled = enabled
    }
  }
}
