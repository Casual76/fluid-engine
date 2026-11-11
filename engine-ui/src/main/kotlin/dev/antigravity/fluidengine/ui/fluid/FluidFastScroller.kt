package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.UnfoldMore
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropLens
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropRecipe
import dev.antigravity.fluidengine.ui.glass.interaction.glassDrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

// Lo scorrimento rapido (3.0, fase 12c): la maniglia lungo il bordo di una lista lunga, con la bolla che
// dice dove si e' ("Marzo 2024", "M"). Due app se l'erano scritta (`GalleryScrubber` di MyCloud,
// `LazyScrollBar` di Fluidify) e ognuna aveva l'altra meta' di quello che serve: la prima aveva la bolla e
// il vetro ma contava la posizione in frazioni che l'app doveva fornire, la seconda contava le righe da
// sola e non diceva dove si era.
//
// Non e' `FluidSectionIndex`, e la differenza e' di mestiere: l'indice salta fra **sezioni** (otto tacche
// semantiche che si aprono tenendo premuto), questa **scorre**, e la posizione del dito e' la posizione
// della lista — nessuna tacca, nessuna sezione, il dito e la lista restano 1:1 per tutto il gesto.

/**
 * Cio' che lo scorrimento rapido deve sapere di una lista, e saperla muovere.
 *
 * Un'interfaccia e non un `LazyListState` perche' una lista e una griglia (e quella di un'app che ha il
 * suo contenitore) rispondono alle stesse quattro domande: quanto e' lunga, quanto se ne vede, dove sta
 * il primo elemento visibile — con la parte che e' gia' passata, perche' la maniglia non faccia un gradino
 * per elemento quando gli elementi sono alti —, e come si porta li' un indice. Gli stati di Compose sono
 * [rememberFluidFastScrollTarget].
 *
 * Tutto cio' che si legge dev'essere **stato di snapshot** (e' cosi' per `LazyListState` e
 * `LazyGridState`): si legge nel disegno, a ogni fotogramma, e la maniglia segue la lista senza ricomporre
 * niente.
 */
@Stable
interface FluidFastScrollTarget {

  /** Quanti elementi ha la lista. */
  val totalItems: Int

  /** Quanti se ne vedono adesso (anche in parte). */
  val visibleItems: Int

  /** L'indice del primo elemento visibile. */
  val firstIndex: Int

  /** `firstIndex` piu' la frazione (0..1) del primo elemento che e' gia' scorsa fuori: continuo, non a gradini. */
  val fractionalFirstIndex: Float

  /** Vero mentre la lista si muove (un dito, uno scatto, una molla). */
  val isScrollInProgress: Boolean

  /** Porta [index] in cima. Sospende; una chiamata nuova interrompe la precedente. */
  suspend fun scrollToIndex(index: Int)
}

/** Un [FluidFastScrollTarget] su una `LazyColumn` o `LazyRow`. Una per stato. */
@Composable
fun rememberFluidFastScrollTarget(state: LazyListState): FluidFastScrollTarget =
  remember(state) { LazyListFastScrollTarget(state) }

/** Un [FluidFastScrollTarget] su una `LazyVerticalGrid`. Una per stato. */
@Composable
fun rememberFluidFastScrollTarget(state: LazyGridState): FluidFastScrollTarget =
  remember(state) { LazyGridFastScrollTarget(state) }

private class LazyListFastScrollTarget(private val state: LazyListState) : FluidFastScrollTarget {
  override val totalItems: Int get() = state.layoutInfo.totalItemsCount
  override val visibleItems: Int get() = state.layoutInfo.visibleItemsInfo.size
  override val firstIndex: Int get() = state.firstVisibleItemIndex
  override val fractionalFirstIndex: Float
    get() = FluidFastScroll.fractionalFirstIndex(
      firstIndex = state.firstVisibleItemIndex,
      firstOffsetPx = state.firstVisibleItemScrollOffset,
      firstSizePx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0,
    )
  override val isScrollInProgress: Boolean get() = state.isScrollInProgress
  override suspend fun scrollToIndex(index: Int) = state.scrollToItem(index)
}

private class LazyGridFastScrollTarget(private val state: LazyGridState) : FluidFastScrollTarget {
  override val totalItems: Int get() = state.layoutInfo.totalItemsCount
  override val visibleItems: Int get() = state.layoutInfo.visibleItemsInfo.size
  override val firstIndex: Int get() = state.firstVisibleItemIndex
  override val fractionalFirstIndex: Float
    get() {
      val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
      // In una griglia la parte scorsa e' di una riga: l'altezza della riga e' quella del primo elemento.
      return FluidFastScroll.fractionalFirstIndex(
        firstIndex = state.firstVisibleItemIndex,
        firstOffsetPx = state.firstVisibleItemScrollOffset,
        firstSizePx = first?.size?.height ?: 0,
      )
    }
  override val isScrollInProgress: Boolean get() = state.isScrollInProgress
  override suspend fun scrollToIndex(index: Int) = state.scrollToItem(index)
}

