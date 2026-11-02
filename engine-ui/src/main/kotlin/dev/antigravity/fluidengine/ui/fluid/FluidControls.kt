package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropFaces
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropLens
import dev.antigravity.fluidengine.ui.glass.interaction.glassDrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How much a control's colour says about it.
 *
 * The palette gives the interface exactly one accent, so the weight of a button is carried by how
 * much of that accent it is allowed to use, not by a different colour per button.
 */
enum class FluidButtonStyle {
  /** The one action the screen is about. Solid accent. */
  Filled,

  /** A secondary action that still deserves a surface. Accent at low opacity. */
  Tinted,

  /** Everything else. Accent text, no background — iOS uses this far more than the other two. */
  Plain,

  /** Deletes, revokes, logs out. Red, and only ever one per screen. */
  Destructive,
}

enum class FluidButtonSize { Large, Medium, Small }

object FluidButtonDefaults {
  fun height(size: FluidButtonSize): Dp = when (size) {
    FluidButtonSize.Large -> 50.dp
    FluidButtonSize.Medium -> 42.dp
    FluidButtonSize.Small -> 32.dp
  }

  fun horizontalPadding(size: FluidButtonSize): Dp = when (size) {
    FluidButtonSize.Large -> 22.dp
    FluidButtonSize.Medium -> 18.dp
    FluidButtonSize.Small -> 14.dp
  }

  fun shape(size: FluidButtonSize): Shape = when (size) {
    FluidButtonSize.Large -> ContinuousCornerShape(14.dp)
    FluidButtonSize.Medium -> ContinuousCornerShape(12.dp)
    FluidButtonSize.Small -> FluidCapsuleShape
  }
}

/**
 * A button.
 *
 * Three things separate this from the Material one, and all three are things a person notices
 * without being able to name them: it answers the finger with its whole body instead of rippling,
 * its corners are continuous, and a disabled button fades rather than turning grey — the greyed-out
 * disabled state is a Material signature, and it also destroys the colour relationship the rest of
 * the screen is built on.
 *
 * Since 2.11.0 the answer is [FluidPressFeedback.Expand]: the button grows under the finger, leans
 * toward it and stretches along a drag. Before, it shrank to 0.974 — and only its label did, because
 * the press sat *inside* the clip and the background, so the container never moved at all. The press
 * is now outside both, and the container is what grows.
 *
 * Since 3.0 it is LibrePods' button, and it behaves like it: pressed at the touch, never taking the
 * gesture from a page that scrolls, three vibrations per tap. It grows by 10%, with the growth of
 * its long side capped at 16 dp: a button that fills the width ([fillWidth]) grows by about 5%.
 * See [fluidPressable] and [FluidPressFeedback.maxScale].
 */
@Composable
fun FluidButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  style: FluidButtonStyle = FluidButtonStyle.Filled,
  size: FluidButtonSize = FluidButtonSize.Medium,
  enabled: Boolean = true,
  loading: Boolean = false,
  fillWidth: Boolean = false,
  leading: (@Composable () -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val container = when (style) {
    FluidButtonStyle.Filled -> scheme.primary
    FluidButtonStyle.Tinted -> scheme.primaryContainer
    FluidButtonStyle.Destructive -> scheme.errorContainer
    FluidButtonStyle.Plain -> Color.Transparent
  }
  val content = when (style) {
    FluidButtonStyle.Filled -> scheme.onPrimary
    FluidButtonStyle.Tinted -> scheme.onPrimaryContainer
    FluidButtonStyle.Destructive -> scheme.onErrorContainer
    FluidButtonStyle.Plain -> scheme.primary
  }
  val shape = FluidButtonDefaults.shape(size)

  Box(
    modifier = modifier
      .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
      .alpha(if (enabled) 1f else 0.4f)
      .fluidPressable(
        onClick = onClick,
        enabled = enabled && !loading,
        role = Role.Button,
        feedback = FluidPressFeedback.Expand,
      )
      .clip(shape)
      .background(container)
      .height(FluidButtonDefaults.height(size))
      .padding(horizontal = FluidButtonDefaults.horizontalPadding(size)),
    contentAlignment = Alignment.Center,
  ) {
    val labelStyle = when (size) {
      FluidButtonSize.Large -> MaterialTheme.typography.bodyLarge
      FluidButtonSize.Medium -> MaterialTheme.typography.bodyLarge
      FluidButtonSize.Small -> MaterialTheme.typography.bodyMedium
    }.copy(fontWeight = FontWeight.SemiBold)

    CompositionLocalProvider(LocalContentColor provides content) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // The spinner takes the icon's place rather than replacing the label, so the button keeps
        // its width and the row it sits in does not reflow the moment work starts.
        if (loading) {
          Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            FluidSpinner(size = 16.dp, color = content)
          }
        } else if (leading != null) {
          Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) { leading() }
        }
        Text(text = text, style = labelStyle, textAlign = TextAlign.Center)
      }
    }
  }
}

