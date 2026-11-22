package dev.antigravity.fluidengine.sample.viewer

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// F6C.5 · I file della galleria «Visori».
//
// La galleria fa quello che fa un'app vera: i file stanno nella sua cartella (`filesDir/visori`), si aprono con
// engine-viewer da un `Uri` (`rememberFluidViewerFiles`), e «Apri con…» e «Condividi» li passano al sistema. Le copie
// si fanno una volta dalle risorse dell'APK; la prova di Universal Converter ha la sua sottocartella.

/** Che cosa mostra la galleria per un file: serve alle miniature, allo strumento e al pannello. */
internal enum class VisoreTipo(val etichetta: String) {
  Foto("Foto"),
  Animata("Immagine animata"),
  Video("Video"),
  Audio("Audio"),
  Pagine("Documento a pagine"),
  Testo("Testo"),
  Codice("Codice"),
  Sconosciuto("Sconosciuto"),
  Eseguibile("Eseguibile"),
  Vuoto("File vuoto"),
  Sparito("File sparito"),
}

/**
 * Un file di esempio. [resource] è la risorsa da cui si copia (una foto di `drawable-nodpi` o un file di `raw`); null
 * per i file che la galleria crea da sé (il file vuoto) o che non ci sono (il file sparito).
 */
@Immutable
internal data class VisoreFile(
  val id: String,
  val title: String,
  val name: String,
  val mime: String?,
  val resource: Int?,
  val tipo: VisoreTipo,
  val credit: String,
  val cover: Int? = null,
  /** Per la prova di Universal Converter: che cosa fa oggi l'app con questo file (`PreviewLoader.kt:30-45`). */
  val today: String? = null,
)

internal object VisoriFiles {
  /** I sei tipi della prima versione (scelta di Alessio del 21 novembre 2026), più lo sconosciuto e l'eseguibile. */
  val gallery: List<VisoreFile> = listOf(
    VisoreFile("lancio", "Lancio", "Lancio.jpg", "image/jpeg", R.drawable.viewer_rocket, VisoreTipo.Foto,
      "Fotografia di SpaceX, via scikit-image", cover = R.drawable.viewer_rocket),
    VisoreFile("collins", "Eileen Collins", "Eileen Collins.jpg", "image/jpeg", R.drawable.viewer_astronaut,
      VisoreTipo.Foto, "Fotografia della NASA, via scikit-image", cover = R.drawable.viewer_astronaut),
    VisoreFile("bolla", "Bolla di vetro", "Bolla.webp", "image/webp", R.raw.viewer_bolla, VisoreTipo.Animata,
      "WebP animata, 30 fotogrammi, disegnata per la galleria"),
    VisoreFile("fiori", "Fiori al vento", "Fiori al vento.mp4", "video/mp4", R.raw.viewer_flower, VisoreTipo.Video,
      "Video CC0 della raccolta MDN", cover = R.drawable.viewer_flower),
    VisoreFile("campanelle", "Campanelle", "Campanelle.m4a", "audio/mp4", R.raw.viewer_campanelle, VisoreTipo.Audio,
      "Dodici secondi di note pure. Copertina dal paesaggio della galleria e tre punti d'ascolto a intervalli di quattro secondi.",
      cover = R.drawable.viewer_campanelle_cover),
    VisoreFile("quaderno", "Quaderno di campo", "Quaderno di campo.pdf", "application/pdf", R.raw.viewer_notes,
      VisoreTipo.Pagine, "PDF di sei pagine, quattro verticali e due orizzontali"),
    VisoreFile("diario", "Diario di bordo", "Diario di bordo.txt", "text/plain", R.raw.viewer_diario, VisoreTipo.Testo,
      "Testo semplice, UTF-8"),
    VisoreFile("codice", "Visore.kt", "Visore.kt", null, R.raw.viewer_codice, VisoreTipo.Codice,
      "Kotlin, colori di sintassi di engine-viewer"),
    VisoreFile("scena", "Scena 3D", "Scena.blend", null, R.raw.viewer_scena, VisoreTipo.Sconosciuto,
      "Un file di Blender: il 3D è fuori dalla prima versione"),
    VisoreFile("aggiornamento", "Aggiornamento", "Aggiornamento.apk", "application/vnd.android.package-archive",
      R.raw.viewer_installer, VisoreTipo.Eseguibile, "Il pacchetto installato di Fluid Glass"),
  )

