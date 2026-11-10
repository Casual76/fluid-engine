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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlinx.coroutines.flow.collectLatest

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

  /**
   * Il rientro piu' grande che si vede, in frazione della larghezza del campo: quando il paragrafo piu'
   * profondo andrebbe oltre (livelli profondi, testo al 200%, telefono stretto), tutti i rientri del
   * documento si stringono della stessa misura, cosi' i livelli restano a passi uguali e distinguibili e
   * le barre delle citazioni in colonna; il posto del segno della voce resta intero. Senza, sei livelli
   * col testo grande lascerebbero al testo una colonna di poche lettere.
   */
  const val MaxIndentFraction: Float = 0.4f
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
 * **Elenchi e citazioni annidati.** Ogni paragrafo ha un livello di rientro (vedi
 * [FluidRichTextParagraphStyle]): i tasti riduci/aumenta rientro della barra, Tab e Maiusc+Tab, l'a capo
 * su una voce vuota e il «cancella» in testa a una voce (che risalgono di un livello alla volta prima di
 * uscire dall'elenco). I puntati cambiano segno per livello (•, ◦, ▪), i numeri ripartono in ogni
 * sotto-elenco, ogni citazione annidata ha la sua barra; il rientro che si vede ha un tetto
 * ([FluidRichTextEditorDefaults.MaxIndentFraction]).
 *
 * **Scorciatoie** da tastiera fisica: Ctrl+Z / Ctrl+Maiusc+Z / Ctrl+Y annulla e ripete (la cronologia e'
 * quella dell'editor, con gli stili), Ctrl+B/I/U, Tab / Maiusc+Tab su voci e citazioni (altrove il Tab fa
 * quel che fa il campo).
 *
 * **TalkBack** legge il testo; lo stato del campo dice cosa e' acceso al cursore («Grassetto, elenco
 * puntato, livello 2»), con le parole di [LocalFluidRichTextLabels].
 *
 * Non ha un pozzo ne' un bordo: occupa la larghezza che riceve e cresce col testo. Il contenitore (una
 * pagina che scorre, una card) e' dell'app. Dentro uno spazio di altezza fissa scorre da se'.
 *
 * **La riga che si scrive sta sopra la barra.** Il campo porta il cursore in vista come ogni campo di testo:
 * sopra il fondo di chi scorre, cioe' sopra la tastiera se la pagina finisce li'. La barra pero' galleggia
 * proprio sopra quel fondo. Mentre la barra e' in scena ([FluidRichTextState.isFormattingBarVisible], con
 * una [FluidRichTextToolbar] che ha lo stesso [state]) l'editor chiede a chi scorre di portare in vista il
 * cursore **piu' lo spazio che la barra occupa**: la riga si ferma appena sopra la barra, col testo al 100%
 * come al 200%, con e senza tastiera. Lo fa solo se la riga e' coperta. Vicino alla fine del testo quello
 * spazio sotto la riga non c'e': finche' la barra e' in scena il campo si tiene sotto l'ultima riga **una
 * coda vuota** alta quanto la barra occupa (il campo e' piu' alto di cosi', o piu' corto se ha un'altezza
 * fissa), e la restituisce quando la barra esce. Senza barra non cambia niente.
 *
 * Cosa deve fare la pagina: **scorrere** (l'editor sposta la riga solo chiedendolo a chi scorre) e **finire
 * sopra la tastiera** (`Modifier.imePadding()` sulla pagina che contiene contenuto e overlay). Una pagina a
 * tutto schermo che resta sotto la tastiera non porta il cursore sopra la tastiera nemmeno senza barra:
 * Android sposta l'intera finestra, e la barra con lei.
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
  val indentLimit = remember(state) { RichTextIndentLimit() }
  val fontSizePx = with(LocalDensity.current) {
    (if (resolvedStyle.fontSize.isSp) resolvedStyle.fontSize else FallbackFontSize).toPx()
  }
  SideEffect { indentLimit.fontSizePx = fontSizePx }
  val output = remember(state, colors, indentLimit) { RichTextOutputTransformation(state, colors, indentLimit) }
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

  // La riga del cursore sopra la barra di formattazione. Il campo porta gia' il cursore in vista da solo,
  // cioe' sopra il fondo di chi scorre (la tastiera); la barra pero' galleggia proprio li' sopra, e la
  // pagina non le lascia uno spazio suo. Con la barra in scena si chiede di portare in vista il cursore
  // **piu' lo spazio che la barra occupa** ([CaretBarAvoidance]), e solo se la riga e' coperta. Vicino
  // alla fine del testo quello spazio non c'e' (sotto l'ultima riga il campo finisce): il campo se lo tiene
  // come coda vuota finche' la barra e' in scena. Senza barra non si chiede niente e non c'e' coda.
  val bringIntoView = remember { BringIntoViewRequester() }
  val placement = remember { FieldPlacement() }
  val ime = WindowInsets.ime
  val density = LocalDensity.current
  val barGapPx = with(density) { CaretBarGap.toPx() }
  LaunchedEffect(state, ime, density, barGapPx) {
    var previous: CaretTrigger? = null
    snapshotFlow {
      // La tastiera che sale o scende sposta la barra: finche' si muove, la riga la segue.
      if (state.isFormattingBarVisible) CaretTrigger(state.selection, state.revision, ime.getBottom(density)) else null
    }.collectLatest { trigger ->
      val before = previous
      previous = trigger
      if (trigger == null) {
        // La barra non c'e' piu': la coda tenuta per lei torna indietro.
        placement.tail = 0
        placement.probe = 0
        return@collectLatest
      }
      // Un giro interrotto a meta' (un altro tasto, la tastiera che si muove) puo' aver lasciato la coda
      // presa solo per misurare: torna indietro prima di rimisurare.
      if (placement.probe > 0) {
        placement.tail = (placement.tail - placement.probe).coerceAtLeast(0)
        placement.probe = 0
      }
      val offset = movedSelectionOffset(before?.selection?.start, before?.selection?.end, trigger.selection.start, trigger.selection.end)
      val avoidance = CaretBarAvoidance()
      while (true) {
        // Due fotogrammi: il primo puo' essere quello che sta ancora componendo la modifica; dopo il
        // secondo il testo, la barra e lo scorrimento appena fatto sono nel layout che si misura.
        repeat(2) { withFrameNanos { } }
        val coordinates = placement.coordinates?.takeIf { it.isAttached } ?: break
        val layout = layoutGetter?.invoke() ?: break
        val caret = layout
          .getCursorRect(offset.coerceIn(0, layout.layoutInput.text.length))
          .translate(0f, -scroll.value.toFloat())
        val fieldBottom = coordinates.localToRoot(Offset(0f, coordinates.size.height.toFloat())).y + placement.tail
        val cutBottom = coordinates.cutBottomInRoot()
        val step = avoidance.next(
          CaretBarMeasure(
            caretBottom = coordinates.localToRoot(Offset(0f, caret.bottom)).y,
            caretHeight = caret.height,
            fieldBottom = fieldBottom,
            barTop = state.formattingBarTop,
            visibleFieldTop = coordinates.boundsInRoot().top,
            scrollBottom = cutBottom ?: fieldBottom,
            cut = cutBottom != null,
            windowHeight = coordinates.findRootCoordinates().size.height.toFloat(),
            gap = barGapPx,
          ),
        )
        when (step) {
          CaretBarStep.Done -> break
          is CaretBarStep.Bring ->
            bringIntoView.bringIntoView(Rect(caret.left, caret.top, caret.right, caret.bottom + step.allowance))
          // Sotto la riga non c'e' abbastanza campo: se ne tiene la coda che manca (o restituisce quella
          // presa in eccesso), e al giro dopo si rimisura.
          is CaretBarStep.Reserve -> {
            val tail = (placement.tail + ceil(step.space).toInt())
              .coerceIn(0, coordinates.findRootCoordinates().size.height)
            if (step.probe) placement.probe = if (step.space > 0f) tail - placement.tail else 0
            placement.tail = tail
          }
        }
      }
    }
  }

  val description = state.format.describe(labels)
  CompositionLocalProvider(LocalClipboard provides richClipboard) {
    BasicTextField(
      state = state.textFieldState,
      modifier = modifier
        .fillMaxWidth()
        // La coda sotto l'ultima riga, per stare sopra la barra (zero senza barra, e finche' la riga non
        // e' coperta vicino alla fine): il campo occupa quei pixel in piu', oppure, se la sua altezza e'
        // fissa, si accorcia di altrettanto.
        .layout { measurable, constraints ->
          val tail = placement.tail
          val placeable = measurable.measure(constraints.offset(vertical = -tail))
          layout(placeable.width, constraints.constrainHeight(placeable.height + tail)) { placeable.place(0, 0) }
        }
        .bringIntoViewRequester(bringIntoView)
        .onPlaced { placement.coordinates = it }
        .focusRequester(state.focusRequester)
        .onSizeChanged { indentLimit.widthPx = it.width.toFloat() }
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
                drawParagraphDecorations(state.document, layout, resolvedStyle, colors, measurer, indentLimit.capEm)
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

/** L'aria fra la riga del cursore e il bordo alto della barra di formattazione. */
private val CaretBarGap = 8.dp

/**
 * Dove sta il campo: le coordinate del layout, lette quando serve misurare (non sono stato di Compose), e
 * la coda vuota che si tiene sotto l'ultima riga mentre la barra e' in scena ([CaretBarStep.Reserve]), in
 * pixel: stato, perche' cambiarla rifa' il layout. Cresce quando serve e torna a zero quando la barra esce.
 */
private class FieldPlacement {
  var coordinates: LayoutCoordinates? = null
  var tail: Int by mutableIntStateOf(0)

  /** Quanta della coda e' stata presa solo per misurare e va restituita (zero a giro finito). */
  var probe: Int = 0
}

/**
 * Il fondo di chi scorre per primo attorno al campo, nelle coordinate della radice: risalendo dal campo, il
 * bordo basso del primo pezzo di layout che e' **tagliato in basso** da chi lo contiene (il contenuto di
 * una pagina che scorre continua sotto il bordo della pagina: il taglio e' quel bordo). Chi scorre non ha
 * un'API per farsi trovare da un figlio, ma il suo ritaglio si vede. Null se niente e' tagliato.
 */
private fun LayoutCoordinates.cutBottomInRoot(): Float? {
  var node: LayoutCoordinates? = this
  while (node != null && node.isAttached) {
    val visible = node.boundsInRoot()
    // Un pezzo del tutto fuori vista non dice dov'e' il bordo: lo dice chi lo contiene.
    if (visible.height > 0f) {
      val bottom = node.localToRoot(Offset(0f, node.size.height.toFloat())).y
      if (bottom > visible.bottom + CaretBarAvoidance.Epsilon) return visible.bottom
    }
    node = node.parentCoordinates
  }
  return null
}

/** Cio' che fa ricontrollare la riga del cursore mentre la barra e' in scena. */
private data class CaretTrigger(val selection: TextRange, val revision: Long, val imeBottom: Int)

/** I colori che la trasformazione d'uscita e i segni accanto al testo usano. */
internal data class RichTextColors(
  val link: Color,
  val quoteText: Color,
  val quoteBar: Color,
  val marker: Color,
)

/**
 * Ctrl+Z/Y/B/I/U, Tab e Maiusc+Tab sul rientro, e il «cancella» in testa al documento. Vero se l'ha
 * gestito l'editor. Il Tab lo prende solo dove cambia un rientro: in un paragrafo normale resta del campo.
 */
internal fun FluidRichTextState.handleKeyEvent(event: KeyEvent): Boolean {
  if (event.type != KeyEventType.KeyDown) return false
  val command = event.isCtrlPressed || event.isMetaPressed
  return when {
    !command && event.key == Key.Tab && !event.isShiftPressed && format.canIncreaseIndent -> {
      increaseIndent()
      true
    }
    !command && event.key == Key.Tab && event.isShiftPressed && format.canDecreaseIndent -> {
      decreaseIndent()
      true
    }
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
  private val indentLimit: RichTextIndentLimit,
) : OutputTransformation {
  override fun TextFieldBuffer.transformOutput() {
    val document = state.document
    val text = asCharSequence()
    if (text.length != document.text.length || !text.contentEqualsString(document.text)) return
    document.forEachRun { start, end, format ->
      if (!format.isPlain) addStyle(spanStyleFor(format, colors), start, end)
    }
    val lastIndex = document.paragraphCount - 1
    val scale = indentScale(document, indentLimit.capEm)
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
      val indent = containerOffsetsEm(style, scale)[style.depth]
      addStyle(ParagraphStyle(textIndent = TextIndent(firstLine = indent.em, restLine = indent.em)), start, end)
      if (style.quote && end > start) addStyle(SpanStyle(color = colors.quoteText), start, end)
    }
  }
}

private const val ZeroWidthSpace = "\u200B"

private fun spanStyleFor(format: FluidRichTextInlineFormat, colors: RichTextColors): SpanStyle {
  val underline = format.underline || format.link != null
  return SpanStyle(
    fontWeight = if (format.bold) FontWeight.Bold else null,
    fontStyle = if (format.italic) FontStyle.Italic else null,
    textDecoration = if (underline) TextDecoration.Underline else null,
    color = if (format.link != null) colors.link else Color.Unspecified,
  )
}

/**
 * Il tetto del rientro che si vede: la larghezza del campo (dal layout) e la dimensione del testo (dallo
 * stile, scala del sistema compresa) sono stato, cosi' la trasformazione d'uscita e i segni si
 * ricalcolano quando cambiano. Prima della prima misura non c'e' tetto.
 */
internal class RichTextIndentLimit {
  var widthPx: Float by mutableFloatStateOf(0f)
  var fontSizePx: Float by mutableFloatStateOf(0f)

  /** Il rientro massimo in em; infinito finche' il campo non e' misurato. */
  val capEm: Float
    get() {
      val width = widthPx
      val font = fontSizePx
      if (width <= 0f || font <= 0f) return Float.POSITIVE_INFINITY
      return width * FluidRichTextEditorDefaults.MaxIndentFraction / font
    }
}

/**
 * Dove comincia ogni contenitore di un paragrafo e, in fondo, dove comincia il testo, in em dal bordo da
 * cui parte la riga: `depth + 1` valori. Una citazione occupa [FluidRichTextEditorDefaults.QuoteIndentEm],
 * un elenco [FluidRichTextEditorDefaults.ListIndentEm].
 *
 * Gli inizi dei contenitori sono moltiplicati per [scale] (vedi [indentScale]); il contenitore piu'
 * interno (il posto del segno della voce, o la barra con la sua aria) resta sempre intero.
 */
internal fun containerOffsetsEm(style: FluidRichTextParagraphStyle, scale: Float): FloatArray {
  val blocks = style.blocks
  val depth = blocks.size
  val offsets = FloatArray(depth + 1)
  if (depth == 0) return offsets
  var raw = 0f
  for (level in 0 until depth) {
    offsets[level] = raw * scale
    raw += blockIndentEm(blocks[level])
  }
  offsets[depth] = offsets[depth - 1] + blockIndentEm(blocks[depth - 1])
  return offsets
}

/**
 * Quanto stringere i rientri di [document] perche' nessun testo cominci oltre [capEm]: 1 se ci stanno,
 * altrimenti la stessa scala per **tutto** il documento, presa dal contenitore piu' profondo. Una scala
 * sola tiene i passi fra i livelli uguali e distinguibili (una curva per livello schiaccerebbe gli ultimi
 * fino a confonderli) e le barre di una citazione in colonna fra paragrafi di profondita' diverse; il
 * prezzo e' che portare una voce oltre il tetto stringe un po' tutti i rientri del documento, che col
 * testo normale su un telefono non succede prima del sesto livello.
 */
internal fun indentScale(document: FluidRichTextDocument, capEm: Float): Float {
  if (capEm.isInfinite()) return 1f
  // Il segno di una voce occupa al piu' un rientro d'elenco: gli inizi stanno sotto il tetto meno quello.
  val startCap = (capEm - FluidRichTextEditorDefaults.ListIndentEm).coerceAtLeast(0f)
  var deepest = 0f
  for (style in document.paragraphStyles) {
    val blocks = style.blocks
    if (blocks.size < 2) continue
    var start = 0f
    for (level in 0 until blocks.size - 1) start += blockIndentEm(blocks[level])
    if (start > deepest) deepest = start
  }
  return if (deepest <= startCap) 1f else startCap / deepest
}

private fun blockIndentEm(block: FluidRichTextBlock): Float =
  if (block == FluidRichTextBlock.Quote) FluidRichTextEditorDefaults.QuoteIndentEm else FluidRichTextEditorDefaults.ListIndentEm

/**
 * I segni accanto al testo: il segno o il numero di una voce (il segno dei puntati cambia per livello),
 * una barra per ogni citazione in cui sta il paragrafo. Stanno nel rientro del paragrafo, ognuno all'inizio
 * del suo contenitore, sul lato dove comincia la riga (a destra in arabo ed ebraico).
 */
private fun DrawScope.drawParagraphDecorations(
  document: FluidRichTextDocument,
  layout: TextLayoutResult,
  textStyle: TextStyle,
  colors: RichTextColors,
  measurer: androidx.compose.ui.text.TextMeasurer,
  capEm: Float,
) {
  val textLength = layout.layoutInput.text.length
  val fontSizePx = if (textStyle.fontSize.isSp) textStyle.fontSize.toPx() else FallbackFontSize.toPx()
  val width = layout.size.width.toFloat()
  val barWidth = QuoteBarWidth.toPx()
  val barInset = QuoteBarInset.toPx()
  val scale = indentScale(document, capEm)
  for (index in 0 until document.paragraphCount) {
    val style = document.paragraphStyles[index]
    if (style.depth == 0) continue
    val start = document.paragraphStart(index).coerceAtMost(textLength)
    val end = document.paragraphEnd(index).coerceAtMost(textLength)
    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(end)
    val rtl = layout.getParagraphDirection(start) == ResolvedTextDirection.Rtl
    val offsets = containerOffsetsEm(style, scale)
    style.blocks.forEachIndexed { level, block ->
      if (block != FluidRichTextBlock.Quote) return@forEachIndexed
      val fromEdge = offsets[level] * fontSizePx + barWidth / 2f + barInset
      val x = if (rtl) width - fromEdge else fromEdge
      drawLine(
        color = colors.quoteBar,
        start = Offset(x, layout.getLineTop(firstLine)),
        end = Offset(x, layout.getLineBottom(lastLine)),
        strokeWidth = barWidth,
      )
    }
    val kind = style.list ?: continue
    val marker = if (kind == FluidRichTextListKind.Bullet) bulletMarker(style.listLevel) else "${document.listNumber(index)}."
    val measured = measurer.measure(marker, textStyle.copy(color = colors.marker, textIndent = null))
    val textStart = offsets[style.depth] * fontSizePx
    val gap = fontSizePx * MarkerGapEm
    val x = if (rtl) width - textStart + gap else textStart - gap - measured.size.width
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
