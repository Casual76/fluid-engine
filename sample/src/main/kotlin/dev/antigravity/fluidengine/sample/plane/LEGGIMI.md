# Piano flottante (F3.6)

La pagina dove si guarda, e si riprende dal tablet, ciò che la fase F3 ha dato all'engine: il contenitore del piano
flottante (`FluidGlassContainer`, `Modifier.fluidGlass` e i suoi attributi), il contenuto che si sfoca e si incrocia
(`FluidGlassContentTransition`), le forme concentriche e il giro uniforme di un contenitore. Dal catalogo Chrome aprire
**Piano flottante**, oppure da riga di comando.

Diciassette scene, un `FluidGlassContainer` ciascuna (due in `molti` e in `piu-contenitori`). **Ogni scena cambia solo lo
stato**: un `if`, una distanza, un margine, una misura o il fondo. Nessuna animazione del vetro è scritta qui: chi nasce da chi, chi compare per lente, chi si fonde, il collo, la
comparsa e il giro li decide il contenitore. Lo stato si cambia col tasto in fondo ai comandi (il comando della scena,
che dice lo stato di adesso), toccando i vetri dove sono tasti, o da solo con `plane.auto`.

```powershell
$A = "dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity"
adb shell am start -S -W -n $A --es gallery.page plane
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene uno-due --es plane.auto true
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene capsule --es plane.auto 2500 --es plane.spacing larga
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene comparsa --es plane.auto true --es plane.theme scuro
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene interruzione --es plane.auto 3000
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene due-nati --es plane.auto 2500
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene bordo --es plane.auto 2500
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene tre-vicini --es plane.auto 2500
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene lista --es plane.auto 3000
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene testo --es plane.auto 2500
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene piu-contenitori --es plane.auto 2500
adb shell am start -S -W -n $A --es gallery.page plane --es plane.scene ridimensiona --es plane.auto 2500
adb logcat -s FluidPiano
```

(`$A` è una variabile di PowerShell; in `bash` va scritta `A=…` e `$A`.) Gli extra valgono solo nella build di debug e
nella **`bench`** (la release misurabile, non debuggable ma profilabile: pacchetto `dev.antigravity.fluidglass.bench`,
al posto di `…fluidglass.debug` in `$A`; come costruirla e installarla in
`artifacts/liquid-glass/sessioni/build-bench/LEGGIMI.md`); la galleria dello store non li legge. Chiudere l'attività
prima di un nuovo lancio con extra diversi (`-S` la ferma e la riparte).

## Gli extra

