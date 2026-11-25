# API · engine-foundation

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-foundation/foundation/AppUpdate.kt`

- `enum class UpdateChannel` — Which release line an app follows.
- `data class AvailableAppUpdate(val version: String, val changelog: String, val releaseTag: String, val apkAsset: String, val downloadUrl: String, val sizeBytes: Long, val sha256: String = "")` — A release the manifest is offering, already resolved to something downloadable.
- `sealed interface AppUpdateInstallState` — Where an install has got to.
  - `data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : AppUpdateInstallState`
  - `data class Verifying(val message: String) : AppUpdateInstallState`
  - `data class Installing(val message: String) : AppUpdateInstallState`
  - `data class AwaitingUserAction(val message: String) : AppUpdateInstallState` — Android is showing its own confirmation; nothing will move until the user answers it.
  - `data class Installed(val filePath: String) : AppUpdateInstallState`
  - `data class Error(val message: String, val kind: AppUpdateErrorKind = AppUpdateErrorKind.Failed) : AppUpdateInstallState` — The install stopped. [kind] says why when the reason changes what the screen should offer (3.0.1): [AppUpdateErrorKind.InstallPermission] is not a failure to show, it is Android asking once for the permission to install from this app — the screen says so and offers the settings, instead of a red "update failed".
- `enum class AppUpdateErrorKind` — Why an install stopped, when it matters to what comes next. Since 3.0.1.
- `interface AppUpdater` — In-app updates for apps distributed outside a store.
  - `suspend fun check(currentVersionName: String, channel: UpdateChannel = UpdateChannel.STABLE, ignoredVersion: String = ""): Result<AvailableAppUpdate?>`
  - `fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState>`

## `engine-foundation/foundation/EngineConfig.kt`

- `data class EngineFlag(val key: String, val default: Boolean = false)` — A flag, declared where it is used.
- `data class EngineConfig(val schema: Int = EngineManifestSchema, val minimumVersion: String? = null, val recommendedVersion: String? = null, val notice: String? = null, val flags: Map<String, Boolean> = emptyMap(), val killSwitch: EngineKillSwitch = EngineKillSwitch(), val fetchedAtEpochMillis: Long? = null)` — The manifest as it applies to *this* app, at this version, right now.
  - `fun isEnabled(flag: EngineFlag): Boolean`
  - `fun isEnabled(key: String, default: Boolean = false): Boolean`
  - `fun compatibility(engineVersion: String = EngineBuild.VERSION): EngineCompatibility` — Whether this build's engine is behind what the manifest asks for.
  - `val isFallback: Boolean get()` — True before the first successful fetch: every value here is the built-in default.
  - `companion object`
    - `val Fallback` — What the app runs on until a manifest has been read — deliberately, an empty one.
- `fun resolveEngineConfig(manifest: EngineManifest, applicationId: String, fetchedAtEpochMillis: Long?): EngineConfig` — Folds the shared section and this app's override into one answer.

## `engine-foundation/foundation/EngineManifest.kt`

- `data class EngineManifest(val app: AppReleases? = null, val engine: EngineSection? = null)` — The one file that every app built on the engine reads.
- `data class AppReleases(val id: String = "", val packageName: String = "", val repository: ReleaseRepository? = null, val stable: AppRelease? = null, val beta: AppRelease? = null)`
- `data class ReleaseRepository(val owner: String, val name: String)`
- `data class AppRelease(val version: String, val changelog: String = "", val releaseTag: String = "", val apkAsset: String = "", val sizeBytes: Long = 0L, val releaseDate: String = "", val sha256: String = "", val highlights: List<FluidReleaseNote> = emptyList())`
- `data class EngineSection(val schema: Int = EngineManifestSchema, val minimumVersion: String? = null, val recommendedVersion: String? = null, val notice: String? = null, val flags: Map<String, Boolean> = emptyMap(), val killSwitch: EngineKillSwitch = EngineKillSwitch(), val overrides: Map<String, EngineOverride> = emptyMap())` — The remotely controlled half of the manifest.
- `data class EngineOverride(val minimumVersion: String? = null, val recommendedVersion: String? = null, val notice: String? = null, val flags: Map<String, Boolean> = emptyMap(), val killSwitch: EngineKillSwitch? = null)`
- `data class EngineKillSwitch(val enabled: Boolean = false, val message: String? = null)` — The last resort: a remote instruction to stop.
- `const val EngineManifestSchema: Int` — Bumped only when the meaning of an existing field changes; new optional fields do not count.
- `object EngineManifestParser` — Reads a manifest without a serialization plugin.
  - `fun parse(raw: String): EngineManifest`

## `engine-foundation/foundation/EngineSettings.kt`

- `enum class ThemeMode` — How the app decides between its light and dark palettes.
- `enum class AccentMode` — Where the accent colour comes from.
- `data class EngineSettings(val themeMode: ThemeMode = ThemeMode.SYSTEM, val accentMode: AccentMode = AccentMode.BRAND, val customAccentName: String = "fluid", val dynamicColorEnabled: Boolean = true, val amoledEnabled: Boolean = false, val hapticsEnabled: Boolean = true)` — Everything the engine needs to know to paint an app.

## `engine-foundation/foundation/EngineVersion.kt`

- `object EngineBuild` — Which engine this app was built against.
  - `const val VERSION: String`
- `fun compareVersions(left: String, right: String): Int` — Orders two versions the way a release train does: numerically segment by segment, with a pre-release suffix sorting *before* the release it leads to, so `1.2.0-beta1` < `1.2.0`.
- `fun isStableVersionNewer(candidate: String, current: String): Boolean` — Whether [candidate] is a stable release newer than [current].
- `enum class EngineCompatibility` — How far behind the engine this app carries is.
- `fun engineCompatibility(engineVersion: String = EngineBuild.VERSION, minimumVersion: String?, recommendedVersion: String?): EngineCompatibility`

## `engine-foundation/foundation/FluidDates.kt`

- `object FluidDates` — I giorni della settimana come li conta una lingua.
  - `fun firstDayOfWeek(locale: Locale): DayOfWeek` — Il primo giorno della settimana per [locale], dai dati CLDR della piattaforma (`WeekFields.of`): lunedi' in Italia e nel Regno Unito, domenica negli Stati Uniti e in Israele, sabato in Egitto. Su Android segue anche la scelta dell'utente nelle impostazioni («Primo giorno della settimana», Android 14), che arriva come estensione `fw` della lingua (`it-IT-u-fw-sun`).
  - `fun weekdays(locale: Locale): List<DayOfWeek>` — I sette giorni nell'ordine di [locale], dal primo.
  - `fun weekOf(date: LocalDate, locale: Locale): FluidDateRange` — La settimana di [date] nell'ordine di [locale], come intervallo di sette giorni.
- `data class FluidDateRange(override val start: LocalDate, override val endInclusive: LocalDate) : ClosedRange<LocalDate>, Iterable<LocalDate>` — Un intervallo di giorni, dal [start] al [endInclusive] compresi (F8.8, `intervalli-e-giorni-sparsi`): la scelta «dal… al…» del calendario. Vive su **date locali**: tre giorni sono tre giorni anche se in mezzo c'e' il cambio dell'ora, e un giorno solo e' un intervallo con lo stesso inizio e la stessa fine.
  - `val dayCount: Long get()` — Quanti giorni, contando il primo e l'ultimo.
  - `val isSingleDay: Boolean get()` — Vero se c'e' un giorno solo.
  - `fun startInstant(zone: ZoneId): Instant` — La mezzanotte del primo giorno in [zone] (la prima ora valida, se quella notte manca la mezzanotte).
  - `fun endInstantExclusive(zone: ZoneId): Instant` — La mezzanotte del giorno dopo l'ultimo in [zone]: la fine esclusa.
  - `fun duration(zone: ZoneId): Duration` — Quanto dura l'intervallo in [zone]: 24 ore per giorno, meno o piu' un'ora nei giorni del cambio.
  - `companion object`
    - `fun between(a: LocalDate, b: LocalDate): FluidDateRange` — L'intervallo fra [a] e [b] in qualunque ordine: il tocco «al» prima del «dal» non e' un errore.

## `engine-foundation/foundation/FluidFirstUse.kt`

- `interface FluidFirstUseMemory` — Dove l'engine ricorda le cose della prima volta: quale versione ha già mostrato il suo benvenuto, quali permessi ha già chiesto e quali spiegazioni ha già dato (F8.11).
  - `suspend fun read(key: String): String?` — Il valore della chiave, o nulla se non c'è.
  - `suspend fun write(key: String, value: String?)` — Scrive il valore della chiave; nulla la cancella.
  - `companion object`
    - `fun inMemory(): FluidFirstUseMemory` — Una memoria che dura quanto il processo: per i test e per le prove.
- `object FluidFirstUseKeys` — Le chiavi della memoria della prima volta. Un nome stabile: cambiarle fa rivedere a tutti il benvenuto.
  - `fun welcomeSeen(scope: String = "app"): String` — L'ultima versione per cui il benvenuto (o le novità) di [scope] è stato mostrato o saltato apposta.
  - `fun permissionAsked(id: String): String` — Il permesso [id] è già stato chiesto almeno una volta col dialogo di Android («1»).
  - `fun permissionExplained(id: String): String` — La spiegazione del permesso [id] è già stata mostrata una volta («1»): da lì in poi, il dialogo diretto.
- `enum class FluidWelcomeKind` — Che foglio mostrare all'apertura (F8.11, `benvenuto-e-novita`).
- `fun fluidWelcomeKind(currentVersion: String, seenVersion: String?, freshInstall: Boolean, hasNews: Boolean, hasWelcome: Boolean = true): FluidWelcomeKind?` — Se all'apertura va mostrato il benvenuto, le novità o niente. Pura: la chiama lo stato del foglio in `engine-ui`.
- `fun fluidWelcomeMarksSilently(currentVersion: String, seenVersion: String?): Boolean` — Se la versione corrente va segnata come vista anche senza mostrare niente: un aggiornamento senza novità, o una prima apertura senza benvenuto. Così una nota aggiunta dopo nel manifest non compare a chi quella versione l'ha già aperta.
- `data class FluidReleaseNote(val title: String, val text: String = "", val symbol: String? = null)` — Una novità di una versione: una riga del foglio «Novità di…», con il titolo, la frase e il nome di un simbolo facoltativo (`star`, `bolt`, `lock`…: l'app o `engine-ui` lo traducono in un'icona).
- `fun AppRelease.releaseNotes(): List<FluidReleaseNote>` — Le novità di questa release: le righe scritte apposta nel manifest (`highlights`) se ci sono, altrimenti il `changelog` diviso in righe ([fluidReleaseNotesFromChangelog]).
- `fun EngineManifest.releaseNotesFor(version: String, beta: Boolean = false): List<FluidReleaseNote>` — Le novità di [version] nel manifest dello store: della stabile o della beta, quella con la stessa versione ([beta] dice quale guardare per prima). Nessuna release con quella versione: nessuna novità, perché le note di un'altra versione non sono le novità di questa.
- `fun fluidReleaseNotesFromChangelog(changelog: String): List<FluidReleaseNote>` — Il `changelog` dello store diviso in righe di novità: una riga per voce (i trattini, i pallini e gli asterischi in testa si tolgono, i titoli Markdown si saltano). Una voce «Titolo: frase» con un titolo corto (al più [TitleMaxWords] parole e [TitleMaxChars] caratteri) diventa titolo e frase; le altre restano un titolo solo.
- `enum class FluidPermissionStatus` — Lo stato di un permesso (F8.11, `permesso-nel-punto-giusto`). Android ne dice due cose (concesso o no, e se mostrare una spiegazione), l'engine ricorda la terza (se l'ha già chiesto): insieme fanno questi cinque.
- `fun fluidPermissionStatus(granted: Boolean, partial: Boolean, asked: Boolean, rationale: Boolean): FluidPermissionStatus` — Lo stato di un permesso da quello che dice Android e da quello che ricorda l'engine.
- `fun fluidPermissionAfterRequest(previous: FluidPermissionStatus, granted: Boolean, partial: Boolean, rationale: Boolean): FluidPermissionStatus` — Lo stato dopo la risposta al dialogo di Android.
- `enum class FluidPermissionStep` — Cosa fa il tasto che ha bisogno di un permesso, quando lo si tocca.
- `fun fluidPermissionStep(status: FluidPermissionStatus, explained: Boolean, hasExplanation: Boolean = true, requestable: Boolean = true): FluidPermissionStep` — Il passo del tocco, dallo [status] e da quello che l'engine ricorda.

## `engine-foundation/foundation/FluidFormat.kt`

- `enum class FluidFormatWidth` — La larghezza delle parole, come `unitsStyle` di Apple: `.wide`, `.abbreviated`, `.narrow`.
- `enum class FluidByteStyle` — Come si contano i byte, come `ByteCountFormatStyle.Style` di Apple.
- `object FluidFormat` — Le funzioni di formattazione dell'engine. Ognuna prende la lingua (di serie quella del sistema) e, dove c'entra il tempo, l'istante «adesso» e il fuso: nessuna legge l'orologio di nascosto.
  - `fun date(instant: Instant, style: FormatStyle = FormatStyle.MEDIUM, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String` — La data nello stile della lingua: «23 nov 2026» (`MEDIUM`, come Foto e `Date.FormatStyle(date: .abbreviated)`; in inglese «Nov 23, 2026»), «23/11/26» (`SHORT`), «23 novembre 2026» (`LONG`), come `Date.FormatStyle(date:)`.
  - `fun date(day: LocalDate, style: FormatStyle = FormatStyle.MEDIUM, locale: Locale = Locale.getDefault()): String` — La data di un giorno del calendario, senza ora né fuso (una foto, una scadenza): come [date], «24 nov 2026». Il giorno non si sposta col fuso del telefono.
  - `fun time(instant: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String` — L'ora nello stile della lingua: «09:04» in italiano, «9:04 AM» in inglese, come `Date.FormatStyle(time: .shortened)`.
  - `fun dayAndTime(instant: Instant, now: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault(), includeTime: Boolean = true): String` — Il giorno e l'ora come li scrive una lista di messaggi: ieri, oggi e domani col nome del giorno («ieri, 09:04»), altrimenti la data media e l'ora. È il `Date.FormatStyle` con la data relativa di Apple (`doesRelativeDateFormatting` di `DateFormatter`), dalle stesse tabelle CLDR: il nome del giorno da `RelativeDateTimeFormatter`, l'unione di giorno e ora dal modello della lingua (`DateTimePatternGenerator.dateTimeForma…
  - `fun listDate(instant: Instant, now: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String` — La data corta di una riga di lista, come la colonna dell'ora di Mail e Messaggi: oggi l'ora («09:04»), ieri «ieri», nella settimana il giorno («lunedì»), nell'anno giorno e mese («12 mar»), prima la data corta («12/03/24»). Soglie di Mail su iOS 17, a occhio il 23 novembre 2026.
  - `fun relative(instant: Instant, now: Instant, locale: Locale = Locale.getDefault(), width: FluidFormatWidth = FluidFormatWidth.Short): String` — Quanto manca o quanto è passato: «ora», «12 min fa», «tra 2 giorni», come `Date.RelativeFormatStyle` di Apple (`presentation: .numeric`, con «ora» sotto il minuto come `.named`).
  - `fun relativeNextChangeMillis(instant: Instant, now: Instant): Long` — Fra quanti millisecondi, da [now], il testo di [relative] cambia: il passo giusto per un testo vivo, che così batte una volta al minuto a «12 min fa», una volta all'ora a «3 h fa» e non a ogni fotogramma. Sempre almeno 1 ms.
  - `fun bytes(count: Long, style: FluidByteStyle = FluidByteStyle.File, locale: Locale = Locale.getDefault()): String` — Un peso: «532 B», «2,4 MB», «1,1 GB», come `ByteCountFormatStyle` di Apple: una cifra decimale al massimo, niente gruppi, e mai «1000 KB» quando l'arrotondamento tocca l'unità dopo. È la stessa regola di `fluidFormatFileSize` dell'interfaccia, che ora la chiama.
  - `fun list(items: List<String>, locale: Locale = Locale.getDefault(), limit: Int = Int.MAX_VALUE, others: (Int) -> String = { n -> defaultOthers(n, locale) }): String` — Un elenco di nomi: «Anna, Luca e Marco», «Anna, Luca, and Marco», come `ListFormatStyle(.and)` di Apple (`ListFormatter` delle ICU, API 26). Con [limit] i nomi oltre il limite diventano «altri 3» («3 others»), ed è l'ultima voce della stessa lista: «Anna, Luca e altri 3».
  - `fun duration(duration: Duration, locale: Locale = Locale.getDefault(), width: FluidFormatWidth = FluidFormatWidth.Short, maxUnits: Int = 2): String` — Una durata in parole: «1 h e 25 min», «1 ora e 25 minuti», «45 s», come `Duration.UnitsFormatStyle` di Apple (`MeasureFormat` delle ICU). Mostra al più [maxUnits] unità, dalla più grande non nulla, e tronca le altre: 1 h 25 min 40 s con due unità è «1 h e 25 min». Zero è «0 s».
  - `fun timer(duration: Duration, locale: Locale = Locale.getDefault()): String` — Una durata da cronometro: «0:07», «25:07», «1:25:07», come `Duration.TimeFormatStyle` (`.hourMinuteSecond`) e `Text(timerInterval:)` di Apple: i minuti senza zero davanti sotto l'ora, le ore senza zero davanti. Si arrotonda per eccesso al secondo, come un conto alla rovescia che dice «0:01» fino all'ultimo istante. Le cifre sono quelle della lingua.
  - `fun number(value: Double, locale: Locale = Locale.getDefault(), fractionDigits: Int? = null): String` — Un numero con i separatori della lingua: «1.234,5». [fractionDigits] fissa le cifre decimali (di serie 0–2).
  - `fun percent(fraction: Double, locale: Locale = Locale.getDefault(), fractionDigits: Int = 0): String` — Una frazione come percentuale: 0,425 è «43%» (o «42,5%» con una cifra), con la forma della lingua.
  - `fun compact(value: Double, locale: Locale = Locale.getDefault(), width: FluidFormatWidth = FluidFormatWidth.Short): String` — Un numero grande in breve: «1,2 Mln», «3,4 Mrd» in italiano, «1.2K» in inglese, come `.notation(.compactName)` di Apple (`CompactDecimalFormat` delle ICU, stesse tabelle CLDR). In italiano le migliaia non si abbreviano: CLDR scrive «1200», come iOS.

## `engine-foundation/foundation/FluidPageAddress.kt`

- `data class FluidAddressParameters(val segments: List<String> = emptyList(), val query: Map<String, String> = emptyMap())` — I valori di una pagina già decodificati. Un segmento può contenere anche una barra o un +.
- `enum class FluidAddressAvailability` — I tre esiti dell'app. Per i dati riservati controllare l'accesso prima dell'esistenza.
- `class FluidAddressPage<P : Any, T : P>(internal val type: KClass<T>, internal val name: String, internal val decode: (FluidAddressParameters) -> T?, private val encode: (T) -> FluidAddressParameters, private val parent: (T) -> P?, internal val public: Boolean = false, private val availability: (T) -> FluidAddressAvailability = { FluidAddressAvailability.Available })` — Una riga per tipo concreto di pagina. [name] è la prima parola dell'indirizzo (es. «nota»). [decode] restituisce null per parametri inattesi. [encode] scrive gli stessi valori. [parent] indica il contesto naturale della pagina. [public] autorizza un ingresso da fuori.
- `data class FluidAddressRequest(val url: String, val trusted: Boolean = false)` — Un indirizzo in ingresso con l'esito della verifica del mittente. Di serie è esterno.
- `fun fluidAddressCallerTrusted(callerPackage: String?, allowedPackages: Set<String>, sameSignature: (String) -> Boolean): Boolean` — Richiede sia un nome ammesso sia la stessa firma. Il nome viene dal sistema operativo.
- `sealed interface FluidAddressResult<out P>` — La pila visibile, dalla radice alla cima, oppure un ingresso da ignorare.
  - `data class Open<P>(val stack: List<P>) : FluidAddressResult<P>` — Tutte le pagine sono disponibili.
  - `data class Missing<P>(val parents: List<P>) : FluidAddressResult<P>` — La prima pagina mancante. L'app presenta un avviso sopra i genitori disponibili.
  - `data class SignIn<P>(val parents: List<P>, val pending: FluidAddressRequest) : FluidAddressResult<P>` — Dopo l'accesso va rivalutata la richiesta originale, con la stessa provenienza.
  - `data object Rejected : FluidAddressResult<Nothing>` — Indirizzo malformato, sconosciuto o non pubblico per un mittente esterno.
- `class FluidAddressTable<P : Any>(private val scheme: String, pages: List<FluidAddressPage<P, out P>>, private val httpsHost: String? = null)` — La tabella bidirezionale dell'app. [httpsHost] abilita anche https://dominio/nome/parametri sul solo host esatto. L'associazione Android del dominio rimane a carico dell'app.
  - `fun addressOf(page: P): String` — L'indirizzo canonico della pagina, pronto per gli appunti o la condivisione.
  - `fun resolve(request: FluidAddressRequest): FluidAddressResult<P>` — Risolve fino a 64 pagine inclusa la destinazione. Per un esterno anche i genitori devono essere pubblici.

## `engine-foundation/foundation/FluidPasswordRules.kt`

- `class FluidPasswordCharset private constructor(val id: String, internal val test: (Char) -> Boolean, internal val pool: String)` — Un insieme di caratteri di una regola: le classi di Apple (`upper`, `lower`, `digit`, `special`, `ascii-printable`, `unicode`) o un insieme su misura (`[-().&@?'#,/"+]`).
  - `operator fun contains(c: Char): Boolean` — Vero se [c] è di questo insieme.
  - `companion object`
    - `val Upper: FluidPasswordCharset`
    - `val Lower: FluidPasswordCharset`
    - `val Digit: FluidPasswordCharset`
    - `val Special: FluidPasswordCharset`
    - `val AsciiPrintable: FluidPasswordCharset`
    - `val Unicode: FluidPasswordCharset`
    - `fun of(chars: String): FluidPasswordCharset` — Un insieme su misura, i caratteri come li scrive la regola fra parentesi quadre.
- `sealed class FluidPasswordRequirement` — Una regola sola, quella che il campo spunta mentre si scrive.
  - `data class MinLength(val count: Int) : FluidPasswordRequirement()` — Almeno [count] caratteri (`minlength`).
  - `data class MaxLength(val count: Int) : FluidPasswordRequirement()` — Al più [count] caratteri (`maxlength`).
  - `data class Contains(val any: List<FluidPasswordCharset>) : FluidPasswordRequirement()` — Almeno un carattere di uno degli [any] insiemi (un `required`).
  - `data class MaxConsecutive(val count: Int) : FluidPasswordRequirement()` — Mai più di [count] caratteri uguali di fila (`max-consecutive`).
  - `data class OnlyAllowed(val allowed: List<FluidPasswordCharset>) : FluidPasswordRequirement()` — Solo caratteri degli [allowed] insiemi (gli `allowed` e i `required` insieme).
- `data class FluidPasswordCheck(val requirement: FluidPasswordRequirement, val satisfied: Boolean)` — Una regola e se la password di adesso la rispetta.
- `class FluidPasswordRules(val minLength: Int = 8, val maxLength: Int? = null, val required: List<List<FluidPasswordCharset>> = emptyList(), val allowed: List<FluidPasswordCharset>? = null, val maxConsecutive: Int? = null)` — Le regole di un servizio: le dichiara l'app (scheda dell'idea: «le regole sono dati che l'app dichiara»), a mano o con la riga di Apple ([parse]). Servono a tre cose: le spunte ([check]), la conferma ([isSatisfiedBy]) e la password forte proposta ([generate]), che le rispetta sempre.
  - `val requirements: List<FluidPasswordRequirement>` — Le regole nell'ordine in cui il campo le mostra: lunghezza, classi, ripetizioni, caratteri ammessi.
  - `fun check(password: String): List<FluidPasswordCheck>` — Ogni regola con la sua spunta per [password].
  - `fun isSatisfiedBy(password: String): Boolean` — Vero se [password] rispetta tutte le regole.
  - `fun generate(random: Random = SecureRandom()): String` — Una password forte che rispetta queste regole, da [random] (di serie `SecureRandom`, mai un generatore prevedibile).
  - `companion object`
    - `val Default: FluidPasswordRules` — Le regole di serie: almeno 8 caratteri con una minuscola, una maiuscola e una cifra. È il minimo che quasi ogni servizio accetta (le linee guida NIST SP 800-63B chiedono 8 caratteri; le classi sono la richiesta più comune dei servizi delle app Pampa). A occhio, 23 novembre 2026.
    - `fun parse(rules: String): FluidPasswordRules` — Le regole scritte come le Password Rules di Apple: `minlength: 8; maxlength: 64; required: lower; required: upper; required: digit; required: [-().&@?'#,/"+]; max-consecutive: 2; allowed: ascii-printable`. Le proprietà sconosciute e i valori non validi si ignorano, come fa Safari.
- `data class FluidPasswordStrength(val level: Int, val bits: Double)` — La forza stimata di una password: **una stima semplice, dichiarata come tale** (scheda dell'idea: niente zxcvbn, «la forza stimata è un aiuto visivo»). Non promette niente: dice solo se una password è corta, prevedibile o comune.
  - `companion object`
    - `const val FairBits: Double` — Le soglie, in bit: sotto 36 debole, poi discreta fino a 60, buona fino a 80, forte da 80. A occhio, 23 novembre 2026, sulle fasce dei gestori di password (KeePass chiama «buone» le password da 64 bit, «forti» da 80; la password suggerita da Apple ne ha 71 per Apple e sta fra le forti con questa stima, che conta l'alfabeto e non il modello).
    - `const val GoodBits: Double`
    - `const val StrongBits: Double`
    - `fun estimate(password: String): FluidPasswordStrength` — La stima: - **l'alfabeto** è la somma delle classi usate (26 minuscole, 26 maiuscole, 10 cifre, 33 simboli, 100 per il resto); - **la lunghezza efficace** toglie tre quarti di carattere per ogni ripetizione («aaa») e per ogni passo di una sequenza («abc», «321», «qwe»); - **una password comune** (fra le più usate, anche con cifre o simboli in coda: «Password1!») resta debole.

## `engine-foundation/foundation/FluidRecurrence.kt`

- `enum class FluidRecurrenceFrequency` — Ogni quanto si ripete una regola: `EKRecurrenceFrequency` (`FREQ` di RFC 5545).
- `data class FluidRecurrenceDay(val dayOfWeek: DayOfWeek, val ordinal: Int = 0)` — Un giorno della settimana di una regola, con il suo numero: `EKRecurrenceDayOfWeek` (`BYDAY` di RFC 5545).
  - `companion object`
    - `fun last(day: DayOfWeek): FluidRecurrenceDay` — L'ultimo [day] del periodo («l'ultimo venerdì»).
- `sealed class FluidRecurrenceEnd` — Quando finisce una regola: `EKRecurrenceEnd` (`UNTIL` e `COUNT` di RFC 5545).
  - `data object Never : FluidRecurrenceEnd()` — Non finisce mai.
  - `data class OnDate(val date: LocalDate) : FluidRecurrenceEnd()` — L'ultima ricorrenza possibile e' il giorno [date], compreso (`UNTIL`).
  - `data class AfterCount(val count: Int) : FluidRecurrenceEnd()` — Dopo [count] ricorrenze, contando la prima (`COUNT`).
- `data class FluidRecurrenceRule(val frequency: FluidRecurrenceFrequency, val interval: Int = 1, val daysOfWeek: List<FluidRecurrenceDay> = emptyList(), val daysOfMonth: List<Int> = emptyList(), val monthsOfYear: List<Month> = emptyList(), val setPositions: List<Int> = emptyList(), val end: FluidRecurrenceEnd = FluidRecurrenceEnd.Never, val weekStart: DayOfWeek = DayOfWeek.MONDAY)` — La regola di una ricorrenza, come `EKRecurrenceRule` e la `RRULE` di RFC 5545.
  - `fun occurrences(start: LocalDate): Sequence<LocalDate>` — Le ricorrenze dal giorno [start] in poi, in ordine: una sequenza pigra, infinita se la regola non finisce. Una regola che non cade mai («ogni mese il 30» solo a febbraio) finisce da sola dopo 400 anni di periodi vuoti, un ciclo intero del calendario gregoriano.
  - `fun occurrences(start: LocalDate, limit: Int, from: LocalDate = start): List<LocalDate>` — Le prime [limit] ricorrenze dal giorno [from] in poi (di serie l'inizio), contate dall'inizio [start].
  - `fun occurrencesBetween(start: LocalDate, first: LocalDate, last: LocalDate): List<LocalDate>` — Le ricorrenze che cadono fra [first] e [last], compresi.
  - `fun occurrences(start: LocalDateTime, zone: ZoneId): Sequence<ZonedDateTime>` — Le ricorrenze con un'ora: la regola si espande in date ([occurrences]) e solo dopo ogni data prende l'ora di [start] nella [zone]. Nell'ora che manca (l'ora legale che comincia) l'ora va avanti della lunghezza del salto; nell'ora che si ripete vale la prima, come vuole RFC 5545 (§ 3.3.5) e come fa `ZonedDateTime.of`.
  - `fun toRRule(): String` — La regola nella sintassi di RFC 5545, senza `RRULE:` davanti: `FREQ=WEEKLY;BYDAY=MO,TH;UNTIL=20261220`.
  - `companion object`
    - `fun parseRRule(text: String): FluidRecurrenceRule` — Legge una regola di RFC 5545 (`FREQ=MONTHLY;BYDAY=-1FR`, anche con `RRULE:` davanti). Le parti che una regola di calendario non usa (`BYHOUR`, `BYWEEKNO`, `BYYEARDAY`…) fanno fallire la lettura invece di essere ignorate: una regola letta a meta' darebbe date sbagliate senza dirlo. `UNTIL` con un'ora vale per la sua data.

## `engine-foundation/foundation/FluidRecurrenceText.kt`

- `object FluidRecurrenceText` — La frase che riassume una [FluidRecurrenceRule] (F8.8): «Ogni lun e gio fino al 20 dicembre», «Every month on the last Friday, 10 times». La scrive il pannello «Ripeti» sopra le prossime date, come la riga di Calendario di Apple sotto la ripetizione personalizzata («L'evento si ripeterà ogni settimana il lunedì e il giovedì»), ma piu' corta: sta in una riga.
  - `fun describe(rule: FluidRecurrenceRule, start: LocalDate, locale: Locale): String` — La frase di [rule] cominciata il giorno [start] (serve ai campi vuoti, che prendono il giorno dall'inizio, e all'anno della fine, scritto solo se e' un altro). [locale] sceglie la lingua.

## `engine-foundation/foundation/FluidUnitFormat.kt`

- `object FluidUnitFormat` — Scrivere e leggere un numero con la sua unità nella lingua di chi legge (F8.3, `campo-con-unita`), pure e senza Android. È la parte delle `FormatStyle` di Apple che serve a un campo: `Measurement.FormatStyle(width: .abbreviated, usage: .asProvided)` e `Decimal.FormatStyle.Currency` col loro `parse`.
  - `const val NoBreakSpace: Char` — Lo spazio che non va a capo fra il numero e il simbolo.
  - `data class Separators(val decimal: Char, val grouping: Char)` — Il separatore decimale e quello delle migliaia di una lingua.
  - `fun separators(locale: Locale): Separators` — I separatori di [locale], dalle `DecimalFormatSymbols` della piattaforma. Lo spazio stretto delle migliaia (francese, U+202F) e quello largo restano quelli della lingua: in lettura valgono tutti.
  - `fun number(value: BigDecimal, locale: Locale, maxFractionDigits: Int, minFractionDigits: Int = 0, grouping: Boolean = true): String` — [value] scritto nella lingua di [locale], con al più [maxFractionDigits] cifre dopo la virgola (arrotondate al pari) e almeno [minFractionDigits]; le migliaia raggruppate se [grouping]. «1234,5» → «1.234,5» in italiano.
  - `fun group(digits: String, separator: Char): String` — Le cifre intere [digits] raggruppate a tre con [separator], dalle quattro cifre in su.
  - `fun symbol(unit: FluidUnit, locale: Locale): String` — Il simbolo di [unit] in [locale]: per una valuta quello della lingua («€», «$», «USD» in italiano), se no il suo.
  - `fun format(quantity: FluidQuantity, locale: Locale, maxFractionDigits: Int = quantity.unit.fractionDigits, minFractionDigits: Int = quantity.unit.minFractionDigits): String` — [quantity] scritta come la scriverebbe Apple con le `FormatStyle` abbreviate: «12,5 km», «21 °C», «12,50 €» in italiano; «12.5 km», «21°C», «€12.50» in inglese. Le cifre sono quelle dell'unità, se non le passi.
  - `fun attach(number: String, unit: FluidUnit, locale: Locale): String` — Il numero già scritto [number] col simbolo di [unit] al suo posto, secondo le regole della lingua.
  - `fun symbolBefore(unit: FluidUnit, locale: Locale): Boolean` — Dove sta il simbolo di [unit] in [locale], e se staccato: l'unica parte delle regole CLDR che serve qui. Pubblica per il campo, che mette la capsula dell'unità dallo stesso lato.
  - `fun name(unit: FluidUnit, locale: Locale, plural: Boolean = false): String?` — Il nome di [unit] in [locale], al singolare o al [plural]: «chilometro», «gradi Celsius». Null per le lingue senza le parole (ci sono italiano e inglese) e per le unità che non sono di serie.
  - `fun spoken(quantity: FluidQuantity, locale: Locale): String` — Come la legge TalkBack: «12,5 chilometri», «21 gradi Celsius», «1 metro». Le parole ci sono in italiano e in inglese per le unità di serie; per le altre (e per le valute, che TalkBack legge bene da sé) resta [format].
  - `fun parse(text: String, locale: Locale): BigDecimal?` — Un numero scritto da una persona o incollato da un'altra app, nella lingua di [locale] e con tolleranza: «1.234,5», «1 234,5», «1'234.5», «1,234.5», «−3», «12,5 km». Null se non è un numero.
  - `fun parseQuantity(text: String, locale: Locale, units: List<FluidUnit>, fallback: FluidUnit): FluidQuantity?` — Come [parse], e in più riconosce il simbolo di una delle [units] attaccato al numero («12,5 km», «€ 12,50»): la quantità ha quell'unità, o [fallback] se il testo non ne porta una. Null se non è un numero o se il simbolo è di un'unità che non è fra le [units].

## `engine-foundation/foundation/FluidUnits.kt`

- `class FluidUnitDimension(val id: String)` — Una grandezza fisica o di conto: lunghezza, massa, temperatura, valuta… Due unità si convertono solo se hanno la stessa dimensione. È un valore (uguale per [id]): un'app può dichiararne una sua, per esempio `FluidUnitDimension("punti")`.
  - `companion object`
    - `val Length: FluidUnitDimension`
    - `val Mass: FluidUnitDimension`
    - `val Temperature: FluidUnitDimension`
    - `val Volume: FluidUnitDimension`
    - `val Area: FluidUnitDimension`
    - `val Speed: FluidUnitDimension`
    - `val Duration: FluidUnitDimension`
    - `val Data: FluidUnitDimension`
    - `val Energy: FluidUnitDimension`
    - `val Percent: FluidUnitDimension`
    - `val Currency: FluidUnitDimension`
- `class FluidUnit private constructor(val id: String, val dimension: FluidUnitDimension, val symbol: String, internal val numerator: BigInteger?, internal val denominator: BigInteger, internal val shift: Ratio, val fractionDigits: Int, val minFractionDigits: Int, val currencyCode: String?)` — Una unità: il simbolo che si legge accanto al numero e come si arriva all'unità base della sua [dimension].
  - `val isConvertible: Boolean get()` — Vero se si converte: una valuta senza cambio dichiarato non si converte in nessun'altra.
  - `fun canConvertTo(other: FluidUnit): Boolean` — Vero se si converte in [other]: stessa dimensione ed entrambe con il loro fattore.
  - `companion object`
    - `fun linear(id: String, dimension: FluidUnitDimension, symbol: String, factor: String, fractionDigits: Int = 2, minFractionDigits: Int = 0): FluidUnit` — Una unità lineare: un'unità vale `factor` unità base. [factor] è una stringa decimale esatta ("1609.344") o una frazione ("5/9"), così non passa mai da un Double.
    - `fun affine(id: String, dimension: FluidUnitDimension, symbol: String, factor: String, shift: String, fractionDigits: Int = 2, minFractionDigits: Int = 0): FluidUnit` — Una unità con uno scostamento ([shift], sommato prima del fattore): i gradi Fahrenheit e i kelvin.
    - `fun currency(code: String, rate: String? = null, digits: Int? = null): FluidUnit` — Una valuta (ISO 4217, «EUR»). Le cifre decimali sono quelle della valuta (2 per l'euro, 0 per lo yen) se [digits] è null. Il cambio è dell'app ([rate]: quante unità della valuta base vale una di questa; null: non si converte), perché l'engine non sa quanto vale un dollaro oggi. Mai Double: [rate] è una stringa decimale.
- `data class FluidQuantity(val value: BigDecimal, val unit: FluidUnit)` — Un numero con la sua unità: quello che il campo con unità restituisce, mai la stringa (scheda dell'idea). È un valore: `FluidQuantity(BigDecimal("12.5"), FluidUnits.Kilometer)`.
  - `fun to(target: FluidUnit, scale: Int = target.fractionDigits): FluidQuantity?` — Lo stesso valore in [target], arrotondato a [scale] cifre (di serie quelle di [target]); null se non si converte.
  - `fun inBase(scale: Int = 12): BigDecimal?` — Il valore nell'unità base della dimensione, a [scale] cifre; null se l'unità non si converte.
- `object FluidUnits` — Le unità di serie e le conversioni. I fattori sono le definizioni esatte del SI e degli accordi internazionali (1 in = 2,54 cm dal 1959, 1 lb = 0,45359237 kg, 1 gal US = 231 in³ = 3,785411784 l, 1 kcal = 4184 J, 1 KiB = 1024 B): fiducia alta, sono definizioni, non misure.
  - `val Millimeter: FluidUnit`
  - `val Centimeter: FluidUnit`
  - `val Meter: FluidUnit`
  - `val Kilometer: FluidUnit`
  - `val Inch: FluidUnit`
  - `val Foot: FluidUnit`
  - `val Yard: FluidUnit`
  - `val Mile: FluidUnit`
  - `val Gram: FluidUnit`
  - `val Kilogram: FluidUnit`
  - `val Ounce: FluidUnit`
  - `val Pound: FluidUnit`
  - `val Celsius: FluidUnit`
  - `val Fahrenheit: FluidUnit`
  - `val Kelvin: FluidUnit`
  - `val Milliliter: FluidUnit`
  - `val Liter: FluidUnit`
  - `val CubicMeter: FluidUnit`
  - `val GallonUS: FluidUnit`
  - `val SquareMeter: FluidUnit`
  - `val Hectare: FluidUnit`
  - `val SquareKilometer: FluidUnit`
  - `val MetersPerSecond: FluidUnit`
  - `val KilometersPerHour: FluidUnit`
  - `val MilesPerHour: FluidUnit`
  - `val Knot: FluidUnit`
  - `val Second: FluidUnit`
  - `val Minute: FluidUnit`
  - `val Hour: FluidUnit`
  - `val Byte: FluidUnit`
  - `val Kilobyte: FluidUnit`
  - `val Megabyte: FluidUnit`
  - `val Gigabyte: FluidUnit`
  - `val Kibibyte: FluidUnit`
  - `val Mebibyte: FluidUnit`
  - `val Joule: FluidUnit`
  - `val Kilojoule: FluidUnit`
  - `val Kilocalorie: FluidUnit`
  - `val KilowattHour: FluidUnit`
  - `val Percent: FluidUnit`
  - `val Euro: FluidUnit`
  - `val Dollar: FluidUnit`
  - `val PoundSterling: FluidUnit`
  - `val all: List<FluidUnit>` — Tutte le unità di serie, nell'ordine delle dimensioni.
  - `fun byId(id: String): FluidUnit?` — L'unità di serie con questo [id] ("km", "celsius", "EUR"), o null.
  - `fun of(dimension: FluidUnitDimension): List<FluidUnit>` — Le unità di serie di una [dimension], nell'ordine della tabella: quelle che il campo offre di serie.
  - `fun convert(value: BigDecimal, from: FluidUnit, to: FluidUnit, scale: Int = to.fractionDigits): BigDecimal?` — [value] da [from] a [to], arrotondato a [scale] cifre dopo la virgola con l'arrotondamento al pari (quello di serie delle `FormatStyle` di Apple, `.toNearestOrEven`). Null se le due unità non si convertono.
  - `fun toBase(value: BigDecimal, unit: FluidUnit, scale: Int = 12): BigDecimal?` — [value] di [unit] nell'unità base della sua dimensione, a [scale] cifre. Null se [unit] non si converte.

## `engine-foundation/foundation/SyncStatus.kt`

- `enum class SyncState` — What the last attempt to refresh the app's data did.
- `data class SyncStatus(val state: SyncState = SyncState.IDLE, val lastSuccessfulSyncEpochMillis: Long? = null, val message: String? = null, val failedSections: List<String> = emptyList(), val notice: String? = null)` — The result of the app's last refresh, in the shape the design system's status components read.

## `engine-foundation/foundation/live/FluidLiveUpdate.kt`

- `class FluidLiveKind private constructor(val name: String, val promotable: Boolean)` — Il genere di un'attività viva: decide l'icona di serie, la categoria della notifica e se può diventare un Live Update.
  - `companion object`
    - `val Sync: FluidLiveKind` — Una sincronizzazione di sottofondo: una notifica con l'avanzamento, mai un Live Update.
    - `val Transfer: FluidLiveKind` — Un trasferimento chiesto dall'utente (uno scaricamento, un caricamento, una conversione).
    - `val Timer: FluidLiveKind` — Un timer avviato dall'utente.
    - `val Assistant: FluidLiveKind` — L'assistente che lavora a una risposta lunga chiesta dall'utente.
    - `val Recording: FluidLiveKind` — Una registrazione in corso.
    - `val Navigation: FluidLiveKind` — Un viaggio in corso (Fluid Transit): il caso tipico dei Live Update, con le tappe e le fermate.
    - `fun custom(name: String, promotable: Boolean = true): FluidLiveKind` — Un genere su misura.
    - `fun named(name: String): FluidLiveKind` — Il genere di serie col nome [name] (quello della pillola), o uno su misura promuovibile.
- `class FluidLiveTime private constructor(val anchorMillis: Long, private val mode: Int)` — Il tempo di un'attività viva, sull'orologio che non salta col cambio d'ora (`SystemClock.elapsedRealtime()`), come `FluidActivityTime` della pillola. Fuori dall'app diventa il cronometro della notifica, che il sistema fa scorrere da solo: nessun aggiornamento al secondo.
  - `val countsDown: Boolean get()` — Vero se il tempo scende verso [anchorMillis] (un timer).
  - `val running: Boolean get()` — Vero se il tempo scorre da solo (non è fermo).
  - `fun shownMillis(nowElapsedMillis: Long): Long` — I millisecondi da mostrare a [nowElapsedMillis], mai meno di zero.
  - `fun wallAnchorMillis(nowElapsedMillis: Long, nowWallMillis: Long): Long?` — L'istante sull'orologio di parete che corrisponde all'ancora, dati gli orologi di adesso: è il `when` del cronometro della notifica. Per un tempo fermo, null.
  - `companion object`
    - `fun since(startedAtElapsedMillis: Long): FluidLiveTime` — Il tempo trascorso da [startedAtElapsedMillis]: una registrazione.
    - `fun until(endsAtElapsedMillis: Long): FluidLiveTime` — Il tempo che resta fino a [endsAtElapsedMillis]: un timer, un arrivo.
    - `fun fixed(millis: Long): FluidLiveTime` — Un tempo fermo di [millis]: in pausa.
- `class FluidLiveStage(val length: Int, val color: Int? = null)` — Una tappa della barra a tappe (il `Segment` di `Notification.ProgressStyle` di Android 16): un pezzo del lavoro lungo [length] unità, con un colore facoltativo (ARGB). Le tappe di un viaggio sono i tratti: a piedi, il bus, a piedi.
- `class FluidLivePoint(val position: Int, val color: Int? = null)` — Un punto sulla barra a tappe (il `Point` di `ProgressStyle`): una fermata, a [position] unità dall'inizio.
- `class FluidLiveAction(val label: String, val onClick: () -> Unit, val destructive: Boolean = false, val key: String = label)` — Un comando dell'attività (pausa, annulla, termina): una scritta e cosa fa. Fuori dall'app è un tasto della notifica, che chiama la stessa funzione finché il processo dell'app è vivo (il lavoro lungo, di solito, lo tiene vivo un servizio). Se il processo è morto la notifica era vecchia: il tocco la toglie.
- `class FluidLiveOrigin private constructor(internal val code: Int)` — Chi ha avviato l'attività: le linee guida dei Live Update di Android li vogliono **solo per attività avviate dall'utente** («Don't allow activities triggered by other parties to generate Live Updates»), e un evento seguito in sottofondo vuole un tasto per smettere di seguirlo («Unpin»). Non è un `enum` (regola 3.11).
  - `val promotable: Boolean get()` — Vero se l'origine permette la promozione a Live Update.
  - `val followed: Boolean get()` — Vero se l'attività è di qualcun altro e l'utente ha scelto di seguirla: serve il tasto per smettere.
  - `companion object`
    - `val User: FluidLiveOrigin` — Avviata dall'utente su questo telefono (di serie).
    - `val Followed: FluidLiveOrigin` — Di un'altra persona, che l'utente ha **scelto di seguire** su questo telefono (il viaggio di Alessio che la mamma segue con «Avvisa quando arrivo»): può diventare Live Update, e l'engine aggiunge il tasto «Smetti di seguire».
    - `val Others: FluidLiveOrigin` — Avviata da altri senza una scelta dell'utente: una notifica con l'avanzamento, mai un Live Update.
- `data class FluidLiveUpdate(val id: String, val kind: FluidLiveKind, val title: String, val text: String? = null, val progress: Float? = null, val stages: List<FluidLiveStage> = emptyList(), val points: List<FluidLivePoint> = emptyList(), val time: FluidLiveTime? = null, val chip: String? = null, val paused: Boolean = false, val actions: List<FluidLiveAction> = emptyList(), val open: String? = null, val promote: Boolean? = null, val origin: FluidLiveOrigin = FluidLiveOrigin.User)` — Un'attività viva: la stessa descrizione per la pillola dentro l'app e per la notifica fuori. L'[id] è l'identità: pubblicare di nuovo lo stesso `id` aggiorna.
  - `val percent: Int? get()` — L'avanzamento come percentuale intera (0–100), o null.
  - `val indeterminate: Boolean get()` — Vero se la barra gira (nessuna misura e nessun tempo).
  - `val wantsPromotion: Boolean get()` — Vero se l'attività chiede di diventare Live Update: la scelta esplicita, o genere e origine che lo permettono.
- `class FluidLiveOutcome private constructor(internal val code: Int, val message: String?)` — Come finisce un'attività viva. Non è un `enum` (regola 3.11).
  - `val succeeded: Boolean get()` — Riuscita.
  - `val failed: Boolean get()` — Non riuscita.
  - `val silent: Boolean get()` — Finita senza esito da mostrare (annullata, o l'esito lo dice qualcun altro).
  - `companion object`
    - `fun succeeded(message: String? = null): FluidLiveOutcome` — Riuscita, con un messaggio facoltativo («Video pronto»).
    - `fun failed(message: String? = null): FluidLiveOutcome` — Non riuscita, con il perché («Rete assente»).
    - `val Silent: FluidLiveOutcome` — Finita senza esito: la notifica se ne va.
- `interface FluidLiveUpdateSink` — Dove va un'attività viva fuori dall'app: lo implementa `engine-notify` (`FluidLiveUpdates`). La pillola di engine-ui ci specchia le sue attività (`FluidActivityHostState.mirrorOutside`), un servizio ci pubblica direttamente.
  - `fun publish(update: FluidLiveUpdate)` — Pubblica [update], o lo aggiorna se c'è già un'attività con lo stesso `id`.
  - `fun finish(id: String, outcome: FluidLiveOutcome)` — Finisce l'attività [id] con [outcome].
  - `fun remove(id: String)` — Toglie l'attività [id] subito, senza esito.
  - `val outside: StateFlow<Boolean>` — Vero quando le attività si vedono fuori: per uno specchio della pillola, quando l'app non è in primo piano. Chi specchia rilegge l'avanzamento solo allora (dentro lo disegna la pillola).
  - `val sampleMillis: Long get()` — Ogni quanto ha senso rileggere un avanzamento che cambia di continuo, in millisecondi.

## `engine-foundation/foundation/memory/FluidMemoryBudget.kt`

- `class FluidMemoryUsage internal constructor(val limitBytes: Long, val usedBytes: Long, val inFlightBytes: Long, val peakBytes: Long, val refusals: Long)` — Lettura puntuale del registro. Il picco comprende insieme lavoro in volo e risorse residenti.
  - `val residentBytes: Long get()`
  - `val excessBytes: Long get()`
- `class FluidMemoryBudget(initialLimits: FluidMemoryLimits)` — Un registro per processo, fornito dal chiamante a tutti i produttori. Il medesimo lock copre entrambi i limiti e ogni passaggio di proprietà. Il registro contabilizza byte dichiarati e lascia al chiamante allocazione, pubblicazione, espulsione e distruzione. Nessuna callback entra nella sezione critica.
  - `fun tryReserve(category: FluidMemoryCategory, bytes: Long): Reservation?` — Prenota una stima conservativa positiva prima di avviare il produttore. Null permette espulsione o ripiego.
  - `fun updateLimits(newLimits: FluidMemoryLimits): Long` — Sostituisce i limiti conservando ogni spesa viva. Ritorna il minimo numero di byte da recuperare.
  - `fun usage(category: FluidMemoryCategory? = null): FluidMemoryUsage` — Diagnostica su richiesta, per il processo (senza argomento) o una categoria.

## `engine-foundation/foundation/memory/FluidMemoryLimits.kt`

- `enum class FluidMemoryCategory` — Il costo di una risorsa resta nella stessa categoria anche quando passa dalla cache allo schermo.
- `data class FluidMemoryLimits(val processBytes: Long, val glassBytes: Long, val otherCacheBytes: Long, val thumbnailBytes: Long, val viewerBytes: Long)` — Tetti indipendenti in byte. Più categorie possono competere per un tetto di processo più basso.
  - `operator fun get(category: FluidMemoryCategory): Long` — Quota effettiva della categoria per questa configurazione.
  - `fun forHeap(referenceHeapBytes: Long): FluidMemoryLimits` — Scala la riga già scelta dalla politica su min(somma, tetto di processo, heap di riferimento / 4). Le differenze fra somme cumulative arrotondate assegnano anche l'ultimo byte. Il calcolo esatto avviene solo quando cambia la politica, anche con quote la cui somma supera Long.MAX_VALUE.
  - `companion object`
    - `val Full` — Massimi iniziali F7, profilo Pieno. Applicare [forHeap] prima del primo lavoro.
    - `val Reduced` — Massimi iniziali F7, profilo Ridotto.
    - `val Protected` — Massimi iniziali F7, profilo Protetto.

## `engine-foundation/foundation/memory/FluidSurfaceBytes.kt`

- `fun fluidSurfaceBytes(width: Long, height: Long, bytesPerPixel: Int, copies: Int = 1, rowStrideBytes: Long? = null): Long` — Byte per una o più superfici vive contemporaneamente. Passare le dimensioni fisiche, comprensive del padding del filtro, e il formato effettivo (per esempio 4 byte/pixel per RGBA8 o 8 per RGBA FP16). [rowStrideBytes] comprende l'eventuale allineamento di riga. La dimensione del file su disco è indipendente.

## `engine-foundation/foundation/notify/FluidNotifyCenter.kt`

- `data class FluidNotifyChannelInfo(val id: String, val name: String, val description: String, val enabled: Boolean = true, val urgent: Boolean = false, val inDigest: Boolean = false, val group: String? = null)` — Un canale delle notifiche come lo vede l'utente: parole comuni, non nomi tecnici.
- `data class FluidNotifyDigestInfo(val enabled: Boolean = false, val times: List<Int> = listOf(8 * 60, 18 * 60), val waiting: Int = 0)` — Il riepilogo a orari scelti (il «Riepilogo programmato» di iOS): **spento di serie**, tutto arriva subito. Lo accende l'utente, e allora i canali che ha scelto arrivano raccolti agli orari [times].
- `class FluidNotifyLiveState private constructor(internal val code: Int)` — Lo stato dei Live Update dell'app (Android 16). Non è un `enum` (regola 3.11).
  - `val supported: Boolean get()` — Vero se il telefono li ha (Android 16).
  - `val allowed: Boolean get()` — Vero se l'utente li lascia accesi per l'app.
  - `companion object`
    - `val NotSupported: FluidNotifyLiveState` — Prima di Android 16: le attività fuori dall'app sono notifiche con l'avanzamento.
    - `val Allowed: FluidNotifyLiveState` — Accesi.
    - `val Off: FluidNotifyLiveState` — Spenti dall'utente nelle impostazioni dell'app.
- `data class FluidNotifySettings(val notificationsAllowed: Boolean = true, val channels: List<FluidNotifyChannelInfo> = emptyList(), val digest: FluidNotifyDigestInfo = FluidNotifyDigestInfo(), val live: FluidNotifyLiveState = FluidNotifyLiveState.NotSupported, val focusActive: Boolean = false)` — Le impostazioni delle notifiche come le disegna la pagina.
- `data class FluidReminderInfo(val id: String, val title: String, val text: String? = null, val atMillis: Long, val exact: Boolean, val repeats: String? = null)` — Un promemoria in programma, per la pagina «Cosa succederà».
- `data class FluidReminderDelivery(val id: String, val title: String, val scheduledMillis: Long, val deliveredMillis: Long, val reason: String? = null)` — Un promemoria arrivato: quando doveva e quando è arrivato davvero (il registro dei ritardi).
  - `val delayMillis: Long get()` — Il ritardo in millisecondi, mai meno di zero.
- `class FluidNotifyFix private constructor(val name: String, val channelId: String? = null)` — Dove porta una correzione della pagina «Le notifiche non arrivano?»: il punto giusto delle impostazioni. Non è un `enum` (regola 3.11).
  - `companion object`
    - `val AppNotifications: FluidNotifyFix` — Le notifiche dell'app (il permesso, o l'interruttore generale).
    - `fun channel(id: String): FluidNotifyFix` — Il canale [id].
    - `val ExactAlarms: FluidNotifyFix` — Sveglie e promemoria (l'allarme esatto, da Android 12).
    - `val Battery: FluidNotifyFix` — La batteria dell'app: «Senza restrizioni», così One UI non la sospende.
    - `val LiveUpdates: FluidNotifyFix` — I Live Update dell'app (Android 16).
    - `val Focus: FluidNotifyFix` — Le modalità di concentrazione (Non disturbare).
- `data class FluidNotifyCheck(val id: String, val title: String, val detail: String, val ok: Boolean, val fix: FluidNotifyFix? = null, val fixLabel: String? = null)` — Un controllo della pagina «Le notifiche non arrivano?»: una riga con l'esito e, se qualcosa non va, la correzione.
- `data class FluidNotifyHealth(val checks: List<FluidNotifyCheck> = emptyList(), val suspended: Boolean = false)` — L'esito dei controlli: [checks] in ordine d'importanza, [suspended] vero se il sistema ha messo l'app a dormire (One UI «App in sospensione», o il secchio «limitato» di Android).
  - `val allGood: Boolean get()` — Vero se tutti i controlli vanno bene.
- `interface FluidNotifyCenter` — Il centro delle notifiche dell'app, come lo leggono e lo comandano le pagine di engine-ui. Lo dà `engine-notify` (`FluidNotify.center`); per le prove e le anteprime basta un'implementazione con dati fissi.
  - `val settings: StateFlow<FluidNotifySettings>` — Le impostazioni.
  - `val reminders: StateFlow<List<FluidReminderInfo>>` — I promemoria in programma, dal più vicino.
  - `val deliveries: StateFlow<List<FluidReminderDelivery>>` — I promemoria arrivati, dal più recente (gli ultimi 50).
  - `val health: StateFlow<FluidNotifyHealth>` — I controlli di «Le notifiche non arrivano?».
  - `fun refresh()` — Rilegge tutto da Android: le pagine lo chiamano alla ripresa (al ritorno dalle impostazioni).
  - `fun setChannelInDigest(channelId: String, inDigest: Boolean)` — Il canale [channelId] nel riepilogo ([inDigest]) o subito.
  - `fun setDigest(enabled: Boolean, times: List<Int>)` — Accende o spegne il riepilogo, con i suoi orari (minuti dalla mezzanotte).
  - `fun cancelReminder(id: String)` — Toglie il promemoria [id] dal programma.
  - `fun open(fix: FluidNotifyFix): Boolean` — Apre il punto giusto delle impostazioni di Android per [fix]. Vero se è riuscito ad aprirlo.

## `engine-foundation/foundation/text/FluidTextDiff.kt`

- `object FluidTextDiff` — Il confronto di due liste di pezzi di testo con l'algoritmo di Myers (E. W. Myers, «An O(ND) Difference Algorithm and Its Variations», Algorithmica 1986): trova la sottosequenza comune piu' lunga facendo al massimo `maxEdits` modifiche.
  - `const val DefaultMaxEdits: Int` — Il tetto di serie: mille pezzi tolti o aggiunti. Nel caso peggiore sono circa 4 MB di lavoro temporaneo e qualche decina di millisecondi su un telefono di fascia bassa; per questo chi lo usa lo chiama fuori dal thread principale. A occhio, 24/11/2026: due versioni di una nota con piu' di mille parole diverse non si fondono comunque in modo utile.
  - `fun words(text: String): List<String>` — I pezzi per parola: ogni parola (lettere e cifre), ogni corsa di spazi, ogni a capo e ogni segno di punteggiatura e' un pezzo. Messi in fila ridanno il testo esatto.
  - `fun lines(text: String): List<String>` — I pezzi per riga: ogni riga con il suo a capo (l'ultima anche senza). Messi in fila ridanno il testo esatto.
  - `fun diff(a: List<String>, b: List<String>, maxEdits: Int = DefaultMaxEdits): FluidDiff?` — Allinea [a] e [b]: per ogni pezzo dice a quale pezzo dell'altra lista corrisponde, se corrisponde. Risponde `null` se servono piu' di [maxEdits] pezzi tolti o aggiunti.
- `class FluidDiff internal constructor(private val aToB: IntArray, private val bToA: IntArray)` — Il risultato di [FluidTextDiff.diff]: per ogni pezzo di `a` il pezzo di `b` che gli corrisponde (o -1), e viceversa. Le corrispondenze vanno sempre avanti: se il pezzo `i` corrisponde a `j`, ogni pezzo dopo `i` corrisponde a un pezzo dopo `j`.
  - `val sizeA: Int get()` — Quanti pezzi ha `a`.
  - `val sizeB: Int get()` — Quanti pezzi ha `b`.
  - `fun matchInB(indexA: Int): Int` — Il pezzo di `b` che corrisponde al pezzo [indexA] di `a`, o -1 se in `b` non c'e'.
  - `fun matchInA(indexB: Int): Int` — Il pezzo di `a` che corrisponde al pezzo [indexB] di `b`, o -1 se in `a` non c'e'.
  - `val edits: Int by lazy` — Quanti pezzi sono stati tolti da `a` o aggiunti in `b`.
  - `val hunks: List<FluidDiffHunk> by lazy` — I tratti diversi, in ordine: fra due tratti tutto e' uguale.
- `data class FluidDiffHunk(val aStart: Int, val aEnd: Int, val bStart: Int, val bEnd: Int)` — Un tratto diverso: i pezzi [aStart]..[aEnd) di `a` (tolti) sono diventati [bStart]..[bEnd) di `b` (aggiunti). Uno dei due intervalli puo' essere vuoto: un'aggiunta pura o una cancellazione pura.