  /**
   * La prova in sola lettura con Universal Converter (F6C.5): i tipi delle sue prove (`convert/test/resources` e
   * `terraria-wld-parser/test`), aperti dal telaio e dal risolutore di engine-viewer invece che dal suo
   * `PreviewLoader`. I due file minimi sono quelli dell'app; video, audio e il mondo `.wld` sono dello stesso tipo ma
   * nostri (`doom.mp4` e `gaster.mp3` contengono materiale di terzi, i mondi di Terraria pesano fino a 12 MB).
   */
  val converter: List<VisoreFile> = listOf(
    VisoreFile("uc-colori", "colors_50x50.png", "colors_50x50.png", "image/png", R.raw.viewer_uc_colori,
      VisoreTipo.Foto, "Dalle prove di Universal Converter (GPL-2.0)", today = "La apre già: immagine"),
    VisoreFile("uc-markdown", "markdown.md", "markdown.md", "text/markdown", R.raw.viewer_uc_markdown,
      VisoreTipo.Codice, "Dalle prove di Universal Converter (GPL-2.0)",
      today = "Lo apre già: testo, senza colori né «trova»"),
    VisoreFile("uc-filmato", "filmato.mp4", "filmato.mp4", "video/mp4", R.raw.viewer_flower, VisoreTipo.Video,
      "Al posto di doom.mp4: il video CC0 della raccolta MDN", today = "Dump esadecimale"),
    VisoreFile("uc-audio", "audio.mp3", "audio.mp3", "audio/mpeg", R.raw.viewer_uc_audio, VisoreTipo.Audio,
      "Al posto di gaster.mp3: note pure generate", today = "Dump esadecimale"),
    VisoreFile("uc-documento", "documento.pdf", "documento.pdf", "application/pdf", R.raw.viewer_notes,
      VisoreTipo.Pagine, "Il quaderno di campo della galleria", today = "Dump esadecimale"),
    VisoreFile("uc-lettera", "lettera.txt", "lettera.txt", "text/plain", R.raw.viewer_uc_lettera, VisoreTipo.Testo,
      "Testo UTF-16 con il BOM", today = "Dump esadecimale: vede i byte zero e lo crede binario"),
    VisoreFile("uc-relazione", "relazione.docx", "relazione.docx",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document", R.raw.viewer_uc_relazione,
      VisoreTipo.Sconosciuto, "Un documento Office minimo: come word.docx", today = "Dump esadecimale"),
    VisoreFile("uc-mondo", "mondo.wld", "mondo.wld", null, R.raw.viewer_scena, VisoreTipo.Sconosciuto,
      "Un binario col nome di un mondo di Terraria", today = "Dump esadecimale"),
    VisoreFile("uc-vuoto", "vuoto.txt", "vuoto.txt", "text/plain", null, VisoreTipo.Vuoto,
      "Zero byte", today = "«Il file è vuoto.»"),
    VisoreFile("uc-sparito", "sparito.pdf", "sparito.pdf", "application/pdf", null, VisoreTipo.Sparito,
      "Cancellato dopo la conversione", today = "«Non riesco a leggere il file.»"),
  )

  /** Cambia quando cambiano i file di esempio: una cartella nuova li ricopia tutti. */
  private const val Version = 1

  fun folder(context: Context, converter: Boolean): File =
    File(context.filesDir, "visori-v$Version/" + if (converter) "converter" else "galleria")

  /**
   * Copia i file nella cartella dell'app (una volta: un file della stessa lunghezza resta) e dà i `FluidViewerFile`
   * da aprire. Il file sparito ha un `Uri` che non porta a niente: è il caso «illeggibile» di Universal Converter.
   */
  suspend fun prepare(context: Context, files: List<VisoreFile>, converter: Boolean): List<FluidViewerFile> =
    withContext(Dispatchers.IO) {
      val app = context.applicationContext
      val dir = folder(app, converter).apply { mkdirs() }
      files.map { file ->
        val installed = if (file.id == "aggiornamento") File(app.applicationInfo.sourceDir).takeIf { it.isFile } else null
        val target = installed ?: File(dir, file.name)
        when {
          installed != null -> Unit
          file.tipo == VisoreTipo.Sparito -> target.delete()
          file.resource == null -> if (!target.exists() || target.length() != 0L) target.writeBytes(ByteArray(0))
          // Le risorse compresse non danno un descrittore: la cartella porta la versione dei file ([Version]).
          !target.exists() || target.length() == 0L -> {
            val partial = File(dir, "${file.name}.parte")
            app.resources.openRawResource(file.resource).use { input -> partial.outputStream().use(input::copyTo) }
            partial.renameTo(target)
          }
        }
        FluidViewerFile(Uri.fromFile(target), file.name, file.mime, target.takeIf { it.exists() }?.length())
      }
    }

  /** L'indirizzo della risorsa da cui viene il file: lo leggono anche le altre app, senza un FileProvider. */
  fun shareableUri(context: Context, file: VisoreFile): Uri? {
    if (file.id == "aggiornamento") {
      val installed = File(context.applicationInfo.sourceDir)
      if (installed.isFile) return VisoriPackageProvider.uri(context)
    }
    return file.resource?.let {
    Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE).authority(context.packageName).appendPath(it.toString()).build()
    }
  }

  /**
   * «Apri con…» e «Condividi» come in Universal Converter (`ACTION_VIEW` e `ACTION_SEND` col tipo del file), dal
   * selettore di sistema. Se nessuna app risponde l'eccezione arriva al preset, che lo dice nella scheda.
   */
  fun openWith(context: Context, file: VisoreFile) {
    val uri = shareableUri(context, file) ?: throw ActivityNotFoundException(file.name)
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(view, file.name))
  }

  fun share(context: Context, file: VisoreFile) {
    val uri = shareableUri(context, file) ?: throw ActivityNotFoundException(file.name)
    val send = Intent(Intent.ACTION_SEND).setType(file.mime ?: "application/octet-stream")
      .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, file.name))
  }

  /** I primi byte di un file, per il dump che lo sconosciuto mostra solo a richiesta. */
  suspend fun firstBytes(file: FluidViewerFile, limit: Int): ByteArray = withContext(Dispatchers.IO) {
    File(requireNotNull(file.uri.path)).inputStream().use { input ->
      val buffer = ByteArray(limit)
      var filled = 0
      while (filled < limit) {
        val read = input.read(buffer, filled, limit - filled)
        if (read < 0) break
        filled += read
      }
      buffer.copyOf(filled)
    }
  }
}
