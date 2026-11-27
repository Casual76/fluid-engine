package dev.antigravity.fluidengine.ui.fluid

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.foundation.FluidFormat
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Un numero che cambia **facendo scorrere solo le cifre cambiate** (F8.1, `numeri-che-scorrono`): in su se il valore
 * cresce, in giù se cala, sfocate mentre viaggiano (la nota di Alessio: «col blur durante il movimento»). È
 * `Text(…).contentTransition(.numericText(value:))` di SwiftUI (iOS 17), quello dei timer di Orologio e dei saldi di
 * Wallet.
 *
 * - **Solo le cifre che cambiano** si muovono: 128 → 131 lascia ferma la prima cifra. Virgola, segno e unità non scorrono:
 *   restano dove sono, o scivolano di lato se il numero si allunga (99 → 100), e cambiano a dissolvenza se cambiano
 *   (MB → GB). Le cifre si allineano da destra, per valore di posizione.
 * - **Il verso viene dal valore** ([value]), non dal testo: un conto alla rovescia scende anche quando il testo «sale»
 *   (1:00 → 0:59).
 * - **Le cifre sono tabulari** di serie ([FluidTextStyles.numeric]): la riga non balla.
 * - **Il tempo** è la molla di serie di SwiftUI da iOS 17 ([FluidTextMotion.DigitResponseSeconds]); la cifra viaggia di
 *   [FluidTextMotion.DigitTravel] righe e si sfoca al più di [FluidTextMotion.DigitPeakBlur] (R7).
 * - **Da fermo non ridisegna**: un testo solo, impaginato una volta per valore; il movimento è letto solo nel disegno e
 *   nella misura, e mai nella composizione.
 * - **Col movimento ridotto** il numero cambia a dissolvenza ([FluidTextMotion.DigitReducedFadeMillis]).
 * - **TalkBack** sente solo il valore finale ([contentDescription], di serie il testo).
 *
 * Un valore che cambia molte volte al secondo (i dB di un fonometro) non scorre: sotto
 * [FluidTextMotion.DigitMinIntervalMillis] fra due cambi il numero cambia di colpo, e torna a scorrere quando rallenta (F8.1b).
 *
 * **Le cifre scorrono solo se le muove l'app** (regola di Alessio del 24 novembre 2026: ciò che muove il dito resta nitido).
 * Mentre il dito trascina un controllo dell'engine che cambia un valore (`FluidSlider`, `FluidRangeSlider`, `FluidRulerDial`,
 * `FluidWheelPicker`) il numero cambia di colpo, nitido: il quadrante guidato da un cursore non si sfoca sotto il dito.
 * Una ruota o un righello lanciati restano del dito per tutta l'inerzia, posa compresa: i minuti scelti con una ruota
 * cambiano di colpo anche se la ruota li dice a dito già alzato. Torna a scorrere ciò che cambia l'app da sola. Un numero
 * che segue il dito di un gesto tuo (il valore sotto il dito di un grafico) usa un `Text`, non questo.
 *
 * @param text il numero già scritto, con i suoi separatori e la sua unità («1.234,5 kg»).
 * @param value il valore del numero, per il verso: cresce, le cifre salgono.
 */
