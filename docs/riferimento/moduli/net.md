<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/net.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-net

**Le due chiamate HTTP che l'engine fa da solo**: leggere un documento piccolo (`EngineHttp.readText`, il manifest) e scaricare un file grande
(`EngineHttp.download`, l'APK dell'aggiornamento), nel pacchetto `dev.antigravity.fluidengine.net`. **Dalla 4.0 (F8.20)** anche l'osservatore della
rete che non lampeggia (`EngineNetwork`) e la lettura delle condizioni del telefono (`EngineConditions`: rete a consumo e roaming, risparmio dati ed
energetico, calore, corrente), con le reti finte per le prove (`testing.FakeNetworks`). La coda dei trasferimenti lunghi, con pausa e ripresa, è un
modulo a parte sopra questo: [transfer](transfer.md).

E' costruita su `HttpURLConnection` e **non** su una libreria client, di proposito: queste chiamate girano prima che lo stack di rete dell'app sia
necessariamente pronto (il controllo aggiornamenti parte a freddo, il fetch della config deve funzionare in un'app che non parla con nient'altro), e
un engine che impone un client HTTP al suo ospite e' un engine che perde la discussione con quello dell'ospite. Modello mentale: **un tubo con due
rubinetti, non un client HTTP generico**. Chi ha bisogno di POST, JSON tipizzato, autenticazione, retry o cache usa il proprio stack.

Usato da [config](config.md) (`readText`) e [update](update.md) (`readText` per il manifest, `download` per l'APK). Catalogo: [`../api/net.md`](../api/net.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| scaricare il testo di un manifest/JSON piccolo | `EngineHttp.readText(url)` |
| scaricare un file grande con avanzamento e **senza lasciare mezzi file** | `EngineHttp.download(url, target, expectedBytes, onProgress = ...)` |
| provare in un test la logica sopra la rete (manifest, «e' piu' nuova?») | una sottoclasse di `EngineHttp` che ridefinisce `readText` — la classe e' `open` per questo |
| dare all'engine il nome della mia app nello `User-Agent` | `EngineHttp(userAgent = "MiaApp/1.2.3")` |
| chiamate dell'app (API, POST, auth, retry, cache, certificati) | il **tuo** client (OkHttp, Ktor…): `EngineHttp` non e' fatto per questo |
| un solo `EngineHttp` per config e aggiornamenti | lo stesso oggetto passato a `EngineRemoteConfig` e a `EngineAppUpdater`/`AndroidAppUpdateInstaller` |
| sapere se c'è internet, senza falsi allarmi (Wi-Fi d'albergo, VPN, micro-cadute) | `EngineNetwork.states(context)` (la tenuta di 2,5 s è di serie) |
| la capsula «Offline · 3 modifiche in attesa» | `EngineNetwork.states` → `online`, nella `FluidConnectionCapsule` di `engine-ui` ([elementi-dinamici](../componenti/elementi-dinamici.md)) |
| chiedere il permesso prima di un lavoro pesante (rete a consumo, risparmio dati, batteria, calore) | `EngineConditions.install(context)` una volta, poi `FluidDeviceConditions.allows(FluidWork.LargeDownload)` ([foundation](foundation.md)) |
| provare la rete in una prova o simularla in una galleria | `testing.FakeNetworks` (la regola e la tenuta vere, senza telefono) |
| un download lungo che si mette in pausa, riprende e continua con l'app chiusa | **non** `EngineHttp`: `engine-transfer` ([transfer](transfer.md)) |

## `EngineHttp`

**Cos'e'.** Un wrapper sottile su `HttpURLConnection` con due metodi `suspend` (eseguiti su `Dispatchers.IO`) e i timeout giusti. `open` perche' tutto cio' che
sta **sopra** la rete — decidere se una release e' piu' nuova, leggere un manifest, risolvere un URL di download — e' logica ordinaria che merita test ordinari, e
una classe `final` costringerebbe ogni app ospite a toccare la rete per provarla (CHANGELOG 1.1.0).

**Firma essenziale.**

