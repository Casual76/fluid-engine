package dev.antigravity.fluidengine.richtext

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange

/**
 * Gli stili di carattere che l'editor sa mettere e togliere da una selezione.
 *
 * Tre, di proposito: sono quelli che ogni client di posta, ogni app di note e ogni campo di messaggio
 * capiscono allo stesso modo, e che un HTML ristretto o un Markdown sanno scrivere senza perdite. Il
 * link non e' qui perche' non e' un interruttore: porta un indirizzo (vedi [FluidRichTextInlineFormat.link]).
 *
 * **Puo' crescere** (barrato, codice) in una versione futura: non scriverci un `when` esaustivo senza
 * un ramo `else`.
 */
enum class FluidRichTextInlineStyle { Bold, Italic, Underline }

/**
 * Il tipo di elenco di un paragrafo. Gli elenchi si annidano (vedi [FluidRichTextParagraphStyle]): un
 * sotto-elenco ha il suo tipo, indipendente da quello sopra. **Puo' crescere**: niente `when` esaustivi
 * senza `else`.
 */
enum class FluidRichTextListKind {
  Bullet,
  Numbered,
  ;

  /** Il contenitore che fa di un paragrafo una voce di questo elenco. */
  val block: FluidRichTextBlock
    get() = if (this == Bullet) FluidRichTextBlock.Bullet else FluidRichTextBlock.Numbered
}

/**
 * Un contenitore in cui sta un paragrafo: una citazione, o un elenco (di cui il paragrafo e' una voce, o
 * dentro la cui voce sta). Sono i tre blocchi che l'HTML di una mail annida (`blockquote`, `ul`, `ol`) e
 * che il Markdown sa scrivere con i rientri. **Puo' crescere**: niente `when` esaustivi senza `else`.
 */
enum class FluidRichTextBlock {
  /** Una citazione: la barra a lato e il testo attenuato; `<blockquote>`, `> `. */
  Quote,

  /** Un elenco puntato: `<ul>`, `- `. */
  Bullet,

  /** Un elenco numerato: `<ol>`, `1. `. */
  Numbered,
  ;

  /** Il tipo di elenco, o null per la citazione. */
  val listKind: FluidRichTextListKind?
    get() = when (this) {
      Bullet -> FluidRichTextListKind.Bullet
      Numbered -> FluidRichTextListKind.Numbered
      Quote -> null
    }
}

/**
 * Come appare un tratto di testo: i tre interruttori e, se e' un link, il suo indirizzo.
 *
 * Il link sta qui e non in un elenco a parte perche' si comporta come uno stile: si allarga se si
 * scrive **dentro** il link, si cancella con il testo, si copia e si incolla con lui. Diversamente dagli
 * altri tre, non si allarga a chi scrive **in fondo** al link (chi finisce di scrivere un indirizzo e
 * va avanti non vuole che la frase dopo diventi parte del link): e' la regola di ogni editor.
 *
 * @param link un indirizzo gia' ritenuto sicuro (vedi [FluidRichTextLinks.sanitize]); null: non e' un link.
 */
@Immutable
data class FluidRichTextInlineFormat(
  val bold: Boolean = false,
  val italic: Boolean = false,
  val underline: Boolean = false,
  val link: String? = null,
) {
  /** Vero se [style] e' acceso. */
  fun has(style: FluidRichTextInlineStyle): Boolean = when (style) {
    FluidRichTextInlineStyle.Bold -> bold
    FluidRichTextInlineStyle.Italic -> italic
    FluidRichTextInlineStyle.Underline -> underline
  }

  /** Lo stesso formato con [style] acceso o spento. */
  fun with(style: FluidRichTextInlineStyle, on: Boolean): FluidRichTextInlineFormat = when (style) {
    FluidRichTextInlineStyle.Bold -> copy(bold = on)
    FluidRichTextInlineStyle.Italic -> copy(italic = on)
    FluidRichTextInlineStyle.Underline -> copy(underline = on)
  }

  /** Nessuno stile e nessun link. */
  val isPlain: Boolean get() = !bold && !italic && !underline && link == null

  companion object {
    /** Testo semplice. */
    val Plain: FluidRichTextInlineFormat = FluidRichTextInlineFormat()
  }
}

