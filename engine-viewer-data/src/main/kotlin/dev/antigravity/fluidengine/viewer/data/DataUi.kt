package dev.antigravity.fluidengine.viewer.data

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFindDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSearchNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidSpinner
import dev.antigravity.fluidengine.ui.fluid.FluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.LocalFluidReduceTransparency
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.icons.ChevronLeft
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Warning
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidCustomPreset
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridPreset
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidImagePreset
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidMediaPreset
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPagesPreset
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidTextPreset
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidTextPresetDefaults
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidUnknownPreset
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerCustomScope
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerFind
import dev.antigravity.fluidengine.viewer.FluidSyntaxAnnotation
import dev.antigravity.fluidengine.viewer.FluidTextDocument
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.fluidSyntaxAnnotated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * La pagina vera di un tipo, dentro la pagina `Custom` che il telaio mostra. Le azioni e «Trova» stanno qui, non nella
 * `Custom` data al telaio: così un preset annidato (l'allegato di una mail, aperto col suo preset) può prendere la
 * barra mentre è aperto, e quando si chiude [generation] cambia e questa pagina la riprende. Il telaio ha una barra
 * sola per pagina: l'ultima che si registra vince.
 */
@Composable
internal fun DataInner(
  scope: FluidViewerCustomScope,
  generation: Int = 0,
  find: FluidViewerFind? = null,
  actions: FluidToolbarScope.() -> Unit = {},
  content: @Composable FluidViewerCustomScope.() -> Unit,
) {
  key(generation) {
    FluidCustomPreset(FluidViewerContent.Custom(find = find, actions = actions, draw = content), scope.chromeVisible)
  }
}

/**
 * F6C.20 · Il passo di «Dimensione del testo» che una pagina web (HTML, libro) dà davvero al suo contenitore, in
 * percento. Una proprietà della pagina che TalkBack non legge: le prove la guardano dove la WebView non c'è.
 */
internal val DataTextZoomKey = SemanticsPropertyKey<Int>("DataTextZoom")

/** F6C.20 · Il corpo (px) che il foglio del lettore di un libro scrive nel capitolo. Come [DataTextZoomKey]. */
internal val DataReaderFontKey = SemanticsPropertyKey<Int>("DataReaderFont")

/**
 * F6C.20 · «Dimensione del testo» in «•••»: un sottomenu con i passi di [DataProfile.TextZoomSteps], la spunta su
 * [current], e il passo di ora sotto la voce, come «Velocità» dell'audio.
 */
internal fun dataTextSizeAction(strings: FluidDataStrings, current: Int, select: (Int) -> Unit): FluidContextAction =
  FluidContextAction(
    strings.textSize, subtitle = strings.textSizeStep(current),
    children = DataProfile.TextZoomSteps.map { step ->
      FluidContextAction(strings.textSizeStep(step), selected = step == current) { select(step) }
    },
  ) {}

/** Il tema è scuro: il fondo ha poca luce. */
@Composable
internal fun dataDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** I colori dei tipi dell'albero per il tema corrente. */
@Composable
internal fun dataTypeColors(): DataTypeColors = if (dataDark()) DataTypeColors.Dark else DataTypeColors.Light

/** Vero con «Riduci trasparenza» dell'app o col contrasto alto: la tinta sparisce e le superfici sono piene (D · Mix). */
@Composable
internal fun dataSolid(): Boolean =
  LocalFluidReduceTransparency.current || FluidSystemContrast.level(LocalFluidSystemContrast.current) >= FluidSystemContrast.High

/** Il fondo leggermente tinto di una pagina a schede (calendario, contatti), o il fondo pieno del tema. */
@Composable
internal fun Modifier.dataTint(color: Color): Modifier {
  val surface = MaterialTheme.colorScheme.surface
  if (dataSolid() || color == Color.Unspecified) return background(surface)
  return background(surface).background(color.copy(alpha = DataProfile.CardTint))
}

/**
 * Il gruppo «Albero | Testo», «Reso | Sorgente» della barra in basso: due tasti nello stesso vetro, uno scelto.
 *
 * F6C.20 · Con [findInSecond] la seconda vista ha «Trova», che il telaio mette nella barra come un vetro suo: con le due
 * metà accanto i vetri erano quattro e il visore ne mandava nel trabocco (R5 B11: «•••», il centro, condividi). Lì
 * resta solo il tasto che torna alla prima vista, nello stesso vetro di «Trova» («Trova | Reso»): la metà scelta non è
 * un'azione.
 */
internal fun FluidToolbarScope.dataModes(
  id: String,
  first: Pair<String, androidx.compose.ui.graphics.vector.ImageVector>,
  second: Pair<String, androidx.compose.ui.graphics.vector.ImageVector>,
  secondSelected: Boolean,
  findInSecond: Boolean = false,
  select: (Boolean) -> Unit,
) {
  if (findInSecond && secondSelected) {
    button("$id.first", first.second, first.first, { select(false) }, placement = FluidToolbarPlacement.BottomBar)
    return
  }
  group(id, placement = FluidToolbarPlacement.BottomBar) {
    button("$id.first", first.second, first.first, { select(false) }, selected = !secondSelected)
    button("$id.second", second.second, second.first, { select(true) }, selected = secondSelected)
  }
}

/** Un avviso nella pagina (un file non valido, le risorse bloccate), con un'azione facoltativa. */
@Composable
internal fun DataNotice(
  title: String,
  message: String?,
  modifier: Modifier = Modifier,
  error: Boolean = false,
  action: String? = null,
  onAction: (() -> Unit)? = null,
) {
  val colors = dataTypeColors()
  val scheme = MaterialTheme.colorScheme
  Column(
    modifier.fillMaxWidth().background(scheme.surfaceContainerHigh, ContinuousCornerShape(FluidRadius.Card))
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FluidIcon(FluidIcons.Warning, contentDescription = null, tint = if (error) colors.error else scheme.tertiary, modifier = Modifier.size(18.dp))
      Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    }
    if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    if (action != null && onAction != null) FluidButton(action, onAction, style = FluidButtonStyle.Plain)
  }
}

