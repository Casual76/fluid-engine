@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package dev.antigravity.fluidengine.ui.fluid

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.ViewGroup
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.floor

// La vista chiusa di FluidWebContent: le impostazioni che chiudono JS, rete, storage e navigazione, il client
// che consegna ogni richiesta all'app, i tocchi e la misura dell'altezza. Finche' il documento e' aperto la
// vista e' di chi la monta (il composable in FluidWebContent.kt); con release() passa a WebViewClosing
// (FluidWebPlatform.kt), che ne spegne il renderer e la distrugge. Il profilo lo riceve da ClosedProfiles.

/** Nessun riferimento alla vista esce dall'implementazione: impostazioni non sovrascrivibili dall'app. */
// ViewConstructor chiede i costruttori (Context[, AttributeSet]) che servono a chi gonfia la vista da XML o la
// mostra nell'editor dei layout. Questa classe e' interna al modulo e la crea solo la factory di FluidWebContent, con
// `darkening` che decide il tema del Context prima che WebView lo legga: quei costruttori non avrebbero chiamanti.
@SuppressLint("ViewConstructor")
internal class ClosedWebView(context: Context, darkening: Boolean) : WebView(ContextThemeWrapper(context,
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

internal fun response(mime: String, encoding: String?, stream: InputStream): WebResourceResponse =
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

internal fun blocked(): WebResourceResponse = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
  mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(byteArrayOf()))
