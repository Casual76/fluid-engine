package dev.antigravity.fluidengine.richtext

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange

/**
 * Da dove viene l'ultima modifica del documento: serve a un'app che salva di continuo per distinguere
 * cio' che l'utente ha scritto da cio' che ha messo lei (una firma, un testo generato) o dall'annulla.
 * **Puo' crescere**: niente `when` esaustivi senza `else`.
 */
enum class FluidRichTextChangeSource {
  /** Tastiera, IME, correzione automatica, dettatura: testo scritto o cancellato. */
  Typing,

  /** Uno stile messo o tolto (barra, scorciatoia, a capo che esce da un elenco). */
  Formatting,

  /** Incollato dagli appunti. */
  Paste,

  /** Dal codice dell'app ([FluidRichTextState.setDocument], [FluidRichTextState.replace]…). */
  Program,

  /** Annulla o ripeti. */
  History,
}

/**
 * Lo stato di un [FluidRichTextEditor]: il documento, il cursore, la cronologia per annullare, e cosa
 * mostra la barra di formattazione.
 *
 * **Chi lo possiede.** L'app, con [rememberFluidRichTextState]: sopravvive alla rotazione e alla morte
 * del processo (documento e selezione, non la cronologia), e lo passa all'editor e alla barra, che
 * possono stare in due punti diversi dell'albero (l'editor nella pagina, la barra nell'overlay sopra la
 * tastiera).
 *
 * **Salvare di continuo.** [revision] cresce a ogni modifica del documento ed e' stato di Compose:
 * `snapshotFlow { state.revision }` e' il modo economico di sapere che c'e' qualcosa da salvare, senza
 * confrontare documenti; [document] e' immutabile e si legge in O(1). [lastChangeSource] dice chi ha
 * fatto l'ultima modifica. Il salvataggio vero (una bozza su disco) e' dell'app: lo stato salvato da
 * Android (`rememberSaveable`) serve a ricreare la schermata, non e' una garanzia contro la perdita di
 * testo, e per un documento oltre [MaxSavedLength] caratteri **non** salva il documento (un `Bundle`
 * troppo grande fa cadere l'app): in quel caso l'app lo riapre dalla sua bozza.
 *
 * **Modificarlo dal codice.** [setDocument], [replace], [insert], [replaceSelection] (una firma, il
 * risultato di un assistente), i comandi della barra ([toggle], [toggleList], [toggleQuote],
 * [increaseIndent], [decreaseIndent], [setLink], [removeLink]) e [undo]/[redo]: ogni modifica si annulla. Il testo non si cambia in altro modo: lo
 * stato del campo di testo sotto e' privato, cosi' testo e stili non possono divergere.
 */
