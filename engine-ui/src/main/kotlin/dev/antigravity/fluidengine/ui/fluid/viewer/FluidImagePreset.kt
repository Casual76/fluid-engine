package dev.antigravity.fluidengine.ui.fluid.viewer

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import dev.antigravity.fluidengine.ui.fluid.FluidZoom
import dev.antigravity.fluidengine.ui.fluid.FluidZoomState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidZoomState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import kotlin.math.roundToInt

// F6C.2 · Il preset immagine e il raccordo fra una pagina del visore e chi la disegna.
//
// Il telaio di F6C.1 e FluidImageViewer (l'overlay di prima, con la firma di prima) usano la stessa pagina: lo zoom
// di FluidZoom (4×, doppio tocco a 2,5×), la regola di chi possiede il dito di fluidZoomable, la pagina lasciata che
// torna a scala 1. Il congedo resta di chi presenta: oltre 110 dp (FluidSheetDismissTravel) nel foglio del modale e
// nella rotta del telaio, che lo misurano allo stesso modo. Il preset non decodifica niente: un'immagine animata
// arriva già pronta (engine-viewer di F6C.4 con ImageDecoder, oppure Coil o Glide dell'app).

/**
 * Ciò che il visore dice al contenuto di una sua pagina: l'indice, se è la pagina ferma, lo zoom che le applica e
 * la barra in cui un preset può dichiarare i propri comandi. Lo forniscono il telaio e [FluidImageViewer].
 */
@Stable
internal class ViewerPageScope(
  val index: Int,
  private val settled: State<Boolean>,
  val zoom: FluidZoomState,
  val image: Boolean,
  val bar: ViewerPresetBar?,
) {
  val active: Boolean get() = settled.value
}

internal val LocalViewerPage = compositionLocalOf<ViewerPageScope?> { null }

/**
 * I comandi che il preset della pagina `i` aggiunge alla barra del visore («Trova», «A capo»). La barra li chiede per
 * la pagina corrente; una pagina che esce dalla composizione li ritira.
 */
@Stable
internal class ViewerPresetBar {
  private val blocks = mutableStateMapOf<Int, FluidToolbarScope.() -> Unit>()

  fun of(index: Int): (FluidToolbarScope.() -> Unit)? = blocks[index]

  fun set(index: Int, block: FluidToolbarScope.() -> Unit) {
    blocks[index] = block
  }

  fun remove(index: Int, block: FluidToolbarScope.() -> Unit) {
    if (blocks[index] === block) blocks.remove(index)
  }
}

/**
 * I comandi del preset di questa pagina nella barra del visore. Fuori da una pagina del telaio non fa niente (il preset
 * mostra solo il contenuto). La barra legge [block] in composizione: lo stato letto dentro (la parola cercata, «a
 * capo») la ricompone quando cambia.
 */
@Composable
internal fun ViewerPresetToolbar(block: FluidToolbarScope.() -> Unit) {
  val page = LocalViewerPage.current
  val bar = page?.bar
  val current = rememberUpdatedState(block)
  val stable: FluidToolbarScope.() -> Unit = remember(current) { { current.value(this) } }
  DisposableEffect(bar, page?.index, stable) {
    if (bar != null && page != null) bar.set(page.index, stable)
    onDispose { if (bar != null && page != null) bar.remove(page.index, stable) }
  }
}

/**
 * Lo stato di una pagina: uno zoom per pagina che torna a scala 1 quando la pagina viene lasciata (tornarci e trovarla
 * ingrandita a metà è una sorpresa, e il pager la tiene viva finché non esce dalla finestra), e il «ferma» letto dal
 * pager. È la stessa regola nel telaio e in [FluidImageViewer].
 */
