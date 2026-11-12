# Robustezza F1B.8

**Giro di correzione R10 eseguito:** costruttore JUnit corretto (quattro argomenti, nessun
valore di default), 3956 percorsi effettivamente avanzati fino al completamento. La verifica
completa ha zero fallimenti; 2260 percorsi passano tutti i criteri e 1696 sono noti dichiarati,
stampati e saltati DOPO le asserzioni obbligatorie di finitezza e completamento. L'elenco
statico e' stato ripulito dei falsi allarmi eliminati dalla verifica geometrica 96?96.
I numeri della sezione storica restano risultati del F1B.8 iniziale.

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

## Cosa certifica la serie (giro di correzione R10)

Il banco parametrico comprende 3956 percorsi: 43 coppie dirette/inverse, 23 varianti,
60/120 Hz, chiaro/scuro. Le dieci varianti originarie senza goccia restano; `goccia` e
`goccia-inverti25/50/75`, `goccia-terza25/50/75`, `goccia-doppio`, `goccia-irregolare`,
`goccia-zero` alimentano il corpo elastico. Le tre `goccia-interrompi25/50/75` cancellano
la coroutine al checkpoint e lasciano posare la goccia sulla forma congelata. Nei gruppi la goccia non viene inventata:
lo stato la ammette soltanto quando restituisce una lastra singola.

Il primo campione viene confrontato con il riposo preso PRIMA di morphTo. Si controllano
anche il cambio istantaneo di destinazione e le due richieste allo stesso istante. I numeri
osservati sono area, due coordinate del baricentro, RGBA del velo, sfocatura, rifrazione e
forza di ombra di contatto/ambiente (alfa del profilo moltiplicata per alfa del colore).
La miscela ottica del banco eredita il proprio valore a ogni ridirezione; non simula un
reset artificiale delle ottiche. La diagnosi S2 usa invece le ottiche Floating costanti
24/40 dp della pagina, densita' 2, palchi sintetici di 1000 e 1440 px: queste taglie sono
scenari di sensibilita', non una misura del viewport del video.