| extra | valori | cosa fa |
|---|---|---|
| `gallery.page` | `plane` | apre la pagina (sopra Chrome: «Indietro» torna al catalogo) |
| `plane.scene` | `uno-due` (di serie), `capsule`, `comparsa`, `unione`, `contesto`, `adattamento`, `clear`, `concentriche`, `interruzione`, `molti`, e dal giro di correzione di F3 `due-nati`, `bordo`, `tre-vicini`, `lista`, `testo`, `piu-contenitori`, `ridimensiona` | la scena |
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
| `clear` | foto chiara / foto scura | `Clear` accanto a `Regular` sopra la foto, nel contenitore (in alto) e fuori (sotto, vetri a sé che non girano). Dalla F3.2c `Clear` è la **variante vera** della ricetta (non più il ponte di F3.2): lo sfondo arriva nitido per la mescola a monte, **col raggio di sfocatura che resta**, con uno scurimento fisso del 35% e senza adattività; fra limpido e di serie cambia un peso e la cattura non si rifà |
| `concentriche` | margini 8/16/24 dp / 18/26/34 dp | un pannello di vetro con `fluidContainerShape` (raggio 38 dp) e dentro tre vetri `FluidConcentricShape(4.dp)`: i raggi seguono il margine (38 − margine, mai sotto 4 né oltre mezzo lato), e al cambio i vetri viaggiano coi raggi |
| `interruzione` | come `uno-due` | il comando cambia lo stato tre volte a 150 ms: il viaggio **riparte da dove si trova** due volte, senza scatti e senza un secondo viaggio in coda |
| `molti` | passo stretto / largo | una riga di quattro tasti e una di otto uniti, che cambiano passo insieme: **4 e 8 pezzi in volo** (gli otto uniti sono un campo solo, il palco più caro). Per il costo del palco in S3 |
| `due-nati` | un tasto / tre | «Seleziona» (una capsula con testo) si sposta a destra e lascia nel suo posto due tondi, «Condividi» e «Elimina»: **nascono tutti e due da lui**, dalla stessa parte, e l'orologio li rilascia a due istanti diversi (**il più lontano dal suo arrivo per primo**, 0,09 del viaggio prima: ~49 ms). Il donatore è largo e si sposta perché i nuovi devono stare entro lo spacing dal suo posto di *prima* (con un donatore fermo ne nasce uno solo per lato). Tornando, confluiscono tutti e due in «Seleziona» |
| `bordo` | senza i nuovi / con i nuovi | il contenitore è **stretto attorno alla riga**, come nelle barre vere: due righe della stessa larghezza, a 24 dp una dall'altra. Sopra «Modifica» è l'ultimo tasto e ne nasce «Preferiti» **verso il bordo** (sta sul bordo del contenitore, che si allarga); sotto «Condividi» è l'ultimo e ne nasce «Cerca» **verso l'interno** (si infila prima di lui, e il donatore va al bordo). Si guardano il superamento della molla e la spinta, che il riquadro del contenitore tagliava (review di F3, A 2) |
| `tre-vicini` | tre tasti / quattro | tre tasti tondi a 8 dp fra i vetri **senza unione** (un grappolo, tre vetri). Il terzo, «Modifica», fa nascere «Elimina» alla sua destra; «Cerca» e «Condividi» **non cambiano e restano al loro vetro di riposo** (non passano dal palco e non si uniscono perché un terzo compare). Solo il terzo è un comando |
| `lista` | un tasto / due, la lista scorre | un contenitore **fermo, attorno alla barra** (in un angolo), sopra una lista di 40 voci colorate che è la sorgente del vetro. A ogni cambio di stato la **lista scorre da sola** per ~1,6 s (giù se lo stato è acceso, su se è spento: ~1000 dp/s), così il viaggio parte con la lista già in moto e la cattura del palco si rinfresca a ogni fotogramma. È l'unica scena in cui qualcosa si muove senza il vetro: prima del primo cambio e a riposo la lista sta ferma e non chiede fotogrammi. Per il costo di scorrimento: i framestats di un viaggio qui contro quelli di `uno-due` |
| `testo` | scritta corta / lunga | un tondo fermo («Indietro») e un tasto con testo («Salva», poi «Salva e chiudi la raccolta») con lo stesso `id`: la capsula cambia taglia **per la scritta**. Si guarda il contenuto nuovo, già della misura d'arrivo, ritagliato sulla sagoma in volo (la scheda lo dichiara «da vedere sul tablet»), e che il tondo fermo non senta il viaggio del vicino |
| `piu-contenitori` | entrambi a riposo / entrambi accesi | due contenitori nella stessa pagina, ognuno col suo namespace: la barra di `uno-due` in alto e la capsula di `contesto` (con le icone che si sfocano) a 64 dp sotto. Un solo comando li cambia insieme: **due viaggi, una sonda ciascuno**, inchiostri indipendenti, nessun intreccio |
| `ridimensiona` | stretto (200 dp) / largo (340 dp) | tre tasti ai due capi e al centro di una riga che riempie il contenitore; cambia **solo la larghezza del contenitore**, senza cambiare pezzi né stato (una rotazione, uno schermo diviso, la tastiera). Per la regola del pacchetto nucleo del giro di correzione i pezzi seguono il contenitore **senza un viaggio e senza luce**; senza la regola parte un viaggio che riparte a ogni misura |

