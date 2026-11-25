/*
 * Le pagine dentro un foglio (4.0, F8.10): la navigazione di un foglio di iOS, un `NavigationStack` dentro `.sheet`.
 * La pagina nuova arriva da destra sopra quella di prima, che arretra di un terzo; si torna col tasto in alto, col back
 * di sistema (predittivo) o col dito dal bordo sinistro. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowBackIosNew
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.page.FluidPageMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** I tag dei test e delle riprese. */
object FluidSheetPagesTags {
  const val Pages = "fluid-sheet-pages"
  const val Back = "fluid-sheet-pages:indietro"
  fun page(key: Any): String = "fluid-sheet-pages:pagina:$key"
}

/**
 * Le pagine dentro un foglio (4.0, F8.10): la navigazione di un foglio di iOS (un `NavigationStack` dentro `.sheet`),
 * come le «Opzioni ›» del foglio di condivisione di Foto. Il foglio resta dov'e': cambia solo cosa c'e' dentro.
 *
 * [pages] e' la pila, la prima in fondo; la disegna l'app, che la cambia lei: una pagina in piu' in cima e' un push,
 * una in meno un ritorno. [onBack] chiede di togliere quella in cima (il tasto di [FluidSheetPageBar], il back di
 * sistema, il dito dal bordo sinistro); il foglio non si chiude mai da qui.
 *
 * Il movimento e' quello delle pagine dell'engine ([FluidPageMotion]), un orologio solo: la corsa in pixel della pagina
 * in cima. Arriva da destra con la molla [FluidPageMotion.page]; quella sotto arretra di un terzo
 * ([FluidPageMotion.belowParallax]) e sfuma, perche' sul vetro di un foglio le pagine sono trasparenti e i due testi non
 * devono sovrapporsi (la sfumatura e' il quadrato della frazione d'uscita: a meta' corsa quella sotto e' a un quarto. A
 * occhio, 24 novembre 2026). Il back predittivo e il dito muovono la stessa corsa; rilasciati, decide
 * [FluidPageMotion.backRelease] e un ritorno annullato rientra con [FluidPageMotion.pageReturn].
 *
 * Le pagine le muove il dito o un tocco, quindi restano nitide (niente sfocatura). Le pagine sotto restano composte, e
 * lo stato `rememberSaveable` di ognuna sopravvive al viaggio; quelle ferme sotto la cima non si misurano, non si
 * disegnano e non ricevono tocchi. Con il movimento ridotto il cambio e' immediato.
 *
 * @param key l'identita' di una pagina; di serie la pagina stessa.
 */
