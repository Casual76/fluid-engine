package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration

/** Presentazione di un popover richiesto su una finestra compatta. */
enum class FluidCompactAdaptation { Sheet, Popover }

/** Un compito modale oscura e blocca la pagina. Un compito parallelo lascia la pagina attiva. */
enum class FluidPresentationMode { Modal, Parallel }

// Il lato di chi dichiara un modale: le presentazioni, `FluidContextAction`, il portale (due overload) e
// `fluidExpandOrigin`. Non disegna niente: scrive nella voce dell'host (`FluidGlassModalHost.kt`).
// Spezzato da `FluidGlassModal.kt` in F5A.0, senza cambiare un comportamento.

/** How a modal arranges itself once it has finished arriving. */
enum class FluidGlassModalPresentation {
  /**
   * A pop-up that opens **on the thing you touched**, sized to whatever it contains.
   *
   * The default, and the one this whole file exists for. It is not a dialog that happens to have a
   * source rectangle: it is placed against that rectangle, it grows out of the corner nearest it,
   * and it takes the width and height its content asks for rather than a fixed card size. A modal
   * that appears in the middle of the screen has already lost the connection to the tap that opened
   * it, and nothing done to it afterwards gets that back.
   */
  Popover,

  /** Anchored to the bottom edge, full width. For something tall enough to want the whole height. */
  Sheet,

  /** A list of actions anchored to the thing that was long-pressed. Placed by [fluidContextMenu]. */
  ContextMenu,

  /**
   * The pop-up **replaces** what opened it, in place.
   *
   * Where [Popover] appears *beside* the anchor, this one starts at exactly the anchor's rectangle
   * and grows out of it while the anchor itself steps aside — so what the eye follows is one object
   * changing size, not a second object arriving next to the first. It is the shape of a button that
   * unfolds into its own menu and folds back once something is chosen.
   *
   * Presenta le **azioni** quando ce ne sono — è il caso per cui esiste, il tasto che diventa il
   * proprio menù — e altrimenti il `content` del portale. Nota che un [Popover] con un'origine
   * fa già viaggiare la sagoma dall'ancora al pannello con la rifrazione addosso: se quello che
   * serve è un pannello di contenuto che nasce da una riga, quello basta, e questa presentazione
   * aggiunge solo la forma a capsula e la larghezza del menù.
   */
  Expand,

  /**
   * Una pagina intera, per un compito che ha piu' di tre campi.
   *
   * Nasce dal basso come [Sheet] ma prende tutta l'altezza, e porta con se' quello che un foglio
   * lascia al chiamante: una barra in cima con il titolo e la chiusura, il contenuto che scorre da
   * solo, il congedo trascinando. Sotto i 600 dp di larghezza sta a filo dei bordi come un foglio;
   * sopra si stacca, con un margine tutto intorno e la larghezza di lettura, perche' un modulo
   * largo milleduecento dp non e' una pagina, e' un muro. Dentro non va una `LazyColumn`: il
   * contenuto scorre gia'.
   *
   * Ed e' **opaca**, l'unica presentazione che lo e': il vetro sta sulle cose che galleggiano
   * sopra un contenuto, e una pagina non galleggia, copre. Di vetro sarebbe l'intera interfaccia
   * sfocata, con la pagina di sotto che si legge attraverso il titolo.
   */
  FullScreen,
}

/**
 * One action in a context menu.
 *
 * Dalla 4.0 (F5A.3) i campi nuovi — [subtitle], [children], [keepsMenuOpen] — stanno **prima** di [onClick], come
 * `startsSection` e `selected`, cosi' la lambda in coda resta l'azione. La chiamata a sette argomenti per posizione
 * della 3.x (`FluidContextAction("X", null, false, true, false, false, {})`) passerebbe la lambda a [subtitle]: la
 * tiene viva il costruttore secondario, con quella firma esatta. Cosa cambia della data class: `copy` per nome
 * compila come prima (per posizione no: il settimo e' [subtitle]); `component7` e' [subtitle] e [onClick] e'
 * `component10`, quindi una destrutturazione a sette non e' piu' l'azione; `equals` e `hashCode` contano anche i
 * tre campi nuovi, e due voci uguali in tutto il resto ma con figli diversi sono diverse.
 */
