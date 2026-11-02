package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRimRecipe
import dev.antigravity.fluidengine.ui.fluid.glassRimRamp
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShaderCacheImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.asComposeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.HighlightLayerMaxPx
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.HighlightStyle
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.layerResolutionScale
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.clipOutline
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.setRuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * La banda del bordo dentro la sagoma, in pixel: quella che il renderer di `glassSurface` lascia
 * vedere. Il suo anello e' un tratto largo il doppio di `ceil(larghezza)`, ritagliato sulla sagoma
 * (`HighlightModifier.kt`): fuori non esce niente, dentro resta esattamente `ceil(larghezza)`, mai
 * oltre mezzo lato corto.
 *
 * Il bordo fisico di prima era un tratto *centrato* sul contorno, largo la larghezza nominale: meta'
 * dentro e meta' fuori, sopra la pagina, e senza sfocatura — una riga disegnata invece della luce
 * presa da uno spigolo. E' la seconda cosa che lo distingueva da una lastra ferma, dopo l'ombra.
 */
internal fun physicsRimBandPx(widthPx: Float, minDimensionPx: Float): Float {
  if (!(widthPx > 0f) || !(minDimensionPx > 0f)) return 0f
  return ceil(widthPx.coerceAtMost(minDimensionPx / 2f))
}

/**
 * Il bordo speculare di una superficie fisica: **a riposo quello di `glassSurface` al pixel**, in
 * viaggio la sua versione senza sfocatura.
 *
 * Una lastra ferma ([restSlab]) registra il proprio anello UNA volta, in un layer, con la ricetta del
 * renderer (`HighlightNode`): tratto doppio ritagliato sulla sagoma, sfocato di mezza larghezza,
 * colorato dallo shader del riflesso di Kyant con l'angolo della luce, e il layer ridotto sopra il
 * tetto d'area come fa il renderer. Poi lo ridisegna e basta: l'alfa e il modo di comporre sono
 * proprieta' del layer, e cambiarli non registra niente.
 *
 * In viaggio la sagoma cambia a ogni fotogramma, e una maschera sfocata rifatta a ogni fotogramma e'
 * il costo che Fluid-physics esiste per non pagare. Per una lastra sola il bordo e' allora la stessa
 * banda interna, con lo stesso shader del riflesso aggiornato per uniform, senza la mezza larghezza
 * di sfocatura — su un tratto di due o tre pixel in movimento non si vede. Un gruppo o una sagoma
 * libera non hanno un rettangolo da dare allo shader: tengono il gradiente lungo la sagoma
 * ([glassRimRamp]) di sempre.
 *
 * La goccia (3.0) ha il profilo intero della lastra ferma, sfocatura compresa, lungo il suo contorno
 * libero: una fascia di triangoli ([DropRimMesh]) invece di un tratto su un path, cosi' quando si
 * posa sulla sua lastra il bordo non cambia di un pixel.
 */
internal class PhysicsRimRenderer(private val layer: GraphicsLayer) {

  private val paint = Paint().apply { style = PaintingStyle.Stroke }
  private val clipPath = Path()
  private val travelPath = Path()
  private val shaderCache = RuntimeShaderCacheImpl()
  private val dropMesh = DropRimMesh()
  private val meshPaint = Paint()

  private var recordedSlab: FluidForm.Slab? = null
  private var recordedWidthPx = Float.NaN
  private var recordedBlurPx = Float.NaN
  private var recordedStyle: HighlightStyle? = null
  private var recordedDensity = Float.NaN

  /**
   * @param restSlab la lastra ferma, se la superficie lo e'; null in viaggio o per un'altra famiglia.
   * @param strength l'alfa del bordo: `highlightAlpha` per intensita' per qualita', come in
   *   `glassSurface` (il colore della ricetta ci si moltiplica sopra).
   */
  fun draw(
    scope: DrawScope,
    plan: PhysicsRenderPlan,
    restSlab: FluidForm.Slab?,
    optics: GlassOptics,
    strength: Float,
    rim: GlassRimRecipe,
  ) {
    val rounded = plan.silhouetteRoundRect
    val drop = plan.dropRest
    if (drop != null) {
      scope.drawDropRim(plan, drop, optics, strength, rim)
      return
    }
    when {
      restSlab != null && rounded != null -> scope.drawRestRim(restSlab, optics, strength, rim)
      rounded != null -> scope.drawTravellingSlabRim(rounded, optics, strength, rim)
      else -> scope.drawShapeRim(plan, optics, strength, rim)
    }
  }

