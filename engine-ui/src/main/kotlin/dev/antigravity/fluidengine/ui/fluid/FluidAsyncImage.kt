package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Image as ImageIcon
import dev.antigravity.fluidengine.ui.fluid.icons.Refresh

/**
 * La fase di un'immagine che si carica, come `AsyncImagePhase` di SwiftUI: [Loading] (`.empty`), [Success], [Failure].
 * La dice l'app, che fa il caricamento vero (Coil, il suo client): l'engine disegna le fasi.
 *
 * Con Coil la traduzione è di poche righe:
 *
 * ```
 * val painter = rememberAsyncImagePainter(url)
 * val state by painter.state.collectAsState()
 * val phase = when (state) {
 *   is AsyncImagePainter.State.Success -> FluidImagePhase.Success(painter)
 *   is AsyncImagePainter.State.Error -> FluidImagePhase.Failure((state as AsyncImagePainter.State.Error).result.throwable)
 *   else -> FluidImagePhase.Loading
 * }
 * FluidAsyncImage(phase, contentDescription = "Copertina", thumbHash = album.thumbHash, onRetry = { painter.restart() })
 * ```
 */
@Immutable
sealed interface FluidImagePhase {
  /** Non c'è ancora niente: si vede l'impronta ([FluidAsyncImage] `thumbHash`) o un fondo quieto. */
  data object Loading : FluidImagePhase

  /** L'immagine è arrivata. */
  data class Success(val painter: Painter) : FluidImagePhase {
    /** Da un'immagine già decodificata. */
    constructor(image: ImageBitmap) : this(BitmapPainter(image))
  }

  /** Non è arrivata: la tessera d'errore, con «Riprova» se l'app lo permette. */
  data class Failure(val error: Throwable? = null) : FluidImagePhase
}

/**
 * Il posto di un'immagine che arriva dalla rete o dal NAS (F8.1, `immagine-che-arriva`): **segnaposto, poi sfocata, poi
 * nitida**, come le fasi di `AsyncImage` di SwiftUI.
 *
 * - **Segnaposto**: l'impronta ThumbHash ([thumbHash], poche decine di byte salvate accanto all'immagine) disegnata coi
 *   suoi colori veri, già sfocata perché è piccola; senza impronta, un fondo quieto del tema. Le impronte decodificate
 *   si tengono in cache ([FluidThumbHash.imageBitmap]).
 * - **Arrivo**: la foto entra sopra l'impronta sfocata e si mette a fuoco ([FluidTextMotion.ImageArrivalMillis],
 *   [FluidTextMotion.ImageArrivalBlurDp]): il contenuto che cambia identità entra sfocato (R7). Una foto già pronta alla
 *   prima composizione (dalla cache dell'app) non si anima.
 * - **Errore**: una tessera con l'icona e, con [onRetry], «Riprova».
 * - **Col movimento ridotto** la foto compare di colpo, senza sfocatura.
 *
 * La sfocatura è sul livello della sola immagine (Android 12 e poi; prima, la sola dissolvenza) e c'è solo mentre arriva:
 * da ferma l'immagine non ridisegna.
 *
 * @param shape la sagoma: di serie nessuna (la decide il posto, come per un `Image`); per una card
 *   `ContinuousCornerShape(FluidRadius.Card)`.
 */
@Composable
fun FluidAsyncImage(
  phase: FluidImagePhase,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  thumbHash: ByteArray? = null,
  contentScale: ContentScale = ContentScale.Crop,
  shape: Shape = RectangleShape,
  onRetry: (() -> Unit)? = null,
) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val words = LocalFluidLabels.current.liveText
  val placeholder = remember(thumbHash?.contentHashCode()) { thumbHash?.let { runCatching { FluidThumbHash.imageBitmap(it) }.getOrNull() } }
  // Una foto già lì alla prima composizione è già arrivata: niente animazione.
  val arrival = remember { Animatable(if (phase is FluidImagePhase.Success) 1f else 0f) }
  var lastWasSuccess by remember { mutableStateOf(phase is FluidImagePhase.Success) }
  LaunchedEffect(phase is FluidImagePhase.Success, reduced) {
    val success = phase is FluidImagePhase.Success
    if (success && !lastWasSuccess) {
      if (reduced) arrival.snapTo(1f) else {
        arrival.snapTo(0f)
        arrival.animateTo(1f, tween(FluidTextMotion.ImageArrivalMillis, easing = FluidMotion.EaseOut))
      }
    } else if (!success) {
      arrival.snapTo(0f)
    }
    lastWasSuccess = success
  }
  // F7.29: una foto ferma non ha layer suoi. Il riquadro si ritaglia nel disegno (una sagoma vera resta il ritaglio del
  // layer, col suo contorno), l'immagine non ha il layer di ritaglio di `Image` ([FluidCellImage]) e il livello della
  // sfocatura c'è solo mentre arriva: in una griglia che scorre ogni cella che entra creava tre layer in più.
  val arriving = phase is FluidImagePhase.Success && !lastWasSuccess
  Box(
    modifier = modifier
      .then(if (shape === RectangleShape) FluidClipToBoundsInDraw else Modifier.clip(shape))
      .then(
        if (placeholder == null && phase !is FluidImagePhase.Success) {
          Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        } else {
          Modifier
        },
      ),
    contentAlignment = Alignment.Center,
  ) {
    val showPlaceholder = phase !is FluidImagePhase.Failure && placeholder != null
    if (showPlaceholder) {
      // Come `Image(bitmap, filterQuality = Low)`: lo stesso pennello, senza il layer di ritaglio.
      val painter = remember(placeholder) { BitmapPainter(placeholder!!, filterQuality = FilterQuality.Low) }
      FluidCellImage(painter = painter, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
    }
    when (phase) {
      is FluidImagePhase.Success -> FluidCellImage(
        painter = phase.painter,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = Modifier
          .fillMaxSize()
          .then(
            if (arriving) {
              Modifier.graphicsLayer {
                val u = arrival.value.coerceIn(0f, 1f)
                alpha = u
                // σ che scende con l'arrivo: piena all'inizio, zero quando è nitida. Il livello è quello della foto.
                val sigma = FluidTextMotion.ImageArrivalBlurDp.dp.toPx() * (1f - u)
                renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sigma > 0.25f) {
                  BlurEffect(sigma, sigma, TileMode.Clamp)
                } else {
                  null
                }
              }
            } else {
              Modifier
            },
          ),
      )
      is FluidImagePhase.Failure -> FluidImageFailure(words, onRetry)
      FluidImagePhase.Loading -> Box(
        Modifier
          .fillMaxSize()
          .clearAndSetSemantics { this.contentDescription = contentDescription ?: words.imageLoading },
      )
    }
  }
}

/** La tessera di un'immagine che non è arrivata: l'icona, la frase e, se si può, «Riprova». */
@Composable
private fun FluidImageFailure(words: FluidLiveTextStrings, onRetry: (() -> Unit)?) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(8.dp)
      .semantics(mergeDescendants = true) {},
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
  ) {
    FluidIcon(
      imageVector = FluidIcons.ImageIcon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = words.imageFailed,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (onRetry != null) {
      FluidButton(
        text = words.retry,
        onClick = onRetry,
        style = FluidButtonStyle.Tinted,
        size = FluidButtonSize.Small,
        leading = { FluidIcon(FluidIcons.Refresh, contentDescription = null) },
      )
    }
  }
}
