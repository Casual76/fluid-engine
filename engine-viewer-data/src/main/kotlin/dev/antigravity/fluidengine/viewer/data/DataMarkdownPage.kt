package dev.antigravity.fluidengine.viewer.data

import android.net.Uri
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdown
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdownDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonText
import dev.antigravity.fluidengine.ui.fluid.FluidWebContent
import dev.antigravity.fluidengine.ui.fluid.FluidWebLink
import dev.antigravity.fluidengine.ui.fluid.FluidWebResponse
import dev.antigravity.fluidengine.ui.fluid.FluidWebSizing
import dev.antigravity.fluidengine.ui.fluid.icons.Code
import dev.antigravity.fluidengine.ui.fluid.icons.Description
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Image
import dev.antigravity.fluidengine.ui.fluid.rememberFluidWebContentState
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerCustomScope
import dev.antigravity.fluidengine.ui.fluid.viewer.viewerTypeActions
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.viewer.FluidTextDocument
import kotlinx.coroutines.launch
import java.util.Locale

/** Un Markdown letto: il testo, i suoi pezzi per la lista pigra, e la sorgente con i colori. */
internal class DataMarkdownModel(val name: String, val text: String, val chunks: List<String>, val source: FluidTextDocument?) {
  /** F6C.20 · I titoli per «Indice», letti una volta con il modello (sul thread di I/O dell'apertura). */
  val headings: List<DataMarkdownHeading> = DataMarkdown.headings(chunks)
}

/** F6C.20 · Un titolo di un Markdown per «Indice»: il livello (1–6), il testo senza i segni, il pezzo che lo tiene. */
internal class DataMarkdownHeading(val level: Int, val title: String, val chunk: Int)

/** I conti di un Markdown per il pannello info. */
internal class DataMarkdownStats(
  val title: String?, val headings: Int, val words: Int, val links: Int, val images: Int, val tables: Int,
  val codeBlocks: Int, val tasks: Int, val tasksDone: Int,
)

/** Le regole del Markdown della famiglia: i pezzi per una lista pigra e i conti. Kotlin puro. */
internal object DataMarkdown {
  private const val ChunkLines = 60

