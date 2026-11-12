package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropFaces
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropLens
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropMotion
import dev.antigravity.fluidengine.ui.glass.interaction.glassDrop
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A semantic destination in a long, sectioned [androidx.compose.foundation.lazy.LazyColumn]. */
@Immutable
data class FluidSectionAnchor(
  val key: String,
  val label: String,
  val itemIndex: Int,
)

/** Whether a section selection should settle spatially or follow a finger without queueing motion. */
enum class FluidSectionSelectionMotion {
  Animated,
  Immediate,
}

/**
 * A draggable section index for semantic jumps such as weekdays or months.
 *
 * At rest it is a sliver of marks — no pane, no pill, nothing that competes with the page. Touched,
 * it **unfolds into the scrollbar it actually is**: the marks spread over a full-height glass rail,
 * a lens rides the rail under the finger, and the label bubble travels with it. The unfolding is
 * one animated value read in layout and draw, so no frame of it recomposes anything.
 *
 * The whole 48 dp trailing strip around the resting marks is the hit target, and it exposes a range
 * to accessibility services instead of making several overlapping four-dp dots focusable. Callers
 * own scrolling so this primitive remains usable with lists whose static leading items vary.
 */
@Composable
fun FluidSectionIndex(
  sections: List<FluidSectionAnchor>,
  activeSectionKey: String?,
  onSelectSection: (FluidSectionAnchor, FluidSectionSelectionMotion) -> Unit,
  modifier: Modifier = Modifier,
  visible: Boolean = true,
  maxVisibleAnchors: Int = FluidSectionIndexDefaults.MaxVisibleAnchors,
  /**
   * Supply this only when the index is drawn from [FluidScreen.overlay]. A rail inside the recorded
   * list body would sample itself; the explicit parameter makes that invalid placement visible.
   */
  backdrop: GlassBackdropState? = null,
) {
  val sampledSections = remember(sections, maxVisibleAnchors, activeSectionKey) {
    sampleFluidSectionAnchors(sections, maxVisibleAnchors, preferredKey = activeSectionKey)
  }
  if (sampledSections.size < 2) return

  val selectedIndex = activeFluidSectionIndex(sampledSections, activeSectionKey)
    .coerceIn(0, sampledSections.lastIndex)
  val activeSection = sampledSections[selectedIndex]
  val currentOnSelect by rememberUpdatedState(onSelectSection)
  val haptics = LocalFluidHaptics.current
  val touchSlop = LocalViewConfiguration.current.touchSlop
  val markColor = MaterialTheme.colorScheme.primary
  // La bolla e' un pannello flottante: la sua pellicola e' quella di tutti (`panelTint`), non la
  // flottante della navigazione.
  val panelTint = GlassDefaults.panelTint()
  val controlTint = GlassDefaults.controlTint()
  val scheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion

  var interacting by remember { mutableStateOf(false) }
  // Dove stava la barretta quando il dito l'ha presa. Il bersaglio segue la barretta, e la barretta
  // segue il dito: senza congelarla, muovere il dito muove il bersaglio sotto il dito, e la y
  // relativa da cui si ricava la selezione insegue se stessa.
  var frozenFraction by remember { mutableFloatStateOf(0f) }
  // 0 = the resting sliver, 1 = the full-height rail with the lens on it.
  val expansion = remember { Animatable(0f) }
  // Where the finger is, as a fraction of the unfolded rail: one number drives the lens, the label
  // and the selection together.
  var fingerFraction by remember { mutableFloatStateOf(0f) }

  val compactTrackHeight = sectionIndexHeight(sampledSections.size)

  // La lente e' una goccia di vetro dove il vetro c'e'; il fondale lo da' chi monta l'indice (vedi
  // `backdrop`), e sotto API 31 non c'e' RenderEffect.
  val lensIsGlass = backdrop != null && isRenderEffectSupported()
  val lensFace = GlassDropFaces.forSurface(GlassDefaults.isDarkSurface())
  // La pressione della goccia e' l'apertura del nastro, e la scala e' la sua: nasce piccola sotto il
  // dito e arriva intera. Nessuna velocita': il dito la porta, e non c'e' una molla da cui leggerla.
  val lensMotion = remember(expansion) {
    GlassDropMotion(
      progress = { expansion.value },
      scaleX = { LensRestScale + (1f - LensRestScale) * expansion.value },
      scaleY = { LensRestScale + (1f - LensRestScale) * expansion.value },
      velocity = { 0f },
    )
  }

  // L'indice entra dal bordo su cui sta, per offset di layout e alfa: mai una scala. Dentro c'e' il
  // vetro (la pista, la goccia), e un layer scalato sopra un vetro rifrange cio' che non c'e' piu':
  // il `scaleIn(0,92)` di prima faceva "respirare" il fondale dietro la pista a ogni comparsa. Verso
  // il bordo, che in destra-sinistra e' il lato opposto.
  val towardsEdge = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = fadeIn(FluidMotion.fadeIn(160)) +
      slideInHorizontally(IndexSlideSpec) { (it * IndexSlideFraction).roundToInt() * towardsEdge },
    exit = fadeOut(FluidMotion.fadeOut(120)) +
      slideOutHorizontally(IndexSlideSpec) { (it * IndexSlideFraction).roundToInt() * towardsEdge },
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxHeight()
        .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.End))
        .padding(end = FluidSectionIndexDefaults.EdgePadding)
        .width(FluidSectionIndexDefaults.TouchWidth),
    ) {
      val rootHeightPx = constraints.maxHeight.toFloat()
      val rootWidthPx = constraints.maxWidth.toFloat()
      val markInsetPx = with(density) { FluidSectionIndexDefaults.ExpandedMarkInset.toPx() }
      // Il percorso a riposo e quello aperto sono due frazioni dell'altezza, non due costanti: su un
      // telefono e su un tablet la barretta deve occupare la stessa *proporzione* di schermo, o su
      // uno dei due diventa un dettaglio invisibile e sull'altro una colonna.
      val compactHPx = (rootHeightPx * FluidSectionIndexDefaults.RestTrackFraction).coerceIn(
        with(density) { FluidSectionIndexDefaults.RestTrackMinHeight.toPx() },
        with(density) { FluidSectionIndexDefaults.RestTrackMaxHeight.toPx() },
      )
      val expandedHPx = (rootHeightPx * FluidSectionIndexDefaults.ExpandedTrackFraction)
        .coerceIn(
          with(density) { FluidSectionIndexDefaults.ExpandedTrackMinHeight.toPx() },
          with(density) { FluidSectionIndexDefaults.ExpandedTrackMaxHeight.toPx() },
        )
        .coerceAtMost(rootHeightPx - 2f * with(density) {
          FluidSectionIndexDefaults.ExpandedMargin.toPx()
        })
        .coerceAtLeast(compactHPx)
      val restBarWPx = with(density) { FluidSectionIndexDefaults.RestBarWidth.toPx() }
      val restBarHPx = with(density) { FluidSectionIndexDefaults.RestBarHeight.toPx() }
      val restWPx = with(density) { FluidSectionIndexDefaults.RestBarWidth.toPx() }
      val expandedWPx = with(density) { FluidSectionIndexDefaults.ExpandedTrackWidth.toPx() }
      val markPx = with(density) { FluidSectionIndexDefaults.MarkSize.toPx() }
      val activeMarkPx = with(density) { FluidSectionIndexDefaults.ActiveMarkWidth.toPx() }
      val markSpacingPx = with(density) { FluidSectionIndexDefaults.MarkSpacing.toPx() }
      val lensPx = with(density) { FluidSectionIndexDefaults.LensSize.toPx() }
      val labelHalfPx = with(density) { 16.dp.toPx() }

      fun trackHeight(t: Float): Float = lerpFloat(compactHPx, expandedHPx, t)
      fun trackTop(t: Float): Float = (rootHeightPx - trackHeight(t)) / 2f
      fun expandedFractionForRootY(rootY: Float): Float {
        val usable = (trackHeight(1f) - 2f * markInsetPx).coerceAtLeast(1f)
        return ((rootY - trackTop(1f) - markInsetPx) / usable).coerceIn(0f, 1f)
      }
      fun compactFractionForRootY(rootY: Float): Float {
        val usable = (trackHeight(0f) - restBarHPx).coerceAtLeast(1f)
        return ((rootY - trackTop(0f) - restBarHPx / 2f) / usable).coerceIn(0f, 1f)
      }
      /** Dove sta la barretta a riposo: il centro della sua corsa, alla frazione data. */
      val hitHalfPx = with(density) {
        (FluidSectionIndexDefaults.RestBarHeight + FluidSectionIndexDefaults.HitMargin).toPx() / 2f
      }
      /** Dove sta la barretta a riposo: il centro della sua corsa, alla frazione data. */
      fun restBarCenterY(fraction: Float): Float =
        restBarHPx / 2f + fraction * (trackHeight(0f) - restBarHPx).coerceAtLeast(0f)
      fun lensCenterY(): Float =
        trackTop(1f) + markInsetPx + fingerFraction * (trackHeight(1f) - 2f * markInsetPx)

      // Cio' che il gesto legge mentre e' in corso, sempre aggiornato (F1B.3, giro di correzione). Il gesto non
      // riparte quando la composizione cambia (vedi `SharedPointerElement`): legge questi valori quando gli
      // servono, non quelli del momento in cui e' nato.
      val currentSelectedIndex by rememberUpdatedState(selectedIndex)
      val currentSections by rememberUpdatedState(sampledSections)
      val currentUsableExpanded by rememberUpdatedState((trackHeight(1f) - 2f * markInsetPx).coerceAtLeast(1f))
      val currentHaptics by rememberUpdatedState(haptics)
      val currentReducedMotion by rememberUpdatedState(reducedMotion)
      val gestureHost = remember(scope, expansion) {
        FluidSectionIndexGestureHost(
          selectedIndex = { currentSelectedIndex },
          count = { currentSections.size },
          usableExpandedPx = { currentUsableExpanded },
          onHeld = { originFraction ->
            currentHaptics.play(FluidHapticEvent.GestureStart)
            frozenFraction = originFraction
            interacting = true
            fingerFraction = originFraction
            scope.launch {
              if (currentReducedMotion) expansion.snapTo(1f)
              else expansion.animateTo(1f, FluidMotion.snappy())
            }
          },
          onFraction = { fraction -> fingerFraction = fraction },
          onIndex = { index ->
            currentHaptics.play(FluidHapticEvent.Tick)
            currentSections.getOrNull(index)?.let { currentOnSelect(it, FluidSectionSelectionMotion.Immediate) }
          },
          onRelease = {
            interacting = false
            scope.launch {
              if (currentReducedMotion) expansion.snapTo(0f)
              else expansion.animateTo(0f, FluidMotion.snappy())
            }
          },
        )
      }


      // The rail. Its size lives in a layout lambda and its marks in a draw lambda, both reading
      // [expansion] directly: the unfolding never recomposes.
      Box(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .layout { measurable, _ ->
            val t = expansion.value
            val w = lerpFloat(restWPx, expandedWPx, t).roundToInt().coerceAtLeast(1)
            val h = trackHeight(t).roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
          }
          .then(
            if (backdrop != null) {
              Modifier.glassSurface(
                state = backdrop,
                tint = controlTint,
                shape = FluidCapsuleShape,
                role = GlassRole.Interactive,
                // The material itself is what unfolds: at rest there is no pane at all, only the
                // marks — the resting blob of glass was the single ugliest pixel this component
                // produced, and nothing about the resting job needs a surface.
                intensity = { expansion.value },
              )
            } else {
              Modifier.drawBehind {
                val t = expansion.value
                if (t > 0.01f) {
                  val radius = size.width / 2f
                  drawRoundRect(
                    color = scheme.surfaceContainerHigh.copy(alpha = 0.9f * t),
                    cornerRadius = CornerRadius(radius, radius),
                  )
                }
              }
            },
          )
          .drawBehind {
            val t = expansion.value
            val n = sampledSections.size

            // Le stazioni esistono solo da aperto. A riposo questo non e' un indice: e' un
            // indicatore, e un indicatore con otto tacche accanto sta gia' chiedendo di essere
            // usato come indice — cioe' sta gia' invitando il pollice a toccarlo mentre scorre.
            if (t > 0.01f) {
              for (index in 0 until n) {
                val isActive = index == selectedIndex && !interacting
                val y = markInsetPx + (size.height - 2f * markInsetPx) * (index / (n - 1f))
                val w = if (isActive) markPx * 2f else markPx
                val radius = markPx / 2f
                drawRoundRect(
                  color = markColor.copy(alpha = lerpFloat(0f, if (isActive) 1f else 0.55f, t)),
                  topLeft = Offset((size.width - w) / 2f, y - markPx / 2f),
                  size = Size(w, markPx),
                  cornerRadius = CornerRadius(radius, radius),
                )
              }
            }

            // La barretta: a riposo dice dove sei, e basta. Aprendosi si accorcia fino a diventare
            // la stazione attiva, cosi' la cosa che stavi guardando e' la stessa cosa che ti ritrovi
            // in mano invece di sparire e lasciare il posto a un altro oggetto.
            val fraction = if (interacting) {
              fingerFraction
            } else {
              selectedIndex / (n - 1f).coerceAtLeast(1f)
            }
            val restCenter = restBarCenterY(fraction)
            val openCenter = markInsetPx + (size.height - 2f * markInsetPx) * fraction
            val centerY = lerpFloat(restCenter, openCenter, t)
            val barW = lerpFloat(restBarWPx, markPx * 2f, t)
            val barH = lerpFloat(restBarHPx, markPx, t)
            val barX = lerpFloat(size.width - barW, (size.width - barW) / 2f, t)
            drawRoundRect(
              color = markColor.copy(alpha = lerpFloat(0.85f, 1f, t)),
              topLeft = Offset(barX, centerY - barH / 2f),
              size = Size(barW, barH),
              cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
          },
      )

      // The lens: a drop of glass that rides the rail under the finger.
      //
      // It is the drop of the recipe (3.0, fase 9: [glassDrop], Kyant's thumb), and its pressure is
      // the unfolding of the rail: it is born under the finger, so there is no "held" of its own to
      // wait for. Full at the first frame of the unfolding, a lens by the last. The growth from 0.4 is
      // in the drop's own `layerBlock` and not in a layer above it: a scaled layer over a pane of glass
      // refracts what is no longer there, and this one is on the move the whole time it exists.
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(FluidSectionIndexDefaults.LensSize)
          .offset {
            val dx = ((lensPx - expandedWPx) / 2f).roundToInt()
            IntOffset(dx, (lensCenterY() - lensPx / 2f).roundToInt())
          }
          .graphicsLayer {
            val t = expansion.value
            alpha = t
            // Il ripiego senza vetro e' una pastiglia dipinta: li' una scala sul layer non rifrange
            // niente di sbagliato, e resta com'era.
            if (!lensIsGlass) {
              scaleX = LensRestScale + (1f - LensRestScale) * t
              scaleY = LensRestScale + (1f - LensRestScale) * t
            }
          }
          .then(
            if (lensIsGlass) {
              Modifier.glassDrop(
                backdrop = backdrop!!.backdrop,
                motion = lensMotion,
                lensSize = GlassDropLens.Slider,
                face = lensFace,
                shape = FluidCapsuleShape,
              )
            } else {
              Modifier
                .clip(FluidCapsuleShape)
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
            },
          ),
      )

      // The label bubble, travelling with the lens. Measured unbounded: the strip is 48 dp wide
      // and the label is a month name — constrained to the strip it would ellipsise to two letters.
      AnimatedVisibility(
        visible = interacting,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .wrapContentWidth(align = Alignment.End, unbounded = true)
          .offset {
            IntOffset(
              -with(density) { (FluidSectionIndexDefaults.TouchWidth - 6.dp).roundToPx() },
              (lensCenterY() - labelHalfPx).roundToInt(),
            )
          },
        // Anche la bolla e' un vetro (il pannello): arriva dalla lente per offset e alfa, non per scala.
        enter = fadeIn(FluidMotion.fadeIn(110)) +
          slideInHorizontally(IndexSlideSpec) { (it * BubbleSlideFraction).roundToInt() * towardsEdge },
        exit = fadeOut(FluidMotion.fadeOut(100)) +
          slideOutHorizontally(IndexSlideSpec) { (it * BubbleSlideFraction).roundToInt() * towardsEdge },
      ) {
        Text(
          text = activeSection.label,
          modifier = Modifier
            .then(
              if (backdrop != null) {
                Modifier.glassSurface(
                  state = backdrop,
                  tint = panelTint,
                  shape = FluidCapsuleShape,
                  role = GlassRole.Modal,
                  // Il materiale di tutti i pannelli, non l'ottica del ruolo `Modal` (lente 20/28 dp su
                  // una bolla alta trenta: non sarebbe una lente, sarebbe il pannello intero ripiegato).
                  // Piccola, quindi `compact` (lente 7/9 dp), e sopra una pagina nitida senza scrim,
                  // quindi la sfocatura sale: il testo che passa dietro lo deve nascondere lei.
                  optics = GlassDefaults.panelOptics(compact = true, overSharpPage = true),
                )
              } else {
                Modifier
                  .clip(FluidCapsuleShape)
                  .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
              },
            )
            .padding(horizontal = 11.dp, vertical = 7.dp),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = scheme.onSurface,
          maxLines = 1,
        )
      }

      // Il bersaglio e' **la barretta**, e viaggia con lei.
      //
      // Un nodo che riceve un tocco lo riceve e basta: anche non consumandolo, la lista che gli sta
      // sotto non lo vede mai, perche' sono fratelli e il test di collisione si ferma al primo che
      // colpisce. Quindi qui non conta cosa il gesto decide di fare, conta quanto schermo occupa:
      // ogni dp di questo riquadro e' un dp in cui scorrere non funziona.
      //
      // Prima erano 48 x 136 dp appoggiati al bordo destro, all'altezza esatta in cui sta il
      // pollice: e' li' che finiva un gesto su cinque. Ora sono 28 dp per l'altezza della barretta
      // piu' un margine, centrati su di lei, che e' l'unica cosa visibile da prendere. Cominciato
      // il trascinamento il flusso di puntatore resta a questo nodo ovunque vada il dito, quindi il
      // nastro aperto si percorre tutto anche partendo da qui.
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .offset {
            val fraction = if (interacting) {
              frozenFraction
            } else {
              selectedIndex / (sampledSections.size - 1f).coerceAtLeast(1f)
            }
            IntOffset(
              0,
              (trackTop(0f) + restBarCenterY(fraction) - hitHalfPx).roundToInt(),
            )
          }
          .width(FluidSectionIndexDefaults.MinTouchWidth)
          .height(FluidSectionIndexDefaults.RestBarHeight + FluidSectionIndexDefaults.HitMargin)
          .sectionIndexSemantics(
            sections = sampledSections,
            selectedIndex = selectedIndex,
            onSelect = { index ->
              haptics.play(FluidHapticEvent.Tick)
              currentOnSelect(sampledSections[index], FluidSectionSelectionMotion.Animated)
            },
            labels = LocalFluidLabels.current,
          )
          // Il gesto (la pressione lunga che prende il nastro, poi il trascinamento) sta in
          // `fluidSectionIndexGesture`, e legge cio' che cambia attraverso `gestureHost`. Le chiavi sono
          // quelle che cambiano il gesto stesso: una ricomposizione a meta' trascinamento (la sezione
          // scelta che cambia sotto il dito) non lo fa ripartire. Vedi `SharedPointerElement`.
          .sharedPointerInput(touchSlop, reducedMotion, gestureHost) {
            fluidSectionIndexGesture(touchSlop, gestureHost)
          },
      )
    }
  }
}

