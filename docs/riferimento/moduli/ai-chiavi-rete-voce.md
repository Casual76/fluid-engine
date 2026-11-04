<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/ai-chiavi-rete-voce.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# AI: chiavi, impostazioni, rete e voce

Tre pezzi di `engine-ai` che stanno **sotto** i provider e **accanto** all'orchestratore: `keys/` (le chiavi dell'utente cifrate col Keystore, le impostazioni dell'assistente, la verifica di una chiave e il catalogo dei modelli su disco),
`net/` (il client HTTP con lo streaming SSE, la traduzione di ogni guasto in un `AiError`, gli helper JSON) e `speech/` (la cattura del microfono con rilevamento del silenzio e la trascrizione con failover).
Chi legge l'orchestratore ([ai-orchestrator.md](ai-orchestrator.md)) e i provider ([ai-provider.md](ai-provider.md)) trova qui da dove arrivano le chiavi, perche' un errore ha il tipo che ha, e come una frase parlata diventa una domanda.
Firme complete: [`../api/ai-chiavi-rete-voce.md`](../api/ai-chiavi-rete-voce.md). Guida generale: [`../ai.md`](../ai.md). La UI delle chiavi e delle impostazioni e' in [ai-ui.md](ai-ui.md).

Modello mentale in due frasi. **Una chiave e' un segreto che non esce mai da un `Flow`**: si salva cifrata, si legge nel momento in cui serve (`AiKeyStore.key`), e prima di valere deve essere *verificata* con una chiamata vera. **Un errore e' un tipo, non un messaggio**:
`AiErrorMapper` riduce ogni risposta dei tre provider ai pochi `AiError` su cui l'orchestratore decide qualcosa (aspettare, cambiare servizio, riprovare, dirlo all'utente).

```
 utente incolla la chiave
        |
  AiKeyStore.set(provider, key)        cifra (KeystoreCipher) -> DataStore `ai_keys`; la verifica si azzera
        |
  AiKeyVerifier.verify(provider)       GET /models vera -> ModelCatalogStore (disco+memoria) -> markVerified
        |                              -> reconcile: i tre livelli (router, chat, profondo) seguono il catalogo
        v
  AiKeyStore.states / anyVerified      "c'e' una chiave che funziona" -> si accende l'assistente
        |
  ProviderFactory.ordered(CHAT | STT)  solo provider con chiave VERIFICATA, nell'ordine di AiSettingsStore
        |
  AiHttp (net)  <->  provider          errori -> AiErrorMapper -> AiError
        ^
  SpeechCapture -> WAV -> Transcriber  (voce: stesso failover, ordine STT)
```

## Scegliere in fretta

| ho bisogno di... | uso |
|---|---|
| salvare / togliere la chiave di un provider | `AiKeyStore.set(provider, key)` (`null` o vuota = togliere) |
| sapere se c'e' una chiave che funziona (accendere l'assistente) | `AiKeyStore.anyVerified` |
| verificare una chiave appena incollata | `AiKeyVerifier.verify(provider)` -> `VerifyResult` |
| accendere l'assistente dopo la **prima** chiave verificata, senza pestare una scelta dell'utente | `AiSettingsStore.enableIfNeverChosen()` |
| dare all'app i **suoi** valori di partenza (azioni accese, `deny` a OpenRouter...) | `AiSettingsStore(context, AiSettingsDefaults(...))` |
| un file di preferenze scelto da me | `AiSettingsStore.open(file, defaults)` / `AiKeyStore.open(file, cipher)` / `AiDataStores.open(file)` |
| un'app che aveva gia' le chiavi sotto un altro alias del Keystore | `KeystoreCipher(alias = "...")` passato a `AiKeyStore` |
| mostrare i modelli subito e rinfrescarli una volta al giorno | `ModelCatalogStore.catalogues` + `AiKeyVerifier.refreshIfStale(provider)` |
| riparare i telefoni con un modello salvato che non esiste piu' | niente: `AiKeyVerifier.reconcile` (anche dal rinfresco) |
| segnare un modello sparito scoperto a meta' domanda | `AskInput.onModelUnavailable = { p, m -> scope.launch { verifier.markUnavailable(p, m) } }` |
| una chiamata HTTP verso un provider (nuovo, di prova) | `AiHttp.postJson` / `postJsonStream` / `getJson` / `postMultipart` |
| una frase per un guasto (UI, diagnostica) | il **tipo** di `AiError` + `AiError.httpCode` + `AiError.providerMessage` |
| registrare la voce con il rilevamento del silenzio | `SpeechCapture(AndroidPcmSource(context)).record(file)` |
| trascrivere con riserva fra provider | `Transcriber { factory.ordered(ProviderFactory.Kind.STT) }` |
| un microfono finto nei test | implementare `PcmSource` (il copione di `SpeechCaptureTest`) |
| una cifratura trasparente nei test (niente Keystore sul computer) | implementare `SecretCipher` |

Non cosi': non salvare la chiave in chiaro in nessun `SharedPreferences`/`DataStore` proprio, non metterla in un `StateFlow`, non loggarla (`AiErrorMapper.shorten` toglie le forme note di chiave da ogni testo del provider, ma solo da quelli).

## `AiKeyStore`

**Cos'e'.** Le chiavi dei provider, **cifrate** col Keystore e in un DataStore tutto loro (`ai_keys`: l'export dei dati dell'app puo' escluderlo e basta). Per ogni provider salva il blob cifrato e **quando e' stata verificata** l'ultima volta.

**Firma essenziale.**

```kotlin
class AiKeyStore(store: DataStore<Preferences>, cipher: SecretCipher) {
  constructor(context: Context, cipher: SecretCipher = KeystoreCipher())     // file `ai_keys`, con la corruzione gestita
  val states: Flow<Map<ProviderId, KeyState>>      // KeyState(present, verifiedAtMillis); .verified = present && verifiedAtMillis != null
  val anyVerified: Flow<Boolean>
  suspend fun currentStates(): Map<ProviderId, KeyState>
  suspend fun key(provider: ProviderId): String?     // in chiaro, adesso
  suspend fun set(provider: ProviderId, key: String?)
  suspend fun markVerified(provider: ProviderId, atMillis: Long)
  companion object { fun open(file: File, cipher: SecretCipher = KeystoreCipher(), scope: CoroutineScope = ...): AiKeyStore }
}
```

**Uso.**

```kotlin
val keys = AiKeyStore(context)                       // o AiKeyStore(context, KeystoreCipher(alias = "miaapp.ai")) se le chiavi stanno gia' sotto quell'alias
keys.set(ProviderId.GROQ, pasted)                    // la chiave nuova parte NON verificata
val result = verifier.verify(ProviderId.GROQ)        // dopo: markVerified
keys.anyVerified.collect { enabled -> showAssistantButton(enabled) }
```

**Comportamento.**
- `states` espone solo **presenza e data**: la chiave in chiaro non esce mai da un `Flow`. `key(provider)` la legge e la decifra al momento.
- Se il Keystore **non riesce piu' a decifrare** (telefono ripristinato, chiave invalidata) `key` ritorna `null` **e cancella la voce**: l'utente vede «reinserisci la chiave», meglio di un 401 senza spiegazione.
- `set` con una chiave **azzera la verifica** (`markVerified` solo se la chiave c'e'); con `null`/vuota/spazi toglie chiave e verifica. Il testo si `trim`-a.
- Un file DataStore **corrotto** si butta (`AiDataStores.corruptionHandler`, dalla 3.0.0): le chiavi vanno reinserite, ma l'app riparte invece di fallire ogni lettura e scrittura per sempre.

**Trappole.**
- **Risalvare la stessa chiave la fa uscire da `ProviderFactory.ordered`** finche' non si riverifica: una UI «Salva» deve sempre chiamare `AiKeyVerifier.verify` subito dopo.
- **L'alias del Keystore.** `KeystoreCipher()` usa `KeystoreCipher.DEFAULT_ALIAS` (`fluidengine.ai`). Un'app che aveva gia' le chiavi sotto un altro alias (FluidWeather: `fluidweather.ai`) lo passa al costruttore, o le chiavi salvate **non si decifrano piu'** e l'utente le vede «da reinserire».
- Un solo `DataStore` per file e per processo: due istanze sullo stesso file fanno cadere l'app. Il costruttore a `Context` e `open(file, ...)` aprono il **loro** file; chi apre `ai_keys` da se' non deve usare anche quelli.
- `AiKeyStore.set(provider, key)` salvando la **prima** chiave di un provider esplodeva (visto sul telefono, 1.0.3: `remove` su una chiave primitiva mai scritta); oggi si usa `-=`. Chi scrive uno store proprio sullo stesso schema ricordi la lezione.

## `SecretCipher` e `KeystoreCipher`

**Cos'e'.** `SecretCipher` (`encrypt(plain): String`, `decrypt(blob): String?`) e' l'interfaccia che permette ai test di sostituire il Keystore (che sul computer non c'e') con una cifratura trasparente. `KeystoreCipher` e' l'implementazione vera: **AES-256-GCM** con la chiave dentro l'Android Keystore (cio' che
`EncryptedSharedPreferences` faceva prima di essere deprecata: security-crypto 1.1.0-alpha07, aprile 2025). Ottanta righe, nessuna dipendenza, stesso livello di protezione.

**Comportamento.** Formato del blob: `v1:` + base64(`iv[12]` || testo cifrato || tag). L'IV lo sceglie il Keystore a ogni cifratura (`setRandomizedEncryptionRequired`): due salvataggi della stessa chiave non si assomigliano. `decrypt` ritorna `null` (non lancia) per prefisso mancante, blob troppo corto, tag non valido, chiave invalidata, chiave irrecuperabile.

**Trappole.** `decrypt` che ritorna `null` e' un **segnale** (la chiave non si legge piu'), non un errore: `AiKeyStore.key` lo traduce nella cancellazione della voce. Un `SecretCipher` di test che cifra «a mano» (`plain:...`) non deve mai arrivare in produzione.

