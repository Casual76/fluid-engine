package dev.antigravity.fluidengine.sample.measure

import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Una lettura arrivata dal lettore, sul filo di lavoro.
 *
 * @property requestedNanos quando il filo principale ha chiesto il fotogramma.
 * @property arrivedNanos quando l'immagine è arrivata al filo di lavoro.
 * @property doneNanos quando i conti sono finiti.
 * @property lumas la luminanza media (0..1) di ogni casella; `NaN` dove non c'è una miniatura.
 * @property rgb i pixel letti (tre byte per pixel, casella dopo casella), solo se chiesti.
 */
internal class ProbeReading(
  val requestedNanos: Long,
  val arrivedNanos: Long,
  val doneNanos: Long,
  val lumas: FloatArray,
  val rgb: ByteArray?,
  /** L'alfa medio (0..1) di tutta l'immagine: zero vuol dire che nel fotogramma non è stato disegnato niente. */
  val alpha: Float,
)

/** Com'è andata una richiesta, dal punto di vista del filo principale. */
internal class ProbeRequest(val outcome: Outcome, val mainNanos: Long, val recordNanos: Long) {
  enum class Outcome {
    /** Il fotogramma è partito: arriverà una [ProbeReading]. */
    Sent,

    /** La lettura di prima non è ancora tornata: questa non si chiede, per non accodare. */
    Overrun,

    /** `syncAndDraw` ha detto che il fotogramma non si farà (superficie persa, contesto fermo, scartato). */
    Refused,

    /** Niente da leggere: nessuna miniatura registrata, o il lettore è stato chiuso. */
    Idle,
  }
}

