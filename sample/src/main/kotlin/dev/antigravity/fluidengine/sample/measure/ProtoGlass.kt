package dev.antigravity.fluidengine.sample.measure

import android.content.res.Resources
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.colorControls
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.runtimeShaderEffect
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Il prototipo della via A (piano «iOS in Android», 3.3 e 3.6), montato con le API pubbliche dell'engine.
 *
 * **Non è il materiale.** È ciò che serve a decidere, con delle misure, come sarà fatto: il corpo è la
 * sonda `sonda_via_a.agsl` (un ingresso, campo fino a otto pezzi) dentro la catena vera,
 *
 *     colorControls(saturazione) → blur(Clamp) → createBlendModeEffect(Plus, w·nitido, (1−w)·sfocato) → shader
 *
 * e la luce è `sonda_luce.agsl`, uno shader senza ingressi disegnato sopra. Le due sorgenti sono le
 * stesse del banco (`tools/glassbench/shaders`), copiate fra le risorse della galleria senza toccarle.
 * Mancano il velo adattivo, il raccordo continuo degli angoli, la taratura e l'ombra a due maschere:
 * lavoro di F1A.
 */
internal class ProtoProgram(val source: String, val maxPieces: Int)

/** Le sorgenti lette dalle risorse, e le loro varianti per numero di pezzi. */
internal class ProtoSources(private val body: String, private val light: String, val edge: String) {

  /**
   * La variante del corpo che porta al più [maxPieces] pezzi (1, 2, 4, 8: piano 3.7). La sorgente è
   * una; le varianti nascono in memoria cambiando la sola costante `MAX_PIECES`, come farà l'engine
   * quando genererà `Body1`…`Body8` dallo stesso blocco.
   */
  fun body(maxPieces: Int = MeasureConfig.MaxPieces): ProtoProgram = variant(body, maxPieces)

  fun light(maxPieces: Int = MeasureConfig.MaxPieces): ProtoProgram = variant(light, maxPieces)

  /**
   * Le sorgenti che il compilatore di questo dispositivo rifiuta, col suo messaggio. Il banco le compila
   * sul PC, ma «compila su Skia» e «compila su questo driver» non sono la stessa affermazione: meglio
   * una riga d'errore leggibile che la pagina che cade al primo disegno.
   */
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  fun refused(): Map<String, String> {
    val errors = LinkedHashMap<String, String>()
    for ((name, source) in listOf("sonda_via_a" to body, "sonda_luce" to light, "misura_bordo" to edge)) {
      protoCompileError(source)?.let { errors[name] = it }
    }
    return errors
  }

  private fun variant(source: String, maxPieces: Int): ProtoProgram {
    if (maxPieces == MeasureConfig.MaxPieces) return ProtoProgram(source, maxPieces)
    check(source.contains(MaxPiecesLine)) { "La sonda non dichiara piu' `$MaxPiecesLine`: le varianti non si possono derivare." }
    return ProtoProgram(source.replace(MaxPiecesLine, "const int MAX_PIECES = $maxPieces;"), maxPieces)
  }

  companion object {
    private const val MaxPiecesLine = "const int MAX_PIECES = 8;"

    fun load(resources: Resources): ProtoSources = ProtoSources(
      body = resources.readRawText(R.raw.sonda_via_a),
      light = resources.readRawText(R.raw.sonda_luce),
      edge = resources.readRawText(R.raw.misura_bordo),
    )

    /** La variante più piccola che tiene [pieces] pezzi: 1, 2, 4 o 8 (piano 3.7). */
    fun fittingVariant(pieces: Int): Int = when {
      pieces <= 1 -> 1
      pieces <= 2 -> 2
      pieces <= 4 -> 4
      else -> MeasureConfig.MaxPieces
    }
  }
}

/** Compila [source] col compilatore AGSL del dispositivo: nullo se la accetta, altrimenti il suo messaggio. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun protoCompileError(source: String): String? = try {
  android.graphics.RuntimeShader(source)
  null
} catch (error: RuntimeException) {
  error.message ?: error.toString()
}

private fun Resources.readRawText(id: Int): String =
  openRawResource(id).bufferedReader(Charsets.UTF_8).use { it.readText() }

/**
 * I numeri del prototipo, in dp. Sono quelli della scena di prova del banco (`glassbench.py`, prova
 * «materiale»): messi a occhio, non tarati. Qui contano per il costo, non per la resa.
 */
