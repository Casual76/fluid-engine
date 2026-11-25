/*
 * Le uscite dell'app (4.0, F8.10, `uscite-stampabili`): una vista Compose disegnata fuori dallo schermo, su una bitmap o
 * sulla pagina di un PDF. E' il pezzo che fa di una pagina qualunque un'immagine o un foglio stampato, come
 * `ImageRenderer` di SwiftUI. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.ui.fluid.output

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density

/**
 * Vero dentro un'uscita dell'engine (l'immagine da condividere, un pezzo di PDF): fuori dallo schermo il vetro non ha
 * uno sfondo da piegare, e un componente che lo sa disegna il materiale piatto ([FluidPrintPanel]) al suo posto.
 */
val LocalFluidPrinting: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * Compone [content] in una `ComposeView` appesa per un attimo alla finestra di [anchor] (invisibile: la finestra non la
 * disegna), la misura e la passa a [draw], poi la stacca. La composizione eredita da [parent] il tema e le parole
 * dell'app, come un `Popup`.
 *
 * Il disegno e' **software** (una tela di bitmap o di PDF): il testo resta testo, le forme restano vettori; la
 * sfocatura e le ombre di `RenderEffect` non ci sono, per questo nelle uscite si usa il materiale piatto. Il contenuto
 * deve essere pronto alla prima composizione: un'immagine che arriva dalla rete dopo non fa in tempo.
 *
 * @param widthPx la larghezza in pixel della tela.
 * @param heightPx l'altezza; null per «quanto serve» (un pezzo di PDF che si misura).
 * @param density i pixel per dp del contenuto: decide quanto e' grande ogni cosa sulla tela.
 * @return quello che ritorna [draw], con l'altezza misurata.
 */
@MainThread
internal fun <T> fluidComposeOffscreen(
  anchor: View,
  parent: CompositionContext?,
  widthPx: Int,
  heightPx: Int?,
  density: Float,
  content: @Composable () -> Unit,
  draw: (view: View, measuredHeightPx: Int) -> T,
): T {
  val root = anchor.rootView as? ViewGroup ?: error("La vista di partenza non e' in una finestra")
  val view = ComposeView(anchor.context).apply {
    if (parent != null) setParentCompositionContext(parent)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    visibility = View.INVISIBLE
    // Fuori dall'albero dell'accessibilita': e' una tela, non una pagina.
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(density, fontScale = 1f),
        LocalFluidPrinting provides true,
        content = content,
      )
    }
  }
  root.addView(view, ViewGroup.LayoutParams(widthPx, heightPx ?: ViewGroup.LayoutParams.WRAP_CONTENT))
  try {
    val heightSpec = if (heightPx != null) View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), heightSpec)
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    return draw(view, view.measuredHeight)
  } finally {
    root.removeView(view)
  }
}

/** Disegna [view], gia' misurata, su [canvas]: la tela software che la vista non ha mai visto. */
internal fun fluidDrawView(view: View, canvas: Canvas) {
  view.draw(canvas)
}