@Immutable
data class FluidContextAction(
  val label: String,
  val icon: ImageVector? = null,
  /** Deletes, revokes, removes. Drawn in the error colour, and worth at most one per menu. */
  val destructive: Boolean = false,
  val enabled: Boolean = true,
  /**
   * La voce apre una sezione nuova (3.0): sopra di lei, al posto della riga sottile, una banda — come
   * i gruppi di un menu di iOS ("Copia, Condividi" | "Elimina"). Sulla prima voce non conta. Prima di
   * [onClick], cosi' la lambda in coda continua a essere l'azione.
   */
  val startsSection: Boolean = false,
  /**
   * La voce e' quella scelta (3.0.1): una spunta a destra, e per TalkBack lo stato «selezionato». Per i menu
   * che scelgono fra alternative («Ordina per», «Vista»): prima ogni app ci metteva un'icona di spunta a
   * mano, che TalkBack non leggeva. Prima di [onClick], cosi' la lambda in coda resta l'azione.
   */
  val selected: Boolean = false,
  /**
   * Una seconda riga sotto l'etichetta, piu' piccola e piu' tenue (4.0, F5A.3): il secondo `Text` di una voce di
   * iOS («Ordina» / «Per data»). La riga si alza per tenerla; non si tronca mai l'etichetta per farle posto.
   */
  val subtitle: String? = null,
  /**
   * Le voci di un sottomenu (4.0, F5A.3): la voce mostra un gallone e, scelta, il menu non si chiude ma si
   * trasforma **dentro lo stesso vetro** nel sottomenu, con in cima la voce madre per tornare indietro. Un livello
   * solo, come chiede Apple: i figli dei figli non si aprono. Con dei figli [onClick] non viene chiamata.
   */
  val children: List<FluidContextAction> = emptyList(),
  /**
   * La scelta non chiude il menu (4.0, F5A.3; `menuActionDismissBehavior(.disabled)` di SwiftUI): un interruttore
   * nel menu, «+» e «−» della dimensione del testo. [onClick] parte e il menu resta; perche' la voce mostri lo
   * stato nuovo il menu va dichiarato con [FluidMenuActions] e un contenuto che legge quello stato.
   */
  val keepsMenuOpen: Boolean = false,
  val onClick: () -> Unit,
) {
  /**
   * La firma della 3.x, sette argomenti per posizione: la chiamata `FluidContextAction("X", null, false, true,
   * false, false, {})` e quella con tutti e sette i nomi risolvono qui (Kotlin preferisce il candidato che non usa
   * valori di serie), e i tre campi nuovi prendono i loro.
   */
  constructor(
    label: String,
    icon: ImageVector?,
    destructive: Boolean,
    enabled: Boolean,
    startsSection: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
  ) : this(
    label = label,
    icon = icon,
    destructive = destructive,
    enabled = enabled,
    startsSection = startsSection,
    selected = selected,
    subtitle = null,
    children = emptyList(),
    keepsMenuOpen = false,
    onClick = onClick,
  )
}

/**
 * Se una finestra ancorata disegna le proprie azioni o il contenuto che le è stato passato.
 *
 * La regola stava scritta come `menu || expand` dentro il disegno, e siccome quelle due
 * presentazioni passavano **sempre** per le azioni, chi dichiarava [FluidGlassModalPresentation.Expand]
 * e gli dava un `content` vedeva il pannello aprirsi vuoto: la sagoma viaggiava, lo scrim c'era,
 * l'ancora si nascondeva, e dentro non c'era niente. Il contenuto non veniva rifiutato — veniva
 * ignorato in silenzio, che è il modo in cui un'API costa un pomeriggio invece di un errore.
 *
 * Le azioni restano la ragione per cui quelle due presentazioni esistono e vincono quando ci sono.
 * Ma una lista vuota non è una presentazione: senza niente da elencare, ciò che il chiamante ha
 * effettivamente passato è l'unica cosa che ha senso disegnare.
 *
 * È una funzione a sé perché è l'unica parte di quel ramo che si possa verificare senza disegnare.
 */
