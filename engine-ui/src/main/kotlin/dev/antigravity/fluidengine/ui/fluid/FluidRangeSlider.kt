/*
 * Il cursore a intervallo (4.0, F8.2): due maniglie su una pista fissano il minimo e il massimo di un filtro. Apple non
 * ha il controllo (lo `Slider` di SwiftUI ha una maniglia sola): il gesto e l'accessibilita' sono quelli di `UISlider`,
 * e la maniglia e la sua fisica sono **quelle di [FluidSlider]**, cioe' il port del `LiquidSlider` di Kyant0
 * (Apache-2.0, vedi `FluidSlider.kt` e `LICENSES/AndroidLiquidGlass.md`): `FluidSliderThumb`,
 * `rememberFluidSliderDrag`, `rememberFluidSliderThumbBackdrop`, la salita nel vetro di F6.3. Qui c'e' solo cio' che due
 * maniglie hanno in piu': chi prende il dito, gli incroci, la distanza minima, due nodi per TalkBack.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.zIndex
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.interaction.rememberGlassDropRiseMotion
import dev.antigravity.fluidengine.ui.haptics.FluidDetent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticDetents
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Un cursore con due maniglie: il minimo e il massimo di un intervallo (la durata dei brani, il peso delle foto, la
 * dimensione di un allegato). E' [FluidSlider] con una maniglia in piu': la pista, le maniglie di vetro che salgono sotto
 * il dito, la presa, le tacche e l'aptica sono le sue.
 *
 * **Le maniglie non si scavalcano.** Quella che si trascina si ferma sull'altra (o a [minDistance] da lei, se c'e'), con
 * un colpo aptico quando la raggiunge; non la spinge.
 *
 * **Chi prende il dito.**
 *  - Un dito su una maniglia (o a 10 dp dal bordo) prende quella, subito, come nello slider.
 *  - Su due maniglie sovrapposte decide il primo movimento: verso i valori piu' alti la maniglia del massimo, verso i
 *    piu' bassi quella del minimo. Cosi' due maniglie in fondo alla pista si separano sempre.
 *  - Sulla pista nuda: un tocco porta li' la maniglia piu' vicina (con la molla); un trascinamento di lato per mezzo
 *    slop la prende e la porta al dito; se va prima in verticale e' della pagina.
 *
 * **Il valore e' del chiamante** ([value]), come nello slider: le maniglie seguono sempre lui. [onValueChange] arriva a
 * ogni cambio vero, con l'intervallo gia' ordinato e dentro [valueRange]; [onValueChangeFinished] alla fine di un gesto
 * (il momento di applicare il filtro).
 *
 * **TalkBack**: due nodi, uno per maniglia, ognuno regolabile da solo («aumenta», «diminuisci», «imposta») dentro lo
 * spazio che l'altra gli lascia. Senza [startContentDescription] e [endContentDescription] si chiamano «Minimo» e
 * «Massimo»; senza le descrizioni di stato TalkBack legge il numero.
 *
 * @param value l'intervallo scelto; un intervallo al contrario si legge ordinato.
 * @param minDistance la distanza minima fra le due maniglie, nell'unita' del valore. Zero: possono toccarsi.
 * @param snapValues le tacche, come in [FluidSlider]: valgono per tutte e due le maniglie.
 * @param snapThreshold la portata del magnete, come frazione dell'intervallo.
 */
