package dev.antigravity.fluidengine.ui.page

import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeSplit
import android.os.Build
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.GlassContentJourney
import dev.antigravity.fluidengine.ui.fluid.GlassContentLayer
import dev.antigravity.fluidengine.ui.fluid.GlassContentRole
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.smoothStep
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarSearchOverlay
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidBottomBar
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarBar
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarDeclaration
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarDefaults
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarExtent
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarGlassId
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarGroupId
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMinimizeProgress
import dev.antigravity.fluidengine.ui.fluid.toolbar.LocalFluidToolbarMinimize
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarMinimizeLayer
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarResolved
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarRole
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.fluid.toolbar.buildFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.fluid.toolbar.diffFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarGlassIds
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarIconSize
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarMetrics
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarModelOf
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarTint
import dev.antigravity.fluidengine.ui.fluid.toolbar.resolveFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionBackEffect
import dev.antigravity.fluidengine.ui.fluid.toolbar.buildFluidScreenToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidTitleHandoffLayer
import dev.antigravity.fluidengine.ui.fluid.toolbar.rememberFluidTitleHandoff
import dev.antigravity.fluidengine.ui.fluid.toolbar.selection
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.runtime.movableContentOf
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
import androidx.compose.ui.unit.offset
import androidx.compose.ui.text.style.TextOverflow
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
 * Il contesto catturato conserva gli State dei local dinamici della pagina, incluso il ViewModelStoreOwner.
 * Se lo stesso provider passa da A a B, Compose 1.10 aggiorna lo State precedente (quello di A)
 * con il valore di B. Le due pagine finirebbero nello stesso ViewModelStore durante il push.
 * Solo il provider cambia identità. Contenitore del vetro, memoria dei tasti e uscita della capsula
 * appartengono all'host e proseguono dal fotogramma corrente.
 */
@Composable
private fun HostBarPageContext(context: CompositionLocalContext, content: @Composable () -> Unit) {
  val bar = remember { movableContentOf<@Composable () -> Unit> { draw -> draw() } }
  key(context) { CompositionLocalProvider(context) { bar(content) } }
}

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
 *
 * **Dalla 4.0 (F4A.3), quando una delle pagine ha dichiarato la sua barra** (`FluidScreen(toolbar = …)`), i tasti non
 * sono più «ancorati alla pagina»: la barra è **una sola, disegnata dal modello** della pagina che le dà i tasti
 * ([fluidPageBarPage]: la cima della pila richiesta), un elemento per `id`, sul piano flottante. Quando quella pagina
 * cambia (push, pop, conferma del gesto indietro, cambio di scheda) la differenza fra i due modelli è un **cambio di
 * stato del suo contenitore**: lo stesso `id` è lo stesso vetro, che resta al suo posto (l'indietro comune fermo al
 * pixel); un'icona diversa si incrocia sfocandosi nel vetro fermo; un'icona uguale non si tocca; un tasto nuovo nasce dal
 * fianco del vicino col collo, o per lente se non ne ha uno; uno che se ne va confluisce o scompare per lente. Il tempo è
 * **quello del piano**, non il progresso della pagina: parte al push (o al rilascio che conferma) e un cambio a metà
 * riparte da dove si trova. Il gesto indietro non tocca i tasti finché non è confermato (D1); l'annullo non fa niente.
 * Il titolo agganciato si scambia sfocandosi, con un passo di 8 dp nel verso della navigazione ([FluidBarTitleSwap]).
 * Un host che non ha mai mostrato una barra dichiarata è quello di prima, identico. Da quando ne mostra una disegna dal
 * modello per sempre: una pagina con le `actions` di sempre porta nel modello solo il suo indietro, e le sue azioni
 * restano nella loro riga come prima ([FluidPageBarTargetMemory.hostModel]).
 */
