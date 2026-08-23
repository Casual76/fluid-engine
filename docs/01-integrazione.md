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

## Prima di installare: l'app puo' ospitarlo?

Cinque minuti di verifica evitano un pomeriggio di errori di build.

| serve | perche' |
|---|---|
| **Compose androidx** (`androidx.compose.*`) | `engine-ui` e' scritto su Material 3 androidx. Un'app in **Compose Multiplatform** (`org.jetbrains.compose`) non puo' ospitarlo: sono due linee di artefatti diverse e i runtime vanno in conflitto |
| **un AGP che conosca il compileSdk dell'engine** | l'engine compila contro l'API 36; un'app su un AGP piu' vecchio puo' abbassarlo (vedi Dipendenze) invece di rinunciare |
| **minSdk almeno 26** | e' il pavimento del codice dell'engine, non una preferenza |
| **una cartella libera** dove metterlo | se `engine/` e' gia' occupata, usa `-EnginePath` |

Un'app senza Compose (View e XML) puo' comunque usare `engine-foundation`, `engine-net`,
`engine-config` e `engine-update`, che non hanno UI. `engine-ui` no: senza Compose non c'e' niente
da tematizzare.

## Installazione

```powershell
cd la-tua-app
git submodule add https://github.com/<owner>/fluid-engine.git android/engine
cd android/engine
git checkout engine-1.0.0
cd ..
powershell -ExecutionPolicy Bypass -File engine/tools/engine-install.ps1 -AppRoot .
```

**Finche' il repo dell'engine e' solo una cartella locale**, git rifiuta il trasporto `file` (una
protezione contro submodule ostili, CVE-2022-39253) e serve dirglielo:

```powershell
git -c protocol.file.allow=always submodule add "C:/VibeCoded Projects/fluid-engine" engine
```

Quando l'engine finisce su GitHub, si ripunta il submodule una volta sola per app:

```powershell
git submodule set-url engine https://github.com/<owner>/fluid-engine.git
git commit -am "engine: submodule su GitHub"
```

Se il nome `engine` e' gia' preso — succede: universal_converter chiama cosi' il suo motore di
conversione — scegline un altro e dillo allo script:

```powershell
git -c protocol.file.allow=always submodule add <url> fluid-engine
powershell -ExecutionPolicy Bypass -File fluid-engine/tools/engine-install.ps1 -AppRoot . -EnginePath fluid-engine
```

`engine-install.ps1` fa tre cose e le dice tutte:

1. aggiunge al `settings.gradle` dell'app il blocco che include i moduli dell'engine;
2. scrive `engine.properties` con la versione agganciata, il canale e i moduli inclusi;
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

### Installare solo una parte dell'engine

```powershell
powershell -ExecutionPolicy Bypass -File engine/tools/engine-install.ps1 -AppRoot . -Modules engine-update
```

Il default e' "tutti", e per un'app Compose va bene. Per le altre no: `engine-ui` e `engine-widget`
applicano il plugin Compose, e in un'app che non lo dichiara nel proprio build root quei moduli non
riescono nemmeno a **configurarsi** — non e' un modulo inutilizzato che pesa, e' un build che non
parte. `-Modules` esiste per quel caso.

Le dipendenze fra moduli le chiude lo script: chiedere `engine-config` include anche
`engine-net`, `engine-storage` e `engine-foundation`. La scelta finisce in `engine.properties`
(`engine.modules`) e `engine-doctor.ps1` verifica che il `settings.gradle` la rispecchi.

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

In un'app con `build.gradle.kts` la sintassi e' quella Kotlin, e `engine-install.ps1` la stampa gia'
giusta a fine installazione:

```kotlin
implementation(project(":engine-ui"))
```

I moduli dell'engine leggono le proprie versioni di dipendenza da `versions.gradle`, non dal version
catalog dell'app: non serve aggiungere niente a `libs.versions.toml`.

Se l'app e' ferma a un AGP che non conosce l'API 36, abbassa il compileSdk dell'engine dal
`gradle.properties` dell'app invece di rinunciare:

```properties
engine.compileSdk=35
```

C'e' anche `engine.minSdk`, che pero' puo' solo **alzare** il pavimento: sotto 26 il codice
dell'engine non gira.

Per compilare da riga di comando serve un `local.properties` con `sdk.dir` nella radice del
progetto. Android Studio lo scrive da solo, ma un repo appena clonato non ce l'ha:

```properties
sdk.dir=C:/Android/Sdk
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

### Adottarlo in un'app che ha gia' un suo tema

Il modo meno invasivo e' tenere la funzione di tema che l'app gia' ha — stesso nome, stessa firma — e
riscriverne il **corpo** come una chiamata a `FluidTheme`. Il diff resta in un file solo, i punti di
chiamata non cambiano, e tornare indietro e' un `git checkout` di quel file.

```kotlin
private val AppBrand = AccentPreset("app", "App", Color(0xFF3B5BDB), Color(0xFFB6C4FF))

@Composable
fun MioTemaApp(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
  FluidTheme(
    settings = EngineSettings(
      themeMode = themeMode.toEngine(),
      accentMode = if (dynamicColor) AccentMode.DYNAMIC else AccentMode.BRAND,
      dynamicColorEnabled = dynamicColor,
    ),
    brand = AppBrand,
    content = content,
  )
}
```

I due colori del marchio si prendono dal `lightColorScheme`/`darkColorScheme` che l'app aveva prima:
sono il suo `primary`, chiaro e scuro. Da li' l'engine ricostruisce l'intera scala di superfici.

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

Va bene lo stesso:

```powershell
powershell -ExecutionPolicy Bypass -File <engine>/tools/engine-install.ps1 -AppRoot . -Mode copy -Source "C:/VibeCoded Projects/fluid-engine"
```

Copia i sorgenti dell'engine dentro l'app (senza `.git`, senza `build`) e poi fa il resto come al
solito. Aggiornare significa rilanciare lo stesso comando: `engine-update.ps1` su una copia non ha
un tag da agganciare, e infatti te lo dice invece di provarci. `engine-doctor.ps1` segnala che sei
in modalita' copia, cosi' nessuno si aspetta un pin che non c'e'.