@Composable
fun FluidRangeSlider(
  value: ClosedFloatingPointRange<Float>,
  onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  enabled: Boolean = true,
  onValueChangeFinished: (() -> Unit)? = null,
  minDistance: Float = 0f,
  snapValues: List<Float> = emptyList(),
  snapThreshold: Float = 0.03f,
  startContentDescription: String? = null,
  endContentDescription: String? = null,
  startStateDescription: String? = null,
  endStateDescription: String? = null,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  val scheme = MaterialTheme.colorScheme
  val dark = GlassDefaults.isDarkSurface()
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val labels = LocalFluidLabels.current.dateTime
  val scope = rememberCoroutineScope()

  val accentColor = scheme.primary
  val trackColor = scheme.onSurface.copy(
    alpha = if (dark) FluidSliderMetrics.TrackAlphaOnDark else FluidSliderMetrics.TrackAlphaOnLight,
  )
  val stopIdleColor = scheme.onSurface.copy(alpha = if (dark) 0.38f else 0.30f)

  val gap = minDistance.coerceAtLeast(0f)
  val shownStart = fluidRangeStartOf(value, valueRange, gap)
  val shownEnd = fluidRangeEndOf(value, valueRange, gap)
  val startFraction = fluidSliderFraction(shownStart, valueRange)
  val endFraction = fluidSliderFraction(shownEnd, valueRange)

  val currentStart by rememberUpdatedState(shownStart)
  val currentEnd by rememberUpdatedState(shownEnd)
  val currentOnValueChange by rememberUpdatedState(onValueChange)
  val currentFinished by rememberUpdatedState(onValueChangeFinished)
  val currentRange by rememberUpdatedState(valueRange)
  val currentStops by rememberUpdatedState(snapValues)
  val currentThreshold by rememberUpdatedState(snapThreshold)
  val currentGap by rememberUpdatedState(gap)
  val currentHaptics by rememberUpdatedState(haptics)
  val currentReducedMotion by rememberUpdatedState(reducedMotion)
  val currentIsLtr by rememberUpdatedState(isLtr)
  val currentStartFraction by rememberUpdatedState(startFraction)
  val currentEndFraction by rememberUpdatedState(endFraction)

  // Due maniglie, due molle: la stessa di FluidSlider, una per maniglia.
  val startDrag = rememberFluidSliderDrag(scope, startFraction)
  val endDrag = rememberFluidSliderDrag(scope, endFraction)
  LaunchedEffect(startDrag, reducedMotion) {
    snapshotFlow { currentStartFraction }.collectLatest { target ->
      if (abs(startDrag.targetValue - target) > FluidSliderMetrics.Sync) {
        if (reducedMotion) startDrag.snapToValue(target) else startDrag.updateValue(target)
      }
    }
  }
  LaunchedEffect(endDrag, reducedMotion) {
    snapshotFlow { currentEndFraction }.collectLatest { target ->
      if (abs(endDrag.targetValue - target) > FluidSliderMetrics.Sync) {
        if (reducedMotion) endDrag.snapToValue(target) else endDrag.updateValue(target)
      }
    }
  }

  // Quale maniglia ha il dito (per lo stiramento della lente) e quale sta sopra: l'ultima presa.
  var held by remember { mutableIntStateOf(RangeNone) }
  var onTop by remember { mutableIntStateOf(RangeEnd) }

  val detents = remember { FluidHapticDetents() }
  val notches = remember { FluidSliderNotchGate() }
  val blocked = remember { booleanArrayOf(false) }

  val trackBackdrop = rememberLayerBackdrop()
  val startBackdrop = rememberFluidSliderThumbBackdrop(backdrop, trackBackdrop, startDrag)
  val endBackdrop = rememberFluidSliderThumbBackdrop(backdrop, trackBackdrop, endDrag)

  val gestures = if (!enabled) {
    Modifier
  } else {
    Modifier.pointerInput(Unit) {
      val edge = FluidSliderMetrics.Edge.toPx()
      val thumbPx = FluidSliderMetrics.ThumbW.toPx()
      val grabReach = thumbPx / 2f + FluidSliderMetrics.GrabMargin.toPx()

      fun dragOf(which: Int) = if (which == RangeStart) startDrag else endDrag

      /** La maniglia [which] col dito a [rawFraction]: dove cade, cosa si sente, cosa si comunica. */
      fun report(which: Int, rawFraction: Float, uptimeMillis: Long, travel: Boolean = false, settling: Boolean = false) {
        val range = currentRange
        val stops = currentStops
        val rawValue = fluidSliderValue(rawFraction, range)
        val threshold = if ((travel || settling) && stops.size > 1) 1f else currentThreshold
        val stop = fluidSliderStopIndex(rawValue, stops, range, threshold)
        val snapped = if (stop >= 0) stops[stop].coerceIn(range.start, range.endInclusive) else rawValue
        val start = currentStart
        val end = currentEnd
        val next = if (which == RangeStart) fluidRangeClampStart(snapped, end, range, currentGap)
        else fluidRangeClampEnd(snapped, start, range, currentGap)
        val isBlocked = next != snapped && (if (which == RangeStart) snapped > next else snapped < next)
        val nextFraction = fluidSliderFraction(next, range)
        val drag = dragOf(which)
        if (travel) {
          if (currentReducedMotion) drag.snapToValue(nextFraction) else drag.animateToValue(nextFraction)
        }
        val detent = detents.step(nextFraction, FluidSliderMetrics.TickSteps, uptimeMillis)
        val landed = notches.land(if (next == snapped) stop else -1, uptimeMillis, settling)
        when {
          isBlocked && !blocked[0] -> currentHaptics.play(FluidHapticEvent.Threshold)
          detent == FluidDetent.Edge -> currentHaptics.play(FluidHapticEvent.Threshold)
          stops.isEmpty() -> if (detent == FluidDetent.Tick) currentHaptics.play(FluidHapticEvent.DragTick)
          landed -> currentHaptics.play(FluidHapticEvent.Tick)
        }
        blocked[0] = isBlocked
        val newStart = if (which == RangeStart) next else start
        val newEnd = if (which == RangeEnd) next else end
        if (newStart != start || newEnd != end) currentOnValueChange(newStart..newEnd)
      }

      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        val trackPx = (width - edge * 2f).coerceAtLeast(1f)
        fun startX(x: Float) = if (currentIsLtr) x else width - x
        val x0 = startX(down.position.x)
        val startCentre = edge + fluidSliderThumbCentre(startDrag.progress, trackPx, thumbPx)
        val endCentre = edge + fluidSliderThumbCentre(endDrag.progress, trackPx, thumbPx)
        var which = fluidRangePick(x0, startCentre, endCentre, grabReach)

        var anchorX = x0
        var claimedFromTrack = false
        var uptime = down.uptimeMillis

        if (which == RangeStart || which == RangeEnd) {
          down.consume()
        } else {
          // Due maniglie sotto il dito (sovrapposte) o la pista nuda: decide il primo movimento, o un tocco.
          val both = which == RangeBoth
          if (both) down.consume()
          var travel = Offset.Zero
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (change.isConsumed && !both) return@awaitEachGesture
            if (!change.pressed) {
              change.consume()
              if (!both) {
                // Un tocco sulla pista: ci va la maniglia piu' vicina.
                val nearest = fluidRangeNearest(x0, startCentre, endCentre, 0f)
                blocked[0] = false
                detents.restartAt(dragOf(nearest).targetValue, FluidSliderMetrics.TickSteps)
                notches.restart(-1)
                report(nearest, fluidSliderFractionAt(x0, edge, trackPx), change.uptimeMillis, travel = true)
                currentFinished?.invoke()
              }
              return@awaitEachGesture
            }
            travel += change.positionChange()
            val towardsEnd = if (currentIsLtr) travel.x else -travel.x
            when (fluidSliderClaim(travel, viewConfiguration.touchSlop)) {
              FluidSliderClaim.Slider -> {
                change.consume()
                which = if (both) {
                  if (towardsEnd >= 0f) RangeEnd else RangeStart
                } else {
                  fluidRangeNearest(x0, startCentre, endCentre, towardsEnd)
                }
                anchorX = startX(change.position.x)
                claimedFromTrack = !both
                uptime = change.uptimeMillis
                break
              }
              FluidSliderClaim.Page -> if (!both) return@awaitEachGesture
              FluidSliderClaim.Undecided -> Unit
            }
            if (both) change.consume()
          }
        }

        val drag = dragOf(which)
        var anchorFraction = if (claimedFromTrack) fluidSliderFractionAt(anchorX, edge, trackPx) else drag.targetValue
        fun fractionAt(x: Float) = anchorFraction + (startX(x) - anchorX) / trackPx

        drag.press()
        held = which
        onTop = which
        blocked[0] = false
        detents.restartAt(drag.targetValue, FluidSliderMetrics.TickSteps)
        val currentValueOf = if (which == RangeStart) currentStart else currentEnd
        notches.restart(fluidSliderStopIndex(currentValueOf, currentStops, currentRange, currentThreshold))
        var pointer = down.id
        try {
          if (claimedFromTrack) report(which, anchorFraction, uptime)
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == pointer }
              ?: event.changes.fastFirstOrNull { it.pressed }?.also {
                pointer = it.id
                anchorX = startX(it.position.x)
                anchorFraction = drag.targetValue
              }
              ?: break
            if (!change.pressed) {
              if (!change.isConsumed && currentStops.size > 1) {
                report(which, fractionAt(change.position.x), change.uptimeMillis, settling = true)
              }
              change.consume()
              break
            }
            if (change.positionChanged()) report(which, fractionAt(change.position.x), change.uptimeMillis)
            change.consume()
          }
        } finally {
          held = RangeNone
          drag.release()
          currentFinished?.invoke()
        }
      }
    }
  }

  BoxWithConstraints(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .fillMaxWidth()
      .height(FluidSliderMetrics.Height)
      .then(gestures)
      .padding(horizontal = FluidSliderMetrics.Edge),
    contentAlignment = Alignment.CenterStart,
  ) {
    val trackPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val thumbPx = with(density) { FluidSliderMetrics.ThumbW.toPx() }
    val startRise = rememberGlassDropRiseMotion(
      drag = startDrag,
      travelDp = { trackPx / density.density },
      reducedMotion = { currentReducedMotion },
      followsFinger = { held == RangeStart },
    )
    val endRise = rememberGlassDropRiseMotion(
      drag = endDrag,
      travelDp = { trackPx / density.density },
      reducedMotion = { currentReducedMotion },
      followsFinger = { held == RangeEnd },
    )

    // 1. Le tacche, come nello slider.
    if (snapValues.isNotEmpty()) {
      Box(
        Modifier
          .fillMaxSize()
          .drawBehind {
            val y = size.height / 2f + (FluidSliderMetrics.Track / 2 + FluidSliderMetrics.StopOffset).toPx()
            val radius = FluidSliderMetrics.Stop.toPx() / 2f
            val from = startDrag.progress - FluidSliderMetrics.Sync
            val to = endDrag.progress + FluidSliderMetrics.Sync
            for (stop in snapValues) {
              val f = fluidSliderFraction(stop, valueRange)
              val centre = fluidSliderThumbCentre(f, size.width, thumbPx)
              drawCircle(
                color = if (f in from..to) accentColor else stopIdleColor,
                radius = radius,
                center = Offset(if (isLtr) centre else size.width - centre, y),
              )
            }
          },
      )
    }

    // 2. La pista, registrata: il velo, e l'accento fra le due maniglie.
    Box(
      Modifier
        .fillMaxWidth()
        .height(FluidSliderMetrics.Track)
        .layerBackdrop(trackBackdrop)
        .drawBehind {
          val h = size.height
          val corner = CornerRadius(h / 2f)
          drawRoundRect(trackColor, cornerRadius = corner)
          val a = startDrag.progress.coerceIn(0f, 1f) * size.width
          val b = endDrag.progress.coerceIn(0f, 1f) * size.width
          val left = if (isLtr) min(a, b) else size.width - max(a, b)
          val w = abs(b - a)
          // Almeno una pista tonda: due maniglie che si toccano lasciano un punto d'accento, non un buco.
          val shown = max(w, h)
          drawRoundRect(accentColor, topLeft = Offset(left - (shown - w) / 2f, 0f), size = Size(shown, h), cornerRadius = corner)
        },
    )

    // 3. Le due maniglie: quella presa per ultima sopra.
    FluidSliderThumb(
      drag = startDrag,
      rise = startRise,
      backdrop = startBackdrop,
      accentColor = accentColor,
      centre = { fluidSliderThumbCentre(startDrag.progress, trackPx, thumbPx) },
      isLtr = isLtr,
      reducedMotion = { currentReducedMotion },
      modifier = Modifier.zIndex(if (onTop == RangeStart) 1f else 0f),
    )
    FluidSliderThumb(
      drag = endDrag,
      rise = endRise,
      backdrop = endBackdrop,
      accentColor = accentColor,
      centre = { fluidSliderThumbCentre(endDrag.progress, trackPx, thumbPx) },
      isLtr = isLtr,
      reducedMotion = { currentReducedMotion },
      modifier = Modifier.zIndex(if (onTop == RangeEnd) 1f else 0f),
    )

    // 4. I due nodi per TalkBack, sopra le maniglie: invisibili, senza gesti (il dito e' della riga).
    val nodeSize = FluidSliderMetrics.Height
    val nodePx = with(density) { nodeSize.toPx() }
    val minLabel = labels.decrease
    val maxLabel = labels.increase
    for (which in intArrayOf(RangeStart, RangeEnd)) {
      val drag = if (which == RangeStart) startDrag else endDrag
      val current = if (which == RangeStart) shownStart else shownEnd
      val lower = if (which == RangeStart) valueRange.start else min(valueRange.endInclusive, shownStart + gap)
      val upper = if (which == RangeStart) max(valueRange.start, shownEnd - gap) else valueRange.endInclusive
      val name = (if (which == RangeStart) startContentDescription else endContentDescription)
        ?: if (which == RangeStart) FluidRangeSliderDefaults.StartDescription else FluidRangeSliderDefaults.EndDescription
      val state = if (which == RangeStart) startStateDescription else endStateDescription
      val stepSize = fluidRangeAccessibilityStep(valueRange)
      Box(
        Modifier
          .offset {
            val centre = fluidSliderThumbCentre(drag.progress, trackPx, thumbPx)
            val x = if (isLtr) centre - nodePx / 2f else trackPx - centre - nodePx / 2f
            IntOffset(x.roundToInt(), 0)
          }
          .size(nodeSize)
          .semantics {
            contentDescription = name
            if (state != null) stateDescription = state
            progressBarRangeInfo = ProgressBarRangeInfo(current.coerceIn(lower, max(lower, upper)), lower..max(lower, upper))
            if (enabled) {
              fun move(target: Float): Boolean {
                val range = currentRange
                val snapped = fluidSliderSnap(target.coerceIn(range.start, range.endInclusive), currentStops, range, currentThreshold)
                val s = currentStart
                val e = currentEnd
                val next = if (which == RangeStart) fluidRangeClampStart(snapped, e, range, currentGap)
                else fluidRangeClampEnd(snapped, s, range, currentGap)
                val ns = if (which == RangeStart) next else s
                val ne = if (which == RangeEnd) next else e
                if (ns == s && ne == e) return false
                currentOnValueChange(ns..ne)
                currentFinished?.invoke()
                return true
              }
              setProgress { target -> move(target) }
              customActions = listOf(
                CustomAccessibilityAction(maxLabel) {
                  move(fluidRangeStepTarget(if (which == RangeStart) currentStart else currentEnd, +1, currentStops, currentRange, stepSize))
                },
                CustomAccessibilityAction(minLabel) {
                  move(fluidRangeStepTarget(if (which == RangeStart) currentStart else currentEnd, -1, currentStops, currentRange, stepSize))
                },
              )
            } else {
              disabled()
            }
          },
      )
    }
  }
}

