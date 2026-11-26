/*
 * engine-health (4.0, F8.26): la porta verso i dati di salute, quella vera (Health Connect) e quella in memoria (le
 * prove e la galleria). Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.health

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass

/**
 * La porta verso i dati di salute. [FluidHealthConnectGateway] e' quella vera; [FluidMemoryHealthGateway] tiene i dati in
 * memoria, per le prove (di un'app e dell'engine) e per la galleria. Tutti i metodi sospendono e si chiamano fuori dal
 * thread principale o da una coroutine: niente lavora sul principale.
 */
interface FluidHealthGateway {
  /** Se Health Connect c'e'. */
  suspend fun availability(): FluidHealthAvailability

  /** I permessi che l'utente ha dato, misura per misura. */
  suspend fun granted(): Set<FluidHealthPermission>

  /** I dati di [metric] fra [from] e [to], di tutte le fonti. Lancia [SecurityException] senza il permesso. */
  suspend fun read(metric: FluidHealthMetric, from: Instant, to: Instant): List<FluidHealthSample>

  /** Il segno da cui chiedere le modifiche di [metrics] da adesso in poi. */
  suspend fun changesToken(metrics: Set<FluidHealthMetric>): String

  /** Le modifiche dopo [token] (al piu' una pagina: [FluidHealthChanges.hasMore] dice se ce ne sono altre). */
  suspend fun changes(token: String): FluidHealthChanges

  /** Scrive [samples] (con la fonte di questa app) e da' i loro id. */
  suspend fun write(samples: List<FluidHealthSample>): List<String>

  /**
   * Il totale (o le statistiche) di [metric] fra [from] e [to], **calcolato da Health Connect** con le sue aggregazioni:
   * per l'attivita' e il sonno senza i doppioni, con l'ordine delle app scelto dall'utente; per gli altri tipi con tutte
   * le app. Con le app che l'hanno dato (`dataOrigins`). Lancia [SecurityException] senza il permesso di lettura.
   */
  suspend fun aggregate(metric: FluidHealthMetric, from: Instant, to: Instant): FluidHealthAggregate

  /** Gli stessi totali a tratti di [bucket] fra [from] e [to]: **solo i tratti con dati** (come Health Connect, la lista e' rada). */
  suspend fun aggregateByDuration(metric: FluidHealthMetric, from: Instant, to: Instant, bucket: Duration): List<FluidHealthAggregate>

  /** I totali di [days] giorni di calendario da [first] nel fuso [zone]: uno per giorno, vuoto ([FluidHealthAggregate.hasData] falso) senza dati. */
  suspend fun aggregateByDays(metric: FluidHealthMetric, first: LocalDate, days: Int, zone: ZoneId = ZoneId.systemDefault()): List<FluidHealthAggregate>

  /** Il dato piu' recente di [metric] fra [from] e [to] (l'ultima pesata, l'ultimo battito), o null. */
  suspend fun latest(metric: FluidHealthMetric, from: Instant, to: Instant): FluidHealthSample?
}

/**
 * Health Connect, attraverso `androidx.health.connect:connect-client`.
 *
 * - **I permessi** sono uno per misura e per verso ([FluidHealthPermissions.of]), e si chiedono con il contratto di
 *   Health Connect ([FluidHealthPermissions.requestContract]). Dopo due rifiuti Android non mostra piu' il dialogo: si
 *   apre la pagina di Health Connect ([FluidHealthPermissions.manageIntent]).
 * - **Le fonti**: ogni dato porta il pacchetto che l'ha scritto e l'apparecchio; il nome dell'app viene dal telefono.
 * - **Il battito** di Health Connect e' un record con tanti campioni: qui diventa un punto per campione (id
 *   `record#indice`).
 */