internal class ProtoSpec(
  val saturation: Float = 1.3f,
  val blurDp: Float = 12f,
  val thicknessDp: Float = 10f,
  val refractionDp: Float = 12f,
  /** Di quanto la pressione piena approfondisce lo spostamento (`1 + pressione · boost`). */
  val pressBoost: Float = 0.6f,
  val dome: Float = 0f,
  val dispersion: Float = 1f,
  val blendDp: Float = 8f,
  val veil: Color = Color(0x40FFFFFF),
  val bodyShade: Float = 0.04f,
  val rimWidthDp: Float = 0.7f,
  val rimDark: Color = Color(0x38000000),
  val reflexStrength: Float = 0.55f,
  val reflexDepthDp: Float = 3f,
  val reflexFloor: Float = 0.15f,
  val glowRadiusDp: Float = 20f,
) {
  companion object {
    /** Sul lato scuro il filo scuro sparisce e resta il riflesso (R5 M3); il velo scurisce invece di schiarire. */
    fun of(blurDp: Float, dispersion: Float, dark: Boolean): ProtoSpec = ProtoSpec(
      blurDp = blurDp,
      dispersion = dispersion,
      veil = if (dark) Color(0x401C1C1E) else Color(0x40FFFFFF),
      rimDark = if (dark) Color(0x00000000) else Color(0x38000000),
    )
  }
}

/**
 * Ciò che si anima, letto mentre si costruisce la catena o si disegna la luce: uno stato letto qui
 * rifà la catena (o ridisegna la luce) senza ricomporre niente.
 */
internal class ProtoDrive(
  val press: () -> Float = { 0f },
  val materialize: () -> Float = { 1f },
  /** `w`, il peso del fondale nitido nella mescola a monte: 0 a riposo. */
  val sharpWeight: () -> Float = { 0f },
  val glow: () -> Float = { 0f },
  /** Dove sta il dito lungo la larghezza, 0..1. */
  val glowAt: () -> Float = { 0.5f },
)

/**
 * Il campo: fino a otto rettangoli arrotondati, in pixel del nodo a risoluzione piena. Lo riempie chi
 * monta il prototipo, dalla taglia del nodo, e lo leggono sia il corpo sia la luce.
 */
internal class ProtoField {
  var count = 0
    private set
  val rects = FloatArray(MeasureConfig.MaxPieces * 4)
  val radii = FloatArray(MeasureConfig.MaxPieces * 4)

  fun clear() {
    count = 0
  }

  /** Un pezzo: centro, mezza taglia, raggio (lo stesso ai quattro angoli). */
  fun add(centerX: Float, centerY: Float, halfWidth: Float, halfHeight: Float, radius: Float) {
    if (count >= MeasureConfig.MaxPieces) return
    val base = count * 4
    rects[base] = centerX
    rects[base + 1] = centerY
    rects[base + 2] = halfWidth
    rects[base + 3] = halfHeight
    val r = min(radius, min(halfWidth, halfHeight))
    radii[base] = r
    radii[base + 1] = r
    radii[base + 2] = r
    radii[base + 3] = r
    count++
  }
}

/** Dispone i pezzi dentro un nodo largo `width` e alto `height` pixel, a densità `density`. */
internal typealias ProtoLayout = ProtoField.(width: Float, height: Float, density: Float) -> Unit

/** L'ombra del prototipo: una maschera sola (quella di oggi), corta come la vuole iOS 27. */
private val ProtoShadow = Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.08f))
private val ProtoShadowBlock: () -> Shadow? = { ProtoShadow }

/**
 * Il margine della cattura del piano (3.3): la sfocatura meno lo spessore della lente che la copre,
 * più il margine del campo. Arrotondato a pixel interi, perché il nodo sposta il layer di un numero
 * intero di pixel e lo shader deve togliere esattamente lo stesso.
 */
private fun protoPadding(blurPx: Float, thicknessPx: Float, marginPx: Float): Float =
  ceil(max(0f, blurPx - thicknessPx) + marginPx)

