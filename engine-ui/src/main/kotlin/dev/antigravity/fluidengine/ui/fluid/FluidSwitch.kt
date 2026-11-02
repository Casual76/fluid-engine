/*
 * The thumb, the squashed copy of the track it refracts, and the numbers that shape both, are
 * ported from Kyant0's LiquidToggle.
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Ported and not reinvented, deliberately. The switch this replaces was a filled track with a lens
 * for a thumb, built out of the engine's own `glassSurface`; so was the first attempt at a glass
 * one, which made the *track* transmit. Both were reasonable-looking controls and both had the
 * construction wrong. What makes the real one feel like glass is that the thumb is the only pane,
 * and that what it refracts is a copy of the track squashed flat, which opens up under the finger.
 * No arrangement of a general surface produces that, so it is taken as it was written.
 *
 * What changed on the way in is named where it happens: the colour is the app's accent instead of
 * the source's hardcoded iOS green; a tap anywhere on the control flips it, where the source only
 * answered the thumb; and it has an `enabled` state, haptics and accessibility, because every
 * other control in this engine does.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropLens
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropRecipe
import dev.antigravity.fluidengine.ui.glass.interaction.glassDrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.flow.collectLatest

/**
 * The switch.
 *
 * The one from the catalogue the rest of this engine's glass comes from, and the construction is
 * the whole of it:
 *
 *  * **The track is a recording, not a background.** It paints itself into its own layer, and that
 *    layer is what the thumb reads. Nothing else in the engine has one surface whose entire job is
 *    to be looked at through another.
 *  * **The thumb is the only pane, and at rest it is opaque.** White at full strength, a pill with
 *    a shadow. Held, that white fades out completely and what is left is a lens: the page behind
 *    the switch, plus a copy of the track squashed to nothing vertically and two thirds
 *    horizontally, opening to three quarters of both as the finger stays down. The accent under
 *    the thumb visibly *unfolds*. That is the effect, and it only exists while it is held.
 *  * **It stretches.** Dragged fast it thins along its direction of travel and swells across it,
 *    by how fast it is going. It is most of why the thing feels like an object being pushed.
 *
 * **Si tocca o si trascina, da qualunque punto (2.13.1).** Un solo gesto su tutto il controllo:
 *
 *  * **un tocco** — un dito che si solleva prima di uno slop — commuta, ovunque cada: sul pomello,
 *    sulla meta' vuota della pista, nell'alone di bersaglio attorno;
 *  * **un dito sul pomello** lo prende subito: il pomello si gonfia, segue il dito senza zona morta
 *    dal primo pixel, e il gesto e' suo sui due assi fino al rilascio;
 *  * **un dito sulla pista vuota** che va di lato per mezzo slop (prima che in verticale) prende il
 *    pomello da dove sta e lo porta; se va prima in verticale e' la pagina che scorre, e una riga
 *    con un interruttore non diventa una fascia da cui la lista non si muove.
 *
 * Al rilascio il pomello va dalla parte piu' vicina, o da quella verso cui correva se la spinta era
 * veloce ([fluidSwitchLanding]).
 *
 * **Mentre si trascina la pagina e' ferma.** Ogni cambiamento del puntatore preso viene consumato,
 * e lo legge prima di lei (il passaggio principale va dal figlio al padre): la `LazyColumn` o il
 * `verticalScroll` attorno non vedono mai un movimento da cui partire, qualunque sia la deriva.
 *
 * **Si sente.** Una vibrazione leggera ([FluidHapticEvent.ToggleOn] / [FluidHapticEvent.ToggleOff])
 * a ogni tocco che commuta, e durante un trascinamento nel momento in cui il pomello passa la meta',
 * che e' quando l'interruttore si accende o si spegne sotto il dito. Al rilascio non si ripete, a
 * meno che il pomello non finisca dall'altra parte rispetto all'ultima vibrazione.
 *
 * The colour is the app's [androidx.compose.material3.ColorScheme.primary], so it belongs to
 * whatever palette the app is wearing, Material You included.
 *
 * Below API 31 there is no `RenderEffect`, so the thumb is a plain white pill with a shadow on a
 * filled track: the same switch with the one thing it cannot have taken out.
 *
 * @param onCheckedChange null when the row around it owns the `toggleable` and the 48 dp target.
 *   The switch then draws and animates but answers nothing itself.
 * @param backdrop what the thumb refracts, under the track's own copy. Defaults to the surface in
 *   scope: a screen's ambient canvas if it has one, otherwise the chrome backdrop, otherwise
 *   nothing — and with nothing there the thumb still has the track to bend, which is most of it.
 */
