# «iOS in Android»: copertura dell'inventario

Una riga per ogni voce di [ricerca/inventario-ios.md](ricerca/inventario-ios.md) (R1). Serve a un controllo solo: che niente
resti indietro senza che qualcuno l'abbia deciso. Fasi e pacchetti sono quelli di [piano.md](piano.md), sezione 6; i
riferimenti «R2 §» sono all'[audit](ricerca/audit-engine.md).

**Esito**: **si fa** = nasce o cambia in modo sostanziale; **c'è** = l'engine 3.1.2 lo fa già (prende il materiale nuovo e,
dove indicato, un ritocco); **no** = non si fa in questa tornata, con il motivo. Le note citano **R5**
([ricerca/ios27-differenze.md](ricerca/ios27-differenze.md), iOS 27) dove R5 misura o documenta, e dicono «a occhio» dove
non misura: lì si decide con confronti in chat. **R6** è [ricerca/icone-e-misure.md](ricerca/icone-e-misure.md), icone e
taglie. Due decisioni di Alessio attraversano la tabella: il **materiale** è quello di iOS 27, il **bordo dello
scorrimento** ha l'aspetto di iOS 26.

Le voci numerate nella tabella riassuntiva di R1 sono 94 (R1 e lo stato dell'incarico dicono 95: il conto torna contando la
riga senza numero in fondo alla famiglia 3.15, riportata qui per ultima).

## Il materiale

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| M1 | Vetro su una vista | alta | si fa | F1A (materiale), F3.2 (`Modifier.fluidGlass`) | oggi è `glassSurface` (R2 §1.1): resta, con il materiale nuovo sotto. Smerigliatura di serie di iOS 27: resta il 6–13% del contrasto (R5 M1), circa metà di iOS 26 |
| M2 | Varianti regular, clear, identity | alta | si fa | F2.2 (`FluidGlass`) | `Clear` con oscuramento del 35% sotto (documentazione invariata in iOS 27: R5) |
| M3 | Lente e materializzazione | alta | si fa | F1A.2 (lente), F3.3 (comparsa per lente) | la lente c'è (R2 §1.3); la comparsa oggi è intensità che scala anche la sfocatura. Forza della lente: non misurata né da R4 né da R5, a occhio |
| M4 | Luce (riflessi) | alta | si fa | F1A.3 (filo scuro sul perimetro e riflesso interno in alto e in basso: R5 M1, M4), F6.5 (luce che segue il movimento) | oggi un filo simmetrico, nero sul tema chiaro (R2 §1.6). I due archi di iOS 26 restano come variante delle tavole. La luce dal movimento è **spenta di serie** (decisione): la accende l'app o l'utente dalle impostazioni d'aspetto |
| M5 | Ombra adattiva | alta | si fa | F1A.4 (due componenti), F2.2 (più forte sul testo) | oggi un'ombra fissa, 3,6–12,8% (R2 §1.6). iOS 27: 2–4% al bordo, nulla a 0,2–0,25 H (R5, alta su fondo piatto); sopra il testo non è misurata: a occhio |
| M6 | Chiaro/scuro adattivo | alta | si fa | F2.1, F2.2, F2.3; F3.2 (un contenitore gira tutto insieme) | oggi niente legge lo sfondo (R2 §7). Tempi di R4 B6 (non rimisurati in R5); soglia in iOS 27 incerta (R5 M3): a occhio, in S3 |
| M7 | Spessore con la taglia | alta | si fa | F1A.5 | c'è per sfocatura e lente (`glassSizedOptics`, R2 §1.3); si estende a ombra e smerigliatura, non al filo |
| M8 | Tinta | alta | si fa | F2.2 (`GlassTintMode.Colored`), F4A.2 (azione principale), F6.4 (tasto prominente) | oggi pellicola fissa col 20% d'accento sui soli controlli |
| M9 | Luce del contenuto sui grandi | bassa | si fa | F4B.4 | velo e ombra prendono il colore medio della sonda |
| M10 | Contenuto sul vetro, niente vetro su vetro | alta | si fa | F2.3 (inchiostro adattivo), F8.1 (regola d'uso nelle schede) | per decisione il vetro su vetro e sul contenuto resta disponibile, non consigliato |
| M11 | Accessibilità e preferenza dell'utente | media | si fa | F6.7 (trasparenza, contrasto, movimento), F2.2 (cursore dell'utente) | contrasto di sistema e movimento ridotto ci sono (R2 §7). In iOS 27 la scelta a due è un **cursore** continuo, di serie a metà, in Impostazioni › Aspetto › Liquid Glass (R5 corregge R1, che lo metteva in Schermo): `LocalFluidGlassPreference` e una riga facoltativa in `fluidAppearanceSection`. Estremi da R5 M2, fiducia bassa |
| M12 | Stato inattivo | bassa | si fa, al minimo | F6.7 | il vetro recede solo in multi-finestra. In iOS 27 icone e testo si attenuano anche su iPad (R5, detto da Apple, non misurato): stessa attenuazione, a occhio |

