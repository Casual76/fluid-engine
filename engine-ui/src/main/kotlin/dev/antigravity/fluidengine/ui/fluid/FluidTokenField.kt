package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Gettone di dominio neutro. L'app assegna ID univoci e decide validità e normalizzazione. */
data class FluidToken(val id: String, val label: String, val isError: Boolean = false) {
  init { require(id.isNotEmpty()) { "FluidToken richiede un ID non vuoto" } }
}

/** Bozza completa e selezione a due tempi; composizione IME transiente, mai ripristinata dopo morte processo. */
data class FluidTokenFieldValue(
  val tokens: List<FluidToken> = emptyList(),
  val draft: TextFieldValue = TextFieldValue(),
  val selectedTokenId: String? = null,
) {
  companion object {
    /** Per rememberSaveable(stateSaver = Saver): salva anche cursore, errori e selezione del gettone. */
    val Saver = listSaver<FluidTokenFieldValue, Any>(
      save = { value -> listOf(value.draft.text, value.draft.selection.start, value.draft.selection.end,
        value.selectedTokenId.orEmpty()) + value.tokens.flatMap { listOf(it.id, it.label, it.isError) } },
      restore = { saved -> FluidTokenFieldValue(
        tokens = saved.drop(4).chunked(3).map { FluidToken(it[0] as String, it[1] as String, it[2] as Boolean) },
        draft = TextFieldValue(saved[0] as String, TextRange(saved[1] as Int, saved[2] as Int)),
        selectedTokenId = (saved[3] as String).takeIf { it.isNotEmpty() },
      ) },
    )
  }
}

/** Operazioni pure: nessuna regex di dominio. Un risultato null del creatore conserva il testo rifiutato. */
object FluidTokenFieldDefaults {
  /** Separazione predefinita; aggiungere lo spazio soltanto per domini che non hanno nomi composti. */
  val Separators: Set<Char> = setOf(',', ';', '\n')

  /**
   * Conferma soltanto porzioni terminate da separatore, mai durante composizione.
   * commitRemainder serve per Invio, perdita focus o incolla esplicito di una lista completa.
   * createToken vede anche i gettoni appena aggiunti: la deduplica è interamente dell'app.
   * null conserva la porzione originale (separatore incluso), senza perdere dati non riconosciuti.
   */
  fun update(
    value: FluidTokenFieldValue,
    draft: TextFieldValue,
    separators: Set<Char> = Separators,
    commitRemainder: Boolean = false,
    createToken: (String, List<FluidToken>) -> FluidToken?,
  ): FluidTokenFieldValue {
    if (draft.composition != null) return value.copy(draft = draft, selectedTokenId = null)
    val tokens = value.tokens.toMutableList()
    val retained = StringBuilder()
    var start = 0
    var consumed = false
    fun accept(end: Int, next: Int) {
      val raw = draft.text.substring(start, end)
      if (raw.isNotBlank()) {
        val token = createToken(raw.trim(), tokens.toList())
        if (token != null && tokens.none { it.id == token.id }) {
          tokens += token
          consumed = true
        } else retained.append(draft.text.substring(start, next))
      } else consumed = true
      start = next
    }
    draft.text.forEachIndexed { index, char -> if (char in separators) accept(index, index + 1) }
    if (commitRemainder) accept(draft.text.length, draft.text.length)
    retained.append(draft.text.substring(start))
    return value.copy(tokens = tokens, draft = if (consumed) TextFieldValue(retained.toString(),
      TextRange(retained.length)) else draft, selectedTokenId = null)
  }

  /** Prima pressione seleziona, seconda rimuove. Testo o composizione presenti lasciano agire l'editor. */
  fun backspace(value: FluidTokenFieldValue): FluidTokenFieldValue {
    if (value.draft.text.isNotEmpty() || value.draft.composition != null) return value
    val last = value.tokens.lastOrNull() ?: return value.copy(selectedTokenId = null)
    return if (value.selectedTokenId == last.id) value.copy(tokens = value.tokens.dropLast(1), selectedTokenId = null)
      else value.copy(selectedTokenId = last.id)
  }
}

/** Suggerimento controllato dall'app: ID stabile per evitare che un aggiornamento scelga un'altra riga. */
data class FluidTokenSuggestion(
  val id: String,
  val title: String,
  val subtitle: String? = null,
  val leading: (@Composable () -> Unit)? = null,
)

