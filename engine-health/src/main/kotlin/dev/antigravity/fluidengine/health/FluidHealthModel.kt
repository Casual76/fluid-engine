/*
 * engine-health (4.0, F8.26): le misure, le fonti e i dati, senza Android. Codice del Fluid Engine.
 *
 * I dati di salute sono delicati: nessuna classe di questo modulo li scrive nel suo `toString` (un log o un rapporto di
 * crash ne vedono solo il tipo), il modulo non scrive log e non parla con nessuna telemetria dell'engine.
 */
package dev.antigravity.fluidengine.health

import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.Instant

/**
 * Una misura di Health Connect. Una classe e non un `enum`, perche' le misure crescono (Health Connect ne ha piu' di
 * quaranta) senza rompere chi fa `when`.
 */
@Immutable
@JvmInline
value class FluidHealthMetric(val id: String) {
  override fun toString(): String = id

  companion object {
    /** I passi (`StepsRecord`): intervalli col conto, sommati. */
    val Steps = FluidHealthMetric("passi")

    /** Il sonno (`SleepSessionRecord`): sessioni con le fasi. */
    val Sleep = FluidHealthMetric("sonno")

    /** Il battito (`HeartRateRecord`): campioni in battiti al minuto. */
    val HeartRate = FluidHealthMetric("battito")

    /** Gli allenamenti (`ExerciseSessionRecord`): sessioni col tipo. */
    val Exercise = FluidHealthMetric("allenamenti")

    /** Il peso (`WeightRecord`): misure in chilogrammi. */
    val Weight = FluidHealthMetric("peso")

    /** L'acqua (`HydrationRecord`): intervalli in millilitri, sommati. */
    val Hydration = FluidHealthMetric("acqua")

    /** Le sei misure del modulo, nell'ordine delle righe dei permessi. */
    val All: List<FluidHealthMetric> = listOf(Steps, Sleep, HeartRate, Exercise, Weight, Hydration)
  }
}

/** Un permesso: leggere o scrivere una misura. Health Connect li chiede uno per uno, e l'utente li da' uno per uno. */
@Immutable
data class FluidHealthPermission(val metric: FluidHealthMetric, val write: Boolean = false) {
  override fun toString(): String = "${if (write) "scrivere" else "leggere"} $metric"

  companion object {
    fun read(metric: FluidHealthMetric) = FluidHealthPermission(metric, write = false)
    fun write(metric: FluidHealthMetric) = FluidHealthPermission(metric, write = true)
  }
}

/** Che cosa ha scritto un dato: l'orologio, il telefono, un anello, una bilancia (Health Connect lo dice dal 2023). */
enum class FluidHealthDeviceKind { Phone, Watch, Ring, Scale, Other, Unknown }

/**
 * La fonte di un dato: l'app che l'ha scritto ([packageName], col suo nome se il telefono lo sa) e l'apparecchio
 * ([device]). E' cio' che le card mostrano sotto ogni valore: «Da Sounds», «Da Fitbit · orologio».
 */
@Immutable
data class FluidHealthSource(
  val packageName: String,
  val appLabel: String? = null,
  val device: FluidHealthDeviceKind = FluidHealthDeviceKind.Unknown,
  val deviceName: String? = null,
) {
  /** Il nome da leggere: quello dell'app, o il pacchetto se il telefono non lo sa. */
  val label: String get() = appLabel ?: packageName
}

/**
 * Un dato di Health Connect, come lo vede il modulo: un intervallo con una quantita' (passi, acqua), un punto con un
 * valore (battito, peso) o una sessione (sonno, allenamento). [id] e' quello di Health Connect; [source] dice chi l'ha
 * scritto.
 *
 * Il `toString` non dice mai il valore ne' gli orari: un log non porta via la salute di nessuno.
 */
sealed interface FluidHealthSample {
  val id: String
  val metric: FluidHealthMetric
  val source: FluidHealthSource
  val start: Instant
  val end: Instant
  val lastModified: Instant?
}

/** Una quantita' in un intervallo: [value] passi, o millilitri d'acqua. */
@Immutable
data class FluidHealthInterval(
  override val id: String,
  override val metric: FluidHealthMetric,
  override val start: Instant,
  override val end: Instant,
  val value: Double,
  override val source: FluidHealthSource,
  override val lastModified: Instant? = null,
) : FluidHealthSample {
  init {
    require(!end.isBefore(start)) { "Un intervallo che finisce prima di cominciare" }
    require(value.isFinite() && value >= 0) { "Una quantita' non valida" }
  }

  override fun toString(): String = "FluidHealthInterval($metric, ***)"
}

