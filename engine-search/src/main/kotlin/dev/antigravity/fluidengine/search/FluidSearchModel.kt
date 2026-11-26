package dev.antigravity.fluidengine.search

// F8.22 · Ricerca (`indice-ricerca`): i tipi dell'indice sul telefono, come Core Spotlight (`CSSearchableItem` con i suoi
// attributi, `CSSearchQuery` e le corrispondenze evidenziate). Qui solo i dati, immutabili: si passano fra fili e si
// mettono interi in uno stato di Compose (regola F7.21: uno stato si scrive dal filo principale o e' un valore intero).

/**
 * Un campo dei documenti di un indice: il titolo, il corpo, il mittente, il nome del file.
 *
 * @property name il nome del campo, lo stesso delle chiavi di [FluidSearchDocument.fields].
 * @property weight quanto conta una parola trovata qui rispetto al corpo (1): il titolo di serie vale 3, come il
 *   «titolo» di Core Spotlight che pesa piu' del contenuto (fiducia media: Apple non pubblica i pesi; 3 a occhio,
 *   25/11/2026, dalle prove col corpus italiano: con 2 il titolo «Riunione di lunedi'» restava sotto una nota che diceva
 *   «riunione» tre volte).
 * @property stored il testo resta nell'indice per mostrarlo (titolo e pezzi evidenziati). Falso: si cerca ma non si
 *   mostra (le parole chiave, il testo di un PDF lungo).
 * @property sensitive un dato da non lasciare leggibile sul disco (una password, un referto): il testo si chiude con la
 *   cassaforte ([FluidSearchCipher]) e le parole entrano nell'indice come impronte con chiave, cosi' il file dell'indice non
 *   le dice. Si trova per parola intera e per inizio di parola (da 3 lettere), non con gli errori di battitura; non va
 *   mai alle altre app (ricerca in tutte le app) ne' nel modello del significato. Vuole un indice con [FluidSearchCipher].
 * @property semantic il testo entra nella ricerca per significato (`FluidTextEmbedder`), se l'indice la usa.
 */
class FluidSearchField(
  val name: String,
  val weight: Float = 1f,
  val stored: Boolean = true,
  val sensitive: Boolean = false,
  val semantic: Boolean = !sensitive,
) {
  init {
    require(name.isNotBlank()) { "FluidSearchField: il nome non puo' essere vuoto" }
    require(weight > 0f && weight.isFinite()) { "FluidSearchField($name): il peso deve essere positivo" }
    require(!(sensitive && semantic)) { "FluidSearchField($name): un campo riservato non va nel modello del significato" }
  }

  override fun toString(): String = "FluidSearchField($name, weight=$weight${if (sensitive) ", sensitive" else ""})"
}

/**
 * Lo schema di un indice: i campi, quale e' il titolo e da quali campi nascono i pezzi evidenziati.
 *
 * **La versione** e' dell'app: quando cambiano i campi la si alza, e l'indice vecchio si butta e si rifa' da capo
 * (l'indice e' una copia dei dati dell'app, mai l'originale: rifarlo costa tempo, non dati). [FluidSearchIndex.rebuilt]
 * dice all'app che deve rimandare tutto.
 *
 * @property name il nome dell'indice (anche il nome del file): «note», «posta».
 * @property title il campo del titolo; di serie il primo.
 * @property snippets i campi da cui si prende il pezzo di testo sotto il titolo, nell'ordine in cui si preferiscono; di
 *   serie tutti i campi conservati tranne il titolo.
 */
