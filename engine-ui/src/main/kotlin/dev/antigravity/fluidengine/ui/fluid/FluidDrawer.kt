package dev.antigravity.fluidengine.ui.fluid

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.page.FluidPageMotion
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** Le misure del pannello laterale ([FluidDrawer]). */
object FluidDrawerDefaults {
  /**
   * La larghezza del pannello: 320 dp, la colonna di una barra laterale (`FluidSidebar`) col suo
   * contenuto. Un tetto, non una promessa: vedi [MaxWidthFraction].
   */
  val Width: Dp = 320.dp

  /**
   * Il pannello non supera l'85% della finestra: su un telefono da 360 dp ne restano almeno ~50 di
   * pagina oscurata a destra, il posto dove toccare per chiuderlo e la prova che la pagina e' ancora li'.
   */
  const val MaxWidthFraction: Float = 0.85f

  /** L'aria fra il pannello e i bordi della finestra: quella della barra laterale, cosi' le due sono la stessa colonna. */
  val Margin: Dp = FluidSidebarDefaults.Margin

  /**
   * La larghezza di finestra da cui il pannello non serve piu': da qui in su le stesse righe stanno in
   * una `FluidSidebar` fissa accanto alla pagina. E' un riferimento per chi decide il layout (l'app):
   * il pannello non lo legge.
   */
  val WideWindowWidth: Dp = 1000.dp
}

/**
 * Lo stato di un [FluidDrawer]: aperto o chiuso, e quanto e' in scena in questo momento.
 *
 * **Cosa si salva.** Solo «aperto o chiuso» ([Saver]): una rotazione con il pannello aperto lo ritrova
 * aperto, mai a meta' di un trascinamento. Le coordinate del dito e la molla in corso non sopravvivono a
 * una ricreazione, e non devono: un pannello che riappare a 0,37 sotto un dito che non c'e' piu' e' un
 * difetto, non uno stato.
 *
 * **Chi lo muove.** [open] e [close] cambiano l'obiettivo; il pannello ci arriva con la sua molla (o di
 * colpo col movimento ridotto). Il dito e il back predittivo lo tengono mentre ci sono e poi decidono.
 * [snapTo] arriva senza animazione: per una finestra che diventa larga col pannello aperto (l'app passa
 * alla barra laterale) o per uno stato iniziale.
 */
@Stable
class FluidDrawerState(initiallyOpen: Boolean = false) {

  /** Aperto o chiuso: l'obiettivo, non la posizione. Diventa falso nell'istante in cui si chiede la chiusura. */
  var isOpen: Boolean by mutableStateOf(initiallyOpen)
    private set

  internal val progressState = mutableFloatStateOf(if (initiallyOpen) 1f else 0f)

  /**
   * Quanto il pannello e' in scena adesso: 0 chiuso, 1 aperto, in mezzo mentre entra, esce o e' sotto il
   * dito. Cambia a ogni fotogramma di un movimento: va letto nel disegno o nel posizionamento, non in
   * composizione (o la composizione ripartirebbe sessanta volte al secondo).
   */
  val progress: Float get() = progressState.floatValue

  /** Vero finche' qualcosa del pannello e' in scena, uscita compresa. */
  val isOnScreen: Boolean get() = isOpen || progress > 0f

  /** Un dito o il back predittivo tengono il pannello: la molla aspetta che lo lascino. */
  internal var held: Boolean by mutableStateOf(false)

  /** La velocita' con cui parte la prossima molla, in corsa al secondo; il pannello la consuma. */
  internal var releaseVelocity: Float = 0f

  /** Cambia a ogni [snapTo], perche' il pannello abbandoni la molla in corso. */
  internal var snaps: Int by mutableIntStateOf(0)

  /** Apre il pannello. Lo apre l'app, da un tasto (☰): non c'e' un gesto dal bordo (vedi [FluidDrawer]). */
  fun open() {
    isOpen = true
  }

  /** Chiude il pannello: torna da dov'e' venuto. Lo chiamano anche il back, il tocco fuori e il dito. */
  fun close() {
    isOpen = false
  }

  /** Aperto o chiuso subito, senza movimento. */
  fun snapTo(open: Boolean) {
    isOpen = open
    held = false
    releaseVelocity = 0f
    progressState.floatValue = if (open) 1f else 0f
    snaps++
  }

  companion object {
    /** Salva solo «aperto o chiuso». */
    val Saver: Saver<FluidDrawerState, Boolean> = Saver(
      save = { it.isOpen },
      restore = { FluidDrawerState(initiallyOpen = it) },
    )
  }
}