## `AiSettingsStore`, `AiSettings`, `AiSettingsDefaults`, `AiDefaults`

**Cos'e'.** Le preferenze dell'assistente in un file DataStore `ai`: interruttore, ordine dei provider per la chat e per la voce, **modello per livello** (router, chat, profondo, trascrizione), modelli di riserva di OpenRouter, politica dati, pensiero, lettura ad alta voce delle risposte, azioni, consenso, quando si sono rinfrescati i cataloghi.
Niente di segreto qui (le chiavi stanno in `AiKeyStore`): l'export dei dati puo' includerlo. `AiSettings` e' la lettura **gia' risolta coi default**: chi la legge non ragiona sui `null`.

**Firma essenziale.**

```kotlin
class AiSettingsStore(store: DataStore<Preferences>, defaults: AiSettingsDefaults = AiSettingsDefaults()) {
  constructor(context: Context, defaults: AiSettingsDefaults = AiSettingsDefaults())     // file `ai`
  val settings: Flow<AiSettings>
  suspend fun current(): AiSettings
  suspend fun setEnabled(enabled: Boolean)
  suspend fun enableIfNeverChosen(): Boolean         // vero se l'ha acceso
  suspend fun setChatOrder(order: List<ProviderId>); suspend fun setSttOrder(order: List<ProviderId>)
  suspend fun setModel(provider: ProviderId, tier: ModelTier, model: String?)     // + setChatModel / setClassifierModel / setDeepModel / setSttModel
  suspend fun setOpenRouterFallbacks(models: List<String>); suspend fun setOpenRouterDataPolicy(policy: OpenRouterDataPolicy)
  suspend fun setThinking(level: ThinkingLevel); suspend fun setSpeakReplies(speak: Boolean); suspend fun setActionsEnabled(enabled: Boolean)
  suspend fun setConsentAccepted(atMillis: Long?); suspend fun markModelsRefreshed(provider: ProviderId, atMillis: Long)
}

data class AiSettingsDefaults(
  val enabled: Boolean = false, val actionsEnabled: Boolean = false, val speakReplies: Boolean = false,
  val thinking: ThinkingLevel = ThinkingLevel.MEDIUM,
  val openRouterDataPolicy: OpenRouterDataPolicy = OpenRouterDataPolicy.ACCOUNT,
  val chatOrder: List<ProviderId> = ProviderId.defaultOrder, val sttOrder: List<ProviderId> = ProviderId.defaultOrder,
)
```

**Uso.**

```kotlin
val settings = AiSettingsStore(context, AiSettingsDefaults(actionsEnabled = true, openRouterDataPolicy = OpenRouterDataPolicy.DENY))

// dopo la prima chiave verificata:
if (result is VerifyResult.Ok) settings.enableIfNeverChosen()

val s = settings.current()
val chat = s.chatModel(ProviderId.GEMINI)            // la scelta dell'utente o AiDefaults.GEMINI_CHAT
val deep = s.model(ProviderId.GEMINI, ModelTier.DEEP) // null = la chat fa anche da profondo
```

