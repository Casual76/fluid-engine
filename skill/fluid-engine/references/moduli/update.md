# engine-update

L'**aggiornamento in-app per app distribuite fuori da uno store**: controlla il manifest, scarica l'APK, lo verifica e lo installa con `PackageInstaller`. Due oggetti in due file del pacchetto
`dev.antigravity.fluidengine.update`: `EngineAppUpdater` (il *controllo*: c'e' una release piu' nuova?) e `AndroidAppUpdateInstaller` (l'*installazione*: scarica, verifica, committa la sessione).
Sono separati perche' falliscono per motivi diversi e si mostrano in posti diversi: un controllo gira in silenzio all'avvio e si puo' ignorare, un'installazione si prende la schermata fino alla fine.

Il contratto (`AppUpdater`, `AvailableAppUpdate`, `AppUpdateInstallState`, `UpdateChannel`) sta in `engine-foundation` ([foundation](foundation.md)); la rete e' [net](net.md); il manifest e' lo stesso che usa la
config remota ([config](config.md)). **Il lato UI** — la sezione delle impostazioni, il foglio con la nota di rilascio, `rememberFluidUpdateController` — sta in `engine-ui`, **non** qui: vedi
[`../componenti/impostazioni.md`](../componenti/impostazioni.md). Il controllore parla solo i tipi di foundation, quindi un'app con un updater suo usa lo stesso disegno.
Modello mentale: **manifest → `check` → `AvailableAppUpdate` → `install` → flusso di stati**. Catalogo: [`../api/update.md`](../api/update.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| aggiornamento in-app da un manifest + GitHub Releases | `EngineAppUpdater(http, UpdateSource(...), AndroidAppUpdateInstaller(context, http))` |
| la schermata «Controlla aggiornamenti» gia' fatta | `rememberFluidUpdateController` + `fluidUpdateSection` in `engine-ui` ([impostazioni](../componenti/impostazioni.md)) |
| controllare senza installare (avviso, badge) | `updater.check(versione, canale, versioneIgnorata)` |
| un host di release che non e' GitHub | `UpdateSource(downloadUrlTemplate = "https://.../{tag}/{asset}")` (ma serve comunque `repository` nel manifest: vedi trappole) |
| scaricare un APK **senza installarlo** (il telefono che porta l'APK dell'orologio) | `AndroidAppUpdateInstaller.download(update, onProgress)` |
| installare un APK **gia' su disco** (ricevuto da un altro dispositivo, scelto in debug) | `AndroidAppUpdateInstaller.installFile(file, expectedVersionName, expectedPackageName, sha256)` |
| un'altra sorgente di aggiornamenti (non il manifest dell'engine) | implementare `AppUpdater` di foundation; l'UI non cambia |
| provare la logica del controllo senza dispositivo | `EngineHttp` finto + `AppUpdateInstaller` finto: i due sono interfacce/`open` apposta |
| un'app che finisce su Google Play | **non** includere questo modulo (dichiara `REQUEST_INSTALL_PACKAGES`) |

## `EngineAppUpdater` e `UpdateSource` (il controllo)

**Cos'e'.** Legge il manifest, sceglie la release giusta per il canale, costruisce l'URL di download e decide se e' piu' nuova della versione in esecuzione. Implementa `AppUpdater`.

**Firma essenziale.**

```kotlin
data class UpdateSource(
  val manifestUrl: String,                   // puo' essere lo stesso URL della config remota
  val applicationId: String,
  val downloadUrlTemplate: String = GithubReleaseTemplate,
) { companion object { const val GithubReleaseTemplate: String } }   // https://github.com/{owner}/{repo}/releases/download/{tag}/{asset}

class EngineAppUpdater(
  private val http: EngineHttp,
  private val source: UpdateSource,
  private val installer: AppUpdateInstaller,
) : AppUpdater {
  override suspend fun check(currentVersionName: String, channel: UpdateChannel, ignoredVersion: String): Result<AvailableAppUpdate?>
  override fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState>   // delega all'installer
}

interface AppUpdateInstaller { fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState> }
```

**Uso** (il costruttore del sample, `sample/.../update/FluidGlassUpdater.kt`).

```kotlin
val http = EngineHttp(userAgent = "MiaApp/${BuildConfig.VERSION_NAME}")
val updater: AppUpdater = EngineAppUpdater(
  http = http,
  source = UpdateSource(manifestUrl = MANIFEST_URL, applicationId = context.packageName),
  installer = AndroidAppUpdateInstaller(context.applicationContext, http),
)

val found: AvailableAppUpdate? = updater.check(currentVersionName = "1.0.0").getOrNull()   // null: niente di nuovo, oppure errore
```

