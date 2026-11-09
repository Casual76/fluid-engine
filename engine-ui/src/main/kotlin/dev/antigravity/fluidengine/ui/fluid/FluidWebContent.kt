@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package dev.antigravity.fluidengine.ui.fluid

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
 * [retry] distrugge e ricrea la vista con un profilo nuovo: non rianima un renderer morto.
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
 * Cookie/cache usano un profilo dedicato, mai riutilizzato. Si elimina al rilascio quando il provider
 * lo permette, altrimenti al primo host del processo successivo. Senza MULTI_PROFILE si fallisce chiusi
 * anziche' cambiare il CookieManager globale o condividere cache con altre WebView dell'app.
 * Il documento radice e' servito su un'origine casuale .invalid, con CSP sandbox e no-store.
 * Nessun `baseUrl` pubblico: un'origine controllata dall'HTML potrebbe condividere dati con altre viste.
 *
 * [onRequest] deve essere thread-safe e finito; le eccezioni bloccano la risorsa. Il suo aggiornamento
 * non ricarica il documento: per applicare nuovi consensi alle risorse gia' bloccate chiamare retry.
 * [onLinkClick] e [onLinkLongPress] sono sul main thread; l'app decide se e dove aprire la destinazione.
 * [darkening] abilita lo scurimento algoritmico se supportato; false mostra l'originale. Il tema scuro
 * e gli stili scuri dichiarati dal documento restano quelli del provider AndroidX/WebView.
 * Cambiare darkening ricrea il profilo e richiede nuovamente le risorse: Chromium legge
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
      document = withContext(Dispatchers.Default) { html.toByteArray(Charsets.UTF_8) }
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

  fun configure(
    html: ByteArray,
    request: (FluidWebRequest) -> FluidWebResponse,
    link: (FluidWebLink) -> Unit,
    longLink: (FluidWebLink) -> Unit,
    onPaint: () -> Unit,
    onFailure: (FluidWebFailure) -> Unit,
    onHeight: (Int) -> Unit,
  ) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) throw IsolationUnavailable()
    ClosedProfiles.pruneAfterProcessRecreation()
    val profile = "fluid-content-${UUID.randomUUID()}"
    WebViewCompat.setProfile(this, profile)
    profileName = profile
    WebViewCompat.getProfile(this).cookieManager.apply {
      setAcceptCookie(false)
      setAcceptThirdPartyCookies(this@ClosedWebView, false)
    }
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
        onFailure(FluidWebFailure.RendererGone)
        release()
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

  fun release() {
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
    webViewClient = WebViewClient()
    setOnLongClickListener(null)
    destroy()
    profileName?.let { ClosedProfiles.deleteIfUnloaded(it) }
    profileName = null
  }
}

private object ClosedProfiles {
  private var pruned = false
  fun deleteIfUnloaded(name: String) {
    try {
      ProfileStore.getInstance().deleteProfile(name)
    } catch (_: IllegalStateException) {
      // Chromium puo' mantenere caricato il profilo anche dopo destroy(). AndroidX vieta di
      // cancellarlo in questo processo: non e' un errore dell'host e non deve far cadere l'Activity.
      // Nessuna vista futura lo riusa; pruneAfterProcessRecreation lo elimina al prossimo processo.
    }
  }
  fun pruneAfterProcessRecreation() {
    if (pruned) return
    pruned = true
    val store = ProfileStore.getInstance()
    // Al primo host del processo nessuna nostra vista e' ancora viva. Recupera anche profili
    // lasciati da una terminazione senza onDispose, senza toccare profili di altre WebView.
    store.allProfileNames.filter { it.startsWith("fluid-content-") }.forEach { store.deleteProfile(it) }
  }
}

private fun response(mime: String, encoding: String?, stream: InputStream): WebResourceResponse =
  try {
    WebResourceResponse(mime, encoding, 200, "OK", mapOf(
      "Cache-Control" to "no-store", "Referrer-Policy" to "no-referrer",
      "Content-Security-Policy" to FluidWebContentDefaults.Csp,
      "X-Content-Type-Options" to "nosniff",
    ), stream)
  } catch (failure: Exception) {
    stream.close()
    throw failure
  }

private fun blocked(): WebResourceResponse = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
  mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(byteArrayOf()))