  /** La ricetta di `HighlightNode`, posata sulla lastra invece che sul nodo intero. */
  private fun DrawScope.drawRestRim(
    slab: FluidForm.Slab,
    optics: GlassOptics,
    strength: Float,
    rim: GlassRimRecipe,
  ) {
    val frame = slab.frame
    val size = Size(frame.width, frame.height)
    if (size.minDimension <= 0f) return
    val resScale = layerResolutionScale(size.width, size.height, HighlightLayerMaxPx)
    val scaledSize = Size(size.width * resScale, size.height * resScale)
    val widthPx = physicsRimBandPx(optics.highlightWidth.toPx(), size.minDimension) * 2f * resScale
    val blurPx = (optics.highlightWidth / 2f).toPx() * resScale
    val style = HighlightStyle.Default(
      color = rim.color,
      blendMode = rim.blendMode,
      angle = optics.highlightAngle,
      falloff = 1f,
    )
    val needsRecord = recordedSlab != slab ||
      recordedWidthPx != widthPx ||
      recordedBlurPx != blurPx ||
      recordedStyle != style ||
      recordedDensity != density

    if (needsRecord) {
      // Nel layer ridotto si riduce anche la geometria, come nel renderer: raggi in pixel per la
      // scala, densita' per la scala.
      val scaledDensity = Density(density * resScale, fontScale)
      val r = slab.cornerRadii
      val shape = ContinuousCornerShape(
        CornerSize(r.topLeft * resScale),
        CornerSize(r.topRight * resScale),
        CornerSize(r.bottomRight * resScale),
        CornerSize(r.bottomLeft * resScale),
        slab.smoothing,
      )
      // I raggi di uno Slab sono assoluti (topLeft e' a sinistra): la direzione e' sempre Ltr.
      val outline = shape.createOutline(scaledSize, LayoutDirection.Ltr, scaledDensity)
      paint.color = style.color
      paint.strokeWidth = widthPx
      paint.blur(blurPx)
      paint.setRuntimeShader(
        if (isRuntimeShaderSupported()) {
          with(style) { createShader(shape, scaledSize, scaledDensity, shaderCache) }
        } else {
          null
        },
      )
      val safeSize = IntSize(ceil(scaledSize.width).toInt() + 2, ceil(scaledSize.height).toInt() + 2)
      layer.record(safeSize) {
        translate(1f, 1f) {
          val canvas = drawContext.canvas
          canvas.save()
          canvas.clipOutline(outline, clipPath)
          canvas.drawOutline(outline, paint)
          canvas.restore()
        }
      }
      recordedSlab = slab
      recordedWidthPx = widthPx
      recordedBlurPx = blurPx
      recordedStyle = style
      recordedDensity = density
    }

    layer.alpha = strength.coerceIn(0f, 1f)
    layer.blendMode = rim.blendMode
    translate(frame.left, frame.top) {
      if (resScale != 1f) {
        scale(1f / resScale, pivot = Offset.Zero) {
          translate(-1f, -1f) { drawLayer(layer) }
        }
      } else {
        translate(-1f, -1f) { drawLayer(layer) }
      }
    }
  }

  /**
   * Una lastra sola in viaggio: la stessa banda interna, senza sfocatura. Il tratto sta a mezza banda
   * dentro il contorno, quindi copre esattamente la banda e non esce mai dalla sagoma — niente
   * ritaglio da pagare a ogni fotogramma.
   */
  private fun DrawScope.drawTravellingSlabRim(
    rounded: RoundRect,
    optics: GlassOptics,
    strength: Float,
    rim: GlassRimRecipe,
  ) {
    val width = rounded.width
    val height = rounded.height
    val band = physicsRimBandPx(optics.highlightWidth.toPx(), minOf(width, height))
    if (band <= 0f) return
    val inset = band / 2f
    fun inner(radius: Float) = CornerRadius((radius - inset).coerceAtLeast(0f))
    travelPath.rewind()
    travelPath.addRoundRect(
      RoundRect(
        left = inset,
        top = inset,
        right = width - inset,
        bottom = height - inset,
        topLeftCornerRadius = inner(rounded.topLeftCornerRadius.x),
        topRightCornerRadius = inner(rounded.topRightCornerRadius.x),
        bottomRightCornerRadius = inner(rounded.bottomRightCornerRadius.x),
        bottomLeftCornerRadius = inner(rounded.bottomLeftCornerRadius.x),
      ),
    )
    val brush: Brush = if (isRuntimeShaderSupported()) {
      val style = HighlightStyle.Default(
        color = rim.color,
        blendMode = rim.blendMode,
        angle = optics.highlightAngle,
        falloff = 1f,
      )
      val shape = ContinuousCornerShape(
        CornerSize(rounded.topLeftCornerRadius.x),
        CornerSize(rounded.topRightCornerRadius.x),
        CornerSize(rounded.bottomRightCornerRadius.x),
        CornerSize(rounded.bottomLeftCornerRadius.x),
      )
      val shader = with(style) { createShader(shape, Size(width, height), this@drawTravellingSlabRim, shaderCache) }
      if (shader != null) ShaderBrush(shader.asComposeShader()) else verticalRimBrush(rounded, rim, 0f, height)
    } else {
      verticalRimBrush(rounded, rim, 0f, height)
    }
    translate(rounded.left, rounded.top) {
      drawPath(
        path = travelPath,
        brush = brush,
        alpha = (strength * rim.color.alpha).coerceIn(0f, 1f),
        style = Stroke(width = band),
        blendMode = rim.blendMode,
      )
    }
  }

