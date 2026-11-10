package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.blur
import kotlin.math.ceil
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.glassShadowColor
import dev.antigravity.fluidengine.ui.fluid.sanitized
import kotlin.math.roundToInt

/**
 * L'ombra di un pannello che viaggia: sfocata UNA volta per taglia di pannello, poi stirata sulla
 * sagoma corrente a ogni fotogramma.
 *
 * Esiste perche' `glassSurface` disegna l'ombra col renderer (`ShadowNode`), che la registra di nuovo
 * a ogni cambio di taglia — e una sagoma che viaggia cambia taglia a ogni fotogramma. Il
 * `BlurMaskFilter` passato sul path della silhouette a ogni fotogramma era il lag dell'apertura: HWUI
 * rasterizza la maschera daccapo ogni volta che il path cambia, e in un morph il path cambia sempre.
 * Qui il blur si paga alla prima richiesta, in un bitmap a mezza risoluzione (l'ombra e' morbida per
 * definizione: mezza risoluzione non si vede), e il fotogramma costa un `drawImage`. Un'ombra morbida
 * stirata e una esatta sono indistinguibili.
 *
 * **Il colore e' quello di `glassSurface`**, non uno proprio: nero al 16% sul chiaro e al 10% sullo
 * scuro ([glassShadowColor]), per il peso del ruolo ([GlassOptics.shadowAlpha]) — a riposo la
 * finestra e' una lastra e deve avere l'ombra di una lastra. La finestra dei modali dipingeva nero
 * al 36% pieno, sei volte il peso di ogni altro pannello. Anche la distanza e' quella del vetro: il
 * sesto del raggio ([GlassOptics.shadowRadius]).
 *
 * Va **dietro** la superficie (prima di `fluidPhysicsSurface` nella catena): la superficie campiona
 * la pagina e disegna il proprio vetro opaco sopra, quindi non serve scavare l'ombra sotto la sagoma
 * come fa il renderer.
 *
 * @param state da cui prende la sagoma corrente, in pixel di layout del nodo che la ospita.
 * @param restSize la taglia del pannello a riposo, per cui si sfoca il bitmap: costante lungo il
 *   viaggio (la taglia d'arrivo), e la sagoma corrente la si stira da li'.
 * @param cornerRadiusPx l'angolo del pannello a riposo, in pixel.
 * @param amount 0..1, letto in fase di disegno: sotto 0,02 non si disegna niente. Il fotogramma di
 *   confine di un nodo puo' uscire con i valori di default: a materiale zero la displaylist e'
 *   vuota, e una displaylist vuota non ha lampi da rigiocare.
 * @param darkSide quale lato del tema, da `GlassDefaults.isDarkSurface()`.
 */
@Composable
internal fun Modifier.fluidPhysicsShadow(
  state: FluidPhysicsState,
  optics: GlassOptics,
  restSize: () -> Size,
  cornerRadiusPx: Float,
  amount: () -> Float,
  darkSide: Boolean = GlassDefaults.isDarkSurface(),
): Modifier {
  val density = LocalDensity.current
  val resolved = remember(optics) { optics.sanitized() }
  val cache = remember { PhysicsShadowCache() }
  val blurPx = with(density) { resolved.shadowRadius.toPx() }
  // La distanza dall'ombra alla sagoma e' quella di `Shadow` (raggio / 6): un pannello fermo e uno
  // in viaggio non possono avere l'ombra a quote diverse.
  val dropPx = blurPx / 6f
  val colorAlpha = glassShadowColor(darkSide).alpha
  val weight = resolved.shadowAlpha * colorAlpha
  // Da quando `fluidPhysicsSurface` disegna la propria ombra (3.0), chi chiama ancora questa la
  // dichiara allo stato: la superficie allora tace, e sotto il vetro resta un'ombra sola.
  DisposableEffect(state) {
    state.externalShadows += 1
    onDispose { state.externalShadows -= 1 }
  }

  return this.drawBehind {
    val strength = amount()
    if (strength > PhysicsShadowGate && weight > 0f) {
      val rest = restSize()
      val shadow = cache.obtain(rest.width, rest.height, cornerRadiusPx, blurPx)
      val bounds = state.ensurePlan().bounds
      if (shadow != null && bounds.width > 1f && bounds.height > 1f) {
        val pad = blurPx * 2f
        val sx = bounds.width / rest.width
        val sy = bounds.height / rest.height
        drawImage(
          image = shadow,
          dstOffset = IntOffset(
            (bounds.left - pad * sx).roundToInt(),
            (bounds.top - pad * sy + dropPx).roundToInt(),
          ),
          dstSize = IntSize(
            (bounds.width + pad * 2f * sx).roundToInt().coerceAtLeast(1),
            (bounds.height + pad * 2f * sy).roundToInt().coerceAtLeast(1),
          ),
          alpha = (weight * strength).coerceIn(0f, 1f),
        )
      }
    }
  }
}

