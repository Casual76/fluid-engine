package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Il sigillo contro il vero Chromium: socket, DNS e l'albero che l'analizzatore costruisce davvero.
 *
 * I test JVM provano che nel testo non resta un `<link` scritto dal documento. Questi provano che Chromium,
 * coi suoi due analizzatori (albero e scanner di precaricamento), non ne vede altri.
 */
@RunWith(AndroidJUnit4::class)
class FluidWebContentSealInstrumentedTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val state = FluidWebContentState()
  private val requests = CopyOnWriteArrayList<String>()
  private var html by mutableStateOf("")

  /** Un solo contenitore per tutta la prova: ogni documento nuovo ricrea vista e profilo, come nell'app. */
  private fun mount(request: (FluidWebRequest) -> FluidWebResponse = { FluidWebResponse.Block }) {
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        FluidWebContent(html, {}, state = state, modifier = Modifier.fillMaxWidth(), sizing = FluidWebSizing.Viewport(300.dp),
          placeholder = {}, onRequest = { requests += it.url.toString(); request(it) })
      }
    }
  }

  private fun show(document: String) {
    compose.runOnIdle { html = document }
    compose.waitForIdle()
    compose.waitUntil(30_000) { state.phase == FluidWebPhase.Ready || state.phase == FluidWebPhase.Error }
    assertEquals("${state.failure}", FluidWebPhase.Ready, state.phase)
  }

  // ---------------------------------------------------------------------------------------------- socket

  /** Sentinella: senza sigillo, con le sole chiusure della 3.1.0, il suggerimento apre socket veri. */
  @Test fun sentinelUnsealedPreconnectOpensNativeSockets() {
    SocketWatch().use { watch ->
      loadUnsealed(compose.activity, "<title>SAFE</title><link rel='preconnect' href='${watch.target.https}'><p>Sentinella</p>")
      watch.settle(2000)
      Log.i("FluidWebSeal", "sentinella socket=${watch.connections}")
      assertTrue("Senza sigillo un preconnect deve essere osservabile: altrimenti gli zero delle altre prove non provano nulla",
        watch.connections.isNotEmpty())
    }
  }

  @Test fun disguisedHintsOpenNoNativeSocket() {
    mount()
    val leaks = ArrayList<String>()
    for (variant in webNetworkDisguises().forThisRun()) {
      SocketWatch().use { watch ->
        show(variant.html(watch.target))
        watch.settle(1200)
        Log.i("FluidWebSeal", "variante=${variant.name} socket=${watch.connections.size} primiByte=${watch.connections}")
        if (watch.connections.isNotEmpty()) leaks += "${variant.name}=${watch.connections}"
      }
    }
    assertTrue("Varianti che hanno aperto connessioni native: $leaks", leaks.isEmpty())
  }

  /** Senza sigillo le stesse varianti: dice quali sono attacchi veri su questo Chromium. Non e' una garanzia, e' la misura. */
  @Test fun unsealedDisguisesAreMeasuredOnThisProvider() {
    val live = ArrayList<String>()
    for (variant in webNetworkDisguises()) {
      SocketWatch().use { watch ->
        loadUnsealed(compose.activity, variant.html(watch.target))
        watch.settle(1200)
        Log.i("FluidWebSeal", "senzaSigillo variante=${variant.name} socket=${watch.connections.size}")
        if (watch.connections.isNotEmpty()) live += variant.name
      }
    }
    Log.i("FluidWebSeal", "senzaSigillo attive=${live.size} $live")
    // Le varianti `assumption_` non sono elementi link per l'HTML: se una apre un socket, il fatto su cui il
    // sigillo si appoggia non vale piu' su questo provider.
    assertEquals("Chromium tratta come link sequenze che non lo sono", emptyList<String>(), live.filter { it.startsWith("assumption_") })
    assertTrue("Il corpus deve contenere attacchi veri: $live", live.size >= 10)
  }

  // ---------------------------------------------------------------------------------------------- albero

  private fun hintElements(dom: JSONObject): List<String> {
    val found = ArrayList<String>()
    val links = dom.getJSONArray("links")
    for (index in 0 until links.length()) {
      val link = links.getJSONObject(index)
      val names = link.keys().asSequence().toSet()
      if (link.optString("rel") != "stylesheet" || !setOf("rel", "href", "media", "title").containsAll(names)) found += "link$link"
    }
    val metas = dom.getJSONArray("metas")
    for (index in 0 until metas.length()) {
      val meta = metas.getJSONObject(index)
      val names = meta.keys().asSequence().toSet()
      if (names != setOf("name", "content") || meta.optString("name") !in setOf("viewport", "color-scheme")) found += "meta$meta"
    }
    if (dom.getInt("frames") > 0) found += "frames=${dom.getInt("frames")}"
    return found
  }

  @Test fun chromiumFindsNoHintElementInSealedDocuments() {
    val parser = ChromiumParser(compose.activity)
    val target = WebTarget("https://tracker.invalid", "http://tracker.invalid", "tracker.invalid")
    val documents = (webNetworkDisguises() + webNetworkCensus()).associate { it.name to it.html(target) } +
      mapOf("corpus_ostile_della_galleria" to dev.antigravity.fluidengine.sample.catalog.webContentHostileSamples().getValue("HTML ostile"))
    val survivors = ArrayList<String>()
    var liveBefore = 0
    for ((name, document) in documents) {
      val before = hintElements(parser.inspect(document))
      val after = hintElements(parser.inspect(sealedHtml(document)))
      if (before.isNotEmpty()) liveBefore++
      Log.i("FluidWebSeal", "albero documento=$name prima=${before.size} dopo=${after.size} $after")
      if (after.isNotEmpty()) survivors += "$name=$after"
    }
    Log.i("FluidWebSeal", "albero documenti=${documents.size} conElementiPrima=$liveBefore")
    assertTrue("Elementi sopravvissuti al sigillo nel DOM di Chromium: $survivors", survivors.isEmpty())
    assertTrue("L'analizzatore di prova deve vedere gli elementi nei documenti originali: $liveBefore", liveBefore >= 30)
  }

  /** Il fatto su cui il sigillo si appoggia, chiesto a Chromium: queste sequenze non creano l'elemento. */
  @Test fun chromiumCreatesTheElementOnlyFromTheLiteralName() {
    val parser = ChromiumParser(compose.activity)
    val target = WebTarget("https://tracker.invalid", "http://tracker.invalid", "tracker.invalid")
    for (variant in webNetworkDisguises().filter { it.name.startsWith("assumption_") }) {
      val dom = parser.inspect(variant.html(target))
      assertEquals("${variant.name}: $dom", 0, dom.getJSONArray("links").length())
      assertEquals("${variant.name}: $dom", 0, dom.getJSONArray("metas").length())
      assertEquals("${variant.name}: $dom", 0, dom.getInt("frames"))
    }
    // Un nome che continua con una lettera o una cifra ASCII e' un altro elemento: e' questo, chiesto a Chromium, a
    // giustificare che il sigillo lo lasci intatto (`<metadata>` di un SVG in linea).
    for (document in listOf("<linkx rel=preconnect href=https://tracker.invalid>", "<link1 rel=stylesheet href=https://tracker.invalid/a.css>",
      "<LINKS rel=preconnect href=https://tracker.invalid>", "<svg><linkx rel=preconnect href=https://tracker.invalid/></svg>")) {
      assertEquals(document, 0, parser.inspect(document).getJSONArray("links").length())
    }
    for (document in listOf("<metadata http-equiv=refresh content=1>", "<metab http-equiv=refresh content=1>", "<meta1 http-equiv=refresh content=1>",
      "<METADATA http-equiv=x-dns-prefetch-control content=on>", "<svg><metadata http-equiv=refresh content=1></metadata></svg>")) {
      assertEquals(document, 0, parser.inspect(document).getJSONArray("metas").length())
    }
    for (document in listOf("<iframes srcdoc=x></iframes>", "<iframe1 srcdoc=x></iframe1>", "<IFRAMEX srcdoc='&lt;p&gt;x'></IFRAMEX>")) {
      assertEquals(document, 0, parser.inspect(document).getInt("frames"))
    }
    for (document in listOf("<m\u0000eta http-equiv=refresh content=1>", "<meta\u0000 http-equiv=refresh content=1>",
      "<\u0000meta http-equiv=refresh content=1>", "<me﻿ta http-equiv=refresh content=1>", "&lt;meta http-equiv=refresh content=1>")) {
      assertEquals(document, 0, parser.inspect(document).getJSONArray("metas").length())
    }
    for (document in listOf("<i\u0000frame srcdoc=x></iframe>", "<iframe\u0000 srcdoc=x></iframe>", "<\u0000iframe srcdoc=x>",
      "<ifr­ame srcdoc=x>", "&lt;iframe srcdoc=x>", "<ıframe srcdoc=x>")) {
      assertEquals(document, 0, parser.inspect(document).getInt("frames"))
    }
    // Controprova: l'analizzatore di prova vede gli elementi veri, anche annidati.
    val nested = parser.inspect("<iframe srcdoc=\"&lt;link rel=preconnect href=https://tracker.invalid&gt;\"></iframe>")
    assertEquals(nested.toString(), 1, nested.getInt("frames"))
    assertEquals(nested.toString(), 1, nested.getJSONArray("links").length())
  }

  @Test fun sealedDocumentKeepsTheTreeShapeAndItsUsefulHead() {
    val parser = ChromiumParser(compose.activity)
    val document = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'>" +
      "<title>Notizie</title><link rel='preconnect' href='https://fonts.invalid' crossorigin>" +
      "<link href='https://fonts.invalid/css2?family=Inter&display=swap' rel='stylesheet'><style>p{margin:0}</style></head>" +
      "<body><p>Uno</p><link rel='prefetch' href='https://tracker.invalid/next'><iframe src='https://tracker.invalid/f'>ripiego</iframe><p>Due</p></body></html>"
    val original = parser.shape(document)
    val sealed = parser.shape(sealedHtml(document))
    Log.i("FluidWebSeal", "forma prima=$original dopo=$sealed")
    assertEquals("Notizie", sealed.getString("title"))
    // Gli elementi neutralizzati restano vuoti e al loro posto: head non si chiude prima, body non cambia figli.
    assertEquals("[\"basefont\",\"meta\",\"basefont\",\"title\",\"basefont\",\"link\",\"basefont\",\"style\"]", sealed.getJSONArray("head").toString())
    assertEquals("[\"p\",\"basefont\",\"noframes\",\"p\"]", sealed.getJSONArray("body").toString())
    assertEquals("[\"meta\",\"meta\",\"title\",\"link\",\"link\",\"style\"]", original.getJSONArray("head").toString())
    assertEquals("[\"p\",\"link\",\"iframe\",\"p\"]", original.getJSONArray("body").toString())
    val dom = parser.inspect(sealedHtml(document))
    assertEquals("https://fonts.invalid/css2?family=Inter&display=swap", dom.getJSONArray("links").getJSONObject(0).getString("href"))
    assertEquals("width=device-width", dom.getJSONArray("metas").getJSONObject(0).getString("content"))
  }

  // ---------------------------------------------------------------------------------------------- SVG con <metadata>

  /** Rilievo A di K1b. `<metadata>` non e' `<meta>`: sigillato, l'SVG ha ancora la sua forma come figlia, e la forma occupa spazio.
   *
   * Su K1 `<metadata>` diventava `<basefontdata>` e `</metadata>` restava: il tag non si chiudeva piu', la forma finiva
   * dentro un elemento SVG sconosciuto e non veniva disegnata.
   */
  @Test fun inlineSvgKeepsItsShapesAfterMetadata() {
    val parser = ChromiumParser(compose.activity)
    val original = parser.shapeElement(SvgWithMetadata)
    val sealed = parser.shapeElement(sealedHtml(SvgWithMetadata))
    Log.i("FluidWebSeal", "svgMetadata prima=$original dopo=$sealed")
    // Controprova: nel documento originale la forma c'e' ed e' disegnata.
    assertEquals(original.toString(), "svg", original.getString("parent"))
    assertTrue(original.toString(), original.getDouble("width") > 0 && original.getDouble("height") > 0)
    assertTrue(sealed.toString(), sealed.getBoolean("found") && sealed.getBoolean("svg"))
    assertEquals("La forma deve restare figlia dell'SVG: $sealed", "svg", sealed.getString("parent"))
    assertTrue("La forma deve occupare spazio: $sealed", sealed.getDouble("width") > 0 && sealed.getDouble("height") > 0)
    val names = sealed.getJSONArray("elements").toString()
    assertTrue(names, names.contains("\"metadata\"") && !names.contains("basefontdata"))
  }

  private fun findWeb(view: View): WebView? {
    if (view is WebView) return view
    if (view is ViewGroup) for (index in 0 until view.childCount) findWeb(view.getChildAt(index))?.let { return it }
    return null
  }

  /** Lo stesso documento nel contenitore vero: il rettangolo rosso dopo `<metadata>` arriva sullo schermo. */
  @Test fun inlineSvgWithMetadataIsStillDrawnByTheContainer() {
    mount()
    show(SvgWithMetadata)
    var x = 0
    var y = 0
    compose.runOnIdle {
      val web = findWeb(compose.activity.window.decorView) ?: error("WebView assente")
      val location = IntArray(2); web.getLocationInWindow(location)
      val density = compose.activity.resources.displayMetrics.density
      x = location[0] + (100 * density).toInt()
      y = location[1] + (100 * density).toInt()
    }
    var pixel = 0
    val deadline = System.currentTimeMillis() + 8_000
    do {
      val decor = compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
      val latch = CountDownLatch(1)
      compose.runOnIdle { PixelCopy.request(compose.activity.window, bitmap, { latch.countDown() }, Handler(Looper.getMainLooper())) }
      assertTrue(latch.await(5, TimeUnit.SECONDS))
      pixel = bitmap.getPixel(x, y)
      bitmap.recycle()
      if (Color.red(pixel) > 200 && Color.green(pixel) < 60) break
      Thread.sleep(250)
    } while (System.currentTimeMillis() < deadline)
    Log.i("FluidWebSeal", "svgMetadata pixel=${Integer.toHexString(pixel)}")
    assertTrue("La forma dopo <metadata> non e' disegnata: pixel ${Integer.toHexString(pixel)}",
      Color.red(pixel) > 200 && Color.green(pixel) < 60 && Color.blue(pixel) < 60)
  }

  // ---------------------------------------------------------------------------------------------- risorse dell'app

  /** Un foglio di stile esterno servito dall'app continua ad applicarsi, e il suggerimento accanto no. */
  @Test fun stylesheetServedByTheAppStillApplies() {
    SocketWatch().use { watch ->
      mount { request ->
        if (request.url.host == "resource.invalid" && request.url.path == "/a.css") {
          FluidWebResponse.Serve("text/css", "UTF-8") { ByteArrayInputStream("div.tall{height:3000px}".toByteArray()) }
        } else FluidWebResponse.Block
      }
      show("<meta name='viewport' content='width=device-width'><title>SAFE</title>" +
        "<link rel='preconnect' href='${watch.target.https}'>" +
        "<link rel=\"stylesheet\" type=\"text/css\" href=\"https://resource.invalid/a.css?x=1&amp;y=2\" media=\"screen\">" +
        "<div class='tall'>Alto solo con il foglio di stile</div>")
      compose.waitUntil(10_000) { state.contentHeightPx > 3000 }
      assertTrue("$requests", "https://resource.invalid/a.css?x=1&y=2" in requests)
      watch.settle(1200)
      assertTrue("${watch.connections}", watch.connections.isEmpty())
    }
  }

  // ---------------------------------------------------------------------------------------------- DNS

  private fun freshHost() = "c1-${UUID.randomUUID().toString().take(13)}.invalid"

  /** Differenza del contatore di sistema durante [action]; -1 se il contatore non e' leggibile. */
  private fun resolutionsDuring(action: () -> Unit): Int {
    val before = DnsCounter.read() ?: return -1
    action()
    Thread.sleep(1500)
    return (DnsCounter.read() ?: return -1) - before
  }

  /** Sentinella DNS: senza sigillo un `dns-prefetch` verso un nome nuovo fa salire il contatore di sistema. */
  @Test fun sentinelUnsealedDnsPrefetchIsCountedBySystem() {
    assumeTrue("dumpsys connmetrics non espone il contatore DNS", DnsCounter.read() != null)
    val deltas = (1..2).map {
      val host = freshHost()
      resolutionsDuring { loadUnsealed(compose.activity, "<title>SAFE</title><link rel='dns-prefetch' href='//$host'><p>Sentinella DNS</p>") }
    }
    Log.i("FluidWebSeal", "sentinellaDns differenze=$deltas")
    assertTrue("Senza sigillo il dns-prefetch deve essere osservabile: $deltas", deltas.all { it >= 1 })
  }

  @Test fun documentCannotCauseNameResolution() {
    assumeTrue("dumpsys connmetrics non espone il contatore DNS", DnsCounter.read() != null)
    mount()
    show("<title>SAFE</title><p>Riscaldamento: il primo avvio del provider puo' risolvere nomi suoi.</p>")
    Thread.sleep(2000)
    val leaks = ArrayList<String>()
    for ((name, document) in webDnsChannels().forThisRun()) {
      // Altre app risolvono nomi quando vogliono: una fuga vera si ripete a ogni tentativo, il rumore no.
      val deltas = ArrayList<Int>()
      for (attempt in 1..3) {
        val delta = resolutionsDuring { show(document(freshHost())) }
        deltas += delta
        if (delta == 0) break
      }
      Log.i("FluidWebSeal", "dns canale=$name differenze=$deltas")
      if (deltas.none { it == 0 }) leaks += "$name=$deltas"
    }
    assertTrue("Canali che hanno fatto risolvere un nome: $leaks", leaks.isEmpty())
  }

  /** Le stesse voci senza sigillo: quali canali risolvono nomi su questo Chromium (misura, non garanzia). */
  @Test fun unsealedNameResolutionIsMeasuredOnThisProvider() {
    assumeTrue("dumpsys connmetrics non espone il contatore DNS", DnsCounter.read() != null)
    for ((name, document) in webDnsChannels()) {
      val deltas = (1..2).map { resolutionsDuring { loadUnsealed(compose.activity, document(freshHost())) } }
      Log.i("FluidWebSeal", "senzaSigillo dns canale=$name differenze=$deltas")
    }
  }
}
