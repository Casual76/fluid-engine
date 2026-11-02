package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidBackButton
import dev.antigravity.fluidengine.ui.fluid.FluidBarTitleMotion
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidTopBarActions
import dev.antigravity.fluidengine.ui.fluid.FluidTopBarGlass
import dev.antigravity.fluidengine.ui.fluid.FluidTopBarRow
import dev.antigravity.fluidengine.ui.fluid.FluidTopBarTitle
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.LocalFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberCombinedGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberCompactTitleStyle
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole

/**
 * La barra ancorata (3.0, fase 8): **una per host**, sopra le pagine e sotto la chrome dell'app.
 *
 * Le pagine descrivono la loro barra ([FluidPageBarSpec]); questa la disegna e, mentre le pagine si
 * muovono, la fa passare dall'una all'altra leggendo tutto in disegno — nessuna ricomposizione per
 * fotogramma:
 *
 *  - **il vetro** rifrange le due pagine insieme, ognuna dove sta in quel fotogramma (le
 *    registrazioni stanno al loro offset vivo), e l'intensita' passa dall'una all'altra. Lo stesso
 *    impilamento va alla chrome dell'app, cosi' la tab bar non cambia fondale di colpo a meta';
 *  - **il titolo grande** e' contenuto e viaggia con la sua pagina; **quello agganciato** e' chrome:
 *    quello che esce sfuma nella prima meta' e scivola verso l'uscita, quello che arriva compare dal
 *    40% e arriva dallo stesso lato;
 *  - **il tasto indietro** e' uno, mai ricreato: se ce l'hanno tutte e due le pagine sta fermo al
 *    pixel; fra la radice e una figlia nasce (o si spegne) da un seme di un pixel;
 *  - **i tasti di vetro** delle azioni non disegnano il loro vetro mentre le pagine cambiano: una
 *    superficie sola di Fluid-physics porta i pezzi della pagina che esce su quelli della pagina che
 *    arriva, e le icone si incrociano sopra. Oltre sei pezzi, o con un controllo che non e' una
 *    capsula di vetro, le icone si incrociano e basta;
 *  - **si tocca solo a riposo**: durante il movimento la riga dei controlli non prende tocchi.
 */
