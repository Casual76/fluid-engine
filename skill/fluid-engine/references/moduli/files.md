# engine-files

**Lo sfoglia-file di vetro** (F8.18, idea `sfoglia-file`): la pagina per sfogliare, cercare, scegliere e aprire i file, come File di iOS 26 (`UIDocumentBrowserViewController`; per scegliere, `fileImporter` di SwiftUI). Pacchetto `dev.antigravity.fluidengine.files`, dalla 4.0.

È un **modulo opt-in**, sopra [`engine-viewer`](viewer.md) (i visori, le miniature, la ripresa dallo stesso punto) e quindi `engine-ui`: si installa con `engine-install.ps1 -Modules …,engine-files`. Nessuna libreria: lo Storage Access Framework è di Android. Con i moduli di famiglia ([`viewer-famiglie.md`](viewer-famiglie.md), o `engine-viewer-all`) apre anche Office, archivi, dati e il resto, senza codice in più.

Modello mentale: **le sorgenti danno i file, il browser li sfoglia, i visori li aprono, l'app fa il resto.** L'engine sfoglia, ordina, cerca, sceglie e apre; copiare, spostare, rinominare e cancellare restano dell'app (il confine della scheda dell'idea: non è un gestore di file completo).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| la pagina intera, con le schede Recenti e Sfoglia, le cartelle e il visore | `FluidFileBrowser(sources = listOf(…), onBack = …)` |
| la cartella dei file dell'app fra le posizioni | `FluidFileSources.appFolder(dir, id, titolo)` |
| una cartella concessa dall'utente (il telefono, Drive, un'app con un `DocumentsProvider`) | di serie: «Aggiungi una posizione…» (`rememberFluidFileLocations`, `rememberFluidAddLocation`); un albero che ho già: `FluidFileSources.documentTree(context, treeUri, titolo)` |
| un cloud, gli allegati di una casella, i file di una conversazione | una mia `FluidFileSource` (a pagine, sospendibile, che può fallire) |
| far scegliere dei file all'utente (il modo `fileImporter`) | `FluidFileBrowser(onPick = { scelti -> … })`: un tocco su un file lo sceglie, come il selettore di documenti di iOS; più file con «Seleziona» e «Apri» |
| le mie azioni sui file scelti (Sposta, Elimina) | `selectionActions = { scelti -> button(…) }` (la DSL della toolbar) |
| i recenti fuori dal browser (la home dell'app) | `rememberFluidFileRecents().entries`; aggiungerne uno: `record(sourceId, item)` |
| griglia o elenco, l'ordine di partenza | `rememberFluidFileBrowserState(layout = FluidFileLayout.List, sort = FluidFileSort.of(FluidFileSortKey.Date))` |

## La pagina

`FluidFileBrowser` è una pagina intera con le sue rotte (un `FluidNavHost` dentro): si mette dove l'app vuole (una scheda, una rotta, un foglio). Tutto si muove con le pagine di sempre, il back col dito compreso.

- **Recenti | Sfoglia** (F8.18b, scelta di Alessio): la prima pagina ha un segmentato sotto il titolo, come le schede di File sull'iPhone (`FluidFileBrowserTags.Tabs`). Si apre su **Recenti** se c'è almeno un file recente, se no su **Sfoglia**; la scheda scelta resta tornando da una cartella o da un file. Il titolo grande è il nome della scheda. Con `recents = null` non ci sono schede: solo Sfoglia.
- **Recenti** è una cartella come le altre (Icone o Elenco, «Cerca in Recenti», «Seleziona»), con i file aperti dal più recente.
- **Sfoglia** sono le **posizioni**: le sorgenti dell'app (`sources`), le cartelle concesse (`locations`) e «Aggiungi una posizione…». Una cartella concessa si toglie tenendola premuta («Togli dalle posizioni»): il permesso torna al sistema se l'aveva preso lo sfoglia-file, i file restano dove sono.
- **Una cartella** ha il nome in alto (il titolo grande che si ritira), «Cerca in …» sotto, il menu «•••» con «Seleziona», **Icone** e **Elenco**, e l'ordine: **Nome, Tipo, Data, Dimensione**, come il menu di File; il secondo tocco sulla voce scelta inverte il verso, la data e la dimensione partono dal più recente e dal più grande. Le cartelle stanno prima dei file.
  - **Icone**: la griglia con le **miniature vere** di `engine-viewer` (`rememberFluidFileThumbnail`), il nome su due righe, la data e il peso (o quanti elementi ha una cartella). Tre colonne sul telefono (la tessera è di almeno 112 dp), fino a otto sul tablet.
  - **Elenco**: righe raggruppate (`FluidListRow` nei gruppi a segmenti) con la miniatura da 44 dp, «data · peso» sotto il nome, la freccia sulle cartelle.
  - **Migliaia di file**: arrivano a pagine da 200 ([`FluidFileFolderState`](#lo-stato-di-una-cartella)); la pagina dopo si chiede quando la lista ci arriva vicino. Da 60 elementi c'è lo **scorrimento rapido** (`FluidFastScroller`) con l'iniziale, il mese, il peso o l'estensione secondo l'ordine. Mentre aspetta, le tessere vuote (`FluidSkeletonTile`); se la posizione non risponde, «Non riesco a leggere la cartella» con «Riprova», e le righe già arrivate restano.
  - **La ricerca** non bada a maiuscole e accenti («citta» trova «Città»). Una sorgente che non cerca da sé si legge tutta e si filtra in memoria; a ricerca vuota la cartella torna a leggersi a pagine (R2: prima, dopo una ricerca, ogni rilettura la tirava giù tutta).
- **Aprire un file**: si apre nei visori dell'engine **dalla sua miniatura e ci torna** (il viaggio d'oro di F6C.10, anche chiudendo col dito), sfogliando gli altri file della cartella così come si vedono (anche filtrati dalla ricerca), col pannello info (tipo, dimensione, data, posizione e i dettagli del visore o della famiglia), «Condividi», «Apri con…», «Copia», e **riparte dal punto in cui era stato lasciato** ([`viewer.md`](viewer.md), «Riprendi dallo stesso punto»). Il file aperto entra nei recenti.
- **TalkBack**: ogni tessera e ogni riga dicono nome, tipo, data e peso («alfa.txt, Testo, 23 nov 2026, 10 B»), una cartella «Cartella, 2 elementi»; in selezione lo stato «selezionato». Lo scorrimento rapido è un cursore che salta.
- **Riduci movimento**: le pagine e il visore seguono la politica del movimento; nessuna animazione è dello sfoglia-file.

Le parole sono in `FluidFileBrowserStrings`, in italiano di serie.

## Le sorgenti

```kotlin
interface FluidFileSource {
  val id: String; val title: String
  val subtitle: String? get() = null; val icon: ImageVector? get() = null
  val sorts: Boolean get() = false        // rende già nell'ordine chiesto
  val searches: Boolean get() = false     // cerca da sé (FluidFileQuery.search)
  suspend fun list(query: FluidFileQuery): FluidFilePage   // al più query.limit, e il segno della prossima
  fun changes(folder: String?): Flow<Unit> = emptyFlow()   // la cartella è cambiata: si rilegge
}
```

- **A pagine**: `FluidFileQuery(folder, sort, cursor, limit, search)` → `FluidFilePage(items, next)`. `next` null è l'ultima. Un `id` che una pagina dopo ripete (un segno a spostamento, una cartella che cambia mentre si scorre) entra una volta sola: la seconda si scarta (R3: prima due chiavi uguali facevano cadere l'elenco).
- **Lenta e fallibile**: `list` si chiama dal thread principale e sposta da sé il lavoro (`withContext(Dispatchers.IO)`); un'eccezione diventa «Riprova».
- **Gli elementi**: `FluidFileItem(id, name, isFolder, mimeType, sizeBytes, modifiedAtMillis, uri, childCount)`. L'`id` è stabile nella sorgente (entra nelle chiavi, nei recenti e nel viaggio d'oro); `uri` è l'indirizzo da cui il visore legge (`viewerFile()`).
- **Le due di serie** leggono la cartella intera una volta (la prima pagina, o una rilettura) e consegnano le pagine dopo dalla stessa lettura, nell'ordine chiesto (`sorts` vero): un file arrivato mentre si scorre non sposta le pagine e non fa doppioni, e si vede alla rilettura.
  - `FluidFileSources.appFolder(root, …)`: il disco dell'app, senza permessi; i file col punto davanti non si mostrano; una cartella fuori dalla radice non si legge. Da R2 tiene anche lei l'ultima lettura (prima ogni pagina rileggeva e riordinava tutta la cartella).
  - `FluidFileSources.documentTree(context, treeUri, …)`: un albero dello Storage Access Framework, con una richiesta sola per cartella (`buildChildDocumentsUriUsingTree`) e gli indirizzi dei documenti costruiti sull'albero. Un provider che carica ancora (un cloud) avvisa con `changes`. Un permesso tolto è un errore che si legge.