/**
 * La via del cancello C5 dopo S1 (piano «iOS in Android», 3.9): leggere una miniatura **senza**
 * `toImageBitmap()`.
 *
 * `toImageBitmap()` costa al filo principale circa 5 ms a lettura (più 2 di copia dei pixel): a ogni
 * chiamata si costruiscono un contesto di resa e una superficie nuovi, si rende e si aspetta. Qui il
 * contesto è uno e resta: un [HardwareRenderer] che rende sulla `Surface` di un [ImageReader]. Al filo
 * principale resta la sola richiesta del fotogramma ([request]: la sincronizzazione con il
 * RenderThread, 0,4 ms se il RenderThread è libero, 2 se sta rendendo la finestra); i pixel arrivano
 * con `onImageAvailable` su un [HandlerThread], che li legge e fa i conti (0,2–0,4 ms). Misurato sul
 * Tab S9 in S1-bis: 4 sonde ogni 100 ms non tolgono un fotogramma, e i pixel sono gli stessi di
 * `toImageBitmap()`.
 *
 * La radice del lettore non copia niente: disegna le miniature **per riferimento**, e quelle a loro
 * volta il fondale registrato dalla pagina. È lo stesso albero che la finestra sta disegnando, visto
 * da un secondo renderer: per questo la radice e le miniature si registrano solo sul filo principale
 * (HWUI non regge una registrazione altrove: il crash di S1).
 *
 * **La radice si ri-registra a ogni richiesta** ([rerecord]), per prudenza. Un renderer ridisegna ciò
 * che nel *suo* albero risulta cambiato, e i cambi dei nodi condivisi li prende la finestra quando
 * sincronizza il proprio fotogramma: una radice sempre nuova garantisce che il lettore ridisegni. Sul
 * Tab S9 il contenuto segue anche senza (`measure.rerecord off`: 92 cambi di fondale visti su 92), ma
 * registrare la radice costa 0,1 ms e non dipende da come un dispositivo tratta i suoi buffer.
 *
 * Ha [slots] caselle affiancate, larghe [side] pixel: con una casella è il lettore di una sonda, con
 * più caselle una richiesta sola legge tutte le sonde.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class ProbeReaderHub(
  private val slots: Int,
  private val side: Int,
  private val density: Density,
  private val keepPixels: Boolean,
  private val rerecord: Boolean,
  private val options: ProbeHubOptions,
  /** Chiamata sul filo di lavoro, una volta per fotogramma arrivato. */
  private val onReading: (ProbeReading) -> Unit,
) {
  // Non `width`: dentro `RenderNode(...).apply { }` quel nome sarebbe la larghezza del nodo, cioè zero,
  // e una radice larga zero non disegna niente (è successo: il lettore leggeva solo trasparente).
  private val imageWidth = side * slots
  private val thread = HandlerThread("FluidMisure.sonda").apply { start() }
  private val handler = Handler(thread.looper)
  private val reader = if (options.gpuUsage) {
    ImageReader.newInstance(
      imageWidth,
      side,
      PixelFormat.RGBA_8888,
      MaxImages,
      HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_CPU_READ_OFTEN,
    )
  } else {
    ImageReader.newInstance(imageWidth, side, PixelFormat.RGBA_8888, MaxImages)
  }
  private val root = RenderNode("FluidMisure.sonda").apply { setPosition(0, 0, imageWidth, side) }
  private val renderer: HardwareRenderer? = if (options.via == MeasureVia.Renderer) {
    HardwareRenderer().apply {
      setName("FluidMisure.sonda")
      setContentRoot(root)
      setSurface(reader.surface)
      // La miniatura copre tutta la casella, ma una casella senza sonda deve leggersi vuota, non sporca.
      isOpaque = options.opaque
      if (options.light) {
        setLightSourceGeometry(0f, 0f, 0f, 0f)
        setLightSourceAlpha(0f, 0f)
      }
      if (options.start) start()
    }
  } else {
    null
  }
  private val drawScope = CanvasDrawScope()
  private val painters = arrayOfNulls<DrawScope.() -> Unit>(slots)
  private var readings = 0
  private var dirty = false
  private var released = false

  /** Gli istanti delle richieste in volo: una per fotogramma, nell'ordine in cui tornano. */
  private val pending = ConcurrentLinkedQueue<Long>()
  private val inFlight = AtomicInteger(0)

  /** La riga di pixel in arrivo, riusata: il filo di lavoro è uno. */
  private var row = ByteArray(0)

  init {
    reader.setOnImageAvailableListener({ source -> drain(source) }, handler)
  }

  /**
   * Che cosa disegna la casella [slot]: la miniatura è stata registrata, o è cambiata. Dal filo
   * principale. [painter] gira sul filo principale, dentro la registrazione della radice.
   */
  fun setContent(slot: Int, painter: DrawScope.() -> Unit) {
    if (slot !in 0 until slots) return
    painters[slot] = painter
    dirty = true
  }

  /** Chiede un fotogramma. Dal filo principale: è l'unico costo che il lettore gli lascia. */
  fun request(): ProbeRequest {
    val started = System.nanoTime()
    if (released || painters.all { it == null }) return ProbeRequest(ProbeRequest.Outcome.Idle, 0L, 0L)
    if (inFlight.get() >= MaxInFlight) return ProbeRequest(ProbeRequest.Outcome.Overrun, 0L, 0L)
    var recordNanos = 0L
    if (dirty || rerecord) {
      recordRoot()
      recordNanos = System.nanoTime() - started
    }
    if (renderer == null) {
      // Le due vie di confronto: il canvas che la `Surface` stessa sa dare, accelerato o in memoria.
      pending.add(started)
      inFlight.incrementAndGet()
      val surface = reader.surface
      val canvas = if (options.via == MeasureVia.LockHardware) surface.lockHardwareCanvas() else surface.lockCanvas(null)
      try {
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        paint(canvas)
      } finally {
        surface.unlockCanvasAndPost(canvas)
      }
      return ProbeRequest(ProbeRequest.Outcome.Sent, System.nanoTime() - started, 0L)
    }
    pending.add(started)
    inFlight.incrementAndGet()
    val result = renderer.createRenderRequest().setVsyncTime(System.nanoTime()).syncAndDraw()
    val mainNanos = System.nanoTime() - started
    if (result and RefusedMask != 0) {
      // Nessun fotogramma, quindi nessuna immagine: la richiesta non resta in coda ad aspettarla.
      pending.remove(started)
      inFlight.decrementAndGet()
      return ProbeRequest(ProbeRequest.Outcome.Refused, mainNanos, recordNanos)
    }
    return ProbeRequest(ProbeRequest.Outcome.Sent, mainNanos, recordNanos)
  }

  /** Dal filo principale, quando la scena se ne va. */
  fun release() {
    if (released) return
    released = true
    reader.setOnImageAvailableListener(null, null)
    renderer?.stop()
    renderer?.destroy()
    reader.close()
    root.discardDisplayList()
    thread.quitSafely()
  }

  private fun recordRoot() {
    val canvas = root.beginRecording()
    try {
      paint(canvas)
    } finally {
      root.endRecording()
    }
    dirty = false
  }

  private fun paint(canvas: android.graphics.Canvas) {
    drawScope.draw(density, LayoutDirection.Ltr, Canvas(canvas), Size(imageWidth.toFloat(), side.toFloat())) {
      for (slot in 0 until slots) {
        val painter = painters[slot] ?: continue
        translate(left = (slot * side).toFloat()) {
          clipRect(0f, 0f, side.toFloat(), side.toFloat()) { painter() }
        }
      }
    }
  }

  /** Sul filo di lavoro: prende ogni immagine pronta, ne legge i pixel e la restituisce subito. */
  private fun drain(source: ImageReader) {
    while (true) {
      val image = try {
        source.acquireNextImage()
      } catch (_: IllegalStateException) {
        // Il lettore è stato chiuso mentre l'avviso era in coda, o le immagini in mano sono troppe.
        null
      } ?: return
      val arrived = System.nanoTime()
      val requested = pending.poll() ?: arrived
      inFlight.decrementAndGet()
      val lumas = FloatArray(slots) { Float.NaN }
      val rgb = if (keepPixels) ByteArray(slots * side * side * 3) else null
      val alpha: Float
      try {
        alpha = read(image, lumas, rgb)
        // Le prime letture, e poi una ogni tanto, in chiaro: che cosa è davvero arrivato.
        if (readings < 3 || readings % 200 == 0) {
          val plane = image.planes[0]
          val head = ByteArray(8)
          val bytes = plane.buffer
          bytes.position(0)
          bytes.get(head, 0, minOf(8, bytes.remaining()))
          Log.i(
            MeasureLogTag,
            "SONDA n=$readings ${image.width}x${image.height} format=${image.format}" +
              " rowStride=${plane.rowStride} pixelStride=${plane.pixelStride} bytes=${bytes.capacity()}" +
              " ts=${image.timestamp} head=${head.joinToString("") { "%02x".format(it) }}" +
              " alpha=$alpha luma=${lumas.joinToString(",")}",
          )
        }
        readings++
      } finally {
        image.close()
      }
      onReading(ProbeReading(requested, arrived, System.nanoTime(), lumas, rgb, alpha))
    }
  }

  /** Riempie [lumas] e [rgb]; dà l'alfa medio dell'immagine. */
  private fun read(image: Image, lumas: FloatArray, rgb: ByteArray?): Float {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val rowBytes = imageWidth * pixelStride
    if (row.size < rowBytes) row = ByteArray(rowBytes)
    val sums = FloatArray(slots)
    var alphaSum = 0L
    for (y in 0 until side) {
      buffer.position(y * rowStride)
      buffer.get(row, 0, minOf(rowBytes, buffer.remaining()))
      for (x in 0 until imageWidth) {
        val at = x * pixelStride
        val r = row[at].toInt() and 0xFF
        val g = row[at + 1].toInt() and 0xFF
        val b = row[at + 2].toInt() and 0xFF
        alphaSum += row[at + 3].toInt() and 0xFF
        val slot = x / side
        sums[slot] += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        if (rgb != null) {
          val out = (slot * side * side + y * side + (x - slot * side)) * 3
          rgb[out] = row[at]
          rgb[out + 1] = row[at + 1]
          rgb[out + 2] = row[at + 2]
        }
      }
    }
    for (slot in 0 until slots) {
      if (painters[slot] != null) lumas[slot] = sums[slot] / (side * side)
    }
    return alphaSum / (255f * imageWidth * side)
  }

  private companion object {
    /** Immagini che l'`ImageReader` può dare insieme: una in lettura e margine per quella che arriva. */
    const val MaxImages = 3

    /** Oltre due fotogrammi in volo il RenderThread resterebbe ad aspettare un buffer libero. */
    const val MaxInFlight = 2

    const val RefusedMask = HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND or
      HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or
      HardwareRenderer.SYNC_FRAME_DROPPED
  }
}

