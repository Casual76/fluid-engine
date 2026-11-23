# Piano dell'ecosistema Pampa: dopo F6

Scritto il 23 novembre 2026, dopo le scelte di Alessio sul banco delle idee. Questo piano continua quello del vetro
(`docs/liquid-glass/piano.md`): F6 si chiude là, F7 resta là. Qui stanno gli elementi nuovi, PampAI al centro,
l'Account Pampa, la galleria-vetrina e il passaggio delle app al nuovo engine.

Materiali di partenza:
- le scelte, con le note di Alessio: `artifacts/liquid-glass/sessioni/idee-2/scelte-alessio.md`;
- le 88 schede scelte per intero: `artifacts/ecosistema/idee-scelte.md`;
- la ricognizione delle app, una scheda per app: `artifacts/ecosistema/ricognizione/`.

Le cartelle `artifacts/` sono fuori da git e restano sul PC.

## 1. Cosa è stato scelto

- **Le idee.** Sul banco c'erano 152 idee da sei modelli, in due giri. Alessio ne ha scelte 88: 58 così come sono e 30
  rivedute con una nota. Le altre 64 restano fuori.
- **Le app.** Dal 23 novembre si possono cambiare senza limiti nove app:
  - cambiamenti maggiori: Universal Converter, Pampa Store, MyCloud Gallery 2, PampAI;
  - cambiamenti minori: ClasseViva Expressive, Fluid Transit, Fluid Weather;
  - poi Fluidify e Pampa Notes.

  Alla fine tutte ricevono il grande aggiornamento al nuovo engine.
- **L'ordine** (scelta di Alessio): prima tutti gli elementi nuovi dell'engine, poi PampAI e l'Account Pampa, poi le
  app. F7 corre insieme agli elementi.

## 2. Le regole che valgono per tutto il piano

- **Nessuna pubblicazione, nemmeno beta**, di nessuna app e dell'engine, fino a una data ancora da decidere e
  probabilmente lontana. Niente publisher, niente manifest dello store.
- **Git.**
  - Nelle app si lavora **solo in locale**: rami locali, niente push finché Alessio non lo dice.
  - Nell'engine si continua come ora sul ramo `t3/liquid-glass-engine`, con push. In main si entra solo alla fine,
    con un'unione schiacciata, dopo la pulizia della repo (§ 9) e con l'ok di Alessio.
- **Prove sul Tab S9.**
  - Le app si installano sopra le versioni vere di Alessio, tranne ClasseViva Expressive, MyCloud Gallery e Fluidify:
    per queste tre si fa una variante dev affiancata (applicationId con suffisso), e l'accesso lo fa Alessio quando
    serve.
  - Restano le regole di sempre: un agente alla volta, `install -r`, ripristino completo, niente S25 né emulatore.
- **Qualità.**
  - Ogni elemento con un equivalente Apple si confronta con Apple: misure, tempi, comportamento.
  - Ogni elemento rispetta le regole di F7: 120 FPS costanti, contano gli spike, scala automatica e invisibile per la
    fascia bassa (riferimento A52s).
- **Accessibilità.** Ha priorità più bassa del resto, e lì si accettano risultati più permissivi (nota di Alessio su
  `avanzamento-accessibile`). Le basi restano: etichette per TalkBack, contrasto leggibile, tocchi da 44 dp.
- **Le app restano gratuite e open source.** Niente licenze né abbonamenti.
- **Il giudizio è di Alessio**, su quello che vede in chat: riprese, confronti e domande a scelta multipla.

## 3. L'ordine dei lavori

| Fase | Cosa | Quando |
|---|---|---|
| F6 (chiusura) | F6B.15, F6C.20, F6B.16 da unire; sessione finale sul tablet (F6B.14, F6C.6, F6D.2) con le prove a mano di Alessio | ora |
| **F8** | **Elementi nuovi dell'engine**, in quattro ondate (§ 4) | subito dopo F6 |
| F7 | Ottimizzazione, come da `docs/liquid-glass/piano.md` | insieme a F8 |
| **F9** | **PampAI al centro dell'IA** (§ 5) | dopo F8 |
| **F10** | **Account Pampa** (§ 6), con calma, accanto a F9 | dopo F8 |
| F11 | Fluid Glass da suite di prove a vetrina dell'engine (§ 7) | quando F8 è quasi finita |
| **F12** | **Le app sul nuovo engine**, con i loro cambiamenti (§ 8) | dopo F9 e F10 |
| R | Pulizia della repo e unione schiacciata in main (§ 9) | alla fine |

