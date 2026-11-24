package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Quanto ci mette una parola nuova a mettersi a fuoco. Dalla 4.0 (F8.1) è [FluidTextMotion.WordArrivalMillis], 260 ms:
 * col blur chiesto da Alessio la sola alfa di 180 ms non lasciava vedere la messa a fuoco.
 */
const val FluidRevealMillis = FluidTextMotion.WordArrivalMillis

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

/** In che pezzi arriva il testo. */
enum class FluidTextArrivalUnit {
  /** Parola per parola, nell'ordine in cui arrivano: lo streaming di un assistente, la dettatura. */
  Word,

  /** Riga per riga: tutte le parole di una riga insieme, le righe una dopo l'altra (il testo che si espande). */
  Line,
}

/**
 * Come arriva il testo nuovo (F8.1, `testo-espandibile` e lo streaming di PampAI): **sfocato, che si mette a fuoco**, e
 * non tutto insieme ma a pezzi, uno dopo l'altro. È la nota di Alessio: «il testo non appare tutto insieme ma
 * gradualmente, e con il blur», da usare sia per il testo che si espande sia per il testo generato.
 *
 * Il disegno resta un `Text` solo, con un layout solo. I pezzi che arrivano sono nascosti da una maschera sui loro
 * riquadri e ridisegnati come un fantasma sfocato che si mette a fuoco sulla parola vera: il testo stesso, ritagliato sulle
 * parole che arrivano, in al più quattro livelli sfocati alti quanto il paragrafo (le parole raggruppate per quanto sono
 * ancora nascoste). Nessun livello per parola, nessun livello a tutto schermo; i livelli e il layer fuori schermo del testo
 * ci sono solo mentre qualcosa arriva. Da fermo il testo non ridisegna. La sfocatura vuole Android 12; prima resta la
 * dissolvenza.
 *
 * Col movimento ridotto il testo compare intero, senza sfocatura e senza passo.
 *
 * @param unit parole o righe.
 * @param staggerMillis il passo fra due pezzi dello stesso arrivo.
 * @param maxSpreadMillis il ritardo massimo di un pezzo sull'arrivo: il passo non si accumula, e l'ordine resta.
 * @param blur falso: solo la dissolvenza, come prima della 4.0.
 * @param peakBlur σ massima, in frazione dell'altezza delle maiuscole ([FluidTextMotion.ArrivalPeakBlur]).
 */
@Immutable
data class FluidTextArrival(
  val unit: FluidTextArrivalUnit = FluidTextArrivalUnit.Word,
  val staggerMillis: Int = FluidTextMotion.WordStaggerMillis,
  val maxSpreadMillis: Int = FluidTextMotion.WordSpreadMillis,
  val blur: Boolean = true,
  val peakBlur: Float = FluidTextMotion.ArrivalPeakBlur,
) {
  init {
    require(staggerMillis >= 0 && maxSpreadMillis >= 0) { "passo e ritardo non possono essere negativi" }
    require(peakBlur >= 0f && peakBlur.isFinite()) { "sfocatura non valida: $peakBlur" }
  }

  companion object {
    /** Il testo generato o dettato: parola per parola, sfocato (di serie in [FluidRevealingText] e `FluidMarkdown`). */
    val Streaming: FluidTextArrival = FluidTextArrival()

    /** Il testo che compare intero o si apre: riga per riga, sfocato ([FluidExpandableText], [FluidArrivingText]). */
    val Lines: FluidTextArrival = FluidTextArrival(
      unit = FluidTextArrivalUnit.Line,
      staggerMillis = FluidTextMotion.LineStaggerMillis,
      maxSpreadMillis = FluidTextMotion.LineSpreadMillis,
    )

    /** La sola dissolvenza, senza sfocatura e senza passo: il testo che arrivava prima della 4.0. */
    val Fade: FluidTextArrival = FluidTextArrival(staggerMillis = 0, maxSpreadMillis = 0, blur = false)
  }
}

