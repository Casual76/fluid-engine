package dev.antigravity.fluidengine.viewer.data

/** Il tipo di un nodo dell'albero: dà il punto colorato e la forma della riga. */
internal enum class DataKind {
  Object, Array, String, Number, Bool, Null, Date, Data, Element, Attribute, Text, Comment, Instruction;

  val container: Boolean get() = this == Object || this == Array || this == Element
}

/**
 * Un nodo di un albero di dati: un valore JSON, un elemento XML, un valore di un plist. [name] è la chiave (o l'indice
 * in un elenco, o il nome dell'elemento); [value] il valore già scritto per le persone (una stringa senza virgolette, un
 * numero com'è nel file, «true»); [line] la riga del file dove comincia, da 1, per «Testo». [id] è unico nell'albero
 * (l'ordine di lettura): lo usa la pagina per ricordare cosa è aperto.
 */
internal class DataNode(
  val id: Int,
  val kind: DataKind,
  val name: String?,
  var value: String?,
  val line: Int,
) {
  /** I figli di un contenitore; `null` per un valore semplice. */
  val children: ArrayList<DataNode>? = if (kind.container) ArrayList() else null

  /** Falso se la lettura si è fermata dentro questo contenitore (tetto o errore): la pagina mostra «…». */
  var complete: Boolean = true

  val childCount: Int get() = children?.size ?: 0
}

/** Dove un file non è valido: riga e colonna da 1, e il motivo in italiano. */
internal class DataTreeError(val line: Int, val column: Int, val message: String) {
  override fun toString(): String = "riga $line, colonna $column: $message"
}

/** I conti di un albero per il pannello info. */
internal class DataTreeStats {
  var nodes = 0
  var maxDepth = 0
  val byKind = IntArray(DataKind.entries.size)
  var comments = 0
  var trailingCommas = 0

  fun count(kind: DataKind) = byKind[kind.ordinal]
}

/**
 * Un albero letto: la [root] (anche a metà, se il file non è valido o è oltre il tetto), l'[error] con riga e colonna,
 * [truncated] se il tetto dei nodi ha fermato la lettura, e il [format] per le persone («JSON», «JSONC», «NDJSON»,
 * «XML», «Property list XML», «Property list binaria»).
 */
internal class DataTree(
  val root: DataNode?,
  val error: DataTreeError?,
  val truncated: Boolean,
  val stats: DataTreeStats,
  val format: String,
  /** Per XML: la dichiarazione (versione, encoding) e il DOCTYPE, se ci sono. */
  val prolog: Map<String, String> = emptyMap(),
)

/** Costruisce i nodi con gli id in ordine e tiene i conti; si ferma al tetto. */
internal class DataTreeBuilder(private val maxNodes: Int) {
  val stats = DataTreeStats()
  private var nextId = 0
  var full = false
    private set

  /** Un nodo nuovo, o `null` se il tetto è raggiunto (allora [full] diventa vero). */
  fun node(kind: DataKind, name: String?, value: String?, line: Int, depth: Int): DataNode? {
    if (nextId >= maxNodes) {
      full = true
      return null
    }
    stats.nodes++
    stats.byKind[kind.ordinal]++
    if (depth > stats.maxDepth) stats.maxDepth = depth
    return DataNode(nextId++, kind, name, value, line)
  }
}

/** Il testo breve di un contenitore chiuso, come nella tavola D: «7 chiavi», «6 elementi», «3 attributi · 2 figli». */
internal fun DataNode.summary(): String {
  val n = childCount
  return when (kind) {
    DataKind.Object -> if (n == 1) "1 chiave" else "${DataText.count(n)} chiavi"
    DataKind.Array -> if (n == 1) "1 elemento" else "${DataText.count(n)} elementi"
    DataKind.Element -> {
      val attributes = children?.count { it.kind == DataKind.Attribute } ?: 0
      val others = n - attributes
      buildList {
        if (attributes > 0) add(if (attributes == 1) "1 attributo" else "$attributes attributi")
        if (others > 0) add(if (others == 1) "1 figlio" else "${DataText.count(others)} figli")
      }.joinToString(" · ").ifEmpty { "vuoto" }
    }
    else -> ""
  }
}

/** Un'anteprima del contenitore: il primo valore breve dentro («office» nella tavola), se c'è. */
internal fun DataNode.preview(): String? {
  val first = children?.firstOrNull { (it.kind == DataKind.String || it.kind == DataKind.Text) && !it.value.isNullOrBlank() }
    ?: return null
  return first.value?.take(24)?.let { if ((first.value?.length ?: 0) > 24) "$it…" else it }
}