/**
 * A segmented control.
 *
 * A track, the labels printed on it, and a **drop** that rides them (3.0, fase 9). The arrangement of
 * the labels is the one [FluidTabBar] used and the floating bar's puck still uses, stood down to 32 dp:
 *
 *  1. **The track**, a static pane (la lastra: `GlassRole.Content`, the material of a grouped list)
 *     wherever the screen has something to look through, and a plain recessed well where it has not.
 *     The labels are printed on it in the resting colour and recorded into their own layer.
 *  2. **A second copy of the labels, invisible.** Drawn at zero alpha, tinted with the accent, and
 *     recorded. Nobody ever sees it directly.
 *  3. **The drop** ([glassDrop]: Kyant's thumb, the same one `FluidSlider` and `FluidSwitch` hold).
 *     At rest it is *full* — white on a light surface, iOS's grey on a dark one — and the selected
 *     label is drawn again on top of it, crisp, because a full face covers what is under it. Under a
 *     finger the face fades and it becomes a lens: it refracts the track *and* the invisible accent
 *     copy, so wherever it sits the label underneath appears through it, magnified, and in the accent
 *     colour.
 *
 * So the selected segment is not painted a different colour: while it is held it is *seen through
 * glass*. That is the difference between a control that looks like one physical object with a moving
 * part and a row of buttons that take turns lighting up, and it is worth the extra recordings a 32 dp
 * control has to pay for it.
 *
 * The drop can also be dragged between segments, with a tick for each segment it crosses; letting go
 * settles it on whichever it is nearest. A finger that lands on another segment and slides sideways
 * gets the drop too (3.0, "come iOS"): the drop comes under it, follows it with the same ticks, and
 * letting go chooses where it is; a finger that lands there and lifts without sliding is a tap. Below API 31, or on a screen with nothing to look through,
 * the track is the plain well it always was and the drop is a solid pill.
 */