/** Il caricamento di una pagina: lo spinner al centro, con il nome per TalkBack. */
@Composable
internal fun DataLoading(label: String, padding: PaddingValues) {
  Box(Modifier.fillMaxSize().padding(padding).semantics { contentDescription = label }, contentAlignment = Alignment.Center) { FluidSpinner() }
}

// --- Il testo sorgente, con i numeri di riga, i colori e «Trova» ---------------------------------------------------

/** Un'occorrenza di «Trova» nel testo sorgente. */
internal class DataMatch(val line: Int, val start: Int, val end: Int)

/** «Trova» per [DataSourceView]: la parola, le occorrenze, la corrente. Le occorrenze le calcola la vista. */
@Stable
internal class DataTextFind : FluidViewerFind {
  private var queryState by mutableStateOf("")
  var matches by mutableStateOf<List<DataMatch>>(emptyList())
  var current by mutableIntStateOf(0)
  /** Cresce a ogni richiesta di portare in vista la corrente, anche la stessa. */
  var serial by mutableIntStateOf(0)

  override val query: String get() = queryState
  override fun onQueryChange(query: String) {
    queryState = query
    current = 0
  }
  override val navigation: FluidSearchNavigation
    get() = FluidSearchNavigation(current.coerceAtMost((matches.size - 1).coerceAtLeast(0)), matches.size,
      onPrevious = { if (matches.isNotEmpty()) { current = (current - 1 + matches.size) % matches.size; serial++ } },
      onNext = { if (matches.isNotEmpty()) { current = (current + 1) % matches.size; serial++ } })

  fun found(list: List<DataMatch>) {
    matches = list
    current = 0
    serial++
  }

