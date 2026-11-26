package dev.antigravity.fluidengine.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass

/**
 * F8.26 · Il ponte con Health Connect su un client finto che implementa l'interfaccia vera (`HealthConnectClient`): i
 * record veri diventano i dati del modulo con la fonte e l'apparecchio, le pagine si leggono tutte, le modifiche e le
 * cancellazioni arrivano, i permessi sono quelli di Health Connect, misura per misura, e la scrittura del sonno di
 * Sounds diventa un `SleepSessionRecord`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidHealthConnectGatewayTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val t0 = Instant.parse("2026-11-20T08:00:00Z")
  private val offset = ZoneOffset.ofHours(1)

  /**
   * I metadati come li scrive Health Connect (con la fonte): il costruttore e' interno alla libreria, e dalle app si
   * fanno solo con le fabbriche senza fonte. Qui con la riflessione, come un record letto.
   */
  private fun meta(id: String, pkg: String, type: Int): Metadata =
    Metadata::class.java.getConstructor(
      Int::class.javaPrimitiveType, String::class.java, DataOrigin::class.java, Instant::class.java, String::class.java,
      Long::class.javaPrimitiveType, Device::class.java,
    ).newInstance(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED, id, DataOrigin(pkg), t0, null, 0L, Device(type, "Google", "Pixel Watch 3"))

  private class FakeClient(private val records: List<Record>, private val granted: Set<String>) : HealthConnectClient {
    val inserted = ArrayList<Record>()
    var changes: ChangesResponse = ChangesResponse(emptyList(), "segno-2", false, false)

    override val permissionController: PermissionController = object : PermissionController {
      override suspend fun getGrantedPermissions(): Set<String> = granted
      override suspend fun revokeAllPermissions() = Unit
    }

    override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse {
      inserted.addAll(records)
      return InsertRecordsResponse(records.indices.map { "nuovo-$it" })
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> {
      readCalls += 1
      val type = request.recordType
      val all = records.filter { type.isInstance(it) } as List<T>
      // Due pagine: la prima col segno della seconda.
      val page = request.pageToken
      return if (page == null && all.size > 1) ReadRecordsResponse(all.take(1), "pagina-2")
      else ReadRecordsResponse(if (page == null) all else all.drop(1), null)
    }

    override suspend fun getChangesToken(request: ChangesTokenRequest): String = "segno-1"
    override suspend fun getChanges(changesToken: String): ChangesResponse = changes
    override suspend fun updateRecords(records: List<Record>) = Unit
    override suspend fun deleteRecords(recordType: KClass<out Record>, recordIdsList: List<String>, clientRecordIdsList: List<String>) = Unit
    override suspend fun deleteRecords(recordType: KClass<out Record>, timeRangeFilter: TimeRangeFilter) = Unit
    override suspend fun <T : Record> readRecord(recordType: KClass<T>, recordId: String): ReadRecordResponse<T> = throw UnsupportedOperationException()
    /** Il totale che il provider calcola (con i doppioni gia' tolti): lo decide la prova. */
    var aggregateResult: AggregationResult = AggregationResult(emptyMap(), emptyMap(), emptySet())
    var byDuration: List<AggregationResultGroupedByDuration> = emptyList()
    var byPeriod: List<AggregationResultGroupedByPeriod> = emptyList()
    var aggregateCalls = 0
    var readCalls = 0

    override suspend fun aggregate(request: AggregateRequest): AggregationResult {
      aggregateCalls += 1
      return aggregateResult
    }
    override suspend fun aggregateGroupByDuration(request: AggregateGroupByDurationRequest): List<AggregationResultGroupedByDuration> = byDuration
    override suspend fun aggregateGroupByPeriod(request: AggregateGroupByPeriodRequest): List<AggregationResultGroupedByPeriod> = byPeriod
  }

  private val stepsA = StepsRecord(t0, offset, t0.plusSeconds(3600), offset, 1200, meta("st-1", "com.google.android.apps.fitness", Device.TYPE_WATCH))
  private val stepsB = StepsRecord(t0.plusSeconds(3600), offset, t0.plusSeconds(7200), offset, 800, meta("st-2", "com.sec.android.app.shealth", Device.TYPE_PHONE))
  private val heart = HeartRateRecord(t0, offset, t0.plusSeconds(120), offset,
    listOf(HeartRateRecord.Sample(t0, 58), HeartRateRecord.Sample(t0.plusSeconds(60), 61)), meta("hr-1", "com.google.android.apps.fitness", Device.TYPE_WATCH))
  private val weight = WeightRecord(t0, offset, Mass.kilograms(72.4), meta("w-1", "com.example.scale", Device.TYPE_SCALE))
  private val sleep = SleepSessionRecord(t0.minusSeconds(8 * 3600), offset, t0, offset, meta("sl-1", "dev.pampa.sounds", Device.TYPE_PHONE), "Timer della notte", null,
    listOf(SleepSessionRecord.Stage(t0.minusSeconds(7 * 3600), t0.minusSeconds(3600), SleepSessionRecord.STAGE_TYPE_DEEP)))

  @Test
  fun `i record veri diventano i dati del modulo con la fonte e l'apparecchio, pagina dopo pagina`() = runBlocking {
    val client = FakeClient(listOf(stepsA, stepsB, heart, weight, sleep), emptySet())
    val gateway = FluidHealthConnectGateway(context, client)
    val steps = gateway.read(FluidHealthMetric.Steps, t0.minusSeconds(86_400), t0.plusSeconds(86_400))
    assertEquals(listOf("st-1", "st-2"), steps.map { it.id }) // la seconda pagina c'e'
    val first = steps[0] as FluidHealthInterval
    assertEquals(1200.0, first.value, 0.0)
    assertEquals("com.google.android.apps.fitness", first.source.packageName)
    assertEquals(FluidHealthDeviceKind.Watch, first.source.device)
    assertEquals("Google Pixel Watch 3", first.source.deviceName)
    assertEquals(FluidHealthDeviceKind.Phone, steps[1].source.device)

    val beats = gateway.read(FluidHealthMetric.HeartRate, t0.minusSeconds(60), t0.plusSeconds(600))
    assertEquals(listOf("hr-1#0", "hr-1#1"), beats.map { it.id })
    assertEquals(61.0, (beats[1] as FluidHealthPoint).value, 0.0)

    assertEquals(72.4, (gateway.read(FluidHealthMetric.Weight, t0.minusSeconds(60), t0.plusSeconds(60)).single() as FluidHealthPoint).value, 1e-9)
    val night = gateway.read(FluidHealthMetric.Sleep, t0.minusSeconds(86_400), t0).single() as FluidHealthSession
    assertEquals("Timer della notte", night.title)
    assertEquals(SleepSessionRecord.STAGE_TYPE_DEEP, night.stages.single().type)
    assertTrue(night.stages.single().asleep)
  }

  @Test
  fun `le modifiche portano i dati nuovi e gli id tolti, col segno dopo`() = runBlocking {
    val client = FakeClient(emptyList(), emptySet())
    client.changes = ChangesResponse(listOf(UpsertionChange(stepsA), DeletionChange("st-9")), "segno-3", true, false)
    val gateway = FluidHealthConnectGateway(context, client)
    assertEquals("segno-1", gateway.changesToken(setOf(FluidHealthMetric.Steps)))
    val changes = gateway.changes("segno-1")
    assertEquals(listOf("st-1"), changes.upserted.map { it.id })
    assertEquals(listOf("st-9"), changes.deletedIds)
    assertEquals("segno-3", changes.nextToken)
    assertTrue(changes.hasMore)
  }

  @Test
  fun `i permessi sono quelli di Health Connect, misura per misura, e quelli parziali restano parziali`() = runBlocking {
    assertEquals("android.permission.health.READ_STEPS", FluidHealthPermissions.of(FluidHealthPermission.read(FluidHealthMetric.Steps)))
    assertEquals("android.permission.health.WRITE_SLEEP", FluidHealthPermissions.of(FluidHealthPermission.write(FluidHealthMetric.Sleep)))
    assertEquals("android.permission.health.READ_HEART_RATE", FluidHealthPermissions.of(FluidHealthPermission.read(FluidHealthMetric.HeartRate)))
    assertEquals("android.permission.health.READ_HYDRATION", FluidHealthPermissions.of(FluidHealthPermission.read(FluidHealthMetric.Hydration)))
    assertEquals("android.permission.health.READ_EXERCISE", FluidHealthPermissions.of(FluidHealthPermission.read(FluidHealthMetric.Exercise)))
    assertEquals("android.permission.health.READ_WEIGHT", FluidHealthPermissions.of(FluidHealthPermission.read(FluidHealthMetric.Weight)))
    val client = FakeClient(emptyList(), setOf("android.permission.health.READ_STEPS", "android.permission.health.WRITE_SLEEP", "android.permission.CAMERA"))
    val granted = FluidHealthConnectGateway(context, client).granted()
    assertEquals(setOf(FluidHealthPermission.read(FluidHealthMetric.Steps), FluidHealthPermission.write(FluidHealthMetric.Sleep)), granted)
    val asked = FluidHealthMetric.All.map { FluidHealthPermission.read(it) } + FluidHealthPermission.write(FluidHealthMetric.Sleep)
    assertEquals(FluidHealthAccess.Allowed, fluidHealthAccess(FluidHealthMetric.Steps, asked, granted))
    assertEquals(FluidHealthAccess.WriteOnly, fluidHealthAccess(FluidHealthMetric.Sleep, asked, granted))
    assertEquals(FluidHealthAccess.NotAllowed, fluidHealthAccess(FluidHealthMetric.HeartRate, asked, granted))
  }

  @Test
  fun `il sonno di Sounds si scrive come una sessione del telefono, con il titolo e le fasi`() = runBlocking {
    val client = FakeClient(emptyList(), emptySet())
    val gateway = FluidHealthConnectGateway(context, client)
    val start = Instant.parse("2026-11-19T22:40:00Z")
    val end = Instant.parse("2026-11-20T06:10:00Z")
    val id = gateway.writeSleep(start, end, title = "Timer della notte")
    assertEquals("nuovo-0", id)
    val record = client.inserted.single() as SleepSessionRecord
    assertEquals(start, record.startTime)
    assertEquals(end, record.endTime)
    assertEquals("Timer della notte", record.title)
    assertEquals(Device.TYPE_PHONE, record.metadata.device?.type)
  }

  private fun result(longs: Map<String, Long> = emptyMap(), doubles: Map<String, Double> = emptyMap(), vararg origins: String) =
    AggregationResult(longs, doubles, origins.map { DataOrigin(it) }.toSet())

  @Test
  fun `niente doppioni - il totale dei passi e' COUNT_TOTAL di Health Connect, non la somma dei record`() = runBlocking {
    // I record del telefono e dell'orologio si sovrappongono (1 200 + 800 = 2 000 se sommati); il provider, con l'ordine
    // che l'utente ha scelto, dice 1 450 e le app che l'hanno dato.
    val client = FakeClient(listOf(stepsA, stepsB), emptySet())
    client.aggregateResult = result(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 1450L), emptyMap(), "com.google.android.apps.fitness", "com.sec.android.app.shealth")
    val gateway = FluidHealthConnectGateway(context, client)
    val day = java.time.LocalDate.of(2026, 11, 20)
    val reading = gateway.day(FluidHealthMetric.Steps, day, java.time.ZoneOffset.UTC, now = t0.plusSeconds(12 * 3600), gapRule = null) as FluidHealthReading.Value
    assertEquals(1450.0, reading.value, 0.0)
    assertEquals(listOf("com.google.android.apps.fitness", "com.sec.android.app.shealth"), reading.sources.map { it.packageName })
    assertEquals(1, client.aggregateCalls)
    assertEquals("nessun record letto per il totale", 0, client.readCalls)
  }

  @Test
  fun `un giorno senza aggregato e' Nessun dato, non zero`() = runBlocking {
    val client = FakeClient(emptyList(), emptySet())
    val gateway = FluidHealthConnectGateway(context, client)
    val reading = gateway.day(FluidHealthMetric.Steps, java.time.LocalDate.of(2026, 11, 20), java.time.ZoneOffset.UTC, now = t0.plusSeconds(3600))
    assertEquals(FluidHealthReading.NoData, reading)
  }

  @Test
  fun `le unita' del provider diventano quelle del modulo - minuti, millilitri, chili, bpm`() = runBlocking {
    val client = FakeClient(emptyList(), emptySet())
    val gateway = FluidHealthConnectGateway(context, client)
    client.aggregateResult = result(mapOf(SleepSessionRecord.SLEEP_DURATION_TOTAL.metricKey to java.time.Duration.ofMinutes(432).toMillis()), emptyMap(), "dev.pampa.sounds")
    assertEquals(432.0, gateway.aggregate(FluidHealthMetric.Sleep, t0, t0.plusSeconds(3600)).total!!, 1e-9)
    client.aggregateResult = result(emptyMap(), mapOf(HydrationRecord.VOLUME_TOTAL.metricKey to 1.25), "x")
    assertEquals(1250.0, gateway.aggregate(FluidHealthMetric.Hydration, t0, t0.plusSeconds(3600)).total!!, 1e-6)
    client.aggregateResult = result(emptyMap(), mapOf(WeightRecord.WEIGHT_AVG.metricKey to 72.4, WeightRecord.WEIGHT_MIN.metricKey to 72.0, WeightRecord.WEIGHT_MAX.metricKey to 73.1), "x")
    val weight = gateway.aggregate(FluidHealthMetric.Weight, t0, t0.plusSeconds(3600))
    assertEquals(72.4, weight.average!!, 1e-9)
    assertEquals(73.1, weight.max!!, 1e-9)
    client.aggregateResult = result(
      mapOf(HeartRateRecord.BPM_MIN.metricKey to 54L, HeartRateRecord.BPM_MAX.metricKey to 131L, HeartRateRecord.BPM_AVG.metricKey to 68L, HeartRateRecord.MEASUREMENTS_COUNT.metricKey to 512L),
      emptyMap(), "x",
    )
    val heart = FluidHealthReadings.heart(gateway.aggregate(FluidHealthMetric.HeartRate, t0, t0.plusSeconds(3600)))!!
    assertEquals(54.0, heart.min, 0.0)
    assertEquals(512L, heart.count)
  }

  @Test
  fun `i tratti e i giorni vengono dai gruppi del provider, e i giorni senza gruppo restano vuoti`() = runBlocking {
    val client = FakeClient(emptyList(), emptySet())
    val gateway = FluidHealthConnectGateway(context, client)
    val withData = result(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 300L), emptyMap(), "x")
    val empty = result()
    client.byDuration = listOf(
      AggregationResultGroupedByDuration(withData, t0, t0.plusSeconds(900), ZoneOffset.UTC, false),
      AggregationResultGroupedByDuration(empty, t0.plusSeconds(900), t0.plusSeconds(1800), ZoneOffset.UTC, false),
    )
    val buckets = gateway.aggregateByDuration(FluidHealthMetric.Steps, t0, t0.plusSeconds(1800), java.time.Duration.ofMinutes(15))
    assertEquals(1, buckets.size)
    val first = java.time.LocalDate.of(2026, 11, 16)
    client.byPeriod = listOf(
      AggregationResultGroupedByPeriod(withData, first.atStartOfDay(), first.plusDays(1).atStartOfDay(), false),
      AggregationResultGroupedByPeriod(withData, first.plusDays(2).atStartOfDay(), first.plusDays(3).atStartOfDay(), false),
    )
    val week = gateway.week(FluidHealthMetric.Steps, first, 3, ZoneOffset.UTC)
    assertTrue(week[0] is FluidHealthReading.Value)
    assertEquals(FluidHealthReading.NoData, week[1])
    assertEquals(300.0, (week[2] as FluidHealthReading.Value).value, 0.0)
  }
}
