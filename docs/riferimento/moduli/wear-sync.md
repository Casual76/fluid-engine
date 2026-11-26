<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/wear-sync.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-wear-sync

**Il ponte fra il telefono e l'orologio** (4.0, F8.25): lo **stato** che il telefono pubblica e l'orologio mostra anche
ad app chiusa (nelle Tile e nelle complicazioni di `engine-wear`), e l'**evento del polso con la ricevuta**, che si salva
anche col telefono lontano e passa da «In coda» a «Registrato» solo quando il telefono l'ha salvato davvero. Pacchetto
`dev.antigravity.fluidengine.wear.sync`.

È un modulo **opt-in e senza interfaccia**, lo stesso sui due lati: l'app del telefono lo chiede per nome
(`-Modules …,engine-wear-sync`), l'app dell'orologio lo riceve con `engine-wear`. **Non impone librerie**: il nucleo è
Kotlin puro (il formato, la coda, il registro, gli stati, l'archivio su file); l'adattatore del Data Layer di Wear OS
usa `play-services-wearable` **solo se l'app lo aggiunge lei** (`compileOnly`, come Media3 per `engine-viewer`). Un'app
che porta i messaggi in un altro modo passa i byte a `FluidWearSync.route`.

I riferimenti Apple: `WCSession.transferUserInfo(_:)` di Watch Connectivity (il trasferimento si accoda e arriva quando i
due si ritrovano) e i widget di WidgetKit che l'orologio mostra dalla timeline dell'app (fiducia alta per la coda, media
per il resto). La **ricevuta dopo il salvataggio** non c'è in Apple: è dell'engine (scheda `wear-con-ricevuta`).

Catalogo delle firme: [`../api/wear-sync.md`](../api/wear-sync.md) (generato). Le Tile e le complicazioni:
[`wear.md`](wear.md), «Tile e complicazioni».

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| far vedere sull'orologio uno stato del telefono (la prossima lezione, le non lette, il timer) | telefono: `FluidWearDataLayer(dataClient, FluidWearSync.get(ctx, Phone)).publish(FluidWearStatus(...))`; orologio: `FluidStatusTileService` / `FluidStatusComplicationService` di `engine-wear` e `FluidWearListenerService` che chiede il ridisegno |
| togliere lo stato (non è più vero) | `withdraw(key)`; per «vecchio ma ancora utile» meglio `staleAfterMillis` (la Tile dice «Non aggiornato») |
| salvare un evento al polso col telefono lontano | orologio: `sync.outbox.enqueue(type, payload, ttlMillis)` poi `dataLayer.flush()` |
| mostrare al polso «In coda» / «Registrato» | `sync.outbox.deliveries` (`StateFlow`), `FluidWearDeliveryState.label` |
| un comando con effetto subito («riproduci») | `ttlMillis` corto (30 s): scade al polso e il telefono non lo applica dopo |
| salvare sul telefono l'evento una volta sola | `FluidWearListenerService.onEvent`: salva **col suo id** e torna `true`; la ricevuta parte da sola dopo |
| un trasporto che non è il Data Layer | `FluidWearSync.route(path, bytes)` e i byte di `FluidWearFormat` |

## Come è fatto

- **Il formato** (`FluidWearFormat`): `FWS`, un byte di versione (oggi **1**), un byte di genere (stato, evento, ricevuta),
  poi campi etichettati (etichetta, lunghezza, valore). Le due promesse: **un campo sconosciuto si salta** (un'app più
  nuova aggiunge campi, una più vecchia legge lo stesso) e **l'etichetta 1 di un evento è sempre il suo id** (un telefono
  vecchio che non sa leggere una versione futura risponde «Non registrato» con l'id giusto, invece di lasciare il polso in
  coda per sempre). `decode` non lancia mai: `Unsupported(version, eventId)` o `Unreadable(reason)`.
- **Gli indirizzi** (`FluidWearPaths`): `/fluid-wear/status/<chiave>`, `/fluid-wear/event/<id>`,
  `/fluid-wear/receipt/<id>`. Chiavi e id solo lettere, cifre, `-`, `_`, `.` (fino a 128).
