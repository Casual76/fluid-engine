package dev.antigravity.fluidengine.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/**
 * Il registro aptico (3.0): cosa e' stato chiesto al vibratore, e quando.
 *
 * Esiste per una domanda sola, che sul telefono non ha risposta a occhio: **"una vibrazione per
 * momento"** e' vero? Un tocco puo' far parlare tre componenti nello stesso istante (il tasto, il
 * pannello che apre, il livello del modale), e la rete dei 70 ms ([FluidHapticPatterns.coalescible])
 * ne lascia suonare uno. Dal polpastrello non si distingue "ne e' arrivato uno" da "ne sono arrivati
 * tre e due sono stati fusi". Qui si'.
 *
 * E' un **decoratore**: [TracingFluidHaptics] inoltra ogni chiamata al motore vero, tale e quale, e
 * ne tiene una copia in una lista limitata e osservabile. Non cambia cosa suona, non cambia il
 * momento in cui suona, e non costa niente a chi non lo installa: senza [ProvideFluidHapticTrace]
 * nessuno lo costruisce.
 *
 * **Cosa dice dell'esito** ([FluidHapticTraceEntry.outcome]) e' una stima, e lo dice: il registro non
 * puo' chiedere al motore vero se un evento e' suonato (l'interfaccia [FluidHaptics] non risponde),
 * quindi rifa' da solo le due reti che il motore di serie ([FluidHapticsImpl]) applica, con le stesse
 * costanti e un orologio suo. Con un motore sotto che non e' quello (o spento dall'interruttore
 * dell'app, o senza vibratore) la stima dice cosa *avrebbe* fatto il motore di serie.
 */
@Stable
class FluidHapticTrace(
  /** Quante voci tiene: le piu' vecchie escono. La grana ne riempie in fretta, quindi e' largo. */
  val capacity: Int = DefaultCapacity,
  private val clock: () -> Long = ::monotonicMillis,
) {
  init {
    require(capacity > 0) { "capacity must be positive: $capacity" }
  }

  private val gate = FluidHapticTraceGate()
  private var stored by mutableStateOf<List<FluidHapticTraceEntry>>(emptyList())

  /** Falso: il registro smette di scrivere (e solo quello, il motore vero suona lo stesso). */
  var recording: Boolean by mutableStateOf(true)

  /** Le voci, dalla piu' vecchia alla piu' nuova. Una lettura in composizione si aggiorna da sola. */
  val entries: List<FluidHapticTraceEntry> get() = stored

  /** Un evento del vocabolario e' stato chiesto adesso. */
  @Synchronized
  fun recordEvent(event: FluidHapticEvent) {
    if (!recording) return
    val now = clock()
    append(FluidHapticTraceEntry(atMillis = now, event = event, outcome = gate.event(event, now)))
  }

  /** Un granello della grana e' stato chiesto adesso. */
  @Synchronized
  fun recordGrain(intensity: Float, style: FluidHapticGrainStyle) {
    if (!recording) return
    val now = clock()
    append(
      FluidHapticTraceEntry(
        atMillis = now,
        event = null,
        grainIntensity = intensity,
        grainStyle = style,
        outcome = gate.grain(style, now),
      ),
    )
  }

  /** Svuota la lista. Le reti stimate no: il motore vero non si e' azzerato. */
  @Synchronized
  fun clear() {
    stored = emptyList()
  }

  /** Le righe da mostrare, le piu' nuove per prime: vedi [fluidHapticTraceRows]. */
  fun rows(maxRows: Int = DefaultRows): List<FluidHapticTraceRow> = fluidHapticTraceRows(entries, maxRows)

  /** [delegate] guardato da questo registro. Se e' gia' guardato da questo stesso registro, e' lui. */
  fun wrap(delegate: FluidHaptics): FluidHaptics =
    if (delegate is TracingFluidHaptics && delegate.trace === this) delegate else TracingFluidHaptics(delegate, this)

  private fun append(entry: FluidHapticTraceEntry) {
    val current = stored
    val keep = if (current.size >= capacity) current.subList(current.size - capacity + 1, current.size) else current
    stored = ArrayList<FluidHapticTraceEntry>(keep.size + 1).apply {
      addAll(keep)
      add(entry)
    }
  }

  companion object {
    const val DefaultCapacity = 240
    const val DefaultRows = 20
  }
}

/** Quanto il motore ne ha fatto, per quanto se ne sa da fuori. */
enum class FluidHapticTraceOutcome {
  /** Suona. */
  Played,

  /**
   * Fuso: un altro evento di controllo e' suonato meno di [FluidHapticPatterns.CoalesceWindowMillis]
   * prima, e questo e' lo stesso momento detto da un altro componente. Non suona.
   */
  Merged,

  /**
   * Scartato: troppo vicino al precedente dello stesso genere (il pavimento dei tick continui, o
   * quello della grana). Non suona.
   */
  Dropped,
}