  /**
   * Il testo in pezzi da mostrare uno per riga di una lista pigra: un file di centinaia di KB non diventa una colonna
   * intera. Si taglia solo su una riga vuota fuori da un blocco di codice, davanti a un titolo o, dopo [ChunkLines]
   * righe, davanti a un paragrafo che non continua un elenco o una tabella.
   */
  fun chunks(text: String): List<String> {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val out = ArrayList<String>()
    var start = 0
    var fence: String? = null
    for (i in lines.indices) {
      val trimmed = lines[i].trimStart()
      if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
        val mark = trimmed.take(3)
        fence = if (fence == null) mark else if (mark == fence) null else fence
      }
      if (fence != null || lines[i].isNotBlank() || i + 1 >= lines.size) continue
      val next = lines.subList(i + 1, lines.size).firstOrNull { it.isNotBlank() } ?: continue
      val nextTrim = next.trimStart()
      val heading = nextTrim.startsWith("#")
      val continues = next.startsWith(" ") || next.startsWith("\t") || nextTrim.startsWith("|") ||
        Regex("""^([-*+]|\d+[.)])\s""").containsMatchIn(nextTrim) || nextTrim.startsWith(">")
      if (heading && i - start > 0 || i - start >= ChunkLines && !continues) {
        out += lines.subList(start, i).joinToString("\n")
        start = i + 1
      }
    }
    if (start < lines.size) out += lines.subList(start, lines.size).joinToString("\n")
    return out.filter { it.isNotBlank() }
  }

  private val HeadingLine = Regex("""^(#{1,6})\s+(.*?)(?:\s+#+)?\s*$""")

  /**
   * F6C.20 · I titoli ATX (`#` … `######`) dei [chunks], fuori dai blocchi di codice, con il pezzo che li tiene: per
   * «Indice», che porta la lista pigra a quel pezzo. [chunks] taglia davanti a un titolo che segue una riga vuota, quindi
   * quasi ogni titolo apre il suo pezzo; uno attaccato al paragrafo di sopra porta al pezzo di quel paragrafo.
   */
  fun headings(chunks: List<String>): List<DataMarkdownHeading> {
    val out = ArrayList<DataMarkdownHeading>()
    chunks.forEachIndexed { index, chunk ->
      var fence: String? = null
      for (line in chunk.lineSequence()) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
          val mark = trimmed.take(3)
          fence = if (fence == null) mark else if (mark == fence) null else fence
          continue
        }
        // Quattro spazi davanti fanno un blocco di codice, non un titolo.
        if (fence != null || line.length - trimmed.length > 3) continue
        val match = HeadingLine.matchEntire(trimmed) ?: continue
        val title = plainInline(match.groupValues[2])
        if (title.isNotEmpty()) out += DataMarkdownHeading(match.groupValues[1].length, title, index)
      }
    }
    return out
  }

  /** Il testo di un titolo senza i segni in linea: i collegamenti col loro testo, niente `**`, `__`, `~~`, `*` e accenti gravi. */
  private fun plainInline(text: String): String = text
    .replace(Regex("""!?\[([^\]]*)]\([^)]*\)"""), "$1")
    .replace("**", "").replace("__", "").replace("~~", "").replace("`", "").replace("*", "")
    .trim()

  fun stats(text: String): DataMarkdownStats {
    var headings = 0
    var title: String? = null
    var tables = 0
    var code = 0
    var tasks = 0
    var done = 0
    var inFence = false
    var previousTable = false
    for (line in text.lineSequence()) {
      val t = line.trimStart()
      if (t.startsWith("```") || t.startsWith("~~~")) {
        inFence = !inFence
        if (inFence) code++
        continue
      }
      if (inFence) continue
      if (Regex("""^#{1,6}\s""").containsMatchIn(t)) {
        headings++
        if (title == null) title = t.trimStart('#').trim()
      }
      val table = t.startsWith("|")
      if (table && !previousTable) tables++
      previousTable = table
      Regex("""^([-*+]|\d+[.)])\s+\[([ xX])]""").find(t)?.let { tasks++; if (it.groupValues[2] != " ") done++ }
    }
    val words = Regex("""[\p{L}\p{N}]+""").findAll(text.replace(Regex("""\(([^)]*)\)"""), " ")).count()
    val images = Regex("""!\[[^\]]*]\(""").findAll(text).count()
    val links = Regex("""(?<!!)\[[^\]]*]\(""").findAll(text).count() + Regex("""<https?://""").findAll(text).count()
    return DataMarkdownStats(title, headings, words, links, images, tables, code, tasks, done)
  }
}

/** Apre un collegamento della pagina: quello dell'app, o il gestore di sistema per http, https, mailto e tel. */
@Composable
internal fun rememberDataLinkOpener(settings: FluidDataSettings): (String) -> Unit {
  val handler = LocalUriHandler.current
  return remember(settings, handler) {
    { url: String ->
      val scheme = Uri.parse(url).scheme?.lowercase(Locale.ROOT)
      if (scheme in setOf("http", "https", "mailto", "tel", "geo")) {
        val custom = settings.openLink
        if (custom != null) custom(url) else try {
          handler.openUri(url)
        } catch (_: Exception) {
        }
      }
    }
  }
}

/**
 * F6C.13 · Il Markdown reso con `FluidMarkdown` e i titoli di pagina, a pezzi in una lista pigra; in basso «Reso |
 * Sorgente», e nella sorgente «Trova». Le immagini restano il loro testo alternativo e l'HTML dentro resta testo
 * (§1.3): niente si carica.
 */
