package dev.antigravity.fluidengine.search.semantic

import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import dev.antigravity.fluidengine.search.FluidEmbeddingSpace
import dev.antigravity.fluidengine.search.FluidTextEmbedder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Un modello del significato da scaricare o da trovare sul telefono: il file, quanto pesa, la sua impronta e da dove
 * viene. Il modulo non lo porta: lo porta l'app (scaricato col consenso, o da un'altra app Pampa).
 */
class FluidEmbeddingModel(
  val fileName: String,
  val sizeBytes: Long,
  val sha256: String,
  val url: String,
  val license: String,
  val space: FluidEmbeddingSpace,
)

/** I modelli che l'engine conosce. */
object FluidEmbeddingModels {
  /**
   * **EmbeddingGemma 2 Text 270M** per LiteRT-LM (litert-community, commit `9be6e8b9`, Apache-2.0, non «gated»): 270
   * milioni di parametri a 4 bit, piu' di cento lingue, l'italiano compreso; 768 dimensioni (Matrjoska fino a 128). E' il
   * modello delle domande di MyCloud Gallery 2 (stesso file, stessa impronta: `ModelCatalog.QUERY_270M`), quindi lo stesso
   * spazio di vettori. Misure in `moduli/search.md`.
   */
  val EmbeddingGemma2Text270M: FluidEmbeddingModel = FluidEmbeddingModel(
    fileName = "embeddinggemma-2-text-270m.litertlm",
    sizeBytes = 164_626_432L,
    sha256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb",
    url = "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/" +
      "9be6e8b90982095dc05c2bd162e4b954ee4dbac7/embeddinggemma-2-text-270m.litertlm",
    license = "Apache-2.0",
    space = FluidEmbeddingSpace.EmbeddingGemma2,
  )

  /** Vero se [file] e' proprio [model]: la dimensione e lo SHA-256 (lettura intera: fuori dal filo principale). */
  fun verify(file: File, model: FluidEmbeddingModel = EmbeddingGemma2Text270M): Boolean {
    if (!file.isFile || file.length() != model.sizeBytes) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered(1 shl 20).use { input ->
      val buffer = ByteArray(1 shl 20)
      while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } == model.sha256
  }
}

/**
 * Il modello del significato sul telefono, con **LiteRT-LM** (`EmbeddingEngine`, CPU), come MyCloud Gallery 2: lo stesso
 * motore, lo stesso modello, gli stessi vettori.
 *
 * - **Si carica al primo [embed]** (o con [warmUp]) e si libera con [release]: il motore occupa ~330 MB di memoria (misura
 *   di Google sul S26 Ultra; sul Tab S9 nel rapporto di F8.22), quindi chi lo usa lo libera quando la ricerca si chiude.
 * - **Un testo alla volta**, su un filo suo: le chiamate si mettono in fila.
 * - **I vettori** sono normalizzati dal motore; con [dimensions] minore di 768 il motore li taglia (Matrjoska).
 * - **Il modello per percorso** ([model]), con la cartella [cacheDir] dove LiteRT-LM salva il programma preparato: col
 *   percorso il secondo avvio e' due volte piu' veloce (MyCloud Gallery 2, P4: 0,39 s contro 0,77 s sull'S25).
 *
 * @param maxInputTokens la lunghezza massima di un testo, in parti di parola: 256 (i pezzi dell'indice sono di 700
 *   caratteri, circa 200).
 */
class FluidLiteRtEmbedder(
  private val model: File,
  private val cacheDir: File,
  dimensions: Int = FluidEmbeddingSpace.EmbeddingGemma2.dimensions,
  private val maxInputTokens: Int = 256,
) : FluidTextEmbedder {

  override val space: FluidEmbeddingSpace = FluidEmbeddingSpace(FluidEmbeddingSpace.EmbeddingGemma2.id, dimensions)

  private val mutex = Mutex()
  private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "fluid-embedder").apply { isDaemon = true } }
  private val dispatcher = thread.asCoroutineDispatcher()
  private var engine: EmbeddingEngine? = null
  private val options = EmbeddingOptions(normalize = true, outputSize = dimensions)

  /** I millisecondi dell'ultimo avvio del motore (-1: mai avviato). */
  @Volatile
  var lastInitMillis: Long = -1
    private set

  /** I millisecondi dell'ultimo [embed], diviso per i testi. */
  @Volatile
  var lastEmbedMillisPerText: Double = -1.0
    private set

  /** Avvia il motore se non c'e': i millisecondi dell'avvio (0 se era gia' acceso). */
  suspend fun warmUp(): Long = mutex.withLock { withContext(dispatcher) { ensure() } }

  override suspend fun embed(texts: List<String>): List<FloatArray> {
    if (texts.isEmpty()) return emptyList()
    return mutex.withLock {
      withContext(dispatcher) {
        ensure()
        val active = engine ?: error("motore non avviato")
        val start = SystemClock.elapsedRealtime()
        val out = texts.map { text ->
          require(text.isNotBlank()) { "testo vuoto" }
          active.computeEmbedding(listOf(InputData.Text(text)), options).embedding
        }
        lastEmbedMillisPerText = (SystemClock.elapsedRealtime() - start).toDouble() / texts.size
        out
      }
    }
  }

  override suspend fun release() {
    mutex.withLock {
      withContext(NonCancellable + dispatcher) {
        runCatching { engine?.close() }
        engine = null
      }
    }
  }

  private fun ensure(): Long {
    if (engine != null) return 0
    cacheDir.mkdirs()
    val start = SystemClock.elapsedRealtime()
    val config = EmbeddingEngineConfig(
      modelPath = model.path,
      backend = Backend.CPU(),
      visionBackend = null,
      audioBackend = null,
      cacheDir = cacheDir.path,
      modelFd = null,
      maxInputLength = maxInputTokens,
    )
    val created = EmbeddingEngine(config)
    try {
      created.initialize()
    } catch (t: Throwable) {
      runCatching { created.close() }
      throw t
    }
    engine = created
    lastInitMillis = SystemClock.elapsedRealtime() - start
    return lastInitMillis
  }
}