**Comportamento — le regole di `check`.**
- `Result.failure` per un errore (rete, HTTP non 2xx, JSON non valido); **`Result.success(null)` per «nessun aggiornamento»**. Sono esiti diversi apposta: l'assenza di un aggiornamento non e' un guasto.
- **Scelta della release per canale.**

  | canale | release letta | e' proposta se… |
  |---|---|---|
  | `STABLE` | `app.stable` | `isStableVersionNewer(versione, corrente)`: nessun `-` nella versione offerta, e piu' alta della corrente |
  | `BETA` | `app.beta`, **altrimenti `app.stable`** | `compareVersions(versione, corrente) > 0` (accetta anche una pre-release piu' alta) |

  Il ripiego dalla beta alla stabile evita che chi e' sul canale beta resti per sempre senza aggiornamenti dopo che la linea beta e' stata ritirata. Nel manifest di Fluid Glass `stable` e `beta` puntano alla stessa release 2.0.0.
- `ignoredVersion`: se la versione offerta e' **uguale (stringa)** a quella ignorata, `check` restituisce `null` («salta questa versione»).
- **URL di download** dalla `downloadUrlTemplate` con `{owner}`, `{repo}` (da `app.repository`), `{tag}` (`releaseTag`) e `{asset}` (`apkAsset`). Un `apkAsset` vuoto diventa `"<applicationId>-<versione>.apk"`.
  Una *template* e non un host cablato perche' un'app pubblicata altrove sostituisca una stringa invece di fare un fork dell'updater.
- `sizeBytes` e `sha256` della release finiscono in `AvailableAppUpdate` e vengono usati dall'installer (peso atteso nel download, checksum prima dell'installazione).

**Trappole.**
- **Senza `repository` nel manifest `check` risponde «nessun aggiornamento»**, non un errore (`toAvailableUpdate` restituisce `null`): un manifest scritto a mano con `app.repository` mancante o incompleto (manca `repoOwner`/`repoName`)
  non aggiorna mai nessuno e non lo dice. Vale anche con una `downloadUrlTemplate` che non usa `{owner}`/`{repo}`: la repository serve lo stesso.
- **`UpdateSource.applicationId` serve solo per il nome dell'APK di ripiego.** L'updater **non** confronta `app.id`/`app.packageName` del manifest con l'app in esecuzione: se punti l'app a un manifest di un'altra app, `check` propone
  l'altra app. Salva l'installer (rifiuta un APK con un altro pacchetto), ma il controllo non lo saprebbe.
- **La versione corrente conta.** `rememberFluidUpdateController(..., currentVersion = BuildConfig.VERSION_NAME.substringBefore("-"))` (come nel suo KDoc) toglie il suffisso beta: una build `2.0.0-beta.4` si presenta come `2.0.0`
  e **non vedra' mai la stabile `2.0.0`** (uguale, non piu' nuova). Passa il `versionName` intero se la beta deve ricevere la stabile omonima.
- **Un manifest con `version` diversa dal `versionName` dell'APK blocca l'installazione** («Versione APK inattesa»): vedi sotto.
- `compareVersions` confronta le pre-release come testo: `2.0.0-beta.10` < `2.0.0-beta.9` ([foundation](foundation.md)), quindi dopo `beta.9` il canale beta non propone la `beta.10`.
- Il manifest viene riletto **a ogni `check`**: nessuna cache, nessun ETag.

**Non usarlo quando.** Distribuisci su uno store (l'aggiornamento e' dello store).

## `AndroidAppUpdateInstaller` (l'installazione)

**Cos'e'.** Scarica l'APK nella cache dell'app, lo verifica e lo installa con una sessione `PackageInstaller`, raccontando ogni passo come `AppUpdateInstallState`.

**Firma essenziale.**

```kotlin
class AndroidAppUpdateInstaller(private val context: Context, private val http: EngineHttp = EngineHttp()) : AppUpdateInstaller {
  override fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState>
  suspend fun download(update: AvailableAppUpdate, onProgress: (AppUpdateInstallState.Downloading) -> Unit = {}): File
  fun installFile(file: File, expectedVersionName: String,
                  expectedPackageName: String = context.packageName, sha256: String = ""): Flow<AppUpdateInstallState>
}
```

