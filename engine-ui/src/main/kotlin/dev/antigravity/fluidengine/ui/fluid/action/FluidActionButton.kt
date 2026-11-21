package dev.antigravity.fluidengine.ui.fluid.action

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.LocalFluidIconSize
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarElementType
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarKind
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarKinds
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTypeSlot
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/*
 * I tasti con esito, la resa (F6B.3a): la faccia (l'icona, l'anello del lavoro, le cifre), il cambio fra le facce (lo
 * scambio sfocato di R7, o il glifo animato di F6B.3b), la scossa del fallimento, le parole per TalkBack e l'aptica.
 * Le tre case del tasto la usano allo stesso modo: [FluidActionButton] da solo, la barra dichiarata
 * (`button`/`primary`/`confirm` con `action`, attraverso [FluidToolbarActionType]) e la capsula
 * ([dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction] con `action`).
 *
 * **Anello e cifre sono contenuto**: si disegnano sopra il vetro, nel riquadro del contenuto, e non toccano il vetro né lo
 * ricatturano. L'anello che gira e l'arco che avanza si ridisegnano (fase di disegno), non ricompongono e non cambiano la
 * misura di niente (prova: `FluidActionCaptureTest` della galleria, con le sonde `glassBodyProbe` e `totalCaptures`).
 */

// ================================================================================================================
// La faccia
// ================================================================================================================

/** Come si disegna una faccia: la taglia dell'icona, l'inchiostro del posto, la tinta d'errore, il movimento ridotto. */
@Immutable
internal data class FluidActionLook(
  val iconSize: Dp,
  val ink: Color,
  val error: Color,
  val reducedMotion: Boolean,
)

/**
 * L'avanzamento come lo legge l'anello: il valore dell'app ([source]) riletto a ogni fotogramma in [value], così l'arco
 * si ridisegna anche quando l'app non scrive in uno stato di Compose. Solo il disegno lo legge: non ricompone.
 */
@Stable
internal class FluidActionProgress {
  internal val value = mutableFloatStateOf(0f)
  internal var source by mutableStateOf<(() -> Float)?>(null, referentialEqualityPolicy())

  /** L'avanzamento adesso, 0..1. Letto in un disegno, lo ridisegna quando cambia. */
  fun read(): Float {
    val stored = value.floatValue
    val live = source?.invoke() ?: return stored
    return if (live.isFinite()) live.coerceIn(0f, 1f) else stored
  }
}

/**
 * Lo stato di resa di un tasto con esito in un posto: l'avanzamento, la scossa, l'ultimo stato visto (per l'aptica).
 * Si ricorda una volta per posto ([rememberFluidActionPresentation]).
 */
@Stable
internal class FluidActionPresentation(initial: FluidActionState) {
  val progress = FluidActionProgress()

  /** La fase della scossa, 0..1; 1 a riposo (nessuno spostamento). */
  val shake = Animatable(1f)

  internal var last: FluidActionState = initial
  internal var shakes by mutableIntStateOf(0)
}

/**
 * La scossa del fallimento in frazione della taglia dell'icona, alla fase [u] (0..1): un seno smorzato che parte e
 * arriva a zero ([FluidActionProfile.ShakeAmplitude], [FluidActionProfile.ShakeHalfPeriods]). Pura.
 */
internal fun fluidActionShake(u: Float): Float {
  val t = u.coerceIn(0f, 1f)
  return FluidActionProfile.ShakeAmplitude * sin(PI.toFloat() * FluidActionProfile.ShakeHalfPeriods * t) * exp(-3f * t)
}

/**
 * Lo stato di resa di [preset] in [state] per un posto. [live] è il posto vero: suona l'aptica (un evento per momento,
 * [fluidActionHaptic]) e trema al fallimento; una copia (il glifo che viaggia fra due vetri della barra) disegna soltanto.
 */
