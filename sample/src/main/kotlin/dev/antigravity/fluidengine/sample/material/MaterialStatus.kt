package dev.antigravity.fluidengine.sample.material

import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassLayerKind
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassModelReason
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassNodeKind
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Ciò che la diagnostica dell'engine dice del vetro in scena, in un momento: quante superfici sono `Liquid` e
 * quante `Classic` e perché, nodi e layer vivi, riscaldamento, livello simulato, verdetti degli shader, manopole.
 *
 * Pura, perché il formato delle righe è ciò che la sessione legge da logcat: lo tiene fermo un test.
 *
 * I conteggi valgono solo con la diagnostica accesa (`FluidGlassDiagnostics.enabled`), e contano ciò che è nato
 * dopo averla accesa: la galleria la accende prima del contenuto, nella build di debug.
 */
internal class MaterialStatus(
  val requested: FluidGlassModel,
  val realApi: Int,
  val simulatedApi: Int?,
  val warmUpEnabled: Boolean,
  val diagnosticsOn: Boolean,
  val surfaces: Map<FluidGlassModel, Int>,
  val choices: Map<FluidGlassModelReason, Int>,
  val nodes: Map<FluidGlassNodeKind, Int>,
  val layers: Map<FluidGlassLayerKind, Int>,
  /** Lo stato di ogni famiglia, già scritto (`Slab: Done (2/2), principale 3,1 ms`). */
  val warmUp: List<String>,
  /** Quante sorgenti AGSL la sonda ha già provato in questo processo. */
  val shadersTried: Int,
  val shadersRefused: List<String>,
  val tuning: Map<String, String>,
) {

  /** Le righe del riquadro, nell'ordine in cui si leggono. */
  fun lines(): List<String> = listOf(
    "vetro chiesto=${requested.name.lowercase()} api=$realApi simulato=${simulatedApi ?: "-"}" +
      " riscaldamento=${onOff(warmUpEnabled)} diagnostica=${onOff(diagnosticsOn)}",
    "superfici liquid=${surfaces[FluidGlassModel.Liquid] ?: 0} classic=${surfaces[FluidGlassModel.Classic] ?: 0}" +
      " | perche ${nonZero(choices)}",
    "nodi ${nonZero(nodes)} | layer ${nonZero(layers)}",
    "caldo ${if (warmUp.isEmpty()) "-" else warmUp.joinToString("; ")}",
    "shader provate=$shadersTried rifiutate=${if (shadersRefused.isEmpty()) "-" else shadersRefused.joinToString(",")}",
    "profilo ${if (tuning.isEmpty()) "di serie" else GalleryGlassLaunch.tuneText(tuning)}",
  )

  /** La riga di logcat: tutto in una, perché si cerchi con un `grep`. */
  fun logLine(key: String): String = "STATO $key | " + lines().joinToString(" | ")

  companion object {
    /** Quello che la diagnostica dice adesso, con [requested] il vetro chiesto dove la pagina si compone. */
    fun read(requested: FluidGlassModel): MaterialStatus {
      val verdicts = FluidGlassDiagnostics.shaderVerdicts
      return MaterialStatus(
        requested = requested,
        realApi = Build.VERSION.SDK_INT,
        simulatedApi = FluidGlassDiagnostics.simulatedApiLevel,
        warmUpEnabled = FluidGlassDiagnostics.warmUpEnabled,
        diagnosticsOn = FluidGlassDiagnostics.enabled,
        surfaces = FluidGlassDiagnostics.liveSurfaces,
        choices = FluidGlassDiagnostics.liveModelChoices,
        nodes = FluidGlassDiagnostics.liveNodes,
        layers = FluidGlassDiagnostics.liveLayers,
        warmUp = FluidGlassDiagnostics.warmUp.map { it.toString() },
        shadersTried = verdicts.size,
        shadersRefused = verdicts.filterValues { it != null }.keys.sorted(),
        tuning = FluidGlassDiagnostics.liquidProfileTuning,
      )
    }

    private fun onOff(value: Boolean) = if (value) "on" else "off"

    private fun <K : Enum<K>> nonZero(values: Map<K, Int>): String =
      values.filterValues { it != 0 }.entries.joinToString(" ") { "${it.key.name}=${it.value}" }.ifEmpty { "-" }
  }
}

