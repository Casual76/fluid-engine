package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import java.text.BreakIterator

/**
 * Un paragrafo lungo chiuso a [collapsedMaxLines] righe, con «altro» in fondo all'ultima riga: un tocco lo apre sul posto
 * (F8.1, `testo-espandibile`), come le descrizioni e le novità dell'App Store. In SwiftUI non c'è un componente: l'App
 * Store lo fa con `lineLimit` e un tasto in coda alla riga.
 *
 * - **«altro» sta nell'ultima riga**, dopo «…», senza coprire il testo: il taglio si misura sul layout vero, una volta
 *   per larghezza, testo e stile (non a ogni scorrimento).
 * - **L'apertura è fluida**: l'altezza cresce con una molla senza rimbalzo ([FluidTextMotion.ExpandResponseSeconds]) e il
 *   testo nuovo **non arriva tutto insieme**: riga per riga, sfocato, che si mette a fuoco ([FluidTextArrival.Lines]), lo
 *   stesso arrivo del testo che genera PampAI (la nota di Alessio).
 * - **I link restano link**: un tocco su un link apre il link, non il paragrafo.
 * - **Lo stato si salva** (rotazione, ritorno alla pagina), e TalkBack ha l'azione «Espandi» sul paragrafo.
 * - **Col movimento ridotto** il paragrafo si apre di colpo e il testo compare intero, senza sfocatura.
 *
 * Come nell'App Store, aperto resta aperto; [collapsible] permette di richiuderlo con un altro tocco.
 *
 * @param moreLabel la parola in fondo alla riga: di serie «altro» ([FluidLiveTextStrings.more]).
 * @param moreColor il colore di «altro»: l'accento, come il tasto dell'App Store.
 * @param onExpandedChange chiamata quando l'utente apre (o richiude) il paragrafo.
 */
@Composable
fun FluidExpandableText(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  collapsedMaxLines: Int = 3,
  style: TextStyle = MaterialTheme.typography.bodyLarge,
  color: Color = MaterialTheme.colorScheme.onSurface,
  moreLabel: String = LocalFluidLabels.current.liveText.more,
  moreColor: Color = MaterialTheme.colorScheme.primary,
  collapsible: Boolean = false,
  arrival: FluidTextArrival = FluidTextArrival.Lines,
  onExpandedChange: ((Boolean) -> Unit)? = null,
) {
  require(collapsedMaxLines >= 1) { "Almeno una riga: $collapsedMaxLines" }
  var expanded by rememberSaveable { mutableStateOf(false) }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val words = LocalFluidLabels.current.liveText
  val measurer = rememberTextMeasurer(cacheSize = 4)
  val change by rememberUpdatedState(onExpandedChange)
  BoxWithConstraints(
    modifier.animateContentSize(
      if (reduced) snap() else spring(
        FluidMotion.DampingChrome,
        FluidMotion.stiffnessForResponse(FluidTextMotion.ExpandResponseSeconds),
        IntSize.VisibilityThreshold,
      ),
    ),
  ) {
    val width = constraints.maxWidth
    val plan = remember(text, style, width, collapsedMaxLines, moreLabel, moreColor, measurer) {
      FluidExpandablePlan.of(measurer, text, style, width, collapsedMaxLines, moreLabel, moreColor)
    }
    val open = expanded || !plan.truncated
    val shown = if (open) text else plan.collapsed
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    val canToggle = plan.truncated && (!expanded || collapsible)
    val toggle: () -> Unit = {
      if (canToggle) {
        expanded = !expanded
        change?.invoke(expanded)
      }
    }
    FluidRevealingTextCore(
      text = shown,
      style = style,
      color = color,
      modifier = Modifier
        // Le chiavi sono il testo mostrato: aperto e chiuso i link stanno in posti diversi.
        .pointerInput(canToggle, shown) {
          if (!canToggle) return@pointerInput
          detectTapGestures { position ->
            // Un tocco su un link è del link (lo apre il testo): il paragrafo non si apre.
            val result = layout[0]
            if (result != null && hasLinkAt(result, shown, position)) return@detectTapGestures
            toggle()
          }
        }
        .semantics {
          if (plan.truncated) {
            stateDescription = if (open) words.expanded else words.collapsed
            if (canToggle) onClick(label = if (open) words.collapse else words.expand) { toggle(); true }
          }
        },
      maxLines = Int.MAX_VALUE,
      overflow = TextOverflow.Clip,
      onTextLayout = { layout[0] = it },
      revealMillis = FluidTextMotion.LineArrivalMillis,
      rewriteMillis = FluidTextMotion.LineArrivalMillis,
      softWrap = true,
      animateFirst = false,
      reveal = true,
      arrival = arrival,
      rewriteArrives = true,
    )
  }
}