@Composable
fun FluidNumericText(
  text: String,
  value: Double,
  modifier: Modifier = Modifier,
  style: TextStyle = FluidTextStyles.numeric,
  color: Color = Color.Unspecified,
  contentDescription: String? = null,
) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val ink = when {
    color.isSpecified -> color
    style.color.isSpecified -> style.color
    else -> LocalContentColor.current
  }
  val measurer = rememberTextMeasurer(cacheSize = 4)
  val layout = remember(text, style, measurer) {
    measurer.measure(text, style, softWrap = false, maxLines = 1)
  }
  val roll = remember { NumericRollState() }
  // In composizione, come il tracker del testo che arriva: il fotogramma che disegna il valore nuovo sa già cosa scorre.
  remember(layout) {
    // L'ora del cambio dall'orologio di sistema (in Robolectric quello delle prove): un cambio troppo vicino al precedente
    // non scorre. Letta qui, nel cambio, e non dal prossimo fotogramma: lo scorrimento parte senza ritardo.
    roll.show(text, value, layout, SystemClock.uptimeMillis())
    roll.generation
  }
  val progress = remember { Animatable(1f) }
  val graphics = LocalGraphicsContext.current
  val ghost = remember(graphics) { RevealGhost(graphics, 2) }
  DisposableEffect(ghost) { onDispose { ghost.release() } }
  LaunchedEffect(roll.generation, reduced) {
    if (!roll.moving) return@LaunchedEffect
    // Un cambio che arriva troppo presto dopo l'altro non scorre (F8.1b): il numero cambia di colpo, come prima.
    if (roll.rapid) {
      FluidNumericTextDiagnostics.jumps++
      progress.snapTo(1f)
      roll.started = true
      roll.settle()
      ghost.release()
      return@LaunchedEffect
    }
    FluidNumericTextDiagnostics.rolls++
    progress.snapTo(0f)
    roll.started = true
    if (reduced) {
      progress.animateTo(1f, tween(FluidTextMotion.DigitReducedFadeMillis, easing = FluidMotion.EaseOut))
    } else {
      progress.animateTo(
        1f,
        // Ferma a 0,004 del viaggio (0,2 px: invisibile): la molla si posa a ~0,62 s invece di ~0,73.
        spring(FluidMotion.DampingChrome, FluidMotion.stiffnessForResponse(FluidTextMotion.DigitResponseSeconds), DigitSettle),
      )
    }
    roll.settle()
    // I due livelli sfocati servono solo mentre le cifre viaggiano.
    ghost.release()
  }
  val spoken = contentDescription ?: text
  Layout(
    content = {},
    modifier = modifier
      // F7.24: il numero che cambia col dito si dice a TalkBack quando il gesto finisce.
      .fluidSettledSemantics { this.contentDescription = spoken }
      .drawBehind {
        val transition = roll.transition
        // Letto sempre, anche prima della partenza: è la lettura che iscrive il disegno ai passi dell'animazione.
        val raw = progress.value
        val p = if (roll.rapid) 1f else if (roll.started) raw else 0f
        if (transition == null || p >= 1f) {
          drawText(roll.layout ?: return@drawBehind, color = ink)
          return@drawBehind
        }
        if (reduced) {
          drawText(transition.from, color = ink, alpha = 1f - p)
          drawText(transition.to, color = ink, alpha = p)
        } else {
          drawRoll(transition, p, ink, style, ghost)
        }
      },
  ) { _, constraints ->
    val current = roll.layout
    val transition = roll.transition
    val raw = progress.value
    val p = if (roll.rapid) 1f else if (roll.started) raw else 0f
    val width = if (transition != null && p < 1f && !reduced) {
      lerp(transition.from.size.width.toFloat(), transition.to.size.width.toFloat(), p)
    } else {
      (current?.size?.width ?: 0).toFloat()
    }
    val height = current?.size?.height ?: 0
    layout(
      ceil(width).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: Int.MAX_VALUE),
      height.coerceIn(constraints.minHeight, constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: Int.MAX_VALUE),
    ) {}
  }
}

/**
 * Un numero che scorre, scritto da [format]: di serie con i separatori della lingua ([FluidFormat.number]). Per un
 * contatore («3 non lette») o una percentuale.
 */
@Composable
fun FluidNumericText(
  value: Number,
  modifier: Modifier = Modifier,
  style: TextStyle = FluidTextStyles.numeric,
  color: Color = Color.Unspecified,
  format: (Number) -> String = { FluidFormat.number(it.toDouble()) },
) {
  FluidNumericText(text = format(value), value = value.toDouble(), modifier = modifier, style = style, color = color)
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Il dito sta muovendo un valore (F8.1b, regola di Alessio del 24 novembre 2026: **ciò che muove il dito resta nitido, si
 * sfoca solo ciò che muove l'app da sola**, e niente blur neanche nell'inerzia di un lancio partito dal dito). I controlli
 * dell'engine che cambiano un valore trascinando (il cursore, l'intervallo, il righello, la ruota) lo dicono con
 * [fluidFingerDrivesValue] finché il dito è giù, e la ruota e il righello con [fluidFingerMotion] finché la loro corsa
 * partita dal dito (lancio, assestamento, tocco, posa) non si ferma; intanto un [FluidNumericText] che cambia (il numero di
 * un quadrante guidato da un cursore, i minuti scelti con una ruota) cambia di colpo, senza scorrere e senza sfocarsi.
 * Solo sul thread principale: un contatore semplice, non uno stato.
 */