## Contenitori e trasformazioni

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| C1 | Contenitore del vetro | alta | si fa | F3.1, F3.2 | generalizza il registro dei pezzi della barra ancorata (R2 §4.3) |
| C2 | Identità e morph | alta | si fa | F3.1, F3.2 | |
| C3 | Transizioni del vetro | alta | si fa | F3.2, F3.3 | `MatchedGeometry`, `Materialize`, `Identity` |
| C4 | Unione | media | si fa | F3.2 | |
| C5 | Dividere e fondere | alta | si fa | F3.3 | il campo fuso c'è in Fluid-physics (≤6 lastre, R2 §2.1): passa a 8, col collo che si strozza |
| C6 | Barra che cambia contesto | alta | si fa | F4A.2 (stessa pagina), F4A.3 (fra pagine) | oggi solo fra due pagine, solo capsule, icone in dissolvenza (R2 §4.3, §4.5) |

## Barre degli strumenti e di navigazione

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| B1 | Barra flottante e gruppi | alta | si fa | F4A.1, F4A.2 | oggi le azioni sono composable liberi senza modello (R2 §4.5) |
| B2 | Spaziatori | alta | si fa | F4A.1 | |
| B3 | Sfondo condiviso | media | si fa | F4A.1 | parametro `sharedBackground` |
| B4 | Azione principale | alta | si fa | F4A.2 | spunta tinta, da sola, a destra |
| B5 | Tinta degli elementi | media | si fa | F1A.11 (icone del set `FluidIcons` a 26 dp, inchiostro monocromo del tema), F2.3 (inchiostro adattivo), F4A.2 | oggi le icone sono del colore d'accento e a 22 dp. Deciso da Alessio: monocrome, da un set al tratto a peso 500 (R6); l'accento resta all'azione principale |
| B6 | Badge | media | si fa | F4A.2 | `FluidTabBadge` c'è per le schede: si riusa |
| B7 | Titolo, sottotitolo, titolo grande | alta | c'è | F4A.2 (modo del titolo) | titolo grande che scorre col contenuto e si aggancia, sottotitolo, fatti a rotazione (R2 §4.1) |
| B8 | Posizioni semantiche e menu «Altro» | alta | si fa | F4A.1, F4B.1 (in basso) | |
| B9 | Tasto indietro e Chiudi | alta | si fa | F1A.11 (freccia a 24 dp, margine a 16 dp), F4A.2 | chevron e X in un tondo di vetro; oggi il tasto indietro è unico per host |
| B10 | Simboli invece di testo | alta | si fa | F4A.1 (il modello non mette testo e simboli nello stesso gruppo), F8.1 | |
| B11 | Barre a spazio limitato (27) | media | si fa | F4A.1 | priorità `Low` / `Automatic` / `High` (escono prima i più bassi), `overflow { }` sempre nel menu, `PinnedTrailing` che resta a destra finché la ricerca non è attiva (R5, documentato). Le priorità relative (`lowerThan`, `higherThan`) non si fanno: bastano i tre livelli e l'ordine di dichiarazione |

