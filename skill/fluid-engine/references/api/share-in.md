# API · engine-share-in

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-share-in/sharein/FluidIncoming.kt`

- `class FluidIncomingFile(val uri: Uri, val mimeType: String?, val name: String?, val sizeBytes: Long?)` — Un file arrivato, prima della copia: l'Uri dell'altra app, il tipo, il nome e il peso se l'app li dice.
- `enum class FluidShareInProblem` — Perche' qualcosa non e' arrivato o non si tiene.
- `sealed class FluidIncoming` — Cio' che e' arrivato, in una delle quattro forme in cui Android consegna (`SEND`, `SEND_MULTIPLE`, `PROCESS_TEXT`, `VIEW`): un testo, un link, dei file, o niente che si possa tenere. Lo legge [FluidShareIn.read].
  - `class Text(val text: String, val subject: String?, val selection: Boolean = false, val readOnly: Boolean = true) : FluidIncoming()` — Un testo; [selection] vero se viene dal menu del testo selezionato (`PROCESS_TEXT`), [readOnly] se non si puo' sostituire.
  - `class Link(val url: String, val title: String?) : FluidIncoming()` — Un link, col titolo che l'app di partenza gli da' (Chrome lo mette nell'oggetto).
  - `class Files(val files: List<FluidIncomingFile>, val text: String? = null, val viewed: Boolean = false) : FluidIncoming()` — Dei file, con il testo che li accompagna se c'e'. [viewed] vero per «apri con» (`VIEW`).
  - `class Unsupported(val problem: FluidShareInProblem, val detail: String = "") : FluidIncoming()` — Niente da tenere, e perche'.
- `object FluidShareIn` — Lettura e copia degli arrivi da altre app (4.0, F8.10). Il flusso di un'attivita' che riceve:
  - `val Actions: Set<String>` — Le azioni che l'engine sa leggere.
  - `suspend fun read(context: Context, intent: Intent?, policy: FluidShareInPolicy = FluidShareInPolicy()): FluidIncoming` — Legge [intent]. Chiede al `ContentResolver` dell'altra app il tipo, il nome e il peso di ogni file: va su `Dispatchers.IO`. Dalla [policy] prende solo le autorita' fidate dell'app stessa; i limiti li applica [copy].

## `engine-share-in/sharein/FluidShareInCopy.kt`

- `data class FluidShareInPolicy(val acceptedTypes: List<String> = listOf("*/*"), val maxFileBytes: Long = 100L * 1024 * 1024, val maxTotalBytes: Long = 250L * 1024 * 1024, val maxFiles: Int = 20, val acceptText: Boolean = true, val trustedAuthorities: Set<String> = emptySet())` — Cosa l'app accetta da fuori: i tipi, i tetti di peso e di numero (4.0, F8.10). I tetti di serie sono quelli di una nota o di una mail (100 MB per file, 250 MB in tutto, 20 file); un'app che salva video sul NAS li alza.
  - `fun accepts(mimeType: String?): Boolean` — Vero se il tipo [mimeType] e' fra quelli accettati; un tipo ignoto passa solo con «* / *».
- `class FluidShareInProgress(val copiedBytes: Long, val totalBytes: Long?, val fileIndex: Int, val fileCount: Int)` — A che punto e' la copia: i byte copiati, il totale se noto, il file in corso.
  - `val fraction: Float? get()` — La frazione fatta, se il totale e' noto.
- `class FluidCopiedFile(val source: FluidIncomingFile, val file: File?, val problem: FluidShareInProblem?)` — Un file dopo la copia: la copia locale, o il motivo per cui non c'e'.
  - `val ok: Boolean get()`
- `object FluidShareInFiles` — Le copie degli arrivi stanno qui: `cacheDir/fluid-share-in/<una cartella per arrivo>`.
  - `const val Folder: String`
  - `fun root(context: Context): File`
  - `fun newSession(context: Context): File`
  - `fun clean(context: Context, olderThanMillis: Long = 24L * 60 * 60 * 1000, now: Long = System.currentTimeMillis())` — Toglie gli arrivi piu' vecchi di [olderThanMillis] che l'app non ha spostato. Fuori dal thread principale.
- `suspend fun FluidShareIn.copy(context: Context, files: List<FluidIncomingFile>, policy: FluidShareInPolicy, into: File = FluidShareInFiles.newSession(context), onProgress: (FluidShareInProgress) -> Unit = {}): List<FluidCopiedFile>` — Copia [files] in [into], uno alla volta, su `Dispatchers.IO` (mai sul thread principale).

## `engine-share-in/sharein/FluidShareInSheet.kt`

- `class FluidShareInDestination(val id: String, val title: String, val subtitle: String = "", val icon: ImageVector? = null)` — Dove l'app puo' mettere cio' che arriva: un quaderno, un album, una cartella del NAS.
- `class FluidShareInContent(val text: String?, val link: String?, val subject: String?, val files: List<File>, val selection: Boolean)` — Cio' che l'app salva: il testo o il link, e le copie locali dei file che sono arrivati bene.
- `enum class FluidShareInPhase` — A che punto e' l'arrivo.
- `class FluidShareInState internal constructor(val policy: FluidShareInPolicy)` — Lo stato di un arrivo (4.0, F8.10): legge l'intent e **copia subito** i file, su `Dispatchers.IO`, appena si compone ([rememberFluidShareInState]). Il foglio lo mostra; l'app lo legge per salvare.
  - `var incoming: FluidIncoming? by mutableStateOf(null)` — Cosa e' arrivato; null finche' non e' letto.
  - `var copies: List<FluidCopiedFile> by mutableStateOf(emptyList())` — I file dopo la copia (quelli arrivati e quelli scartati, col motivo).
  - `var progress: FluidShareInProgress? by mutableStateOf(null)` — L'avanzamento della copia; null fuori dalla copia.
  - `var phase: FluidShareInPhase by mutableStateOf(FluidShareInPhase.Reading)`
  - `val content: FluidShareInContent?` — Il contenuto da salvare, quando c'e' qualcosa da salvare.
  - `val rejected: List<FluidCopiedFile> get()` — I file scartati, col motivo.
  - `suspend fun receive(context: Context, intent: Intent?)` — Legge e copia. Chiamato una volta da [rememberFluidShareInState].
- `@Composable fun rememberFluidShareInState(intent: Intent?, policy: FluidShareInPolicy = FluidShareInPolicy()): FluidShareInState` — Lo stato dell'arrivo di [intent], che comincia subito a leggere e a copiare. La cartella delle copie si salva: dopo una rotazione l'arrivo si ricopia **nella stessa cartella** (gli stessi nomi), senza lasciare copie orfane (R2).
- `data class FluidShareInLabels(val cancel: String = "Annulla", val save: String = "Salva", val destination: String = "Dove", val reading: String = "Leggo cosa è arrivato…", val copying: String = "Copio %1\$d di %2\$d…", val saving: String = "Salvo…", val saved: String = "Salvato", val nothing: String = "Niente da salvare qui.", val textSelection: String = "Testo selezionato", val rejected: String = "Non arrivati: %s", val tooLarge: String = "troppo grande", val tooMany: String = "troppi file", val typeNotAccepted: String = "tipo non accettato", val unreadable: String = "non si legge", val unsafe: String = "non sicuro", val notAShare: String = "non è una condivisione", val empty: String = "vuoto", val failed: String = "Non si è salvato")` — Le parole del foglio, di serie in italiano.
- `object FluidShareInTags` — I tag dei test e delle riprese.
  - `const val Sheet`
  - `const val Scrim`
  - `const val Save`
  - `const val Cancel`
  - `const val Preview`
  - `const val Status`
  - `fun destination(id: String): String`
- `@Composable fun FluidShareInSheet(state: FluidShareInState, destinations: List<FluidShareInDestination>, onSave: suspend (FluidShareInDestination, FluidShareInContent) -> Unit, onClose: () -> Unit, title: String, initialDestination: String? = null, labels: FluidShareInLabels = FluidShareInLabels())` — Il foglio degli arrivi (4.0, F8.10, `arrivi-da-altre-app`): la Share Extension di iOS, sopra l'app di partenza. «Annulla», il titolo e «Salva» in cima; l'anteprima di cio' che arriva (il testo, il link, i file con il loro stato); l'avanzamento della copia; le destinazioni ([destinations], la scelta di partenza da una scorciatoia di condivisione).
- `object FluidShareInWindow` — La finestra dell'attivita' che riceve (4.0, F8.10): trasparente sopra l'app di partenza, da bordo a bordo, con la sfocatura di sistema dietro (Android 12 e oltre, `FLAG_BLUR_BEHIND`) quando il dispositivo la permette. Da chiamare in `onCreate`, prima di `setContent`, in un'attivita' col tema `Theme.FluidShareIn`.
  - `const val BlurDp: Float` — Il raggio della sfocatura dietro, a occhio (24 novembre 2026): quanto basta perche' il testo dietro non si legga.
  - `fun install(activity: Activity)`
  - `fun blurAvailable(context: Context): Boolean` — Vero se Android sfoca dietro la finestra (Android 12 e oltre, sfocatura fra finestre accesa).
- `object FluidShareInShortcuts` — Le scorciatoie di condivisione (4.0, F8.10): le ultime destinazioni scelte compaiono in alto nel foglio di Android, come i suggerimenti di iOS. L'ordine lo decide Android, non l'app. L'app dichiara in `res/xml/shortcuts.xml` un `<share-target>` con la sua attivita' e la stessa [category], e mette il meta-data `android.app.shortcuts` su un'attivita' di lancio.
  - `fun publish(context: Context, destination: FluidShareInDestination, activity: Class<out Activity>, category: String)` — Pubblica (o rinfresca) [destination] come destinazione di condivisione verso [activity].
  - `fun destinationOf(intent: Intent?): String?` — La destinazione chiesta dalla scorciatoia che ha aperto l'attivita', se e' una delle nostre.
