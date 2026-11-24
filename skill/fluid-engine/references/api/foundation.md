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
- `data class AppRelease(val version: String, val changelog: String = "", val releaseTag: String = "", val apkAsset: String = "", val sizeBytes: Long = 0L, val releaseDate: String = "", val sha256: String = "")`
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