/**
 * Cio' che il gesto dell'indice chiede e comunica a chi lo disegna, tutto **letto quando serve** (F1B.3, giro di
 * correzione). Il gesto vive quanto il nodo che lo porta, non quanto una composizione: i valori che cambiano mentre il
 * dito trascina (la sezione scelta, quante sezioni ci sono, l'altezza del nastro aperto) li chiede qui.
 */
internal class FluidSectionIndexGestureHost(
  val selectedIndex: () -> Int,
  val count: () -> Int,
  val usableExpandedPx: () -> Float,
  /** La pressione lunga ha preso il gesto: il nastro si apre alla frazione della sezione scelta. */
  val onHeld: (originFraction: Float) -> Unit,
  /** Dove sta il dito sul nastro aperto, 0..1. */
  val onFraction: (Float) -> Unit,
  /** Il dito ha attraversato una tacca: la sezione [Int] e' quella nuova. */
  val onIndex: (Int) -> Unit,
  /** Il dito si e' alzato, o il gesto e' stato tolto: il nastro si richiude. */
  val onRelease: () -> Unit,
)

/**
 * Il gesto dell'indice di sezione: **si prende solo tenendo premuto**, poi segue il dito sul nastro aperto.
 *
 * Prima lo prendeva al tocco, e doveva: lasciato libero, lo scorrevole della lista rivendica il movimento alla *sua*
 * soglia e la pagina scorre sotto il dito invece dell'indice. Ma un controllo che rivendica al tocco rivendica anche
 * tutti i tocchi che non erano per lui — e stando sul bordo destro, all'altezza del pollice, quelli sono la
 * maggioranza: la lista saltava a una sezione a caso circa un gesto su cinque.
 *
 * La pressione lunga scioglie il nodo senza compromessi, perche' le due intenzioni si distinguono da sole: chi vuole
 * scorrere si muove subito, chi vuole l'indice si ferma. Finche' il dito e' fermo la lista non ha ancora niente da
 * rivendicare, quindi prenderlo qui non toglie niente a nessuno; e se invece si muove, **prima della presa non si
 * consuma mai** e lo scorrimento parte come se questo nodo non esistesse.
 *
 * **Dopo la presa si consuma ogni cambio, fino al sollevamento** (F1B.3, giro di correzione, S2 parte 2.1: la pagina
 * scorreva di 103 px su 120 di dito). Il consumo c'era gia'; si perdeva perche' il gesto ripartiva a ogni
 * ricomposizione (vedi `SharedPointerElement`), e il gesto nuovo, in attesa di un dito che scende, lasciava i cambi
 * del dito gia' giu' senza consumarli. Lo scorrevole della lista (Compose 1.10, `DragGestureNode`) li raccoglie: chi
 * vede un cambio consumato da un figlio non si arrende, aspetta un evento che nessuno ha consumato e da li' riparte
 * dalla soglia. 120 px di dito meno la soglia (8 dp, 17 px sul tablet) sono i 103 px misurati.
 *
 * Il trascinamento e' relativo a dove il dito ha preso: il nastro si apre *dove sei*, e mappare la y del dito in
 * assoluto sul nastro aperto sposterebbe la selezione al solo aprirlo (la barretta a riposo e il nastro aperto non
 * hanno la stessa altezza).
 */