**Comportamento.**
- **Default vs scelta** (3.0.0): un valore **assente** dal file vale quello di `AiSettingsDefaults`, uno **scritto** vale quello che l'utente ha scelto, e il file **non si tocca** (leggere i default non scrive niente). Prima ogni app aveva i suoi valori («azioni accese: mostra subito, agisci con conferma»; «a OpenRouter sempre `deny`») e li imponeva riscrivendo il file con una migrazione.
- **«Mai scelto» non e' «spento»**: `AiSettings.enabledChosen` e' vero se l'utente ha mai toccato l'interruttore (`setEnabled(false)` e' una scelta come un'altra). `enableIfNeverChosen()` accende solo se non l'ha mai fatto: chi incollava la chiave vedeva «ok» e tornava all'app senza sapere che mancava l'interruttore; chi l'ha spento una volta lo ritrova spento.
- **I modelli**: `chatModel(provider)` = scelta oppure `AiDefaults.chatModel(provider)` (`null` solo per OpenRouter); `classifierModel` idem (default Groq e Gemini, `null` per OpenRouter); `deepModel` **non ha default** (`null` finche' non lo sceglie `AiKeyVerifier`/l'utente); `sttModel` ha sempre un default. `setModel(provider, tier, null)` **toglie** la scelta e rimette il default.
- Gli ordini: un ordine salvato che non nomina un provider lo accoda (`AiSettingsStore.parseOrder`), cosi' una versione con un quarto servizio non lo perde; anche l'ordine di default dell'app si completa e si deduplica.
- `setOpenRouterFallbacks` salva **al massimo due** modelli (separati da newline). `setConsentAccepted(atMillis)`: il consenso e' un **istante**, non un interruttore (`AiSettings.consentAccepted` = `consentAcceptedAtMillis != null`).
- Un file **corrotto** riparte da vuoto (3.0.0): le preferenze tornano ai default, le chiavi vanno reinserite.
- `ThinkingLevel` (`LOW`, `MEDIUM`, `HIGH`) e' cio' che l'orchestratore traduce in `ReasoningLevel` ([ai-orchestrator.md](ai-orchestrator.md)).

**`AiDefaults`.** Cio' con cui l'assistente parte prima che l'utente cambi qualcosa (valori in [ai-provider.md](ai-provider.md)): modelli di default per provider e livello, le liste di gratuiti di OpenRouter preferiti per chat, profondo e router, `AiDefaults.OPENROUTER_AVOID`/`avoided(model)`,
`AiDefaults.latestGemini(ids, family)`. Sono scelte di **prodotto**, riviste a ogni release: l'app non le copia.

**Trappole.**
- **Un file e' aperto una volta sola per processo**: niente delegato dell'app sullo stesso file. Chi vuole un handle proprio usa `AiDataStores.open(file)` e passa il `DataStore` al costruttore (il costruttore a `DataStore` non e' cambiato).
- `AiSettingsDefaults` vale solo per i campi che il file **non ha**: cambiare un default in una release non cambia l'utente che ha gia' scelto, ne' quello che aveva scelto il valore di serie (una scelta e' una scelta anche se uguale al default).
- `AiSettings.enabled` e' **risolto**: non distingue «spento perche' l'ha spento» da «spento perche' non ha mai scelto». Per quello `enabledChosen`.
- Il default del **router di Groq** (`AiDefaults.GROQ_CLASSIFIER`) non e' salvato: se il provider lo ritira, `reconcile` interviene solo allora (e scrive la scelta).

## `AiKeyVerifier` e `ModelCatalogStore`

**Cos'e'.** La verifica di una chiave **e' una chiamata vera** (`GET /models`): se risponde, la chiave si segna verificata, il catalogo si salva e i tre livelli si allineano. `ModelCatalogStore` tiene i cataloghi **su disco** (`<directory>/models-<provider>.json`) e **in memoria** (`catalogues: StateFlow`), cosi' la pagina delle impostazioni li mostra subito.
`AiKeyVerifier` li rinfresca alla verifica, alla richiesta e al massimo una volta al giorno.

**Firma essenziale.**

```kotlin
class AiKeyVerifier(keys: AiKeyStore, settings: AiSettingsStore, providers: ProviderFactory, catalogs: ModelCatalogStore, clock: () -> Long = System::currentTimeMillis) {
  val keyInfo: StateFlow<Map<ProviderId, OpenRouterKeyInfo>>      // l'ultimo GET /key di OpenRouter
  val unavailable: StateFlow<Map<ProviderId, Set<String>>>        // modelli segnati assenti, in memoria
  suspend fun verify(provider: ProviderId): VerifyResult
  suspend fun refreshIfStale(provider: ProviderId, force: Boolean = false): ModelCatalogue?
  suspend fun reconcile(provider: ProviderId, catalogue: ModelCatalogue)
  suspend fun markUnavailable(provider: ProviderId, model: String)
}

sealed interface VerifyResult { Ok(catalogue, keyInfo, chosenDefault, chosenIsFree); Invalid; Failed(error: AiError?) }
class ModelCatalogStore(directory: File) { val catalogues: StateFlow<...>; suspend fun load(p); suspend fun save(p, c) }
```

**Uso.**

```kotlin
val catalogs = ModelCatalogStore(File(context.cacheDir, "ai"))
val verifier = AiKeyVerifier(keys, settings, factory, catalogs)

when (val r = verifier.verify(ProviderId.OPENROUTER)) {
  is VerifyResult.Ok -> { settings.enableIfNeverChosen(); showModels(r.catalogue); if (!r.chosenIsFree) warnPaid(r.chosenDefault) }
  VerifyResult.Invalid -> showWrongKey()          // 401/403, o nessuna chiave
  is VerifyResult.Failed -> showTryAgain(r.error) // rete, 5xx, catalogo vuoto (error == null)
}
// all'avvio dell'app, in background:
ProviderId.entries.forEach { launch { verifier.refreshIfStale(it) } }
```

