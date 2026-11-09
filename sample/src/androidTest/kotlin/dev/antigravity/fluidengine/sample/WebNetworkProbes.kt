package dev.antigravity.fluidengine.sample

import android.app.Activity
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import org.json.JSONTokener

/** Socket locale in ascolto: registra ogni connessione TCP, anche quelle senza una richiesta HTTP.
 *
 * Un `preconnect` apre la connessione e manda al piu' un ClientHello: MockWebServer non lo conta come
 * richiesta, questo si'. Le prove usano l'indirizzo 127.0.0.1, quindi non dipendono dal DNS.
 */
internal class SocketWatch : AutoCloseable {
  private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 100 }
  private val running = AtomicBoolean(true)
  private val lastAccept = AtomicLong(0)
  val connections = CopyOnWriteArrayList<String>()
  val target = WebTarget("https://127.0.0.1:${server.localPort}", "http://127.0.0.1:${server.localPort}", "127.0.0.1:${server.localPort}")
  private val thread = Thread {
    while (running.get()) {
      try {
        server.accept().use { socket ->
          lastAccept.set(System.nanoTime())
          socket.soTimeout = 300
          var first = "nessun byte"
          try {
            val prefix = ByteArray(8)
            val count = socket.getInputStream().read(prefix)
            first = if (count > 0) prefix.take(count).joinToString("") { "%02x".format(it.toInt() and 255) } else "chiusa"
          } catch (_: SocketTimeoutException) {
          } catch (_: IOException) {
          }
          connections += first
        }
      } catch (_: SocketTimeoutException) {
      } catch (_: IOException) {
      }
    }
  }.apply { isDaemon = true; start() }

  /** Aspetta [quietMillis] e poi finche' il socket non resta fermo: le connessioni in coda vengono contate tutte. */
  fun settle(quietMillis: Long = 1500) {
    Thread.sleep(quietMillis)
    while (System.nanoTime() - lastAccept.get() < 500_000_000L) Thread.sleep(100)
  }

  override fun close() {
    running.set(false)
    thread.join(1500)
    server.close()
  }
}

/** Numero totale di risoluzioni DNS viste dal sistema (`dumpsys connmetrics`), senza nomi: basta la differenza. */
internal object DnsCounter {
  private val event = Regex("""DnsEvent\(netId=\d+, transports=\{[^}]*\}, (\d+) events""")

  /** `null` se il sistema non espone il contatore: la prova allora non puo' concludere nulla. */
  fun read(): Int? {
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys connmetrics")
    val text = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    val counts = event.findAll(text).map { it.groupValues[1].toInt() }.toList()
    return if (counts.isEmpty()) null else counts.sum()
  }
}

/** Divide un ciclo di prove in piu' lanci: `-e webChunk 2/8` tiene le voci di posto 2, 10, 18...
 *
 * Ogni contenitore usa un profilo suo, e il provider tiene vivo un processo renderer per profilo fino alla fine
 * del processo dell'app. Decine di documenti nello stesso lancio esauriscono la memoria di un emulatore da 2 GB:
 * non e' un esito della prova, e' il motivo per dividerla. Senza l'argomento si eseguono tutte le voci.
 */
internal fun <T> List<T>.forThisRun(): List<T> {
  val spec = InstrumentationRegistry.getArguments().getString("webChunk") ?: return this
  val (index, count) = spec.split('/').map { it.trim().toInt() }
  return filterIndexed { position, _ -> position % count == index }
}

/** Il sigillo e' interno a engine-ui: le prove lo raggiungono cosi' per non aggiungere un'API pubblica. */
internal fun sealedHtml(html: String): String {
  val type = Class.forName("dev.antigravity.fluidengine.ui.fluid.FluidWebSeal")
  return type.getMethod("seal", String::class.java).invoke(type.getField("INSTANCE").get(null), html) as String
}

private const val LegacyCsp = "sandbox; script-src 'none'; object-src 'none'; frame-src 'none'; " +
  "child-src 'none'; form-action 'none'; base-uri 'none'; connect-src 'none'"

private fun blockedResponse() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
  mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(byteArrayOf()))

private var unsealedView: WebView? = null

/** WebView di sola prova con le chiusure della 3.1.0 e **senza** sigillo: e' la sentinella.
 *
 * Mostra che cosa farebbe Chromium con il documento cosi' com'e'. Se qui un `preconnect` non apre piu'
 * socket, il metodo d'osservazione non vale e gli zero del contenitore non provano nulla.
 */