@Composable
internal fun DataMarkdownPage(scope: FluidViewerCustomScope, model: DataMarkdownModel, settings: FluidDataSettings) {
  val strings = settings.strings
  var source by rememberSaveable { mutableStateOf(false) }
  var contents by rememberSaveable { mutableStateOf(false) }
  val find = remember { DataTextFind() }
  val open = rememberDataLinkOpener(settings)
  // F6C.20: la lista del reso sta qui, così «Indice» di «•••» la porta al titolo scelto anche dalla sorgente.
  val list = rememberLazyListState()
  val coroutines = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current
  DataInner(
    scope,
    find = if (source) find else null,
    actions = {
      dataModes("data.md.mode", strings.rendered to FluidIcons.Description, strings.source to FluidIcons.Code, source,
        findInSecond = true) { source = it }
      // Le azioni del tipo (F6C.19, F6C.20): l'indice dei titoli, se ce n'è uno, e il testo Markdown negli appunti.
      viewerTypeActions(strings.more) {
        listOfNotNull(
          if (model.headings.isEmpty()) null else FluidContextAction(strings.contents) { contents = true },
          FluidContextAction(strings.copyText) { clipboard.setText(AnnotatedString(model.text)) },
        )
      }
    },
  ) {
    val document = model.source
    if (source && document != null) {
      DataSourceView(document, contentPadding, find)
    } else if (model.chunks.isEmpty()) {
      Text(strings.empty, Modifier.fillMaxWidth().padding(contentPadding).padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
      val style = FluidMarkdownDefaults.style(blockSpacing = 10.dp, headings = FluidMarkdownDefaults.documentHeadings())
      LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(
          start = FluidScreenDefaults.HorizontalPadding, end = FluidScreenDefaults.HorizontalPadding,
          top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding(),
        ),
      ) {
        itemsIndexed(model.chunks, key = { i, _ -> i }) { _, chunk ->
          FluidMarkdown(chunk, Modifier.fillMaxWidth().padding(bottom = 10.dp), style = style, onLinkClick = open, selectable = true)
        }
      }
    }
    DataMarkdownContents(model, contents, strings, onDismiss = { contents = false }) { heading ->
      contents = false
      source = false
      coroutines.launch { list.scrollToItem(heading.chunk) }
    }
  }
}

/**
 * F6C.20 · «Indice» di un Markdown: un foglio con i titoli, rientrati per livello come l'indice di un libro; un tocco
 * porta il reso a quel titolo.
 */
@Composable
private fun DataMarkdownContents(
  model: DataMarkdownModel,
  visible: Boolean,
  strings: FluidDataStrings,
  onDismiss: () -> Unit,
  onPick: (DataMarkdownHeading) -> Unit,
) {
  FluidGlassSheet(
    visible = visible,
    onDismissRequest = onDismiss,
    detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
    paneTitle = strings.contents,
  ) {
    Text(
      strings.contents,
      Modifier.padding(horizontal = FluidScreenDefaults.HorizontalPadding, vertical = DataProfile.SheetTitlePadding).semantics { heading() },
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
    val top = model.headings.minOfOrNull { it.level } ?: 1
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FluidScreenDefaults.HorizontalPadding)) {
      FluidListGroup {
        model.headings.forEachIndexed { i, heading ->
          FluidListRow(
            title = heading.title,
            subtitle = "",
            modifier = Modifier.padding(start = DataProfile.HeadingIndent * (heading.level - top)),
            disclosure = false,
            onClick = { onPick(heading) },
          )
          if (i < model.headings.lastIndex) FluidListDivider()
        }
      }
    }
  }
}

/** Un HTML letto: il documento, le regole delle risorse, quante remote, e la sorgente. */
internal class DataHtmlModel(
  val name: String,
  val html: String,
  val policy: DataWeb.Policy,
  val remote: Int,
  val remoteLoader: ((Uri) -> FluidWebResponse)?,
  val source: FluidTextDocument?,
)

