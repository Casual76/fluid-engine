package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Il visualizzatore di immagini (3.0, fase 12c). Tre app ne avevano uno, tutti dentro un `Dialog` di
// piattaforma: `ImageViewer` di PampAI (pizzico e doppio tocco, un tocco chiude), `PhotoViewer` di Codex e
// `ZoomableMedia` di MyCloud (lo zoom dentro un pager). Nessuno dei tre aveva i limiti del pan —
// l'immagine si poteva spingere fuori dallo schermo —, e tutti e tre passavano il dito al pager o al
// congedo con la regola «se non e' zoomato, non consumo» scritta a mano e in modo diverso.

/**
 * I conti dello zoom, puri e provati (`FluidZoomTest`): quanto si puo' ingrandire, dove l'immagine si
 * puo' spingere, dove si deve spostare per tenere fermo il punto che si pizzica.
 *
 * Il sistema di riferimento e' **il centro del contenitore**: l'immagine, a scala 1, e' centrata, e la
 * posizione di un suo punto `q` (rispetto al centro) e' `scala * q + traslazione`. E' lo stesso modo in cui un
 * `graphicsLayer` scala (attorno al centro) e trasla (dopo la scala): i numeri di qui sono quelli che il layer
 * usa, senza una conversione in mezzo.
 */
object FluidZoom {

  /** Piu' piccola di cosi' l'immagine non va: a scala 1 sta tutta nel contenitore. */
  const val MinScale: Float = 1f

  /** Quanto si puo' ingrandire, di serie: quattro volte l'immagine adattata al contenitore. */
  const val DefaultMaxScale: Float = 4f

  /** Dove porta un doppio tocco, partendo da scala 1. */
  const val DoubleTapScale: Float = 2.5f

  /** Sotto questa differenza da 1 l'immagine non e' "ingrandita": un pizzico lasciato a 1,004 non toglie il dito al pager. */
  private const val ZoomedEpsilon = 0.01f

  fun isZoomed(scale: Float): Boolean = scale > MinScale + ZoomedEpsilon

  /** La scala tenuta fra [MinScale] e [maxScale] (mai sotto 1, anche se qualcuno passa un massimo minore). */
  fun clampScale(scale: Float, maxScale: Float = DefaultMaxScale): Float =
    scale.coerceIn(MinScale, max(MinScale, maxScale))

  /**
   * La taglia a cui l'immagine sta, a scala 1, dentro [container] **adattata** (tutta visibile, senza
   * tagliare): da qui si vede quanto e' grande davvero, e quindi fin dove si puo' spingere.
   *
   * Senza il rapporto d'aspetto ([aspectRatio] nullo o non positivo) l'immagine e' il contenitore intero: i
   * limiti sono quelli del riquadro, e un'immagine piu' stretta lascia le sue bande vuote spingibili. E'
   * il caso di chi non sa le misure del contenuto, e il motivo per cui si possono passare.
   */
  fun fitSize(container: Size, aspectRatio: Float?): Size {
    if (aspectRatio == null || aspectRatio <= 0f || container.width <= 0f || container.height <= 0f) return container
    val containerRatio = container.width / container.height
    return if (aspectRatio > containerRatio) {
      Size(container.width, container.width / aspectRatio)
    } else {
      Size(container.height * aspectRatio, container.height)
    }
  }

  /**
   * Di quanto si puo' spingere l'immagine, per asse: quanto la sua taglia in scala eccede il contenitore,
   * diviso due (la meta' per lato). **Zero** se in scala sta ancora dentro: un'immagine piu' piccola del
   * contenitore resta al centro, non si porta in un angolo.
   */
  fun maxPan(container: Size, content: Size, scale: Float): Offset = Offset(
    x = max(0f, (content.width * scale - container.width) / 2f),
    y = max(0f, (content.height * scale - container.height) / 2f),
  )

  /** [pan] tenuto dentro [maxPan]: l'immagine non lascia mai uno spazio vuoto fra il suo bordo e quello dello schermo. */
  fun clampPan(pan: Offset, container: Size, content: Size, scale: Float): Offset {
    val limit = maxPan(container, content, scale)
    // `+ 0f`: con un limite zero `coerceIn(-0f, 0f)` puo' tornare -0f, che non e' lo stesso `Offset` di zero.
    return Offset(pan.x.coerceIn(-limit.x, limit.x) + 0f, pan.y.coerceIn(-limit.y, limit.y) + 0f)
  }