**Uso.**

```kotlin
updater.install(update).collect { step ->
  when (step) {
    is AppUpdateInstallState.Downloading -> mostraProgresso(step.progress)
    is AppUpdateInstallState.Verifying, is AppUpdateInstallState.Installing -> mostraAttesa()
    is AppUpdateInstallState.AwaitingUserAction -> mostraAttesa()      // Android mostra la sua conferma
    is AppUpdateInstallState.Installed -> fatto()
    is AppUpdateInstallState.Error -> if (step.kind == AppUpdateErrorKind.InstallPermission) chiediPermesso() else errore(step.message)
  }
}
```

**Comportamento — la sequenza di `install`.**
1. `Verifying("Preparazione aggiornamento...")`.
2. **Permesso d'installare, prima del download.** Se `canRequestPackageInstalls()` e' falso apre `ACTION_MANAGE_UNKNOWN_APP_SOURCES` per questo pacchetto, emette `Error(..., kind = InstallPermission)` e **il flusso finisce**: non riprende da solo,
   il prossimo tentativo (dopo aver concesso) passa. Chiesto *prima* del download perche' scoprire dopo 30 MB che non si puo' installare e' lo stesso risultato con un download sprecato davanti. Su alcuni dispositivi (Wear OS fra questi)
   la schermata e' nascosta e il permesso va dato da `adb` (`appops ... REQUEST_INSTALL_PACKAGES allow`, comando di Android e non dell'engine).
3. **Download** in `cacheDir/engine_updates/<apkAsset>` (se `apkAsset` e' vuoto, `update-<versione>.apk`) con `EngineHttp.download(url, target, expectedBytes = update.sizeBytes)`: `.part` poi rinomina, **verifica del peso**, redirect a mano ([net](net.md)).
   Emette `Downloading(progress, downloadedBytes, totalBytes)` a ogni lettura da 8 KiB (con `trySend`: se il consumatore e' lento qualche aggiornamento di avanzamento puo' andare perso). Il file **resta in cache** dopo l'installazione.
4. `Verifying("Verifica APK...")` e **tre controlli prima del commit**, nell'ordine: **checksum** (solo se `sha256` non e' vuoto; confronto senza maiuscole/spazi), **leggibilita'** dell'APK (`getPackageArchiveInfo`), **pacchetto e versione**
   (`rejectApk`). Un APK che non e' questa app, o non e' la versione che il manifest annunciava, e' un errore della release o il file di qualcun altro: il guasto senza questi controlli sarebbe una richiesta di installazione di un pacchetto
   estraneo, la cosa peggiore che un auto-aggiornatore possa fare.
5. `Installing("Installazione aggiornamento...")`: sessione `MODE_FULL_INSTALL`, `setAppPackageName`, da Android 12 `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` (l'aggiornamento puo' essere senza conferma *se Android lo consente*), da Android 13
   `PACKAGE_SOURCE_LOCAL_FILE`; il file e' scritto con `fsync` e committato con un `PendingIntent` (mutabile da Android 12) verso `AppUpdateInstallResultReceiver`.
