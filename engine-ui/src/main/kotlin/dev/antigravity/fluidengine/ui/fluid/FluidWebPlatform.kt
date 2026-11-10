@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package dev.antigravity.fluidengine.ui.fluid

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import java.util.UUID

// Il lato Android delle due macchine a stati di FluidWebContent: qui, e solo qui, stanno le chiamate al
// provider per i profili e per il renderer. Nessuna decisione: quelle sono in ClosedProfilePool e in
// RendererClosing, che si provano sul JVM. Ogni chiamata ad androidx.webkit resta dentro il suo
// WebViewFeature.isFeatureSupported, anche dove la macchina ha gia' controllato: lo chiede il lint.

/** Profili e chiusure hanno un solo thread, quello principale: un ingresso da un altro e' un errore del contenitore. */
internal fun requireMainLooper() =
  check(Looper.myLooper() == Looper.getMainLooper()) { "FluidWebContent: stato dei profili toccato fuori dal thread principale" }

/** I profili del processo: l'unico [ClosedProfilePool] vero. Solo nomi: nessuna vista e nessuna Activity. */
internal val ClosedProfiles: ClosedProfilePool<WebView> = ClosedProfilePool(AndroidProfiles)

/** [ProfilePlatform] su AndroidX WebKit: `ProfileStore`, `WebViewCompat`, `WebStorageCompat` e un Handler. */
private object AndroidProfiles : ProfilePlatform<WebView> {
  private val main = Handler(Looper.getMainLooper())

  override fun requireMainThread() = requireMainLooper()

  override val multiProfile: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

  override val deleteBrowsingData: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)

  override fun newId(): String = UUID.randomUUID().toString()

  override fun existingNames(): Collection<String> =
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      ProfileStore.getInstance().allProfileNames
    } else {
      emptyList()
    }

  override fun assign(view: WebView, name: String) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      WebViewCompat.setProfile(view, name)
      val cookies = WebViewCompat.getProfile(view).cookieManager
      cookies.setAcceptCookie(false)
      cookies.setAcceptThirdPartyCookies(view, false)
    } else {
      throw IsolationUnavailable()
    }
  }

  override fun clearData(name: String, done: () -> Unit): Boolean {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
        val profile = ProfileStore.getInstance().getProfile(name) ?: return false
        // La risposta torna sul thread principale, qualunque sia quello su cui il provider la produce.
        WebStorageCompat.deleteBrowsingData(profile.webStorage, { main.post(it) }) { done() }
        return true
      }
    }
    return false
  }

  override fun delete(name: String) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      ProfileStore.getInstance().deleteProfile(name)
    }
  }

  override fun schedule(delayMillis: Long, action: () -> Unit) {
    main.postDelayed({ action() }, delayMillis)
  }
}

/** La chiusura di una vista, lato Android: il client che la vista riceve quando si chiude, e [ClosingPlatform].
 *
 * Le decisioni (quando terminare, quanto aspettare, che fine fa il profilo) sono di [RendererClosing]. Questo
 * oggetto tiene la vista e il riferimento al renderer, blocca ogni richiesta che arrivasse ancora, e risponde
 * true a `onRenderProcessGone`: la morte del renderer di un documento chiuso e' gestita qui e non arriva
 * all'app. `shouldInterceptRequest` gira su altri thread, ma non tocca nessuno stato.
 */
internal class WebViewClosing(view: WebView, profile: String?, rendererGone: Boolean) : WebViewClient(), ClosingPlatform {
  /** Nulla appena la vista e' distrutta: i tentativi rimasti in coda non trattengono vista ne' Activity. */
  private var view: WebView? = view
  private var renderer: WebViewRenderProcess? = null
  private val closing = RendererClosing(this, profile, rendererGone, ClosedProfiles)

  fun start() = closing.start()

  override fun shouldInterceptRequest(view: WebView, resource: WebResourceRequest): WebResourceResponse = blocked()
  override fun shouldOverrideUrlLoading(view: WebView, resource: WebResourceRequest): Boolean = true
  override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

  override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
    closing.rendererGone(crashed = detail.didCrash())
    return true
  }

  override fun requireMainThread() = requireMainLooper()

  override fun acquireRenderer(): Boolean {
    val current = view ?: return false
    renderer = try {
      if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_RENDERER)) {
        WebViewCompat.getWebViewRenderProcess(current)
      } else null
    } catch (_: RuntimeException) {
      null // Chiudere un documento non deve mai far cadere l'app.
    }
    return renderer != null
  }

  override fun takeOver() {
    view?.webViewClient = this
  }

  override fun terminate(): Boolean? {
    val process = renderer ?: return false
    return try {
      if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)) {
        process.terminate()
      } else null
    } catch (_: RuntimeException) {
      null
    }
  }

  override fun destroyView() {
    view?.destroy()
    view = null
  }

  override fun schedule(delayMillis: Long, action: () -> Unit) {
    Main.postAtTime({ action() }, this, SystemClock.uptimeMillis() + delayMillis)
  }

  override fun cancelScheduled() = Main.removeCallbacksAndMessages(this)

  private companion object {
    val Main = Handler(Looper.getMainLooper())
  }
}
