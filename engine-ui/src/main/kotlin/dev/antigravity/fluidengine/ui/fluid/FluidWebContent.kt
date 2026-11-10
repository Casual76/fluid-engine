@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package dev.antigravity.fluidengine.ui.fluid

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.print.PrintDocumentAdapter
import android.view.MotionEvent
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
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

private class IsolationUnavailable : RuntimeException()

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

/** Nessun riferimento alla vista esce dall'implementazione: impostazioni non sovrascrivibili dall'app. */
// ViewConstructor chiede i costruttori (Context[, AttributeSet]) che servono a chi gonfia la vista da XML o la
// mostra nell'editor dei layout. Questa classe e' privata e la crea solo la factory di FluidWebContent, con
// `darkening` che decide il tema del Context prima che WebView lo legga: quei costruttori non avrebbero chiamanti.
@SuppressLint("ViewConstructor")
private class ClosedWebView(context: Context, darkening: Boolean) : WebView(ContextThemeWrapper(context,
  if (darkening) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)) {
  var released = false
    private set
  var internalScroll = false
  private var profileName: String? = null
  private var reportHeight: (Int) -> Unit = {}
  private var lastHeight = 0
  private var reportLink: (FluidWebLink) -> Unit = {}
  private var reportLongLink: (FluidWebLink) -> Unit = {}
  private var touchX = 0f
  private var touchY = 0f
  private var moved = false
  private var pinching = false
  private var pendingHeight = false
  private var darkAppearance: Boolean? = null
  private val main = Handler(Looper.getMainLooper())

  @androidx.annotation.OptIn(markerClass = [WebSettingsCompat.ExperimentalSpeculativeLoading::class])
  fun configure(
    html: ByteArray,
    request: (FluidWebRequest) -> FluidWebResponse,
    link: (FluidWebLink) -> Unit,
    longLink: (FluidWebLink) -> Unit,
    onPaint: () -> Unit,
    onFailure: (FluidWebFailure) -> Unit,
    onHeight: (Int) -> Unit,
  ) {
    // Fallisce chiuso prima di ogni altra cosa: senza profili isolati non nasce una vista meno isolata.
    profileName = ClosedProfiles.isolate(this)
    reportHeight = onHeight
    reportLink = link
    reportLongLink = longLink
    alpha = 0f
    isSaveEnabled = false
    importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    settings.apply {
      javaScriptEnabled = false
      javaScriptCanOpenWindowsAutomatically = false
      allowFileAccess = false
      allowContentAccess = false
      allowFileAccessFromFileURLs = false
      allowUniversalAccessFromFileURLs = false
      domStorageEnabled = false
      databaseEnabled = false
      saveFormData = false
      savePassword = false
      setGeolocationEnabled(false)
      setSupportMultipleWindows(false)
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      safeBrowsingEnabled = false
      blockNetworkLoads = true
      cacheMode = WebSettings.LOAD_NO_CACHE
      userAgentString = FluidWebContentDefaults.UserAgent
      mediaPlaybackRequiresUserGesture = true
      useWideViewPort = true
      loadWithOverviewMode = true
      displayZoomControls = false
    }
    // Di serie e' gia' spento: lo si dichiara, cosi' un provider che cambiasse il valore non apre
    // precaricamenti e prerender decisi dal documento.
    if (WebViewFeature.isFeatureSupported(WebViewFeature.SPECULATIVE_LOADING)) {
      WebSettingsCompat.setSpeculativeLoadingStatus(settings, WebSettingsCompat.SPECULATIVE_LOADING_DISABLED)
    }
    setNetworkAvailable(false)
    removeJavascriptInterface("searchBoxJavaBridge_")
    removeJavascriptInterface("accessibility")
    removeJavascriptInterface("accessibilityTraversal")
    setDownloadListener { _, _, _, _, _ -> }
    webChromeClient = object : WebChromeClient() {
      override fun onPermissionRequest(request: PermissionRequest) = request.deny()
      override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
        callback.invoke(origin, false, false)
      override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        callback.onReceiveValue(null)
        return true
      }
    }
    val root = "https://${UUID.randomUUID()}.invalid/document"
    val rootServed = AtomicBoolean(false)
    webViewClient = object : WebViewClient() {
      override fun shouldInterceptRequest(view: WebView, resource: WebResourceRequest): WebResourceResponse {
        if (resource.isForMainFrame && resource.url.toString() == root && rootServed.compareAndSet(false, true)) {
          return response("text/html", "UTF-8", ByteArrayInputStream(html))
        }
        if (resource.isForMainFrame) return blocked()
        return try {
          when (val result = request(FluidWebRequest(resource.url, resource.method, resource.isForMainFrame))) {
            FluidWebResponse.Block -> blocked()
            is FluidWebResponse.Serve -> response(result.mimeType, result.encoding, result.body())
          }
        } catch (_: Exception) { blocked() }
      }
      override fun shouldOverrideUrlLoading(view: WebView, resource: WebResourceRequest): Boolean {
        if (resource.isForMainFrame && resource.hasGesture()) {
          requestLink(resource.url.toString(), reportLink)
        }
        return true
      }
      override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
      override fun onPageCommitVisible(view: WebView, url: String) {
        if (url != root || released) return
        postVisualStateCallback(0, object : VisualStateCallback() {
          override fun onComplete(requestId: Long) {
            if (!released) { alpha = 1f; onPaint(); invalidate() }
          }
        })
      }
      override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        // Arriva solo a un documento ancora aperto: il renderer e' morto da se' (un crash, o il sistema a
        // corto di memoria). Chi chiude un documento passa prima da release(), che cambia client: la morte
        // chiesta dal contenitore va a WebViewClosing, mai qui.
        onFailure(FluidWebFailure.RendererGone)
        release(rendererGone = true)
        return true
      }
    }
    setOnLongClickListener {
      moved = true
      requestLink(null, reportLongLink)
      true // Nessun menu di piattaforma puo' aprire la destinazione autonomamente.
    }
    loadUrl(root)
  }

  fun updateAppearance(zoom: Int, darkening: Boolean, pinch: Boolean) {
    if (settings.textZoom != zoom) settings.textZoom = zoom
    settings.setSupportZoom(pinch)
    settings.builtInZoomControls = pinch
    if (darkAppearance != darkening) {
      darkAppearance = darkening
      // Il tema Compose puo' essere diverso da quello XML dell'Activity: WebView legge isLightTheme
      // dal proprio Context. Si aggiorna la configurazione senza ricaricare HTML o risorse.
      (context as ContextThemeWrapper).setTheme(if (darkening) android.R.style.Theme_Material_NoActionBar
        else android.R.style.Theme_Material_Light_NoActionBar)
      val config = Configuration(resources.configuration)
      config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        if (darkening) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
      super.onConfigurationChanged(config)
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
      WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darkening)
    } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
      WebSettingsCompat.setForceDark(settings, if (darkening) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
    }
  }

  private fun requestLink(fallback: String?, action: (FluidWebLink) -> Unit) {
    val handler = Handler(Looper.getMainLooper()) { message ->
      if (!released) {
        val url = message.data.getString("url").takeUnless { it.isNullOrBlank() } ?: fallback
        if (!url.isNullOrBlank()) action(FluidWebLink(url, message.data.getString("title")?.takeIf { it.isNotBlank() }))
      }
      true
    }
    requestFocusNodeHref(handler.obtainMessage())
  }

  // ClickableViewAccessibility vuole che la logica di un clic riconosciuto in onTouchEvent stia in performClick.
  // Qui non si riconosce nessun clic: il metodo guarda il gesto solo per decidere a chi tocca lo scroll
  // (requestDisallowInterceptTouchEvent) e passa sempre l'evento a WebView, che gestisce da se' i tocchi e le azioni
  // di accessibilita' sui nodi della pagina. I link arrivano all'app da shouldOverrideUrlLoading, non da qui.
  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (released) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        touchX = event.x; touchY = event.y; moved = false; pinching = false
        // Prima del primo MOVE: una LazyColumn puo' intercettarlo prima che arrivi alla WebView.
        parent?.requestDisallowInterceptTouchEvent(internalScroll &&
          (canScrollVertically(1) || canScrollVertically(-1)))
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        pinching = true; moved = true
        parent?.requestDisallowInterceptTouchEvent(true)
      }
      MotionEvent.ACTION_MOVE -> {
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        if (kotlin.math.abs(event.x - touchX) > slop || kotlin.math.abs(event.y - touchY) > slop) moved = true
        parent?.requestDisallowInterceptTouchEvent(pinching ||
          (internalScroll && canScrollVertically(if (event.y < touchY) 1 else -1)))
      }
      // Chromium aggiorna l'ancora focalizzata al rilascio. Cancellare ACTION_UP impedisce
      // sia requestFocusNodeHref sia shouldOverrideUrlLoading. La navigazione resta bloccata
      // dal client; JS e CSP restano spenti anche per gli href javascript:.
    }
    return super.onTouchEvent(event)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val measured = floor(contentHeight.toDouble() * scale.toDouble()).coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt()
    // contentHeight include almeno la viewport, arrotondata a pixel CSS. Riconvertirla con
    // ceil fa crescere la viewport di un pixel a ogni draw. Ignora il solo errore di un pixel CSS.
    val rounding = ceil(scale.toDouble()).toInt().coerceAtLeast(1)
    if (measured > 0 && (lastHeight == 0 || kotlin.math.abs(measured - lastHeight) > rounding) && !pendingHeight) {
      pendingHeight = true
      main.post {
        pendingHeight = false
        if (!released) { lastHeight = measured; reportHeight(measured) }
      }
    }
  }

  /** Chiude la vista: da qui in poi non riporta piu' nulla, e [WebViewClosing] ne spegne il renderer.
   *
   * [rendererGone] e' vero quando il renderer e' gia' morto da se': non c'e' piu' nulla da terminare, e il
   * profilo non verra' piu' riusato.
   */
  fun release(rendererGone: Boolean = false) {
    if (released) return
    released = true
    main.removeCallbacksAndMessages(null)
    reportHeight = {}
    reportLink = {}
    reportLongLink = {}
    // Senza fuoco nella catena removeView non chiede il fuoco alla radice: vedi ReleaseViewFocusElement.
    if (hasFocus()) parent?.clearChildFocus(this)
    (parent as? ViewGroup)?.removeView(this)
    stopLoading()
    webChromeClient = null
    setOnLongClickListener(null)
    val profile = profileName
    profileName = null
    WebViewClosing(this, profile, rendererGone).start()
  }
}