/**
 * Posa il prototipo su questo nodo.
 *
 * @param mount come il corpo sta sui layer: è la variabile del cancello C1.
 * @param shape il ritaglio hardware del nodo. Un rettangolo (con [marginDp] attorno ai pezzi) quando la
 *   sagoma deve ritagliarla lo shader, come per una lastra che viaggia o un campo a più pezzi.
 * @param light la variante della luce, o nulla per spegnerla.
 * @param scale la risoluzione della cattura (`backdropScale`).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun Modifier.protoGlass(
  source: LayerBackdrop,
  body: ProtoProgram,
  light: ProtoProgram?,
  spec: ProtoSpec,
  mount: MeasureMount,
  shape: Shape,
  marginDp: Float,
  scale: Float,
  shadow: Boolean,
  drive: ProtoDrive,
  layout: ProtoLayout,
): Modifier {
  val baseDensity = LocalDensity.current.density
  val field = remember { ProtoField() }
  val uniforms = remember(body) { ProtoBodyUniforms(body.maxPieces) }
  val shapeBlock = remember(shape) { { shape } }
  val upstream = remember { UpstreamCache() }
  val painter = remember(light) { light?.let { ProtoLight(it) } }
  val onDrawSurface: (DrawScope.() -> Unit)? = remember(painter, spec, drive, layout) {
    painter?.let { lightPainter ->
      val block: DrawScope.() -> Unit = {
        field.clear()
        field.layout(size.width, size.height, density)
        lightPainter.draw(this, field, spec, drive)
      }
      block
    }
  }
  val shadowBlock = if (shadow) ProtoShadowBlock else null

  /** Gli uniform del corpo, dal punto di vista di uno scope dell'effetto (già alla scala della cattura). */
  fun BackdropEffectScope.bodyUniforms(shader: RuntimeShader, paddingPx: Float) {
    val captureScale = density / baseDensity
    field.clear()
    field.layout(size.width / captureScale, size.height / captureScale, baseDensity)
    uniforms.write(
      shader = shader,
      field = field,
      captureScale = captureScale,
      paddingPx = paddingPx,
      blendPx = spec.blendDp.dp.toPx(),
      thicknessPx = spec.thicknessDp.dp.toPx(),
      refractionPx = spec.refractionDp.dp.toPx() * (1f + drive.press().coerceIn(0f, 1f) * spec.pressBoost),
      spec = spec,
      materialize = drive.materialize().coerceIn(0f, 1f),
    )
  }

  return when (mount) {
    MeasureMount.One, MeasureMount.OneStable -> {
      val stable = mount == MeasureMount.OneStable
      val effects: BackdropEffectScope.() -> Unit = remember(body, spec, drive, layout, stable, marginDp) {
        {
          // Prima del primo disegno lo scope non ha ancora una taglia: non c'è niente da costruire.
          if (size.isSpecified) {
            measureSection("Misure.catena") {
              val blurPx = spec.blurDp.dp.toPx()
              viaAUpstream(spec.saturation, blurPx, drive.sharpWeight(), if (stable) upstream else null)
              val paddingPx = protoPadding(blurPx, spec.thicknessDp.dp.toPx(), marginDp.dp.toPx())
              padding = paddingPx
              runtimeShaderEffect("misure-corpo", body.source, "content") { bodyUniforms(this, paddingPx) }
            }
          }
        }
      }
      this.drawBackdrop(
        backdrop = source,
        shape = shapeBlock,
        effects = effects,
        highlight = null,
        shadow = shadowBlock,
        onDrawSurface = onDrawSurface,
        backdropScale = scale,
        nestInContainer = false,
      )
    }

    MeasureMount.Two, MeasureMount.TwoComposited -> {
      val composited = mount == MeasureMount.TwoComposited
      val blurLayer = rememberGraphicsLayer()
      val sharpLayer = rememberGraphicsLayer()
      val staged = remember(source, blurLayer, sharpLayer, composited) {
        StagedBackdrop(source, blurLayer, if (composited) sharpLayer else null)
      }
      val effects: BackdropEffectScope.() -> Unit = remember(body, spec, drive, layout, staged, marginDp) {
        {
          if (size.isSpecified) {
            measureSection("Misure.catena") {
              val blurPx = spec.blurDp.dp.toPx()
              val weight = drive.sharpWeight().coerceIn(0f, 1f)
              if (composited) {
                // Il primo layer sfoca e basta; la mescola è la composizione dei due layer, con `w` come
                // loro alfa. La saturazione è lineare come la sfocatura, quindi può stare dopo.
                staged.configure(upstream.blurOnly(blurPx), blurAlpha = 1f - weight, sharpAlpha = weight)
                colorControls(saturation = spec.saturation)
              } else {
                // Il primo layer porta tutta la catena a monte, tenuta ferma finché i suoi numeri non cambiano.
                viaAUpstream(spec.saturation, blurPx, weight, upstream)
                staged.configure(renderEffect, blurAlpha = 1f, sharpAlpha = 0f)
                renderEffect = null
              }
              val paddingPx = protoPadding(blurPx, spec.thicknessDp.dp.toPx(), marginDp.dp.toPx())
              padding = paddingPx
              runtimeShaderEffect("misure-corpo", body.source, "content") { bodyUniforms(this, paddingPx) }
            }
          }
        }
      }
      this.drawBackdrop(
        backdrop = staged,
        shape = shapeBlock,
        effects = effects,
        highlight = null,
        shadow = shadowBlock,
        onDrawSurface = onDrawSurface,
        backdropScale = scale,
        nestInContainer = false,
      )
    }
  }
}

