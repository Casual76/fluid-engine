# API · engine-feedback

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-feedback/feedback/FluidFeedback.kt`

- `class FluidFeedbackGitHub(val owner: String, val repo: String, val template: String? = null, val labels: List<String> = emptyList())` — Dove si apre una issue su GitHub: `https://github.com/[owner]/[repo]/issues/new`. [template] è il nome di un modello della repo (`.github/ISSUE_TEMPLATE/segnalazione.md`): un modello mette da sé le etichette anche a chi non può metterle, mentre [labels] nell'indirizzo valgono solo per chi può già etichettare la repo (documentazione di GitHub, «Creating an issue from a URL query»).
  - `val repoUrl: String get()` — La pagina della repo.
- `class FluidFeedbackConfig(val appName: String, val github: FluidFeedbackGitHub? = null, val logLines: Int = 40, val crashes: Int = 3)` — Come si comporta «Segnala un problema» in un'app.
- `data class FluidFeedbackDraft(val description: String = "", val screenshot: File? = null, val includeScreenshot: Boolean = true, val includeDevice: Boolean = true, val includeExperiments: Boolean = true, val includeCrashes: Boolean = true, val includeLog: Boolean = true, val credit: String = "")` — Cosa sta scrivendo l'utente: la frase, lo screenshot segnato (un file dell'app) e cosa allegare. Un valore intero: il foglio lo sostituisce a ogni cambio, sul thread principale.
  - `val canSend: Boolean get()` — Vero se c'è qualcosa da mandare: una frase o uno screenshot.
  - `val attachedScreenshot: File? get()` — Lo screenshot che parte davvero (nullo se tolto o spento).