L'uscita dell'engine 4.0 aspetta l'Account Pampa, come ha detto Alessio.

**Come si lavora su ogni pacchetto**, come in F6:
- un brief con le schede delle idee, le note di Alessio e il riferimento Apple;
- un agente che sviluppa al PC, con i test, i golden e la documentazione dell'engine aggiornata;
- le riprese sul tablet;
- l'unione da parte mia, poi il giudizio di Alessio.

Al massimo tre o quattro pacchetti insieme, secondo le quote degli account; il tablet sempre a uno solo.

## 4. F8: gli elementi nuovi dell'engine

Sono 74 idee in 27 pacchetti. Ogni elemento facoltativo vive in un modulo opt-in, così un'app paga solo quello che usa.
Per ogni pacchetto l'anteprima del banco (`idee-2/pagina/anteprime/<id>.html`) è un punto di partenza, non il disegno
finale.

### Ondata 1: piccoli elementi in `engine-ui`, senza librerie nuove

- **F8.1 Testo e numeri in movimento**
  - Idee: `numeri-che-scorrono`, `testo-espandibile`, `conto-alla-rovescia`, `formattatori-e-tempi-vivi`,
    `immagine-che-arriva`, `stati-pronti`.
  - Le note di Alessio:
    - i numeri che scorrono hanno il **blur durante il movimento**;
    - il testo che si espande arriva **gradualmente, col blur**, non tutto insieme;
    - lo stesso arrivo graduale serve anche al testo generato da PampAI, che vive in un altro ambito ma ha la stessa
      animazione.
- **F8.2 Gesti e ruote**
  - Idee: `selettore-a-ruota`, `ghiera-a-righello`, `cursore-a-intervallo`.
- **F8.3 Campi speciali**
  - Idee: `campo-con-unita`, `campo-codice`, `campo-password`, `valore-riservato`, `copia-delicata`, `tasto-incolla`.
  - Nota di Alessio sul tasto incolla: compare da solo **solo per i dati concordati**, cioè un OTP richiesto, un link o
    uno screenshot. Gli appunti normali no.
- **F8.4 Liste e pagine**
  - Idee: `sezioni-richiudibili`, `selezione-trascinando`, `griglia-media`, `pager-segmentato`.
  - Le note di Alessio:
    - all'apertura di una sezione, **il vetro si separa dall'elemento d'origine**;
    - la griglia mostra **anteprime in generale**, anche di file e non solo di foto e video, configurabile dall'app;
    - il pager segmentato è **più simile alla pillola di navigazione** che all'anteprima del banco.
- **F8.5 Festa e simboli**
  - Idee: `coriandoli`, `effetti-dei-simboli`.
- **F8.6 Grafici**
  - Idee: `grafici-da-toccare`, `indicatore-a-quadrante`.
- **F8.7 Colore**
  - Idee: `selettore-colore`, `colore-dal-contenuto`.
  - Il colore dal contenuto parte da due codici che ci sono già:
    - **Pampa Widgets**, con l'estrattore automatico da bitmap a seme (`MediaWidgetUpdater.kt:1232-1312`);
    - **Fluidify**, con la multipalette e la cache per URL (`ArtworkColor.kt`).

    L'engine prende l'estrazione e i ruoli col contrasto verificato. L'app sceglie l'immagine e la superficie.

### Ondata 2: pannelli, date, condivisione, prima volta

- **F8.8 Date**
  - Idee: `calendario-in-pagina`, `intervalli-e-giorni-sparsi`, `scelta-ricorrenza`.
  - Il calendario va accanto a `FluidDateTimeSheet`, che poi lo usa.
- **F8.9 Dati nel testo**
  - Idee: `dati-nel-testo`, accanto a `FluidMarkdown`.
- **F8.10 Condivisione**
  - Idee: `foglio-condividi`, `prepara-la-copia`, `uscite-stampabili`, `arrivi-da-altre-app`.
  - Nota di Alessio: in generale serve un modo di **condividere i dati in formati migliori del testo**. Per esempio un
    voto o un impegno dell'agenda di ClasseViva come immagine, senza fare lo screenshot. In ClasseViva gli ingressi ci
    sono già, `shareGrade` e `shareEntry`, che oggi mandano testo.