/** Lo stesso paragrafo da un testo semplice. */
@Composable
fun FluidExpandableText(
  text: String,
  modifier: Modifier = Modifier,
  collapsedMaxLines: Int = 3,
  style: TextStyle = MaterialTheme.typography.bodyLarge,
  color: Color = MaterialTheme.colorScheme.onSurface,
  moreLabel: String = LocalFluidLabels.current.liveText.more,
  moreColor: Color = MaterialTheme.colorScheme.primary,
  collapsible: Boolean = false,
  arrival: FluidTextArrival = FluidTextArrival.Lines,
  onExpandedChange: ((Boolean) -> Unit)? = null,
) {
  FluidExpandableText(
    text = remember(text) { AnnotatedString(text) },
    modifier = modifier,
    collapsedMaxLines = collapsedMaxLines,
    style = style,
    color = color,
    moreLabel = moreLabel,
    moreColor = moreColor,
    collapsible = collapsible,
    arrival = arrival,
    onExpandedChange = onExpandedChange,
  )
}

private fun hasLinkAt(layout: TextLayoutResult, text: AnnotatedString, position: Offset): Boolean {
  if (text.isEmpty()) return false
  val offset = layout.getOffsetForPosition(position)
  return text.getLinkAnnotations(offset, offset + 1).isNotEmpty()
}

/**
 * Il taglio del paragrafo chiuso, puro sul layout (`FluidExpandableTextTest`): [collapsed] è il testo fino a [cut], poi
 * «…», uno spazio e «altro», e sta in [lines] righe.
 */
internal class FluidExpandablePlan(
  val truncated: Boolean,
  val collapsed: AnnotatedString,
  val cut: Int,
  val lines: Int,
) {
  companion object {
    const val Ellipsis = "…"

    fun of(
      measurer: TextMeasurer,
      text: AnnotatedString,
      style: TextStyle,
      width: Int,
      maxLines: Int,
      more: String,
      moreColor: Color,
    ): FluidExpandablePlan {
      val constraints = Constraints(maxWidth = width.coerceAtLeast(1))
      val full = measurer.measure(text, style, constraints = constraints)
      if (full.lineCount <= maxLines) return FluidExpandablePlan(false, text, text.length, full.lineCount)
      val lastLine = maxLines - 1
      val suffix = suffix(more, moreColor, style)
      val suffixWidth = measurer.measure(suffix, style, softWrap = false, maxLines = 1).size.width
      val lineStart = full.getLineStart(lastLine)
      val lineEnd = full.getLineEnd(lastLine, visibleEnd = true)
      val y = (full.getLineTop(lastLine) + full.getLineBottom(lastLine)) / 2f
      val rtl = full.getParagraphDirection(lineStart) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl
      val room = if (rtl) suffixWidth.toFloat() else (width - suffixWidth).toFloat()
      // Si taglia fra due grafemi, mai dentro uno (R2): un emoji e' una coppia di Char, una lettera con l'accento unito due
      // o piu'; tagliare a meta' lasciava un carattere rotto prima di «… altro».
      val graphemes = BreakIterator.getCharacterInstance().apply { setText(text.text) }
      fun boundaryAtOrBefore(offset: Int): Int =
        if (offset <= 0 || offset >= text.length || graphemes.isBoundary(offset)) offset
        else graphemes.preceding(offset).let { if (it == BreakIterator.DONE) 0 else it }
      var cut = boundaryAtOrBefore(full.getOffsetForPosition(Offset(room.coerceAtLeast(0f), y)).coerceIn(lineStart, lineEnd))
      // Indietro fino a che il paragrafo chiuso sta nelle sue righe: la misura del pezzo e quella dell'insieme non
      // coincidono al pixel (il kerning fra «…» e il testo), e un «altro» che va a capo è peggio di una lettera in meno.
      var collapsed = collapsedText(text, cut, suffix)
      var guard = 0
      while (cut > lineStart && guard < 24 &&
        measurer.measure(collapsed, style, constraints = constraints).lineCount > maxLines
      ) {
        cut = boundaryAtOrBefore(cut - 1).coerceAtLeast(lineStart)
        guard++
        collapsed = collapsedText(text, cut, suffix)
      }
      return FluidExpandablePlan(true, collapsed, trimmedEnd(text, cut), maxLines)
    }

    private fun suffix(more: String, moreColor: Color, style: TextStyle): AnnotatedString = buildAnnotatedString {
      append(Ellipsis)
      append(' ')
      withStyle(SpanStyle(color = moreColor, fontWeight = style.fontWeight ?: FontWeight.Normal)) { append(more) }
    }

    private fun trimmedEnd(text: AnnotatedString, cut: Int): Int {
      var end = cut
      while (end > 0 && text[end - 1].isWhitespace()) end--
      return end
    }

    private fun collapsedText(text: AnnotatedString, cut: Int, suffix: AnnotatedString): AnnotatedString =
      buildAnnotatedString {
        append(text.subSequence(0, trimmedEnd(text, cut)))
        append(suffix)
      }
  }
}
