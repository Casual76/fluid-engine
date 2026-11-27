# engine-ai-memory: la memoria di PampAI

**La memoria che funziona** (4.0, F9.3 e F9.3b, idea `ricordi-dell-assistente`): i fatti che l'assistente ricorda
dell'utente («è in 4F al liceo Galilei», «è vegetariana», «la verifica di matematica è venerdì 27»), con la fonte, la
data e la scadenza, cifrati nella cassaforte, e la ricerca per pertinenza che decide quali entrano in una domanda. **Tutto
passa dalla memoria di PampAI**, anche i ricordi che nascono in un'altra app Pampa (nota di Alessio), ed è **una memoria
sola**: il profilo della ricerca di oggi (i fatti imparati dai dati delle app, con le prove, le fonti autorizzate e le
esclusioni) ci entra dentro (risposta 6 di Alessio, F9.3b). **Di serie chiede** prima di ricordare («Vuoi che lo
ricordi?» con Ricorda e No); con «Ricorda da solo» salva e lo dice con Annulla (risposta 2). Pacchetto
`dev.antigravity.fluidengine.ai.memory`, in tre pezzi:

| dove | cosa | chi lo include |
|---|---|---|
| `engine-ai` (`ai/memory/`) | le parole comuni: `AiMemory`, `AiMemorySource`, `AiMemoryNotice`, il formato con cui un'app propone (`AiMemoryProposal`, `AiMemoryProtocol`, `AiMemoryProposalResult`) | tutte le app con un assistente o col ponte |
| **`engine-ai-memory`** (opt-in) | il motore: `AiMemoryBook` (cassaforte, contraddizioni, Annulla, revoca, tetti), `AiMemoryExtractor` (dopo i turni), la pertinenza e il blocco del prompt, `AiMemoryInbox` (i ricordi delle altre app), `AiMemoryLegacyImport` (Room v7) | **solo PampAI** |
| `engine-ai-ui` (`aiui/memory/`) | `FluidMemoryNoticeCard` («Vuoi che lo ricordi?» con Ricorda e No, «Ho ricordato…» con Annulla), `FluidMemoryNotices.notification`, `FluidMemoryPage` («Cosa ricordo di te»), `FluidMemoryStrings` | PampAI, e le app che mostrano l'esito di una proposta |

