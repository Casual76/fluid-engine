package dev.antigravity.fluidengine.ui.fluid

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Le parole del renderer: l'etichetta del blocco di codice e il suo tasto. Come le altre
 * `Fluid*Strings`: un'app tradotta le passa tutte.
 */
@Immutable
data class FluidMarkdownStrings(
  /** L'etichetta di un blocco di codice senza linguaggio. */
  val codeLabel: String = "codice",
  val copy: String = "copia",
  val copied: String = "copiato",
  val copyDescription: String = "Copia il codice",
  /** Cosa legge TalkBack per un blocco di codice; `%1$s` e' il linguaggio o [codeLabel]. */
  val codeBlockDescription: String = "Blocco di %1\$s",
  /** Il segno di una voce da fare, fatta e no (3.0.1), per TalkBack. */
  val taskDone: String = "Fatto",
  val taskOpen: String = "Da fare",
)

/**
 * Colori e misure del testo Markdown. Di serie vengono dal tema ([FluidMarkdownDefaults.style]):
 * chi vuole il testo su un altro fondo (sopra il cielo di un onboarding, dentro una bolla colorata)
 * cambia [textColor] e [mutedColor] e il resto segue.
 *
 * Di serie due misure di titolo e non sei: in una risposta un titolo separa i paragrafi, non
 * costruisce una gerarchia. I default del Markdown da pagina (`displayLarge` per `#`) in mezzo a una
 * chat sono un cartello: "## Estate" a 45 sp. Una nota o una pagina di testo vuole invece la
 * gerarchia, e [headings] da' una misura a ogni livello ([FluidMarkdownDefaults.documentHeadings]).
 */
@Immutable
data class FluidMarkdownStyle(
  val body: TextStyle,
  /** `#` e `##`. */
  val heading: TextStyle,
  /** Dal `###` in giu'. */
  val subheading: TextStyle,
  val code: TextStyle,
  val textColor: Color,
  val mutedColor: Color,
  val linkColor: Color,
  /** Il pallino e il numero degli elenchi. */
  val markerColor: Color,
  /** Il fondo del codice e delle tabelle. */
  val codeBackground: Color,
  val quoteBar: Color,
  val blockSpacing: Dp = 8.dp,
  /**
   * Le misure dei titoli livello per livello: la prima e' per `#`, la seconda per `##`, e cosi' via;
   * l'ultima vale anche per i livelli piu' profondi. Vuota (di serie), `#` e `##` sono [heading] e dal
   * `###` in giu' e' [subheading].
   */
  val headings: List<TextStyle> = emptyList(),
) {
  /** La misura del titolo di [level] (da 1 a 6). */
  fun headingFor(level: Int): TextStyle = when {
    headings.isNotEmpty() -> headings[(level - 1).coerceIn(0, headings.lastIndex)]
    level <= 2 -> heading
    else -> subheading
  }
}

object FluidMarkdownDefaults {
  /**
   * Lo stile del tema corrente.
   *
   * @param body la misura del corpo: di serie `bodyLarge`, una scheda piccola vuole `bodyMedium`.
   * @param headings le misure dei titoli livello per livello (vedi [FluidMarkdownStyle.headings]);
   *   vuota, i titoli sono due sole misure.
   */
  @Composable
  fun style(
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    mutedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    blockSpacing: Dp = 8.dp,
    body: TextStyle = MaterialTheme.typography.bodyLarge,
    headings: List<TextStyle> = emptyList(),
  ): FluidMarkdownStyle {
    val typography = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    return FluidMarkdownStyle(
      body = body,
      heading = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      subheading = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
      code = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
      textColor = textColor,
      mutedColor = mutedColor,
      linkColor = scheme.primary,
      markerColor = scheme.primary,
      codeBackground = scheme.surfaceVariant.copy(alpha = 0.6f),
      quoteBar = scheme.outlineVariant,
      blockSpacing = blockSpacing,
      headings = headings,
    )
  }

  /**
   * I titoli di una pagina di testo, una nota o un changelog: quattro misure in semigrassetto, da
   * `titleLarge` per `#` a `bodyLarge` dal `####`. Si passa a [style] come `headings`.
   */
  @Composable
  fun documentHeadings(): List<TextStyle> {
    val typography = MaterialTheme.typography
    return listOf(
      typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
      typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
      typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
    )
  }
}

