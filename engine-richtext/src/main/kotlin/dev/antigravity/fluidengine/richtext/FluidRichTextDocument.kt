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
 * Il tipo di elenco di un paragrafo. Un solo livello: un elenco dentro un elenco non c'e' (vedi
 * [FluidRichTextParagraphStyle]). **Puo' crescere**: niente `when` esaustivi senza `else`.
 */
enum class FluidRichTextListKind { Bullet, Numbered }

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
 * Lo stile di un paragrafo: se e' una voce di elenco, e se sta in una citazione.
 *
 * **Un livello solo di elenco e di citazione.** Elenchi annidati e citazioni dentro citazioni sono ci'o
 * che rende un editor sul telefono difficile da usare (rientri che non si vedono, «indietro» che non
 * si capisce cosa toglie) e da serializzare; un HTML in ingresso che li ha viene appiattito a un livello
 * (il testo resta tutto). Una voce di elenco dentro una citazione si', perche' e' quello che produce
 * una risposta citata.
 */
@Immutable
data class FluidRichTextParagraphStyle(
  val list: FluidRichTextListKind? = null,
  val quote: Boolean = false,
) {
  companion object {
    /** Un paragrafo normale. */
    val Plain: FluidRichTextParagraphStyle = FluidRichTextParagraphStyle()
  }
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
   * Il numero di una voce di elenco numerato: 1 per la prima di una serie di voci numerate consecutive
   * (con la stessa citazione), e poi a salire. Zero per un paragrafo che non e' una voce numerata.
   */
  fun listNumber(paragraph: Int): Int {
    val style = paragraphStyles[paragraph]
    if (style.list != FluidRichTextListKind.Numbered) return 0
    var number = 1
    var index = paragraph - 1
    while (index >= 0 && paragraphStyles[index] == style) {
      number++
      index--
    }
    return number
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
    if (char == '\r' || char == ' ' || char == ' ' || (char < ' ' && char != '\n' && char != '\t') || char == '\u007F') {
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
      char == ' ' || char == ' ' -> out.append('\n')
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