internal fun fluidModalShowsActions(
  presentation: FluidGlassModalPresentation,
  hasActions: Boolean,
): Boolean = hasActions && when (presentation) {
  FluidGlassModalPresentation.ContextMenu, FluidGlassModalPresentation.Expand -> true
  FluidGlassModalPresentation.Popover,
  FluidGlassModalPresentation.Sheet,
  FluidGlassModalPresentation.FullScreen,
  -> false
}

/** Le presentazioni che arrivano viaggiando dal basso: la molla e' l'animazione, non la dissolvenza. */
internal fun fluidModalSlidesUp(presentation: FluidGlassModalPresentation): Boolean =
  presentation == FluidGlassModalPresentation.Sheet || presentation == FluidGlassModalPresentation.FullScreen

/**
 * Declares a modal here and draws it at the root.
 *
 * Nothing is emitted at the call site — this composable has no size and no pixels. It writes its
 * content into the host inside a `DisposableEffect` and takes it back out in `onDispose`, so a
 * feature that leaves the composition can never leave a modal stranded above the app.
 *
 * @param origin the rectangle to grow out of, in root coordinates — normally the bounds of the row
 *   that was tapped, captured with [fluidExpandOrigin]. Null opens from the centre.
 */
@Composable
fun FluidGlassModalPortal(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  origin: () -> Rect? = { null },
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover,
  paneTitle: String? = null,
  paneTint: Color? = null,
  /**
   * I tasti che chiudono il compito, sotto il contenuto.
   *
   * In una [FluidGlassModalPresentation.FullScreen] restano fermi mentre il contenuto scorre: un
   * modulo in cui «Salva» sta sotto il bordo dello schermo e' un modulo in cui meta' delle persone
   * non trova il tasto, perche' la lista dei campi sembra la fine della pagina. Nelle altre
   * presentazioni vengono semplicemente dopo il contenuto.
   */
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  /**
   * Il fondo di una [FluidGlassModalPresentation.FullScreen] al posto di `surface` (3.0.1): il nero di un
   * visualizzatore di foto, il colore di una pagina che ne ha uno suo. Il titolo e la chiusura in cima
   * prendono il colore che ci si legge sopra. Le altre presentazioni sono vetro, e lo ignorano.
   */
  pageColor: Color? = null,
  content: @Composable ColumnScope.() -> Unit,
) = FluidGlassModalPortalEntry(
  visible, onDismissRequest, origin, presentation, paneTitle, paneTint, footer, pageColor,
  source = null, mode = null, content = content,
)

/**
 * Presenta un contenuto dalla [source]. Il popover prende in prestito la sorgente per tutta
 * la corsa, ritorno compreso. In larghezza compatta si presenta come foglio di contenuto.
 * Il popover è parallelo di serie; il foglio adattato è modale. [mode] permette di dichiarare
 * come il compito si integra con la pagina. Un [origin] geometrico può fare da ripiego.
 */
@Composable
fun FluidGlassModalPortal(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  origin: () -> Rect? = { null },
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover,
  paneTitle: String? = null,
  paneTint: Color? = null,
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  pageColor: Color? = null,
  source: FluidTransitionSource? = null,
  compactAdaptation: FluidCompactAdaptation = FluidCompactAdaptation.Sheet,
  mode: FluidPresentationMode? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val compact = LocalConfiguration.current.screenWidthDp in 1 until FluidModalCompactWidth
  val adapted = if (presentation == FluidGlassModalPresentation.Popover &&
    fluidModalAdaptsPopover(compact, compactAdaptation)
  ) FluidGlassModalPresentation.Sheet else presentation
  FluidGlassModalPortalEntry(
    visible, onDismissRequest, origin, adapted, paneTitle, paneTint, footer, pageColor,
    source = source,
    mode = mode ?: if (adapted == FluidGlassModalPresentation.Popover) {
      FluidPresentationMode.Parallel
    } else {
      FluidPresentationMode.Modal
    },
    content = content,
  )
}

private const val FluidModalCompactWidth = 600

internal fun fluidModalAdaptsPopover(compact: Boolean, adaptation: FluidCompactAdaptation): Boolean =
  compact && adaptation == FluidCompactAdaptation.Sheet

