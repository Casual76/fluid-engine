package dev.antigravity.fluidengine.ui.page

import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeSplit
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidBackButton
import dev.antigravity.fluidengine.ui.fluid.FluidBarEdgeInset
import dev.antigravity.fluidengine.ui.fluid.FluidBarTitleMotion
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidTopBarActions
import dev.antigravity.fluidengine.ui.fluid.FluidTopBarControls
import dev.antigravity.fluidengine.ui.fluid.FluidTopBarBackground
import dev.antigravity.fluidengine.ui.fluid.NoScrollEdgeShade
import dev.antigravity.fluidengine.ui.fluid.FluidScrollEdgeShade
import dev.antigravity.fluidengine.ui.fluid.LocalFluidScrollEdgeShade
import dev.antigravity.fluidengine.ui.fluid.fluidScrollEdgeInk
import dev.antigravity.fluidengine.ui.fluid.rememberFluidScrollEdgeShade
import dev.antigravity.fluidengine.ui.fluid.FluidTopBarRow
import dev.antigravity.fluidengine.ui.fluid.fluidChromeScrollEdge
import dev.antigravity.fluidengine.ui.fluid.fluidTopBarChromeHeight
import dev.antigravity.fluidengine.ui.fluid.FluidTopBarTitle
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberCombinedGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberCompactTitleStyle
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.field.glassTravelShadowReachDp
import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole
import dev.antigravity.fluidengine.ui.fluid.LocalGlassSharedHost
import dev.antigravity.fluidengine.ui.fluid.GlassInkContent

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
 *  - **i tasti di vetro** delle azioni non disegnano il loro vetro mentre la loro forma viaggia: una
 *    superficie sola di Fluid-physics porta i pezzi della pagina che esce su quelli della pagina che
 *    arriva, e le icone si incrociano sopra. Il vetro passa di mano quando la forma parte e quando si
 *    ferma, in un fotogramma solo e senza vuoti ([FluidBarJourney.active], F1B.2). Oltre sei pezzi, o con
 *    un controllo che non e' una capsula di vetro, le icone si incrociano e basta;
 *  - **i controlli restano disponibili**: durante il movimento risponde la cima della pila richiesta;
 *    la pagina uscente continua a disegnarsi senza poter eseguire azioni.
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
  //
  // Dalla 4.0 sotto `Liquid` pesa allo stesso modo il bordo dello scorrimento (F2.5): fra una pagina in cima (niente
  // sotto la barra, zero) e una scorsa (uno) la striscia arriva e se ne va con la pagina, senza scatti, perche'
  // l'intensita' scala sfocatura e velo e non l'alfa di un'immagine.
  //
  // Dal seguito di F2.5 la striscia, mentre le pagine si muovono, non pesa più le due intensità con la corsa: ogni
  // pagina si porta la sua ([anchoredBarSplit]), dove sta in quel fotogramma. Con la corsa, la striscia della pagina che
  // esce in un ritorno si spegneva tutta nei primi fotogrammi (la molla è veloce all'inizio: metà corsa in sei), mentre
  // la pagina era ancora per metà sullo schermo. Qui l'intensità serve al cancello: c'è se una delle due ce l'ha.
  val intensity: () -> Float = remember(state) {
    {
      if (state.isMoving) {
        anchoredBarSplit(state, currentShown, null)?.let { maxOf(it.inside, it.outside) } ?: 0f
      } else {
        val e = state.frontFraction()
        var amount = 0f
        for ((page, spec) in currentShown) {
          amount += anchoredBarPageWeight(state.roleOf(page), e) * spec.glassIntensity.value
        }
        amount.coerceIn(0f, 1f)
      }
    }
  }
  val splitHolder = remember(state) { GlassScrollEdgeSplit() }
  val split: () -> GlassScrollEdgeSplit? = remember(state, splitHolder) {
    { if (state.isMoving) anchoredBarSplit(state, currentShown, splitHolder) else null }
  }
  val barGlass = rememberGlassBackdrop()
  val controlBackdrop = if (barBackdrop != null) {
    key(barBackdrop) { rememberCombinedGlassBackdrop(barBackdrop, barGlass) }
  } else {
    currentGlassBackdrop()
  }

  val physics = rememberFluidPhysicsState(FluidForm.Slab(Rect(0f, 0f, 1f, 1f)))
  // Di quanto si allarga in alto e in basso il nodo della superficie che viaggia: fin dove arriva l'ombra di un tasto
  // (la regola della taglia non cresce sotto i 64 dp di lato corto, e i pezzi della barra sono tasti).
  val travelShadowGrowPx = with(density) {
    glassTravelShadowReachDp(GlassDefaults.buttonOptics().sanitized(), gain = 0f).dp.roundToPx()
  }
  val journey = remember(state, physics) { FluidBarJourney(state, physics) }
  journey.blendPx = with(density) { 4.dp.toPx() }
  val backKeys = buildSet {
    if (upperKey != null && upperSpec?.onBack != null) add(upperKey)
    if (lowerKey != null && lowerSpec?.onBack != null) add(lowerKey)
  }
  SideEffect {
    journey.backKeys = backKeys
    journey.knownPages = shown.mapTo(HashSet()) { it.first }
    journey.recompute()
  }
  val shiftPx = with(density) { FluidPageMotion.BarTitleShiftDp.dp.toPx() }
  // Quanto occupano il tasto indietro e le azioni: il titolo agganciato si ferma prima (3.0.1).
  val controls = remember { FluidTopBarControls() }
  // In un pop upper e' la pagina uscente: non deve conservare i callback della barra.
  val activeSpec = state.interactionPage?.let { registry[it]?.spec }
  val scrollEdge = fluidChromeScrollEdge()
  val shade = if (scrollEdge && barBackdrop != null) {
    key(barBackdrop) { rememberFluidScrollEdgeShade(barBackdrop) }
  } else {
    NoScrollEdgeShade
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(fluidTopBarChromeHeight(statusBar, scrollEdge)),
  ) {
    if (barBackdrop != null) {
      // Il bordo dello scorrimento col materiale nuovo, la fascia altrove (F2.5): vedi `FluidTopBarBackground`.
      FluidTopBarBackground(
        scrollEdge = scrollEdge,
        backdrop = barBackdrop,
        tint = tint,
        intensity = intensity,
        exports = barGlass,
        split = split,
        shade = shade,
      )
    }
    // Un colore di base sotto il contesto di ogni pagina (3.0.1): una registrazione che non lo dice
    // (una pagina senza `FluidScreen`, un'app senza `Surface` alla radice) ha comunque il testo del fondo,
    // non il nero di `LocalContentColor` fuori da una `Surface`. Chi lo dice vince: il suo contesto sta sopra.
    //
    // La riga dei tasti non prende la qualità ridotta delle transizioni (giro di correzione di F1B.2, riga 16 del
    // tablet): mentre due pagine si muovono `RouteMotionSignals.glassQuality` scende a `ScrollingFloor` (0,25), e la
    // superficie che viaggia catturava a un quarto della risoluzione, con il bordo speculare ridotto. Il filo di un
    // pixel e mezzo, ingrandito quattro volte, era un filo grigio sottile senza la riga chiara: «un altro bordo» per
    // tutto il viaggio, che tornava netto al passaggio ai tasti fermi. Il banco, a qualità piena, non lo vedeva. I
    // tasti sono pochi e piccoli: lo sfondo della barra sotto (`FluidTopBarBackground`) e le pagine restano al minimo.
    CompositionLocalProvider(
      LocalGlassBackdrop provides controlBackdrop,
      // Il titolo segue il lato della striscia (dal seguito di F2.5): vedi `fluidScrollEdgeInk`.
      LocalContentColor provides fluidScrollEdgeInk(MaterialTheme.colorScheme.onBackground, shade),
      LocalFluidCanvasBackdrop provides null,
      LocalFluidGlassQuality provides null,
      // F2.3c: i tasti della barra sono ospitati dalla decisione della barra (vedi `FluidScrollEdgeShade.host`).
      LocalGlassSharedHost provides shade.shared,
    ) {
      // F2.3c: il titolo e i tasti prendono il lato dalla stessa decisione della striscia, col tema del lato.
      GlassInkContent(shade.ink, LocalContentColor.current) {
      FluidTopBarRow(
        onTapTitle = { state.interactionPage?.let { registry[it]?.spec?.onTapTitle }?.invoke() },
      ) {
        if (moving) {
          // Fra il vetro della barra e le icone: i pezzi che viaggiano. Composta per tutto il movimento, disegna
          // solo mentre porta il vetro.
          //
          // Chi porta il vetro (F1B.2) è UN segnale, `journey.active`, letto qui IN COMPOSIZIONE come lo leggono i
          // tasti (`glassControlSurface`, che in composizione decide se montare il proprio vetro). Le due letture
          // stanno nella stessa passata di composizione, quindi in ogni fotogramma vedono lo stesso valore: o la
          // superficie o i tasti, mai nessuno dei due, mai tutti e due. Il disegno della superficie legge il valore
          // di questa composizione (`carries`), non lo stato vivo: uno stato cambiato dopo la composizione (in un
          // `SideEffect`, al posizionamento) arriverebbe al disegno della superficie un fotogramma prima che ai
          // tasti. Fino a F1B.1 la superficie spariva con `isMoving` e i tasti riaccendevano il vetro solo alla
          // ricomposizione dopo: un fotogramma senza vetro a ogni arrivo (partenza.md, difetto 1).
          val carrying = journey.active
          val carries by rememberUpdatedState(carrying)
          Box(
            modifier = Modifier
              .matchParentSize()
              // L'ombra della superficie è nel suo corpo (F1B.2), e il layer del vetro è ritagliato sul nodo: la
              // riga è alta poco più dei tasti, quindi il nodo si allarga sopra e sotto di quanto arriva l'ombra di
              // un tasto, perché in viaggio si veda tutta, come quella dei tasti fermi. I pezzi si misurano su
              // questo nodo, quindi stanno dove stavano.
              .layout { measurable, constraints ->
                val grow = travelShadowGrowPx
                val placeable = measurable.measure(
                  Constraints.fixed(constraints.maxWidth, constraints.maxHeight + 2 * grow),
                )
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -grow) }
              }
              .onGloballyPositioned { coordinates ->
                journey.node = coordinates
                journey.recompute()
              }
              // Quando non porta il vetro non disegna niente, nemmeno la cattura: è il cancello di `glassPresence`.
              .drawWithContent { if (carries) drawContent() }
              .fluidPhysicsSurface(
                state = physics,
                backdrop = controlBackdrop,
                tint = GlassDefaults.controlTint(),
                role = GlassRole.Interactive,
                optics = GlassDefaults.buttonOptics(),
                intensity = if (carrying) BarTravelFull else BarTravelOff,
              ),
          )
        }
        for ((page, spec) in shown) {
          key(page) { BarTitle(state, page, spec, shiftPx, controls, shade) }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.onSizeChanged { controls.startPx = it.width }) {
            if (backKeys.isNotEmpty()) {
              Box(if (activeSpec?.onBack == null) Modifier.clearAndSetSemantics { } else Modifier) {
                BarBackButton(state, journey) { state.interactionPage?.let { registry[it]?.spec?.onBack }?.invoke() }
              }
            } else {
              Spacer(Modifier.width(FluidScreenDefaults.HorizontalPadding))
            }
          }
          Spacer(Modifier.weight(1f))
          Row(modifier = Modifier.onSizeChanged { controls.endPx = it.width }, verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.CenterEnd) {
              // L'azione della destinazione corrente deve vincere anche l'hit test fra due icone.
              for ((page, spec) in shown.sortedBy { it.first == state.interactionPage }) {
                key(page) { BarActions(state, page, spec, controlBackdrop, journey, shade) }
              }
            }
            // Il vetro dell'ultima azione a 16 dp dal bordo (4.0, R6), come in `FluidScreen`.
            Spacer(Modifier.width(FluidBarEdgeInset))
          }
        }
      }
      }
    }
  }
}