- **F8.11 Prima volta, permessi e avvio**
  - Idee: `benvenuto-e-novita`, `permesso-nel-punto-giusto`, `sfondi-vivi`, `apertura-dall-icona` (rivista).
  - Le note di Alessio:
    - gli sfondi vivi vanno **solo negli onboarding e nelle pagine giocose**, non in quelle di tutti i giorni;
    - l'avvio è l'inverso dell'idea del banco. Ogni app ha **sempre un primo fotogramma pronto**, così l'avvio sembra il
      più rapido possibile, come è obbligatorio su iOS.
    - L'animazione di avvio di Android non si tocca. La misura dell'avvio sta in F7.
- **F8.12 Indirizzi delle pagine**
  - Idee: `indirizzi-delle-pagine`, i deep link tipizzati per `FluidNavHost`.

### Ondata 3: media e documenti, moduli nuovi facoltativi

- **F8.13 Audio**
  - Idee: `registratore-vocale`, `onda-audio`, `riproduzione-di-sistema` (`engine-playback`),
    `ascolto-dal-paragrafo` (`engine-transcript`).
  - Nota di Alessio: servono **timestamp ai paragrafi**. Pampa Notes ne ha una versione fatta non molto bene: il tocco
    salta all'inizio della frase anche quando ci sono i tempi per parola (`SessionScreen.kt:1305`). L'engine dà il
    contratto di tempi e salto per paragrafo e per parola, sopra `FluidSpokenText`.
- **F8.14 Foto e allegati**
  - Idee: `scelta-foto-e-video` (`engine-media`), `pannello-allegati`.
  - Nota di Alessio: il selettore **legge anche da MyCloud Gallery**, con la ricerca semantica e le foto che stanno solo
    nel backup.
  - Va preparato **aperto a sorgenti future**, come un archivio con indice e ricerca semantica.
  - L'engine definisce un contratto di sorgente esterna. MyCloud Gallery ha già un selettore, un `DocumentsProvider` e la
    consegna degli URI (`PickerDelivery.kt`, `MediaDocumentsProvider.kt`). In F12 le espone la ricerca rispettando gli
    stessi filtri d'accesso.
- **F8.15 Modifica delle immagini**
  - Idee: `ritaglia-e-regola` (`engine-editor`), `confronto-prima-dopo`.
- **F8.16 Disegno e annotazioni**
  - Idee: `disegno-e-annotazioni` (`engine-ink`, con `androidx.ink`).
- **F8.17 Documenti**
  - Idee: `scanner-documenti` (`engine-scan`), `testo-nelle-immagini` (famiglia di visori OCR).
  - Lo scanner consegna immagini o PDF, che Universal Converter e Pampa Notes accettano già per URI.
- **F8.18 File**
  - Idee: `sfoglia-file`, `visore-con-bilancio` (rivista), `riprendi-dallo-stesso-punto`.
  - Nota di Alessio sul bilancio: è **automatico e invisibile**. Un PDF gigante ha la barra di scorrimento per tutto il
    documento, ma solo una selezione di pagine è disegnata e il resto si carica al momento. Non si chiede niente
    all'utente: serve alle prestazioni.

### Ondata 4: sistema, dati, rete, dispositivi

- **F8.19 Notifiche e attività**
  - Idee: `notifiche-di-sistema`, `promemoria-affidabili`, `attivita-fuori-dall-app`.
  - Nota di Alessio: le attività fuori dall'app diventano **un modulo ben fatto per le Live Update di Android**, da usare
    anche per altro. Fluid Transit pubblica già una notifica di viaggio con `ProgressStyle` su Android 16
    (`NavigationService.kt:1329-1359`), ma non chiede la promozione: è il primo cliente.
- **F8.20 Rete e trasferimenti**
  - Idee: `stato-rete-e-offline`, `pochi-dati-poca-batteria`, `centro-trasferimenti`.
- **F8.21 Sincronizzazione**
  - Idee: `sincronizzazione-offline`, `scelta-tra-versioni`.
- **F8.22 Ricerca**
  - Idee: `indice-ricerca`, `ricerca-in-tutte-le-app`.
  - La ricerca fra le app passa dal ponte firmato che le app già usano con PampAI.
- **F8.23 Sicurezza**
  - Idee: `blocco-biometrico`, `cassaforte-cifrata`.
