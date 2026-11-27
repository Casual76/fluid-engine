package dev.antigravity.fluidengine.search

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.antigravity.fluidengine.foundation.FluidDeviceConditions
import dev.antigravity.fluidengine.foundation.FluidWork
import dev.antigravity.fluidengine.foundation.FluidWorkNeeds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield

// F8.22 · L'aggiornamento a pezzi, in sottofondo, e il lavoro pesante solo in carica (nota del brief: «l'indicizzazione
// pesante solo in carica, e dove serve in Wi-Fi, a pezzi, in sottofondo»). Il riferimento Apple e' `CSIndexExtension`
// (Core Spotlight chiede all'estensione di reindicizzare quando vuole lui, a pezzi) e i lavori «in carica» di
// `BGProcessingTask` (`requiresExternalPower`).

/**
 * Da dove arrivano i documenti di un indice: l'app li elenca a pezzi, da un segno in poi (il suo `updatedAt`, un
 * numero di versione, un indice di pagina). L'engine non sa cosa siano.
 */
interface FluidSearchSource {
  /** Il nome della sorgente: un indice puo' averne piu' d'una (note e trascrizioni). */
  val id: String

  /** Le modifiche dopo [cursor] (null: dall'inizio), al piu' [limit] fra nuove, cambiate e tolte. */
  suspend fun changes(cursor: String?, limit: Int): FluidSearchChanges
}

/**
 * Le regole dell'aggiornamento.
 *
 * @property chunk quanti documenti per pezzo (una transazione): 100 (a occhio, 25/11/2026: sul Tab S9 un pezzo di 100 note
 *   corte sta sotto i 60 ms, e il filo dell'IO non tiene ferme le ricerche piu' di cosi').
 * @property foregroundDocs quanti documenti al piu' si indicizzano con l'app aperta, in una volta: oltre, il resto
 *   («pesante») aspetta la carica ([FluidSearchWork]).
 * @property heavyNeedsCharging il lavoro pesante vuole la corrente (di serie si').
 * @property heavyNeedsWifi il lavoro pesante vuole una rete che non costa: per una sorgente che scarica (i corpi delle mail).
 */
data class FluidSearchIndexPolicy(
  val chunk: Int = 100,
  val foregroundDocs: Int = 300,
  val heavyNeedsCharging: Boolean = true,
  val heavyNeedsWifi: Boolean = false,
) {
  init {
    require(chunk in 1..2000) { "FluidSearchIndexPolicy: chunk fuori da 1..2000" }
    require(foregroundDocs >= 0) { "FluidSearchIndexPolicy: foregroundDocs negativo" }
  }

  /** I vincoli del lavoro pesante, nel linguaggio delle condizioni dell'engine. */
  val heavyNeeds: FluidWorkNeeds get() = FluidWorkNeeds(wifiOnly = heavyNeedsWifi, chargingOnly = heavyNeedsCharging)
}

/**
 * A che punto e' un aggiornamento: un valore intero, da mettere com'e' in uno stato.
 *
 * @property indexed quanti documenti sono entrati (o usciti) in questo giro.
 * @property hasMore resta del lavoro, che aspetta la carica (o il giro dopo).
 * @property tookMillis quanto e' durato il giro.
 */
data class FluidSearchProgress(
  val indexed: Int = 0,
  val running: Boolean = false,
  val hasMore: Boolean = false,
  val tookMillis: Long = 0,
  val error: String? = null,
)

/** Un lavoro dell'indice che il lavoro in sottofondo sa far girare: le parole ([FluidSearchIndexer]) o il significato. */
interface FluidSearchJob {
  /** Un giro pesante, fino a [deadlineMillis] (orologio di `SystemClock.elapsedRealtime`) o a [stop]. Vero se resta lavoro. */
  suspend fun runHeavy(deadlineMillis: Long, stop: () -> Boolean): Boolean
}