/** La chiusura di una vista: termina il suo renderer, aspetta che il provider lo confermi, poi la distrugge.
 *
 * Il provider da' a ogni profilo un processo renderer suo, e quel processo sopravvive a `destroy()` finche'
 * vive l'app: senza terminarlo, ogni documento aperto in una sessione ne lascerebbe uno (decine di megabyte).
 * Si puo' terminare senza toccare nessun altro proprio perche' il profilo e' di questa sola vista: le WebView
 * dell'app stanno su altri profili, quindi su altri processi. Senza un profilo dedicato ([profile] nullo: la
 * creazione e' fallita prima) il renderer sarebbe il loro, e non si prende nemmeno.
 *
 * L'ordine conta. Questo oggetto diventa il client della vista, cosi' la morte del renderer che ha chiesto
 * lui viene riportata a lui (`onRenderProcessGone`, a cui risponde true) e mai all'app: il client predefinito
 * risponderebbe false e il sistema chiuderebbe l'app. La vista, gia' staccata e ferma, viene distrutta solo
 * a morte confermata: e' quella conferma a dire che nel profilo non gira piu' niente del documento, ed e' la
 * condizione perche' [ClosedProfiles] possa pulire il profilo e darlo a un documento successivo. Un documento
 * nuovo non puo' quindi ricevere la morte del renderer di uno vecchio.
 *
 * `terminate()` risponde false in due casi che da qui non si distinguono: il processo non c'e', oppure sta
 * ancora partendo (un documento chiuso nei primi istanti, per esempio sfogliando in fretta). Nel secondo
 * caso finirebbe di partire e resterebbe orfano, quindi si riprova a intervalli crescenti. La vista si tiene
 * solo per i primi tentativi (pochi secondi); poi viene distrutta, il profilo non si riusa piu', e i tentativi
 * continuano sul solo riferimento al processo, per meno di un minuto in tutto.
 *
 * Dove il provider non sa dare o terminare il renderer la vista viene distrutta subito, come nella 3.1.0.
 *
 * Vive solo sul thread principale: ci arriva da `release()`, da `onRenderProcessGone` (che WebView chiama li') e
 * dai tentativi rimessi in coda sul suo Handler. `shouldInterceptRequest` gira altrove, ma non tocca nessuno stato.
 */
