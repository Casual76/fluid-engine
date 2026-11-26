package dev.antigravity.fluidengine.ink

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushBehavior
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushPaint
import androidx.ink.brush.BrushTip
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.InputToolType
import androidx.ink.brush.SelfOverlap
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import java.util.IdentityHashMap
import kotlin.math.max

/**
 * Le cose del disegno che non sono di un componente: se c'e' la resa di androidx.ink, e i crediti della libreria.
 */
object FluidInk {
  /**
   * Vero se la libreria nativa di androidx.ink si carica: sul telefono sempre (c'e' per tutte le ABI di Android), sul
   * computer no (androidx.ink la da' per Linux, non per Windows; e il modulo usa la variante Android). Senza, il foglio
   * disegna i tratti col ripiego dell'engine (un `Path` per tratto, lo spessore con la pressione), con lo stesso modello:
   * gomma, lazo, forme, annulla e la consegna non cambiano. Si legge una volta.
   */
  val nativeAvailable: Boolean by lazy {
    if (forceFallback) return@lazy false
    try {
      System.loadLibrary("ink")
      true
    } catch (t: Throwable) {
      Log.i("FluidInk", "androidx.ink senza la libreria nativa (${t.javaClass.simpleName}): il ripiego disegna i tratti")
      false
    }
  }

  /** Solo per le prove: il ripiego anche dove la libreria c'e'. Va chiamato prima di disegnare. */
  @Volatile
  internal var forceFallback: Boolean = false
}

/**
 * Disegna [block] con il canvas portato nelle coordinate del tratto: `CanvasStrokeRenderer.draw` di androidx.ink **non**
 * applica al canvas la matrice che riceve (la usa solo per la precisione dei bordi: «will not be applied to the canvas in
 * any way»), e chi chiama deve metterla sul canvas lui (`canvas.withMatrix`). Senza, i tratti finiti uscivano nelle
 * coordinate del foglio prese per pixel: piccoli di un fattore pari alla scala e spostati verso l'angolo (visto sul Tab S9
 * il 26/11/2026, F8.16c). Il ripiego in `Path` porta i punti da se' e non passa di qui.
 */
internal inline fun drawInStrokeSpace(canvas: Canvas, toCanvas: Matrix, block: (Canvas) -> Unit) {
  val save = canvas.save()
  try {
    canvas.concat(toCanvas)
    block(canvas)
  } finally {
    canvas.restoreToCount(save)
  }
}

/** Il disegno dei tratti finiti su un `Canvas` Android, dal foglio alla consegna. */
internal interface InkRenderer {
  /** Disegna [stroke] con [toCanvas] (dalle coordinate del foglio a quelle del canvas). */
  fun draw(canvas: Canvas, stroke: FluidInkStroke, toCanvas: Matrix)

  /** Dimentica i tratti che non sono in [live] (la cache dei tratti nativi segue il disegno). */
  fun retain(live: List<FluidInkStroke>) {}

  companion object {
    fun create(): InkRenderer = if (FluidInk.nativeAvailable) InkNativeRenderer() else InkPathRenderer()
  }
}

/**
 * I pennelli di androidx.ink per gli strumenti: la penna e l'evidenziatore sono quelli di serie della libreria (V1), la
 * matita e' dell'engine. Il pennello si fa con il colore, lo spessore e l'[epsilon] del tratto: la distanza sotto cui due
 * punti sono lo stesso punto, un decimo di pixel della vista di partenza.
 */
internal object InkBrushes {
  private val pen by lazy { StockBrushes.pressurePen() }
  private val highlighter by lazy { StockBrushes.highlighter(SelfOverlap.DISCARD) }
  private val monoline by lazy { StockBrushes.marker() }

  /**
   * La matita: punta tonda e sottile, piu' chiara se si preme poco e piu' larga e chiara se la penna e' inclinata, come
   * la matita di PencilKit (che sfuma inclinata). I numeri sono a occhio (25/11/2026) sulle riprese di Note; senza la
   * grana della carta (androidx.ink 1.0 ne ha una solo in prova). Il costruttore della famiglia e' pubblico dalla 1.1 di
   * androidx.ink; nella 1.0 c'e' ma e' segnato interno, da qui il permesso.
   */
  private val pencil by lazy { pencilFamily() }

