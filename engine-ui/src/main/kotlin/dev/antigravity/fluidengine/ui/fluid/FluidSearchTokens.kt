package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * F6B.8b · I gettoni nel campo della ricerca: la seconda metà di F6B.8.
 *
 * Come SwiftUI `searchable(text:tokens:suggestedTokens:)`: i gettoni stanno **dentro** il campo, prima del testo, e il
 * testo scorre dopo di loro; i gettoni suggeriti (`FluidSearchSpec.filters`) sono una fila di pillole di vetro accanto al
 * campo aperto. La pillola scelta **diventa gettone** con un viaggio vero: il suo vetro vola dentro il campo, si fa
 * piccolo e diventa il gettone tinto d'accento, mentre il suo posto nella fila si richiude; il gettone tolto (col
 * «cancella» a testo vuoto o toccato) rifà il viaggio al contrario e torna pillola nel posto che la fila gli riapre.
 *
 * Il viaggio d'oro (F1B): la pillola, il volo e il gettone sono **un solo disegno** che cambia forma; in nessun fotogramma
 * si leggono la pillola e il gettone insieme. L'etichetta è la stessa scritta (lo stile del chip), quindi non cambia
 * identità e viaggia nitida; cambia solo il colore, dall'inchiostro del vetro a quello dell'accento.
 *
 * Apple non mostra il viaggio (in iOS il gettone suggerito compare nel campo; WWDC26 292, 13:46): i numeri del viaggio
 * sono **a occhio** (23 novembre 2026), sulla curva del campo che si apre (F4B.2), come scrive la riga «pillola →
 * gettone» del piano.
 */

/** Le misure del gettone e della fila dei gettoni suggeriti. A occhio (F6B.8b, 23 novembre 2026), dove non è detto. */
internal object FluidSearchTokenDefaults {
  /** L'altezza del gettone: 28 dp nel campo da 40, sei d'aria sopra e sotto (il gettone di iOS sta così nel campo). */
  val Height: Dp = 28.dp

  /** Il margine orizzontale dell'etichetta del gettone. */
  val HorizontalPadding: Dp = 10.dp

  /** Un gettone non è mai più stretto del bersaglio: 48 dp (anche «Al» si tocca senza mancarlo). */
  val MinWidth: Dp = FluidTargetMin

  /** Lo spazio fra due gettoni. */
  val Gap: Dp = 4.dp

  /** Lo spazio fra l'ultimo gettone e il testo. */
  val TextGap: Dp = 6.dp

  /**
   * Il testo che deve restare accanto ai gettoni: 64 dp, cinque o sei lettere e il cursore. Se i gettoni non ci stanno con
   * lui, i primi si raccolgono in «+N».
   */
  val MinTextRoom: Dp = 64.dp

  /** Lo spazio fra la fila delle pillole e la riga del campo. */
  val PillsToField: Dp = 8.dp

  /**
   * Il viaggio: la curva del campo che si apre (F4B.2, 334 ms, `CubicBezier(.38, 0, .35, 1)`, WWDC25 256), a occhio per
   * questo elemento: la pillola vola nel campo nello stesso tempo in cui il campo si apre.
   */
  const val FlightMillis: Int = 334

  /**
   * Il vetro che diventa gettone: fra il 35% e l'80% del viaggio il vetro della pillola svanisce e la tinta del gettone
   * entra (in uscita il contrario, fra il 20% e il 65%), con lo `smoothStep`. Prima e dopo è tutto vetro o tutto gettone.
   */
  const val MixFrom: Float = 0.35f
  const val MixSpan: Float = 0.45f
}

// ================================================================================================================
// Il modello (puro, provato al JVM)
// ================================================================================================================

/** Le regole dei gettoni della ricerca (F6B.8b). Pure: il campo e la fila le chiamano, le prove le leggono. */
internal object FluidSearchTokenRules {
  /** Il gettone di una pillola: lo stesso id e la stessa etichetta. */
  fun tokenOf(filter: FluidFilter): FluidToken = FluidToken(filter.id, filter.label)

