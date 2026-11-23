package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope

/**
 * F6C.19 · **«•••» della pagina: le azioni del tipo** (scelta di Alessio del 23 novembre: «•••» c'è sempre, come nella
 * tavola «D · Mix», e tiene le azioni proprie del tipo, come Cerca e Annotazioni in Quick Look). Le azioni del file
 * (Condividi, Apri con…, Informazioni…) stanno nel menu del nome in cima: il visore toglie da qui le voci che il nome ha
 * già, così niente è doppio.
 *
 * I preset di serie lo dichiarano da sé (pagine, audio, foglio). **Una famiglia** lo dichiara nelle `actions` della sua
 * pagina [FluidViewerContent.Custom]:
 *
 * ```kotlin
 * FluidViewerContent.Custom(actions = {
 *   viewerTypeActions { listOf(FluidContextAction("Espandi tutto") { espandi() }, FluidContextAction("Comprimi tutto") { chiudi() }) }
 * }) { … }
 * ```
 *
 * Il menu si legge quando si apre: lo stato letto dentro (una voce scelta, un timer acceso) è quello di quel momento.
 * Una pagina senza azioni del tipo ha in «•••» «Nascondi controlli».
 */
fun FluidToolbarScope.viewerTypeActions(label: String = "Altro", menu: () -> List<FluidContextAction>) {
  action(ViewerTypeActionsId, FluidIcons.MoreHoriz, label, placement = FluidToolbarPlacement.BottomBar, menu = menu)
}

/** L'id del «•••» di una pagina nella barra del visore. */
internal object ViewerTypeActionsId {
  override fun toString(): String = "viewer.type.actions"
}

/**
 * F6C.19 · Una sorgente di pagine che conosce le **note del relatore** delle sue slide (PPTX e ODP del modulo Office).
 * Il preset delle pagine la riconosce e mette «Note del relatore» in «•••». È una capacità facoltativa, come
 * [FluidMediaDurationLoader] per un player.
 */
interface FluidPageNotes {
  /** Le note della pagina [index] (da zero), `null` se non ne ha. */
  fun notes(index: Int): String?

  /** Vero se almeno una pagina ha le note. Pubblicalo come stato di Compose se arriva dopo. */
  val hasNotes: Boolean
}

/**
 * Un riferimento di cella come in un foglio di calcolo («B12», «aa3», «$C$7»): la colonna e la riga da zero, o `null`.
 * Pura.
 */
internal fun fluidCellReference(text: String): Pair<Int, Int>? {
  val clean = text.trim().replace("$", "").uppercase()
  val match = Regex("^([A-Z]{1,3})([0-9]{1,7})$").matchEntire(clean) ?: return null
  var column = 0
  for (letter in match.groupValues[1]) column = column * 26 + (letter - 'A' + 1)
  val row = match.groupValues[2].toIntOrNull() ?: return null
  if (row < 1) return null
  return (column - 1) to (row - 1)
}

/**
 * Il riquadro «Vai a…» delle azioni del tipo (pagina, slide, cella): un campo, «Annulla» e «Vai». [parse] dice se il
 * testo è valido (e «Vai» si accende); [onGo] riceve il valore letto.
 */
@Composable
internal fun <T : Any> ViewerGoToAlert(
  visible: Boolean,
  title: String,
  label: String,
  hint: String,
  numeric: Boolean,
  parse: (String) -> T?,
  onGo: (T) -> Unit,
  onDismiss: () -> Unit,
  go: String = "Vai",
  cancel: String = "Annulla",
) {
  var text by remember(visible) { mutableStateOf("") }
  val value = parse(text)
  FluidAlert(if (visible) title else null, onDismissRequest = onDismiss, title = { it }, message = { hint },
    actions = { _ ->
      listOf(FluidAlertAction(cancel, onDismiss), FluidAlertAction(go, {
        val chosen = parse(text)
        onDismiss()
        if (chosen != null) onGo(chosen)
      }, FluidAlertAction.Emphasis.Preferred, enabled = value != null))
    }) { _ ->
    FluidTextField(text, { text = it }, Modifier.fillMaxWidth(), label = label,
      keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Ascii,
        imeAction = ImeAction.Go),
      keyboardActions = KeyboardActions(onGo = {
        parse(text)?.let { chosen ->
          onDismiss()
          onGo(chosen)
        }
      }))
  }
}
