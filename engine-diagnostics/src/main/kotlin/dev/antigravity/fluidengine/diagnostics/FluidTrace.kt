package dev.antigravity.fluidengine.diagnostics

import java.util.concurrent.atomic.AtomicInteger

// F8.24 · Le tracce dei tempi (`tracce-dei-tempi`, rivista da Alessio): «da mettere come tool da usare solo per versioni
// dev o debug, di default non nelle release». Come OSSignposter di Apple, che segna intervalli da guardare in
// Instruments: qui si segnano con AndroidX Tracing e si guardano in Perfetto (ui.perfetto.dev).
//
// Come resta fuori dalla release. Il modulo ha due versioni di [FluidTraceBackend]: `src/debug` scrive con AndroidX
// Tracing, `src/release` è vuota e ha `Enabled = false`, una costante. Le funzioni qui sotto sono `inline` e chiedono
// prima `if (FluidTraceBackend.Enabled)`: nella release il compilatore di Kotlin toglie il ramo già quando compila l'app
// (la costante è falsa), e non resta nessuna chiamata a Trace. Una build dell'app «dev» prende la versione di debug con
// `matchingFallbacks = ['debug']`; la bench della galleria e ogni release prendono quella vuota. Lo provano
// FluidTraceReleaseTest (testReleaseUnitTest) e il controllo del dex della bench nel rapporto di F8.24.

/**
 * Il nome di un intervallo: scelto dal codice, mai dai dati. Solo lettere minuscole, cifre, `.`, `_`, `:` e `-`, da 1 a
 * 64 caratteri: un nome di file o un indirizzo (che possono contenere dati privati, rischio della scheda) non passa.
 * Si dichiara una volta come costante: `val ApriAlbum = FluidTraceName("gallery:apri-album")`.
 */
class FluidTraceName(val value: String) {
  init {
    require(Pattern.matches(value)) { "Nome di traccia non valido: «$value» (a-z, 0-9, ., _, :, -, fino a 64)" }
  }

  override fun equals(other: Any?): Boolean = other is FluidTraceName && other.value == value
  override fun hashCode(): Int = value.hashCode()
  override fun toString(): String = value

  private companion object {
    val Pattern = Regex("^[a-z0-9][a-z0-9._:-]{0,63}$")
  }
}

/**
 * I nomi comuni degli intervalli, uguali in tutte le app Pampa: in Perfetto si cercano allo stesso modo. Ogni app ne
 * aggiunge di suoi con lo stesso stile (`app:cosa`).
 */
object FluidTraceNames {
  /** Dal tocco dell'icona al primo schermo pronto da usare. */
  val FirstScreen: FluidTraceName = FluidTraceName("pampa:primo-schermo")

  /** Leggere un file dal disco (o da un Uri). */
  val ReadFile: FluidTraceName = FluidTraceName("pampa:leggi-file")

  /** Scrivere un file. */
  val WriteFile: FluidTraceName = FluidTraceName("pampa:scrivi-file")

  /** Una richiesta di rete, dalla partenza all'ultima riga letta. */
  val Network: FluidTraceName = FluidTraceName("pampa:rete")

  /** Una lettura o scrittura del database. */
  val Database: FluidTraceName = FluidTraceName("pampa:database")

  /** Decodificare un'immagine o un documento. */
  val Decode: FluidTraceName = FluidTraceName("pampa:decodifica")

  /** Dal tocco su un elemento (un album, una nota) al suo contenuto pronto: attraversa I/O e fili. */
  val OpenItem: FluidTraceName = FluidTraceName("pampa:apri")

  /** La sincronizzazione, dall'inizio alla fine. */
  val Sync: FluidTraceName = FluidTraceName("pampa:sincronizza")
}

/**
 * Un intervallo aperto con [FluidTrace.begin], da chiudere con [end]: per ciò che comincia in un posto e finisce in un
 * altro (il tocco di un album e la prima miniatura disegnata). [end] chiude una volta sola; le altre non fanno niente.
 */
class FluidTraceInterval internal constructor(private val name: FluidTraceName, private val cookie: Int) {
  @Volatile
  private var open = true

  /** Chiude l'intervallo. */
  fun end() {
    if (!open) return
    open = false
    if (FluidTraceBackend.Enabled) FluidTraceBackend.endAsync(name.value, cookie)
  }
}

/**
 * Le tracce dei tempi dell'engine (F8.24): intervalli col nome che si vedono in Perfetto, **solo nelle build di debug e
 * dev**. Nella release ogni chiamata è vuota (vedi la nota in cima al file) e il blocco gira com'è.
 *
 * ```kotlin
 * val miniature = FluidTrace.section(FluidTraceNames.Decode) { decodifica(file) }
 * val album = FluidTrace.async(FluidTraceNames.OpenItem) { repository.apri(id) }   // attraversa I/O e fili
 * ```
 *
 * Tutte chiudono l'intervallo anche se il blocco lancia o la coroutine viene annullata.
 */
object FluidTrace {
  private val cookies = AtomicInteger(1)

  /** Vero se questa build scrive le tracce (debug e dev). Falso, sempre, nella release. */
  val isAvailable: Boolean get() = FluidTraceBackend.Enabled

  /** Vero se in questo momento qualcuno registra (Perfetto o `atrace`): per non preparare niente quando nessuno guarda. */
  val isRecording: Boolean get() = FluidTraceBackend.Enabled && FluidTraceBackend.isRecording()

  /** [block] in un intervallo [name] sullo stesso filo. Si chiude anche se [block] lancia. */
  inline fun <T> section(name: FluidTraceName, block: () -> T): T {
    if (!FluidTraceBackend.Enabled) return block()
    FluidTraceBackend.begin(name.value)
    try {
      return block()
    } finally {
      FluidTraceBackend.end()
    }
  }

  /**
   * [block] in un intervallo [name] che può cambiare filo (una sospensione, un `withContext(Dispatchers.IO)`): in
   * Perfetto è una riga a sé. Si chiude anche se [block] lancia o viene annullato.
   */
  suspend inline fun <T> async(name: FluidTraceName, crossinline block: suspend () -> T): T {
    if (!FluidTraceBackend.Enabled) return block()
    val cookie = nextCookie()
    FluidTraceBackend.beginAsync(name.value, cookie)
    try {
      return block()
    } finally {
      FluidTraceBackend.endAsync(name.value, cookie)
    }
  }

  /** Apre un intervallo [name] da chiudere altrove con [FluidTraceInterval.end]. */
  fun begin(name: FluidTraceName): FluidTraceInterval {
    if (!FluidTraceBackend.Enabled) return FluidTraceInterval(name, 0)
    val cookie = nextCookie()
    FluidTraceBackend.beginAsync(name.value, cookie)
    return FluidTraceInterval(name, cookie)
  }

  /** Un contatore con nome ([name]) che vale [value] da adesso: le miniature in coda, i byte letti. */
  fun counter(name: FluidTraceName, value: Long) {
    if (FluidTraceBackend.Enabled) FluidTraceBackend.counter(name.value, value)
  }

  @PublishedApi
  internal fun nextCookie(): Int = cookies.getAndIncrement()
}
