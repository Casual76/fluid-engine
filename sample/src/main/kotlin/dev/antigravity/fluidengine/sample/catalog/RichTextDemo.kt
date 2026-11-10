package dev.antigravity.fluidengine.sample.catalog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
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
import dev.antigravity.fluidengine.richtext.FluidRichTextBlock.Bullet
import dev.antigravity.fluidengine.richtext.FluidRichTextBlock.Numbered
import dev.antigravity.fluidengine.richtext.FluidRichTextBlock.Quote
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
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * L'editor con formattazione (modulo opt-in `engine-richtext`): il campo, la barra sopra la
 * tastiera, il tasto «Aa», l'incolla di HTML sporco e le tre uscite (HTML ristretto, Markdown, testo).
 *
 * E' la pagina «Testo ricco» della galleria (gruppo Campi). Stati: vuoto, con contenuto (tutti gli
 * stili, elenchi, link, citazione), elenchi e citazioni annidati su piu' livelli con le loro uscite, sola
 * lettura, e due riquadri che rifanno l'editor col testo al 200% in arabo (da destra a sinistra, con un
 * elenco profondo) e in scuro con il movimento ridotto (annidato).
 *
 * **La barra sta dove sta in un'app vera: nell'overlay della pagina** ([RichTextDemoToolbars], che la
 * pagina passa a `FluidScreen.overlay`), non dentro il riquadro. Prima ogni riquadro alto 420 dp si posava
 * la barra in fondo a se' con `avoidKeyboard = false`, perche' una barra con `avoidKeyboard = true` dentro
 * un riquadro che non arriva in fondo allo schermo sale di tutta la tastiera e esce dal riquadro: ma cosi'
 * la barra finiva dietro Gboard (con «Aa» acceso non si vedeva niente) e, senza tastiera, sotto la pillola
 * della galleria. Nell'overlay vale l'uso vero: con la tastiera aperta la barra prende il posto della
 * barra delle schede e sale con la tastiera (`ReplacingTabBar`, `avoidKeyboard` di serie); a tastiera
 * chiusa la galleria ha la sua pillola in fondo, e la barra si posa sopra di lei (`AboveTabBar`) invece di
 * finirle sotto. L'host dei fogli (quello del link) e' quello della radice della galleria, come in un'app.
 *
 * **La pagina finisce sopra la tastiera** (`imePadding` sulla `FluidScreen` che la ospita: vedi `SectionsPage`
 * con `avoidKeyboard`), come deve fare la pagina di un compositore: cosi' e' la pagina che scorre a portare
 * la riga che si scrive sopra la tastiera, e l'editor, con la barra accesa, a portarla **sopra la barra**.
 * Prima la pagina restava sotto la tastiera, e per mostrare il campo Android spostava l'intera finestra:
 * il titolo usciva dallo schermo e la barra finiva a meta' del testo.
 */
@Composable
fun RichTextDemo(states: RichTextDemoStates) {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("richtext-demo"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    FluidSectionHeader(
      title = "Editor vuoto",
      detail = "Il segnaposto, il tasto «Aa» che chiede la barra; seleziona del testo e la barra compare da sola.",
    )
    EditorStage(states.empty, placeholder = "Scrivi un messaggio…", tag = "richtext-empty")

    FluidSectionHeader(
      title = "Con contenuto ed esportazione",
      detail = "Grassetto, corsivo, sottolineato, elenchi, link, citazione. Sotto: l'HTML ristretto, il Markdown e il testo, aggiornati mentre scrivi.",
    )
    EditorStage(states.full, placeholder = null, tag = "richtext-full")
    PasteControls(states.full)
    Exports(states.full)

    FluidSectionHeader(
      title = "Elenchi e citazioni annidati",
      detail = "Sotto-elenchi puntati e numerati, citazione dentro una voce e voce dentro una citazione. " +
        "Riduci/aumenta rientro dalla barra (o Tab e Maiusc+Tab), a capo su una voce vuota risale di un livello.",
    )
    EditorStage(states.nested, placeholder = null, tag = "richtext-nested")
    Exports(states.nested, tag = "richtext-nested-export")

    FluidSectionHeader(title = "Sola lettura", detail = "Niente tastiera e niente barra; il testo si seleziona e si copia.")
    EditorStage(states.readOnly, placeholder = null, readOnly = true, tag = "richtext-readonly")

    FluidSectionHeader(
      title = "Testo al 200%, da destra a sinistra",
      detail = "I segni degli elenchi e la barra della citazione passano sul lato destro; ai livelli profondi il rientro si stringe.",
    )
    RtlLargeEnvironment {
      EditorStage(states.rtl, placeholder = null, tag = "richtext-rtl-large")
    }

    FluidSectionHeader(title = "Scuro, movimento ridotto", detail = "La barra entra in dissolvenza, senza salire.")
    DarkReducedEnvironment {
      EditorStage(states.dark, placeholder = null, tag = "richtext-dark")
    }
  }
}