```kotlin
open class EngineHttp(
  private val userAgent: String = DefaultUserAgent,          // "FluidEngine/1.0"
  private val connectTimeoutMillis: Int = 15_000,
  private val readTimeoutMillis: Int = 30_000,
) {
  open suspend fun readText(url: String, headers: Map<String, String> = emptyMap()): String
  open suspend fun download(
    url: String, target: File, expectedBytes: Long = 0L,
    headers: Map<String, String> = emptyMap(),
    onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit = { _, _, _ -> },
  ): File
  companion object { const val DefaultUserAgent: String }
}
```

**Uso.**

```kotlin
val http = EngineHttp(userAgent = "MiaApp/${BuildConfig.VERSION_NAME}")
val raw = http.readText("https://raw.githubusercontent.com/OWNER/REPO/main/manifest.json")

// Un finto per i test: niente rete, la logica sopra resta vera.
class FakeHttp(private val body: String) : EngineHttp() {
  override suspend fun readText(url: String, headers: Map<String, String>): String = body
}
```

**Comportamento — `readText`.**
- Aggiunge `Accept: application/json` **dopo** i tuoi header: se ne passi uno `Accept` tuo, vince quello dell'engine. Imposta anche `User-Agent`.
- Legge **tutto il corpo in memoria** come `String` (UTF-8). Per un manifest va bene; non e' fatto per file grandi.
- **Qualunque codice fuori da 200–299 lancia** `IllegalStateException("Richiesta non riuscita (<codice>).")`; il corpo dell'errore viene letto e **scartato**.
  Gli errori di rete (`UnknownHostException`, `SocketTimeoutException`, …) risalgono cosi' come sono. Nessun retry, nessuna cache HTTP, nessun ETag.
  I chiamanti dell'engine lo mettono in un `Result` (`runCatching`).
- Segue i redirect con il meccanismo di `HttpURLConnection` (`instanceFollowRedirects = true`), che **rifiuta quelli che cambiano protocollo**
  (`http` → `https`): un URL `http://` che reindirizza a `https://` finisce in «Richiesta non riuscita (301)». Usa direttamente `https://`.

**Comportamento — `download`.**
- **Scrive in `<nome>.part` e rinomina alla fine.** Un file mezzo scritto col nome definitivo e' indistinguibile da uno completo, e il giro dopo qualcuno proverebbe a
  installarlo. Il `.part` precedente viene cancellato prima di cominciare, e rimosso nel `finally` se qualcosa va storto. La cartella di destinazione viene creata
  (`mkdirs`); un `target` gia' esistente viene sostituito solo a download completato.
- **Redirect a mano, fino a 5.** `HttpURLConnection` non segue un redirect che cambia protocollo, ed e' esattamente cio' che fa un host di release quando passa
  alla sua CDN (GitHub Releases → storage): seguirli a mano non e' opzionale. Alla sesta redirezione, o con un `3xx` senza `Location`, lancia «Download non riuscito (<codice>).».
  Gli stessi `headers` sono mandati a **ogni** salto (anche a un host diverso: non mettere un token qui).
- **Verifica la dimensione.** Se `expectedBytes > 0` e i byte scaricati sono diversi lancia «File incompleto: N byte invece di M.». Un download troncato **non solleva
  niente da solo** (la connessione si chiude e il file resta a meta'): senza questo controllo l'APK dimezzato arriverebbe all'installer, che lo rifiuta con un errore che non spiega niente.
  Il confronto e' con `expectedBytes` (il `sizeBytes` del manifest), **non** con il `Content-Length` della risposta.
- `total` per `onProgress` e' `Content-Length` se positivo, altrimenti `expectedBytes`; se nessuno dei due e' noto il `progress` resta `0f`.
  Il callback e' chiamato **a ogni lettura da 8 KiB**, **sul thread IO** (non sul principale) e con `progress` limitato a `0f..1f`: un APK di 3,3 MB sono circa 400 chiamate.
- Timeout: connessione `connectTimeoutMillis` (15 s); lettura **fissa a 60 s** nel download (non usa `readTimeoutMillis`, che vale solo per `readText`).

**Trappole.**
- **`expectedBytes` sbagliato blocca il download.** Con un `sizeBytes` del manifest diverso dal peso vero (un APK ricostruito senza aggiornare il manifest) il file viene scaricato per intero
  e poi rifiutato come incompleto. E' una cosa buona (meglio che installare l'APK sbagliato), ma e' anche l'errore piu' tipico di una pubblicazione fatta a mano.