@Composable
internal fun BoxScope.FluidAnchoredBar(
  state: FluidPageHostState,
  registry: FluidPageBarRegistry,
  lowerKey: Any?,
  upperKey: Any?,
) {
  val moving = state.isMoving
  val upperSpec = upperKey?.let { registry[it]?.spec }
  val lowerSpec = if (moving) lowerKey?.let { registry[it]?.spec } else null
  // F4A.3: la pagina che dà i tasti alla barra (la cima della pila richiesta), e se la barra si disegna dal modello.
  val barPage = fluidPageBarPage(state.interactionPage, upperKey)
  val barSlot = barPage?.let { registry[it] }
  val barSpec = barSlot?.spec
  val modelMode = fluidPageBarUsesModel(listOf(upperSpec, lowerSpec, barSpec))
  // Ricordate prima di ogni uscita anticipata: la memoria della barra disegnata dal modello e lo scambio del titolo
  // sopravvivono a un fotogramma senza barra.
  val target = remember(state) { FluidPageBarTargetMemory() }
  // L'host disegna dal modello da quando ha visto la prima barra dichiarata, e da lì in poi sempre (vedi
  // [FluidPageBarTargetMemory.hostModel]): un host che non ne ha mai vista una è quello di prima, identico.
  val hostModel = target.noteModel(modelMode || lowerKey?.let { registry[it]?.spec?.toolbar } != null)
  val registeredDeclaration = target.update(barPage, barSlot != null, barSpec, moving)
  val withdrawn = barSpec?.minimize?.hidden == true
  val declaration = if (withdrawn) FluidPageBarEmpty else registeredDeclaration
  val swap = remember(state) { FluidBarTitleSwap() }
  val finger = remember(state) { FluidBarFingerMorph(state) }
  val followFinger = FluidPageBarDiagnostics.gestureFollowsFinger
  val policy = LocalFluidMotionPolicy.current
  SideEffect {
    // Il titolo agganciato cambia quando cambia la pagina della barra, sul tempo suo; nella variante che segue il dito
    // il titolo lo porta la corsa, e lo scambio arriva già fatto.
    swap.retarget(
      page = barPage,
      step = FluidPageMotion.barTitleStepSign(state.kind, state.direction),
      snap = policy.reducedMotion || (followFinger && state.kind == FluidPageMove.Back),
    )
  }
  FluidBarTitleClock(swap, policy.durationScale)
  // F6B.1, D1: mentre un gesto indietro non è confermato la barra in basso che la shell legge
  // (`FluidChromeController.bottomReplacement`) resta quella della pagina in cima, anche quando il «davanti» del vetro
  // passa di mano a metà uscita. La tenuta finisce quando la pila cambia (conferma) o quando l'annullo si è posato.
  val replacementChrome = LocalFluidChromeController.current
  val replacementHold = remember { Any() }
  LaunchedEffect(replacementChrome, state) {
    val chrome = replacementChrome ?: return@LaunchedEffect
    try {
      snapshotFlow { fluidPageBackUnconfirmed(state) }.collect { chrome.holdBottomReplacement(replacementHold, it) }
    } finally {
      chrome.holdBottomReplacement(replacementHold, false)
    }
  }
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
  val shownState = rememberUpdatedState(shown)
  val currentShown by shownState
  // Il ritiro della barra (F4A.4) per ciò che è dell'host: l'indietro, la barra del modello e l'area che riporta in cima
  // (giro di correzione di F4A, B1 e C8). Null se nessuna delle pagine che la barra mostra si ritira: allora non cambia
  // niente, nemmeno l'albero.
  val minimizeReduced = shown.firstNotNullOfOrNull { it.second.minimize }?.reducedMotion
  val hostMinimize = if (minimizeReduced == null) {
    null
  } else {
    remember(state, shownState, minimizeReduced) { FluidPageBarMinimize(state, shownState, minimizeReduced) }
  }
  val barHidden by remember(hostMinimize) { derivedStateOf { hostMinimize?.hidden == true } }

  // Il vetro: le due registrazioni impilate mentre ci si muove, quella della cima a riposo.
  val upperBackdrop = upperSpec?.backdrop
  val lowerBackdrop = lowerSpec?.backdrop
  val combined = if (upperBackdrop != null && lowerBackdrop != null) {
    key(lowerBackdrop, upperBackdrop) { rememberCombinedGlassBackdrop(lowerBackdrop, upperBackdrop) }
  } else {
    null
  }
  // Lo zoom (F5B.3): finché la sagoma della pagina sopra non arriva sotto i controlli, sotto la barra c'è la pagina sotto,
  // e la registrazione della pagina sopra (intera, non scalata) non va impilata: la barra rifrange solo quella sotto.
  val zoomBelowBar by remember(state) { derivedStateOf { state.zoomBelowBar() } }
  val barBackdrop = (if (zoomBelowBar) lowerBackdrop else null) ?: combined ?: upperBackdrop ?: lowerBackdrop
  if (hostModel) {
    HostBarPageContext(target.context ?: currentCompositionLocalContext) {
      if (barSpec?.search?.occupiesBottom != true) {
        FluidBottomBar(registeredDeclaration, barBackdrop ?: currentGlassBackdrop(), hostMinimize, Modifier.align(Alignment.BottomCenter))
      }
    }
  }
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
    // Col modello la barra di prima non viaggia: i tasti li trasforma il piano (F4A.3).
    journey.enabled = !modelMode
    // In un host che usa il modello l'indietro è del modello anche fra due pagine di sempre: la barra di prima porta solo
    // le loro azioni.
    if (hostModel) journey.backKeys = emptySet()
    journey.recompute()
  }
  val shiftPx = with(density) { FluidPageMotion.BarTitleShiftDp.dp.toPx() }
  val stepPx = with(density) { FluidPageMotion.BarTitleStepDp.dp.toPx() }
  // Quanto occupano il tasto indietro e le azioni: il titolo agganciato si ferma prima (3.0.1). Col modello lo dice la
  // misura d'arrivo dei gruppi (F4A.3): il titolo segue il bersaglio, non il vetro in volo.
  val controls = remember { FluidTopBarControls() }
  val insets = remember(controls) { FluidBarInsets(controls) }
  val legacyRows = hostModel && shown.any { it.second.toolbar == null }
  val declaredPage = if (legacyRows && modelMode) shown.firstOrNull { it.second.toolbar != null }?.first else null
  SideEffect {
    insets.model = hostModel
    insets.legacyRows = legacyRows
    insets.push()
  }
  // In un pop upper e' la pagina uscente: non deve conservare i callback della barra.
  val activeSpec = state.interactionPage?.let { registry[it]?.spec }
  val scrollEdge = fluidChromeScrollEdge()
  val shade = if (scrollEdge && barBackdrop != null) {
    key(barBackdrop) { rememberFluidScrollEdgeShade(barBackdrop) }
  } else {
    NoScrollEdgeShade
  }
  // La variante del gesto che segue il dito (interruttore delle prove, spento di serie): solo col modello, solo mentre
  // il dito tiene la pagina, e solo fra due pagine che la barra conosce.
  val fingerLayer = followFinger && modelMode && moving && state.kind == FluidPageMove.Back &&
    upperSpec != null && lowerSpec != null && upperSpec.minimize?.hidden != true && lowerSpec.minimize?.hidden != true

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(fluidTopBarChromeHeight(statusBar, scrollEdge)),
  ) {
    if (barBackdrop != null) {
      // Il bordo dello scorrimento col materiale nuovo, la fascia altrove (F2.5): vedi `FluidTopBarBackground`.
      Box(Modifier.matchParentSize().drawWithContent {
        // Una pagina che si ritira ha già disegnato la striscia sotto il campo. Durante il pop
        // l'altra pagina conserva la sua parte della striscia dell'host, fino al bordo della carta.
        val ownUpper = upperSpec?.minimize != null
        val ownLower = lowerSpec?.minimize != null
        if (!ownUpper && !ownLower) drawContent()
        else if (state.isMoving && ownUpper != ownLower) {
          val (left, right) = state.upperCoverage()
          clipRect(left = left, right = right, clipOp = if (ownUpper) ClipOp.Difference else ClipOp.Intersect) {
            this@drawWithContent.drawContent()
          }
        }
      }) {
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
        // Con la barra ritirata la riga non prende più tocchi: sotto c'è il campo fissato, che sale fin lì (B1).
        onTapTitle = if (barHidden) null else ({ state.interactionPage?.let { registry[it]?.spec?.onTapTitle }?.invoke() }),
      ) {
        if (moving && !modelMode) {
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
        if (fingerLayer) {
          BarFingerMorph(
            finger = finger,
            upper = upperSpec!!,
            lower = lowerSpec!!,
            barIsLower = barPage == lowerKey,
            controlBackdrop = controlBackdrop,
            growPx = travelShadowGrowPx,
            shade = shade,
          )
        }
        for ((page, spec) in shown) {
          key(page) {
            BarTitle(
              state = state,
              page = page,
              spec = spec,
              shiftPx = shiftPx,
              controls = controls,
              shade = shade,
              swap = swap.takeIf { modelMode },
              stepPx = stepPx,
              followsFinger = followFinger,
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.onSizeChanged { insets.rowStart(it.width) }) {
            if (!hostModel && backKeys.isNotEmpty()) {
              // L'indietro di sempre si ritira con la barra, come le azioni della pagina (C8).
              Box(
                Modifier
                  .fluidToolbarMinimizeLayer(hostMinimize)
                  .then(if (activeSpec?.onBack == null) Modifier.clearAndSetSemantics { } else Modifier),
              ) {
                BarBackButton(state, journey) { state.interactionPage?.let { registry[it]?.spec?.onBack }?.invoke() }
              }
            } else {
              Spacer(Modifier.width(FluidScreenDefaults.HorizontalPadding))
            }
          }
          Spacer(Modifier.weight(1f))
          Row(modifier = Modifier.onSizeChanged { insets.rowEnd(it.width) }, verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.CenterEnd) {
              // L'azione della destinazione corrente deve vincere anche l'hit test fra due icone.
              for ((page, spec) in shown.sortedBy { it.first == state.interactionPage }) {
                // Col modello qui restano solo le azioni delle pagine con lo slot di sempre (F4A.3), nello stesso
                // posto dell'albero: la stessa composizione prima, durante e dopo il passaggio a una pagina dichiarata.
                if (!hostModel || spec.toolbar == null) {
                  key(page) { BarActions(state, page, spec, controlBackdrop, journey, shade, swap, declaredPage) }
                }
              }
            }
            // Il vetro dell'ultima azione a 16 dp dal bordo (4.0, R6), come in `FluidScreen`.
            Spacer(Modifier.width(FluidBarEdgeInset))
          }
        }
        if (hostModel) {
          HostModelBar(
            declaration = declaration,
            title = (barSpec ?: upperSpec)?.title,
            context = target.context ?: currentCompositionLocalContext,
            controlBackdrop = controlBackdrop,
            shade = shade,
            insets = insets,
            hidden = finger::overlay,
            snap = fingerLayer,
            minimize = hostMinimize,
            trailingOpacity = declaredPage?.let { { swap.alpha(it) } },
          )
        }
      }
      }
    }
  }
  barSpec?.search?.let { search ->
    key(barPage) {
      CompositionLocalProvider(barSpec.context) { FluidToolbarSearchOverlay(search, barSpec.toolbar, controlBackdrop, hostMinimize) }
    }
  }
}