Le scene sul palco stanno sopra la foto sintetica della galleria (la stessa di «Adattività»); nelle scene del fondo
(`adattamento`, `clear`) un velo chiaro o scuro, disegnato e non ricomposto: il vetro resta fermo e cambia ciò che ha dietro.
`lista` ha la sua sorgente (la lista) dentro la scena. **Le sette scene dopo `molti`** (`due-nati`, `bordo`, `tre-vicini`,
`lista`, `testo`, `piu-contenitori`, `ridimensiona`) sono del giro di correzione di F3 (review di F3: A 13, D R6, C R10):
provano ciò che le prime dieci non toccavano. Cosa riprendere sul tablet, comando per comando e con l'esito atteso:
`artifacts/liquid-glass/sessioni/f3-corr-scene/riprese.md`.

**Dalla F3.8a**, in tutte le scene: il contenuto di un pezzo si vede solo dentro il suo vetro («Fatto» arriva sfocato
dentro il vetro dopo metà viaggio, e al ritorno si sfoca dov'era; il simbolo di `comparsa` è ritagliato sul tondo); chi
non cambia resta al suo vetro di riposo (in `comparsa` i due tasti fermi non si uniscono mai); in `unione` il comando
apre un viaggio del collo (300 ms), non un fotogramma; i vetri che viaggiano si gonfiano nel mezzo. Cosa riprendere:
`artifacts/liquid-glass/sessioni/f3-8a/riprese.md`.

**Dalla F3.10**: nel mezzo di ogni viaggio il vetro **si illumina e si fa lattiginoso** (la luce «a metà» del carattere
del viaggio) e, quando il cambio parte da un tocco su un vetro della scena (i tasti tondi della scena fanno anche da
comando: «Modifica» in `uno-due`, i tasti di `comparsa` e di `molti`), il bagliore della pressione riempie quel vetro e lo
accompagna (la luce del dito). Il comando sotto la scena e `plane.auto` cambiano lo stato senza toccare un vetro del
contenitore: lì la luce del dito non c'è, ed è il confronto. Le manopole in `gallery.tune`: `journey=off|gentle|half|apple`,
`finger=on|off`. La pagina non annuncia più il disegno del fondo a mano: `adattamento` e `clear` girano perché la
sorgente dell'engine lo dice da sé. Cosa riprendere: `artifacts/liquid-glass/sessioni/f3-10/riprese.md`.

## Cosa scrive in logcat

Tag `FluidPiano`. I tempi sono `SystemClock.uptimeMillis()`. I riquadri dei pezzi sono in px **del contenitore**; dove
sta il contenitore nella finestra lo dice `RIQUADRO`.

| riga | quando | cosa dice |
|---|---|---|
| `SCENA` | al lancio e a ogni cambio di scena, tema, spacing o vetro | la scena, il tema, lo spacing, il vetro chiesto, l'API, la scala delle animazioni, automatico e interruzione |
| `STATO scena passo=n stato t` | a ogni cambio di stato (comando, automatico, tocco) | lo stato nuovo in parole e l'istante: da qui si contano i tempi del viaggio |
| `VIAGGIO partito\|ripartito` | quando il contenitore apre (o fa ripartire) un viaggio | dopo quanti ms dal cambio, i contatori (viaggi, ripartenze) e chi va dove: `da->a:Cambio(vicino)` con `Matched`, `BornFromNeighbour(da x)`, `MergeIntoNeighbour(in x)`, `Materialize`, `Dematerialize`, `Identity`; `dito=` (F3.10) il bagliore da cui comincia il campo del vetro toccato, `0.00` se il cambio non è partito da un tocco su un vetro del contenitore |
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
ripartenze, e in nessuna scena c'è un avviso d'ordine. Per le sette del giro di correzione: due nati dallo stesso
donatore (che confluiscono tornando), i due casi del bordo e i tasti sul bordo, il terzo tasto che fa nascere il quarto
con i due fermi che non nascono né confluiscono, la lista che scende da sola e risale (un viaggio per cambio), la
capsula che si allarga per la scritta col tasto fermo dov'era, due viaggi e due sonde in due contenitori, il contenitore
che si allarga di 140 dp con i pezzi agli stessi `id`. `PlanePureTest` prova gli extra e chi legge i contatori. Come si
vede lo dicono solo le riprese: l'elenco per la sessione S3 è in `artifacts/liquid-glass/sessioni/f3-6/riprese-S3.md`.
La scheda dell'engine è `skill/fluid-engine/references/componenti/piano-flottante.md`.