- **F8.24 Diagnostica e Laboratorio**
  - Idee: `crash-e-lentezze`, `segnala-problema` (rivista), `tracce-dei-tempi` (rivista), `flag-graduali` (rivista).
  - Le note di Alessio:
    - **Segnalare un problema** non è invadente: non chiede «qualcosa non va?» a ogni screenshot. Si può scegliere tra
      due strade:
      - preparare **un file con tutte le informazioni da mandare su WhatsApp**, semplice e rapido;
      - aprire **una issue pubblica su GitHub**, che entra nel flusso, fa numeri sulla repo e dà **crediti** a chi
        segnala.
    - **Le tracce dei tempi** esistono solo nelle versioni dev e debug, mai nelle release.
    - **I flag**: uno strumento nativo dell'engine per le **funzioni sperimentali**, attivabili da un «Laboratorio» nelle
      impostazioni. I flag remoti nuovi non interessano. Quelli che PampAI già usa per gli incidenti, come il kill
      switch, restano dove sono.
- **F8.25 Widget e Wear**
  - Idee: `vetrina-dei-widget`, `tile-e-complicazioni`, `wear-con-ricevuta`.
- **F8.26 Luoghi e salute**
  - Idee: `mappa-e-luoghi` (`engine-map`, MapLibre), `salute-e-movimento` (Health Connect).
- **F8.27 Passa di vicino**
  - Idee: `passa-di-vicino` (rivista).
  - Alessio la voleva solo se Android avesse aperto Quick Share alle app. È così: Google ha annunciato un **Quick Share
    SDK** per Android e iOS, atteso nella **prima metà del 2027**, con circa 20 righe di integrazione.
  - Il pacchetto parte quando l'SDK esce. Fino ad allora è in attesa.

## 5. F9: PampAI al centro dell'IA

Alessio vuole che **tutto il flusso dell'IA nelle app Pampa passi da PampAI**, e che PampAI diventi bella da usare. Oggi
le cose stanno così:
- PampAI chiama già i tool delle altre app con un ponte firmato.
- **Manca il percorso inverso**: un'app non può mandare una richiesta a PampAI e riceverne il risultato.
- La memoria dipende dal tool `ricorda`, che il modello usa o no.
- Le ricevute mostrano troppi dettagli.
- Gemini Nano non c'è.
- Ogni app ha il suo assistente con chiavi proprie.

Le idee di questa fase sono nove: `connettori-mcp`, `consumi-assistente`, `fonti-apribili`, `modello-sul-telefono`,
`ricevute-tool-ia`, `ricordi-dell-assistente`, `assistente-vede-la-pagina`, `strumenti-di-scrittura`, `centro-aiuto`.

- **F9.1 Il contratto inverso.**
  - Un'app chiede a PampAI e riceve la risposta: identità dell'app che chiama, ambito, contesto della pagina col
    consenso, risultato.
  - È una nuova versione del ponte (`engine-ai-bridge`), compatibile con la v1 e la v2.
  - Su questo poggiano `assistente-vede-la-pagina` e `strumenti-di-scrittura` («via PampAI, nelle app Pampa»).
- **F9.2 Tool e token.**
  - Prima si misura quanto pesano davvero gli schemi, i risultati e il prompt di instradamento.
  - Poi i **tool di base** diventano quelli **dell'app da cui si parla**. Le altre app restano raggiungibili, ma non
    sono caricate di serie.
  - I connettori MCP (`connettori-mcp`) entrano con la stessa ricerca dei tool, per risparmiare token in ingresso.
- **F9.3 Una memoria che funziona.**
  - Un estrattore automatico dopo i turni, i ricordi correggibili, la ricerca per pertinenza e la revoca.
  - Tutto passa dalla memoria di PampAI, anche quello che nasce in un'altra app.
- **F9.4 Ricevute semplici.**
  - All'utente si mostra una riga come «Controllo il meteo», con l'icona di Fluid Weather.
  - I dati dettagliati restano nella diagnostica e finiscono solo in una segnalazione (F8.24).
- **F9.5 Gemini Nano, se c'è.**
  - Solo il modello di sistema (AICore), niente da scaricare o installare.
  - Serve per i compiti semplici, per esempio scegliere il livello di ragionamento al posto di una chiamata cloud che
    aggiunge attesa e costi. Oggi c'è un selettore euristico in `AssistantEngine.kt:375-389`.
