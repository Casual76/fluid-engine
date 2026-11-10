# R5 · iOS 27 rispetto a iOS 26: le differenze del nuovo design, con le misure

Ricerca del 2026-11-10 per il progetto «iOS in Android». Il bersaglio è iOS 27 dove differisce da iOS 26;
dove non differisce valgono R1 (`inventario-ios.md`) e R4 (`riferimenti-misure.md`). Stesso metodo e stesse
unità di R4: frazioni dell'elemento (`H` altezza, `D` diametro), millisecondi, luma Rec. 709 su valori sRGB
0–255, incertezza dichiarata.

**Gradi delle fonti**, come in R1: **D** documentato, **W** detto da Apple in una sessione, **O** osservato
(qui: misurato sui video Apple, o fonte secondaria se indicato), **De** dedotto.

**Avviso.** I video sono di giugno 2026 (prima beta). Apple ha ritoccato almeno l'estremo «ultra trasparente»
del cursore nella beta 5 (9to5Mac): l'aspetto di serie della 27.0 definitiva può scostarsi di poco da quello
misurato. Non c'era un dispositivo con iOS 27 per verificarlo.

---

## Cosa conta per il piano

1. **Il vetro di serie diffonde molto di più.** Sotto una barra resta il 6–13% del contrasto di ciò che sta
   dietro (iOS 26: 19–27%, cioè circa la metà); i dettagli fini scendono al 35–45% di quelli di iOS 26. Il
   colore del fondo si spande su tutta l'altezza dell'elemento.
2. **Il corpo è meno bianco**: su pagina chiara +8 livelli invece di +15; su bianco puro sta 4–11 livelli
   **sotto** il bianco.
3. **Bordo nuovo: filo scuro più riflessi.** Un filo scuro di 1–2% di H corre intorno all'elemento (−20…−30%
   di luminanza su fondo chiaro); in alto e in basso c'è un riflesso chiaro interno (+10…+16 sul corpo).
   In iOS 26 sul chiaro il filo scuro non c'era.
4. **L'ombra larga è sparita**: 2% al bordo, esaurita entro 0,2 H (iOS 26 nella stessa scena: 7–8%, estesa
   0,8 H). La separazione ora la fa il filo scuro.
5. **Il bordo dello scorrimento automatico è una barra uniforme**: dalla cima dello schermo fino a 0,21 D
   sotto i tasti, taglio netto (≤4 px) con un filo separatore, dentro sfocatura uniforme più velo bianco del
   60–80%. Non c'è più la sfumatura progressiva di iOS 26. `.soft` e `.hard` esistono ancora ma non sono più
   l'aspetto di serie.
6. **La barra di navigazione si ritira scorrendo** (`toolbarMinimizationBehavior`): titolo e tasti sfocano e
   svaniscono in ≈170–230 ms, il campo di ricerca resta fissato come capsula di vetro. Di serie accade solo
   con la ricerca in posizione principale.
7. **Scheda prominente**: tondo di diametro H a 0,11 H dalla capsula, margini 0,30–0,32 H, solo glifo.
   Vale per qualsiasi scheda, resta visibile a barra minimizzata.
8. **Cursore dell'utente** (Impostazioni › Aspetto › Liquid Glass) da ultra trasparente a del tutto colorato,
   di serie a metà: all'estremo colorato il vetro diventa una lastra quasi opaca del colore di fondo.
9. **Solo API, niente aspetto nuovo**: priorità di visibilità e menu di trabocco delle barre, avvisi e dialoghi
   legati a un dato, `swipeActionsContainer`. Menu, morph, tocco, tinta e forme non risultano cambiati.
10. **Tema scuro e bordo in basso di iOS 27 restano scoperti**: un solo fermo immagine HD per il primo,
    niente di misurabile per il secondo.

---

## Tabella delle differenze