/** Uno stato del pannello che sopravvive alla rotazione (solo aperto o chiuso). */
@Composable
fun rememberFluidDrawerState(initiallyOpen: Boolean = false): FluidDrawerState =
  rememberSaveable(saver = FluidDrawerState.Saver) { FluidDrawerState(initiallyOpen) }

/**
 * Nasconde all'accessibilita' quello che sta sotto il pannello mentre e' aperto.
 *
 * Come [fluidGlassModalObscured] per i modali: un pannello che lascia camminare TalkBack nella pagina che
 * si intravede dietro e' peggio di uno opaco, perche' niente avvisa che quello su cui si e' arrivati non
 * si puo' toccare. Va sul contenitore della pagina e della barra, sotto al [FluidDrawer]; letto in
 * composizione (cambia due volte per apertura).
 */
fun Modifier.fluidDrawerObscured(state: FluidDrawerState): Modifier =
  if (state.isOpen) this.semantics { hideFromAccessibility() } else this

/**
 * La larghezza del pannello: quella chiesta, mai oltre [FluidDrawerDefaults.MaxWidthFraction] della
 * finestra, mai negativa. Pura: e' la parte che decide quanto resta della pagina su un telefono stretto.
 */
internal fun fluidDrawerWidth(requested: Dp, available: Dp): Dp =
  minOf(requested, available * FluidDrawerDefaults.MaxWidthFraction).coerceAtLeast(0.dp)

/**
 * Se un rilascio chiude il pannello: la regola del back delle pagine, tale e quale
 * ([FluidPageMotion.backRelease]). Oltre il 35% della corsa (con il tetto di 180 dp) o con una spinta di
 * 400 dp/s verso la chiusura; una spinta contraria della stessa forza lo tiene aperto anche oltre la
 * distanza. Lo stesso gesto, con lo stesso dito, deve decidere allo stesso punto.
 *
 * @param closedPx di quanto il pannello e' rientrato, in pixel (0 aperto).
 * @param velocityPx la velocita' del dito verso la chiusura, in pixel al secondo (negativa se riapre).
 * @param travelPx la corsa intera del pannello (la sua larghezza con i margini).
 */
internal fun fluidDrawerReleaseCloses(closedPx: Float, velocityPx: Float, travelPx: Float, density: Float): Boolean =
  FluidPageMotion.backRelease(travel = closedPx, velocity = velocityPx, width = travelPx, density = density)

