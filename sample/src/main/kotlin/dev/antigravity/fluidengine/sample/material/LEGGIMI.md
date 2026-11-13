# La pagina «Materiale»

Pacchetto F1A.9 del piano «iOS in Android» (`docs/liquid-glass/piano.md`, sezione F1A). Il materiale nuovo
(`Liquid`) sul pannello vero è stato visto su **un solo dispositivo, un Tab S9**, in due sessioni e dopo il giro di
correzione di F1A: S1-ter (`artifacts/liquid-glass/sessioni/S1-ter.md`), con la combinazione di prima delle scelte di
gusto; S2-pre (`sessioni/S2-pre.md`), con il profilo di serie (le scelte di Alessio, F1A.5c) e con la regola del vetro
annidato, che nella notifica era un anello vuoto; poi il giro di correzione (`sessioni/F1A-correzione/`), che ha chiuso
l'annidato sul tablet. Lo stato di ogni voce (visto, visto in parte, mai visto) e **chi la guarda** (una sessione o una
fase del piano) sono nella tabella «Cosa si è visto e chi guarda il resto», prima della mappa. Questa pagina mette le
voci davanti alle sessioni sul tablet (S2 e le successive, piano 4.5) voce per voce, **accanto al vetro di prima**
(`Classic`), sui sei fondi di 4.6, fermo o con il fondo che scorre, e scrive in logcat (tag `FluidMateriale`) ciò che
dice la diagnostica dell'engine. Si guida tutta da `adb`, senza toccare lo schermo; a mano si apre dal catalogo di
Chrome (riga «Materiale») o dalle Impostazioni della galleria (sezione «Vetro (solo debug)»).

Chi guarda le foto e i video è Alessio, in chat: la sessione porta collage e ritagli, non giudizi a parole.

**Regole del tablet** (brief degli implementatori): mai il tasto di accensione (`keyevent 26`, `KEYCODE_POWER`,
`KEYCODE_SLEEP`), niente che spenga o blocchi lo schermo; in secondo piano si va con `keyevent 3` (Home). Alla fine
`wm size reset` e `wm density reset` se si è passati al formato telefono.

## Prima di cominciare

Da Git Bash, nella radice del worktree:

```bash
export ANDROID_SERIAL=R52X50DKRBW          # il Tab S9 (prima di qa-common.sh, che altrimenti punta all'emulatore)
source tools/qa/qa-common.sh               # $ADB, $FFMPEG, qa_record, qa_finish, qa_shot, MSYS_NO_PATHCONV
adb() { "$ADB" "$@"; }                     # adb non è nel PATH
PKG=dev.antigravity.fluidglass.debug
ACT=dev.antigravity.fluidengine.sample.GalleryActivity
PY=artifacts/liquid-glass/venv/Scripts/python.exe
OUT=artifacts/liquid-glass/s2/materiale     # foto, video, ritagli (artifacts/ non è tracciato)
mkdir -p "$OUT"

adb shell dumpsys window | grep isKeyguardShowing     # deve dire false: altrimenti fermarsi e chiamare Alessio
adb install -r -t -d sample/build/outputs/apk/debug/sample-debug.apk
adb install -r -t -d sample/build/outputs/apk/androidTest/debug/sample-debug-androidTest.apk
```

Gli APK escono da `:sample:assembleDebug :sample:assembleDebugAndroidTest`, con lo script del posto Gradle
(`artifacts\liquid-glass\tools\build-gradle.ps1`), prima della sessione.

Le funzioni della sessione:

```bash
# materiale <scena> [chiave valore]...   lancia la scena, aspetta PRONTO, stampa le righe della pagina.
# $GALLERIA (facoltativa) porta gli interruttori di tutta la galleria e il tema:
#   GALLERIA="--es gallery.model classic --es gallery.theme dark" materiale components
materiale() {
  local scene="$1"; shift
  local extras=""
  while [ $# -gt 1 ]; do extras="$extras --es material.$1 $2"; shift 2; done
  adb logcat -c
  adb shell am start -S -W -n "$PKG/$ACT" --es gallery.page material --es material.scene "$scene" $extras $GALLERIA >/dev/null
  for _ in $(seq 1 30); do
    adb logcat -d -s FluidMateriale:I | grep -q "FluidMateriale: PRONTO" && break
    sleep 1
  done
  adb logcat -d -s FluidMateriale:I | sed 's/^.*FluidMateriale: //' | grep -v '^RIQUADRO'
}

# riquadro <nome> [modello]   «x y w h» dell'elemento in pixel dello schermo, dall'ultima riga RIQUADRO
riquadro() {
  adb logcat -d -s FluidMateriale:I | grep "RIQUADRO" | grep " modello=${2:-liquid} nome=$1 " | tail -n 1 \
    | sed -E 's/.* x=(-?[0-9]+) y=(-?[0-9]+) w=([0-9]+) h=([0-9]+).*/\1 \2 \3 \4/'
}

# ritaglio <png> <x> <y> <w> <h> <uscita.png> [fattore]   un ritaglio ingrandito senza filtro (6x di serie)
ritaglio() {
  "$PY" -I -c 'import sys
from PIL import Image
a = sys.argv
im = Image.open(a[1]); x, y, w, h = map(int, a[2:6]); k = int(a[7]) if len(a) > 7 else 6
im.crop((x, y, x + w, y + h)).resize((w * k, h * k), Image.NEAREST).save(a[6])' "$@"
}

# ritagli <foto> <nomi...>   ogni elemento, nelle due colonne, con 8 px di margine, a 6x
ritagli() {
  local shot="$1"; shift
  for m in classic liquid; do
    for n in "$@"; do
      read -r x y w h < <(riquadro "$n" "$m")
      [ -n "$h" ] && ritaglio "$OUT/$shot.png" $((x - 8)) $((y - 8)) $((w + 16)) $((h + 16)) "$OUT/$shot-$m-$n-x6.png"
    done
  done
}

# angoli <foto> <nome> [lato]   l'angolo in alto a sinistra e quello in basso a destra, nelle due colonne, a 6x
angoli() {
  local shot="$1" n="$2" s="${3:-120}"
  for m in classic liquid; do
    read -r x y w h < <(riquadro "$n" "$m")
    [ -n "$h" ] || continue
    ritaglio "$OUT/$shot.png" "$x" "$y" "$s" "$s" "$OUT/$shot-$m-$n-alto-sx-x6.png"
    ritaglio "$OUT/$shot.png" $((x + w - s)) $((y + h - s)) "$s" "$s" "$OUT/$shot-$m-$n-basso-dx-x6.png"
  done
}

# stato   l'ultima riga di stato della diagnostica
stato() { adb logcat -d -s FluidMateriale:I | grep "STATO" | tail -n 1 | sed 's/^.*FluidMateriale: //'; }
```

Foto: `qa_shot "$OUT" nome` (`screencap` sul dispositivo e `pull`: mai `exec-out screencap > file` da PowerShell).
Video e fotogrammi: `qa_record nome secondi` e poi `qa_finish nome "$OUT"` (fotogrammi in ordine di presentazione,
`-fps_mode passthrough`, e la luminanza per fotogramma in `nome_luma.txt`).

## Come si lancia

La pagina è la voce `Material` di `GalleryPage`: `--es gallery.page material`. La scena si sceglie con
`material.scene`; senza, compare l'elenco delle scene (con gli interruttori in cima). Gli extra si leggono solo nella
build di debug; un valore che non si riconosce si ignora e resta il default, e la riga `AVVIO` dice con che cosa la
scena è partita davvero. `-S` ferma l'app prima: senza, un secondo lancio riporterebbe davanti la scena di prima.

A mano: il riquadro di stato in alto si tocca e apre il pannello delle scelte (scena, fondo, movimento, vetri, ciclo,
vetro della radice, Android simulato, riscaldamento, manopole a varianti); con `hud off` lo apre l'angolo in alto a
destra, che non disegna niente. «Indietro» riporta alla scena degli extra o all'elenco.

### Gli extra della pagina

Tutti `--es material.<nome> <valore>` (con `materiale`: `materiale <scena> <nome> <valore> ...`).