@Composable
fun FluidSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  val scheme = MaterialTheme.colorScheme
  val dark = GlassDefaults.isDarkSurface()
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion

  val accentColor = scheme.primary
  // The source's neutral, kept. Taking it from `onSurface` instead would tint the *unfilled* half
  // of the control with the accent on any Material You palette, and "off" is precisely the state
  // that is not the accent.
  val trackColor =
    if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.2f)

  val density = LocalDensity.current
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val dragWidth = with(density) { DragWidth.toPx() }
  val animationScope = rememberCoroutineScope()
  val currentChecked by rememberUpdatedState(checked)
  val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
  // Letti *ora* e non catturati allora: il gesto vive in un `pointerInput(Unit)` che sopravvive a
  // ogni ricomposizione, e una lambda che ha chiuso il valore di allora lo tiene per sempre.
  val currentHaptics by rememberUpdatedState(haptics)
  val currentIsLtr by rememberUpdatedState(isLtr)
  val currentReducedMotion by rememberUpdatedState(reducedMotion)

  var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

  // Solo la fisica: chi la muove e' il gesto qui sotto, non il rilevatore di Kyant che la classe si
  // porta dietro (`dragAnimation.modifier` non e' applicato da nessuna parte, di proposito).
  val dragAnimation = remember(animationScope) {
    GlassDragAnimation(
      animationScope = animationScope,
      initialValue = fraction,
      valueRange = 0f..1f,
      visibilityThreshold = 0.001f,
      initialScale = 1f,
      pressedScale = 1.5f,
    )
  }

  // Two directions, and they are not the same animation. This one is the finger: the fraction
  // moves under a drag and the thumb follows it without a press ramp of its own.
  LaunchedEffect(dragAnimation, reducedMotion) {
    snapshotFlow { fraction }.collectLatest { value ->
      if (reducedMotion) dragAnimation.snapToValue(value) else dragAnimation.updateValue(value)
    }
  }
  // And this one is the caller. `currentChecked` and not `checked`: this effect is keyed on the
  // animation, so it is not restarted when the parameter changes, and a captured parameter is
  // frozen at the composition that started it. Reading the `rememberUpdatedState` is a snapshot
  // read, which is the thing `snapshotFlow` can actually see.
  //
  // It is also the correction path: a caller that refuses the change — a switch whose state is
  // owned elsewhere and did not move — puts the thumb back instead of leaving it lying about a
  // state that never happened.
  LaunchedEffect(dragAnimation, reducedMotion) {
    snapshotFlow { currentChecked }.collectLatest { isChecked ->
      val target = if (isChecked) 1f else 0f
      if (target != fraction) {
        fraction = target
        if (reducedMotion) {
          dragAnimation.snapToValue(target)
        } else {
          dragAnimation.animateToValue(target)
        }
      }
    }
  }

  val trackBackdrop = rememberLayerBackdrop()
  // The track, flattened. At rest it is squashed to nothing vertically and two thirds
  // horizontally, and it opens to three quarters of both while the thumb is held — so what the
  // lens shows is not a picture of the track but the track *unfolding* under the finger.
  val squashedTrack: DrawScope.(DrawScope.() -> Unit) -> Unit = remember(dragAnimation) {
    { drawTrack ->
      val progress = dragAnimation.pressProgress
      scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) { drawTrack() }
    }
  }
  val thumbBackdrop: Backdrop = rememberCombinedBackdrop(
    backdrop.backdrop,
    rememberBackdrop(trackBackdrop, squashedTrack),
  )

  val interactive = enabled && onCheckedChange != null
  val interactionSource = remember { MutableInteractionSource() }

  /**
   * Il pomello si posa su [target]. Ottimista, come ogni indicatore di questo design: il gesto e'
   * l'affermazione, il cambio di stato del chiamante e' come la si registra, e far aspettare il
   * pomello il giro completo e' quello che si legge come ritardo. L'effetto qui sopra lo rimette a
   * posto se il chiamante dice di no.
   *
   * [announced] e' il lato che il dito ha gia' sentito: un trascinamento vibra quando il pomello
   * passa la meta', e al rilascio non vibra una seconda volta per la stessa cosa. Vibra solo se il
   * pomello finisce da una parte diversa da quella che il dito ha sentito per ultima.
   */
  fun settle(target: Boolean, announced: Boolean) {
    fraction = if (target) 1f else 0f
    if (target != announced) currentHaptics.play(fluidSwitchHaptic(target))
    if (target != currentChecked) currentOnCheckedChange?.invoke(target)
  }

  // Un solo rilevatore per tutto il controllo: tocco e trascinamento, sulla pista, sul pomello e
  // nell'alone di bersaglio che Compose aggiunge attorno a un controllo piu' piccolo di 48 dp.
  //
  // Prima erano tre pezzi che si parlavano attraverso un contatore — un rilevatore sul solo pomello
  // che leggeva senza consumare, un `toggleable` che faceva il suo click, e un modificatore che
  // consumava per tenere ferma la pagina — e il risultato, sul telefono, era un interruttore che si
  // poteva solo toccare: un trascinamento partito dalla pista non era di nessuno, e uno partito dal
  // pomello buttava i primi otto dp nello slop e tornava indietro se non arrivava a diciotto.
  val gestures = Modifier.pointerInput(Unit) {
    awaitEachGesture {
      // Nell'Initial, dal padre al figlio: il `toggleable` qui sotto (e una riga cliccabile attorno)
      // leggono il loro nel Main, lo trovano consumato e lo saltano. A loro restano tastiera, fuoco e
      // TalkBack; il puntatore e' tutto di questo gesto, e non ci sono due click per un tocco.
      // La pagina invece scorre ancora: il suo `scrollable` non chiede un down libero.
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      down.consume()

      val slop = viewConfiguration.touchSlop
      val dragPx = DragWidth.toPx()
      val flingPx = FlingVelocity.toPx()
      val ltr = currentIsLtr
      // Tutto si misura dall'inizio del controllo: da destra a sinistra si specchia la x, qui.
      fun startX(x: Float) = if (ltr) x else size.width - x

      val thumbStart = ThumbInset.toPx() + dragPx * dragAnimation.value
      val onThumb = startX(down.position.x) in
        (thumbStart - ThumbGrabMargin.toPx())..(thumbStart + ThumbWidth.toPx() + ThumbGrabMargin.toPx())

      // Il pomello sotto il dito e' gia' preso: si gonfia subito, e da qui in poi ogni movimento di
      // questo puntatore e' suo, sui due assi, fino al rilascio. Sulla pista nuda invece puo' ancora
      // essere qualcuno che scorre la pagina, e si decide col primo mezzo slop (come lo slider).
      var owned = onThumb
      var pressed = false
      if (owned) {
        dragAnimation.press()
        pressed = true
      }
      var anchorX = startX(down.position.x)
      var anchorFraction = fraction
      var announced = currentChecked
      var maxDistance = 0f
      var travel = Offset.Zero
      val velocity = VelocityTracker()
      velocity.addPosition(down.uptimeMillis, Offset(startX(down.position.x), 0f))

      try {
        while (true) {
          val event = awaitPointerEvent()
          val change = event.changes.fastFirstOrNull { it.id == down.id }
          if (change == null) {
            // Il puntatore e' sparito senza un rilascio: se stava trascinando, il pomello va dalla
            // parte piu' vicina; se no non e' successo niente.
            if (owned && maxDistance >= slop) settle(fraction >= 0.5f, announced)
            break
          }
          // Il primo evento del Main e' lo stesso down che abbiamo consumato nell'Initial: e' nostro,
          // non di qualcun altro, e leggerlo come "preso" chiudeva il gesto prima che cominciasse.
          if (change.changedToDownIgnoreConsumed()) continue
          // Qualcuno sopra di noi l'ha preso prima che fosse nostro: e' suo.
          if (!owned && change.isConsumed) break

          if (!change.pressed) {
            val tap = maxDistance < slop
            if (owned || tap) change.consume()
            when {
              tap -> {
                // Un tocco, ovunque sul controllo: commuta. Con la molla e la pressione della
                // sorgente, cosi' anche un tocco mostra il vetro che si apre mentre il pomello va.
                val target = !currentChecked
                settle(target, announced)
                if (!currentReducedMotion) {
                  dragAnimation.animateToValue(if (target) 1f else 0f)
                  // La pressione la chiude animateToValue, quando il pomello e' arrivato.
                  pressed = false
                }
              }
              owned -> {
                velocity.addPosition(change.uptimeMillis, Offset(startX(change.position.x), 0f))
                val speed = velocity.calculateVelocity().x / dragPx
                settle(fluidSwitchLanding(fraction, speed, flingPx / dragPx), announced)
              }
            }
            break
          }

          val moved = change.position - down.position
          maxDistance = maxOf(maxDistance, moved.getDistance())
          travel += change.positionChange()

          if (!owned) {
            when (fluidSliderClaim(travel, slop)) {
              // Di lato prima che in verticale: e' un trascinamento dell'interruttore, e da qui il
              // pomello segue il dito dal punto in cui e' stato preso, senza saltargli sotto.
              FluidSliderClaim.Slider -> {
                owned = true
                anchorX = startX(change.position.x)
                anchorFraction = fraction
                dragAnimation.press()
                pressed = true
              }
              // La pagina scorre: il gesto e' suo, e questo controllo non si e' mai mosso.
              FluidSliderClaim.Page -> break
              FluidSliderClaim.Undecided -> Unit
            }
          }

          if (owned) {
            val x = startX(change.position.x)
            velocity.addPosition(change.uptimeMillis, Offset(x, 0f))
            // Dal punto di presa, non sommando i passi: un dito che sborda oltre la fine della corsa
            // e torna indietro non sposta il pomello finche' non ripassa da dove era uscito.
            fraction = (anchorFraction + (x - anchorX) / dragPx).fastCoerceIn(0f, 1f)
            val side = fluidSwitchSide(fraction, announced)
            if (side != announced) {
              // Il pomello ha passato la meta': e' li' che l'interruttore si accende o si spegne
              // sotto il dito, e li' si sente. Una volta per passaggio, con un margine attorno al
              // centro perche' un dito fermo li' sopra non ronzi.
              announced = side
              currentHaptics.play(fluidSwitchHaptic(side))
            }
            // I due assi, ogni cambiamento, fino al rilascio: la pagina sotto un interruttore che
            // si trascina non si muove, qualunque sia la deriva del dito.
            change.consume()
          }
        }
      } finally {
        if (pressed) dragAnimation.release()
      }
    }
  }

  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .size(TrackWidth, TrackHeight)
      // Il gesto prima del `toggleable`, cioe' fuori: nell'Initial lo legge per primo e ne consuma
      // il down. Vedi sopra.
      .then(if (interactive) gestures else Modifier)
      .then(
        if (interactive) {
          Modifier.toggleable(
            value = checked,
            enabled = true,
            role = Role.Switch,
            interactionSource = interactionSource,
            // The thumb is the feedback, and it moves on the way down. A ripple on a 64 x 28
            // capsule is a second answer to the same touch.
            indication = null,
            // Solo tastiera, fuoco e TalkBack: il puntatore lo prende il gesto qui sopra.
            onValueChange = { value -> settle(value, announced = currentChecked) },
          )
        } else {
          Modifier
        },
      ),
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      Modifier
        .layerBackdrop(trackBackdrop)
        .clip(FluidCapsuleShape)
        .drawBehind { drawRect(lerp(trackColor, accentColor, dragAnimation.value)) }
        .size(TrackWidth, TrackHeight),
    )

    Box(
      Modifier
        .graphicsLayer {
          val padding = ThumbInset.toPx()
          translationX = if (isLtr) {
            lerp(padding, padding + dragWidth, dragAnimation.value)
          } else {
            lerp(-padding, -(padding + dragWidth), dragAnimation.value)
          }
        }
        .then(
          if (isRenderEffectSupported()) {
            // La goccia di Kyant (`glass/interaction/GlassDrop.kt`), coi numeri del port di
            // `LiquidToggle`: lente 5/10 dp, e lo squash CON IL SEGNO e diviso per 50, senza il
            // cancello delle animazioni ridotte. E' il metro della ricetta e conserva i suoi numeri.
            Modifier.glassDrop(
              backdrop = thumbBackdrop,
              drag = dragAnimation,
              lensSize = GlassDropLens.Switch,
              squashDivisor = GlassDropRecipe.SwitchSquashDivisor,
              squashSigned = true,
            )
          } else {
            Modifier
              .shadow(2.dp, FluidCapsuleShape)
              .clip(FluidCapsuleShape)
              .drawBehind { drawRect(Color.White) }
          },
        )
        .size(ThumbWidth, ThumbHeight),
    )
  }
}

