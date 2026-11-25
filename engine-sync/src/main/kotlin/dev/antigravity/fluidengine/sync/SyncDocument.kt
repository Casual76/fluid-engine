package dev.antigravity.fluidengine.sync

/**
 * Il valore di un campo. Ogni tipo si unisce per conto suo:
 * - [Text], [Integer], [Real], [Bool] e [Bytes] sono valori **da ultimo che vince**: fra due modifiche fatte senza vedersi
 *   resta quella con lo stamp piu' grande ([SyncHlc]);
 * - [LongText] e' un testo **che si fonde**: due modifiche a passaggi diversi restano tutte e due, e due aggiunte nello
 *   stesso punto anche; quando i due dispositivi hanno cambiato lo stesso passaggio in modo diverso, il campo tiene le due
 *   versioni e chiede la scelta ([SyncConflict]).
 *
 * Il tipo e' una promessa sul modo di fondere, non sul contenuto: un titolo corto e' [Text], il corpo di una nota e'
 * [LongText].
 */
sealed class SyncValue {
  /** Un testo corto (un titolo, un nome): vince l'ultimo. */
  data class Text(val text: String) : SyncValue()

  /** Un testo lungo (il corpo di una nota): si fonde, e quando non si puo' chiede la scelta. */
  data class LongText(val text: String) : SyncValue()

  /** Un numero intero: vince l'ultimo. */
  data class Integer(val value: Long) : SyncValue()

  /** Un numero con la virgola: vince l'ultimo. */
  data class Real(val value: Double) : SyncValue()

  /** Un si'/no (una voce spuntata): vince l'ultimo. */
  data class Bool(val value: Boolean) : SyncValue()

  /** Byte qualunque (un'immagine piccola, un valore gia' codificato dall'app): vince l'ultimo. */
  class Bytes(bytes: ByteArray) : SyncValue() {
    private val data = bytes.copyOf()

    /** Una copia dei byte. */
    val bytes: ByteArray get() = data.copyOf()

    internal val raw: ByteArray get() = data

    override fun equals(other: Any?): Boolean = other is Bytes && other.data.contentEquals(data)

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String = "Bytes(${data.size})"
  }
}

/**
 * Una versione di un campo rimasta indietro in un conflitto: il suo stamp (che e' l'identificativo della revisione) e il
 * valore. Resta nel documento, e viaggia cifrata con lui, finche' qualcuno sceglie ([SyncSpace.resolve]).
 */
data class SyncAlternative(val stamp: SyncHlc, val value: SyncValue?) {
  /** L'identificativo della revisione: lo stamp in forma di testo. */
  val revision: String get() = stamp.encode()
}

/**
 * Un campo: il valore (nullo se cancellato), lo stamp della scrittura che l'ha messo e le versioni in conflitto ancora da
 * scegliere ([alternatives], di solito nessuna).
 */
data class SyncField(
  val value: SyncValue?,
  val stamp: SyncHlc,
  val alternatives: List<SyncAlternative> = emptyList(),
) {
  /** L'identificativo della revisione del valore attuale. */
  val revision: String get() = stamp.encode()

  /** Vero se il campo aspetta una scelta fra versioni. */
  val inConflict: Boolean get() = alternatives.isNotEmpty()
}

/**
 * Un documento: i campi di un elemento (una nota, una voce di lista, un ricordo) e, se e' stato cancellato, lo stamp della
 * cancellazione ([deleted]).
 *
 * **La cancellazione e' definitiva.** Un documento cancellato perde i campi e resta come pietra tombale finche' tutti i
 * dispositivi l'hanno vista; una modifica arrivata insieme non lo fa tornare (vince la cancellazione, come nelle liste
 * dell'Account Pampa: «se una voce viene spuntata e tolta insieme, vince la tolta»). Un cestino da cui si recupera e' un
 * campo dell'app («nel cestino: si'»), non una cancellazione.
 */
class SyncDocument(
  fields: Map<String, SyncField> = emptyMap(),
  val deleted: SyncHlc? = null,
) {
  /** I campi, in ordine di nome: due documenti uguali hanno lo stesso ordine e la stessa codifica. */
  val fields: Map<String, SyncField> = if (deleted != null) emptyMap() else fields.toSortedMap()

  /** Vero se il documento e' una pietra tombale. */
  val isDeleted: Boolean get() = deleted != null

  /** Il valore di [name], o null. */
  operator fun get(name: String): SyncValue? = fields[name]?.value

  /** Il testo di [name] se e' [SyncValue.Text] o [SyncValue.LongText], altrimenti null. */
  fun text(name: String): String? = when (val v = get(name)) {
    is SyncValue.Text -> v.text
    is SyncValue.LongText -> v.text
    else -> null
  }

  /** Il numero intero di [name], o null. */
  fun integer(name: String): Long? = (get(name) as? SyncValue.Integer)?.value

  /** Il numero con la virgola di [name], o null. */
  fun real(name: String): Double? = (get(name) as? SyncValue.Real)?.value

  /** Il si'/no di [name], o null. */
  fun bool(name: String): Boolean? = (get(name) as? SyncValue.Bool)?.value

  /** I nomi dei campi che aspettano una scelta. */
  val conflictedFields: List<String> get() = fields.filterValues { it.inConflict }.keys.toList()

  /** Lo stamp piu' grande del documento: l'ultima scrittura che contiene. */
  val latestStamp: SyncHlc
    get() {
      var max = deleted ?: SyncHlc.Zero
      for (field in fields.values) {
        if (field.stamp > max) max = field.stamp
        for (alt in field.alternatives) if (alt.stamp > max) max = alt.stamp
      }
      return max
    }

  override fun equals(other: Any?): Boolean =
    other is SyncDocument && other.deleted == deleted && other.fields == fields

  override fun hashCode(): Int = fields.hashCode() * 31 + (deleted?.hashCode() ?: 0)

  override fun toString(): String = if (deleted != null) "SyncDocument(cancellato $deleted)" else "SyncDocument($fields)"

  /** Una copia con [name] cambiato. */
  internal fun with(name: String, field: SyncField): SyncDocument =
    if (deleted != null) this else SyncDocument(fields + (name to field), null)

  companion object {
    /** Il documento vuoto: un elemento appena nato. */
    val Empty: SyncDocument = SyncDocument()

    /** La pietra tombale con lo stamp della cancellazione. */
    fun tombstone(stamp: SyncHlc): SyncDocument = SyncDocument(emptyMap(), stamp)
  }
}