@Composable
internal fun FluidAnchoredBar(
  state: FluidPageHostState,
  registry: FluidPageBarRegistry,
  lowerKey: Any?,
  upperKey: Any?,
) {
  val moving = state.isMoving
  val upperSpec = upperKey?.let { registry[it]?.spec }
  val lowerSpec = if (moving) lowerKey?.let { registry[it]?.spec } else null
  val density = LocalDensity.current
  val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val hasBar = upperSpec != null || lowerSpec != null
  val barBottomPx = with(density) { (statusBar + FluidScreenDefaults.ControlRowHeight).toPx() }
  SideEffect { state.barBottomPx = if (hasBar) barBottomPx else 0f }
  DisposableEffect(state) { onDispose { state.barBottomPx = 0f } }
  if (!hasBar) return

  // Le pagine che la barra mostra, dalla piu' bassa: il ruolo di ognuna si rilegge in disegno.
  val shown: List<Pair<Any, FluidPageBarSpec>> = buildList {
    if (lowerKey != null && lowerSpec != null) add(lowerKey to lowerSpec)
    if (upperKey != null && upperSpec != null) add(upperKey to upperSpec)
  }
  val currentShown by rememberUpdatedState(shown)

  // Il vetro: le due registrazioni impilate mentre ci si muove, quella della cima a riposo.
  val upperBackdrop = upperSpec?.backdrop
  val lowerBackdrop = lowerSpec?.backdrop
  val combined = if (upperBackdrop != null && lowerBackdrop != null) {
    key(lowerBackdrop, upperBackdrop) { rememberCombinedGlassBackdrop(lowerBackdrop, upperBackdrop) }
  } else {
    null
  }
  val barBackdrop = combined ?: upperBackdrop ?: lowerBackdrop
  val chrome = LocalFluidChromeController.current
  val owner = remember { Any() }
  DisposableEffect(chrome, combined) {
    chrome?.setTransitionBackdrop(owner, combined)
    onDispose { chrome?.setTransitionBackdrop(owner, null) }
  }
  val tint = (upperSpec ?: lowerSpec)?.barTint ?: GlassDefaults.barTint()
  // Letta in disegno: muove l'intensita' fra le due pagine, e per questo fa anche rifare la cattura
  // a ogni fotogramma del movimento (le pagine si spostano sotto un vetro che sta fermo).
  val intensity: () -> Float = remember(state) {
    {
      val e = state.frontFraction()
      var amount = 0f
      for ((page, spec) in currentShown) {
        val weight = when (state.roleOf(page)) {
          PageRole.Upper -> 1f - e
          PageRole.Lower -> e
          PageRole.Gone -> 0f
        }
        amount += weight * spec.glassIntensity.value
      }
      amount.coerceIn(0f, 1f)
    }
  }
  val barGlass = rememberGlassBackdrop()
  val controlBackdrop = if (barBackdrop != null) {
    key(barBackdrop) { rememberCombinedGlassBackdrop(barBackdrop, barGlass) }
  } else {
    currentGlassBackdrop()
  }

  val physics = rememberFluidPhysicsState(FluidForm.Slab(Rect(0f, 0f, 1f, 1f)))
  val journey = remember(state, physics) { FluidBarJourney(state, physics) }
  journey.blendPx = with(density) { 4.dp.toPx() }
  val backKeys = buildSet {
    if (upperKey != null && upperSpec?.onBack != null) add(upperKey)
    if (lowerKey != null && lowerSpec?.onBack != null) add(lowerKey)
  }
  SideEffect {
    journey.backKeys = backKeys
    journey.recompute()
  }
  val shiftPx = with(density) { FluidPageMotion.BarTitleShiftDp.dp.toPx() }
  val onTapTitle by rememberUpdatedState(upperSpec?.onTapTitle)
  val onBack by rememberUpdatedState(upperSpec?.onBack)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(statusBar + FluidScreenDefaults.ControlRowHeight + FluidScreenDefaults.GlassFadeTail),
  ) {
    if (barBackdrop != null) {
      FluidTopBarGlass(backdrop = barBackdrop, tint = tint, intensity = intensity, exports = barGlass)
    }
    CompositionLocalProvider(LocalGlassBackdrop provides controlBackdrop) {
      FluidTopBarRow(
        onTapTitle = { if (!state.isMoving) onTapTitle?.invoke() },
        // Si tocca solo a riposo: durante il movimento la riga beve tutto prima dei controlli.
        modifier = Modifier.pointerInput(state) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              if (state.isMoving) event.changes.forEach { it.consume() }
            }
          }
        },
      ) {
        if (moving) {
          // Fra il vetro della barra e le icone: i pezzi che viaggiano. Solo durante il movimento.
          Box(
            modifier = Modifier
              .matchParentSize()
              .onGloballyPositioned { coordinates ->
                journey.node = coordinates
                journey.recompute()
              }
              .fluidPhysicsSurface(
                state = physics,
                backdrop = controlBackdrop,
                tint = GlassDefaults.controlTint(),
                role = GlassRole.Interactive,
                optics = GlassDefaults.buttonOptics(),
                intensity = { if (journey.active) 1f else 0f },
              ),
          )
        }
        for ((page, spec) in shown) {
          key(page) { BarTitle(state, page, spec, shiftPx) }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (backKeys.isNotEmpty()) {
            BarBackButton(state, journey) { if (!state.isMoving) onBack?.invoke() }
          } else {
            Spacer(Modifier.width(FluidScreenDefaults.HorizontalPadding))
          }
          Spacer(Modifier.weight(1f))
          Box(contentAlignment = Alignment.CenterEnd) {
            for ((page, spec) in shown) {
              key(page) { BarActions(state, page, spec, controlBackdrop, journey) }
            }
          }
          Spacer(Modifier.width(6.dp))
        }
      }
    }
  }
}