  companion object {
    fun matches(lines: List<String>, query: String): List<DataMatch> {
      if (query.isBlank()) return emptyList()
      val out = ArrayList<DataMatch>()
      for ((index, line) in lines.withIndex()) {
        var at = line.indexOf(query, ignoreCase = true)
        while (at >= 0 && out.size < 10_000) {
          out += DataMatch(index, at, at + query.length)
          at = line.indexOf(query, at + query.length, ignoreCase = true)
        }
      }
      return out
    }
  }
}

/**
 * Il testo di un file (la sorgente di un Markdown o di un HTML, il «Testo» di un JSON o di un XML): numeri di riga, i
 * colori di sintassi di engine-viewer con gli stili del preset testo, a capo sempre, «Trova» con le occorrenze. [mark]
 * è una riga da segnare (l'errore), [jump] una riga da portare in vista all'apertura.
 */
@Composable
internal fun DataSourceView(
  document: FluidTextDocument,
  padding: PaddingValues,
  find: DataTextFind?,
  mark: Int? = null,
  jump: Int? = null,
) {
  val list = rememberLazyListState()
  val styles = FluidTextPresetDefaults.syntaxStyles()
  val highlights = FluidFindDefaults.highlights()
  val colors = dataTypeColors()
  val scheme = MaterialTheme.colorScheme
  val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
  var viewport by remember { mutableIntStateOf(0) }
  // I colori si calcolano in avanti una volta, fuori dal thread principale.
  LaunchedEffect(document) { withContext(Dispatchers.Default) { document.syntaxSpans(0, document.lines.size) } }
  LaunchedEffect(jump, document) {
    if (jump != null && jump in document.lines.indices) list.scrollToItem(jump, -(viewport / 3))
  }
  if (find != null) {
    LaunchedEffect(find, document) {
      snapshotFlow { find.query }.collectLatest { q ->
        val found = withContext(Dispatchers.Default) { DataTextFind.matches(document.lines, q) }
        find.found(found)
      }
    }
    LaunchedEffect(find, document) {
      snapshotFlow { find.serial }.collectLatest {
        val match = find.matches.getOrNull(find.current) ?: return@collectLatest
        list.animateScrollToItem(match.line, -(viewport / 3))
      }
    }
  }
  val digits = maxOf(3, document.lines.size.toString().length)
  val gutter = (digits * 8 + 12).dp
  if (document.lines.isEmpty()) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
      Text("—", color = scheme.onSurfaceVariant)
    }
    return
  }
  LazyColumn(
    state = list,
    modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it.height },
    contentPadding = PaddingValues(
      start = FluidScreenDefaults.HorizontalPadding / 2, end = FluidScreenDefaults.HorizontalPadding,
      top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding(),
    ),
  ) {
    items(document.lines.size, key = { it }) { index ->
      val line = document.lines[index]
      val text = remember(line, index, styles, find?.matches, find?.current) {
        val spans = document.syntaxSpans(index, 1).firstOrNull().orEmpty()
        val annotated = fluidSyntaxAnnotated(line, spans)
        styled(annotated, styles, find?.matches.orEmpty(), find?.current ?: -1, index, highlights.current, highlights.others)
      }
      val marked = mark == index
      Row(
        Modifier.fillMaxWidth().then(if (marked) Modifier.background(colors.error.copy(alpha = 0.14f), ContinuousCornerShape(FluidRadius.Small)) else Modifier),
      ) {
        Text(
          "${index + 1}", Modifier.width(gutter).padding(end = 10.dp).clearAndSetSemantics {}, style = style,
          color = if (marked) colors.error else scheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1,
        )
        Text(text, Modifier.weight(1f), style = style, color = LocalContentColor.current)
      }
    }
    if (document.truncated) item(key = "truncated") {
      Text(
        "Il file continua oltre ${DataText.size(document.bytesRead)}: mostrato l'inizio",
        Modifier.fillMaxWidth().padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
      )
    }
  }
}