6. Dal ricevitore: `STATUS_PENDING_USER_ACTION` → apre la conferma di sistema (`FLAG_ACTIVITY_NEW_TASK`) ed emette `AwaitingUserAction("Conferma l'installazione sul dispositivo.")`; `STATUS_SUCCESS` → `Installed(filePath)` (il percorso dell'APK);
   ogni altro stato → `Error(...)` con il messaggio dell'elenco sotto. `Installed` ed `Error` chiudono il flusso.

**Gli errori, con le parole vere** (sono in italiano e fisse):

| causa | messaggio | `kind` |
|---|---|---|
| permesso di installare mancante | «Abilita l'installazione da questa app nelle impostazioni di Android e riprova.» | `InstallPermission` |
| download fallito (rete, HTTP, peso) | il messaggio dell'eccezione («Download non riuscito (404).», «File incompleto: N byte invece di M.»…), o «Download aggiornamento non riuscito.» | `Failed` |
| `sha256` diverso | «Il file ricevuto non corrisponde a quello pubblicato.» | `Failed` |
| APK illeggibile | «Android non riesce a leggere l'APK scaricato.» | `Failed` |
| pacchetto diverso | «L'APK scaricato non appartiene a questa app.» | `Failed` |
| `versionName` diverso dal manifest | «Versione APK inattesa: X.» | `Failed` |
| `STATUS_FAILURE_ABORTED` | «Installazione interrotta: annullata, oppure bloccata da Play Protect.» | `Failed` |
| `STATUS_FAILURE_BLOCKED` | «Android ha bloccato l'installazione dell'APK.» | `Failed` |
| `STATUS_FAILURE_CONFLICT` | «Conflitto di firma o versione con l'app installata.» | `Failed` |
| `STATUS_FAILURE_INCOMPATIBLE` | «L'APK non e' compatibile con questo dispositivo.» | `Failed` |
| `STATUS_FAILURE_INVALID` | «Android considera l'APK non valido o non installabile.» | `Failed` |
| `STATUS_FAILURE_STORAGE` | «Spazio insufficiente per completare l'installazione.» | `Failed` |
| altro | il messaggio di sistema, o «Installazione non riuscita.» | `Failed` |

**Comportamento — `download` e `installFile` separati (dalla 2.10.0).** `download` restituisce il `File` senza installare (stessa cartella `engine_updates`); `installFile` fa gli stessi controlli (checksum se dato, pacchetto, versione) e la stessa sessione per un APK
arrivato in un altro modo. `expectedPackageName` e' **questa app** di serie, l'unico pacchetto che un auto-aggiornatore dovrebbe installare; per l'APK di un altro pacchetto lo si dice esplicitamente. `install()` e' rimasto identico.

**Trappole.**
- **`Installed` puo' non arrivare mai.** Quando l'APK sostituisce l'app che sta girando, Android termina il processo: un auto-aggiornamento non e' garantito di poter raccontare il successo. Non costruire logica che dipende dal ricevere `Installed` nello stesso processo; se serve
  un «aggiornato!», controllalo al lancio dopo (versione corrente piu' alta della precedente).
- **`ABORTED` e' ambiguo.** Android lo riporta sia quando l'utente tocca Annulla sia quando **Play Protect** blocca l'installazione, e non c'e' modo di distinguere (verificato su un dispositivo reale); per questo il messaggio dice entrambe le cose.
- **`CONFLICT` = firma diversa.** Una build firmata con un'altra chiave (una debug di Android Studio) non si aggiorna con l'APK dello store e viceversa: l'installazione fallisce con «Conflitto di firma o versione». La chiave del progetto e' una sola per tutte le app.
- **`versionName` dell'APK e `version` del manifest devono coincidere come stringhe** (`2.0.0-beta.4` e `2.0.0-beta.4`, non `v2.0.0`). Una `versionName` **vuota** nell'APK **non** e' un motivo di rifiuto (alcuni strumenti di firma la lasciano fuori); una diversa si'.
- **Il flusso non riprende dopo il permesso.** `Error(InstallPermission)` chiude il flusso: la schermata deve ricominciare l'installazione quando l'utente torna (il controllore di `engine-ui` lo fa: lo stesso tocco riprova).
- Il ricevitore **ingoia** un errore di `startActivity` sulla conferma (`runCatching`) ed emette comunque `AwaitingUserAction`: se la conferma non compare (app in background su un Android che lo vieta) la schermata resta in attesa senza errore.
- I messaggi di stato (`Preparazione aggiornamento...`, `Verifica APK...`, ...) e gli errori sono **hardcoded in italiano**: l'UI in un'altra lingua li mostra cosi' se passa il `message` tale e quale (il controllore di `engine-ui` mappa gli stati ma riporta il `message`).
- **Play Store.** `REQUEST_INSTALL_PACKAGES` (dichiarato nel manifest del modulo) rende l'app non pubblicabile su Google Play senza una motivazione accettata. Per app su Play togli il modulo.

**Non usarlo quando.** L'app e' sullo store, o l'APK non e' firmato con la stessa chiave dell'app installata (l'installazione non puo' riuscire).

**Versione.** `download`/`installFile`/`sha256`: 2.10.0. `rejectApk` estratto e testato, `ABORTED` che nomina Play Protect: 1.1.0. `AppUpdateErrorKind.InstallPermission` (il permesso come stato a parte, non «aggiornamento non riuscito»): 3.0.1.

## `AppUpdateInstallResultReceiver` e `AppUpdateInstallSessionRegistry`

**Cos'e'.** Il ricevitore dichiarato nel manifest **del modulo** (non esportato): l'app che dipende dal modulo non copia nessun `<receiver>`. Riceve gli esiti di `PackageInstaller` e li consegna al flusso che li aspetta tramite il registro
(`MutableSharedFlow` per sessione, con **replay di 8**: il sistema puo' consegnare il primo evento prima che il flusso abbia cominciato a raccogliere). Un'app non li chiama; servono a sapere cosa c'e' nel manifest fuso
(`REQUEST_INSTALL_PACKAGES`, `INTERNET`, il `<receiver>`) e a scrivere test.

## Token, costanti, Defaults

| nome | valore |
|---|---|
| `UpdateSource.GithubReleaseTemplate` | `https://github.com/{owner}/{repo}/releases/download/{tag}/{asset}` |
| cartella del download | `cacheDir/engine_updates/` |
| `AppUpdateInstallResultReceiver.ACTION_INSTALL_STATUS` | `dev.antigravity.fluidengine.action.UPDATE_INSTALL_STATUS` |
| `AppUpdateInstallResultReceiver.EXTRA_SESSION_ID` | `dev.antigravity.fluidengine.extra.UPDATE_SESSION_ID` |
| permessi dichiarati dal modulo | `REQUEST_INSTALL_PACKAGES`, `INTERNET` (piu' quelli di `engine-net`) |

## Cosa e' verificato dai test

- `ApkRejectionTest` (5): un APK di un altro pacchetto e' rifiutato; una versione diversa da quella annunciata e' rifiutata; quello giusto passa; **una versione assente nell'APK non e' un motivo di rifiuto**; un pacchetto assente e' rifiutato.
- `ChecksumRejectionTest` (4): una attesa vuota non si controlla; il confronto ignora maiuscole e spazi; un checksum diverso e' rifiutato; `sha256Of` su un file e' lo SHA-256 standard.
- **Non verificato:** `check` (scelta del canale, `ignoredVersion`, URL), la sequenza dei passi di `install`, il ricevitore, il registro, il permesso. Sono le parti che servono un dispositivo, e vanno guardate su uno (`rejectApk` e `rejectChecksum`
  sono `internal` e fuori dal flusso proprio per potersi provare senza Android). L'UI che li consuma ha i suoi test in `engine-ui` (`FluidUpdateControllerTest`, `FluidUpdateModelTest`).

## Dove guardare

- `engine-update/src/main/kotlin/dev/antigravity/fluidengine/update/EngineAppUpdater.kt` — `UpdateSource`, `EngineAppUpdater.check` (canali, URL), `AppUpdateInstaller`.
- `.../AndroidAppUpdateInstaller.kt` — `install`, `download`, `installFile`, il permesso, la sessione `PackageInstaller`, il ricevitore, il registro, `rejectApk`, `rejectChecksum`, `sha256Of`, i messaggi d'errore.
- `engine-update/src/main/AndroidManifest.xml` — permessi e `<receiver>`.
- `engine-foundation/.../AppUpdate.kt` — il contratto: [foundation](foundation.md).
- `sample/src/main/kotlin/dev/antigravity/fluidengine/sample/update/FluidGlassUpdater.kt` — costruzione completa e URL del manifest.
- `engine-ui/.../fluid/FluidUpdateController.kt`, `FluidUpdateModel.kt` — la schermata, [impostazioni](../componenti/impostazioni.md).

## Dipendenze e configurazione minima

**Dipendenze del modulo.** `api` su `engine-foundation` e `engine-net` (non su `engine-storage`: l'aggiornamento non salva niente). Nessuna libreria esterna. Il manifest porta nell'app `REQUEST_INSTALL_PACKAGES`, `INTERNET` e il `<receiver>`.
`tools/engine-install.ps1 -AppRoot . -Modules engine-update` include foundation e net; **funziona anche in un'app di sole View**, senza Compose. Nel `build.gradle`:

```groovy
implementation project(':engine-update')   // aggiornamento in-app
```

**Configurazione minima.** (1) un manifest pubblicato con `app.repository`, `stable` (`version`, `releaseTag`, `apkAsset`, `sizeBytes`) e, se vuoi il controllo, `sha256`; una release con l'APK al tag e al nome indicati, la cui `versionName` e' la `version` del manifest;
(2) un `EngineAppUpdater` costruito come sopra (template `templates/EngineModule.kt.txt`, funzione `appUpdater`); (3) per la schermata, `rememberFluidUpdateController(updater, appScope, versione)` + `fluidUpdateSection` in `engine-ui`.
L'app non deve dichiarare nient'altro: permessi e ricevitore arrivano col modulo. Il primo aggiornamento chiede **una volta** all'utente il permesso di installare da quest'app.
