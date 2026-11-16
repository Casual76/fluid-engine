package dev.antigravity.fluidengine.sample.assistant

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel

/** Il tag di logcat della pagina: `adb logcat -s FluidAssistente`. */
internal const val AssistantLogTag = "FluidAssistente"

/**
 * I fondi della pagina «Assistente» (piano «iOS in Android», F3.2b): ciò che passa dietro alle superfici di
 * `engine-ai-ui`. [key] è il valore di `assistant.fund`. Il chiaro e lo scuro sono i due estremi della sonda (i piccoli
 * girano, i grandi stanno ai loro pavimenti); la foto è il caso vero, colori e luci in mezzo.
 */
internal enum class AssistantFund(val key: String, val title: String) {
  Light("chiaro", "chiaro"),
  Dark("scuro", "scuro"),
  Photo("foto", "foto"),
  ;

  /** Il fondo dopo questo, in giro: chiaro → scuro → foto → chiaro. */
  fun next(): AssistantFund = entries[(ordinal + 1) % entries.size]
}

/**
 * Lo stato della card della pagina. [Answer] è la risposta finita coi chip: ferma, non chiede fotogrammi. [Working] è
 * l'assistente al lavoro, con lo stop annidato sulla card e il pallino che respira (quindi una pagina viva, apposta).
 */
internal enum class AssistantCardState(val key: String, val title: String) {
  Answer("risposta", "risposta"),
  Working("lavoro", "al lavoro"),
  ;

  fun next(): AssistantCardState = entries[(ordinal + 1) % entries.size]
}

/**
 * Ciò che fa **rinascere** la pagina: il tema, il vetro chiesto e lo stato della card. Il fondo e l'automatico vivono in
 * [AssistantLive]: cambiarli è proprio ciò che si guarda, e la pagina non rinasce.
 */
internal data class AssistantConfig(
  val dark: Boolean = false,
  val model: FluidGlassModel = FluidGlassModel.Liquid,
  val card: AssistantCardState = AssistantCardState.Answer,
) {
  /** Una riga leggibile, per logcat e per il sottotitolo. */
  val key: String
    get() = "tema ${if (dark) "scuro" else "chiaro"} · ${model.name} · card ${card.title}"
}

/**
 * Il fondo e il suo automatico. Il fondo si legge **in disegno** (cambiarlo non ricompone niente: le superfici restano
 * ferme e cambia ciò che hanno dietro, che è ciò che la sonda deve vedere). Il cambio lo fa solo [change], che conta i
 * passi per la riga `FONDO`.
 */
@Stable
internal class AssistantLive(fund: AssistantFund = AssistantFund.Light, autoMs: Long = 0L) {
  private var fundIndex by mutableIntStateOf(fund.ordinal)

  /** Quanti cambi di fondo ci sono stati. */
  var step by mutableIntStateOf(0)
    private set

  /** Ogni quanto l'automatico cambia il fondo, in ms; 0 = spento. */
  var autoMs by mutableLongStateOf(autoMs)

  val fund: AssistantFund get() = AssistantFund.entries[fundIndex]

  /** Il fondo dopo, in giro; [log] riceve il fondo nuovo e il passo. */
  fun change(log: (AssistantFund, Int) -> Unit = { _, _ -> }) {
    fundIndex = fund.next().ordinal
    step += 1
    log(fund, step)
  }
}

/** Le richieste di lancio della pagina, lette da [AssistantLaunch.parse]. */
internal data class AssistantStart(
  val fund: AssistantFund = AssistantFund.Light,
  val autoMs: Long = 0L,
)

internal object AssistantLaunch {
  const val ExtraFund = "assistant.fund"
  const val ExtraTheme = "assistant.theme"
  const val ExtraModel = "assistant.model"
  const val ExtraAuto = "assistant.auto"
  const val ExtraCard = "assistant.card"

  /**
   * L'intervallo dell'automatico quando si chiede solo «acceso»: lascia finire il giro di un piccolo (fino a 450 ms coi
   * tempi di serie) e lo scurimento di un grande, e guardarli fermi prima del fondo dopo.
   */
  const val DefaultAutoMs = 2_500L

  private val truthy = setOf("true", "1", "on", "yes", "si", "sì", "acceso")
  private val falsy = setOf("false", "0", "off", "no", "spento")

  /**
   * Gli extra di lancio (solo nella build di debug). Un valore sconosciuto lascia quello di serie: una pagina che non parte
   * per un refuso in un comando di prova è peggio di una che parte di serie. [inheritedDark] è il tema della galleria,
   * [inherited] il vetro che chiede (`gallery.model`).
   */
  fun parse(
    inheritedDark: Boolean,
    inherited: FluidGlassModel,
    extra: (String) -> String?,
  ): Pair<AssistantConfig, AssistantStart> {
    fun value(key: String): String? = extra(key)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    val fund = value(ExtraFund)?.let { name ->
      AssistantFund.entries.firstOrNull { it.key == name || it.name.lowercase() == name }
    } ?: AssistantFund.Light
    val dark = when (value(ExtraTheme)) {
      "scuro", "dark" -> true
      "chiaro", "light" -> false
      else -> inheritedDark
    }
    val model = when (value(ExtraModel)) {
      "classic" -> FluidGlassModel.Classic
      "liquid" -> FluidGlassModel.Liquid
      else -> inherited
    }
    val card = value(ExtraCard)?.let { name ->
      AssistantCardState.entries.firstOrNull { it.key == name || it.name.lowercase() == name }
    } ?: AssistantCardState.Answer
    val auto = when (val text = value(ExtraAuto)) {
      null -> 0L
      in truthy -> DefaultAutoMs
      in falsy -> 0L
      else -> text.removeSuffix("ms").trim().toLongOrNull()?.let { if (it <= 0L) 0L else it.coerceIn(500L, 60_000L) } ?: 0L
    }
    return AssistantConfig(dark, model, card) to AssistantStart(fund = fund, autoMs = auto)
  }
}
