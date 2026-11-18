package dev.antigravity.fluidengine.ui.fluid

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.ui.unit.sp
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
 * @param removesItem vero: [onAction] parte dopo l'uscita dello scorrimento pieno o dopo la chiusura per tocco
 *   dell'azione aperta. L'app toglie il dato e la lista richiude il posto con [fluidGroupedItems]. Falso:
 *   [onAction] parte alla conferma e la riga torna a posto (letta/non letta, fissa).
 * @param onAction l'azione. Parte **una volta per conferma** sul thread principale, anche se la riga viene
 *   smontata durante l'animazione. [FluidSwipeState.reset] annulla un'azione ancora in attesa.
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
 * La stessa azione come voce del menu contestuale della riga (4.0, F6.6).
 *
 * In iOS le azioni a scorrimento e le prime voci del menu della pressione lunga **coincidono** (R1 L3): sono due strade
 * per lo stesso verbo, e un'azione che c'e' scorrendo e manca tenendo premuto (o ha un altro nome, un'altra icona) e'
 * una riga che dice due cose diverse. Si mettono in testa a `contextActions`, nello stesso ordine:
 * `contextActions = { listOf(archivia.asContextAction(), elimina.asContextAction(destructive = true), …) }`.
 *
 * Nel menu la voce chiama [FluidSwipeAction.onAction] direttamente: la riga non esce di lato, la toglie la lista con la
 * sua animazione (`fluidGroupedItems`, `animateItem`).
 *
 * @param destructive la voce nel colore dell'errore, come il tasto rosso di Apple; lo decide l'app, perche' «togliere
 *   dalla lista» (archivia) e «distruggere» (elimina) sono due cose, e solo la seconda e' rossa.
 */
fun FluidSwipeAction.asContextAction(destructive: Boolean = false): FluidContextAction =
  FluidContextAction(label = label, icon = icon, destructive = destructive, onClick = onAction)

/**
 * L'aspetto delle azioni a scorrimento (4.0, F6.6).
 *
 * Non e' un `enum` di proposito, come [FluidGroupedMaterial]: un aspetto nuovo in un `enum` pubblico rompe ogni `when`
 * esaustivo di un'app.
 *
 * - [Capsule], di serie: quello di iOS 27 (R5 L3, misurato in F6.6 sulla sessione 269 a 17:53 e 18:25). Appena il
 *   dito prende la riga, la riga diventa un rettangolo arrotondato (lo stesso raggio della riga sollevata dal menu
 *   contestuale) un poco piu' scuro del gruppo; nello spazio che si apre nasce una **capsula** del colore
 *   dell'azione, col glifo bianco e l'etichetta grigia sotto, che si gonfia con una molla e si allunga a riempire lo
 *   spazio. Torna con la molla critica che Apple usa per richiuderla.
 * - [Fill]: com'era fino alla 3.x. Il colore dell'azione riempie tutto lo spazio rivelato (grigio quieto fino alla
 *   soglia, pieno oltre), l'icona sul bordo. E' l'altro termine della scelta di gusto del piano («tondo con etichetta
 *   sotto (iOS 27) / com'e' oggi»).
 */
@Immutable
@JvmInline
value class FluidSwipeStyle private constructor(private val id: Int) {

  internal val isCapsule: Boolean get() = id == CapsuleId

  override fun toString(): String = if (id == CapsuleId) "Capsule" else "Fill"

  companion object {
    private const val CapsuleId = 0
    private const val FillId = 1

    /** La capsula col glifo e l'etichetta sotto, la riga che si arrotonda: iOS 27. Di serie. */
    val Capsule: FluidSwipeStyle = FluidSwipeStyle(CapsuleId)

    /** Il colore che riempie lo spazio rivelato: l'aspetto della 3.x. */
    val Fill: FluidSwipeStyle = FluidSwipeStyle(FillId)
  }
}

/**
 * L'aspetto di serie delle azioni a scorrimento sotto questo punto: un'app (o la galleria, per mettere a confronto i due
 * aspetti) lo cambia per tutte le righe senza toccarle una per una. Di serie [FluidSwipeStyle.Capsule].
 */
val LocalFluidSwipeStyle: ProvidableCompositionLocal<FluidSwipeStyle> =
  staticCompositionLocalOf { FluidSwipeStyle.Capsule }

/**
 * Tiene una sola riga aperta fra le [fluidSwipeActions] discendenti, anche fra gruppi distinti. Avvolge la lista
 * o l'area della schermata che deve richiudere la riga al tocco o allo scorrimento. I controlli esterni alla riga
 * ricevono il loro tocco normalmente. Senza questo contenitore, un gruppo dell'engine coordina le proprie righe.
 */
@Composable
fun FluidSwipeActionsContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val group = remember { FluidSwipeGroup() }
  CompositionLocalProvider(LocalFluidSwipeGroup provides group) {
    Box(
      modifier = modifier.observeFluidSwipeGroup(group).nestedScroll(group.scrollConnection),
      propagateMinConstraints = true,
    ) { content() }
  }
}

internal val LocalFluidSwipeGroup = staticCompositionLocalOf<FluidSwipeGroup?> { null }

/** Un solo proprietario della posa aperta. La riga precedente puo' finire di richiudersi in parallelo. */
internal class FluidSwipeGroup {
  var openRow: FluidSwipeState? = null
    private set

  fun open(row: FluidSwipeState) {
    val previous = openRow
    openRow = row
    if (previous !== row) previous?.close()
  }

  fun release(row: FluidSwipeState) {
    if (openRow === row) openRow = null
  }