internal suspend fun PointerInputScope.fluidSectionIndexGesture(touchSlop: Float, host: FluidSectionIndexGestureHost) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
      while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
          ?: return@withTimeoutOrNull false
        if (!change.pressed || change.isConsumed) return@withTimeoutOrNull false
        if (distance(down.position, change.position) > touchSlop) {
          return@withTimeoutOrNull false
        }
      }
      @Suppress("UNREACHABLE_CODE")
      false
    } ?: true
    if (!held) return@awaitEachGesture

    val count = host.count()
    val originY = down.position.y
    val originFraction = host.selectedIndex() / (count - 1f).coerceAtLeast(1f)
    // Parte da dove sei: la goccia nasce sotto il dito sulla tacca della sezione attiva, e il primo evento a dito
    // fermo non e' una tacca attraversata. Con `null` suonava un Tick subito dopo il `GestureStart` della pressione
    // lunga (due colpi per un momento) e riselezionava la sezione dov'era gia'.
    var lastDragIndex = host.selectedIndex()
    host.onHeld(originFraction)
    try {
      while (true) {
        val event = awaitPointerEvent()
        // Ogni cambio, non solo quello del dito: anche un secondo dito appoggiato dopo la presa e' dell'indice, o
        // lo scorrevole lo prenderebbe come un evento che nessuno ha consumato.
        event.changes.forEach { it.consume() }
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        val fraction = (originFraction + (change.position.y - originY) / host.usableExpandedPx()).coerceIn(0f, 1f)
        host.onFraction(fraction)
        val index = fluidSectionIndexForFraction(fraction, host.count())
        if (index != lastDragIndex) {
          lastDragIndex = index
          host.onIndex(index)
        }
        if (change.changedToUpIgnoreConsumed() || !change.pressed) break
      }
    } finally {
      // Anche se il gesto viene tolto a meta' (il nodo che se ne va, una chiave che cambia): il nastro non resta
      // aperto sotto un dito che non c'e' piu'.
      host.onRelease()
    }
  }
}