**Comportamento.**
- `verify`: `forVerification(provider)` (senza client, cioe' senza chiave -> `Invalid`); `listModels`; un catalogo **vuoto** (chat e voce) e' `Failed(null)`; poi salva il catalogo, `markModelsRefreshed`, `markVerified`; per OpenRouter anche `keyInfo()` (crediti, limite, `isFreeTier`: se fallisce non fa fallire la verifica); poi `reconcile`.
  `Unauthorized` -> `Invalid`, qualunque altro `AiError` -> `Failed(e)`, altro -> `Failed(null)`. **Una chiave `Invalid` non viene cancellata**: resta salvata, non verificata.
- `refreshIfStale`: **riallinea sempre** (`reconcile`) sul catalogo che ha in cache, anche se fresco: un telefono che ha verificato la chiave ieri non ripassa dalla rete, e senza questo una scelta finita su un modello da evitare ci resterebbe per sempre. Poi, se il catalogo ha piu' di un giorno (`AiKeyVerifier.DAY_MILLIS`) o `force`, lo riscarica (errore di rete -> si tiene la cache) e riallinea di nuovo.
- **`reconcile`: i tre livelli seguono il catalogo.** Una scelta vale **finche' esiste nel catalogo e non e' da evitare**; altrimenti si riassegna il default di quel livello.
  - **Chat**: OpenRouter -> i gratuiti preferiti con tool (`AiDefaults.OPENROUTER_CHAT_PREFERRED`), poi `OpenRouterCatalog.pickDefaultFree`, poi `AiDefaults.OPENROUTER_CHAT_FALLBACK` (a pagamento), poi il primo non da evitare. Gemini -> l'ultimo flash (`latestGemini(ids, "flash")`). Groq (dalla 2.9.0) -> il default di codice se c'e'; se non c'e' piu', il modello con tool non piccolo, che ragiona, col contesto piu' grande.
  - **Router**: OpenRouter -> il primo di `AiDefaults.OPENROUTER_CLASSIFIER_PREFERRED` presente; Gemini -> l'ultimo `flash-lite`; Groq -> il default se c'e', altrimenti il primo dei piccoli (nome con `instant`, `8b`, `mini`, `nano`, `lite`, `1b`, `3b`), poi `openai/gpt-oss-20b`; se non c'e' nessuno **il router lo fa la chat** (2.8.1: prima il default sparito restava e ogni domanda cominciava con un 404).
  - **Profondo**: `TierDefaults.pickDeep` ([ai-provider.md](ai-provider.md)). Una scelta **da evitare** se ne va anche senza sostituto (il livello ricade sulla chat); una scelta **solo sparita dal catalogo** resta finche' non c'e' un sostituto (i gratuiti di OpenRouter vanno e vengono).
  - I default di codice di Groq **non si salvano**: tornare al default vuol dire togliere la scelta, cosi' il default segue l'engine.
- `markUnavailable(provider, model)`: segna il modello come assente **in memoria** e riallinea subito sul catalogo salvato; il sostituto finisce nelle impostazioni come ogni altra scelta, e al riavvio resta. L'insieme non si persiste di proposito: un catalogo a volte elenca per un giorno un modello gia' ritirato, e un modello tornato disponibile non deve restare escluso per sempre.
- `ModelCatalogStore`: `load` risponde dalla memoria, poi dal file (che puo' essere assente o illeggibile -> `null`); `save` scrive memoria e file (`directory` si crea; un errore di scrittura non lancia). Il formato e' versionato (`"v": 1`): un file di una versione che non si capisce vale come assente.

**Trappole.**
- La verifica e' una **chiamata di rete costosa** sul tetto del provider: non va fatta a ogni apertura delle impostazioni, per quello c'e' `refreshIfStale`.
- Con `ProviderFactory(decorate = ...)` attivo i crediti di OpenRouter non si leggono (`keyInfo` vuoto): il tipo concreto e' nascosto dall'involucro.
- `VerifyResult.Ok.chosenDefault` e' valorizzato **solo per OpenRouter** (il modello scelto dall'euristica); gli altri provider hanno default di codice.
- Un catalogo che il provider cambia forma (campi nuovi, nomi nuovi) non rompe niente — si legge con navigazione `JsonElement` senza modelli — ma puo' cambiare cosa finisce nella lista chat: i filtri sono per sottostringa ([ai-provider.md](ai-provider.md)).

## `AiHttp`

**Cos'e'.** Il client HTTP dei provider: POST con corpo JSON, POST in streaming (SSE), POST multipart per l'audio, GET per i cataloghi. Su `HttpURLConnection` come `EngineHttp`: l'app non ha e non vuole una libreria HTTP; quello che serve in piu' (un corpo, un flusso di eventi) sta in un file.

**Firma essenziale.**

```kotlin
class AiHttp(userAgent: String, connectTimeoutMillis: Int = 10_000, readTimeoutMillis: Int = 60_000, streamChunkTimeoutMillis: Int = 45_000, io: CoroutineDispatcher = Dispatchers.IO) {
  suspend fun postJson(url: String, headers: Map<String, String>, body: JsonObject, readTimeoutMillis: Int? = null): AiResponse
  suspend fun getJson(url: String, headers: Map<String, String>): AiResponse
  suspend fun postMultipart(url: String, headers: Map<String, String>, fields: Map<String, String>, file: File, fileMime: String, fileName: String, fileFieldName: String = "file"): AiResponse
  fun postJsonStream(url: String, headers: Map<String, String>, body: JsonObject, chunkTimeoutMillis: Int? = null, onHeaders: (Map<String, String>) -> Unit = {}): Flow<String>
}
class AiResponse(val code: Int, val body: JsonElement?, val headers: Map<String, String>, val rateLimit: RateLimitInfo)
```

**Uso.**

```kotlin
val http = AiHttp(userAgent = "MiaApp/1.0 (Android)")
val r = http.getJson("https://api.example.com/v1/models", mapOf("Authorization" to "Bearer $key"))
http.postJsonStream(url, headers, body, chunkTimeoutMillis = 120_000, onHeaders = { h -> limits = AiErrorMapper.parseRateLimit(h) })
  .collect { payload -> handle(payload) }        // un evento SSE `data:` per emissione, gia' scorporato
```

**Comportamento.**
- **Streaming**: un `callbackFlow` con un thread di lettura. Una coroutine bloccata in `readLine()` non vede la cancellazione e una socket non si interrompe: l'unico modo affidabile di fermare una lettura e' `disconnect()` da un altro thread, che fa fallire la `readLine()`. `awaitClose` lo fa quando chi raccoglie se ne va (provato col server locale dei test).
  Si chiede **`Accept-Encoding: identity`** in stream: Android aggiunge gzip da solo e il gzip bufferizza i delta. Il flusso termina a `[DONE]` o alla chiusura del server; un ultimo evento non terminato si recupera (`SseParser.flush`).
- **Errori HTTP** (non 2xx) arrivano come `AiError` **prima** del primo evento; quelli di rete a meta' come chiusura del flusso con causa (`AiErrorMapper.wrap`: `SocketTimeoutException` -> `Timeout`; `UnknownHostException`/`ConnectException`/`SSLException`/altro `IOException` -> `Network`; la cancellazione passa intatta; un'altra eccezione resta com'e').
- **Timeout**: connessione 10 s; lettura 60 s senza stream; **45 s di silenzio fra due righe** in stream. Una richiesta puo' portare il suo (`readTimeoutMillis`/`chunkTimeoutMillis`, solo se > 0): la pazienza giusta dipende da cosa si chiede, e con un solo numero per tutti o si aspetta troppo un server morto o si uccide un pensiero a meta'.
- **Niente redirect** (`instanceFollowRedirects = false`): mai ri-inviare un corpo con `Authorization` dietro a un redirect. Un 3xx non e' 2xx: finisce in `AiError.Server`.
- `readResponse`: una risposta 2xx non JSON, o JSON che non e' un oggetto/array (un proxy, un captive portal che passa come letterale) e' `AiError.Parse`. Gli header si normalizzano in **minuscolo**.
- Il multipart si manda con `Content-Length` esatto (`setFixedLengthStreamingMode`) e il file **copiato a flusso**: l'audio non sta in memoria due volte.