  /**
   * La pillola [filter] scelta: il suo gettone in fondo. In [FluidFilterMode.Single] prende il posto dei gettoni della
   * stessa fila. Una pillola che è già gettone non cambia niente.
   */
  fun choose(tokens: List<FluidToken>, filter: FluidFilter, set: FluidFilterSet?): List<FluidToken> {
    if (tokens.any { it.id == filter.id }) return tokens
    val kept = if (set?.mode == FluidFilterMode.Single) {
      val ids = set.filters.mapTo(HashSet()) { it.id }
      tokens.filterNot { it.id in ids }
    } else tokens
    return kept + tokenOf(filter)
  }

  /** Il gettone [id] tolto (toccato, o col secondo «cancella»). */
  fun remove(tokens: List<FluidToken>, id: String): List<FluidToken> = tokens.filterNot { it.id == id }

  /** Il risultato di un «cancella» a testo vuoto: i gettoni, quello scelto, e se i gettoni sono cambiati. */
  data class Backspace(val tokens: List<FluidToken>, val selected: String?, val changed: Boolean)

  /**
   * «Cancella» con il cursore all'inizio del testo (come `UISearchTextField`): il primo colpo **sceglie** l'ultimo
   * gettone, il secondo lo toglie. Senza gettoni non succede niente.
   */
  fun backspace(tokens: List<FluidToken>, selected: String?): Backspace {
    val last = tokens.lastOrNull() ?: return Backspace(tokens, null, false)
    return if (selected == last.id) Backspace(tokens.dropLast(1), null, true) else Backspace(tokens, last.id, false)
  }

  /** La fila: le pillole di [set] che non sono gettoni, nell'ordine dichiarato. Si richiude e si riapre al loro posto. */
  fun pills(set: FluidFilterSet?, tokens: List<FluidToken>): List<FluidFilter> =
    set?.filters.orEmpty().filter { filter -> tokens.none { it.id == filter.id } }

  /**
   * Quanti gettoni, dall'inizio, si raccolgono nel gettone «+N» perché gli altri stiano in [room] (in dp: lo spazio del
   * testo meno [FluidSearchTokenDefaults.MinTextRoom]). [widths] sono le larghezze dei gettoni, [gap] lo spazio fra due,
   * [countWidth] la larghezza di «+N». L'ultimo gettone resta sempre in vista (è quello appena arrivato).
   */
  fun gathered(widths: List<Float>, room: Float, gap: Float, countWidth: (Int) -> Float): Int {
    val n = widths.size
    if (n == 0) return 0
    for (k in 0 until n) {
      val shown = n - k
      val items = shown + if (k > 0) 1 else 0
      val need = widths.subList(k, n).sum() + (if (k > 0) countWidth(k) else 0f) + gap * (items - 1)
      if (need <= room + 0.5f) return k
    }
    return n - 1
  }

  /** Gli id arrivati e quelli usciti fra due liste di gettoni. */
  data class Changes(val added: List<String>, val removed: List<String>)

  fun changes(before: List<String>, after: List<String>): Changes =
    Changes(after.filter { it !in before }, before.filter { it !in after })

  /**
   * Quanto il disegno in volo è gettone (0: tutto vetro della pillola, 1: tutto gettone) al progresso [progress] del
   * viaggio, verso il campo ([inward]) o verso la fila.
   */
  fun mix(progress: Float, inward: Boolean): Float =
    if (inward) smoothStep((progress - FluidSearchTokenDefaults.MixFrom) / FluidSearchTokenDefaults.MixSpan)
    else 1f - smoothStep((progress - (1f - FluidSearchTokenDefaults.MixFrom - FluidSearchTokenDefaults.MixSpan)) /
      FluidSearchTokenDefaults.MixSpan)
}

// ================================================================================================================
// Il palco del viaggio
// ================================================================================================================

/**
 * Un viaggio fra la fila e il campo (F6B.8b): [inward] verso il campo (la pillola diventa gettone) o verso la fila. [from]
 * è il riquadro di partenza, nelle coordinate della sovrapposizione; [index] il posto che il gettone aveva (per chi esce).
 */
@Stable
internal class ToolbarSearchTokenFlight(
  val token: FluidToken,
  val icon: ImageVector?,
  val inward: Boolean,
  val serial: Int,
  val from: Rect,
  val index: Int,
) {
  /** Il progresso del viaggio, già sulla curva (0 alla partenza, 1 all'arrivo). */
  val progress = Animatable(0f)

  /** Dov'è adesso il disegno in volo (scritto dal suo layout): da qui riparte un viaggio invertito. */
  internal var rect: Rect = from
}