/**
 * Lo stile di un paragrafo: i contenitori in cui sta, dal piu' esterno al piu' interno ([blocks]).
 *
 * **Una pila, non un numero.** Elenchi e citazioni si annidano e si combinano come in un HTML di posta:
 * `[Bullet, Bullet]` e' una voce di un sotto-elenco puntato, `[Quote, Numbered]` una voce numerata dentro
 * una citazione, `[Bullet, Quote]` una citazione dentro una voce, `[Quote, Quote]` una citazione dentro
 * una citazione. Il **livello di rientro** e' [depth], da 0 (paragrafo normale) a [MaxDepth].
 *
 * **Voce o contenuto.** Un paragrafo il cui contenitore piu' interno e' un elenco e' una **voce** di
 * quell'elenco ([list]); uno il cui piu' interno e' una citazione e' un paragrafo citato. Due paragrafi
 * vicini con la stessa pila sono voci dello stesso elenco (o paragrafi della stessa citazione); un
 * paragrafo con una pila piu' lunga che comincia come quella di sopra sta **dentro** la voce di sopra
 * (un sotto-elenco, una citazione nella voce): per questo la numerazione di un elenco prosegue dopo un
 * sotto-elenco e riparte in ogni sotto-elenco ([FluidRichTextDocument.listNumber]).
 *
 * **Il tetto.** Oltre [MaxDepth] contenitori (un HTML incollato con cento citazioni una dentro l'altra) si
 * tengono i primi `MaxDepth - 1` e il piu' interno: il paragrafo resta una voce se lo era, e il testo
 * resta tutto. Il rientro che si vede ha a sua volta un limite (vedi
 * [FluidRichTextEditorDefaults.MaxIndentFraction]): oltre quello i rientri del documento si stringono.
 *
 * Si costruisce con la pila ([of], o il costruttore con la lista), o con il costruttore a un livello
 * `(list, quote)`, che fa una voce dentro una citazione come prima che gli elenchi si annidassero.
 */
@Immutable
class FluidRichTextParagraphStyle(blocks: List<FluidRichTextBlock>) {
  /** I contenitori, dal piu' esterno al piu' interno; vuota per un paragrafo normale. Mai oltre [MaxDepth]. */
  val blocks: List<FluidRichTextBlock> =
    if (blocks.size <= MaxDepth) blocks.toList() else blocks.subList(0, MaxDepth - 1) + blocks.last()

  /**
   * Lo stile a un livello: una voce dell'elenco [list] (se c'e'), dentro una citazione se [quote]. E' la
   * forma di prima dell'annidamento, e resta il modo breve di scrivere i casi comuni.
   */
  constructor(list: FluidRichTextListKind? = null, quote: Boolean = false) :
    this(listOfNotNull(if (quote) FluidRichTextBlock.Quote else null, list?.block))

  /** Il livello di rientro: quanti contenitori. 0 per un paragrafo normale. */
  val depth: Int get() = blocks.size

  /** Il tipo dell'elenco di cui il paragrafo e' una voce (il contenitore piu' interno), o null. */
  val list: FluidRichTextListKind? get() = blocks.lastOrNull()?.listKind

  /** Vero se il paragrafo sta in almeno una citazione (a qualunque livello). */
  val quote: Boolean get() = blocks.contains(FluidRichTextBlock.Quote)

  /**
   * Il livello della voce fra gli elenchi: 1 per una voce di un elenco, 2 per una voce di un sotto-elenco,
   * e cosi' via (le citazioni non contano). 0 se il paragrafo non e' una voce. Decide il segno dei puntati
   * e cio' che TalkBack annuncia («elenco puntato, livello 2»).
   */
  val listLevel: Int get() = if (list == null) 0 else blocks.count { it != FluidRichTextBlock.Quote }

  /** In quante citazioni sta il paragrafo, una dentro l'altra. */
  val quoteLevel: Int get() = blocks.count { it == FluidRichTextBlock.Quote }

  /**
   * Lo stesso stile con l'elenco e la citazione cambiati come li cambiano i tasti della barra: [list]
   * diverso sostituisce l'elenco della voce (o ne fa una voce, dentro i contenitori che ha; null toglie gli
   * elenchi in fondo alla pila); [quote] falso toglie tutte le citazioni, vero mette in una citazione
   * tutto il paragrafo se non lo era. Su uno stile a un livello e' la `copy` di prima.
   */
  fun copy(list: FluidRichTextListKind? = this.list, quote: Boolean = this.quote): FluidRichTextParagraphStyle {
    var result = this
    if (list != this.list) result = if (list == null) result.withoutList() else result.withList(list)
    if (quote != result.quote) result = if (quote) result.wrappedInQuote() else result.withoutQuotes()
    return result
  }

  override fun equals(other: Any?): Boolean = other is FluidRichTextParagraphStyle && blocks == other.blocks

  override fun hashCode(): Int = blocks.hashCode()

  override fun toString(): String = "FluidRichTextParagraphStyle($blocks)"