@Stable
class FluidRichTextState(
  initial: FluidRichTextDocument = FluidRichTextDocument.Empty,
  initialSelection: TextRange = TextRange(initial.text.length),
) {
  internal val textFieldState: TextFieldState = TextFieldState(
    initial.text,
    initialSelection.clampTo(initial.text.length),
  )

  private var documentState by mutableStateOf(initial)

  /** Il documento com'e' adesso. */
  val document: FluidRichTextDocument get() = documentState

  /** Il cursore, o la selezione (`start` puo' essere dopo `end` se si e' selezionato all'indietro). */
  val selection: TextRange get() = textFieldState.selection

  /**
   * La parola che la tastiera sta ancora componendo (sottolineata da Gboard mentre si scrive o si
   * detta), o null. Un'app non deve sostituire il testo li' dentro: la tastiera la riscriverebbe.
   */
  val composition: TextRange? get() = textFieldState.composition

  /** Cresce di uno a ogni modifica del documento (testo o stile). Per salvare di continuo. */
  var revision: Long by mutableLongStateOf(0L)
    private set

  /** Chi ha fatto l'ultima modifica. */
  var lastChangeSource: FluidRichTextChangeSource by mutableStateOf(FluidRichTextChangeSource.Program)
    private set

  internal var pending: PendingFormat? by mutableStateOf(null)

  private val formatState = derivedStateOf { documentState.formatFor(textFieldState.selection, pending) }

  /** Cosa e' acceso al cursore o su tutta la selezione: vedi [FluidRichTextFormat]. */
  val format: FluidRichTextFormat get() = formatState.value

  /** Vero mentre l'editor ha il fuoco (la tastiera e' sua). */
  var isFocused: Boolean by mutableStateOf(false)
    internal set

  /**
   * Il tasto «Aa»: vero quando l'utente ha chiesto la barra di formattazione anche senza selezione.
   * Lo cambiano [FluidRichTextFormatButton] e il primo tasto della barra; un'app puo' scriverlo.
   */
  var isFormattingRequested: Boolean by mutableStateOf(false)

  internal var isEditable: Boolean by mutableStateOf(true)

  /** Vero quando la barra di formattazione deve stare sopra la tastiera: editor col fuoco e una selezione, o «Aa». */
  val isFormattingBarVisible: Boolean
    get() = isEditable && isFocused && (isFormattingRequested || !textFieldState.selection.collapsed)

  /** Il link che si sta modificando nel foglio della barra: null quando il foglio e' chiuso. */
  internal var linkRequest: FluidRichTextLinkRequest? by mutableStateOf(null)

  internal val focusRequester: FocusRequester = FocusRequester()

  /**
   * L'ultima selezione non vuota, col documento di quel momento: quando il campo copia o taglia, la
   * selezione puo' essere gia' chiusa (o il testo gia' tolto) prima che gli appunti vengano scritti, e
   * l'HTML della copia si ricava da qui. Il documento e' immutabile: tenerlo costa un riferimento.
   */
  internal var lastSelection: Pair<FluidRichTextDocument, TextRange>? = null

  /** Chiede il fuoco per l'editor (e quindi la tastiera). Senza un editor in scena non fa niente. */
  fun requestFocus() {
    runCatching { focusRequester.requestFocus() }
  }

  // --- cronologia ----------------------------------------------------------------------------------

  private class Snapshot(val document: FluidRichTextDocument, val selection: TextRange)

  private enum class UnitKind { Typing, Deleting, Other }

  private val undoStack = ArrayDeque<Snapshot>()
  private val redoStack = ArrayDeque<Snapshot>()
  private var undoCount by mutableIntStateOf(0)
  private var redoCount by mutableIntStateOf(0)
  private var openUnit: UnitKind? = null
  private var selectionAfterInput: TextRange? = null

  /** Vero se c'e' qualcosa da annullare. */
  val canUndo: Boolean get() = undoCount > 0

  /** Vero se c'e' qualcosa da ripetere. */
  val canRedo: Boolean get() = redoCount > 0

  private fun record(kind: UnitKind, selectionBefore: TextRange) {
    // Le lettere di una parola sono un passo solo: annullare toglie la parola, non una lettera. Si
    // chiude il passo a fine parola, quando cambia il tipo (scrivere/cancellare) o se il cursore si e'
    // spostato da dove l'aveva lasciato l'ultima modifica.
    val coalesce = kind != UnitKind.Other && kind == openUnit && selectionAfterInput == selectionBefore
    if (!coalesce) {
      undoStack.addLast(Snapshot(documentState, selectionBefore))
      while (undoStack.size > MaxHistory) undoStack.removeFirst()
    }
    openUnit = if (kind == UnitKind.Other) null else kind
    redoStack.clear()
    syncCounts()
  }

  private fun syncCounts() {
    undoCount = undoStack.size
    redoCount = redoStack.size
  }

  /** Annulla l'ultimo passo (una parola, uno stile, un incolla, un inserimento dal codice). */
  fun undo() {
    val snapshot = undoStack.removeLastOrNull() ?: return
    redoStack.addLast(Snapshot(documentState, selection))
    openUnit = null
    syncCounts()
    apply(snapshot.document, snapshot.selection, FluidRichTextChangeSource.History)
  }

  /** Ripete l'ultimo passo annullato. */
  fun redo() {
    val snapshot = redoStack.removeLastOrNull() ?: return
    undoStack.addLast(Snapshot(documentState, selection))
    openUnit = null
    syncCounts()
    apply(snapshot.document, snapshot.selection, FluidRichTextChangeSource.History)
  }

  /** Dimentica la cronologia (per esempio dopo aver aperto una bozza: non si annulla l'apertura). */
  fun clearHistory() {
    undoStack.clear()
    redoStack.clear()
    openUnit = null
    syncCounts()
  }

  // --- modifiche dal codice ------------------------------------------------------------------------

  /**
   * Sostituisce tutto il documento (una bozza riaperta, un testo generato accettato). Si annulla, a meno
   * che subito dopo l'app chiami [clearHistory]. Il cursore va dove dice [selection], di serie in fondo.
   */
  fun setDocument(document: FluidRichTextDocument, selection: TextRange = TextRange(document.text.length)) {
    commit(document, selection, FluidRichTextChangeSource.Program)
  }

  /**
   * Sostituisce `[start, end)` con [fragment], paragrafi e stili compresi, e mette il cursore dopo il
   * frammento. E' il modo di inserire o rimpiazzare un blocco noto: l'app sa dov'e' (cerca nel
   * [FluidRichTextDocument.text]) e lo sostituisce.
   */
  fun replace(start: Int, end: Int, fragment: FluidRichTextDocument) {
    val length = documentState.text.length
    val from = minOf(start, end).coerceIn(0, length)
    val to = maxOf(start, end).coerceIn(0, length)
    commit(documentState.replaceWith(from, to, fragment), TextRange(from + fragment.text.length), FluidRichTextChangeSource.Program)
  }

  /** Inserisce [fragment] in [offset]. */
  fun insert(offset: Int, fragment: FluidRichTextDocument) = replace(offset, offset, fragment)

  /** Mette [fragment] al posto della selezione (o al cursore). */
  fun replaceSelection(fragment: FluidRichTextDocument) = replace(selection.min, selection.max, fragment)

  internal fun paste(fragment: FluidRichTextDocument) {
    val from = selection.min
    val to = selection.max
    commit(documentState.replaceWith(from, to, fragment), TextRange(from + fragment.text.length), FluidRichTextChangeSource.Paste)
  }

  internal fun pastePlain(text: String) {
    val from = selection.min
    val to = selection.max
    val clean = normalizeEditorText(text)
    val format = pending?.takeIf { it.offset == from && from == to }?.format ?: documentState.typingFormatAt(from)
    commit(documentState.replaceText(from, to, clean, format), TextRange(from + clean.length), FluidRichTextChangeSource.Paste)
  }

  internal fun forceReconcile(fieldText: String) {
    documentState = documentState.reconcile(fieldText)
    bump(FluidRichTextChangeSource.Typing)
  }

  /** Sposta il cursore o la selezione. */
  fun select(selection: TextRange) {
    val length = documentState.text.length
    textFieldState.edit { this.selection = selection.clampTo(length) }
  }

  /** Il pezzo selezionato, con i suoi stili. Vuoto senza selezione. */
  fun selectedDocument(): FluidRichTextDocument = documentState.slice(selection.min, selection.max)

  // --- comandi della barra -------------------------------------------------------------------------

  /**
   * Accende o spegne [style]. Con una selezione vale per tutta (se era acceso su tutta si spegne,
   * altrimenti si accende su tutta); col cursore fermo vale per cio' che si scrivera' da li'.
   */
  fun toggle(style: FluidRichTextInlineStyle) {
    val current = selection
    if (current.collapsed) {
      val base = pending?.takeIf { it.offset == current.start }?.format ?: documentState.typingFormatAt(current.start)
      pending = PendingFormat(current.start, base.with(style, !base.has(style)))
      return
    }
    val on = !documentState.allInline(current.min, current.max) { it.has(style) }
    commit(documentState.mapInline(current.min, current.max) { it.with(style, on) }, null, FluidRichTextChangeSource.Formatting)
  }

  /**
   * Mette o toglie l'elenco [kind] ai paragrafi toccati dalla selezione.
   *
   * Se sono gia' tutti voci di un elenco [kind] (a qualunque livello) escono dagli elenchi: tornano
   * paragrafi, dentro le citazioni che avevano. Altrimenti diventano voci [kind]: una voce di un elenco
   * dell'altro tipo cambia tipo restando al suo livello, un paragrafo (anche citato) diventa una voce li'
   * dove sta.
   */
  fun toggleList(kind: FluidRichTextListKind) {
    val (first, last) = selectedParagraphs()
    val all = (first..last).all { documentState.paragraphStyles[it].list == kind }
    commit(
      documentState.mapParagraphs(first, last) { if (all) it.withoutList() else it.withList(kind) },
      null,
      FluidRichTextChangeSource.Formatting,
    )
  }

  /**
   * Mette o toglie la citazione ai paragrafi toccati dalla selezione: se stanno gia' tutti in una
   * citazione ne escono del tutto (anche dalle citazioni annidate; gli elenchi restano), altrimenti chi
   * non c'era entra in una citazione che lo contiene tutto (elenchi e sotto-elenchi compresi).
   */
  fun toggleQuote() {
    val (first, last) = selectedParagraphs()
    val all = (first..last).all { documentState.paragraphStyles[it].quote }
    commit(
      documentState.mapParagraphs(first, last) { if (all) it.withoutQuotes() else if (it.quote) it else it.wrappedInQuote() },
      null,
      FluidRichTextChangeSource.Formatting,
    )
  }

  /**
   * Aumenta di un livello il rientro dei paragrafi toccati che ne hanno uno: una voce diventa la voce di
   * un sotto-elenco dello stesso tipo (la numerazione riparte), un paragrafo citato una citazione nella
   * citazione. I paragrafi normali e quelli gia' a [FluidRichTextParagraphStyle.MaxDepth] restano dove
   * sono. Si annulla in un passo. Il tasto della barra e Tab da una tastiera fisica fanno questo; se non
   * cambierebbe niente ([FluidRichTextFormat.canIncreaseIndent] falso) non fa niente.
   */
  fun increaseIndent() {
    val (first, last) = selectedParagraphs()
    commit(documentState.mapParagraphs(first, last) { it.indented() }, null, FluidRichTextChangeSource.Formatting)
  }

  /**
   * Diminuisce di un livello il rientro dei paragrafi toccati: una voce di un sotto-elenco torna voce
   * dell'elenco sopra, una voce di primo livello torna paragrafo, una citazione annidata torna citazione
   * semplice, un paragrafo citato esce dalla citazione. Il tasto della barra e Maiusc+Tab.
   */
  fun decreaseIndent() {
    val (first, last) = selectedParagraphs()
    commit(documentState.mapParagraphs(first, last) { it.outdented() }, null, FluidRichTextChangeSource.Formatting)
  }

  /**
   * Fa della selezione un link a [url] (completato con [FluidRichTextLinks.fromUserInput]: `esempio.it`
   * va bene). Col cursore dentro un link ne cambia l'indirizzo; col cursore fermo altrove inserisce
   * [text] (o l'indirizzo) come link. Con [text] diverso dal testo attuale lo sostituisce.
   *
   * @return falso, senza cambiare niente, se l'indirizzo non e' sicuro (`javascript:`, vuoto…).
   */
  fun setLink(url: String, text: String? = null): Boolean {
    val safe = FluidRichTextLinks.fromUserInput(url) ?: return false
    val range = linkTargetRange()
    applyLink(range, safe, text)
    return true
  }

  /** Toglie il link dalla selezione, o dal link che il cursore tocca. Il testo resta. */
  fun removeLink() {
    val current = selection
    val range = if (current.collapsed) documentState.linkRangeAt(current.start) ?: return else TextRange(current.min, current.max)
    commit(documentState.mapInline(range.min, range.max) { it.copy(link = null) }, null, FluidRichTextChangeSource.Formatting)
  }

  internal fun linkTargetRange(): TextRange {
    val current = selection
    if (!current.collapsed) return TextRange(current.min, current.max)
    return documentState.linkRangeAt(current.start) ?: TextRange(current.start)
  }

  internal fun applyLink(range: TextRange, safeUrl: String, text: String?) {
    val doc = documentState
    val currentText = doc.text.substring(range.min, range.max)
    val wantedText = text?.let { normalizeEditorText(it).replace('\n', ' ') }?.takeIf { it.isNotEmpty() }
    val result: FluidRichTextDocument
    val end: Int
    if (range.collapsed || (wantedText != null && wantedText != currentText && !currentText.contains('\n'))) {
      val shown = wantedText ?: FluidRichTextLinks.displayText(safeUrl)
      val base = if (range.collapsed) doc.typingFormatAt(range.min) else doc.formatAt(range.min)
      result = doc.replaceText(range.min, range.max, shown, base.copy(link = safeUrl))
      end = range.min + shown.length
    } else {
      result = doc.mapInline(range.min, range.max) { it.copy(link = safeUrl) }
      end = range.max
    }
    commit(result, TextRange(end), FluidRichTextChangeSource.Formatting)
  }

  /** Apre il foglio del link della barra sulla selezione (o sul link al cursore). */
  internal fun openLinkEditor() {
    val range = linkTargetRange()
    val doc = documentState
    val text = doc.text.substring(range.min, range.max)
    val url = if (range.collapsed) null else doc.allInline(range.min, range.max) { it.link != null }.let {
      if (it) doc.formatAt(range.min).link else null
    }
    linkRequest = FluidRichTextLinkRequest(range, text, url ?: "", canEditText = !text.contains('\n'))
  }

  private fun selectedParagraphs(): Pair<Int, Int> {
    val current = selection
    return documentState.paragraphIndexAt(current.min) to documentState.paragraphIndexAt(current.max)
  }

  // --- tastiera ------------------------------------------------------------------------------------

  /**
   * «Cancella» con il cursore all'inizio del primo paragrafo: non c'e' niente da cancellare, quindi la
   * tastiera non manda una modifica, ma una voce di elenco li' deve poter tornare paragrafo. Ritorna
   * vero se ha tolto uno stile.
   */
  internal fun backspaceAtStart(): Boolean {
    val current = selection
    if (!current.collapsed) return false
    val paragraph = documentState.paragraphIndexAt(current.start)
    if (current.start != documentState.paragraphStart(paragraph) || paragraph != 0) return false
    val style = documentState.paragraphStyles[paragraph]
    if (style.depth == 0) return false
    commit(documentState.mapParagraphs(paragraph, paragraph) { it.outdented() }, null, FluidRichTextChangeSource.Formatting)
    return true
  }

  /** La trasformazione che vede ogni modifica della tastiera e la riporta sul documento. */
  internal val inputTransformation: InputTransformation = object : InputTransformation {
    override fun TextFieldBuffer.transformInput() = onUserInput(this)
  }

  @OptIn(ExperimentalFoundationApi::class)
  private fun onUserInput(buffer: TextFieldBuffer) {
    if (buffer.changes.changeCount == 0) return
    normalizeInserted(buffer)
    val changes = buffer.changes
    if (changes.changeCount == 0) return
    var doc = documentState
    val original = buffer.originalText
    // Rete di sicurezza: se il testo e' cambiato per una strada che non e' passata di qui (non ce ne
    // sono di previste), il documento si riallinea prima di applicare la modifica nuova.
    if (!original.contentEquals(doc.text)) doc = doc.reconcile(original.toString())
    val current = buffer.asCharSequence()
    val list = (0 until changes.changeCount).map { index ->
      val before = changes.getOriginalRange(index)
      val after = changes.getRange(index)
      UserChange(before.min, before.max, current.subSequence(after.min, after.max).toString())
    }
    val selectionBefore = buffer.originalSelection
    when (val outcome = applyUserChanges(doc, list, selectionBefore, pending)) {
      is UserEditOutcome.Intercepted -> {
        buffer.revertAllChanges()
        record(UnitKind.Other, selectionBefore)
        documentState = outcome.document
        pending = null
        bump(FluidRichTextChangeSource.Formatting)
      }
      is UserEditOutcome.Applied -> {
        val inserted = list.joinToString("") { it.inserted }
        val kind = if (inserted.isEmpty()) UnitKind.Deleting else UnitKind.Typing
        record(kind, selectionBefore)
        documentState = outcome.document
        pending = outcome.pending
        selectionAfterInput = buffer.selection
        if (inserted.lastOrNull()?.isWhitespace() == true) openUnit = null
        bump(FluidRichTextChangeSource.Typing)
      }
    }
  }

  /** Gli a capo di Windows, i separatori Unicode e i caratteri di controllo incollati diventano testo pulito. */
  @OptIn(ExperimentalFoundationApi::class)
  private fun normalizeInserted(buffer: TextFieldBuffer) {
    val changes = buffer.changes
    for (index in changes.changeCount - 1 downTo 0) {
      val range = changes.getRange(index)
      if (range.collapsed) continue
      val inserted = buffer.asCharSequence().subSequence(range.min, range.max)
      val clean = normalizeEditorText(inserted)
      if (clean != inserted.toString()) buffer.replace(range.min, range.max, clean)
    }
  }

  // --- applicare -----------------------------------------------------------------------------------

  /** Una modifica dal codice o dalla barra: si registra per l'annulla, poi si applica. */
  private fun commit(newDocument: FluidRichTextDocument, newSelection: TextRange?, source: FluidRichTextChangeSource) {
    if (newDocument == documentState && newSelection == null) return
    record(UnitKind.Other, selection)
    apply(newDocument, newSelection, source)
  }

  /**
   * Scrive documento e testo insieme. Il campo di testo cambia solo dove il testo e' diverso (una
   * sostituzione minima, non tutto il testo): la tastiera non perde la sua posizione per uno stile, e
   * per un inserimento rivede solo il pezzo nuovo.
   */
  private fun apply(newDocument: FluidRichTextDocument, newSelection: TextRange?, source: FluidRichTextChangeSource) {
    documentState = newDocument
    pending = null
    val old = textFieldState.text
    val textChanged = !old.contentEquals(newDocument.text)
    if (textChanged || newSelection != null) {
      textFieldState.edit {
        if (textChanged) {
          val (start, oldEnd, newEnd) = minimalDiff(old, newDocument.text)
          replace(start, oldEnd, newDocument.text.substring(start, newEnd))
        }
        if (newSelection != null) selection = newSelection.clampTo(newDocument.text.length)
      }
    }
    bump(source)
  }

  private fun bump(source: FluidRichTextChangeSource) {
    lastChangeSource = source
    revision++
  }

  companion object {
    /** Quanti passi tiene l'annulla. */
    const val MaxHistory: Int = 100

    /**
     * Oltre questa lunghezza lo stato salvato da Android non porta il documento (vedi la nota sul
     * salvataggio in [FluidRichTextState]).
     */
    const val MaxSavedLength: Int = 50_000

    /** Salva documento, selezione e «Aa» in tipi che un `Bundle` accetta. Non la cronologia. */
    val Saver: Saver<FluidRichTextState, Any> = Saver(
      save = { state ->
        val doc = state.document
        if (doc.text.length > MaxSavedLength) {
          null
        } else {
          arrayListOf(
            doc.text,
            encodeRuns(doc),
            ArrayList(linksOf(doc)),
            encodeParagraphs(doc),
            state.selection.start,
            state.selection.end,
            state.isFormattingRequested,
            SavedParagraphsVersion,
          )
        }
      },
      restore = { saved ->
        runCatching {
          val list = saved as List<*>
          val text = list[0] as String
          val links = (list[2] as List<*>).map { it as String }
          val nested = list.size > 7 && list[7] == SavedParagraphsVersion
          val paragraphs = if (nested) decodeParagraphs(list[3] as IntArray) else decodeFlatParagraphs(list[3] as IntArray)
          val document = FluidRichTextDocument(text, decodeRuns(list[1] as IntArray, links), paragraphs)
          FluidRichTextState(document, TextRange(list[4] as Int, list[5] as Int)).also {
            it.isFormattingRequested = list[6] as Boolean
          }
        }.getOrNull()
      },
    )

    private fun linksOf(doc: FluidRichTextDocument): List<String> = doc.runs.mapNotNull { it.format.link }.distinct()

    private fun encodeRuns(doc: FluidRichTextDocument): IntArray {
      val links = linksOf(doc)
      val out = IntArray(doc.runs.size * 2)
      doc.runs.forEachIndexed { index, run ->
        val format = run.format
        var flags = 0
        if (format.bold) flags = flags or 1
        if (format.italic) flags = flags or 2
        if (format.underline) flags = flags or 4
        format.link?.let { flags = flags or ((links.indexOf(it) + 1) shl 3) }
        out[index * 2] = run.length
        out[index * 2 + 1] = flags
      }
      return out
    }

    private fun decodeRuns(data: IntArray, links: List<String>): List<FluidRichTextRun> {
      val builder = RunBuilder()
      for (index in 0 until data.size / 2) {
        val flags = data[index * 2 + 1]
        val linkIndex = (flags shr 3) - 1
        builder.add(
          data[index * 2],
          FluidRichTextInlineFormat(
            bold = flags and 1 != 0,
            italic = flags and 2 != 0,
            underline = flags and 4 != 0,
            link = links.getOrNull(linkIndex),
          ),
        )
      }
      return builder.build()
    }

    /**
     * Il formato degli stili di paragrafo nello stato salvato: 2 da quando gli elenchi si annidano (la
     * pila dei contenitori in un `Int`: la profondita' nei 4 bit bassi, poi 2 bit per contenitore). Lo
     * stato salvato senza versione e' quello a un livello di prima, e si legge ancora.
     */
    private const val SavedParagraphsVersion: Int = 2

    private fun encodeParagraphs(doc: FluidRichTextDocument): IntArray = IntArray(doc.paragraphCount) { index ->
      val blocks = doc.paragraphStyles[index].blocks
      var code = blocks.size
      blocks.forEachIndexed { level, block ->
        val kind = when (block) {
          FluidRichTextBlock.Quote -> 0
          FluidRichTextBlock.Bullet -> 1
          FluidRichTextBlock.Numbered -> 2
        }
        code = code or (kind shl (4 + 2 * level))
      }
      code
    }

    private fun decodeParagraphs(data: IntArray): List<FluidRichTextParagraphStyle> = data.map { code ->
      val depth = (code and 15).coerceAtMost(FluidRichTextParagraphStyle.MaxDepth)
      FluidRichTextParagraphStyle(
        List(depth) { level ->
          when ((code shr (4 + 2 * level)) and 3) {
            1 -> FluidRichTextBlock.Bullet
            2 -> FluidRichTextBlock.Numbered
            else -> FluidRichTextBlock.Quote
          }
        },
      )
    }

    private fun decodeFlatParagraphs(data: IntArray): List<FluidRichTextParagraphStyle> = data.map { code ->
      FluidRichTextParagraphStyle(
        list = when (code and 3) {
          1 -> FluidRichTextListKind.Bullet
          2 -> FluidRichTextListKind.Numbered
          else -> null
        },
        quote = code and 4 != 0,
      )
    }
  }
}

/** Il link che il foglio della barra sta modificando. */
internal data class FluidRichTextLinkRequest(
  val range: TextRange,
  val text: String,
  val url: String,
  val canEditText: Boolean,
)

/**
 * Crea e ricorda lo stato di un editor, salvato per la rotazione e la morte del processo.
 *
 * [initial] conta solo la prima volta: dopo, lo stato e' quello salvato. Per riaprire una bozza dopo
 * che lo stato esiste gia' si usa [FluidRichTextState.setDocument] (seguito da
 * [FluidRichTextState.clearHistory], se l'apertura non deve essere annullabile).
 */
@Composable
fun rememberFluidRichTextState(initial: FluidRichTextDocument = FluidRichTextDocument.Empty): FluidRichTextState =
  rememberSaveable(saver = FluidRichTextState.Saver) { FluidRichTextState(initial) }

private fun CharSequence.contentEquals(other: String): Boolean {
  if (length != other.length) return false
  for (index in indices) if (this[index] != other[index]) return false
  return true
}
