# engine-media

**Foto e allegati** (4.0, F8.14, `scelta-foto-e-video` e `pannello-allegati`): il selettore di foto e video di vetro,
come il selettore di foto di iOS (`PHPickerViewController`), aperto a **sorgenti diverse dal telefono** (MyCloud Gallery
con la ricerca per significato e le foto che stanno solo nel backup, in F12; un archivio futuro), e il pannello degli
allegati del «+», come in Messaggi di iOS 26. Pacchetto `dev.antigravity.fluidengine.media`.

È un **modulo opt-in** su `engine-ui`, **senza librerie nuove** (MediaStore, `ImageDecoder` e il Photo Picker di
`androidx.activity`, che `engine-ui` porta già). **Il modulo non dichiara permessi**: li dichiara l'app che usa la griglia
del telefono (sotto, «I permessi»). Dichiara solo la visibilità delle sorgenti delle altre app (`<queries>`).

**L'engine** fa la griglia, la scelta in ordine, gli album, la ricerca, l'anteprima, i tre regimi dei permessi, il
contratto fra app, le sorgenti del telefono e delle altre app, il pannello e il suo passaggio con la tastiera.
**L'app** dice cosa accetta (`FluidMediaRequest`), quali sorgenti offre, cosa fa degli indirizzi scelti, e riempie le
fonti del pannello.