/**
 * The name this control had for one release, while it was a second control beside the old switch.
 *
 * It is the switch now, so there is nothing for a separate name to mean. Deprecated rather than
 * deleted for the usual reason: an app that adopted it in 2.1.0 should not have to be edited to
 * keep building.
 */
@Deprecated(
  "FluidSwitch is this control now.",
  ReplaceWith("FluidSwitch(checked, onCheckedChange, modifier, enabled, backdrop)"),
)
@Composable
fun FluidGlassSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  FluidSwitch(checked, onCheckedChange, modifier, enabled, backdrop)
}

/**
 * 64 x 28, with a 40 x 24 thumb and two dp of padding.
 *
 * The source's numbers, kept whole. They are not the 56 x 31 with a round 27 dp thumb this control
 * used to be: the thumb here is a *pill*, and twenty dp of travel under a forty dp thumb is what
 * makes the squashed track underneath read as something being uncovered rather than something
 * sliding.
 */
private val TrackWidth: Dp = 64.dp
private val TrackHeight: Dp = 28.dp
private val ThumbWidth: Dp = 40.dp
private val ThumbHeight: Dp = 24.dp
private val ThumbInset: Dp = 2.dp

/** How far the thumb travels, and therefore how far a finger has to drag to flip the switch. */
private val DragWidth: Dp = 20.dp

