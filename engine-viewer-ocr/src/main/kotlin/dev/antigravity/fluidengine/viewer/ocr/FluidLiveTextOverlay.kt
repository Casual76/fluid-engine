/*
 * engine-viewer-ocr (4.0, F8.17): il disegno e i gesti di «Leggi testo», sopra l'immagine e dentro lo zoom del visore:
 * i riquadri seguono la foto da soli quando si ingrandisce, si sposta o si gira il telefono. Le maniglie e il menu
 * restano della stessa misura a ogni zoom (la scala si legge nel disegno, senza ricomporre). Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.viewer.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.viewer.currentFluidViewerZoom
import kotlin.math.max
import kotlin.math.roundToInt

/** I tag dei test di «Leggi testo». */
object FluidLiveTextTags {
  const val Overlay = "livetext:sopra"
  const val Button = "livetext:tasto"
  const val CopyAll = "livetext:copia-tutto"
  const val ShowText = "livetext:testo"
  const val QuickAction = "livetext:azione-rapida"
  const val Menu = "livetext:menu"
  const val MenuCopy = "livetext:menu:copia"
  const val MenuSelectAll = "livetext:menu:tutto"
  const val Sheet = "livetext:foglio"

  fun line(index: Int): String = "livetext:riga:$index"
}

/**
 * Il disegno di «Leggi testo» sopra un'immagine: va messo **dentro** il riquadro dell'immagine (lo stesso che lo zoom
 * del visore ingrandisce), e riempie quel riquadro. Fa:
 * - con la modalità attiva ([FluidLiveTextState.active]) il velo scuro sulle parti senza testo e le righe in evidenza,
 *   come il tasto di Testo attivo; per TalkBack ogni riga diventa un elemento, nell'ordine della lettura;
 * - **tieni premuto su una parola** per selezionarla (anche con la modalità spenta, come in Foto), poi trascina per
 *   allargare; con la modalità attiva basta un tocco; le maniglie spostano i due capi; il menu sopra la selezione dà
 *   Copia, Seleziona tutto e Condividi;
 * - i risultati della ricerca in giallo, quello corrente più acceso.
 *
 * Il dito resta nitido (F8): nessuna sfocatura su ciò che si trascina. Il velo e le righe compaiono in 250 ms (subito
 * col movimento ridotto).
 */
@Composable
fun FluidLiveTextOverlay(
  state: FluidLiveTextState,
  modifier: Modifier = Modifier,
  strings: FluidLiveTextStrings = FluidLiveText.strings,
) {
  val text = state.text ?: return
  if (text.isEmpty) return
  val zoom = currentFluidViewerZoom()
  val haptics = LocalHapticFeedback.current
  val context = LocalContext.current
  val motion = LocalFluidMotionPolicy.current
  val veil by animateFloatAsState(if (state.active) 1f else 0f, if (motion.reducedMotion) tween(0) else tween(VeilMillis), label = "LiveTextVeil")
  val accent = MaterialTheme.colorScheme.primary
  val longPress = LocalViewConfiguration.current.longPressTimeoutMillis
  BoxWithConstraints(modifier.fillMaxSize().testTag(FluidLiveTextTags.Overlay)) {
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    val size = Size(w, h)
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
      .pointerInput(text, w, h) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val scale = zoom?.scale ?: 1f
          val p = Offset(down.position.x / w, down.position.y / h)
          val handle = handleAt(state, text, down.position, size, HandleSlop.toPx() / scale)
          if (handle != null) {
            down.consume()
            dragSelection(state, text, size, fixed = handle) { it.consume() }
            return@awaitEachGesture
          }
          val word = wordAt(text, p, WordSlop.toPx() / scale / max(w, h))
          if (word == null) {
            // Un tocco fuori dalle parole toglie la selezione; il visore riceve lo stesso il tocco (le barre).
            if (state.selection != null) {
              val up = waitForUpOrCancellation()
              if (up != null) state.selection = null
            }
            return@awaitEachGesture
          }
          // Il tempo scaduto è la pressione lunga; un gesto preso da altri (lo scorrimento delle pagine, lo spostamento
          // della foto ingrandita, il pizzico) non lo è: allora non si seleziona niente.
          var taken = false
          val up = withTimeoutOrNull(longPress) { waitForUpOrCancellation().also { if (it == null) taken = true } }
          if (taken) return@awaitEachGesture
          if (up == null) {
            // Tenuto premuto: la parola, poi il dito allarga la selezione finché non si alza.
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            state.selection = FluidTextMatch(word, word)
            dragSelection(state, text, size, fixed = word) { it.consume() }
          } else if (state.active) {
            up.consume()
            state.selection = FluidTextMatch(word, word)
          }
        }
      }) {
      val scale = zoom?.scale ?: 1f
      drawLiveText(state, text, veil, accent, scale)
    }
    if (state.active) LiveTextLines(state, text, strings, w, h)
    val selection = state.selection
    if (selection != null) SelectionMenu(state, text, selection, strings, context, w, h, zoom?.scale ?: 1f)
  }
}

