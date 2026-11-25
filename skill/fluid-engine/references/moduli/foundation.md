# engine-foundation

Il modulo dei **tipi**: le cose che gli altri moduli (e l'app) si passano senza sapere come sono fatte. Versioni e loro
confronto, il manifest remoto e la sua lettura, i flag, le impostazioni d'aspetto, il contratto dell'aggiornamento
in-app, lo stato di sincronizzazione. Pacchetto `dev.antigravity.fluidengine.foundation`.

**Niente Compose, niente Android UI, niente `Context`.** Nessun file del modulo importa `android.*` o `androidx.*`:
e' Kotlin puro (piu' coroutine e `kotlinx-serialization-json`, che esporta come `api`), quindi si prova sulla JVM e
ci stanno sopra sia il design system (`engine-ui`) sia i moduli senza UI (`engine-net`, `engine-config`, `engine-update`).
Modello mentale: **foundation dice cosa e', gli altri moduli dicono come si fa**. Le implementazioni stanno altrove
(`engine-storage` salva, `engine-net` scarica, `engine-config` applica il manifest, `engine-update` installa), e il disegno
sta in `engine-ui`: foundation e' la lingua comune, quindi l'unica cosa che ogni altro modulo dell'engine importa.

Schede dei moduli che lo usano: [storage](storage.md), [net](net.md), [config](config.md), [update](update.md),
[widget](widget.md). Catalogo completo delle firme: [`../api/foundation.md`](../api/foundation.md).

## Scegliere in fretta

Per gli indirizzi delle pagine, `FluidAddressPage` associa a un tipo Kotlin il nome, i parametri,
il genitore, la visibilità e la disponibilità. `FluidAddressParameters` contiene i segmenti del
percorso e i parametri della query già decodificati. `FluidAddressTable` emette il link con
`addressOf` e risolve una `FluidAddressRequest` in un `FluidAddressResult`. La disponibilità
è `FluidAddressAvailability`. La stessa verifica pura del pacchetto e della firma è disponibile
con `fluidAddressCallerTrusted`. La [scheda delle pagine](../componenti/pagine.md) mostra il collegamento ad Android.

| ho bisogno di… | uso |
|---|---|
| sapere con quale engine e' stata compilata l'app | `EngineBuild.VERSION` (costante compilata, non letta a runtime) |
| ordinare due versioni | `compareVersions(left, right)` (numerica per segmento, pre-release prima della release) |
| «questa release stabile e' piu' nuova della mia?» | `isStableVersionNewer(candidate, current)` — **non** `compareVersions(...) > 0`: quello offrirebbe anche le `-beta` |
| sapere se l'engine dell'app e' indietro rispetto al manifest | `engineCompatibility(...)` o, con un `EngineConfig`, `config.compatibility()` |
| leggere il testo di un manifest | `EngineManifestParser.parse(raw)` — non una classe `@Serializable` tua |
| dichiarare un feature flag | `EngineFlag(key, default)` dove serve; si legge con `EngineRemoteConfig` ([config](config.md)) |
| sapere cosa vale per **questa** app dal manifest | `resolveEngineConfig(manifest, applicationId, fetchedAt)` (lo chiama `EngineRemoteConfig`, di solito non a mano) |
| dire al design system tema, accento, aptica | `EngineSettings` (mappa i tuoi settings con un `map { }`, non adottare il modello) |
| salvare quelle impostazioni | `EngineSettingsStore` in `engine-storage` ([storage](storage.md)); foundation non salva niente |
| mostrare «aggiornato 5 min fa / offline / errore» | `SyncStatus` + `SyncState`; la riga la disegna `engine-ui` (`FluidSync*`) |
| un'altra implementazione dell'aggiornamento in-app (non GitHub) | implementare `AppUpdater`; l'UI parla solo questo contratto |
| un campo di configurazione **senza** default sicuro | non e' un flag: va nella build (vedi `EngineFlag`) |
| un numero con la sua unità, convertito senza `Double` (F8.3) | `FluidQuantity(valore, FluidUnits.Kilometer).to(FluidUnits.Mile)`, `FluidUnits.convert(…)` |
| scrivere o leggere «12,5 km», «€12.50» nella lingua | `FluidUnitFormat.format(quantità, locale)`, `FluidUnitFormat.parse(testo, locale)` |
| le regole di una password, la forza, una password forte (F8.3) | `FluidPasswordRules.parse(…)`, `FluidPasswordStrength.estimate(…)`, `rules.generate()` |
| una ricorrenza («ogni lun e gio fino al 20 dicembre») come dato, le sue prossime date (F8.8) | `FluidRecurrenceRule(…)`, `regola.occurrences(inizio, limit = 5)` |
| la frase di una ricorrenza in italiano o in inglese | `FluidRecurrenceText.describe(regola, inizio, locale)` |
| leggere o scrivere una `RRULE` (RFC 5545, EventKit) | `FluidRecurrenceRule.parseRRule(testo)`, `regola.toRRule()` |
| un intervallo di giorni «dal… al…», anche sull'ora legale | `FluidDateRange(inizio, fine)`: `dayCount`, `startInstant(zona)`, `endInstantExclusive(zona)` |
| il primo giorno della settimana della lingua | `FluidDates.firstDayOfWeek(locale)`, `FluidDates.weekdays(locale)` |
| se mostrare il benvenuto o le novita' all'apertura (F8.11) | `fluidWelcomeKind(versione, vista, installazioneNuova, haNovita)` |
| le novita' di una versione dal manifest | `manifest.releaseNotesFor(versione)`, `AppRelease.releaseNotes()`, `fluidReleaseNotesFromChangelog(testo)` |
| lo stato di un permesso e il passo del tasto (F8.11) | `fluidPermissionStatus(…)`, `fluidPermissionAfterRequest(…)`, `fluidPermissionStep(…)` |
| la memoria della prima volta (interfaccia) | `FluidFirstUseMemory`, le chiavi in `FluidFirstUseKeys`; su disco `EngineFirstUseStore` di `engine-storage` |

