# API · engine-vault

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-vault/vault/AndroidVaultKeystore.kt`

- `class AndroidVaultKeystore(context: Context? = null) : VaultKeystore` — Il Keystore di Android per la cassaforte: le chiavi nascono nel chip separato (StrongBox) quando c'è, altrimenti nell'ambiente protetto del processore, e non escono mai.
  - `companion object`

## `engine-vault/vault/FluidKeystoreKey.kt`

- `class FluidKeystoreKey(val alias: String, private val keystore: VaultKeystore, private val spec: VaultKeySpec = VaultKeySpec())` — Una chiave AES-256-GCM del Keystore usata **direttamente**: ogni cifratura entra nel Keystore e la chiave non è mai nella memoria dell'app. È la forma più semplice della cassaforte, per pochi segreti piccoli (una chiave di un servizio, un token): ogni operazione costa un viaggio al Keystore, millisecondi nel TEE e decine con StrongBox. Per molti valori, file o un DataStore serve una sezione di [FluidVault].
  - `val exists: Boolean get()` — Vero se la chiave c'è già nel Keystore.
  - `fun seal(plain: ByteArray, associated: ByteArray = ByteArray(0)): ByteArray` — Cifra [plain]: nonce(12) ‖ testo cifrato ‖ tag(16). Crea la chiave se manca.
  - `fun open(sealed: ByteArray, associated: ByteArray = ByteArray(0)): ByteArray` — Decifra quello che [seal] ha cifrato.
  - `fun delete()` — Toglie la chiave dal Keystore: quello che ha cifrato non si riapre più.
  - `companion object`
    - `fun android(alias: String, context: Context? = null, spec: VaultKeySpec = VaultKeySpec()): FluidKeystoreKey` — La chiave [alias] nel Keystore di Android. [context] serve solo a leggere il blocco schermo; può mancare.

## `engine-vault/vault/FluidVault.kt`

- `sealed interface VaultAccess` — Chi può aprire una sezione della cassaforte. Si sceglie quando la sezione nasce e non cambia più.
  - `data class Device(val whileUnlocked: Boolean = false) : VaultAccess` — **Il solo telefono.** La sezione si apre senza chiedere niente, ma solo su questo telefono: la sua chiave sta nel Keystore e non esce, e i file stanno fuori dal backup. È la classe «fino al primo sblocco» della Data Protection di Apple, quella di serie su iOS.
  - `data class Biometric(val relockAfter: Duration = VaultDefaults.RelockAfter, val reserve: VaultReserve = VaultReserve.DeviceCredential) : VaultAccess` — **L'impronta.** La sezione si apre solo dopo l'impronta o il volto forte, e si richiude da sola dopo [relockAfter] senza usarla, e quando il telefono si blocca. È il Portachiavi con `kSecAccessControlBiometryCurrentSet`: un'impronta aggiunta chiude la chiave per sempre, e la sezione si riapre con la [reserve] (con la frase pronta di [VaultLabels.biometricsChanged]), che rifà la chiave da sola.
- `enum class VaultReserve` — La seconda porta di una sezione all'impronta: quella che resta quando l'impronta non va.
- `object VaultDefaults` — I numeri della cassaforte.
  - `val RelockAfter: Duration` — Dopo quanto una sezione all'impronta si richiude senza usarla: 5 minuti. Apple scrive che aprire una nota bloccata apre tutte le note bloccate «per qualche minuto» (support.apple.com, «Lock your notes on iPhone», iOS 27, letto il 24/11/2026): il numero esatto non lo dice. **A occhio**, 24/11/2026.
  - `const val ReserveWindowSeconds: Int` — Per quanti secondi la chiave di riserva lavora dopo il codice del telefono: 30. Il tempo di aprire la sezione e, dopo un'impronta aggiunta, di rifarne la chiave. **A occhio**, 24/11/2026.
  - `const val SecretIterations: Int` — I giri di PBKDF2-HMAC-SHA256 per il segreto dell'app: 210 000. È il numero di OWASP del 2023 per PBKDF2-HMAC-SHA512 (per SHA-256 ne chiede 600 000): basta, perché il risultato è chiuso anche da una chiave del Keystore e un attacco fuori dal telefono non lo vede. Circa 0,2–0,5 s su un telefono di fascia media, una volta per apertura.
- `enum class VaultAuthMethod` — Come si chiede l'autenticazione: l'impronta (col `CryptoObject`) o il codice del telefono.
- `class VaultAuthRequest(val method: VaultAuthMethod, val cipher: Cipher?, val title: String, val subtitle: String? = null, val fallback: String? = null)` — Una richiesta di autenticazione della cassaforte all'interfaccia.
- `sealed interface VaultAuthResult` — Com'è andata un'autenticazione.
  - `data object Success : VaultAuthResult` — Riuscita: il `CryptoObject` è sbloccato, o il codice è stato scritto.
  - `data object Fallback : VaultAuthResult` — La persona ha toccato il tasto della riserva (il codice del telefono, il PIN dell'app).
  - `data object Cancelled : VaultAuthResult` — Annullata (indietro, tocco fuori, «Annulla»).
  - `data class Failed(val message: String) : VaultAuthResult` — Non riuscita, con la frase del sistema (troppi tentativi, sensore occupato).
  - `data class Unavailable(val reason: VaultUnavailability) : VaultAuthResult` — Questo modo non c'è sul telefono (nessuna impronta, nessun blocco schermo).
- `fun interface VaultAuthenticator` — Chi mostra il dialogo dell'impronta o del codice per la cassaforte. La cassaforte non ha interfaccia: questo lo dà l'app, o `engine-security` con il dialogo di sistema già pronto (`FluidSystemAuthenticator`).
  - `suspend fun authenticate(request: VaultAuthRequest): VaultAuthResult` — Mostra il dialogo e aspetta l'esito. Si chiama sul thread principale, anche se `unlock` arriva da un altro filo.
- `data class VaultPrompt(val title: String, val subtitle: String? = null)` — Le parole del dialogo per aprire una sezione: il titolo («Sblocca le note») e una riga sotto.
- `sealed interface VaultUnlock` — Com'è andata l'apertura di una sezione.
  - `data class Opened(val recovered: VaultInvalidation? = null) : VaultUnlock` — Aperta. [recovered] non nullo: aperta con la riserva dopo che la chiave dell'impronta si era chiusa per quel motivo; la chiave nuova è già fatta, e la prossima volta basta l'impronta.
  - `data object Cancelled : VaultUnlock` — La persona ha annullato.
  - `data class NeedsSecret(val invalidation: VaultInvalidation?) : VaultUnlock` — La riserva è il segreto dell'app: va chiesto (il PIN dell'app) e passato a [VaultSection.unlockWithSecret].
  - `data object WrongSecret : VaultUnlock` — Il segreto dell'app non è quello giusto.
  - `data class Lost(val reason: VaultInvalidation) : VaultUnlock` — La sezione non si apre più ([reason]) e non c'è riserva: si ricomincia con [VaultSection.reset].
  - `data class Unavailable(val reason: VaultUnavailability) : VaultUnlock` — La protezione chiesta non c'è su questo telefono.
  - `data class Failed(val message: String) : VaultUnlock` — Non riuscita, con la frase del sistema.
- `sealed interface VaultSectionState` — Lo stato di una sezione, da osservare.
  - `data object Closed : VaultSectionState` — Chiusa: per leggerla serve [VaultSection.unlock]. Una sezione «solo telefono» si apre da sola al primo uso.
  - `data class Open(val relocksAt: Long?) : VaultSectionState` — Aperta; [relocksAt] è quando si richiude da sola (in `SystemClock.elapsedRealtime`), nullo se non si richiude.
  - `data class Lost(val reason: VaultInvalidation) : VaultSectionState` — Non si apre più ([reason]) e non ha riserva.
- `class FluidVault internal constructor(val name: String, internal val dir: File, internal val keystore: VaultKeystore, val labels: VaultLabels, internal val clock: () -> Long, internal val scope: CoroutineScope, internal val io: CoroutineDispatcher, internal val secretIterations: Int)` — **La cassaforte** dell'app (F8.23): valori, file e DataStore cifrati con chiavi del Keystore (StrongBox quando c'è), divisi in sezioni. Ogni sezione ha la sua chiave dei dati, chiusa da una chiave del Keystore, e un accesso: [VaultAccess.Device] (il solo telefono) o [VaultAccess.Biometric] (l'impronta, che si richiude da sola).
  - `fun section(name: String = DefaultSection, access: VaultAccess = VaultAccess.Device()): VaultSection` — La sezione [name], con l'accesso [access]. Sempre la stessa per lo stesso nome. Una sezione nata con un accesso non ne cambia: chiederla con un altro è un errore (per cambiarlo, [VaultSection.reset] e una sezione nuova).
  - `val openedSections: List<VaultSection> get()` — Le sezioni chieste finora in questo processo.
  - `fun lockAll()` — Chiude subito tutte le sezioni (quelle «solo telefono» si riapriranno da sole al prossimo uso).
  - `companion object`
    - `const val DefaultSection: String` — Il nome della sezione e della cassaforte di serie.
    - `fun get(context: Context, name: String = DefaultSection, labels: VaultLabels = VaultLabels()): FluidVault` — La cassaforte [name] di questa app, una per processo. Al primo uso si mette in ascolto dello schermo che si spegne (per chiudere le sezioni all'impronta), con un ricevitore non esportato.
    - `fun create(dir: File, keystore: VaultKeystore, name: String = DefaultSection, labels: VaultLabels = VaultLabels(), clock: () -> Long = SystemClock::elapsedRealtime, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.De…, io: CoroutineDispatcher = Dispatchers.IO, secretIterations: Int = VaultDefaults.SecretIterations): FluidVault` — Una cassaforte con le parti scelte a mano: per i test ([MemoryVaultKeystore], un orologio finto, lo scope del test) e per chi la vuole in una cartella sua. La cartella deve stare fuori dal backup.

## `engine-vault/vault/MemoryVaultKeystore.kt`

- `class MemoryVaultKeystore(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) : VaultKeystore` — Un Keystore in memoria, **per i test e per il computer**: mai in un'app vera (le chiavi stanno in chiaro nella memoria del processo e muoiono con lui).
  - `var deviceSecure: Boolean` — Vero se il telefono finto ha un blocco schermo.
  - `var biometricsEnrolled: Boolean` — Vero se il telefono finto ha almeno un'impronta registrata.
  - `var strongBoxAvailable: Boolean` — Vero se il telefono finto ha StrongBox: lo dice solo [VaultKeyInfo.strongBox].
  - `var operations: Int` — Quante operazioni sono state concluse: per i test che contano gli usi del Keystore.
  - `val aliases: Set<String> get()` — Le chiavi che ci sono adesso.
  - `fun authenticate()` — Un'autenticazione dell'utente, adesso: apre le chiavi a finestra ([VaultKeyAuth.Recent]).
  - `fun approve(cipher: Cipher)` — Il dialogo di sistema è riuscito col `CryptoObject` [cipher]: l'operazione all'impronta si può concludere.
  - `fun enrollBiometric()` — Un'impronta aggiunta: le chiavi [VaultKeyAuth.BiometricEachUse] non valgono più.
  - `fun removeScreenLock()` — Il blocco schermo tolto: tutte le chiavi con autenticazione non valgono più.
  - `fun wipe()` — Il Keystore azzerato: nessuna chiave resta.

## `engine-vault/vault/VaultDataStore.kt`

- `fun <T> VaultSection.dataStore(fileName: String, serializer: Serializer<T>, corruptionHandler: ReplaceFileCorruptionHandler<T>? = null, migrations: List<DataMigration<T>> = emptyList(), scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())): DataStore<T>` — Un **DataStore intero cifrato** in una sezione «solo telefono»: lo stesso `DataStore<T>` di Jetpack, con lo stesso `Serializer<T>` dell'app, ma il file sul disco è cifrato tutto con la chiave della sezione (AES-256-GCM, una chiave derivata per file). Il file sta fuori dal backup, nella cartella della sezione.
- `fun VaultSection.preferencesDataStore(fileName: String, corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null, migrations: List<DataMigration<Preferences>> = emptyList(), scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())): DataStore<Preferences>` — Le **Preferences** di DataStore, cifrate in una sezione «solo telefono»: le stesse chiavi (`stringPreferencesKey`…) e lo stesso `edit { }`, con il file cifrato. Vedi [dataStore].

## `engine-vault/vault/VaultFile.kt`

- `class VaultFile internal constructor(private val section: VaultSection, val name: String)` — Un file cifrato di una sezione: un documento, una foto, un referto. Si scrive e si legge **a pezzi** da 64 KiB, come lo «streaming AEAD» di Tink (AES-GCM-HKDF a segmenti, lo schema STREAM di Hoang, Reyhanitabar, Rogaway e Vizár, 2015): un file grande non entra mai tutto in memoria, e un pezzo tolto, spostato o aggiunto si vede.
  - `val file: File get()` — Dove sta il file cifrato sul disco (fuori dal backup).
  - `val exists: Boolean get()` — Vero se il file c'è.
  - `suspend fun write(bytes: ByteArray)` — Scrive [bytes] nel file, al posto di quello che c'era.
  - `suspend fun write(block: (OutputStream) -> Unit)` — Scrive il file a pezzi: [block] riceve un `OutputStream` che cifra mentre scrive. Il file nuovo prende il posto del vecchio solo quando [block] finisce senza errori, dopo essere stato messo sul disco: anche dopo uno spegnimento brusco resta il file di prima o quello nuovo intero. Due scritture dello stesso file vanno in fila.
  - `suspend fun importFrom(input: InputStream)` — Copia [input] nel file, cifrandolo a pezzi (per importare un file grande senza tenerlo in memoria).
  - `suspend fun read(): ByteArray` — Tutto il file, decifrato. Per un file grande, [read] con il blocco.
  - `suspend fun <T> read(block: (InputStream) -> T): T` — Legge il file a pezzi: [block] riceve un `InputStream` che decifra mentre legge, e che vale solo dentro il blocco. Un pezzo toccato o mancante fa lanciare [VaultCorruptStreamException] alla lettura.
  - `suspend fun delete()` — Toglie il file.
- `class VaultCorruptStreamException(message: String, cause: Throwable? = null) : IOException(message, cause)` — Un file cifrato che non torna: un pezzo toccato, tolto o aggiunto, o la chiave di un'altra sezione.

## `engine-vault/vault/VaultKeystore.kt`

- `sealed interface VaultKeyAuth` — Che cosa chiede una chiave del Keystore prima di lavorare.
  - `data object None : VaultKeyAuth` — Nessuna autenticazione: la chiave lavora finché il telefono è acceso e l'app gira.
  - `data object BiometricEachUse : VaultKeyAuth` — Ogni operazione chiede l'impronta forte, con un `CryptoObject`. Invalidata da un'impronta nuova.
  - `data class Recent(val seconds: Int) : VaultKeyAuth` — La chiave lavora per [seconds] secondi dopo un'autenticazione forte o col codice del telefono.
- `data class VaultKeySpec(val auth: VaultKeyAuth = VaultKeyAuth.None, val strongBox: Boolean = true, val unlockedDeviceRequired: Boolean = false)` — Come nasce una chiave del Keystore.
- `data class VaultKeyInfo(val alias: String, val auth: VaultKeyAuth, val strongBox: Boolean?)` — Quello che si sa di una chiave appena nata.
- `interface VaultCipherOp` — Un'operazione di cifratura pronta: il [cipher] da passare al dialogo di sistema quando la chiave chiede l'impronta ([VaultKeyAuth.BiometricEachUse], il `CryptoObject` di `BiometricPrompt`), e [finish] che la conclude.
  - `val cipher: Cipher` — Il `Cipher` inizializzato: va dato a `BiometricPrompt.CryptoObject`, mai usato a mano.
  - `val iv: ByteArray` — Il nonce scelto dal Keystore per una cifratura AES-GCM; vuoto per le altre operazioni.
  - `fun associate(data: ByteArray)` — I dati associati (AES-GCM): autenticati ma non cifrati. Prima di [finish].
  - `fun finish(input: ByteArray): ByteArray` — Conclude l'operazione su [input].
- `interface VaultKeystore` — Il Keystore visto dalla cassaforte: crea, trova e usa le chiavi per nome.
  - `val isDeviceSecure: Boolean` — Vero se il telefono ha un blocco schermo sicuro (PIN, sequenza, password).
  - `fun contains(alias: String): Boolean` — Vero se c'è una chiave con questo nome.
  - `fun delete(alias: String)` — Toglie la chiave. Nessun errore se non c'era.
  - `fun createSecretKey(alias: String, spec: VaultKeySpec): VaultKeyInfo` — Crea una chiave AES-256-GCM.
  - `fun createKeyPair(alias: String, spec: VaultKeySpec): VaultKeyInfo` — Crea una coppia RSA-2048 per OAEP (SHA-256, MGF1 SHA-1): la privata resta nel Keystore.
  - `fun publicKey(alias: String): PublicKey?` — La chiave pubblica della coppia [alias], fuori dal Keystore; null se la coppia non c'è.
  - `fun aesEncrypt(alias: String): VaultCipherOp` — Una cifratura AES-GCM con la chiave [alias]. Il nonce lo sceglie il Keystore.
  - `fun aesDecrypt(alias: String, iv: ByteArray): VaultCipherOp` — Una decifratura AES-GCM con la chiave [alias] e il nonce [iv]. Stesse eccezioni di [aesEncrypt].
  - `fun rsaDecrypt(alias: String): VaultCipherOp` — Una decifratura RSA-OAEP con la privata della coppia [alias]. Stesse eccezioni di [aesEncrypt].
- `enum class VaultInvalidation` — Perché una chiave non vale più.
- `enum class VaultUnavailability` — Perché una sezione non si può proteggere come chiesto, su questo telefono.
- `sealed class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)` — Gli errori della cassaforte. Ognuno ha la sua frase pronta in [VaultLabels].
  - `class Locked(val section: String) : VaultException("la sezione «$section» è chiusa")` — La sezione è chiusa: va aperta con `unlock` prima di leggere o scrivere.
  - `class Invalidated(val reason: VaultInvalidation, cause: Throwable? = null) :` — La chiave non vale più ([reason]): i dati si riaprono solo con la riserva, se c'è.
  - `class NeedsAuthentication(cause: Throwable? = null) : VaultException("serve un'autenticazione", cause)` — La chiave chiede un'autenticazione recente (l'impronta o il codice) che non c'è stata.
  - `class Corrupt(message: String = "dato cifrato non valido", cause: Throwable? = null) : VaultException(message, cause)` — Il dato non torna: toccato, troncato, o chiuso con un'altra chiave.
  - `class Unavailable(val reason: VaultUnavailability, cause: Throwable? = null) :` — La protezione chiesta non c'è su questo telefono ([reason]).

## `engine-vault/vault/VaultLabels.kt`

- `data class VaultLabels(val unlockTitle: String = "Sblocca", val useDeviceCredential: String = "Usa il codice del telefono", val useAppSecret: String = "Usa il PIN dell'app", val biometricsChanged: String = "Hai cambiato le impronte o il volto del telefo…, poi basterà di nuovo l'impronta.", val biometricsChangedSecret: String = "Hai cambiato le impronte o il volto del telefo…, poi basterà di nuovo l'impronta.", val biometricsChangedLost: String = "Hai cambiato le impronte o il volto del telefo…, val screenLockRemoved: String = "Il blocco schermo è stato tolto, e con lui le chiavi di questa sezione: i suoi dati " + "non si possono più aprire. Si ricomincia da una sezione vuota.", val screenLockRemovedSecret: String = "Il blocco schermo è stato tolto: apri questa s…, val keyLost: String = "La chiave di questa sezione non c'è più su que…, val noScreenLock: String = "Per proteggere questa sezione imposta un blocc…, val noBiometrics: String = "Per usare l'impronta registrane una nelle impo…, val keystoreError: String = "Il telefono non riesce a usare le sue chiavi i…, val wrongSecret: String = "PIN sbagliato.", val noSecret: String = "Questa sezione non ha un PIN dell'app.", val authenticationFailed: String = "Non è stato possibile verificare che sei tu. R…, val locked: String = "Questa sezione è chiusa.", val corrupt: String = "Questo dato è danneggiato e non si può aprire.")` — Le frasi pronte della cassaforte: i dialoghi e i casi che la persona deve capire senza sapere cos'è un Keystore. Italiano di serie, [English] per un'app in inglese; un'app le cambia con `copy`.
  - `fun reason(reason: VaultInvalidation, reserve: VaultReserve): String` — La frase di un'invalidazione [reason], secondo la riserva della sezione.
  - `fun unavailable(reason: VaultUnavailability): String` — La frase di un motivo per cui la protezione non c'è.
  - `fun message(unlock: VaultUnlock, reserve: VaultReserve): String?` — La frase da mostrare per l'esito [unlock] di una sezione con la riserva [reserve]; null quando non c'è niente da dire (aperta, annullata).
  - `companion object`
    - `val English: VaultLabels` — Le frasi in inglese.

## `engine-vault/vault/VaultSection.kt`

- `class VaultSection internal constructor(private val vault: FluidVault, val name: String, val access: VaultAccess)` — Una sezione della cassaforte: una chiave dei dati sua (256 bit casuali), chiusa da una chiave del Keystore, e tutto quello che ci sta dentro: [values], i file ([file]), i DataStore ([dataStore], [preferencesDataStore]) e i byte sigillati a mano ([seal], [open]).
  - `val labels: VaultLabels get()` — Le frasi pronte della sua cassaforte.
  - `val state: StateFlow<VaultSectionState>` — Lo stato della sezione: chiusa, aperta (fino a quando), persa.
  - `val isOpen: Boolean get()` — Vero se la chiave dei dati è in memoria adesso.
  - `val values: VaultValues by lazy` — I valori della sezione: testi e byte per nome, cifrati in un file solo.
  - `fun file(fileName: String): VaultFile` — Il file cifrato [fileName] della sezione (lettere, cifre, `.`, `-`, `_`).
  - `suspend fun unlock(authenticator: VaultAuthenticator, prompt: VaultPrompt = VaultPrompt(vault.labels.unlockTitle)): VaultUnlock` — Apre la sezione. Per [VaultAccess.Device] non chiede niente (e non serve chiamarla: il primo uso la apre). Per [VaultAccess.Biometric] mostra il dialogo dell'impronta con [authenticator]; se l'impronta non va, o la persona sceglie la riserva, passa alla riserva: - il codice del telefono: un secondo dialogo, e se l'impronta era cambiata la chiave si rifà da sola;
  - `suspend fun unlockWithSecret(secret: CharArray): VaultUnlock` — Apre la sezione col segreto dell'app ([VaultReserve.AppSecret]): il PIN dell'app, una frase. Se l'impronta era cambiata, la sua chiave si rifà. Il segreto non si tiene: chi lo passa lo azzera dopo.
  - `suspend fun setReserveSecret(secret: CharArray)` — Dà alla sezione (aperta) il segreto dell'app come riserva ([VaultReserve.AppSecret]), o lo cambia. Non tiene [secret]: chi lo passa lo azzera dopo.
  - `fun lock()` — Chiude subito la sezione: la chiave dei dati esce dalla memoria. Una sezione «solo telefono» si riaprirà da sola.
  - `suspend fun reset()` — Butta la sezione: le sue chiavi nel Keystore, la sua scheda e tutti i suoi dati. È la strada dopo [VaultUnlock.Lost]: si ricomincia da una sezione vuota.
  - `suspend fun seal(plain: ByteArray, associated: ByteArray = ByteArray(0)): ByteArray` — Sigilla [plain] con la chiave della sezione: la scatola che torna si apre solo su questo telefono, con questa sezione aperta (e, se è all'impronta, dopo l'impronta). Per il materiale che l'app genera e deve rileggere: una chiave simmetrica derivata, una radice privata.
  - `suspend fun open(sealed: ByteArray, associated: ByteArray = ByteArray(0)): ByteArray` — Apre una scatola di [seal]. Chi riceve i byte li azzera quando ha finito.

## `engine-vault/vault/VaultValues.kt`

- `class VaultValues internal constructor(private val section: VaultSection)` — I valori di una sezione: testi e byte per nome, tutti in un file cifrato solo (`values.fv`), scritto tutto o niente. Pensati per poche decine di voci piccole: password, token, chiavi. Per un documento serve un [VaultFile].
  - `suspend fun getBytes(key: String): ByteArray?` — Il valore [key] in byte, o null se non c'è. Chi lo riceve lo azzera quando ha finito.
  - `suspend fun putBytes(key: String, value: ByteArray?)` — Scrive [value] sotto [key]; null lo toglie.
  - `suspend fun getString(key: String): String?` — Il testo [key], o null se non c'è.
  - `suspend fun putString(key: String, value: String?)` — Scrive il testo [value] sotto [key]; null lo toglie.
  - `suspend fun remove(key: String)` — Toglie [key].
  - `suspend fun keys(): Set<String>` — I nomi dei valori che ci sono.
  - `suspend fun contains(key: String): Boolean` — Vero se c'è il valore [key].
  - `suspend fun clear()` — Toglie tutti i valori della sezione.
  - `val changed: Flow<Long>` — Un segnale a ogni scrittura: un'interfaccia lo osserva e rilegge quello che le serve. Non porta i valori, così un segreto non resta in un `Flow`.
  - `fun observeString(key: String): Flow<String?>` — Il testo [key] ogni volta che cambia, letto quando serve. Una sezione chiusa fa fallire il `Flow`.
