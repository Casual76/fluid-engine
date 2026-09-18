package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText

/** Una parola dentro un testo: dove sta, e quando viene detta. */
@Immutable
data class FluidSpokenWord(
  val start: Int,
  val end: Int,
  val startMs: Long,
  val endMs: Long,
)

/**
 * Il testo che si accende mentre l'audio va.
 *
 * Quello che e' gia' stato detto e' nel colore pieno, quello che deve ancora venire e' velato, e il
 * confine si muove parola per parola. Non e' una decorazione: e' l'unica cosa che tiene insieme una
 * registrazione e la sua trascrizione mentre le si ascolta, e dice a colpo d'occhio «sei qui».
 *
 * **La posizione arriva come lambda, non come parametro**, e la differenza e' tutta. A cinque
 * battiti al secondo un parametro rimisurerebbe il testo cinque volte al secondo — su un paragrafo
 * di dieci righe e' un fotogramma perso ogni volta. Letta dentro il disegno, invalida solo il
 * disegno: il testo resta misurato, e cambia quale parte viene ridipinta.
 *
 * Due passate: tutto in [pendingColor] — che e' il `Text` vero e proprio, quindi conserva
 * accessibilita', selezione e layout — e poi la parte gia' detta in [spokenColor], ritagliata sulle
 * righe intere piu' il rettangolo parziale della riga corrente.
 */
@Composable
fun FluidSpokenText(
  text: String,
  words: List<FluidSpokenWord>,
  positionMs: () -> Long,
  modifier: Modifier = Modifier,
  style: TextStyle = LocalTextStyle.current,
  spokenColor: Color = MaterialTheme.colorScheme.onSurface,
  pendingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  var layout by remember(text, style) { mutableStateOf<TextLayoutResult?>(null) }

  Box(modifier = modifier) {
    Text(
      text = text,
      style = style,
      color = pendingColor,
      onTextLayout = {
        layout = it
        onTextLayout(it)
      },
      modifier = Modifier.drawWithContent {
        drawContent()
        val result = layout ?: return@drawWithContent
        val spoken = fluidSpokenChars(words, positionMs())
        if (spoken <= 0) return@drawWithContent
        drawSpoken(result, spoken.coerceAtMost(text.length), spokenColor)
      },
    )
  }
}

/**
 * Fin dove il testo e' stato detto, in caratteri, alla posizione data.
 *
 * Ricerca binaria sulle parole, che sono in ordine di tempo: un paragrafo puo' averne trecento e
 * questa domanda si fa a ogni fotogramma. Dentro una parola il confine avanza in proporzione, cosi'
 * l'evidenziazione scorre invece di scattare da una parola all'altra.
 *
 * Fuori da questo file e' internal-visibile apposta: e' l'unica parte con una logica da provare.
 */
fun fluidSpokenChars(words: List<FluidSpokenWord>, positionMs: Long): Int {
  if (words.isEmpty()) return 0
  if (positionMs < words.first().startMs) return 0
  val last = words.last()
  if (positionMs >= last.endMs) return last.end

  var low = 0
  var high = words.lastIndex
  var index = 0
  while (low <= high) {
    val middle = (low + high) / 2
    if (words[middle].startMs <= positionMs) {
      index = middle
      low = middle + 1
    } else {
      high = middle - 1
    }
  }

  val word = words[index]
  if (positionMs >= word.endMs) return word.end
  val span = (word.endMs - word.startMs).coerceAtLeast(1L)
  val fraction = ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
  val length = (word.end - word.start).coerceAtLeast(0)
  return word.start + (length * fraction).toInt()
}

/**
 * Ridipinge in [color] i primi [spoken] caratteri.
 *
 * Le righe intere prima di quella corrente si ritagliano tutte insieme; la riga corrente si taglia
 * alla posizione orizzontale del carattere. Con il testo da destra a sinistra il taglio parte
 * dall'altro lato, che e' l'unica cosa che cambia.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpoken(
  layout: TextLayoutResult,
  spoken: Int,
  color: Color,
) {
  val line = layout.getLineForOffset((spoken - 1).coerceAtLeast(0))
  val lineTop = layout.getLineTop(line)
  val lineBottom = layout.getLineBottom(line)

  if (line > 0) {
    clipRect(top = 0f, bottom = lineTop) { drawText(layout, color = color) }
  }
  val x = layout.getHorizontalPosition(spoken, usePrimaryDirection = true)
  val leftToRight = layout.getParagraphDirection(spoken) == androidx.compose.ui.text.style.ResolvedTextDirection.Ltr
  val rect = if (leftToRight) {
    Rect(Offset(0f, lineTop), Size(x, lineBottom - lineTop))
  } else {
    Rect(Offset(x, lineTop), Size(size.width - x, lineBottom - lineTop))
  }
  clipRect(left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom) {
    drawText(layout, color = color)
  }
}
