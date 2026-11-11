# Le tracce: il formato di scambio fra l'engine e il banco del movimento

Una **traccia** e' un file JSON che descrive un viaggio fotogramma per fotogramma: per ogni
fotogramma, le grandezze che arrivano agli shader (pezzi del campo, uniform del corpo e della luce,
ombra, contenuto), con il suo istante. La scrive l'engine dalle sue funzioni pure (piano 4.3:
`GlassTraceExportTest`, pacchetto F0.5), la legge `glassmotion.py`, che ne fa fotogrammi, video,
collage e curve. Non esiste una seconda descrizione del movimento: se la traccia e' giusta e il
dispositivo no, il difetto e' nel tempo, non nella forma.

Chi ha fretta legge «In dieci righe», l'esempio completo e «Per chi scrive le tracce dal Kotlin».

## In dieci righe

1. Un file JSON per viaggio: `schema` (1), `name`, `fps`, `density`, `theme`, `size` in dp, e
   `frames`.
2. Lo sfondo viene da una scena del banco fermo (`scene`) o sta nella traccia (`background`, `page`).
3. Ogni fotogramma ha `t` (millisecondi dall'inizio del viaggio; negativo = preambolo) e
   `elements`, nell'ordine in cui si disegnano.
4. Un elemento e' una voce del **vocabolario delle scene** (`kind`: `shader`, `material`, `glass`,
   `shape`), con gli uniform gia' calcolati. Le lunghezze si scrivono in dp, `"12.5dp"`; un'area
   `"16dp^2"`. Niente si arrotonda al pixel: tutto si posa a coordinate frazionarie.
5. `base` (facoltativo) tiene cio' che non cambia: ogni elemento di un fotogramma parte dalla voce
   di `base` con il suo `name` e ci scrive sopra solo cio' che si muove.
6. I pezzi del campo sono un dato, `pieces` (riquadro, raggi, fusione) o `ring` (un anello di
   vertici): gli uniform li citano con `"$pieceRect"`, `"$pieceRadii"`, `"$pieceBlend"`, `"$verts"`.
7. L'ombra (`shadow`, `shadowAmount`) segue i pezzi, o il riquadro dell'elemento se non ce ne sono.
8. Il contenuto e' `content`: una lista di strati, ognuno con icone o testo e con la sua
   sfocatura, scala, opacita' e, se serve, un effetto a shader (la lente del contenuto).
   `contentClip` li ritaglia tutti sulla sagoma del vetro, `clip` ne ritaglia uno solo.
