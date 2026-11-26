/*
 * engine-viewer-ocr (4.0, F8.17): «Leggi testo», come Testo attivo di iOS. Lo stato di una pagina, la lettura in
 * sottofondo con la memoria dei risultati, le parole. Il disegno e i gesti sono in FluidLiveTextOverlay.kt.
 * Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.viewer.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Le parole di «Leggi testo», in italiano di serie. */
@Immutable
data class FluidLiveTextStrings(
  val liveText: String = "Leggi testo",
  val on: String = "attivo",
  val off: String = "spento",
  val copy: String = "Copia",
  val copyAll: String = "Copia tutto",
  val selectAll: String = "Seleziona tutto",
  val share: String = "Condividi",
  val showText: String = "Testo",
  val sheetTitle: String = "Testo trovato",
  val search: String = "Cerca nel testo",
  val noMatches: String = "Nessun risultato",
  val copied: String = "Copiato",
  val analyzing: String = "Lettura del testo…",
  val lines: (Int) -> String = { n -> if (n == 1) "1 riga" else "$n righe" },
  val copyLine: String = "Copia la riga",
  val selection: (String) -> String = { text -> "Selezionato: $text" },
)

/**
 * «Leggi testo» nei visori: la configurazione del modulo. Con `engine-viewer-ocr` fra le dipendenze le foto, le
 * schermate e i PDF aperti con `rememberFluidViewerFiles` hanno da soli il tasto di Testo attivo, la selezione col dito,
 * la copia e la ricerca nelle parole trovate. Qui l'app cambia, se vuole, chi legge e le parole.
 */
object FluidLiveText {
  /** Chi legge: di serie Tesseract con l'italiano. */
  @Volatile
  var recognizer: (Context) -> FluidTextRecognizer = { FluidTesseractRecognizer(it) }

  /** Le parole. */
  @Volatile
  var strings: FluidLiveTextStrings = FluidLiveTextStrings()

  /** L'ultimo errore di una lettura (la foto che non si apre, il riconoscimento che manca), per i log dell'app. */
  @Volatile
  var lastError: Throwable? = null
    internal set

  /** Falso per spegnere «Leggi testo» in tutta l'app senza togliere il modulo. */
  @Volatile
  var enabled: Boolean = true

  /**
   * «Scansiona, poi leggi» (F8.17d): in una foto storta di un foglio si legge la pagina raddrizzata e le righe tornano
   * sulla foto. Di serie acceso; falso per leggere sempre la foto intera.
   */
  @Volatile
  var straightenSheets: Boolean = true

  /**
   * Quanto aspetta la pagina ferma prima di leggere: 450 ms. A occhio (25/11/2026): chi sfoglia di corsa non paga
   * letture, chi si ferma a guardare trova il tasto entro un secondo, come Foto.
   */
  const val SettleMillis: Long = 450L

  private val cache = LruCache<String, FluidRecognizedText>(48)
  private val reading = Mutex()

  /** Il risultato già letto per [key], o `null`. */
  fun cached(key: String): FluidRecognizedText? = cache.get(key)

  /** Dimentica i risultati (le prove, un file cambiato). */
  fun clearCache() = cache.evictAll()

  /**
   * Legge [load] per [key] (una lettura alla volta in tutto il processo: Tesseract è uno), o restituisce quella già
   * fatta. [load] dà la bitmap, che qui si ricicla dopo la lettura. Con [findSheet] (una foto, non una pagina di un
   * documento) e [straightenSheets], un foglio storto nella foto si legge raddrizzato.
   */
  suspend fun read(
    context: Context,
    key: String,
    findSheet: Boolean = false,
    load: suspend () -> Bitmap?,
  ): FluidRecognizedText? {
    cache.get(key)?.let { return it }
    // Tutto fuori dal filo principale (F8.17d): chi chiama sta in un LaunchedEffect, e il riconoscitore, i bordi del
    // foglio e il raddrizzamento sono lavoro vero.
    return reading.withLock {
      cache.get(key)?.let { return@withLock it }
      withContext(Dispatchers.Default) {
        val recognizer = recognizer(context.applicationContext ?: context)
        if (!recognizer.available) return@withContext null
        val bitmap = load() ?: return@withContext null
        // R1: Tesseract non si interrompe a metà pagina. Una lettura cominciata si finisce e si ricorda anche se chi
        // l'ha chiesta non la vuole più (la ricerca cambiata, la pagina sfogliata via): prima si buttava, e la ricerca
        // che ripartiva rileggeva la stessa pagina da capo.
        withContext(NonCancellable) {
          try {
            val result = if (findSheet && straightenSheets) SheetReading.recognize(recognizer, bitmap) else recognizer.recognize(bitmap)
            result.also { cache.put(key, it) }
          } finally {
            bitmap.recycle()
          }
        }
      }
    }
  }