private class WebViewClosing(
  view: WebView,
  private val profile: String?,
  rendererGone: Boolean,
) : WebViewClient() {
  /** Nulla appena la vista e' distrutta: i tentativi rimasti in coda non trattengono vista ne' Activity. */
  private var view: WebView? = view
  private val renderer: WebViewRenderProcess? = if (profile != null && !rendererGone) rendererOf(view) else null
  private var finished = false
  private var asked = false
  private var asking = false
  private var attempts = 0

  fun start() {
    requireMainThread()
    view?.webViewClient = this
    if (renderer == null) finish(confirmed = false) else attempt()
  }

  override fun shouldInterceptRequest(view: WebView, resource: WebResourceRequest): WebResourceResponse = blocked()
  override fun shouldOverrideUrlLoading(view: WebView, resource: WebResourceRequest): Boolean = true
  override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

  override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
    // Il processo non c'e' piu': niente da riprovare. Conferma solo la morte chiesta da qui: un crash
    // arrivato nel frattempo non fa riusare il profilo.
    Main.removeCallbacksAndMessages(this)
    finish(confirmed = (asked || asking) && !detail.didCrash())
    return true
  }

  private fun attempt() {
    requireMainThread()
    val process = renderer ?: return
    asking = true
    val terminated = terminate(process)
    asking = false
    when {
      terminated == null -> finish(confirmed = false)
      terminated -> {
        asked = true
        // La conferma arriva in qualche decina di millisecondi. Se non arrivasse, la vista non resta in giro.
        if (!finished) Main.postAtTime({ finish(confirmed = false) }, this, SystemClock.uptimeMillis() + ConfirmationMillis)
      }
      else -> {
        if (attempts == HeldAttempts) finish(confirmed = false)
        if (attempts < RetryDelays.size) Main.postAtTime(::attempt, this, SystemClock.uptimeMillis() + RetryDelays[attempts++])
      }
    }
  }

  private fun finish(confirmed: Boolean) {
    requireMainThread()
    if (finished) return
    finished = true
    // Dopo una terminazione riuscita non resta nulla da riprovare. Se invece il processo stava ancora
    // partendo, i tentativi restano in coda: non toccano piu' la vista, solo il riferimento al processo.
    if (asked) Main.removeCallbacksAndMessages(this)
    view?.destroy()
    view = null
    if (profile != null) {
      if (confirmed) ClosedProfiles.recycle(profile) else ClosedProfiles.retire(profile)
    }
  }

  private companion object {
    val Main = Handler(Looper.getMainLooper())
    /** Attese fra un tentativo di terminazione e il successivo, in millisecondi. */
    val RetryDelays = longArrayOf(50, 150, 400, 1_000, 2_500, 6_000, 15_000, 30_000)
    /** Tentativi falliti dopo i quali la vista non si tiene piu' (circa quattro secondi). */
    const val HeldAttempts = 5
    const val ConfirmationMillis = 5_000L

    fun rendererOf(view: WebView): WebViewRenderProcess? =
      try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_RENDERER)) {
          WebViewCompat.getWebViewRenderProcess(view)
        } else null
      } catch (_: RuntimeException) {
        null // Chiudere un documento non deve mai far cadere l'app.
      }

    /** `null` se il provider non sa terminare un renderer. */
    fun terminate(renderer: WebViewRenderProcess): Boolean? =
      try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)) {
          renderer.terminate()
        } else null
      } catch (_: RuntimeException) {
        null
      }
  }
}

