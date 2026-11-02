package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
      val bitmap = android.graphics.Bitmap.createBitmap(
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
