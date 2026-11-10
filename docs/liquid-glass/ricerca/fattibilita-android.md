# R3 · Fattibilità tecnica su Android

Ricerca del 2026-11-10 sull'engine 3.1.2, senza dispositivo: codice dell'engine, sorgenti delle
librerie a monte, documentazione, e ciò che il banco di render (`tools/glassbench/`) lascia provare.
Ogni affermazione porta la sua prova: **[banco]** provato rendendo sul PC, **[codice]** letto nei
sorgenti (dell'engine o di una libreria), **[doc]** documentazione o articolo, **[da misurare]**
non verificabile senza dispositivo.

## Cosa conta per il piano

1. **Un materiale solo è fattibile**, ma va fatto in **due strati**, non in una passata: il *corpo* (sfocatura, lente, dispersione, tinta, ombreggiatura) che si ricalcola solo quando cambia lo sfondo o la sagoma, e la *luce* (filo speculare, bagliore, luce dal sensore) senza ingresso di sfondo, che si può animare a ogni fotogramma senza rifare la sfocatura.
2. **Due ingressi nello stesso shader non esistono in una catena di `RenderEffect`**: l'API ne collega uno. Le vie sono tre (grafo con `Plus` come Haze, atlante nitido+sfocato in una cattura, mescola a monte): si parte dalla mescola a monte, che basta per il vetro di Apple.
3. **Un solo campo di distanza a N pezzi regge fermo e trasformazione** **[banco]**: una sonda con 5 pezzi fusi, lente, luce, ombra e due ingressi compila e rende. Tetto consigliato 4 pezzi per gruppo (8 al massimo), varianti dello shader per 1, 2, 4, 8 pezzi dalla stessa sorgente.
4. **Difetto trovato dal banco, da correggere prima di tutto**: i due shader poligonali di Fluid-physics non compilano (`half4 flat`: parola riservata), e la sonda di salute dell'engine in quel caso porta **tutto** Fluid-physics al livello `Balanced` (niente lente né ponti). Introdotto il 2026-11-02, è nella 3.0.0. Da confermare sul tablet con `adb logcat -s FluidPhysics`.
5. **L'adattività si fa in due velocità**: tinta, tono e ombra nello shader (continui, senza latenza); il giro chiaro/scuro delle icone sulla CPU, da una miniatura letta poche volte al secondo, con isteresi e dissolvenza. La via è già scritta nel catalogo di Kyant.
6. **Il bordo dello scorrimento richiede una sfocatura a due passate separabili con raggio per pixel** **[banco]**: una passata sola a 24 campioni lascia un disegno a spirale sul testo; la maschera d'alfa di oggi lascia l'immagine doppia. La tecnica è quella di Haze e di Compose 1.13.
7. **Haze 2.x (Apache-2.0) ha già fatto e misurato metà di questo progetto** su Pixel 6: renderer fuso, gruppi che si fondono (smooth minimum, tetto 4), scala adattiva con isteresi. È la fonte principale di idee e di numeri; a monte di Kyant non c'è nulla di nuovo.
8. **Flessione a gel e ombra nello shader chiedono che la sagoma sia ritagliata dallo shader**, non dal `RenderNode`, e che il layer abbia un margine: è il modello di `fluidPhysicsSurface`, da estendere a tutte le lastre.
9. **Il costo nuovo sta nel numero di volte in cui la catena si riesegue**, più che nei conti per pixel: ogni uniform che cambia rifà anche la sfocatura. Le leve che contano: strato della luce separato, una cattura per contenitore invece che una per tasto, risoluzione, tenuta della cattura.
10. **Il banco funziona** e riproduce il vetro di oggi in modo riconoscibile (confronto con uno screenshot vero); non misura tempi né precisione della GPU: quelle restano prove da tablet.

## Lo strumento: cosa il banco ha permesso di provare

`tools/glassbench/` (README accanto). skia-python 144.1 si installa e compila gli shader runtime: non
è servita una seconda via.

| prova | esito |
|---|---|
| compilazione di tutte le sorgenti AGSL dell'engine | 6 su 8 compilano; **2 no** (vedi cap. 2) |
| il vetro di oggi (tasto 44 dp, capsula con tre icone, menu) su grigio sfumato, testo, fotografia, chiaro e scuro | `artifacts/liquid-glass/bench/oggi-*.png` |
| somiglianza con l'engine vero | `confronto-reale-vs-banco.png`: stessi tasti di `08-chats-scuro.png`, stesso riempimento (60,55,70 contro 58,54,73 con palette stimata), stesso filo pieno sopra e sotto e spento ai fianchi |
| un materiale in una passata, due ingressi, 5 pezzi fusi | `sonda-materiale-*.png` (shader `shaders/sonda-materiale.agsl`) |
| comparsa per lente | `sonda-comparsa.png` |
| bordo dello scorrimento, due tecniche | `sonda-bordo-scorrimento.zoom.png` |
| lo shader di gruppo di Fluid-physics com'è oggi | `fluidphysics-gruppo-oggi.png` |
| oggi, sonda e Apple affiancati | `confronto-oggi-sonda-apple-grigio.png` |

Le sonde sono prove che una cosa *si può scrivere*: i loro numeri sono messi a occhio e non sono una
proposta di materiale.

Due difetti dell'engine emersi come effetto collaterale, entrambi **[banco]** + **[codice]**:

- **`flat` riservata** (cap. 2).
- **`radiusAt(coord, cornerRadii)`** negli shader di Kyant (`Shaders.kt:70, 107, 173, 195`, uguale a
  monte) riceve la coordinata del layer, sempre positiva, invece di quella centrata: sceglie sempre
  `radii.z`. Una lastra con angoli diversi (un foglio arrotondato solo in alto) ha lente e bordo
  calcolati con il raggio in basso a destra. Sparisce da solo quando il campo nuovo sostituisce
  questi shader.

## 1. Un materiale in una passata

### Come si compongono sfocatura e shader

- `RenderEffect` è un grafo di `SkImageFilter`: `createBlurEffect`, `createColorFilterEffect`,
  `createBlendModeEffect(dst, src, mode)`, `createChainEffect(outer, inner)`, `createShaderEffect`
  dalla API 31; `createRuntimeShaderEffect(shader, nomeUniform)` dalla 33 **[doc]**.
- HWUI applica il grafo all'**immagine del layer** del `RenderNode` (alla sua risoluzione, non a
  quella dello schermo) e tiene il risultato finché né il contenuto né l'effetto cambiano. È il
  motivo per cui oggi `backdropScale` fa risparmiare davvero, e per cui «il materiale registrato di
  nuovo» costa una catena intera (commenti in `DrawBackdropModifier.kt`, coerenti con le misure
  dell'engine). **[codice]**
- Conseguenza che decide l'architettura: **un uniform che cambia è un `RenderEffect` nuovo, e un
  `RenderEffect` nuovo rifà tutto il grafo, sfocatura compresa**. Se la luce del tocco o del sensore
  vive nello stesso shader della lente, ogni suo fotogramma ripaga la sfocatura. Se Skia riusi il
  risultato intermedio quando il nodo di sfocatura è lo stesso oggetto **[da misurare]**.

### Due ingressi: no, e le tre vie

`createRuntimeShaderEffect` collega **un** `uniform shader` al contenuto del nodo. Gli altri figli
si possono impostare con `RuntimeShader.setInputShader`, ma solo con uno `Shader` vero (bitmap,
gradiente): non esiste uno shader che rappresenti «il risultato di un altro effetto» **[doc]**. Su
Skia desktop due figli funzionano **[banco]**, quindi il limite è dell'API di Android e non del
linguaggio.

| via | come | costo | stato |
|---|---|---|---|
| **A. mescola a monte** (consigliata per partire) | `createBlendModeEffect(Plus, filtroColore(w, nitido), filtroColore(1-w, sfocato))`, poi lo shader con un ingresso solo | nessuna passata di shader in più; la mescola è uniforme sulla lastra | è ciò che fa Haze (`GlassRenderEffectFactory.android.kt`, `createGlassDepthInputRenderEffect`) **[codice]** |
| **B. grafo con due shader** | due nodi runtime, uno legge lo sfocato e scrive il materiale lasciando libero il bordo, l'altro legge il nitido e scrive solo il dettaglio rifratto; somma con `Plus` | il campo si valuta due volte | Haze, ADR-0003, validato su Pixel 6 **[codice]** |
| **C. atlante** | nella stessa cattura si disegnano il fondale nitido e, accanto, un layer figlio con la sua sfocatura; lo shader legge `content.eval(coord)` e `content.eval(coord + spostamento)` | un layer in più, cattura larga il doppio | non provata da nessuno: **[da misurare]** |

Il vetro di Apple ha smerigliatura uniforme sulla lastra, quindi A basta per il corpo. Il secondo
ingresso serve davvero in tre casi: tenere **nitido il bordo rifratto** (Haze lo fa con B), colorare
l'**ombra**, stimare il **contrasto locale** (|nitido − sfocato|). Il piano può partire da A e
aggiungere B solo sul bordo, se la resa lo chiede.

### Cosa mettere dove

| strato | contenuto | quando si riesegue |
|---|---|---|
| corpo (catena di `RenderEffect` sulla cattura) | saturazione → sfocatura → mescola → **uno shader**: campo, lente, dispersione, tinta, tono adattivo, ombreggiatura del corpo, maschera della sagoma | cattura nuova, sagoma o taglia cambiate, pressione |
| luce (shader disegnato con `Paint.shader`, senza ingresso) | filo speculare da due sorgenti, bagliore interno, risposta al tocco, luce dal sensore | a piacere: tocca solo i pixel del bordo e del bagliore |
| ombra | maschera sfumata tenuta in cache (come oggi) con alfa e colore adattivi; nello shader solo durante una trasformazione | taglia o sagoma cambiate |

Haze è arrivato alla stessa divisione: «Rim, interaction lighting, and group-alpha composition
remain separate» (ADR-0003) **[codice]**. La sonda del banco mette tutto in uno shader per
dimostrare che *si può*; non è la forma da spedire.

### Limiti di AGSL che contano

| limite | conseguenza |
|---|---|
| cicli solo `for` con limite costante; uscita con `break` su un uniform | N pezzi = costante di compilazione; si esce prima con `if (float(i) >= pieceCount) break;` (già così in Fluid-physics) |
| indici di array: costanti o la variabile del ciclo | niente `pieces[j]` con `j` calcolato: il pezzo precedente si porta in una variabile (già così) |
| uniform a matrice di `float`, `float2`, `float4`; `int` dalla 33 | i pezzi arrivano come `float4 pieceRect[N]`; il tetto pratico è la memoria degli uniform del driver (l'engine usa già `float2 verts[96]`) |
| niente derivate (`dFdx`, `fwidth`) | l'antialias della sagoma si fa con la distanza (uniform `aa`), la normale con il gradiente analitico |
| niente accesso diretto alla texture: solo `figlio.eval(coord)` | ogni `eval` di un'immagine è una lettura di texture; niente mipmap, niente LOD |
| `half` può essere a 16 bit | distanze e coordinate in `float`, sempre; `half` solo per i colori. Sul PC non si vede **[da misurare]** |
| parole riservate di GLSL (`flat`, `noperspective`, `input`, `half`…) | vedi cap. 2: il banco le trova |
| una sorgente = un programma compilato al primo disegno | ogni variante nuova è uno scatto al primo uso: scaldarle all'avvio **[da misurare]** |

**Da misurare sul dispositivo**: tempo di RenderThread della catena A su tasto, barra, menu, a
risoluzione piena e ridotta; costo di un cambio di soli uniform (rifà la sfocatura?); la via C;
scatto alla prima compilazione di ogni variante; bande di precisione sui gradienti del corpo.

## 2. Un solo motore per fermo e trasformazione

### Prima: il difetto di oggi

`PolyMorphRefractionShaderString` e `PolyMorphDispersionShaderString`
(`FluidPhysicsShaders.kt`, blocco `PhysicsDropBandBlock`) dichiarano `half4 flat = …`. `flat` è una
parola riservata del lessico di SkSL: `error: 70: expected ';', but found 'flat'` **[banco]**;
rinominata la variabile compilano **[banco]**. Il lessico è lo stesso su Android (AGSL è SkSL), e
`FluidPhysicsShaderHealth.probe()` compila proprio quello shader: se fallisce, il verdetto resta per
tutto il processo e il livello scende a `Balanced` per **ogni** famiglia, gruppi compresi
(`FluidPhysicsTier.kt:40-46, 79-86`) **[codice]**. Il blocco è entrato con il commit `c4d338a` del
2026-11-02 ed è nella 3.0.0.

Se è confermato, da allora i morph girano senza lente né ponti liquidi, e nessuno se ne è accorto
perché il ripiego è silenzioso. **Prova**: `adb logcat -s FluidPhysics` all'avvio di un'app sulla
3.0+ («Shader di Fluid-physics rifiutati: tier Balanced»). Peso sul piano: ciò che Alessio giudica
oggi di goccia e rimbalzo potrebbe essere il ripiego.

### Il campo a N pezzi

Lo schema c'è già (`SlabGroupRefractionShaderString`): smooth minimum polinomiale, gradiente
mescolato con lo stesso `h`. Il gradiente mescolato è **esatto** dentro la zona di fusione, non
un'approssimazione: derivando `d = mix(b, a, h) − k·h·(1−h)` i termini in `∇h` si annullano. La
sonda lo riusa con luce e ombra sopra **[banco]**.

Costo per pixel, misurato sul PC in software (**numero di istruzioni, non tempo di GPU**):

| shader | costo relativo |
|---|---|
| lente di oggi, rettangolo singolo (`RoundedRectRefraction`) | 1,0 |
| lente di oggi con dispersione (7 campioni) | 2,8 |
| gruppo di oggi, 1 pezzo | 1,8 |
| gruppo di oggi, 2 pezzi | 2,3 |
| gruppo di oggi, 6 pezzi | 5,1 |
| sonda (lente + 3 campioni di dispersione + luce + ombra), 1 pezzo | 2,4 |
| sonda, 2 / 4 / 8 pezzi | 3,5 / 5,3 / 8,2 |

Ogni pezzo in più costa circa un terzo del primo. Su GPU il peso relativo dei conti aritmetici
rispetto alle letture di texture è minore, quindi il rapporto vero sarà più basso
**[da misurare]**.

### Tenere economico «un pezzo solo»

- **Varianti dalla stessa sorgente**: la costante `MAX_PIECES` si sostituisce prima di compilare
  (1, 2, 4, 8). Un tasto fermo usa la variante a 1 pezzo, che è un rettangolo arrotondato senza
  ciclo; un contenitore passa a 2 o 4 solo mentre i membri sono a distanza di fusione.
- **Uscite anticipate**: riquadro del campo (fuori, zero); zona piatta (dentro oltre lo spessore,
  una lettura sola e nessuna luce). `PhysicsDropBandBlock` fa già questo per la goccia.
- **Tetto**: Haze fonde al massimo 4 membri per gruppo (`MAX_GLASS_MERGED_MEMBERS = 4`), oltre li
  disegna separati **[codice]**. L'engine oggi ne porta 6. Consiglio 4 di serie e 8 come massimo:
  una barra di Apple che cambia contesto ne ha 5 o 6 in transito, ma raramente tutti a distanza di
  fusione nello stesso istante.
- **Le tre correzioni che Haze ha dovuto fare** sul campo fuso (ADR-0013) **[codice]**: attenuare
  la rifrazione sui ponti sottili; attenuare la fusione quando i membri si sovrappongono (una
  sovrapposizione piena deve finire nell'unione); non calcolare l'ottica dalla taglia del layer
  allargato. La sonda mostra il primo difetto: una **piega di luce** al centro del ponte, perché lì
  passa l'asse mediano del campo e il profilo a cupola fa cresta **[banco]**. Con l'ombreggiatura
  del corpo si vede; con la sola lente di oggi no.
- **Una cattura per contenitore**: se i tasti di una barra sono pezzi di un solo campo, la catena
  gira una volta invece di N. È l'unico punto in cui il materiale nuovo costa *meno* di oggi.

**Da misurare**: costo reale di 1, 2, 4, 8 pezzi su Tab S9 e S25; scatto al cambio di variante a
metà gesto (precompilare); memoria degli uniform con 8 pezzi su GLES.

## 3. Leggere lo sfondo per adattarsi

| via | cosa legge | API | latenza | costo | nota |
|---|---|---|---|---|---|
| **miniatura in un `GraphicsLayer` + `toImageBitmap()`** (consigliata per le icone) | il fondale del pannello, senza il pannello | Compose 1.7+, minSdk dell'engine | 1–3 fotogrammi | una passata minuscola + una rilettura dalla GPU di pochi pixel | è il demo `AdaptiveLuminanceGlassContent` di Kyant: `layer.record { drawBackdrop() }`, poi `scale(5, 5)`, `readPixels`, media **[codice]** |
| **media nello shader** (consigliata per tinta, tono, ombra) | 4–9 campioni dell'ingresso sfocato attorno al centro | AGSL, 33+ | zero | qualche lettura in più per pixel | non esce dalla GPU: non può decidere il colore di un'icona di Compose |
| `PixelCopy` della finestra | lo schermo composto, **vetro e icone compresi** | 26+ | 2+ fotogrammi | rilettura della regione | si rilegge il proprio risultato: l'isteresi deve reggere la retroazione |
| `HardwareRenderer` + `ImageReader` | un `RenderNode` proprio | 29+ | 1–2 fotogrammi | come la prima, con più codice | ha senso solo se `toImageBitmap()` risultasse troppo lento |
| inchiostro deciso nello shader | l'ingresso, per pixel | 33+ | zero | basso | richiede che le icone entrino nello shader come maschera: è la via C del cap. 1, non la partenza |

Come evitare lo sfarfallio (nessuna libreria esaminata lo fa bene: Kyant usa solo un'animazione di
un secondo e una soglia secca a 0,5; Haze non ha adattività di luminanza) **[codice]**:

- **quando campionare**: solo nei fotogrammi in cui la cattura è stata rifatta (il nodo lo sa già:
  `needsRecord`), al massimo ogni 100–150 ms, mai durante un lancio veloce (`FluidGlassQuality`
  basso);
- **cosa campionare**: una miniatura di 8×8 o 16×16 della regione sotto il pannello, registrata a
  scala molto bassa dal layer della pagina (che è già una texture: `glassBackdropSource` compone
  fuori schermo, quindi è un quadrilatero e non un rigiro della pagina);
- **filtro**: media mobile esponenziale con costante di tempo di 150–250 ms;
- **isteresi**: due soglie distanti (per esempio scuro sopra 0,62 di luminanza, chiaro sotto 0,45),
  permanenza minima di 300–500 ms, cambio in dissolvenza di 200–300 ms. Anche il giro di Apple ha
  ritardo e dissolvenza visibili (frame 15:15–15:57);
- **per chi**: la decisione è del **contenitore** (una barra gira tutta insieme), e solo dei
  piccoli; menu e barre laterali adattano tono e ombra nello shader senza girare.

**Da misurare**: tempo di `toImageBitmap()` su una miniatura (main e RenderThread) e se blocca;
frequenza sostenibile con 3–4 contenitori sullo schermo; comportamento durante uno scorrimento su
contenuto a scacchi chiaro/scuro (il caso che fa sfarfallare).

## 4. Sfocatura progressiva per il bordo dello scorrimento

Confronto reso sul banco (`sonda-bordo-scorrimento.zoom.png`), stesso raggio massimo e stessa
altezza, sopra testo nero:

- **maschera d'alfa su una sfocatura uniforme** (oggi: `GlassFalloff.FadeDown`; demo
  `ProgressiveBlurContent` di Kyant): a metà dissolvenza si leggono insieme il testo nitido e la
  sua copia sfocata. È la «fascia» da togliere **[banco]**;
- **una passata, raggio per pixel, 24 campioni a spirale**: niente immagine doppia, ma sul testo il
  disegno dei campioni si vede (lettere ripetute ad anello) **[banco]**. Non basta.

| tecnica | resa | costo | fonte |
|---|---|---|---|
| **due passate separabili (orizzontale, verticale) con raggio per pixel da una maschera** (consigliata) | gaussiana vera a raggio variabile | 2 passate × fino a 41 campioni, **solo sulla striscia** e a risoluzione ridotta | Haze: `SemanticBlurKernel` (20 coppie + centro per passata, raggio massimo 38,5 px nello spazio dell'ingresso), Apache-2.0 **[codice]**; Compose 1.13 fa lo stesso **[doc]** |
| `Modifier.blur(BlurRadiusSpec.verticalGradient(...))` di Compose | la stessa | sfoca il contenuto **proprio** del composable: applicato al corpo della pagina fa passare tutta la pagina dallo shader | Compose 1.13.0-alpha03, API 33+ **[doc]**; l'engine è sul BOM 2026.03.00 e non ce l'ha |
| 3–5 sfocature native a raggi crescenti, incrociate con maschere | buona con 4+ livelli | ogni livello è una sfocatura nativa (economica), ma sono 4 catene | — |
| maschera d'alfa | immagine doppia | minimo | oggi |

Via consigliata: una **striscia** alta quanto il bordo (non una lastra di vetro: nessuna sagoma,
nessuna lente, nessun bordo), catturata con il meccanismo che c'è già (`drawPlainBackdrop`) a metà
risoluzione, con due nodi runtime in catena e lo scurimento per il contenuto scuro nella seconda
passata. A metà risoluzione 38,5 px sono circa 25 dp su un telefono a densità 3: sopra la misura
che serve. La variante «dura» di Apple è la stessa striscia con la rampa più corta e più velo.

Il costo è per fotogramma di scorrimento, perché il contenuto sotto cambia sempre: è la voce nuova
più pesante mentre si scorre, e sostituisce la fascia `Bar` di oggi (che fa una sfocatura da
12,5 dp più la lente sulla stessa area).

**Da misurare**: tempo delle due passate sulla striscia in alto e in basso insieme durante un
lancio, su Tab S9 (striscia larga 2560 px); se la tenuta della cattura a 100 ms è accettabile su un
bordo così sottile.

## 5. Comparsa per lente, bagliore ai vicini, flessione a gel

Tutti e tre escono dal campo di distanza con soli uniform **[banco]** per il primo, **[codice]**
per gli altri (Haze li ha).

- **Comparsa per lente**: un uniform `materialize` (0..1) che scala insieme spostamento della lente,
  quota di sfocato, pellicola, luce e ombra. A 0 lo shader restituisce l'ingresso nitido, quindi la
  lastra non c'è senza che l'opacità c'entri. Reso in `sonda-comparsa.png`. Oggi `intensity` fa una
  cosa simile ma scala anche la sfocatura a monte: va tenuta distinta dalla comparsa, che di Apple
  ha anche un filo di scala.
- **Bagliore che passa ai vicini**: il bagliore è una funzione della distanza dal dito
  (`exp(−|p − tocco|² / r²)`) valutata nello spazio del **contenitore**. Se i vicini sono pezzi dello
  stesso campo arriva da solo; se sono lastre separate basta dare a tutte lo stesso punto e lo
  stesso raggio (uno stato del contenitore, in coordinate di radice). Nessun buffer condiviso. Sta
  nello strato della luce, quindi non rifà la sfocatura. Haze: «Pressing one member can shrink it
  within the merged shape, and only that member brightens» è la scelta opposta, da non copiare qui.
- **Flessione a gel**: si deforma il **punto interrogato**, non la sagoma: `p' = c + S⁻¹·(p − c)`
  con `S` allungamento lungo la velocità e schiacciamento a volume costante sull'asse ortogonale,
  più un rigonfiamento verso il dito. Il campo di un rettangolo sotto una scala non uniforme non è
  più una distanza esatta: lo spessore del bordo varia di qualche punto percentuale, accettabile
  per deformazioni fino a 1,2 (il tetto che Haze ha appena alzato per le interazioni).
  Due condizioni di architettura:
  1. la sagoma la ritaglia **lo shader**, e il `RenderNode` tiene un ritaglio largo e fisso (come
     `fluidPhysicsSurface`: «la maschera è dello shader»). Con il ritaglio hardware di oggi una
     lastra non può sporgere dai suoi limiti;
  2. il contenuto (icone, etichette) segue con una trasformazione del suo layer, che è
     un'approssimazione: per deformarlo davvero dovrebbe passare dalla lente.

**Da misurare**: il costo del layer allargato (margine per sporgenza e ombra) su una barra a tutta
larghezza; la resa della flessione a 120 Hz (S25) contro 60.

## 6. Ombre adattive e colorate dallo sfondo

Oggi l'ombra è una maschera sfumata registrata una volta e tenuta (`ShadowNode`): nero al 10–16 %,
scostamento `raggio / 6`, costo per fotogramma nullo.

| via | adattiva | colorata | costo |
|---|---|---|---|
| **maschera in cache, con alfa e colore guidati dalla lettura dello sfondo** (consigliata a riposo) | sì, alla frequenza della lettura (cap. 3) | sì, con il colore medio | nullo per fotogramma |
| **nello shader**: il campo valutato una seconda volta nel punto spostato, colore dall'ingresso sfocato (resa nella sonda) | sì, per pixel | sì, per pixel | una valutazione del campo e una lettura in più sui pixel del margine; layer più grande |
| contrasto locale `|nitido − sfocato|` per «più forte sul testo» | sì, per pixel | — | richiede i due ingressi (cap. 1, vie B o C) |

Consiglio: a riposo la prima; durante una trasformazione la seconda, perché la sagoma non è più un
rettangolo arrotondato e una maschera a path andrebbe rifatta a ogni fotogramma (oggi lo fa
`FluidPhysicsShadow`). «Più forte sul testo» si ottiene a costo zero dalla miniatura del cap. 3: la
**varianza** di luminanza della miniatura è la misura di quanto lo sfondo è «pieno».

Due strati di ombra (una stretta di contatto, una larga) come Apple sono due maschere in cache:
nessun costo per fotogramma, il doppio di memoria di layer; il tetto d'area che c'è già
(`HighlightLayerMaxPx`) va esteso all'ombra.

**Da misurare**: memoria dei layer d'ombra con molte lastre (il problema del 2026-10-13 sull'S25);
se l'ombra nello shader durante un morph regge il fotogramma su una finestra grande.

## 7. Cosa c'è di nuovo a monte

### Kyant0/AndroidLiquidGlass (Apache-2.0)

Dopo `b18eb0ff` c'è **un solo commit**: `65ab177e` del 2026-08-26, «Update dependencies» (tre file
di Gradle; è la 2.0.1). Nessuno shader nuovo, nessuna ottimizzazione. Da prendere restano cose del
**catalogo**, che l'engine non ha copiato:

- `AdaptiveLuminanceGlassContent.kt`: la lettura dello sfondo del cap. 3, e la curva che lega
  luminanza a luminosità, contrasto e raggio di sfocatura della lastra (sopra il chiaro: più
  luminosità, contrasto a zero, più sfocatura; sopra lo scuro il contrario);
- `ProgressiveBlurContent.kt`: la maschera d'alfa (la tecnica da superare);
- `UISensor.kt`: la luce dal sensore (cap. 8);
- `LockScreenContent.kt` + `SdfShader`: una sagoma qualsiasi da una **texture di distanza**
  precalcolata. Un'alternativa all'anello di 96 vertici per le forme libere **[da valutare]**.

Il difetto di `radiusAt` è ancora a monte.

### Haze di Chris Banes (Apache-2.0), 2.0.0 del 2026-09-22, 2.1.0 del 2026-11-02

Non è più solo sfocatura: `haze-glass` è un vetro liquido completo, con decisioni scritte e misurate
(`docs/adr/`). È la cosa più utile uscita nel 2026.

| idea | dove | cosa prendere |
|---|---|---|
| renderer fuso su API 33+ | ADR-0003 | una cattura, un layer d'uscita, grafo di `RenderEffect`: sfocatura, mescola nitido/sfocato, shader ottico, shader del dettaglio nitido sommato con `Plus`. Su Pixel 6 il collo era il RenderThread e il driver Vulkan (attraversamento dei layer, `flush`), **non** l'area dei frammenti: ridurre la cattura da sola non bastava |
| gruppi che si fondono | ADR-0013, `GlassMergedField.kt` | `hazeGlassContainer`: smooth minimum con `k = distanza di fusione`, tetto 4, transizioni `MatchedGeometry` per chi entra ed esce, e le tre correzioni del cap. 2. È il `GlassEffectContainer` di Apple |
| scala adattiva con isteresi | ADR-0005 | punteggio = pixel tenuti × aggiornamenti ravvicinati (entro 100 ms, fino a 3); due livelli (0,707 e 0,5 lineari), soglia d'ingresso 1,5 M, d'uscita più bassa del 12,5 %. Misure: a 0,75 un vetro animato sfora il fotogramma (22,9 ms), a 0,5 sta dentro (13,8 ms) |
| sfocatura progressiva | `SemanticBlurKernel.kt` | cap. 4 |
| fondale di piattaforma | ADR-0010 | `RenderNode.setBackdropRenderEffect`, pubblico da **Android 17 QPR2 (API 37.2)**: filtra i pixel già disegnati dietro il nodo, senza cattura. Nelle prime misure di Banes è **più lento** della via a cattura per il vetro. Da tenere d'occhio, non da pianificare |
| stili Regular e Clear, accessibilità, profili di superficie | ADR-0007, `GlassAccessibility.kt`, `SurfaceProfile.kt` | vocabolario e confini fra le varianti |

Haze **non** ha: adattività di luminanza, giro chiaro/scuro delle icone, luce dal sensore, flessione
a gel, bagliore condiviso.

### LibrePods (GPL-3.0), ramo `android/rewrite`, testa `2e244d3f` del 2026-10-31

È un'app, non una libreria: i suoi `Styled*` usano **Haze 2.0.1** (`haze-blur`, `haze-glass`) e
**backdrop 2.0.1** di Kyant insieme. Lo scaffold sfoca la barra con `hazeBlur` e stili da
`UltraThin` a `UltraThick`. L'engine ne ha già preso la lente del pulsante (`LICENSES/LibrePods.md`).
Di nuovo c'è la conferma che la combinazione Haze + Kyant è ciò che usa chi oggi fa questo sul
serio; nessuna tecnica che Haze non abbia.

### Altre, 2026

| libreria | licenza | nota |
|---|---|---|
| FletchMcKee/liquid | Apache-2.0 | effetti liquidi con `RuntimeShader` e nodi modificatore, multipiattaforma; attiva (push del 2026-11-09). Da leggere per i profili di rifrazione |
| skydoves/Cloudy | Apache-2.0 | sfocatura con ripiego su CPU, più un vetro liquido; utile solo per il ripiego sotto API 31, che il piano non tocca |
| Kashif-E/KMPLiquidGlass | Apache-2.0 | porta di Kyant, niente di nuovo |
| android/compose-samples, PR 1710 | Apache-2.0 | un `BackdropBlur` d'esempio sul nuovo `setBackdropRenderEffect` |

### Licenze

L'engine è GPL-3.0 dalla 3.0 con parti Apache-2.0. **Apache-2.0 → GPL-3.0 è compatibile**: il
codice di Haze e di Kyant si può copiare e modificare tenendo intestazione, avviso delle modifiche e
una voce in `LICENSES/` (come già per Kyant). **GPL-3.0 → GPL-3.0** (LibrePods) pure. Il verso
contrario no: nulla dell'engine può tornare in una libreria Apache. Le **idee** (smooth minimum,
isteresi, nucleo separabile) non hanno licenza: riscriverle è libero.