/**
 * La catena a monte dello shader, com'è nel piano (3.6): saturazione, sfocatura, e la mescola `w` fra
 * il fondale nitido e quello sfocato fatta **prima** dello shader, perché un `RenderEffect` collega un
 * ingresso solo.
 *
 * Con [cache] i nodi restano gli stessi oggetti finché saturazione, raggio e `w` non cambiano; senza,
 * si ricostruiscono a ogni chiamata, che è ciò che fa oggi ogni superficie dell'engine.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun BackdropEffectScope.viaAUpstream(saturation: Float, blurPx: Float, sharpWeight: Float, cache: UpstreamCache?) {
  val weight = sharpWeight.coerceIn(0f, 1f)
  val cached = cache?.find(saturation, blurPx, weight)
  if (cached != null) {
    renderEffect = cached
    return
  }
  renderEffect = null
  colorControls(saturation = saturation)
  val sharp = renderEffect
  blur(blurPx)
  val blurred = renderEffect
  val blended = upstreamBlend(sharp, blurred, weight)
  renderEffect = blended
  cache?.store(saturation, blurPx, weight, blended)
}

/** `createBlendModeEffect(dst = (1 − w)·sfocato, src = w·nitido, Plus)`: la via A di R3. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun upstreamBlend(sharp: RenderEffect?, blurred: RenderEffect?, weight: Float): RenderEffect {
  val soft = blurred ?: sharp
  val dst = if (soft != null) {
    android.graphics.RenderEffect.createColorFilterEffect(alphaFilter(1f - weight), soft.asAndroidRenderEffect())
  } else {
    android.graphics.RenderEffect.createColorFilterEffect(alphaFilter(1f - weight))
  }
  val src = if (sharp != null) {
    android.graphics.RenderEffect.createColorFilterEffect(alphaFilter(weight), sharp.asAndroidRenderEffect())
  } else {
    android.graphics.RenderEffect.createColorFilterEffect(alphaFilter(weight))
  }
  return android.graphics.RenderEffect
    .createBlendModeEffect(dst, src, android.graphics.BlendMode.PLUS)
    .asComposeRenderEffect()
}

/** Scala il solo alfa: sul colore premoltiplicato vuol dire moltiplicare tutti e quattro i canali. */
private fun alphaFilter(alpha: Float): android.graphics.ColorFilter = android.graphics.ColorMatrixColorFilter(
  android.graphics.ColorMatrix(
    floatArrayOf(
      1f, 0f, 0f, 0f, 0f,
      0f, 1f, 0f, 0f, 0f,
      0f, 0f, 1f, 0f, 0f,
      0f, 0f, 0f, alpha, 0f,
    ),
  ),
)

/** L'ultima catena a monte costruita, tenuta finché i suoi numeri non cambiano. */
private class UpstreamCache {
  private var saturation = Float.NaN
  private var blurPx = Float.NaN
  private var weight = Float.NaN
  private var effect: RenderEffect? = null

  private var blurOnlyPx = Float.NaN
  private var blurOnly: RenderEffect? = null

