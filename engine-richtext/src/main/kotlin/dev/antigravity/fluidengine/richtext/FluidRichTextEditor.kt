package dev.antigravity.fluidengine.richtext

import android.content.ClipData
import android.text.Html
import android.text.Spanned
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** I valori di serie di [FluidRichTextEditor]. */
object FluidRichTextEditorDefaults {
  /**
   * La tastiera di chi scrive un testo: maiuscola a inizio frase, correzione e suggerimenti accesi, il
   * tasto invio che va a capo.
   */
  val KeyboardOptions: KeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    autoCorrectEnabled = true,
  )

  /** Il rientro di una voce di elenco: lo spazio del segno, in multipli della dimensione del testo. */
  const val ListIndentEm: Float = 1.6f

  /** Il rientro di un paragrafo citato: la barra e l'aria dopo. */
  const val QuoteIndentEm: Float = 1.0f
}

/**
 * Un editor di testo con formattazione: grassetto, corsivo, sottolineato, elenchi puntati e numerati,
 * link, citazione. Per il corpo di una mail, una firma, una nota, un messaggio lungo.
 *
 * **Due pezzi, uno stato.** L'editor e' il campo; la barra dei comandi e' [FluidRichTextToolbar], che
 * l'app posa sopra la tastiera (nell'overlay della pagina) passandole lo stesso [state]. Il tasto «Aa»
 * che chiede la barra e' [FluidRichTextFormatButton], dove l'app vuole; selezionare del testo la fa
 * comparire da sola.
 *
 * **Come funziona sotto.** Un `BasicTextField` a stato (`TextFieldState`) che contiene solo il testo,
 * cosi' la tastiera (Gboard: suggerimenti, correzione, scrittura col dito, dettatura) vede un campo di
 * testo normale; ogni modifica che fa passa da una trasformazione d'ingresso che la riporta sul
 * documento, con gli stili spostati, ereditati, fusi ([FluidRichTextState]); gli stili si disegnano con
 * una trasformazione d'uscita, e i segni degli elenchi e la barra della citazione accanto al testo, nel
 * rientro del paragrafo. Il testo che la tastiera vede e quello del documento sono lo stesso, carattere
 * per carattere: nessun segno inserito nel testo, nessuna posizione da tradurre.
 *
 * **Incolla** dagli appunti: l'HTML (da un browser, da Google Documenti, da un'altra mail) tiene cio' che
 * l'editor sa mostrare ([FluidRichTextHtml.read]); il testo con stili di un'altra app pure; il testo
 * semplice entra con il formato del punto in cui si incolla. **Copia** mette negli appunti anche l'HTML
 * della selezione, cosi' copiare e incollare dentro l'editor non perde gli stili.
 *
 * **Scorciatoie** da tastiera fisica: Ctrl+Z / Ctrl+Maiusc+Z / Ctrl+Y annulla e ripete (la cronologia e'
 * quella dell'editor, con gli stili), Ctrl+B/I/U.
 *
 * **TalkBack** legge il testo; lo stato del campo dice cosa e' acceso al cursore («Grassetto, elenco
 * puntato»), con le parole di [LocalFluidRichTextLabels].
 *
 * Non ha un pozzo ne' un bordo: occupa la larghezza che riceve e cresce col testo. Il contenitore (una
 * pagina che scorre, una card) e' dell'app. Dentro uno spazio di altezza fissa scorre da se'.
 *
 * @param placeholder il testo grigio quando il documento e' vuoto.
 * @param contentDescription il nome del campo per TalkBack («Corpo», «Firma»); senza, il segnaposto non
 *   basta, perche' sparisce appena si scrive.
 * @param textStyle lo stile del testo; il colore, se non e' indicato, e' `onSurface`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FluidRichTextEditor(
  state: FluidRichTextState,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  contentDescription: String? = null,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  minLines: Int = 1,
  textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
  keyboardOptions: KeyboardOptions = FluidRichTextEditorDefaults.KeyboardOptions,
) {
  val scheme = MaterialTheme.colorScheme
  val labels = LocalFluidRichTextLabels.current
  val resolvedStyle = if (textStyle.color.isSpecified) textStyle else textStyle.copy(color = scheme.onSurface)
  val colors = RichTextColors(
    link = scheme.primary,
    quoteText = scheme.onSurfaceVariant,
    quoteBar = scheme.outline,
    marker = resolvedStyle.color,
  )
  SideEffect { state.isEditable = enabled && !readOnly }

  val scroll = rememberScrollState()
  var layoutGetter by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
  val measurer = rememberTextMeasurer()
  val output = remember(state, colors) { RichTextOutputTransformation(state, colors) }
  val clipboard = LocalClipboard.current
  val richClipboard = remember(clipboard, state) { RichTextClipboard(clipboard, state) }
  val receiver = remember(state) { RichTextPasteReceiver(state) }

  // La rete di sicurezza: se il testo del campo e il documento si separassero (non dovrebbe succedere:
  // l'unica strada per cambiare il testo passa dalla trasformazione d'ingresso), si riallineano qui.
  LaunchedEffect(state) {
    snapshotFlow { state.textFieldState.text.toString() }.collect { state.reconcileWithField(it) }
  }
  LaunchedEffect(state) {
    // Selezione e documento insieme: uno stile messo sulla selezione cambia cosa si copiera'.
    snapshotFlow { state.document to state.selection }.collect { (document, selection) ->
      if (!selection.collapsed) state.lastSelection = document to TextRange(selection.min, selection.max)
    }
  }

  val description = state.format.describe(labels)
  CompositionLocalProvider(LocalClipboard provides richClipboard) {
    BasicTextField(
      state = state.textFieldState,
      modifier = modifier
        .fillMaxWidth()
        .focusRequester(state.focusRequester)
        .onFocusChanged { state.isFocused = it.isFocused }
        .onPreviewKeyEvent { event -> enabled && !readOnly && state.handleKeyEvent(event) }
        .contentReceiver(receiver)
        .semantics {
          if (description.isNotEmpty()) stateDescription = description
          if (contentDescription != null) this.contentDescription = contentDescription
        },
      enabled = enabled,
      readOnly = readOnly,
      inputTransformation = state.inputTransformation,
      textStyle = resolvedStyle,
      keyboardOptions = keyboardOptions,
      lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = minLines.coerceAtLeast(1)),
      onTextLayout = { getResult -> layoutGetter = getResult },
      cursorBrush = SolidColor(scheme.primary),
      outputTransformation = output,
      scrollState = scroll,
      decorator = { innerTextField ->
        Box(
          modifier = Modifier
            .clipToBounds()
            .drawBehind {
              val layout = layoutGetter?.invoke() ?: return@drawBehind
              translate(top = -scroll.value.toFloat()) {
                drawParagraphDecorations(state.document, layout, resolvedStyle, colors, measurer)
              }
            },
        ) {
          if (placeholder != null && state.document.isEmpty()) {
            Text(
              text = placeholder,
              style = resolvedStyle.copy(color = scheme.onSurfaceVariant),
            )
          }
          innerTextField()
        }
      },
    )
  }
}

/** I colori che la trasformazione d'uscita e i segni accanto al testo usano. */
internal data class RichTextColors(
  val link: Color,
  val quoteText: Color,
  val quoteBar: Color,
  val marker: Color,
)

