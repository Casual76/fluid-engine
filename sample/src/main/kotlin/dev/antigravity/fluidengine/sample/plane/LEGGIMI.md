# Piano flottante (F3.6)

La pagina dove si guarda, e si riprende dal tablet, ciò che la fase F3 ha dato all'engine: il contenitore del piano
flottante (`FluidGlassContainer`, `Modifier.fluidGlass` e i suoi attributi), il contenuto che si sfoca e si incrocia
(`FluidGlassContentTransition`), le forme concentriche e il giro uniforme di un contenitore. Dal catalogo Chrome aprire
**Piano flottante**, oppure da riga di comando.

Nove scene, un `FluidGlassContainer` ciascuna. **Ogni scena cambia solo lo stato**: un `if`, una distanza, un margine o
il fondo. Nessuna animazione è scritta qui: chi nasce da chi, chi compare per lente, chi si fonde, il collo, la
comparsa e il giro li decide il contenitore. Lo stato si cambia col tasto in fondo ai comandi (il comando della scena,
che dice lo stato di adesso), toccando i vetri dove sono tasti, o da solo con `plane.auto`.

```powershell
$A = "dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity"
adb shell am start -S -W -n $A --es gallery.page plane
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene uno-due --es plane.auto true
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene capsule --es plane.auto 2500 --es plane.spacing larga
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene comparsa --es plane.auto true --es plane.theme scuro
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene interruzione --es plane.auto 3000
adb logcat -s FluidPiano
```

(`$A` è una variabile di PowerShell; in `bash` va scritta `A=…` e `$A`.) Gli extra valgono solo nella build di debug:
chiudere l'attività prima di un nuovo lancio con extra diversi (`-S` la ferma e la riparte).

## Gli extra

| extra | valori | cosa fa |
|---|---|---|
| `gallery.page` | `plane` | apre la pagina (sopra Chrome: «Indietro» torna al catalogo) |
| `plane.scene` | `uno-due` (di serie), `capsule`, `comparsa`, `unione`, `contesto`, `adattamento`, `clear`, `concentriche`, `interruzione`, `molti` | la scena |
| `plane.theme` | `chiaro`, `scuro` | il tema della pagina; di serie quello della galleria (`gallery.theme`) |
| `plane.spacing` | `stretta` (0 dp), `media` (16 dp, di serie), `larga` (40 dp), o un numero di dp | lo `spacing` del contenitore della scena |
| `plane.fusion` | `stretta` (8 dp), `media` (16 dp), `larga` (24 dp, fusi anche a riposo) | le tre distanze della tavola «distanza di fusione» di F3.3, per **tutti** i contenitori della pagina (`FluidGlassContainerDiagnostics.fusionPreview`): per riprenderle sul tablet. Alessio ha scelto la media (2026-11-16) |
| `plane.model` | `liquid`, `classic` | il vetro chiesto; con `classic` (e sotto Android 13) il contenitore non disegna e non viaggia: ogni pezzo è il vetro di sempre |
| `plane.auto` | `true` (ogni 2000 ms), un numero di ms (da 300), `false` | il comando della scena a intervalli, per le riprese; senza, **la pagina non si muove** |
| `plane.interrupt` | `true` (150 ms), un numero di ms, `false` | il comando cambia lo stato **tre volte**, a questo passo: due cambi a metà viaggio. Di serie acceso solo in `interruzione` |

**Lento.** Non c'è un extra della pagina: il modo che l'engine ha già è la scala delle animazioni di sistema, che il
contenitore, i viaggi di Fluid-physics e il contenuto che si sfoca leggono tutti e tre.
`adb shell settings put global animator_duration_scale 5` (poi **rimetterla a 1**). La pagina la legge (la scrive nella
riga `SCENA` e nel sottotitolo) e allunga allo stesso modo i suoi tempi: il passo dell'interruzione e l'intervallo
dell'automatico, perché «a metà viaggio» resti a metà. A scala zero non c'è nessun viaggio (movimento ridotto).

Un valore che non si riconosce lascia quello di serie. Il tema, lo spacing, il vetro e la scena cambiati dai tasti
rifanno la scena da capo, con lo stato di partenza.

## Le scene