internal object FluidFingerDrive {
  /**
   * Per quanto, dopo l'ultimo rilascio, un cambio conta ancora come del dito. Il valore scritto all'ultimo evento (o
   * all'ultimo passo di una corsa) si ricompone nel fotogramma dopo, e lo slider dice il valore di un tocco sulla pista
   * proprio al sollevamento, dopo che l'osservatore ha già chiuso. 50 ms: tre fotogrammi a 60 Hz, sei a 120 (a occhio,
   * 24 novembre 2026). Un cambio dell'app che cade lì dentro cambia di colpo: un difetto che non si vede.
   */
  const val LingerMillis = 50L

  var active: Int = 0
    private set

  /** L'istante (`SystemClock.uptimeMillis`) in cui il conto è tornato a zero. */
  private var releasedAtMillis = Long.MIN_VALUE / 2

  /**
   * Cresce ogni volta che il conto torna a zero (F7.24): uno **stato**, letto da chi aspetta la fine del gesto (la
   * semantica di [fluidSettledSemantics]). Scritto solo qui, sul thread principale, fuori dalla composizione.
   */
  private val idleCount = androidx.compose.runtime.mutableIntStateOf(0)
  val idleGeneration: Int get() = idleCount.intValue

  /** Un dito muove un valore, o una corsa partita dal dito non si è fermata (senza la coda di [LingerMillis]). */
  val holding: Boolean get() = active > 0

  fun begin() {
    active++
  }

  fun end() {
    if (active > 0) active--
    if (active == 0) {
      releasedAtMillis = SystemClock.uptimeMillis()
      idleCount.intValue++
    }
  }

  /** Vero mentre il dito muove un valore, o una corsa partita dal dito non si è fermata, e per [LingerMillis] dopo. */
  fun driving(nowMillis: Long): Boolean {
    if (active > 0) return true
    if (nowMillis == Long.MIN_VALUE) return false
    // Solo in avanti: un orologio che riparte (una prova nuova in Robolectric) non vede il rilascio di un'altra.
    val since = nowMillis - releasedAtMillis
    return since in 0 until LingerMillis
  }
}

/**
 * La corsa di un controllo partita dal dito (il lancio, l'assestamento, il tocco che porta una voce al centro, la posa che
 * dice il valore al chiamante): conta come il dito giù finché [block] non finisce, anche se il dito si è già alzato. Un
 * dito che riprende il controllo annulla la corsa: il conto scende qui e risale col suo osservatore.
 */
internal suspend fun fluidFingerMotion(block: suspend () -> Unit) {
  FluidFingerDrive.begin()
  try {
    block()
  } finally {
    FluidFingerDrive.end()
  }
}

/**
 * Dice a [FluidFingerDrive] che il dito è giù su questo controllo, dal primo contatto all'ultimo sollevamento. Guarda gli
 * eventi nel passaggio iniziale e **non consuma niente**: il gesto del controllo resta com'è.
 */
internal fun Modifier.fluidFingerDrivesValue(): Modifier = pointerInput(Unit) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    FluidFingerDrive.begin()
    try {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.none { it.pressed }) break
      }
    } finally {
      FluidFingerDrive.end()
    }
  }
}

/** Dove si ferma la molla delle cifre: a 0,004 del viaggio (0,2 px su una riga da 50). */
private const val DigitSettle = 0.004f

/** I contatori delle prove: quanti cambi sono scorsi e quanti sono saltati perche' troppo fitti. */
internal object FluidNumericTextDiagnostics {
  var rolls: Int = 0
  var jumps: Int = 0
}

/** Il passaggio da una scrittura all'altra, già pianificato: chi resta, chi scorre, chi entra, chi esce. */
internal class NumericTransition(
  val from: TextLayoutResult,
  val to: TextLayoutResult,
  /** +1 sale (cresce), -1 scende. */
  val direction: Int,
  val slots: List<NumericSlot>,
) {
  /** I bordi dei caratteri, letti una volta: `getBoundingBox` darebbe un `Rect` nuovo a ogni fotogramma. */
  val fromLeft = FloatArray(from.layoutInput.text.length) { from.getBoundingBox(it).left }
  val fromRight = FloatArray(from.layoutInput.text.length) { from.getBoundingBox(it).right }
  val toLeft = FloatArray(to.layoutInput.text.length) { to.getBoundingBox(it).left }
  val toRight = FloatArray(to.layoutInput.text.length) { to.getBoundingBox(it).right }
}