private fun DrawScope.drawLiveText(state: FluidLiveTextState, text: FluidRecognizedText, veil: Float, accent: Color, scale: Float) {
  val pad = 3.dp.toPx() / scale
  val radius = CornerRadius(4.dp.toPx() / scale)
  if (veil > 0f) {
    // Il velo di Testo attivo: tutto si scurisce, le righe restano chiare con un contorno bianco leggero.
    drawRect(Color.Black.copy(alpha = VeilAlpha * veil))
    val stroke = Stroke(1.dp.toPx() / scale)
    for (line in text.lines) {
      val q = line.quad.scaled(size)
      drawQuad(q, Color.Black, radius, pad, blendMode = BlendMode.Clear)
      drawQuad(q, Color.White.copy(alpha = 0.10f * veil), radius, pad)
      drawQuad(q, Color.White.copy(alpha = 0.75f * veil), radius, pad, stroke)
    }
  }
  val matches = state.matches
  matches.forEachIndexed { i, m ->
    val color = if (i == state.current.coerceIn(0, max(0, matches.lastIndex))) MatchCurrent else MatchOther
    for (q in spanQuads(text, m.first, m.last)) drawQuad(q.scaled(size), color.copy(alpha = 0.5f), radius, 0f)
  }
  val selection = state.selection ?: return
  val quads = spanQuads(text, selection.first, selection.last).map { it.scaled(size) }
  for (q in quads) drawQuad(q, accent.copy(alpha = 0.32f), radius, 0f)
  // Le maniglie di iOS: una sbarra con un pallino, all'inizio in alto e alla fine in basso, lungo il lato della riga
  // (storto, se la riga è storta in una foto).
  val first = quads.firstOrNull() ?: return
  val last = quads.last()
  val bar = 2.dp.toPx() / scale
  val dot = 5.dp.toPx() / scale
  drawLine(accent, first.topLeft, first.bottomLeft, bar)
  drawCircle(accent, dot, first.topLeft + (first.topLeft - first.bottomLeft).unit() * dot)
  drawLine(accent, last.topRight, last.bottomRight, bar)
  drawCircle(accent, dot, last.bottomRight + (last.bottomRight - last.topRight).unit() * dot)
}

/**
 * Un quadrilatero (pixel) allargato di [pad]: un rettangolo arrotondato se è dritto (le schermate, le pagine), se no il
 * poligono storto della riga in una foto (F8.17d).
 */
private fun DrawScope.drawQuad(
  q: FluidTextQuad,
  color: Color,
  radius: CornerRadius,
  pad: Float,
  style: androidx.compose.ui.graphics.drawscope.DrawStyle = androidx.compose.ui.graphics.drawscope.Fill,
  blendMode: BlendMode = BlendMode.SrcOver,
) {
  if (q.isUpright) {
    val r = q.bounds.inflate(pad)
    drawRoundRect(color, r.topLeft, r.size, radius, style = style, blendMode = blendMode)
    return
  }
  val c = Offset((q.topLeft.x + q.topRight.x + q.bottomRight.x + q.bottomLeft.x) / 4f,
    (q.topLeft.y + q.topRight.y + q.bottomRight.y + q.bottomLeft.y) / 4f)
  fun out(p: Offset) = p + (p - c).unit() * (pad * 1.41f)
  val path = Path().apply {
    val a = out(q.topLeft)
    moveTo(a.x, a.y)
    for (p in listOf(q.topRight, q.bottomRight, q.bottomLeft)) out(p).let { lineTo(it.x, it.y) }
    close()
  }
  drawPath(path, color, style = style, blendMode = blendMode)
}

