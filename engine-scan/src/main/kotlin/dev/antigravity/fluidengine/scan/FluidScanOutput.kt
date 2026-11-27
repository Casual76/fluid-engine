/*
 * engine-scan (4.0, F8.17): la consegna della scansione. Il PDF è scritto qui, con i JPEG delle pagine dentro così come
 * sono (DCTDecode): il PdfDocument di Android ricomprime le immagini senza perdita e una pagina pesa megabyte. Le
 * pagine e il PDF escono per URI dal FileProvider del modulo, in sola lettura. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.abs

/** Il FileProvider delle scansioni (`${applicationId}.fluidscan`): solo la cartella `cache/fluid-scan/`. */
class FluidScanFileProvider : FileProvider()

/**
 * Cosa consegna lo scanner: il PDF, le immagini delle pagine (JPEG), o tutte e due. Non è un `enum` di proposito
 * (regola 3.11).
 */
@Immutable
@JvmInline
value class FluidScanOutput private constructor(private val bits: Int) {
  val pdf: Boolean get() = bits and 1 != 0
  val images: Boolean get() = bits and 2 != 0

  override fun toString(): String = when (bits) { 1 -> "Pdf"; 2 -> "Images"; else -> "PdfAndImages" }

  companion object {
    /** Un PDF con una pagina per foglio: quello che Note di iOS salva, e che Universal Converter e Pampa Notes leggono. */
    val Pdf: FluidScanOutput = FluidScanOutput(1)
    /** Un JPEG per pagina. */
    val Images: FluidScanOutput = FluidScanOutput(2)
    val PdfAndImages: FluidScanOutput = FluidScanOutput(3)
  }
}

/**
 * Una scansione consegnata. Gli indirizzi sono `content://` del FileProvider del modulo, in sola lettura: l'app li
 * apre con il `ContentResolver`, li copia dove vuole o li passa a un'altra app con [grantTo]. I file restano nella cache
 * dell'app finché non chiama [FluidScanState.clear] o finché Android non la svuota.
 */
@Immutable
data class FluidScanResult(
  /** Il PDF, se chiesto. */
  val pdf: Uri?,
  /** Le pagine in JPEG nell'ordine della fila, se chieste. */
  val images: List<Uri>,
  val pageCount: Int,
  /** Il nome di serie del documento: «Scansione 25 nov 2026 14.32», come Note. */
  val title: String,
  /** I file sul disco dell'app (per chi li usa dentro l'app senza passare dal provider). */
  val pdfFile: File? = null,
  val imageFiles: List<File> = emptyList(),
) {
  /** Tutti gli indirizzi, il PDF per primo. */
  val uris: List<Uri> get() = listOfNotNull(pdf) + images

  /**
   * Un intent con gli indirizzi e il permesso di lettura, per consegnarli a un'altra app o come esito d'attività.
   *
   * R3: per un invio mette anche quello che le altre app leggono, se l'app non l'ha già messo: con `ACTION_SEND` il tipo
   * e `EXTRA_STREAM` del PDF (o della prima pagina, se il PDF non c'è); con `ACTION_SEND_MULTIPLE` tutti gli indirizzi,
   * col tipo comune (`image/jpeg`, `application/pdf`, o quello generico, asterisco/asterisco, se sono tutti e due). Per
   * le altre azioni (l'esito del contratto) solo gli indirizzi e il permesso, come prima.
   */
  fun grantTo(intent: Intent): Intent {
    val all = uris
    if (all.isEmpty()) return intent
    intent.clipData = android.content.ClipData.newRawUri(title, all.first()).apply { all.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val single = intent.action == Intent.ACTION_SEND
    if (single || intent.action == Intent.ACTION_SEND_MULTIPLE) {
      val sent = if (single) all.take(1) else all
      if (intent.type == null) intent.setType(sent.map { if (it == pdf) PdfType else JpegType }.distinct().singleOrNull() ?: AnyType)
      if (!intent.hasExtra(Intent.EXTRA_STREAM)) {
        if (single) intent.putExtra(Intent.EXTRA_STREAM, sent.first())
        else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(sent))
      }
    }
    return intent
  }

  private companion object {
    const val PdfType = "application/pdf"
    const val JpegType = "image/jpeg"
    const val AnyType = "*/*"
  }
}

internal object ScanFiles {
  const val Directory = "fluid-scan"

  fun root(context: Context): File = File(context.cacheDir, Directory).apply { mkdirs() }