  /**
   * Un gruppo o una sagoma libera: il gradiente lungo la sagoma col profilo dello shader, pieno sopra
   * e sotto e a zero sui fianchi ([glassRimRamp]). Il tratto resta centrato sul contorno: senza un
   * rettangolo non c'e' un modo gratuito di tenerlo dentro.
   */
  private fun DrawScope.drawShapeRim(
    plan: PhysicsRenderPlan,
    optics: GlassOptics,
    strength: Float,
    rim: GlassRimRecipe,
  ) {
    val bounds = plan.bounds
    if (bounds.height <= 0f) return
    val alpha = strength * rim.color.alpha
    val ramp = glassRimRamp(minOf(bounds.width, bounds.height) / 2f, bounds.width, bounds.height)
      .coerceAtLeast(MinRimRamp)
    val lit = rim.color.copy(alpha = alpha)
    val dark = rim.color.copy(alpha = 0f)
    drawPath(
      path = plan.silhouette,
      brush = Brush.verticalGradient(
        0f to lit,
        ramp to dark,
        (1f - ramp) to dark,
        1f to lit,
        startY = bounds.top,
        endY = bounds.bottom,
      ),
      style = Stroke(width = optics.highlightWidth.toPx()),
      blendMode = rim.blendMode,
    )
  }

  /**
   * La goccia in viaggio (3.0): il bordo della lastra ferma, lungo il contorno libero. Non un tratto
   * su un path: un path di quasi cento lati che cambia a ogni fotogramma l'acceleratore lo rasterizza
   * daccapo a ogni fotogramma, sulla CPU del RenderThread. E' una fascia di triangoli ([DropRimMesh]):
   * anelli paralleli al contorno, ognuno col peso del profilo del bordo a riposo a quella profondita'
   * ([restRimProfile]: il tratto doppio sfocato di mezza larghezza e ritagliato sulla sagoma, come lo
   * registra [drawRestRim]) e la luce dello shader del riflesso della sagoma a riposo [rest], posata
   * sul riquadro della goccia. Quando la goccia si posa sulla sua lastra la banda e' la stessa al
   * profilo: nessun gradino da vedere.
   */
  private fun DrawScope.drawDropRim(
    plan: PhysicsRenderPlan,
    rest: FluidForm.Slab,
    optics: GlassOptics,
    strength: Float,
    rim: GlassRimRecipe,
  ) {
    val bounds = plan.bounds
    if (bounds.minDimension <= 0f) return
    val band = physicsRimBandPx(optics.highlightWidth.toPx(), bounds.minDimension)
    if (band <= 0f) return
    val sigma = blurMaskSigma((optics.highlightWidth / 2f).toPx())
    if (!dropMesh.build(plan.verts, plan.vertCount, bounds.left, bounds.top, band, sigma)) return
    val r = rest.cornerRadii
    val shader = if (isRuntimeShaderSupported()) {
      val style = HighlightStyle.Default(
        color = rim.color,
        blendMode = rim.blendMode,
        angle = optics.highlightAngle,
        falloff = 1f,
      )
      // La luce si calcola sul riquadro della goccia, nelle sue coordinate: la fascia si disegna li'.
      val shape = ContinuousCornerShape(
        CornerSize(r.topLeft),
        CornerSize(r.topRight),
        CornerSize(r.bottomRight),
        CornerSize(r.bottomLeft),
      )
      with(style) { createShader(shape, Size(bounds.width, bounds.height), this@drawDropRim, shaderCache) }
        ?.asComposeShader()
    } else {
      null
    }
    meshPaint.shader = shader ?: run {
      val ramp = glassRimRamp(minOf(r.topLeft, r.topRight).coerceAtLeast(0f), bounds.width, bounds.height)
        .coerceAtLeast(MinRimRamp)
      LinearGradientShader(
        from = Offset.Zero,
        to = Offset(0f, bounds.height),
        colors = listOf(
          rim.color.copy(alpha = 1f),
          rim.color.copy(alpha = 0f),
          rim.color.copy(alpha = 0f),
          rim.color.copy(alpha = 1f),
        ),
        colorStops = listOf(0f, ramp, 1f - ramp, 1f),
      )
    }
    meshPaint.alpha = (strength * rim.color.alpha).coerceIn(0f, 1f)
    meshPaint.blendMode = rim.blendMode
    val mesh = dropMesh
    drawIntoCanvas { canvas ->
      val native = canvas.nativeCanvas
      native.save()
      native.translate(bounds.left, bounds.top)
      // Le coordinate della trama sono le posizioni stesse: lo shader del riflesso lavora nel riquadro
      // della goccia, e l'alfa dei vertici (il profilo) lo modula.
      native.drawVertices(
        android.graphics.Canvas.VertexMode.TRIANGLES,
        mesh.vertexFloats,
        mesh.positions,
        0,
        mesh.positions,
        0,
        mesh.colors,
        0,
        mesh.indices,
        0,
        mesh.indexCount,
        meshPaint.asFrameworkPaint(),
      )
      native.restore()
    }
  }

