# engine-sync

La sincronizzazione (F8.21, idea `sincronizzazione-offline`): **prima il registro, poi la rete**. Ogni modifica va subito
nel registro locale del dispositivo e l'app funziona tutta senza rete; quando la rete c'e', le modifiche partono **una volta
sola** verso il relay, sigillate da chi chiama; quello che arriva dagli altri dispositivi si **fonde** con le modifiche in
attesa. Pacchetto `dev.antigravity.fluidengine.sync`. **Opt-in**, senza interfaccia (il foglio della scelta e'
[revisions](revisions.md)). Riferimento Apple: CloudKit con `NSPersistentCloudKitContainer` e SwiftData sincronizzato.

**Pensato per l'Account Pampa** (`artifacts/ecosistema/account/`, F10.3, F10.4, F10.6): la destinazione principale e' il
relay cifrato dell'account (rotte `/v1/sync/{collection}` di `b/api.md` § 4.10). Ogni **spazio** (i dati di un'app di
una persona, oppure uno spazio condiviso fra piu' persone: una lista, un gruppo) ha il suo registro. **Il relay vede solo
byte sigillati**: la cifratura la mette l'account con il suo `SyncSealer`, l'engine non ha chiavi.

Catalogo completo delle firme: [`../api/sync.md`](../api/sync.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| il motore in un'app (registro su disco, WorkManager) | `FluidSync.open(context, relay, sealer)` e l'`Application` che implementa `FluidSyncHost` |
| aprire i dati di un'app o uno spazio condiviso | `sync.space(SyncSpaceId("…"))` |
| scrivere (subito nel registro, senza rete) | `space.edit(id) { text("titolo", …); longText("corpo", …); bool("fatto", true) }`, `space.create { … }`, `space.delete(id)` |
| leggere per l'interfaccia | `space.state` (`StateFlow<SyncSpaceState>`: elementi vivi, conflitti, modifiche in attesa) |
| lo stato nei componenti che ci sono gia' | `sync.status` (`SyncStatus`) per `FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction`; `sync.overview` per i numeri |
| sincronizzare adesso | `space.sync()` o `sync.syncAll()` (lo fa da solo `FluidSyncWorker` dopo ogni modifica, con la rete) |
| far scegliere fra due versioni | `space.state.value.conflicts` → il foglio di [revisions](revisions.md) → `space.resolve(id, campo, keep, shown)` |
| cifrare (la mette l'account) | un `SyncSealer` con i dati associati di `SyncSealContext` |
| provare la sincronizzazione senza rete | `MemorySyncRelay` (il relay in memoria) e `testing.FakeSyncRelay` (la rete che cade) |
| il registro in memoria (prove, scene) | `InMemorySyncStore` |
| un registro piccolo chiuso da me (la cassaforte) | `BlobSyncStore(load, save)`: tutto in un blob a ogni transazione (F10.8, i luoghi dello Store) |

## Come funziona

**Il documento.** Un elemento e' un `SyncDocument`: campi con un valore e lo **stamp** della scrittura che l'ha messo
(`SyncHlc`, orologio logico ibrido). I valori dicono come si fondono:

| valore | quando due dispositivi lo cambiano senza vedersi |
|---|---|
| `SyncValue.Text`, `Integer`, `Real`, `Bool`, `Bytes` | vince lo stamp piu' grande (l'ultimo), uguale su tutti i dispositivi |
| `SyncValue.LongText` | si fonde a tre vie, prima per righe e poi per parole dentro un passaggio conteso; due aggiunte nello stesso punto restano tutte e due. Se lo stesso passaggio e' cambiato in due modi, il campo tiene le due versioni (`SyncField.alternatives`) e chiede la scelta |
| la cancellazione | vince su tutto ed e' definitiva: il cestino da cui si recupera e' un campo dell'app |

**Gli orologi logici.** Lo stamp e' (millisecondi, contatore, dispositivo). Una modifica fatta dopo averne vista un'altra
ha sempre lo stamp piu' grande, anche con l'orologio del telefono indietro di ore. Il relay dice la sua ora a ogni
risposta, e l'orologio del dispositivo si corregge: due modifiche che non si sono viste le ordina l'ora vera.

**Il registro.** Per ogni elemento: lo stato locale, la base (l'ultima versione confermata dal relay) e la sua versione,
e le voci delle modifiche in attesa (`SyncJournalEntry`). Una modifica e la sua voce si scrivono in una transazione sola.
Le voci sono una per campo, ma le «modifiche in attesa» che vede chi usa l'app (`pending`, «3 modifiche in attesa») si
contano **per elemento**: una voce nuova con tre campi e' una modifica; «tieni tutte e due» ne fa due, l'elemento e la copia.
`SqliteSyncStore` lo tiene in SQLite della piattaforma (niente Room: nessun processore di annotazioni), in
`noBackupFilesDir`, fuori dal backup automatico.

**L'invio, una volta sola.** Le modifiche partono a pacchetti con una **chiave d'idempotenza**; il pacchetto e' salvato
nel registro **prima** di partire e si rimanda identico finche' non arriva la risposta. Se la rete cade a meta' (il relay
ha applicato, la risposta si e' persa) il rinvio ha la stessa risposta e non scrive due volte; se il relay ha gia'
dimenticato la chiave (24 ore), la propria scrittura che torna come «conflitto» e' riconosciuta come una conferma.

**L'unione.** Una scrittura passa solo se il relay ha ancora la versione su cui si e' lavorato (`baseVersion`); altrimenti
torna la riga attuale e il dispositivo fonde a tre vie (`SyncMerge`) e rimanda. Fondere e' idempotente: una versione gia'
vista non cambia niente.

**Pietre tombali e compattazione.** Le voci confermate se ne vanno dal registro; le pietre tombali restano 90 giorni
(come le lapidi del relay e di Notes), poi se ne vanno; un elemento nato e cancellato senza mai partire non lascia niente.

**Fermo per mesi.** Se il relay ha gia' tolto pietre tombali piu' nuove del cursore del dispositivo, risponde
`CursorExpired`: il dispositivo rilegge tutto, tiene le sue modifiche in attesa, e un elemento confermato che il relay non
ha piu' era stato cancellato. Le modifiche fatte a un elemento cancellato altrove sono superate dalla cancellazione
(`SyncSpaceReport.supersededByDelete`).

**Il sigillo.** Verso il relay passano solo i byte di `SyncSealer.seal`, legati con i dati associati a spazio, elemento e
versione (`SyncSealContext.associatedData()`): un relay che sposta o ripresenta un contenuto fa fallire l'apertura, e
l'elemento resta com'era (stato `ERROR`, codice `unreadable`). Se il sigillo non si fa (la chiave dell'account non
c'e' ancora) le modifiche aspettano sul telefono e lo stato lo dice (codice `seal`). Il registro sul telefono e' la verita' locale dell'app,
come il suo database; chi lo vuole cifrato anche li' passa un `SyncStore` che cifra (`engine-vault`, F8.23).

**Una riga che non si apre non ferma lo spazio per sempre (R2).** Una riga che questo dispositivo non sa aprire (la
chiave di un'epoca che non ha ancora, un engine piu' nuovo, un contenuto rovinato) ferma il ritiro per qualche giro
(`SyncConfig.unreadableRetries`, tre di serie: il tempo perche' una chiave in ritardo arrivi da sola, con lo stato
`unreadable`). Poi resta da parte, col suo contenuto sigillato, e il cursore la passa: le altre righe arrivano e le
modifiche partono. L'elemento sta in `SyncSpaceState.unreadable` (e in `SyncSpaceReport.unreadable`); a ogni
sincronizzazione si riprova, e quando si apre si applica ed esce da li'. Una versione nuova dello stesso elemento la
supera, e una riga che il relay, riletto tutto, non ha piu' se ne va. Resta da parte anche dopo un riavvio.

## Usarlo in un'app

```kotlin
class App : Application(), FluidSyncHost {
  override val fluidSync by lazy { FluidSync.open(this, relay = account.syncRelay(), sealer = account.syncSealer()) }
}

val notes = app.fluidSync.space(SyncSpaceId("notes"))
val id = notes.create { text("titolo", "Spesa"); longText("corpo", "latte\npane\n") }
val state by notes.state.collectAsState()          // elementi, conflitti, modifiche in attesa
FluidSyncAction(status = app.fluidSync.status.collectAsState().value)
```

`FluidSync.open` mette il registro su disco, il nodo dell'installazione (`EngineInstallId` di
[storage](storage.md)) e l'invio con WorkManager: ogni modifica chiede un lavoro unico (`FluidSyncWork.request`, due
secondi d'attesa per raccogliere una raffica, vincolo di rete, pause crescenti dopo un errore); `FluidSyncWork.schedulePeriodic`
aggiunge un giro ogni sei ore. WorkManager si avvia con l'app (androidx.startup): un'app che non lo vuole all'avvio lo
inizializza a mano.

## I limiti, scritti

- La destinazione e' il relay dell'account; gli adattatori per Drive, WebDAV e NAS non ci sono (c'e' l'interfaccia,
  `SyncRelay`).
- Prima del primo contatto col relay, due modifiche indipendenti le ordina l'ora del telefono.
- Un elemento cancellato e gia' tolto dal registro, riscritto con lo **stesso** identificativo, rinasce: per gli elementi
  nuovi si usa `create`, che da' un identificativo casuale.
- Il relay non puo' spostare o ripresentare un contenuto senza che il sigillo se ne accorga; puo' pero' servire a un
  dispositivo nuovo una versione vecchia come ultima (la freschezza richiede una testa firmata, che non c'e').
- Lo stato di uno spazio si ricalcola intero a ogni modifica: va bene per centinaia di elementi, non per centomila.

## Le prove

Il banco (`engine-sync/src/test`): dispositivi finti (due, tre, uno fermo per quattro mesi), orologi sfasati di ore, la
rete che cade a meta' dell'invio, il doppio invio, la chiave d'idempotenza scaduta, le cancellazioni incrociate, la
compattazione, i conflitti sullo stesso campo e sui testi lunghi, «tieni tutte e due», il sigillo (nessun testo in chiaro
verso il relay, un contenuto spostato non si apre), il processo che muore con un invio in volo, una transazione interrotta.
La prova generativa (`SyncPropertyTest`, 160 corse) controlla che **tutti convergano, nessuna modifica si perda e
nessuna si applichi due volte**. In Robolectric: il registro su SQLite e il lavoro di WorkManager.
- `BlobSyncStoreTest` (F10.8): il blob che torna uguale a un avvio dopo, la transazione annullata se il salvataggio cade.
- `SyncUnreadableR2Test` (R2): la riga che non si apre ferma lo spazio per tre giri, poi resta da parte e le altre passano;
  si apre quando arriva la chiave, resta da parte dopo un riavvio, e una versione nuova la supera.

## File

- `engine-sync/src/main/kotlin/dev/antigravity/fluidengine/sync/` — `FluidSync.kt` (motore, stato, configurazione),
  `SyncSpace.kt` (lo spazio: scrivere, sincronizzare, scegliere), `SyncDocument.kt`, `SyncClock.kt`, `SyncMerge.kt`,
  `SyncCodec.kt`, `SyncRelay.kt` (l'adattatore e il sigillo), `SyncStore.kt`, `SqliteSyncStore.kt` e `BlobSyncStore.kt` (il registro),
  `MemorySyncRelay.kt`, `FluidSyncWork.kt` (WorkManager), `testing/FakeSyncRelay.kt`.
- Le scene: `sample/.../novita/F8p21Scenes.kt` (`sync-due-dispositivi`, `sync-conflitto`, `sync-fermo`).

**Dipendenze del modulo.** `api` su `engine-storage` (e quindi `engine-foundation`) e su coroutines-android;
`implementation` WorkManager 2.11 (Apache-2.0), che chiede all'app `compileSdk` 35 o piu'. Nessuna dipendenza da Compose.