/**
 * C'è un gesto indietro che il rilascio non ha ancora confermato (D1): il dito o il sistema tengono la pagina in cima, o
 * l'annullo la sta riportando al suo posto. La conferma toglie la pagina dalla pila richiesta, e da lì non vale più.
 * Legge stato dell'host, quindi va letta in un `snapshotFlow` o in disegno.
 */
internal fun fluidPageBackUnconfirmed(state: FluidPageHostState): Boolean {
  if (state.kind != FluidPageMove.Back) return false
  val top = state.interactionPage ?: return false
  return top == state.upper
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

/**
 * Il ritaglio di un titolo grande dalla sagoma di uno zoom (F5B.3): dentro per la pagina sopra, fuori per quella sotto.
 * [zoom] è già nelle coordinate di chi disegna ([barTitleZoomClip]).
 */
private inline fun DrawScope.clipZoom(zoom: FluidZoomShape, inside: Boolean, block: DrawScope.() -> Unit) {
  val path = Path().apply { addRoundRect(zoom.toRoundRect()) }
  clipPath(path, if (inside) ClipOp.Intersect else ClipOp.Difference, block)
}

/**
 * La sagoma di uno zoom ([zoom], nelle coordinate dell'host) nelle coordinate del riquadro del titolo, che sta a [origin]
 * nell'host (corr-s4-zoom). Il titolo non sta nell'angolo dell'host: sta nella riga dei controlli, sotto la barra di
 * stato. Ritagliato con la sagoma dell'host, il taglio scendeva dell'altezza della barra di stato: sul Tab S9 il «Zoom»
 * della griglia restava sopra la pagina che si apriva per 27 dp sotto il bordo della sagoma (S4, apertura, fotogrammi
 * 6–8), e al ritorno sopra la pagina che rientrava.
 */
internal fun barTitleZoomClip(zoom: FluidZoomShape, origin: Offset): FluidZoomShape =
  if (origin == Offset.Zero) zoom else zoom.translate(-origin)

/** Dove sta il riquadro [box] nell'host di [state], o zero se uno dei due non è (più) nell'albero. */
private fun barTitleOrigin(state: FluidPageHostState, box: LayoutCoordinates?): Offset {
  val host = state.hostCoordinates?.takeIf { it.isAttached } ?: return Offset.Zero
  val here = box?.takeIf { it.isAttached } ?: return Offset.Zero
  return host.localPositionOf(here, Offset.Zero)
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

/**
 * Il titolo di una pagina nella barra, con il suo moto e, per quella sotto, la parte che si vede.
 *
 * Con il modello ([swap] non null, F4A.3) il titolo **agganciato** non segue più la corsa della pagina: si scambia
 * sfocandosi sul tempo suo quando cambia la pagina della barra, con un passo di [stepPx] nel verso della navigazione
 * (R7 26, A9); la sfocatura vale quanto il titolo è agganciato (il titolo grande è contenuto e viaggia nitido con la sua
 * pagina, R7 27). Nella variante che segue il dito, mentre il dito tiene la pagina, torna il moto legato alla corsa.
 * Una barra dichiarata senza titolo (`FluidToolbarTitleMode.None`, o un elemento al centro) non ne disegna.
 */
@Composable
private fun BoxScope.BarTitle(
  state: FluidPageHostState,
  page: Any,
  spec: FluidPageBarSpec,
  shiftPx: Float,
  controls: FluidTopBarControls,
  shade: FluidScrollEdgeShade,
  swap: FluidBarTitleSwap?,
  stepPx: Float,
  followsFinger: Boolean,
) {
  val toolbar = spec.toolbar
  // F6B.1: quando il centro si occupa (il conteggio di una selezione) il titolo agganciato esce sfocandosi invece di
  // sparire in un fotogramma, e torna allo stesso modo; resta composto finché non è uscito del tutto.
  val principal = toolbar?.hasPrincipal == true
  val handoff = rememberFluidTitleHandoff(hidden = principal)
  val titleGone by remember(handoff) { derivedStateOf { handoff.value >= 1f } }
  if (toolbar != null && (toolbar.titleMode == FluidToolbarTitleMode.None || (principal && titleGone))) return
  // Il moto legato alla corsa vale per le pagine di sempre e, nella variante delle prove, mentre il dito tiene la pagina.
  val byTravel: () -> Boolean = remember(state, swap, followsFinger) {
    { swap == null || (followsFinger && state.kind == FluidPageMove.Back) }
  }
  val motion = remember(state, page, shiftPx, swap, stepPx, byTravel) {
    FluidBarTitleMotion(
      pageX = { state.pageOffsetX(state.roleOf(page)) + state.headingCompensation(page) },
      pageScale = { state.pageScale(state.roleOf(page)) },
      pageCenter = { Offset(state.width / 2f, state.height / 2f) },
      chromeX = { if (byTravel()) barTitleChrome(state, page, shiftPx).second else swap!!.step(page) * stepPx },
      chromeAlpha = { if (byTravel()) barTitleChrome(state, page, shiftPx).first else swap!!.alpha(page) },
      pageY = { state.pageOffsetY(state.roleOf(page)) },
    )
  }
  val glyphPx = with(LocalDensity.current) { spec.compactStyle.fontSize.toPx() }
  // Il riquadro del titolo, per riportarci la sagoma dello zoom (che è nelle coordinate dell'host): vedi [barTitleZoomClip].
  val box = remember { arrayOfNulls<LayoutCoordinates>(1) }
  // Il titolo grande cade sotto la riga alta 46 dp. Il filtro richiede un layer grande
  // quanto la sagoma, nelle sue coordinate, altrimenti il RenderEffect taglia il titolo.
  val zoomLayer = if (state.zoom != null && state.upper == page && state.zoomCrossfade) rememberGraphicsLayer() else null
  CompositionLocalProvider(spec.context) {
    // Il titolo segue il lato della striscia della barra anche col contesto della pagina (dal seguito di F2.5).
    CompositionLocalProvider(LocalContentColor provides fluidScrollEdgeInk(LocalContentColor.current, shade)) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .onGloballyPositioned { box[0] = it }
        // F2.3c: il titolo svanisce e risale con l'inchiostro della barra quando la barra cambia lato.
        .graphicsLayer {
          alpha = shade.fade()
          // F4A.3: la sfocatura dello scambio, quanto il titolo è agganciato (è chrome solo lì).
          val sigmaPx = if (swap == null || byTravel() || !FluidBarTitleBlur) {
            0f
          } else {
            swap.blur(page) * glyphPx * smoothStep(spec.collapseProgress.value.coerceIn(0f, 1f))
          }
          renderEffect = if (sigmaPx > 0.05f) {
            val radius = glassContentBlurRadiusPx(sigmaPx)
            BlurEffect(radius, radius, TileMode.Decal)
          } else {
            null
          }
          // Il titolo che lascia il centro al conteggio (F6B.1); a riposo non tocca niente.
          fluidTitleHandoffLayer(handoff.value, glyphPx)
        }
        .drawWithContent {
          when (state.roleOf(page)) {
            PageRole.Gone -> Unit
            // Nello zoom (F5B.3) il titolo grande della pagina sopra si vede solo dentro la sua sagoma, e se ne va con la
            // sua pagina, sulla stessa curva (corr-s4-zoom e F6B.5b).
            PageRole.Upper -> state.zoomShape()?.let { zoom ->
              val shape = barTitleZoomClip(zoom, barTitleOrigin(state, box[0]))
              val look = if (zoomLayer != null) state.zoomFaces() else null
              clipZoom(shape, inside = true) {
                if (zoomLayer == null || look == null || look.pageAlpha >= 1f) {
                  this@drawWithContent.drawContent()
                } else if (look.pageAlpha > 0f) {
                  val bounds = shape.bounds
                  val scaledDensity = density * state.pageScale(PageRole.Upper)
                  zoomLayer.record(size = IntSize(ceil(bounds.width).toInt(), ceil(bounds.height).toInt())) {
                    translate(-bounds.left, -bounds.top) { this@drawWithContent.drawContent() }
                  }
                  zoomLayer.alpha = look.pageAlpha
                  zoomLayer.renderEffect = look.pageBlurEffect(scaledDensity)
                  translate(bounds.left, bounds.top + look.riseDp * scaledDensity) { drawLayer(zoomLayer) }
                }
              }
            } ?: drawContent()
            // Il titolo grande della pagina sotto e' suo contenuto: dove la pagina sopra la copre, lo copre anche
            // lui. Col modello il titolo agganciato invece e' della barra (F4A.3): non si taglia con la pagina.
            PageRole.Lower -> if (state.kind != FluidPageMove.None &&
              (byTravel() || smoothStep(spec.collapseProgress.value.coerceIn(0f, 1f)) < 0.5f)
            ) {
              // La barra sta sopra tutte e due, quindi il taglio lo fa qui.
              val zoom = state.zoomShape()
              val (left, right) = state.upperCoverage()
              val far = size.width * 4f + size.height * 4f
              if (zoom != null) {
                // Nello zoom la pagina sopra copre un riquadro, non una striscia (F5B.3).
                clipZoom(barTitleZoomClip(zoom, barTitleOrigin(state, box[0])), inside = false) {
                  this@drawWithContent.drawContent()
                }
              } else if (left > 0.5f) {
                clipRect(left = -far, top = -far, right = left, bottom = far) {
                  this@drawWithContent.drawContent()
                }
              } else {
                clipRect(left = right, top = -far, right = far, bottom = far) {
                  this@drawWithContent.drawContent()
                }
              }
            } else if (state.kind != FluidPageMove.None) {
              drawContent()
            }
          }
        },
    ) {
      val titleContent: @Composable () -> Unit = { FluidTopBarTitle(
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
      ) }
      val subtitle = spec.subtitle
      if (subtitle == null) {
        titleContent()
      } else {
        Box(Modifier.fillMaxSize().padding(bottom = FluidToolbarDefaults.SubtitleLift)) { titleContent() }
        Box(
          Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .layout { measurable, constraints ->
              val needed = maxOf(controls.startPx, controls.endPx) + FluidMetrics.GlassSpacing.roundToPx()
              val inset = maxOf(FluidScreenDefaults.CompactTitleInset.roundToPx(), needed)
                .coerceAtMost(constraints.maxWidth / 2)
              val placeable = measurable.measure(constraints.offset(horizontal = -inset * 2))
              layout(constraints.maxWidth, placeable.height) { placeable.place(inset, 0) }
            }
            .graphicsLayer {
              alpha = smoothStep(spec.collapseProgress.value.coerceIn(0f, 1f)) * motion.chromeAlpha()
              translationX = motion.chromeX()
            },
          contentAlignment = Alignment.Center,
        ) {
          Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = FluidToolbarDefaults.SubtitleInk),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
    }
}

/** La sfocatura c'è da Android 12 (`RenderEffect`); sotto resta la dissolvenza col passo. */
private val FluidBarTitleBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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
  swap: FluidBarTitleSwap,
  declaredPage: Any?,
) {
  val sink = remember(journey, page) {
    FluidBarPieceSink(suppressed = { journey.active }, onChanged = journey::recompute)
  }
  val mixedInk = if (declaredPage == null) Modifier else mixedActionInk(sink, swap, page)
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
            if (declaredPage != null) {
              // Il vetro di prima sostiene quello dei gruppi finché la nuova lente è opaca.
              val coverage = swap.alpha(declaredPage)
              alpha = legacyMaterialAlpha(coverage)
            } else {
              alpha = when (state.roleOf(page)) {
                PageRole.Upper -> if (state.isMoving) FluidPageMotion.barIconAlpha(m, ofLower = false) else 1f
                PageRole.Lower -> if (state.isMoving) FluidPageMotion.barIconAlpha(m, ofLower = true) else 0f
                PageRole.Gone -> 0f
              }
            }
          }.then(mixedInk),
      )
    }
  }
}