/** Il titolo di una pagina nella barra, con il suo moto e, per quella sotto, la parte che si vede. */
@Composable
private fun BoxScope.BarTitle(state: FluidPageHostState, page: Any, spec: FluidPageBarSpec, shiftPx: Float) {
  val motion = remember(state, page, shiftPx) {
    FluidBarTitleMotion(
      pageX = { state.pageOffsetX(state.roleOf(page)) },
      pageScale = { state.pageScale(state.roleOf(page)) },
      pageCenter = { Offset(state.width / 2f, state.height / 2f) },
      chromeX = { barTitleChrome(state, page, shiftPx).second },
      chromeAlpha = { barTitleChrome(state, page, shiftPx).first },
    )
  }
  CompositionLocalProvider(spec.context) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .drawWithContent {
          when (state.roleOf(page)) {
            PageRole.Gone -> Unit
            PageRole.Upper -> drawContent()
            PageRole.Lower -> if (state.kind != FluidPageMove.None) {
              // Il titolo grande della pagina sotto e' suo contenuto: dove la pagina sopra la copre,
              // lo copre anche lui. La barra sta sopra tutte e due, quindi il taglio lo fa qui.
              val (left, right) = state.upperCoverage()
              val far = size.width * 4f + size.height * 4f
              if (left > 0.5f) {
                clipRect(left = -far, top = -far, right = left, bottom = far) {
                  this@drawWithContent.drawContent()
                }
              } else {
                clipRect(left = right, top = -far, right = far, bottom = far) {
                  this@drawWithContent.drawContent()
                }
              }
            }
          }
        },
    ) {
      FluidTopBarTitle(
        title = spec.title,
        collapseProgress = spec.collapseProgress,
        activeFacet = spec.activeFacet,
        morphState = spec.morphState,
        compactStyle = spec.compactStyle,
        contentTranslation = spec.contentTranslation,
        fontScale = spec.fontScale,
        reducedMotion = spec.reducedMotion,
        motion = motion,
      )
    }
  }
}

/** (alpha, traslazione x) del titolo agganciato di [page] in questo fotogramma. */
private fun barTitleChrome(state: FluidPageHostState, page: Any, shiftPx: Float): Pair<Float, Float> {
  val role = state.roleOf(page)
  if (role == PageRole.Gone) return 0f to 0f
  if (state.kind == FluidPageMove.None) return (if (role == PageRole.Upper) 1f else 0f) to 0f
  val forward = state.kind == FluidPageMove.Push || state.kind == FluidPageMove.Peer
  val direction = if (state.kind == FluidPageMove.Peer) state.peerDirection else state.direction
  return FluidPageMotion.barTitleFrame(state.frontFraction(), role == PageRole.Upper, forward, shiftPx, direction)
}

/** Le azioni di una pagina, nel contesto della pagina, iscritte al viaggio dei pezzi. */
@Composable
private fun BarActions(
  state: FluidPageHostState,
  page: Any,
  spec: FluidPageBarSpec,
  controlBackdrop: GlassBackdropState,
  journey: FluidBarJourney,
) {
  val sink = remember(journey, page) {
    FluidBarPieceSink(suppressed = { journey.active }, onChanged = journey::recompute)
  }
  DisposableEffect(journey, page, sink) {
    journey.sinks[page] = sink
    journey.recompute()
    onDispose {
      if (journey.sinks[page] === sink) journey.sinks.remove(page)
      journey.recompute()
    }
  }
  CompositionLocalProvider(spec.context) {
    CompositionLocalProvider(
      LocalGlassBackdrop provides controlBackdrop,
      LocalFluidBarPieceSink provides sink,
    ) {
      FluidTopBarActions(
        actions = spec.actions,
        modifier = Modifier.graphicsLayer {
          val m = FluidPageMotion.barMorph(state.frontFraction())
          alpha = when (state.roleOf(page)) {
            PageRole.Upper -> if (state.isMoving) FluidPageMotion.barIconAlpha(m, ofLower = false) else 1f
            PageRole.Lower -> if (state.isMoving) FluidPageMotion.barIconAlpha(m, ofLower = true) else 0f
            PageRole.Gone -> 0f
          }
        },
      )
    }
  }
}

