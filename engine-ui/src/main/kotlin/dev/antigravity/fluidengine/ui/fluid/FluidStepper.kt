package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Uno stepper: meno e piu' in una capsula, per un numero piccolo che si aggiusta a passi (copie, persone, minuti). Dalla
 * 4.0 (piano «iOS in Android», F6.6): prima l'engine non l'aveva, e le app ne scrivevano uno con due `FluidButton`.
 *
 * ### Com'e' fatto
 *
 * - **La forma e la taglia di Apple.** Una capsula di 94 x 32 dp, le misure di `UIStepper` (94 x 32 pt, documentate da
 *   UIKit fino a iOS 18; iOS 26 lo mette nella lista dei controlli da rivedere senza dire come, R1 K6: le misure di
 *   iOS 27 non ci sono, fiducia media). Due meta' uguali, un filo verticale fra le due, i segni disegnati (non icone):
 *   13 dp di lato, tratto di 2 dp con le estremita' tonde.
 * - **E' un controllo di vetro.** Dove c'e' qualcosa da attraversare (il canvas di una pagina ambientale, un foglio,
 *   una barra: la stessa regola di [FluidChip] e del segmentato) la capsula e' la lastra di [FluidGlassButton]: al
 *   tocco **cresce, si piega verso il dito e si accende dal punto di contatto**, con la pressione e la luce del dito
 *   del vetro (una [FluidPressState] sola per il vetro e per il tocco, come nei tasti). Il punto acceso e' quello del
 *   dito, quindi sta sulla meta' toccata. Dove non c'e' niente da attraversare (una pagina piatta, sotto API 31) e' la
 *   pastiglia opaca nel grigio di riempimento dei campi ([fluidQuietFillAlpha]), che cresce sotto il dito allo stesso
 *   modo.
 * - **Tenuto premuto ripete**, come `UIStepper.autorepeat` (di serie in Apple): dopo il click lungo un passo ogni 150
 *   ms, poi ogni 100 e, dopo venti passi, ogni 50 (i tempi sono a occhio: Apple non li documenta). Ogni passo ripetuto e'
 *   un `Tick`; il primo e' il click del tasto.
 * - **Ai capi** la meta' che non puo' andare oltre si spegne (il segno al 30%, come la meta' disabilitata di Apple) e
 *   non fa niente.
 * - **Per chi legge lo schermo** e' un nodo solo: il nome ([label]), il valore come stato ([valueText]) e due azioni,
 *   «aumenta» e «diminuisci» (le parole di [LocalFluidLabels]), presenti solo quando c'e' un passo da fare.
 *
 * Il valore non lo mostra: in Apple sta a sinistra dello stepper, nella riga (`Stepper("Copie: \(n)", value: $n)`).
 * In una [dev.antigravity.fluidengine.ui.theme.FluidListRow] sta nel `badge`, accanto a un [FluidRowValue].
 *
 * ### Dove si rompe
 *
 * - Il dito tocca la capsula, alta 32 dp: sotto i 48 dp di Android, come il segmentato. Il riquadro del layout resta di
 *   32 dp perche' lo stepper sta nel `badge` di una riga, che ha gia' la sua altezza.
 * - Il valore e' dell'app ([value] e [onValueChange]): se l'app non lo aggiorna, lo stepper chiede ogni volta lo
 *   stesso passo.
 * - [step] deve essere positivo; un valore fuori da [range] torna dentro al primo passo.
 *
 * @param value il valore di adesso.
 * @param onValueChange il valore nuovo, gia' dentro [range].
 * @param range i capi; di serie nessuno.
 * @param step di quanto si muove a ogni passo.
 * @param enabled spento: grigio, niente tocco, niente azioni per TalkBack.
 * @param label il nome per chi legge lo schermo («Copie»): lo stepper da solo non dice cosa conta.
 * @param valueText il valore detto a chi legge lo schermo; di serie il numero.
 * @param repeatOnHold falso per un passo per tocco anche tenendo premuto.
 */
@Composable
fun FluidStepper(
  value: Int,
  onValueChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
  step: Int = 1,
  enabled: Boolean = true,
  label: String? = null,
  valueText: (Int) -> String = { it.toString() },
  repeatOnHold: Boolean = true,
) {
  require(step > 0) { "il passo dello stepper deve essere positivo: era $step" }
  val current by rememberUpdatedState(value)
  val onChange by rememberUpdatedState(onValueChange)
  val haptics = LocalFluidHaptics.current
  val labels = LocalFluidLabels.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val scope = rememberCoroutineScope()
  val touch = remember { FluidStepperTouch() }
  val canDecrement = enabled && fluidStepperCanStep(value, step, range, up = false)
  val canIncrement = enabled && fluidStepperCanStep(value, step, range, up = true)

  val stepOnce: (Boolean) -> Boolean = { up ->
    val next = fluidStepperNext(current, step, range, up)
    if (next != current) onChange(next)
    next != current
  }
  val onClick: () -> Unit = { touch.side?.let { stepOnce(it == FluidStepperSide.Increment) } }
  val onLongClick: (() -> Unit)? = if (!repeatOnHold) {
    null
  } else {
    {
      val up = touch.side == FluidStepperSide.Increment
      touch.repeat?.cancel()
      touch.repeat = scope.launch {
        var shown = current
        var count = 0
        while (touch.held) {
          val next = fluidStepperNext(shown, step, range, up)
          if (next == shown) break
          shown = next
          onChange(next)
          // Il primo passo e' il click lungo, che ha gia' il suo colpo; gli altri sono agganci.
          if (count > 0) haptics.play(FluidHapticEvent.Tick)
          delay(fluidStepperRepeatDelayMillis(count++))
        }
      }
    }
  }

  val description = label
  val state = valueText(value)
  val semantics = Modifier.clearAndSetSemantics {
    if (description != null) contentDescription = description
    stateDescription = state
    customActions = buildList {
      if (canIncrement) add(CustomAccessibilityAction(labels.dateTimeIncrease) { stepOnce(true) })
      if (canDecrement) add(CustomAccessibilityAction(labels.dateTimeDecrease) { stepOnce(false) })
    }
  }
  // La meta' toccata e il dito ancora giu', letti prima di chiunque altro e senza consumare niente: il click e il
  // click lungo del tasto sanno cosi' quale meta' e' stata premuta, e la ripetizione sa quando smettere.
  val sideTracking = Modifier.pointerInput(rtl) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      touch.side = fluidStepperSide(down.position.x, size.width.toFloat(), rtl)
      touch.held = true
      try {
        while (true) {
          // Nel passaggio Final: se la lista ha preso il dito (lo ha consumato per scorrere), la ripetizione smette
          // anche se il dito e' ancora giu'.
          val event = awaitPointerEvent(PointerEventPass.Final)
          if (event.changes.none { it.pressed }) break
          if (event.changes.any { it.pressed && it.isConsumed }) break
        }
      } finally {
        touch.held = false
      }
    }
  }

  val glass = fluidControlGlassBackdrop()
  val scheme = MaterialTheme.colorScheme
  val faces: @Composable () -> Unit = {
    FluidStepperFaces(canDecrement = canDecrement, canIncrement = canIncrement)
  }
  if (glass != null) {
    val press = rememberFluidPressState(FluidPressFeedback.Expand)
    val ink = rememberGlassInk()
    Box(modifier = modifier.then(semantics), contentAlignment = Alignment.Center) {
      Box(
        modifier = Modifier
          .alpha(if (enabled) 1f else FluidStepperDisabledAlpha)
          .size(FluidStepperWidth, FluidStepperHeight)
          .then(sideTracking)
          .glassControlSurfaceInked(
            backdrop = glass,
            shape = FluidCapsuleShape,
            tint = GlassDefaults.controlTint(),
            optics = GlassDefaults.buttonOptics(),
            selected = false,
            interactive = enabled,
            depth = { 1f },
            frozen = { false },
            heldWhileMoving = { false },
            pressState = press,
            ink = ink,
          )
          .fluidPressable(
            onClick = onClick,
            onLongClick = onLongClick,
            enabled = enabled,
            focusShape = FluidCapsuleShape,
            pressState = press,
            longClickHaptic = FluidHapticEvent.Tick,
            // Uno stepper sta in una riga di una lista che scorre: il dito che parte da lui deve poterla far scorrere.
            grip = FluidPressGrip.YieldsToScroll,
          ),
        contentAlignment = Alignment.Center,
      ) { GlassInkContent(ink, scheme.onSurface) { faces() } }
    }
    return
  }

  Box(modifier = modifier.then(semantics), contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier
        .alpha(if (enabled) 1f else FluidStepperDisabledAlpha)
        .size(FluidStepperWidth, FluidStepperHeight)
        .then(sideTracking)
        // Fuori dal ritaglio e dal fondo, cosi' e' la pastiglia a crescere (come il chip opaco).
        .fluidPressable(
          onClick = onClick,
          onLongClick = onLongClick,
          enabled = enabled,
          focusShape = FluidCapsuleShape,
          feedback = FluidPressFeedback.Expand,
          longClickHaptic = FluidHapticEvent.Tick,
          grip = FluidPressGrip.YieldsToScroll,
        )
        .clip(FluidCapsuleShape)
        .background(scheme.onSurface.copy(alpha = fluidQuietFillAlpha(GlassDefaults.isDarkSurface()))),
      contentAlignment = Alignment.Center,
    ) {
      CompositionLocalProvider(LocalContentColor provides scheme.onSurface) { faces() }
    }
  }
}