/**
 * Width helper so a row of controls can reserve the switch's footprint without instantiating one.
 *
 * It moved here with the control, and its value moved with it: 64 instead of 56. A layout that
 * reserved this is still right; one that wrote 56 by hand is now eight dp short.
 */
val FluidSwitchWidth: Dp = TrackWidth

/**
 * Quanto oltre i bordi del pomello un dito lo afferra ancora.
 *
 * Sotto un polpastrello un pomello da 40 dp e' quasi tutto coperto, e il punto in cui il sistema
 * dice che il dito e' sceso cade spesso un poco fuori. Poco, pero': la pista vuota accanto deve
 * restare un posto da cui la pagina si scorre.
 */
private val ThumbGrabMargin: Dp = 4.dp

/**
 * La velocita' oltre la quale un rilascio va dalla parte verso cui il dito correva, e non da quella
 * piu' vicina. Una spinta rapida che si ferma prima della meta' e' comunque una spinta.
 */
private val FlingVelocity: Dp = 250.dp

/**
 * Di quanto il pomello deve passare la meta' perche' il trascinamento la conti come passata.
 *
 * Senza margine un dito fermo proprio sul centro accenderebbe e spegnerebbe la vibrazione a ogni
 * pixel di tremolio.
 */
internal const val FluidSwitchSideHysteresis = 0.08f