/** Come è montato il lettore: le varianti servono a capire che cosa della catena conta. */
internal class ProbeHubOptions(
  val via: MeasureVia,
  val opaque: Boolean,
  val start: Boolean,
  val gpuUsage: Boolean,
  val light: Boolean,
)

/**
 * Il confronto fra due letture della stessa miniatura, pixel per pixel: quanto differiscono le
 * luminanze medie e quanto, al massimo e in media, un canale di un pixel. In livelli (0..255) per i
 * pixel, in frazione (0..1) per la luminanza. Puro: si prova senza dispositivo.
 */
internal class ProbeDifference(val lumaDelta: Float, val maxChannelDelta: Int, val meanChannelDelta: Float)

/**
 * @param argb i pixel di `toImageBitmap()`, come li dà `Bitmap.getPixels`.
 * @param rgb i pixel del lettore, tre byte per pixel, a partire da [rgbOffset].
 */
internal fun probeDifference(argb: IntArray, rgb: ByteArray, rgbOffset: Int = 0): ProbeDifference {
  val count = minOf(argb.size, (rgb.size - rgbOffset) / 3)
  if (count <= 0) return ProbeDifference(Float.NaN, -1, Float.NaN)
  var lumaA = 0f
  var lumaB = 0f
  var worst = 0
  var total = 0L
  for (index in 0 until count) {
    val pixel = argb[index]
    val ra = (pixel shr 16) and 0xFF
    val ga = (pixel shr 8) and 0xFF
    val ba = pixel and 0xFF
    val at = rgbOffset + index * 3
    val rb = rgb[at].toInt() and 0xFF
    val gb = rgb[at + 1].toInt() and 0xFF
    val bb = rgb[at + 2].toInt() and 0xFF
    lumaA += (0.2126f * ra + 0.7152f * ga + 0.0722f * ba) / 255f
    lumaB += (0.2126f * rb + 0.7152f * gb + 0.0722f * bb) / 255f
    val dr = kotlin.math.abs(ra - rb)
    val dg = kotlin.math.abs(ga - gb)
    val db = kotlin.math.abs(ba - bb)
    worst = maxOf(worst, dr, dg, db)
    total += dr + dg + db
  }
  return ProbeDifference(
    lumaDelta = kotlin.math.abs(lumaA - lumaB) / count,
    maxChannelDelta = worst,
    meanChannelDelta = total / (count * 3f),
  )
}