/** Lo stato delle cifre: il valore mostrato e, mentre scorrono, il passaggio. Non è uno stato di Compose. */
internal class NumericRollState {
  var text: String? = null
    private set
  var value: Double = 0.0
    private set
  var layout: TextLayoutResult? = null
    private set
  var transition: NumericTransition? = null
    private set
  /** L'istante (`SystemClock.uptimeMillis`) dell'ultimo cambio: un cambio piu' vicino di `DigitMinIntervalMillis` non scorre. */
  private var lastChangeMillis: Long = Long.MIN_VALUE

  /** Il cambio in corso e' arrivato troppo presto dopo il precedente: cambia di colpo. */
  var rapid: Boolean = false
    private set

  /** Cresce a ogni cambio: la chiave dell'animazione. Un numero semplice, letto in composizione dopo [show]. */
  var generation = 0
    private set

  /**
   * L'animazione è partita (il suo primo `snapTo(0)`). Fino ad allora il disegno sta all'inizio del passaggio: il
   * fotogramma del cambio non mostra il numero nuovo già fermo.
   */
  var started = false

  val moving: Boolean get() = transition != null

  fun show(newText: String, newValue: Double, newLayout: TextLayoutResult, nowMillis: Long = Long.MIN_VALUE) {
    val oldText = text
    val oldLayout = layout
    val oldValue = value
    text = newText
    value = newValue
    layout = newLayout
    if (oldText == null || oldLayout == null || oldText == newText) {
      // Il primo valore, o lo stesso testo con uno stile nuovo: nessun movimento.
      transition = null
      return
    }
    transition = NumericTransition(
      from = oldLayout,
      to = newLayout,
      direction = FluidNumericRoll.direction(oldValue, newValue, oldText, newText),
      slots = FluidNumericRoll.plan(oldText, newText),
    )
    // Troppo presto dopo il cambio di prima, o mosso dal dito (un cursore, una ruota): cambia di colpo, nitido.
    rapid = FluidFingerDrive.driving(nowMillis) || (
      nowMillis != Long.MIN_VALUE && lastChangeMillis != Long.MIN_VALUE &&
        nowMillis - lastChangeMillis < FluidTextMotion.DigitMinIntervalMillis
      )
    if (nowMillis != Long.MIN_VALUE) lastChangeMillis = nowMillis
    started = false
    generation++
  }

  fun settle() {
    transition = null
  }
}

/** Che fa un carattere nel cambio. */
internal enum class NumericSlotKind {
  /** Uguale nei due testi: resta (e scivola di lato se il numero si allunga o si accorcia). */
  Static,

  /** Una cifra che diventa un'altra: la vecchia esce, la nuova entra, nel verso del cambio. */
  Roll,

  /** Una cifra (o un separatore) che prima non c'era: entra nel verso del cambio. */
  Enter,

  /** Una cifra (o un separatore) che non c'è più: esce nel verso del cambio. */
  Exit,

  /** Un segno che non è una cifra e cambia (MB → GB): dissolvenza sul posto, senza scorrere. */
  Swap,
}

/** Un posto del cambio: l'indice nel testo vecchio e in quello nuovo, -1 dove manca. */
internal data class NumericSlot(val kind: NumericSlotKind, val from: Int, val to: Int)

/**
 * La regola del cambio, pura (`FluidNumericTextTest`): il testo si divide in tre parti, ciò che sta prima della prima
 * cifra (il segno, «€ »), il **corpo** dalla prima all'ultima cifra (con i separatori) e ciò che segue (l'unità). Il
 * corpo si allinea **da destra**, per valore di posizione: le unità sotto le unità, così 99 → 100 fa scorrere le due
 * cifre di destra e fa entrare l'1. Le parti prima e dopo si allineano dal loro bordo esterno e non scorrono mai.
 */