/**
 * L'aggiornamento di un indice da una sorgente, a pezzi ([FluidSearchIndexPolicy.chunk]): ogni pezzo entra col suo
 * segno in una transazione sola, cosi' un'app chiusa a meta' riparte dal pezzo dopo.
 *
 * Con l'app aperta ([update]) si fa il lavoro leggero, fino a [FluidSearchIndexPolicy.foregroundDocs]; se resta lavoro
 * (la prima volta, con migliaia di note) si chiama [FluidSearchWork.schedule] e il resto lo fa il lavoro in carica.
 *
 * Una sorgente che dice «ce n'e' ancora» ([FluidSearchChanges.hasMore]) senza dare niente e senza far avanzare il segno
 * ferma il giro con [FluidSearchProgress.error] e [FluidSearchProgress.hasMore] falso (R3): la stessa domanda darebbe la
 * stessa pagina. Una pagina vuota col segno nuovo (una sorgente che salta cio' che non indicizza) va bene.
 */
class FluidSearchIndexer(
  val index: FluidSearchIndex,
  val source: FluidSearchSource,
  val policy: FluidSearchIndexPolicy = FluidSearchIndexPolicy(),
) : FluidSearchJob {
  private val _progress = MutableStateFlow(FluidSearchProgress())

  /** L'ultimo giro. */
  val progress: StateFlow<FluidSearchProgress> = _progress.asStateFlow()

  /**
   * Un giro leggero (con [heavy] falso: fino a [FluidSearchIndexPolicy.foregroundDocs]) o completo. Restituisce come e'
   * finito; [FluidSearchProgress.hasMore] vero vuol dire che il resto aspetta il lavoro in carica.
   */
  suspend fun update(heavy: Boolean = false): FluidSearchProgress =
    run(maxDocs = if (heavy) Int.MAX_VALUE else policy.foregroundDocs, deadlineMillis = Long.MAX_VALUE) { false }

  override suspend fun runHeavy(deadlineMillis: Long, stop: () -> Boolean): Boolean =
    run(Int.MAX_VALUE, deadlineMillis, stop).hasMore

  // La sorgente e il modello lavorano fuori dal filo principale, anche se chi chiama sta li' (un tasto, un effetto).
  private suspend fun run(maxDocs: Int, deadlineMillis: Long, stop: () -> Boolean): FluidSearchProgress = withContext(Dispatchers.IO) {
    runHere(maxDocs, deadlineMillis, stop)
  }

  private suspend fun runHere(maxDocs: Int, deadlineMillis: Long, stop: () -> Boolean): FluidSearchProgress {
    val started = SystemClock.elapsedRealtime()
    var done = 0
    var hasMore = true
    _progress.value = FluidSearchProgress(running = true)
    try {
      var cursor = index.cursor(source.id)
      while (hasMore) {
        if (done >= maxDocs || stop() || SystemClock.elapsedRealtime() >= deadlineMillis) break
        val changes = source.changes(cursor, policy.chunk)
        // R3: «ce n'e' ancora» senza niente di nuovo e con lo stesso segno: richiamarla darebbe la stessa pagina, per
        // sempre (in primo piano non c'e' una scadenza). Ci si ferma e lo si dice; il lavoro in carica non riparte.
        if (changes.hasMore && changes.upserts.isEmpty() && changes.deletes.isEmpty() && changes.cursor == cursor) {
          _progress.value = FluidSearchProgress(indexed = done, hasMore = false, tookMillis = SystemClock.elapsedRealtime() - started,
            error = "la sorgente ${source.id} non va avanti: nessuna modifica e lo stesso segno")
          return _progress.value
        }
        index.apply(source.id, changes)
        done += changes.upserts.size + changes.deletes.size
        cursor = changes.cursor
        hasMore = changes.hasMore
        _progress.value = FluidSearchProgress(indexed = done, running = hasMore, hasMore = hasMore,
          tookMillis = SystemClock.elapsedRealtime() - started)
        yield()
      }
    } catch (e: CancellationException) {
      _progress.value = _progress.value.copy(running = false)
      throw e
    } catch (e: Exception) {
      _progress.value = FluidSearchProgress(indexed = done, hasMore = true, tookMillis = SystemClock.elapsedRealtime() - started,
        error = e.message ?: e::class.simpleName)
      return _progress.value
    }
    _progress.value = FluidSearchProgress(indexed = done, hasMore = hasMore, tookMillis = SystemClock.elapsedRealtime() - started)
    return _progress.value
  }
}