  companion object {
    /** Quanti contenitori al massimo, uno dentro l'altro: i livelli di rientro vanno da 0 a questo. */
    const val MaxDepth: Int = 6

    /** Un paragrafo normale. */
    val Plain: FluidRichTextParagraphStyle = FluidRichTextParagraphStyle(emptyList<FluidRichTextBlock>())

    /** Lo stile con i contenitori [blocks], dal piu' esterno: `of(Quote, Bullet, Bullet)`. */
    fun of(vararg blocks: FluidRichTextBlock): FluidRichTextParagraphStyle = FluidRichTextParagraphStyle(blocks.asList())
  }
}

// --- operazioni sugli stili di paragrafo (i comandi della barra e della tastiera) ---------------------

/** Una voce dell'elenco [kind]: cambia il tipo della voce, o la fa voce dentro i contenitori che ha. */
internal fun FluidRichTextParagraphStyle.withList(kind: FluidRichTextListKind): FluidRichTextParagraphStyle = when {
  list == kind -> this
  list != null -> FluidRichTextParagraphStyle(blocks.dropLast(1) + kind.block)
  depth >= FluidRichTextParagraphStyle.MaxDepth -> this
  else -> FluidRichTextParagraphStyle(blocks + kind.block)
}

/** Fuori dagli elenchi in fondo alla pila: `[Quote, Bullet, Bullet]` torna `[Quote]`. */
internal fun FluidRichTextParagraphStyle.withoutList(): FluidRichTextParagraphStyle {
  if (list == null) return this
  return FluidRichTextParagraphStyle(blocks.dropLastWhile { it != FluidRichTextBlock.Quote })
}

/** Tutto il paragrafo dentro una citazione (il contenitore piu' esterno). */
internal fun FluidRichTextParagraphStyle.wrappedInQuote(): FluidRichTextParagraphStyle =
  if (depth >= FluidRichTextParagraphStyle.MaxDepth) this else FluidRichTextParagraphStyle(listOf(FluidRichTextBlock.Quote) + blocks)

/** Fuori da tutte le citazioni; gli elenchi restano. */
internal fun FluidRichTextParagraphStyle.withoutQuotes(): FluidRichTextParagraphStyle =
  if (!quote) this else FluidRichTextParagraphStyle(blocks.filter { it != FluidRichTextBlock.Quote })

/** Vero se [indented] cambia qualcosa: c'e' un contenitore da ripetere e spazio sotto il tetto. */
internal val FluidRichTextParagraphStyle.canIndent: Boolean
  get() = depth in 1 until FluidRichTextParagraphStyle.MaxDepth

/**
 * Un livello piu' dentro: il contenitore piu' interno si ripete (una voce diventa la voce di un
 * sotto-elenco dello stesso tipo, un paragrafo citato una citazione nella citazione). Un paragrafo
 * normale non ha un rientro da aumentare, e al tetto non cambia niente.
 */
internal fun FluidRichTextParagraphStyle.indented(): FluidRichTextParagraphStyle =
  if (!canIndent) this else FluidRichTextParagraphStyle(blocks + blocks.last())

/**
 * Un livello piu' fuori: toglie il contenitore piu' interno. Una voce di un sotto-elenco torna voce
 * dell'elenco sopra, una voce di primo livello torna paragrafo (o paragrafo citato), una citazione
 * annidata torna citazione. E' anche cio' che fanno l'a capo su una voce vuota e il «cancella» in testa.
 */
internal fun FluidRichTextParagraphStyle.outdented(): FluidRichTextParagraphStyle =
  if (depth == 0) this else FluidRichTextParagraphStyle(blocks.dropLast(1))

/** Quanti contenitori iniziali hanno in comune [a] e [b]. */
internal fun commonDepth(a: List<FluidRichTextBlock>, b: List<FluidRichTextBlock>): Int {
  val max = minOf(a.size, b.size)
  var index = 0
  while (index < max && a[index] == b[index]) index++
  return index
}

/** Un tratto di testo con un formato non vuoto, `[start, end)` nel testo del documento. */
@Immutable
data class FluidRichTextSpan(val start: Int, val end: Int, val format: FluidRichTextInlineFormat)

/** Un paragrafo: `[start, end)` nel testo, senza l'a capo che lo chiude, e il suo stile. */
@Immutable
data class FluidRichTextParagraph(val start: Int, val end: Int, val style: FluidRichTextParagraphStyle)

/** Un tratto di lunghezza [length] con un formato solo. I tratti coprono tutto il testo. */
@Immutable
internal data class FluidRichTextRun(val length: Int, val format: FluidRichTextInlineFormat)