- `class FluidFeedbackInfo(val appName: String, val appId: String, val appVersion: String, val versionCode: Long, val buildType: String, val engineVersion: String, val device: String, val android: String, val language: String, val screen: String, val appearance: String, val experiments: List<String>, val crashes: List<String>, val log: List<String>, val createdAtMillis: Long)` — Le informazioni che una segnalazione può allegare, raccolte quando il foglio si apre ([collect]). Niente di nascosto: il foglio le mostra tutte, e parte solo ciò che l'utente lascia acceso.
  - `fun deviceRows(labels: FluidFeedbackLabels): List<Pair<String, String>>` — Le righe «etichetta — valore» dell'app e del telefono, nell'ordine in cui si leggono.
  - `companion object`
    - `fun collect(context: Context, config: FluidFeedbackConfig, lab: FluidLab? = null, diagnostics: FluidDiagnostics? = FluidDiagnostics.get(), now: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): FluidFeedbackInfo` — Raccoglie le informazioni adesso. Legge la coda della diagnostica (se c'è): da un filo in sottofondo. Il registro esce già coi dati personali tolti ([FluidLog.export]); i crash portano solo il tipo e il primo passaggio del codice dell'app.
- `object FluidFeedbackText` — Il testo di una segnalazione (F8.24): lo stesso per il file e per la issue, così quello che si vede nel foglio è quello che parte. Le sezioni spente non ci sono.
  - `const val MaxIssueUrlLength: Int` — La lunghezza massima dell'indirizzo della issue: GitHub rifiuta gli indirizzi troppo lunghi (a occhio, 8 kB).
  - `fun title(draft: FluidFeedbackDraft, info: FluidFeedbackInfo, labels: FluidFeedbackLabels = FluidFeedbackLabels()): String` — Il titolo della issue: la prima riga della frase, al massimo 80 caratteri, dopo l'app e la versione.
  - `fun issueBody(draft: FluidFeedbackDraft, info: FluidFeedbackInfo, labels: FluidFeedbackLabels = FluidFeedbackLabels(), screenshotName: String? = null, logLimit: Int = info.log.size, crashLimit: Int = info.crashes.size): String` — Il corpo della issue in Markdown: «Cosa non va», l'app e il telefono in una tabella, le funzioni in prova, gli ultimi crash, gli ultimi [logLimit] eventi in un blocco richiudibile, dove allegare lo screenshot ([screenshotName]) e i ringraziamenti. La frase dell'utente passa dal filtro dei dati personali: la issue è pubblica.
  - `fun issueUrl(draft: FluidFeedbackDraft, info: FluidFeedbackInfo, github: FluidFeedbackGitHub, labels: FluidFeedbackLabels = FluidFeedbackLabels(), screenshotName: String? = null, maxLength: Int = MaxIssueUrlLength): String` — L'indirizzo della pagina «nuova issue» di [github] con titolo e corpo già scritti. Se è troppo lungo per GitHub si accorcia togliendo prima gli eventi più vecchi, poi i crash, poi la coda della frase: non si taglia mai a metà di un carattere codificato.
  - `fun fileName(info: FluidFeedbackInfo, extension: String): String` — Il nome di un file della segnalazione: `Segnalazione-pampa-notes-20261126-1403.zip`.

## `engine-feedback/feedback/FluidFeedbackFile.kt`

- `object FluidFeedbackFile` — Il file di una segnalazione (F8.24b, scelta di Alessio): **uno ZIP** da mandare su WhatsApp come documento, col foglio Condividi dell'engine. Dentro, nell'ordine: - `LEGGIMI.txt`, il primo, da leggere senza programmi: la frase, l'app e il telefono, le funzioni in prova, gli ultimi arresti, i ringraziamenti, e cosa c'è negli altri file; - `segnalazione.json`, gli stessi dati per i programmi (lo schema [JsonSchema]);
  - `const val MimeType: String` — Il tipo dello ZIP: WhatsApp lo manda come documento.
  - `const val JsonSchema: Int` — La versione di `segnalazione.json`.
  - `object Names` — I nomi dei file dentro lo ZIP.
    - `const val Readme: String`
    - `const val Json: String`
    - `const val Screenshot: String`
    - `const val Log: String`
  - `fun entries(draft: FluidFeedbackDraft, info: FluidFeedbackInfo, screenshot: ByteArray?, labels: FluidFeedbackLabels = FluidFeedbackLabels(), locale: Locale = Locale.getDefault()): List<Pair<String, ByteArray>>` — I file dello ZIP, nell'ordine in cui entrano (`LEGGIMI.txt` per primo), ognuno coi suoi byte. Puro: lo provano i test senza scrivere niente. [screenshot] sono i byte del PNG segnato (nulla per nessuno).
  - `fun readme(draft: FluidFeedbackDraft, info: FluidFeedbackInfo, labels: FluidFeedbackLabels = FluidFeedbackLabels(), locale: Locale = Locale.getDefault(), withScreenshot: Boolean = draft.attachedScreenshot != null, withLog: Boolean = draft.includeLog && info.log.isNotEmpty()): String` — Il testo di `LEGGIMI.txt`, coi fine riga di Windows (si apre bene anche nel Blocco note).
  - `fun json(draft: FluidFeedbackDraft, info: FluidFeedbackInfo, withScreenshot: Boolean): String` — Il testo di `segnalazione.json`: gli stessi dati di `LEGGIMI.txt`, per i programmi. Le parti spente non ci sono.
  - `fun writeZip(out: OutputStream, entries: List<Pair<String, ByteArray>>, timeMillis: Long)` — Scrive lo ZIP con [entries] in [out]. Le voci hanno l'ora della segnalazione ([timeMillis]): lo stesso ZIP due volte ha gli stessi byte. Da un filo in sottofondo.
  - `suspend fun writeToFile(context: Context, draft: FluidFeedbackDraft, info: FluidFeedbackInfo, labels: FluidFeedbackLabels = FluidFeedbackLabels(), locale: Locale = Locale.getDefault()): File` — Lo ZIP della segnalazione in un file nuovo della cartella delle uscite di engine-ui (quella del foglio Condividi), col nome chiaro `Segnalazione-<app>-<AAAAMMGG-hhmm>.zip`. Legge lo screenshot segnato dal suo file. Su `Dispatchers.IO`.
  - `fun loadScreenshot(file: File, maxSide: Int = 1600): ImageBitmap?` — Lo screenshot di [file] per la miniatura del foglio, ridotto a [maxSide] pixel. Da un filo in sottofondo.
  - `suspend fun saveToDownloads(context: Context, file: File, name: String): Uri?` — Copia lo screenshot nella cartella Download del telefono col nome [name], per allegarlo alla issue di GitHub dal browser. Android 10+, senza permessi (MediaStore). Torna l'Uri, o nulla sui telefoni più vecchi.
  - `fun workDir(context: Context): File` — La cartella dei file di lavoro delle segnalazioni, nella cache dell'app (Android la svuota se serve spazio).
- `object FluidFeedbackCapture` — Lo screenshot di una finestra dell'app (F8.24): `PixelCopy` di ciò che la finestra mostra, con il vetro e tutto, senza permessi e senza vedere le altre app. Android 8+.
  - `suspend fun window(activity: Activity): Bitmap` — Copia i pixel della finestra di [activity] in una bitmap. Dal thread principale; la copia la fa il sistema.
  - `fun writePng(bitmap: Bitmap, file: File)` — Scrive [bitmap] in PNG in [file]. Da un filo in sottofondo.

## `engine-feedback/feedback/FluidFeedbackLabels.kt`

- `data class FluidFeedbackLabels(val title: String = "Segnala un problema", val rowSubtitle: String = "Uno screenshot e due righe: arrivano allo svil…, val close: String = "Chiudi", val whatsWrong: String = "Cosa non va?", val whatsWrongPlaceholder: String = "Cosa stavi facendo, cosa ti aspettavi, cosa è successo", val noDescription: String = "(nessuna descrizione)", val untitled: String = "Segnalazione", val screenshot: String = "Screenshot", val capturePage: String = "Cattura una pagina", val pickPhoto: String = "Scegli dalle foto", val markAgain: String = "Segna", val removeScreenshot: String = "Togli", val screenshotDescription: String = "Lo screenshot della segnalazione", val screenshotWarning: String = "Lo screenshot può mostrare dati tuoi: coprili…, val captureHint: String = "Vai alla pagina col problema, poi tocca qui", val captureAction: String = "Cattura", val captureCancel: String = "Annulla", val markTitle: String = "Segna il problema", val sends: String = "Cosa parte", val sendsScreenshot: String = "Lo screenshot", val sendsDevice: String = "L'app e il telefono", val sendsExperiments: (Int) -> String = { n -> if (n == 1) "1 funzione in prova accesa"…, val sendsCrashes: (Int) -> String = { n -> if (n == 1) "L'ultimo arresto dell'app"…, val sendsLog: (Int) -> String = { n -> "Gli ultimi $n eventi, senza dati person…, val showWhatSends: String = "Mostra tutto quello che parte", val hideWhatSends: String = "Nascondi", val credit: String = "Il tuo nome per i ringraziamenti", val creditPlaceholder: String = "Facoltativo: un nome o @utente di GitHub", val sendFile: String = "Manda un file", val sendFileDetail: String = "Un file ZIP con tutto, da mandare su WhatsApp o dove vuoi", val openIssue: String = "Apri una issue su GitHub", val openIssueDetail: String = "Pubblica, col tuo nome utente: ti dà i crediti della segnalazione", val issueFootnote: String = "Su GitHub la segnalazione è pubblica. Si apre…, dal tuo account.", val issueScreenshotSaved: (String) -> String = { name -> "Lo screenshot è in Download ($name):…, val preparing: String = "Preparo il file…", val failed: String = "Non sono riuscito a preparare il file", val thanks: String = "Grazie della segnalazione!", val where: String = "Dove", val experiments: String = "Funzioni in prova accese", val crashes: String = "Ultimi arresti", val log: String = "Ultimi eventi", val logSummary: (Int) -> String = { n -> "Ultimi eventi ($n)" }, val issueScreenshotHint: (String) -> String = { name -> "Allegalo qui sotto: è nella cartella…, val issueCredit: (credit: String, appName: String) -> String = { credit, appName -> if (credit.isBlank()) "Seg…, val fileTitle: String = "Segnalazione", val fileCredit: (String) -> String = { credit -> "Segnalato da $credit" }, val rowApp: String = "App", val rowEngine: String = "Fluid Engine", val rowDevice: String = "Telefono", val rowAndroid: String = "Android", val rowLanguage: String = "Lingua", val rowScreen: String = "Schermo", val rowAppearance: String = "Aspetto", val whatsapp: String = "WhatsApp", val readmeContents: String = "In questo file", val readmeJson: (String) -> String = { name -> "- $name: gli stessi dati, per i prog…, val readmeScreenshot: (String) -> String = { name -> "- $name: lo screenshot segnato" }, val readmeLog: (name: String, lines: Int) -> String = { name, lines -> if (lines == 1) "- $name: l'ul…, val readmePrivacy: String = "I dati personali (mail, telefoni, chiavi, indi…, " + "prima che il file partisse.")` — Le parole di «Segnala un problema» (F8.24). In italiano di serie; [english] è il punto di partenza per un'app tradotta. Le frasi con un pezzo variabile sono funzioni.
  - `companion object`
    - `fun english(): FluidFeedbackLabels` — Le stesse parole in inglese.

## `engine-feedback/feedback/FluidFeedbackUi.kt`

- `object FluidFeedbackTags` — I tag dei test di «Segnala un problema».
  - `const val Sheet`
  - `const val Row`
  - `const val Description`
  - `const val Capture`
  - `const val Pick`
  - `const val Thumbnail`
  - `const val Mark`
  - `const val Remove`
  - `const val Pill`
  - `const val PillCapture`
  - `const val PillCancel`
  - `const val Markup`
  - `const val SendFile`
  - `const val OpenIssue`
  - `const val Preview`
  - `const val PreviewToggle`
  - `const val Credit`
  - `fun include(part: String): String` — L'interruttore di una parte («screenshot», «telefono», «prove», «arresti», «eventi»).
- `class FluidFeedbackState internal constructor(val config: FluidFeedbackConfig, internal val lab: FluidLab?, internal val diagnostics: FluidDiagnostics?)` — Lo stato di «Segnala un problema» di un'app (F8.24): la frase, lo screenshot e cosa allegare restano anche se il foglio si chiude, finché la segnalazione non parte. Si ottiene con [rememberFluidFeedbackState] e si passa a [FluidFeedbackHost]; [open] apre il foglio da qualunque punto (la riga [FluidFeedbackRow], il Laboratorio).
  - `var draft by mutableStateOf(FluidFeedbackDraft())` — Quello che l'utente sta scrivendo.
  - `var info by mutableStateOf<FluidFeedbackInfo?>(null)` — Le informazioni raccolte all'apertura del foglio, o nulla finché non sono pronte.
  - `val isOpen: Boolean get()` — Vero se il foglio, la capsula o la segnatura sono aperti.
  - `fun open()` — Apre il foglio (dalla riga di «Informazioni», dal Laboratorio, da un tasto dell'app).
  - `fun close()` — Chiude tutto, tenendo la bozza per la prossima volta.
  - `fun update(transform: (FluidFeedbackDraft) -> FluidFeedbackDraft)` — Cambia la bozza (la frase, cosa allegare).
  - `fun discard()` — Dimentica la bozza: dopo l'invio, o quando l'utente la butta.
- `val LocalFluidFeedback` — La segnalazione della pagina, se l'app ha un [FluidFeedbackHost] sopra: `LocalFluidFeedback.current?.open()`.
- `@Composable fun rememberFluidFeedbackState(config: FluidFeedbackConfig, lab: FluidLab? = null, diagnostics: FluidDiagnostics? = FluidDiagnostics.get()): FluidFeedbackState` — Lo stato di «Segnala un problema» ([FluidFeedbackState]). [lab] aggiunge le funzioni in prova accese; [diagnostics] gli ultimi arresti della coda (di serie quella installata nel processo).
- `@Composable fun FluidFeedbackHost(state: FluidFeedbackState, modifier: Modifier = Modifier, labels: FluidFeedbackLabels = FluidFeedbackLabels(), content: @Composable () -> Unit)` — L'ospite di «Segnala un problema» (F8.24): si mette **una volta**, attorno al contenuto dell'app (dentro `FluidTheme`, attorno al `FluidNavHost`), perché la capsula della cattura deve restare sopra mentre l'utente cambia pagina. Porta il foglio, la capsula, la segnatura dello screenshot e il foglio Condividi; fornisce [LocalFluidFeedback].
- `@Composable fun FluidFeedbackRow(modifier: Modifier = Modifier, state: FluidFeedbackState? = LocalFluidFeedback.current, labels: FluidFeedbackLabels = FluidFeedbackLabels())` — La riga «Segnala un problema» per la pagina «Informazioni» (in un `FluidListGroup`). Apre il foglio di [state], o di [LocalFluidFeedback] se non lo si passa.