@Composable
fun <T> FluidSegmentedControl(
  options: List<T>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  /**
   * What a segment shows, when a word is not it.
   *
   * [label] stays required even here, and is not optional politeness: with a glyph in the segment it
   * is the only thing a screen reader has to go on, so it becomes the description instead of the
   * text. Note that this is composed **three times** — once for the track, once for the invisible
   * accent copy the lens reads, and once on top of the resting drop — so it has to be a picture of a
   * segment and not a place to start work.
   */
  content: (@Composable (option: T, selected: Boolean) -> Unit)? = null,
  label: (T) -> String,
) {
  if (options.isEmpty()) return
  val scheme = MaterialTheme.colorScheme
  val accent = scheme.primary
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  // Da destra a sinistra i segmenti si dispongono da soli (il layout specchia), ma la goccia si muove con
  // una traslazione di layer e col dito: non la specchia nessuno, e i due versi li deve dire lei.
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val scope = rememberCoroutineScope()
  val haptics = LocalFluidHaptics.current
  val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
  val dropFace = GlassDropFaces.forSurface(GlassDefaults.isDarkSurface())
  val lensAvailable = isRenderEffectSupported()

  val barGlass = rememberGlassBackdrop(blurRadius = 0.dp)
  val labelsGlass = rememberGlassBackdrop(blurRadius = 0.dp)
  val pillBackdrop = rememberCombinedGlassBackdrop(barGlass, labelsGlass)

  val shape = ContinuousCornerShape(9.dp)
  val pillShape = ContinuousCornerShape(7.dp)

  // La pista e' una lastra dove c'e' qualcosa da attraversare (il canvas della pagina, il foglio, la
  // barra), come un gruppo di lista: materiale e ottica del ruolo `Content`, mai numeri suoi. Dove
  // non c'e' — una pagina piatta, sotto API 31 — resta il pozzetto di sempre, qui sotto. Il pozzetto
  // sta in ogni caso: la goccia bianca su una lastra chiara ha bisogno di una pista piu' scura per
  // staccarsi, ed e' la velatura che l'occhio legge come "incassata".
  val trackBackdrop = fluidControlGlassBackdrop()
  val trackGlass = if (trackBackdrop != null) {
    val stacked = LocalFluidCanvasIsGlass.current
    Modifier.glassSurface(
      state = trackBackdrop,
      tint = if (stacked) GlassDefaults.stackedContentTint() else GlassDefaults.contentTint(),
      shape = shape,
      role = GlassRole.Content,
      optics = if (stacked) GlassDefaults.stackedContentOptics() else GlassDefaults.optics(GlassRole.Content),
    )
  } else {
    Modifier
  }

  BoxWithConstraints(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .fillMaxWidth()
      .height(SegmentHeight),
  ) {
    val inset = SegmentPillInset
    val segmentWidth = with(density) {
      (constraints.maxWidth.toFloat() - inset.toPx() * 2f) / options.size
    }
    var currentIndex by remember { mutableStateOf(selectedIndex) }
    // Letti adesso, non catturati allora: l'animazione sopravvive alle ricomposizioni e le sue
    // lambda vivono piu' a lungo di qualunque valore abbiano chiuso dentro. Vedi la nota gemella
    // in FluidFoldingTabBar.
    val currentOptions by rememberUpdatedState(options)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentHaptics by rememberUpdatedState(haptics)
    // Dove sta il dito sulla pista, in segmenti, tenuto qui e non riletto dalla molla: l'animazione
    // aggiorna il suo bersaglio in una coroutine, quindi due eventi nello stesso fotogramma leggevano
    // lo stesso valore, e un segmento attraversato dentro un solo evento non faceva il suo tick.
    val dragAt = remember { FloatArray(1) }

    val pill = remember(scope, options.size, segmentWidth, rtl) {
      GlassDragAnimation(
        animationScope = scope,
        initialValue = selectedIndex.toFloat(),
        valueRange = 0f..(options.size - 1).toFloat(),
        visibilityThreshold = 0.001f,
        initialScale = 1f,
        pressedScale = if (reducedMotion) 1f else SegmentDropPressedScale,
        onDragStarted = { dragAt[0] = targetValue },
        onDragStopped = {
          // Dove il dito ha lasciato la goccia, e non dove la molla sta ancora andando: il bersaglio
          // della molla segue in una coroutine, e un rilascio nello stesso fotogramma dell'ultimo
          // movimento leggeva il segmento di prima.
          val target = dragAt[0].roundToInt().coerceIn(0, currentOptions.size - 1)
          animateToValue(target.toFloat())
          if (target != currentIndex) {
            currentIndex = target
            currentOptions.getOrNull(target)?.let { currentOnSelect(it) }
          }
        },
        onDrag = { _, dragAmount ->
          if (segmentWidth <= 0f || !currentEnabled) return@GlassDragAnimation
          val before = dragAt[0]
          val after = (before + segmentedDragDelta(dragAmount.x, segmentWidth, rtl))
            .coerceIn(0f, (currentOptions.size - 1).toFloat())
          dragAt[0] = after
          updateValue(after)
          // Un tick per segmento attraversato, che e' l'unico momento di un trascinamento che vuol
          // dire qualcosa: la goccia e' continua, e cio' che sceglie no. Letto sul valore arrotondato
          // e non su una soglia nostra, cosi' il dito sente nell'istante in cui la goccia si poserebbe
          // li' se la si lasciasse.
          if (after.roundToInt() != before.roundToInt()) currentHaptics.play(FluidHapticEvent.Tick)
        },
      )
    }

    // Il dito che scende su un segmento NON scelto e scivola di lato (3.0, scelta di Alessio: "come
    // iOS"): passato lo slop orizzontale la goccia gli viene sotto e lo segue, con i tick della goccia, e
    // al rilascio sceglie dove si trova — le stesse funzioni della goccia trascinata, chiamate da qui.
    // Un dito fermo resta il click del segmento; uno che scende sulla goccia e' gia' suo; uno che va in
    // verticale e' della pagina. Dal fotogramma in cui la goccia prende il dito ne consuma i cambi gia'
    // nell'Initial: il segmento sotto smette di premersi e non clicca, la pagina non scorre.
    val slideFromSegment = Modifier.pointerInput(pill, options.size, segmentWidth, rtl) {
      val insetPx = inset.toPx()
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!currentEnabled || segmentWidth <= 0f) return@awaitEachGesture
        val count = currentOptions.size
        val landed = segmentedFingerValue(down.position.x, size.width.toFloat(), insetPx, segmentWidth, rtl)
        if (segmentedTouchOnDrop(landed, pill.value)) return@awaitEachGesture
        val slid = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
          ?: return@awaitEachGesture
        val last = (count - 1).toFloat()
        val from = pill.value
        val begin = segmentedFingerValue(slid.position.x, size.width.toFloat(), insetPx, segmentWidth, rtl)
          .coerceIn(0f, last)
        dragAt[0] = begin
        pill.press()
        pill.updateValue(begin)
        if (begin.roundToInt() != from.roundToInt()) currentHaptics.play(FluidHapticEvent.Tick)
        val id = slid.id
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val change = event.changes.fastFirstOrNull { it.id == id } ?: break
          val dx = change.positionChange().x
          change.consume()
          if (!change.pressed) break
          if (dx != 0f) pill.onDrag(pill, IntSize.Zero, Offset(dx, 0f))
        }
        pill.onDragStopped(pill)
        pill.release()
      }
    }

    // Selection also arrives from outside — a restored state, a filter reset. The pill follows it
    // without reporting anything back: whoever changed it already knows.
    LaunchedEffect(selectedIndex, pill) {
      if (currentIndex != selectedIndex) {
        currentIndex = selectedIndex
        if (reducedMotion) pill.snapToValue(selectedIndex.toFloat())
        else pill.animateToValue(selectedIndex.toFloat())
      }
    }

    // Un segmento com'e': l'etichetta, o la figura che l'app ha dato. Chiamata dalla pista, dalla
    // copia invisibile e dalla goccia a riposo, e deve disegnare sempre la stessa cosa.
    val face: @Composable (T, Boolean) -> Unit = { option, isSelected ->
      if (content != null) {
        content(option, isSelected)
      } else {
        Text(
          text = label(option),
          style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
          ),
          // Flat, in all the copies. The accent copy is tinted wholesale by a colour filter, and a
          // per-segment colour animation underneath it would only fight the filter.
          color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
          maxLines = 1,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 6.dp),
        )
      }
    }

    val segments: @Composable RowScope.() -> Unit = {
      options.forEachIndexed { index, option ->
        val isSelected = index == currentIndex
        Box(
          modifier = Modifier
            .weight(1f)
            .height(SegmentHeight)
            .semantics {
              this.role = Role.Tab
              this.selected = isSelected
              if (content != null) this.contentDescription = label(option)
            }
            .fluidPressable(
              onClick = {
                if (isSelected) return@fluidPressable
                currentIndex = index
                if (reducedMotion) pill.snapToValue(index.toFloat())
                else pill.animateToValue(index.toFloat())
                onSelect(option)
              },
              enabled = enabled,
              pressedScale = 0.96f,
              focusShape = FluidCapsuleShape,
            ),
          contentAlignment = Alignment.Center,
        ) {
          face(option, isSelected)
        }
      }
    }

    // Quale segmento sta sotto la goccia in questo istante: la goccia a riposo ha la faccia piena e
    // copre l'etichetta che le sta sotto, quindi quella la ridisegna sopra. Derivato, cosi' si
    // ricompone quando la goccia entra in un segmento nuovo e non a ogni fotogramma.
    val dropIndex by remember(pill, options.size) {
      derivedStateOf { pill.value.roundToInt().coerceIn(0, options.size - 1) }
    }

    // Il contenitore: la lastra della pista, e dentro tutto il resto. Il vetro che sta dentro un
    // altro vetro ne campiona il materiale da solo (la goccia sfoca la sfocatura della pista).
    Box(modifier = Modifier.fillMaxSize().then(trackGlass).then(slideFromSegment)) {
      // 1. The track, with the labels printed on it.
      Row(
        modifier = Modifier
          .fillMaxSize()
          .glassBackdropSource(barGlass)
          .background(scheme.onSurface.copy(alpha = 0.07f), shape)
          .padding(horizontal = inset)
          .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        content = segments,
      )

      // 2. The invisible accent copy the lens reads.
      Row(
        modifier = Modifier
          .clearAndSetSemantics {}
          .alpha(0f)
          .glassBackdropSource(labelsGlass)
          .fillMaxSize()
          .padding(horizontal = inset)
          .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
        verticalAlignment = Alignment.CenterVertically,
        content = segments,
      )

      // 3. The drop.
      Box(
        modifier = Modifier
          .padding(inset)
          // The slide lives *here*, ahead of the gesture modifier, exactly as in FluidTabBar — not in
          // the glass surface's layerBlock below. A layer applies only to what follows it in the
          // chain, and the gesture surface has to follow: with the translation inside the glass
          // layer, the pill was *seen* on the selected segment while its touch target stayed parked
          // over the first one. Tapping the first segment then fed the pill's drag detector instead
          // of the segment's click — a zero-length drag that reselects — so once the pill left the
          // first slot, that segment went dead and the pill itself could no longer be grabbed.
          .graphicsLayer { translationX = segmentedDropTranslation(pill.value, segmentWidth, rtl) }
          .then(pill.modifier)
          .then(
            if (lensAvailable) {
              // La goccia di Kyant, coi numeri del pomello dello slider (lente 10/14 dp, sfocatura
              // 8 dp che scende a zero): bianca a riposo (grigia sullo scuro), lente sotto il dito.
              // La scala e lo squash stanno nel suo `layerBlock`, mai in un layer sopra: il
              // renderer inverte quella trasformazione quando campiona, e la lente ha in mano le
              // etichette che ci sono davvero dietro.
              Modifier.glassDrop(
                backdrop = pillBackdrop.backdrop,
                drag = pill,
                lensSize = GlassDropLens.Slider,
                face = dropFace,
                reducedMotion = { reducedMotion },
                shape = pillShape,
              )
            } else {
              // Senza RenderEffect non c'e' una lente da guardare attraverso: la pastiglia piena di
              // sempre, che cresce sotto il dito.
              Modifier
                .graphicsLayer {
                  scaleX = pill.scaleX
                  scaleY = pill.scaleY
                }
                .clip(pillShape)
                .background(dropFace)
            },
          )
          .fillMaxHeight()
          .width(with(density) { segmentWidth.toDp() }),
      )

      // 4. The selected label, again, on top of the resting drop.
      //
      // Decorativa: il segmento sotto ha il tocco e la semantica, e questa non deve portarsi via
      // nessuno dei due (non ha modificatori di puntatore, quindi il tocco la attraversa). Segue la
      // traslazione e la crescita della goccia, e sfuma tre volte piu' in fretta della pressione: a
      // un terzo del gesto non c'e' piu', e resta solo la copia che la lente piega.
      Box(
        modifier = Modifier
          .clearAndSetSemantics {}
          .padding(inset)
          .graphicsLayer {
            translationX = segmentedDropTranslation(pill.value, segmentWidth, rtl)
            scaleX = pill.scaleX
            scaleY = pill.scaleY
            alpha = (1f - pill.pressProgress * 3f).coerceIn(0f, 1f)
          }
          .fillMaxHeight()
          .width(with(density) { segmentWidth.toDp() }),
        contentAlignment = Alignment.Center,
      ) {
        currentOptions.getOrNull(dropIndex)?.let { option -> face(option, true) }
      }
    }
  }
}

