package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.haptics.NoOpFluidHaptics
import dev.antigravity.fluidengine.ui.page.FluidPageMotion
import dev.antigravity.fluidengine.ui.theme.fluidLegibleInk
import kotlin.math.abs
import kotlin.math.atanh
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Un'azione che una riga rivela scorrendo di lato: archivia, segna come letta, elimina, fissa.
 *
 * Il colore e l'icona li sceglie chi chiama (l'engine non sa cosa vuol dire «archivia» in un'app), il colore
 * dell'icona no: lo sceglie il contrasto col colore dell'azione (≥ 4,5:1, lo stesso conto della pillola di
 * stato), cosi' un giallo chiaro non porta un'icona bianca che non si legge.
 *
 * @param label il nome dell'azione: e' quello che TalkBack offre fra le azioni della riga, perche' un gesto che
 *   solo chi vede puo' fare non e' un'azione, e' un trucco.
 * @param icon l'icona rivelata dietro la riga.
 * @param color il fondo rivelato quando la soglia e' superata (prima e' un grigio quieto: la soglia si vede).
 * @param removesItem vero: confermata, la riga esce del tutto verso il lato e [onAction] parte a uscita finita;
 *   l'app toglie il dato e la lista richiude il posto (con [fluidGroupedItems], `animateItem`). Falso: [onAction]
 *   parte al rilascio e la riga torna a posto (letta/non letta, fissa).
 * @param onAction l'azione. Parte una volta per gesto, anche se la lista cambia durante l'uscita.
 */
@Immutable
class FluidSwipeAction(
  val label: String,
  val icon: ImageVector,
  val color: Color,
  val removesItem: Boolean = false,
  val onAction: () -> Unit,
)

/**
 * Lo stato di una riga a scorrimento: dove sta, se la soglia e' superata, se e' uscita.
 *
 * Effimero di proposito: un gesto a meta' non sopravvive a una rotazione, e una riga riciclata o uscita dallo
 * schermo torna a posto (lo stato muore con l'item). Si crea con [rememberFluidSwipeState] **dentro** l'item
 * della lista, cosi' ogni riga ha il suo e la chiave della riga lo tiene.
 */
@Stable
class FluidSwipeState internal constructor(private val scope: CoroutineScope) {

  /**
   * Lo spostamento della riga in px, **logico**: positivo verso la fine (rivela l'azione iniziale, a sinistra
   * in LTR), negativo verso l'inizio. Letto nel disegno, mai nella composizione.
   */
  var offset: Float by mutableFloatStateOf(0f)
    private set

  /** La soglia e' superata: rilasciando adesso l'azione parte. */
  var armed: Boolean by mutableStateOf(false)
    private set

  /**
   * La riga e' uscita (un'azione con `removesItem` confermata) e aspetta che l'app la tolga. Non risponde al dito
   * finche' non la si rimette con [reset].
   */
  var isDismissed: Boolean by mutableStateOf(false)
    private set

  /** Quanto e' acceso il colore dell'azione (0 grigio, 1 pieno): segue la soglia con un lampo. */
  internal val armProgress = Animatable(0f)

  internal var widthPx: Int = 0
  internal var coordinates: LayoutCoordinates? = null
  internal var config: FluidSwipeConfig = FluidSwipeConfig.Disabled

  private var rawPx = 0f
  private var settle: Job? = null
  private var firedThisGesture = false

  /**
   * Rimette la riga a posto con la molla (istantanea col movimento ridotto): per un'azione che l'app non ha potuto
   * fare, o per l'«Annulla» di una riga uscita che resta nei dati.
   */
  suspend fun reset() {
    settle?.cancel()
    settle = null
    isDismissed = false
    firedThisGesture = false
    disarm(silent = true)
    if (config.reducedMotion) {
      offset = 0f
    } else {
      animate(initialValue = offset, targetValue = 0f, animationSpec = FluidMotion.swipeSettle()) { value, _ ->
        offset = value
      }
    }
    offset = 0f
    rawPx = 0f
  }

  private val thresholdPx: Float
    get() = fluidSwipeThresholdPx(widthPx.toFloat(), config.density)