@Composable
fun <T : Any> FluidSheetPages(
  pages: List<T>,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  key: (T) -> Any = { it },
  content: @Composable (T) -> Unit,
) {
  require(pages.isNotEmpty()) { "FluidSheetPages vuole almeno una pagina" }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val slop = LocalViewConfiguration.current.touchSlop
  val scope = rememberCoroutineScope()
  val saveable = rememberSaveableStateHolder()
  val back by rememberUpdatedState(onBack)
  val motion = remember { SheetPagesMotion<T>() }
  val keys = pages.map(key)

  // Il cambio si legge qui, nella composizione: la corsa di partenza deve esserci gia' al primo fotogramma della pagina
  // nuova, prima che l'effetto parta.
  val change = motion.observe(keys, pages, key)
  val still = reduced || motion.width <= 0f
  when (change) {
    SheetPageChange.Push -> motion.travel = if (still) 0f else motion.width
    SheetPageChange.Pop -> if (still) {
      motion.leaving = null
      motion.travel = 0f
    }
    SheetPageChange.None -> Unit
  }
  LaunchedEffect(keys, reduced) {
    if (reduced) return@LaunchedEffect
    when {
      motion.leaving != null -> {
        motion.run(FluidPageMotion.page(), motion.width)
        motion.leaving = null
        motion.travel = 0f
      }
      motion.travel > 0f -> motion.run(FluidPageMotion.page(), 0f)
    }
  }

  val canGoBack = pages.size > 1
  PredictiveBackHandler(enabled = canGoBack) { events ->
    try {
      motion.stop()
      events.collect { if (!reduced) motion.travel = it.progress.coerceIn(0f, 1f) * motion.width }
      back()
    } catch (_: CancellationException) {
      scope.launch { motion.run(FluidPageMotion.pageReturn(), 0f) }
    }
  }

  val rendered = motion.leaving?.let { pages + it } ?: pages
  val edgePx = with(density) { FluidPageMotion.EdgeZoneDp.dp.toPx() }
  Layout(
    modifier = modifier
      .testTag(FluidSheetPagesTags.Pages)
      .clipToBounds()
      .pointerInput(canGoBack, reduced) {
        if (!canGoBack) return@pointerInput
        // Il dito dal bordo sinistro: lo reclama il foglio solo quando si muove in orizzontale oltre lo slop, prima dei
        // figli (passo Initial). Un tocco sul bordo arriva ancora a chi sta sotto.
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
          if (down.position.x > edgePx || motion.leaving != null) return@awaitEachGesture
          val tracker = VelocityTracker()
          tracker.addPosition(down.uptimeMillis, down.position)
          var dragging = false
          var dx = 0f
          var dy = 0f
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val drag = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!drag.pressed) break
            val delta = drag.positionChange()
            tracker.addPosition(drag.uptimeMillis, drag.position)
            if (!dragging) {
              dx += delta.x
              dy += delta.y
              when {
                dx > slop && abs(dx) > abs(dy) -> {
                  dragging = true
                  scope.launch { motion.stop() }
                }
                abs(dy) > slop || dx < -slop -> return@awaitEachGesture
              }
            }
            if (dragging) {
              motion.travel = (motion.travel + delta.x).coerceIn(0f, motion.width)
              drag.consume()
            }
          }
          if (dragging) {
            val velocity = tracker.calculateVelocity().x
            // Col movimento ridotto il dito sposta ancora la pagina (la muove lui), ma niente molla dopo.
            when {
              FluidPageMotion.backRelease(motion.travel, velocity, motion.width, density.density) -> back()
              reduced -> motion.travel = 0f
              else -> scope.launch { motion.run(FluidPageMotion.pageReturn(), 0f, velocity) }
            }
          }
        }
      },
    content = {
      rendered.forEach { page ->
        val id = key(page)
        // Il `key` di Compose per nome intero: il parametro `key` lo copre.
        androidx.compose.runtime.key(id) {
          Box(Modifier.testTag(FluidSheetPagesTags.page(id))) { saveable.SaveableStateProvider(id) { content(page) } }
        }
      }
    },
  ) { measurables, constraints ->
    val top = measurables.lastIndex
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val child = if (constraints.hasBoundedHeight) {
      Constraints.fixed(width, constraints.maxHeight)
    } else {
      Constraints(minWidth = width, maxWidth = width)
    }
    // Solo la cima e quella appena sotto: le altre restano composte (il loro stato vive) ma non costano niente.
    val upper = measurables[top].measure(child)
    val lower = if (top > 0) measurables[top - 1].measure(child) else null
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else max(upper.height, lower?.height ?: 0)
    motion.width = width.toFloat()
    layout(width, height) {
      val travel = motion.travel
      if (lower != null && travel > FluidPageMotion.CardThresholdPx) {
        val exit = if (width > 0) (travel / width).coerceIn(0f, 1f) else 1f
        lower.placeWithLayer(0, 0) {
          translationX = FluidPageMotion.belowParallax(exit, width.toFloat())
          alpha = exit * exit
        }
      }
      upper.placeWithLayer(0, 0) { translationX = motion.travel }
    }
  }
}