/** Le due meta': i segni e il filo in mezzo, nell'inchiostro del contenitore. */
@Composable
private fun FluidStepperFaces(canDecrement: Boolean, canIncrement: Boolean) {
  val ink = LocalContentColor.current
  Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
      FluidStepperGlyph(plus = false, color = ink, alpha = if (canDecrement) 1f else FluidStepperDisabledGlyphAlpha)
    }
    Box(
      modifier = Modifier
        .size(FluidStepperDividerWidth, FluidStepperDividerHeight)
        .background(ink.copy(alpha = FluidStepperDividerAlpha)),
    )
    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
      FluidStepperGlyph(plus = true, color = ink, alpha = if (canIncrement) 1f else FluidStepperDisabledGlyphAlpha)
    }
  }
}

@Composable
private fun FluidStepperGlyph(plus: Boolean, color: Color, alpha: Float) {
  Canvas(modifier = Modifier.size(FluidStepperGlyphSize)) {
    val stroke = FluidStepperGlyphStroke.toPx()
    val half = size.minDimension / 2f - stroke / 2f
    val tint = color.copy(alpha = color.alpha * alpha)
    drawLine(tint, Offset(center.x - half, center.y), Offset(center.x + half, center.y), stroke, StrokeCap.Round)
    if (plus) drawLine(tint, Offset(center.x, center.y - half), Offset(center.x, center.y + half), stroke, StrokeCap.Round)
  }
}

