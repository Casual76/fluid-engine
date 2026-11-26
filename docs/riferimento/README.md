<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/SKILL.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->


# Fluid Engine

Le fondamenta condivise delle app Android di Antigravity, estratte da ClasseViva Expressive. Un repo a sé,
agganciato a un tag dentro ogni app che lo usa. **Versione documentata: 3.1.2** (schede, catalogo API e changelog allineati).

Questa skill è la documentazione dell'engine **e** le regole per lavorarci senza romperlo. Non è un riassunto:
dietro c'è una scheda per ogni famiglia di componenti e per ogni modulo, e un catalogo di tutte le API pubbliche
generato dal codice. Il tuo lavoro è **trovare la pagina giusta e leggerla**, non ricordare a memoria.

**Prima di modificare qualsiasi cosa leggi `regole.md`**: sono poche e sono tutte deliberate.

## Come si naviga

```
SKILL.md                      <- sei qui: situazione, mappa, regole, indice
references/
  regole.md                   le regole del design system, col perché
  mappa.md                    moduli, dipendenze, cosa installare, dove sta il codice
  ricette.md                  i compiti frequenti, passo per passo
  risoluzione-problemi.md     sintomo -> causa -> rimedio
  strumenti-e-qa.md           script, build, collaudo sul dispositivo
  storia.md  glossario.md     come siamo arrivati qui; le parole dell'engine
  integrazione.md  aggiornamenti.md  config-remota.md  widget.md  limiti.md  ai.md
  design-system.md            inventario storico di engine-ui (le schede sotto lo sostituiscono, resta per il contesto)
  componenti/                 UNA SCHEDA PER FAMIGLIA di engine-ui (scopo, uso, comportamento, trappole)
  moduli/                     UNA SCHEDA PER MODULO senza UI o opt-in (foundation ... ai-ui, wear, richtext, viewer, viewer-famiglie)
  api/                        catalogo GENERATO: ogni dichiarazione pubblica con la firma
scripts/                      gen_api_catalog.py, check_docs.py, sync_docs.py
```

Regola pratica: **la scheda spiega, il catalogo elenca, il sorgente decide.** Cerca il nome nel catalogo
(`grep -rn "FluidSlider" references/api/`), leggi la scheda della famiglia, e quando serve un numero o un
comportamento non scritto apri il file Kotlin indicato nella scheda («Dove guardare»): il KDoc spiega il perché.

## Riconoscere la situazione

| segnale | dove sei |
|---|---|
| esiste `engine/ENGINE_VERSION` e `engine.properties` alla radice | in un'**app** che usa l'engine |
| esiste `ENGINE_VERSION` alla radice e le cartelle `engine-*` | **dentro l'engine** |
| `engine.properties` con `engine.mode=port` ma nessuna cartella dell'engine | in un'app con una **copia** del look: `integrazione.md`, «Il porto» |
| nessuno dei due, ma serve partire da queste fondamenta | `integrazione.md`, poi `ricette.md` |

La distinzione conta: **il codice dentro `engine/` non è codice dell'app.** Modificarlo lì e non committarlo nel
repo dell'engine crea una variante silenziosa che sparisce al primo aggiornamento.

## La mappa

Non entrano tutti in ogni app: `engine-install.ps1 -Modules …` include solo quello che serve e chiude da sé le
dipendenze (un'app senza il plugin Compose non riesce nemmeno a *configurare* `engine-ui`, e un modulo che non
configura ferma tutto il build). Tutto sotto `dev.antigravity.fluidengine.*`. Dettaglio, dipendenze e toolchain:
`mappa.md`.

