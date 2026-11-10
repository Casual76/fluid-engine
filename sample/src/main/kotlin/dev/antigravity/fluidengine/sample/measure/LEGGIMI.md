# La pagina «Misure»

Pacchetto F0.6 del piano «iOS in Android» (`docs/liquid-glass/piano.md`, sezione F0). Serve alla prima
sessione sul tablet (S1): sei punti dell'architettura del vetro nuovo si decidono con dei numeri, prima
di scrivere il materiale. Qui ci sono le scene che li producono e i comandi per prenderli senza toccare
lo schermo.

**I prototipi non sono il materiale.** Il corpo è la sonda `sonda_via_a.agsl`, la luce `sonda_luce.agsl`
(le stesse sorgenti di `tools/glassbench/shaders/`, copiate in `sample/src/main/res/raw/` senza
modifiche: cambia solo il nome del file, perché una risorsa non può avere trattini). La striscia del
bordo è `misura_bordo.agsl`, scritta per questa pagina. Cosa di ogni misura è solo indicativo sta in
fondo, in «Dove il prototipo non è la cosa vera».

## Come si lancia

La pagina è la voce `Measure` di `GalleryPage`: si apre dal catalogo della galleria, oppure da riga di
comando con `gallery.page measure`. La scena si sceglie con `measure.scene`; senza, compare l'elenco.
Gli extra si leggono solo nella build di debug. `-S` ferma l'app prima di avviarla: senza, un secondo
lancio riporterebbe davanti la scena di prima.

L'aggancio alla galleria sta fuori da questa cartella, ed è tutto qui: la voce `Measure` in
`GalleryNav.kt`, il ramo che chiama `MeasurePage` in `GalleryActivity.kt`, la riga del catalogo in
`ChromeTab.kt`. Se uno dei tre manca, i comandi qui sotto aprono la galleria e non la pagina.

```bash
PKG=dev.antigravity.fluidglass.debug
ACT=dev.antigravity.fluidengine.sample.GalleryActivity
export MSYS_NO_PATHCONV=1          # Git Bash non deve riscrivere i percorsi del dispositivo

# misura <scena> [chiave valore]...   lancia, aspetta la riga FINE, stampa le righe della misura
misura() {
  local scene="$1"; shift
  local extras=""
  while [ $# -gt 1 ]; do extras="$extras --es measure.$1 $2"; shift 2; done
  adb logcat -c
  adb shell am start -S -W -n "$PKG/$ACT" --es gallery.page measure --es measure.scene "$scene" $extras >/dev/null
  for _ in $(seq 1 180); do
    adb logcat -d -s FluidMisure:I | grep -q "FluidMisure: FINE" && break
    sleep 1
  done
  adb logcat -d -s FluidMisure:I | sed 's/^.*FluidMisure: //'
}
```

Una misura di serie dura 26 secondi: 2 di attesa, poi tre finestre da 8. Lo schermo deve restare
acceso e il dispositivo fermo; a batteria scarica o caldo i numeri cambiano, e si vede nella riga
`SPREAD`.

## Cosa esce

Tutto in logcat con il tag `FluidMisure`, e le ultime righe anche a schermo (`measure.hud off` le
toglie). Ogni riga comincia con una parola e porta la **chiave** della misura, cioè gli extra che
contano per quella scena: due righe con la stessa chiave sono la stessa misura.

| riga | cosa dice |
|---|---|
| `DISPOSITIVO` | modello, livello API, pixel, densità, frequenza dello schermo |
| `AVVIO <chiave>` | la scena è partita, con attesa, durata e numero delle finestre |
| `RIS <chiave> run=i/n` | una finestra: `frames`, `hz`, `budget_ms`, poi per `total_ms`, `ui_ms`, `render_ms`, `gpu_ms` mediana (`p50`), `p90`, `p99` e massimo; `over` e `over_pct` (fotogrammi oltre il bilancio); `dropped` (fotogrammi che il sistema non ha consegnato al contatore: se non è zero la finestra è monca) |
| `SPREAD <chiave>` | la dispersione fra le finestre: minimo/mediana/massimo della mediana e del `p90` |
| `COMPILA ...` | solo la scena `compile`: una riga per variante |
| `FISICA ...` | solo la scena `physics`: il livello chiesto e i verdetti del compilatore sulle sorgenti di Fluid-physics |
| `FINE <chiave>` | la misura è finita: si può leggere |

