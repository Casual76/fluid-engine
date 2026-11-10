package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** Quanto ci mette una parola nuova a sfumare dentro. */
const val FluidRevealMillis = 180

/**
 * Quanto ci mette una parola riscritta: il riconoscitore che cambia idea su cio' che ha gia' detto,
 * o il modello che chiude un `**`. Piu' corta, perche' al posto della parola vecchia ce n'e' gia'
 * una nuova da leggere.
 */
const val FluidRewriteMillis = 120

/**
 * Sotto questa maschera non si disegna: l'ultimo fotogramma della sfumatura vale zero e non
 * merita un `drawPath`, e un valore mai esattamente zero non deve tenere in vita il layer.
 */
private const val MaskFloor = 0.004f

private const val NanosPerMilli = 1_000_000L

/**
 * Testo che *arriva*: le parole nuove sfumano dentro in [revealMillis], quelle gia' viste restano
 * ferme. Un unico `Text`, un solo layout; la sfumatura e' una maschera `DstOut` sui glifi delle
 * parole nuove, in un layer fuori schermo acceso solo mentre c'e' qualcosa da rivelare.
 *
 * Solo alfa, niente salita di qualche dp: un `Text` gia' impaginato non puo' spostare una parola, ed
 * e' comunque l'effetto di ChatGPT. Il primo testo mai visto non si anima (la riapertura di una
 * chat, il cambio di chiave dell'item alla prima risposta), a meno che [animateFirst] non dica che
 * il testo sta proprio nascendo. Con il movimento ridotto dal sistema le durate sono zero e tutto
 * compare com'e'.
 *
 * L'orologio e' un `LaunchedEffect` che gira solo finche' c'e' qualcosa da rivelare e scrive `now` in
 * uno stato letto **solo nel disegno**: avanzare l'orologio invalida il disegno di questo `Text`, non
 * ricompone niente.
 *
 * @param reveal falso: nessuna sfumatura, il testo e' un `Text` come un altro (a fine risposta, o
 *   per una risposta vecchia). Resta lo stesso nodo, quindi il passaggio da "arriva" a "e' arrivato"
 *   non rifa' il layout ne' sposta niente.
 */
@Composable
fun FluidRevealingText(
  text: AnnotatedString,
  style: TextStyle,
  color: Color,
  modifier: Modifier = Modifier,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  onTextLayout: ((TextLayoutResult) -> Unit)? = null,
  revealMillis: Int = FluidRevealMillis,
  rewriteMillis: Int = FluidRewriteMillis,
  softWrap: Boolean = true,
  animateFirst: Boolean = false,
  reveal: Boolean = true,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val tracker = remember { RevealTracker() }
  val clock = remember { RevealClock() }
  val layout = remember { LayoutHolder() }
  val animating = reveal && !reducedMotion
  // In composizione, prima del layout e del disegno del fotogramma: quando il testo nuovo viene
  // disegnato la prima volta il tracker sa gia' quali parole nascondere. In un effetto sarebbe un
  // fotogramma tardi, e quel fotogramma mostrerebbe le parole nuove intere prima di sfumarle.
  remember(text, animating) {
    tracker.update(
      newText = text.text,
      revealNanos = if (animating) revealMillis * NanosPerMilli else 0L,
      rewriteNanos = if (animating) rewriteMillis * NanosPerMilli else 0L,
      animateFirst = animateFirst,
    )
    // Il valore ricordato non serve a nessuno (conta l'aggiornamento qui sopra): e' un Boolean solo perche'
    // `remember` non deve restituire `Unit` (RememberReturnType), e il comportamento e' lo stesso.
    tracker.hasWork.also { clock.active.value = it }
  }
  LaunchedEffect(text, animating) {
    if (!tracker.hasWork) return@LaunchedEffect
    while (true) {
      val now = withFrameNanos { it }
      tracker.stamp(now)
      clock.now.longValue = now
      if (tracker.settle(now)) {
        clock.active.value = false
        break
      }
    }
  }
  Text(
    text = text,
    style = style,
    color = color,
    maxLines = maxLines,
    overflow = overflow,
    softWrap = softWrap,
    onTextLayout = { result ->
      layout.result = result
      onTextLayout?.invoke(result)
    },
    modifier = modifier
      .graphicsLayer {
        // Il layer fuori schermo serve alla maschera: senza, il `DstOut` buca il fondale invece
        // del testo. Acceso solo mentre c'e' qualcosa da rivelare: un layer alto quanto una
        // risposta lunga e' una texture intera, e da fermo non serve a niente.
        compositingStrategy = if (clock.active.value) CompositingStrategy.Offscreen else CompositingStrategy.Auto
      }
      .drawWithContent {
        drawContent()
        if (!clock.active.value) return@drawWithContent
        val result = layout.result ?: return@drawWithContent
        val now = clock.now.longValue
        for (token in tracker.revealing) {
          val mask = token.mask(now)
          if (mask > MaskFloor) drawPath(token.path(result), Color.Black, alpha = mask, blendMode = BlendMode.DstOut)
        }
      },
  )
}

/** Nato, ma non ancora timbrato dal primo fotogramma. */
internal const val RevealPending = Long.MAX_VALUE

