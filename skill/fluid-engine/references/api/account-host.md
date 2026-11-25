# API · engine-account-host

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-account-host/account/host/AccountChangedBroadcaster.kt`

- `class AccountChangedBroadcaster(private val context: Context)` — F10.8 · L'avviso dello Store alle app quando cambia qualcosa (`b\api.md` § 5.4): `dev.pampa.account.CHANGED`, con dentro solo `what` (`appearance`, `places`, `state`, …). **Esplicito**, uno per app: si mandano solo alle app che hanno il ricevitore di `engine-account` **e la firma dello Store**. Un'app con un'altra firma non sa nemmeno che qualcosa è cambiato.
  - `fun receivers(): List<String>` — I pacchetti che ricevono l'avviso adesso: quelli col ricevitore e la firma dello Store, tranne lo Store.
  - `fun send(what: List<String>): Int` — Manda l'avviso [what] a ogni app ([receivers]); risponde a quante.

## `engine-account-host/account/host/AccountHostProvider.kt`

- `fun interface BackupDirectory` — Dove lo Store trova i backup di un'app sul server (pacchetto 5, `BackupScheduler`). `null` se non lo sa (offline).
  - `suspend fun available(packageName: String): List<BackupInfo>?`
- `class AccountProtocolHandler(private val session: AccountSession, private val custody: CustodyService, private val policy: AppPolicy, private val places: PlacesStore, private val appearance: AppearanceStore, private val backups: BackupDirectory, private val storeVersion: String, private val hasRecovery: () -> Boolean = { false }, private val people: AccountPeopleMethods? = null)` — Il protocollo del provider dello Store, metodo per metodo (`b\api.md` § 5.3), senza Android: chi chiama è già stato riconosciuto dal sistema ([caller] viene dall'UID del Binder, mai dall'app). Ogni risposta porta `protocol` e `capabilities`.
  - `suspend fun handle(caller: String, method: String, request: JsonObject?): JsonObject`
- `abstract class AccountHostProvider : ContentProvider()` — Il provider firmato dello Store (`b\api.md` § 5.1 e § 5.2): autorità `<applicationId>.account`, meta-data `dev.pampa.account.HOST = 1`, `exported="true"` **senza** `android:permission` (il permesso `signature` ha già dato problemi al ponte dei tool). Il controllo è a ogni chiamata, ed è l'unico cancello:
  - `abstract fun handler(): AccountProtocolHandler` — Il gestore dello Store, già collegato a sessione, custodia, luoghi e aspetto.
  - `open val callTimeoutMillis: Long` — Quanto può durare una chiamata (un token può aspettare la rete).

## `engine-account-host/account/host/AccountSession.kt`

- `sealed interface HostSession` — Lo stato della sessione dello Store su questo telefono.
  - `data object SignedOut : HostSession`
  - `data class SignedIn(val accountId: String, val deviceId: String, val displayName: String, val kind: SessionKind) : HostSession`
- `enum class ExitKind` — Come esce un dispositivo (`b\chiavi.md` § 4.4): chi esce da sé tiene le copie, chi è fatto uscire da remoto no.
- `sealed interface TokenOutcome` — Il risultato di una richiesta di token per un'app: le stesse ragioni del protocollo (`b\api.md` § 5.3).
  - `data class Ok(val token: String, val expiresInSeconds: Long) : TokenOutcome`
  - `data object SignedOut : TokenOutcome`
  - `data object NotAllowed : TokenOutcome`
  - `data object Disconnected : TokenOutcome`
  - `data object Offline : TokenOutcome`
  - `data class Unavailable(val reason: String) : TokenOutcome`
- `class AccountSession(private val client: AccountServerClient, private val baseUrl: String, private val sessions: SessionStore, private val deviceKey: DeviceKey, private val tokens: TokenCache, private val keyring: CustodyKeyring, private val policy: AppPolicy, private val notify: (what: List<String>) -> Unit = {})` — La sessione dello Store (`b\architettura.md` § 3; `b\api.md` § 3): il token d'accesso dello Store e quelli delle app in memoria, il rinnovo col token di rinnovo **più la prova del dispositivo**, e l'uscita.
  - `val state: StateFlow<HostSession>`
  - `val lastRemoteExit: Long get()` — L'ultima uscita da remoto vista, per rispondere alle app che chiedono conferma di `wiped` (`b\api.md` § 5.4).
  - `fun restore()` — Rilegge la sessione dal disco all'avvio dello Store.
  - `fun adopt(response: JsonObject, displayName: String, storePackage: String)` — Una risposta d'ingresso del server (`register/verify`, `login/verify`, `recovery/redeem`, `google/login`): salva la sessione e mette in memoria il token d'accesso dello Store.
  - `fun deviceField(name: String, storePackage: String, osApi: Int?, storeVersion: String): JsonObject` — Il campo `device` delle richieste d'ingresso: nome, pacchetto dello Store, e la pubblica della chiave del dispositivo.
  - `suspend fun storeToken(): String` — Il token d'accesso dello Store (`aud: account`), rinnovato se serve. [IOException] senza rete; [AccountServerException] per il resto. Un `session_revoked` pulisce prima di essere rilanciato.
  - `suspend fun appToken(packageName: String, audience: String, scopes: Set<String>): TokenOutcome` — Un token per l'app [packageName] (`b\architettura.md` § 2.3). Prima la tabella sul telefono (un'app non ammessa non fa partire niente), poi la cache, poi il server. Il pacchetto lo ha già ricavato dal sistema chi chiama.
  - `suspend fun signOut()` — «Esci da questo dispositivo»: revoca sul server (se c'è rete) e pulisce le chiavi. Le copie restano (R1).
  - `suspend fun onRevoked(reason: String?)` — Il server ha detto `session_revoked` (anche dal campanello): pulisce secondo il motivo.
  - `companion object`
    - `const val REFRESH_PATH`
    - `val REMOTE_REASONS` — I motivi di `session_revoked` che vogliono la cancellazione delle copie (`b\chiavi.md` § 4.4; F10.3).

## `engine-account-host/account/host/AppPolicy.kt`

- `class AppPolicy(val apps: Map<String, AppEntry> = DEFAULT, val devBuild: Boolean = false)` — La tabella delle app sul telefono (`b\architettura.md` § 2.3; contratto `Pampa-account\contract\v1\apps.json`): chi può chiedere quale pubblico e quali ambiti, chi può avere i luoghi. Sta due volte, qui per rispondere offline «non ammesso» e nel Worker (`src/apps.ts`), che è quella che conta. Tutte e due sono codice rivisto in git.
  - `enum class Places` — Se l'app può avere i luoghi comuni: mai (MyCloud Gallery: la sua «casa» resta sua), o chiedendo all'utente.
  - `data class AppEntry(val packageName: String, val name: String, val audiences: Map<String, Set<String>>, val places: Places = Places.NEVER, val googleServices: Set<String> = emptySet())`
  - `fun entryFor(packageName: String): AppEntry?` — La voce di [packageName], tenendo conto delle varianti di sviluppo solo se [devBuild].
  - `fun allows(packageName: String, audience: String, scopes: Set<String>): Boolean` — L'app può chiedere [audience] con [scopes]? Ambiti vuoti valgono «tutti quelli ammessi».
  - `fun workKeyGrants(installed: Collection<String>? = null): List<WorkKeyGrant>` — Le chiavi di lavoro da tenere pronte (`CustodyKeyring`): `sync.data` dà la chiave dell'app, `sync.backup` quella del backup.
  - `companion object`
    - `val DEV_SUFFIXES`
    - `const val STORE` — I pacchetti dello Store: il vero e quello di sviluppo (critica M9).
    - `const val STORE_DEBUG`
    - `val DEFAULT: Map<String, AppEntry>` — La tabella di partenza, uguale a `apps.json` del contratto v1 (una prova le confronta).

## `engine-account-host/account/host/AppearanceStore.kt`

- `object PrefsContract` — Le chiavi delle preferenze come le accetta il server (`Pampa-account\src\prefs.ts`, copia in `contract\v1\ prefs.json`: una prova le confronta). Il telefono controlla come il server, così una scelta che il server rifiuterebbe non nasce nemmeno.
  - `sealed interface Type`
    - `data class Enum(val values: List<String>) : Type`
    - `data object Color : Type`
    - `data object Bool : Type`
    - `data class Number(val min: Double, val max: Double, val step: Double) : Type`
  - `data class Spec(val scopes: String, val type: Type)` — Dove vale una chiave: `global`, `app` (solo come eccezione di un'app) o tutte e due.
  - `val PREFS: Map<String, Spec>`
  - `const val FOLLOW_GLOBAL`
  - `fun check(key: String, scope: String, value: JsonElement?): String?` — Il motivo per cui il server rifiuterebbe, o `null` se va bene (la stessa logica di `checkPref`).
  - `fun typed(key: String, text: String): JsonPrimitive?` — Il valore col suo tipo JSON, da un testo (le scelte dello Store e delle app viaggiano come testo nel provider).
- `object PrefsHlc` — L'orologio logico ibrido nel formato del server (`Pampa-account\src\hlc.ts`): `<ms, 13 cifre>-<contatore, 5 cifre>- <nodo [a-z0-9]{1,16}>`. A larghezza fissa, così il confronto fra testi è quello vero; qui si confronta comunque per campi, con l'ordine di [SyncHlc].
  - `fun format(h: SyncHlc): String`
  - `fun parse(text: String?): SyncHlc?`
- `data class PrefRow(val scope: String, val key: String, val value: JsonElement, val hlc: String)` — Una riga delle preferenze come viaggia col server: dove vale, la chiave, il valore (`null` toglie un'eccezione) e l'HLC.
- `class AppearanceStore(private val dir: File, private val wall: () -> Long = System::currentTimeMillis, private val onChange: (List<String>) -> Unit = {})` — L'aspetto comune sul telefono (`b\architettura.md` § 2.5 e § 5.2): il tema comune e le eccezioni delle app. Risponde **anche senza account**. Scrive solo lo Store; un'app chiede la sua eccezione con `setAppearance`. Le chiavi sono quelle del contratto ([PrefsContract]): una chiave o un valore che il server non accetterebbe non si scrive.
  - `data class Snapshot(val version: Long, val global: Map<String, String>, val overrides: Map<String, Map<String, String>>, val follow: Map<String, Boolean>)` — La versione cresce a ogni cambio: le app la confrontano con la loro copia.
  - `fun snapshot(): Snapshot`
  - `fun setGlobal(values: Map<String, String>): Snapshot` — Lo Store cambia il tema comune. Le chiavi e i valori che il contratto non accetta si scartano.
  - `fun setException(packageName: String, followGlobal: Boolean, override: Map<String, String>?): Snapshot` — L'eccezione di un'app: segue il comune, oppure tiene i suoi valori (solo le chiavi che valgono per un'app).
  - `fun responseFor(packageName: String): JsonObject` — La risposta di `appearance` per [packageName]: `{ version, global, override?, followGlobal }`.
  - `fun pending(): List<PrefRow>` — Le righe da mandare al server, solo delle chiavi che il server annuncia (critica M1), se le conosce.
  - `fun markSent(rows: Collection<PrefRow>)` — Il server ha preso [rows] (o le ha rifiutate per sempre): non si rimandano. Una riga riscritta nel frattempo resta.
  - `fun merge(rows: Collection<PrefRow>): Boolean` — Le righe arrivate dal server (o da un altro dispositivo): per ogni campo vince l'HLC più grande. Risponde se è cambiato qualcosa che si vede; in quel caso avvisa le app. L'orologio di qui le osserva, così una scelta fatta dopo averle viste ha sempre un HLC più grande.
  - `fun serverSeq(): Long` — Il numero d'ordine del server già letto (`GET /v1/prefs?since=`).
  - `fun setServerSeq(seq: Long)`
  - `fun setAccepted(keys: Set<String>)` — Le chiavi annunciate dal server (`accepted`): lo Store manda solo quelle (critica M1).
  - `fun syncClockTo(serverNowMillis: Long)` — L'ora del server appena letta ([serverNowMillis]): l'orologio logico si corregge di quanto il telefono è avanti o indietro, così le scelte nuove non nascono nel futuro del server (che le rifiuterebbe) né nel passato.
  - `fun restamp(rows: Collection<PrefRow>, serverNowMillis: Long)` — Le righe che il server ha rifiutato per l'orologio (`bad_hlc`: un telefono con l'ora avanti di più di un giorno). Non le ha viste nessuno, quindi rinascono con un orologio nuovo, all'ora corretta ([syncClockTo]) e dopo tutte le righe valide: l'orologio di prima, rimasto nel futuro, non si usa più. Restano da mandare.
  - `val lastHlc: SyncHlc get()` — L'ultimo HLC scritto o visto, per le prove.
  - `companion object`
    - `const val GLOBAL`
    - `val GLOBAL_KEYS: Set<String>` — Le chiavi del contratto v1 (`prefs.json`) che valgono per tutte le app.
    - `val APP_KEYS: Set<String>` — Quelle che un'app può tenere diverse (`scopes: global+app`).
    - `const val WHAT_APPEARANCE` — Il campo `what` dell'avviso alle app.
    - `const val MAX_FUTURE_MILLIS` — Quanto avanti sull'ora del server il server accetta un HLC: un giorno (`HLC_MAX_FUTURE_MS`, F10.3).

## `engine-account-host/account/host/CustodyService.kt`

- `sealed interface AppKeyOutcome` — La risposta a `appKey` per un'app (`b\api.md` § 5.3).
  - `data class Ok(val keys: AppKeySet) : AppKeyOutcome`
  - `data object NoE2e : AppKeyOutcome` — La cifratura non è partita (nessuna cosa cifrata ancora, Q11): l'app lavora in locale.
  - `data object Locked : AppKeyOutcome` — Le chiavi di lavoro non sono pronte perché il telefono non è stato sbloccato dall'accensione, o serve la madre.
  - `data object NeedsDoor : AppKeyOutcome` — Il Keystore ha perso la custodia, o l'app è nuova: lo Store deve aprire una porta (a telefono sbloccato).
  - `data object NotAllowed : AppKeyOutcome` — L'app non è nella tabella per questo scopo (K3).
- `class CustodyService(private val client: AccountServerClient, private val session: AccountSession, private val keyring: CustodyKeyring, private val policy: AppPolicy, private val accountId: () -> String?, private val installed: () -> Collection<String>? = { null }, private val deviceId: () -> String? = { null })` — La custodia al lavoro con il server (`b\chiavi.md` § 4): prende lo stato (`GET /v1/keys`), lo fa verificare a [KeyCustody], spedisce quello che lei prepara, e chiude il risultato in [CustodyKeyring]. Le operazioni che toccano la chiave madre si fanno a telefono sbloccato, e le azioni delicate vogliono una prova recente (S+R, `b\api.md` § 2): se il server risponde `reauth_required`, lo Store chiede l'impronta e riprov…
  - `suspend fun keysView(): KeysView`
  - `suspend fun startEncryption(recovery: RecoveryCode?, others: List<DoorSecret> = emptyList()): KeyCustody` — La prima cifratura (`b\chiavi.md` § 4.1): la chiave nasce qui, le porte date diventano le prime. Con il codice di recupero la porta viaggia con `auth` nello stesso batch (`POST /v1/recovery`), le altre con `PUT`.
  - `suspend fun ensureDeviceDoor(custody: KeyCustody? = keyring.loadCustody()): KeyCustody` — La porta di questo dispositivo (`device:<deviceId>`), se non c'è ancora: così le epoche nuove create da un altro dispositivo arrivano in silenzio ([catchUp]). Si fa appena la custodia ha la chiave, e una volta per i dispositivi già dentro. Non conta per K2.
  - `suspend fun catchUp(): KeyCustody` — Un altro dispositivo ha creato un'epoca nuova: la si apre con la porta di questo dispositivo, senza chiedere niente. Se la porta manca, [dev.antigravity.fluidengine.account.host.custody.CustodyBehindException]: serve una porta vera ([openWithDoor]) o il passaggio. Serve la madre: si fa a telefono sbloccato.
  - `suspend fun createRecoveryCode(): RecoveryCode` — Un codice di recupero nuovo, generato qui (critica S1). Se la cifratura è partita la sua porta va con `auth` nello stesso batch: un codice che non apre i dati non può esistere. Il codice si mostra una volta e basta.
  - `suspend fun addPrfDoor(credentialId: String, prfOutput: ByteArray, synced: Boolean)` — Una passkey col PRF diventa una porta: il telefono, che ha la chiave, la sigilla per tutte le epoche.
  - `suspend fun rotate(kind: EpochKind = EpochKind.NORMAL): KeyCustody` — Un'epoca nuova (`b\chiavi.md` § 6): dopo un «perso o rubato» [kind] è [EpochKind.LOST] e i tag si rifanno.
  - `suspend fun openWithDoor(door: DoorSecret): KeyCustody` — Apre la chiave dei dati da una porta: il telefono nuovo, il Keystore che l'ha persa (§ 11 punto 7), o il telefono rimasto indietro di un'epoca. Verifica tutto (K8, K9) prima di chiudere il risultato.
  - `suspend fun beginTransfer(): TransferOffer` — Il telefono **nuovo**: chiede un numero al server, a corpo vuoto, e prepara il QR.
  - `suspend fun transferInfo(qr: TransferQr): JsonObject` — Il telefono **vecchio** legge di che dispositivo è il passaggio, per la schermata «Passare i tuoi dati a…?».
  - `suspend fun confirmTransfer(qr: TransferQr)` — Il telefono **vecchio**, dopo l'impronta: sigilla e carica.
  - `suspend fun completeTransfer(offer: TransferOffer): KeyCustody` — Il telefono **nuovo** ritira, verifica, chiude nel suo Keystore e dimentica `n` e `s`.
  - `fun appKey(packageName: String, purpose: AppKeyPurpose): AppKeyOutcome` — Le chiavi di [packageName] per [purpose], solo sue (K3). Legge solo le chiavi di lavoro, mai la madre: con la raccomandazione A di Q7 risponde anche a telefono bloccato.
  - `fun refreshWorkKeys(): Boolean` — Al primo sblocco, o quando cambia la tabella delle app: ricava le chiavi di lavoro mancanti. Serve la madre.

## `engine-account-host/account/host/LocalStores.kt`

- `enum class PlaceAccess` — Cosa può fare un'app coi luoghi comuni: niente, leggerli, o anche scriverli (`b\chiavi.md` § 8).
- `class PlacesStore(private val vault: CustodyVault, private val dir: File, private val policy: AppPolicy, private val choice: KeyClassPolicy.Choice = KeyClassPolicy.CHOICE, private val onChange: (List<String>) -> Unit = {})` — I luoghi comuni, custoditi dallo Store (`b\chiavi.md` § 8; scelta N7 e B8): «I miei luoghi» (casa, scuola, lavoro, altro) e il permesso di ogni app. Ogni app li vede solo se l'utente glielo permette: la prima volta lo Store chiede «Fluid Weather vuole usare i tuoi luoghi (Casa, Scuola). Consenti?» ([PlacesConsent]). MyCloud Gallery non li riceve mai ([AppPolicy.Places.NEVER]). Funziona anche senza account.
  - `fun decision(packageName: String): PlaceAccess?` — La scelta dell'utente per [packageName], o `null` se non gliel'hanno mai chiesta.
  - `fun access(packageName: String): PlaceAccess` — Il permesso di [packageName]; un'app che la tabella esclude ha sempre [PlaceAccess.NONE].
  - `fun accessFor(packageName: String): PlacesAccess` — Il permesso come lo vede l'app ([PlacesAccess]): mai chiesto, negato, dato, o mai.
  - `fun decisions(): Map<String, PlaceAccess>` — Tutte le scelte fatte, per la pagina dell'account nello Store («Chi usa i tuoi luoghi»).
  - `fun setAccess(packageName: String, access: PlaceAccess): Boolean` — La scelta dell'utente, dalla richiesta dello Store o dalla pagina dell'account. Rifiutata per chi non li può avere. [PlaceAccess.NONE] è «negato» (o «revocato»): l'app non richiede più da sola.
  - `fun forget(packageName: String)` — Dimentica la scelta: l'app tornerà a chiedere (la pagina dell'account, «Chiedi di nuovo»).
  - `suspend fun all(): List<Place>` — Tutti i luoghi: per lo Store. Alle app li dà [listFor].
  - `suspend fun listFor(packageName: String): List<Place>?`
  - `suspend fun put(packageName: String?, place: Place): Boolean` — Aggiunge o cambia un luogo. [packageName] `null` è lo Store stesso.
  - `suspend fun delete(packageName: String?, id: String): Boolean`
  - `fun wipe()` — Dopo un'uscita **da remoto** (telefono perso o rubato, `b\chiavi.md` § 4.4): via i luoghi, i permessi e le chiavi della loro cassaforte. La casa dice dove abiti, e chi ha il telefono non deve trovarla. Un'uscita volontaria non la chiama. Dopo, i luoghi ripartono vuoti.
  - `suspend fun migrateLegacy()` — I luoghi del file di F10.4, se ci sono: entrano nello spazio una volta, poi il file tiene solo i permessi. F10.4 non è mai uscito; c'è perché un telefono di prova non perda i suoi luoghi.
  - `companion object`
    - `const val COLLECTION` — Lo spazio e la raccolta sul server: `places`, che il server riserva allo Store (`sync.places`, F10.3).
    - `val SPACE`
    - `const val WHAT`
    - `const val F_ID`
    - `const val F_NAME`
    - `const val F_KIND`
    - `const val F_LAT`
    - `const val F_LON`
    - `const val F_ADDRESS`

## `engine-account-host/account/host/PlacesConsent.kt`

- `class PlacesConsent(private val places: PlacesStore, private val policy: AppPolicy)` — F10.8 · La richiesta dei luoghi, nello Store (scelta B8; `b\chiavi.md` § 8): la prima volta che un'app li vuole, lo Store chiede «Fluid Weather vuole usare i tuoi luoghi (Casa, Scuola). Consenti?». La domanda è dello Store, come un permesso di Android è del sistema: se la facesse l'app, un'app potrebbe rispondersi da sola.
  - `sealed interface Step`
    - `data class Ask(val packageName: String, val appName: String, val placeNames: List<String>, val write: Boolean) : Step` — Da chiedere all'utente con [question]; poi [answer].
    - `data class Decided(val access: PlacesAccess) : Step` — Già deciso: niente da chiedere. La pagina si chiude subito.
    - `data object Refused : Step` — Non si chiede: chi chiama non si sa, o non è un'app che può avere i luoghi.
  - `suspend fun begin(callingPackage: String?, write: Boolean): Step` — Cosa fare per una richiesta di [callingPackage] (dal sistema), che vuole anche scrivere se [write].
  - `fun answer(ask: Step.Ask, allow: Boolean): PlacesAccess` — La risposta dell'utente a [ask]. Gli altri vengono avvisati (`places`) da [PlacesStore].
  - `companion object`
    - `fun isRequest(intent: Intent?): Boolean` — È una richiesta dei luoghi? (`ACTION_OPEN` con la sezione [AccountSections.PLACES_REQUEST]).
    - `fun wantsWrite(intent: Intent?): Boolean` — Vuole anche scrivere?
    - `fun callerOf(activity: Activity): String?` — Chi chiede, dal sistema, se ha la stessa firma dello Store; altrimenti `null`. Senza `startActivityForResult` il sistema non lo dice, e la risposta è `null`.

## `engine-account-host/account/host/backup/StoreBackup.kt`

- `class StoreBackupJob(private val session: AccountSession, private val client: AccountServerClient, private val keyring: CustodyKeyring, private val accountId: () -> String?, private val content: suspend () -> BackupPackage, private val stateDir: File, private val storePackage: String = AppPolicy.STORE)` — Il backup dello Store (`b\chiavi.md` § 7; architettura § 5.8): l'elenco delle app Pampa installate e le preferenze dello Store, così un telefono nuovo propone «Avevi 7 app Pampa: installarle?». Cifrato con `K_bak(com.pampa.store)`.
  - `sealed interface Outcome`
    - `data class Uploaded(val n: Long, val bytes: Int) : Outcome`
    - `data object Unchanged : Outcome`
    - `data object NoE2e : Outcome`
    - `data object Locked : Outcome`
    - `data object SignedOut : Outcome`
    - `data object Offline : Outcome`
    - `data class Failed(val message: String) : Outcome`
  - `suspend fun run(): Outcome`
  - `companion object`
    - `suspend fun list(client: AccountServerClient, token: String): List<Pair<String, BackupInfo>>` — `GET /v1/backups`: lo Store vede l'elenco di tutte le app dell'account, senza il contenuto.
    - `fun storeContent(context: Context, policy: AppPolicy, storeVersion: String, preferences: Map<String, String>, now: Long = System.currentTimeMillis()): BackupPackage` — Il contenuto del backup dello Store: le app Pampa installate (quelle della tabella, dal `PackageManager`) e le preferenze dello Store. Mai token, mai credenziali.
- `class ServerBackupDirectory(private val session: AccountSession, private val client: AccountServerClient) : BackupDirectory` — L'elenco dei backup di un'app, per `backup.available` (`b\api.md` § 5.3): lo chiede lo Store col suo token.
- `interface AccountHostOwner` — Lo Store dà il suo lavoro di backup a WorkManager attraverso la sua `Application`.
  - `fun storeBackupJob(): StoreBackupJob?` — Il lavoro del backup già collegato, o `null` se l'account non c'è.
- `class StoreBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)` — Il lavoro in sottofondo di WorkManager: chiama [StoreBackupJob] e riprova più tardi se il telefono è chiuso o offline.
- `object BackupScheduler` — Il calendario del backup dello Store: una volta al giorno, con la rete, un lavoro solo.
  - `const val WORK_NAME`
  - `fun schedule(context: Context)`
  - `fun cancel(context: Context)` — All'uscita dall'account.

## `engine-account-host/account/host/custody/CustodyKeyring.kt`

- `data class WorkKeyGrant(val packageName: String, val purposes: Set<AppKeyPurpose>)` — Quali chiavi di lavoro tenere pronte per un'app: lo dice la tabella delle app (`AppPolicy`).
- `class CustodyKeyring(private val vault: CustodyVault, private val dir: File, private val choice: KeyClassPolicy.Choice = KeyClassPolicy.CHOICE)` — La custodia sul telefono, nelle due classi (`b\chiavi.md` § 3.3, K10): - `custodia.bin`: la chiave dei dati di tutte le epoche e le porte conosciute, nella classe di [CustodyKeyKind.DATA_KEY] (madre, con la raccomandazione A di Q7);
  - `val hasCustody: Boolean get()` — C'è una custodia su questo telefono? Non apre niente.
  - `fun saveCustody(custody: KeyCustody, grants: List<WorkKeyGrant>)` — Chiude la custodia (classe madre) e ricava subito le chiavi di lavoro per [grants]. Va fatto a telefono sbloccato.
  - `fun loadCustody(): KeyCustody?` — Apre la custodia; [CustodyLockedException] a telefono bloccato, [CustodyKeyLostException] se il Keystore l'ha persa.
  - `fun refreshWorkKeys(custody: KeyCustody, grants: List<WorkKeyGrant>)` — Ricava e chiude le chiavi di lavoro delle app di [grants] e quelle comuni dello Store. Si rifà a ogni epoca nuova e quando la tabella delle app cambia; un'app nuova chiede la sua chiave e trova `locked` finché non si ricavano.
  - `fun workKeys(packageName: String, purpose: AppKeyPurpose): AppKeySet?` — Le chiavi di lavoro di [packageName] per [purpose], o `null` se non sono mai state ricavate (l'app è nuova, o la cifratura non è partita). Usa solo la classe delle chiavi di lavoro: con la raccomandazione A di Q7 funziona anche a telefono bloccato.
  - `fun sharedKeys(name: String): AppKeySet?` — Le chiavi comuni (`places`, `rubrica`, `persona`) ricavate per lo Store.
  - `fun wipe()` — Uscita, uscita da remoto, «perso»: via i file e le chiavi del Keystore (`b\chiavi.md` § 4.4).
  - `companion object`
    - `val SHARED` — Le chiavi comuni che lo Store tiene pronte (`b\chiavi.md` § 3).

## `engine-account-host/account/host/custody/CustodyMath.kt`

- `object CustodyMath` — Le formule della custodia, una per riga, come le fissa il contratto del server (`Pampa-account\contract\v1\e2e.json`, `inputs`) e come le usa il suo telefono finto (`test\fake\phone.ts`). Tutto il resto della custodia è costruito sopra queste; le prove le confrontano coi valori usciti dal codice del server.
  - `fun doorSeed(secret: ByteArray, salt: ByteArray): ByteArray` — Il seme della porta: resta sempre sul telefono (K6).
  - `fun doorKeys(secret: ByteArray, salt: ByteArray): X25519KeyPair` — La coppia X25519 di una porta, dal suo segreto (codice o uscita del PRF) e dal suo sale.
  - `fun sealInfo(doorId: String, epoch: Int): ByteArray`
  - `fun sealForDoor(doorPub: ByteArray, doorId: String, epoch: Int, cd: ByteArray): ByteArray` — Sigilla la chiave dei dati [cd] di un'epoca per la porta con la pubblica [doorPub].
  - `fun openDoorSeal(door: X25519KeyPair, doorId: String, epoch: Int, sealed: ByteArray): ByteArray`
  - `fun tag(cdR: ByteArray, doorId: String, kind: String, pub: String, salt: String, r: Int): ByteArray` — Il sigillo d'autenticità di una porta (K8), fatto con la chiave dei dati dell'epoca di riferimento [r].
  - `fun check(secret: ByteArray, cdRoot: ByteArray): ByteArray` — Il controllo della prima epoca (K9): solo chi conosce il segreto della porta lo sa fare.
  - `fun link(cdPrev: ByteArray, epoch: Int, kind: String, cd: ByteArray): ByteArray` — Il collegamento dell'epoca [epoch] alla precedente (K9). Il tipo entra nella catena (F10.3, S11).
  - `fun appKey(cd: ByteArray, accountId: String, packageName: String): ByteArray`
  - `fun backupKey(cd: ByteArray, accountId: String, packageName: String): ByteArray`
  - `fun sharedKey(cd: ByteArray, accountId: String, name: String): ByteArray`
  - `fun recoveryAuth(code: ByteArray): ByteArray` — `auth` del codice di recupero: l'unico valore del codice che va al server (critica S1).
  - `fun transferInfo(transferId: String): ByteArray`
  - `fun transferMac(s: ByteArray, transferId: String, n: ByteArray, sealed: ByteArray): ByteArray`

## `engine-account-host/account/host/custody/CustodyVault.kt`

- `class CustodyLockedException(val keyClass: KeyClass) : Exception("la chiave ${keyClass.name.lowercase()} si apre solo a telefono sbloccato")` — La chiave di quella classe adesso non si apre: il telefono è bloccato (classe madre), o non è ancora stato sbloccato.
- `class CustodyKeyLostException(val keyClass: KeyClass, cause: Throwable? = null) : Exception("la chiave ${keyClass.name.lowercase()} del Keystore non vale più", cause)` — La chiave del Keystore non c'è più o non vale più: un ripristino di fabbrica parziale, un aggiornamento che cambia il Keystore, il blocco schermo tolto su Android 9–14 (`b\chiavi.md` § 11, punto 7). I file chiusi con lei sono persi: lo Store se ne accorge e riapre la chiave dei dati da una porta (codice, passkey col PRF, passaggio).
- `interface CustodyVault` — Dove la custodia chiude i suoi file: una chiave per classe ([KeyClass]). L'implementazione vera è [KeystoreCustodyVault], sopra la cassaforte dell'engine (`engine-vault`, F8.23); le prove usano `testing.MemoryCustodyVault`, che sa fare il telefono bloccato e la chiave persa.
  - `fun seal(keyClass: KeyClass, plaintext: ByteArray, aad: ByteArray): ByteArray` — Chiude [plaintext] con la chiave della classe [keyClass], legandolo ad [aad].
  - `fun open(keyClass: KeyClass, sealed: ByteArray, aad: ByteArray): ByteArray` — Apre; [CustodyLockedException] se la classe adesso è chiusa, [CustodyKeyLostException] se la chiave non vale più.
  - `fun destroy()` — Cancella le chiavi (uscita, uscita da remoto, eliminazione).
- `open class KeystoreCustodyVault(private val keystore: VaultKeystore, private val isDeviceLocked: () -> Boolean, private val config: Config = Config(), private val sdkInt: Int = Build.VERSION.SDK_INT) : CustodyVault` — La custodia sopra la cassaforte dell'engine (`engine-vault`, F8.23): una chiave AES-256-GCM per classe, creata e usata dal [VaultKeystore] (il Keystore di Android in un'app, `MemoryVaultKeystore` nelle prove), col formato della cassaforte (`nonce(12) ‖ testo cifrato ‖ tag`).
  - `enum class StrongBoxUse`
  - `data class Config(val aliasPrefix: String = "dev.pampa.account.custody", val strongBox: StrongBoxUse = StrongBoxUse.MOTHER_ONLY, val unlockedRequiredFromApi: Int = 35)`
  - `val strongBoxBacked: MutableMap<KeyClass, Boolean?>` — Le chiavi messe davvero in StrongBox (null se non si sa), per il rapporto della prova sul tablet.
  - `fun specFor(keyClass: KeyClass): VaultKeySpec` — Come nasce la chiave di [keyClass]: la ricetta della cassaforte con le regole della custodia.
- `class AndroidKeystoreVault(context: Context, config: KeystoreCustodyVault.Config = KeystoreCustodyVault.Config()) : KeystoreCustodyVault(keystore = AndroidVaultKeystore(context), isDeviceLocked = { context.getSystemService(KeyguardManager::cla…, config = config)` — La custodia nel Keystore di Android, attraverso `engine-vault` ([AndroidVaultKeystore]): è quella che usa lo Store. Il telefono bloccato lo dice `KeyguardManager.isDeviceLocked`.

## `engine-account-host/account/host/custody/KeyClasses.kt`

- `enum class KeyClass` — Le due classi di chiavi sul telefono (`b\chiavi.md` § 3.3, K10; critica S3).
- `enum class CustodyKeyKind` — Cosa custodisce lo Store, per tipo: ogni tipo ha la sua classe secondo [KeyClassPolicy].
- `object KeyClassPolicy` — **Domanda Q7 di Alessio, in attesa** (`progetto.md` § 8.2). Qui c'è la raccomandazione A, ed è l'unico posto da cambiare: [CHOICE].
  - `enum class Choice`
  - `val CHOICE: Choice` — La risposta a Q7. Di serie la raccomandazione A. Chi la usa la riceve come parametro (`AccountHostConfig`, `CustodyKeyring`), così le prove provano tutte e tre le risposte senza toccare questa costante.
  - `fun classOf(kind: CustodyKeyKind, choice: Choice = CHOICE): KeyClass` — La classe di [kind] con la scelta [choice] (di serie quella di oggi).
  - `fun workKeysInBackground(choice: Choice = CHOICE): Boolean` — Il lavoro in sottofondo può usare le chiavi di lavoro col telefono bloccato? Sì solo con la A.
- `object RecoveryCodePolicy` — **Domanda Q11 di Alessio, in attesa**: quando si crea il codice di recupero. Qui c'è la raccomandazione A ([AT_FIRST_ENCRYPTED]): alla prima cosa cifrata (il primo backup, la prima persona aggiunta), con «Più tardi»; fino ad allora non parte niente di cifrato. Con la B ([AT_CREATION]) lo Store lo chiede già alla creazione dell'account. La regola delle due porte (K2) resta uguale con tutte e due.
  - `enum class When`
  - `val WHEN: When` — La risposta a Q11. Di serie la raccomandazione A.

## `engine-account-host/account/host/custody/KeyCustody.kt`

- `class CustodyTamperedException(message: String) : Exception(message)` — Il server ha mentito, o qualcosa non torna: il telefono **non usa** quello che ha ricevuto e lo segnala (`b\chiavi.md` § 11, punti 2 e 10). Il messaggio dice cosa, mai un segreto.
- `class CustodyBehindException(val serverEpoch: Int, val mine: Int) : Exception("c'è un'epoca nuova ($serverEpoch) che questo telefono non ha ancora ($mine)")` — Un altro dispositivo ha creato un'epoca nuova ([serverEpoch]) che questa custodia non ha. La si apre con la porta di questo dispositivo ([KeyCustody.catchUp] senza porta), in silenzio; se la porta del dispositivo manca, con una porta vera (codice, passkey col PRF) o col passaggio. Fino ad allora si scrive con l'epoca che si ha (`b\chiavi.md` § 3.3).
- `class NeedsDoorException : Exception("Per proteggere i tuoi dati, crea un codice di recupero. Ti servirà se perdi questo telefono.")` — La regola delle due porte (K2): niente parte cifrato finché non c'è una porta **fuori da questo telefono**, cioè il codice di recupero o una passkey col PRF sincronizzata. Lo Store chiede il codice (`RecoveryCodePolicy`). Le porte dei dispositivi non contano.
- `class DoorSecret private constructor(val doorId: String, val kind: DoorKind, internal val secret: ByteArray, val external: Boolean)` — Il segreto di una porta, solo sul telefono: i 16 byte del codice di recupero o i 32 byte del PRF di una passkey. [external] dice se la porta si apre **senza** questo telefono (K2): il codice sì; una passkey solo se è sincronizzata (flag BS della passkey, che lo Store legge da `engine-auth`).
  - `companion object`
    - `fun recovery(code: RecoveryCode): DoorSecret`
    - `fun prf(credentialId: String, prfOutput: ByteArray, synced: Boolean): DoorSecret` — La porta di una passkey col PRF; [synced] è il flag BS. L'uscita del PRF non lascia mai il telefono (K6).
    - `fun fromPasskey(result: FluidPasskeyResult): DoorSecret` — La porta di una passkey appena usata, letta da `engine-auth` (`FluidWebAuthnJson.readAssertion` o `readCreation` con l'ingresso di [StorePrf]): l'id della credenziale, l'uscita del PRF e il flag BS (`backedUp`) dai dati dell'autenticatore. [IllegalArgumentException] se il gestore non ha dato l'uscita del PRF: quella passkey non fa da porta.
    - `fun device(deviceId: String, secret: ByteArray): DoorSecret` — La porta di un dispositivo già dentro (`e2e.json`, `deviceDoor`): 32 byte casuali nati sul dispositivo, che restano nella sua custodia (mai al server, mai in un backup, mai nel passaggio). Non conta per K2.
    - `const val RECOVERY_DOOR`
    - `fun deviceDoorId(deviceId: String): String` — L'id della porta di un dispositivo: `device:<deviceId>`.
- `class AppKeySet(val current: Int, val keys: SortedMap<Int, ByteArray>)` — Le chiavi di un'app per tutte le epoche, e l'epoca con cui scrive oggi. Quello che risponde `appKey` (`b\api.md` § 5.3).
- `enum class AppKeyPurpose(val wire: String)` — Per cosa servono le chiavi di un'app: la sincronizzazione dei suoi dati o il suo backup.
- `class KeyCustody private constructor(val accountId: String, epochs: Map<Int, ByteArray>, knownDoors: Set<String>, private var device: DoorSecret? = null)` — La custodia della chiave dei dati dell'account (`b\chiavi.md`): tutte le epoche di `CD`, le porte che questo telefono conosce, e le operazioni che le cambiano. **Logica pura**: non parla col server e non tocca il Keystore. Riceve lo stato del server ([KeysView]), lo verifica, e restituisce i corpi da mandare;
  - `val deviceDoorId: String? get()` — La porta di questo dispositivo (`device:<deviceId>`), se l'ha già creata.
  - `val epochs: Set<Int> get()` — Le epoche che questa custodia tiene.
  - `val current: Int get()` — L'epoca con cui si scrive oggi.
  - `val knownDoors: Set<String> get()` — Le porte che questo telefono ha creato o aperto: se il server ne toglie una, se ne accorge.
  - `fun appKeys(packageName: String, purpose: AppKeyPurpose, from: Int = rootOfChain()): AppKeySet` — Le chiavi di [packageName] per [purpose], una per epoca della catena di oggi (K3: mai quelle di un'altra app).
  - `fun sharedKeys(name: String, from: Int = rootOfChain()): AppKeySet` — Le chiavi comuni `K_com(name)`: «places», «rubrica», «persona». Solo lo Store (e Pampa Mail per la rubrica).
  - `fun rootOfChain(): Int` — La radice della catena di oggi: 1, o l'epoca dell'ultimo «Ricomincia».
  - `fun buildDoor(door: DoorSecret, view: KeysView?): DoorInfo` — La porta di [door] per tutte le epoche della catena di oggi, col tag dell'epoca di riferimento [reference] e il `check` della radice. Prima di costruirla verifica lo stato del server ([verify]), se c'è.
  - `fun forgetDoor(doorId: String)` — Una porta tolta da questo telefono (col sì del server): non la si cerca più.
  - `fun verify(view: KeysView): Int` — Verifica tutto lo stato del server prima di usarlo: catena, sigilli d'autenticità, porte conosciute, epoca che non torna indietro. Restituisce l'epoca di riferimento dei tag, ricalcolata dalla catena (non presa dal server).
  - `fun verifyChain(view: KeysView): Int` — K9: ogni epoca dopo la radice è legata alla precedente, col suo tipo. Restituisce la radice.
  - `fun verifyTags(view: KeysView): Int` — K8: il sigillo d'autenticità di ogni porta è fatto con la chiave dei dati, all'epoca di riferimento giusta: la radice, o l'ultimo «perso o rubato» (S11). Una porta che conoscevo e che manca è scoperta. Restituisce il riferimento.
  - `fun rotate(view: KeysView, kind: EpochKind = EpochKind.NORMAL): Pending<EpochUpload>` — Un'epoca nuova, sigillata per **tutte** le porte del server (K5), dopo averne verificato i tag. Con [kind] = [EpochKind.LOST] rifà anche i sigilli d'autenticità con la chiave nuova (S11). Restituisce il corpo da mandare e la custodia che vale **se il server dice sì**: fino ad allora questa non cambia.
  - `fun reset(doors: List<DoorSecret>): Pending<ResetUpload>` — «Ricomincia» (`b\chiavi.md` § 5): una chiave nuova, un'epoca nuova di tipo `reset` che fa da radice, e le porte nuove. Le epoche vecchie se ne vanno: i dati chiusi con quelle sono persi. Serve almeno una porta esterna (K2).
  - `fun sealTransfer(qr: TransferQr): TransferSeal` — Il telefono **vecchio** ha inquadrato il QR del nuovo e l'utente ha confermato con l'impronta: sigilla tutte le epoche verso `N` e firma il sigillo col segreto `s` del QR. Né `N` né `s` passano dal server.
  - `fun catchUp(view: KeysView, door: DoorSecret): KeyCustody` — Il telefono è rimasto indietro di un'epoca ([CustodyBehindException]) e apre una porta per averla. La custodia nuova deve **contenere** questa: ogni epoca che avevo deve essere la stessa, altrimenti il server ha cambiato le chiavi alle mie spalle.
  - `fun catchUp(view: KeysView): KeyCustody` — Rimettersi in pari **in silenzio** con la porta di questo dispositivo (`e2e.json`, `deviceDoor.catchUp`): chi ha ruotato ha sigillato l'epoca nuova anche per lei. Gli stessi controlli di quando si apre una porta (la pubblica, K9 sulla radice, la catena, i tag K8), e le epoche che avevo devono restare le stesse.
  - `fun createDeviceDoor(deviceId: String, view: KeysView): Pending<DoorInfo>` — La porta di questo dispositivo, appena la custodia ha la chiave (dopo il PRF, il codice o il QR, e una volta per i dispositivi già dentro). Restituisce il corpo di `PUT /v1/keys/doors/device:<deviceId>` e la custodia che la ricorda.
  - `class Pending<T>(val upload: T, val custody: KeyCustody)` — Quello che cambia se il server dice sì: il corpo da mandare e la custodia di dopo.
  - `companion object`
    - `fun start(accountId: String, doors: List<DoorSecret>): Pending<FirstUpload>` — La prima volta che serve la cifratura (`b\chiavi.md` § 4.1): `CD_1` nasce qui e le porte date diventano le prime. **K2**: senza almeno una porta esterna, [NeedsDoorException], e niente parte.
    - `fun openWithDoor(accountId: String, view: KeysView, door: DoorSecret): KeyCustody` — Un telefono nuovo apre una porta (`b\chiavi.md` § 4.2): ricava la sua privata dal segreto, apre i sigilli, verifica la radice col `check` (K9), la catena dei `link` e i sigilli d'autenticità (K8).
    - `fun receiveTransfer(accountId: String, offer: TransferOffer, seal: TransferSeal, view: KeysView): KeyCustody` — Il telefono **nuovo** ritira il sigillo del passaggio: controlla il `mac` col segreto del QR prima di tutto, apre con la sua effimera, e verifica la catena e i sigilli d'autenticità (K9). Poi chi chiama dimentica l'offerta ([TransferOffer.forget]).
- `class FirstUpload(val doors: List<DoorInfo>, val epoch: EpochUpload)` — Il primo avvio della cifratura: le porte da creare e la prima epoca (`POST /v1/recovery` o `PUT …/doors`, poi `POST /v1/keys/epochs`).

## `engine-account-host/account/host/custody/KeysView.kt`

- `enum class EpochKind(val wire: String)` — Il tipo di un'epoca (contratto `e2e.json`, `epochKinds`). Il tipo entra nella catena (F10.3).
  - `companion object`
    - `fun of(wire: String?): EpochKind?`
- `enum class DoorKind(val wire: String)` — Il tipo di una porta (contratto `e2e.json`, `doorKinds`): il codice di recupero, una passkey col PRF, o un dispositivo già dentro ([DEVICE], `device:<deviceId>`: riceve le epoche nuove in silenzio, e non conta per K2).
  - `companion object`
    - `fun of(wire: String?): DoorKind?`
- `data class EpochInfo(val epoch: Int, val kind: EpochKind, val link: String?)` — Un'epoca come la racconta il server: il numero, il tipo, il collegamento alla precedente (`null` per la radice).
- `data class SealInfo(val epoch: Int, val sealed: String)` — Il sigillo HPKE della chiave dei dati di un'epoca per una porta, in base64url (`enc || testo cifrato`).
- `data class DoorInfo(val doorId: String, val kind: DoorKind, val pub: String, val salt: String, val tag: String, val tagEpoch: Int, val check: String, val seals: List<SealInfo>)` — Una porta come sta sul server: la pubblica, il sale, il sigillo d'autenticità (K8) con la sua epoca di riferimento, il controllo della radice (K9) e un sigillo per epoca. Tutto pubblico: nessun segreto della porta sta qui.
  - `fun toJson(): JsonObject` — Il corpo di `PUT /v1/keys/doors/{doorId}` e della porta dentro `POST /v1/recovery` (`b\api.md` § 4.5 e § 4.11).
- `data class KeysView(val epoch: Int, val root: Int, val reference: Int, val epochs: List<EpochInfo>, val doors: List<DoorInfo>)` — Lo stato delle chiavi come lo dà `GET /v1/keys`: l'epoca attiva, la radice della catena, l'epoca di riferimento dei sigilli d'autenticità, le epoche e le porte. **Il telefono non se ne fida**: lo verifica con [KeyCustody] (K8, K9), e i numeri che contano (radice, riferimento) li ricalcola dalla catena.
  - `val started: Boolean get()` — La cifratura è partita? Prima della prima epoca il server risponde con `epoch = 0`.
  - `fun door(doorId: String): DoorInfo?`
  - `fun toJson(): JsonObject`
  - `companion object`
    - `fun parse(json: JsonObject): KeysView` — Legge la risposta del server; un campo che manca o è del tipo sbagliato è [CustodyTamperedException].
- `data class EpochUpload(val epoch: Int, val kind: EpochKind, val link: String?, val seals: List<Pair<String, String>>, val tags: List<Pair<String, String>> = emptyList())` — Il corpo di `POST /v1/keys/epochs`: un'epoca nuova, il suo collegamento, un sigillo per ogni porta, e i tag nuovi dopo un furto.
  - `fun toJson(): JsonObject`
- `data class ResetUpload(val epoch: Int, val doors: List<DoorInfo>)` — Il corpo di `POST /v1/keys/reset`, «Ricomincia» (`b\chiavi.md` § 5): la conferma scritta, l'epoca nuova, le porte nuove.
  - `fun toJson(): JsonObject`

## `engine-account-host/account/host/custody/PampaHpke.kt`

- `class X25519KeyPair private constructor(private val private: X25519PrivateKeyParameters)` — Una coppia X25519. La privata sono 32 byte qualunque: X25519 li «schiaccia» da sé (RFC 7748 § 5), così un seme di HKDF diventa direttamente una privata, come nel telefono finto del server (`x25519FromSeed`).
  - `val publicKey: ByteArray` — La pubblica, 32 byte: va sul server (porte) o nel QR (passaggio).
  - `companion object`
    - `fun fromSeed(seed: ByteArray): X25519KeyPair` — La coppia che nasce da [seed] (32 byte).
    - `fun random(): X25519KeyPair` — Una coppia nuova e casuale (l'effimera del passaggio fra telefoni).
- `object PampaHpke` — HPKE «base» (RFC 9180) con DHKEM(X25519, HKDF-SHA256), HKDF-SHA256 e AES-256-GCM: la suite del contratto (`e2e.json`, `hpke`: kem 0x0020, kdf 0x0001, aead 0x0002), quella di Bouncy Castle. Serve alle porte della chiave dei dati e al passaggio fra telefoni (`b\chiavi.md` § 3 e § 4.2). La modalità «auth» non serve più (critica S10).
  - `const val ENC_BYTES`
  - `fun seal(recipient: ByteArray, info: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray` — Sigilla [plaintext] per la pubblica [recipient], con [info] e [aad]. Ogni sigillo ha un'effimera nuova.
  - `fun open(recipient: X25519KeyPair, info: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray` — Apre un sigillo per [recipient]; [PampaCryptoException] se non è per questa chiave o è stato toccato.

## `engine-account-host/account/host/custody/RecoveryCode.kt`

- `class RecoveryCode private constructor(private val bytes: ByteArray)` — Il codice di recupero (`b\chiavi.md` § 3.1; critica S1). Lo genera **sempre lo Store**, mai il server: 128 bit casuali, scritti in base32 (RFC 4648, maiuscole, senza riempimento) in gruppi da cinque, 26 caratteri: `ABCDE-FGHIJ-KLMNO-PQRST-UVWXY-Z`. È lo stesso formato del telefono finto del server.
  - `val display: String` — Come lo vede l'utente, una volta sola: 26 caratteri in gruppi da cinque.
  - `val auth: String get()` — `auth = HKDF(codice, "pampa/v1/recovery-auth")`, in base64url: 43 caratteri, l'unica cosa che parte.
  - `companion object`
    - `const val BYTES`
    - `const val LENGTH`
    - `fun generate(): RecoveryCode` — Un codice nuovo, dal generatore del telefono.
    - `fun fromBytes(bytes: ByteArray): RecoveryCode` — Per le prove e per chi ha già i 16 byte (il gestore delle password).
    - `fun parse(text: String): RecoveryCode?` — Il codice scritto dall'utente, o `null` se non è un codice. Perdona quello che si sbaglia scrivendo a mano: minuscole, spazi e trattini in qualunque punto, `0` per `O`, `1` per `I`, `8` per `B` (base32 non li usa). Gli ultimi due bit del ventiseiesimo carattere devono essere zero: un errore lì non è un codice.

## `engine-account-host/account/host/custody/StorePrf.kt`

- `object StorePrf` — Il PRF della passkey lo decide lo Store, non il server (`b\chiavi.md` § 3.2; critica S9). Il lavoro lo fa `engine-auth` (F10.2), con l'ingresso fisso dell'Account Pampa ([FluidPrfInput.PampaV1], `SHA-256("pampa/v1/prf")`, lo stesso di `PampaLabels.prfInput()`):
  - `val input: FluidPrfInput get()` — L'ingresso del PRF dello Store, all'accesso e alla creazione.
  - `fun assertionRequest(optionsJson: String): String` — Le opzioni di accesso per Credential Manager, col PRF dello Store al posto di quello del server.
  - `fun creationRequest(optionsJson: String): String` — Le opzioni di creazione per Credential Manager, col PRF dello Store (chiesto già alla creazione).
  - `fun door(result: FluidPasskeyResult): DoorSecret` — La porta della passkey appena usata: vedi [DoorSecret.fromPasskey].
  - `fun leaksPrfOutput(json: String): Boolean` — Vero se [json] porta ancora un'uscita del PRF, in un punto qualunque: una richiesta così non parte.

## `engine-account-host/account/host/custody/Transfer.kt`

- `class TransferOffer private constructor(val transferId: String, private var pair: X25519KeyPair?, private var s: ByteArray?)` — Il passaggio fra telefoni, dal lato del telefono **nuovo** (`b\chiavi.md` § 4.2; critica S2): una coppia X25519 effimera `(n, N)` e un segreto `s` di 128 bit, che passano solo dallo schermo alla fotocamera. Al server va solo la richiesta di un numero di passaggio, a corpo vuoto (`POST /v1/keys/transfers`): nessuna chiave.
  - `val publicKey: ByteArray` — La pubblica `N`, 32 byte.
  - `val qr: String` — Il testo del QR da mostrare: «Inquadra questo codice con un dispositivo dove sei già dentro».
  - `fun forget()` — Cancella `n` e `s`: un passaggio vale una volta.
  - `companion object`
    - `fun create(transferId: String): TransferOffer` — Il numero viene dal server; la chiave e il segreto nascono qui e non lo raggiungono mai.
- `class TransferQr(val transferId: String, val n: ByteArray, val s: ByteArray)` — Quello che il telefono **vecchio** legge dal QR. Con [parse] un testo che non è un QR di passaggio dà `null`.
  - `fun encode(): String`
  - `companion object`
    - `fun parse(text: String): TransferQr?`
- `data class TransferSeal(val sealed: String, val mac: String)` — Il sigillo del passaggio e il suo `mac` col segreto del QR, come li carica il vecchio e li ritira il nuovo.

## `engine-account-host/account/host/server/AccountServer.kt`

- `class ServerRequest(val method: String, val path: String, val headers: Map<String, String> = emptyMap(), val body: ByteArray? = null, val contentType: String? = null)` — Una richiesta verso il server dell'account, già pronta: niente segreti nell'indirizzo (critica S14).
- `class ServerResponse(val status: Int, val body: ByteArray, val serverTimeMillis: Long?)` — La risposta: lo stato HTTP, il corpo grezzo, e l'ora del server se l'ha detta (`Date`).
  - `val json: JsonObject? by lazy`
- `fun interface AccountHttp` — Il trasporto HTTP verso `account.pampa-apps.com`, sostituibile nelle prove. Di serie `HttpURLConnection`, come `EngineHttp` di engine-net: niente librerie in più nello Store. Un errore di rete è `IOException`, che la sessione traduce in «offline», non in «errore» (`b\architettura.md` § 3.5).
  - `suspend fun execute(baseUrl: String, request: ServerRequest): ServerResponse`
- `object UrlConnectionHttp : AccountHttp` — Il trasporto di serie. Niente cache, niente redirect seguiti, tempi brevi: il server o risponde o è offline.
- `class AccountServerException(val status: Int, val code: String, override val message: String, val reason: String? = null, val retryAfterSeconds: Long? = null, val body: JsonObject? = null) : Exception(message)` — Un errore del server, col codice del contratto (`errors.json`) e la frase già pronta per l'utente. [reason] è il dettaglio che alcune risposte portano (per esempio il motivo di `session_revoked`, o `lost_rotation_required`).
  - `val isSessionRevoked: Boolean get()` — Un dispositivo fatto uscire: con `remote`, `lost` o `all` si cancellano anche le copie (`b\chiavi.md` § 4.4).
  - `val isWaiting: Boolean get()` — Azione distruttiva messa in attesa di sicurezza (Q8): la risposta è 202 con `executeAt`.
- `object AccountHeaders` — Le intestazioni del contratto (`routes.json`, `headers`).
  - `const val STORE_VERSION`
  - `const val DEVICE_PROOF`
  - `const val IDEMPOTENCY_KEY`
  - `const val BACKUP_EPOCH`
- `class AccountServerClient(private val baseUrl: String, private val storeVersion: String, private val http: AccountHttp = UrlConnectionHttp)` — Il client del server dell'account per lo Store: compone le richieste, aggiunge le intestazioni del contratto e traduce le risposte d'errore in [AccountServerException]. Non tiene stato: i token li dà chi chiama.
  - `suspend fun call(method: String, path: String, json: JsonObject? = null, token: String? = null, headers: Map<String, String> = emptyMap()): JsonObject`
  - `suspend fun upload(method: String, path: String, bytes: ByteArray, token: String, headers: Map<String, String> = emptyMap()): JsonObject`
  - `suspend fun download(path: String, token: String): ByteArray`
  - `fun serverNow(): Long` — L'ora del server stimata, in millisecondi.

## `engine-account-host/account/host/server/DeviceKey.kt`

- `interface DeviceKey` — La chiave del dispositivo (`b\architettura.md` § 3.2; `b\api.md` § 3.3): EC P-256, solo per firmare la prova del rinnovo, non esportabile. Senza di lei il token di rinnovo, anche rubato da una copia, non rinnova niente. Con la raccomandazione A di Q7 è in classe «consegna»: il rinnovo funziona anche col telefono in tasca.
  - `val publicJwk: JsonObject` — La pubblica come JWK (`{ kty: "EC", crv: "P-256", x, y }`), da mandare all'accesso nel campo `device.deviceKey`.
  - `fun sign(data: ByteArray): ByteArray` — Firma [data] con ES256; la firma è `r || s`, 64 byte, come vuole JWS (RFC 7518 § 3.4).
  - `fun destroy()` — Cancella la chiave (uscita).
  - `companion object`
    - `fun jwkOf(key: ECPublicKey): JsonObject` — La JWK di una pubblica P-256.
    - `fun derToJose(der: ByteArray): ByteArray` — Da una firma DER di `SHA256withECDSA` a `r || s` di 64 byte.
- `class AndroidKeystoreDeviceKey(private val context: Context, private val alias: String = "dev.pampa.account.device", private val strongBox: Boolean = false, private val choice: KeyClassPolicy.Choice = KeyClassPolicy.CHOICE, private val attestationChallenge: ByteArray? = null) : DeviceKey` — La chiave del dispositivo nel Keystore di Android: P-256, `SHA256withECDSA`, StrongBox se c'è e se chiesto, e il vincolo «solo a telefono sbloccato» se la classe di [CustodyKeyKind.DEVICE_KEY] è madre (Q7 C) e da Android 15. Nessuna richiesta d'impronta: il rinnovo gira anche in sottofondo (architettura § 3.2).
  - `var strongBoxBacked: Boolean` — La chiave è finita davvero in StrongBox? Per la prova sul tablet.
- `object DeviceProof` — La prova del dispositivo per il rinnovo (`b\api.md` § 3.3), un JWS compatto ES256: `{ typ: "pampa-proof+jwt", alg: "ES256" }` · `{ htm, htu, iat, jti, rth }`, con `rth = base64url(SHA-256(token di rinnovo))`. Il token di rinnovo non entra mai nella prova: solo il suo hash. `iat` è l'ora del server (± 5 minuti).
  - `fun create(key: DeviceKey, method: String, url: String, refreshToken: String, serverNowMillis: Long): String`

## `engine-account-host/account/host/server/SessionStore.kt`

- `enum class SessionKind(val wire: String)` — Di che tipo è la sessione: normale, o nata dal codice di recupero (S-rec, `b\api.md` § 2).
  - `companion object`
    - `fun of(wire: String?): SessionKind`
- `class StoredSession(val accountId: String, val deviceId: String, internal val refreshToken: String, val kind: SessionKind, val displayName: String, val storePackage: String)` — La sessione dello Store su questo telefono. Il token di rinnovo sta solo qui, mai in un log, mai nel backup.
- `class SessionStore(private val vault: CustodyVault, private val dir: File, private val choice: KeyClassPolicy.Choice = KeyClassPolicy.CHOICE)` — Il file della sessione (`b\architettura.md` § 3.1): chiuso con la cassaforte nella classe di [CustodyKeyKind.SESSION] («consegna» con la raccomandazione A di Q7, così il rinnovo gira in tasca), dentro [dir] che è `noBackupFilesDir`: **escluso dal backup di Android**. Copiato su un altro telefono non servirebbe comunque, perché la chiave del Keystore non passa.
  - `fun save(session: StoredSession)`
  - `fun load(): StoredSession?` — La sessione, o `null` se nessuno è entrato. Una chiave del Keystore persa vuol dire rientrare (con la passkey è un tocco).
  - `fun markRemoteExit(at: Long)` — Quando è avvenuta l'ultima uscita da remoto: le app la chiedono per confermare `wiped` (`bpi.md` § 5.4). Sta in un file a parte, in chiaro (non è un segreto), così resta anche se lo Store si riavvia prima che l'app chieda.
  - `fun remoteExitAt(): Long`
  - `fun clearRemoteExit()` — Una nuova entrata chiude la storia dell'uscita di prima: un avviso `wiped` vecchio non cancella più niente.
  - `fun clear()`
- `class TokenCache(private val elapsedMillis: () -> Long = { SystemClock.elapsedRealtime() }, private val marginMillis: Long = 60_000)` — I token d'accesso in memoria (`b\architettura.md` § 2.3; R4): uno per app, pubblico e ambiti, per 15 minuti. Mai su disco. Le scadenze si contano con l'orologio monotono ([elapsedMillis]), non con la data del telefono, che l'utente può spostare (§ 3.5). Un token si rinnova [marginMillis] prima della scadenza.
  - `class Entry(val token: String, val expiresAtElapsed: Long)` — Un token con la sua scadenza sull'orologio monotono.
  - `fun get(packageName: String, audience: String, scopes: Set<String>): Entry?`
  - `fun put(packageName: String, audience: String, scopes: Set<String>, token: String, expiresInSeconds: Long): Entry` — Mette un token che vale [expiresInSeconds] da adesso (la durata dal server, non la sua data).
  - `fun secondsLeft(entry: Entry): Long` — Quanti secondi vale ancora [entry]: è l'`expiresIn` che si dà all'app.
  - `fun forget(packageName: String)` — Via i token di un'app scollegata.
  - `fun clear()` — Uscita: via tutto.

## `engine-account-host/account/host/sync/AccountSync.kt`

- `class AccountSyncRelay(private val client: AccountServerClient, private val token: suspend () -> String, private val collection: String, private val currentEpoch: () -> Int) : SyncRelay` — F10.8 · Il relay di `engine-sync` sopra le righe cifrate del server (`b\api.md` § 4.10; F10.3, `src\routes\sync.ts`): `GET` e `POST /v1/sync/{raccolta}` col token di un'app. Il server vede solo byte sigillati, versioni e numeri d'ordine. Lo usa lo Store per i luoghi (`places`, ambito `sync.places`); è lo stesso contratto che useranno le app per i loro dati in F10.9.
- `class PlacesSealer(private val accountId: String, private val storePackage: String, private val keys: () -> AppKeySet?) : SyncSealer` — F10.8 · Il sigillo dei luoghi fra i dispositivi: AES-256-GCM con `K_com(places)` dell'epoca, la busta `{v,e,alg,n,c}` e i dati associati del contratto (`accountId|pacchetto dello Store|places|idRiga|versione`, `e2e.json` `recordAad`). Il server non può spostare una riga su un'altra, né servirla come una versione diversa (K4).
- `sealed interface StoreSyncResult` — Com'è andata una sincronizzazione dello Store (luoghi o preferenze).
  - `data class Ok(val changed: Boolean, val pushed: Int, val pulled: Int, val rejected: List<String> = emptyList()) : StoreSyncResult` — [changed]: qualcosa che le app vedono è cambiato (e lo Store le ha avvisate).
  - `data object SignedOut : StoreSyncResult`
  - `data object NoE2e : StoreSyncResult` — La cifratura non è partita, o le chiavi dei luoghi non sono state ancora ricavate: i luoghi restano sul telefono.
  - `data object Locked : StoreSyncResult` — Il telefono non è stato sbloccato dall'accensione: si riprova dopo.
  - `data object Offline : StoreSyncResult`
  - `data class Failed(val failure: SyncFailure) : StoreSyncResult`
- `class PlacesSync(private val places: PlacesStore, private val client: AccountServerClient, private val session: AccountSession, private val keyring: CustodyKeyring, private val storePackage: String)` — F10.8 · I luoghi comuni fra i dispositivi (`b\chiavi.md` § 8): lo spazio `places` di [PlacesStore] sopra [AccountSyncRelay], cifrato con `K_com(places)` ([PlacesSealer]). Il token è quello dello Store per `sync` con l'ambito `sync.places`, che il server dà solo allo Store (F10.3). Due modifiche dello stesso campo: vince la più recente, con l'orologio logico di `engine-sync`.
  - `suspend fun sync(): StoreSyncResult`
- `class AppearanceSync(private val client: AccountServerClient, private val session: AccountSession, private val store: AppearanceStore)` — F10.8 · Tema e preferenze fra i dispositivi (`b\architettura.md` § 5.2; `b\api.md` § 4.9; F10.3, `src\routes\choices.ts`): legge le novità (`GET /v1/prefs?since=`) e le fonde campo per campo con l'orologio logico ([AppearanceStore.merge]), poi manda le scelte fatte qui (`POST /v1/prefs`), solo delle chiavi che il server annuncia (critica M1).
  - `suspend fun sync(): StoreSyncResult`

## `engine-account-host/account/host/testing/MemoryCustodyVault.kt`

- `class MemoryCustodyVault : CustodyVault` — La cassaforte finta delle prove: fa quello che fa il Keystore per la custodia, compresi i casi che Robolectric non ha (critica F2): - [locked]: il telefono bloccato, la madre non si apre; - [beforeFirstUnlock]: appena acceso, non si apre niente; - [invalidate]: la chiave del Keystore persa (`b\chiavi.md` § 11, punto 7);
  - `val opened: MutableMap<KeyClass, Int>`
  - `val sealedCount: MutableMap<KeyClass, Int>`
  - `fun invalidate(keyClass: KeyClass? = null)` — Il Keystore ha perso le chiavi (tutte, o solo quella di [keyClass]).

## `engine-account-host/account/host/testing/SoftwareDeviceKey.kt`

- `class SoftwareDeviceKey : DeviceKey` — La chiave del dispositivo delle prove, in memoria: P-256 del JDK al posto del Keystore. Mai in un'app.
  - `val publicKey: ECPublicKey get()`
