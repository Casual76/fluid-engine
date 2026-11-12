# Robustezza F1B.8

Dal catalogo Chrome aprire **Robustezza**. Precedente/Successivo scelgono il caso,
Ripeti riparte dalla sorgente, Automatico scorre i 43 casi e le otto varianti visibili ogni 1,5 secondi.
Il nome resta scritto sopra e sotto il titolo. Nessun caso casuale.

Comandi da eseguire da chi possiede il tablet (qui non sono stati eseguiti):

```powershell
adb shell am start -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity --es gallery.page robustness --es robustness.case stella5-stella12
adb shell am start -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity --es gallery.page robustness --es robustness.auto true
```

Gli extra valgono nella build debug, come quelli delle altre pagine. Chiudere l'attività
prima di un nuovo lancio con extra diversi. Il catalogo unico è
`engine-ui/src/robustness/kotlin/dev/antigravity/fluidengine/ui/fluidphysics/MorphRobustnessCases.kt`, usato dai test `MorphRobustnessTest`.
Le coordinate in pixel sono adattate geometricamente al palco; il fondale non viene scalato.
La pagina mostra anche inversioni e ridirezioni al 25/50/75% e due richieste nello stesso fotogramma.
Frequenze, passo irregolare e passo zero sono esercitati dall'orologio manuale JVM;
la pagina usa l'orologio reale del dispositivo.

Il banco comprende 946 prove: 43 casi × 11 varianti × 2 frequenze. La variante aggiuntiva
`goccia` alimenta direttamente il corpo elastico durante il percorso diretto; le dieci varianti
di interruzione e temporizzazione provano il nucleo geometrico senza corpo elastico.

Casi noti, dichiarati e stampati da `MorphRobustnessTest` (dieci prove saltate, cinque casi
a entrambe le frequenze): `taglia-24-600` e il suo inverso superano la soglia numerica di
continuità con la goccia; `larghezza-zero-inverso`, `altezza-zero-inverso` e
`un-pixel-inverso` producono incroci dell'anello elastico. Non sono stati cambiati i parametri
della goccia condivisi dai preset comuni per forzare questi casi. La pagina li lascia visibili.

La soglia unica del banco è una velocità normalizzata massima di 20/s, moltiplicata per il
passo reale, più 0,001 di tolleranza numerica: copre il picco della molla e la derivata dell'area
su due assi. Area e distanza sono normalizzate rispettivamente al quadrato e alla prima potenza
della scala del viaggio; sfocatura e rifrazione a 50 dp. Il baricentro e l'area delle lastre
sono stimati integrando il campo su celle delimitate dai pezzi, con 12×12 campioni per cella;
per i poligoni si usa lo shoelace. Non è una misura subpixel del raster GPU. All'arrivo si
confrontano anche gli array attivi della forma con il piano di riposo, entro 0,001 px.

## Diagnosi dei dieci noti, senza rimedi nel motore

`MorphRobustnessDiagnosticsTest` ripercorre anche i casi saltati dall'accettazione e stampa
le grandezze separate. I fotogrammi sono numerati da zero; il primo campione è a 16,667 ms
a 60 Hz e a 8,333 ms a 120 Hz. La densità del banco è 1: i pixel delle forme equivalgono a dp.

Nei quattro viaggi 24↔600 supera la soglia **solo la rifrazione della ricetta**, `refractionDp`:
area, baricentro, velo e sfocatura non la superano in nessun fotogramma. Gli allarmi registrati sono:

| Viaggio | Hz | Frame | Tempo ms | Variazione rifrazione dp | Limite dp | Eccesso dp |
|---|---:|---:|---:|---:|---:|---:|
| 24→600 | 60 | 3 | 66,667 | 20,405100 | 16,716667 | 3,688433 |
| 24→600 | 120 | 5 | 50,000 | 9,723818 | 8,383333 | 1,340485 |
| 24→600 | 120 | 6 | 58,333 | 10,589081 | 8,383333 | 2,205748 |
| 600→24 | 60 | 8 | 150,000 | 16,953476 | 16,716667 | 0,236809 |
| 600→24 | 120 | 16 | 141,667 | 8,562250 | 8,383333 | 0,178917 |

Il 24→600 a 120 Hz ha due fotogrammi oltre soglia; il test stampa entrambi e il massimo.
È un superamento del limite sulla velocità del numero della ricetta: questi dati non dimostrano
una discontinuità matematica, né un salto geometrico. Nessun parametro è stato cambiato.

Per le sei contrazioni, nel percorso originale gli incroci compaiono nel tratto finale:

| Destinazione | Hz | Primo frame | Tempo ms | Taglia inseguita al primo incrocio px | Intervallo del lato corto con incroci px |
|---|---:|---:|---:|---|---|
| larghezza zero | 60 | 11 | 200,000 | 0 × 82,223480 | 0 |
| larghezza zero | 120 | 22 | 191,667 | 0 × 82,223480 | 0 |
| altezza zero | 60 | 11 | 200,000 | 82,223480 × 0 | 0 |
| altezza zero | 120 | 21 | 183,333 | 81,438766 × 0 | 0…1 |
| un pixel | 60 | 12 | 216,667 | 1,042046 × 1,042046 | 1,042046 |
| un pixel | 120 | 23 | 200,000 | 1,035545 × 1,035545 | 1,035545…1,042046 |

La taglia inseguita non è la taglia dell'anello deformato: al primo incrocio il suo lato corto
è circa 8…21 px e gli scarti elastici massimi sono 5,54…20,35 px. L'inerzia ha ancora un peso
maggiore del lato finale. L'oltrepasso e il successivo rientro spiegano anche il campione a 1 px
nel caso con altezza finale zero: sotto progresso 1 il lerp tiene il minimo già esistente di 1 px.

Il banco prova poi destinazioni sintetiche con lato minimo 2, 4, 8, 16, 32 e 64 px, attende
la posa della goccia e solo dopo posa la forma originale. **2 px fallisce in tutte e sei le prove**;
4 px fallisce nei due casi a lato zero a entrambe le frequenze e nel caso da un pixel a 120 Hz.
8 px è il primo pavimento provato senza incroci in tutte e sei. Non è una soglia universale:
cambiare il pavimento cambia anche la storia delle molle. Limitare invece soltanto il contorno
inseguito a 2 px, lasciando intatto il viaggio originale, fallisce anch'esso in tutte e sei.

La posa istantanea dopo il pavimento da 8 px toglie circa 640 px² nei casi a lato zero e
63,222 px² nel caso da un pixel, con baricentro sostanzialmente fermo. Quindi anche l'ipotesi
da 8 px deve risolvere il passaggio finale: il banco non la promuove a correzione. Un eventuale
trattamento limitato ai bersagli degeneri potrebbe lasciare fuori i preset comuni; alzare invece
globalmente `MinSlabSidePx` toccherebbe anche i semi da un pixel dei gruppi. Qui non è stato fatto
né l'uno né l'altro, e non è stata verificata la parità dei preset sotto queste ipotesi.