/**
 * Un gettone che nasce o se ne va **senza** una pillola da cui venire o in cui tornare (F6B.8b): un gettone che l'app
 * aggiunge da un suggerimento o da sé, o che toglie e non è nella fila. Nasce al suo posto sfocato (R7 A6, chi nasce
 * senza predecessore) mentre il posto si apre, o esce sfocato (R7 A1) mentre il posto si richiude. [u] corre sul tempo di
 * R7 (`GlassContentDefaults`), fino a `EnterEnd` o a `ExitEnd`.
 */
@Stable
internal class ToolbarSearchTokenFade(val token: FluidToken, val entering: Boolean, val serial: Int) {
  val u = Animatable(0f)
  val end: Float get() = if (entering) GlassContentDefaults.EnterEnd else GlassContentDefaults.ExitEnd

  /** Quanto è aperto il posto: segue lo scambio (il posto è pieno quando il contenuto è a metà). */
  val opening: Float get() = (u.value / end).coerceIn(0f, 1f).let { if (entering) it else 1f - it }
}

/**
 * Il palco dei gettoni di un campo (F6B.8b): dove stanno le pillole e i posti dei gettoni (coordinate scritte dalla posa,
 * senza ricomporre), i viaggi in corso, il gettone scelto dal primo «cancella». Vive con la sovrapposizione della ricerca.
 */
@Stable
internal class ToolbarSearchTokenStage {
  internal var root: LayoutCoordinates? = null
  internal val pillPlaces = HashMap<String, LayoutCoordinates>()
  internal val tokenPlaces = HashMap<String, LayoutCoordinates>()
  internal val tokenWidths = HashMap<String, Float>()
  internal val pillWidths = HashMap<String, Float>()
  internal val pillHeights = HashMap<String, Float>()

  /** I viaggi in corso. */
  val flights = mutableStateListOf<ToolbarSearchTokenFlight>()

  /** Le nascite e le uscite al posto, senza pillola (R7). */
  val fades = mutableStateListOf<ToolbarSearchTokenFade>()

  /** Il gettone scelto dal primo «cancella» a testo vuoto. */
  var selected by mutableStateOf<String?>(null)

  /** I gettoni che escono, col posto che avevano: il campo ne tiene il posto finché il loro vetro non è tornato pillola. */
  var leaving by mutableStateOf<List<Pair<Int, FluidToken>>>(emptyList())
    private set

  private var known: List<FluidToken>? = null
  private var serial = 0

  fun flight(id: String): ToolbarSearchTokenFlight? = flights.lastOrNull { it.token.id == id }

  fun fade(id: String): ToolbarSearchTokenFade? = fades.lastOrNull { it.token.id == id }

  /** La nascita o l'uscita è finita. */
  fun endFade(fade: ToolbarSearchTokenFade) {
    fades.remove(fade)
    if (!fade.entering) leaving = leaving.filterNot { it.second.id == fade.token.id }
  }

  private fun rectOf(coordinates: LayoutCoordinates?, widthPx: Float?, heightPx: Float?): Rect? {
    val root = root?.takeIf { it.isAttached } ?: return null
    val place = coordinates?.takeIf { it.isAttached } ?: return null
    val box = root.localBoundingBoxOf(place, clipBounds = false)
    val w = widthPx ?: box.width
    val h = heightPx ?: box.height
    val top = box.center.y - h / 2f
    return Rect(box.left, top, box.left + w, top + h)
  }

  /** Il riquadro della pillola [id] nella fila (anche col suo posto ancora chiuso), o null. */
  fun pillRect(id: String): Rect? = rectOf(pillPlaces[id], pillWidths[id], pillHeights[id])

  /** Il riquadro del gettone [id] nel campo (anche col suo posto ancora chiuso), o null. */
  fun tokenRect(id: String, heightPx: Float): Rect? = rectOf(tokenPlaces[id], tokenWidths[id], heightPx)

