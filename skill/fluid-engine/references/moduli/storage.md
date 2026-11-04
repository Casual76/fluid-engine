# engine-storage

Tutto cio' che **l'engine stesso** ricorda fra due avvii, in **un solo DataStore** (`fluid_engine`): le impostazioni d'aspetto che il design
system legge (`EngineSettingsStore`) e l'ultima copia del manifest remoto (`EngineConfigCache`). Pacchetto
`dev.antigravity.fluidengine.storage`, un solo file sorgente (`EngineStore.kt`).

E' un modulo **piccolo e sostituibile**. Un'app che ha gia' un suo modello di impostazioni non ne ha bisogno per il tema: mappa il proprio
modello su `EngineSettings` ([foundation](foundation.md)) e basta. `engine-config` invece lo richiede, perche' la cache del manifest sta qui.
Modello mentale: **le preferenze dell'app sono dell'app, quelle dell'engine sono dell'engine** — due file diversi, cosi' l'engine puo'
cancellare, migrare o azzerare il suo senza toccare una sola preferenza dell'app, e per questo un aggiornamento dell'engine e' sicuro.

Catalogo completo delle firme: [`../api/storage.md`](../api/storage.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| tema, accento, aptica salvati per una **app nuova** | `EngineSettingsStore` |
| tema, accento, aptica in un'app che **ha gia'** le sue impostazioni | niente: `map { }` del tuo flow su `EngineSettings` |
| leggere le impostazioni una volta, fuori da una schermata | `EngineSettingsStore.current()` |
| cambiare un solo campo | un setter (`setThemeMode`, `setCustomAccent`, …) o `update { it.copy(...) }` |
| la copia del manifest per la config remota | `EngineConfigCache` (la crea chi costruisce `EngineRemoteConfig`, non la leggi tu) |
| salvare **altre** preferenze dell'app | il tuo DataStore, con un **nome diverso** da `fluid_engine` |
| un database, un file, una cache d'immagini | non e' compito di questo modulo |

## `EngineSettingsStore`

**Cos'e'.** Persistenza delle sei impostazioni di `EngineSettings` su DataStore Preferences. Esiste perche' una nuova app abbia un selettore
di tema funzionante senza scrivere prima la persistenza.

**Firma essenziale.**

```kotlin
class EngineSettingsStore(private val context: Context) {
  val settings: Flow<EngineSettings>
  suspend fun current(): EngineSettings
  suspend fun update(transform: (EngineSettings) -> EngineSettings)
  suspend fun setThemeMode(mode: ThemeMode)
  suspend fun setAccentMode(mode: AccentMode)
  suspend fun setCustomAccent(name: String)            // imposta anche accentMode = CUSTOM_PRESET
  suspend fun setDynamicColorEnabled(enabled: Boolean)
  suspend fun setAmoledEnabled(enabled: Boolean)
  suspend fun setHapticsEnabled(enabled: Boolean)      // dalla 1.19.0
}
```

**Uso.**

```kotlin
val store = EngineSettingsStore(context)
val settings by store.settings.collectAsStateWithLifecycle(EngineSettings())   // i default finche' il file non risponde

FluidTheme(settings = settings) { /* ... */ }
scope.launch { store.setThemeMode(ThemeMode.DARK) }
```

**Comportamento.**
- **Un file solo, per processo.** `Context.fluidEngineStore` e' una proprieta' delegata di primo livello (`preferencesDataStore(name = "fluid_engine")`):
  `EngineSettingsStore` e `EngineConfigCache` ne condividono la stessa istanza, quindi si possono costruire quanti oggetti si vuole.
- **`update` e' una lettura-modifica-scrittura atomica** (`edit`): legge il valore corrente, applica `transform`, scrive **tutti e sei** i campi
  in una sola transazione. Due `update` concorrenti non si perdono i campi a vicenda.
- **Tolleranza in lettura.** Un nome salvato che non indica piu' niente (una voce di enum tolta da un aggiornamento dell'engine) si risolve
  nel **default** invece di lanciare, perche' e' una lettura che l'utente non vede e non puo' correggere. Un campo assente vale il default di
  `EngineSettings()` (sistema, marchio, `"fluid"`, dinamico acceso, AMOLED spento, aptica accesa).