  /** Il dito ha preso la riga: se stava ancora tornando la si riprende da dov'e'. */
  internal fun beginDrag() {
    settle?.cancel()
    settle = null
    firedThisGesture = false
    rawPx = fluidSwipeRawPx(
      offsetPx = offset,
      thresholdPx = thresholdPx,
      elasticPx = FluidSwipeElasticDp * config.density,
      hasStart = config.startAction != null,
      hasEnd = config.endAction != null,
    )
  }

  /** [deltaPx] logico (positivo verso la fine). */
  internal fun dragBy(deltaPx: Float) {
    rawPx += deltaPx
    val threshold = thresholdPx
    offset = fluidSwipeOffsetPx(
      rawPx = rawPx,
      thresholdPx = threshold,
      elasticPx = FluidSwipeElasticDp * config.density,
      hasStart = config.startAction != null,
      hasEnd = config.endAction != null,
      reducedMotion = config.reducedMotion,
    )
    val nowArmed = fluidSwipeArmed(
      wasArmed = armed,
      offsetPx = offset,
      thresholdPx = threshold,
      hysteresisPx = FluidSwipeHysteresisDp * config.density,
      sideHasAction = actionFor(offset) != null,
    )
    if (nowArmed != armed) {
      armed = nowArmed
      // Un momento, un colpo: la soglia attraversata, in un verso o nell'altro (`Threshold`, che la rete dei
      // 70 ms fonde con qualunque altro colpo dello stesso istante).
      config.haptics.play(FluidHapticEvent.Threshold)
      animateArm(if (nowArmed) 1f else 0f)
    }
  }

  /** Il dito si e' alzato ([cancelled] falso) o e' sparito (il sistema se l'e' preso, la riga e' andata). */
  internal fun release(velocityPx: Float, cancelled: Boolean) {
    val decision = if (cancelled) {
      FluidSwipeRelease.Cancel
    } else {
      fluidSwipeRelease(
        offsetPx = offset,
        velocityPx = velocityPx,
        armed = armed,
        thresholdPx = thresholdPx,
        flingPx = FluidPageMotion.FlingDpPerSecond * config.density,
        hasStart = config.startAction != null,
        hasEnd = config.endAction != null,
      )
    }
    when (decision) {
      FluidSwipeRelease.Cancel -> settleHome(velocityPx)
      FluidSwipeRelease.Start -> config.startAction?.let { commit(it, sign = 1f, velocityPx = velocityPx, fromGesture = true) }
      FluidSwipeRelease.End -> config.endAction?.let { commit(it, sign = -1f, velocityPx = velocityPx, fromGesture = true) }
    }
  }

  /** L'azione chiesta da TalkBack: lo stesso esito del gesto, senza gesto. */
  internal fun perform(start: Boolean): Boolean {
    if (isDismissed) return false
    val action = (if (start) config.startAction else config.endAction) ?: return false
    firedThisGesture = false
    commit(action, sign = if (start) 1f else -1f, velocityPx = 0f, fromGesture = false)
    return true
  }

  private fun actionFor(offsetPx: Float): FluidSwipeAction? = when {
    offsetPx > 0f -> config.startAction
    offsetPx < 0f -> config.endAction
    else -> null
  }

  private fun commit(action: FluidSwipeAction, sign: Float, velocityPx: Float, fromGesture: Boolean) {
    if (firedThisGesture) return
    firedThisGesture = true
    if (!fromGesture || !armed) {
      // Una spinta che conferma prima della soglia, o TalkBack: la soglia non si e' sentita, si sente adesso.
      if (fromGesture) config.haptics.play(FluidHapticEvent.Threshold)
      animateArm(1f)
    }
    if (!action.removesItem) {
      action.onAction()
      settleHome(velocityPx)
      return
    }
    isDismissed = true
    settle?.cancel()
    val target = sign * max(widthPx, 1)
    settle = scope.launch {
      if (config.reducedMotion) {
        offset = target
      } else {
        animate(
          initialValue = offset,
          targetValue = target,
          initialVelocity = velocityPx,
          animationSpec = FluidMotion.swipeExit(),
        ) { value, _ -> offset = value }
      }
      offset = target
      action.onAction()
    }
  }