/** Ctrl+Z/Y/B/I/U e il «cancella» in testa al documento. Vero se l'ha gestito l'editor. */
internal fun FluidRichTextState.handleKeyEvent(event: KeyEvent): Boolean {
  if (event.type != KeyEventType.KeyDown) return false
  val command = event.isCtrlPressed || event.isMetaPressed
  return when {
    command && event.key == Key.Z && !event.isShiftPressed -> {
      undo()
      true
    }
    command && (event.key == Key.Y || (event.key == Key.Z && event.isShiftPressed)) -> {
      redo()
      true
    }
    command && event.key == Key.B -> {
      toggle(FluidRichTextInlineStyle.Bold)
      true
    }
    command && event.key == Key.I -> {
      toggle(FluidRichTextInlineStyle.Italic)
      true
    }
    command && event.key == Key.U -> {
      toggle(FluidRichTextInlineStyle.Underline)
      true
    }
    !command && event.key == Key.Backspace -> backspaceAtStart()
    else -> false
  }
}

/**
 * Disegna gli stili del documento sul testo che il campo mostra: grassetto, corsivo, sottolineato e
 * link come stili di carattere, elenchi e citazioni come rientro di paragrafo (i segni li disegna
 * [drawParagraphDecorations] in quello spazio).
 *
 * Se testo e documento non coincidono (un istante fra due scritture) non disegna niente: un intervallo
 * fuori dal testo farebbe cadere il campo, e il fotogramma dopo e' gia' giusto.
 */
