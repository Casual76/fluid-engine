package dev.antigravity.fluidengine.sample.material

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidPickerOption
import dev.antigravity.fluidengine.ui.fluid.FluidPickerRow
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/** Il tag di logcat della pagina «Materiale» e degli interruttori della galleria: `adb logcat -s FluidMateriale`. */
internal const val MaterialLogTag = "FluidMateriale"

/**
 * Gli interruttori del vetro che valgono per tutta la galleria, nella sola build di debug: quale vetro chiede la
 * radice, quale Android il vetro crede di avere, il riscaldamento, le manopole del profilo. Servono alla sessione
 * S2 (piano 4.5) per guardare **ogni** pagina con `Classic` forzato o con il livello simulato, e alla pagina
 * «Materiale» per le varianti delle tavole.
 *
 * Vive sopra la chiave che ricompone tutta la galleria ([epoch]): il livello simulato e le manopole valgono per ciò
 * che entra in composizione **dopo** (una superficie sceglie il vetro e il profilo quando nasce), quindi chi li
 * cambia fa rinascere tutto, tranne questo stato, la pila delle pagine e le impostazioni. Il vetro chiesto
 * ([model]) non ne ha bisogno: la scelta del modello lo legge come chiave.
 *
 * Il livello, il riscaldamento e le manopole stanno nell'engine (`FluidGlassDiagnostics`) per tutto il processo: qui
 * ce n'è solo la copia che la UI osserva.
 */
@Stable
internal class GalleryGlassSwitches(private val modelState: MutableState<FluidGlassModel>) {

  /** Il vetro chiesto alla radice della galleria (`LocalFluidGlassModel`). */
  var model: FluidGlassModel
    get() = modelState.value
    set(value) {
      if (value != modelState.value) {
        modelState.value = value
        Log.i(MaterialLogTag, "GALLERIA model=${value.name.lowercase()} (a mano)")
      }
    }

  /** Cresce a ogni cambio che chiede alla galleria di rinascere: è la chiave sopra il tema. */
  var epoch by mutableIntStateOf(0)
    private set

  var apiLevel by mutableStateOf(FluidGlassDiagnostics.simulatedApiLevel)
    private set

  var warmUp by mutableStateOf(FluidGlassDiagnostics.warmUpEnabled)
    private set

  var tuning by mutableStateOf(FluidGlassDiagnostics.liquidProfileTuning)
    private set

  /**
   * La scena che la pagina «Materiale» mostra quando la si è cambiata a mano. Sta qui, sopra [epoch], perché
   * cambiare una manopola fa rinascere anche la pagina, che deve tornare dov'era.
   */
  var materialConfig by mutableStateOf<MaterialConfig?>(null)

  /** Il pannello delle scelte della pagina «Materiale» aperto: per la stessa ragione di [materialConfig]. */
  var materialPanelOpen by mutableStateOf(false)

  /** Simula [level] (`null`: il livello vero) e fa rinascere la galleria. Falso se l'engine non l'ha accettato. */
  fun simulate(context: Context, level: Int?): Boolean {
    val applied = FluidGlassDiagnostics.simulateApiLevel(context, level)
    apiLevel = FluidGlassDiagnostics.simulatedApiLevel
    Log.i(MaterialLogTag, "GALLERIA api=${level ?: "off"} applicato=${if (applied) "si" else "no"} (a mano)")
    if (applied) epoch++
    return applied
  }

  fun switchWarmUp(on: Boolean) {
    FluidGlassDiagnostics.warmUpEnabled = on
    warmUp = FluidGlassDiagnostics.warmUpEnabled
    Log.i(MaterialLogTag, "GALLERIA warmup=${if (on) "on" else "off"} (a mano)")
  }

  /** Mette le manopole [knobs] (vuote: il profilo di serie) e fa rinascere la galleria. */
  fun tune(context: Context, knobs: Map<String, String>): Boolean {
    val applied = FluidGlassDiagnostics.tuneLiquidProfile(context, knobs)
    tuning = FluidGlassDiagnostics.liquidProfileTuning
    Log.i(MaterialLogTag, tuneLine(knobs, tuning, applied, byHand = true))
    if (applied) epoch++
    return applied
  }