/**
 * Una voce del registro: un evento del vocabolario ([event]) o un granello di grana ([event] nullo,
 * con [grainIntensity] e [grainStyle]).
 *
 * @property atMillis l'istante, sull'orologio del registro (monotono, di origine qualunque: si
 *   leggono solo le differenze).
 */
@Immutable
data class FluidHapticTraceEntry(
  val atMillis: Long,
  val event: FluidHapticEvent?,
  val grainIntensity: Float = 0f,
  val grainStyle: FluidHapticGrainStyle? = null,
  val outcome: FluidHapticTraceOutcome = FluidHapticTraceOutcome.Played,
) {
  val isGrain: Boolean get() = event == null
}

/**
 * Una riga di registro leggibile: un evento, o una **fila** di granelli consecutivi dello stesso stile
 * (la grana ne fa decine al secondo: una riga ciascuno coprirebbe il resto).
 *
 * @property label il nome: l'evento (`PressDown`) o `grana LibrePods` / `grana Fluid`.
 * @property detail la nota sotto il nome, se serve: perche' non suona, quanti granelli, quanto forti.
 * @property count quante voci contiene: 1 per un evento, il numero di granelli per una fila.
 * @property deltaMillis i millisecondi dall'ultima voce della riga precedente (la piu' vecchia).
 *   Nullo per la prima di tutte. Sotto [FluidHapticPatterns.CoalesceWindowMillis] e' lo stesso momento.
 * @property outcome per una fila: [FluidHapticTraceOutcome.Played] se almeno un granello suona.
 * @property atMillis l'istante della prima voce della riga.
 */
@Immutable
data class FluidHapticTraceRow(
  val label: String,
  val detail: String?,
  val count: Int,
  val deltaMillis: Long?,
  val outcome: FluidHapticTraceOutcome,
  val atMillis: Long,
) {
  /** Il nome con il conteggio, per una riga sola: `grana LibrePods ×14`. */
  val text: String get() = if (count > 1) "$label ×$count" else label

  /** Vero se la riga comincia un momento nuovo (almeno la finestra della rete dei 70 ms dopo la precedente). */
  val startsMoment: Boolean
    get() = deltaMillis == null || deltaMillis >= FluidHapticPatterns.CoalesceWindowMillis
}

/** Due granelli piu' lontani di cosi' non sono la stessa fila. */
const val FluidHapticTraceGrainRunGapMillis = 150L

/**
 * Le righe di [entries] (dalla piu' vecchia alla piu' nuova), **le piu' nuove per prime** e al piu'
 * [maxRows]. Pura: la prova sta sul computer.
 *
 * I granelli consecutivi dello stesso stile, a non piu' di [FluidHapticTraceGrainRunGapMillis] l'uno
 * dall'altro, diventano una riga sola. I ritardi si calcolano su tutte le voci, non solo su quelle che
 * entrano in [maxRows]: la riga piu' vecchia che si vede sa comunque da cosa dista.
 */
fun fluidHapticTraceRows(entries: List<FluidHapticTraceEntry>, maxRows: Int = 20): List<FluidHapticTraceRow> {
  val rows = ArrayList<FluidHapticTraceRow>()
  var previousEnd: Long? = null
  var index = 0
  while (index < entries.size) {
    val first = entries[index]
    if (first.event != null) {
      rows += FluidHapticTraceRow(
        label = first.event.name,
        detail = when (first.outcome) {
          FluidHapticTraceOutcome.Played -> null
          FluidHapticTraceOutcome.Merged -> "fuso: stesso momento del colpo prima"
          FluidHapticTraceOutcome.Dropped -> "scartato: troppo vicino al precedente"
        },
        count = 1,
        deltaMillis = previousEnd?.let { first.atMillis - it },
        outcome = first.outcome,
        atMillis = first.atMillis,
      )
      previousEnd = first.atMillis
      index++
      continue
    }

    // Una fila di granelli: finche' sono dello stesso stile e fitti.
    var end = index
    var played = 0
    var dropped = 0
    var weakest = Float.MAX_VALUE
    var strongest = 0f
    while (end < entries.size) {
      val entry = entries[end]
      val continues = entry.isGrain && entry.grainStyle == first.grainStyle &&
        (end == index || entry.atMillis - entries[end - 1].atMillis <= FluidHapticTraceGrainRunGapMillis)
      if (!continues) break
      if (entry.outcome == FluidHapticTraceOutcome.Played) played++ else dropped++
      weakest = minOf(weakest, entry.grainIntensity)
      strongest = maxOf(strongest, entry.grainIntensity)
      end++
    }
    val count = end - index
    val last = entries[end - 1]
    val parts = ArrayList<String>()
    parts += "$count in ${last.atMillis - first.atMillis} ms"
    if (dropped > 0) parts += "$dropped scartati"
    // L'intensita' conta solo per la grana dell'engine: il tick di LibrePods e' sempre la costante.
    if (first.grainStyle == FluidHapticGrainStyle.Fluid) {
      parts += "forza ${(weakest * 100f).roundToInt()}-${(strongest * 100f).roundToInt()}%"
    }
    rows += FluidHapticTraceRow(
      label = "grana ${first.grainStyle?.name ?: "?"}",
      detail = parts.joinToString(", "),
      count = count,
      deltaMillis = previousEnd?.let { first.atMillis - it },
      outcome = if (played > 0) FluidHapticTraceOutcome.Played else FluidHapticTraceOutcome.Dropped,
      atMillis = first.atMillis,
    )
    previousEnd = last.atMillis
    index = end
  }
  return rows.takeLast(maxRows.coerceAtLeast(0)).asReversed()
}