/**
 * Il modello del significato (F8.22, `ricerca-in-tutte-le-app`): trasforma un testo in un vettore, cosi' «la mail del
 * dentista» trova «Studio Odontoiatrico Rossi». L'implementazione di serie e' `FluidLiteRtEmbedder` di
 * `engine-search-semantic` (EmbeddingGemma 2 con LiteRT-LM, tutto sul telefono); un'app senza il modello puo' chiedere i
 * vettori all'app che lo ha (`FluidSearchBridge.remoteEmbedder`).
 */
interface FluidTextEmbedder {
  /** Lo spazio dei vettori: vettori di spazi diversi non si confrontano. */
  val space: FluidEmbeddingSpace

  /** I vettori (norma 1) dei [texts], nello stesso ordine. Lavora fuori dal filo principale. */
  suspend fun embed(texts: List<String>): List<FloatArray>

  /** Libera il modello e la sua memoria; il prossimo [embed] lo ricarica. */
  suspend fun release() {}
}

/**
 * Uno spazio di vettori: il modello e la sua ricetta. [id] e' quello di MyCloud Gallery per EmbeddingGemma 2
 * («embeddinggemma2-float-v1»): stesse domande, stessi vettori, e la ricerca in tutte le app puo' mandare a Gallery lo
 * stesso vettore. [dimensions] e' la lunghezza piena (768); i vettori Matrjoska si possono tagliare (256, 128).
 */
data class FluidEmbeddingSpace(val id: String, val dimensions: Int) {
  init {
    require(id.isNotBlank()) { "FluidEmbeddingSpace: id vuoto" }
    require(dimensions > 0) { "FluidEmbeddingSpace: dimensioni non positive" }
  }

  companion object {
    /** EmbeddingGemma 2 (270M solo testo, o 440M testo e immagini: stesso spazio), 768 dimensioni, senza prefissi. */
    val EmbeddingGemma2: FluidEmbeddingSpace = FluidEmbeddingSpace("embeddinggemma2-float-v1", 768)
  }
}

/**
 * Il significato dei documenti di un indice: chiede al modello i vettori di chi non li ha (o li ha di un testo vecchio),
 * a pezzi. E' sempre lavoro pesante: il modello costa decine di millisecondi per pezzo di testo (misure nella scheda del
 * modulo), e trentamila documenti si fanno solo in carica ([FluidSearchWork]).
 *
 * @param batch quanti documenti per giro (i loro pezzi vanno al modello insieme).
 */
class FluidSemanticIndexer(
  val index: FluidSearchIndex,
  val embedder: FluidTextEmbedder,
  private val batch: Int = 8,
) : FluidSearchJob {
  private val _progress = MutableStateFlow(FluidSearchProgress())

  /** L'ultimo giro. */
  val progress: StateFlow<FluidSearchProgress> = _progress.asStateFlow()

  /** Fa i vettori che mancano, fino a [maxDocs] documenti. Restituisce come e' finito. */
  suspend fun update(maxDocs: Int = Int.MAX_VALUE): FluidSearchProgress = run(maxDocs, Long.MAX_VALUE) { false }

  override suspend fun runHeavy(deadlineMillis: Long, stop: () -> Boolean): Boolean = run(Int.MAX_VALUE, deadlineMillis, stop).hasMore

  // La sorgente e il modello lavorano fuori dal filo principale, anche se chi chiama sta li' (un tasto, un effetto).
  private suspend fun run(maxDocs: Int, deadlineMillis: Long, stop: () -> Boolean): FluidSearchProgress = withContext(Dispatchers.IO) {
    runHere(maxDocs, deadlineMillis, stop)
  }

  private suspend fun runHere(maxDocs: Int, deadlineMillis: Long, stop: () -> Boolean): FluidSearchProgress {
    val started = SystemClock.elapsedRealtime()
    var done = 0
    var hasMore = true
    _progress.value = FluidSearchProgress(running = true)
    try {
      index.useMeaningSpace(embedder.space)
      while (hasMore) {
        if (done >= maxDocs || stop() || SystemClock.elapsedRealtime() >= deadlineMillis) break
        val pending = index.pendingMeaning(minOf(batch, maxDocs - done))
        if (pending.isEmpty()) {
          hasMore = false
          break
        }
        for (doc in pending) {
          val vectors = if (doc.passages.isEmpty()) emptyList() else embedder.embed(doc.passages)
          index.putMeaning(doc, vectors)
          done++
        }
        _progress.value = FluidSearchProgress(indexed = done, running = true, hasMore = true, tookMillis = SystemClock.elapsedRealtime() - started)
        yield()
      }
      if (hasMore) hasMore = index.pendingMeaningCount() > 0
    } catch (e: CancellationException) {
      _progress.value = _progress.value.copy(running = false)
      throw e
    } catch (e: Exception) {
      _progress.value = FluidSearchProgress(indexed = done, hasMore = true, tookMillis = SystemClock.elapsedRealtime() - started,
        error = e.message ?: e::class.simpleName)
      return _progress.value
    }
    _progress.value = FluidSearchProgress(indexed = done, hasMore = hasMore, tookMillis = SystemClock.elapsedRealtime() - started)
    return _progress.value
  }
}

