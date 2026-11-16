/*
 * LE MANOPOLE DI DEBUG DEL GIRO (piano «iOS in Android», S3).
 *
 * Alessio sceglie i tempi del giro, l'attesa del filtro e le sue due soglie dalle riprese del tablet, non dalle tavole del
 * banco. Per riprendere le varianti servono manopole che la galleria passa con `gallery.tune`, come quelle del profilo:
 *
 * - `fliptime` = `swift` (di serie dalla G1, scelta 1 di S3) | `ios26` | `slow`: i tempi del giro ([GlassFlipTimings]);
 * - `flipwait` = millisecondi, 20..2000 (di serie 200): la costante di tempo della media del filtro, cioè quanto il
 *   filtro aspetta prima di decidere ([GlassAdaptiveTimeConstantMillis]);
 * - `flipon` / `flipoff` = 0..1 (di serie 0,45 e 0,62): le due soglie del filtro, verso lo scuro e di ritorno al chiaro
 *   ([GlassAdaptiveDarkBelow], [GlassAdaptiveLightAbove]). Devono restare distanti (`flipon` sotto `flipoff`): una
 *   coppia rovesciata si scarta tutta e restano le soglie di serie.
 *
 * Dalla G1 (rilievi A-R3, B-R5, B-R10: scorrendo, barra e tab bar passano troppo tempo a girare), la scelta «cosa fa il
 * vetro mentre scorri»:
 *
 * - `flipdwell` = millisecondi, 0..3000 (di serie 400): la permanenza minima dopo un giro
 *   ([GlassAdaptiveMinDwellMillis]); a 1000 nessun vetro gira due volte in meno di un secondo;
 * - `flipscroll` = `live` (di serie: decide mentre scorre) | `rest` (mentre lo sfondo scorre più svelto di
 *   [GlassFlipRestSpeedDp] il lato resta quello dell'ultimo riposo, e si decide quando rallenta o si ferma) | `ahead`
 *   (la sonda legge la fascia che sta **entrando** sotto il vetro, [GlassFlipAheadMillis] avanti nel verso dello
 *   scorrimento, invece di quella che sta uscendo);
 * - `flipby` = `luminance` (di serie: la luminanza media) | `coverage` (la quota della miniatura più scura della soglia
 *   [GlassFlipCoverageDarkLuma]: R4 A6, Apple gira quando lo scuro copre il 70–85% del vetro);
 * - `flipcover` = 0,55..0,95 (di serie 0,75): con `flipby=coverage`, la quota di scuro a cui gira verso lo scuro; torna
 *   al chiaro quando lo scuro scende a `1 − flipcover`, l'uscita simmetrica all'entrata.
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
  val timing: GlassFlipTiming = GlassFlipTimings.Shipped,
  val waitMillis: Float = GlassAdaptiveTimeConstantMillis,
  val darkBelow: Float = GlassAdaptiveDarkBelow,
  val lightAbove: Float = GlassAdaptiveLightAbove,
  val taken: Map<String, String> = emptyMap(),
  /** La permanenza minima dopo un giro (`flipdwell`, G1). */
  val dwellMillis: Long = GlassAdaptiveMinDwellMillis,
  /** Che cosa fa il vetro mentre lo sfondo scorre (`flipscroll`, G1). */
  val scroll: GlassFlipScroll = GlassFlipScroll.Live,
  /** Su che cosa decide il filtro (`flipby`, G1). */
  val by: GlassFlipBy = GlassFlipBy.Luminance,
  /** Con [GlassFlipBy.Coverage], la quota di scuro a cui gira (`flipcover`, G1). */
  val cover: Float = GlassFlipCoverDefault,
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
      } ?: GlassFlipTimings.Shipped
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
      val dwell = number(GlassAdaptiveTuning.FlipDwellKnob, GlassAdaptiveTuning.DwellRange)
        ?.also { taken[GlassAdaptiveTuning.FlipDwellKnob] = glassScrollEdgeKnobText(it) }
        ?.toLong() ?: GlassAdaptiveMinDwellMillis
      val scroll = raw(GlassAdaptiveTuning.FlipScrollKnob)?.let { name ->
        GlassFlipScroll.entries.firstOrNull { it.knob == name }?.also { taken[GlassAdaptiveTuning.FlipScrollKnob] = name }
      } ?: GlassFlipScroll.Live
      val by = raw(GlassAdaptiveTuning.FlipByKnob)?.let { name ->
        GlassFlipBy.entries.firstOrNull { it.knob == name }?.also { taken[GlassAdaptiveTuning.FlipByKnob] = name }
      } ?: GlassFlipBy.Luminance
      val cover = number(GlassAdaptiveTuning.FlipCoverKnob, GlassAdaptiveTuning.CoverRange)
        ?.also { taken[GlassAdaptiveTuning.FlipCoverKnob] = glassScrollEdgeKnobText(it) }
        ?: GlassFlipCoverDefault
      return GlassAdaptiveKnobs(
        timing = timing,
        waitMillis = wait,
        darkBelow = if (thresholdsHold) darkBelow else GlassAdaptiveDarkBelow,
        lightAbove = if (thresholdsHold) lightAbove else GlassAdaptiveLightAbove,
        taken = taken,
        dwellMillis = dwell,
        scroll = scroll,
        by = by,
        cover = cover,
      )
    }
  }

  /**
   * Il filtro di una sonda nuova con queste manopole; di serie è quello di sempre. Con [GlassFlipBy.Coverage] il filtro
   * riceve la quota di chiaro (`1 −` quota di scuro, [glassFlipValue]) e gira sotto `1 − cover`, torna sopra `cover`.
   */
  fun filter(initialDark: Boolean): GlassAdaptiveFilter = GlassAdaptiveFilter(
    initialDark = initialDark,
    timeConstantMillis = waitMillis,
    darkBelow = if (by == GlassFlipBy.Coverage) 1f - cover else darkBelow,
    lightAbove = if (by == GlassFlipBy.Coverage) cover else lightAbove,
    minDwellMillis = dwellMillis,
  )
}

