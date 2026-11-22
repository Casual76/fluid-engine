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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdown
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdownDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
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
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerCustomScope
import dev.antigravity.fluidengine.viewer.FluidTextDocument
import java.util.Locale

/** Un Markdown letto: il testo, i suoi pezzi per la lista pigra, e la sorgente con i colori. */
internal class DataMarkdownModel(val name: String, val text: String, val chunks: List<String>, val source: FluidTextDocument?)

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
  val find = remember { DataTextFind() }
  val open = rememberDataLinkOpener(settings)
  DataInner(
    scope,
    find = if (source) find else null,
    actions = { dataModes("data.md.mode", strings.rendered to FluidIcons.Description, strings.source to FluidIcons.Code, source) { source = it } },
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
 * l'app dà il suo client. In tema scuro la pagina si scurisce (lo scurimento algoritmico di WebView). «Reso | Sorgente».
 */
@Composable
internal fun DataHtmlPage(scope: FluidViewerCustomScope, model: DataHtmlModel, settings: FluidDataSettings) {
  val strings = settings.strings
  var source by rememberSaveable { mutableStateOf(false) }
  var remoteAllowed by rememberSaveable { mutableStateOf(false) }
  val find = remember { DataTextFind() }
  val state = rememberFluidWebContentState()
  val open = rememberDataLinkOpener(settings)
  val dark = dataDark()
  DataInner(
    scope,
    find = if (source) find else null,
    actions = {
      dataModes("data.html.mode", strings.rendered to FluidIcons.Description, strings.source to FluidIcons.Code, source) { source = it }
      if (!source && model.remote > 0 && model.remoteLoader != null && !remoteAllowed) {
        button("data.html.images", FluidIcons.Image, strings.loadImages, {
          model.policy.remote = model.remoteLoader
          remoteAllowed = true
          state.retry()
        }, placement = FluidToolbarPlacement.BottomBar)
      }
    },
  ) {
    val document = model.source
    if (source && document != null) {
      DataSourceView(document, contentPadding, find)
      return@DataInner
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
          html = model.html,
          onLinkClick = { link: FluidWebLink -> open(link.url) },
          modifier = Modifier.fillMaxWidth().height(height),
          state = state,
          onRequest = model.policy::handle,
          sizing = FluidWebSizing.Viewport(height),
          darkening = dark,
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