Catalogo delle firme: [`../api/media.md`](../api/media.md) (generato). La griglia di anteprime e la selezione sono in
[`../componenti/contenuto-righe-liste.md`](../componenti/contenuto-righe-liste.md) e
[`../componenti/contenuto-testo-e-media.md`](../componenti/contenuto-testo-e-media.md); il permesso dal tasto in
[`../componenti/prima-volta.md`](../componenti/prima-volta.md); il visore in [`../componenti/visori.md`](../componenti/visori.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| far scegliere foto e video dentro l'app, in ordine, con album e ricerca | `val state = rememberFluidMediaPickerState(sources, FluidMediaRequest.attachments(10))` e `FluidMediaPicker(state, onResult = { … }, onCancel = { … })` in una pagina |
| una foto sola (il profilo): un tocco sceglie e torna | `FluidMediaRequest.SinglePhoto` |
| le foto del telefono | `FluidDeviceMediaSource(context)`, coi permessi nel manifest dell'app |
| solo le foto del telefono, senza chiedere permessi | `rememberFluidSystemPhotoPicker(request) { media -> … }.launch()`; `FluidSystemPhotoPicker.suffices(context, sources)` dice quando basta |
| leggere da MyCloud Gallery (F12) o da un'altra app Pampa | `FluidMediaSources.discover(context)` e passare le sorgenti trovate al selettore |
| offrire alle altre app le foto della mia app (MyCloud Gallery in F12, un archivio) | estendere `FluidMediaSourceProvider` con la mia `FluidMediaSource` e dichiararlo nel manifest (sotto, «Il contratto fra app») |
| una sorgente finta per le prove o una galleria | `FluidFakeMediaSource.sample()` |
| il «+» di Messaggi: la scheda che nasce dal tasto, con le recenti e le fonti | `FluidAttachmentMenu(visible, onDismissRequest, source = FluidTransitionSource(id, ns), recents, sources, attached, onAttach, onRemove)` |
| il pannello al posto della tastiera, alla sua altezza | `FluidAttachmentPanelHost(state, panel = { FluidAttachmentPanel(…) }) { schermata }` col «+» che chiama `state.toggle()` |
| le fonti del pannello | `FluidAttachmentSource.camera(…)`, `photos`, `files`, `location`, `audio`, `other(id, nome, …)`: un elenco aperto |
| la miniatura di un elemento scelto, fuori dal selettore (il vassoio del composer) | `rememberFluidMediaThumbnail(source, item, sizePx)` dentro un `FluidAsyncImage` |

## Il selettore: `FluidMediaPicker`

Una **pagina intera** (una rotta dell'app, o un livello sopra). Le misure vengono dal selettore di foto di iOS (immagini
ufficiali in `artifacts/liquid-glass/sessioni/f8-14/riferimenti/`, `indice.md`):

- **La griglia** è `FluidPreviewGrid` di F8.4: il pizzico fra 3, 5 e 9 colonne (`FluidMediaPickerDefaults.Columns`),
  2 dp fra le celle, celle quadrate; parte da **3 colonne**, come il selettore di iOS 15–26 (passo 132 pt su 393).
- **La testata è quella del selettore di iOS 26** (l'unica immagine ufficiale Liquid Glass, WWDC25): la «x» di vetro a
  sinistra e il ✓ in evidenza a destra (cerchi da 44 dp a 10 dp dai bordi), il titolo al centro («Foto», l'album,
  «Cerca») con sotto la scelta («3 foto selezionate») o la sorgente; nella prima pagina, 11 dp sotto, il segmentato
  **«Foto | Album»** a tutta larghezza; la griglia comincia 16 dp sotto e le foto passano sotto la testata con un velo del
  colore della pagina. Il tasto rotondo della **ricerca** sta in basso a destra (in iOS 26 la ricerca è un tasto).
- **La scelta in ordine** (`FluidMediaRequest.ordered`, vero di serie con più di una scelta): il disco della scelta porta
  il **numero** al posto della spunta (`FluidSelectionMark(number = …)`, iOS 15 `selection = .ordered`: cifra da 12–13 pt
  nello stesso posto della spunta, in basso a destra a ~16 dp dai bordi; le foto non scelte non hanno l'anello, come in
  iOS); togliendo la seconda, la terza diventa seconda e
  la cifra **scorre sfocata** (`FluidNumericText`: lo fa l'app, non il dito su quel segno). Il dito che scorre **di lato**
  sceglie in fila (la selezione trascinando di F8.4). A tetto pieno (`FluidMediaRequest.limit`, il `selectionLimit` di
  iOS) le altre celle si **spengono** sotto un velo del colore della pagina e il tocco non le sceglie.
- **Un tetto di 1** (la foto del profilo): niente segni né ✓, il tocco sceglie e torna, come iOS.
- **La ricerca** cerca nella sorgente che sa cercare per significato (MyCloud Gallery) e, se ce n'è più d'una, offre la
  scelta; il campo sta in basso, sopra la tastiera; sotto il campo le parole che la sorgente ha capito («Mare · Estate»).
  Si cerca 300 ms dopo l'ultima lettera (`FluidMediaPickerDefaults.SearchDebounceMillis`).
- **L'anteprima a tutto schermo** con la **pressione lunga** (come il selettore di iOS; la pressione lunga allora non
  entra nella selezione trascinando): la foto **cresce dal suo riquadro** e ci rientra (il viaggio d'oro, `FluidPageHost`
  con `FluidZoomStyle.Zoom` e l'immagine condivisa), anche trascinando in giù; si sfoglia nel visore dell'engine
  (`FluidFileViewer`), e dal tasto in basso si sceglie o si toglie. Tornando dopo uno sfoglio rientra nella foto
  raggiunta. TalkBack ha l'azione «Anteprima» su ogni cella.
- **Le celle**: la miniatura arriva sfocata e si mette a fuoco (`FluidAsyncImage` di F8.1: una miniatura già in cache non
  si anima); un video ha il triangolo e la durata come Foto («0:42», in basso a destra; dove c'è il segno della scelta, in
  basso a sinistra); un elemento **solo nel backup** ha la nuvola in alto a destra. TalkBack: «Mare, Video, 1 minuto e 5
  secondi, 13 lug 2025, solo nel backup».
- **La consegna**: il ✓ chiede a ogni sorgente i suoi elementi, **nell'ordine della scelta**, e li rimette in ordine
  (`FluidMediaPickerState.deliver`). Se una sorgente rifiuta (il backup non risponde, troppi insieme) **non esce niente**:
  la scelta resta perché la persona la corregga e un avviso dice perché con parole semplici. Gli elementi spariti nel
  frattempo si contano e si dicono.
- **Alla rotazione e alla morte del processo** la scelta resta **con i suoi elementi** (R1): con le chiavi si salvano i
  campi degli elementi scelti (al più 200, una quarantina di kB nel `Bundle`), perché dopo la ricreazione le pagine
  ripartono dalla prima e un elemento scelto oltre la prima pagina, da un album o da una ricerca non tornerebbe. La
  consegna li ricontrolla con la sorgente. Una chiave che non si ritrova (messa nella scelta dall'app senza che una pagina
  l'abbia portata) esce dalla scelta al ✓ e conta fra i saltati: mai un ✓ che non fa niente.

### L'accesso: i tre regimi delle foto, lo sblocco, la sorgente che non c'è

Ogni sorgente dice il suo `FluidMediaAccess` come stato; il selettore mostra la cosa giusta al posto della griglia:

| accesso | cosa si vede |
|---|---|
| `Full` | la griglia |
| `Limited` (Android 14, «solo alcune foto») | la griglia con quelle concesse e la capsula «Solo le foto che hai scelto · Gestisci» (riapre la scelta di Android: `requestMore()` di F8.11) |
| `NotAllowed` | «Consenti l'accesso alle foto» col tasto del permesso (`rememberFluidPermission(FluidPermission.PhotosAndVideos)`, la spiegazione nasce dal tasto; dopo due no, «Apri Impostazioni») e «Scegli con Android» (il Photo Picker, senza permessi) |
| `Locked` | «MyCloud Gallery è bloccata» con «Sblocca»: apre lo sblocco dell'app sorgente (`PendingIntent`), poi rilegge |
| `Unavailable` | «… non è disponibile» con «Riprova» |
| una pagina che non arriva (il NAS spento, una scadenza, un errore della sorgente) | lo stesso «… non è disponibile» con «Riprova» al posto di «Nessuna foto»; a metà lista una capsula in fondo con «Riprova». Gli elementi arrivati restano (`FluidMediaFeed.failed`, `retry()`: R1). Vale anche per il MediaStore del telefono che lancia o non risponde e per la risposta `error` di una sorgente di un'altra app (R3) |
| gli album che non arrivano (R3) | nella pagina Album, al posto del gruppo della sorgente, «… non è disponibile» con «Riprova»; un errore non si tiene come «nessun album», e tornando sulla pagina si richiedono |

L'accesso si **rilegge da solo** dopo il dialogo di Android, al ritorno dalle impostazioni (lo stato del permesso) e dopo
lo sblocco; le pagine della sorgente si ricaricano solo se l'accesso è cambiato.

### I permessi

Il modulo non ne dichiara. L'app che usa `FluidDeviceMediaSource` scrive nel suo manifest:

```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
```

Il **Photo Picker di Android** non ne vuole, e nemmeno le sorgenti delle altre app. Un'app che non dichiara i permessi e
offre solo il telefono non ha bisogno della griglia dell'engine: `FluidSystemPhotoPicker.suffices` lo dice, e
`rememberFluidSystemPhotoPicker` apre quello di sistema (uno o, col tetto, più elementi; `ImageOnly`, `VideoOnly` o un
tipo preciso dalla richiesta). Il risultato è lo stesso `FluidPickedMedia`.

## Le sorgenti: `FluidMediaSource`

Un **archivio con indice e ricerca**: `id` e `label`, `capabilities` (ricerca, ricerca per significato, album, elementi
solo nel backup, tetto della consegna), `access` (stato di Compose) e `refresh()`, `albums()`, `page(query, cursor,
limit)` (le pagine dal più recente o dal più pertinente, con `interpretation` per una ricerca), `thumbnail(item, sizePx)`
e `deliver(items, request, onProgress)` (gli indirizzi nell'ordine chiesto, o `Refused` col motivo, o `NeedsUnlock`).

**Le regole di chi implementa** (le stesse del selettore di MyCloud Gallery, `PickerDelivery.kt`):
- **la vista di adesso decide**: si elenca e si consegna solo ciò che la persona vedrebbe adesso nella sorgente (mai i
  nascosti, mai un album chiuso, mai ciò che il permesso non concede); un elemento sparito dopo che la griglia l'aveva
  mostrato conta fra i saltati;
- **id opachi**, mai percorsi o nomi privati; `description` e `name` solo se la persona li vedrebbe;
- **mai attese infinite**: un backup che non risponde si dice subito (`FluidMediaRefusal.Unreachable`), prima di
  consegnare qualunque cosa;
- **l'ordine della scelta** si rispetta, e una consegna annullata non lascia niente di consegnato.

Le sorgenti di serie:
- `FluidDeviceMediaSource` (id `device`): il MediaStore; gli album come Foto (Recenti, Preferiti da Android 11, Video,
  Screenshot, poi le cartelle con la Fotocamera per prima); la ricerca per nome del file e della cartella (il testo passa
  come argomento, mai nell'SQL); le pagine con limite e spostamento (`QUERY_ARG_LIMIT` da Android 11); le miniature con
  `loadThumbnail` (Android 10+); la consegna con gli indirizzi del MediaStore, riletti uno per uno. Un MediaStore che
  lancia (una colonna che un produttore non ha) o che non risponde (il cursore nullo) **lancia** anche dalla sorgente (R3):
  prima diventava una pagina vuota e finita, cioè «Nessuna foto».
- `FluidRemoteMediaSource`: la sorgente di un'altra app attraverso il contratto, trovata con `FluidMediaSources.discover`.
- `FluidFakeMediaSource`: in memoria, con album, elementi solo nel backup, una ricerca per significato finta (parole
  contro etichette, stagioni contro la data: «mare d'estate» → «Mare», «Estate»), lo sblocco, il NAS spento
  (`backupReachable`) e un'attesa (`latencyMillis`). `sample()` è una libreria di prova come quella di MyCloud.

## Il contratto fra app: `FluidMediaSourceContract` (versione 1)

È il punto d'incontro con **MyCloud Gallery in F12**: la galleria mette la sua Libreria dietro un provider, e ogni app Pampa
col selettore la legge senza vedere altro.

**Chi serve** estende `FluidMediaSourceProvider` (un `ContentProvider`), restituisce la sua sorgente da `createSource` e
lo dichiara così:

```xml
<provider android:name=".media.GalleryMediaSource"
    android:authorities="dev.pampa.mycloudgallery.fluidmedia"
    android:exported="true">
  <intent-filter><action android:name="dev.antigravity.fluidengine.media.SOURCE" /></intent-filter>
</provider>
```

**Chi legge**: `FluidMediaSources.discover(context)` trova i provider con l'azione `ACTION_SOURCE`, si fida di serie solo di
quelli firmati come lui (`FluidMediaSourceTrust.SameSignature`: le app Pampa), si collega a ognuno (chi è, cosa sa fare,
l'accesso) e restituisce delle `FluidRemoteMediaSource`, che il selettore usa come le altre. `connect(context, authority)`
per chi conosce già l'autorità.

| metodo (`ContentProvider.call`) | cosa chiede | cosa risponde |
|---|---|---|
| `METHOD_INFO` | — | versione, id, nome, cosa sa fare, l'accesso (con lo sblocco) |
| `METHOD_ALBUMS` | — | gli album con la copertina |
| `METHOD_PAGE` | album, testo, tipi, solo locali, segno di pagina, quanti (al più `MAX_PAGE`, 300) | gli elementi in colonne, il segno dopo, il totale, come ha capito la ricerca |
| `METHOD_DELIVER` | gli elementi nell'ordine della scelta, i tipi ammessi, il tetto (al più `MAX_DELIVERY`, 100) | gli indirizzi col permesso di **sola lettura** dato a chi chiede, o il rifiuto col motivo, o lo sblocco |
| miniatura (`openTypedAssetFile`) | `content://<autorità>/thumbnail?id=…&kind=…`, il lato in `ContentResolver.EXTRA_SIZE` | un JPEG su un tubo, mai il file intero |

Cosa fa da solo `FluidMediaSourceProvider`: il **controllo di chi chiede** a ogni chiamata, miniature comprese
(`isCallerAllowed`: di serie la stessa app e le app firmate con la sua chiave; MyCloud lo sostituisce col suo `CallerGuard`
se vuole), le **scadenze** di ogni metodo (4 s, 8 s per una pagina, 2 minuti per una consegna: oltre, «non disponibile» o
`Unreachable`, mai un binder appeso), niente elenchi senza accesso, il **permesso di lettura** sugli indirizzi consegnati
(`grantUriPermission`: gli indirizzi stanno in un provider dell'app con `grantUriPermissions`, come il `DocumentsProvider`
di MyCloud con gli id opachi consegnati), il tetto della consegna, e la sola lettura (niente righe, niente scritture).
**Quanto vale una consegna** (R1): il permesso di `grantUriPermission` non è legato a un'attività, e resta finché nessuno
chiama `revokeUriPermission`, il telefono non si riavvia o una delle due app non viene fermata o tolta. Bloccare la
sorgente non lo toglie: ciò che è consegnato resta all'app che l'ha chiesto, come nel selettore di iOS. Se il blocco deve
chiudere anche quello, è il provider che serve i file (il `DocumentsProvider` di MyCloud) a ricontrollarlo a ogni
apertura.
Chi legge ignora le chiavi che non conosce; una versione nuova aggiunge chiavi, non le cambia.

**Cosa resta a MyCloud Gallery in F12** (la galleria oggi non si tocca): la sua sorgente sopra la Libreria (la vista di
adesso con l'accesso parziale, i nascosti, il blocco dell'app come `Locked` con il suo `unlockPendingIntent`, la versione
fermata come `Unavailable(UNAVAILABLE_STOPPED)`), la ricerca semantica come `page(query.text)` con le sue parole in
`interpretation`, la consegna dei solo-NAS coi suoi tetti e il NAS provato prima di creare gli id, «Rimuovi la posizione»
(`FluidMediaRefusal.LocationNotRemoved` se non si può).

## Il pannello degli allegati

Due presentazioni dello stesso contenuto (le foto recenti in cima, poi le fonti):

- **`FluidAttachmentMenu`, come Messaggi di iOS 26** (di serie nella galleria): il «+» diventa una **scheda di vetro** che
  cresce dal tasto e ci rientra (il pannello che nasce da un tasto, `FluidGlassModalPortal` con `source`, anche sul
  telefono). Misure di iOS 26 e 27 (guida dell'iPhone e articolo 104969): **320 × 456** al più, righe da **66 dp**, icone
  tonde da **38 dp** col centro a 53 dp dal bordo, il nome a 100 dp (`FluidAttachmentMenuDefaults`). Una fonte toccata
  chiude la scheda e si apre; una foto recente si allega e la scheda resta. Messaggi non ha la striscia delle recenti: è la
  nota della scheda `pannello-allegati`, e si toglie con `recents = null`.
- **`FluidAttachmentPanelHost` + `FluidAttachmentPanel`, al posto della tastiera**: il pannello sta in fondo **all'altezza
  dell'ultima tastiera vista** (300 dp sopra la barra di sistema prima di vederla), e il passaggio è senza salto: aprendo con
  la tastiera su, il pannello prende subito il posto sotto di lei e la schermata ha già gli inset tolti, quindi il campo
  resta fermo mentre la tastiera scende; toccando il campo la tastiera sale sopra il pannello e quando è arrivata il
  pannello lascia il posto. Senza tastiera sale con la molla dei fogli (0,86 / 380). La quota e la parte riservata si
  leggono in misura: la tastiera che si muove non ricompone la schermata.

La **striscia delle recenti**: 30 foto da 84 dp con 2 dp fra l'una e l'altra (il selettore compatto di iOS 17); un tocco **allega subito** (la sorgente consegna quell'elemento,
`onAttach`) e la foto porta il **numero** del vassoio finché è fra gli allegati (`attached`, le chiavi in ordine), un altro
tocco la toglie (`onRemove`); a `maxAttachments` le altre si spengono; in fondo «Tutte le foto» (`onAllPhotos`, di solito il
selettore). Senza permesso, al posto della striscia il tasto «Consenti l'accesso». Mentre una sorgente lenta (il NAS)
consegna, la foto porta la rotella, un secondo tocco non la consegna di nuovo e conta già per `maxAttachments`; una
consegna rifiutata o che fallisce lo dice con l'avviso del selettore (le stesse frasi), e una sorgente chiusa apre lo
sblocco come nel selettore (R3).

Le **fonti** (`FluidAttachmentSource`) sono un elenco aperto: i preset danno icona e tono della piastrella (fotocamera,
foto, file, posizione, audio), `other` una dell'app. Cosa fanno lo decide l'app: la fotocamera vuole un `FileProvider`
suo per `ACTION_IMAGE_CAPTURE`, i file `OpenMultipleDocuments`, la posizione il suo permesso dal tasto.

## Cosa costa

- **La griglia** non tiene immagini sue: le miniature stanno in `FluidMediaThumbnails` (un ottavo della memoria, al più
  32 MB, le meno usate escono; lati a passi di 64 px; al più 4 letture insieme). Una cella già in vista non ricompone
  scorrendo; i numeri si leggono per cella solo quando cambiano; il testo del numero c'è solo sui segni accesi.
- **Le pagine** arrivano a 120 elementi, la dopo quando mancano 40 alla fine; la stessa domanda dà la stessa lista
  (tornando da un album la griglia è già piena).
- **L'anteprima** legge un'immagine grande (al più 2048 px) solo per la pagina in vista e le vicine del visore, senza cache.
- **Il pannello** al posto della tastiera registra la schermata come sorgente del vetro, ma il vetro c'è solo a pannello
  aperto.

## Trappole

- **`FluidAttachmentMenu` vuole un `FluidGlassModalHost`** in scena, e il «+» col suo `Modifier.fluidTransitionSource` in
  testa ai modificatori: senza sorgente la scheda nasce dal centro.
- **Un indirizzo consegnato da un'altra app** si legge finché quell'app lo concede: per tenerlo, l'app ne fa una copia.
- **`FluidDeviceMediaSource` senza i permessi nel manifest** resta `NotAllowed` per sempre (Android risponde «no» senza
  mostrare niente): o i permessi, o il Photo Picker di Android.
- **Nella galleria dell'engine** tutte le sorgenti sono finte, anche quella del telefono (id `device`): sul tablet di prova
  non si legge mai la libreria vera, e il Photo Picker di Android non si offre.
- Le chiavi della scelta sono `"sorgente/id"` (`fluidMediaKey`): due sorgenti con lo stesso id interno non si confondono.

## Come si prova

- `engine-media`: `FluidMediaSourceContractTest` (il contratto **dal vero**: un provider in Robolectric con la sorgente
  finta, il lettore attraverso il `ContentResolver`: album, pagine, ricerca, miniature sul tubo, ordine della consegna,
  blocco, NAS spento, tetto, chi non può chiedere, le traduzioni dei `Bundle`), `FluidDeviceMediaSourceTest` (i tre regimi,
  le pagine e gli album contro un MediaStore finto, la consegna riletta, la ricerca come argomento),
  `FluidMediaPickerStateTest` (pagine, ordine e tetto, la consegna da due sorgenti, il rifiuto, i saltati, lo sblocco;
  R1: la pagina che non arriva e «Riprova», gli elementi scelti nel `Bundle`, la chiave senza elemento; R3: gli album che
  non arrivano non si tengono come nessun album). R3: nel MediaStore finto una pagina e gli album che lanciano o col
  cursore nullo lanciano anche dalla sorgente; nel contratto la risposta `error` di pagine e album è un errore, non un
  vuoto, e l'accesso resta pieno.
- `engine-ui`: `FluidSelectionLimitTest` (l'ordine dei tocchi, il tetto, il salvataggio, le parole del numero).
- `sample`: `F8p14MediaUiTest`, col dito vero e il vetro vero (la scelta col numero e lo scalare, il dito di lato, il tetto,
  la foto sola, gli album di due sorgenti, la ricerca per significato, il permesso negato e «solo alcune» col dialogo di
  Android finto e «Gestisci», lo sblocco e il NAS spento, TalkBack, l'anteprima dalla pressione lunga, il «+», il pannello
  al posto della tastiera anche col movimento ridotto); `R1cMediaPickerTest` (la scelta oltre la prima pagina che
  sopravvive alla ricreazione e si consegna intera, la chiave senza elemento che si dice, la sorgente che non risponde);
  `R3m1MediaTest` (gli album che non arrivano dicono «Riprova»; nel pannello tre tocchi svelti su una sorgente lenta
  allegano una foto sola, col tetto contato; il rifiuto e l'errore della consegna si dicono).
- In galleria: «Elementi nuovi» › F8.14, scene `selettore-foto`, `foto-profilo`, `accesso-foto`, `allegati-piu`,
  `allegati-tastiera` (`sample/…/novita/F8p14Scenes.kt`).

## Dove guardare

| file | cosa |
|---|---|
| `FluidMediaModel.kt` | elementi, album, domande, pagine, accesso, rifiuti, consegne, la richiesta dell'app, cio' che torna |
| `FluidMediaSource.kt` | il contratto di una sorgente e le sue regole |
| `FluidMediaSourceContract.kt` | il contratto fra app: azione, metodi, chiavi, traduzioni |
| `FluidMediaSourceProvider.kt` | il lato di chi serve (MyCloud Gallery in F12) |
| `FluidRemoteMediaSource.kt` | il lato di chi legge, la scoperta e la fiducia |
| `FluidDeviceMediaSource.kt` | il telefono: MediaStore, regimi, album, ricerca |
| `FluidFakeMediaSource.kt` | la sorgente finta |
| `FluidMediaPickerState.kt`, `FluidMediaPicker.kt` | lo stato, la consegna, la pagina del selettore |
| `FluidMediaThumbnails.kt` | la cache delle miniature |
| `FluidSystemPhotoPicker.kt` | il Photo Picker di Android |
| `FluidAttachmentPanel.kt` | la scheda del «+», il pannello al posto della tastiera, le fonti |
| `FluidMediaLabels.kt` | le parole (italiano e inglese) |
