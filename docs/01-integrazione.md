# 01 · Integrazione

Come mettere l'engine dentro un'app. Vale sia per un progetto nuovo sia per uno che esiste già.

## Il modello: una cartella, un pin

L'engine vive nel suo repo. Ogni app se lo porta dentro come **submodule git agganciato a un tag**, e il build dell'app include i moduli dell'engine come sorgenti.

```
la-tua-app/
  android/
    app/
    core/
    engine/            <- submodule, agganciato a engine-1.0.0
    settings.gradle    <- include i moduli dell'engine
    engine.properties  <- dice quale versione è agganciata
```

Perché così e non con un artefatto Maven:

- **Zero infrastruttura.** Niente publishing, niente token, niente JitPack. Funziona anche offline.
- **Il codice è leggibile dentro l'app.** Quando qualcosa non torna, si apre il file e lo si legge; non è un jar.
- **Ogni app decide da sola quando aggiornare.** Il pin è nel repo dell'app, non nell'engine.
- **Un build solo.** I moduli dell'engine usano l'AGP e il Kotlin dell'app che li ospita, quindi non esiste il caso "l'app è passata a AGP 9 e l'engine no".

Il prezzo è che l'engine viene ricompilato dentro ogni app. Su questi moduli è questione di secondi, con la build cache anche meno.

## Installazione

```powershell
cd la-tua-app
git submodule add https://github.com/<owner>/fluid-engine.git android/engine
cd android/engine
git checkout engine-1.0.0
cd ..
powershell -ExecutionPolicy Bypass -File engine/tools/engine-install.ps1 -AppRoot .
```

`engine-install.ps1` fa tre cose e le dice tutte:

1. aggiunge al `settings.gradle` dell'app il blocco che include i moduli dell'engine;
2. scrive `engine.properties` con la versione agganciata e il canale;
3. stampa le righe di dipendenza da incollare nei moduli che useranno l'engine.

Il blocco che finisce nel `settings.gradle` è questo (e si può anche scrivere a mano):

```groovy
// --- fluid-engine (inizio) ---
def engineDir = file('engine')
if (engineDir.exists()) {
  ['engine-foundation', 'engine-ui', 'engine-storage',
   'engine-net', 'engine-config', 'engine-update', 'engine-widget'].each { name ->
    include ":$name"
    project(":$name").projectDir = new File(engineDir, name)
  }
}
// --- fluid-engine (fine) ---
```

I percorsi dei progetti sono piatti (`:engine-ui`, non `:engine:engine-ui`) di proposito: sono gli stessi che l'engine usa quando viene compilato da solo, quindi un `project(':engine-foundation')` dentro l'engine risolve allo stesso modo nei due casi.

## Dipendenze

Nel modulo che disegna l'interfaccia:

```groovy
dependencies {
  implementation project(':engine-ui')
}
```

`engine-ui` esporta già Compose, Material 3 e `engine-foundation` come `api`: non serve ridichiararli.

Gli altri, solo se servono:

```groovy
implementation project(':engine-storage')  // impostazioni su DataStore
implementation project(':engine-config')   // feature flag remoti
implementation project(':engine-update')   // aggiornamento in-app
implementation project(':engine-widget')   // widget Glance
```

## Il tema

`FluidTheme` prende un `EngineSettings` e il colore del marchio dell'app:

```kotlin
val brand = AccentPreset(
  name = "laMiaApp",
  label = "La mia app",
  light = Color(0xFF1F9E6E),
  dark = Color(0xFF2ED8A0),
)

@Composable
fun MiaApp(settings: EngineSettings) {
  FluidTheme(settings = settings, brand = brand) {
    // …
  }
}
```

Da qui in giù `MaterialTheme.colorScheme`, `MaterialTheme.typography` e `MaterialTheme.shapes` sono quelli dell'engine: i componenti Material standard si adeguano da soli, e i componenti `Fluid*` sono già a posto.

Se l'app ha già un suo modello di impostazioni — e di solito ce l'ha, con dentro molto altro — **non** sostituirlo: mappalo al bordo.

```kotlin
val engineSettings = appSettings.map { settings ->
  EngineSettings(
    themeMode = settings.themeMode.toEngine(),
    accentMode = settings.accentMode.toEngine(),
    customAccentName = settings.customAccentName,
    dynamicColorEnabled = settings.dynamicColorEnabled,
    amoledEnabled = settings.amoledEnabled,
  )
}
```

Se invece l'app è nuova e non ha ancora niente, `EngineSettingsStore` fa la persistenza al posto tuo:

```kotlin
val store = EngineSettingsStore(context)
val settings by store.settings.collectAsStateWithLifecycle(EngineSettings())
```

## Dependency injection

L'engine **non** dipende da Hilt. Le sue classi hanno costruttori normali, così funzionano anche in un'app che usa altro o niente. In un'app con Hilt il collegamento è un modulo solo, da copiare da `templates/EngineModule.kt.txt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

  @Provides @Singleton
  fun engineHttp(): EngineHttp = EngineHttp(userAgent = "MiaApp/1.0")

  @Provides @Singleton
  fun engineSettingsStore(@ApplicationContext context: Context) = EngineSettingsStore(context)

  @Provides @Singleton
  fun engineRemoteConfig(
    http: EngineHttp,
    @ApplicationContext context: Context,
  ): EngineRemoteConfig = EngineRemoteConfig(
    http = http,
    cache = EngineConfigCache(context),
    source = EngineConfigSource(
      manifestUrl = "https://raw.githubusercontent.com/<owner>/<repo>/main/manifest.json",
      applicationId = BuildConfig.APPLICATION_ID,
    ),
  )

  @Provides @Singleton
  fun appUpdater(http: EngineHttp, @ApplicationContext context: Context): AppUpdater =
    EngineAppUpdater(
      http = http,
      source = UpdateSource(
        manifestUrl = "https://raw.githubusercontent.com/<owner>/<repo>/main/manifest.json",
        applicationId = BuildConfig.APPLICATION_ID,
      ),
      installer = AndroidAppUpdateInstaller(context, http),
    )
}
```

## Verifica

```powershell
powershell -ExecutionPolicy Bypass -File engine/tools/engine-doctor.ps1 -AppRoot .
```

Controlla che il pin del submodule, `engine.properties`, `ENGINE_VERSION` e `EngineBuild.VERSION` dicano tutti la stessa cosa, e segnala se l'engine ha modifiche locali non committate — che è il modo in cui una copia condivisa smette silenziosamente di essere condivisa.

## Se non usi git submodule

Va bene lo stesso: basta che `android/engine` contenga l'engine e che `engine.properties` dica quale versione è. `engine-install.ps1` accetta `-Mode copy` e copia i file invece di aggiungere un submodule. In quel caso però `engine-update.ps1` non può fare un checkout: ricopia, e `engine-doctor.ps1` avvisa se qualcuno ha modificato la copia locale.
