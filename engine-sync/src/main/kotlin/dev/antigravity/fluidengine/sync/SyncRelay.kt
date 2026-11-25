package dev.antigravity.fluidengine.sync

/**
 * Lo spazio di sincronizzazione: i dati di un'app di una persona, oppure uno spazio condiviso fra piu' persone (una lista,
 * un gruppo). Ogni spazio ha il suo registro, il suo cursore e le sue righe sul relay. L'identificativo e' scelto da chi
 * chiama (l'account usa il pacchetto dell'app e la collezione, o l'id dello spazio condiviso) e non deve contenere nomi o
 * titoli: sul relay sta in chiaro.
 */
@JvmInline
value class SyncSpaceId(val value: String) {
  init {
    require(value.isNotBlank() && value.length <= 200) { "spazio non valido: «$value»" }
  }

  override fun toString(): String = value
}

/**
 * Il contenuto di un elemento come viaggia: byte che il relay non sa leggere, prodotti dal [SyncSealer] di chi chiama.
 * [keyVersion] e' l'epoca della chiave che li ha sigillati (l'Account Pampa ruota la chiave dei dati a epoche); il relay
 * la conserva e la restituisce, senza capirla.
 */
class SyncSealedPayload(bytes: ByteArray, val keyVersion: Int = 0) {
  private val data = bytes.copyOf()

  /** Una copia dei byte sigillati. */
  val bytes: ByteArray get() = data.copyOf()

  internal val raw: ByteArray get() = data

  /** Quanti byte. */
  val size: Int get() = data.size

  override fun equals(other: Any?): Boolean =
    other is SyncSealedPayload && other.keyVersion == keyVersion && other.data.contentEquals(data)

  override fun hashCode(): Int = data.contentHashCode() * 31 + keyVersion

  override fun toString(): String = "SyncSealedPayload(${data.size} byte, chiave $keyVersion)"
}

/** Una riga del relay: l'elemento, la sua versione sul relay, se e' cancellato, il contenuto sigillato e il numero d'ordine. */
class SyncRemoteRow(
  val recordId: String,
  /** La versione sul relay: 1 alla nascita, +1 a ogni scrittura accettata. */
  val version: Long,
  val deleted: Boolean,
  /** Il contenuto sigillato; puo' mancare solo in una pietra tombale gia' alleggerita dal relay. */
  val payload: SyncSealedPayload?,
  /** Il numero d'ordine dell'ultima scrittura nello spazio: il cursore di [SyncRelay.pull]. */
  val seq: Long,
)

/** Una modifica da mandare: l'elemento, la versione su cui si e' lavorato ([baseVersion], 0 per un elemento nuovo), il contenuto. */
class SyncPushChange(
  val recordId: String,
  val baseVersion: Long,
  val deleted: Boolean,
  val payload: SyncSealedPayload?,
)

/**
 * Un invio: le modifiche e la **chiave d'idempotenza**. Il relay ricorda la risposta per quella chiave (l'Account Pampa per
 * 24 ore, `api.md` § 1): rimandare lo stesso invio dopo un errore di rete da' la stessa risposta e non scrive due volte.
 * L'engine salva l'invio nel registro **prima** di mandarlo e lo rimanda identico finche' non ha la risposta.
 */
class SyncPushBatch(val idempotencyKey: String, val changes: List<SyncPushChange>)

/** L'ora del relay in una risposta: corregge l'orologio del telefono ([SyncClock.offsetMillis]). 0 se il relay non la dice. */
interface SyncRelayTime {
  val serverTimeMillis: Long
}

/** Una modifica accettata: la sua versione nuova sul relay e il suo numero d'ordine. */
class SyncAccepted(val recordId: String, val version: Long, val seq: Long)

/**
 * La risposta a un invio. Ogni modifica e' accettata (se il relay aveva ancora la sua `baseVersion`) oppure torna in
 * [conflicts] con la riga attuale: l'engine la fonde con la propria e rimanda.
 */
class SyncPushResult(
  val accepted: List<SyncAccepted>,
  val conflicts: List<SyncRemoteRow>,
  override val serverTimeMillis: Long = 0,
) : SyncRelayTime

/** La risposta a una lettura: le righe cambiate dopo il cursore, in ordine, e il cursore dopo l'ultima. */
sealed class SyncPullResult : SyncRelayTime {
  /** Una pagina di righe. [hasMore] se ce ne sono altre dopo [next]. */
  class Rows(
    val rows: List<SyncRemoteRow>,
    val next: Long,
    val hasMore: Boolean,
    override val serverTimeMillis: Long = 0,
  ) : SyncPullResult()

  /**
   * Il cursore e' troppo vecchio: il relay ha gia' cancellato pietre tombali piu' recenti (l'Account Pampa le tiene 90
   * giorni). Il dispositivo e' stato fermo troppo: rilegge tutto da capo, senza perdere le sue modifiche in attesa.
   */
  class CursorExpired(override val serverTimeMillis: Long = 0) : SyncPullResult()
}