  /**
   * La traslazione nuova quando la scala passa da [scale] a [newScale] **tenendo fermo il punto [focal]**
   * (rispetto al centro): il punto dell'immagine sotto le dita resta sotto le dita. E' la ragione per cui un
   * pizzico ingrandisce *li'* e non verso il centro. Il punto fermo `q = (focal - pan) / scale` va a
   * `focal = newScale * q + newPan`, quindi `newPan = focal - (newScale / scale) * (focal - pan)`.
   */
  fun zoomAbout(scale: Float, pan: Offset, newScale: Float, focal: Offset): Offset {
    if (scale <= 0f) return pan
    val ratio = newScale / scale
    return Offset(focal.x - ratio * (focal.x - pan.x), focal.y - ratio * (focal.y - pan.y))
  }

  /** Dove porta un doppio tocco: da ingrandita torna a 1, da 1 va a [tapScale] (mai oltre [maxScale]). */
  fun doubleTapTarget(
    scale: Float,
    maxScale: Float = DefaultMaxScale,
    tapScale: Float = DoubleTapScale,
  ): Float = if (isZoomed(scale)) MinScale else clampScale(tapScale, maxScale)

  /**
   * Il livello dopo [scale] per i comandi a gradini (le azioni per TalkBack, la tastiera): 1, [DoubleTapScale],
   * [maxScale]. Ingrandire da un livello va al successivo, ridurre al precedente; dagli estremi non si muove.
   */
  fun step(scale: Float, zoomIn: Boolean, maxScale: Float = DefaultMaxScale): Float {
    val levels = listOf(MinScale, clampScale(DoubleTapScale, maxScale), clampScale(maxScale, maxScale)).distinct()
    return if (zoomIn) {
      levels.firstOrNull { it > scale + ZoomedEpsilon } ?: levels.last()
    } else {
      levels.lastOrNull { it < scale - ZoomedEpsilon } ?: levels.first()
    }
  }
}

/**
 * Lo stato dello zoom di un'immagine: la scala e lo spostamento, e come cambiano.
 *
 * Uno per immagine ([rememberFluidZoomState]). La scala e lo spostamento sono stato di snapshot e si leggono
 * nel `layerBlock` di [fluidZoomable]: il pizzico non ricompone niente.
 */