## 8. Riflessi che seguono il movimento del telefono

Fattibile e piccolo, a una condizione.

- **Sensore**: `TYPE_GAME_ROTATION_VECTOR` (giroscopio + accelerometro, senza magnetometro, API 18)
  a `SENSOR_DELAY_UI` o `GAME`; in mancanza l'accelerometro con un passa-basso, come `UISensor.kt` di
  Kyant (media esponenziale con 0,5, ne ricava un angolo e un vettore di gravità) **[codice]**.
- **Cosa guida**: la direzione delle due luci del bordo (pochi gradi attorno alla posizione di
  riposo) e, al più, un piccolo spostamento del riflesso del corpo. Un solo stato per finestra.
- **La condizione**: deve vivere nello **strato della luce**. Nello shader del corpo ogni lettura
  del sensore rifarebbe sfocatura e lente di ogni lastra sullo schermo, sempre (cap. 1).
- **Costo**: il sensore acceso a schermo acceso (qualche mA), più il ridisegno dei soli fili
  speculari a 15–50 Hz. Da fermare con il telefono fermo (soglia sulla variazione), a schermo
  spento, in risparmio energetico e con «rimuovi animazioni».

**Da misurare**: consumo con il sensore a `GAME`; se il ridisegno dello strato della luce di 8–10
lastre a 50 Hz resta sotto il millisecondo di RenderThread.

