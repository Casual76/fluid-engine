package dev.antigravity.fluidengine.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerToolsScope

/**
 * Il legame fra il visore dei file e l'editor: tiene lo stato dell'editor aperto, cosi' «Fine» del visore sa che
 * ricetta dare all'app. Uno per visore, con [rememberFluidImageEditTool].
 */
@Stable
class FluidImageEditTool internal constructor() {
  /** Lo stato dell'editor aperto nel visore, o null se lo strumento e' chiuso. */
  var state: FluidImageEditorState? by mutableStateOf(null)
    internal set
}

/** Il legame per un visore dei file. */
@Composable
fun rememberFluidImageEditTool(): FluidImageEditTool = remember { FluidImageEditTool() }

/**
 * **«Modifica» come strumento del visore dei file** (la scheda dell'idea: il visore ha il posto per gli strumenti). Il
 * tasto compare nella capsula in basso del visore; scelto, la pagina diventa l'editor (la sua seconda riga, la foto, la
 * ruota o la cornice, le schede) e la capsula del visore tiene «Fine». **«Fine» chiama [onDone]** con la ricetta;
 * indietro chiude lo strumento senza salvare, come «Annulla».
 *
 * ```kotlin
 * val edit = rememberFluidImageEditTool()
 * FluidFileViewer(count, content = …, tools = {
 *   if (isPhoto(current)) fluidImageEditTool(edit, source = { i -> FluidEditorSource.uri(files[i].uri) }) { i, ricetta ->
 *     scope.launch { edit.state?.export(nuovoFile(i)) }   // un file nuovo: l'originale resta
 *   }
 * }, onPageChange = { current = it })
 * ```
 *
 * @param source la foto della pagina [index]; null se la pagina non e' una foto (lo strumento non si apre).
 * @param initial la ricetta con cui riaprire la pagina (una modifica salvata), di serie la foto com'e'.
 */
fun FluidViewerToolsScope.fluidImageEditTool(
  tool: FluidImageEditTool,
  source: (index: Int) -> FluidEditorSource?,
  initial: (index: Int) -> FluidImageEdit = { FluidImageEdit.Original },
  label: String = "Modifica",
  strings: FluidImageEditorStrings = FluidImageEditorStrings(),
  aspects: List<FluidCropAspect> = FluidCropAspect.Presets,
  id: String = "fluid-image-edit",
  onDone: (index: Int, edit: FluidImageEdit) -> Unit,
) {
  tool(
    id = id,
    icon = FluidIcons.Edit,
    label = label,
    onDone = { index -> tool.state?.let { onDone(index, it.edit) } },
  ) { index ->
    val src = source(index) ?: return@tool
    EditToolBody(tool, src, initial(index), strings, aspects)
  }
}

@Composable
private fun EditToolBody(
  tool: FluidImageEditTool, source: FluidEditorSource, initial: FluidImageEdit, strings: FluidImageEditorStrings,
  aspects: List<FluidCropAspect>,
) {
  val state = rememberFluidImageEditorState(source, initial)
  DisposableEffect(state) {
    tool.state = state
    onDispose { if (tool.state === state) tool.state = null }
  }
  EditorScaffold(state, onCancel = {}, onDone = {}, modifier = Modifier, strings = strings, aspects = aspects,
    onMarkup = null, embedded = true)
}