/**
 * Un testo con formattazione: il modello dell'editor, e il formato in cui l'app lo riceve e lo da'.
 *
 * **Testo + tratti + paragrafi.** Il testo e' una `String` normale, con `\n` fra un paragrafo e il
 * successivo (e solo li': non ci sono altri a capo). Sopra ci sono due strati che il testo non vede: i
 * tratti di formato dei caratteri (grassetto, corsivo, sottolineato, link), che coprono il testo senza
 * buchi e senza due vicini uguali, e uno stile per ogni paragrafo (elenco, citazione). Per questo
 * cercare, contare, confrontare il testo e' una normale operazione su stringhe, e per questo la
 * tastiera (che vede solo il testo) e il modello non possono raccontare due storie diverse.
 *
 * **Immutabile.** Ogni modifica produce un documento nuovo: la cronologia dell'annulla tiene i
 * documenti precedenti senza copiarli, e l'app puo' confrontare due versioni con `==`.
 *
 * Si costruisce con [buildFluidRichText], da un testo con [fromPlainText], o leggendo HTML
 * ([FluidRichTextHtml.read]) o Markdown ([FluidRichTextMarkdown.read]); si modifica attraverso
 * [FluidRichTextState], che sa dove sta il cursore.
 */
@Immutable
class FluidRichTextDocument internal constructor(
  /** Il testo, con `\n` fra i paragrafi. */
  val text: String,
  internal val runs: List<FluidRichTextRun>,
  /** Lo stile di ogni paragrafo, nell'ordine: tanti quanti i `\n` del testo piu' uno. */
  val paragraphStyles: List<FluidRichTextParagraphStyle>,
) {
  private val starts: IntArray

  init {
    var newlines = 0
    val positions = ArrayList<Int>()
    positions.add(0)
    for (i in text.indices) {
      if (text[i] == '\n') {
        newlines++
        positions.add(i + 1)
      }
    }
    require(paragraphStyles.size == newlines + 1) {
      "un documento con $newlines a capo ha ${newlines + 1} paragrafi, non ${paragraphStyles.size}"
    }
    require(runs.sumOf { it.length } == text.length) { "i tratti non coprono il testo" }
    starts = positions.toIntArray()
  }

  /** Quanti paragrafi: almeno uno, anche nel documento vuoto. */
  val paragraphCount: Int get() = starts.size

  /** Vero se non c'e' testo (puo' esserci comunque uno stile di paragrafo: un elenco appena iniziato). */
  fun isEmpty(): Boolean = text.isEmpty()

  /** Dove comincia il paragrafo [index]. */
  fun paragraphStart(index: Int): Int = starts[index]

  /** Dove finisce il paragrafo [index], escluso l'a capo. */
  fun paragraphEnd(index: Int): Int = if (index + 1 < starts.size) starts[index + 1] - 1 else text.length

  /** In quale paragrafo sta la posizione [offset] (fra 0 e la lunghezza del testo, compresi). */
  fun paragraphIndexAt(offset: Int): Int {
    val clamped = offset.coerceIn(0, text.length)
    var low = 0
    var high = starts.size - 1
    while (low < high) {
      val mid = (low + high + 1) ushr 1
      if (starts[mid] <= clamped) low = mid else high = mid - 1
    }
    return low
  }

  /** I paragrafi con le loro posizioni. */
  val paragraphs: List<FluidRichTextParagraph>
    get() = List(paragraphCount) { FluidRichTextParagraph(paragraphStart(it), paragraphEnd(it), paragraphStyles[it]) }

  /** I tratti con un formato (quelli di testo semplice non ci sono), nell'ordine. */
  val spans: List<FluidRichTextSpan>
    get() {
      val result = ArrayList<FluidRichTextSpan>()
      var position = 0
      for (run in runs) {
        if (!run.format.isPlain) result.add(FluidRichTextSpan(position, position + run.length, run.format))
        position += run.length
      }
      return result
    }

  private val runStarts: IntArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val result = IntArray(runs.size)
    var position = 0
    runs.forEachIndexed { index, run ->
      result[index] = position
      position += run.length
    }
    result
  }

  /** Il formato del carattere in [offset] (`0 until text.length`). */
  fun formatAt(offset: Int): FluidRichTextInlineFormat {
    require(offset in text.indices) { "posizione $offset fuori dal testo (${text.length})" }
    return runs[runIndexAt(offset)].format
  }

  /** L'indice del tratto che contiene il carattere [offset]. */
  internal fun runIndexAt(offset: Int): Int {
    var low = 0
    var high = runStarts.size - 1
    while (low < high) {
      val mid = (low + high + 1) ushr 1
      if (runStarts[mid] <= offset) low = mid else high = mid - 1
    }
    return low
  }

  /** Dove comincia il tratto [index]. */
  internal fun runStart(index: Int): Int = runStarts[index]

  /**
   * Il numero di una voce di elenco numerato: 1 per la prima voce dell'elenco, e poi a salire. Le voci
   * dello stesso elenco sono quelle con la stessa pila di contenitori; cio' che sta in mezzo con una pila
   * piu' lunga (un sotto-elenco, una citazione nella voce) non interrompe la numerazione, cio' che ne
   * esce si'. Ogni sotto-elenco riparte da 1. Zero per un paragrafo che non e' una voce numerata.
   *
   * Si calcola una volta per documento (che e' immutabile), per tutti i paragrafi insieme.
   */
  fun listNumber(paragraph: Int): Int = listNumbers[paragraph]

  /**
   * Il numero di ogni voce (puntate comprese: serve a sapere quale voce e' la prima), 0 per i paragrafi
   * che non sono voci. Un'unica passata, con i contatori dei contenitori aperti: e' lo stesso
   * raggruppamento che fa lo scrittore dell'HTML.
   */
  private val listNumbers: IntArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val result = IntArray(paragraphStyles.size)
    val counters = IntArray(FluidRichTextParagraphStyle.MaxDepth)
    var open: List<FluidRichTextBlock> = emptyList()
    paragraphStyles.forEachIndexed { index, style ->
      val blocks = style.blocks
      val common = commonDepth(open, blocks)
      // I contenitori che si aprono qui ripartono. Un elenco aperto solo per contenere qualcosa di piu'
      // profondo (una voce senza testo suo) ha gia' una voce: nell'HTML e' un `<li>` e conta.
      for (level in common until blocks.size) {
        counters[level] = if (level < blocks.size - 1 && blocks[level] != FluidRichTextBlock.Quote) 1 else 0
      }
      if (style.list != null) {
        val level = blocks.size - 1
        counters[level]++
        if (style.list == FluidRichTextListKind.Numbered) result[index] = counters[level]
      }
      open = blocks
    }
    result
  }

  /**
   * Il pezzo `[start, end)` come documento a se': testo, formati e stili dei paragrafi toccati. E' quello
   * che si copia negli appunti, e quello che un'app prende per citare una parte.
   */
  fun slice(start: Int, end: Int): FluidRichTextDocument {
    val from = start.coerceIn(0, text.length)
    val to = end.coerceIn(from, text.length)
    val builder = RunBuilder()
    forEachRun { runStart, runEnd, format ->
      val a = maxOf(runStart, from)
      val b = minOf(runEnd, to)
      if (a < b) builder.add(b - a, format)
    }
    val first = paragraphIndexAt(from)
    val last = paragraphIndexAt(to)
    return FluidRichTextDocument(text.substring(from, to), builder.build(), paragraphStyles.subList(first, last + 1).toList())
  }

  /** Il testo semplice, con i segni degli elenchi e delle citazioni: vedi [FluidRichTextPlainText]. */
  fun toPlainText(): String = FluidRichTextPlainText.write(this)

  internal inline fun forEachRun(action: (start: Int, end: Int, format: FluidRichTextInlineFormat) -> Unit) {
    var position = 0
    for (run in runs) {
      action(position, position + run.length, run.format)
      position += run.length
    }
  }

  override fun equals(other: Any?): Boolean =
    other is FluidRichTextDocument && text == other.text && runs == other.runs && paragraphStyles == other.paragraphStyles

  override fun hashCode(): Int = (text.hashCode() * 31 + runs.hashCode()) * 31 + paragraphStyles.hashCode()

  override fun toString(): String = "FluidRichTextDocument(text=${text.take(80)}, spans=$spans, paragraphs=$paragraphStyles)"

  companion object {
    /** Il documento vuoto: un paragrafo normale senza testo. */
    val Empty: FluidRichTextDocument = FluidRichTextDocument("", emptyList(), listOf(FluidRichTextParagraphStyle.Plain))

    /**
     * Un documento di solo testo: ogni riga un paragrafo normale. Gli a capo di ogni piattaforma
     * (`\r\n`, `\r`, separatori Unicode) diventano `\n`, e i caratteri di controllo spariscono.
     */
    fun fromPlainText(text: String): FluidRichTextDocument {
      val clean = normalizeEditorText(text)
      if (clean.isEmpty()) return Empty
      val paragraphs = clean.count { it == '\n' } + 1
      return FluidRichTextDocument(
        clean,
        RunBuilder().apply { add(clean.length, FluidRichTextInlineFormat.Plain) }.build(),
        List(paragraphs) { FluidRichTextParagraphStyle.Plain },
      )
    }
  }
}

