# R7 · La sfocatura nei cambi e la matrice dei tasti di una barra

Ricerca del 2026-11-11 per il progetto «iOS in Android». Risponde a due domande di Alessio: come Apple usa la
sfocatura per rendere naturali i cambi (parte A), e come devono trasformarsi i tasti di una barra in tutte le
combinazioni, dentro una pagina e fra due pagine, compreso il gesto indietro (parte B). È l'ingresso di F4A.1
(le differenze fra due modelli della barra), di F3.4 (la grammatica del contenuto) e, per ciò che non sta in
F4A, di F4B e F5A.

Immagini e attrezzi: `artifacts/liquid-glass/ricerca/r7/` (in questo documento `r7/`). Fotogrammi e collage di
R4 e R5: `artifacts/liquid-glass/riferimenti/` (`rif/`). Gradi delle fonti come in R1: **D** documentato da
Apple (o da Google), **W** detto in una sessione WWDC, **O** osservato nei video di Apple, **T** terzi (non
Apple), **De** dedotto. Fiducia: **alta** (misurato su interfaccia vera o su più clip concordi), **media** (una
clip, o un render), **bassa** (a occhio, o una clip montata).

Convenzioni: `h` è l'altezza dell'icona, `H` l'altezza del vetro del tasto (44 dp nell'engine), `T` la durata
dello scambio del contenuto (dall'inizio all'icona nuova nitida). I tempi in ms vengono da clip a 29,97 fps:
±33 ms per estremo.

---

## Sommario

1. **La sfocatura dei cambi di Apple è uniforme, non di moto.** Su otto icone che escono ed entrano, una gaussiana
   con σ orizzontale e verticale liberi torna sempre σx ≈ σy. Anche l'icona che si sposta di 36 px in orizzontale
   mentre si sfoca resta rotonda, e un modello «tutto in orizzontale» spiega peggio (`r7/A3_isotropia_invia_cartella.png`).
   Quella che sembra «sfocatura di moto» è sfocatura uniforme più una scala piccola (1 → 0,9), l'opacità, e il vetro
   che cambia forma sotto. Fiducia alta.
2. **Si sfoca solo ciò che cambia identità.** Un'icona che resta, resta nitida anche quando corre: la lente della
   ricerca attraversa la barra a più di 100 px per fotogramma e non si sfoca (`r7/A5_…`). Fiducia alta.
3. **I numeri**:
   - σ cresce in modo accelerato fino a ≈0,15 h (0,14–0,17), cioè ≈3–4 dp su un'icona da 26 dp, al 45–55% di T;
   - per 70–100 ms le due icone stanno insieme nello stesso posto, entrambe sfocate, con opacità ≈0,35 ciascuna;
   - l'icona nuova torna nitida a T ≈ 430 ms (300–330 ms sulla clip col telefono);
   - la vecchia scende di scala a 0,9; la nuova sale da 0,93, e da 0,75 se nasce senza un predecessore.
4. **La tinta entra dentro la sfocatura.** L'icona che diventa l'azione principale prende il colore mentre è
   sfocata (a ≈130 ms), non dopo. Fiducia alta.
5. **Fra due pagine i tasti non viaggiano con la pagina: si trasformano sul posto, su un orologio loro.** Nel push
   della sessione 256 un tondo solo diventa un tondo più un gruppo di due: il gruppo **nasce dal fianco del tondo
   con un collo** che si rompe a ≈400 ms. L'icona del tondo cambia per sfocatura e il tasto indietro compare per
   lente, sul posto (`r7/B1_…`). Fiducia alta.
6. **Gesto indietro.** Nella demo di UIKit (sessione 284) i tasti restano quelli della pagina in cima per tutto il
   trascinamento, anche quando la pagina sotto è quasi tutta visibile. Si trasformano **solo al rilascio che
   conferma** (`r7/B2_pop_interattivo_…`). È la proposta di Alessio, ed è il contrario di ciò che fa l'engine oggi,
   dove il morph segue il dito. Fiducia media: una clip montata, più un'osservazione di terzi concorde.
7. **Accoppiamento fra due barre**: per `id`, poi per ruolo, poi per simbolo uguale, poi per ordine dall'esterno
   sullo stesso lato, mai attraverso il centro. Chi nasce nasce dal vicino dello stesso lato; se il vicino non c'è,
   compare per lente. Chi muore perde prima il contenuto e poi il vetro.
8. **Cosa manca nell'engine oggi**:
   - gli scambi di icona sono una dissolvenza con un buco in mezzo, e anche un'icona uguale nelle due pagine
     lampeggia;
   - i tasti nuovi nascono da un seme di un pixel sul proprio centro;
   - non c'è identità: l'accoppiamento va solo per lato e ordine;
   - nella stessa pagina non c'è nessuna animazione.
9. Le regole: dieci per il contenuto (A.6) e diciassette per il modello della barra (B.9), ognuna con fonte e fiducia.
   Le manopole di gusto sono raccolte in un posto solo.
10. Quattro domande per Alessio, a scelta multipla e mostrabili in video (B.10): gesto indietro, icona uguale in
    vetri diversi, dove nasce un tasto, cambio di scheda.

---

## Il materiale usato

| sorgente | cosa mostra | classe (R4) | dove |
|---|---|---|---|
| WWDC25 219, screenshot scelti a mano «Tasti che si trasformano» (25 PNG 2559×1439) | due capsule di una barra che cambiano contesto: (cartella, cestino) → (graffetta, A cerchiata, più) e (rispondi, invia) → freccia tinta | B | abbinati qui ai fotogrammi **f28856…f28879 consecutivi più f28888** (`r7/tools/abbina2.py`, confronto sulla sola fascia della barra contro il 4K): il passo è di 33,4 ms, quindi i tempi si conoscono |
| WWDC25 219, video intero | barra col dito (4:44), «Select» → tre lobi (5:01), menu, comparsa, tocco | A, B, C | `rif/collage/` (R4) |
| WWDC25 256 «What's new in SwiftUI», 2:23–2:25 | **push** da «Trips» (un tondo con il filtro) a una pagina con indietro, gruppo ˄˅ e «…» | B | scaricato in `r7/video/` |
| WWDC25 256, 4:04 | la scheda di ricerca che diventa campo, la tab bar che si chiude nel tondo | B | idem |
| WWDC25 284 «Build a UIKit app with the new design», 12:15–12:40 | Note: push, **gesto indietro interattivo** con l'indicatore del dito, ritorno | B, montata | idem |
| WWDC25 284, 13:46 e 14:07 | menu da un gruppo della barra in basso su una foto; foglio che nasce da un tasto della barra | B | idem |
| WWDC25 323 | trascrizione (barre, spaziatori, badge) | — | trascrizione |
| WWDC26 278 (R5) | la barra che si ritira scorrendo, il titolo in linea | B | `rif/ios27/collage/` |

**Metodo per la parte A.** Per ogni icona, l'immagine nitida (prima o dopo il cambio) è stata adattata al fotogramma
osservato con il modello `oss = α · G(σx, σy) ∗ scala_s(rif) + β`, dove G è una gaussiana con σ orizzontale e
verticale separati. La ricerca è su una griglia (σ 0–32 px, scala 0,7–1,4), e il miglior modello isotropo si
confronta col migliore libero (`r7/tools/fit_blur.py`, uscite in `r7/lavoro/fit_blur.txt`). Nei fotogrammi in cui
le due icone stanno insieme, il modello ha due icone sotto la stessa gaussiana, con due opacità
(`r7/tools/fit_incrocio.py`, `r7/lavoro/fit_incrocio.txt`). Il controllo di partenza: sul fotogramma nitido
contro sé stesso torna σ = 0, scala 1, R² = 1.

---

## Parte A · Come Apple usa la sfocatura nel movimento

### A.1 Dove compare

