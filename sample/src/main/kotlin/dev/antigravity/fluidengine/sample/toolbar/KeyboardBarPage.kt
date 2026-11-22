package dev.antigravity.fluidengine.sample.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.antigravity.fluidengine.richtext.FluidRichTextEditor
import dev.antigravity.fluidengine.richtext.FluidRichTextInlineStyle
import dev.antigravity.fluidengine.richtext.rememberFluidRichTextState
import dev.antigravity.fluidengine.ui.fluid.FluidBarMinimizeBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.FluidTokenSuggestion
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FormatBold
import dev.antigravity.fluidengine.ui.fluid.icons.FormatItalic
import dev.antigravity.fluidengine.ui.fluid.icons.FormatUnderlined
import dev.antigravity.fluidengine.ui.fluid.toolbar.fieldNavigation
import dev.antigravity.fluidengine.ui.fluid.toolbar.history
import dev.antigravity.fluidengine.ui.fluid.toolbar.suggestions

/** Scena del pacchetto F6B.9, apribile con `gallery.page toolbar` e `toolbar.scene keyboard`. */
@Composable
internal fun KeyboardBarPage(onBack: () -> Unit) {
  var recipient by rememberSaveable { mutableStateOf("") }
  var recipientFocused by remember { mutableStateOf(false) }
  val editor = rememberFluidRichTextState()
  val focus = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  FluidScreen(
    title = "Barra della tastiera",
    onBack = onBack,
    barMinimize = FluidBarMinimizeBehavior.Never,
    toolbar = {
      keyboard(context = if (recipientFocused) "destinatari" else "formattazione",
        fieldFocused = recipientFocused || editor.isFocused) {
        if (recipientFocused) {
          suggestions(listOf(FluidTokenSuggestion("1", "Anna"), FluidTokenSuggestion("2", "Luca"))) {
            recipient = it.title
          }
        } else {
          button("bold", FluidIcons.FormatBold, "Grassetto", { editor.toggle(FluidRichTextInlineStyle.Bold) },
            selected = editor.format.bold)
          button("italic", FluidIcons.FormatItalic, "Corsivo", { editor.toggle(FluidRichTextInlineStyle.Italic) },
            selected = editor.format.italic)
          button("underline", FluidIcons.FormatUnderlined, "Sottolineato", { editor.toggle(FluidRichTextInlineStyle.Underline) },
            selected = editor.format.underline)
          history(editor.canUndo, editor.canRedo, { editor.undo() }, { editor.redo() })
        }
        fieldNavigation(focus, keyboardController, hasPrevious = !recipientFocused, hasNext = recipientFocused)
      }
    },
  ) {
    item {
      FluidTextField(recipient, { recipient = it }, Modifier.onFocusChanged { recipientFocused = it.isFocused },
        placeholder = "Destinatari")
    }
    item { FluidRichTextEditor(editor, placeholder = "Scrivi il messaggio", contentDescription = "Messaggio") }
  }
}