  val scrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      if (available.y != 0f) openRow?.close()
      return Offset.Zero
    }
  }
}

/** Al passaggio Final, la riga aperta ha gia' potuto distinguere il tap sulla sua azione da quello sul testo. */
internal fun Modifier.observeFluidSwipeGroup(group: FluidSwipeGroup): Modifier = pointerInput(group) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val row = group.openRow ?: return@awaitEachGesture
    val up = awaitSwipeTapUp(down, PointerEventPass.Final) ?: return@awaitEachGesture
    if (group.openRow === row && up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
      row.close()
    }
  }
}

/**
 * Lo stato di una riga a scorrimento: dove sta, se e' aperta, se la soglia e' superata, se e' uscita.
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

  /** La riga ha come destinazione la posa aperta, con l'azione esposta e toccabile. */
  var isOpen: Boolean by mutableStateOf(false)
    private set

  /**
   * La riga e' uscita (un'azione con `removesItem` confermata) e aspetta che l'app la tolga. Non risponde al dito
   * finche' non la si rimette con [reset].
   */
  var isDismissed: Boolean by mutableStateOf(false)
    private set

  /** Quanto e' acceso il colore dell'azione (0 grigio, 1 pieno): segue la soglia con un lampo. */
  internal val armProgress = Animatable(0f)

  /**
   * La capsula che nasce ([FluidSwipeStyle.Capsule]): 0 assente, 1 alla sua taglia, con il piccolo superamento della
   * molla. Parte quando il dito prende la riga, come in Apple, e torna a zero quando la riga e' di nuovo a posto.
   */
  internal val appear = Animatable(0f)

  /**
   * Quanto la riga e' «presa» ([FluidSwipeStyle.Capsule]): angoli arrotondati e fondo un poco piu' scuro. In Apple
   * cambia nel primo fotogramma in cui la riga si muove; qui con un lampo, e si spegne quando la riga e' a posto.
   */
  internal val lift = Animatable(0f)

  internal var widthPx: Int = 0
    private set
  internal var coordinates: LayoutCoordinates? = null
  internal var config: FluidSwipeConfig = FluidSwipeConfig.Disabled
    private set
  private var group: FluidSwipeGroup? = null
  private var openPositionY: Float? = null

  private var rawPx = 0f
  private var settle: Job? = null
  private var firedThisGesture = false

  /**
   * L'azione di un gesto confermato che toglie la riga e non e' ancora partita: la lancia la fine dell'uscita o,
   * se la riga esce prima dalla composizione, [flushPendingAction]. Sempre svuotata prima di chiamarla: parte una
   * volta sola, da qualunque dei due arrivi per primo.
   */
  private var pendingAction: (() -> Unit)? = null

  internal val acceptsGestures: Boolean
    get() = config.enabled && !isDismissed && pendingAction == null && widthPx > 0

  internal fun attach(group: FluidSwipeGroup?) {
    this.group = group
  }

  internal fun detach() {
    close()
    group = null
  }

  internal fun updateConfig(next: FluidSwipeConfig) {
    val geometryChanged = next.style != config.style || next.density != config.density ||
      next.layoutDirection != config.layoutDirection
    val reduceNow = next.reducedMotion && !config.reducedMotion
    config = next
    if (isOpen && (!config.enabled || actionFor(offset) == null || geometryChanged)) close()
    if (isOpen && reduceNow) settleOpen(sign(offset))
  }

  internal fun resize(width: Int) {
    if (width != widthPx && isOpen) close()
    widthPx = width
  }

  internal fun checkOpenPosition(coordinates: LayoutCoordinates) {
    if (!isOpen) return
    val y = coordinates.positionInRoot().y
    val initialY = openPositionY
    if (initialY == null) openPositionY = y
    else if (abs(y - initialY) > 0.5f) close()
  }

  internal fun close() {
    if (isOpen) settleHome(0f)
  }

  private fun clearOpen() {
    isOpen = false
    openPositionY = null
    group?.release(this)
  }

  /**
   * Rimette la riga a posto con la molla (istantanea col movimento ridotto): per un'azione che l'app non ha potuto
   * fare, o per l'«Annulla» di una riga uscita che resta nei dati.
   */
  suspend fun reset() {
    clearOpen()
    settle?.cancel()
    settle = null
    // L'app ha cambiato idea mentre la riga usciva: l'azione che non e' ancora partita non parte piu'.
    pendingAction = null
    isDismissed = false
    firedThisGesture = false
    disarm(silent = true)
    if (config.reducedMotion) {
      offset = 0f
    } else {
      animate(initialValue = offset, targetValue = 0f, animationSpec = fluidSwipeSettleSpec(config.style)) { value, _ ->
        offset = value
      }
    }
    offset = 0f
    rawPx = 0f
    restLook()
  }

  /** La riga e' a posto: la capsula sparisce (senza spazio non si vede gia' piu') e la riga torna piatta. */
  private fun restLook() {
    scope.launch { appear.snapTo(0f) }
    scope.launch {
      if (config.reducedMotion) lift.snapTo(0f) else lift.animateTo(0f, FluidMotion.flick())
    }
  }

  /** Il dito ha preso la riga: nell'aspetto di iOS 27 la riga si arrotonda e la capsula comincia a nascere. */
  private fun startLook() {
    if (!config.style.isCapsule) return
    scope.launch {
      if (config.reducedMotion) lift.snapTo(1f) else lift.animateTo(1f, FluidMotion.flick())
    }
    scope.launch {
      if (config.reducedMotion) appear.snapTo(1f) else appear.animateTo(1f, fluidSwipeAppearSpec())
    }
  }

  private val thresholdPx: Float
    get() = fluidSwipeThresholdPx(widthPx.toFloat(), config.density)

  /** Il dito ha preso la riga: se stava ancora tornando la si riprende da dov'e'. */
  internal fun beginDrag() {
    settle?.cancel()
    settle = null
    clearOpen()
    firedThisGesture = false
    startLook()
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
        openPx = openDistancePx,
      )
    }
    when (decision) {
      FluidSwipeRelease.Cancel -> settleHome(velocityPx)
      FluidSwipeRelease.Open -> settleOpen(sign(offset))
      FluidSwipeRelease.Start -> config.startAction?.let { commit(it, sign = 1f, velocityPx = velocityPx, fromGesture = true) }
      FluidSwipeRelease.End -> config.endAction?.let { commit(it, sign = -1f, velocityPx = velocityPx, fromGesture = true) }
    }
  }

  private val openDistancePx: Float
    get() = fluidSwipeOpenDistancePx(thresholdPx, config.density, config.style)

  private fun settleOpen(side: Float) {
    if (!isOpen) {
      openPositionY = coordinates?.takeIf { it.isAttached }?.positionInRoot()?.y
      isOpen = true
      group?.open(this)
    }
    disarm(silent = true)
    val target = side * openDistancePx
    settle?.cancel()
    settle = scope.launch {
      if (config.reducedMotion) {
        startLook()
      } else {
        // Il dito di Apple si ferma al rilascio: la molla verso la posa aperta parte ferma.
        animate(initialValue = offset, targetValue = target, animationSpec = fluidSwipeSettleSpec(config.style)) { value, _ ->
          offset = value
        }
      }
      offset = target
    }
  }

  /** L'azione confermata dal tocco sulla fascia esposta o dal comando di TalkBack. */
  internal fun perform(start: Boolean): Boolean {
    if (!config.enabled || isDismissed || pendingAction != null) return false
    val action = (if (start) config.startAction else config.endAction) ?: return false
    if (isOpen) {
      if (action.removesItem) pendingAction = action.onAction
      settleHome(0f)
      if (!action.removesItem) action.onAction()
      return true
    }
    firedThisGesture = false
    commit(action, sign = if (start) 1f else -1f, velocityPx = 0f, fromGesture = false)
    return true
  }

  internal fun tapOpenRow(downX: Float, upX: Float) {
    if (!isOpen) return
    val physical = offset * if (config.layoutDirection == LayoutDirection.Ltr) 1f else -1f
    if (fluidSwipeHitAction(downX, physical, widthPx) && fluidSwipeHitAction(upX, physical, widthPx)) {
      if (perform(start = offset > 0f)) return
    }
    close()
  }

  private fun actionFor(offsetPx: Float): FluidSwipeAction? = when {
    offsetPx > 0f -> config.startAction
    offsetPx < 0f -> config.endAction
    else -> null
  }

  private fun commit(action: FluidSwipeAction, sign: Float, velocityPx: Float, fromGesture: Boolean) {
    if (firedThisGesture) return
    firedThisGesture = true
    // TalkBack non prende la riga col dito: la capsula nasce qui, perche' l'uscita la mostri.
    if (!fromGesture) startLook()
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
    // Il gesto e' confermato qui: da adesso l'azione parte comunque, anche se la riga esce dalla composizione
    // prima della fine dell'uscita (lo scope muore con lei e la coroutine non arriverebbe in fondo).
    pendingAction = action.onAction
    val target = sign * max(widthPx, 1)
    settle = scope.launch {
      if (config.reducedMotion) {
        offset = target
      } else {
        animate(
          initialValue = offset,
          targetValue = target,
          initialVelocity = velocityPx,
          animationSpec = fluidSwipeExitSpec(config.style),
        ) { value, _ -> offset = value }
      }
      offset = target
      flushPendingAction()
    }
  }

  /** Lancia l'azione in sospeso, se c'e': la svuota prima, cosi' non parte mai due volte. */
  internal fun flushPendingAction() {
    val action = pendingAction ?: return
    pendingAction = null
    action()
  }

  private fun settleHome(velocityPx: Float) {
    clearOpen()
    disarm(silent = true)
    settle?.cancel()
    settle = scope.launch {
      if (config.reducedMotion) {
        offset = 0f
      } else {
        // La riga si lascia quando arriva, non quando la molla a pixel si ferma: l'ultimo tratto e' sotto il pixel e
        // dura ~250 ms, e una riga ferma a casa ma ancora arrotondata e velata si vede (banco F6.6).
        val restPx = FluidSwipeRestLookDp * config.density
        var resting = false
        animate(
          initialValue = offset,
          targetValue = 0f,
          initialVelocity = velocityPx,
          animationSpec = fluidSwipeSettleSpec(config.style),
        ) { value, _ ->
          offset = value
          if (!resting && abs(value) <= restPx) {
            resting = true
            restLook()
          }
        }
      }
      offset = 0f
      rawPx = 0f
      restLook()
      flushPendingAction()
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
  val style: FluidSwipeStyle = FluidSwipeStyle.Capsule,
) {
  companion object {
    val Disabled = FluidSwipeConfig(null, null, false, NoOpFluidHaptics, false, 1f, 0f, 0f, LayoutDirection.Ltr)
  }
}

/** Lo stato di una riga a scorrimento, ricordato nell'item. */
@Composable
fun rememberFluidSwipeState(): FluidSwipeState {
  val scope = rememberCoroutineScope()
  val state = remember(scope) { FluidSwipeState(scope) }
  // Se la riga esce dalla composizione mentre un gesto confermato sta ancora uscendo, l'azione parte adesso.
  remember(state) { FluidSwipeUnmountGuard(state) }
  return state
}

/**
 * Chiude il conto di un gesto confermato quando la riga lascia la composizione: l'animazione di uscita vive nello
 * scope della riga e muore con lei, l'azione no. `onForgotten` gira sul thread che applica la composizione, cioe'
 * il principale; se per qualche ragione fosse un altro, l'azione viene rimandata al principale.
 */
private class FluidSwipeUnmountGuard(private val state: FluidSwipeState) : RememberObserver {
  override fun onRemembered() = Unit

  override fun onForgotten() {
    val main = Looper.getMainLooper()
    if (Looper.myLooper() === main) state.flushPendingAction() else Handler(main).post(state::flushPendingAction)
  }

  override fun onAbandoned() = Unit
}

/**
 * Azioni a scorrimento orizzontale su una riga: un'azione per verso, soglia che si sente, annullabile tornando
 * indietro col dito, uscita o ritorno al rilascio, e le stesse azioni per TalkBack.
 *
 * **Va sul nodo della riga.** Per [dev.antigravity.fluidengine.ui.theme.FluidListRow] e' il suo `modifier`, che
 * sta sul `ListItem` stesso (un nodo solo): li' le azioni di TalkBack si aggiungono a quelle della riga invece di
 * finire su un discendente, dove nessuno le raggiungerebbe. Dentro [fluidGroupedItems] si passano la sagoma e il
 * fondo del pezzo: `Modifier.fluidSwipeActions(state, ..., shape = segmentShape, fill = rowFill)`.
 *
 * ### L'aspetto (4.0, F6.6)
 *
 * Di serie quello di iOS 27 ([FluidSwipeStyle.Capsule]), misurato fotogramma per fotogramma sulla sessione 269 di
 * WWDC26 (17:53 in una lista, 18:20 e 18:25 in un contenitore libero; `artifacts/liquid-glass/sessioni/f6-6/`):
 *
 * - **la riga si prende**: appena il dito la tiene diventa un rettangolo arrotondato col raggio della riga sollevata
 *   dal menu contestuale ([FluidRadius.Group], al piu' meta' altezza), un poco piu' scuro del gruppo, e scorre col
 *   dito; il lato che esce lo taglia la sagoma del gruppo;
 * - **la capsula nasce** nello spazio che si apre: del colore dell'azione, piena (nessun filo, nessun riflesso: e'
 *   cio' che si misura sul rosso di Apple), col glifo nell'inchiostro leggibile e l'etichetta grigia sotto. Riempie lo
 *   spazio fra la riga e il bordo (8 dp da una parte e dall'altra) e si **gonfia** con la molla misurata
 *   ([FluidSwipeAppearDamping], [FluidSwipeResponseSeconds]): dal niente alla sua taglia in ≈200 ms, un superamento
 *   del 4% a ≈300 ms, poi ferma. Glifo ed etichetta crescono con lei. Lo spazio che si allunga la allunga;
 * - **il ritorno** e' la molla critica di Apple (risposta 0,46 s: meta' strada a ≈130 ms, il 90% a ≈270 ms), che
 *   parte dalla velocita' del dito; l'uscita di una riga tolta usa la stessa molla, e la capsula si allunga fino a
 *   riempire la riga che se ne va.
 *
 * Oltre la soglia l'etichetta prende il colore dell'azione e la capsula cresce di poco: la soglia si vede, oltre che
 * sentirsi. Apple non la mostra (la clip non ha uno scorrimento fino in fondo): e' una scelta a occhio.
 *
 * Un rilascio parziale lascia l'azione toccabile. La stessa molla porta la riga alla posa aperta e la richiude
 * al tocco sulla riga, al tocco sull'azione o con Indietro. [FluidSwipeActionsContainer] coordina l'apertura
 * fra righe e la chiusura toccando fuori o scorrendo.
 *
 * [FluidSwipeStyle.Fill] e' l'aspetto di prima: lo spazio rivelato si riempie del colore dell'azione (grigio fino alla
 * soglia), l'icona sta sul bordo, le molle sono quelle di [FluidMotion.swipeSettle] e [FluidMotion.swipeExit].
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
 * partono dalla velocita' del dito e col movimento ridotto diventano un salto (la capsula e' subito alla sua taglia).
 *
 * ### Coerente col menu contestuale
 *
 * Le stesse azioni stanno in testa al menu della pressione lunga ([asContextAction]): stesso nome, stessa icona,
 * stesso ordine. La riga presa ha gli angoli della riga sollevata dal menu, cosi' i due gesti mostrano lo stesso
 * oggetto.
 *
 * ### Dove si rompe
 *
 * - La riga disegna il proprio fondo ([fill]) mentre si sposta, perche' dietro c'e' lo spazio dell'azione: un fondo
 *   diverso da quello del gruppo si vede come una riga di un altro colore. Con [fluidGroupedItems] passa `rowFill`.
 * - Con [FluidSwipeStyle.Capsule] dietro la riga resta cio' che c'e' sotto (il fondo del gruppo): fuori da un gruppo
 *   si vede la pagina, come in Apple si vede il foglio.
 * - I separatori fra la riga presa e le vicine restano (Apple li nasconde): il separatore lo disegna la riga di sopra,
 *   che non sa che quella sotto e' presa.
 * - Lo spostamento e' di disegno. La fascia esposta della riga aperta riceve il tocco dell'azione; il resto
 *   della riga riceve il tocco di chiusura. Le azioni di TalkBack restano sul nodo della riga.
 * - Una riga uscita ([FluidSwipeState.isDismissed]) che l'app non toglie resta vuota: l'app la rimette con
 *   [FluidSwipeState.reset].
 * - Una riga che esce dalla composizione mentre sta uscendo (la lista scorre o si aggiorna, la schermata si
 *   chiude) lancia [FluidSwipeAction.onAction] in quel momento, una volta sola: il gesto confermato non si perde,
 *   ma l'app non puo' contare su un'animazione finita prima dell'azione.
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
 * @param style l'aspetto; di serie quello di [LocalFluidSwipeStyle], cioe' [FluidSwipeStyle.Capsule]. Dalla 4.0.
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
  style: FluidSwipeStyle = LocalFluidSwipeStyle.current,
): Modifier {
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val scheme = MaterialTheme.colorScheme
  val gestureInsets = WindowInsets.systemGestures
  val edgePx = with(density) { FluidPageMotion.EdgeZoneDp.dp.toPx() }
  val active = enabled && (startAction != null || endAction != null)
  val group = LocalFluidSwipeGroup.current

  // Scritto in composizione: il gesto lo legge al tocco, senza ripartire quando cambia.
  val config = FluidSwipeConfig(
    startAction = startAction,
    endAction = endAction,
    enabled = active,
    haptics = haptics,
    reducedMotion = reducedMotion,
    density = density.density,
    leftExclusionPx = max(edgePx, gestureInsets.getLeft(density, LayoutDirection.Ltr).toFloat()),
    rightExclusionPx = max(edgePx, gestureInsets.getRight(density, LayoutDirection.Ltr).toFloat()),
    layoutDirection = LocalLayoutDirection.current,
    style = style,
  )
  DisposableEffect(state, group) {
    state.attach(group)
    onDispose { state.detach() }
  }
  SideEffect { state.updateConfig(config) }
  if (state.isOpen && LocalOnBackPressedDispatcherOwner.current != null) {
    BackHandler { state.close() }
  }

  val rowFill = if (fill.isSpecified) fill else scheme.surfaceContainerLow
  val neutral = scheme.surfaceContainerHighest
  val neutralInk = scheme.onSurfaceVariant
  // La riga presa: il suo fondo con un velo dell'inchiostro, come la riga premuta di Apple (−20 livelli sul chiaro).
  val heldFill = scheme.onSurface.copy(alpha = FluidSwipeHeldVeil).compositeOver(rowFill)
  val startPainter = startAction?.let { rememberVectorPainter(it.icon) }
  val endPainter = endAction?.let { rememberVectorPainter(it.icon) }
  val startInk = startAction?.let { fluidLegibleInk(it.color.compositeOver(scheme.surface)) }
  val endInk = endAction?.let { fluidLegibleInk(it.color.compositeOver(scheme.surface)) }
  val currentStart by rememberUpdatedState(startAction)
  val currentEnd by rememberUpdatedState(endAction)
  val measurer = if (style.isCapsule) rememberTextMeasurer() else null
  val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0f.sp)

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
    .onSizeChanged { state.resize(it.width) }
    .then(if (state.isOpen) Modifier.onGloballyPositioned(state::checkOpenPosition) else Modifier)
    .fluidAccessibilityActions(a11y)
    .pointerInput(state) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!state.isOpen) return@awaitEachGesture
        val up = awaitSwipeTapUp(down, PointerEventPass.Initial) ?: return@awaitEachGesture
        if (!up.isConsumed && state.isOpen &&
          up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
        ) {
          up.consume()
          state.tapOpenRow(down.position.x, up.position.x)
        }
      }
    }
    .pointerInput(state) { fluidSwipeGesture(state) }
    .drawWithCache {
      val outline = shape.createOutline(size, layoutDirection, this)
      val clip = Path().apply { addOutline(outline) }
      val iconPx = FluidSwipeIconDp.dp.toPx()
      val insetPx = FluidSwipeIconInsetDp.dp.toPx()
      val sign = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
      if (!style.isCapsule || measurer == null) {
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
      } else {
        // L'aspetto di iOS 27. Le misure in px, calcolate una volta per taglia.
        val metrics = FluidSwipeCapsuleMetrics(
          gapPx = FluidSwipeCapsuleGapDp.dp.toPx(),
          insetPx = FluidSwipeCapsuleInsetDp.dp.toPx(),
          heightPx = fluidSwipeCapsuleHeightPx(size.height, density.density),
          labelGapPx = FluidSwipeLabelGapDp.dp.toPx(),
        )
        val glyphPx = fluidSwipeGlyphPx(metrics.heightPx, density.density)
        val rowRadiusPx = min(FluidRadius.Group.toPx(), size.height / 2f)
        val openPx = fluidSwipeOpenDistancePx(fluidSwipeThresholdPx(size.width, density.density), density.density, style)
        val labelMaxPx = (openPx - metrics.gapPx - metrics.insetPx).toInt()
        val startLabel = currentStart?.let { measureSwipeLabel(measurer, it.label, labelStyle, labelMaxPx) }
        val endLabel = currentEnd?.let { measureSwipeLabel(measurer, it.label, labelStyle, labelMaxPx) }
        val rowPath = Path()
        onDrawWithContent {
          val logical = state.offset
          val lift = state.lift.value.coerceIn(0f, 1f)
          if (logical == 0f && lift == 0f) {
            drawContent()
            return@onDrawWithContent
          }
          val physical = logical * sign
          val start = logical > 0f
          val action = when {
            logical > 0f -> currentStart
            logical < 0f -> currentEnd
            else -> null
          }
          clipPath(clip) {
            if (action != null) {
              drawSwipeCapsule(
                action = action,
                painter = if (start) startPainter else endPainter,
                ink = (if (start) startInk else endInk) ?: Color.White,
                label = if (start) startLabel else endLabel,
                labelInk = lerp(neutralInk, action.color, state.armProgress.value),
                physical = physical,
                metrics = metrics,
                glyphPx = glyphPx,
                appear = state.appear.value,
                armed = state.armProgress.value,
              )
            }
            // La riga presa, sopra lo spazio: arrotondata dal primo fotogramma, col suo fondo e il velo.
            val radius = rowRadiusPx * lift
            val rowRect = RoundRect(
              rect = Rect(physical, 0f, physical + size.width, size.height),
              cornerRadius = CornerRadius(radius, radius),
            )
            rowPath.reset()
            rowPath.addRoundRect(rowRect)
            drawPath(rowPath, lerp(rowFill, heldFill, lift))
            clipPath(rowPath) {
              translate(left = physical) { this@onDrawWithContent.drawContent() }
            }
          }
        }
      }
    }
}

/** Le misure della capsula in px, per la taglia corrente della riga. */
internal class FluidSwipeCapsuleMetrics(
  val gapPx: Float,
  val insetPx: Float,
  val heightPx: Float,
  val labelGapPx: Float,
)

private fun measureSwipeLabel(measurer: TextMeasurer, text: String, style: TextStyle, maxWidthPx: Int): TextLayoutResult =
  measurer.measure(
    text = text,
    style = style,
    overflow = TextOverflow.Ellipsis,
    softWrap = false,
    maxLines = 1,
    constraints = Constraints(maxWidth = maxWidthPx.coerceAtLeast(1)),
  )

/**
 * La capsula, il glifo e l'etichetta nello spazio che la riga ha aperto. Tutto cresce insieme ([appear]) attorno al
 * centro del gruppo capsula + etichetta, che sta al centro della riga.
 */
private fun DrawScope.drawSwipeCapsule(
  action: FluidSwipeAction,
  painter: VectorPainter?,
  ink: Color,
  label: TextLayoutResult?,
  labelInk: Color,
  physical: Float,
  metrics: FluidSwipeCapsuleMetrics,
  glyphPx: Float,
  appear: Float,
  armed: Float,
) {
  val slot = fluidSwipeCapsuleSlot(physical, size.width, metrics.gapPx, metrics.insetPx) ?: return
  val labelHeight = label?.size?.height?.toFloat() ?: 0f
  val geometry = fluidSwipeCapsuleGeometry(
    slotStart = slot.start,
    slotEnd = slot.endInclusive,
    rowHeight = size.height,
    capsuleHeight = metrics.heightPx,
    labelBlock = if (label != null) metrics.labelGapPx + labelHeight else 0f,
    appear = appear,
    armed = armed,
  ) ?: return
  val alpha = fluidSwipeAppearAlpha(appear)
  if (alpha <= 0f) return
  val capsule = geometry.capsule
  drawRoundRect(
    color = action.color,
    topLeft = capsule.topLeft,
    size = capsule.size,
    cornerRadius = CornerRadius(capsule.height / 2f, capsule.height / 2f),
    alpha = alpha,
  )
  val grow = geometry.scale
  if (painter != null) {
    val glyph = glyphPx * grow
    // Il glifo non sta in una capsula piu' stretta di lui: compare quando c'e' posto.
    val glyphAlpha = alpha * ((capsule.width - glyph) / glyph).coerceIn(0f, 1f)
    if (glyphAlpha > 0f) {
      translate(left = capsule.center.x - glyph / 2f, top = capsule.center.y - glyph / 2f) {
        with(painter) { draw(Size(glyph, glyph), alpha = glyphAlpha, colorFilter = ColorFilter.tint(ink)) }
      }
    }
  }
  if (label != null && grow > 0.05f) {
    val w = label.size.width.toFloat()
    val top = capsule.bottom + metrics.labelGapPx * grow
    translate(left = capsule.center.x - w * grow / 2f, top = top) {
      // L'etichetta cresce con la capsula: una scala attorno al suo angolo, gia' portato al posto giusto.
      scale(grow, pivot = Offset.Zero) { drawText(label, color = labelInk, alpha = alpha) }
    }
  }
}

private suspend fun AwaitPointerEventScope.awaitSwipeTapUp(
  down: PointerInputChange,
  pass: PointerEventPass,
): PointerInputChange? {
  while (true) {
    val event = awaitPointerEvent(pass)
    val change = event.changes.firstOrNull { it.id == down.id } ?: return null
    if (event.changes.any { it.id != down.id && it.pressed }) return null
    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return null
    if (!change.pressed) return change
  }
}

private suspend fun PointerInputScope.fluidSwipeGesture(state: FluidSwipeState) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
    val config = state.config
    if (!state.acceptsGestures) return@awaitEachGesture
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
        if (event.changes.any { it.id != down.id && it.pressed } || !state.acceptsGestures) break
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (change.isConsumed) break
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
internal enum class FluidSwipeRelease { Cancel, Open, Start, End }

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

/** I 56 pt della capsula aperta in f32244, piu' i suoi margini. Su righe strette resta sotto la soglia piena. */
internal fun fluidSwipeOpenDistancePx(thresholdPx: Float, density: Float, style: FluidSwipeStyle): Float {
  val margins = FluidSwipeCapsuleGapDp + FluidSwipeCapsuleInsetDp
  val nominal = if (style.isCapsule) 56f + margins else FluidSwipeIconDp + 2f * FluidSwipeIconInsetDp
  val available = min(nominal * density, thresholdPx * 0.8f)
  return if (style.isCapsule && available <= margins * density) 0f else available
}

/** La fascia del controllo segue lo spazio effettivamente rivelato, anche in RTL e durante l'assestamento. */
internal fun fluidSwipeHitAction(x: Float, physicalOffset: Float, width: Int): Boolean = when {
  x < 0f || x > width -> false
  physicalOffset > 0f -> x < physicalOffset
  physicalOffset < 0f -> x > width + physicalOffset
  else -> false
}

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
 * Conferma se armata o con una spinta verso l'azione da almeno meta' soglia. Fra meta' della posa aperta e
 * la conferma si lascia l'azione esposta. Una spinta contraria chiude anche oltre la soglia.
 */
internal fun fluidSwipeRelease(
  offsetPx: Float,
  velocityPx: Float,
  armed: Boolean,
  thresholdPx: Float,
  flingPx: Float,
  hasStart: Boolean,
  hasEnd: Boolean,
  openPx: Float,
): FluidSwipeRelease {
  if (offsetPx == 0f || thresholdPx <= 0f) return FluidSwipeRelease.Cancel
  val start = offsetPx > 0f
  if (if (start) !hasStart else !hasEnd) return FluidSwipeRelease.Cancel
  val outward = if (start) velocityPx else -velocityPx
  if (outward <= -flingPx) return FluidSwipeRelease.Cancel
  val confirmed = armed ||
    (outward >= flingPx && abs(offsetPx) >= thresholdPx * FluidSwipeFlingMinFraction)
  return when {
    !confirmed -> if (openPx > 0f && abs(offsetPx) >= openPx / 2f) FluidSwipeRelease.Open else FluidSwipeRelease.Cancel
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

// --- L'aspetto di iOS 27 (F6.6) -----------------------------------------------------------------------------------
//
// I numeri vengono dalle misure di F6.6 sulla sessione 269 di WWDC26 in 4K (spezzone 17:24-19:50), telefono da 402 pt
// a 2,149 px/pt (il testo da 17 pt ha la maiuscola alta 25,8 px): una lista in un foglio (f32204-f32244) e un
// contenitore libero (f32988-f33019, f33150-f33190). Collage e tabelle in `artifacts/liquid-glass/sessioni/f6-6/`.

/**
 * La risposta della molla delle azioni a scorrimento, in secondi: 0,46 (misurata, fiducia media-alta). Il ritorno della
 * riga di Apple e' una molla **critica** che parte ferma: meta' strada a 133 ms, l'86% a 233 ms, il 96% a 333 ms, in
 * due prese uguali al fotogramma (f33001-f33013, f33170-f33185); la miglior molla critica ha ω = 13,8 rad/s. La
 * nascita della capsula usa la stessa risposta con lo smorzamento di [FluidSwipeAppearDamping].
 *
 * Sta qui e non in [FluidMotion] perche' quel file non e' nel perimetro di F6.6: le due molle di prima
 * ([FluidMotion.swipeSettle], [FluidMotion.swipeExit], 0,30 s) restano all'aspetto [FluidSwipeStyle.Fill]. Va
 * spostata fra i token quando si unisce il pacchetto.
 */
internal const val FluidSwipeResponseSeconds: Double = 0.46

/**
 * Lo smorzamento della capsula che nasce: 0,74, il [FluidMotion.DampingFluid] dei token, accanto allo 0,72 che si
 * misura (altezza della capsula fotogramma per fotogramma in due prese: 0,23 / 0,42 / 0,60 / 0,76 / 0,87 / 0,95 / 1,00,
 * poi un picco di +4% a ≈300 ms e ferma). Con 0,74 l'errore sulle due prese e' lo stesso della miglior molla.
 */
internal const val FluidSwipeAppearDamping: Float = FluidMotion.DampingFluid

/** Il ritorno della riga: la molla critica di Apple per [FluidSwipeStyle.Capsule], quella di prima per l'altro. */
internal fun fluidSwipeSettleSpec(style: FluidSwipeStyle): SpringSpec<Float> =
  if (style.isCapsule) {
    FluidMotion.pixels(FluidMotion.DampingChrome, FluidMotion.stiffnessForResponse(FluidSwipeResponseSeconds))
  } else {
    FluidMotion.swipeSettle()
  }

/** L'uscita di una riga tolta: la stessa molla del ritorno, perche' e' lo stesso gesto verso l'altro capo. */
internal fun fluidSwipeExitSpec(style: FluidSwipeStyle): SpringSpec<Float> =
  if (style.isCapsule) fluidSwipeSettleSpec(style) else FluidMotion.swipeExit()

/** La nascita della capsula: [FluidSwipeAppearDamping] con la risposta di [FluidSwipeResponseSeconds]. */
internal fun fluidSwipeAppearSpec(): SpringSpec<Float> =
  FluidMotion.tuned(FluidSwipeAppearDamping, FluidMotion.stiffnessForResponse(FluidSwipeResponseSeconds))

/**
 * Fra la riga e la capsula: 8 dp. Apple ne lascia 16 px = 7,4 pt nel contenitore libero (f33186), 21 px = 9,8 pt
 * nella lista (f32244).
 */
internal const val FluidSwipeCapsuleGapDp = 8f

/** Fra la capsula e il bordo della riga: 8 dp. Apple 15 px = 7 pt dal bordo della card (f33186). */
internal const val FluidSwipeCapsuleInsetDp = 8f

/**
 * Quanto della riga non e' capsula: 24 dp. La capsula e' alta quanto la riga meno l'etichetta e l'aria: in Apple 34 pt
 * in una riga di 58,6 pt (lista) e 44 pt in una di 66,5 pt (contenitore), cioe' 24,6 e 22,5 pt in meno.
 */
internal const val FluidSwipeCapsuleReserveDp = 24f

/** La capsula non supera 44 dp (i 44 pt di Apple nel contenitore) e non scende sotto 24. */
internal const val FluidSwipeCapsuleMaxDp = 44f
internal const val FluidSwipeCapsuleMinDp = 24f

/** Fra la capsula e l'etichetta: 4 dp (Apple 7 pt fino alla maiuscola, cioe' ≈3 pt fino alla riga del testo). */
internal const val FluidSwipeLabelGapDp = 4f

/**
 * A quanti dp da casa la riga che torna si lascia (angoli e velo si spengono col lampo di [FluidMotion.flick]): 2. La
 * molla a pixel finisce a mezzo pixel, ~250 ms dopo che la riga e' a vista arrivata.
 */
internal const val FluidSwipeRestLookDp = 2f

/** Il velo dell'inchiostro sulla riga presa: 7% (la riga presa di Apple sta 20 livelli sotto il foglio, f32210). */
internal const val FluidSwipeHeldVeil = 0.07f

/**
 * Quanto cresce la capsula oltre la soglia: 6%. Scelta a occhio (2026-11-18, F6.6): la soglia di Apple non si vede
 * nelle clip, e un colpo d'aptica senza niente che cambia a vista e' un colpo nel vuoto.
 */
internal const val FluidSwipeArmedGrowth = 0.06f

/** L'altezza nominale della capsula in px, per una riga alta [rowHeightPx]. */
internal fun fluidSwipeCapsuleHeightPx(rowHeightPx: Float, density: Float): Float =
  (rowHeightPx - FluidSwipeCapsuleReserveDp * density)
    .coerceIn(FluidSwipeCapsuleMinDp * density, FluidSwipeCapsuleMaxDp * density)

/**
 * Il glifo nella capsula: 0,45 della sua altezza, fra 16 e 20 dp. In Apple il cestino e' alto 15,8 pt in una capsula
 * da 44 (0,36); un'icona Material occupa ≈0,75 del suo riquadro, quindi 20 dp di riquadro danno ≈15 dp di sagoma.
 */
internal fun fluidSwipeGlyphPx(capsuleHeightPx: Float, density: Float): Float =
  (capsuleHeightPx * 0.45f).coerceIn(16f * density, 20f * density)

/**
 * Lo spazio della capsula, da un capo all'altro in px (sinistra, destra): fra la riga spostata di [physicalPx] (meno il
 * suo margine [gapPx]) e il bordo della riga a riposo (meno [insetPx]). Nullo finche' lo spazio non c'e'.
 */
internal fun fluidSwipeCapsuleSlot(
  physicalPx: Float,
  widthPx: Float,
  gapPx: Float,
  insetPx: Float,
): ClosedFloatingPointRange<Float>? {
  if (physicalPx == 0f || widthPx <= 0f) return null
  val revealed = abs(physicalPx)
  if (revealed <= gapPx + insetPx) return null
  return if (physicalPx < 0f) {
    (widthPx - revealed + gapPx)..(widthPx - insetPx)
  } else {
    insetPx..(revealed - gapPx)
  }
}

/** Dove sta la capsula: il suo riquadro e la scala che glifo ed etichetta prendono con lei. */
internal class FluidSwipeCapsuleGeometry(val capsule: Rect, val scale: Float)

/**
 * Il riquadro della capsula nello spazio da [slotStart] a [slotEnd]: piena, e' larga quanto lo spazio e alta
 * [capsuleHeight] (mai piu' larga di quanto e' alta: in uno spazio stretto e' un tondo); il gruppo capsula +
 * etichetta ([labelBlock]) sta al centro della riga. [appear] la fa crescere attorno al centro del gruppo (in Apple
 * capsula ed etichetta crescono insieme, f32211-f32217), [armed] la gonfia di [FluidSwipeArmedGrowth]. Nullo se la
 * capsula non ha ancora una taglia.
 */
internal fun fluidSwipeCapsuleGeometry(
  slotStart: Float,
  slotEnd: Float,
  rowHeight: Float,
  capsuleHeight: Float,
  labelBlock: Float,
  appear: Float,
  armed: Float,
): FluidSwipeCapsuleGeometry? {
  val slot = slotEnd - slotStart
  val grow = appear.coerceAtLeast(0f) * (1f + FluidSwipeArmedGrowth * armed.coerceIn(0f, 1f))
  if (slot <= 0f || grow <= 0f) return null
  val fullHeight = min(capsuleHeight, slot)
  val width = slot * grow
  val height = fullHeight * grow
  val centerX = (slotStart + slotEnd) / 2f
  // Il centro del gruppo e' quello della riga; la capsula sta sopra, l'etichetta sotto, e crescono attorno a lui.
  val groupCenterY = rowHeight / 2f
  val capsuleCenterY = groupCenterY - labelBlock * grow / 2f
  return FluidSwipeCapsuleGeometry(
    capsule = Rect(
      left = centerX - width / 2f,
      top = capsuleCenterY - height / 2f,
      right = centerX + width / 2f,
      bottom = capsuleCenterY + height / 2f,
    ),
    scale = grow,
  )
}

/**
 * L'opacita' della capsula che nasce: piena da tre quarti della taglia. In Apple i primi tre o quattro fotogrammi sono
 * trasparenti (il rosa della capsula a f32993-f32996), pieni da f32997.
 */
internal fun fluidSwipeAppearAlpha(appear: Float): Float {
  val t = (appear / 0.75f).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}
