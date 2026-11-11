# R8 · Componenti dinamici: parti dell'interfaccia che si trasformano

Ricerca leggera del 2026-11-11 per il progetto «iOS in Android». Nasce dalla richiesta di Alessio del campo di inserimento
dinamico (`piano.md`, riquadro di F4B) e dalla sua domanda: *«se hai suggerimenti per altri elementi di questo tipo, da
creare o da rivedere, dimmelo»*. Dopo una prima lettura Alessio ha deciso che questi elementi avranno **una fase dedicata**
(«creazione e modifica di elementi di qualità preconfigurati», **F6B**) e ha commentato cinque spunti: barra delle azioni di
selezione, elemento che diventa pagina, tasti con esito, filtri a pillola, barra sopra la tastiera. Il documento ne tiene
conto e va oltre: ci sono idee che nessuno ha ancora nominato, ricavate da ciò che le sue app costruiscono a mano.

**Cos'è un «componente dinamico» qui.** Un elemento che sta sopra il contenuto (è dell'interfaccia, non della pagina), resta
fra una pagina e l'altra o nasce da un tasto, e **si trasforma**: cambia forma, si espande, si fonde coi vicini, segue la
tastiera o lo scorrimento. Una cosa che si disegna una volta e sta ferma non è nell'elenco.

**Metodo.** (1) Catalogo e schede dell'engine (`skill/fluid-engine/`), per dire «c'è» da «non c'è». (2) `piano.md` (F3, F4A,
F4B, F5A, F5B, F6, §7) e R1, per non riproporre ciò che è già in programma. (3) Le app dell'utente in sola lettura, a campione:
nomi di file, intestazioni e KDoc, e qualche `grep` mirato; **non ho letto i file per intero**, né configurazioni o dati.
(4) Poche fonti sul web (Apple HIG, WWDC25, Android 16, Material 3 Expressive). Nessun codice, nessuna build, nessun
dispositivo. Dove una fonte non dice una cosa lo scrivo: per le barre di selezione di Foto e Mail di iOS 26 le fonti trovate
non descrivono nulla di utilizzabile, e per i permessi nelle app l'evidenza è debole (vedi riga 21).