/**
 * Il pannello laterale modale: una lastra di vetro che entra dal bordo d'inizio (da sinistra; da destra
 * in RTL) sopra la pagina, per le cartelle, le sezioni, gli account di un'app su una finestra stretta.
 *
 * **Perche' esiste.** Da 1000 dp le stesse righe stanno in una `FluidSidebar` fissa accanto alla pagina;
 * sotto non c'e' posto, e ogni app se lo scriveva: con il `ModalNavigationDrawer` di Material (un foglio
 * opaco, la sua molla, il back che chiudeva la pagina sotto) o con un pannello a mano che non sapeva del
 * back predittivo, del fuoco, di TalkBack. Qui il pannello e' dell'engine, con lo stesso contenuto della
 * barra laterale ([FluidSidebarRow], [FluidSidebarSection]) e le regole dei pannelli flottanti.
 *
 * **Si apre solo dall'app.** Un tasto (☰) chiama [FluidDrawerState.open]. **Nessun gesto dal bordo**: con la
 * navigazione a gesti il bordo e' il back di Android (che in radice esce dall'app) e dentro l'app e' il
 * back dell'engine (`FluidBackSwipe.EdgeOnly`); un pannello che lo contendesse renderebbe imprevedibili
 * tutti e due. **Si chiude** col back (di sistema, a tasti, predittivo), toccando la pagina oscurata,
 * trascinandolo verso il bordo da cui e' venuto e con l'azione «chiudi» di TalkBack. Rientra sempre da dove
 * e' entrato: la chiusura inverte l'apertura.
 *
 * **Il dito.** Un trascinamento verso il bordo d'inizio, partito sul pannello o sulla pagina oscurata,
 * prende il pannello e lo tiene **sotto il dito, 1:1** (la corsa e' dito meno punto di contatto, come il
 * back delle pagine); al rilascio decide la regola del back ([FluidPageMotion.backRelease]: 35% della
 * corsa col tetto, o 400 dp/s, e una spinta contraria vince) e la molla parte dalla velocita' del dito.
 * Un trascinamento verso l'esterno non lo prende (il pannello e' gia' aperto); uno verticale e' della
 * lista; uno che parte su un tasto e' del tasto (i tasti tengono il dito). Un dito che lo prende mentre
 * entra lo ferma dov'e'.
 *
 * **Il back.** Il gestore entra in composizione **all'apertura**, non prima (la regola della 3.0.6 per i
 * menu): sul dispatcher risponde il gestore abilitato registrato per ultimo, quindi un pannello aperto sopra
 * una pagina annidata o sopra una selezione attiva si chiude **prima** di loro, e un menu aperto dentro il
 * pannello (contestuale di una riga) si chiude prima del pannello: menu, pannello, selezione, pagina. Il
 * back predittivo lo fa rientrare in proporzione al gesto; annullato, torna aperto.
 *
 * **Dove si compone.** Alla radice, **dopo** la pagina e la barra di navigazione (deve coprirle) e
 * **prima** degli host dei modali, del menu che si trasforma e delle notifiche (un menu aperto da una riga
 * del pannello sta sopra il pannello). Composto sempre: chiuso non disegna niente e non prende tocchi. Il
 * contenitore della pagina porta [fluidDrawerObscured].
 *
 * **Il materiale.** Una lastra flottante (`GlassDefaults.panelOptics(overSharpPage = true)`,
 * `panelTint()`), come la notifica: rifrange la pagina cruda e non il composito dello scrim — gli exports di
 * uno scrim sono registrati a forza piena, e una lastra che li campionasse resterebbe scura mentre lo scrim
 * vero sfuma col pannello. La pagina dietro si oscura con lo scrim dei modali, in proporzione a quanto il
 * pannello e' in scena: **nessuna pagina sfuma** (la regola delle pagine), si vela. Il pannello entra per
 * spostamento di layout, mai scalato. Senza fondale (`backdrop` null) e' una lastra opaca
 * (`surfaceContainerHigh`) su un velo quasi pieno, come i modali nei test.
 *
 * **Movimento.** La molla e' [FluidMotion.DrawerTravel] (quella delle pagine, criticamente smorzata: non
 * rimbalza). Col movimento ridotto il pannello c'e' o non c'e', in un fotogramma; il dito lo segue lo stesso.
 * Nessuna vibrazione: il momento e' il tocco sul ☰, che ha il suo colpo.
 *
 * **Accessibilita'.** All'apertura il fuoco entra nel pannello (un bersaglio invisibile, come nei modali) e
 * TalkBack annuncia [paneTitle]; il pannello e' un gruppo di attraversamento e di fuoco, ha l'azione «chiudi»
 * ([FluidLabels.closeDrawer]) e chiude con Esc da tastiera. Le righe da toccare col dito vanno a 48 dp:
 * `FluidSidebarRow(minHeight = FluidSidebarDefaults.ComfortableRowHeight)` (la barra laterale resta a 44).
 * Il contenuto scorre (e' una `LazyColumn`), quindi il testo al 200% allunga la lista e non taglia niente.
 *
 * **Dove si rompe.** Composto solo mentre e' aperto (`if (open) FluidDrawer(…)`) non ha un'uscita: va
 * composto sempre. Un'app che lo toglie dalla composizione col pannello aperto (la finestra diventa larga
 * e passa alla barra laterale) lo trova chiuso, senza animazione: niente scrim ne' gestori rimasti in giro.
 * Se il contenuto ha un campo di testo, la tastiera si chiude all'apertura.
 *
 * @param state lo stato: [rememberFluidDrawerState].
 * @param backdrop la pagina da rifrangere (il fondale della chrome, come per gli host dei modali); null
 *   per una lastra opaca.
 * @param width la larghezza del pannello, al piu' [FluidDrawerDefaults.MaxWidthFraction] della finestra.
 * @param paneTitle il nome del pannello per TalkBack («Cartelle»); di serie [FluidLabels.drawer].
 * @param listState lo scorrimento della lista: resta fra un'apertura e l'altra finche' il pannello e'
 *   composto, e l'app puo' portare in vista la voce corrente.
 * @param returnFocusTo dove riportare il fuoco quando il pannello si chiude, se lo si stava usando da
 *   tastiera: il ☰ che l'ha aperto. Col dito non succede niente (il fuoco non si mostra al tocco).
 * @param header in testa, ferma: l'account, un titolo.
 * @param footer in fondo, ferma, sotto una riga sottile: «Impostazioni».
 * @param content le righe: [FluidSidebarSection], [FluidSidebarRow] (anche annidate, con `level`).
 */