  private fun settleHome(velocityPx: Float) {
    disarm(silent = true)
    settle?.cancel()
    settle = scope.launch {
      if (config.reducedMotion) {
        offset = 0f
      } else {
        animate(
          initialValue = offset,
          targetValue = 0f,
          initialVelocity = velocityPx,
          animationSpec = FluidMotion.swipeSettle(),
        ) { value, _ -> offset = value }
      }
      offset = 0f
      rawPx = 0f
    }
  }

  private fun disarm(silent: Boolean) {
    if (!armed && armProgress.targetValue == 0f) return
    armed = false
    if (!silent) config.haptics.play(FluidHapticEvent.Threshold)
    animateArm(0f)
  }

  private fun animateArm(target: Float) {
    scope.launch {
      if (config.reducedMotion) armProgress.snapTo(target) else armProgress.animateTo(target, FluidMotion.swipeArm())
    }
  }

  /** Il primo tocco cade nella zona del bordo (back dell'engine, gesto di sistema)? Allora non e' della riga. */
  internal fun startsInEdge(localX: Float, localY: Float): Boolean {
    val coords = coordinates ?: return false
    if (!coords.isAttached) return false
    val root = coords.findRootCoordinates()
    val inRoot = root.localPositionOf(coords, androidx.compose.ui.geometry.Offset(localX, localY))
    return fluidSwipeStartsInEdge(
      xInRoot = inRoot.x,
      rootWidth = root.size.width.toFloat(),
      leftExclusionPx = config.leftExclusionPx,
      rightExclusionPx = config.rightExclusionPx,
    )
  }
}

/** Quello che il gesto legge al momento del tocco: aggiornato a ogni composizione, mai causa di un riavvio. */
internal class FluidSwipeConfig(
  val startAction: FluidSwipeAction?,
  val endAction: FluidSwipeAction?,
  val enabled: Boolean,
  val haptics: FluidHaptics,
  val reducedMotion: Boolean,
  val density: Float,
  val leftExclusionPx: Float,
  val rightExclusionPx: Float,
  val layoutDirection: LayoutDirection,
) {
  companion object {
    val Disabled = FluidSwipeConfig(null, null, false, NoOpFluidHaptics, false, 1f, 0f, 0f, LayoutDirection.Ltr)
  }
}

/** Lo stato di una riga a scorrimento, ricordato nell'item. */
@Composable
fun rememberFluidSwipeState(): FluidSwipeState {
  val scope = rememberCoroutineScope()
  return remember(scope) { FluidSwipeState(scope) }
}

