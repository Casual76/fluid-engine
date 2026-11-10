package dev.antigravity.fluidengine.ui.fluid

import android.net.Uri
import android.print.PrintDocumentAdapter
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Richiesta di una risorsa: nessuna intestazione personale della WebView attraversa questo confine.
 * Il gestore viene chiamato su thread di I/O, anche contemporaneamente. Se scarica la risorsa deve usare
 * un client senza cookie, referer, credenziali o redirect impliciti; [headers] offre solo un UA generico.
 * Schemi inline personalizzati passano dallo stesso gestore; data/blob/about sono interni al renderer.
 */
data class FluidWebRequest(
  val url: Uri,
  val method: String,
  val isMainFrame: Boolean,
) {
  /** Intestazioni minime suggerite al client dell'app; non sono quelle ricevute dal renderer. */
  val headers: Map<String, String> get() = mapOf("User-Agent" to FluidWebContentDefaults.UserAgent)
}

/** Nessuna alternativa abilita la rete della WebView: l'app puo' soltanto fornire byte o bloccare.
 * Non si accettano header, redirect o Set-Cookie dalla risposta dell'app. La politica delle risorse e
 * la sanificazione dell'HTML restano responsabilita' dell'app, anche quando restituisce [Serve].
 */
sealed interface FluidWebResponse {
  /** Risposta vuota locale, mai `null` (che autorizzerebbe il caricamento nativo). */
  data object Block : FluidWebResponse
  /** [body] si apre su I/O; WebView chiude lo stream. Non restituire stream condivisi fra richieste. */
  class Serve(val mimeType: String, val encoding: String? = null, val body: () -> InputStream) : FluidWebResponse
}

/** Destinazione vera e testo dell'ancora, quando il renderer riesce a fornirlo senza eseguire script. */
data class FluidWebLink(val url: String, val visibleText: String? = null)

/** Stato effimero: il documento rimane nei dati dell'app, mai nel Bundle Android. */
enum class FluidWebPhase { Empty, Loading, Ready, Error }

/** Cause su cui l'app puo' offrire testo alternativo o aggiornamento del provider WebView. */
enum class FluidWebFailure { IsolationUnavailable, RendererGone, Timeout, ContentTooLarge, Unavailable }

/** Altezza sempre limitata: un documento enorme non diventa una texture enorme in una lista lazy. */
sealed interface FluidWebSizing {
  /** Cede lo scroll verticale alla pagina finche' il contenuto entra; oltre il tetto scorre da se'. */
  data class WrapContent(val maxHeight: Dp = 2048.dp) : FluidWebSizing
  /** Viewport fissa per documenti lunghi. Inserirla preferibilmente in una pagina senza altro scroll. */
  data class Viewport(val height: Dp = 600.dp) : FluidWebSizing
}

/** Limiti del contenitore, indipendenti dal dominio dell'app. */
object FluidWebContentDefaults {
  /** Nessuna identificazione di dispositivo, piattaforma, versione o app. */
  const val UserAgent: String = "WebContent/1.0"
  /** Rifiuto prima di copiare l'HTML su I/O; l'app puo' mostrare la propria versione testuale. */
  const val MaxHtmlCharacters: Int = 4_000_000
  internal const val MaxViewportPx = 4096
  internal const val Csp = "sandbox; script-src 'none'; object-src 'none'; frame-src 'none'; " +
    "child-src 'none'; form-action 'none'; base-uri 'none'; connect-src 'none'"
}

/** Stato di una sola istanza montata. Non conserva Activity o WebView dopo lo smontaggio.
 * [retry] distrugge e ricrea la vista con un altro profilo: non rianima un renderer morto, e il profilo
 * di un renderer morto da se' non viene piu' riusato.
 * Tutte le azioni e letture UI si eseguono sul thread principale.
 */
@Stable
class FluidWebContentState {
  /** Prima pittura confermata dal renderer, non il solo completamento della richiesta HTML. */
  var phase: FluidWebPhase by mutableStateOf(FluidWebPhase.Empty)
    private set
  /** Errore esposto allo slot dell'app; nullo durante una nuova apertura. */
  var failure: FluidWebFailure? by mutableStateOf(null)
    private set
  /** Altezza fisica del documento (puo' superare quella della viewport). */
  var contentHeightPx: Int by mutableIntStateOf(0)
    internal set
  /** Vero anche nel ripiego automatico oltre il tetto di WrapContent. */
  var scrollsInternally: Boolean by mutableStateOf(false)
    internal set
  internal var revision by mutableIntStateOf(0)
  private var owner: Any? = null
  private var printer: ((String) -> PrintDocumentAdapter)? = null