/** La sezione a una frazione del nastro: la tacca piu' vicina. */
internal fun fluidSectionIndexForFraction(fraction: Float, count: Int): Int =
  if (count <= 1) 0 else (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)

/** Da quanto e' grande la lente nel primo fotogramma dell'apertura: il 40%, e arriva intera. */
private const val LensRestScale = 0.4f

/** Quanta della sua larghezza l'indice percorre entrando: dal bordo, non dal nulla. */
private const val IndexSlideFraction = 0.5f

/** La bolla si stacca dalla lente di poco: una frazione della sua larghezza, che e' quella di un nome di mese. */
private const val BubbleSlideFraction = 0.15f

/** La molla dell'entrata e dell'uscita dell'indice e della bolla: critica, in pixel interi (nessuna coda sotto-pixel). */
private val IndexSlideSpec = FluidMotion.intOffset(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy)

object FluidSectionIndexDefaults {
  const val MaxVisibleAnchors: Int = 8
  val TouchWidth = 48.dp
  val MarkSize = 4.dp
  val ActiveMarkWidth = 16.dp
  val MarkSpacing = 10.dp

  /**
   * La barretta a riposo.
   *
   * Cinque per cinquanta, non quattro per quaranta: a quattro dp, con l'alfa di un indicatore
   * discreto, sopra una lavata chiara e a filo del bordo destro, era **invisibile** — misurata su
   * una registrazione, non c'era una sola colonna di pixel che si staccasse dallo sfondo.
   */
  val RestBarWidth = 5.dp
  val RestBarHeight = 50.dp