/** Sotto questa forza il materiale non si disegna: vedi il parametro `amount`. */
private const val PhysicsShadowGate = 0.02f

/**
 * Il bitmap dell'ombra: nero con la maschera sfocata, per una taglia, un angolo e un raggio.
 * Si rifa solo se uno dei quattro cambia.
 */
internal class PhysicsShadowCache {
  private var image: ImageBitmap? = null
  private var keyWidth = -1f
  private var keyHeight = -1f
  private var keyRadius = -1f
  private var keyBlur = -1f

  fun obtain(paneWidth: Float, paneHeight: Float, cornerRadius: Float, blur: Float): ImageBitmap? {
    if (paneWidth < 1f || paneHeight < 1f || blur < 1f) return null
    if (image == null || paneWidth != keyWidth || paneHeight != keyHeight ||
      cornerRadius != keyRadius || blur != keyBlur
    ) {
      keyWidth = paneWidth
      keyHeight = paneHeight
      keyRadius = cornerRadius
      keyBlur = blur
      val pad = blur * 2f
      val scale = 0.5f
      val bitmap = createBitmap(
        (((paneWidth + pad * 2f) * scale).toInt()).coerceAtLeast(2),
        (((paneHeight + pad * 2f) * scale).toInt()).coerceAtLeast(2),
        android.graphics.Bitmap.Config.ARGB_8888,
      )
      val canvas = android.graphics.Canvas(bitmap)
      val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        maskFilter = android.graphics.BlurMaskFilter(
          (blur * scale).coerceAtLeast(1f),
          android.graphics.BlurMaskFilter.Blur.NORMAL,
        )
      }
      canvas.drawRoundRect(
        pad * scale,
        pad * scale,
        (pad + paneWidth) * scale,
        (pad + paneHeight) * scale,
        cornerRadius * scale,
        cornerRadius * scale,
        paint,
      )
      image = bitmap.asImageBitmap()
    }
    return image
  }
}

/**
 * L'ombra che `fluidPhysicsSurface` disegna da se' (3.0): **a riposo quella di `glassSurface` al
 * pixel**, in viaggio le fotografie dei due capi stirate sulla sagoma e sfumate fra loro.
 *
 * Prima la superficie fisica non ne aveva nessuna — "il distacco lo raccontano la dislocazione e il
 * bordo" — e un pannello fermo di Fluid-physics accanto a una lastra dello stesso ruolo era piatto:
 * la differenza piu' visibile fra le due, e la prima ragione per cui il morph non sembrava vetro.
 *
 * Come la registra il renderer (`ShadowNode`): il contorno della forma ferma, col colore del lato
 * del tema e il `BlurMaskFilter` del raggio, spostato di un sesto del raggio in giu', e scavato sotto
 * la sagoma, in un layer fuori schermo. Registrata UNA volta per forma, raggio e colore; poi il layer
 * si ridisegna, e alfa e posizione non registrano niente. In viaggio non si registra mai di nuovo
 * quello che c'e' gia': il layer della partenza e quello dell'arrivo (due posti, tenuti finche'
 * servono) si stirano dal loro riquadro a quello della sagoma corrente — una texture scalata, non una
 * maschera rifatta — e si sfumano col progresso. A t = 0 e' l'ombra della partenza, a t = 1 quella
 * dell'arrivo, e a riposo quella della lastra e basta.
 */
internal class PhysicsShadowLayers(first: GraphicsLayer, second: GraphicsLayer) {

  private class Slot(val layer: GraphicsLayer) {
    var form: FluidForm? = null
    var radiusPx: Float = Float.NaN
    var color: Color? = null
    var usedAt: Long = -1L
  }

  private val slots = arrayOf(Slot(first), Slot(second))
  private var stamp = 0L
  private val paint = Paint()
  private val clearPaint = Paint().apply { blendMode = BlendMode.Clear }
  private val path = Path()
  private val scratchPlan = PhysicsRenderPlan()

  init {
    first.compositingStrategy = CompositingStrategy.Offscreen
    second.compositingStrategy = CompositingStrategy.Offscreen
  }