internal object FluidNumericRoll {
  fun plan(old: String, new: String): List<NumericSlot> {
    val o = Parts.of(old)
    val n = Parts.of(new)
    val slots = ArrayList<NumericSlot>(maxOf(old.length, new.length) + 2)
    align(old, 0, o.coreStart, new, 0, n.coreStart, fromRight = true, roll = false, into = slots)
    align(old, o.coreStart, o.coreEnd, new, n.coreStart, n.coreEnd, fromRight = true, roll = true, into = slots)
    align(old, o.coreEnd, old.length, new, n.coreEnd, new.length, fromRight = false, roll = false, into = slots)
    return slots
  }

  /**
   * Il verso: dal valore, e a parità di valore dal testo (un testo che si allunga sale). +1 sale, -1 scende.
   */
  fun direction(oldValue: Double, newValue: Double, oldText: String, newText: String): Int = when {
    newValue > oldValue -> 1
    newValue < oldValue -> -1
    newText.length >= oldText.length -> 1
    else -> -1
  }

  private fun align(
    old: String, oStart: Int, oEnd: Int,
    new: String, nStart: Int, nEnd: Int,
    fromRight: Boolean, roll: Boolean,
    into: MutableList<NumericSlot>,
  ) {
    val count = maxOf(oEnd - oStart, nEnd - nStart)
    for (k in 0 until count) {
      val oi = if (fromRight) oEnd - 1 - k else oStart + k
      val ni = if (fromRight) nEnd - 1 - k else nStart + k
      val hasOld = oi in oStart until oEnd
      val hasNew = ni in nStart until nEnd
      val kind = when {
        hasOld && hasNew && old[oi] == new[ni] -> NumericSlotKind.Static
        hasOld && hasNew && roll && old[oi].isDigit() && new[ni].isDigit() -> NumericSlotKind.Roll
        hasOld && hasNew -> NumericSlotKind.Swap
        // Solo le cifre entrano ed escono viaggiando; un separatore che nasce o se ne va (999 → 1.000) cambia sul posto,
        // a dissolvenza, come promette il KDoc: virgola e separatori non scorrono mai (R2).
        hasNew -> if (roll && new[ni].isDigit()) NumericSlotKind.Enter else NumericSlotKind.Swap
        else -> if (roll && old[oi].isDigit()) NumericSlotKind.Exit else NumericSlotKind.Swap
      }
      into += NumericSlot(kind, if (hasOld) oi else -1, if (hasNew) ni else -1)
    }
  }