**Legenda.** *Stato*: **nuovo** (non c'è) · **da rivedere** (c'è, ma ancorato alla pagina, statico o scritto per un'app sola) ·
**già in programma** (con la fase). *Sforzo*: piccolo (qualche giorno, un pacchetto) · medio (un paio di pacchetti) · grande
(un pezzo di motore o più pacchetti), con ciò da cui dipende. *Priorità*: alta / media / bassa, con il motivo.

---

## 1. In sintesi

- **23 idee**: 5 già in programma (1, 2, 13, 22, 23), 12 da rivedere, 6 nuove. 10 in priorità alta, 6 media, 5 bassa, 2 già
  decise dal piano. Le cinque scelte da Alessio sono tutte in alta.
- **Il filo comune che le app dimostrano.** Le app dell'utente costruiscono a mano sempre le stesse tre mosse: *un vetro che si
  estende in un pannello* (Transit `SearchGlass.kt`, Weather `LocationPill`, Fluidify `NowPlayingSheet.kt`), *un pannello che si
  riduce a capsula* (Transit `NavOverlay.kt`) e *un tasto che diventa un controllo* (Fluidify `KaraokeDial.kt`). Quasi tutte le
  idee ad alta priorità sono variazioni di queste mosse sul piano flottante di F3: per questo F6B è una fase di *preset*,
  non di motore, salvo l'idea 12 che è davvero motore.
- **Pampa Mail aspetta queste cose.** Cinque delle dieci alte sono scritte nel suo piano come comportamenti attesi
  (UX-INB-08 selezione, UX-INB-09 stato di sincronizzazione, UX-CMP-08 «Annulla invio», UX-SRC-03 chip, D-48 campo di ricerca in
  alto) e il compositore (`ComposePlaceholder.kt`) è ancora un segnaposto: è il momento in cui costano meno.
- **Le tre da fare per prime:** 7 (barra di selezione), 8 (tasti con esito) e 10 (avviso dal tasto): vedi §4. Più una da
  *cominciare* subito perché è la più lunga: 12 (elemento che diventa pagina).
- **Le idee che non erano fra gli spunti**: 5, 9, 14, 15, 16, 17, 18, 21, più il modo in cui 8 e 10 si compongono in un solo
  viaggio («Invia»).

---

## 2. La tabella

Le app: **Mail** = Pampa Mail, **Notes** = Pampa notes, **Gallery** = MyCloud Gallery, **Weather** = FluidWeather,
**Transit** = Fluid-transit. Percorsi dei file in §6. «R1», «R5» sono le ricerche del progetto.

| # | Nome | Cos'è e come si muove | Dove lo usano iOS e app note | App dell'utente (dove oggi è a mano) | Stato nell'engine | Sforzo (dipende da) | Priorità |
|---|---|---|---|---|---|---|---|
| 1 | **Campo dal basso** | Un tasto in fondo si allunga di lato in campo, sale con la tastiera, torna tasto | iOS 26: ricerca in basso, scheda Cerca che diventa campo (R1 R2, T5) | Mail (oggi la zona Cerca, `SearchZone.kt`); la «barra di scrittura» dell'assistente: ClasseViva (overlay dell'engine), Weather (`HomeFloatingBar.kt`), PampAI e Transit (overlay propri) | **già in programma** (F4B nota; tipo in F4A.1, comportamento F4B.2) | grande (piano F3, barra F4A) | **alta**: richiesta; il resto la riusa |
| 2 | **Campo dall'alto** | Si apre in cima: resta lì inglobando i tasti, oppure scende sopra la tastiera | iOS 27: campo fissato sotto la barra che si ritira (R5 V5); Safari lascia scegliere fra tre disposizioni | Mail (D-48; avatar e ☰ da inglobare); Transit (`SearchGlass.kt`: barra → pannello) | **già in programma** (F4B nota) | grande (come 1, più F4A.4) | **alta**: richiesta, caso di Mail |
| 3 | **Dentro il campo** (composer) | Righe che crescono, allegati, dettatura → invio → stop, chip rapidi, alone d'ascolto | Messaggi: «+», dettatura, invio | PampAI `ChatComposer.kt`, `ComposerMenus.kt`; gli overlay dell'assistente (ClasseViva usa quello dell'engine) | **da rivedere**: `FluidComposer` (engine-ai-ui) c'è con tutti gli slot, ma è la barra in fondo di una chat | medio (dipende da 1) | **alta**: è metà del campo |
| 4 | **Suggerimenti e filtri a pillola** | Sotto il campo: recenti, suggerimenti per pertinenza, pillole che diventano gettoni nel campo o menu | iOS: `searchScopes`, gettoni, suggerimenti (R1 R5) | Mail (UX-SRC-03); Transit (`SearchGlass.kt`: recenti e fermate vicine; `ChipLayoutTest.kt`: fila di filtri); Gallery (`SearchScreen.kt`) | **da rivedere**: `FluidChip`, `FluidTokenField` ci sono; gli ambiti in linea sono in F4B.2 | medio (1–2, F5A per chip → menu) | **alta**: scelta di Alessio, vale per ogni ricerca con suggerimenti |
| 5 | **«Trova nella pagina»** | Barra con contatore «3/12» e frecce sopra la tastiera, che non sostituisce la navigazione | Pattern comune dei lettori (non verificato qui) | Notes (`TranscriptSearchBar.kt`) | **da rivedere**: `FluidSearchField` con navigazione c'è | piccolo (dipende da 1) | media: modo del campo, quasi gratis dopo 1 |
| 6 | **Barra sopra la tastiera** | Capsula di vetro che sale con la tastiera e cambia contenuto col contesto | iOS: barra sopra la tastiera (`ToolbarItemPlacement.keyboard`, da verificare) | Mail (compositore, UX-CMP-04, da scrivere); Notes (cartella `ui/editor/`, non letta) | **da rivedere**: `FluidRichTextToolbar` è un solo preset fisso | medio (modello barre F4A, icone F3.4) | **alta**: scelta di Alessio |
| 7 | **Barra delle azioni di selezione** | Prende il posto della tab bar (o sta in alto): «N selezionate», azioni con priorità e «···» | Mail iOS 26 ha «Seleziona» (le fonti non descrivono la barra) | **a mano**: Notes (`FolderScreen.kt` + `Selection.kt`), Gallery (`GalleryScreen.kt`, `AlbumDetailScreen.kt`, `TrashScreen.kt`, `DuplicatesScreen.kt`), Mail (UX-INB-08) | **da rivedere**: capsula + stato di selezione + back ci sono; non sostituisce la tab bar | medio (F4B.1, F4B.3) | **alta**: scelta di Alessio; tre app la rifanno |
| 8 | **Tasti con stato di lavoro ed esito** | Famiglia: invia, salva, scarica, elimina, copia, preferito… lavora → esito animato col significato dell'icona, anche negativo | HIG Feedback; SF Symbols 7 (Draw, Magic Replace); M3 Expressive (indicatore di caricamento) | Mail (Invia, UX-CMP-08); lavori lunghi in Gallery (caricamento dalla fotocamera), Notes (trascrizione in cloud), Fluidify (scarico): come li mostrano non l'ho verificato | **nuovo** (esistono `FluidStepProgress`, indicatori; non sul tasto) | medio-grande (F3.4, F6.1, aptica) | **alta**: scelta di Alessio |
| 9 | **Tasto diviso** | Azione principale + freccia che apre il menu dallo stesso vetro | `Menu(primaryAction:)` (N5); split button di M3 Expressive | Mail («Invia ▾ Programma invio», UX-CMP-09) | **nuovo** (N5 «tocco = azione, pressione lunga = menu» c'è; il tasto visibile no) | piccolo (F5A) | media: nasce con 8, vale per ogni azione con varianti |
| 10 | **Avviso che nasce dal tasto** | Nasce dal tasto che l'ha causato, conto alla rovescia dell'Annulla, si aggiorna in esito, torna al tasto | iOS 26: avvisi e conferme escono dalla loro sorgente (P4, P5), non i banner | Mail (UX-CMP-08 «Annulla» 10 s; UX-INB-08 un solo Annulla) | **da rivedere**: `FluidNotificationHost` c'è, scende dall'alto; F5B.1 fa nascere dalla sorgente solo avviso e conferma | piccolo-medio (F5B.1) | **alta**: bisogno scritto, host già pronto |
| 11 | **Pillola di attività** | In cima o in banda sopra la tab bar: compatta ↔ estesa; sincronizzazione, caricamenti, registrazione, viaggio | Live Activities / Dynamic Island; Live Updates di Android 16 | Mail (UX-INB-09); Gallery (`SyncIndicator.kt`); Transit (`NavOverlay.kt`); Notes, Fluidify (lavori lunghi) | **nuovo** (la banda sopra la tab bar c'è) | medio-grande (F3, F4A/F4B) | **alta**: quattro app, nessuna lo fa con l'engine |
| 12 | **Elemento che diventa pagina** | Un tasto, riga, miniatura o accessorio diventa una pagina intera e vera; guidato dal dito, interrompibile | iOS: zoom da sorgente (V4, P3); mini player di Musica | Fluidify (`NowPlayingSheet.kt`), Gallery (miniatura → visore con `sharedElement`), Notes (Expand), Weather (pillola → pannello) | **da rivedere**: `FluidPageTransition.Expand` c'è; F5B.3 è lo zoom | grande (motore: F5B.3, F4A.6) | **alta**: scelta di Alessio, è la più lunga |
| 13 | **Tasto principale flottante** | In basso, si apre in azioni; l'etichetta si ritira scorrendo; mostra l'ultima azione usata | FAB menu e toolbar flottante di M3 Expressive | Mail («Scrivi», cerchio staccato, UX-NAV-01) | **già in programma** in gran parte (F5A tasto → menu; `standaloneTab` c'è) | piccolo (F5A) | media: poco da aggiungere |
| 14 | **Pillola del titolo che diventa selettore** | Titolo o pillola nella barra si espande in un pannello di scelta (località, account, cartella) | Menu del titolo di iOS (non verificato) | Weather (`LocationPill` → `LocationPane`); Mail (titolo = account, UX-INB-01); Notes (`FolderPickerSheet.kt`, scelta della cartella in un foglio) | **da rivedere**: popover in F5B.1, `fluidExpandOrigin` c'è | piccolo-medio (F5B.1) | media: tre app, pezzo di 12 |
| 15 | **Pannello persistente a detent** | Foglio non modale sopra mappa o lista, tre altezze, resta fra un dettaglio e l'altro | Fogli con detent (P1, P2) | Transit (`BottomPanelHost.kt`, `PanelState.kt`, `StopSheet.kt`); Weather (`HomeSummarySheet.kt`, un foglio); Gallery (`ExifBottomSheet.kt`, un foglio) | **da rivedere**: foglio modale c'è; F5B.2 dà i detent e `undimmedUpTo`; il persistente non è nominato | medio (F5B.2) | media: solo Transit lo vive davvero |
| 16 | **Fila di scelte che diventa menu** | Chip o segmentato che, quando manca spazio (testo grande), si raccolgono in un tasto-menu | Segmentato e picker di iOS | Transit (`ChipLayoutTest.kt`: layout a mano per scala 1,3 e 2,0) | **nuovo** | piccolo (F5A) | media: accessibilità, difetto già visto |
| 17 | **Barre che si nascondono su contenuto immersivo** | Un tocco sfuma barre e capsule; tornano al tocco o allo scorrimento | Foto e video a schermo intero | Fluidify (`FullScreenVideo.kt`, `showControls` con timeout); Gallery (`ViewerScreen.kt`) | **da rivedere**: `FluidChromeController` c'è (scorrimento) | piccolo | bassa: due app, a mano funziona |
| 18 | **Controllo che si espande dal tasto** | Tasto → capsula verticale (volume, livello), si richiude al rilascio | Controllo del volume e Centro di controllo | Fluidify (`KaraokeDial.kt`) | **nuovo** (cursore e scrubber ci sono; F6.3 li fa salire nel vetro) | piccolo-medio (F6.3) | bassa: una sola app |
| 19 | **Aggiornamento tirando, col vetro** | L'indicatore è una goccia di vetro che si stacca dalla barra mentre si tira | Aggiornamento a trascinamento | Mail (UX-INB-09) | **da rivedere**: `FluidScreen(isRefreshing, onRefresh)` c'è | piccolo | bassa: già c'è un tira-per-aggiornare |
| 20 | **Indicatore di posizione con anteprima** | Maniglia sul bordo con bolla (data, lettera, miniatura); etichetta di sezione fluttuante | Foto, Rubrica | Gallery (`TimelineHeader.kt`, nessun scorrimento rapido trovato); Mail (liste di decine di migliaia) | **da rivedere**: `FluidFastScroller`, `FluidSectionIndex` ci sono; manca l'anteprima | piccolo | bassa: soprattutto adozione |
| 21 | **Permesso nel punto in cui serve** | La spiegazione nasce dal tasto che ne ha bisogno, poi il dialogo di sistema, poi lo stato «negato» con «Apri impostazioni» | Principio generale (non verificato) | Permessi chiesti in onboarding e impostazioni: Weather (`OnboardingScreen.kt`), Fluidify (`MainActivity.kt`), Notes (`NotificationPermission.kt`) | **nuovo** | piccolo (F5B.1) | bassa: evidenza debole, file non letti a fondo |
| 22 | **Tab bar ↔ barra laterale** | La tab bar in alto sul tablet diventa barra laterale e ritorna | R1 T7 | (nessuna app lo fa) | **già in programma** (F4B.4) | — | già deciso; niente da aggiungere |
| 23 | **Popover e suggerimenti da un tasto** | Un pannello nasce dal tasto e ne prende il posto | R1 P6 | Weather (`LocationPill`) | **già in programma** (F5B.1); i callout del tutorial (`fluidTutorialAnchor`) ci sono | — | già deciso; si appoggia a 14 |

Scartate o già coperte, senza riga: la pillola «3 nuove ↑» (`FluidJumpPill` c'è), il riordino e le azioni a scorrimento
(c'è; F6.6), il menu contestuale con fila di simboli (F5A), la bolla di selezione della tab bar (F4B.3), la riga che si
espande sul posto (nessuna app lo costruisce a mano, che io abbia visto), il banner «immagini bloccate» di Mail
(`FluidInlineMessage` c'è: è della pagina, non un elemento dell'interfaccia).

---

## 3. Le schede (priorità alta)

Ogni scheda ha al più dieci righe, **preset** e **cosa resta configurabile** compresi. I preset sono una proposta di nomi e
casi, non un'API: i tipi li decide F6B (e F4A.1 per il campo, che deve nominarlo una volta sola).

### Scheda 1a · Il campo di inserimento, dal basso

- **Stati**: tasto (nella barra, o staccato accanto alla tab bar) · campo aperto · campo con la tastiera · campo con testo (compare l'invio) · di nuovo tasto.
- **Viaggi**: tasto → campo è una sola sagoma che si allunga verso il lato libero; i vicini si stringono o si raccolgono in un «…»; con più di due vicini il campo nasce già come tasto (R1 R2). Se il tasto sta in una tab bar, le altre schede collassano in un solo tasto (R1 T5). Campo → tasto è il viaggio inverso.
- **Con la tastiera**: il campo sale con l'altezza della tastiera a ogni fotogramma e la pagina cede lo spazio (`contentInset`). Nessun salto, nessuna attesa.
- **Sceglie chi lo usa**: dove sta a riposo; cosa fanno i vicini; se all'invio torna tasto (ricerca) o resta aperto (chat); se si chiude toccando fuori.
- **Rotazione e tablet**: testo e stato sopravvivono; il viaggio in corso riprende dalla geometria nuova. Sul tablet il campo ha una larghezza massima e sta dove dice `Automatic` (F4B.2: in alto a destra).
- **Back e tastiera**: il primo back chiude la tastiera, il secondo riporta a tasto; col back predittivo non cambia nulla finché non è confermato (regola 3 di F4A). Tastiera in ritardo: il campo parte subito e segue l'altezza che arriva; l'animazione piena della tastiera lato app c'è da Android 11 (ipotesi mia, da misurare in S4: sotto, il campo fa un breve viaggio a molla).
- **Testo lungo**: chiuso, una riga con ellissi; aperto, cresce fino al tetto (`FluidComposer.maxLines`, 6) e poi scorre dentro.
- **Esempio**: oggi il caso dal basso è la «barra di scrittura» dell'assistente (ClasseViva sull'overlay dell'engine; Weather, PampAI e Transit con overlay propri) e il cerchio di ricerca della tab bar; Pampa Mail ha la ricerca in alto (D-48) e «Scrivi» apre una pagina, quindi per Mail il dal-basso verrà con le risposte rapide, se le vorrà.
- **Preset**: Ricerca · Messaggio · Nota rapida · Domanda all'assistente · Due campi Da/A (Transit `PlannerGlass.kt`: «Da e A, uno sopra l'altro, al posto della barra di ricerca») · Trova nella pagina (riga 5).
- **Resta configurabile**: collocazione, vicini (si raccolgono / si ritirano), comportamento all'invio, pannello sotto/sopra (scheda 4), tasto a destra fisso o che cambia (invio, dettatura, stop), tetto di righe, chiusura fuori. In iOS 27 (beta) Messaggi lascia scegliere se il tasto a destra cambia: fonte secondaria, ma dice che il parametro serve.

### Scheda 1b · Il campo di inserimento, dall'alto

- **Stati**: riposo (titolo e tasti di navigazione) · campo aperto in cima (modo «resta») · campo sopra la tastiera (modo «viaggia») · chiuso.
- **Modo «resta in cima, ingloba i tasti»**: il tasto di ricerca si allarga e i tasti vicini si sfocano e rientrano nel campo, o si riducono a ciò che serve (default: domanda 5); il titolo grande si ritira; chiudendo, viaggio inverso e i tasti tornano. Precedente: in iOS 27 il campo resta fissato mentre titolo e tasti si ritirano (R5 V5, F4A.4).
- **Modo «viaggia sopra la tastiera»**: parte dalla cima e scende con la tastiera fino a posarsi sul suo bordo; i tasti di navigazione non restano vuoti: tornano subito al loro posto; chiudendo risale. Precedente: la ricerca iOS che appare sopra la tastiera, «in alto se in basso non c'è spazio» (R1 R3, R4).
- **Pannello**: sotto il campo nel modo «resta» (Transit `SearchGlass.kt` si estende «nello stesso pezzo di vetro»); nel modo «viaggia» cresce verso l'alto, fra il campo e la cima.
- **Sceglie chi lo usa**: il modo, per pagina (come ha chiesto Alessio) e, volendo, per persona nelle impostazioni: Safari di iOS 26 lascia scegliere fra tre disposizioni delle barre ([MacRumors](https://www.macrumors.com/2025/06/10/apple-ios-26-safari-design/)).
- **Rotazione e tablet**: in orizzontale su telefono la tastiera lascia poca altezza: «viaggia» diventa «resta» da solo. Su tablet i tasti ci stanno: il campo non li ingloba, si allarga a destra.
- **Back e tastiera in ritardo**: come 1a; in «viaggia», se la tastiera non arriva (tastiera fisica) il campo resta in cima e si comporta da «resta».
- **Testo lungo**: in «resta» una riga con scorrimento orizzontale; in «viaggia» cresce in altezza.
- **Esempio**: Pampa Mail, D-48: la ricerca diventa una barra di vetro in alto che lascia la pillola; avatar (UX-NAV-03) e ☰ (UX-NAV-04) sono i tasti da inglobare.
- **Preset e configurabile**: quelli di 1a, più il modo («resta» / «viaggia» / «deciso dal campo»), la regola per i tasti inglobati e quali restano (i `High` di F4A).

### Scheda 3 · Dentro il campo: il composer

- **Stato di partenza**: `FluidComposer` (engine-ai-ui) ha già `leading`, `chips`, `pills` (allegati), `voiceLine`, `glow`, `maxLines = 6`, e `fluidComposerAction` (microfono, invio, stop) è una funzione pura. Manca che viaggi: oggi è la barra in fondo di una chat.
- **Stati**: una riga · più righe · con allegati (vassoio sopra) · in ascolto (alone, riga di voce) · in invio · bloccato (l'assistente lavora).
- **Viaggi**: il tasto a destra cambia fra dettatura → invio → stop sulla stessa sagoma, con le icone che si incrociano sfocate (F3.4), non tre tasti; il vassoio degli allegati cresce verso l'alto spostando il bordo alto, non quello basso.
- **Vicini**: nella chat «+», chip del modello e invio fanno parte del campo; nel modo ricerca non ci sono.
- **Casi limite**: testo incollato lunghissimo (tetto e scorrimento interno, il bordo non sale oltre la tastiera); rotazione (lo stato si salva: `FluidComposerState.Saver` c'è); doppio invio (disabilitato mentre parte); allegati oltre il tetto (messaggio, non scomparsa).
- **Esempio**: PampAI (`ChatComposer.kt` ha allegati, riga di voce, chip del modello, e `ComposerMenus.kt` i menu: tutto a mano); ClasseViva passa dall'overlay dell'engine. Se Weather e Transit usino `FluidComposer` non l'ho verificato.
- **Raccomandazione**: non scrivere un secondo composer; il campo dinamico ne ospita il contenuto. Resta in `engine-ai-ui`, collegato al campo.
- **Preset**: Chat (+, modello, invio/dettatura/stop) · Ricerca (lente, cancella, chiudi, contatore) · Nota (testo e invio) · Con allegati.
- **Resta configurabile**: gli slot (`leading`, `chips`, `pills`, `voiceLine`), tetto di righe, azioni a destra, alone, permesso del microfono (riga 21).

### Scheda 4 · Suggerimenti e filtri a pillola

- **Stati**: campo col fuoco e vuoto (recenti, suggeriti, filtri rapidi) · digitando (suggerimenti in **una lista sola ordinata per pertinenza**, come in Transit) · filtro attivo (pillola piena con ✕) · risultati (i filtri restano sopra come rifiniture: Mail UX-SRC-03) · senza risultati.
- **Viaggi**: una pillola scelta passa dal pannello al campo come **gettone** (iOS ha i gettoni nella ricerca; `FluidTokenField` c'è) e il suo posto si richiude; una pillola con tanti valori si apre in **menu dal suo vetro** (periodo: 7 giorni · 30 giorni · altro: F5A); a poco spazio la fila si raccoglie in un tasto filtri con contatore (riga 16).
- **Chi lo usa**: Mail (account, Non lette, Con allegati, Speciali, periodo; e operatori `from:`, `has:attachment` che diventano gettoni); Transit (Tutti / Urbani / Extraurbani, recenti e fermate vicine); Gallery (`SearchScreen.kt`).
- **Casi limite**: testo grande (Transit `ChipLayoutTest.kt`: a scala 1,3 e 2,0 le etichette si leggevano «Tutt», «Urba»: la fila deve diventare menu); tablet (pannello sotto il campo largo, risultati accanto); back (il primo chiude la tastiera, il secondo svuota o chiude il campo); suggerimenti che arrivano dopo il primo carattere (mai un «nessun risultato» prematuro, come ha scoperto Transit).
- **Nel piano**: F4B.2 ha gli *ambiti in linea sulla riga del campo* (iOS 27); qui si aggiungono i filtri **del pannello** e la loro trasformazione.
- **Preset**: Ambiti (esclusivi, in linea) · Filtri a scelta multipla · Intervalli (periodo) · Gettoni (da:persona) · Recenti (con ✕ per riga) · Vicini / popolari.
- **Resta configurabile**: esclusivo o multiplo; dove stanno (riga del campo, sotto, nel pannello); se diventano gettoni; funzione di pertinenza (dell'app); tetto di righe; cosa fa il tocco su un suggerimento (compila, esegue, apre); quando sparisce il pannello.

### Scheda 6 · Barra sopra la tastiera

- **Stati**: nascosta (nessun campo col fuoco) · visibile (campo col fuoco: capsula in due gruppi, azioni a sinistra e principale a destra) · contestuale (cambia contenuto: selezione → formattazione, campo destinatari → suggerimenti) · ridotta al tondo «Aa» (c'è: `FluidRichTextFormatButton`).
- **Viaggi**: contenuto A → B nella stessa capsula, con i gruppi che cambiano larghezza e le icone che si incrociano (stesso meccanismo del cambio di contesto delle barre, F3.4 / F4A); barra ↔ «Aa»; barra → pannello che si apre verso l'alto (colore, stile).
- **Stato di partenza**: `FluidRichTextToolbar` è una `FluidFloatingCapsule` con `avoidKeyboard` che compare quando serve; in 3.1.2 la riga del cursore sta sopra la barra. È un solo preset scritto per una sola app: si generalizza.
- **Preset**: Formattazione (c'è) · Suggerimenti (destinatari, parole: `FluidTokenSuggestion`) · Azioni rapide (allegato, foto, dettatura, «Scrivi con Aria» di Mail UX-CMP-11) · **Navigazione fra campi** (precedente, successivo, fatto) · Cerca nel testo (3/12, riga 5) · Cronologia (annulla, ripeti).
- **Forma dichiarativa**: `fluidKeyboardBar { group { action(id, icona, etichetta) { … } }; flexibleSpacer(); primary(…) }`, con **lo stesso modello della barra in alto** (F4A.1: gruppi, priorità, trabocco, ruoli) più una chiave di contesto e una condizione di visibilità. Se non rispondi alla domanda 5, assumo questo.
- **Casi limite**: tastiera in ritardo (la barra segue l'altezza, non aspetta); tastiera fisica (non appare, o resta ancorata al fondo: da decidere); tablet con tastiera flottante o divisa (la barra si ancora al bordo basso della finestra, non alla tastiera); rotazione (il cursore resta sopra la barra, regola 3.1.2 estesa a ogni campo); back (chiude tastiera e barra insieme); schermo stretto (trabocco in «···»). La barra non può rifrangere la tastiera (è un'altra finestra): velo più pieno su quel bordo (rischio di F4B).
- **Esempio**: Mail compositore (UX-CMP-04 «formattazione essenziale»; il compositore è ancora `ComposePlaceholder.kt`, quindi nasce già sul componente giusto); Notes ha una cartella `ui/editor/` che non ho letto.
- **Resta configurabile**: preset o dichiarativa; compare con la tastiera o a richiesta; sostituisce la tab bar (`placement`); priorità e trabocco; cosa si chiede di sé al campo.
- **Fonte iOS**: la barra sopra la tastiera di SwiftUI (`ToolbarItemPlacement.keyboard`) esiste ma non l'ho potuta verificare nella documentazione in questa ricerca.

### Scheda 7 · Barra delle azioni di selezione

- **Dove la fanno a mano**: Notes, in `FolderScreen.kt`, scambia le azioni della barra in alto (Trascrivi, Sposta, Esporta, Elimina…) con un `SelectionState` **proprio** (`ui/common/Selection.kt`, «la stessa in ogni elenco») benché l'engine abbia `FluidSelectionState`; Gallery, in `GalleryScreen.kt`, scambia la barra con una `CenterAlignedTopAppBar` di Material con due azioni («Seleziona tutto», «Elimina»), e altre tre schermate (album, cestino, duplicati) usano la stessa selezione (`selectedIds`: la loro barra non l'ho letta); Mail la specifica in UX-INB-08 e non esiste ancora.
- **Stati**: assente · selezione attiva con 0 elementi (azioni spente) · con N (titolo «N selezionate», «Seleziona tutto», «Fine») · azione in corso (il tasto lavora: riga 8) · esito (avviso con **un solo** Annulla per tutte le azioni di massa: riga 10).
- **Viaggi**: tab bar → capsula delle azioni nella stessa superficie in basso (le schede escono sfocate, le azioni entrano sfocate: cambio di contesto, F3.4); capsula → tab bar, anche con il back; in alto il titolo diventa «N selezionate» e «···» diventa «Fine»; le righe cambiano solo lo spazio `leading` (il segno di selezione) così la lista non salta.
- **Posizioni**: al posto della tab bar (di serie) · sopra la tab bar · in alto, per le pagine senza tab bar (oggi Notes) · flottante senza barra (Gallery).
- **Casi limite**: tablet (la capsula sta nel pannello dell'elenco, non su tutta la finestra: Notes lo fa già); rotazione (la selezione sopravvive, `FluidSelectionState.saver` c'è); back (esce dalla selezione, non dalla pagina: `FluidSelectionBackHandler`; predittivo: i tasti non cambiano finché non è confermato); «Seleziona tutto» su liste paginate (lo stato lo sa); troppe azioni (trabocco «···» con priorità); migliaia di righe (il conto non le materializza).
- **Pezzi che ci sono**: `FluidFloatingCapsule` (con `ReplacingTabBar`/`AboveTabBar`), `FluidCapsuleAction`, `FluidSelectionState`, `FluidSelectionMark`. Manca la **trasformazione** (F4B.1 porta la capsula sul piano; qui si dichiara «sostituisce la tab bar» come viaggio).
- **Preset**: Posta (archivia, elimina, letta/non letta, sposta, ···) · Galleria (condividi, aggiungi ad album, elimina, ···) · File e note (sposta, esporta, elimina) · Solo elimina.
- **Resta configurabile**: azioni, priorità e trabocco, posizione, quali azioni valgono con N=1 o N≥1, distruttiva con conferma o con Annulla, titolo (N / N di M / dimensione), come si entra (pressione lunga, «Seleziona», due dita) e come si esce.
- **Esempio d'uso**: Mail (UX-INB-08) come prima app; poi Notes e Gallery abbandonano le loro versioni.

### Scheda 8 · Tasti con stato di lavoro ed esito

- **Stati**: riposo · al lavoro (indeterminato per attese brevi, con percentuale per le lunghe: M3 Expressive riserva il suo indicatore a forme alle attese sotto i cinque secondi) · riuscito · fallito · annullato · poi di nuovo a riposo, o l'esito resta.
- **Viaggi**: riposo → lavoro (l'icona si compatta o diventa anello nello **stesso vetro**, larghezza fissa); lavoro → esito (l'animazione del significato); esito → riposo; fallito → «Riprova». Il cambio d'icona usa il meccanismo di F3.4 (sfocatura incrociata o tratto che si disegna: SF Symbols 7 ha Draw e Magic Replace per questo).
- **Regole mie**: il tasto è disabilitato mentre lavora; un lavoro più corto di ≈300 ms non mostra «lavoro» ma mostra l'esito; l'esito è annunciato anche da TalkBack e dall'aptica dell'engine (una per momento); con movimento ridotto l'esito è un cambio d'icona senza moto. Apple: confermare le azioni importanti e dire soprattutto quando falliscono ([HIG Feedback](https://developer.apple.com/design/human-interface-guidelines/feedback)).
- **Preset (versione 1)**: **Invia** (lavora: l'aeroplanino «si carica»; riuscito: parte in diagonale con sfocatura di moto; fallito: torna indietro con una scossa e diventa «Riprova») · **Salva** (anello → la spunta si disegna; fallito: X) · **Scarica / Carica** (freccia → anello con %, pausa/riprendi; riuscito: la freccia cade nell'anello pieno → spunta) · **Elimina** (conferma a pressione lunga con anello che si riempie; riuscito: il contenuto esce; fallito: scossa, resta) · **Copia** (nessun lavoro: l'icona diventa spunta ≈1 s) · **Preferito** (ottimistico: si riempie subito con un piccolo rimbalzo; se il server rifiuta torna vuoto con scossa e avviso) · **Sincronizza** (frecce che ruotano → spunta; fallito: «!» e Riprova).
- **Resta configurabile**: icona di partenza e d'esito, etichetta (o solo icona), durata minima del lavoro, determinato o no, cosa fa il fallito (riprova / resta in errore / torna a riposo con avviso), quanto resta l'esito, tinta, aptica, variante a movimento ridotto.
- **Casi limite**: rotazione o ricomposizione durante il lavoro (lo stato sta nel modello dell'app, il tasto è puro); gesto indietro (la pagina si chiude, il lavoro no: l'esito passa all'avviso, riga 10); tablet (nessuna differenza); con etichetta, la larghezza è la più larga fra gli stati.
- **Viaggio composto «Invia»** (candidato a viaggio d'oro, riga 8 + riga 10): in Mail, toccando Invia il compositore si chiude e compare «Invio in corso… Annulla» per 10 s (UX-CMP-08). Qui l'aeroplanino parte e l'avviso **nasce da lui**, poi si aggiorna in «Inviata».
- **Esempio d'uso**: Mail (Invia), Gallery (caricamento dalla fotocamera), Notes (trascrizione in cloud, `CloudTranscriptCard.kt`), Fluidify (scarico).

### Scheda 10 · Avviso che nasce dal tasto

- **Stato di partenza**: `FluidNotificationHost` scende dalla cima (slide di un terzo, dissolvenza 150 ms, scala 0,97), ha azioni in linea, `replaceGroup`, `updateCurrent` (il conto alla rovescia dell'Annulla), `showForResult`. Non conosce la sorgente; F5B.1 fa nascere dalla sorgente solo avviso e conferma.
- **Stati**: assente · nasce (dal tasto: cede un pezzo di vetro e l'avviso si stacca) · a vista (testo, fino a due azioni in linea, conto alla rovescia sull'Annulla) · espanso (tirato giù: c'è) · esito (si aggiorna sul posto) · esce (torna al tasto se è ancora lì, altrimenti scivola via).
- **Viaggi**: tasto → avviso; avviso → tasto (Annulla: il contenuto torna dov'era; Mail «riapre il compositore con tutto intatto»); avviso A → avviso B (`replaceGroup`: la stessa sagoma si aggiorna).
- **Origine che non esiste più**: la pagina può essere già chiusa (il compositore): l'origine si registra **quando si pubblica**, e l'avviso nasce da quel punto anche se il tasto non c'è più.
- **Preset**: **Annulla** (azione già fatta, 5–30 s, conto alla rovescia; un solo Annulla per le azioni di massa) · **Esito** (riuscito / fallito, 2–3 s) · **Promemoria con azione** («Apri») · **Micro-conferma** (accanto al tasto, ≈1 s: «Copiato», senza card).
- **Casi limite**: rotazione (resta, è dell'host alla radice); tablet (in alto, larghezza massima); gesto indietro (non intercetta il back); tasto sopra la tastiera (l'avviso scende comunque dall'alto); movimento ridotto (dissolvenza); più avvisi (coda FIFO, c'è).
- **Esempio**: Mail UX-CMP-08 («Annulla» configurabile 5/10/20/30 s) e UX-INB-08 (un solo Annulla).
- **Resta configurabile**: durata, tono, sorgente (tasto o nessuna), posizione, azioni, gruppo di sostituzione, aptica.
- **Dipende da**: le sorgenti di F5A.1 e il lavoro avvisi di F5B.1; l'host c'è.

### Scheda 11 · Pillola di attività

- **Stati**: assente · compatta (icona o anello + una frase breve + tempo o %) · minima (solo anello, con più attività o a barra ritirata) · estesa (card con dettagli e azioni: pausa, annulla, apri) · esito (pulsa e svanisce, o diventa avviso/tasto con esito: righe 8 e 10).
- **Viaggi**: compatta ↔ estesa è una sola sagoma, al tocco o trascinando (Transit `NavOverlay.kt`: «Si riduce a capsula per guardare la mappa, e torna su con un tocco o con una trascinata»); due attività: la seconda si divide dalla prima e si fondono quando una finisce (C5 del piano); l'esito si trasforma nell'elemento successivo.
- **Posizioni**: in cima sotto la barra di stato · in banda sopra la tab bar (è l'accessorio T4: `expandedAccessory`) · dentro la barra. Non è modale e non ruba il back.
- **Modello**: l'app pubblica un'attività (`id`, tipo, avanzamento, frase, azioni); l'engine la mostra. Il ponte verso la notifica di sistema (Live Updates di Android 16, chip nella barra di stato, `Notification.ProgressStyle`) è dell'app: il modello è lo stesso.
- **Preset**: **Sincronizzazione** (in corso, offline «le azioni verranno inviate appena torni in rete», errore con «Riprova», account da ricollegare: Mail UX-INB-09) · **Trasferimento** (carica/scarica con %, pausa, annulla) · **Registrazione** (rec, tempo, livello audio) · **Lavoro dell'assistente** (elabora…) · **Viaggio o timer** (prossimo passo, minuti).
- **Casi limite**: rotazione e tablet (centrata, larghezza massima); ritaglio o isola nello schermo (inset); testo lungo (ellissi, mai due righe in compatta); molte attività (due visibili e un contatore); tastiera (in cima non interferisce).
- **Esempio**: Mail (indicatore «discreto nella barra, non un banner»); Gallery (`SyncIndicator.kt`: icone di nuvola in dissolvenza); Transit (`NavOverlay.kt`). Apple: compatta, minima ed estesa devono «transitare in modo prevedibile» ([HIG Live Activities](https://developer.apple.com/design/human-interface-guidelines/live-activities)).
- **Resta configurabile**: posizione, priorità fra attività (una sola estesa), durata dell'esito, azioni dell'estesa, riducibile o no, cosa apre al tocco (una pagina: riga 12).
- **Sforzo**: nuovo; la parte difficile è la coda e le priorità, non l'animazione.

### Scheda 12 · Elemento che diventa pagina

- **Quello che c'è**: `FluidPageTransition.Expand` fa crescere la pagina dal punto toccato (scala 0,91 → 1, opaca; ritorno come `ShrinkSlide`); `fluidExpandOrigin` dà la sorgente ai modali; F5B.3 porta la sorgente dichiarata (`fluidTransitionSource`, F5A.1) per fogli e pagine. Difetto già scritto in F4A: con `Expand` il push interrotto non è continuo.
- **Cosa aggiungerei a F5B.3** (cinque cose, nell'ordine): **(1) la sagoma è il viaggio**: una superficie sola che va dalla forma della sorgente a quella della pagina (angoli del telefono), non una pagina scalata; **(2) il dito guida**: il progresso lo scrive il gesto (trascinare su apre, giù chiude) ed è interrompibile e invertibile in ogni punto, con il protocollo dei viaggi d'oro; **(3) la destinazione è una rotta vera** (back stack, deep link, ripristino), non un foglio: il commento di Fluidify dice che come destinazione di navigazione poteva fare solo una dissolvenza e che come trasformazione erano ancora due oggetti sulla scena; **(4) tre tratti disgiunti**: la faccia della sorgente esce, un **elemento condiviso** (copertina, miniatura) riempie, la chrome d'arrivo sale per ultima; **(5) sorgente sostituibile**: se è uscita dalla lista, ripiego senza salto (F5B lo prevede).
- **Stati**: riposo · in viaggio (0..1, col dito o con la molla) · pagina aperta · in chiusura (anche da gesto indietro).
- **Viaggi**: elemento → pagina e ritorno, interrotti a metà in entrambi i versi; ritorno verso una sorgente che nel frattempo è scorsa o è stata riciclata.
- **Preset**: **Zoom** (miniatura o card → pagina, con elemento condiviso: griglia → visore della Galleria) · **Sale** (accessorio in basso → pagina a tutta altezza, guidato dal dito: il mini lettore come un caso) · **Cresce** (tasto o pillola di vetro → pagina o pannello: la pillola della località di Weather) · **Dalla barra** (tasto della barra → pagina: «Scrivi» → compositore di Mail).
- **Casi limite**: tablet (la destinazione è un pannello: cresce fino ai suoi bordi, `FluidPaneScaffold`); rotazione durante il viaggio (riparte dalla geometria nuova, stato intatto); back predittivo (progresso dalla posizione vera del dito, regola 3); tastiera (la pagina d'arrivo la apre a viaggio finito).
- **Resta configurabile**: sorgente e forma, elemento condiviso (slot), se guida il dito e da quale lato, raggio d'arrivo (telefono o pannello), quali contenuti sono «faccia» e quali «chrome», rotta registrata o no, ripiego se la sorgente manca.
- **Esempio**: Fluidify `NowPlayingSheet.kt` (357 righe, i tre tratti scritti a mano; non usa il contratto `Incoming` perché il suo zoom limitato lasciava due strisce di vetro ai lati: lo dice il suo commento); Gallery (`sharedElement` a mano in tre file); Notes (`Expand` nel grafo).
- **Sforzo e ordine**: grande, è motore (F5B.3 più F4A.6); la più lunga della fase, da cominciare per prima anche se si chiude per ultima.

---

## 4. Le tre da fare per prime

Dentro F6B, **7, 8 e 10**, in quest'ordine, e **12 da cominciare subito** perché è la più lunga.

1. **7 · Barra delle azioni di selezione.** Il bisogno è provato tre volte (Notes, Gallery, Mail) e le app la fanno a mano (Alessio: «fatta da loro e maluccio»): Notes ha uno stato di selezione proprio benché l'engine ne abbia uno. I pezzi ci sono (capsula, stato, back); resta la trasformazione «sostituisce la tab bar», parente del viaggio d'oro (g) della tab bar e del cambio di contesto di F3.4. Sforzo medio, rischio basso, e Mail la attende.
2. **8 · Tasti con stato di lavoro ed esito.** È la più gradita ad Alessio e la più riusabile (ogni app ha un «Invia», un «Salva», un «Scarica»). Si parte da tre preset (invia, salva, copia) perché provano le tre forme: lavoro lungo, lavoro breve, nessun lavoro.
3. **10 · Avviso che nasce dal tasto.** Piccolo, perché l'host c'è. Si compone con 8 nel viaggio «Invia → Inviata → Annulla», che è l'unico viaggio d'oro nuovo che propongo. Insieme costano poco più di uno dei due.

Le idee 3 e 4 non sono in questo ordine perché si fanno **con il campo** (F4B.2): sono il contenuto e il pannello del campo. La 6 usa lo stesso modello di barra di F4A.1 e conviene subito dopo le prime tre. La 11 (pillola di attività) è la quarta per bisogno ma richiede coda e priorità: dopo.

**Dove stanno nel piano.** F6B va dopo F5B (usa le sorgenti di F5A.1 e il lavoro avvisi di F5B.1). Le idee 1 e 2 restano in F4B; 3, 4 e 6 si appoggiano a F4B. L'idea 12 estende F5B.3 invece di aggiungersi: conviene dire in F5B.3 che l'uscita è «una sagoma e un dito», così F6B non rifà lo zoom.

---

## 5. Cinque domande per Alessio

**1. In che ordine dentro F6B?**
- A. Barra di selezione → tasti con esito → avviso dal tasto; elemento che diventa pagina in parallelo *(consigliato)*
- B. Per bisogno di Pampa Mail: campo in alto → selezione → avviso con Annulla → sincronizzazione
- C. Elemento che diventa pagina per primo (è la più lunga), poi A
- D. Un altro ordine

**2. Oltre alle cinque scelte, quali altre idee entrano in F6B?** (più risposte)
- A. Pillola di attività (11)
- B. Avviso che nasce dal tasto (10)
- C. Tasto diviso (9) e tasto principale flottante (13)
- D. Pillola del titolo (14) e pannello persistente a detent (15)
- E. Fila di scelte che diventa menu (16)
- F. Nessuna oltre le cinque: le altre restano in R8 per dopo

**3. Quali preset dei tasti con esito nella prima versione?**
- A. Invia, salva, copia
- B. A, più scarica/carica e preferito *(consigliato)*
- C. Tutti e sette (anche elimina con pressione lunga e sincronizza)
- D. Solo «Invia», poi si guarda

**4. Quanto lontano porta F5B.3 «un elemento che diventa pagina»?**
- A. Migliorare `Expand` di serie: sagoma continua e interrompibile; il resto lo fa l'app
- B. A, più il dito che guida apertura e chiusura e l'elemento condiviso
- C. B, più la pagina d'arrivo sempre rotta vera; Fluidify e Gallery migrano dal loro codice *(consigliato)*
- D. Solo documentare come farlo a mano

**5. Chi decide se il campo dall'alto resta in cima o sale sopra la tastiera, e cosa fanno i tasti inglobati?**
- A. L'app, per pagina, con un parametro; i tasti si sfocano e rientrano nel campo e tornano alla chiusura
- B. Il campo decide da solo (in cima se la pagina ha tasti di navigazione, sopra la tastiera altrimenti), con parametro per forzarlo; restano solo i tasti `High`
- C. La persona, dalle impostazioni d'aspetto (come Safari), con A come valore di serie
- D. Altro

---

## 6. Fonti

**Web** (solo ciò che è servito; le fonti secondarie sono segnate):
- Apple, [HIG · Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars) · [HIG · Feedback](https://developer.apple.com/design/human-interface-guidelines/feedback) · [HIG · Progress indicators](https://developer.apple.com/design/human-interface-guidelines/progress-indicators) · [HIG · Live Activities](https://developer.apple.com/design/human-interface-guidelines/live-activities)
- WWDC25: [284 · Build a UIKit app with the new design](https://developer.apple.com/videos/play/wwdc2025/284/) (accessorio in basso) · [337 · What's new in SF Symbols 7](https://developer.apple.com/videos/play/wwdc2025/337/) (Draw, Magic Replace)
- Safari di iOS 26, tre disposizioni: [MacRumors](https://www.macrumors.com/2025/06/10/apple-ios-26-safari-design/) (secondaria)
- Tasto a destra del campo in iOS 27 beta: [Gear Patrol](https://www.gearpatrol.com/tech/iphone-ios-27-messages-app-send-button-fix/) (secondaria, beta, può essere cambiata)
- Mail iOS 26, «Seleziona» reinserito: [iDownloadBlog](https://www.idownloadblog.com/2025/08/06/apple-ios-26-mail-app-select-button-restored/) (secondaria)
- Android 16 Live Updates: [documentazione Android](https://developer.android.com/develop/ui/views/notifications/live-update) · [9to5Google](https://9to5google.com/2025/07/03/google-details-android-16-live-updates/)
- Material 3 Expressive: [toolbar, FAB menu](https://9to5google.com/2025/05/18/material-3-expressive-toolbars/) (secondaria) · [`LoadingIndicator`](https://composables.com/jetpack-compose/androidx.compose.material3/material3/components/LoadingIndicator/api.md) (secondaria)
- SF Symbols, animazioni di disegno: [Create with Swift](https://www.createwithswift.com/implementing-draw-animations-for-sf-symbols-in-swiftui/) (secondaria; le firme vanno confrontate con Apple)

**Engine e piano** (`C:\Users\casua\.t3\worktrees\fluid-engine\t3-8663befb\`): `docs/liquid-glass/piano.md` (F1B «viaggi d'oro», F3, F4A, F4B, F5A, F5B, F6, §7); `docs/liquid-glass/ricerca/inventario-ios.md` (R1: R1–R5, T4, T5, T7, N5, P1–P6, V4); `ricerca/ios27-differenze.md` (R5); `docs/liquid-glass/copertura-inventario.md`; `skill/fluid-engine/SKILL.md`; `references/componenti/barre.md`, `notifiche.md`, `menu-modali.md`, `pagine.md`, `schermata.md`, `tutorial.md`; `references/moduli/richtext.md`, `ai-ui-chat.md`, `ai-ui-overlay.md`; `references/api/barre.md`, `ai-ui.md`, `contenuto.md`, `pagine.md`, `richtext.md`.

**App dell'utente** (`C:\VibeCoded Projects\…`, sola lettura, a campione):
- **Pampa Mail**: `PIANO.md` (D-48; UX-NAV-01, -03, -04; UX-INB-01, -08, -09; UX-SRC-03; UX-CMP-04, -08, -09, -11); `app/src/main/java/dev/pampa/mail/ui/zones/SearchZone.kt`, `ComposePlaceholder.kt`.
- **Pampa notes** (`app/src/main/kotlin/dev/pampa/pampanotes/`): `ui/folder/FolderScreen.kt`, `ui/common/Selection.kt`, `ui/session/PlayerBar.kt`, `ui/session/TranscriptSearchBar.kt`, `ui/session/CloudTranscriptCard.kt`, `ui/nav/PampaGraph.kt`, `ui/common/FolderPickerSheet.kt`, `work/NotificationPermission.kt`.
- **Fluidify** (`app/src/main/java/dev/lelonio/square/ui/player/`): `NowPlayingSheet.kt`, `MiniPlayer.kt`, `FloatingMiniPlayer.kt`, `KaraokeDial.kt`, `PlaybackScrubber.kt`, `FullScreenVideo.kt`; `ui/MainActivity.kt`.
- **PampAI** (`feature-assistant/src/main/kotlin/dev/pampa/pampai/feature/assistant/`): `chat/ChatComposer.kt`, `chat/AssistantComposer.kt`, `chat/ComposerMenus.kt`, `session/SessionOverlay.kt`.
- **FluidWeather**: `feature-home/…/HomeFloatingBar.kt`, `HomeSummarySheet.kt`; `feature-settings/…/OnboardingScreen.kt`, `NotificationsSettingsScreen.kt`.
- **Fluid-transit** (`app/src/main/kotlin/dev/antigravity/fluidtransit/ui/`): `map/SearchGlass.kt`, `map/PlannerGlass.kt`, `map/BottomPanelHost.kt`, `map/PanelState.kt`, `map/StopSheet.kt`, `nav/NavOverlay.kt`; test `map/ChipLayoutTest.kt`, `map/PlaceKeyboardLiftTest.kt`.
- **MyCloud-Gallery** (`app/src/main/java/com/mycloudgallery/presentation/`): `gallery/GalleryScreen.kt`, `album/AlbumDetailScreen.kt`, `trash/TrashScreen.kt`, `duplicates/DuplicatesScreen.kt`, `shared/SyncIndicator.kt`, `search/SearchScreen.kt`, `gallery/TimelineHeader.kt`, `gallery/components/MediaThumbnail.kt`, `viewer/ViewerScreen.kt`, `viewer/components/ZoomableImage.kt`, `viewer/components/ExifBottomSheet.kt`, `navigation/AppNavigation.kt`.
- **ClasseViva Expressive**: `android/feature/feature-assistant/…/overlay/AssistantOverlay.kt` (l'overlay dell'engine). **Pampa sounds**: a campione non ho trovato elementi dinamici scritti a mano.