class FluidHealthConnectGateway(
  context: Context,
  private val client: HealthConnectClient? = null,
) : FluidHealthGateway {
  private val appContext = context.applicationContext
  private val connect: HealthConnectClient by lazy { client ?: HealthConnectClient.getOrCreate(appContext) }
  private val labels = HashMap<String, String?>()

  override suspend fun availability(): FluidHealthAvailability = if (client != null) FluidHealthAvailability.Available else
    when (HealthConnectClient.getSdkStatus(appContext)) {
      HealthConnectClient.SDK_AVAILABLE -> FluidHealthAvailability.Available
      HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> FluidHealthAvailability.NeedsUpdate
      else -> FluidHealthAvailability.Unavailable
    }

  override suspend fun granted(): Set<FluidHealthPermission> =
    FluidHealthPermissions.fromStrings(connect.permissionController.getGrantedPermissions())

  override suspend fun read(metric: FluidHealthMetric, from: Instant, to: Instant): List<FluidHealthSample> {
    @Suppress("UNCHECKED_CAST")
    val type = FluidHealthPermissions.recordType(metric) as? KClass<Record> ?: return emptyList()
    val out = ArrayList<FluidHealthSample>()
    var page: String? = null
    do {
      val response = connect.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageToken = page))
      for (record in response.records) out.addAll(toSamples(record))
      page = response.pageToken
    } while (!page.isNullOrEmpty())
    return out
  }

  override suspend fun changesToken(metrics: Set<FluidHealthMetric>): String =
    connect.getChangesToken(ChangesTokenRequest(metrics.mapNotNull { FluidHealthPermissions.recordType(it) }.toSet()))

  override suspend fun changes(token: String): FluidHealthChanges {
    val response = connect.getChanges(token)
    val upserted = ArrayList<FluidHealthSample>()
    val deleted = ArrayList<String>()
    for (change in response.changes) {
      when (change) {
        is UpsertionChange -> upserted.addAll(toSamples(change.record))
        is DeletionChange -> deleted.add(change.recordId)
      }
    }
    return FluidHealthChanges(upserted, deleted, response.nextChangesToken, response.hasMore, response.changesTokenExpired)
  }

  override suspend fun write(samples: List<FluidHealthSample>): List<String> {
    if (samples.isEmpty()) return emptyList()
    return connect.insertRecords(samples.map { toRecord(it) }).recordIdsList
  }

  override suspend fun aggregate(metric: FluidHealthMetric, from: Instant, to: Instant): FluidHealthAggregate {
    val metrics = aggregateMetrics(metric) ?: return FluidHealthAggregate.empty(metric, from, to)
    return toAggregate(metric, connect.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(from, to))), from, to)
  }

  override suspend fun aggregateByDuration(metric: FluidHealthMetric, from: Instant, to: Instant, bucket: Duration): List<FluidHealthAggregate> {
    val metrics = aggregateMetrics(metric) ?: return emptyList()
    return connect.aggregateGroupByDuration(AggregateGroupByDurationRequest(metrics, TimeRangeFilter.between(from, to), bucket))
      .map { toAggregate(metric, it.result, it.startTime, it.endTime) }
      .filter { it.hasData }
  }

  override suspend fun aggregateByDays(metric: FluidHealthMetric, first: LocalDate, days: Int, zone: ZoneId): List<FluidHealthAggregate> {
    if (days <= 0) return emptyList()
    val empty = (0 until days).map { i ->
      val d = first.plusDays(i.toLong())
      FluidHealthAggregate.empty(metric, d.atStartOfDay(zone).toInstant(), d.plusDays(1).atStartOfDay(zone).toInstant())
    }
    val metrics = aggregateMetrics(metric) ?: return empty
    // I giorni di calendario si chiedono in ora locale (Health Connect li taglia col fuso di ogni dato).
    val groups = connect.aggregateGroupByPeriod(
      AggregateGroupByPeriodRequest(metrics, TimeRangeFilter.between(first.atStartOfDay(), first.plusDays(days.toLong()).atStartOfDay()), Period.ofDays(1)),
    )
    val byDay = groups.associateBy { it.startTime.toLocalDate() }
    return empty.mapIndexed { i, e ->
      val g = byDay[first.plusDays(i.toLong())] ?: return@mapIndexed e
      toAggregate(metric, g.result, e.start, e.end)
    }
  }

  override suspend fun latest(metric: FluidHealthMetric, from: Instant, to: Instant): FluidHealthSample? {
    @Suppress("UNCHECKED_CAST")
    val type = FluidHealthPermissions.recordType(metric) as? KClass<Record> ?: return null
    val response = connect.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), ascendingOrder = false, pageSize = 1))
    return response.records.flatMap { toSamples(it) }.maxByOrNull { it.end }
  }

  /** Le metriche di Health Connect di [metric]. */
  private fun aggregateMetrics(metric: FluidHealthMetric): Set<AggregateMetric<*>>? = when (metric) {
    FluidHealthMetric.Steps -> setOf(StepsRecord.COUNT_TOTAL)
    FluidHealthMetric.Hydration -> setOf(HydrationRecord.VOLUME_TOTAL)
    FluidHealthMetric.Sleep -> setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL)
    FluidHealthMetric.Exercise -> setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
    FluidHealthMetric.HeartRate -> setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX, HeartRateRecord.BPM_AVG, HeartRateRecord.MEASUREMENTS_COUNT)
    FluidHealthMetric.Weight -> setOf(WeightRecord.WEIGHT_MIN, WeightRecord.WEIGHT_MAX, WeightRecord.WEIGHT_AVG)
    else -> null
  }

  /** Il risultato di Health Connect nelle unita' del modulo: passi, millilitri, minuti, bpm, chilogrammi. */
  internal fun toAggregate(metric: FluidHealthMetric, result: AggregationResult, from: Instant, to: Instant): FluidHealthAggregate {
    val sources = result.dataOrigins.map { sourceOf(it.packageName, null) }.sortedBy { it.packageName }
    fun minutes(d: Duration?): Double? = d?.let { it.toMillis() / 60_000.0 }
    return when (metric) {
      FluidHealthMetric.Steps -> FluidHealthAggregate(metric, from, to, total = result[StepsRecord.COUNT_TOTAL]?.toDouble(), sources = sources)
      FluidHealthMetric.Hydration -> FluidHealthAggregate(metric, from, to, total = result[HydrationRecord.VOLUME_TOTAL]?.inMilliliters, sources = sources)
      FluidHealthMetric.Sleep -> FluidHealthAggregate(metric, from, to, total = minutes(result[SleepSessionRecord.SLEEP_DURATION_TOTAL]), sources = sources)
      FluidHealthMetric.Exercise -> FluidHealthAggregate(metric, from, to, total = minutes(result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]), sources = sources)
      FluidHealthMetric.HeartRate -> FluidHealthAggregate(
        metric, from, to,
        min = result[HeartRateRecord.BPM_MIN]?.toDouble(), max = result[HeartRateRecord.BPM_MAX]?.toDouble(),
        average = result[HeartRateRecord.BPM_AVG]?.toDouble(), count = result[HeartRateRecord.MEASUREMENTS_COUNT], sources = sources,
      )
      FluidHealthMetric.Weight -> FluidHealthAggregate(
        metric, from, to,
        min = result[WeightRecord.WEIGHT_MIN]?.inKilograms, max = result[WeightRecord.WEIGHT_MAX]?.inKilograms,
        average = result[WeightRecord.WEIGHT_AVG]?.inKilograms, sources = sources,
      )
      else -> FluidHealthAggregate.empty(metric, from, to)
    }
  }

  /** I dati del modulo da un record di Health Connect (vuoto per i tipi che il modulo non conosce). */
  internal fun toSamples(record: Record): List<FluidHealthSample> {
    val source = sourceOf(record.metadata)
    val id = record.metadata.id
    val modified = record.metadata.lastModifiedTime
    return when (record) {
      is StepsRecord -> listOf(FluidHealthInterval(id, FluidHealthMetric.Steps, record.startTime, record.endTime, record.count.toDouble(), source, modified))
      is HydrationRecord -> listOf(FluidHealthInterval(id, FluidHealthMetric.Hydration, record.startTime, record.endTime, record.volume.inMilliliters, source, modified))
      is WeightRecord -> listOf(FluidHealthPoint(id, FluidHealthMetric.Weight, record.time, record.weight.inKilograms, source, modified))
      is HeartRateRecord -> record.samples.mapIndexed { i, s ->
        FluidHealthPoint("$id#$i", FluidHealthMetric.HeartRate, s.time, s.beatsPerMinute.toDouble(), source, modified)
      }
      is SleepSessionRecord -> listOf(
        FluidHealthSession(id, FluidHealthMetric.Sleep, record.startTime, record.endTime, source, record.title,
          stages = record.stages.map { FluidSleepStage(it.startTime, it.endTime, it.stage) }, lastModified = modified),
      )
      is ExerciseSessionRecord -> listOf(
        FluidHealthSession(id, FluidHealthMetric.Exercise, record.startTime, record.endTime, source, record.title,
          exerciseType = record.exerciseType, lastModified = modified),
      )
      else -> emptyList()
    }
  }

  private fun sourceOf(metadata: Metadata): FluidHealthSource = sourceOf(metadata.dataOrigin.packageName, metadata.device)

  /** La fonte da un pacchetto (il nome dell'app dal telefono) e, se c'e', dall'apparecchio. */
  private fun sourceOf(pkg: String, device: Device?): FluidHealthSource {
    val label = labels.getOrPut(pkg) {
      runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
      }.getOrNull()
    }
    val kind = when (device?.type) {
      Device.TYPE_PHONE -> FluidHealthDeviceKind.Phone
      Device.TYPE_WATCH -> FluidHealthDeviceKind.Watch
      Device.TYPE_RING -> FluidHealthDeviceKind.Ring
      Device.TYPE_FITNESS_BAND -> FluidHealthDeviceKind.Watch
      Device.TYPE_SCALE -> FluidHealthDeviceKind.Scale
      null, Device.TYPE_UNKNOWN -> FluidHealthDeviceKind.Unknown
      else -> FluidHealthDeviceKind.Other
    }
    val name = listOfNotNull(device?.manufacturer, device?.model).joinToString(" ").ifBlank { null }
    return FluidHealthSource(pkg, label, kind, name)
  }

  internal companion object {
    /** Un record di Health Connect dai dati del modulo: scritto «a mano» o «registrato», dal telefono. */
    fun toRecord(sample: FluidHealthSample, zone: ZoneId = ZoneId.systemDefault()): Record {
      val metadata = Metadata.manualEntry(device = Device(type = Device.TYPE_PHONE))
      fun offset(at: Instant): ZoneOffset = zone.rules.getOffset(at)
      return when (sample) {
        is FluidHealthInterval -> when (sample.metric) {
          FluidHealthMetric.Steps -> StepsRecord(sample.start, offset(sample.start), sample.end, offset(sample.end), sample.value.toLong().coerceAtLeast(1), metadata)
          FluidHealthMetric.Hydration -> HydrationRecord(sample.start, offset(sample.start), sample.end, offset(sample.end), Volume.milliliters(sample.value), metadata)
          else -> throw IllegalArgumentException("Una misura che non e' un intervallo: ${sample.metric}")
        }
        is FluidHealthPoint -> when (sample.metric) {
          FluidHealthMetric.Weight -> WeightRecord(sample.time, offset(sample.time), Mass.kilograms(sample.value), metadata)
          FluidHealthMetric.HeartRate -> HeartRateRecord(sample.time, offset(sample.time), sample.time.plusSeconds(1), offset(sample.time),
            listOf(HeartRateRecord.Sample(sample.time, sample.value.toLong())), metadata)
          else -> throw IllegalArgumentException("Una misura che non e' un punto: ${sample.metric}")
        }
        is FluidHealthSession -> when (sample.metric) {
          FluidHealthMetric.Sleep -> SleepSessionRecord(sample.start, offset(sample.start), sample.end, offset(sample.end),
            Metadata.autoRecorded(Device(type = Device.TYPE_PHONE)), sample.title, null,
            sample.stages.map { SleepSessionRecord.Stage(it.start, it.end, it.type) })
          FluidHealthMetric.Exercise -> ExerciseSessionRecord(sample.start, offset(sample.start), sample.end, offset(sample.end),
            Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE)), sample.exerciseType ?: ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            sample.title, null)
          else -> throw IllegalArgumentException("Una misura che non e' una sessione: ${sample.metric}")
        }
      }
    }
  }
}