/**
 * Azioni a scorrimento orizzontale su una riga: un'azione per verso, sfondo col colore e l'icona dell'azione,
 * soglia che si sente, annullabile tornando indietro col dito, uscita o ritorno al rilascio, e le stesse azioni
 * per TalkBack.
 *
 * **Va sul nodo della riga.** Per [dev.antigravity.fluidengine.ui.theme.FluidListRow] e' il suo `modifier`, che
 * sta sul `ListItem` stesso (un nodo solo): li' le azioni di TalkBack si aggiungono a quelle della riga invece di
 * finire su un discendente, dove nessuno le raggiungerebbe. Dentro [fluidGroupedItems] si passano la sagoma e il
 * fondo del pezzo: `Modifier.fluidSwipeActions(state, ..., shape = segmentShape, fill = rowFill)`.
 *
 * ### Chi ha il dito
 *
 * - **Lo scorrimento verticale vince sempre.** Il gesto si decide alla soglia di tocco: orizzontale solo se
 *   nettamente piu' orizzontale che verticale (una volta e mezza); un primo movimento verticale lascia il dito
 *   alla lista, e un diagonale non lo prende nessuno dei due per conto della riga.
 * - **Il bordo non e' della riga.** Un contatto nei primi 20 dp di ciascun bordo dello schermo
 *   ([FluidPageMotion.EdgeZoneDp], la zona del back dell'engine) o dentro gli inset dei gesti di sistema non avvia
 *   lo scorrimento: li' vincono il back dell'engine e il back di sistema. La riga non chiede mai un'esclusione dai
 *   gesti di sistema.
 * - **La pressione lunga resta del menu.** Un dito fermo oltre il tempo del click lungo e' del menu contestuale
 *   (che poi si prende il dito); un trascinamento orizzontale preso dalla riga annulla il click lungo e il click.
 * - **Un tasto nella riga tiene il dito** come sempre: se il gesto e' gia' suo, la riga non parte.
 * - Un secondo dito, o un dito che sparisce (il sistema se l'e' preso), e' un annullo.
 *
 * ### Il gesto
 *
 * Segue il dito 1:1 fino alla soglia (il 35% della larghezza, al piu' 180 dp: le soglie del back a dito), poi con
 * resistenza; dal lato senza azione solo un elastico breve (niente, col movimento ridotto). La soglia attraversata
 * suona `Threshold`, in un verso e nell'altro, con un'isteresi di 12 dp perche' un dito che trema sulla soglia non
 * vibri a raffica. Al rilascio: oltre la soglia, o con una spinta di 400 dp/s verso l'azione da almeno meta'
 * soglia, conferma; una spinta contraria annulla anche oltre la soglia (il dito ha cambiato idea). Le molle
 * partono dalla velocita' del dito ([FluidMotion.swipeSettle], [FluidMotion.swipeExit]) e col movimento ridotto
 * diventano un salto.
 *
 * ### Dove si rompe
 *
 * - La riga disegna il proprio fondo ([fill]) mentre si sposta, perche' dietro c'e' il colore dell'azione: un fondo
 *   diverso da quello del gruppo si vede come una riga di un altro colore. Con [fluidGroupedItems] passa `rowFill`.
 * - Lo spostamento e' di disegno: tocco e semantica restano dove la riga sta a riposo (durante un gesto non serve
 *   altro, e una riga uscita non si tocca).
 * - Una riga uscita ([FluidSwipeState.isDismissed]) che l'app non toglie resta vuota: l'app la rimette con
 *   [FluidSwipeState.reset].
 * - Altre azioni di TalkBack sulla stessa riga si **sommano** a queste (`FluidListRow(customActions = …)` o
 *   [fluidAccessibilityActions]), in qualunque ordine si mettano i modificatori.
 *
 * @param state lo stato, da [rememberFluidSwipeState] dentro l'item.
 * @param startAction l'azione rivelata trascinando verso la fine (a destra in LTR, a sinistra in RTL).
 * @param endAction l'azione rivelata trascinando verso l'inizio.
 * @param enabled spento: niente gesto e niente azioni di TalkBack (in selezione, per esempio).
 * @param shape la sagoma della riga, su cui si ritaglia cio' che si rivela (il pezzo di un gruppo).
 * @param fill il fondo della riga mentre si sposta; non specificato: quello opaco di un gruppo.
 * @param accessibilityActions le azioni anche per TalkBack, sul nodo della riga. Falso solo se l'app le offre in
 *   un altro modo sullo stesso nodo.
 */