/**
 * Le due reti del motore di serie, rifatte con le sue costanti (vedi [FluidHapticsImpl.play] e
 * [FluidHapticsImpl.grain]): per dire, di ogni voce, se il motore l'avrebbe lasciata suonare.
 */
internal class FluidHapticTraceGate {
  // Meta' del minimo, come nel motore: un orologio che parte da zero deve far passare il primo.
  private var lastDiscreteAt = Long.MIN_VALUE / 2
  private var lastContinuousAt = Long.MIN_VALUE / 2
  private var lastGrainAt = Long.MIN_VALUE / 2

  fun event(event: FluidHapticEvent, now: Long): FluidHapticTraceOutcome {
    // I tre colpi di un tasto che cresce vanno dritti alla costante di sistema, senza rete.
    if (event in FluidHapticPatterns.platformOnly) return FluidHapticTraceOutcome.Played
    if (event.continuous) {
      if (now - lastContinuousAt < FluidHapticsImpl.CONTINUOUS_MIN_GAP_MILLIS) return FluidHapticTraceOutcome.Dropped
      lastContinuousAt = now
      return FluidHapticTraceOutcome.Played
    }
    if (event in FluidHapticPatterns.coalescible) {
      if (fluidHapticCoalesces(lastDiscreteAt, now)) return FluidHapticTraceOutcome.Merged
      lastDiscreteAt = now
    }
    return FluidHapticTraceOutcome.Played
  }

  fun grain(style: FluidHapticGrainStyle, now: Long): FluidHapticTraceOutcome {
    // La grana di LibrePods non ha tetto: un tick di sistema a ogni evento deciso.
    if (style == FluidHapticGrainStyle.LibrePods) return FluidHapticTraceOutcome.Played
    if (now - lastGrainAt < FluidHapticsImpl.GRAIN_MIN_GAP_MILLIS) return FluidHapticTraceOutcome.Dropped
    lastGrainAt = now
    return FluidHapticTraceOutcome.Played
  }
}

/**
 * Il decoratore: inoltra tutto a [delegate] e, prima, ne scrive una copia in [trace].
 *
 * Non tocca niente di quello che passa: stesso evento, stesso momento, stesso [engine]. Si costruisce
 * da [FluidHapticTrace.wrap] o da [ProvideFluidHapticTrace].
 */
class TracingFluidHaptics internal constructor(
  val delegate: FluidHaptics,
  val trace: FluidHapticTrace,
) : FluidHaptics {

  override val engine: FluidHapticEngine get() = delegate.engine

  override fun play(event: FluidHapticEvent) {
    trace.recordEvent(event)
    delegate.play(event)
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) {
    trace.recordGrain(intensity, style)
    delegate.grain(intensity, style)
  }
}

/** Il registro installato sopra questo punto dell'albero, se c'e'. Nullo di serie: niente registro, niente costo. */
val LocalFluidHapticTrace: ProvidableCompositionLocal<FluidHapticTrace?> = staticCompositionLocalOf { null }

/**
 * Mette [trace] attorno all'aptica del tema corrente per tutto [content], e lo rende leggibile con
 * [LocalFluidHapticTrace].
 *
 * **Va dentro** il `FluidTheme`, perche' e' lui a installare l'aptica: un registro messo fuori
 * sarebbe sovrascritto. Per lo stesso motivo un `FluidTheme` annidato (che ne crea una nuova) toglie
 * il registro a quello che contiene: dentro lo richiama `ProvideFluidHapticTrace { ... }` senza
 * argomenti, che rimette quello ereditato. Senza registro (nullo, e niente ereditato) passa [content]
 * cosi' com'e'.
 */
@Composable
fun ProvideFluidHapticTrace(
  trace: FluidHapticTrace? = LocalFluidHapticTrace.current,
  content: @Composable () -> Unit,
) {
  if (trace == null) {
    content()
    return
  }
  val real = LocalFluidHaptics.current
  val traced = remember(real, trace) { trace.wrap(real) }
  CompositionLocalProvider(
    LocalFluidHapticTrace provides trace,
    LocalFluidHaptics provides traced,
    content = content,
  )
}

/** Un orologio che non torna indietro, ovunque giri (anche sul computer): per le differenze. */
internal fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L