  /** Il ripiego senza AGSL per la lastra in viaggio: lo stesso gradiente, sulle sue rampe vere. */
  private fun verticalRimBrush(rounded: RoundRect, rim: GlassRimRecipe, top: Float, bottom: Float): Brush {
    val width = rounded.width
    val height = rounded.height
    val topRamp = glassRimRamp(
      maxOf(rounded.topLeftCornerRadius.x, rounded.topRightCornerRadius.x),
      width,
      height,
    ).coerceAtLeast(MinRimRamp)
    val bottomRamp = glassRimRamp(
      maxOf(rounded.bottomLeftCornerRadius.x, rounded.bottomRightCornerRadius.x),
      width,
      height,
    ).coerceAtLeast(MinRimRamp)
    val lit = rim.color.copy(alpha = 1f)
    val dark = rim.color.copy(alpha = 0f)
    return Brush.verticalGradient(
      0f to lit,
      topRamp to dark,
      (1f - bottomRamp) to dark,
      1f to lit,
      startY = top,
      endY = bottom,
    )
  }
}

/** Sempre almeno questa rampa, cosi' le fermate del gradiente restano crescenti anche a raggio zero. */
private const val MinRimRamp = 0.002f

/**
 * Il sigma di una `BlurMaskFilter` di raggio [radiusPx]: la conversione di Skia (`raggio / √3 + 0,5`).
 * E' la sfocatura del bordo a riposo ([PhysicsRimRenderer]), che la goccia rifa' a mano.
 */
internal fun blurMaskSigma(radiusPx: Float): Float = if (radiusPx > 0f) radiusPx * 0.57735f + 0.5f else 0f

/**
 * Il peso del bordo a riposo alla profondita' [depthPx] dentro la sagoma (negativa: fuori): il tratto
 * largo il doppio della [bandPx], centrato sul contorno, sfocato di [sigmaPx] e ritagliato sulla
 * sagoma con un pixel d'antialias. E' il profilo che [PhysicsRimRenderer] registra per una lastra
 * ferma, scritto come funzione: la goccia lo campiona lungo il suo contorno.
 */
internal fun restRimProfile(depthPx: Float, bandPx: Float, sigmaPx: Float): Float {
  val clip = (depthPx + 0.5f).coerceIn(0f, 1f)
  if (clip <= 0f) return 0f
  val stroke = if (sigmaPx > 0f) {
    normalCdf((bandPx - depthPx) / sigmaPx) - normalCdf((-bandPx - depthPx) / sigmaPx)
  } else {
    if (depthPx <= bandPx) 1f else 0f
  }
  return clip * stroke.coerceIn(0f, 1f)
}

/** La funzione di ripartizione della normale, con l'erf di Abramowitz-Stegun (errore sotto 2e-7). */
private fun normalCdf(x: Float): Float {
  val z = x / 1.4142135f
  val t = 1f / (1f + 0.3275911f * abs(z))
  val poly = ((((1.0614054f * t - 1.4531521f) * t + 1.4214138f) * t - 0.28449672f) * t + 0.2548296f) * t
  val y = 1f - poly * exp(-z * z)
  val erf = if (z >= 0f) y else -y
  return 0.5f * (1f + erf)
}

