package dev.antigravity.fluidengine.sample.catalog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.richtext.FluidRichTextDocument
import dev.antigravity.fluidengine.richtext.FluidRichTextEditor
import dev.antigravity.fluidengine.richtext.FluidRichTextFormatButton
import dev.antigravity.fluidengine.richtext.FluidRichTextHtml
import dev.antigravity.fluidengine.richtext.FluidRichTextLabels
import dev.antigravity.fluidengine.richtext.FluidRichTextListKind
import dev.antigravity.fluidengine.richtext.FluidRichTextMarkdown
import dev.antigravity.fluidengine.richtext.FluidRichTextParagraphStyle
import dev.antigravity.fluidengine.richtext.FluidRichTextState
import dev.antigravity.fluidengine.richtext.FluidRichTextToolbar
import dev.antigravity.fluidengine.richtext.LocalFluidRichTextLabels
import dev.antigravity.fluidengine.richtext.buildFluidRichText
import dev.antigravity.fluidengine.richtext.rememberFluidRichTextState
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsulePlacement
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * L'editor con formattazione (modulo opt-in `engine-richtext`, F1.4 parte C): il campo, la barra sopra la
 * tastiera, il tasto «Aa», l'incolla di HTML sporco e le tre uscite (HTML ristretto, Markdown, testo).
 *
 * Non e' ancora nella galleria: la monta chi la prepara (F1.6). Stati: vuoto, con contenuto (tutti gli
 * stili, elenchi, link, citazione), sola lettura, e tre riquadri che rifanno l'editor col testo al 200%
 * in arabo (da destra a sinistra), in scuro e con il movimento ridotto. Ogni riquadro ha il suo host dei
 * fogli (per il foglio del link) e posa la barra in fondo a se' senza seguire la tastiera: in una pagina
 * vera la barra sta nell'overlay della schermata e sale con la tastiera (`avoidKeyboard`, di serie).
 */
@Composable
fun RichTextDemo() {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("richtext-demo"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    FluidSectionHeader(
      title = "Editor vuoto",
      detail = "Il segnaposto, il tasto «Aa» che chiede la barra; seleziona del testo e la barra compare da sola.",
    )
    EditorStage(rememberFluidRichTextState(), placeholder = "Scrivi un messaggio…", tag = "richtext-empty")

    FluidSectionHeader(
      title = "Con contenuto ed esportazione",
      detail = "Grassetto, corsivo, sottolineato, elenchi, link, citazione. Sotto: l'HTML ristretto, il Markdown e il testo, aggiornati mentre scrivi.",
    )
    val full = rememberFluidRichTextState(sampleDocument())
    EditorStage(full, placeholder = null, tag = "richtext-full")
    PasteControls(full)
    Exports(full)

    FluidSectionHeader(title = "Sola lettura", detail = "Niente tastiera e niente barra; il testo si seleziona e si copia.")
    EditorStage(rememberFluidRichTextState(sampleDocument()), placeholder = null, readOnly = true, tag = "richtext-readonly")

    FluidSectionHeader(title = "Testo al 200%, da destra a sinistra", detail = "I segni degli elenchi e la barra della citazione passano sul lato destro.")
    val density = LocalDensity.current
    CompositionLocalProvider(
      LocalDensity provides Density(density.density, 2f),
      LocalLayoutDirection provides LayoutDirection.Rtl,
      LocalFluidRichTextLabels provides FluidRichTextLabels.english(),
    ) {
      EditorStage(rememberFluidRichTextState(rtlDocument()), placeholder = null, tag = "richtext-rtl-large")
    }

    FluidSectionHeader(title = "Scuro, movimento ridotto", detail = "La barra entra in dissolvenza, senza salire.")
    FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.DARK) }) {
      CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(0f)) {
        EditorStage(rememberFluidRichTextState(sampleDocument()), placeholder = null, tag = "richtext-dark")
      }
    }
  }
}

/** Un editor dentro un riquadro con la sua barra in fondo e il suo host dei fogli. */
@Composable
private fun EditorStage(
  state: FluidRichTextState,
  placeholder: String?,
  tag: String,
  readOnly: Boolean = false,
) {
  val backdrop = rememberGlassBackdrop()
  val host = rememberFluidGlassModalHostState()
  CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(420.dp)
        .clip(ContinuousCornerShape(FluidRadius.Group))
        .background(MaterialTheme.colorScheme.surface)
        .testTag(tag),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .glassBackdropSource(backdrop)
          .background(MaterialTheme.colorScheme.surface)
          .verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = if (readOnly) "Sola lettura" else "Corpo",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          if (!readOnly) FluidRichTextFormatButton(state)
        }
        FluidRichTextEditor(
          state = state,
          placeholder = placeholder,
          contentDescription = "Corpo",
          readOnly = readOnly,
          minLines = 6,
          modifier = Modifier.heightIn(min = 160.dp),
        )
      }
      FluidRichTextToolbar(
        state = state,
        modifier = Modifier.align(Alignment.BottomCenter),
        backdrop = backdrop,
        placement = FluidFloatingCapsulePlacement.Free,
        avoidKeyboard = false,
      )
      FluidGlassModalHost(state = host, backdrop = backdrop)
    }
  }
}