  /** Mette in memoria un risultato già noto (le prove, un'app che ha letto da sé). */
  fun remember(key: String, text: FluidRecognizedText) {
    cache.put(key, text)
  }
}

/** A che punto è la lettura di una pagina. Non è un `enum` di proposito (regola 3.11). */
@Immutable
@JvmInline
value class FluidLiveTextStatus private constructor(private val id: Int) {
  override fun toString(): String = when (id) { 0 -> "Idle"; 1 -> "Reading"; 2 -> "Ready"; else -> "Unavailable" }

  companion object {
    val Idle: FluidLiveTextStatus = FluidLiveTextStatus(0)
    val Reading: FluidLiveTextStatus = FluidLiveTextStatus(1)
    /** Letta: [FluidLiveTextState.text] c'è (anche vuoto, se nell'immagine non c'è testo). */
    val Ready: FluidLiveTextStatus = FluidLiveTextStatus(2)
    /** Il riconoscimento non c'è su questo telefono, o la lettura è fallita. */
    val Unavailable: FluidLiveTextStatus = FluidLiveTextStatus(3)
  }
}

/**
 * Lo stato di «Leggi testo» per un'immagine (o una pagina di un PDF): il testo letto, la modalità attiva (il velo sulle
 * parti senza testo e le righe in evidenza, come il tasto di Testo attivo), la selezione in parole e la ricerca. Si
 * legge in composizione; si scrive sul filo principale.
 */
@Stable
class FluidLiveTextState(
  /** Larghezza su altezza dell'immagine: i riquadri sono in 0..1 dentro di lei. */
  val aspect: Float = 1f,
) {
  var status by mutableStateOf(FluidLiveTextStatus.Idle)
    internal set
  var text by mutableStateOf<FluidRecognizedText?>(null)
    internal set

  /** Lo stato del documento a cui appartiene questa pagina: la modalità e la ricerca sono le sue. */
  internal var parent: FluidLiveTextState? = null
  private var ownActive by mutableStateOf(false)
  private var ownQuery by mutableStateOf("")

  /** La modalità del tasto di Testo attivo. */
  var active: Boolean
    get() = parent?.active ?: ownActive
    set(value) {
      val p = parent
      if (p != null) p.active = value else ownActive = value
    }

  /** La selezione: la prima e l'ultima parola (nell'ordine della lettura), o `null`. */
  var selection by mutableStateOf<FluidTextMatch?>(null)

  /** La ricerca nelle parole trovate e i risultati. */
  var query: String
    get() = parent?.query ?: ownQuery
    set(value) {
      val p = parent
      if (p != null) p.query = value else ownQuery = value
    }
  val matches: List<FluidTextMatch> by derivedStateOf { text?.takeIf { query.isNotBlank() }?.search(query).orEmpty() }
  var current by mutableIntStateOf(0)

  /** L'elenco delle righe (il foglio «Testo trovato») aperto. */
  var sheet by mutableStateOf(false)

  /** Vero se c'è qualcosa da mostrare: la lettura è finita e ha trovato parole. */
  val hasText: Boolean get() = status == FluidLiveTextStatus.Ready && text?.isEmpty == false

  /** Il testo selezionato, o `null`. */
  val selectedText: String? get() {
    val s = selection ?: return null
    return text?.text(s.first, s.last)
  }

  /** Seleziona tutte le parole. */
  fun selectAll() {
    val t = text ?: return
    if (!t.isEmpty) selection = FluidTextMatch(0, t.words.lastIndex)
  }

  /** Mette il risultato di una lettura (anche dalle prove, o da un'app che legge da sé). */
  fun show(result: FluidRecognizedText?) {
    text = result
    status = if (result == null) FluidLiveTextStatus.Unavailable else FluidLiveTextStatus.Ready
    if (result == null || result.isEmpty) {
      // Una pagina di un documento senza testo non spegne la modalità del documento.
      if (parent == null) active = false
      selection = null
    }
  }

  internal suspend fun read(block: suspend () -> FluidRecognizedText?) {
    if (status == FluidLiveTextStatus.Ready) return
    status = FluidLiveTextStatus.Reading
    val result = try {
      block()
    } catch (e: CancellationException) {
      status = FluidLiveTextStatus.Idle
      throw e
    } catch (e: Throwable) {
      FluidLiveText.lastError = e
      // R1: solo il tipo. Il messaggio di un file che non si apre ha il percorso o l'indirizzo (il nome della foto):
      // niente dati personali nei log. L'eccezione intera resta in lastError, per i log dell'app.
      android.util.Log.w("FluidLiveText", "Lettura del testo non riuscita (${e.javaClass.simpleName})")
      null
    }
    show(result)
  }
}