## 9. Il conto

### Dove il materiale nuovo costa più di oggi

| voce | perché | peso atteso |
|---|---|---|
| corpo più ricco (luce del corpo, tono adattivo, 3 campioni di dispersione) | conti in più per pixel; la dispersione però scende da 7 a 3 campioni | basso |
| sagoma ritagliata dallo shader invece che dal `RenderNode` | lo shader gira anche sul margine del layer | medio sulle lastre grandi |
| campo a N pezzi | un terzo del costo del primo pezzo per ogni pezzo in più (stima da CPU) | basso a 1–2, medio a 4+ |
| uniform animati (pressione, flessione, comparsa) | ogni fotogramma rifà la catena, sfocatura compresa | **alto durante i gesti** |
| bordo dello scorrimento | due passate fino a 41 campioni sulla striscia, a ogni fotogramma di scorrimento | **alto mentre si scorre**, ma sostituisce la fascia di oggi |
| lettura dello sfondo | una passata minuscola e una rilettura, poche volte al secondo | basso |
| strato della luce | un layer in più per lastra (c'è già: è il bordo di oggi) | nullo |
| prima compilazione delle varianti | uno scatto per programma nuovo | una volta per installazione, se scaldato |

Il dato di Haze su Pixel 6 conferma quello dell'engine sull'S25 del 2026-10-13: a saturare è il
**numero di layer e di passate che il RenderThread deve attraversare**, prima dell'area. Il
materiale nuovo deve quindi avere *meno nodi* di oggi (oggi: cattura, catena, layer del bordo, layer
dell'ombra, più l'eventuale materiale esportato, per ogni lastra).

### Leve da tenere pronte per la fase di ottimizzazione

Ci sono già, e restano valide: risoluzione della cattura in funzione della sfocatura
(`glassResolutionScale`), qualità adattiva (`FluidGlassQuality`, due livelli quantizzati), cattura
tenuta mentre la superficie si muove (`frozen`, `heldWhileMoving`, rinfresco a 110 ms),
`sampleOnce`, `resampleIntervalMillis`, tetto d'area dei layer ausiliari, materiale esportato solo
se qualcuno lo campiona.

Da aggiungere:

1. **una cattura e una catena per contenitore**, non per tasto;
2. **strato della luce separato**, l'unico che si anima a riposo;
3. **varianti a 1, 2, 4, 8 pezzi**, con i pezzi fusi solo mentre sono in movimento o a distanza di
   fusione;
4. **nodo di sfocatura tenuto stabile** fra un fotogramma e l'altro quando cambiano solo gli uniform
   dello shader (se la misura dice che Skia lo riusa);
5. **isteresi sulla scala** come Haze (soglie d'ingresso e d'uscita distinte, punteggio pesato dalla
   cadenza degli aggiornamenti) al posto della soglia secca di `quantiseScaleFactor`;
6. **dispersione solo sui piccoli**, e spenta in movimento (c'è già);
7. **ombra nello shader solo durante una trasformazione**;
8. **scaldare gli shader** all'avvio, disegnandoli una volta fuori schermo;
9. **lettura dello sfondo sospesa** quando la cattura non cambia.

**Da misurare, in una sessione sola sul tablet** (ordine di utilità): (a) logcat di `FluidPhysics`;
(b) fotogramma di RenderThread del vetro di oggi contro un prototipo «corpo + luce» sugli stessi tre
elementi del banco; (c) costo di un cambio di soli uniform; (d) 1, 2, 4, 8 pezzi; (e) striscia del
bordo durante un lancio; (f) `toImageBitmap()` di una miniatura; (g) prima compilazione.

## Rischi in ordine

1. **Il costo dei gesti.** Se un cambio di uniform rifà davvero tutta la catena e sul Tab S9 non
   sta nel fotogramma, pressione, flessione e comparsa su lastre grandi vanno ridisegnate (sfocatura
   tenuta ferma durante il gesto, o effetto ridotto sui grandi). È la misura (c): **può cambiare
   l'architettura del corpo**.
2. **Fluid-physics forse gira già in ripiego.** Se il difetto di `flat` è confermato, il giudizio di
   partenza su goccia e trasformazioni è da rifare dopo la correzione di una riga, e va fatto
   *prima* di decidere quanto del motore di oggi tenere.
3. **Il bordo dello scorrimento su schermi larghi.** Due passate a ogni fotogramma di scorrimento su
   2560 px di larghezza: se non regge, si scende a tre sfocature native incrociate o a una rampa più
   corta, e la resa si allontana da Apple.
4. **La via dei due ingressi.** Se la resa chiede il bordo rifratto nitido e la via B (campo
   valutato due volte) costa troppo, resta la C, che nessuno ha provato su HWUI.
5. **Lo sfarfallio dell'adattività.** Le soglie si tarano solo guardando uno scorrimento vero su
   contenuto misto; se non si trova un'isteresi che regge, il giro chiaro/scuro resta ai soli
   elementi fermi sopra contenuto fermo.
6. **Precisione e driver.** `half` a 16 bit, varianti che un driver rifiuta, differenze fra Adreno
   740 (Tab S9) e 830 (S25), e il passaggio di HWUI a un nuovo backend di Skia sulle versioni
   recenti: il banco non vede niente di questo. La sonda di salute va estesa a ogni variante nuova,
   con un ripiego che si vede in un test invece che solo in logcat.
7. **La piega di luce sui ponti.** L'ombreggiatura del corpo rende visibile l'asse mediano del
   campo fuso: serve un profilo dello spessore più dolce o una normale lisciata; è un problema di
   resa, non di fattibilità, ma può costare qualche giro.
8. **Compose 1.13.** La sfocatura progressiva di piattaforma esiste solo in alfa e solo sul
   contenuto proprio: non conviene far dipendere il piano da un aggiornamento del BOM.
9. **Il banco inganna in positivo.** Niente tempi, sfocatura leggermente diversa, sRGB a 8 bit: una
   resa approvata sul PC può avere bande o colori diversi su un pannello P3. Ogni fase grafica deve
   chiudersi con un confronto sul tablet, non solo sul banco.

## Fonti

- Engine 3.1.2: `glass/backdrop/DrawBackdropModifier.kt`, `glass/backdrop/internal/Shaders.kt`,
  `glass/backdrop/effects/*`, `glass/backdrop/highlight/*`, `glass/backdrop/shadow/*`,
  `fluid/GlassMaterial.kt`, `fluidphysics/FluidPhysicsShaders.kt`, `fluidphysics/FluidPhysicsTier.kt`.
- Kyant0/AndroidLiquidGlass, ramo `kmp`, testa `65ab177e`: <https://github.com/Kyant0/AndroidLiquidGlass>
- chrisbanes/haze, ramo `main` al 2026-11-10 (`docs/adr/0003`, `0005`, `0010`, `0013`;
  `haze-glass/…/GlassRenderEffectFactory.android.kt`, `SemanticBlurKernel.kt`, `GlassMergedField.kt`):
  <https://github.com/chrisbanes/haze>
- Chris Banes, «Compose has progressive blur. Do you still need Haze?», 2026-09-13:
  <https://chrisbanes.me/posts/compose-progressive-blur/>
- librepods-org/librepods, ramo `android/rewrite`: <https://github.com/librepods-org/librepods>
- `RenderEffect`, `RenderNode.setBackdropRenderEffect`, AGSL:
  <https://developer.android.com/reference/android/graphics/RenderEffect>,
  <https://developer.android.com/reference/android/graphics/RenderNode>,
  <https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl>
- FletchMcKee/liquid, skydoves/Cloudy, Kashif-E/KMPLiquidGlass, android/compose-samples (PR 1710).