/**
 * F6C.13 · Una pagina HTML nella WebView chiusa di engine-ui, fra le due barre: JavaScript spento, nessuna rete; le
 * risorse relative le serve l'app (la cartella del file), quelle remote restano bloccate, con «Carica immagini» solo se
 * l'app dà il suo client. Nel tema scuro una pagina senza colori suoi prende il fondo e l'inchiostro del tema, una che
 * dichiara il suo schema lo usa, una con i suoi colori resta com'è ([DataWeb.colors]). «Reso | Sorgente».
 */
@Composable
internal fun DataHtmlPage(scope: FluidViewerCustomScope, model: DataHtmlModel, settings: FluidDataSettings) {
  val strings = settings.strings
  var source by rememberSaveable { mutableStateOf(false) }
  var remoteAllowed by rememberSaveable { mutableStateOf(false) }
  // F6C.20: il passo di «Dimensione del testo», che arriva alla WebView come zoom del testo (sopra la scala di sistema).
  var zoom by rememberSaveable { mutableIntStateOf(DataProfile.TextZoomDefault) }
  val find = remember { DataTextFind() }
  val state = rememberFluidWebContentState()
  val open = rememberDataLinkOpener(settings)
  val dark = dataDark()
  DataInner(
    scope,
    find = if (source) find else null,
    actions = {
      dataModes("data.html.mode", strings.rendered to FluidIcons.Description, strings.source to FluidIcons.Code, source,
        findInSecond = true) { source = it }
      // Le azioni del tipo (F6C.20): sul reso «Carica immagini» se l'app lo permette (era un tasto accanto a «Reso |
      // Sorgente», il quarto vetro della barra, che il visore mandava nel trabocco: R5 B11), la dimensione del testo
      // della pagina resa (dalla sorgente ci torna) e la pagina caricata da capo (la vista rifatta, le risorse di nuovo).
      viewerTypeActions(strings.more) {
        listOfNotNull(
          if (source || model.remote == 0 || model.remoteLoader == null || remoteAllowed) null
          else FluidContextAction(strings.loadImages, icon = FluidIcons.Image) {
            model.policy.remote = model.remoteLoader
            remoteAllowed = true
            state.retry()
          },
          dataTextSizeAction(strings, zoom) {
            zoom = it
            source = false
          },
          if (source) null else FluidContextAction(strings.reload) { state.retry() },
        )
      }
    },
  ) {
    val document = model.source
    if (source && document != null) {
      DataSourceView(document, contentPadding, find)
      return@DataInner
    }
    // Nel tema scuro una pagina senza colori suoi prende quelli del tema; una con i suoi colori resta com'è (DataWeb.colors).
    val scheme = MaterialTheme.colorScheme
    val (html, darkening) = remember(model.html, dark, scheme) {
      DataWeb.forTheme(model.html, dark, scheme.surface, scheme.onSurface, scheme.primary)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val top = contentPadding.calculateTopPadding()
      val bottom = contentPadding.calculateBottomPadding()
      val maxPx = with(LocalDensity.current) { 4096.toDp() }
      val height = (maxHeight - top - bottom).coerceIn(120.dp, maxPx)
      Column(Modifier.fillMaxSize().padding(top = top)) {
        if (model.remote > 0 && !remoteAllowed) {
          Text(
            strings.remoteBlocked(model.remote), Modifier.fillMaxWidth().padding(horizontal = FluidScreenDefaults.HorizontalPadding, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        FluidWebContent(
          html = html,
          onLinkClick = { link: FluidWebLink -> open(link.url) },
          modifier = Modifier.fillMaxWidth().height(height).semantics { this[DataTextZoomKey] = zoom },
          state = state,
          onRequest = model.policy::handle,
          sizing = FluidWebSizing.Viewport(height),
          darkening = darkening,
          textScale = zoom / 100f,
          placeholder = { FluidSkeletonText(lines = 8, modifier = Modifier.padding(FluidScreenDefaults.HorizontalPadding)) },
          failure = {
            DataNotice(strings.webFailed, it.toString(), Modifier.padding(FluidScreenDefaults.HorizontalPadding),
              action = if (document != null) strings.source else null, onAction = { source = true })
          },
        )
      }
    }
  }
}
