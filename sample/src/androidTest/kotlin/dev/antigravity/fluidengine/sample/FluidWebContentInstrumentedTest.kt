package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.catalog.webContentHostileSamples
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.ByteArrayInputStream
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prove reali Chromium. Il server registra anche una fuga che aggirasse il gestore dell'app. */
@RunWith(AndroidJUnit4::class)
class FluidWebContentInstrumentedTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var server: MockWebServer
  private val state = FluidWebContentState()
  private val seen = CopyOnWriteArrayList<FluidWebRequest>()
  private val links = CopyOnWriteArrayList<FluidWebLink>()

  @Before fun start() { server = MockWebServer(); server.start() }
  @After fun stop() { server.shutdown() }

  private fun show(html: String, request: (FluidWebRequest) -> FluidWebResponse = { FluidWebResponse.Block }) {
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        FluidMotionPolicyProvider(FluidMotionPolicy(0f)) {
          FluidWebContent(html, { links += it }, state = state,
            modifier = Modifier.fillMaxWidth().testTag("web"),
            onRequest = { seen += it; request(it) },
            sizing = FluidWebSizing.Viewport(400.dp), placeholder = { Text("placeholder") },
            failure = { Text("failure:$it") })
        }
      }
    }
    ready()
  }
  private fun ready() {
    compose.waitUntil(20_000) { state.phase == FluidWebPhase.Ready || state.phase == FluidWebPhase.Error }
    assertEquals("failure=${state.failure}; resources=$seen", FluidWebPhase.Ready, state.phase)
  }
  private fun web(): WebView = findWeb() ?: error("WebView assente")
  private fun findWeb(): WebView? {
    fun find(view: View): WebView? {
      if (view is WebView) return view
      if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
      return null
    }
    return find(compose.activity.window.decorView)
  }
  private fun waitForSettledResources() {
    // Finestra per refresh, error handlers e CSS asincroni; non si usa l'idle Compose come idle Chromium.
    val latch = CountDownLatch(1)
    Handler(Looper.getMainLooper()).postDelayed({ latch.countDown() }, 1500)
    assertTrue(latch.await(5, TimeUnit.SECONDS))
  }

  @androidx.annotation.OptIn(markerClass = [WebSettingsCompat.ExperimentalSpeculativeLoading::class])
  @Test fun hostileDocumentCannotSendRequestsOrExecuteScripts() {
    // Un socket in ascolto al posto di MockWebServer: conta anche le connessioni senza richiesta HTTP
    // (preconnect), che `requestCount` non vede.
    SocketWatch().use { watch ->
      val html = webContentHostileSamples().getValue("HTML ostile")
        .replace("https://tracker.invalid", watch.target.https)
        .replace("//tracker.invalid", "//${watch.target.authority}")
      assertFalse(html.contains("tracker.invalid"))
      show(html)
      waitForSettledResources()
      compose.runOnIdle {
        val web = web()
        assertFalse(web.settings.javaScriptEnabled)
        assertFalse(web.settings.allowFileAccess)
        assertFalse(web.settings.allowContentAccess)
        assertTrue(web.settings.blockNetworkLoads)
        assertFalse(web.settings.safeBrowsingEnabled)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SPECULATIVE_LOADING)) {
          assertEquals(WebSettingsCompat.SPECULATIVE_LOADING_DISABLED, WebSettingsCompat.getSpeculativeLoadingStatus(web.settings))
        }
        assertEquals("SAFE", web.title)
        assertFalse(WebViewCompat.getProfile(web).cookieManager.acceptCookie())
        assertNotNull(state.printAdapter("Documento"))
      }
      watch.settle(500)
      assertTrue("Connessioni native verso il tracker: ${watch.connections}; onRequest=${seen.map { it.url }}", watch.connections.isEmpty())
      assertTrue(seen.any { it.url.path == "/pixel" })
      assertTrue(seen.none { it.url.path?.contains("script") == true })
    }
  }

  @Test fun allowedStreamsUseOnlyAppHeadersAndInlineResourcesUseSameHook() {
    repeat(2) { server.enqueue(MockResponse().setBody("<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20'/>")
      .setHeader("Set-Cookie", "secret=must-not-return").setHeader("Connection", "close")) }
    show("<title>SAFE</title><img src='https://resource.invalid/one'><img src='https://resource.invalid/two'><img src='cid:part'>") { resource ->
      when (resource.url.scheme) {
        "cid" -> FluidWebResponse.Serve("image/svg+xml", "UTF-8") {
          ByteArrayInputStream("<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20'/>".toByteArray())
        }
        "https" -> {
          // Client dell'app di prova: nessun client di rete viene fornito dall'engine.
          val bytes = Socket(server.hostName, server.port).use { socket ->
            socket.soTimeout = 5000
            val headers = resource.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
            socket.getOutputStream().write(("GET ${resource.url.path} HTTP/1.1\r\nHost: localhost\r\n" + headers + "Connection: close\r\n\r\n").toByteArray())
            val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            raw.substringAfter("\r\n\r\n").toByteArray()
          }
          FluidWebResponse.Serve("image/svg+xml", "UTF-8") { ByteArrayInputStream(bytes) }
        }
        else -> FluidWebResponse.Block
      }
    }
    compose.waitUntil(5_000) { server.requestCount == 2 && seen.any { it.url.scheme == "cid" } }
    repeat(2) {
      val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
      assertNull(recorded.getHeader("Cookie"))
      assertNull(recorded.getHeader("Referer"))
      assertEquals(FluidWebContentDefaults.UserAgent, recorded.getHeader("User-Agent"))
    }
    waitForSettledResources()
    assertEquals(2, server.requestCount)
    compose.runOnIdle { assertFalse(WebViewCompat.getProfile(web()).cookieManager.hasCookies()) }
  }

  @Test fun tapsReportRealDestinationAndVisibleTextWithoutNavigation() {
    show("<meta name='viewport' content='width=device-width'><title>SAFE</title>" +
      "<a style='display:block;height:120px' href='${server.url("/destination")}'>https://display.invalid</a>")
    var root: String? = null
    compose.runOnIdle { root = web().url }
    compose.onNodeWithTag("web").performTouchInput { click(Offset(80f, 50f)) }
    compose.waitUntil(5_000) { links.isNotEmpty() }
    assertEquals(server.url("/destination").toString(), links.single().url)
    assertEquals("https://display.invalid", links.single().visibleText)
    compose.runOnIdle { assertEquals(root, web().url); assertEquals("SAFE", web().title) }
    assertEquals(0, server.requestCount)
  }

  @Test fun javascriptUrlTapCannotExecute() {
    show("<meta name='viewport' content='width=device-width'><title>SAFE</title>" +
      "<a style='display:block;height:120px' href=\"javascript:document.title='EXECUTED'\">Script</a>")
    compose.onNodeWithTag("web").performTouchInput { click(Offset(80f, 50f)) }
    waitForSettledResources()
    compose.runOnIdle { assertEquals("SAFE", web().title) }
    // Con JS disabilitato Chromium puo' ignorare del tutto l'ancora javascript:.
    assertTrue(links.all { it.url.startsWith("javascript:") })
    assertEquals(0, server.requestCount)
  }

  @Test fun themeRecreatesViewAndFontScaleUpdatesWithoutResourceReload() {
    var dark by mutableStateOf(false)
    var font by mutableStateOf(1f)
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
          FluidWebContent("<title>SAFE</title><body><p>Testo</p><img src='https://resource.invalid/image'>", {},
            state = state, darkening = dark, sizing = FluidWebSizing.Viewport(400.dp), placeholder = {},
            onRequest = { seen += it; FluidWebResponse.Block })
        }
      }
    }
    ready()
    waitForSettledResources()
    val count = seen.size
    lateinit var original: WebView
    compose.runOnIdle { original = web(); font = 2f }
    compose.waitForIdle()
    compose.runOnIdle {
      assertSame(original, web())
      assertEquals(200, web().settings.textZoom)
    }
    waitForSettledResources()
    assertEquals(count, seen.size)
    compose.runOnIdle { dark = true }
    compose.waitForIdle()
    ready()
    compose.runOnIdle {
      assertNotSame(original, web())
      assertTrue(WebSettingsCompat.isAlgorithmicDarkeningAllowed(web().settings))
      assertEquals(200, web().settings.textZoom)
      original = web()
      dark = false
    }
    compose.waitForIdle()
    ready()
    compose.runOnIdle {
      assertNotSame(original, web())
      assertFalse(WebSettingsCompat.isAlgorithmicDarkeningAllowed(web().settings))
    }
  }

  /** Difetto 1 della verifica F1: link toccato (la WebView prende il fuoco Android), poi la vista
   * viene staccata mentre Compose applica la composizione, dentro una LazyColumn. Prima della
   * correzione: ComposeRuntimeError "pending composition has not been applied" e processo chiuso.
   */
  @Test fun focusedViewInsideLazyColumnSurvivesRecreationAndRemoval() {
    val html = "<meta name='viewport' content='width=device-width'><title>SAFE</title>" +
      "<a style='display:block;height:120px' href='https://example.org/vero'>https://altro.example</a>"
    var dark by mutableStateOf(false)
    var document by mutableStateOf(html)
    var shown by mutableStateOf(true)
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT), systemBars = false) {
        LazyColumn(Modifier.fillMaxSize()) {
          item { Text("Sopra") }
          item {
            Column {
              if (shown) {
                FluidWebContent(document, { links += it }, state = state, darkening = dark,
                  modifier = Modifier.fillMaxWidth().testTag("web"), sizing = FluidWebSizing.WrapContent(400.dp),
                  placeholder = { Text("placeholder") })
              }
              Text("Sotto")
            }
          }
        }
      }
    }
    fun focusByTappingLink() {
      ready()
      val before = links.size
      compose.onNodeWithTag("web").performTouchInput { click(Offset(80f, 50f)) }
      compose.waitUntil(5_000) { links.size > before }
      assertEquals("https://example.org/vero", links.last().url)
      compose.runOnIdle { assertTrue("Il tocco deve dare il fuoco Android alla WebView", web().hasFocus()) }
    }
    // Sequenza della galleria: link, poi tema scuro. Darkening ricrea la vista, e la nuova vista prende da
    // ClosedProfiles.isolate un profilo tutto suo: uno gia' usato e pulito, se ce n'e' uno, altrimenti uno nuovo.
    // Mai quello del documento ancora aperto; il test controlla solo la vista nuova.
    focusByTappingLink()
    lateinit var old: WebView
    compose.runOnIdle { old = web(); dark = true }
    compose.waitForIdle()
    focusByTappingLink()
    compose.runOnIdle { assertNotSame(old, web()); old = web(); document = "$html<p>Nuovo</p>" }
    compose.waitForIdle()
    focusByTappingLink()
    compose.runOnIdle { assertNotSame(old, web()); shown = false }
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals(FluidWebPhase.Empty, state.phase)
      assertNull(findWeb())
    }
  }

  @Test fun rendererDeathIsReportedAndRetryUsesNewViewAndProfile() {
    show("<title>SAFE</title><h1>Documento</h1>")
    lateinit var old: WebView
    lateinit var oldProfile: String
    compose.runOnIdle {
      old = web()
      oldProfile = WebViewCompat.getProfile(old).name
      assertTrue(old.webViewRenderProcess!!.terminate())
    }
    compose.waitUntil(10_000) { state.failure == FluidWebFailure.RendererGone }
    assertEquals(FluidWebPhase.Error, state.phase)
    assertNull(state.printAdapter("Documento"))
    compose.runOnIdle { state.retry() }
    // retry invalida Compose: non leggere il vecchio Error prima del nuovo attach.
    compose.waitUntil(10_000) { state.phase != FluidWebPhase.Error }
    ready()
    compose.runOnIdle {
      assertNotSame(old, web())
      assertNotEquals(oldProfile, WebViewCompat.getProfile(web()).name)
      assertEquals("SAFE", web().title)
    }
  }

  @Test fun hugeMalformedContentHasBoundedHeightAtTwoHundredPercentAndCanShrink() {
    var html by mutableStateOf(webContentHostileSamples().getValue("Lungo e malformato"))
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = ThemeMode.DARK), systemBars = false) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
          FluidMotionPolicyProvider(FluidMotionPolicy(0f)) {
            LazyColumn {
              item {
                FluidWebContent(html, {}, state = state, darkening = true,
                  modifier = Modifier.fillMaxWidth().testTag("hugeweb"),
                  sizing = FluidWebSizing.WrapContent(500.dp), placeholder = { Text("placeholder") })
              }
            }
          }
        }
      }
    }
    ready()
    compose.waitUntil(10_000) { state.scrollsInternally && state.contentHeightPx > 4096 }
    compose.onNodeWithTag("hugeweb").performTouchInput { swipeUp(durationMillis = 500) }
    compose.runOnIdle {
      assertTrue(web().height <= 4096)
      assertTrue("Lo scroll deve raggiungere la WebView dentro LazyColumn", web().scrollY > 0)
      assertEquals(200, web().settings.textZoom)
      html = "<title>SHORT</title><p>Breve</p>"
    }
    compose.waitUntil(15_000) { state.phase == FluidWebPhase.Ready && !state.scrollsInternally && state.contentHeightPx in 1..1000 }
    val settledHeight = state.contentHeightPx
    waitForSettledResources()
    assertEquals("La viewport non deve crescere a ogni ridisegno", settledHeight, state.contentHeightPx)
  }

  @Test fun glassBackdropActuallyContainsWebViewPixels() {
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize().background(ComposeColor.White)) {
          Box(Modifier.fillMaxWidth().height(400.dp).glassBackdropSource(backdrop)) {
            FluidWebContent("<meta name='viewport' content='width=device-width'><style>body{margin:0;background:rgb(255,0,0);height:1000px}</style>",
              {}, state = state, sizing = FluidWebSizing.Viewport(400.dp), placeholder = {})
          }
          // Copre il rosso originale: un vetro che campionasse vuoto non puo' passare il test
          // semplicemente lasciando trasparire la WebView sottostante.
          Box(Modifier.padding(top = 60.dp).fillMaxWidth().height(100.dp).background(ComposeColor.White))
          Box(Modifier.padding(top = 60.dp).fillMaxWidth().height(100.dp)
            .glassSurface(backdrop, GlassDefaults.barTint(), role = GlassRole.Bar))
        }
      }
    }
    ready()
    waitForSettledResources()
    val bitmap = Bitmap.createBitmap(compose.activity.window.decorView.width, compose.activity.window.decorView.height, Bitmap.Config.ARGB_8888)
    val latch = CountDownLatch(1)
    var result = -1
    compose.runOnIdle { PixelCopy.request(compose.activity.window, bitmap, { result = it; latch.countDown() }, Handler(Looper.getMainLooper())) }
    assertTrue(latch.await(5, TimeUnit.SECONDS))
    assertEquals(PixelCopy.SUCCESS, result)
    var x = 0
    var y = 0
    compose.runOnIdle {
      val location = IntArray(2); web().getLocationInWindow(location)
      x = location[0] + web().width / 2
      y = location[1] + (100 * compose.activity.resources.displayMetrics.density).toInt()
    }
    val pixel = bitmap.getPixel(x, y)
    android.util.Log.i("FluidWebGlass", "pixel sotto la barra: ${Integer.toHexString(pixel)}")
    // Se il draw functor non entra nel GraphicsLayer, questo punto e' bianco/grigio anziche' rosso.
    assertTrue("Il backdrop non contiene il rosso WebView: ${Integer.toHexString(pixel)}", Color.red(pixel) > Color.green(pixel) + 40)
    bitmap.recycle()
  }
}