Catalogo delle firme: [`../api/ai-memory.md`](../api/ai-memory.md) (generato). L'orchestratore e i provider sono in
[`ai-orchestrator.md`](ai-orchestrator.md) e [`ai-provider.md`](ai-provider.md); la cassaforte in [`vault.md`](vault.md);
l'indice in [`search.md`](search.md); il ponte in [`ai-bridge.md`](ai-bridge.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| aprire la memoria di PampAI (cifrata) | `AiMemoryBook.open(context)` (la sezione `ai-memory` della cassaforte), fuori dal thread principale |
| far nascere i ricordi dopo ogni turno | `book.afterTurn(AiMemoryTurn(...), AiMemoryExtractor(provider, modelloPiccolo, usageSink = consumi))` |
| «ricordati che…» chiesto dall'utente (il tool `ricorda`) | `book.remember(proposta, turn.source, turn = turn)`; con `NeedsConfirmation` la conferma di PampAI, poi `confirmed = true` |
| mostrare la domanda o «Ho ricordato…» sotto la risposta | `FluidMemoryNoticeCard(notice, onUndo = { book.undo(notice.id) }, onAnswer = { book.answer(notice.id, it) })`, con gli avvisi di `book.notices` |
| …come notifica di vetro (un'altra app, l'overlay) | `host.show(FluidMemoryNotices.notification(notice, onUndo, onAnswer = …))` |
| l'interruttore «Ricorda da solo» | `book.autoSave` e `book.setAutoSave(acceso)` (resta nel file cifrato); `FluidMemoryPage(autoSave = …)` |
| le domande che aspettano | `book.pending` (avvisi `KindAsk`), `book.answer(id, accept, exclude)`; `FluidMemoryPage(pending = …)` |
| non imparare più un argomento, un'app, una fonte | `book.exclude(AiMemoryExclusion(key = "salute"))`, `book.include(…)`, `book.forget(id, exclude = true)` |
| portare il profilo della ricerca (Room v7) | le righe dei DAO `aria_*` come `AiMemoryProfileRow` → `book.importProfile(righe, esclusioni)`; senza Room `AiMemoryLegacyImport.readPampaiProfile(file)` |
| una fonte autorizzata revocata | `book.forgetFromGrant(grantId, record)`: via i fatti imparati da lei, restano quelli detti dall'utente |
| il modello del telefono (AICore, F9.5) | `AiMemoryConfig(onDevice = modello)` allarga la domanda; `AiMemoryExtractor.onDevice(modello)` estrae sul telefono |
| il blocco dei ricordi per il prompt di una domanda | `book.prompt(domanda)` → `AiMemoryPrompt` (testo, gettoni, ricordi): vuoto se niente c'entra |
| la pagina «Cosa ricordo di te» | `FluidMemoryPage(book.memories, FluidMemoryActions(...), onBack)` |
| dimenticare uno, tutti, quelli di un'app | `book.forget(id)`, `book.forgetAll()`, `book.forgetFromApp(pacchetto)`; per non riceverne altri `book.setAppBlocked` |
| proporre un ricordo a PampAI da ClasseViva | `AiMemoryProtocol.proposeRequest(AiMemoryProposal("è in 4F", "scuola.classe"))` sul ponte di F9.1, poi `AiMemoryProposalResult.fromJson`; con `pending` la risposta va con `AiMemoryProtocol.answerRequest(noticeId, accept)` |
| ricevere le proposte in PampAI | `AiMemoryInbox(book).handle(metodo, json, AiMemoryCaller(pacchetto, nome))` nel provider dell'assistenza, dopo il cancello |
| portare la memoria di prima (Room v7) | le righe del `MemoryDao` come `AiMemoryLegacyRow` → `book.importLegacy(righe)`; senza Room `AiMemoryLegacyImport.readPampaiRoom(file)` |
| il significato («cosa cucino stasera?» trova «è vegetariana») | `AiMemoryConfig(embedder = …)`: un `FluidTextEmbedder` (`engine-search-semantic`, o prestato da un'altra app) |
| provare sul computer | `AiMemoryBook.open(AiMemoryStore.inMemory(), config, clock)` e un `ChatProvider` finto |

## Perché quella di prima non funzionava

La diagnosi completa, coi file e le righe di PampAI 1.4.0, è nel rapporto di F9.3 (`diagnosi-memoria.md`). In breve:

1. **Il ricordo dipendeva dal modello**: l'unica strada era il tool `ricorda`, che il modello della chat chiama o no.
2. **E spesso il tool non c'era**: `ricorda` sta nel gruppo «aria», che il pre-router carica solo se la domanda dice
   «ricorda», «memoria»… Su «sono in 4F, che compiti ho?» il pre-router sceglie ClasseViva e il tool non è nell'elenco.
3. **Nessuno lo vedeva**: il ricordo finiva in una riga della traccia («Me lo sono segnato»), senza card e senza Annulla.
4. **Il blocco fisso**: i fissati e i più recenti, fino a 40 righe e 1 500 caratteri (~430 gettoni), in **ogni** domanda;
   oltre la quarantesima riga un ricordo non entrava più, nemmeno quando c'entrava.
5. **Niente contraddizioni**: «è in 4F» e «è in 5F» restavano tutti e due; si riconosceva solo lo stesso testo.
6. **In chiaro** in Room (nessuna cifratura oltre a quella del telefono), senza scadenze e senza fonte oltre alla
   conversazione; nessuna strada per i ricordi delle altre app.

## Il ricordo

`AiMemory` è un valore intero: `text` (al più 300 caratteri, come oggi), `key` (l'argomento: `scuola.classe`), `topics`
(le parole per cui serve), `source` (`AiMemorySource`: chat, app, scritto a mano, memoria di prima; `app` e `appLabel`
per quelli nati in un'app), `createdAtMillis`, `updatedAtMillis` (cambia solo il fatto), `observedAtMillis` (quando è
stato detto), `expiresAtMillis`, `pinned`, `always`, `previousText` e `previousSinceMillis`, `edited`.

**Le contraddizioni.** Due ricordi con la stessa chiave non stanno insieme: vince quello **detto** più di recente
(`observedAtMillis`), anche se arriva dopo. Il nuovo prende il posto del vecchio e tiene `previousText` («prima: è in
4F»): l'avviso è `KindUpdated` e lo dice. Un fatto vecchio che arriva tardi (ClasseViva che manda la classe di
settembre) non vince: l'avviso è `KindKept` («Tengo il più recente»). Senza chiave si riconosce solo lo stesso testo
(maiuscole, accenti e punteggiatura a parte), e resta uno.

## Niente si ricorda senza che l'utente lo veda

**Di serie la memoria chiede** (risposta 2 di Alessio, F9.3b): un fatto che l'utente non ha chiesto lui (l'estrattore, il
modello con lo strumento, un'app, la ricerca) diventa una **domanda** e non un ricordo: l'esito è `pending`, l'avviso è
`KindAsk` («Vuoi che lo ricordi?», con sotto «al posto di: …» se ne sostituirebbe uno), la domanda sta in `book.pending`
e nel file cifrato (resta dopo un riavvio, al più 30, per 14 giorni). `book.answer(id, true)` lo salva e l'avviso, con lo
stesso id, diventa «Ho ricordato» con Annulla; `book.answer(id, false)` no («Va bene, non lo ricordo»), e con
`exclude = true` la memoria non chiede più di quell'argomento. **Entra subito** ciò che l'utente ha chiesto lui: un
«ricordati che…» nel messaggio, `confirmed = true`, uno scritto a mano, la memoria di prima. **«Ricorda da solo»**
(`book.setAutoSave(true)`, come in ChatGPT) salta la domanda: tutto entra subito e l'avviso è «Ho ricordato…» con Annulla.
L'interruttore è **uno solo per tutto** (risposta 4 alle domande di F9.3b): la chat, le app, lo strumento e **i fatti
imparati dalla ricerca del profilo** (risposta 2, C): spento, la ricerca chiede uno per uno («Da confermare»); acceso, i
fatti entrano da soli e la pagina li segna «Imparato dai dati di …».

Ogni ricordo nuovo o cambiato produce un `AiMemoryNotice` in `book.notices`: `KindRemembered`, `KindUpdated`,
`KindKept` (e `KindAsk` per le domande). **Annulla** (`book.undo(id)`) toglie un ricordo nuovo o rimette quello di prima, intero; vale finché il fatto
non cambia di nuovo (allora l'avviso è `stale` e la card non offre più Annulla). Gli annulla restano anche dopo un
riavvio (gli ultimi 50, nel file cifrato). Fissare e cambiare la scadenza non toccano il fatto: Annulla resta.

**La card** (`FluidMemoryNoticeCard`): per una domanda «Vuoi che lo ricordi?» (da un'app «ClasseViva vuole che lo
ricordi»), il fatto e sotto Ricorda e No (`onAnswer`); poi «Ho ricordato» (o «Ho aggiornato · da ClasseViva»), il fatto,
sotto «prima: …» se ne ha sostituito uno, e il tasto Annulla da 48 dp. È vetro di contenuto dell'engine (`FluidCard(glass = true)`, campiona
il fondale una volta: da ferma non ridisegna). Il testo arriva sfocato (`FluidArrivingText`, R7: l'app lo cambia da sola);
dopo Annulla la parola in testa diventa «Non lo ricordo più». **TalkBack**: un nodo solo che si annuncia (area viva
gentile) con l'azione «Annulla», o «Ricorda» e «No» per la domanda. **Riduci movimento**: niente sfocatura e niente altezza animata, il cambio è di colpo.

## Quando si ricorda (e quando no)

`AiMemoryPolicy` tiene le regole, in un posto:

| il turno | l'estrattore | «ricordati che…» |
|---|---|---|
| chat temporanea (`temporary`) | non parte | rifiutato |
| telefono bloccato (`locked`) | non parte | rifiutato |
| «senza usare quello che sai di me» (`storedKnowledgeAllowed = false`) | non parte | — |
| dati di provenienza privata (`privateProvenance`) | non parte | chiede conferma |
| testo non dell'utente (`untrustedInput`: condiviso, incollato, dallo schermo) | non parte | chiede conferma |
| ha letto testo di altri (`readExternal`: web, notifiche, schermo) | parte, sulle sole parole dell'utente | chiede conferma |
| normale | parte se il filtro locale vede qualcosa di sé | sì |

**Mai un segreto** (`looksSecret`): password, PIN, codici, IBAN, carte, codice fiscale, chiavi dei fornitori non diventano
ricordi, nemmeno se chiesti; quelli della memoria di prima passano (li aveva chiesti l'utente) ma non entrano mai nel
prompt e la pagina li segnala.

## L'estrattore dopo i turni

`AiMemoryExtractor(provider, model, config, usageSink, zone)`: un modello **piccolo** (per PampAI quello del router,
`ModelTier.ROUTER`, su Groq `llama-3.1-8b-instant`) legge **solo il messaggio dell'utente** (e l'inizio della risposta di
prima, come contesto) e risponde con uno schema JSON rigido: `{"fatti":[{"testo","chiave","argomenti","scade","sempre"}]}`.

- **Il filtro locale** (`AiMemorySignals`) decide prima se chiamarlo: parole in prima persona, «ricordati», impegni con
  una data. Sul corpus di 80 messaggi delle prove chiama per **22** (tutti i 20 con un fatto, più «ricordami alle 18…» e
  «che ore sono a Tokyo?»): il 72% dei turni costa zero.
- **Il costo**: ~370 gettoni di istruzioni più il messaggio e i ricordi noti, cioè **≈ 440 gettoni** di richiesta e al
  più 200 di risposta. Con Groq `llama-3.1-8b-instant` (0,05 $ e 0,08 $ al milione, listino del 2025, fiducia media) sono
  circa 3 centesimi di dollaro ogni mille chiamate. Va nei consumi con l'`AiUsageSink` (gettoni, mai il testo), **mai**
  nel registro della diagnostica.
- **Il tetto**: al più 2 fatti a turno, al più 12 ricordi automatici al giorno.
- **Tre controlli** su ogni fatto: non sembra un segreto; viene dalle parole dell'utente (almeno una parola del fatto è
  nel messaggio: un fatto preso dalla risposta di prima, o inventato, cade); non è già scaduto.
- **Le chiavi**: il modello vede i ricordi noti pertinenti e le chiavi già usate, e le riusa: così «da lunedì sono in
  5F» aggiorna «è in 4F».

L'alternativa, chiedere i fatti allo stesso giro della chat, costa zero chiamate ma gettoni del modello grande, e vuole
un cambio dell'orchestratore: è una domanda del rapporto di F9.3.

**Le domande arrivate da un'altra app** (la chat «Dalle app» di F9.1, `AiMemoryTurn.app`): l'estrattore parte solo per le
app della tabella (`AiMemoryConfig.apps`); per le altre il dopo-turno si ferma con `SkipApp` e costa zero (risposta 3).

## AICore: cosa fa e cosa no

PampAI usa il modello del telefono **dal servizio di sistema AICore** (F9.5, risposta 5 di Alessio: i modelli che il
sistema offre, niente da scaricare). La memoria lo prende come `AiMemoryOnDeviceModel` (una funzione: istruzioni e testo
dentro, testo fuori), così l'engine non dipende dalla libreria: il legame lo fa F9.5.

- **Vettori: no.** AICore dà un modello che genera testo (Gemini Nano), non i vettori del significato: la pertinenza «per
  significato» resta del `FluidTextEmbedder` (`engine-search-semantic`), facoltativo.
- **La domanda allargata: sì** (`AiMemoryConfig(onDevice = …)`). Prima della ricerca il modello scrive 3–6 parole
  dell'argomento («dove prendo una pizza?» → cena, ristorante, cibo), che cercano col peso 0,7 di una parola scritta;
  al più 800 ms (`onDeviceTimeoutMillis`), poi la ricerca va avanti senza. Così «è vegetariana» entra anche senza vettori.
- **L'estrattore sul telefono: sì** (`AiMemoryExtractor.onDevice(modello)`): le stesse istruzioni, gli stessi controlli,
  nessuna chiamata a un fornitore e nessun consumo da contare. La scelta di Alessio (risposta 1 alle domande di F9.3b):
  **AICore quando c'è, il modello piccolo del fornitore quando non c'è**; quale dei due lo decide l'app (PampAI con F9.5).
- **Senza AICore** (un telefono che non l'ha): un modello da scaricare col consenso o spento, come dice F9.5; la memoria
  intanto funziona con le parole (e col modello piccolo del fornitore per l'estrattore).

## La pertinenza e il tetto nel prompt

`book.prompt(domanda)` mette nel prompt, in quest'ordine e finché c'è posto: i fissati, quelli «per ogni domanda»
(`always`: il nome, come vuole le risposte; al più 4), poi i pertinenti. Il tetto di serie (`AiMemoryPromptBudget`) è
**250 gettoni** e 10 ricordi; una domanda che non c'entra con niente paga **zero**. La riga è
`- è in 5F al liceo Galilei (2026-11-26)`, con l'app e la scadenza quando ci sono.

**Dopo l'attesa, lo stato di adesso** (R3): AICore che allarga la domanda e l'indice fanno aspettare; `prompt` e
`relevant` rileggono i ricordi e l'ora **dopo** l'attesa, così un ricordo tolto, sfissato o scaduto nel frattempo non
entra nel prompt né nel contesto dell'estrattore.

**La ricerca** usa l'indice di `engine-search` (l'italiano, gli errori di battitura, i plurali, BM25F) **solo in memoria**:
si rifà a ogni apertura dai ricordi, così sul disco non c'è niente oltre al file cifrato. Una domanda all'assistente nomina
un ricordo con una parola sola, quindi ogni parola è una ricerca a sé e i punteggi si sommano (l'indice di F8.22 vuole
tutte le parole); passano i ricordi a un quarto del migliore o più. Il testo pesa 3, gli argomenti 2, la chiave 1. Con un
`FluidTextEmbedder` anche il significato cerca, e i due elenchi si uniscono per rango come in F8.22; i vettori si salvano
nel file cifrato e al riavvio non si ricalcolano.

## La cassaforte

`AiMemoryStore.vault(section)`: un file solo (`ai-memory.v1`) nella sezione `ai-memory` (`VaultAccess.Device()`, si apre da
sola, in `noBackupFilesDir`), cifrato a pezzi con AES-256-GCM dalla chiave della sezione, chiusa nel Keystore. Ogni
scrittura va sul disco **prima** di cambiare ciò che si vede; un file che non si apre (un altro telefono, il Keystore
azzerato) fa lanciare l'eccezione e **non** azzera la memoria da solo.

## I ricordi delle altre app

Passano da PampAI col contratto inverso di F9.1 (`ContentResolver.call` sul provider dell'assistenza, la firma, il
pacchetto detto dal sistema). I metodi sono di `AiMemoryProtocol`: `memory_propose`, `memory_answer`, `memory_undo`,
`memory_forget_mine`. `AiMemoryInbox` decide dopo il cancello di F9.1.

**L'aggancio** (R3-S1b, D5 di Alessio): la sottoclasse di `AiAssistHostProvider` di PampAI dà la memoria con
`override suspend fun memory(method, json, caller) = inbox.handle(method, json, AiMemoryCaller(caller.packageName,
caller.label))`. Il provider passa i metodi dei ricordi dopo il cancello (la firma, il pacchetto dal sistema), e **il
«no» di F9.1 vale anche qui**: un'app rifiutata o bloccata in «App che usano PampAI» riceve `not_allowed` e la memoria
non la sente. Il sì di F9.1 «da chiedere» non ferma i ricordi. Senza l'aggancio i metodi rispondono «metodo
sconosciuto». La galleria chiama ancora l'inbox nello stesso processo.

L'inbox controlla:

- **la tabella** (`AiMemoryAppPolicy.DEFAULT`, risposta 3 di Alessio): Pampa Mail le chiavi `scrittura.*` e `mail.*`,
  MyCloud Gallery `foto.*`, `luoghi.*` e `persone.*`, ClasseViva `scuola.*`; le varianti `.debug` solo con un PampAI di
  sviluppo; una proposta senza chiave no. **Le altre app no**, Pampa Notes compresa: un ricordo coi loro dati nasce solo
  se lo chiede il modello della chat di PampAI con lo strumento della memoria (`AiMemorySource.tool(conversazione, app)`,
  che di serie chiede anche lui);
- **l'utente**: un'app bloccata (`setAppBlocked`) riceve `not_allowed`;
- **il tetto**: 5 ricordi nuovi al giorno per app (un aggiornamento non conta);
- **«per ogni domanda»** non lo decide un'app.

L'esito torna all'app (`AiMemoryProposalResult`): di serie `pending`, e l'app mostra la domanda (`result.notice(source,
ora)` è un `KindAsk`) e manda la risposta con `memory_answer`; col salvataggio automatico `remembered`, e l'app mostra la
sua card con Annulla. PampAI ha il suo avviso (la chat «Dalle app» e la pagina, dove la domanda resta «Da confermare»).
Un'app risponde, annulla e dimentica solo quello che ha proposto lei.

**L'app riceve solo il suo** (R3): l'esito, l'id dell'avviso e il fatto che ha scritto lei (`text`). Il ricordo con la
stessa chiave che resta (`kept`) o che viene sostituito (`pending`, `updated`) è dell'utente: `previousText` non arriva
più dal ponte e per `kept` l'app non mostra una card (`notice` torna null, come per `duplicate`). Il «al posto di…» e il
conflitto si vedono nella pagina di PampAI.

**Lo stesso testo di un ricordo non suo** (R3-S1b, D2 di Alessio): se un'app propone il testo di un ricordo che non ha
proposto lei (scritto dall'utente, detto in chat, di un'altra app), quel ricordo non si tocca, né la scadenza né la
chiave né le parole, e l'esito è `duplicate`. Un ricordo proposto dall'app stessa si aggiorna come prima.

## Una memoria sola: il profilo dentro

Il profilo della ricerca di PampAI 1.4.0 (`aria_profile` con `aria_dependencies`, `aria_evidence`, `aria_grants`,
`aria_exclusions`) era una seconda memoria, con le sue regole. In F9.3b ci entra (risposta 6):

- **i campi strutturati**: un fatto del profilo è un ricordo con la chiave del campo (`musica.artista_preferito`), il
  valore (`value`) e il testo «musica artista preferito: Coldplay» (`importProfile(…, label)` per un'etichetta migliore);
- **l'origine** (`AiMemory.origin`): `statement` (detto dall'utente), `observation` (visto nei dati di un'app),
  `inference` (dedotto da un modello, con `limitations` e `derivedBy`). Nelle contraddizioni una cosa detta batte una
  vista o dedotta; fra pari vince la più recente. Nel prompt un fatto dedotto lo dice: «(da Fluidify, 2026-11-24; dedotto,
  non detto: solo dagli ultimi 30 giorni)»;
- **le prove** (`AiMemoryEvidence`: la prova, l'autorizzazione, l'app, il dato, il tipo di dato, quando): la fonte vera
  di ciò che la ricerca ha imparato (`AiMemorySource.research`);
- **la correzione**: `book.correct` fa del fatto una cosa detta (`statement`);
- **l'esclusione** (`AiMemoryExclusion`: un argomento o un tipo di dato, un'app, un'autorizzazione, un dato): ciò che la
  memoria non deve più imparare; toglie anche ciò che aveva già imparato da lì (non le cose dette dall'utente);
- **la revoca di una fonte**: `book.forgetFromGrant(grant, record)`. Con i ricordi se ne vanno, nella stessa scrittura,
  le domande ancora aperte che vengono da lì e le versioni di prima che Annulla rimetterebbe (R3). Lo stesso vale per
  `exclude` e `forgetFromApp`; `setAppBlocked` chiude le domande dell'app. «Ricorda» e Annulla ricontrollano le regole
  di adesso: un'app bloccata, un argomento escluso o un fatto già scaduto non entrano (`not_allowed`, `excluded`,
  `refused`).

Il lettore di prova `AiMemoryLegacyImport.readPampaiProfile(file)` legge le cinque tabelle in sola lettura: solo le voci
`ACTIVE` passano (le altre restano nella copia di Room), solo le prove valide, l'app dall'autorizzazione. Un'esclusione
**da chiarire** (`unmapped`, che oggi sospende tutto l'apprendimento) non si traduce: è contata e il passaggio aspetta.

## La memoria di prima

`book.importLegacy(righe)` porta la tabella `memories` di Room v7 **senza perdere niente**: il testo intero, la data, la
conversazione, la fissatura (la fonte diventa `KindImported`). Si può rifare: le righe già passate non si duplicano
(il segno `room7:<id>` sta nel file cifrato). I tetti non valgono qui. Le righe di Room non si cancellano: PampAI le
toglie con una migrazione dopo, quando il passaggio è andato bene.

## Trappole

- **`AiMemoryBook.open` va fuori dal thread principale**: legge e decifra il file e rifà l'indice.
- **Un deposito che non si legge lancia**: non chiamare `forgetAll` per «aggiustarlo» senza chiedere all'utente.
- **L'estrattore non va chiamato coi risultati dei tool**: `AiMemoryTurn.userText` è solo ciò che l'utente ha scritto.
- **Le chiavi decidono le contraddizioni**: un'app che propone deve sempre dire la chiave (senza, `not_allowed`).
- **I ricordi della chat «Dalle app»** hanno `source.app` dell'app: `forgetFromApp` li toglie, `forgetFromApp(…,
  onlyProposed = true)` (la revoca chiesta dall'app) no.

## Prove

- `engine-ai/src/test/.../ai/memory/AiMemoryProposalTest` (JVM): i limiti, la pulizia, il JSON del ponte, gli esiti.
- `engine-ai-memory/src/test` (Robolectric, con SQLite FTS4 vero): `AiMemoryExtractorTest` (i fatti giusti; niente da
  chat temporanee, telefono bloccato, turni privati o testo non suo; niente segreti né fatti inventati; il tetto; il costo
  sul corpus), `AiMemoryBookTest` (avvisi e Annulla, contraddizioni, doppioni, «ricordati» con conferma, i tetti, il
  dopo-turno, le scadenze, il riavvio), `AiMemoryRelevanceTest` (la pertinenza, il tetto dei gettoni contro il blocco di
  oggi, fissati e «per ogni domanda», il significato), `AiMemoryVaultAndRevocationTest` (niente in chiaro sul disco,
  l'altra chiave, la revoca), `AiMemoryLegacyImportTest` (un database finto con lo schema v7), `AiMemoryInboxTest` (il
  ponte), `AiMemoryAskAndProfileTest` (F9.3b: la domanda di serie, Ricorda e No, l'esclusione, «ricordati» che entra
  subito, l'interruttore che resta, le app della tabella e lo strumento, il profilo da un database v7 finto con prove,
  esclusioni e revoca di una fonte, AICore finto per la domanda allargata e l'estrattore sul telefono), `AiMemoryR3Test`
  (R3: il ponte non porta il ricordo privato con la stessa chiave, la revoca chiude le domande e l'Annulla, «Ricorda»
  ricontrolla blocco, esclusione e scadenza, il prompt dopo l'attesa di AICore; R3-S1b: lo stesso testo di un ricordo
  dell'utente non gli cambia scadenza né chiave).
- `engine-ai-ui/src/test/.../aiui/memory/FluidMemoryTextsTest` (JVM): le frasi della card, della domanda e della pagina.
- `sample/src/testDebug/.../novita/F9p3MemoryBridgeTest` (Robolectric, R3-S1b): i ricordi dal provider vero, col
  cancello, il «no» di F9.1, il blocco della memoria e un'app fuori tabella.
- `sample/src/testDebug/.../novita/F9p3MemoryUiTest` (Compose): la conversazione con la domanda, Ricorda e No, «Ricorda da
  solo», la card e Annulla, TalkBack, «Riduci movimento», la chat temporanea, ClasseViva col ponte, Pampa Notes e lo
  strumento, la pagina con «Da confermare», il fatto dedotto e le esclusioni.

## Dove guardare

- `engine-ai/src/main/kotlin/dev/antigravity/fluidengine/ai/memory/` — `AiMemory.kt`, `AiMemoryProposal.kt`.
- `engine-ai-memory/src/main/kotlin/dev/antigravity/fluidengine/ai/memory/` — `AiMemoryBook.kt` (il motore),
  `AiMemoryPolicy.kt` (le regole e il filtro), `AiMemoryExtractor.kt`, `AiMemoryIndex.kt` (la pertinenza),
  `AiMemoryPrompt.kt`, `AiMemoryStore.kt` (la cassaforte e il formato del file), `AiMemoryInbox.kt`,
  `AiMemoryLegacyImport.kt` (anche il profilo), `AiMemoryOnDevice.kt` (AICore).
- `engine-ai-ui/src/main/kotlin/dev/antigravity/fluidengine/aiui/memory/` — `FluidMemoryNoticeCard.kt`, `FluidMemoryPage.kt`,
  `FluidMemoryStrings.kt`.
- `sample/.../novita/F9p3Scenes.kt` — le tre scene della galleria.

Installazione: `engine-install.ps1 -AppRoot . -Modules engine-ai-memory` (porta `engine-search`, `engine-vault`,
`engine-ai`, `engine-ai-bridge`, `engine-ui`). Nel `build.gradle` di PampAI: `implementation project(':engine-ai-memory')`.
Nessun permesso, nessuna libreria nuova.