/** Che cosa fa il vetro mentre lo sfondo scorre (`flipscroll`, G1). */
internal enum class GlassFlipScroll(val knob: String) {
  /** Di serie: decide mentre scorre, come ogni altro cambio dello sfondo. */
  Live("live"),

  /** Mentre lo sfondo scorre più svelto di [GlassFlipRestSpeedDp] il lato resta quello dell'ultimo riposo. */
  Rest("rest"),

  /** Legge la fascia che sta entrando sotto il vetro, [GlassFlipAheadMillis] avanti nel verso dello scorrimento. */
  Ahead("ahead"),
}

/** Su che cosa decide il filtro (`flipby`, G1). */
internal enum class GlassFlipBy(val knob: String) {
  /** Di serie: la luminanza media della miniatura. */
  Luminance("luminance"),

  /** La quota della miniatura più scura di [GlassFlipCoverageDarkLuma] (R4 A6). */
  Coverage("coverage"),
}

/**
 * Con `flipscroll=rest`: sopra questa velocità del contenuto (dp al secondo) il lato si trattiene. Due dp a
 * fotogramma a 60 Hz: sotto si sta leggendo o la lista si sta fermando, sopra si sta scorrendo (la scena `scorrimento`
 * va a 300 dp/s, il caso veloce a 600).
 */
internal const val GlassFlipRestSpeedDp: Float = 120f

/**
 * Con `flipscroll=ahead`: quanto avanti guarda la sonda, in ms di scorrimento alla velocità di adesso. È il ritardo
 * della decisione (la media del filtro e la lettura al più ogni 120 ms) più la prima metà del giro.
 */
internal const val GlassFlipAheadMillis: Float = 300f

/** Con `flipscroll=ahead`: lo spostamento della lettura non va oltre questo multiplo dell'altezza del vetro. */
internal const val GlassFlipAheadMaxHeights: Float = 2f

