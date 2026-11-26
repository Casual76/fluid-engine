/*
 * engine-scan (4.0, F8.17): lo scanner come attività con un contratto, per chi lo apre come si apriva lo scanner di ML
 * Kit o `VNDocumentCameraViewController`: una riga, e la scansione torna per URI. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.core.content.IntentCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/** Cosa chiede l'app allo scanner aperto col contratto. */
@Immutable
data class FluidScanRequest(
  val output: FluidScanOutput = FluidScanOutput.Pdf,
  val filter: FluidScanFilter = FluidScanFilter.Document,
  val autoCapture: Boolean = true,
  /** Il nome del documento; di serie «Scansione» con la data e l'ora. */
  val title: String? = null,
)

/**
 * Il contratto dello scanner: si lancia con una [FluidScanRequest] e torna una [FluidScanResult] (senza i file: solo gli
 * indirizzi, già concessi in lettura), o `null` se l'utente annulla.
 *
 * ```kotlin
 * val scan = rememberLauncherForActivityResult(FluidScanDocumentContract()) { result ->
 *   result?.pdf?.let { apri(it) }      // Universal Converter: il PDF; Pampa Notes: le immagini nella nota
 * }
 * scan.launch(FluidScanRequest(output = FluidScanOutput.Pdf))
 * ```
 *
 * L'app dichiara `android.permission.CAMERA` nel suo manifest; lo scanner lo chiede col tasto quando serve.
 */
class FluidScanDocumentContract : ActivityResultContract<FluidScanRequest, FluidScanResult?>() {
  override fun createIntent(context: Context, input: FluidScanRequest): Intent =
    Intent(context, FluidScanActivity::class.java)
      .putExtra(ExtraOutput, input.output.toString())
      .putExtra(ExtraFilter, input.filter.id)
      .putExtra(ExtraAuto, input.autoCapture)
      .putExtra(ExtraTitle, input.title)

  override fun parseResult(resultCode: Int, intent: Intent?): FluidScanResult? {
    if (resultCode != Activity.RESULT_OK || intent == null) return null
    val pdf = IntentCompat.getParcelableExtra(intent, ExtraPdf, Uri::class.java)
    val images = IntentCompat.getParcelableArrayListExtra(intent, ExtraImages, Uri::class.java).orEmpty()
    return FluidScanResult(pdf, images, intent.getIntExtra(ExtraPages, images.size), intent.getStringExtra(ExtraTitle).orEmpty())
  }

  internal companion object {
    const val ExtraOutput = "dev.antigravity.fluidengine.scan.OUTPUT"
    const val ExtraFilter = "dev.antigravity.fluidengine.scan.FILTER"
    const val ExtraAuto = "dev.antigravity.fluidengine.scan.AUTO"
    const val ExtraTitle = "dev.antigravity.fluidengine.scan.TITLE"
    const val ExtraPdf = "dev.antigravity.fluidengine.scan.PDF"
    const val ExtraImages = "dev.antigravity.fluidengine.scan.IMAGES"
    const val ExtraPages = "dev.antigravity.fluidengine.scan.PAGES"

    fun output(name: String?): FluidScanOutput = when (name) {
      "Images" -> FluidScanOutput.Images
      "PdfAndImages" -> FluidScanOutput.PdfAndImages
      else -> FluidScanOutput.Pdf
    }
  }
}

/**
 * L'attività dello scanner del contratto (non esportata). Il tema è quello di serie dell'engine; un'app che vuole il suo
 * lo mette in [FluidScanActivity.theme] all'avvio (o usa [FluidDocumentScanner] in una sua pagina).
 */
class FluidScanActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    // R1: sotto la barra a tre tasti Android stende un velo di contrasto (la regola delle app dell'engine lo spegne).
    // L'attività gestisce da sé la rotazione, e `enableEdgeToEdge()` lo riaccende ai cambi di configurazione: dopo, lo
    // si rispegne, come nella galleria.
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      window.isNavigationBarContrastEnforced = false
      addOnConfigurationChangedListener { window.decorView.post { window.isNavigationBarContrastEnforced = false } }
    }
    val options = FluidScanOptions(
      output = FluidScanDocumentContract.output(intent.getStringExtra(FluidScanDocumentContract.ExtraOutput)),
      filter = FluidScanFilter.of(intent.getStringExtra(FluidScanDocumentContract.ExtraFilter)),
      autoCapture = intent.getBooleanExtra(FluidScanDocumentContract.ExtraAuto, true),
      title = intent.getStringExtra(FluidScanDocumentContract.ExtraTitle),
    )
    setContent {
      theme {
        val state = rememberFluidScanState(options)
        val camera = rememberFluidCameraXScanCamera()
        FluidDocumentScanner(
          state = state,
          camera = camera,
          onDone = { result ->
            val named = result
            val data = Intent()
              .putExtra(FluidScanDocumentContract.ExtraPdf, named.pdf)
              .putParcelableArrayListExtra(FluidScanDocumentContract.ExtraImages, ArrayList(named.images))
              .putExtra(FluidScanDocumentContract.ExtraPages, named.pageCount)
              .putExtra(FluidScanDocumentContract.ExtraTitle, named.title)
            setResult(RESULT_OK, named.grantTo(data))
            finish()
          },
          onCancel = {
            setResult(RESULT_CANCELED)
            finish()
          },
        )
      }
    }
  }

  companion object {
    /** Il tema dello scanner del contratto: di serie [FluidTheme] con le impostazioni di serie. */
    @JvmStatic
    var theme: @Composable (content: @Composable () -> Unit) -> Unit = { content -> FluidTheme(EngineSettings()) { content() } }
  }
}