/**
 * Il Markdown, disegnato: la risposta di un assistente come una nota, le novita' di una versione, il
 * testo di una guida. Titoli, elenchi (anche annidati), corsivo, grassetto, barrato, codice in linea e
 * a blocco (con la lingua in testa e il tasto "copia", il testo che scorre invece di essere
 * tagliato), link, citazioni, tabelle che scorrono, righe. Niente libreria esterna, niente
 * dipendenza dall'assistente: il parser e' [FluidMarkdownParser], puro Kotlin.
 *
 * **Un solo disegno per il testo che arriva e per quello arrivato.** Con [streaming] le parole nuove
 * sfumano dentro ([FluidRevealingText]); a risposta finita [streaming] torna falso e il nodo e' lo
 * stesso: non c'e' un secondo resa che prenda il posto del primo, quindi non c'e' un salto di misure
 * (PampAI ne aveva due, e un passaggio fra l'uno e l'altro da far sembrare uguali). Gli stili sono
 * quelli del testo in linea gia' a meta': `**` aperto resta `**` finche' il modello non lo chiude.
 * Un testo che non arriva mai a pezzi (una nota) lascia [streaming] falso e non ha nessuna sfumatura.
 *
 * @param streaming il testo sta ancora arrivando: le parole nuove sfumano dentro.
 * @param animateFirst anche il primo testo si rivela (la risposta e' appena nata); falso per una
 *   risposta vecchia riaperta, che e' gia' letta e non si anima.
 * @param onLinkClick cosa fa un tocco su un link; null apre l'indirizzo col gestore di sistema.
 * @param selectable il testo si puo' selezionare e copiare (una nota, un changelog). Di serie no: in una
 *   card o in una bolla la selezione si prenderebbe il tocco lungo che e' del contenitore.
 */
@Composable
fun FluidMarkdown(
  markdown: String,
  modifier: Modifier = Modifier,
  streaming: Boolean = false,
  animateFirst: Boolean = streaming,
  style: FluidMarkdownStyle = FluidMarkdownDefaults.style(),
  strings: FluidMarkdownStrings = FluidMarkdownStrings(),
  options: FluidMarkdownOptions = FluidMarkdownOptions(),
  onLinkClick: ((String) -> Unit)? = null,
  selectable: Boolean = false,
) {
  val blocks = remember(markdown, options) { FluidMarkdownParser.parse(markdown, options) }
  val reveal = RevealMode(streaming = streaming, animateFirst = animateFirst)
  val firstCount = remember { blocks.size }
  if (selectable) {
    SelectionContainer(modifier) { MarkdownBlocks(blocks, firstCount, reveal, style, strings, onLinkClick, Modifier) }
  } else {
    MarkdownBlocks(blocks, firstCount, reveal, style, strings, onLinkClick, modifier)
  }
}

@Composable
private fun MarkdownBlocks(
  blocks: List<FluidMdBlock>,
  firstCount: Int,
  reveal: RevealMode,
  style: FluidMarkdownStyle,
  strings: FluidMarkdownStrings,
  onLinkClick: ((String) -> Unit)?,
  modifier: Modifier,
) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(style.blockSpacing)) {
    blocks.forEachIndexed { index, block ->
      key(index) {
        // I blocchi che c'erano al primo passaggio sono gia' letti; quelli che si aprono dopo,
        // mentre il testo arriva, si rivelano parola per parola.
        FluidMarkdownBlock(block, style, strings, reveal.forBlock(fresh = index >= firstCount), onLinkClick)
      }
    }
  }
}

/** Come si rivela un blocco: se il testo sta arrivando, e se *questo* blocco e' nato adesso. */
private data class RevealMode(val streaming: Boolean, val animateFirst: Boolean, val fresh: Boolean = false) {
  fun forBlock(fresh: Boolean) = copy(fresh = fresh)

  /** Vero se le parole di questo blocco sfumano dentro: sta arrivando, ed e' nuovo o il testo e' tutto nato adesso. */
  val animateThis: Boolean get() = streaming && (animateFirst || fresh)
}