  @SuppressLint("RestrictedApi")
  @OptIn(ExperimentalInkCustomBrushApi::class)
  private fun pencilFamily(): BrushFamily {
    val stylus = setOf(InputToolType.STYLUS)
    return BrushFamily(
      tip = BrushTip(
        behaviors = listOf(
          BrushBehavior(BrushBehavior.Source.NORMALIZED_PRESSURE, BrushBehavior.Target.OPACITY_MULTIPLIER, 0f, 1f, 0.45f, 1f,
            BrushBehavior.OutOfRange.CLAMP, responseTimeMillis = 20L, enabledToolTypes = stylus),
          BrushBehavior(BrushBehavior.Source.NORMALIZED_PRESSURE, BrushBehavior.Target.SIZE_MULTIPLIER, 0f, 1f, 0.85f, 1.15f,
            BrushBehavior.OutOfRange.CLAMP, responseTimeMillis = 20L, enabledToolTypes = stylus),
          BrushBehavior(BrushBehavior.Source.TILT_IN_RADIANS, BrushBehavior.Target.SIZE_MULTIPLIER, 0.4f, 1.2f, 1f, 2.2f,
            BrushBehavior.OutOfRange.CLAMP, responseTimeMillis = 30L, enabledToolTypes = stylus),
          BrushBehavior(BrushBehavior.Source.TILT_IN_RADIANS, BrushBehavior.Target.OPACITY_MULTIPLIER, 0.4f, 1.2f, 1f, 0.55f,
            BrushBehavior.OutOfRange.CLAMP, responseTimeMillis = 30L, enabledToolTypes = stylus),
        ),
      ),
      paint = BrushPaint(selfOverlap = SelfOverlap.DISCARD),
      inputModel = BrushFamily.SlidingWindowModel(),
    )
  }

  /** L'opacita' di base della matita (la grafite non copre mai del tutto): a occhio, 25/11/2026. */
  const val PencilAlpha = 0.86f

  fun family(tool: FluidInkTool): BrushFamily = when (tool) {
    FluidInkTool.Highlighter -> highlighter
    FluidInkTool.Pencil -> pencil
    FluidInkTool.Monoline -> monoline
    else -> pen
  }

  fun brush(tool: FluidInkTool, argb: Int, size: Float, epsilon: Float): Brush =
    Brush.createWithColorIntArgb(family(tool), toolArgb(tool, argb), size, max(epsilon, 1e-4f))

  /** Il colore che lo strumento dipinge davvero: la matita toglie un filo di copertura. */
  fun toolArgb(tool: FluidInkTool, argb: Int): Int {
    if (tool != FluidInkTool.Pencil) return argb
    val alpha = ((argb ushr 24) * PencilAlpha).toInt().coerceIn(0, 255)
    return (alpha shl 24) or (argb and 0x00FFFFFF)
  }

  fun inputType(input: FluidInkInput): InputToolType = when (input) {
    FluidInkInput.Stylus -> InputToolType.STYLUS
    FluidInkInput.Touch -> InputToolType.TOUCH
    FluidInkInput.Mouse -> InputToolType.MOUSE
    else -> InputToolType.UNKNOWN
  }

  /** L'epsilon di un tratto: un decimo di pixel nelle unita' del foglio, coi [unitsPerPixel] della vista di partenza. */
  fun epsilonFor(unitsPerPixel: Float): Float = (unitsPerPixel * 0.1f).coerceAtLeast(1e-3f)

  /** Il tratto di androidx.ink fatto dai punti del modello. */
  fun stroke(stroke: FluidInkStroke, epsilon: Float): Stroke {
    val batch = MutableStrokeInputBatch()
    val p = stroke.points
    val type = inputType(stroke.input)
    for (i in 0 until p.size) {
      batch.add(type, p.x(i), p.y(i), p.time(i).toLong(),
        pressure = if (p.hasPressure) p.pressure(i) else androidx.ink.strokes.StrokeInput.NO_PRESSURE,
        tiltRadians = if (p.hasTilt) p.tilt(i) else androidx.ink.strokes.StrokeInput.NO_TILT,
        orientationRadians = if (p.hasOrientation) p.orientation(i) else androidx.ink.strokes.StrokeInput.NO_ORIENTATION,
      )
    }
    return Stroke(brush(stroke.tool, stroke.color.toArgb(), stroke.size, epsilon), batch)
  }
}

/**
 * La resa di androidx.ink (`CanvasStrokeRenderer`: le mesh sul canvas accelerato, i `Path` su quello di un PDF). I tratti
 * nativi si fanno una volta per tratto del modello e si tengono finche' il tratto e' nel disegno; quello appena finito col
 * dito arriva gia' fatto dal tratto a bassa latenza ([adopt]).
 */
internal class InkNativeRenderer : InkRenderer {
  private val renderer = CanvasStrokeRenderer.create()
  private val cache = IdentityHashMap<FluidInkStroke, Stroke>()
  private val fallback = InkPathRenderer()

  /** L'epsilon dei tratti rifatti dai punti (la vista lo aggiorna quando conosce la sua scala). */
  var epsilon: Float = 0.05f