@Stable
class FluidZoomState internal constructor(
  /** L'ingrandimento massimo. */
  val maxScale: Float,
  private val scope: CoroutineScope,
  private val reducedMotion: () -> Boolean,
) {
  var scale: Float by mutableFloatStateOf(FluidZoom.MinScale)
    private set
  var panX: Float by mutableFloatStateOf(0f)
    private set
  var panY: Float by mutableFloatStateOf(0f)
    private set

  /** La taglia del contenitore: la scrive [fluidZoomable] a ogni misura. */
  internal var containerSize: Size = Size.Zero

  /** Il rapporto d'aspetto del contenuto, se lo si conosce; vedi [FluidZoom.fitSize]. */
  internal var contentAspectRatio: Float? = null

  private var animation: Job? = null

  val isZoomed: Boolean get() = FluidZoom.isZoomed(scale)

  private val contentSize: Size get() = FluidZoom.fitSize(containerSize, contentAspectRatio)

  /** Torna a scala 1, senza animazione: per un'immagine che esce di scena. */
  fun reset() {
    animation?.cancel()
    scale = FluidZoom.MinScale
    panX = 0f
    panY = 0f
  }

  /**
   * Applica un passo del gesto: [zoom] (fattore) e [pan] (spostamento delle dita) attorno a [focal]
   * (rispetto al centro). Vero se qualcosa si e' mosso: chi legge il gesto lo consuma solo allora.
   */
  internal fun transformBy(zoom: Float, pan: Offset, focal: Offset): Boolean {
    animation?.cancel()
    val newScale = FluidZoom.clampScale(scale * zoom, maxScale)
    val anchored = FluidZoom.zoomAbout(scale, Offset(panX, panY), newScale, focal) + pan
    val clamped = FluidZoom.clampPan(anchored, containerSize, contentSize, newScale)
    val moved = newScale != scale || clamped.x != panX || clamped.y != panY
    scale = newScale
    panX = clamped.x
    panY = clamped.y
    return moved
  }

  /**
   * Porta la scala a [target] tenendo fermo il punto [focal]: un doppio tocco, un gradino. Con le animazioni
   * ridotte arriva subito; altrimenti una molla critica (un'immagine che ingrandisce non rimbalza).
   */
  fun zoomTo(target: Float, focal: Offset = Offset.Zero) {
    animation?.cancel()
    val startScale = scale
    val startPan = Offset(panX, panY)
    val end = FluidZoom.clampScale(target, maxScale)
    val apply = { progress: Float ->
      val s = startScale + (end - startScale) * progress
      val pan = FluidZoom.clampPan(
        FluidZoom.zoomAbout(startScale, startPan, s, focal),
        containerSize,
        contentSize,
        s,
      )
      scale = s
      panX = pan.x
      panY = pan.y
    }
    if (reducedMotion()) {
      apply(1f)
      return
    }
    animation = scope.launch {
      animate(0f, 1f, animationSpec = FluidMotion.snappy()) { value, _ -> apply(value) }
    }
  }

  /** Il doppio tocco: da ingrandita torna a 1, da 1 va a [FluidZoom.DoubleTapScale], attorno al punto toccato. */
  internal fun toggleAt(focal: Offset) = zoomTo(FluidZoom.doubleTapTarget(scale, maxScale), focal)

  /** Un gradino di zoom (azioni per TalkBack): attorno al centro. */
  internal fun stepZoom(zoomIn: Boolean) = zoomTo(FluidZoom.step(scale, zoomIn, maxScale))
}

/** Lo stato dello zoom di un'immagine. */
@Composable
fun rememberFluidZoomState(maxScale: Float = FluidZoom.DefaultMaxScale): FluidZoomState {
  val scope = rememberCoroutineScope()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val currentReduced by rememberUpdatedState(reduced)
  return remember(maxScale, scope) { FluidZoomState(maxScale, scope) { currentReduced } }
}

/**
 * Rende ingrandibile quello che il modificatore avvolge: pizzico, doppio tocco, spostamento limitato al
 * contenuto.
 *
 * **Di chi e' il dito.** E' la parte che i tre visualizzatori delle app sbagliavano:
 *
 *  - a **scala 1 con un dito solo** non consuma niente: lo scorrimento laterale e' del pager, quello verso il
 *    basso e' del congedo (il foglio che segue il dito);
 *  - con **due dita** consuma sempre: un pizzico e' dell'immagine e di nessun altro;
 *  - da **ingrandita** con un dito sposta l'immagine, e consuma il gesto solo se l'immagine si e' mossa — o se
 *    e' verticale, perche' un'immagine ingrandita non si chiude trascinando giu' per sbaglio: al bordo laterale
 *    il resto del gesto passa al pager, e si cambia pagina.
 *
 * Il doppio tocco alterna 1 e [FluidZoom.DoubleTapScale] attorno al punto toccato, e un tocco solo va a [onTap] se
 * c'e'. Dopo un pizzico o uno spostamento l'immagine non lascia mai un vuoto fra il suo bordo e quello del
 * contenitore ([FluidZoom.clampPan]).
 *
 * **Accessibilita'**: due azioni, «Ingrandisci» e «Riduci» ([FluidImageViewerStrings]), a gradini, e lo
 * zoom come stato («Zoom 250%»).
 *
 * Va messo su un nodo che ha le misure del contenitore (il contenuto, dentro, e' adattato).
 *
 * @param contentAspectRatio larghezza su altezza del contenuto, se si sa: i limiti dello spostamento
 *   seguono l'immagine e non il riquadro. Nullo: il riquadro intero.
 */