/**
 * Chi segue la prova «il fondale passa da chiaro a scuro sotto la sonda»: sa quando il fondale è
 * cambiato e, a ogni lettura, dice se la sonda l'ha visto e dopo quanto. Puro (gli istanti glieli dà
 * chi chiama), e usato da più fili: per questo è sincronizzato.
 *
 * @param staleNanos oltre quanto dal cambio una lettura **chiesta** dopo deve già vedere il lato nuovo:
 *   se vede ancora il vecchio è una lettura stantia, cioè il lettore non segue la sorgente.
 */
internal class FlipTracker(private val probes: Int, private val staleNanos: Long) {
  private var at = 0L
  private var dark = false
  private val seen = BooleanArray(probes)

  /** Ciò che una lettura ha detto della prova, o nulla se non c'entra. */
  class Verdict(val kind: Kind, val seenNanos: Long, val waitNanos: Long, val pipeNanos: Long)

  enum class Kind { Seen, Stale }

  /** Il fondale è appena cambiato. Dice quante sonde non avevano ancora visto il cambio di prima. */
  @Synchronized
  fun mark(nowNanos: Long, toDark: Boolean): Int {
    val missed = if (at == 0L) 0 else seen.count { !it }
    at = nowNanos
    dark = toDark
    seen.fill(false)
    return missed
  }

  @Synchronized
  fun onReading(probe: Int, luma: Float, requestedNanos: Long, doneNanos: Long): Verdict? {
    if (at == 0L || probe !in 0 until probes || luma.isNaN()) return null
    val readsDark = luma < 0.5f
    if (readsDark == dark) {
      if (seen[probe]) return null
      seen[probe] = true
      return Verdict(Kind.Seen, doneNanos - at, maxOf(0L, requestedNanos - at), doneNanos - requestedNanos)
    }
    return if (requestedNanos - at > staleNanos) {
      Verdict(Kind.Stale, doneNanos - at, requestedNanos - at, doneNanos - requestedNanos)
    } else {
      null
    }
  }
}
