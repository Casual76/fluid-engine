package dev.antigravity.fluidengine.ui.theme

import dev.antigravity.fluidengine.ui.fluid.FluidGlassQuality
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.page.FluidPageScope

/**
 * Where a destination grows from, as a fraction of the navigation host.
 *
 * The app used to pair a source control with a matching element inside the destination and hand the
 * pair to `sharedBounds`. That contract is only as strong as its weakest destination: a screen with
 * no data composes no matching element, the source is left without a partner, and it strands in the
 * transition overlay on top of the arriving page. Elements arriving late, or not at all, were all
 * the same defect wearing different clothes.
 *
 * A touch point cannot fail. Every destination knows how to grow out of one, nothing has to be
 * registered in advance, no two screens ever share a render layer, and the source list pays nothing
 * while it is merely being scrolled.
 */
@Immutable
data class MotionOrigin(val fractionX: Float, val fractionY: Float) {

  fun toTransformOrigin(): TransformOrigin = TransformOrigin(
    pivotFractionX = fractionX.coerceIn(0f, 1f),
    pivotFractionY = fractionY.coerceIn(0f, 1f),
  )

  companion object {
    /** Used by deep links, notifications and restored back stacks, which have no gesture behind them. */
    val Center = MotionOrigin(0.5f, 0.5f)
  }
}

/** Tracks where the finger last went down, so the next navigation knows what the user touched. */
@Stable
class FluidTouchOriginState internal constructor() {
  var origin by mutableStateOf(MotionOrigin.Center)
    private set

  internal fun record(fractionX: Float, fractionY: Float) {
    origin = MotionOrigin(fractionX.coerceIn(0f, 1f), fractionY.coerceIn(0f, 1f))
  }
}

@Composable
fun rememberFluidTouchOrigin(): FluidTouchOriginState = remember { FluidTouchOriginState() }

/**
 * Records press positions without taking part in the gesture.
 *
 * Reading on [PointerEventPass.Initial] and consuming nothing means the tracker is invisible to
 * every control below it: presses, drags and long-presses behave exactly as they would if it were
 * not there.
 */
fun Modifier.fluidTouchOriginTracker(state: FluidTouchOriginState): Modifier =
  this.pointerInput(state) {
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.type != PointerEventType.Press) continue
        val position = event.changes.firstOrNull()?.position ?: continue
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        if (width <= 0f || height <= 0f) continue
        state.record(position.x / width, position.y / height)
      }
    }
  }

object FluidRouteMotion {
  /**
   * How small an arriving destination starts.
   *
   * Deprecato dalla 3.0: la crescita dal punto toccato e' `FluidPageTransition.Expand`, con lo stesso
   * numero in `FluidPageMotion.ExpandInitialScale`.
   *
   * Nearer to full size than it once was. The distance a page travels is time the eye has to spend
   * watching an already-legible screen finish arriving, and past a certain point more travel does
   * not read as more depth — it reads as the page being slow.
   */
  @Deprecated(
    "Dalla 3.0: FluidPageMotion.ExpandInitialScale (e FluidPageTransition.Expand).",
    ReplaceWith("FluidPageMotion.ExpandInitialScale", "dev.antigravity.fluidengine.ui.page.FluidPageMotion"),
  )
  const val ExpandInitialScale = 0.91f

  /** How far the page being covered recedes. It is going *under* the new one, not away from it. */
  @Deprecated("Dalla 3.0 la pagina coperta non si scala: arretra di un terzo (FluidNavHost).")
  const val ExpandParentScale = 1.04f

  /**
   * The radius a hierarchical page carries while it is not yet the whole screen.
   *
   * This is what lets the arriving page be *opaque* from the first frame. Left square, a page at
   * ninety-one percent is a rectangle of one screen pasted over another, so it had to fade in to
   * disguise its own edge — and a page fading in over a page is two readable screens at once, which
   * is the single thing that made every hierarchical move look cheap. Rounded, the same page reads
   * as a card lifting off the one behind it, and needs to hide nothing.
   */
  @Deprecated("Dalla 3.0 la pagina in movimento ha gli angoli del telefono (FluidNavHost), non 30 dp.")
  val ExpandCornerRadius: Dp = 30.dp

