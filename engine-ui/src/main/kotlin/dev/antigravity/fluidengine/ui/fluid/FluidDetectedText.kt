package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Configura [FluidDetectedText] o il riconoscimento facoltativo di [FluidMarkdown].
 *
 * [kinds] limita i tipi riconosciuti, [locale] è la lingua del testo (null segue l'app).
 * [additionalActions] aggiunge voci al menu dopo le azioni di sistema e prima di Copia.
 * Viene chiamato all'apertura del menu con il dato che l'utente ha scelto.
 */
data class FluidTextDetection(
  val kinds: Set<FluidDetectedTextKind> = FluidDetectedTextKind.entries.toSet(),
  val locale: Locale? = null,
  val additionalActions: (FluidDetectedValue) -> List<FluidContextAction> = { emptyList() },
)

/**
 * Testo semplice con dati sottolineati appena finisce il riconoscimento fuori dal thread UI.
 * La sottolineatura usa l'inchiostro del testo, così eredita il suo contrasto. Il primo layout
 * contiene già tutto il testo e il segno non cambia le misure della tipografia.
 *
 * Il tocco apre il menu del dato in un [FluidGlassModalHost]. I link web si aprono al tocco
 * e mostrano il menu alla pressione lunga. La pressione lunga sugli altri dati apre lo stesso
 * menu del tocco. Il testo normale resta selezionabile quando [selectable] è vero.
 * Tastiera e TalkBack possono attivare il dato per aprire il menu.
 */
@Composable
fun FluidDetectedText(
  text: String,
  modifier: Modifier = Modifier,
  detection: FluidTextDetection = FluidTextDetection(),
  selectable: Boolean = true,
  style: TextStyle = MaterialTheme.typography.bodyLarge,
  color: Color = Color.Unspecified,
) {
  val annotated = remember(text) { AnnotatedString(text) }
  val ink = color.takeIf { it != Color.Unspecified }
    ?: style.color.takeIf { it != Color.Unspecified }
    ?: MaterialTheme.colorScheme.onSurface
  if (selectable) {
    SelectionContainer(modifier) { DetectedTextContent(annotated, style, ink, detection) }
  } else {
    DetectedTextContent(annotated, style, ink, detection, modifier)
  }
}

internal const val DetectedTextCodeAnnotation = "fluid.markdown.code"

@Composable
internal fun DetectedTextContent(
  text: AnnotatedString,
  style: TextStyle,
  color: Color,
  detection: FluidTextDetection,
  modifier: Modifier = Modifier,
  animateFirst: Boolean = false,
  reveal: Boolean = false,
) {
  val context = LocalContext.current
  val locale = detection.locale ?: LocalConfiguration.current.locales[0] ?: Locale.getDefault()
  val kinds = detection.kinds
  var values by remember(text, locale, kinds, context, reveal) { mutableStateOf(emptyList<FluidDetectedValue>()) }
  LaunchedEffect(text, locale, kinds, context, reveal) {
    if (!reveal && text.isNotEmpty() && kinds.isNotEmpty()) {
      val existing = text.getLinkAnnotations(0, text.length) + text.getStringAnnotations(DetectedTextCodeAnnotation, 0, text.length)
      val detected = classifyFluidText(context.applicationContext, text.text, locale, LocalDate.now(), kinds)
      values = if (existing.isEmpty()) detected else detected.filter { value ->
        existing.none { value.start < it.end && value.end > it.start }
      }
    }
  }
  val haptics = LocalFluidHaptics.current
  val additionalActions by rememberUpdatedState(detection.additionalActions)
  val actualContext by rememberUpdatedState(context)
  val geometry = remember(text) { DetectedTextGeometry() }
  var liftedValue by remember(text) { mutableStateOf<FluidDetectedValue?>(null) }
  val host = LocalFluidGlassModalHostState.current
  val controller = if (host != null && values.isNotEmpty()) {
    val preview: @Composable () -> Unit = remember(text, locale) {
      @Composable {
        val value = liftedValue
        if (value != null) DetectedValuePreview(value, locale)
      }
    }
    rememberFluidContextMenu(
      actions = { liftedValue?.let { value -> value.contextActions(actualContext, additionalActions(value)) }.orEmpty() },
      preview = preview,
    )
  } else null
  val open: (FluidDetectedValue, Offset?) -> Boolean = remember(controller, geometry, haptics) {
    { value, position ->
      val anchor = geometry.anchorFor(value, position)
      if (controller == null || controller.entry.lifted || anchor == null) {
        false
      } else {
        liftedValue = value
        controller.entry.previewSize = anchor
        controller.entry.origin = { anchor }
        if (controller.open()) {
          haptics.play(FluidHapticEvent.Open)
          true
        } else false
      }
    }
  }
  val marked = remember(text, values, open) {
    if (values.isEmpty()) text
    else AnnotatedString.Builder(text).apply {
      for (value in values) {
        if (controller == null && value.kind != FluidDetectedTextKind.Link) {
          addStyle(DetectedUnderline, value.start, value.end)
          continue
        }
        val link = LinkAnnotation.Clickable(
          tag = "fluid.detected.${value.start}",
          styles = DetectedLinkStyle,
          linkInteractionListener = {
            if (controller == null) value.launchPrimary(actualContext) else open(value, null)
          },
        )
        addLink(link, value.start, value.end)
      }
    }.toAnnotatedString()
  }

  val gestures = if (controller == null) Modifier else Modifier
    .onGloballyPositioned { geometry.coordinates = it }
    .fluidMenuPointerRelay(controller.handOff)
    .pointerInput(controller, geometry, values, open) {
      awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val value = geometry.hit(values, down.position) ?: return@awaitEachGesture
        // A un dato spetta il suo gesto sin dall'Initial; in Main la selezione di Compose
        // vede ancora tutti i down sul testo normale e sui link Markdown.
        down.consume()
        val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.singleOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
            if (change.isConsumed || event.changes.size != 1 ||
              (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            ) return@withTimeoutOrNull false
            if (!change.pressed) {
              change.consume()
              return@withTimeoutOrNull true
            }
            awaitPointerEvent(PointerEventPass.Final)
            if (change.isConsumed) return@withTimeoutOrNull false
          }
          @Suppress("UNREACHABLE_CODE")
          false
        }
        when (tapped) {
          true -> if (value.kind == FluidDetectedTextKind.Link) {
            value.launchPrimary(actualContext)
          } else {
            open(value, down.position)
          }
          null -> open(value, down.position)
          false -> Unit
        }
      }
    }
    .drawWithContent {
      // Il dato si trova nella card finché il menu si posa o si richiude. Il resto del
      // paragrafo rimane al suo posto e mantiene la stessa selezione e lo stesso layout.
      val path = if (controller.entry.lifted) liftedValue?.let(geometry::pathFor) else null
      if (path == null) drawContent() else clipPath(path, ClipOp.Difference) { this@drawWithContent.drawContent() }
    }
  val platformView = LocalViewConfiguration.current
  val inlineHitView = remember(platformView) {
    object : ViewConfiguration by platformView {
      // Un link in linea non può prendere i tocchi della riga di testo che gli sta accanto.
      override val minimumTouchTargetSize = DpSize.Zero
    }
  }
  CompositionLocalProvider(LocalViewConfiguration provides if (values.isEmpty()) platformView else inlineHitView) {
    FluidRevealingText(
      text = marked,
      style = style,
      color = color,
      modifier = modifier.then(gestures),
      onTextLayout = { geometry.layout = it },
      animateFirst = animateFirst,
      reveal = reveal,
    )
  }
}

