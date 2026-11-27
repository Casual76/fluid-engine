/*
 * La condivisione (4.0, F8.10, `foglio-condividi`): cio' che si manda fuori dall'app. Il foglio, le azioni e la fila delle
 * app Pampa leggono tutti questo modello; l'app lo costruisce dall'oggetto vero (un Uri dato da un FileProvider, un testo,
 * un link). Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.ui.fluid.share

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import dev.antigravity.fluidengine.ui.fluid.output.FluidPdfDocument
import java.io.File
import java.util.Locale

/**
 * Cio' che il foglio di condivisione manda: uno o piu' file ([uris], letti con il permesso di lettura che l'engine passa a
 * chi riceve), un testo o un link ([text]). Come l'oggetto di `ShareLink` di SwiftUI: l'anteprima in cima al foglio
 * ([title], tipo e peso, [preview]) viene da qui, e la consegna resta agli intent di Android.
 *
 * Si costruisce di solito con i metodi del companion ([text], [link], [file], [files]). Per un file dell'app c'e' [file]
 * con un [File], come `ShareLink` accetta qualunque file leggibile: se sta nella cartella dell'engine (un'immagine di
 * [dev.antigravity.fluidengine.ui.fluid.output.FluidShareImageRenderer], un PDF di
 * [dev.antigravity.fluidengine.ui.fluid.output.FluidPdf], [FluidShareFiles.newFile]) l'Uri lo da' subito il FileProvider
 * dell'engine; se sta altrove (una foto in `filesDir`) resta in [localFiles] e il foglio la copia nella cartella
 * dell'engine quando si apre ([FluidShareFiles.stage]).
 *
 * @param title il nome in cima al foglio: il nome del file, il titolo della pagina, «3 foto».
 * @param mimeType il tipo MIME di cio' che si manda (`text/plain` per un testo o un link; «image» con l'asterisco per foto miste).
 * @param uris i file, `content://` (mai `file://`: Android lo vieta fra app). Vuoto per un testo o un link.
 * @param text il testo o il link; con dei file e' il messaggio che li accompagna (`EXTRA_TEXT`).
 * @param subject l'oggetto, per chi lo usa (una mail).
 * @param sizeBytes il peso totale, se noto: compare accanto al tipo («Immagine PNG · 245 KB»).
 * @param fileName il nome del file, per il tipo («PNG») e per «Salva in File»; di serie [title].
 * @param preview la miniatura gia' pronta. Senza, il foglio la prepara da se' dal primo Uri di un'immagine.
 * @param detail una parola in piu' sotto il nome, dopo il tipo e il peso («Posizione inclusa», come Foto in iOS 26).
 * @param print il documento da stampare quando l'oggetto non e' gia' stampabile (un testo lungo, una pagina): con lui
 *   «Stampa» passa dal PDF impaginato dell'engine. Un'immagine o un PDF si stampano anche senza.
 * @param localFiles i file dell'app fuori dalla cartella dell'engine, ancora da copiare: li riempie [file] con un [File],
 *   li svuota [FluidShareFiles.stage]. Il foglio li copia da se' fuori dal thread principale, con l'attesa nell'anteprima,
 *   un tetto e un messaggio se non partono; chi usa [FluidShareIntents] senza il foglio chiama prima `stage`.
 * @param sensitive vero per un contenuto delicato (un codice, una password, un dato di salute): «Copia» segna gli
 *   appunti come delicati (`ClipDescription.EXTRA_IS_SENSITIVE`, Android 13 e oltre; prima non c'e' niente da segnare), e
 *   Android non ne mostra il testo nell'anteprima della copia ne' nei suggerimenti della tastiera. Di serie falso (R2).
 */