/** I nomi di serie delle due maniglie per TalkBack. Un'app in un'altra lingua li passa sempre. */
object FluidRangeSliderDefaults {
  const val StartDescription: String = "Minimo"
  const val EndDescription: String = "Massimo"
}

// ---------------------------------------------------------------------------------------------
// I conti delle due maniglie (puri, provati)
// ---------------------------------------------------------------------------------------------

internal const val RangeNone = -1
internal const val RangeStart = 0
internal const val RangeEnd = 1
internal const val RangeBoth = 2

/**
 * L'inizio mostrato: [value] ordinato e dentro [range]. Se le due maniglie sono piu' vicine di [gap] l'inizio resta e la
 * fine si allontana; solo in fondo alla pista, dove la fine non puo', l'inizio arretra.
 */
internal fun fluidRangeStartOf(value: ClosedFloatingPointRange<Float>, range: ClosedFloatingPointRange<Float>, gap: Float): Float {
  val hi = max(range.start, range.endInclusive)
  val a = min(value.start, value.endInclusive).coerceIn(range.start, hi)
  val b = max(value.start, value.endInclusive).coerceIn(range.start, hi)
  return if (b - a >= gap || a + gap <= hi) a else max(range.start, hi - gap)
}

/** La fine mostrata, come [fluidRangeStartOf]. */
internal fun fluidRangeEndOf(value: ClosedFloatingPointRange<Float>, range: ClosedFloatingPointRange<Float>, gap: Float): Float {
  val start = fluidRangeStartOf(value, range, gap)
  val b = max(value.start, value.endInclusive).coerceIn(range.start, max(range.start, range.endInclusive))
  return min(max(range.start, range.endInclusive), max(b, start + gap))
}