I tempi vengono da `Window.addOnFrameMetricsAvailableListener`:

- `total_ms` = `TOTAL_DURATION`, dal vsync alla consegna del fotogramma. È quello che si confronta con il bilancio;
- `ui_ms` = ingresso + animazioni + misura + disegno, sul filo principale;
- `render_ms` = sincronizzazione + emissione dei comandi + scambio dei buffer, sul RenderThread. **Il vetro costa qui**: la catena degli effetti gira mentre il RenderThread emette i comandi;
- `gpu_ms` = `GPU_DURATION` (da Android 12; zero se il dispositivo non lo dichiara);
- il bilancio è `DEADLINE` del singolo fotogramma (da Android 12), altrimenti il periodo dello schermo (`budget_ms`).

### Perfetto

L'app di debug è tracciabile. Le finestre sono sezioni asincrone `Misure <scena> i/n`; sul filo
principale ci sono `Misure.catena` (costruzione della catena del prototipo), `Misure.bordo`,
`Misure.compila <variante>`; `Misure.miniatura` è asincrona (la lettura sospende).

```bash
adb shell perfetto -o /data/misc/perfetto-traces/misure.pftrace -t 30s sched freq gfx view -a $PKG &
misura uniforms element capsule mount one animate press
adb pull /data/misc/perfetto-traces/misure.pftrace
```

Per un controllo incrociato dei tempi: `adb shell dumpsys gfxinfo $PKG framestats`.

## Gli extra

Tutti `--es measure.<nome> <valore>`. Un valore non riconosciuto si ignora e resta il default: la
chiave in logcat dice con che cosa la scena è partita davvero.

