# R4 · Misure sui riferimenti di Apple: ottica a riposo e movimento

Studio del 2026-11-10 sulla sessione WWDC25 219 «Meet Liquid Glass». Trasforma i riferimenti in numeri:
sono le specifiche contro cui giudicare il lavoro sull'engine. Indice dei frame e proprietà del
materiale stanno in `docs/liquid-glass/riferimenti.md`.

## Cosa conta per il piano

1. **Il bordo di luce è un filo, non una cornice**: 1–2% dell'altezza di una barra (spessore assoluto,
   non cresce con l'elemento), quasi saturo su **due archi opposti** (≈110° e ≈70°) e quasi assente
   altrove. L'engine oggi lo disegna simmetrico e uguale ovunque.
2. **Il corpo è più chiaro del fondo**, di 15–27 livelli su fondi chiari: un velo bianco del 30–45%
   su fondo chiaro e del 55–60% su fondo medio o scuro. È questo, con il filo, a staccare un tasto da
   un fondo piatto: la lente lì non ha niente da piegare.
3. **L'ombra dipende da cosa c'è sotto**: 4% sul fondo piatto, 18% sul testo (×4,9), con lo stesso
   scostamento (massimo a 0,2 altezze sotto il bordo). Il menu sulla stessa striscia bianca fa il 15%
   contro il 2% della barra.
4. **Il menu vero si apre in 330–430 ms**: un solo superamento del 3–6% verso il lato lontano dal
   tasto, nessuna seconda oscillazione visibile. I render ingranditi del video sono circa 2× più lenti.
5. **La sagoma in viaggio non è mai un rettangolo**: goccia inclinata, lobi, colli che si strozzano. Il
   contenuto nuovo è dentro dal primo frame, sfocato e deformato dalla lente, e si legge a metà corsa.
6. **Le icone si scambiano sfocandosi** in 200–330 ms, incrociandosi al massimo della sfocatura.
   L'icona che resta nei due stati non si sfoca.
7. **Il vetro compare modulando la lente**: 430 ms, parte più grande dell'8–10%, il simbolo si
   condensa da 2× a 1×. L'opacità del corpo non cambia mai.
8. **Tocco**: il tondo cresce a ×1,2 in 130–170 ms senza rimbalzo, si accende, segue il dito; al
   rilascio scende sotto la taglia del 2,7% e torna in 330 ms.
9. **Chiaro/scuro**: i piccoli girano con 130 ms di ritardo, corpo in 300 ms, icona in 500–700 ms
   passando per il contrasto zero. I grandi non girano: scuriscono in continuo (48 contro 22).
10. **Bordo dello scorrimento**: sfocatura più velo, non vetro. Il testo perde metà del contrasto a
    0,43 altezze di barra sotto i tasti e sotto i tasti ne resta il 5–20%.

## Il video e come si è misurato

| cosa | valore |
|---|---|
| file ufficiale | `artifacts/liquid-glass/riferimenti/video/wwdc2025-219_hd.mp4`: 1920×1080, H.264, **29,97 fps**, 35151 frame |
| variante 4K | `…/video/wwdc2025-219_2160p.mp4`: 3840×2160, HEVC 10 bit, 29,97 fps, stesso CDN (lista HLS della pagina), allineata al frame con la HD |
| cadenza nativa | 29,97 fps = **33,4 ms per frame**. Non esiste una versione a 60 fps |
| screenshot scelti a mano | sono **frame consecutivi** del video (uno ogni 33,4 ms): sequenza 1 = `f13046`→`f13061` (più quattro a riposo), sequenza 2 = `f28857`→`f28879`, l'ultimo è `f28888` |

Convenzioni: `fN` è il numero di frame (tempo = N × 1001/30000 s). Le misure di luminanza sono valori
sRGB 0–255 (luma Rec. 709), non luce lineare. Le coordinate sono della sorgente 4K. `H` è l'altezza
dell'elemento, `D` il diametro. Ogni tempo ha un errore di ±1 frame (±33 ms) per estremo.

Affidabilità delle clip, in ordine:

- **A · telefono con il dito** (4:43–4:46, 5:05–5:11): interfaccia vera dentro un telefono. Le più
  affidabili per i tempi. Sono comunque montate: il dito può essere composto sopra.
- **B · interfaccia ingrandita senza dito** (7:14–7:17, 4:47–5:00, 15:57–16:04, 6:33–6:55): tempi
  coerenti con le A entro uno o due frame. La ripresa spesso zooma piano, quindi le posizioni
  assolute derivano.
- **C · render dimostrativi** (5:01–5:04, 5:11–5:18, 3:51–4:06, 4:18–4:32, 15:15–15:40): materiale
  stilizzato, tempi spesso rallentati. Buoni per la sagoma e l'ordine delle fasi.
- **D · riprese in prospettiva** (1:44–1:56, 3:41–3:51, 4:10–4:18, 5:33–5:51, 12:05–12:23):
  non misurabili in piano. Solo durate e ordine.

Limiti comuni: la compressione allarga i fili sottili di 1–2 px e sporca le zone appena scoperte
(sembrano sfocate per 2–3 frame); i valori vicini a 255 sono saturi.