## Versioni: `EngineBuild`, `compareVersions`, `isStableVersionNewer`, `EngineCompatibility`

**Cos'e'.** `EngineBuild.VERSION` e' la versione dell'engine **compilata dentro l'app**. E' compilata (non letta da un file) perche'
e' esattamente il fatto che un controllo remoto deve poter confrontare: il manifest dice «sotto la 1.4.0 si deve aggiornare» e
ogni app risponde onestamente. Il file `ENGINE_VERSION` alla radice dell'engine, `EngineBuild.VERSION` e il tag git sono tre
copie dello stesso numero, mosse insieme da `tools/engine-release.ps1` e verificate da `tools/engine-doctor.ps1`: non toccarle a mano.

**Firma essenziale.**

```kotlin
object EngineBuild { const val VERSION: String }            // "3.0.4" nel repo di oggi
fun compareVersions(left: String, right: String): Int
fun isStableVersionNewer(candidate: String, current: String): Boolean
enum class EngineCompatibility { OK, UPDATE_RECOMMENDED, UPDATE_REQUIRED }
fun engineCompatibility(
  engineVersion: String = EngineBuild.VERSION,
  minimumVersion: String?,
  recommendedVersion: String?,
): EngineCompatibility
```

**Uso.**

```kotlin
compareVersions("1.10.0", "1.9.0") > 0          // vero: numerico, non testuale
compareVersions("1.2.0-beta1", "1.2.0") < 0     // la pre-release precede la release
compareVersions("1.2", "1.2.0") == 0            // i segmenti mancanti valgono zero
isStableVersionNewer("1.3.0-beta1", "1.2.0")    // falso: lo stabile non offre mai una pre-release
```

**Comportamento.**
- `compareVersions` separa al **primo** `-`: la parte prima e' numerica a segmenti (`1.2.3.4` va bene, quanti segmenti si
  vuole), quella dopo e' la pre-release.