/** Il materiale uscente sostiene il tondo durante l'ingresso del gruppo dichiarato. */
internal fun legacyMaterialAlpha(modelAlpha: Float): Float = 1f - modelAlpha * modelAlpha

/** La sfocatura interessa la faccia del controllo. Il bordo e l'ombra continuano a leggere il vetro vivo. */
@Composable
private fun mixedActionInk(sink: FluidBarPieceSink, swap: FluidBarTitleSwap, page: Any): Modifier {
  val layer = rememberGraphicsLayer()
  val faces = remember { Path() }
  var row by remember { mutableStateOf<LayoutCoordinates?>(null) }
  return Modifier.onGloballyPositioned { row = it }.drawWithContent {
    val radius = glassContentBlurRadiusPx(swap.blur(page) * FluidMetrics.BarIconSize.toPx())
    val origin = row
    if (!FluidBarTitleBlur || radius <= .05f || origin == null || !origin.isAttached) {
      drawContent()
      return@drawWithContent
    }
    faces.reset()
    for (piece in sink.pieces) {
      val coordinates = piece.coordinates?.takeIf { it.isAttached } ?: continue
      val bounds = origin.localBoundingBoxOf(coordinates, clipBounds = false)
      faces.addOval(mixedActionFace(bounds, FluidMetrics.BarIconSize.toPx()))
    }
    layer.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
    layer.record { this@drawWithContent.drawContent() }
    if (faces.isEmpty) drawLayer(layer) else {
      clipPath(faces, ClipOp.Difference) { this@drawWithContent.drawContent() }
      clipPath(faces) { drawLayer(layer) }
    }
  }
}