/** Il tasto indietro della barra: uno per host, composto finche' una delle due pagine ce l'ha. */
@Composable
private fun BarBackButton(state: FluidPageHostState, journey: FluidBarJourney, onBack: () -> Unit) {
  val sink = remember(journey) {
    FluidBarPieceSink(suppressed = { journey.active && journey.backInJourney }, onChanged = journey::recompute)
  }
  DisposableEffect(journey, sink) {
    journey.backSink = sink
    journey.recompute()
    onDispose {
      if (journey.backSink === sink) journey.backSink = null
      journey.recompute()
    }
  }
  CompositionLocalProvider(LocalFluidBarPieceSink provides sink) {
    Box(
      modifier = Modifier.graphicsLayer {
        val upperHas = journey.hasBack(state.upper)
        val lowerHas = journey.hasBack(state.lower)
        val m = FluidPageMotion.barMorph(state.frontFraction())
        alpha = when {
          // Tutte e due lo hanno: fermo al pixel, sempre intero.
          upperHas && lowerHas -> 1f
          upperHas -> if (state.isMoving) FluidPageMotion.barIconAlpha(m, ofLower = false) else 1f
          lowerHas -> if (state.isMoving) FluidPageMotion.barIconAlpha(m, ofLower = true) else 0f
          else -> 0f
        }
      },
    ) {
      FluidBackButton(onBack)
    }
  }
}

/**
 * Il viaggio dei pezzi di vetro della barra fra due pagine: raccoglie dove stanno le lenti dei tasti
 * delle due pagine (e quella del tasto indietro, se c'e' da una parte sola), le accoppia
 * ([FluidPageMotion.barPieceJourney], i semi per chi resta solo) e guida la superficie di
 * Fluid-physics col progresso del dito ([FluidPageMotion.barMorph]).
 */
@Stable
internal class FluidBarJourney(
  private val state: FluidPageHostState,
  private val physics: FluidPhysicsState,
) {
  var node: LayoutCoordinates? = null
  val sinks = HashMap<Any, FluidBarPieceSink>()
  var backSink: FluidBarPieceSink? = null
  var backKeys: Set<Any> = emptySet()
  var blendPx: Float = 0f

  /** Se la superficie fisica porta i pezzi adesso (letto in disegno dai tasti, che spengono il vetro). */
  var active by mutableStateOf(false)
    private set

  /** Il tasto indietro viaggia solo se ce l'ha una pagina sola: se ce l'hanno tutte e due sta fermo. */
  val backInJourney: Boolean get() = hasBack(state.upper) != hasBack(state.lower)

  fun hasBack(page: Any?): Boolean = page != null && page in backKeys

  fun recompute() {
    val node = node?.takeIf { it.isAttached }
    if (node == null || !state.isMoving) {
      active = false
      return
    }
    val from = ArrayList<Rect>()
    val to = ArrayList<Rect>()
    val upper = state.upper
    val lower = state.lower
    if (!collect(upper?.let(sinks::get), node, from) || !collect(lower?.let(sinks::get), node, to)) {
      active = false
      return
    }
    if (backInJourney) {
      val back = ArrayList<Rect>()
      if (!collect(backSink, node, back) || back.isEmpty()) {
        active = false
        return
      }
      if (hasBack(upper)) from += back.first() else to += back.first()
    }
    val pairs = FluidPageMotion.barPieceJourney(from, to, node.size.width.toFloat())
    if (pairs == null) {
      active = false
      return
    }
    physics.driveExternally(
      from = formOf(pairs.map { it.first }),
      to = formOf(pairs.map { it.second }),
      progress = { FluidPageMotion.barMorph(state.frontFraction()) },
    )
    active = true
  }

  private fun collect(sink: FluidBarPieceSink?, node: LayoutCoordinates, out: MutableList<Rect>): Boolean {
    sink ?: return true
    for (piece in sink.pieces) {
      // Un controllo che non e' una capsula di vetro: il campo fuso non lo sa disegnare uguale.
      if (!piece.capsule) return false
      val coordinates = piece.coordinates?.takeIf { it.isAttached } ?: continue
      out += node.localBoundingBoxOf(coordinates, clipBounds = false)
    }
    return true
  }

  private fun formOf(rects: List<Rect>): FluidForm {
    val slabs = rects.map { FluidForm.Slab(it, FluidCornerRadii.all(it.minDimension / 2f)) }
    // Un raccordo piccolo di proposito: ai due capi i pezzi devono essere i tasti, separati come a
    // riposo, e due lenti a pochi dp l'una dall'altra con il raggio di serie si fonderebbero.
    return slabs.singleOrNull() ?: FluidForm.Group(slabs, blendRadius = blendPx)
  }
}

