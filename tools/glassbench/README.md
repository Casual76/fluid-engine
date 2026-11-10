# glassbench: gli shader dell'engine, resi sul PC

Rende con Skia, su Windows e senza dispositivo, **le stesse sorgenti AGSL** che l'engine compila su
Android, e scrive PNG da mettere accanto ai frame di Apple. Serve al progetto «iOS in Android»: il
materiale si giudica qui molte volte, sul tablet poche.

Non e' un emulatore di Compose. Riproduce le passate del vetro (cattura, saturazione, sfocatura,
lente, pellicola, bordo, ombra) nell'ordine e con i numeri dell'engine, su una scena descritta in
JSON.

## Installare

Python 3.11. L'ambiente sta in `artifacts/liquid-glass/venv` (cartella ignorata da git):

```powershell
py -3.11 -m venv artifacts\liquid-glass\venv
artifacts\liquid-glass\venv\Scripts\python -m pip install -r tools\glassbench\requirements.txt
```

Provato con skia-python 144.1 (Skia m144), numpy 2.4, Pillow 13. Nessuna GPU: Skia rende in
software, quindi una scena a tutto schermo con uno shader pesante impiega da qualche secondo a una
decina. Niente resta acceso dopo il comando.

## Comandi

```powershell
$py = "artifacts\liquid-glass\venv\Scripts\python"

# compila ogni shader dell'engine e rilegge i ruoli: esce con 1 se qualcosa non compila
& $py tools\glassbench\glassbench.py check

# stampa le ottiche lette da fluid/GlassMaterial.kt
& $py tools\glassbench\glassbench.py roles

# rende una scena (di serie in artifacts\liquid-glass\bench\<nome>.png)
& $py tools\glassbench\glassbench.py render tools\glassbench\scenes\oggi-grigio.json
& $py tools\glassbench\glassbench.py render tools\glassbench\scenes\oggi-foto.json --theme dark
& $py tools\glassbench\glassbench.py render scena.json --stages          # anche le passate intermedie
& $py tools\glassbench\glassbench.py render scena.json --crop 96,99,172,72 --zoom 5   # ritaglio in dp

# affianca immagini alla stessa altezza, con un'etichetta sotto
& $py tools\glassbench\glassbench.py sheet a.png b.png frame_apple.jpg -o confronto.png --labels "oggi|nuovo|Apple"
```

`--theme` e `--density` forzano il valore scritto nella scena; con `--theme` il file prende il
suffisso (`oggi-foto-dark.png`).

## Da dove vengono shader e numeri

| cosa | da dove | come |
|---|---|---|
| shader del vetro e del bordo | `glass/backdrop/internal/Shaders.kt` | stringhe `"""..."""` estratte, con `$Nome` e `${Nome}` risolti come fa Kotlin |
| shader di Fluid-physics | `fluidphysics/FluidPhysicsShaders.kt` | idem, comprese le costanti (`$PhysicsMaxPieces`) |
| ottiche dei ruoli, regola della taglia, risoluzione della cattura | `fluid/GlassMaterial.kt` | `XxxOptics = GlassOptics(...)`, i `.copy(...)`, le `const val` |
| colore del bordo e dell'ombra, alfa delle pellicole, grigi del vetro | `fluid/GlassMaterial.kt` | **ricopiati** in testa a `render.py`, con il nome della funzione d'origine |
| shader nuovi | `tools/glassbench/shaders/*.agsl` | file a parte, letti cosi' come sono |

Quindi uno shader o un'ottica cambiati nell'engine cambiano il render senza toccare il banco. Le
pellicole (`TINT_ALPHA`) e la ricetta del bordo (`RIM`) no: se cambiano in `GlassMaterial.kt` vanno
ricopiate. La palette dell'app non esiste sul PC: di serie il banco usa quella di base di Material 3,
e una scena puo' dare la sua (`"palette"`).

## Descrivere una scena

Le misure sono in **dp**; `density` le porta in pixel. Esempio minimo:

```json
{
  "width": 480, "height": 270, "density": 3.0, "theme": "light",
  "background": {"kind": "gradient4", "tl": "#D0D0D0", "tr": "#5B6268", "bl": "#E1E5E8", "br": "#3E4347"},
  "elements": [
    {"role": "button",   "x": 30,  "y": 113, "w": 44,  "h": 44, "radius": "capsule", "icon": "plus"},
    {"role": "floating", "x": 104, "y": 107, "w": 156, "h": 56, "radius": "capsule", "icons": ["back", "plus", "circle"]}
  ]
}
```

**Sfondo** (`background.kind`):

