package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.richtext.FluidRichTextDocument
import dev.antigravity.fluidengine.richtext.FluidRichTextEditor
import dev.antigravity.fluidengine.richtext.FluidRichTextFormatButton
import dev.antigravity.fluidengine.richtext.FluidRichTextInlineStyle
import dev.antigravity.fluidengine.richtext.FluidRichTextToolbar
import dev.antigravity.fluidengine.richtext.rememberFluidRichTextState
import dev.antigravity.fluidengine.ui.fluid.FluidBarMinimizeBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.FluidToken
import dev.antigravity.fluidengine.ui.fluid.FluidTokenField
import dev.antigravity.fluidengine.ui.fluid.FluidTokenFieldValue
import dev.antigravity.fluidengine.ui.fluid.FluidTokenSuggestion
import dev.antigravity.fluidengine.ui.fluid.icons.Drafts
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FormatBold
import dev.antigravity.fluidengine.ui.fluid.icons.FormatItalic
import dev.antigravity.fluidengine.ui.fluid.icons.Schedule
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.fluid.toolbar.fieldNavigation
import dev.antigravity.fluidengine.ui.fluid.toolbar.history
import dev.antigravity.fluidengine.ui.fluid.toolbar.quickActions
import dev.antigravity.fluidengine.ui.fluid.toolbar.suggestions
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

// F6B.9: la barra sopra la tastiera. I casi delle app di Alessio rifatti col solo engine: nessuna API di movimento qui
// (la quota della tastiera fotogramma per fotogramma e il cambio di contesto che si trasforma sono della barra).
//
// - `tastiera`: Mail «Scrivi». Tre campi e una barra per campo, coi preset: nei destinatari i suggerimenti, nell'oggetto
//   le azioni rapide (allega, foto), nel testo la formattazione con annulla e ripeti e «Scrivi con Aria»; in tutti la
//   navigazione fra campi con «Fatto». Passando da un campo all'altro la barra si trasforma come una barra in alto che
//   cambia stato. In alto «Invia ▾», il tasto diviso di F6B.11.
// - `testo-ricco`: Notes, una nota. La barra del testo ricco (`FluidRichTextToolbar`, la firma di sempre) è il preset
//   della barra sopra la tastiera: compare con «Aa» o con una selezione, i tasti scorrono di lato nella capsula.

/** I tag dei test e delle riprese. */
internal object KeyboardSceneTags {
  const val To = "tastiera:a"
  const val Subject = "tastiera:oggetto"
  const val Body = "tastiera:testo"
  const val Outcome = "tastiera:esito"
  const val Note = "testo-ricco:nota"
}

/** Il campo con il fuoco: la chiave di contesto della barra sopra la tastiera. */
private enum class ComposeField { None, To, Subject, Body }

private val ComposePeople = listOf("Anna Bellini", "Luca", "Marta R.", "Giorgio", "Sara")