// La sottolineatura richiama Note, iOS 26 (guida Apple, «Add links in Notes»).
private val DetectedUnderline = SpanStyle(textDecoration = TextDecoration.Underline)
private val DetectedLinkStyle = TextLinkStyles(DetectedUnderline)

private class DetectedTextGeometry {
  var coordinates: LayoutCoordinates? = null
  var layout: TextLayoutResult? = null
    set(value) {
      if (field != value) cachedPath = null
      field = value
    }

  private var cachedValue: FluidDetectedValue? = null
  private var cachedPath: Path? = null

  fun pathFor(value: FluidDetectedValue): Path? {
    val result = layout ?: return null
    if (cachedValue != value || cachedPath == null) {
      cachedValue = value
      cachedPath = result.getPathForRange(value.start, value.end)
    }
    return cachedPath
  }

  fun hit(values: List<FluidDetectedValue>, position: Offset): FluidDetectedValue? {
    val result = layout ?: return null
    if (position.y < 0 || position.y >= result.size.height) return null
    val offset = result.getOffsetForPosition(position)
    // Il cursore di Compose sceglie il bordo più vicino del glifo. Controlla anche il
    // carattere precedente, e respingi i tocchi nel margine oltre la fine della riga.
    return hitCharacter(result, values, position, offset) ?: hitCharacter(result, values, position, offset - 1)
  }

  private fun hitCharacter(
    result: TextLayoutResult,
    values: List<FluidDetectedValue>,
    position: Offset,
    offset: Int,
  ): FluidDetectedValue? {
    val index = values.binarySearch { value ->
      when {
        value.start > offset -> 1
        value.end <= offset -> -1
        else -> 0
      }
    }
    return values.getOrNull(index)?.takeIf { result.getBoundingBox(offset).contains(position) }
  }

  fun anchorFor(value: FluidDetectedValue, position: Offset?): Rect? {
    val result = layout ?: return null
    val coords = coordinates?.takeIf { it.isAttached } ?: return null
    val line = if (position == null) result.getLineForOffset(value.start) else result.getLineForVerticalPosition(position.y)
    val start = maxOf(value.start, result.getLineStart(line))
    val end = minOf(value.end, result.getLineEnd(line, visibleEnd = true))
    if (start >= end) return null
    val bounds = result.getPathForRange(start, end).getBounds()
    val anchor = Rect(coords.localToRoot(bounds.topLeft), coords.localToRoot(bounds.bottomRight))
    return anchor.intersect(coords.boundsInRoot()).takeUnless { it.isEmpty }
  }
}

/** Scheda locale. Larghezza e rientri scelti a occhio il 24 novembre 2026 per mostrare il giorno in una riga. */
@Composable
private fun DetectedValuePreview(value: FluidDetectedValue, locale: Locale) {
  // Una scheda locale: per una data mostra il giorno che finirà in agenda. L'indirizzo
  // resta leggibile senza interrogare Mappe; nessuna richiesta di rete per l'anteprima.
  Column(Modifier.widthIn(max = 280.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
    val date = value.date
    if (date != null) {
      Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)), style = MaterialTheme.typography.titleMedium)
      value.time?.let { time ->
        Text(time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)), style = MaterialTheme.typography.bodyMedium)
      }
    } else {
      Text(value.text, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
  }
}
