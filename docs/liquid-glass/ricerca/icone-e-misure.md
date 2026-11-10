# R6 · Peso delle icone e taglia dei tasti: Apple contro l'engine

Studio del 2026-11-10. Parte dalla richiesta di Alessio: icone «un po' più grassettate, come Apple», tasti «forse
leggermente più grandi», icone delle barre monocrome. Stesso metodo e stesse convenzioni di R4
(`riferimenti-misure.md`) e R5 (`ios27-differenze.md`): `fN` è un frame, le misure sono in pixel della sorgente 4K e
poi in frazioni dell'elemento; `D` è il diametro di un tasto tondo, `H` l'altezza di una barra. In più, qui, i **punti**:
vedi «La scala» sotto. Gradi delle fonti come in R1: **D** documentato da Apple, **W** detto in una sessione, **O**
osservato o fonte secondaria.

Immagine da mostrare in chat: `artifacts/liquid-glass/riferimenti/icone/confronto_barra.png` (la tab bar in
`…/icone/confronto_tabbar.png`).

## Cosa conta per il piano

1. **Il tratto delle icone dell'engine non è più sottile di quello di Apple.** Nelle barre: 1,9 dp contro 1,6–2,1 pt,
   cioè il 4,3% del tasto contro il 3,6–4,5%. L'impressione è giusta, ma la causa è un'altra.
2. **Le icone sono più piccole del 20–30%.** Alte in media 0,37 del tasto (15–18 dp in 44 dp) contro 0,44–0,48 di
   Apple (19–24 pt in 44 pt): a pari tratto, metà dell'inchiostro dentro lo stesso tondo.
3. **Il contrasto è un terzo.** Icone nel colore d'accento: 3,5–6,7:1 sul vetro. Apple, nere o bianche: 15–17:1. Nella
   tab bar l'engine attenua le icone non scelte al 60% (`FluidFloatingTabBar.kt:2153`): 4,8:1 contro 17:1.
4. **I tasti non sono più piccoli.** Vetro da 44 dp contro 44 pt misurati (iOS 27); sull'S25 a 2,625 px/dp sono
   7,06 mm contro 7,29 mm. Sembrano piccoli perché l'icona li riempie meno.
5. **Proposta per il peso**: icona a **0,6 del vetro** (26 dp nel tasto da 44; freccia indietro 24 dp) e inchiostro
   monocromo. Il tratto sale da solo a 2,2 dp (5,0% del tasto), già sopra Apple. Nessun ritocco alle sagome.
6. **Per l'aspetto**: un set curato `FluidIcons` (Material Symbols al tratto, peso 400 o 500, scelta di Alessio
   sull'immagine) per le icone dei componenti e i verbi comuni delle barre. L'ingrossamento automatico di un
   `ImageVector` regge fino a +0,6 dp, ma con le icone Material non serve: resta un'opzione, spenta di serie.
7. **Taglie proposte**: tasto 44 dp (invariato), bersaglio 48 dp (invariato), icona 26 dp, margine del vetro dal bordo
   16 dp (oggi 8; Apple 20 pt), tab bar 62 dp (oggi 60; Apple 63 pt) con icone da 28 dp piene di inchiostro ed
   etichette da 11 sp (oggi 24 dp e 13 sp; Apple 21–31 pt e 10 pt).
8. **Dove si innesta**: numeri in `FluidMetrics` (F3.5); componente e set in un pacchetto F4A.0 che non dipende dal
   campo e può partire durante F2; tab bar in F4B.3; `FluidGlassSize.iconSize` in F6.4; l'inchiostro è già in F2.3.

## La scala: come si arriva ai punti

R4 e R5 si fermano alle frazioni. Qui servono i punti, quindi la larghezza dello schermo va dichiarata.

| ripresa | larghezza dello schermo | righelli indipendenti | scala adottata | fiducia |
|---|---|---|---|---|
| **iOS 27**, WWDC26 269 @6:20 (`f11400`), @6:59 (`f12585`), @5:19 (`f9570`): telefono con cornice, classe B | 1377–1378 px (bordo interno della cornice nera, ±2 px) | testo del menu, maiuscola alta 37,5 px: a 17 pt dà 3,13 px/pt; margine basso della tab bar 65,5 px: a 21 pt (O) dà 3,12; tasto tondo 138 px: a 44 pt (D) dà 3,14; etichetta della tab bar 22,1 px: a 10 pt dà 3,14; barra di stato 193 px: a 62 pt dà 3,11 | **3,13 px/pt, schermo da 440 pt** (classe iPhone 17 Pro Max) | alta sulle frazioni, medio-alta sui punti (±2%) |
| **iOS 26**, WWDC25 219 @5:06 (`f9170`): telefono con cornice e dito, classe A | 1524 px | testo del menu, maiuscola alta 46,2 px: a 17 pt dà 3,86 px/pt, cioè 395 pt | **3,83 px/pt ± 0,05, schermo da 393–402 pt** | media (un righello solo, ±1,5%) |
| iOS 26, render ingranditi 219 `f28855`, `f8632`, `f9076` (classe C) e Posta `f16650` (classe B) | non nota | — | solo frazioni | — |

Il conto usa l'altezza delle maiuscole del San Francisco, 0,705 del corpo (a memoria, non riverificato in questa
sessione). Un righello contraddice gli altri: l'orologio della barra di stato di 269 è alto 41 px, che a 17 pt darebbe
uno schermo da 402 pt. Con 402 pt il tasto tondo sarebbe da 40,3 pt, sotto il minimo documentato di 44: si scarta, e il
corpo dell'orologio su quel modello resta ignoto. Se lo schermo di 269 fosse da 402 pt, tutti i punti di iOS 27 qui
sotto andrebbero moltiplicati per 0,914.