@Composable
private fun FluidGlassModalPortalEntry(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  origin: () -> Rect?,
  presentation: FluidGlassModalPresentation,
  paneTitle: String?,
  paneTint: Color?,
  footer: (@Composable ColumnScope.() -> Unit)?,
  pageColor: Color?,
  source: FluidTransitionSource?,
  mode: FluidPresentationMode?,
  content: @Composable ColumnScope.() -> Unit,
) {
  val host = LocalFluidGlassModalHostState.current ?: return
  val entry = rememberFluidGlassModalEntry()

  // Written after composition succeeds rather than during it. The lambda is rebuilt on every
  // recomposition of the feature, so whatever it captured is never more than one frame old — and
  // what it *reads* is snapshot state, which the host subscribes to directly when it runs it.
  SideEffect {
    val opening = visible && !entry.visible
    if (opening) entry.openedAt = host.nextSequence()
    entry.visible = visible
    entry.onDismissRequest = onDismissRequest
    entry.origin = origin
    entry.paneTitle = paneTitle
    entry.paneTint = paneTint
    entry.pageColor = pageColor
    entry.footer = footer
    entry.content = content
    // Il popover sceglie l'adattamento all'apertura. Un cambio di larghezza durante la corsa
    // non sposta il contenuto in un altro layer e non restituisce il tasto in anticipo.
    if (mode == null || opening || (!visible && !entry.lifted)) {
      entry.presentation = presentation
      entry.source = source
      entry.mode = mode
    }
  }

  DisposableEffect(host, entry) {
    host.register(entry)
    onDispose { host.unregister(entry) }
  }
}

/** Il popover da [source] aperto da un dato, conservato per il viaggio di ritorno. */
@Composable
fun <T : Any> FluidGlassModalPortal(
  item: T?,
  onDismissRequest: () -> Unit,
  origin: () -> Rect? = { null },
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover,
  paneTitle: String? = null,
  paneTint: Color? = null,
  footer: (@Composable ColumnScope.(T) -> Unit)? = null,
  pageColor: Color? = null,
  source: FluidTransitionSource? = null,
  compactAdaptation: FluidCompactAdaptation = FluidCompactAdaptation.Sheet,
  mode: FluidPresentationMode? = null,
  content: @Composable ColumnScope.(T) -> Unit,
) {
  var lastItem by remember { mutableStateOf(item) }
  if (item != null) lastItem = item
  FluidGlassModalPortal(
    visible = item != null,
    onDismissRequest = onDismissRequest,
    origin = origin,
    presentation = presentation,
    paneTitle = paneTitle,
    paneTint = paneTint,
    footer = footer?.let { footer -> { lastItem?.let { footer(it) } } },
    pageColor = pageColor,
    source = source,
    compactAdaptation = compactAdaptation,
    mode = mode,
  ) { lastItem?.let { content(it) } }
}

/**
 * Declares a modal that presents **an item**, and stays whole while it leaves.
 *
 * This is the form to use when the modal shows a selected thing — a detail, a record, a note — and
 * "open" means "something is selected". Passing `visible = x != null` plus a lambda that reads `x`
 * to the other overload cannot survive its own dismissal: the compiler gives that lambda one
 * mutable instance whose captures are swapped in place, so on the frame the selection clears the
 * content composes to nothing and the pane would leave the screen as a hard cut, its text gone
 * while the scrim is still fading. Here the portal keeps the last non-null [item] itself and keeps
 * handing it to [content] for as long as the exit runs, so the pane leaves exactly as it stood.
 */
@Composable
fun <T : Any> FluidGlassModalPortal(
  item: T?,
  onDismissRequest: () -> Unit,
  origin: () -> Rect? = { null },
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover,
  paneTitle: String? = null,
  paneTint: Color? = null,
  footer: (@Composable ColumnScope.(T) -> Unit)? = null,
  /** Vedi l'altro overload. Dalla 3.0.1. */
  pageColor: Color? = null,
  content: @Composable ColumnScope.(T) -> Unit,
) {
  var lastItem by remember { mutableStateOf(item) }
  if (item != null) lastItem = item
  FluidGlassModalPortal(
    visible = item != null,
    onDismissRequest = onDismissRequest,
    origin = origin,
    presentation = presentation,
    paneTitle = paneTitle,
    paneTint = paneTint,
    footer = footer?.let { footer -> { lastItem?.let { footer(it) } } },
    pageColor = pageColor,
  ) {
    lastItem?.let { content(it) }
  }
}