/**
 * Il taglio della striscia della barra fra due pagine che si muovono (dal seguito di F2.5): la pagina sopra copre da
 * [FluidPageHostState.upperCoverage], e lì vale la sua intensità; fuori quella della pagina sotto. Null se non c'è niente
 * da tagliare. Scrive in [into] (riusato a ogni fotogramma), o in uno nuovo.
 */
internal fun anchoredBarSplit(
  state: FluidPageHostState,
  shown: List<Pair<Any, FluidPageBarSpec>>,
  into: GlassScrollEdgeSplit?,
): GlassScrollEdgeSplit? {
  var upper = 0f
  var lower = 0f
  var any = false
  for ((page, spec) in shown) {
    when (state.roleOf(page)) {
      PageRole.Upper -> {
        upper = spec.glassIntensity.value
        any = true
      }
      PageRole.Lower -> {
        lower = spec.glassIntensity.value
        any = true
      }
      PageRole.Gone -> Unit
    }
  }
  if (!any) return null
  val (from, to) = state.upperCoverage()
  return anchoredBarSplitOf(from, to, upper, lower, into)
}

/** La parte pura di [anchoredBarSplit]: la pagina sopra copre da [from] a [to] px. */
internal fun anchoredBarSplitOf(
  from: Float,
  to: Float,
  upper: Float,
  lower: Float,
  into: GlassScrollEdgeSplit? = null,
): GlassScrollEdgeSplit {
  val split = into ?: GlassScrollEdgeSplit()
  split.from = from
  split.to = to
  split.inside = upper.coerceIn(0f, 1f)
  split.outside = lower.coerceIn(0f, 1f)
  return split
}

