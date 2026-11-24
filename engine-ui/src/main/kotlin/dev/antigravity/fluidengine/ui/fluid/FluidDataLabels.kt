package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Le parole dei grafici da toccare e del quadrante (F8.6): la capsula dello scrubber dei grafici, la differenza fra due
 * punti e le frasi di [FluidGauge] per TalkBack. Di serie in italiano; [English] è la versione inglese completa. Un'app in
 * un'altra lingua ne dà una sua con [LocalFluidDataLabels].
 *
 * Sono a parte da [FluidLabels] perché i pacchetti di F8 corrono insieme: ognuno porta le sue parole senza toccare la
 * classe di tutti (come [FluidSpecialFieldLabels]).
 */
@Immutable
data class FluidDataLabels(
  // --- I grafici da toccare ---
  /** L'intervallo fra due punti scelti con due dita, nella capsula: «Gen – Mar». */
  val chartSpan: (from: String, to: String) -> String = { from, to -> "$from – $to" },
  /** La differenza con la variazione in percentuale: «+1,5 (+20 %)». */
  val chartDelta: (difference: String, percent: String) -> String = { difference, percent -> "$difference ($percent)" },

  // --- Il quadrante ---
  /** Il valore nel suo intervallo, per TalkBack: «18 %, da 0 a 100 %». */
  val gaugeValueOf: (value: String, min: String, max: String) -> String = { value, min, max -> "$value, da $min a $max" },
  /** Una soglia, per TalkBack: «limite 25 %». `label` è il nome della soglia dato dall'app, o [gaugeThreshold]. */
  val gaugeThresholdAt: (label: String, value: String) -> String = { label, value -> "$label $value" },
  /** Il nome di una soglia senza nome. */
  val gaugeThreshold: String = "soglia",
  /** Il valore ha superato una soglia: «oltre il limite». */
  val gaugeBeyond: (label: String) -> String = { "oltre: $it" },
) {
  companion object {
    /** Le stesse parole in inglese. */
    val English: FluidDataLabels = FluidDataLabels(
      chartSpan = { from, to -> "$from – $to" },
      chartDelta = { difference, percent -> "$difference ($percent)" },
      gaugeValueOf = { value, min, max -> "$value, from $min to $max" },
      gaugeThresholdAt = { label, value -> "$label $value" },
      gaugeThreshold = "threshold",
      gaugeBeyond = { "beyond: $it" },
    )
  }
}

/** Le parole di grafici da toccare e quadrante in scope: di serie quelle italiane. Vedi [FluidDataLabels]. */
val LocalFluidDataLabels: ProvidableCompositionLocal<FluidDataLabels> = staticCompositionLocalOf { FluidDataLabels() }