  fun find(saturation: Float, blurPx: Float, weight: Float): RenderEffect? =
    if (saturation == this.saturation && blurPx == this.blurPx && weight == this.weight) effect else null

  fun store(saturation: Float, blurPx: Float, weight: Float, effect: RenderEffect) {
    this.saturation = saturation
    this.blurPx = blurPx
    this.weight = weight
    this.effect = effect
  }

  /** La sola sfocatura (`Clamp`), per il montaggio in cui la mescola la fanno i layer. */
  fun blurOnly(blurPx: Float): RenderEffect? {
    if (blurPx <= 0f) return null
    if (blurPx != blurOnlyPx || blurOnly == null) {
      blurOnlyPx = blurPx
      blurOnly = BlurEffect(blurPx, blurPx, TileMode.Clamp)
    }
    return blurOnly
  }
}

/**
 * Il fondale del montaggio a due layer (piano 3.5): invece della pagina, alla superficie si dà un
 * **primo layer** che la pagina l'ha già catturata e sfocata. La superficie resta un `drawBackdrop`
 * qualunque e il suo effetto è il solo shader: un uniform che cambia riesegue lo shader sul primo
 * layer, che HWUI tiene pronto, e la sfocatura non si ripaga.
 *
 * Il primo layer si registra quando la superficie rifà la propria cattura (cioè quando si muove
 * rispetto alla pagina: le stesse regole di sempre) e tiene la pagina per riferimento, quindi segue il
 * suo contenuto da solo. Con [sharpLayer] accanto al layer sfocato ce n'è uno nitido, e la mescola `w`
 * è l'alfa dei due.
 */
private class StagedBackdrop(
  private val source: Backdrop,
  private val blurLayer: GraphicsLayer,
  private val sharpLayer: GraphicsLayer?,
) : Backdrop {

  override val isCoordinatesDependent: Boolean get() = source.isCoordinatesDependent

  // Ciò che invalida la cattura è la geometria della pagina, non quella di questo involucro.
  override val layerSources: List<Any> get() = source.layerSources

  override fun isReadyToSample(): Boolean = source.isReadyToSample()

  override fun forEachPart(block: (Backdrop) -> Unit) {
    block(this)
    source.forEachPart(block)
  }

  /** L'effetto del primo layer e i pesi della mescola. Chiamata mentre si costruisce la catena. */
  fun configure(effect: RenderEffect?, blurAlpha: Float, sharpAlpha: Float) {
    blurLayer.renderEffect = effect
    blurLayer.alpha = blurAlpha
    if (sharpLayer != null) {
      // `Plus` sul layer sfocato: nitido·w + sfocato·(1 − w), come il nodo di mescola della via A.
      blurLayer.blendMode = BlendMode.Plus
      sharpLayer.alpha = sharpAlpha
    }
  }

  override fun DrawScope.drawBackdrop(
    density: Density,
    coordinates: LayoutCoordinates?,
    layerBlock: (GraphicsLayerScope.() -> Unit)?,
  ) {
    // Chi chiama è la cattura di `drawBackdrop`: lo scope è grande quanto la cattura (taglia più due
    // margini), e il canvas è già spostato del margine e scalato alla risoluzione ridotta.
    val effectScope = density as? BackdropEffectScope
    val pad = effectScope?.padding ?: 0f
    val captureScale =
      if (effectScope != null && this.density > 0f && effectScope.density > 0f) effectScope.density / this.density else 1f
    val recordSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))

    val page: DrawScope.() -> Unit = {
      withTransform({
        translate(pad, pad)
        scale(captureScale, captureScale, Offset.Zero)
      }) {
        with(source) { drawBackdrop(density, coordinates, layerBlock) }
      }
    }
    blurLayer.topLeft = IntOffset.Zero
    blurLayer.record(recordSize, page)
    sharpLayer?.let { sharp ->
      sharp.topLeft = IntOffset.Zero
      sharp.clip = true
      sharp.record(recordSize, page)
    }

    // I layer vanno nella cattura uno a uno: si disfa la trasformazione che la cattura ha già messo.
    withTransform({
      scale(1f / captureScale, 1f / captureScale, Offset.Zero)
      translate(-pad, -pad)
    }) {
      sharpLayer?.let { drawLayer(it) }
      drawLayer(blurLayer)
    }
  }
}

