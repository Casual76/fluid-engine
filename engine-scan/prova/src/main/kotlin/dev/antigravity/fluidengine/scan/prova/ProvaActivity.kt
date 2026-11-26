package dev.antigravity.fluidengine.scan.prova

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.scan.FluidScanDocumentContract
import dev.antigravity.fluidengine.scan.FluidScanOutput
import dev.antigravity.fluidengine.scan.FluidScanRequest
import dev.antigravity.fluidengine.scan.FluidScanResult
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles
import dev.antigravity.fluidengine.viewer.ocr.FluidTextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.min

/**
 * L'app di prova: «Scansiona» apre lo scanner del contratto con la fotocamera vera e poi il PDF e le immagini nel visore
 * (con «Leggi testo»); «Misura OCR» legge le immagini di prova con ogni riconoscitore della variante e scrive i tempi e
 * l'errore per carattere.
 */
class ProvaActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val step = intent.getStringExtra("passo")
    setContent { FluidTheme(EngineSettings()) { Prova(step) } }
  }
}

@Composable
private fun Prova(step: String?) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var result by remember { mutableStateOf<FluidScanResult?>(null) }
  var viewing by remember { mutableStateOf(false) }
  var report by remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  val scan = rememberLauncherForActivityResult(FluidScanDocumentContract()) { result = it; if (it != null) viewing = true }
  val measure: () -> Unit = {
    report = "Misura in corso…"
    scope.launch { report = OcrBench.run(context) }
  }
  LaunchedEffect(step) { if (step == "misura") measure() }
  val done = result
  if (viewing && done != null) {
    BackHandler { viewing = false }
    val files = remember(done) {
      listOfNotNull(done.pdf?.let { FluidViewerFile(it, done.title + ".pdf", "application/pdf") }) +
        done.images.mapIndexed { i, uri -> FluidViewerFile(uri, "${done.title} - ${i + 1}.jpg", "image/jpeg") }
    }
    val opened = rememberFluidViewerFiles(files)
    FluidFileViewer(count = opened.count, content = opened::content, onBack = { viewing = false })
    return
  }
  Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("Pampa · prova scanner", style = MaterialTheme.typography.headlineSmall)
    Text("La fotocamera vera dello scanner di documenti (engine-scan) e «Leggi testo» (engine-viewer-ocr).",
      style = MaterialTheme.typography.bodyMedium)
    FluidButton("Scansiona un documento", { scan.launch(FluidScanRequest(output = FluidScanOutput.PdfAndImages)) })
    if (done != null) {
      Text("Ultima scansione: ${done.pageCount} pagine, «${done.title}»", style = MaterialTheme.typography.bodyMedium)
      FluidButton("Apri nel visore", { viewing = true })
    }
    FluidButton("Misura OCR sulle immagini di prova", measure)
    if (report.isNotEmpty()) Text(report, style = MaterialTheme.typography.bodySmall)
  }
}

/** La misura dei riconoscitori sulle immagini di prova (assets/ocr, le stesse delle prove del modulo). */
internal object OcrBench {
  private val images = listOf("circolare.png", "lettera.png", "ricevuta.png", "schermata.png", "foto-lettera.jpg")

  suspend fun run(context: Context): String = withContext(Dispatchers.Default) {
    val truth = JSONObject(context.assets.open("ocr/righe.json").use { it.readBytes().toString(Charsets.UTF_8) })
    val all = JSONArray()
    val text = StringBuilder()
    for ((name, recognizer) in recognizers(context)) {
      text.append("== $name\n")
      val first = timeFirst(context, recognizer)
      text.append("primo uso (modello): $first ms\n")
      for (image in images + Scanned) {
        val bitmap = if (image == Scanned) scanned(context) else context.assets.open("ocr/$image").use { BitmapFactory.decodeStream(it) }
        val times = ArrayList<Long>()
        var recognized = ""
        var words = emptyList<String>()
        repeat(3) {
          val start = System.nanoTime()
          val r = recognizer.recognize(bitmap)
          times += (System.nanoTime() - start) / 1_000_000
          recognized = r.lines.joinToString(" ") { it.text }
          words = r.words.map { it.text }
        }
        val expected = lines(truth, if (image == "foto-lettera.jpg" || image == Scanned) "lettera.png" else image).joinToString(" ")
        val cer = cer(expected, recognized)
        val found = recall(expected, words)
        val median = times.sorted()[1]
        text.append("$image: ${"%.1f".format(cer * 100)}% CER, ${"%.1f".format(found * 100)}% parole, $median ms\n")
        all.put(JSONObject().put("engine", name).put("image", image).put("cer", cer).put("recall", found).put("ms", median)
          .put("first_ms", first).put("text", recognized))
        Log.i("PampaScan", "ocr $name $image cer=${"%.4f".format(cer)} ms=$median")
        bitmap.recycle()
      }
    }
    File(context.filesDir, "misure-ocr.json").writeText(JSONObject().put("device", android.os.Build.MODEL).put("results", all).toString(1))
    text.toString()
  }

  /** La foto storta della lettera passata dallo scanner: i bordi trovati, raddrizzata, il filtro «Documento». */
  private const val Scanned = "foto-lettera-scansionata"

  private fun scanned(context: Context): android.graphics.Bitmap {
    val photo = context.assets.open("ocr/foto-lettera.jpg").use { BitmapFactory.decodeStream(it) }
    val quad = dev.antigravity.fluidengine.scan.FluidScanEdges.detect(photo)?.quad ?: dev.antigravity.fluidengine.scan.FluidScanQuad.Full
    val straight = dev.antigravity.fluidengine.scan.FluidScanImages.straighten(photo, quad)
    return dev.antigravity.fluidengine.scan.FluidScanImages.filter(straight, dev.antigravity.fluidengine.scan.FluidScanFilter.Document)
  }

  private suspend fun timeFirst(context: Context, recognizer: FluidTextRecognizer): Long {
    val bitmap = context.assets.open("ocr/ricevuta.png").use { BitmapFactory.decodeStream(it) }
    val start = System.nanoTime()
    recognizer.recognize(bitmap)
    return (System.nanoTime() - start) / 1_000_000
  }

  private fun lines(truth: JSONObject, name: String): List<String> {
    val a = truth.getJSONArray("images")
    for (i in 0 until a.length()) {
      val e = a.getJSONObject(i)
      if (e.getString("name") == name) return (0 until e.getJSONArray("lines").length()).map { e.getJSONArray("lines").getJSONObject(it).getString("text") }
    }
    return emptyList()
  }

  /** Le parole vere ritrovate fra quelle lette, in qualunque ordine (la stessa misura di OcrRecognitionTest). */
  fun recall(expected: String, words: List<String>): Double {
    fun norm(w: String) = w.lowercase().trim { !it.isLetterOrDigit() }
    val truth = expected.replace(Regex("_{2,}"), " ").split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }
    val pool = words.map(::norm).filter { it.isNotEmpty() }.groupingBy { it }.eachCount().toMutableMap()
    var found = 0
    for (w in truth) {
      val n = pool[w] ?: 0
      if (n > 0) { found++; pool[w] = n - 1 }
    }
    return found.toDouble() / truth.size.coerceAtLeast(1)
  }

  fun cer(expected: String, actual: String): Double {
    val a = expected.replace(Regex("_{2,}"), " ").replace(Regex("\\s+"), " ").trim()
    val b = actual.replace(Regex("\\s+"), " ").trim()
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
      cur[0] = i
      for (j in 1..b.length) cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
      val t = prev; prev = cur; cur = t
    }
    return prev[b.length].toDouble() / a.length.coerceAtLeast(1)
  }
}
