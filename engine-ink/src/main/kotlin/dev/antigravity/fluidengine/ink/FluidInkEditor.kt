package dev.antigravity.fluidengine.ink

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmationDialog
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidSpinner
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Redo
import dev.antigravity.fluidengine.ui.fluid.icons.Undo
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop

/** I tag dell'editor. */
object FluidInkEditorTags {
  const val Editor = "fluid-ink:editor"
  const val Cancel = "fluid-ink:editor:chiudi"
  const val Done = "fluid-ink:editor:fine"
  const val Undo = "fluid-ink:editor:annulla"
  const val Redo = "fluid-ink:editor:ripristina"
  const val Loading = "fluid-ink:editor:apertura"
}

/**
 * L'editor del disegno a tutto schermo, come Markup di iOS 26: in alto la «x» di vetro, annulla e ripristina, il ✓ in
 * evidenza; sotto il foglio ([FluidInkCanvas]) con il foglio bianco, la foto o il PDF; in basso (o in alto) la tavolozza
 * ([FluidInkPalette]). Il foglio scorre sotto le barre di vetro.
 *
 * [onDone] arriva col ✓: l'app consegna con [FluidInkExport] (un file nuovo, dove vuole) o salva i tratti
 * ([FluidInkState.encode]); con [busy] il ✓ gira mentre lo fa. [onCancel] arriva con la «x» o col back: se il disegno e'
 * cambiato chiede prima «Eliminare le modifiche?», come Markup.
 *
 * [sheet] e' lo stato dell'apertura ([rememberFluidInkImageSheet], [rememberFluidInkPdfSheet]) o un foglio pronto.
 */
@Composable
fun FluidInkEditor(
  state: FluidInkState,
  sheet: FluidInkSheetLoad,
  onCancel: () -> Unit,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  title: String? = null,
  labels: FluidInkLabels = FluidInkLabels(),
  busy: Boolean = false,
) {
  val backdrop = rememberGlassBackdrop()
  val startRevision = remember { mutableIntStateOf(state.revision) }
  var confirm by remember { mutableStateOf(false) }
  val changed = state.revision != startRevision.intValue && !state.isEmpty
  val askCancel: () -> Unit = { if (changed) { confirm = true } else { onCancel() } }
  BackHandler(enabled = !confirm) { askCancel() }
  val barKey = remember { Any() }
  DisposableEffect(state, barKey) { onDispose { state.inkMasks.remove(barKey) } }

  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val barRoom = 60.dp
  val paletteRoom = FluidInkPaletteDefaults.Height + FluidInkPaletteDefaults.BottomMargin * 2
  Box(
    modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      .testTag(FluidInkEditorTags.Editor),
  ) {
    when (sheet) {
      is FluidInkSheetLoad.Ready -> FluidInkCanvas(
        state = state,
        sheet = sheet.sheet,
        modifier = Modifier.fillMaxSize(),
        backdrop = backdrop,
        contentPadding = PaddingValues(
          top = top + barRoom + if (state.paletteAtTop) paletteRoom else 0.dp,
          bottom = bottom + if (state.paletteAtTop) 12.dp else paletteRoom,
          start = 12.dp,
          end = 12.dp,
        ),
        labels = labels,
      )
      is FluidInkSheetLoad.Failed -> Text(
        sheet.error.message ?: sheet.error.javaClass.simpleName,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(32.dp),
      )
      FluidInkSheetLoad.Loading -> FluidSpinner(Modifier.align(Alignment.Center).testTag(FluidInkEditorTags.Loading), size = 28.dp)
    }

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
      Column(
        Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 6.dp)
          .onGloballyPositioned { state.inkMasks[barKey] = it.boundsInRoot() },
      ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          FluidGlassIconButton(onClick = askCancel, modifier = Modifier.testTag(FluidInkEditorTags.Cancel)) {
            FluidIcon(FluidIcons.Close, labels.cancel)
          }
          Spacer(Modifier.weight(1f))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluidGlassIconButton(onClick = { state.undo() }, enabled = state.canUndo, modifier = Modifier.testTag(FluidInkEditorTags.Undo)) {
              FluidIcon(FluidIcons.Undo, labels.undo)
            }
            FluidGlassIconButton(onClick = { state.redo() }, enabled = state.canRedo, modifier = Modifier.testTag(FluidInkEditorTags.Redo)) {
              FluidIcon(FluidIcons.Redo, labels.redo)
            }
          }
          Spacer(Modifier.weight(1f))
          FluidGlassIconButton(
            onClick = onDone,
            enabled = !busy && sheet is FluidInkSheetLoad.Ready,
            style = FluidGlassButtonStyle.Prominent,
            modifier = Modifier.testTag(FluidInkEditorTags.Done),
          ) {
            if (busy) FluidSpinner(size = 20.dp, color = MaterialTheme.colorScheme.onPrimary) else FluidIcon(FluidIcons.Check, labels.done)
          }
        }
        if (title != null) {
          Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).semantics { heading() },
          )
        }
      }
      Box(
        Modifier
          .align(if (state.paletteAtTop) Alignment.TopCenter else Alignment.BottomCenter)
          .then(if (state.paletteAtTop) Modifier.statusBarsPadding().padding(top = barRoom + (if (title != null) 20.dp else 0.dp)) else Modifier.navigationBarsPadding())
          .padding(horizontal = FluidInkPaletteDefaults.SideMargin, vertical = FluidInkPaletteDefaults.BottomMargin),
      ) {
        if (sheet is FluidInkSheetLoad.Ready) FluidInkPalette(state, labels = labels)
      }
    }
  }

  FluidConfirmationDialog(
    visible = confirm,
    onDismissRequest = { confirm = false },
    title = labels.discardTitle,
    actions = listOf(FluidAlertAction(labels.discard, { confirm = false; onCancel() }, FluidAlertAction.Emphasis.Destructive)),
    cancelLabel = labels.keepEditing,
  )
}

/** Un foglio gia' pronto (un foglio bianco, una foto gia' decodificata) come stato d'apertura dell'editor. */
fun FluidInkSheet.ready(): FluidInkSheetLoad = FluidInkSheetLoad.Ready(this)