/**
 * Costruisce un documento a paragrafi, per il codice: una firma, un testo generato, un esempio.
 *
 * ```kotlin
 * val firma = buildFluidRichText {
 *   paragraph { bold("Mario Rossi") }
 *   paragraph { append("Tel. "); link("+39 055 000000", "tel:+39055000000") }
 *   paragraph(FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)) { append("una voce") }
 *   paragraph(FluidRichTextParagraphStyle.of(FluidRichTextBlock.Bullet, FluidRichTextBlock.Numbered)) {
 *     append("la prima voce numerata di un sotto-elenco")
 *   }
 * }
 * ```
 *
 * Gli indirizzi dei link passano da [FluidRichTextLinks.sanitize]: uno non sicuro lascia il testo
 * senza link. Un `\n` dentro un paragrafo diventa uno spazio (i paragrafi si fanno con [FluidRichTextBuilder.paragraph]).
 */
fun buildFluidRichText(block: FluidRichTextBuilder.() -> Unit): FluidRichTextDocument =
  FluidRichTextBuilder().apply(block).build()

/** Il costruttore di [buildFluidRichText]. */
class FluidRichTextBuilder internal constructor() {
  private val assembler = DocumentAssembler()

  /** Un paragrafo con lo stile [style] e il contenuto scritto da [content]. */
  fun paragraph(
    style: FluidRichTextParagraphStyle = FluidRichTextParagraphStyle.Plain,
    content: FluidRichTextParagraphScope.() -> Unit = {},
  ) {
    assembler.begin(style)
    FluidRichTextParagraphScope(assembler).content()
    assembler.end()
  }