- Un segmento non numerico vale **0**, senza eccezioni (`compareVersions("1.0.0", "not-a-version") > 0` e' un test).
- A parita' di segmenti: release > pre-release; fra due pre-release decide il confronto **testuale** della stringa dopo il `-`.
- `engineCompatibility`: `UPDATE_REQUIRED` se l'engine e' sotto `minimumVersion`, altrimenti `UPDATE_RECOMMENDED` se e' sotto
  `recommendedVersion`, altrimenti `OK`. Un valore `null` o vuoto vale «nessun vincolo».
- `UPDATE_REQUIRED` e' la leva che permette a **un file ospitato** di ritirare una build vecchia da tutte le app: l'app non puo'
  ricompilarsi, ma puo' smettere di fidarsi di un contratto che non implementa piu' e dirlo, invece di rompersi in un modo che l'utente
  deve interpretare. Cosa farne (schermata bloccante, avviso) e' dell'app: l'engine riporta il fatto.

**Trappole.**
- **`beta.10` viene prima di `beta.9`.** La parte dopo il `-` si confronta come testo: `"beta.10"` < `"beta.9"`. Le pre-release
  della galleria arrivano a `beta.4`, quindi nessuno se n'e' accorto; chi usa piu' di nove beta deve zero-paddare (`beta.09`) o
  non mettere la beta in un manifest letto da `compareVersions`.
- **Un prefisso `v` azzera il primo segmento**: `"v1.2.0"` e' `0.2.0`. Il manifest scrive `1.2.0`, non `v1.2.0`.
- **`isStableVersionNewer` e' `false` per qualunque candidato con un `-`**, anche se chi chiede e' a sua volta su una pre-release.
  Il confronto fra pre-release lo fa `compareVersions` (e lo usa `EngineAppUpdater` per il canale beta).
- `EngineConfig.compatibility()` confronta `EngineBuild.VERSION` di **questo** modulo; `EngineRemoteConfig.compatibility()` usa invece
  `EngineConfigSource.engineVersion`. Sono la stessa cosa finche' non si sovrascrive `engineVersion` (utile solo nei test).

**Versione.** Tutto dalla 1.0; `isStableVersionNewer` e la regola «mai una pre-release» dalla 1.0 (`EngineVersionTest`).

## Il manifest remoto: `EngineManifest` e le sue parti

**Cos'e'.** Il **file unico** che ogni app dell'engine legge, con due metà indipendenti apposta. `app` e' il puntatore alla release
che serve all'aggiornamento in-app, **nella forma che il Pampa Store pubblica gia'** (un manifest di store esistente funziona
senza toccarlo). `engine` e' cio' che puo' cambiare il comportamento di una build gia' installata: flag, pavimento di versione,
avviso, kill switch. Tutto e' opzionale: solo `app` = manifest di store, solo `engine` = file di controllo, entrambi = quello a cui
punta di solito un'app. Modificare la sezione `engine` cambia ogni app che legge il file, al loro prossimo controllo, senza ricompilare.

**Firma essenziale.**

```kotlin
data class EngineManifest(val app: AppReleases? = null, val engine: EngineSection? = null)

data class AppReleases(
  val id: String = "", val packageName: String = "",
  val repository: ReleaseRepository? = null,
  val stable: AppRelease? = null, val beta: AppRelease? = null,
)
data class ReleaseRepository(val owner: String, val name: String)
data class AppRelease(
  val version: String, val changelog: String = "", val releaseTag: String = "",
  val apkAsset: String = "", val sizeBytes: Long = 0L, val releaseDate: String = "",
  val sha256: String = "",              // facoltativo; vuoto = non verificato (dalla 2.10.0)
)

data class EngineSection(
  val schema: Int = EngineManifestSchema,
  val minimumVersion: String? = null, val recommendedVersion: String? = null,
  val notice: String? = null,
  val flags: Map<String, Boolean> = emptyMap(),
  val killSwitch: EngineKillSwitch = EngineKillSwitch(),
  val overrides: Map<String, EngineOverride> = emptyMap(),   // chiave: application id
)
data class EngineOverride(/* gli stessi campi, tutti opzionali; killSwitch e' nullable */)
data class EngineKillSwitch(val enabled: Boolean = false, val message: String? = null)

object EngineManifestParser { fun parse(raw: String): EngineManifest }
const val EngineManifestSchema: Int = 1
```

**Uso.**

```kotlin
val manifest = EngineManifestParser.parse(raw)               // lancia se il testo non e' un oggetto JSON
manifest.app?.stable?.version                                // "2.0.0"
manifest.engine?.flags?.get("nuovaAgenda")                   // true / false / null
```

**Comportamento (il parser).**
- **A mano, senza plugin.** Passa dall'API `JsonElement`: `@Serializable` trascinerebbe il compilatore di serializzazione in ogni
  app che ospita l'engine, per un documento di quindici campi. Per lo stesso motivo il modulo non ha classi `@Serializable`.
- **Campi sconosciuti ignorati** (`ignoreUnknownKeys`, `isLenient`): un manifest piu' nuovo si puo' servire a un'app vecchia. Per questo
  `schema` si alza **solo** se cambia il *significato* di un campo esistente; un campo opzionale in piu' non conta.
  (Oggi nessun codice confronta `schema` con `EngineManifestSchema`: viene letto e riportato in `EngineConfig.schema`, non decide niente.)
- **Il testo che non e' un oggetto JSON lancia** (`parse` non ha `Result`): lo catturano i chiamanti (`EngineRemoteConfig.refresh` e
  `EngineAppUpdater.check` lo mettono in un `Result.failure`, il flusso di `config` ricade su `EngineConfig.Fallback`).
- **Tolleranza campo per campo**: un campo del tipo sbagliato non fa fallire il parse, viene trattato come assente. In particolare
  `release` senza `version` (o con `version` vuota) viene scartata (`"beta": null` e `"beta": {}` valgono entrambi «nessuna beta»);
  un flag il cui valore non e' un booleano (`1`, `"si'"`, un oggetto) viene **ignorato** e vale il default compilato nell'app;
  l'override con un valore che non e' un oggetto diventa un override vuoto; `"null"` (la stringa) e `null` JSON valgono entrambi `null`.