La velocita' normalizzata massima **20/s e' solo un controllo di esplosione**. Non esclude
scatti visibili. Il nuovo controllo misura la variazione delle velocita' di due intervalli
consecutivi, divisa per la distanza fra i rispettivi punti medi: accelerazione su tre campioni,
anche con un passo irregolare. Il limite e' 400 scale/s?: la molla di default (risposta circa
0,36 s) ha omega? circa 305/s?; il margine e' dichiarato, non un limite percettivo. Al passo
zero si richiede lo stesso valore entro la tolleranza, senza dividere per zero. Il banco
ha controlli negativi: un tratto lineare veloce passa; un impulso che passa la sicura 20/s
viene segnalato dall'accelerazione. La tolleranza per colore e' un livello su 255 per ciascuna
misura (due differenze nell'accelerazione), per le altre grandezze 0,001 normalizzato.
Area e distanza sono normalizzate al quadrato e alla prima potenza della scala del viaggio;
sfocatura e rifrazione a 50 dp. La quadratura delle lastre usa celle delimitate dai pezzi,
24?24 campioni per cella; i poligoni usano lo shoelace. Ogni allarme geometrico viene ricontrollato a 96?96 sugli stessi due/tre piani congelati:
se non supera piu' lo stesso limite viene classificato come errore di quadratura e scartato,
non come noto del motore. La fascia del collo esterna ai riquadri dei pezzi e' compresa.
Non e' una misura subpixel del raster.

Finitudine dei numeri/array, limiti di pezzi e vertici, completamento e addormentamento
rimangono asserzioni obbligatorie. Incroci, verso, esplosione e accelerazione vengono
raccolti per tutto il percorso: i noti sono un elenco fisso in `MorphRobustnessKnownTest.kt`,
non una registrazione automatica. Un nuovo rilievo fuori elenco fallisce. Un caso noto viene
stampato e saltato DOPO aver percorso il viaggio e controllato completamento e finitezza.
Il numero di test verdi non comprende i casi noti saltati.

La certificazione riguarda questi campioni numerici e geometrici, non la resa a schermo,
il raster AGSL/HWUI, l'osservazione/reinstallazione degli effetti Compose, l'antialias, la
leggibilita', il costo GPU o la perfetta fedelta' agli istanti del video. Un contorno semplice
puo' essere molto concavo e apparire arrotolato. I limiti numerici non promettono invisibilita'
percettiva dello scatto. L'arrivo finale e' confrontato con un piano costruito DALLO STESSO
MOTORE: e' un controllo di completamento, non un oracolo indipendente della sagoma desiderata.

## Esiti e classificazione del giro unico

Verifica finale: `:engine-ui:testDebugUnitTest` 5721 test, zero falliti, 1696 saltati
soltanto dopo l'esecuzione dei noti dichiarati. I 6 test di controllo/diagnosi aggiuntivi
sono verdi. `:sample:testDebugUnitTest` 730 test, zero falliti, 16 saltati (eseguito nella
prima verifica completa; UP-TO-DATE nell'ultima ripetizione). Nessun dispositivo.

Registro di questa ripresa, sempre `lg-f1b-cr` e posto rilasciato: raccolta iniziale 128 s,
fallita su 1642 rilievi non ancora dichiarati; raccolta con oracolo corretto 245 s, 1734
rilievi non dichiarati; verifica dell'elenco 245 s, fallita per una riga vuota nel caricatore
dei noti (errore del banco corretto); prima verifica completa 321 s, riuscita; ripetizione
completa con elenco ripulito 294 s, riuscita. I precedenti tentativi da 68/84 s si erano
fermati nella compilazione esterna di `PhysicsVeilGoalTest`, prima dell'esecuzione.

La classificazione per percorso e' esclusiva e segue questa priorita'; il record del singolo
caso conserva TUTTI i criteri osservati, non soltanto la categoria principale.

| Categoria | Percorsi esclusi | Significato e diagnosi |
|---|---:|---|
| Geometria | 72 | Incroci e/o verso invertito dell'anello. 70 traiettorie riguardano lati zero/un pixel, 2 il 600?24 con un passo da 100 ms a 120 Hz, nei due temi. La goccia conserva inerzia mentre il riferimento collassa; `LiquidDrop.step` limita il dt integrato a 1/30 s ma riceve il riferimento avanzato di 100 ms (`FluidPhysicsLiquid.kt:174?222`). I momenti di un poligono incrociato non sono una sagoma fisica valida: i successivi picchi del baricentro non sono difetti indipendenti. Nessun rimedio applicato. |
| Ritargeting | 538 | Variazioni sopra tolleranza allo stesso istante, persistenti al controllo 96?96, nella forma o nel materiale. `currentForm`/`formAt` ricostruiscono una forma dai parametri e il nuovo viaggio ricalcola campo/campioni (`FluidPhysicsState.kt:415,1086,1211`; `FluidPhysicsRings.kt:38,252`). Non tutti i rilievi sono dichiarati visibili sul raster. Esempio: cerchio?lastra inverti25, area normalizzata a t=58,333 ms cambia di 0,00464253 a 24?24 e 0,00464192 a 96?96. |
| Sola soglia di accelerazione | 964 | Il limite conservativo 400/s? viene superato senza un salto istantaneo sopra tolleranza misurato. La motivazione omega? circa 305/s? riguarda la partenza da ferma e non costituisce un tetto universale per ridirezioni e derivate della ricetta. Questi sono limiti della certificazione richiesta, NON 964 difetti del motore dimostrati e non una taratura percettiva. La soglia non e' stata alzata per farli passare. |
| Sicura di velocita' | 56 | Refrazione oltre 20/s (oltre ad eventuali allarmi di accelerazione), senza anello invalido o cambio istantaneo. Numeri finiti e arrivo corretti: un allarme di velocita' non dimostra una discontinuita' matematica. |
| Posa attesa | 66 | Cancellazione -> forma ferma -> posa immediata del velo, come il contratto corretto `largeVeilWeight`/`settle` (`FluidPhysicsEffects.kt:981?997`). In scuro compaiono cambi RGB anche di 8?20 livelli a passo zero. Non e' una regressione del nuovo contratto; quel salto intenzionale resta escluso da una promessa di materiale temporalmente continuo. Gli altri eventuali criteri restano elencati nel record. |

La tabella fissa `MorphRobustnessKnownTest.kt` consente soltanto le chiavi e i criteri effettivamente
osservati, senza registrazione automatica durante i test. Un criterio nuovo fallisce. Le
esenzioni dei noti non sono un limite sull'ampiezza di un futuro peggioramento dello stesso
criterio: i noti NON sono certificati come corretti. Finitezza e completamento rimangono obbligatori.

Errori del banco corretti: costruttore parametrico con default; tinta `Fixed` trasparente al
posto del velo `Neutral`; campo del collo troncato ai riquadri dei pezzi; piccoli falsi salti
geometrici dell'integratore 24?24. La verifica 96?96 scarta 124 allarmi in 108 percorsi e
riabilita 38 percorsi interi. Nel caso raggi-asimmetrici/inverti25 i salti istantanei di area
e baricentro spariscono a 48/96: errore dell'oracolo, non nuovo noto del motore. La sola
accelerazione del medesimo caso resta sopra limite anche raffinando. Il test di convergenza
stampa tutti i confronti a 12/24/48/96; il test negativo distingue il tratto veloce dall'impulso.

Il collo vivo sui pezzi compenetrati richiede di includere la fascia esterna nella quadratura;
non si ripristina l'atteso vecchio di fusione nulla. Il velo segue il contratto nuovo a riposo:
il banco e' stato adeguato, non il motore. `PhysicsDriveHeading` riguarda la guida esterna,
mentre questa matrice usa `morphTo`: non certifica tutte le guide bidirezionali dei menu.
La creazione pigra dei layer d'ombra non e' provata dai numeri della ricetta: nessuna
conclusione su allocazioni, passate o costo GPU viene ricavata da questi test.

## Incidenti S2: esecuzione JVM del giro unico

Eseguiti gli stessi casi e gesti in scuro, goccia attiva e ottiche Floating 24/40 dp,
a 60/120 Hz, densita' 2 e palchi sintetici di 1000/1440 px. Le dimensioni sono una prova
di sensibilita': non sono una misura del viewport del tablet. Concavita' = quota di area
mancante rispetto all'inviluppo convesso; 1% e' un indicatore diagnostico, non un difetto
ne' una soglia percettiva. Gli otto percorsi hanno zero incroci, numeri finiti e completano.

| Caso | Hz | Palco | Cambio di meta ms | Primo episodio concavita' >1% ms | Massimo | Uscita dal palco |
|---|---:|---:|---:|---|---:|---|
| un-pixel-inverso / inverti50 | 60 | 1000 | 100,000 | 66,667?133,333 | 4,18% | prima a 366,667 ms, fuori dall'episodio |
| un-pixel-inverso / inverti50 | 120 | 1000 | 91,667 | 50,000?125,000 | 4,67% | prima a 341,667 ms, fuori dall'episodio |
| un-pixel-inverso / inverti50 | 60 | 1440 | 100,000 | 66,667?116,667 | 2,88% | prima a 366,667 ms |
| un-pixel-inverso / inverti50 | 120 | 1440 | 91,667 | 58,333?108,333 | 3,10% | prima a 341,667 ms |
| taglia-24-600-inverso / terza75 | 60 | 1000 | 133,333 | 66,667?150,000 | 5,86% | mai |
| taglia-24-600-inverso / terza75 | 120 | 1000 | 125,000 | 50,000?116,667 | 5,91% | mai |
| taglia-24-600-inverso / terza75 | 60 | 1440 | 133,333 | 66,667?116,667 | 7,57% | mai |
| taglia-24-600-inverso / terza75 | 120 | 1440 | 125,000 | 58,333?108,333 | 7,63% | mai |

Il secondo caso ha anche episodi successivi: sul palco 1000/120 Hz, 133,333?158,333 e
200,000?333,333 ms. Quindi non si forza una coincidenza di durata con il video. Nei due
percorsi 1000/120 Hz il modo passa da lastre ad anello a 16,667 ms e torna a lastre solo a
866,667 / 825,000 ms: nessun cambio temporaneo di nucleo durante l'arrotolamento.
La concavita' e' riprodotta qualitativamente, l'autointersezione no. Durante il primo episodio
il palco contiene la forma; il clipping della forma sul bordo del nodo non spiega quel residuo.
L'ombra intera puo' comunque uscire dal nodo: e' un'altra misura, non dedotta dai soli bounds.

Nel ritargeting `FluidPhysicsState.kt:415?417` cattura `transit.formAt`, non l'anello elastico;
`FluidPhysicsLiquid.kt:530?560` aggiunge gli scarti ancora vivi alla nuova sagoma inseguita.
Le forze e lo smorzamento dipendono dall'ancora (`:197?222`): e' il percorso che produce la
risposta concava/asimmetrica, non un contorno che cambia verso in questi due ingressi.

Il velo neutro e' ora misurato davvero: il vecchio `GlassTint` trasparente di default era
`Fixed` e forzava alfa zero (errore del banco corretto con `Neutral`). Il corpo limita il velo
alla copertura del campo (`GlassBodyShader.kt:213?215,259,291?293`): fuori dalla sagoma la strada
prevista restituisce solo l'ombra. La maschera d'ambiente dell'anello usa un rettangolo
sostitutivo corretto con un rapporto di cadute (`GlassShadow.kt:522?534`). Questo resta un
candidato per il residuo rettangolare. JVM non esegue il raster AGSL/HWUI e non verifica la
reinstallazione della catena degli effetti: non attribuisco con certezza la luminosita' del
rettangolo del video a quell'approssimazione, al clipping o a uniform rimasti indietro.

### Tracce complete a 120 Hz, palco 1000 px

Gli istanti ripetuti sono il campione prima e dopo la ridirezione allo stesso tempo, non
fotogrammi aggiunti. Le tracce degli altri palchi/frequenze e tutti gli uniform sono stampati
nel system-out di `MorphRobustnessTabletTest` (XML Gradle), riproducibili con `--tests *MorphRobustnessTabletTest`.


<details><summary>un-pixel-inverso/inverti50 120Hz palco=1000.0 densita=2</summary>

```text
frame;ms;progress;cambio;modo;incroci;concavita_percento;fuori_palco;bounds_px;velo_RGBA;ombra_contatto_ambiente
-1;0.000;1.0;false;0;0;0.000;false;Rect.fromLTRB(16.0, 137.0, 984.0, 863.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
0;8.333;0.0;false;0;0;0.000;false;Rect.fromLTRB(16.0, 137.0, 984.0, 863.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
1;16.667;0.009193948;false;1;0;0.016;false;Rect.fromLTRB(16.0, 137.0, 983.8, 862.8);[0.5019608, 0.21960784, 0.21960784, 0.24313726];[0.0, 0.21600002]
2;25.000;0.03463405;false;1;0;0.084;false;Rect.fromLTRB(16.0, 137.0, 982.6, 861.9);[0.5019608, 0.22745098, 0.22745098, 0.2509804];[0.0, 0.21600002]
3;33.333;0.07890095;false;1;0;0.265;false;Rect.fromLTRB(16.0, 137.0, 979.4, 859.4);[0.5019608, 0.23529412, 0.23529412, 0.25882354];[0.0, 0.21600002]
4;41.667;0.12908666;false;1;0;0.603;false;Rect.fromLTRB(15.9, 137.0, 972.9, 854.6);[0.5019608, 0.23921569, 0.23921569, 0.26666668];[0.0, 0.21597888]
5;50.000;0.18685903;false;1;0;1.143;false;Rect.fromLTRB(15.9, 137.0, 962.0, 846.9);[0.5019608, 0.24705882, 0.24705882, 0.27058825];[0.0, 0.21507736]
6;58.333;0.25814417;false;1;0;1.959;false;Rect.fromLTRB(15.9, 137.0, 945.0, 835.4);[0.5019608, 0.25490198, 0.25490198, 0.2784314];[0.0, 0.21239191]
7;66.667;0.32492918;false;1;0;3.028;false;Rect.fromLTRB(15.9, 137.0, 921.2, 819.6);[0.5019608, 0.2627451, 0.2627451, 0.28627452];[0.0, 0.20866384]
8;75.000;0.39316183;false;1;0;4.148;false;Rect.fromLTRB(15.9, 137.0, 890.1, 799.5);[0.5019608, 0.27058825, 0.27058825, 0.29411766];[0.0, 0.20402756]
9;83.333;0.46985805;false;1;0;4.666;false;Rect.fromLTRB(15.9, 137.0, 851.2, 774.9);[0.5019608, 0.2784314, 0.2784314, 0.3019608];[0.0, 0.198293]
10;91.667;0.53664017;false;1;0;4.027;false;Rect.fromLTRB(15.9, 137.0, 806.0, 746.4);[0.5019608, 0.28235295, 0.28235295, 0.30980393];[0.0, 0.19325821]
10;91.667;0.0;true;1;0;4.027;false;Rect.fromLTRB(15.9, 137.0, 806.0, 746.4);[0.5019608, 0.28235295, 0.28235295, 0.30980393];[0.0, 0.19325821]
11;100.000;0.0;true;1;0;3.201;false;Rect.fromLTRB(15.9, 137.0, 758.5, 714.6);[0.5019608, 0.2784314, 0.2784314, 0.3019608];[0.0, 0.19325821]
12;108.333;-0.012771803;true;1;0;2.461;false;Rect.fromLTRB(16.0, 137.0, 711.4, 681.0);[0.5019608, 0.27058825, 0.27058825, 0.29411766];[0.0, 0.19275463]
13;116.667;-0.0054128217;true;1;0;1.799;false;Rect.fromLTRB(16.0, 137.0, 667.0, 648.0);[0.5019608, 0.2627451, 0.2627451, 0.28627452];[0.0, 0.19304432]
14;125.000;0.02278884;true;1;0;1.184;false;Rect.fromLTRB(16.0, 137.0, 627.7, 617.9);[0.5019608, 0.25490198, 0.25490198, 0.2784314];[0.0, 0.19416542]
15;133.333;0.062149417;true;1;0;0.659;false;Rect.fromLTRB(16.0, 137.0, 595.0, 592.2);[0.5019608, 0.24705882, 0.24705882, 0.27058825];[0.0, 0.19575271]
16;141.667;0.11201391;true;1;0;0.306;false;Rect.fromLTRB(16.0, 137.0, 569.5, 571.6);[0.5019608, 0.23921569, 0.23921569, 0.26666668];[0.0, 0.19778506]
17;150.000;0.17748843;true;1;0;0.146;false;Rect.fromLTRB(16.0, 137.0, 551.6, 556.9);[0.5019608, 0.23529412, 0.23529412, 0.25882354];[0.0, 0.2004537]
18;158.333;0.2415004;true;1;0;0.085;false;Rect.fromLTRB(16.0, 137.0, 540.9, 547.8);[0.5019608, 0.22745098, 0.22745098, 0.2509804];[0.0, 0.20301639]
19;166.667;0.3088919;true;1;0;0.064;false;Rect.fromLTRB(16.0, 137.0, 536.7, 543.9);[0.5019608, 0.21960784, 0.21960784, 0.24313726];[0.0, 0.20561047]
20;175.000;0.386588;true;1;0;0.056;false;Rect.fromLTRB(16.0, 137.0, 538.5, 545.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.20839542]
21;183.333;0.45568585;true;1;0;0.057;false;Rect.fromLTRB(16.0, 137.0, 545.4, 550.3);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21061923]
22;191.667;0.5235268;true;1;0;0.065;false;Rect.fromLTRB(16.0, 137.0, 556.8, 561.2);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21250871]
23;200.000;0.59709245;true;1;0;0.093;false;Rect.fromLTRB(16.0, 136.8, 571.9, 576.3);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21416023]
24;208.333;0.6591503;true;1;0;0.150;false;Rect.fromLTRB(16.0, 136.5, 590.5, 593.7);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21517846]
25;216.667;0.7174341;true;1;0;0.227;false;Rect.fromLTRB(15.7, 136.2, 612.4, 612.3);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21577989]
26;225.000;0.77150214;true;1;0;0.295;false;Rect.fromLTRB(15.5, 135.9, 636.1, 631.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21599738]
27;233.333;0.82692593;true;1;0;0.357;false;Rect.fromLTRB(15.2, 135.6, 661.3, 651.3);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
28;241.667;0.8712107;true;1;0;0.404;false;Rect.fromLTRB(15.0, 135.4, 687.3, 671.2);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
29;250.000;0.91536844;true;1;0;0.438;false;Rect.fromLTRB(14.8, 135.2, 713.7, 691.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
30;258.333;0.9496434;true;1;0;0.459;false;Rect.fromLTRB(14.6, 135.0, 740.1, 710.5);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
31;266.667;0.97936934;true;1;0;0.473;false;Rect.fromLTRB(14.4, 134.8, 766.5, 729.5);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
32;275.000;1.0076146;true;1;0;0.471;false;Rect.fromLTRB(14.2, 134.7, 792.3, 747.8);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
33;283.333;1.0283698;true;1;0;0.437;false;Rect.fromLTRB(13.9, 134.5, 817.2, 765.2);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
34;291.667;1.0453182;true;1;0;0.382;false;Rect.fromLTRB(12.6, 134.0, 841.0, 781.5);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
35;300.000;1.0602123;true;1;0;0.345;false;Rect.fromLTRB(10.6, 132.4, 863.4, 796.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
36;308.333;1.0700932;true;1;0;0.346;false;Rect.fromLTRB(8.4, 130.2, 884.2, 810.4);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
37;316.667;1.0771505;true;1;0;0.344;false;Rect.fromLTRB(6.0, 128.0, 903.5, 822.9);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
38;325.000;1.081707;true;1;0;0.339;false;Rect.fromLTRB(3.5, 125.9, 921.0, 834.1);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
39;333.333;1.0842371;true;1;0;0.327;false;Rect.fromLTRB(1.1, 124.1, 936.9, 844.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
40;341.667;1.0845151;true;1;0;0.309;true;Rect.fromLTRB(-0.9, 122.6, 951.4, 852.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
41;350.000;1.0832382;true;1;0;0.287;true;Rect.fromLTRB(-2.7, 121.4, 964.1, 860.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
42;358.333;1.0802791;true;1;0;0.284;true;Rect.fromLTRB(-4.1, 120.5, 975.2, 866.3);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
43;366.667;1.0765702;true;1;0;0.276;true;Rect.fromLTRB(-5.1, 120.0, 984.6, 871.7);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
44;375.000;1.0714772;true;1;0;0.262;true;Rect.fromLTRB(-5.7, 119.8, 992.6, 876.3);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
45;383.333;1.0663561;true;1;0;0.242;true;Rect.fromLTRB(-5.9, 119.9, 999.0, 879.9);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
46;391.667;1.0608631;true;1;0;0.222;true;Rect.fromLTRB(-5.8, 120.4, 1004.2, 882.5);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
47;400.000;1.0544342;true;1;0;0.229;true;Rect.fromLTRB(-5.3, 120.9, 1008.4, 884.4);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
48;408.333;1.048647;true;1;0;0.246;true;Rect.fromLTRB(-4.6, 121.6, 1011.5, 885.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
49;416.667;1.0429106;true;1;0;0.266;true;Rect.fromLTRB(-3.7, 122.2, 1013.7, 886.1);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
50;425.000;1.0366344;true;1;0;0.288;true;Rect.fromLTRB(-3.3, 122.6, 1015.0, 886.4);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
51;433.333;1.0312973;true;1;0;0.311;true;Rect.fromLTRB(-2.7, 122.8, 1015.5, 886.7);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
52;441.667;1.0262493;true;1;0;0.329;true;Rect.fromLTRB(-2.0, 123.3, 1015.4, 886.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
53;450.000;1.0209705;true;1;0;0.340;true;Rect.fromLTRB(-1.1, 123.8, 1014.7, 886.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
54;458.333;1.0166677;true;1;0;0.349;true;Rect.fromLTRB(-0.1, 124.5, 1013.6, 885.1);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
55;466.667;1.0127511;true;1;0;0.353;true;Rect.fromLTRB(1.0, 125.2, 1012.1, 884.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
56;475.000;1.0088168;true;1;0;0.354;true;Rect.fromLTRB(2.3, 126.1, 1010.4, 882.7);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
57;483.333;1.005739;true;1;0;0.348;true;Rect.fromLTRB(3.6, 127.0, 1008.5, 881.2);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
58;491.667;1.0030478;true;1;0;0.338;true;Rect.fromLTRB(4.9, 127.9, 1006.5, 879.7);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
59;500.000;1.0004656;true;1;0;0.325;true;Rect.fromLTRB(6.3, 128.9, 1004.4, 878.1);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
60;508.333;0.9985463;true;1;0;0.306;true;Rect.fromLTRB(7.6, 129.8, 1002.3, 876.5);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
61;516.667;0.996958;true;1;0;0.283;true;Rect.fromLTRB(8.8, 130.8, 1000.2, 874.9);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
62;525.000;0.99567664;true;1;0;0.257;false;Rect.fromLTRB(10.0, 131.7, 998.1, 873.3);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
63;533.333;0.9945696;true;1;0;0.230;false;Rect.fromLTRB(11.0, 132.5, 996.1, 871.8);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
64;541.667;0.9938533;true;1;0;0.202;false;Rect.fromLTRB(12.0, 133.2, 994.1, 870.3);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
65;550.000;0.99336064;true;1;0;0.175;false;Rect.fromLTRB(12.8, 133.9, 992.3, 868.9);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
66;558.333;0.99304056;true;1;0;0.149;false;Rect.fromLTRB(13.6, 134.5, 990.5, 867.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
67;566.667;0.9929355;true;1;0;0.124;false;Rect.fromLTRB(14.2, 135.1, 988.9, 866.5);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
68;575.000;0.9929733;true;1;0;0.101;false;Rect.fromLTRB(14.8, 135.6, 987.5, 865.4);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
69;583.333;0.99315774;true;1;0;0.080;false;Rect.fromLTRB(15.2, 136.0, 986.2, 864.4);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
70;591.667;0.99342364;true;1;0;0.061;false;Rect.fromLTRB(15.6, 136.3, 985.0, 863.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
71;600.000;0.9937649;true;1;0;0.044;false;Rect.fromLTRB(15.9, 136.6, 984.0, 862.8);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
72;608.333;0.9942162;true;1;0;0.033;false;Rect.fromLTRB(16.1, 136.8, 983.1, 862.2);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
73;616.667;0.99465966;true;1;0;0.025;false;Rect.fromLTRB(16.3, 137.0, 982.4, 861.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
74;625.000;0.99512804;true;1;0;0.018;false;Rect.fromLTRB(16.3, 137.2, 981.8, 861.2);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
75;633.333;0.9956695;true;1;0;0.013;false;Rect.fromLTRB(16.3, 137.1, 981.3, 860.9);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
76;641.667;0.9961521;true;1;0;0.010;false;Rect.fromLTRB(16.2, 137.1, 981.0, 860.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
77;650.000;0.99668515;true;1;0;0.007;false;Rect.fromLTRB(16.2, 137.1, 980.7, 860.5);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
78;658.333;0.99714226;true;1;0;0.006;false;Rect.fromLTRB(16.2, 137.1, 980.6, 860.4);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
79;666.667;0.9975778;true;1;0;0.005;false;Rect.fromLTRB(16.1, 137.0, 980.5, 860.4);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
80;675.000;0.9980366;true;1;0;0.004;false;Rect.fromLTRB(16.1, 137.0, 980.5, 860.5);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
81;683.333;0.9984132;true;1;0;0.004;false;Rect.fromLTRB(16.1, 137.0, 980.6, 860.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
82;691.667;0.9987583;true;1;0;0.004;false;Rect.fromLTRB(16.0, 137.0, 980.7, 860.8);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
83;700.000;0.9991075;true;1;0;0.005;false;Rect.fromLTRB(16.0, 137.0, 980.9, 860.9);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
84;708.333;0.99938273;true;1;0;0.005;false;Rect.fromLTRB(16.0, 137.0, 981.1, 861.1);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
85;716.667;0.99962527;true;1;0;0.005;false;Rect.fromLTRB(16.0, 137.0, 981.3, 861.3);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
86;725.000;0.9998601;true;1;0;0.005;false;Rect.fromLTRB(16.0, 137.0, 981.5, 861.5);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
87;733.333;1.0000365;true;1;0;0.005;false;Rect.fromLTRB(16.0, 137.0, 981.8, 861.7);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
88;741.667;1.0001842;true;1;0;0.005;false;Rect.fromLTRB(16.0, 137.0, 982.0, 861.8);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
89;750.000;1.0003184;true;1;0;0.005;false;Rect.fromLTRB(16.0, 137.0, 982.3, 862.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
90;758.333;1.0004117;true;1;0;0.005;false;Rect.fromLTRB(15.9, 137.0, 982.5, 862.2);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
91;766.667;1.0004829;true;1;0;0.004;false;Rect.fromLTRB(15.9, 136.9, 982.7, 862.3);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
92;775.000;1.0;true;1;0;0.004;false;Rect.fromLTRB(15.9, 136.9, 983.0, 862.5);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
93;783.333;1.0;true;1;0;0.004;false;Rect.fromLTRB(15.9, 136.9, 983.1, 862.6);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
94;791.667;1.0;true;1;0;0.004;false;Rect.fromLTRB(15.9, 137.0, 983.3, 862.7);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
95;800.000;1.0;true;1;0;0.004;false;Rect.fromLTRB(15.9, 137.0, 983.5, 862.8);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
96;808.333;1.0;true;1;0;0.004;false;Rect.fromLTRB(16.0, 137.0, 983.6, 862.9);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
97;816.667;1.0;true;1;0;0.004;false;Rect.fromLTRB(16.0, 137.0, 983.7, 862.9);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
98;825.000;1.0;true;1;0;0.004;false;Rect.fromLTRB(16.0, 137.0, 983.8, 863.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
99;833.333;1.0;true;1;0;0.004;false;Rect.fromLTRB(16.0, 137.0, 983.9, 863.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
100;841.667;1.0;true;1;0;0.003;false;Rect.fromLTRB(16.0, 137.0, 983.9, 863.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
101;850.000;1.0;true;1;0;0.003;false;Rect.fromLTRB(16.0, 137.0, 984.0, 863.1);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
102;858.333;1.0;true;1;0;0.003;false;Rect.fromLTRB(16.0, 137.0, 984.0, 863.1);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
103;866.667;1.0;true;0;0;0.000;false;Rect.fromLTRB(16.0, 137.0, 984.0, 863.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
```

</details>

<details><summary>taglia-24-600-inverso/terza75 120Hz palco=1000.0 densita=2</summary>

```text
frame;ms;progress;cambio;modo;incroci;concavita_percento;fuori_palco;bounds_px;velo_RGBA;ombra_contatto_ambiente
-1;0.000;1.0;false;0;0;0.000;false;Rect.fromLTRB(16.0, 62.1, 937.9, 984.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
0;8.333;0.0;false;0;0;0.000;false;Rect.fromLTRB(16.0, 62.1, 937.9, 984.0);[0.5019608, 0.21176471, 0.21176471, 0.23529412];[0.0, 0.21600002]
1;16.667;0.009193948;false;1;0;0.032;false;Rect.fromLTRB(16.0, 62.1, 937.8, 983.9);[0.5019608, 0.21960784, 0.21960784, 0.24313726];[0.0, 0.21600002]
2;25.000;0.03463405;false;1;0;0.143;false;Rect.fromLTRB(15.8, 61.9, 937.0, 983.1);[0.5019608, 0.22745098, 0.22745098, 0.2509804];[0.0, 0.21600002]
3;33.333;0.07890095;false;1;0;0.391;false;Rect.fromLTRB(15.7, 61.8, 934.7, 980.8);[0.5019608, 0.23529412, 0.23529412, 0.25882354];[0.0, 0.21600002]
4;41.667;0.12908666;false;1;0;0.792;false;Rect.fromLTRB(15.5, 61.6, 929.8, 975.9);[0.5019608, 0.23921569, 0.23921569, 0.26666668];[0.0, 0.21600002]
5;50.000;0.18685903;false;1;0;1.398;false;Rect.fromLTRB(15.4, 61.5, 921.5, 967.6);[0.5019608, 0.24705882, 0.24705882, 0.27058825];[0.0, 0.21600002]
6;58.333;0.25814417;false;1;0;2.305;false;Rect.fromLTRB(15.3, 61.4, 908.6, 954.7);[0.5019608, 0.25490198, 0.25490198, 0.2784314];[0.0, 0.21600002]
7;66.667;0.32492918;false;1;0;3.466;false;Rect.fromLTRB(15.2, 61.3, 890.3, 936.4);[0.5019608, 0.2627451, 0.2627451, 0.28627452];[0.0, 0.21598688]
8;75.000;0.39316183;false;1;0;4.735;false;Rect.fromLTRB(15.3, 61.4, 866.5, 912.6);[0.5019608, 0.27058825, 0.27058825, 0.29411766];[0.0, 0.21435702]
9;83.333;0.46985805;false;1;0;5.581;false;Rect.fromLTRB(15.4, 61.5, 836.5, 882.6);[0.5019608, 0.2784314, 0.2784314, 0.3019608];[0.0, 0.20989855]
10;91.667;0.53664017;false;1;0;4.906;false;Rect.fromLTRB(15.6, 61.7, 801.0, 847.1);[0.5019608, 0.28235295, 0.28235295, 0.30980393];[0.0, 0.20450673]
11;100.000;0.6010867;false;1;0;3.525;false;Rect.fromLTRB(15.9, 62.0, 760.6, 806.7);[0.5019608, 0.2901961, 0.2901961, 0.31764707];[0.0, 0.19862488]
12;108.333;0.6698325;false;1;0;2.408;false;Rect.fromLTRB(15.9, 62.0, 716.2, 762.3);[0.5019608, 0.29803923, 0.29803923, 0.3254902];[0.0, 0.1922969]
13;116.667;0.72694755;false;1;0;1.545;false;Rect.fromLTRB(15.9, 62.0, 668.7, 714.8);[0.5019608, 0.30588236, 0.30588236, 0.33333334];[0.0, 0.18750608]
14;125.000;0.7798625;false;1;0;0.972;false;Rect.fromLTRB(15.9, 62.0, 620.7, 666.8);[0.5019608, 0.3137255, 0.3137255, 0.3372549];[0.0, 0.18383245]
14;125.000;0.0;true;1;0;0.972;false;Rect.fromLTRB(15.9, 62.0, 620.7, 666.8);[0.5019608, 0.3137255, 0.3137255, 0.3372549];[0.0, 0.18383245]
15;133.333;0.0;true;1;0;1.329;false;Rect.fromLTRB(15.9, 62.0, 567.5, 613.6);[0.5019608, 0.30588236, 0.30588236, 0.33333334];[0.0, 0.18383245]
16;141.667;-0.012771803;true;1;0;1.843;false;Rect.fromLTRB(15.5, 62.1, 513.0, 559.5);[0.5019608, 0.29803923, 0.29803923, 0.3254902];[0.0, 0.18382016]
17;150.000;-0.0054128217;true;1;0;1.664;false;Rect.fromLTRB(15.2, 62.2, 461.0, 507.8);[0.5019608, 0.2901961, 0.2901961, 0.31764707];[0.0, 0.18382724]
18;158.333;0.02278884;true;1;0;1.027;false;Rect.fromLTRB(16.1, 62.1, 414.3, 460.6);[0.5019608, 0.28235295, 0.28235295, 0.30980393];[0.0, 0.18373561]
19;166.667;0.062149417;true;1;0;0.366;false;Rect.fromLTRB(18.5, 61.5, 374.5, 418.9);[0.5019608, 0.2784314, 0.2784314, 0.3019608];[0.0, 0.18357074]
20;175.000;0.11201391;true;1;0;0.231;false;Rect.fromLTRB(22.8, 60.4, 342.1, 383.2);[0.5019608, 0.27058825, 0.27058825, 0.29411766];[0.0, 0.18336624]
21;183.333;0.17748843;true;1;0;0.419;false;Rect.fromLTRB(29.4, 58.8, 317.2, 353.3);[0.5019608, 0.2627451, 0.2627451, 0.28627452];[0.0, 0.18310536]
22;191.667;0.2415004;true;1;0;0.696;false;Rect.fromLTRB(38.2, 56.9, 299.3, 329.1);[0.5019608, 0.25490198, 0.25490198, 0.2784314];[0.0, 0.18285888]
23;200.000;0.3088919;true;1;0;1.045;false;Rect.fromLTRB(48.8, 54.5, 287.4, 310.2);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18260877]
24;208.333;0.386588;true;1;0;1.660;false;Rect.fromLTRB(61.2, 51.8, 281.1, 295.7);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18233265]
25;216.667;0.45568585;true;1;0;2.519;false;Rect.fromLTRB(74.3, 48.7, 279.6, 284.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18209842]
26;225.000;0.5235268;true;1;0;3.410;false;Rect.fromLTRB(88.0, 45.4, 281.5, 277.1);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18187906]
27;233.333;0.59709245;true;1;0;4.305;false;Rect.fromLTRB(102.2, 42.1, 288.9, 271.6);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18165338]
28;241.667;0.6591503;true;1;0;5.071;false;Rect.fromLTRB(116.5, 38.9, 298.2, 267.6);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18147308]
29;250.000;0.7174341;true;1;0;5.568;false;Rect.fromLTRB(130.3, 35.9, 308.0, 267.6);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18131234]
30;258.333;0.77150214;true;1;0;5.832;false;Rect.fromLTRB(143.6, 32.9, 317.8, 267.6);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1811708]
31;266.667;0.82692593;true;1;0;5.911;false;Rect.fromLTRB(156.3, 30.0, 327.4, 267.5);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18103343]
32;275.000;0.8712107;true;1;0;5.808;false;Rect.fromLTRB(168.0, 27.3, 336.6, 267.3);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18092933]
33;283.333;0.91536844;true;1;0;5.553;false;Rect.fromLTRB(178.9, 24.7, 345.2, 266.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18083067]
34;291.667;0.9496434;true;1;0;5.134;false;Rect.fromLTRB(188.8, 22.4, 353.1, 266.5);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18075766]
35;300.000;0.97936934;true;1;0;4.605;false;Rect.fromLTRB(197.5, 20.4, 360.4, 265.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18069686]
36;308.333;1.0076146;true;1;0;3.986;false;Rect.fromLTRB(205.0, 18.6, 366.9, 265.3);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806803]
37;316.667;1.0283698;true;1;0;3.241;false;Rect.fromLTRB(210.6, 17.1, 372.4, 264.8);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18074836]
38;325.000;1.0453182;true;1;0;2.492;false;Rect.fromLTRB(214.4, 15.6, 377.1, 264.6);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18080622]
39;333.333;1.0602123;true;1;0;1.797;false;Rect.fromLTRB(216.8, 14.3, 381.0, 264.6);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18085867]
40;341.667;1.0700932;true;1;0;0.787;false;Rect.fromLTRB(218.8, 13.4, 384.1, 264.3);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18089435]
41;350.000;1.0771505;true;1;0;0.444;false;Rect.fromLTRB(219.1, 12.4, 386.5, 264.7);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18092024]
42;358.333;1.081707;true;1;0;0.198;false;Rect.fromLTRB(218.7, 11.4, 388.4, 265.2);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18093713]
43;366.667;1.0842371;true;1;0;0.090;false;Rect.fromLTRB(217.7, 10.7, 389.8, 266.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18094657]
44;375.000;1.0845151;true;1;0;0.062;false;Rect.fromLTRB(216.2, 10.1, 390.7, 266.7);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1809476]
45;383.333;1.0832382;true;1;0;0.046;false;Rect.fromLTRB(214.8, 9.7, 391.3, 267.2);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18094283]
46;391.667;1.0802791;true;1;0;0.038;false;Rect.fromLTRB(213.8, 9.5, 391.6, 267.5);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1809318]
47;400.000;1.0765702;true;1;0;0.048;false;Rect.fromLTRB(213.0, 9.4, 391.7, 267.7);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1809181]
48;408.333;1.0714772;true;1;0;0.063;false;Rect.fromLTRB(212.6, 9.5, 391.6, 267.8);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18089941]
49;416.667;1.0663561;true;1;0;0.109;false;Rect.fromLTRB(212.3, 9.6, 391.6, 267.7);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18088078]
50;425.000;1.0608631;true;1;0;0.115;false;Rect.fromLTRB(212.2, 9.7, 391.5, 267.5);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18086101]
51;433.333;1.0544342;true;1;0;0.117;false;Rect.fromLTRB(212.3, 9.9, 391.2, 267.2);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18083814]
52;441.667;1.048647;true;1;0;0.115;false;Rect.fromLTRB(212.5, 10.2, 390.8, 266.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1808178]
53;450.000;1.0429106;true;1;0;0.111;false;Rect.fromLTRB(212.7, 10.5, 390.3, 266.7);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18079787]
54;458.333;1.0366344;true;1;0;0.105;false;Rect.fromLTRB(213.0, 10.9, 389.8, 266.3);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18077633]
55;466.667;1.0312973;true;1;0;0.106;false;Rect.fromLTRB(213.3, 11.3, 389.3, 266.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18075821]
56;475.000;1.0262493;true;1;0;0.111;false;Rect.fromLTRB(213.5, 11.8, 388.8, 265.6);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18074128]
57;483.333;1.0209705;true;1;0;0.115;false;Rect.fromLTRB(213.8, 12.2, 388.3, 265.2);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18072376]
58;491.667;1.0166677;true;1;0;0.117;false;Rect.fromLTRB(214.0, 12.6, 387.8, 264.8);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18070962]
59;500.000;1.0127511;true;1;0;0.116;false;Rect.fromLTRB(214.3, 13.1, 387.3, 264.4);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18069686]
60;508.333;1.0088168;true;1;0;0.111;false;Rect.fromLTRB(214.5, 13.5, 386.9, 264.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18068415]
61;516.667;1.005739;true;1;0;0.104;false;Rect.fromLTRB(214.8, 13.9, 386.5, 263.6);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806743]
62;525.000;1.0030478;true;1;0;0.095;false;Rect.fromLTRB(215.0, 14.3, 386.1, 263.3);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066573]
63;533.333;1.0004656;true;1;0;0.086;false;Rect.fromLTRB(215.2, 14.7, 385.8, 263.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065755]
64;541.667;0.9985463;true;1;0;0.073;false;Rect.fromLTRB(215.4, 15.0, 385.5, 262.7);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806589]
65;550.000;0.996958;true;1;0;0.055;false;Rect.fromLTRB(215.4, 15.3, 385.2, 262.4);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066202]
66;558.333;0.99567664;true;1;0;0.036;false;Rect.fromLTRB(215.4, 15.5, 385.0, 262.2);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066452]
67;566.667;0.9945696;true;1;0;0.018;false;Rect.fromLTRB(215.3, 15.8, 384.8, 262.1);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806667]
68;575.000;0.9938533;true;1;0;0.013;false;Rect.fromLTRB(215.1, 15.9, 384.6, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066812]
69;583.333;0.99336064;true;1;0;0.010;false;Rect.fromLTRB(214.9, 16.1, 384.4, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066908]
70;591.667;0.99304056;true;1;0;0.008;false;Rect.fromLTRB(214.7, 16.2, 384.2, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806697]
71;600.000;0.9929355;true;1;0;0.006;false;Rect.fromLTRB(214.5, 16.3, 384.1, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066992]
72;608.333;0.9929733;true;1;0;0.005;false;Rect.fromLTRB(214.4, 16.3, 384.0, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066983]
73;616.667;0.99315774;true;1;0;0.004;false;Rect.fromLTRB(214.4, 16.4, 383.9, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066949]
74;625.000;0.99342364;true;1;0;0.003;false;Rect.fromLTRB(214.3, 16.4, 383.8, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066895]
75;633.333;0.9937649;true;1;0;0.003;false;Rect.fromLTRB(214.3, 16.4, 383.8, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066828]
76;641.667;0.9942162;true;1;0;0.004;false;Rect.fromLTRB(214.4, 16.4, 383.8, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806674]
77;650.000;0.99465966;true;1;0;0.005;false;Rect.fromLTRB(214.4, 16.4, 383.8, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066652]
78;658.333;0.99512804;true;1;0;0.007;false;Rect.fromLTRB(214.5, 16.4, 383.8, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806656]
79;666.667;0.9956695;true;1;0;0.012;false;Rect.fromLTRB(214.6, 16.3, 383.9, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066455]
80;675.000;0.9961521;true;1;0;0.017;false;Rect.fromLTRB(214.7, 16.3, 383.9, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806636]
81;683.333;0.99668515;true;1;0;0.021;false;Rect.fromLTRB(214.8, 16.3, 384.0, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066256]
82;691.667;0.99714226;true;1;0;0.024;false;Rect.fromLTRB(214.9, 16.2, 384.0, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066165]
83;700.000;0.9975778;true;1;0;0.026;false;Rect.fromLTRB(215.0, 16.2, 384.1, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18066081]
84;708.333;0.9980366;true;1;0;0.027;false;Rect.fromLTRB(215.1, 16.2, 384.2, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065992]
85;716.667;0.9984132;true;1;0;0.028;false;Rect.fromLTRB(215.2, 16.1, 384.2, 262.0);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065916]
86;725.000;0.9987583;true;1;0;0.027;false;Rect.fromLTRB(215.3, 16.1, 384.3, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806585]
87;733.333;0.9991075;true;1;0;0.026;false;Rect.fromLTRB(215.3, 16.1, 384.4, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065782]
88;741.667;0.99938273;true;1;0;0.025;false;Rect.fromLTRB(215.4, 16.1, 384.4, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065728]
89;750.000;0.99962527;true;1;0;0.023;false;Rect.fromLTRB(215.5, 16.1, 384.5, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806568]
90;758.333;0.9998601;true;1;0;0.021;false;Rect.fromLTRB(215.6, 16.0, 384.5, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065636]
91;766.667;1.0000365;true;1;0;0.018;false;Rect.fromLTRB(215.6, 16.0, 384.6, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065621]
92;775.000;1.0001842;true;1;0;0.015;false;Rect.fromLTRB(215.7, 16.0, 384.6, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065666]
93;783.333;1.0003184;true;1;0;0.012;false;Rect.fromLTRB(215.7, 16.0, 384.7, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065709]
94;791.667;1.0004117;true;1;0;0.009;false;Rect.fromLTRB(215.7, 16.0, 384.7, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065739]
95;800.000;1.0004829;true;1;0;0.006;false;Rect.fromLTRB(215.8, 16.0, 384.7, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.1806576]
96;808.333;1.0;true;1;0;0.004;false;Rect.fromLTRB(215.8, 16.0, 384.8, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065608]
97;816.667;1.0;true;1;0;0.003;false;Rect.fromLTRB(215.8, 16.0, 384.8, 261.9);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065608]
98;825.000;1.0;true;0;0;0.000;false;Rect.fromLTRB(215.7, 16.0, 384.8, 261.8);[0.5019608, 0.2509804, 0.2509804, 0.27450982];[0.0, 0.18065608]
```

</details>

## Diagnosi storica dei dieci noti (F1B.8 iniziale, senza rimedi nel motore)

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