## Cosa documenta Apple

Da R1 (`inventario-ios.md`) e dalle pagine lette per questo studio.

| misura o regola | valore | grado |
|---|---|---|
| bersaglio minimo di tocco | 44×44 pt | D, HIG Buttons (R1 K2, G3) |
| altezze di barre e controlli, raggi, spaziature | non pubblicate | R1 G3 |
| icone delle barre | monocrome; la tinta colora solo il simbolo, lo stile prominente tinge lo sfondo | D+W (R1 B5) |
| simboli nelle barre | di sistema, senza bordo; testo e simboli mai nello stesso gruppo | D+W (R1 B10) |
| tab bar | simboli pieni; icone sopra le etichette in compatto | D (R1 T1) |
| tab bar: distanza dai bordi, etichette | 21 pt, 11 pt | O, Learn UI Design (R1 G3). Qui misurati 20,9 pt in basso, 18,7 pt ai lati, etichette da 10 pt |
| menu | icone sul bordo iniziale; testo 17 pt | D / O (R1 N3) |
| simboli delle barre: stile body, peso medium, scala large; lastra della barra alta 44 pt; gruppo della barra in basso alto 48 pt | — | O, **bassa**: rapporto di reverse engineering di UIKit 26.5 (innei.in), che si dichiara generato da un'IA. Coincide con le misure: 44,0 pt in alto, 47,8 pt in basso |
| pesi e scale dei simboli SF | nove pesi, tre scale | a memoria: la pagina HIG «SF Symbols» non si è lasciata leggere in questa sessione |

## Misure di Apple

Attrezzo: `tools/icone_misura.py` (riquadro al sub-pixel, area d'inchiostro, spessore del tratto dalla distanza dal
bordo sulla copertura ingrandita ×4). Su tratti sintetici da 6,5 e 9 px sbaglia meno di 0,1 px. La compressione
allarga i fili di 1–2 px in HD; qui si misura in 4K, dove un tratto è largo 5–8 px: l'errore sullo spessore è ±0,5 px
(±0,15 pt).

### Taglie assolute

| grandezza | misurato | in punti | incertezza | frame |
|---|---|---|---|---|
| tasto tondo, barra in alto (iOS 27) | 137–138 px, 0,100 dello schermo | **44,0 pt** | ±0,7 pt | 269 `f11400` |
| altezza della capsula, barra in alto (iOS 27) | 138 px | **44,0 pt** | ±0,7 pt | idem |
| tasto «…» (iOS 27) | 143×138 px | 45,7×44 pt: appena più largo che alto | ±0,7 pt | idem |
| tasto tondo flottante in basso (iOS 26) | 183 px, 0,120 dello schermo | **47,8 pt** | ±1,5 pt (scala) | 219 `f9170` |
| tasto tondo, barra in alto di Posta (iOS 26) | 168 px, 0,117 della scheda | 46–47 pt se la scheda è lo schermo intero | **bassa**: classe B, larghezza non certa | 219 `f16650` |
| margine del tasto dal bordo dello schermo, in alto | 63 px a sinistra e a destra | **20,1 pt** su 440 pt (0,046 dello schermo) | ±0,7 pt | 269 `f11400` |
| stesso, Posta iOS 26 | 0,32–0,36 D | 14–16 pt se D = 44 | bassa | 219 `f16650` |
| margini del tasto flottante in basso (iOS 26) | 115 px a destra, 107 px sotto | 30 pt e 28 pt | ±1,5 pt | 219 `f9170` |
| distanza fra due vetri vicini, barra in alto | 38 px | **12,1 pt** | ±0,7 pt | 269 `f11400` |
| bordo alto dei tasti dalla cima dello schermo | 193 px | 61,6 pt | ±1 pt | idem |
| capsula di tre icone (iOS 27) | 537 px = 3,89 D | 171,5 pt | ±1 pt | idem |
| passo fra le icone nella capsula (iOS 27) | 191–197 px = 1,41 D | 62 pt | ±1 pt | idem |
| passo fra le icone nella capsula (iOS 26, render) | 0,99–1,00 H; capsula di tre = 3,06 D | 44–48 pt | media | 219 `f28855`, `f8632` |
| capsula di due chevron (Posta, iOS 26) | 1,89 D, passo 0,78 D | — | media | 219 `f16650` |
| tab bar: altezza | 196 px | **62,6 pt** | ±0,7 pt | 269 `f9570` |
| tab bar: margine dal fondo dello schermo | 65,5 px | **20,9 pt** | ±0,7 pt | idem |
| tab bar: margini laterali | 58–59 px | 18,7 pt | ±0,7 pt | idem |
| tab bar: tondo a fianco, e distanza dalla capsula | 197 px; 23 px | 62,9 pt; 7,3 pt | ±0,7 pt | idem |
| tab bar: capsula di tre schede | 1030 px | 329 pt (≈110 pt a scheda) | ±1 pt | idem |
| tab bar: pillola della scheda scelta | alta 159 px | 50,8 pt (5,9 pt di margine sopra e sotto) | ±1 pt | idem |
| menu: passo fra le righe | 131,5 px (iOS 27); 161,1 px (iOS 26) | **42,0 pt** in entrambe | ±0,7 pt | 269 `f12585`, 219 `f9170` |
| menu: corpo del testo | maiuscola 37,5 px (iOS 27) | 17,0 pt a 3,13 px/pt | in iOS 26 è il righello stesso | 269 `f12585` |
| menu: larghezza (tre righe, iOS 27) | ≈780 px | ≈250 pt | **stima sul ritaglio**, ±10 pt | idem |
| tasto con etichetta: maiuscola / altezza del vetro | 173 / 536 px = 0,32 | — | **bassa**: render stilizzato | 219 `f9076` |