## Le posizioni concesse

`FluidFileLocations` legge le cartelle concesse dai **permessi persistenti** dell'app (`persistedUriPermissions`): una cartella il cui permesso è stato tolto (Impostazioni, un'app disinstallata) sparisce da sola. `add(treeUri)` prende il permesso di lettura, `remove(source)` lo restituisce **solo se è dello sfoglia-file** (R1): l'ha preso `add`, l'app non lo teneva già e nessuno ci scrive. Una cartella che usa anche un'altra funzione dell'app (l'export che ci scrive, un'importazione che la teneva prima) si **nasconde** soltanto, anche ai prossimi avvii, e l'altra funzione non perde l'accesso; riscelta col selettore, torna. Il nome è quello della cartella; la seconda riga dice da dove viene («Sul telefono», «Download», l'app del provider).

`rememberFluidAddLocation(locations)` apre il **selettore di sistema** (`ACTION_OPEN_DOCUMENT_TREE`), di serie sulla memoria interna (`FluidFileLocations.PhoneHint`). È l'unica porta verso le app con un `DocumentsProvider`: Android non lascia a un'app elencare i provider delle altre, li mostra solo nel suo selettore. Da Android 11 il selettore non concede la radice della memoria interna né la cartella Download intera: si sceglie una cartella dentro (è la regola della piattaforma).

## I recenti

`FluidFileRecents` tiene gli ultimi 60 file aperti, il più recente in cima, una volta sola; un file riaperto torna su. Si salvano in un file corto in `noBackupFilesDir`, scritto in sottofondo; una scrittura che il disco non prende si riprova da sé, con un'attesa che raddoppia da 1,5 s a un minuto (R2). **Un file che non c'è più esce**: alla prima apertura dello sfoglia-file si chiede a ogni indirizzo se esiste (`resumeTargetExists` di `engine-viewer`), e il visore toglie un file che non riesce più a leggere. `source` è la sorgente della scheda «Recenti» (con «Data» è l'ordine d'apertura); `sourceOf(item)` dice da quale posizione veniva.

## Lo stato di una cartella

`FluidFileFolderState` è una cartella aperta: `items` (ciò che si vede, ordinato e filtrato), `files` (solo i file, per il visore), `loading`, `failure`, `complete`, e `start`, `loadMore`, `retry`, `refresh`, `sortBy`, `search`. Lo tiene `FluidFileBrowserState` (al più 12 cartelle aperte), così tornando indietro la cartella è ancora lì, allo stesso punto. `layout` e `sort` sono uguali per tutte le cartelle, come in File, e sopravvivono alla ricreazione.

## Trappole

- **Un file dell'app non si condivide da sé.** Di serie «Condividi» passa al sistema solo gli indirizzi `content://`; un `file://` ha bisogno del `FileProvider` dell'app: passare `share = { scelti -> … }`.
- **Il permesso di una cartella concessa** è dell'app: chi prende una cartella col suo selettore e non chiama `takePersistableUriPermission` la perde al riavvio. Con `FluidFileLocations.add` lo fa l'engine.
- **Una sorgente che non ordina** (`sorts` falso) riordina in memoria le pagine arrivate: una pagina nuova può inserire file sopra quelli che si vedono. Se la sorgente sa ordinare, che lo dica.
- **Le chiavi**: l'`id` di una sorgente non deve contenere «/»; l'`id` di un elemento dev'essere stabile fra due letture, o il viaggio d'oro e i recenti non lo ritrovano.

## Cosa è verificato dai test

- `FluidFileSourcesTest` (Robolectric): l'ordine di File (cartelle prima, la lingua, i versi di serie), la ricerca senza accenti, la cartella dell'app a pagine e dentro la sua radice, un albero letto da un provider finto con gli indirizzi di Android (una richiesta sola per cartella, i documenti sull'albero, il permesso tolto), le posizioni col permesso persistente preso e restituito; R1: la cartella dell'export (lettura e scrittura) e quella che l'app teneva già, nascoste senza togliere il permesso; R2: le pagine della cartella dell'app da una lettura sola.
- `FluidFileFolderStateTest`: le pagine che arrivano, l'errore con «Riprova» dal punto giusto, la ricerca su tutta la cartella (e, R2, il ritorno alle pagine dopo la ricerca), l'ordine in memoria e quello chiesto alla sorgente, R3: le pagine sovrapposte con gli stessi id (ogni elemento una volta); i recenti in ordine, una volta sola, salvati, ripuliti dai file spariti; `FluidFileRecentsRetryTest` (R2): una scrittura fallita si riprova da sé.
- Nella galleria, `F8p18FilesUiTest` col dito vero: le schede Recenti e Sfoglia (si apre su Recenti quando ce n'è uno, la scheda resta tornando indietro, un file si riapre dai Recenti), la cartella in griglia e in elenco, l'ordine, la ricerca, i nomi per TalkBack, la cartella lunga a pagine con lo scorrimento rapido, il file aperto nel visore e nei recenti, la cartella concessa col selettore di sistema simulato, «Riduci movimento». `R3m3FilesSearchTest` (R3): una sorgente con le pagine sovrapposte, in elenco, ogni file una volta e senza cadere.

## Dove guardare

| cosa | file |
|---|---|
| il contratto delle sorgenti, l'ordine, la ricerca | `engine-files/src/main/kotlin/dev/antigravity/fluidengine/files/FluidFileSource.kt` |
| le due sorgenti di serie | `FluidFileSources.kt` |
| le posizioni concesse e il selettore | `FluidFileLocations.kt` |
| i recenti | `FluidFileRecents.kt` |
| una cartella aperta | `FluidFileFolderState.kt` |
| la pagina, le rotte, la griglia, l'elenco, il visore | `FluidFileBrowser.kt` |
| il modulo usato da un'app | `sample/.../novita/F8p18Scenes.kt` (la scena «Sfoglia i file») |

## Dipendenze e configurazione minima

Con lo script: `engine-install.ps1 -AppRoot . -Modules engine-ui,engine-files` (porta `engine-viewer`, `engine-ui` ed `engine-foundation`). A mano:

```groovy
// settings.gradle dell'app
include ':engine-files'
project(':engine-files').projectDir = file('engine/engine-files')

// build.gradle dell'app
implementation project(':engine-files')
// facoltativo: tutte le famiglie dei visori
implementation project(':engine-viewer-all')
```

Nessun permesso: le cartelle arrivano dal selettore di sistema. `minSdk` 26.