/** La mezza corona tra faccia e orlo sostiene la lente mentre l'inchiostro cambia. */
internal fun mixedActionFace(lens: Rect, iconPx: Float): Rect =
  lens.deflate((lens.minDimension - iconPx).coerceAtLeast(0f) / 4f)

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

  /** Falso quando la barra si disegna dal modello (F4A.3): i tasti li trasforma il piano, e questa non viaggia. */
  var enabled: Boolean = true

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
    if (node == null || !state.isMoving || !enabled) {
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
 *
 * [toolbar] (dalla 4.0, F4A.3) e' la barra **dichiarata**, la stessa DSL di `FluidScreen(toolbar = …)`: se c'e' vince su
 * [actions] e su [onBack] (che aggiunge l'indietro se la barra non dichiara un `back` o un `close`). Dentro un host i
 * suoi tasti restano al loro posto e si trasformano in quelli della pagina d'arrivo; fuori i gruppi stanno sul piano
 * flottante come in `FluidScreen`.
 */
@Composable
fun FluidPageChrome(
  title: String,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  backdrop: GlassBackdropState? = null,
  barTint: GlassTint? = null,
  toolbar: (FluidToolbarScope.() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  // Il colore del titolo e dei tasti PRIMA di registrarsi (3.0.1), come fa `FluidScreen`: l'host disegna
  // la barra nel contesto che la pagina gli consegna (`currentCompositionLocalContext`), e una pagina che
  // non e' una `FluidScreen` non ha mai detto il colore del testo: fuori da una `Surface` vale nero, e il
  // titolo agganciato era nero sul tema scuro. Il fondo sotto la barra e' quello della pagina.
  // F6B.1: in selezione la barra non aggiunge l'indietro della pagina, e il back esce dalla selezione.
  val declaration = toolbar?.let { buildFluidScreenToolbar(onBack, it) }
  val selection = declaration?.selection
  if (selection != null && !LocalFluidPagePreparing.current) {
    FluidSelectionBackEffect(enabled = true, onExit = selection.exit)
  }
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    FluidPageChromeContent(title, modifier, onBack, backdrop, barTint, declaration, actions)
  }
}

@Composable
private fun FluidPageChromeContent(
  title: String,
  modifier: Modifier,
  onBack: (() -> Unit)?,
  backdrop: GlassBackdropState?,
  barTint: GlassTint?,
  toolbar: FluidToolbarDeclaration?,
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
        toolbar = toolbar,
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
        val showsTitle = toolbar == null || (toolbar.titleMode != FluidToolbarTitleMode.None && !toolbar.hasPrincipal)
        if (showsTitle) {
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
        }
        if (toolbar != null) {
          // La barra dichiarata (F4A.3): i gruppi sul piano flottante, gli inset del titolo dalla misura d'arrivo.
          FluidToolbarBar(
            declaration = toolbar,
            bar = FluidToolbarBar.TopOnly,
            title = title.takeIf { showsTitle },
            onExtent = { extent ->
              controls.startPx = extent.startPx
              controls.endPx = extent.endPx
            },
          )
          return@FluidTopBarRow
        }
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

// ================================================================================================================
// La barra disegnata dal modello (F4A.3)
// ================================================================================================================

/**
 * Gli interruttori delle prove della barra dell'host (la galleria, le sessioni sul tablet): **non sono API per le app**,
 * come `FluidGlassContainerDiagnostics.fusionPreview`. Spenti di serie.
 */
object FluidPageBarDiagnostics {
  /**
   * La variante del gesto indietro che **segue il dito** (R7 B.7, strada B), da mostrare ad Alessio accanto a quella di
   * serie (D1: i tasti aspettano la conferma). Accesa, mentre il dito tiene una pagina i vetri della barra dichiarata si
   * trasformano col progresso del gesto (una superficie di Fluid-physics fra i vetri delle due pagine, accoppiati per
   * identità) e le icone si incrociano in dissolvenza; il rilascio finisce da dove è arrivato, l'annullo torna indietro.
   * Solo fra pagine con la barra dichiarata e con al più otto vetri; altrimenti vale quella di serie.
   */
  var gestureFollowsFinger: Boolean by mutableStateOf(false)
}

/**
 * Gli inset del titolo agganciato della barra dell'host: con le `actions` di sempre la misura della riga (com'era), col
 * modello la misura d'**arrivo** dei gruppi (F4A.3: il titolo segue il bersaglio, non il vetro in volo) e, fra una pagina
 * dichiarata e una di prima, il più largo dei due lati.
 */
internal class FluidBarInsets(private val controls: FluidTopBarControls) {
  val trailingWidthPx: Int get() = controls.endPx
  var model = false
  var legacyRows = false
  private var modelStart = 0
  private var modelEnd = 0
  private var rowStart = 0
  private var rowEnd = 0

  fun modelExtent(extent: FluidToolbarExtent) {
    modelStart = extent.startPx
    modelEnd = extent.endPx
    push()
  }

  fun rowStart(px: Int) {
    rowStart = px
    push()
  }

  fun rowEnd(px: Int) {
    rowEnd = px
    push()
  }

  fun push() {
    if (model) {
      controls.startPx = modelStart
      controls.endPx = if (legacyRows) maxOf(modelEnd, rowEnd) else modelEnd
    } else {
      controls.startPx = rowStart
      controls.endPx = rowEnd
    }
  }
}

/**
 * Lo scambio del titolo agganciato fra due pagine (F4A.3, R7 26): **sfocatura uniforme** come le icone (A1, sul testo
 * per A9), un passo di al più 8 dp nel verso della navigazione, **sul tempo suo** (quello del contenuto,
 * [GlassContentTiming]: 300 ms) e non sul progresso della pagina. Parte quando cambia la pagina della barra
 * ([fluidPageBarPage]: al push, al pop col tasto, al rilascio che conferma il gesto, al cambio di scheda senza passo) e,
 * se arriva un altro cambio a metà, riparte dall'aspetto che ha ([GlassContentJourney.retarget], invariante 16).
 *
 * Il viaggio ha per chiavi le pagine: ogni titolo legge la sua parte ([alpha], [blur], [step]) in disegno.
 */
@Stable
internal class FluidBarTitleSwap(
  /** Sotto Android 12 non c'è sfocatura: lo scambio usa le curve senza sovrapposizione di [GlassContentJourney]. */
  private val blurAvailable: Boolean = FluidBarTitleBlur,
) {
  private var journey by mutableStateOf(GlassContentJourney.rest(null, blurAvailable = blurAvailable))

  /** Il tempo del viaggio, in ms: avanza solo mentre qualcosa si muove (vedi [FluidBarTitleClock]). */
  private var now by mutableDoubleStateOf(0.0)

  /** Cambia a ogni scambio che parte: l'orologio riparte con lui. */
  var generation by mutableIntStateOf(0)
    private set

  private var started = false
  private var target: Any? = null

  /** Per ogni strato: il segno del lato da cui è arrivato, il segno del passo d'uscita, il passo che aveva all'uscita. */
  private val enterSign = HashMap<Long, Float>()
  private val exitSign = HashMap<Long, Float>()
  private val exitFrom = HashMap<Long, Float>()

  val finished: Boolean get() = journey.frame(now).finished

  /**
   * La pagina della barra è [page]. [step] è il segno del lato da cui arriva il titolo nuovo (0: nessun passo).
   * [snap]: niente scambio, il titolo nuovo c'è subito (movimento ridotto, la variante che segue il dito).
   */
  fun retarget(page: Any?, step: Int, snap: Boolean) {
    if (started && page == target) return
    target = page
    if (!started || snap) {
      started = true
      journey = GlassContentJourney.rest(page, blurAvailable = blurAvailable)
      enterSign.clear()
      exitSign.clear()
      exitFrom.clear()
      generation++
      return
    }
    val current = journey.layers.lastOrNull { it.role != GlassContentRole.Exit }
    val currentStep = current?.let { stepOf(it) } ?: 0f
    journey = journey.retarget(now, page)
    if (current != null) {
      exitSign[current.id] = step.toFloat()
      exitFrom[current.id] = currentStep
    }
    val arriving = journey.layers.lastOrNull { it.role != GlassContentRole.Exit }
    if (arriving != null && arriving.key == page) enterSign[arriving.id] = step.toFloat()
    generation++
  }

  /** L'orologio avanza di [ms]. */
  fun advance(ms: Double) {
    now += ms
  }

  /** Lo scambio è finito: resta il titolo d'arrivo, fermo. */
  fun settle() {
    journey = journey.settle()
    enterSign.clear()
    exitSign.clear()
    exitFrom.clear()
  }

  private fun layerOf(page: Any): GlassContentLayer? = journey.layers.lastOrNull { it.key == page }

  /** L'opacità del titolo agganciato di [page]: 1 per la pagina della barra a riposo, 0 per chi non c'è. */
  fun alpha(page: Any): Float {
    val layer = layerOf(page) ?: return 0f
    return journey.look(layer, now).alpha
  }

  /** La σ del titolo di [page] in frazione dell'altezza del carattere. */
  fun blur(page: Any): Float {
    val layer = layerOf(page) ?: return 0f
    return journey.look(layer, now).blur
  }

  /** Il passo del titolo di [page] in frazioni di [FluidPageMotion.BarTitleStepDp], col segno. */
  fun step(page: Any): Float = layerOf(page)?.let(::stepOf) ?: 0f

  private fun stepOf(layer: GlassContentLayer): Float {
    val elapsed = ((now - layer.startMs) / journey.timing.durationMs).toFloat().coerceAtLeast(0f)
    return when (layer.role) {
      GlassContentRole.Rest -> 0f
      GlassContentRole.Exit -> {
        val sign = exitSign[layer.id] ?: 0f
        if (sign == 0f) exitFrom[layer.id] ?: 0f
        else sign * FluidPageMotion.barTitleExitStep(elapsed, (exitFrom[layer.id] ?: 0f) * sign)
      }
      GlassContentRole.Enter, GlassContentRole.Born ->
        (enterSign[layer.id] ?: 0f) * FluidPageMotion.barTitleEnterStep(layer.fromU + elapsed)
    }
  }
}

/** L'orologio di [swap]: conta i fotogrammi solo mentre uno scambio è in corso, alla scala delle animazioni. */
@Composable
private fun FluidBarTitleClock(swap: FluidBarTitleSwap, durationScale: Float) {
  val generation = swap.generation
  LaunchedEffect(swap, generation, durationScale) {
    if (durationScale <= 0f) {
      swap.settle()
      return@LaunchedEffect
    }
    var last = -1L
    while (!swap.finished) {
      val nanos = withFrameNanos { it }
      if (last >= 0L) swap.advance((nanos - last) / 1_000_000.0 / durationScale)
      last = nanos
    }
    swap.settle()
  }
}

/**
 * La barra dell'host disegnata dal modello (F4A.3): la barra dichiarata di [FluidToolbarBar], **una sola**, nel contesto
 * della pagina che le dà i tasti ([context]: tema, ViewModel della voce, host dei modali) e sul vetro della barra. Il suo
 * contenitore e la sua memoria vivono quanto l'host: cambiare pagina è cambiare il suo stato, e il piano lo trasforma.
 *
 * [hidden] la nasconde mentre la variante che segue il dito porta il vetro; [snap] le toglie il viaggio (il cambio che
 * arriva mentre la variante la tiene nascosta va al posto nuovo nel fotogramma dopo). [minimize] è il ritiro dell'host
 * ([FluidPageBarMinimize]), o null se nessuna pagina si ritira.
 */
@Composable
private fun HostModelBar(
  declaration: FluidToolbarDeclaration,
  title: String?,
  context: CompositionLocalContext,
  controlBackdrop: GlassBackdropState,
  shade: FluidScrollEdgeShade,
  insets: FluidBarInsets,
  hidden: () -> Boolean,
  snap: Boolean,
  minimize: FluidToolbarMinimizeProgress?,
  trailingOpacity: (() -> Float)?,
) {
  HostBarPageContext(context) {
    val policy = LocalFluidMotionPolicy.current
    CompositionLocalProvider(
      LocalGlassBackdrop provides controlBackdrop,
      // L'inchiostro della striscia della barra, dopo il contesto della pagina (come il titolo).
      LocalContentColor provides fluidScrollEdgeInk(LocalContentColor.current, shade),
      LocalFluidScrollEdgeShade provides shade,
      LocalGlassSharedHost provides shade.shared,
      LocalFluidCanvasBackdrop provides null,
      // I tasti della barra restano a qualità piena mentre le pagine si muovono (vedi `BarActions`).
      LocalFluidGlassQuality provides null,
      LocalFluidMotionPolicy provides if (snap) FluidMotionPolicy(durationScale = 0f) else policy,
      // Il ritiro dell'host, non quello della pagina del contesto (B1): la barra è una per host, e quando la sua pagina
      // cambia fra una ritirata e una aperta i tasti seguono la corsa delle pagine invece di comparire in un fotogramma.
      LocalFluidToolbarMinimize provides minimize,
    ) {
      val paint = remember { Paint() }
      FluidToolbarBar(
        declaration = declaration,
        modifier = Modifier.graphicsLayer { alpha = if (hidden()) 0f else 1f }.drawWithContent {
          val amount = trailingOpacity?.invoke() ?: 1f
          if (amount >= 1f) {
            drawContent()
          } else {
            // L'indietro resta sul piano. Sul lato delle azioni il modello entra sopra il vetro
            // dello slot libero con lo stesso orologio dell'inchiostro, conservando i suoi hit test.
            val rtl = layoutDirection == LayoutDirection.Rtl
            val width = insets.trailingWidthPx.toFloat().coerceAtMost(size.width)
            val trailing = Rect(if (rtl) -size.height else size.width - width, -size.height,
              if (rtl) width else size.width + size.height, size.height * 2f)
            clipRect(trailing.left, trailing.top, trailing.right, trailing.bottom, ClipOp.Difference) {
              this@drawWithContent.drawContent()
            }
            if (amount > 0f) clipRect(trailing.left, trailing.top, trailing.right, trailing.bottom) {
              paint.alpha = amount
              drawContext.canvas.saveLayer(trailing, paint)
              this@drawWithContent.drawContent()
              drawContext.canvas.restore()
            }
          }
        },
        title = title,
        onExtent = insets::modelExtent,
      )
    }
  }
}

/**
 * La variante del gesto indietro che segue il dito (interruttore [FluidPageBarDiagnostics.gestureFollowsFinger]): mentre
 * il dito tiene la pagina una superficie di Fluid-physics porta i vetri della barra della pagina in cima su quelli della
 * pagina sotto col progresso del gesto ([FluidPageMotion.barMorph]), e le icone delle due si incrociano in dissolvenza
 * ([FluidPageMotion.barIconAlpha]); intanto la barra del modello è nascosta. È la barra di prima, con i vetri accoppiati
 * per identità invece che per lato.
 */
@Stable
internal class FluidBarFingerMorph(private val state: FluidPageHostState) {
  private var ready by mutableStateOf(false)

  /** La pagina della barra è già quella sotto (dopo la conferma), e la barra del modello ha avuto il tempo di arrivarci. */
  var barIsLower by mutableStateOf(false)
  internal var physics: FluidPhysicsState? = null
  internal var node: LayoutCoordinates? = null
  internal var from: Map<Any, Rect> = emptyMap()
  internal var to: Map<Any, Rect> = emptyMap()
  internal var blendPx: Float = 0f

  fun m(): Float = FluidPageMotion.barMorph(state.frontFraction())

  /** Se la superficie porta il vetro adesso (letto in disegno): il dito guida, la forma è partita e non è ancora arrivata. */
  fun overlay(): Boolean {
    if (!ready || state.kind != FluidPageMove.Back) return false
    val m = m()
    return m > 0f && !(m >= 1f && barIsLower)
  }

  fun recompute() {
    val node = node?.takeIf { it.isAttached }
    val physics = physics
    val pairs = if (node != null && physics != null) FluidPageMotion.barModelPieceJourney(from, to) else null
    if (pairs == null || physics == null) {
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

  fun release() {
    ready = false
  }

  private fun formOf(rects: List<Rect>): FluidForm {
    val slabs = rects.map { FluidForm.Slab(it, FluidCornerRadii.all(it.minDimension / 2f)) }
    return slabs.singleOrNull() ?: FluidForm.Group(slabs, blendRadius = blendPx)
  }
}

/** I riquadri dei vetri di [resolved] nel nodo della superficie (in px, allargato di [growPx] in alto), per identità. */
private fun fingerRects(
  resolved: FluidToolbarResolved,
  ids: Map<FluidToolbarGroupId, FluidToolbarGlassId>,
  density: Float,
  growPx: Int,
  rtl: Boolean,
): Map<Any, Rect> {
  val height = FluidMetrics.GlassButtonLens.value * density
  val out = LinkedHashMap<Any, Rect>()
  for (group in resolved.groups) {
    if (!group.sharedBackground) continue
    val left = if (rtl) resolved.widthDp - group.leftDp - group.widthDp else group.leftDp
    val id = ids[group.id] ?: continue
    out[id] = Rect(left * density, growPx.toFloat(), (left + group.widthDp) * density, growPx + height)
  }
  return out
}

@Composable
private fun BoxScope.BarFingerMorph(
  finger: FluidBarFingerMorph,
  upper: FluidPageBarSpec,
  lower: FluidPageBarSpec,
  barIsLower: Boolean,
  controlBackdrop: GlassBackdropState,
  growPx: Int,
  shade: FluidScrollEdgeShade,
) {
  val density = LocalDensity.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val physics = rememberFluidPhysicsState(FluidForm.Slab(Rect(0f, 0f, 1f, 1f)))
  val fromDeclaration = fluidPageBarDeclaration(upper)
  val toDeclaration = fluidPageBarDeclaration(lower)
  val fromModel = fluidToolbarModelOf(fromDeclaration, upper.title)
  val toModel = fluidToolbarModelOf(toDeclaration, lower.title)
  // La conferma arriva alla barra del modello un fotogramma dopo, e il suo contenitore la posa al fotogramma dopo
  // ancora: la superficie lascia il vetro solo quando la barra è davvero arrivata.
  LaunchedEffect(finger, barIsLower) {
    if (barIsLower) repeat(2) { withFrameNanos { } }
    finger.barIsLower = barIsLower
  }
  DisposableEffect(finger, physics) {
    finger.physics = physics
    onDispose {
      finger.release()
      if (finger.physics === physics) finger.physics = null
      finger.barIsLower = false
    }
  }
  BoxWithConstraints(Modifier.matchParentSize()) {
    val width = maxWidth.value
    val metrics = fluidToolbarMetrics()
    val fromResolved = remember(fromModel, width) { resolveFluidToolbar(fromModel, width, metrics = metrics) }
    val toResolved = remember(toModel, width) { resolveFluidToolbar(toModel, width, metrics = metrics) }
    val fromIds = remember(fromResolved) { fluidToolbarGlassIds(fromResolved) }
    val toIds = remember(fromResolved, toResolved, fromIds) {
      fluidToolbarGlassIds(toResolved, fromIds, fromResolved, diffFluidToolbar(fromResolved, toResolved))
    }
    SideEffect {
      finger.from = fingerRects(fromResolved, fromIds, density.density, growPx, rtl)
      finger.to = fingerRects(toResolved, toIds, density.density, growPx, rtl)
      finger.blendPx = with(density) { 4.dp.toPx() }
      finger.recompute()
    }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .layout { measurable, constraints ->
          val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight + 2 * growPx))
          layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -growPx) }
        }
        .onGloballyPositioned { coordinates ->
          finger.node = coordinates
          finger.recompute()
        }
        .drawWithContent { if (finger.overlay()) drawContent() }
        .fluidPhysicsSurface(
          state = physics,
          backdrop = controlBackdrop,
          tint = GlassDefaults.controlTint(),
          role = GlassRole.Interactive,
          optics = GlassDefaults.buttonOptics(),
          intensity = { if (finger.overlay()) 1f else 0f },
        ),
    )
    CompositionLocalProvider(LocalContentColor provides fluidScrollEdgeInk(LocalContentColor.current, shade)) {
      FingerFaces(fromResolved, fromDeclaration, upper.context) {
        if (finger.overlay()) FluidPageMotion.barIconAlpha(finger.m(), ofLower = false) else 0f
      }
      FingerFaces(toResolved, toDeclaration, lower.context) {
        if (finger.overlay()) FluidPageMotion.barIconAlpha(finger.m(), ofLower = true) else 0f
      }
    }
  }
}