  /**
   * @param radiusPx il raggio dell'ombra del ruolo ([GlassOptics.shadowRadius]) in pixel.
   * @param alpha il peso: `shadowAlpha` per intensita' per qualita', come in `glassSurface`.
   */
  fun draw(scope: DrawScope, state: FluidPhysicsState, radiusPx: Float, color: Color, alpha: Float) {
    if (!(radiusPx >= 1f) || !(alpha > 0.001f)) return
    stamp++
    val rest = state.restFormNow()
    if (rest != null) {
      val layer = obtain(scope, rest, radiusPx, color) ?: return
      scope.drawStretched(layer, rest.frame, rest.frame, radiusPx, alpha)
      return
    }
    val target = state.journeyTargetNow() ?: return
    val origin = state.journeyOriginNow()?.takeIf { it != target }
    val bounds = state.ensurePlan().bounds
    val t = state.progress.coerceIn(0f, 1f)
    // Prima si prendono i due posti, poi si disegna: chiedere il secondo non deve poter sfrattare
    // il primo nello stesso fotogramma.
    val targetLayer = obtain(scope, target, radiusPx, color)
    val originLayer = if (origin != null && t < 1f) obtain(scope, origin, radiusPx, color) else null
    if (originLayer != null && origin != null) {
      scope.drawStretched(originLayer, origin.frame, bounds, radiusPx, alpha * (1f - t))
      if (targetLayer != null) scope.drawStretched(targetLayer, target.frame, bounds, radiusPx, alpha * t)
    } else if (targetLayer != null) {
      scope.drawStretched(targetLayer, target.frame, bounds, radiusPx, alpha)
    }
  }

  private fun DrawScope.drawStretched(layer: GraphicsLayer, recorded: Rect, dest: Rect, radiusPx: Float, alpha: Float) {
    if (!(alpha > 0.001f)) return
    if (recorded.width < 1f || recorded.height < 1f || dest.width < 1f || dest.height < 1f) return
    layer.alpha = alpha.coerceIn(0f, 1f)
    val sx = dest.width / recorded.width
    val sy = dest.height / recorded.height
    translate(dest.left, dest.top) {
      if (sx == 1f && sy == 1f) {
        translate(-radiusPx * 2f, -radiusPx * 2f) { drawLayer(layer) }
      } else {
        scale(sx, sy, pivot = Offset.Zero) {
          translate(-radiusPx * 2f, -radiusPx * 2f) { drawLayer(layer) }
        }
      }
    }
  }

  private fun obtain(scope: DrawScope, form: FluidForm, radiusPx: Float, color: Color): GraphicsLayer? {
    val frame = form.frame
    if (frame.width < 1f || frame.height < 1f) return null
    for (slot in slots) {
      if (slot.form == form && slot.radiusPx == radiusPx && slot.color == color) {
        slot.usedAt = stamp
        return slot.layer
      }
    }
    var victim: Slot? = null
    for (slot in slots) {
      if (slot.usedAt == stamp) continue
      if (victim == null || slot.usedAt < victim.usedAt) victim = slot
    }
    val chosen = victim ?: return null
    scope.recordShadow(chosen, form, radiusPx, color)
    chosen.usedAt = stamp
    return chosen.layer
  }

  private fun DrawScope.recordShadow(slot: Slot, form: FluidForm, radiusPx: Float, color: Color) {
    val frame = form.frame
    // La silhouette ferma di questa forma, la stessa che la superficie disegna a riposo (raccordo
    // continuo per gli Slab, l'anello per le sagome libere, l'unione per i gruppi), riportata
    // all'origine del proprio riquadro.
    scratchPlan.clipToBounds = true
    buildRestPlan(scratchPlan, form)
    path.rewind()
    path.addPath(scratchPlan.silhouette, Offset(-frame.left, -frame.top))
    // `Shadow`: spostamento di un sesto del raggio in giu', e il layer largo quattro raggi in piu'.
    val offsetY = radiusPx / 6f
    paint.color = color
    paint.blur(radiusPx)
    val size = IntSize(
      ceil(frame.width + radiusPx * 4f).toInt(),
      ceil(frame.height + radiusPx * 4f + offsetY).toInt(),
    )
    slot.layer.record(size) {
      translate(radiusPx * 2f, radiusPx * 2f + offsetY) {
        val canvas = drawContext.canvas
        canvas.drawPath(path, paint)
        canvas.translate(0f, -offsetY)
        canvas.drawPath(path, clearPaint)
        canvas.translate(0f, offsetY)
      }
    }
    slot.form = form
    slot.radiusPx = radiusPx
    slot.color = color
  }
}