  internal fun build(): FluidRichTextDocument = assembler.build()
}

/** Il contenuto di un paragrafo in [buildFluidRichText]. */
class FluidRichTextParagraphScope internal constructor(private val assembler: DocumentAssembler) {
  /** Testo con il formato [format]. */
  fun append(text: String, format: FluidRichTextInlineFormat = FluidRichTextInlineFormat.Plain) {
    val safeLink = format.link?.let { FluidRichTextLinks.sanitize(it) }
    assembler.append(normalizeEditorText(text).replace('\n', ' '), format.copy(link = safeLink))
  }

  /** Testo in grassetto. */
  fun bold(text: String) = append(text, FluidRichTextInlineFormat(bold = true))

  /** Testo in corsivo. */
  fun italic(text: String) = append(text, FluidRichTextInlineFormat(italic = true))

  /** Testo sottolineato. */
  fun underline(text: String) = append(text, FluidRichTextInlineFormat(underline = true))

  /** Un link: [text] che porta a [url] (se [url] non e' sicuro resta il solo testo). */
  fun link(text: String, url: String) = append(text, FluidRichTextInlineFormat(link = url))
}

// ---------------------------------------------------------------------------------------------------
// Pezzi interni: costruzione dei tratti e operazioni sul documento. Puri, provati sulla JVM.
// ---------------------------------------------------------------------------------------------------

/**
 * Accumula tratti fondendo i vicini uguali e scartando quelli vuoti, cosi' ogni documento costruito e'
 * gia' normalizzato e due documenti uguali hanno gli stessi tratti (e `==` funziona).
 */
internal class RunBuilder {
  private val runs = ArrayList<FluidRichTextRun>()

  fun add(length: Int, format: FluidRichTextInlineFormat) {
    if (length <= 0) return
    val last = runs.lastOrNull()
    if (last != null && last.format == format) {
      runs[runs.size - 1] = FluidRichTextRun(last.length + length, format)
    } else {
      runs.add(FluidRichTextRun(length, format))
    }
  }

  /** Aggiunge [text] con [format], ma gli a capo sempre senza formato: un a capo non e' grassetto. */
  fun addText(text: CharSequence, format: FluidRichTextInlineFormat) {
    var segment = 0
    for (char in text) {
      if (char == '\n') {
        add(segment, format)
        add(1, FluidRichTextInlineFormat.Plain)
        segment = 0
      } else {
        segment++
      }
    }
    add(segment, format)
  }

  fun build(): List<FluidRichTextRun> = runs.toList()
}

/** Costruisce un documento paragrafo per paragrafo: lo usano il builder pubblico e i lettori. */
internal class DocumentAssembler {
  private val text = StringBuilder()
  private val runs = RunBuilder()
  private val styles = ArrayList<FluidRichTextParagraphStyle>()

  /** Vero fra [begin] e [end]. */
  var isOpen: Boolean = false
    private set

  /** Quanti caratteri ha il paragrafo aperto. */
  var currentLength: Int = 0
    private set

  fun begin(style: FluidRichTextParagraphStyle) {
    if (styles.isNotEmpty()) {
      text.append('\n')
      runs.add(1, FluidRichTextInlineFormat.Plain)
    }
    styles.add(style)
    isOpen = true
    currentLength = 0
  }

