package dev.antigravity.fluidengine.sync

import dev.antigravity.fluidengine.sync.testing.FakeSyncRelay
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking

/*
 * Il banco di prove della sincronizzazione (F8.21): un mondo con un relay in memoria, l'ora vera che avanza a comando e
 * dispositivi finti, ciascuno col suo registro, il suo orologio (sfasato a piacere) e la sua rete davanti al relay.
 */

/** Un giorno, in millisecondi. */
const val Day: Long = 24L * 60 * 60 * 1000

/**
 * Un sigillo vero per le prove: AES-256-GCM con i dati associati del [SyncSealContext], come quello che mettera' l'account
 * (che usera' Bouncy Castle e le sue chiavi). Conta quante volte ha sigillato e aperto.
 */
class AesTestSealer(key: ByteArray = ByteArray(32) { (it * 7 + 3).toByte() }) : SyncSealer {
  private val secret = SecretKeySpec(key, "AES")
  private val random = SecureRandom()
  @Volatile var sealed = 0
  @Volatile var opened = 0

  override suspend fun seal(context: SyncSealContext, plaintext: ByteArray): SyncSealedPayload {
    val iv = ByteArray(12).also(random::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
    cipher.updateAAD(context.associatedData())
    sealed++
    return SyncSealedPayload(iv + cipher.doFinal(plaintext), keyVersion = 1)
  }

  override suspend fun open(context: SyncSealContext, payload: SyncSealedPayload): ByteArray {
    val bytes = payload.bytes
    if (bytes.size < 28) throw SyncSealException("troppo corto")
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, bytes, 0, 12))
    cipher.updateAAD(context.associatedData())
    opened++
    return try {
      cipher.doFinal(bytes, 12, bytes.size - 12)
    } catch (e: javax.crypto.AEADBadTagException) {
      throw SyncSealException("il sigillo non torna per $context", e)
    }
  }
}

/** Il mondo del banco: l'ora vera, il relay e i dispositivi. */
class BenchWorld(start: Long = 1_795_000_000_000L) {
  /** L'ora vera (quella del relay). */
  @Volatile var time: Long = start

  val relay = MemorySyncRelay(now = { time })
  val sealer = AesTestSealer()

  /** Un registro di ogni chiamata al deposito fatta su un thread diverso da quello dell'engine. */
  val wrongThread = AtomicReference<String?>(null)

  private val devices = ArrayList<BenchDevice>()

  fun advance(millis: Long) {
    time += millis
  }

  /** Un dispositivo nuovo. [skewMillis] e' quanto il suo orologio e' avanti (positivo) o indietro (negativo). */
  fun device(name: String, skewMillis: Long = 0, config: SyncConfig = SyncConfig()): BenchDevice =
    BenchDevice(this, name, skewMillis, config).also(devices::add)

  fun close() = devices.forEach { it.close() }
}

/** Un registro che controlla il thread di ogni transazione: deve essere sempre quello dell'engine. */
class ThreadCheckingStore(private val inner: SyncStore, private val expected: String, private val wrong: AtomicReference<String?>) : SyncStore {
  @Volatile var transactions = 0

  override fun <T> transaction(block: SyncStoreTransaction.() -> T): T {
    val name = Thread.currentThread().name
    if (!name.startsWith(expected)) wrong.compareAndSet(null, name)
    transactions++
    return inner.transaction(block)
  }
}

/** Un dispositivo finto: il suo registro, la sua rete, il suo orologio. */
class BenchDevice(
  val world: BenchWorld,
  val name: String,
  var skewMillis: Long,
  val config: SyncConfig,
) {
  /** Il «disco» del dispositivo: sopravvive a [restart]. */
  val disk = InMemorySyncStore()

  /** La rete del dispositivo, davanti al relay comune. */
  val net = FakeSyncRelay(world.relay)

  private val threadName = "sync-$name"
  private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, threadName).apply { isDaemon = true } }
  val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()
  val store = ThreadCheckingStore(disk, threadName, world.wrongThread)

  var sync: FluidSync = newEngine()
    private set

  private fun newEngine() = FluidSync(
    store = store,
    relay = net,
    sealer = world.sealer,
    node = name,
    wall = { world.time + skewMillis },
    dispatcher = dispatcher,
    config = config,
  )

  /** Il processo muore e riparte: stesso disco, motore nuovo. */
  fun restart() {
    sync = newEngine()
  }

  fun space(id: String = "spesa"): SyncSpace = sync.space(SyncSpaceId(id))

  fun edit(record: String, space: String = "spesa", block: SyncEditor.() -> Unit): Boolean =
    runBlocking { space(space).edit(record, block) }

  fun delete(record: String, space: String = "spesa"): Boolean = runBlocking { space(space).delete(record) }

  fun create(space: String = "spesa", block: SyncEditor.() -> Unit): String = runBlocking { space(space).create(block) }

  fun syncNow(space: String = "spesa"): SyncSpaceReport = runBlocking { space(space).sync() }

  fun syncAll(): SyncReport = runBlocking { sync.syncAll() }

  fun state(space: String = "spesa"): SyncSpaceState = runBlocking {
    space(space).load()
    space(space).state.value
  }

  fun records(space: String = "spesa"): Map<String, SyncDocument> = state(space).records

  fun doc(record: String, space: String = "spesa"): SyncDocument? = runBlocking { space(space).document(record) }

  fun journal(space: String = "spesa"): List<SyncJournalEntry> = disk.transaction { journal(space) }

  fun stored(space: String = "spesa"): List<SyncStoredRecord> = disk.transaction { records(space) }

  fun goOffline() {
    net.online = false
  }

  fun goOnline() {
    net.online = true
  }

  fun close() = executor.shutdownNow()

  override fun toString(): String = "dispositivo $name"
}

/** Sincronizza tutti finche' nessuno ha piu' niente da mandare ne' da ricevere (al massimo [rounds] giri). */
fun BenchWorld.settle(vararg devices: BenchDevice, space: String = "spesa", rounds: Int = 6) {
  repeat(rounds) {
    devices.forEach { it.syncNow(space) }
  }
}

/** Il testo in chiaro di ogni riga del relay, aperto col sigillo del mondo: cosa sa il relay di uno spazio, decifrato. */
fun BenchWorld.relayDocuments(space: String = "spesa"): Map<String, SyncDocument> = runBlocking {
  relay.rows(SyncSpaceId(space)).associate { row ->
    row.recordId to (
      row.payload?.let { SyncDocumentCodec.decode(sealer.open(SyncSealContext(SyncSpaceId(space), row.recordId, row.version), it)) }
        ?: SyncDocument.tombstone(SyncHlc.Zero)
      )
  }
}