**Trappole.**
- Il `userAgent` e' obbligatorio e va **dato dall'app**: i provider lo registrano.
- Un parser che si aspetta `AiResponse.body` non nullo si rompe su un 204 o un corpo vuoto (`body == null`).
- Non tenere un `Flow` di `postJsonStream` raccolto due volte: ogni raccolta apre una connessione.

## `AiError`, `RateLimitInfo`, `BadReason`, `AiErrorMapper`

**Cos'e'.** Gli errori di un provider **ridotti a quelli su cui l'orchestratore decide**: aspettare, passare a un altro provider, riprovare, o dirlo all'utente. Il messaggio grezzo resta per la diagnostica; la frase per l'utente la sceglie la UI in base al **tipo**.
`AiErrorMapper.map(code, headers, body)` e' puro: si prova sul computer con i corpi veri dei tre provider (fixture nei test).

**I tipi** (tutti `AiError`, una classe sigillata; ognuno con `httpCode` e `providerMessage` dalla 2.9.0).

| tipo | quando |
|---|---|
| `AiError.Unauthorized` | 401/403 (non di moderazione), o il 400 di Gemini «API key not valid» (`api key` / `api_key` nel messaggio): una chiave sbagliata non si maschera con un ripiego |
| `AiError.RateLimited(retryAfterSec, rateLimit, freeModelCap)` | 429; `freeModelCap` per il tetto giornaliero dei modelli gratuiti di OpenRouter (merita una frase sua) |
| `AiError.Server(code)` | 5xx, 408, e qualunque risposta non 2xx senza una categoria migliore |
| `AiError.BadRequest(code, reason)` | gli altri 4xx e i rifiuti dentro un 200; `reason` e' un `BadReason` |
| `AiError.Network`, `AiError.Timeout` | eccezioni di rete / di lettura |
| `AiError.Parse` | un 2xx che non si capisce: JSON rotto, struttura inattesa |

**`BadReason`** (2.9.0): `GENERIC`, `TOOL_USE_FAILED` (Groq: il modello ha scritto una tool call che il server non legge; **un'altra estrazione di solito va**), `MODEL_UNAVAILABLE` (ritirato, rinominato, senza endpoint per questa chiave), `CONTEXT_TOO_LONG` (non ci sta: contesto o tetto per richiesta di Groq), `BLOCKED` (contenuto bloccato).
Un 400 non e' una cosa sola: prima erano tutti «il servizio ha risposto con un errore» e la domanda finiva li'. La tabella di cosa fa ognuno e' in [ai-orchestrator.md](ai-orchestrator.md).

**Come si legge un 4xx** (`badReason`, in ordine): `tool_use_failed` (nel codice, tipo, stato o testo) -> `TOOL_USE_FAILED`; 413, `context_length_exceeded` o una delle frasi note («maximum context length», «prompt is too long», «request too large», ...) -> `CONTEXT_TOO_LONG`;
codice `model_decommissioned`/`model_not_found`, o 404 con «model» nel testo, o le frasi note («is not found for api version», «no endpoints found», «is not a valid model id», «has been decommissioned», ...) -> `MODEL_UNAVAILABLE`; altrimenti `GENERIC`.
Si guarda **tutto** perche' nessuno promette niente: `error.code` (Groq una parola, Gemini e OpenRouter un numero), `error.type`, `error.status` (Gemini), il messaggio, e `error.metadata.raw` di OpenRouter (l'errore del fornitore a valle).
Il 403 della **moderazione** di OpenRouter (`flagged`/`moderation`/`metadata.reasons`) e' `BLOCKED`, non una chiave sbagliata; **«No endpoints found matching your data policy» e' `MODEL_UNAVAILABLE`** (e' cio' che dava `OpenRouterDataPolicy.DENY` imposto sempre).

**`inBandError`.** Un errore arrivato **dentro un 200** (Groq manda cosi' il suo `tool_use_failed`, a stream gia' aperto; Gemini/OpenRouter un `error` nel corpo): quelli che si sanno leggere sono un `BadRequest` col loro `BadReason`, gli altri restano un `Server` (cioe' una riprova).

**`retry-after`** (`parseRetryAfter`), in ordine di fiducia: header `retry-after` (secondi o data HTTP RFC 1123); `RetryInfo.retryDelay` nei dettagli di Gemini («37s»); la frase di Groq «try again in 7.66s». `parseDuration` capisce `1h2m3.5s`, `250ms`, `2m59.56s`.
**`RateLimitInfo`** (`x-ratelimit-limit-requests`, `remaining-requests`, `limit-tokens`, `remaining-tokens`, `reset-*`): tutto opzionale, **Gemini non ne manda nessuno**; il reset di Groq e' una durata, quello di OpenRouter un timestamp in millisecondi (> 10^12) che diventa «secondi da adesso».

**`providerMessage` e `shorten`.** `AiErrorMapper.shorten` ripulisce un testo del provider: una riga sola, al massimo `AiErrorMapper.PROVIDER_MESSAGE_CHARS` (300) caratteri, **senza chiavi** (forme note: `AIza...`, `gsk_...`, `sk-...`, `Bearer ...`). Un messaggio d'errore non dovrebbe mai contenerne, ma qualche servizio ripete meta' della chiave sbagliata, e quel testo finisce in uno screenshot.
`providerDetails` aggiunge il codice o lo stato del provider davanti (`tool_use_failed: ...`) e l'errore a valle di OpenRouter fra parentesi.

**Trappole.**
- **Un `when` esaustivo dell'app su `AiError`/`BadReason`** va completato se ne arrivano di nuovi (la classe e' sigillata).
- `shorten` toglie le **forme note** di chiave: una chiave di un provider nuovo con un'altra forma passa. Non mettere mai in una UI il `message` grezzo di un `AiError`; `providerMessage` e' quello pulito.
- `BadRequest(200, ...)`: un `httpCode` 200 significa «l'errore e' arrivato dentro un flusso gia' aperto», non un successo.
- L'ordine `tool_use_failed` > contesto > modello sparito conta: un messaggio che nomina un parametro dello schema di un tool non e' un modello sparito.

