/*
 * engine-scan (4.0, F8.17): il contratto della fotocamera dello scanner e la sorgente finta, che mostra foto di fogli
 * già scattate. La fotocamera vera (CameraX) sta in FluidCameraXScanCamera.kt. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * La fotocamera dello scanner: l'anteprima, i fotogrammi per l'analisi e lo scatto. Lo scanner non sa da dove arrivano:
 * la fotocamera vera ([rememberFluidCameraXScanCamera]), oppure foto di fogli già scattate ([FluidStillScanCamera]) per
 * le prove, la galleria e un telefono senza fotocamera.
 *
 * L'anteprima e i fotogrammi coprono la stessa scena: l'anteprima la riempie tagliando i bordi (come la fotocamera di
 * iOS), e [aspect] dice le proporzioni dritte della scena intera, così lo scanner disegna gli angoli trovati nel posto
 * giusto.
 */
@Stable
interface FluidScanCamera {
  /** Larghezza su altezza della scena dritta (per esempio 3/4 in verticale). Pubblicato come stato di Compose. */
  val aspect: Float

  /** Vero quando la fotocamera dà immagini; falso mentre parte o se non c'è (allora [problem] dice perché). */
  val ready: Boolean

  /** Perché la fotocamera non c'è; `null` se va. */
  val problem: FluidScanCameraProblem? get() = null

  /** Il permesso della fotocamera potrebbe essere cambiato (lo scanner lo chiama dopo averlo chiesto). */
  fun permissionChanged() {}

  /** Vero se c'è il flash da tenere acceso (la torcia). */
  val hasTorch: Boolean get() = false

  /** La torcia accesa. */
  var torch: Boolean

  /**
   * Il disegno dell'anteprima, che riempie [modifier] tagliando i bordi. La fotocamera vera si accende quando entra in
   * composizione e si spegne quando esce.
   */
  @Composable
  fun Preview(modifier: Modifier)

  /**
   * Fa arrivare i fotogrammi d'analisi a [analyzer] finché la sospensione non viene annullata. [analyzer] gira fuori
   * dal filo principale, un fotogramma alla volta: quelli che arrivano mentre lavora si saltano.
   */
  suspend fun analyze(analyzer: (FluidScanFrame) -> Unit)

  /**
   * Scatta: la foto intera e dritta, alla risoluzione più alta che la fotocamera dà per un documento. Gira fuori dal filo
   * principale. Chi la riceve la possiede (la ricicla quando ha finito).
   */
  suspend fun capture(): Bitmap

  /**
   * Lo spostamento finto della scena, in frazioni dell'immagine: lo usa solo [FluidStillScanCamera] per far tremare la
   * mano. Il riconoscimento lo aggiunge agli angoli trovati. Zero per la fotocamera vera.
   */
  fun shake(timeMillis: Long): FluidScanPoint = FluidScanPoint(0f, 0f)
}

/** Perché la fotocamera non c'è. Non è un `enum` di proposito (regola 3.11). */
@androidx.compose.runtime.Immutable
@JvmInline
value class FluidScanCameraProblem private constructor(private val id: Int) {
  override fun toString(): String = if (id == 0) "Permission" else "Unavailable"

  companion object {
    /** Manca il permesso `CAMERA`: lo scanner offre «Consenti la fotocamera». */
    val Permission: FluidScanCameraProblem = FluidScanCameraProblem(0)
    /** Non c'è una fotocamera posteriore, o non si apre. */
    val Unavailable: FluidScanCameraProblem = FluidScanCameraProblem(1)
  }
}

/**
 * La fotocamera finta: mostra una per volta le foto di fogli in [sheets] (ognuna una funzione che la carica), con la
 * mano che trema un poco e poi si ferma, e dopo ogni scatto toglie il foglio per un momento mostrando [table] (il tavolo
 * vuoto), come chi cambia pagina. Serve alle prove, alla galleria (che non chiede la fotocamera) e alle riprese.
 *
 * Gli istanti vengono dal filo dei fotogrammi ([delay]): nei test di Compose seguono l'orologio del test.
 */