private fun styled(
  text: AnnotatedString,
  styles: Map<String, SpanStyle>,
  matches: List<DataMatch>,
  current: Int,
  line: Int,
  currentStyle: SpanStyle,
  otherStyle: SpanStyle,
): AnnotatedString {
  val builder = AnnotatedString.Builder(text)
  for (role in text.getStringAnnotations(FluidSyntaxAnnotation, 0, text.length)) styles[role.item]?.let { builder.addStyle(it, role.start, role.end) }
  matches.forEachIndexed { i, m ->
    if (m.line == line && m.end <= text.length) builder.addStyle(if (i == current) currentStyle else otherStyle, m.start, m.end)
  }
  return builder.toAnnotatedString()
}

// --- Il documento annidato: un allegato aperto col suo preset ------------------------------------------------------

/**
 * Un file dentro il file (l'allegato di una mail), aperto dal risolutore e mostrato **col suo preset**: un PDF a
 * pagine, un CSV in griglia, un'altra mail. Il preset prende la barra del visore mentre è aperto; il back di sistema e
 * la capsula «‹ [back]» in alto a destra tornano alla pagina di prima, che riprende la sua barra.
 */
@Composable
internal fun DataNested(scope: FluidViewerCustomScope, document: FluidViewerDocument, back: String, close: () -> Unit) {
  BackHandler(onBack = close)
  Box(Modifier.fillMaxSize()) {
    when (val content = document.content) {
      is FluidViewerContent.Pages -> FluidPagesPreset(content, scope.chromeVisible)
      is FluidViewerContent.Text -> FluidTextPreset(content)
      is FluidViewerContent.Grid -> FluidGridPreset(content, scope.chromeVisible)
      is FluidViewerContent.Custom -> FluidCustomPreset(content, scope.chromeVisible)
      is FluidViewerContent.Media -> FluidMediaPreset(content, scope.chromeVisible)
      is FluidViewerContent.Unknown -> FluidUnknownPreset(content)
      is FluidViewerContent.Image -> Box(Modifier.fillMaxSize().padding(scope.contentPadding), contentAlignment = Alignment.Center) {
        FluidImagePreset(content)
      }
      else -> Spacer(Modifier.fillMaxSize())
    }
    if (scope.chromeVisible) {
      FluidGlassButton(
        back, close,
        // In alto a destra: a sinistra c'e' gia' la pillola delle pagine del preset PDF, o la lingua del preset testo.
        Modifier.align(Alignment.TopEnd).padding(top = scope.contentPadding.calculateTopPadding(), end = FluidScreenDefaults.HorizontalPadding)
          .widthIn(max = 280.dp),
        leading = { FluidIcon(FluidIcons.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp)) },
        maxLines = 1,
      )
    }
  }
}

/** Il testo breve di un'etichetta della pagina: «Lettore» → «lettore», per le frasi. */
internal fun String.lowerFirst(): String = replaceFirstChar { it.lowercase(Locale.ITALIAN) }

/** Una riga di dettaglio di una scheda: l'etichetta piccola sopra, il valore sotto, come Contatti. */
@Composable
internal fun DataField(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
  Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyLarge, color = if (valueColor == Color.Unspecified) LocalContentColor.current else valueColor)
  }
}

/** Il titolo di una sezione di una pagina a schede. */
@Composable
internal fun DataSectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(Locale.ITALIAN), modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp).semantics { heading() },
    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** Il fondo pieno di una scheda dentro una pagina tinta. */
@Composable
internal fun Modifier.dataCard(): Modifier = background(MaterialTheme.colorScheme.surfaceContainerLowest, ContinuousCornerShape(FluidRadius.Group))

/** Il punto colorato di un tipo, come nella tavola D. */
@Composable
internal fun DataDot(color: Color, modifier: Modifier = Modifier) {
  Box(modifier.size(7.dp).background(color, FluidCapsuleShape))
}