- **F9.6 Fonti apribili e consumi.**
  - Le fonti si aprono dal punto citato.
  - I consumi esistono già (`UsageRoute.kt`): si verificano e si tengono.
- **F9.7 L'aiuto passa dall'assistente.**
  - Ogni app ha una documentazione scritta per PampAI.
  - Niente sezione info dentro le app (nota su `centro-aiuto`).
- **F9.8 PampAI rifatta.**
  - Chat, schede dei risultati, overlay di sistema, tastiera, navigazione e allegati, sul nuovo vetro.
  - I «widget che non funzionano» e pieni di glitch di cui parla Alessio sono **le schede dei risultati in chat**
    (calendario, musica, timer…), come ha confermato il 23 novembre. Vanno rifatte.
- **F9.9 Le app passano da PampAI.**
  - Gli assistenti di ClasseViva, Fluid Transit, Fluid Weather e Pampa Notes diventano clienti di PampAI e smettono di
    tenere chiavi proprie.
  - Per Pampa Notes vanno portati anche i modelli di trascrizione, i budget per cartella, i tentativi e le richieste
    pagate, e resta l'opzione del computer con WhisperX.
  - Il passaggio vero avviene in F12, app per app.

## 6. F10: l'Account Pampa

La nota di Alessio, per intero:

> da inserire, e se possibile integrare anche l'accesso a google, usato da qualche app, comune poi per tutte le app, in
> modo che l'account pampa porti con se anche quello google (ovviamente però ci sta prima da concedere dalle impostazioni
> dell'account pampa le concessioni google). è difficile e richiede diverso tempo, quindi da fare contemporaneamente ad
> altri lavori, con calma. Il gestore ell'account direi di farlo vivere dentro pampa store (di cui autorizzo la modifica a
> questo scopo), così che abbia un motivo in piu di esistere lo store.

Le idee di questa fase sono cinque: `account-pampa`, `passkey`, `tema-unico-fra-app`, `icona-dell-app`,
`vetro-trasparente-o-colorato`.

**Le scelte già fatte.**
- **Accesso:** con una **passkey**, e **Google in alternativa**. Le concessioni Google per le app si danno dalle
  impostazioni dell'account.
- **Server: Cloudflare.** Alessio ha lasciato a me la scelta fra Firebase e Cloudflare. Ho scelto Cloudflare per tre
  motivi:
  - Pampa Notes (Worker, D1, R2 e scambio del token Google) e Fluid Transit (Worker e R2) lo usano già, quindi resta
    una piattaforma sola;
  - le passkey si verificano direttamente nel Worker. Firebase Auth non le gestisce da sé e richiederebbe Cloud
    Functions, cioè il piano a consumo con la carta registrata;
  - con pochi utenti il piano gratuito basta largamente.

  Alessio riceverà **una guida passo passo** per l'impostazione. Dopo, la manutenzione dovrebbe essere minima.

**I pacchetti.**
- **F10.1 Progetto e guida.**
  - Si definiscono l'identità, le sessioni, i dispositivi, le concessioni Google per servizio con la revoca, il
    funzionamento offline e il backup.
  - Si decide anche come le app chiedono l'identità allo store. La strada probabile è un account di sistema Android
    gestito da Pampa Store, che appare in Impostazioni → Account e si raggiunge dalle app firmate con la stessa chiave.
  - Si scrive la guida per Alessio: account Cloudflare, `wrangler login`, client OAuth nella console Google.
- **F10.2 Il server.** Un Worker «account» con D1:
  - la verifica delle passkey (WebAuthn);
  - la verifica del token Google;
  - le sessioni e le concessioni.

  Il Worker di Pampa Notes si fida dell'account, così il login di Notes confluisce.
- **F10.3 Il modulo dell'engine.** `engine-account`, con `engine-auth` per le passkey e Credential Manager: le app lo
  usano per sapere chi è l'utente e cosa ha concesso.
- **F10.4 Il gestore in Pampa Store.** Creazione, accesso, dispositivi, concessioni Google, tema e icone. Lo store
  oggi non ha nessuna identità: tutto è nuovo.