/**
 * La fascia di triangoli del bordo di una goccia: anelli paralleli al contorno, dal mezzo pixel fuori
 * fino a dove il profilo del bordo a riposo ([restRimProfile]) si spegne, ognuno spostato verso
 * l'interno lungo la normale di ogni vertice e col peso del profilo a quella profondita' come alfa del
 * vertice. Gli anelli estremi pesano zero: i lati dei triangoli non si vedono, e la fascia non chiede
 * antialias. Riusata fotogramma dopo fotogramma: nessuna allocazione a regime.
 */
internal class DropRimMesh {
  var positions = FloatArray(0)
    private set
  var colors = IntArray(0)
    private set
  var indices = ShortArray(0)
    private set
  var vertexFloats = 0
    private set
  var indexCount = 0
    private set
  var rings = 0
    private set
  private val depths = FloatArray(MaxRimRings)
  private val alphas = FloatArray(MaxRimRings)
  private var normals = FloatArray(0)

  /** La profondita' dell'anello [k], in pixel dentro il contorno. */
  fun depth(k: Int): Float = depths[k]

  /** Il peso dell'anello [k]. */
  fun alpha(k: Int): Float = alphas[k]

  /**
   * Costruisce la fascia sul contorno [verts] (coppie x, y, senso orario sullo schermo) di [count]
   * punti, nelle coordinate del riquadro con l'angolo in ([originX], [originY]). Falso se non c'e'
   * niente da disegnare.
   */
  fun build(verts: FloatArray, count: Int, originX: Float, originY: Float, bandPx: Float, sigmaPx: Float): Boolean {
    if (count < 3 || !(bandPx > 0f)) return false
    val reach = bandPx + 3f * sigmaPx
    var k = 0
    var u = -0.5f
    while (k < MaxRimRings - 1 && u < reach) {
      depths[k] = u
      alphas[k] = restRimProfile(u, bandPx, sigmaPx)
      k++
      u += RimRingStepPx
    }
    depths[k] = u
    alphas[k] = 0f
    alphas[0] = 0f
    rings = k + 1

    val vertices = rings * count
    if (positions.size < vertices * 2) {
      positions = FloatArray(vertices * 2)
      colors = IntArray(vertices)
    }
    if (normals.size < count * 2) normals = FloatArray(count * 2)
    val quads = (rings - 1) * count
    if (indices.size < quads * 6) indices = ShortArray(quads * 6)

    // La normale interna di ogni vertice: quella della corda fra i due vicini. Senso orario sullo
    // schermo: la normale interna di (tx, ty) e' (-ty, tx).
    for (i in 0 until count) {
      val prev = ((i + count - 1) % count) * 2
      val next = ((i + 1) % count) * 2
      val tx = verts[next] - verts[prev]
      val ty = verts[next + 1] - verts[prev + 1]
      val len = sqrt(tx * tx + ty * ty)
      normals[i * 2] = if (len > 0.0001f) -ty / len else 0f
      normals[i * 2 + 1] = if (len > 0.0001f) tx / len else 0f
    }
    for (ring in 0 until rings) {
      val d = depths[ring]
      val a = (alphas[ring] * 255f + 0.5f).toInt().coerceIn(0, 255)
      val color = (a shl 24) or 0x00FFFFFF
      for (i in 0 until count) {
        val v = ring * count + i
        positions[v * 2] = verts[i * 2] - originX + normals[i * 2] * d
        positions[v * 2 + 1] = verts[i * 2 + 1] - originY + normals[i * 2 + 1] * d
        colors[v] = color
      }
    }
    var n = 0
    for (ring in 0 until rings - 1) {
      val a0 = ring * count
      val b0 = (ring + 1) * count
      for (i in 0 until count) {
        val j = (i + 1) % count
        indices[n++] = (a0 + i).toShort()
        indices[n++] = (a0 + j).toShort()
        indices[n++] = (b0 + i).toShort()
        indices[n++] = (b0 + i).toShort()
        indices[n++] = (a0 + j).toShort()
        indices[n++] = (b0 + j).toShort()
      }
    }
    indexCount = n
    vertexFloats = vertices * 2
    return true
  }
}

/** Il passo fra due anelli della fascia del bordo della goccia, in pixel. */
private const val RimRingStepPx = 1f

/** Al piu' tanti anelli: un bordo largo quattro pixel con la sua sfocatura ne vuole una decina. */
private const val MaxRimRings = 20