/** I permessi di Health Connect per le misure del modulo, e come chiederli. */
object FluidHealthPermissions {
  /** Il tipo di record di Health Connect di [metric]; null per una misura che il modulo non conosce. */
  fun recordType(metric: FluidHealthMetric): KClass<out Record>? = when (metric) {
    FluidHealthMetric.Steps -> StepsRecord::class
    FluidHealthMetric.Sleep -> SleepSessionRecord::class
    FluidHealthMetric.HeartRate -> HeartRateRecord::class
    FluidHealthMetric.Exercise -> ExerciseSessionRecord::class
    FluidHealthMetric.Weight -> WeightRecord::class
    FluidHealthMetric.Hydration -> HydrationRecord::class
    else -> null
  }

  /** La stringa del permesso di Health Connect («android.permission.health.READ_STEPS»). */
  fun of(permission: FluidHealthPermission): String? {
    val type = recordType(permission.metric) ?: return null
    return if (permission.write) HealthPermission.getWritePermission(type) else HealthPermission.getReadPermission(type)
  }

  /** I permessi del modulo da quelli di Health Connect (gli altri si ignorano). */
  fun fromStrings(granted: Set<String>): Set<FluidHealthPermission> = buildSet {
    for (metric in FluidHealthMetric.All) for (write in listOf(false, true)) {
      val p = FluidHealthPermission(metric, write)
      if (of(p) in granted) add(p)
    }
  }