/** Profili e chiusure hanno un solo thread, quello principale: un ingresso da un altro e' un errore del contenitore. */
private fun requireMainThread() =
  check(Looper.myLooper() == Looper.getMainLooper()) { "FluidWebContent: stato dei profili toccato fuori dal thread principale" }

/** I profili dei documenti: uno per vista alla volta, riusato solo dopo una pulizia confermata.
 *
 * Un profilo caricato non si puo' cancellare nello stesso processo (lo vieta AndroidX), e resta caricato
 * anche a renderer morto: sull'emulatore di prova ogni profilo costa al processo dell'app circa 24
 * descrittori di file, 2 thread e 0,7 MB, piu' un centinaio di kilobyte su disco. Un profilo nuovo per ogni
 * documento farebbe quindi crescere l'app a ogni mail aperta. Per questo i profili sono pochi: un profilo
 * torna disponibile quando, e solo quando,
 * 1. la sua vista e' stata distrutta;
 * 2. il suo renderer e' stato terminato dal contenitore e il provider ne ha confermato la morte
 *    ([WebViewClosing]): nessuna memoria del renderer passa al documento successivo;
 * 3. `deleteBrowsingData` ha risposto: cache di rete, cookie e ogni storage del profilo sono cancellati.
 * In ogni altro caso (renderer morto da se', conferma mai arrivata, funzione mancante nel provider) il
 * profilo viene ritirato: nessuna vista lo riusa in questo processo.
 *
 * La regola e' scritta negli stati ([Stage]): un nome entra fra i disponibili solo da `Cleaning`, dove arriva
 * solo da `InUse`, e ne esce appena una vista lo prende. Quindi due viste vive non condividono mai un profilo,
 * e un profilo ritirato non torna. I disponibili si danno dal piu' vecchio: nessuno resta indietro.
 *
 * RESIDUO NOTO: il provider non offre un modo di cancellare l'elenco degli indirizzi visitati di un profilo.
 * Dopo la pulizia vi restano gli indirizzi dei documenti precedenti, casuali e nuovi per ogni vista (un
 * documento non li puo' conoscere), e cio' che una pagina con script vi avesse aggiunto con pushState.
 *
 * Tutto sul thread principale, e ogni ingresso lo controlla. Qui restano solo nomi: nessuna vista e nessuna
 * Activity.
 */