  /**
   * R1: le cancellazioni dei file delle scansioni («Butta», una pagina tolta). Non dipendono dalla schermata: lo scope
   * della composizione si chiude con lei, anche subito dopo «Butta», e una pulizia lì poteva non partire mai.
   */
  val cleanup: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /**
   * L'indirizzo `content://` di [file] nel FileProvider del modulo, nella stessa forma che dà
   * `FileProvider.getUriForFile` (il nome della radice, poi il percorso): lo si costruisce qui perché la radice è una
   * sola e nota, e perché `getUriForFile` confronta i percorsi con «/» e sulla JVM di Windows delle prove non trova mai
   * la radice. Il provider lo risolve con la sua regola (`fluid_scan_paths.xml`).
   */
  fun uri(context: Context, file: File): Uri {
    val root = root(context).canonicalFile
    val path = file.canonicalFile.relativeTo(root).invariantSeparatorsPath
    require(!path.startsWith("..")) { "Fuori dalla cartella delle scansioni: $file" }
    return Uri.Builder().scheme("content").authority("${context.packageName}.fluidscan")
      .encodedPath(Uri.encode(RootName) + "/" + Uri.encode(path, "/")).build()
  }

  /** Il nome della radice in `fluid_scan_paths.xml`. */
  const val RootName = "fluid_scan"
}

/**
 * Il PDF più semplice che PdfRenderer, Anteprima e Acrobat leggono: PDF 1.4, una pagina per immagine, l'immagine come
 * JPEG intero (DCTDecode) che riempie la pagina, la tabella xref coi byte veri. Il formato della pagina: A4 o Letter se
 * le proporzioni ci stanno entro il 3%, altrimenti la larghezza di un A4 e l'altezza della pagina (o il contrario in
 * orizzontale), come Note di iOS.
 */
internal object ScanPdfWriter {
  class Page(val jpeg: ByteArray, val width: Int, val height: Int)

  /** La misura in punti di una pagina di [width] × [height] pixel. */
  fun pageSize(width: Int, height: Int): Pair<Float, Float> {
    val portrait = height >= width
    val ratio = if (portrait) width.toFloat() / height else height.toFloat() / width
    val (short, long) = when {
      abs(ratio - A4Ratio) / A4Ratio < 0.03f -> 595.28f to 841.89f
      abs(ratio - LetterRatio) / LetterRatio < 0.03f -> 612f to 792f
      else -> 595.28f to 595.28f / ratio
    }
    return if (portrait) short to long else long to short
  }

  fun write(pages: List<Page>, title: String, out: OutputStream) {
    val body = ByteArrayOutputStream()
    val offsets = ArrayList<Int>()
    fun obj(text: String) { offsets.add(body.size()); body.write(text.toByteArray(StandardCharsets.ISO_8859_1)) }
    body.write("%PDF-1.4\n%âãÏÓ\n".toByteArray(StandardCharsets.ISO_8859_1))
    // 1 catalogo, 2 pagine, 3 informazioni; poi per ogni pagina: pagina, contenuto, immagine.
    val kids = pages.indices.joinToString(" ") { "${4 + it * 3} 0 R" }
    obj("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
    obj("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\nendobj\n")
    obj("3 0 obj\n<< /Title ${pdfString(title)} /Producer (Fluid Engine) >>\nendobj\n")
    pages.forEachIndexed { i, page ->
      val pageId = 4 + i * 3
      val contentId = pageId + 1
      val imageId = pageId + 2
      val (w, h) = pageSize(page.width, page.height)
      val draw = "q ${fmt(w)} 0 0 ${fmt(h)} 0 0 cm /Im0 Do Q\n"
      obj("$pageId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmt(w)} ${fmt(h)}] " +
        "/Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n")
      obj("$contentId 0 obj\n<< /Length ${draw.length} >>\nstream\n${draw}endstream\nendobj\n")
      offsets.add(body.size())
      body.write(("$imageId 0 obj\n<< /Type /XObject /Subtype /Image /Width ${page.width} /Height ${page.height} " +
        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${page.jpeg.size} >>\nstream\n")
        .toByteArray(StandardCharsets.ISO_8859_1))
      body.write(page.jpeg)
      body.write("\nendstream\nendobj\n".toByteArray(StandardCharsets.ISO_8859_1))
    }
    val xref = body.size()
    val count = offsets.size + 1
    val table = StringBuilder("xref\n0 $count\n0000000000 65535 f \n")
    for (offset in offsets) table.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset))
    table.append("trailer\n<< /Size $count /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xref\n%%EOF\n")
    body.write(table.toString().toByteArray(StandardCharsets.ISO_8859_1))
    body.writeTo(out)
  }

  private fun fmt(v: Float): String = String.format(java.util.Locale.ROOT, "%.2f", v)

  /** Una stringa PDF: ASCII tra parentesi con gli escape; con lettere accentate, UTF-16BE in esadecimale. */
  private fun pdfString(text: String): String {
    if (text.all { it.code in 32..126 }) return "(" + text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ")"
    val bytes = text.toByteArray(StandardCharsets.UTF_16BE)
    return "<FEFF" + bytes.joinToString("") { String.format(java.util.Locale.ROOT, "%02X", it) } + ">"
  }

  private const val A4Ratio = 210f / 297f
  private const val LetterRatio = 8.5f / 11f
}