| scena | stato (pari / dispari) | cosa si guarda |
|---|---|---|
| `uno-due` | «Modifica» solo / con «Fatto» accanto | «Fatto» **nasce** da «Modifica» (è entro lo spacing): parte sovrapposto e se ne stacca col collo che si strozza; al ritorno vi **confluisce**. È il caso tipo della scheda |
| `capsule` | divise (96 dp) / accostate (0 dp) | due capsule con il loro `id` (stesso pezzo, si spostano): accostandosi il collo nasce e le unisce, allontanandosi si strozza e si rompe. **A riposo due pezzi restano separati** anche accostati (scelta di Alessio, 2026-11-11): il collo esiste solo in viaggio |
| `comparsa` | due tasti / tre, il terzo all'altro capo della riga | il tasto lontano **compare per lente** al suo posto: contorno da poco più largo (×1,09), rifrazione che sale, 430 ms; se ne va in 470 ms. Il corpo non sfuma mai |
| `unione` | tre tasti distinti a 8 dp / con `fluidGlassUnion` | un blocco di tasti: distinti sono già un grappolo (una cattura) ma **tre vetri separati del tutto a riposo**; con l'unione sono **una sagoma sola, ferma** (F3.2c, la raccomandazione della scheda per i blocchi di tasti) |
| `contesto` | contesto A (indietro, condividi, altro) / B (indietro, modifica, elimina) e «Fatto» | le icone che cambiano si sfocano e si **incrociano** al massimo della sfocatura; l'icona comune (indietro) resta **nitida**; «Fatto» nasce dalla barra |
| `adattamento` | foto chiara / foto scura | tre vetri **fermi** (tre grappoli, una sonda sola) sopra una foto che cambia: girano tutti **nello stesso fotogramma**. Nessun viaggio: cambia il fondo, non il piano |
| `clear` | foto chiara / foto scura | `Clear` accanto a `Regular` sopra la foto, nel contenitore (in alto) e fuori (sotto, vetri a sé che non girano). `Clear` oggi è il ponte di F3.2: scurimento 35% e smerigliatura a zero |
| `concentriche` | margini 8/16/24 dp / 18/26/34 dp | un pannello di vetro con `fluidContainerShape` (raggio 38 dp) e dentro tre vetri `FluidConcentricShape(4.dp)`: i raggi seguono il margine (38 − margine, mai sotto 4 né oltre mezzo lato), e al cambio i vetri viaggiano coi raggi |
| `interruzione` | come `uno-due` | il comando cambia lo stato tre volte a 150 ms: il viaggio **riparte da dove si trova** due volte, senza scatti e senza un secondo viaggio in coda |
| `molti` | passo stretto / largo | una riga di quattro tasti e una di otto uniti, che cambiano passo insieme: **4 e 8 pezzi in volo** (gli otto uniti sono un campo solo, il palco più caro). Per il costo del palco in S3 |

Le scene sul palco stanno sopra la foto sintetica della galleria (la stessa di «Adattività»); nelle scene del fondo
(`adattamento`, `clear`) un velo chiaro o scuro, disegnato e non ricomposto: il vetro resta fermo e cambia ciò che ha dietro.

## Cosa scrive in logcat

Tag `FluidPiano`. I tempi sono `SystemClock.uptimeMillis()`. I riquadri dei pezzi sono in px **del contenitore**; dove
sta il contenitore nella finestra lo dice `RIQUADRO`.

| riga | quando | cosa dice |
|---|---|---|
| `SCENA` | al lancio e a ogni cambio di scena, tema, spacing o vetro | la scena, il tema, lo spacing, il vetro chiesto, l'API, la scala delle animazioni, automatico e interruzione |
| `STATO scena passo=n stato t` | a ogni cambio di stato (comando, automatico, tocco) | lo stato nuovo in parole e l'istante: da qui si contano i tempi del viaggio |
| `VIAGGIO partito\|ripartito` | quando il contenitore apre (o fa ripartire) un viaggio | dopo quanti ms dal cambio, i contatori (viaggi, ripartenze) e chi va dove: `da->a:Cambio(vicino)` con `Matched`, `BornFromNeighbour(da x)`, `MergeIntoNeighbour(in x)`, `Materialize`, `Dematerialize`, `Identity` |
| `FINE dopo n ms` | quando il riposo d'arrivo è pubblicato | la durata dalla partenza (preciso a 16 ms: si legge a ogni passo di 16 ms, non a ogni fotogramma) e i pezzi a riposo: `id[x,y lxa r=tl/tr/br/bl g=grappolo]` |
| `RIPOSO` | il primo riposo, e un riposo senza viaggio (un aspetto cambiato) | i pezzi a riposo; con `RIQUADRO`, 1,2 s dopo la scena, anche i contenitori vivi, le sonde vive e gli avvisi d'ordine |
| `RIQUADRO contenitore` | 1,2 s dopo la scena | il riquadro del contenitore in px della finestra |
| `NESSUNA-FINE` | un viaggio che dopo 10 s (per la scala) non ha pubblicato l'arrivo | un difetto dell'engine da riferire |

La pagina legge i contatori di `FluidGlassContainerDiagnostics` **solo dopo un cambio e finché il viaggio non arriva**,
con un `delay` che non chiede fotogrammi. Ferma, non scrive niente e non chiede fotogrammi (`GlassProbeQuietTest` la
conta come le altre pagine). I contatori sono del processo: con due contenitori in scena (la scena `clear` ha un
contenitore solo; gli altri vetri sono fuori) le righe li sommano.

## Cosa provano i test

`PlanePageTest` (Robolectric) apre ogni scena, preme il suo comando e controlla i viaggi dichiarati: «Fatto» nasce da
«Modifica» e vi confluisce, le capsule accostate sono un grappolo, il tasto lontano compare e scompare per lente,
l'unione fa un grappolo solo, «Fatto» nasce dalla barra, il fondo che cambia non apre viaggi e la sonda è una, `Clear` e
`Regular` sono due grappoli, i raggi concentrici seguono i margini, il comando che interrompe fa un viaggio e due
ripartenze, e in nessuna scena c'è un avviso d'ordine. `PlanePureTest` prova gli extra e chi legge i contatori. Come si
vede lo dicono solo le riprese: l'elenco per la sessione S3 è in `artifacts/liquid-glass/sessioni/f3-6/riprese-S3.md`.
La scheda dell'engine è `skill/fluid-engine/references/componenti/piano-flottante.md`.