/**
 * Testo che *arriva*: le parole nuove arrivano sfocate e si mettono a fuoco in [revealMillis], quelle gia' viste restano
 * ferme. Un unico `Text`, un solo layout; la sfumatura e' una maschera `DstOut` sui glifi delle parole nuove, in un layer
 * fuori schermo acceso solo mentre c'e' qualcosa da rivelare, e la sfocatura un fantasma del testo stesso in un livello
 * sfocato ([FluidTextArrival]).
 *
 * Niente salita di qualche dp: un `Text` gia' impaginato non puo' spostare una parola. Il primo testo mai visto non si
 * anima (la riapertura di una chat, il cambio di chiave dell'item alla prima risposta), a meno che [animateFirst] non dica
 * che il testo sta proprio nascendo. Con il movimento ridotto dal sistema le durate sono zero e tutto compare com'e'.
 *
 * L'orologio e' un `LaunchedEffect` che gira solo finche' c'e' qualcosa da rivelare e scrive `now` in
 * uno stato letto **solo nel disegno**: avanzare l'orologio invalida il disegno di questo `Text`, non
 * ricompone niente.
 *
 * @param reveal falso: nessuna sfumatura, il testo e' un `Text` come un altro (a fine risposta, o
 *   per una risposta vecchia). Resta lo stesso nodo, quindi il passaggio da "arriva" a "e' arrivato"
 *   non rifa' il layout ne' sposta niente.
 * @param arrival come arriva il testo nuovo: di serie parola per parola e sfocato (dalla 4.0, F8.1);
 *   [FluidTextArrival.Fade] e' la sola dissolvenza di prima.
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
  arrival: FluidTextArrival = FluidTextArrival.Streaming,
) {
  FluidRevealingTextCore(
    text = text,
    style = style,
    color = color,
    modifier = modifier,
    maxLines = maxLines,
    overflow = overflow,
    onTextLayout = onTextLayout,
    revealMillis = revealMillis,
    rewriteMillis = rewriteMillis,
    softWrap = softWrap,
    animateFirst = animateFirst,
    reveal = reveal,
    arrival = arrival,
    rewriteArrives = false,
  )
}

/**
 * Un testo che **arriva intero**, riga per riga, sfocato, e si mette a fuoco: una risposta che compare, una descrizione
 * caricata, un testo che cambia identità. Quando [text] cambia, il nuovo arriva allo stesso modo dal primo carattere
 * diverso in poi; quello che resta uguale non si muove.
 *
 * È [FluidRevealingText] con [FluidTextArrival.Lines] e il primo testo animato: lo stesso primitivo del testo generato da
 * PampAI e del testo che si espande ([FluidExpandableText]).
 *
 * @param animateFirst falso: il testo che c'è alla prima composizione è già dentro (una pagina riaperta).
 */
@Composable
fun FluidArrivingText(
  text: String,
  modifier: Modifier = Modifier,
  style: TextStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
  color: Color = Color.Unspecified,
  animateFirst: Boolean = true,
  arrival: FluidTextArrival = FluidTextArrival.Lines,
  maxLines: Int = Int.MAX_VALUE,
) {
  val ink = if (color.isSpecified) color else style.color.takeIf { it.isSpecified }
    ?: androidx.compose.material3.LocalContentColor.current
  val annotated = remember(text) { AnnotatedString(text) }
  FluidRevealingTextCore(
    text = annotated,
    style = style,
    color = ink,
    modifier = modifier,
    maxLines = maxLines,
    overflow = TextOverflow.Clip,
    onTextLayout = null,
    revealMillis = if (arrival.unit == FluidTextArrivalUnit.Line) FluidTextMotion.LineArrivalMillis else FluidRevealMillis,
    rewriteMillis = if (arrival.unit == FluidTextArrivalUnit.Line) FluidTextMotion.LineArrivalMillis else FluidRevealMillis,
    softWrap = true,
    animateFirst = animateFirst,
    reveal = true,
    arrival = arrival,
    rewriteArrives = true,
  )
}

/**
 * Il cuore comune: [rewriteArrives] vero tratta un testo riscritto come un testo che arriva (col passo e la durata
 * dell'arrivo), ed è quello che serve al testo che si espande, dove «… altro» lascia il posto al resto del paragrafo.
 */