## Tab bar

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| T1 | Tab bar flottante | alta | c'è | F1B.4 (materiale e misure) | `FluidFloatingTabBar` (R2 §4.4). Misure di R6: alta 62 dp, icone da 28 dp a inchiostro pieno anche se non scelte, etichetta da 11 sp; margini invariati |
| T2 | Bolla di selezione | alta | si fa | F4B.3 | il puck con lente c'è (R2 §2.2); si rifinisce il trascinamento e il contenuto deformato |
| T3 | Minimizzazione | media | c'è | F4B.3 (fusione col campo) | stati `INLINE`/`EXPANDED`, `inlineBehavior` |
| T4 | Accessorio in basso | media | c'è | F4B.3 | slot `inlineAccessory`, `expandedAccessory` |
| T5 | Scheda di ricerca | alta | c'è | F4B.3 (ruolo, attivazione) | `standaloneTab`, `searchMode`, `searchBarContent` |
| T6 | Scheda prominente (27) | media | si fa | F4B.3 | `FluidTabRole.Prominent`: tondo di diametro H a 0,11–0,12 H dalla capsula, solo glifo, stesso materiale; una sola, una scheda qualsiasi; se manca, il trattamento va alla ricerca; resta a barra ripiegata (R5 T6, alta). Scelta e transizione non mostrate: a occhio |
| T7 | Tablet: barra in alto e barra laterale | media | si fa | F4B.4 | `FluidTabRail` e `FluidSidebar` ci sono; nascono la scelta automatica dalla larghezza, la **tab bar in alto** sul tablet e il tasto che la converte in barra laterale e ritorno (voce di S4). Barra laterale su telefono (27): `FluidSidebar` chiamata a mano, c'è. **Divergenza scelta**: in iOS 27 le barre laterali di iPad arrivano ai bordi e hanno icone d'accento (R5); qui restano flottanti e monocrome |

## Ricerca

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| R1 | Ricerca automatica | alta | si fa | F4A.1 (tipo), F4A.2 (parametro e scope), F4B.2 (comportamento) | `FluidSearchSpec`, posizione `Automatic` |
| R2 | Ricerca nella barra in basso | alta | si fa | F4B.2 | larghezza che si adatta ai vicini; R5 non trova differenze dichiarate e non la misura: a occhio |
| R3 | Ricerca minimizzata | alta | si fa | F4B.2 | `FluidSearchField` passa già da cerchio a campo, ma senza fondersi coi vicini e ricatturando a ogni fotogramma (R2 §4.4) |
| R4 | Ricerca in alto o in linea | media | si fa | F4B.2 | |
| R5 | Anatomia del campo | media | si fa | F4B.2 | lente, cancella, chiudi e navigazione fra i risultati ci sono; ambiti e suggerimenti nuovi, con gli ambiti **in linea** sulla riga del campo (iOS 27, documentato); token: `FluidTokenField` |