/** Le icone e le scritte di una barra risolta, ferme ai loro posti, senza vetro: la variante che segue il dito. */
@Composable
private fun FingerFaces(
  resolved: FluidToolbarResolved,
  declaration: FluidToolbarDeclaration,
  context: CompositionLocalContext,
  alpha: () -> Float,
) {
  val ink = LocalContentColor.current
  CompositionLocalProvider(context) {
    val scheme = MaterialTheme.colorScheme
    val textStyle = FluidToolbarDefaults.textStyle()
    Box(
      Modifier
        .fillMaxWidth()
        .height(FluidMetrics.GlassButtonLens)
        .clearAndSetSemantics { }
        .graphicsLayer { this.alpha = alpha() },
    ) {
      for (element in resolved.elements) {
        if (element.inOverflow) continue
        val item = element.item
        val entry = declaration.buttons[item.id]
        val overflow = item.role == FluidToolbarRole.Overflow
        val tint = entry?.let { fluidToolbarTint(it, scheme.primary, scheme.error) } ?: Color.Unspecified
        val color = when {
          item.selected -> scheme.primary
          tint != Color.Unspecified -> tint
          else -> ink
        }
        Box(
          Modifier
            .align(Alignment.CenterStart)
            .offset(x = element.leftDp.dp)
            .width(element.widthDp.dp)
            .height(FluidMetrics.GlassButtonLens),
          contentAlignment = Alignment.Center,
        ) {
          val icon = if (overflow) FluidIcons.MoreHoriz else entry?.icon
          when {
            entry?.custom != null -> entry.custom.invoke()
            entry?.text != null -> Text(entry.text, style = textStyle, color = color, maxLines = 1, softWrap = false)
            icon != null -> FluidIcon(icon, contentDescription = null, tint = color, size = fluidToolbarIconSize(item.role))
          }
        }
      }
    }
  }
}