/**
 * Il lato che un pomello a [fraction] (0 spento, 1 acceso) mostra, se fino a ora mostrava [current].
 * Cambia solo oltre la meta' piu' [FluidSwitchSideHysteresis].
 */
internal fun fluidSwitchSide(fraction: Float, current: Boolean): Boolean = when {
  !current && fraction >= 0.5f + FluidSwitchSideHysteresis -> true
  current && fraction <= 0.5f - FluidSwitchSideHysteresis -> false
  else -> current
}

/**
 * Da che parte si posa un pomello lasciato a [fraction] mentre correva a [velocity] (corse al
 * secondo, positivo verso "acceso"): la parte verso cui andava se andava piu' veloce di
 * [flingVelocity], altrimenti la piu' vicina.
 */
internal fun fluidSwitchLanding(fraction: Float, velocity: Float, flingVelocity: Float): Boolean = when {
  velocity >= flingVelocity -> true
  velocity <= -flingVelocity -> false
  else -> fraction >= 0.5f
}

/** Cosa si sente quando l'interruttore si accende o si spegne: un colpo solo, leggero. */
internal fun fluidSwitchHaptic(on: Boolean): FluidHapticEvent =
  if (on) FluidHapticEvent.ToggleOn else FluidHapticEvent.ToggleOff