/** Gia' dentro: non si disegna e non si anima. */
internal const val RevealCommitted = Long.MIN_VALUE

/**
 * Un pezzo di testo che si rivela da solo: `[start, end)` sul testo del layout (che coincide con
 * `AnnotatedString.text`), quando e' nato, quanto ci mette.
 */
internal class RevealToken(val start: Int, val end: Int, var born: Long, val durationNanos: Long) {
  private var pathFor: TextLayoutResult? = null
  private var cached: Path? = null

  /**
   * 1 = nascosto, 0 = dentro. In attesa del primo fotogramma e' nascosto: il testo nuovo viene
   * disegnato una volta *prima* che l'orologio lo timbri, e quel fotogramma non deve mostrarlo.
   */
  fun mask(now: Long): Float = when {
    born == RevealCommitted -> 0f
    born == RevealPending -> 1f
    durationNanos <= 0L -> 0f
    else -> 1f - FluidMotion.EaseOut.transform(((now - born).toFloat() / durationNanos).coerceIn(0f, 1f))
  }

  fun finished(now: Long): Boolean = born != RevealPending && (durationNanos <= 0L || now - born >= durationNanos)

  /** Il contorno dei glifi: una volta per layout (che cambia col testo), non a ogni fotogramma. */
  fun path(layout: TextLayoutResult): Path {
    if (pathFor !== layout) {
      pathFor = layout
      cached = layout.getPathForRange(start, end)
    }
    return cached!!
  }
}

/**
 * Sa quali parole sono nuove. I token sono i run massimali di non-spazio; sul testo nuovo si tiene
 * il prefisso comune col precedente, e quel che viene dopo nasce in attesa. Se il nuovo testo
 * diverge *prima* della fine del vecchio e' una riscrittura: la coda dalla divergenza rinasce, col
 * tempo della riscrittura. Una parola tagliata dal prefisso (il modello che manda "Prima" e poi
 * "vera") tiene la testa com'era e fa nascere solo la coda: cosi' i pezzi di parola non
 * lampeggiano a ogni chunk.
 *
 * Pura: non sa nulla di Compose, e si prova sul computer (`FluidTextRevealTest`).
 */
internal class RevealTracker {
  private var text = ""
  private var seen = false
  private var tokens: List<RevealToken> = emptyList()

  /** I token non ancora dentro: in attesa del fotogramma o a meta' sfumatura. */
  var revealing: List<RevealToken> = emptyList()
    private set

  val hasWork: Boolean get() = revealing.isNotEmpty()

  /** Tutti i token, dentro o no: per i test. */
  val all: List<RevealToken> get() = tokens

  fun update(newText: String, revealNanos: Long, rewriteNanos: Long, animateFirst: Boolean) {
    if (seen && newText == text) return
    val old = text
    val oldTokens = tokens
    text = newText
    val fresh = ArrayList<RevealToken>(oldTokens.size + 8)
    if (!seen) {
      seen = true
      val born = if (animateFirst && revealNanos > 0L) RevealPending else RevealCommitted
      eachRun(newText, 0) { start, end -> fresh += RevealToken(start, end, born, revealNanos) }
    } else {
      val common = commonPrefix(old, newText)
      val rewrite = common < old.length
      val duration = if (rewrite) rewriteNanos else revealNanos
      val born = if (duration > 0L) RevealPending else RevealCommitted
      for (token in oldTokens) {
        if (token.end <= common) {
          fresh += token
          continue
        }
        if (token.start < common) fresh += RevealToken(token.start, common, token.born, token.durationNanos)
        break
      }
      eachRun(newText, common) { start, end -> fresh += RevealToken(start, end, born, duration) }
    }
    tokens = fresh
    revealing = fresh.filter { it.born != RevealCommitted }
  }

  /** Il primo fotogramma dopo la nascita: da qui si conta. */
  fun stamp(now: Long) {
    for (token in revealing) if (token.born == RevealPending) token.born = now
  }

  /** Mette a posto i token finiti; vero quando non c'e' piu' niente da rivelare. */
  fun settle(now: Long): Boolean {
    if (revealing.any { it.finished(now) }) {
      revealing = revealing.filter { token ->
        val done = token.finished(now)
        if (done) token.born = RevealCommitted
        !done
      }
    }
    return revealing.isEmpty()
  }
}

private inline fun eachRun(text: String, from: Int, block: (Int, Int) -> Unit) {
  var i = from
  val n = text.length
  while (i < n) {
    while (i < n && text[i].isWhitespace()) i++
    if (i >= n) break
    val start = i
    while (i < n && !text[i].isWhitespace()) i++
    block(start, i)
  }
}

private fun commonPrefix(a: String, b: String): Int {
  val n = minOf(a.length, b.length)
  var i = 0
  while (i < n && a[i] == b[i]) i++
  return i
}

/** `now` e' letto solo nel disegno, `active` dal layer e dal disegno: nessuno dei due ricompone. */
private class RevealClock {
  val now = mutableLongStateOf(0L)
  val active = mutableStateOf(false)
}

/** Il layout arriva in fase di layout, il disegno lo legge dopo: un contenitore, non uno stato. */
private class LayoutHolder {
  var result: TextLayoutResult? = null
}