/** Dove puo' andare l'inizio [candidate] con la fine a [end]: mai oltre `end − gap`, mai fuori da [range]. */
internal fun fluidRangeClampStart(candidate: Float, end: Float, range: ClosedFloatingPointRange<Float>, gap: Float): Float =
  candidate.coerceIn(range.start, max(range.start, end - gap))

/** Dove puo' andare la fine [candidate] con l'inizio a [start]: mai prima di `start + gap`, mai fuori da [range]. */
internal fun fluidRangeClampEnd(candidate: Float, start: Float, range: ClosedFloatingPointRange<Float>, gap: Float): Float =
  candidate.coerceIn(min(range.endInclusive, start + gap), range.endInclusive)

/**
 * Quale maniglia sta sotto un dito a [x] (in pixel dall'inizio della riga), con i centri a [startCentre] e [endCentre] e
 * la presa a [reach]: [RangeStart], [RangeEnd], [RangeBoth] se le prende tutte e due (sovrapposte), [RangeNone] se e'
 * la pista nuda. Fra due maniglie vicine ma non sovrapposte vince quella piu' vicina al dito.
 */
internal fun fluidRangePick(x: Float, startCentre: Float, endCentre: Float, reach: Float): Int {
  val ds = abs(x - startCentre)
  val de = abs(x - endCentre)
  val onStart = ds <= reach
  val onEnd = de <= reach
  return when {
    onStart && onEnd -> if (abs(startCentre - endCentre) < 1f) RangeBoth else if (ds < de) RangeStart else if (de < ds) RangeEnd else RangeBoth
    onStart -> RangeStart
    onEnd -> RangeEnd
    else -> RangeNone
  }
}