| modulo | cosa porta | dove si legge |
|---|---|---|
| `engine-foundation` | modelli, versioni, manifest remoto, flag, impostazioni — niente Compose | `moduli/foundation.md` |
| `engine-ui` | il design system Fluid: tema, componenti, vetro, Fluid-physics, pagine, aptica, tutorial | `componenti/` |
| `engine-storage` | DataStore: impostazioni, cache del manifest | `moduli/storage.md` |
| `engine-net` | `EngineHttp`: leggi un documento, scarica un file; l'osservatore della rete che non lampeggia e le condizioni del telefono (rete a consumo, risparmio dati ed energetico, calore) | `moduli/net.md` |
| `engine-config` | `EngineRemoteConfig`: flag, versione minima, avvisi, kill switch | `moduli/config.md` |
| `engine-update` | aggiornamento in-app via PackageInstaller | `moduli/update.md` |
| `engine-widget` | palette e componenti Glance con lo stesso aspetto | `moduli/widget.md` |
| `engine-wear` | il design system su Wear OS (**opt-in**: solo nel modulo dell'orologio) | `moduli/wear.md` |
| `engine-ai` | l'assistente senza dominio: provider BYOK, chiavi, SSE, failover, router e orchestratore, voce | `moduli/ai-provider.md`, `ai-orchestrator.md`, `ai-chiavi-rete-voce.md` |
| `engine-ai-ui` | l'interfaccia dell'assistente: overlay, chat, impostazioni, halo (**opt-in**) | `moduli/ai-ui*.md` |
| `engine-ai-bridge` | tool federati fra app (ContentProvider che verifica la firma di chi chiama) | `moduli/ai-bridge.md` |
| `engine-vault` | la cassaforte: valori, file e DataStore cifrati col Keystore, sezioni all'impronta che si richiudono da sole, i byte sigillati dell'account (**opt-in**, dalla 4.0) | `moduli/vault.md` |
| `engine-security` | il blocco dell'app con l'impronta, la schermata di vetro, il PIN dell'app, il contenuto protetto, l'anteprima oscurata nelle recenti (**opt-in**, dalla 4.0) | `moduli/security.md` |
| `engine-qr` | il QR di «Mostra come QR» nel foglio di condivisione, con ZXing core (**opt-in**, dalla 4.0) | `moduli/qr.md` |
| `engine-share-in` | gli arrivi da altre app: le forme di Android lette in un modo solo, la copia subito con tetto, il foglio sopra l'app di partenza (**opt-in**, dalla 4.0) | `moduli/share-in.md` |
| `engine-richtext` | l'editor di testo con formattazione (elenchi e citazioni annidati, link, barra sopra la tastiera), HTML ristretto, Markdown, testo (**opt-in**, dalla 3.1.0) | `moduli/richtext.md` |
| `engine-viewer` | i decodificatori dei visori di file: PDF, testo con charset e colori di sintassi, GIF e WebP animate, il `Player` di Media3, il risolutore dal MIME al preset e il registro delle famiglie (**opt-in**, dalla 4.0) | `moduli/viewer.md` |
| `engine-sync` | la sincronizzazione: registro locale, invio una volta sola, unione per campo e dei testi lunghi, pietre tombali, verso il relay cifrato dell'Account Pampa; WorkManager (**opt-in**, dalla 4.0) | `moduli/sync.md` |
| `engine-revisions` | il foglio di vetro della scelta tra versioni in conflitto (**opt-in**, dalla 4.0) | `moduli/revisions.md` |
| `engine-notify` | le notifiche vere di Android (canali con parole comuni, notifiche raccolte, risposte rapide, riepilogo programmato), le attività fuori dall'app (Live Update di Android 16, notifica con l'avanzamento prima), i promemoria che sopravvivono a riavvio e cambio d'ora; le pagine e il ponte con la pillola sono in `engine-ui` (**opt-in**, dalla 4.0) | `moduli/notify.md` |
| `engine-account` | l'Account Pampa visto da un'app: chi sei, token per il suo servizio, chiavi dell'app, backup, aspetto e luoghi comuni, sempre attraverso Pampa Store e **mai la rete** (**opt-in**, dalla 4.0) | `moduli/account.md` |
| `engine-account-host` | il motore dell'account dentro Pampa Store: provider firmato, sessione, custodia delle chiavi (epoche, porte, passaggio col QR, due classi), codice di recupero, backup dello Store (**opt-in**, solo lo Store) | `moduli/account-host.md` |
| `engine-account-ui` | la riga «Account Pampa» e l'avatar, nei quattro stati, e i componenti delle persone (`FluidPeopleRow` con «I tuoi dispositivi», `FluidSharedWithYou`, `FluidAttribution`, `FluidQuickReply`), senza grafica finale (**opt-in**) | `moduli/account-ui.md` |
| `engine-people` | le persone dell'account: la crittografia di Codex (PMX1, CDXB, CDXM, schede, quattro parole con l'impegno), la persona in radice e chiavi di lavoro, la casella con la coda in uscita, gli spazi condivisi sopra `engine-sync`, la vicinanza con la runa (**opt-in**, Pampa Store e Pampa Mail) | `moduli/persone.md` |
| `engine-push-fcm` | il campanello di Pampa Store con Firebase Messaging, spento (FCM e Analytics) fino all'accesso (**opt-in**, solo lo Store) | `moduli/push-fcm.md` |
| `engine-media` | foto e allegati: il selettore di foto e video di vetro (scelta in ordine, album, ricerca anche per significato, anteprima dalla miniatura, «solo alcune foto»), il contratto delle sorgenti fra app (MyCloud Gallery in F12), il Photo Picker di Android dove basta, il pannello degli allegati del «+» (**opt-in**, dalla 4.0) | `moduli/media.md` |
| `engine-audio` | la cattura del registratore: il microfono con le interruzioni, pausa e ripresa in un file solo (AAC o WAV), l'onda dal vivo, il suono che taglia, il taglio dei silenzi, l'onda di un file (**opt-in**, dalla 4.0) | `moduli/audio.md` |
| `engine-playback` | la riproduzione di sistema con Media3 dell'app: notifica e schermata di blocco con ±15 s, cuffie e chiamate, velocità, salta i silenzi, timer, coda ricordata, lo stato per Compose (**opt-in**, dalla 4.0) | `moduli/playback.md` |
| `engine-transcript` | l'ascolto dal paragrafo: i tempi per paragrafo, frase e parola, il salto alla parola toccata, la pagina che segue la voce senza saltare (**opt-in**, dalla 4.0) | `moduli/transcript.md` |
| `engine-ink` | il disegno e le annotazioni come Markup: il foglio col tratto a bassa latenza (androidx.ink), la penna dello stilo e il dito che scorre, la tavolozza di vetro (penna, evidenziatore, matita, gomma, lazo, righello), le forme tenendo fermo, annulla e ripristina, la foto e il PDF annotati in un file nuovo (**opt-in**, dalla 4.0) | `moduli/ink.md` |
| `engine-transfer` | il centro trasferimenti: coda persistita di scaricamenti e caricamenti lunghi, pausa e ripresa con `Range`, solo Wi-Fi e in carica, WorkManager col servizio `dataSync` (**opt-in**, dalla 4.0) | `moduli/transfer.md` |
| `engine-files` | lo sfoglia-file di vetro come File di iOS: le schede Recenti e Sfoglia (le posizioni: la cartella dell'app, le cartelle concesse e le app con un `DocumentsProvider`), griglia con le miniature vere o elenco, ordine, ricerca, selezione e scelta, apertura nei visori dalla miniatura (**opt-in**, dalla 4.0) | `moduli/files.md` |
| `engine-files` | lo sfoglia-file di vetro come File di iOS: Sfoglia con le posizioni (la cartella dell'app, le cartelle concesse e le app con un `DocumentsProvider`) e Recenti, griglia con le miniature vere o elenco, ordine, ricerca, selezione e scelta, apertura nei visori dalla miniatura (**opt-in**, dalla 4.0) | `moduli/files.md` |
| `engine-search`, `engine-search-semantic` | la ricerca: l'indice sul telefono come Core Spotlight (SQLite FTS4, l'italiano, gli errori di battitura, gli inizi, la pertinenza, i pezzi evidenziati, la cassaforte per i dati sensibili, l'aggiornamento a pezzi in carica, l'aggancio al campo di ricerca) e «Cerca in Pampa» col ponte firmato; a parte il modello del significato con LiteRT-LM (**opt-in**, dalla 4.0) | `moduli/search.md` |
| `engine-viewer-office`, `-archive`, `-data`, `-graphics`, `-3d`, `-ar`, `-all` | i moduli di famiglia dei visori: Office, archivi, testi ricchi e dati, immagini speciali e font, 3D, AR, e il visore universale (**opt-in**, uno per famiglia, col loro peso) | `moduli/viewer-famiglie.md` |
| `engine-auth`, `engine-auth-google` | le passkey e il gestore delle password con Credential Manager, senza rete (il PRF lo scrive il modulo, gli errori come frasi pronte); «Continua con Google» e le concessioni Google, spente (**opt-in**, dalla 4.0; Google ha il permesso della GPL-3.0 § 7) | `moduli/auth.md` |

## Che componente uso per…

La tabella «Scegliere in fretta» sta in testa a ogni scheda; questo è l'indirizzamento.

| ho bisogno di… | scheda |
|---|---|
| una schermata, titolo grande che si ritira, refresh, schermi larghi, indice laterale, scroll veloce | `componenti/schermata.md` |
| barra delle schede a pillola (con segni sulle zone), rail, capsula di azioni (anche accese/spente), campo di ricerca | `componenti/barre.md` |
| righe, liste raggruppate, card, hero, skeleton, Markdown, visore di immagini, riordino | `componenti/contenuto.md` (indice) → `contenuto-righe-liste.md`, `contenuto-testo-e-media.md` |
| una sezione che si apre col vetro che si separa, la selezione trascinando, una griglia di foto o di file col pizzico | `componenti/contenuto-righe-liste.md`, «Sezioni richiudibili, selezione trascinando, griglia di anteprime» |
| una lista **lunga** che cambia mentre la si legge, righe con gesto di lato, «3 nuove in alto», stato vuoto con azione | `componenti/contenuto-righe-liste.md`, «Liste lunghe, una riga per elemento» |
| l'avatar di una persona, i colori stabili, il badge «3 non lette», la selezione multipla (anche «Seleziona tutto» paginato), un allegato | `componenti/contenuto-testo-e-media.md`, «Identità e selezione», «File» |
| mostrare un HTML ricevuto (mail, newsletter) in una vista chiusa | `componenti/contenuto-web.md` |
| una ciambella, barre o un andamento con legenda | `componenti/grafici.md` |
| scrivere testo formattato (elenchi, link, citazioni, HTML/Markdown) | `moduli/richtext.md` (opt-in) |
| sincronizzare i dati dell'app fra i dispositivi (anche senza rete), uno spazio condiviso, «3 modifiche in attesa», i conflitti | `moduli/sync.md` (opt-in) |
| far scegliere fra due versioni di un elemento cambiate su dispositivi diversi (tieni questa, l'altra, tutte e due) | `moduli/revisions.md` (opt-in) |
| far scegliere foto e video dentro l'app (in ordine, album, ricerca, MyCloud Gallery), la foto del profilo, il «+» degli allegati del composer (la scheda come Messaggi o il pannello al posto della tastiera), offrire le foto della propria app alle altre | `moduli/media.md` (opt-in) |
| disegnare col dito o con la penna (uno schizzo, una firma), scrivere su una foto o firmare un PDF, con la tavolozza di vetro di Markup | `moduli/ink.md` (opt-in) |
| condividere (il foglio di vetro con le app Pampa e le azioni, le opzioni prima dell'invio), fare di una vista un'immagine (un voto nei formati delle storie), un PDF impaginato con testo vero, la stampa; ricevere da un'altra app | `componenti/condivisione.md`; ricevere: `moduli/share-in.md`; il QR: `moduli/qr.md` |
| l'Account Pampa in un'app: la riga nelle impostazioni, un token per il servizio, le chiavi per sincronizzare, il backup | `moduli/account.md`, `moduli/account-ui.md` (opt-in) |
| custodire le chiavi dell'account in Pampa Store (porte, epoche, passaggio, codice di recupero) | `moduli/account-host.md` (opt-in, solo lo Store) |
| condividere con una persona, «Condivisi con te», «Da X», «Rispondi», «I tuoi dispositivi» in un'app | `moduli/account.md` (`PampaPeople`), `moduli/account-ui.md` (opt-in) |
| le persone nello Store o in Pampa Mail: rubrica, casella, gruppi, spazi, campanello | `moduli/persone.md`, `moduli/push-fcm.md` (opt-in) |
| «Offline · 3 modifiche in attesa» sopra il contenuto, o sapere se c'è internet senza falsi allarmi (Wi-Fi d'albergo, VPN, micro-cadute) | `componenti/elementi-dinamici.md` («La capsula della rete») e `moduli/net.md` |
| non scaricare un file grande con la rete a consumo, il risparmio dati, la batteria scarica o il telefono caldo, con «Fallo comunque» | `moduli/foundation.md` («Le condizioni del telefono») e `moduli/net.md` |
| scaricare o caricare file grandi con pausa e ripresa, anche con l'app chiusa, e la pagina «Trasferimenti» | `moduli/transfer.md` (opt-in) e `componenti/elementi-dinamici.md` |
| il visore di un file a tutto schermo (foto, video, audio, PDF, testo, sconosciuto): rotta, ritorno nella miniatura, barre al tocco, pannello info, strumenti | `componenti/visori.md` |
| aprire un PDF, un file di testo o di codice, una GIF animata o un video nel visore di file (decodificare, non solo mostrare) | `moduli/viewer.md` (opt-in) |
| sfogliare le cartelle e scegliere file (i file dell'app, una cartella del telefono, Drive), con i recenti; un PDF di migliaia di pagine senza scatti; un file che si riapre dove lo si era lasciato | `moduli/files.md` (opt-in); il bilancio e la ripresa: `componenti/visori.md` |
| aprire un DOCX, un foglio, uno ZIP, un Markdown o un JSON, un EPUB, un HEIC, un SVG, un font, un modello 3D; quanto pesa ogni modulo | `moduli/viewer-famiglie.md` (opt-in) |
| cercare nei dati dell'app con un indice (errori di battitura, accenti, plurali, pezzi evidenziati, risultati per tipo), anche per significato; «Cerca in Pampa» fra le app | `moduli/search.md` (opt-in) |
| tasti, segmentato, chip, campo di testo, campo a gettoni (destinatari, etichette), avatar come azione della barra | `componenti/controlli.md` |
| interruttore, cursore, scrubber, cursore a due maniglie, ruota di iOS (anche durata e ora), ghiera a righello (i controlli a gesto) | `componenti/controlli-gesto.md` |
| l'onda di un audio da scorrere col dito (il messaggio audio, Memo Vocali col pizzico), il registratore col tasto rosso | `componenti/controlli-gesto.md`, «L'audio»; la cattura `moduli/audio.md` (opt-in) |
| un audio che continua fuori dall'app (notifica, schermata di blocco, cuffie, timer, salta i silenzi) | `moduli/playback.md` (opt-in) |
| una trascrizione che si tocca per ascoltare, per parola e per paragrafo, che segue la voce | `moduli/transcript.md` (opt-in) |
| un numero con l'unità (importi, misure), il codice a caselle, la password con le regole, un valore riservato, la copia che si cancella, il tasto incolla per i dati concordati | `componenti/campi-speciali.md` |
| scegliere un colore (il pozzetto, il foglio con griglia, spettro e cursori, il contagocce, la fila corta di campioni), o tingere la pagina coi colori di una copertina come Apple Music | `componenti/colore.md` |
| un calendario nella pagina coi segni sotto i giorni, giorni sparsi o un intervallo, la striscia della settimana; la data o la durata in una riga che apre un popover; il pannello «Ripeti» e la regola di una ricorrenza | `componenti/date.md` |
| i coriandoli di un successo; un'icona che rimbalza, dondola, gira, respira o pulsa, le tacche del Wi-Fi in fila, play che diventa pausa, la spunta che si disegna (gli effetti di SF Symbols) | `componenti/festa-e-simboli.md` |
| pop-up, foglio, avviso, conferma, portale, pannello laterale (`FluidDrawer`), scelta di data e ora | `componenti/menu-modali.md` |
| menu che si trasforma, azioni della barra, menu contestuale | `componenti/menu-liquidi.md` |
| notifiche contestuali (la card che si tocca; azioni in linea, sostituzione per gruppo, esito) | `componenti/notifiche.md` |
| pressione, gesto, molle, movimento ridotto | `componenti/movimento.md` |
| vibrazioni | `componenti/aptica.md` |
| il materiale di vetro, i ruoli, il costo; i **due materiali** della 4.0 (`Liquid`, il vetro nuovo, e `Classic`, quello di prima: chi sceglie, cosa cambia per un'app che aggiorna, come tenere il vetro di prima) | `componenti/vetro.md`; la libreria backdrop di Kyant0: `vetro-backdrop.md` |
| una forma che si trasforma in un'altra (la card che si espande, il tasto che diventa menu) | `componenti/fluid-physics.md`; il motore dentro: `fluid-physics-motore.md` |
| vetri che cambiano insieme con un `if` (un tasto che diventa due, una barra che cambia azioni, un tasto che compare), il contenuto che si sfoca, gli angoli concentrici: il **piano flottante** della 4.0 (`FluidGlassContainer`) | `componenti/piano-flottante.md` |
| la barra in alto dichiarata (`toolbar { … }` su `FluidScreen`: tasti, gruppi, principale, indietro e Chiudi, spaziatori, trabocco «Altro», titolo e sottotitolo, ricerca) che si trasforma da sola quando la pagina cambia stato: la **toolbar** della 4.0 | `componenti/toolbar.md` |
| più pagine, migrare da `NavHost` | `componenti/pagine.md` |
| ciò che si trasforma da solo (F6B): la selezione con la tab bar che diventa le azioni, un tasto che lavora e dice l'esito, «Annulla» nel tasto, l'avviso che nasce dal tasto, un elemento che diventa pagina, il campo coi preset e «trova», il composer che cresce nel campo, i filtri a pillole, la barra sopra la tastiera, la pillola di attività, il tasto diviso, il titolo selettore | `componenti/elementi-dinamici.md` |
| back col dito, `BackHandler`, l'ordine del back (menu → pannello → selezione → pagina), barra unica e ancorata | `componenti/pagine-back-e-barra.md` |
| colori, tipografia, angoli, tema, accento, la palette d'identità | `componenti/tema.md` |
| sezione aspetto, aggiornamento in-app, parole traducibili, licenze | `componenti/impostazioni.md` |
| suggerimenti al primo uso | `componenti/tutorial.md` |
| il foglio «Benvenuto in…» e «Novità di…» una volta per versione, un permesso chiesto dal tasto che ne ha bisogno (anche le foto «solo alcune»), uno sfondo vivo per un onboarding o un traguardo, la finestra del colore giusto al primo fotogramma | `componenti/prima-volta.md` |
| entrare con una passkey, salvare un codice nel gestore delle password, «Continua con Google» | `moduli/auth.md` (opt-in) |
| un orologio | `moduli/wear.md` |
| cifrare una password, un file, un DataStore; una cartella che si apre con l'impronta e si richiude da sola; il caso «chiave non più valida» | `moduli/vault.md` (opt-in) |
| bloccare l'app con l'impronta al rientro, il PIN dell'app, un album o le note che restano chiusi, l'anteprima nelle recenti | `moduli/security.md` (opt-in) |
| un assistente IA | `ai.md`, `moduli/ai-provider.md`, `ai-orchestrator.md`, `ai-chiavi-rete-voce.md`, e l'interfaccia in `moduli/ai-ui.md` (+ `ai-ui-overlay`, `-chat`, `-impostazioni`, `-stringhe`) |

## Le cinque regole che non si negoziano

Il perché di ciascuna, i numeri e le eccezioni sono in `regole.md`.

1. **Nessun colore, dimensione di testo o raggio scritto a mano** in una schermata. Colori da
   `MaterialTheme.colorScheme`, testo da `MaterialTheme.typography` o `FluidTextStyles`, angoli da `FluidRadius` con
   `ContinuousCornerShape` (mai `RoundedCornerShape`), animazioni da `FluidMotion`.
2. **Le liste sono raggruppate.** `FluidListGroup` + `FluidListRow` + `FluidListDivider`. Il colore della categoria
   sta sulla piastrella dell'icona, mai sullo sfondo della riga.
3. **Una pagina in arrivo non sfuma mai.** Le pagine si muovono con `FluidNavHost` (al posto di `NavHost`): opache,
   da destra, con la pagina sotto che arretra di un terzo; il back è quello di iOS sulla **posizione vera del dito**
   (`touchX`, mai il `progress` del gesto di sistema) e parte dal bordo. Un `fadeIn` fra due schermate, o le
   transizioni di un `NavHost` o di un `FluidRouteMotionHost` (deprecato), vanno contro il vocabolario dell'app.
   Dentro un host la barra è **una sola, ferma**: la pagina la descrive, non se ne disegna una sua.
   **Dalla 3.0 l'engine include codice GPL-3.0 di LibrePods** (pulsanti, pagine, aptica; `LICENSES/LibrePods.md`):
   un'app che lo aggiorna è un'opera derivata, si distribuisce sotto GPL-3.0 con i sorgenti a chi riceve l'APK, e
   la sua pagina «informazioni» chiama `fluidLicensesSection()`. Dillo **prima** di migrare.
4. **`engine-foundation` non conosce Android UI, e nessun modulo dell'engine conosce il dominio dell'app.** Un
   componente che sa cos'è un voto o una materia non è un componente dell'engine.
5. **Il codice non si aggiorna da remoto.** Il manifest cambia comportamento (flag, versione minima, avvisi, kill
   switch) e a quale APK punta l'aggiornamento; il resto richiede una build. Mai `DexClassLoader` o dex scaricati:
   `limiti.md`.

Sempre vero e facile da dimenticare: **il tema non porta i componenti.** `FluidTheme` cambia colori, tipografia,
angoli e curve; il vetro, l'overscroll e le liste raggruppate arrivano solo chiamando `FluidScreen`,
`FluidListGroup` e il materiale. Un'app con solo il tema si vede quasi identica a prima: dillo così.

Due regole per le app dalla 4.0, con il perché e la verifica in `regole.md`: **dopo `enableEdgeToEdge()`
si spegne il velo di contrasto della barra di navigazione** (`if (Build.VERSION.SDK_INT >= 29)
window.isNavigationBarContrastEnforced = false`: altrimenti, con la navigazione a tre tasti, una fascia scura in fondo
a ogni foglio), e **le pagine delle schede di una tab bar restano vive o precomposte** (se ogni cambio di scheda
compone la pagina da zero, la pillola si ferma a metà viaggio). Per vedere le strade del vetro una accanto all'altra:
la pagina «Parità» della galleria (`--es gallery.page parity`).

Una regola per il codice, dell'engine e delle app (F7.21): **uno stato di Compose si scrive dal thread principale, o è
un valore immutabile intero**. Una lista o una mappa di stato cambiata da un filo in sottofondo mentre un altro la legge
dà `ConcurrentModificationException` e liste a metà, anche dentro un'istantanea (Compose 1.10.5); il lavoro va su
`Dispatchers.IO`, la scrittura fuori dal `withContext`. Il perché e i numeri sono in `regole.md`.

## Compiti frequenti

Ogni compito ha la sua ricetta con i passi e il perché in `ricette.md`. Qui, il filo.

- **Installare l'engine in un'app** — prima verifica che ci entri (cinque controlli in `integrazione.md`: Compose
  androidx o Multiplatform, Compose c'è, AGP/compileSdk, `minSdk ≥ 26`, cartella `engine/` libera). Un'app in
  Compose Multiplatform non può ospitare `engine-ui`: dillo e fermati. Poi submodule, `engine-install.ps1`, dipendenza,
  tema riscrivendo il corpo della funzione di tema esistente.
- **Aggiungere una schermata** — `FluidScreen(title, subtitle) { item { FluidSectionHeader(...) } item { FluidListGroup { FluidListRow(...) } } }`; scheda `componenti/schermata.md` e `contenuto.md`.
- **Aggiungere un componente all'engine** — solo se è **generico**; in `engine-ui`, prefisso `Fluid`, KDoc che dice
  *perché*, un test se ha logica, una demo nella galleria; poi release e catalogo (`ricette.md`).
- **Far trasformare il vetro** — dalla 1.9.7 i preset usano già Fluid-physics: un'app che aggiorna ritrova la card
  che si espande trasformata senza cambiare una riga. Si chiama qualcosa solo per il tasto che si apre nel proprio
  menu (`FluidMorphMenuButton` + `FluidMorphMenuHost`) o per una forma inventata (`rememberFluidPhysicsState`,
  `Modifier.fluidPhysicsSurface`). Un menu si sceglie col dito che l'ha aperto; ha un pannello, un viaggio, un
  materiale (`componenti/menu-modali.md`). **Giudica dai fotogrammi**, mai «giusto per costruzione».
- **Dare un assistente a un'app** — `engine-ai` porta tutto ciò che non sa cosa fa l'app; l'app scrive solo gruppi,
  tool (`AiTool<C>`), prompt, azioni e UI (`ai.md`, `moduli/`).
- **Aggiungere un feature flag** — `EngineFlag(key, default)` con il default = come si comporta oggi; `remoteConfig.flag(...)`;
  la voce nel manifest solo quando serve (`config-remota.md`).
- **Aggiornare l'engine di un'app** — `engine-update.ps1 -AppRoot . -Version X`; se l'app è un porto lo script non
  tocca niente; poi compila, leggi le voci **BREAKING**, committa `engine` + `engine.properties` insieme.
- **Tagliare una versione** — `engine-release.ps1 -Version X -Notes "…" -Tag`: muove insieme `ENGINE_VERSION`,
  `EngineBuild.VERSION`, la «Versione documentata» di questo file e il tag. Mai a mano. Poi rigenera il catalogo (`scripts/gen_api_catalog.py`).
- **«Il widget non segue il tema»** — manca il collegamento fra impostazioni e `updateAll` (`widget.md`).
- **Qualcosa non va** — `risoluzione-problemi.md`, e prima: l'app è sull'ultima patch (3.0.1–3.0.4 correggono
  metà dei sintomi)?

## Il movimento ridotto

`FluidTheme` applica il movimento ridotto quando la durata delle animazioni di sistema è a zero (4.0).
Per un tema proprio o per seguire anche le scale intermedie, usa `FluidMotionPolicyProvider`
(`componenti/movimento.md`).

## Prima di dire che hai finito

```powershell
powershell -ExecutionPolicy Bypass -File engine\tools\engine-doctor.ps1 -AppRoot .
```

```bash
./gradlew.bat --no-daemon --no-watch-fs :app:assembleDebug testDebugUnitTest
```

Poi **guardala sul dispositivo**: la UI si giudica in mano, e le animazioni dai fotogrammi (`strumenti-e-qa.md`).
Se hai toccato l'engine dentro un'app: o l'hai committato nel repo dell'engine, o lo stai per perdere. Se hai
toccato il codice pubblico dell'engine: `gen_api_catalog.py`, la scheda, `check_docs.py`, `sync_docs.py`.

## Mantenere questa skill

Vive dentro il repo dell'engine, in `skill/fluid-engine/`. Le correzioni si fanno **sempre qui**.

- **Catalogo API** (`api/`): *generato*, mai a mano. `python -I skill/fluid-engine/scripts/gen_api_catalog.py`
  dalla radice dell'engine; `--check` esce 1 se è vecchio. Rilancialo a ogni release.
- **Schede** (`componenti/`, `moduli/`): scritte a mano dal codice. `check_docs.py` fa fallire un nome che non esiste
  o un link rotto: lancialo dopo ogni modifica. Una scheda dice *perché* e *dove si rompe*; le firme stanno nel catalogo.
- **`docs/riferimento/`**: copia generata di tutta la skill, leggibile senza di lei. `sync_docs.py` la riscrive,
  `--check` verifica l'allineamento. Non si modifica a mano.
- **Installazione:** verifica com'è installata (`Get-Item "$env:USERPROFILE\.claude\skills\fluid-engine" -Force | Select-Object LinkType, Target`).
  `LinkType: Junction` (com'è sulla macchina di Alessio): l'installazione *è* il repo, le modifiche sono già live e
  `Copy-Item` lì sopra fallisce (copierebbe i file su se stessi). `LinkType` vuoto: è una copia, da rifare dopo ogni
  modifica con `Copy-Item -Recurse -Force <engine>\skill\fluid-engine "$env:USERPROFILE\.claude\skills\"`.

## Il piano di adozione

`ADOZIONE.md` alla radice dell'engine tiene l'ordine deciso da Alessio per portare **i componenti** (non solo il
tema) su tutte le app: leggilo prima di una migrazione e aggiornalo quando una fase si chiude. Stato e tappe:
`storia.md`.

## Indice dei riferimenti

- `regole.md` — le regole del design system, per esteso, con il perché
- `mappa.md` — moduli, dipendenze, toolchain, cosa installare
- `ricette.md` — i compiti frequenti, passo per passo
- `risoluzione-problemi.md` — sintomo → causa → rimedio
- `strumenti-e-qa.md` — script, build, firma, collaudo sul dispositivo
- `storia.md`, `glossario.md` — le tappe; le parole
- `integrazione.md` — mettere l'engine in un'app nuova o esistente
- `aggiornamenti.md` — versioni, canali, rilascio, rollback
- `config-remota.md` — manifest, flag, kill switch, compatibilità
- `widget.md` — il kit Glance
- `limiti.md` — cosa non si può fare, e cosa rispondere quando viene chiesto
- `ai.md` — l'assistente: cosa dà `engine-ai`, cosa resta all'app, come si scrive un tool
- `design-system.md` — l'inventario storico di `engine-ui` (token, regola del vetro, pagine…), che le schede in `componenti/` ora dettagliano
- `componenti/*.md`, `moduli/*.md` — le schede (indici nelle tabelle sopra); nuove nella 3.1.0: `componenti/contenuto-web.md`, `componenti/grafici.md`, `moduli/richtext.md`
- `api/*.md` — il catalogo generato; `api/versioni.md` — una riga per versione dal changelog