@Composable
internal fun rememberFluidActionPresentation(
  preset: FluidActionPreset,
  state: FluidActionState,
  live: Boolean,
): FluidActionPresentation {
  val presentation = remember { FluidActionPresentation(state) }
  val working = state as? FluidActionState.Working
  val paused = working?.paused == true
  // Congela una sola lettura al bordo della pausa, anche se il produttore esterno continua a scrivere.
  val held = remember(preset.kind, paused, working?.progress != null) {
    if (paused) Snapshot.withoutReadObservation {
      val live = working?.progress?.invoke()
      if (live != null && live.isFinite()) live.coerceIn(0f, 1f) else presentation.progress.value.floatValue
    } else null
  }
  if (working?.progress != null) {
    presentation.progress.source = if (held == null) working.progress else remember(held) { { held } }
  }
  val face = fluidActionFace(preset, state)
  if (face.ring == FluidActionRing.Determinate && !paused) {
    val source = presentation.progress.source
    LaunchedEffect(presentation, source) {
      if (source == null) return@LaunchedEffect
      // L'app può scrivere l'avanzamento fuori dagli stati di Compose: lo si rilegge a ogni fotogramma, e lo legge il disegno.
      // È un ciclo senza fine finché il lavoro dura: come le animazioni infinite, perché un test d'interfaccia (dell'engine o
      // di un'app) possa ancora dire «fermo» con un tasto che lavora. Un avanzamento in uno stato di Compose (quello
      // dell'esecutore) ridisegna l'arco anche senza questo ciclo, perché il disegno lo legge.
      while (true) {
        val value = source()
        presentation.progress.value.floatValue = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        withInfiniteAnimationFrameNanos { }
      }
    }
  }
  if (live) {
    val haptics = LocalFluidHaptics.current
    val reduced = LocalFluidMotionPolicy.current.reducedMotion
    LaunchedEffect(presentation, state, preset) {
      val from = presentation.last
      presentation.last = state
      fluidActionHaptic(preset, from, state)?.let(haptics::play)
      val failed = fluidActionPhase(preset, state) == FluidActionPhase.Failed
      if (failed && fluidActionPhase(preset, from) != FluidActionPhase.Failed && !reduced) presentation.shakes++
    }
    // La scossa ha il suo effetto: un cambio di stato a metà non la ferma a mezz'aria.
    val swapMs = GlassContentDefaults.DurationMs * LocalFluidMotionPolicy.current.durationScale
    LaunchedEffect(presentation, presentation.shakes) {
      if (presentation.shakes == 0) return@LaunchedEffect
      // Prima la faccia del fallimento entra sfocata (R7); la scossa parte quando la si legge.
      presentation.shake.snapTo(1f)
      delay((swapMs * FluidActionProfile.ShakeDelayFraction).toLong().coerceAtLeast(0L))
      presentation.shake.snapTo(0f)
      presentation.shake.animateTo(1f, tween(FluidActionProfile.ShakeMs.toInt(), easing = LinearEasing))
    }
  }
  return presentation
}

/**
 * Il disegno di una faccia ferma: l'icona (piccola dentro l'anello), l'anello del lavoro, le cifre. Il riquadro è quello
 * dell'anello, [FluidActionProfile.RingDiameter] volte la taglia dell'icona, per tutte le facce: niente cambia misura fra
 * uno stato e l'altro.
 */