  /** [content] non deve contenere `\n`. */
  fun append(content: CharSequence, format: FluidRichTextInlineFormat) {
    if (content.isEmpty()) return
    if (!isOpen) begin(FluidRichTextParagraphStyle.Plain)
    text.append(content)
    runs.add(content.length, format)
    currentLength += content.length
  }

  fun end() {
    isOpen = false
  }

  fun build(): FluidRichTextDocument =
    if (styles.isEmpty()) FluidRichTextDocument.Empty
    else FluidRichTextDocument(text.toString(), runs.build(), styles.toList())
}

/** I caratteri che l'editor accetta: gli a capo di ogni piattaforma diventano `\n`, i controlli spariscono (resta il tab). */
internal fun normalizeEditorText(input: CharSequence): String {
  var needsWork = false
  for (char in input) {
    if (char == '\r' || char == '\u2028' || char == '\u2029' || (char < ' ' && char != '\n' && char != '\t') || char == '\u007F') {
      needsWork = true
      break
    }
  }
  if (!needsWork) return input.toString()
  val out = StringBuilder(input.length)
  var index = 0
  while (index < input.length) {
    val char = input[index]
    when {
      char == '\r' -> {
        out.append('\n')
        if (index + 1 < input.length && input[index + 1] == '\n') index++
      }
      char == '\u2028' || char == '\u2029' -> out.append('\n')
      char == '\n' || char == '\t' -> out.append(char)
      char < ' ' || char == '\u007F' -> Unit
      else -> out.append(char)
    }
    index++
  }
  return out.toString()
}

/**
 * Sostituisce `[start, end)` con [insertText]/[insertRuns].
 *
 * Gli stili dei paragrafi: i paragrafi toccati dalla cancellazione si fondono e tengono lo stile del
 * primo (dove comincia la selezione). Con [insertStyles] nullo (scrittura) ogni a capo inserito crea un
 * paragrafo con lo stesso stile, cosi' un elenco continua; con gli stili di un frammento (incolla,
 * inserimento dal codice) i paragrafi interi del frammento portano i loro.
 */
internal fun FluidRichTextDocument.splice(
  start: Int,
  end: Int,
  insertText: String,
  insertRuns: List<FluidRichTextRun>,
  insertStyles: List<FluidRichTextParagraphStyle>?,
): FluidRichTextDocument {
  val from = start.coerceIn(0, text.length)
  val to = end.coerceIn(from, text.length)
  val builder = RunBuilder()
  forEachRun { runStart, runEnd, format ->
    if (runStart < from) builder.add(minOf(runEnd, from) - runStart, format)
  }
  insertRuns.forEach { builder.add(it.length, it.format) }
  forEachRun { runStart, runEnd, format ->
    if (runEnd > to) builder.add(runEnd - maxOf(runStart, to), format)
  }

  val first = paragraphIndexAt(from)
  val last = paragraphIndexAt(to)
  val newlines = insertText.count { it == '\n' }
  val prefixEmpty = from == paragraphStart(first)
  val suffixEmpty = to == paragraphEnd(last)
  val middle: List<FluidRichTextParagraphStyle> = when {
    insertStyles == null -> List(newlines + 1) { paragraphStyles[first] }
    newlines == 0 -> {
      val fragmentStyle = insertStyles.first()
      listOf(
        if (prefixEmpty && suffixEmpty && fragmentStyle != FluidRichTextParagraphStyle.Plain) fragmentStyle
        else paragraphStyles[first],
      )
    }
    else -> {
      require(insertStyles.size == newlines + 1) { "il frammento ha ${newlines + 1} paragrafi" }
      val head = if (prefixEmpty) insertStyles.first() else paragraphStyles[first]
      val lastFragmentEmpty = insertText.endsWith('\n')
      val tail = if (lastFragmentEmpty) paragraphStyles[last] else insertStyles.last()
      buildList {
        add(head)
        addAll(insertStyles.subList(1, newlines))
        add(tail)
      }
    }
  }
  val styles = buildList {
    addAll(paragraphStyles.subList(0, first))
    addAll(middle)
    addAll(paragraphStyles.subList(last + 1, paragraphStyles.size))
  }
  return FluidRichTextDocument(text.substring(0, from) + insertText + text.substring(to), builder.build(), styles)
}

/** Sostituisce `[start, end)` con testo scritto: tutti i caratteri con [format], i paragrafi nuovi con lo stile di quello toccato. */
internal fun FluidRichTextDocument.replaceText(
  start: Int,
  end: Int,
  insert: String,
  format: FluidRichTextInlineFormat,
): FluidRichTextDocument {
  val clean = normalizeEditorText(insert)
  return splice(start, end, clean, RunBuilder().apply { addText(clean, format) }.build(), null)
}