@Composable
internal fun FluidRevealingTextCore(
  text: AnnotatedString,
  style: TextStyle,
  color: Color,
  modifier: Modifier,
  maxLines: Int,
  overflow: TextOverflow,
  onTextLayout: ((TextLayoutResult) -> Unit)?,
  revealMillis: Int,
  rewriteMillis: Int,
  softWrap: Boolean,
  animateFirst: Boolean,
  reveal: Boolean,
  arrival: FluidTextArrival,
  rewriteArrives: Boolean,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val tracker = remember { RevealTracker() }
  val clock = remember { RevealClock() }
  val layout = remember { LayoutHolder() }
  val graphics = LocalGraphicsContext.current
  val ghost = remember(graphics) { RevealGhost(graphics, GhostBuckets) }
  DisposableEffect(ghost) { onDispose { ghost.release() } }
  val probe = LocalFluidRevealProbe.current
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
      staggerNanos = if (animating) arrival.staggerMillis * NanosPerMilli else 0L,
      spreadNanos = if (animating) arrival.maxSpreadMillis * NanosPerMilli else 0L,
      byLine = arrival.unit == FluidTextArrivalUnit.Line,
      rewriteArrives = rewriteArrives,
    )
    // Il valore ricordato non serve a nessuno (conta l'aggiornamento qui sopra): e' un Boolean solo perche'
    // `remember` non deve restituire `Unit` (RememberReturnType), e il comportamento e' lo stesso.
    probe?.invoke(tracker, Long.MIN_VALUE)
    tracker.hasWork.also { clock.active.value = it }
  }
  LaunchedEffect(text, animating) {
    if (!tracker.hasWork) return@LaunchedEffect
    while (true) {
      val now = withFrameNanos { it }
      tracker.stamp(now, layout.result)
      clock.now.longValue = now
      probe?.invoke(tracker, now)
      if (tracker.settle(now)) {
        clock.active.value = false
        // I livelli del fantasma servono solo mentre qualcosa arriva: in una chat lunga non restano cento livelli.
        ghost.release()
        break
      }
    }
  }
  val blurOn = arrival.blur && arrival.peakBlur > 0f && RevealGhost.Available
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
        val revealing = tracker.revealing
        val peakPx = if (blurOn) arrival.peakBlur * FluidTextMotion.CapHeight * fontSizePx(style) else 0f
        // Prima la maschera: nasconde (del tutto o in parte) le parole vere che arrivano.
        for (index in revealing.indices) {
          val token = revealing[index]
          val mask = token.mask(now)
          if (mask <= MaskFloor) continue
          val progress = 1f - mask
          // Col fantasma la parola vera si accende piu' tardi: a meta' strada e' un quarto, il resto e' il fantasma.
          val hidden = if (peakPx <= 0f) mask else 1f - progress * progress
          drawPath(token.path(result), Color.Black, alpha = hidden, blendMode = BlendMode.DstOut)
        }
        if (peakPx <= 0f) return@drawWithContent
        // Poi il fantasma: il testo vero, ritagliato sulle parole che arrivano e sfocato in un livello. Le parole si
        // raggruppano per quanto sono ancora nascoste (al piu' GhostBuckets livelli, uno per gruppo): un livello alto
        // quanto il paragrafo, mai a tutto schermo, e solo mentre qualcosa arriva.
        for (bucket in 0 until GhostBuckets) {
          val clip = ghost.clip(bucket)
          clip.reset()
          var any = false
          for (index in revealing.indices) {
            val token = revealing[index]
            val mask = token.mask(now)
            if (mask <= MaskFloor || ghostBucketOf(mask) != bucket) continue
            clip.addRect(token.bounds(result))
            any = true
          }
          if (!any) continue
          val mask = (bucket + 0.5f) / GhostBuckets
          val progress = 1f - mask
          val sigma = peakPx * mask
          val margin = kotlin.math.ceil(sigma * 3f).toInt()
          val layer = ghost.layer(bucket)
          layer.record(IntSize(size.width.toInt() + 2 * margin, size.height.toInt() + 2 * margin)) {
            translate(margin.toFloat(), margin.toFloat()) {
              clipPath(clip) { this@drawWithContent.drawContent() }
            }
          }
          layer.topLeft = IntOffset(-margin, -margin)
          layer.alpha = 2f * progress * (1f - progress)
          layer.renderEffect = ghost.blur(sigma)
          drawLayer(layer)
        }
      },
  )
}

/** Quanti gruppi di sfocatura ha il fantasma: quattro gradini fra nascosta e dentro (a occhio, 24 novembre 2026). */
internal const val GhostBuckets = 4

internal fun ghostBucketOf(mask: Float): Int = (mask * GhostBuckets).toInt().coerceIn(0, GhostBuckets - 1)

private fun DrawScope.fontSizePx(style: TextStyle): Float =
  if (style.fontSize.isSpecified) style.fontSize.toPx() else 16.sp.toPx()