  /** Dove comincia e finisce il corpo del numero: [coreStart, coreEnd). Senza cifre il corpo è vuoto, in fondo. */
  private class Parts(val coreStart: Int, val coreEnd: Int) {
    companion object {
      fun of(text: String): Parts {
        val first = text.indexOfFirst { it.isDigit() }
        if (first < 0) return Parts(text.length, text.length)
        val last = text.indexOfLast { it.isDigit() }
        return Parts(first, last + 1)
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// Il disegno del passaggio
// ---------------------------------------------------------------------------------------------------------------------

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
  val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/** Sotto questa σ un glifo si disegna come testo vero, ritagliato: niente salto quando la cifra si ferma. */
private const val SharpSigmaPx = 0.6f

private fun DrawScope.drawRoll(t: NumericTransition, p: Float, ink: Color, style: TextStyle, ghost: RevealGhost) {
  val lineHeight = t.to.size.height.toFloat()
  val travel = FluidTextMotion.DigitTravel * lineHeight
  val fontPx = if (style.fontSize.isSpecified) style.fontSize.toPx() else 17.sp.toPx()
  val peak = FluidTextMotion.DigitPeakBlur * FluidTextMotion.CapHeight * fontPx
  // Chi esce sale (valore che cresce) e svanisce sfocandosi; chi entra arriva da sotto e si mette a fuoco.
  val outAlpha = 1f - smoothstep(0f, 0.7f, p)
  val outSigma = peak * smoothstep(0f, 0.5f, p)
  val inAlpha = smoothstep(0.2f, 1f, p)
  val inSigma = peak * (1f - smoothstep(0.35f, 1f, p))
  val blurOut = RevealGhost.Available && outSigma >= SharpSigmaPx
  val blurIn = RevealGhost.Available && inSigma >= SharpSigmaPx
  // Le cifre ferme e quelle nitide, direttamente; quelle sfocate, ognuna nel livello della sua parte (chi esce, chi entra).
  drawSlots(t, p, travel, ink, sharpOut = !blurOut, sharpIn = !blurIn, outAlpha, inAlpha, drawStatic = true)
  if (blurOut && outAlpha > 0.004f) {
    blurredSlots(t, p, travel, ink, ghost, layer = 0, out = true, alpha = outAlpha, sigma = outSigma)
  }
  if (blurIn && inAlpha > 0.004f) {
    blurredSlots(t, p, travel, ink, ghost, layer = 1, out = false, alpha = inAlpha, sigma = inSigma)
  }
}

/** Le cifre di una parte in un livello sfocato: un livello per parte, alto quanto la riga più il viaggio. */
private fun DrawScope.blurredSlots(
  t: NumericTransition,
  p: Float,
  travel: Float,
  ink: Color,
  ghost: RevealGhost,
  layer: Int,
  out: Boolean,
  alpha: Float,
  sigma: Float,
) {
  val margin = kotlin.math.ceil(travel + sigma * 3f).toInt()
  val graphics = ghost.layer(layer)
  graphics.record(IntSize(size.width.toInt() + 2 * margin, size.height.toInt() + 2 * margin)) {
    translate(margin.toFloat(), margin.toFloat()) {
      drawSlots(t, p, travel, ink, sharpOut = out, sharpIn = !out, 1f, 1f, drawStatic = false, only = if (out) 1 else 2)
    }
  }
  graphics.topLeft = IntOffset(-margin, -margin)
  graphics.alpha = alpha
  graphics.renderEffect = ghost.blur(sigma)
  drawLayer(graphics)
}

/**
 * Disegna i posti del cambio: le cifre ferme (se [drawStatic]), quelle che escono (se [sharpOut]) e quelle che entrano
 * (se [sharpIn]), ognuna al suo posto del fotogramma [p]. [only] limita a chi esce (1) o a chi entra (2).
 */
private fun DrawScope.drawSlots(
  t: NumericTransition,
  p: Float,
  travel: Float,
  ink: Color,
  sharpOut: Boolean,
  sharpIn: Boolean,
  outAlpha: Float,
  inAlpha: Float,
  drawStatic: Boolean,
  only: Int = 0,
) {
  val dir = t.direction
  val slots = t.slots
  val showOut = sharpOut && only != 2
  val showIn = sharpIn && only != 1
  for (i in slots.indices) {
    val slot = slots[i]
    val from = slot.from
    val to = slot.to
    when (slot.kind) {
      NumericSlotKind.Static -> if (drawStatic) {
        val dx = lerp(t.fromLeft[from], t.toLeft[to], p) - t.toLeft[to]
        glyph(t.to, t.toLeft[to], t.toRight[to], dx, 0f, 1f, ink)
      }
      NumericSlotKind.Roll, NumericSlotKind.Swap -> {
        val travelOn = if (slot.kind == NumericSlotKind.Roll) 1f else 0f
        if (from >= 0 && showOut) {
          val x = if (to >= 0) lerp(t.fromLeft[from], t.toLeft[to], p) else t.fromLeft[from]
          glyph(t.from, t.fromLeft[from], t.fromRight[from], x - t.fromLeft[from], -dir * travel * p * travelOn, outAlpha, ink)
        }
        if (to >= 0 && showIn) {
          val x = if (from >= 0) lerp(t.fromLeft[from], t.toLeft[to], p) else t.toLeft[to]
          glyph(t.to, t.toLeft[to], t.toRight[to], x - t.toLeft[to], dir * travel * (1f - p) * travelOn, inAlpha, ink)
        }
      }
      NumericSlotKind.Enter -> if (showIn) {
        glyph(t.to, t.toLeft[to], t.toRight[to], 0f, dir * travel * (1f - p), inAlpha, ink)
      }
      NumericSlotKind.Exit -> if (showOut) {
        glyph(t.from, t.fromLeft[from], t.fromRight[from], 0f, -dir * travel * p, outAlpha, ink)
      }
    }
  }
}

/** Un carattere del testo [layout] (il testo vero, ritagliato al suo riquadro), spostato di ([dx], [dy]). */
private fun DrawScope.glyph(layout: TextLayoutResult, left: Float, right: Float, dx: Float, dy: Float, alpha: Float, ink: Color) {
  if (alpha <= 0.004f) return
  translate(dx, dy) {
    clipRect(left - 0.5f, 0f, right + 0.5f, layout.size.height.toFloat()) {
      drawText(layout, color = ink, alpha = alpha)
    }
  }
}