## Menu

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| N1 | Menu dal suo tasto | alta | si fa | F5A.2 | il viaggio c'è (R2 §5); mancano le voci dentro la lente. La chiusura (Apple ≈200 ms, engine ≈0,5 s per una preferenza registrata) è una scelta di gusto. In iOS 27 nessun cambio rilevabile (R5 B11) |
| N2 | Tasto della barra → menu | alta | si fa | F5A.2 | è il gruppo intero a trasformarsi; chiede F4A |
| N3 | Struttura del menu | alta | si fa | F5A.3, F5A.2 (icone delle righe a 22 dp: R6) | sezioni e voce scelta ci sono; nascono sottomenu, sottotitolo, fila di simboli. Icone nascoste di serie (27): riguarda la barra dei menu di iPad e Mac, su iPhone nessun cambio (R5): non si fa |
| N4 | Ordine e chiusura | media | si fa | F5A.3 | `FluidMenuOrder`, `keepsMenuOpen` |
| N5 | Azione principale del menu | media | c'è | — | `FluidBarAction(actions, opensOn)`, `FluidMorphMenuButton(actions, onClick)` |
| N6 | Selezione trascinando il dito | media | c'è | — | `FluidMenuHandOff`, `ref/regole.md:570` |
| N7 | Menu di modifica del testo | media | no | — | è la barra di selezione del testo della piattaforma; rifarla di vetro vuol dire sostituire `LocalTextToolbar` e gestire selezione e tastiera: incarico a parte |

## Menu contestuali

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| X1 | Menu contestuale | alta | si fa | F5A.4 | sollevamento e menu accanto ci sono (R2 §5); nasce `Modifier.fluidContextMenu` |
| X2 | Sfondo del menu contestuale | alta | si fa | F5A.4 | decisione di Alessio: scurito con una sfocatura leggera (diverge da iOS 26, che scurisce soltanto) |
| X3 | Anteprima personalizzata | media | si fa | F5A.4 | |
| X4 | Regole del contenuto | media | c'è | F5A.3 (ordine), F8.1 | distruttive in rosso e sezioni ci sono; il resto è regola d'uso nelle schede |

## Fogli, avvisi, conferme, popover

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| P1 | Foglio di vetro | alta | si fa | F5B.2 | oggi il foglio dell'host è una lastra unica; fuori dall'host è dipinto. In più il foglio in dissolvenza di iOS 27 (documentato, mai mostrato: a occhio) |
| P2 | Detent e maniglia | alta | si fa | F5B.2 | `FluidSheetDetent` |
| P3 | Foglio che nasce dal tasto | alta | si fa | F5B.3 | |
| P4 | Avviso | alta | si fa | F5B.1 | oggi entra in dissolvenza (R2 §5). Le varianti di iOS 27 sono legate a un **dato** (`item:`), non a un elemento visivo (R5 corregge R1): sovraccarico `FluidAlert(item: T?, …)`, nessun cambio d'aspetto |
| P5 | Dialogo di conferma dalla sorgente | alta | si fa | F5B.1 | `FluidConfirmationDialog`; `FluidConfirmSheet(origin)` resta; sovraccarico con `item: T?` come per P4 |
| P6 | Popover | media | si fa | F5B.1 | sostituisce il tasto finché è aperto |
| P7 | Modalità e oscuramento | alta | si fa | F5B.1 | `undimmedUpTo` |

## Controlli

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| K1 | Tasti di vetro | alta | si fa | F6.4 | `FluidGlassButtonStyle` su `FluidGlassButton` e `FluidGlassIconButton` |
| K2 | Forma e taglie dei controlli | alta | c'è | F1A.11 (icona delle azioni a 26 dp), F6.4 (larghezza flessibile, taglia d'ambiente, `iconSize` a 0,6 del vetro, valori di serie dentro fogli e popover: iOS 27) | capsula di serie e `FluidGlassSize` ci sono; vetro 44 dp e bersaglio 48 dp invariati (R6: Apple 44,0 pt). La taglia extra large non si aggiunge (contraddizione aperta in R1, e un valore nuovo nell'enum rompe i `when` delle app) |
| K3 | Interruttore | alta | si fa | F1B.3 (materiale), F6.3 (tempi contro R4 B5) | il pomello che diventa lente c'è |
| K4 | Cursore | alta | si fa | F6.3 | la lente c'è; nascono inerzia, tacche, valore neutro, senza pomello |
| K5 | Controllo segmentato | alta | si fa | F1B.3, F6.3 | la pillola che diventa lente c'è |
| K6 | Stepper, selettori, data | media | si fa | F6.6 (stepper), F5A (selettore a menu) | lo stepper non c'è; la data è `FluidDateTimeSheet` |
| K7 | Campi di testo | alta | c'è | F6.6 (metriche) | `FluidTextField` nel contenuto, `FluidSearchField` di vetro |

## Liste, moduli e contenuto

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| L1 | Contenuto senza vetro | alta | c'è, per scelta diversa | F8.1 | decisione: il vetro sul contenuto (card, gruppi) resta disponibile, non più consigliato; le app esistenti non cambiano da sole |
| L2 | Liste e moduli | alta | si fa | F6.6 | righe, raggi e margini delle sezioni; le misure non sono documentate: proporzioni dai frame |
| L3 | Azioni a scorrimento | alta | c'è | F6.6 | `Modifier.fluidSwipeActions`, che è già un modificatore di qualsiasi vista. Aspetto di iOS 27 (tondo con il glifo e l'etichetta sotto: R5 L3, osservato) a occhio. **Divergenza che resta**: `swipeActionsContainer` (una riga aperta alla volta) non ha un equivalente, perché nell'engine l'azione parte al rilascio e la riga non resta aperta |