  /**
   * In composizione, prima di comporre il campo e la fila: confronta i gettoni con quelli di prima e fa partire i viaggi
   * (F6B.8b). Una pillola sparita dalla fila e il suo disegno in volo nascono nella stessa composizione, quindi nessun
   * fotogramma resta senza l'una o l'altro. Con [animate] falso (movimento ridotto, campo chiuso) il cambio è di colpo.
   */
  fun sync(tokens: List<FluidToken>, filters: FluidFilterSet?, animate: Boolean, tokenHeightPx: Float) {
    val before = known
    known = tokens
    if (before == null) return
    val change = FluidSearchTokenRules.changes(before.map { it.id }, tokens.map { it.id })
    if (change.added.isEmpty() && change.removed.isEmpty()) return
    if (selected != null && tokens.none { it.id == selected }) selected = null
    val icons = filters?.filters.orEmpty().associate { it.id to it.icon }
    for (id in change.removed) {
      val index = before.indexOfFirst { it.id == id }
      val token = before[index]
      val running = flight(id)
      val returns = filters?.filters?.any { it.id == id } == true
      val from = running?.rect ?: tokenRect(id, tokenHeightPx)
      flights.removeAll { it.token.id == id }
      fades.removeAll { it.token.id == id }
      leaving = leaving.filterNot { it.second.id == id }
      if (animate && returns && from != null) {
        flights += ToolbarSearchTokenFlight(token, icons[id], inward = false, serial = ++serial, from = from, index = index)
        leaving = leaving + (index to token)
      } else if (animate && tokenPlaces[id]?.isAttached == true) {
        // Non torna nella fila: esce sfocato dove stava, e il suo posto si richiude.
        fades += ToolbarSearchTokenFade(token, entering = false, serial = ++serial)
        leaving = leaving + (index to token)
      }
    }
    for (id in change.added) {
      val token = tokens.first { it.id == id }
      val running = flight(id)
      val from = running?.rect ?: pillRect(id)
      flights.removeAll { it.token.id == id }
      fades.removeAll { it.token.id == id }
      leaving = leaving.filterNot { it.second.id == id }
      if (animate && from != null) {
        flights += ToolbarSearchTokenFlight(token, icons[id], inward = true, serial = ++serial, from = from, index = -1)
      } else if (animate) {
        // Nessuna pillola da cui venire (un suggerimento, l'app): nasce sfocato al suo posto, che si apre.
        fades += ToolbarSearchTokenFade(token, entering = true, serial = ++serial)
      }
    }
  }

  /** Il viaggio è arrivato: il gettone (o la pillola) prende il suo posto nello stesso fotogramma in cui il volo sparisce. */
  fun land(flight: ToolbarSearchTokenFlight) {
    flights.remove(flight)
    if (!flight.inward) leaving = leaving.filterNot { it.second.id == flight.token.id }
  }

  /** Quanto è aperto il posto del gettone [id] nel campo: 0 chiuso, 1 aperto. */
  fun tokenOpening(id: String): Float {
    fade(id)?.let { return it.opening }
    val f = flight(id) ?: return 1f
    return if (f.inward) f.progress.value else 1f - f.progress.value
  }

  /** Quanto è aperto il posto della pillola [id] nella fila. */
  fun pillOpening(id: String): Float {
    val f = flight(id) ?: return 1f
    return if (f.inward) 1f - f.progress.value else f.progress.value
  }
}

@Composable
internal fun rememberToolbarSearchTokenStage(): ToolbarSearchTokenStage = remember { ToolbarSearchTokenStage() }

// ================================================================================================================
// Il gettone
// ================================================================================================================

/** La tinta del gettone: l'accento, come il `tokenBackgroundColor` di iOS (di serie la tinta del campo). */
@Composable
internal fun fluidSearchTokenFill(selected: Boolean): Color =
  if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary

/** L'inchiostro dell'etichetta del gettone. */
@Composable
internal fun fluidSearchTokenInk(selected: Boolean): Color =
  if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary

/** Lo stile dell'etichetta: quello del chip, così la scritta viaggia dalla pillola al gettone senza cambiare. */
@Composable
internal fun fluidSearchTokenStyle(): TextStyle = fluidChipLabelStyle()