@Composable
internal fun FluidActionFaceDrawing(
  face: FluidActionFace,
  progress: FluidActionProgress,
  look: FluidActionLook,
  ringClosure: (() -> Float)? = null,
) {
  val box = look.iconSize * FluidActionProfile.RingDiameter
  val color = if (face.ink == FluidActionInk.Error) look.error else look.ink
  val spin: State<Float>? = if (face.ring == FluidActionRing.Indeterminate && !look.reducedMotion) {
    val angle = remember { mutableFloatStateOf(0f) }
    val scale = LocalFluidMotionPolicy.current.durationScale.coerceAtLeast(0.01f)
    LaunchedEffect(face.paused, scale) {
      if (face.paused) return@LaunchedEffect
      var previous = withInfiniteAnimationFrameNanos { it }
      while (true) {
        val now = withInfiniteAnimationFrameNanos { it }
        val frameMs = (now - previous) / 1_000_000f
        angle.floatValue = (angle.floatValue + 360f * frameMs / (FluidActionProfile.SpinnerTurnMs * scale)) % 360f
        previous = now
      }
    }
    angle
  } else null
  val digits = if (face.digits) fluidActionDigits(look.iconSize, box) else null
  Box(
    Modifier.requiredSize(box).drawBehind {
      if (face.ring == FluidActionRing.None) return@drawBehind
      val stroke = max(FluidActionProfile.RingMinStrokeDp.dp.toPx(), look.iconSize.toPx() * FluidActionProfile.RingStroke)
      val diameter = size.minDimension - stroke
      val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
      val arc = Size(diameter, diameter)
      val line = Stroke(width = stroke, cap = StrokeCap.Round)
      drawArc(color.copy(alpha = color.alpha * FluidActionProfile.RingTrackAlpha), 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
      when (face.ring) {
        FluidActionRing.Indeterminate -> if (spin != null) {
          val arcColor = if (face.paused) color.copy(alpha = color.alpha * FluidActionProfile.ReducedRingAlpha) else color
          val sweep = FluidActionProfile.SpinnerSweep * 360f
          drawArc(arcColor, spin.value - 90f, sweep + (360f - sweep) * (ringClosure?.invoke() ?: 0f), false, topLeft, arc, style = line)
        } else {
          // Movimento ridotto: niente che gira; l'anello intero, più pieno della pista, dice «sto lavorando».
          drawArc(color.copy(alpha = color.alpha * FluidActionProfile.ReducedRingAlpha), 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
        }
        FluidActionRing.Determinate -> {
          // Da ore 12 in senso orario (F6B.0 §6, Variable Draw: alta sull'ordine e sul verso).
          val fraction = progress.read()
          val sweep = 360f * (fraction + (1f - fraction) * (ringClosure?.invoke() ?: 0f))
          val arcColor = if (face.paused) color.copy(alpha = color.alpha * FluidActionProfile.ReducedRingAlpha) else color
          if (sweep > 0f) drawArc(arcColor, -90f, sweep, false, topLeft, arc, style = line)
        }
        FluidActionRing.None -> Unit
      }
      if (digits != null) {
        val text = digits.measurer.measure("${fluidActionPercent(progress.read())}%", digits.style)
        drawText(text, color, Offset((size.width - text.size.width) / 2f, (size.height - text.size.height) / 2f))
      }
    },
    contentAlignment = Alignment.Center,
  ) {
    if (face.icon != null) FluidIcon(face.icon, contentDescription = null, tint = color, size = look.iconSize * face.iconScale)
  }
}

/** Le cifre nell'anello: lo stile tabulare e la misura che fa stare «100%», la più larga, nel diametro interno. */
private class FluidActionDigits(val measurer: androidx.compose.ui.text.TextMeasurer, val style: TextStyle)

@Composable
private fun fluidActionDigits(iconSize: Dp, box: Dp): FluidActionDigits {
  val measurer = rememberTextMeasurer()
  val base = MaterialTheme.typography.labelSmall
  val density = LocalDensity.current
  return remember(measurer, base, iconSize, box, density) {
    // Cifre tabulari: «18%» e «88%» sono larghe uguali, e la larghezza è quella del caso più largo, «100%».
    val probe = base.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.sp)
    val stroke = max(FluidActionProfile.RingMinStrokeDp, iconSize.value * FluidActionProfile.RingStroke)
    val inner = with(density) { (box.value - 2f * stroke).dp.toPx() } * FluidActionProfile.DigitsFill
    val widest = measurer.measure("100%", probe).size.width.toFloat().coerceAtLeast(1f)
    val scale = (inner / widest).coerceIn(0.5f, 1.6f)
    FluidActionDigits(measurer, probe.copy(fontSize = (10f * scale).sp, lineHeight = (10f * scale * 1.1f).sp))
  }
}

// ================================================================================================================
// Il cambio fra le facce: l'innesto dei glifi di F6B.3b
// ================================================================================================================

/**
 * Come un preset passa da una faccia all'altra. Di serie lo scambio sfocato di R7 ([FluidActionSwapGlyph]); F6B.3b
 * innesta i glifi animati veri dei cinque preset in **un punto solo**: [FluidActionGlyphs.of], nel suo file
 * (`FluidActionGlyphs.kt`). Un glifo riceve la faccia d'arrivo e ne disegna il passaggio;
 * deve tenere il riquadro di [FluidActionFaceDrawing] (la misura non cambia fra gli stati) e, con
 * [FluidActionLook.reducedMotion], cambiare faccia senza moto.
 */
internal interface FluidActionGlyph {
  @Composable
  fun Content(face: FluidActionFace, progress: FluidActionProgress, look: FluidActionLook, modifier: Modifier)
}

/** Lo scambio sfocato di R7 (F3.4): chi esce si sfoca e svanisce, chi entra arriva sfocato; mai due facce leggibili insieme. */
internal object FluidActionSwapGlyph : FluidActionGlyph {
  @Composable
  override fun Content(face: FluidActionFace, progress: FluidActionProgress, look: FluidActionLook, modifier: Modifier) {
    FluidGlassContentTransition(targetState = face, modifier = modifier, blurReference = look.iconSize) { shown ->
      FluidActionFaceDrawing(shown, progress, look)
    }
  }
}

/** Il glifo di [kind]: quello di F6B.3b se c'è, altrimenti lo scambio sfocato. */
internal fun fluidActionGlyph(kind: FluidActionKind): FluidActionGlyph = FluidActionGlyphs.of(kind) ?: FluidActionSwapGlyph

/**
 * Il contenuto di un tasto con esito in un posto: il glifo del preset sulla faccia di [state], con la scossa del
 * fallimento. La scossa sposta **solo il contenuto**: il vetro resta fermo e i vicini non saltano.
 */
@Composable
internal fun FluidActionGlyphContent(
  preset: FluidActionPreset,
  state: FluidActionState,
  presentation: FluidActionPresentation,
  look: FluidActionLook,
  modifier: Modifier = Modifier,
) {
  val face = fluidActionFace(preset, state)
  val glyph = fluidActionGlyph(preset.kind)
  val iconPx = with(LocalDensity.current) { look.iconSize.toPx() }
  glyph.Content(face, presentation.progress, look, modifier.graphicsLayer {
    translationX = if (look.reducedMotion) 0f else fluidActionShake(presentation.shake.value) * iconPx
  })
}

// ================================================================================================================
// Le parole
// ================================================================================================================

/** L'avanzamento detto da TalkBack, aggiornato solo quando cambia il passo (ricompone quattro volte, non a ogni punto). */
@Composable
internal fun rememberFluidActionSpokenProgress(
  preset: FluidActionPreset,
  state: FluidActionState,
  presentation: FluidActionPresentation,
  labels: FluidActionLabels,
): String? {
  val determinate = fluidActionFace(preset, state).ring == FluidActionRing.Determinate
  val paused = (state as? FluidActionState.Working)?.paused == true
  val spoken by remember(presentation, labels, determinate, paused) {
    derivedStateOf { if (determinate) fluidActionSpokenProgress(presentation.progress.read(), labels, paused) else null }
  }
  return spoken
}

/**
 * Le parole di un tasto con esito per TalkBack: il nome è ciò che dice adesso («Invia», «Invio in corso…», «Inviato»,
 * «Invio non riuscito»), in un'area viva gentile fuori dal riposo, così l'esito si sente anche senza il fuoco sul tasto;
 * l'avanzamento come stato («50%»); spento mentre lavora.
 */
internal fun fluidActionDescribe(
  receiver: SemanticsPropertyReceiver,
  preset: FluidActionPreset,
  state: FluidActionState,
  labels: FluidActionLabels,
  name: String?,
  spokenProgress: String?,
) {
  with(receiver) {
    contentDescription = fluidActionSpoken(preset, state, labels, name)
    if (state != FluidActionState.Idle) liveRegion = LiveRegionMode.Polite
    if (spokenProgress != null) stateDescription = spokenProgress
    if (!state.acceptsTap) disabled()
  }
}

// ================================================================================================================
// Il tasto da solo
// ================================================================================================================

/**
 * Un tasto con esito (piano «iOS in Android», F6B.3a): un tasto di vetro che mostra il lavoro (un anello sottile attorno
 * all'icona, dentro il vetro) e l'esito (la spunta, l'icona del fallimento nella tinta d'errore), con lo scambio sfocato
 * di R7 fra uno stato e l'altro. È `UIButton.Configuration.showsActivityIndicator` più `symbolEffect(.replace)` di
 * SwiftUI, con l'esito che UIKit lascia all'app.
 *
 * ```
 * val save = rememberFluidActionRunner(FluidActionPreset.Save)
 * FluidActionButton(FluidActionPreset.Save, save.state, onClick = { save.run { repository.save(note) } })
 * ```
 *
 * - **Lo stato è dell'app** ([state]): il tasto non ha logica, e la rotazione non lo azzera.
 * - **Mentre lavora è spento**: non risponde al tocco (né al doppio tocco di TalkBack) e non cresce sotto il dito; non si
 *   vela, perché l'anello è il suo riscontro.
 * - **La misura è fissa**: il vetro è quello del tasto ([size]) in ogni stato; anello e cifre ci stanno dentro.
 * - **L'esito negativo** fa tremare solo il contenuto, nella tinta d'errore; il vetro resta fermo.
 * - **TalkBack** dice «Invio in corso…», «Inviato», «Invio non riuscito» con un'area viva. Con il movimento ridotto
 *   l'esito è un cambio d'icona senza moto.
 * - **Aptica**: un evento per momento (riuscito, non riuscito, annullato; il tocco lo suona il vetro che cresce).
 * - **La pressione** è quella dei tasti di vetro (F6, crescita ×1,35).
 *
 * @param label una scritta accanto all'icona («Salva»), che non cambia con lo stato (come il titolo di un `UIButton`):
 *   il tasto diventa una capsula, e l'anello sta attorno all'icona davanti alla scritta. Null: un tondo di vetro col solo
 *   simbolo, che per TalkBack si chiama col nome del preset.
 * @param enabled falso: spento e velato come ogni tasto, in ogni stato.
 * @param style lo stile del vetro ([FluidGlassButtonStyle]); su [FluidGlassButtonStyle.Prominent] il fallimento resta
 *   nell'inchiostro della tinta, perché il rosso sull'accento non si legge.
 * @param size la taglia del vetro.
 */
@Composable
fun FluidActionButton(
  preset: FluidActionPreset,
  state: FluidActionState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  enabled: Boolean = true,
  style: FluidGlassButtonStyle = LocalFluidGlassButtonStyle.current,
  size: FluidGlassSize = LocalFluidGlassSize.current,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
) {
  val presentation = rememberFluidActionPresentation(preset, state, live = true)
  val labels = LocalFluidActionLabels.current
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val error = MaterialTheme.colorScheme.error
  val busy = !state.acceptsTap
  val spokenProgress = rememberFluidActionSpokenProgress(preset, state, presentation, labels)
  // Un doppio tocco di TalkBack su un tasto che lavora non fa niente: il tasto è spento.
  val tap = { if (state.acceptsTap) onClick() }
  val semantics = Modifier.clearAndSetSemantics {
    role = Role.Button
    if (enabled && !busy) this.onClick(action = { tap(); true })
    fluidActionDescribe(this, preset, state, labels, label, spokenProgress)
    if (!enabled) disabled()
  }
  Box(modifier, propagateMinConstraints = true) {
    if (label == null) {
      FluidGlassIconButton(
        onClick = tap,
        modifier = semantics,
        backdrop = backdrop,
        enabled = enabled,
        size = size,
        haptic = if (busy) null else FluidHapticEvent.Tap,
        style = style,
      ) {
        val ink = LocalContentColor.current
        FluidActionGlyphContent(preset, state, presentation,
          FluidActionLook(LocalFluidIconSize.current, ink, if (style.isProminent) ink else error, reduced))
      }
    } else {
      FluidGlassButton(
        text = label,
        onClick = tap,
        modifier = semantics,
        backdrop = backdrop,
        enabled = enabled,
        size = size,
        haptic = if (busy) null else FluidHapticEvent.Tap,
        maxLines = 1,
        style = style,
        leading = {
          val ink = LocalContentColor.current
          FluidActionGlyphContent(preset, state, presentation,
            FluidActionLook(LocalFluidIconSize.current, ink, if (style.isProminent) ink else error, reduced))
        },
      )
    }
    // Mentre lavora il tasto non cresce sotto il dito e non risponde: un velo senza disegno prende il tocco sopra di lui.
    if (busy && enabled) {
      Box(Modifier.matchParentSize().pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
      })
    }
  }
}