- **F10.5 Tema e icone per tutte le app.**
  - Il tema si sceglie una volta e vale ovunque (`tema-unico-fra-app`).
  - Dentro il tema ci sta anche la preferenza fra vetro trasparente e colorato. Nota di Alessio: «al massimo […]
    scegliere il suo tema preferito in maniera centrale con l'account pampa».
  - Le icone si possono cambiare (`icona-dell-app`), e sono **riviste per la grande unione** delle app nell'ecosistema.
- **F10.6 I login che ci sono già confluiscono.** Il primo è l'accesso Google di Pampa Notes, che oggi alimenta la sua
  sincronizzazione e il relay su Cloudflare.

## 7. F11: Fluid Glass come vetrina

La galleria del vetro oggi è soprattutto una nostra suite di prove. Deve diventare **un percorso guidato di tutto il
vetro e dell'engine**, che serve anche da pubblicità.
- Le scene di prova e di misura restano, ma in un'area per lo sviluppo, fuori dal giro principale.
- Gli sfondi vivi (F8.11) e gli elementi nuovi di F8 sono il materiale della vetrina.
- Si comincia quando F8 è quasi finita, per mostrare tutto.

## 8. F12: le app sul nuovo engine

Ogni app passa all'engine 4.0 e riceve i suoi cambiamenti. Si comincia dalle più semplici, per far emergere presto i
problemi di migrazione. Le dimensioni vengono dalla ricognizione.

| App | Oggi | Cosa riceve oltre al nuovo engine |
|---|---|---|
| **Universal Converter** | 24 file Kotlin; motore WASM in una WebView; nove tool per PampAI; engine 3.0.4 | Visori dell'engine per l'anteprima dei file d'ingresso e d'uscita, ingresso dallo scanner, griglia di anteprime, condivisione nuova. *Maggiore* |
| **Pampa Store** | 25 file Kotlin; catalogo e APK da GitHub; nessuna identità; engine 3.0.4 | Il gestore dell'Account Pampa (F10.4); la verifica dello SHA-256 al momento dell'installazione, perché il publisher lo scrive ma lo store non lo controlla. *Maggiore* |
| **MyCloud Gallery 2** | ~4.900 file Kotlin; 49 moduli; NAS via SMB; ricerca semantica sul telefono; engine 3.0.4 | La sorgente per il selettore dell'engine, con ricerca semantica e foto solo nel backup (F8.14); la nuova UI su timeline, visore e selettore. Variante dev sul tablet. *Maggiore* |
| **PampAI** | 166 file Kotlin; tool federati; memoria e consumi; engine 3.0.4 più un fork | Il rifacimento di F9.8 e il ruolo di centro dell'IA. Va riassorbito il suo fork dell'engine (`ab11336`, `4e8c1dc`). *Maggiore* |
| **Pampa Notes** | 590 file Kotlin; Worker Cloudflare; accesso Google; engine 3.0.5 | Ascolto dal paragrafo (F8.13), IA via PampAI, login nell'Account Pampa, ingresso dallo scanner |
| **ClasseViva Expressive** | 287 file Kotlin; sessioni ClasseViva/CIE; engine 3.0.5 più un commit | La diagnosi del crash sull'A52s; voto e impegno come immagine (F8.10); IA via PampAI. Variante dev sul tablet. *Minore* |
| **Fluid Transit** | Orari offline e realtime via Worker; mappa MapLibre; engine 3.0.5 | La Live Update del viaggio (F8.19); il contrasto del testo bianco su 9 tinte di linea su 12 (`APERTO.md`); IA via PampAI. *Minore* |
| **Fluid Weather** | Nowcast sul telefono, widget, notifiche durevoli; engine 3.0.5 | Promemoria affidabili, notifiche educate, IA via PampAI. *Minore* |
| **Fluidify** | 683 file Kotlin; player Spotify, AirPods, Wear; engine 3.0.6 più 7 commit | Colore dal contenuto dall'engine (F8.7). È la migrazione più delicata: player, AirPods, Wear, audio nativo. Variante dev sul tablet |

Altri dettagli:
- **Il crash di ClasseViva sull'A52s** non ha ancora un log. La ricognizione indica due piste:
  - l'assistente legge gli allegati interi con `readBytes()` prima di controllare la soglia (`AttachmentReader.kt:52-87`);
  - la WebView della CIE.

  Serve sapere quando accade. Si guarda con le regole di F7 per la fascia bassa.