private object ClosedProfiles {
  /** Dove si trova un profilo creato in questo processo. */
  private enum class Stage { InUse, Cleaning, Clean, Retired }

  private const val Prefix = "fluid-content-"
  /** Attesa prima di cominciare a eliminare i profili del processo precedente: il primo documento non li ha fra i piedi. */
  private const val PruneStartMillis = 4_000L
  /** Pausa fra un profilo eliminato e il successivo: sul thread principale ce n'e' al piu' uno ogni tanto. */
  private const val PrunePauseMillis = 50L
  private val main = Handler(Looper.getMainLooper())
  /** Ogni profilo creato in questo processo, dal primo momento. Quelli del processo precedente non ci sono mai. */
  private val stages = HashMap<String, Stage>()
  private val clean = ArrayDeque<String>()
  private val leftovers = ArrayDeque<String>()
  private var pruned = false

  /** Da' a [view] un profilo suo e senza cookie; restituisce il nome. Senza MULTI_PROFILE fallisce chiuso. */
  fun isolate(view: WebView): String {
    requireMainThread()
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      pruneAfterProcessRecreation()
      val name = takeClean() ?: (Prefix + UUID.randomUUID())
      // Ritirato finche' la vista non lo ha davvero: se una chiamata qui sotto fallisce, non torna disponibile.
      stages[name] = Stage.Retired
      WebViewCompat.setProfile(view, name)
      val cookies = WebViewCompat.getProfile(view).cookieManager
      cookies.setAcceptCookie(false)
      cookies.setAcceptThirdPartyCookies(view, false)
      stages[name] = Stage.InUse
      return name
    } else {
      throw IsolationUnavailable()
    }
  }

  private fun takeClean(): String? {
    while (true) {
      val name = clean.removeFirstOrNull() ?: return null
      if (stages[name] == Stage.Clean) return name
    }
  }

  /** Il profilo non ha piu' vista ne' renderer: lo pulisce, e solo a pulizia finita lo rende disponibile. */
  fun recycle(name: String) {
    requireMainThread()
    if (stages[name] != Stage.InUse) return
    // Ritirato finche' la pulizia non e' partita davvero, e di nuovo se non parte.
    stages[name] = Stage.Retired
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
        try {
          val profile = ProfileStore.getInstance().getProfile(name) ?: return
          stages[name] = Stage.Cleaning
          // La risposta torna sul thread principale, qualunque sia quello su cui il provider la produce.
          WebStorageCompat.deleteBrowsingData(profile.webStorage, { main.post(it) }) { cleaned(name) }
        } catch (_: RuntimeException) {
          stages[name] = Stage.Retired // Senza una pulizia confermata il profilo resta ritirato.
        }
      }
    }
  }

  private fun cleaned(name: String) {
    requireMainThread()
    if (stages[name] != Stage.Cleaning) return
    stages[name] = Stage.Clean
    clean.addLast(name)
  }

  /** Il profilo non verra' piu' usato in questo processo; [pruneAfterProcessRecreation] lo elimina al prossimo. */
  fun retire(name: String) {
    requireMainThread()
    stages[name] = Stage.Retired
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      try {
        ProfileStore.getInstance().deleteProfile(name)
      } catch (_: IllegalStateException) {
        // Chromium tiene caricato il profilo anche dopo destroy(), e AndroidX vieta di cancellarlo in
        // questo processo: non e' un errore dell'host e non deve far cadere l'Activity.
      }
    }
  }

  /** Al primo host del processo: elenca i profili lasciati dal processo precedente, per eliminarli con calma.
   *
   * Non puo' toccare un profilo di questo processo (in uso, in pulizia, disponibile o ritirato), per tre
   * motivi che non dipendono l'uno dall'altro: l'elenco si prende prima di creare il primo profilo, e non si
   * riprende piu'; [pruneOne] salta ogni nome che questo processo ha creato; e il provider rifiuta di
   * cancellare un profilo caricato, cioe' ogni profilo usato qui. Recupera anche i profili lasciati da una
   * terminazione senza onDispose, e non tocca quelli di altre WebView dell'app.
   *
   * Cancellarli costa qualche millisecondo l'uno, sul thread principale perche' l'API lo richiede: si comincia
   * qualche secondo dopo il primo documento e se ne elimina uno per volta. Se il processo finisce prima, i
   * rimasti si ritrovano nell'elenco del processo successivo.
   */
  private fun pruneAfterProcessRecreation() {
    if (pruned) return
    pruned = true
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      for (name in ProfileStore.getInstance().allProfileNames) {
        if (name.startsWith(Prefix)) leftovers.addLast(name)
      }
      if (leftovers.isNotEmpty()) main.postDelayed(::pruneOne, PruneStartMillis)
    }
  }

  private fun pruneOne() {
    requireMainThread()
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      val name = leftovers.removeFirstOrNull() ?: return
      if (name !in stages) {
        try {
          ProfileStore.getInstance().deleteProfile(name)
        } catch (_: IllegalStateException) {
          // Caricato da qualcun altro in questo processo: non e' nostro da cancellare adesso.
        }
      }
      if (leftovers.isNotEmpty()) main.postDelayed(::pruneOne, PrunePauseMillis)
    }
  }
}

private fun response(mime: String, encoding: String?, stream: InputStream): WebResourceResponse =
  try {
    WebResourceResponse(mime, encoding, 200, "OK", mapOf(
      "Cache-Control" to "no-store", "Referrer-Policy" to "no-referrer",
      "Content-Security-Policy" to FluidWebContentDefaults.Csp,
      "X-Content-Type-Options" to "nosniff",
      // Spegne la risoluzione anticipata dei nomi delle ancore; un <meta> successivo non la riaccende.
      "X-DNS-Prefetch-Control" to "off",
    ), stream)
  } catch (failure: Exception) {
    stream.close()
    throw failure
  }

private fun blocked(): WebResourceResponse = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
  mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(byteArrayOf()))