| voce | iOS 26 (rimando) | iOS 27 | fonte e grado | cosa cambia per chi deve ricrearlo |
|---|---|---|---|---|
| Diffusione del vetro regular | capsula lascia il 26% del contrasto del testo, menu il 13% di una foto (R4 A5) | «diffonde molto più efficacemente il contenuto complesso»: 6–13% sotto una barra | [keynote @7:20](https://developer.apple.com/videos/play/wwdc2026/101/?time=440), [SOTU @22:43](https://developer.apple.com/videos/play/wwdc2026/102/?time=1363) **W**; misura M1 **O** | sfocatura più ampia (il colore si spande su ≈0,7–0,9 H) e meno dettaglio; il valore di serie dell'engine va spostato verso quello che in iOS 26 era il menu |
| Corpo (velo) | +15…+27 sul fondo chiaro, velo 30–45% (R4 A2) | +8 su pagina chiara, −4…−11 su bianco puro; velo ≈50% su colore saturo (iOS 26 nella stessa scena ≈60%) | misura M1, M4 **O** | velo più leggero; su bianco il tasto è leggermente grigio, non più indistinguibile |
| Bordo: filo scuro | nessuno dove il filo di luce è acceso; solo sui lati dei grandi, −5…−18 (R4 A1) | «bordo scurito»: filo di 1–2% di H tutto intorno su fondo chiaro, −20…−30% | [SOTU @22:50](https://developer.apple.com/videos/play/wwdc2026/102/?time=1370) **W**; misura M1, M4 **O** | nuovo strato da disegnare: contorno scuro sottile, sempre presente sul chiaro |
| Bordo: riflessi | due archi opposti quasi saturi (R4 A1) | «riflessi speculari più brillanti»: in alto e in basso, +10…+16 sul corpo, verso l'interno | SOTU **W**; misura M1, M4 **O** | con il corpo meno bianco il riflesso si vede anche su fondo chiaro; posizione alto/basso invece di due archi diagonali (da confermare su un tondo) |
| Ombra | 4% su fondo piatto, 18% sul testo, estesa 0,75–0,85 H (R4 A3) | 2% al bordo, esaurita a 0,2–0,25 H | misura M1, M4 **O** (Apple non lo dice) | ombra stretta e leggera; la profondità passa al filo scuro |
| Cursore dell'utente | 26.1: scelta Trasparente / Colorato (R1 M11) | cursore continuo da «ultra trasparente» a «del tutto colorato» in Impostazioni › Aspetto › Liquid Glass, di serie a metà; sostituisce la scelta a due | keynote, SOTU, [269 @2:28](https://developer.apple.com/videos/play/wwdc2026/269/?time=148) **W**; [9to5Mac](https://9to5mac.com/2026/06/15/ios-27-adds-new-liquid-glass-slider-on-iphone-heres-what-it-lets-you-do/), [TechnoBezz](https://www.technobezz.com/how-to-change-liquid-glass-on-iphone) (secondarie) **O**; misura M2 | un solo parametro continuo che muove insieme velo e sfocatura; le app non fanno nulla |
| Bordo dello scorrimento, `.automatic` | il sistema sceglie fra soft e hard; su iPhone soft: sfumatura progressiva (R1 E2, R4 B7) | non alterna più: aspetto proprio, «barra uniforme» in alto, più opaca | [278 @11:50](https://developer.apple.com/videos/play/wwdc2026/278/?time=710), SOTU @24:10 **W**; [HIG Scroll views](https://developer.apple.com/design/human-interface-guidelines/scroll-views) (8 giugno 2026) **D**; misura E1 **O** | fascia a tutta larghezza con taglio netto, sfocatura uniforme e velo forte; non la sfumatura di R4 |
| Bordo dello scorrimento, `.soft` e `.hard` | soft di serie, hard per intestazioni fissate | restano, ma Apple chiede di preferire `.automatic`; `.soft` «non coincide più con l'aspetto di sistema» | 278 **W**, HIG **D** | tenere i due stili come opzioni, cambiare il valore di serie |
| Barra di navigazione che si ritira | non esiste (R1 V5 la segna come novità 27) | `toolbarMinimizationBehavior(_:for:)` con `.automatic`, `.never`, `.onScrollDown`, `.onScrollUp`; di serie solo con ricerca in `toolbarPrincipal`; UIKit `UINavigationItem.navigationBarMinimization` | [pagina](https://developer.apple.com/documentation/swiftui/view/toolbarminimizationbehavior(_:for:)), [note di rilascio](https://developer.apple.com/documentation/ios-ipados-release-notes/ios-ipados-27-release-notes) **D**; misura V5 **O** | i nomi della sessione (`toolbarMinimizeBehavior`, `barMinimizationBehavior`) sono stati sostituiti: R1 va corretto. Movimento da ricreare, vedi V5 |
| Area sicura durante il ritiro | — | di serie il contenuto risale nello spazio liberato; `toolbarMinimizationSafeAreaAdjustment(.disabled, …)` lo ferma | [pagina](https://developer.apple.com/documentation/swiftui/view/toolbarminimizationsafeareaadjustment(_:for:)) **D** | l'inset in alto segue la barra, salvo richiesta contraria |
| Scheda prominente | solo la ricerca è separata a destra (R1 T5) | `TabRole.prominent` su una scheda qualsiasi, una sola; se manca, la ricerca prende il trattamento; sempre visibile a barra minimizzata; UIKit `prominentTabIdentifier` | [TabRole.prominent](https://developer.apple.com/documentation/swiftui/tabrole/prominent), [UIKit](https://developer.apple.com/documentation/uikit/uitabbarcontroller/prominenttabidentifier) **D**; 278 @10:53, 269 @5:12 **W**; misura T6 **O** | il tondo a destra diventa un ruolo generico, non un caso speciale della ricerca |
| Barre con poco spazio | il sistema sposta nel trabocco ciò che non entra, senza controllo (R1 B8) | `visibilityPriority(.low/.automatic/.high)` e priorità relative (`init(lowerThan:)`, `init(higherThan:)`): escono prima i più bassi; `ToolbarOverflowMenu { }` sta sempre nel menu; `.topBarPinnedTrailing` resta a destra e va nel trabocco solo con la ricerca attiva | [visibilityPriority](https://developer.apple.com/documentation/swiftui/toolbarcontent/visibilitypriority(_:)), [ToolbarOverflowMenu](https://developer.apple.com/documentation/swiftui/toolbaroverflowmenu), [topBarPinnedTrailing](https://developer.apple.com/documentation/swiftui/toolbaritemplacement/topbarpinnedtrailing) **D**; 269 @5:27 **W** | regola di trabocco dichiarativa: tre livelli più «sempre nel menu» e «fissato». Il menu di trabocco è un menu normale (N1), ritaglio B11 |
| Avvisi e dialoghi di conferma | binding booleano (R1 P4, P5) | varianti `alert(_:item:…)`, `alert(error:…)`, `confirmationDialog(_:item:…)`: «item» è un **dato** opzionale, non un elemento dell'interfaccia; disponibili da iOS 15 | [SwiftUI updates](https://developer.apple.com/documentation/updates/swiftui), note di rilascio **D**; 269 @19:11 **W** | nessun cambio visivo né di ancoraggio: è solo il modo di aprirli. R1 («binding a un elemento») va letto così |
| Azioni a scorrimento | solo nelle righe di `List` (R1 L3) | su qualsiasi vista; `swipeActionsContainer()` sul contenitore: una riga aperta alla volta, si chiude scorrendo o toccando fuori; `onPresentationChanged` | [swipeActionsContainer](https://developer.apple.com/documentation/swiftui/view/swipeactionscontainer()) **D**; 269 @17:44 **W**; ritaglio L3 **O** | il coordinamento diventa un modificatore del contenitore. Aspetto osservato: tondo rosso con etichetta sotto, la riga si accorcia e arrotonda |
| Foglio in dissolvenza | il foglio sale dal basso o fa lo zoom dalla sorgente (R1 P3) | `navigationTransition(.crossFade)` su un foglio: compare in dissolvenza sopra il contenuto | [crossFade](https://developer.apple.com/documentation/swiftui/navigationtransition/crossfade) **D** | transizione nuova, non mostrata in video: nessuna misura |
| Riordino per trascinamento | solo `List` | `reorderable()` + `reorderContainer(for:…)` in liste, pile, griglie; il sistema anima sollevamento e rilascio | SwiftUI updates **D**; SOTU @29:20 **W** | fuori dal materiale; da tenere presente per le liste |
| Icone nei menu | icone a sinistra (R1 N3) | barra dei menu di iPad e Mac e menu contestuali del Mac: icone nascoste di serie, visibili con `labelStyle(.titleAndIcon)` / `preferredImageVisibility`. Su iPhone nessun cambio dichiarato | note di rilascio **D**; 278 @12:24 **W** | per il telefono non cambia nulla |
| Voce «Chiedi a Siri» nei menu | — | i menu di iOS 27 mostrano da soli un tasto per parlare con Siri quando c'è contenuto adatto | 278 @12:56 **W** | funzione di sistema, non replicabile |
| App iPhone ridimensionabili | — | finestra libera in Duplicazione iPhone e su iPad; barra laterale anche su iPhone se l'app la sceglie (`sidebar.preferredPlacement = .sidebar`) | 278 @0:46 e @9:51 **W** | spinge verso layout per classi di misura; rilevante per il Tab S9 |
| Barre laterali | flottanti, rientrate (R1 T7, V6) | su Mac e iPad arrivano ai bordi della finestra; icone di nuovo col colore d'accento | keynote @8:24, SOTU @23:36 **W**; HIG Sidebars (8 giugno 2026) **D** | solo tablet |
| Finestra inattiva su iPad | solo Mac (R1 M12) | icone e testo si attenuano anche su iPad | 269 @2:51 **W** | bassa priorità |
| Barra degli ambiti della ricerca | riga separata sotto il campo | in linea, sulla stessa riga del campo, con la ricerca al centro | note di rilascio **D** | piccolo cambio di layout della ricerca |
| Campi di testo | stili `.roundedBorder`, `.squareBorder` | `textInputBorderShape(_:)` e stile `.bordered`; i due vecchi in via di ritiro | note di rilascio **D** | solo API |
| Raggi concentrici | `ConcentricRectangle` (R1 G1) | in più `GeometryProxy.concentricCornerRadii` per disegni propri | note di rilascio **D** | solo API |
| Taglia dei controlli nei fogli | ereditata dal genitore | `controlSize`, `buttonSizing`, forma del bordo tornano ai valori di serie dentro fogli e popover | note di rilascio **D** | un foglio non eredita lo stile della barra che lo apre |
| Barre personalizzate e bordo | i discendenti danno forma all'effetto (R1 E3) | anche il contenitore di `UIScrollEdgeElementContainerInteraction` può contribuire con misura e posizione | note di rilascio **D** | coerente con la fascia uniforme |
| Icone delle app | strati con riflessi (R1 O5) | più strati di vetro, rifrazione fra strati, resa più nitida | keynote @8:58 **W** | fuori dall'engine |
| Dispositivo pieghevole (settembre 2026) | — | `ArrangementView`, regioni riservate, cerniera, barre sull'asse verticale (`toolbarVerticalEdge`, `axisBehavior`) | SwiftUI e UIKit updates, settembre 2026 **D** | fuori perimetro; esiste un modello di barre verticali se servisse per i pieghevoli |

Non risultano differenze dichiarate per: `Glass` (stesse varianti e metodi), `GlassEffectContainer`,
identità e transizioni del vetro, tinta, menu dal tasto, menu contestuale, fogli e detent, popover, controlli,
ricerca, minimizzazione della tab bar, concentricità. Apple dice che le app prendono il nuovo aspetto «senza
cambiare una riga di codice» (269 @2:17).

---

## Le sorgenti e come si è misurato

| cosa | valore |
|---|---|
| sessioni intere, HD | `artifacts/liquid-glass/riferimenti/video/wwdc2026-269_hd.mp4`, `…-278_hd.mp4`, `…-251_hd.mp4`, `…-292_hd.mp4`, `…-250_hd.mp4`: 1920×1080, H.264, **30 fps esatti** (33,3 ms per frame) |
| spezzoni 4K (lista HLS, HEVC, tempi originali) | `…-102_2160p_2128.mp4` (21:25–25:33), `…-102_2160p_2950.mp4` (29:50–33:40), `…-269_2160p_0208.mp4` (2:06–8:12), `…-269_2160p_1724.mp4` (17:18–19:50), `…-278_2160p_0928.mp4` (9:24–12:52) |
| keynote | `…-101_2160p_0706.mp4`: scaricati solo 18 s (7:16–7:34 del flusso); il CDN dell'evento interrompe i segmenti successivi. Non usata per le misure |
| cadenza | 269, 278, 292: 30 fps; 101 e 102: 29,97 fps |

`fN` è il numero di frame della sessione alla cadenza della sorgente. Classi di affidabilità come in R4.

- **Confronto di Apple, stessa scena** (SOTU 22:43–22:57, classe B/C): tab bar e accessorio di Musica
  ingranditi, una tendina passa due volte da sinistra a destra scoprendo iOS 27 sopra iOS 26. `f40864` è
  tutto iOS 26, `f41284` tutto iOS 27. Fuori dal vetro le due immagini coincidono (arte: 126,3 contro 127,3;
  pagina lontana 235 in entrambe): ogni differenza è del materiale. È la misura più solida del documento.
- **Telefono simulato** (278 11:15–12:22, 269 5:03–7:22, classe B): interfaccia vera, scorrimento programmato.
- **Grafica di presentazione** (278 10:52–11:09, 292, classe C): disegnata con l'aspetto di iOS 27.

Attrezzi nuovi in `artifacts/liquid-glass/riferimenti/tools/`: `lg27.py` (lettura da più sorgenti),
`sheet27.py` (collage), `ios27_misure.py` (rifà i numeri delle schede M1, E1, M4, T6). Risultati in
`artifacts/liquid-glass/riferimenti/ios27/` (`panoramica/`, `collage/`, `ritagli/`).

---

## Le misure

### M1 · Il materiale, iOS 26 contro iOS 27 sulla stessa scena

Ritaglio: `artifacts/liquid-glass/riferimenti/ios27/ritagli/M1_tabbar_ios26_sopra_ios27_sotto.jpg`
(sopra iOS 26, sotto iOS 27). Sequenza: `…/ios27/panoramica/102_tabbar_2243-2259_ogni20.jpg`.
Tab bar alta H = 570 px, tema chiaro, pagina 235, arte colorata sotto l'accessorio.

| proprietà | iOS 26 (`f40864`) | iOS 27 (`f41284`) | incertezza |
|---|---|---|---|
| contrasto che resta, arte sotto l'accessorio (dev. std; arte nuda 68–76) | 14,2–18,7: **19–27%** | 4,3–9,5: **6–13%** | l'arte sotto e quella nuda non sono gli stessi pixel: ±5 punti |
| intervallo 2–98% della luma, stessa finestra | 47–52 | 16–34 (**34–65%** di iOS 26) | ±5 |
| testo dietro la tab bar («Break…») | dev. std 10,2, intervallo 38 | 5,4 e 16 (**42–53%**) | ±3 |
| bordo di una striscia gialla sotto la barra (canale B) | salto di 135 livelli in 24 px su 172 totali (78%) | salto di 64 in 24 px su 160 (40%); il resto si spande su ≈0,7–0,9 H | ±5 punti |
| corpo su pagina chiara (235) | 250: **+15** | 243: **+8** | ±2 |
| velo su arte arancio satura (canali G, B) | 0,62 e 0,58 | 0,56 e 0,49 | ±0,04 |
| velo su arte gialla (canale G) | 0,84 | 0,44 | il giallo diffuso sporca la misura |
| ombra sotto il bordo basso, livelli sotto la pagina | 18 a 0,03 H (**7,7%**), 14 a 0,1 H, 11 a 0,2 H, 8 a 0,3 H, 3 a 0,5 H, 0 a 0,8 H | 4 a 0,03 H (**1,7%**), 4 a 0,1 H, 0 a 0,2 H | ±1 livello |
| ombra a sinistra del fianco, a 0,2 H | 6–7 livelli (2,7%) | 0 | ±1 |
| arte nel varco fra accessorio e tab bar | 188 (scurita del 5,5%) | 199 | ±1 |
| fianco sinistro: filo | nessun filo scuro: 223 → 238 → 250 | **filo scuro** 163–198 contro pagina 233 (**−35…−70**, 15–30%), largo 4–8 px (0,7–1,4% di H) | la compressione allarga il filo |
| dove sta il filo scuro (testata sinistra, angoli da destra in senso antiorario) | — | pieno fra 165° e 225°, −17 a 240°, assente da 255° a 270° | passo di 15° |
| bordo basso | 251 su corpo 250: non distinguibile | **253 su corpo 243–245 (+8…+10)**, spesso ≈8 px (1,4% di H) | saturo |
| bordo alto (sopra arte gialla) | 250 su corpo 250 | 243–250 su corpo 227–235 (**+8…+16**) | ±3 |

La relazione fra le due immagini non è una sfocatura semplice: provando a sfocare l'iOS 26 con una gaussiana
l'errore resta quasi piatto (minimo debole a σ ≈ 0,07 H). In iOS 27 il fondo appare anche spostato e
ingrandito, come attraverso una lente più forte. Il raggio vero non si ricava da qui.

### M2 · Il cursore dell'utente

Collage: `artifacts/liquid-glass/riferimenti/ios27/collage/M2_cursore_trasparente-colorato_102_2258-2310.jpg`.
Ritaglio dei quattro stati: `…/ios27/ritagli/M2_cursore_tre_stati.jpg`. SOTU 22:58–23:09, classe B.
Il contenuto scorre mentre il cursore si muove: gli stati non hanno lo stesso fondo, i numeri sono indicativi.

| stato | cosa si vede | misura sull'accessorio sopra l'arte rosa (arte nuda: dev. std 35, intervallo 183) |
|---|---|---|
| di serie (`f41361`, cursore al centro) | forme riconoscibili come macchie, niente lettere | non confrontabile (sotto c'è la pagina chiara) |
| ultra trasparente (`f41466`) | il testo dietro si legge, piegato dalla lente; il colore resta schiarito | dev. std 14, intervallo 51 (28%); colore (252,173,217) su (236,88,145): velo ≈0,5 |
| del tutto colorato (`f41613`) | lastra quasi opaca: una sola campitura sfumata del colore dominante, nessuna forma | dev. std 4,2, intervallo 16 (**9%**); colore (242,184,223): velo 0,57–0,71 |

Il cursore passa da un estremo all'altro in ≈6 s nella clip (animazione di presentazione, non un tempo
dell'interfaccia). Il velo cambia poco fra gli estremi: cambia soprattutto la diffusione.

### M3 · Tema scuro e vetro su contenuto scuro

Ritagli: `artifacts/liquid-glass/riferimenti/ios27/ritagli/M3_tab_bar_scura_292_0632.jpg`,
`…/M3_tab_bar_scura_292_0527.jpg` (sessione 292, solo HD, grafica di presentazione, classe C);
`…/B11_barra_e_trabocco_269.jpg` (269 @6:20, 4K, classe B).

| caso | valore misurato | incertezza | frame |
|---|---|---|---|
| tab bar scura su griglia colorata | corpo 47–157 secondo il fondo, sfumato in continuo; sotto la barra nessuna forma riconoscibile | HD, barra alta 68 px | 292 `6:32` |
| stessa barra sopra un varco nero (luma 15–23) | corpo 58–78 | ±5 | idem |
| bordo alto, tema scuro | +10…+25 sul corpo; nessun filo scuro visibile | ±5 | idem |
| scheda selezionata, tema scuro | pillola più scura del corpo (47–57), glifo ed etichetta tinti | a occhio | idem |
| barra chiara sopra foto scura (luma 51, dev. std 9) | corpo **141–146**, uniforme (dev. std 1,8); glifi neri; non gira allo scuro | ±2 | 269 `6:20` |
| suo bordo | riflesso alto 171, basso 172 (**+26…+27** sul corpo), ≈4 px su H = 132 | ±3 | idem |

Il valore 141–146 coincide con il «vetro chiaro su nero prima di girare» di R4 (136–143). Qui però la scena è
ferma e il vetro resta chiaro: o la soglia del cambio non è raggiunta a luma 51, o in iOS 27 il cambio è più
restio. Non si può decidere da un'inquadratura.

### M4 · Vetro su fondo chiaro: tasto e tab bar veri

Ritagli: `artifacts/liquid-glass/riferimenti/ios27/ritagli/T6_scheda_prominente_269_0519.jpg` (tab bar su
telefono, H = 197 px), `…/E1_bordo_scorrimento_alto_278.jpg` (tasto indietro, D = 161 px).

| proprietà | valore misurato | incertezza | frame |
|---|---|---|---|
| tab bar su pagina 238: corpo | 240–243 (**+2…+5**) | ±2 | 269 `5:19` |
| filo scuro, tutto il perimetro | 187–193 al pixel (**−45…−51**, 19–21%), 2–3 px (1,0–1,5% di H) | ±1 px | idem |
| riflesso interno, alto e basso | 250–254 (**+10…+12**), torna al corpo in ≈16 px (0,08 H) | saturo | idem |
| ombra sotto | 229–231 al bordo (**3–4%**), nulla a ≈0,25 H | ±1 livello | idem |
| pillola della scheda selezionata | 219 (−19 sulla pagina), rientrata di 0,076 H, alta 0,85 H | ±2 px | idem |
| tasto tondo su bianco 253: corpo | 243 in alto, 249 in basso (**−4…−10**) | ±1 | 278 `f21480` |
| suo filo scuro | 172–203 (**−50…−81**, 20–32%), 2–3 px (1,2–1,9% di D), uguale su tutti i lati | ±1 px | idem |
| sua ombra | −2 (meno dell'1%) entro 0,15 D | ±1 | idem |
| tasto sopra la barra del bordo di scorrimento | corpo 222–233 contro 246–252 intorno (**−13…−24**); filo 148–164 (**−88…−98**) | ±3 | 278 `f21390`, `f21440` |
| tab bar disegnata su bianco 253 | corpo 242–248, più scuro verso i bordi; filo −35 (media su 4–6 px); ombra 3% entro 0,15 H | grafica | 278 `f19650` |

### E1 · Il bordo dello scorrimento di iOS 27 (in alto)

Collage: `artifacts/liquid-glass/riferimenti/ios27/collage/E1_bordo_scorrimento_278_1151-1201_ogni10.jpg`.
Ritaglio: `…/ios27/ritagli/E1_bordo_scorrimento_alto_278.jpg`. Sessione 278, 11:51–12:01, classe B: pagina
con tasto indietro e titolo in linea, tema chiaro. D = 161 px è il diametro del tasto.

| proprietà | valore misurato | incertezza | frame |
|---|---|---|---|
| forma | fascia a tutta larghezza, dalla cima dello schermo a una **linea orizzontale dritta** | — | `f21370`, `f21440` |
| dove finisce | **0,21 D sotto** il bordo basso dei tasti | ±0,02 D | `f21440` |
| altezza totale | 2,56 D (barra di stato 1,35 D, tasto 1 D, margine 0,21 D) | ±0,05 D | `f21480` |
| taglio | da 193 a 30 in **≤4 px (≤0,025 D)**: netto | ±2 px | `f21440` |
| filo separatore | −15 livelli su bianco (6%), 2–3 px | ±3 | `f21390` |
| velo verso il bianco, scheda scura (17,26,45) | dentro (195,200,204): **0,75–0,77** | ±0,03 | `f21440` |
| velo, scheda viola (128,76,195) | dentro (228,206,252): 0,73–0,81 (B saturo) | ±0,05 | `f21370` |
| velo, scheda gialla (250,190,17) | dentro (252,239,163): 0,62–0,79 | ±0,05 | `f21370` |
| sfocatura | bordo di una scheda: 10–90% in 53–56 px = **0,33–0,36 D**, cioè σ ≈ 0,13 D | ±0,03 D | `f21370`, `f21390` |
| andamento in altezza | uniforme: 188 in cima, 196 a metà, 189 al taglio | ±5 (macchie delle icone) | `f21440` |
| leggibilità sotto la fascia | il testo sparisce, restano macchie di colore grandi come un'icona | a occhio | `f21350` |
| contrasto subito sotto il taglio | pieno: il testo è tagliato dalla linea a metà carattere | — | `f21455` |
| su contenuto scuro, tema chiaro | **schiarisce**: scheda a luma 25 → fascia a 195 | ±3 | `f21440` |
| su pagina bianca | fascia bianca: si vede solo il filo | — | `f21390` |
| in cima alla pagina | nessuna fascia, nessun filo, titolo grande | — | `f21480` |
| titolo in linea | compare in ≈100–200 ms quando il titolo grande passa sotto; sparisce in ≈200–270 ms | ±1 frame; scorrimento programmato molto veloce | `f21333`–`f21339`, `f21462`–`f21471` |

Confronto con R4 B7 (iOS 26, soft): là il contrasto cade su ≈1,2 H, è al 50% a 0,43 H sotto i tasti e al
20% al loro bordo basso, con un velo che cresce dal 6% al 36%. Qui è pieno fino a 0,21 D sotto i tasti e
crolla in pochi pixel; il velo è il 62–81% su tutta la fascia.

Con la barra ritirata (V5) la fascia copre barra di stato e campo di ricerca e mostra la stessa campitura
di colore; il margine sotto il campo è ≈0,25 dell'altezza del campo (a occhio).

**In basso.** Nell'unica inquadratura utile (292 @6:32, tema scuro, HD) il testo delle schede sotto e accanto
alla tab bar è nitido e a contrasto pieno (luma 242–244): nessuna fascia, nessuna sfumatura visibile. Nelle
altre clip il fondo sotto la tab bar è piatto. Fiducia bassa.

### V5 · La barra di navigazione che si ritira

Collage: `artifacts/liquid-glass/riferimenti/ios27/collage/V5_barra_che_si_ritira_278_1115-1131_ogni8.jpg`,
`…/V5_ritiro_denso_278_f20258-20293.jpg`, `…/V5_ritorno_denso_278_f20352-20410_ogni2.jpg`.
Sessione 278, 11:15–11:30, classe B: pagina «Search» di Podcast, titolo grande, tasto tondo a destra e campo
di ricerca in posizione principale (il caso in cui `.automatic` ritira la barra). Lo scorrimento è
programmato: i tempi dipendono dalla sua velocità, non da una durata fissa.

A riposo: riga del titolo grande con il tasto a destra, sotto il campo di ricerca con lo stile del contenuto.

Ritiro, t = 0 a `f20266`:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 100 | il titolo sale con il contenuto e comincia a sfocarsi; il tasto a destra perde il vetro e il glifo rimpicciolisce |
| 100 | 170 | titolo ridotto a un'ombra sfocata, tasto quasi svanito; il campo sale insieme al contenuto |
| 170 | 230 | titolo e tasto spariti; il campo si ferma sotto la barra di stato e diventa una capsula di vetro; sopra e dietro si forma la fascia del bordo di scorrimento |
| 230 | — | stato ritirato: restano barra di stato e campo; il contenuto scorre sotto |

Ritorno (scorrimento più lento), t = 0 a `f20376`:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 130 | il campo comincia a scendere; un'ombra molto sfocata del titolo compare già nella sua posizione |
| 200 | 270 | il campo lascia il vetro e torna allo stile del contenuto (riempimento grigio) |
| 270 | 600 | il titolo si condensa dalla sfocatura mentre scende; il tasto a destra rientra crescendo |
| 600 | 800 | titolo nitido; piccolo assestamento della posizione (rimbalzo dello scorrimento) |

**Cosa si muove.** Titolo e tasti non escono scivolando come un blocco: traslano con lo scorrimento e
intanto **sfocano e svaniscono** (stessa grammatica dello scambio di icone di R4 B2). **Cosa resta.** Il
campo di ricerca, che cambia materiale: contenuto a riposo, vetro quando è fissato. Con `.onScrollDown` su
una barra senza ricerca (269 @7:38, iPad) l'intera barra esce: non misurata, inquadratura piccola.

### T6 · La scheda prominente

Ritagli: `artifacts/liquid-glass/riferimenti/ios27/ritagli/T6_scheda_prominente_269_0519.jpg` (telefono,
classe B), `…/T6_scheda_prominente_278_f19650.jpg` (grafica, classe C). Comparsa della grafica:
`…/ios27/collage/T6_scheda_prominente_278_1051-1057_ogni4.jpg`.

| proprietà | telefono (269, H = 197 px) | grafica (278, H = 263 px) | incertezza |
|---|---|---|---|
| forma | tondo | tondo | — |
| diametro | **1,00 H** | 1,01 H | ±0,01 |
| distanza dalla capsula | **0,117 H** | 0,106 H | ±0,01 |
| margine dai bordi dello schermo (sinistra, destra) | 0,30 H e 0,30 H | — | ±0,02 |
| margine dal fondo dello schermo | ≈0,32 H | — | ±0,03 (bordo curvo) |
| larghezza della capsula | 5,23 H con tre schede: riempie lo spazio lasciato dal tondo | 4,9 H con quattro schede | ±0,03 |
| contenuto del tondo | solo glifo, senza etichetta, nero | solo glifo, tinto (rosso) | — |
| stesso materiale della capsula | sì: stesso corpo, filo e ombra | sì | — |

Larghezza dello schermo = 7,0 H: capsula 5,23, distanza 0,12, tondo 1,0, due margini da 0,30 (somma 6,95).
La scheda prominente **selezionata** e il passaggio da e verso di essa non compaiono in nessuna clip.

### B11 · Barra con poco spazio e menu di trabocco

Ritaglio: `artifacts/liquid-glass/riferimenti/ios27/ritagli/B11_barra_e_trabocco_269.jpg`. Sessione 269,
6:16–7:22, classe B.

Composizione osservata su iPhone, da sinistra: indietro (tondo), un tasto tondo, un gruppo di tre in una
capsula, il tasto «…» tondo. Il menu di trabocco si apre dal tasto «…» come un menu normale: tre righe con
icona a sinistra, l'ultima rossa. Apertura misurata sul riquadro dei pixel che cambiano (`f12506`→`f12510`):
larghezza 41% a 0 ms, 63% a 33 ms, 81% a 67 ms, 93% a 100 ms, 100% a 133 ms; altezza al massimo a 100 ms,
**+1,6%** oltre il valore successivo. La coda non si misura (l'inquadratura cambia a 167 ms). È compatibile
con R4 B1 (massimo superamento a 100–167 ms): nessun cambio rilevabile nei tempi del menu.

### L3 · Azioni a scorrimento

Ritaglio: `artifacts/liquid-glass/riferimenti/ios27/ritagli/L3_azioni_a_scorrimento_269.jpg` (269 @17:52 e
@18:27). In lista e in contenitore libero l'azione è un **tondo rosso con il glifo bianco e l'etichetta
sotto**, a destra della riga; la riga scorre a sinistra, si accorcia e prende angoli arrotondati. Non ho un
riferimento di iOS 26 per dire se l'aspetto è cambiato: è solo osservato. Nessuna misura di tempo (la clip
taglia sul gesto).

---

## Specifiche di sintesi per iOS 27

Fiducia come in R4: **alta** = interfaccia vera o confronto diretto di Apple, più clip concordi; **media** =
una clip o un render; **bassa** = stima a occhio o derivata.

Valori che sostituiscono quelli di R4:

| regola per iOS 27 | sostituisce (R4) | fiducia |
|---|---|---|
| Smerigliatura di una barra: resta il 6–13% del contrasto di ciò che sta sotto; i dettagli fini al 35–45% di quelli di iOS 26 | capsula 26% | alta nel rapporto, media nel valore assoluto |
| Il colore del fondo si spande su ≈0,7–0,9 H dentro l'elemento | sfondo sfocato sul 10–15% di H | media |
| Corpo: +8 su pagina chiara, +2…+5 su grigio chiaro 238, −4…−11 su bianco puro; velo ≈0,5 su colore saturo | velo 30–45% su chiaro, +15…+27 | alta |
| Corpo chiaro su fondo scuro: 141–146 su luma 51 | 136–143 prima di girare | media (un'inquadratura) |
| Filo scuro: tutto il perimetro su fondo chiaro, 1–2% di H, −20…−30% di luminanza (−45…−80 livelli) | nessuna linea scura sugli archi accesi | alta |
| Riflesso: interno, in alto e in basso, +10…+16 sul corpo, sfuma in ≈0,08 H; su fondo scuro +26 | due archi a ≈130° e ≈315°, +40…+55 | media (misurato su capsule, non su un tondo) |
| Ombra: 2–4% al bordo, nulla a 0,2–0,25 H | 4% piatto e 18% su testo, estesa 0,75–0,85 H | alta su fondo piatto; su testo non misurata |
| Bordo dello scorrimento in alto: fascia uniforme dalla cima a 0,21 D sotto i tasti, taglio ≤0,025 D, filo −6% | contrasto al 50% a 0,43 H, tratto di 1,2 H | alta |
| Dentro la fascia: velo bianco 0,62–0,81 (centrale 0,75), sfocatura σ ≈ 0,13 D, uniforme | velo 6–36% progressivo più sfocatura progressiva | alta sul velo, media sulla sfocatura |
| Fascia su contenuto scuro, tema chiaro: schiarisce (25 → 195) | scurisce a ≈0,8 | media (tema scuro non visto) |
| Tasto di vetro sopra la fascia: corpo −13…−24 rispetto alla fascia, filo −88…−98 | — | media |
| Scheda prominente: tondo D = H, a 0,11–0,12 H dalla capsula, margini 0,30–0,32 H, solo glifo | — (nuovo) | alta |
| Ritiro della barra: titolo e tasti sfocano e svaniscono in 170–230 ms seguendo lo scorrimento; il campo resta e diventa vetro | — (nuovo) | media (tempi legati allo scorrimento) |
| Ritorno della barra: titolo dalla sfocatura al nitido in ≈300–400 ms, tutto a riposo in 600–800 ms | — (nuovo) | media |
| Estremo «colorato» del cursore: resta ≈9% dell'intervallo di luma, nessuna forma; estremo «trasparente»: ≈28%, testo leggibile | — (nuovo) | bassa |

Cosa **non cambia** (vale R4, con il motivo):

| voce di R4 | perché si ritiene invariata |
|---|---|
| Spessore del filo: 1–2% dell'altezza di una barra, assoluto | il filo scuro di iOS 27 ha lo stesso spessore; misurato |
| Apertura e chiusura del menu dal tasto (B1) | salita in 100–133 ms con superamento dell'1,6%: compatibile; coda non misurata |
| Sagoma in viaggio, contenuto dentro dal primo frame | non rimisurato; Apple non dichiara cambi, nessuna clip nuova |
| Cambio di contesto della barra, scambio di icone per sfocatura (B2) | non rimisurato; il ritiro della barra usa la stessa grammatica |
| Comparsa per modulazione della lente (B3) | non rimisurato; nessuna clip nuova |
| Tocco: crescita ×1,2, bagliore, rilascio (B4) | non rimisurato; in 269 Apple estende lo stesso comportamento al Mac |
| Controlli che salgono nel vetro (B5) | non rimisurato |
| Chiaro/scuro: tempi del passaggio (B6) | non rimisurato; vedi il dubbio in M3 sulla soglia |
| Tinta (A7) | non rimisurata; API identica |
| Lente al bordo e spessore con la taglia (A2, A4) | non rimisurati; in M1 la lente appare semmai più forte (a occhio) |
| Variante clear, oscuramento al 35% | documentazione invariata |
| Bordo duro e bordo morbido come stili espliciti (B7) | restano nell'API; non rimisurati su iOS 27 |
| Contenitore, identità, unione, transizioni del vetro | documentazione invariata |
| Tab bar: bolla di selezione, minimizzazione, accessorio, ricerca | documentazione e HIG invariate, salvo la scheda prominente |

«Non rimisurato» vuol dire che l'invarianza è **dedotta** (Apple elenca i cambi e questi non ci sono), non
verificata sui frame.

---

## Correzioni a R1 emerse da qui

- V5: il modificatore si chiama `toolbarMinimizationBehavior(_:for:)`; `toolbarMinimizeBehavior` è il nome
  della prima beta, sostituito (note di rilascio). In UIKit `UINavigationItem.navigationBarMinimization`
  sostituisce `barMinimizeBehavior` e `barMinimizationSafeAreaAdjustment`. Priorità: non più bassa, perché
  di serie accade in ogni pagina con ricerca principale.
- P4, P5: il «binding a un elemento» è un binding a un **dato** (`item:`), non a un elemento visivo.
- M11: il cursore sta in Impostazioni › Aspetto › Liquid Glass (fonti secondarie), non in Schermo.
- E1: «in basso sfuma senza sfocare» vale per iOS 26; per iOS 27 non c'è conferma.

---

## Cosa non ho trovato o non si può misurare

- **Keynote**: scaricati solo 18 s; il CDN dell'evento chiude i segmenti successivi. Il confronto del
  materiale viene dalla State of the Union, che mostra la stessa animazione.
- **iOS 27 definitivo**: tutte le misure sono su video di giugno 2026. Un cambio fra beta e 27.0 è noto
  (estremo trasparente del cursore, beta 5); altri non si possono escludere.
- **Tema scuro**: un solo fermo immagine in HD di una grafica (292). Mancano filo, ombra, corpo e bordo
  dello scorrimento in scuro. Mancava già in R4.
- **Bordo dello scorrimento in basso** in iOS 27: nessuna clip con contenuto ricco che scorre sotto una tab
  bar chiara. L'unico indizio dice «nessun effetto visibile».
- **Bordo dello scorrimento sopra testo fitto** e **su tablet**: non mostrati su iPhone; la fascia del Mac
  (SOTU 24:10) non è stata misurata.
- **Raggio di sfocatura del vetro**: si misura il contrasto che resta, non il raggio; il confronto M1 non è
  riducibile a una gaussiana.
- **Riflessi su un tasto tondo di iOS 27** e loro eventuale movimento: misurati solo capsule e un tondo su
  bianco, dove il riflesso non si distingue.
- **Soglia del cambio chiaro/scuro** in iOS 27: un'inquadratura ferma non basta.
- **Ombra sopra il testo** in iOS 27 (in iOS 26 era ×4,9): nessuna scena adatta.
- **Scheda prominente selezionata** e sua transizione; **barra minimizzata con scheda prominente**: descritte
  dalla documentazione, non mostrate.
- **Foglio in dissolvenza** (`.crossFade`): documentato, nessun video.
- **Durate proprie del ritiro della barra**: la clip è guidata da uno scorrimento programmato; con il dito
  i tempi seguono il gesto.
- **Azioni a scorrimento**: nessun confronto con iOS 26 e nessun tempo.
- **Posizione di serie del cursore** («a metà») e nome della voce nelle Impostazioni: solo fonti secondarie.
- **Misure in punti**: come in R4, tutto è in frazioni dell'elemento.
- **Sessioni non analizzate**: 250 e 251 (scaricate, solo trascrizione), 289 (AppKit), 321, 322, i Group
  Lab di SwiftUI (trascrizioni scaricate, non lette per intero).

## Fonti

Apple: [WWDC26 101](https://developer.apple.com/videos/play/wwdc2026/101/),
[102](https://developer.apple.com/videos/play/wwdc2026/102/),
[269](https://developer.apple.com/videos/play/wwdc2026/269/),
[278](https://developer.apple.com/videos/play/wwdc2026/278/),
[292](https://developer.apple.com/videos/play/wwdc2026/292/);
[note di rilascio di iOS e iPadOS 27](https://developer.apple.com/documentation/ios-ipados-release-notes/ios-ipados-27-release-notes);
[SwiftUI updates](https://developer.apple.com/documentation/updates/swiftui),
[UIKit updates](https://developer.apple.com/documentation/updates/uikit);
[HIG Scroll views](https://developer.apple.com/design/human-interface-guidelines/scroll-views),
[HIG Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars);
pagine di [ToolbarMinimizationBehavior](https://developer.apple.com/documentation/swiftui/toolbarminimizationbehavior),
[ScrollEdgeEffectStyle](https://developer.apple.com/documentation/swiftui/scrolledgeeffectstyle),
[ToolbarItemVisibilityPriority](https://developer.apple.com/documentation/swiftui/toolbaritemvisibilitypriority).

Secondarie: [MacRumors, 10 giugno 2026](https://www.macrumors.com/2026/06/10/how-liquid-glass-is-changing-in-ios-27/)
(riassunto delle dichiarazioni di Apple),
[9to5Mac, 15 giugno 2026](https://9to5mac.com/2026/06/15/ios-27-adds-new-liquid-glass-slider-on-iphone-heres-what-it-lets-you-do/),
[9to5Mac, 11 agosto 2026](https://9to5mac.com/2026/08/11/ios-27-beta-5-lets-you-make-liquid-glass-more-transparent-than-ever/),
[TechnoBezz](https://www.technobezz.com/how-to-change-liquid-glass-on-iphone).
