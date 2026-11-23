package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.toolbar.ToolbarSearchTiming
import dev.antigravity.fluidengine.ui.fluid.toolbar.toolbarSearchPillsEntry

/**
 * I gettoni nei campi **fuori dalla barra** (F6B.15, scelta di Alessio del 23 novembre 2026, pomeriggio: «i gettoni
 * anche negli altri campi»): il campo sotto il titolo (`FluidSearchPlacement.Principal`) e [FluidSearchField] nella
 * pagina. Lo stesso modello del campo della barra (F6B.8b: `tokens`, `onTokensChange`, i gettoni suggeriti `filters`, le
 * regole di [FluidSearchTokenRules]) e lo stesso viaggio: la pillola scelta vola nel campo e diventa gettone, il gettone
 * tolto torna pillola al suo posto ([ToolbarSearchTokenStage], [ToolbarSearchTokenFlights]). La fila delle pillole sta
 * **sotto** il campo, a [FluidSearchTokenDefaults.PillsToField], ed entra ed esce col campo che si apre
 * (`toolbarSearchPillsEntry`, la curva di [ToolbarSearchTiming]).
 *
 * [stage] è il palco del viaggio (le coordinate sono quelle della cornice, [FluidSearchFieldTokensFrame]); [pillsIn] quanto
 * la fila è entrata.
 */
@Stable
internal class FluidSearchFieldTokens(
  val stage: ToolbarSearchTokenStage,
  val pillsIn: Animatable<Float, AnimationVector1D>,
) {
  /** La fila si vede (entra, sta, o esce). */
  val pillsShown: Boolean get() = pillsIn.value > 0f || pillsWanted

  internal var pillsWanted by mutableStateOf(false)
}

/**
 * Il palco dei gettoni di un campo fuori dalla barra (F6B.15): fa partire i viaggi quando [spec] cambia i gettoni, prima
 * di comporre il campo e la fila (come `FluidToolbarSearchOverlay`), e fa entrare la fila quando il campo è [open].
 */
@Composable
internal fun rememberFluidSearchFieldTokens(spec: FluidSearchSpec, open: Boolean): FluidSearchFieldTokens {
  val stage = rememberToolbarSearchTokenStage()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val heightPx = with(LocalDensity.current) { FluidSearchTokenDefaults.Height.toPx() }
  stage.sync(spec.tokens, spec.filters, animate = open && !reduced, heightPx)
  val wanted = open && (FluidSearchTokenRules.pills(spec.filters, spec.tokens).isNotEmpty() || stage.flights.isNotEmpty())
  val pillsIn = remember { Animatable(if (wanted) 1f else 0f) }
  val tokens = remember(stage, pillsIn) { FluidSearchFieldTokens(stage, pillsIn) }
  SideEffect { tokens.pillsWanted = wanted }
  LaunchedEffect(wanted, reduced) {
    val to = if (wanted) 1f else 0f
    if (reduced) pillsIn.snapTo(to) else pillsIn.animateTo(to, ToolbarSearchTiming)
  }
  return tokens
}

/**
 * La cornice di un campo coi gettoni fuori dalla barra (F6B.15): il campo ([field]), sotto la fila delle pillole e
 * [below] (i suggerimenti), e sopra tutto i disegni in volo. Le coordinate del viaggio sono le sue: pillola e gettone
 * stanno tutti e due qui dentro.
 */
@Composable
internal fun FluidSearchFieldTokensFrame(
  tokens: FluidSearchFieldTokens,
  spec: FluidSearchSpec,
  modifier: Modifier = Modifier,
  below: @Composable () -> Unit = {},
  field: @Composable () -> Unit,
) {
  Box(modifier.onPlaced { tokens.stage.root = it }) {
    Column(Modifier.fillMaxWidth()) {
      field()
      // Larga quanto il campo e allineata all'inizio: la fila che si richiude non si ricentra (come nella barra).
      if (tokens.pillsShown && spec.filters != null) {
        ToolbarSearchTokenPills(tokens.stage, spec, Modifier.fillMaxWidth()
          .padding(top = FluidSearchTokenDefaults.PillsToField)
          .toolbarSearchPillsEntry { tokens.pillsIn.value })
      }
      below()
    }
    ToolbarSearchTokenFlights(tokens.stage, fluidControlGlassBackdrop())
  }
}

/**
 * Lo spazio dei gettoni in un campo largo [fieldWidthDp] (F6B.15), in dp: il campo meno la lente ([leadingDp]), i comandi
 * ([trailingDp]) e il testo che deve restare ([FluidSearchTokenDefaults.MinTextRoom]). Pura, come
 * `toolbarSearchTokenRoomDp` della barra.
 */
internal fun fluidSearchFieldTokenRoomDp(fieldWidthDp: Float, leadingDp: Float, trailingDp: Float): Float =
  (fieldWidthDp - leadingDp - trailingDp - FluidSearchTokenDefaults.MinTextRoom.value).coerceAtLeast(0f)

/**
 * L'editor di un campo coi gettoni (F6B.15), lo stesso della barra (F6B.8b): con dei gettoni il buffer della tastiera ha
 * il carattere di guardia all'inizio, e cancellarlo è il «cancella» a testo vuoto (il primo colpo sceglie l'ultimo
 * gettone, il secondo lo toglie: [FluidSearchTokenRules.backspace]); la tastiera fisica passa da `onPreviewKeyEvent`.
 * Senza gettoni è un campo di testo qualunque.
 */
@Composable
internal fun FluidSearchTokenEditor(
  spec: FluidSearchSpec,
  stage: ToolbarSearchTokenStage,
  textStyle: TextStyle,
  cursor: Color,
  modifier: Modifier,
  keyboardOptions: KeyboardOptions,
  keyboardActions: KeyboardActions,
  singleLine: Boolean = true,
  maxLines: Int = 1,
  decorationBox: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
  var editor by remember { mutableStateOf(TextFieldValue(spec.query, TextRange(spec.query.length))) }
  if (editor.text != spec.query) SideEffect { editor = TextFieldValue(spec.query, TextRange(spec.query.length)) }
  val withTokens = spec.tokens.isNotEmpty()
  val backspace = {
    val result = FluidSearchTokenRules.backspace(spec.tokens, stage.selected)
    stage.selected = result.selected
    if (result.changed) spec.onTokensChange?.invoke(result.tokens)
  }
  BasicTextField(
    value = if (withTokens) tokenEditorValue(editor) else editor,
    onValueChange = { raw ->
      val next = if (!withTokens) raw else if (raw.text.startsWith(TokenSentinel)) tokenDraftValue(raw) else {
        backspace()
        raw
      }
      val changed = next.text != editor.text
      editor = next
      if (changed) {
        stage.selected = null
        spec.onQueryChange(next.text)
      }
    },
    visualTransformation = if (withTokens) SearchTokenSentinelTransformation else VisualTransformation.None,
    singleLine = singleLine,
    maxLines = maxLines,
    textStyle = textStyle,
    cursorBrush = SolidColor(cursor),
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    modifier = modifier.then(if (withTokens) Modifier.onPreviewKeyEvent { event ->
      val start = editor.selection.let { it.collapsed && it.start == 0 }
      if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && start && editor.composition == null) {
        backspace(); true
      } else false
    } else Modifier),
    decorationBox = decorationBox,
  )
}