@Immutable
data class FluidShareItem(
  val title: String,
  val mimeType: String,
  val uris: List<Uri> = emptyList(),
  val text: String? = null,
  val subject: String? = null,
  val sizeBytes: Long? = null,
  val fileName: String? = null,
  val preview: ImageBitmap? = null,
  val print: FluidPdfDocument? = null,
  val detail: String? = null,
  val localFiles: List<File> = emptyList(),
  val sensitive: Boolean = false,
) {
  /** Vero se si mandano dei file. */
  val hasFiles: Boolean get() = uris.isNotEmpty() || localFiles.isNotEmpty()

  /**
   * Vero se qualche file va ancora copiato nella cartella dell'engine prima di partire ([FluidShareFiles.stage]): i
   * [localFiles], e un Uri `file://` messo in [uris] (R2: Android vieta di mandarlo a un'altra app, e `startActivity`
   * lancerebbe `FileUriExposedException`; la copia lo fa diventare un `content://`).
   */
  val needsCopy: Boolean get() = localFiles.isNotEmpty() || uris.any(::fluidIsFileUri)

  /** Quanti file si mandano. */
  val fileCount: Int get() = uris.size + localFiles.size

  /** Vero se e' un link solo: un testo che e' un indirizzo web. */
  val isLink: Boolean get() = !hasFiles && text != null && fluidLooksLikeLink(text)

  /** Il nome che va in «Salva in File». */
  val saveName: String get() = fileName ?: title

  companion object {
    /** Un testo. */
    fun text(text: String, title: String = text.lineSequence().first().take(60), subject: String? = null): FluidShareItem =
      FluidShareItem(title = title, mimeType = "text/plain", text = text, subject = subject)

    /** Un link: il foglio mostra il dominio sotto il titolo e «Mostra come QR». */
    fun link(url: String, title: String = url): FluidShareItem =
      FluidShareItem(title = title, mimeType = "text/plain", text = url)

    /** Un file gia' esposto da un FileProvider (o da un altro `ContentProvider` dell'app). */
    fun file(
      uri: Uri,
      fileName: String,
      mimeType: String,
      sizeBytes: Long? = null,
      preview: ImageBitmap? = null,
      title: String = fileName,
    ): FluidShareItem = FluidShareItem(
      title = title, mimeType = mimeType, uris = listOf(uri), sizeBytes = sizeBytes, fileName = fileName, preview = preview,
    )

    /**
     * Un file qualsiasi dell'app. Nella cartella dell'engine ([FluidShareFiles.newFile]) l'Uri c'e' subito; altrove (una
     * foto in `filesDir`, un documento dell'app) il file resta in [localFiles] e si copia quando si condivide. Non lancia
     * mai: un file che non c'e' o non si legge lo dice il foglio, al posto delle azioni.
     */
    fun file(context: Context, file: File, mimeType: String, preview: ImageBitmap? = null, title: String = file.name): FluidShareItem {
      val size = file.length().takeIf { it > 0 }
      // R2 · Un'uscita che non c'e' piu' (la cache svuotata da Android) resta da controllare come un file da copiare:
      // il foglio dice perche' non parte invece di mandare un Uri morto.
      val uri = if (FluidShareFiles.isShareable(context, file) && file.isFile) runCatching { FluidShareFiles.uriFor(context, file) }.getOrNull() else null
      return if (uri != null) {
        file(uri, file.name, mimeType, size, preview, title)
      } else {
        FluidShareItem(title = title, mimeType = mimeType, sizeBytes = size, fileName = file.name, preview = preview, localFiles = listOf(file))
      }
    }

    /** Piu' file dell'app insieme, dentro o fuori la cartella dell'engine, come [file]: quelli fuori si copiano quando si condivide. */
    fun files(context: Context, files: List<File>, title: String, mimeType: String, preview: ImageBitmap? = null): FluidShareItem =
      FluidShareItem(
        title = title,
        mimeType = mimeType,
        sizeBytes = files.sumOf { it.length() }.takeIf { it > 0 },
        fileName = files.singleOrNull()?.name,
        preview = preview,
        localFiles = files,
      )

    /** Piu' file insieme (le foto di un album): `SEND_MULTIPLE`. [mimeType] e' il tipo comune (per foto miste «image» con l'asterisco). */
    fun files(uris: List<Uri>, title: String, mimeType: String, sizeBytes: Long? = null, preview: ImageBitmap? = null): FluidShareItem =
      FluidShareItem(title = title, mimeType = mimeType, uris = uris, sizeBytes = sizeBytes, preview = preview)
  }
}

/** Vero per un Uri `file://`: fra app non parte mai, va copiato prima. */
internal fun fluidIsFileUri(uri: Uri): Boolean = "file".equals(uri.scheme, ignoreCase = true)

/** Vero se [text] e' un solo indirizzo web (niente spazi, schema http o https). */
internal fun fluidLooksLikeLink(text: String): Boolean {
  val t = text.trim()
  if (t.isEmpty() || t.any { it.isWhitespace() }) return false
  val lower = t.lowercase(Locale.ROOT)
  return (lower.startsWith("https://") || lower.startsWith("http://")) && t.length > 8
}

/** Il dominio di un link, senza `www.`, per il sottotitolo del foglio; null se non si legge. */
internal fun fluidLinkHost(text: String): String? = runCatching {
  Uri.parse(text.trim()).host?.removePrefix("www.")
}.getOrNull()?.takeIf { it.isNotBlank() }
