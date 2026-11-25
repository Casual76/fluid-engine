# API · engine-playback

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-playback/playback/FluidPlayback.kt`

- `data class FluidPlaybackItem(val id: String, val uri: Uri, val title: String, val subtitle: String? = null, val artworkUri: Uri? = null, val durationMillis: Long? = null, val mimeType: String? = null)` — Una cosa da ascoltare (F8.13): un memo, una lezione, un brano. È il catalogo dell'app ridotto a quello che serve al sistema: dove sta il file, il titolo e il sottotitolo che la notifica e la schermata di blocco mostrano, la copertina.
- `fun FluidPlaybackItem.toMediaItem(): MediaItem` — Il [MediaItem] di Media3 per [this]: l'indirizzo anche nei metadati della richiesta, che attraversano la sessione.
- `fun MediaItem.toFluidPlaybackItem(): FluidPlaybackItem?` — L'elemento di un [MediaItem] scritto da [toMediaItem] (o da chiunque metta l'indirizzo nei metadati), o null.
- `data class FluidPlaybackConfig(val speech: Boolean = true, val skipBackMillis: Long = 15_000L, val skipForwardMillis: Long = 15_000L, val restoreOnStart: Boolean = true, val sessionActivity: ((Context) -> PendingIntent?)? = null, val networkWakeLock: Boolean = false)` — Come suona il servizio di [FluidPlayback]. Di serie quello di un memo o di una lezione: il parlato, i salti da 15 secondi di Memo Vocali, la coda ricordata.
- `object FluidPlayback` — La **riproduzione di sistema** (F8.13, idea `riproduzione-di-sistema`), il modulo opt-in `engine-playback`.
  - `var config: FluidPlaybackConfig` — La configurazione di adesso.
  - `fun configure(config: FluidPlaybackConfig)` — Cambia la configurazione. Vale per il servizio che parte dopo; uno già avviato tiene la sua.
  - `const val CommandSleepTimer: String` — Il comando del timer di spegnimento: [ArgMinutes] (0 lo spegne) o [ArgEndOfItem].
  - `const val CommandSkipSilence: String` — Il comando del salto dei silenzi: [ArgEnabled].
  - `const val ArgMinutes: String`
  - `const val ArgEndOfItem: String`
  - `const val ArgEnabled: String`
  - `const val ExtraSleepAt: String` — Negli extra della sessione: quando scatta il timer (millisecondi di `System.currentTimeMillis`), o assente.
  - `const val ExtraSleepAtEndOfItem: String` — Negli extra della sessione: vero se il timer scatta a fine elemento.
  - `const val ExtraSkipSilence: String` — Negli extra della sessione: vero se il salto dei silenzi è acceso.
- `val FluidPlaybackLicenses: List<FluidLicense>` — Le opere di terzi che un'app con `engine-playback` porta con sé: Media3 (Apache-2.0). Da aggiungere a `FluidEngineLicenses` in `fluidLicensesSection(licences = …)`.

## `engine-playback/playback/FluidPlaybackController.kt`

- `class FluidPlaybackController internal constructor(val player: Player) : FluidMediaSession, FluidAudioSpeed, FluidMediaDurationLoader, AutoCloseable` — Il **controllo** della riproduzione di sistema per l'interfaccia (F8.13): il `MediaController` di Media3 collegato a [FluidPlaybackService], letto in stati di Compose. È anche la [FluidMediaSession] (con la velocità, [FluidAudioSpeed]) che leggono il visore audio e l'onda di `engine-ui`: lo stesso stato alimenta la pagina, il mini-riproduttore, la notifica e la schermata di blocco.
  - `var buffering: Boolean by mutableStateOf(false)` — Vero mentre carica.
  - `var ended: Boolean by mutableStateOf(false)` — Vero quando l'elemento è finito: [play] riparte da capo.
  - `var item: FluidPlaybackItem? by mutableStateOf(null)` — L'elemento che si ascolta, o null con la coda vuota.
  - `var index: Int by mutableIntStateOf(0)` — La posizione nella coda e quanto è lunga.
  - `var queueSize: Int by mutableIntStateOf(0)`
  - `var sleepAtMillis: Long? by mutableStateOf(null)` — Quando scatta il timer, in millisecondi di `System.currentTimeMillis`, o null.
  - `var sleepAtEndOfItem: Boolean by mutableStateOf(false)` — Vero se il timer scatta a fine elemento.
  - `var skipSilence: Boolean by mutableStateOf(false)` — Vero se il salto dei silenzi è acceso.
  - `fun attach()` — Comincia ad ascoltare il lettore. [close] smette e scollega.
  - `fun setItems(items: List<FluidPlaybackItem>, startIndex: Int = 0, startPositionMillis: Long = 0L, play: Boolean = true)` — Mette in coda [items] e comincia da [startIndex] a [startPositionMillis]. Con [play] falso prepara e basta: la lezione si apre al punto giusto senza suonare.
  - `fun isCurrent(id: String): Boolean` — Vero se si sta già ascoltando l'elemento con [id].
  - `fun skipBy(deltaMillis: Long)` — Indietro o avanti di [deltaMillis], dentro l'elemento.
  - `fun setSleepTimer(minutes: Int)` — Il timer: fra [minutes] minuti; 0 lo spegne.
  - `fun sleepAtEndOfItem()` — Il timer a fine elemento.
  - `fun cancelSleepTimer()` — Spegne il timer.
  - `fun changeSkipSilence(enabled: Boolean)` — Accende o spegne il salto dei silenzi.
  - `fun refresh()` — Rilegge tutto dal lettore. La chiamano gli eventi.
  - `suspend fun followPosition()` — La posizione a ogni fotogramma mentre suona; ferma quando non suona. Non torna: si annulla.
  - `companion object`
    - `suspend fun connect(context: Context): FluidPlaybackController` — Si collega al servizio di riproduzione (che parte, se non c'è) e torna il controllo, già attaccato. Sul thread principale. Da chiudere con [close].
- `@Composable fun rememberFluidPlaybackController(): FluidPlaybackController?` — Il controllo della riproduzione per questa composizione: null finché si collega (un attimo), poi il controllo, che segue la posizione a ogni fotogramma mentre suona e si scollega quando la composizione se ne va. La riproduzione no: continua nel servizio, con la notifica.

## `engine-playback/playback/FluidPlaybackService.kt`

- `open class FluidPlaybackService : MediaSessionService()` — Il **servizio di riproduzione** di `engine-playback` (F8.13): un `MediaSessionService` di Media3 con un ExoPlayer, già dichiarato nel manifesto del modulo. L'app non lo avvia mai a mano: lo avvia [FluidPlaybackController] collegandosi, e il sistema (la notifica, la schermata di blocco, i tasti delle cuffie, l'orologio, l'auto) attraverso la sessione.
  - `var session: MediaSession?` — La sessione, finché il servizio vive. Per le prove e per chi estende il servizio.
  - `val player: ExoPlayer? get()` — Il lettore, finché il servizio vive.
  - `fun setSkipSilence(enabled: Boolean)` — Accende o spegne il salto dei silenzi.
  - `fun setSleepTimer(minutes: Int, endOfItem: Boolean = false)` — Il timer: fra [minutes] minuti (0 lo spegne), o a fine elemento con [endOfItem].