/**
 * Il verso in cui cresce l'indice di un segmento sullo schermo: +1 da sinistra a destra, −1 da destra a
 * sinistra. Pura, perche' e' un errore che non si vede finche' qualcuno non apre l'app in arabo.
 */
internal fun segmentedDirection(rtl: Boolean): Float = if (rtl) -1f else 1f

/**
 * Dove sta la goccia del segmentato, in pixel dal punto in cui il layout l'ha messa (il primo segmento).
 *
 * Il layout specchia da solo la posizione di partenza (il primo segmento sta a destra), ma la
 * traslazione e' di un layer e non specchia niente: senza il segno la goccia usciva dalla pista dal lato
 * sbagliato, e il secondo segmento non aveva mai la goccia sotto.
 */
internal fun segmentedDropTranslation(value: Float, segmentWidth: Float, rtl: Boolean): Float =
  segmentedDirection(rtl) * value * segmentWidth

/** Di quanti segmenti un movimento orizzontale del dito sposta la goccia: lo stesso verso, specchiato. */
internal fun segmentedDragDelta(dragAmountX: Float, segmentWidth: Float, rtl: Boolean): Float =
  segmentedDirection(rtl) * dragAmountX / segmentWidth

/**
 * Dove cade un dito sulla pista, in segmenti, misurato perche' la goccia ci stia **centrata sotto**:
 * 0 e' la goccia sul primo segmento, 0,5 il dito sul bordo fra il primo e il secondo. Non limitato:
 * chi la mette sotto il dito la limita, chi chiede se il dito e' sulla goccia no. Da destra a sinistra
 * il primo segmento sta a destra.
 */