/** La faccia di un gettone o di una pillola: il simbolo e l'etichetta, nell'inchiostro [ink]. */
@Composable
internal fun FluidSearchTokenFace(label: String, icon: ImageVector?, ink: () -> Color) {
  Row(horizontalArrangement = Arrangement.spacedBy(FluidChipContentGap), verticalAlignment = Alignment.CenterVertically) {
    if (icon != null) Box(Modifier.size(FluidChipGlyphSize), contentAlignment = Alignment.Center) {
      FluidIcon(icon, null, tint = ink(), size = FluidChipGlyphSize)
    }
    BasicText(label, style = fluidSearchTokenStyle(), color = { ink() }, maxLines = 1, softWrap = false)
  }
}

/** Il gettone disegnato: la capsula tinta e la faccia. Il bersaglio lo decide chi lo posa. */
@Composable
internal fun FluidSearchTokenCapsule(label: String, icon: ImageVector?, selected: Boolean, modifier: Modifier = Modifier) {
  val fill = fluidSearchTokenFill(selected)
  val ink = fluidSearchTokenInk(selected)
  Box(
    modifier
      .defaultMinSize(minWidth = FluidSearchTokenDefaults.MinWidth)
      .height(FluidSearchTokenDefaults.Height)
      .background(fill, FluidCapsuleShape)
      .padding(horizontal = FluidSearchTokenDefaults.HorizontalPadding),
    contentAlignment = Alignment.Center,
  ) { FluidSearchTokenFace(label, icon) { ink } }
}

/** La larghezza naturale di un gettone, in dp: margini, simbolo e scritta, almeno [FluidSearchTokenDefaults.MinWidth]. */
@Composable
internal fun rememberFluidSearchTokenWidths(tokens: List<FluidToken>, icons: Map<String, ImageVector?>): Map<String, Float> {
  val measurer = rememberTextMeasurer()
  val style = fluidSearchTokenStyle()
  val density = LocalDensity.current
  return tokens.associate { token ->
    val text = measurer.measure(token.label, style, maxLines = 1, softWrap = false).size.width / density.density
    val glyph = if (icons[token.id] != null) FluidChipGlyphSize.value + FluidChipContentGap.value else 0f
    token.id to maxOf(FluidSearchTokenDefaults.MinWidth.value, 2 * FluidSearchTokenDefaults.HorizontalPadding.value + glyph + text)
  }
}

/** La larghezza di «+N», in dp. */
@Composable
internal fun rememberFluidSearchCountWidth(): (Int) -> Float {
  val measurer = rememberTextMeasurer()
  val style = fluidSearchTokenStyle()
  val density = LocalDensity.current
  return remember(measurer, style, density) {
    { count ->
      val text = measurer.measure("+$count", style, maxLines = 1, softWrap = false).size.width / density.density
      maxOf(FluidSearchTokenDefaults.Height.value, 2 * FluidSearchTokenDefaults.HorizontalPadding.value + text)
    }
  }
}

/**
 * Un posto che si apre e si chiude (F6B.8b): misura il contenuto alla sua larghezza naturale e dice al genitore quella
 * larghezza (più [trailing]) moltiplicata per [opening], letto nel layout: il posto segue il viaggio senza ricomporre.
 * [onNatural] riceve la larghezza naturale in px.
 */
internal fun Modifier.fluidTokenSlot(opening: () -> Float, trailing: Dp, onNatural: (Float) -> Unit = {}): Modifier =
  layout { measurable, constraints ->
    val child = measurable.measure(Constraints(maxHeight = constraints.maxHeight))
    onNatural(child.width.toFloat())
    val full = child.width + trailing.roundToPx()
    val w = (full * opening().coerceIn(0f, 1f)).roundToInt()
    layout(w, child.height) { child.placeRelative(0, 0) }
  }

// ================================================================================================================
// I gettoni nel campo
// ================================================================================================================

/**
 * I gettoni dentro il campo, prima del testo (F6B.8b). [roomDp] è lo spazio dei gettoni (lo spazio del testo meno
 * [FluidSearchTokenDefaults.MinTextRoom]): i primi che non ci stanno si raccolgono in «+N». Ogni gettone sta in un posto
 * che si apre col suo viaggio; mentre vola il gettone non si disegna (è il disegno in volo). Toccato, a campo aperto, si
 * toglie e torna pillola.
 */