## Bordo dello scorrimento

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| E1 | Bordo dello scorrimento | alta | si fa | F2.4, F2.5 | sostituisce la fascia `FluidTopBarGlass`; in alto e in basso. **Aspetto di iOS 26, per decisione di Alessio**: è l'eccezione dichiarata a «iOS 27 dove differisce». In basso «sfuma senza sfocare» vale per iOS 26; per iOS 27 nessuna conferma (R5 corregge R1): a occhio |
| E2 | Soft, hard, scurimento | alta | si fa | F2.4 | `Automatic` è il bordo morbido di iOS 26 e `Hard` quello di iOS 26 (R4 B7). La fascia uniforme a taglio netto che iOS 27 dà ad `Automatic` (R5 E1, alta) **non si fa**: è ciò che Alessio ha chiesto di togliere |
| E3 | Barre personalizzate | media | si fa | F2.5 | `FluidSafeAreaBar`, `FluidScrollEdge` |
| E4 | Estensione dello sfondo | media | si fa | F4B.4 | `Modifier.fluidBackgroundExtension()` |

## Geometria

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| G1 | Concentricità | alta | si fa | F3.5 | `FluidConcentricShape`, `Modifier.fluidContainerShape` |
| G2 | Capsula e margine dai bordi | alta | si fa | F1A.11 (margine dei tasti a 16 dp), F3.5 | la capsula c'è (`FluidCapsuleShape`); il margine del vetro dal bordo passa da 8 a 16 dp (R6: Apple 20 pt su 440) |
| G3 | Misure note | alta | si fa | F1A.11 (nasce `FluidMetrics` con le taglie di R6), F3.5 (gruppi: 12 dp fra due, passo di 44 dp) | il bersaglio minimo dell'engine è già 48 dp; R6 dà in punti tasti, icone, margini e tab bar, non altezze e raggi delle barre |

## Tocco e movimento

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| H1 | Flessione e luce al tocco | alta | si fa | F6.1 | oggi la flessione è una scala del layer (R2 §6) |
| H2 | Bagliore che si propaga | alta | si fa | F6.2 | oggi il bagliore sta sul solo elemento premuto |
| H3 | Vetro interattivo | alta | si fa | F2.2 (`FluidGlass.interactive`), F6.1, F6.2 | |
| H4 | Salire nel vetro | alta | si fa | F6.3 | la goccia dei controlli c'è (R2 §2.2) |
| H5 | Molle e durate | alta | c'è | ogni fase | i token sono in `FluidMotion`; ogni fase li tara contro R4 dove R4 misura |
| H6 | Aptica | media | c'è | — | modulo aptico dell'engine; Apple non ne documenta una legata al vetro |

