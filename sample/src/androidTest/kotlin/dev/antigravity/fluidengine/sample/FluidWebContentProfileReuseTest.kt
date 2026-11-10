package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Il riuso dei profili (K1b): fra due documenti che usano lo stesso profilo non passa niente.
 *
 * Dalla 3.1.1 il contenitore non crea piu' un profilo per ogni documento: un profilo torna disponibile dopo che la sua
 * vista e' stata distrutta, il suo renderer terminato e confermato morto, i suoi dati cancellati. Queste prove
 * guardano i canali osservabili: le risorse servite (devono tornare a `onRequest`), i cookie, lo storage che un
 * renderer compromesso avrebbe potuto scrivere nel profilo (simulato da una WebView di prova con JavaScript acceso,
 * attaccata allo stesso profilo), lo stato `:visited`.
 *
 * Ogni prova controlla prima di tutto che il riuso sia avvenuto davvero: senza, non proverebbe nulla.
 */
@RunWith(AndroidJUnit4::class)
class FluidWebContentProfileReuseTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val first = FluidWebContentState()
  private val second = FluidWebContentState()
  private val requests = CopyOnWriteArrayList<String>()
  private var html by mutableStateOf("")
  private var other by mutableStateOf<String?>(null)

  @Before fun requireReuse() {
    assumeTrue("Senza queste funzioni il contenitore non riusa nessun profilo: non c'e' nulla da provare",
      WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_RENDERER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))
  }

  /** L'app serve due risorse, come farebbe un client di posta con le immagini sbloccate. */
  private fun mount() {
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        Column {
          FluidWebContent(html, {}, state = first, modifier = Modifier.fillMaxWidth(), sizing = FluidWebSizing.Viewport(240.dp),
            placeholder = {}, onRequest = ::serve)
          other?.let {
            FluidWebContent(it, {}, state = second, modifier = Modifier.fillMaxWidth(), sizing = FluidWebSizing.Viewport(240.dp),
              placeholder = {}, onRequest = ::serve)
          }
        }
      }
    }
  }

  private fun serve(request: FluidWebRequest): FluidWebResponse {
    requests += request.url.toString()
    return when (request.url.toString()) {
      SharedStyle -> FluidWebResponse.Serve("text/css", "UTF-8") { ByteArrayInputStream("p{margin:0}".toByteArray()) }
      SharedImage -> FluidWebResponse.Serve("image/svg+xml", "UTF-8") {
        ByteArrayInputStream("<svg xmlns='http://www.w3.org/2000/svg' width='8' height='8'/>".toByteArray())
      }
      else -> FluidWebResponse.Block
    }
  }

  private fun webs(): List<WebView> {
    val found = ArrayList<WebView>()
    fun find(view: View) {
      if (view is WebView) found += view
      else if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))
    }
    find(compose.activity.window.decorView)
    return found
  }

  private fun profiles(): List<String> = compose.runOnIdle { webs().map { WebViewCompat.getProfile(it).name } }

  private fun show(document: String): String {
    compose.runOnIdle { html = document }
    compose.waitForIdle()
    compose.waitUntil(30_000) { first.phase == FluidWebPhase.Ready || first.phase == FluidWebPhase.Error }
    assertEquals("${first.failure}", FluidWebPhase.Ready, first.phase)
    return profiles().first()
  }

  private fun page(title: String, body: String = "") = "<meta name='viewport' content='width=device-width'><title>$title</title>" +
    "<link rel='stylesheet' href='$SharedStyle'><body style='margin:0'>$body<p>$title</p><img src='$SharedImage' width='8' height='8'>"

  private fun served(url: String) = requests.count { it == url }

  /** Apre documenti finche' il contenitore non rida' [profile] a quello mostrato; fallisce se non succede.
   *
   * Restituisce quante volte le due risorse erano state chieste prima di aprire il documento che ha riusato il profilo.
   */
  private fun showUntilReused(profile: String, document: String): Pair<Int, Int> {
    // I profili puliti si danno dal piu' vecchio: quello cercato torna dopo quelli gia' pronti prima di lui, che sono pochi.
    for (attempt in 1..16) {
      // La pulizia del profilo appena lasciato e' asincrona: una pausa breve prima di aprire il successivo.
      Thread.sleep(600)
      compose.waitForIdle()
      val before = served(SharedStyle) to served(SharedImage)
      if (show("$document<!-- tentativo $attempt -->") == profile) return before
      compose.waitUntil(10_000) { served(SharedStyle) > before.first && served(SharedImage) > before.second }
    }
    fail("Il contenitore non ha riusato il profilo $profile: la prova non puo' dire nulla")
    throw AssertionError()
  }

  // ------------------------------------------------------------------------------- WebView di prova con JavaScript

  /** Una WebView con JavaScript acceso attaccata a [profile]: fa quello che potrebbe fare un renderer compromesso. */
  private fun spy(profile: String, acceptCookies: Boolean): WebView {
    val loaded = CountDownLatch(1)
    lateinit var web: WebView
    compose.runOnUiThread {
      web = WebView(compose.activity)
      WebViewCompat.setProfile(web, profile)
      web.settings.javaScriptEnabled = true
      web.settings.domStorageEnabled = true
      @Suppress("DEPRECATION")
      web.settings.databaseEnabled = true
      if (acceptCookies) WebViewCompat.getProfile(web).cookieManager.setAcceptCookie(true)
      web.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
          if (request.url.toString() == StainPage) {
            WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"),
              ByteArrayInputStream("<title>macchia</title><p>pagina di prova</p>".toByteArray()))
          } else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        override fun onPageFinished(view: WebView, url: String) { if (url == StainPage) loaded.countDown() }
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail) = true
      }
      web.loadUrl(StainPage)
    }
    assertTrue("La WebView di prova non ha caricato", loaded.await(20, TimeUnit.SECONDS))
    return web
  }

  /** Lancia [script], che deve finire assegnando una stringa JSON a `window.result`, e la restituisce. */
  private fun run(web: WebView, script: String): JSONObject {
    compose.runOnUiThread { web.evaluateJavascript("window.result='';$script", null) }
    val deadline = System.currentTimeMillis() + 15_000
    while (System.currentTimeMillis() < deadline) {
      val answered = CountDownLatch(1)
      var answer: String? = null
      compose.runOnUiThread { web.evaluateJavascript("window.result") { answer = it; answered.countDown() } }
      assertTrue(answered.await(10, TimeUnit.SECONDS))
      val text = JSONTokener(answer ?: "null").nextValue()
      if (text is String && text.isNotEmpty()) return JSONObject(text)
      Thread.sleep(100)
    }
    fail("Lo script di prova non ha risposto")
    throw AssertionError()
  }

  private fun destroy(web: WebView) = compose.runOnUiThread { web.destroy() }

  private fun pixelOfFirstWeb(dxDp: Int, dyDp: Int): Int {
    var x = 0
    var y = 0
    compose.runOnIdle {
      val location = IntArray(2); webs().first().getLocationInWindow(location)
      val density = compose.activity.resources.displayMetrics.density
      x = location[0] + (dxDp * density).toInt()
      y = location[1] + (dyDp * density).toInt()
    }
    val decor = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    val copied = CountDownLatch(1)
    compose.runOnIdle { PixelCopy.request(compose.activity.window, bitmap, { copied.countDown() }, Handler(Looper.getMainLooper())) }
    assertTrue(copied.await(5, TimeUnit.SECONDS))
    return bitmap.getPixel(x, y).also { bitmap.recycle() }
  }

  /** Colore del collegamento numero [index] in cima al documento mostrato: verde se non visitato, rosso se visitato. */
  private fun linkColor(index: Int = 0): String {
    var pixel = 0
    val deadline = System.currentTimeMillis() + 6_000
    do {
      pixel = pixelOfFirstWeb(60, 30 + 60 * index)
      if (Color.green(pixel) > 120 && Color.red(pixel) < 80) return "verde"
      if (Color.red(pixel) > 150 && Color.green(pixel) < 80) return "rosso"
      Thread.sleep(200)
    } while (System.currentTimeMillis() < deadline)
    return "indeterminato:${Integer.toHexString(pixel)}"
  }

  /** Collegamenti verdi se non visitati e rossi se visitati; ogni regola prova anche a caricare una risorsa sua. */
  private fun linkTo(vararg urls: String) = "<style>a:link{background-color:rgb(0,160,0);background-image:url('$LinkImage')}" +
    "a:visited{background-color:rgb(200,0,0);background-image:url('$VisitedImage')}</style>" +
    urls.joinToString("") { "<a href='$it' style='display:block;height:60px'>collegamento</a>" }

  // ------------------------------------------------------------------------------------------------ prove

  /** Il cuore: il documento che riceve un profilo gia' usato non trova nulla di cio' che il precedente vi ha lasciato. */
  @Test fun reusedProfileCarriesNothingFromThePreviousDocument() {
    mount()
    val profile = show(page("PRIMO"))
    var firstUrl = ""
    compose.runOnIdle { firstUrl = webs().first().url ?: "" }
    assertTrue("Indirizzo del primo documento: $firstUrl", firstUrl.startsWith("https://") && firstUrl.endsWith(".invalid/document"))
    compose.waitUntil(10_000) { served(SharedStyle) == 1 && served(SharedImage) == 1 }

    // Un renderer compromesso scrive nel profilo tutto quello che puo'. I cookie: anche dopo averli riaccesi.
    val stain = spy(profile, acceptCookies = true)
    val written = run(stain, StainScript)
    Log.i("FluidWebLifecycle", "riuso macchia scritta=$written")
    assertEquals("macchia", written.getString("ls"))
    assertTrue(written.toString(), written.getString("ck").contains("c=macchia"))
    assertEquals(written.toString(), "scritto", written.getString("idb"))
    assertEquals(written.toString(), "scritto", written.getString("cache"))
    compose.runOnIdle {
      val cookies = ProfileStore.getInstance().getProfile(profile)!!.cookieManager
      cookies.setCookie(StainPage, "api=macchia; Max-Age=100000; Secure")
      cookies.flush()
      assertTrue("Controprova: i cookie devono essere stati scritti", cookies.hasCookies())
    }
    destroy(stain)

    // Il documento successivo arriva mentre il profilo del primo e' ancora in chiusura: ne riceve un altro.
    val next = show(page("SECONDO"))
    assertNotEquals("Due documenti consecutivi non possono avere lo stesso profilo se il primo non e' ancora pulito", profile, next)
    compose.waitUntil(10_000) { served(SharedStyle) == 2 && served(SharedImage) == 2 }
    val never = "https://${UUID.randomUUID()}.invalid/document"
    val before = showUntilReused(profile, page("TERZO", linkTo(firstUrl, never, "")))
    Log.i("FluidWebLifecycle", "riuso profilo=$profile riottenuto; richieste prima=$before")

    // 1. Le stesse risorse, gia' servite a questo profilo dal primo documento, tornano a onRequest: una volta ciascuna,
    //    come per un documento che le vede per la prima volta. Nessuna cache le ha tenute.
    compose.waitUntil(10_000) { served(SharedStyle) == before.first + 1 && served(SharedImage) == before.second + 1 }
    // 2. Lo stato :visited. RESIDUO NOTO, non una garanzia: il provider non offre un modo di cancellare l'elenco degli
    //    indirizzi visitati di un profilo, e ne' la morte del renderer ne' `deleteBrowsingData` lo toccano. Nell'elenco
    //    c'e' solo l'indirizzo dei documenti precedenti, casuale e nuovo per ogni vista: la prova lo conosce perche' lo
    //    legge dalla vista, un documento no. Si controlla cio' che protegge: gli indirizzi non si ripetono, e uno
    //    mai aperto non risulta visitato (il metodo distingue: il collegamento a se' stesso e' rosso).
    var thirdUrl = ""
    compose.runOnIdle { thirdUrl = webs().first().url ?: "" }
    assertNotEquals("Due documenti sullo stesso profilo non devono avere lo stesso indirizzo", firstUrl, thirdUrl)
    val previous = linkColor(0)
    Log.i("FluidWebLifecycle", "riuso visited indirizzoDelPrecedente=$previous indirizzoMaiAperto=${linkColor(1)} seStesso=${linkColor(2)}")
    assertEquals("Un indirizzo mai aperto risulta visitato", "verde", linkColor(1))
    assertEquals("Controprova: il collegamento a se' stesso deve risultare visitato", "rosso", linkColor(2))
    // 3. Cookie e storage: vuoti, e i cookie di nuovo spenti.
    compose.runOnIdle {
      val cookies = WebViewCompat.getProfile(webs().first()).cookieManager
      assertFalse("Il profilo riusato accetta cookie", cookies.acceptCookie())
      assertFalse("Il profilo riusato ha ancora cookie", cookies.hasCookies())
      assertNull(cookies.getCookie(StainPage))
    }
    val reader = spy(profile, acceptCookies = false)
    val found = run(reader, ReadScript)
    destroy(reader)
    Log.i("FluidWebLifecycle", "riuso letto dopo la pulizia=$found")
    assertTrue("localStorage sopravvissuto: $found", found.isNull("ls"))
    assertEquals("Cookie sopravvissuti: $found", "", found.getString("ck"))
    assertEquals("IndexedDB sopravvissuto: $found", "[]", found.getJSONArray("idb").toString())
    assertEquals("Cache API sopravvissuta: $found", "[]", found.getJSONArray("cache").toString())
    // 4. Lo stato :visited non esce. Il documento ha due collegamenti che risultano visitati (rossi), eppure la risorsa
    //    della regola `a:visited` non e' mai stata chiesta; quella della regola `a:link` si' (controprova: il metodo vede).
    compose.waitUntil(10_000) { served(LinkImage) >= 1 }
    Log.i("FluidWebLifecycle", "riuso visited richiesteDaLink=${served(LinkImage)} richiesteDaVisited=${served(VisitedImage)}")
    assertEquals("Una regola a:visited ha fatto partire una richiesta", 0, served(VisitedImage))
  }

  /** Due documenti vivi insieme (una lista, o il vecchio e il nuovo durante un cambio) hanno sempre profili diversi. */
  @Test fun documentsAliveTogetherNeverShareAProfile() {
    mount()
    compose.runOnIdle { other = page("ACCANTO-0") }
    val seen = LinkedHashSet<String>()
    for (round in 1..12) {
      show(page("SOPRA-$round"))
      compose.runOnIdle { other = page("ACCANTO-$round") }
      compose.waitForIdle()
      compose.waitUntil(30_000) { second.phase == FluidWebPhase.Ready && first.phase == FluidWebPhase.Ready }
      val alive = profiles()
      assertEquals("Servono due viste vive: $alive", 2, alive.size)
      assertEquals("Due documenti vivi condividono un profilo: $alive", 2, alive.toSet().size)
      seen += alive
      Thread.sleep(150)
    }
    Log.i("FluidWebLifecycle", "riuso dueVivi profiliDistinti=${seen.size} in 24 documenti")
    assertTrue("Con 24 documenti il riuso deve essere avvenuto: ${seen.size} profili", seen.size < 24)
  }

  /** Un profilo il cui renderer e' morto da se' non torna piu', nemmeno molti documenti dopo. */
  @Test fun profileWhoseRendererDiedByItselfIsNeverReused() {
    mount()
    val dead = show(page("VITTIMA"))
    compose.runOnIdle { assertTrue(webs().first().webViewRenderProcess!!.terminate()) }
    compose.waitUntil(10_000) { first.failure == FluidWebFailure.RendererGone }
    compose.runOnIdle { first.retry() }
    compose.waitUntil(10_000) { first.phase != FluidWebPhase.Error }
    val later = LinkedHashSet<String>()
    for (round in 1..10) {
      Thread.sleep(300)
      later += show(page("DOPO-$round"))
    }
    Log.i("FluidWebLifecycle", "riuso dopoMorte morto=$dead successivi=${later.size}")
    assertFalse("Il profilo di un renderer morto da solo e' stato riusato", dead in later)
    assertTrue("Il riuso degli altri profili deve continuare: $later", later.size < 10)
  }

  /** Misura, senza garanzie: su questo provider lo stato `:visited` si vede? E sopravvive alla pulizia di un profilo?
   *
   * Il primo colore dice se il metodo d'osservazione funziona (un collegamento al documento stesso). Gli altri due
   * usano WebView di prova sullo stesso profilo e sullo stesso sito, il caso piu' favorevole a chi osserva: nel
   * contenitore ogni documento ha un'origine casuale diversa.
   */
  @Test fun visitedStateIsMeasuredOnThisProvider() {
    mount()
    show(page("SE-STESSO", linkTo("")))
    val self = linkColor()
    val profile = "prova-visited-${UUID.randomUUID()}"
    fun visit(url: String, body: String, kill: Boolean, wipe: Boolean, links: Int = 1): List<String> {
      val loaded = CountDownLatch(1)
      lateinit var web: WebView
      compose.runOnUiThread {
        web = WebView(compose.activity)
        WebViewCompat.setProfile(web, profile)
        web.settings.javaScriptEnabled = true
        web.webViewClient = object : WebViewClient() {
          override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
            if (request.isForMainFrame) {
              WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream("<meta name='viewport' content='width=device-width'><body style='margin:0'>$body".toByteArray()))
            } else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
          override fun onPageFinished(view: WebView, url: String) { loaded.countDown() }
          override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail) = true
        }
        compose.activity.setContentView(web)
        web.loadUrl(url)
      }
      assertTrue(loaded.await(20, TimeUnit.SECONDS))
      Thread.sleep(600)
      val colors = (0 until links).map { linkColor(it) }
      val wiped = CountDownLatch(1)
      compose.runOnUiThread {
        val renderer = WebViewCompat.getWebViewRenderProcess(web)
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        if (kill) renderer?.terminate()
        if (wipe) {
          WebStorageCompat.deleteBrowsingData(ProfileStore.getInstance().getProfile(profile)!!.webStorage) { wiped.countDown() }
        } else wiped.countDown()
      }
      assertTrue(wiped.await(20, TimeUnit.SECONDS))
      Thread.sleep(400)
      return colors
    }
    val target = "https://visited.invalid/uno"
    val pushed = "https://visited.invalid/scritto-con-pushstate"
    val framed = "https://cornice.invalid/in-un-iframe"
    // La prima pagina fa quello che potrebbe fare un renderer compromesso: cambia il proprio indirizzo e naviga un iframe
    // (la risposta e' il 403 vuoto che il contenitore da' a ogni risorsa bloccata).
    visit(target, "<p>uno</p><script>history.pushState({}, '', '/scritto-con-pushstate')</script><iframe src='$framed'></iframe>" + linkTo(target),
      kill = false, wipe = false)
    val sameSite = visit("https://visited.invalid/due", linkTo(target, pushed, framed), kill = false, wipe = false, links = 3)
    val crossSite = visit("https://altro.invalid/tre", linkTo(target), kill = true, wipe = false)
    val afterKill = visit("https://altro.invalid/quattro", linkTo(target), kill = false, wipe = true)
    val afterWipe = visit("https://altro.invalid/cinque", linkTo(target), kill = true, wipe = true)
    val afterBoth = visit("https://altro.invalid/sei", linkTo(target), kill = false, wipe = false)
    val atTheEnd = visit("https://visited.invalid/sette", linkTo(target, pushed, framed), kill = false, wipe = false, links = 3)
    Log.i("FluidWebLifecycle", "visited seStesso=$self stessoSito[pagina,pushState,iframe]=$sameSite altroSito=$crossSite " +
      "appenaDopoLaTerminazione=$afterKill poiDopoLaPulizia=$afterWipe appenaDopoEntrambe=$afterBoth allaFine[pagina,pushState,iframe]=$atTheEnd")
  }

  private companion object {
    const val SharedStyle = "https://resource.invalid/condiviso.css"
    const val SharedImage = "https://resource.invalid/condiviso.svg"
    const val StainPage = "https://macchia.invalid/pagina"
    const val LinkImage = "https://resource.invalid/da-link.png"
    const val VisitedImage = "https://resource.invalid/da-visited.png"

    const val StainScript = """(function(){
      var out = {};
      try { localStorage.setItem('k', 'macchia'); out.ls = localStorage.getItem('k'); } catch (e) { out.ls = 'errore:' + e; }
      try { document.cookie = 'c=macchia; max-age=100000; Secure'; out.ck = document.cookie; } catch (e) { out.ck = 'errore:' + e; }
      var pending = 2;
      function done(){ if (--pending === 0) window.result = JSON.stringify(out); }
      try {
        var request = indexedDB.open('macchia', 1);
        request.onupgradeneeded = function(){ request.result.createObjectStore('s'); };
        request.onsuccess = function(){
          var transaction = request.result.transaction('s', 'readwrite');
          transaction.objectStore('s').put('v', 'k');
          transaction.oncomplete = function(){ out.idb = 'scritto'; done(); };
          transaction.onerror = function(){ out.idb = 'errore'; done(); };
        };
        request.onerror = function(){ out.idb = 'errore'; done(); };
      } catch (e) { out.idb = 'eccezione:' + e; done(); }
      try {
        caches.open('macchia').then(function(c){ return c.put('/voce', new Response('x')); })
          .then(function(){ out.cache = 'scritto'; done(); }, function(e){ out.cache = 'errore:' + e; done(); });
      } catch (e) { out.cache = 'eccezione:' + e; done(); }
    })();"""

    const val ReadScript = """(function(){
      var out = {ls: null, ck: ''};
      try { out.ls = localStorage.getItem('k'); } catch (e) { out.ls = 'errore:' + e; }
      try { out.ck = document.cookie; } catch (e) { out.ck = 'errore:' + e; }
      var pending = 2;
      function done(){ if (--pending === 0) window.result = JSON.stringify(out); }
      indexedDB.databases().then(function(list){ out.idb = list.map(function(d){ return d.name; }); done(); },
        function(e){ out.idb = ['errore:' + e]; done(); });
      caches.keys().then(function(keys){ out.cache = keys; done(); }, function(e){ out.cache = ['errore:' + e]; done(); });
    })();"""
  }
}