/**
 * L'app che tiene gli indici nella sua `Application`: il lavoro in carica parte anche ad app chiusa, in un processo
 * nuovo, e chiede qui cosa far girare.
 */
interface FluidSearchHost {
  /** I lavori pesanti degli indici (le parole, poi il significato), nell'ordine in cui farli. */
  suspend fun fluidSearchJobs(): List<FluidSearchJob>
}

/**
 * Il lavoro pesante in sottofondo (F8.22): WorkManager, solo **in carica** e con la batteria non scarica (e solo in Wi-Fi
 * se lo chiede la politica), un giro per volta fino a [FluidSearchWork.RunMillis]; se resta lavoro se ne mette in coda
 * un altro.
 */
class FluidSearchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val host = applicationContext as? FluidSearchHost ?: return Result.success()
    val deadline = SystemClock.elapsedRealtime() + FluidSearchWork.RunMillis
    var more = false
    for (job in host.fluidSearchJobs()) {
      if (isStopped) return Result.success()
      more = job.runHeavy(deadline) { isStopped } || more
    }
    if (more && !isStopped) FluidSearchWork.schedule(applicationContext, wifiOnly = inputData.getBoolean(KeyWifi, false), append = true)
    return Result.success()
  }

  internal companion object {
    const val KeyWifi = "wifi"
  }
}

/** Il lavoro pesante degli indici dell'app, con WorkManager. */
object FluidSearchWork {
  /** Il nome del lavoro: uno per app. */
  const val UniqueName: String = "fluid-search-heavy"

  /**
   * Quanto dura al piu' un giro: 8 minuti, sotto i 10 che WorkManager concede a un lavoro (fiducia alta: documentazione
   * di `ListenableWorker`).
   */
  const val RunMillis: Long = 8 * 60_000L

  /** Il genere di lavoro, per le condizioni dell'engine: niente rete, si ferma col risparmio energetico e col caldo. */
  val HeavyIndexing: FluidWork = FluidWork.custom(
    name = "search-heavy-index",
    needsNetwork = false,
    avoidsLowPower = true,
    avoidsHeat = true,
  )

  /**
   * Mette in coda il lavoro pesante: parte quando il telefono e' in carica (e con [wifiOnly] su una rete che non costa).
   * Un lavoro gia' in coda resta quello ([append] lo accoda a quello in corso).
   */
  fun schedule(context: Context, wifiOnly: Boolean = false, append: Boolean = false) {
    val constraints = Constraints.Builder()
      .setRequiresCharging(true)
      .setRequiresBatteryNotLow(true)
      .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED)
      .build()
    val request = OneTimeWorkRequestBuilder<FluidSearchWorker>()
      .setConstraints(constraints)
      .setInputData(androidx.work.Data.Builder().putBoolean(FluidSearchWorker.KeyWifi, wifiOnly).build())
      .addTag(UniqueName)
      .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
      UniqueName,
      if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
      request,
    )
  }

  /**
   * Vero se adesso si puo' fare il lavoro pesante **in primo piano** (l'app aperta e il telefono gia' in carica): la
   * corrente, e le condizioni dell'engine se qualcuno le legge (risparmio energetico, caldo, Wi-Fi).
   */
  fun heavyAllowedNow(context: Context, policy: FluidSearchIndexPolicy = FluidSearchIndexPolicy()): Boolean {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    if (policy.heavyNeedsCharging && !plugged) return false
    val conditions = FluidDeviceConditions.current.let { if (it.known) it.copy(charging = plugged) else it }
    return conditions.allows(HeavyIndexing, policy.heavyNeeds).allowed
  }
}