  /** Le stringhe da chiedere per [permissions]. */
  fun strings(permissions: Collection<FluidHealthPermission>): Set<String> = permissions.mapNotNull { of(it) }.toSet()

  /** Il contratto per chiedere i permessi (il foglio di Health Connect): entra un insieme di stringhe, esce quello dato. */
  fun requestContract(): ActivityResultContract<Set<String>, Set<String>> = PermissionController.createRequestPermissionResultContract()

  /** La pagina dei permessi di questa app in Health Connect, per dopo due rifiuti (Android non mostra piu' il dialogo). */
  fun manageIntent(context: Context): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context)

  /** I permessi di salute che l'app ha scritto nel manifest: solo quelli si possono chiedere. */
  fun declared(context: Context): Set<FluidHealthPermission> {
    val info = runCatching {
      context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    }.getOrNull() ?: return emptySet()
    return fromStrings(info.requestedPermissions?.toSet().orEmpty())
  }
}

/**
 * I dati in memoria, come li darebbe Health Connect: per le prove (di un'app e dell'engine) e per la galleria. Si danno
 * i permessi con [grant] e [revoke], si aggiungono dati di qualunque fonte con [put], si tolgono con [remove]; le
 * modifiche si leggono per segno come quelle vere, e un segno si fa scadere con [expireTokens].
 *
 * **Le aggregazioni** si comportano come quelle di Health Connect (`FluidMemoryHealthAggregation`): per l'attivita' e il
 * sonno tengono l'app più in alto in [sourcePriority], l'ordine che **l'utente** sceglie nelle impostazioni di Health
 * Connect (vuoto: l'ordine in cui le app hanno scritto la prima volta, come Health Connect le aggiunge all'elenco); per gli
 * altri tipi mettono insieme tutte le app.
 */