internal class RichTextOutputTransformation(
  private val state: FluidRichTextState,
  private val colors: RichTextColors,
) : OutputTransformation {
  override fun TextFieldBuffer.transformOutput() {
    val document = state.document
    val text = asCharSequence()
    if (text.length != document.text.length || !text.contentEqualsString(document.text)) return
    document.forEachRun { start, end, format ->
      if (!format.isPlain) addStyle(spanStyleFor(format, colors), start, end)
    }
    val lastIndex = document.paragraphCount - 1
    for (index in 0..lastIndex) {
      val style = document.paragraphStyles[index]
      if (style == FluidRichTextParagraphStyle.Plain) continue
      val start = document.paragraphStart(index)
      var end = if (index < lastIndex) document.paragraphEnd(index) + 1 else document.paragraphEnd(index)
      if (start == end) {
        // L'ultimo paragrafo vuoto non ha caratteri a cui dare il rientro: un carattere a larghezza
        // zero in coda (solo nel testo mostrato, mai in quello del documento o della tastiera) glielo
        // da', cosi' il cursore di una voce appena creata sta dopo il segno e non sopra.
        append(ZeroWidthSpace)
        end = start + 1
      }
      val indent = paragraphIndentEm(style)
      addStyle(ParagraphStyle(textIndent = TextIndent(firstLine = indent.em, restLine = indent.em)), start, end)
      if (style.quote && end > start) addStyle(SpanStyle(color = colors.quoteText), start, end)
    }
  }
}

private const val ZeroWidthSpace = "​"

private fun spanStyleFor(format: FluidRichTextInlineFormat, colors: RichTextColors): SpanStyle {
  val underline = format.underline || format.link != null
  return SpanStyle(
    fontWeight = if (format.bold) FontWeight.Bold else null,
    fontStyle = if (format.italic) FontStyle.Italic else null,
    textDecoration = if (underline) TextDecoration.Underline else null,
    color = if (format.link != null) colors.link else Color.Unspecified,
  )
}

/** Il rientro di un paragrafo in multipli della dimensione del testo. */
internal fun paragraphIndentEm(style: FluidRichTextParagraphStyle): Float =
  (if (style.quote) FluidRichTextEditorDefaults.QuoteIndentEm else 0f) +
    (if (style.list != null) FluidRichTextEditorDefaults.ListIndentEm else 0f)

/**
 * I segni accanto al testo: il pallino o il numero di una voce, la barra di una citazione. Stanno nel
 * rientro del paragrafo, sul lato dove comincia la riga (a destra in arabo ed ebraico).
 */
private fun DrawScope.drawParagraphDecorations(
  document: FluidRichTextDocument,
  layout: TextLayoutResult,
  textStyle: TextStyle,
  colors: RichTextColors,
  measurer: androidx.compose.ui.text.TextMeasurer,
) {
  val textLength = layout.layoutInput.text.length
  val fontSizePx = if (textStyle.fontSize.isSp) textStyle.fontSize.toPx() else FallbackFontSize.toPx()
  val width = layout.size.width.toFloat()
  for (index in 0 until document.paragraphCount) {
    val style = document.paragraphStyles[index]
    if (style == FluidRichTextParagraphStyle.Plain) continue
    val start = document.paragraphStart(index).coerceAtMost(textLength)
    val end = document.paragraphEnd(index).coerceAtMost(textLength)
    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(end)
    val rtl = layout.getParagraphDirection(start) == ResolvedTextDirection.Rtl
    val quoteIndent = if (style.quote) FluidRichTextEditorDefaults.QuoteIndentEm * fontSizePx else 0f
    if (style.quote) {
      val barWidth = QuoteBarWidth.toPx()
      val x = if (rtl) width - barWidth / 2f - QuoteBarInset.toPx() else barWidth / 2f + QuoteBarInset.toPx()
      drawLine(
        color = colors.quoteBar,
        start = Offset(x, layout.getLineTop(firstLine)),
        end = Offset(x, layout.getLineBottom(lastLine)),
        strokeWidth = barWidth,
      )
    }
    val kind = style.list ?: continue
    val marker = if (kind == FluidRichTextListKind.Bullet) "•" else "${document.listNumber(index)}."
    val measured = measurer.measure(marker, textStyle.copy(color = colors.marker, textIndent = null))
    val listIndent = FluidRichTextEditorDefaults.ListIndentEm * fontSizePx
    val gap = fontSizePx * MarkerGapEm
    val x = if (rtl) {
      width - quoteIndent - listIndent + gap
    } else {
      quoteIndent + listIndent - gap - measured.size.width
    }
    val baseline = layout.getLineBaseline(firstLine)
    drawText(measured, topLeft = Offset(x, baseline - measured.firstBaseline))
  }
}

