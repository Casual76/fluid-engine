<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/auth.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-auth

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-auth/auth/AuthResult.kt`

- `sealed interface AuthResult<out T>` — L'esito di una chiamata a Credential Manager, con l'errore già tradotto in una frase che si può mostrare.
  - `data class Ok<T>(val value: T) : AuthResult<T>` — È andata.
  - `data object Cancelled : AuthResult<Nothing>` — L'utente ha chiuso il foglio di sistema (o il gestore ha risposto `NotAllowedError`).
  - `data object NoCredential : AuthResult<Nothing>` — Nessuna credenziale da usare per questa richiesta, su questo telefono.
  - `data class Unsupported(val why: Why) : AuthResult<Nothing>` — Il telefono non può farlo: versione di Android, nessun gestore, nessun blocco schermo.
  - `data class Failed(val message: String, val cause: Throwable?, val retry: Boolean = false) : AuthResult<Nothing>` — Tutto il resto. [message] è la frase per l'utente, già scelta da [FluidAuthLabels]; [cause] è l'eccezione di Credential Manager (o del JSON), per i log; [retry] è vero quando riprovare subito ha senso (un'interruzione).
  - `enum class Why` — Perché il telefono non può. I tre casi della scheda `passkey`. Un caso nuovo non entrerà qui come valore: un motivo che non è uno di questi arriva come [Failed].
- `fun AuthResult<*>.phrase(labels: FluidAuthLabels = FluidAuthLabels(), password: Boolean = false): String?` — La frase da mostrare per questo esito, o `null` se è andata ([AuthResult.Ok]). Per [AuthResult.Failed] è il suo messaggio, già scelto quando è nato; per gli altri casi viene da [labels]. [password] è vero per gli esiti di [FluidPasswords]: cambia solo la frase di [AuthResult.NoCredential] («nel gestore delle password…»).
- `fun <T> AuthResult<T>.valueOrNull(): T?` — Il valore se è andata, altrimenti `null`.

## `engine-auth/auth/FluidAuthErrors.kt`

- `object FluidAuthErrors` — Le eccezioni di Credential Manager tradotte in [AuthResult] (B § 9.2). Una tabella sola per la creazione e una per l'accesso, così le frasi sono le stesse in ogni chiamata, anche in `engine-auth-google` e in un'app che chiama Credential Manager da sé.
  - `fun fromCreate(e: CreateCredentialException, labels: FluidAuthLabels): AuthResult<Nothing>` — L'esito di un'eccezione della creazione (passkey o password).
  - `fun fromGet(e: GetCredentialException, labels: FluidAuthLabels): AuthResult<Nothing>` — L'esito di un'eccezione dell'accesso (passkey, password, o l'accesso con Google di `engine-auth-google`).

## `engine-auth/auth/FluidAuthLabels.kt`

- `data class FluidAuthLabels(val cancelled: String = "Operazione annullata.", val noCredential: String = "Su questo telefono non trovo una passkey da us…, val noPassword: String = "Nel gestore delle password non trovo niente da…, val oldAndroid: String = "Le passkey funzionano da Android 9 in poi. Su…, val noProvider: String = "Non trovo un gestore di passkey e password. Pu…, val noScreenLock: String = "Per usare una passkey serve un blocco schermo.…, poi riprova.", val wrongDomain: String = "Il servizio non riconosce ancora quest'app. Ri…, val alreadyExists: String = "Su questo telefono c'è già una passkey per que…, val interrupted: String = "Si è interrotto tutto a metà. Riprova.", val badRequest: String = "La richiesta non è valida. Riprova più tardi.", val generic: String = "Qualcosa non è andato. Riprova.")` — Le frasi di [AuthResult]: quelle che vede l'utente quando una passkey o una password non va.
  - `companion object`
    - `fun english(): FluidAuthLabels` — Le stesse frasi in inglese. Un'app in un'altra lingua parte da questa con `copy(...)` o le scrive tutte.

## `engine-auth/auth/FluidAuthenticatorData.kt`

- `class FluidAuthenticatorData private constructor(private val rpIdHashBytes: ByteArray, val flags: Int, val signCount: Long, private val aaguidBytes: ByteArray?, private val credentialIdBytes: ByteArray?)` — I dati dell'autenticatore di una risposta WebAuthn: l'hash dell'RP ID, i flag, il contatore e, alla creazione, l'AAGUID del gestore ([WebAuthn § 6.1](https://www.w3.org/TR/webauthn-3/#sctn-authenticator-data), fiducia alta).
  - `val userPresent: Boolean get()` — UP: l'utente era presente.
  - `val userVerified: Boolean get()` — UV: l'utente si è fatto riconoscere (impronta, volto, PIN).
  - `val backupEligible: Boolean get()` — BE: la passkey può essere sincronizzata fra dispositivi. Non cambia mai per la stessa passkey.
  - `val backedUp: Boolean get()` — BS: la passkey è sincronizzata adesso. Può cambiare nel tempo.
  - `val hasAttestedCredential: Boolean get()` — AT: ci sono i dati della credenziale (solo alla creazione).
  - `val hasExtensions: Boolean get()` — ED: ci sono estensioni firmate dall'autenticatore.
  - `val aaguid: String? get()` — L'AAGUID del gestore, nella forma `ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4`, solo alla creazione. Con `attestation: "none"` un gestore può darlo tutto a zero: allora [aaguidIsZero] è vero.
  - `val aaguidIsZero: Boolean get()` — L'AAGUID è tutto a zero (il gestore non lo dichiara).
  - `val credentialId: String? get()` — L'id della credenziale scritto nei dati della creazione, in base64url.
  - `fun rpIdHash(): ByteArray` — L'hash SHA-256 dell'RP ID (una copia).
  - `fun isFor(rpId: String): Boolean` — Vero se questi dati sono per l'RP ID dato.
  - `companion object`
    - `fun parse(bytes: ByteArray): FluidAuthenticatorData` — Legge i byte dei dati dell'autenticatore. Lancia [FluidWebAuthnJsonException] se non sono validi.
    - `fun fromAttestationObject(bytes: ByteArray): FluidAuthenticatorData` — Legge i dati dell'autenticatore dentro un `attestationObject` (CBOR, campo `authData`).

## `engine-auth/auth/FluidPasskeyResult.kt`

- `class FluidPasskeyResult internal constructor(val credentialId: String, val responseJson: String, val authenticatorData: FluidAuthenticatorData, val prfEnabled: Boolean?, private val prfFirst: ByteArray?, val transports: List<String>, val userHandle: String?, val origin: String?)` — Una passkey appena creata o appena usata, già divisa fra quello che va al server e quello che resta sul telefono.
  - `val hasPrfOutput: Boolean get()` — C'è l'uscita del PRF.
  - `fun prfOutput(): ByteArray?` — L'uscita del PRF per l'ingresso dato (una copia), o `null`. Resta sul telefono.

## `engine-auth/auth/FluidPasskeys.kt`

- `object FluidPasskeys` — Le passkey col foglio di sistema di Android (Credential Manager), senza rete: JSON WebAuthn in entrata, risposta divisa fra server e telefono in uscita ([FluidPasskeyResult]). È la scheda `passkey` dell'Account Pampa (B § 9.2).
  - `const val MIN_SDK: Int` — Android 9: da qui Credential Manager gestisce le passkey ([passkeys](https://developer.android.com/identity/passkeys), fiducia alta).
  - `fun unsupportedReason(context: Context): AuthResult.Why?` — Perché il telefono non può usare le passkey, o `null` se può provarci. Non sa se c'è un gestore: quello lo dice solo Credential Manager, quando lo si chiama.
  - `fun isSupported(context: Context): Boolean` — Android 9 o successivo e un blocco schermo. Il gestore si scopre chiamando.
  - `suspend fun create(activity: Activity, requestJson: String, prf: FluidPrfInput? = FluidPrfInput.PampaV1, evaluatePrfAtCreation: Boolean = true, labels: FluidAuthLabels = FluidAuthLabels()): AuthResult<FluidPasskeyResult>` — Crea una passkey. [requestJson] è l'oggetto `options` di WebAuthn che manda il server (`challenge`, `rp`, `user`, `pubKeyCredParams`…). [prf] è l'ingresso del PRF, scritto dal modulo; `null` non lo chiede. [evaluatePrfAtCreation] chiede il valore già alla creazione; falso chiede solo se c'è (`prf: {}`).
  - `suspend fun get(activity: Activity, requestJson: String, prf: FluidPrfInput? = FluidPrfInput.PampaV1, onlyOnThisPhone: Boolean = false, labels: FluidAuthLabels = FluidAuthLabels()): AuthResult<FluidPasskeyResult>` — Entra con una passkey. [requestJson] è l'oggetto `options` di accesso (`challenge`, `rpId`, `allowCredentials`…). [prf] è l'ingresso del PRF, scritto dal modulo al posto di quello del server; `null` non lo chiede. [onlyOnThisPhone] non propone passkey di altri dispositivi (il QR): senza una passkey qui risponde subito [AuthResult.NoCredential] senza mostrare niente, ed è il modo di provare all'avvio.

## `engine-auth/auth/FluidPasswords.kt`

- `class FluidSavedPassword internal constructor(val id: String, private val value: String)` — Una password (o un codice) letta dal gestore. [toString] non stampa il segreto.
  - `fun secret(): String` — Il segreto. Resta sul telefono.
- `object FluidPasswords` — Un segreto nel gestore delle password del telefono (Gestore password di Google, Samsung Pass…), con lo stesso foglio di sistema delle passkey.
  - `suspend fun save(activity: Activity, id: String, secret: String, labels: FluidAuthLabels = FluidAuthLabels()): AuthResult<Unit>` — Salva [secret] col nome [id]. Il gestore mostra il suo foglio e chiede conferma.
  - `suspend fun get(activity: Activity, ids: Set<String> = emptySet(), labels: FluidAuthLabels = FluidAuthLabels()): AuthResult<FluidSavedPassword>` — Rilegge un segreto salvato. Con [ids] il gestore propone solo quelli con quei nomi. Senza niente da proporre risponde [AuthResult.NoCredential]: la frase è `phrase(password = true)`.

## `engine-auth/auth/FluidPrfInput.kt`

- `class FluidPrfInput private constructor(private val value: ByteArray)` — L'ingresso dell'estensione PRF di WebAuthn: il «sale» per cui la passkey restituisce 32 byte segreti e sempre uguali ([WebAuthn, prf](https://www.w3.org/TR/webauthn-3/#prf-extension), fiducia alta).
  - `fun bytes(): ByteArray` — I byte dell'ingresso (una copia).
  - `val base64Url: String get()` — L'ingresso in base64url senza «=», come va nel JSON (`extensions.prf.eval.first`).
  - `companion object`
    - `const val PAMPA_V1_LABEL: String` — La costante dell'Account Pampa, `chiavi.md` § 3.2.
    - `val PampaV1: FluidPrfInput` — `SHA-256("pampa/v1/prf")`: l'ingresso di Pampa Store, all'accesso e alla creazione.
    - `fun fromLabel(label: String): FluidPrfInput` — L'ingresso `SHA-256(label)`, per un servizio che non è l'Account Pampa (una copia dell'engine, regola R8).
    - `fun ofBytes(bytes: ByteArray): FluidPrfInput` — Un ingresso già fatto: da 1 a 64 byte.

## `engine-auth/auth/FluidWebAuthnJson.kt`

- `class FluidWebAuthnJsonException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)` — Un JSON WebAuthn che non si legge, o che manca di un campo obbligatorio. Non contiene mai i valori segreti.
- `object FluidWebAuthnJson` — Le conversioni JSON di WebAuthn fra il server e Credential Manager, senza Android: si provano sulla JVM.
  - `fun creationRequest(optionsJson: String, prf: FluidPrfInput?, evaluatePrfAtCreation: Boolean = true): String` — Le opzioni di creazione per Credential Manager. [optionsJson] è l'oggetto `options` della risposta del server (`challenge`, `rp`, `user`, `pubKeyCredParams`…). Con [prf] il modulo scrive `extensions.prf`: con [evaluatePrfAtCreation] chiede già il valore (`eval.first`), altrimenti chiede solo se c'è (`prf: {}`). Senza [prf] non chiede il PRF, ma toglie comunque quello del server.
  - `fun assertionRequest(optionsJson: String, prf: FluidPrfInput?): String` — Le opzioni di accesso per Credential Manager, con l'estensione PRF del modulo al posto di quella del server.
  - `fun readCreation(responseJson: String): FluidPasskeyResult` — Legge la risposta di Credential Manager a una creazione (`registrationResponseJson`).
  - `fun readAssertion(responseJson: String): FluidPasskeyResult` — Legge la risposta di Credential Manager a un accesso (`authenticationResponseJson`).
  - `fun hasPrfOutput(anyJson: String): Boolean` — Vero se un JSON contiene ancora un'uscita del PRF (`prf.results`, o il vecchio `hmacGetSecret`) in un punto qualunque. Per un banco che guarda ogni richiesta in uscita (`chiavi.md` § 11, punto 9).
