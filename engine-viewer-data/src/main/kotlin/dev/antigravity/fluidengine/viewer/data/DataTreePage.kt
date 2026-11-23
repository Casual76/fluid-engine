package dev.antigravity.fluidengine.viewer.data

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.icons.ChevronRight
import dev.antigravity.fluidengine.ui.fluid.icons.Code
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FormatListBulleted
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerCustomScope
import dev.antigravity.fluidengine.ui.fluid.viewer.viewerTypeActions
import dev.antigravity.fluidengine.viewer.FluidTextDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un albero letto, pronto per la pagina: il testo per «Testo» si legge solo quando serve. */
internal class DataTreeModel(
  val name: String,
  val tree: DataTree,
  val format: DataTreeFormat,
  private val loadText: suspend () -> FluidTextDocument?,
) {
  @Volatile private var text: FluidTextDocument? = null

  suspend fun text(): FluidTextDocument? = text ?: withContext(Dispatchers.IO) { loadText() }.also { text = it }

  /**
   * F6C.19 · «Espandi tutto»: i contenitori da aprire, livello per livello, finché le righe in vista (al più
   * [DataProfile.TreePage] figli per nodo, come [dataTreeRows]) restano entro [limit].
   */
  fun expandAll(limit: Int = DataProfile.ExpandAllRows): Set<Int> {
    val root = tree.root ?: return emptySet()
    val open = hashSetOf<Int>()
    var rows = 1
    var level = listOf(root)
    while (level.isNotEmpty()) {
      val next = ArrayList<DataNode>()
      for (node in level) {
        val children = node.children ?: continue
        val shown = minOf(children.size, DataProfile.TreePage)
        if (rows + shown > limit) return open
        open += node.id
        rows += shown
        for (i in 0 until shown) if (children[i].kind.container) next += children[i]
      }
      level = next
    }
    return open
  }

  /** I nodi aperti all'inizio: la radice, e i suoi figli se l'albero è piccolo (come la tavola D). */
  fun initiallyExpanded(): Set<Int> {
    val root = tree.root ?: return emptySet()
    val open = hashSetOf(root.id)
    if (tree.stats.nodes <= DataProfile.ExpandSmallTree) root.children?.filter { it.kind.container }?.forEach { open += it.id }
    return open
  }
}

/** Una riga dell'albero in vista. */
@Immutable
internal sealed interface DataTreeRow {
  val key: Any
  class Node(val node: DataNode, val depth: Int) : DataTreeRow { override val key: Any get() = node.id }
  class More(val parent: DataNode, val depth: Int, val shown: Int) : DataTreeRow { override val key: Any get() = "more-${parent.id}" }
  class Partial(val parent: DataNode, val depth: Int) : DataTreeRow { override val key: Any get() = "partial-${parent.id}" }
}

/** Le righe in vista: i nodi aperti, con al più [DataProfile.TreePage] figli alla volta («Mostra altri»). */
internal fun dataTreeRows(root: DataNode?, expanded: Set<Int>, shown: Map<Int, Int>): List<DataTreeRow> {
  val out = ArrayList<DataTreeRow>()
  fun walk(node: DataNode, depth: Int) {
    out += DataTreeRow.Node(node, depth)
    val children = node.children ?: return
    if (node.id !in expanded) return
    val limit = shown[node.id] ?: DataProfile.TreePage
    val visible = minOf(limit, children.size)
    for (i in 0 until visible) walk(children[i], depth + 1)
    if (children.size > visible) out += DataTreeRow.More(node, depth + 1, visible)
    else if (!node.complete) out += DataTreeRow.Partial(node, depth + 1)
  }
  root?.let { walk(it, 0) }
  return out
}

/**
 * F6C.13 · La pagina di un albero (JSON, JSONC, NDJSON, XML, plist), come la tavola D: righe in monospazio con il
 * chevron, il punto colorato del tipo, la chiave in grassetto e il valore nel colore del tipo; i contenitori chiusi
 * dicono quanti figli hanno e un'anteprima. In basso «Albero | Testo». Un file non valido ha in cima l'avviso con la riga
 * e la colonna dell'errore, e «Mostra nel testo» che porta alla riga segnata.
 */