/**
 * I quadrilateri (0..1) delle parole da [from] a [to]: uno per riga toccata, come la selezione di iOS. Ognuno è il pezzo
 * della sua riga dall'inizio della prima parola alla fine dell'ultima, alto quanto la riga: dritto in una schermata,
 * storto come la riga in una foto storta.
 */
internal fun spanQuads(text: FluidRecognizedText, from: Int, to: Int): List<FluidTextQuad> {
  if (text.words.isEmpty()) return emptyList()
  val a = minOf(from, to).coerceIn(0, text.words.lastIndex)
  val b = maxOf(from, to).coerceIn(0, text.words.lastIndex)
  val out = ArrayList<FluidTextQuad>()
  var i = a
  while (i <= b) {
    val line = text.lineOfWord[i]
    var j = i
    while (j + 1 <= b && text.lineOfWord[j + 1] == line) j++
    out += text.lines[line].quad.between(text.words[i].quad, text.words[j].quad)
    i = j + 1
  }
  return out
}

/** I rettangoli (pixel del riquadro) dei pezzi di [spanQuads]: per il menu e per «Trova» dei PDF. */
internal fun spanRects(text: FluidRecognizedText, from: Int, to: Int, size: Size): List<Rect> =
  spanQuads(text, from, to).map { it.scaled(size).bounds }

/** La parola sotto [p] (0..1), o la più vicina entro [slop]; `null` se non ce n'è. */
internal fun wordAt(text: FluidRecognizedText, p: Offset, slop: Float): Int? {
  var best: Int? = null
  var bestDistance = Float.MAX_VALUE
  text.words.forEachIndexed { i, w ->
    val d = w.quad.distanceTo(p)
    if (d < bestDistance) { bestDistance = d; best = i }
  }
  return best?.takeIf { bestDistance <= slop }
}

/** Quale capo della selezione sta sotto [position] (pixel): restituisce l'**altro** capo, quello che resta fermo. */
private fun handleAt(state: FluidLiveTextState, text: FluidRecognizedText, position: Offset, size: Size, slop: Float): Int? {
  val s = state.selection ?: return null
  val quads = spanQuads(text, s.first, s.last)
  val first = quads.firstOrNull()?.scaled(size) ?: return null
  val last = quads.last().scaled(size)
  val start = (first.topLeft + first.bottomLeft) / 2f
  val end = (last.topRight + last.bottomRight) / 2f
  val a = minOf(s.first, s.last)
  val b = maxOf(s.first, s.last)
  return when {
    (position - end).getDistance() <= slop -> a
    (position - start).getDistance() <= slop -> b
    else -> null
  }
}

/** Il dito che allarga la selezione dalla parola [fixed] fino a quella sotto il dito, finché non si alza. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.dragSelection(
  state: FluidLiveTextState, text: FluidRecognizedText, size: Size, fixed: Int, consume: (PointerInputChange) -> Unit,
) {
  while (true) {
    val event = awaitPointerEvent()
    val change = event.changes.firstOrNull() ?: return
    if (!change.pressed) { consume(change); return }
    if (change.positionChange() != Offset.Zero) {
      val p = Offset(change.position.x / size.width, change.position.y / size.height)
      wordAt(text, p, 1f)?.let { word -> state.selection = FluidTextMatch(minOf(fixed, word), maxOf(fixed, word)) }
    }
    consume(change)
  }
}

/** Per TalkBack, con la modalità attiva: un elemento per riga nel posto della riga, letto nell'ordine. */
@Composable
private fun LiveTextLines(state: FluidLiveTextState, text: FluidRecognizedText, strings: FluidLiveTextStrings, w: Float, h: Float) {
  val context = LocalContext.current
  Layout(content = {
    text.lines.forEachIndexed { i, line ->
      Box(Modifier.testTag(FluidLiveTextTags.line(i)).semantics {
        this.text = AnnotatedString(line.text)
        customActions = listOf(CustomAccessibilityAction(strings.copyLine) { copy(context, line.text); true })
      })
    }
  }) { measurables, _ ->
    val placeables = measurables.mapIndexed { i, m ->
      val r = text.lines[i].quad.bounds.scaled(Size(w, h))
      m.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1))) to r
    }
    layout(w.roundToInt(), h.roundToInt()) {
      for ((p, r) in placeables) p.place(r.left.roundToInt(), r.top.roundToInt())
    }
  }
}