  companion object {
    /**
     * Applica [launch] all'avvio dell'attività, **prima** del contenuto: livello, riscaldamento e manopole valgono
     * per ciò che entra in composizione dopo. Una riga in logcat dice che cosa è stato preso.
     */
    fun applyAtLaunch(context: Context, launch: GalleryGlassLaunch) {
      if (launch.isEmpty) return
      val parts = ArrayList<String>()
      launch.model?.let { parts += "model=${it.name.lowercase()}" }
      launch.api?.let { level ->
        val wanted = level.takeIf { it != GalleryGlassLaunch.ApiOff }
        val applied = FluidGlassDiagnostics.simulateApiLevel(context, wanted)
        parts += "api=${wanted ?: "off"} applicato=${if (applied) "si" else "no"}"
      }
      launch.warmUp?.let { on ->
        FluidGlassDiagnostics.warmUpEnabled = on
        parts += "warmup=${if (on) "on" else "off"}"
      }
      Log.i(MaterialLogTag, "GALLERIA ${parts.joinToString(" ")}".trimEnd())
      launch.tune?.let { knobs ->
        val applied = FluidGlassDiagnostics.tuneLiquidProfile(context, knobs)
        Log.i(MaterialLogTag, tuneLine(knobs, FluidGlassDiagnostics.liquidProfileTuning, applied, byHand = false))
      }
    }

    /** `PROFILO chiesto=… preso=… scartato=…`: la riga che dice quali manopole l'engine ha preso. */
    fun tuneLine(asked: Map<String, String>, taken: Map<String, String>, applied: Boolean, byHand: Boolean): String {
      val dropped = asked.keys.map { it.trim().lowercase() }.filter { it !in taken }
      return "PROFILO chiesto=${GalleryGlassLaunch.tuneText(asked)} preso=${GalleryGlassLaunch.tuneText(taken)}" +
        " scartato=${if (dropped.isEmpty()) "-" else dropped.joinToString(",")}" +
        (if (applied) "" else " NON APPLICATO (app non debuggable)") +
        (if (byHand) " (a mano)" else "")
    }
  }
}

/** Gli interruttori della galleria; nulli fuori dall'attività della galleria (le prove che compongono un pezzo solo). */
internal val LocalGalleryGlassSwitches = staticCompositionLocalOf<GalleryGlassSwitches?> { null }

/** Apre una pagina della galleria: lo fornisce la galleria, perché le Impostazioni portino alla pagina «Materiale». */
internal val LocalGalleryPageOpener = staticCompositionLocalOf<((GalleryPage) -> Unit)?> { null }

/** Gli interruttori dell'attività, col vetro chiesto che sopravvive a una rotazione. */
@Composable
internal fun rememberGalleryGlassSwitches(launch: GalleryGlassLaunch): GalleryGlassSwitches {
  val model = rememberSaveable { mutableStateOf(launch.model ?: FluidGlassModel.Liquid) }
  return remember(model) { GalleryGlassSwitches(model) }
}

/** Vero nella build di debug: gli interruttori e la pagina esistono solo lì. */
internal fun Context.isDebuggableApp(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

/**
 * Gli interruttori a mano, nelle Impostazioni accanto all'aspetto: dove si cambia già il tema. Niente se la galleria
 * non li fornisce (build di rilascio, prove).
 */
@Composable
internal fun GalleryGlassSwitchesSection(modifier: Modifier = Modifier) {
  val switches = LocalGalleryGlassSwitches.current ?: return
  val context = LocalContext.current
  val open = LocalGalleryPageOpener.current
  Column(modifier = modifier.fillMaxWidth().testTag("gallery-glass-switches")) {
    FluidSectionHeader(
      title = "Vetro (solo debug)",
      detail = "Per le sessioni sul dispositivo: valgono per tutta la galleria. Gli stessi da adb: gallery.model, " +
        "gallery.api, gallery.warmup, gallery.tune.",
    )
    FluidListGroup(glass = true) {
      FluidPickerRow(
        title = "Vetro",
        subtitle = "gallery.model",
        selectedKey = switches.model,
        options = listOf(
          FluidPickerOption(FluidGlassModel.Liquid, "Nuovo (Liquid)", subtitle = "Il materiale di iOS 27, dove può esistere"),
          FluidPickerOption(FluidGlassModel.Classic, "Di prima (Classic)", subtitle = "Il vetro della 3.x, ovunque"),
        ),
        onSelected = { switches.model = it },
      )
      FluidListDivider()
      FluidPickerRow(
        title = "Android simulato",
        subtitle = "gallery.api · ricompone tutta la galleria",
        selectedKey = switches.apiLevel ?: GalleryGlassLaunch.ApiOff,
        options = listOf(
          FluidPickerOption(GalleryGlassLaunch.ApiOff, "Quello vero"),
          FluidPickerOption(32, "Android 12 (32)", subtitle = "RenderEffect sì, AGSL no"),
          FluidPickerOption(30, "Android 11 (30)", subtitle = "Né RenderEffect né AGSL"),
        ),
        onSelected = { level -> switches.simulate(context, level.takeIf { it != GalleryGlassLaunch.ApiOff }) },
      )
      FluidListDivider()
      FluidToggleRow(
        title = "Riscaldamento dei programmi",
        subtitle = "gallery.warmup · vale dalla prossima richiesta",
        checked = switches.warmUp,
        onCheckedChange = { switches.switchWarmUp(it) },
      )
      FluidListDivider()
      FluidListRow(
        title = "Manopole del profilo",
        subtitle = if (switches.tuning.isEmpty()) {
          "Profilo di serie · gallery.tune"
        } else {
          GalleryGlassLaunch.tuneText(switches.tuning)
        },
        onClick = open?.let { opener -> { opener(GalleryPage.Material) } },
      )
      FluidListDivider()
      FluidListRow(
        title = "Pagina «Materiale»",
        subtitle = "Classic accanto a Liquid, i sei fondi, le scene della sessione sul tablet",
        modifier = Modifier.testTag("gallery-open-material"),
        onClick = open?.let { opener -> { opener(GalleryPage.Material) } },
      )
    }
  }
}