/**
 * Il fantasma sfocato di ciò che si muove (le parole che arrivano, le cifre che scorrono): il **testo vero** disegnato in un
 * livello grafico con una sfocatura `RenderEffect` (F8.1). I livelli nascono al primo uso e si liberano con [release]; le
 * sfocature si fanno una volta per gradino di σ (mezzo pixel) e si tengono: niente allocazioni per fotogramma.
 *
 * Non la sagoma dei glifi: `TextLayoutResult.getPathForRange` dà il riquadro della selezione, e il primo giro di F8.1 sul
 * Tab S9 ha mostrato rettangoli al posto delle lettere sfocate. La sfocatura di un livello vuole Android 12 (API 31):
 * prima il fantasma non si disegna e resta la sola dissolvenza.
 */
internal class RevealGhost(private val graphics: GraphicsContext, count: Int) {
  private val layers = arrayOfNulls<GraphicsLayer>(count)
  private val clips = Array(count) { Path() }
  private val effects = arrayOfNulls<RenderEffect>(MaxSteps + 1)

  fun layer(index: Int): GraphicsLayer = layers[index] ?: graphics.createGraphicsLayer().also { layers[index] = it }

  /** Il ritaglio del gruppo [index], da riempire a ogni fotogramma (si riusa, non si alloca). */
  fun clip(index: Int): Path = clips[index]

  /** La sfocatura di σ = [sigmaPx], a gradini di mezzo pixel; null sotto il mezzo pixel. */
  fun blur(sigmaPx: Float): RenderEffect? {
    val step = (sigmaPx * 2f).roundToInt().coerceIn(0, MaxSteps)
    if (step == 0) return null
    return effects[step] ?: run {
      val radius = radiusForSigma(step / 2f)
      BlurEffect(radius, radius, TileMode.Decal)
    }.also { effects[step] = it }
  }

  fun release() {
    for (index in layers.indices) {
      layers[index]?.let(graphics::releaseGraphicsLayer)
      layers[index] = null
    }
  }

  companion object {
    /** Fino a σ = 24 px a mezzi pixel: oltre, ciò che si muove e' gia' quasi trasparente. */
    private const val MaxSteps = 48

    val Available: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Skia: σ = 0,57735 · raggio + 0,5 (`SkBlurMask::ConvertRadiusToSigma`), il raggio di `RenderEffect.createBlurEffect`. */
    fun radiusForSigma(sigma: Float): Float = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
  }
}

/**
 * Per le prove (`F8p1MotionTest`): chiamata col tracker quando il testo cambia (istante `Long.MIN_VALUE`) e a ogni
 * fotogramma dell'arrivo. In Robolectric si guarda lo stato vero del testo, non i pixel.
 */
internal val LocalFluidRevealProbe = androidx.compose.runtime.staticCompositionLocalOf<((RevealTracker, Long) -> Unit)?> { null }

/** Nato, ma non ancora timbrato dal primo fotogramma. */
internal const val RevealPending = Long.MAX_VALUE

/** Gia' dentro: non si disegna e non si anima. */
internal const val RevealCommitted = Long.MIN_VALUE

/**
 * Un pezzo di testo che si rivela da solo: `[start, end)` sul testo del layout (che coincide con
 * `AnnotatedString.text`), quando e' nato, quanto ci mette.
 *
 * @param order la posizione della parola nel suo arrivo (0 per la prima): il passo la ritarda di `order` passi.
 */