/** Con `flipscroll=rest|ahead`: quanto dopo l'ultimo passo dello scorrimento il contenuto si considera fermo, ms. */
internal const val GlassFlipQuietMillis: Long = 120L

/** Con `flipby=coverage`: la luma sotto cui un punto della miniatura conta come scuro (la soglia del giro di serie). */
internal const val GlassFlipCoverageDarkLuma: Float = GlassAdaptiveDarkBelow

/** Con `flipby=coverage`, di serie: gira quando lo scuro copre il 75% (R4 A6: 70–85%). */
internal const val GlassFlipCoverDefault: Float = 0.75f

/**
 * Il numero che il filtro riceve da una lettura [stats] secondo [by]: la luminanza media, o la quota di chiaro
 * (`1 −` [GlassBackdropStats.darkShare]). Una lettura senza quota (un doppio dei test) dà la luminanza. Pura.
 */
internal fun glassFlipValue(stats: GlassBackdropStats, by: GlassFlipBy): Float =
  if (by == GlassFlipBy.Coverage && stats.darkShare.isFinite()) 1f - stats.darkShare.coerceIn(0f, 1f) else stats.luminance

/**
 * Le manopole del giro installate adesso (vedi l'intestazione del file). Le legge chi nasce: la sonda il suo filtro
 * ([filter]), lo stato adattivo i tempi del giro ([flipTiming]).
 */
internal object GlassAdaptiveTuning {
  const val FlipTimeKnob: String = "fliptime"
  const val FlipWaitKnob: String = "flipwait"
  const val FlipOnKnob: String = "flipon"
  const val FlipOffKnob: String = "flipoff"
  const val FlipDwellKnob: String = "flipdwell"
  const val FlipScrollKnob: String = "flipscroll"
  const val FlipByKnob: String = "flipby"
  const val FlipCoverKnob: String = "flipcover"

  /** I millisecondi che `flipdwell` accetta. */
  val DwellRange: ClosedFloatingPointRange<Float> = 0f..3000f

  /** Le quote che `flipcover` accetta: sopra la metà, perché entrata e uscita restino distinte. */
  val CoverRange: ClosedFloatingPointRange<Float> = 0.55f..0.95f

  /** I millisecondi che `flipwait` accetta. */
  val WaitRange: ClosedFloatingPointRange<Float> = 20f..2000f

  /** Le manopole e i valori che accettano, come le elenca `FluidGlassDiagnostics.liquidProfileKnobs`. */
  val Knobs: Map<String, List<String>> = linkedMapOf(
    FlipTimeKnob to GlassFlipTimings.Named.keys.toList(),
    FlipWaitKnob to listOf("20..2000"),
    FlipOnKnob to listOf("0..1"),
    FlipOffKnob to listOf("0..1"),
    FlipDwellKnob to listOf("0..3000"),
    FlipScrollKnob to GlassFlipScroll.entries.map { it.knob },
    FlipByKnob to GlassFlipBy.entries.map { it.knob },
    FlipCoverKnob to listOf("0.55..0.95"),
  )

  @Volatile
  private var installed: GlassAdaptiveKnobs = GlassAdaptiveKnobs.Shipped

  /** I tempi del giro per un vetro che nasce adesso. */
  val flipTiming: GlassFlipTiming get() = installed.timing

  /** Il filtro per una sonda che nasce adesso. */
  fun filter(initialDark: Boolean): GlassAdaptiveFilter = installed.filter(initialDark)

  /** Che cosa fa una sonda che nasce adesso mentre lo sfondo scorre (G1). */
  val scroll: GlassFlipScroll get() = installed.scroll

  /** Su che cosa decide una sonda che nasce adesso (G1). */
  val by: GlassFlipBy get() = installed.by

  /** Installa le manopole di [knobs] (quelle che mancano tornano di serie) e restituisce quelle prese. */
  fun apply(knobs: Map<String, String>?): Map<String, String> {
    val parsed = GlassAdaptiveKnobs.parse(knobs)
    installed = parsed
    return parsed.taken
  }
}