class FluidSearchSchema(
  val name: String,
  val version: Int,
  val fields: List<FluidSearchField>,
  val title: String? = fields.firstOrNull()?.name,
  val snippets: List<String> = fields.filter { it.stored && it.name != title }.map { it.name },
) {
  init {
    require(name.matches(Regex("[A-Za-z0-9_.-]+"))) { "FluidSearchSchema: il nome '$name' puo' avere solo lettere, cifre, '.', '_' e '-'" }
    require(version >= 1) { "FluidSearchSchema($name): la versione parte da 1" }
    require(fields.isNotEmpty()) { "FluidSearchSchema($name): serve almeno un campo" }
    require(fields.map { it.name }.toSet().size == fields.size) { "FluidSearchSchema($name): due campi con lo stesso nome" }
    require(fields.size <= MaxFields) { "FluidSearchSchema($name): al massimo $MaxFields campi" }
    require(title == null || fields.any { it.name == title }) { "FluidSearchSchema($name): il titolo '$title' non e' un campo" }
    require(snippets.all { s -> fields.any { it.name == s } }) { "FluidSearchSchema($name): un campo dei pezzi non esiste" }
  }

  /** Il campo [name], o null. */
  fun field(name: String): FluidSearchField? = fields.firstOrNull { it.name == name }

  /** Vero se un campo e' riservato: l'indice allora vuole la cassaforte. */
  val hasSensitive: Boolean get() = fields.any { it.sensitive }

  /** L'impronta dello schema: cambia se cambiano i campi o i loro pesi (con la versione, decide se rifare l'indice). */
  internal val fingerprint: String
    get() = buildString {
      append(version)
      fields.forEach { append('|').append(it.name).append(':').append(it.weight).append(':').append(it.stored).append(':').append(it.sensitive).append(':').append(it.semantic) }
    }

  companion object {
    /** Quante colonne puo' avere l'indice (FTS4 ne regge molte di piu'; un indice dell'app ne ha da 2 a 6). */
    const val MaxFields: Int = 16

    /** Lo schema piu' comune: titolo (peso 3) e testo. */
    fun titleAndBody(name: String, version: Int = 1): FluidSearchSchema = FluidSearchSchema(
      name = name,
      version = version,
      fields = listOf(FluidSearchField("title", weight = 3f), FluidSearchField("body")),
    )
  }
}

/**
 * Un documento dell'indice: una nota, un messaggio, una comunicazione, una foto con la sua descrizione. Sostituisce
 * quello con lo stesso [id] (come `CSSearchableItem.uniqueIdentifier`).
 *
 * @property kind il tipo, per i gruppi dei risultati e per i gettoni: «nota», «trascrizione», «allegato». Libero, lo
 *   decide l'app.
 * @property fields il testo di ogni campo dello schema; i campi assenti sono vuoti.
 * @property timestamp quando e' nato o cambiato, in millisecondi: a parita' di pertinenza viene prima il piu' recente.
 * @property boost quanto spingere questo documento (1 di serie): una nota fissata in alto, un contatto preferito.
 * @property link l'indirizzo che lo apre nell'app (un deep link di F8.12): lo usa la ricerca in tutte le app.
 */
data class FluidSearchDocument(
  val id: String,
  val fields: Map<String, String>,
  val kind: String = "",
  val timestamp: Long? = null,
  val boost: Float = 1f,
  val link: String? = null,
) {
  init {
    require(id.isNotEmpty()) { "FluidSearchDocument: l'id non puo' essere vuoto" }
    require(boost > 0f && boost.isFinite()) { "FluidSearchDocument($id): boost deve essere positivo" }
  }

  /** Il testo del campo [name], vuoto se manca. */
  operator fun get(name: String): String = fields[name].orEmpty()
}

/**
 * Una domanda all'indice.
 *
 * @property text quello che la persona ha scritto, cosi' com'e': l'ultima parola senza spazio dopo vale anche come
 *   inizio di parola (si sta scrivendo).
 * @property kinds solo questi tipi (i gettoni «Note», «Allegati»); vuoto: tutti.
 * @property limit quanti risultati al piu'.
 * @property typos tollera gli errori di battitura (una lettera fino a 6, due da 7): «riunone» trova «riunione».
 * @property meaning il vettore della domanda per la ricerca per significato ([FluidTextEmbedder]); null: solo parole.
 * @property includeSensitive cerca anche nei campi riservati e li mostra (di serie si', dentro l'app). Falso per chi
 *   risponde a un'altra app (la ricerca in tutte le app): un campo riservato allora non trova niente e non si vede.
 */