@Composable
fun FluidDrawer(
  state: FluidDrawerState,
  backdrop: GlassBackdropState?,
  modifier: Modifier = Modifier,
  width: Dp = FluidDrawerDefaults.Width,
  paneTitle: String? = null,
  listState: LazyListState = rememberLazyListState(),
  returnFocusTo: FocusRequester? = null,
  header: (@Composable ColumnScope.() -> Unit)? = null,
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  content: LazyListScope.() -> Unit,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val labels = LocalFluidLabels.current
  val keyboard = LocalSoftwareKeyboardController.current
  val inputModes = LocalInputModeManager.current
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

  // La molla: dall'obiettivo che cambia, dal dito che lascia, da uno scatto. Rilanciata a ogni cambio, parte
  // dal punto in cui si trova (una chiusura a meta' apertura torna indietro da li', con la sua velocita').
  LaunchedEffect(state, state.isOpen, state.held, state.snaps, reducedMotion) {
    if (state.held) return@LaunchedEffect
    val target = if (state.isOpen) 1f else 0f
    val velocity = state.releaseVelocity
    state.releaseVelocity = 0f
    if (reducedMotion) {
      state.progressState.floatValue = target
      return@LaunchedEffect
    }
    if (state.progress == target) return@LaunchedEffect
    animate(
      initialValue = state.progress,
      targetValue = target,
      initialVelocity = velocity,
      animationSpec = FluidMotion.DrawerTravel,
    ) { value, _ ->
      // Un dito che prende il pannello a meta' molla lo tiene da subito: la molla, che il cambio di
      // `held` cancella al fotogramma dopo, non scrive piu' sopra di lui.
      if (!state.held) state.progressState.floatValue = value
    }
  }

  // All'apertura: la tastiera si chiude (il pannello copre il campo che l'aveva aperta). Alla chiusura, da
  // tastiera, il fuoco torna a chi l'ha aperto.
  var wasOpen by remember(state) { mutableStateOf(state.isOpen) }
  LaunchedEffect(state, state.isOpen) {
    if (state.isOpen) {
      keyboard?.hide()
    } else if (wasOpen && returnFocusTo != null && inputModes.inputMode == InputMode.Keyboard) {
      runCatching { returnFocusTo.requestFocus() }
    }
    wasOpen = state.isOpen
  }

  // Tolto dalla composizione col pannello aperto (la finestra e' diventata larga): chiuso, senza uscita.
  DisposableEffect(state) {
    onDispose { if (state.isOnScreen) state.snapTo(false) }
  }

  val onScreen by remember(state) { derivedStateOf { state.isOnScreen } }
  if (!onScreen) return

  // Il back, solo da aperto: entra nella pila del dispatcher adesso, sopra tutto quello che c'era.
  if (state.isOpen) {
    PredictiveBackHandler { events ->
      var started = false
      var from = 1f
      try {
        events.collect { event ->
          if (!started) {
            started = true
            state.held = true
            from = state.progress
          }
          state.progressState.floatValue = (from * (1f - event.progress)).coerceIn(0f, 1f)
        }
        state.held = false
        state.close()
      } catch (e: CancellationException) {
        // Annullato: torna aperto da dov'e'.
        state.held = false
        throw e
      }
    }
  }

  val focusTarget = remember { FocusRequester() }
  LaunchedEffect(state, state.isOpen) {
    if (state.isOpen) runCatching { focusTarget.requestFocus() }
  }
  val geometry = remember { FluidDrawerGeometry() }
  val scrimGlass = rememberGlassBackdrop()

  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .semantics { isTraversalGroup = true }
        .focusGroup()
        .onPreviewKeyEvent { event ->
          if (event.key == Key.Escape && event.type == KeyEventType.KeyUp && state.isOpen) {
            state.close()
            true
          } else {
            false
          }
        }
        .then(
          if (state.isOpen) {
            Modifier.fluidDrawerDrag(state = state, geometry = geometry, isLtr = isLtr)
          } else {
            // In uscita la pagina e' gia' sua: i tocchi passano.
            Modifier
          },
        ),
    ) {
      FluidGlassModalScrim(
        backdrop = backdrop,
        exports = scrimGlass,
        intensity = { state.progress },
        interactive = state.isOpen,
        onDismiss = { state.close() },
      )

      // Il bersaglio invisibile del fuoco: senza, il primo movimento da tastiera finirebbe nella pagina.
      Box(
        modifier = Modifier
          .size(1.dp)
          .focusRequester(focusTarget)
          .focusable(),
      )

      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val panelWidth = fluidDrawerWidth(width, maxWidth)
        val shape = ContinuousCornerShape(FluidRadius.Group)
        val title = paneTitle ?: labels.drawer
        Column(
          modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            // La corsa: il pannello intero (margini compresi) esce dal bordo d'inizio. Letta nel
            // posizionamento: un fotogramma di movimento non ricompone niente. Prima del vetro, che
            // cosi' campiona la pagina dove sta davvero.
            .layout { measurable, constraints ->
              val placeable = measurable.measure(constraints)
              geometry.travelPx = placeable.width.toFloat()
              layout(placeable.width, placeable.height) {
                val hidden = 1f - state.progress.coerceIn(0f, 1f)
                placeable.placeRelative(x = -(hidden * placeable.width).roundToInt(), y = 0)
              }
            }
            .windowInsetsPadding(
              WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
            )
            .padding(start = FluidDrawerDefaults.Margin, top = FluidDrawerDefaults.Margin, bottom = FluidDrawerDefaults.Margin)
            .width(panelWidth)
            .semantics {
              this.paneTitle = title
              isTraversalGroup = true
              dismiss(label = labels.closeDrawer) {
                state.close()
                true
              }
            }
            .then(
              if (backdrop != null) {
                Modifier.glassSurface(
                  state = backdrop,
                  tint = GlassDefaults.panelTint(),
                  shape = shape,
                  role = GlassRole.Modal,
                  optics = GlassDefaults.panelOptics(overSharpPage = true),
                )
              } else {
                Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
              },
            )
            .clip(shape),
        ) {
          if (header != null) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
              content = header,
            )
          }
          LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
          )
          if (footer != null) {
            FluidHairline()
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp), content = footer)
          }
        }
      }
    }
  }
}