/**
 * Reports this element's bounds in root coordinates, for a modal to expand out of.
 *
 * The rectangle is what the eye was looking at when it tapped, and starting the pop-up anywhere else
 * costs the connection between the two. Cheap to leave on a row: it only reports when the row
 * actually moves.
 */
@Composable
fun Modifier.fluidExpandOrigin(
  /**
   * Whether the modal this element opens is on screen right now.
   *
   * While it is, the element **stops drawing**, and that is not tidiness: the pane that grew out of
   * it is glass, so the row underneath went on showing *through* it. Two rims one inside the other
   * where the card's edge should be, and the row's own title readable behind the pane's — which is
   * exactly what "the pop-up interpenetrates the button that opened it" looks like from the outside.
   *
   * The space stays reserved, because this is a draw and not a layout: nothing below the row moves
   * while the pane is up, so folding back into it lands on the same rectangle it left.
   */
  open: () -> Boolean = { false },
  onMeasured: (Rect) -> Unit,
): Modifier {
  val requested = open()
  // La dissolvenza legacy per chi passa `open`: resta onorata, ma il lavoro vero oggi lo fa il
  // registro delle ancore qui sotto — la finestra nasconde la riga DA SOLA, senza che il chiamante
  // debba cablare niente. E' cosi' che ogni app eredita la trasformazione aggiornando l'engine.
  val alpha = remember { Animatable(1f) }
  var grace by remember { mutableStateOf(false) }
  LaunchedEffect(requested) {
    if (requested) {
      grace = true
      alpha.animateTo(0f, FluidMotion.fadeOut(FluidAnchorFadeMillis))
    } else if (grace) {
      alpha.animateTo(1f, FluidMotion.fadeIn(FluidAnchorFadeMillis))
      grace = false
    }
  }

  // Il pezzo che mancava a "il tasto stesso diventa il pop-up": la riga si registra presso l'host,
  // si dipinge in un GraphicsLayer mentre un dito la tocca (lo stesso mestiere dell'ancora del
  // menu contestuale: il tocco che apre il modale e' anche quello che scatta la fotografia), e
  // quando la finestra e' in scena smette di disegnarsi DI COLPO — l'immagine continua il viaggio
  // nella finestra, quindi il cambio di proprietario non ha niente da mostrare.
  val host = LocalFluidGlassModalHostState.current
  val layer = rememberGraphicsLayer()
  val node = remember(layer) { FluidExpandAnchorNode(layer) }
  DisposableEffect(host, node) {
    host?.expandAnchors?.add(node)
    onDispose {
      node.hidden = false
      host?.expandAnchors?.remove(node)
    }
  }
  return this
    .onGloballyPositioned {
      val rect = it.boundsInRoot()
      node.bounds = rect
      onMeasured(rect)
    }
    .graphicsLayer { this.alpha = alpha.value }
    .drawWithContent {
      // In scena: la riga E' la finestra adesso, e disegnarla anche qui era il difetto numero
      // uno dell'apertura — "il tasto rimane li', non si e' trasformato". Lo spazio resta
      // riservato (e' un draw, non un layout), quindi il rientro atterra su un rettangolo vuoto.
      //
      // E la registrazione avviene QUI, nel primo draw da nascosta: niente registrazione al
      // tocco — un tap iniettato dura meno di un fotogramma e non lasciava mai un'istantanea
      // (layer 0x0, verificato a logcat). Nascondere invalida il draw, quindi questo ramo corre
      // nello stesso fotogramma in cui la finestra appare, sempre PRIMA che l'overlay la
      // ridisegni: l'immagine che parte col viaggio e' quella vera, di quel momento.
      if (node.hidden) {
        node.layer.record { this@drawWithContent.drawContent() }
        return@drawWithContent
      }
      drawContent()
    }
}

/** Quanto ci mette la riga a togliersi di mezzo, e a tornare (solo dissolvenza legacy di `open`). */
private const val FluidAnchorFadeMillis = 120