/**
 * Campo a gettoni che cresce nella pagina: inserirlo nello scroll del chiamante, senza limite verticale interno.
 * createToken valida, normalizza e deduplica (null conserva il testo); un gettone non valido usa isError.
 * onEditToken è opzionale: l'app decide se rimuovere il gettone e mettere la sua etichetta nella bozza.
 * I suggerimenti sono dati della query corrente: l'app scarta risposte obsolete e gestisce caricamento/errore.
 * Le frecce su/giù scelgono un suggerimento, Invio lo accetta senza spostare il focus dall'input.
 * Una sentinella invisibile consente di osservare deleteSurroundingText della tastiera a input vuoto.
 * Separatori sintattici dentro nomi composti richiedono una policy/parsing dell'app prima dell'inserimento.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FluidTokenField(
  value: FluidTokenFieldValue,
  onValueChange: (FluidTokenFieldValue) -> Unit,
  createToken: (String, List<FluidToken>) -> FluidToken?,
  modifier: Modifier = Modifier,
  label: String? = null,
  placeholder: String? = null,
  separators: Set<Char> = FluidTokenFieldDefaults.Separators,
  enabled: Boolean = true,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  onEditToken: ((FluidToken) -> Unit)? = null,
  suggestions: List<FluidTokenSuggestion> = emptyList(),
  onSuggestionClick: (FluidTokenSuggestion) -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  val labels = LocalFluidLabels.current
  val inputFocus = remember { FocusRequester() }
  var focused by remember { mutableStateOf(false) }
  var activeSuggestion by remember { mutableStateOf<String?>(null) }
  fun commit() = onValueChange(FluidTokenFieldDefaults.update(value,
    value.draft, separators, true, createToken))
  val well = if (LocalFluidCanvasIsGlass.current) lerp(GlassDefaults.glassFilm(), colors.onSurface, .18f)
    else colors.onSurface.copy(alpha = .06f)
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
    FlowRow(Modifier.fillMaxWidth().clip(ContinuousCornerShape(FluidRadius.Control)).background(well).padding(8.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      value.tokens.forEach { token ->
        key(token.id) {
          val selected = value.selectedTokenId == token.id
          val background = when { token.isError -> colors.errorContainer; selected -> colors.primaryContainer; else -> colors.surfaceContainerHighest }
          val foreground = when { token.isError -> colors.onErrorContainer; selected -> colors.onPrimaryContainer; else -> colors.onSurface }
          Row(Modifier.widthIn(max = 280.dp).heightIn(min = 48.dp)
            .clip(ContinuousCornerShape(FluidRadius.Control)).background(background)
            .then(if (selected) Modifier.border(1.5.dp, foreground, ContinuousCornerShape(FluidRadius.Control)) else Modifier)
            .semantics(mergeDescendants = true) {
              contentDescription = token.label
              this.selected = selected
              if (token.isError) { error(labels.tokenInvalid); stateDescription = labels.tokenInvalid }
              if (!enabled) disabled()
              if (enabled) customActions = (if (onEditToken != null) listOf(CustomAccessibilityAction(labels.tokenEdit) { onEditToken(token); inputFocus.requestFocus(); true }) else emptyList()) + listOf(CustomAccessibilityAction(labels.tokenRemove) {
                onValueChange(value.copy(tokens = value.tokens.filterNot { it.id == token.id }, selectedTokenId = null)); true
              })
            }, verticalAlignment = Alignment.CenterVertically) {
            val edit = if (onEditToken != null) Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).fluidPressable(enabled = enabled,
              onClick = { onEditToken(token); inputFocus.requestFocus() }, grip = FluidPressGrip.YieldsToScroll).clearAndSetSemantics {} else Modifier
            Text(token.label, Modifier.weight(1f, fill = false).then(edit).padding(12.dp),
              color = foreground, style = MaterialTheme.typography.bodyMedium,
              maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Box(Modifier.size(48.dp).fluidPressable(enabled = enabled, onClick = {
              onValueChange(value.copy(tokens = value.tokens.filterNot { it.id == token.id }, selectedTokenId = null))
            }, grip = FluidPressGrip.YieldsToScroll).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
              Icon(Icons.Rounded.Close, null, tint = foreground, modifier = Modifier.size(18.dp))
            }
          }
        }
      }
      BasicTextField(
        value = tokenEditorValue(value.draft),
        onValueChange = { editor ->
          if (!editor.text.startsWith(TokenSentinel)) {
            if (value.draft.text.isEmpty() && editor.text.isEmpty()) onValueChange(FluidTokenFieldDefaults.backspace(value))
            else onValueChange(FluidTokenFieldDefaults.update(value, editor, separators,
              commitRemainder = !focused || (editor.text.length - value.draft.text.length > 1 && editor.text.any { it in separators }),
              createToken = createToken))
          } else {
            val draft = tokenDraftValue(editor)
            val paste = draft.text.length - value.draft.text.length > 1 && draft.text.any { it in separators }
            onValueChange(FluidTokenFieldDefaults.update(value, draft, separators, paste || !focused, createToken))
          }
          activeSuggestion = null
        },
        modifier = Modifier.widthIn(min = 80.dp).heightIn(min = 48.dp).weight(1f)
          .semantics { contentDescription = label ?: placeholder.orEmpty(); stateDescription = "${labels.tokenCount}: ${value.tokens.size}" }
          .focusRequester(inputFocus)
          .onFocusChanged { state ->
            if (focused && !state.isFocused) commit()
            focused = state.isFocused
          }.onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) false
            else when {
              event.key == Key.Backspace && value.draft.text.isEmpty() && value.draft.composition == null -> {
                onValueChange(FluidTokenFieldDefaults.backspace(value)); true
              }
              (event.key == Key.DirectionDown || event.key == Key.DirectionUp) && suggestions.isNotEmpty() -> {
                val index = suggestions.indexOfFirst { it.id == activeSuggestion }
                val next = if (event.key == Key.DirectionDown) (index + 1).mod(suggestions.size)
                  else if (index < 0) suggestions.lastIndex else (index - 1).mod(suggestions.size)
                activeSuggestion = suggestions[next].id; true
              }
              event.key == Key.Enter && value.draft.composition == null -> {
                val chosen = suggestions.firstOrNull { it.id == activeSuggestion }
                if (chosen != null) onSuggestionClick(chosen) else if ('\n' in separators) commit()
                chosen != null || '\n' in separators
              }
              else -> false
            }
          },
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        visualTransformation = TokenSentinelTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onDone = {
          val chosen = suggestions.firstOrNull { it.id == activeSuggestion }
          if (chosen != null) onSuggestionClick(chosen) else commit()
        }),
        decorationBox = { inner -> Box(Modifier.padding(vertical = 12.dp)) {
          if (value.draft.text.isEmpty()) placeholder?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant) }
          inner()
        } },
      )
    }
    if (focused && suggestions.isNotEmpty()) {
      FluidListGroup {
        suggestions.forEach { suggestion ->
          key(suggestion.id) { FluidSuggestionRow(suggestion.title, suggestion.subtitle, suggestion.leading,
            onClick = { onSuggestionClick(suggestion); activeSuggestion = null }, enabled = enabled,
            selected = suggestion.id == activeSuggestion) }
        }
      }
    }
  }
}

/** Riga in pagina, un nodo accessibile; non prende focus alla tastiera mentre si sceglie un suggerimento. */
@Composable
fun FluidSuggestionRow(
  title: String,
  subtitle: String? = null,
  leading: (@Composable () -> Unit)? = null,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  selected: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  Row(modifier.fillMaxWidth().heightIn(min = 48.dp).focusProperties { canFocus = false }
    .semantics(mergeDescendants = true) { this.selected = selected }
    .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
    .fluidPressable(onClick = onClick, enabled = enabled, role = Role.Button, grip = FluidPressGrip.YieldsToScroll)
    .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    leading?.invoke()
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
      subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
    }
  }
}

// Il buffer IME contiene un carattere di guardia, il modello pubblico e il testo disegnato no. EditableText espone la guardia al servizio di accessibilit?; collaudare TalkBack.
internal const val TokenSentinel = "\u200B"
internal fun tokenEditorValue(draft: TextFieldValue) = TextFieldValue(TokenSentinel + draft.text,
  TextRange(draft.selection.start + 1, draft.selection.end + 1),
  draft.composition?.let { TextRange(it.start + 1, it.end + 1) })
internal fun tokenDraftValue(editor: TextFieldValue) = TextFieldValue(editor.text.drop(1),
  TextRange((editor.selection.start - 1).coerceAtLeast(0), (editor.selection.end - 1).coerceAtLeast(0)),
  editor.composition?.let { TextRange((it.start - 1).coerceAtLeast(0), (it.end - 1).coerceAtLeast(0)) })
private val TokenSentinelTransformation = VisualTransformation { text ->
  TransformedText(AnnotatedString(text.text.drop(1)), object : OffsetMapping {
    override fun originalToTransformed(offset: Int) = (offset - 1).coerceAtLeast(0)
    override fun transformedToOriginal(offset: Int) = offset + 1
  })
}
