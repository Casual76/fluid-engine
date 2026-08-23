# Mettere l'engine in un'app

## Prima di toccare qualsiasi cosa: l'app puo' ospitarlo?

Cinque controlli, in quest'ordine. Se uno fallisce, **dillo e fermati**: installare l'engine dove non
entra costa un pomeriggio di errori di build e non lascia niente di buono.

1. **Che Compose usa?** `grep -r "org.jetbrains.compose" gradle/libs.versions.toml` — se compare,
   e' **Compose Multiplatform** e `engine-ui` non entra: sono due linee di artefatti diverse
   (`org.jetbrains.compose` contro `androidx.compose`) e i runtime vanno in conflitto. Non forzarlo.
2. **Compose c'e'?** Un'app di sole View e XML puo' usare `engine-foundation`, `engine-net`,
   `engine-config`, `engine-update` — non `engine-ui`, che senza Compose non ha niente da tematizzare.
   Non e' solo inutile: `engine-ui` applica il plugin Compose, e in un'app che non lo dichiara nel
   suo build root il modulo non riesce nemmeno a **configurarsi**, quindi ferma tutto il build. In
   quel caso l'installazione va ristretta:

   ```powershell
   engine-install.ps1 -AppRoot . -Modules engine-update
   ```

   Le dipendenze si aggiungono da sole: `engine-update` porta con se' `engine-net` e
   `engine-foundation`.
3. **AGP e compileSdk.** L'engine compila contro l'API 36. Se l'app e' su un AGP che non la conosce,
   non serve un aggiornamento del toolchain: basta `engine.compileSdk=35` nel suo `gradle.properties`.
4. **minSdk >= 26.** Sotto, il codice dell'engine non gira; AGP lo direbbe comunque.
5. **La cartella `engine/` e' libera?** Non sempre: `universal_converter` chiama cosi' il suo motore
   di conversione. Usa `-EnginePath fluid-engine` e il resto degli script segue.

## App nuova

```powershell
cd la-tua-app
git submodule add <url-engine> android/engine
cd android
powershell -ExecutionPolicy Bypass -File engine\tools\engine-install.ps1 -AppRoot .
```

**Se il repo dell'engine e' ancora una cartella locale**, git rifiuta il trasporto `file`
(CVE-2022-39253) e il comando fallisce con `fatal: transport 'file' not allowed`:

```powershell
git -c protocol.file.allow=always submodule add "C:/VibeCoded Projects/fluid-engine" engine
```

Poi si aggancia il tag, che e' quello che il pin dice:

```powershell
cd engine ; git checkout engine-1.0.0 ; cd ..
```

Quando l'engine passa su GitHub, una volta per app: `git submodule set-url engine <url>`.

Senza git: `engine-install.ps1 -Mode copy -Source <cartella dell'engine>`.

## Dipendenze

```groovy
implementation project(':engine-ui')          // Groovy
```
```kotlin
implementation(project(":engine-ui"))         // Kotlin DSL
```

`engine-ui` esporta Compose, Material 3 e `engine-foundation` come `api`. Gli altri moduli solo se
servono davvero: aggiungere `engine-update` a un'app che non si aggiorna da sola significa
importarsi `REQUEST_INSTALL_PACKAGES` per niente.

Il build **root** dell'app deve dichiarare i plugin che i moduli dell'engine applicano senza
versione (`com.android.library`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`).
In un progetto Compose ci sono gia'.

Per compilare da riga di comando serve `local.properties` con `sdk.dir` — un repo appena clonato non
ce l'ha, e l'errore e' `SDK location not found`.

## Adottare il tema in un'app che ne ha gia' uno

**Non** cambiare i punti di chiamata. Tieni la funzione di tema che l'app ha gia' — stesso nome,
stessa firma — e riscrivine il corpo:

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

private fun ThemeMode.toEngine(): EngineThemeMode = when (this) {
  ThemeMode.System -> EngineThemeMode.SYSTEM
  ThemeMode.Light -> EngineThemeMode.LIGHT
  ThemeMode.Dark -> EngineThemeMode.DARK
}
```

Tre cose che fanno la differenza:

- **I colori del marchio si prendono dall'app, non si inventano.** Sono il `primary` del suo
  `lightColorScheme` e del suo `darkColorScheme`. Da quella coppia l'engine ricostruisce tutta la
  scala: e' quello che fa restare l'app riconoscibile dopo il cambio.
- **`ThemeMode` esiste due volte** (l'app ne ha uno suo): importa quello dell'engine con un alias,
  `import dev.antigravity.fluidengine.foundation.ThemeMode as EngineThemeMode`.
- **Il diff resta in un file.** Se il risultato non convince, `git checkout` di quel file e sei
  tornato indietro.

Le vecchie `lightColorScheme`/`darkColorScheme`/`Shapes` diventano codice morto: cancellale nello
stesso commit, o resteranno li' a far credere che siano ancora loro a decidere.

## Verifica, sempre nell'ordine

```powershell
powershell -ExecutionPolicy Bypass -File engine\tools\engine-doctor.ps1 -AppRoot .
```

```bash
./gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

## Se qualcosa non risolve

| sintomo | causa quasi sempre |
|---|---|
| `fatal: transport 'file' not allowed` | engine ancora locale: `-c protocol.file.allow=always` |
| `SDK location not found` | manca `local.properties` con `sdk.dir` |
| l'AGP non conosce l'API 36 | `engine.compileSdk=35` nel `gradle.properties` dell'app |
| `Plugin [id: '…compose'] was not found` | il root build dell'app non dichiara quel plugin |
| `Project with path ':engine-ui' could not be found` | manca il blocco in `settings.gradle`: rilancia `engine-install.ps1` |
| `Could not get unknown property 'engine'` | un modulo e' stato spostato: `versions.gradle` si applica da `${projectDir}/..` |
| classi Compose duplicate | l'app e' Compose Multiplatform, oppure forza una versione di Compose diversa |