/** Se lo stile non dice la dimensione (non succede con la tipografia del tema): quella di `bodyLarge`. */
private val FallbackFontSize = 16.sp

private val QuoteBarWidth = 3.dp
private val QuoteBarInset = 2.dp

/** L'aria fra il segno di una voce e il suo testo. */
private const val MarkerGapEm = 0.45f

private fun CharSequence.contentEqualsString(other: String): Boolean {
  for (index in indices) if (this[index] != other[index]) return false
  return true
}

/** Riallinea il documento al testo del campo, se divergono (vedi la rete di sicurezza nell'editor). */
internal fun FluidRichTextState.reconcileWithField(fieldText: String) {
  if (document.text != fieldText) forceReconcile(fieldText)
}

/**
 * Gli appunti dell'editor: uguali a quelli di sistema, ma quando il campo copia la selezione ci mettono
 * anche il suo HTML ristretto. Il testo semplice resta quello che il campo ha scelto.
 */
internal class RichTextClipboard(
  private val delegate: Clipboard,
  private val state: FluidRichTextState,
) : Clipboard {
  override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

  override suspend fun setClipEntry(clipEntry: ClipEntry?) {
    delegate.setClipEntry(enrich(clipEntry))
  }

  override val nativeClipboard: android.content.ClipboardManager get() = delegate.nativeClipboard

  /** La selezione di adesso, o l'ultima non vuota (il campo la chiude subito dopo aver copiato). */
  private fun currentOrLastSelection(): Pair<FluidRichTextDocument, TextRange>? {
    val selection = state.selection
    if (!selection.collapsed) return state.document to TextRange(selection.min, selection.max)
    return state.lastSelection
  }

  private fun enrich(entry: ClipEntry?): ClipEntry? {
    val data = entry?.clipData ?: return entry
    if (data.itemCount != 1) return entry
    val plain = data.getItemAt(0).text?.toString() ?: return entry
    val (document, range) = currentOrLastSelection() ?: return entry
    if (range.max > document.text.length || document.text.substring(range.min, range.max) != plain) return entry
    val fragment = document.slice(range.min, range.max)
    val plainOnly = fragment.spans.isEmpty() && fragment.paragraphStyles.all { it == FluidRichTextParagraphStyle.Plain }
    if (plainOnly) return entry
    val label = data.description?.label ?: ""
    return ClipEntry(ClipData.newHtmlText(label, plain, FluidRichTextHtml.write(fragment)))
  }
}

/**
 * Riceve cio' che si incolla: HTML o testo con stili diventano un frammento formattato, il testo semplice
 * entra col formato del punto. Cio' che non e' testo (un'immagine dalla tastiera) torna indietro, e il
 * campo lo ignora.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class RichTextPasteReceiver(private val state: FluidRichTextState) : ReceiveContentListener {
  override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
    if (!state.isEditable) return transferableContent
    // Solo gli appunti: un testo trascinato deve cadere dove lo si lascia, e quello lo fa il campo
    // (come testo scritto, col formato del punto); un'immagine dalla tastiera non e' testo.
    if (transferableContent.source != TransferableContent.Source.Clipboard) return transferableContent
    if (!transferableContent.hasMediaType(MediaType.Text) && !transferableContent.hasMediaType(MediaType.HtmlText)) {
      return transferableContent
    }
    val data = transferableContent.clipEntry.clipData
    for (index in 0 until data.itemCount) {
      val item = data.getItemAt(index)
      val html = item.htmlText
      val text = item.text
      when {
        !html.isNullOrEmpty() -> {
          state.paste(FluidRichTextHtml.read(html))
          return null
        }
        text is Spanned && text.hasSupportedSpans() -> {
          state.paste(FluidRichTextHtml.read(Html.toHtml(text, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)))
          return null
        }
        !text.isNullOrEmpty() -> {
          state.pastePlain(text.toString())
          return null
        }
      }
    }
    return transferableContent
  }
}

private fun Spanned.hasSupportedSpans(): Boolean =
  getSpans(0, length, android.text.style.StyleSpan::class.java).isNotEmpty() ||
    getSpans(0, length, android.text.style.UnderlineSpan::class.java).isNotEmpty() ||
    getSpans(0, length, android.text.style.URLSpan::class.java).isNotEmpty() ||
    getSpans(0, length, android.text.style.QuoteSpan::class.java).isNotEmpty() ||
    getSpans(0, length, android.text.style.BulletSpan::class.java).isNotEmpty()