  /** Defocus applied to whatever is leaving. One blurred surface at a time, never two. */
  @Deprecated("Dalla 3.0 nessuna pagina si sfoca: FluidRouteMotionHost non la applica piu'.")
  val ExitBlur: Dp = 6.dp
}

/**
 * Whether the movement on screen right now is a hierarchical open or close.
 *
 * Two effects hang off this, and both are wrong for a step between tabs. A page that grows needs a
 * rounded edge; two pages that slide side by side at full size need square ones, or the corners
 * would show the wall behind them. And a full-screen blur is by a wide margin the most expensive
 * thing drawn during a route change, at the moment with the least frame budget to spare: it buys
 * depth when one page passes behind another and nothing at all during a sideways step that is over
 * before the eye could read it.
 *
 * Deliberately a plain field rather than snapshot state: the navigation host sets it while building
 * a transition and the layers read it from the draw pass, so making it observable would only add an
 * invalidation to a frame that is already the tightest one in the app.
 */
@Stable
class RouteMotionSignals internal constructor() {
  @Deprecated("Dalla 3.0 non fa piu' niente: FluidRouteMotionHost non ha piu' angoli ne' sfocatura da spegnere.")
  var hierarchical: Boolean = true

  /**
   * Fino a quando il vetro deve restare al minimo perche' due pagine sono in movimento.
   *
   * Il cambio di scheda e' il fotogramma piu' caro dell'app e per un motivo strutturale: durante la
   * transizione **esistono due pagine**, quindi due fondali ambientali, due serie di pannelli di
   * contenuto e la chrome, tutti disegnati insieme. Il conto raddoppia esattamente nel momento in
   * cui il budget e' gia' speso, ed e' quello che si vede trascinando la pastiglia fra le schede.
   *
   * Non e' un momento in cui il materiale si guarda: due pagine si stanno attraversando. Quindi
   * scende alla stessa soglia che tiene durante uno scorrimento veloce.
   *
   * E' una **scadenza** invece di un contatore perche' le pagine in transizione sono due e nessuna
   * delle due sa quando l'altra ha finito: chi si muove timbra, e il timbro scade da solo. Un
   * contatore avrebbe avuto bisogno che ogni pagina chiudesse la propria parentesi, cosa che una
   * pagina rimossa a meta' animazione non fa.
   */
  internal var glassRelaxAt: Long = 0L

  /** Quanta ottica concede il movimento fra pagine, adesso. */
  val glassQuality: Float
    get() = if (SystemClock.uptimeMillis() < glassRelaxAt) {
      FluidGlassQuality.ScrollingFloor
    } else {
      1f
    }

  internal fun markMoving() {
    glassRelaxAt = SystemClock.uptimeMillis() + GlassRelaxHoldMillis
  }

  private companion object {
    /**
     * Quanto vale un timbro. Poco piu' di due fotogrammi a 60 Hz: abbastanza da coprire il buco fra
     * un disegno e il successivo, troppo poco perche' il vetro resti scadente dopo che la pagina si
     * e' fermata.
     */
    const val GlassRelaxHoldMillis = 40L
  }
}

val LocalRouteMotionSignals: ProvidableCompositionLocal<RouteMotionSignals> =
  staticCompositionLocalOf { RouteMotionSignals() }

@Composable
fun rememberRouteMotionSignals(): RouteMotionSignals = remember { RouteMotionSignals() }

/**
 * Wraps one navigation destination so that whatever is leaving loses focus while it goes.
 *
 * Only the departing side is ever blurred. Blurring both would double the cost of the most
 * expensive frame in the app for no legibility gain — and the arriving page is the one the user is
 * trying to read.
 */