| extra | valori | default | che cosa fa |
|---|---|---|---|
| `material.scene` | `components` `edge` `corner` `nested` `siblings` `press` `intensity` `list` `menu` `modal` `tints` `apps` `contrast` `rtl` | (elenco) | la scena: vedi «Le scene» |
| `material.backdrop` | `page` `page-light` `page-dark` `white` `black` `text` `photo` | `page` (`nested`, `siblings`, `press`, `menu`, `modal`: `text`; `apps`, `tints`: `photo`) | il fondo sotto le lastre |
| `material.motion` | `still` `scroll` | `still` | il fondo fermo, o che scorre (ogni lastra rifà la cattura a ogni fotogramma) |
| `material.speed` | dp al secondo | `240` | con `scroll` |
| `material.model` | `both` `liquid` `classic` | `both` | i vetri della pagina: i due affiancati (uno sopra l'altro in formato telefono), o uno solo a tutta pagina |
| `material.cycle` | `cycle` `hold` `off` | `cycle` | le scene che si muovono da sole (`nested`, `siblings`, `press`, `intensity`, `list`, `menu`, `modal`): in ciclo; ferme nello stato «acceso» per le foto (dito giù, menu aperto, foglio aperto, `intensity` 1, lista a metà); ferme del tutto |
| `material.period` | secondi | `3` (`list`, `menu`, `modal`: `4`) | la durata di un giro |
| `material.intensity` | `0`…`1`, `cycle` | `cycle` | `intensity` fissa nella scena `intensity` |
| `material.contrast` | `standard` `medium` `high`, `0`…`1`, `all` | `all` | un livello solo nella scena `contrast` |
| `material.hud` | `on` `off` | `on` | il riquadro di stato: **`off` per le foto** |
| `material.fingers` | `1` `2` | `1` | quante dita finte nella scena `press`: con `2` il tondo e la capsula della colonna scendono insieme e scorrono in versi opposti (due bagliori nello stesso momento; con `cycle hold`, sulla colonna `Liquid`) |
| `material.sheet` | `glass` `opaque` | `glass` | il foglio della scena `modal`: sul palco (di vetro) o **senza fondo** (l'host dei modali senza `backdrop`: il foglio pieno, coi suoi tasti di vetro sopra) |

I sei fondi di 4.6: `page-light` (`#F2F2F7`) e `page-dark` (`#0D0D0F`), le pagine vere dell'engine; `white`, `black`;
`text` (la frase del banco, nell'inchiostro del tema sulla pagina del tema); `photo` (la «foto» generata in codice di
«Misure»: dune, cielo saturo, macchia rossa; sempre uguale). `page` è la pagina del tema in uso
(`colorScheme.background`): nel tema chiaro quasi `#F2F2F7`, nello scuro quasi `#0D0D0F`.

### Gli interruttori di tutta la galleria

Valgono per **ogni** pagina, non solo per questa (piano 4.5, S2: «`Classic` forzato» e «livello simulato» su tutta la
galleria). Si leggono all'avvio, prima del contenuto, solo nella build di debug; a mano stanno nelle Impostazioni
(«Vetro (solo debug)») e nel pannello di questa pagina. Con `materiale` si passano in `$GALLERIA`.

| extra | valori | che cosa fa |
|---|---|---|
| `gallery.model` | `classic` `liquid` | il vetro chiesto alla radice (`LocalFluidGlassModel`). Le colonne di questa pagina chiedono il loro; la barra delle schede, i modali e le altre pagine prendono questo |
| `gallery.api` | `32` `30` (un livello sotto quello vero, da 26) · `off` | `FluidGlassDiagnostics.simulateApiLevel`: l'engine risponde come su Android 12 (`RenderEffect` sì, AGSL no) o 11 (né l'uno né l'altro). A mano ricompone tutta la galleria |
| `gallery.warmup` | `on` `off` | `FluidGlassDiagnostics.warmUpEnabled`: il riscaldamento dei programmi dopo il primo fotogramma |
| `gallery.tune` | `nome=valore,nome=valore` · `off` | le manopole del profilo del materiale nuovo (sotto). Valgono per le lastre che nascono dopo; a mano ricompongono tutta la galleria |
| `gallery.theme` | `light` `dark` `black` | il tema (c'era già) |

Una riga in logcat dice che cosa è stato preso: `GALLERIA model=classic api=32 applicato=si warmup=off` e
`PROFILO chiesto=… preso=… scartato=…`. Un `applicato=no` vuol dire che l'engine ha rifiutato il livello (pari o sopra
quello vero, o sotto 26).

### Le manopole del profilo (`gallery.tune`)

I numeri del profilo (`ui/glass/field/GlassLiquidProfile.kt`) che le tavole di gusto di F1A mettono a confronto,
cambiati dal vivo per fotografarli sul pannello. Solo nella build di debug (`FluidGlassDiagnostics.tuneLiquidProfile`):
senza manopole l'engine disegna il profilo di serie, identico al banco (`compare --golden liquid`, 38 immagini su 38).
Le stesse forme rese dalle strade diverse del materiale, fianco a fianco, sono nella pagina «Parità» (`parity/LEGGIMI.md`). Nomi e
valori senza distinzione di maiuscole; i numeri col punto. Prima le varianti, poi i numeri (un numero cambia il pezzo
che la variante ha scelto). Una manopola sconosciuta si scarta e la riga `PROFILO` lo dice.

In grassetto ciò che è di serie (le scelte di Alessio sulle tavole, 11 novembre 2026, e la regola dell'annidato):
senza manopole l'engine disegna quello.

| manopola | valori | la tavola |
|---|---|---|
| `profile` | `shipped` · `before` | tutta la combinazione: **quella di serie** / quella di prima delle scelte (F1A.5: ombra corta, solo riflesso sullo scuro, velo scuro uguale su ogni taglia, smerigliatura 12 dp, vetro annidato come sulla pagina). Si applica per prima: le altre manopole la ritoccano |
| `edge` | `ios27` · `ios26` · `today` | «il bordo»: **A iOS 27** (filo scuro e riflesso in alto e in basso) / B iOS 26 (due archi di luce) / C oggi (filetto scuro sopra e sotto) |
| `veil` | `ios27` · `ios26` | «il velo»: **A iOS 27** / B iOS 26 (bianco 40%). La C «oggi, pellicola» non è un pezzo del profilo: è `gallery.model classic` |
| `shadow` | `ios27` · `ios26` | «l'ombra»: A corta, due componenti / **B larga, iOS 26** (34 dp, scostata in basso). La C «oggi» è `gallery.model classic` |
| `round` | `bar` · `apple` | «il tondo»: **A l'ombra della barra** / D come Apple (un quarto) |
| `frost` | `r12` · `r24` · `wide` · `mid` · `ios26` | «la smerigliatura»: A 12 dp / B 24 dp / C larga e mista (36 dp con il 12% di nitido) / M di mezzo (8 dp) / **D iOS 26, 4,5 dp** |
| `lens` | `light` · `medium` · `strong` | «la lente»: **`medium`** |
| `large` | `none` · `step` · `stepwide` · `two` | «i grandi»: **`stepwide`** (un passo tarato sull'ombra larga) / `step` il passo tarato sull'ombra corta / nessuno / due |
| `darkedge` | `reflex` · `wire` · `faint` · `today` | «il tema scuro», bordo: A solo il riflesso / **B resa più decisa (`wire`: filo bianco al 34% su tutto il perimetro, riflesso 0,20)** / B com'era nella tavola (`faint`, filo al 10%) / C com'è oggi |
| `darkveil` | `lighter` · `deeper` | «il tema scuro», velo dei tasti: **A più chiaro** / B più profondo |
| `darklarge` | `deep` · `same` | il velo scuro dei grandi (fogli, card, pannelli, menu): **`deep`** (`#36363C` al 50%: il testo secondario a 6:1 e oltre) / `same` quello dei tasti (il grigio medio di S1-ter, testo a 3,8:1) |
| `nested` | `on` · `off` | il vetro annidato: **`on`** (sul tema scuro un terzo del velo, sui due temi metà dell'ombra) / `off` come una lastra sulla pagina (la toppa grigia di S1-ter) |
| `travel` | `off` · `soft` · `strong` | il materiale **in viaggio** delle superfici di Fluid-physics (F1B.1): `off` il vetro di riposo anche in moto / **`soft`, un po' limpido** (nitido 0,25, lente ×1,5, cupola a metà, ingrandimento 3,5%, frangia 0,15) / `strong` (nitido 0,5, lente ×2, cupola piena, frangia 0,3). Pesato da quanto la sagoma si muove: da ferma non cambia niente |
| `travelclip` | `on` · `off` | **manopola di diagnosi** (F1B.2): il ritaglio del nodo che il corpo di una superficie fisica emula; **`on`** di serie, `off` per vedere se un bordo doppio viene da lì |
| `rimwidth` | `0..4` (dp) | lo spessore del filo, sui due lati (di serie 0.6) |
| `rimalpha` | `0..1` | la forza del filo scuro (di serie 0.32) |
| `reflex` | `0..1` | la riga chiara (di serie 0.12) |
| `halo` | `0..1` | l'alone sotto la riga (di serie 0.45) |
| `veilalpha` | `0..1` | il velo chiaro (di serie 0.46) |
| `adapt` | `0..1` | quanto il velo si ispessisce dove il fondo gli somiglia (di serie 0.7) |
| `blur` | `0..64` (dp) | la sfocatura di tasti, capsule e barre, prima della crescita dei grandi (di serie 4.5) |
| `mix` | `0..1` | il nitido sopra lo sfocato (di serie 0) |
| `bend` | `0..3` | la lente: lo spostamento in multipli della banda (di serie 0.9) |
| `shadowscale` | `0..8` | le due alfa dell'ombra insieme, sui due lati |
| `darkwire` | `0..1` | il filo bianco che copre, sul lato scuro (di serie 0.34; la B della tavola era 0.1) |
| `darkreflex` | `0..1` | la riga chiara sul lato scuro (di serie 0.20) |
| `darkveilalpha` | `0..1` | il velo scuro dei tasti (di serie 0.5) |
| `nestedveil` | `0..1` | quanto resta del velo di un vetro annidato sul tema **scuro** (di serie 0.35) |
| `nestedlightveil` | `0..1` | lo stesso sul tema chiaro (di serie 1: il velo resta) |
| `nestedshadow` | `0..1` | quanto resta dell'ombra di un vetro annidato, sui due temi (di serie 0.5) |
| `travelclear` | `0..1` | in viaggio, quanto nitido entra nella mescola a moto pieno (di serie 0.25) |
| `travelbend` | `0..3` | in viaggio, di quanto cresce lo spostamento della lente a moto pieno (di serie 0.5) |
| `travelneck` | `0.05..0.5` | a che frazione del tempo di un viaggio di `morphTo` (fino alla posa della molla) il collo fra due pezzi si rompe: più grande, collo più lungo (di serie 0.38, ~250 ms con la molla di serie; F1B.9). Le guide esterne (la barra fra le pagine) non lo leggono |
| `knobrim` | `on` · `off` | la goccia, non un numero del profilo: **`off`** il pomello a riposo senza filo né riflesso (arrivano con la pressione) / `on` filo e riflesso anche a riposo, com'erano dopo F1B.3 |
| `darkceiling` | `0..1` | il soffitto del velo scuro dei grandi a copertura parziale (F2.3; di serie 0.40). Sul tablet (S3, pop-up sopra il nero al 47%): di serie 83, a 1 (spento) 124; il banco dava 110 e 90 |
| `darklargealpha` | `0..1` | l'alfa del velo scuro dei grandi (`#36363C`, di serie 0.5): la profondità di un foglio, un pop-up o un pannello sopra il nero pieno. Sul tablet (S3, pop-up, nero al 25/47/74/100%): di serie 116/83/70/27, con 0.89 117/89/83/48 (Apple 111/89/61/48) |
| `edgedim` | `0..1` | non un numero del profilo: lo scurimento della striscia del bordo dello scorrimento sul lato scuro, al bordo dei tasti (di serie 0.2: ×0,80 al bordo e ×0,72 dietro i tasti) |
| `fliptime` | `ios26` · `swift` · `slow` | non un numero del profilo: i tempi del giro dei piccoli. **`ios26`** (corpo 300 ms, inchiostro pieno a 700, misurato su Apple) / `swift` (200 e 450) / `slow` (450 e 1000) |
| `flipwait` | `20..2000` (ms) | non un numero del profilo: la costante di tempo della media del filtro della sonda, cioè quanto aspetta prima di decidere il lato (di serie 200) |
| `flipon` · `flipoff` | `0..1` | non numeri del profilo: le due soglie del filtro sulla luminanza media, verso lo scuro e di ritorno al chiaro (di serie 0.45 e 0.62). `flipon` deve stare sotto `flipoff`: una coppia rovesciata si scarta tutta |

Le manopole del giro (`fliptime`, `flipwait`, `flipon`, `flipoff`) valgono per le sonde e i vetri che nascono dopo, come le
altre; le prove sul tablet sono nella pagina «Adattività» (`adaptive/LEGGIMI.md`, «Le manopole di S3»).

Esempio: `GALLERIA="--es gallery.tune edge=ios26,frost=r24" materiale components hud off`.

## Cosa esce

Tutto in logcat con il tag `FluidMateriale`; le ultime righe anche nel riquadro di stato.

| riga | cosa dice |
|---|---|
| `GALLERIA …` | gli interruttori presi all'avvio o a mano |
| `PROFILO chiesto=… preso=… scartato=…` | le manopole che l'engine ha preso |
| `AVVIO <chiave> \| <voci>` | la scena è partita; la chiave porta solo gli extra che contano per lei |
| `STATO <chiave> \| …` | la diagnostica, una volta al secondo quando cambia: vetro chiesto, livello vero e simulato, riscaldamento acceso; `superfici liquid=N classic=M` (le lastre vive per materiale) e `perche` (i motivi delle scelte: `Liquid`, `ClassicRequested`, `ApiLevel`, `FadeDown`, `GenericShape`, `FlatVeil`, `ShadersRefused`); `nodi` e `layer` vivi; `caldo` (lo stato del riscaldamento per famiglia); `shader provate=N rifiutate=…`; `profilo` |
| `RIQUADRO <chiave> modello=… nome=… x= y= w= h=` | dove sta ogni elemento, in pixel dello schermo: per `ritagli` e `angoli` |
| `PRONTO <chiave>` | due secondi dopo l'avvio: si può fotografare |
| `PRESSIONE … nome=… giu\|su` | il dito finto (scene `press` e `nested`): l'istante, per mettere i fotogrammi accanto |
| `MENU …`, `FOGLIO …`, `LISTA …` | le scene `menu`, `modal`, `list`: quando si apre, si chiude, scorre |

Il conteggio di superfici, nodi e layer vale perché la galleria accende la diagnostica prima del contenuto. Un
`glassControlSurface` che resta `Classic` conta due scelte (la sua e quella del vetro dentro di lui): le superfici vere
sono `superfici`. Anche il tag `FluidGlass` (dell'engine) va letto: riscaldamento fatto o fallito, sorgenti rifiutate.

## Le scene

Ogni scena mostra i due vetri affiancati (`Classic` a sinistra, `Liquid` a destra; uno sopra l'altro sotto i 720 dp
di larghezza), con i componenti veri dell'engine e non copie, sopra il fondo scelto. La barra delle schede della
galleria, in fondo, prende il vetro di `gallery.model`.

### scena components — Componenti
Tasto tondo (`FluidGlassIconButton`), tasto scelto e tasto (`FluidGlassButton`), capsula con tre azioni
(`FluidFloatingCapsule`), pannello della tab bar (`FluidFloatingTabBar`, col suo puck), tasto del menu
(`FluidMorphMenuButton`: si apre col tocco), card `Content` (`FluidCard(glass = true)`, senza ombra), due lastre con
una tinta scritta a mano (`Fixed`) e alla Apple (`Colored`), pannello `Modal` (l'ottica del foglio). Nomi dei
riquadri: `tondo` `scelto` `tasto` `capsula` `tabbar` `menu` `card` `fixed` `colored` `pannello`.

### scena edge — Il bordo a taglia vera
Gli elementi del banco (`oggi-foto`): tasto tondo vero da 44 dp (`tondo`), capsula 156×56 (`capsula`), menu 170×192
con raggio 28 (`menu`). Si fotografano interi e si ritagliano a 6x con `ritagli`.

### scena corner — L'angolo dei grandi
Due pannelli grandi con l'angolo continuo (raggio 38): `menu-grande` 260×220 e `foglio` 340×260. Si ritagliano gli
angoli con `angoli`.

### scena nested — Vetro annidato
Un foglio di vetro (`foglio`) con dentro un tasto (`conferma`), un tasto tondo (`tondo`) e un interruttore, che
ricevono il fondo della pagina come li scrivono le app. Il dito finto preme `conferma` in ciclo, una colonna dopo
l'altra. I tre controlli sono **vetro annidato**: dal nodo (il foglio è fra i loro antenati) nella colonna `Liquid`
prendono la regola dell'annidato (sul tema scuro un terzo del velo, sui due temi metà dell'ombra); `gallery.tune
nested=off` li rimette come lastre sulla pagina, per il confronto. Il caso di S1-ter (la notifica verde) è nella
pagina «Avvisi con azione»: vedi la voce A.6 qui sotto.

### scena siblings — Vetro annidato, tasti fratelli
Lo schema che il KDoc di `exports` insegna alle app: un pannello `Liquid` (`pannello`) in un nodo che **esporta** il suo
materiale, e i tasti (`conferma`, `tondo`, un interruttore) in un nodo **fratello**, sopra, che campionano
`rememberCombinedGlassBackdrop(pagina, materiale)`. Il nodo dei tasti non ha un vetro fra gli antenati: la regola
dell'annidato arriva dal fondo (`GlassLiquidPanels`, F1A.13). `zona` è una striscia del pannello senza testo, per
misurarne il corpo accanto al tasto (c'è anche in `nested`). Atteso: la stessa differenza fra `gallery.tune nested=on` e
`nested=off` della scena `nested`, e il corpo del tasto mai uguale al pannello sul tema scuro. Il dito finto preme
`conferma` come in `nested`.

### scena press — Pressione
Un tasto tondo da 76 dp (`tondo`) e una capsula grande (`capsula`). Il dito finto (eventi di tocco veri, consegnati
alla vista della composizione) scende a un quarto, scorre fino a tre quarti in metà del giro, resta un quarto, sale.
`material.cycle hold`: giù sul tondo della colonna `Liquid`, e resta, scivolando piano.

### scena intensity — Intensità
Una capsula e un pannello con `intensity` da 0 a 1 in ciclo (`material.intensity 0.5` la tiene ferma). A metà strada
il contenuto si dissolve, com'è oggi (F1A.6).

### scena list — Lista sotto una barra
Una pagina vera (`FluidScreen`, la barra in alto resta `Classic` fino a F2.5) che scorre da sola di 2400 dp e torna, e
sopra due capsule flottanti vere, una per vetro. Il fondo è quello della pagina: `material.backdrop` qui non conta.

### scena menu — Menu da un tasto
Il tasto che diventa il proprio menu, aperto dal dito finto e richiuso, una colonna dopo l'altra. Il viaggio è
Fluid-physics (vetro di prima) fino a F1B: è la voce che lo guarda.

### scena modal — Foglio e scrim
Un foglio vero dell'host dei modali, aperto e chiuso in ciclo, con lo scrim dietro, **in ogni colonna**
(`material.sheet opaque`: l'host senza fondo, il foglio pieno coi tasti di vetro sopra): ogni colonna
ha il suo host dei modali sotto il suo vetro, quindi il foglio della colonna `Liquid` è il materiale nuovo e quello
della colonna `Classic` il vetro di prima, aperti e chiusi insieme (fino a F1A.9 il foglio si apriva nell'host della
radice e prendeva il vetro di `gallery.model` in tutte e due). Lo scrim resta il vetro di prima nelle due colonne
(regola del velo piatto). Il tasto `apri` li apre anche a mano; il testo nel foglio dice di quale vetro è.

### scena tints — Tinte
L'indicatore di sincronizzazione (due stati), il tasto scelto, la capsula su una foto (`onPhoto`), le lastre `Fixed`
e `Colored`, una tinta di serie a cui l'app ha cambiato colore con `copy(overlay = arancio)` (`copiata`, deve leggersi
`Fixed`: regola di F1A.8), un pannello `tintedModalTint`.

### scena apps — Lastre delle app
Le lastre scritte a mano trovate da F1A.8: tasto di Fluidify su una copertina, scheda del titolo, pannello del
lettore con la sfocatura 7, capsula di MyCloud Gallery (le serie chiare col tema chiaro, le scure col tema scuro), e
nel chiaro i due tasti di Fluidify con la cattura al 40% (`fluidify-piatto`, `fluidify-in-pagina`).

### scena contrast — Contrasto di sistema
Tasto tondo, tasto con testo, card e pannello col testo sopra, a contrasto di sistema standard, medio e alto
(`LocalFluidSystemContrast`, come lo fornisce il tema da Android 14): sopra lo standard le tinte di serie diventano la
pellicola densa di oggi.

### scena rtl — Destra-sinistra
Gli stessi vetri da sinistra a destra e da destra a sinistra: un foglio con gli angoli diversi (38 e 10), una capsula
con la freccia indietro, un tasto con l'icona in testa, la tab bar con la prima scheda scelta.

## Cosa si è visto e chi guarda il resto

Stato delle voci della mappa dopo S1-ter, S2-pre e il giro di correzione di F1A (un solo dispositivo, il Tab S9, tema di
sistema chiaro all'ultima sessione; i rapporti stanno in `artifacts/liquid-glass/sessioni/`). Nella colonna **chi**: la
sessione sul dispositivo o la fase del piano che guarda ciò che manca (`docs/liquid-glass/piano.md`: 4.5 per le sessioni,
le tabelle «Difetti del materiale nuovo visti sul tablet» e «Dopo S2-pre, F1A.13 e il giro di correzione» per i difetti);
dove il piano non assegna una voce, il proprietario è **S2**. «Chiuso» vuol dire che non resta niente da guardare per F1A.

| voce | cosa si è visto | cosa manca | chi |
|---|---|---|---|
| 6.1, 7.1, 7.2 compilazione, test strumentati, riscaldamento | nessuno shader rifiutato; `OK (3 tests)` × 2, 13 «compila», `half4 flat` rifiutato; riscaldamento 7,7–8,1 ms di filo principale (S1-ter 4,6–6,2) | niente | chiuso |
| 6.2, A.2 il bordo, chiaro e scuro | combacia col banco entro pochi livelli: tondo scuro +95 contro +93 in alto, chiaro −50 contro −42, un pixel più in fuori | niente | chiuso |
| 6.3, A.4, A.5 ombra | come il banco sotto il foglio (−16 a ridosso, −10 a 16 dp), nessun rettangolo; l'indicatore da 26 dp senza anello | l'ombra che cresce premendo non si è registrata; la lente pesca l'ombra in fondo ai grandi (1–2 livelli, −5 con `large=step`); un foglio scuro a filo dello schermo ha una banda scura in fondo | l'ombra premuta: **S2**; la lente e la banda: **F3** |
| 6.4 ritaglio al 98% | a occhio, a 4×, il filo ha spessore costante agli angoli dei grandi | in numeri, sul dispositivo | **S2** |
| 6.5, A.6 vetro annidato | foglio `Liquid` 84 → 61 su 40; notifica piena 65–67 su 56 (scuro) e 240 su 233 (chiaro); controlli fratelli di un pannello 62 su 48; foglio senza fondo 56 su 43; test strumentato 5 su 5 | il composer di `engine-ai-ui` (non è nella galleria); nella notifica **di vetro** in chiaro il corpo è uguale al pannello (239,9 su 240,2) | composer: **S2**; il corpo in chiaro: **F2** |
| 6.6 pressione | lente, bagliore e filo puliti, ± 5 livelli a fotogramma, nessun lampo; due bagliori insieme col dito finto: va; +0,7 ms di GPU contro il vetro di prima | il bagliore è piatto su tutta la lastra e in chiaro satura a bianco pieno (visto in S1-ter, uguale nel vetro di prima) | **F6** (S5) |
| 6.7 lista sotto una barra | scadenze non peggiori del vetro di prima (17,5–20,0% contro 20,8–29,2%) | niente | chiuso |
| 6.8, C ritorno dallo sfondo e rientro nell'albero | 0 pixel cambiati; l'anello dopo il riciclo di una lista coincide con gli angoli | niente | chiuso |
| 6.9 `intensity` | ferma a 0,25, 0,5 e 0,75 (S2-pre C13); la comparsa: filo e corpo dal primo fotogramma | il primo fotogramma di un foglio che compare con la lastra nuova non si è guardato a vista | **F1B** (prova 6) |
| 6.10, 6.11 barra ancorata, menu da un tasto, tab bar | il materiale cambia a vista fra riposo e viaggio; la barra fra le pagine mostra un fotogramma senza vetro all'arrivo, 6 su 6, nei due temi di sistema | il difetto è assegnato, non risolto | il materiale: **F1B**; il fotogramma: **F4A**, prova in F1B.6 |
| 6.12 modale | il foglio dell'host senza fondo (tasto 56 su 43, a mano) e il foglio scuro con fondo (A.1) | il foglio a schermo intero in chiaro (il tocco è caduto sul bordo) | **S2** |
| 6.13, 8.2 tinte | l'indicatore da 26 dp (A.5) | `Fixed`, `Colored`, `copiata` e il tono dell'indicatore non risultano guardati nei rapporti | **S2** |
| 6.14, B.3 destra-sinistra | la barra specchiata della sezione `rtl`: margini 16,0 dp da tutti e due i lati | l'intera galleria in destra-sinistra: con la sola chiave `debug.force_rtl` l'app non si gira | **S2** |
| 7.3, S2 livello simulato e `Classic` forzato | a 32 e a 30: nessuna caduta, `shader provate=0`; `gallery.model classic` uguale al vetro di prima | un dispositivo vero sotto Android 13 (il livello simulato non prova il driver) | **S2** (serve un dispositivo: piano 4.4); l'S25 è della **S5** |
| 7.4 primo fotogramma con e senza riscaldamento | misurato: 54–61 ms acceso, 54–55 spento; «Controlli» 45–50 contro 49–50; «Materiale» 13 contro 12–13: il riscaldamento non protegge la prima schermata | se tenerlo, anticiparlo o toglierlo | **F7** (il cancello C6 si riapre) |
| 7.5 conta dei layer | `Liquid` 4 nodi e 3 layer, `Classic` 5 e 2, `Liquid` senza ombra 3 e 2 | i tre nodi di Kyant del vetro di prima non si contano | chiuso (limite noto) |
| 8.1 orologio | — | l'orologio non è nella galleria e chiede `Classic` | incarico a parte (piano, sezione 2) |
| 8.3 composer | — | non è nella galleria | **S2** |
| 8.4 contrasto medio e alto | le lastre tornano la pellicola densa, dal vivo: tondo chiaro 240 · 229 · 225, pannello scuro 35 · 49 · 60 | niente | chiuso |
| 8.5, A.7 pannello di Fluidify | solo sul banco: 11,5 dp contro 17,5 | sul tablet, su copertine vere | **S2** |
| 8.6 leggibilità a contrasto standard | debito dichiarato: peggiore di oggi su fondi del tono opposto al tema | la correzione | **F2** (F2-e) |
| A.1 fogli e card scuri | foglio 42, card 43, controlli 38; testo secondario 6,2, 6,1, 6,6:1 (dopo la correzione: 46 e 45, 5,9:1, un decimo sotto 6:1) | niente per F1A | chiuso |
| A.3 smerigliatura in movimento | D: macchie a strisce; M: velatura; C: lettere fantasma; sfarfallio D : M : C da 1,5 : 1,2 : 1 (tab bar) a 2,9 : 2,1 : 1 (capsula) | sotto un menu aperto (è ancora Fluid-physics); dietro la tab bar D lascia più contrasto di iOS 26 (19,0 contro 10,2) | il menu: **F1B**; la tab bar: **F2** (col bordo dello scorrimento) |
| A.8 cattura al 73% | circa +0,1 ms di GPU; scadenze uguali | niente | chiuso |
| B.1, B.2, B.4, B.5 icone, barre, tab bar, rail | taglie nitide e monocrome; vetri a 16,0–16,7 dp dal bordo, centri a 52 dp, freccia centrata, tab bar 61,9–62,0 dp, rail verticale, lente a inchiostro, croce a 16,7 dp, titolo lungo troncato; icona piena su ogni scheda (tavola 12) | niente | chiuso |
| B.5 `set`, B.6 prima costruzione di un'icona | — | la sezione `set` non si è aperta e il costo della prima costruzione non si è misurato | **S2** |
| B.7 minori | — | la scheda «Preferiti» sempre piena e la faccia a 20 dp del tasto deprecato sono noti, non annotati sul dispositivo | **S2** |

## La mappa: voce del backlog → scena → comando → cosa guardare → esito atteso

Le voci di F1A.6 sono le quattordici del rapporto di F1A.6 («Backlog del dispositivo, in ordine di rischio»), quelle
di F1A.7 e F1A.8 dai loro rapporti (`artifacts/liquid-glass/stato.md`, sezione F1A). Tema chiaro di serie; dove
serve lo scuro, `GALLERIA="--es gallery.theme dark"`.

| voce | scena | comando | cosa guardare | esito atteso |
|---|---|---|---|---|
| 6.1 compilazione e primo fotogramma | `components` | `materiale components hud off; qa_shot "$OUT" componenti-chiaro; adb logcat -d -s FluidGlass:*; stato` | il logcat di `FluidGlass`; la riga `STATO`; la foto | nessuna riga «rifiutata»; `shader … rifiutate=-`; `superfici liquid=` sopra zero; tasto e capsula `Liquid` visibili, non neri né vuoti |
| 6.2 i due disegni della luce | `edge` | `materiale edge hud off; qa_shot "$OUT" bordo-pagina; ritagli bordo-pagina tondo capsula menu`, poi lo stesso con `backdrop white` e con `GALLERIA="--es gallery.theme dark"` | i ritagli a 6x della colonna `Liquid` | sul chiaro una riga chiara in alto e in basso e il filo scuro continuo su tutto il perimetro; sullo scuro un filo chiaro su tutto il perimetro (pieno in alto e in basso, un poco più tenue sui fianchi) e il riflesso. Se il filo ha il colore della luce, o manca, la piattaforma non fotografa lo shader fra i due disegni come assunto (F1A.3b). Visto in S1-ter con la combinazione di prima: il bordo combacia col banco |
| 6.3 nodo dell'ombra | `edge`, `press` | `materiale edge backdrop white hud off; qa_shot "$OUT" ombra-bianco; ritagli ombra-bianco capsula menu`; poi il video di 6.6 | il bordo basso dei ritagli; nel video, l'ombra sotto il tasto premuto | ombra larga e morbida sotto capsula e pannello (la B), niente sotto la sagoma, nessun rettangolo; premendo, l'ombra cresce col tasto |
| 6.4 ritaglio al 98% | `corner` | `materiale corner hud off; qa_shot "$OUT" angolo; angoli angolo menu-grande; angoli angolo foglio`; poi `backdrop page-dark` e tema scuro | gli angoli a 6x, `Liquid` contro `Classic` | il filo ha lo stesso spessore sulla diagonale e sui lati, nessuna mezzaluna di corpo fuori dal filo. Annotare se il bordo del corpo all'angolo esce morbido sotto il filo (cattura a 0,4) |
| 6.5 vetro annidato | `nested` | `materiale nested hud off; qa_record annidato 14; qa_finish annidato "$OUT"` | i fotogrammi attorno alle righe `PRESSIONE`; gli altri due controlli sul foglio | i tasti rifrangono il corpo del foglio, non il suo filo né il suo bagliore; mentre `conferma` è premuto gli altri non sfarfallano (luminanza piatta nella loro zona). Nel tema scuro, con la regola dell'annidato: vedi A.6 |
| 6.6 pressione | `press` | `materiale press hud off; qa_record premi-chiaro 14; qa_finish premi-chiaro "$OUT"`; poi tema scuro; poi `materiale press cycle hold hud off; qa_shot "$OUT" premuto` | lente, bagliore, filo; i fotogrammi persi (`dropped` in `dumpsys gfxinfo $PKG`); la luminanza sotto il dito nella foto `premuto` | la lente si approfondisce, il bagliore segue il dito, il filo resta pulito sotto il bagliore, nessun fotogramma perso. **Sul chiaro: il punto sotto il dito è +64 o bianco pieno?** (la risposta tara F6) |
| 6.7 lista sotto una barra | `list` | `adb shell dumpsys gfxinfo $PKG reset; materiale list hud off; qa_record lista 20; qa_finish lista "$OUT"; adb shell dumpsys gfxinfo $PKG; adb logcat -d -s FluidGlassDiag` | capsule e tab bar mentre la pagina scorre; le righe `captures=` | le capsule e la barra si aggiornano col contenuto, nessun salto del margine alla soglia di qualità (al più due ricatture per lancio) |
| 6.8 ritorno dallo sfondo | `components` | `materiale components hud off; qa_shot "$OUT" prima-home; adb shell input keyevent 3; sleep 3; adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT"; sleep 3; qa_shot "$OUT" dopo-home` | le due foto | identiche: luce e ombra tornano, nessun contorno di un'altra lastra |
| 6.9 `intensity` | `intensity` | `materiale intensity hud off; qa_record intensita 10; qa_finish intensita "$OUT"`; foto ferme con `intensity 0.25`, `0.5`, `0.75` | i primi fotogrammi della comparsa | una lastra che compare da zero non mostra un rettangolo della pagina vecchia; a metà il contenuto si dissolve come oggi |
| 6.10 barra ancorata e menu da tasto | `menu` | `materiale menu hud off; qa_record menu 18; qa_finish menu "$OUT"`; la barra ancorata: Chrome → «Pagine» (sotto) | il passaggio fra il tasto fermo (`Liquid`) e il viaggio (vetro di prima) | **atteso un cambio di materiale visibile** fino a F1B: annotare quanto si vede, all'apertura e alla chiusura, e se c'è un fotogramma senza vetro |
| 6.11 tab bar flottante | `components` | dalla foto `componenti-chiaro`: `ritagli componenti-chiaro tabbar`; e la barra della galleria in fondo con `gallery.model liquid` | l'ombra sotto il pannello; il puck | ombra nuova, larga, sotto il pannello; il puck (ancora vetro di prima) sopra il vetro nuovo, senza aloni |
| 6.12 modale | `modal` | `materiale modal hud off; qa_record foglio 12; qa_finish foglio "$OUT"`; foto ferme con `cycle hold` | il foglio e lo scrim nelle due colonne | il foglio `Liquid` è il materiale nuovo e quello `Classic` il vetro di prima; lo scrim è identico nelle due colonne (resta `Classic`: regola del velo piatto) |
| 6.13 tinte | `tints` | `materiale tints hud off; qa_shot "$OUT" tinte-foto`; poi `backdrop page`, e il tema scuro | indicatore di sincronizzazione, tasto scelto, capsula su foto, `copiata`, `Colored` | l'indicatore ha il suo tono (regola di F1A.8; F1A.6 lo dava perso), il tasto scelto l'accento, la capsula su foto la pellicola di oggi, `copiata` è arancio, `Colored` segue il fondo |
| 6.14 destra-sinistra e orologio | `rtl` | `materiale rtl hud off; qa_shot "$OUT" rtl; ritagli rtl foglio-ltr foglio-rtl` | il foglio con gli angoli diversi, la capsula, la tab bar | in `rtl` gli angoli sono specchiati e filo e ritaglio li seguono; nessun angolo «sbagliato». L'orologio non è nella galleria: resta nel backlog (NV qui) |
| 7.1 i due test strumentati | — | `adb shell pm clear $PKG; adb shell am instrument -w -r -e class dev.antigravity.fluidengine.sample.GlassShaderCompileInstrumentedTest $PKG.test/androidx.test.runner.AndroidJUnitRunner`; lo stesso con `GlassNodeCountInstrumentedTest`; poi `adb logcat -d -s GlassShaderCompile GlassNodeCount FluidGlass` | l'uscita dei due test e il logcat | `OK (3 tests)` per ciascuno; 13 «compila»; `Slab` e `Group` `Done (2/2)`; `Liquid` 4 nodi e 3 layer, senza ombra 3 e 2, `Classic` 5 nodi e 2 layer (letti sul tablet in S1-ter e S2-pre) |
| 7.2 riscaldamento automatico | — | `adb shell pm clear $PKG; adb logcat -c; adb shell am start -S -W -n "$PKG/$ACT"; sleep 5; adb logcat -d -s FluidGlass` | la riga del riscaldamento | «Riscaldamento del vetro: Slab: Done (2/2)…» circa 0,5–1 s dopo che la prima schermata si ferma; nessun `Failed`. Misurato 7,7–8,1 ms di filo principale (S1-ter 4,6–6,2) |
| 7.3 livello simulato | `components` | `GALLERIA="--es gallery.api 32" materiale components; qa_shot "$OUT" api32; stato`; lo stesso con `30`; poi `gallery.page Menus` e `AllGlass` con gli stessi extra | la riga `STATO`; le foto accanto a quelle dell'APK di riferimento 3.1.2 | a 32: `superfici liquid=0`, `perche ApiLevel=…`, niente lente, riflesso piatto, Fluid-physics a `Balanced`, nessuna compilazione AGSL nuova in logcat; a 30: tinta piena, Fluid-physics a `Lite` |
| 7.4 primo fotogramma con e senza riscaldamento | «Misure», `compile` | i comandi «(g bis)» di `../measure/LEGGIMI.md` (`steps engine,body1`, `gallery.model classic`, `gallery.warmup off` e `on`, `pm clear` ogni volta) | le righe `COMPILA … engine-warmup`, `engine-before`, `variant=engine` | `engine-before liquid=0`; spento, `first_render_ms` del passo `engine` porta la compilazione (31–39 più 19–25 ms a freddo); acceso, vicino a quello a caldo (3–6 ms per programma). **Poi misurato** sulla prima schermata e sulle pagine aperte dopo: il riscaldamento non le accorcia (54–61 ms acceso contro 54–55 spento; «Controlli» 45–50 contro 49–50), vedi la tabella «Cosa si è visto» |
| 7.5 conta dei layer dal vero | `edge` | `materiale edge model liquid; stato`; poi `model classic` | `nodi` e `layer` della riga `STATO` | con tre lastre ferme `Liquid` (più la barra della galleria) i layer per lastra sono 3, e 2 senza ombra; `Classic` 5 nodi e 2 layer: i tre nodi di Kyant, e i loro tre layer, non sono contati (limite noto di F1A.7: il «5 e 5» del test in Robolectric li deduce dal codice) |
| 8.1 orologio | — | — | — | NV in questa sessione: serve l'orologio. Atteso: identico alla 3.x (`engine-wear` fornisce `Classic`) |
| 8.2 tono dell'indicatore di sincronizzazione | `tints` | come 6.13 | l'indicatore | il tono si vede |
| 8.3 composer | — | — | — | NV qui: il composer è di `engine-ai-ui`, non è nella galleria (PampAI) |
| 8.4 contrasto medio e alto | `contrast` | `materiale contrast hud off; qa_shot "$OUT" contrasto`; poi dal vivo: `adb shell settings put secure contrast_level 0.5`, `qa_shot …`, `1.0`, `qa_shot …`, e **sempre** `adb shell settings put secure contrast_level 0` alla fine | le tre colonne; poi il vetro vero mentre il sistema cambia | sopra lo standard il vetro diventa la pellicola densa di oggi, dal vivo, senza riavviare. Se One UI non propaga `contrast_level`, annotarlo e provare dalle impostazioni a mano (se Alessio vuole) |
| 8.5 pannello di Fluidify | `apps` | `materiale apps hud off; qa_shot "$OUT" app-chiaro`; poi tema scuro | `fluidify-pannello`, `Liquid` contro `Classic` | la sfocatura chiesta oltre il ruolo vale come crescita (F1A.5c): il pannello esce smerigliato due volte la D (9 dp, che la crescita dei grandi porta a circa 11 dp): più del tasto, ancora meno dei 17,5 dp di oggi. Le copertine vere restano NV: il fondo è la foto generata (le copertine: voce C.7) |
| 8.6 leggibilità a contrasto standard (F1A-g) | `contrast` | `materiale contrast contrast standard backdrop black hud off; qa_shot "$OUT" leggibilita-nero`; poi `backdrop photo` e il tema scuro | il testo su card e pannello | **atteso peggiore di oggi** nel tema chiaro sui fondi scuri: debito dichiarato per F2 (inchiostro e lato adattivi), da portare ad Alessio, non da correggere in sessione |
| S2 `Classic` forzato | tutte | `GALLERIA="--es gallery.model classic"` con `--es gallery.page Menus`, `AllGlass`, `Gaps` e `materiale components model classic`; accanto all'APK di riferimento 3.1.2 | ogni pagina, nelle due build | identiche (il vetro di prima resta al pixel: `golden/classic`) |

La barra ancorata fra pagine (6.10): «Pagine» è un'attività a parte che `adb` non può aprire da fuori. Si arriva con
`materiale components`, poi `adb shell input keyevent 4` (Indietro: si torna alla scheda Chrome sotto la pagina),
poi `read -r x y < <(qa_bounds "Pagine"); adb shell input tap $x $y` (`qa_bounds` legge la UI con `uiautomator`).
Gli interruttori di processo (livello simulato, manopole) valgono anche lì; il vetro chiesto alla radice no (è
un'altra attività).

## Le tavole sul pannello (le manopole)

Le tavole di gusto di F1A (piano, F1A «Scelte di gusto») sono state giudicate sul banco: qui si rifanno sul pannello,
con il componente vero, nelle varianti che Alessio sta scegliendo. Stessa scena per tutte: `components` con un vetro
solo, foto senza riquadro, sulla pagina vera e sulla foto.

```bash
tavola() {   # tavola <nome> <manopole> [extra della pagina...]
  local name="$1" knobs="$2"; shift 2
  for b in page photo; do
    GALLERIA="--es gallery.tune $knobs $TEMA" materiale components model liquid backdrop "$b" hud off "$@" >/dev/null
    qa_shot "$OUT" "tavola-$name-$b"
  done
}
TEMA=""
tavola serie profile=shipped; tavola prima profile=before
tavola bordo-A edge=ios27;  tavola bordo-B edge=ios26;  tavola bordo-C edge=today
tavola velo-A veil=ios27;   tavola velo-B veil=ios26
tavola ombra-A shadow=ios27; tavola ombra-B shadow=ios26; tavola tondo-D round=apple
tavola smeriglio-A frost=r12; tavola smeriglio-B frost=r24; tavola smeriglio-C frost=wide
tavola smeriglio-M frost=mid; tavola smeriglio-D frost=ios26
tavola grandi-passo-corto large=step
TEMA="--es gallery.theme dark"
tavola scuro-serie profile=shipped; tavola scuro-prima profile=before
tavola scuro-A darkedge=reflex; tavola scuro-B darkedge=wire; tavola scuro-B-tavola darkedge=faint
tavola scuro-C darkedge=today
tavola velo-scuro-A darkveil=lighter; tavola velo-scuro-B darkveil=deeper
tavola grandi-scuri-come-tasti darklarge=same
```

Il vetro annidato non sta nella scena `components`: si fotografa con `nested` (e la notifica, voce A.6):

```bash
for t in light dark; do
  for n in on off; do
    GALLERIA="--es gallery.tune nested=$n --es gallery.theme $t" materiale nested model liquid cycle off hud off >/dev/null
    qa_shot "$OUT" "annidato-$t-$n"
  done
done
```

Poi i ritagli a 6x del tasto tondo e della capsula di ogni tavola: `riquadro` legge l'ultima riga `RIQUADRO`, quindi
si ritaglia subito dopo ogni foto (o si rilancia la scena). Per la combinazione che Alessio ha scelto, una tavola con
tutte le manopole insieme (`edge=…,veil=…,frost=…`) e la galleria intera con `gallery.tune` (le pagine vere).

## Ordine del giorno proposto

Le voci aggiunte dopo S1-ter (F1A.5c, F1A.11a, F1A.11b e la regola del vetro annidato) sono nella tabella «Voci
aggiunte» in fondo a questa sezione. La sessione breve che ha chiuso F1A (S2-pre) ne ha preso la parte A e, da questa
tabella, le righe 2, 3 e 7; ciò che non ha guardato, e chi lo guarda, è nella tabella «Cosa si è visto e chi guarda il
resto». L'ordine che segue resta quello di una sessione intera (S2, dopo F1B).

Circa due ore e mezza per la parte del materiale di S2; le altre voci di S2 (Fluid-physics, parità dei tre percorsi,
icone) hanno le loro pagine.

| | cosa | voci | tempo |
|---|---|---|---|
| 1 | preparazione: lucchetto, installazione, `materiale components` di prova, `stato` | — | 10 min |
| 2 | compilazione: i due test strumentati, il riscaldamento automatico, la riga `STATO` senza rifiuti | 7.1, 7.2, 6.1 | 10 min |
| 3 | il materiale fermo: `components` sui sette fondi in chiaro e in scuro (14 foto), `edge` con i ritagli su pagina, bianco, nero, foto e nel tema scuro, `corner` con gli angoli | 6.1, 6.2, 6.3, 6.4, 6.11 | 30 min |
| 4 | il movimento: `press` (chiaro e scuro, e `hold`), `nested`, `intensity`, `list` con `gfxinfo`, `menu`, `modal` nei due vetri della radice, `components` con `motion scroll` | 6.5, 6.6, 6.7, 6.9, 6.10, 6.12 | 35 min |
| 5 | il resto: ritorno dallo sfondo, `tints`, `apps`, `contrast` (anche dal vivo, e rimettere `contrast_level 0`), `rtl` | 6.8, 6.13, 6.14, 8.2, 8.4, 8.5, 8.6 | 20 min |
| 6 | le tavole sul pannello con le manopole, e i loro ritagli | scelte di F1A | 25 min |
| 7 | la galleria con il livello simulato a 32 e a 30 e con `Classic` forzato, accanto all'APK 3.1.2; il primo fotogramma con e senza riscaldamento in «Misure» | 7.3, 7.4, 7.5, S2 | 20 min |
| 8 | chiusura: `wm size reset` e `wm density reset` se usati, `contrast_level 0`, un ultimo `adb logcat -d -s FluidMateriale FluidGlass > "$OUT/logcat.txt"`, collage | — | 5 min |

Il formato telefono (`adb shell wm size 1080x2340` e `wm density 440`) per una ripresa di `components` e di `press`,
se resta tempo: le colonne passano una sopra l'altra.

### Voci aggiunte (dopo S1-ter)

Tema scuro con `GALLERIA="--es gallery.theme dark"`. La pagina «Icone» si lancia con `adb shell am start -S -W -n
"$PKG/$ACT" --es gallery.page icons --es icons.section sizes|chrome|rtl|set` (a mano: Chrome → «Icone»).

**A · Il materiale di serie, dopo le scelte (F1A.5c) e la regola dell'annidato** (circa 40 min):

| voce | dove e comando | cosa guardare | esito atteso |
|---|---|---|---|
| A.1 fogli e card scuri | il foglio «Ancorato al bordo» (Chrome, il tasto del foglio) e la card «Come si risponde» («Da provare», `--es gallery.page totry`), tema scuro; poi con `gallery.tune darklarge=same` e `profile=before` | corpo e testo secondario, con lo strumento di S1-ter (`S1-ter/strumenti/`) | corpo ≈ 41 di luma (in S1-ter 74–75), testo secondario ≥ 6:1 (in S1-ter 3,8:1); con `darklarge=same` torna il grigio medio. **Visto** in S2-pre: foglio 42 con 6,2:1, card 43 con 6,1:1, controlli 38 con 6,6:1 (dopo la correzione del vetro annidato, 46 e 45 con 5,9:1) |
| A.2 bordo scuro | `materiale edge` tema scuro, `ritagli … tondo capsula`; poi `darkedge=faint` e `darkedge=reflex` | il filo chiaro sui fianchi e in alto | di serie un filo chiaro netto tutto intorno (sul banco +52 in alto, +34 sui fianchi), più deciso del 10% della tavola |
| A.3 smerigliatura in movimento | `materiale components motion scroll hud off` e `materiale list`, sotto la tab bar e un menu aperto; D di serie contro `frost=wide` e `frost=mid` (`qa_record` di ciascuna) | le parole dietro la barra mentre scorrono, lo sfarfallio | D: macchie che si riconoscono come forme, la più mossa (sul banco sfarfallio 7,6 contro 2,2 di C); C: lettere fantasma; M in mezzo. Alessio ha scelto D «con molta difficoltà»: le riprese sono per lui |
| A.4 ombra dei grandi | `materiale components` e `corner`, chiaro, foto ferme; poi `large=step` e `shadow=ios27` | l'ombra sotto menu e pannelli, ai fianchi | di serie un'ombra larga che dice «più in alto di una barra» senza alone enorme (sul banco 15 livelli a ridosso del pannello, 10 a 16 dp) |
| A.5 indicatore da 26 dp | `materiale tints`, chiaro e scuro, ritaglio a 6x dell'indicatore | anello chiaro, ombra | il filo e una riga chiara tenue, niente anello bianco, l'ombra non più grande del tondo |
| A.6 vetro annidato | pagina «Avvisi con azione» (`--es gallery.page NotificationUndo`, tasto «Modifica ordinaria»: la notifica «Operazione aggiornata» con «Ripristina ultima modifica» dentro), tema scuro e chiaro; poi con `gallery.tune nested=off`; poi `materiale nested` nei due temi | il tasto dentro la notifica e il pannello attorno, luma media come in S1-ter (`shots/difetti/4-…`) | scuro: il tasto prende il verde del pannello e sta poco sopra di lui (sul banco 64 su 54; in S1-ter 71, toppa grigia; il vetro di ieri 62); con `nested=off` torna la toppa. Chiaro: come in S1-ter (pulito), l'ombra del tasto più leggera. **Visto**: in S2-pre la notifica era un anello vuoto (56 su 56 in scuro, 233 su 233 in chiaro, `nested=on` e `off` uguali al pixel); dopo la correzione (F1A.13) il tasto sta a 65–67 su 56 in scuro e a 240 su 233 in chiaro, con D, M, r12 e C, e nella scena `nested` a 61 su 40 |
| A.7 pannello di Fluidify | `materiale apps hud off` (`fluidify-pannello`), chiaro e scuro; se si può, sopra copertine vere | la smerigliatura del pannello | due volte la D (9 dp, circa 11 con la crescita dei grandi): le copertine dietro non si leggono, ma il pannello è meno smerigliato di oggi (17,5 dp): giudicare se basta. **Non fatto** in S2-pre; sul banco 11,5 contro 17,5 dp |
| A.8 costo della cattura al 73% | `adb shell dumpsys gfxinfo $PKG reset; materiale list hud off; …; dumpsys gfxinfo`, di serie e con `frost=r12` (cattura al 40%) | GPU per fotogramma, fotogrammi persi | la D cattura a 0,73 della risoluzione (la A a 0,4): annotare quanto costa in più su una pagina che scorre. **Misurato** in S2-pre: circa +0,1 ms di GPU (lista 2,43 contro 2,33 ms, pressione 3,15 contro 3,03), scadenze mancate uguali |

**B · Le icone (F1A.11a, F1A.11b)** (circa 30 min):

| voce | dove e comando | cosa guardare | esito atteso |
|---|---|---|---|
| B.1 taglie | «Icone», sezione `sizes`, chiaro e scuro | le taglie dei token sul vetro | 24/26/32 dp nei tasti, inchiostro monocromo, nessuna icona sfocata o tagliata |
| B.2 barre su telefono e tablet | sezione `chrome`, sul tablet e in formato telefono (`wm size 1080x2340`, `wm density 440`, poi `reset`); un titolo lungo | barre a 26 dp, freccia `ArrowBackIosNew` 24 dp, vetri a 16 dp dal bordo e 8 fra loro, `CompactTitleInset` 84 | il titolo lungo si tronca prima dei tasti, nessuna sovrapposizione |
| B.3 destra-sinistra | sezione `rtl` | le icone che si girano e quelle che no | si girano solo le frecce e le icone direzionali |
| B.4 tab bar e rail | la barra della galleria (62 dp, icone 28, etichetta 11 sp); il rail su un tablet in orizzontale (≥ 600 dp) | icone, etichette, voce scelta | la tab bar vera a 62 dp, il rail come la tab bar (icone 28, inchiostro pieno) |
| B.5 ricerca, foglio, set | il campo di ricerca (lente a inchiostro), la croce del foglio (16 dp), la sezione `set` della pagina «Icone» (`Dns`, `Shield`, `Language` e `Group`, i quattro cambi di icona, non sono usate da nessun componente né da nessuna riga della galleria: si vedono solo lì), i tre tasti senza taglia | le icone sostituite | nessuna icona mancante o di Material vecchio; la croce del foglio piccola e centrata. Visto in S2-pre: ricerca e croce (16,7 dp dal bordo); non visto: `set` |
| B.6 prima costruzione | l'apertura della sezione `set` (tutto il set) a freddo (`pm clear` prima), con una traccia Perfetto o `dumpsys gfxinfo`: la pagina non scrive questo tempo in logcat | il fotogramma in cui il set compare | la prima costruzione di un'icona sotto 1 ms (stima di F1A.11a: da misurare) |
| B.7 minori | «Tutto in vetro», scheda «Preferiti»; `FluidGlassMenuButton` (deprecato) | — | la scheda «Preferiti» è sempre piena (noto, da annotare); la faccia del tasto deprecato a 20 dp |

**C · Ciò che S1-ter non ha fatto** (circa 20 min). Fatto in S2-pre: l'anello di `HighlightModifier` dopo un rientro
nell'albero, nei due vetri e nei due temi (C10: gli angoli coincidono, 0 pixel cambiati nel foglio); il livello simulato 32
e 30 (C11: nessuna caduta, nessuno shader provato); la barra fra le pagine col tema di **sistema** chiaro (C12: il
difetto c'è, 6 su 6); `intensity` ferma e il contrasto di sistema (C13). Fatto nel giro di correzione: due bagliori
insieme col dito finto (scena `press` con `material.fingers 2`) e la comparsa con `intensity`. **Resta**: la
destra-sinistra di sistema (`materiale rtl`, voce 6.14, e B.3), che con la sola chiave `debug.force_rtl` non si applica
(proprietario: S2), e il resto della tabella «Cosa si è visto e chi guarda il resto».

## Che cosa la pagina non mostra

- **Il menu e i viaggi** sono ancora Fluid-physics (vetro di prima) fino a F1B, in tutte e due le colonne: la scena
  `menu` guarda il passaggio, non il materiale del menu.
- **L'host dei modali della radice**: nella scena `modal` ogni colonna ha un host suo. Il foglio di un'app, che si apre
  nell'host della radice, si guarda con `gallery.model` (6.12 nelle altre pagine).
- **La barra in alto** di `FluidScreen` (scena `list`) è `Classic` fino a F2.5.
- **La barra delle schede** della galleria, sopra la scena, campiona il palco della scena (giro di correzione di F1A:
  la pagina registra il suo palco nel fondo della galleria). Prima campionava un fondo vuoto e lasciava il testo
  quasi nitido.
- **L'ombra** non è nel riscaldamento (F1A.7) e i tre nodi di Kyant del vetro di prima non sono nei conteggi.
- **La foto** è generata in codice: niente copertine vere (8.5), niente colori P3 veri.
- **L'orologio** e **il composer** non sono nella galleria.
- **Il dito finto** è un tocco senza dimensione né pressione variabile, e passa dalla vista della composizione: prova
  il componente, non il percorso del sistema (per quello, `adb shell input swipe x1 y1 x2 y2 1500` sui riquadri).