### Peso e taglia delle icone

Percentuali riferite a D o H dell'elemento che le contiene (44 pt in alto, 48 pt nel tasto flottante). «Inchiostro» è
l'area dell'icona sull'area di un disco di diametro D.

| icona | dove | larghezza × altezza | altezza / D | tratto | tratto / D | inchiostro | frame |
|---|---|---|---|---|---|---|---|
| chevron indietro | barra in alto, iOS 27 | 10,9×18,7 pt | 0,42 | **2,5 pt** | 5,7% | 4,5% | 269 `f11400` |
| chevron indietro | Posta, iOS 26 | 0,23×0,39 D | 0,39 | — | 5,1% | 4,2% | 219 `f16650` |
| foto (al tratto) | capsula, iOS 27 | 25,5×19,7 pt | 0,44 | 1,76 pt | 4,0% | 20% | 269 `f11400` |
| foto con freccia (piena) | capsula, iOS 27 | 29,9×21,9 pt | 0,49 | 2,1 pt | 4,7% | 26% | idem |
| strati sbarrati (al tratto) | capsula, iOS 27 | 22,3×24,1 pt | 0,55 | 1,60 pt | 3,6% | 14% | idem |
| borsa (piena) | tondo, iOS 27 | 19,1×21,6 pt | 0,49 | piena | — | 23% | idem |
| tre puntini | tondo, iOS 27 | larghi 19,9 pt, punti da 4,0 pt | — | — | 9,1% | 2,9% | idem |
| lampadina (al tratto) | tondo in basso, iOS 26 | 14,2×24,8 pt | 0,52 | 1,61 pt | 3,4% | 6,7% | 219 `f9170` |
| cartella, cestino, rispondi, invia | capsule, iOS 26, render | 0,43–0,51 × 0,42–0,51 H | 0,46 in media | — | 3,9–4,9% | 7–12% | 219 `f28855` |
| elenco, cuore, condividi, più | tondo e capsula, iOS 26, render | 0,35–0,50 × 0,35–0,53 D | 0,42 in media | — | 4,1–4,5% | 4–9% | 219 `f8632` |
| cerchio, triangolo | tondi, iOS 26, render | 0,43 × 0,40–0,43 H | 0,42 | — | 4,0% | 7–8% | 219 `f9076` |
| icone del menu | menu, iOS 27 | 17–23 × 15–18 pt | — | 1,3–1,6 pt | — | — | 269 `f12585` |
| icone del menu | menu, iOS 26 | 18,6–19,2 pt di lato | — | 1,4–1,6 pt | — | — | 219 `f9170` |
| palloncini, carrello (pieni) | tab bar, iOS 27 | 21,7×30,8 pt; 26,2×22,4 pt | 0,49; 0,36 di H | pieni | — | — | 269 `f9570` |
| faccina (al tratto) | tab bar, iOS 27 | 23,2×23,3 pt | 0,37 di H | 2,0 pt | 3,3% di H | — | idem |
| calendario (scheda scelta) | tab bar, iOS 27 | 23,0×20,8 pt | 0,33 di H | 2,1 pt | — | — | idem |
| etichette della tab bar | iOS 27 | maiuscola 7,1 pt | — | ≈1,0 pt | — | — | idem: corpo **10 pt** |

Cosa se ne ricava:

- **Barra in alto e in basso: icone al tratto**, tratto 1,6–2,1 pt (3,4–4,7% del tasto). Le sagome piene compaiono
  quando il simbolo scelto dall'app è pieno (borsa, foto con freccia): non è una regola del luogo. Il **chevron** del
  tasto indietro è più pesante degli altri: 5,1–5,7% (2,2–2,5 pt).
- **Tab bar: icone piene**, anche le non scelte, **tutte a inchiostro pieno** (nero 12 su vetro 238). La scheda scelta
  si distingue per il colore d'accento e per la pillola dietro, non per il peso.
- **Menu: le stesse icone a scala ridotta**: quelle della barra sono 1,3 volte quelle del menu, nel lato e nel tratto
  (foto: 25,5 pt e 1,76 pt in barra, 19,6 pt e 1,28 pt nel menu).
- **Taglia dell'icona**: 0,42–0,55 del tasto in altezza (media 0,48 in iOS 27, 0,44 in iOS 26). I render ingranditi di
  iOS 26 danno un tratto un po' più grosso (4,4%) dell'interfaccia vera (3,4–4,0%): sono classe C.