| cambio | sfocatura? | fiducia | prova |
|---|---|---|---|
| un'icona ne sostituisce un'altra nello stesso tasto | **sì**: uniforme, l'una dentro l'altra | alta | `r7/A1_scambio_icone_gruppo_sinistra_f28856-28888.jpg`, `r7/A2_gruppo_in_tasto_tinto_destra_f28856-28888.jpg`; push 256 (filtro → «…»), `r7/B1_push_un_tasto_diventa_due_256_f4300-4318.jpg`; `rif/collage/B2_barra_telefono_f8536-8570.jpg` (lista → lente) |
| icone che entrano in una barra (vetro nuovo o capsula che si allarga) | **sì**: arrivano sfocate, più piccole e chiare | alta per la capsula che si allarga (il «più», `r7/A1_…`), media per il gruppo nato (256: ˄˅ arrivano piccole e chiare, poco sfocate a occhio) | `r7/A1_…`, `r7/B1_…` |
| icone che escono da una barra | **sì**: si sfocano sul posto e il bordo del vetro che si ritira le copre; non scivolano via | alta | `r7/A2_…` (rispondi), `rif/collage/B2_barra_telefono_f8536-8570.jpg` (cuore e condividi) |
| un'icona che resta nei due stati | **no**: resta nitida e si sposta col suo vetro | alta in uno stesso contesto (il «più» di 219 4:44 e 4:48); **ma** nelle transizioni di navigazione di UIKit un «…» presente nelle due pagine si sfoca insieme al gruppo che cambia (284) | `rif/collage/B2_barra_telefono_…`; `r7/B2_pop_interattivo_284_f22247-22421.jpg` |
| un titolo che cambia | **titolo grande**: no, è contenuto e scorre con la pagina. **Titolo in linea**: sì quando la barra si ritira; fra due pagine non si vede | alta (grande), media (ritiro), non osservato (fra pagine) | `r7/B1c_push_pagina_intera_256_f4298-4322.jpg`; `rif/ios27/collage/V5_ritiro_denso_278_f20258-20293.jpg` |
| il contenuto di un menu che nasce | **sì**: le voci sono dentro dal primo fotogramma, sfocate e piegate dalla lente, nitide a ≈50–70% del viaggio | alta (R4 B1) | `rif/collage/B1_menu_foto_f13036-13075.jpg`, `r7/A6_menu_dal_gruppo_sfondo_nitido_284_f24773-24803.jpg` |
| il contenuto di un foglio che nasce da un tasto | **sì**: dentro dal principio, sfocato, nitido a ≈300 ms | media (una clip) | `r7/A7_foglio_dal_tasto_284_f25408-25424.jpg` |
| le etichette della tab bar | **sì** quando la tab bar si chiude nel tondo per la ricerca («Summary», «Sharing» si sfocano in ≈100 ms). Cambio di scheda: **non osservato** | alta / — | `r7/A5b_ricerca_da_tab_bar_a_campo_256_f7314-7343.jpg` |
| il contenuto sotto un menu o un foglio | **no** (iOS 26): sotto il menu la foto resta nitida e non si scurisce; sotto il foglio la pagina si scurisce, non si sfoca. Il menu contestuale scurisce soltanto (R1 X2) | alta | `r7/A6_…`, `r7/A7_…`. La decisione di Alessio (sfocatura leggera sotto il menu contestuale) resta, per scelta |
| i tasti di una barra che si ritira | **sì**: titolo e tasti si sfocano e svaniscono seguendo lo scorrimento | media (R5 V5) | `rif/ios27/collage/V5_ritiro_denso_278_f20258-20293.jpg` |
| il testo di un tasto che cambia | **sì**: «Select» si sfoca prima che il vetro si divida; il segnaposto «Search» arriva sfocato | media (render rallentato) / alta (256) | `rif/collage/B2_select_done_f9029-9076.jpg`, `r7/A5_ricerca_lente_che_viaggia_nitida_256_f7320-7328.jpg` |
| un numero o un badge | `numericText`: sfocatura quasi isotropa misurata da terzi (σ = 0,125 h all'inizio). Nei widget il testo cambia «con transizioni sfocate di serie». Badge che compare: non osservato | **T** (misura di terzi), **D** (widget) | [react-native-numeric-text, METHODOLOGY](https://github.com/AmatoGiulio/react-native-numeric-text), [WidgetKit](https://developer.apple.com/documentation/widgetkit/animating-data-updates-in-widgets-and-live-activities) |

### A.2 Che sfocatura è

**Uniforme.** La tabella riassume l'adattamento (σ in px dello screenshot; icone alte 100–121 px, vetro H = 235 px).
La colonna «R² isotropo» è lo scarto del miglior modello con σx = σy; «R² libero» quello con σx e σy separati.

| icona | fotogramma | σx | σy | R² libero | R² isotropo |
|---|---|---|---|---|---|
| cartella (esce) | +67 / +100 / +133 ms | 4 / 7 / 12 | 4 / 8 / 14 | .984 / .916 / .826 | .984 / .916 / .825 |
| cestino (esce) | +100 / +133 / +167 ms | 7 / 10 / 14 | 7 / 10 / 12 | .981 / .986 / .990 | .981 / .986 / .990 |
| rispondi (esce) | +67 / +100 ms | 4 / 7 | 4 / 7 | .963 / .801 | .963 / .801 |
| invia (esce, **si sposta di +36 px in x**) | +67 / +100 / +133 ms | 4 / 7 / 10 | 4 / 7 / 10 | .966 / .925 / .901 | stessi |
| graffetta (entra) | +267 / +334 / +434 ms | 12 / 7 / 3 | 12 / 8 / 3 | .975 / .971 / .969 | .975 / .969 / .969 |
| A cerchiata (entra) | +267 / +334 ms | 18 / 8 | 14 / 8 | .900 / .875 | .897 / .875 |
| più (nasce) | +267 / +334 ms | 10 / 6 | 10 / 7 | .857 / .957 | .857 / .956 |
| freccia (entra, tinta) | +267 / +334 ms | 14 / 8 | 16 / 12 | .832 / .760 | .832 / .751 |
| incrocio, due icone insieme (tre posti) | +200 / +234 ms | 16 / 16–18 | 16 / 16–18 | .90–.96 | identici al libero |

Il modello libero non batte mai l'isotropo di più di 0,01 di R². Nella prova più severa, «invia» si sposta in
orizzontale di ≈9 px per fotogramma mentre si sfoca: una scia di moto lungo lo spostamento sarebbe al più di
≈9 px (σ ≈ 2,6 px), contro i 10 px di σ misurati in tutte le direzioni. La figura
`r7/A3_isotropia_invia_cartella.png` mette accanto l'osservato, la gaussiana uniforme e la stessa sfocatura tutta
in orizzontale: la terza non somiglia all'osservato, e lo scarto lo dice (R² .889 contro .775; .738 contro .341
per la cartella).

**La sfocatura non si lega al moto.** Nella stessa sessione 256, nel passaggio dalla scheda di ricerca al campo, la
lente attraversa la barra a più di 100 px per fotogramma ed è nitida in ogni fotogramma. Si sfocano solo il
segnaposto «Search» che arriva e le etichette che se ne vanno (`r7/A5_…`, f7321–f7327). Nel push di 256 il tondo
si sposta di ≈0,25 D e poi torna, e l'icona dentro si sfoca perché cambia, non perché si muove.