| extra | valori | default | vale per |
|---|---|---|---|
| `scene` | `base` `compare` `uniforms` `pieces` `edge` `probe` `compile` `physics` | (elenco) | |
| `backdrop` | `text` `photo` `flat` `checker` | `text` (`physics`: `photo`) | tutte |
| `motion` | `scroll` (il fondale scorre: ogni cattura si rifà) · `still` (fondale fermo, lo schermo si ridisegna lo stesso) | `scroll` (`uniforms`, `compile`, `physics`: `still`) | tutte |
| `speed` | dp al secondo | `900` (`edge`: `1800`) | con `motion scroll` |
| `element` | `round` 44×44 · `capsule` 156×56 · `panel` 170×192 r28 · `large` 360×480 r28 · `all` (i primi tre) | `all` (`uniforms`: `capsule`) | `compare`, `uniforms` |
| `glass` | `proto` · `today` (`glassSurface`) · `both` (per guardare) · `none` | `proto` | `compare`, `uniforms`, `pieces` |
| `mount` | `one` · `one-stable` · `two` · `two-composited` (vedi sotto) | `one` | prototipo |
| `light` | `on` `off` | `on` | prototipo |
| `shadow` | `on` `off` (l'ombra a una maschera di oggi; senza ritaglio è sempre spenta) | `on` | prototipo |
| `animate` | `none` · `press` (`refraction`) · `appear` (`materialize` e `w`) · `glow` (solo la luce) | `none` (`uniforms`: `press`) | `compare`, `uniforms`, `pieces` |
| `pieces` | `1`…`8` | `1` | `pieces` |
| `variant` | `max` (la sonda a 8 pezzi con uscita anticipata) · `fit` (la variante più piccola: 1, 2, 4, 8) | `max` | `pieces` |
| `scale` | `1` · `0.5` (risoluzione della cattura) | `1` | elementi, `edge` |
| `clip` | `on` (ritaglio hardware sulla sagoma) · `off` (ritaglia lo shader, margine di 8 dp) | `on` (`pieces`: `off`) | prototipo |
| `blur` | dp | `12` (`edge`: `14`, raggio massimo al bordo) | prototipo, `edge` |
| `dispersion` | `0`…`1` | `1` | prototipo |
| `strip` | `two-pass` · `today` (la fascia `FadeDown`, solo in alto) · `none` | `two-pass` | `edge` |
| `edges` | `both` `top` `bottom` | `both` | `edge` |
| `height` | dp | `96` | `edge` |
| `period` | millisecondi fra due letture | `120` | `probe` |
| `thread` | `main` · `mixed` (lettura sul principale, copia dei pixel su un filo di lavoro) · `background` | `main` | `probe` |
| `probes` | `1`…`4` | `1` | `probe` |
| `side` | lato della miniatura in pixel | `16` | `probe` |
| `tier` | `auto` `full` `balanced` `lite` | `auto` | `physics` |
| `journey` | `square` (cerchio ↔ quadrato) · `panel` (capsula ↔ pannello) · `pieces` (due tondi ↔ pannello) | `square` | `physics` |
| `seconds` | durata di una finestra | `8` | tutte tranne `compile` |
| `warmup` | attesa prima della prima finestra | `2` (`compile`: `3`) | tutte |
| `runs` | numero di finestre | `3` | tutte tranne `compile` |
| `hud` | `on` `off` | `on` | tutte |

Tema chiaro o scuro: `--es gallery.theme light|dark`, come per il resto della galleria. Il formato
telefono si ottiene dal dispositivo (`adb shell wm size 1080x2340`, e `wm size reset` alla fine).

### I quattro montaggi del corpo

È la variabile del cancello C1 (piano 3.5). In tutti la catena è quella della via A:
`colorControls(saturazione)` → `blur(Clamp)` → `createBlendModeEffect(Plus, w·nitido, (1−w)·sfocato)` → lo
shader del corpo con un ingresso; la luce è un `drawRect` sopra, fuori dalla catena.

| `mount` | layer | cosa succede quando cambia un uniform del corpo |
|---|---|---|
| `one` | cattura con tutta la catena | la catena si ricostruisce per intero: è ciò che fa oggi ogni superficie dell'engine |
| `one-stable` | come `one` | i nodi a monte dello shader restano gli stessi oggetti; cambia solo il nodo dello shader. Se costa come `two`, Skia riusa la sfocatura (leva 4 di R3 cap. 9) |
| `two` | primo layer: saturazione, sfocatura, mescola. Secondo layer: lo shader | si riesegue solo lo shader. Ma `w` sta nel primo layer: la comparsa rifà la sfocatura |
| `two-composited` | primo layer: sfocatura; accanto, un layer nitido. La mescola `w` è l'alfa dei due, la saturazione sta nel secondo layer con lo shader | nemmeno la comparsa rifà la sfocatura. Costa un layer nitido in più mentre `w` > 0 |

## Le scene, una per una

```bash
# base: il palco senza vetro. Va presa per ogni fondale e ogni moto che si usa: è ciò che si sottrae.
misura base
misura base motion still
misura base backdrop photo

# (b) corpo + luce contro il vetro di oggi, sugli stessi tre elementi, sopra un fondale che scorre
misura compare glass today
misura compare glass proto
misura compare glass proto light off          # quanto pesa la luce quando la catena gira
misura compare glass proto mount two          # quanto pesa il layer in più
misura compare glass today motion still       # a riposo: il corpo esce dalla cache
misura compare glass proto motion still
misura compare glass proto motion still light off   # il costo della luce a riposo (3.5)
misura compare glass both backdrop photo hud off    # da guardare e fotografare, non da misurare

# (c) soli uniform a cattura ferma, nei montaggi a uno e a due layer
for el in round capsule panel; do
  for mount in one one-stable two two-composited; do
    for anim in press appear; do
      misura uniforms element $el mount $mount animate $anim
      misura uniforms element $el mount $mount animate $anim scale 0.5
    done
  done
  misura uniforms element $el glass today animate press    # quanto costa oggi la stessa pressione
  misura uniforms element $el animate glow                  # solo la luce: il corpo non deve muoversi
done

# (d) 1, 2, 4, 8 pezzi nello stesso campo
for n in 1 2 4 8; do
  misura pieces pieces $n                    # la sonda a 8 con uscita anticipata
  misura pieces pieces $n variant fit        # la variante scelta per conteggio
  misura pieces pieces $n glass today        # oggi: n lastre separate
done

# (e) la striscia del bordo, in alto e in basso, durante uno scorrimento veloce
misura edge                                  # due passate, risoluzione piena
misura edge scale 0.5                        # due passate, metà risoluzione (la proposta del piano)
misura edge strip today edges top            # la fascia di oggi (solo in alto)
misura edge edges top scale 0.5              # la sua pari: una striscia sola
misura edge strip none                       # il pavimento a questa velocità
# poi tutto di nuovo in formato telefono: adb shell wm size 1080x2340 ... adb shell wm size reset

# (f) la lettura della miniatura
for thread in main mixed background; do
  for period in 100 150 250; do
    misura probe thread $thread period $period
    misura probe thread $thread period $period probes 4
  done
done

# (g) la prima compilazione. A freddo: svuotare la cache dei programmi (cancella anche le impostazioni)
adb shell pm clear $PKG
misura compile            # a freddo
misura compile            # a caldo: i programmi arrivano dal disco

# (h) Fluid-physics, Full contro Balanced
for journey in square panel pieces; do
  misura physics journey $journey tier full
  misura physics journey $journey tier balanced
done
```

La scena `probe` aggiunge alla riga `RIS`: `snapshot_ms` (il tempo di `toImageBitmap()`, con `n` =
quante letture), `read_ms` (la copia dei pixel in memoria più la media), `failed` (letture cadute) e
`luma` (l'ultima luminanza letta da ogni sonda: se resta `-` la lettura non funziona).

La scena `compile` scrive una riga `COMPILA` per variante (`body1`, `light1`, `body2`, … `edge`):
`build_ms` è la costruzione del programma (`RuntimeShader(sorgente)`, sul filo principale);
`first_total_ms`, `first_ui_ms`, `first_render_ms`, `first_gpu_ms` sono il fotogramma peggiore fra
quelli in cui la variante entra in scena; `steady_total_ms` è la mediana dei fotogrammi che seguono. La
riga `variant=engine-all` è il tempo di `FluidGlassDiagnostics.compileAllShaders()` e l'elenco delle
sorgenti dell'engine rifiutate (solo quelle che nessuno aveva ancora provato in quel processo).

## Cancello → scena → cosa leggere

Il piano non dà soglie in millisecondi per i cancelli. La regola è quella dei bilanci (F7): **stare nel
periodo dello schermo al 90° percentile**. In ogni riga `RIS` è `total_ms p90` contro `budget_ms`
(8,33 ms a 120 Hz), con `over_pct` come controprova; dove la baseline di oggi sfora già, vale «non
peggio della scena `glass today`».

| cancello | scena | cosa leggere | va bene se | allora |
|---|---|---|---|---|
| **C1** costo dei gesti | `uniforms`, `mount one` contro `two`, `animate press` e `appear`, su `round`, `capsule`, `panel`, a `scale 1` e `0.5` | `total_ms p90`, `render_ms p50/p90`, `over_pct` | `mount one` sta nel bilancio sulle lastre che ne hanno bisogno | corpo a un layer; altrimenti a due (e `one-stable` dice se basta tenere fermi i nodi) |
| **C2** Fluid-physics a `Full` | `physics`, `tier full` contro `balanced`, sui tre viaggi | `total_ms p90`, `over_pct`, la riga `FISICA` (verdetti `ok`); più le registrazioni dei componenti veri | `full` sta nel bilancio su una finestra da menu e non mostra difetti suoi | resta come in 3.8; altrimenti il ripiego di 3.8 |
| **C3** pezzi | `pieces`, 1, 2, 4, 8 | `render_ms` e `gpu_ms p50` in rapporto a un pezzo (la stima di R3 è 2,4 : 5,3 : 8,2 per 1 : 4 : 8), `total_ms p90` a 8 | 8 pezzi stanno nel bilancio | tetto 8, soglia 4; altrimenti tetto 4 |
| **C4** bordo dello scorrimento | `edge`, `scale 1` e `0.5`, a 2560 px e in formato telefono | `total_ms p90`, `over_pct`, contro `strip today` e `strip none` | le due passate stanno nel bilancio durante lo scorrimento | due passate; altrimenti tre sfocature native incrociate |
| **C5** sonda | `probe`, `period 100`–`150`, `probes 1` e `4`, i tre `thread` | `snapshot_ms`, `read_ms`, e `over_pct` contro la scena `base` | la lettura non aggiunge fotogrammi oltre il bilancio a 100–150 ms con quattro sonde | `toImageBitmap()`; altrimenti `HardwareRenderer` + `ImageReader`, o frequenza più bassa |
| **C6** prima compilazione | `compile`, a freddo e a caldo | `build_ms` e `first_total_ms` di ogni variante, e la loro somma | la somma a freddo si nasconde nell'avvio | riscaldamento all'avvio; altrimenti pigro per famiglia e meno varianti |

## Dove il prototipo non è la cosa vera

- **Shader.** Le sonde hanno i termini principali del corpo (campo, lente, dispersione a tre campioni,
  velo, ombreggiatura, maschera) e della luce, non il velo adattivo, il raccordo continuo degli angoli,
  la taratura. Il costo degli shader definitivi si rimisura in S2.
- **Ombra.** È quella di oggi a una maschera (`Shadow`), non il nodo a due maschere del piano. Senza
  ritaglio (`clip off`, e sempre in `pieces`) non c'è.
- **Ritaglio.** Il nodo di `drawBackdrop` mette sempre il proprio ritaglio hardware: `clip off` lo
  rende un rettangolo più largo della sagoma, non lo spegne.
- **La luce** si disegna in `onDrawSurface`, cioè dentro il materiale che la superficie esporterebbe:
  il parametro `onDrawLight` del piano non esiste ancora. Qui nessuno campiona quel materiale, quindi
  la misura non cambia.
- **Pezzi.** `variant max` usa sempre il programma a otto pezzi con l'uscita su `pieceCount`; `variant
  fit` cambia la sola costante `MAX_PIECES`, ma `Body1` del piano non avrà il ciclo. Il costo vero di un
  pezzo solo è sotto quello misurato.
- **Bordo.** `misura_bordo.agsl` ha il conto dei campioni del piano (20 coppie più il centro per
  passata) ma calcola i pesi per campione e non ha lo scurimento sul contenuto scuro: il costo è una
  stima per eccesso, la resa non si giudica. La striscia non esporta se stessa ai tasti.
- **Scorrimento.** Il fondale scorre traslando un layer: il filo principale non ridisegna niente.
  È ciò che isola il RenderThread, e non è una lista vera (là si paga anche la registrazione della
  pagina a ogni fotogramma).
- **Miniatura.** La lettura non è agganciata alla revisione della sorgente né allo schedulatore: è un
  ciclo a periodo fisso. Misura il costo di una lettura, non la politica.
- **Fluid-physics.** Il livello si forza solo sulla superficie che crea la scena: menu, menu che si
  trasforma e barra chiamano `fluidPhysicsSurface` dentro l'engine e non espongono il livello. Per loro
  il confronto si fa con i due APK di riferimento di F0.7 (la 3.1.2 com'è uscita gira a `Balanced`).
- **Il resto della galleria** è sullo schermo: la barra delle schede (vetro di oggi, fermo) e gli host
  dei modali. È un costo costante, che la scena `base` porta con sé e che quindi si sottrae.
- **Prima compilazione.** Il corpo si compila due volte: una per prendere `build_ms`, una dentro la
  cache dell'engine al primo disegno, e quella seconda finisce in `first_ui_ms`. `first_render_ms` e
  `first_gpu_ms` sono il programma della GPU. A caldo i programmi arrivano dal disco.