internal fun segmentedFingerValue(x: Float, width: Float, inset: Float, segmentWidth: Float, rtl: Boolean): Float {
  if (segmentWidth <= 0f) return 0f
  val fromStart = if (rtl) width - x else x
  return (fromStart - inset) / segmentWidth - 0.5f
}

/** Il dito e' sceso sulla goccia, che a [dropValue] copre mezzo segmento per parte: e' gia' della goccia. */
internal fun segmentedTouchOnDrop(fingerValue: Float, dropValue: Float): Boolean =
  abs(fingerValue - dropValue) <= 0.5f

/**
 * Quanto cresce la goccia del segmentato sotto il dito.
 *
 * Quanto la pillola della tab bar, `78/56` (la sua parente piu' vicina: una pastiglia in una barra a
 * pastiglia), e non il 1,5 dei pomelli: quelli stanno su una pista sottile e escono volentieri, questa
 * sta in una pista alta quanto lei e a 1,5 coprirebbe due segmenti. Un numero da giudicare in mano.
 */
private const val SegmentDropPressedScale = 78f / 56f

/**
 * Il vetro che un controllo piccolo guarda, se c'e': il canvas della pagina (la lavata ambientale,
 * il foglio) o, dove non c'e', quello della barra o della capsula che lo contiene. Nullo sotto API 31
 * e su una pagina senza niente da attraversare: li' il controllo disegna la superficie opaca di
 * sempre.
 *
 * La stessa regola di `glass = true` sui contenitori: una *richiesta*, che vale solo dove c'e' un
 * fondale, e che per questo si puo' aggiungere a un controllo prima che ogni schermata che lo usa
 * abbia un canvas.
 */