/** Gli stati degli editor della pagina: stanno fuori dal contenuto perche' le barre vivono nell'overlay. */
@Stable
class RichTextDemoStates internal constructor(
  internal val empty: FluidRichTextState,
  internal val full: FluidRichTextState,
  internal val nested: FluidRichTextState,
  internal val readOnly: FluidRichTextState,
  internal val rtl: FluidRichTextState,
  internal val dark: FluidRichTextState,
)

@Composable
fun rememberRichTextDemoStates(): RichTextDemoStates = RichTextDemoStates(
  empty = rememberFluidRichTextState(),
  full = rememberFluidRichTextState(sampleDocument()),
  nested = rememberFluidRichTextState(nestedDocument()),
  readOnly = rememberFluidRichTextState(sampleDocument()),
  rtl = rememberFluidRichTextState(rtlDocument()),
  dark = rememberFluidRichTextState(nestedDocument()),
)

/** Il riquadro dell'arabo: testo al 200%, da destra a sinistra, etichette inglesi (editor e barra lo ripetono). */
@Composable
private fun RtlLargeEnvironment(content: @Composable () -> Unit) {
  val density = LocalDensity.current
  CompositionLocalProvider(
    LocalDensity provides Density(density.density, 2f),
    LocalLayoutDirection provides LayoutDirection.Rtl,
    LocalFluidRichTextLabels provides FluidRichTextLabels.english(),
    content = content,
  )
}

/** Il riquadro scuro con il movimento ridotto (editor e barra lo ripetono). */
@Composable
private fun DarkReducedEnvironment(content: @Composable () -> Unit) {
  FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.DARK) }) {
    CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(0f), content = content)
  }
}

/**
 * Le barre di formattazione di tutti gli editor della pagina, **da passare a `FluidScreen.overlay`**: ognuna
 * e' in scena solo per l'editor che ha il fuoco (`isFormattingBarVisible`), quindi ne compare una sola.
 *
 * Con la tastiera aperta la barra e' quella di un'app vera (`ReplacingTabBar`, `avoidKeyboard` di serie: sale
 * con la tastiera). A tastiera chiusa la pillola della galleria e' in fondo allo schermo: la barra si posa
 * sopra di lei (`AboveTabBar`) e non le finisce sotto.
 */
@Composable
fun BoxScope.RichTextDemoToolbars(states: RichTextDemoStates, backdrop: GlassBackdropState) {
  val keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
  val placement =
    if (keyboardUp) FluidFloatingCapsulePlacement.ReplacingTabBar else FluidFloatingCapsulePlacement.AboveTabBar
  val bar = Modifier.align(Alignment.BottomCenter)
  FluidRichTextToolbar(states.empty, modifier = bar, backdrop = backdrop, placement = placement)
  FluidRichTextToolbar(states.full, modifier = bar, backdrop = backdrop, placement = placement)
  FluidRichTextToolbar(states.nested, modifier = bar, backdrop = backdrop, placement = placement)
  RtlLargeEnvironment {
    FluidRichTextToolbar(states.rtl, modifier = bar, backdrop = backdrop, placement = placement)
  }
  DarkReducedEnvironment {
    FluidRichTextToolbar(states.dark, modifier = bar, backdrop = backdrop, placement = placement)
  }
}