@Composable
fun Modifier.fluidSwipeActions(
  state: FluidSwipeState,
  startAction: FluidSwipeAction? = null,
  endAction: FluidSwipeAction? = null,
  enabled: Boolean = true,
  shape: Shape = RectangleShape,
  fill: Color = Color.Unspecified,
  accessibilityActions: Boolean = true,
): Modifier {
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val scheme = MaterialTheme.colorScheme
  val gestureInsets = WindowInsets.systemGestures
  val edgePx = with(density) { FluidPageMotion.EdgeZoneDp.dp.toPx() }
  val active = enabled && (startAction != null || endAction != null)

  // Scritto in composizione: il gesto lo legge al tocco, senza ripartire quando cambia.
  state.config = FluidSwipeConfig(
    startAction = startAction,
    endAction = endAction,
    enabled = active,
    haptics = haptics,
    reducedMotion = reducedMotion,
    density = density.density,
    leftExclusionPx = max(edgePx, gestureInsets.getLeft(density, LayoutDirection.Ltr).toFloat()),
    rightExclusionPx = max(edgePx, gestureInsets.getRight(density, LayoutDirection.Ltr).toFloat()),
    layoutDirection = LocalLayoutDirection.current,
  )

  val rowFill = if (fill.isSpecified) fill else scheme.surfaceContainerLow
  val neutral = scheme.surfaceContainerHighest
  val neutralInk = scheme.onSurfaceVariant
  val startPainter = startAction?.let { rememberVectorPainter(it.icon) }
  val endPainter = endAction?.let { rememberVectorPainter(it.icon) }
  val startInk = startAction?.let { fluidLegibleInk(it.color.compositeOver(scheme.surface)) }
  val endInk = endAction?.let { fluidLegibleInk(it.color.compositeOver(scheme.surface)) }
  val currentStart by rememberUpdatedState(startAction)
  val currentEnd by rememberUpdatedState(endAction)

  val a11y: List<CustomAccessibilityAction> = if (active && accessibilityActions) {
    val startLabel = startAction?.label
    val endLabel = endAction?.label
    remember(state, startLabel, endLabel) {
      buildList {
        if (startLabel != null) add(CustomAccessibilityAction(startLabel) { currentStart != null && state.perform(start = true) })
        if (endLabel != null) add(CustomAccessibilityAction(endLabel) { currentEnd != null && state.perform(start = false) })
      }
    }
  } else {
    emptyList()
  }

  return this
    .onPlaced { state.coordinates = it }
    .onSizeChanged { state.widthPx = it.width }
    .fluidAccessibilityActions(a11y)
    .pointerInput(state) { fluidSwipeGesture(state) }
    .drawWithCache {
      val outline = shape.createOutline(size, layoutDirection, this)
      val clip = Path().apply { addOutline(outline) }
      val iconPx = FluidSwipeIconDp.dp.toPx()
      val insetPx = FluidSwipeIconInsetDp.dp.toPx()
      val sign = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
      onDrawWithContent {
        val logical = state.offset
        if (logical == 0f) {
          drawContent()
          return@onDrawWithContent
        }
        val start = logical > 0f
        val action = if (start) currentStart else currentEnd
        val painter: VectorPainter? = if (start) startPainter else endPainter
        val ink = (if (start) startInk else endInk) ?: neutralInk
        val physical = logical * sign
        val progress = state.armProgress.value
        clipPath(clip) {
          drawRect(if (action != null) lerp(neutral, action.color, progress) else neutral)
          if (painter != null) {
            val revealed = abs(physical)
            val alpha = fluidSwipeIconAlpha(revealed, iconPx, insetPx)
            val x = if (physical > 0f) insetPx else size.width - insetPx - iconPx
            translate(left = x, top = (size.height - iconPx) / 2f) {
              with(painter) {
                draw(Size(iconPx, iconPx), alpha = alpha, colorFilter = ColorFilter.tint(lerp(neutralInk, ink, progress)))
              }
            }
          }
          translate(left = physical) {
            drawRect(rowFill)
            this@onDrawWithContent.drawContent()
          }
        }
      }
    }
}

private suspend fun PointerInputScope.fluidSwipeGesture(state: FluidSwipeState) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
    val config = state.config
    if (!config.enabled || state.isDismissed || state.widthPx <= 0) return@awaitEachGesture
    if (state.startsInEdge(down.position.x, down.position.y)) return@awaitEachGesture
    val slop = viewConfiguration.touchSlop
    val longPressMillis = viewConfiguration.longPressTimeoutMillis
    val sign = if (config.layoutDirection == LayoutDirection.Ltr) 1f else -1f
    val grabbing = state.offset != 0f

    // La decisione, alla soglia di tocco, nel passaggio Main: dopo i figli (un tasto che tiene il dito ha gia'
    // consumato) e prima della lista che scorre (che vede il consumo e lascia).
    var claimDx = 0f
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Main)
      if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
      val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
      if (!change.pressed || change.isConsumed) return@awaitEachGesture
      if (!grabbing && change.uptimeMillis - down.uptimeMillis >= longPressMillis) return@awaitEachGesture
      val dx = change.position.x - down.position.x
      val dy = change.position.y - down.position.y
      when (fluidSwipeClaim(dx, dy, slop)) {
        FluidSwipeClaim.Undecided -> continue
        FluidSwipeClaim.Leave -> return@awaitEachGesture
        FluidSwipeClaim.Take -> {
          change.consume()
          claimDx = dx - sign(dx) * slop
          state.beginDrag()
          state.dragBy(claimDx * sign)
        }
      }
      break
    }

    val tracker = VelocityTracker()
    var released = false
    try {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        tracker.addPointerInputChange(change)
        val delta = change.position.x - change.previousPosition.x
        change.consume()
        if (!change.pressed) {
          released = true
          break
        }
        if (delta != 0f) state.dragBy(delta * sign)
      }
    } finally {
      val velocity = if (released) tracker.calculateVelocity().x * sign else 0f
      state.release(velocity, cancelled = !released)
    }
  }
}