// ================================================================================================================
// Nella barra dichiarata: il primo tipo registrato
// ================================================================================================================

/** Il lato puro del tasto con esito nella barra: il preset è l'identità, la faccia è lo stato (senza l'avanzamento). */
internal data class FluidToolbarActionKind(val preset: FluidActionKind, val face: FluidActionFace) : FluidToolbarKind {
  override val type: String get() = FluidToolbarKinds.Action
  override val identity: Any get() = preset
  override val state: Any get() = face
}

/**
 * Il tasto con esito come tipo della barra dichiarata ([FluidToolbarElementType], il punto unico di registrazione): il
 * modello vede solo [kind]; la resa è [FluidActionGlyphContent] nel posto, le parole [fluidActionDescribe].
 */
internal class FluidToolbarActionType(val preset: FluidActionPreset, val state: FluidActionState) : FluidToolbarElementType() {
  override val kind: FluidToolbarKind = FluidToolbarActionKind(preset.kind, fluidActionFace(preset, state))

  override val acceptsTap: Boolean get() = state.acceptsTap

  @Composable
  override fun rememberSlot(live: Boolean): FluidToolbarTypeSlot {
    val presentation = rememberFluidActionPresentation(preset, state, live)
    val labels = LocalFluidActionLabels.current
    val reduced = LocalFluidMotionPolicy.current.reducedMotion
    val error = MaterialTheme.colorScheme.error
    val spokenProgress = rememberFluidActionSpokenProgress(preset, state, presentation, labels)
    return object : FluidToolbarTypeSlot {
      @Composable
      override fun Face(color: Color, iconSize: Dp, onTint: Boolean) {
        FluidActionGlyphContent(preset, state, presentation,
          FluidActionLook(iconSize, color, if (onTint) color else error, reduced))
      }

      override fun describe(receiver: SemanticsPropertyReceiver, name: String?) {
        fluidActionDescribe(receiver, preset, state, labels, name, spokenProgress)
      }
    }
  }
}

/** Il tipo della barra per un tasto con [action], o null per un tasto di sempre. */
internal fun fluidToolbarActionType(action: FluidActionPreset?, state: FluidActionState): FluidToolbarElementType? =
  action?.let { FluidToolbarActionType(it, state) }