/**
 * I conti dello scorrimento rapido, puri (`FluidFastScrollTest`): dove sta la maniglia, dove porta il
 * dito, quando c'e'.
 *
 * La posizione si misura **in elementi** e non in pixel, come aveva fatto Fluidify: una lista pigra
 * conosce l'altezza solo di quello che e' a schermo, e una posizione in pixel vorrebbe ogni riga
 * misurata in anticipo, che e' proprio cio' che una lista pigra esiste per evitare. Con righe quasi
 * uguali contare e misurare sono indistinguibili; con righe diverse la maniglia e' approssimata, e il
 * **dito resta 1:1 con la maniglia comunque**, perche' durante il gesto la maniglia non legge la lista:
 * si sposta di quanto si sposta il dito.
 */
object FluidFastScroll {

  /**
   * L'ultimo indice che puo' stare in cima: oltre, la lista e' finita e gli elementi visibili sono gli
   * ultimi. Zero se tutta la lista sta a schermo (non c'e' niente da scorrere).
   */
  fun maxIndex(totalItems: Int, visibleItems: Int): Int = (totalItems - visibleItems).coerceAtLeast(0)

  /**
   * L'indice del primo elemento con la parte che e' gia' scorsa: 3 elementi e un quarto sono 3,25. Il
   * quarto e' [firstOffsetPx] su [firstSizePx], tenuto fra 0 e 1 (un elemento di altezza zero non ne ha).
   */
  fun fractionalFirstIndex(firstIndex: Int, firstOffsetPx: Int, firstSizePx: Int): Float {
    val part = if (firstSizePx > 0) (firstOffsetPx.toFloat() / firstSizePx).coerceIn(0f, 1f) else 0f
    return firstIndex + part
  }

  /** Dov'e' la lista, 0 in cima e 1 in fondo. Zero se non c'e' niente da scorrere. */
  fun fraction(fractionalFirstIndex: Float, totalItems: Int, visibleItems: Int): Float {
    val max = maxIndex(totalItems, visibleItems)
    if (max <= 0) return 0f
    return (fractionalFirstIndex / max).coerceIn(0f, 1f)
  }

  /** L'indice a cui porta una frazione: l'inversa di [fraction], arrotondata, mai oltre [maxIndex]. */
  fun indexForFraction(fraction: Float, totalItems: Int, visibleItems: Int): Int {
    val max = maxIndex(totalItems, visibleItems)
    return (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
  }

  /**
   * La frazione sotto il dito: da dov'era la maniglia quando e' stata presa, di quanto si e' mosso il
   * dito su [travelPx] di corsa. **Relativa**, non assoluta: la maniglia non salta sotto il dito all'atto
   * di prenderla (si prende per il bordo, e il bordo non e' il centro), e un dito che esce dalla pista e
   * rientra non la lascia indietro.
   */
  fun scrubbedFraction(startFraction: Float, draggedPx: Float, travelPx: Float): Float {
    if (travelPx <= 0f) return startFraction.coerceIn(0f, 1f)
    return (startFraction + draggedPx / travelPx).coerceIn(0f, 1f)
  }

  /** Quanto puo' scendere la maniglia: la pista meno lei, mai meno di un pixel. */
  fun travel(trackPx: Float, thumbPx: Float): Float = (trackPx - thumbPx).coerceAtLeast(1f)

  /** Se la lista e' abbastanza lunga da meritare la maniglia: scorre davvero, e ha almeno [minItems] elementi. */
  fun isScrollable(totalItems: Int, visibleItems: Int, minItems: Int): Boolean =
    totalItems >= minItems && maxIndex(totalItems, visibleItems) > 0

  /** Se il dito ha attraversato un'etichetta: [next] c'e' ed e' diversa da quella di prima. Cio' che suona il tick. */
  fun labelCrossed(previous: String?, next: String?): Boolean = next != null && next != previous
}

/** Le misure dello scorrimento rapido. */
object FluidFastScrollerDefaults {

  /** Sotto questo numero di elementi la lista si scorre a mano: la maniglia sarebbe arredamento. */
  const val MinItems: Int = 30

  /** La maniglia: una goccia alta quanto un pollice. */
  val ThumbWidth: Dp = 28.dp
  val ThumbHeight: Dp = 52.dp

