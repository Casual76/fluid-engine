package dev.antigravity.fluidengine.sample.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.ViewerGallery
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidFileThumbnail
import dev.antigravity.fluidengine.viewer.FluidFileThumbnails
import dev.antigravity.fluidengine.viewer.FluidThumbnailLook
import dev.antigravity.fluidengine.viewer.FluidViewerFamily
import dev.antigravity.fluidengine.viewer.FluidViewerFamilyType
import dev.antigravity.fluidengine.viewer.FluidViewerOpened
import dev.antigravity.fluidengine.viewer.FluidViewerPreset
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * F6C.17 · Le miniature dei file della galleria «Visori», cioè il corpo di prova di tutte le famiglie:
 * - ogni file ha una miniatura non vuota e non uniforme, e i tipi che hanno un contenuto lo mostrano (niente icona);
 * - «Senza moduli» testo e codice hanno ancora la loro, i tipi delle famiglie l'icona del documento con l'estensione;
 * - l'apertura dalla miniatura parte dall'immagine vera (un JSON e un archivio) e ci torna: i fotogrammi vanno in
 *   `build/visori-apertura`, le miniature in `build/visori-miniature`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class VisoriMiniatureTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val context get() = RuntimeEnvironment.getApplication()
  private val thumbnails = File("build/visori-miniature").also { it.mkdirs() }
  private val frames = File("build/visori-apertura").also { it.mkdirs() }
  private val archive = FluidViewerRegistry.families.first { it.id == "archive" }

  @Before fun clean() {
    FluidFileThumbnails.clear()
  }

  @After fun restore() {
    while (VisoriSenzaModuli.active) VisoriSenzaModuli.leave()
    FluidViewerRegistry.install(archive)
    FluidFileThumbnails.clear()
  }

  /**
   * I file che sulla JVM non hanno il loro decodificatore e quindi danno l'icona: HEIC e AVIF (`ImageDecoder`
   * della piattaforma), l'APK (`PackageManager.getPackageArchiveInfo` di Robolectric non legge l'icona). Sul tablet
   * hanno la loro miniatura. Gli altri segnaposto sono voluti: lo sconosciuto e gli archivi (il simbolo).
   */
  private val nativeOnly = setOf("grafica-heic", "grafica-avif", "aggiornamento")
  private val symbols = setOf("scena", "archivio-zip", "archivio-7z", "archivio-rar", "archivio-tar", "archivio-tgz", "archivio-gz")

  private fun corpus(): List<Pair<VisoreFile, dev.antigravity.fluidengine.viewer.FluidViewerFile>> = runBlocking {
    VisoriFamiglia.entries.flatMap { family ->
      val files = family.files
      files.zip(VisoriFiles.prepare(context, files, family.scene))
    }.filter { (file, _) -> file.cover == null && file.tipo != VisoreTipo.Animata }
  }

  @Test fun ogniFileDelCorpoHaUnaMiniaturaVera() {
    for (look in listOf(FluidThumbnailLook.Light, FluidThumbnailLook.Dark)) {
      val shown = ArrayList<Triple<VisoreFile, FluidFileThumbnail, Long>>()
      for ((file, prepared) in corpus()) {
        val started = System.nanoTime()
        val thumbnail = runBlocking { FluidFileThumbnails.load(context, prepared, 480, look, timeoutMillis = 30_000) }
        val millis = (System.nanoTime() - started) / 1_000_000
        shown += Triple(file, thumbnail, millis)
        val name = if (look.dark) "${file.id}-scuro" else file.id
        save(File(thumbnails, "$name.png"), thumbnail.image.asAndroidBitmap())
        println("miniatura ${file.id} (${if (look.dark) "scuro" else "chiaro"}): ${thumbnail.image.width}×${thumbnail.image.height}, " +
          "${if (thumbnail.isPlaceholder) "segnaposto" else "contenuto"}, ${if (thumbnail.fillsFrame) "riempie" else "intera"}, $millis ms")
        assertTrue("${file.id}: la miniatura non è vuota né uniforme", hasInk(thumbnail.image.asAndroidBitmap()))
        if (file.id !in nativeOnly && file.id !in symbols) {
          assertFalse("${file.id}: mostra il contenuto, non l'icona", thumbnail.isPlaceholder)
        }
        val pixels = thumbnail.image.asAndroidBitmap()
        if (file.id == "dati-html") {
          assertTrue("La pagina mantiene il suo sfondo CSS", pixels.getPixel(0, 0) == 0xFFF6F3EB.toInt())
          assertTrue("La pagina contiene l'immagine insieme al testo", colored(pixels) > 200)
        }
        if (file.id == "modello-glb") {
          assertTrue("Anelli con e senza texture nella stessa scena",
            count(pixels) { Color.blue(it) > 1.35f * Color.red(it) && Color.blue(it) > 34 } > 400 &&
              count(pixels) { Color.red(it) > 1.45f * Color.blue(it) && Color.red(it) > 70 } > 400)
        }
        if (file.id.startsWith("archivio-")) {
          assertTrue("${file.id}: l'archivio non è una tessera grigia", colored(thumbnail.image.asAndroidBitmap()) > 40)
        }
      }
      sheet(shown, look.dark)
    }
  }

  @Test fun senzaModuliTestoECodiceHannoLaLoroGliAltriLIcona() {
    val files = corpus().filter { it.first.family != VisoriFamiglia.Serie }
    VisoriSenzaModuli.enter()
    assertTrue(FluidViewerRegistry.families.isEmpty())
    val shown = ArrayList<Triple<VisoreFile, FluidFileThumbnail, Long>>()
    for ((file, prepared) in files) {
      val thumbnail = runBlocking { FluidFileThumbnails.load(context, prepared, 240, timeoutMillis = 30_000) }
      shown += Triple(file, thumbnail, 0L)
      save(File(thumbnails, "senza-${file.id}.png"), thumbnail.image.asAndroidBitmap())
      assertTrue("${file.id}: mai vuota", hasInk(thumbnail.image.asAndroidBitmap()))
      val type = FluidViewerType.of(file.mime, file.name)
      if (type.preset == FluidViewerPreset.Text) {
        assertFalse("${file.id}: il testo di serie ha le sue prime righe", thumbnail.isPlaceholder)
      }
      if (type.preset == FluidViewerPreset.Unknown) {
        assertTrue("${file.id}: senza il modulo, l'icona del documento", thumbnail.isPlaceholder)
      }
    }
    VisoriSenzaModuli.leave()
    sheet(shown, dark = false, name = "senza-moduli")
  }

  // --- L'apertura dalla miniatura ---------------------------------------------------------------------------------

  private val deps = VisoriDeps(
    content = { file ->
      when (file.tipo) {
        VisoreTipo.Foto -> FluidViewerContent.Image(1.5f) { Box(Modifier.fillMaxSize().semantics { contentDescription = file.name }) }
        VisoreTipo.Pagine -> FluidViewerContent.Pages(object : FluidPageSource {
          override val count = 6
          override suspend fun size(index: Int) = IntSize(420, 595)
          override suspend fun render(index: Int, target: IntSize) =
            androidx.compose.ui.graphics.ImageBitmap(target.width.coerceAtLeast(1), target.height.coerceAtLeast(1))
        })
        else -> null
      }
    },
  )

  private fun show() {
    // libarchive non si carica sulla JVM: una famiglia con gli stessi tipi apre una pagina finta, la miniatura è vera.
    FluidViewerRegistry.install(object : FluidViewerFamily {
      override val id = archive.id
      override val types: List<FluidViewerFamilyType> = archive.types
      override suspend fun open(request: FluidViewerRequest) = FluidViewerOpened(FluidViewerContent.Custom {
        Text("Archivio finto: ${request.source.name}")
      })
      override suspend fun thumbnail(request: FluidViewerRequest, sizePx: Int) = archive.thumbnail(request, sizePx)
    })
    compose.setContent { FluidTheme(settings = remember { EngineSettings() }) { ViewerGallery(deps) {} } }
    compose.waitForIdle()
  }

  // A mdpi: ogni fotogramma del vetro su Robolectric costa secondi, e a xhdpi la prova durava dieci minuti.
  @Test @Config(qualifiers = "w411dp-h891dp-mdpi") fun lAperturaPartedallaMiniaturaVeraETornaLi() {
    show()
    for (id in listOf("dati-json", "archivio-zip")) {
      val ink = ::colored
      compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("visori-$id"))
      // La tessera al centro della finestra, intera: performScrollToNode la lascia sul bordo.
      val height = compose.activity.window.decorView.height
      val center = compose.onNodeWithTag("visori-$id").fetchSemanticsNode().boundsInWindow.center.y
      compose.onNode(hasScrollToNodeAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, center - height / 2f) }
      waitFor("la miniatura di $id") {
        compose.onAllNodes(hasTestTag("visori-miniatura-$id"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
      }
      compose.waitForIdle()
      val before = window("$id-0-griglia")
      val tile = compose.onNodeWithTag("visori-$id").fetchSemanticsNode().boundsInWindow
      val inTile = ink(crop(before, tile))
      assertTrue("$id: la tessera mostra il contenuto ($inTile)", inTile > 20)
      compose.mainClock.autoAdvance = false
      try {
        compose.onNodeWithTag("visori-$id").performClick()
        var drawn = 0
        for (ms in 16..496 step 32) {
          compose.mainClock.advanceTimeBy(32)
          val frame = window("$id-1-apre-${"%03d".format(ms)}")
          // Dove stava la tessera c'è ancora la sua immagine, ingrandita dalla sagoma che cresce.
          if (ms <= 112 && ink(crop(frame, tile)) > inTile / 3) drawn++
        }
        assertTrue("$id: i primi fotogrammi mostrano la miniatura vera mentre cresce ($drawn)", drawn >= 2)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        for (ms in 16..496 step 48) {
          compose.mainClock.advanceTimeBy(48)
          window("$id-2-torna-${"%03d".format(ms)}")
        }
      } finally {
        compose.mainClock.autoAdvance = true
      }
      compose.waitForIdle()
      val after = window("$id-3-tornata")
      val back = compose.onNodeWithTag("visori-$id").fetchSemanticsNode().boundsInWindow
      assertTrue("$id: tornata, la tessera ha ancora la sua miniatura", ink(crop(after, back)) > inTile / 2)
    }
  }

  // --- Misure -----------------------------------------------------------------------------------------------------

  private fun waitFor(what: String, condition: () -> Boolean) {
    try {
      compose.waitUntil(20_000) { condition() }
    } catch (e: ComposeTimeoutException) {
      throw AssertionError("Non compare: $what", e)
    }
  }

  private fun window(frame: String): Bitmap {
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var code = PixelCopy.ERROR_UNKNOWN
    compose.runOnIdle { PixelCopy.request(window, bitmap, { code = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue("PixelCopy $code", code == PixelCopy.SUCCESS)
    save(File(frames, "$frame.png"), bitmap)
    return bitmap
  }

  private fun crop(bitmap: Bitmap, rect: androidx.compose.ui.geometry.Rect): Bitmap {
    val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
    val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
    val width = rect.width.toInt().coerceIn(1, bitmap.width - left)
    val height = rect.height.toInt().coerceIn(1, bitmap.height - top)
    return Bitmap.createBitmap(bitmap, left, top, width, height)
  }

  /** I campioni con un colore deciso: le stringhe e i numeri dell'albero, la tinta dell'archivio. Non grigi. */
  private fun colored(bitmap: Bitmap): Int = count(bitmap) { c ->
    Color.alpha(c) > 128 && max(Color.red(c), max(Color.green(c), Color.blue(c))) -
      minOf(Color.red(c), Color.green(c), Color.blue(c)) > 60
  }

  private fun count(bitmap: Bitmap, test: (Int) -> Boolean): Int {
    // Lo stesso passo per la tessera e per la finestra intera: i conti si confrontano.
    var n = 0
    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (test(bitmap.getPixel(x, y))) n++
    return n
  }

  /** Almeno l'1% dei campioni si stacca dal colore principale. */
  private fun hasInk(bitmap: Bitmap): Boolean {
    val counts = HashMap<Int, Int>()
    val samples = ArrayList<Int>()
    val step = max(1, max(bitmap.width, bitmap.height) / 120)
    for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) {
      val c = bitmap.getPixel(x, y)
      samples += c
      counts[c] = (counts[c] ?: 0) + 1
    }
    val main = counts.maxBy { it.value }.key
    val ink = samples.count { c ->
      maxOf(abs(Color.red(c) - Color.red(main)), abs(Color.green(c) - Color.green(main)), abs(Color.blue(c) - Color.blue(main)),
        abs(Color.alpha(c) - Color.alpha(main))) > 24
    }
    return ink >= samples.size / 100
  }

  private fun save(file: File, bitmap: Bitmap) {
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  /**
   * Il foglio delle miniature come tessere della galleria (1,5:1, il fondo della tessera del tema), con il nome e il
   * tempo: `build/visori-miniature/<name>-chiaro.png` e `-scuro.png`.
   */
  private fun sheet(items: List<Triple<VisoreFile, FluidFileThumbnail, Long>>, dark: Boolean, name: String = "corpo") {
    val columns = 6
    val tileW = 300
    val tileH = 200
    val label = 54
    val gap = 24
    val rows = (items.size + columns - 1) / columns
    val bitmap = Bitmap.createBitmap(gap + columns * (tileW + gap), gap + rows * (tileH + label + gap), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(if (dark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt())
    val tileColor = if (dark) 0xFF2C2C2E.toInt() else 0xFFE5E5EA.toInt()
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; color = if (dark) Color.WHITE else Color.BLACK }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; color = 0xFF8E8E93.toInt() }
    for ((i, item) in items.withIndex()) {
      val (file, thumbnail, millis) = item
      val x = gap + (i % columns) * (tileW + gap)
      val y = gap + (i / columns) * (tileH + label + gap)
      val tile = RectF(x.toFloat(), y.toFloat(), (x + tileW).toFloat(), (y + tileH).toFloat())
      canvas.save()
      val path = android.graphics.Path().apply { addRoundRect(tile, 22f, 22f, android.graphics.Path.Direction.CW) }
      canvas.clipPath(path)
      canvas.drawRect(tile, Paint().apply { color = tileColor })
      val image = thumbnail.image.asAndroidBitmap()
      val scale = if (thumbnail.fillsFrame) max(tileW.toFloat() / image.width, tileH.toFloat() / image.height)
        else minOf(tileW.toFloat() / image.width, tileH.toFloat() / image.height)
      val w = image.width * scale
      val h = image.height * scale
      canvas.drawBitmap(image, null, RectF(tile.centerX() - w / 2, tile.centerY() - h / 2, tile.centerX() + w / 2, tile.centerY() + h / 2),
        Paint(Paint.FILTER_BITMAP_FLAG))
      canvas.restore()
      canvas.drawText(file.name, x.toFloat(), (y + tileH + 24).toFloat(), text)
      canvas.drawText((if (thumbnail.isPlaceholder) "icona" else "contenuto") + if (millis > 0) " · $millis ms" else "",
        x.toFloat(), (y + tileH + 46).toFloat(), small)
    }
    save(File(thumbnails, "$name-${if (dark) "scuro" else "chiaro"}.png"), bitmap)
  }
}