/** Gli uniform del corpo. Gli array hanno la lunghezza della variante: `MAX_PIECES` float4. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ProtoBodyUniforms(private val maxPieces: Int) {
  private val rects = FloatArray(maxPieces * 4)
  private val radii = FloatArray(maxPieces * 4)

  fun write(
    shader: RuntimeShader,
    field: ProtoField,
    captureScale: Float,
    paddingPx: Float,
    blendPx: Float,
    thicknessPx: Float,
    refractionPx: Float,
    spec: ProtoSpec,
    materialize: Float,
  ) {
    val count = min(field.count, maxPieces)
    for (index in 0 until maxPieces * 4) {
      rects[index] = if (index < count * 4) field.rects[index] * captureScale else 0f
      radii[index] = if (index < count * 4) field.radii[index] * captureScale else 0f
    }
    // La cattura parte un margine prima del nodo: `offset` riporta `coord` nel sistema del nodo.
    shader.setFloatUniform("offset", -paddingPx, -paddingPx)
    shader.setFloatUniform("pieceCount", count.toFloat())
    shader.setFloatUniform("pieceRect", rects)
    shader.setFloatUniform("pieceRadii", radii)
    shader.setFloatUniform("blend", blendPx)
    shader.setFloatUniform("thickness", thicknessPx)
    // Negato come nell'engine: lo spostamento tira il fondale verso l'interno.
    shader.setFloatUniform("refraction", -refractionPx)
    shader.setFloatUniform("dome", spec.dome)
    shader.setFloatUniform("dispersion", spec.dispersion)
    shader.setFloatUniform("materialize", materialize)
    shader.setColorUniform("veilColor", spec.veil)
    shader.setFloatUniform("bodyShade", spec.bodyShade)
    shader.setFloatUniform("aa", 1f)
  }
}

/**
 * La luce: un `drawRect` con uno shader senza ingressi, in `SrcOver`, sopra il corpo. Non sta nella
 * catena del `RenderEffect`, quindi un suo uniform che cambia ridisegna il nodo e nient'altro.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ProtoLight(private val program: ProtoProgram) {
  private val shader = android.graphics.RuntimeShader(program.source)
  private val brush = ShaderBrush(shader)
  private val rects = FloatArray(program.maxPieces * 4)
  private val radii = FloatArray(program.maxPieces * 4)

  fun draw(scope: DrawScope, field: ProtoField, spec: ProtoSpec, drive: ProtoDrive) {
    val density = scope.density
    val count = min(field.count, program.maxPieces)
    for (index in 0 until program.maxPieces * 4) {
      rects[index] = if (index < count * 4) field.rects[index] else 0f
      radii[index] = if (index < count * 4) field.radii[index] else 0f
    }
    val glow = drive.glow().coerceIn(0f, 1f)
    shader.setFloatUniform("offset", 0f, 0f)
    shader.setFloatUniform("pieceCount", count.toFloat())
    shader.setFloatUniform("pieceRect", rects)
    shader.setFloatUniform("pieceRadii", radii)
    shader.setFloatUniform("blend", spec.blendDp * density)
    shader.setFloatUniform("rimWidth", spec.rimWidthDp * density)
    shader.setColorUniform("rimDark", spec.rimDark.toArgb())
    shader.setFloatUniform("reflexDir", 0f, -1f)
    shader.setFloatUniform("reflexWidths", 2f, 2f)
    shader.setFloatUniform("reflexStrength", spec.reflexStrength)
    shader.setFloatUniform("reflexDepth", spec.reflexDepthDp * density)
    shader.setFloatUniform("reflexFloor", spec.reflexFloor)
    shader.setFloatUniform("glowCenter", scope.size.width * drive.glowAt().coerceIn(0f, 1f), scope.size.height / 2f)
    shader.setFloatUniform("glowRadius", spec.glowRadiusDp * density)
    shader.setFloatUniform("glow", glow)
    shader.setFloatUniform("glowFill", 0f)
    shader.setColorUniform("lightColor", Color.White.toArgb())
    shader.setFloatUniform("materialize", drive.materialize().coerceIn(0f, 1f))
    shader.setFloatUniform("aa", 1f)
    scope.drawRect(brush)
  }
}
