# Il rivelatore di scatti (`glassjerk.py`)

Il terzo strumento del banco, accanto a `glassbench.py` (il fermo) e `glassmotion.py` (il movimento). Nasce con
F1B.0 (piano, «Il protocollo dei viaggi d'oro»): prende una sequenza di fotogrammi con i loro tempi e una
regione, misura ogni fotogramma e trova **da solo** le discontinuità di un viaggio. Si usa uguale sulle riprese
del tablet e sui fotogrammi resi dal banco. Gira col Python del banco (`artifacts/liquid-glass/venv`), ma non
importa skia: basta numpy e Pillow, più `ffmpeg`/`ffprobe` nel PATH.

## In dieci righe

1. La sorgente è un video a cadenza variabile (tempi = i `pts`), una cartella di PNG con i tempi (`tempi.json`
   o `_t<ms>` nel nome), o la cartella di un `glassmotion.py render` (`frames/manifest.json`).
2. La **sagoma** del vetro è ciò che si scosta dalla **pagina prevista**; la pagina prevista si fa con i due
   **riposi** (PRIMA e DOPO il viaggio), o, su un fondo piatto, col suo colore (`--piatta`).
3. Per ogni fotogramma: passo, moto, pezzi (area, baricentro, riquadro, identità fra fotogrammi), corpo, filo
   (percentili 20 e 80 della fascia di 4 px), ombra (media della fascia 1–10 px fuori), contenuto dentro e fuori,
   e, se le chiedi, le **sonde d'icona**.
4. I fotogrammi in moto si raggruppano in **viaggi**; con l'orologio di `screenrecord --bugreport` e il log di
   `InputDispatcher` i tempi partono **dal dito**.
5. Le segnalazioni: VUOTO, MAT, FILO, OMBRA, SALTO, AREA, NASCE, MUORE, FUORI, LUNGO, RIPET (sotto).
6. Le conseguenze dirette di un difetto non si contano due volte (un vuoto spiega i salti dei due fotogrammi
   dopo; un pezzo che nasce spiega il salto del baricentro; un'ombra che entra spiega l'area): il rapporto le
   elenca come «spiegate».
7. Le curve (larghezza, altezza, area, baricentro) danno superamento, picco, assestamento e oscillazioni.
8. Il prodotto: `rapporto.md`, `misure.csv`, `misure.json`, un **collage di tutti i fotogrammi** di ogni viaggio
   (contorno della sagoma in magenta, segnalati incorniciati in rosso), i primi e gli ultimi cinque ingranditi,
   le curve.
9. Le soglie stanno tutte in `SOGLIE`, in testa al file, ognuna con la sua ragione.
10. `selftest` le prova su sequenze sintetiche (otto difetti più una pulita, nei due temi, più il manifesto del
    banco); le prove sui dati veri sono qui sotto, con i comandi.

## Comandi

```powershell
$py = "artifacts\liquid-glass\venv\Scripts\python"
$gj = "tools\glassbench\glassjerk.py"

& $py $gj selftest
& $py $gj orologio VIDEO --mostra 20,200                          # ritaglia la sovrimpressione: leggila a occhio
& $py $gj orologio VIDEO --leggi 20=17:04:35.074 200=17:04:40.324  # scarto parete - pts, controllato (< 2 ms)
& $py $gj dito LOG                                                  # gli ingressi del log
& $py $gj fotogramma VIDEO --dito LOG --prima-dito 2 -o riposo.png  # un riposo intero, per --prima-img/--dopo-img
& $py $gj analizza SORGENTE --regione x,y,w,h [opzioni]
```

Opzioni di `analizza` (coordinate sempre in pixel della sorgente):

| opzione | cosa fa |
|---|---|
| `--regione x,y,w,h` | il riquadro in cui vive il viaggio. Oltre 350 000 px si lavora a metà risoluzione, oltre 1 400 000 a un quarto (le misure tornano in pixel della sorgente; il filo di 2-3 px a un quarto non si legge) |
| `--da`, `--a` | la finestra, in ms della sorgente |
| `--prima T`, `--dopo T` | i riposi (di serie il primo e l'ultimo fotogramma) |
| `--prima-dito N`, `--dopo-dito N` | il riposo è l'ultimo fotogramma prima dell'N-esimo dito (serve `--dito`) |
| `--prima-img`, `--dopo-img` | i riposi da immagini intere della stessa pagina: per le varianti interrotte, che a destinazione non arrivano mai |
| `--piatta` | la pagina è piatta: il suo colore (mediana del bordo della regione) trova il vetro nei riposi |
| `--origine x,y,w,h` | un seme: dove sta il vetro nel riposo PRIMA (il tasto da cui nasce il menu) |
| `--fermo x,y,w,h` | vetro che sta nello stesso posto nei due riposi (un tasto della barra che resta): in `--piatta` la sua forma viene dai dati e sotto la pagina si presume piatta |
| `--ignora x,y,w,h` | da non guardare: la sovrimpressione dell'orologio, la barra di stato, un'altra app |
| `--scarta-strisce` | via i pezzi che vanno da cima a fondo della regione (la pagina che scorre sotto una barra): quei fotogrammi diventano «ciechi» |
| `--icona nome=x,y,w,h` | sonda d'inchiostro di un'icona in un riquadro fisso (ripetibile) |
| `--solo-tempi` | niente sagoma: viaggi, tempi dal dito, fotogrammi lunghi e ripetuti (per una pagina che si vela o si sfoca tutta) |
| `--dito LOG` | il log degli ingressi (`logcat -v epoch`, righe `InputDispatcher: Inject`); l'orologio si cerca in `<video>.orologio.json` |
| `-o`, `--nome`, `--cella`, `--senza-immagini` | dove, come si chiama, larghezza delle celle del collage |

## La sagoma: la pagina prevista

Scelta e dimostrata nei due temi: **differenza dalla pagina prevista**, non soglia sul filo. Il filo esiste solo
a riposo nel materiale nuovo; in viaggio (oggi Fluid-physics, il vetro di prima) non c'è, e una soglia sul filo
vedrebbe «niente vetro» per tutto il viaggio.

- **Modo `riposi`** (fondi carichi). PRIMA e DOPO sono due fotogrammi fermi. Dove coincidono, la pagina è quella
  (o un vetro fermo: per il rivelatore è fondale). Dove differiscono c'è vetro in uno dei due: le parti che
  toccano `--origine` sono il vetro di PRIMA (lì la pagina è DOPO), le altre il vetro di DOPO (lì la pagina è
  PRIMA). Un pixel «cambia» se un canale, dopo una media 3×3 di fotogramma e pagina, si scosta oltre `cambio` (8)
  più il 10% dell'escursione locale della pagina: sul testo nitido il codificatore sposta i bordi delle lettere
  di 20–40 livelli fra due fotogrammi fermi (S1-ter: 44 000 px oltre 8, 489 oltre 40), un vetro che ci passa
  sopra li sposta di 80–150. La sagoma è il cambiato **chiuso, riempito e poi aperto** (via ciò che è più
  sottile di 5 px): in quest'ordine, perché un vetro chiaro su pagina chiara a riposo si stacca solo con un
  anello di 3–4 px (filo e riga scura), che un'apertura prima del riempimento cancellerebbe.
- **Modo `piatta`** (barre, tab bar, controlli). Il colore della pagina trova il vetro nei due riposi; poi la
  pagina prevista è PRIMA dove PRIMA non ha vetro, DOPO dove ce l'aveva PRIMA, e il colore piatto solo dove il
  vetro sta in tutti e due ma cambiato, o dentro i `--fermo`. In questo modo la sagoma prende solo i pixel che
  si scostano **nel verso del vetro** (quello che copre più area sul vetro dei riposi: quasi sempre più chiaro,
  il velo), contro la pagina prevista **o** contro il colore piatto: l'ombra e la riga scura sotto il filo restano
  fuori (la riga scura il filo la racchiude, e il riempimento la rimette dentro). Il verso non si usa su un fondo
  carico e colorato (un velo grigio schiarisce il testo scuro e scurisce il giallo), né quando nei riposi non c'è
  vetro da cui impararlo (il tasto indietro, che esiste solo nella pagina di mezzo).
- **Il modo piatto vuole una pagina piatta**: una regione dentro una capsula di vetro (la tab bar in scuro) non
  lo è, e il «fuori» lo dice a ogni fotogramma fermo. Lì si usa `riposi`.

## Le segnalazioni

| sigla | difetto | regola (soglie in `SOGLIE`) | famiglia |
|---|---|---|---|
| VUOTO | un fotogramma senza vetro | sulla sagoma comune ai due vicini, senza le icone, la presenza (la maggiore fra la media dello scarto nel corpo e il 90° percentile nella fascia del bordo ±4 px, meno lo stesso percentile sulla pagina vicina) scende sotto il 35% del vicino più debole (che vale almeno 8 livelli: un tasto che nasce o muore ha vicini deboli e rapporti rumorosi); anche due fotogrammi vuoti di fila. Il corpo parla in scuro, il bordo in chiaro (vetro chiaro su pagina chiara: all'arrivo della barra la media dava il 45% e il vuoto sfuggiva; ora il 6–10%) | materiale |
| MAT | il corpo cambia ricetta | la mediana di (fotogramma − precedente) sul corpo comune ai due supera 6 livelli e 3 volte il tipico della ripresa | materiale |
| FILO | il filo cambia ricetta | i percentili 20 o 80 della fascia del filo fanno un **gradino che resta** di oltre 14 livelli (vedi sotto) | materiale |
| OMBRA | un'ombra a scatto | la media della fascia 1–10 px fuori fa un gradino che resta di oltre 3 livelli | materiale |
| SALTO | un pezzo che salta | il baricentro si sposta di oltre 8 px con una velocità oltre 3 volte quella del più veloce dei due passi vicini | geometria |
| AREA | area a scatto | l'area cambia di oltre l'8% del massimo con un ritmo oltre 3 volte i vicini | geometria |
| NASCE, MUORE | un pezzo senza fase | un pezzo compare (o sparisce) già oltre 600 px e oltre metà della taglia che avrà 3 fotogrammi dopo (o che aveva, seguendo i suoi antenati) | geometria |
| FUORI | contenuto fuori dalla sagoma | oltre 30 px netti (oltre 30 livelli dalla pagina prevista, più la tessitura) fra 5 e 24 px fuori: nei primi 5 px stanno filo, riga scura e ombra del vetro | geometria |
| LUNGO | fotogramma lungo | dentro un viaggio un passo oltre 1,5 volte la mediana (a 120 Hz tutto ciò che arriva a 16,7 ms), ma non oltre 250 ms (lì sono due eventi) | costo |
| RIPET | fotogramma ripetuto | dentro un viaggio un fotogramma che non cambia fra due che cambiano davvero | costo |

Il **gradino che resta** (`stair`): i valori prima stanno fermi, quelli dopo pure (dispersione sotto un terzo
del gradino) e almeno il 60% del gradino avviene in quel fotogramma. Prima e dopo sono due fotogrammi, o uno se
un buco li chiude: a cadenza variabile uno stato fermo è **un fotogramma solo seguito da un buco**, ed è rimasto
sullo schermo. Così il testo che passa sotto il filo (un'escursione che va e torna) non è un cambio di
materiale; un vetro che cambia ricetta, e resta cambiato, sì.

Un SALTO o un'AREA subito dopo un passo lungo portano la nota «dopo un passo lungo: cadenza irregolare»: il
fotogramma in ritardo e quello corto dopo portano lo stesso avanzamento, e il moto va a scatti. Si vede, ed è
della cadenza.

## Le sonde d'icona

`--icona nome=x,y,w,h` misura in ogni fotogramma l'**inchiostro** dell'icona: la media di quanto i pixel del
riquadro si staccano dalla mediana del riquadro (il corpo del tasto) oltre 20 livelli. Vale ~0 senza icona e
scende quando l'icona sbiadisce o si sfoca. Per ogni viaggio il rapporto dà il minimo contro i due riposi:
sotto il 35% è un **buco** (uno scambio d'icone che passa per il niente) o, se l'icona è la stessa prima e dopo,
un **lampeggio**. È la misura di R7 B.11 (`docs/liquid-glass/ricerca/sfocatura-e-cambi.md`).

## Le prove

`selftest` (sintetico, nei due temi): un tasto di vetro che sale e si allarga su una pagina con righe di testo,
a 120 Hz, con un difetto alla volta. Ognuno esce al suo fotogramma (VUOTO@20, MAT@15, SALTO@18, OMBRA@25,
LUNGO@12, RIPET@14, NASCE@22, FUORI@10–13); la sequenza pulita non segnala niente. Più: il manifesto del banco
(`frames/manifest.json`) dà lo stesso risultato della memoria, e l'etichettatura a 8 vicini.

**Le prove sui difetti noti** (S1-ter, tema scuro), con i comandi esatti; più, in chiaro, la stessa barra
ripresa in F1B.0 (`partenza/d-barra/video/d-push-ritorno-chiaro.mp4`): VUOTO a f154, f224, f284, f354, f411,
f480, 6 su 6 (tre push e tre ritorni).

```powershell
# 1. Il fotogramma senza vetro all'arrivo della barra fra le pagine: 6 arrivi su 6 (f47, f121, f184, f257, f320, f394)
& $py $gj analizza artifacts\liquid-glass\sessioni\S1-ter\video\nuovo-barra-ripetuta.mp4 --regione 2290,56,270,124 --piatta --scarta-strisce --fermo 2440,62,92,92 --nome noto-barra-destra -o artifacts\liquid-glass\moto\f1b\partenza\prove-note\barra-destra
# ... e a sinistra (il tasto indietro esiste solo in B): i 3 arrivi del push (f47, f184, f320)
& $py $gj analizza artifacts\liquid-glass\sessioni\S1-ter\video\nuovo-barra-ripetuta.mp4 --regione 0,56,200,124 --piatta --scarta-strisce --nome noto-barra-sinistra -o artifacts\liquid-glass\moto\f1b\partenza\prove-note\barra-sinistra
# 2. Il cambio di materiale riposo <-> viaggio di «Ordina»: 6 su 6 (le partenze f1, f108, f241; gli scatti al materiale nuovo dopo la chiusura f107, f239, f379)
& $py $gj analizza artifacts\liquid-glass\sessioni\S1-ter\video\nuovo-menu-ripetuto.mp4 --regione 1000,640,1088,768 --prima 1398 --dopo 3465 --origine 1325,1212,135,56 --nome noto-ordina-materiale -o artifacts\liquid-glass\moto\f1b\partenza\prove-note\ordina
```

Il tratto fermo: sulle riprese del Playground (`a-cerchio-due-*`) la tab bar resta ferma mentre il palco si
trasforma a 120 Hz; il rivelatore sulla sola tab bar non segnala niente (vedi `partenza.md`, «Prove dello
strumento»).

## Cosa non sa vedere

- **Una pagina che si muove sotto il vetro** (push, scorrimento): la pagina prevista è ferma. Con
  `--scarta-strisce` il bordo sfumato della pagina che entra si riconosce e quei fotogrammi si dichiarano
  «ciechi»; le prove di geometria lì tacciono.
- **Un velo o una sfocatura su tutta la pagina** (i menu `Expand` della barra velano e sfocano lo schermo): tutto
  cambia, la sagoma non si separa. Di quei viaggi restano tempi e cadenza.
- **Un vetro che non si stacca dalla pagina** (un pannello chiaro sopra una pagina chiara senza filo): lì la
  sagoma si perde; il vuoto si vede ancora se i vicini si staccavano.
- **Il contenuto tagliato dal bordo**: vede quello che sporge (FUORI), non quello che il bordo mangia.
- **La rifrazione come tale**: il corpo è una mediana; una lente che piega di più o di meno non è una
  segnalazione, finché il corpo non salta.
- **Il verso del vetro che si capovolge** (un velo che passa da più chiaro a più scuro della pagina nello stesso
  viaggio, in modo piatto): la sagoma ne perde un pezzo. Non è un caso di oggi.
- **Sotto i 3 livelli d'ombra** (in scuro una pagina a 14–18 ha poco margine): un'ombra che compare in scuro può
  restare sotto soglia.
- **Una pagina che scorre sotto un vetro dichiarato fermo** (`--fermo` sulla barra, con la pagina che scorre
  sotto, come nel fondo carico di F1B.0). La forma del fermo si ricava dai dati, cioè dai pixel che restano
  diversi dalla pagina, e il testo che passa sotto la barra la gonfia: 18 154 px invece di ~7 500 in chiaro,
  44 540 in scuro, dove la sagoma va a zero in ogni fotogramma. La prova di presenza si fa su una sagoma troppo
  grande: il vetro a riposo vale 5–10 livelli invece di 19–27, sotto `presenza_min`, e il vuoto non si prova.
  In `d-carico-ritorno` il rivelatore vede 1 arrivo vuoto su 5 in chiaro e 0 su 5 in scuro; a occhio il vuoto
  c'è in 5 su 5 per tema. Lì si guarda la striscia degli arrivi (in F1B.0:
  `artifacts/liquid-glass/moto/f1b/partenza/strumenti/arrivi.py`, i fotogrammi fra +560 e +720 ms da ogni dito).
  Se il vetro fermo è noto, conviene dichiararlo per intero invece di farlo ricavare.
- **Due riposi che si sovrappongono** (un cerchio che diventa due pezzi nello stesso posto, un gruppo di tasti
  che diventa un menù sopra di sé): la pagina prevista nell'area comune non c'è, quindi tutto il D01 è origine.
  FUORI, VUOTO, NASCE e MUORE lì vengono dal limite, non dal vetro, e larghezza e area non valgono; i tempi
  (dal moto dei pixel) e la cadenza sì. Si guardano i collage.
- **Un pomello che diventa lente al tocco** (cursore, interruttore) in modo `piatta`: la lente rifrange la traccia
  e il cambio di materiale è voluto. Si usa `--fermo` sulla traccia e si leggono le MAT come attese.
- I tempi «dal dito» valgono quanto l'orologio: la sovrimpressione si legge a occhio (due letture per ripresa,
  controllate fra loro al millisecondo).