/** Quanto e' lunga la corsa del pannello, scritta dal posizionamento e letta dal dito. Non e' stato di Compose: nessuno ridisegna per lei. */
private class FluidDrawerGeometry {
  var travelPx: Float = 0f
}

/**
 * Il dito che chiude il pannello: verso il bordo d'inizio, da qualunque punto del pannello o della pagina
 * oscurata, 1:1 dal punto di contatto.
 *
 * Decide **dopo** i figli (passaggio Main): una lista che scorre in verticale, una riga col suo menu, un
 * tasto che tiene il dito arrivano prima e, se si prendono il gesto, il pannello non lo vede. Un primo
 * movimento verso l'esterno non lo prende: il pannello e' gia' tutto fuori.
 */
private fun Modifier.fluidDrawerDrag(
  state: FluidDrawerState,
  geometry: FluidDrawerGeometry,
  isLtr: Boolean,
): Modifier = pointerInput(state, isLtr) {
  // Verso la chiusura: a sinistra in LTR, a destra in RTL.
  val closing = if (isLtr) -1f else 1f
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (!state.isOpen) return@awaitEachGesture
    val tracker = VelocityTracker()
    tracker.addPointerInputChange(down)
    val grabbed = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
      // Solo verso il bordo: un movimento verso l'esterno resta di chi vuole prenderlo.
      if (overSlop * closing > 0f) change.consume()
    } ?: return@awaitEachGesture
    val travel = geometry.travelPx
    if (travel <= 0f) return@awaitEachGesture

    state.held = true
    val from = state.progress
    fun follow(x: Float) {
      val closed = (x - down.position.x) * closing
      state.progressState.floatValue = (from - closed / travel).coerceIn(0f, 1f)
    }
    tracker.addPointerInputChange(grabbed)
    follow(grabbed.position.x)
    val finished = horizontalDrag(grabbed.id) { change ->
      tracker.addPointerInputChange(change)
      follow(change.position.x)
      change.consume()
    }
    // Rilasciato (o perso: un dito che sparisce decide con la distanza, senza spinta).
    val velocity = if (finished) tracker.calculateVelocity().x * closing else 0f
    val closedPx = (1f - state.progress) * travel
    val closes = fluidDrawerReleaseCloses(closedPx, velocity, travel, density)
    // La molla parte dalla velocita' del dito, in corsa al secondo (la corsa scende chiudendo).
    state.releaseVelocity = -velocity / travel
    state.held = false
    if (closes) state.close()
  }
}