@Composable
internal fun DataTreePage(scope: FluidViewerCustomScope, model: DataTreeModel, settings: FluidDataSettings) {
  val strings = settings.strings
  var text by rememberSaveable { mutableStateOf(false) }
  var jump by remember { mutableStateOf<Int?>(null) }
  val find = remember { DataTextFind() }
  // F6C.19: i nodi aperti stanno qui, così «Espandi tutto» e «Comprimi tutto» di «•••» li cambiano anche dal testo.
  val expanded = remember(model) { mutableStateMapOf<Int, Boolean>().apply { model.initiallyExpanded().forEach { put(it, true) } } }
  DataInner(
    scope,
    find = if (text) find else null,
    actions = {
      dataModes("data.tree.mode", strings.tree to FluidIcons.FormatListBulleted, strings.text to FluidIcons.Code, text) {
        text = it
        if (!it) jump = null
      }
      // Le azioni del tipo in «•••» (F6C.19), come «Espandi tutto» e «Comprimi tutto» degli ispettori: dal testo tornano
      // all'albero.
      viewerTypeActions(strings.more) {
        listOf(
          FluidContextAction(strings.expandAll) {
            val open = model.expandAll()
            expanded.keys.retainAll(open)
            open.forEach { expanded[it] = true }
            text = false
          },
          FluidContextAction(strings.collapseAll) {
            expanded.clear()
            model.tree.root?.let { expanded[it.id] = true }
            text = false
          },
        )
      }
    },
  ) {
    if (text) {
      val document by produceState<FluidTextDocument?>(null, model) { value = model.text() }
      val doc = document
      if (doc == null) DataLoading(strings.loading, contentPadding)
      else DataSourceView(doc, contentPadding, find, mark = model.tree.error?.line?.minus(1), jump = jump)
    } else {
      DataTreeView(model, expanded, contentPadding, strings) { line ->
        jump = line
        text = true
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DataTreeView(model: DataTreeModel, expanded: SnapshotStateMap<Int, Boolean>, padding: PaddingValues,
  strings: FluidDataStrings, showLine: (Int) -> Unit) {
  val tree = model.tree
  val shown = remember(model) { mutableStateMapOf<Int, Int>() }
  val rows = remember(model, expanded.toMap(), shown.toMap()) { dataTreeRows(tree.root, expanded.keys, shown) }
  val colors = dataTypeColors()
  val scheme = MaterialTheme.colorScheme
  val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 18.sp)
  val small = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
  val clipboard = LocalClipboardManager.current
  val ndjson = model.format == DataTreeFormat.Ndjson
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
    tree.error?.let { error ->
      item(key = "error") {
        DataNotice(
          strings.invalid, "Riga ${error.line}, colonna ${error.column}: ${error.message}",
          Modifier.padding(horizontal = FluidScreenDefaults.HorizontalPadding, vertical = 8.dp), error = true,
          action = strings.showInText, onAction = { showLine(error.line - 1) },
        )
      }
    }
    if (tree.root == null && tree.error == null) item(key = "empty") {
      Text(strings.empty, Modifier.fillMaxWidth().padding(24.dp), color = scheme.onSurfaceVariant)
    }
    items(rows, key = { it.key }) { row ->
      when (row) {
        is DataTreeRow.Node -> {
          val node = row.node
          val container = node.kind.container && (node.children?.isNotEmpty() == true || !node.complete)
          val open = expanded[node.id] == true
          val line = remember(node, colors, ndjson && row.depth == 0) { treeLine(node, colors, scheme.onSurfaceVariant, ndjson && row.depth == 0) }
          val description = remember(node) { describe(node) }
          Row(
            Modifier.fillMaxWidth().heightIn(min = DataProfile.TreeRowHeight).drawBehind {
              val stroke = .7.dp.toPx()
              drawLine(scheme.outlineVariant.copy(alpha = 0.6f), Offset(0f, size.height - stroke / 2),
                Offset(size.width, size.height - stroke / 2), strokeWidth = stroke)
            }
              .combinedClickable(
                enabled = true,
                role = if (container) Role.Button else null,
                onClickLabel = if (container) (if (open) "Chiudi" else "Apri") else null,
                onLongClickLabel = "Copia valore",
                onLongClick = { clipboard.setText(AnnotatedString(copyText(node))) },
                onClick = { if (container) { if (open) expanded.remove(node.id) else expanded[node.id] = true } },
              )
              .semantics {
                contentDescription = description
                if (container) stateDescription = if (open) "aperto" else "chiuso"
              }
              .padding(start = 14.dp + DataProfile.TreeIndent * row.depth, end = FluidScreenDefaults.HorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(Modifier.size(13.dp), contentAlignment = Alignment.Center) {
              if (container) FluidIcon(FluidIcons.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp).rotate(if (open) 90f else 0f))
            }
            Spacer(Modifier.width(6.dp))
            DataDot(dotColor(node, colors))
            Spacer(Modifier.width(6.dp))
            // La riga la legge TalkBack dalla sua descrizione («nome, stringa, Fluid Glass»): il testo non si ripete.
            Text(line, style = mono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clearAndSetSemantics {})
          }
        }
        is DataTreeRow.More -> {
          val total = row.parent.childCount
          Text(
            strings.showMore(row.shown, total),
            Modifier.fillMaxWidth().heightIn(min = DataProfile.TreeRowHeight)
              .combinedClickable(role = Role.Button, onClick = { shown[row.parent.id] = row.shown + DataProfile.TreePage })
              .padding(start = 12.dp + DataProfile.TreeIndent * row.depth + 28.dp, top = 10.dp, bottom = 10.dp),
            style = small, color = scheme.primary,
          )
        }
        is DataTreeRow.Partial -> Text(
          if (tree.truncated) strings.truncated(DataProfile.MaxTreeNodes) else "… il resto non si legge",
          Modifier.fillMaxWidth().padding(start = 12.dp + DataProfile.TreeIndent * row.depth + 28.dp, top = 10.dp, bottom = 10.dp),
          style = small, color = scheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun dotColor(node: DataNode, c: DataTypeColors) = when (node.kind) {
  DataKind.String, DataKind.Text -> c.string
  DataKind.Number -> c.number
  DataKind.Bool -> c.bool
  DataKind.Null -> c.nil
  DataKind.Date -> c.date
  DataKind.Data -> c.data
  DataKind.Element -> if (node.value != null) c.string else c.element
  DataKind.Attribute -> c.attribute
  DataKind.Comment, DataKind.Instruction -> c.comment
  DataKind.Object, DataKind.Array -> c.container
}

/** La riga di un nodo: `chiave : valore` nei colori del tipo, `chiave : { } 7 chiavi  anteprima` per un contenitore. */
private fun treeLine(node: DataNode, c: DataTypeColors, muted: androidx.compose.ui.graphics.Color, ndjsonRoot: Boolean): AnnotatedString = buildAnnotatedString {
  val key = when (node.kind) {
    DataKind.Element -> node.name
    DataKind.Attribute -> "@${node.name}"
    DataKind.Text -> null
    DataKind.Comment -> null
    DataKind.Instruction -> null
    else -> node.name
  }
  if (key != null) {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(key) }
    withStyle(SpanStyle(color = muted)) { append(" : ") }
  }
  when (node.kind) {
    DataKind.Object, DataKind.Array -> {
      withStyle(SpanStyle(color = muted)) { append(if (node.kind == DataKind.Object) "{ }" else "[ ]") }
      withStyle(SpanStyle(color = muted, fontSize = 12.sp)) {
        append("  ")
        append(if (ndjsonRoot) "${DataText.count(node.childCount)} righe" else node.summary())
        node.preview()?.let { append("  ·  $it") }
      }
    }
    DataKind.Element -> {
      val value = node.value
      if (value != null) withStyle(SpanStyle(color = c.string)) { append("\"${value.replace('\n', ' ')}\"") }
      else withStyle(SpanStyle(color = muted)) {
        append("< >  ${node.summary()}")
        node.preview()?.let { append("  ·  $it") }
      }
    }
    DataKind.String -> withStyle(SpanStyle(color = c.string)) { append("\"${node.value.orEmpty().replace('\n', ' ')}\"") }
    DataKind.Text -> withStyle(SpanStyle(color = c.string)) { append("\"${node.value.orEmpty().replace('\n', ' ')}\"") }
    DataKind.Attribute -> withStyle(SpanStyle(color = c.attribute)) { append("\"${node.value.orEmpty()}\"") }
    DataKind.Number -> withStyle(SpanStyle(color = c.number)) { append(node.value.orEmpty()) }
    DataKind.Bool -> withStyle(SpanStyle(color = c.bool)) { append(node.value.orEmpty()) }
    DataKind.Null -> withStyle(SpanStyle(color = c.nil)) { append(node.value.orEmpty()) }
    DataKind.Date -> withStyle(SpanStyle(color = c.date)) { append(node.value.orEmpty()) }
    DataKind.Data -> withStyle(SpanStyle(color = c.data)) { append(node.value.orEmpty()) }
    DataKind.Comment -> withStyle(SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)) { append("<!-- ${node.value.orEmpty().replace('\n', ' ')} -->") }
    DataKind.Instruction -> withStyle(SpanStyle(color = c.comment)) { append("<?${node.value.orEmpty()}?>") }
  }
}

/** Cosa legge TalkBack: «nome, stringa, Fluid Glass»; «moduli, elenco di 6 elementi». */
private fun describe(node: DataNode): String {
  val type = when (node.kind) {
    DataKind.Object -> "oggetto, " + node.summary()
    DataKind.Array -> "elenco, " + node.summary()
    DataKind.String -> "stringa"
    DataKind.Number -> "numero"
    DataKind.Bool -> "vero o falso"
    DataKind.Null -> "null"
    DataKind.Date -> "data"
    DataKind.Data -> "dati"
    DataKind.Element -> "elemento, " + if (node.value != null) "testo" else node.summary()
    DataKind.Attribute -> "attributo"
    DataKind.Text -> "testo"
    DataKind.Comment -> "commento"
    DataKind.Instruction -> "istruzione"
  }
  return listOfNotNull(node.name, type, node.value?.take(200)).joinToString(", ")
}

/** Il valore da copiare con la pressione lunga: il testo del valore, o il sottoalbero in JSON. */
private fun copyText(node: DataNode): String = if (node.kind.container && node.value == null) DataTreeText.render(node) else node.value.orEmpty()