/** Sostituisce `[start, end)` con un frammento formattato, paragrafi compresi. */
internal fun FluidRichTextDocument.replaceWith(start: Int, end: Int, fragment: FluidRichTextDocument): FluidRichTextDocument =
  splice(start, end, fragment.text, fragment.runs, fragment.paragraphStyles)

/** Applica [transform] al formato dei caratteri di `[start, end)`, mai agli a capo. */
internal fun FluidRichTextDocument.mapInline(
  start: Int,
  end: Int,
  transform: (FluidRichTextInlineFormat) -> FluidRichTextInlineFormat,
): FluidRichTextDocument {
  val from = start.coerceIn(0, text.length)
  val to = end.coerceIn(from, text.length)
  if (from == to) return this
  val builder = RunBuilder()
  forEachRun { runStart, runEnd, format ->
    if (runEnd <= from || runStart >= to) {
      builder.add(runEnd - runStart, format)
    } else {
      if (runStart < from) builder.add(from - runStart, format)
      val a = maxOf(runStart, from)
      val b = minOf(runEnd, to)
      var segmentStart = a
      for (i in a until b) {
        if (text[i] == '\n') {
          builder.add(i - segmentStart, transform(format))
          builder.add(1, FluidRichTextInlineFormat.Plain)
          segmentStart = i + 1
        }
      }
      builder.add(b - segmentStart, transform(format))
      if (runEnd > to) builder.add(runEnd - to, format)
    }
  }
  return FluidRichTextDocument(text, builder.build(), paragraphStyles)
}

/** Vero se ogni carattere (a capo esclusi) di `[start, end)` soddisfa [predicate]; falso se non ce ne sono. */
internal fun FluidRichTextDocument.allInline(start: Int, end: Int, predicate: (FluidRichTextInlineFormat) -> Boolean): Boolean {
  var any = false
  var ok = true
  forEachRun { runStart, runEnd, format ->
    val a = maxOf(runStart, start)
    val b = minOf(runEnd, end)
    if (a < b) {
      val hasText = (a until b).any { text[it] != '\n' }
      if (hasText) {
        any = true
        if (!predicate(format)) ok = false
      }
    }
  }
  return any && ok
}

/** Applica [transform] allo stile dei paragrafi da [first] a [last] compresi. */
internal fun FluidRichTextDocument.mapParagraphs(
  first: Int,
  last: Int,
  transform: (FluidRichTextParagraphStyle) -> FluidRichTextParagraphStyle,
): FluidRichTextDocument {
  val styles = paragraphStyles.mapIndexed { index, style -> if (index in first..last) transform(style) else style }
  return if (styles == paragraphStyles) this else FluidRichTextDocument(text, runs, styles)
}

/**
 * Il formato con cui esce un carattere scritto in [offset]: quello del carattere prima, nello stesso
 * paragrafo; all'inizio di un paragrafo quello del primo carattere. Il link si eredita solo scrivendo
 * **dentro** (non in fondo, non in testa).
 */
internal fun FluidRichTextDocument.typingFormatAt(offset: Int): FluidRichTextInlineFormat {
  val paragraph = paragraphIndexAt(offset)
  val start = paragraphStart(paragraph)
  val end = paragraphEnd(paragraph)
  if (offset > start) {
    val before = formatAt(offset - 1)
    if (before.link != null) {
      val after = if (offset < end) formatAt(offset) else null
      if (after?.link != before.link) return before.copy(link = null)
    }
    return before
  }
  if (end > start) return formatAt(offset).copy(link = null)
  return FluidRichTextInlineFormat.Plain
}

/** L'intervallo del link che tocca [offset] (il carattere dopo, o quello prima), o null. */
internal fun FluidRichTextDocument.linkRangeAt(offset: Int): TextRange? {
  val probe = when {
    offset < text.length && formatAt(offset).link != null -> offset
    offset > 0 && offset - 1 < text.length && formatAt(offset - 1).link != null -> offset - 1
    else -> return null
  }
  val index = runIndexAt(probe)
  val url = runs[index].format.link
  // Un link puo' essere fatto di piu' tratti (una parola in grassetto nel mezzo): si allarga finche' i
  // vicini portano lo stesso indirizzo. Gli a capo sono tratti senza formato, quindi lo fermano da soli.
  var first = index
  while (first > 0 && runs[first - 1].format.link == url) first--
  var last = index
  while (last + 1 < runs.size && runs[last + 1].format.link == url) last++
  return TextRange(runStart(first), runStart(last) + runs[last].length)
}

/** Una selezione riportata dentro `[0, length]`, con la stessa direzione. */
internal fun TextRange.clampTo(length: Int): TextRange =
  TextRange(start.coerceIn(0, length), end.coerceIn(0, length))