/**
 * La barra di una pagina che non e' una `FluidScreen` (3.0): titolo agganciato, tasto indietro,
 * azioni, e il vetro se c'e' una registrazione da rifrangere ([backdrop]).
 *
 * Dentro un [FluidPageHost] si descrive e la disegna l'host, ancorata come quella di ogni altra
 * pagina; fuori si disegna qui, in cima a chi la chiama (da mettere in un `Box` sopra il contenuto).
 * Senza [backdrop] la barra e' trasparente: titolo e controlli sul contenuto.
 */
@Composable
fun FluidPageChrome(
  title: String,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  backdrop: GlassBackdropState? = null,
  barTint: GlassTint? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  val docked: State<Float> = remember { mutableFloatStateOf(1f) }
  val glass = remember { mutableFloatStateOf(0f) }
  glass.floatValue = if (backdrop != null) 1f else 0f
  val facet: State<String?> = remember { mutableStateOf<String?>(null) }
  val compactStyle = rememberCompactTitleStyle(MaterialTheme.typography.displayLarge)
  val density = LocalDensity.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val slot = LocalFluidPageBar.current
  if (slot != null) {
    RegisterFluidPageBar(
      slot,
      FluidPageBarSpec(
        title = title,
        activeFacet = facet,
        onBack = onBack,
        actions = actions,
        context = currentCompositionLocalContext,
        collapseProgress = docked,
        glassIntensity = glass,
        morphState = null,
        compactStyle = compactStyle,
        contentTranslation = NoTranslation,
        fontScale = density.fontScale,
        reducedMotion = reducedMotion,
        onTapTitle = null,
        barTint = barTint,
        backdrop = backdrop,
      ),
    )
    return
  }

  val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val barGlass = rememberGlassBackdrop()
  val controlBackdrop = if (backdrop != null) {
    key(backdrop) { rememberCombinedGlassBackdrop(backdrop, barGlass) }
  } else {
    currentGlassBackdrop()
  }
  val tint = barTint ?: GlassDefaults.barTint()
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(statusBar + FluidScreenDefaults.ControlRowHeight + FluidScreenDefaults.GlassFadeTail),
  ) {
    if (backdrop != null) {
      FluidTopBarGlass(backdrop = backdrop, tint = tint, intensity = { 1f }, exports = barGlass)
    }
    CompositionLocalProvider(LocalGlassBackdrop provides controlBackdrop) {
      FluidTopBarRow(onTapTitle = null) {
        FluidTopBarTitle(
          title = title,
          collapseProgress = docked,
          activeFacet = facet,
          morphState = null,
          compactStyle = compactStyle,
          contentTranslation = NoTranslation,
          fontScale = density.fontScale,
          reducedMotion = reducedMotion,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (onBack != null) {
            FluidBackButton(onBack)
          } else {
            Spacer(Modifier.width(FluidScreenDefaults.HorizontalPadding))
          }
          Spacer(Modifier.weight(1f))
          FluidTopBarActions(actions)
          Spacer(Modifier.width(6.dp))
        }
      }
    }
  }
}

private val NoTranslation: () -> Float = { 0f }