@Composable
internal fun ToolbarSearchTokenStrip(
  stage: ToolbarSearchTokenStage,
  spec: FluidSearchSpec,
  roomDp: Float,
  interactive: Boolean,
  labels: FluidSearchLabels,
) {
  val icons = spec.filters?.filters.orEmpty().associate { it.id to it.icon }
  // I gettoni in vista e quelli che escono, al posto che avevano.
  val shown = ArrayList(spec.tokens)
  for ((index, token) in stage.leaving.sortedBy { it.first }) {
    if (shown.none { it.id == token.id }) shown.add(index.coerceIn(0, shown.size), token)
  }
  val widths = rememberFluidSearchTokenWidths(shown, icons)
  val countWidth = rememberFluidSearchCountWidth()
  val gap = FluidSearchTokenDefaults.Gap.value
  val steady = spec.tokens.filter { t -> stage.flight(t.id)?.inward != true }
  val gather = FluidSearchTokenRules.gathered(steady.map { widths.getValue(it.id) }, roomDp, gap, countWidth)
  val gatheredIds = steady.take(gather).mapTo(HashSet()) { it.id }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val motionScale = LocalFluidMotionPolicy.current.durationScale
  val blurAvailable = android.os.Build.VERSION.SDK_INT >= 31
  val onChange = spec.onTokensChange
  Row(verticalAlignment = Alignment.CenterVertically) {
    // «+N»: i gettoni raccolti. Non si tocca; TalkBack ha un'azione per toglierne ognuno.
    val countOpen by animateFloatAsState(if (gather > 0) 1f else 0f, if (reduced) snap() else FluidMotion.snappy(), label = "gettoni raccolti")
    if (gather > 0 || countOpen > 0.001f) {
      val gathered = steady.take(gather)
      Box(Modifier.fluidTokenSlot({ countOpen }, FluidSearchTokenDefaults.Gap).graphicsLayer { alpha = countOpen }
        .semantics {
          contentDescription = labels.tokensGathered(gathered.size) + ": " + gathered.joinToString { it.label }
          if (onChange != null && interactive) customActions = gathered.map { token ->
            CustomAccessibilityAction("${labels.tokenRemove} ${token.label}") {
              onChange(FluidSearchTokenRules.remove(spec.tokens, token.id)); true
            }
          }
        }) {
        FluidGlassContentTransition(gathered.size.takeIf { it > 0 }) { count ->
          val countInk = LocalContentColor.current
          if (count != null) Box(
            Modifier.height(FluidSearchTokenDefaults.Height).defaultMinSize(minWidth = FluidSearchTokenDefaults.Height)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), FluidCapsuleShape)
              .padding(horizontal = FluidSearchTokenDefaults.HorizontalPadding),
            contentAlignment = Alignment.Center,
          ) { BasicText("+$count", style = fluidSearchTokenStyle(), color = { countInk }, maxLines = 1, softWrap = false) }
        }
      }
    }
    for (token in shown) key(token.id) {
      val gatheredNow = token.id in gatheredIds
      val stay by animateFloatAsState(if (gatheredNow) 0f else 1f, if (reduced) snap() else FluidMotion.snappy(), label = "gettone in vista")
      val selected = stage.selected == token.id
      val icon = icons[token.id]
      val fade = stage.fade(token.id)
      if (fade != null) LaunchedEffect(fade) {
        fade.u.animateTo(fade.end, tween((GlassContentDefaults.DurationMs * fade.end * motionScale).roundToInt(),
          easing = androidx.compose.animation.core.LinearEasing))
        stage.endFade(fade)
      }
      Box(
        Modifier
          .fluidTokenSlot({ stage.tokenOpening(token.id) * stay }, FluidSearchTokenDefaults.Gap) { stage.tokenWidths[token.id] = it }
          .onPlaced { stage.tokenPlaces[token.id] = it }
          .then(if (fade == null) Modifier else Modifier.graphicsLayer {
            // R7: chi nasce senza predecessore (A6) o chi esce (A1), sfocato, sulla stessa curva dei contenuti del vetro.
            val role = if (fade.entering) GlassContentRole.Born else GlassContentRole.Exit
            val u = fade.u.value
            alpha = GlassContentCurves.alpha(role, u, blurAvailable)
            scaleX = GlassContentCurves.scale(role, u)
            scaleY = scaleX
            val radius = glassContentBlurRadiusPx(glassContentSigmaPx(GlassContentCurves.blur(role, u) *
              GlassContentDefaults.PeakBlur, FluidSearchTokenDefaults.Height.toPx()))
            renderEffect = if (blurAvailable && radius > 0.05f) BlurEffect(radius, radius, TileMode.Decal) else null
          }),
      ) {
        val visible = stage.flight(token.id) == null && !gatheredNow && (fade == null || fade.entering)
        val target = FluidTargetOutset(vertical = ((FluidTargetMin - FluidSearchTokenDefaults.Height) / 2))
        Box(
          Modifier
            .graphicsLayer { alpha = if (visible) stay else 0f }
            .then(
              if (interactive && onChange != null && visible) {
                Modifier
                  .fluidTargetOutset(target)
                  .fluidPressable(
                    onClick = {
                      stage.selected = null
                      onChange(FluidSearchTokenRules.remove(spec.tokens, token.id))
                    },
                    role = Role.Button,
                    feedback = FluidPressFeedback.Expand,
                    focusShape = FluidCapsuleShape,
                  )
                  .semantics {
                    contentDescription = token.label
                    this.selected = selected
                    if (selected) stateDescription = labels.tokenSelected
                  }
                  .fluidTargetInner(target)
              } else {
                Modifier.clearAndSetSemantics { if (visible) contentDescription = token.label }
              },
            ),
        ) { FluidSearchTokenCapsule(token.label, icon, selected) }
      }
    }
    Spacer(Modifier.width(FluidSearchTokenDefaults.TextGap - FluidSearchTokenDefaults.Gap))
  }
}

