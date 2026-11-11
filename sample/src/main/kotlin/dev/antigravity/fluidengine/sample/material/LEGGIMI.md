# La pagina «Materiale»

Pacchetto F1A.9 del piano «iOS in Android» (`docs/liquid-glass/piano.md`, sezione F1A). Il materiale nuovo
(`Liquid`) sul pannello vero non l'ha ancora visto nessuno: questa pagina lo mette davanti alla sessione sul tablet
(S2, piano 4.5) voce per voce, **accanto al vetro di prima** (`Classic`), sui sei fondi di 4.6, fermo o con il fondo
che scorre, e scrive in logcat (tag `FluidMateriale`) ciò che dice la diagnostica dell'engine. Si guida tutta da
`adb`, senza toccare lo schermo; a mano si apre dalle Impostazioni della galleria (sezione «Vetro (solo debug)»).

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
| `material.scene` | `components` `edge` `corner` `nested` `press` `intensity` `list` `menu` `modal` `tints` `apps` `contrast` `rtl` | (elenco) | la scena: vedi «Le scene» |
| `material.backdrop` | `page` `page-light` `page-dark` `white` `black` `text` `photo` | `page` (`nested`, `press`, `menu`, `modal`: `text`; `apps`, `tints`: `photo`) | il fondo sotto le lastre |
| `material.motion` | `still` `scroll` | `still` | il fondo fermo, o che scorre (ogni lastra rifà la cattura a ogni fotogramma) |
| `material.speed` | dp al secondo | `240` | con `scroll` |
| `material.model` | `both` `liquid` `classic` | `both` | i vetri della pagina: i due affiancati (uno sopra l'altro in formato telefono), o uno solo a tutta pagina |
| `material.cycle` | `cycle` `hold` `off` | `cycle` | le scene che si muovono da sole (`nested`, `press`, `intensity`, `list`, `menu`, `modal`): in ciclo; ferme nello stato «acceso» per le foto (dito giù, menu aperto, foglio aperto, `intensity` 1, lista a metà); ferme del tutto |
| `material.period` | secondi | `3` (`list`, `menu`, `modal`: `4`) | la durata di un giro |
| `material.intensity` | `0`…`1`, `cycle` | `cycle` | `intensity` fissa nella scena `intensity` |
| `material.contrast` | `standard` `medium` `high`, `0`…`1`, `all` | `all` | un livello solo nella scena `contrast` |
| `material.hud` | `on` `off` | `on` | il riquadro di stato: **`off` per le foto** |

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
senza manopole l'engine disegna il profilo di serie, identico al banco (`compare --golden liquid` 13 su 13). Nomi e
valori senza distinzione di maiuscole; i numeri col punto. Prima le varianti, poi i numeri (un numero cambia il pezzo
che la variante ha scelto). Una manopola sconosciuta si scarta e la riga `PROFILO` lo dice.

| manopola | valori | la tavola |
|---|---|---|
| `edge` | `ios27` · `ios26` · `today` | «il bordo»: A iOS 27 (filo scuro e riflesso in alto e in basso, di serie) / B iOS 26 (due archi di luce) / C oggi (filetto scuro sopra e sotto) |
| `veil` | `ios27` · `ios26` | «il velo»: A iOS 27 (di serie) / B iOS 26 (bianco 40%). La C «oggi, pellicola» non è un pezzo del profilo: è `gallery.model classic` |
| `shadow` | `ios27` · `ios26` | «l'ombra»: A corta (di serie) / B larga. La C «oggi» è `gallery.model classic` |
| `round` | `bar` · `apple` | «il tondo»: A l'ombra della barra (di serie) / D come Apple (un quarto) |
| `frost` | `r12` · `r24` · `wide` · `ios26` | «la smerigliatura»: A 12 dp (di serie) / B 24 dp / C larga e mista (36 dp con il 12% di nitido) / D iOS 26 (4,5 dp) |
| `lens` | `light` · `medium` · `strong` | «la lente» (decisa: `medium`) |
| `large` | `none` · `step` · `two` | «i grandi» (decisa: `step`) |
| `darkedge` | `reflex` · `wire` · `today` | «il tema scuro», bordo: A solo il riflesso (di serie) / B riflesso e filo chiaro / C com'è oggi |
| `darkveil` | `lighter` · `deeper` | «il tema scuro», velo: A più chiaro (di serie) / B più profondo |
| `rimwidth` | `0..4` (dp) | lo spessore del filo, sui due lati (di serie 0.6) |
| `rimalpha` | `0..1` | la forza del filo scuro (di serie 0.32) |
| `reflex` | `0..1` | la riga chiara (di serie 0.12) |
| `halo` | `0..1` | l'alone sotto la riga (di serie 0.45) |
| `veilalpha` | `0..1` | il velo chiaro (di serie 0.46) |
| `adapt` | `0..1` | quanto il velo si ispessisce dove il fondo gli somiglia (di serie 0.7) |
| `blur` | `0..64` (dp) | la sfocatura di tasti, capsule e barre (i grandi crescono da qui) |
| `mix` | `0..1` | il nitido sopra lo sfocato (di serie 0) |
| `bend` | `0..3` | la lente: lo spostamento in multipli della banda (di serie 0.9) |
| `shadowscale` | `0..8` | le due alfa dell'ombra insieme, sui due lati |
| `darkwire` | `0..1` | un filo bianco che copre, sul lato scuro (la B di «tema scuro» è 0.1) |
| `darkreflex` | `0..1` | la riga chiara sul lato scuro (di serie 0.12) |
| `darkveilalpha` | `0..1` | il velo scuro (di serie 0.5) |

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
l'altra.

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
Un foglio vero dell'host dei modali, aperto e chiuso in ciclo, con lo scrim dietro. Si disegna nell'host della radice:
prende il vetro di `gallery.model`, non delle colonne. Il tasto `apri` lo apre anche a mano.

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

## La mappa: voce del backlog → scena → comando → cosa guardare → esito atteso

Le voci di F1A.6 sono le quattordici del rapporto di F1A.6 («Backlog del dispositivo, in ordine di rischio»), quelle
di F1A.7 e F1A.8 dai loro rapporti (`artifacts/liquid-glass/stato.md`, sezione F1A). Tema chiaro di serie; dove
serve lo scuro, `GALLERIA="--es gallery.theme dark"`.

| voce | scena | comando | cosa guardare | esito atteso |
|---|---|---|---|---|
| 6.1 compilazione e primo fotogramma | `components` | `materiale components hud off; qa_shot "$OUT" componenti-chiaro; adb logcat -d -s FluidGlass:*; stato` | il logcat di `FluidGlass`; la riga `STATO`; la foto | nessuna riga «rifiutata»; `shader … rifiutate=-`; `superfici liquid=` sopra zero; tasto e capsula `Liquid` visibili, non neri né vuoti |
| 6.2 i due disegni della luce | `edge` | `materiale edge hud off; qa_shot "$OUT" bordo-pagina; ritagli bordo-pagina tondo capsula menu`, poi lo stesso con `backdrop white` e con `GALLERIA="--es gallery.theme dark"` | i ritagli a 6x della colonna `Liquid` | sul chiaro una riga chiara in alto e in basso e il filo scuro continuo su tutto il perimetro; sullo scuro solo il riflesso. Se il filo ha il colore della luce, o manca, la piattaforma non fotografa lo shader fra i due disegni come assunto (F1A.3b) |
| 6.3 nodo dell'ombra | `edge`, `press` | `materiale edge backdrop white hud off; qa_shot "$OUT" ombra-bianco; ritagli ombra-bianco capsula menu`; poi il video di 6.6 | il bordo basso dei ritagli; nel video, l'ombra sotto il tasto premuto | ombra corta sotto capsula e pannello, niente sotto la sagoma, nessun rettangolo; premendo, l'ombra cresce col tasto |
| 6.4 ritaglio al 98% | `corner` | `materiale corner hud off; qa_shot "$OUT" angolo; angoli angolo menu-grande; angoli angolo foglio`; poi `backdrop page-dark` e tema scuro | gli angoli a 6x, `Liquid` contro `Classic` | il filo ha lo stesso spessore sulla diagonale e sui lati, nessuna mezzaluna di corpo fuori dal filo. Annotare se il bordo del corpo all'angolo esce morbido sotto il filo (cattura a 0,4) |
| 6.5 vetro annidato | `nested` | `materiale nested hud off; qa_record annidato 14; qa_finish annidato "$OUT"` | i fotogrammi attorno alle righe `PRESSIONE`; gli altri due controlli sul foglio | i tasti rifrangono il corpo del foglio, non il suo filo né il suo bagliore; mentre `conferma` è premuto gli altri non sfarfallano (luminanza piatta nella loro zona) |
| 6.6 pressione | `press` | `materiale press hud off; qa_record premi-chiaro 14; qa_finish premi-chiaro "$OUT"`; poi tema scuro; poi `materiale press cycle hold hud off; qa_shot "$OUT" premuto` | lente, bagliore, filo; i fotogrammi persi (`dropped` in `dumpsys gfxinfo $PKG`); la luminanza sotto il dito nella foto `premuto` | la lente si approfondisce, il bagliore segue il dito, il filo resta pulito sotto il bagliore, nessun fotogramma perso. **Sul chiaro: il punto sotto il dito è +64 o bianco pieno?** (la risposta tara F6) |
| 6.7 lista sotto una barra | `list` | `adb shell dumpsys gfxinfo $PKG reset; materiale list hud off; qa_record lista 20; qa_finish lista "$OUT"; adb shell dumpsys gfxinfo $PKG; adb logcat -d -s FluidGlassDiag` | capsule e tab bar mentre la pagina scorre; le righe `captures=` | le capsule e la barra si aggiornano col contenuto, nessun salto del margine alla soglia di qualità (al più due ricatture per lancio) |
| 6.8 ritorno dallo sfondo | `components` | `materiale components hud off; qa_shot "$OUT" prima-home; adb shell input keyevent 3; sleep 3; adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT"; sleep 3; qa_shot "$OUT" dopo-home` | le due foto | identiche: luce e ombra tornano, nessun contorno di un'altra lastra |
| 6.9 `intensity` | `intensity` | `materiale intensity hud off; qa_record intensita 10; qa_finish intensita "$OUT"`; foto ferme con `intensity 0.25`, `0.5`, `0.75` | i primi fotogrammi della comparsa | una lastra che compare da zero non mostra un rettangolo della pagina vecchia; a metà il contenuto si dissolve come oggi |
| 6.10 barra ancorata e menu da tasto | `menu` | `materiale menu hud off; qa_record menu 18; qa_finish menu "$OUT"`; la barra ancorata: Chrome → «Pagine» (sotto) | il passaggio fra il tasto fermo (`Liquid`) e il viaggio (vetro di prima) | **atteso un cambio di materiale visibile** fino a F1B: annotare quanto si vede, all'apertura e alla chiusura, e se c'è un fotogramma senza vetro |
| 6.11 tab bar flottante | `components` | dalla foto `componenti-chiaro`: `ritagli componenti-chiaro tabbar`; e la barra della galleria in fondo con `gallery.model liquid` | l'ombra sotto il pannello; il puck | ombra nuova corta sotto il pannello; il puck (ancora vetro di prima) sopra il vetro nuovo, senza aloni |
| 6.12 modale | `modal` | `GALLERIA="--es gallery.model liquid" materiale modal hud off; qa_record foglio-liquid 12; qa_finish foglio-liquid "$OUT"`; lo stesso con `gallery.model classic` | lo scrim dietro il foglio, nei due video | lo scrim è identico nei due (resta `Classic`: regola del velo piatto) |
| 6.13 tinte | `tints` | `materiale tints hud off; qa_shot "$OUT" tinte-foto`; poi `backdrop page`, e il tema scuro | indicatore di sincronizzazione, tasto scelto, capsula su foto, `copiata`, `Colored` | l'indicatore ha il suo tono (regola di F1A.8; F1A.6 lo dava perso), il tasto scelto l'accento, la capsula su foto la pellicola di oggi, `copiata` è arancio, `Colored` segue il fondo |
| 6.14 destra-sinistra e orologio | `rtl` | `materiale rtl hud off; qa_shot "$OUT" rtl; ritagli rtl foglio-ltr foglio-rtl` | il foglio con gli angoli diversi, la capsula, la tab bar | in `rtl` gli angoli sono specchiati e filo e ritaglio li seguono; nessun angolo «sbagliato». L'orologio non è nella galleria: resta nel backlog (NV qui) |
| 7.1 i due test strumentati | — | `adb shell pm clear $PKG; adb shell am instrument -w -r -e class dev.antigravity.fluidengine.sample.GlassShaderCompileInstrumentedTest $PKG.test/androidx.test.runner.AndroidJUnitRunner`; lo stesso con `GlassNodeCountInstrumentedTest`; poi `adb logcat -d -s GlassShaderCompile GlassNodeCount FluidGlass` | l'uscita dei due test e il logcat | `OK (3 tests)` per ciascuno; 13 «compila»; `Slab` e `Group` `Done (2/2)`; `Liquid` 4 nodi e 3 layer, senza ombra 3 e 2, `Classic` 5 nodi |
| 7.2 riscaldamento automatico | — | `adb shell pm clear $PKG; adb logcat -c; adb shell am start -S -W -n "$PKG/$ACT"; sleep 5; adb logcat -d -s FluidGlass` | la riga del riscaldamento | «Riscaldamento del vetro: Slab: Done (2/2)…» circa 0,5–1 s dopo che la prima schermata si ferma; nessun `Failed` |
| 7.3 livello simulato | `components` | `GALLERIA="--es gallery.api 32" materiale components; qa_shot "$OUT" api32; stato`; lo stesso con `30`; poi `gallery.page Menus` e `AllGlass` con gli stessi extra | la riga `STATO`; le foto accanto a quelle dell'APK di riferimento 3.1.2 | a 32: `superfici liquid=0`, `perche ApiLevel=…`, niente lente, riflesso piatto, Fluid-physics a `Balanced`, nessuna compilazione AGSL nuova in logcat; a 30: tinta piena, Fluid-physics a `Lite` |
| 7.4 primo fotogramma con e senza riscaldamento | «Misure», `compile` | i comandi «(g bis)» di `../measure/LEGGIMI.md` (`steps engine,body1`, `gallery.model classic`, `gallery.warmup off` e `on`, `pm clear` ogni volta) | le righe `COMPILA … engine-warmup`, `engine-before`, `variant=engine` | `engine-before liquid=0`; spento, `first_render_ms` del passo `engine` porta la compilazione (31–39 più 19–25 ms a freddo); acceso, vicino a quello a caldo (3–6 ms per programma) |
| 7.5 conta dei layer dal vero | `edge` | `materiale edge model liquid; stato`; poi `model classic` | `nodi` e `layer` della riga `STATO` | con tre lastre ferme `Liquid` (più la barra della galleria) i layer per lastra sono 3, e 2 senza ombra; `Classic` 5 nodi (i tre di Kyant non sono contati: limite noto di F1A.7) |
| 8.1 orologio | — | — | — | NV in questa sessione: serve l'orologio. Atteso: identico alla 3.x (`engine-wear` fornisce `Classic`) |
| 8.2 tono dell'indicatore di sincronizzazione | `tints` | come 6.13 | l'indicatore | il tono si vede |
| 8.3 composer | — | — | — | NV qui: il composer è di `engine-ai-ui`, non è nella galleria (PampAI) |
| 8.4 contrasto medio e alto | `contrast` | `materiale contrast hud off; qa_shot "$OUT" contrasto`; poi dal vivo: `adb shell settings put secure contrast_level 0.5`, `qa_shot …`, `1.0`, `qa_shot …`, e **sempre** `adb shell settings put secure contrast_level 0` alla fine | le tre colonne; poi il vetro vero mentre il sistema cambia | sopra lo standard il vetro diventa la pellicola densa di oggi, dal vivo, senza riavviare. Se One UI non propaga `contrast_level`, annotarlo e provare dalle impostazioni a mano (se Alessio vuole) |
| 8.5 pannello di Fluidify | `apps` | `materiale apps hud off; qa_shot "$OUT" app-chiaro`; poi tema scuro | `fluidify-pannello`, `Liquid` contro `Classic` | col materiale nuovo esce **meno** smerigliato di oggi (sfocatura 7 sopra il ruolo): decisione aperta (crescita con `blurScale`). Le copertine vere restano NV: il fondo è la foto generata |
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
tavola bordo-A edge=ios27;  tavola bordo-B edge=ios26;  tavola bordo-C edge=today
tavola velo-A veil=ios27;   tavola velo-B veil=ios26
tavola ombra-A shadow=ios27; tavola ombra-B shadow=ios26; tavola tondo-D round=apple
tavola smeriglio-A frost=r12; tavola smeriglio-B frost=r24; tavola smeriglio-C frost=wide; tavola smeriglio-D frost=ios26
TEMA="--es gallery.theme dark"
tavola scuro-A darkedge=reflex; tavola scuro-B darkedge=wire; tavola scuro-C darkedge=today
tavola velo-scuro-A darkveil=lighter; tavola velo-scuro-B darkveil=deeper
```

Poi i ritagli a 6x del tasto tondo e della capsula di ogni tavola: `riquadro` legge l'ultima riga `RIQUADRO`, quindi
si ritaglia subito dopo ogni foto (o si rilancia la scena). Per la combinazione che Alessio ha scelto, una tavola con
tutte le manopole insieme (`edge=…,veil=…,frost=…`) e la galleria intera con `gallery.tune` (le pagine vere).

## Ordine del giorno proposto

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

## Che cosa la pagina non mostra

- **Il menu e i viaggi** sono ancora Fluid-physics (vetro di prima) fino a F1B, in tutte e due le colonne: la scena
  `menu` guarda il passaggio, non il materiale del menu.
- **Il foglio** della scena `modal` prende il vetro della radice, non delle colonne.
- **La barra in alto** di `FluidScreen` (scena `list`) è `Classic` fino a F2.5.
- **L'ombra** non è nel riscaldamento (F1A.7) e i tre nodi di Kyant del vetro di prima non sono nei conteggi.
- **La foto** è generata in codice: niente copertine vere (8.5), niente colori P3 veri.
- **L'orologio** e **il composer** non sono nella galleria.
- **Il dito finto** è un tocco senza dimensione né pressione variabile, e passa dalla vista della composizione: prova
  il componente, non il percorso del sistema (per quello, `adb shell input swipe x1 y1 x2 y2 1500` sui riquadri).