**La documentazione dice la stessa cosa.**
- `Transition.blurReplace`: «combining blurring and scaling effects», con due configurazioni che riguardano il
  verso della **scala** (`downUp`: rimpicciolisce uscendo e cresce entrando; `upUp`), non uno spostamento. Nessun
  raggio, nessuna durata documentati.
  [blurReplace](https://developer.apple.com/documentation/swiftui/transition/blurreplace) (D),
  [configurazioni](https://developer.apple.com/documentation/swiftui/blurreplacetransition/configuration-swift.struct) (D).
- `symbolEffect(.replace)` e `ReplaceSymbolEffect`: «predefined **scaling** animations» (down-up, up-up, off-up,
  per strato o simbolo intero). Magic Replace è il comportamento di serie da iOS 18 e anima le parti comuni fra due
  simboli (barre oblique, badge); da iOS 26 usa anche Draw Off/Draw On. Non è documentata nessuna sfocatura
  ([ReplaceSymbolEffect](https://developer.apple.com/documentation/symbols/replacesymboleffect), D;
  [HIG SF Symbols](https://developer.apple.com/design/human-interface-guidelines/sf-symbols), D;
  [WWDC24 10188 @5:13](https://developer.apple.com/videos/play/wwdc2024/10188/), W;
  [WWDC25 337 @6:28](https://developer.apple.com/videos/play/wwdc2025/337/), W). Quindi lo scambio nei tasti
  della barra che si vede nei video **non è** il Replace dei simboli. È una sostituzione del contenuto del vetro
  con sfocatura, scala e opacità, cioè la famiglia di `blurReplace` (De).
- `ContentTransition` non ha nessun membro con sfocatura
  ([ContentTransition](https://developer.apple.com/documentation/swiftui/contenttransition), D). La sfocatura è
  scritta solo per il testo dei widget («blurred content transitions by default», D).
- `glassEffectTransition(.materialize)` «will fade in content and animate in or out the glass material»; entro la
  spaziatura vale di serie `.matchedGeometry`, e «the system applies more than opacity changes»
  ([GlassEffectTransition](https://developer.apple.com/documentation/swiftui/glasseffecttransition),
  [Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views), D).
- La sfocatura di moto vera Apple la nomina una volta, per l'apertura delle app: «motion blur basically bakes in
  more information in each frame about the movement», insieme all'allungamento
  ([WWDC18 803 @19:51](https://developer.apple.com/videos/play/wwdc2018/803/), W). È l'oggetto che vola dall'icona
  alla finestra, non un controllo.

**Cosa si riesce a distinguere e cosa no.** A 29,97 fps una scia corta di un fotogramma (≤ 1/30 s) non si separa
dalla sfocatura uniforme quando lo spostamento per fotogramma è piccolo rispetto a σ. Qui lo è per tutte le icone,
salvo la lente della ricerca, che però è nitida. In alcune clip la compressione o la fusione di fotogrammi dà
immagini doppie (f7324 di 256: la lente ha un contorno doppio in un solo fotogramma). Sembra moto, ma è montaggio:
nessuna regola ne deve tenere conto. Conclusione per il progetto: **non serve uno shader di sfocatura direzionale.**
Basta una gaussiana sul layer del contenuto, che è anche la soluzione meno costosa.

### A.3 Quanto e quando

Le curve misurate stanno in `r7/A4_curve_scambio_icone.png`. In tabella, per la clip di 16:02 (classe B, T ≈ 434 ms)
e in frazione di T:

| tempo | frazione di T | icona vecchia | icona nuova | vetro |
|---|---|---|---|---|
| 0 | 0 | nitida | — | comincia a cambiare larghezza nello stesso fotogramma (R4 B2 c) |
| 33 ms | 0,08 | σ 0,02 h, α 0,92 | — | |
| 67 ms | 0,15 | σ 0,04 h, α 0,88 | — | il bordo esterno della capsula che si allarga rientra fino a 0,17 H prima di uscire (R4: 0,19 H); quello della capsula che si chiude resta quasi fermo (+0,11 H) |
| 100 ms | 0,23 | σ 0,065 h, α 0,80 | — | il corpo schiarisce (a occhio) |
| 133 ms | 0,31 | σ 0,10 h, α 0,75, scala 0,95; **la tinta entra qui** | — | |
| 167 ms | 0,38 | σ 0,13 h, α ≈0,45–0,75, scala 0,9 | compare: α ≈0,2, σ come la vecchia | |
| 200–234 ms | 0,46–0,54 | **σ massima 0,14–0,17 h**; α 0,12–0,27 | α 0,37–0,59 | **incrocio**: in due posti su tre a ≈190 ms, nel terzo a ≈255 ms |
| 267 ms | 0,62 | α ≤0,1 (in un posto 0,35) | σ 0,10–0,14 h, α ≈0,6, scala 0,93 (0,75 se nasce) | |
| 334 ms | 0,77 | — | σ 0,07 h, α 0,78 | la capsula si allarga ancora |
| 434 ms | 1,0 | — | σ ≤0,03 h (nitida), α 0,85 | |
| 500 ms | 1,15 | — | contrasto pieno | larghezza al massimo +1,3% a 600–900 ms (R4) |

Altri tempi, dalle altre clip:
- **Telefono col dito** (219 4:44, classe A, la più affidabile per i tempi): tutto lo scambio in 270–330 ms (R4 B2 a).
  La forma del cambio è la stessa.
- **Push di 256**: il contenuto del tondo si sfoca dal primo fotogramma, il gruppo nuovo nasce a ≈200–233 ms, il
  collo si rompe a ≈400 ms e tutto è fermo a ≈470–530 ms.
- **Comparsa per lente** (R4 B3): 430 ms; il simbolo si condensa da 2× a 1×.
- **Chi parte prima.** Il contenuto vecchio e la larghezza del vetro partono insieme. Le nascite di un vetro nuovo
  partono dopo, quando il contenuto vecchio è già sfocato (≈0,45 T nel push di 256). Il contenuto nuovo è l'ultimo
  a mettersi a fuoco.
- **Sovrapposizione.** Le due icone sono visibili insieme per ≈70–100 ms, nel punto di massima sfocatura. La somma
  delle opacità all'incrocio è ≈0,7, non 1: c'è un leggero calo di inchiostro, coperto dalla sfocatura.
- **Posizione.** L'icona non scivola per conto suo: si sposta di pochi pixel insieme al vetro che cambia forma.
  Quella che esce viene coperta dal bordo del vetro che si ritira (rispondi, `r7/A2_…`, f28861–f28864).

In unità dell'engine (icona 26 dp, vetro 44 dp) σ massima vale ≈3,6–4,4 dp. `RenderEffect.createBlurEffect` vuole
un *raggio*, che hwui converte in σ come 0,57735 · r + 0,5 (De: da verificare nel sorgente di hwui prima di usarlo):
il raggio da passare è ≈5,5–7 dp.

### A.4 Perché funziona, e quando Apple non la usa

**Cosa nasconde.** Il salto fra due forme che non hanno niente in comune (una cartella e una graffetta). Al massimo
della sfocatura tutte e due sono una macchia della stessa taglia e dello stesso colore, e l'occhio non vede il
momento in cui una diventa l'altra. Una dissolvenza nitida mostrerebbe due icone sovrapposte e leggibili, cioè un
errore. La sfocatura serve anche a coprire il calo di opacità dell'incrocio.

**Cosa lascia vedere.**
- La continuità del vetro: il vetro non si sfoca mai e cambia forma in continuo.
- La posizione: la macchia resta dove sta il tasto.
- Il colore: la tinta entra mentre la forma è ancora indistinta, così il colore arriva prima della forma, come fa
  l'occhio.
- Ciò che resta uguale: le icone che non cambiano non si toccano, e diventano i punti fermi su cui l'occhio
  ricostruisce il cambio.

**Quando Apple non la usa:**
- **quando le forme sono parenti**: due vetri che si fondono o si dividono, un tasto che diventa menu, un tasto che
  diventa foglio. Lì si trasforma la sagoma (collo, goccia, crescita), e solo il *contenuto* nuovo arriva sfocato
  dentro la lente (R4 B1, B2 d);
- **quando l'identità resta**: la lente della ricerca che viaggia, il «più», il tasto indietro fermo fra due
  pagine;
- **quando è il contenuto della pagina a muoversi**: titoli grandi, liste e foto scorrono con la pagina, nitidi;
- **per il cambio chiaro/scuro**: un incrocio di colore che passa per il contrasto zero, senza sfocatura (R4 B6);
- **per lo sfondo** sotto menu e fogli (iOS 26): niente sfocatura, al più uno scurimento.

### A.5 Altri usi della sfocatura nel movimento

| uso | cosa si vede | prova o fonte | fiducia |
|---|---|---|---|
| titolo grande che si aggancia | il titolo in linea compare in ≈100–200 ms quando il grande passa sotto la barra; dai fotogrammi non si distingue se con sfocatura | R5 E1, `rif/ios27/collage/E1_bordo_scorrimento_278_1151-1201_ogni10.jpg` | bassa sulla sfocatura |
| bordo dello scorrimento | «a subtle blur and fade effect applied to content under system toolbars»: il contenuto si dissolve, sfocandosi, verso il bordo | [WWDC25 323 @10:40](https://developer.apple.com/videos/play/wwdc2025/323/) (W), R4 B7 | alta |
| barra che si ritira e ritorna | titolo e tasti si sfocano e svaniscono seguendo lo scorrimento; tornando, il titolo «si condensa dalla sfocatura» | R5 V5 | media |
| comparsa del vetro | non per opacità: la lente si accende e il simbolo si condensa da 2×, sfocato, a 1× | R4 B3, `rif/collage/B3_comparsa_play_f5397-5420.jpg` | media |
| cambio di scheda | non osservato | — | — |
| ricerca che si apre | la tab bar si chiude nel tondo con le etichette sfocate, il segnaposto arriva sfocato, la lente viaggia nitida (la tastiera non è nella clip) | `r7/A5b_…`, `r7/A5_…` | alta |
| menu che nasce | voci dentro dal primo fotogramma, sfocate e piegate dalla lente; la barra sorgente perde le icone sfocandole in 67–100 ms | R4 B1, `r7/A6_…` | alta |
| foglio che nasce da un tasto (zoom) | contenuto dentro sfocato mentre il vetro cresce, nitido a ≈300 ms; pagina dietro scurita, non sfocata | `r7/A7_…` | media |
| testo dei widget, numeri | sfocatura di serie per il testo che cambia (widget, D); `numericText` quasi isotropo (T) | vedi A.1 | D / T |

### A.6 Le regole del contenuto (per il motore)

Valgono per il contenuto (icone, testo, colore) dentro un vetro che resta, nasce o muore. La forma del vetro è
affare di F3. T = durata dello scambio (di serie 400 ms; 330 ms sul telefono di Apple, 430 ms sulla clip ingrandita).

| # | regola | fiducia, fonte |
|---|---|---|
| A1 | **Scambio di contenuto in un vetro che resta.** Si sfoca e si scambia solo un contenuto con una chiave di contenuto diversa (altro simbolo, altro testo, altro colore). Chi esce: σ da 0 a 0,15 h con andamento accelerato (≈0,04 h a 0,15 T, 0,10 h a 0,3 T, 0,15 h a 0,5 T); opacità 1 → 0,75 a 0,3 T → 0 a ≈0,62 T; scala 1 → 0,9 dopo 0,25 T. Chi entra: presente da ≈0,38 T con σ uguale a quella di chi esce, opacità che incrocia quella vecchia a ≈0,45–0,5 T (α ≈0,35 ciascuna), σ che scende quasi linearmente a 0 entro T, scala 0,93 → 1, contrasto pieno a ≈1,15 T | alta (R7 A.3, R4 B2) |
| A2 | **La sfocatura è uniforme** (gaussiana, σx = σy), anche se il contenuto si sposta. Niente scie | alta (A.2) |
| A3 | **Ciò che resta non si sfoca.** Stessa chiave di contenuto nei due stati: nitido, opacità 1, si sposta col suo vetro, anche veloce | alta (256 4:04, 219 4:44) |
| A4 | **Il colore cambia dentro la sfocatura.** Una tinta che arriva o se ne va (azione principale, colore dell'icona) si incrocia fra ≈0,25 T e ≈0,5 T, mentre σ è sopra 0,08 h; il corpo tinto del vetro segue la stessa finestra | alta per il simbolo (f28861), media per il corpo |
| A5 | **Il contenuto sta al suo posto d'arrivo e il vetro lo ritaglia.** Chi esce resta dov'era e viene coperto dal bordo che si ritira; chi entra in una parte nuova del vetro appare dove starà. Nessun contenuto si stira né scivola per conto suo (invariante 23) | alta (`r7/A2_…`) |
| A6 | **Contenuto senza predecessore** (un'icona che nasce nella parte nuova di una capsula, o il contenuto di un vetro nato): entra con la stessa curva di A1, ma da scala 0,75 | media (il «più»: scala 0,75 → 1) |
| A7 | **Contenuto di un vetro che nasce grande** (menu, foglio): dentro dal primo fotogramma, alla scala d'arrivo, sfocato, piegato dalla lente nel menu; nitido a 0,5–0,7 del viaggio | alta (R4 B1), media (foglio) |
| A8 | **Contenuto di un vetro che compare o sparisce da solo** (comparsa per lente): entrando il simbolo si condensa da scala 2 e sfocato a 1 e nitido fra 0,25 e 0,85 della comparsa; uscendo se ne va nei primi 130 ms, prima che la lente si spenga | media (R4 B3) |
| A9 | **Il testo segue le icone** (stessa curva, σ in frazione dell'altezza del carattere); il titolo in linea che cambia fra due pagine aggiunge un passo di ≤8 dp nel verso della navigazione | media per il testo che cambia; **De** per il passo |
| A10 | **Sfocatura guidata da un gesto o da uno scorrimento** (barra che si ritira, bordo dello scorrimento): stessa sfocatura uniforme, ma con il progresso del gesto al posto del tempo; è l'unico caso in cui una sfocatura si ferma a metà | media (R5 V5) |

**Manopole** (scelte di gusto, da mostrare ad Alessio con valori vicini a quelli di Apple):
- σ massima: 0,10 / **0,15** / 0,20 h;
- T: 300 / **400** / 450 ms;
- durata dell'incrocio: le due icone insieme per 50 / **80** / 120 ms;
- calo di opacità all'incrocio: somma 0,6 / **0,7** / 1,0;
- scala di chi esce: **0,9** / 1,0;
- scala di nascita: **0,75** / 0,9;
- quanto schiarisce il vetro durante il cambio: nulla / **appena** (a occhio);
- sfasamento fra i posti di una barra: **0** (Apple non ne ha uno sistematico: un posto su tre in ritardo di ≈60 ms) / 30 ms dall'esterno.

---

## Parte B · La matrice dei cambi dei tasti di una barra

### B.1 Il vocabolario

**Come può stare un elemento in una barra:**

| sigla | modo | vetro |
|---|---|---|
| **I** | tasto con un'icona, da solo | tondo proprio |
| **G** | più icone in un vetro (gruppo) | capsula, passo 1 H |
| **Tx** | tasto di testo («Seleziona», «Modifica») | capsula propria, mai con simboli |
| **P** | azione principale tinta (spunta, freccia, «Fine») | proprio, mai in gruppo, sempre a destra |
| **B** | indietro (chevron) | tondo, gruppo a sé, a sinistra |
| **X** | chiudi | tondo proprio; nei fogli a sinistra (`cancellationAction`) |
| **O** | elemento nel trabocco | nessun vetro: vive nel menu del «…», che è un **I** con ruolo «trabocco» |
| **Ti** | titolo e sottotitolo | nessun vetro |
| **S** | ricerca: tasto (tondo con lente) o campo (capsula larga) | proprio |
| **∅** | niente | — |

Le posizioni sono **sinistra** (B, X, azioni d'inizio), **destra** (azioni, P, «…», ricerca su tablet) e
**centro** (Ti; nella barra in basso le azioni centrali). Il bordo esterno di un lato, cioè il margine dello
schermo, fa da **àncora**.

**Due tipi di cambio:**
- **Di stato**, nella stessa pagina: selezione, modifica, ricerca attiva. Non ha un verso.
- **Di pagina**: push, pop col tasto, pop col gesto, cambio di scheda, apertura di un foglio.

### B.2 Cosa fa Apple: le quattro clip che contano

1. **Cambio di contesto nella stessa barra** (219 16:02, gli screenshot scelti a mano; 219 4:44 col dito).
   - Le capsule cambiano larghezza in continuo.
   - Un gruppo di due diventa un gruppo di tre: la capsula si allarga verso l'interno, le icone si scambiano per
     sfocatura, il «più» nasce nella parte nuova da scala 0,75 (`r7/A1_…`).
   - Un gruppo di due diventa un tondo tinto: la capsula si chiude dal lato interno verso l'icona che resta, quella
     che esce è coperta dal bordo, la freccia diventa blu dentro la sfocatura (`r7/A2_…`).
   - Col dito: tre icone diventano il solo «più», che resta nitido mentre la capsula si chiude su di lui
     (`rif/collage/B2_barra_telefono_f8536-8570.jpg`).
2. **Push** (256 2:23). Nella pagina di partenza c'è un tondo col filtro; in quella d'arrivo l'indietro, un
   gruppo ˄˅ e un tondo «…» (`r7/B1_…`, `r7/B1b_…`, `r7/B1c_…`).
   - Il tondo di destra resta: è il più esterno a destra in tutte e due le pagine. Il filtro diventa «…» per
     sfocatura.
   - Il gruppo ˄˅ **nasce dal fianco interno del tondo**. Il tondo prima si sporge di ≈0,25 D verso sinistra, poi
     spunta un bocciolo, poi la capsula cresce attaccata con un collo che si assottiglia e si rompe a ≈400 ms. Le
     icone ˄˅ arrivano piccole e chiare.
   - L'indietro compare sul posto: prima il vetro, poi il chevron.
   - La pagina è quasi ferma a ≈200 ms (a occhio) e la barra finisce a ≈470–530 ms: la barra ha un orologio suo.
3. **Push e gesto indietro** (284 12:15–12:28, Note; `r7/B2_pop_interattivo_284_f22247-22421.jpg`).
   - Push: il tondo «…» della lista si allarga in una capsula verso sinistra (diventerà condividi + «…»), con il
     contenuto sfocato. Il montaggio ferma il push all'85% per due secondi.
   - Poi il dito afferra la pagina e la trascina verso destra. Durante il trascinamento la barra **non segue il
     dito**: anzi finisce la sua trasformazione verso la nota, che è ancora la pagina in cima, mentre la lista è già
     quasi tutta visibile (12:25.8).
   - Al rilascio che conferma (12:26.1) il gruppo condividi + «…» si sfoca e si chiude nel tondo «…» della lista, e
     la barra in basso fa lo stesso.
   - In questa clip UIKit sfoca anche il «…», che c'è nelle due pagine: lo scambio è del vetro intero, non
     dell'icona.
4. **Scheda di ricerca** (256 4:04, `r7/A5b_…`). La tab bar si chiude nel tondo della scheda scelta. Il tondo della
   ricerca si allunga nel campo, la lente lo percorre nitida, il segnaposto arriva sfocato. Tempo: ≈200 ms.

Documentazione:
- [`UIBarButtonItem.identifier`](https://developer.apple.com/documentation/uikit/uibarbuttonitem/identifier) (D):
  quando l'insieme degli elementi cambia, «for example, when pushing or popping view controllers», UIKit anima da
  solo la transizione. Di serie l'abbinamento è un'euristica su posizione e contenuto; lo stesso `identifier` su
  due elementi «indicates that they should be treated as the same item during transitions».
- [WWDC25 256 @2:18](https://developer.apple.com/videos/play/wwdc2025/256/) (W): «During navigation transitions,
  these items can even morph».
- Nessuna API SwiftUI documentata per l'abbinamento (R1 C6).

### B.3 Cosa segue l'occhio

Le regole della matrice nascono da cinque principi. Sono deduzioni (De), ma ognuno ha una clip che lo mostra.

1. **Identità.** L'occhio tratta come lo stesso oggetto ciò che ha la stessa funzione, la stessa icona e lo stesso
   posto. Se le tre cose coincidono, l'oggetto non si deve toccare (il «più», l'indietro fermo). Se coincidono
   posto e funzione ma l'icona cambia, si scambia solo il contenuto. Se coincide solo il posto, è comunque lo stesso
   vetro che cambia contenuto: è il caso più comune e quello che Apple fa (filtro → «…»).
2. **Continuità del vetro.** Il vetro è materia: non sparisce in un fotogramma e non salta. Quello che resta non si
   muove, o si muove di poco per fare posto. Uno nuovo esce da un vicino, oppure si materializza dove starà se il
   vicino non c'è. Due che si avvicinano si fondono passando per un ponte; uno che se ne va prima si svuota, poi si
   ritira nel vicino o si spegne.
3. **Direzione.** La pagina ha un verso, la barra no: i tasti si trasformano sul posto. Al più il titolo in linea fa
   un passo piccolo nel verso della navigazione. Un tasto che scivola via con la pagina dice «questo tasto
   apparteneva alla pagina», ed è proprio l'effetto «ancorato» che Alessio vuole togliere.
4. **Poche cose insieme.** L'attenzione segue uno o due eventi di forma per volta. Gli scambi di contenuto costano
   poco: sono sul posto e sincroni.
5. **Cosa non deve succedere:**
   - un vetro che attraversa lo schermo o un altro vetro;
   - due cambi in sequenza sullo stesso elemento (prima la forma e poi, a parte, il contenuto, o viceversa);
   - un lampo, cioè opacità che scende e risale su ciò che non cambia;
   - un fotogramma senza vetro;
   - un'icona leggibile sopra un'altra leggibile;
   - una trasformazione lasciata a metà a lungo.

### B.4 Come si accoppiano gli elementi di due barre

L'engine confronta il modello di prima con quello di adesso. Vale per un cambio di stato come per un cambio di
pagina. Gli elementi si accoppiano in quest'ordine, lato per lato, dove un elemento a sinistra non si accoppia mai
con uno a destra:

1. **`id` uguale**, in qualsiasi posizione dello stesso lato. È l'equivalente di `UIBarButtonItem.identifier`.
2. **Ruolo unico uguale**: indietro con indietro, chiudi con chiudi, «…» con «…», ricerca con ricerca, principale
   con principale. Indietro e chiudi sono lo stesso ruolo di «uscita» sul lato sinistro (B.5, riga 22).
3. **Stesso simbolo sullo stesso lato**, se in ciascuna barra è unico.
4. **Ordine dall'esterno** fra i rimasti dello stesso lato: il più esterno col più esterno, poi verso l'interno.
   Solo fra tipi compatibili: un tondo con un tondo o con un tondo tinto, un testo con un testo, un tondo con un
   testo (il vetro cambia forma). Questo passo è l'euristica «posizione» di UIKit, ed è ciò che spiega il filtro
   che diventa «…» in 256.

**Gruppi.** Due gruppi si accoppiano se hanno in comune più `id` (o simboli) di qualunque altra coppia; altrimenti
per posizione. Un elemento che passa da un gruppo all'altro non viaggia: per lui il cambio è una morte più una
nascita (regola M10).

**Cosa può dichiarare l'app:**
- `id` stabili, uguali fra le pagine per dire «è lo stesso tasto» anche se cambia icona o posto;
- `id` diversi per impedire un accoppiamento sbagliato (diventa morte più nascita);
- `bornFrom = id` per scegliere da quale vicino nasce un tasto;
- `transition = Identity` per un elemento che deve cambiare di colpo;
- gruppi e spaziatori: decidono chi condivide un vetro, e quindi chi si fonde e chi si divide.

Senza dichiarazioni, i passi 2–4 bastano per i casi comuni. La scheda deve dire di dare gli `id` quando due pagine
hanno lo stesso tasto in posti diversi.

### B.5 La matrice

Tabella per righe raggruppate (partenza → arrivo). «Apple» dice cosa si vede o cosa è documentato. «Regola» è la
trasformazione proposta, valida per un cambio di stato e per un cambio di pagina, salvo dove la colonna dice
altro. Le regole M sono in B.9.

| # | partenza → arrivo | Apple | regola naturale (chi diventa chi, chi nasce da chi, ordine) | regole | fase |
|---|---|---|---|---|---|
| 1 | I → I, stesso simbolo | resta nitido (219); in UIKit fra pagine, se il suo gruppo cambia, si sfoca con lui (284) | non si muove, non si sfoca. Divergenza dalla clip di UIKit: **domanda D2** | M3 | F3.4, F4A.3 |
| 2 | I → I, simbolo diverso, stesso posto | scambio per sfocatura (256, 219 col dito) | vetro fermo; scambio di contenuto (A1) | M4 | F3.4 |
| 3 | G(n) → G(n), alcune icone cambiano | quelle uguali nitide, le altre scambiate (219 4:48) | larghezza ferma; scambio icona per icona | M3, M4 | F3.4, F4A.2 |
| 4 | G(n) → G(m), m ≠ n, stesso gruppo | larghezza continua, bordo esterno quasi fermo, icone scambiate (16:02); 3 → 1 col dito (4:44) | la capsula cambia larghezza ancorata al bordo esterno; le icone uguali scorrono nitide al passo nuovo; quelle nuove nascono nella parte nuova da scala 0,75 (A6); quelle che escono si sfocano sul posto e il bordo le copre | M5, A5, A6 | F3, F4A.2 |
| 5 | I → G (il tondo diventa un gruppo che lo contiene) | Note: il «…» si allarga in condividi + «…» (284) | la capsula cresce dal tondo verso l'interno; l'icona che c'era resta nitida (M3) e le altre nascono sfocate | M5, M3, A6 | F3, F4A.3 |
| 6 | G → I | la capsula si chiude dal lato interno sull'icona che resta (16:02, 4:44) | inverso del 5; se nessuna icona resta, la capsula si chiude sull'elemento accoppiato e il contenuto si scambia | M5, A5 | F3, F4A |
| 7 | I → I + I (nasce un vetro separato) | il gruppo ˄˅ nasce dal fianco del tondo con un collo (256) | il vetro nuovo nasce dal vicino dello stesso lato, dalla parte in cui il layout d'arrivo lo mette, attaccato con un collo che si rompe a ≈0,85 T; contenuto da scala 0,75 sfocato (A6). **Caso 1** | M6 | F3.1, F3.3, F4A.3 |
| 8 | I + I → I (muore un vetro separato) | non osservato da solo; il gruppo che si chiude nel tondo (284, al rilascio) è parente | prima il contenuto via sul posto (0–0,4 T), poi il vetro vuoto va verso il vicino, si salda e viene assorbito. **Caso 1, ritorno** | M7 | F3.3, F4A.3 |
| 9 | I + I ↔ G (stesse icone, separati ↔ gruppo) | fusione per vicinanza e divisione come gocce (doc C5); «Select» → tre lobi (219 5:01, render) | fusione con un ponte che si allarga; divisione con una vita che diventa collo e si rompe; icone nitide che scorrono al nuovo passo. **Caso 3** | M8, M3 | F3.3 |
| 10 | G → G + G (uno spaziatore fisso spezza il gruppo) e inverso | come 9 | collo nel punto dello spaziatore | M8 | F3.3, F4A.2 |
| 11 | ∅ → I/G su un lato che ha già un vetro | nascita dal vicino (256) | come 7 | M6 | F3.1 |
| 12 | ∅ → I/G su un lato vuoto | l'indietro di 256 compare sul posto | comparsa per lente sul posto: contorno +8–10%, lente da 0 a 1, contenuto da scala 2 sfocato (A8), ≈430 ms | M6 | F3.3 |
| 13 | I/G → ∅ (l'ultimo vetro di un lato) | non osservato in una barra; la scomparsa per lente di R4 B3 | dematerializzazione sul posto: contenuto via in 130 ms, lente a zero entro 400 ms, poi il contorno | M7 | F3.3 |
| 14 | Tx → Tx («Modifica» → «Annulla») | il testo si sfoca prima che il vetro cambi forma (219 5:01) | capsula che cambia larghezza ancorata al bordo esterno; testo scambiato (A1, A9) | M5, M4 | F3.4, F4A.2 |
| 15 | Tx ↔ I | non osservato | stesso vetro: capsula ↔ tondo, con lo scambio di contenuto | M5, M4 | F3.4 |
| 16 | Tx → G + Tx («Seleziona» → ○ △ + «Fine») | un vetro si gonfia, si allunga in tre lobi e si divide (219 5:01, render rallentato) | il tasto di testo diventa il vetro del testo nuovo (stesso ruolo, posto esterno); il gruppo nasce da lui con un collo (come 7); testo e simboli mai nello stesso vetro | M6, M8 | F3, F4A.2 |
| 17 | I/Tx → P al posto dell'elemento | tinta dentro la sfocatura (16:02) | stesso vetro, forma invariata (o capsula → tondo); contenuto scambiato e colore incrociato fra 0,25 e 0,5 T (A4). **Caso 5a** | M4, A4 | F3.4, F4A.2, F1B (tinta in viaggio) |
| 18 | G → P (un elemento del gruppo diventa principale) | il gruppo si chiude in un tondo tinto (16:02) | P non si raggruppa mai: se gli altri restano, la capsula si divide e la parte di P diventa tondo tinto (come 9); se gli altri spariscono, la capsula si chiude su P. **Caso 5b** | M8, M11 | F3, F4A.2 |
| 19 | P → I/Tx, P → ∅ | non osservato | inverso di 17, 18; P → ∅ come 13 | M4, M7 | F4A.2 |
| 20 | B → B (le due pagine hanno l'indietro) | fermo al pixel (284 push e pop) | fermo, nitido, mai riprodotto | M12 | c'è già (`FluidPageAnchoredBar.kt:392`) |
| 21 | ∅ → B, B → ∅ (radice ↔ figlia) | compare sul posto, prima il vetro poi il chevron (256) | per lente sul posto (12, 13), mai scivolando via con la pagina; il titolo in linea si sposta se il layout lo chiede. **Caso 6** | M12 | F3.3, F4A.3 |
| 22 | X ↔ B (radice di un foglio con «chiudi» ↔ figlia con «indietro») | non osservato | stesso ruolo di uscita, stesso posto: stesso vetro, simbolo scambiato | M2, M4 | F4A.3, F5B |
| 23 | X → ∅, ∅ → X | non osservato | come 12, 13 | M6, M7 | F4A |
| 24 | visibile → O (azioni che finiscono nel trabocco) | composizione con «…» (R5 B11); il passaggio non osservato | chi entra nel menu va nel «…»: se il «…» c'è, ci viene assorbito (come 8); se non c'è, il «…» nasce dal vetro di chi entra nel menu (divisione). **Caso 4** | M13 | F4A.1, F4A.3 |
| 25 | O → visibile | non osservato | chi esce dal menu nasce dal «…» (come 7); se il menu si svuota, il «…» diventa l'ultimo elemento uscito (scambio di contenuto) | M13 | F4A |
| 26 | Ti → Ti (titolo in linea) | fra pagine non osservato; nel ritiro il titolo si sfoca (R5 V5) | incrocio sfocato (A1 sul testo) con passo ≤8 dp nel verso della navigazione; nessun passo in un cambio di stato | M14 | F3.4, F4A.2 |
| 27 | titolo grande | scorre con la sua pagina, nitido (256, 284) | è contenuto: viaggia con la pagina (c'è già) | M14 | c'è |
| 28 | sottotitolo che compare o sparisce | non osservato | arriva sfocato sotto il titolo; il titolo si sposta col layout, nitido | M14, A6 | F4A.2 |
| 29 | S tasto ↔ S campo | il tondo si allunga nel campo, la lente viaggia nitida, il segnaposto arriva sfocato (256) | come 5, con la lente che conserva l'identità (M3) | M5, M3 | **F4B.2** |
| 30 | S ↔ ∅, campo fissato in alto | barra che si ritira (R5 V5) | come 12, 13; il ritiro segue lo scorrimento (A10) | M6, M7, A10 | F4A.4, F4B |
| 31 | I/G → menu | tutto il gruppo diventa il menu; icone sfocate via in 67–100 ms (R4 B1, 284 13:46) | fuori dalla barra: F5A | — | **F5A** |
| 32 | I/G → foglio (zoom dal tasto) | il vetro cresce dal tasto; contenuto sfocato, nitido a ≈300 ms; pagina dietro scurita (284 14:07) | il tasto sorgente diventa il foglio e resta «consumato» finché il foglio è aperto; rinasce dal foglio alla chiusura | — | **F5B** |

**Per tipo di cambio:**

| cambio | quando parte la barra | orologio | verso | titolo in linea |
|---|---|---|---|---|
| stato | subito | T | nessuno | incrocio sul posto |
| push | col tocco, insieme alla pagina | T_nav ≈ 450 ms, indipendente dalla pagina (256: la barra finisce ≈250 ms dopo la pagina) | nessuno per i tasti | passo nel verso della navigazione |
| pop col tasto | col tocco | come il push | come il push | come il push |
| pop col gesto | **al rilascio che conferma**; all'annullo niente (B.7) | T_nav dal rilascio | — | — |
| push interrotto da un gesto | la trasformazione in corso finisce verso la pagina in cima e riparte da dove si trova alla conferma del pop, senza salti (284; invariante 16) | — | — | — |
| cambio di scheda | non osservato: **domanda D4** | — | — | — |
| apertura di un foglio | la barra della pagina non cambia; il tasto sorgente diventa il foglio | (F5B) | — | il foglio ha la sua barra |

Le posizioni cambiano solo il lato d'àncora. A sinistra l'àncora è il bordo sinistro e le nascite vanno verso
destra; a destra è il contrario. Al centro, il centro: una capsula centrale nella barra in basso cresce simmetrica.

### B.6 I sei casi dell'utente

**(i) Un tasto «…» → «…» + un secondo tasto separato, e ritorno.** `r7/B_caso1_un_tasto_diventa_due.png`.
- Accoppiamento: «…» con «…» per ruolo e simbolo; «condividi» non ha compagno e nasce.
- Andata:
  - il «…» resta fermo e nitido per tutto il viaggio;
  - dal suo fianco interno spunta un bocciolo (0,15 T) e si allontana verso il posto d'arrivo, con un collo sempre
    più sottile;
  - l'icona nuova arriva sfocata, a scala 0,75–0,8 e con opacità bassa, e si mette a fuoco con la curva di A1;
  - il collo si rompe a ≈0,75–0,85 T; poi un superamento ≤1,3% e il riposo.
  - È ciò che fa Apple in 256: lì cambiava anche l'icona del tondo, qui no.
- Ritorno:
  - prima «condividi» si sfoca sul posto (0–0,4 T);
  - poi il suo vetro, ormai quasi vuoto, si avvicina, si salda al «…» e viene assorbito, lasciando un rigonfiamento
    che rientra.
  - Il vetro si muove solo quando il contenuto se n'è andato: si evita un tasto pieno che «entra» in un altro.
- Durata: T_nav ≈ 450 ms.

**(ii) Due tasti restano due e cambiano icona.** `r7/B_caso2_due_tasti_cambiano_icona.png`.
- I due vetri non si muovono.
- Le icone si scambiano con A1, tutte e due nello stesso momento.
- Se i due simboli nuovi hanno larghezze diverse (testo), il vetro cambia larghezza ancorato al bordo esterno.
- È il caso più frequente, ed è quello in cui oggi l'engine fa peggio: dissolvenza con buco, che ogni tanto passa
  per due icone semitrasparenti leggibili.

**(iii) Due tasti separati → un gruppo di due, e ritorno.** `r7/B_caso3_separati_e_gruppo.png`.
- Andata:
  - il vetro interno si avvicina a quello esterno, che resta fermo perché ancorato;
  - nel varco nasce un ponte che si allarga in una vita e si riempie fino alla capsula;
  - le icone sono le stesse, quindi non si sfocano mai: scorrono col vetro fino al passo del gruppo.
- Ritorno:
  - la capsula fa una vita fra le due icone;
  - la vita diventa un collo, che si rompe a ≈0,75 T;
  - il vetro interno torna al suo posto.
- È la fusione e divisione di Apple (doc C5; 219 5:01) senza scambi di contenuto: il caso più «liquido» e il più
  facile da leggere.

**(iv) Tre azioni → una sola più il trabocco.** `r7/B_caso4_tre_azioni_e_trabocco.png`.
- «Preferito» resta, per `id`.
- «Condividi» ed «elimina» passano nel menu: la loro parte di capsula diventa il «…».
- Ordine:
  1. le due icone che se ne vanno si sfocano sul posto;
  2. la capsula fa una vita subito dopo «preferito»;
  3. la parte di destra si stringe nel tondo del «…», che arriva sfocato al posto dell'icona più esterna;
  4. il collo si rompe;
  5. «preferito» scorre nitido al suo posto, di meno di un posto.
- Ritorno: il «…» si allarga verso l'interno e si fonde con «preferito»; il «…» si sfoca via e condividi ed
  elimina arrivano sfocati.
- La logica da spiegare all'occhio: le azioni che spariscono «vanno a finire» nel tasto che le contiene.

**(v) Compare l'azione principale tinta al posto di un tasto normale.** `r7/B_caso5_azione_principale_tinta.png`.
- **5a**, tasto per tasto: il vetro resta e prende la tinta dentro la finestra della sfocatura (A4), mentre la
  matita diventa la spunta.
- **5b**, gruppo di due → tondo tinto, ciò che Apple fa a 16:02: la capsula si chiude dal lato interno, l'icona che
  esce è coperta dal bordo, quella che resta prende il colore mentre è sfocata e diventa la freccia.
- Regola d'uso che tocca la trasformazione: la principale non si raggruppa mai. Se il tasto sostituito stava in un
  gruppo che resta, il gruppo si divide (come iii, al ritorno).

**(vi) Il tasto indietro che compare o sparisce alla radice.** `r7/B_caso6_indietro_alla_radice.png`.
- A sinistra non c'è un vicino di vetro (il titolo non lo è). L'indietro quindi **compare per lente sul posto**:
  il contorno parte più largo del 9%, la lente si accende e il chevron si condensa da grande e sfocato. È ciò che
  si vede in 256 (prima il vetro, poi il chevron).
- Sparendo fa il percorso inverso: prima il segno, poi la lente, poi il contorno.
- Non scivola mai via con la pagina.
- Il titolo in linea cambia per incrocio sfocato con un passo piccolo. Questa parte è dedotta, non osservata.

Gli schemi sono disegni, non render del materiale: servono a fissare chi diventa chi, l'ordine e i tempi relativi.

### B.7 Il gesto indietro

**Cosa fa iOS.**
- Nella clip di UIKit (284, Note, `r7/B2_pop_interattivo_284_f22247-22421.jpg`) la barra in alto e quella in basso
  restano quelle della pagina in cima per tutto il trascinamento. La trasformazione verso la lista parte **al
  rilascio che conferma**: in alto il gruppo si sfoca e si chiude nel tondo «…»; in basso la barra della nota
  diventa il campo di ricerca.
- La clip è montata (il push è fermo all'85% per due secondi prima del gesto) e forse rallentata: i tempi assoluti
  non valgono, l'ordine sì. Fiducia media.
- Un'osservazione di terzi su iOS 26/27 dice lo stesso: «the bar doesn't react while the finger is down», e i tasti
  cambiano dopo il commit. Lì è descritto come una dissolvenza rapida; nella clip di Apple è uno scambio con
  sfocatura e un cambio di forma. Fonte:
  [liquid_glass_widgets #353](https://github.com/sdegenaar/liquid_glass_widgets/issues/353) e
  [PR #360](https://github.com/sdegenaar/liquid_glass_widgets/pull/360) (T).
- Fino a iOS 18 la barra stava nello stesso gruppo d'animazione della transizione interattiva e seguiva il
  progresso del dito ([transition coordinator](https://developer.apple.com/documentation/uikit/uiviewcontrollertransitioncoordinator), D;
  De sulla resa).
- Apple ha quindi **cambiato idea con Liquid Glass**: una barra che si fonde e si divide non si può tenere ferma a
  metà con decoro.

**Cosa fa l'engine oggi.** È il contrario. `FluidBarJourney.recompute` guida il morph con
`progress = { FluidPageMotion.barMorph(state.frontFraction()) }` (`ui/page/FluidPageAnchoredBar.kt:460`), cioè
segue il dito, con zone morte del 10% (`ui/page/FluidPageMotion.kt:415`). Le icone si incrociano in dissolvenza
con lo stesso progresso.

**Le strade.**

| strada | durante il trascinamento | al rilascio che conferma | all'annullo | pregi | difetti |
|---|---|---|---|---|---|
| **A · aspetta la conferma** (Alessio, Apple 284) | la barra resta quella della pagina in cima; una trasformazione già in corso finisce verso di lei | trasformazione intera in T_nav, che parte da dove si trova e conserva la velocità se ce n'è | niente | mai stati a metà; nessun lavoro all'annullo; è ciò che fa Apple; regge l'annullo tardivo del sistema | per un attimo la pagina sotto si vede con i tasti della pagina sopra; la barra finisce ≈200–300 ms dopo la pagina |
| **B · segue il dito** (engine oggi) | morph e incrocio legati al progresso | finisce dal punto raggiunto | torna indietro dal punto raggiunto | continuità perfetta fra pagina e barra | colli tenuti a metà, icone a metà sfocatura ferme sotto il dito, un ritorno che rigioca l'animazione al contrario: è ciò che Alessio chiama brutto |
| **C · anticipo reversibile** | solo gli elementi che cambieranno contenuto ricevono un anticipo leggero e reversibile: σ ≤ 0,04 h e opacità ≥ 0,85, proporzionali al progresso fino alla soglia di conferma; nessuna forma si muove | la trasformazione parte dallo stato anticipato (A1 lo contiene già nei primi 0,15 T) | l'anticipo torna a nitido in 150 ms | dice «questi tasti cambieranno» senza mostrare mezze forme; è nello spirito del predictive back di Android | non è Apple; una barra appena sfocata sotto il dito può sembrare un difetto |
| D · scatta alla soglia | niente fino al 35%, poi la trasformazione intera | — | se l'annullo arriva dopo la soglia, c'è un'animazione da rifare al contrario | come le linee guida di Android per il contenuto | è proprio l'«animazione lasciata a metà» |

Le linee guida di Android sul predictive back dentro l'app chiedono un'anteprima legata al progresso e reversibile:
la pagina segue il dito, il contenuto si scambia «using a fade through» alla soglia, e all'annullo si torna in
fretta ([Predictive back, design](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back),
D). Per gli elementi della barra in alto non dicono nulla.

**Raccomandazione: A, di serie.** Motivi:
- è ciò che fa Apple con Liquid Glass;
- è l'intuizione di Alessio;
- non ha mai uno stato a metà;
- l'annullo è gratis;
- la pagina, che è ciò che il dito tocca, continua a seguire il dito: l'anteprima di Android c'è, sulla pagina.

**C** è l'unica variante che vale la pena mostrare accanto ad A. **B** si mostra solo come riferimento, perché è
il comportamento di oggi.

**Cosa mostrare ad Alessio**: un video per strada, sulla stessa coppia di pagine, con:
- il caso 1 (un tasto → due) e il caso 4 (tre → uno + «…»);
- gesto fermato e tenuto al 25%, 50% e 75%, poi rilasciato;
- un annullo al 75%;
- un lancio veloce;
- un push seguito subito dal gesto (il caso di 284).

### B.8 I tasti collegati: quando le azioni condividono un vetro

Regole di Apple, completate rispetto a R1 B1–B3 e B10:
1. Di serie i tasti con immagine condividono un vetro con i tasti con immagine vicini, e un gruppo con più elementi
   condivide il suo. **Tasti di testo, «Fine» e «Chiudi» di sistema e tasti prominenti hanno ciascuno il proprio**
   ([WWDC25 284 @7:44–8:11](https://developer.apple.com/videos/play/wwdc2025/284/), W). L'indietro sta in un
   gruppo a sé (R1 B1).
2. Uno spaziatore fisso spezza un gruppo; uno flessibile separa e spinge. Per tenere un vetro solo con elementi
   distribuiti, in UIKit si usano spazi flessibili con `hidesSharedBackground = false`
   ([284 @9:16](https://developer.apple.com/videos/play/wwdc2025/284/), W).
3. Nascondere lo sfondo condiviso mette l'elemento in un gruppo suo
   ([sharedBackgroundVisibility](https://developer.apple.com/documentation/swiftui/toolbarcontent/sharedbackgroundvisibility(_:)),
   D). `hidesSharedBackground` e `sharesBackground` sono ignorati dentro un `UIBarButtonItemGroup` con più elementi
   (D).
4. Raggruppare **per funzione e frequenza**. Non mettere mai testo e simboli nello stesso gruppo («Select» e
   condividi sembrerebbero un tasto solo). Al più tre gruppi. Le azioni critiche (Fine, Chiudi, Salva) stanno in un
   gruppo dedicato ([WWDC25 356 @9:33–9:41](https://developer.apple.com/videos/play/wwdc2025/356/), W; HIG Toolbars,
   D).
5. «Items grouped using the correct API will automatically update to share a background and **preserve their
   spatial relationship**» ([356 @9:07](https://developer.apple.com/videos/play/wwdc2025/356/), W): il gruppo è una
   relazione stabile, non un ornamento.

Cosa comporta per le trasformazioni:
- Il **gruppo è un'unità di vetro con un'identità** (derivata dagli `id` dei membri: F4A.2) e una **lista di
  contenuti**. Il vetro si trasforma come un pezzo; i contenuti seguono le regole A icona per icona.
- **Un gruppo che guadagna un'icona** si allarga dalla parte in cui l'icona entra, con il bordo esterno fermo.
  Le icone che restano scorrono nitide; la nuova nasce sfocata da 0,75 (riga 4).
- **Un gruppo che perde un'icona** la sfoca sul posto e la copre col bordo che rientra (riga 4, A5).
- **Un gruppo che si divide** fa una vita nel punto della divisione, poi un collo, poi la rottura. **Due che si
  fondono** fanno un ponte, poi una vita, poi una capsula (righe 9–10). Nessuna icona si sfoca se non cambia.
- **Un elemento che cambia gruppo** non viaggia da un vetro all'altro: si sfoca via da uno e nasce nell'altro (M10).
- **Un gruppo è anche la sorgente del menu** (R1 N2, F5A): se l'app raggruppa di più, più cose spariscono quando un
  menu si apre. È un argomento per i gruppi piccoli: due o tre icone.
- Alessio ha chiesto di usare di più i tasti collegati. Di serie l'engine raggruppa ciò che Apple raggruppa (punto
  1), e con il modello di F4A diventa la norma senza scrivere niente.

### B.9 Le regole del modello

| # | regola | casi coperti (righe di B.5) | fiducia |
|---|---|---|---|
| M1 | La barra è uno **stato dichiarato**: elementi con `id`, ruolo, lato, gruppo. Ogni cambio, di stato o di pagina, è una differenza fra due stati calcolata dall'engine; la pagina non scrive animazioni | tutti | — (architettura, F4A.1) |
| M2 | **Accoppiamento** in quattro passi: `id`, ruolo unico, simbolo uguale, ordine dall'esterno fra tipi compatibili; sempre dentro lo stesso lato | 1–30 | media (euristica UIKit documentata, D; ordine dedotto da 256) |
| M3 | **Stesso contenuto, nessun cambio**: nitido, opacità 1; si sposta solo col suo vetro | 1, 3, 4, 5, 9, 20, 29 | alta (219, 256) |
| M4 | **Contenuto diverso, stesso vetro**: scambio per sfocatura (A1–A4) | 2, 3, 14, 15, 17, 22 | alta |
| M5 | **Vetro accoppiato che cambia forma**: larghezza e raggi in continuo, bordo esterno del lato fermo (al più 0,2 H di rientro e ≤1,3% di superamento) | 4, 5, 6, 14, 15, 29 | alta (16:02) |
| M6 | **Nascita**: dal vicino dello stesso lato entro la spaziatura del contenitore, dalla parte in cui il layout lo mette, con un collo che si rompe a ≈0,85 T; senza vicino, comparsa per lente sul posto | 7, 11, 12, 16, 21, 23 | alta (256) per il vicino, media per la lente |
| M7 | **Morte**: prima il contenuto (0–0,4 T), poi il vetro vuoto si salda al vicino e viene assorbito, o si dematerializza sul posto se è solo | 8, 13, 19, 21, 23 | media (simmetria e doc C3: «vi rientra») |
| M8 | **Fusione e divisione**: ponte → vita → capsula, e vita → collo → rottura; P mai in un gruppo; testo e simboli mai nello stesso vetro | 9, 10, 16, 18 | media (doc C5, 219 5:01 rallentato) |
| M9 | **Poche cose**: al più un evento di forma (nascita, morte, fusione o divisione) per lato e per cambio; oltre, gli eventi in più diventano scambi di contenuto sul posto o comparse per lente | — (difesa) | De |
| M10 | **Niente attraversa**: un vetro non passa sopra o sotto un altro vetro e non si sposta di più di ≈1,5 H; un elemento che cambia gruppo o lato muore e rinasce | 4, 24 | De (B.3) |
| M11 | **Ordine dei tempi**: contenuto vecchio via (0–0,6 T), forme in continuo (0–1 T, le nascite da ≈0,3–0,45 T), contenuto nuovo a fuoco per ultimo (≈0,4–1 T); un solo T per tutta la barra; lati simultanei | tutti | alta (A.3, 256) |
| M12 | **Navigazione**: la barra non viaggia con la pagina. Parte col tocco su un orologio suo (T_nav); col gesto, al rilascio che conferma; all'annullo niente. Una trasformazione in corso non si inverte sotto il dito: finisce verso la pagina in cima e riparte da dove si trova. L'indietro comune alle due pagine è fermo; quello che compare o sparisce lo fa sul posto | 20, 21, tabella per tipo | media (284 + T) |
| M13 | **Trabocco**: il «…» è la casa di chi è nel menu. Chi entra nel menu ci finisce dentro (assorbito, o il «…» nasce dal suo vetro); chi esce nasce dal «…» | 24, 25 | De |
| M14 | **Titoli**: il grande è contenuto e viaggia con la pagina; quello in linea e il sottotitolo si scambiano per sfocatura, con un passo ≤8 dp solo fra pagine | 26, 27, 28 | alta (grande), De (in linea) |
| M15 | **Ritiro guidato dallo scorrimento**: stessa grammatica, progresso dello scorrimento al posto del tempo | 30 | media (R5 V5) |
| M16 | **Ciò che l'app può dichiarare**: `id` (stesso tasto fra stati e pagine), `id` diversi (vietare l'accoppiamento), `bornFrom` (vicino d'origine), `transition = Identity` (niente animazione), gruppi e spaziatori | — | — (API, F4A.1–2) |
| M17 | **Movimento ridotto**: niente forme in viaggio; i vetri accoppiati saltano al riposo e il contenuto si scambia in dissolvenza breve (≈150 ms) senza sfocatura | tutti | De; una libreria di terzi che imita iOS fa lo stesso (T) |

### B.10 Casi ambigui: le domande per Alessio

Ogni domanda ha alternative mostrabili in un video della galleria. La prima opzione è quella raccomandata.

**D1 · Il gesto indietro** (B.7). Video: caso 1 e caso 4, gesto tenuto al 25/50/75%, annullo al 75%, push seguito
dal gesto.
- **A · Aspetta la conferma** (Apple; consigliata): i tasti non cambiano mentre il dito trascina, poi la
  trasformazione intera.
- **C · Anticipo reversibile**: i tasti che cambieranno si appannano appena mentre si trascina; alla conferma la
  trasformazione parte da lì.
- **B · Segue il dito** (com'è oggi).

**D2 · Un'icona uguale nelle due pagine ma in un vetro diverso** (per esempio «…» da solo → «…» in un gruppo con
«condividi»). Video: le pagine della lista e della nota di Note, rifatte.
- **A · Resta nitida e scorre col suo vetro** (consigliata: regola dell'identità, come il «più» di Apple).
- **B · Si sfoca e torna insieme al gruppo**, come fa UIKit in Note (284).

**D3 · Da dove nasce un tasto nuovo quando sul suo lato c'è già un vetro.** Video: caso 1.
- **A · Dal fianco del vicino, con un collo che si rompe** (Apple 256; consigliata).
- **B · Per lente, sul posto**, senza toccare il vicino.
- **C · Dal bordo dello schermo**, scivolando dentro.

**D4 · Il cambio di scheda** (non osservato da Apple). Video: due schede con barre diverse (una con «…», l'altra con
condividi + «…»).
- **A · Come un cambio di stato**: i tasti si trasformano sul posto, senza verso (consigliata: la tab bar non ha un
  verso spaziale forte).
- **B · Come una pagina**, con il titolo che fa un passo nel verso della scheda.
- **C · Cambio secco**, come iOS 18.

Scelte di gusto, cioè le manopole di A.6, da mostrare come confronti senza domanda aperta:
- σ massima;
- T;
- scala di nascita;
- quanto si sporge il vicino che partorisce (Apple ≈0,25 D; 0 è più sobrio);
- tinta dell'azione principale: piena o solo sul simbolo, già fra le scelte di F4A.

### B.11 Cosa manca nell'engine di oggi

Capacità, non codice. I riferimenti dicono dove sta il comportamento di oggi.

| manca | oggi | fase |
|---|---|---|
| uno **scambio di contenuto con sfocatura** uniforme, scala e opacità, con incrocio e chiave di contenuto | le icone fra pagine fanno una dissolvenza: esce `1 − m/0,35`, entra `(m − 0,25)/0,5` (`ui/page/FluidPageMotion.kt:422`); a m ≈ 0,3 tutte e due sono sotto 0,15 di opacità, cioè un **buco** | **F3.4** |
| che **un'icona uguale nelle due pagine resti ferma** | le azioni delle due pagine sono due righe composte a parte e sovrapposte (`ui/page/FluidPageAnchoredBar.kt:360`): anche il «…» comune passa per il buco e **lampeggia** | **F4A.3** (una barra disegnata dal modello, un elemento per `id`) |
| un'**identità** degli elementi (`id`, ruolo, simbolo) per accoppiare | coppie per metà della barra e ordine dall'esterno (`FluidPageMotion.barPieceJourney`, `:469`) | **F4A.1**, F4A.3 |
| che **un tasto nasca da un vicino** con un collo | chi resta senza compagno nasce o si spegne da un **seme di un pixel sul proprio centro** (`seedAt`, `:497`) | **F3.1** (classificazione), **F3.3** (collo) |
| la **comparsa per lente** (indietro alla radice) | il tasto indietro entra con la stessa dissolvenza delle icone e un seme | **F3.3**, F4A.3 |
| una **trasformazione nella stessa pagina** | `FluidTopBarActions` è una `Row` che si ricompone: i tasti compaiono e spariscono in un fotogramma (`ui/fluid/FluidScreen.kt:1459`) | **F4A.2** su **F3.2** |
| **gruppi** (più icone in un vetro), spaziatori, principale che non si raggruppa | le azioni sono composable liberi, ognuna col suo vetro | **F4A.1**, F4A.2 |
| un **viaggio della barra che aspetta la conferma** del gesto, con un orologio suo e la ripresa senza salti | il morph segue il dito (`FluidPageAnchoredBar.kt:460`, zone morte del 10%) | **F4A.3**, F4A.6 per la ripresa |
| **forme diverse dalla capsula** e più di sei pezzi nel viaggio | un controllo che non è una capsula spegne tutto il viaggio (`FluidPageAnchoredBar.kt:469`); oltre 6 coppie solo dissolvenza (`maxPieces = 6`) | **F1B** (8 pezzi), **F4A.3** |
| la **tinta che cambia dentro un viaggio** (vetro normale ↔ tinto) | il viaggio non mescola la tinta del corpo | **F1B** (ricetta mescolata fra partenza e arrivo), F3.4 per il simbolo |
| il **titolo in linea che si scambia per sfocatura** | dissolvenza più un passo di 16 dp (`barTitleFrame`, `:433`, `BarTitleShiftDp`) | **F3.4**, F4A.2 (passo: ≤8 dp) |
| il **trabocco** come casa degli elementi (nascita e assorbimento nel «…») | non c'è trabocco | **F4A.1**, F4A.3 |
| il **tasto di ricerca che diventa campo**, con la lente che viaggia nitida | il campo anima la larghezza in un modificatore di layout | **F4B.2** |
| il **gruppo che diventa menu** e le icone che si sfocano via | il menu nasce dal tasto; gli altri tasti restano | **F5A** |
| il **tasto sorgente che diventa foglio** e resta consumato | — | **F5B** |
| la **regola sul movimento ridotto** per la barra | `LocalFluidMotionPolicy` c'è, la barra non ne ha una sua | F4A.2, F6.7 |

---

## Limiti del materiale

**Cosa non si può sapere dai riferimenti:**
- **Tempi sotto i 33 ms**: tutte le clip sono a 29,97 fps. Inizio della sfocatura e rottura del collo hanno ±1
  fotogramma.
- **Montaggio**: la clip del gesto indietro (284) ha un fermo di ≈2 s e forse è rallentata. Gli schermi
  ingranditi di 219 sono di classe B (la ripresa zooma). I tempi assoluti valgono solo per le clip col dito.
- **Quale API produce cosa**: non si sa se lo scambio che si vede è di UIKit, di SwiftUI o di una demo fatta a
  mano. La differenza fra il «più» nitido (219) e il «…» sfocato (284) può dipendere da questo.
- **Il raggio vero**: si misura σ in pixel del video, non in punti. Fra i due c'è lo zoom della ripresa, compensato
  esprimendo tutto in frazione dell'icona.
- **Il colore del corpo tinto**: nella clip di 16:02 la freccia è blu su un vetro appena azzurrino. Dal video non si
  separano la tinta del corpo e quella del simbolo.
- **Non osservati**: cambio di scheda; X ↔ indietro nei fogli; passaggio da e verso il trabocco; sottotitolo che
  compare; badge; tema scuro per tutti questi cambi; movimento ridotto; iOS 27 per questi cambi (R5 li dichiara
  invariati senza clip).
- **Il gesto indietro**: una clip di Apple montata e un'osservazione di terzi su simulatore. Mancano tre cose: un
  gesto tenuto fermo a metà, un annullo, un lancio.
- **Sfocatura di moto con otturatore**: a 30 fps una scia più corta di un fotogramma non si distingue. Le prove
  dicono che non c'è dove conta (σ uniforme ben più grande dello spostamento), ma su elementi molto veloci resta un
  dubbio.

**Cosa registrare da un iPhone vero** (iOS 27 e, se c'è, iOS 26). Registrazione dello schermo a 60 fps per la forma,
una camera a 240 fps per i tempi.
1. **Gesto indietro** in Note, Mail e Impostazioni: tenuto al 25/50/75%, annullato, lanciato; un push seguito subito
   dal gesto; il tasto indietro toccato tre volte di fila.
2. **Un tasto uguale nelle due pagine** (il «…» di Note, lista e nota) col tasto indietro e col gesto: chiude D2.
3. **Selezione** in Foto e in Mail («Seleziona» → «Fine» più la barra in basso), avanti e indietro.
4. **Cambio di scheda** con barre diverse (App Store, Foto, Musica): chiude D4.
5. **Un foglio con navigazione dentro** (X ↔ indietro).
6. **Trabocco**: barre con molti tasti (File, Pages) e iPad a finestra che si stringe.
7. **Ricerca** in basso con la tastiera, e la barra che si ritira (`OnScrollDown`).
8. Tutto in **tema scuro** e con **Riduci movimento** acceso.

---

## Fonti

Apple, documentazione (D):
- [UIBarButtonItem.identifier](https://developer.apple.com/documentation/uikit/uibarbuttonitem/identifier),
  [hidesSharedBackground](https://developer.apple.com/documentation/uikit/uibarbuttonitem/hidessharedbackground),
  [sharesBackground](https://developer.apple.com/documentation/uikit/uibarbuttonitem/sharesbackground),
  [setSymbolImage(_:contentTransition:options:)](https://developer.apple.com/documentation/uikit/uibarbuttonitem/setsymbolimage(_:contenttransition:options:)),
  [isSymbolAnimationEnabled](https://developer.apple.com/documentation/uikit/uibarbuttonitem/issymbolanimationenabled),
  [UISymbolContentTransition](https://developer.apple.com/documentation/uikit/uisymbolcontenttransition),
  [interactiveContentPopGestureRecognizer](https://developer.apple.com/documentation/uikit/uinavigationcontroller/interactivecontentpopgesturerecognizer),
  [UIViewControllerTransitionCoordinator](https://developer.apple.com/documentation/uikit/uiviewcontrollertransitioncoordinator),
  [Enhancing your app with fluid transitions](https://developer.apple.com/documentation/uikit/enhancing-your-app-with-fluid-transitions).
- SwiftUI: [ContentTransition](https://developer.apple.com/documentation/swiftui/contenttransition),
  [numericText(countsDown:)](https://developer.apple.com/documentation/swiftui/contenttransition/numerictext(countsdown:)),
  [blurReplace](https://developer.apple.com/documentation/swiftui/transition/blurreplace),
  [BlurReplaceTransition.Configuration](https://developer.apple.com/documentation/swiftui/blurreplacetransition/configuration-swift.struct),
  [GlassEffectTransition](https://developer.apple.com/documentation/swiftui/glasseffecttransition),
  [glassEffectTransition(_:)](https://developer.apple.com/documentation/swiftui/view/glasseffecttransition(_:)),
  [glassEffectID](https://developer.apple.com/documentation/swiftui/view/glasseffectid(_:in:)),
  [GlassEffectContainer](https://developer.apple.com/documentation/swiftui/glasseffectcontainer),
  [Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views),
  [ToolbarSpacer](https://developer.apple.com/documentation/swiftui/toolbarspacer),
  [sharedBackgroundVisibility](https://developer.apple.com/documentation/swiftui/toolbarcontent/sharedbackgroundvisibility(_:)),
  [ToolbarContent.matchedTransitionSource](https://developer.apple.com/documentation/swiftui/toolbarcontent/matchedtransitionsource(id:in:)).
- Simboli: [ReplaceSymbolEffect](https://developer.apple.com/documentation/symbols/replacesymboleffect),
  [MagicReplace](https://developer.apple.com/documentation/symbols/replacesymboleffect/magicreplace),
  [NSSymbolReplaceContentTransition](https://developer.apple.com/documentation/symbols/nssymbolreplacecontenttransition).
- WidgetKit: [Animating data updates](https://developer.apple.com/documentation/widgetkit/animating-data-updates-in-widgets-and-live-activities).
- HIG: [Motion](https://developer.apple.com/design/human-interface-guidelines/motion),
  [SF Symbols](https://developer.apple.com/design/human-interface-guidelines/sf-symbols),
  [Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars).

Apple, sessioni (W, con i video usati per O):
- [WWDC25 219 «Meet Liquid Glass»](https://developer.apple.com/videos/play/wwdc2025/219/) (4:41, 2:55; fotogrammi in `rif/`);
- [WWDC25 256 «What's new in SwiftUI»](https://developer.apple.com/videos/play/wwdc2025/256/) (2:11–2:27 barre che si trasformano nella navigazione; 4:03 scheda di ricerca);
- [WWDC25 284 «Build a UIKit app with the new design»](https://developer.apple.com/videos/play/wwdc2025/284/) (7:19–9:30 gruppi; 12:04–12:40 navigazione interrompibile; 13:35–14:10 menu e fogli dai tasti);
- [WWDC25 323 «Build a SwiftUI app with the new design»](https://developer.apple.com/videos/play/wwdc2025/323/) (7:53–10:40 barre e bordo; 20:41 badge);
- [WWDC25 356 «Get to know the new design system»](https://developer.apple.com/videos/play/wwdc2025/356/) (9:07–9:41);
- [WWDC25 337](https://developer.apple.com/videos/play/wwdc2025/337/) (Magic Replace con Draw);
- [WWDC24 10188](https://developer.apple.com/videos/play/wwdc2024/10188/) (Magic Replace);
- [WWDC24 10145](https://developer.apple.com/videos/play/wwdc2024/10145/) (zoom interattivo);
- [WWDC23 10258](https://developer.apple.com/videos/play/wwdc2023/10258/) (Replace sui `UIBarButtonItem`);
- [WWDC23 10158](https://developer.apple.com/videos/play/wwdc2023/10158/) (molle e ripresa);
- [WWDC18 803 «Designing Fluid Interfaces»](https://developer.apple.com/videos/play/wwdc2018/803/) (19:51 sfocatura di moto all'apertura delle app; 7:02 interruzione; 52:54 tracciamento 1:1).
- WWDC26 278 tramite R5.

Google (D):
- [Predictive back, linee guida di design](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back);
- [Support predictive back animations](https://developer.android.com/guide/navigation/custom-back/support-animations);
- [PredictiveBackHandler](https://developer.android.com/reference/kotlin/androidx/activity/compose/PredictiveBackHandler.composable);
- [BackEventCompat](https://developer.android.com/reference/kotlin/androidx/activity/BackEventCompat).

Terzi (T, non Apple):
- [liquid_glass_widgets, issue #353 e PR #360](https://github.com/sdegenaar/liquid_glass_widgets/issues/353)
  (barra durante il pop interattivo su iOS 26/27; materializzazione tarata a 120 fps);
- [react-native-numeric-text](https://github.com/AmatoGiulio/react-native-numeric-text) (misura di `numericText`, sfocatura quasi isotropa).

Ricerche del progetto: R1 `inventario-ios.md` (C1–C6, B1–B11, N1–N2, V1–V5), R4 `riferimenti-misure.md` (B1, B2,
B3, B6), R5 `ios27-differenze.md` (V5, E1, B11), R6 `icone-e-misure.md` (taglie), audit R2.

Attrezzi di questa ricerca (`artifacts/liquid-glass/ricerca/r7/tools/`):
- `v.py`: fotogrammi da ogni video, fogli a contatto;
- `abbina2.py`: screenshot → fotogramma;
- `hp.py`, `hp_larghezze.py`: screenshot scelti a mano, larghezze delle capsule;
- `fit_blur.py`, `fit_incrocio.py`: forma e quantità della sfocatura;
- `fig_isotropia.py`, `fig_curve.py`: figure di A;
- `schemi.py`, `casi.py`: schemi di B.

I tre video WWDC25 scaricati (256, 284, 323) stanno in `r7/video/`.