9. `events` segna le interruzioni (`interrupt`: il viaggio cambia destinazione a quell'istante);
   `probes` dice cosa misurare nel tempo; `apple` dice con quale video confrontare e da che
   fotogramma (anche piu' riprese, ognuna con un nome).
10. Ogni comando valida prima di rendere: uno uniform dimenticato, un numero non finito o un nome
    sbagliato fermano tutto, con il punto esatto.

## Comandi

```powershell
$py = "artifacts\liquid-glass\venv\Scripts\python"
$gm = "tools\glassbench\glassmotion.py"
$t  = "artifacts\liquid-glass\moto\F5A\menu-apertura\trace.json"

& $py $gm validate $t           # controlla e basta; esce con 1 se e' sbagliata
& $py $gm render   $t           # frames\f0000.png ... (solo quelli che mancano o sono cambiati)
& $py $gm video    $t --slow 4  # <nome>.mp4 a velocita' vera e <nome>-lento.mp4
& $py $gm collage  $t           # <nome>-collage.jpg, un fotogramma ogni 33,4 ms (la cadenza di Apple)
& $py $gm collage  $t --step-ms 0 --cols 10        # tutti i fotogrammi
& $py $gm pair     $t           # <nome>-vs-apple.jpg e <nome>-vs-apple-lento.mp4, per ogni blocco `apple`
& $py $gm pair     $t --apple foto                  # uno solo, per nome: <nome>-vs-apple-foto.jpg
& $py $gm pair     $t --t0-frame 13046 --apple-crop 540,120,840,840 --apple-name foto   # uno al volo
& $py $gm pair     $t --real video.mp4 --real-crop 1572,744,680,595   # contro una registrazione vera: contorni
& $py $gm first    $t           # <nome>-primi5.png, ingranditi
& $py $gm first    $t --at 150  # cinque fotogrammi da 150 ms (uno prima): per un'interruzione
& $py $gm last     $t           # <nome>-ultimi5.png
& $py $gm curve    $t           # curve.csv, curve.png, curve.md
& $py $gm all      $t           # tutto
& $py $gm scene    $t --frame -1 -o scena.json     # un fotogramma come scena di glassbench.py
& $py $gm scene    $t --no-subpixel                # ... con i riquadri arrotondati al pixel (una lastra ferma)
& $py $gm selftest              # le prove del banco su se stesso
```

Opzioni comuni: `-o cartella` (di serie `artifacts/liquid-glass/moto/<phase>/<name>/`, con
`prove` al posto della fase se la traccia non la dice; e' ignorata da git: fotogrammi e video non
si tracciano), `--jobs N` (processi di rendering, di serie 2, al
piu' 4: il PC e' condiviso), `--force`, `--density D` (una bozza a densita' piu' bassa: vale solo
se le grandezze degli uniform sono scritte in dp, `"12dp"` e `"16dp^2"`, e non come pixel gia'
calcolati), `--crop x,y,w,h` in dp dove ha senso.

`render` tiene un manifesto (`frames/manifest.json`): un fotogramma si rifa' solo se cambia la sua
scena, uno shader che la traccia nomina, `render.py`, `GlassMaterial.kt` o le icone. Gli altri
comandi rendono cio' che manca e poi lavorano sui PNG.

Le tracce vere stanno in `artifacts/liquid-glass/moto/<fase>/<scenario>/trace.json`. Quelle
d'esempio, scritte a mano da `traces/esempi/genera.py`, in `tools/glassbench/traces/esempi/`.

## Esempio completo

Una capsula di oggi (`SlabGroupRefractionShaderString`) che si stringe, cambia idea a 16,7 ms e
torna larga; un'ombra, uno strato di icone che si sfoca. Quattro fotogrammi: e' una traccia valida
(`selftest` lo verifica).

<!-- esempio-completo -->
```json
{
  "schema": 1, "name": "capsula-si-stringe", "phase": "F0", "fps": 60, "durationMs": 50,
  "density": 3.0, "theme": "light", "size": [240, 84],
  "background": {"kind": "color", "color": "#C9CBD1"},
  "page": [{"kind": "text", "text": "Sotto il vetro", "x": 12, "y": 48, "sizeDp": 16}],
  "apple": {"video": "wwdc2025-219_hd", "t0Frame": 8634, "crop": [1040, 580, 500, 250],
            "collage": "artifacts/liquid-glass/riferimenti/collage/B2_barra_render_4m48_f8632-8659.jpg"},
  "events": [{"t": 16.667, "kind": "interrupt", "label": "torna larga"}],
  "probes": [{"name": "larghezza", "kind": "value", "path": "barra.pieces[0].w", "rest": [44, 120],
              "ref": {"overshootPct": [0, 1.3], "restMs": [270, 330], "source": "R4 B2, media"}}],
  "base": {
    "barra": {
      "kind": "shader", "source": "kotlin:SlabGroupRefractionShaderString",
      "x": 8, "y": 14, "w": 224, "h": 56,
      "inputs": {"content": {"saturation": 1.6, "blurDp": 4.5, "edge": "clamp"}},
      "uniforms": {
        "offset": "$offset",
        "pieceCount": "$pieceCount", "pieceRect": "$pieceRect", "pieceRadii": "$pieceRadii",
        "pieceBlend": "$pieceBlend",
        "refractionHeight": "14dp", "refractionAmount": "-22dp", "depthEffect": 0, "magnify": 0,
        "fieldCenter": ["112dp", "28dp"], "aa": 1.0, "tintColor": "#F8F8F985"
      },
      "shadow": [{"radiusDp": 10, "offsetDp": [0, 3], "alpha": 0.14}],
      "content": [{"name": "icone", "y": 6, "h": 44, "icons": ["search", "share", "plus"]}]
    }
  },
  "frames": [
    {"t": 0, "elements": [{"name": "barra",
      "pieces": [{"x": 88, "y": 6, "w": 120, "h": 44, "radius": "capsule", "blend": 18}],
      "content": [{"name": "icone", "x": 88, "w": 120}]}]},
    {"t": 16.667, "elements": [{"name": "barra",
      "pieces": [{"x": 99.5, "y": 6, "w": 108.5, "h": 44, "radius": "capsule", "blend": 18}],
      "content": [{"name": "icone", "x": 99.5, "w": 108.5, "blurDp": 2, "alpha": 0.8}]}]},
    {"t": 33.333, "elements": [{"name": "barra",
      "pieces": [{"x": 96, "y": 6, "w": 112, "h": 44, "radius": "capsule", "blend": 18}],
      "content": [{"name": "icone", "x": 96, "w": 112, "blurDp": 1, "alpha": 0.9}]}]},
    {"t": 50, "elements": [{"name": "barra",
      "pieces": [{"x": 88, "y": 6, "w": 120, "h": 44, "radius": "capsule", "blend": 18}],
      "content": [{"name": "icone", "x": 88, "w": 120}]}]}
  ]
}
```

Esempi piu' ricchi, da copiare: `traces/esempi/esempio-lastre.json` (due pezzi che si fondono),
`esempio-contenuto.json` (un `material`, due strati che si incrociano, un effetto a shader),
`esempio-interruzione.json`, `esempio-menu.json` (con il preambolo e il confronto con Apple).

## La testa

| campo | | significato |
|---|---|---|
| `schema` | obbligatorio | `1`. Un lettore rifiuta le altre versioni |
| `name` | obbligatorio | minuscole, cifre, trattini: e' il nome dei file prodotti |
| `phase` | facoltativo | la fase del piano (`"F5A"`): con `name` decide la cartella dei prodotti |
| `fps` | obbligatorio | la cadenza a cui il viaggio e' stato **calcolato**: quella del dispositivo che si vuole riprodurre (60, o 120: vedi «La cadenza»). Se i `t` non la rispettano il validatore avvisa, e il video tiene comunque ogni fotogramma fino al successivo |
| `durationMs` | facoltativo | la durata dichiarata del viaggio; serve solo al controllo. Zero e' una scena ferma: una traccia di un fotogramma |
| `density` | obbligatorio | pixel per dp del rendering |
| `theme` | obbligatorio | `"light"` o `"dark"` |
| `size` | obbligatorio | `[larghezza, altezza]` della tela, in dp |
| `scene` | uno dei due | una scena del banco fermo: ne vengono `background`, `page`, `palette`, `paletteBase`, `corner`. I suoi elementi, la sua taglia, la sua densita' e il suo tema **non** contano |
| `background`, `page`, `palette`, `paletteBase`, `corner` | uno dei due | gli stessi campi di una scena (`README.md`); se c'e' anche `scene`, questi vincono |
| `base` | facoltativo | nome dell'elemento -> i campi che non cambiano (vedi sotto) |
| `frames` | obbligatorio | i fotogrammi |
| `events`, `probes`, `apple` | facoltativi | vedi le sezioni loro. `apple` e' un oggetto, o una lista di oggetti con un `name` |
| `measure` | facoltativo | le misure di una scena (`README.md`): le porta con se' `scene`, per `glassbench.py measure` |
| `_`, `_qualcosa` | facoltativi | commenti: un campo che comincia con `_` e' ignorato in testa, nei fotogrammi, negli elementi, negli strati di contenuto, negli eventi, nelle sonde e in `apple` (non dentro `uniforms`, `pieces`, `shadow`) |

Un campo sconosciuto e' un errore, non un avviso: un refuso (`frame` per `frames`) non deve
passare in silenzio.

**Percorsi.** Dentro una traccia ogni percorso parte dalla **radice del repo**: `scene`,
`file:tools/glassbench/shaders/x.agsl`, `apple.collage`, un'immagine di sfondo. Cosi' una traccia
vale ovunque la si sposti.

## Fotogrammi e `base`

```json
{"t": 133.333, "elements": [ { "name": "menu", "...": 0 } ], "page": [ ]}
```

- `t`: millisecondi dall'inizio del viaggio, crescenti. `t = 0` e' l'istante in cui il viaggio
  parte (cio' che la funzione pura da' a `tMs = 0`), ed e' l'istante che `apple.t0Frame` indica nel
  video. Un `t` negativo e' un fotogramma di preambolo (la lastra ferma prima di partire): entra
  in video e collage, non nei conti di `curve`.
- `elements`: gli elementi **di quel fotogramma**, nell'ordine in cui si disegnano. Un elemento
  che manca in un fotogramma non si disegna: i pezzi possono nascere e sparire.
- `page` (facoltativo): la pagina di quel solo fotogramma, al posto di quella della traccia. E'
  il modo di far scorrere il contenuto sotto il vetro (`scrollEdgeFrame`).

**Niente si arrotonda al pixel.** Il banco del movimento rende ogni fotogramma con `"subpixel":
true` (`README.md`, «Posizioni sotto il pixel»): riquadri di elementi e di strati, icone ed
etichette si posano a coordinate frazionarie, con il loro antialias (circa un centesimo di pixel
per sagome e icone, un quarto per il testo). Un'icona che segue una molla non scatta di un pixel
da un fotogramma all'altro. Chi scrive la traccia mette le coordinate vere, con i decimali.

**La fusione con `base`.** Ogni elemento ha un `name`. Se `base` ha una voce con quel nome,
l'elemento parte da li' e il fotogramma ci scrive sopra:

- gli oggetti si fondono chiave per chiave (`uniforms`, `capture`, `body`, `light`...);
- le liste e i valori si sostituiscono per intero (`pieces`, `shadow`, un array di uniform);
- `content` e' l'eccezione: la lista del fotogramma decide quali strati ci sono e in che ordine,
  e ogni strato parte da quello con lo stesso `name` in `base`;
- `null` toglie un campo ereditato (`"light": null`).

`base` e' una comodita' di chi scrive: una traccia senza `base`, con ogni elemento ripetuto per
intero a ogni fotogramma, vale lo stesso. Chi la usa mette in `base` sorgenti, catena a monte,
uniform costanti, contenuto; nei fotogrammi solo cio' che si muove.

## Gli elementi

Sono le voci di `elements` di una scena, e `README.md` le descrive campo per campo. In breve:

| `kind` | cosa rende | campi |
|---|---|---|
| `shader` | uno shader a tutta area su un rettangolo: `fluidPhysicsSurface` di oggi, un corpo in una passata | `source`, `x`, `y`, `w`, `h`, `inputs` (la catena a monte di ogni ingresso: `saturation`, `blurDp`, `edge`, `w`), `uniforms`, `marginDp`, `scale`, `clip`, `radius`, `blend`, `alpha` |
| `material` | il materiale nuovo: ombra, catena a monte, corpo, luce | `x`, `y`, `w`, `h`, `radius`, `clip`, `marginDp`, `capture` (`saturation`, `blurDp`, `w`, `paddingDp`, `scale`), `body` e `light` (`source`, `uniforms`; `light` puo' essere una **lista di strati**, ognuno col suo `blend`, come nelle scene scritte dalla ricetta), `clipRadiusScale` (il ritaglio a una frazione del raggio), `children` |
| `glass` | una lastra di oggi (`glassSurface`), con le ottiche lette dall'engine | `role` o `optics`, `tint`, `intensity`, `depth`, `pressed`, `quality`... |
| `shape` | una sagoma piena o al tratto | vedi `README.md` |

`x`, `y`, `w`, `h` sono in dp sulla tela, anche frazionari. Tutti i tipi accettano `content` e
`contentClip` (vedi «Il contenuto»). Per `shader` e `material` un campo che il banco non conosce
e' un errore.

**`source`**: `kotlin:NomeDellaCostante` per una sorgente dell'engine (una `const val
...ShaderString`, letta dai sorgenti: la sorgente e' una sola), `file:percorso.agsl` per un file.

**Uniform.** Tutti quelli che la sorgente dichiara, nessuno escluso e nessuno in piu': sul banco
fermo uno uniform dimenticato vale zero in silenzio, in una traccia e' un errore. I valori:

| forma | significato |
|---|---|
| `0.35` | un numero, cosi' com'e' (pesi, angoli, conteggi, e i pixel veri come `aa`) |
| `"12.5dp"` | una lunghezza: il banco la porta in pixel con la densita' **e con la risoluzione ridotta della cattura** (`capture.scale`, `scale`). E' la forma giusta per ogni lunghezza. Vale anche `"1.0E-4dp"` |
| `"16dp^2"`, `"0.5dp^-1"` | una grandezza che scala con una **potenza** della densita': un'area (`normalSmooth` dello shader ad anello e' in px^2: 4 dp al quadrato sono `"16dp^2"`), l'inverso di una lunghezza. Vale `numero x densita'^n`, con la densita' del posto in cui lo shader gira (per il corpo quella della cattura ridotta) |
| `"#RRGGBBAA"` | un colore non premoltiplicato, per gli uniform `layout(color)` (4 numeri) |
| `"$offset"`, `"$size"`, `"$halfSize"`, `"$center"`, `"$captureSize"`, `"$density"`, `"$dark"` | i valori che il banco conosce da se'; per `material` anche `"$radii"` e `"$padding"`; per l'effetto sul contenuto `"$layerSize"` al posto di `"$captureSize"` |
| `"$pieceCount"`, `"$pieceRect"`, `"$pieceRadii"`, `"$pieceBlend"`, `"$pieceSmooth"`, `"$vertCount"`, `"$verts"`, `"$bbox"` | il campo, da `pieces` e da `ring` (sotto) |
| una lista | i valori in fila; le liste annidate si appiattiscono; un array non pieno si completa con zeri |

`"offset": "$offset"` va sempre scritto cosi': e' il margine della cattura, che il banco calcola.

## Il campo: `pieces` e `ring`

I pezzi sono un dato dell'elemento, non numeri sparsi negli uniform: cosi' corpo, luce e lente
del contenuto leggono **lo stesso** campo, le sonde lo trovano per nome, e chi legge la traccia
vede una capsula larga 120 dp invece di quattro mezze taglie.

```json
"pieces": [
  {"x": 88, "y": 6, "w": 120, "h": 44, "radius": "capsule", "blend": 18},
  {"x": 0,  "y": 6, "w": 44,  "h": 44, "radius": [22, 22, 8, 8], "blend": 18, "smooth": 0.6}
]
```

- `x`, `y`, `w`, `h` in dp, **relativi all'elemento** (il palco). Frazioni di dp ammesse: il
  campo si muove sotto il pixel;
- `radius`: `"capsule"`, un numero, o quattro (alto-sx, alto-dx, basso-dx, basso-sx);
- `blend`: il raggio di fusione del pezzo, in dp; `smooth`: il raccordo continuo, 0..1.

Negli uniform:

| valore | diventa | convenzione |
|---|---|---|
| `"$pieceCount"` | quanti pezzi | |
| `"$pieceRect"` | per pezzo: centro x, centro y, mezza larghezza, mezza altezza | quella di `SlabGroupRefractionShaderString` e delle sonde |
| `"$pieceRadii"` | per pezzo: i quattro raggi, tagliati a mezzo lato corto | alto-sx, alto-dx, basso-dx, basso-sx |
| `"$pieceBlend"` | per pezzo: il raggio di fusione | uno shader con un solo `blend` lo scrive a mano: `"blend": "14dp"` |
| `"$pieceSmooth"` | per pezzo: `smooth` | |

Per una sagoma libera (`PolyMorphRefractionShaderString`, `GlassBodyRingShaderString`):

```json
"ring": [[12.0, 4.5], [40.2, 3.1], [58.0, 22.0], [41.0, 40.2], [12.5, 41.0]]
```

vertici in dp relativi all'elemento, in ordine; `"$vertCount"`, `"$verts"` e `"$bbox"` (minimo x,
minimo y, massimo x, massimo y) vengono da li'. Gli altri uniform dell'anello (`restCenter`,
`restHalfSize`, `restRadii`, `dropReach`, `normalSmooth`, `soften`) si scrivono a mano, in dp.

Un elemento `material` fermo, che e' un pezzo solo grande quanto il suo riquadro, non ha bisogno
di `pieces`: usa `"pieceRect": [["$center", "$halfSize"]]` e `"pieceRadii": ["$radii"]`, come
nelle scene.

## L'ombra

```json
"shadow": [{"radiusDp": 2, "offsetDp": [0, 1], "alpha": 0.10},
           {"radiusDp": 16, "offsetDp": [0, 6], "alpha": 0.10}],
"shadowAmount": 0.6
```

Le maschere di `GlassShadowNode` (contatto, ambiente): `radiusDp`, `offsetDp`, `alpha`, `color`,
`spreadDp`. `shadowAmount` le scala tutte (0..1): e' cio' che si anima.

- elemento `material` **senza** `pieces`: l'ombra del banco fermo, sulla sagoma dell'elemento;
- elemento con `pieces`, di qualunque tipo: un'ombra **per pezzo**, sulla sagoma del pezzo;
- elemento `shader` senza `pieces`: sul riquadro dell'elemento con il suo `radius`.

L'ombra che in viaggio sta **dentro lo shader** (piano 3.5) non e' questa: sono uniform
(`shadowOffset`, `shadowRadii`, `shadowAlphas`, `shadowColor`), e la traccia li porta come tutti
gli altri.

## Il contenuto

`content` e' una lista di **strati**, disegnati sopra l'elemento nell'ordine dato. Ogni strato e'
un `graphicsLayer` di Compose con il suo `RenderEffect`:

```json
"content": [
  {"name": "vecchie", "icons": ["back", "plus", "circle"], "blurDp": 6, "alpha": 0.4, "scale": 0.9},
  {"name": "nuove", "x": 20, "y": 0, "w": 116, "h": 56, "icons": ["search", "share"],
   "blurDp": 3, "alpha": 0.6, "scale": [0.9, 0.9], "marginDp": 6,
   "effect": {"source": "kotlin:GlassContentLensSlabShaderString",
              "uniforms": {"offset": "$offset", "pieceCount": "$pieceCount", "pieceRect": "$pieceRect", "...": 0}}}
]
```

| campo | significato |
|---|---|
| `name` | obbligatorio: identifica lo strato (fusione con `base`, sonde) |
| `x`, `y`, `w`, `h` | il riquadro dello strato in dp, relativo all'elemento; di serie tutto l'elemento |
| `icon`, `icons`, `label`, `rows`, `items` e `iconSizeDp`, `labelSizeDp`, `labelWeight`, `rowHeightDp`, `rowsTopDp` | cosa c'e' dentro, nel vocabolario delle scene. `items` posa testo e icone dove si vuole (`{"kind": "text", "text": "...", "x": 56, "y": 40, "sizeDp": 17}`, `{"kind": "icon", "icon": "fluid:add|plus", "x": 30, "y": 34, "sizeDp": 22}`), in dp dentro lo strato |
| `color` | il colore del contenuto (`#RRGGBB`, `#RRGGBBAA`); di serie `onSurface` della palette |
| `blurDp` | la sfocatura dello strato |
| `effect` | uno shader con **un ingresso**, applicato allo strato gia' sfocato: `source`, `uniforms`, `input` (il nome dell'ingresso; di serie il primo). E' la lente del contenuto di F5A |
| `scale` | un numero o `[sx, sy]`, attorno al centro del riquadro |
| `alpha` | 0..1; a zero lo strato non si disegna |
| `marginDp` | allarga lo strato oltre il riquadro (cio' che la lente sposta fuori non si taglia); lo shader riceve `"$offset"` = -margine |
| `clip` | il ritaglio di **questo solo strato**: `"box"` (il suo riquadro) o `{"x", "y", "w", "h", "radius"}` in dp relativi all'elemento. Vedi sotto |

L'ordine dentro uno strato e' quello di Compose: disegno, sfocatura, effetto, poi scala e opacita'.

### Da cosa il contenuto e' ritagliato

Due livelli, che si intersecano.

**`contentClip`, sull'elemento**: tutti gli strati insieme, sulla sagoma del vetro.

| valore | ritaglio |
|---|---|
| `"shape"` | il riquadro dell'elemento con il suo `radius`: il ritaglio hardware di una lastra che non cede |
| `"alpha"` | l'alfa di cio' che l'elemento ha disegnato: il campo a pezzi **con i suoi ponti**, l'anello di una goccia. Vuole uno `shader`, o un `material` con il corpo |
| `"pieces"` | l'unione dei `pieces` dell'elemento, **senza** i ponti. E' quello giusto quando lo shader disegna anche un'ombra fuori dalla sagoma (con `"alpha"` il contenuto trapelerebbe nell'ombra) |
| una lista | `[{"x", "y", "w", "h", "radius"}, ...]` in dp relativi all'elemento: la loro unione |
| `"none"` | nessuno |
| `"auto"` | la scelta del banco fermo (`README.md`): `shape` dove c'e' il ritaglio hardware, `alpha` dove ritaglia lo shader |

**Di serie**, quando la traccia non lo scrive: `shape` per una lastra `glass` e per un `material`
con `clip` (il ritaglio hardware ritaglia anche i figli), **`none`** per uno `shader` e per un
`material` senza `clip`. L'alfa dello shader non ritaglia il contenuto da sola: nell'engine di oggi
il contenuto di una superficie di Fluid-physics non e' ritagliato dalla sagoma, e nel materiale nuovo
dove la lastra viaggia il ritaglio hardware e' spento. Chi vuole `alpha` lo chiede (e l'engine deve
farlo davvero: con una maschera nella lente del contenuto, per esempio).

**`clip`, sullo strato**: uno strato solo, su un rettangolo arrotondato dato **sullo schermo**, cioe'
in dp relativi all'elemento e non scalato con lo strato. E' il `travelClipIn` di un menu: le voci
ritagliate dentro il corpo che cresce, mentre la faccia del tasto resta intera.

```json
{"name": "voci", "items": [ ], "alpha": 0.8, "scale": 0.96,
 "clip": {"x": 24.2, "y": 43.1, "w": 208.4, "h": 178.5, "radius": 22}}
```

`"clip": "box"` ritaglia lo strato sul proprio riquadro. Il ritaglio ha un pixel di antialias, come
quello hardware. Uno strato con `clip` non puo' avere anche `effect` (il banco da' a uno strato un
effetto solo, e questo ritaglio ne e' uno, `traces/ritaglio-strato.agsl`): se l'effetto e' una
lente, la maschera la fa lei.

Negli uniform di un `effect`, `"$pieceRect"` e `"$verts"` sono **nel sistema dello strato** (il
banco sottrae `x` e `y`): la lente del contenuto legge lo stesso campo della lastra, visto dal suo
layer. `"$size"`, `"$center"`, `"$halfSize"` sono quelli del riquadro dello strato.

`icon`, `icons`, `label`, `rows`, `items` e `contentLayer` **sull'elemento** sono un errore: in una
traccia il contenuto sta sempre in `content`.

## Eventi: le interruzioni

```json
"events": [
  {"t": 150, "kind": "interrupt", "label": "nuova destinazione: stato C", "to": "C"},
  {"t": 333.3, "kind": "mark", "label": "riposo secondo R4"}
]
```

Un viaggio che cambia destinazione a meta' (`planeJourneyFrame` con `interruzione`) e' una traccia
sola: i fotogrammi dicono cosa accade, l'evento `interrupt` dice **quando** la destinazione cambia.
`t` e' l'istante del primo fotogramma calcolato verso la destinazione nuova. Con quel segno:

- collage e video marcano i fotogrammi dopo l'interruzione (`INT+50` = 50 ms dopo);
- `first --at 150` ingrandisce i fotogrammi attorno a quell'istante;
- `curve` spezza i conti in tratti e, all'interruzione, misura lo **scarto di posizione** dalla
  curva dei fotogrammi prima (deve essere zero: il viaggio riparte da dove si trova) e la
  velocita' nel fotogramma prima e in quello dopo.

`mark` e' un segno e basta (una riga sul grafico, una nota sul video).

## Sonde e curve

```json
"probes": [
  {"name": "larghezza", "kind": "value", "path": "barra.pieces[0].w", "rest": 44,
   "ref": {"overshootPct": [0, 1.3], "peakMs": [100, 167], "restMs": [270, 330], "source": "R4 B2, media"}},
  {"name": "bordo-alto", "kind": "edge", "line": "v:109:0:253", "which": "first", "percentOf": 200},
  {"name": "larghezza-pixel", "kind": "width", "line": "h:42:0:240", "mode": "light"},
  {"name": "luce", "kind": "luma", "rect": [60, 150, 40, 40]}
]
```

| `kind` | misura | campi |
|---|---|---|
| `value` | un numero **della traccia**, senza rendere niente | `path`: `elemento.campo`, con `[i]` per le liste e `[nome]` per gli strati di contenuto: `menu.w`, `barra.pieces[0].w`, `barra.content[icone].blurDp`, `menu.body.uniforms.materialize`. Un `"12dp"` vale 12 |
| `edge` | la posizione di un bordo del vetro lungo una riga, **dai pixel resi** | `line`: `h:Y:X0:X1` o `v:X:Y0:Y1` in dp; `which`: `first` o `last`; `band` (dp, 2), `thr` (livelli, 6), `mode`: `abs`, `light`, `dark` |
| `width` | la distanza fra il primo e l'ultimo bordo lungo la riga | come `edge`, senza `which` |
| `luma` | la luminanza media (Rec. 709, 0-255) in un rettangolo | `rect`: `[x, y, w, h]` in dp |

Le sonde sui pixel cercano dove il fotogramma si scosta dalla **pagina nuda** piu' della soglia:
e' il metodo degli attrezzi della ricerca (`riferimenti/tools/scan.py`), con la pagina vera al
posto del fondo interpolato. `mode: "light"` conta solo cio' che schiarisce (il corpo del vetro e
non la sua ombra). Una sonda `value` dice cosa l'engine ha chiesto, una sui pixel cosa si vede: se
divergono, lo shader non fa cio' che i numeri dicono.

Per tutte: `rest` (il valore di riposo; di serie l'ultimo. Una lista da' un riposo per tratto, se
ci sono interruzioni), `percentOf` (la grandezza su cui fare la percentuale del superamento: per
un bordo, l'altezza a riposo), `unit`, `label`, e `ref`: i numeri di Apple da mettere accanto
(`overshootPct`, `peakMs`, `restMs`, ognuno un numero o `[minimo, massimo]`, piu' `source`).

`curve` scrive `curve.csv` (una riga per fotogramma), `curve.png` e `curve.md`: per ogni sonda i
tre osservabili robusti del piano (**superamento, istante del picco, istante del riposo**), il
rientro iniziale e la seconda oscillazione, accanto a `ref`. I riferimenti sono a 29,97 fps: ogni
tempo di Apple ha +-33 ms.

## Il confronto con Apple

```json
"apple": {"video": "wwdc2025-219_hd", "t0Frame": 9148, "crop": [560, 280, 520, 480],
          "collage": "artifacts/liquid-glass/riferimenti/collage/B1_menu_telefono_apertura_f9143-9166.jpg",
          "speed": 1.0, "note": "R4 B1 (a)"}
```

| campo | significato |
|---|---|
| `video` | il file in `artifacts/liquid-glass/riferimenti/video/` senza `.mp4`, o un percorso |
| `t0Frame` | il numero del fotogramma di Apple che vale `t = 0`, **della sessione** e alla cadenza del file, come lo danno le tabelle di R4 (`f9148`) e gli attrezzi `lg.py` e `lg27.py`. Gli spezzoni 4K conservano i tempi della sessione |
| `crop` | `[x, y, w, h]` in pixel del video: l'inquadratura da mettere accanto alla nostra. Senza, il fotogramma intero |
| `collage` | il collage della ricerca che i critici aprono: `pair` lo cita, non lo usa |
| `speed` | quanto la clip di Apple e' rallentata rispetto al vero (i render di classe C: circa 2). Il fotogramma `k` di Apple si mette accanto all'istante `k * 33,4 / speed` |

**Piu' riprese dello stesso gesto.** `apple` puo' essere una lista: ogni voce ha in piu' un
`name` (minuscole, cifre, trattini), unico.

```json
"apple": [
  {"name": "telefono", "video": "wwdc2025-219_hd", "t0Frame": 9148, "crop": [560, 280, 520, 480]},
  {"name": "foto", "video": "wwdc2025-219_hd", "t0Frame": 13046, "crop": [540, 120, 840, 840]}
]
```

`pair` le confronta tutte: la prima scrive `<nome>-vs-apple.jpg`, le altre
`<nome>-vs-apple-<name>.jpg`. `pair --apple foto` ne fa una sola. Per provare un confronto senza
riscrivere la traccia: `pair --apple-video V --t0-frame N --apple-crop x,y,w,h --apple-speed S
--apple-name foto` (cio' che non si da' viene dal blocco scelto, o dal primo). Le etichette `~f9152`
dei collage contano i fotogrammi della prima ripresa.

`pair` estrae i fotogrammi **dal video** a partire da `t0Frame` (i collage di R4 partono prima
dello zero e non si allineano fotogramma a fotogramma), mette sotto ognuno il nostro fotogramma
dello stesso istante con i millisecondi, e scrive anche il video affiancato rallentato. La tela
della traccia conviene sceglierla alla stessa inquadratura del ritaglio (per il telefono di
`wwdc2025-219_hd`: 1,9 pixel del video per punto).

## Il confronto con una registrazione vera

`pair --real` mette il banco accanto a una registrazione dello schermo (lo `screenrecord` del
tablet) e confronta le **sagome**. Serve quando la traccia descrive cio' che l'engine fa oggi, alla
densita' del dispositivo: allora un pixel del banco e' un pixel dello schermo.

```powershell
$v = "artifacts\liquid-glass\sessioni\S1\video"
& $py $gm pair $t --real $v\adesso-a-menu-stella.mp4 --real-crop 1572,744,680,595 `
      --veil-region 480,744,970,595 --real-name s1-contorni --real-to 175
& $py $gm pair $t --real $v\adesso-t-tasto.mp4 --real-crop 2322,0,238,204 `
      --ignore "2413,23,40,30;2460,21,55,34" --real-name s1-contorni --real-to 150
```

| opzione | significato |
|---|---|
| `--real` | un video, o una cartella di fotogrammi. Del video valgono i `pts` (`ffprobe`): lo `screenrecord` e' a cadenza variabile e l'indice non e' un tempo. Nella cartella il tempo sta nel nome (`f0012_t1435.50.png`, millisecondi) o in un `tempi.json` (`{"file": ms}`) |
| `--real-crop x,y,w,h` | la tela della traccia nei pixel della registrazione. Il ritaglio si fa **in RGB**: in YUV 4:2:0 un lato dispari viene arrotondato al pari e i fotogrammi scivolano di una riga. Se la misura non e' quella della tela viene ricampionato, e lo dice |
| `--align` | `first-change` (di serie): il primo fotogramma in cui qualcosa cambia, nel vero (piu' di 50 px con \|dL\| > 6 rispetto al fermo) e nel banco (piu' di 10 px con \|dL\| > 2). Oppure `VERO_MS:BANCO_MS`: quel `pts` vale quell'istante della traccia |
| `--real-still N` | il fotogramma fermo, quello da cui si misura la differenza (di serie 0) |
| `--real-to N` | l'ultimo fotogramma da leggere |
| `--veil-region x,y,w,h` | una regione di **sola pagina**: da li' si stima la velatura (un menu scurisce la pagina) e la si toglie prima della differenza |
| `--ignore "x,y,w,h;..."` | cio' che copre la tela senza essere dell'app (le icone della barra di stato): li' i due lati si contano uguali |
| `--threshold`, `--open-radius` | la soglia del residuo (di serie 8 livelli; il banco la prova sempre anche a -30% e +30%) e il raggio di chiusura e apertura (di serie 2) |
| `--elements a,b` | gli elementi di cui confrontare la sagoma (di serie quelli la cui forma cambia nella traccia) |
| `--glass-sign` | `auto` (di serie: il verso che copre piu' area), `light`, `dark` |
| `--real-name` | il suffisso dei file (di serie il nome del video) |
| `--real-frames 61,63,70`, `--step-ms` | quali coppie mettere nel collage (di serie una ogni 33,4 ms) |

**L'accoppiamento e' per tempo**: ogni fotogramma vero va con il fotogramma del banco piu' vicino al
suo `pts` allineato, e lo scarto (`dt`, al piu' mezzo fotogramma del banco) sta scritto in ogni cella
del collage e in ogni riga della tabella.

**La regola delle sagome** e' una sola per tutti i viaggi (sta scritta per esteso in testa alla
sezione di `glassmotion.py`):

- la sagoma **del banco** e' esatta: il campo dei `pieces` (lo stesso di
  `SlabGroupRefractionShaderString`), il poligono del `ring`, o il riquadro dell'elemento;
- la sagoma **del vero** e' cio' che cambia rispetto al fotogramma fermo: un pixel e' cambiato se un
  canale (R, G, B) si scosta dalla pagina prevista piu' della soglia. Poi chiusura e apertura
  morfologica e buchi riempiti;
- due letture. **Dentro**: i soli pixel che vanno nel verso del vetro (schiariscono, se il vetro e'
  chiaro). **Fuori**: tutti i pixel cambiati, anche il filo scuro e l'ombra. Il bordo vero sta fra
  le due;
- dove il vetro c'era gia' a riposo e c'e' ancora, la differenza col fermo non dice niente: quella
  parte si conta uguale nei due lati, e la tabella da' anche l'IoU **senza** di lei;
- cio' che cambia a piu' di 24 px dalla sagoma del banco e' pagina (sotto un menu viene sfocata):
  esce dal confronto e si conta a parte, in pixel.

**Le misure**, per coppia:

| misura | cosa dice |
|---|---|
| nella fascia | quanta parte del contorno del banco cade fra «dentro» e «fuori», con un pixel di tolleranza per parte. Non dipende da dove si mette il confine fra vetro e ombra |
| IoU dentro, IoU fuori | la sovrapposizione delle due letture con la sagoma del banco |
| distanza fra i contorni | media, massima (Hausdorff) e con segno (positiva = il vero sta fuori dal banco), in pixel |
| larghezza della fascia | lo spessore medio fra «dentro» e «fuori»: filo e ombra che il banco non rende |
| pagina px | i pixel di pagina che cambiano lontano dal vetro |
| velo | quanto la pagina e' scurita in quel fotogramma (1 = niente) |

Le stesse misure sono rifatte **a pari avanzamento**: ogni fotogramma vero accanto a quello del banco
con la stessa quota di area fra riposo e picco (solo dove l'avanzamento cambia, non sui pianori). E'
la sagoma tolto il ritardo, e il ritardo tolto sta nella tabella. Se un viaggio coincide a pari
avanzamento e non a pari tempo, la forma e' giusta e l'istante no.

**Gli istanti**, dall'area della sagoma: primo movimento, un decimo della corsa, meta', picco, meta'
del ritorno (se torna), riposo; per ognuno il tempo nel vero, nel banco e lo scarto in ms.

**Cosa esce**, accanto alla traccia: `<nome>-vs-<suffisso>.jpg` (coppie per tempo; magenta la sagoma
del banco, verde «dentro», azzurro «fuori»), `<nome>-vs-<suffisso>-pari-avanzamento.jpg`,
`<nome>-vs-<suffisso>.md` e `.json` (riassunto, istanti, e una riga per coppia: tempo vero, tempo del
banco, scarto, sovrapposizione, distanze).

**Cosa questa regola non sa leggere**, e dichiara invece di contare:

- **sotto il riposo**: quando la sagoma del banco scende sotto quella a riposo (il rimbalzo di un
  tasto), nell'anello lasciato libero «schiarisce» non vuol piu' dire «vetro». Quelle coppie escono
  dal riassunto e dagli istanti (il riposo del vero non si da'); di loro resta solo quanta parte
  dell'anello cambia anche nel vero;
- **dove il vetro non si stacca dalla pagina** piu' della soglia (un pannello chiaro su una pagina
  chiara) il bordo non c'e' in nessuna lettura, e li' il contorno del banco risulta fuori dalla
  fascia senza che la forma sia diversa;
- **a riposo** le due sagome coincidono per costruzione: non e' una prova;
- una differenza di forma piu' piccola della fascia (filo e ombra: 8 px attorno al tasto di oggi)
  non si vede.

## Cosa il banco prova, e cosa no

Misurato in F0 su due viaggi dell'engine di oggi contro il Tab S9
(`artifacts/liquid-glass/moto/f0/convalida-F0-a-contorni.md`).

**Prova:**

- la **sagoma** in ogni punto del viaggio (menu: contorni a 1,8 px tolto il ritardo; tasto: contorno
  nella fascia al 95-99% in salita e in tenuta);
- l'**ordine** degli eventi;
- la **durata** del viaggio **una volta partito** (menu: dal decimo della corsa al picco 181 ms nel
  vero, 190 nel banco).

**Non prova:**

- la **puntualita' della partenza**. Il banco parte sempre al suo `t = 0`; il dispositivo no: il
  menu parte 24-40 ms dopo, il primo fotogramma di un push arriva dopo 65-73 ms;
- i **fotogrammi lunghi**: uno schermo che resta fermo due fotogrammi, un passo da 20 ms in mezzo
  a un viaggio a 120 Hz. Nel banco ogni fotogramma dura quanto dice `fps`;
- il materiale che non rende (filo e ombra di oggi).

Puntualita' e fotogrammi lunghi si giudicano **solo sul dispositivo**. Un viaggio che sul banco e'
puntuale non e' per questo puntuale in mano; un viaggio che sul banco ha la forma giusta, quella
forma ce l'ha.

## La cadenza: 60 o 120 Hz

`fps` dice a che cadenza il viaggio e' stato calcolato, e basta a dichiarare quella del dispositivo:
non serve un altro campo. Conta perche' non tutto l'engine e' funzione del solo tempo: una molla
valutata in forma chiusa da' gli stessi valori a ogni cadenza, la goccia di Fluid-physics invece
avanza a fotogrammi: si accorge del cambio un fotogramma dopo e comincia a integrare da quello
successivo, cioe' parte dopo due fotogrammi (33 ms a 60 Hz, 17 ms a 120). A 120 Hz parte quindi
circa 17 ms prima. Una traccia a 120 Hz
si scrive facendo girare il conducente a 120 (`t = i * 1000 / 120`) con `"fps": 120`; per confrontare
le due cadenze sono **due tracce** (`oggi-menu-apertura` e `oggi-menu-apertura-120`).

Cosa cambia nei prodotti:

- `render`, `first`, `last`: tutti i fotogrammi, cioe' il doppio; i cinque fotogrammi di `first`
  coprono 33 ms invece di 67;
- `collage`: niente, il passo e' in millisecondi (33,4 di serie) e prende il fotogramma piu' vicino;
- `pair` con Apple (29,97 fps): a ogni fotogramma di Apple il nostro piu' vicino, che a 60 Hz dista
  al piu' 8 ms e a 120 al piu' 4. Ma i tempi di Apple hanno +-33 ms: uno scarto di 17 ms fra le due
  cadenze **non si decide contro Apple**. Si decide contro la registrazione del dispositivo, che e'
  a 120;
- `video`: quello a velocita' vera resta a 60 fps (di una traccia a 120 mostra un fotogramma si' e
  uno no; `--video-fps 120` li mostra tutti), quello rallentato li mostra tutti;
- `curve`: istanti con la risoluzione della traccia (8 ms invece di 17).

## Cosa controlla il validatore

`validate` (e ogni altro comando, prima di rendere) si ferma con l'elenco dei problemi, ognuno con
il suo punto (`frames[12].elements[menu].body.uniforms.thickness`); lo stesso errore su molti
fotogrammi si dice una volta. Controlla:

- che il file sia JSON, senza `NaN` ne' `Infinity`, e che ogni numero sia finito;
- i campi della testa, e che non ce ne siano di sconosciuti;
- che i tempi crescano; avvisa se la cadenza non e' quella di `fps`;
- un `name` per elemento, unico nel fotogramma; `kind`, riquadro, raggi;
- che ogni sorgente esista e **compili** (con lo stesso compilatore di `glassbench.py check`);
- che gli uniform siano quelli che la sorgente dichiara, tutti, della taglia giusta, nelle forme
  ammesse; che il corpo e l'effetto abbiano un ingresso e la luce nessuno;
- `pieces`, `ring`, `shadow`, gli strati di `content` e i loro effetti, `contentClip` e `clip`
  (non insieme a `effect` sullo stesso strato);
- eventi dentro i tempi dei fotogrammi; sonde con un `path` che da' un numero almeno una volta;
- i blocchi `apple`, con nomi unici se sono piu' d'uno (se il video non c'e' su questa macchina
  avvisa soltanto).

Non controlla che i numeri siano **giusti**: una traccia valida con una molla sbagliata si rende
benissimo.

## Per chi scrive le tracce dal Kotlin

- **Dove.** `artifacts/liquid-glass/moto/<phase>/<name>/trace.json`: e' la cartella in cui il
  banco mette i prodotti di quella traccia. Niente si scrive se `GLASS_TRACE_DIR` non c'e' (piano 4.3).
- **Numeri.** Solo JSON: punto decimale (`Locale.ROOT`: con la lingua italiana `String.format`
  scrive la virgola), mai `NaN` o `Infinity` (un `Float` non finito e' un difetto da trovare, non
  da scrivere). Tre decimali bastano per i dp, sei per i pesi.
- **Lunghezze in dp, non in pixel.** Gli uniform nell'engine sono pixel del layer; nella traccia
  si scrivono in dp (`pixel / densita' / fattore di scala della cattura`) come `"12.5dp"`. Il banco
  rifa' la moltiplicazione per ogni passata, compresa la cattura a risoluzione ridotta: e' l'unico
  modo perche' corpo (mondo della cattura) e luce (pixel di schermo) ricevano lo stesso campo.
  `"1.0E-4dp"` e' accettato.
- **Tutti gli uniform.** Ogni `uniform` della sorgente va scritto, anche quelli a zero.
- **Coordinate vere, con i decimali.** Niente si arrotonda al pixel: un elemento o uno strato a
  37,43 dp si posa li'. Una superficie di Fluid-physics resta un elemento fermo grande quanto il palco
  con i `pieces` che si muovono dentro perche' **cosi' e' fatta nell'engine**, non piu' per un limite
  del banco.
- **Le grandezze che non sono lunghezze.** Un'area in px^2 (`normalSmooth`) e' `"16dp^2"`, non un
  numero calcolato a una densita': cosi' la traccia vale a ogni `--density`.
- **Il ritaglio del contenuto.** Si scrive cio' che l'engine fa: `clip` sullo strato per un ritaglio
  di quel solo strato (`travelClipIn`), `contentClip` sull'elemento per la sagoma del vetro. Non
  serve piu' uno shader di oggi usato come maschera in `effect`.
- **`t`.** `tMs` della funzione pura, per `i * 1000 / fps`. Il fotogramma a `t = 0` c'e' sempre.
  `fps` e' la cadenza a cui il conducente ha girato (vedi «La cadenza»).
- **Nomi stabili.** Lo stesso elemento ha lo stesso `name` in tutti i fotogrammi: e' la chiave di
  `base` e delle sonde.
- **`base`.** Facoltativo: lo scrittore piu' semplice ripete tutto a ogni fotogramma.
- **Scene ferme.** Una scena ferma e' una traccia di un fotogramma (piano 4.2):
  `glassmotion.py scene traccia.json -o scena.json` ne fa una scena per `glassbench.py`
  (`render --crop`, `measure`, `compare`, immagini di riferimento). Il fotogramma reso dal banco
  del movimento e la scena resa dal banco fermo sono uguali al pixel. Una scena ferma puo' dire
  `"durationMs": 0`. La scena esce con `"subpixel": true`, come il viaggio; `--no-subpixel` la
  vuole arrotondata al pixel, come una lastra che il layout ha posato.
- **Piu' confronti.** Due riprese di Apple dello stesso gesto sono due voci di `apple`, con un `name`.
- **Sorgenti.** `kotlin:Nome` vale per le `const val` che il banco sa leggere (`fp/`,
  `bd/internal/`, `gf/`), composte con `$Nome`: una sorgente costruita da una funzione il banco
  non la vede.
- **Prima di consegnare.** `glassmotion.py validate` su ogni traccia scritta.

## Cosa una traccia non puo' descrivere

- **Il tempo vero.** Fotogrammi persi, cadenza reale, scatti del RenderThread, il passo della
  goccia che sul dispositivo insegue il tempo reale: una traccia dice cosa si disegna a ogni
  istante, non se il dispositivo ci arriva.
- **Compose.** Layout, ricomposizione, quando la cattura si rifa': il banco cattura la pagina
  **a ogni fotogramma**, quindi una cattura tenuta ferma in viaggio (`heldWhileMoving`, rinfresco a
  110 ms) e il passaggio di consegne riposo-palco non si vedono.
- **Il bagliore del dito di oggi.** Il suo shader e' una stringa scritta dentro
  `ui/glass/interaction/GlassInteraction.kt` (`GlassTouchHighlight`), non una `const val
  ...ShaderString`: il banco non la legge, e una traccia non la puo' nominare. Finche' non diventa
  una costante il bagliore di oggi **non e' rendibile**; `oggi-tasto` ne porta i numeri in
  `_bagliore`, come commento.
- **Cio' che l'engine disegna fuori dagli shader.** Bordo e ombra a path di `fluidPhysicsSurface`,
  filetto: le tracce «di oggi» si rendono senza. L'ombra di una traccia
  e' quella del materiale nuovo, per pezzo: un ponte fra due pezzi e una sagoma ad anello non
  hanno ombra, a meno che non sia nello shader.
- **Ritaglio ed effetto sullo stesso strato.** Uno strato ha un effetto solo, e `clip` ne e' uno.
  Un ritaglio che non sia un rettangolo arrotondato (un path) si fa solo con `contentClip: "alpha"`,
  cioe' per tutto l'elemento.
- **Il dito.** Un gesto entra solo come effetto sugli uniform (`glowCenter`, `flex`); il dito non
  si disegna.
- **Il ciclo dell'adattivita'.** La sonda che legge lo sfondo e decide chiaro o scuro non gira
  sul banco: la traccia porta il risultato (gli uniform gia' adattati), non la decisione.
- **L'allineamento nello spazio con Apple.** `pair` allinea nel tempo su un `t0` e una velocita'
  costante; l'inquadratura e' un ritaglio fisso scelto a mano. Una ripresa che si muove, o un
  tempo che cambia a meta' clip, non si allineano.
- E tutto cio' che il banco fermo non prova (`README.md`, «Cosa il banco non prova»): GPU,
  precisione di `half`, P3, ripiego sotto Android 13, carattere dell'app.


## Cosa e' cambiato dopo la prima stesura (F0.4b)

Lo schema resta `1`: tutto cio' che segue e' un'aggiunta, e le tracce gia' scritte restano valide e
si rendono come prima, salvo che niente scatta piu' di un pixel.

| | prima | adesso |
|---|---|---|
| posizioni | riquadri di elementi e strati arrotondati al pixel | frazionarie, sempre |
| contenuto | mai ritagliato | `contentClip` sull'elemento, `clip` sullo strato |
| grandezze in px^2 | un numero valido a una sola densita' | `"16dp^2"` |
| scena ferma | `durationMs` doveva essere positivo | `0` ammesso |
| confronti con Apple | un blocco `apple` | anche una lista con `name`; `pair --apple`, `--t0-frame`... |
| cadenza | `fps` = 60 sottinteso | `fps` e' la cadenza del dispositivo riprodotto (60 o 120) |

F0.4c non tocca lo schema: aggiunge `pair --real` (il confronto con una registrazione vera, per
contorni) e la regola su cosa il banco prova.