class FluidMemoryHealthGateway(
  var availabilityValue: FluidHealthAvailability = FluidHealthAvailability.Available,
  /** La fonte dei dati scritti da [write]: questa app. */
  private val self: FluidHealthSource = FluidHealthSource("dev.antigravity.fluidglass", "Questa app", FluidHealthDeviceKind.Phone),
  /** L'ordine delle app nelle impostazioni di Health Connect, come lo sceglie l'utente (per le prove); vuoto: chi ha scritto prima. */
  var sourcePriority: List<String> = emptyList(),
) : FluidHealthGateway {
  private val lock = Any()
  private val data = LinkedHashMap<String, FluidHealthSample>()
  private val log = ArrayList<Pair<FluidHealthSample?, String>>() // (dato nuovo o null, id tolto)
  private val tokens = HashMap<String, Pair<Int, Set<FluidHealthMetric>>>()
  private val grantedSet = HashSet<FluidHealthPermission>()
  private var nextId = 1
  private var expiredBefore = 0
  private val firstSeen = LinkedHashSet<String>()

  /** Il posto di una fonte nell'ordine di Health Connect: [sourcePriority], poi chi ha scritto prima. */
  private fun rank(source: FluidHealthSource): Int {
    val listed = sourcePriority.indexOf(source.packageName)
    if (listed >= 0) return listed
    return sourcePriority.size + firstSeen.indexOf(source.packageName).let { if (it < 0) Int.MAX_VALUE / 2 else it }
  }

  /** Quante letture intere ([read]) e quante richieste di modifiche ([changes]) ha ricevuto: per le prove. */
  @Volatile var reads: Int = 0
    private set
  @Volatile var changeRequests: Int = 0
    private set

  /** Quante aggregazioni ha calcolato: per le prove (le card leggono i totali da qui, non dai record). */
  @Volatile var aggregations: Int = 0
    private set

  fun grant(vararg permissions: FluidHealthPermission) = synchronized(lock) { grantedSet.addAll(permissions) }
  fun revoke(vararg permissions: FluidHealthPermission) = synchronized(lock) { grantedSet.removeAll(permissions.toSet()) }
  fun grantAll() = synchronized(lock) { FluidHealthMetric.All.forEach { grantedSet.add(FluidHealthPermission(it)); grantedSet.add(FluidHealthPermission(it, true)) } }

  /** Aggiunge o sostituisce dati (di qualunque fonte). */
  fun put(vararg samples: FluidHealthSample) = synchronized(lock) {
    for (s in samples) {
      data[s.id] = s
      log.add(s to s.id)
      firstSeen.add(s.source.packageName)
    }
  }

  /** Toglie i dati con questi id. */
  fun remove(vararg ids: String) = synchronized(lock) {
    for (id in ids) if (data.remove(id) != null) log.add(null to id)
  }

  /** Fa scadere tutti i segni dati finora (come Health Connect dopo 30 giorni). */
  fun expireTokens() = synchronized(lock) { expiredBefore = log.size + 1 }

  override suspend fun availability(): FluidHealthAvailability = availabilityValue

  override suspend fun granted(): Set<FluidHealthPermission> = synchronized(lock) { grantedSet.toSet() }

  override suspend fun read(metric: FluidHealthMetric, from: Instant, to: Instant): List<FluidHealthSample> = synchronized(lock) {
    if (FluidHealthPermission(metric) !in grantedSet) throw SecurityException("Permesso negato: leggere $metric")
    reads += 1
    data.values.filter { it.metric == metric && it.start.isBefore(to) && !it.end.isBefore(from) }
  }

  override suspend fun changesToken(metrics: Set<FluidHealthMetric>): String = synchronized(lock) {
    val token = "segno-${tokens.size + 1}"
    tokens[token] = log.size to metrics
    token
  }

  override suspend fun changes(token: String): FluidHealthChanges = synchronized(lock) {
    changeRequests += 1
    val (position, metrics) = tokens[token] ?: return@synchronized FluidHealthChanges(emptyList(), emptyList(), token, false, tokenExpired = true)
    if (position < expiredBefore) return@synchronized FluidHealthChanges(emptyList(), emptyList(), token, false, tokenExpired = true)
    val page = log.drop(position).take(PageSize)
    val upserted = page.mapNotNull { it.first }.filter { it.metric in metrics && FluidHealthPermission(it.metric) in grantedSet }
    val deleted = page.filter { it.first == null }.map { it.second }
    val next = "segno-${tokens.size + 1}"
    tokens[next] = (position + page.size) to metrics
    FluidHealthChanges(upserted, deleted, next, hasMore = position + page.size < log.size)
  }

  override suspend fun write(samples: List<FluidHealthSample>): List<String> = synchronized(lock) {
    samples.map { s ->
      if (FluidHealthPermission(s.metric, write = true) !in grantedSet) throw SecurityException("Permesso negato: scrivere ${s.metric}")
      val id = "scritto-${nextId++}"
      val mine = when (s) {
        is FluidHealthInterval -> s.copy(id = id, source = self)
        is FluidHealthPoint -> s.copy(id = id, source = self)
        is FluidHealthSession -> s.copy(id = id, source = self)
      }
      data[id] = mine
      log.add(mine to id)
      firstSeen.add(mine.source.packageName)
      id
    }
  }

  override suspend fun aggregate(metric: FluidHealthMetric, from: Instant, to: Instant): FluidHealthAggregate = synchronized(lock) {
    if (FluidHealthPermission(metric) !in grantedSet) throw SecurityException("Permesso negato: leggere $metric")
    aggregations += 1
    FluidMemoryHealthAggregation.aggregate(metric, data.values.toList(), from, to, ::rank)
  }

  override suspend fun aggregateByDuration(metric: FluidHealthMetric, from: Instant, to: Instant, bucket: Duration): List<FluidHealthAggregate> = synchronized(lock) {
    if (FluidHealthPermission(metric) !in grantedSet) throw SecurityException("Permesso negato: leggere $metric")
    aggregations += 1
    val all = data.values.toList()
    val out = ArrayList<FluidHealthAggregate>()
    var a = from
    while (a.isBefore(to)) {
      val b = minOf(a.plus(bucket), to)
      val r = FluidMemoryHealthAggregation.aggregate(metric, all, a, b, ::rank)
      if (r.hasData) out.add(r)
      a = b
    }
    out
  }

  override suspend fun aggregateByDays(metric: FluidHealthMetric, first: LocalDate, days: Int, zone: ZoneId): List<FluidHealthAggregate> = synchronized(lock) {
    if (FluidHealthPermission(metric) !in grantedSet) throw SecurityException("Permesso negato: leggere $metric")
    aggregations += 1
    val all = data.values.toList()
    (0 until days).map { i ->
      val d = first.plusDays(i.toLong())
      FluidMemoryHealthAggregation.aggregate(metric, all, d.atStartOfDay(zone).toInstant(), d.plusDays(1).atStartOfDay(zone).toInstant(), ::rank)
    }
  }

  override suspend fun latest(metric: FluidHealthMetric, from: Instant, to: Instant): FluidHealthSample? = synchronized(lock) {
    if (FluidHealthPermission(metric) !in grantedSet) throw SecurityException("Permesso negato: leggere $metric")
    data.values.filter { it.metric == metric && !it.end.isBefore(from) && it.end.isBefore(to) }.maxByOrNull { it.end }
  }

  /** Tutti i dati di adesso (per le prove). */
  fun all(): List<FluidHealthSample> = synchronized(lock) { data.values.toList() }

  private companion object {
    const val PageSize = 50
  }
}

/**
 * Scrive una notte di sonno di questa app (Sounds col timer della notte): da [start] a [end], col titolo. Da' l'id del
 * dato. Serve il permesso di scrittura del sonno.
 *
 * ```kotlin
 * gateway.writeSleep(inizio, fine, title = "Timer della notte")
 * ```
 */
suspend fun FluidHealthGateway.writeSleep(start: Instant, end: Instant, title: String? = null, stages: List<FluidSleepStage> = emptyList()): String =
  write(listOf(FluidHealthSession("", FluidHealthMetric.Sleep, start, end, FluidHealthSource(""), title, stages = stages))).first()
