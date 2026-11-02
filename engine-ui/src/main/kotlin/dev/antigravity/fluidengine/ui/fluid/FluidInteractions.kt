package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.onLongClick as semanticsOnLongClick
import androidx.compose.ui.semantics.semantics
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
 *  * **The press is one thing.** Since 2.11.0 the movement is a [FluidPressState], shared by whoever
 *    drives it and whoever draws it, so the click, the glass and the light all read the same number.
 *
 * Two families of feedback, see [FluidPressFeedback]: surfaces **yield** a few hundredths
 * ([pressedScale], the behaviour this modifier always had), buttons **expand**
 * ([FluidPressFeedback.Expand], what every button of the engine uses since 2.11.0).
 *
 * **Un pulsante e' LibrePods (3.0).** Per [FluidPressFeedback.Expand] il puntatore lo guida
 * [fluidButtonGesture], e la regola e' quella di LibrePods, che Alessio ha scelto come riferimento:
 *
 *  * **La pressione parte al contatto**, senza ritardo e senza soglia, in una lista come in una barra.
 *  * **Il pulsante non prende mai il gesto.** Non consuma niente: una passata che parte su un
 *    pulsante in una `LazyColumn` scorre la lista *e* il pulsante cresce e segue il dito, insieme.
 *    Sostituisce la regola della 2.12 ("un pulsante e' di chi lo tiene premuto": 100 ms di attesa e
 *    poi il gesto era suo e la pagina non partiva).
 *  * **Il click e' di `clickable`**, come in LibrePods: scatta se il dito si solleva sul pulsante e
 *    nessuno ha consumato, quindi non scatta se la pagina ha scorso. Tastiera (Invio, Spazio, fuoco),
 *    TalkBack e ruolo sono i suoi, e non sono cambiati.
 *  * **Il click lungo resta** (ai menu serve): scatta se il dito e' rimasto entro lo slop per il tempo
 *    del click lungo, e dopo non c'e' click al rilascio.
 *  * **Tre colpi per tocco**, con le costanti di sistema: [FluidHapticEvent.PressDown] al contatto,
 *    [FluidHapticEvent.PressRelease] al rilascio, [FluidHapticEvent.PressClick] al click, piu' la
 *    grana mentre il dito corre. Vedi [haptic].
 *
 * Le superfici che cedono (card, tessere, [fluidRowPressable]) tengono `clickable` anche per il
 * puntatore e non cambiano: una card non e' qualcosa a cui aggrapparsi mentre si scorre.
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
 * @param interactionSource riceve le interazioni di `clickable`: `PressInteraction` (dal contatto, o
 *   dopo il ritardo di `clickable` dentro uno scorrevole), il fuoco, la tastiera. Dalla 3.0 e'
 *   `clickable` e basta, anche per un pulsante: non emette piu' niente di suo.
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
  /**
   * Cosa si sente al click; null per un controllo che vibra gia' per conto suo.
   *
   * Per un pulsante che cresce ([FluidPressFeedback.expands]) il default, [FluidHapticEvent.Tap],
   * vuol dire il click di LibrePods, [FluidHapticEvent.PressClick]; un evento diverso (un tasto che
   * apre un menu passa `Open`) lo **sostituisce**, e i colpi del contatto e del rilascio restano.
   * `null` spegne tutto: contatto, rilascio, grana e click, perche' un controllo che ha il suo colpo
   * non deve averne tre in piu'.
   */
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
  // Un pulsante — tutto cio' che cresce sotto il dito — risponde al dito dal contatto e non lo
  // prende mai; una superficie che cede lo lascia alla pagina e a `clickable`, come ha sempre fatto.
  val expands = press.feedback.expands
  val ownsGesture = enabled && expands
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
  // che parte su una card non la accende mai) e quando glielo hanno portato via. Raccolta, non letta
  // con `collectIsPressedAsState`, cosi' una pressione non ricompone niente.
  //
  // **Quella di un pulsante no**, ed e' il punto: se la pagina parte a scorrere `clickable` annulla
  // e manda un `Cancel`, e il pulsante si scioglierebbe sotto un dito che e' ancora giu'. Il
  // pulsante la prende dal suo gesto (il puntatore) e da `fluidButtonKeys` (la tastiera).
  LaunchedEffect(resolvedInteractionSource, press, expands) {
    if (expands) return@LaunchedEffect
    resolvedInteractionSource.interactions.collect { interaction ->
      when (interaction) {
        is PressInteraction.Press -> press.press()
        is PressInteraction.Release -> press.release()
        is PressInteraction.Cancel -> press.release()
      }
    }
  }
  val tracksFinger = press.feedback.followsFinger || press.feedback.dragHaptics

  // Le stesse due azioni per il puntatore, la tastiera e TalkBack. Il gesto le legge attraverso uno
  // State: il suo ciclo del puntatore sopravvive alle lambda di ogni singola composizione.
  val clickHaptic = if (expands && haptic == FluidHapticEvent.Tap) FluidHapticEvent.PressClick else haptic
  val click: () -> Unit = {
    clickHaptic?.let { haptics.play(it) }
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
    // La tastiera prima di `clickable` nella catena: l'anteprima dei tasti va dai genitori ai
    // figli, e il fuoco e' sul nodo di `clickable`.
    .then(if (ownsGesture) Modifier.fluidButtonKeys(press) else Modifier)
    .then(
      if (expands) {
        // Il click e' di `clickable`, come in LibrePods. Il click lungo non passa da `clickable`
        // (il suo scatterebbe senza lo slop, e in piu' il gesto qui sotto lo sa gia'): a lui
        // resta solo il ruolo e, per TalkBack, l'azione.
        Modifier
          .clickable(
            interactionSource = resolvedInteractionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = click,
          )
          .semantics {
            if (enabled && longClick != null) {
              semanticsOnLongClick(label = null) {
                currentLongClick?.invoke()
                true
              }
            }
          }
      } else {
        Modifier.combinedClickable(
          interactionSource = resolvedInteractionSource,
          indication = null,
          enabled = enabled,
          role = role,
          onClick = click,
          onLongClick = longClick,
          // Il click lungo ha gia' il suo colpo, dal vocabolario (`longClickHaptic`): quello di
          // piattaforma che `combinedClickable` suona da se' gli si sommava sotto.
          hapticFeedbackEnabled = false,
        )
      },
    )
    // Dopo `clickable` nella catena: e' piu' interno, quindi nel passaggio Main vede il puntatore
    // *prima* di lui. Il rilascio passa di qui (`PressRelease`) e poi dal click (`PressClick`).
    .then(
      when {
        ownsGesture -> Modifier.fluidButtonGesture(
          state = press,
          playsHaptics = haptic != null,
          onLongClick = if (longClick != null) ({ currentLongClick?.invoke() }) else null,
        )
        enabled && tracksFinger -> Modifier.fluidPressTracking(press, ownsPress = false)
        else -> Modifier
      },
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
  /**
   * Cosa si sente quando scatta il click lungo; null per chi ha un colpo suo (3.0). Una riga che
   * apre il proprio menu contestuale passa null e suona `Open` lei, solo se il menu si e' aperto:
   * prima suonavano GestureStart qui e Open nel menu, due colpi per un momento.
   */
  longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart,
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
          longClickHaptic?.let { event -> haptics.play(event) }
          it()
        }
      },
      // Il colpo del click lungo e' quello del vocabolario, qui sopra: niente LongPress di
      // piattaforma sommato sotto.
      hapticFeedbackEnabled = false,
    )
}

/** Quanto si fa avanti un elemento col fuoco da tastiera: abbastanza da vederlo, non da spostare i vicini. */
private const val FocusedScale = 1.03f