@Composable
private fun FluidMarkdownBlock(
  block: FluidMdBlock,
  style: FluidMarkdownStyle,
  strings: FluidMarkdownStrings,
  reveal: RevealMode,
  onLinkClick: ((String) -> Unit)?,
) {
  when (block) {
    is FluidMdBlock.Paragraph -> MarkdownText(block.inlines, style.body, style, reveal, onLinkClick)
    is FluidMdBlock.Heading -> MarkdownText(
      block.inlines,
      style.headingFor(block.level),
      style,
      reveal,
      onLinkClick,
    )
    is FluidMdBlock.ListBlock -> MarkdownList(block, 0, style, strings, reveal, onLinkClick)
    is FluidMdBlock.CodeBlock -> MarkdownCode(block, style, strings, reveal)
    is FluidMdBlock.Quote -> {
      val bar = style.quoteBar
      Column(
        Modifier
          .padding(start = 12.dp)
          .drawBehind {
            drawRoundRect(bar, Offset(-12.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(1.5.dp.toPx()))
          },
        verticalArrangement = Arrangement.spacedBy(style.blockSpacing),
      ) {
        val muted = style.copy(textColor = style.mutedColor)
        block.blocks.forEachIndexed { index, inner -> key(index) { FluidMarkdownBlock(inner, muted, strings, reveal, onLinkClick) } }
      }
    }
    is FluidMdBlock.Table -> MarkdownTable(block, style, onLinkClick)
    FluidMdBlock.Rule -> FluidHairline(Modifier.padding(vertical = 4.dp))
  }
}

@Composable
private fun MarkdownText(
  inlines: List<FluidMdInline>,
  textStyle: TextStyle,
  style: FluidMarkdownStyle,
  reveal: RevealMode,
  onLinkClick: ((String) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val code = style.code.toSpanStyle().copy(background = style.codeBackground)
  val annotated = remember(inlines, style.linkColor, code, onLinkClick) {
    FluidMarkdownText.annotate(inlines, linkColor = style.linkColor, code = code, onLinkClick = onLinkClick)
  }
  FluidRevealingText(
    text = annotated,
    style = textStyle,
    color = style.textColor,
    modifier = modifier,
    animateFirst = reveal.animateThis,
    reveal = reveal.streaming,
  )
}

/** Un elenco: il segno (pallino o numero) a sinistra, il testo a destra, i figli rientrati sotto la voce. */
@Composable
private fun MarkdownList(
  list: FluidMdBlock.ListBlock,
  depth: Int,
  style: FluidMarkdownStyle,
  strings: FluidMarkdownStrings,
  reveal: RevealMode,
  onLinkClick: ((String) -> Unit)?,
) {
  val firstItems = remember { list.items.size }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    list.items.forEachIndexed { index, item ->
      key(index) {
        val itemReveal = reveal.copy(fresh = reveal.fresh || index >= firstItems)
        Row(verticalAlignment = Alignment.Top) {
          val checked = item.checked
          if (checked != null) {
            // Una voce da fare (3.0.1): la casella al posto del pallino, piena se fatta. Disegnata, non un
            // carattere: una casella che non c'e' nel font diventa un quadratino vuoto che non dice niente.
            Box(modifier = Modifier.width(if (list.ordered) 26.dp else 18.dp), contentAlignment = Alignment.TopEnd) {
              Icon(
                imageVector = if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = if (checked) strings.taskDone else strings.taskOpen,
                tint = style.markerColor,
                modifier = Modifier.padding(top = 2.dp).size(18.dp),
              )
            }
          } else {
            Text(
              text = if (list.ordered) "${list.start + index}." else if (depth == 0) "•" else "–",
              style = style.body,
              color = style.markerColor,
              textAlign = TextAlign.End,
              modifier = Modifier.width(if (list.ordered) 26.dp else 18.dp),
            )
          }
          Column(Modifier.padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MarkdownText(item.inlines, style.body, style, itemReveal, onLinkClick)
            item.children.forEachIndexed { childIndex, child ->
              key(childIndex) {
                if (child is FluidMdBlock.ListBlock) {
                  MarkdownList(child, depth + 1, style, strings, itemReveal, onLinkClick)
                } else {
                  FluidMarkdownBlock(child, style, strings, itemReveal, onLinkClick)
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Un blocco di codice come lo si vuole in una chat: lingua in testa, tasto copia, e il testo che
 * **scorre** invece di essere tagliato. Una riga di codice tagliata a meta' non e' codice, e'
 * un'immagine di codice: qui la riga resta intera (`softWrap = false`) dentro uno scorrimento
 * orizzontale suo.
 */
@Composable
private fun MarkdownCode(block: FluidMdBlock.CodeBlock, style: FluidMarkdownStyle, strings: FluidMarkdownStrings, reveal: RevealMode) {
  val body = block.code
  val label = block.language ?: strings.codeLabel
  val context = LocalContext.current
  var copied by remember(body) { mutableStateOf(false) }
  LaunchedEffect(copied) {
    if (copied) {
      delay(1_600)
      copied = false
    }
  }
  Column(
    Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp)
      .background(style.codeBackground, ContinuousCornerShape(FluidRadius.Control)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 14.dp, end = 6.dp, top = 6.dp)
        .height(32.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label, style = MaterialTheme.typography.labelSmall, color = style.mutedColor, modifier = Modifier.weight(1f))
      // La copia di un codice che sta ancora arrivando copierebbe mezza funzione: compare alla chiusura.
      if (block.closed || !reveal.streaming) {
        Row(
          modifier = Modifier
            .fluidPressable(
              onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, body))
                copied = true
              },
              role = Role.Button,
              haptic = null,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            contentDescription = strings.copyDescription,
            tint = style.mutedColor,
            modifier = Modifier.size(14.dp),
          )
          Text(if (copied) strings.copied else strings.copy, style = MaterialTheme.typography.labelSmall, color = style.mutedColor)
        }
      }
    }
    FluidRevealingText(
      text = AnnotatedString(body),
      style = style.code,
      color = style.textColor,
      softWrap = false,
      animateFirst = reveal.animateThis,
      reveal = reveal.streaming,
      modifier = Modifier
        .horizontalScroll(rememberScrollState())
        .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp),
    )
  }
}

/**
 * Una tabella che scorre invece di essere tagliata: le colonne di una tabella a quattro o cinque
 * voci, divise la larghezza dello schermo, restano larghe un pollice e il contenuto sparisce nei
 * puntini. Qui la tabella prende quello che le serve ([ColumnWidth] a colonna, mai meno dello
 * schermo) e lo schermo ci scorre sopra. L'intestazione e' in grassetto, le righe separate da un filo.
 */
@Composable
private fun MarkdownTable(table: FluidMdBlock.Table, style: FluidMarkdownStyle, onLinkClick: ((String) -> Unit)?) {
  val columns = table.columns.coerceAtLeast(1)
  val code = style.code.toSpanStyle().copy(background = style.codeBackground)
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val wanted = maxOf(maxWidth, ColumnWidth * columns)
    Box(
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .background(style.codeBackground, ContinuousCornerShape(FluidRadius.Control)),
    ) {
      Column(Modifier.width(wanted).padding(vertical = 4.dp)) {
        TableRow(table.header, table.aligns, columns, header = true, style, code, onLinkClick)
        table.rows.forEachIndexed { index, cells ->
          FluidHairline(Modifier.padding(horizontal = 10.dp), color = style.mutedColor.copy(alpha = 0.25f))
          key(index) { TableRow(cells, table.aligns, columns, header = false, style, code, onLinkClick) }
        }
      }
    }
  }
}

@Composable
private fun TableRow(
  cells: List<List<FluidMdInline>>,
  aligns: List<FluidMdAlign>,
  columns: Int,
  header: Boolean,
  style: FluidMarkdownStyle,
  code: SpanStyle,
  onLinkClick: ((String) -> Unit)?,
) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
    for (column in 0 until columns) {
      val cell = cells.getOrNull(column).orEmpty()
      val annotated = remember(cell, header, style.linkColor, code, onLinkClick) {
        val base = FluidMarkdownText.annotate(cell, linkColor = style.linkColor, code = code, onLinkClick = onLinkClick)
        if (header) AnnotatedString.Builder().apply { pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold)); append(base) }.toAnnotatedString() else base
      }
      Text(
        text = annotated,
        style = style.body.copy(fontSize = style.code.fontSize),
        color = style.textColor,
        textAlign = when (aligns.getOrNull(column)) {
          FluidMdAlign.Center -> TextAlign.Center
          FluidMdAlign.End -> TextAlign.End
          else -> TextAlign.Start
        },
        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
      )
    }
  }
}

/** Quanto e' larga, al minimo, una colonna di una tabella. */
private val ColumnWidth = 128.dp

/**
 * Dal testo in linea a un `AnnotatedString`: separato dalla resa perche' non vuole il tema e si
 * prova sul computer (la lunghezza, gli intervalli degli stili, i link).
 */
internal object FluidMarkdownText {

  fun annotate(
    inlines: List<FluidMdInline>,
    linkColor: Color,
    code: SpanStyle,
    onLinkClick: ((String) -> Unit)? = null,
  ): AnnotatedString = buildAnnotatedString {
    fun add(items: List<FluidMdInline>) {
      for (item in items) when (item) {
        is FluidMdInline.Text -> append(item.text)
        is FluidMdInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { add(item.children) }
        is FluidMdInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { add(item.children) }
        is FluidMdInline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { add(item.children) }
        is FluidMdInline.Code -> withStyle(code) { append(item.text) }
        is FluidMdInline.Link -> {
          val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
          val link = LinkAnnotation.Url(
            url = item.url,
            styles = styles,
            linkInteractionListener = onLinkClick?.let { callback -> LinkInteractionListener { callback(item.url) } },
          )
          withLink(link) { add(item.children) }
        }
      }
    }
    add(inlines)
  }
}
