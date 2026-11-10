package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Processi renderer di **questa** app vivi in questo momento, letti con i permessi della shell.
 *
 * Un renderer di WebView e' un servizio isolato del provider (`…:sandboxed_process…`) che il sistema registra fra i
 * servizi dell'app che lo usa: `dumpsys activity services <pacchetto>` dice quali processi sono suoi, `ps` dice
 * quali di quelli sono ancora vivi (il registro dei servizi puo' restare indietro di un istante). I renderer delle
 * altre app non entrano nel conto.
 */
internal object RendererCensus {
  class Snapshot(val count: Int, val rssKb: Long, val pids: List<String> = emptyList())

  private val owned = Regex("""app=ProcessRecord\{\w+ (\d+):\S*sandboxed_process""")

  private fun shell(command: String): String {
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
  }

  fun read(): Snapshot {
    val application = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    val mine = owned.findAll(shell("dumpsys activity services $application")).map { it.groupValues[1] }.toSet()
    var count = 0
    var rss = 0L
    val pids = ArrayList<String>()
    for (line in shell("ps -A -o PID,RSS,NAME").lineSequence()) {
      val fields = line.trim().split(Regex("\\s+"), limit = 3)
      if (fields.size < 3 || fields[0] !in mine || "sandboxed_process" !in fields[2]) continue
      count++
      pids += fields[0]
      rss += fields[1].toLongOrNull() ?: 0L
    }
    return Snapshot(count, rss, pids)
  }
}

/** Rilievo B di K1b: un documento chiuso non lascia vivo il suo processo renderer.
 *
 * Ogni vista di [FluidWebContent] ha un profilo tutto suo finche' e' aperta, e il provider da' a ogni profilo un
 * processo renderer suo, che sopravvive a `destroy()`. Fino a K1 restava vivo finche' viveva l'app: una trentina di documenti esaurivano un
 * emulatore da 2 GB. Qui si aprono e si chiudono decine di documenti in un solo processo e si contano i processi.
 * Si contano anche i profili: restano caricati fino alla fine del processo (descrittori, thread, memoria), quindi
 * devono essere pochi e riusati, non uno per documento. Le misure sono nel log.
 *
 * Nessuna soglia di tempo: le attese sono solo limiti oltre i quali la prova dichiara il fallimento. I numeri di memoria
 * finiscono nel log `FluidWebLifecycle` come misure indicative, non come asserzioni.
 *
 * `-e webDocuments 100` cambia quanti documenti apre la prova principale (di serie 60).
 */
@RunWith(AndroidJUnit4::class)
class FluidWebContentRendererLifecycleTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private var scenario: ActivityScenario<ComponentActivity>? = null
  @Volatile private var activity: ComponentActivity? = null
  @Volatile private var state: FluidWebContentState? = null
  @Volatile private var host: WebView? = null
  private var sentinel: WebView? = null
  private var html by mutableStateOf("")
  private var dark by mutableStateOf(false)
  private var shown by mutableStateOf(true)
  private val hostGone = AtomicInteger()
  private val hostFinished = AtomicInteger()
  private val failures = CopyOnWriteArrayList<String>()
  private val requests = AtomicInteger()
  private val profiles = LinkedHashSet<String>()
  private var reuses = 0
  private var views = 0
  private var previous: WebView? = null

  @After fun close() {
    instrumentation.runOnMainSync { sentinel?.destroy(); sentinel = null }
    scenario?.close()
  }

  // ------------------------------------------------------------------------------------------------ strumenti

  private fun <T> onMain(block: () -> T): T {
    var result: Result<T>? = null
    instrumentation.runOnMainSync { result = runCatching(block) }
    return result!!.getOrThrow()
  }

  private fun await(what: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (!onMain(condition)) {
      if (SystemClock.uptimeMillis() > deadline) fail("Attesa scaduta: $what (fase=${state?.phase}, errore=${state?.failure}, fallimenti=$failures)")
      Thread.sleep(40)
    }
  }

  private fun hostClient() = object : WebViewClient() {
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
      hostGone.incrementAndGet()
      return true // La prova deve arrivare in fondo per poterlo dichiarare.
    }
    override fun onPageFinished(view: WebView, url: String) { hostFinished.incrementAndGet() }
  }

  /** Avvia l'Activity; con [withHost] una WebView ordinaria dell'app ospite (profilo predefinito) sta sopra il contenitore. */
  private fun mount(withHost: Boolean = true) {
    val launched = scenario ?: ActivityScenario.launch(ComponentActivity::class.java).also { scenario = it }
    launched.onActivity { current ->
      host?.let { old -> (old.parent as? ViewGroup)?.removeView(old); old.destroy() }
      val web = if (withHost) WebView(current).apply { webViewClient = hostClient() } else null
      val contentState = FluidWebContentState()
      host = web
      state = contentState
      activity = current
      previous = null
      current.setContent {
        FluidTheme(EngineSettings(), systemBars = false) {
          Column(Modifier.fillMaxSize()) {
            if (web != null) AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().height(80.dp))
            if (shown) {
              FluidWebContent(html, {}, state = contentState, darkening = dark, modifier = Modifier.fillMaxWidth(),
                sizing = FluidWebSizing.Viewport(300.dp), placeholder = {},
                failure = { reason -> LaunchedEffect(reason) { failures += "$reason" } },
                onRequest = { requests.incrementAndGet(); FluidWebResponse.Block })
            }
          }
        }
      }
    }
  }

  private fun containerWeb(): WebView? {
    fun find(view: View): WebView? {
      if (view is WebView) return view.takeIf { it !== host }
      if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
      return null
    }
    return activity?.window?.decorView?.let(::find)
  }

  private fun document(title: String) = "<meta name='viewport' content='width=device-width'><title>$title</title>" +
    "<link rel='stylesheet' href='https://resource.invalid/$title.css'><body><h1>$title</h1>" +
    "<p>Documento sintetico di prova, senza dati veri.</p><img src='https://resource.invalid/$title.png' width='40' height='40'>"

  /** Aspetta la vista nuova del contenitore; con [ready] anche la prima pittura del documento [title]. */
  private fun awaitView(title: String, ready: Boolean) {
    await("vista nuova per $title") { containerWeb()?.let { it !== previous } ?: false }
    onMain {
      val web = containerWeb()!!
      previous = web
      views++
      if (!profiles.add(WebViewCompat.getProfile(web).name)) reuses++
    }
    if (!ready) return
    await("prima pittura di $title") {
      val phase = state?.phase
      phase == FluidWebPhase.Error || (phase == FluidWebPhase.Ready && containerWeb()?.title == title)
    }
    assertEquals("$title: errore ${state?.failure}", FluidWebPhase.Ready, state?.phase)
  }

  private fun open(title: String, ready: Boolean = true) {
    onMain { html = document(title) }
    awaitView(title, ready)
  }

  private fun awaitRenderers(what: String, atMost: Int, timeoutMillis: Long = 40_000): Int {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    var count = RendererCensus.read().count
    while (count > atMost && SystemClock.uptimeMillis() < deadline) {
      Thread.sleep(250)
      count = RendererCensus.read().count
    }
    assertTrue("$what: $count processi renderer vivi, attesi al piu' $atMost", count <= atMost)
    return count
  }

  private fun directoryKb(directory: File): Long = directory.walkBottomUp().filter { it.isFile }.sumOf { it.length() } / 1024

  private fun registeredContentProfiles(): Set<String> =
    onMain { ProfileStore.getInstance().allProfileNames.filter { it.startsWith("fluid-content-") }.toSet() }

  private fun loadedContentProfiles(): Int = registeredContentProfiles().size

  private fun measure(label: String) {
    val renderers = RendererCensus.read()
    Runtime.getRuntime().gc()
    val context = instrumentation.targetContext
    val threads = File("/proc/self/status").readLines().firstOrNull { it.startsWith("Threads:") }?.filter(Char::isDigit) ?: "?"
    val free = File("/proc/meminfo").readLines().firstOrNull { it.startsWith("MemAvailable:") }?.filter(Char::isDigit) ?: "?"
    Log.i("FluidWebLifecycle", "misura=$label viste=$views profiliDistinti=${profiles.size} riusi=$reuses profiliNelRegistro=${loadedContentProfiles()} " +
      "renderer=${renderers.count} rendererRssKb=${renderers.rssKb} appPssKb=${Debug.getPss()} " +
      "appNativoKb=${Debug.getNativeHeapAllocatedSize() / 1024} thread=$threads descrittori=${File("/proc/self/fd").list()?.size ?: -1} " +
      "webviewSuDiscoKb=${directoryKb(File(context.dataDir, "app_webview"))} cacheSuDiscoKb=${directoryKb(context.cacheDir)} " +
      "memoriaLiberaKb=$free richieste=${requests.get()}")
  }

  /** Carica nella WebView ospite una pagina a tinta unita e controlla sullo schermo che sia stata disegnata. */
  private fun hostLoadsAndDraws(red: Int, green: Int, blue: Int) {
    val before = hostFinished.get()
    onMain { host!!.loadDataWithBaseURL(null, "<body style='margin:0;background:rgb($red,$green,$blue)'>ospite", "text/html", "utf-8", null) }
    await("la WebView ospite finisce di caricare") { hostFinished.get() > before }
    var pixel = 0
    val deadline = SystemClock.uptimeMillis() + 10_000
    do {
      val current = activity!!
      val bitmap = Bitmap.createBitmap(current.window.decorView.width, current.window.decorView.height, Bitmap.Config.ARGB_8888)
      val copied = CountDownLatch(1)
      var x = 0
      var y = 0
      onMain {
        val location = IntArray(2); host!!.getLocationInWindow(location)
        x = location[0] + host!!.width / 2
        y = location[1] + host!!.height / 2
        PixelCopy.request(current.window, bitmap, { copied.countDown() }, Handler(Looper.getMainLooper()))
      }
      assertTrue(copied.await(5, TimeUnit.SECONDS))
      pixel = bitmap.getPixel(x, y)
      bitmap.recycle()
      if (near(pixel, red, green, blue)) break
      Thread.sleep(200)
    } while (SystemClock.uptimeMillis() < deadline)
    assertTrue("La WebView ospite non disegna la pagina nuova: pixel ${Integer.toHexString(pixel)}", near(pixel, red, green, blue))
  }

  private fun near(pixel: Int, red: Int, green: Int, blue: Int) =
    kotlin.math.abs(Color.red(pixel) - red) < 24 && kotlin.math.abs(Color.green(pixel) - green) < 24 && kotlin.math.abs(Color.blue(pixel) - blue) < 24

  /** Una WebView dell'app ospite mai attaccata a una finestra, viva per tutta la prova: alla fine deve caricare ancora. */
  private fun startSentinel() = onMain {
    sentinel = WebView(instrumentation.targetContext).apply {
      webViewClient = object : WebViewClient() {
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
          hostGone.incrementAndGet()
          return true
        }
      }
      loadDataWithBaseURL(null, "<title>INIZIO</title>ospite", "text/html", "utf-8", null)
    }
  }

  private fun sentinelStillLoads() {
    onMain { sentinel!!.loadDataWithBaseURL(null, "<title>FINE</title>ospite", "text/html", "utf-8", null) }
    await("la WebView ospite senza finestra carica ancora") { sentinel!!.title == "FINE" }
  }

  /** Base: i renderer vivi con le sole WebView dell'ospite cariche e il contenitore vuoto. */
  private fun startWithHost(): Int {
    assumeTrue("Il provider non isola i profili: il contenitore fallisce chiuso e non c'e' nulla da contare",
      WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    mount()
    startSentinel()
    await("la WebView ospite senza finestra carica") { sentinel!!.title == "INIZIO" }
    hostLoadsAndDraws(0, 0, 200)
    await("contenitore vuoto") { state?.phase == FluidWebPhase.Empty }
    Thread.sleep(1500)
    val baseline = RendererCensus.read().count
    Log.i("FluidWebLifecycle", "base renderer=$baseline")
    assertTrue("La WebView ospite deve avere il suo renderer: $baseline", baseline >= 1)
    return baseline
  }

  private fun closeAll() {
    onMain { html = "" }
    await("contenitore vuoto alla fine") { state?.phase == FluidWebPhase.Empty && containerWeb() == null }
  }

  // ------------------------------------------------------------------------------------------------ prove

  /** Accettazione: almeno 60 documenti aperti e chiusi in un solo processo, in tutti i modi in cui un'app li chiude. */
  @Test fun manyDocumentsInOneProcessLeaveNoRendererBehind() {
    val total = InstrumentationRegistry.getArguments().getString("webDocuments")?.toInt() ?: 60
    val baseline = startWithHost()
    measure("inizio")
    for (index in 1..total) {
      val title = "DOC-$index"
      when (index % 6) {
        1, 5 -> open(title)
        2 -> { // riprova: vista nuova, con un altro profilo, per lo stesso documento
          open(title)
          onMain { state!!.retry() }
          awaitView(title, ready = true)
        }
        3 -> open(title, ready = false) // chiuso mentre carica: lo sostituisce il documento successivo
        4 -> { // cambio di tema: la vista viene ricreata
          open(title)
          onMain { dark = !dark }
          awaitView(title, ready = true)
        }
        else -> { // tolto dalla composizione e rimesso
          open(title)
          onMain { shown = false }
          await("contenitore tolto") { containerWeb() == null }
          onMain { shown = true }
          awaitView(title, ready = true)
        }
      }
      if (index == total / 2) {
        // Rotazione: l'Activity viene ricreata con il documento aperto.
        scenario!!.recreate()
        mount()
        awaitView(title, ready = true)
        hostLoadsAndDraws(200, 120, 0)
      }
      if (index % 10 == 0) {
        // Un documento e' ancora montato: il suo renderer e quelli dell'ospite sono gli unici ammessi.
        val alive = awaitRenderers("dopo $index documenti", baseline + 1)
        measure("dopo-$index")
        assertTrue("Fallimenti riportati dopo $index documenti: $failures", failures.isEmpty())
        Log.i("FluidWebLifecycle", "controllo documenti=$index renderer=$alive base=$baseline")
      }
    }
    closeAll()
    // Nessun margine: chiuso l'ultimo documento restano solo i renderer che c'erano prima di aprire il primo.
    val alive = awaitRenderers("alla fine, con il contenitore vuoto", baseline)
    measure("fine")
    Log.i("FluidWebLifecycle", "esito documenti=$total viste=$views renderer=$alive base=$baseline fallimenti=$failures ospiteMorto=${hostGone.get()}")
    assertTrue("Il contenitore ha riportato fallimenti per documenti che ha chiuso lui: $failures", failures.isEmpty())
    assertEquals("Le WebView dell'app ospite hanno visto morire il loro renderer", 0, hostGone.get())
    assertTrue("La prova deve aver creato almeno una vista per documento: $views", views >= total)
    assertTrue(profiles.toString(), profiles.all { it.startsWith("fluid-content-") })
    // I profili non crescono con i documenti: ne servono tanti quante sono le viste aperte o in chiusura nello stesso
    // momento. La prova apre un documento per volta e al piu' ne chiude qualcuno in rapida successione.
    assertTrue("Troppi profili per $views viste: ${profiles.size} (riusi $reuses)", profiles.size <= MaxProfiles)
    val registered = loadedContentProfiles()
    assertTrue("Troppi profili caricati nel processo: $registered", registered <= MaxProfiles)
    sentinelStillLoads()
    hostLoadsAndDraws(0, 160, 0)
    assertEquals(0, hostGone.get())
  }

  /** Chiusure rapide: ogni documento viene sostituito appena la sua vista esiste, mentre il renderer sta ancora partendo. */
  @Test fun documentsClosedWhileLoadingLeaveNoRendererBehind() {
    val baseline = startWithHost()
    repeat(4) { round ->
      for (index in 1..6) open("VELOCE-$round-$index", ready = false)
      open("FERMO-$round")
      awaitRenderers("dopo la raffica $round", baseline + 1)
    }
    // Rilascio durante il caricamento seguito subito dalla rimozione del contenitore.
    open("ULTIMO", ready = false)
    onMain { shown = false }
    await("contenitore tolto") { containerWeb() == null }
    val alive = awaitRenderers("dopo le chiusure rapide", baseline)
    measure("chiusure-rapide")
    Log.i("FluidWebLifecycle", "chiusureRapide viste=$views profili=${profiles.size} renderer=$alive base=$baseline fallimenti=$failures")
    assertTrue("$failures", failures.isEmpty())
    assertTrue("Troppi profili per $views viste: ${profiles.size}", profiles.size <= MaxProfiles)
    assertEquals(0, hostGone.get())
    sentinelStillLoads()
    hostLoadsAndDraws(0, 160, 0)
  }

  /** Un'app senza WebView sue: chiusi i documenti resta al piu' un renderer, quello che il provider avvia da se'.
   *
   * Margine fisso di uno, non proporzionale ai documenti. Alla seconda WebView creata in un processo il provider avvia
   * un renderer in piu' che non ospita nessun documento del contenitore; non compare quando l'app ha gia' una WebView
   * sua sul profilo predefinito (le altre prove, dove il conto torna alla base senza margine), quindi e' con ogni
   * probabilita' il renderer di quel profilo. Il contenitore non lo tocca: non e' di un suo profilo, e non potrebbe
   * distinguerlo da quello di una WebView dell'app. La prova pretende che resti uno, anche raddoppiando i documenti.
   */
  @Test fun containerAloneLeavesAtMostTheProvidersOwnRenderer() {
    assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    mount(withHost = false)
    await("contenitore vuoto") { state?.phase == FluidWebPhase.Empty }
    val before = RendererCensus.read().count
    for (index in 1..12) open("SOLO-$index")
    closeAll()
    val afterTwelve = awaitRenderers("dopo 12 documenti, senza WebView dell'ospite", before + 1)
    for (index in 13..24) open("SOLO-$index")
    closeAll()
    val afterTwentyFour = awaitRenderers("dopo 24 documenti, senza WebView dell'ospite", before + 1)
    Log.i("FluidWebLifecycle", "senzaOspite rendererPrima=$before dopo12=$afterTwelve dopo24=$afterTwentyFour viste=$views fallimenti=$failures")
    assertTrue("$failures", failures.isEmpty())
    assertTrue("Troppi profili per $views viste: ${profiles.size}", profiles.size <= MaxProfiles)
  }

  private companion object {
    /** Tetto fisso, non proporzionale ai documenti: vedi le asserzioni che lo usano. Misurati: 3 su 151 viste, 6 nelle raffiche. */
    const val MaxProfiles = 12
    const val SeededLeftovers = 60
  }

  /** Non e' una prova del contenitore: lascia al processo successivo un numero noto di profili da eliminare.
   *
   * Con il riuso un processo lascia pochi profili, troppo pochi per vedere l'eliminazione lavorare mentre il contenitore
   * apre documenti. Questi hanno lo stesso prefisso di quelli del contenitore e nessuna vista: al processo dopo sono
   * avanzi come gli altri. Va lanciata subito prima di [leftoverProfilesArePrunedWithoutHoldingTheFirstDocument].
   */
  @Test fun leftoverProfilesAreSeededForTheNextProcess() {
    assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    onMain { repeat(SeededLeftovers) { ProfileStore.getInstance().getOrCreateProfile("fluid-content-avanzo-di-prova-$it") } }
    Thread.sleep(3000) // il registro dei profili viene scritto su disco poco dopo
    val registered = loadedContentProfiles()
    Log.i("FluidWebLifecycle", "avanziSeminati profili=$registered")
    assertTrue("Profili seminati: $registered", registered >= SeededLeftovers)
  }

  /** Gli avanzi del processo precedente spariscono, il primo documento non li aspetta, e nessun profilo di questo
   * processo viene toccato.
   *
   * Va lanciata in un processo nuovo che trova profili lasciati da quello precedente
   * ([leftoverProfilesAreSeededForTheNextProcess]). Mentre il contenitore li elimina la prova continua ad aprire
   * documenti, cosi' l'eliminazione lavora insieme a profili in uso, in pulizia e pronti per il riuso. Le asserzioni
   * sono conteggi: alla fine nel registro non resta nessun avanzo, e restano tutti e soli i profili che il
   * contenitore ha usato qui. Nel log restano, come misure indicative, quanti avanzi c'erano ancora alla prima
   * pittura (il contenitore comincia a eliminarli qualche secondo dopo) e il fermo piu' lungo del thread principale.
   */
  @Test fun leftoverProfilesArePrunedWithoutHoldingTheFirstDocument() {
    assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    val leftoverNames = registeredContentProfiles()
    val leftovers = leftoverNames.size
    mount()
    onMain { html = document("PRIMO-DOPO-GLI-AVANZI") }
    awaitView("PRIMO-DOPO-GLI-AVANZI", ready = false)
    // Battito del thread principale: la distanza massima fra due giri mentre gli avanzi vengono eliminati.
    val worst = AtomicLong()
    val slow = AtomicInteger()
    val beating = AtomicBoolean(true)
    val handler = Handler(Looper.getMainLooper())
    var last = SystemClock.uptimeMillis()
    handler.post(object : Runnable {
      override fun run() {
        val now = SystemClock.uptimeMillis()
        if (now - last > worst.get()) worst.set(now - last)
        if (now - last > 32) slow.incrementAndGet()
        last = now
        if (beating.get()) handler.post(this)
      }
    })
    val startedAt = SystemClock.uptimeMillis()
    await("prima pittura con $leftovers avanzi") {
      state?.phase == FluidWebPhase.Error || (state?.phase == FluidWebPhase.Ready && containerWeb()?.title == "PRIMO-DOPO-GLI-AVANZI")
    }
    val readyAfter = SystemClock.uptimeMillis() - startedAt
    assertEquals("${state?.failure}", FluidWebPhase.Ready, state?.phase)
    val atFirstPaint = loadedContentProfiles()
    val worstUntilPaint = worst.get()
    // Finche' restano avanzi si aprono documenti: l'eliminazione e il riuso dei profili lavorano insieme.
    val deadline = SystemClock.uptimeMillis() + 120_000
    var during = 0
    while (during < 6 || (registeredContentProfiles().any { it in leftoverNames } && SystemClock.uptimeMillis() < deadline)) {
      open("DURANTE-GLI-AVANZI-${++during}")
    }
    val prunedAfter = SystemClock.uptimeMillis() - startedAt
    beating.set(false)
    // E dopo: i profili di questo processo ci sono ancora tutti, e il contenitore continua a riusarli.
    repeat(6) { open("DOPO-GLI-AVANZI-$it") }
    closeAll()
    val registered = registeredContentProfiles()
    Log.i("FluidWebLifecycle", "avanziContenitore profiliAllInizio=$leftovers allaPrimaPittura=$atFirstPaint " +
      "avanziRimasti=${registered.count { it in leftoverNames }} profiliDiQuestoProcesso=${profiles.size} nelRegistro=${registered.size} " +
      "documentiDurante=$during viste=$views riusi=$reuses fermoMassimoFinoAllaPitturaMs=$worstUntilPaint fermoMassimoMs=${worst.get()} " +
      "giriOltre32Ms=${slow.get()} primaPitturaDopoMs=$readyAfter eliminatiInMs=$prunedAfter fallimenti=$failures")
    assertTrue("Fallimenti mentre gli avanzi venivano eliminati: $failures", failures.isEmpty())
    assertEquals("Avanzi del processo precedente ancora nel registro", emptySet<String>(), registered.filter { it in leftoverNames }.toSet())
    assertEquals("L'eliminazione degli avanzi ha toccato un profilo di questo processo, o ne e' comparso uno estraneo",
      profiles.toSet(), registered)
    assertTrue("Con ${views} viste il riuso deve essere avvenuto: ${profiles.size} profili", reuses > 0)
    assertTrue("Troppi profili per $views viste: ${profiles.size}", profiles.size <= MaxProfiles)
  }

  /** Misura, senza soglie: quanto costerebbe eliminare gli avanzi tutti insieme, com'era fino alla 3.1.0.
   *
   * Va lanciata in un processo nuovo dopo una prova che ha aperto molti documenti. Usa le stesse chiamate del
   * contenitore (`allProfileNames`, `deleteProfile`), sul thread principale come lui, ma senza interruzioni.
   */
  @Test fun leftoverProfilesAtProcessStartAreMeasured() {
    assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    val context = instrumentation.targetContext
    val diskBefore = directoryKb(File(context.dataDir, "app_webview"))
    onMain {
      val started = SystemClock.elapsedRealtimeNanos()
      val store = ProfileStore.getInstance()
      val opened = SystemClock.elapsedRealtimeNanos()
      val names = store.allProfileNames.filter { it.startsWith("fluid-content-") }
      val listed = SystemClock.elapsedRealtimeNanos()
      var slowest = 0L
      for (name in names) {
        val before = SystemClock.elapsedRealtimeNanos()
        store.deleteProfile(name)
        slowest = maxOf(slowest, SystemClock.elapsedRealtimeNanos() - before)
      }
      val deleted = SystemClock.elapsedRealtimeNanos()
      Log.i("FluidWebLifecycle", "avanzi profili=${names.size} avvioProviderMs=${(opened - started) / 1_000_000} " +
        "elencoMs=${(listed - opened) / 1_000_000.0} cancellazioneTotaleMs=${(deleted - listed) / 1_000_000.0} " +
        "cancellazionePiuLentaMs=${slowest / 1_000_000.0} discoPrimaKb=$diskBefore")
    }
    Thread.sleep(3000)
    Log.i("FluidWebLifecycle", "avanzi discoDopoKb=${directoryKb(File(context.dataDir, "app_webview"))} " +
      "profiliRimasti=${loadedContentProfiles()}")
  }
}