- iOS 26 e iOS 27 non differiscono in modo misurabile nel peso delle icone. Differisce il passo nella capsula (1,0 H nei
  render del 2025, 1,41 D nell'app del 2026): una sola barra per parte, non basta per dire che è cambiata la regola.

Ritagli: `artifacts/liquid-glass/riferimenti/icone/R6_apple_ios27_barra_269_f11400.png`,
`…/R6_apple_ios27_menu_269_f12585.jpg`, `…/R6_apple_ios26_barre_219_f28855_f8632.jpg`,
`…/R6_apple_ios26_menu_219_f9170.jpg`, `…/R6_apple_ios26_posta_219_f16650.jpg`; la tab bar è
`…/riferimenti/ios27/ritagli/T6_scheda_prominente_269_0519.jpg` (R5).

## Misure dell'engine

### Nel codice (3.1.2)

Le icone vengono da **Material Icons** di Compose: `api 'androidx.compose.material:material-icons-extended'`
(`engine-ui/build.gradle:51`), quindi le hanno anche le app. Niente Material Symbols, nessun vettore proprio.
`engine-ui` usa 55 icone diverse, quasi tutte `Icons.Rounded` (sagome piene), 13 `Icons.Outlined`; la galleria usa
`Icons.Rounded`. Sono `ImageVector` in un riquadro di 24 unità, con tratti da 2 unità: disegnate a 22 dp il tratto è
1,83 dp. La libreria è ferma: gli artefatti `material-icons-*` non escono più dopo Compose 1.7 e Google indica
Material Symbols (O, fonti secondarie).

| grandezza | valore | dove |
|---|---|---|
| vetro del tasto tondo | `Compact` 40, **`Regular` 44**, `Large` 54 dp | `FluidGlassControls.kt:77` (`FluidGlassSize`) |
| bersaglio di tocco | mai sotto 48 dp | `FluidGlassControls.kt:48`, `:89` |
| icona consigliata nel tasto | metà del vetro: 20, 22, 27 dp | `FluidGlassControls.kt:92` (`iconSize`) |
| tasto di vetro con testo | alto 48 dp, etichetta `bodyLarge` 17 sp, icona davanti 20 dp | `FluidGlassControls.kt:52`, `:104` |
| azione della barra in alto | vetro 44 dp, icona **22 dp**, tinta `primary` | `FluidScreen.kt:1695`–`1711` (`FluidBarAction`) |
| tasto indietro | vetro 44 dp, `ArrowBackIos` a **20 dp**, tinta `primary`, 6 dp dal bordo | `FluidScreen.kt:1652`–`1664` |
| riga dei controlli | alta 44 dp; azioni distanziate di 2 dp fra i riquadri da 48 | `FluidScreen.kt:408`, `:1460` |
| margine del contenuto | 20 dp | `FluidScreen.kt:425` |
| capsula flottante | alta 60 dp, azione 48 dp, icona 24 dp, 4 dp fra le azioni, 6 dp di margine interno | `FluidFloatingCapsule.kt:87`–`95`, `:370` |
| tab bar flottante | alta 60 dp (52 piegata, 64 da sola), scheda larga 88 dp, 16 dp dal fondo più la barra di sistema, 14 dp ai lati | `FluidFloatingTabBar.kt:3248`–`3284` |
| tab bar: icona, etichetta | 24 dp (dell'app); `labelMedium` 13 sp Medium | `FluidTabBar.kt:392`, `FluidFloatingTabBar.kt:3382` |
| tab bar: scheda non scelta | icona al **60% di opacità**, etichetta piena | `FluidFloatingTabBar.kt:2153`, `:2194` |
| menu | riga min 48 dp, testo `bodyLarge` 17 sp, icona 20 dp **a destra**, pannello 252 dp | `FluidMenuJourney.kt:506`–`510`, `FluidGlassMenuPanel.kt:435`–`447` |

Un tasto `FluidGlassIconButton` prende il contenuto dall'app: lì la taglia dell'icona è quella che l'app scrive
(24 dp se non scrive niente).

### Sugli screenshot

Gli screenshot di `artifacts/migrazione/` e `artifacts/beta5/qa/` sono 1080×2400 (Pampai 1080×2340). La densità si
ricava dal vetro: tasto largo 116 px, margine 21 px, distanza 15–16 px fra due vetri, che con i 44, 8 e 6 dp del codice
danno **2,625 px/dp** (schermo da 411 dp). Il nome del dispositivo non è scritto da nessuna parte: è una densità
dedotta, coerente su tutte le app. Misurati a quella scala:

| icona | app, tema | larghezza × altezza | altezza / D | tratto | tratto / D | inchiostro | contrasto sul vetro |
|---|---|---|---|---|---|---|---|
| freccia indietro (`ArrowBackIos`, 20 dp) | Pampa notes, Pampa Store, galleria | 9,0×15,7 dp | 0,36 | 2,15 dp | 4,9% | 2,9% | 3,6:1 chiaro, 5,8:1 scuro; 4,6:1 Pampa Store |
| matita (`Edit`, piena) | Pampa notes | 16,6×16,6 dp | 0,38 | piena | — | 6,2% | 3,5:1 chiaro, 5,8:1 scuro |
| tre puntini | Pampa notes | larghi 14,6 dp, punti da 3,5 dp | — | — | 8,0% | 2,1% | idem |
| aggiorna (`Refresh`) | Pampa Store | 14,6×14,6 dp | 0,33 | 1,89 dp | 4,3% | 5,3% | 4,4:1 chiaro, 4,7:1 scuro |
| cerca immagine | Codex, chiaro | 18,3×18,4 dp | 0,42 | 1,90 dp | 4,3% | 9,3% | 5,6:1 |
| aggiungi persona (piena) | Codex, chiaro | 20,3×14,6 dp | 0,33 | piena | — | 8,7% | 5,6:1 |
| ingranaggio (pieno) | Convert to it! | 17,8×18,4 dp | 0,42 | pieno | — | 13% | 4,5:1 chiaro, 6,7:1 scuro |
| cerca, condividi, cestino | galleria (beta 5), scuro | 15,6; 16,6×18,3; 12,9×16,6 dp | 0,36–0,42 | 1,89; 1,62 dp; pieno | 4,3%; 3,7% | 4,7–11% | — |
| menu, più, copia, condividi (senza vetro) | Pampai, chiaro | 11–17 dp | — | 1,35–1,9 dp | — | — | 4,2–7,2:1 (grigie) |
| cerca, strati, posizione (nere) | Fluid Transit, Fluidweather | 15–20 dp | — | 1,7–2,4 dp | — | — | 15–18:1 |
| scheda scelta (casa, piena) | tab bar, Pampa Store | 18,4×16,6 dp | 0,28 di H | piena | — | — | nera su pillola |
| schede non scelte | tab bar, Pampa Store, Codex | 14–18 × 16–18 dp | 0,27–0,30 di H | 2,1 dp o piene | 3,5% di H | — | **4,8:1** (grigio 110 su 248) |
| schede | tab bar, Fluid Transit | 17–22 dp | 0,27–0,37 di H | 1,9 dp o piene | — | — | 5:1 circa |
| nota, lente (al tratto) | tab bar e tondo, Fluidify | 23,5×25,3; 21,1 dp | 0,42 di H | 1,9; **1,63 dp** | 3,2%; 2,7% | — | 4,6:1; 16:1 |
| etichette | tab bar | maiuscola 9,5 dp (13 sp); Fluidify 7,6 dp | — | — | — | — | — |

Altre taglie sugli screenshot: vetro 116 px = 44,2 dp; margine del vetro dal bordo dello schermo 8 dp; distanza fra due
vetri 6 dp; tab bar alta 156 px = 59,4 dp, a 40 dp dal fondo dello schermo (16 di margine più 24 di barra di sistema).
La resa sintetica delle stesse sagome (`icone/prova_misure.txt`: cerca a 22 dp = 15,6 dp di lato, tratto 1,85 dp)
coincide con gli screenshot: l'attrezzo e il modello dicono la stessa cosa.

Ritagli: `artifacts/liquid-glass/riferimenti/icone/R6_engine_barre_in_alto.png`, `…/R6_engine_tabbar.png`,
`…/R6_engine_panoramica_1.jpg`, `…/R6_engine_panoramica_2.jpg`.

## Il confronto

1 pt e 1 dp sono qui la stessa unità: sono le unità in cui ciascun sistema disegna, e sullo schermo distano del 3–4%
(vedi sotto).

| grandezza | Apple (fiducia) | engine oggi | differenza |
|---|---|---|---|
| tasto tondo, barra in alto | 44,0 pt (alta) | 44 dp | **nessuna** |
| tasto tondo, in basso o flottante | 47,8 pt (media) | 48 dp (azione della capsula) | nessuna |
| tasto, frazione dello schermo | 0,100 di 440 pt; 0,109–0,112 su 393–402 pt | 0,107 di 411 dp | nessuna |
| bersaglio di tocco | 44 pt (D) | 48 dp | engine +9% |
| **icona, altezza / tasto** | 0,42–0,55; media 0,48 (iOS 27), 0,44 (iOS 26) (alta) | 0,33–0,42; media 0,37 | **engine −16…−23%** |
| icona, lato in unità | 19–24 pt | 15–18 dp | engine −20…−30% |
| **tratto delle icone al tratto** | 1,6–2,1 pt; 3,4–4,7% del tasto (alta) | 1,6–1,9 dp; 3,7–4,3% | **nessuna** |
| tratto del chevron indietro | 2,2–2,5 pt; 5,1–5,7% (alta) | 2,15 dp; 4,9% | engine −4…−14% |
| altezza del chevron | 17,3–18,7 pt; 0,39–0,42 D | 15,7 dp; 0,36 D | engine −9…−16% |
| inchiostro nel tasto, icone al tratto | 7–20% del disco (media) | 3–9% | engine circa la metà |
| **contrasto dell'icona sul vetro** | 15–17:1, nero o bianco (alta) | 3,5–6,7:1, accento | **engine da un terzo a un quarto** |
| margine del vetro dal bordo, in alto | 20,1 pt su 440 pt; 14–16 pt su iOS 26 (media) | 8 dp | **engine −8…−12** |
| distanza fra due vetri | 12,1 pt (alta) | 6 dp | engine −6 |
| passo fra icone vicine | 44–62 pt dentro una capsula | 50 dp fra vetri separati | l'engine non ha ancora il gruppo (F4A) |
| tab bar, altezza | 62,6 pt (alta) | 60 dp | engine −4% |
| tab bar, icone | 21–31 pt, 0,33–0,49 di H (alta) | 16–18 dp, 0,27–0,30 di H | **engine −25…−35%** |
| tab bar, icona non scelta | inchiostro pieno, 17:1 (alta) | 60% di opacità, 4,8:1 | **engine un quarto del contrasto** |
| tab bar, etichetta | 10 pt (alta) | 13 sp | engine +30% |
| tab bar, icona / maiuscola dell'etichetta | 3,2 | 1,8 | in Apple comanda l'icona, nell'engine l'etichetta |
| tab bar, dal fondo dello schermo | 20,9 pt (alta) | 40 dp con la barra dei gesti | engine +19 |
| tab bar, margini laterali | 18,7 pt (alta) | 14 dp (barra a tutta larghezza) | engine −5 |
| menu, altezza della riga | 42,0 pt (alta) | 48 dp | engine +14% |
| menu, testo | 17 pt | 17 sp | nessuna |
| menu, icona | 15–19 pt di sagoma, a sinistra | 20 dp di riquadro (≈15 dp di sagoma), a destra | taglia uguale; il lato è R1 N3 |
| menu, larghezza | ≈250 pt (stima) | 252 dp | nessuna |
| tasto con testo | maiuscola 0,32 dell'altezza (bassa) | 48 dp con 17 sp: 0,26 | non concludente |

### Il verdetto sulle due impressioni

**«Le icone sono meno grassettate»: l'impressione è giusta, la causa non è il tratto.** A pari tasto il tratto è lo
stesso (4,3% contro 3,6–4,5%). Si leggono peggio per tre ragioni che si sommano:

1. sono più piccole del 20–30%, quindi nello stesso tondo c'è circa metà dell'inchiostro;
2. sono nel colore d'accento: 3,5–6,7:1 contro 15–17:1. Un tratto colorato su un vetro della stessa famiglia sembra più
   sottile di quello che è. È la parte già decisa (icone monocrome);
3. nella tab bar le non scelte sono al 60% e sono piccole rispetto all'etichetta.

Dove il tratto è davvero più sottile è nelle app che portano icone proprie al tratto dentro vetri grandi: la lente di
Fluidify fa 1,63 dp in un tondo da 60 dp, il 2,7% contro il 3,4–4,5% di Apple (−30%). E Pampai mette icone grigie da
11–15 dp senza vetro attorno.

**«I tasti sono più piccoli»: no.** 44 dp contro 44 pt in alto, 48 contro 48 in basso. Sullo schermo: 44 pt su un
iPhone da 460 ppi sono 7,29 mm; 44 dp a 2,625 px/dp su un S25 (416 ppi) sono 7,06 mm, il 3% in meno, e a densità
maggiori il tasto cresce (a 3,0 px/dp sarebbero 8,06 mm). Quale densità usi l'S25 di Alessio non si è potuto leggere:
2,625 è quella degli screenshot. Sembrano più piccoli perché l'icona occupa 0,37 del vetro invece di 0,48, e perché
stanno a 8 dp dal bordo dello schermo dove Apple ne lascia 16–20.

## La proposta

### Il peso delle icone: quattro vie

Prove rese con Skia sulle sagome vere (`tools/icone_prova.py`): `icone/prova_ingrossamento.png`,
`icone/prova_symbols.png`, numeri in `icone/prova_misure.txt`.

| via | cosa dà | pro | contro | costo |
|---|---|---|---|---|
| **A · Stesse icone, più grandi e monocrome** (22 → 26 dp) | tratto 2,2 dp = 5,0% del tasto (Apple 3,6–4,5%); altezza media 0,44 D (Apple 0,44–0,48); contrasto 15:1 | nessuna app cambia le sue icone; è la causa vera misurata | le sagome piene di `Icons.Rounded` (ingranaggio, cestino, matita) restano piene: Apple nelle barre usa il tratto | due costanti e `iconSize`; il colore è già F2.3 |
| **B · Ingrossare ogni `ImageVector`** (tratto aggiunto sopra il riempimento) | +0,3 → 2,5 dp; +0,6 → 2,8 dp; +1,0 → 3,25 dp (a 26 dp) | vale per qualunque icona, anche quelle sottili delle app; si fa con API pubbliche: si ricostruisce il vettore dando a ogni tracciato `stroke` uguale al `fill`, `strokeLineWidth` e giunti tondi, in cache per (icona, peso) | **regge fino a +0,6**: a +1,0 i cursori del filtro si toccano, il foro dell'ingranaggio e la tacca della matita si chiudono, gli angoli si arrotondano tutti. Le icone piene ingrassano senza guadagnare lettura. Un'icona che tocca il bordo del riquadro verrebbe tagliata (serve un riquadro allargato) | ≈100 righe più test; nessun costo a regime (il pittore del vettore tiene il bitmap) |
| **C · Material Symbols con l'asse del peso** | al tratto: 400 = 2,2 dp, 500 = 2,5 dp, 600 = 2,9 dp, 700 = 3,4 dp (a 26 dp); asse FILL per vuoto/pieno | la famiglia è viva, al tratto come SF Symbols, il pieno serve alla tab bar | **come font** pesa 15 MB (va ridotto ai glifi usati), l'icona diventa un carattere e non un `ImageVector`: rompe `icon: ImageVector` in tutta l'API dell'engine. **Come vettori** esportati a peso fisso perde l'asse ma resta `ImageVector` | font: alto e invasivo. Vettori: vedi D |
| **D · Set curato dell'engine** (`FluidIcons`) | 25–30 icone Material Symbols Rounded esportate a un peso, vuote e (per la tab bar) piene, come `ImageVector` in `engine-ui` | i componenti dell'engine (indietro, chiudi, cerca, spunta, chevron, altro, cancella campo) cambiano in tutte le app senza toccarle; le app hanno i verbi comuni già nel peso giusto; un solo aspetto | le app devono scrivere `FluidIcons.X` al posto di `Icons.Rounded.X` per i loro verbi; le icone fuori dal set restano com'erano (con A sono comunque grandi e nere) | uno script di generazione più un file da 40–60 KB; licenza Apache 2.0 da aggiungere a `LICENSES` |

**Raccomandazione: A subito, D per l'aspetto, B come opzione spenta, C come font no.**

- **A** risolve da sola quello che si è misurato: con 26 dp e l'inchiostro monocromo il tratto è già sopra quello di
  Apple e l'icona ha la sua taglia. È la riga «Proposta A» dell'immagine di confronto.
- **D** porta le icone dei componenti e i dieci verbi delle barre (indietro, chiudi, cerca, più, condividi, altro,
  modifica, cestino, filtro, impostazioni) al tratto, come le barre di Apple. Peso: **400 se si vuole il tratto di
  Apple** (2,0–2,2 dp), **500 se si vuole «un po' più grassettate» di oggi** (2,5 dp = 5,7% del tasto, il peso del
  chevron di Apple esteso a tutte). È una scelta di gusto: riga «Proposta B» dell'immagine, a peso 500.
- **B** entra come parametro `weight` di `FluidIcon`, a zero di serie, con il tetto a +0,6 dp: serve alle app che
  hanno vettori propri sottili (Fluidify), non alle icone Material. Se nessuna app lo chiede, si può non fare.
- Nelle barre **al tratto**, nella tab bar **piene**: come Apple. Con Material Icons di oggi è il contrario (barre
  piene); con D si sistema.

### Le taglie

| grandezza | oggi | proposta | Apple | perché |
|---|---|---|---|---|
| vetro del tasto tondo `Regular` | 44 dp | **44 dp** | 44,0 pt | è già uguale. Se sul dispositivo, con le icone nuove, sembra ancora piccolo: 46 dp, che però alza la riga dei controlli (`ControlRowHeight` 44) |
| bersaglio di tocco | 48 dp | **48 dp** | 44 pt | non si scende |
| icona nel tasto (`FluidGlassSize.iconSize`) | metà del vetro: 20 / 22 / 27 | **0,6 del vetro: 24 / 26 / 32 dp** | 0,42–0,55 in altezza | la sagoma Material occupa ≈0,75 del riquadro: 26 dp danno 0,44 del vetro |
| icona di `FluidBarAction` | 22 dp | **26 dp** | 19–24 pt di sagoma | idem; sagoma 17–22 dp |
| freccia indietro | 20 dp | **24 dp** | 18,7 pt, tratto 2,5 pt | sagoma 18,8 dp, tratto 2,6 dp |
| inchiostro delle azioni | `primary` | monocromo adattivo (già deciso, F2.3) | nero / bianco | contrasto da 3,5–6,7:1 a 15:1 |
| margine del vetro dal bordo dello schermo | 8 dp | **16 dp** | 20 pt su 440 pt, 14–16 pt su iOS 26 | il vetro si avvicina al margine del contenuto (20 dp) invece di sporgere di 12 |
| distanza fra due vetri separati | 6 dp | **8 dp** ora, **12 dp** fra gruppi quando arriva il gruppo di F4A | 12,1 pt | con tre azioni sciolte 12 dp sono troppi su 360 dp |
| passo delle icone dentro un gruppo (F4A) | — | **44 dp** (vetro largo n × 44) | 1,0 H in iOS 26; 62 pt in una barra di iOS 27 | il valore di iOS 26, che sta su ogni larghezza; 62 è **bersaglio da rivedere** su altri frame di iOS 27 |
| azione della capsula flottante | 48 dp, icona 24 | 48 dp, **icona 26** | 47,8 pt | il tasto c'è già; cresce l'icona |
| tab bar, altezza | 60 dp | **62 dp** | 62,6 pt | ci stanno l'icona da 28 e una riga da 11 sp |
| tab bar, icona | 24 dp | **28 dp**, imposta dalla barra | 21–31 pt di sagoma | sagoma 19–23 dp; la barra impone la taglia come già impone lo stile dell'etichetta |
| tab bar, icona non scelta | opacità 0,6 | **opacità 1** | inchiostro pieno | la scelta la dicono pillola e accento |
| tab bar, etichetta | 13 sp Medium | **11 sp** Medium | 10 pt | sotto gli 11 sp l'italiano («Aggiornamenti», «Impostazioni») non guadagna abbastanza da giustificare la lettura peggiore |
| tab bar, margini | 16 dp dal fondo più la barra di sistema; 14 dp ai lati | invariati | 20,9 e 18,7 pt | i 40 dp dal fondo vengono dalla barra dei gesti di Android: è un'altra decisione, la segnalo soltanto |
| menu | riga 48 dp, testo 17 sp, icona 20 dp | riga e testo invariati, **icona 22 dp** | 42 pt, 17 pt, 15–19 pt | la riga resta sopra il bersaglio; l'icona segue il rapporto 1,3 con quella della barra (26 / 1,3 = 20: anche lasciarla a 20 è coerente) |
| tasto di vetro con testo | 48 dp, 17 sp | invariato | — | nessuna misura affidabile lo contraddice |

### Cosa cambia nelle app che aggiornano

- **Senza toccare codice**: le azioni di `FluidBarAction` e il tasto indietro hanno l'icona più grande e (con F2.3)
  monocroma; le barre rientrano di 8 dp per lato; la tab bar è 2 dp più alta, con icone più grandi e piene di
  inchiostro ed etichette più piccole. Sono cambi visibili: vanno annunciati nel CHANGELOG e portati ad Alessio come
  confronto prima di fonderli, come già scritto per l'inchiostro in F2.
- **Titolo agganciato**: con il margine a 16 dp lo spazio riservato ai tasti (`CompactTitleInset`, 76 dp) deve salire
  a 84 dp: un titolo lungo si tronca 16 dp prima.
- **`ContentInset` della tab bar** sale di 2 dp: chi usa la costante segue da solo, chi ha scritto 76 dp a mano resta
  corto di 2.
- **`FluidGlassIconButton` con un'icona propria**: non cambia finché l'app scrive la sua taglia. Per seguire: usare
  `FluidIcon` (prende la taglia dal vetro attorno) o `size.iconSize`.
- **Icone tinte a mano dall'app** (un `tint` esplicito) restano tinte: è la regola di F2.3.
- **Componente** proposto: `@Composable fun FluidIcon(imageVector, contentDescription, modifier, tint, weight: Dp = 0.dp)`
  che legge taglia e inchiostro dall'ambiente (`LocalFluidGlassSize`, `LocalContentColor`); `object FluidIcons` con il
  set. `Icon` di Material resta lecito.

### Dove si innesta nel piano

Proposte: il piano non è stato toccato.

- **Sezione 2**: la decisione c'è già («bersaglio da R6», pacchetto F4A.0). I valori sono le due tabelle qui sopra; le
  scelte ancora di Alessio sono tre: peso del set (400 o 500), vetro a 44 o 46 dp, etichetta della tab bar a 11 sp.
- **F1A**: nessun numero di R6 entra nel materiale. Una sola richiesta: le tavole di taratura di F1A.5 e la galleria
  di F1A.9 mettano sul vetro icone alla taglia e nell'inchiostro di R6 (0,6 del vetro, nere o bianche), altrimenti il
  confronto con i ritagli di Apple giudica anche le icone.
- **F2.3** (inchiostro monocromo): ci sta anche l'opacità 0,6 delle schede non scelte, che è la stessa questione.
  Se resta fuori perimetro (`FluidFloatingTabBar.kt` non è nel pacchetto), va in F4B.3.
- **F3.5** (`FluidMetrics`): le misure di questo documento come costanti con nome: vetro 44, rapporto dell'icona 0,6,
  margine 16, distanze 8 e 12, passo nel gruppo 44, tab bar 62, bersaglio 48. È logica pura come il resto di F3.5.
- **F4A.0** (pacchetto nuovo, prima di F4A.2, senza dipendenze dal campo: può partire durante F2 come F3.5):
  `FluidIcon`, `FluidIcons` con lo script che lo genera, le taglie in `FluidBarAction` e `FluidBackButton`, una pagina
  «Icone» in galleria con la tavola delle dieci icone. Perimetro: `ui/fluid/FluidIcon.kt`, `ui/fluid/icons/`,
  `ui/fluid/FluidScreen.kt` (le due taglie e i margini), `LICENSES`, un test sul vettore ingrossato. Serve Gradle.
- **F4A.2** (DSL della barra): `action(icon: ImageVector, …)` disegna con `FluidIcon`; il gruppo usa il passo di
  `FluidMetrics`.
- **F4B.1 e F4B.3**: icona della capsula a 26 dp; tab bar a 62 dp, icona 28 imposta dalla barra, etichetta 11 sp.
- **F5A**: icona del menu a 22 dp (o lasciata a 20).
- **F6 pacchetto F6.4** («stili, larghezza e taglia d'ambiente»): `FluidGlassSize.iconSize` a 0,6 del vetro e
  `LocalFluidGlassSize` come fonte della taglia per `FluidIcon`; lì si chiude anche la scelta 44 o 46 dp.

## Cosa non si può misurare

- **I punti di iOS 26 in alto**: l'unica barra in alto del 2025 con lo schermo intero (Posta, `f16650`) è una scheda
  senza cornice, classe B, e dà 46–47 pt se è larga quanto lo schermo. Il 44,0 pt viene da iOS 27.
- **Il modello del telefono**: dedotto dai righelli, non dichiarato da Apple. Un righello su sei (l'orologio) non torna.
- **Il peso nominale dei simboli** (medium, semibold): si misura lo spessore, non il nome del peso. «Medium, scala
  large» viene da una fonte secondaria di bassa fiducia.
- **Il tema scuro di Apple** per le icone: nei frame usati le barre sono su vetro chiaro. R4 A6 dà solo la luminanza
  (236 su vetro scuro).
- **Il tasto con etichetta**: un solo render stilizzato; corpo del testo e altezza in punti restano ignoti.
- **Il passo delle icone nel gruppo di iOS 27**: una barra sola, con simboli larghi; non basta per una regola.
- **La densità dell'S25 di Alessio** e quindi i millimetri veri dei tasti sul suo telefono: niente dispositivi in
  questo studio. I 2,625 px/dp sono quelli degli screenshot.
- **La resa vera delle proposte**: le righe «Proposta» dell'immagine sono screenshot ritoccati e le prove sono rese
  con Skia, non con Compose. Nessuna build. Antialiasing e arrotondamento ai pixel del dispositivo possono spostare
  un tratto di ±0,2 dp.
- **Le icone delle app fuori dagli screenshot**: misurate nove app su dodici, una o due schermate ciascuna. Non è un
  censimento delle icone di ogni app.
- **Le sorgenti delle app**: non sono in questo repo. Che libreria usino le icone di Fluidify e Pampai si deduce
  dall'aspetto, non dal codice.

## Fonti esterne usate

- [Learn UI Design, iOS design guidelines](https://learnui.design/blog/ios-design-guidelines-templates.html): 21 pt
  della tab bar, 44×44 pt, tavola delle larghezze degli schermi (440, 420, 402 pt). **O**.
- [Rapporto di reverse engineering di UIKitCore (innei.in)](https://innei.in/zh-TW/posts/tinkering/uikitcore-reverse-engineering-report):
  lastra da 44 pt, gruppo in basso da 48 pt, simboli body/medium/large. **O**, bassa: si dichiara generato da un'IA.
- [Apple Developer Forums, thread 796986](https://developer.apple.com/forums/thread/796986): tab bar di iOS 26 «62» in
  un contenitore da «83» (unità scritte come px). **O**; coincide con i 62,6 pt misurati.
- [google/material-design-icons](https://github.com/google/material-design-icons): i font delle prove (Apache 2.0).
- Stato di `material-icons-extended` dopo Compose 1.7: [Zenn, TimeTree](https://zenn.dev/timetree/articles/797d8e359edd72),
  [Kotlin Slack](https://slack-chats.kotlinlang.org/t/18867921/hi-why-will-the-material-icons-libraries-be-removed-are-ther).
  **O**: le note di rilascio di androidx non sono state rilette.