internal class RevealToken(
  val start: Int,
  val end: Int,
  var born: Long,
  val durationNanos: Long,
  val order: Int = 0,
) {
  private var pathFor: TextLayoutResult? = null
  private var cached: Path? = null
  private var cachedBounds: Rect = Rect.Zero

  /**
   * 1 = nascosto, 0 = dentro. In attesa del primo fotogramma e' nascosto: il testo nuovo viene
   * disegnato una volta *prima* che l'orologio lo timbri, e quel fotogramma non deve mostrarlo. Prima della sua nascita
   * (il passo) resta nascosto.
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
      cached = layout.getPathForRange(start, end.coerceAtMost(layout.layoutInput.text.length)).also {
        cachedBounds = it.getBounds()
      }
    }
    return cached!!
  }

  /** Il riquadro della parola (quello della selezione), per il ritaglio del fantasma. */
  fun bounds(layout: TextLayoutResult): Rect {
    path(layout)
    return cachedBounds
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
 * Dalla 4.0 (F8.1) i pezzi di uno stesso arrivo nascono **uno dopo l'altro**: una parola dopo l'altra (o una riga dopo
 * l'altra, con il layout), di un passo, senza mai superare il ritardo massimo e **senza mai superare l'ordine**: una
 * parola non nasce prima di quella che la precede, nemmeno fra due arrivi.
 *
 * Pura: non sa nulla di Compose, e si prova sul computer (`FluidTextRevealTest`).
 */
internal class RevealTracker {
  private var text = ""
  private var seen = false
  private var tokens: List<RevealToken> = emptyList()
  private var staggerNanos = 0L
  private var spreadNanos = 0L
  private var byLine = false

  /** L'ultima nascita data: la prossima parola non nasce prima. */
  private var lastBorn = Long.MIN_VALUE

  /** I token non ancora dentro: in attesa del fotogramma o a meta' sfumatura. */
  var revealing: List<RevealToken> = emptyList()
    private set

  val hasWork: Boolean get() = revealing.isNotEmpty()

  /** Tutti i token, dentro o no: per i test. */
  val all: List<RevealToken> get() = tokens

  fun update(
    newText: String,
    revealNanos: Long,
    rewriteNanos: Long,
    animateFirst: Boolean,
    staggerNanos: Long = 0L,
    spreadNanos: Long = 0L,
    byLine: Boolean = false,
    rewriteArrives: Boolean = false,
  ) {
    if (seen && newText == text) return
    this.staggerNanos = staggerNanos
    this.spreadNanos = spreadNanos
    this.byLine = byLine
    val old = text
    val oldTokens = tokens
    text = newText
    val fresh = ArrayList<RevealToken>(oldTokens.size + 8)
    if (!seen) {
      seen = true
      val born = if (animateFirst && revealNanos > 0L) RevealPending else RevealCommitted
      var order = 0
      eachRun(newText, 0) { start, end -> fresh += RevealToken(start, end, born, revealNanos, order++) }
    } else {
      val common = commonPrefix(old, newText)
      val rewrite = common < old.length && !rewriteArrives
      val duration = if (rewrite) rewriteNanos else revealNanos
      val born = if (duration > 0L) RevealPending else RevealCommitted
      for (token in oldTokens) {
        if (token.end <= common) {
          fresh += token
          continue
        }
        if (token.start < common) fresh += RevealToken(token.start, common, token.born, token.durationNanos, token.order)
        break
      }
      // Una riscrittura sostituisce quel che c'era gia': senza passo. Un arrivo, col passo.
      var order = 0
      eachRun(newText, common) { start, end ->
        fresh += RevealToken(start, end, born, duration, if (rewrite) 0 else order++)
      }
    }
    tokens = fresh
    revealing = fresh.filter { it.born != RevealCommitted }
  }

  /**
   * Il primo fotogramma dopo la nascita: da qui si conta. Ogni pezzo in attesa nasce `order` passi dopo (o tanti passi
   * quante righe dopo la prima del suo arrivo, con [layout] e le righe), entro il ritardo massimo e mai prima del pezzo
   * che lo precede.
   */
  fun stamp(now: Long, layout: TextLayoutResult? = null) {
    var firstLine = -1
    var previousLine = -1
    for (index in revealing.indices) {
      val token = revealing[index]
      if (token.born != RevealPending) continue
      if (staggerNanos == 0L) {
        // Senza passo: tutto il pezzo nasce adesso, come prima della 4.0.
        token.born = now
        continue
      }
      val line = if (byLine && layout != null) layout.lineOfOffset(token.start) else -1
      if (line >= 0 && line == previousLine) {
        // La stessa riga: nasce con la parola prima.
        token.born = lastBorn
        continue
      }
      if (line >= 0 && firstLine < 0) firstLine = line
      val steps = if (line >= 0) line - firstLine else token.order
      val wanted = now + (steps * staggerNanos).coerceAtMost(spreadNanos)
      val after = if (lastBorn == Long.MIN_VALUE) wanted else maxOf(wanted, lastBorn + staggerNanos)
      token.born = minOf(after, now + spreadNanos).coerceAtLeast(if (lastBorn == Long.MIN_VALUE) now else lastBorn)
      lastBorn = token.born
      previousLine = line
    }
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

/** La riga di un carattere, anche oltre la fine del testo impaginato (un layout di un fotogramma prima). */
private fun TextLayoutResult.lineOfOffset(offset: Int): Int =
  getLineForOffset(offset.coerceIn(0, layoutInput.text.length))

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