- Chiavi su disco (utili solo per leggere un backup o scrivere un test): `theme_mode`, `accent_mode`, `custom_accent_name`,
  `dynamic_color_enabled`, `amoled_enabled`, `haptics_enabled` (enum salvati per **nome**, non per ordinale: riordinare l'enum non rompe i dati).
- `settings` emette il valore corrente appena il file e' letto e poi a ogni scrittura; **non** ha `distinctUntilChanged`: DataStore emette a ogni
  modifica del file (anche di un'altra chiave), quindi chi non vuole ricomporre per niente lo aggiunge.

**Trappole.**
- **Un secondo DataStore con lo stesso nome nel tuo codice fa crollare l'app.** Se l'app dichiara a sua volta un `preferencesDataStore(name = "fluid_engine")`
  si ottiene «There are multiple DataStores active for the same file» appena entrambi vengono aperti. Il nome e' dell'engine.
- **Nessun gestore di corruzione.** Il DataStore e' creato senza `corruptionHandler`: se il file e' corrotto il flow lancia. Raro, ma qui non e' coperto
  (l'assistente, in `engine-ai`, ha il suo store che riparte da vuoto: non e' lo stesso file).
- `settings` non e' `StateFlow`: serve un valore iniziale quando lo si raccoglie in Compose (`EngineSettings()` e' il default giusto), o l'UI aspetta il disco.
- Scrivere un campo di `EngineSettings` che il tuo modello non ha **sovrascrive** quello salvato: se mappi i tuoi settings su `EngineSettings` *e* usi
  lo store, due fonti di verita' si pestano i piedi. Scegline una.

**Non usarlo quando.** L'app ha gia' un modello di impostazioni (le si mappa, non si duplicano); oppure serve salvare qualcosa che non e' d'aspetto.

**Versione.** Dalla 1.0; `hapticsEnabled`/`setHapticsEnabled` dalla 1.19.0.

## `EngineConfigCache` e `CachedManifest`

**Cos'e'.** L'ultimo manifest letto, tenuto perche' l'app abbia una risposta **prima** che la rete ne abbia una. E' l'unico punto in cui il manifest
remoto tocca il disco. Lo usa `EngineRemoteConfig` ([config](config.md)); un'app non lo legge di norma.

**Firma essenziale.**

```kotlin
data class CachedManifest(val raw: String, val fetchedAtEpochMillis: Long)

class EngineConfigCache(private val context: Context) {
  val cached: Flow<CachedManifest?>                       // null finche' non c'e' mai stata una scrittura
  suspend fun store(raw: String, fetchedAtEpochMillis: Long)
}
```

**Comportamento.**
- **Salva il testo grezzo, non un oggetto.** L'interpretazione e' della versione dell'engine che sta girando: un modello decodificato e salvato andrebbe
  migrato a ogni cambio di schema, mentre rileggere il testo non costa niente e non puo' mai disaccordare con il codice che lo legge.
  Dopo un aggiornamento dell'engine, dunque, la cache **vecchia** viene riletta col parser **nuovo**: e' voluto.
- `store` scrive testo e istante in un'unica transazione; chiavi `engine_manifest_json` e `engine_manifest_fetched_at`.
- Se manca l'istante, `fetchedAtEpochMillis` vale `0` (cioe' «vecchissima»: `refreshIfStale` scarichera' subito).
- `store` **non valida**: chi la chiama ha gia' fatto il parse (lo fa `EngineRemoteConfig.refresh`, apposta *prima* di salvare, cosi' un download troncato
  non diventa la risposta valida per le sei ore dopo).

**Trappola.** Non scriverci a mano un JSON per «forzare» un flag in debug: sparisce al refresh successivo e aggira il parse. Per provare un flag usa un
`manifestUrl` di prova e un `applicationId` finto (vedi [config](config.md)).

## Token, costanti, Defaults

Nessun oggetto `Defaults`. Il nome del file DataStore e' `fluid_engine` (privato nel sorgente). I default delle impostazioni sono quelli di `EngineSettings()`.

## Cosa e' verificato dai test

**Nessun test** in `engine-storage` (la cartella `src/test` non esiste). Le garanzie sopra vengono dal codice e dal KDoc, non da una prova: in
particolare la tolleranza ai nomi di enum sconosciuti e l'atomicita' di `update` non sono coperte. Le rappresentazioni che il resto dell'engine usa
(`EngineSettings`, il parse del manifest) sono invece provate in `engine-foundation` ([foundation](foundation.md)).

## Dove guardare

- `engine-storage/src/main/kotlin/dev/antigravity/fluidengine/storage/EngineStore.kt` — tutto: il DataStore condiviso, `EngineSettingsStore` con le sue chiavi
  e la lettura tollerante, `CachedManifest`, `EngineConfigCache`.

## Dipendenze e configurazione minima

**Dipendenze del modulo.** `api project(':engine-foundation')` e `api androidx.datastore:datastore-preferences` (1.1.1 in `versions.gradle`). Quindi chi dipende da
`engine-storage` riceve anche DataStore e foundation. Non ha `AndroidManifest` e non chiede permessi.

**Aggancio.** `tools/engine-install.ps1 -AppRoot . -Modules engine-storage` (porta `engine-foundation`; `engine-config` lo porta da solo). Nel `build.gradle` dell'app:

```groovy
implementation project(':engine-storage')   // impostazioni su DataStore
```

**Configurazione minima.** Nessun file da toccare. Con Hilt, una riga nel modulo DI (e' nel template `templates/EngineModule.kt.txt`):

```kotlin
@Provides @Singleton
fun engineSettingsStore(@ApplicationContext context: Context): EngineSettingsStore = EngineSettingsStore(context)
```

Senza Hilt basta `EngineSettingsStore(applicationContext)` tenuto dove vive l'app. Ricorda: l'`applicationContext`, non l'Activity.