  /** Quanto la maniglia sta lontana dal bordo. */
  val EdgeInset: Dp = 4.dp

  /** Fra la maniglia e la bolla. */
  val BubbleGap: Dp = 10.dp

  /** Quanto la maniglia cresce sotto il dito (la goccia: scala e lente dalla ricetta). */
  const val PressedScale: Float = 1.15f

  /** La striscia che lo scorrimento rapido offre a TalkBack, sempre, anche con la maniglia nascosta. */
  val AccessibilityStrip: Dp = 24.dp
}

/**
 * Lo **scorrimento rapido**: una maniglia di vetro sul bordo di una lista lunga che la porta in un colpo
 * dove si vuole, con una bolla che dice dov'e' si e' arrivati.
 *
 *  - **Compare quando serve**: mentre la lista scorre e per un momento dopo, mai a lista ferma (sarebbe
 *    arredamento), e **non prende i tocchi finche' non si vede** — un dito sul bordo di una lista senza
 *    maniglia scorre la lista, e basta.
 *  - **Il dito e' 1:1**: la maniglia si sposta di quanto si sposta il dito, la lista va dove va la
 *    maniglia ([FluidFastScroll.scrubbedFraction]). Non salta sotto il dito quando la si prende.
 *  - **E' una goccia** (ricetta della goccia, `glass/interaction/GlassDrop.kt`): a riposo e' piena, sotto
 *    il dito diventa lente e cresce; senza fondale (o sotto API 31) e' una pastiglia dipinta.
 *  - **La bolla** ([label] dell'indice sotto il dito: «Marzo 2024», «M») e' un pannello flottante, con
 *    l'ottica e la pellicola di tutti (`GlassDefaults.panelOptics` / `panelTint`), accanto alla maniglia e
 *    solo mentre la si trascina.
 *  - **Un momento, un colpo**: `GestureStart` quando la si prende, un `Tick` per ogni etichetta
 *    attraversata (non per ogni pixel, non per ogni elemento: se tre righe hanno la stessa etichetta si
 *    sente una volta), niente al rilascio.
 *  - **Per TalkBack e' un cursore** sempre presente (anche a maniglia nascosta) che dice l'etichetta
 *    come stato e salta con `setProgress`.
 *
 * Va **sopra** la lista, in un `Box` che le fa da genitore (o nello slot `overlay` di `FluidScreen`): non e'
 * un modificatore, perche' ha una geometria sua. Occupa tutto il genitore e **non prende tocchi** se non
 * quelli della maniglia; si accorcia con un `Modifier.padding` (sotto una barra, sopra una tab bar). Il
 * [backdrop] va passato solo se si disegna dal `overlay` di `FluidScreen`: una maniglia dentro il corpo
 * registrato campionerebbe se stessa.
 *
 * @param target la lista ([rememberFluidFastScrollTarget]), o i due overload con lo stato.
 * @param label l'etichetta di un elemento, dal suo indice: il mese, la lettera iniziale. Null o vuota: la
 *   bolla non c'e' (e non suona niente) per quel punto.
 * @param minItems sotto questa lunghezza non compare; [FluidFastScrollerDefaults.MinItems].
 * @param contentDescription il nome del cursore per TalkBack.
 */
@Composable
fun FluidFastScroller(
  target: FluidFastScrollTarget,
  label: (index: Int) -> String?,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState? = null,
  minItems: Int = FluidFastScrollerDefaults.MinItems,
  contentDescription: String = "Scorrimento rapido",
) {
  val enabled by remember(target, minItems) {
    derivedStateOf { FluidFastScroll.isScrollable(target.totalItems, target.visibleItems, minItems) }
  }
  if (!enabled) return

  val scope = rememberCoroutineScope()
  val haptics = rememberFluidHaptics()
  val density = LocalDensity.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val currentLabel by rememberUpdatedState(label)
  val scheme = MaterialTheme.colorScheme
  val panelTint = GlassDefaults.panelTint()

  // Il dito, mentre c'e': dove sta la maniglia (frazione) e quale etichetta ha attraversato. Lo stato e' di
  // chi guida, la lista lo segue: leggere la posizione dalla lista durante il gesto farebbe correre la
  // maniglia dietro il dito di un fotogramma, e a dita veloci la si vedrebbe staccarsi.
  var scrubbing by remember { mutableStateOf(false) }
  var scrubFraction by remember { mutableFloatStateOf(0f) }
  var scrubLabel by remember { mutableStateOf<String?>(null) }
  // 0 = nascosta, 1 = tutta. Una sola dissolvenza, letta nel disegno.
  val appearance = remember { Animatable(0f) }

  // La posizione della lista che si vede: dal dito mentre lo si tiene, dalla lista altrimenti.
  val listFraction = {
    FluidFastScroll.fraction(target.fractionalFirstIndex, target.totalItems, target.visibleItems)
  }
  val thumbFraction: () -> Float = { if (scrubbing) scrubFraction else listFraction() }

  // L'ultima richiesta, e basta: scorrere a ogni evento del dito in una coroutine ciascuna le metteva in
  // coda, e la lista arrivava dove il dito era mezzo secondo prima. Un canale conflated tiene l'ultima.
  val requests = remember(target) { Channel<Float>(Channel.CONFLATED) }
  LaunchedEffect(target) {
    for (fraction in requests) {
      target.scrollToIndex(
        FluidFastScroll.indexForFraction(fraction, target.totalItems, target.visibleItems),
      )
    }
  }

  // Compare quando la lista si muove o la si tiene; sparisce dopo un momento di quiete, mai col dito su.
  LaunchedEffect(target, reducedMotion) {
    snapshotFlow { target.isScrollInProgress || scrubbing }.collectLatest { active ->
      if (active) {
        if (reducedMotion) appearance.snapTo(1f) else appearance.animateTo(1f, FluidMotion.fadeIn(160))
      } else {
        delay(FluidMotion.FastScrollerHideMillis)
        if (reducedMotion) appearance.snapTo(0f) else appearance.animateTo(0f, FluidMotion.fadeOut(300))
      }
    }
  }

  // La maniglia e' una goccia: la pressione, la scala e lo squash dalla velocita' sono le molle della
  // ricetta. La sua posizione no: quella e' del dito (o della lista) e passa dal layer, mai dalla molla.
  val drag = remember(scope) {
    GlassDragAnimation(
      animationScope = scope,
      initialValue = 0f,
      valueRange = 0f..1f,
      visibilityThreshold = 0.001f,
      initialScale = 1f,
      pressedScale = FluidFastScrollerDefaults.PressedScale,
    )
  }
  val glassThumb = backdrop != null && isRenderEffectSupported()

  // Si prende solo quello che si vede: a maniglia nascosta il bordo e' della lista.
  val grabbable by remember { derivedStateOf { appearance.value > GrabbableFrom } }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val trackPx = constraints.maxHeight.toFloat()
    val thumbPx = with(density) { FluidFastScrollerDefaults.ThumbHeight.toPx() }
    val travel = FluidFastScroll.travel(trackPx, thumbPx)
    val slidePx = with(density) { SlideIn.toPx() }

    // La striscia per TalkBack: sempre li', e senza tocchi (nessun modificatore di puntatore: quelli
    // passano alla lista sotto).
    Box(
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .fillMaxHeight()
        .width(FluidFastScrollerDefaults.AccessibilityStrip)
        .semantics {
          this.contentDescription = contentDescription
          stateDescription = currentLabel(target.firstIndex).orEmpty()
          progressBarRangeInfo = ProgressBarRangeInfo(thumbFraction().coerceIn(0f, 1f), 0f..1f)
          setProgress { requested ->
            requests.trySend(requested.coerceIn(0f, 1f))
            true
          }
        },
    )

    // La bolla, a sinistra della maniglia, solo mentre la si trascina.
    AnimatedVisibility(
      visible = scrubbing && !scrubLabel.isNullOrBlank(),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .layout { measurable, constraints ->
          val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity))
          val centre = thumbFraction() * travel + thumbPx / 2f
          val gap = with(density) {
            (FluidFastScrollerDefaults.ThumbWidth + FluidFastScrollerDefaults.EdgeInset + FluidFastScrollerDefaults.BubbleGap)
              .roundToPx()
          }
          layout(0, placeable.height) {
            placeable.place(-(placeable.width + gap), (centre - placeable.height / 2f).roundToInt())
          }
        },
      enter = fadeIn(FluidMotion.fadeIn(110)) + scaleIn(initialScale = 0.94f, animationSpec = FluidMotion.snappy()),
      exit = fadeOut(FluidMotion.fadeOut(100)) + scaleOut(targetScale = 0.96f, animationSpec = FluidMotion.snappy()),
    ) {
      Text(
        text = scrubLabel.orEmpty(),
        modifier = Modifier
          .then(
            if (backdrop != null && isRenderEffectSupported()) {
              // Un pannello flottante come tutti: la pellicola e l'ottica sono quelle di ogni pannello,
              // compatto (e' una pastiglia) e sopra una pagina che nessuno scrim ha sfocato, quindi il
              // testo che passa dietro lo deve nascondere la sfocatura.
              Modifier.glassSurface(
                state = backdrop,
                tint = panelTint,
                shape = FluidCapsuleShape,
                role = GlassRole.Modal,
                optics = GlassDefaults.panelOptics(compact = true, overSharpPage = true),
              )
            } else {
              Modifier
                .clip(FluidCapsuleShape)
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
            },
          )
          .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    // La maniglia: la traslazione nel layer, PRIMA della goccia (che ha la sua scala nel proprio
    // `layerBlock`, e un layer scalato sopra un vetro rifrange cio' che non c'e' piu').
    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(end = FluidFastScrollerDefaults.EdgeInset)
        .graphicsLayer {
          val amount = if (scrubbing) 1f else appearance.value
          alpha = amount
          translationY = thumbFraction() * travel
          translationX = (1f - amount) * slidePx
        }
        .then(
          if (glassThumb) {
            Modifier.glassDrop(
              backdrop = backdrop!!.backdrop,
              drag = drag,
              lensSize = GlassDropLens.Slider,
              squashDivisor = GlassDropRecipe.SquashDivisor,
              squashSigned = false,
              reducedMotion = { reducedMotion },
              shape = FluidCapsuleShape,
            )
          } else {
            Modifier
              .graphicsLayer {
                val swell = 1f + (FluidFastScrollerDefaults.PressedScale - 1f) * drag.pressProgress
                scaleX = swell
                scaleY = swell
              }
              .clip(FluidCapsuleShape)
              .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
          },
        )
        .size(FluidFastScrollerDefaults.ThumbWidth, FluidFastScrollerDefaults.ThumbHeight)
        .then(
          if (grabbable || scrubbing) {
            Modifier.pointerInput(target, travel) {
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Un dito che ha la maniglia non e' di nessun altro: ne' della lista sotto, ne' del
                // gesto "indietro" della pagina.
                down.consume()
                val start = listFraction()
                var dragged = 0f
                scrubFraction = start
                scrubLabel = currentLabel(FluidFastScroll.indexForFraction(start, target.totalItems, target.visibleItems))
                scrubbing = true
                drag.press()
                haptics.play(FluidHapticEvent.GestureStart)
                try {
                  while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    // Il movimento prima del consumo: dopo, `positionChange()` vale zero.
                    val moved = change.positionChangeIgnoreConsumed().y
                    change.consume()
                    if (change.changedToUpIgnoreConsumed() || !change.pressed) break
                    dragged += moved
                    val fraction = FluidFastScroll.scrubbedFraction(start, dragged, travel)
                    scrubFraction = fraction
                    drag.updateValue(fraction)
                    requests.trySend(fraction)
                    val next = currentLabel(
                      FluidFastScroll.indexForFraction(fraction, target.totalItems, target.visibleItems),
                    )
                    if (FluidFastScroll.labelCrossed(scrubLabel, next)) haptics.play(FluidHapticEvent.Tick)
                    scrubLabel = next
                  }
                } finally {
                  scrubbing = false
                  drag.release()
                }
              }
            }
          } else {
            Modifier
          },
        ),
      contentAlignment = Alignment.Center,
    ) {
      FluidIcon(
        imageVector = FluidIcons.UnfoldMore,
        contentDescription = null,
        modifier = Modifier.size(ThumbGlyph),
        tint = scheme.onSurface.copy(alpha = 0.72f),
      )
    }
  }
}

/** [FluidFastScroller] su una `LazyColumn` o `LazyRow`. */
@Composable
fun FluidFastScroller(
  state: LazyListState,
  label: (index: Int) -> String?,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState? = null,
  minItems: Int = FluidFastScrollerDefaults.MinItems,
  contentDescription: String = "Scorrimento rapido",
) {
  FluidFastScroller(rememberFluidFastScrollTarget(state), label, modifier, backdrop, minItems, contentDescription)
}

/** [FluidFastScroller] su una `LazyVerticalGrid`. */
@Composable
fun FluidFastScroller(
  state: LazyGridState,
  label: (index: Int) -> String?,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState? = null,
  minItems: Int = FluidFastScrollerDefaults.MinItems,
  contentDescription: String = "Scorrimento rapido",
) {
  FluidFastScroller(rememberFluidFastScrollTarget(state), label, modifier, backdrop, minItems, contentDescription)
}

/** Da quando la maniglia si prende: appena si vede, non a meta' dissolvenza di un'uscita. */
private const val GrabbableFrom = 0.3f

/** Di quanto la maniglia entra di lato mentre compare. */
private val SlideIn = 12.dp

/** L'icona sulla maniglia. */
private val ThumbGlyph = 22.dp