/**
 * La maniglia piu' vicina a [x]; a pari distanza decide il verso del movimento ([towardsEnd] positivo: la fine), e da
 * fermi la fine se il dito sta oltre, l'inizio se sta prima.
 */
internal fun fluidRangeNearest(x: Float, startCentre: Float, endCentre: Float, towardsEnd: Float): Int {
  val ds = abs(x - startCentre)
  val de = abs(x - endCentre)
  return when {
    ds < de -> RangeStart
    de < ds -> RangeEnd
    towardsEnd > 0f -> RangeEnd
    towardsEnd < 0f -> RangeStart
    x >= endCentre -> RangeEnd
    else -> RangeStart
  }
}

/** Il passo di «aumenta» e «diminuisci» senza tacche: un ventesimo dell'intervallo, come lo slider (tacche aptiche). */
internal fun fluidRangeAccessibilityStep(range: ClosedFloatingPointRange<Float>): Float =
  ((range.endInclusive - range.start) / FluidSliderMetrics.TickSteps).coerceAtLeast(0f)

/** Dove va una maniglia a [current] con un passo in [direction]: la tacca dopo, se ci sono tacche, o [step]. */
internal fun fluidRangeStepTarget(
  current: Float,
  direction: Int,
  stops: List<Float>,
  range: ClosedFloatingPointRange<Float>,
  step: Float,
): Float {
  if (stops.size > 1) {
    val sorted = stops.map { it.coerceIn(range.start, range.endInclusive) }.sorted()
    return if (direction > 0) sorted.firstOrNull { it > current + 1e-6f } ?: range.endInclusive
    else sorted.lastOrNull { it < current - 1e-6f } ?: range.start
  }
  return (current + direction * step).coerceIn(range.start, range.endInclusive)
}