internal fun loadUnsealed(activity: Activity, html: String): WebView {
  val painted = CountDownLatch(1)
  lateinit var web: WebView
  activity.runOnUiThread {
    // Una sola vista di controllo viva per volta: le prove ne caricano decine di seguito.
    unsealedView?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }
    web = WebView(activity)
    unsealedView = web
    web.settings.apply {
      javaScriptEnabled = false
      blockNetworkLoads = true
      cacheMode = WebSettings.LOAD_NO_CACHE
      safeBrowsingEnabled = false
    }
    web.setNetworkAvailable(false)
    val root = "https://${UUID.randomUUID()}.invalid/document"
    val bytes = html.toByteArray(Charsets.UTF_8)
    web.webViewClient = object : WebViewClient() {
      override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
        if (request.isForMainFrame && request.url.toString() == root) {
          WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store",
            "Content-Security-Policy" to LegacyCsp, "X-Content-Type-Options" to "nosniff"), ByteArrayInputStream(bytes))
        } else blockedResponse()
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
      override fun onPageFinished(view: WebView, url: String) { if (url == root) painted.countDown() }
    }
    activity.setContentView(web)
    web.loadUrl(root)
  }
  check(painted.await(20, TimeUnit.SECONDS)) { "La WebView di controllo non ha finito di caricare" }
  return web
}

/** Il vero analizzatore di Chromium, interrogato sul DOM che costruisce da un testo.
 *
 * Il documento si carica con JavaScript spento, come nel contenitore (cosi' `<noscript>` e' markup e nessuno
 * script del documento gira); solo dopo si accende JavaScript per leggere l'albero. Niente CSP: gli iframe
 * `srcdoc` qui si caricano, e la prova li conta.
 */
internal class ChromiumParser(private val activity: Activity) {
  private lateinit var web: WebView
  @Volatile private var root = ""
  @Volatile private var bytes = ByteArray(0)
  @Volatile private var finished = CountDownLatch(1)

  init {
    val ready = CountDownLatch(1)
    activity.runOnUiThread {
      web = WebView(activity)
      web.settings.blockNetworkLoads = true
      web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
      web.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
          if (request.isForMainFrame && request.url.toString() == root) {
            WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(bytes))
          } else blockedResponse()
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
        override fun onPageFinished(view: WebView, url: String) { if (url == root) finished.countDown() }
      }
      activity.setContentView(web)
      ready.countDown()
    }
    check(ready.await(20, TimeUnit.SECONDS))
  }

  /** Elementi `link` e `meta` dell'albero e numero di `iframe`, compresi `template`, radici ombra aperte e documenti annidati. */
  fun inspect(html: String): JSONObject = JSONObject(evaluate(html, Collector))

  /** Nomi dei figli di `head` e `body`, per confrontare la forma dell'albero. */
  fun shape(html: String): JSONObject = JSONObject(evaluate(html, Shape))

  private fun evaluate(html: String, script: String): String {
    finished = CountDownLatch(1)
    bytes = html.toByteArray(Charsets.UTF_8)
    root = "https://${UUID.randomUUID()}.invalid/document"
    activity.runOnUiThread {
      web.settings.javaScriptEnabled = false
      web.loadUrl(root)
    }
    check(finished.await(20, TimeUnit.SECONDS)) { "Il documento di prova non ha finito di caricare" }
    val answered = CountDownLatch(1)
    var answer: String? = null
    activity.runOnUiThread {
      web.settings.javaScriptEnabled = true
      web.evaluateJavascript(script) { answer = it; answered.countDown() }
    }
    check(answered.await(20, TimeUnit.SECONDS)) { "Nessuna risposta dal renderer" }
    val text = JSONTokener(answer ?: "null").nextValue()
    check(text is String) { "JavaScript non ha potuto leggere il documento: $answer" }
    return text
  }

  private companion object {
    const val Collector = """(function(){
      var out = {links: [], metas: [], frames: 0, elements: 0};
      function attributes(e){ var o = {}; for (var i = 0; i < e.attributes.length; i++) o[e.attributes[i].name] = e.attributes[i].value; return o; }
      function walk(root){
        var all = root.querySelectorAll('*');
        for (var i = 0; i < all.length; i++){
          var e = all[i]; out.elements++;
          if (e.namespaceURI === 'http://www.w3.org/1999/xhtml'){
            var n = e.localName;
            if (n === 'link') out.links.push(attributes(e));
            else if (n === 'meta') out.metas.push(attributes(e));
            else if (n === 'iframe' || n === 'frame' || n === 'object' || n === 'embed' || n === 'fencedframe' || n === 'portal'){
              // Solo iframe ha srcdoc, cioe' un documento scritto dentro il documento: gli altri hanno un indirizzo,
              // che nel contenitore la CSP rifiuta (frame-src, object-src) e che il censimento prova coi socket.
              if (n === 'iframe') out.frames++;
              try { if (e.contentDocument) walk(e.contentDocument); } catch (x) {}
            }
            else if (n === 'template') walk(e.content);
          }
          if (e.shadowRoot) walk(e.shadowRoot);
        }
      }
      walk(document);
      return JSON.stringify(out);
    })()"""

    const val Shape = """(function(){
      function names(e){ var o = []; if (e) for (var i = 0; i < e.children.length; i++) o.push(e.children[i].localName + (e.children[i].children.length ? '+' : '')); return o; }
      return JSON.stringify({head: names(document.head), body: names(document.body), title: document.title});
    })()"""
  }
}
