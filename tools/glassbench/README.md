# glassbench: gli shader dell'engine, resi sul PC

Rende con Skia, su Windows e senza dispositivo, **le stesse sorgenti AGSL** che l'engine compila su
Android, scrive PNG da mettere accanto ai frame di Apple e ne tira fuori **gli stessi numeri della
ricerca** (R4 e R5). Serve al progetto «iOS in Android»: il materiale si giudica qui molte volte, sul
tablet poche.

Non e' un emulatore di Compose. Riproduce le passate del vetro (cattura, saturazione, sfocatura,
mescola, lente, pellicola, bordo, ombra, luce) nell'ordine e con i numeri dell'engine, su una scena
descritta in JSON. Cosa prova e cosa no sta in fondo, in «Cosa il banco non prova».

## Installare

Python 3.11. L'ambiente sta in `artifacts/liquid-glass/venv` (cartella ignorata da git):

```powershell
py -3.11 -m venv artifacts\liquid-glass\venv
artifacts\liquid-glass\venv\Scripts\python -m pip install -r tools\glassbench\requirements.txt
```

`requirements.txt` porta **versioni esatte** (skia-python 144.1 cioe' Skia m144, numpy 2.4.6,
Pillow 13.0.0): Skia in software e' deterministico solo a parita' di versione, e le immagini di
riferimento si confrontano con tolleranza zero. `check` avvisa se l'ambiente ne ha altre. Nessuna
GPU: una scena a tutto schermo con uno shader pesante impiega da qualche secondo a una decina.
Niente resta acceso dopo il comando.

## Comandi

```powershell
$py = "artifacts\liquid-glass\venv\Scripts\python"
$gb = "tools\glassbench\glassbench.py"

& $py $gb check                       # compila ogni sorgente AGSL; esce con 1 se una non compila
& $py $gb check --also mio.agsl       # ... e in piu' i file dati (.agsl, o .kt da cui estrarre)
& $py $gb roles                       # le ottiche lette da fluid/GlassMaterial.kt

& $py $gb render tools\glassbench\scenes\rif\pagina-chiara.json     # -> artifacts\liquid-glass\bench\
& $py $gb render scena.json --theme dark --density 2
& $py $gb render scena.json --stages                                 # anche le passate intermedie
& $py $gb render scena.json --crop 96,99,172,72 --zoom 6             # ritaglio in dp, ingrandito
& $py $gb render scena.json --no-content                             # senza icone ne' etichette

& $py $gb measure tools\glassbench\scenes\rif\rif27-tabbar.json     # i numeri, accanto a quelli di Apple
& $py $gb measure tools\glassbench\scenes\rif\*.json --md misure.md --json misure.json --detail

& $py $gb compare a.png b.png                                        # esce con 1 se differiscono
& $py $gb compare a.png b.png --max-diff 2 --mean-diff 0.05 --diff differenze.png
& $py $gb compare --golden classic                                   # le scene contro le immagini di riferimento
& $py $gb golden classic --update                                    # le riscrive (solo di proposito)

& $py $gb sheet a.png b.png frame_apple.jpg -o confronto.png --labels "oggi|nuovo|Apple"
& $py $gb selftest                                                   # le prove del banco su se stesso
```

`--theme` e `--density` forzano il valore scritto nella scena; con `--theme` il file prende il
suffisso (`oggi-foto-dark.png`). `-o` e' un file se finisce in `.png`, altrimenti una cartella.

**Chi tocca una sorgente AGSL** lancia `check` e `compare --golden classic` prima di consegnare, e
allega i diff. Non serve Gradle.

## Da dove vengono shader e numeri

| cosa | da dove | come |
|---|---|---|
| shader del vetro e del bordo | `glass/backdrop/internal/Shaders.kt` | stringhe `"""..."""` estratte, con `$Nome` e `${Nome}` risolti come fa Kotlin |
| shader di Fluid-physics | `fluidphysics/FluidPhysicsShaders.kt` | idem, comprese le costanti (`$PhysicsMaxPieces`) |
| shader del materiale nuovo | ogni `.kt` di `glass/field/` (il `gf/` del piano), sottocartelle comprese | idem; un'interpolazione puo' puntare a una stringa o a una costante di **un altro file del pacchetto**. Se la cartella non c'e' il banco legge solo i due file sopra |
| ottiche dei ruoli, regola della taglia, risoluzione della cattura | `fluid/GlassMaterial.kt` | `XxxOptics = GlassOptics(...)`, i `.copy(...)`, le `const val` |
| colore del bordo e dell'ombra, alfa delle pellicole, grigi del vetro | `fluid/GlassMaterial.kt` | **ricopiati** in testa a `render.py`, con il nome della funzione d'origine |
| raccordo continuo degli angoli | `fluid/FluidShape.kt` | **portato** riga per riga in `render.py` (`continuous_path`, `Outline`), con le tre costanti ricopiate |
| palette neutra dell'engine | `theme/FluidTheme.kt` (`surfacePalette`) | **ricopiata** in `render.py` (`"palette": "engine"`) |
| shader di prova | `tools/glassbench/shaders/*.agsl` | file a parte, letti cosi' come sono |
| icone dell'engine | `tools/glassbench/icons/*.json` (le scrive `tools/icons/gen_fluid_icons.py`, pacchetto F0.8) | lette se ci sono; finche' mancano il banco usa i suoi disegni di servizio |