- `repository` accetta sia `repoOwner`/`repoName` (forma dello store) sia `owner`/`name`; se manca uno dei due la repository e' `null`
  (e **l'updater risponde «nessun aggiornamento»**, vedi [update](update.md)).
- `sizeBytes` e' un `Long`; se manca o non e' un intero vale 0, e 0 significa «peso sconosciuto» (nessun controllo sui byte scaricati).

**Trappole.**
- **Un override sostituisce l'intero `killSwitch`, non lo fonde campo a campo** (`resolveEngineConfig`: `override?.killSwitch ?: section.killSwitch`).
  Un override che scrive `"killSwitch": {"message": "ciao"}` ha `enabled = false` e **spegne** un kill switch globale per quell'app.
  Per l'app che deve restare ferma, l'override non deve nominare `killSwitch` oppure ripetere `"enabled": true`.
- Gli altri scalari (`minimumVersion`, `recommendedVersion`, `notice`) si sostituiscono per intero; **solo `flags` si fonde** (l'override vince
  sulle chiavi che nomina, le altre restano quelle condivise).
- Il manifest e' **pubblico**: niente chiavi, URL privati, nomi di funzioni non annunciate. E' un file solo, e puo' fermare tutto.
- `sizeBytes` non e' decorativo: `EngineHttp.download` lo confronta con i byte scaricati e rifiuta il file se differiscono
  («File incompleto»). Un `sizeBytes` sbagliato blocca l'aggiornamento di ogni app. Non ritoccarlo a mano.

**Versione.** `sha256` dalla 2.10.0 (verificato dall'installer quando c'e', [update](update.md)). Il resto dalla 1.0.

### I due file che chiamano questa struttura

- **`templates/engine-manifest.json`** — il *modello* da copiare per una nuova app. Ha una chiave `_commento` (ignorata dal parser),
  una sezione `app` (`id`, `name`, `packageName`, `repository` con `repoOwner`/`repoName`, `stable` con `version`, `releaseDate`,
  `changelog`, `releaseTag`, `apkAsset`, `sizeBytes`; `beta: null`) e una `engine` neutra: `schema: 1`, `minimumVersion` e
  `recommendedVersion` a `"1.0.0"` (nessuna build e' sotto), `notice: null`, `flags: {}`, `killSwitch` spento, `overrides: {}`.
  Va adattato (id, package, repo, release) prima di pubblicarlo: cosi' com'e' descrive un'app che non esiste.
- **`manifest.json` alla radice del repo** — il manifest **vero** dell'app «Fluid Glass» (la galleria del repo), nella forma dello
  Pampa Store: solo `app`, **nessuna sezione `engine`**. Oltre ai campi che l'engine legge ha quelli che servono allo store
  (`description`, `iconUrl`, `category`, `developer`, `screenshots`, `repository.manifestPath`/`ref`, e `history[]` con tutte le
  release passate), che il parser ignora. `stable` e `beta` puntano alla **stessa** release (la 2.0.0, tag
  `stable-fluid-glass-v2.0.0`), cosi' chi e' sul canale beta non resta senza aggiornamenti dopo che la beta e' diventata stabile.
  Lo legge l'updater del sample dall'URL raw di GitHub (`FLUID_GLASS_MANIFEST_URL` in `sample/.../update/FluidGlassUpdater.kt`),
  quindi store e aggiornamento in-app hanno **una fonte sola**. Lo scrive lo strumento di pubblicazione (vedi la skill
  `pampa-store-publish-direct`); non modificarlo a mano se non per correggere un testo.

## Flag e configurazione: `EngineFlag`, `EngineConfig`, `resolveEngineConfig`

**Cos'e'.** `EngineFlag` dichiara un interruttore **dove viene usato**, con il valore con cui la build e' stata provata. `EngineConfig`
e' il manifest **come vale per questa app, a questa versione, adesso**: prodotto da `resolveEngineConfig` e messo in cache, cosi' l'app
ha una risposta prima della rete.

**Firma essenziale.**

```kotlin
data class EngineFlag(val key: String, val default: Boolean = false)

data class EngineConfig(
  val schema: Int = EngineManifestSchema,
  val minimumVersion: String? = null, val recommendedVersion: String? = null,
  val notice: String? = null,
  val flags: Map<String, Boolean> = emptyMap(),
  val killSwitch: EngineKillSwitch = EngineKillSwitch(),
  val fetchedAtEpochMillis: Long? = null,
) {
  fun isEnabled(flag: EngineFlag): Boolean
  fun isEnabled(key: String, default: Boolean = false): Boolean
  fun compatibility(engineVersion: String = EngineBuild.VERSION): EngineCompatibility
  val isFallback: Boolean            // true finche' non c'e' stato un fetch riuscito
  companion object { val Fallback: EngineConfig }
}

fun resolveEngineConfig(manifest: EngineManifest, applicationId: String, fetchedAtEpochMillis: Long?): EngineConfig
```

**Uso.**

```kotlin
object Flags { val NuovaAgenda = EngineFlag(key = "nuovaAgenda", default = false) }

config.isEnabled(Flags.NuovaAgenda)        // flag del manifest, o false se non ne parla
```

**Comportamento.**
- **Il default e' il comportamento di oggi.** Un'app senza rete, o con un manifest che di quel flag non ha mai parlato, si comporta
  esattamente come il giorno in cui e' uscita: `flags[key] ?: default`. Per questo un flag senza default sicuro **non e' un flag**, e'
  configurazione obbligatoria, e va nella build.
- `EngineConfig.Fallback` e' vuoto di proposito (nessun vincolo, nessun flag, kill switch spento) e ha `fetchedAtEpochMillis = null`;
  `isFallback` e' `true` solo per lui e per ogni config risolta con `fetchedAt = null`.
- `resolveEngineConfig` con un manifest **senza** sezione `engine` restituisce una config vuota **ma con** `fetchedAt` valorizzato
  (quindi `isFallback = false`): «ho letto il file, non dice niente».
- L'override per application id **si fonde** sui flag e **sostituisce** gli altri campi (vedi le trappole sopra).

**Trappole.**
- Un flag ha una **scadenza**: quando la funzione e' stabile ovunque si toglie il ramo vecchio e il flag. Un flag dimenticato e' un
  secondo percorso di codice che nessuno prova piu'.
- I flag scelgono fra percorsi che la build **contiene gia'**: non possono aggiungere codice (vedi [`../limiti.md`](../limiti.md)).

**Non usarlo quando.** Il valore non ha un default accettabile se la rete manca (un endpoint, una chiave): e' configurazione di build.

## Impostazioni d'aspetto: `EngineSettings`, `ThemeMode`, `AccentMode`

**Cos'e'.** Tutto cio' che il design system deve sapere per dipingere un'app. **Volutamente piccolo e volutamente non il modello di
impostazioni dell'app**: un'app ha decine di preferenze e nessuna delle altre appartiene a un design system. Si mappano le proprie
impostazioni su questo tipo ai margini (un `map { }` sul flow che si ha gia'), e l'engine resta indipendente dal resto.

**Firma essenziale.**

```kotlin
data class EngineSettings(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val accentMode: AccentMode = AccentMode.BRAND,
  val customAccentName: String = "fluid",
  val dynamicColorEnabled: Boolean = true,
  val amoledEnabled: Boolean = false,
  val hapticsEnabled: Boolean = true,
)
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class AccentMode { BRAND, DYNAMIC, CUSTOM_PRESET }
```

**Comportamento.**
- `ThemeMode.AMOLED` e' un terzo stato e non un flag su `DARK` perche' cambia **cos'e'** la palette (superfici nere: serve la sua scala
  di superfici per restare leggibile), non come viene scelta. `amoledEnabled` e' lo stesso risultato per un'app che lo offre come
  interruttore sotto «Scuro» invece che come quarto radio: le due strade producono la stessa palette. Il nero vale **solo in un tema scuro**.
- `AccentMode.DYNAMIC` e' il colore dello sfondo da Android 12; **sotto ricade su `BRAND`**. Serve `dynamicColorEnabled = true` *e* `DYNAMIC`.
- `customAccentName` vale solo con `CUSTOM_PRESET`; un nome sconosciuto ricade sul primo preset.
- `hapticsEnabled` (dalla 1.19.0), di serie **acceso**: le app vibravano sulle pressioni lunghe prima che questo interruttore esistesse, e
  un'impostazione che compare non deve cambiare cio' che il telefono faceva gia'.
- La traduzione fra un modello d'app e questo (e il pezzo di schermata «Aspetto») sta in `engine-ui` (`FluidAppearance`,
  `fluidAppearanceSection`); la persistenza in `EngineSettingsStore` ([storage](storage.md)). Lo stesso `EngineSettings` va **anche** al
  widget: [widget](widget.md).

**Trappole.** Non aggiungere qui preferenze dell'app «per comodita'»: ogni campo di `EngineSettings` e' scritto da `EngineSettingsStore.update`
e letto da tema e widget; un campo estraneo li accoppia al tuo modello.

## L'aggiornamento in-app (solo i tipi): `AppUpdater`, `UpdateChannel`, `AvailableAppUpdate`, `AppUpdateInstallState`

**Cos'e'.** Il contratto, senza implementazione. L'implementazione e' [update](update.md); la schermata (sezione impostazioni, foglio, controller)
e' in `engine-ui` e parla **solo questi tipi**: un'app con un updater suo usa lo stesso disegno.

**Firma essenziale.**

```kotlin
enum class UpdateChannel { STABLE, BETA }

interface AppUpdater {
  suspend fun check(currentVersionName: String, channel: UpdateChannel = UpdateChannel.STABLE, ignoredVersion: String = ""): Result<AvailableAppUpdate?>
  fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState>
}

data class AvailableAppUpdate(val version: String, val changelog: String, val releaseTag: String, val apkAsset: String,
                              val downloadUrl: String, val sizeBytes: Long, val sha256: String = "")

sealed interface AppUpdateInstallState {
  Downloading(progress: Float, downloadedBytes: Long, totalBytes: Long)
  Verifying(message)  Installing(message)  AwaitingUserAction(message)
  Installed(filePath)
  Error(message, kind: AppUpdateErrorKind = AppUpdateErrorKind.Failed)
}
enum class AppUpdateErrorKind { Failed, InstallPermission }
```

**Comportamento.**
- **`check` restituisce `null` quando non c'e' niente di nuovo**: un aggiornamento assente e' un esito ordinario, non un errore.
  Confonderli e' il modo in cui «controlla aggiornamenti» finisce per mostrare un errore quando va tutto bene.
- Lo stato e' una **macchina a stati, non una percentuale**: la maggior parte di un'installazione non e' una percentuale (le pause lunghe
  sono la verifica e la finestra di conferma di Android), e una barra ferma al 100% e' il modo piu' comune in cui un aggiornamento fuori
  store sembra rotto mentre funziona. `AwaitingUserAction` = Android mostra la *sua* conferma e niente si muove finche' l'utente non risponde.
- **Un nuovo motivo di errore e' un nuovo `AppUpdateErrorKind`, mai un nuovo stato**: il `when` esaustivo dell'app continua a compilare.
  `InstallPermission` (dalla 3.0.1) non e' un errore da mostrare in rosso: e' Android che chiede una volta il permesso di installare da
  quest'app; la schermata lo dice e offre le impostazioni.

**Trappola.** Un `when` su `AppUpdateInstallState` con un ramo `else` nasconde un nuovo stato; scrivilo esaustivo.

## Stato di sincronizzazione: `SyncStatus`, `SyncState`

**Cos'e'.** Il risultato dell'ultimo aggiornamento dei dati dell'app, nella forma che i componenti di stato del design system leggono.

```kotlin
enum class SyncState { IDLE, SYNCING, PARTIAL, OFFLINE, ERROR }
data class SyncStatus(
  val state: SyncState = SyncState.IDLE,
  val lastSuccessfulSyncEpochMillis: Long? = null,
  val message: String? = null,
  val failedSections: List<String> = emptyList(),
  val notice: String? = null,
)
```

**Comportamento.** Il campo da capire e' `notice`. Alcuni «fallimenti» non lo sono: una fonte che non ha ancora pubblicato i dati di
quest'anno, un account senza la sezione richiesta, un server che risponde «non ancora» di proposito. Mostrarli nello stesso rosso di una
sessione scaduta insegna a ignorare il rosso. **Con `notice` valorizzato** i componenti di stato passano a un tono informativo, mostrano la
frase cosi' com'e' e **non offrono il riprova** (riprovare non aiuta). `failedSections` serve a `PARTIAL`: quali parti non sono arrivate.

**Trappola.** Mettere un guasto vero in `notice` per «renderlo meno rosso»: sparisce il tasto riprova e l'utente non ha piu' modo di agire.

## Unità e password (F8.3): `FluidUnits`, `FluidUnitFormat`, `FluidPasswordRules`

La parte pura dei campi speciali di `engine-ui` ([../componenti/campi-speciali.md](../componenti/campi-speciali.md)).

- **`FluidUnit` e `FluidUnits`**: una unità è un simbolo e la strada esatta verso l'unità base della sua dimensione
  (`FluidUnitDimension`): `base = (valore + scostamento) × fattore`, con fattore e scostamento frazioni di `BigInteger`.
  La sola divisione è l'ultima, arrotondata al pari (`.toNearestOrEven` delle `FormatStyle`). Le unità di serie sono le
  più usate di Foundation, coi fattori delle definizioni; un'app ne dichiara di sue con `FluidUnit.linear` e
  `FluidUnit.affine`, e le valute con `FluidUnit.currency(codice, rate)`: senza cambio una valuta non si converte.
- **`FluidQuantity`**: il numero con la sua unità, quello che il campo restituisce; `to(unità)` e `inBase()`.
- **`FluidUnitFormat`**: i separatori della lingua, i numeri raggruppati, il simbolo al suo posto con lo spazio che non
  va a capo, le parole per TalkBack in italiano e in inglese, e la lettura larga (l'altra convenzione dei separatori, gli
  spazi e gli apostrofi delle migliaia, il meno tipografico, il simbolo attaccato). Fiducia alta per italiano e inglese.
- **`FluidPasswordRules`**: le regole come dati, anche dalla riga delle Password Rules di Apple (`parse`); `check` dà le
  spunte, `generate` la password forte (il formato di Apple quando le regole lo ammettono, da `SecureRandom`).
- **`FluidPasswordStrength`**: la stima semplice e dichiarata (alfabeto per lunghezza efficace, le password comuni
  deboli), quattro livelli. Non è zxcvbn e non promette niente.
## Formattatori: `FluidFormat` (F8.1, 4.0)

Un posto solo per scrivere i valori come li legge una persona, sul modello delle `FormatStyle` di Foundation (iOS 15):
funzioni pure, con la lingua (di serie quella del sistema), l'istante «adesso» e il fuso passati da fuori, così si provano
con un orologio finto. Le parole e i plurali vengono da CLDR attraverso le ICU di Android (`android.icu.*`), le stesse tabelle
di Apple.

| funzione | esempio (italiano) | Apple |
|---|---|---|
| `FluidFormat.date(instant, style)` / `date(giorno, style)` / `time(instant)` | «23 nov 2026» (inglese «Nov 23, 2026»), «09:04» | `Date.FormatStyle` |
| `FluidFormat.dayAndTime(instant, now)` | «ieri, 09:04» | `Date.FormatStyle` relativo |
| `FluidFormat.listDate(instant, now)` | «09:04», «ieri», «giovedì», «12 mar», «12/03/24» | la colonna dell'ora di Mail |
| `FluidFormat.relative(instant, now, width)` | «ora», «12 min fa», «tra 2 giorni» | `Date.RelativeFormatStyle` |
| `FluidFormat.relativeNextChangeMillis(instant, now)` | fra quanto il testo di `relative` cambia | (per i testi vivi) |
| `FluidFormat.bytes(count, FluidByteStyle.File)` | «2,4 MB» (1000), `FluidByteStyle.Memory` (1024) | `ByteCountFormatStyle` |
| `FluidFormat.list(items, limit = 2)` | «Anna, Luca e altri 3» | `ListFormatStyle` |
| `FluidFormat.duration(duration, width)` | «1 h e 25 min», «1 ora e 25 minuti» | `Duration.UnitsFormatStyle` |
| `FluidFormat.timer(duration)` | «0:07», «25:07», «1:02:07» | `Duration.TimeFormatStyle`, `Text(timerInterval:)` |
| `FluidFormat.number(value)` / `percent(fraction)` | «1.234,5», «43%» | `.number`, `.percent` |
| `FluidFormat.compact(value)` | «1,2 Mln» (le migliaia in italiano non si abbreviano, come iOS) | `.notation(.compactName)` |

**Le date vengono dalle ICU, non da `java.time`** (F8.4c). Su Android 16 `DateTimeFormatter.ofLocalizedDate` e i
modelli con `MMM` o `EEEE` scrivono il mese e il giorno con la maiuscola («23 Nov 2026», «Giovedì», visti sul Tab S9),
mentre la JVM delle prove li scrive minuscoli: `date` (`MEDIUM` è lo scheletro `yMMMd`) e `listDate` usano
`android.icu.text.DateFormat` nel contesto «in mezzo alla frase». Una data visibile in un'app passa da qui, non da
`java.time`; e si guarda sul dispositivo, perché la prova sulla JVM non vede la differenza.

`FluidFormatWidth` è la larghezza delle parole (`Wide` per TalkBack: «12 minuti fa»). Le soglie del tempo relativo (a occhio
il 23 novembre 2026, sul modello di Apple che mostra l'unità più grande non nulla e tronca): sotto il minuto «ora», poi
minuti, ore, giorni fino a 6, settimane fino a 4, mesi, anni. `fluidFormatFileSize` dell'interfaccia ora chiama
`FluidFormat.bytes`, con lo stesso testo di prima. Le ICU cambiano un poco fra versioni di Android («1 min fa» o «1 minuto
fa»): le prove (`F8p1FormatTest`, Robolectric) fissano la lingua e l'SDK.

## Date e ricorrenze (F8.8): `FluidRecurrenceRule`, `FluidRecurrenceText`, `FluidDateRange`, `FluidDates`

La parte pura delle date di `engine-ui` ([../componenti/date.md](../componenti/date.md)): il pannello «Ripeti» restituisce
una `FluidRecurrenceRule`, il calendario sceglie un `FluidDateRange`.

- **`FluidRecurrenceRule`**: `EKRecurrenceRule` e la `RRULE` di RFC 5545 come data class: frequenza
  (`FluidRecurrenceFrequency`), intervallo, giorni della settimana col numero (`FluidRecurrenceDay`, «l'ultimo venerdì» è
  `FluidRecurrenceDay.last(FRIDAY)`), giorni del mese (anche dalla fine), mesi, posizioni (`setPositions`: «l'ultimo giorno
  feriale»), fine (`FluidRecurrenceEnd.Never`, `OnDate`, `AfterCount`) e `weekStart`. I campi vuoti prendono il giorno
  dall'inizio passato a `occurrences`.
  - **L'espansione lavora su date locali**: `occurrences(inizio)` è una sequenza pigra di `LocalDate`; con un
    `LocalDateTime` e una zona ogni data prende l'ora solo alla fine (`ZonedDateTime.of`: l'ora che manca va avanti del
    salto, quella che si ripete vale la prima, come RFC 5545 § 3.3.5). Mai millisecondi sommati.
  - **I casi limite seguono RFC 5545 e Calendario**: il 31 salta i mesi senza 31 (per l'ultimo giorno si usa
    `daysOfMonth = listOf(-1)`), il 29 febbraio cade solo negli anni bisestili, il quinto lunedì salta i mesi che non lo
    hanno, l'inizio conta solo se rispetta la regola. Una regola che non cade mai finisce da sola dopo 400 anni vuoti.
  - `toRRule()` e `parseRRule()` parlano la sintassi di RFC 5545 (`FREQ`, `INTERVAL`, `BYDAY`, `BYMONTHDAY`, `BYMONTH`,
    `BYSETPOS`, `UNTIL`, `COUNT`, `WKST`); le altre parti fanno fallire la lettura invece di essere ignorate.
- **`FluidRecurrenceText.describe`**: la frase in una riga, in italiano per `it` e in inglese per le altre lingue, con le
  parole scritte nell'engine (le stesse sul telefono e nelle prove): «Ogni lun e gio fino al 20 dicembre», «Ogni mese
  l'ultima domenica», «Ogni giorno fino all'8 dicembre», «Every month on the last Friday, 10 times».
- **`FluidDateRange`**: dal primo all'ultimo giorno compresi; `dayCount` conta i giorni, `duration(zona)` le ore (71 nei tre
  giorni del passaggio all'ora legale, 73 in quelli del ritorno).
- **`FluidDates`**: il primo giorno della settimana dai dati CLDR della lingua (`WeekFields.of`, con la scelta dell'utente
  di Android 14 nell'estensione `fw`), i sette giorni in ordine, la settimana di un giorno.

## La prima volta (F8.11): `FluidFirstUseMemory`, `fluidWelcomeKind`, `FluidReleaseNote`, `fluidPermissionStatus`

Le decisioni pure del benvenuto e dei permessi, senza Android, provate in `FluidFirstUseTest`:
- **`fluidWelcomeKind`**: `Welcome` a un'installazione nuova, `WhatsNew` a un aggiornamento con delle novita' (anche alla prima
  versione col benvenuto dell'engine, senza versione vista), niente alla stessa versione o a un ritorno indietro;
  `fluidWelcomeMarksSilently` dice quando segnare la versione senza mostrare niente.
- **`FluidReleaseNote`** e `AppRelease.highlights` (campo nuovo in coda, `"highlights": [{"title", "text", "symbol"}]`): senza,
  `releaseNotes()` divide il `changelog` in righe («Titolo: frase» con un titolo corto). `releaseNotesFor(versione)` guarda la
  stabile e la beta con quella versione; un'altra versione non ha novita'.
- **`fluidPermissionStatus`**: Android dice concesso e spiegazione, la memoria dice «chiesto»: insieme `NeverAsked`, `Granted`,
  `Partial`, `Denied`, `DeniedForever`. `fluidPermissionAfterRequest` tiene `NeverAsked` per un primo dialogo chiuso senza
  scegliere; `fluidPermissionStep` mostra la spiegazione una volta sola.

La parte visibile e' in [../componenti/prima-volta.md](../componenti/prima-volta.md).

## Token, costanti, Defaults

| nome | valore | note |
|---|---|---|
| `EngineBuild.VERSION` | `"3.0.4"` | compilata; tenuta in passo da `engine-release.ps1` |
| `EngineManifestSchema` | `1` | solo se cambia il significato di un campo |
| `EngineSettings` (default) | `SYSTEM`, `BRAND`, `"fluid"`, dinamico acceso, AMOLED spento, aptica accesa | il «comportamento di oggi» |
| `EngineConfig.Fallback` | config vuota | nessun flag, nessun vincolo, nessun kill switch |

## Cosa e' verificato dai test

- `EngineConfigTest` (7): un manifest porta insieme release e sezione di controllo; l'app senza override prende i valori condivisi; un override
  **fonde** sui flag invece di sostituire; un flag ignoto ricade sul default compilato; un manifest senza `engine` lascia l'app sui default;
  i campi sconosciuti sono ignorati; il pavimento di versione ritira una build vecchia (0.9.9 → richiesto, 1.1.0 → consigliato, 1.2.0 → ok).
- `EngineVersionTest` (4): confronto per segmento e non testuale; pre-release prima della release; lo stabile non offre mai una pre-release;
  segmenti senza senso non lanciano.
- `FluidRecurrenceTest` (13, F8.8): il 31 del mese e l'ultimo giorno, il 29 febbraio e il 2100, l'ultimo venerdì, il quinto
  lunedì, l'ultimo giorno feriale, le settimane alterne col loro `WKST`, la fine per data e per conteggio, l'ora legale a
  Roma e a New York, la `RRULE` avanti e indietro, le regole rifiutate, la frase in italiano e in inglese, il primo giorno
  della settimana per lingua, l'intervallo sull'ora legale.
- **Non verificato dai test:** il confronto fra due pre-release con numeri a due cifre (vedi la trappola), l'override del `killSwitch`,
  `SyncStatus`, `EngineSettings`.

## Dove guardare

- `engine-foundation/src/main/kotlin/dev/antigravity/fluidengine/foundation/EngineVersion.kt` — `EngineBuild`, `compareVersions`, `isStableVersionNewer`, `EngineCompatibility`.
- `.../EngineManifest.kt` — i tipi del manifest e `EngineManifestParser` (tutta la tolleranza del parser).
- `.../EngineConfig.kt` — `EngineFlag`, `EngineConfig`, `resolveEngineConfig` (le regole dell'override).
- `.../EngineSettings.kt` — `EngineSettings`, `ThemeMode`, `AccentMode`.
- `.../AppUpdate.kt` — `AppUpdater`, `UpdateChannel`, `AvailableAppUpdate`, `AppUpdateInstallState`, `AppUpdateErrorKind`.
- `.../SyncStatus.kt` — `SyncStatus`, `SyncState`.
- `.../FluidRecurrence.kt`, `.../FluidRecurrenceText.kt`, `.../FluidDates.kt` — la ricorrenza, la sua frase, gli intervalli di giorni (F8.8).
- `templates/engine-manifest.json`, `manifest.json` (radice) — il modello e il manifest vero di Fluid Glass.

## Dipendenze e configurazione minima

**Dipendenze del modulo.** Nessun modulo dell'engine. Esporta come `api`: `kotlinx-coroutines-core` e `kotlinx-serialization-json`.
Namespace `dev.antigravity.fluidengine.foundation`, `minSdk` 26 (il piu' alto fra quello dell'engine e quello dell'app host), Java 17.

**Aggancio.** `tools/engine-install.ps1 -AppRoot . -Modules engine-foundation` (e' il fondo di ogni altro modulo: lo script lo aggiunge da solo
quando se ne chiede un altro). Nel `build.gradle` dell'app:

```groovy
implementation project(':engine-foundation')   // solo se non c'e' gia' un altro modulo che lo esporta (engine-ui lo esporta come api)
```

**Configurazione minima.** Nessuna: sono tipi. Per mettere in piedi **config remota e aggiornamento** il collegamento e' nei template
`templates/EngineModule.kt.txt` (Hilt: `EngineHttp`, `EngineRemoteConfig`, `EngineAppUpdater`) e `templates/EngineBootstrap.kt.txt`
(`Application`: refresh, compatibilita', kill switch, widget). Vedi le schede [config](config.md) e [update](update.md).