@Composable
fun Modifier.fluidZoomable(
  state: FluidZoomState,
  contentAspectRatio: Float? = null,
  enabled: Boolean = true,
  onTap: (() -> Unit)? = null,
  strings: FluidImageViewerStrings = FluidImageViewerStrings(),
): Modifier {
  state.contentAspectRatio = contentAspectRatio
  val haptics = rememberFluidHaptics()
  val currentOnTap by rememberUpdatedState(onTap)
  val hasTap = onTap != null
  return this
    .onSizeChanged { state.containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
    .semantics {
      stateDescription = strings.zoomState((state.scale * 100f).roundToInt())
      if (enabled) {
        customActions = listOf(
          CustomAccessibilityAction(strings.zoomIn) { state.stepZoom(zoomIn = true); true },
          CustomAccessibilityAction(strings.zoomOut) { state.stepZoom(zoomIn = false); true },
        )
      }
    }
    // I gesti fuori dal layer, nelle coordinate del contenitore: dentro il layer ingrandito il dito, visto
    // dall'immagine, correrebbe a una velocita' diversa da quella a cui si muove sullo schermo.
    .then(
      if (enabled) {
        Modifier
          .pointerInput(state, hasTap) {
            detectTapGestures(
              onDoubleTap = { at ->
                // Un doppio tocco e' un momento: un colpo, quando l'immagine si ingrandisce.
                haptics.play(FluidHapticEvent.Tap)
                state.toggleAt(Offset(at.x - size.width / 2f, at.y - size.height / 2f))
              },
              onTap = if (hasTap) ({ _: Offset -> currentOnTap?.invoke() }) else null,
            )
          }
          .pointerInput(state) { zoomGesture(state) }
      } else {
        Modifier
      },
    )
    .graphicsLayer {
      scaleX = state.scale
      scaleY = state.scale
      translationX = state.panX
      translationY = state.panY
    }
}

/** Il pizzico e lo spostamento: vedi [fluidZoomable] per di chi e' il dito. */
private suspend fun PointerInputScope.zoomGesture(state: FluidZoomState) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false)
    do {
      val event = awaitPointerEvent()
      val cancelled = event.changes.any { it.isConsumed }
      if (!cancelled) {
        val fingers = event.changes.count { it.pressed }
        if (fingers >= 2 || state.isZoomed) {
          val zoom = event.calculateZoom()
          val pan = event.calculatePan()
          val centroid = event.calculateCentroid(useCurrent = true)
          val focal = if (centroid.isSpecified) {
            Offset(centroid.x - size.width / 2f, centroid.y - size.height / 2f)
          } else {
            Offset.Zero
          }
          val moved = state.transformBy(zoom, pan, focal)
          val verticalHold = fingers < 2 && abs(pan.y) >= abs(pan.x)
          if (moved || fingers >= 2 || verticalHold) {
            event.changes.forEach { if (it.positionChanged()) it.consume() }
          }
        }
      }
    } while (!cancelled && event.changes.any { it.pressed })
  }
}

/** Le parole del visualizzatore, in italiano di serie. */
@Immutable
data class FluidImageViewerStrings(
  val zoomIn: String = "Ingrandisci",
  val zoomOut: String = "Riduci",
  val zoomState: (percent: Int) -> String = { "Zoom $it%" },
)

