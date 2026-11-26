<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/health.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-health

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-health/health/FluidHealthGateway.kt`

- `interface FluidHealthGateway` — La porta verso i dati di salute. [FluidHealthConnectGateway] e' quella vera; [FluidMemoryHealthGateway] tiene i dati in memoria, per le prove (di un'app e dell'engine) e per la galleria. Tutti i metodi sospendono e si chiamano fuori dal thread principale o da una coroutine: niente lavora sul principale.
  - `suspend fun availability(): FluidHealthAvailability` — Se Health Connect c'e'.
  - `suspend fun granted(): Set<FluidHealthPermission>` — I permessi che l'utente ha dato, misura per misura.
  - `suspend fun read(metric: FluidHealthMetric, from: Instant, to: Instant): List<FluidHealthSample>` — I dati di [metric] fra [from] e [to], di tutte le fonti. Lancia [SecurityException] senza il permesso.
  - `suspend fun changesToken(metrics: Set<FluidHealthMetric>): String` — Il segno da cui chiedere le modifiche di [metrics] da adesso in poi.
  - `suspend fun changes(token: String): FluidHealthChanges` — Le modifiche dopo [token] (al piu' una pagina: [FluidHealthChanges.hasMore] dice se ce ne sono altre).
  - `suspend fun write(samples: List<FluidHealthSample>): List<String>` — Scrive [samples] (con la fonte di questa app) e da' i loro id.
  - `suspend fun aggregate(metric: FluidHealthMetric, from: Instant, to: Instant): FluidHealthAggregate` — Il totale (o le statistiche) di [metric] fra [from] e [to], **calcolato da Health Connect** con le sue aggregazioni: per l'attivita' e il sonno senza i doppioni, con l'ordine delle app scelto dall'utente; per gli altri tipi con tutte le app. Con le app che l'hanno dato (`dataOrigins`). Lancia [SecurityException] senza il permesso di lettura.
  - `suspend fun aggregateByDuration(metric: FluidHealthMetric, from: Instant, to: Instant, bucket: Duration): List<FluidHealthAggregate>` — Gli stessi totali a tratti di [bucket] fra [from] e [to]: **solo i tratti con dati** (come Health Connect, la lista e' rada).
  - `suspend fun aggregateByDays(metric: FluidHealthMetric, first: LocalDate, days: Int, zone: ZoneId = ZoneId.systemDefault()): List<FluidHealthAggregate>` — I totali di [days] giorni di calendario da [first] nel fuso [zone]: uno per giorno, vuoto ([FluidHealthAggregate.hasData] falso) senza dati.
  - `suspend fun latest(metric: FluidHealthMetric, from: Instant, to: Instant): FluidHealthSample?` — Il dato piu' recente di [metric] fra [from] e [to] (l'ultima pesata, l'ultimo battito), o null.
- `class FluidHealthConnectGateway(context: Context, private val client: HealthConnectClient? = null) : FluidHealthGateway` — Health Connect, attraverso `androidx.health.connect:connect-client`.
- `object FluidHealthPermissions` — I permessi di Health Connect per le misure del modulo, e come chiederli.
  - `fun recordType(metric: FluidHealthMetric): KClass<out Record>?` — Il tipo di record di Health Connect di [metric]; null per una misura che il modulo non conosce.
  - `fun of(permission: FluidHealthPermission): String?` — La stringa del permesso di Health Connect («android.permission.health.READ_STEPS»).
  - `fun fromStrings(granted: Set<String>): Set<FluidHealthPermission>` — I permessi del modulo da quelli di Health Connect (gli altri si ignorano).
  - `fun strings(permissions: Collection<FluidHealthPermission>): Set<String>` — Le stringhe da chiedere per [permissions].
  - `fun requestContract(): ActivityResultContract<Set<String>, Set<String>>` — Il contratto per chiedere i permessi (il foglio di Health Connect): entra un insieme di stringhe, esce quello dato.
  - `fun manageIntent(context: Context): Intent` — La pagina dei permessi di questa app in Health Connect, per dopo due rifiuti (Android non mostra piu' il dialogo).
  - `fun declared(context: Context): Set<FluidHealthPermission>` — I permessi di salute che l'app ha scritto nel manifest: solo quelli si possono chiedere.
- `class FluidMemoryHealthGateway(var availabilityValue: FluidHealthAvailability = FluidHealthAvailability.Available, private val self: FluidHealthSource = FluidHealthSource("dev.antigravity.fluidglass",…, var sourcePriority: List<String> = emptyList()) : FluidHealthGateway` — I dati in memoria, come li darebbe Health Connect: per le prove (di un'app e dell'engine) e per la galleria. Si danno i permessi con [grant] e [revoke], si aggiungono dati di qualunque fonte con [put], si tolgono con [remove]; le modifiche si leggono per segno come quelle vere, e un segno si fa scadere con [expireTokens].
  - `fun grant(vararg permissions: FluidHealthPermission)`
  - `fun revoke(vararg permissions: FluidHealthPermission)`
  - `fun grantAll()`
  - `fun put(vararg samples: FluidHealthSample)` — Aggiunge o sostituisce dati (di qualunque fonte).
  - `fun remove(vararg ids: String)` — Toglie i dati con questi id.
  - `fun expireTokens()` — Fa scadere tutti i segni dati finora (come Health Connect dopo 30 giorni).
  - `fun all(): List<FluidHealthSample>` — Tutti i dati di adesso (per le prove).
- `suspend fun FluidHealthGateway.writeSleep(start: Instant, end: Instant, title: String? = null, stages: List<FluidSleepStage> = emptyList()): String` — Scrive una notte di sonno di questa app (Sounds col timer della notte): da [start] a [end], col titolo. Da' l'id del dato. Serve il permesso di scrittura del sonno.

## `engine-health/health/FluidHealthModel.kt`

- `value class FluidHealthMetric(val id: String)` — Una misura di Health Connect. Una classe e non un `enum`, perche' le misure crescono (Health Connect ne ha piu' di quaranta) senza rompere chi fa `when`.
  - `companion object`
    - `val Steps` — I passi (`StepsRecord`): intervalli col conto, sommati.
    - `val Sleep` — Il sonno (`SleepSessionRecord`): sessioni con le fasi.
    - `val HeartRate` — Il battito (`HeartRateRecord`): campioni in battiti al minuto.
    - `val Exercise` — Gli allenamenti (`ExerciseSessionRecord`): sessioni col tipo.
    - `val Weight` — Il peso (`WeightRecord`): misure in chilogrammi.
    - `val Hydration` — L'acqua (`HydrationRecord`): intervalli in millilitri, sommati.
    - `val All: List<FluidHealthMetric>` — Le sei misure del modulo, nell'ordine delle righe dei permessi.
- `data class FluidHealthPermission(val metric: FluidHealthMetric, val write: Boolean = false)` — Un permesso: leggere o scrivere una misura. Health Connect li chiede uno per uno, e l'utente li da' uno per uno.
  - `companion object`
    - `fun read(metric: FluidHealthMetric)`
    - `fun write(metric: FluidHealthMetric)`
- `enum class FluidHealthDeviceKind` — Che cosa ha scritto un dato: l'orologio, il telefono, un anello, una bilancia (Health Connect lo dice dal 2023).
- `data class FluidHealthSource(val packageName: String, val appLabel: String? = null, val device: FluidHealthDeviceKind = FluidHealthDeviceKind.Unknown, val deviceName: String? = null)` — La fonte di un dato: l'app che l'ha scritto ([packageName], col suo nome se il telefono lo sa) e l'apparecchio ([device]). E' cio' che le card mostrano sotto ogni valore: «Da Sounds», «Da Fitbit · orologio».
  - `val label: String get()` — Il nome da leggere: quello dell'app, o il pacchetto se il telefono non lo sa.
- `sealed interface FluidHealthSample` — Un dato di Health Connect, come lo vede il modulo: un intervallo con una quantita' (passi, acqua), un punto con un valore (battito, peso) o una sessione (sonno, allenamento). [id] e' quello di Health Connect; [source] dice chi l'ha scritto.
  - `val id: String`
  - `val metric: FluidHealthMetric`
  - `val source: FluidHealthSource`
  - `val start: Instant`
  - `val end: Instant`
  - `val lastModified: Instant?`
- `data class FluidHealthInterval(override val id: String, override val metric: FluidHealthMetric, override val start: Instant, override val end: Instant, val value: Double, override val source: FluidHealthSource, override val lastModified: Instant? = null) : FluidHealthSample` — Una quantita' in un intervallo: [value] passi, o millilitri d'acqua.
- `data class FluidHealthPoint(override val id: String, override val metric: FluidHealthMetric, val time: Instant, val value: Double, override val source: FluidHealthSource, override val lastModified: Instant? = null) : FluidHealthSample` — Un valore in un istante: [value] battiti al minuto, o chilogrammi.
- `data class FluidSleepStage(val start: Instant, val end: Instant, val type: Int)` — Una fase del sonno: il tipo e' quello di Health Connect (`SleepSessionRecord.STAGE_TYPE_*`).
  - `val asleep: Boolean get()` — Vero per le fasi in cui si dorme (leggero, profondo, REM, «dorme» senza dettaglio).
- `data class FluidHealthSession(override val id: String, override val metric: FluidHealthMetric, override val start: Instant, override val end: Instant, override val source: FluidHealthSource, val title: String? = null, val exerciseType: Int? = null, val stages: List<FluidSleepStage> = emptyList(), override val lastModified: Instant? = null) : FluidHealthSample` — Una sessione: una notte ([FluidHealthMetric.Sleep], con le [stages] se la fonte le da') o un allenamento ([FluidHealthMetric.Exercise], col tipo di Health Connect in [exerciseType]).
  - `val duration: Duration get()`
- `data class FluidHealthAggregate(val metric: FluidHealthMetric, val start: Instant, val end: Instant, val total: Double? = null, val min: Double? = null, val max: Double? = null, val average: Double? = null, val count: Long? = null, val sources: List<FluidHealthSource> = emptyList())` — Un totale (o una statistica) di una misura in un tratto, **come lo calcola Health Connect** con le sue aggregazioni (`StepsRecord.COUNT_TOTAL`, `SleepSessionRecord.SLEEP_DURATION_TOTAL`, `HeartRateRecord.BPM_AVG`…). L'engine non rifa' la scelta delle fonti: per l'attivita' e il sonno Health Connect toglie i doppioni con l'ordine delle app che l'utente sceglie nelle sue impostazioni;
  - `val hasData: Boolean get()` — Vero se Health Connect ha dei dati nel tratto.
  - `companion object`
    - `fun empty(metric: FluidHealthMetric, start: Instant, end: Instant): FluidHealthAggregate` — Il tratto senza dati.
- `enum class FluidHealthAvailability` — Se Health Connect c'e' sul telefono.
- `data class FluidHealthChanges(val upserted: List<FluidHealthSample>, val deletedIds: List<String>, val nextToken: String, val hasMore: Boolean, val tokenExpired: Boolean = false)` — Le modifiche dall'ultima sincronizzazione: i dati nuovi o cambiati, quelli tolti, e il segno per la prossima volta.

## `engine-health/health/FluidHealthPermissionsUi.kt`

- `enum class FluidHealthAccess` — Lo stato di una misura per le righe dei permessi: cosa l'app chiede ([asked]) e cosa l'utente ha dato ([granted]).
- `fun fluidHealthAccess(metric: FluidHealthMetric, asked: Collection<FluidHealthPermission>, granted: Set<FluidHealthPermission>): FluidHealthAccess` — Lo stato di [metric] dati i permessi [asked] (dell'app) e [granted] (dell'utente).
- `class FluidHealthPermissionRequester internal constructor(private val launch: (Set<String>) -> Unit, private val openHealthConnect: () -> Unit)` — Chi chiede i permessi: il foglio di Health Connect, misura per misura. Dopo **due rifiuti** della stessa misura Android non mostra piu' il foglio (risponde subito con niente): allora il tasto della riga apre la pagina dell'app in Health Connect. I rifiuti si contano qui, per la vita della pagina.
  - `fun needsSettings(metric: FluidHealthMetric): Boolean` — Vero se per [metric] il foglio non si mostra piu' (due rifiuti): serve Health Connect.
  - `fun request(permissions: Set<FluidHealthPermission>)` — Chiede [permissions] (di una misura sola, di solito) col foglio, o apre Health Connect dopo due rifiuti.
- `@Composable fun rememberFluidHealthPermissionRequester(onResult: (Set<FluidHealthPermission>) -> Unit): FluidHealthPermissionRequester` — Il richiedente dei permessi della pagina: [onResult] riceve i permessi del modulo dati dall'utente (anche nessuno).
- `fun openHealthConnect(context: Context): Boolean` — Apre la pagina dei permessi di questa app in Health Connect. Falso se non si puo' (Health Connect assente).
- `@Composable fun FluidHealthPermissionRows(asked: List<FluidHealthPermission>, granted: Set<FluidHealthPermission>, onRequest: (Set<FluidHealthPermission>) -> Unit, modifier: Modifier = Modifier, availability: FluidHealthAvailability = FluidHealthAvailability.Available, onOpenHealthConnect: (() -> Unit)? = null, needsSettings: (FluidHealthMetric) -> Boolean = { false }, labels: FluidHealthLabels = FluidHealthLabels(), glass: Boolean = false)` — Le righe dei permessi, una per misura, come la pagina dei permessi di Salute: il simbolo nel colore della misura, il nome, cosa l'app chiede («Lettura e scrittura»), e lo stato («Consentito», «Solo lettura», «Non consentito»). Toccata, una riga chiede i permessi che mancano **di quella misura sola** ([onRequest]). Se Health Connect non c'e' o va aggiornato, sopra c'e' l'avviso con il tasto.
- `class FluidHealthPrivacyActivity : ComponentActivity()` — La pagina della privacy che Health Connect apre dal suo elenco dei permessi (il manifest del modulo la dichiara): dice in parole semplici che cosa l'app chiede, misura per misura (dai permessi scritti nel suo manifest), che i dati restano sul telefono cifrati e fuori da ogni statistica, e porta all'informativa dell'app (il meta-data `dev.antigravity.fluidengine.health.PRIVACY_URL` nel manifest dell'app).
  - `companion object`
    - `const val PrivacyUrlKey: String` — Il meta-data dell'app con l'indirizzo della sua informativa sulla privacy.
- `@Composable fun FluidHealthPrivacyPage(appName: String, asked: List<FluidHealthPermission>, privacyUrl: String?, onBack: () -> Unit, labels: FluidHealthLabels = FluidHealthLabels())` — La pagina della privacy dei dati di salute (anche da mettere nelle impostazioni dell'app).
- `val FluidHealthLicenses: List<FluidLicense>` — Le licenze di `engine-health`, da aggiungere alla pagina «Licenze e crediti».

## `engine-health/health/FluidHealthReadings.kt`

- `data class FluidHealthGap(val start: Instant, val end: Instant)` — Un tratto senza nessun dato, da nessuna app: Health Connect non ha un tratto con dati li'.
  - `val duration: Duration get()`
- `data class FluidHealthGapRule(val minimum: Duration = Duration.ofHours(3), val from: LocalTime = LocalTime.of(7, 0), val to: LocalTime = LocalTime.of(23, 0))` — Quando un tratto senza dati e' un **buco** da dire: lungo almeno [minimum] e dentro la giornata sveglia, fra [from] e [to] (la notte senza passi non e' un buco). Di serie tre ore fra le 7 e le 23 (scelta di Alessio, F8.26): piu' corto, un pomeriggio alla scrivania (il telefono non scrive niente se non si cammina) diventa un avviso falso.
- `sealed interface FluidHealthReading` — La lettura di un tratto di una misura, come la mostra una card: mai uno zero inventato.
  - `data object NoData : FluidHealthReading` — Health Connect non ha niente nel tratto: «Nessun dato», non «0».
  - `data class Value(val value: Double, val sources: List<FluidHealthSource>, val gaps: List<FluidHealthGap> = emptyList()) : FluidHealthReading` — Il totale di Health Connect, dalle app [sources] (quelle che dice l'aggregazione); [gaps] sono i buchi lunghi (allora la card dice «niente dalle… alle…»).
    - `val partial: Boolean get()`
- `data class FluidHeartSummary(val min: Double, val max: Double, val average: Double, val count: Long?, val latest: FluidHealthPoint?, val sources: List<FluidHealthSource>)` — Il battito di un tratto, dalle statistiche di Health Connect (`BPM_MIN`, `BPM_MAX`, `BPM_AVG`, `MEASUREMENTS_COUNT`).
- `object FluidHealthReadings` — Le letture delle card dai totali di **Health Connect**.
  - `fun dayRange(date: LocalDate, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): Pair<Instant, Instant>` — Il tratto del giorno [date] nel fuso [zone], da mezzanotte a mezzanotte; quello di oggi si ferma a [now].
  - `fun nightRange(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant>` — La notte che finisce il giorno [date]: dalle 18 del giorno prima alle 18 di [date] (a occhio, 26/11/2026: la sessione del timer della notte di Sounds e quella dell'orologio ci stanno dentro intere).
  - `fun reading(aggregate: FluidHealthAggregate, buckets: List<FluidHealthAggregate> = emptyList(), zone: ZoneId = ZoneId.systemDefault(), gapRule: FluidHealthGapRule? = FluidHealthGapRule()): FluidHealthReading` — La lettura di [aggregate]: «Nessun dato» se Health Connect non ha niente, altrimenti il suo totale con le app che l'hanno dato. I [buckets] (i tratti con dati di [FluidHealthGateway.aggregateByDuration]) servono solo a dire i buchi.
  - `fun gaps(buckets: List<FluidHealthAggregate>, from: Instant, to: Instant, zone: ZoneId, rule: FluidHealthGapRule): List<FluidHealthGap>` — I buchi lunghi fra i tratti con dati [buckets] (la lista rada di Health Connect), e dall'ultimo dato fino a [to]; solo nella giornata sveglia, e mai prima del primo dato (la mattina prima di uscire non e' un buco).
  - `fun heart(aggregate: FluidHealthAggregate, latest: FluidHealthPoint? = null): FluidHeartSummary?` — Il battito dalle statistiche di Health Connect; null senza dati (niente «0 bpm»).
  - `fun latest(points: List<FluidHealthPoint>): FluidHealthPoint?` — L'ultima misura di un elenco (il peso letto da me): la più recente, mai una media inventata. Null senza misure.
- `val FluidHealthGapBucket: Duration` — Il tratto dei buchi di serie: un quarto d'ora. I buchi si dicono al quarto d'ora («Niente dalle 14:15»): un giorno sono 96 tratti, una richiesta sola a Health Connect. A occhio, 26/11/2026.
- `suspend fun FluidHealthGateway.day(metric: FluidHealthMetric, date: LocalDate, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now(), gapRule: FluidHealthGapRule? = FluidHealthGapRule()): FluidHealthReading` — La lettura del giorno [date] di una misura che si somma (passi, acqua, minuti di allenamento): il totale di Health Connect, le app che l'hanno dato e i buchi lunghi. Oggi si ferma a [now].
- `suspend fun FluidHealthGateway.week(metric: FluidHealthMetric, first: LocalDate, days: Int = 7, zone: ZoneId = ZoneId.systemDefault()): List<FluidHealthReading>` — Le letture di [days] giorni da [first] (la settimana di una card), coi totali giorno per giorno di Health Connect.
- `suspend fun FluidHealthGateway.night(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): FluidHealthReading` — La notte che finisce il giorno [date]: i minuti di sonno di Health Connect (`SLEEP_DURATION_TOTAL`), che tiene una sola notte quando due app (Sounds e l'orologio) la scrivono, con l'ordine dell'utente.

## `engine-health/health/FluidHealthSync.kt`

- `class FluidHealthSnapshot(val samples: List<FluidHealthSample>, val token: String?, val metrics: Set<FluidHealthMetric>, val syncedAt: Instant?)` — La copia dei dati com'e' in un momento: i dati, il segno delle modifiche di Health Connect, le misure che copre e quando si e' sincronizzata.
  - `fun of(metric: FluidHealthMetric): List<FluidHealthSample>` — I dati di [metric].
  - `companion object`
    - `val Empty`
- `interface FluidHealthStore` — Dove sta la copia. [FluidVaultHealthStore] la cifra nella cassaforte; [FluidMemoryHealthStore] la tiene in memoria.
  - `suspend fun load(): FluidHealthSnapshot`
  - `suspend fun save(snapshot: FluidHealthSnapshot)`
  - `suspend fun clear()`
- `class FluidVaultHealthStore(private val section: VaultSection, private val fileName: String = "salute.bin") : FluidHealthStore` — La copia cifrata in un file della cassaforte ([VaultSection.file]): AES-GCM a pezzi con una chiave del Keystore, fuori dal backup. Una sezione che si apre col solo telefono basta (`VaultAccess.Device()`); un'app che vuole l'impronta usa una sezione all'impronta, e la copia si legge solo a sezione aperta.
- `class FluidMemoryHealthStore : FluidHealthStore` — La copia in memoria: per le prove e per la galleria.
- `sealed interface FluidHealthSyncResult` — Com'e' andata una sincronizzazione.
  - `data class Unavailable(val availability: FluidHealthAvailability) : FluidHealthSyncResult` — Health Connect non c'e' (o va aggiornato): la copia resta com'era.
  - `data object NoPermission : FluidHealthSyncResult` — Nessuna delle misure chieste ha il permesso di lettura: niente da leggere (la copia di quelle misure si toglie).
  - `data class Full(val count: Int, val denied: Set<FluidHealthMetric>) : FluidHealthSyncResult` — Letto tutto da capo (la prima volta, un segno scaduto, misure cambiate): [count] dati.
  - `data class Changes(val upserted: Int, val deleted: Int, val denied: Set<FluidHealthMetric>) : FluidHealthSyncResult` — Lette solo le modifiche: [upserted] dati nuovi o cambiati, [deleted] tolti.
- `class FluidHealthSync(private val gateway: FluidHealthGateway, private val store: FluidHealthStore, val metrics: Set<FluidHealthMetric>, val window: Duration = Duration.ofDays(30), private val clock: () -> Instant = Instant::now)` — La sincronizzazione per differenze: la prima volta legge [window] all'indietro e prende il segno delle modifiche di Health Connect; dopo chiede solo le modifiche dal segno (i dati nuovi, cambiati e tolti), pagina per pagina. Un segno scaduto (Health Connect lo tiene 30 giorni), un permesso nuovo o tolto, o misure diverse rifanno la lettura intera.
  - `suspend fun snapshot(): FluidHealthSnapshot` — La copia di adesso.
  - `suspend fun sync(): FluidHealthSyncResult` — Sincronizza: le modifiche se si puo', tutto da capo se serve.
  - `suspend fun reset()` — Dimentica la copia e il segno: la prossima [sync] rilegge tutto.
- `object FluidHealthCodec` — Il formato della copia: binario, con una versione in testa. Le stringhe in UTF modificato di Java, gli istanti in secondi e nanosecondi. Pura: si prova sulla JVM.
  - `fun encode(snapshot: FluidHealthSnapshot): ByteArray`
  - `fun decode(bytes: ByteArray): FluidHealthSnapshot`

## `engine-health/health/FluidHealthUi.kt`

- `object FluidHealthTags` — I tag delle prove.
  - `const val Rings: String`
  - `const val Week: String`
  - `fun card(metric: FluidHealthMetric): String`
  - `fun permission(metric: FluidHealthMetric): String`
- `data class FluidHealthLabels(val metricName: (FluidHealthMetric) -> String = { m -> when (m) { FluidHealthMetric.Steps -> "P…, val noData: String = "Nessun dato", val noDataToday: String = "Nessun dato oggi", val from: (sources: String) -> String = { "Da $it" }, val fromMany: (count: Int) -> String = { "Da $it fonti" }, val gap: (from: String, to: String) -> String = { a, b -> "Niente dalle $a alle $b" }, val gapUntilNow: (from: String) -> String = { "Niente dalle $it" }, val ofGoal: (value: String, goal: String) -> String = { v, g -> "$v su $g" }, val read: String = "Lettura", val write: String = "Scrittura", val allowed: String = "Consentito", val notAllowed: String = "Non consentito", val readOnly: String = "Solo lettura", val writeOnly: String = "Solo scrittura", val unavailable: String = "Health Connect non c'e' su questo telefono", val needsUpdate: String = "Health Connect va aggiornato", val openHealthConnect: String = "Apri Health Connect", val askAgain: String = "Consenti in Health Connect", val watch: String = "orologio", val phone: String = "telefono", val ring: String = "anello", val scale: String = "bilancia", val beatsPerMinute: String = "bpm", val kilograms: String = "kg", val liters: String = "l", val steps: String = "passi", val minutes: String = "min")` — Le parole della salute, per le card, TalkBack e le righe dei permessi. Italiano di serie; [English] per le app in inglese. Le frasi dicono **fatti**, mai cause: «Nessun dato dalle 14:10», non «orologio scarico».
  - `companion object`
    - `val English: FluidHealthLabels`
  - `fun sourceLine(sources: List<FluidHealthSource>, locale: Locale = Locale.getDefault()): String?` — «Da Sounds», «Da Fitbit · orologio», «Da Sounds e Fitbit», «Da 3 fonti». La parola dell'apparecchio non si ripete quando il nome dell'app la dice gia': «Da Orologio», non «Da Orologio · orologio» (F8.26c).
  - `fun deviceWord(kind: FluidHealthDeviceKind): String?` — La parola dell'apparecchio, o null se non si sa.
  - `fun gapLine(gaps: List<FluidHealthGap>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String?` — Il buco piu' lungo, a parole: «Niente dalle 14:10 alle 18:30» (o «dalle 14:10» se arriva fino ad adesso).
- `object FluidHealthColors` — I colori delle misure: dalla palette d'identita' dell'engine (contrasto gia' provato), uno per misura, chiaro e scuro. Non quelli di Salute: i passi arancio, il sonno indaco, il battito rosa, gli allenamenti verdi, il peso viola, l'acqua blu.
  - `fun of(metric: FluidHealthMetric, dark: Boolean): Color`
  - `fun icon(metric: FluidHealthMetric): ImageVector` — Il simbolo pieno di una misura (quello delle card).
- `data class FluidHealthRing(val label: String, val value: Double, val goal: Double, val valueText: String, val goalText: String, val color: Color = Color.Unspecified)` — Un anello del giorno: quanto si e' fatto ([value]) rispetto all'obiettivo ([goal]), col nome e il testo del valore. [color] `Unspecified` prende i colori di serie degli anelli (l'accento e due tinte dell'engine).
  - `val fraction: Double get()` — Quanto dell'obiettivo, da 0 (anche oltre 1).
- `object FluidHealthRingDefaults` — Le misure degli anelli. Il disegno e' dell'engine e **non** quello di Fitness: archi **aperti** in basso (300°, come il quadrante di `FluidGauge`), a tinta piena senza sfumature e senza frecce, con la traccia chiara sotto; oltre l'obiettivo l'arco resta pieno e la percentuale lo dice (niente secondo giro sovrapposto). A occhio, 26/11/2026.
  - `val Diameter: Dp`
  - `val Stroke: Dp`
  - `val Gap: Dp`
  - `const val SweepDegrees: Float`
  - `const val TrackAlpha: Float`
- `@Composable fun FluidHealthRings(rings: List<FluidHealthRing>, modifier: Modifier = Modifier, diameter: Dp = FluidHealthRingDefaults.Diameter, stroke: Dp = FluidHealthRingDefaults.Stroke, labels: FluidHealthLabels = FluidHealthLabels(), center: (@Composable () -> Unit)? = null)` — Gli anelli del giorno: uno, due o tre archi concentrici aperti in basso. Quando i valori arrivano (o cambiano) gli archi crescono con una molla dall'ultimo valore; col movimento ridotto sono gia' li'. Un anello fermo non ridisegna. TalkBack legge ogni anello: «Passi, 7 412 su 10 000, 74%».
- `@Composable fun FluidHealthDayCard(rings: List<FluidHealthRing>, modifier: Modifier = Modifier, title: String? = null, sources: List<FluidHealthSource> = emptyList(), labels: FluidHealthLabels = FluidHealthLabels(), glass: Boolean = false)` — La card del giorno: gli anelli a sinistra e, a destra, una riga per anello col pallino del colore, il nome e «7 412 su 10 000» (il numero scorre, sfocato, quando arriva: e' l'app che lo cambia). Sotto, la fonte.
- `@Composable fun FluidHealthCard(metric: FluidHealthMetric, value: String?, modifier: Modifier = Modifier, numeric: Double = 0.0, unit: String? = null, time: String? = null, sources: List<FluidHealthSource> = emptyList(), note: String? = null, labels: FluidHealthLabels = FluidHealthLabels(), glass: Boolean = false, onClick: (() -> Unit)? = null, chart: (@Composable () -> Unit)? = null)` — La card di una misura, come le card di Salute: il simbolo e il nome nel colore della misura, l'ora a destra, il valore grande con l'unita', e sotto **la fonte** («Da Sounds · telefono») e, se ci sono, i buchi («Niente dalle 14:10 alle 18:30»). Senza valore dice «Nessun dato», mai «0». Il valore che arriva scorre sfocato ([FluidNumericText]).
- `data class FluidHealthWeekDay(val label: String, val value: Double?, val valueText: String? = null)` — Un giorno della settimana: l'etichetta («L») e il valore, o null se quel giorno non ha dati (niente barra, un trattino).
- `@Composable fun FluidHealthWeek(days: List<FluidHealthWeekDay>, metric: FluidHealthMetric, modifier: Modifier = Modifier, goal: Double? = null, height: Dp = 72.dp, labels: FluidHealthLabels = FluidHealthLabels())` — La settimana di una misura: sette barre dal basso, piene fino all'obiettivo ([goal], la linea tratteggiata), e per un giorno **senza dati** nessuna barra ma un trattino: un giorno vuoto non e' un giorno a zero. TalkBack legge giorno per giorno.
- `object FluidHealthFormat` — Le parole dei valori: i passi interi («7 412»), il sonno in ore e minuti («7 h 12 min»), il battito («58»), il peso con un decimale («72,4»), l'acqua in litri con un decimale («1,2»). Nella lingua del telefono.
  - `fun steps(value: Double, locale: Locale = Locale.getDefault()): String`
  - `fun sleep(duration: Duration, locale: Locale = Locale.getDefault()): String`
  - `fun heart(value: Double, locale: Locale = Locale.getDefault()): String`
  - `fun weight(kilograms: Double, locale: Locale = Locale.getDefault()): String`
  - `fun water(milliliters: Double, locale: Locale = Locale.getDefault()): String`
  - `fun week(readings: List<FluidHealthReading>, first: LocalDate, locale: Locale = Locale.getDefault(), text: (Double) -> String = { steps(it, locale) }): List<FluidHealthWeekDay>` — Le letture di una settimana per [FluidHealthWeek], con le iniziali dei giorni («L», «M», …) dalla lingua.

## `engine-health/health/icons/FluidIconsFilled.kt`

- `val FluidIcons.Filled.DirectionsRun: ImageVector` — `directions_run`, pieno. Nel set per: gli allenamenti. Si gira da sola nelle lingue da destra a sinistra.
- `val FluidIcons.Filled.Footprint: ImageVector` — `footprint`, pieno. Nel set per: i passi.
- `val FluidIcons.Filled.MonitorWeight: ImageVector` — `monitor_weight`, pieno. Nel set per: il peso.
- `val FluidIcons.Filled.WaterDrop: ImageVector` — `water_drop`, pieno. Nel set per: l'acqua.

## `engine-health/health/icons/FluidIconsOutline.kt`

- `val FluidIcons.DirectionsRun: ImageVector` — `directions_run`, al tratto. Nel set per: gli allenamenti. Si gira da sola nelle lingue da destra a sinistra.
- `val FluidIcons.Footprint: ImageVector` — `footprint`, al tratto. Nel set per: i passi.
- `val FluidIcons.MonitorWeight: ImageVector` — `monitor_weight`, al tratto. Nel set per: il peso.
- `val FluidIcons.Smartphone: ImageVector` — `smartphone`, al tratto. Nel set per: un dato dal telefono.
- `val FluidIcons.Watch: ImageVector` — `watch`, al tratto. Nel set per: un dato dall'orologio.
- `val FluidIcons.WaterDrop: ImageVector` — `water_drop`, al tratto. Nel set per: l'acqua.