## `SseParser`, `Json`, `MultipartBody`

- **`SseParser`**: parser di Server-Sent Events riga per riga, come dice la specifica. Le righe `data:` di un evento si accumulano (unite da newline), una riga vuota lo chiude (`feed` ritorna il payload), i commenti (`: OPENROUTER PROCESSING`, i keep-alive di Groq) e i campi `event:`/`id:`/`retry:` si ignorano, un solo spazio dopo i due punti si toglie, il `\r` finale non entra. Una istanza **per stream**; `flush()` recupera un ultimo evento non terminato.
- **`Json.kt`** (`JsonElement?.get(key)`, `at(index)`, `asArray()`, `double()`, `string()`): navigazione senza modelli, **ogni campo mancante e' un `null` che il chiamante decide come trattare**. I provider cambiano forma spesso e senza annunciarlo (un campo in piu', uno che diventa nullo, un dialetto diverso): con modelli tipizzati ogni scostamento sarebbe un crash.
  `string()` ritorna il contenuto di **qualunque** primitiva (anche un numero).
- **`MultipartBody.build(fields, fileFieldName, fileName, fileMime, boundary)`**: il corpo `multipart/form-data` a mano — campi e intestazione del file in un **preludio**, chiusura in un **epilogo**, file nel mezzo copiato a flusso — cosi' `Content-Length` e' esatto (`Parts.contentLength(fileBytes)`). Il boundary di serie e' `----FluidEngine<nanoTime>` (non porta il nome di nessuna app); un campo che contiene il boundary e' **rifiutato** (`require`).

## `SpeechCapture`, `PcmSource`, `AndroidPcmSource`, `WavWriter`

**Cos'e'.** La cattura vocale con **rilevamento del silenzio**, su `AudioRecord` (non `MediaRecorder`, che da' solo un picco senza campioni e non si puo' rifilare). PCM 16 kHz mono in frame da 20 ms: dagli stessi frame escono il livello per l'aureola e la decisione di fermarsi.
Il WAV si scrive **una volta alla fine**, gia' tagliato all'ultima parola piu' un margine. `PcmSource` e' l'**unico punto Android** della cattura (un microfono che da' campioni): la logica (VAD, taratura, durata) e' pura e si prova con un microfono finto.

**Firma essenziale.**

```kotlin
class SpeechCapture(source: PcmSource, config: VadConfig = VadConfig(), clock: () -> Long = System::currentTimeMillis) {
  fun record(target: File): Flow<Event>        // freddo: parte all'iscrizione
  fun stopNow()                                 // tocco: chiude la cattura al prossimo frame
  sealed interface Event { Level(level, speaking, elapsedMillis); SpeechStarted; Finished(file, durationMillis, reason); Empty(reason); Failed(cause) }
  enum class EndReason { SILENCE, MAX_DURATION, MANUAL }
  enum class EmptyReason { NOTHING_HEARD, TOO_SHORT }
}
interface PcmSource { fun start(sampleRate: Int): Boolean; fun read(frame: ShortArray): Int; fun stop(); val failure: MicrophoneFailure }
enum class MicrophoneFailure { UNAVAILABLE, BUSY, LOST, UNKNOWN }          // MicrophoneException(failure, message)
```

**Uso (la voce di un'app: dal microfono alla domanda).**

```kotlin
// prima: il permesso RECORD_AUDIO lo controlla l'app (onboarding o impostazioni, mai a sorpresa)
val capture = SpeechCapture(AndroidPcmSource(context))
val transcriber = Transcriber { factory.ordered(ProviderFactory.Kind.STT) }
val hint = Transcriber.hint("Domande su un'app di elementi:", elementNames)

capture.record(File(context.cacheDir, "ai/domanda.wav")).collect { e ->
  when (e) {
    is SpeechCapture.Event.Level -> micLevel.value = MicLevel(e.level, e.speaking)        // un flusso a parte: 50 volte al secondo
    SpeechCapture.Event.SpeechStarted -> Unit
    is SpeechCapture.Event.Finished -> {
      state.value = AssistantState.Transcribing
      val text = try { transcriber.transcribe(e.file, "it", hint).text }
        catch (x: AiError) { state.value = fail(AssistantFailure.of(x, other = FailureKind.TRANSCRIPTION)); return@collect }
      ask(text)                                                                              // mode = AskMode.VOICE
    }
    is SpeechCapture.Event.Empty -> state.value = AssistantState.HeardNothing
    is SpeechCapture.Event.Failed -> state.value = micFailed(e.cause)                        // MicrophoneException.failure -> FailureKind.MICROPHONE
  }
}
// Tocco sul tasto: capture.stopNow()
```

**Comportamento (numeri veri di `SpeechCapture.VadConfig`).**
- **Calibrazione** (primi 300 ms): il pavimento del rumore e' la **mediana** degli RMS (non il minimo: i primi frame dopo `startRecording` sono quasi vuoti e il pavimento crollava al valore piu' basso), tenuta fra 60 (`minFloor`) e 300 (`maxCalibratedFloor`: oltre non e' piu' un fondo ma qualcuno che parla). Poi **insegue il rumore, non la voce**: scende in fretta sui frame quieti, sale piano, e solo finche' non e' iniziato il parlato.
- **Soglia d'ingresso** = `max(pavimento x 2.5, speechFloorRms = 180)` (~ -45 dBFS: sotto restano respiro, ventole, fruscio). **Isteresi**: chi parla resta «parlato» fino a 0.6 della soglia (senza, le vocali deboli e le pause spezzano il parlato in coriandoli). Il parlato comincia dopo **3 frame** consecutivi sopra soglia.
- **Fine**: **1200 ms** di silenzio dopo l'ultimo parlato (`endSilenceMillis`), oppure **30 s** (`maxDurationMillis`), oppure `stopNow`. `Finished` e' valido solo se il parlato dura >= **300 ms** (`minSpeechMillis`) e la registrazione >= **600 ms** (`minTotalMillis`), altrimenti `Empty(TOO_SHORT)`. Il WAV tiene **300 ms** di coda dopo l'ultima parola (`tailKeepMillis`).
  La durata minima **non** e' una condizione per fermarsi (con la durata minima li' dentro una frase breve non fermava mai la cattura): il silenzio ferma, la durata giudica dopo.