## Navigazione

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| V1 | Push/pop interrompibile | alta | si fa (in parte c'è) | F4A.6 | `FluidPageHost` c'è (`ref/regole.md:422`) e il gesto riprende una pagina che torna a posto; ma un gesto indietro o un pop che arrivano **durante un push** lo fanno finire di colpo e ripartono da zero (`ui/page/FluidPageHostState.kt:451-459`, `:293-315`). Si conservano posizione e velocità; test scritto prima della correzione; voce di S4 |
| V2 | Ritorno da tutto il contenuto | alta | c'è | — | `FluidBackSwipe.Anywhere` |
| V3 | Elementi della barra che si trasformano nel push | alta | si fa | F4A.3 | c'è per le sole capsule, fino a 6 coppie (R2 §4.3) |
| V4 | Transizione zoom | media | si fa | F5B.3 | `FluidPageTransition.Expand` c'è; nasce la sorgente dichiarata |
| V5 | Barra di navigazione che si ritira (27) | media (R1 diceva bassa; R5: di serie accade in ogni pagina con la ricerca principale) | si fa | F4A.4 | `FluidChromeController` c'è. `toolbarMinimizationBehavior` (R5 corregge il nome di R1): `barMinimize` con `Automatic` (solo con la ricerca in posizione principale), `Never`, `OnScrollDown`, `OnScrollUp`; titolo e tasti sfocano e svaniscono in 170–230 ms, il campo resta e diventa vetro (R5 V5, media); `barMinimizeAdjustsInsets` |
| V6 | Split view e ispettore | media | c'è | F4B.4 (materiale e luce del contenuto) | `FluidListDetailScaffold`, `FluidSidebar` |

## Altro

| ID | voce | priorità | esito | dove | note |
|---|---|---|---|---|---|
| O1 | Tastiera | media | si fa, in parte | F4B.2 | il campo di ricerca sale sopra la tastiera; lo stile della tastiera no (è un'altra finestra, e il vetro non può rifrangerla) |
| O2 | Lente del testo | bassa | no | — | è la lente di selezione della piattaforma |
| O3 | Foglio di condivisione | media | no | — | è il foglio di sistema di Android; ciò che insegna (vetro a mezza altezza, opaco a tutta altezza) è P1, in F5B.2 |
| O4 | Schermata di blocco | bassa | no | — | di sistema; la tinta come materiale è M8 (F2.2), la luce che corre sul bordo è in F6.5 |
| O5 | Icone a strati | bassa | no | — | fuori dall'engine (icone delle app) |
| O6 | Player video | bassa | no | — | l'engine non ha un player; tasti `Clear` sopra un video sono M2 (F2.2), con una demo in F2.6 |
| — | Notifiche, Centro di controllo | — | no | — | R1 non trova comportamenti utili; `FluidNotification` dell'engine prende il materiale in F1B.5 |

## Voci di iOS 27 che R1 non aveva (da R5)

Non hanno un numero in R1 e non entrano nel conto. Quelle che R5 riconduce a una voce numerata stanno sopra (cursore: M11;
scheda prominente: T6; priorità e trabocco: B11; barra che si ritira: V5; avvisi da un dato: P4, P5).

| voce | esito | dove | note |
|---|---|---|---|
| Area sicura durante il ritiro della barra (`toolbarMinimizationSafeAreaAdjustment`) | si fa | F4A.4 | `barMinimizeAdjustsInsets`: di serie il contenuto risale |
| Azioni a scorrimento su qualsiasi vista | c'è | — | `Modifier.fluidSwipeActions` è già un modificatore |
| `swipeActionsContainer` | no | — | divergenza dichiarata in L3 |
| Foglio in dissolvenza (`navigationTransition(.crossFade)`) | si fa | F5B.2 | documentato, nessun video: a occhio |
| Riordino per trascinamento fuori dalle liste | c'è | — | `fluidReorderable`, `rememberFluidReorderState` |
| Icone nascoste di serie nei menu di iPad e Mac | no | — | su iPhone nessun cambio dichiarato |
| Voce «Chiedi a Siri» nei menu | no | — | funzione di sistema |
| App ridimensionabili; barra laterale anche su iPhone | c'è | F4B.4 | la forma della navigazione segue la larghezza; `FluidSidebar` si può chiamare a mano |
| Barre laterali ai bordi della finestra, icone d'accento (iPad) | no, per scelta | F4B.4 | divergenza dichiarata in T7 |
| Ambiti della ricerca in linea | si fa | F4B.2 | in R5 (anatomia del campo) |
| `textInputBorderShape`, stile `.bordered` dei campi | no | — | solo API di stile; `FluidTextField` ha il suo |
| `GeometryProxy.concentricCornerRadii` | si fa | F3.5 | `rememberFluidConcentricRadii` |
| Taglia e stile dei controlli che tornano di serie dentro fogli e popover | si fa | F6.4 | |
| Il contenitore di una barra personalizzata contribuisce al bordo dello scorrimento | no | — | serve alla fascia uniforme di iOS 27, che non si fa; `FluidScrollEdge` e `FluidSafeAreaBar` coprono E3 |
| Icone delle app a più strati; dispositivo pieghevole | no | — | fuori dall'engine e fuori perimetro |

## Icone e taglie (da R6)

Nemmeno queste sono voci di R1. Alessio ha deciso: icone monocrome, da un set curato al tratto a peso 500, senza
ingrossamento automatico delle icone delle app.

| voce | esito | dove | note |
|---|---|---|---|
| Set `FluidIcons` e generatore | si fa | F0.8 | Material Symbols (Apache-2.0) come `ImageVector`; copre le icone dei componenti dell'engine e quelle che le app usano davvero; vive in `engine-ui` |
| Componente `FluidIcon`, taglia d'ambiente, adozione nei componenti dell'engine | si fa | F1A.11 | nessun parametro di peso |
| Icona nel tasto a 26 dp (indietro 24), margine a 16 dp, distanza a 8 dp | si fa | F1A.11 | vetro 44 dp e bersaglio 48 dp invariati |
| Tab bar: 62 dp, icone da 28 dp a inchiostro pieno, etichetta da 11 sp | si fa | F1B.4 | |
| 12 dp fra due gruppi, passo di 44 dp nel gruppo | si fa | F3.5 (numeri), F4A.2 (uso) | il passo di iOS 27 (62 pt su una barra sola) è scelta di gusto |
| Icona delle righe di menu a 22 dp | si fa | F5A.2 | riga invariata |
| `FluidGlassSize.iconSize` a 0,6 del vetro | si fa | F6.4 | |
| Migrazione delle icone app per app | no, in questa tornata | F8.2 (le note dicono come) | compito delle app |

## Conto

| | voci |
|---|---|
| si fa (anche in parte) | 72 |
| c'è (con il materiale nuovo e i ritocchi indicati) | 16 |
| no | 6 numerate, più la riga senza numero |
| **totale numerate** | **94** |

Rispetto al giro 0: V1 passa da «c'è» a «si fa (in parte c'è)».

Nessuna voce di priorità alta è «no». Nessuna voce aspetta più una ricerca. Restano **a occhio**, con confronto in chat,
perché né R4 né R5 le misurano: tema scuro del materiale, ombra sopra il testo, raggio di sfocatura e forza della lente,
smerigliatura e ombra di menu e pannelli, soglia del giro chiaro/scuro, bordo dello scorrimento in basso, scheda prominente
scelta, ricerca in basso, foglio in dissolvenza, azioni a scorrimento, stato inattivo. Divergenze scelte e scritte: bordo
dello scorrimento di iOS 26 (E1, E2), sfondo del menu contestuale (X2), vetro sul contenuto ancora disponibile (L1, M10),
barra laterale del tablet (T7), `swipeActionsContainer` (L3).