/** Che cosa e' cambiato nella pila dall'ultima composizione. */
private enum class SheetPageChange { None, Push, Pop }

/** La corsa della pagina in cima e chi sta uscendo: un orologio solo, letto nel layout e nei layer. */
@Stable
private class SheetPagesMotion<T : Any> {
  /** I pixel che la pagina in cima ha percorso verso destra: 0 al suo posto, [width] fuori. */
  var travel by mutableFloatStateOf(0f)

  /** La pagina che sta uscendo dopo un ritorno, disegnata sopra la nuova cima finche' la corsa non finisce. */
  var leaving by mutableStateOf<T?>(null)

  /** La larghezza delle pagine, scritta dal layout. */
  var width = 0f

  private var lastKeys: List<Any>? = null
  private var lastPages: List<T> = emptyList()
  private val mutex = MutatorMutex()

  fun observe(keys: List<Any>, pages: List<T>, key: (T) -> Any): SheetPageChange {
    val old = lastKeys
    val oldPages = lastPages
    lastKeys = keys
    lastPages = pages
    if (old == null || old == keys) return SheetPageChange.None
    return when {
      keys.size > old.size && keys.subList(0, old.size) == old -> {
        leaving = null
        SheetPageChange.Push
      }
      keys.size < old.size && old.subList(0, keys.size) == keys -> {
        // Un salto di piu' pagine: esce solo quella in cima, le altre spariscono senza farsi vedere.
        leaving = oldPages.last()
        SheetPageChange.Pop
      }
      key(oldPages.last()) != keys.last() -> {
        leaving = null
        SheetPageChange.Push
      }
      else -> SheetPageChange.None
    }
  }

  /** Porta la corsa a [target] con [spec]; l'ultima richiesta vince. */
  suspend fun run(spec: SpringSpec<Float>, target: Float, velocity: Float = 0f) {
    mutex.mutate {
      animate(travel, target, velocity, spec) { value, _ -> travel = value }
    }
  }

  /** Ferma la corsa in corso (il dito o il back l'hanno presa in mano). */
  suspend fun stop() {
    mutex.mutate { }
  }
}

/**
 * La barra in cima a una pagina di [FluidSheetPages]: il tasto indietro di vetro a sinistra (lo stesso chevron delle
 * pagine dell'engine), il titolo al centro e, a destra, [trailing] (di solito la conferma, un tasto di vetro in
 * evidenza con la spunta, come in iOS 26). Il titolo e' un'intestazione e il nome del riquadro: TalkBack lo legge quando
 * la pagina arriva.
 *
 * @param onBack null per una pagina senza indietro (la prima).
 */
@Composable
fun FluidSheetPageBar(
  title: String,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  backLabel: String = "Indietro",
  trailing: @Composable RowScope.() -> Unit = {},
) {
  Box(
    modifier
      .fillMaxWidth()
      .heightIn(min = FluidSheetPageBarHeight)
      .padding(horizontal = 10.dp)
      .semantics { paneTitle = title },
  ) {
    if (onBack != null) {
      FluidGlassIconButton(
        onClick = onBack,
        size = FluidSheetPageBarButton,
        modifier = Modifier.align(Alignment.CenterStart).testTag(FluidSheetPagesTags.Back),
      ) {
        FluidIcon(FluidIcons.ArrowBackIosNew, contentDescription = backLabel, size = FluidMetrics.BackIconSize)
      }
    }
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .align(Alignment.Center)
        .padding(horizontal = FluidSheetPageBarButton + 12.dp)
        .semantics { heading() },
    )
    Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = trailing)
  }
}

/** I tasti della barra: 44 dp, la misura dei tasti di vetro nella barra di un foglio di iOS 26 (a occhio dalla guida). */
internal val FluidSheetPageBarButton = 44.dp

/** L'altezza della barra: il tasto con 6 dp sopra e sotto. */
internal val FluidSheetPageBarHeight = 56.dp