/**
 * Se questa schermata e' quella che occupa davvero lo schermo, adesso.
 *
 * Durante una transizione di rotta — e per tutta la durata di un back predittivo, che puo' essere
 * lunga quanto il dito resta appoggiato — **due schermate sono composte insieme**. La chrome che
 * sta sopra il navigation host (la barra in vetro) ne rifrange una sola, e senza questo segnale
 * sceglierebbe l'ultima entrata in composizione: cioe' la destinazione, dal primo fotogramma del
 * gesto, mentre l'utente sta ancora decidendo se confermarlo.
 *
 * Il criterio e' `currentState` e non `targetState`: durante un back predittivo la pagina in arrivo
 * ha gia' `targetState == Visible` ma resta `currentState == PreEnter` fino a che la transizione
 * non si compie, mentre quella che si sta lasciando e' `Visible` per tutto il gesto. Se il gesto
 * viene annullato, la pagina in arrivo non diventa mai davanti — che e' esattamente quello che si
 * vede sullo schermo.
 *
 * Fuori da un navigation host il valore e' `true`: chi non ha una transizione e' sempre davanti.
 */
val LocalFluidRouteFront: ProvidableCompositionLocal<State<Boolean>> =
  staticCompositionLocalOf { mutableStateOf(true) }

@Deprecated(
  "Dalla 3.0 le pagine le muove FluidNavHost (dev.antigravity.fluidengine.ui.navigation): opache, " +
    "sotto il dito, senza sfocatura ne' angoli. Questo involucro tiene solo il davanti e il vetro " +
    "che si assottiglia, per le app non ancora migrate.",
)
@Composable
fun FluidRouteMotionHost(
  animatedVisibilityScope: AnimatedVisibilityScope,
  content: @Composable () -> Unit,
) {
  // Vale in entrambi i rami: la barra deve sapere chi e' davanti anche quando il movimento e'
  // disattivato, perche' il problema e' la sorgente del vetro, non l'animazione.
  //
  // **Il passaggio di consegne avviene a meta' strada, e serve una frazione per saperlo.** Con il
  // solo `currentState` la pagina che se ne va resta "davanti" per tutto il gesto: a back trascinato
  // fino in fondo la barra rifrangeva ancora la pagina che non si vede quasi piu'. Con il solo
  // `targetState` il difetto e' speculare, ed e' quello di partenza: la destinazione vince dal primo
  // fotogramma, quando sullo schermo non c'e' ancora. La transizione anima questa frazione con il
  // dito — il back predittivo la guida davvero — quindi superato il mezzo vince chi sta arrivando, e
  // se il gesto viene annullato la frazione torna indietro da sola.
  val frontness = with(animatedVisibilityScope) {
    transition.animateFloat(
      transitionSpec = { tween(FluidMotion.DurationExpand, easing = FluidMotion.EaseEmphasized) },
      label = "route frontness",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
  }
  val isFront = remember(frontness) { derivedStateOf { frontness.value > 0.5f } }
  val signals = LocalRouteMotionSignals.current

  // Dalla 3.0 niente sfocatura (6 dp a tutto schermo: il fotogramma piu' caro dell'app nel momento
  // che ne ha meno) e niente angoli: le pagine sono opache e la carta e' di FluidNavHost. Resta il
  // timbro che assottiglia il vetro mentre due pagine si muovono.
  Box(
    modifier = Modifier
      .fillMaxSize()
      .graphicsLayer {
        val moving = frontness.value > 0.001f && frontness.value < 0.999f
        if (moving) signals.markMoving()
      },
  ) {
    CompositionLocalProvider(LocalFluidRouteFront provides isFront) { content() }
  }
}

/**
 * Per un grafo migrato a FluidNavHost che chiama ancora l'involucro con `this@composable`: il
 * movimento lo fa l'host, e questo non fa piu' niente.
 */
@Deprecated(
  "Con FluidNavHost l'involucro non serve: togli FluidRouteMotionHost e lascia il contenuto.",
  ReplaceWith("content()"),
)
@Composable
fun FluidRouteMotionHost(
  @Suppress("UNUSED_PARAMETER") scope: FluidPageScope,
  content: @Composable () -> Unit,
) {
  content()
}
