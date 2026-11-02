package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Le misure del [FluidPagerDots]. */
object FluidPagerDotsDefaults {
  /** Un punto a riposo e quello della pagina corrente: 7 e 10 dp, come l'onboarding di Fluidweather. */
  val DotSize: Dp = 7.dp
  val ActiveSize: Dp = 10.dp

  /** Lo spazio fra due punti, bordo a bordo del punto grande. */
  val Spacing: Dp = 8.dp

  /** L'opacita' di un punto a riposo e di quello corrente. */
  const val InactiveAlpha: Float = 0.35f
  const val ActiveAlpha: Float = 0.95f

  /**
   * Quanti punti si vedono al massimo. Oltre, la fila scorre e quelli ai bordi si rimpiccioliscono, come
   * i controlli di pagina di iOS: venti punti in fila non sono un indicatore, sono una riga.
   */
  const val MaxVisible: Int = 7
}

/**
 * Quanto il punto [index] e' "il corrente" quando il pager sta alla posizione frazionaria [position]:
 * 1 sulla sua pagina, 0 a una pagina di distanza o piu', lineare in mezzo.
 *
 * E' la ragione per cui i punti non scattano: durante uno scorrimento a meta' fra la pagina 2 e la 3
 * tutti e due sono "mezzo correnti", e il passaggio del dito li porta uno dentro l'altro. Prima, a mano,
 * ogni app confrontava `index == currentPage` e il punto cambiava di colpo a pagina assestata,
 * qualche centinaio di millisecondi dopo che l'occhio aveva gia' visto la pagina nuova.
 */
internal fun fluidPagerDotWeight(index: Int, position: Float): Float =
  (1f - abs(index - position)).coerceIn(0f, 1f)

/**
 * Di quanti punti e' scorsa la fila, quando le pagine sono piu' dei punti visibili ([maxVisible]):
 * il punto corrente sta al centro finche' c'e' fila da una parte e dall'altra, poi la fila si ferma
 * e il punto corrente si sposta verso il bordo. Zero se i punti stanno tutti.
 *
 * Frazionario, perche' la fila scorre col dito e non a salti.
 */
internal fun fluidPagerDotsScroll(pageCount: Int, position: Float, maxVisible: Int): Float {
  if (pageCount <= maxVisible || maxVisible <= 0) return 0f
  val maxScroll = (pageCount - maxVisible).toFloat()
  return (position - (maxVisible - 1) / 2f).coerceIn(0f, maxScroll)
}

/** Quanto resta di un punto sull'orlo estremo della fila (0 = spento), e in quanti punti di fila risale a 1. */
private const val DotEdgeMin = 0.35f
private const val DotTaperSlots = 2f

/** La forma della rampa ai bordi: [d] e' la distanza, in punti, dall'orlo della fila. */
private fun dotEdge(d: Float): Float = when {
  d <= -1f -> 0f
  d < 0f -> DotEdgeMin * (d + 1f)
  d < DotTaperSlots -> DotEdgeMin + (1f - DotEdgeMin) * (d / DotTaperSlots)
  else -> 1f
}

/**
 * Il fattore (0..1) con cui si rimpicciolisce il punto che sta alla posizione [slot] della fila — 0 il
 * primo posto visibile — quando la fila e' scorsa di [scroll] su [pageCount] pagine.
 *
 * I punti si rimpiccioliscono solo sul lato dove la fila continua, e **in modo continuo**: con la fila
 * ferma in testa il primo punto e' intero; non appena comincia a scorrere cede, in proporzione a
 * quanto e' scorsa. Un interruttore "ci sono altri punti da questa parte" farebbe saltare il primo
 * punto a un terzo di dimensione nel fotogramma in cui la fila si muove.
 */
internal fun fluidPagerDotTaper(slot: Float, maxVisible: Int, scroll: Float, pageCount: Int): Float {
  if (pageCount <= maxVisible) return 1f
  val maxScroll = (pageCount - maxVisible).toFloat()
  val leftMore = scroll.coerceIn(0f, 1f)
  val rightMore = (maxScroll - scroll).coerceIn(0f, 1f)
  val left = lerp(1f, dotEdge(slot), leftMore)
  val right = lerp(1f, dotEdge((maxVisible - 1) - slot), rightMore)
  return left * right
}

/** Quanti punti si disegnano: tutti, o [maxVisible]. */
internal fun fluidPagerDotsShown(pageCount: Int, maxVisible: Int): Int =
  min(pageCount.coerceAtLeast(0), maxVisible.coerceAtLeast(1))

/**
 * L'indicatore di pagina di un [PagerState]: un punto per pagina, quello corrente piu' grande e
 * pieno.
 *
 * Vedi la versione a posizione [FluidPagerDots] per i dettagli; questa legge `currentPage +
 * currentPageOffsetFraction` dallo stato, che e' la posizione del dito.
 *
 * @param pageLabel cosa dice TalkBack («Pagina 2 di 5»): la pagina corrente e quante sono.
 */