/**
 * Le azioni personalizzate di TalkBack, **aggiunte** a quelle che il nodo ha gia'.
 *
 * Per una riga composta (avatar, stella, chip toccabili) le azioni devono stare sul nodo che TalkBack legge, cioe'
 * quello della riga: su un discendente sono irraggiungibili. E un nodo ne riceve da piu' parti (la riga, lo
 * scorrimento di lato, l'app): `semantics { customActions = … }` scritto due volte sullo stesso nodo tiene solo
 * l'ultimo, e una delle due liste sparisce in silenzio. Questo le somma, in qualunque ordine stiano i
 * modificatori. Lista vuota: il modificatore non c'e' (stesso albero di prima).
 */
fun Modifier.fluidAccessibilityActions(actions: List<CustomAccessibilityAction>): Modifier =
  if (actions.isEmpty()) {
    this
  } else {
    semantics {
      // I modificatori di semantica di un nodo scrivono tutti nella stessa configurazione, dal piu' interno al
      // piu' esterno: quello che c'e' gia' e' delle azioni messe piu' dentro, e si tiene.
      val existing = (this as? SemanticsConfiguration)?.getOrNull(SemanticsActions.CustomActions).orEmpty()
      customActions = existing + actions
    }
  }

/** Come finisce un rilascio. */
internal enum class FluidSwipeRelease { Cancel, Start, End }

/** Cosa fare del dito alla soglia di tocco. */
internal enum class FluidSwipeClaim { Undecided, Take, Leave }

/** Quanto deve essere piu' orizzontale che verticale un trascinamento per essere della riga. */
internal const val FluidSwipeDirectionRatio = 1.5f

/** L'elastico dal lato senza azione, in dp: dice «qui non c'e' niente» senza aprire niente. */
internal const val FluidSwipeElasticDp = 16f

/** La resistenza oltre la soglia: il dito continua, la riga ne fa la meta'. */
internal const val FluidSwipeResistance = 0.5f

/** L'isteresi della soglia, in dp: un dito che trema sulla soglia non vibra a raffica. */
internal const val FluidSwipeHysteresisDp = 12f

/** Da che frazione della soglia una spinta verso l'azione conferma. */
internal const val FluidSwipeFlingMinFraction = 0.5f

private const val FluidSwipeIconDp = 24f
private const val FluidSwipeIconInsetDp = 20f

/** La soglia in px: la frazione del back a dito della larghezza, col suo tetto. Zero se la riga non ha larghezza. */
internal fun fluidSwipeThresholdPx(widthPx: Float, density: Float): Float =
  min(FluidPageMotion.CommitFraction * max(0f, widthPx), FluidPageMotion.CommitCapDp * density)

/**
 * Decide di chi e' il dito, una volta sola: [FluidSwipeClaim.Take] se lo spostamento orizzontale supera lo slop ed
 * e' almeno [FluidSwipeDirectionRatio] volte quello verticale; [FluidSwipeClaim.Leave] se il verticale supera lo slop
 * per primo, o se il gesto e' diagonale.
 */
internal fun fluidSwipeClaim(dx: Float, dy: Float, slopPx: Float): FluidSwipeClaim = when {
  abs(dy) > slopPx && abs(dy) >= abs(dx) -> FluidSwipeClaim.Leave
  abs(dx) > slopPx -> if (abs(dx) >= abs(dy) * FluidSwipeDirectionRatio) FluidSwipeClaim.Take else FluidSwipeClaim.Leave
  else -> FluidSwipeClaim.Undecided
}

/**
 * Dove sta la riga per un trascinamento logico di [rawPx]: 1:1 fino alla soglia, poi con resistenza; dal lato senza
 * azione un elastico che tende a [elasticPx] (zero col movimento ridotto).
 */