- **Il ruolo** (`FluidWearRole`): il telefono tratta solo gli eventi, l'orologio solo gli stati e le ricevute. Così un
  elemento che il Data Layer rimanda anche a chi l'ha scritto non si tratta due volte.
- **La coda del polso** (`FluidWearOutbox`): `enqueue` scrive su disco **prima di tornare**; `due()` dà gli eventi mai
  consegnati e quelli consegnati più di **15 minuti** fa senza ricevuta (il telefono può averli ricevuti e non salvati);
  `due(resendAll = true)` li dà tutti. Ogni consegna ha un **numero** che cambia i byte (`encode(event, attempt)`): il Data
  Layer non avvisa per un elemento rimesso identico. Un evento oltre la scadenza si chiude «Scaduto» e non si consegna più;
  **se poi arriva la ricevuta del telefono, vince il telefono** (l'aveva salvato in tempo). Un evento chiuso resta **un
  giorno**, poi si toglie; tetto 200 voci (si tolgono prima i chiusi più vecchi).
- **Il registro del telefono** (`FluidWearInbox`): `receive` dice `New`, `Repeated(receipt)` o `Expired(receipt)`;
  `confirm(id, outcome)` registra su disco e dà la ricevuta; chiamato due volte dà la prima. Ricorda **una settimana**, al
  massimo 2000 eventi. `handle(event) { salva }` fa i tre passi in fila. **L'unico buco**: il telefono si spegne fra il
  salvataggio e `confirm` → l'evento torna nuovo alla riconsegna. Per questo l'app salva **l'id con il dato**, e un
  salvataggio ripetuto non raddoppia.
- **Gli stati** (`FluidWearStatusStore`): l'ultimo per chiave, su disco. Uno più vecchio di quello tenuto
  (`updatedAtMillis` minore) si scarta: il Data Layer non promette l'ordine. È il file che le Tile e le complicazioni
  leggono: niente Play services al momento del disegno, quindi lo stato c'è ad app chiusa, col telefono lontano e dopo un
  riavvio.
- **L'archivio** (`FluidWearFileStore`): pochi file piccoli riscritti interi (file accanto, `fsync`, spostamento atomico),
  in `noBackupFilesDir/fluid-wear-sync` (`FluidWearSync.get`): fuori dal backup, perché una coda ripristinata su un altro
  telefono rimanderebbe eventi già registrati. Un file rotto si ricomincia vuoto, non ferma l'orologio.
  `FluidWearMemoryStore` per le prove e la galleria.
- **I fili**: tutti i metodi si chiamano da qualunque filo (un lucchetto per oggetto) e pubblicano in `deliveries` e
  `statuses` **una mappa o una lista immutabile nuova, intera** (la regola degli stati scritti in sottofondo,
  `regole.md`); in Compose si leggono con `collectAsState`.

## Il Data Layer (l'app porta Play services)

`play-services-wearable` è **gratuito ma non open source** (Android Software Development Kit License): l'engine lo
permette col permesso della GPL-3.0 § 7 (`LICENSES/Permesso-librerie-Google.md`), e il modulo **non lo porta**. L'app che
usa l'adattatore aggiunge `implementation "com.google.android.gms:play-services-wearable:20.0.1"` (la versione provata) in
tutti e due i moduli (telefono e orologio). `checkWearSyncRuntime` controlla che resti fuori dal runtime del modulo.

- **Elementi di dati, non messaggi**: un `putDataItem` riesce anche col telefono lontano, Play services lo tiene su disco e
  lo consegna appena i due si ritrovano, anche dopo un riavvio. Ogni messaggio sta in un elemento suo, campo `fws`,
  **urgente** (`setUrgent`).
- `FluidWearDataLayer(dataClient, sync)`: `publish(status)` e `withdraw(key)` (telefono); `flush()` (polso, dopo
  `enqueue`); `sendReceipt(receipt)` (telefono); `pullStatuses()` (orologio, all'avvio della sua app: gli stati arrivati
  prima); `forget(eventId)` toglie evento e ricevuta dal Data Layer quando l'evento è chiuso (gli elementi non scadono da
  soli).