/** Mail «Scrivi»: una barra sopra la tastiera per campo (`keyboard(context = campo) { … }`), coi preset dell'engine. */
@Composable
internal fun KeyboardBarScene(onBack: () -> Unit) {
  var to by rememberSaveable(stateSaver = FluidTokenFieldValue.Saver) { mutableStateOf(FluidTokenFieldValue()) }
  var subject by rememberSaveable { mutableStateOf("") }
  var outcome by rememberSaveable { mutableStateOf("Nessuna azione") }
  var focused by remember { mutableStateOf(ComposeField.None) }
  val body = rememberFluidRichTextState()
  val focus = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  val draft = to.draft.text.trim()
  val offered = ComposePeople
    .filter { name -> to.tokens.none { it.label == name } && (draft.isEmpty() || name.contains(draft, ignoreCase = true)) }
    .map { FluidTokenSuggestion(it, it) }
  val field = if (body.isFocused) ComposeField.Body else focused
  FluidScreen(
    title = "Nuova mail",
    onBack = onBack,
    barMinimize = FluidBarMinimizeBehavior.Never,
    toolbar = {
      title(FluidToolbarTitleMode.Inline)
      split(
        "invia", "Invia",
        onClick = { outcome = "Inviata ora" },
        menuLabel = "Opzioni di invio",
        menu = {
          listOf(
            FluidContextAction("Programma invio", icon = FluidIcons.Schedule, onClick = { outcome = "Programmata per domani alle 8" }),
            FluidContextAction("Salva come bozza", icon = FluidIcons.Drafts, onClick = { outcome = "Salvata nelle bozze" }),
          )
        },
      )
      keyboard(context = field, fieldFocused = field != ComposeField.None) {
        when (field) {
          ComposeField.To -> suggestions(offered.take(3)) { picked ->
            to = to.copy(tokens = to.tokens + FluidToken(picked.id, picked.title), draft = TextFieldValue())
          }
          ComposeField.Subject -> quickActions(
            onAttach = { outcome = "Allegato un documento" },
            onPhoto = { outcome = "Aggiunta una foto" },
          )
          ComposeField.Body -> {
            button("grassetto", FluidIcons.FormatBold, "Grassetto", { body.toggle(FluidRichTextInlineStyle.Bold) },
              selected = body.format.bold)
            button("corsivo", FluidIcons.FormatItalic, "Corsivo", { body.toggle(FluidRichTextInlineStyle.Italic) },
              selected = body.format.italic)
            history(body.canUndo, body.canRedo, { body.undo() }, { body.redo() })
            quickActions(onWritingTools = { outcome = "Scrivi con Aria" }, writingToolsLabel = "Scrivi con Aria")
          }
          ComposeField.None -> Unit
        }
        fieldNavigation(focus, keyboard, hasPrevious = field != ComposeField.To, hasNext = field != ComposeField.Body)
      }
    },
  ) {
    item(key = "a") {
      FluidTokenField(
        value = to,
        onValueChange = { to = it },
        createToken = { text, tokens ->
          val name = text.trim()
          if (name.isEmpty() || tokens.any { it.label == name }) null else FluidToken(name, name)
        },
        label = "A",
        placeholder = "Destinatari",
        modifier = Modifier.onFocusChanged { if (it.hasFocus) focused = ComposeField.To else if (focused == ComposeField.To) focused = ComposeField.None }
          .testTag(KeyboardSceneTags.To),
      )
    }
    item(key = "oggetto") {
      FluidTextField(
        subject, { subject = it },
        modifier = Modifier.onFocusChanged {
          if (it.isFocused) focused = ComposeField.Subject else if (focused == ComposeField.Subject) focused = ComposeField.None
        }.testTag(KeyboardSceneTags.Subject),
        placeholder = "Oggetto",
      )
    }
    item(key = "testo") {
      FluidRichTextEditor(body, placeholder = "Scrivi il messaggio", contentDescription = "Messaggio", minLines = 6,
        modifier = Modifier.testTag(KeyboardSceneTags.Body))
    }
    item(key = "esito") {
      FluidListGroup {
        FluidListRow(title = outcome, subtitle = "Ultima azione", modifier = Modifier.testTag(KeyboardSceneTags.Outcome))
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Tocca un campo: sopra la tastiera c'è la sua barra. Passando al campo dopo, la barra si trasforma; «Fatto» " +
          "chiude la tastiera e la barra insieme.",
      )
    }
  }
}

private val NoteText = FluidRichTextDocument.fromPlainText(
  "Lista per il trasloco\n\nScatole per il magazzino, nastro, pennarelli. Chiamare Sara per la rete e i telefoni. " +
    "Il preventivo va firmato entro venerdì.",
)

/**
 * Notes: la barra del testo ricco (`FluidRichTextToolbar`), il preset della barra sopra la tastiera con la firma di
 * sempre, nell'overlay della pagina. «Aa» la chiede; una selezione la fa comparire da sola.
 */
@Composable
internal fun RichTextKeyboardScene(onBack: () -> Unit) {
  val note = rememberFluidRichTextState(NoteText)
  FluidScreen(
    title = "Trasloco",
    onBack = onBack,
    barMinimize = FluidBarMinimizeBehavior.Never,
    toolbar = { title(FluidToolbarTitleMode.Inline) },
    overlay = { backdrop ->
      FluidRichTextToolbar(note, modifier = Modifier.align(Alignment.BottomCenter), backdrop = backdrop)
    },
  ) {
    item(key = "intestazione") {
      Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Nota", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FluidRichTextFormatButton(note)
      }
    }
    item(key = "nota") {
      FluidRichTextEditor(note, placeholder = "Scrivi la nota", contentDescription = "Nota", minLines = 8,
        modifier = Modifier.testTag(KeyboardSceneTags.Note))
    }
    item(key = "spiega-titolo") { FluidSectionHeader(title = "Come si prova") }
    item(key = "spiega") {
      FluidSectionFootnote(
        "Tocca «Aa» o seleziona una parola: la barra del testo ricco sale sopra la tastiera, coi tasti che dicono cosa " +
          "è acceso. La riga che scrivi resta sopra la barra.",
      )
    }
  }
}
