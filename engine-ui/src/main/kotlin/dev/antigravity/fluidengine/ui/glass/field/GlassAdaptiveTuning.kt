/*
 * LE MANOPOLE DI DEBUG DEL GIRO (piano «iOS in Android», S3).
 *
 * Alessio sceglie i tempi del giro, l'attesa del filtro e le sue due soglie dalle riprese del tablet, non dalle tavole del
 * banco. Per riprendere le varianti servono manopole che la galleria passa con `gallery.tune`, come quelle del profilo:
 *
 * - `fliptime` = `ios26` (di serie) | `swift` | `slow`: i tempi del giro ([GlassFlipTimings]);
 * - `flipwait` = millisecondi, 20..2000 (di serie 200): la costante di tempo della media del filtro, cioè quanto il
 *   filtro aspetta prima di decidere ([GlassAdaptiveTimeConstantMillis]);
 * - `flipon` / `flipoff` = 0..1 (di serie 0,45 e 0,62): le due soglie del filtro, verso lo scuro e di ritorno al chiaro
 *   ([GlassAdaptiveDarkBelow], [GlassAdaptiveLightAbove]). Devono restare distanti (`flipon` sotto `flipoff`): una
 *   coppia rovesciata si scarta tutta e restano le soglie di serie.
 *
 * Non sono numeri del profilo (`GlassLiquidProfile`): il filtro e la legge del giro sono comportamento, non gusto del
 * materiale. Come il velo del bordo (`GlassScrollEdgeTuning`), passano dalla porta unica di
 * `FluidGlassDiagnostics.tuneLiquidProfile`, solo nelle build di debug, e valgono per le sonde e i vetri che nascono
 * dopo. Senza manopole tutto è di serie, al bit.
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.ui.fluid.GlassFlipTiming
import dev.antigravity.fluidengine.ui.fluid.GlassFlipTimings

/**
 * Le manopole del giro lette da una richiesta, pure: [timing], [waitMillis], [darkBelow], [lightAbove], e [taken], le
 * manopole prese coi valori normalizzati (quelle scartate non ci sono).
 */
@Immutable
internal data class GlassAdaptiveKnobs(
  val timing: GlassFlipTiming = GlassFlipTimings.Ios26,
  val waitMillis: Float = GlassAdaptiveTimeConstantMillis,
  val darkBelow: Float = GlassAdaptiveDarkBelow,
  val lightAbove: Float = GlassAdaptiveLightAbove,
  val taken: Map<String, String> = emptyMap(),
) {
  companion object {
    /** Il filtro e il giro di serie. */
    val Shipped: GlassAdaptiveKnobs = GlassAdaptiveKnobs()

    /**
     * Legge [knobs] (nomi e valori senza distinzione di maiuscole, numeri col punto o la virgola). Una manopola con un
     * valore che non si riconosce resta di serie e non compare in [taken].
     */
    fun parse(knobs: Map<String, String>?): GlassAdaptiveKnobs {
      if (knobs.isNullOrEmpty()) return Shipped
      fun raw(name: String): String? =
        knobs.entries.firstOrNull { it.key.trim().lowercase() == name }?.value?.trim()?.lowercase()
      fun number(name: String, range: ClosedFloatingPointRange<Float>): Float? =
        raw(name)?.replace(',', '.')?.toFloatOrNull()?.takeIf { it.isFinite() && it in range }

      val taken = LinkedHashMap<String, String>()
      val timing = raw(GlassAdaptiveTuning.FlipTimeKnob)?.let { name ->
        GlassFlipTimings.Named[name]?.also { taken[GlassAdaptiveTuning.FlipTimeKnob] = name }
      } ?: GlassFlipTimings.Ios26
      val wait = number(GlassAdaptiveTuning.FlipWaitKnob, GlassAdaptiveTuning.WaitRange)
        ?.also { taken[GlassAdaptiveTuning.FlipWaitKnob] = glassScrollEdgeKnobText(it) }
        ?: GlassAdaptiveTimeConstantMillis
      val on = number(GlassAdaptiveTuning.FlipOnKnob, 0f..1f)
      val off = number(GlassAdaptiveTuning.FlipOffKnob, 0f..1f)
      val darkBelow = on ?: GlassAdaptiveDarkBelow
      val lightAbove = off ?: GlassAdaptiveLightAbove
      // Le soglie devono restare distanti (il filtro lo pretende): una coppia rovesciata non si prende.
      val thresholdsHold = darkBelow < lightAbove
      if (thresholdsHold) {
        on?.let { taken[GlassAdaptiveTuning.FlipOnKnob] = glassScrollEdgeKnobText(it) }
        off?.let { taken[GlassAdaptiveTuning.FlipOffKnob] = glassScrollEdgeKnobText(it) }
      }
      return GlassAdaptiveKnobs(
        timing = timing,
        waitMillis = wait,
        darkBelow = if (thresholdsHold) darkBelow else GlassAdaptiveDarkBelow,
        lightAbove = if (thresholdsHold) lightAbove else GlassAdaptiveLightAbove,
        taken = taken,
      )
    }
  }

  /** Il filtro di una sonda nuova con queste manopole; di serie è quello di sempre. */
  fun filter(initialDark: Boolean): GlassAdaptiveFilter = GlassAdaptiveFilter(
    initialDark = initialDark,
    timeConstantMillis = waitMillis,
    darkBelow = darkBelow,
    lightAbove = lightAbove,
  )
}

/**
 * Le manopole del giro installate adesso (vedi l'intestazione del file). Le legge chi nasce: la sonda il suo filtro
 * ([filter]), lo stato adattivo i tempi del giro ([flipTiming]).
 */
internal object GlassAdaptiveTuning {
  const val FlipTimeKnob: String = "fliptime"
  const val FlipWaitKnob: String = "flipwait"
  const val FlipOnKnob: String = "flipon"
  const val FlipOffKnob: String = "flipoff"

  /** I millisecondi che `flipwait` accetta. */
  val WaitRange: ClosedFloatingPointRange<Float> = 20f..2000f

  /** Le manopole e i valori che accettano, come le elenca `FluidGlassDiagnostics.liquidProfileKnobs`. */
  val Knobs: Map<String, List<String>> = linkedMapOf(
    FlipTimeKnob to GlassFlipTimings.Named.keys.toList(),
    FlipWaitKnob to listOf("20..2000"),
    FlipOnKnob to listOf("0..1"),
    FlipOffKnob to listOf("0..1"),
  )

  @Volatile
  private var installed: GlassAdaptiveKnobs = GlassAdaptiveKnobs.Shipped

  /** I tempi del giro per un vetro che nasce adesso. */
  val flipTiming: GlassFlipTiming get() = installed.timing

  /** Il filtro per una sonda che nasce adesso. */
  fun filter(initialDark: Boolean): GlassAdaptiveFilter = installed.filter(initialDark)

  /** Installa le manopole di [knobs] (quelle che mancano tornano di serie) e restituisce quelle prese. */
  fun apply(knobs: Map<String, String>?): Map<String, String> {
    val parsed = GlassAdaptiveKnobs.parse(knobs)
    installed = parsed
    return parsed.taken
  }
}
