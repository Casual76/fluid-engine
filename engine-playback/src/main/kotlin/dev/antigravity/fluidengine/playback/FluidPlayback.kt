package dev.antigravity.fluidengine.playback

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.antigravity.fluidengine.ui.fluid.FluidLicense

/**
 * Una cosa da ascoltare (F8.13): un memo, una lezione, un brano. È il catalogo dell'app ridotto a quello che serve al
 * sistema: dove sta il file, il titolo e il sottotitolo che la notifica e la schermata di blocco mostrano, la copertina.
 *
 * L'indirizzo ([uri]) può essere `file://`, `content://`, `android.resource://` o `https://`. Viaggia dentro la sessione
 * (nei metadati della richiesta), quindi il servizio non deve chiedere all'app «che cos'è questo id»: anche dopo un
 * riavvio, la coda ricordata basta a sé.
 */
@Immutable
data class FluidPlaybackItem(
  /** Un identificativo stabile dell'app (l'id della nota, del memo): torna in [FluidPlaybackController.item]. */
  val id: String,
  val uri: Uri,
  val title: String,
  /** La riga sotto il titolo: il quaderno, l'autore, la data. */
  val subtitle: String? = null,
  val artworkUri: Uri? = null,
  /** La durata, se l'app la sa già: la barra della notifica la mostra prima che il file sia aperto. */
  val durationMillis: Long? = null,
  /** Il tipo MIME, se il nome del file non lo dice. */
  val mimeType: String? = null,
)

/** Il [MediaItem] di Media3 per [this]: l'indirizzo anche nei metadati della richiesta, che attraversano la sessione. */
fun FluidPlaybackItem.toMediaItem(): MediaItem {
  val metadata = MediaMetadata.Builder()
    .setTitle(title)
    .setArtist(subtitle)
    .setArtworkUri(artworkUri)
    .setIsPlayable(true)
    .setIsBrowsable(false)
    .apply { durationMillis?.let { setExtras(Bundle().apply { putLong(ExtraDuration, it) }) } }
    .build()
  return MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMimeType(mimeType)
    .setMediaMetadata(metadata)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .build()
}

/** L'elemento di un [MediaItem] scritto da [toMediaItem] (o da chiunque metta l'indirizzo nei metadati), o null. */
fun MediaItem.toFluidPlaybackItem(): FluidPlaybackItem? {
  val uri = localConfiguration?.uri ?: requestMetadata.mediaUri ?: return null
  val metadata = mediaMetadata
  return FluidPlaybackItem(
    id = mediaId,
    uri = uri,
    title = metadata.title?.toString() ?: metadata.displayTitle?.toString() ?: "",
    subtitle = metadata.artist?.toString(),
    artworkUri = metadata.artworkUri,
    durationMillis = metadata.extras?.getLong(ExtraDuration, C.TIME_UNSET)?.takeIf { it != C.TIME_UNSET && it >= 0L },
    mimeType = localConfiguration?.mimeType,
  )
}

private const val ExtraDuration = "dev.antigravity.fluidengine.playback.DURATION"

/**
 * Come suona il servizio di [FluidPlayback]. Di serie quello di un memo o di una lezione: il parlato, i salti da 15
 * secondi di Memo Vocali, la coda ricordata.
 *
 * @param speech il tipo del contenuto per il sistema: parlato (vero) o musica. Cambia come Android abbassa il volume
 *   quando un'altra app parla sopra (il parlato si mette in pausa, la musica si abbassa).
 * @param skipBackMillis e [skipForwardMillis] i salti dei tasti della notifica, delle cuffie e di [FluidPlaybackController]:
 *   15 s, come Memo Vocali (guida dell'iPhone, «Play it back», iOS 26). Un podcast tiene 15 e 30.
 * @param restoreOnStart all'avvio del servizio rimette la coda ricordata, ferma al punto giusto: riaprendo l'app la
 *   lezione c'è e non suona da sola.
 * @param sessionActivity cosa apre il tocco sulla notifica: di serie l'app (la sua attività di avvio).
 * @param networkWakeLock vero se si ascolta dalla rete: tiene sveglio anche il Wi-Fi a schermo spento.
 */
@Immutable
data class FluidPlaybackConfig(
  val speech: Boolean = true,
  val skipBackMillis: Long = 15_000L,
  val skipForwardMillis: Long = 15_000L,
  val restoreOnStart: Boolean = true,
  val sessionActivity: ((Context) -> PendingIntent?)? = null,
  val networkWakeLock: Boolean = false,
)

/**
 * La **riproduzione di sistema** (F8.13, idea `riproduzione-di-sistema`), il modulo opt-in `engine-playback`.
 *
 * L'app non dichiara niente nel manifesto: il servizio ([FluidPlaybackService]) e il ricevitore dei tasti delle cuffie
 * sono del modulo. Se vuole cambiare la configurazione la passa una volta, in `Application.onCreate` (il servizio può
 * ripartire da solo dopo che il sistema ha chiuso l'app, e legge [config] in quel momento):
 *
 * ```kotlin
 * FluidPlayback.configure(FluidPlaybackConfig(skipForwardMillis = 30_000L))
 * ```
 *
 * Poi l'interfaccia prende il suo controllo con `rememberFluidPlaybackController()` e gli passa cosa suonare.
 */
object FluidPlayback {

  /** La configurazione di adesso. */
  @Volatile
  var config: FluidPlaybackConfig = FluidPlaybackConfig()
    private set

  /** Cambia la configurazione. Vale per il servizio che parte dopo; uno già avviato tiene la sua. */
  fun configure(config: FluidPlaybackConfig) {
    this.config = config
  }

  /** Il comando del timer di spegnimento: [ArgMinutes] (0 lo spegne) o [ArgEndOfItem]. */
  const val CommandSleepTimer: String = "dev.antigravity.fluidengine.playback.SLEEP_TIMER"

  /** Il comando del salto dei silenzi: [ArgEnabled]. */
  const val CommandSkipSilence: String = "dev.antigravity.fluidengine.playback.SKIP_SILENCE"

  const val ArgMinutes: String = "minutes"
  const val ArgEndOfItem: String = "endOfItem"
  const val ArgEnabled: String = "enabled"

  /** Negli extra della sessione: quando scatta il timer (millisecondi di `System.currentTimeMillis`), o assente. */
  const val ExtraSleepAt: String = "dev.antigravity.fluidengine.playback.SLEEP_AT"

  /** Negli extra della sessione: vero se il timer scatta a fine elemento. */
  const val ExtraSleepAtEndOfItem: String = "dev.antigravity.fluidengine.playback.SLEEP_END_OF_ITEM"

  /** Negli extra della sessione: vero se il salto dei silenzi è acceso. */
  const val ExtraSkipSilence: String = "dev.antigravity.fluidengine.playback.SKIP_SILENCE_ON"
}

/**
 * Le opere di terzi che un'app con `engine-playback` porta con sé: Media3 (Apache-2.0). Da aggiungere a
 * `FluidEngineLicenses` in `fluidLicensesSection(licences = …)`.
 */
val FluidPlaybackLicenses: List<FluidLicense> = listOf(
  FluidLicense(
    name = "AndroidX Media3 (session, ExoPlayer)",
    author = "The Android Open Source Project",
    licence = "Apache License 2.0",
    url = "https://github.com/androidx/media",
    role = "La riproduzione fuori dall'app: la sessione, la notifica, la schermata di blocco, il lettore.",
  ),
)