@Stable
class FluidStillScanCamera(
  private val sheets: List<suspend () -> Bitmap>,
  private val table: (suspend () -> Bitmap)? = null,
  /** Quanto trema la mano quando arriva un foglio, in frazioni dell'immagine. Zero per una scena ferma. */
  private val shakeAmount: Float = 0.025f,
  /** La cadenza dei fotogrammi d'analisi. */
  private val frameMillis: Long = 66L,
) : FluidScanCamera {
  private var index by mutableIntStateOf(0)
  private var showing by mutableStateOf<Bitmap?>(null)
  private var sheetVisible by mutableStateOf(true)
  private var arrivedAt = 0L
  private var clock = 0L
  private var current: FluidScanFrame? = null
  override var aspect by mutableFloatStateOf(3f / 4f)
    private set
  override var ready by mutableStateOf(false)
    private set
  override var torch: Boolean = false

  /** L'indice del foglio mostrato adesso (per le prove). */
  val sheetIndex: Int get() = index

  /** Quanti fogli restano da mostrare. */
  val remaining: Int get() = (sheets.size - index).coerceAtLeast(0)

  private var shakeX by mutableFloatStateOf(0f)
  private var shakeY by mutableFloatStateOf(0f)

  @Composable
  override fun Preview(modifier: Modifier) {
    val bitmap = showing
    Box(modifier.background(Color.Black)) {
      if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer {
          // Letto nel layer: la mano che trema ridisegna, non ricompone.
          translationX = shakeX * size.width
          translationY = shakeY * size.height
        })
      }
    }
  }

  override fun shake(timeMillis: Long): FluidScanPoint = FluidScanPoint(shakeX, shakeY)

  override suspend fun analyze(analyzer: (FluidScanFrame) -> Unit) {
    lastAnalyzer = analyzer
    if (showing == null) show(index)
    while (true) {
      if (pendingNext) {
        pendingNext = false
        next()
      }
      val frame = current
      val t = clock - arrivedAt
      // La mano: un'oscillazione che si spegne in circa un secondo e mezzo.
      val fade = exp(-t / 450.0).toFloat()
      val amount = if (sheetVisible) shakeAmount * fade else 0f
      shakeX = amount * sin(2 * PI * t / 700.0).toFloat()
      shakeY = amount * 0.6f * sin(2 * PI * t / 530.0 + 1.0).toFloat()
      if (frame != null) withContext(Dispatchers.Default) { analyzer(FluidScanFrame(frame.width, frame.height, frame.luma, frame.stride, 0, clock)) }
      delay(frameMillis)
      clock += frameMillis
    }
  }

  override suspend fun capture(): Bitmap {
    val bitmap = showing ?: error("Nessuna foto da scattare")
    val copy = withContext(Dispatchers.Default) { bitmap.copy(Bitmap.Config.ARGB_8888, false) }
    // Il foglio scattato se ne va: un momento di tavolo vuoto, poi il foglio dopo (nel giro dei fotogrammi).
    pendingNext = true
    return copy
  }

  private var pendingNext = false
  private var lastAnalyzer: ((FluidScanFrame) -> Unit)? = null

  private suspend fun next() {
    if (table != null) {
      sheetVisible = false
      showing = table.invoke()
      updateFrame()
      arrivedAt = clock
      var waited = 0L
      while (waited < TableMillis) {
        current?.let { frame -> withContext(Dispatchers.Default) { lastAnalyzer?.invoke(FluidScanFrame(frame.width, frame.height, frame.luma, frame.stride, 0, clock)) } }
        delay(frameMillis)
        clock += frameMillis
        waited += frameMillis
      }
    }
    index++
    show(index)
  }

  private suspend fun show(i: Int) {
    val loader = sheets.getOrNull(i)
    val bitmap = loader?.invoke() ?: table?.invoke()
    sheetVisible = loader != null
    showing = bitmap
    arrivedAt = clock
    updateFrame()
    ready = bitmap != null
  }

  private suspend fun updateFrame() {
    val bitmap = showing ?: return
    aspect = bitmap.width.toFloat() / bitmap.height
    current = withContext(Dispatchers.Default) { FluidScanFrame.of(bitmap) }
  }

  private companion object {
    /** Il tavolo vuoto fra un foglio e l'altro: 700 ms. A occhio, il tempo di girare pagina. */
    const val TableMillis = 700L
  }
}