@Composable
internal fun fluidControlGlassBackdrop(): GlassBackdropState? {
  if (!isRenderEffectSupported()) return null
  return currentCanvasBackdrop() ?: LocalGlassBackdrop.current
}

private val SegmentHeight = 32.dp
private val SegmentPillInset = 2.dp

/**
 * A filter pill.
 *
 * Used where a segmented control cannot be — when the options do not fit on one row, or when more
 * than one can be on at a time.
 *
 * Dalla 3.0 (fase 9) e' un **tasto di vetro** dove c'e' qualcosa da attraversare — il canvas di una
 * pagina ambientale, un foglio, una barra: la stessa lastra di [FluidGlassButton], e quando e' scelto
 * l'accento si unisce al materiale (non un riempimento opaco che lo sostituisce) come in ogni tasto di
 * vetro scelto. La pressione e' quella dei tasti — cresce, segue il dito, tre colpi per un tocco e la
 * grana se il dito scorre veloce — e la luce del dito e' quella del vetro. Dove non c'e' niente da
 * attraversare (una pagina piatta, sotto API 31) e' la pastiglia opaca di sempre.
 */
@Composable
fun FluidChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leading: (@Composable () -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val glass = fluidControlGlassBackdrop()

  val container by animateColorAsState(
    targetValue = if (selected) scheme.primary else scheme.onSurface.copy(alpha = 0.06f),
    animationSpec = FluidMotion.color(200),
    label = "chip container",
  )
  val content by animateColorAsState(
    targetValue = when {
      // Come il tasto di vetro scelto: l'accento e' nel materiale, e l'etichetta sta sul suo
      // contenitore, non su un riempimento pieno.
      glass != null -> if (selected) scheme.onPrimaryContainer else scheme.onSurface
      selected -> scheme.onPrimary
      else -> scheme.onSurface
    },
    animationSpec = FluidMotion.color(200),
    label = "chip content",
  )

  val face: @Composable () -> Unit = {
    CompositionLocalProvider(LocalContentColor provides content) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (leading != null) {
          Box(modifier = Modifier.size(15.dp), contentAlignment = Alignment.Center) { leading() }
        }
        ProvideTextStyle(
          MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        ) {
          Text(text = label, maxLines = 1)
        }
      }
    }
  }

  if (glass != null) {
    // Una pressione sola per il click e per il vetro, come in FluidGlassButton: la lente cresce, segue
    // il dito e si accende sulla stessa linea del tempo su cui il click viene confermato.
    val press = rememberFluidPressState(FluidPressFeedback.Expand)
    Box(
      modifier = modifier
        .alpha(if (enabled) 1f else 0.4f)
        .defaultMinSize(minHeight = 48.dp)
        .semantics { this.selected = selected }
        .glassControlSurface(
          backdrop = glass,
          shape = FluidCapsuleShape,
          selected = selected,
          interactive = enabled,
          pressState = press,
        )
        .fluidPressable(
          onClick = onClick,
          enabled = enabled,
          role = Role.Button,
          focusShape = FluidCapsuleShape,
          pressState = press,
        )
        .padding(horizontal = 14.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center,
    ) { face() }
    return
  }

  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.4f)
      .defaultMinSize(minHeight = 48.dp)
      .semantics { this.selected = selected }
      // Outside the clip and the fill, so the pill is what grows (2.11.0): inside them only the
      // label moved, and a chip pressed under a thumb showed nothing at all.
      .fluidPressable(
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
        focusShape = FluidCapsuleShape,
        feedback = FluidPressFeedback.Expand,
      )
      .clip(FluidCapsuleShape)
      .background(container)
      .padding(horizontal = 14.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
  ) { face() }
}