- **Annullare il coroutine non interrompe un download in corso.** Nel ciclo di lettura non c'e' nessun controllo di cancellazione (`ensureActive`): il blocco e' I/O bloccante dentro `withContext`,
  quindi la lettura prosegue fino alla fine e l'eccezione di cancellazione arriva solo dopo. Per fermarlo davvero serve un `EngineHttp` tuo (sottoclasse) che controlli l'attivita'.
- **Senza `expectedBytes` un troncamento passa inosservato.** Passa sempre il peso quando lo conosci (`AvailableAppUpdate.sizeBytes`).
- I messaggi d'errore sono **in italiano** e fissi: un'app in un'altra lingua li vedra' cosi' se li mostra tali e quali.
- Non e' uno stack di rete: niente POST, niente corpo di richiesta, niente cookie, niente configurazione TLS, niente cache.

**Non usarlo quando.** Servono richieste dell'app (usa il tuo client) o un download che deve poter essere annullato/ripreso (scrivine uno tuo, o usa `DownloadManager`).

**Versione.** Dalla 1.0; la verifica della dimensione attesa e `open` dalla 1.1.0.

## Lo stato della rete: `EngineNetwork`, `EngineNetworkState` (F8.1, F8.20)

L'osservatore della rete dell'engine, **senza interfaccia**. Lo usano gli stati pronti di `engine-ui` (che riprovano da
soli quando la rete torna), la capsula della rete (`FluidConnectionCapsule`) e le condizioni del telefono
(`EngineConditions`, sotto). Guarda la rete predefinita del telefono, quella che le app usano davvero, e le reti che
stanno sotto una VPN.

```kotlin
EngineNetwork.current(context): EngineNetworkState                          // adesso (una lettura, senza tenuta)
EngineNetwork.states(context, graceMillis = 2_500): Flow<EngineNetworkState> // ogni volta che cambia, con la tenuta
EngineNetwork.steady(raw, graceMillis, now): Flow<EngineNetworkState>        // la tenuta su un flusso qualsiasi
EngineNetworkState(connected, validated, metered, vpn = false, roaming = false).online   // connected && validated
```

- **«Offline» non è «senza rete»**: un Wi-Fi d'albergo senza accesso è una rete che non porta a internet. `online` chiede
  anche `validated`, cioè `NET_CAPABILITY_VALIDATED` (Android ha provato che quella rete arriva a internet); un portale
  di login (`CAPTIVE_PORTAL`) e una rete sospesa (`NOT_SUSPENDED` assente, i dati durante una chiamata) non sono online.