/** Perche' il relay non ha risposto: decide se riprovare e cosa dire nello stato. */
sealed class SyncFailure {
  /** Niente rete, o il relay irraggiungibile: si riprova quando torna la rete. */
  data object Offline : SyncFailure()

  /** Il relay ha detto di aspettare (troppe richieste, quota del giorno, manutenzione). */
  data class RetryLater(val afterMillis: Long) : SyncFailure()

  /** Il relay ha rifiutato (accesso scaduto, app scollegata, spazio pieno): riprovare non basta. [code] e' il suo codice. */
  data class Rejected(val code: String, val message: String? = null) : SyncFailure()
}

/** Il relay non ha risposto come doveva. */
class SyncRelayException(val failure: SyncFailure, cause: Throwable? = null) :
  Exception(failure.toString(), cause)

/**
 * L'adattatore verso una destinazione: il **relay cifrato dell'Account Pampa** (F10.3, rotte `/v1/sync/…` di `api.md`
 * § 4.10) e, un giorno, una cartella dell'utente (Drive, WebDAV, un NAS), che oggi non servono.
 *
 * Il contratto e' quello del relay: righe per spazio con una versione che cresce di uno, scritture accettate solo sulla
 * versione attesa (`baseVersion`), chiave d'idempotenza, numero d'ordine per leggere le novita', pietre tombali tenute per
 * un tempo e poi cancellate. **Il relay vede solo byte sigillati** ([SyncSealedPayload]): non legge, non cerca e non fonde.
 *
 * Gli errori di rete e i rifiuti escono come [SyncRelayException]. Le implementazioni di serie sono [MemorySyncRelay] (in
 * memoria, fedele al contratto) e `testing.FakeSyncRelay` (la destinazione finta delle prove, che sa perdere la rete a meta').
 */
interface SyncRelay {
  /** Le righe di [space] cambiate dopo [since] (0 = tutte), al massimo [limit]. */
  suspend fun pull(space: SyncSpaceId, since: Long, limit: Int): SyncPullResult

  /** Manda le modifiche di [batch] a [space]. Lo stesso [SyncPushBatch.idempotencyKey] da' la stessa risposta. */
  suspend fun push(space: SyncSpaceId, batch: SyncPushBatch): SyncPushResult
}

/**
 * Dove e cosa si sigilla: lo spazio, l'elemento e la versione che avra' sul relay. Il [SyncSealer] lo lega ai byte come
 * dati associati ([associatedData]): cosi' il relay non puo' spostare un contenuto su un altro elemento o in un altro
 * spazio, ne' servirlo come una versione diversa, senza che l'apertura fallisca (`b/chiavi.md`, K4: «ogni dato cifrato
 * porta con se' di chi e' e dove sta»).
 */
class SyncSealContext(val space: SyncSpaceId, val recordId: String, val version: Long) {
  /** I dati associati: `fluid-sync/1`, lo spazio, l'elemento e la versione, separati da uno zero. */
  fun associatedData(): ByteArray =
    "fluid-sync/1\u0000${space.value}\u0000$recordId\u0000$version".toByteArray(Charsets.UTF_8)

  override fun equals(other: Any?): Boolean =
    other is SyncSealContext && other.space == space && other.recordId == recordId && other.version == version

  override fun hashCode(): Int = (space.hashCode() * 31 + recordId.hashCode()) * 31 + version.hashCode()

  override fun toString(): String = "SyncSealContext($space/$recordId@$version)"
}

/** Un contenuto che non si apre: chiave sbagliata, byte toccati, oppure spostato su un altro elemento o un'altra versione. */
class SyncSealException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * **Il punto d'aggancio della cifratura.** L'engine non ha chiavi e non fa crittografia: prima di mandare un documento lo
 * passa a [seal], e cio' che arriva dal relay lo apre con [open]. Il sigillo vero lo mette l'Account Pampa (F10.4: la
 * chiave dell'app `K_app(p)` per i dati di un'app, la chiave dello spazio condiviso per le liste e i gruppi), con i dati
 * associati di [SyncSealContext].
 *
 * Cosa garantisce l'engine:
 * - **verso il relay passano solo i byte di [seal]**: l'adattatore ([SyncRelay]) accetta solo [SyncSealedPayload], e la
 *   coda salvata per rimandare un invio contiene solo byte sigillati;
 * - un contenuto che [open] rifiuta ([SyncSealException]) non si applica, non sostituisce niente e finisce nello stato
 *   come errore.
 *
 * Il registro sul telefono e' invece la verita' locale dell'app, come il suo database: sta nella cartella privata
 * dell'app, fuori dal backup. Chi lo vuole cifrato anche li' passa un [SyncStore] che cifra (`engine-vault`, F8.23).
 */
interface SyncSealer {
  /** Sigilla [plaintext] (la codifica di un documento) legandolo a [context]. */
  suspend fun seal(context: SyncSealContext, plaintext: ByteArray): SyncSealedPayload

  /** Apre [payload] se e' stato sigillato per [context]; altrimenti [SyncSealException]. */
  suspend fun open(context: SyncSealContext, payload: SyncSealedPayload): ByteArray
}
