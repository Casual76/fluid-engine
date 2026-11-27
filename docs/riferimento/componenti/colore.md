<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/colore.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Colore: il selettore e il colore preso dal contenuto

Due elementi della 4.0 (F8.7) che parlano di colore. Il **selettore** fa scegliere un colore all'utente: un pozzetto tondo
che apre un foglio di vetro con tre modi, l'opacità, i recenti e il contagocce, più una fila corta di campioni per una
riga delle impostazioni. Il **colore dal contenuto** fa l'opposto: legge i colori di un'immagine (una copertina, una foto)
e tinge la sola pagina che la mostra, coi colori corretti per restare leggibili in chiaro e in scuro.

I riferimenti sono `ColorPicker` di SwiftUI e `UIColorPickerViewController` di UIKit (iOS 14) per il selettore, e la
pagina del brano di Apple Music per la pagina tinta (fiducia media: il comportamento si vede, l'API pubblica non c'è).

Stanno in `engine-ui`. Il selettore è in `dev.antigravity.fluidengine.ui.fluid` (`FluidColorPicker.kt`), accanto a
`FluidColorDot` e `FluidAccentPicker` ([controlli.md](controlli.md), [impostazioni.md](impostazioni.md)); il colore dal
contenuto è in `dev.antigravity.fluidengine.ui.theme` (`FluidContentColor.kt`, `FluidContentTheme.kt`), accanto a
`FluidTheme` ([tema.md](tema.md)). Nessuna libreria nuova. Catalogo delle firme: [../api/controlli.md](../api/controlli.md)
e [../api/tema.md](../api/tema.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un colore scelto dall'utente in una riga delle impostazioni («Sfondo», «Colore della cartella») | `FluidColorRow(title, color, onColorChange)`: la riga col pozzetto a destra, come `ColorPicker` in un `Form` |
| il pozzetto da solo, in un posto mio | `FluidColorWell(color, onColorChange, label)` |
| pochi colori proposti, più «altro» | `FluidColorSwatches(colors, selected, onSelect)`: la fila corta, coi pallini di `FluidAccentPicker` e il pozzetto in fondo |
| il foglio aperto da un mio tasto | `FluidColorPickerSheet(visible, color, onColorChange, onDismissRequest, source = …)` |
| il selettore dentro una pagina o un popover (senza foglio) | `FluidColorPicker(color, onColorChange)` |
| prendere un colore dalla finestra dell'app | `FluidColorEyedropper(active, onPick)` (il foglio lo fa da sé) |
| i colori recenti che durano fra una volta e l'altra | `rememberFluidRecentColors(initial = salvati, onChange = { salva(it) })` |
| un colore scritto o letto come `#RRGGBB` | `fluidColorHex(color)`, `fluidParseHexColor(testo)` |
| un colore detto a parole (TalkBack) | `fluidColorName(color)` («blu scuro», «rosso, opacità 50 %») |
| i colori di una copertina | `rememberFluidContentColors(key) { decodifica piccola }` (fuori dal thread principale, con la cache) |
| tingere la pagina coi colori del suo contenuto | `FluidContentTheme(colors) { pagina }` |
| un vetro mio (un mini player) del colore della pagina | `LocalFluidContentRoles.current?.barTint()` |
| i ruoli col contrasto verificato, fuori dalla composizione | `fluidContentRoles(colors, isDark)` |
| **non così**: una griglia di `Box` cliccabili fatta a mano | il foglio ha già griglia, spettro, cursori, l'opacità e i nomi per TalkBack |
| **non così**: `androidx.palette` per la tinta della pagina | l'estrattore in casa basta (è quello di Pampa Widgets) e la cache c'è |

## Il selettore

### Il pozzetto e la riga

`FluidColorWell` è un cerchio di 28 dp col colore dentro l'anello dell'arcobaleno (3 dp, 2 dp di vuoto), su un bersaglio
di 44 dp: è `UIColorWell`. Toccato apre il foglio, che **nasce dal pozzetto** e ci rientra (`FluidTransitionSource` sul
cerchio: il viaggio del foglio di vetro, «Il foglio che nasce dal tasto» in [menu-modali.md](menu-modali.md)). Per
TalkBack dice l'etichetta e il nome del colore. `FluidColorRow` è la stessa cosa dentro un `FluidListRow`: tutta la riga
si tocca, il pozzetto sta a destra (nello slot `badge`), lo stato per TalkBack è il nome del colore.

```kotlin
val recenti = rememberFluidRecentColors(initial = store.recenti, onChange = { store.salvaRecenti(it) })
FluidListGroup {
  FluidColorRow(title = "Sfondo", color = sfondo, onColorChange = { sfondo = it }, recents = recenti)
  FluidListDivider()
  FluidColorRow(title = "Testo", color = testo, onColorChange = { testo = it }, supportsOpacity = false, recents = recenti)
}
```

### Il foglio

`FluidColorPickerSheet` è `FluidGlassSheet` (serve l'host dei modali) con due altezze: 640 dp, dove il foglio è vetro
staccato dai bordi, e tutta altezza, dove diventa pagina come ogni foglio di vetro. Dentro, `FluidColorPicker`:

- **la testata**: il contagocce a sinistra, «Colori» al centro, Chiudi a destra (tasti di vetro), come iOS;
- **i tre modi** in un `FluidSegmentedControl`: **Griglia** (12 × 10, la prima riga di grigi dal bianco al nero, poi
  nove righe dallo scuro al chiaro coi colori della griglia di iOS: `FluidColorGrid`), **Spettro** (la tinta da
  sinistra a destra, il bianco in alto, il pieno a metà, il nero in basso; il segno ha il colore scelto, anche quando un
  colore smorzato dai cursori non sta sullo spettro, R2) e **Cursori** (rosso, verde, blu da 0 a 255 col numero, e il
  codice esadecimale da scrivere: sei cifre tengono l'opacità di adesso, otto, `RRGGBBAA` incollate da un'altra app,
  portano anche l'opacità se `supportsOpacity`, R2);
- **l'opacità**, se `supportsOpacity`: un cursore su una scacchiera, col valore in percentuale;
- **il colore scelto** (un quadrato di 74 dp) e **i recenti** (pallini di 28 dp che vanno a capo), col «+» in fondo che
  salva il colore di adesso, come iOS 26.

Le misure che si vedono (il foglio a riposo alto ~640 punti, la griglia a caselle di ~30 punti con gli angoli di ~15, il
quadrato di 74 punti, i pallini di 28, il valore in una capsula accanto al cursore, l'etichetta dell'esadecimale a
sinistra del campo) sono prese da una schermata di iOS 26 e da una di iOS 18 (`FluidColorPickerDefaults`, con la fonte nel
KDoc).

Il dito sceglie dal primo contatto: sulla griglia la casella sotto il dito e quelle che attraversa, sullo spettro il
punto, sui cursori il valore. **Niente si sfoca** («dito nitido, app sfocata»: è tutto mosso dal dito). Il colore
scelto entra nei recenti quando il foglio si chiude, se è cambiato. Il valore resta in sRGB anche sugli schermi a
colori estesi.

### Il contagocce

Toccato, il foglio esce; dopo 420 ms (60 col movimento ridotto) la finestra dell'app si fotografa con `PixelCopy` e
compare la **lente** al centro: 120 dp, 11 × 11 pixel ingranditi con la casella di mezzo segnata e un anello del colore
sotto. Il dito la porta (relativo: la lente non sta sotto il dito); al rilascio il colore del pixel nel mezzo è scelto,
con l'opacità di prima, e il foglio torna. Indietro annulla. La lente sta in un `Popup`, una finestra sua, così la
fotografia non la contiene.

**Il limite**: vede solo la finestra dell'app, non tutto lo schermo come su iOS (servirebbe la registrazione dello
schermo). Una finestra con `FLAG_SECURE` non si lascia fotografare: il contagocce restituisce null.

### I recenti

`FluidRecentColors` tiene l'ultimo davanti, senza doppioni, al massimo 10 di serie. **Li conserva l'app**: `onChange`
riceve la lista nuova (per lo store che ha, anche `engine-storage`), `initial` la riporta; `rememberFluidRecentColors`
li tiene attraverso i cambi di configurazione. Più pozzetti della stessa pagina condividono la lista se l'app passa lo
stesso oggetto.

### La fila corta

`FluidColorSwatches` è una fila di `FluidColorDot` (30 dp, bersaglio 48, ruolo `RadioButton`, il nome a parole) che
scorre di lato se non ci sta, con un `FluidColorWell` in fondo (`customLabel`, «Altro colore»; null lo toglie) che mostra
il colore scelto quando non è fra i campioni. È la forma per «il colore di una materia» dentro una riga.

## Il colore preso dal contenuto

### L'estrazione

`fluidContentColorsOf(bitmap)` (o `IntArray` di pixel, puro) restituisce `FluidContentColors`: `seed` (il colore per il
tema), `dominant` (la media), `vibrant` (il colore vivo), `swatches` (da uno a tre colori distinti per tinta, il primo è
il seme) e `monochrome`. È l'estrattore di Pampa Widgets: l'immagine ridotta a 96 px, un pixel ogni due, l'alfa sotto
160 scartata, il colore vivo pesato per saturazione al quadrato e luminosità. Una differenza: la media pesata si fa sulla
fascia di tinta più pesante e sulle due vicine, perché su una copertina a tre colori la media di tutti era un grigio
fangoso. Un'immagine tutta trasparente dà null (il tema resta normale); una con meno del 4 % di pixel colorati è
monocroma, e i ruoli restano neutri. **Un colore pieno è colorato anche a luminosità piena** (R2): il bianco si riconosce
dalla saturazione, e sopra 0,94 di luminosità un pixel conta fra i colorati se è saturo almeno 0,5 (a occhio). Prima una
copertina gialla o rossa pura (canale a 255) era «monocroma» e la pagina restava neutra proprio sulle copertine più
colorate.

**Il colore vivo** (`vivid`, F8.7b). Accanto a `vibrant` l'estrattore tiene la fascia di tinta più viva che pesa davvero:
quella col peso per la croma OKLCH al quadrato più alto (`FluidContentApple.SeedChromaPower`), se pesa almeno il 15 %
della massa colorata, è un altro colore (almeno 20° di tinta) ed è più viva (almeno il 15 % di croma in più). È il seme
del lato della copertina. Il fiore della galleria, con foglie oliva, verdi e bruno-rosse, dava la media delle tre, un
oliva spento; ora dà il verde delle foglie, e la pagina parte da 0,46 di luminosità invece di scendere fino a un verde
quasi nero (`VividPageL`). Le copertine di una tinta sola (il razzo, la copertina chiara, la tuta arancione) non
cambiano di un livello: lo provano le copertine vere della galleria (`F8p7ColorUiTest`).

`rememberFluidContentColors(key) { … }` la fa una volta per chiave su `Dispatchers.Default`, con una cache di 64 voci
(`FluidContentColorCache`, come Fluidify): la stessa copertina la chiedono il mini player, la pagina e il widget. Mentre
i colori di una chiave nuova si leggono restano quelli di prima, così fra un brano e l'altro la pagina non torna bianca.
**L'immagine la decodifica l'app**, piccola: 96 px bastano. `rememberFluidContentColors(image)` senza chiave usa
l'immagine come chiave **tenuta in modo debole** (R2): la cache tiene i colori, non le immagini, e un'immagine lasciata
andare non resta appesa fra le 64 voci. Una chiave data (l'indirizzo della copertina) resta com'è.

### I ruoli

`fluidContentRoles(colors, isDark, side)` dà `FluidContentRoles`: `pageTint` e `onPage`/`onPageVariant` (la pagina e il suo
testo), `barGlass` e `onBar` (la pellicola di un vetro di barra e il suo inchiostro), `accent` e `onAccent` (l'accento
locale e il testo sul suo pieno). **Ogni coppia fa almeno 4,5:1**, provato su tutta la ruota dei colori, sui grigi e sui
neon; anche l'accento sulla pagina, perché si usa come testo. Si lavora in OKLCH: tinta e croma vengono dal seme (la
croma con un tetto, così i colori «tossici» si fermano), la luminosità da una quota per lato, e ogni inchiostro si sposta
del minimo che serve per arrivare a 4,5:1. **Il lato del tema non si ribalta**: nel tema chiaro la pagina è una tinta
chiara col testo scuro, nel tema scuro il contrario, così il vetro, le barre di sistema e i controlli restano dalla loro
parte.

**Da che parte sta la pagina** (`FluidContentSide`). Di serie `Content`, **come Apple Music** (la pagina dell'album e
della playlist di iOS 26.4, misurata sulle immagini di Apple): la pagina prende il colore della copertina, stessa tinta,
un po' meno saturo e medio-scuro, col **testo bianco**, in chiaro e in scuro; solo una copertina chiarissima e gialla
resta chiara col testo nero (scurirla darebbe un verde fango). L'accento è una tinta chiara della copertina, come il tasto
Riproduci bianco col testo del colore della pagina. Con `Theme` la pagina resta dalla parte del tema: una tinta chiara
nel tema chiaro, scura nel tema scuro (le quote OKLCH di prima). `fluidContentRoles(colors, isDark)` senza lato è
`Theme`.

### Il tema della pagina

```kotlin
val colori by rememberFluidContentColors(key = brano.copertinaUrl) { caricaPiccola(brano.copertinaUrl) }
FluidContentTheme(colors = colori, enabled = impostazioni.tingiPagina) {
  FluidScreen(title = brano.album, onBack = onBack) { … }
}
```

Dentro, `MaterialTheme.colorScheme` ha lo sfondo, le superfici, il testo e i contorni presi dalla pagina (`page`) e
`primary` dall'accento (`accent`): righe, card, tasti e il velo dei vetri seguono da soli. Col lato `Content` anche
`LocalFluidSurfaceSide` è quello della pagina (dalla metà del passaggio): su una pagina scura in un'app chiara il vetro,
i controlli e la barra girano scuri, e il bordo della barra di `FluidScreen`, che legge col sondino quello che ha sotto,
chiede da sé le icone di stato chiare. I ruoli si leggono da
`LocalFluidContentRoles` (null a tema normale), per i pezzi che l'app disegna: un vetro di barra del colore della pagina
con `barTint()` (tinta di Apple, `GlassTintMode.Colored`), un'aura.

**Il passaggio sfuma**: entrare, cambiare tinta e uscire (`colors` null o `enabled` falso) durano 450 ms sulla curva dei
colori, ripartendo da dove si è se arriva un cambio a metà. È un cambio guidato dall'app, quindi sfuma; col movimento
ridotto è immediato. L'albero resta lo stesso, tinto o no: accendere o spegnere la tinta non ricompone la pagina da capo.
**I 4,5:1 valgono ai due capi** (R2): fra una pagina chiara e una scura fondo e testo si mescolano ognuno per conto suo,
come in una dissolvenza, e a metà passaggio il testo per qualche fotogramma non si legge (contrasto vicino a 1).

**L'app sceglie** l'immagine, le superfici (`page`, `accent`, i suoi vetri) e quando: il tema va attorno alla pagina, non
all'app. Ferma non costa niente; durante il passaggio lo schema cambia a ogni fotogramma per mezzo secondo.

## Trappole

- **Il foglio vuole l'host dei modali** (`FluidGlassModalHost`): senza, non si apre (non va in crash), come ogni foglio di
  vetro.
- **I recenti di un pozzetto senza `recents`** vivono solo in quel pozzetto: per condividerli o salvarli passa lo stesso
  `rememberFluidRecentColors(…)`.
- **Il contagocce prende il colore com'è disegnato**: sopra un vetro prende il vetro, non quello che c'è sotto.
- **`FluidContentTheme` attorno a tutta l'app** tingerebbe ogni pagina: va attorno alla pagina che mostra l'immagine.
- **Un'immagine `HARDWARE`** si copia in memoria prima di leggerla: decodificarla piccola e software costa meno.

## Token, costanti, Defaults

`FluidColorPickerDefaults` ha le misure del selettore (pozzetto, griglia, cursori, foglio, lente, ritardo del
contagocce), ognuna col KDoc che dice la fonte o «a occhio» con la data. `FluidContentColorDefaults` ha quelle
dell'estrattore (96 px, passo 2, alfa 160, 4 % per il monocromo, 64 voci di cache) e la durata del passaggio (450 ms).
Le quote dei ruoli in OKLCH sono interne (`FluidContentRecipe`): si cambiano in un posto solo.

## Cosa è verificato dai test

- `F8p7ColorPickerMathTest` (engine-ui): l'esadecimale scritto e riletto, nelle forme da 3, 6 e 8 cifre; i nomi dei
  colori; lo spettro che va e torna; la griglia di iOS; i recenti.
- `F8p7ContentColorTest` (engine-ui): l'estrattore su immagini monocrome, trasparenti, molto scure, molto chiare e
  colorate; i ruoli a 4,5:1 in chiaro e in scuro su più di 500 semi, dalle due parti; l'azzurro chiaro di Apple Music
  che diventa azzurro medio col testo bianco e il giallo che resta chiaro col testo nero; lo schema tinto; OKLCH; la cache.
- `F8p7ColorUiTest` (sample, Robolectric col vetro vero): i tre modi, la casella sotto il dito, l'esadecimale letto e
  scritto con l'opacità che resta, i recenti col «+» e alla chiusura del foglio aperto dalla riga, il contagocce che
  prende il pixel giusto della finestra (PixelCopy vero), il tema che entra, cambia tinta ed esce, e subito col
  movimento ridotto.

## Dove guardare

| file | cosa |
|---|---|
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/FluidColorPicker.kt` | pozzetto, riga, fila corta, foglio, selettore, contagocce, recenti, esadecimale, nomi, griglia |
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/theme/FluidContentColor.kt` | `FluidContentColors`, l'estrattore, la cache, `rememberFluidContentColors` |
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/theme/FluidContentTheme.kt` | `FluidContentRoles`, `fluidContentRoles`, `FluidContentTheme`, `LocalFluidContentRoles` |
| `sample/src/main/kotlin/dev/antigravity/fluidengine/sample/novita/F8p7Scenes.kt` | le scene «Elementi nuovi»: il widget di Pampa Widgets, la materia di ClasseViva, la pagina del brano di Fluidify |