Attrezzi in `artifacts/liquid-glass/riferimenti/tools/` (vedi `LEGGIMI.md` lì), panoramiche a 1 fps in
`…/panoramica/`, collage in `…/collage/`, ritagli in `…/ritagli/`, slit-scan in `…/slit/`.

---

## A · Ottica a riposo

### A1 · Il bordo di luce

Ritaglio: `artifacts/liquid-glass/riferimenti/ritagli/A1_bordo_di_luce.jpg`

| proprietà | valore misurato | incertezza | frame |
|---|---|---|---|
| spessore, tasto tondo chiaro | 3–7 px su D = 290 px: **1,0–2,4% di D**, mediana 1,6% | ±1 px; la compressione allarga | `f8632` |
| spessore, capsula chiara | 5–8 px su H = 358 px: **1,5–2,1% di H** | ±1 px | `f28855` |
| spessore, tasto tondo scuro | 6–7 px su D = 430 px: 1,4–1,6% di D | ±1 px | `f12384` |
| spessore, menu | 2–4 px con barra alta ≈260 px nella stessa ripresa: ≈1% dell'altezza della barra, 0,2–0,3% dell'altezza del menu | ±1 px | `f9368`, `f13080` |
| regola dello spessore | **assoluto**: ≈1–2% dell'altezza di una barra, uguale su tasto, capsula e menu | media | — |
| dove è forte, tondo chiaro | due archi: **75°–185°** (centro ≈130°, alto a sinistra, largo ≈110°) e **280°–350°** (centro ≈315°, basso a destra, largo ≈70°). Angoli da destra in senso antiorario | ±15° (passo di misura) | `f8632` |
| quanto è forte, tema chiaro | picco 250–255 (saturo): +21…+53 sul corpo, +43…+80 sul fondo | il picco vero può superare 255 | `f8632`, `f28855` |
| fuori dagli archi | +2…+13 sul corpo: il filo quasi sparisce. Passaggio pieno→nullo in 15–30° | ±5 livelli | `f8632` |
| capsula chiara, lati | alto 253, basso 255, estremo sinistro 255, estremo destro 250; capsula vicina, lato interno 238 | qui tutti e quattro i lati sono accesi | `f28855` |
| tondo scuro | archi a **150°–210°** e **330°–45°** (sinistra e destra): picco 77–92 su corpo 33–46 (+40…+59); in alto 54–63 | l'asse cambia con la scena | `f12384` |
| menu | alto e basso chiari (188 e 245, +16 e +27 sul corpo); lati con **linea scura** di 2–4 px (−18 a sinistra, −5 a destra rispetto al fondo) | — | `f9368` |
| sfumatura | fuori: cade al fondo in 1–2 px. Dentro: torna al corpo in 4–8 px, poi un alone di +3…+5 livelli largo il 12–15% di H | ±2 px | `f28855` |
| linea scura | nessuna sugli archi accesi (subito fuori −2…−5 livelli, è l'ombra). Compare dove il filo manca | — | `f8632`, `f9368` |
| solo riflessi (11:06–11:15) | contorno sottile sempre visibile, più un arco chiaro di 90–120° e uno più debole opposto; nella dimostrazione fa ¾ di giro in 8,5 s | stima a occhio sugli angoli | `f19975`–`f20231` |
| frangia cromatica | visibile solo sulla lente grande (arancio/verde in basso a destra) | stima a occhio | `f1485` |

La lente spessa di 2:34 (`f4620`) è un caso a parte: linea scura in alto (−18) con alone interno di
+15 che decade sul 12% di H, banda bianca in basso spessa l'1,9% di H. Non è un controllo.

### A2 · Il corpo

| proprietà | valore misurato | incertezza | frame |
|---|---|---|---|
| corpo contro fondo, capsula chiara | 226–233 contro 200–210: **+19…+27** | ±2 | `f28855` |
| tondo chiaro | 200–216 contro 174–184: **+22…+27** | ±2 | `f8632` |
| menu chiaro (render) | **+17…+24**; la barra nella stessa ripresa +15…+17 | ±2 | `f9368`, `f9336` |
| capsula a 11:59 | 230–233 contro 218: +12…+15 | ±2 | `f21570` |
| su bianco puro (226) | 228,5: **+2**, il corpo non si distingue | ±1 | `f27430` |
| su nero puro | vetro chiaro **143**; vetro scuro 22 | ±2 | `f27466`, `f27520` |
| su foto media (luma 120–138) | barra 200, menu 206 | la ripresa zooma | `f13040`, `f13080` |
| velo bianco equivalente | 28–48% su fondo chiaro (178–218), 56–59% su fondo medio e scuro | calcolato su valori sRGB | — |
| gradiente alto→basso, capsula | 233 in alto, 228 al centro, 232–235 in basso: i bordi +4…+6 sul centro | ±1 | `f28855` |
| gradiente, pannello grande | 233 in alto, 230 al centro, 227 in basso | ±1 | `f27850` |
| gradiente, tondo | 216 in basso a sinistra, 200 a destra (Δ16, in diagonale) | ±3 | `f8632` |
| menu contro il fondo sfumato | segue il gradiente del fondo: +0…+4 in cima, +18 dal 10% di H in giù | ±2 | `f9368` |
| banda piegata, lente grande | centro non distorto per ≈60% del diametro; le linee piegano negli ultimi 35–45% del raggio, con uncino brusco negli ultimi ≈7% di D | **stima a occhio** | `f1485` |
| banda piegata, capsula sul testo | 10–15% di H | **stima a occhio** | `f21300` |

Ritaglio per la lente: `artifacts/liquid-glass/riferimenti/ritagli/A_lente_griglia_f1485_f1700.jpg`

### A3 · L'ombra

Ritaglio: `artifacts/liquid-glass/riferimenti/ritagli/A3_ombra.jpg`

Opacità = scurimento del fondo in valori sRGB. La capsula di 11:46–12:04 è alta 705 px.

| caso | opacità massima | dove sta il massimo | estensione | frame |
|---|---|---|---|---|
| capsula su fondo piatto chiaro | **3,7%** (218→210) | 0,20–0,24 H sotto il bordo basso | fino a ≈0,75 H sotto; sopra 1,5–2%, ai lati 2% | `f21570` |
| stessa capsula su testo | **18%** (218→179) | 0,16–0,20 H sotto | fino a ≈0,85 H sotto; sopra 11% | `f21300` |
| rapporto testo/piatto | **×4,9**, stessa forma e stesso scostamento | ±0,5 | — |
| barra chiara 16:02, fondo piatto | non rilevabile (<1%) | — | — | `f28855` |
| tondo 4:48 | 1–3% subito fuori dal filo | — | — | `f8632` |
| barra, render 5:11 | 2% | 0,15–0,3 H sotto | ≈0,5 H | `f9336` |
| menu, render 5:11 | 1% sotto e a destra, 0 sopra e a sinistra | — | ≈0,4 altezze di barra | `f9368` |
| barra su striscia bianca (7:15) | **2%** al bordo | al bordo | — | `f13040` |
| menu sulla stessa striscia | **15%** al bordo, 10% a +0,2, 5% a +0,4, 3% a +0,55 altezze di barra | al bordo | oltre 0,55 altezze di barra | `f13080` |

Forma: segue la sagoma, morbida, più ampia sotto, mai una linea di contatto netta. Dalla capsula si
ricava uno scostamento verso il basso di ≈0,10–0,15 H e una sfocatura di ≈0,3–0,4 H (derivato, non
misurato direttamente). Sopra una foto l'ombra non si misura: manca il fondo di confronto.

Il render di 5:11 contraddice la crescita dal tasto al menu (lì il menu ha meno ombra della barra):
vale la coppia di 7:15, che è interfaccia vera.

### A4 · Lo spessore con la taglia

| | tasto tondo | capsula | menu | foglio / pannello |
|---|---|---|---|---|
| bordo di luce | 1,0–2,4% di D | 1,5–2,1% di H | 0,2–0,3% della sua altezza | non misurato |
| corpo sul fondo chiaro | +22…+27 | +19…+27 | +17…+24 | +4…+10 su bianco 223 |
| smerigliatura (contrasto che resta) | non misurato | 26% (testo) | 13% (foto) | non misurato |
| ombra sulla stessa striscia bianca | — | 2% | 15% | non misurato |
| lente al bordo | non misurato | 10–15% di H (a occhio) | più marcata (a occhio) | non misurato |
| cambio chiaro/scuro | gira | gira | non gira | non gira (48 su nero) |

Cresce con la taglia: ombra (≈×7), smerigliatura (≈×2), e il vetro grande smette di girare. Non
cresce: lo spessore del filo. I due valori di smerigliatura vengono da fondi diversi (testo e foto):
il confronto è indicativo.

### A5 · La smerigliatura

Ritaglio: `artifacts/liquid-glass/riferimenti/ritagli/A5_smerigliatura.jpg`

| caso | valore misurato | incertezza | frame |
|---|---|---|---|
| testo nero sotto la capsula | deviazione standard 75→19: resta il **26%** del contrasto. Il nero (0) sale a 172, la carta da 221 a 232 | ±3 punti | `f21300` |
| nitidezza del testo | gradiente medio 2,20→0,22: resta il 10% | — | `f21300` |
| leggibilità | le parole si riconoscono come forme, le lettere no; sfocatura ≈3–5% di H | **stima a occhio** | `f21300` |
| foto sotto il menu | deviazione standard 35,8→4,7: resta il **13%**; intervallo 83–182 → 197–213 | la ripresa zooma fra i due frame | `f13040`, `f13080` |
| colore sotto il menu | foto (193,129,64) → menu (236,202,150): il colore resta, schiarito | ±5 | idem |
| sfondo sotto il vetro tinto | il bordo di uno scacco si allarga su 10–15% di H | ±3 punti | `f29865` |
| variante clear | nessuna smerigliatura: la foto si legge | a occhio | `f21934` |

### A6 · Chiaro e scuro

Ritaglio: `artifacts/liquid-glass/riferimenti/ritagli/A6_chiaro_scuro.jpg`

| caso | luminanza del corpo | frame |
|---|---|---|
| vetro chiaro su bianco (226) | 228 | `f27430` |
| vetro chiaro su nero, prima di girare | 143 (clip sintetica), 136 (interfaccia vera) | `f27466`, `f12360` |
| vetro scuro su nero | 22 (sintetica), 45 su fondo 28–50 (vera) | `f27520`, `f12372` |
| vetro scuro su bianco, prima di tornare | 168–180 | `f27616` |
| pannello grande su nero | 143 → **48** in ≈1,5 s: non gira | `f27934`→`f28030` |
| pannello a metà coperto | il velo segue la quota di nero: 61 (74%), 89 (47%), 111 (25%) | `f28102`–`f28126` |
| icone | scure 14–20 sul vetro chiaro; chiare 236 sul vetro scuro | `f12340`, `f12385` |

Il vetro piccolo gira quando il nero copre il 70–85% dell'elemento (clip sintetica, ±10 punti). I
tempi stanno nella scheda B6.

### A7 · La tinta

Ritaglio: `artifacts/liquid-glass/riferimenti/ritagli/A7_tinta.jpg`

| caso | colore misurato (R,G,B) | frame |
|---|---|---|
| rosso su bianco | (222, 0, 12), gradiente laterale 201→222→212 | `f29795` |
| rosso su scacchi | sul bianco (222, 0, 12); sul nero **(129, 0, 11)**: il 58% del valore, stessa tonalità | `f29865` |
| verde su nero | (35, 112, 11) | `f30300` |
| giallo su fondo chiaro | (245, 187, 11) | `f30180` |
| riempimento pieno su foto | (221, 15, 208) uniforme: opaco | `f30900` |
| tinta vera su foto | viola da (103, 0, 167) a (173, 59, 235): la luminosità segue la foto, ±17% | `f30985` |
| bordo del tasto tinto | filo più chiaro e meno saturo: (224, 103, 116) e (243, 23, 42) | `f29795` |

La tonalità resta, la luminosità segue lo sfondo sfocato sotto. Il tasto tinto proietta un alone del
suo colore sulla superficie vicina, largo circa un diametro (`f30460`–`f30500`, a occhio).

---

## B · Movimento

### B1 · Il tasto di una barra che diventa menu

**Clip.** (a) telefono con il dito, 5:05–5:11, classe A: la più affidabile. (b) menu sulla foto,
7:15–7:16, classe B: è la prima sequenza scelta a mano. (c) render ingrandito, 5:11–5:18, classe C:
circa 2× più lento e con la goccia bianca opaca, che nell'interfaccia vera non c'è.

**Durata.** Apertura 330–370 ms (a), 400–430 ms (b, menu di sette righe), ≈1000 ms (c).
Chiusura ≈200 ms di sagoma più 100 ms per l'icona (a), ≈870 ms (c).

Apertura (a), t = 0 a `f9148`:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 33 | il dito si alza; sul tasto compare una bolla, già lente |
| 33 | 100 | la bolla cresce in alto e a destra; le voci sono dentro dal primo frame, sfocate e deformate |
| 100 | 167 | massimo superamento: bordo alto **+6% dell'altezza** oltre il riposo, larghezza **+3,4%**, centro spostato del 4,5% della larghezza dal lato opposto al tasto |
| 167 | 330 | rientra senza oscillare (larghezza +1,8% a 267 ms, +0,3% a 333 ms); testo leggibile da ≈170–200 ms, nitido a ≈270 ms; prima e ultima riga deformate fino a ≈230 ms |
| 330 | 370 | a riposo; nessuna seconda oscillazione sopra lo 0,2% |

Chiusura (a), t = 0 a `f9187`:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 67 | le voci si sfocano, il bordo alto scende dell'1–4% |
| 67 | 133 | la sagoma crolla nel tasto in due frame |
| 133 | 200 | resta una bolla sul tasto |
| 200 | 300 | l'icona del tasto torna nitida |

Apertura (b), t = 0 a `f13046`:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 33 | la capsula di tre tasti si stringe al 60%, le icone si sfocano |
| 33 | 67 | goccia inclinata, senza icone, con un punto di luce forte |
| 67 | 100 | fagiolo verticale; il testo è già dentro, minuscolo e piegato |
| 100 | 267 | sagoma irregolare che cresce in alto a destra; le righe seguono la deformazione e si fanno nitide |
| 267 | 400 | quasi rettangolo, ancora panciuto e storto (lato sinistro più alto); prima e ultima riga deformate |
| 400 | 530 | a riposo; il vetro passa da limpido e rifrangente a smerigliato |

Apertura (c), t = 0 a `f9338`, misurata sui bordi:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 130 | la barra si stringe al 70% della larghezza, diventa bianca, le icone spariscono |
| 100 | 367 | la goccia sale; bordo alto al massimo a 367 ms: **+2,8% dell'altezza** oltre il riposo |
| 267 | 434 | compare il testo; larghezza al massimo a 434 ms: **+3,3%** |
| 434 | 734 | la larghezza scende a **−1,0%**; il corpo intero affonda del 3,3% dell'altezza (600 ms) |
| 734 | 1000 | torna a riposo |

Chiusura (c), t = 0 a `f9393`: il bordo alto cade in 330 ms; la barra passa da 74% a **103%** della
larghezza a 400 ms, scende a 99,0% a 600 ms, è a riposo a 870 ms.

**Sagoma.** Mai un rettangolo che cambia misura: capsula stretta, goccia, uovo, quadrato panciuto,
rettangolo. Un solo superamento nell'interfaccia vera; il render ne mostra un secondo dell'1%, forse
stilizzato. Il superamento va verso il lato lontano dal tasto.

**Contenuto.** Le voci non entrano in dissolvenza: sono dentro dal primo frame, sfocate, scalate e
piegate dalla lente. Le icone della barra si sfocano e spariscono nei primi 67–100 ms.

**Materiale.** In viaggio il vetro è limpido e molto rifrangente; la smerigliatura e l'ombra piena
arrivano negli ultimi 100–150 ms. Nei 500 ms prima che la sagoma si muova (`f13031`–`f13045`) le due
icone di sinistra sono già sfocate: lettura incerta, stato premuto o montaggio.

**Collage.**
`artifacts/liquid-glass/riferimenti/collage/B1_menu_telefono_apertura_f9143-9166.jpg`,
`…/collage/B1_menu_telefono_chiusura_f9185-9200.jpg`,
`…/collage/B1_menu_foto_f13036-13075.jpg`,
`…/collage/B1_menu_apertura_render_f9336-9367.jpg`,
`…/collage/B1_menu_chiusura_render_f9391-9414.jpg`;
slit-scan in `…/slit/B1_*.png`.

### B2 · La barra che cambia contesto

**Clip.** (a) telefono con il dito, 4:44–4:46, classe A (il dito copre l'inizio). (b) render
ingrandito, 4:47–5:00, classe B: tre stati in ciclo. (c) 16:02–16:04, classe B: la seconda sequenza
scelta a mano. (d) «Select» → cerchio, triangolo, «Done», 5:01–5:03, classe C, rallentata.

**Durata.** 270–330 ms (a), 330 ms più coda (b), 430 ms per le icone e ≈1300 ms per l'ultimo
assestamento della larghezza (c), 1070 ms fino alla divisione e 1300 ms a riposo (d).

(b), t = 0 a `f8634`:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 67 | le icone cominciano a sfocarsi |
| 67 | 133 | sfocatura massima; l'icona di sinistra cambia sotto la sfocatura |
| 67 | 267 | la capsula di tre icone si chiude da sinistra verso il «+», che resta |
| 267 | 333 | cerchio formato, icone nitide |
| 333 | 530 | resta un'impronta debole dove stava la capsula (forse compressione) |

(c), t = 0 a `f28857`, misurata sui bordi (H = 358 px):

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 130 | la capsula di destra passa da 1,58 H a ≈1,1 H, chiudendosi da sinistra; poi la misura si perde nella tinta |
| 67 | 230 | le icone vecchie si sfocano; a 200–230 ms si incrociano con le nuove |
| 0 | 330 | il bordo sinistro della capsula di sinistra **rientra di 0,19 H** prima di allargarsi |
| 230 | 430 | le icone nuove tornano nitide; la terza compare per ultima |
| 330 | 600 | la capsula si allarga da 2,2 H a 3,08 H; il bordo destro supera il riposo dello 0,5% |
| 600 | 900 | larghezza al massimo: **+1,3%** |
| 900 | 1370 | rientro lento |

(d), t = 0 a `f9031`:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 130 | il testo si sfoca, il vetro diventa bianco e si gonfia (≈+15%, a occhio) |
| 130 | 400 | goccia bianca |
| 400 | 570 | si allunga in orizzontale, con gobbe |
| 600 | 700 | contenuto nuovo sfocato; tre lobi uniti |
| 730 | 930 | il contenuto si fa nitido, i colli si assottigliano |
| 1000 | 1070 | i colli si strozzano e si rompono: prima il sinistro, un frame dopo il destro |
| 1070 | 1300 | i lobi si assestano |

**Sagoma.** Le capsule cambiano larghezza in continuo. La fusione e la divisione passano per un
collo che si assottiglia mentre i lobi restano tondi (unione morbida); la rottura arriva ≈200 ms
prima del riposo.

**Contenuto.** Sfocatura, non dissolvenza: le icone si incrociano al massimo della sfocatura. In
(b) il «+», presente nei due stati, resta quasi nitido. Durante il cambio il vetro schiarisce, e il
tondo di sinistra si gonfia di ≈10% (a occhio).

**Collage.**
`artifacts/liquid-glass/riferimenti/collage/B2_barra_telefono_f8536-8570.jpg`,
`…/collage/B2_barra_render_4m48_f8632-8659.jpg`,
`…/collage/B2_barra_contesto_16m02_f28855-28890.jpg`,
`…/collage/B2_select_done_f9029-9076.jpg`.

### B3 · La comparsa del vetro per modulazione della lente

**Clip.** Tasto di riproduzione sopra un video di dune, 2:56–3:09, classe B/C (senza dito).
**L'indice dà 2:33–2:48, ma quella clip è il confronto fra materiale vecchio e lente** (una pillola
che scorre dentro e fuori): la comparsa di cui parla la trascrizione è questa.

**Durata.** Comparsa 430 ms, scomparsa 470 ms.

Comparsa, t = 0 a `f5399`:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 67 | solo il contorno sottile, 8–10% più largo del riposo (a occhio); rifrazione quasi nulla |
| 100 | 230 | il simbolo compare dilatato ≈2× e traslucido; la rifrazione cresce |
| 270 | 370 | il simbolo si contrae alla sua forma e diventa opaco; la lente si stringe |
| 370 | 430 | a riposo |

Scomparsa, t = 0 a `f5368`:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 130 | il simbolo svanisce |
| 130 | 400 | la rifrazione scende a zero: le linee delle dune tornano dritte |
| 400 | 470 | resta il contorno, poi niente |

**Sagoma e materiale.** Il corpo non cambia opacità: cambiano la forza della lente e il filo.

**Collage.**
`artifacts/liquid-glass/riferimenti/collage/B3_comparsa_play_f5397-5420.jpg`,
`…/collage/B3_comparsa_play_f5290-5400_ogni2.jpg`,
`…/collage/B3_comparsa_lente_f4496-4690_ogni4.jpg` (la clip 2:30–2:36).

### B4 · Il tocco

**Clip.** Tasto tondo, 3:51–3:54, classe C con il dito. Capsula trascinata, 4:04–4:05, classe C con
il dito. Bagliore propagato, 12:05–12:10, classe D. Capsula «Play», 12:11–12:23, classe D.

Tasto tondo, pressione a `f6950`, rilascio a `f6967`:

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 133–167 | cresce a **×1,19–1,20**, senza superamento |
| 0 | 233 | si accende fino quasi al bianco; l'icona si sfoca e cresce; il bagliore continua a salire finché il dito resta |
| tenuto | — | segue il dito: spostato di ≈0,23 D e allungato a ellisse (≈1,15:1, a occhio) |
| rilascio +0 | +167 | torna alla taglia di riposo |
| +167 | +200 | scende sotto: **−2,7%** |
| +200 | +330 | torna a riposo; la posizione supera il riposo dell'11% dello spostamento a +300 ms |
| +0 | +500 | il bagliore si spegne, dimezzandosi ogni ≈100 ms |

Capsula trascinata: alla pressione si allarga a ×1,17 in 167 ms (misura sporcata dall'alone); il
bagliore parte sotto il dito e riempie la capsula in ≈330 ms; al rilascio torna in ≈270 ms, supera
del 4% dello spostamento, ferma a ≈400 ms.

Bagliore propagato: si accende in ≈300 ms e si spegne in ≈300 ms. «Play»: prima si stringe a ≈×0,9,
poi si allarga a ≈×1,2 con il bagliore e torna in ≈500 ms (tutto a occhio, ripresa in prospettiva).

**Collage.**
`artifacts/liquid-glass/riferimenti/collage/B4_tocco_tasto_f6940-7018_ogni2.jpg`,
`…/collage/B4_flessione_capsula_f7300-7384_ogni2.jpg`,
`…/collage/B4_bagliore_propagato_f21776-21880_ogni3.jpg`,
`…/collage/B4_bagliore_play_f21934-22036_ogni3.jpg`.

### B5 · I controlli che salgono nel vetro

**Clip.** Interruttore, 4:18–4:23, classe C con il dito, quattro scatti. Cursore, 4:28–4:32,
classe C/D con il dito.

**Durata.** Uno scatto dell'interruttore: ≈530 ms (16 frame).

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 70–100 | il pomello bianco diventa lente limpida e cresce oltre la traccia (≈×1,5–1,6, a occhio) |
| 100 | 300 | attraversa; sotto la lente il colore della traccia cambia e si vede rifratto |
| 300 | 430 | la lente si richiude nel pomello bianco; resta una macchia di colore sfocata |
| 430 | 530 | a riposo |

Cursore: la lente si allunga nel verso del moto (≈×1,5 nei tratti veloci, a occhio); al rilascio
torna pomello bianco in 130–200 ms.

**Collage.**
`artifacts/liquid-glass/riferimenti/collage/B5_interruttore_f7752-7880_ogni2.jpg`,
`…/collage/B5_cursore_f8040-8146_ogni2.jpg`.

### B6 · Il passaggio chiaro/scuro mentre il contenuto scorre

**Clip.** Tasto sopra contenuto che scorre, 6:52–6:53, classe B: la più affidabile. Clip sintetica,
15:15–15:40, classe C: bianco e nero puri, lenta.

**È un incrocio, non uno scatto.** Interfaccia vera, t = 0 quando lo scuro copre tutto il tasto
(`f12360`):

| da ms | a ms | cosa accade |
|---|---|---|
| 0 | 130 | niente: il vetro chiaro resta a 136 sopra lo scuro |
| 130 | 430 | il corpo scende da 136 a 45, quasi tutto nei primi 200 ms |
| 230 | 270 | l'icona scura e il corpo hanno la stessa luminanza: **contrasto zero per un frame** |
| 270 | 600 | l'icona sale al chiaro: 90% a 600 ms |
| 600 | 830 | coda dell'icona fino a 236 |

Clip sintetica: verso lo scuro il corpo passa da 143 a 22 in ≈1,0 s, quasi lineare; verso il chiaro
da 180 a 228 in ≈2 s, con il 77% fatto nel primo secondo. I grandi non girano (A6).

Nessun collage dedicato: i numeri vengono da `tools/flip.py` e da una sonda sul tasto.

### B7 · Il bordo dello scorrimento

**Clip.** Posta su telefono, 9:07–9:41, classe B. Finestra del Mac, 9:51–10:07, classe B.
Altezza dei tasti della barra: 140 px = 1 H.

| proprietà | valore misurato | incertezza | frame |
|---|---|---|---|
| contrasto del testo, pieno | da 0,95 H sotto il bordo basso dei tasti in giù | ±0,1 H | `f16636`–`f16700` |
| contrasto al 50% | **0,43 H sotto** il bordo basso dei tasti | ±0,1 H | idem |
| al bordo basso dei tasti | 20% | ±5 punti | idem |
| al centro dei tasti | 8% | ±3 punti | idem |
| sopra i tasti, fino in cima | 3–5% (rumore) | — | idem |
| tratto in cui il contrasto cade | ≈1,2 H; la zona intera è alta 3,6 H dalla cima dello schermo | ±0,2 H | idem |
| velo verso il colore del fondo | 36% a 0,17 H sotto i tasti, 22% a 0,4 H, 13% a 0,6 H, 6% a 0,85 H, 0 a 1,1 H | ±5 punti | `f17058`, `f17046` |
| su contenuto scuro | scurisce invece di schiarire: ≈0,8 del valore all'altezza del bordo basso dei tasti | **stima indiretta**, ±0,1 | `f17132`, `f17120` |
| variante dura | fascia uniforme che nasconde tutto (contrasto 1%), taglio netto in meno di 0,2 altezze di barra | ±0,1 | `f17800`–`f17990` |

Il testo perde contrasto molto prima di quanto dica il solo velo: il resto è sfocatura progressiva.
Il contenuto si scioglie nel colore del fondo, non in un grigio. Nessuna fascia di vetro.

### B8 · Altre transizioni trovate

| transizione | dove | cosa si vede | misura |
|---|---|---|---|
| foglio che sale | 4:05,6–4:06,1 | sale in ≈330 ms, supera appena e rientra | a occhio |
| tasto tinto che si stacca dalla capsula | 16:56,2–16:56,6 | esce in 350–400 ms con un alone del suo colore | a occhio; ritaglio sbagliato |
| luce che corre sul bordo | 11:06–11:15 | l'arco chiaro fa ¾ di giro in 8,5 s | a occhio |
| vecchio materiale contro lente | 2:30–2:36 | la pillola smerigliata e poi la lente scorrono dentro e fuori | non misurata |
| lente che cambia forma | 0:46–1:06 | cerchio, capsula, menu, due forme fuse | non misurata |
| strati esplosi | 6:23–6:33, 10:45–11:06 | gli strati si separano e si ricompongono | non misurata |
| pressione e tinta della barra | 15:59–16:01 | i tasti diventano azzurri | non misurata |
| tinta che cambia colore | 16:05–16:08, 17:13–17:19 | il colore passa per incrocio | non misurata |
| finestra che perde il fuoco | 12:36–12:45 | il vetro si ritira | non misurata |
| accessibilità | 18:15–18:42 | tre varianti del materiale | non misurata |

Collage: `artifacts/liquid-glass/riferimenti/collage/B8_luce_sul_bordo_f19975-20235_ogni8.jpg`,
`…/collage/B8_divisione_tasto_tinto_f30440-30500.jpg` (inquadra sotto la barra: mostra solo l'alone).

---

## Specifiche di sintesi

Fiducia: **alta** = misurato su interfaccia vera o su più clip concordi; **media** = misurato su una
clip o su un render; **bassa** = stima a occhio o derivata.

Ottica:

| regola | fiducia |
|---|---|
| Bordo di luce: 1–2% dell'altezza di una barra, spessore assoluto, uguale su tasto, capsula e menu | alta |
| Bordo di luce: pieno su due archi opposti (≈110° e ≈70°), quasi nullo altrove, passaggio in 15–30° | alta sul tondo chiaro, media in generale |
| Asse degli archi: alto-sinistra / basso-destra nel tema chiaro; cambia con la scena e può muoversi | media |
| Intensità del filo: +40…+55 livelli sul corpo, chiaro e scuro | alta |
| Dentro il filo: alone di +3…+5 livelli largo il 12–15% di H | media |
| Dove il filo manca, sugli elementi grandi: linea scura di 2–4 px, −5…−18 livelli | media |
| Corpo: velo bianco del 30–45% su fondo chiaro, 55–60% su fondo medio e scuro | alta |
| Corpo: bordi alto e basso +4…+6 livelli sul centro | media |
| Ombra: 4% su fondo piatto, 18% su testo, massimo a 0,2 H sotto il bordo, estesa 0,75–0,85 H | alta |
| Ombra: scostamento ≈0,10–0,15 H, sfocatura ≈0,3–0,4 H | bassa |
| Ombra del menu: ≈7× quella della barra sullo stesso fondo (15% contro 2%) | media |
| Smerigliatura: capsula lascia il 26% del contrasto del testo, menu il 13% di una foto | media |
| Vetro piccolo: gira allo scuro (corpo ≈22–45); vetro grande: non gira, scurisce in continuo (≈48 su nero) | alta |
| Tinta: tonalità fissa, luminosità 100% su bianco e 58% su nero, sfondo sfocato sul 10–15% di H | alta |
| Lente: centro non distorto, piega concentrata nell'ultimo 10–15% di H sui controlli | bassa |

Movimento:

| regola | fiducia |
|---|---|
| Apertura del menu: 330–430 ms, un superamento del 3–6% verso il lato lontano dal tasto, nessuna seconda oscillazione | alta |
| Chiusura del menu: 200 ms di sagoma, icona nitida a 300 ms | alta |
| Molla dell'apertura: smorzamento 0,65–0,75, rigidezza 500–800 (massa 1), da tarare | bassa |
| Contenuto del menu: dentro dal primo frame, sfocato e deformato; leggibile a ≈50% della durata, nitido a ≈70% | alta |
| Materiale in viaggio: limpido e rifrangente; smerigliatura e ombra piena negli ultimi 100–150 ms | media |
| Cambio di contesto: icone sfocate e incrociate in 200–330 ms; chi resta nei due stati non si sfoca | alta |
| Capsule: larghezza continua, superamento finale ≤1,3%, rientro iniziale di ≈0,2 H prima di allargarsi | media |
| Fusione e divisione: collo che si strozza, rottura ≈200 ms prima del riposo | media |
| Comparsa: 430 ms, lente da +8–10% a 0, simbolo da 2× a 1×, opacità del corpo ferma | media |
| Pressione: ×1,2 in 130–170 ms senza rimbalzo; bagliore pieno in 230 ms | media |
| Rilascio: −2,7% a 170–200 ms, riposo a 330 ms; posizione oltre il riposo dell'11% dello spostamento | media |
| Molla del rilascio: smorzamento 0,55–0,60, rigidezza 150–200 (massa 1), da tarare | bassa |
| Bagliore al rilascio: si dimezza ogni ≈100 ms, spento a 500 ms | media |
| Interruttore: lente in 70–100 ms, corsa 200 ms, chiusura 130 ms; lente ≈×1,5 il pomello | media |
| Chiaro/scuro: ritardo 130 ms, corpo in 300 ms, icona in 500–700 ms con un frame a contrasto zero | alta |
| Bordo dello scorrimento: contrasto al 50% a 0,43 H sotto i tasti, 5–20% sotto i tasti; sfocatura più velo | alta |
| Bordo duro: fascia opaca uniforme, taglio in meno di 0,2 altezze di barra | media |

Le rigidezze sono ricavate dal tempo del picco e dal superamento come se fosse una molla semplice
che parte da ferma: indicano l'ordine di grandezza, non il valore.

## Cosa non si può misurare da qui

- **Sotto i 33 ms**: il video è a 29,97 fps. Le fasi più corte di un frame (inizio dell'apertura,
  crollo della chiusura) hanno un errore del 30–50%.
- **Misure in dp o in punti**: la scala del video non è quella di un telefono. Tutto è in frazioni.
- **I parametri veri delle molle**: si vedono superamento e tempi, non massa, rigidezza e
  smorzamento. Le clip C sono rallentate di una quantità non dichiarata (≈2× per il menu).
- **La forza della lente** (indice di rifrazione, profilo dello spessore): servirebbe un fondo a
  griglia sotto un controllo vero. C'è solo sotto la lente grande dimostrativa.
- **Il raggio di sfocatura**: si misura il contrasto che resta, non il raggio.
- **L'ombra sopra una foto**: manca il fondo di confronto.
- **Le clip in prospettiva** (1:44–1:56, 3:41–3:51, 4:10–4:18, 5:33–5:51, 12:05–12:23): solo durate.
- **La luce che reagisce al movimento del dispositivo**: detta nella sessione, non mostrata in modo
  misurabile.
- **Il tema scuro**: un solo tasto e una clip sintetica. Mancano capsula, menu e ombra in scuro.
- **Valori oltre 255**: il filo di luce è saturo; il suo picco vero non si conosce.
- **Risposta aptica e suono**: non sono nel video.
- **Non analizzati per tempo**: barre laterali e luce che si riversa (7:42–8:30), varianti regular e
  clear (14:03–14:45), tinta sull'azione principale (17:21–18:03), accessibilità (18:15–18:42).