/**
 * Le righe della pagina: ognuna va in logcat (tag [MaterialLogTag]) e le ultime anche nel riquadro. I riquadri
 * degli elementi ([bounds]) vanno solo in logcat, una volta per elemento e quando cambiano.
 */
@Stable
internal class MaterialReport {
  val lines = mutableStateListOf<String>()
  private val boundsSeen = HashMap<String, String>()

  fun line(text: String) {
    Log.i(MaterialLogTag, text)
    lines += text
    while (lines.size > MaxLines) lines.removeAt(0)
  }

  fun bounds(id: String, text: String) {
    if (boundsSeen[id] == text) return
    boundsSeen[id] = text
    Log.i(MaterialLogTag, text)
  }

  private companion object {
    const val MaxLines = 4
  }
}

/**
 * Scrive in logcat, con il nome dell'elemento e il vetro della colonna, il riquadro dell'elemento in pixel della
 * finestra: è ciò che serve per ritagliare dallo `screencap` il bordo e l'angolo a 6x, senza indovinare.
 */
@Composable
internal fun Modifier.reported(report: MaterialReport, config: MaterialConfig, name: String): Modifier {
  val model = LocalFluidGlassModel.current.name.lowercase()
  return onGloballyPositioned { coordinates ->
    val r = coordinates.boundsInWindow()
    report.bounds(
      "${config.scene.key}/$model/$name",
      "RIQUADRO ${config.key} modello=$model nome=$name x=${r.left.roundToInt()} y=${r.top.roundToInt()}" +
        " w=${r.width.roundToInt()} h=${r.height.roundToInt()}",
    )
  }
}

/**
 * Lo stato, letto una volta al secondo: a schermo nel riquadro, e in logcat (`STATO …`) quando cambia. Dopo due
 * secondi una riga `PRONTO`: la scena si è posata e si può fotografare.
 */
@Composable
internal fun rememberMaterialStatus(config: MaterialConfig, report: MaterialReport): () -> MaterialStatus? {
  val requested = LocalFluidGlassModel.current
  var status by remember { mutableStateOf<MaterialStatus?>(null) }
  LaunchedEffect(config, requested) {
    var last = ""
    var ticks = 0
    while (true) {
      val now = MaterialStatus.read(requested)
      status = now
      val line = now.logLine(config.key)
      if (line != last) {
        Log.i(MaterialLogTag, line)
        last = line
      }
      if (ticks == 2) report.line("PRONTO ${config.key}")
      ticks++
      delay(1_000L)
    }
  }
  return { status }
}

/**
 * Il riquadro di stato, sopra la scena. Nero e bianco fissi, come in «Misure»: deve leggersi su ogni fondo, chiaro
 * e scuro. `material.hud off` lo toglie per le fotografie; il tocco apre il pannello delle scelte.
 */
@Composable
internal fun MaterialHud(
  config: MaterialConfig,
  report: MaterialReport,
  status: () -> MaterialStatus?,
  onOpenPanel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .widthIn(max = 640.dp)
      .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
      .clickable(onClick = onOpenPanel)
      .padding(8.dp)
      .testTag("material-hud"),
  ) {
    Row {
      HudText("MATERIALE ${config.scene.title} · ${config.key}", Color(0xFFFFD60A))
    }
    status()?.lines()?.forEach { HudText(it, Color.White) }
    report.lines.forEach { HudText(it, Color(0xFFB0B0B8)) }
    HudText("tocca qui per il pannello · material.hud off per le foto", Color(0xFF8E8E93))
  }
}

@Composable
private fun HudText(text: String, color: Color) {
  Text(text = text, color = color, fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace)
}