- **Fermata a mano** senza parlato rilevato: se l'audio c'e' (registrazione >= 600 ms e picco >= `max(pavimento x 1.5, 180 x 0.6)`) si **tiene tutto** (voce bassa, stanza rumorosa); altrimenti `Empty(NOTHING_HEARD)`. Buttare l'audio e dire «non ho sentito niente» e' il modo peggiore di sbagliare ed e' cio' che faceva la prima versione.
- **`Level`**: 0..1 su una scala da -60 dBFS (0) a 0 dBFS (1), un evento per frame (50 al secondo). **Non metterlo nello `StateFlow<AssistantState>`**: ricomponeva tutto l'overlay a ogni frame audio (e faceva ripartire gli effetti che fermavano la voce di sistema); sta in un flusso a parte che legge solo l'aureola (`MicLevel`).
- **Microfono**: `start` falso -> `Event.Failed(MicrophoneException(source.failure))`; `read < 0` idem; una **raffica di letture vuote** (25 di fila, ~mezzo secondo) -> `MicrophoneFailure.LOST` (il microfono non c'e' piu' o se l'e' preso qualcun altro); un frame vuoto ogni tanto non e' un guasto.
  `MicrophoneFailure`: `UNAVAILABLE` (non esiste/non si inizializza), `BUSY` (non parte: una chiamata, un registratore, un assistente di sistema; o `ERROR_INVALID_OPERATION` in lettura), `LOST` (`ERROR_DEAD_OBJECT`), `UNKNOWN`. Sono casi diversi con **rimedi diversi**: la UI li distingue.
- **Cancellazione**: `record` e' freddo e la cancellazione ferma il microfono (`finally { source.stop() }`) e **non scrive niente**.
- **`AndroidPcmSource(context)`**: sorgente `VOICE_RECOGNITION` (l'OEM la tara per il parlato), `NoiseSuppressor` e `AutomaticGainControl` dove ci sono, thread di lettura a priorita' `URGENT_AUDIO` **ripristinata in `stop`** (e' un thread del pool IO), buffer `max(minimo, sampleRate x 2)` byte. Con un `Context` chiede il **fuoco audio** (`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`): senza, una musica in riproduzione continua a suonare dentro al microfono e la domanda finisce nel mezzo di una canzone; con il fuoco il sistema abbassa o mette in pausa l'altra app e poi la restituisce.
- **`WavWriter`**: WAV PCM 16 bit mono, intestazione di 44 byte **little-endian** (`WavWriter.header`, `write`): mai `DataOutputStream`, che e' big-endian.

**Trappole.**
- **`stopNow()` prima che il flusso sia raccolto si perde**: `record` azzera la richiesta di stop all'inizio della raccolta. Il tocco sul tasto che arriva fra la creazione del flusso e la sua raccolta non ferma niente.
- **Le soglie sono state rifatte il 2026-09-03** dopo la prima prova su un telefono vero, dove la voce non veniva mai riconosciuta: la versione precedente chiedeva -34 dBFS (RMS 655), cioe' una voce alzata a pochi centimetri dal microfono; una persona a mezzo metro sta fra 100 e 250 di RMS. Prima di toccare i numeri di `VadConfig`, provare su un telefono vero in una stanza vera.
- `AndroidPcmSource` **presume il permesso concesso**: se manca, `AudioRecord` non si inizializza e si vede `UNAVAILABLE`. `RECORD_AUDIO` arriva nel manifest fuso insieme al modulo: il permesso a runtime si chiede dall'onboarding o dalle impostazioni, **mai a sorpresa**.
- Senza `Context` (`AndroidPcmSource()`) niente fuoco audio.
- `Event.Finished.file` e' il `target` che si e' passato: si **cancella** dopo la trascrizione (e' la voce dell'utente).

## `Transcriber`

**Cos'e'.** La trascrizione segue **l'ordine dei provider scelto per la voce** (`ProviderFactory.Kind.STT`): il primo con chiave prova, i successivi sono riserva. La lingua e' quella dell'app (`it`/`en`), **forzata**: piu' precisa dell'auto-rilevamento sulle frasi corte.

**Firma essenziale.**

```kotlin
class Transcriber(providers: suspend () -> List<ReadyProvider>) {
  suspend fun transcribe(audio: File, language: String, hint: String?): Transcription     // Transcription(text, provider, model)
  companion object { fun hint(domainHint: String, vocabulary: List<String>, maxWords: Int = MAX_HINT_WORDS): String }
}
```

**Comportamento.**
- Lista vuota -> `AiError.Unauthorized` («nessun provider per la trascrizione»). Un `AiError.Unauthorized` di un provider **ferma subito** (chiave sbagliata: non si maschera); **qualunque altro errore** passa al provider dopo, e se finiscono tutti si rilancia l'ultimo. (Il KDoc dice «riserva su 429, 5xx e rete»; il codice ripiega su ogni errore che non e' una chiave sbagliata.)
- Il file parte come `audio/wav`. Modelli: `ready.sttModel` di ogni provider (default Whisper su Groq e OpenRouter, `AiDefaults.GEMINI_STT` su Gemini, con ripiego su un flash se il 400 dice che non esiste).
- **`Transcriber.hint(domainHint, vocabulary, maxWords)`**: una frase sul dominio dell'app («Domande su un'app di elementi:») piu' i nomi propri che l'utente potrebbe dire (al massimo `maxWords`, di serie `Transcriber.MAX_HINT_WORDS` = 20, parametro dalla 3.0.0; i vuoti non contano), troncata a `Transcriber.HINT_MAX_CHARS` (800: il prompt di Whisper vale al massimo 224 token). Senza nomi resta la frase.
  Il hint e' il **prompt di Whisper** su Groq (`GroqProvider.PROMPT_MAX_CHARS`) e un «Context:» nel testo su Gemini; **OpenRouter non lo manda**.
- Il failure della trascrizione si traduce con `AssistantFailure.of(e, other = FailureKind.TRANSCRIPTION)`: la stessa tabella di una domanda scritta (chiave, limite, rete, tempo, servizio, contenuto bloccato, modello sparito), invece di «non sono riuscito a capire l'audio» per ogni guasto — su un treno senza campo l'audio era perfetto e il guasto era la rete.

**Trappole.** Il `domainHint` e' per **il modello di trascrizione**, non per l'utente: nomi propri e vocabolario del dominio, non una frase cortese. Il `Transcription.text` puo' essere vuoto (silenzio che il rilevatore ha lasciato passare): l'app decide se e' `HeardNothing`.

## Token, costanti, Defaults

| costante | valore |
|---|---|
| `KeystoreCipher.DEFAULT_ALIAS` | `fluidengine.ai` |
| `AiKeyVerifier.DAY_MILLIS` | 24 ore (rinfresco dei cataloghi) |
| `AiHttp` default | connessione 10 s, lettura 60 s, silenzio in stream 45 s |
| `AiErrorMapper.PROVIDER_MESSAGE_CHARS` | 300 |
| `AiError.BLOCKED_PREFIX` | `bloccato` (la convenzione di prima del 2.9.0: un messaggio che comincia cosi' vale `BadReason.BLOCKED`) |
| `Transcriber.HINT_MAX_CHARS` / `MAX_HINT_WORDS` | 800 / 20 |
| `GroqProvider.PROMPT_MAX_CHARS` | 800 |
| `SpeechCapture.VadConfig` | 16 kHz, frame 20 ms, calibrazione 300 ms, x2.5, soglia 180, pavimento 60..300 (inseguito fino a 2000), isteresi 0.6, 3 frame, silenzio finale 1200 ms, massimo 30 s, parlato minimo 300 ms, totale minimo 600 ms, coda 300 ms |
| `SpeechCapture` letture vuote | 25 di fila = `LOST` |

## Cosa e' verificato dai test

- `AiKeyStoreTest`: salvare la **prima** chiave non esplode; una chiave nuova cancella la verifica della vecchia; togliere una chiave assente e' tranquillo; una chiave che il Keystore non legge piu' si toglie da sola.
- `AiSettingsDefaultsTest`: i default di serie sono quelli di sempre; un valore assente vale il default dell'app e uno scritto vale la scelta (anche se uguale al default); `enableIfNeverChosen` accende una volta sola e non fa tornare acceso chi ha spento; leggere i default non scrive; un file di impostazioni o di chiavi **corrotto** riparte e si puo' riscrivere; `AiDataStores.open` e il costruttore da un `DataStore` gia' aperto.
- `AiSettingsStoreTest`: l'ordine salvato accoda i provider che non nomina; i default dei modelli seguono le decisioni di prodotto e il profondo viene dal catalogo.
- `AvoidListTest` e `GroqReconcileTest`: la lista da evitare, una scelta su un modello da evitare che si sostituisce al riallineamento (anche con il catalogo fresco, anche senza client), il default di Groq ritirato che si sostituisce, il router di Groq senza candidati che cade sulla chat, `markUnavailable` che esclude un modello che il catalogo elenca ancora.
- `AiErrorMapperTest`: `retry-after` da header, da frase di Groq, da `RetryInfo` di Gemini; il tetto giornaliero dei gratuiti; la chiave sbagliata in tutte le forme; `tool_use_failed`, modello sparito, contesto troppo lungo (413 compreso) nei tre dialetti; la moderazione che non e' una chiave sbagliata; i dettagli corti e senza chiavi; un errore dentro uno stream aperto.
- `AiHttpServerTest` (server locale): corpo e header, 429 con `Retry-After`, stream evento per evento fino a `[DONE]`, header prima del primo evento, **la cancellazione chiude la connessione dal lato del server**, il timeout per lettura di un server muto, il multipart, un 200 non JSON come `Parse`. `StreamTimeoutTest`: il timeout lungo vs corto, il timeout della richiesta fino alla socket.
- `SseParserTest`, `MultipartBodyTest`: eventi, commenti, righe multiple, `\r` finale, flush; preludio ed epilogo, boundary nei campi rifiutato.
- `SpeechCaptureTest` (microfono finto): una frase e poi silenzio con WAV tagliato, il livello che segue la voce, una frase troppo breve, fermata a mano senza parlato, **voce bassa fermata a mano non si butta**, il tetto di durata, la stanza rumorosa, il solo rumore di fondo, i guasti del microfono con il perche', la cancellazione, l'intestazione WAV little-endian.
- `SmallPartsTest`: «un'altra riga omessa» al singolare, il boundary senza nome d'app, `Transcriber.hint` (venti nomi di serie, parametro, vuoti, tetto).
- **Non coperto**: `KeystoreCipher` su un telefono (nessun test col Keystore vero); `AndroidPcmSource` (richiede un dispositivo: fuoco audio, priorita' del thread, soppressore di rumore); le chiamate vere ai tre servizi.

## Dove guardare

Radice: `engine-ai/src/main/kotlin/dev/antigravity/fluidengine/ai/`.

| file | cosa c'e' dentro |
|---|---|
| `keys/AiKeyStore.kt`, `keys/SecretCipher.kt` | `AiKeyStore`, `KeyState`; `SecretCipher`, `KeystoreCipher` |
| `keys/AiSettingsStore.kt` | `AiSettingsStore`, `AiSettings`, `AiSettingsDefaults`, `AiDefaults`, `ThinkingLevel` |
| `keys/AiDataStores.kt` | apertura dei DataStore con la corruzione gestita |
| `keys/AiKeyVerifier.kt` | `AiKeyVerifier` (verify, reconcile, refreshIfStale, markUnavailable), `ModelCatalogStore`, `VerifyResult` |
| `net/AiHttp.kt` | `AiHttp`, `AiResponse`: POST JSON, SSE, multipart, GET |
| `net/AiError.kt`, `net/AiErrorMapper.kt` | `AiError`, `BadReason`, `RateLimitInfo`; la traduzione da HTTP a tipo |
| `net/SseParser.kt`, `net/Json.kt`, `net/MultipartBody.kt` | gli helper |
| `speech/SpeechCapture.kt` | `SpeechCapture`, `VadConfig`, `PcmSource`, `MicrophoneFailure`, `WavWriter` |
| `speech/AndroidPcmSource.kt` | l'`AudioRecord` vero, fuoco audio, soppressore di rumore |
| `speech/Transcriber.kt` | `Transcriber`, `Transcription`, `hint` |

Altre schede: provider e modelli in [ai-provider.md](ai-provider.md); orchestratore, tool e conferme in [ai-orchestrator.md](ai-orchestrator.md); l'interfaccia in [ai-ui.md](ai-ui.md); i tool federati in [ai-bridge.md](ai-bridge.md); le regole trasversali in [`../regole.md`](../regole.md).