- `FluidWearListenerService`: il servizio pronto. L'app lo estende, dice il `role`, e lo dichiara:

```xml
<service android:name=".PonteOrologio" android:exported="true">
  <intent-filter>
    <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
    <data android:scheme="wear" android:host="*" android:pathPrefix="/fluid-wear" />
  </intent-filter>
</service>
```

```kotlin
// Telefono (ClasseViva): pubblica la prossima lezione.
FluidWearDataLayer(Wearable.getDataClient(context), FluidWearSync.get(context, FluidWearRole.Phone))
  .publish(FluidWearStatus("lezione", "Matematica", "10:00", detail = "Aula 3", progress = 0.4f,
    updatedAtMillis = System.currentTimeMillis(), staleAfterMillis = fineLezione))

// Telefono (Fluidify): riceve il preferito del polso.
class PonteTelefono : FluidWearListenerService() {
  override val role = FluidWearRole.Phone
  override suspend fun onEvent(event: FluidWearEvent): Boolean =
    if (event.type == "preferito") { preferiti.aggiungi(event.payload, idEvento = event.id); true } else false
}

// Orologio: lo stato arriva, si ridisegnano Tile e complicazioni.
class PonteOrologio : FluidWearListenerService() {
  override val role = FluidWearRole.Watch
  override suspend fun onStatusChanged(status: FluidWearStatus) =
    FluidWearSurfaces.refresh(this, tiles = listOf(LezioneTile::class.java), complications = listOf(LezioneComplicazione::class.java))
}

// Orologio: il polso salva un preferito.
val sync = FluidWearSync.get(context, FluidWearRole.Watch)
sync.outbox.enqueue("preferito", """{"brano":"42"}""")
FluidWearDataLayer(Wearable.getDataClient(context), sync).flush()
```

## Trappole

- **`onEvent` di serie torna `false`**: un'app che non lo scrive risponde «Non registrato» a tutto, che è la verità.
- **Il ruolo è per processo** (`FluidWearSync.get` con un ruolo diverso lancia): un'app ha un lato solo.
- **L'ora**: la scadenza la decide l'ora di chi la guarda (il polso nella coda, il telefono nel registro). Gli orologi di
  telefono e orologio abbinati si allineano dalla rete; uno scarto di minuti sposta di minuti una scadenza.
- **Cambio del telefono abbinato**: la coda del polso resta, e riconsegna al telefono nuovo. Un evento che il telefono
  vecchio aveva salvato senza far tornare la ricevuta si salva anche sul nuovo (registri diversi). Non provato su
  dispositivi veri.
- **Payload**: al massimo 64 KiB in UTF-8 (il Data Layer tiene 100 KB per elemento). Per un file serve un `Asset`.

## Verifica

- `FluidWearFormatTest` (8): andata e ritorno, il numero della consegna, i campi sconosciuti saltati, le versioni future
  (con l'id), i byte rotti, l'esito sconosciuto mai «Registrato», i tetti.
- `FluidWearReceiptFlowTest` (10): i due lati su disco e un collegamento finto come il Data Layer — «In coda» col
  telefono lontano e dopo un riavvio, poi «Registrato»; la consegna ripetuta (anche a un telefono riavviato) salvata una
  volta e con la stessa ricevuta; la scadenza al polso e al telefono; la ricevuta che vince sulla scadenza; la versione
  futura; i ruoli; la pulizia; la coda rotta; `handle`.
- `FluidWearStatusStoreTest` (4) e `FluidWearDataLayerTest` (5, Robolectric con le classi vere del Data Layer: richiesta
  urgente all'indirizzo giusto, `DataMap` coi byte, smistamento di `onDataChanged`).
- **Non provato**: il Data Layer vero fra un telefono e un orologio (non c'è un orologio). Va provato con un orologio
  abbinato: stato pubblicato ad app dell'orologio chiusa, evento col Bluetooth spento e poi acceso, riavvio dell'orologio
  con un evento in coda.

## Licenza

Il codice del modulo è dell'engine. `play-services-wearable` non è nel modulo: la porta l'app, con la Android Software
Development Kit License di Google e il permesso aggiuntivo della GPL-3.0 § 7 dell'engine.