  fun adopt(stroke: FluidInkStroke, native: Stroke) {
    cache[stroke] = native
  }

  override fun draw(canvas: Canvas, stroke: FluidInkStroke, toCanvas: Matrix) {
    val native = cache[stroke] ?: try {
      InkBrushes.stroke(stroke, epsilon).also { cache[stroke] = it }
    } catch (e: RuntimeException) {
      // Un ingresso che androidx.ink rifiuta (un salvataggio strano): il tratto si vede col ripiego, non sparisce.
      Log.w("FluidInk", "Tratto ${stroke.id} disegnato col ripiego: ${e.message}")
      null
    }
    if (native == null) fallback.draw(canvas, stroke, toCanvas) else drawInStrokeSpace(canvas, toCanvas) { renderer.draw(it, native, toCanvas) }
  }

  override fun retain(live: List<FluidInkStroke>) {
    if (cache.isEmpty()) return
    val keep = IdentityHashMap<FluidInkStroke, Boolean>(live.size)
    for (s in live) keep[s] = true
    cache.keys.retainAll { keep.containsKey(it) }
  }
}

/**
 * Il ripiego dell'engine, dove androidx.ink non c'e' (il computer): ogni segmento e' una linea dallo spessore del tratto
 * (con la pressione, se c'e'), le punte tonde; l'evidenziatore a scalpello e trasparente, senza sommarsi con se stesso
 * (un layer per tratto); la matita un filo trasparente. Gli stessi punti, gli stessi colori, gli stessi spessori: le
 * prove guardano i pixel di questo.
 */
internal class InkPathRenderer : InkRenderer {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeJoin = Paint.Join.ROUND
  }
  private val path = Path()
  private val src = FloatArray(4)
  private val dst = FloatArray(4)

  override fun draw(canvas: Canvas, stroke: FluidInkStroke, toCanvas: Matrix) {
    val p = stroke.points
    drawPacked(canvas, p.data, p.size, p.hasPressure, stroke.tool, stroke.color.toArgb(), stroke.size, toCanvas)
  }

  /** Disegna dei punti impacchettati ([FluidInkPoints.Stride] valori per punto): anche il tratto che sta arrivando. */
  fun drawPacked(
    canvas: Canvas,
    data: FloatArray,
    count: Int,
    hasPressure: Boolean,
    tool: FluidInkTool,
    colorArgb: Int,
    size: Float,
    toCanvas: Matrix,
  ) {
    if (count == 0) return
    val stride = FluidInkPoints.Stride
    val scale = toCanvas.mapRadius(1f)
    val argb = InkBrushes.toolArgb(tool, colorArgb)
    val highlighter = tool == FluidInkTool.Highlighter
    paint.strokeCap = if (highlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
    val alpha = argb ushr 24
    // Un tratto trasparente non si somma con se stesso: il suo contorno pieno, poi l'opacita' una volta sola.
    val layered = alpha < 255
    if (layered) canvas.saveLayerAlpha(null, alpha)
    paint.color = argb or (0xFF shl 24)
    if (!hasPressure || highlighter || tool == FluidInkTool.Monoline) {
      path.rewind()
      src[0] = data[0]; src[1] = data[1]
      toCanvas.mapPoints(dst, 0, src, 0, 1)
      path.moveTo(dst[0], dst[1])
      if (count == 1) path.lineTo(dst[0] + 0.01f, dst[1])
      for (i in 1 until count) {
        src[0] = data[i * stride]; src[1] = data[i * stride + 1]
        toCanvas.mapPoints(dst, 0, src, 0, 1)
        path.lineTo(dst[0], dst[1])
      }
      paint.strokeWidth = size * scale
      canvas.drawPath(path, paint)
    } else {
      for (i in 0 until max(1, count - 1)) {
        val j = if (count == 1) 0 else i + 1
        src[0] = data[i * stride]; src[1] = data[i * stride + 1]; src[2] = data[j * stride]; src[3] = data[j * stride + 1]
        toCanvas.mapPoints(dst, 0, src, 0, 2)
        val pressure = (data[i * stride + 3] + data[j * stride + 3]) / 2f
        paint.strokeWidth = size * scale * pressureGain(tool, pressure)
        canvas.drawLine(dst[0], dst[1], if (j == i) dst[2] + 0.01f else dst[2], dst[3], paint)
      }
    }
    if (layered) canvas.restore()
  }

  private fun pressureGain(tool: FluidInkTool, pressure: Float): Float = when (tool) {
    FluidInkTool.Highlighter, FluidInkTool.Monoline -> 1f
    FluidInkTool.Pencil -> 0.85f + 0.3f * pressure
    else -> 0.75f + 0.5f * pressure
  }
}
