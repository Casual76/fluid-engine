package dev.antigravity.fluidengine.viewer.data

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidWebContent
import dev.antigravity.fluidengine.ui.fluid.FluidWebContentState
import dev.antigravity.fluidengine.ui.fluid.FluidWebPhase
import dev.antigravity.fluidengine.ui.fluid.FluidWebSizing
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * F6C.13 · Le prove sul dispositivo: **nessuna rete parte mai** dalle pagine della famiglia, con il contenitore chiuso
 * vero (FluidWebContent) e un server locale che conta le richieste; e le riprese delle pagine in chiaro e in scuro.
 */
@RunWith(AndroidJUnit4::class)
class DataDeviceTest {
  private lateinit var server: MockWebServer

  @Before fun setUp() {
    server = MockWebServer()
    server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
      override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "image/png").setBody("x")
    }
    server.start()
  }

  @After fun tearDown() {
    server.shutdown()
  }

  /** Mostra [html] col gestore [policy] nel contenitore chiuso e aspetta la prima pittura (o l'errore). */
  private fun render(html: String, policy: DataWeb.Policy): FluidWebPhase {
    val state = FluidWebContentState()
    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        activity.setContent {
          FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
            FluidWebContent(html, onLinkClick = {}, modifier = Modifier.fillMaxWidth().height(600.dp), state = state,
              onRequest = policy::handle, sizing = FluidWebSizing.Viewport(600.dp))
          }
        }
      }
      val deadline = System.currentTimeMillis() + 15_000
      while (System.currentTimeMillis() < deadline) {
        var phase = FluidWebPhase.Empty
        InstrumentationRegistry.getInstrumentation().runOnMainSync { phase = state.phase }
        if (phase == FluidWebPhase.Ready || phase == FluidWebPhase.Error) break
        Thread.sleep(100)
      }
      // Ancora un poco: le immagini pigre e i fogli si chiedono dopo la prima pittura.
      Thread.sleep(1_500)
      var phase = FluidWebPhase.Empty
      InstrumentationRegistry.getInstrumentation().runOnMainSync { phase = state.phase }
      return phase
    }
  }

  @Test fun htmlWithRemoteResourcesSendsNothing() {
    val base = server.url("/").toString()
    val html = """<!DOCTYPE html><html><head><link rel="stylesheet" href="${base}stile.css">
      <style>@import url("${base}importato.css"); body { background: url("${base}sfondo.png"); }</style></head>
      <body><h1>Rete chiusa</h1><img src="${base}foto.png"><img srcset="${base}a.png 1x, ${base}b.png 2x">
      <video poster="${base}poster.png"></video><iframe src="${base}frame.html"></iframe>
      <a href="${base}link">link</a><script src="${base}script.js"></script>
      <link rel="preconnect" href="${base}"><link rel="dns-prefetch" href="${base}"></body></html>"""
    val policy = DataWeb.Policy(local = null)
    val phase = render(html, policy)
    assertEquals(FluidWebPhase.Ready, phase)
    assertEquals("nessuna richiesta deve arrivare al server", 0, server.requestCount)
    assertTrue("le risorse remote sono passate da onRequest e bloccate", policy.blocked.get() >= 3)
  }

  @Test fun mailAndBookWithRemoteImagesSendNothing() {
    val base = server.url("/").toString()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val raw = context.assets.open("data/messaggio.eml").use { it.readBytes() }
    val mail = DataMime.parse(String(raw, Charsets.ISO_8859_1).replace("https://tracker.example.com/", base).toByteArray(Charsets.ISO_8859_1))
    val html = mail.html!!.replace("https://tracker.example.com/", base)
    val policy = DataWeb.Policy(local = null, part = { id -> mail.part(id)?.let { DataWeb.serveBytes(it.type, it.body) } })
    assertEquals(FluidWebPhase.Ready, render(DataWeb.inject(html, DataWeb.MailCss), policy))
    assertTrue("il logo cid: è servito dalla mail", policy.served.get() >= 1)
    assertEquals(0, server.requestCount)

    val book = File(context.cacheDir, "libro3.epub")
    context.assets.open("data/libro3.epub").use { input -> book.outputStream().use { input.copyTo(it) } }
    DataZip(java.io.FileInputStream(book).channel).use { zip ->
      val epub = DataEpub.read(zip)
      val chapter = DataEpub.chapterHtml(zip, epub, 2, "", "light").replace("https://example.com/", base)
      val model = DataEpubModel("libro3.epub", zip, epub)
      assertEquals(FluidWebPhase.Ready, render(chapter, model.policy))
      assertEquals(0, server.requestCount)
    }
  }

  /**
   * La parte chiara della pagina (pixel con luminanza oltre 0,8) fra il 15% e l'85% dell'altezza, letta con PixelCopy
   * dalla finestra dell'app di prova: niente UiAutomation, cosi' la prova gira anche accanto a una sessione agent-device.
   */
  private fun brightShare(name: String, dark: Boolean): Float {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val intent = Intent(instrumentation.targetContext, DataProvaActivity::class.java)
      .putExtra("file", name).putExtra("dark", dark).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ActivityScenario.launch<DataProvaActivity>(intent).use { scenario ->
      Thread.sleep(4_500)
      var share = -1f
      val thread = android.os.HandlerThread("pixelcopy").apply { start() }
      try {
        val done = java.util.concurrent.CountDownLatch(1)
        var bitmap: Bitmap? = null
        scenario.onActivity { activity ->
          val view = activity.window.decorView
          val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
          bitmap = b
          android.view.PixelCopy.request(activity.window, b, { done.countDown() }, android.os.Handler(thread.looper))
        }
        done.await(5, java.util.concurrent.TimeUnit.SECONDS)
        val b = bitmap ?: return -1f
        var bright = 0
        var total = 0
        val top = (b.height * 0.15f).toInt()
        val bottom = (b.height * 0.85f).toInt()
        for (y in top until bottom step 4) for (x in 0 until b.width step 4) {
          val c = b.getPixel(x, y)
          val l = (0.2126f * android.graphics.Color.red(c) + 0.7152f * android.graphics.Color.green(c) + 0.0722f * android.graphics.Color.blue(c)) / 255f
          if (l > 0.8f) bright++
          total++
        }
        share = bright.toFloat() / total
      } finally {
        thread.quitSafely()
      }
      return share
    }
  }

  /**
   * Nel tema scuro: un HTML senza colori suoi, una mail e un libro prendono il fondo del tema (quasi niente di chiaro);
   * una pagina con i suoi colori (colori.html, fondo bianco dell'autore) resta chiara. Con l'APK di prova a targetSdk 36.
   */
  @Test fun darkThemePagesFollowTheThemeUnlessThePageHasItsColors() {
    val out = StringBuilder()
    for (name in listOf("pagina.html", "messaggio.eml", "libro3.epub")) {
      val share = brightShare(name, dark = true)
      out.append("$name scuro: ${"%.3f".format(share)}").append('\n')
      assertTrue("$name nel tema scuro ha ${"%.1f".format(share * 100)}% di chiaro", share in 0f..0.08f)
    }
    val own = brightShare("colori.html", dark = true)
    out.append("colori.html scuro: ${"%.3f".format(own)}").append('\n')
    assertTrue("colori.html tiene il suo fondo bianco: ${"%.1f".format(own * 100)}% di chiaro", own > 0.5f)
    val light = brightShare("pagina.html", dark = false)
    out.append("pagina.html chiaro: ${"%.3f".format(light)}").append('\n')
    assertTrue("pagina.html nel tema chiaro e' chiara: ${"%.1f".format(light * 100)}%", light > 0.5f)
    android.util.Log.i("F6C13", out.toString())
  }

  /** Le riprese delle pagine: ogni file, chiaro e scuro, nella cartella dei file esterni dell'app di prova. */
  @Test fun pagesLightAndDark() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val out = File(instrumentation.targetContext.getExternalFilesDir(null), "f6c13").apply { mkdirs() }
    val only = InstrumentationRegistry.getArguments().getString("data.files")?.split(',')
    val dark = InstrumentationRegistry.getArguments().getString("data.themes")?.split(',') ?: listOf("chiaro", "scuro")
    val font = InstrumentationRegistry.getArguments().getString("data.font")?.toFloatOrNull() ?: 0f
    val suffix = if (font > 0f) "-${font.toInt()}x" else ""
    for (name in only ?: DataProva.Files) {
      for (theme in dark) {
        val intent = Intent(instrumentation.targetContext, DataProvaActivity::class.java)
          .putExtra("file", name).putExtra("dark", theme == "scuro").putExtra("font", font).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<DataProvaActivity>(intent).use {
          Thread.sleep(if (name.endsWith(".html") || name.endsWith(".epub") || name.endsWith(".eml")) 3_500 else 2_000)
          val shot: Bitmap? = instrumentation.uiAutomation.takeScreenshot()
          if (shot != null) File(out, "${name.replace('.', '-')}-$theme$suffix.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
      }
    }
    assertTrue(out.listFiles().orEmpty().isNotEmpty())
  }
}