  /** Riprova anche dopo un errore di creazione; non salva l'HTML ne' consensi fra istanze. */
  fun retry() { revision++ }

  /** Adattatore per PrintManager.print dell'Activity dell'app; nullo senza prima pittura valida.
   * L'app deve mantenere l'host montato per la durata della stampa.
   */
  fun printAdapter(jobName: String): PrintDocumentAdapter? =
    if (phase == FluidWebPhase.Ready) printer?.invoke(jobName) else null

  internal fun attach(token: Any, hasContent: Boolean) {
    check(owner == null || owner === token) { "FluidWebContentState richiede un solo host montato" }
    owner = token
    phase = if (hasContent) FluidWebPhase.Loading else FluidWebPhase.Empty
    failure = null
    contentHeightPx = 0
    scrollsInternally = false
  }
  internal fun bindPrinter(token: Any, action: (String) -> PrintDocumentAdapter) {
    if (owner === token) printer = action
  }
  internal fun ready(token: Any) {
    if (owner === token && phase == FluidWebPhase.Loading) phase = FluidWebPhase.Ready
  }
  internal fun fail(token: Any, reason: FluidWebFailure) {
    if (owner !== token || phase == FluidWebPhase.Error) return
    failure = reason
    phase = FluidWebPhase.Error
    printer = null
  }
  internal fun detach(token: Any) {
    if (owner !== token) return
    printer = null
    owner = null
    phase = FluidWebPhase.Empty
    failure = null
    contentHeightPx = 0
    scrollsInternally = false
  }
}

/** Conserva solo lo stato effimero; dopo ricreazione il contenuto viene fornito nuovamente dall'app. */
@Composable
fun rememberFluidWebContentState(): FluidWebContentState = remember { FluidWebContentState() }

/** HTML locale in una WebView chiusa. Non e' un sanificatore: l'app filtra HTML, CSS e risorse.
 *
 * JS, bridge, file/content, storage, form, finestre, permessi, navigazione e rete nativa sono chiusi.
 * Cookie/cache usano un profilo dedicato, di una sola vista alla volta. Senza MULTI_PROFILE si fallisce
 * chiusi anziche' cambiare il CookieManager globale o condividere cache con altre WebView dell'app.
 * Alla chiusura di un documento (html diverso, darkening, retry, uscita dalla composizione) il suo
 * processo renderer viene terminato e la vista distrutta: nessun RendererGone arriva per un documento che
 * l'app ha chiuso, e le altre WebView dell'app, che stanno su altri profili, non se ne accorgono.
 * Un profilo non si puo' cancellare finche' vive il processo, quindi i profili sono pochi e riusati: uno
 * torna disponibile solo dopo che la vista e' stata distrutta, il renderer terminato e confermato morto, e
 * cache, cookie e storage cancellati; un profilo il cui renderer e' morto da se' non si riusa piu'. Resta
 * un solo stato che il provider non lascia cancellare, l'elenco degli indirizzi visitati: vedi la scheda.
 * Il documento radice e' servito su un'origine casuale .invalid, con CSP sandbox e no-store.
 * Nessun `baseUrl` pubblico: un'origine controllata dall'HTML potrebbe condividere dati con altre viste.
 *
 * Prima di arrivare al renderer il documento viene sigillato, sempre e senza opzioni: `preconnect`,
 * `dns-prefetch` e `x-dns-prefetch-control` non sono caricamenti, quindi aprirebbero socket e
 * risolverebbero nomi senza passare da [onRequest] (basta a un mittente per sapere che il documento e'
 * stato aperto). Ogni `<link`, `<meta` e `<iframe` scritto dal documento viene neutralizzato, anche
 * travestito o dentro commenti e `<svg>`. Sopravvivono, riscritti dal contenitore: un foglio di stile
 * esterno semplice (`rel="stylesheet"`, con `href`, `media`, `title`), che resta una richiesta per
 * [onRequest]; `<meta name="viewport">`; `<meta name="color-scheme">`. Gli iframe non vengono resi,
 * nemmeno `srcdoc`. Non sopravvivono icone, `preload`, `<meta http-equiv>` e i fogli con `integrity`,
 * `crossorigin` o attributi ambigui. Un documento senza quei tag arriva al renderer identico.
 *
 * [onRequest] deve essere thread-safe e finito; le eccezioni bloccano la risorsa. Il suo aggiornamento
 * non ricarica il documento: per applicare nuovi consensi alle risorse gia' bloccate chiamare retry.
 * [onLinkClick] e [onLinkLongPress] sono sul main thread; l'app decide se e dove aprire la destinazione.
 * [darkening] abilita lo scurimento algoritmico se supportato; false mostra l'originale. Il tema scuro
 * e gli stili scuri dichiarati dal documento restano quelli del provider AndroidX/WebView.
 * Cambiare darkening ricrea la vista (con un altro profilo) e richiede nuovamente le risorse: Chromium legge
 * isLightTheme alla creazione, non quando cambia il tema del Context esistente.
 * [textScale] moltiplica sempre la scala del carattere di sistema. Il pizzico e' opzionale.
 * [placeholder] resta sopra la vista fino alla prima pittura; HTML e stream non vengono copiati sul main.
 * La creazione di WebView richiede comunque il main thread; nessuna promessa di latenza di avvio a freddo.
 * Altezza fisica massima 4096 px, anche con font al 200%. Nessun pool globale conserva Activity.
 */