/**
 * A stepper-free numeric or textual value shown at the trailing edge of a settings row.
 *
 * iOS sets these in the secondary colour, never the accent: the value is information, and the row
 * itself is the thing you tap.
 */
@Composable
fun FluidRowValue(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    modifier = modifier,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
  )
}

/** A hairline. 0.5dp is a real width on every screen the app runs on, and it is what iOS draws. */
@Composable
fun FluidHairline(
  modifier: Modifier = Modifier,
  startInset: Dp = 0.dp,
  color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(start = startInset)
      .height(0.5.dp)
      .background(color),
  )
}

/** A circular dot, used to preview an accent colour in the picker. */
@Composable
fun FluidColorDot(
  color: Color,
  selected: Boolean,
  onClick: () -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  size: Dp = 30.dp,
) {
  val scheme = MaterialTheme.colorScheme
  val touchTargetSize = maxOf(48.dp, size + 10.dp)
  val ringAlpha by animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = FluidMotion.color(180),
    label = "dot ring",
  )
  Box(
    modifier = modifier
      .size(touchTargetSize)
      .semantics {
        contentDescription = label
        this.selected = selected
      }
      .fluidPressable(onClick = onClick, role = Role.RadioButton, feedback = FluidPressFeedback.Expand)
      .drawBehind {
        if (ringAlpha > 0.001f) {
          drawCircle(
            color = scheme.onSurface.copy(alpha = FluidSelectedRingAlpha * ringAlpha),
            radius = size.toPx() / 2f + 4.dp.toPx(),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
          )
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(color),
    )
  }
}

/** Final-state contrast is at least 3:1 on the light and dark app container surfaces. */
internal const val FluidSelectedRingAlpha = 0.55f