/** Un valore in un istante: [value] battiti al minuto, o chilogrammi. */
@Immutable
data class FluidHealthPoint(
  override val id: String,
  override val metric: FluidHealthMetric,
  val time: Instant,
  val value: Double,
  override val source: FluidHealthSource,
  override val lastModified: Instant? = null,
) : FluidHealthSample {
  init {
    require(value.isFinite()) { "Un valore non valido" }
  }

  override val start: Instant get() = time
  override val end: Instant get() = time

  override fun toString(): String = "FluidHealthPoint($metric, ***)"
}

/** Una fase del sonno: il tipo e' quello di Health Connect (`SleepSessionRecord.STAGE_TYPE_*`). */
@Immutable
data class FluidSleepStage(val start: Instant, val end: Instant, val type: Int) {
  /** Vero per le fasi in cui si dorme (leggero, profondo, REM, «dorme» senza dettaglio). */
  val asleep: Boolean get() = type in AsleepTypes

  override fun toString(): String = "FluidSleepStage(***)"

  internal companion object {
    // SleepSessionRecord: SLEEPING 2, LIGHT 4, DEEP 5, REM 6.
    val AsleepTypes = setOf(2, 4, 5, 6)
  }
}

/**
 * Una sessione: una notte ([FluidHealthMetric.Sleep], con le [stages] se la fonte le da') o un allenamento
 * ([FluidHealthMetric.Exercise], col tipo di Health Connect in [exerciseType]).
 */
@Immutable
data class FluidHealthSession(
  override val id: String,
  override val metric: FluidHealthMetric,
  override val start: Instant,
  override val end: Instant,
  override val source: FluidHealthSource,
  val title: String? = null,
  val exerciseType: Int? = null,
  val stages: List<FluidSleepStage> = emptyList(),
  override val lastModified: Instant? = null,
) : FluidHealthSample {
  init {
    require(!end.isBefore(start)) { "Una sessione che finisce prima di cominciare" }
  }

  val duration: Duration get() = Duration.between(start, end)

  override fun toString(): String = "FluidHealthSession($metric, ***)"
}

/**
 * Un totale (o una statistica) di una misura in un tratto, **come lo calcola Health Connect** con le sue aggregazioni
 * (`StepsRecord.COUNT_TOTAL`, `SleepSessionRecord.SLEEP_DURATION_TOTAL`, `HeartRateRecord.BPM_AVG`…). L'engine non rifa' la
 * scelta delle fonti: per l'attivita' e il sonno Health Connect toglie i doppioni con l'ordine delle app che l'utente
 * sceglie nelle sue impostazioni; per gli altri tipi somma tutte le app (developer.android.com, «Read aggregated data»,
 * aggiornata il 10/11/2026).
 *
 * @property total la somma (passi, millilitri d'acqua, minuti di sonno o di allenamento); null se nessuna app ha scritto
 *   niente nel tratto: allora non e' «0», e' «Nessun dato».
 * @property min, max, average per il battito (bpm) e il peso (kg); null dove non hanno senso o mancano i dati.
 * @property count quante misure (il battito: `MEASUREMENTS_COUNT`), se Health Connect lo dice.
 * @property sources le app che hanno dato i dati del totale (`dataOrigins` dell'aggregazione): la riga sotto il valore.
 */
@Immutable
data class FluidHealthAggregate(
  val metric: FluidHealthMetric,
  val start: Instant,
  val end: Instant,
  val total: Double? = null,
  val min: Double? = null,
  val max: Double? = null,
  val average: Double? = null,
  val count: Long? = null,
  val sources: List<FluidHealthSource> = emptyList(),
) {
  /** Vero se Health Connect ha dei dati nel tratto. */
  val hasData: Boolean get() = total != null || average != null || min != null || max != null

  override fun toString(): String = "FluidHealthAggregate($metric, dati: $hasData, ***)"

  companion object {
    /** Il tratto senza dati. */
    fun empty(metric: FluidHealthMetric, start: Instant, end: Instant): FluidHealthAggregate = FluidHealthAggregate(metric, start, end)
  }
}

/** Se Health Connect c'e' sul telefono. */
enum class FluidHealthAvailability {
  /** C'e' ed e' pronto. */
  Available,

  /** C'e', ma va aggiornato (Android 13 e prima, dove e' un'app del Play Store). */
  NeedsUpdate,

  /** Non c'e' e non si puo' avere (un telefono senza Play Store prima di Android 14, un profilo di lavoro). */
  Unavailable,
}

/** Le modifiche dall'ultima sincronizzazione: i dati nuovi o cambiati, quelli tolti, e il segno per la prossima volta. */
@Immutable
data class FluidHealthChanges(
  val upserted: List<FluidHealthSample>,
  val deletedIds: List<String>,
  val nextToken: String,
  val hasMore: Boolean,
  /** Il segno era troppo vecchio (Health Connect lo tiene 30 giorni): serve una lettura intera. */
  val tokenExpired: Boolean = false,
) {
  override fun toString(): String = "FluidHealthChanges(${upserted.size} nuovi, ${deletedIds.size} tolti, altri: $hasMore)"
}