internal fun fluidSwipeOffsetPx(
  rawPx: Float,
  thresholdPx: Float,
  elasticPx: Float,
  hasStart: Boolean,
  hasEnd: Boolean,
  reducedMotion: Boolean,
): Float {
  if (rawPx == 0f) return 0f
  val sideHasAction = if (rawPx > 0f) hasStart else hasEnd
  if (!sideHasAction) {
    if (reducedMotion || elasticPx <= 0f) return 0f
    return elasticPx * tanh(rawPx / elasticPx)
  }
  val magnitude = abs(rawPx)
  val shaped = if (magnitude <= thresholdPx) magnitude else thresholdPx + (magnitude - thresholdPx) * FluidSwipeResistance
  return sign(rawPx) * shaped
}

/** L'inverso di [fluidSwipeOffsetPx]: da dove riparte il dito quando prende una riga che sta tornando. */
internal fun fluidSwipeRawPx(
  offsetPx: Float,
  thresholdPx: Float,
  elasticPx: Float,
  hasStart: Boolean,
  hasEnd: Boolean,
): Float {
  if (offsetPx == 0f) return 0f
  val sideHasAction = if (offsetPx > 0f) hasStart else hasEnd
  if (!sideHasAction) {
    if (elasticPx <= 0f) return 0f
    val ratio = (offsetPx / elasticPx).coerceIn(-0.999f, 0.999f)
    return elasticPx * atanh(ratio)
  }
  val magnitude = abs(offsetPx)
  val raw = if (magnitude <= thresholdPx) magnitude else thresholdPx + (magnitude - thresholdPx) / FluidSwipeResistance
  return sign(offsetPx) * raw
}

/** Se la soglia e' superata, con isteresi: si arma a [thresholdPx], si disarma sotto `soglia - isteresi`. */
internal fun fluidSwipeArmed(
  wasArmed: Boolean,
  offsetPx: Float,
  thresholdPx: Float,
  hysteresisPx: Float,
  sideHasAction: Boolean,
): Boolean {
  if (!sideHasAction || thresholdPx <= 0f) return false
  val magnitude = abs(offsetPx)
  return if (wasArmed) magnitude >= thresholdPx - hysteresisPx else magnitude >= thresholdPx
}

/**
 * L'esito di un rilascio: conferma se armata, o con una spinta verso l'azione da almeno meta' soglia; una spinta
 * contraria annulla sempre. Pura: e' qui che si decide se un'email viene archiviata.
 */
internal fun fluidSwipeRelease(
  offsetPx: Float,
  velocityPx: Float,
  armed: Boolean,
  thresholdPx: Float,
  flingPx: Float,
  hasStart: Boolean,
  hasEnd: Boolean,
): FluidSwipeRelease {
  if (offsetPx == 0f || thresholdPx <= 0f) return FluidSwipeRelease.Cancel
  val start = offsetPx > 0f
  if (if (start) !hasStart else !hasEnd) return FluidSwipeRelease.Cancel
  val outward = if (start) velocityPx else -velocityPx
  if (outward <= -flingPx) return FluidSwipeRelease.Cancel
  val confirmed = armed ||
    (outward >= flingPx && abs(offsetPx) >= thresholdPx * FluidSwipeFlingMinFraction)
  return when {
    !confirmed -> FluidSwipeRelease.Cancel
    start -> FluidSwipeRelease.Start
    else -> FluidSwipeRelease.End
  }
}

/** Il primo tocco cade in una delle due fasce dei bordi dello schermo? */
internal fun fluidSwipeStartsInEdge(
  xInRoot: Float,
  rootWidth: Float,
  leftExclusionPx: Float,
  rightExclusionPx: Float,
): Boolean = xInRoot < leftExclusionPx || xInRoot > rootWidth - rightExclusionPx

/** L'icona compare mentre lo spazio si apre: piena quando c'e' posto per lei e per il suo margine. */
internal fun fluidSwipeIconAlpha(revealedPx: Float, iconPx: Float, insetPx: Float): Float =
  ((revealedPx - insetPx / 2f) / (iconPx + insetPx)).coerceIn(0f, 1f)
