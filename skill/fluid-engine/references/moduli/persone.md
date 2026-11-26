# engine-people

Le persone dell'Account Pampa sul telefono (F10.6, `artifacts/ecosistema/account/d/server-e-chiavi.md` § 10;
`progetto.md` § 3.5–3.12): la crittografia di Codex portata nell'engine, la persona divisa in radice e chiavi di lavoro,
la casella con la coda in uscita, gli spazi condivisi e la vicinanza con la runa. Pacchetto
`dev.antigravity.fluidengine.people`. **Opt-in**, lo usano **Pampa Store** (tutto) e **Pampa Mail** (la crittografia
delle conversazioni). Le altre app vedono solo la fila delle persone, «Condivisi con te», «Da X» e «Rispondi»,
attraverso lo Store ([account](account.md), `PampaPeople`) e i componenti di [account-ui](account-ui.md).

Catalogo completo delle firme: [`../api/people.md`](../api/people.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| la persona nello Store (radice in classe madre, chiavi di lavoro in classe consegna) | `PersonaStore(dir, vault)`, `create(nome)`, `rotateWork()`, `exportSealed`/`importSealed` sotto `K_com(persona)` |
| parlare col server delle persone | `PeopleClient(persona, PeopleStateStore(file, vault), PeopleApi(UrlPeopleHttp(base, versione), auth))` |
| aggiungere col codice | `client.lookup("PMP-7K3P-2M9Q")` (la scheda si controlla sul telefono) e `client.request(card, saluto)` |
| aggiungere col QR (verificato per tutti e due) | `client.newQr()` → `qr.encode()` nel QR; chi inquadra: `client.requestFromQr(PersonQr.decode(testo)!!)` |
| aggiungere di persona, con la runa | `NearbyPairing(client).handshake(link)` → `session.rune` sui due schermi → `session.confirm()` |
| ritirare (campanello, canale aperto) | `client.onBell()` → `List<PeopleEvent>`; nello Store `PeopleBell(client, PeopleDispatcher(client, AndroidAppNotifier(ctx))).ring(data)` |
| le quattro parole al telefono | `client.words(codice)` (pronte quando lo scambio con l'impegno è finito), poi `markWordsVerified` |
| mandare un messaggio o una cosa condivisa | `client.sendMessage(codice, json)`; «I tuoi dispositivi»: `client.sendToMyDevices(json)` |
| un gruppo | `createGroup`, `groupInvite` (il QR da 24 ore), `joinGroup`, `approve`, `groupMessage`, `removeFromGroup` |
| una lista condivisa | `SharedSpaces(client)`: `create`, `share`, `removeMembers`; `open(id, FluidSync(store, spaces.relay, spaces.sealer, nodo), SharedListItem.Codec)` |
| i metodi `people.*`, `share.*`, `devices.send` del provider | `AccountProtocolHandler(…, people = PeopleProviderMethods(client))` di [account-host](account-host.md) |
| la coda in uscita in sottofondo | `PeopleClient(…, scheduler = PeopleWork.scheduler(context))`, e la `Application` dello Store come `PeopleWorkHost` |
| le prove senza rete | `testing.FakePeopleServer` (le regole del Worker vero), `testing.MemoryPersonVault`, `nearby.FakeNearbyLink` |

## Come funziona

**La crittografia** (`people.crypto`), con Bouncy Castle come tutto l'account. Le formule e le etichette sono quelle del
contratto del server (`Pampa-account/contract/v1/e2e.json` → `people`); le prove rifanno al byte i vettori usciti dal
codice del server e quelli di Codex.

| pezzo | formato | da Codex |
|---|---|---|
| `PersonCode` | `PMP-XXXX-XXXX`: 40 bit di `SHA-256("pampa/v1/codice|" ‖ radice)` in Crockford; la casella dalla stessa radice | `CodexId` col prefisso `PMP-` (Q10: niente `CDX-`) |
| `PersonCard` | JSON firmato dalla radice, coi byte così come sono (`raw`, `sig`); `verify` controlla codice, casella, firme, e rifiuta una chiave di cifratura di ordine piccolo (R2) | `ContactCard`, senza uid Firebase |
| `Cdxb` | `"CDXB" ‖ u32 ‖ u32 ‖ HPKE ‖ firma`, con la scheda di chi manda dentro | `InboxParcel`, rifatta con HPKE (F10.5) |
| `Pmx1` | `"PMX1" ‖ epoca ‖ id ‖ nonce ‖ AES-GCM`, una chiave per messaggio, le due caselle nei dati associati | `MessageEnvelope` (CDX3) |
| `Cdxm` | i file a blocchi da 64 KiB | `MediaCipher`, **identico al byte** |
| `SpaceRecordCipher` | `nonce ‖ AES-GCM` con la firma di chi scrive dentro | nuovo |
| `PairCode` | chiave di coppia, gemma, quattro parole (elenco BIP-39 italiano, `ItalianWords`) con l'**impegno** | gemma dell'alpha |
| `NearbyHandshake`, `PampaRune` | saluto, sfida firmata, runa di 4 glifi dalle due effimere | `NearbyHandshake`, `RuneAlphabet`, `SeededRandom` |
| `PersonQr` | `PMQ1:` + scheda, invito da 24 ore e un segreto che il server non vede | il QR dell'alpha |
| `EpochKeyring` | le chiavi a epoche di gruppi e spazi: chi esce tiene il passato | `GroupSecret` |

**Le quattro parole e il «compleanno».** Le parole nascono dalla chiave di coppia e da due nonce scambiati con un
impegno, come lo SAS di ZRTP: chi comincia (chi chiede, o chi rinnova le chiavi) manda solo l'impegno, l'altro risponde
col suo nonce, poi il primo rivela. Un server che volesse due parole uguali per due schede false dovrebbe scegliere le
sue chiavi prima di vedere il nonce dell'altro: niente ricerca del compleanno, una possibilità su 2⁴⁴ a tentativo. Il
nonce di chi risponde nasce dalla sua chiave di lavoro e dall'impegno, così tutti i suoi dispositivi mostrano le stesse
parole. Dopo un rinnovo le parole cambiano: `ContactNotice.WORDS_CHANGED_THEIRS` («Le parole con X sono cambiate perché X
ha rinnovato le chiavi») e la verifica a distanza è da rifare (`PeoplePolicy.RENEWAL_DROPS_WORDS_VERIFICATION`).

**Le classi delle chiavi (Q7 A).** `PersonVault` ha due ruoli: `ROOT` (la radice, classe madre: solo a telefono
sbloccato, per firmare la scheda e le chiavi nuove) e `WORK` (chiavi di lavoro, rubrica, posta: classe consegna, dal
primo sblocco). Lo Store lo collega alla sua `CustodyVault` con `KeyClassPolicy`.

**La coda in uscita (critica M3).** Ogni busta si chiude e si firma quando la prepari e aspetta in
`PeopleState.outbox`: senza rete resta, con un 429 o la casella piena aspetta, con `gone` o `not_found` si toglie e lo
dice (`FlushReport`). Riprovarla non chiede chiavi, quindi va anche col telefono in tasca. Oltre a ogni invio, ritiro e
campanello, la riprova `PeopleOutboxWorker` con WorkManager quando la rete torna (`PeopleWork.scheduler`).

**I dispositivi della stessa persona.** Le azioni fatte su un dispositivo (richiesta, accettazione, verifica, rimozione)
vanno agli altri con una CDXB a sé stessi; un telefono nuovo riprende persona e rubrica dalle copie sotto
`K_com(persona)` e `K_com(rubrica)`.

**Lo Store e le app.** `PeopleDispatcher` mette ciò che arriva nella posta dell'app giusta e la avvisa col broadcast
esplicito `CHANGED` (`what = share`); l'app rilegge dal provider firmato (`share.received`) e mostra la sua notifica. Se
l'app non c'è, lo Store mostra lui una notifica semplice (T21). **Niente si perde a metà ritiro** (R1): il ritiro scrive
le consegne da smistare (`PeopleState.undispatched`: le cose condivise, «I tuoi dispositivi», le richieste) nello stesso
salvataggio del cursore e degli id visti, e `PeopleDispatcher.dispatch` le toglie nello stesso salvataggio della posta.
Se la seconda pagina cade, il campanello scade o lo Store riparte, la condivisione arriva al giro dopo, una volta sola.
Una richiesta già accettata o ignorata nel frattempo non si annuncia più.

**Contro le buste storte e i ritorni indietro (R2).** Il server può rimandare buste vecchie e genuine, e chiunque può
mandare una richiesta col contenuto storto. Le regole:
- **una busta storta è di quella busta sola**: un campo che non è base64, un numero che non è un numero, un tipo
  sbagliato diventano `PeopleEvent.Detected("busta malformata")`, e il cursore la passa. Solo la rete, i tetti
  (`retryable`) e l'accesso da rinnovare (401–403) fermano il ritiro, che riprende la pagina al giro dopo;
- **la scheda di un contatto non scende mai**: fra quella che ho e quella di una busta resta la più nuova (epoca di
  lavoro più alta, a pari epoca l'ora più recente). Lo stesso per la rubrica degli altri miei dispositivi, per la copia
  sotto `K_com(rubrica)` e per gli scriventi di uno spazio;
- **gli spazi e i gruppi tengono l'epoca più alta**: uno stato vecchio di uno spazio porta solo chiavi, non il gettone di
  prima né chi era stato tolto (`SpaceMembership.mergeIncoming`); uno stato di un gruppo più vecchio di quello che ho
  è `Detected`;
- **la persona ripresa dal server non torna indietro**: `importSealed` unisce le chiavi di lavoro e tiene la scheda più
  nuova; `importRubrica` rifiuta una copia con una versione più vecchia dell'ultima ripresa (`PampaCryptoException`);
- **un contatto tolto resta tolto**: togliere lascia una lapide per 90 giorni (`rubrica-via` porta l'ora), e una rubrica
  vecchia di un altro dispositivo, o una copia, non rimette un contatto aggiunto prima di quell'ora. Aggiungerlo di
  nuovo dopo vale;
- **un codice che ho già non si sostituisce**: `request` con un contatto della stessa radice lo rende com'è, con
  un'altra radice è `PeopleDetected` (la scheda falsa di S5); così anche l'accettazione e la vicinanza.

**Prima offline anche per le impronte (R2, R6).** Bloccare, togliere, sbloccare e ogni gettone nuovo (richieste,
accettazioni, gruppi, vicinanza) valgono subito sul telefono: l'impronta da aggiungere o togliere nella casella va in coda
con le buste, e la coda la manda **prima** di loro. La coda si salva dopo ogni chiamata, e il lavoro in sottofondo la
finisce anche se WorkManager lo sostituisce: niente doppioni.

**Un messaggio prima della sua chiave (R2).** Una PMX1 di un contatto (o di un membro di un gruppo) con un'epoca della
conversazione che non ho ancora resta da parte, cifrata, fino a 100 buste, 1 MiB e 30 giorni; si apre quando arriva la
«chiave-chat» (o lo stato del gruppo) e torna come `PeopleEvent.Message` col suo numero di busta.

**I record degli spazi portano le chiavi di chi scrive (R2).** Dentro il record, accanto alla firma, c'è il campo
facoltativo `k`: le chiavi di lavoro pubbliche di chi scrive, firmate dalla sua radice. Chi legge controlla la firma con
la scheda fra gli scriventi se è della stessa epoca, altrimenti con `k`, se la radice è quella che il proprietario ha
messo fra gli scriventi: chi rinnova le chiavi resta leggibile per tutti. Un record senza `k` (engine di prima) si legge
come prima. Ogni guasto di un record è `SyncSealException`, mai un'altra eccezione.

## Trappole

- `PeopleClient` vuole la persona (`PersonaStore.create`) prima di tutto: senza, `identity()` fallisce.
- Un dispositivo che ritira **non** rimanda la rubrica per ciò che riceve (lo riceve anche l'altro): solo per ciò che fa.
- `SharedSpace.put/add/remove` senza gettone di scrittura sono `IllegalStateException`: una scrittura che il server
  rifiuterebbe resterebbe in coda e fermerebbe anche la lettura dello spazio.
- Il canale aperto (`PeopleSocket`) va solo ad app aperta; in sottofondo suona il campanello ([push-fcm](push-fcm.md)).
  Se per due ping e mezzo non arriva niente (nemmeno il «pong»), la lettura scade e il canale si chiude con 1006 (R2).
- Chi ritira con `pickup()` senza `PeopleDispatcher` lascia le consegne nella coda (fino a 500): il primo `dispatch` le
  smista. Di `dispatch(events)` contano solo la coda e le consegne fatte a mano, oltre il cursore.
- **Lo stato che non si apre più** (R1): con la chiave del Keystore persa o invalidata, o il file troncato,
  `PeopleStateStore` mette da parte il file accanto (`persone.bin.rotto-<ora>`, mai buttato), riparte vuoto e lo dice in
  `setAside`. Lo Store lo mostra e riprende la rubrica dal server (`importRubrica`). Con la classe consegna chiusa
  (`PersonLockedException`, prima del primo sblocco) non tocca niente: si riprova dopo.

## Le prove

`CodexVectorsTest` (i vettori degli standard di Codex e gli stessi byte della copia di riferimento di Codex),
`ServerVectorsTest` (i vettori del server), `PeopleCryptoTest`, `PersonaTest`, `PeopleBenchTest` (tre persone con due
dispositivi, gruppi da 30 e 100, server ostile, telefono rubato, campanello finto, coda in uscita, tetti),
`PeopleHttpTest` (MockWebServer e il WebSocket di OkHttp), `SharedSpaceTest`, `NearbyPairingTest`, `PeopleStoreTest`
(anche il ritiro che cade alla seconda pagina o scade, con lo Store che riparte), `PeopleStateStoreTest` (il file che non
si apre),
`PeopleWorkTest` (la coda con WorkManager, senza rete e col telefono in tasca).
`PeopleR2Test` e `PeopleSocketR2Test` (R2: busta malformata, schede, spazi e gruppi rimandati, persona e rubrica vecchie,
contatto tolto che torna, blocco senza rete, messaggio prima della chiave, coda fermata a metà, contatto ri-aggiunto, il
canale che tace).
Il passaggio Store → app col provider vero è `PeopleBridgeTest` in `engine-account-host`.
