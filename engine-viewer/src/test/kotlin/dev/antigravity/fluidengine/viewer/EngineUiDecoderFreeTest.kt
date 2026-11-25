package dev.antigravity.fluidengine.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F6C-d dalla parte dei sorgenti: engine-ui resta senza decodificatori. Il grafo delle dipendenze lo controlla il
 * compito Gradle `checkEngineUiHasNoDecoders`, che gira prima di questi test; qui si leggono il `build.gradle` e i
 * sorgenti di engine-ui, che il grafo non vede (una dipendenza `compileOnly`, una API della piattaforma).
 */
class EngineUiDecoderFreeTest {

  // I test JVM girano nella cartella del modulo; engine-ui è accanto, qui e in un'app ospite (cartelle piatte).
  private val engineUi = File(System.getProperty("user.dir") ?: ".").resolve("../engine-ui").canonicalFile

  private val forbiddenApis = listOf(
    "android.graphics.pdf.", "android.graphics.ImageDecoder", "android.graphics.drawable.AnimatedImageDrawable",
    "android.graphics.Movie", "android.graphics.BitmapFactory", "android.media.MediaPlayer", "android.media.MediaCodec",
    "android.media.MediaExtractor", "android.media.MediaMetadataRetriever", "androidx.media3.", "com.google.android.exoplayer",
    "androidx.pdf.", "coil.", "coil3.", "com.bumptech.glide.", "com.facebook.fresco.", "dev.antigravity.fluidengine.viewer.",
  )

  // Le eccezioni ammesse, una per una, con l'import esatto (F8.10). Scrivere un PDF non è decodificarlo
  // (`PdfDocument` crea, `PdfRenderer` resta vietato), e la miniatura del foglio di condivisione e la stampa di una
  // foto leggono l'immagine a campioni, mai intera e mai per mostrarla come un visore.
  private val allowedImports = setOf(
    "output/FluidPdf.kt" to "android.graphics.pdf.PdfDocument",
    "output/FluidPrint.kt" to "android.graphics.pdf.PdfDocument",
    "output/FluidPrint.kt" to "android.graphics.BitmapFactory",
    "share/FluidShareSheet.kt" to "android.graphics.BitmapFactory",
  )

  private fun allowed(file: File, line: String): Boolean = allowedImports.any { (suffix, api) ->
    file.invariantSeparatorsPath.endsWith("/fluid/$suffix") && line.trim() == "import $api"
  }

  // Un nome di pacchetto intero, non un pezzo di parola: `coil.` non deve prendere `FluidRecoil.Default`.
  private val apiPatterns = forbiddenApis.map { Regex("(?<![A-Za-z0-9_.])" + Regex.escape(it)) }

  private val forbiddenCoordinates = listOf(
    "androidx.media3", "com.google.android.exoplayer", "androidx.pdf", "io.coil-kt", "com.github.bumptech.glide",
    "com.facebook.fresco", "pl.droidsonroids.gif", "com.tom-roush", "org.apache.pdfbox", "juniversalchardet",
    "com.ibm.icu", "io.noties", ":engine-viewer",
  )

  @Test
  fun `engine-ui is next to this module`() {
    assertTrue("engine-ui non trovato in $engineUi", engineUi.resolve("build.gradle").isFile)
  }

  @Test
  fun `the engine-ui build declares no decoder and not this module`() {
    val script = engineUi.resolve("build.gradle").readLines()
      .map { it.substringBefore("//").trim() }
      .filter { it.isNotEmpty() }
    val found = script.filter { line -> forbiddenCoordinates.any { it in line } }
    assertEquals("dipendenze di decodifica in engine-ui/build.gradle", emptyList<String>(), found)
  }

  @Test
  fun `the engine-ui sources use no decoder api`() {
    val sources = engineUi.resolve("src/main").walkTopDown()
      .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
      .toList()
    assertTrue("nessun sorgente letto in $engineUi", sources.size > 100)
    val found = sources.flatMap { file ->
      file.readLines().withIndex()
        .filter { (_, line) -> !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*") }
        .filter { (_, line) -> apiPatterns.any { it.containsMatchIn(line) } }
        .filter { (_, line) -> !allowed(file, line) }
        .map { (n, line) -> "${file.relativeTo(engineUi)}:${n + 1}: ${line.trim()}" }
    }
    assertEquals("API di decodifica nei sorgenti di engine-ui", emptyList<String>(), found)
  }
}