  /** Quanta altezza percorre la barretta a riposo, e i limiti entro cui resta leggibile. */
  const val RestTrackFraction: Float = 0.42f
  val RestTrackMinHeight = 160.dp
  val RestTrackMaxHeight = 300.dp

  /**
   * Quanto si apre il nastro quando lo tieni premuto.
   *
   * Poco piu' della meta' dello schermo, non tutto. Un nastro alto quanto il display mette otto
   * stazioni a un centimetro l'una dall'altra: sembra piu' facile da centrare e invece obbliga il
   * pollice a percorrere l'intera altezza del telefono per attraversare l'archivio, che e' proprio
   * il movimento che una scorciatoia dovrebbe evitare.
   */
  const val ExpandedTrackFraction: Float = 0.55f
  val ExpandedTrackMinHeight = 260.dp
  val ExpandedTrackMaxHeight = 460.dp
  val EdgePadding = 4.dp

  /**
   * Narrowest the rail's hit target may be, however thin the marks it is resting as.
   *
   * A compromise between two failures that are not symmetric: a target smaller than this is one you
   * have to aim at, and a target much larger than this eats scrolls aimed at the list. Missing the
   * index costs a second attempt; losing a scroll costs the page jumping somewhere you did not ask
   * for, and the second is far worse.
   */
  val MinTouchWidth = 28.dp