@Composable
internal fun rememberViewerPage(
  pager: PagerState,
  page: Int,
  image: Boolean,
  bar: ViewerPresetBar?,
  maxScale: Float = FluidZoom.DefaultMaxScale,
): ViewerPageScope {
  val zoom = rememberFluidZoomState(maxScale)
  LaunchedEffect(pager.settledPage) {
    if (pager.settledPage != page) zoom.reset()
  }
  val settled = remember(pager, page) { derivedStateOf { pager.settledPage == page } }
  return remember(page, settled, zoom, image, bar) { ViewerPageScope(page, settled, zoom, image, bar) }
}

/**
 * Dentro una pagina di [FluidFileViewer] o di [dev.antigravity.fluidengine.ui.fluid.FluidImageViewer]: vero se è la
 * pagina ferma, falso mentre il dito o il pager la portano via e per le pagine vicine. Fuori da un visore è sempre
 * vero. Un contenuto che si muove da solo (un'animazione, un video) parte e si ferma con questo valore, come in
 * Gallery-2 (F6C.0 §1).
 */
@Composable
fun currentFluidViewerPageActive(): Boolean = LocalViewerPage.current?.active ?: true

/**
 * Dentro una pagina immagine del visore: lo zoom che il visore le applica, null altrove. Un visore a tasselli
 * dell'app (Gallery-2, fino a 200 MP) passato come [FluidViewerContent.Image] legge qui [FluidZoomState.scale],
 * [FluidZoomState.panX] e [FluidZoomState.panY] per scegliere la risoluzione e i tasselli da caricare; i gesti, i
 * limiti dello spostamento e la chrome restano del visore (piano F6C, «Rischi»).
 */
@Composable
fun currentFluidViewerZoom(): FluidZoomState? = LocalViewerPage.current?.takeIf { it.image }?.zoom

/**
 * Il preset immagine dentro il telaio: disegna il contenuto dell'app. Lo zoom, il pager, il congedo e il ritorno alla
 * miniatura sono del telaio, che li applica attorno a questo corpo.
 */
@Composable
fun FluidImagePreset(file: FluidViewerContent.Image) {
  file.draw()
}

/** Le parole del preset immagine. */
@Immutable
data class FluidImagePresetStrings(
  val play: String = "Riproduci animazione",
  val pause: String = "Metti in pausa l'animazione",
)

/**
 * Un'immagine animata (GIF, WebP animata, l'animazione dell'app) come contenuto del visore. `engine-ui` non decodifica:
 * [drawable] arriva già pronto, da `engine-viewer` (F6C.4, `ImageDecoder` e `AnimatedImageDrawable`) o dall'app (Coil
 * e Glide danno drawable che implementano [Animatable]). Un drawable che non è [Animatable] resta un'immagine ferma.
 *
 * - Parte solo sulla pagina ferma ([currentFluidViewerPageActive]) e si ferma quando la si lascia, come Gallery-2.
 * - Con il movimento ridotto non parte da sola; TalkBack ha l'azione «Riproduci animazione» (e «Metti in pausa»).
 * - [still] è il fermo di ripiego (il primo fotogramma, una miniatura): si vede finché l'animazione non è partita.
 * - Zoom, pager e congedo sono quelli di ogni immagine. Il visore non possiede il drawable: lo ferma, non lo ricicla.
 *
 * @param aspectRatio larghezza su altezza; di serie dalle misure del drawable, poi da quelle di [still].
 */
fun fluidAnimatedImage(
  drawable: Drawable,
  description: String? = null,
  still: Painter? = null,
  aspectRatio: Float? = drawableAspectRatio(drawable) ?: still?.intrinsicSize?.let(::sizeAspectRatio),
  strings: FluidImagePresetStrings = FluidImagePresetStrings(),
): FluidViewerContent.Image = FluidViewerContent.Image(aspectRatio) {
  FluidAnimatedImage(drawable, description = description, still = still, strings = strings)
}

private fun drawableAspectRatio(drawable: Drawable): Float? {
  val w = drawable.intrinsicWidth
  val h = drawable.intrinsicHeight
  return if (w > 0 && h > 0) w.toFloat() / h else null
}

