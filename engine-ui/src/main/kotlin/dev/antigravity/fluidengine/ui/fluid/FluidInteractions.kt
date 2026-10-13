package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics

/**
 * Press feedback in the shape Apple gives it: the element itself answers the finger.
 *
 * Deliberately different from the Material default in three ways, each of which is a reason the
 * previous treatment read as "off":
 *
 *  * **No ripple.** A ripple announces a touch point; a change of size announces that the *thing you
 *    touched* responded. The second is what makes a surface feel physical.
 *  * **No shape morphing here.** Animating a corner radius from a screen forces a re-clip every
 *    frame and makes the element's silhouette wobble against its neighbours. Since 1.9.0 shape
 *    morphing exists, but it lives in ONE place — Fluid-physics
 *    ([dev.antigravity.fluidengine.ui.fluidphysics]) — which earns it with its own discipline: the
 *    layer clip never chases the shape (the shader's alpha mask sculpts the silhouette), and the
 *    morph is transient by contract (at rest the surface holds one stable Shape instance). A
 *    corner radius animated ad hoc in a screen is still the bug it always was.
 *  * **The press always finishes.** Since 2.11.0 the movement is a [FluidPressState]: a release
 *    that arrives while the press is still travelling waits for it to arrive, so a 20 ms tap shows
 *    the same complete press as a held one. Before, the release restarted from wherever the press
 *    had got to — a tenth of the way, on a real tap — and inside a list, where `clickable` holds
 *    the press back 100 ms, a quick tap delivered press and release in the same frame and nothing
 *    moved at all.
 *
 * Two families of feedback, see [FluidPressFeedback]: surfaces **yield** a few hundredths
 * ([pressedScale], the behaviour this modifier always had), buttons **expand** — they grow under
 * the finger, lean toward it, stretch along its drag and tick under it as it slides
 * ([FluidPressFeedback.Expand], what every button of the engine uses since 2.11.0).
 *
 * **Un pulsante si trascina sempre (2.12.0).** Per [FluidPressFeedback.Expand] il puntatore non e'
 * piu' di `clickable`: decide [fluidButtonGesture], dall'ora degli eventi e dallo slop. Una passata
 * veloce che parte su un pulsante scorre ancora la pagina — non si consuma niente, non si preme
 * niente, nessun click. Un dito che resta giu' 100 ms senza che la pagina se lo sia preso ha
 * *premuto* il pulsante, e da li' il gesto e' suo fino al rilascio: si inclina, si allunga, scatta,
 * segue il dito anche fuori dai suoi bordi, e ogni cambio e' consumato, quindi un `verticalScroll`
 * o una `LazyColumn` attorno non partono mai. Al rilascio il click scatta se il dito e' dentro il
 * pulsante piu' 16 dp, altrimenti la pressione si scioglie e basta. A `clickable` resta quello che
 * non e' puntatore: la tastiera (Invio, Spazio, fuoco), il click e il click lungo di TalkBack, il
 * ruolo. Le superfici che cedono (card, tessere, [fluidRowPressable]) tengono `clickable` anche
 * per il puntatore: una card non e' qualcosa a cui aggrapparsi mentre si scorre.
 *
 * The transform is applied in the draw phase from state read there, so a press — and a finger
 * dragged across a pressed button — invalidates only this node's drawing: nothing recomposes, and
 * no separate RenderNode is kept alive for every clickable card while it is at rest. Put this
 * modifier *before* the clip and the background of what it presses, or only the content inside
 * them will move.
 *
 * @param pressedScale how far a surface yields, when [feedback] is not given.
 * @param feedback how the element answers the finger. Null keeps the historical behaviour:
 *   `FluidPressFeedback.yielding(pressedScale)`. Since 2.11.0.
 * @param pressState a press shared with whoever *draws* it — a pane of glass reads it in its own
 *   layer, see [glassControlSurface]. Given one, this modifier drives it (press, release, finger,
 *   ticks) and draws no transform of its own; its feedback is the state's, and [feedback] and
 *   [pressedScale] are ignored. Since 2.11.0.
 * @param interactionSource riceve `PressInteraction.Press`/`Release`/`Cancel` per ogni pressione del
 *   puntatore — per un pulsante, quando e' premuto dopo i 100 ms di cui sopra e non al primo
 *   tocco — oltre al fuoco e alle pressioni da tastiera di `clickable`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.fluidPressable(
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  enabled: Boolean = true,
  pressedScale: Float = FluidPressFeedback.DefaultYieldScale,
  role: androidx.compose.ui.semantics.Role? = null,
  interactionSource: MutableInteractionSource? = null,
  /** Cosa si sente al rilascio; null per un controllo che vibra gia' per conto suo. */
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
  /**
   * La sagoma in cui mostrare il fuoco da tastiera con un velo dell'accento, oltre al farsi
   * avanti. Serve a chi non ha una superficie propria che si vede avanzare — un segmento, un'icona
   * di vetro — e dove il 3% su un'etichetta non si nota. Null: solo il farsi avanti.
   */
  focusShape: Shape? = null,
  feedback: FluidPressFeedback? = null,
  pressState: FluidPressState? = null,
  /** Cosa si sente quando scatta il click lungo; null per un controllo che ha il suo colpo. */
  longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart,
): Modifier {
  if (onClick == null && onLongClick == null) return this

  val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
  val ownState = rememberFluidPressState(feedback ?: FluidPressFeedback.yielding(pressedScale))
  val press = pressState ?: ownState
  val drawsPress = pressState == null
  // Un pulsante — tutto cio' che cresce sotto il dito — tiene il dito fino al rilascio; una
  // superficie che cede lo lascia alla pagina e a `clickable`, come ha sempre fatto.
  val ownsGesture = enabled && press.feedback.expands
  // Le pressioni che il gesto scrive sulla sorgente: l'effetto qui sotto non deve premere due volte.
  val emitted = remember { HashSet<PressInteraction.Press>() }
  // Il fuoco da tastiera e' il gesto opposto alla pressione: la cosa si fa avanti invece di
  // cedere, come su tvOS. Senza, con una tastiera attaccata al tablet Tab spostava il fuoco su
  // elementi che non lo mostravano in nessun modo. Col dito il fuoco non arriva mai qui (in
  // modalita' touch un clickable non lo prende), quindi al tocco non cambia niente.
  val focused by resolvedInteractionSource.collectIsFocusedAsState()
  val focusLift = remember { Animatable(1f) }
  val haptics = LocalFluidHaptics.current

  val focusWash by animateFloatAsState(
    targetValue = if (focused && focusShape != null) 1f else 0f,
    animationSpec = if (focused) FluidMotion.instant() else FluidMotion.fadeOut(160),
    label = "fluid focus wash",
  )
  val focusColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

  LaunchedEffect(focused) {
    focusLift.animateTo(if (focused) FocusedScale else 1f, FluidMotion.fluid())
  }

  // La pressione di una superficie che cede viene dalle interazioni di `clickable`: sa gia' quando
  // un tocco e' una pressione (dopo il suo ritardo dentro uno scorrevole, cosi' uno scorrimento
  // che parte su una card non la accende mai) e quando glielo hanno portato via. Quella di un
  // pulsante viene dal suo gesto, deciso nel ciclo del puntatore — un'interazione arriva un
  // fotogramma dopo, e il gesto deve sapere *adesso* se il dito e' del pulsante — e qui si
  // raccoglie, per lui, la tastiera: Invio e Spazio premono e rilasciano dalla stessa sorgente.
  // Raccolta, non letta con `collectIsPressedAsState`, cosi' una pressione non ricompone niente.
  LaunchedEffect(resolvedInteractionSource, press) {
    resolvedInteractionSource.interactions.collect { interaction ->
      when (interaction) {
        is PressInteraction.Press -> if (interaction !in emitted) press.press()
        is PressInteraction.Release -> if (!emitted.remove(interaction.press)) press.release()
        is PressInteraction.Cancel -> if (!emitted.remove(interaction.press)) press.release()
      }
    }
  }
  val tracksFinger = press.feedback.followsFinger || press.feedback.dragHaptics

  // Le stesse due azioni per il puntatore, la tastiera e TalkBack. Il gesto le legge attraverso uno
  // State: il suo ciclo del puntatore sopravvive alle lambda di ogni singola composizione.
  val click: () -> Unit = {
    haptic?.let { haptics.play(it) }
    onClick?.invoke()
  }
  val longClick: (() -> Unit)? = onLongClick?.let {
    {
      longClickHaptic?.let { event -> haptics.play(event) }
      it()
    }
  }
  val currentClick by rememberUpdatedState(click)
  val currentLongClick by rememberUpdatedState(longClick)

  return this
    .drawWithContent {
      if (focusShape != null && focusWash > 0.001f) {
        drawOutline(
          focusShape.createOutline(size, layoutDirection, this),
          focusColor.copy(alpha = focusColor.alpha * focusWash),
        )
      }
      val transform = if (drawsPress) {
        fluidPressTransform(
          feedback = press.feedback,
          progress = press.progress,
          lean = press.lean,
          width = size.width,
          height = size.height,
          dp = density,
          reducedMotion = press.reducedMotion,
        )
      } else {
        FluidPressTransform.Identity
      }
      val lift = focusLift.value
      if (transform.isIdentity && lift == 1f) {
        drawContent()
      } else {
        withTransform({
          translate(transform.translationX, transform.translationY)
          scale(transform.scaleX * lift, transform.scaleY * lift, pivot = center)
        }) {
          this@drawWithContent.drawContent()
        }
      }
    }
    .then(
      when {
        // Prima di `clickable` nella catena: nell'Initial vede il puntatore per primo e consuma il
        // down prima che `clickable` lo legga nel Main. Una sola sorgente di click.
        ownsGesture -> Modifier.fluidButtonGesture(
          state = press,
          interactionSource = resolvedInteractionSource,
          emitted = emitted,
          onClick = { currentClick() },
          onLongClick = if (longClick != null) ({ currentLongClick?.invoke() }) else null,
        )
        enabled && tracksFinger -> Modifier.fluidPressTracking(press, ownsPress = false)
        else -> Modifier
      },
    )
    // Per un pulsante non e' piu' il puntatore — il down l'ha preso il suo gesto — ma la semantica:
    // ruolo, click e click lungo di TalkBack, `disabled`, la tastiera e il fuoco.
    .combinedClickable(
      interactionSource = resolvedInteractionSource,
      indication = null,
      enabled = enabled,
      role = role,
      onClick = click,
      onLongClick = longClick,
    )
}