/**
 * Quanto conta l'intensità di una pagina nello sfondo della barra ancorata, a [frontFraction] del movimento: tutta
 * quella di sopra a riposo, tutta quella di sotto quando sta per tornare davanti, in mezzo in proporzione. Le due
 * somme fanno sempre uno, quindi fra una pagina in cima (zero) e una scorsa (uno) lo sfondo va e viene con la pagina,
 * senza salti: era il peso della fascia, ed è quello del bordo dello scorrimento (F2.5).
 */
internal fun anchoredBarPageWeight(role: PageRole, frontFraction: Float): Float = when (role) {
  PageRole.Upper -> 1f - frontFraction
  PageRole.Lower -> frontFraction
  PageRole.Gone -> 0f
}

/** Il titolo di una pagina nella barra, con il suo moto e, per quella sotto, la parte che si vede. */
@Composable
private fun BoxScope.BarTitle(
  state: FluidPageHostState,
  page: Any,
  spec: FluidPageBarSpec,
  shiftPx: Float,
  controls: FluidTopBarControls,
  shade: FluidScrollEdgeShade,
) {
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
    // Il titolo segue il lato della striscia della barra anche col contesto della pagina (dal seguito di F2.5).
    CompositionLocalProvider(LocalContentColor provides fluidScrollEdgeInk(LocalContentColor.current, shade)) {
    Box(
      modifier = Modifier
        .matchParentSize()
        // F2.3c: il titolo svanisce e risale con l'inchiostro della barra quando la barra cambia lato.
        .graphicsLayer { alpha = shade.fade() }
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
        controls = controls,
      )
    }
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
  shade: FluidScrollEdgeShade,
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
      // Il lato della striscia della barra, dopo il contesto della pagina (dal seguito di F2.5): vedi `fluidBarInk`.
      LocalFluidScrollEdgeShade provides shade,
      // F2.3c: i tasti della barra sono ospitati dalla decisione della barra.
      LocalGlassSharedHost provides shade.shared,
      // Il contesto della pagina porta il suo canvas: nella barra un controllo sta sul vetro della barra.
      LocalFluidCanvasBackdrop provides null,
      // E la sua qualità, che scende mentre le pagine si muovono: i tasti della barra restano a qualità piena come la
      // superficie che viaggia (vedi `FluidPageAnchoredBar`). Un tasto che nasce durante la transizione (il primo push
      // e il primo ritorno dopo l'avvio, i tasti di C) si disegnava a 0,25 e non si ridisegnava fino a pagina posata:
      // a +650…+720 ms il filo, la riga chiara e l'ombra comparivano a scatto (riga 18 del tablet).
      LocalFluidGlassQuality provides null,
    ) {
      FluidTopBarActions(
        actions = spec.actions,
        modifier = Modifier
          .then(if (page != state.interactionPage) Modifier.clearAndSetSemantics { } else Modifier)
          .pointerInput(state, page) {
            awaitPointerEventScope {
              while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (page != state.interactionPage) event.changes.forEach { it.consume() }
              }
            }
          }
          .graphicsLayer {
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
        // Una pagina che la barra non ha ancora composto vale come l'altra (vedi `FluidBarJourney.known`): fra B e C la
        // freccia non si spegne per un fotogramma.
        val upperKnown = journey.known(state.upper)
        val lowerKnown = journey.known(state.lower)
        val upperHas = if (upperKnown || !lowerKnown) journey.hasBack(state.upper) else journey.hasBack(state.lower)
        val lowerHas = if (lowerKnown || !upperKnown) journey.hasBack(state.lower) else journey.hasBack(state.upper)
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

  /**
   * Le pagine di cui la barra ha già composto la registrazione (quelle che la sua composizione ha mostrato), scritte col
   * `SideEffect` insieme a [backKeys]. Vedi [backInJourney].
   */
  var knownPages: Set<Any> = emptySet()
  var blendPx: Float = 0f

  /**
   * Se i pezzi delle due pagine sono raccolti e la superficie fisica è guidata fra loro. Lo scrive [recompute], che
   * gira dopo la composizione (nel `SideEffect` della barra e al posizionamento della superficie): da solo non
   * decide niente, entra in [active].
   */
  private var ready by mutableStateOf(false)

  /**
   * Se la superficie fisica porta il vetro dei tasti in questo fotogramma (F1B.2): i pezzi sono pronti, le pagine si
   * muovono e la forma è fra i due capi ([FluidPageMotion.barJourneyCarries]). I tasti tolgono allora solo il
   * proprio materiale, e lo rimettono quando la forma arriva.
   *
   * È **il** segnale del passaggio di consegne: lo leggono in composizione la superficie (se disegnare) e i tasti
   * (se montare il proprio vetro), nella stessa passata. È uno stato derivato: si ricalcola quando la pagina si
   * muove, ma chi lo legge si ricompone solo quando il valore cambia, cioè due volte per viaggio.
   */
  private val carrying = derivedStateOf {
    ready && state.isMoving && FluidPageMotion.barJourneyCarries(FluidPageMotion.barMorph(state.frontFraction()))
  }

  val active: Boolean get() = carrying.value

  /** Il tasto indietro viaggia solo se ce l'ha una pagina sola: se ce l'hanno tutte e due sta fermo. */
  val backInJourney: Boolean
    get() = known(state.upper) && known(state.lower) && hasBack(state.upper) != hasBack(state.lower)

  /**
   * Il tasto indietro viaggia solo fra due pagine che la barra ha già composto (giro di correzione unico di F1B, rilievo 1
   * del critico D). La pila nuova arriva allo stato (`onStack`, nel `SideEffect` dell'host) dopo la composizione: per un
   * fotogramma la cima è una pagina che la barra non ha ancora visto, e [backKeys] non la contiene. Fra B e C, che hanno
   * tutte e due l'indietro, sembrava allora che l'indietro l'avesse una sola: col primo fotogramma del push lungo (la
   * pagina C si compone, ~175 ms) la forma era già in viaggio, il tasto spegneva il suo vetro per una composizione e,
   * rimontandolo, restava senza disco per 84–108 ms (sul tablet, 4 su 4 nei due temi). Una pagina non ancora composta non
   * dice ancora niente: il tasto resta dov'è finché le due pagine non sono note.
   */
  fun known(page: Any?): Boolean = page == null || page in knownPages

  fun hasBack(page: Any?): Boolean = page != null && page in backKeys

  fun recompute() {
    val node = node?.takeIf { it.isAttached }
    if (node == null || !state.isMoving) {
      ready = false
      return
    }
    val from = ArrayList<Rect>()
    val to = ArrayList<Rect>()
    val upper = state.upper
    val lower = state.lower
    if (!collect(upper?.let(sinks::get), node, from) || !collect(lower?.let(sinks::get), node, to)) {
      ready = false
      return
    }
    if (backInJourney) {
      val back = ArrayList<Rect>()
      if (!collect(backSink, node, back) || back.isEmpty()) {
        ready = false
        return
      }
      if (hasBack(upper)) from += back.first() else to += back.first()
    }
    val pairs = FluidPageMotion.barPieceJourney(from, to, node.size.width.toFloat())
    if (pairs == null) {
      ready = false
      return
    }
    physics.driveExternally(
      from = formOf(pairs.map { it.first }),
      to = formOf(pairs.map { it.second }),
      progress = { FluidPageMotion.barMorph(state.frontFraction()) },
    )
    ready = true
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
 *
 * Dalla 4.0, col materiale `Liquid`, lo sfondo della barra e' il **bordo dello scorrimento** (F2.5): il contenuto
 * sotto i tasti si scioglie nel colore del fondo, sempre pieno perche' questa barra non sa quanto la pagina e' scorsa.
 * Il riquadro arriva 61,6 dp sotto la riga dei tasti invece di 34: sopra il contenuto non sposta niente. Sotto Android
 * 13 e con `FluidGlassModel.Classic` resta la fascia di vetro.
 *
 * [barTint] e' **deprecato dalla 4.0** (piano «iOS in Android» 3.11): col materiale `Liquid` e' ignorato (la striscia
 * non ha pellicola) e vale solo per la fascia di ripiego. Kotlin non sa deprecare un parametro solo: lo dice qui.
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
  // Il colore del titolo e dei tasti PRIMA di registrarsi (3.0.1), come fa `FluidScreen`: l'host disegna
  // la barra nel contesto che la pagina gli consegna (`currentCompositionLocalContext`), e una pagina che
  // non e' una `FluidScreen` non ha mai detto il colore del testo: fuori da una `Surface` vale nero, e il
  // titolo agganciato era nero sul tema scuro. Il fondo sotto la barra e' quello della pagina.
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    FluidPageChromeContent(title, modifier, onBack, backdrop, barTint, actions)
  }
}

@Composable
private fun FluidPageChromeContent(
  title: String,
  modifier: Modifier,
  onBack: (() -> Unit)?,
  backdrop: GlassBackdropState?,
  barTint: GlassTint?,
  actions: @Composable RowScope.() -> Unit,
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
  val scrollEdge = fluidChromeScrollEdge()
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(fluidTopBarChromeHeight(statusBar, scrollEdge)),
  ) {
    if (backdrop != null) {
      // Una pagina che non e' una `FluidScreen` non dice quanto e' scorsa: la barra resta piena, come la fascia.
      FluidTopBarBackground(
        scrollEdge = scrollEdge,
        backdrop = backdrop,
        tint = tint,
        intensity = FullBarIntensity,
        exports = barGlass,
        shade = if (scrollEdge) rememberFluidScrollEdgeShade(backdrop) else NoScrollEdgeShade,
      )
    }
    CompositionLocalProvider(LocalGlassBackdrop provides controlBackdrop, LocalFluidCanvasBackdrop provides null) {
      val controls = remember { FluidTopBarControls() }
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
          controls = controls,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.onSizeChanged { controls.startPx = it.width }) {
            if (onBack != null) {
              FluidBackButton(onBack)
            } else {
              Spacer(Modifier.width(FluidScreenDefaults.HorizontalPadding))
            }
          }
          Spacer(Modifier.weight(1f))
          Row(modifier = Modifier.onSizeChanged { controls.endPx = it.width }, verticalAlignment = Alignment.CenterVertically) {
            FluidTopBarActions(actions)
            Spacer(Modifier.width(FluidBarEdgeInset))
          }
        }
      }
    }
  }
}

private val NoTranslation: () -> Float = { 0f }

/** L'intensità dello sfondo di una [FluidPageChrome] da sola: sempre piena (una lambda stabile, non una per ricomposizione). */
private val FullBarIntensity: () -> Float = { 1f }

/** L'intensità della superficie che viaggia: piena mentre porta il vetro, zero quando lo portano i tasti. */
private val BarTravelFull: () -> Float = { 1f }
private val BarTravelOff: () -> Float = { 0f }