Quindi uno shader o un'ottica cambiati nell'engine cambiano il render senza toccare il banco. Cio'
che e' **ricopiato** no: se cambia nell'engine va ricopiato (pellicole `TINT_ALPHA`, ricetta del
bordo `RIM`, costanti di `FluidShape.kt`, palette). Una stringa grezza di `gf/` che non e' AGSL (un
messaggio, un JSON) viene riconosciuta e saltata; una sorgente composta da una funzione Kotlin
invece che da `const val` con `$Nome` il banco non la sa leggere, e `check` lo dice.

## Descrivere una scena

Le misure sono in **dp**; `density` le porta in pixel. Esempio minimo:

```json
{
  "width": 480, "height": 270, "density": 3.0, "theme": "light",
  "palette": "engine", "corner": "engine",
  "background": {"kind": "color", "color": "#F2F2F7"},
  "elements": [
    {"name": "tasto",   "role": "button",   "x": 30,  "y": 113, "w": 44,  "h": 44, "radius": "capsule", "icon": "plus"},
    {"name": "capsula", "role": "floating", "x": 104, "y": 107, "w": 156, "h": 56, "radius": "capsule", "icons": ["back", "plus", "circle"]}
  ]
}
```

Campi della scena: `width`, `height`, `density`, `theme` (`light`/`dark`), `background`, `page`,
`elements`, `measure`, e:

- `palette`: una famiglia per nome (`material`, di serie: Material 3 di base; `engine`: i neutri di
  `FluidTheme.kt` con l'accento di serie; `engine-amoled`), oppure un oggetto di colori che ritocca
  quella di base (`paletteBase` sceglie quale). La palette dell'app sul PC non esiste;
- `corner`: come si disegna la sagoma di ogni lastra (un elemento puo' dire il suo):
  - `round` (di serie, per non cambiare le scene vecchie): rettangolo arrotondato circolare al raggio
    nominale, per tutto;
  - `engine`: la scelta dell'engine. Sotto 256 px di lato lungo un rettangolo arrotondato al raggio
    equivalente (x0,82; le capsule no). Sopra, pellicola, bordo e ombra seguono il **path continuo**
    e il ritaglio hardware resta al raggio nominale. La lente riceve sempre i raggi nominali;
  - `continuous`: il path continuo sempre, anche sui piccoli (serve ai confronti).

**Sfondo** (`background.kind`):

- `color`: `{"color": "#RRGGBB"}`;
- `gradient4`: quattro colori agli angoli (`tl`, `tr`, `bl`, `br`), interpolati in sRGB;
- `text`: testo che riempie la tela (`text` o `lines`, `fontSizeDp`, `lineHeightDp`, `paddingDp`,
  `color`, `background`);
- `grid`: righe ogni `stepDp` spesse `lineDp` (`color` su `background`, `originDp`);
- `checker`: scacchi di lato `stepDp`;
- `synthetic`: una «fotografia» fatta di conti (macchie di colore, striature, dettaglio a piu'
  scale), uguale a ogni densita' e su ogni macchina: `palette`, `scaleDp`, `detail`, `contrast`,
  `lift`. E' la foto delle scene `rif/` e delle immagini di riferimento, perche' i frame di Apple
  **non stanno nel repo**. Cio' che si misura sotto una foto (contrasto e nitidezza che restano,
  colore che passa) dipende dalla sua statistica, non dal soggetto;
- `image`: `{"path": "...", "crop": [x, y, w, h]}`, riempie la tela tenendo le proporzioni. Le
  scene vecchie `*-foto.json` puntano con un percorso assoluto a un frame del materiale di Alessio
  in `Downloads`: su un'altra macchina va cambiato.

`"page"` e' una lista di cose disegnate sopra lo sfondo **dentro la cattura** (il corpo della
schermata, cio' che il vetro rifrange): `rect`, `circle`, `text`, `icon`, `background` (uno sfondo
qualsiasi dentro un rettangolo: una foto in una fascia, mezza pagina a scacchi), `shape`.

### Una lastra di oggi (`kind: "glass"`, di serie)