/**
 * Press feedback for a row inside a grouped list.
 *
 * Rows must not scale: a single row shrinking inside a stack of identical rows breaks the group's
 * silhouette. iOS instead tints the row's background for as long as the finger is down, which is
 * what this does.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.fluidRowPressable(
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  enabled: Boolean = true,
  animateFeedback: Boolean = true,
  shape: Shape? = null,
  highlightColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
): Modifier {
  if (onClick == null && onLongClick == null) return this

  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  // Una riga non si fa avanti — romperebbe il gruppo, come la scala alla pressione — quindi il
  // fuoco da tastiera e' un bordo dell'accento lungo la riga. Solo da tastiera: al tocco una riga
  // non prende mai il fuoco.
  val focused by interactionSource.collectIsFocusedAsState()
  val focusRing by animateFloatAsState(
    targetValue = if (focused) 1f else 0f,
    animationSpec = if (focused) FluidMotion.instant() else FluidMotion.fadeOut(160),
    label = "fluid row focus",
  )
  val focusColor = MaterialTheme.colorScheme.primary
  val haptics = LocalFluidHaptics.current
  // Con un mouse o un trackpad la riga sotto il puntatore si accende a meta': dice cosa si
  // aprirebbe cliccando, come nelle liste di iPadOS. Col dito non c'e' hover.
  val hovered by interactionSource.collectIsHoveredAsState()
  val highlight by animateFloatAsState(
    targetValue = when {
      !animateFeedback -> 0f
      pressed -> 1f
      hovered -> 0.5f
      else -> 0f
    },
    // Appearing instantly and fading out unhurriedly is what stops a quick tap from looking like a
    // flicker: the highlight is always visible for at least the length of the fade.
    animationSpec = if (pressed) FluidMotion.instant() else FluidMotion.fadeOut(260),
    label = "fluid row highlight",
  )

  return this
    .drawWithContent {
      drawContent()
      if (focusRing <= 0.001f) return@drawWithContent
      val width = 2.dp.toPx()
      val color = focusColor.copy(alpha = focusRing)
      if (shape == null) {
        drawRect(
          color = color,
          topLeft = Offset(width / 2, width / 2),
          size = Size(size.width - width, size.height - width),
          style = Stroke(width),
        )
      } else {
        drawOutline(shape.createOutline(size, layoutDirection, this), color, style = Stroke(width))
      }
    }
    .drawBehind {
      if (highlight <= 0.001f) return@drawBehind
      val color = highlightColor.copy(alpha = highlightColor.alpha * highlight)
      if (shape == null) {
        drawRect(color)
      } else {
        drawOutline(shape.createOutline(size, layoutDirection, this), color)
      }
    }
    .combinedClickable(
      interactionSource = interactionSource,
      indication = null,
      enabled = enabled,
      onClick = {
        haptic?.let { haptics.play(it) }
        onClick?.invoke()
      },
      onLongClick = onLongClick?.let {
        {
          haptics.play(FluidHapticEvent.GestureStart)
          it()
        }
      },
    )
}

/** Quanto si fa avanti un elemento col fuoco da tastiera: abbastanza da vederlo, non da spostare i vicini. */
private const val FocusedScale = 1.03f
