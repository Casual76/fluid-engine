# API · engine-account

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-account/account/AccountProtocol.kt`

- `object AccountProtocol` — Il contratto sul telefono fra le app e Pampa Store (`b\api.md` § 5): un `ContentProvider` dello Store, chiamato con `ContentResolver.call(method, null, extras)` e un JSON in `extras["json"]`, come il ponte dei tool. Ogni risposta porta `protocol` e `capabilities`. Lo Store tiene il protocollo `1` per sempre: le novità arrivano come capacità.
  - `const val PROTOCOL`
  - `const val META_HOST` — Il meta-data sul `<provider>` dello Store: `dev.pampa.account.HOST = 1`.
  - `const val AUTHORITY_SUFFIX` — L'autorità è `<applicationId dello Store>.account`.
  - `const val KEY_JSON`
  - `const val ACTION_CHANGED` — Il broadcast esplicito dello Store a ogni app Pampa quando cambia qualcosa (§ 5.4). Dentro solo `what`.
  - `const val EXTRA_WHAT`
  - `const val ACTION_OPEN` — L'azione con cui un'app apre lo Store sulla pagina giusta (`open`).
  - `const val EXTRA_SECTION`
  - `const val EXTRA_PLACES_WRITE` — F10.8 · Nella richiesta dei luoghi ([AccountSections.PLACES_REQUEST]): l'app vuole anche aggiungerne.
  - `const val META_ICON_SET` — F10.8 · Il meta-data di un `<activity-alias>` dell'app: la famiglia d'icone che porta (`default`, `tinted`, …).
  - `const val METHOD_NEGOTIATE`
  - `const val METHOD_STATE`
  - `const val METHOD_TOKEN`
  - `const val METHOD_GOOGLE`
  - `const val METHOD_GRANTS`
  - `const val METHOD_APPEARANCE`
  - `const val METHOD_SET_APPEARANCE`
  - `const val METHOD_APP_KEY`
  - `const val METHOD_PLACES_LIST`
  - `const val METHOD_PLACES_PUT`
  - `const val METHOD_PLACES_DELETE`
  - `const val METHOD_BACKUP_AVAILABLE`
  - `const val METHOD_OPEN`
  - `val CAPABILITIES` — Le capacità del protocollo 1 in F10.4. `people.*`, `share.*` e `devices.send` arrivano con F10.6.
  - `const val METHOD_PEOPLE_ROW` — F10.6 · Le persone (`bpi.md` § 5.3): la fila delle persone, condividere, ricevere, rispondere e «I tuoi dispositivi». Le annuncia solo uno Store che ha `engine-people`; uno Store più vecchio non le ha, e l'app non mostra la fila (P1) invece di fallire.
  - `const val METHOD_SHARE_SEND`
  - `const val METHOD_SHARE_RECEIVED`
  - `const val METHOD_SHARE_REPLY`
  - `const val METHOD_SHARE_HIDE`
  - `const val METHOD_DEVICES_SEND`
  - `val PEOPLE_CAPABILITIES` — Le capacità che uno Store con `engine-people` aggiunge a [CAPABILITIES].
  - `object What` — Cosa può cambiare (il contenuto di `what`).
    - `const val STATE`
    - `const val APPEARANCE`
    - `const val GRANTS`
    - `const val PLACES`
    - `const val KEYS`
    - `const val WIPED`
    - `const val SHARE` — F10.6 · È arrivato qualcosa per quest'app («Condivisi con te»): l'app rilegge `share.received`.
    - `const val PEOPLE` — F10.6 · È cambiata la fila delle persone (un contatto nuovo, un nome, un blocco).
  - `fun parse(text: String?): JsonObject?` — Il JSON di una richiesta o di una risposta, o `null` se non è un oggetto JSON.
- `sealed interface AccountState` — Quello che vede un'app dell'account (`b\architettura.md` § 2.4 e § 9.4).
  - `data object NoStore : AccountState` — Lo Store non c'è, oppure c'è un pacchetto con quel nome ma firmato da altri (M1).
  - `data class StoreTooOld(val needed: String) : AccountState` — Lo Store non ha la capacità che serve: «Aggiorna Pampa Store per usare l'account».
  - `data object SignedOut : AccountState` — Lo Store c'è, nessuno è entrato: «Accedi con Pampa Store».
  - `data class SignedIn(val profile: AccountProfile) : AccountState`
- `data class AccountProfile(val id: String, val displayName: String, val initials: String, val recovery: Boolean, val recoverySession: Boolean = false)` — Chi sei, come lo dice lo Store. Niente email, niente dati che il server non ha (R3).
- `sealed interface TokenResult` — La risposta a `token` (`b\api.md` § 5.3). Il token vale per un'app, un servizio e 15 minuti, e sta solo in memoria.
  - `data class Ok(val token: String, val expiresAtElapsed: Long) : TokenResult`
  - `data object SignedOut : TokenResult`
  - `data object NotAllowed : TokenResult`
  - `data object Disconnected : TokenResult`
  - `data object Offline : TokenResult`
  - `data class Unavailable(val reason: String) : TokenResult`
- `enum class KeyPurpose(val wire: String)` — Le chiavi di un'app: per la sincronizzazione dei suoi dati ([APP]) o per il suo backup ([BACKUP]).
- `sealed interface AppKeyResult` — La risposta a `appKey`: le chiavi di quest'app per ogni epoca, mai quelle di un'altra (K3).
  - `class Ok(val current: Int, val keys: Map<Int, ByteArray>) : AppKeyResult`
    - `val currentKey: ByteArray get()` — La chiave con cui si scrive oggi.
  - `data object NoE2e : AppKeyResult` — La cifratura non è partita: l'app lavora in locale (R1).
  - `data object Locked : AppKeyResult` — Il telefono non è stato sbloccato dall'accensione, o serve la chiave madre: si riprova dopo lo sblocco.
  - `data object NeedsDoor : AppKeyResult` — Lo Store deve aprire una porta: l'app propone di aprire lo Store.
  - `data object NotAllowed : AppKeyResult`
  - `data object SignedOut : AppKeyResult`
  - `data object NoStore : AppKeyResult`
- `data class Place(val id: String, val name: String, val kind: String, val latitude: Double, val longitude: Double, val address: String? = null)` — Un luogo comune (`b\chiavi.md` § 8): nome, tipo, coordinate e indirizzo scritto dall'utente.
  - `companion object`
    - `const val HOME`
    - `const val SCHOOL`
    - `const val WORK`
    - `const val OTHER`
- `sealed interface PlacesResult` — La risposta a `places.list`: i luoghi, oppure il permesso da chiedere allo Store.
  - `data class Ok(val places: List<Place>, val canWrite: Boolean) : PlacesResult`
  - `data object NotAllowed : PlacesResult` — L'utente non ha dato il permesso: non gliel'hanno ancora chiesto, l'ha negato, o l'app non può averli (MyCloud Gallery). Quale dei tre lo dice `PampaAccount.placesAccess`; la richiesta si apre con `PampaAccount.placesRequestIntent`.
  - `data object NoStore : PlacesResult`
- `enum class PlacesAccess(val wire: String)` — F10.8 · Il permesso dei luoghi per quest'app (scelta B8): ogni app lo chiede la prima volta, e l'utente decide app per app. Si cambia dalla pagina dell'account nello Store.
  - `companion object`
    - `fun of(wire: String?): PlacesAccess?`
- `data class BackupInfo(val n: Long, val createdAt: Long, val size: Long, val epoch: Int)` — Un backup di quest'app sul server, per proporre il ripristino al primo avvio («Ripristinare i dati di ieri?»).
- `data class SharedAppearance(val version: Long, val global: Map<String, String>, val override: Map<String, String>?, val followGlobal: Boolean)` — L'aspetto comune scelto nello Store, e l'eccezione di quest'app se c'è (`b\architettura.md` § 2.5).
  - `fun effective(key: String): String?` — Il valore che vale per quest'app: prima l'eccezione (se l'app non segue il comune), poi il comune.
  - `companion object`
    - `val EMPTY`
- `object AccountSections` — Le pagine dello Store che un'app può aprire (`open`).
  - `const val ACCOUNT`
  - `const val SIGN_IN`
  - `const val GOOGLE`
  - `const val DEVICES`
  - `const val APPEARANCE`
  - `const val PLACES`
  - `const val PLACES_REQUEST` — F10.8 · La richiesta dei luoghi di un'app («Fluid Weather vuole usare i tuoi luoghi. Consenti?»). Si apre **con `startActivityForResult`** (`ActivityResultContracts.StartActivityForResult`): solo così lo Store sa dal sistema chi chiede (`callingPackage`). Aperta in un altro modo, lo Store non chiede niente.
- `object AccountJson` — I pezzi del protocollo scritti in JSON, uguali per lo Store e per le app.
  - `fun placeJson(p: Place): JsonObject` — Un luogo come viaggia nel provider: `{ id, name, kind, lat, lon, address? }`.
  - `fun place(o: JsonObject): Place`

## `engine-account/account/PampaAccount.kt`

- `object PampaAccount` — L'Account Pampa visto da un'app (`b\architettura.md` § 9.4). Tutto passa da Pampa Store sul telefono: **mai la rete** (R9). Senza Store, o con uno Store firmato da altri, lo stato è [AccountState.NoStore] e l'app va avanti come oggi (R1); la riga «Account Pampa» di `engine-account-ui` lo dice da sé.
  - `fun configure(config: AccountClientConfig)` — La configurazione, prima del primo uso: i pacchetti dello Store (R8: chi ricompila mette i suoi).
  - `fun client(context: Context): PampaAccountClient` — Il cliente di questo processo; alla prima chiamata rimette la copia locale e rilegge dallo Store.
  - `fun state(context: Context): StateFlow<AccountState>` — Lo stato dell'account; si aggiorna da sé agli avvisi dello Store. Chiama [refresh] quando l'app torna in primo piano.
  - `suspend fun refresh(context: Context): AccountState` — Rilegge dallo Store (una chiamata locale, meno di un millisecondo di lavoro nello Store).
  - `suspend fun token(context: Context, audience: String, scopes: Set<String>): TokenResult`
  - `suspend fun google(context: Context, service: String): GoogleTokenResult`
  - `fun grants(context: Context): Flow<Set<String>>`
  - `fun appearance(context: Context): Flow<SharedAppearance>` — L'aspetto comune (funziona anche senza account, `b\architettura.md` § 2.5). Lo collega al tema F10.8.
  - `suspend fun setAppearanceException(context: Context, followGlobal: Boolean, override: Map<String, String>?)`
  - `suspend fun appKey(context: Context, purpose: KeyPurpose): AppKeyResult`
  - `fun places(context: Context): Flow<PlacesResult>` — I luoghi comuni, col permesso dell'utente (scelta B8). Si rilegge a ogni avviso `places` (e `state`, `wiped`).
  - `suspend fun placesAccess(context: Context): PlacesAccess` — F10.8 · Il permesso dei luoghi di quest'app: mai chiesto, negato, dato, mai (Gallery), o niente Store.
  - `fun placesRequestIntent(context: Context, write: Boolean = false): Intent?` — F10.8 · L'intent della richiesta dei luoghi dello Store, la prima volta (scelta B8). Va lanciato **con `startActivityForResult`** (`registerForActivityResult(StartActivityForResult())`): lo Store chiede «… vuole usare i tuoi luoghi. Consenti?» solo se il sistema gli dice chi chiede. Al ritorno, [placesAccess] dice la risposta.
  - `fun icons(context: Context): PampaAppIcons` — F10.8 · Le icone dell'app (gli `activity-alias` con `dev.pampa.account.ICON_SET`), per chi vuole sapere lo stato.
  - `suspend fun putPlace(context: Context, place: Place): PlacesResult`
  - `suspend fun availableBackups(context: Context): List<BackupInfo>`
  - `fun storeIntent(context: Context, section: String): Intent?` — L'intent che apre lo Store sulla pagina [section] (`AccountSections`), o `null` senza Store.
  - `fun onWiped(context: Context, handler: suspend () -> Unit)` — Un lavoro da fare quando lo Store conferma un'uscita da remoto: cancellare le copie sincronizzate e le chiavi.
- `class PampaAccountChangedReceiver : BroadcastReceiver()` — L'avviso `dev.pampa.account.CHANGED` dello Store (`b\api.md` § 5.4). Esportato senza permesso di proposito: chiunque può mandarlo, ma l'app non si fida del contenuto e rilegge dal provider. Un avviso falso costa una lettura in più.

## `engine-account/account/PampaAccountClient.kt`

- `sealed interface CallResult` — Il risultato di una chiamata al provider dello Store.
  - `data class Ok(val json: JsonObject) : CallResult`
  - `data object Denied : CallResult` — Lo Store ha rifiutato chi chiama (firma diversa): `SecurityException`.
  - `data object Missing : CallResult` — Il provider non c'è più (lo Store è stato tolto nel frattempo).
  - `data class Failed(val message: String) : CallResult`
- `fun interface AccountTransport` — La porta verso il provider dello Store: `ContentResolver.call`, sostituibile nelle prove.
  - `fun call(authority: String, method: String, request: JsonObject): CallResult`
  - `companion object`
    - `fun system(context: Context): AccountTransport` — Il trasporto vero, come il cliente del ponte dei tool (`AiToolClient`).
- `interface AccountCache` — La copia locale dello stato e dell'aspetto, per partire offline e senza aspettare lo Store (`b\architettura.md` § 9.4).
  - `suspend fun read(key: String): String?`
  - `suspend fun write(key: String, value: String?)`
- `sealed interface GoogleTokenResult` — La risposta a `google` (le concessioni Google, scelta N6: si accendono al primo uso vero, non in F10.4).
  - `data class Ok(val accessToken: String, val expiresAtElapsed: Long) : GoogleTokenResult`
  - `data object NotGranted : GoogleTokenResult`
  - `data object NeedsConsent : GoogleTokenResult`
  - `data object NoPlayServices : GoogleTokenResult`
  - `data object Offline : GoogleTokenResult`
  - `data object SignedOut : GoogleTokenResult`
  - `data object NoStore : GoogleTokenResult`
- `class PampaAccountClient(private val locate: () -> StoreHost?, private val transport: AccountTransport, private val cache: AccountCache? = null, private val clientVersion: String = "engine-account/4.0.0", private val elapsed: () -> Long = { SystemClock.elapsedRealtime() })` — Il cliente dell'account dentro un'app: trova lo Store ([StoreLocator]), negozia, e chiede. **Non usa mai la rete** (R9): tutto passa dal provider dello Store sul telefono. Non si fida del contenuto dei broadcast: a ogni avviso rilegge dal provider (`b\api.md` § 5.4).
  - `fun addChangeListener(listener: suspend (List<String>) -> Unit)` — F10.6 · Un lavoro da fare a ogni avviso `CHANGED`, con i suoi `what` (lo usa [PampaPeople] per `share`).
  - `val state: StateFlow<AccountState>`
  - `val appearance: StateFlow<SharedAppearance>`
  - `val grants: StateFlow<Set<String>>`
  - `val sharedLayer: Flow<EngineAppearanceLayer?>` — F10.8 · Le scelte comuni per quest'app, eccezione compresa ([PampaAppearance.layerOf]); `null` senza Store (R1). Il primo valore arriva dopo la copia locale ([restoreCache]) o la prima lettura, così il tema non lampeggia su quello dell'app prima di passare al comune.
  - `val placesFlow: Flow<PlacesResult>` — F10.8 · I luoghi, riletti a ogni avviso dello Store che li può cambiare (un permesso dato o tolto, un luogo nuovo).
  - `val store: StoreHost? get()` — Lo Store trovato all'ultima lettura, per aprirlo ([storeIntent]).
  - `suspend fun restoreCache()` — Rimette la copia locale (all'avvio, prima che lo Store risponda).
  - `suspend fun refresh(): AccountState` — Rilegge tutto dallo Store: lo trova, controlla la firma, negozia, legge stato e aspetto. Lo chiama [PampaAccount] all'avvio, quando l'app torna in primo piano, e a ogni avviso `CHANGED`.
  - `suspend fun onChanged(what: List<String>)` — Un avviso `CHANGED` dello Store: non ci si fida del contenuto, si rilegge. `wiped` si conferma con lo Store.
  - `val storeCapabilities: Set<String> get()` — Le capacità che lo Store ha annunciato all'ultima lettura.
  - `fun onWiped(handler: suspend () -> Unit)` — Un lavoro da fare quando lo Store conferma l'uscita da remoto: di solito cancellare le copie di `engine-sync`.
  - `suspend fun token(audience: String, scopes: Set<String>): TokenResult` — Un token per il servizio di quest'app (`audience`) con gli ambiti dati. Sta solo in memoria.
  - `suspend fun appKey(purpose: KeyPurpose): AppKeyResult` — Le chiavi di quest'app per [purpose], una per epoca (per `engine-sync` e per il backup). Mai di un'altra app.
  - `suspend fun places(): PlacesResult` — I luoghi comuni, se l'utente ha dato il permesso a quest'app. Funziona anche senza account.
  - `suspend fun placesAccess(): PlacesAccess` — F10.8 · Il permesso dei luoghi di quest'app (scelta B8): [PlacesAccess.NOT_ASKED] vuol dire che l'app può aprire la richiesta dello Store ([placesRequestIntent]); dopo un «no» è [PlacesAccess.DENIED] e l'app non richiede da sola. Uno Store di F10.4, che non dice il motivo, risponde [PlacesAccess.NOT_ASKED] finché non dà i luoghi.
  - `fun placesRequestIntent(write: Boolean = false): Intent?` — F10.8 · L'intent della richiesta dei luoghi, da lanciare **con `startActivityForResult`** (lo Store legge dal sistema chi chiede). [write]: l'app vuole anche aggiungere luoghi. `null` senza Store. Alla risposta l'app rilegge [placesAccess]: lo Store non mette niente nel risultato, così nessuno deve fidarsi di un risultato.
  - `suspend fun putPlace(place: Place): PlacesResult`
  - `suspend fun deletePlace(id: String): PlacesResult`
  - `suspend fun setAppearanceException(followGlobal: Boolean, override: Map<String, String>?)` — L'eccezione di quest'app all'aspetto comune, dalla sua sezione Aspetto. Scrive solo lo Store.
  - `suspend fun availableBackups(): List<BackupInfo>` — I backup di quest'app, per proporre il ripristino al primo avvio. Lista vuota se non ce ne sono o se non si sa.
  - `suspend fun google(service: String): GoogleTokenResult` — Un token Google per [service], col consenso dato nello Store (N6: in F10.4 lo Store risponde sempre «non concesso»).
  - `suspend fun refreshGrants(): Set<String>` — Le concessioni Google di quest'app.
  - `fun storeIntent(section: String): Intent?` — L'intent che apre lo Store su [section]; `null` senza Store. L'app lo lancia con `startActivity`.
  - `companion object`
    - `fun initialsOf(name: String): String` — Le iniziali di un nome: «Alessio Pampaloni» → «AP», «alessio» → «A».

## `engine-account/account/PampaAppIcons.kt`

- `data class PampaIconAlias(val className: String, val iconSet: String, val enabledInManifest: Boolean)` — Un'icona dell'app: un `<activity-alias>` col meta-data [AccountProtocol.META_ICON_SET].
- `enum class PampaIconOutcome` — Cosa è successo a una richiesta d'icona.
- `class PampaAppIcons internal constructor(private val host: Host)` — F10.8 · L'icona dell'app che segue la famiglia d'icone dell'aspetto comune (`icon.set`; critica C6; `b\architettura.md` § 5.2). L'app dichiara un `<activity-alias>` per famiglia, ognuno col meta-data `dev.pampa.account.ICON_SET`, e uno solo acceso nel manifest (l'icona di serie):
  - `interface Host` — Le domande al sistema, sostituibili nelle prove.
    - `fun aliases(): List<PampaIconAlias>`
    - `fun isEnabled(alias: PampaIconAlias): Boolean`
    - `fun setEnabled(alias: PampaIconAlias, enabled: Boolean)` — Accende o spegne [alias] tornando, se si può, allo stato del manifest (così l'icona di serie non lascia tracce).
    - `fun inForeground(): Boolean` — L'app ha qualcosa di visibile adesso?
    - `fun listenBackground(callback: () -> Unit)` — Chiama [callback] ogni volta che l'app passa in sottofondo. Si registra una volta sola.
  - `val hasAliases: Boolean get()` — L'app ha alias d'icona? Se no, `engine-account` non guarda nemmeno l'aspetto per l'icona.
  - `fun currentIconSet(): String?` — La famiglia dell'icona accesa adesso, o `null` se l'app non ha alias.
  - `fun request(iconSet: String?): PampaIconOutcome` — Chiede la famiglia [iconSet] (`null`: quella di serie). Adesso, o al primo sottofondo se l'app è aperta.
  - `companion object`
    - `fun of(context: Context): PampaAppIcons` — Le icone di questo processo.

## `engine-account/account/PampaAppearance.kt`

- `object PampaAppearance` — F10.8 · L'aspetto comune di Pampa Store tradotto per il tema dell'engine (`b\architettura.md` § 2.5 e § 5.2; `progetto.md` § 3.13). Le chiavi e i valori sono quelli del contratto del server, `Pampa-account\contract\v1\ prefs.json`: una prova li confronta con la copia del contratto.
  - `object Keys` — Le chiavi del contratto v1.
    - `const val MODE`
    - `const val ACCENT`
    - `const val GLASS`
    - `const val TEXT_SCALE`
    - `const val CONTRAST`
    - `const val ICON_SET`
    - `const val MOTION_REDUCED`
    - `const val FOLLOW_GLOBAL`
  - `val MODES` — I valori di `appearance.mode`.
  - `const val GLASS_DEFAULT` — F10.8b · Il vetro dell'aspetto comune è la sua **chiarezza** (scelta di Alessio del 25/11/2026): un numero da 0 «perfettamente trasparente» a 1 «più sfocato, come ora», di serie 1, come il cursore Liquid Glass di iOS 27.
  - `val ICON_SETS` — Le famiglie d'icone.
  - `val APP_KEYS` — Le chiavi che un'app può tenere diverse dal comune (`scopes: global+app`).
  - `const val TEXT_SCALE_MIN` — I limiti di `appearance.textScale` nel contratto.
  - `const val TEXT_SCALE_MAX`
  - `val SPECIAL_ACCENTS` — I due accenti che non sono un colore: «il colore di ogni app» e «i colori del sistema» (F10.8b, scelta di Alessio del 25/11/2026; nel contratto sono le `keywords` del tipo `color`).
  - `fun layerOf(shared: SharedAppearance): EngineAppearanceLayer` — Le scelte che valgono per quest'app: per ogni chiave l'eccezione (se l'app non segue il comune), poi il comune.
  - `fun glassPreferenceOf(value: String): Float?` — Il cursore del vetro dell'engine per un valore di `appearance.glass` (la chiarezza, da 0 a 1), o `null`.
  - `fun clarityOf(glassPreference: Float): Float` — La chiarezza per il contratto di un valore del cursore dell'engine. Un valore salvato oltre [GLASS_DEFAULT] (la metà colorata di prima, F10.8c) è 1, «come ora», come lo legge il vetro.
  - `fun exceptionValues(layer: EngineAppearanceLayer): Map<String, String>` — I campi di un'eccezione dell'app scritti nelle chiavi del contratto, per `setAppearance`. Solo le chiavi che un'app può tenere diverse ([APP_KEYS]); il vetro diventa la sua chiarezza, a passi di 0,01 come nel contratto.
- `class PampaSharedAppearance : EngineSharedAppearance` — F10.8 · La sorgente dell'aspetto comune per `EngineSettingsStore`: la trova `ServiceLoader` (`META-INF/services` di questo modulo), quindi un'app con `engine-account` ha il tema dello Store senza scrivere niente. Legge e scrive dallo Store con [PampaAccount]: nessuna rete (R9).

## `engine-account/account/PampaPeople.kt`

- `enum class PeopleRowKind(val wire: String)` — Che cosa c'è nella fila delle persone: «I tuoi dispositivi» per primi (scelta Q1), poi persone e gruppi.
- `data class PeopleRowEntry(val id: String, val kind: PeopleRowKind, val name: String, val initials: String, val mineral: String?, val verified: Boolean)` — Una voce della fila delle persone (`people.row`, `b\api.md` § 5.3): nome, iniziali, il seme del minerale (l'avatar) e un [id] **valido solo per l'app che chiama**. Niente chiavi, niente codice Pampa, niente casella (progetto, T10).
- `enum class ShareKind(val wire: String)` — Cosa si manda (`progetto.md` § 3.7): l'indirizzo di una pagina, una copia, o l'oggetto vivo che cambia per tutti.
- `data class SharedReply(val from: String, val text: String, val at: Long, val mine: Boolean)` — Una risposta attaccata a una cosa condivisa, quando non c'è Pampa Mail («Mamma: brava!»).
- `data class SharedItem(val id: String, val from: PeopleRowEntry, val kind: ShareKind, val title: String, val payload: String, val receivedAt: Long, val replies: List<SharedReply> = emptyList(), val inReplyTo: String? = null)` — Una cosa arrivata per quest'app («Condivisi con te», `share.received`): già aperta dallo Store, con chi la manda ([from], per il segno «Da X»), quando, e il contenuto che l'app mittente ha preparato ([payload], JSON dell'app).
- `sealed interface ShareResult` — Com'è andato un invio. [Queued]: lo Store l'ha chiuso e firmato, e se la rete manca riprova da solo (M3).
  - `data class Queued(val id: String) : ShareResult`
  - `data object NotAllowed : ShareResult` — L'app non è nella tabella dello Store, o la persona non accetta da quest'app.
  - `data object NoPeople : ShareResult` — Lo Store non c'è, o è troppo vecchio per le persone: la fila non si mostra (P1).
  - `data object SignedOut : ShareResult`
  - `data object UnknownPerson : ShareResult`
- `object PampaPeople` — Le persone viste da un'app (`progetto.md` § 3.7; `b\api.md` § 5.3). Tutto passa da Pampa Store sul telefono, col provider firmato: l'app non tocca mai la rubrica né una chiave, e non va mai in rete (R9).
  - `suspend fun row(context: Context): List<PeopleRowEntry>` — La fila: «I tuoi dispositivi» e fino a otto persone e gruppi recenti. Vuota senza Store o senza contatti.
  - `suspend fun send(context: Context, to: String, kind: ShareKind, title: String, payload: String): ShareResult` — Manda a [to] (un id della fila). Lo Store cifra, firma e consegna; la coda in uscita riprova se manca la rete.
  - `suspend fun received(context: Context, since: Long = 0): List<SharedItem>` — Le cose arrivate per quest'app dopo [since], già aperte. Rispetta blocchi e interruttori.
  - `suspend fun reply(context: Context, item: String, text: String): ShareResult` — «Rispondi» (`FluidQuickReply`): va nella conversazione in Pampa Mail, o resta attaccata alla cosa.
  - `suspend fun hide(context: Context, item: String, muteSender: Boolean = false): Boolean` — «Togli dai condivisi» ([muteSender] = falso) o «Non mostrare più da X in questa app» (vero).
  - `suspend fun sendToMyDevices(context: Context, address: String, title: String = ""): ShareResult` — «I tuoi dispositivi» (scelta Q1): manda l'indirizzo F8.12 di una pagina agli altri tuoi dispositivi.
  - `fun onReceived(context: Context, handler: suspend (List<SharedItem>) -> Unit)` — Chiamato quando lo Store dice che è arrivato qualcosa (`CHANGED` con `share`): qui l'app mostra **la sua** notifica («Alessio ti ha condiviso una lista»). Si registra all'avvio dell'app (`Application.onCreate`): l'avviso esplicito dello Store fa partire il processo anche con l'app chiusa.
- `object PeopleCalls` — Le chiamate delle persone sul cliente dell'account, senza `Context`: le usa [PampaPeople] e le prove.
  - `suspend fun row(c: PampaAccountClient): List<PeopleRowEntry>`
  - `suspend fun send(c: PampaAccountClient, to: String, kind: ShareKind, title: String, payload: String): ShareResult`
  - `suspend fun received(c: PampaAccountClient, since: Long): List<SharedItem>`
  - `suspend fun reply(c: PampaAccountClient, item: String, text: String): ShareResult`
  - `suspend fun hide(c: PampaAccountClient, item: String, muteSender: Boolean): Boolean`
  - `suspend fun devices(c: PampaAccountClient, address: String, title: String): ShareResult`
  - `fun entry(o: JsonObject)`
  - `fun item(o: JsonObject)`
- `fun interface AccountPeopleMethods` — Il gestore dei metodi delle persone dentro lo Store: lo dà `engine-people` (`PeopleProviderMethods`) al provider di `engine-account-host`. Sta qui, nel protocollo comune, così i due moduli non dipendono l'uno dall'altro. [caller] è il pacchetto di chi chiama, preso dal sistema e già controllato (firma, tabella delle app).
  - `suspend fun handle(caller: String, method: String, request: JsonObject?): JsonObject`

## `engine-account/account/StoreLocator.kt`

- `data class AccountClientConfig(val storePackage: String = "com.pampa.store", val devStorePackage: String = "com.pampa.store.debug", val devBuild: Boolean? = null)` — Come un'app trova Pampa Store (`b\api.md` § 5.1; architettura § 2.2). Lo Store di sviluppo esiste solo per chi costruisce le app: [devBuild] di serie segue il flag `debuggable` dell'app ospite.
- `data class StoreHost(val packageName: String, val authority: String, val versionName: String?)` — Lo Store trovato: pacchetto, autorità e versione.
- `class StoreLocator(private val config: AccountClientConfig, private val devBuild: Boolean, private val probe: Probe)` — Trova lo Store e decide se fidarsi (critica M1 e M9):
  - `interface Probe` — Le domande al sistema.
    - `fun provider(packageName: String, authority: String): Bundle?` — Il provider di [authority] se appartiene a [packageName] ed è installato, coi suoi meta-data; altrimenti `null`.
    - `fun sameSignature(packageName: String): Boolean` — Lo Store ha la stessa firma di quest'app?
    - `fun versionName(packageName: String): String?`
  - `fun candidates(): List<String>` — I pacchetti dello Store da provare, nell'ordine (M9).
  - `fun locate(): StoreHost?` — Lo Store di cui fidarsi, o `null`.
  - `companion object`
    - `fun systemProbe(context: Context): Probe` — Le domande vere al `PackageManager`.
    - `fun isDebuggable(context: Context): Boolean` — La build dell'app ospite è di sviluppo? Il flag `debuggable` del manifest, che le build vere non hanno.

## `engine-account/account/backup/PampaBackup.kt`

- `class BackupFile(val name: String, val bytes: ByteArray)` — Un file del backup di un'app: un nome scelto dall'app e i suoi byte.
- `data class BackupPackage(val app: String, val appVersion: String, val schema: Int, val createdAt: Long, val files: List<BackupFile>)` — Il pacchetto di backup di un'app (`b\chiavi.md` § 7): uguale per tutte, lo decide l'engine.
  - `val size: Long get()`
  - `fun file(name: String): BackupFile?`
- `interface PampaBackupSource` — La lista di un'app: cosa entra nel suo backup e come lo rimette. Esempi del documento A: PampAI le preferenze; ClasseViva obiettivi, eventi personali e orario aggiunto a mano; Fluid Weather località e segnalazioni; Fluidify le preferenze audio, **mai** i token Spotify né le chiavi degli AirPods.
  - `val schema: Int` — La versione del contenuto: cresce quando l'app cambia il formato dei suoi file.
  - `suspend fun collect(): List<BackupFile>` — I file da mettere nel backup adesso.
  - `suspend fun restore(backup: BackupPackage)` — Rimette un backup che l'utente ha scelto di ripristinare («Ripristinare i dati di ieri?»). L'engine non lo chiama mai da solo sopra dati esistenti (`b\chiavi.md` § 7).
- `object PampaBackupFormat` — Il formato del backup sul server (`b\chiavi.md` § 7 e § 9): il pacchetto in CBOR deterministico, chiuso con AES-256-GCM sotto `K_bak(app, epoca)`, legato a `accountId|app|backup|n|epoca`. Il server non lo sa aprire.
  - `const val VERSION`
  - `const val MAX_BYTES` — 5 MB a pacchetto (`routes.json`, `limits.backup.perPackageBytes`).
  - `fun encode(p: BackupPackage): ByteArray` — Il pacchetto in CBOR deterministico: stessi dati, stessi byte.
  - `fun decode(bytes: ByteArray): BackupPackage` — Legge un pacchetto; [PampaCryptoException] se non è un pacchetto di backup di versione 1.
  - `fun fingerprint(p: BackupPackage): ByteArray` — L'impronta del contenuto (senza la data): due backup con la stessa impronta sono uguali, e il secondo non parte.
  - `fun seal(p: BackupPackage, key: ByteArray, epoch: Int, accountId: String, n: Long): ByteArray` — Chiude [p] per il server: il numero [n] del backup e l'[epoch] della chiave entrano nei dati associati, così il server non può scambiare due backup né rimetterne uno di un'altra app. Oltre [MAX_BYTES] è un errore.
  - `fun epochOf(blob: ByteArray): Int` — L'epoca scritta nella testa di un blob, per scegliere la chiave.
  - `fun open(blob: ByteArray, keyFor: (Int) -> ByteArray?, accountId: String, app: String, n: Long): BackupPackage` — Apre un blob del server: la chiave dell'epoca scritta nella testa ([keyFor]), e gli stessi dati associati. Un backup spostato (altro numero, altra app, altro account) non si apre.

## `engine-account/account/crypto/PampaCrypto.kt`

- `object PampaCrypto` — Le primitive dell'Account Pampa, tutte con Bouncy Castle (`b\chiavi.md` § 9; progetto, T9): SHA-256, HMAC-SHA256, HKDF-SHA256 e AES-256-GCM. Sono le stesse di `core/crypto` di Codex, già provate sui telefoni veri, e le stesse formule del telefono finto del server (`Pampa-account\test\fake\phone.ts`): le prove di questo modulo e di `engine-account-host` lo verificano coi vettori degli RFC e con quelli usciti dal codic…
  - `const val KEY_BYTES`
  - `const val NONCE_BYTES`
  - `const val TAG_BYTES`
  - `val EMPTY: ByteArray` — Il sale vuoto di HKDF: per RFC 5869 § 2.2 vale come 32 zeri.
  - `fun random(size: Int): ByteArray` — [size] byte casuali dal generatore del sistema.
  - `fun sha256(vararg parts: ByteArray): ByteArray` — SHA-256 dei pezzi uno dopo l'altro.
  - `fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray` — HMAC-SHA256 di [data] con [key] (RFC 2104).
  - `fun hkdf(ikm: ByteArray, salt: ByteArray = EMPTY, info: String, length: Int = KEY_BYTES): ByteArray` — HKDF-SHA256 (RFC 5869) con un `info` di testo, in UTF-8: è così che sono scritte tutte le etichette del contratto (`PampaLabels`). Il sale vuoto vale come 32 zeri, come nel server (WebCrypto).
  - `fun hkdfRaw(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray` — HKDF-SHA256 con `info` già in byte: serve ai vettori di RFC 5869.
  - `fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray` — AES-256-GCM: [plaintext] cifrato con [key] e [nonce], legato ad [aad]. Il risultato è il testo cifrato col tag da 16 byte in coda, come in WebCrypto. **Il nonce non si ripete mai con la stessa chiave**: chi chiama ne prende uno casuale da 96 bit a ogni cifratura (`b\chiavi.md` § 9: il limite di circa 2³² cifrature per chiave è lontanissimo).
  - `fun aesGcmOpen(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray` — Apre ciò che ha chiuso [aesGcmSeal]. Chiave sbagliata, testo toccato o dati associati diversi danno [PampaCryptoException]: sopra questo livello la differenza non esiste, ed è giusto così.
  - `fun equal(a: ByteArray, b: ByteArray): Boolean` — Confronto a tempo costante: per i tag, i `check`, i `link` e i `mac`.
- `class PampaCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)` — Una busta che non si apre: chiave sbagliata, dati toccati, o messa al posto di un'altra (dati associati diversi).
- `object PampaBase64` — Base64url senza «=», il formato dei byte dentro i JSON del server (`b\api.md` § 1). La lettura accetta anche il riempimento, e rifiuta tutto ciò che non è base64url.
  - `fun encode(bytes: ByteArray): String`
  - `fun decode(text: String): ByteArray` — I byte di [text]; [IllegalArgumentException] se non è base64url.

## `engine-account/account/crypto/PampaEnvelope.kt`

- `class PampaEnvelope(val epoch: Int, val nonce: ByteArray, val ciphertext: ByteArray)` — La busta di ogni dato cifrato dell'account (`b\chiavi.md` § 9; contratto `e2e.json`, `envelope`):
  - `fun toJson(): String` — Il JSON della busta, con i campi nell'ordine del contratto.
  - `fun toField(): String` — Il valore del campo `ciphertext` sul server: base64url del JSON in UTF-8.
  - `companion object`
    - `const val VERSION`
    - `const val ALG`
    - `fun seal(key: ByteArray, epoch: Int, plaintext: ByteArray, aad: ByteArray): PampaEnvelope` — Chiude [plaintext] con [key] all'epoca [epoch], legandolo ad [aad]. Il nonce è casuale, 96 bit.
    - `fun parseField(field: String): PampaEnvelope` — Legge una busta dal campo `ciphertext` del server; [PampaCryptoException] se non è una busta di versione 1.
    - `fun parseJson(json: String): PampaEnvelope`
  - `fun open(key: ByteArray, aad: ByteArray): ByteArray` — Apre la busta con [key] (la chiave dell'epoca [epoch]) e gli stessi dati associati della chiusura.
- `class PampaRecordCipher(private val accountId: String, private val packageName: String)` — Le righe sincronizzate di un'app: la busta coi dati associati del contratto (`accountId|pacchetto|raccolta|idRiga| versione`). Le chiavi sono quelle che l'app riceve dallo Store con `PampaAccount.appKey` (una per epoca); qui non si salvano mai.
  - `fun seal(key: ByteArray, epoch: Int, collection: String, recordId: String, version: Long, plaintext: ByteArray): String` — Chiude [plaintext] per la riga [recordId] della raccolta [collection], alla versione che avrà sul server.
  - `fun open(keyFor: (Int) -> ByteArray?, collection: String, recordId: String, version: Long, field: String): ByteArray` — Apre una riga come la dà il server. [keyFor] dà la chiave di un'epoca, o `null` se l'app non la ha (in quel caso [PampaCryptoException]: l'app chiede le chiavi nuove allo Store e riprova). Una riga spostata, scambiata con un'altra o tornata a una versione vecchia è [PampaCryptoException].
  - `fun lastSeen(): Map<String, Long>` — L'ultima versione vista di ogni riga, `raccolta|idRiga` → versione.
  - `fun remember(versions: Map<String, Long>)` — Rimette le versioni viste salvate dall'app.

## `engine-account/account/crypto/PampaLabels.kt`

- `object PampaLabels` — Le etichette della cifratura dell'account: gli `info` di HKDF e di HPKE e i dati associati. Vengono dal contratto del server, `Pampa-account\contract\v1\e2e.json` (F10.3), che le fissa per lo Store e per il telefono finto del banco; una prova di questo modulo confronta ognuna con la copia del contratto, così un cambio da una parte sola non passa.
  - `const val RECOVERY_AUTH`
  - `const val DOOR_SEED`
  - `const val DOOR_SEAL`
  - `const val DOOR_TAG`
  - `const val DOOR_CHECK`
  - `const val EPOCH_LINK`
  - `const val APP_KEY`
  - `const val BACKUP_KEY`
  - `const val SHARED_KEY`
  - `const val TRANSFER_SEAL`
  - `const val TRANSFER_MAC`
  - `const val PRF_INPUT_TEXT` — L'ingresso fisso del PRF della passkey: lo scrive lo Store, mai il server (critica S9).
  - `const val SHARED_PLACES` — I nomi delle chiavi comuni (`b\chiavi.md` § 3): i luoghi, la rubrica, le chiavi private della persona.
  - `const val SHARED_RUBRICA`
  - `const val SHARED_PERSONA`
  - `fun prfInput(): ByteArray` — `SHA-256("pampa/v1/prf")`: i 32 byte che lo Store mette in `prf.eval.first`.
  - `fun fill(template: String, vararg values: Pair<String, Any>): String` — Sostituisce i segnaposto di [template] coi valori di [values]; un segnaposto senza valore è un errore.
  - `fun recordAad(accountId: String, packageName: String, collection: String, recordId: String, version: Long): ByteArray` — I dati associati di una riga sincronizzata: `accountId|pacchetto|raccolta|idRiga|versione` (K4).
  - `fun backupAad(accountId: String, packageName: String, n: Long, epoch: Int): ByteArray` — I dati associati di un pacchetto di backup: `accountId|pacchetto|backup|n|epoca` (`b\chiavi.md` § 7).