- `color`: `{"color": "#RRGGBB"}`;
- `gradient4`: quattro colori agli angoli (`tl`, `tr`, `bl`, `br`), interpolati in sRGB;
- `image`: `{"path": "...", "crop": [x, y, w, h]}`, riempie la tela tenendo le proporzioni. Il
  percorso relativo parte dalla cartella della scena;
- `text`: testo che riempie la tela (`text` o `lines`, `fontSizeDp`, `lineHeightDp`, `paddingDp`,
  `color`, `background`).

Le scene `*-foto.json` puntano con un percorso assoluto a un frame del materiale di Alessio
(`Downloads\Fluid glass huge step up source material\...\frame_t0180s.jpg`): i frame di Apple non
stanno nel repo. Su un'altra macchina va cambiato il percorso.

`"page"` e' una lista di cose disegnate sopra lo sfondo **dentro la cattura** (`rect`, `circle`,
`text`, `icon`): e' il corpo della schermata, cio' che il vetro rifrange.

**Una lastra di `glassSurface`** (`kind: "glass"`, di serie):

| campo | significato |
|---|---|
| `x`, `y`, `w`, `h` | in dp |
| `radius` | `"capsule"`, un numero, o quattro (alto-sx, alto-dx, basso-dx, basso-sx) |
| `role` | `bar`, `floating`, `interactive`, `button`, `modal`, `content`, `stackedcontent`, `panel`, `compactpanel`, `sharppagepanel`, `compactsharppagepanel` |
| `optics` | al posto di `role`, per ritoccare: `{"role": "floating", "blurScale": 3, "dispersion": true}` (i campi di `GlassOptics`) |
| `tint` | `bar`, `floating`, `control`, `modal`, `panel`, `content`, `none`, o un colore `#RRGGBBAA` |
| `intensity`, `depth`, `pressed`, `quality` | 0..1: gli stessi parametri di `glassSurface` e di `FluidGlassQuality` |
| `backdropScale` | forza la risoluzione della cattura (di serie la calcola come l'engine) |
| `icon`, `icons`, `label`, `rows`, `items` | il contenuto stampato sopra |
| `children` | lastre dentro questa: campionano il suo materiale (vetro su vetro, 2.13.0) |

**Uno shader qualsiasi** (`kind: "shader"`): disegna uno shader a tutta area sul rettangolo dato.
E' la forma di `fluidPhysicsSurface` e di un materiale nuovo.

```json
{
  "kind": "shader",
  "source": "file:../shaders/sonda-materiale.agsl",
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
- `inputs`: un ingresso per ogni `uniform shader`. Ognuno e' il fondale sotto il rettangolo, con
  saturazione e sfocatura facoltative (`blurDp`); `edge` e' `decal` (trasparente fuori) o `clamp`;
- `uniforms`: numeri cosi' come sono, `"12dp"` moltiplicato per la densita', `"#RRGGBBAA"` un colore
  non premoltiplicato, `"$size"`, `"$center"`, `"$halfSize"`, `"$density"`, `"$dark"` valori del
  banco. Le liste si appiattiscono; un array non pieno si completa con zeri;
- `scale`: rende lo shader a una frazione della risoluzione e lo riporta in scala.

## Aggiungere uno shader nuovo

1. Scrivi `tools/glassbench/shaders/nome.agsl`. Solo AGSL di Android 13: cicli `for` a limite
   costante, indici di array costanti o la variabile del ciclo, niente `dFdx`/`fwidth`, niente
   `while`, lo sfondo solo con `ingresso.eval(coord)`.
2. Mettilo in una scena come elemento `kind: "shader"`, con i suoi `inputs` e `uniforms`.
3. `glassbench.py check` lo compila; `render` lo disegna.
4. Quando entra nell'engine, **la sorgente e' una sola**: o il Kotlin la carica dal file, o il banco
   la legge dal Kotlin con `kotlin:Nome`. Non tenere due copie.

`shaders/sonda-materiale.agsl` e' un esempio completo (campo a N pezzi, due ingressi, lente, luce,
ombra): e' una **sonda di fattibilita'**, non il materiale.

## Cosa riproduce fedelmente

- **Gli shader**: sono le stringhe dell'engine, compilate dallo stesso compilatore SkSL che Android
  usa per AGSL. Lente, dispersione, bordo speculare, campo fuso di Fluid-physics.
- **L'ordine delle passate** di `glassSurface`: ombra sotto e scavata dentro la sagoma; cattura;
  saturazione, poi sfocatura, poi lente; ritaglio; pellicola; contenuto; bordo sopra, nel suo layer,
  con il suo modo di comporre (`Plus` sullo scuro, `SrcOver` sul chiaro).
- **I numeri**: ottiche dei ruoli, regola della taglia, margine della cattura (`padding`),
  risoluzione ridotta della cattura (la catena gira sui pixel ridotti e si riporta in scala, come su
  HWUI, che applica il `RenderEffect` all'immagine del layer e non allo schermo).
- **I raggi di sfocatura**: `sigma = 0,57735 * raggio + 0,5`, il conto di HWUI sia per
  `RenderEffect.createBlurEffect` sia per `BlurMaskFilter`.
- **La premoltiplicazione**: superfici e ingressi premoltiplicati, come su Android; gli uniform
  `layout(color)` ricevono colori non premoltiplicati.
- **Vetro su vetro**: una lastra figlia campiona pagina + materiale del pannello, senza il suo
  contenuto.

Verifica fatta: `scenes/confronto-reale-scuro.json` accanto ai tasti veri di
`artifacts/migrazione/codex/dopo/08-chats-scuro.png` (stessa densita', palette stimata): stesso
riempimento, stesso filo chiaro pieno sopra e sotto e spento sui fianchi.

## Cosa non riproduce

- **La sfocatura non e' identica al pixel.** Skia in software approssima la gaussiana con tre
  passate di media per i sigma grandi; la GPU di Android usa una gaussiana vera, ridotta di scala.
  La differenza e' sotto la soglia di cio' che si giudica a occhio, ma un confronto pixel per pixel
  con uno screenshot non torna a zero.
- **Lo spazio colore.** Il banco lavora in sRGB a 8 bit, senza linearizzare (come HWUI su uno
  schermo sRGB). Su un pannello P3 HWUI converte i colori, e le superfici intermedie possono essere
  a 16 bit: saturazione e `Plus` sui colori molto vivi possono differire.
- **La precisione.** In software `half` e `float` sono entrambi a 32 bit. Su alcune GPU `half` e' a
  16 bit: un difetto di precisione (bande, coordinate grandi) qui non si vede.
- **Il campionamento.** Gli ingressi sono campionati in modo lineare e trasparenti fuori dal bordo,
  come fa `RenderEffect.createRuntimeShaderEffect`; il ritaglio antialias di Skia e quello hardware
  del `RenderNode` differiscono di frazioni di pixel.
- **Il tempo.** Niente animazioni, niente costo per fotogramma. I tempi del banco sono di CPU e non
  dicono nulla della GPU del telefono.
- **Cio' che l'engine disegna fuori dagli shader**: `glassMask` e `GlassFalloff.FadeDown`, l'ombra
  interna (oggi a zero in tutti i ruoli), il filetto (`hairline`), il bordo e l'ombra a path di
  `fluidPhysicsSurface`, il bagliore al tocco, il contrasto di sistema (`forSystemContrast`). Un
  elemento `kind: "shader"` rende lo shader e basta.
- **Il carattere.** Le etichette usano Segoe UI, non il font dell'app; le icone sono disegni di
  servizio.
- **Il ripiego sotto Android 13.** Il banco rende solo il materiale con AGSL.

## Differenze fra SkSL di Skia e AGSL incontrate

- Nessuna di sintassi: tutte le sorgenti dell'engine che compilano qui sono AGSL valido. Il banco
  non fa nessuna traduzione.
- **`flat` e' una parola riservata.** `PolyMorphRefractionShaderString` e
  `PolyMorphDispersionShaderString` dichiarano `half4 flat = ...` (blocco `PhysicsDropBandBlock`,
  dalla 3.0) e **non compilano**: `error: expected ';', but found 'flat'`. La parola e' riservata nel
  lessico di SkSL (insieme a `noperspective`), che e' lo stesso su Android; rinominata la variabile,
  compilano. `check` lo segnala finche' non viene corretto nell'engine. Sul dispositivo si verifica
  con `adb logcat -s FluidPhysics`.
- Skia m144 e' piu' nuovo dello Skia di Android 13: una funzione di SkSL aggiunta dopo potrebbe
  compilare qui e non la'. Vale la regola del punto 1 di «Aggiungere uno shader nuovo».
- `RuntimeShaderBuilder.setUniform` di skia-python non accetta gli array: il banco impacchetta gli
  uniform a mano (`Program` in `render.py`), nell'ordine di dichiarazione.
- `radiusAt(coord, cornerRadii)` negli shader di Kyant riceve la coordinata del layer, non quella
  centrata: per una sagoma con angoli diversi la lente e il bordo usano sempre il raggio in basso a
  destra. Il banco lo riproduce, perche' esegue la stessa sorgente.

## File

```
glassbench.py        comandi
kotlin_sources.py    estrazione di shader e ottiche dai sorgenti Kotlin
render.py            le passate
scenes/              oggi-*.json (il vetro di oggi), confronto-reale-scuro.json,
                     fluidphysics-gruppo-oggi.json, sonda-*.json
shaders/             sonda-materiale.agsl
```