@Composable
fun FluidPagerDots(
  state: PagerState,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.onSurface,
  activeColor: Color = color,
  maxVisible: Int = FluidPagerDotsDefaults.MaxVisible,
  hideForSinglePage: Boolean = true,
  pageLabel: (page: Int, count: Int) -> String = { page, count -> "Pagina $page di $count" },
) {
  FluidPagerDots(
    pageCount = state.pageCount,
    position = { state.currentPage + state.currentPageOffsetFraction },
    modifier = modifier,
    color = color,
    activeColor = activeColor,
    maxVisible = maxVisible,
    hideForSinglePage = hideForSinglePage,
    pageLabel = pageLabel,
  )
}

/**
 * L'indicatore di pagina: un punto per pagina, quello corrente piu' grande e pieno, che **segue il
 * dito** e non scatta a pagina assestata.
 *
 * Nato dall'onboarding di Fluidweather, dove cinque righe di `Box` confrontavano `index ==
 * currentPage`: corretto, ma il punto cambiava solo quando il pager si fermava, e la pagina nuova era
 * sullo schermo da un terzo di secondo. Qui ogni punto prende il suo peso dalla posizione frazionaria
 * ([fluidPagerDotWeight]) e passa da 7 a 10 dp e da 35% a 95% di opacita' mentre la pagina scorre.
 *
 * **Il layout non si muove mai.** Ogni punto ha un posto fisso da [FluidPagerDotsDefaults.ActiveSize]
 * piu' lo spazio, e dentro cresce e cala: una fila che cambia larghezza a ogni fotogramma di uno
 * scorrimento farebbe ricalcolare tutto quello che le sta accanto. Tutto e' un disegno solo, letto
 * nel disegno (`position` e' una lambda): la pagina scorre e nulla si ricompone.
 *
 * Con piu' pagine di [maxVisible] la fila scorre e i punti dei bordi si rimpiccioliscono
 * ([fluidPagerDotsScroll], [fluidPagerDotTaper]).
 *
 * Non e' un controllo: non si tocca (il pager ha i suoi gesti). Per TalkBack e' un'etichetta, «Pagina 2
 * di 5».
 *
 * @param pageCount quante pagine.
 * @param position la posizione frazionaria del pager (pagina + frazione di scorrimento); letta nel
 *   disegno.
 * @param color il colore di un punto a riposo; sopra una foto, il bianco.
 * @param activeColor il colore del punto corrente; di serie lo stesso, e cambiano solo dimensione e
 *   opacita'.
 * @param hideForSinglePage con una pagina sola un indicatore dice «non c'e' altro», e non c'e'
 *   ragione di dirlo.
 */
@Composable
fun FluidPagerDots(
  pageCount: Int,
  position: () -> Float,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.onSurface,
  activeColor: Color = color,
  maxVisible: Int = FluidPagerDotsDefaults.MaxVisible,
  hideForSinglePage: Boolean = true,
  pageLabel: (page: Int, count: Int) -> String = { page, count -> "Pagina $page di $count" },
) {
  if (pageCount <= 0 || (hideForSinglePage && pageCount == 1)) return
  val visible = maxVisible.coerceAtLeast(1)
  val shown = fluidPagerDotsShown(pageCount, visible)
  val dot = FluidPagerDotsDefaults.DotSize
  val active = FluidPagerDotsDefaults.ActiveSize
  val slot = active + FluidPagerDotsDefaults.Spacing

  Canvas(
    modifier = modifier
      .width(slot * shown)
      .height(active)
      .semantics {
        val page = position().roundToInt().coerceIn(0, pageCount - 1)
        contentDescription = pageLabel(page + 1, pageCount)
      },
  ) {
    val slotPx = slot.toPx()
    val dotPx = dot.toPx()
    val activePx = active.toPx()
    val current = position()
    val scroll = fluidPagerDotsScroll(pageCount, current, visible)
    val first = if (pageCount > shown) floor(scroll).toInt() - 1 else 0
    val last = if (pageCount > shown) ceil(scroll).toInt() + shown else pageCount - 1
    for (index in first.coerceAtLeast(0)..last.coerceAtMost(pageCount - 1)) {
      val place = index - scroll
      val taper = fluidPagerDotTaper(place, shown, scroll, pageCount)
      if (taper <= 0f) continue
      val weight = fluidPagerDotWeight(index, current)
      val diameter = lerp(dotPx, activePx, weight) * taper
      val alpha = lerp(FluidPagerDotsDefaults.InactiveAlpha, FluidPagerDotsDefaults.ActiveAlpha, weight) * taper
      drawCircle(
        color = lerpColor(color, activeColor, weight),
        radius = diameter / 2f,
        center = Offset(x = (place + 0.5f) * slotPx, y = size.height / 2f),
        alpha = alpha.coerceIn(0f, 1f),
      )
    }
  }
}