| campo | significato |
|---|---|
| `x`, `y`, `w`, `h` | in dp |
| `radius` | `"capsule"`, un numero, o quattro (alto-sx, alto-dx, basso-dx, basso-sx) |
| `role` | `bar`, `floating`, `interactive`, `button`, `modal`, `content`, `stackedcontent`, `panel`, `compactpanel`, `sharppagepanel`, `compactsharppagepanel` |
| `optics` | al posto di `role`, per ritoccare: `{"role": "floating", "blurScale": 3, "dispersion": true}` |
| `tint` | `bar`, `floating`, `control`, `modal`, `panel`, `content`, `none`, o un colore `#RRGGBBAA` |
| `intensity`, `depth`, `pressed`, `quality` | 0..1: gli stessi parametri di `glassSurface` e di `FluidGlassQuality` |
| `backdropScale` | forza la risoluzione della cattura (di serie la calcola come l'engine) |
| `theme`, `palette` | il tema di questa sola lastra (vetro scuro in una scena chiara) |
| `corner` | vedi sopra |
| `icon`, `icons`, `label`, `rows`, `items` | il contenuto stampato sopra |
| `contentLayer` | il contenuto nel suo layer, con sfocatura, scala e un effetto a shader (vedi sotto) |
| `children` | lastre dentro questa: campionano il suo materiale (vetro su vetro, 2.13.0) |

**Icone.** `"plus"` e' un disegno di servizio del banco. `"fluid:add|plus"` e' l'icona `add` del set
dell'engine se `tools/glassbench/icons/` c'e', altrimenti il disegno di servizio `plus`. Le scene
`rif/` usano questa forma. `--service-icons` (e le immagini `classic`) forzano i disegni di servizio.
Il lettore accetta, per ogni nome, un `d` di SVG o un oggetto con `path` / `d` / `pathData` /
`paths`, e il riquadro in `viewport` / `size` / `viewBox`: e' scritto prima del generatore, e
`check` segnala cio' che non riesce a leggere.

### Uno shader qualsiasi (`kind: "shader"`)

Disegna uno shader a tutta area sul rettangolo dato: la forma di `fluidPhysicsSurface`, di un corpo
in una passata, e **senza ingressi** quella della luce.

```json
{
  "kind": "shader", "source": "file:../shaders/sonda-materiale.agsl",
  "x": 0, "y": 0, "w": 480, "h": 270,
  "inputs": {
    "content": {"saturation": 1.3},
    "blurred": {"saturation": 1.3, "blurDp": 10, "edge": "clamp"}
  },
  "uniforms": {"thickness": "12dp", "tintColor": "#FFFFFF2E", "lightDir": [0.7071, 0.7071],
               "pieceCount": 2, "pieceRect": [["52dp", "135dp", "22dp", "22dp"], ["182dp", "135dp", "78dp", "28dp"]]}
}
```

- `source`: `file:percorso.agsl` (relativo alla scena) oppure `kotlin:NomeDellaCostante`;
- `inputs`: un ingresso per ogni `uniform shader`. Ognuno e' il fondale sotto il rettangolo con la sua
  catena a monte: `saturation`, `blurDp`, `edge` (`decal`, trasparente fuori, o `clamp`) e `w`, la
  **mescola a monte**. Se `inputs` manca, ogni ingresso riceve il fondale cosi' com'e'; se lo shader
  non ha ingressi non si collega niente (`"inputs": {}` lo dice in chiaro);
- `uniforms`: numeri cosi' come sono, `"12dp"` moltiplicato per la densita', `"#RRGGBBAA"` un colore
  non premoltiplicato, e i valori del banco `"$size"`, `"$center"`, `"$halfSize"`, `"$offset"`,
  `"$captureSize"`, `"$density"`, `"$dark"` (per il materiale anche `"$radii"` e `"$padding"`). Le
  liste si appiattiscono; un array non pieno si completa con zeri;
- `blend`, `alpha`: **il modo di comporre lo strato**. L'uscita dello shader e' premoltiplicata e si
  posa sullo schermo con `blend` (i nomi di `BlendMode` di Compose: `SrcOver` di serie, `Plus`,
  `Multiply`, `Screen`, ...). Uno shader senza ingressi che restituisce nero al 30% scurisce, uno
  che restituisce bianco al 30% schiarisce, con la stessa passata;
- `scale`: rende lo shader a una frazione della risoluzione e lo riporta in scala;
- `marginDp`: allarga cattura e disegno oltre il rettangolo (una lastra che cede o viaggia); lo
  shader riceve `$offset` = -margine;
- `clip`: ritaglia l'uscita sulla sagoma (`radius`, `corner`).

**La mescola a monte (via A, piano 3.6).** Con `"w"` l'ingresso passa per la catena che Android puo'
fare con un `RenderEffect` a un solo ingresso:

```
ColorFilter(saturazione) -> Blur(Clamp) -> BlendMode(Plus, w * nitido, (1 - w) * sfocato) -> shader
```

cioe' `w * nitido + (1 - w) * sfocato`, dove «nitido» e' la cattura saturata e non sfocata; ogni
passaggio arrotonda a 8 bit, come una catena di layer. `w` e' una **dissolvenza**, non una sfocatura
intermedia: con `w = 0,26` resta il 26% del contrasto ma con le lettere nitide sopra la loro copia
sfocata. Per questo `measure` da' sempre due numeri (vedi sotto).

### Il materiale nuovo (`kind: "material"`)

Compone ombra, corpo e luce **nell'ordine dell'engine** (piano 3.3):

```json
{
  "kind": "material", "name": "tasto", "x": 30, "y": 113, "w": 44, "h": 44, "radius": "capsule",
  "clip": true, "marginDp": 0,
  "shadow":  [{"radiusDp": 1.5, "offsetDp": [0, 0.5], "alpha": 0.10},
              {"radiusDp": 6,   "offsetDp": [0, 2],   "alpha": 0.05}],
  "capture": {"saturation": 1.4, "blurDp": 10, "w": 0.0, "paddingDp": 10, "scale": 1.0},
  "body":    {"source": "kotlin:GlassBody1ShaderString", "uniforms": {"offset": "$offset", "...": 0}},
  "light":   {"source": "kotlin:GlassLight1ShaderString", "uniforms": {"offset": "$offset", "...": 0}},
  "icon": "plus"
}
```

1. `shadow`: le maschere di `GlassShadowNode` (contatto, ambiente), prima della lastra, fuori dal suo
   ritaglio e scavate dentro la sagoma. Ogni voce: `radiusDp`, `offsetDp`, `alpha`, `color`, `spreadDp`;
2. `capture`: la catena a monte, **una per lastra**: saturazione, sfocatura con `Clamp`, mescola
   `w`. La cattura e' grande taglia + 2 x (`paddingDp` + `marginDp`); `scale` e' la risoluzione
   ridotta, come su HWUI. L'engine usa padding = max(0, sfocatura - spessore della lente) + margine:
   chi scrive la scena dalla ricetta mette `paddingDp`, senza il banco tiene tutta la sfocatura;
3. `body`: lo shader del corpo. Ogni suo ingresso riceve l'uscita della catena, trasparente fuori
   dai bordi (`body.inputs` puo' dare a un ingresso una catena sua);
4. `clip`: il ritaglio hardware sulla sagoma. `false` dove la lastra cede o viaggia: allora ritaglia
   lo shader, e conta `marginDp`;
5. `light`: lo shader della luce, **senza ingressi**, a risoluzione piena, posato in `SrcOver`
   **sotto** il contenuto. Se ha un ingresso il banco si ferma (la luce non legge il fondale), a
   meno che `light.inputs` lo dichiari;
6. il contenuto (e `contentLayer`), poi i `children`, che campionano pagina + corpo, **mai** la luce
   ne' il contenuto: e' l'export di `drawBackdrop`.

Per il corpo `"12dp"` e i valori `$...` sono nel mondo rimpicciolito della cattura (`scale`), per la
luce in pixel di schermo. Il filetto di `GlassEdge` non si rende.

**Gli uniform di una scena `material` non si scrivono a mano**: li calcola `glassRecipe` e li scrive
lo scrittore delle tracce dell'engine (piano 4.2). Fanno eccezione le prove di F1A.2-F1A.4, prima
che la ricetta esista, che non diventano mai immagini di riferimento.

### Il contenuto nel suo layer, con un effetto a shader

Su qualunque elemento, `contentLayer` mette icone ed etichette in un layer loro, come un
`graphicsLayer` di Compose con un `RenderEffect`: e' la lente del contenuto di un viaggio (F5A).

```json
"contentLayer": {"blurDp": 3, "scale": 1.15, "alpha": 0.9, "marginDp": 8,
                 "effect": {"source": "kotlin:GlassContentLensSlabShaderString", "uniforms": {"...": 0}}}
```

Ordine: il contenuto si disegna nel layer; `blurDp` lo sfoca; `effect` e' uno shader il cui ingresso
(`input`, di serie il primo `uniform shader`) e' il layer sfocato; poi il layer si posa con `scale`
(attorno al centro, un numero o `[sx, sy]`) e `alpha`. `marginDp` allarga il layer; lo shader riceve
`$offset` = -margine.

### Il path continuo, per confrontarlo con un campo

`{"kind": "shape", "corner": "continuous", "x": 10, "y": 10, "w": 180, "h": 130, "radius": 44,
"color": "#111114", "stroke": 0.34, "strokeAlign": "inside", "smoothing": 0.6}` disegna una sagoma
piena o al tratto: fra gli `elements` sta sopra la pagina e fuori dalla cattura, in `page` ci sta
dentro. E' lo stesso emettitore di `ContinuousCornerShape`: `selftest` verifica che parta a
(1 + s) r dall'angolo, che dal cerchio nominale si scosti di circa l'1,25% del raggio e solo negli
angoli, e che una capsula sia un semicerchio.

## Le scene di riferimento (`scenes/rif/`)

Ogni scena porta nel campo `apple` il frame, il ritaglio (in `artifacts/liquid-glass/riferimenti/`,
cartella ignorata da git) e la scala; nel campo `measure` le grandezze da misurare, con accanto i
valori di Apple copiati dalla ricerca. Le lastre sono alla **taglia vera in dp** (tasto 44, tab bar
62): accanto a un ritaglio di Apple si mettono con `sheet`, che le porta alla stessa altezza.

| scena | ricostruisce | frame o ritaglio |
|---|---|---|
| `rif-tondo-chiaro` | tasto tondo su fondo 174-184 | `ritagli/A1_bordo_di_luce.jpg`, `f8632` |
| `rif-capsula-chiara` | capsula di una barra su fondo 200-210 | `f28855` |
| `rif-capsula-piatto`, `rif-capsula-testo` | la stessa capsula su fondo piatto e su testo nero | `ritagli/A3_ombra.jpg`, `A5_smerigliatura.jpg`, `f21570`, `f21300` |
| `rif-barra-menu-foto` | barra e menu sulla stessa foto e sulla striscia bianca | `f13040`, `f13080` |
| `rif-bianco-nero` | vetro chiaro e scuro su bianco e nero puri; pannello grande | `ritagli/A6_chiaro_scuro.jpg` |
| `rif-tinta` | rosso su bianco e su scacchi, verde su nero | `ritagli/A7_tinta.jpg` |
| `rif-lente-griglia` | lente grande su griglia | `ritagli/A_lente_griglia_f1485_f1700.jpg` |
| `rif-tondo-scuro` | tasto scuro su fondo 28-50 | `f12384` |
| `rif-bordo-scorrimento` | testo che scorre sotto una riga di tasti | `f16636`-`f16700` |
| `rif27-tabbar` | tab bar e accessorio su pagina 235 e arte colorata: **il confronto di Apple fra iOS 26 e iOS 27** | `ios27/ritagli/M1_tabbar_ios26_sopra_ios27_sotto.jpg` |
| `rif27-tabbar-telefono` | tab bar su pagina 238 con il tondo a destra | `ios27/ritagli/T6_scheda_prominente_269_0519.jpg` |
| `rif27-tasto-bianco` | tasto tondo su bianco 253 | `ios27/ritagli/E1_bordo_scorrimento_alto_278.jpg` |
| `rif27-scuro` | tab bar scura su griglia colorata; barra chiara su foto scura | `ios27/ritagli/M3_tab_bar_scura_292_0632.jpg`, `B11_barra_e_trabocco_269.jpg` |
| `pagina-chiara`, `pagina-scura`, `pagina-nera`, `pagina-bianca`, `pagina-testo`, `pagina-foto` | gli stessi tre elementi (tasto, capsula, menu) sui **fondi veri dell'engine**: `#F2F2F7`, `#0D0D0F`, nero, bianco, un testo, una foto | nessun frame: e' dove si decide se il materiale stacca dalla pagina delle app |

Dove il frame ha una foto la scena ha una foto **sintetica** con la stessa statistica (luma media e
deviazione standard dichiarate nella scena). `rif-bordo-scorrimento` rende la fascia di oggi con la
sonda `sonda-bordo-maschera.agsl`, perche' `GlassFalloff.FadeDown` il banco non lo rende: i suoi
numeri «di oggi» sono indicativi.

## Misurare (`measure`)

`measure.py` porta nel repo i conti degli attrezzi della ricerca (`rim.py`, `profile.py`,
`probe.py`, `scrolledge.py`, `ios27_misure.py`, che stanno in `artifacts/`) e li applica al PNG reso.
Unita', come in R4: luma Rec. 709 sui valori sRGB 0-255; distanze in frazioni dell'elemento (`H`,
`D`) e in pixel; percento. Stampa una tabella «banco / Apple iOS 26 (R4) / Apple iOS 27 (R5) /
fiducia»: il giudizio parte da li', non da un'impressione.

Due differenze dal metodo della ricerca, a favore del banco: il banco ha la **pagina nuda**, quindi
«contro il fondo» vuol dire contro gli stessi pixel senza vetro; e misura la scena **senza il
contenuto** delle lastre (`--with-content` per tenerlo), per non dover scansare i glifi.

Una voce di `"measure"`: `kind`, `element` (il `name` di un elemento; oppure `rect`/`window` in dp),
e facoltativi `scheda`, `fiducia`, `label` (prefisso), `rows` (le sole grandezze da tenere), `ref`
(grandezza -> `[valore di iOS 26, valore di iOS 27]`).

| `kind` | cosa misura | grandezze (chiavi di `rows` e `ref`) |
|---|---|---|
| `body` | luminanza del corpo contro lo sfondo (R4 A2, A6; R5 M1, M3, M4). `at` = finestra in frazioni dell'elemento | `corpo`, `fondo`, `corpo-fondo`, `velo` (quanta strada verso il bianco o il nero), `uniformita`, `gradiente` |
| `edge` | profilo del bordo su un lato (`side`, `along`) o a un angolo (`angle`, `cap`): finestre di `rim.py` | `filo-scuro`, `filo-largo`, `riflesso`, `riflesso-largo`, `riflesso-rientro`, `corpo-fondo` |
| `rim` | il giro completo attorno a un tondo ogni `step` gradi | `riflesso`, `riflesso-archi`, `riflesso-largo`, `filo-scuro`, `filo-archi`, `filo-largo`, `corpo-fondo` |
| `shadow` | scurimento della pagina sotto il bordo basso a 0,03-0,8 H (le distanze di `ios27_misure.py`), massimo, estensione, sopra e ai lati. `refElement` per misurare in altezze di un altro elemento | `ombra@0.03` ... `ombra@0.8`, `ombra-max`, `ombra-estensione`, `ombra-sopra`, `ombra-lati` |
| `frost` | la smerigliatura in una finestra | `contrasto`, `intervallo`, `nitidezza`, `laplaciano`, `estremi`, `colore` |
| `stepedge` | un gradino della pagina visto attraverso il vetro (`line` = `h:Y:X0:X1` o `v:X:Y0:Y1`, `channel`) | `gradino-10-90`, `gradino-locale`, `gradino-residuo` |
| `scrolledge` | inviluppo del contrasto del testo sotto una riga di tasti (`x`, `lineDp`) | `contrasto@-0.5` ... `contrasto@0.95`, `contrasto-50`, `contrasto-pieno` |
| `tint` | RGB medio del corpo e della pagina sotto | `colore`, `colore-fondo` |
| `profile` | la luma lungo un segmento (finisce nelle note, con `--detail`) | `profilo` |

**La smerigliatura si misura sempre su due grandezze**: `contrasto` (deviazione standard, vetro /
pagina) e `nitidezza` (gradiente medio, vetro / pagina). Una sola non distingue un vetro smerigliato
da una dissolvenza: `selftest` lo prova con una dissolvenza al 25% (25% e 25%) e una sfocatura vera
(41% e 22%). `laplaciano` e' la nitidezza di `probe.py`: vale dove la pagina ha dettaglio fine
forte (un testo); su un fondo morbido il suo valore e' il rumore dell'arrotondamento a 8 bit, e resta
alto anche sotto un vetro che sfoca.

Il **filo scuro** e' una linea piu' scura sia del corpo sia della pagina, cercata nei pixel pieni
della lastra; il **riflesso** e' un picco sopra il corpo. Sotto 3 livelli non ci sono («nessuno»):
R4 da' +-5 livelli ai suoi profili. Su testo e foto il profilo del bordo legge le lettere, non il
filo: li' non si misura.

`--image foto.png` misura un PNG dato (stessa taglia della scena) al posto del render: uno
screenshot del dispositivo ritagliato sulla scena, per esempio.

## Confrontare (`compare`) e le immagini di riferimento (`golden/`)

`compare a.png b.png` stampa differenza massima, media e pixel diversi, scrive l'immagine delle
differenze (i pixel diversi in magenta sopra la prima immagine in grigio) ed **esce con 1** se le due
differiscono oltre le soglie. Di serie le soglie sono zero.

`golden/classic/` sono le immagini del **vetro di oggi**, tracciate in git: 13 PNG a densita' 2,
senza fotografie esterne al repo. `manifest.json` elenca scena, tema e PNG, e `--update` vi scrive
le versioni esatte, la piattaforma, l'impronta dei sorgenti dell'engine letti e quella di ogni PNG.

| immagine | scena | copre |
|---|---|---|
| `pagina-*` (sei) | `scenes/rif/pagina-*.json` | tasto, capsula e menu sui sei fondi |
| `rif-bianco-nero`, `rif-tinta`, `rif-lente-griglia`, `rif27-tabbar` | `scenes/rif/` | tema per elemento, tinta, la lente grande con la regola della taglia, barra e accessorio su un fondo fitto |
| `ruoli-chiaro`, `ruoli-scuro` | `golden/classic/scene/ruoli.json` | gli undici ruoli; dispersione, pressione, qualita' e intensita' ridotte, **raggi diversi per angolo**, vetro su vetro |
| `fluidphysics` | `golden/classic/scene/fluidphysics.json` | le quattro sorgenti di Fluid-physics (campo a pezzi e ad anello, con e senza frangia) e `AmbientHighlightShaderString` |

Cosa provano: **la parita' ottica degli shader AGSL e dei numeri di `Classic` sul PC**. Coprono le
sorgenti di oggi e i numeri che il banco legge da `GlassMaterial.kt`. Prova fatta: rimettendo in
memoria il vecchio `radiusAt(coord, ...)`, `ruoli-chiaro` e `ruoli-scuro` risultano diverse.

Cosa **non** provano: il disegno Kotlin (pellicola, filetto, bordo e ombra a path, che il banco
ridisegna per conto suo), quale ramo `glassSurface` prende su un dato Android, il ripiego sotto API
33. Non sono «la prova che il vetro di oggi non e' cambiato»: sono una delle tre, con i test del
vetro di oggi che restano verdi e con i controlli di selezione del percorso.

Quando una immagine non torna, `compare --golden` dice quale sorgente dell'engine e' cambiata da
quando le immagini sono state create, e avvisa se le versioni installate non sono quelle del
manifesto. Le immagini si riscrivono solo di proposito (`golden classic --update`, che rifiuta
versioni diverse da `requirements.txt` senza `--force`), con il diff nel rapporto. La famiglia
`golden/liquid/` nascera' con la ricetta (F1A): stessa forma, un manifesto suo.

## Aggiungere uno shader nuovo

1. Scrivi `tools/glassbench/shaders/nome.agsl`. Solo AGSL di Android 13: cicli `for` a limite
   costante, indici di array costanti o la variabile del ciclo, niente `dFdx`/`fwidth`, niente
   `while`, lo sfondo solo con `ingresso.eval(coord)`.
2. Mettilo in una scena come `kind: "shader"`, o come `body` / `light` di un `kind: "material"`.
3. `glassbench.py check` lo compila; `render` lo disegna.
4. Quando entra nell'engine, **la sorgente e' una sola**: o il Kotlin la carica dal file, o il banco
   la legge dal Kotlin con `kotlin:Nome`. Non tenere due copie.

Le sonde in `shaders/`:

| file | ingressi | a cosa serve |
|---|---|---|
| `sonda-materiale.agsl` | **due** (`content`, `blurred`) | la sonda di fattibilita' di R3: su Skia funziona, su Android un `RenderEffect` collega un ingresso solo, quindi li' **non si puo' montare** |
| `sonda-via-a.agsl` | **uno** (`content`, la mescola a monte) | il corpo del prototipo della via A: campo a 8 pezzi, lente, dispersione a tre campioni, velo, ombreggiatura, maschera. E' quello che la pagina «Misure» (F0.6) copia fra le risorse del `sample` |
| `sonda-luce.agsl` | **nessuno** | la luce del prototipo, disegnata sopra il corpo: filo scuro, riflessi su due lobi, bagliore del dito, in un colore premoltiplicato da posare in `SrcOver` |
| `sonda-bordo-scorrimento.agsl`, `sonda-bordo-maschera.agsl` | uno | il bordo dello scorrimento: sfocatura progressiva in una passata, e il termine di paragone di oggi |

Sono **sonde**, non il materiale: i numeri sono messi a occhio. `sonda-via-a` e `sonda-luce` non
hanno il velo adattivo, il raccordo continuo degli angoli ne' la taratura (lavoro di F1A).

## Cosa riproduce fedelmente

- **Gli shader**: sono le stringhe dell'engine, compilate dallo stesso compilatore SkSL che Android
  usa per AGSL. Lente, dispersione, bordo speculare, campo fuso di Fluid-physics.
- **L'ordine delle passate** di `glassSurface`: ombra sotto e scavata dentro la sagoma; cattura;
  saturazione, poi sfocatura, poi lente; ritaglio; pellicola; contenuto; bordo sopra, nel suo layer,
  con il suo modo di comporre (`Plus` sullo scuro, `SrcOver` sul chiaro). E quello del materiale
  nuovo: ombra, catena a monte, corpo, luce, contenuto.
- **I numeri**: ottiche dei ruoli, regola della taglia, margine della cattura (`padding`),
  risoluzione ridotta della cattura (la catena gira sui pixel ridotti e si riporta in scala, come su
  HWUI, che applica il `RenderEffect` all'immagine del layer e non allo schermo).
- **I raggi di sfocatura**: `sigma = 0,57735 * raggio + 0,5`, il conto di HWUI sia per
  `RenderEffect.createBlurEffect` sia per `BlurMaskFilter`.
- **La premoltiplicazione**: superfici e ingressi premoltiplicati, come su Android; gli uniform
  `layout(color)` ricevono colori non premoltiplicati.
- **Vetro su vetro**: una lastra figlia campiona pagina + materiale del pannello, senza il suo
  contenuto (e senza la luce, per il materiale nuovo).
- **La sagoma**: con `corner: "engine"`, la stessa scelta per taglia di `ContinuousCornerShape` e
  di `ShapeProvider`, e lo stesso path.

Verifica fatta: `scenes/confronto-reale-scuro.json` accanto ai tasti veri di
`artifacts/migrazione/codex/dopo/08-chats-scuro.png` (stessa densita', palette stimata): stesso
riempimento, stesso filo chiaro pieno sopra e sotto e spento sui fianchi. `measure` lo ritrova:
sul tondo di `rif-tondo-scuro` il bordo di oggi e' acceso fra 45 e 135 gradi e fra 225 e 315, e
manca ai fianchi.

## Cosa il banco non prova

- **Il ripiego sotto Android 13.** Il banco rende solo AGSL. Sotto API 33 l'engine disegna un altro
  vetro (pellicola e sfocatura senza shader), e **quale percorso** sceglie su un dato Android lo
  decidono i test di logica e il dispositivo, non il banco. Un'immagine di riferimento verde non
  dice niente di quel vetro.
- **La GPU.** Skia qui rende in software. Non si vedono: la precisione di `half` (qui 32 bit, su
  molte GPU 16: bande, coordinate grandi), i limiti dei driver, le differenze fra Mali e Adreno, una
  sorgente che compila in SkSL m144 e non nello Skia piu' vecchio di Android 13 (vale la regola del
  punto 1 di «Aggiungere uno shader nuovo»). `check` verde vuol dire «compila in Skia m144», non
  «compila su ogni dispositivo»: per quello c'e' il test strumentato della galleria.
- **I tempi.** Niente costo per fotogramma, niente riuso della sfocatura quando cambiano i soli
  uniform, niente prima compilazione, niente RenderThread. I tempi del banco sono di CPU e non
  dicono nulla del telefono: i cancelli C1-C6 si chiudono sul dispositivo (pagina «Misure»).
- **Il movimento.** Una scena e' un fotogramma. Fasi, tempi e sagome in viaggio sono del banco del
  movimento (`glassmotion.py`).
- **La sfocatura al pixel.** Skia in software approssima la gaussiana con tre passate di media per i
  sigma grandi; la GPU di Android usa una gaussiana vera, ridotta di scala. La differenza e' sotto la
  soglia di cio' che si giudica a occhio, ma un confronto pixel per pixel con uno screenshot non
  torna a zero.
- **Lo spazio colore.** Il banco lavora in sRGB a 8 bit, senza linearizzare (come HWUI su uno
  schermo sRGB). Su un pannello P3 HWUI converte i colori, e le superfici intermedie possono essere
  a 16 bit: saturazione e `Plus` sui colori molto vivi possono differire, e la mescola a monte
  arrotonda in modo diverso.
- **Il campionamento.** Gli ingressi sono campionati in modo lineare e trasparenti fuori dal bordo,
  come fa `RenderEffect.createRuntimeShaderEffect`; il ritaglio antialias di Skia e quello hardware
  del `RenderNode` differiscono di frazioni di pixel.
- **Cio' che l'engine disegna in Kotlin, fuori dagli shader.** Pellicola, bordo e ombra il banco li
  **ridisegna per conto suo** dai numeri ricopiati: se il Kotlin cambia senza cambiare quei numeri,
  il banco non se ne accorge. Non rende affatto: `glassMask` e `GlassFalloff.FadeDown` (la fascia in
  alto), l'ombra interna (oggi a zero in tutti i ruoli), il filetto (`hairline` e `GlassEdge`), il
  bordo e l'ombra a path di `fluidPhysicsSurface`, il bagliore al tocco, il contrasto di sistema
  (`forSystemContrast`). Un elemento `kind: "shader"` rende lo shader e basta.
- **Tutto cio' che e' Compose.** Layout, ricomposizione, quando la cattura si rifa', l'annidamento
  dei layer, l'invalidazione, gli inset. Una scena dice dove sta una lastra: non prova che l'engine
  la metta li'.
- **Che i numeri di una scena siano quelli dell'engine.** Per il vetro di oggi il banco li legge dai
  sorgenti. Per il materiale nuovo valgono solo se la scena e' scritta dalla ricetta dell'engine
  (piano 4.2): uno uniform ricopiato a mano descrive un'intenzione, non cio' che l'engine disegnera'.
- **Il carattere e la macchina.** Etichette e fondi di testo usano Segoe UI, non il font dell'app, e
  il font e' quello installato: le immagini di riferimento con del testo valgono su questo PC (il
  manifesto registra la piattaforma). Le icone sono disegni di servizio finche' il set non c'e'. La
  foto sintetica e' fatta di conti in virgola mobile: su un altro processore un pixel puo' cambiare
  di un livello.
- **Le misure non sono i frame.** `measure` usa il metodo della ricerca, ma su una scena ricostruita:
  fondi sintetici, taglie in dp invece dei pixel del video, nessuna compressione. Un numero del banco
  entro l'incertezza di R4 o R5 e' un buon segno, non una prova: il confronto a occhio accanto al
  ritaglio resta obbligatorio, con i ritagli a 6x di un bordo dritto e di un angolo.

## Differenze fra SkSL di Skia e AGSL incontrate

- Nessuna di sintassi: tutte le sorgenti dell'engine che compilano qui sono AGSL valido. Il banco
  non fa nessuna traduzione.
- **`flat` e' una parola riservata** (con `noperspective`). Dalla 3.0.0 alla 3.1.2 i due shader
  poligonali dichiaravano `half4 flat = ...` e non compilavano (`error: expected ';', but found
  'flat'`): tutto Fluid-physics girava al livello `Balanced`. Corretto nell'engine (`flatSample`).
  `selftest` tiene quella sorgente sbagliata apposta e verifica che `check` esca con 1.
- Skia m144 e' piu' nuovo dello Skia di Android 13: una funzione di SkSL aggiunta dopo potrebbe
  compilare qui e non la'.
- `RuntimeShaderBuilder.setUniform` di skia-python non accetta gli array: il banco impacchetta gli
  uniform a mano (`Program` in `render.py`), nell'ordine di dichiarazione.
- skia-python non espone `SkParsePath`: i tracciati SVG delle icone li legge `svg_path` in
  `render.py`.
- `radiusAt(coord, cornerRadii)` negli shader di Kyant riceveva la coordinata del layer invece di
  quella centrata, e sceglieva sempre il raggio in basso a destra. Corretto nell'engine (F0.2): le
  immagini `classic` sono state create **dopo** la correzione.

## File

```
glassbench.py        comandi
kotlin_sources.py    estrazione di shader e ottiche dai sorgenti Kotlin
render.py            le passate; `render_scene` e `render_scene_full` sono l'ingresso per chi lo usa da fuori
measure.py           i numeri di una scena, nelle unita' della ricerca
requirements.txt     versioni esatte
scenes/              oggi-*.json (il vetro di oggi), confronto-reale-scuro.json,
                     fluidphysics-gruppo-oggi.json, f0-raggi-diversi.json, sonda-*.json
scenes/rif/          le inquadrature dei frame di Apple (R4, R5) e le sei pagine dell'engine
shaders/             le sonde
golden/classic/      immagini di riferimento del vetro di oggi, manifesto, scene proprie
icons/               (quando c'e') i tracciati di FluidIcons scritti dal generatore
```