/** Il menu della selezione (Copia, Seleziona tutto, Condividi): una capsula di vetro sopra la selezione, o sotto. */
@Composable
private fun SelectionMenu(state: FluidLiveTextState, text: FluidRecognizedText, selection: FluidTextMatch, strings: FluidLiveTextStrings,
  context: Context, w: Float, h: Float, scale: Float) {
  val rects = remember(text, selection, w, h) { spanRects(text, selection.first, selection.last, Size(w, h)) }
  val top = rects.minOfOrNull { it.top } ?: return
  val bottom = rects.maxOf { it.bottom }
  val center = (rects.minOf { it.left } + rects.maxOf { it.right }) / 2f
  val zoom = currentFluidViewerZoom()
  Layout(content = {
    Row(Modifier.graphicsLayer {
      // La misura sullo schermo resta la stessa a ogni zoom: il menu si rimpicciolisce quanto lo zoom ingrandisce.
      val s = 1f / (zoom?.scale ?: 1f)
      scaleX = s; scaleY = s
    }.fluidGlass(shape = FluidCapsuleShape).padding(horizontal = 4.dp).testTag(FluidLiveTextTags.Menu)) {
      MenuItem(strings.copy, FluidLiveTextTags.MenuCopy) {
        state.selectedText?.let { copy(context, it) }
        state.selection = null
      }
      MenuItem(strings.selectAll, FluidLiveTextTags.MenuSelectAll) { state.selectAll() }
      MenuItem(strings.share, null) {
        state.selectedText?.let { share(context, it) }
      }
    }
  }) { measurables, constraints ->
    val menu = measurables.first().measure(Constraints())
    layout(constraints.maxWidth, constraints.maxHeight) {
      val gap = 10.dp.toPx() / scale
      val visualH = menu.height / scale
      val above = top - gap - visualH
      // Il layer scala attorno al centro: la posizione si corregge di metà della differenza.
      val y = if (above >= 0f) above - (menu.height - visualH) / 2f else bottom + gap - (menu.height - visualH) / 2f
      val x = (center - menu.width / 2f).coerceIn(-(menu.width - menu.width / scale) / 2f, w - menu.width + (menu.width - menu.width / scale) / 2f)
      menu.place(x.roundToInt(), y.roundToInt())
    }
  }
}

@Composable
private fun MenuItem(label: String, tag: String?, onClick: () -> Unit) {
  Box(Modifier.fluidPressable(onClick = onClick).then(if (tag != null) Modifier.testTag(tag) else Modifier)
    .padding(horizontal = 14.dp, vertical = 11.dp)) {
    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
  }
}

internal fun copy(context: Context, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText(FluidLiveText.strings.liveText, text))
}

internal fun share(context: Context, text: String) {
  val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
  context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun Rect.scaled(size: Size) = Rect(left * size.width, top * size.height, right * size.width, bottom * size.height)

/** Il quadrilatero in pixel di un riquadro [size]. */
private fun FluidTextQuad.scaled(size: Size): FluidTextQuad {
  fun s(p: Offset) = Offset(p.x * size.width, p.y * size.height)
  return FluidTextQuad(s(topLeft), s(topRight), s(bottomRight), s(bottomLeft))
}

/** La direzione di questo vettore, lunga 1 (zero se è zero). */
private fun Offset.unit(): Offset {
  val d = getDistance()
  return if (d > 0f) this / d else Offset.Zero
}

/** Il velo di Testo attivo: nero al 45%. A occhio, sulle schermate di Foto in iOS 26 (fiducia media). */
private const val VeilAlpha = 0.45f
/** La comparsa del velo: 250 ms. A occhio. */
private const val VeilMillis = 250
private val HandleSlop = 28.dp
private val WordSlop = 12.dp
private val MatchCurrent = Color(0xFFFFC400)
private val MatchOther = Color(0xFFFFE680)