- **La VPN vale quanto la rete sotto.** Android segna spesso la VPN come validata senza provarla, e certe VPN non lo dicono
  mai: l'osservatore guarda anche le reti che non sono VPN (`registerNetworkCallback` con la richiesta di serie, che
  chiede `NOT_VPN`). Online se almeno una rete sotto porta a internet, offline se nessuna, anche se la VPN dice il
  contrario; a consumo e in roaming come la rete sotto che la VPN usa (quella che non costa, se c'è). Se non si sa cosa
  c'è sotto, si crede alla VPN.
- **Non lampeggia.** Il passaggio da online a non online aspetta `graceMillis` (`EngineNetworkDefaults.OfflineGraceMillis`,
  **2,5 s a occhio**, 25/11/2026) e si annulla se la rete torna: il cambio dal Wi-Fi ai dati (il Wi-Fi si perde prima che
  la rete nuova sia validata), un ascensore, un treno sotto un ponte. La tenuta conta dalla **prima** caduta, non
  dall'ultimo cambio (Wi-Fi senza internet e poi niente: 2,5 s dal primo). Il ritorno in linea e ogni altro cambio
  (a consumo, roaming) passano subito. Ogni raccolta ha il suo stato: due schermate sullo stesso flusso non si pestano.
- `metered` è la rete a consumo (dati mobili, hotspot; `TEMPORARILY_NOT_METERED` del 5G illimitato conta come non a
  consumo): un'app rimanda i download grandi. `roaming` dall'API 28.
- La regola è pura (`EngineNetworkRule`, interna) e si prova sul computer con le reti finte.
- Serve `ACCESS_NETWORK_STATE`, che il manifesto di `engine-net` dichiara già.

**Apple.** Nessun componente pubblico: `NWPathMonitor` riporta ogni cambio, e sono Mail e Note a non mostrare le cadute
brevi. Il tempo della tenuta è nostro.

### Le reti finte: `testing.FakeNetworks`

Per le prove e per la simulazione di una galleria: **non tocca il telefono**, e la regola e la tenuta sono quelle vere.

```kotlin
val nets = FakeNetworks(graceMillis = 2_500, now = { clock })   // parte con un Wi-Fi che porta a internet
nets.wifi(validated = false, captivePortal = true)              // un Wi-Fi d'albergo
nets.cellular(roaming = true); nets.vpn(claimsValidated = true); nets.vpnOff(); nets.lose("wifi"); nets.disconnect()
nets.microCut(600)                                              // sospesa: una caduta di 0,6 s
nets.handover(gapMillis = 400, validationMillis = 700)          // sospesa: dal Wi-Fi ai dati com'è su un telefono
nets.raw: StateFlow<EngineNetworkState>                         // senza tenuta
nets.states: Flow<EngineNetworkState>                           // con la tenuta, come EngineNetwork.states
```

## Le condizioni del telefono: `EngineConditions` (F8.20, 4.0)

**Un posto solo** che sa in che condizioni è il telefono (la scheda `pochi-dati-poca-batteria`): la rete (con la tenuta),
il risparmio dati, il risparmio energetico, il calore e la corrente, in un `FluidConditions` di `engine-foundation`. Qui
c'è solo la lettura di Android; i tipi e la regola (`FluidConditions.allows`, `FluidWork`, `FluidPermit`) e il posto solo
del processo (`FluidDeviceConditions`) stanno in [foundation](foundation.md).

```kotlin
EngineConditions.install(context)            // una volta: tiene aggiornato FluidDeviceConditions per tutto il processo
EngineConditions.current(context): FluidConditions
EngineConditions.states(context, graceMillis = 2_500): Flow<FluidConditions>
// prima di un lavoro pesante, ovunque:
val permit = FluidDeviceConditions.allows(FluidWork.LargeDownload, FluidWorkNeeds(wifiOnly = true))
if (permit.waiting) mostraIlPerche(permit.reasons)          // e «Fallo comunque»: allows(…, force = true)
```

| condizione | da dove | quando conta |
|---|---|---|
| rete, a consumo, roaming | `EngineNetwork.states` (tenuta di 2,5 s) | sempre |
| risparmio dati | `ConnectivityManager.getRestrictBackgroundStatus() == ENABLED`, annuncio `ACTION_RESTRICT_BACKGROUND_CHANGED` | **solo su una rete che costa**: Android limita solo lì |
| risparmio energetico | `PowerManager.isPowerSaveMode`, annuncio `ACTION_POWER_SAVE_MODE_CHANGED` | sempre |
| calore | `PowerManager.getCurrentThermalStatus` e il suo ascoltatore (API 29): nessuno e leggero → `Nominal`, moderato → `Fair`, severo → `Serious`, critico e oltre → `Critical` | sotto l'API 29, o dove resta a zero, mai caldo |
| corrente | `ACTION_BATTERY_CHANGED` (`EXTRA_PLUGGED`), annunci `ACTION_POWER_CONNECTED`/`DISCONNECTED` | sempre |

- **`install` è facoltativo e una volta sola** (le chiamate dopo la prima non fanno niente; `uninstall` per le prove). Costa
  un callback di rete, un ricevitore e un ascoltatore del calore; tutto fuori dal thread principale. `FluidTransfers.open`
  di `engine-transfer` lo chiama da sé. Senza `install`, `FluidDeviceConditions` resta `Unknown` e niente si ferma.
- **Chi legge già da qui**: gli stati pronti offline (`FluidEmptyState(FluidEmptyPreset.Offline)` senza `online` riprova da
  solo quando la rete torna), la luce del vetro che segue l'inclinazione (`FluidWork.MotionEffects`: si ferma col
  risparmio energetico, anche in carica, e col telefono caldo), l'aptica continua (`FluidWork.ContinuousHaptics`: col
  risparmio energetico, anche in carica), la coda dei trasferimenti. Il vetro e il risparmio energetico hanno le loro regole di F7, che restano.
