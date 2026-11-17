/*
 * LA MANOPOLA DI DEBUG DEL CONTENUTO CHE RESTA (piano «iOS in Android», F4A.5b).
 *
 * Nelle due clip a tempo vero della barra di Apple (219, il telefono a 4:44 e il render a 4:48) il «+» che resta mentre
 * la capsula di tre icone si chiude su di lui **si sfoca anche lui** fra ~67 e ~270 ms, e torna nitido; R7 diceva «si
 * sfoca solo ciò che cambia identità», misurato su altre clip. La regola la sceglie Alessio guardando le due varianti:
 *
 * - `barkeep` = `sharp` (com'era fino a F4A.5): il contenuto che resta nello stesso vetro resta nitido e fermo;
 * - `barkeep` = `soft` (**di serie**, scelta di Alessio del 17 novembre 2026: «morbido, come Apple»): il contenuto che
 * resta in un vetro che **cambia forma** (un gruppo che perde o guadagna un tasto)
 *   si ammorbidisce con la curva dello scambio (`GlassContentDefaults.KeptSoftness` del massimo) e torna nitido.
 *
 * La scelta D2 di Alessio resta in tutte e due: la stessa icona in **due vetri diversi** resta nitida e scorre (il suo
 * posto nasce in un vetro nuovo e non ha una forma di prima da cui ammorbidirsi).
 *
 * Come le manopole del velo e del giro, passa da `gallery.tune`, solo nelle build di debug: la porta unica è
 * `FluidGlassDiagnostics.tuneLiquidProfile`, che deve chiamare [FluidToolbarTuning.apply] accanto a
 * `GlassAdaptiveTuning.apply` (una riga in `glass/backdrop/`, fuori dal perimetro di F4A.5b: la propone il rapporto della
 * sessione). Senza manopola tutto è di serie.
 */
package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Che cosa fa il contenuto che resta in un vetro della barra che cambia forma (F4A.5b): le due varianti per Alessio. */
internal enum class FluidToolbarKeptContent {
  /** Nitido e fermo, com'era (R7 A3). */
  Sharp,

  /** Si ammorbidisce e torna nitido, come il «+» delle clip di Apple a tempo vero: di serie (scelta di Alessio, 17/11/2026). */
  Soft,
}

/** La manopola `barkeep` di `gallery.tune` (F4A.5b): vedi la nota in testa al file. */
internal object FluidToolbarTuning {

  /** Il nome della manopola, come lo scrive `gallery.tune`. */
  const val Knob: String = "barkeep"

  /** I valori che accetta. */
  val Values: List<String> = listOf("sharp", "soft")

  /**
   * La variante di adesso. È uno stato di Compose: le barre la leggono mentre si compongono, e una manopola nuova vale
   * dal cambio successivo, senza ricreare la pagina.
   */
  var keptContent: FluidToolbarKeptContent by mutableStateOf(FluidToolbarKeptContent.Soft)
    private set

  /**
   * Il valore di [knobs] per la manopola (nome e valore senza distinzione di maiuscole), puro: la variante, o null se la
   * manopola non c'è o il valore non si riconosce.
   */
  fun parse(knobs: Map<String, String>?): FluidToolbarKeptContent? {
    val raw = knobs?.entries?.firstOrNull { it.key.trim().lowercase() == Knob }?.value ?: return null
    return when (raw.trim().lowercase()) {
      "sharp" -> FluidToolbarKeptContent.Sharp
      "soft" -> FluidToolbarKeptContent.Soft
      else -> null
    }
  }

  /**
   * Prende la manopola da [knobs] e la applica; senza la manopola torna di serie. Restituisce la manopola presa (nome e
   * valore normalizzato), vuota se non c'era o se il suo valore non si riconosce (allora resta di serie).
   */
  fun apply(knobs: Map<String, String>?): Map<String, String> {
    val taken = parse(knobs)
    keptContent = taken ?: FluidToolbarKeptContent.Soft
    return if (taken == null) emptyMap() else mapOf(Knob to taken.name.lowercase())
  }
}
