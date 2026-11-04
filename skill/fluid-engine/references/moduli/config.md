# engine-config

**La parte remota dell'engine**: un file JSON ospitato decide come si comportano le app gia' installate. Contiene `EngineRemoteConfig` (scarica il manifest,
lo mette in cache, risolve flag e vincoli per *questa* app) e `EngineConfigSource` (da dove, per chi, quanto spesso). Pacchetto
`dev.antigravity.fluidengine.config`, un solo file (`EngineRemoteConfig.kt`).

Il patto da tenere in testa e' che **cambia il comportamento, mai il codice**. Su Android non c'e' un modo stabile e ammesso di sostituire classi compilate in un'app
installata, quindi cio' che un file ospitato puo' fare e' scegliere fra percorsi che la build **contiene gia'**: accendere una funzione finita, spegnerne una rotta, alzare il
pavimento della versione dell'engine ritenuta valida, mostrare un avviso, fermare l'app (kill switch). Il resto richiede una nuova build: [`../limiti.md`](../limiti.md).
Il formato del file, il parser e le regole degli override sono in [foundation](foundation.md); questa scheda spiega **come lo vive l'app**.

Modello mentale: **sempre una risposta, subito; la rete aggiorna la risposta, non la sostituisce con il vuoto**. Cache e rete sono [storage](storage.md) e [net](net.md).
Catalogo: [`../api/config.md`](../api/config.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un interruttore che posso accendere/spegnere da remoto | `EngineFlag(key, default)` + `EngineRemoteConfig.flag(flag)` (un `Flow<Boolean>`) |
| il valore di un flag **adesso**, in un punto solo | `remoteConfig.current().isEnabled(flag)` |
| reagire appena arriva un nuovo manifest | raccogli `config` (tutto) o `flag(flag)` (un valore, senza ripetizioni) |
| scaricare il manifest all'avvio | `refreshIfStale()` in uno scope dell'app — non davanti al primo frame |
| forzare un controllo (pulsante «ricarica», debug) | `refresh()` |
| sapere se la build e' troppo vecchia | `compatibility()` → `OK` / `UPDATE_RECOMMENDED` / `UPDATE_REQUIRED` |
| mostrare un avviso (manutenzione, disservizio noto) | `current().notice` (una frase; cosa farne e' dell'app) |
| fermare una build che sta facendo danno | `current().killSwitch` (`enabled` + `message`) |
| un valore **senza** default sicuro (chiave, endpoint) | non e' un flag: va nella build |
| un flag **diverso per un'app** su un file condiviso | `overrides` nel manifest, chiave = `applicationId` (si fonde sui flag) |
| una configurazione che cambia ogni minuto / per utente | non e' un feed: questo e' un file di controllo, che cambia poche volte l'anno |

## `EngineRemoteConfig`

**Cos'e'.** Il lettore del file di controllo: tiene l'ultima risposta valida in cache e la espone come `Flow`. `config` **emette sempre**: prima del primo fetch emette
`EngineConfig.Fallback`, dove ogni flag vale il default compilato nella build. Un'app che non raggiunge mai la rete si comporta **esattamente come il giorno in cui e' uscita**.

**Firma essenziale.**

```kotlin
data class EngineConfigSource(
  val manifestUrl: String,
  val applicationId: String,                       // sceglie l'override di questa app
  val engineVersion: String = EngineBuild.VERSION,
  val refreshIntervalMillis: Long = DefaultRefreshIntervalMillis,   // 6 ore
)

class EngineRemoteConfig(
  private val http: EngineHttp,
  private val cache: EngineConfigCache,
  private val source: EngineConfigSource,
  private val now: () -> Long = { System.currentTimeMillis() },
) {
  val config: Flow<EngineConfig>
  fun flag(flag: EngineFlag): Flow<Boolean>
  suspend fun current(): EngineConfig
  suspend fun compatibility(): EngineCompatibility
  suspend fun refresh(): Result<EngineConfig>
  suspend fun refreshIfStale(): Result<EngineConfig>?   // null = la cache e' fresca, non ha scaricato
}
```

**Uso.**

```kotlin
object Flags { val NuovaAgenda = EngineFlag(key = "nuovaAgenda", default = false) }

// All'avvio: non blocca niente, finche' non arriva si usa l'ultima risposta (o i default).
applicationScope.launch { remoteConfig.refreshIfStale() }

// In un ViewModel: il valore parte dal default e si aggiorna da solo.
val nuovaAgenda = remoteConfig.flag(Flags.NuovaAgenda)
  .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Flags.NuovaAgenda.default)

// Compatibilita' e kill switch (e' tutto nel template EngineBootstrap.kt.txt):
when (remoteConfig.compatibility()) {
  EngineCompatibility.OK -> Unit
  EngineCompatibility.UPDATE_RECOMMENDED -> segnalaAggiornamentoConsigliato()
  EngineCompatibility.UPDATE_REQUIRED -> segnalaAggiornamentoNecessario()
}
val kill = remoteConfig.current().killSwitch
if (kill.enabled) fermaLApp(kill.message ?: "Questa versione non e' piu' utilizzabile.")
```

**Comportamento.**
- **Cosa succede senza rete.**

  | situazione | `config` | flag | `compatibility()` | kill switch |
  |---|---|---|---|---|
  | mai riuscito un fetch (prima installazione, offline) | `EngineConfig.Fallback`, `isFallback = true` | tutti il default compilato | `OK` | spento |
  | un fetch e' riuscito in passato, ora offline | l'**ultima risposta valida, senza scadenza** | quelli dell'ultimo manifest | quello dell'ultimo manifest | quello dell'ultimo manifest |
  | il fetch fallisce (404, timeout, JSON rotto) | invariata: `refresh()` restituisce `Result.failure` e **non tocca la cache** | invariati | invariata | invariato |
  | il manifest e' valido ma senza sezione `engine` | config vuota con `fetchedAt` valorizzato (`isFallback = false`) | default compilati | `OK` | spento |
  | la copia in cache non si legge piu' col parser attuale | torna a `Fallback` | default | `OK` | spento |

  «Perdere la rete non e' un motivo per cambiare comportamento»: un fallimento **non** riporta ai default, lascia in piedi l'ultima risposta.
- **Il default e' il comportamento di oggi.** E' la parte importante: scrivi il default come cio' che la build di adesso fa. Un flag che nessun manifest nomina vale il suo default.
- **`refresh()` non salva niente che non abbia letto.** Il manifest e' parsato **prima** di essere scritto in cache, cosi' un download troncato o malformato non diventa la risposta
  valida per le sei ore dopo. Poi salva, e **`config` emette da solo** (la cache e' osservata): le schermate che raccolgono `flag(...)` reagiscono nel momento in cui il refresh arriva.
- **`refreshIfStale()`** scarica solo se la copia in cache ha almeno `refreshIntervalMillis` (6 ore di serie) o non esiste; altrimenti restituisce `null` senza toccare la rete.
  Sei ore perche' un file di controllo non e' un feed: un fetch a ogni avvio a freddo metterebbe una chiamata di rete davanti al primo frame per un valore che cambia poche volte
  l'anno, e la risposta in cache basta sempre per disegnare.
- **Nessuno scheduler.** L'engine non rinfresca da solo: se il processo vive giorni, il fetch parte solo quando l'app chiama `refreshIfStale()`. Chiamalo all'avvio e, se serve,
  quando l'app torna in primo piano.
- `flag(flag)` ha `distinctUntilChanged`: emette solo quando **quel** valore cambia, anche se `config` cambia per altro. `config` invece emette a ogni refresh riuscito
  (cambia `fetchedAtEpochMillis`).
- **Override per app.** `EngineConfigSource.applicationId` sceglie la voce di `overrides`; e' **passato**, non letto dal package, cosi' una build di debug puo' fingersi la release
  mentre provi un flag. Le regole di fusione sono in [foundation](foundation.md) (i `flags` si fondono; `killSwitch`, `minimumVersion`, `recommendedVersion`, `notice` si sostituiscono).
- **Kill switch: l'engine riporta, non agisce.** Non esiste una schermata che si apre da sola: `killSwitch.enabled` e la frase sono dati; fermare l'app (schermata, uscita) e' una scelta
  dell'app. Non va usato per funzioni da ritirare con calma (quello e' un flag), ma per una build che sta facendo danno (un endpoint ora a pagamento, dati corrotti, un server martellato).

**Trappole.**
- **La cache non scade.** Se un kill switch e' stato acceso e poi spento nel manifest, un'app **offline** resta ferma finche' non riesce un fetch che dice il contrario. Un `killSwitch.enabled: true`
  copiato per sbaglio ferma ogni app che lo legge: il manifest va trattato come codice in produzione (si modifica con un commit, non a mano sulla pagina web di GitHub alle due di notte).
- **L'override del `killSwitch` sostituisce, non fonde.** Un override con solo `message` spegne il kill switch globale per quell'app (dettaglio in [foundation](foundation.md)).
- **`applicationId` e override.** Una build di debug con `applicationIdSuffix` ha un id diverso da quello della release: passando `BuildConfig.APPLICATION_ID` l'override della release non si applica.
  Per provarlo passa l'id della release a `EngineConfigSource`.
- **Il manifest e' pubblico.** Niente chiavi, URL privati, nomi di funzioni non annunciate.
- **Propagazione non istantanea.** Fra il commit e l'app passano: la CDN che serve il file raw (qualche minuto di cache), le sei ore di `refreshIntervalMillis`, e il momento in cui l'app chiama
  `refreshIfStale()`. Per un test, `refresh()` e' immediato rispetto alle sei ore, non rispetto alla CDN.
- **Orologio sballato.** `refreshIfStale` confronta `now() - fetchedAt` con l'intervallo: se il telefono ha l'ora indietro rispetto al momento del fetch l'eta' risulta negativa e non riscarica finche' l'ora non raggiunge
  il fetch precedente (comportamento letto dal codice, non provato).
- **Un flag ha una scadenza.** Quando la funzione e' stabile ovunque togli il ramo vecchio e il flag: un flag dimenticato e' un secondo percorso di codice che nessuno prova piu'.
- `compatibility()` di `EngineRemoteConfig` usa `source.engineVersion`; `EngineConfig.compatibility()` usa `EngineBuild.VERSION` salvo parametro. Lascia il default di `engineVersion` fuori dai test.

**Non usarlo quando.** Serve codice nuovo, dati per utente o un valore che cambia spesso; o un'impostazione che l'utente sceglie (e' una preferenza: [storage](storage.md)).

**Versione.** Dalla 1.0.

## Il file ospitato (riassunto)

```json
{
  "app": { "id": "…", "packageName": "…", "repository": { "repoOwner": "…", "repoName": "…" },
           "stable": { "version": "1.0.0", "releaseTag": "…", "apkAsset": "…", "sizeBytes": 0 } },
  "engine": {
    "schema": 1,
    "minimumVersion": "1.0.0", "recommendedVersion": "1.1.0", "notice": null,
    "flags": { "nuovaAgenda": false },
    "killSwitch": { "enabled": false, "message": null },
    "overrides": { "dev.antigravity.unaApp": { "flags": { "nuovaAgenda": true } } }
  }
}
```

La sezione `app` e' quella dello store e serve [update](update.md): **lo stesso URL basta per config e aggiornamenti**. Ogni campo, il modello `templates/engine-manifest.json` e il manifest vero
di Fluid Glass sono in [foundation](foundation.md). Dopo aver pubblicato una release, il manifest e' anche il modo in cui «il cambio dell'engine arriva a tutti»: non cambia il codice, ma decide **quale APK** le app scaricano.

## Token, costanti, Defaults

| nome | valore | note |
|---|---|---|
| `EngineConfigSource.DefaultRefreshIntervalMillis` | `21_600_000` (6 h) | scritto `6L * 60L * 60L * 1000L` |
| `EngineConfig.Fallback` | config vuota | cio' che l'app usa finche' non c'e' un manifest |

## Cosa e' verificato dai test

**Nessun test in `engine-config`** (la cartella `src/test` non esiste): `EngineRemoteConfig` non e' provato direttamente. Quello che e' provato, in `engine-foundation`
(`EngineConfigTest`), e' la **logica che usa**: il parse del manifest, la fusione degli override, il ritorno al default per un flag ignoto, il manifest senza sezione `engine`, i campi sconosciuti,
la soglia di versione. Non e' provato: la cache, il refresh, la finestra delle sei ore, l'emissione di `config` dopo un refresh. `now` e' un parametro e `EngineHttp` e' `open` proprio per scrivere quei test.

## Dove guardare

- `engine-config/src/main/kotlin/dev/antigravity/fluidengine/config/EngineRemoteConfig.kt` — `EngineConfigSource`, `EngineRemoteConfig` (tutto il comportamento descritto sopra).
- `engine-foundation/.../EngineConfig.kt`, `EngineManifest.kt` — `EngineFlag`, `EngineConfig`, `resolveEngineConfig`, il parser: [foundation](foundation.md).
- `templates/EngineBootstrap.kt.txt` — il collegamento nella `Application` (refresh, compatibilita', kill switch, widget); `templates/EngineModule.kt.txt` — il costruttore.

## Dipendenze e configurazione minima

**Dipendenze del modulo.** `api` su `engine-foundation`, `engine-net`, `engine-storage` (quindi anche DataStore e il permesso `INTERNET`).
`tools/engine-install.ps1 -AppRoot . -Modules engine-config` include da solo foundation, net e storage. Nel `build.gradle`:

```groovy
implementation project(':engine-config')   // feature flag remoti
```

**Configurazione minima.** Un `manifestUrl` (di solito il `manifest.json` raw del repo dell'app), l'`applicationId`, e uno scope dell'app che chiami `refreshIfStale()`:

```kotlin
// Con Hilt (templates/EngineModule.kt.txt)
@Provides @Singleton
fun engineRemoteConfig(http: EngineHttp, @ApplicationContext context: Context): EngineRemoteConfig =
  EngineRemoteConfig(
    http = http,
    cache = EngineConfigCache(context),
    source = EngineConfigSource(
      manifestUrl = "https://raw.githubusercontent.com/OWNER/REPO/main/manifest.json",
      applicationId = BuildConfig.APPLICATION_ID,
    ),
  )

// Nella Application (templates/EngineBootstrap.kt.txt)
applicationScope.launch { remoteConfig.refreshIfStale() }
```

Servono quindi nell'app: un'istanza condivisa di `EngineHttp` ([net](net.md)), il manifest pubblicato, e almeno un flag dichiarato (`EngineFlag`). Senza un manifest raggiungibile tutto funziona sui default.
