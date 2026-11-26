package dev.antigravity.fluidengine.widget

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews

/**
 * Una taglia di widget, come nella galleria dei widget di iPhone: piccola, media, grande.
 *
 * Le misure di serie sono quelle di Apple per un iPhone da 393×852 pt (HIG «Widgets», Specifications: piccolo 158×158,
 * medio 338×158, grande 338×354; fiducia alta, tabella letta alla lettera nel JSON della pagina il 26/11/2026). Un punto di iOS e un
 * dp di Android sono quasi la stessa cosa su un telefono, e un telefono da 411 dp come il Galaxy S25 ha celle di quella
 * misura sulla griglia 4×5 di One UI. La misura vera la decide il launcher quando il widget è sulla Home: qui serve a
 * mostrarlo come sarà, disposto **alla sua misura** e solo rimpicciolito se la colonna è più stretta.
 *
 * @property label il nome della taglia («Medio»).
 * @property columns, rows le celle della griglia della Home (2×2, 4×2, 4×4).
 * @property provider il provider di questa taglia, se l'app ne ha uno per taglia. Android non sa chiedere al launcher
 * una taglia (`requestPinAppWidget` mette il widget alla sua misura di serie): con un provider per taglia la richiesta
 * mette quella giusta; senza, la vetrina dice che la taglia si cambia sulla Home.
 */
@Immutable
data class FluidWidgetSize(
  val label: String,
  val width: Dp,
  val height: Dp,
  val columns: Int,
  val rows: Int,
  val provider: ComponentName? = null,
) {
  /** La misura come [DpSize]. */
  val dpSize: DpSize get() = DpSize(width, height)

  companion object {
    val Small: FluidWidgetSize = FluidWidgetSize("Piccolo", 158.dp, 158.dp, 2, 2)
    val Medium: FluidWidgetSize = FluidWidgetSize("Medio", 338.dp, 158.dp, 4, 2)
    val Large: FluidWidgetSize = FluidWidgetSize("Grande", 338.dp, 354.dp, 4, 4)

    /** Le tre taglie di iPhone, nell'ordine della galleria. */
    val All: List<FluidWidgetSize> = listOf(Small, Medium, Large)
  }
}

/** Come si mostra l'anteprima di un widget. */
sealed interface FluidWidgetPreview {
  /**
   * Il contenuto Glance del widget, lo stesso che il widget passa a `provideContent`: Glance lo compone alla taglia
   * scelta (`LocalSize` è quella) e la vetrina mostra le `RemoteViews` vere, come le mostrerebbe il launcher.
   */
  class Glance(val content: @Composable () -> Unit) : FluidWidgetPreview

  /** Le `RemoteViews` di un widget non Glance, costruite dall'app per la taglia. */
  class Remote(val build: (context: Context, size: DpSize) -> RemoteViews) : FluidWidgetPreview

  /** Un'immagine preparata (l'anteprima del provider, `previewImage`), adattata alla taglia. */
  class Image(@DrawableRes val drawable: Int) : FluidWidgetPreview
}

/**
 * Un widget dell'app nella vetrina.
 *
 * @property provider il provider del widget (la sottoclasse di `GlanceAppWidgetReceiver` o di `AppWidgetProvider`).
 * @property title il nome del widget («Agenda»).
 * @property description una frase su cosa mostra.
 * @property preview l'anteprima ([FluidWidgetPreview]).
 * @property sizes le taglie, nell'ordine; di serie le tre di iPhone.
 */
@Immutable
data class FluidWidgetEntry(
  val provider: ComponentName,
  val title: String,
  val description: String,
  val preview: FluidWidgetPreview,
  val sizes: List<FluidWidgetSize> = FluidWidgetSize.All,
) {
  init {
    require(sizes.isNotEmpty()) { "un widget ha almeno una taglia" }
  }
}

/**
 * L'anteprima vera di [entry] alla taglia [size]: disposta alla sua misura in dp e rimpicciolita solo se lo spazio è più
 * stretto (mai ingrandita). Non si tocca (è un'anteprima: i tocchi non arrivano al widget) e per TalkBack è un'immagine
 * sola con [contentDescription].
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
fun FluidWidgetPreviewBox(
  entry: FluidWidgetEntry,
  size: FluidWidgetSize,
  contentDescription: String,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
    val scale = fluidWidgetPreviewScale(size.dpSize, maxWidth)
    Box(
      Modifier
        .size(size.width * scale, size.height * scale)
        // Il tag prima: dentro clearAndSetSemantics sarebbe cancellato con il resto.
        .testTag(FluidWidgetShowcaseTags.preview(size.label))
        .clearAndSetSemantics { this.contentDescription = contentDescription },
      contentAlignment = Alignment.Center,
    ) {
      Box(
        Modifier
          .requiredSize(size.width, size.height)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
          },
      ) {
        when (val preview = entry.preview) {
          is FluidWidgetPreview.Image -> Image(
            painter = painterResource(preview.drawable),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.requiredSize(size.width, size.height),
          )
          is FluidWidgetPreview.Glance -> {
            val context = LocalContext.current
            val views by produceState<RemoteViews?>(null, preview, size.dpSize) {
              value = GlanceRemoteViews().compose(context, size.dpSize, content = preview.content).remoteViews
            }
            views?.let { RemoteViewsHost(it, size) }
          }
          is FluidWidgetPreview.Remote -> {
            val context = LocalContext.current
            val views by produceState<RemoteViews?>(null, preview, size.dpSize) {
              value = preview.build(context, size.dpSize)
            }
            views?.let { RemoteViewsHost(it, size) }
          }
        }
      }
    }
  }
}

/**
 * Quanto rimpicciolire un widget largo [widget] per stare in [available]: 1 se ci sta, mai più di 1. Pura, per le prove.
 */
fun fluidWidgetPreviewScale(widget: DpSize, available: Dp): Float =
  if (available.value <= 0f || widget.width.value <= 0f) 1f else (available / widget.width).coerceAtMost(1f)

@Composable
private fun RemoteViewsHost(views: RemoteViews, size: FluidWidgetSize) {
  AndroidView(
    factory = { PreviewFrame(it) },
    update = { it.show(views) },
    modifier = Modifier.requiredSize(size.width, size.height),
  )
}

/**
 * Il telaio dell'anteprima: applica le `RemoteViews` come farebbe l'host del launcher, ferma i tocchi (un'anteprima non
 * apre niente) e nasconde l'albero a TalkBack (la descrizione è del contenitore Compose).
 */
@SuppressLint("ViewConstructor")
internal class PreviewFrame(context: Context) : FrameLayout(context) {
  private var shown: RemoteViews? = null

  init {
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  }

  /** Mostra [views]; le stesse `RemoteViews` non si riapplicano. */
  fun show(views: RemoteViews) {
    if (views === shown) return
    shown = views
    removeAllViews()
    addView(views.apply(context, this))
  }

  override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent?): Boolean = false
}