  /** Quanto margine sopra e sotto la barretta e' ancora "la barretta", per un pollice. */
  val HitMargin = 16.dp

  /** How far the unfolded rail stays from the strip's vertical ends. */
  val ExpandedMargin = 18.dp

  /** Inset of the first and last station inside the unfolded rail. */
  val ExpandedMarkInset = 16.dp

  /** Width of the unfolded rail. */
  val ExpandedTrackWidth = 20.dp

  /** Diameter of the lens that rides the rail. */
  val LensSize = 34.dp
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
  start + (stop - start) * fraction

internal fun sampleFluidSectionAnchors(
  sections: List<FluidSectionAnchor>,
  maxVisibleAnchors: Int,
  preferredKey: String? = null,
): List<FluidSectionAnchor> {
  if (sections.isEmpty() || maxVisibleAnchors <= 0) return emptyList()
  if (sections.size <= maxVisibleAnchors) return sections
  if (maxVisibleAnchors == 1) return listOf(sections.first())

  val last = sections.lastIndex.toFloat()
  val sampledIndices = (0 until maxVisibleAnchors)
    .map { sampleIndex -> ((sampleIndex * last) / (maxVisibleAnchors - 1)).roundToInt() }
    .distinct()
    .toMutableList()
  val preferredIndex = sections.indexOfFirst { it.key == preferredKey }
  if (
    preferredIndex > 0 &&
    preferredIndex < sections.lastIndex &&
    preferredIndex !in sampledIndices &&
    sampledIndices.size > 2
  ) {
    val replacementPosition = (1 until sampledIndices.lastIndex)
      .minByOrNull { position -> kotlin.math.abs(sampledIndices[position] - preferredIndex) }
    if (replacementPosition != null) sampledIndices[replacementPosition] = preferredIndex
  }
  return sampledIndices.distinct().sorted().map(sections::get)
}

internal fun activeFluidSectionIndex(
  sections: List<FluidSectionAnchor>,
  activeSectionKey: String?,
): Int {
  if (sections.isEmpty()) return -1
  val exact = sections.indexOfFirst { it.key == activeSectionKey }
  return if (exact >= 0) exact else 0
}

fun activeFluidSectionForItemIndex(
  sections: List<FluidSectionAnchor>,
  firstVisibleItemIndex: Int,
): FluidSectionAnchor? {
  var active: FluidSectionAnchor? = null
  for (section in sections) {
    if (section.itemIndex > firstVisibleItemIndex) continue
    if (active == null || section.itemIndex > active.itemIndex) active = section
  }
  return active
}

internal fun fluidSectionIndexForPosition(
  positionY: Float,
  height: Float,
  sectionCount: Int,
): Int {
  if (sectionCount <= 1 || height <= 0f) return 0
  val fraction = (positionY / height).coerceIn(0f, 1f)
  return (fraction * (sectionCount - 1)).roundToInt().coerceIn(0, sectionCount - 1)
}

private fun sectionIndexHeight(sectionCount: Int) =
  FluidSectionIndexDefaults.MarkSize * sectionCount +
    FluidSectionIndexDefaults.MarkSpacing * (sectionCount - 1).coerceAtLeast(0)

private fun distance(first: Offset, second: Offset): Float {
  val dx = second.x - first.x
  val dy = second.y - first.y
  return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun Modifier.sectionIndexSemantics(
  sections: List<FluidSectionAnchor>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  labels: FluidLabels,
): Modifier = semantics(mergeDescendants = true) {
  val lastIndex = sections.lastIndex
  contentDescription = labels.sectionIndex
  stateDescription = labels.sectionPosition(sections[selectedIndex].label, selectedIndex + 1, sections.size)
  progressBarRangeInfo = ProgressBarRangeInfo(
    current = selectedIndex.toFloat(),
    range = 0f..lastIndex.toFloat(),
    steps = (sections.size - 2).coerceAtLeast(0),
  )
  setProgress { requested ->
    onSelect(requested.roundToInt().coerceIn(0, lastIndex))
    true
  }
  customActions = listOfNotNull(
    if (selectedIndex > 0) {
      CustomAccessibilityAction(labels.previousSection) {
        onSelect(selectedIndex - 1)
        true
      }
    } else {
      null
    },
    if (selectedIndex < lastIndex) {
      CustomAccessibilityAction(labels.nextSection) {
        onSelect(selectedIndex + 1)
        true
      }
    } else {
      null
    },
  )
}


/**
 * `pointerInput` that lets the nodes **underneath** see the same gesture.
 *
 * Compose stops hit-testing at the first sibling it lands on. That is the right default and it was
 * the wrong one here: this strip stands over a scrolling list, so every touch that fell on it was a
 * touch the list never heard about — and since the rail only claims a gesture after a long press,
 * a finger that landed on the bar and immediately dragged got **nothing at all**. Not a scroll, not
 * an index: a gesture that vanished. Shrinking the target made it rarer without making it right.
 *
 * Sharing removes the choice from geometry entirely. Both nodes receive every event; the list acts
 * on movement, the rail acts on stillness, and whichever recognises its own gesture first consumes
 * it. Nothing has to be small any more to be safe.
 *
 * **Restarts only when a key changes**, like `pointerInput` (F1B.3, giro di correzione). Until then it
 * replaced its gesture whenever the block changed identity, and the block is a new lambda at every
 * recomposition that changes what it captures: the section under the finger changing mid-drag was
 * enough. The gesture in progress was cancelled, the new one sat waiting for a finger to come down,
 * and the moves of the finger already down went through unconsumed, for the list to pick up. The
 * block now reads what changes through [FluidSectionIndexGestureHost].
 */
private fun Modifier.sharedPointerInput(
  key1: Any?,
  key2: Any?,
  key3: Any?,
  block: suspend PointerInputScope.() -> Unit,
): Modifier = this then SharedPointerElement(key1, key2, key3, block)

/**
 * The element of [sharedPointerInput]: equal to another with the same keys, **whatever the block**, so
 * a recomposition that only rebuilds the lambda leaves the running gesture alone.
 */
internal class SharedPointerElement(
  private val key1: Any?,
  private val key2: Any?,
  private val key3: Any?,
  private val block: suspend PointerInputScope.() -> Unit,
) : ModifierNodeElement<SharedPointerNode>() {
  override fun create(): SharedPointerNode = SharedPointerNode(block)

  /** Only reached when a key changed: a new gesture, with the block of now. */
  override fun update(node: SharedPointerNode) {
    node.update(block)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "sharedPointerInput"
  }

  override fun equals(other: Any?): Boolean =
    other is SharedPointerElement && key1 == other.key1 && key2 == other.key2 && key3 == other.key3

  override fun hashCode(): Int {
    var result = key1?.hashCode() ?: 0
    result = 31 * result + (key2?.hashCode() ?: 0)
    result = 31 * result + (key3?.hashCode() ?: 0)
    return result
  }
}

internal class SharedPointerNode(
  block: suspend PointerInputScope.() -> Unit,
) : DelegatingNode(), PointerInputModifierNode {

  private var pointer = delegate(SuspendingPointerInputModifierNode(block))

  fun update(block: suspend PointerInputScope.() -> Unit) {
    undelegate(pointer)
    pointer = delegate(SuspendingPointerInputModifierNode(block))
  }

  override fun onPointerEvent(
    pointerEvent: PointerEvent,
    pass: PointerEventPass,
    bounds: IntSize,
  ) {
    pointer.onPointerEvent(pointerEvent, pass, bounds)
  }

  override fun onCancelPointerInput() {
    pointer.onCancelPointerInput()
  }

  /** The whole point. See [sharedPointerInput]. */
  override fun sharePointerInputWithSiblings(): Boolean = true
}