/** La meta' toccata e il dito giu': scritti dal gesto, letti dal click e dalla ripetizione, mai dal disegno. */
private class FluidStepperTouch {
  var side: FluidStepperSide? = null
  var held: Boolean = false
  var repeat: Job? = null
}

/** Quale meta' dello stepper. */
internal enum class FluidStepperSide { Decrement, Increment }

/** La capsula: 94 x 32 dp, le misure di `UIStepper` (94 x 32 pt). */
internal val FluidStepperWidth: Dp = 94.dp
internal val FluidStepperHeight: Dp = 32.dp

/** I segni: 13 dp di lato, tratto di 2 dp (il «meno» e il «piu'» di Apple stanno in ≈13 pt, peso medio). */
internal val FluidStepperGlyphSize: Dp = 13.dp
internal val FluidStepperGlyphStroke: Dp = 2.dp

/** Il filo fra le due meta': 1 x 18 dp, l'inchiostro al 20%. */
internal val FluidStepperDividerWidth: Dp = 1.dp
internal val FluidStepperDividerHeight: Dp = 18.dp
internal const val FluidStepperDividerAlpha = 0.2f

/** Il segno della meta' che non puo' andare oltre: al 30%. */
internal const val FluidStepperDisabledGlyphAlpha = 0.3f

/** Tutto lo stepper spento: al 38%, come i tasti di vetro spenti. */
internal const val FluidStepperDisabledAlpha = 0.38f

/** La meta' toccata a [x] (in px dal bordo sinistro) di uno stepper largo [width]: da destra a sinistra si scambiano. */
internal fun fluidStepperSide(x: Float, width: Float, rtl: Boolean): FluidStepperSide {
  val left = x < width / 2f
  return if (left != rtl) FluidStepperSide.Decrement else FluidStepperSide.Increment
}

/**
 * Il valore dopo un passo in su ([up]) o in giu', dentro [range]. Il conto in `Long`: un passo da [Int.MAX_VALUE] non
 * gira. Un valore fuori dai capi ci torna dentro.
 */
internal fun fluidStepperNext(value: Int, step: Int, range: IntRange, up: Boolean): Int {
  val moved = value.toLong() + if (up) step.toLong() else -step.toLong()
  return moved.coerceIn(range.first.toLong(), range.last.toLong()).toInt()
}

/** Se un passo in quel verso cambia qualcosa: falso al capo. */
internal fun fluidStepperCanStep(value: Int, step: Int, range: IntRange, up: Boolean): Boolean =
  fluidStepperNext(value, step, range, up) != value

/**
 * Quanto aspettare dopo il passo ripetuto numero [count] (0 il primo, al click lungo): 150 ms per i primi cinque, 100
 * fino al ventesimo, poi 50. A occhio: Apple dice solo che il valore cambia piu' in fretta tenendo premuto.
 */
internal fun fluidStepperRepeatDelayMillis(count: Int): Long = when {
  count < 5 -> 150L
  count < 20 -> 100L
  else -> 50L
}