/** Il campo di testo con i gettoni: il buffer della tastiera ha un carattere di guardia all'inizio, mai disegnato. */
internal val SearchTokenSentinelTransformation = VisualTransformation { text ->
  TransformedText(AnnotatedString(text.text.drop(1)), object : OffsetMapping {
    override fun originalToTransformed(offset: Int) = (offset - 1).coerceAtLeast(0)
    override fun transformedToOriginal(offset: Int) = offset + 1
  })
}

// ================================================================================================================
// La fila delle pillole
// ================================================================================================================

/**
 * La fila dei gettoni suggeriti (F6B.8b): una pillola di vetro per ogni filtro di [FluidSearchSpec.filters] che non è
 * gettone, nell'ordine dichiarato. Il posto di una pillola che vola nel campo si richiude col viaggio, e si riapre per
 * quella che torna; mentre vola la pillola non si disegna (è il disegno in volo). Scorre di lato se non ci sta.
 */
@Composable
internal fun ToolbarSearchTokenPills(stage: ToolbarSearchTokenStage, spec: FluidSearchSpec, modifier: Modifier = Modifier) {
  val set = spec.filters ?: return
  val onChange = spec.onTokensChange
  val tokens = spec.tokens
  val scroll = rememberScrollState()
  Row(
    // Senza lo stiramento della piattaforma (F6B.15), come la lista di `FluidScreen`: sul Tab S9 la prima pillola di vetro
    // della fila in alto restava un vetro vuoto, col bordo sinistro velato, mentre le altre si leggevano.
    modifier
      // Per le sessioni sul dispositivo (F6B.15): il dito arriva alla fila? (solo osservato, mai consumato)
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics.event("tokens.touch", scroll) {
              "type=${event.type} at=${event.changes.firstOrNull()?.position} flights=${stage.flights.size}"
            }
          }
        }
      }
      .horizontalScroll(scroll, overscrollEffect = null)
      .drawWithContent {
        // Per le sessioni sul dispositivo: dove sta la fila e quanto è scorsa.
        dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics.event("tokens.row", scroll) {
          "scroll=${scroll.value}/${scroll.maxValue} width=${size.width} pills=${set.filters.size}"
        }
        drawContent()
      },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    for (filter in set.filters) key(filter.id) {
      val isToken = tokens.any { it.id == filter.id }
      val flight = stage.flight(filter.id)
      // Un gettone fermo non sta nella fila; quello che vola nel campo ci lascia il posto che si richiude.
      if (!isToken || flight != null) {
        Box(
          Modifier
            .fluidTokenSlot({ if (isToken && flight == null) 0f else stage.pillOpening(filter.id) }, FluidFilterChipsDefaults.Spacing) {
              stage.pillWidths[filter.id] = it
            }
            .onPlaced { coordinates ->
              stage.pillPlaces[filter.id] = coordinates
              stage.pillHeights[filter.id] = coordinates.size.height.toFloat()
            },
        ) {
          val visible = flight == null && !isToken
          Box(Modifier.graphicsLayer { alpha = if (visible) 1f else 0f }) {
            FluidChipCore(
              label = filter.label,
              selected = false,
              onClick = {
                if (onChange != null && visible) {
                  stage.selected = null
                  onChange(FluidSearchTokenRules.choose(spec.tokens, filter, set))
                }
              },
              modifier = Modifier.defaultMinSize(minWidth = FluidChipMinTarget)
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics { }),
              enabled = filter.enabled && onChange != null,
              leading = filter.icon?.let { icon -> { FluidIcon(icon, null, size = FluidChipGlyphSize) } },
            )
          }
        }
      }
    }
  }
}