/** Un editor dentro un riquadro: la sua barra sta nell'overlay della pagina ([RichTextDemoToolbars]). */
@Composable
private fun EditorStage(
  state: FluidRichTextState,
  placeholder: String?,
  tag: String,
  readOnly: Boolean = false,
) {
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
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
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
  }
}

/** L'incolla di un HTML sporco (da un browser o da Google Documenti), dal codice o dagli appunti. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasteControls(state: FluidRichTextState) {
  val context = LocalContext.current
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun Exports(state: FluidRichTextState, tag: String = "richtext-export") {
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
      .testTag(tag),
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
  paragraph(FluidRichTextParagraphStyle.of(Bullet, Bullet)) { append("بند فرعي") }
  paragraph(FluidRichTextParagraphStyle.of(Bullet, Bullet, Bullet, Bullet)) { append("بند عميق في القائمة المتداخلة") }
  paragraph(FluidRichTextParagraphStyle(list = FluidRichTextListKind.Numbered)) { append("الخطوة الأولى") }
  paragraph(FluidRichTextParagraphStyle(quote = true)) { italic("اقتباس") }
  paragraph(FluidRichTextParagraphStyle.of(Quote, Quote)) { append("اقتباس داخل اقتباس") }
}

/** Tutte le combinazioni: sotto-elenchi, numerazione che riparte e prosegue, citazioni annidate e miste. */
private fun nestedDocument(): FluidRichTextDocument = buildFluidRichText {
  paragraph { append("Ordine del giorno:") }
  paragraph(FluidRichTextParagraphStyle.of(Numbered)) { bold("Bilancio") }
  paragraph(FluidRichTextParagraphStyle.of(Numbered, Bullet)) { append("entrate") }
  paragraph(FluidRichTextParagraphStyle.of(Numbered, Bullet)) { append("uscite") }
  paragraph(FluidRichTextParagraphStyle.of(Numbered, Bullet, Bullet)) { append("affitto della sala") }
  paragraph(FluidRichTextParagraphStyle.of(Numbered, Bullet, Bullet, Numbered)) { append("primo preventivo") }
  paragraph(FluidRichTextParagraphStyle.of(Numbered, Bullet, Bullet, Numbered)) { append("secondo preventivo") }
  paragraph(FluidRichTextParagraphStyle.of(Numbered, Quote)) { italic("«Restiamo nel budget»") }
  paragraph(FluidRichTextParagraphStyle.of(Numbered)) { bold("Calendario") }
  paragraph(FluidRichTextParagraphStyle.of(Numbered, Numbered)) { append("bozza entro venerdì") }
  paragraph(FluidRichTextParagraphStyle.of(Numbered)) { bold("Varie") }
  paragraph { append("Dalla mail di Luca:") }
  paragraph(FluidRichTextParagraphStyle.of(Quote)) { append("Per me va bene, con due note:") }
  paragraph(FluidRichTextParagraphStyle.of(Quote, Bullet)) { append("la sala piccola basta") }
  paragraph(FluidRichTextParagraphStyle.of(Quote, Bullet, Bullet)) { append("se siamo meno di venti") }
  paragraph(FluidRichTextParagraphStyle.of(Quote, Quote)) { append("Anna aveva scritto: «Meglio giovedì»") }
}

/**
 * Un HTML come lo copia un browser: stili in linea, script, un link pericoloso, un'immagine-spia, un
 * elenco annidato (che resta annidato).
 */
private const val DirtyHtml =
  "<meta charset='utf-8'><style>p{color:red}</style><script>alert('no')</script>" +
    "<b style=\"font-weight:normal\" id=\"docs-internal-guid-x\"><p><span style=\"font-weight:700\">Titolo incollato</span></p>" +
    "<p>Testo con <span style=\"font-style:italic\">corsivo</span>, <font color=red>colore</font> e " +
    "<a href=\"javascript:alert(1)\">un link cattivo</a> e <a href=\"https://esempio.it\">uno buono</a>.</p>" +
    "<img src=\"https://tracker.example/pixel.gif\"><ul><li>uno<ul><li>annidato</li></ul></li><li>due</li></ul></b>"