/** L'incolla di un HTML sporco (da un browser o da Google Documenti), dal codice o dagli appunti. */
@Composable
private fun PasteControls(state: FluidRichTextState) {
  val context = LocalContext.current
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidButton(
      text = "Incolla HTML sporco",
      onClick = { state.replaceSelection(FluidRichTextHtml.read(DirtyHtml)) },
      style = FluidButtonStyle.Tinted,
      size = FluidButtonSize.Small,
    )
    FluidButton(
      text = "Mettilo negli appunti",
      onClick = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newHtmlText("demo", "testo di prova", DirtyHtml))
      },
      style = FluidButtonStyle.Plain,
      size = FluidButtonSize.Small,
    )
  }
}

private enum class ExportKind { Html, Markdown, Text }

/** Le tre uscite del documento, lette a ogni modifica ([FluidRichTextState.revision]). */
@Composable
private fun Exports(state: FluidRichTextState) {
  var kind by remember { mutableStateOf(ExportKind.Html) }
  FluidSegmentedControl(
    options = ExportKind.entries,
    selected = kind,
    onSelect = { kind = it },
    label = { when (it) { ExportKind.Html -> "HTML"; ExportKind.Markdown -> "Markdown"; ExportKind.Text -> "Testo" } },
  )
  // `document` e' immutabile e si legge in O(1): convertirlo a ogni modifica va bene per una demo; un'app
  // che salva una bozza lo fa dopo una pausa (`snapshotFlow { state.revision }` + debounce).
  val document = state.document
  val output = when (kind) {
    ExportKind.Html -> FluidRichTextHtml.write(document)
    ExportKind.Markdown -> FluidRichTextMarkdown.write(document)
    ExportKind.Text -> document.toPlainText()
  }
  Text(
    text = output,
    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .fillMaxWidth()
      .clip(ContinuousCornerShape(FluidRadius.Control))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(12.dp)
      .testTag("richtext-export"),
  )
}

private fun sampleDocument(): FluidRichTextDocument = buildFluidRichText {
  paragraph { append("Ciao "); bold("Anna"); append(",") }
  paragraph { append("ecco il "); italic("riepilogo"); append(" della riunione, con le cose "); underline("da fare"); append(":") }
  paragraph(FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)) { append("rivedere il preventivo") }
  paragraph(FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)) { append("prenotare la sala") }
  paragraph(FluidRichTextParagraphStyle(list = FluidRichTextListKind.Numbered)) { append("prima la bozza") }
  paragraph(FluidRichTextParagraphStyle(list = FluidRichTextListKind.Numbered)) { append("poi la versione finale") }
  paragraph { append("I dettagli sono "); link("sul sito", "https://esempio.it/riunione"); append(".") }
  paragraph(FluidRichTextParagraphStyle(quote = true)) { append("Ci vediamo giovedì alle 10.") }
}

private fun rtlDocument(): FluidRichTextDocument = buildFluidRichText {
  paragraph { bold("مرحبا"); append(" بالعالم") }
  paragraph(FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)) { append("البند الأول") }
  paragraph(FluidRichTextParagraphStyle(list = FluidRichTextListKind.Numbered)) { append("الخطوة الأولى") }
  paragraph(FluidRichTextParagraphStyle(quote = true)) { italic("اقتباس") }
}

/** Un HTML come lo copia un browser: stili in linea, script, un link pericoloso, un'immagine-spia. */
private const val DirtyHtml =
  "<meta charset='utf-8'><style>p{color:red}</style><script>alert('no')</script>" +
    "<b style=\"font-weight:normal\" id=\"docs-internal-guid-x\"><p><span style=\"font-weight:700\">Titolo incollato</span></p>" +
    "<p>Testo con <span style=\"font-style:italic\">corsivo</span>, <font color=red>colore</font> e " +
    "<a href=\"javascript:alert(1)\">un link cattivo</a> e <a href=\"https://esempio.it\">uno buono</a>.</p>" +
    "<img src=\"https://tracker.example/pixel.gif\"><ul><li>uno<ul><li>annidato</li></ul></li><li>due</li></ul></b>"