/**
 * Il visualizzatore di immagini a tutto schermo, **dentro l'host dei modali** dell'engine: una o piu'
 * immagini, pizzico e doppio tocco per ingrandire, spostamento limitato all'immagine, congedo trascinando
 * in giu'.
 *
 * Non carica niente: [page] e' l'immagine che l'app disegna (con Coil, con un `Bitmap`, un `Image` di
 * Compose), e il visualizzatore la ingrandisce e la sposta. Nessuna libreria di immagini nell'engine.
 *
 * **Dove sta e perche'.** E' un [FluidGlassModalPortal] nella presentazione `FullScreen`, e tutto cio' che e'
 * del modale arriva da li' invece di essere riscritto: il tasto indietro (anche predittivo), il fuoco, la
 * pagina dietro nascosta ai servizi di accessibilita', la chiusura con il tasto in alto, e il **congedo
 * trascinando in giu'**, in cui la pagina intera segue il dito e al rilascio parte dalla sua velocita' (si
 * chiude oltre i 110 dp o con una spinta, altrimenti torna con una molla). Il visualizzatore sta nello slot
 * dei tasti fermi, **non nel corpo**: il corpo di una pagina intera scorre, e un contenuto che deve
 * riempire lo spazio non ha un'altezza da scorrere — il piede ne ha una vera, quello che resta sotto la
 * barra.
 *
 * **Di chi e' il dito**: vedi [fluidZoomable]. A scala 1 il gesto verso il basso non e' dell'immagine, e il
 * foglio lo prende da solo; da ingrandita no, e un pizzico lasciato a meta' non chiude niente.
 *
 * **Quello che non fa**, e il perche': lo sfondo e' la pagina opaca dell'host (non nera, il tema), e lo
 * scurimento dietro **non** e' proporzionale al trascinamento. Il fondale di un modale pagina intera e' un
 * velo fisso, e una presentazione con uno sfondo che si schiarisce col dito vorrebbe un'aggiunta all'host
 * (`FluidGlassModal.kt`), non un giro qui. Senza un `FluidGlassModalHost` in scope non si apre e non va in
 * crash (e' la regola del portale).
 *
 * @param pageCount quante immagini; con piu' di una c'e' il pager e il titolo «2 di 5».
 * @param initialPage da quale partire, a ogni apertura.
 * @param onPageChange la pagina in cui ci si e' fermati.
 * @param title il titolo della barra per la pagina corrente (il contatore, di serie); nullo: nessun titolo.
 * @param maxScale l'ingrandimento massimo.
 * @param contentAspectRatio il rapporto d'aspetto dell'immagine di una pagina, se si sa: i limiti dello spostamento
 *   seguono l'immagine (vedi [FluidZoom.fitSize]).
 * @param page l'immagine della pagina `n`: riempie il contenitore, e il visualizzatore la adatta.
 */
@Composable
fun FluidImageViewer(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  pageCount: Int = 1,
  initialPage: Int = 0,
  onPageChange: (page: Int) -> Unit = {},
  title: (page: Int, pageCount: Int) -> String? = { page, count -> if (count > 1) "${page + 1} di $count" else null },
  maxScale: Float = FluidZoom.DefaultMaxScale,
  contentAspectRatio: (page: Int) -> Float? = { null },
  strings: FluidImageViewerStrings = FluidImageViewerStrings(),
  page: @Composable (page: Int) -> Unit,
) {
  if (pageCount <= 0) return
  val start = initialPage.coerceIn(0, pageCount - 1)
  val pagerState = rememberPagerState(initialPage = start, pageCount = { pageCount })
  val currentOnPageChange by rememberUpdatedState(onPageChange)

  // A ogni apertura si riparte dalla pagina chiesta: il pager e' del chiamante, e vive anche a visualizzatore chiuso.
  LaunchedEffect(visible, start) {
    if (visible && pagerState.currentPage != start) pagerState.scrollToPage(start)
  }
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }.collect { currentOnPageChange(it) }
  }

  FluidGlassModalPortal(
    visible = visible,
    onDismissRequest = onDismissRequest,
    presentation = FluidGlassModalPresentation.FullScreen,
    paneTitle = title(pagerState.currentPage, pageCount),
    footer = {
      FluidImageViewerPager(
        pagerState = pagerState,
        maxScale = maxScale,
        contentAspectRatio = contentAspectRatio,
        strings = strings,
        modifier = modifier,
        page = page,
      )
    },
    content = {},
  )
}

@Composable
private fun FluidImageViewerPager(
  pagerState: androidx.compose.foundation.pager.PagerState,
  maxScale: Float,
  contentAspectRatio: (Int) -> Float?,
  strings: FluidImageViewerStrings,
  modifier: Modifier,
  page: @Composable (Int) -> Unit,
) {
  HorizontalPager(
    state = pagerState,
    modifier = modifier.fillMaxSize(),
    key = { it },
  ) { index ->
    val zoom = rememberFluidZoomState(maxScale)
    // Una pagina che si lascia indietro torna a scala 1: tornarci e trovarla ingrandita a meta' e' una
    // sorpresa, e il pager la tiene viva (e ingrandita) finche' non esce dalla finestra.
    LaunchedEffect(pagerState.settledPage) {
      if (pagerState.settledPage != index) zoom.reset()
    }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .fluidZoomable(zoom, contentAspectRatio = contentAspectRatio(index), strings = strings),
      contentAlignment = Alignment.Center,
    ) {
      page(index)
    }
  }
}