@Composable
fun FluidWebContent(
  html: String,
  onLinkClick: (FluidWebLink) -> Unit,
  modifier: Modifier = Modifier,
  state: FluidWebContentState = rememberFluidWebContentState(),
  onRequest: (FluidWebRequest) -> FluidWebResponse = { FluidWebResponse.Block },
  sizing: FluidWebSizing = FluidWebSizing.WrapContent(),
  darkening: Boolean = false,
  textScale: Float = 1f,
  pinchZoom: Boolean = true,
  onLinkLongPress: ((FluidWebLink) -> Unit)? = null,
  placeholder: @Composable () -> Unit = { FluidSkeletonText(lines = 6) },
  failure: @Composable (FluidWebFailure) -> Unit = {},
) {
  require(textScale.isFinite() && textScale > 0f)
  val density = LocalDensity.current
  val desiredHeight = when (sizing) {
    is FluidWebSizing.WrapContent -> sizing.maxHeight
    is FluidWebSizing.Viewport -> sizing.height
  }
  require(desiredHeight.value.isFinite() && desiredHeight.value > 0f)
  val cap = with(density) { desiredHeight.roundToPx() }.coerceIn(1, FluidWebContentDefaults.MaxViewportPx)
  val zoom = (density.fontScale * textScale * 100).roundToInt().coerceIn(1, 1000)
  val request by rememberUpdatedState(onRequest)
  val link by rememberUpdatedState(onLinkClick)
  val longLink by rememberUpdatedState(onLinkLongPress)
  key(html, state, state.revision, darkening) {
    val token = remember { Any() }
    var document by remember { mutableStateOf<ByteArray?>(null) }
    var view by remember { mutableStateOf<ClosedWebView?>(null) }
    DisposableEffect(state, token) {
      state.attach(token, html.isNotBlank())
      onDispose { view?.release(); state.detach(token) }
    }
    LaunchedEffect(token) {
      if (html.isBlank()) return@LaunchedEffect
      if (html.length > FluidWebContentDefaults.MaxHtmlCharacters) {
        state.fail(token, FluidWebFailure.ContentTooLarge)
        return@LaunchedEffect
      }
      // Il sigillo e' parte del contratto chiuso, non un'opzione: vedi FluidWebSeal.
      val sealed = withContext(Dispatchers.Default) {
        FluidWebSeal.seal(html).takeIf(FluidWebSeal::isSealed)?.toByteArray(Charsets.UTF_8)
      }
      if (sealed == null) {
        // Non deve succedere: se la riscrittura e il suo controllo non concordano, non si carica nulla.
        state.fail(token, FluidWebFailure.Unavailable)
        return@LaunchedEffect
      }
      document = sealed
      delay(15_000)
      if (state.phase == FluidWebPhase.Loading) {
        state.fail(token, FluidWebFailure.Timeout)
        view?.release()
      }
    }
    // Durante il parsing di HTML enorme contentHeight cambia a ogni frame. Oltre il tetto
    // queste variazioni non devono ricomporre la pagina o impedire ai gesti di trovare idle.
    val heightPx by remember(sizing, cap, density) { derivedStateOf {
      when (sizing) {
        is FluidWebSizing.Viewport -> cap
        // Evita una viewport iniziale inferiore a un pixel CSS.
        is FluidWebSizing.WrapContent -> state.contentHeightPx.coerceIn(
          with(density) { 1.dp.roundToPx() }.coerceIn(1, cap), cap)
      }
    } }
    val internalScroll by remember(sizing, cap) { derivedStateOf {
      sizing is FluidWebSizing.Viewport || state.contentHeightPx > cap
    } }
    Box(modifier) {
      val bytes = document
      if (bytes != null && state.phase != FluidWebPhase.Error) {
        val anchor = remember { ViewFocusAnchor() }
        // Il nodo che lascia il fuoco deve stare su un antenato: Compose stacca la vista
        // dell'AndroidView prima di eseguire il ciclo di vita dei modificatori del suo stesso nodo.
        Box(ReleaseViewFocusElement(anchor)) {
          AndroidView(
            modifier = Modifier.fillMaxWidth().height(with(density) { heightPx.toDp() }),
            factory = { context ->
              FrameLayout(context).also { container ->
                anchor.container = container
                try {
                  val web = ClosedWebView(context, darkening)
                  view = web
                  web.configure(bytes, { request(it) }, { link(it) }, { longLink?.invoke(it) },
                    onPaint = { state.ready(token) },
                    onFailure = { state.fail(token, it) },
                    onHeight = { state.contentHeightPx = it })
                  container.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                  state.bindPrinter(token, web::createPrintDocumentAdapter)
                } catch (_: IsolationUnavailable) {
                  state.fail(token, FluidWebFailure.IsolationUnavailable)
                  view?.release()
                } catch (_: RuntimeException) {
                  state.fail(token, FluidWebFailure.Unavailable)
                  view?.release()
                }
              }
            },
            update = {
              view?.takeUnless { it.released }?.let { web ->
                web.updateAppearance(zoom, darkening, pinchZoom)
                web.internalScroll = internalScroll
                state.scrollsInternally = internalScroll
              }
            },
            onRelease = { view?.release(); anchor.container = null },
          )
        }
      }
      when (state.phase) {
        FluidWebPhase.Loading -> placeholder()
        FluidWebPhase.Error -> state.failure?.let { failure(it) }
        else -> Unit
      }
    }
  }
}