- Nessun permesso oltre `ACCESS_NETWORK_STATE`; nessuna libreria.

**Apple.** `NWPath.isExpensive` e `isConstrained` (Modalità dati ridotti), `ProcessInfo.isLowPowerModeEnabled` e
`thermalState`: i nomi e il significato vengono da lì (fiducia alta); quali lavori si fermano per cosa è la regola di
`FluidWork`, nostra.

## Token, costanti, Defaults

| nome | valore |
|---|---|
| `EngineHttp.DefaultUserAgent` | `"FluidEngine/1.0"` (non e' la versione dell'engine: e' una stringa fissa) |
| timeout di connessione | 15 000 ms (parametro) |
| timeout di lettura di `readText` | 30 000 ms (parametro) |
| timeout di lettura di `download` | 60 000 ms (**fisso**) |
| redirect massimi in `download` | 5 (privato) |

## Cosa e' verificato dai test

- `EngineNetworkTest` (JVM, tempo delle coroutine): la regola (validata, non validata, portale, sospesa, roaming, la VPN
  sopra un Wi-Fi con e senza internet, sopra i dati, con Wi-Fi e dati insieme), le reti finte passo per passo, la tenuta
  (una micro-caduta e il cambio di rete non si vedono, una caduta vera si vede dopo 2,5 s e il ritorno subito, la tenuta
  conta dalla prima caduta, due raccolte non si pestano).
- `EngineConditionsTest` (Robolectric): il risparmio dati solo sulle reti che costano, i gradini del calore, i segnali
  letti da Android (risparmio energetico, calore, risparmio dati, corrente), `install` che riempie il posto solo.
- `EngineHttp` non ha prove sue (la classe e' `open` per fingere la rete altrove); la coda di `engine-transfer` prova la
  ripresa con `Range` contro un server vero (MockWebServer).

## Dove guardare

- `engine-net/src/main/kotlin/dev/antigravity/fluidengine/net/EngineHttp.kt` — `readText`, `download`, i redirect a mano (`openFollowingRedirects`), `DefaultUserAgent`.
- `…/net/EngineNetwork.kt` — la regola (`EngineNetworkRule`), la tenuta (`steady`), i due callback (`rawStates`), `capsOf`.
- `…/net/EngineConditions.kt` — la lettura dei segnali (`readSignals`, `signals`), `conditionsOf`, `install`.
- `…/net/testing/FakeNetworks.kt` — le reti finte.
- `engine-net/src/main/AndroidManifest.xml` — i permessi che il modulo porta nell'app.

## Dipendenze e configurazione minima

**Dipendenze del modulo.** `api project(':engine-foundation')`. Nessuna libreria di rete. Il manifest del modulo dichiara `INTERNET` e `ACCESS_NETWORK_STATE`, che si fondono
nel manifest dell'app (`ACCESS_NETWORK_STATE` non e' usato da `EngineHttp`, che non controlla lo stato della rete: un telefono offline fallisce con la sua eccezione).
Cosi' un'app che usa solo `engine-config` ha gia' `INTERNET` senza dichiararlo.

**Aggancio.** `tools/engine-install.ps1 -AppRoot . -Modules engine-net` (porta `engine-foundation`; `engine-config` e `engine-update` lo portano da soli). Nel `build.gradle`:

```groovy
implementation project(':engine-net')    // quasi sempre arriva gia' con engine-config o engine-update
```

**Configurazione minima.** Un'istanza condivisa con lo `User-Agent` dell'app (e' in `templates/EngineModule.kt.txt`):

```kotlin
@Provides @Singleton
fun engineHttp(): EngineHttp = EngineHttp(userAgent = "MiaApp/${BuildConfig.VERSION_NAME}")
```