// ================================================================================================================
// Il viaggio
// ================================================================================================================

/**
 * I disegni in volo (F6B.8b), sopra la sovrapposizione della ricerca: uno per viaggio. Ognuno parte dal riquadro di
 * partenza e insegue il posto d'arrivo **dov'è adesso** (il campo sale con la tastiera, la fila scorre), letto nel layout.
 */
@Composable
internal fun BoxScope.ToolbarSearchTokenFlights(stage: ToolbarSearchTokenStage, backdrop: GlassBackdropState?) {
  if (stage.flights.isEmpty()) return
  Box(Modifier.matchParentSize()) {
    for (flight in stage.flights.toList()) key(flight.serial) { ToolbarSearchTokenFlyer(stage, flight, backdrop) }
  }
}

@Composable
private fun ToolbarSearchTokenFlyer(stage: ToolbarSearchTokenStage, flight: ToolbarSearchTokenFlight, backdrop: GlassBackdropState?) {
  val motion = LocalFluidMotionPolicy.current
  LaunchedEffect(flight) {
    flight.progress.animateTo(1f, tween((FluidSearchTokenDefaults.FlightMillis * motion.durationScale).roundToInt(),
      easing = androidx.compose.animation.core.CubicBezierEasing(.38f, 0f, .35f, 1f)))
    stage.land(flight)
  }
  val density = LocalDensity.current
  val tokenHeightPx = with(density) { FluidSearchTokenDefaults.Height.toPx() }
  val scheme = MaterialTheme.colorScheme
  val fill = scheme.primary
  val tokenInk = scheme.onPrimary
  val glassInk = rememberGlassInk()
  val mix = { FluidSearchTokenRules.mix(flight.progress.value, flight.inward) }
  Box(
    Modifier
      .layout { measurable, constraints ->
        val p = flight.progress.value
        val target = if (flight.inward) stage.tokenRect(flight.token.id, tokenHeightPx) else stage.pillRect(flight.token.id)
        val rect = if (target == null) flight.rect else lerp(flight.from, target, p)
        flight.rect = rect
        val child = measurable.measure(Constraints.fixed(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)))
        layout(constraints.maxWidth, constraints.maxHeight) { child.place(rect.left.roundToInt(), rect.top.roundToInt()) }
      }
      .clearAndSetSemantics { },
    contentAlignment = Alignment.Center,
  ) {
    // Il vetro della pillola, che svanisce entrando nel campo (e torna uscendo).
    if (backdrop != null) Box(
      Modifier.matchParentSize().graphicsLayer { alpha = 1f - mix() }.glassControlSurfaceInked(
        backdrop = backdrop, shape = FluidCapsuleShape, tint = GlassDefaults.controlTint(), optics = GlassDefaults.buttonOptics(),
        selected = false, interactive = false, depth = { 1f }, frozen = { false }, heldWhileMoving = { false },
        pressState = null, ink = glassInk,
      ),
    ) else Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - mix() }
      .background(scheme.onSurface.copy(alpha = 0.06f), FluidCapsuleShape))
    // La tinta del gettone, che entra.
    Box(Modifier.matchParentSize().graphicsLayer { alpha = mix() }.background(fill, FluidCapsuleShape))
    GlassInkContent(glassInk, LocalContentColor.current) {
      val pillInk = LocalContentColor.current
      FluidSearchTokenFace(flight.token.label, flight.icon) { lerp(pillInk, tokenInk, mix()) }
    }
  }
}