private fun sizeAspectRatio(size: Size): Float? =
  if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f) size.width / size.height else null

/**
 * Se l'animazione deve girare: solo sulla pagina ferma, e con il movimento ridotto solo se l'ha chiesto chi usa
 * TalkBack ([requested]). Una scelta esplicita vale anche senza movimento ridotto (la pausa resta pausa).
 */
internal fun animatedImagePlays(animatable: Boolean, active: Boolean, reducedMotion: Boolean, requested: Boolean?): Boolean =
  animatable && active && (requested ?: !reducedMotion)

/**
 * Il corpo di [fluidAnimatedImage], per chi disegna la pagina da sé: dentro una pagina di
 * [dev.antigravity.fluidengine.ui.fluid.FluidImageViewer] o in un `renderContent` del telaio. Riempie il contenitore
 * e adatta l'immagine.
 */
@Composable
fun FluidAnimatedImage(
  drawable: Drawable,
  modifier: Modifier = Modifier,
  description: String? = null,
  still: Painter? = null,
  strings: FluidImagePresetStrings = FluidImagePresetStrings(),
) {
  val active = currentFluidViewerPageActive()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  var requested by rememberSaveable { mutableStateOf<Boolean?>(null) }
  val animatable = drawable as? Animatable
  val playing = animatedImagePlays(animatable != null, active, reduced, requested)
  var started by remember(drawable) { mutableStateOf(false) }
  val painter = remember(drawable) { ViewerDrawablePainter(drawable) }
  DisposableEffect(animatable, playing) {
    if (playing) {
      animatable?.start()
      started = true
    } else {
      animatable?.stop()
    }
    onDispose { animatable?.stop() }
  }
  val shown = if (!started && still != null) still else painter
  Image(
    painter = shown,
    contentDescription = null,
    contentScale = ContentScale.Fit,
    modifier = modifier.fillMaxSize().semantics {
      if (description != null) contentDescription = description
      if (animatable != null) {
        customActions = listOf(CustomAccessibilityAction(if (playing) strings.pause else strings.play) {
          requested = !playing
          true
        })
      }
    },
  )
}

/**
 * Un [Drawable] come [Painter]: si ridisegna a ogni `invalidateSelf` del drawable (è così che un
 * `AnimatedImageDrawable` o una GIF di Glide chiedono il fotogramma dopo) e programma i suoi passi sul thread
 * principale. Il drawable resta dell'app: qui si attacca e si stacca solo il callback.
 */
internal class ViewerDrawablePainter(val drawable: Drawable) : Painter(), RememberObserver {
  private var frame by mutableIntStateOf(0)
  private val main by lazy { Handler(Looper.getMainLooper()) }

  // Il drawable tiene il callback con un riferimento debole: questo campo lo tiene vivo.
  private val callback = object : Drawable.Callback {
    override fun invalidateDrawable(who: Drawable) {
      frame++
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
      main.postAtTime(what, `when`.coerceAtLeast(SystemClock.uptimeMillis()))
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
      main.removeCallbacks(what)
    }
  }

  override val intrinsicSize: Size
    get() {
      val w = drawable.intrinsicWidth
      val h = drawable.intrinsicHeight
      return if (w > 0 && h > 0) Size(w.toFloat(), h.toFloat()) else Size.Unspecified
    }

  override fun DrawScope.onDraw() {
    frame // Letto in disegno: un fotogramma nuovo ridisegna senza ricomporre.
    drawIntoCanvas { canvas ->
      drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
      drawable.draw(canvas.nativeCanvas)
    }
  }

  override fun onRemembered() {
    drawable.callback = callback
    drawable.setVisible(true, true)
  }

  override fun onForgotten() = detach()

  override fun onAbandoned() = detach()

  private fun detach() {
    (drawable as? Animatable)?.stop()
    drawable.setVisible(false, false)
    if (drawable.callback === callback) drawable.callback = null
  }
}