data class FluidSearchQuery(
  val text: String,
  val kinds: Set<String> = emptySet(),
  val limit: Int = 50,
  val typos: Boolean = true,
  val meaning: FloatArray? = null,
  val includeSensitive: Boolean = true,
) {
  init { require(limit in 1..MaxLimit) { "FluidSearchQuery: limit fuori da 1..$MaxLimit" } }

  override fun equals(other: Any?): Boolean = other is FluidSearchQuery && other.text == text && other.kinds == kinds &&
    other.limit == limit && other.typos == typos && other.includeSensitive == includeSensitive &&
    (other.meaning?.contentEquals(meaning) ?: (meaning == null))

  override fun hashCode(): Int = ((text.hashCode() * 31 + kinds.hashCode()) * 31 + limit) * 31 + (meaning?.contentHashCode() ?: 0)

  companion object {
    /** Il tetto di [limit]. */
    const val MaxLimit: Int = 500
  }
}

/**
 * Un pezzo di testo con le parole trovate: [highlights] sono gli intervalli (inclusivi) dentro [text] da mettere in
 * evidenza. Un pezzo tagliato comincia o finisce con «…».
 */
data class FluidSearchSnippet(
  val text: String,
  val highlights: List<IntRange> = emptyList(),
) {
  /** Le parole evidenziate, nell'ordine. */
  val highlighted: List<String> get() = highlights.map { text.substring(it.first, it.last + 1) }
}

/**
 * Un risultato.
 *
 * @property title il titolo, intero, con le parole trovate.
 * @property snippet il pezzo di testo sotto il titolo, con le parole trovate; null se non c'e' testo da mostrare.
 * @property fields i campi conservati del documento (quelli riservati compresi: e' l'app che li mostra), per la riga.
 * @property byWords trovato per parole; [byMeaning] per significato. Possono essere veri tutti e due.
 * @property similarity la somiglianza col significato della domanda (coseno), se e' stata cercata.
 */
data class FluidSearchHit(
  val id: String,
  val kind: String,
  val score: Float,
  val title: FluidSearchSnippet?,
  val snippet: FluidSearchSnippet?,
  val fields: Map<String, String>,
  val timestamp: Long?,
  val link: String?,
  val byWords: Boolean = true,
  val byMeaning: Boolean = false,
  val similarity: Float? = null,
)

/**
 * La risposta a una domanda.
 *
 * @property hits i risultati, dal piu' pertinente.
 * @property total quanti documenti corrispondono (anche oltre [FluidSearchQuery.limit]).
 * @property corrected la domanda corretta quando una parola non c'era e l'indice ne ha trovata una vicina: «riunione» per
 *   «riunone». La riga sopra i risultati dice «Risultati per riunione · cercato anche «riunone»».
 * @property tookMillis quanto e' durata, in millisecondi.
 * @property meaning vero se la domanda e' passata anche dal significato.
 */
data class FluidSearchResults(
  val query: String,
  val hits: List<FluidSearchHit>,
  val total: Int,
  val corrected: String? = null,
  val tookMillis: Long = 0,
  val meaning: Boolean = false,
) {
  /** I risultati divisi per tipo, nell'ordine in cui compare il primo di ogni tipo. */
  val byKind: List<Pair<String, List<FluidSearchHit>>>
    get() = hits.groupBy { it.kind }.toList()

  companion object {
    /** Nessuna domanda: niente risultati. */
    val Empty: FluidSearchResults = FluidSearchResults(query = "", hits = emptyList(), total = 0)
  }
}

/**
 * Un pezzo di modifiche da una sorgente ([FluidSearchSource]): i documenti nuovi o cambiati, quelli tolti, e il segno
 * da cui ripartire. L'indice li applica tutti insieme, col segno, in una transazione sola: se il telefono si spegne a
 * meta', si riparte dal pezzo prima.
 */
data class FluidSearchChanges(
  val upserts: List<FluidSearchDocument> = emptyList(),
  val deletes: List<String> = emptyList(),
  val cursor: String? = null,
  val hasMore: Boolean = false,
)