- **Fluidify.** Il 23 novembre la linea beta (2.1.0-beta.5 e la parità LibrePods del 21) è stata unita in master in
  locale: master ora è a `215d59aa`, 2.1.0-beta.7. Lo stato non salvato del 10 novembre è nel ramo locale
  `archivio/candidata-beta2-20261110`.
- **Fluid Weather.** Il 23 novembre il ramo `reliability/daily-use-20261117` (2.2.0-beta.1) è stato unito in main in
  locale: main ora è a `776ec51`.

## 9. Pulizia della repo e uscita

Prima del push finale in main la repo dell'engine va pulita:
- circa 7 milioni di righe del diff sono tracce del banco, 232 MB in tutto: escono da git e finiscono in `.gitignore`;
- resta tutto quello che serve davvero;
- l'unione in main è schiacciata e si fa solo con l'ok di Alessio.

Lo stesso vale per le app quando arriverà il momento.

## 10. Cosa resta fuori

Le 64 idee scartate sono elencate in `scelte-alessio.md`. Due note valgono come regola:
- `licenze-e-abbonamenti`: le app sono open source e gratuite, e restano così;
- `avanzamento-accessibile`: l'accessibilità ha priorità inferiore, con risultati più permissivi (§ 2).

## 11. Domande chiuse il 23 novembre sera

- **Fluidify sul tablet**: con una variante dev affiancata, come ClasseViva e MyCloud Gallery.
- **I «widget» di PampAI**: sono le schede dei risultati in chat (§ 5, F9.8).
- **Il ramo di Fluid Weather**: unito in main, in locale (§ 8).

## 12. Dove va ogni idea scelta

| Pacchetto | Idee |
|---|---|
| F8.1 | numeri-che-scorrono, testo-espandibile, conto-alla-rovescia, formattatori-e-tempi-vivi, immagine-che-arriva, stati-pronti |
| F8.2 | selettore-a-ruota, ghiera-a-righello, cursore-a-intervallo |
| F8.3 | campo-con-unita, campo-codice, campo-password, valore-riservato, copia-delicata, tasto-incolla |
| F8.4 | sezioni-richiudibili, selezione-trascinando, griglia-media, pager-segmentato |
| F8.5 | coriandoli, effetti-dei-simboli |
| F8.6 | grafici-da-toccare, indicatore-a-quadrante |
| F8.7 | selettore-colore, colore-dal-contenuto |
| F8.8 | calendario-in-pagina, intervalli-e-giorni-sparsi, scelta-ricorrenza |
| F8.9 | dati-nel-testo |
| F8.10 | foglio-condividi, prepara-la-copia, uscite-stampabili, arrivi-da-altre-app |
| F8.11 | benvenuto-e-novita, permesso-nel-punto-giusto, sfondi-vivi, apertura-dall-icona |
| F8.12 | indirizzi-delle-pagine |
| F8.13 | registratore-vocale, onda-audio, riproduzione-di-sistema, ascolto-dal-paragrafo |
| F8.14 | scelta-foto-e-video, pannello-allegati |
| F8.15 | ritaglia-e-regola, confronto-prima-dopo |
| F8.16 | disegno-e-annotazioni |
| F8.17 | scanner-documenti, testo-nelle-immagini |
| F8.18 | sfoglia-file, visore-con-bilancio, riprendi-dallo-stesso-punto |
| F8.19 | notifiche-di-sistema, promemoria-affidabili, attivita-fuori-dall-app |
| F8.20 | stato-rete-e-offline, pochi-dati-poca-batteria, centro-trasferimenti |
| F8.21 | sincronizzazione-offline, scelta-tra-versioni |
| F8.22 | indice-ricerca, ricerca-in-tutte-le-app |
| F8.23 | blocco-biometrico, cassaforte-cifrata |
| F8.24 | crash-e-lentezze, segnala-problema, tracce-dei-tempi, flag-graduali |
| F8.25 | vetrina-dei-widget, tile-e-complicazioni, wear-con-ricevuta |
| F8.26 | mappa-e-luoghi, salute-e-movimento |
| F8.27 | passa-di-vicino |
| F9 | connettori-mcp, consumi-assistente, fonti-apribili, modello-sul-telefono, ricevute-tool-ia, ricordi-dell-assistente, assistente-vede-la-pagina, strumenti-di-scrittura, centro-aiuto |
| F10 | account-pampa, passkey, tema-unico-fra-app, icona-dell-app, vetro-trasparente-o-colorato |