/** Contenitore Android della vista corrente; nullo prima della factory e dopo il rilascio. */
private class ViewFocusAnchor { var container: ViewGroup? = null }

/** Toglie il fuoco Android alla WebView prima che Compose stacchi la sua vista.
 *
 * Un tocco (un link, una selezione) da' il fuoco Android alla WebView. Quando Compose rimuove una vista
 * con fuoco mentre applica una composizione (ricreazione per darkening/html/retry, oppure l'app che
 * toglie il contenitore), la piattaforma chiede il fuoco alla radice: la ricerca del fuoco di Compose
 * misura una LazyColumn genitrice e ricompone un elemento non ancora applicato, con ComposeRuntimeError
 * "pending composition has not been applied". [ViewParent.clearChildFocus] scollega la catena del fuoco
 * senza chiedere un sostituto, lo stesso esito di un focusable Compose rimosso. La vista non viene mai
 * riusata: si rilascia subito dopo il distacco, quindi lo stato interno di fuoco non sopravvive.
 * Compose esegue onDetach di un nodo prima di staccare i suoi figli: per questo sta su un antenato.
 */
private class ReleaseViewFocusElement(private val anchor: ViewFocusAnchor) :
  ModifierNodeElement<ReleaseViewFocusNode>() {
  override fun create() = ReleaseViewFocusNode(anchor)
  override fun update(node: ReleaseViewFocusNode) { node.anchor = anchor }
  override fun equals(other: Any?) = other is ReleaseViewFocusElement && other.anchor === anchor
  override fun hashCode() = System.identityHashCode(anchor)
  override fun InspectorInfo.inspectableProperties() { name = "releaseViewFocus" }
}

private class ReleaseViewFocusNode(var anchor: ViewFocusAnchor) : Modifier.Node() {
  override fun onDetach() {
    val container = anchor.container ?: return
    if (container.hasFocus()) container.parent?.clearChildFocus(container)
  }
}
